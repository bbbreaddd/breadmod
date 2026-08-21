package dev.bbbreaddd.breadmod;

import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Method;
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
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;

public final class RefinedStorageSupport {
	private static final String GRID_SCREEN = "com.refinedmods.refinedstorage.screen.grid.GridScreen";
	private static final String CONTAINER_MENUS = "com.refinedmods.refinedstorage.RSContainerMenus";
	private static final String TRANSFER_HANDLER =
		"com.refinedmods.refinedstorage.integration.jei.GridRecipeTransferHandler";
	private static final String CRAFTING_SETTINGS_SCREEN =
		"com.refinedmods.refinedstorage.screen.grid.CraftingSettingsScreen";

	// The JEI bridge builds a fresh wrapper on every lookup, so hold onto one per menu rather than
	// allocating one per rendered frame. The inventory snapshot expires on a timer instead, so the
	// two caches must not share a field.
	private static AbstractContainerMenu delegateMenu;
	private static EmiRecipeHandler<AbstractContainerMenu> cachedDelegate;
	private static boolean delegateResolved;

	private static AbstractContainerScreen<?> gridTypeScreen;
	private static String gridType = "CRAFTING";

	// Refreshed with the inventory snapshot below and only read while that snapshot is current. Only
	// items that actually turn up carrying NBT are kept: for everything else EMI's item-keyed check
	// is already exact, and holding every stack of a large network would be a lot of garbage to
	// produce four times a second.
	private static final Map<Item, List<ItemStack>> nbtVariants = new HashMap<>();
	private static final Set<Item> plainItems = new HashSet<>();
	private static final Map<OutputKey, Object> autocraftableEntries = new HashMap<>();

	private static AbstractContainerScreen<?> inventoryScreen;
	private static EmiPlayerInventory cachedInventory;
	private static long cachedAt;

	private RefinedStorageSupport() {
	}

	public static boolean isGridScreen(AbstractContainerScreen<?> screen) {
		return screen != null && screen.getClass().getName().equals(GRID_SCREEN);
	}

	public static boolean isRefinedStorage(JemiRecipeHandler<?, ?> handler) {
		return handler.handler.getClass().getName().equals(TRANSFER_HANDLER);
	}

	/**
	 * The menu type of a Refined Storage Grid, or null if Refined Storage is not present in the shape
	 * this expects.
	 */
	@SuppressWarnings("unchecked")
	public static MenuType<AbstractContainerMenu> gridMenuType() {
		try {
			Object holder = Class.forName(CONTAINER_MENUS).getField("GRID").get(null);
			Object type = holder.getClass().getMethod("get").invoke(holder);
			if (type instanceof MenuType<?> menuType) {
				return (MenuType<AbstractContainerMenu>) menuType;
			}
		} catch (ReflectiveOperationException | RuntimeException ignored) {
			// Reported by the caller; there is nothing sensible to fall back to.
		}
		return null;
	}

	/**
	 * Refined Storage's own universal JEI handler, wrapped for EMI.
	 *
	 * <p>EMI's normal JEI lookup is keyed on the EMI category. That works for recipes imported from
	 * JEI, but not for native categories supplied by mods such as AE2. Refined Storage registers this
	 * handler as universal, so fall back to wrapping its singleton directly. The wrapper itself is
	 * category-independent and can safely be cached per menu.
	 */
	@SuppressWarnings("unchecked")
	public static EmiRecipeHandler<AbstractContainerMenu> delegate(AbstractContainerMenu menu,
			EmiRecipe recipe) {
		if (delegateMenu == menu && delegateResolved) {
			return cachedDelegate;
		}
		delegateMenu = menu;
		delegateResolved = true;
		cachedDelegate = null;
		EmiRecipeHandler<?> handler = EmiRecipeFiller.extraHandlers.apply(menu, recipe);
		if (handler instanceof JemiRecipeHandler<?, ?> jemi && isRefinedStorage(jemi)) {
			cachedDelegate = (EmiRecipeHandler<AbstractContainerMenu>) handler;
			return cachedDelegate;
		}
		try {
			Object instance = Class.forName(TRANSFER_HANDLER).getField("INSTANCE").get(null);
			if (instance instanceof IRecipeTransferHandler<?, ?> transferHandler) {
				cachedDelegate = (EmiRecipeHandler<AbstractContainerMenu>) (EmiRecipeHandler<?>)
					new JemiRecipeHandler<>((IRecipeTransferHandler) transferHandler);
			}
		} catch (ReflectiveOperationException | RuntimeException ignored) {
			// Refined Storage or its JEI integration is not available in the expected shape.
		}
		return cachedDelegate;
	}

