package dev.bbbreaddd.breadmod;

import java.util.List;

import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiPlayerInventory;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiResolutionRecipe;
import dev.emi.emi.api.recipe.VanillaEmiRecipeCategories;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.TagEmiIngredient;
import dev.emi.emi.bom.BoM;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.resources.ResourceLocation;

public final class IngredientPreference {
	private IngredientPreference() {
	}

	public static EmiRecipe resolve(EmiIngredient ingredient) {
		List<EmiStack> accepted = ingredient.getEmiStacks();
		if (accepted.size() < 2) {
			return null;
		}

		EmiStack selected = preferredVariant(ingredient, accepted);
		AbstractContainerScreen<?> screen = EmiApi.getHandledScreen();
		if (RefinedStorageSupport.isGridScreen(screen)) {
			selected = preferredAvailable(accepted, RefinedStorageSupport.getInventory(screen), selected);
		}
		return selected == null ? null : new EmiResolutionRecipe(ingredient, selected);
	}

	public static EmiRecipe improvePreferred(List<EmiRecipe> recipes, EmiRecipe original) {
		if (original == null) {
			return null;
		}
		EmiRecipe preferred = original;
		int preferredScore = preferenceScore(original);
		for (EmiRecipe recipe : recipes) {
			if (!recipe.supportsRecipeTree() || BoM.disabledRecipes.contains(recipe)) {
				continue;
			}
			int score = preferenceScore(recipe);
			if (preferred == null || score > preferredScore
					|| (score == preferredScore && stableId(recipe).compareTo(stableId(preferred)) < 0)) {
				preferred = recipe;
				preferredScore = score;
			}
		}
		return preferred;
	}

	private static int preferenceScore(EmiRecipe recipe) {
		ResourceLocation recipeId = recipe.getId();
		ResourceLocation categoryId = recipe.getCategory().getId();
		EmiStack output = recipe.getOutputs().isEmpty() ? EmiStack.EMPTY : recipe.getOutputs().get(0);
		String recipePath = recipeId == null ? "" : recipeId.getPath().toLowerCase();
		String categoryPath = categoryId == null ? "" : categoryId.getPath().toLowerCase();
		PreferenceInput input = new PreferenceInput(
			recipe.getCategory().equals(VanillaEmiRecipeCategories.CRAFTING),
			!output.isEmpty() && output.getId().equals(recipeId),
			categoryId != null && !output.isEmpty()
				&& output.getId().getNamespace().equals(categoryId.getNamespace()),
			categoryPath, recipePath);
		int score = 0;
		for (PreferenceRule rule : PREFERENCE_RULES) {
			score += rule.score(input);
		}
		return score;
	}

	private record PreferenceInput(boolean craftingCategory, boolean outputMatchesRecipeId,
			boolean sameNamespace, String categoryPath, String recipePath) {
	}

	private interface PreferenceRule {
		int score(PreferenceInput input);
	}

	private record FlagRule(java.util.function.Predicate<PreferenceInput> test, int weight)
			implements PreferenceRule {
		@Override
		public int score(PreferenceInput input) {
			return test.test(input) ? weight : 0;
		}
	}

	private record ContainsRule(java.util.function.Function<PreferenceInput, String> field,
			String needle, int weight) implements PreferenceRule {
		@Override
		public int score(PreferenceInput input) {
			String value = field.apply(input);
			return value != null && value.contains(needle) ? weight : 0;
		}
	}

	private static final List<PreferenceRule> PREFERENCE_RULES = List.of(
		new FlagRule(PreferenceInput::craftingCategory, 10_000),
		new FlagRule(PreferenceInput::outputMatchesRecipeId, 2_000),
		new FlagRule(PreferenceInput::sameNamespace, 1_000),
		new ContainsRule(PreferenceInput::categoryPath, "inscriber", 500),
		new ContainsRule(PreferenceInput::categoryPath, "reaction", -500),
		new ContainsRule(PreferenceInput::recipePath, "uncraft", -20_000),
		new ContainsRule(PreferenceInput::recipePath, "recycl", -20_000),
		new ContainsRule(PreferenceInput::recipePath, "reverse", -20_000));

	private static boolean containsAny(String value, String... needles) {
		for (String needle : needles) {
			if (value.contains(needle)) {
				return true;
			}
		}
		return false;
	}

	private static String stableId(EmiRecipe recipe) {
		ResourceLocation id = recipe.getId();
		return recipe.getCategory().getId() + "/" + (id == null ? "" : id);
	}

	private static EmiStack preferredVariant(EmiIngredient ingredient, List<EmiStack> accepted) {
		String tagPath = ingredient instanceof TagEmiIngredient tag
			? tag.key.location().getPath().toLowerCase()
			: "";
		EmiStack preferred = null;
		int preferredScore = Integer.MIN_VALUE;
		for (EmiStack stack : accepted) {
			if (stack.isEmpty()) {
				continue;
			}
			int score = variantScore(tagPath, stack);
			if (preferred == null || score > preferredScore
					|| (score == preferredScore && stack.getId().toString()
						.compareTo(preferred.getId().toString()) < 0)) {
				preferred = stack;
				preferredScore = score;
			}
		}
		return preferred;
	}

	private static int variantScore(String tagPath, EmiStack stack) {
		String itemPath = stack.getId().getPath().toLowerCase();
		int score = 0;
		if (!tagPath.isEmpty()) {
			if (tagPath.equals(itemPath) || tagPath.equals(itemPath + "s")) {
				score += 3_000;
			}
			if (tagPath.endsWith("_blocks")
					&& tagPath.substring(0, tagPath.length() - "_blocks".length()).equals(itemPath)) {
				score += 3_000;
			}
			if (singularize(tagPath).equals(singularize(itemPath))) {
				score += 2_000;
			}
		}
		if (stack.getId().getNamespace().equals("minecraft")) {
			score += 500;
		}
		if (containsAny(itemPath, "framed", "reinforced", "tinted", "chiseled",
				"decorative", "encased", "ornate")) {
			score -= 1_000;
		}
		return score - itemPath.length();
	}

	private static String singularize(String value) {
		String[] parts = value.split("_");
		for (int i = 0; i < parts.length; i++) {
			if (parts[i].length() > 1 && parts[i].endsWith("s") && !parts[i].endsWith("ss")) {
				parts[i] = parts[i].substring(0, parts[i].length() - 1);
			}
		}
		return String.join("_", parts);
	}

	private static EmiStack preferredAvailable(List<EmiStack> accepted, EmiPlayerInventory inventory,
			EmiStack fallback) {
		if (available(inventory, fallback) > 0) {
			return fallback;
		}
		EmiStack best = fallback;
		long bestAmount = 0;
		for (EmiStack candidate : accepted) {
			long amount = available(inventory, candidate);
			if (amount > bestAmount) {
				best = candidate;
				bestAmount = amount;
			}
		}
		return best;
	}

	private static long available(EmiPlayerInventory inventory, EmiStack candidate) {
		if (candidate == null) {
			return 0;
		}
		EmiStack stored = inventory.inventory.get(candidate);
		return stored == null ? 0 : stored.getAmount();
	}
}
