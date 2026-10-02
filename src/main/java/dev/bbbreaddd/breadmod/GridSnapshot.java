package dev.bbbreaddd.breadmod;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.mojang.logging.LogUtils;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

final class GridSnapshot {
	private static final Logger LOGGER = LogUtils.getLogger();

	private AbstractContainerScreen<?> screen;
	private long takenAt;
	private boolean valid;

	final List<EmiStack> stacks = new ArrayList<>();
	final Map<Item, List<ItemStack>> nbtVariants = new HashMap<>();
	final Set<Item> plainItems = new HashSet<>();
	final List<StoredStack> storedStacks = new ArrayList<>();
	final Map<Item, List<Integer>> storedStackIndices = new HashMap<>();
	final Map<OutputKey, Object> autocraftableEntries = new HashMap<>();

	boolean freshFor(AbstractContainerScreen<?> screen) {
		long interval = Math.max(50, BreadmodConfig.snapshotRefreshMs());
		return valid && this.screen == screen && System.currentTimeMillis() - takenAt < interval;
	}

	void refresh(AbstractContainerScreen<?> screen) {
		this.screen = screen;
		this.takenAt = System.currentTimeMillis();
		this.valid = true;
		stacks.clear();
		nbtVariants.clear();
		plainItems.clear();
		storedStacks.clear();
		storedStackIndices.clear();
		autocraftableEntries.clear();
		scanNetwork(screen);
		addCraftingMatrix(screen);
		if (Minecraft.getInstance().player != null) {
			for (ItemStack stack : Minecraft.getInstance().player.getInventory().items) {
				stacks.add(EmiStack.of(stack));
				record(stack, stack.getCount());
			}
		}
		if (screen != null && screen.getMenu() != null) {
			ItemStack carried = screen.getMenu().getCarried();
			record(carried, carried.getCount());
		}
	}

	private void scanNetwork(AbstractContainerScreen<?> screen) {
		if (screen == null) {
			return;
		}
		try {
			Method getView = RsReflection.screenViewMethod(screen.getClass());
			if (getView == null) {
				return;
			}
			Object view = getView.invoke(screen);
			if (view == null) {
				return;
			}
			Method getAllStacks = RsReflection.allStacksMethod(view.getClass());
			if (getAllStacks == null) {
				return;
			}
			Object entries = getAllStacks.invoke(view);
			if (!(entries instanceof Collection<?> collection)) {
				return;
			}
			RsReflection.Methods cached = null;
			Class<?> cachedClass = null;
			for (Object entry : collection) {
				if (entry == null) {
					continue;
				}
				if (entry.getClass() != cachedClass) {
					cachedClass = entry.getClass();
					cached = RsReflection.entryMethods(cachedClass);
				}
				if (cached == null) {
					return;
				}
				Object ingredient = cached.ingredient().invoke(entry);
				if (!(ingredient instanceof ItemStack itemStack)) {
					continue;
				}
				boolean craftable = (boolean) cached.craftable().invoke(entry);
				if (craftable) {
					autocraftableEntries.put(new OutputKey(
						EmiStack.of(itemStack).getKey(), EmiStack.of(itemStack).getNbt()), entry);
				}
				int amount = (int) cached.quantity().invoke(entry);
				if (amount > 0) {
					stacks.add(EmiStack.of(itemStack, amount));
					record(itemStack, amount);
				}
			}
		} catch (ReflectiveOperationException | RuntimeException ignored) {
		}
	}

	private void addCraftingMatrix(AbstractContainerScreen<?> screen) {
		if (screen == null || screen.getMenu() == null) {
			return;
		}
		try {
			Object menu = screen.getMenu();
			RsReflection.GridAccess access = RsReflection.gridAccess(menu.getClass());
			if (access == null || access.getMatrix() == null) {
				return;
			}
			Object grid = access.getGrid().invoke(menu);
			if (grid == null) {
				return;
			}
			Object matrix = access.getMatrix().invoke(grid);
			if (matrix instanceof Container container) {
				for (int slot = 0; slot < container.getContainerSize(); slot++) {
					ItemStack stack = container.getItem(slot);
					stacks.add(EmiStack.of(stack));
					record(stack, stack.getCount());
				}
			}
		} catch (ReflectiveOperationException | RuntimeException ignored) {
		}
	}

	private void record(ItemStack stack, long amount) {
		if (stack.isEmpty() || amount <= 0) {
			return;
		}
		int index = storedStacks.size();
		storedStacks.add(new StoredStack(stack.copyWithCount(1), amount));
		storedStackIndices.computeIfAbsent(stack.getItem(), item -> new ArrayList<>()).add(index);
		if (stack.getTag() == null) {
			plainItems.add(stack.getItem());
		} else {
			nbtVariants.computeIfAbsent(stack.getItem(), item -> new ArrayList<>()).add(stack);
		}
	}

	Object autocraftableEntryFor(dev.emi.emi.api.recipe.EmiRecipe recipe) {
		if (recipe == null) {
			return null;
		}
		for (EmiStack output : recipe.getOutputs()) {
			Object entry = autocraftableEntries.get(new OutputKey(output.getKey(), output.getNbt()));
			if (entry != null) {
				return entry;
			}
		}
		return null;
	}

	record StoredStack(ItemStack stack, long amount) {
	}

	record OutputKey(Object key, CompoundTag nbt) {
	}
}
