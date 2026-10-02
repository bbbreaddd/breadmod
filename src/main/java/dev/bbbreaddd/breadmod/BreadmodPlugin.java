package dev.bbbreaddd.breadmod;

import com.mojang.logging.LogUtils;

import org.slf4j.Logger;

import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;

@EmiEntrypoint
public class BreadmodPlugin implements EmiPlugin {
	private static final Logger LOGGER = LogUtils.getLogger();

	@Override
	public void register(EmiRegistry registry) {
		if (BreadmodConfig.reusableIngredientFix()) {
			safely(() -> DurableIngredientSupport.register(registry), "durability comparison");
			safely(() -> MysticalAgricultureSupport.register(registry), "mystical agriculture comparison");
			safely(() -> MekanismSupport.register(registry), "mekanism comparison");
		}

		MenuType<AbstractContainerMenu> type = RefinedStorageSupport.gridMenuType();
		if (type == null) {
			LOGGER.warn("Could not resolve the Refined Storage Grid menu type; craftables disabled");
			return;
		}
		registry.addRecipeHandler(type, new GridRecipeHandler());
	}

	private static void safely(Runnable task, String name) {
		try {
			task.run();
		} catch (RuntimeException exception) {
			LOGGER.warn("Breadmod {} fix failed; continuing without it", name, exception);
		}
	}
}
