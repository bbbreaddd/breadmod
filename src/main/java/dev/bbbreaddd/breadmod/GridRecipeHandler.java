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

/**
 * EMI's handler for a Refined Storage Grid.
 *
 * <p>Registered against the Grid's menu type, so EMI resolves it before the handler the JEI bridge
 * synthesizes from Refined Storage's transfer handler. That matters because the bridged handler
 * reports an empty inventory and answers every question by dry-running the transfer, which walks the
 * whole network view; asking it once per recipe in the game, as the craftables sidebar does, locks
 * the screen up.
 *
 * <p>Only the sidebar needs that replacement, so everything else is delegated straight back to
 * Refined Storage rather than reimplemented. In particular the fill button keeps Refined Storage's
 * own answers, including the ctrl-click autocrafting request it offers when ingredients are missing.
 */
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
			// Answered from the snapshot: this runs for every candidate recipe.
			return fillable != null
				&& RefinedStorageSupport.isCraftingGrid(context.getScreen())
				&& context.getInventory().canCraft(fillable)
				&& RefinedStorageSupport.matchesIngredientNbt(fillable);
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
		// Refined Storage's feedback shades the slots the screen drew, matching them by identity
		// against the recipe's own ingredients. A rebuilt recipe does not own those slots, so every
		// missing ingredient would be shaded in the corner instead; leave the recipe unshaded rather
		// than mark the wrong thing. Filling still works, ctrl+click autocrafting included.
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
