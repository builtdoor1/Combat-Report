package combat_report.screen;

import combat_report.record.SavedReports;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;

/** The scrolling list of saved reports on {@link CombatReportScreen}. */
public class ReportList extends ObjectSelectionList<ReportList.Row> {

	private static final int ROW_HEIGHT = 30;
	private static final int INK = 0xFFE8ECF1;
	private static final int MUTED = 0xFF93A1B0;

	private final Consumer<SavedReports.Entry> onOpen;

	public ReportList(Minecraft minecraft, int width, int height, int top, Consumer<SavedReports.Entry> onOpen) {
		super(minecraft, width, height, top, ROW_HEIGHT);
		this.onOpen = onOpen;
	}

	public void refresh(List<SavedReports.Entry> entries) {
		this.clearEntries();

		for (SavedReports.Entry entry : entries) {
			this.addEntry(new Row(entry));
		}

		this.setScrollAmount(0.0);
	}

	@Override
	public int getRowWidth() {
		return Math.min(420, this.width - 40);
	}

	/** The report a row stands for, or null when nothing is selected. */
	public SavedReports.Entry selectedEntry() {
		Row row = this.getSelected();
		return row == null ? null : row.entry;
	}

	public class Row extends ObjectSelectionList.Entry<Row> {

		private final SavedReports.Entry entry;

		Row(SavedReports.Entry entry) {
			this.entry = entry;
		}

		@Override
		public void renderContent(GuiGraphics guiGraphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
			Minecraft mc = Minecraft.getInstance();
			int x = this.getX() + 6;
			int y = this.getY() + 4;

			guiGraphics.drawString(mc.font, this.entry.label(), x, y, INK);
			guiGraphics.drawString(mc.font, this.entry.detail(), x, y + 12, MUTED);
		}

		@Override
		public boolean mouseClicked(MouseButtonEvent mouseButtonEvent, boolean doubleClick) {
			ReportList.this.setSelected(this);

			if (doubleClick) {
				ReportList.this.onOpen.accept(this.entry);
			}

			return true;
		}

		@Override
		public Component getNarration() {
			return Component.literal(this.entry.label());
		}
	}
}
