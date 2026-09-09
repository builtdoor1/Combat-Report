package combat_report.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The handful of things that are genuinely a preference.
 *
 * <p>Nothing that changes a measurement lives here. Those are in
 * {@code combat_report.combat.Constants}, deliberately out of reach, because two
 * reports are only comparable if both were measured the same way.
 */
public final class CrConfig {

	private static final Logger LOGGER = LoggerFactory.getLogger("combat-report");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private static CrConfig instance;

	/** Show the recording indicator on the HUD. */
	public boolean showRecordingHud = true;

	/** Print the saved-report path to chat when a recording stops. */
	public boolean chatMessages = true;

	/** Open the report in a browser as soon as it is written. */
	public boolean openReportOnStop = false;

	public static CrConfig get() {
		if (instance == null) {
			instance = load();
		}

		return instance;
	}

	public static Path configDir() {
		return FabricLoader.getInstance().getConfigDir().resolve("combat_report");
	}

	private static Path configFile() {
		return configDir().resolve("config.json");
	}

	private static CrConfig load() {
		Path file = configFile();

		if (!Files.isRegularFile(file)) {
			return new CrConfig();
		}

		try {
			CrConfig loaded = GSON.fromJson(Files.readString(file), CrConfig.class);
			return loaded == null ? new CrConfig() : loaded;
		} catch (Exception e) {
			LOGGER.warn("[combat-report] could not read config.json, using defaults", e);
			return new CrConfig();
		}
	}

	public void save() {
		try {
			Files.createDirectories(configDir());
			Files.writeString(configFile(), GSON.toJson(this));
		} catch (Exception e) {
			LOGGER.warn("[combat-report] could not write config.json", e);
		}
	}
}
