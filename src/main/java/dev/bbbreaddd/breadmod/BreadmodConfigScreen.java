package dev.bbbreaddd.breadmod;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public class BreadmodConfigScreen extends Screen {
	private final Screen parent;

	public BreadmodConfigScreen(Screen parent) {
		super(Component.literal("Breadmod Compatibility"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int center = this.width / 2;
		int row = this.height / 2 - 90;
		final int rowHeight = 24;
		final int widgetWidth = Math.min(340, this.width - 40);

		addToggle(center, row, widgetWidth, "RS craftables replacement",
			BreadmodConfig.rsCraftablesReplacement(),
			value -> BreadmodConfig.RS_CRAFTABLES_REPLACEMENT.set(value));
		row += rowHeight;
		addToggle(center, row, widgetWidth, "Automatic ambiguous-ingredient resolution",
			BreadmodConfig.autoResolveAmbiguous(),
			value -> BreadmodConfig.AUTO_RESOLVE_AMBIGUOUS.set(value));
		row += rowHeight;
		addToggle(center, row, widgetWidth, "Canonical recipe preference",
			BreadmodConfig.canonicalRecipePreference(),
			value -> BreadmodConfig.CANONICAL_RECIPE_PREFERENCE.set(value));
		row += rowHeight;
		addToggle(center, row, widgetWidth, "NBT recipe lookup fallback",
			BreadmodConfig.nbtLookupFallback(),
			value -> BreadmodConfig.NBT_LOOKUP_FALLBACK.set(value));
		row += rowHeight;
		addToggle(center, row, widgetWidth, "Reusable-ingredient comparison fix",
			BreadmodConfig.reusableIngredientFix(),
			value -> BreadmodConfig.REUSABLE_INGREDIENT_FIX.set(value));
		row += rowHeight;
		addToggle(center, row, widgetWidth, "Debug diagnostics file",
			BreadmodConfig.debugDiagnostics(),
			value -> BreadmodConfig.DEBUG_DIAGNOSTICS.set(value));
		row += rowHeight;

		this.addRenderableWidget(new RefreshSlider(center - widgetWidth / 2, row, widgetWidth, 20));
		row += rowHeight + 6;

		this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> {
			BreadmodConfig.SPEC.save();
			if (this.minecraft != null) {
				this.minecraft.setScreen(parent);
			}
		}).bounds(center - 100, row, 200, 20).build());
	}

	private void addToggle(int center, int y, int width, String name, boolean current,
			java.util.function.Consumer<Boolean> setter) {
		this.addRenderableWidget(CycleButton.onOffBuilder(current)
			.displayOnlyValue()
			.create(center - width / 2, y, width, 20, Component.literal(name),
				(button, value) -> setter.accept(value)));
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		this.renderBackground(graphics);
		graphics.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - 110, 0xFFFFFF);
		super.render(graphics, mouseX, mouseY, partialTick);
	}

	@Override
	public void onClose() {
		BreadmodConfig.SPEC.save();
		if (this.minecraft != null) {
			this.minecraft.setScreen(parent);
		}
	}

	private static final class RefreshSlider extends AbstractSliderButton {
		RefreshSlider(int x, int y, int width, int height) {
			super(x, y, width, height,
				Component.literal("Snapshot refresh: " + BreadmodConfig.snapshotRefreshMs() + " ms"),
				(clamp(BreadmodConfig.snapshotRefreshMs()) - 50.0) / 4950.0);
		}

		private static int clamp(int value) {
			return Math.max(50, Math.min(5000, value));
		}

		@Override
		protected void updateMessage() {
			int ms = 50 + (int) Math.round(this.value * 4950.0 / 50.0) * 50;
			this.setMessage(Component.literal("Snapshot refresh: " + ms + " ms"));
		}

		@Override
		protected void applyValue() {
			int ms = 50 + (int) Math.round(this.value * 4950.0 / 50.0) * 50;
			BreadmodConfig.SNAPSHOT_REFRESH_MS.set(ms);
		}
	}
}
