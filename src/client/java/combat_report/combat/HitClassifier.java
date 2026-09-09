package combat_report.combat;

import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Decides which of the five {@link HitType}s a landed hit was.
 *
 * <p>This is a transcription of vanilla {@code Player.attack(Entity)} in 1.21.11,
 * not an approximation of it. The three interesting flags there are:
 *
 * <pre>
 *   float g   = this.getAttackStrengthScale(0.5F);
 *   boolean bl  = g &gt; 0.9F;                            // charged
 *   boolean bl2 = this.isSprinting() &amp;&amp; bl;             // sprint hit
 *   boolean bl3 = bl &amp;&amp; this.canCriticalAttack(entity); // crit
 *   boolean bl4 = this.isSweepAttack(bl, bl3, bl2);      // sweep
 * </pre>
 *
 * <p>{@code canCriticalAttack} and {@code isSweepAttack} are private, so they are
 * reimplemented below from the same source. Every method they call
 * ({@code getAttackStrengthScale}, {@code isMobilityRestricted}, {@code onClimbable},
 * {@code getKnownMovement}, {@code getSpeed}, {@code getItemInHand}) is public, so
 * nothing here has to guess or use an accessor mixin.
 *
 * <p>Because a crit requires {@code !isSprinting} and a sweep requires neither a
 * crit nor a sprint hit, and all three require a charged swing, the five outcomes
 * are mutually exclusive by construction. The order below is therefore a readable
 * ordering, not a tie-break — there are no ties to break.
 *
 * <p><b>Call this before the attack is processed.</b> {@code MultiPlayerGameMode.attack}
 * calls {@code player.attack(entity)} and then {@code player.resetAttackStrengthTicker()},
 * so the charge must be read at the HEAD of that method. One instruction later it
 * reads 0.
 */
public final class HitClassifier {

	private HitClassifier() {
	}

	/** The charge fraction vanilla itself uses to decide whether a swing is strong. */
	public static float charge(Player player) {
		return player.getAttackStrengthScale(0.5F);
	}

	public static HitType classify(Player player, Entity target) {
		float charge = charge(player);
		boolean charged = charge > 0.9F;

		if (!charged) {
			return HitType.PICK;
		}

		if (player.isSprinting()) {
			return HitType.KB;
		}

		if (canCriticalAttack(player, target)) {
			return HitType.CRIT;
		}

		if (isSweepAttack(player)) {
			return HitType.SWEEP;
		}

		return HitType.PLAIN;
	}

	/**
	 * Verbatim from {@code Player.canCriticalAttack}. The sprint test is part of
	 * vanilla's own condition, not an addition here — you cannot crit while
	 * sprinting, which is why crits and sprint hits never collide.
	 */
	private static boolean canCriticalAttack(Player player, Entity target) {
		return player.fallDistance > 0.0
				&& !player.onGround()
				&& !player.onClimbable()
				&& !player.isInWater()
				&& !player.isMobilityRestricted()
				&& !player.isPassenger()
				&& target instanceof LivingEntity
				&& !player.isSprinting();
	}

	/**
	 * Verbatim from {@code Player.isSweepAttack}, with the charged/crit/sprint flags
	 * already resolved by the caller above.
	 *
	 * <p>Vanilla compares the squared horizontal movement against
	 * {@code Mth.square(getSpeed() * 2.5)} — that is the "standing still enough to
	 * sweep" test, and it is why walking forward with a sword produces a plain hit
	 * rather than a sweep.
	 */
	private static boolean isSweepAttack(Player player) {
		if (!player.onGround()) {
			return false;
		}

		double moved = player.getKnownMovement().horizontalDistanceSqr();
		double limit = player.getSpeed() * 2.5;

		if (moved >= Mth.square(limit)) {
			return false;
		}

		return player.getItemInHand(InteractionHand.MAIN_HAND).is(ItemTags.SWORDS);
	}
}
