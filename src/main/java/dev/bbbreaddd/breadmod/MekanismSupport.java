package dev.bbbreaddd.breadmod;

import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.stack.Comparison;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;

public final class MekanismSupport {
	private static final ResourceLocation ENERGY_TABLET =
		ResourceLocation.tryParse("mekanism:energy_tablet");

	private MekanismSupport() {
	}

	public static void register(EmiRegistry registry) {
		Item item = ForgeRegistries.ITEMS.getValue(ENERGY_TABLET);
		if (item != null) {
			registry.setDefaultComparison(item, Comparison.DEFAULT_COMPARISON);
		}
	}
}
