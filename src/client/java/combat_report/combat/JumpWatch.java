package combat_report.combat;

import combat_report.record.ReportData;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

/**
 * Scores jumps two ways: as knockback resets, and as jumps that got punished.
 *
 * <p>Neither answer is available at the moment you jump, so jumps are held open and
 * resolved later:
 *
 * <ul>
 *   <li><b>Reset.</b> A hit taken shortly before the jump makes it an attempt, and
 *       an attempt inside the success window is a reset. A jump that comes <i>before</i>
 *       the hit is also an attempt — a mistimed one — which is why the window is
 *       checked in both directions and the recorded delta can be negative.</li>
 *   <li><b>Deflection.</b> A jump is deflected if the opponent lands a hit on you
 *       before your feet touch the ground again. That can only be known once you
 *       land, so the jump stays open until then.</li>
 * </ul>
 *
 * <p>Every jump made during the recording is captured, in combat or not. The idle
 * ones are dropped when the report is summarised rather than never recorded, so a
 * reset thrown a fraction of a second before the first hit of a fight still counts.
 *
 * <p>A jump is emitted once both questions are settled: it has landed, and its
 * attempt window has expired.
 */
public final class JumpWatch {

	private final Consumer<ReportData.Jump> sink;
	private final List<Pending> pending = new ArrayList<>();

	private long lastHitTakenMs = Long.MIN_VALUE;

	public JumpWatch(Consumer<ReportData.Jump> sink) {
		this.sink = sink;
	}

	private static final class Pending {
		final ReportData.Jump jump = new ReportData.Jump();
		boolean airborne = true;
	}

	public void onJump(long nowMs, boolean inCombat) {
		Pending p = new Pending();
		p.jump.t = nowMs;
		p.jump.inCombat = inCombat;

		// A hit just before this jump makes it a reset attempt.
		if (this.lastHitTakenMs != Long.MIN_VALUE && nowMs - this.lastHitTakenMs <= Constants.JUMP_ATTEMPT_MS) {
			long delta = nowMs - this.lastHitTakenMs;
			p.jump.attempt = true;
			p.jump.deltaMs = delta;
			p.jump.reset = delta >= Constants.JUMP_SUCCESS_MIN_MS && delta <= Constants.JUMP_SUCCESS_MAX_MS;
		}

		this.pending.add(p);
	}

	public void onHitTaken(long nowMs) {
		this.lastHitTakenMs = nowMs;

		for (Pending p : this.pending) {
			// A hit landing while you are still in the air is a punished jump - and
			// being hit mid-air is combat whatever the fight state said when the jump
			// was thrown, so the jump counts even if it started as an idle one.
			if (p.airborne) {
				p.jump.deflected = true;
				p.jump.inCombat = true;
			}

			// A hit arriving just after a jump makes that jump a mistimed attempt:
			// you jumped early. The delta is negative to say so.
			if (!p.jump.attempt && nowMs - p.jump.t <= Constants.JUMP_ATTEMPT_MS) {
				p.jump.attempt = true;
				p.jump.deltaMs = p.jump.t - nowMs;
				p.jump.reset = false;

				// A hit landing this close means combat had started by the time the
				// jump mattered, even if the fight had not opened when it was thrown.
				// That is the pre-emptive reset at the start of a fight, and it is a
				// real attempt, not empty time.
				p.jump.inCombat = true;
			}
		}
	}

	/**
	 * A jump that has not landed within this is closed anyway.
	 *
	 * <p>Without it a jump could stay open forever and the pending list would grow
	 * for the rest of the recording: dying in the air, landing in water, an elytra,
	 * a boat, a lava lift or a level change all leave a jump that never sees
	 * {@code onGround} again. Ten seconds is far longer than any jump that could
	 * still be punished, so closing at that point costs nothing.
	 */
	private static final long AIRBORNE_GIVE_UP_MS = 10_000L;

	/** @param onGround the player's grounded state this tick */
	public void tick(long nowMs, boolean onGround) {
		Iterator<Pending> it = this.pending.iterator();

		while (it.hasNext()) {
			Pending p = it.next();

			if (onGround && nowMs > p.jump.t) {
				p.airborne = false;
			}

			boolean windowClosed = nowMs - p.jump.t > Constants.JUMP_ATTEMPT_MS;
			boolean gaveUp = nowMs - p.jump.t > AIRBORNE_GIVE_UP_MS;

			if ((!p.airborne && windowClosed) || gaveUp) {
				this.sink.accept(p.jump);
				it.remove();
			}
		}
	}

	/** Emits everything still open. Called when the recording stops. */
	public void flush() {
		for (Pending p : this.pending) {
			this.sink.accept(p.jump);
		}

		this.pending.clear();
	}

	public void reset() {
		this.pending.clear();
		this.lastHitTakenMs = Long.MIN_VALUE;
	}
}
