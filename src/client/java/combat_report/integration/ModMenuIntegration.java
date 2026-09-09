package combat_report.integration;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import combat_report.screen.CombatReportScreen;

/**
 * Mod Menu opens the recording screen directly.
 *
 * <p>There is no separate config screen because there is barely any config, and the
 * three settings there are live on the recording screen next to the thing they
 * affect. That is also why this mod pulls in no config library at all.
 */
public class ModMenuIntegration implements ModMenuApi {

	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return CombatReportScreen::new;
	}
}
