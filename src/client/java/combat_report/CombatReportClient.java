package combat_report;

import combat_report.combat.FightWatcher;
import combat_report.config.CrConfig;
import combat_report.record.SessionRecorder;
import combat_report.screen.CombatReportScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * Client entrypoint: two keybinds, the tick pump, and one safety net.
 *
 * <p>The safety net is the disconnect handler. A recording lives in memory until
 * you stop it, so leaving the server mid-recording would otherwise throw away the
 * fight you just had. Instead it is stopped and written like any other.
 */
public class CombatReportClient implements ClientModInitializer {

	public static final String MOD_ID = "combat_report";

	private static final KeyMapping.Category CATEGORY =
			KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));

	private KeyMapping openScreenKey;
	private KeyMapping toggleRecordKey;

	private boolean openRequested;

	@Override
	public void onInitializeClient() {
		CrConfig.get();

		this.openScreenKey = KeyBindingHelper.registerKeyBinding(
				new KeyMapping("key." + MOD_ID + ".open_screen", GLFW.GLFW_KEY_K, CATEGORY));

		// Unbound by default. Starting a recording by accident mid-fight is worse
		// than having to bind a key once, and the screen has a button anyway.
		this.toggleRecordKey = KeyBindingHelper.registerKeyBinding(
				new KeyMapping("key." + MOD_ID + ".toggle_record", GLFW.GLFW_KEY_UNKNOWN, CATEGORY));

		ClientTickEvents.END_CLIENT_TICK.register(this::onEndTick);

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			if (SessionRecorder.get().isRecording()) {
				SessionRecorder.get().stop();
			}
		});
	}

	private void onEndTick(Minecraft mc) {
		while (this.toggleRecordKey.consumeClick()) {
			SessionRecorder.get().toggle();
		}

		while (this.openScreenKey.consumeClick()) {
			this.openRequested = true;
		}

		// Opening on the tick after the key is drained, rather than inside the drain
		// loop, so the screen does not swallow the rest of this tick's input.
		if (this.openRequested) {
			this.openRequested = false;
			mc.setScreen(new CombatReportScreen(mc.screen));
		}

		FightWatcher.get().tick(mc);
	}
}
