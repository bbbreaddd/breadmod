package dev.bbbreaddd.breadmod;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.bbbreaddd.breadmod.GridSnapshot.OutputKey;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiPlayerInventory;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeManager;
import dev.emi.emi.api.recipe.VanillaEmiRecipeCategories;
import dev.emi.emi.api.recipe.handler.EmiRecipeHandler;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.jemi.JemiRecipeHandler;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.registry.EmiRecipeFiller;
import dev.emi.emi.runtime.EmiFavorite;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Recipe;

public final class RefinedStorageSupport {
	private static AbstractContainerMenu delegateMenu;
	private static EmiRecipeHandler<AbstractContainerMenu> cachedDelegate;
	private static boolean delegateResolved;

	private static AbstractContainerScreen<?> gridTypeScreen;
	private static String gridType = "CRAFTING";

	private static final GridSnapshot SNAPSHOT = new GridSnapshot();

	private static EmiRecipeManager statefulRecipeManager;
	private static final Map<Item, List<EmiRecipe>> STATEFUL_CACHE = new HashMap<>();
	private static boolean statefulFullIndexBuilt;
	private static Map<Item, List<EmiRecipe>> statefulRecipes = Map.of();

	private RefinedStorageSupport() {
	}

	public static boolean isGridScreen(AbstractContainerScreen<?> screen) {
		return screen != null && screen.getClass().getName().equals(RsReflection.GRID_SCREEN);
	}

	public static boolean isRefinedStorage(JemiRecipeHandler<?, ?> handler) {
		return handler.handler.getClass().getName().equals(RsReflection.TRANSFER_HANDLER);
	}

	@SuppressWarnings("unchecked")
	public static MenuType<AbstractContainerMenu> gridMenuType() {
		try {
			Object type = RsReflection.gridMenuTypeInstance();
			if (type instanceof MenuType<?> menuType) {
				return (MenuType<AbstractContainerMenu>) menuType;
			}
		} catch (RuntimeException | LinkageError exception) {
			MixinDiagnostics.warnOnce("grid-menu-type",
				"could not resolve RS grid menu type", exception);
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	public static EmiRecipeHandler<AbstractContainerMenu> delegate(AbstractContainerMenu menu,
			EmiRecipe recipe) {
		if (delegateMenu == menu && delegateResolved) {
			return cachedDelegate;
		}
		delegateMenu = menu;
		delegateResolved = true;
		cachedDelegate = null;
		try {
			EmiRecipeHandler<?> handler = EmiRecipeFiller.extraHandlers.apply(menu, recipe);
			if (handler instanceof JemiRecipeHandler<?, ?> jemi && isRefinedStorage(jemi)) {
				cachedDelegate = (EmiRecipeHandler<AbstractContainerMenu>) handler;
				return cachedDelegate;
			}
		} catch (RuntimeException | LinkageError exception) {
			MixinDiagnostics.warnOnce("delegate-handler",
				"could not resolve RS recipe handler", exception);
		}
		try {
			Object instance = RsReflection.transferHandlerInstance();
			if (instance instanceof IRecipeTransferHandler<?, ?> transferHandler) {
				cachedDelegate = (EmiRecipeHandler<AbstractContainerMenu>) (EmiRecipeHandler<?>)
					new JemiRecipeHandler<>((IRecipeTransferHandler) transferHandler);
			}
		} catch (RuntimeException | LinkageError exception) {
			MixinDiagnostics.warnOnce("transfer-handler",
				"could not instantiate RS transfer handler", exception);
		}
		return cachedDelegate;
	}

	public static boolean isCraftingGrid(AbstractContainerScreen<?> screen) {
		return gridType(screen).equals("CRAFTING");
	}

	public static boolean isPatternGrid(AbstractContainerScreen<?> screen) {
		return gridType(screen).equals("PATTERN");
	}

	private static String gridType(AbstractContainerScreen<?> screen) {
		if (!isGridScreen(screen)) {
			return "";
		}
		if (gridTypeScreen == screen) {
			return gridType;
		}
		gridTypeScreen = screen;
		gridType = "CRAFTING";
		try {
			Object menu = screen.getMenu();
			if (menu == null) {
				return gridType;
			}
			RsReflection.GridAccess access = RsReflection.gridAccess(menu.getClass());
			if (access == null || access.getGridType() == null) {
				return gridType;
			}
			Object grid = access.getGrid().invoke(menu);
			if (grid != null) {
				Object type = access.getGridType().invoke(grid);
				if (type instanceof Enum<?> value) {
					gridType = value.name();
				}
			}
		} catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
			MixinDiagnostics.warnOnce("grid-type",
				"could not read RS grid type; assuming crafting grid", exception);
		}
		return gridType;
	}

	public static boolean isPatternTransfer(EmiRecipe recipe) {
		return recipe != null
			&& recipe.supportsRecipeTree()
			&& !recipe.getInputs().isEmpty()
			&& !recipe.getOutputs().isEmpty();
	}

	public static boolean isCraftable(EmiRecipe recipe) {
		if (recipe == null
				|| !recipe.getCategory().equals(VanillaEmiRecipeCategories.CRAFTING)
				|| !recipe.supportsRecipeTree()
				|| recipe.getInputs().isEmpty()) {
			return false;
		}
		Recipe<?> backing = recipe.getBackingRecipe();
		if (backing != null) {
			return backing.canCraftInDimensions(3, 3);
		}
		return recipe.getInputs().size() <= 9;
	}

	public static EmiRecipe fillable(EmiRecipe recipe) {
		if (recipe.supportsRecipeTree()) {
			return recipe;
		}
		if (!isCraftingGrid(EmiApi.getHandledScreen())) {
			return null;
		}
		return FillableRecipes.rebuild(recipe);
	}

	public static EmiPlayerInventory getInventory(AbstractContainerScreen<?> screen) {
		GridSnapshot snapshot = snapshotFor(screen);
		return new EmiPlayerInventory(new ArrayList<>(snapshot.stacks));
	}

	static GridSnapshot snapshotFor(AbstractContainerScreen<?> screen) {
		if (!SNAPSHOT.freshFor(screen)) {
			SNAPSHOT.refresh(screen);
		}
		return SNAPSHOT;
	}

	public static boolean hasAutocraftableOutput(AbstractContainerScreen<?> screen, EmiRecipe recipe) {
		if (!isGridScreen(screen) || recipe == null) {
			return false;
		}
		return snapshotFor(screen).autocraftableEntryFor(recipe) != null;
	}

	public static boolean openAutocrafting(AbstractContainerScreen<?> screen, EmiRecipe recipe) {
		Object entry = snapshotFor(screen).autocraftableEntryFor(recipe);
		Minecraft minecraft = Minecraft.getInstance();
		if (!isGridScreen(screen) || entry == null || minecraft.player == null) {
			return false;
		}
		try {
			var constructor = RsReflection.craftingSettingsConstructor();
			if (constructor == null) {
				return false;
			}
			Object settings = constructor.newInstance(screen, minecraft.player, entry);
			if (settings instanceof net.minecraft.client.gui.screens.Screen settingsScreen) {
				minecraft.setScreen(settingsScreen);
				return true;
			}
		} catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
			MixinDiagnostics.warnOnce("autocraft-screen",
				"could not open RS autocrafting screen", exception);
		}
		return false;
	}

