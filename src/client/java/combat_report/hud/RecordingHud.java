package combat_report.hud;

import combat_report.combat.FightWatcher;
import combat_report.config.CrConfig;
import combat_report.record.SessionRecorder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * A small indicator while a recording is running.
 *
 * <p>It says two things, and the second one matters more than it looks: whether the
 * recording is running, and whether the mod currently thinks you are in a fight.
 * Only time inside a fight is measured, so seeing that state is the difference
 * between trusting the report and wondering why it is empty.
 */
public final class RecordingHud {

	private static final int RED = 0xFFE06A6A;
	private static final int GREEN = 0xFF4EC9A0;
	private static final int MUTED = 0xFF93A1B0;
	private static final int BACKDROP = 0x70000000;

	private RecordingHud() {
	}

	public static void render(GuiGraphics graphics) {
		if (!CrConfig.get().showRecordingHud || !SessionRecorder.get().isRecording()) {
			return;
		}

		Minecraft mc = Minecraft.getInstance();

		if (mc.player == null || mc.options.hideGui) {
			return;
		}

		boolean inFight = FightWatcher.get().inFight();
		Component line = Component.literal("REC " + timer(SessionRecorder.get().elapsedMs()));
		Component state = Component.literal(inFight ? "in fight" : "waiting for a fight");

		int x = 6;
		int y = 6;
		int width = Math.max(mc.font.width(line), mc.font.width(state)) + 20;

		graphics.fill(x - 4, y - 4, x + width, y + 22, BACKDROP);
		graphics.fill(x + 1, y + 2, x + 6, y + 7, RED);
		graphics.drawString(mc.font, line, x + 11, y, RED);
		graphics.drawString(mc.font, state, x + 11, y + 11, inFight ? GREEN : MUTED);
	}

	private static String timer(long ms) {
		long seconds = ms / 1000L;
		return String.format(Locale.ROOT, "%d:%02d", seconds / 60L, seconds % 60L);
	}
}
