package combat_report.screen;

import combat_report.combat.FightWatcher;
import combat_report.config.CrConfig;
import combat_report.record.SavedReports;
import combat_report.record.SessionRecorder;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * The recording screen: start, stop, and everything you recorded before.
 *
 * <p>Also the Mod Menu config screen, which is why it carries the three settings
 * along the bottom rather than pulling in a config library for three booleans.
 */
public class CombatReportScreen extends Screen {

	private static final int INK = 0xFFE8ECF1;
	private static final int MUTED = 0xFF93A1B0;
	private static final int GREEN = 0xFF4EC9A0;
	private static final int RED = 0xFFE06A6A;

	private final @Nullable Screen parent;

	private ReportList list;
	private Button recordButton;
	private Button openReportButton;
	private Button hudButton;
	private Button chatButton;
	private Button autoOpenButton;

	public CombatReportScreen(@Nullable Screen parent) {
		super(Component.translatable("combat_report.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int listTop = 74;
		int listBottom = this.height - 76;
		int centre = this.width / 2;

		this.recordButton = Button.builder(recordLabel(), b -> {
			SessionRecorder.get().toggle();
			this.recordButton.setMessage(recordLabel());

			if (!SessionRecorder.get().isRecording()) {
				refreshList();
			}
		}).bounds(centre - 100, 42, 200, 20).build();
		addRenderableWidget(this.recordButton);

		this.list = new ReportList(this.minecraft, this.width, Math.max(30, listBottom - listTop), listTop, this::openReport);
		addRenderableWidget(this.list);

		this.openReportButton = Button.builder(Component.literal("Open report"), b -> {
			SavedReports.Entry selected = this.list.selectedEntry();

			if (selected != null) {
				openReport(selected);
			}
		}).bounds(centre - 154, listBottom + 6, 100, 20).build();
		this.openReportButton.setTooltip(Tooltip.create(
				Component.literal("Opens the selected report in your browser. Double-clicking a row does the same.")));
		addRenderableWidget(this.openReportButton);

		Button folder = Button.builder(Component.translatable("combat_report.open_folder"),
						b -> SessionRecorder.openReportsFolder())
				.bounds(centre - 50, listBottom + 6, 150, 20).build();
		folder.setTooltip(Tooltip.create(Component.literal(SessionRecorder.dir().toString())));
		addRenderableWidget(folder);

		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
				.bounds(centre + 104, listBottom + 6, 50, 20).build());

		// Settings. Three booleans do not justify a config library, and keeping them
		// on this screen means the recording controls and the things that change what
		// recording does are in the same place.
		CrConfig config = CrConfig.get();
		int settingsY = this.height - 46;

		this.hudButton = Button.builder(toggleLabel("HUD", config.showRecordingHud), b -> {
			config.showRecordingHud = !config.showRecordingHud;
			config.save();
			this.hudButton.setMessage(toggleLabel("HUD", config.showRecordingHud));
		}).bounds(centre - 154, settingsY, 100, 20).build();
		this.hudButton.setTooltip(Tooltip.create(Component.literal("Show the recording indicator while recording.")));
		addRenderableWidget(this.hudButton);

		this.chatButton = Button.builder(toggleLabel("Chat", config.chatMessages), b -> {
			config.chatMessages = !config.chatMessages;
			config.save();
			this.chatButton.setMessage(toggleLabel("Chat", config.chatMessages));
		}).bounds(centre - 50, settingsY, 100, 20).build();
		this.chatButton.setTooltip(Tooltip.create(Component.literal("Print the saved report path to chat when a recording stops.")));
		addRenderableWidget(this.chatButton);

		this.autoOpenButton = Button.builder(toggleLabel("Auto-open", config.openReportOnStop), b -> {
			config.openReportOnStop = !config.openReportOnStop;
			config.save();
			this.autoOpenButton.setMessage(toggleLabel("Auto-open", config.openReportOnStop));
		}).bounds(centre + 54, settingsY, 100, 20).build();
		this.autoOpenButton.setTooltip(Tooltip.create(Component.literal("Open the report in a browser as soon as it is written.")));
		addRenderableWidget(this.autoOpenButton);

		refreshList();
	}

	private void refreshList() {
		this.list.refresh(SavedReports.list());
	}

	private void openReport(SavedReports.Entry entry) {
		SessionRecorder.openInBrowser(entry.html());
	}

	@Override
	public void tick() {
		super.tick();

		// The timer and the in-fight state move on their own while this is open.
		if (SessionRecorder.get().isRecording()) {
			this.recordButton.setMessage(recordLabel());
		}

		this.openReportButton.active = this.list.selectedEntry() != null;
	}

	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		super.render(guiGraphics, mouseX, mouseY, partialTick);

		guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 16, INK);
		guiGraphics.drawCenteredString(this.font, statusLine(), this.width / 2, 28, statusColour());

		if (this.list.children().isEmpty()) {
			guiGraphics.drawCenteredString(this.font, Component.literal("No reports yet. Record a fight and one lands here."),
					this.width / 2, this.height / 2 - 4, MUTED);
		}
	}

	private Component recordLabel() {
		boolean recording = SessionRecorder.get().isRecording();

		if (!recording) {
			return Component.translatable("combat_report.record.start").withStyle(ChatFormatting.GREEN);
		}

		long seconds = SessionRecorder.get().elapsedMs() / 1000L;
		String timer = String.format(Locale.ROOT, "%d:%02d", seconds / 60L, seconds % 60L);
		return Component.literal("Stop Recording  " + timer).withStyle(ChatFormatting.RED);
	}

	private Component statusLine() {
		if (!SessionRecorder.get().isRecording()) {
			return Component.literal("Not recording. Idle time is trimmed from the report, not the recording.");
		}

		return Component.literal(FightWatcher.get().inFight()
				? "Recording - in combat."
				: "Recording - out of combat, this stretch will be trimmed.");
	}

	private int statusColour() {
		if (!SessionRecorder.get().isRecording()) {
			return MUTED;
		}

		return FightWatcher.get().inFight() ? GREEN : RED;
	}

	private static Component toggleLabel(String name, boolean on) {
		return Component.literal(name + ": ")
				.append(Component.literal(on ? "On" : "Off")
						.withStyle(on ? ChatFormatting.GREEN : ChatFormatting.GRAY));
	}

	@Override
	public void onClose() {
		this.minecraft.setScreen(this.parent);
	}
}
