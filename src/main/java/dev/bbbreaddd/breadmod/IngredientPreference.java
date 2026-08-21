package dev.bbbreaddd.breadmod;

import java.util.List;

import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiPlayerInventory;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiResolutionRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

/**
 * Supplies a useful last-resort resolution for recipe ingredients with several accepted stacks.
 *
 * <p>EMI deliberately leaves these unresolved until the player picks one. That is valuable when
 * the choice changes the recipe tree, but makes routine recipes ask the same question repeatedly.
 * EMI's saved user choice and data-pack defaults are consulted before this class, so an explicit
 * preference always wins.
 */
public final class IngredientPreference {
	private IngredientPreference() {
	}

	/**
	 * Builds EMI's ordinary resolution recipe for the best automatic candidate, or {@code null} if
	 * this ingredient is not actually ambiguous.
	 */
	public static EmiRecipe resolve(EmiIngredient ingredient) {
		List<EmiStack> accepted = ingredient.getEmiStacks();
		if (accepted.size() < 2) {
			return null;
		}

		EmiStack selected = firstUsable(accepted);
		AbstractContainerScreen<?> screen = EmiApi.getHandledScreen();
		if (RefinedStorageSupport.isGridScreen(screen)) {
			selected = mostAvailable(accepted, RefinedStorageSupport.getInventory(screen), selected);
		}
		return selected == null ? null : new EmiResolutionRecipe(ingredient, selected);
	}

	/**
	 * Finds the conventional primary recipe for a single item.
	 *
	 * <p>Many mods give their main recipe the same id as its output and suffix reverse conversions
	 * with names such as {@code _uncraft}. EMI otherwise breaks equal-scoring recipes by registration
	 * order, which can make a storage-block unpacking recipe win in the recipe tree. Matching ids are
	 * a strong, mod-supplied signal that avoids hard-coding individual items or recipe namespaces.
	 */
	public static EmiRecipe canonicalRecipe(EmiIngredient ingredient) {
		List<EmiStack> stacks = ingredient.getEmiStacks();
		if (stacks.size() != 1 || stacks.get(0).isEmpty()) {
			return null;
		}
		EmiStack output = stacks.get(0);
		for (EmiRecipe recipe : EmiApi.getRecipeManager().getRecipesByOutput(output)) {
			if (output.getId().equals(recipe.getId())
					&& recipe.supportsRecipeTree()
					&& recipe.getOutputs().stream().anyMatch(stack -> stack.isEqual(output))) {
				return recipe;
			}
		}
		return null;
	}

	/**
	 * Tag/list order is meaningful data supplied by the recipe or mod author, and is the safest
	 * deterministic fallback when none of the accepted variants is currently available.
	 */
	private static EmiStack firstUsable(List<EmiStack> accepted) {
		for (EmiStack stack : accepted) {
			if (!stack.isEmpty()) {
				return stack;
			}
		}
		return null;
	}

	/** Prefer the accepted stack with the greatest quantity in the open Grid's combined view. */
	private static EmiStack mostAvailable(List<EmiStack> accepted, EmiPlayerInventory inventory,
			EmiStack fallback) {
		EmiStack best = fallback;
		long bestAmount = 0;
		for (EmiStack candidate : accepted) {
			EmiStack stored = inventory.inventory.get(candidate);
			if (stored != null && stored.getAmount() > bestAmount) {
				best = candidate;
				bestAmount = stored.getAmount();
			}
		}
		return best;
	}
}
