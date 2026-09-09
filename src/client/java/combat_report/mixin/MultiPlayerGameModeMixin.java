package combat_report.mixin;

import combat_report.combat.FightWatcher;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A landed melee hit, caught at the only moment it can still be classified.
 *
 * <p>Vanilla reaches this method only when the client's pick found an entity within
 * reach, so arriving here is what "landed" means client-side. The body then runs
 * {@code player.attack(entity)} followed immediately by
 * {@code player.resetAttackStrengthTicker()} - so a HEAD injection sees the real
 * attack charge and anything later sees zero.
 *
 * <p>The injection sits on the {@code player.attack(entity)} call rather than at the
 * head of the method, because the method body is guarded:
 *
 * <pre>
 *   if (this.localPlayerMode != GameType.SPECTATOR) {
 *       player.attack(entity);
 *       player.resetAttackStrengthTicker();
 *   }
 * </pre>
 *
 * A head injection recorded a landed hit for a spectator left-clicking a player,
 * which vanilla never processes. This point is inside the guard and still ahead of
 * the ticker reset, so it keeps the charge intact and drops the phantom.
 *
 * <p>Worth being plain about: this is the client's own prediction. The server can
 * still reject the hit for reach, cooldown, a shield or invulnerability frames, and
 * nothing here hears about that.
 */
@Mixin(MultiPlayerGameMode.class)
public class MultiPlayerGameModeMixin {

	@Inject(
			method = "attack(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/entity/Entity;)V",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/entity/player/Player;attack(Lnet/minecraft/world/entity/Entity;)V"
			)
	)
	private void combatReport$onAttack(Player player, Entity target, CallbackInfo ci) {
		FightWatcher.get().onLandedHit(player, target);
	}
}
