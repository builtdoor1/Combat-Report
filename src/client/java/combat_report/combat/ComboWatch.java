package combat_report.combat;

import combat_report.record.ReportData;

import java.util.List;
import java.util.function.Consumer;

/**
 * Chains hits on one opponent into combos.
 *
 * <p>One instance tracks the combos you land; a second, independent instance tracks
 * the ones you take. They are the same shape because the question is the same one
 * asked from the other side.
 *
 * <p>A chain is closed — and only then handed to the consumer — when the gap runs
 * out, the opponent changes, or the fight ends. Chains of a single hit are still
 * emitted, so the report can say how many isolated pokes there were rather than
 * quietly discarding them or averaging them in as one-hit combos.
 */
public final class ComboWatch {

	private final Consumer<ReportData.Combo> sink;

	private int opponent = -1;
	private long startMs;
	private long lastMs;
	private int hits;

	public ComboWatch(Consumer<ReportData.Combo> sink) {
		this.sink = sink;
	}

	public void onHit(int opponentIndex, long nowMs) {
		boolean continues = this.hits > 0
				&& opponentIndex == this.opponent
				&& nowMs - this.lastMs <= Constants.COMBO_GAP_MS;

		if (!continues) {
			flush();
			this.opponent = opponentIndex;
			this.startMs = nowMs;
			this.hits = 0;
		}

		this.hits++;
		this.lastMs = nowMs;
	}

	/** Closes a chain whose gap has run out. Call once per client tick. */
	public void tick(long nowMs) {
		if (this.hits > 0 && nowMs - this.lastMs > Constants.COMBO_GAP_MS) {
			flush();
		}
	}

	public void flush() {
		if (this.hits <= 0) {
			return;
		}

		ReportData.Combo c = new ReportData.Combo();
		c.start = this.startMs;
		c.end = this.lastMs;
		c.hits = this.hits;
		c.opponent = this.opponent;
		this.sink.accept(c);

		this.hits = 0;
		this.opponent = -1;
	}

	public void reset() {
		this.hits = 0;
		this.opponent = -1;
	}

	/** Sorts a combo list into time order, which the frequency figure depends on. */
	public static void sortByStart(List<ReportData.Combo> combos) {
		combos.sort((a, b) -> Long.compare(a.start, b.start));
	}
}
