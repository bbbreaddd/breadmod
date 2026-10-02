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

public final class RecipeLookupFallback {
	private RecipeLookupFallback() {
	}

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

	public static EmiStack forUses(EmiIngredient ingredient) {
		EmiStack stack = itemStackForm(ingredient);
		if (stack == null) {
			return null;
		}
		EmiRecipeManager manager = EmiApi.getRecipeManager();
		if (!manager.getRecipesByInput(stack).isEmpty() || !workstationRecipes(stack).isEmpty()) {
			return null;
		}
		EmiStack plain = plain(stack);
		return manager.getRecipesByInput(plain).isEmpty() && workstationRecipes(plain).isEmpty() ? null : plain;
	}

	private static List<EmiRecipe> workstationRecipes(EmiStack stack) {
		return EmiRecipes.byWorkstation.getOrDefault(stack, List.of());
	}

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
