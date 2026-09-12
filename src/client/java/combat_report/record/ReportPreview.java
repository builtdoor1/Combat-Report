package combat_report.record;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Renders a report from synthetic data, without Minecraft.
 *
 * <p>Run with {@code ./gradlew reportPreview}. It writes a sample page to
 * {@code build/preview} and checks the statistics that are easy to get subtly
 * wrong: that the hit spread sums to a hundred, that the range band actually
 * contains the average it describes, and that a percentage with no denominator
 * stays a dash rather than becoming a zero.
 *
 * <p>This is possible at all because everything downstream of {@link ReportData} is
 * game-free. It is also the only test the project has, which is the honest reason
 * it checks arithmetic rather than pretending to check gameplay.
 */
public final class ReportPreview {

	private ReportPreview() {
	}

	public static void main(String[] args) throws Exception {
		Path out = Path.of(args.length > 0 ? args[0] : "build/preview");
		Files.createDirectories(out);

		ReportData data = synthesise();
		ReportStats.summarise(data);

		Path html = out.resolve("preview-report.html");
		Files.writeString(html, ReportBuilder.build(data));

		List<String> failures = new ArrayList<>();
		check(data, failures);
		checkEmpty(failures);
		checkKnownValues(failures);

		System.out.println("Wrote " + html.toAbsolutePath());
		summarise(data);

		if (!failures.isEmpty()) {
			failures.forEach(f -> System.err.println("FAIL: " + f));
			throw new IllegalStateException(failures.size() + " check(s) failed");
		}

		System.out.println("All checks passed.");
	}

	// ---- synthetic session --------------------------------------------------

	private static ReportData synthesise() {
		Random rng = new Random(20260908L);
		ReportData d = new ReportData();

		long start = 1_760_000_000_000L;
		d.startEpochMs = start;
		d.endEpochMs = start + 9L * 60_000L;
		d.startUtc = "2026-09-08 19:20:00";
		d.endUtc = "2026-09-08 19:29:00";
		d.modVersion = "1.0.0";
		d.minecraftVersion = "1.21.11";
		d.playerName = "builtdoor";
		d.playerUuid = "00000000-0000-0000-0000-000000000000";
		d.pingMs = 47.0;
		d.fights = 3;
		d.fightMs = 214_000L;
		d.opponents.add("Opponent1");
		d.opponents.add("Opponent2");
		d.opponentHealthSeen = true;

		String[] types = {"KB", "KB", "KB", "CRIT", "SWEEP", "PICK", "PLAIN"};

		for (int i = 0; i < 180; i++) {
			ReportData.Swing s = new ReportData.Swing();
			s.t = start + i * 900L;
			s.landed = rng.nextDouble() < 0.63;
			s.reach = s.landed
					? 2.1 + rng.nextGaussian() * 0.45
					: 3.1 + Math.abs(rng.nextGaussian()) * 0.7;
			s.reach = Math.max(0.4, Math.min(5.5, s.reach));
			s.opponent = rng.nextInt(2);

			if (s.landed) {
				s.type = types[rng.nextInt(types.length)];
				s.charge = "PICK".equals(s.type) ? 0.45 + rng.nextDouble() * 0.4 : 1.0;
			}

			d.swings.add(s);
		}

		long cursor = start;

		for (int i = 0; i < 22; i++) {
			ReportData.Combo c = new ReportData.Combo();
			c.hits = 1 + rng.nextInt(5);
			c.start = cursor;
			c.end = cursor + c.hits * 550L;
			c.opponent = rng.nextInt(2);
			d.combosDealt.add(c);
			cursor = c.end + 1500L + rng.nextInt(4000);
		}

		cursor = start + 400L;

		for (int i = 0; i < 19; i++) {
			ReportData.Combo c = new ReportData.Combo();
			c.hits = 1 + rng.nextInt(4);
			c.start = cursor;
			c.end = cursor + c.hits * 580L;
			c.opponent = rng.nextInt(2);
			d.combosTaken.add(c);
			cursor = c.end + 1800L + rng.nextInt(4200);
		}

		for (int i = 0; i < 64; i++) {
			ReportData.Jump j = new ReportData.Jump();
			j.t = start + i * 2600L;
			j.attempt = rng.nextDouble() < 0.68;

			if (j.attempt) {
				j.deltaMs = Math.round(rng.nextGaussian() * 62.0 + 46.0);
				j.deltaMs = Math.max(-200L, Math.min(200L, j.deltaMs));
				j.reset = j.deltaMs >= 0L && j.deltaMs <= 80L;
			}

			j.deflected = rng.nextDouble() < 0.21;
			j.inCombat = true;
			d.jumps.add(j);
		}

		for (int i = 0; i < 26; i++) {
			ReportData.Trade t = new ReportData.Trade();
			t.t = start + i * 7000L;
			t.dealt = 3.0 + rng.nextDouble() * 4.0;
			t.taken = 3.0 + rng.nextDouble() * 4.0;
			t.momentumKnown = true;
			t.momentumPct = rng.nextGaussian() * 55.0 + 18.0;
			// One trade in nine has no observed opponent health drop, so the preview
			// exercises the path where a trade is excluded from the win rate.
			t.damageKnown = i % 9 != 0;
			t.opponent = rng.nextInt(2);
			d.trades.add(t);
		}

		return d;
	}

