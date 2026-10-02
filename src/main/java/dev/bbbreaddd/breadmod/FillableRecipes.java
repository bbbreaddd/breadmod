package dev.bbbreaddd.breadmod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import dev.emi.emi.EmiPort;
import dev.emi.emi.api.recipe.EmiCraftingRecipe;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.VanillaEmiRecipeCategories;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;

public final class FillableRecipes {
	private static final Map<EmiRecipe, EmiRecipe> rebuilt = new WeakHashMap<>();
	private static final Set<EmiRecipe> unusable = Collections.newSetFromMap(new WeakHashMap<>());

	private FillableRecipes() {
	}

	public static EmiRecipe rebuild(EmiRecipe recipe) {
		EmiRecipe cached = rebuilt.get(recipe);
		if (cached != null || unusable.contains(recipe)) {
			return cached;
		}
		EmiRecipe result = build(recipe);
		if (result == null) {
			unusable.add(recipe);
		} else {
			rebuilt.put(recipe, result);
		}
		return result;
	}

	private static EmiRecipe build(EmiRecipe recipe) {
		if (!recipe.getCategory().equals(VanillaEmiRecipeCategories.CRAFTING)) {
			return null;
		}
		Recipe<?> backing = recipe.getBackingRecipe();
		if (!(backing instanceof CraftingRecipe crafting) || !crafting.canCraftInDimensions(3, 3)) {
			return null;
		}
		try {
			List<Ingredient> ingredients = crafting.getIngredients();
			ItemStack output = EmiPort.getOutput(crafting);
			if (ingredients.isEmpty() || ingredients.size() > 9 || output.isEmpty()) {
				return null;
			}
			boolean shaped = crafting instanceof ShapedRecipe;
			List<EmiIngredient> input = shaped
				? pad((ShapedRecipe) crafting, ingredients)
				: ingredients.stream().map(EmiIngredient::of).toList();
			if (input.stream().allMatch(EmiIngredient::isEmpty)) {
				return null;
			}
			return new Rebuilt(input, EmiStack.of(output), crafting.getId(), !shaped, crafting);
		} catch (RuntimeException | LinkageError exception) {
			return null;
		}
	}

	private static List<EmiIngredient> pad(ShapedRecipe shaped, List<Ingredient> ingredients) {
		List<EmiIngredient> input = new ArrayList<>(9);
		int next = 0;
		for (int y = 0; y < 3; y++) {
			for (int x = 0; x < 3; x++) {
				if (x >= shaped.getWidth() || y >= shaped.getHeight() || next >= ingredients.size()) {
					input.add(EmiStack.EMPTY);
				} else {
					input.add(EmiIngredient.of(ingredients.get(next++)));
				}
			}
		}
		return input;
	}

	private static final class Rebuilt extends EmiCraftingRecipe {
		private final Recipe<?> backing;

		private Rebuilt(List<EmiIngredient> input, EmiStack output, ResourceLocation id,
				boolean shapeless, Recipe<?> backing) {
			super(input, output, id, shapeless);
			this.backing = backing;
		}

		@Override
		public Recipe<?> getBackingRecipe() {
			return backing;
		}
	}
}
