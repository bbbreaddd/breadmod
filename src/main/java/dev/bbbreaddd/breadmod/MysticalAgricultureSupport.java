package dev.bbbreaddd.breadmod;

import java.util.List;

import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.stack.Comparison;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;

public final class MysticalAgricultureSupport {
	private static final List<ResourceLocation> INFUSION_CRYSTALS = List.of(
		ResourceLocation.tryParse("mysticalagriculture:infusion_crystal"),
		ResourceLocation.tryParse("matc:inferium_crystal"),
		ResourceLocation.tryParse("matc:prudentium_crystal"),
		ResourceLocation.tryParse("matc:tertium_crystal"),
		ResourceLocation.tryParse("matc:imperium_crystal"),
		ResourceLocation.tryParse("matc:supremium_crystal")
	);

	private MysticalAgricultureSupport() {
	}

	public static void register(EmiRegistry registry) {
		for (ResourceLocation id : INFUSION_CRYSTALS) {
			Item item = ForgeRegistries.ITEMS.getValue(id);
			if (item != null) {
				registry.setDefaultComparison(item, Comparison.DEFAULT_COMPARISON);
			}
		}
	}
}
