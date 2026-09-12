package combat_report.record;

import combat_report.combat.Constants;

import java.util.List;

/**
 * Turns the recorded event lists into the numbers the report prints.
 *
 * <p>Game-free on purpose (see {@link ReportData}), and computed once at stop time
 * over every event — never over a sample.
 *
 * <p>Where a figure has no denominator it is left at zero and the report prints a
 * dash. A percentage of nothing is not zero percent, and printing it as zero is how
 * an empty recording ends up looking like a terrible one.
 */
public final class ReportStats {

	private ReportStats() {
	}

	public static void summarise(ReportData d) {
		ReportData.Summary s = new ReportData.Summary();
		hits(d, s);
		combos(d, s);
		jumps(d, s);
		trades(d, s);
		d.summary = s;
	}

	// ---- Section 1: hits ----------------------------------------------------

	private static void hits(ReportData d, ReportData.Summary s) {
		s.swings = d.swings.size();

		double reachSum = 0.0;
		double pickChargeSum = 0.0;

		for (ReportData.Swing sw : d.swings) {
			if (!sw.landed) {
				s.missed++;
				continue;
			}

			s.landed++;
			reachSum += sw.reach;
			s.maxReach = Math.max(s.maxReach, sw.reach);

			if (sw.reach > Constants.THREE_BLOCK_THRESHOLD) {
				s.threeBlockHits++;
			}

			if (sw.type == null) {
				s.plain++;
				continue;
			}

			switch (sw.type) {
				case "PICK" -> {
					s.pick++;
					pickChargeSum += sw.charge;
				}
				case "KB" -> s.kb++;
				case "CRIT" -> s.crit++;
				case "SWEEP" -> s.sweep++;
				default -> s.plain++;
			}
		}

		if (s.swings > 0) {
			s.accuracyPct = pct(s.landed, s.swings);
		}

		if (s.landed > 0) {
			s.pickPct = pct(s.pick, s.landed);
			s.kbPct = pct(s.kb, s.landed);
			s.critPct = pct(s.crit, s.landed);
			s.sweepPct = pct(s.sweep, s.landed);
			s.plainPct = pct(s.plain, s.landed);

			s.avgReach = reachSum / s.landed;
			s.threeBlockPct = pct(s.threeBlockHits, s.landed);

			// The band is 0.3 wide and contains the average, snapped to a tenth, so
			// an average of 2.65 reads 2.5-2.8. A single figure to two decimals
			// implies a precision that client-side reach measurement does not have.
			s.rangeLo = Math.floor((s.avgReach - Constants.RANGE_BAND_WIDTH / 2.0) * 10.0) / 10.0;
			if (s.rangeLo < 0.0) {
				s.rangeLo = 0.0;
			}
			s.rangeHi = s.rangeLo + Constants.RANGE_BAND_WIDTH;
		}

		if (s.pick > 0) {
			s.avgPickCharge = pickChargeSum / s.pick;
		}
	}

	// ---- Section 2: combos --------------------------------------------------

	private static void combos(ReportData d, ReportData.Summary s) {
		Counted mine = countCombos(d.combosDealt);
		s.combos = mine.combos;
		s.comboHits = mine.hits;
		s.singleHits = mine.singles;
		s.avgComboHits = mine.combos > 0 ? (double) mine.hits / mine.combos : 0.0;

		Counted theirs = countCombos(d.combosTaken);
		s.combosTaken = theirs.combos;
		s.comboHitsTaken = theirs.hits;
		s.singleHitsTaken = theirs.singles;
		s.avgComboHitsTaken = theirs.combos > 0 ? (double) theirs.hits / theirs.combos : 0.0;

		// Combo frequency is the gap from the end of one combo to the start of the
		// next. Only gaps inside a single fight count: a pair of combos separated by
		// a walk across the map is not a two-minute combo frequency.
		long gapSum = 0L;
		int gaps = 0;
		ReportData.Combo prev = null;

		for (ReportData.Combo c : d.combosDealt) {
			if (c.hits < Constants.COMBO_MIN_HITS) {
				continue;
			}

			if (prev != null && c.start >= prev.end && c.start - prev.end <= Constants.FIGHT_IDLE_MS) {
				gapSum += c.start - prev.end;
				gaps++;
			}

			prev = c;
		}

		s.comboGaps = gaps;
		s.comboFrequencyMs = gaps > 0 ? (double) gapSum / gaps : 0.0;
	}

	private record Counted(int combos, int hits, int singles) {
	}

	private static Counted countCombos(List<ReportData.Combo> list) {
		int combos = 0;
		int hits = 0;
		int singles = 0;

		for (ReportData.Combo c : list) {
			if (c.hits >= Constants.COMBO_MIN_HITS) {
				combos++;
				hits += c.hits;
			} else {
				singles++;
			}
		}

		return new Counted(combos, hits, singles);
	}

	// ---- Section 3: jumps ---------------------------------------------------

	private static void jumps(ReportData d, ReportData.Summary s) {
		long deltaSum = 0L;

		for (ReportData.Jump j : d.jumps) {
			// This is where the empty parts of a recording are removed. Jumping
			// around a lobby between fights is not combat and must not dilute the
			// punishment rate, but a jump that a hit arrived next to is combat even
			// if no fight had opened when it was thrown.
			if (!j.inCombat && !j.attempt) {
				continue;
			}

			s.jumps++;

			if (j.attempt) {
				s.resetAttempts++;
				deltaSum += j.deltaMs;

				if (j.reset) {
					s.resets++;
				}
			}

			if (j.deflected) {
				s.deflected++;
			}
		}

		if (s.resetAttempts > 0) {
			s.resetPct = pct(s.resets, s.resetAttempts);
			s.avgResetDeltaMs = (double) deltaSum / s.resetAttempts;
		}

		if (s.jumps > 0) {
			s.deflectedPct = pct(s.deflected, s.jumps);
		}
	}

	// ---- Section 4: momentum and trades -------------------------------------

	private static void trades(ReportData d, ReportData.Summary s) {
		s.trades = d.trades.size();

		double momentumSum = 0.0;

		for (ReportData.Trade t : d.trades) {
			if (t.momentumKnown) {
				s.tradesWithMomentum++;
				momentumSum += t.momentumPct;

				if (t.momentumPct > 0.0) {
					s.forwardTrades++;
				}
			}

			// Only trades where the opponent was actually seen to lose health are
			// scored. A trade where the drop never arrived is not a loss, it is not a
			// measurement, and treating the two alike turns the win rate into a
			// function of ping.
			if (t.damageKnown) {
				if (t.dealt > t.taken) {
					s.damageWins++;
				} else if (t.dealt < t.taken) {
					s.damageLosses++;
				} else {
					s.damageDraws++;
				}
			}
		}

		if (s.tradesWithMomentum > 0) {
			s.avgMomentumPct = momentumSum / s.tradesWithMomentum;
			s.forwardPct = pct(s.forwardTrades, s.tradesWithMomentum);
		}

		// A trade where both players took the same damage is a draw, and draws are
		// left out of the win rate rather than counted as half a win. "You win 60% of
		// the trades that were decided" is a claim the data supports; folding draws
		// into the denominator makes the figure move when nothing changed.
		int decided = s.damageWins + s.damageLosses;
		s.damageKnown = decided > 0;

		if (s.damageKnown) {
			s.damageWinPct = pct(s.damageWins, decided);
		}
	}

	// ---- helpers ------------------------------------------------------------

	private static double pct(int part, int whole) {
		return whole <= 0 ? 0.0 : 100.0 * part / whole;
	}
}
