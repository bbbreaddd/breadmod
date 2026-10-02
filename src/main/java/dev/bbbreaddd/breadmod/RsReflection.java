package dev.bbbreaddd.breadmod;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class RsReflection {
	static final String GRID_SCREEN = "com.refinedmods.refinedstorage.screen.grid.GridScreen";
	static final String CONTAINER_MENUS = "com.refinedmods.refinedstorage.RSContainerMenus";
	static final String TRANSFER_HANDLER =
		"com.refinedmods.refinedstorage.integration.jei.GridRecipeTransferHandler";
	static final String CRAFTING_SETTINGS_SCREEN =
		"com.refinedmods.refinedstorage.screen.grid.CraftingSettingsScreen";

	private static final Map<Class<?>, Methods> ENTRY_METHODS = new ConcurrentHashMap<>();
	private static final Map<Class<?>, ViewMethods> VIEW_METHODS = new ConcurrentHashMap<>();
	private static final Map<Class<?>, GridAccess> GRID_ACCESS = new ConcurrentHashMap<>();

	private RsReflection() {
	}

	static Methods entryMethods(Class<?> entryClass) {
		return ENTRY_METHODS.computeIfAbsent(entryClass, type -> {
			try {
				return new Methods(
					type.getMethod("getIngredient"),
					type.getMethod("getQuantity"),
					type.getMethod("isCraftable"));
			} catch (NoSuchMethodException | SecurityException ignored) {
				return null;
			}
		});
	}

	static Method allStacksMethod(Class<?> viewClass) {
		ViewMethods cached = VIEW_METHODS.computeIfAbsent(viewClass, type -> {
			try {
				return new ViewMethods(type.getMethod("getAllStacks"));
			} catch (NoSuchMethodException | SecurityException ignored) {
				return null;
			}
		});
		return cached == null ? null : cached.allStacks();
	}

	static GridAccess gridAccess(Class<?> menuClass) {
		return GRID_ACCESS.computeIfAbsent(menuClass, type -> {
			try {
				Method getGrid = type.getMethod("getGrid");
				Method getMatrix = null;
				Method getGridType = null;
				try {
					getMatrix = getGrid.getReturnType().getMethod("getCraftingMatrix");
				} catch (NoSuchMethodException | SecurityException ignored) {
				}
				try {
					getGridType = getGrid.getReturnType().getMethod("getGridType");
				} catch (NoSuchMethodException | SecurityException ignored) {
				}
				return new GridAccess(getGrid, getMatrix, getGridType);
			} catch (NoSuchMethodException | SecurityException ignored) {
				return null;
			}
		});
	}

	static Method screenViewMethod(Class<?> screenClass) {
		try {
			return screenClass.getMethod("getView");
		} catch (NoSuchMethodException | SecurityException ignored) {
			return null;
		}
	}

	static Object gridMenuTypeInstance() {
		try {
			Object holder = Class.forName(CONTAINER_MENUS).getField("GRID").get(null);
			return holder.getClass().getMethod("get").invoke(holder);
		} catch (ReflectiveOperationException | RuntimeException ignored) {
			return null;
		}
	}

	static Object transferHandlerInstance() {
		try {
			Field field = Class.forName(TRANSFER_HANDLER).getField("INSTANCE");
			return field.get(null);
		} catch (ReflectiveOperationException | RuntimeException ignored) {
			return null;
		}
	}

	static Constructor<?> craftingSettingsConstructor() {
		try {
			Class<?> settingsClass = Class.forName(CRAFTING_SETTINGS_SCREEN);
			for (Constructor<?> constructor : settingsClass.getConstructors()) {
				if (constructor.getParameterCount() == 3) {
					return constructor;
				}
			}
		} catch (ReflectiveOperationException | RuntimeException ignored) {
		}
		return null;
	}

	record Methods(Method ingredient, Method quantity, Method craftable) {
	}

	private record ViewMethods(Method allStacks) {
	}

	record GridAccess(Method getGrid, Method getMatrix, Method getGridType) {
	}
}
