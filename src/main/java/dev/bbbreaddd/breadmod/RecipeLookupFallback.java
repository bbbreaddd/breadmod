package dev.bbbreaddd.breadmod;

import java.util.List;

import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeManager;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.registry.EmiRecipes;
import dev.emi.emi.runtime.EmiFavorite;
import net.minecraft.world.item.ItemStack;

/**
 * Falls back to the plain form of an item when its exact form has no recipes indexed.
 *
 * <p>EMI indexes recipes in maps keyed by {@code EmiStack}, and a mod may declare that two stacks of
 * an item only count as the same stack when some part of their NBT agrees — Sophisticated Backpacks
 * does this for every backpack, on dye colours and render info. The lookup maps have no fallback, so
 * once a backpack has been used, pressing R or U on it asks for a key nothing was ever filed under
 * and the recipe screen comes up empty. The item is not special: the same happens to anything whose
 * mod registers a comparison and then hands the player a stack that differs in the compared data.
 *
 * <p>The retry has to use the plain item rather than a copy with the comparison cleared. The lookup
 * maps hash with {@code EmiStackList.ComparisonHashStrategy}, which ignores whatever comparison a
 * stack carries and always applies the one registered for the item, so a cleared copy hashes and
 * compares exactly like the original and would miss again.
 */
public final class RecipeLookupFallback {
	private RecipeLookupFallback() {
	}

	/**
	 * The stack {@code displayRecipes} should look up instead, or null to leave it alone.
	 */
	public static EmiStack forSources(EmiIngredient ingredient) {
		EmiStack stack = itemStackForm(ingredient);
		if (stack == null) {
			return null;
		}
		EmiRecipeManager manager = EmiApi.getRecipeManager();
		if (!manager.getRecipesByOutput(stack).isEmpty()) {
			return null;
		}
		EmiStack plain = plain(stack);
		return manager.getRecipesByOutput(plain).isEmpty() ? null : plain;
	}

	/**
	 * The stack {@code displayUses} should look up instead, or null to leave it alone.
	 */
	public static EmiStack forUses(EmiIngredient ingredient) {
		EmiStack stack = itemStackForm(ingredient);
		if (stack == null) {
			return null;
		}
		EmiRecipeManager manager = EmiApi.getRecipeManager();
		// Workstations are indexed separately, and substituting would discard whatever they found.
		if (!manager.getRecipesByInput(stack).isEmpty() || !workstationRecipes(stack).isEmpty()) {
			return null;
		}
		EmiStack plain = plain(stack);
		return manager.getRecipesByInput(plain).isEmpty() && workstationRecipes(plain).isEmpty() ? null : plain;
	}

	private static List<EmiRecipe> workstationRecipes(EmiStack stack) {
		return EmiRecipes.byWorkstation.getOrDefault(stack, List.of());
	}

	/**
	 * The single item stack this ingredient stands for, if it is one and carries NBT. Tags and lists
	 * are left out because EMI answers those from somewhere other than the by-stack maps, and a stack
	 * without NBT is left out because the retry would look up the same key it just missed on.
	 */
	private static EmiStack itemStackForm(EmiIngredient ingredient) {
		if (ingredient instanceof EmiFavorite favorite) {
			ingredient = favorite.getStack();
		}
		if (!(ingredient instanceof EmiStack stack)) {
			return null;
		}
		ItemStack item = stack.getItemStack();
		return !item.isEmpty() && item.getTag() != null ? stack : null;
	}

	private static EmiStack plain(EmiStack stack) {
		return EmiStack.of(stack.getItemStack().getItem());
	}
}
