package combat_report.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The geometry of a swing: who it was thrown at, and how far away they were.
 *
 * <p>Pure functions over the player's tick state, so they can be called from a
 * mixin without carrying any state of their own.
 */
public final class SwingCapture {

	private SwingCapture() {
	}

	/** A swing and the player it was aimed at. */
	public record Aim(Player target, boolean onTarget, double reach) {
	}

	/**
	 * Works out who a swing was meant for.
	 *
	 * <p>If vanilla's own pick found a player, that is the answer and the swing is
	 * on target. Otherwise the swing missed, and it is attributed to the player
	 * closest to the crosshair within {@link Constants#SWING_TARGET_RANGE} and
	 * {@link Constants#SWING_TARGET_CONE_DEG}. A swing at empty air with nobody near
	 * you returns null and is not recorded at all — it is not a miss at anything,
	 * and counting it would make the accuracy figure a measure of how often you
	 * flick your mouse.
	 */
	public static Aim resolve(Minecraft mc, Player self) {
		HitResult hit = mc.hitResult;

		if (hit instanceof EntityHitResult entityHit
				&& entityHit.getEntity() instanceof Player picked
				&& picked != self) {
			return new Aim(picked, true, reachTo(self, picked));
		}

		// The click connected with something that is not a player - an end crystal, a
		// pet, an armour stand. That is not a swing at a player at all, so it is
		// dropped rather than blamed on whoever happened to be nearest the crosshair.
		// Crystal PvP would otherwise fill the miss column with clicks that hit
		// exactly what they were aimed at.
		if (hit instanceof EntityHitResult) {
			return null;
		}

		Player intended = findIntendedTarget(mc, self);
		return intended == null ? null : new Aim(intended, false, reachTo(self, intended));
	}

	/**
	 * Eye to the nearest point of the target's hitbox, which is exactly what vanilla
	 * checks — {@code Player.isWithinEntityInteractionRange} reduces to
	 * {@code box.distanceToSqr(getEyePosition()) < range * range}, with range the
	 * {@code entity_interaction_range} attribute, 3.0 by default.
	 *
	 * <p>Measured on tick positions rather than the interpolated render frame,
	 * because vanilla validates on tick positions; mixing an interpolated eye with a
	 * tick-position hitbox is worth a quarter of a block at sprint speed.
	 *
	 * <p>Deliberately not the point where the aim ray crosses the box. That is a
	 * different and always-larger quantity, and it makes legitimate vanilla hits
	 * read above 3.0 blocks.
	 */
	public static double reachTo(Player self, Player target) {
		Vec3 eye = self.getEyePosition();
		AABB box = target.getBoundingBox();
		return Math.sqrt(box.distanceToSqr(eye));
	}

	private static Player findIntendedTarget(Minecraft mc, Player self) {
		if (mc.level == null) {
			return null;
		}

		Vec3 eye = self.getEyePosition();
		Vec3 look = self.getViewVector(1.0F).normalize();

		Player best = null;
		double bestAngle = Constants.SWING_TARGET_CONE_DEG;

		for (Player other : mc.level.players()) {
			if (other == self || !other.isAlive()) {
				continue;
			}

			Vec3 toTarget = other.getBoundingBox().getCenter().subtract(eye);

			if (toTarget.length() > Constants.SWING_TARGET_RANGE) {
				continue;
			}

			double angle = angleBetween(look, toTarget);

			if (angle < bestAngle) {
				bestAngle = angle;
				best = other;
			}
		}

		return best;
	}

	private static double angleBetween(Vec3 a, Vec3 b) {
		double lengths = a.length() * b.length();

		if (lengths < 1.0e-6) {
			return 180.0;
		}

		double cos = Math.max(-1.0, Math.min(1.0, a.dot(b) / lengths));
		return Math.toDegrees(Math.acos(cos));
	}
}
