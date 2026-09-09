package combat_report.record;

import com.google.gson.Gson;
import combat_report.combat.Constants;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Reads the reports folder back so past sessions can be listed in game.
 *
 * <p>Game-free, like the rest of this package. A file that will not parse is
 * skipped rather than failing the whole listing: one corrupt report should not make
 * the other fifty invisible.
 */
public final class SavedReports {

	private static final Gson GSON = new Gson();

	private SavedReports() {
	}

	/** One saved report: its two files and the data behind them. */
	public record Entry(Path json, Path html, ReportData data) {

		public String label() {
			String when = when();
			String who = opponents();
			return who.isEmpty() ? when : when + "  vs " + who;
		}

		public String detail() {
			ReportData.Summary s = this.data.summary;
			long fightSeconds = this.data.fightMs / 1000L;

			return String.format(
					Locale.ROOT,
					"%d fight%s, %dm %ds of combat - %.0f%% accuracy, %.1f hits per combo",
					this.data.fights,
					this.data.fights == 1 ? "" : "s",
					fightSeconds / 60L,
					fightSeconds % 60L,
					s.accuracyPct,
					s.avgComboHits
			);
		}

		/** "01-14 20:15" - the year and seconds are noise in a picker. */
		public String when() {
			String utc = this.data.startUtc;
			return utc.length() >= 16 ? utc.substring(5, 16) : utc;
		}

		public String opponents() {
			List<String> names = this.data.opponents;

			if (names.isEmpty()) {
				return "";
			}

			if (names.size() <= 2) {
				return String.join(", ", names);
			}

			return names.get(0) + ", " + names.get(1) + " +" + (names.size() - 2);
		}
	}

	public static List<Entry> list() {
		return list(SessionRecorder.dir());
	}

	public static List<Entry> list(Path dir) {
		List<Entry> out = new ArrayList<>();

		if (!Files.isDirectory(dir)) {
			return out;
		}

		try (Stream<Path> files = Files.list(dir)) {
			files.filter(Files::isRegularFile)
					.filter(p -> p.getFileName().toString().endsWith(".json"))
					.forEach(p -> {
						Entry e = read(p);

						if (e != null) {
							out.add(e);
						}
					});
		} catch (Exception ignored) {
			// A folder that cannot be listed shows as empty, which is what the
			// screen already says when there is nothing in it.
		}

		out.sort(Comparator.comparingLong((Entry e) -> e.data().startEpochMs).reversed());

		return out.size() > Constants.MAX_LISTED_REPORTS
				? new ArrayList<>(out.subList(0, Constants.MAX_LISTED_REPORTS))
				: out;
	}

	private static Entry read(Path json) {
		try {
			ReportData data = GSON.fromJson(Files.readString(json), ReportData.class);

			if (data == null) {
				return null;
			}

			if (data.summary == null) {
				data.summary = new ReportData.Summary();
			}

			String name = json.getFileName().toString();
			Path html = json.resolveSibling(name.substring(0, name.length() - 5) + ".html");

			return new Entry(json, html, data);
		} catch (Exception e) {
			return null;
		}
	}
}
