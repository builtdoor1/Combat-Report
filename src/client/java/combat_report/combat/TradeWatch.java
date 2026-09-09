package combat_report.combat;

import combat_report.record.ReportData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Pairs a hit dealt with a hit taken into a trade, then measures who came out of it
 * ahead — on damage, and on momentum.
 *
 * <p><b>Momentum is not speed.</b> How fast you are moving says nothing about a
 * fight; what says something is whether you are moving <i>toward</i> them or away.
 * So the velocity is projected onto the horizontal you-to-opponent axis and only
 * that component is kept. Positive is pushing forward, negative is backing off.
 *
 * <p>It is reported as a percentage of sprint speed, scaled by the player's
 * movement-speed attribute so a Speed effect does not read as superhuman momentum.
 * Values can exceed 100%: a knockback impulse moves you faster than you can run.
 *
 * <p>Both the momentum sample and the damage lookup are deferred a few ticks after
 * the exchange. Momentum needs the knockback to have been applied and not yet
 * decayed; damage needs the health packet, which arrives separately from the damage
 * event, to have turned up at all.
 */
public final class TradeWatch {

	private final Consumer<ReportData.Trade> sink;
	private final HealthWatch health;
	private final VelocityWatch velocity;

	private long dealtMs = Long.MIN_VALUE;
	private boolean dealtUsed = true;
	private UUID dealtOn;
	private int dealtIndex = -1;

	private long takenMs = Long.MIN_VALUE;
	private boolean takenUsed = true;
	private UUID takenFrom;

	private final List<Pending> pending = new ArrayList<>();

	public TradeWatch(Consumer<ReportData.Trade> sink, HealthWatch health, VelocityWatch velocity) {
		this.sink = sink;
		this.health = health;
		this.velocity = velocity;
	}

	private static final class Pending {
		ReportData.Trade trade = new ReportData.Trade();
		UUID opponent;
		long dealtAtMs;
		long takenAtMs;
		int ticksLeft = Constants.MOMENTUM_SAMPLE_TICKS;
	}

	public void onHitDealt(UUID opponent, int opponentIndex, long nowMs) {
		this.dealtMs = nowMs;
		this.dealtUsed = false;
		this.dealtOn = opponent;
		this.dealtIndex = opponentIndex;
		tryPair(nowMs);
	}

	public void onHitTaken(UUID attacker, long nowMs) {
		this.takenMs = nowMs;
		this.takenUsed = false;
		this.takenFrom = attacker;
		tryPair(nowMs);
	}

	/**
	 * A trade is one hit each, close together, between the same two players. Each
	 * hit can only be spent once, so a four-hit combo answered by a single hit is one
	 * trade, not four.
	 */
	private void tryPair(long nowMs) {
		if (this.dealtUsed || this.takenUsed) {
			return;
		}

		if (Math.abs(this.dealtMs - this.takenMs) > Constants.TRADE_WINDOW_MS) {
			return;
		}

		if (this.dealtOn == null || this.takenFrom == null || !this.dealtOn.equals(this.takenFrom)) {
			return;
		}

		this.dealtUsed = true;
		this.takenUsed = true;

		Pending p = new Pending();
		p.trade.t = Math.max(this.dealtMs, this.takenMs);
		p.trade.opponent = this.dealtIndex;
		p.opponent = this.dealtOn;
		p.dealtAtMs = this.dealtMs;
		p.takenAtMs = this.takenMs;
		this.pending.add(p);
	}

	public void tick(Minecraft mc, long nowMs) {
		Iterator<Pending> it = this.pending.iterator();

		while (it.hasNext()) {
			Pending p = it.next();

			if (--p.ticksLeft > 0) {
				continue;
			}

			settle(mc, p);
			this.sink.accept(p.trade);
			it.remove();
		}
	}

	private void settle(Minecraft mc, Pending p) {
		LocalPlayer self = mc.player;

		if (self == null) {
			return;
		}

		p.trade.dealt = this.health.damageAround(p.opponent, p.dealtAtMs);
		p.trade.taken = this.health.damageAround(self.getUUID(), p.takenAtMs);

		Player opponent = findPlayer(mc, p.opponent);

		if (opponent == null) {
			return;
		}

		Vec3 toOpponent = opponent.position().subtract(self.position());
		double flatLength = Math.sqrt(toOpponent.x * toOpponent.x + toOpponent.z * toOpponent.z);

		// Standing inside each other leaves no meaningful axis to project onto.
		if (flatLength < 1.0e-4) {
			return;
		}

		double axisX = toOpponent.x / flatLength;
		double axisZ = toOpponent.z / flatLength;

		Vec3 v = this.velocity.perSecond();
		double along = v.x * axisX + v.z * axisZ;

		p.trade.momentumPct = 100.0 * along / sprintReference(self);
		p.trade.momentumKnown = true;
	}

	/** Sprint speed for this player right now, in blocks per second. */
	private static double sprintReference(LocalPlayer self) {
		double speedAttr = self.getAttributeValue(Attributes.MOVEMENT_SPEED);
		double scale = speedAttr > 0.0 ? speedAttr / Constants.BASE_MOVEMENT_SPEED : 1.0;
		return Constants.SPRINT_SPEED_BPS * scale;
	}

	private static Player findPlayer(Minecraft mc, UUID id) {
		if (mc.level == null || id == null) {
			return null;
		}

		for (Player p : mc.level.players()) {
			if (p.getUUID().equals(id)) {
				return p;
			}
		}

		return null;
	}

	/** Settles everything outstanding. Called when the recording stops. */
	public void flush(Minecraft mc) {
		for (Pending p : this.pending) {
			settle(mc, p);
			this.sink.accept(p.trade);
		}

		this.pending.clear();
	}

	public void reset() {
		this.pending.clear();
		this.dealtUsed = true;
		this.takenUsed = true;
		this.dealtOn = null;
		this.takenFrom = null;
		this.dealtMs = Long.MIN_VALUE;
		this.takenMs = Long.MIN_VALUE;
	}
}
