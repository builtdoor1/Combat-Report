package combat_report.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * How much damage a hit actually did, read off health rather than guessed.
 *
 * <p>A damage event tells you a hit happened but not what it was worth, so the
 * amount is recovered by watching health fall. Health is synced entity data
 * ({@code DATA_HEALTH_ID}), so this works for the opponent as well as for you —
 * with one caveat that the report is careful about: some servers do not send other
 * players' health, and there the opponent's health simply never changes. That is
 * why {@link #opponentHealthSeen()} exists, and why the damage figures are withheld
 * rather than printed as zero when it is false.
 *
 * <p>Absorption is added to health before differencing, so a hit soaked by a golden
 * apple still registers as damage rather than as nothing happening.
 */
public final class HealthWatch {

	/** Only players this close are sampled. */
	private static final double SAMPLE_RANGE = 32.0;

	/** How long a damage sample stays available to be matched to a hit. */
	private static final long RETAIN_MS = 4_000L;

	/** One observed drop in someone's health. */
	public record Damage(UUID who, double amount, long atMs) {
	}

	private final Map<UUID, Double> lastHealth = new HashMap<>();
	private final Set<UUID> seenThisTick = new HashSet<>();
	private final Deque<Damage> recent = new ArrayDeque<>();
	private boolean opponentHealthSeen;

	public void reset() {
		this.lastHealth.clear();
		this.seenThisTick.clear();
		this.recent.clear();
		this.opponentHealthSeen = false;
	}

	public boolean opponentHealthSeen() {
		return this.opponentHealthSeen;
	}

	public void tick(Minecraft mc, long nowMs) {
		if (mc.level == null || mc.player == null) {
			return;
		}

		UUID selfId = mc.player.getUUID();
		this.seenThisTick.clear();

		for (Player p : mc.level.players()) {
			if (p != mc.player && p.distanceToSqr(mc.player) > SAMPLE_RANGE * SAMPLE_RANGE) {
				continue;
			}

			this.seenThisTick.add(p.getUUID());
			double now = p.getHealth() + p.getAbsorptionAmount();
			Double prev = this.lastHealth.put(p.getUUID(), now);

			if (prev == null) {
				continue;
			}

			double drop = prev - now;

			// Only falls count. Regeneration and respawns are not damage, and a
			// respawn in particular would otherwise read as a huge hit.
			if (drop > 0.01 && drop < 1000.0) {
				this.recent.addLast(new Damage(p.getUUID(), drop, nowMs));

				if (!p.getUUID().equals(selfId)) {
					this.opponentHealthSeen = true;
				}
			}
		}

		// Forget anyone who is no longer in range or no longer here. Two reasons: the
		// map would otherwise grow for the whole recording on a busy server, and a
		// stale reading is worse than no reading - someone who walks away at half
		// health and comes back healed would register the difference as damage on the
		// tick they reappear.
		this.lastHealth.keySet().retainAll(this.seenThisTick);

		while (!this.recent.isEmpty() && nowMs - this.recent.peekFirst().atMs() > RETAIN_MS) {
			this.recent.removeFirst();
		}
	}

	/**
	 * The damage done to one player just after a given moment.
	 *
	 * <p>Health arrives in its own packet, so the two are matched by time rather than
	 * by the identity of the event. The window is deliberately asymmetric: a health
	 * drop caused by your click can only ever arrive <i>after</i> it, one round trip
	 * later, so the window reaches forward by the link window plus the connection's
	 * latency and barely reaches back at all. A symmetric window would let a hit
	 * steal the previous hit's damage, which inside a combo is half a second away.
	 *
	 * @param toleranceMs how far forward to look, already including latency
	 */
	public double damageAfter(UUID who, long atMs, long toleranceMs) {
		double total = 0.0;

		for (Damage d : this.recent) {
			if (!d.who().equals(who)) {
				continue;
			}

			long offset = d.atMs() - atMs;

			if (offset >= -Constants.DAMAGE_LINK_BACK_MS && offset <= toleranceMs) {
				total += d.amount();
			}
		}

		return total;
	}
}
