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

/**
 * Rebuilds display-only crafting recipes into something a workstation can actually fill.
 *
 * <p>A mod that wants to show one entry standing in for many variants — Sophisticated Backpacks'
 * tier upgrades are the case that prompted this — commonly registers each variant with EMI
 * indexing either its inputs or its outputs, never both, so that the same physical recipe is not
 * offered several times over in search results. EMI reads that as a recipe that does not fit the
 * recipe tree, and every handler in the game, including EMI's own crafting table one and the mod's
 * own, then refuses it: the fill button reads "current workstation does not support recipe".
 *
 * <p>The information needed to fill it has not gone anywhere, though. Those recipes still hand back
 * a real {@link CraftingRecipe} from {@code getBackingRecipe}, with the concrete ingredients and
 * result of the variant on screen. Rebuilding an ordinary crafting recipe from it gives the Grid
 * something it can transfer, without this mod knowing anything about the mod that registered it.
 */
public final class FillableRecipes {
	// Keyed by identity, since EmiRecipe does not define equality. Weak so that a reload, which
	// discards every EmiRecipe and builds new ones, drops these with them.
	private static final Map<EmiRecipe, EmiRecipe> rebuilt = new WeakHashMap<>();
	private static final Set<EmiRecipe> unusable = Collections.newSetFromMap(new WeakHashMap<>());

	private FillableRecipes() {
	}

	/**
	 * An equivalent of this recipe that supports the recipe tree, or null if there is not enough
	 * left in it to rebuild one.
	 */
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
		} catch (RuntimeException ignored) {
			// A recipe that cannot describe itself outside of its own screen keeps EMI's answer.
			return null;
		}
	}

	/**
	 * Lays a shaped recipe out across the nine slots EMI draws, the same way EMI itself does, so
	 * that a 2x2 recipe is not read as filling the top row of the matrix.
	 */
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

	/**
	 * Carries the recipe it was rebuilt from, so the ingredient checks that consult the vanilla
	 * recipe keep working; the inherited lookup would go looking for the display recipe's id, which
	 * is a synthetic one no recipe manager has ever heard of.
	 */
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
