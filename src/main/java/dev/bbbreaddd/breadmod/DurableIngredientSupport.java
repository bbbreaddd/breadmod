package dev.bbbreaddd.breadmod;

import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.stack.Comparison;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;

public final class DurableIngredientSupport {
	private static final String DAMAGE = "Damage";
	private static final Comparison WITHOUT_DAMAGE =
		Comparison.compareData(DurableIngredientSupport::dataWithoutDamage);

	private DurableIngredientSupport() {
	}

	public static void register(EmiRegistry registry) {
		for (Item item : ForgeRegistries.ITEMS) {
			if (item.getDefaultInstance().isDamageableItem()) {
				registry.setDefaultComparison(item, WITHOUT_DAMAGE);
			}
		}
	}

	private static CompoundTag dataWithoutDamage(EmiStack stack) {
		CompoundTag tag = stack.getNbt();
		if (tag == null) {
			return null;
		}
		CompoundTag normalized = tag.copy();
		normalized.remove(DAMAGE);
		return normalized.isEmpty() ? null : normalized;
	}
}
