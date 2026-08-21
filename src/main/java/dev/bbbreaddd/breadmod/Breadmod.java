package dev.bbbreaddd.breadmod;

import com.mojang.logging.LogUtils;

import org.slf4j.Logger;

import net.minecraftforge.fml.common.Mod;

@Mod(Breadmod.MOD_ID)
public final class Breadmod {
	public static final String MOD_ID = "breadmod";

	private static final Logger LOGGER = LogUtils.getLogger();

	public Breadmod() {
		LOGGER.info("Breadmod loaded");
	}
}
