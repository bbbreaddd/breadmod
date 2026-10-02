package dev.bbbreaddd.breadmod;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.mojang.logging.LogUtils;
import dev.bbbreaddd.breadmod.GridSnapshot.StoredStack;
import dev.emi.emi.api.recipe.EmiPlayerInventory;
import dev.emi.emi.api.recipe.EmiRecipe;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

final class CraftabilityChecker {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static boolean crystalMatchLogged;
	private static boolean essenceMatchLogged;

	private CraftabilityChecker() {
	}

	static boolean canCraftBacking(EmiRecipe recipe, EmiPlayerInventory inventory, GridSnapshot snapshot) {
		Recipe<?> backing = recipe.getBackingRecipe();
		if (backing == null) {
			boolean result = inventory.canCraft(recipe) && satisfiesNbtJointly(recipe, snapshot);
			logCrystal(recipe, "no backing recipe; EMI accepted=" + result);
			logEssence(recipe, "no backing recipe; EMI accepted=" + result);
			return result;
		}
		try {
			List<int[]> matches = candidateSets(backing, snapshot);
			if (matches == null) {
				logEssence(recipe, "rejected: an ingredient has no acceptable stored variant");
				return false;
			}
			matches.sort(java.util.Comparator.comparingInt(a -> a.length));
			long[] remaining = snapshot.storedStacks.stream().mapToLong(StoredStack::amount).toArray();
			boolean result = assignIngredients(matches, remaining, 0);
			logEssence(recipe, "backing=" + backing.getClass().getName() + " ingredients="
				+ matches.size() + " storedVariants=" + snapshot.storedStacks.size() + " accepted=" + result);
			logCrystal(recipe, "backing=" + backing.getClass().getName() + " ingredients="
				+ matches.size() + " storedVariants=" + snapshot.storedStacks.size() + " accepted=" + result);
			return result;
		} catch (RuntimeException exception) {
			boolean result = inventory.canCraft(recipe);
			logEssence(recipe, "matcher threw " + exception.getClass().getName() + "; EMI accepted=" + result);
			logCrystal(recipe, "matcher threw; EMI accepted=" + result);
			return result;
		}
	}

	static boolean satisfiesNbtJointly(EmiRecipe recipe, GridSnapshot snapshot) {
		List<Ingredient> sensitive = new ArrayList<>();
		Recipe<?> backing = recipe.getBackingRecipe();
		if (backing == null) {
			return true;
		}
		try {
			for (Ingredient ingredient : backing.getIngredients()) {
				if (!ingredient.isEmpty() && namesStoredNbt(ingredient, snapshot)) {
					sensitive.add(ingredient);
				}
			}
		} catch (RuntimeException ignored) {
			return true;
		}
		if (sensitive.isEmpty()) {
			return true;
		}
		List<int[]> matches = new ArrayList<>();
		for (Ingredient ingredient : sensitive) {
			Set<Integer> candidates = new HashSet<>();
			for (ItemStack shape : ingredient.getItems()) {
				candidates.addAll(snapshot.storedStackIndices.getOrDefault(shape.getItem(), List.of()));
			}
			int[] accepted = candidates.stream()
				.filter(i -> ingredient.test(snapshot.storedStacks.get(i).stack()))
				.mapToInt(Integer::intValue).toArray();
			if (accepted.length == 0) {
				return false;
			}
			matches.add(accepted);
		}
		matches.sort(java.util.Comparator.comparingInt(a -> a.length));
		long[] remaining = snapshot.storedStacks.stream().mapToLong(StoredStack::amount).toArray();
		return assignIngredients(matches, remaining, 0);
	}