	// ---- checks -------------------------------------------------------------

	private static void check(ReportData d, List<String> failures) {
		ReportData.Summary s = d.summary;

		if (s.landed + s.missed != s.swings) {
			failures.add("landed + missed != swings");
		}

		if (s.pick + s.kb + s.crit + s.sweep + s.plain != s.landed) {
			failures.add("hit types do not add up to the landed count");
		}

		double spread = s.pickPct + s.kbPct + s.critPct + s.sweepPct + s.plainPct;

		if (Math.abs(spread - 100.0) > 0.001) {
			failures.add("hit spread sums to " + spread + ", not 100");
		}

		// The band is the whole point of reporting a range rather than a figure, so
		// it had better contain the average it was built from.
		if (s.avgReach < s.rangeLo || s.avgReach > s.rangeHi) {
			failures.add("average reach " + s.avgReach + " outside its band " + s.rangeLo + "-" + s.rangeHi);
		}

		if (Math.abs((s.rangeHi - s.rangeLo) - 0.3) > 1.0e-9) {
			failures.add("range band is not 0.3 wide");
		}

		if (s.combos > 0 && s.avgComboHits < 2.0) {
			failures.add("average combo below 2 hits, so single hits leaked into the average");
		}

		if (s.resets > s.resetAttempts) {
			failures.add("more resets than attempts");
		}

		if (s.damageWins + s.damageLosses + s.damageDraws > s.trades) {
			failures.add("more scored trade outcomes than trades");
		}

		if (s.forwardTrades > s.tradesWithMomentum) {
			failures.add("more forward trades than trades with a momentum sample");
		}
	}

	/**
	 * A tiny session with every answer worked out by hand.
	 *
	 * <p>Random data can only check that numbers are consistent with each other.
	 * This checks that they are <i>right</i>, which is the only way an off-by-one in
	 * a denominator ever shows up.
	 */
	private static void checkKnownValues(List<String> failures) {
		ReportData d = new ReportData();
		d.startUtc = "2026-01-01 00:00:00";
		d.opponentHealthSeen = true;

		// Four swings, two landed at 2.0 and 3.0 blocks.
		// Accuracy 50%. Average reach 2.5. Band floor((2.5-0.15)*10)/10 = 2.3 to 2.6.
		// One landed hit is past 2.9, so 3 block accuracy is 50%.
		d.swings.add(swing(0L, true, 2.0, "KB"));
		d.swings.add(swing(100L, false, 3.4, null));
		d.swings.add(swing(200L, true, 3.0, "CRIT"));
		d.swings.add(swing(300L, false, 4.0, null));

		// Hits of 1, 2, 3 and 4. Only the last three are combos: 3 combos, 9 hits,
		// average 3.0, and one lone hit kept out of that average.
		// Ends and starts are placed so the two gaps are 1000 ms and 2000 ms.
		d.combosDealt.add(combo(0L, 500L, 1));
		d.combosDealt.add(combo(1_500L, 2_000L, 2));
		d.combosDealt.add(combo(3_000L, 3_500L, 3));
		d.combosDealt.add(combo(5_500L, 6_000L, 4));

		// Three jumps that count: one reset inside the window, one attempt too late,
		// and one that was not an attempt at all but was punished in the air.
		d.jumps.add(jump(0L, true, 40L, true, false, true));
		d.jumps.add(jump(1_000L, true, 150L, false, false, true));
		d.jumps.add(jump(2_000L, false, 0L, false, true, true));

		// ...and one thrown while idle between fights, which must be removed rather
		// than diluting the punishment rate. This is the "empty parts" filter.
		d.jumps.add(jump(3_000L, false, 0L, false, false, false));

		// Four trades: one won, one lost, one even, and one where the opponent was
		// never seen to lose health. The even one and the unmeasured one are both
		// excluded, so the win rate is 1 of 2 decided, not 1 of 4.
		d.trades.add(trade(0L, 6.0, 3.0, 40.0));
		d.trades.add(trade(1_000L, 2.0, 5.0, -20.0));
		d.trades.add(trade(2_000L, 4.0, 4.0, 10.0));
		d.trades.add(trade(3_000L, 0.0, 5.0, -30.0));

		ReportStats.summarise(d);
		ReportData.Summary s = d.summary;

		expect(failures, "accuracy", 50.0, s.accuracyPct);
		expect(failures, "average reach", 2.5, s.avgReach);
		expect(failures, "range low", 2.3, s.rangeLo);
		expect(failures, "range high", 2.6, s.rangeHi);
		expect(failures, "3 block accuracy", 50.0, s.threeBlockPct);
		expect(failures, "kb share", 50.0, s.kbPct);
		expect(failures, "crit share", 50.0, s.critPct);

		expect(failures, "combos", 3.0, s.combos);
		expect(failures, "combo hits", 9.0, s.comboHits);
		expect(failures, "average combo", 3.0, s.avgComboHits);
		expect(failures, "lone hits", 1.0, s.singleHits);
		expect(failures, "combo gaps", 2.0, s.comboGaps);
		expect(failures, "combo frequency", 1500.0, s.comboFrequencyMs);

		// Four jumps recorded, three counted - the idle one is trimmed.
		expect(failures, "jumps recorded", 4.0, d.jumps.size());
		expect(failures, "jumps counted", 3.0, s.jumps);
		expect(failures, "reset attempts", 2.0, s.resetAttempts);
		expect(failures, "reset accuracy", 50.0, s.resetPct);
		expect(failures, "average reset timing", 95.0, s.avgResetDeltaMs);
		expect(failures, "jump punishment", 100.0 / 3.0, s.deflectedPct);

		expect(failures, "trades", 4.0, s.trades);
		expect(failures, "forward trades", 2.0, s.forwardTrades);
		expect(failures, "forward share", 50.0, s.forwardPct);
		expect(failures, "average momentum", 0.0, s.avgMomentumPct);
		expect(failures, "damage wins", 1.0, s.damageWins);
		expect(failures, "damage losses", 1.0, s.damageLosses);
		expect(failures, "damage draws", 1.0, s.damageDraws);
		expect(failures, "damage win rate", 50.0, s.damageWinPct);
		expect(failures, "unmeasured trade excluded",
				3.0, s.damageWins + s.damageLosses + s.damageDraws);
	}

