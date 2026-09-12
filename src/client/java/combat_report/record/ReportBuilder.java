package combat_report.record;

import combat_report.combat.Constants;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Renders a finished recording as one self-contained HTML page.
 *
 * <p>Everything is inline: the stylesheet, the charts as hand-emitted SVG, and a
 * dozen lines of script for the hover tooltips. The file opens in a browser with no
 * internet connection and fetches nothing, which is what makes it something you can
 * hand to someone. With scripting off it still renders in full, and every mark
 * keeps a {@code <title>} so the browser's own tooltip takes over.
 *
 * <p>Game-free (see {@link ReportData}), so {@code ./gradlew reportPreview} can
 * render one from synthetic data and the page can be checked without Minecraft.
 *
 * <p>A figure with no denominator prints as a dash rather than as zero. An empty
 * recording should look empty, not terrible.
 */
public final class ReportBuilder {

	private static final String INK = "#e8ecf1";
	private static final String MUTED = "#93a1b0";
	private static final String LINE = "#2b3440";
	private static final String PANEL = "#161c24";
	private static final String BG = "#0e1319";
	private static final String GOOD = "#4ec9a0";
	private static final String BAD = "#e06a6a";
	private static final String ACCENT = "#6db3f2";

	// One colour per hit type, in the order the spread bar draws them.
	private static final String C_PICK = "#8b7ec8";
	private static final String C_KB = "#e0a45e";
	private static final String C_CRIT = "#e06a6a";
	private static final String C_SWEEP = "#4ec9a0";
	private static final String C_PLAIN = "#5d7a94";

	private ReportBuilder() {
	}

	public static String build(ReportData d) {
		ReportData.Summary s = d.summary == null ? new ReportData.Summary() : d.summary;
		StringBuilder h = new StringBuilder(24576);

		h.append("<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"utf-8\">");
		h.append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">");
		h.append("<title>Combat Report ").append(esc(d.startUtc)).append("</title>");
		h.append("<style>").append(css()).append("</style></head><body><main>");

		hero(h, d, s);

		if (d.fights == 0) {
			h.append("<div class=\"empty\">No fights were detected in this recording. ")
					.append("A fight starts when you swing at another player or take a hit from one.</div>");
		}

		hits(h, d, s);
		combos(h, s);
		jumps(h, d, s);
		trades(h, d, s);

		h.append("<footer>Combat Report ").append(esc(d.modVersion))
				.append(" &middot; Minecraft ").append(esc(d.minecraftVersion))
				.append(" &middot; measured on the client, so reach and timing are what your game saw, ")
				.append("not what the server did.</footer>");

