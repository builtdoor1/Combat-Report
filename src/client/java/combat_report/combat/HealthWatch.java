package combat_report.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
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
	private static final long RETAIN_MS = 2_000L;

	/** One observed drop in someone's health. */
	public record Damage(UUID who, double amount, long atMs) {
	}

	private final Map<UUID, Double> lastHealth = new HashMap<>();
	private final Deque<Damage> recent = new ArrayDeque<>();
	private boolean opponentHealthSeen;

	public void reset() {
		this.lastHealth.clear();
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

		for (Player p : mc.level.players()) {
			if (p != mc.player && p.distanceToSqr(mc.player) > SAMPLE_RANGE * SAMPLE_RANGE) {
				continue;
			}

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

		while (!this.recent.isEmpty() && nowMs - this.recent.peekFirst().atMs() > RETAIN_MS) {
			this.recent.removeFirst();
		}
	}

	/**
	 * The damage done to one player around a given moment. Health arrives in its own
	 * packet, so the two are matched by time rather than by identity of the event;
	 * everything inside the link window is summed, because a hit and the tick it
	 * lands on do not always agree to the millisecond.
	 */
	public double damageAround(UUID who, long atMs) {
		double total = 0.0;

		for (Damage d : this.recent) {
			if (d.who().equals(who) && Math.abs(d.atMs() - atMs) <= Constants.DAMAGE_LINK_MS) {
				total += d.amount();
			}
		}

		return total;
	}

	/** Forgets a player who left, so a rejoin does not read as a huge heal or hit. */
	public void forget(UUID who) {
		this.lastHealth.remove(who);
	}
}
