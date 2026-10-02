package dev.bbbreaddd.breadmod.storage;

import dev.bbbreaddd.breadmod.RefinedStorageSupport;
import dev.emi.emi.api.recipe.EmiPlayerInventory;
import dev.emi.emi.api.recipe.EmiRecipe;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

public final class RefinedStorageBackend implements StorageBackend {
	public static final RefinedStorageBackend INSTANCE = new RefinedStorageBackend();

	private RefinedStorageBackend() {
	}

	@Override
	public boolean supports(AbstractContainerScreen<?> screen) {
		return RefinedStorageSupport.isGridScreen(screen);
	}

	@Override
	public EmiPlayerInventory inventory(AbstractContainerScreen<?> screen) {
		return RefinedStorageSupport.getInventory(screen);
	}

	@Override
	public boolean canAutocraft(EmiRecipe recipe, AbstractContainerScreen<?> screen) {
		return RefinedStorageSupport.hasAutocraftableOutput(screen, recipe);
	}

	@Override
	public boolean requestAutocraft(EmiRecipe recipe, AbstractContainerScreen<?> screen) {
		return RefinedStorageSupport.openAutocrafting(screen, recipe);
	}
}