		h.append("</main><script>").append(hoverScript()).append("</script></body></html>");
		return h.toString();
	}

	/**
	 * Fast tooltips, and the link between the two jump panels.
	 *
	 * <p>The SVG marks carry a {@code <title>} as well, so the report still explains
	 * itself with scripting turned off - just with the browser's own sluggish native
	 * tooltip. When this runs it strips those titles out, otherwise both would fire
	 * and the native one would arrive a second later on top of ours.
	 *
	 * <p>Nothing here is fetched. It is a dozen lines of event delegation on a page
	 * that still opens with no network at all.
	 */
	private static String hoverScript() {
		return """
				for (const box of document.querySelectorAll('.chartbox')) {
				  const tip = box.querySelector('.tip');
				  for (const t of box.querySelectorAll('[data-tip] > title')) t.remove();
				  let held = null;
				  const clear = () => {
				    if (held === null) return;
				    for (const m of box.querySelectorAll('.hi')) m.classList.remove('hi');
				    held = null;
				  };
				  box.addEventListener('mousemove', e => {
				    const el = e.target.closest('[data-tip]');
				    if (!el) { tip.hidden = true; clear(); return; }
				    tip.textContent = el.getAttribute('data-tip');
				    tip.hidden = false;
				    const r = box.getBoundingClientRect();
				    let x = e.clientX - r.left + 14;
				    let y = e.clientY - r.top - tip.offsetHeight - 12;
				    x = Math.max(6, Math.min(x, r.width - tip.offsetWidth - 6));
				    if (y < 4) y = e.clientY - r.top + 18;
				    tip.style.left = x + 'px';
				    tip.style.top = y + 'px';
				    const id = el.getAttribute('data-jump');
				    if (id !== held) {
				      clear();
				      if (id !== null) {
				        for (const m of box.querySelectorAll('[data-jump="' + id + '"]')) m.classList.add('hi');
				        held = id;
				      }
				    }
				  });
				  box.addEventListener('mouseleave', () => { tip.hidden = true; clear(); });
				}
				""";
	}

	// ---- header -------------------------------------------------------------

	private static void hero(StringBuilder h, ReportData d, ReportData.Summary s) {
		long fightSec = d.fightMs / 1000L;
		long totalSec = Math.max(0L, (d.endEpochMs - d.startEpochMs) / 1000L);

		h.append("<header class=\"hero\"><h1>Combat Report</h1>");
		h.append("<p class=\"who\">").append(esc(d.playerName.isEmpty() ? "Unknown player" : d.playerName));

		if (!d.opponents.isEmpty()) {
			h.append(" <span class=\"vs\">vs</span> ").append(esc(String.join(", ", d.opponents)));
		}

		h.append("</p><div class=\"chips\">");
		chip(h, esc(d.startUtc) + " UTC");
		chip(h, d.fights + (d.fights == 1 ? " fight" : " fights"));
		chip(h, duration(fightSec) + " of combat");
		chip(h, duration(totalSec) + " recorded");

		if (d.pingMs > 0.0) {
			chip(h, Math.round(d.pingMs) + " ms ping");
		}

		h.append("</div>");
		h.append("<p class=\"sub\">Only time inside a fight is measured. ")
				.append("A fight ends after ").append(Constants.FIGHT_IDLE_MS / 1000L)
				.append(" seconds with no hits either way, so walking between fights is not counted.</p>");
		h.append("</header>");
	}

	private static void chip(StringBuilder h, String text) {
		h.append("<span class=\"chip\">").append(text).append("</span>");
	}

	// ---- section 1: hits ----------------------------------------------------

	private static void hits(StringBuilder h, ReportData d, ReportData.Summary s) {
		h.append("<section><h2>1 &middot; Hits</h2>");

		h.append("<div class=\"cards\">");
		card(h, "Accuracy", pct(s.accuracyPct, s.swings), s.landed + " landed of " + s.swings + " swung");
		card(h, "Average range", s.landed > 0 ? band(s.rangeLo, s.rangeHi) : "&mdash;",
				s.landed > 0 ? num(s.avgReach, 2) + " blocks, max " + num(s.maxReach, 2) : "no landed hits");
		card(h, "3 block accuracy", pct(s.threeBlockPct, s.landed),
				s.threeBlockHits + (s.threeBlockHits == 1 ? " landed hit" : " landed hits")
						+ " past " + num(Constants.THREE_BLOCK_THRESHOLD, 1) + " blocks");
		card(h, "Misses", String.valueOf(s.missed), "swings at an opponent that did not connect");
		h.append("</div>");

		if (d.piercingSwings > 0) {
			h.append("<p class=\"note\">")
					.append(d.piercingSwings)
					.append(d.piercingSwings == 1 ? " spear attack was" : " spear attacks were")
					.append(" thrown during this recording and are not included above. A spear is")
					.append(" resolved on the server, so this client is never told whether the stab")
					.append(" landed, and its reach goes well past the 3.0 blocks these figures are")
					.append(" built around. Counting them would have made every number here worse.")
					.append("</p>");
		}

		h.append("<h3>Spread of landed hits</h3>");
		h.append("<p class=\"sub\">What kind of hit each landed swing was. ")
				.append("These five are how vanilla itself decides an attack, so every landed hit is exactly one of them.</p>");
		spreadBar(h, landedSlices(s), s.landed, "hits", "No landed hits to break down.");

		if (s.pick > 0) {
			h.append("<p class=\"sub\">Pick hits were swung at ")
					.append(Math.round(s.avgPickCharge * 100.0))
					.append("% charge on average. Below 90% is what makes a hit a pick hit.</p>");
		}

		h.append("<h3>Spread of misses</h3>");
		h.append("<p class=\"sub\">And what kind of swing each <i>miss</i> was. Charge, sprint, fall and ")
				.append("weapon are all facts about the swing you threw rather than about the hit you did not ")
				.append("get, so a miss can be typed the same way a hit can &mdash; this is what the swing ")
				.append("would have been had it connected.</p>");
		spreadBar(h, missedSlices(s), s.missed, "misses", "Nothing missed.");

		// Two percentages sit on every legend line and they are easy to read as the
		// same kind of number. They are not, and the second is the useful one.
		h.append("<p class=\"sub\">The percentage on each row is that type's share of your ")
				.append("<b>misses</b>. The figure after it is that type's <b>land rate</b> &mdash; how much ")
				.append("of what you threw actually connected. A type can be most of your misses just by ")
				.append("being most of your swings, so the land rate is the one that says a kind of swing is ")
				.append("letting you down.</p>");

		if (s.missPick > 0) {
			h.append("<p class=\"sub\">Missed pick swings were thrown at ")
					.append(Math.round(s.avgMissPickCharge * 100.0))
					.append("% charge on average");

			if (s.pick > 0) {
				h.append(", against ").append(Math.round(s.avgPickCharge * 100.0))
						.append("% for the ones that landed");
			}

			h.append(". A gap there means the early swings are the ones going wide.</p>");
		}

		if (s.swings > 0) {
			h.append("<h3>Range of every swing</h3>");
			reachChart(h, d);
			h.append("<p class=\"sub\">Green landed, red missed. The dashed line is vanilla melee range, 3.0 blocks. ")
					.append("Measured eye to the nearest point of the hitbox, which is what the game itself checks.</p>");
		}

		h.append("</section>");
	}

	/** One band of a spread bar, and its line in the legend beneath it. */
	private record Slice(String label, int count, double pct, String colour, String note) {
	}

	private static List<Slice> landedSlices(ReportData.Summary s) {
		List<Slice> slices = new ArrayList<>();
		slices.add(new Slice("Pick", s.pick, s.pickPct, C_PICK, "swung before the cooldown finished"));
		slices.add(new Slice("KB", s.kb, s.kbPct, C_KB, "sprint hit, full knockback"));
		slices.add(new Slice("Crit", s.crit, s.critPct, C_CRIT, "falling and not sprinting"));
		slices.add(new Slice("Sweep", s.sweep, s.sweepPct, C_SWEEP, "charged sword swung standing still"));
		slices.add(new Slice("Plain", s.plain, s.plainPct, C_PLAIN, "charged, but none of the above"));
		return slices;
	}

	/**
	 * The same five buckets over the swings that missed, each carrying how often
	 * that kind of swing lands at all.
	 *
	 * <p>The two figures answer different questions and the second is the one worth
	 * acting on. A type can dominate the misses simply by dominating the swings —
	 * half your misses being sprint hits means nothing if sprint hits are also half
	 * of everything you throw. The landing rate is what says a kind of swing is
	 * actually letting you down.
	 */
	private static List<Slice> missedSlices(ReportData.Summary s) {
		List<Slice> slices = new ArrayList<>();
		slices.add(new Slice("Pick", s.missPick, s.missPickPct, C_PICK, landNote(s.pick, s.pickThrown)));
		slices.add(new Slice("KB", s.missKb, s.missKbPct, C_KB, landNote(s.kb, s.kbThrown)));
		slices.add(new Slice("Crit", s.missCrit, s.missCritPct, C_CRIT, landNote(s.crit, s.critThrown)));
		slices.add(new Slice("Sweep", s.missSweep, s.missSweepPct, C_SWEEP, landNote(s.sweep, s.sweepThrown)));
		slices.add(new Slice("Plain", s.missPlain, s.missPlainPct, C_PLAIN, landNote(s.plain, s.plainThrown)));
		return slices;
	}

	private static String landNote(int landed, int thrown) {
		if (thrown <= 0) {
			return "none thrown";
		}

		return landed + " of " + thrown + " thrown landed &middot; " + num(100.0 * landed / thrown, 0) + "% land rate";
	}

	private static void spreadBar(StringBuilder h, List<Slice> slices, int total, String noun, String emptyText) {
		if (total <= 0) {
			h.append("<p class=\"none\">").append(emptyText).append("</p>");
			return;
		}

		h.append("<div class=\"bar\">");

		for (Slice sl : slices) {
			if (sl.count() <= 0) {
				continue;
			}

			h.append("<div class=\"seg\" style=\"width:").append(num(sl.pct(), 3))
					.append("%;background:").append(sl.colour()).append("\" title=\"")
					.append(sl.label()).append(": ").append(sl.count()).append(" ").append(noun).append(", ")
					.append(num(sl.pct(), 1)).append("%\"></div>");
		}

		h.append("</div><div class=\"legend\">");

		for (Slice sl : slices) {
			h.append("<div class=\"lg\"><span class=\"sw\" style=\"background:").append(sl.colour())
					.append("\"></span><b>").append(sl.label()).append("</b> ")
					.append(num(sl.pct(), 1)).append("% <span class=\"n\">(")
					.append(sl.count()).append(")</span><em>").append(sl.note()).append("</em></div>");
		}

		h.append("</div>");
	}

	private static void reachChart(StringBuilder h, ReportData d) {
		int w = 1200;
		int hgt = 260;
		int ml = 46;
		int mr = 14;
		int mt = 16;
		int mb = 26;
		int pw = w - ml - mr;
		int ph = hgt - mt - mb;

		double max = 3.6;

		for (ReportData.Swing sw : d.swings) {
			max = Math.max(max, sw.reach + 0.2);
		}

		chartOpen(h, w, hgt);
		axes(h, ml, mt, pw, ph);

		// Y gridlines every half block.
		for (double v = 0.0; v <= max; v += 0.5) {
			int y = (int) Math.round(mt + ph - v / max * ph);
			h.append("<line x1=\"").append(ml).append("\" y1=\"").append(y)
					.append("\" x2=\"").append(ml + pw).append("\" y2=\"").append(y)
					.append("\" stroke=\"").append(LINE).append("\" stroke-width=\"1\"/>");
			h.append("<text x=\"").append(ml - 8).append("\" y=\"").append(y + 4)
					.append("\" class=\"ax\" text-anchor=\"end\">").append(num(v, 1)).append("</text>");
		}

		// Vanilla melee range.
		int limitY = (int) Math.round(mt + ph - 3.0 / max * ph);
		h.append("<line x1=\"").append(ml).append("\" y1=\"").append(limitY)
				.append("\" x2=\"").append(ml + pw).append("\" y2=\"").append(limitY)
				.append("\" stroke=\"").append(MUTED)
				.append("\" stroke-width=\"1.5\" stroke-dasharray=\"5 4\"/>");
		h.append("<text x=\"").append(ml + pw - 4).append("\" y=\"").append(limitY - 6)
				.append("\" class=\"ax\" text-anchor=\"end\">vanilla 3.0</text>");

		int n = d.swings.size();

		for (int i = 0; i < n; i++) {
			ReportData.Swing sw = d.swings.get(i);
			double fx = n <= 1 ? 0.5 : (double) i / (n - 1);
			int x = (int) Math.round(ml + fx * pw);
			int y = (int) Math.round(mt + ph - sw.reach / max * ph);
			String colour = sw.landed ? GOOD : BAD;

			String tip = (sw.landed ? "landed" : "missed") + " at " + num(sw.reach, 2) + " blocks"
					+ (sw.type == null ? "" : " (" + label(sw.type) + ")");

			h.append("<circle cx=\"").append(x).append("\" cy=\"").append(y)
					.append("\" r=\"3\" fill=\"").append(colour)
					.append("\" fill-opacity=\"0.85\" data-tip=\"").append(esc(tip))
					.append("\"><title>").append(esc(tip)).append("</title></circle>");
		}

		chartClose(h);
	}

	// ---- section 2: combos --------------------------------------------------

	private static void combos(StringBuilder h, ReportData.Summary s) {
		h.append("<section><h2>2 &middot; Combos</h2>");
		h.append("<p class=\"sub\">A combo is ").append(Constants.COMBO_MIN_HITS)
				.append(" or more hits on the same opponent with no gap longer than ")
				.append(Constants.COMBO_GAP_MS).append(" ms. ")
				.append("Isolated single hits are counted separately rather than averaged in as one-hit combos.</p>");

		h.append("<div class=\"cards\">");
		card(h, "Average combo", s.combos > 0 ? num(s.avgComboHits, 2) + " hits" : "&mdash;",
				s.combos + " combos, " + s.comboHits + " hits, " + s.singleHits + " lone hits");
		card(h, "Average received combo", s.combosTaken > 0 ? num(s.avgComboHitsTaken, 2) + " hits" : "&mdash;",
				s.combosTaken + " combos taken, " + s.singleHitsTaken + " lone hits taken");
		card(h, "Combo frequency", s.comboGaps > 0 ? num(s.comboFrequencyMs / 1000.0, 2) + " s" : "&mdash;",
				s.comboGaps > 0 ? "average gap across " + s.comboGaps + " gaps, within a fight" : "needs two combos in one fight");
		h.append("</div>");

		if (s.combos > 0 || s.combosTaken > 0) {
			h.append("<h3>Yours against theirs</h3>");
			compareBars(h,
					"Hits per combo", s.avgComboHits, s.combos > 0,
					"Hits per combo taken", s.avgComboHitsTaken, s.combosTaken > 0);
		}

		h.append("</section>");
	}

	private static void compareBars(StringBuilder h, String labelA, double a, boolean hasA,
			String labelB, double b, boolean hasB) {
		double max = Math.max(1.0, Math.max(hasA ? a : 0.0, hasB ? b : 0.0));

		h.append("<div class=\"vs2\">");
		compareRow(h, labelA, a, hasA, max, GOOD);
		compareRow(h, labelB, b, hasB, max, BAD);
		h.append("</div>");
	}

	private static void compareRow(StringBuilder h, String label, double value, boolean has, double max, String colour) {
		h.append("<div class=\"vsrow\"><span class=\"vslab\">").append(label).append("</span>");
		h.append("<span class=\"vstrack\"><span class=\"vsfill\" style=\"width:")
				.append(has ? num(100.0 * value / max, 2) : "0")
				.append("%;background:").append(colour).append("\"></span></span>");
		h.append("<span class=\"vsval\">").append(has ? num(value, 2) : "&mdash;").append("</span></div>");
	}

	// ---- section 3: jumps ---------------------------------------------------

	private static void jumps(StringBuilder h, ReportData d, ReportData.Summary s) {
		h.append("<section><h2>3 &middot; Jumps</h2>");

		h.append("<div class=\"cards\">");
		card(h, "Jump reset accuracy", pct(s.resetPct, s.resetAttempts),
				s.resets + " of " + s.resetAttempts + " attempts inside "
						+ Constants.JUMP_SUCCESS_MIN_MS + "-" + Constants.JUMP_SUCCESS_MAX_MS + " ms");
		card(h, "Average timing", s.resetAttempts > 0 ? Math.round(s.avgResetDeltaMs) + " ms" : "&mdash;",
				"after the hit; negative means you jumped early");
		card(h, "Jump punishment", pct(s.deflectedPct, s.jumps),
				s.deflected + " of " + s.jumps + " jumps were hit before landing");
		h.append("</div>");

		h.append("<p class=\"sub\">A jump within ").append(Constants.JUMP_ATTEMPT_MS)
				.append(" ms of a hit taken counts as a reset attempt. Jumps further from a hit are not scored ")
				.append("at all, because a jump a fifth of a second either side of taking damage is just a jump ")
				.append("that happened nearby. A jump is punished if the opponent lands a hit on you before your ")
				.append("feet touch the ground again.</p>");

		if (s.jumps > 0) {
			h.append("<h3>Timing and punishment</h3>");
			jumpChart(h, d, s);
			h.append("<p class=\"sub\"><b>Top:</b> every reset attempt, placed by how long after the hit ")
					.append("you jumped. The green band is the window that cancels the knockback; points ")
					.append("left of zero are jumps thrown before the hit landed. Height carries no meaning, ")
					.append("it only keeps the points apart. A ring means that jump was also punished in ")
					.append("the air.</p>");
			h.append("<p class=\"sub\"><b>Bottom:</b> one mark per jump in combat, oldest first &mdash; ")
					.append("the population the punishment rate is measured over. Tall red marks were hit ")
					.append("before landing. The share of the strip that is red <i>is</i> the ")
					.append(num(s.deflectedPct, 1)).append("% above. Attempts are a subset of these, ")
					.append("so the two panels do not hold the same number of marks.</p>");
			h.append("<p class=\"sub\">Hover any mark for that jump's timing and how it ended. ")
					.append("A jump that appears on both panels lights up on both at once, so you can ")
					.append("see where a punished jump sat on the timing axis - and a jump that lights ")
					.append("up alone is one with no hit close enough to have a timing at all.</p>");
		}

		h.append("</section>");
	}

	/**
	 * Two panels, because the section answers two questions over two different
	 * populations and pretending otherwise would misrepresent both.
	 *
	 * <p>The top panel is reset timing: only attempts appear, placed by their delta.
	 * The bottom strip is every jump in combat, which is the denominator the
	 * punishment rate is actually measured over — a superset of the attempts. Trying
	 * to put punishment on the timing axis would have meant either dropping the
	 * non-attempt jumps (changing the statistic) or inventing an x position for
	 * jumps that have no delta (inventing data).
	 */
	private static void jumpChart(StringBuilder h, ReportData d, ReportData.Summary s) {
		int w = 1200;
		int ml = 46;
		int mr = 14;
		int pw = w - ml - mr;

		int topLabelY = 14;
		int topY = 22;
		int topH = 118;
		int axisY = topY + topH;
		int stripLabelY = 190;
		int stripY = 200;
		int stripH = 46;
		int hgt = stripY + stripH + 26;

		long span = Constants.JUMP_ATTEMPT_MS;

		chartOpen(h, w, hgt);

		// ---- top panel: when each attempt was thrown ------------------------
		h.append("<text x=\"").append(ml).append("\" y=\"").append(topLabelY)
				.append("\" class=\"ax\">reset attempts, by timing</text>");

		int bandX0 = msToX(Constants.JUMP_SUCCESS_MIN_MS, span, ml, pw);
		int bandX1 = msToX(Constants.JUMP_SUCCESS_MAX_MS, span, ml, pw);
		h.append("<rect x=\"").append(bandX0).append("\" y=\"").append(topY)
				.append("\" width=\"").append(Math.max(1, bandX1 - bandX0)).append("\" height=\"").append(topH)
				.append("\" fill=\"").append(GOOD).append("\" fill-opacity=\"0.14\"/>");

		for (long v = -span; v <= span; v += 50L) {
			int x = msToX(v, span, ml, pw);
			h.append("<line x1=\"").append(x).append("\" y1=\"").append(topY)
					.append("\" x2=\"").append(x).append("\" y2=\"").append(axisY)
					.append("\" stroke=\"").append(LINE).append("\" stroke-width=\"1\"/>");
			h.append("<text x=\"").append(x).append("\" y=\"").append(axisY + 18)
					.append("\" class=\"ax\" text-anchor=\"middle\">").append(v).append("</text>");
		}

		List<ReportData.Jump> counted = new ArrayList<>();

		for (ReportData.Jump j : d.jumps) {
			if (j.counted()) {
				counted.add(j);
			}
		}

		int total = counted.size();
		int n = 0;

		for (ReportData.Jump j : counted) {
			if (j.attempt) {
				n++;
			}
		}

		// The index is into the counted list, not the attempt list, so a mark in one
		// panel and its twin in the other carry the same data-jump and light up
		// together on hover. That is the point of showing both panels at once: you
		// can see where a punished jump sat on the timing axis.
		int placed = 0;

		for (int i = 0; i < total; i++) {
			ReportData.Jump j = counted.get(i);

			if (!j.attempt) {
				continue;
			}

			int x = msToX(j.deltaMs, span, ml, pw);
			double fy = n <= 1 ? 0.5 : (double) placed / (n - 1);
			int y = (int) Math.round(topY + 10 + fy * (topH - 20));
			placed++;

			String tip = jumpTip(j, i, total);

			h.append("<circle cx=\"").append(x).append("\" cy=\"").append(y)
					.append("\" r=\"4\" fill=\"").append(j.reset ? GOOD : BAD)
					.append("\" fill-opacity=\"0.85\" data-jump=\"").append(i)
					.append("\" data-tip=\"").append(esc(tip)).append("\"><title>")
					.append(esc(tip)).append("</title></circle>");

			// A ring rather than a third colour: the fill already carries whether the
			// timing worked, and punishment is a separate question about the same jump.
			if (j.deflected) {
				h.append("<circle cx=\"").append(x).append("\" cy=\"").append(y)
						.append("\" r=\"7.5\" fill=\"none\" stroke=\"").append(MUTED)
						.append("\" stroke-width=\"1.5\" pointer-events=\"none\"/>");
			}
		}

		if (n == 0) {
			h.append("<text x=\"").append(ml + pw / 2).append("\" y=\"").append(topY + topH / 2)
					.append("\" class=\"ax\" text-anchor=\"middle\">no jump landed close enough to a hit to be an attempt</text>");
		}

		// ---- bottom strip: every jump in combat, punished or not ------------
		h.append("<text x=\"").append(ml).append("\" y=\"").append(stripLabelY)
				.append("\" class=\"ax\">every jump in combat, oldest first - ")
				.append(s.deflected).append(" of ").append(counted.size()).append(" punished</text>");

		h.append("<rect x=\"").append(ml).append("\" y=\"").append(stripY)
				.append("\" width=\"").append(pw).append("\" height=\"").append(stripH)
				.append("\" fill=\"").append(BG).append("\" fill-opacity=\"0.5\" rx=\"4\"/>");

		double slot = (double) pw / Math.max(1, total);
		double barW = Math.max(2.0, Math.min(10.0, slot * 0.6));
		int midY = stripY + stripH / 2;

		for (int i = 0; i < total; i++) {
			ReportData.Jump j = counted.get(i);
			double cx = ml + slot * (i + 0.5);
			int barH = j.deflected ? 30 : 12;
			double y = midY - barH / 2.0;
			String tip = jumpTip(j, i, total);

			// A full-height invisible rect the width of the whole slot sits behind
			// each bar, so a two-pixel mark on a long session is still easy to hit
			// with the cursor. Without it the strip is interactive in principle and
			// not in practice.
			h.append("<rect x=\"").append(num(cx - slot / 2.0, 2))
					.append("\" y=\"").append(stripY).append("\" width=\"").append(num(slot, 2))
					.append("\" height=\"").append(stripH).append("\" fill=\"transparent\" data-jump=\"")
					.append(i).append("\" data-tip=\"").append(esc(tip)).append("\"><title>")
					.append(esc(tip)).append("</title></rect>");

			h.append("<rect x=\"").append(num(cx - barW / 2.0, 2)).append("\" y=\"").append(num(y, 2))
					.append("\" width=\"").append(num(barW, 2)).append("\" height=\"").append(barH)
					.append("\" rx=\"1\" pointer-events=\"none\" fill=\"").append(j.deflected ? BAD : MUTED)
					.append("\" fill-opacity=\"").append(j.deflected ? "0.9" : "0.45").append("\"/>");
		}

		chartClose(h);
	}

	/**
	 * The whole story of one jump, shown on whichever panel you hover.
	 *
	 * <p>The same text on both marks on purpose. From the strip you want to know the
	 * timing you cannot see there; from the timing panel you want to know whether it
	 * was punished, which the ring only hints at.
	 */
	private static String jumpTip(ReportData.Jump j, int index, int total) {
		StringBuilder t = new StringBuilder(64);
		t.append("jump ").append(index + 1).append(" of ").append(total).append(" - ");

		if (j.attempt) {
			t.append(j.reset ? "reset" : (j.deltaMs < 0L ? "too early" : "too late"))
					.append(", ").append(j.deltaMs >= 0L ? "+" : "").append(j.deltaMs).append(" ms");
		} else {
			t.append("no hit nearby, not a reset attempt");
		}

		return t.append(j.deflected ? " - punished in the air" : " - landed cleanly").toString();
	}

	private static int msToX(long ms, long span, int ml, int pw) {
		double f = (ms + (double) span) / (2.0 * span);
		return (int) Math.round(ml + Math.max(0.0, Math.min(1.0, f)) * pw);
	}

	// ---- section 4: momentum and trades -------------------------------------

	private static void trades(StringBuilder h, ReportData d, ReportData.Summary s) {
		h.append("<section><h2>4 &middot; Momentum and trades</h2>");
		h.append("<p class=\"sub\">A trade is one hit each, landed within ")
				.append(Constants.TRADE_WINDOW_MS).append(" ms of the other. ")
				.append("Momentum is not speed: it is how fast you were moving along the line between you and ")
				.append("them, sampled ").append(Constants.MOMENTUM_SAMPLE_TICKS * 50)
				.append(" ms after the exchange. Positive means you came out of it pushing forward.</p>");

		h.append("<div class=\"cards\">");
		card(h, "Pushing forward", pct(s.forwardPct, s.tradesWithMomentum),
				s.forwardTrades + " of " + s.tradesWithMomentum + " trades left you moving toward them");
		card(h, "Average momentum",
				s.tradesWithMomentum > 0 ? signed(s.avgMomentumPct) : "&mdash;",
				"of sprint speed, along the line to your opponent");
		card(h, "Damage wins",
				s.damageKnown ? pct(s.damageWinPct, s.damageWins + s.damageLosses) : "&mdash;",
				s.damageKnown
						? s.damageWins + " won, " + s.damageLosses + " lost, " + s.damageDraws + " even"
						: (d.opponentHealthSeen ? "no trade had a measurable outcome" : "this server does not share opponent health"));
		card(h, "Trades", String.valueOf(s.trades), "exchanges where you both connected");
		h.append("</div>");

		if (s.tradesWithMomentum > 0) {
			h.append("<h3>Momentum leaving each trade</h3>");
			momentumChart(h, d);
			h.append("<p class=\"sub\">Above the line is forward, below it is backing off. ")
					.append("Values past 100% are knockback carrying you faster than you can run.</p>");
		}

		if (!d.opponentHealthSeen && s.trades > 0) {
			h.append("<p class=\"note\">Damage figures are left out because your opponent's health never changed ")
					.append("on this client. Some servers do not send other players' health, and a zero there would ")
					.append("read as you doing no damage rather than as nothing being measured.</p>");
		}

		h.append("</section>");
	}

	private static void momentumChart(StringBuilder h, ReportData d) {
		int w = 1200;
		int hgt = 260;
		int ml = 52;
		int mr = 14;
		int mt = 16;
		int mb = 24;
		int pw = w - ml - mr;
		int ph = hgt - mt - mb;

		List<ReportData.Trade> shown = new ArrayList<>();

		for (ReportData.Trade t : d.trades) {
			if (t.momentumKnown) {
				shown.add(t);
			}
		}

		double max = 100.0;

		for (ReportData.Trade t : shown) {
			max = Math.max(max, Math.abs(t.momentumPct));
		}

		max = Math.ceil(max / 50.0) * 50.0;
		int zeroY = mt + ph / 2;

		chartOpen(h, w, hgt);

		for (double v = -max; v <= max + 0.001; v += max / 2.0) {
			int y = (int) Math.round(zeroY - v / max * (ph / 2.0));
			h.append("<line x1=\"").append(ml).append("\" y1=\"").append(y)
					.append("\" x2=\"").append(ml + pw).append("\" y2=\"").append(y)
					.append("\" stroke=\"").append(LINE).append("\" stroke-width=\"1\"/>");
			h.append("<text x=\"").append(ml - 8).append("\" y=\"").append(y + 4)
					.append("\" class=\"ax\" text-anchor=\"end\">").append(Math.round(v)).append("%</text>");
		}

		h.append("<line x1=\"").append(ml).append("\" y1=\"").append(zeroY)
				.append("\" x2=\"").append(ml + pw).append("\" y2=\"").append(zeroY)
				.append("\" stroke=\"").append(MUTED).append("\" stroke-width=\"1.5\"/>");

		int n = shown.size();
		double slot = (double) pw / Math.max(1, n);
		double barW = Math.max(2.0, Math.min(18.0, slot * 0.6));

		for (int i = 0; i < n; i++) {
			ReportData.Trade t = shown.get(i);
			double cx = ml + slot * (i + 0.5);
			double clamped = Math.max(-max, Math.min(max, t.momentumPct));
			double hh = Math.abs(clamped) / max * (ph / 2.0);
			double y = clamped >= 0 ? zeroY - hh : zeroY;

			String tip = signed(t.momentumPct) + " of sprint speed, "
					+ (t.momentumPct >= 0.0 ? "pushing forward" : "backing off");

			h.append("<rect x=\"").append(num(cx - barW / 2.0, 2)).append("\" y=\"").append(num(y, 2))
					.append("\" width=\"").append(num(barW, 2)).append("\" height=\"").append(num(Math.max(1.0, hh), 2))
					.append("\" fill=\"").append(clamped >= 0 ? GOOD : BAD)
					.append("\" fill-opacity=\"0.85\" data-tip=\"").append(esc(tip))
					.append("\"><title>").append(esc(tip)).append("</title></rect>");
		}

		chartClose(h);
	}

	// ---- small pieces -------------------------------------------------------

	private static void card(StringBuilder h, String label, String value, String note) {
		h.append("<div class=\"card\"><div class=\"lab\">").append(label)
				.append("</div><div class=\"val\">").append(value)
				.append("</div><div class=\"note\">").append(note).append("</div></div>");
	}

	/**
	 * Opens a chart: an outer box that the tooltip is positioned against, and an
	 * inner scroller that holds the SVG.
	 *
	 * <p>The tooltip has to live outside the scroller. Setting {@code overflow-x} on
	 * the inner div makes {@code overflow-y} compute to auto as well, so a tooltip
	 * placed in there would be clipped by the chart's own top edge.
	 */
	private static void chartOpen(StringBuilder h, int w, int hgt) {
		h.append("<div class=\"chartbox\"><div class=\"chartwrap\">");
		svgOpen(h, w, hgt);
	}

	private static void chartClose(StringBuilder h) {
		h.append("</svg></div><div class=\"tip\" hidden></div></div>");
	}

	private static void svgOpen(StringBuilder h, int w, int hgt) {
		// No preserveAspectRatio override: stretching the viewBox non-uniformly would
		// squash the axis text and turn the data points into ellipses.
		h.append("<svg class=\"chart\" viewBox=\"0 0 ").append(w).append(" ").append(hgt)
				.append("\" width=\"100%\" xmlns=\"http://www.w3.org/2000/svg\">");
	}

	private static void axes(StringBuilder h, int ml, int mt, int pw, int ph) {
		h.append("<line x1=\"").append(ml).append("\" y1=\"").append(mt + ph)
				.append("\" x2=\"").append(ml + pw).append("\" y2=\"").append(mt + ph)
				.append("\" stroke=\"").append(LINE).append("\" stroke-width=\"1\"/>");
	}

	private static String label(String typeName) {
		return switch (typeName) {
			case "PICK" -> "pick";
			case "KB" -> "kb";
			case "CRIT" -> "crit";
			case "SWEEP" -> "sweep";
			default -> "plain";
		};
	}

	private static String pct(double value, int denominator) {
		return denominator <= 0 ? "&mdash;" : num(value, 1) + "%";
	}

	private static String signed(double value) {
		String sign = value > 0.0 ? "+" : "";
		return sign + num(value, 1) + "%";
	}

	private static String band(double lo, double hi) {
		return num(lo, 1) + "&ndash;" + num(hi, 1);
	}

	private static String num(double v, int places) {
		return String.format(Locale.ROOT, "%." + places + "f", v);
	}

	private static String duration(long seconds) {
		long m = seconds / 60L;
		long s = seconds % 60L;
		return m + "m " + s + "s";
	}

	private static String esc(String raw) {
		if (raw == null) {
			return "";
		}

		StringBuilder out = new StringBuilder(raw.length() + 8);

		for (int i = 0; i < raw.length(); i++) {
			char c = raw.charAt(i);

			switch (c) {
				case '&' -> out.append("&amp;");
				case '<' -> out.append("&lt;");
				case '>' -> out.append("&gt;");
				case '"' -> out.append("&quot;");
				default -> out.append(c);
			}
		}

		return out.toString();
	}

	private static String css() {
		return """
				*{box-sizing:border-box}
				body{margin:0;background:%BG%;color:%INK%;
				font:15px/1.55 system-ui,-apple-system,"Segoe UI",Roboto,sans-serif}
				main{max-width:1280px;margin:0 auto;padding:32px 20px 64px}
				h1{font-size:26px;margin:0 0 6px;letter-spacing:-.01em}
				h2{font-size:19px;margin:0 0 4px;letter-spacing:-.01em}
				h3{font-size:14px;margin:26px 0 10px;color:%MUTED%;font-weight:600;
				text-transform:uppercase;letter-spacing:.06em}
				.hero{padding-bottom:22px;border-bottom:1px solid %LINE%;margin-bottom:8px}
				.who{margin:0 0 12px;font-size:17px;color:%INK%}
				.vs{color:%MUTED%;font-style:italic;padding:0 2px}
				.chips{display:flex;flex-wrap:wrap;gap:8px;margin-bottom:12px}
				.chip{background:%PANEL%;border:1px solid %LINE%;border-radius:999px;
				padding:4px 12px;font-size:12.5px;color:%MUTED%}
				section{padding:26px 0;border-bottom:1px solid %LINE%}
				section:last-of-type{border-bottom:none}
				.sub{color:%MUTED%;font-size:13.5px;margin:6px 0 0;max-width:78ch}
				.none{color:%MUTED%;font-size:13.5px;font-style:italic}
				.note{color:%MUTED%;font-size:13px;margin-top:14px;padding:10px 14px;
				background:%PANEL%;border-left:2px solid %ACCENT%;border-radius:0 6px 6px 0;max-width:78ch}
				.empty{margin:18px 0;padding:14px 16px;background:%PANEL%;border:1px solid %LINE%;
				border-radius:8px;color:%MUTED%}
				.cards{display:grid;grid-template-columns:repeat(auto-fit,minmax(210px,1fr));
				gap:12px;margin:16px 0 4px}
				.card{background:%PANEL%;border:1px solid %LINE%;border-radius:10px;padding:14px 16px}
				.card .lab{font-size:12px;color:%MUTED%;text-transform:uppercase;letter-spacing:.06em}
				.card .val{font-size:27px;font-weight:600;margin:5px 0 4px;letter-spacing:-.02em}
				.card .note{font-size:12.5px;color:%MUTED%;background:none;border:none;padding:0;margin:0}
				.bar{display:flex;height:26px;border-radius:6px;overflow:hidden;
				background:%PANEL%;border:1px solid %LINE%;margin:14px 0 12px}
				.seg{height:100%}
				.legend{display:grid;grid-template-columns:repeat(auto-fit,minmax(240px,1fr));gap:6px 18px}
				.lg{font-size:13px;color:%INK%;display:flex;align-items:baseline;gap:7px;flex-wrap:wrap}
				.lg .n{color:%MUTED%}
				.lg em{color:%MUTED%;font-style:normal;font-size:12.5px;flex-basis:100%;
				margin-left:17px;margin-top:-3px}
				.sw{width:10px;height:10px;border-radius:3px;flex:none;transform:translateY(1px)}
				.chartbox{position:relative;margin:12px 0 4px}
				.chartwrap{overflow-x:auto}
				.tip{position:absolute;pointer-events:none;z-index:5;background:%PANEL%;
				border:1px solid %LINE%;border-radius:6px;padding:5px 9px;font-size:12.5px;
				color:%INK%;white-space:nowrap;box-shadow:0 2px 10px rgba(0,0,0,.45)}
				.tip[hidden]{display:none}
				svg.chart [data-tip]{cursor:crosshair}
				svg.chart .hi{stroke:%INK%;stroke-width:2}
				svg.chart{display:block;background:%PANEL%;border:1px solid %LINE%;border-radius:10px}
				text.ax{fill:%MUTED%;font-size:11px;font-family:system-ui,sans-serif}
				.vs2{margin:12px 0}
				.vsrow{display:flex;align-items:center;gap:12px;margin:8px 0}
				.vslab{width:190px;flex:none;font-size:13px;color:%MUTED%}
				.vstrack{flex:1;height:16px;background:%PANEL%;border:1px solid %LINE%;
				border-radius:8px;overflow:hidden}
				.vsfill{display:block;height:100%}
				.vsval{width:52px;flex:none;text-align:right;font-size:14px;font-weight:600}
				footer{color:%MUTED%;font-size:12.5px;margin-top:30px;max-width:78ch}
				@media(max-width:720px){svg.chart{min-width:760px}.vslab{width:120px}}
				"""
				.replace("%BG%", BG)
				.replace("%INK%", INK)
				.replace("%MUTED%", MUTED)
				.replace("%LINE%", LINE)
				.replace("%PANEL%", PANEL)
				.replace("%ACCENT%", ACCENT);
	}
}