	static List<String> missingIngredients(EmiRecipe recipe, GridSnapshot snapshot) {
		List<String> missing = new ArrayList<>();
		Recipe<?> backing = recipe == null ? null : recipe.getBackingRecipe();
		if (backing == null) {
			return missing;
		}
		try {
			List<int[]> matches = new ArrayList<>();
			List<Ingredient> ordered = new ArrayList<>();
			for (Ingredient ingredient : backing.getIngredients()) {
				if (ingredient.isEmpty()) {
					continue;
				}
				ordered.add(ingredient);
				Set<Integer> candidates = new HashSet<>();
				for (ItemStack shape : ingredient.getItems()) {
					candidates.addAll(snapshot.storedStackIndices.getOrDefault(shape.getItem(), List.of()));
				}
				int[] accepted = candidates.stream()
					.filter(i -> ingredient.test(snapshot.storedStacks.get(i).stack()))
					.mapToInt(Integer::intValue).toArray();
				matches.add(accepted);
			}
			long[] remaining = snapshot.storedStacks.stream().mapToLong(StoredStack::amount).toArray();
			List<Integer> order = new ArrayList<>();
			for (int i = 0; i < matches.size(); i++) {
				order.add(i);
			}
			order.sort((a, b) -> Integer.compare(matches.get(a).length, matches.get(b).length));
			for (int index : order) {
				boolean satisfied = false;
				for (int stack : matches.get(index)) {
					if (remaining[stack] > 0) {
						remaining[stack]--;
						satisfied = true;
						break;
					}
				}
				if (!satisfied) {
					missing.add(describeIngredient(ordered.get(index)));
				}
			}
		} catch (RuntimeException ignored) {
		}
		return missing;
	}

	private static List<int[]> candidateSets(Recipe<?> backing, GridSnapshot snapshot) {
		List<int[]> matches = new ArrayList<>();
		for (Ingredient ingredient : backing.getIngredients()) {
			if (ingredient.isEmpty()) {
				continue;
			}
			Set<Integer> candidates = new HashSet<>();
			for (ItemStack shape : ingredient.getItems()) {
				candidates.addAll(snapshot.storedStackIndices.getOrDefault(shape.getItem(), List.of()));
			}
			int[] accepted = new int[candidates.size()];
			int size = 0;
			for (int i : candidates) {
				if (ingredient.test(snapshot.storedStacks.get(i).stack())) {
					accepted[size++] = i;
				}
			}
			if (size == 0) {
				return null;
			}
			matches.add(java.util.Arrays.copyOf(accepted, size));
		}
		return matches;
	}

	private static boolean namesStoredNbt(Ingredient ingredient, GridSnapshot snapshot) {
		for (ItemStack shape : ingredient.getItems()) {
			if (snapshot.nbtVariants.containsKey(shape.getItem())) {
				return true;
			}
		}
		return false;
	}

	private static boolean assignIngredients(List<int[]> matches, long[] remaining, int ingredient) {
		if (ingredient == matches.size()) {
			return true;
		}
		for (int stack : matches.get(ingredient)) {
			if (remaining[stack] > 0) {
				remaining[stack]--;
				if (assignIngredients(matches, remaining, ingredient + 1)) {
					return true;
				}
				remaining[stack]++;
			}
		}
		return false;
	}

	private static String describeIngredient(Ingredient ingredient) {
		ItemStack[] shapes = ingredient.getItems();
		if (shapes.length == 0) {
			return "<empty>";
		}
		String first = String.valueOf(ForgeRegistries.ITEMS.getKey(shapes[0].getItem()));
		return shapes.length == 1 ? first : first + " (or " + (shapes.length - 1) + " more)";
	}

	private static void logEssence(EmiRecipe recipe, String result) {
		if (!essenceMatchLogged && isEssenceRecipe(recipe)) {
			essenceMatchLogged = true;
			LOGGER.info("Breadmod essence diagnostic: matcher recipe {} {}", recipe.getId(), result);
		}
	}

	private static boolean isEssenceRecipe(EmiRecipe recipe) {
		return recipe != null && new net.minecraft.resources.ResourceLocation("matc", "prudentium_essence")
			.equals(recipe.getId());
	}

	private static void logCrystal(EmiRecipe recipe, String result) {
		if (crystalMatchLogged) {
			return;
		}
		for (var ingredient : recipe.getInputs()) {
			for (var stack : ingredient.getEmiStacks()) {
				if (stack.getKey() instanceof net.minecraft.world.item.Item item
						&& isInferiumCrystal(item)) {
					crystalMatchLogged = true;
					LOGGER.info("Breadmod crystal diagnostic: recipe {} {}", recipe.getId(), result);
					return;
				}
			}
		}
	}

	private static boolean isInferiumCrystal(net.minecraft.world.item.Item item) {
		var id = ForgeRegistries.ITEMS.getKey(item);
		return id != null && id.toString().equals("matc:inferium_crystal");
	}
}