	public static boolean canCraftBacking(EmiRecipe recipe, EmiPlayerInventory inventory) {
		return CraftabilityChecker.canCraftBacking(
			recipe, inventory, snapshotFor(EmiApi.getHandledScreen()));
	}

	public static boolean canCraftBacking(EmiRecipe recipe, EmiPlayerInventory inventory,
			AbstractContainerScreen<?> screen) {
		return CraftabilityChecker.canCraftBacking(recipe, inventory, snapshotFor(screen));
	}

	public static boolean matchesIngredientNbt(EmiRecipe recipe) {
		return CraftabilityChecker.satisfiesNbtJointly(
			recipe, snapshotFor(EmiApi.getHandledScreen()));
	}

	public static boolean matchesIngredientNbt(EmiRecipe recipe, AbstractContainerScreen<?> screen) {
		return CraftabilityChecker.satisfiesNbtJointly(recipe, snapshotFor(screen));
	}

	public static List<String> missingIngredients(EmiRecipe recipe, AbstractContainerScreen<?> screen) {
		return CraftabilityChecker.missingIngredients(recipe, snapshotFor(screen));
	}

	public static Collection<EmiRecipe> candidates(EmiPlayerInventory inventory) {
		Set<EmiRecipe> recipes = Collections.newSetFromMap(new IdentityHashMap<>());
		EmiRecipeManager manager = EmiApi.getRecipeManager();
		for (EmiStack stack : inventory.inventory.keySet()) {
			recipes.addAll(manager.getRecipesByInput(stack));
			if (stack.getKey() instanceof Item item && stack.getNbt() != null) {
				recipes.addAll(manager.getRecipesByInput(EmiStack.of(item)));
				recipes.addAll(fallbackRecipes(manager, item));
			}
		}
		return recipes;
	}

