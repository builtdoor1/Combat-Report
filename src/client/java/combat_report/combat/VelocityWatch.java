package combat_report.combat;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * The local player's actual velocity, measured as the distance moved each tick.
 *
 * <p>Position delta rather than {@code getDeltaMovement()} on purpose: delta
 * movement is what the physics <i>intended</i>, and after a knockback impulse into
 * a wall the two disagree. The report is about where the fight actually went, so
 * the number that matters is how far you really travelled.
 */
public final class VelocityWatch {

	/** Beyond this in one tick it was a teleport or a pearl, not movement. */
	private static final double TELEPORT_BLOCKS_PER_TICK = 4.0;

	private Vec3 lastPos;
	private Vec3 perSecond = Vec3.ZERO;

	public void reset() {
		this.lastPos = null;
		this.perSecond = Vec3.ZERO;
	}

	public void tick(LocalPlayer player) {
		Vec3 pos = player.position();

		if (this.lastPos != null) {
			Vec3 delta = pos.subtract(this.lastPos);

			if (delta.length() <= TELEPORT_BLOCKS_PER_TICK) {
				this.perSecond = delta.scale(Constants.TICKS_PER_SECOND);
			}
		}

		this.lastPos = pos;
	}

	/** Blocks per second, world axes. */
	public Vec3 perSecond() {
		return this.perSecond;
	}
}
