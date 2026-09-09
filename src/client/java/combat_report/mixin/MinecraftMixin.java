package combat_report.mixin;

import combat_report.combat.FightWatcher;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Catches every left-click attack, including the ones that hit nothing.
 *
 * <p>{@code Minecraft.startAttack()} is reached only from {@code handleKeybinds()},
 * once per attack click, and it fires whether or not anything was in reach. That is
 * what makes a whiff measurable at all: {@code MultiPlayerGameMode.attack} never
 * sees one.
 *
 * <p>HEAD opens the swing and RETURN commits it, because what happens in between -
 * whether vanilla reached {@code gameMode.attack} and what the hit was classified
 * as - is exactly what the record needs. RETURN fires at every exit including the
 * early ones, so a swing rejected by the guards is committed as nothing rather than
 * left open to contaminate the next click.
 */
@Mixin(Minecraft.class)
public class MinecraftMixin {

	@Inject(method = "startAttack", at = @At("HEAD"))
	private void combatReport$swingBegin(CallbackInfoReturnable<Boolean> cir) {
		FightWatcher.get().onSwingBegin((Minecraft) (Object) this);
	}

	@Inject(method = "startAttack", at = @At("RETURN"))
	private void combatReport$swingCommit(CallbackInfoReturnable<Boolean> cir) {
		FightWatcher.get().onSwingCommit();
	}
}
