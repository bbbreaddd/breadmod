package dev.bbbreaddd.breadmod;

import com.mojang.logging.LogUtils;

import org.slf4j.Logger;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod(Breadmod.MOD_ID)
public final class Breadmod {
	public static final String MOD_ID = "breadmod";

	private static final Logger LOGGER = LogUtils.getLogger();

	public Breadmod() {
		ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, BreadmodConfig.SPEC);
		if (FMLEnvironment.dist == Dist.CLIENT) {
			registerConfigScreen();
		}
		LOGGER.info("Breadmod loaded");
	}

	private static void registerConfigScreen() {
		try {
			ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
				() -> new ConfigScreenHandler.ConfigScreenFactory(
					(minecraft, parent) -> new BreadmodConfigScreen(parent)));
		} catch (RuntimeException exception) {
			LOGGER.warn("Could not register Breadmod config screen", exception);
		}
	}
}