	/**
	 * Only a Crafting Grid resolves its matrix against {@code RecipeType.CRAFTING} and hands back an
	 * item; a normal, fluid, or Pattern Grid never crafts anything, so nothing on those screens
	 * belongs in the craftables list.
	 */
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
		// If Refined Storage's API ever moves, keep the previous behaviour rather than silently
		// emptying the sidebar; the category filter below still removes the bogus entries.
		gridType = "CRAFTING";
		try {
			Object menu = screen.getMenu();
			Method getGrid = menu.getClass().getMethod("getGrid");
			Object grid = getGrid.invoke(menu);
			if (grid != null) {
				// Resolve against the declared IGrid interface: the implementing class is not public,
				// so a method looked up on it would fail to invoke.
				Object type = getGrid.getReturnType().getMethod("getGridType").invoke(grid);
				if (type instanceof Enum<?> value) {
					gridType = value.name();
				}
			}
		} catch (ReflectiveOperationException | RuntimeException ignored) {
			// Fall back to allowing craftables.
		}
		return gridType;
	}

	/** Whether a native EMI machine recipe can be written as an RS processing pattern. */
	public static boolean isPatternTransfer(EmiRecipe recipe) {
		return recipe != null
			&& recipe.supportsRecipeTree()
			&& !recipe.getInputs().isEmpty()
			&& !recipe.getOutputs().isEmpty();
	}

	/**
	 * Whether the Grid could ever produce this recipe's output.
	 *
	 * <p>Refined Storage registers with JEI as a <em>universal</em> transfer handler, so its
	 * {@code getRecipeType()} is null and EMI's {@code JemiRecipeHandler.supportsRecipe} accepts
	 * every recipe in the game. Vanilla EMI gets away with that because it then asks Refined Storage
	 * to dry-run the transfer, which we deliberately skip. Reinstate the filter EMI's own crafting
	 * table handler uses instead, or the sidebar advertises smelting, mob drops, and JEI-only
	 * informational recipes as craftable.
	 */
	public static boolean isCraftable(EmiRecipe recipe) {
		// An empty input list would pass EmiPlayerInventory.canCraft vacuously, so reject it too:
		// nothing is craftable out of nothing.
		if (recipe == null
				|| !recipe.getCategory().equals(VanillaEmiRecipeCategories.CRAFTING)
				|| !recipe.supportsRecipeTree()
				|| recipe.getInputs().isEmpty()) {
			return false;
		}
		Recipe<?> backing = recipe.getBackingRecipe();
		if (backing != null) {
			// The recipe knows its own shape, so a 3x1 recipe padded out to nine displayed slots is
			// judged on whether it fits the matrix rather than on how many slots EMI drew.
			return backing.canCraftInDimensions(3, 3);
		}
		return recipe.getInputs().size() <= 9;
	}

	/**
	 * The form of this recipe to answer questions about, which is the recipe itself unless it is one
	 * of the display-only ones {@link FillableRecipes} can rebuild. Null when there is nothing here
	 * a Grid could ever transfer.
	 *
	 * <p>Rebuilt recipes are offered on a Crafting Grid only. Refined Storage decides between laying
	 * a recipe out as a crafting pattern and laying it out as a processing one by asking whether the
	 * object EMI handed it is a vanilla {@code Recipe}, and a rebuilt recipe reaches it under a
	 * synthetic id that no recipe manager resolves, so on a Pattern Grid it would be written out as
	 * a processing pattern. A Crafting Grid transfers the same way either way.
	 */
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
		long now = System.currentTimeMillis();
		if (inventoryScreen != screen || cachedInventory == null || now - cachedAt > 250) {
			inventoryScreen = screen;
			cachedAt = now;
			cachedInventory = snapshot(screen);
		}
		return cachedInventory;
	}

	private static EmiPlayerInventory snapshot(AbstractContainerScreen<?> screen) {
		List<EmiStack> stacks = new ArrayList<>();
		nbtVariants.clear();
		plainItems.clear();
		autocraftableEntries.clear();
		try {
			Object view = screen.getClass().getMethod("getView").invoke(screen);
			if (view != null) {
				Object entries = view.getClass().getMethod("getAllStacks").invoke(view);
				if (entries instanceof Collection<?> collection) {
					for (Object entry : collection) {
						Method ingredientMethod = entry.getClass().getMethod("getIngredient");
						Object ingredient = ingredientMethod.invoke(entry);
						if (ingredient instanceof ItemStack itemStack) {
							boolean craftable = (boolean) entry.getClass()
								.getMethod("isCraftable").invoke(entry);
							if (craftable) {
								autocraftableEntries.put(
									(OutputKey) outputKey(EmiStack.of(itemStack)), entry);
							}
							int amount = (int) entry.getClass().getMethod("getQuantity").invoke(entry);
							if (amount > 0) {
								stacks.add(EmiStack.of(itemStack, amount));
								record(itemStack);
							}
						}
					}
				}
			}
		} catch (ReflectiveOperationException | RuntimeException ignored) {
			// The Grid view can be unavailable during its first initialization frame.
		}
		addCraftingMatrix(screen, stacks);
		if (Minecraft.getInstance().player != null) {
			for (ItemStack stack : Minecraft.getInstance().player.getInventory().items) {
				stacks.add(EmiStack.of(stack));
				record(stack);
			}
		}
		return new EmiPlayerInventory(stacks);
	}

	/** Whether the open Grid has an autocrafting pattern for any exact output of this recipe. */
	public static boolean hasAutocraftableOutput(AbstractContainerScreen<?> screen, EmiRecipe recipe) {
		if (!isGridScreen(screen) || recipe == null) {
			return false;
		}
		// Refreshes both the inventory and the craftable-entry index on the same short timer.
		getInventory(screen);
		return autocraftableEntry(recipe) != null;
	}

	/** Opens Refined Storage's normal amount-and-preview screen for this recipe's output. */
	public static boolean openAutocrafting(AbstractContainerScreen<?> screen, EmiRecipe recipe) {
		Object entry = autocraftableEntry(recipe);
		Minecraft minecraft = Minecraft.getInstance();
		if (!isGridScreen(screen) || entry == null || minecraft.player == null) {
			return false;
		}
		try {
			Class<?> settingsClass = Class.forName(CRAFTING_SETTINGS_SCREEN);
			for (var constructor : settingsClass.getConstructors()) {
				if (constructor.getParameterCount() == 3) {
					Object settings = constructor.newInstance(screen, minecraft.player, entry);
					if (settings instanceof net.minecraft.client.gui.screens.Screen settingsScreen) {
						minecraft.setScreen(settingsScreen);
						return true;
					}
				}
			}
		} catch (ReflectiveOperationException | RuntimeException ignored) {
			// Leave the recipe screen open if RS cannot create its settings screen.
		}
		return false;
	}

	private static Object autocraftableEntry(EmiRecipe recipe) {
		if (recipe == null) {
			return null;
		}
		for (EmiStack output : recipe.getOutputs()) {
			Object entry = autocraftableEntries.get(outputKey(output));
			if (entry != null) {
				return entry;
			}
		}
		return null;
	}

	/**
	 * Items sitting in the Grid's 3x3 matrix count as available, exactly as EMI's own crafting table
	 * handler counts its crafting slots among the input sources.
	 *
	 * <p>Without this, transferring a recipe looks like a net loss of items: Refined Storage pulls
	 * the ingredients out of network storage into the matrix, the snapshot stops seeing them, and
	 * every craftable they supported disappears. Everything after it in the list then shifts up,
	 * which reads as the whole page rearranging under the cursor, and transferring again shifts it
	 * back.
	 */
	private static void addCraftingMatrix(AbstractContainerScreen<?> screen, List<EmiStack> stacks) {
		try {
			Object menu = screen.getMenu();
			Method getGrid = menu.getClass().getMethod("getGrid");
			Object grid = getGrid.invoke(menu);
			if (grid == null) {
				return;
			}
			Object matrix = getGrid.getReturnType().getMethod("getCraftingMatrix").invoke(grid);
			if (matrix instanceof Container container) {
				for (int slot = 0; slot < container.getContainerSize(); slot++) {
					stacks.add(EmiStack.of(container.getItem(slot)));
					record(container.getItem(slot));
				}
			}
		} catch (ReflectiveOperationException | RuntimeException ignored) {
			// Grids without a crafting matrix simply contribute nothing.
		}
	}

	private static void record(ItemStack stack) {
		if (stack.isEmpty()) {
			return;
		}
		if (stack.getTag() == null) {
			plainItems.add(stack.getItem());
		} else {
			nbtVariants.computeIfAbsent(stack.getItem(), item -> new ArrayList<>()).add(stack);
		}
	}

	/**
	 * Whether the Grid really holds ingredients this recipe accepts, rather than items that merely
	 * look like them.
	 *
	 * <p>{@link EmiStack} hashes on the item alone and, unless a mod says otherwise, compares on
	 * nothing more, so items whose identity lives in their NBT all collapse into one entry. Tetra's
	 * scrolls are the clearest case: every scroll in the game is the single item
	 * {@code tetra:scroll_rolled}, so holding any scroll at all convinces EMI that the network holds
	 * the two specific ones a gilding recipe asks for.
	 *
	 * <p>The vanilla {@link Ingredient} is the authority on what the crafting grid accepts, so ask it
	 * directly. Only ingredients naming an item the Grid stores with NBT are worth asking about; for
	 * the rest EMI's answer was already exact. A plain vanilla ingredient ignores NBT, so consulting
	 * it can never reject something a crafting table would have accepted.
	 */
	public static boolean matchesIngredientNbt(EmiRecipe recipe) {
		Recipe<?> backing = recipe.getBackingRecipe();
		if (backing == null) {
			return true;
		}
		try {
			for (Ingredient ingredient : backing.getIngredients()) {
				if (!ingredient.isEmpty() && namesStoredNbt(ingredient) && !isSatisfied(ingredient)) {
					return false;
				}
			}
		} catch (RuntimeException ignored) {
			// A recipe that cannot describe its own ingredients keeps whatever EMI decided.
		}
		return true;
	}

	private static boolean namesStoredNbt(Ingredient ingredient) {
		for (ItemStack shape : ingredient.getItems()) {
			if (nbtVariants.containsKey(shape.getItem())) {
				return true;
			}
		}
		return false;
	}

	private static boolean isSatisfied(Ingredient ingredient) {
		for (ItemStack shape : ingredient.getItems()) {
			Item item = shape.getItem();
			List<ItemStack> variants = nbtVariants.get(item);
			if (variants != null) {
				for (ItemStack candidate : variants) {
					if (ingredient.test(candidate)) {
						return true;
					}
				}
			}
			if (plainItems.contains(item) && ingredient.test(new ItemStack(item))) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Recipes worth testing against the Grid's contents. Only recipes that consume something the
	 * Grid actually holds can ever be craftable, which is the same candidate set EMI itself uses;
	 * walking every known recipe instead costs seconds per rebuild on a large pack.
	 */
	public static Collection<EmiRecipe> candidates(EmiPlayerInventory inventory) {
		Set<EmiRecipe> recipes = Collections.newSetFromMap(new IdentityHashMap<>());
		EmiRecipeManager manager = EmiApi.getRecipeManager();
		for (EmiStack stack : inventory.inventory.keySet()) {
			recipes.addAll(manager.getRecipesByInput(stack));
		}
		return recipes;
	}

	/**
	 * A hashable identity for a recipe output. {@link EmiStack} itself is unusable as a map key
	 * here: its hash is only the item key, so every NBT variant of an item collides into one
	 * bucket and each lookup degrades into deep NBT comparisons.
	 */
	public static Object outputKey(EmiStack stack) {
		return new OutputKey(stack.getKey(), stack.getNbt());
	}

	private record OutputKey(Object key, CompoundTag nbt) {
	}

	/**
	 * A total, content-derived order for entries EMI's index cannot separate. Without it equal
	 * index values leave the order at the mercy of hash iteration, which changes wholesale whenever
	 * the candidate set resizes its table.
	 */
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

	// Set -Dbreadmod.debug=true to have the craftables list written out for inspection.
	private static final boolean DEBUG = Boolean.getBoolean("breadmod.debug");
	private static long lastDump;

	/**
	 * Writes every craftable with the recipe and category that justified it, so a report of
	 * "this isn't really craftable" can be traced to the recipe responsible.
	 */
	public static void debugDump(List<EmiFavorite.Craftable> craftables) {
		if (!DEBUG) {
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
		} catch (IOException | RuntimeException ignored) {
			// Diagnostics are best effort.
		}
	}
}
