package dev.bbbreaddd.breadmod;

import java.util.List;

import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiPlayerInventory;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.handler.EmiCraftContext;
import dev.emi.emi.api.recipe.handler.EmiRecipeHandler;
import dev.emi.emi.api.widget.Widget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;

public class GridRecipeHandler implements EmiRecipeHandler<AbstractContainerMenu> {
	@Override
	public EmiPlayerInventory getInventory(AbstractContainerScreen<AbstractContainerMenu> screen) {
		return RefinedStorageSupport.getInventory(screen);
	}

	@Override
	public boolean supportsRecipe(EmiRecipe recipe) {
		EmiRecipe fillable = RefinedStorageSupport.fillable(recipe);
		AbstractContainerScreen<?> screen = EmiApi.getHandledScreen();
		return (fillable != null && usesDelegate(fillable, screen))
			|| RefinedStorageSupport.hasAutocraftableOutput(screen, recipe);
	}

	@Override
	public boolean canCraft(EmiRecipe recipe, EmiCraftContext<AbstractContainerMenu> context) {
		EmiRecipe fillable = RefinedStorageSupport.fillable(recipe);
		if (context.getType() == EmiCraftContext.Type.CRAFTABLE) {
			return fillable != null
				&& RefinedStorageSupport.isCraftingGrid(context.getScreen())
				&& RefinedStorageSupport.canCraftBacking(fillable, context.getInventory(), context.getScreen());
		}
		if (fillable != null && usesDelegate(fillable, context.getScreen())) {
			EmiRecipeHandler<AbstractContainerMenu> delegate =
				RefinedStorageSupport.delegate(context.getScreenHandler(), recipe);
			return delegate != null && delegate.canCraft(fillable, context);
		}
		return RefinedStorageSupport.hasAutocraftableOutput(context.getScreen(), recipe);
	}

	@Override
	public boolean craft(EmiRecipe recipe, EmiCraftContext<AbstractContainerMenu> context) {
		EmiRecipe fillable = RefinedStorageSupport.fillable(recipe);
		if (fillable != null && usesDelegate(fillable, context.getScreen())) {
			EmiRecipeHandler<AbstractContainerMenu> delegate =
				RefinedStorageSupport.delegate(context.getScreenHandler(), recipe);
			return delegate != null && delegate.craft(fillable, context);
		}
		return RefinedStorageSupport.openAutocrafting(context.getScreen(), recipe);
	}

	@Override
	public void render(EmiRecipe recipe, EmiCraftContext<AbstractContainerMenu> context,
			List<Widget> widgets, GuiGraphics graphics) {
		EmiRecipe fillable = RefinedStorageSupport.fillable(recipe);
		if (fillable != recipe || !usesDelegate(fillable, context.getScreen())) {
			return;
		}
		EmiRecipeHandler<AbstractContainerMenu> delegate =
			RefinedStorageSupport.delegate(context.getScreenHandler(), recipe);
		if (delegate != null) {
			delegate.render(recipe, context, widgets, graphics);
		}
	}

	private static boolean usesDelegate(EmiRecipe recipe, AbstractContainerScreen<?> screen) {
		return (RefinedStorageSupport.isCraftingGrid(screen)
				&& RefinedStorageSupport.isCraftable(recipe))
			|| (RefinedStorageSupport.isPatternGrid(screen)
				&& RefinedStorageSupport.isPatternTransfer(recipe));
	}
}