	private static List<EmiRecipe> fallbackRecipes(EmiRecipeManager manager, Item item) {
		if (statefulRecipeManager != manager) {
			statefulRecipeManager = manager;
			STATEFUL_CACHE.clear();
			statefulFullIndexBuilt = false;
			statefulRecipes = Map.of();
		}
		List<EmiRecipe> cached = STATEFUL_CACHE.get(item);
		if (cached != null) {
			return cached;
		}
		if (statefulFullIndexBuilt) {
			return statefulRecipes.getOrDefault(item, List.of());
		}
		if (STATEFUL_CACHE.size() >= 12) {
			statefulRecipes = buildFullIndex(manager);
			statefulFullIndexBuilt = true;
			return statefulRecipes.getOrDefault(item, List.of());
		}
		List<EmiRecipe> found = scanRecipesForItem(manager, item);
		STATEFUL_CACHE.put(item, found);
		return found;
	}

	private static List<EmiRecipe> scanRecipesForItem(EmiRecipeManager manager, Item item) {
		List<EmiRecipe> found = new ArrayList<>();
		for (EmiRecipe recipe : manager.getRecipes()) {
			if (!recipe.getCategory().equals(VanillaEmiRecipeCategories.CRAFTING)) {
				continue;
			}
			if (recipeUsesItem(recipe, item)) {
				found.add(recipe);
			}
		}
		return found;
	}

	private static boolean recipeUsesItem(EmiRecipe recipe, Item item) {
		try {
			for (EmiIngredient ingredient : recipe.getInputs()) {
				for (EmiStack stack : ingredient.getEmiStacks()) {
					if (stack.getKey() instanceof Item key && key.equals(item)) {
						return true;
					}
				}
			}
		} catch (RuntimeException | LinkageError exception) {
			MixinDiagnostics.warnOnce("recipe-scan",
				"recipe scan failed for " + recipe.getId(), exception);
		}
		return false;
	}

	private static Map<Item, List<EmiRecipe>> buildFullIndex(EmiRecipeManager manager) {
		Map<Item, List<EmiRecipe>> byItem = new HashMap<>();
		for (EmiRecipe recipe : manager.getRecipes()) {
			if (!recipe.getCategory().equals(VanillaEmiRecipeCategories.CRAFTING)) {
				continue;
			}
			Set<Item> indexed = new HashSet<>();
			for (EmiIngredient ingredient : recipe.getInputs()) {
				for (EmiStack stack : ingredient.getEmiStacks()) {
					if (stack.getKey() instanceof Item item && indexed.add(item)) {
						byItem.computeIfAbsent(item, key -> new ArrayList<>()).add(recipe);
					}
				}
			}
		}
		statefulRecipes = byItem;
		return statefulRecipes;
	}

	public static Object outputKey(EmiStack stack) {
		return new OutputKey(stack.getKey(), stack.getNbt());
	}

	static Object outputKey(Object key, CompoundTag nbt) {
		return new OutputKey(key, nbt);
	}

	public static String sortKey(EmiFavorite.Craftable craftable) {
		EmiRecipe recipe = craftable.getRecipe();
		if (recipe != null && recipe.getId() != null) {
			return recipe.getId().toString();
		}
		return String.valueOf(craftable.getStack().getEmiStacks().get(0).getKey());
	}

	private static String describe(EmiIngredient ingredient) {
		List<EmiStack> stacks = ingredient.getEmiStacks();
		if (stacks.isEmpty()) {
			return "<empty>";
		}
		String first = String.valueOf(stacks.get(0).getKey());
		return stacks.size() == 1 ? first : first + "+" + (stacks.size() - 1);
	}

	public static void debugDump(List<EmiFavorite.Craftable> craftables) {
		if (!BreadmodConfig.debugDiagnostics()) {
			return;
		}
		long now = System.currentTimeMillis();
		if (now - lastDump < 10_000) {
			return;
		}
		lastDump = now;
		Path path = Minecraft.getInstance().gameDirectory.toPath().resolve("breadmod-craftables.txt");
		try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
			writer.write(craftables.size() + " craftables\n");
			for (EmiFavorite.Craftable craftable : craftables) {
				EmiRecipe recipe = craftable.getRecipe();
				writer.write(craftable.getStack().getEmiStacks().get(0).getKey()
					+ " | category=" + (recipe == null ? "?" : recipe.getCategory().getId())
					+ " | recipe=" + (recipe == null ? "?" : recipe.getId())
					+ " | inputs=" + (recipe == null ? "?" : recipe.getInputs().stream()
						.map(RefinedStorageSupport::describe).toList())
					+ "\n");
			}
		} catch (IOException | RuntimeException | LinkageError exception) {
			MixinDiagnostics.warnOnce("craftables-dump",
				"could not write breadmod-craftables.txt", exception);
		}
	}

	private static long lastDump;
}
