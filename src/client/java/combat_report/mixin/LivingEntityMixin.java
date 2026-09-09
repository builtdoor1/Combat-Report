package combat_report.mixin;

import combat_report.combat.FightWatcher;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Damage taken, and jumps.
 *
 * <p>{@code handleDamageEvent} is the client-side arrival of a damage event, and the
 * source it is handed has already been resolved against the client's level - so the
 * attacker is a real entity, not an id to guess at. That is a much better signal
 * than inferring a melee hit from knockback magnitude: it names who hit you, which
 * is what the received-combo and trade figures are built on.
 *
 * <p>{@code jumpFromGround} is the real jump call, so knockback that throws you
 * upward can never be mistaken for a jump.
 *
 * <p>Neither injection filters to the local player here. That is done in
 * {@link FightWatcher}, which has the context to decide.
 */
@Mixin(LivingEntity.class)
public class LivingEntityMixin {

	@Inject(method = "handleDamageEvent(Lnet/minecraft/world/damagesource/DamageSource;)V", at = @At("HEAD"))
	private void combatReport$onDamage(DamageSource source, CallbackInfo ci) {
		FightWatcher.get().onDamage((LivingEntity) (Object) this, source);
	}

	@Inject(method = "jumpFromGround()V", at = @At("HEAD"))
	private void combatReport$onJump(CallbackInfo ci) {
		FightWatcher.get().onJump((LivingEntity) (Object) this);
	}
}