	private static ReportData.Swing swing(long t, boolean landed, double reach, String type) {
		ReportData.Swing s = new ReportData.Swing();
		s.t = t;
		s.landed = landed;
		s.reach = reach;
		s.type = type;
		s.charge = 1.0;
		return s;
	}

	private static ReportData.Combo combo(long start, long end, int hits) {
		ReportData.Combo c = new ReportData.Combo();
		c.start = start;
		c.end = end;
		c.hits = hits;
		return c;
	}

	private static ReportData.Jump jump(long t, boolean attempt, long delta, boolean reset,
			boolean deflected, boolean inCombat) {
		ReportData.Jump j = new ReportData.Jump();
		j.t = t;
		j.attempt = attempt;
		j.deltaMs = delta;
		j.reset = reset;
		j.deflected = deflected;
		j.inCombat = inCombat;
		return j;
	}

	private static ReportData.Trade trade(long t, double dealt, double taken, double momentumPct) {
		ReportData.Trade tr = new ReportData.Trade();
		tr.t = t;
		tr.dealt = dealt;
		tr.taken = taken;
		tr.momentumPct = momentumPct;
		tr.momentumKnown = true;
		tr.damageKnown = dealt > 0.0;
		return tr;
	}

	private static void expect(List<String> failures, String what, double wanted, double got) {
		if (Math.abs(wanted - got) > 1.0e-6) {
			failures.add(what + ": expected " + wanted + ", got " + got);
		}
	}

	/** An empty recording must not read as a terrible one. */
	private static void checkEmpty(List<String> failures) {
		ReportData empty = new ReportData();
		empty.startUtc = "2026-09-08 00:00:00";
		ReportStats.summarise(empty);

		ReportData.Summary s = empty.summary;

		if (s.accuracyPct != 0.0 || s.landed != 0) {
			failures.add("an empty session produced non-zero hit statistics");
		}

		if (s.damageKnown) {
			failures.add("an empty session claimed damage figures were known");
		}

		String html = ReportBuilder.build(empty);

		if (!html.contains("&mdash;")) {
			failures.add("an empty report printed numbers where it should print dashes");
		}

		if (!html.contains("No fights were detected")) {
			failures.add("an empty report did not say it was empty");
		}
	}

	private static void summarise(ReportData d) {
		ReportData.Summary s = d.summary;

		System.out.printf(Locale.ROOT,
				"  hits     %.1f%% accuracy, %d/%d landed, range %.1f-%.1f, %.1f%% past 2.9%n",
				s.accuracyPct, s.landed, s.swings, s.rangeLo, s.rangeHi, s.threeBlockPct);
		System.out.printf(Locale.ROOT,
				"  spread   pick %.1f%%  kb %.1f%%  crit %.1f%%  sweep %.1f%%  plain %.1f%%%n",
				s.pickPct, s.kbPct, s.critPct, s.sweepPct, s.plainPct);
		System.out.printf(Locale.ROOT,
				"  combos   %.2f per combo (%d combos, %d lone hits), taken %.2f (%d combos, %d lone), every %.2fs%n",
				s.avgComboHits, s.combos, s.singleHits,
				s.avgComboHitsTaken, s.combosTaken, s.singleHitsTaken, s.comboFrequencyMs / 1000.0);
		System.out.printf(Locale.ROOT,
				"  jumps    %.1f%% reset accuracy over %d attempts, %.1f%% punished%n",
				s.resetPct, s.resetAttempts, s.deflectedPct);
		System.out.printf(Locale.ROOT,
				"  trades   %d, %.1f%% forward, %+.1f%% average momentum, %.1f%% damage wins%n",
				s.trades, s.forwardPct, s.avgMomentumPct, s.damageWinPct);
	}
}
