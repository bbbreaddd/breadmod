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
		MysticalAgricultureSupport.register(registry);

		MenuType<AbstractContainerMenu> type = RefinedStorageSupport.gridMenuType();
		if (type == null) {
			// Nothing else in the mod does anything without the Grid, so say so once rather than
			// leaving an inert sidebar to be puzzled over.
			LOGGER.warn("Could not resolve the Refined Storage Grid menu type; craftables disabled");
			return;
		}
		registry.addRecipeHandler(type, new GridRecipeHandler());
	}
}
