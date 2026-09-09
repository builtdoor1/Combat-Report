package combat_report.record;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import combat_report.combat.FightWatcher;
import combat_report.config.CrConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Start, stop, and the two files that come out the other end.
 *
 * <p>Recording is entirely manual. There is no auto-start on a hit: a session is
 * whatever you pressed record around, and the fight segmentation inside it is what
 * throws away the parts that were not combat.
 */
public final class SessionRecorder {

	private static final Logger LOGGER = LoggerFactory.getLogger("combat-report");

	private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	private static final DateTimeFormatter STAMP =
			DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneId.of("UTC"));

	private static final DateTimeFormatter UTC =
			DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("UTC"));

	private static final SessionRecorder INSTANCE = new SessionRecorder();

	public static SessionRecorder get() {
		return INSTANCE;
	}

	private SessionRecorder() {
	}

	private long startEpochMs;
	private Path lastReport;

	public static Path dir() {
		return CrConfig.configDir().resolve("reports");
	}

	public boolean isRecording() {
		return FightWatcher.get().isRecording();
	}

	public Path lastReport() {
		return this.lastReport;
	}

	/** Milliseconds since record was pressed, for the HUD timer. */
	public long elapsedMs() {
		return isRecording() ? System.currentTimeMillis() - this.startEpochMs : 0L;
	}

	public void toggle() {
		if (isRecording()) {
			stop();
		} else {
			start();
		}
	}

	public void start() {
		if (isRecording()) {
			return;
		}

		this.startEpochMs = System.currentTimeMillis();

		ReportData data = new ReportData();
		data.startEpochMs = this.startEpochMs;
		data.startUtc = UTC.format(Instant.ofEpochMilli(this.startEpochMs));
		data.modVersion = modVersion();
		data.minecraftVersion = FabricLoader.getInstance()
				.getModContainer("minecraft")
				.map(m -> m.getMetadata().getVersion().getFriendlyString())
				.orElse("unknown");

		FightWatcher.get().start(data);
		chat(Component.literal("Recording started.").withStyle(ChatFormatting.GREEN));
	}

	public void stop() {
		if (!isRecording()) {
			return;
		}

		Minecraft mc = Minecraft.getInstance();
		ReportData data = FightWatcher.get().stop(mc);

		if (data == null) {
			return;
		}

		data.endEpochMs = System.currentTimeMillis();
		data.endUtc = UTC.format(Instant.ofEpochMilli(data.endEpochMs));
		ReportStats.summarise(data);

		Path html = write(data);

		if (html == null) {
			chat(Component.literal("Recording stopped, but the report could not be written. See the log.")
					.withStyle(ChatFormatting.RED));
			return;
		}

		this.lastReport = html;

		if (data.swings.isEmpty() && data.jumps.isEmpty()) {
			chat(Component.literal("Recording stopped. No fights were detected, so the report is empty.")
					.withStyle(ChatFormatting.YELLOW));
		} else {
			chat(Component.literal("Report saved: " + html).withStyle(ChatFormatting.GREEN));
		}

		if (CrConfig.get().openReportOnStop) {
			openInBrowser(html);
		}
	}

	/**
	 * Writes the report and its data side by side.
	 *
	 * <p>The HTML is the thing you read and the JSON is the same numbers in a form
	 * anyone can check, which is the point of keeping the whole pipeline game-free.
	 *
	 * @return the path of the HTML report, or null if writing failed
	 */
	private Path write(ReportData data) {
		try {
			Path dir = dir();
			Files.createDirectories(dir);

			String stamp = STAMP.format(Instant.ofEpochMilli(data.startEpochMs));
			Path json = dir.resolve("report-" + stamp + ".json");
			Path html = dir.resolve("report-" + stamp + ".html");

			Files.writeString(json, PRETTY.toJson(data));
			Files.writeString(html, ReportBuilder.build(data));

			return html;
		} catch (Exception e) {
			LOGGER.error("[combat-report] could not write the report", e);
			return null;
		}
	}

	public static void openReportsFolder() {
		try {
			Files.createDirectories(dir());
			Util.getPlatform().openPath(dir());
		} catch (Exception e) {
			LOGGER.warn("[combat-report] could not open the reports folder", e);
		}
	}

	public static void openInBrowser(Path report) {
		try {
			Util.getPlatform().openUri(report.toUri());
		} catch (Exception e) {
			LOGGER.warn("[combat-report] could not open the report", e);
		}
	}

	private static String modVersion() {
		return FabricLoader.getInstance()
				.getModContainer("combat_report")
				.map(m -> m.getMetadata().getVersion().getFriendlyString())
				.orElse("dev");
	}

	private static void chat(Component message) {
		if (!CrConfig.get().chatMessages) {
			return;
		}

		Minecraft mc = Minecraft.getInstance();

		if (mc.player != null) {
			mc.player.displayClientMessage(
					Component.literal("[Combat Report] ").withStyle(ChatFormatting.GRAY).append(message),
					false
			);
		}
	}
}
