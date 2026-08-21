package dev.bbbreaddd.breadmod;

import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.stack.Comparison;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;

/** Compatibility for reusable Mystical Agriculture crafting ingredients. */
public final class MysticalAgricultureSupport {
	private static final ResourceLocation INFUSION_CRYSTAL =
		ResourceLocation.tryParse("mysticalagriculture:infusion_crystal");

	private MysticalAgricultureSupport() {
	}

	/**
	 * Makes a used Infusion Crystal match the pristine stack shown in recipes.
	 *
	 * <p>The crystal stores its remaining durability in the stack's {@code Damage} NBT, but its
	 * recipes use an ordinary vanilla item/tag ingredient and therefore accept any unbroken crystal.
	 * EMI's recipe index and inventory availability maps both use the registered stack comparison,
	 * so an NBT-sensitive comparison would otherwise hide every crystal recipe after the first use.
	 */
	public static void register(EmiRegistry registry) {
		Item item = ForgeRegistries.ITEMS.getValue(INFUSION_CRYSTAL);
		if (item != null) {
			registry.setDefaultComparison(item, Comparison.DEFAULT_COMPARISON);
		}
	}
}
