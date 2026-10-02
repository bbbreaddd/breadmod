package dev.bbbreaddd.breadmod.storage;

import dev.emi.emi.api.recipe.EmiPlayerInventory;
import dev.emi.emi.api.recipe.EmiRecipe;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

public interface StorageBackend {
	boolean supports(AbstractContainerScreen<?> screen);

	EmiPlayerInventory inventory(AbstractContainerScreen<?> screen);

	boolean canAutocraft(EmiRecipe recipe, AbstractContainerScreen<?> screen);

	boolean requestAutocraft(EmiRecipe recipe, AbstractContainerScreen<?> screen);
}
