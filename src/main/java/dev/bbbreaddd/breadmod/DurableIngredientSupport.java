package dev.bbbreaddd.breadmod;

import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.stack.Comparison;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

public final class DurableIngredientSupport {
	private static final String DAMAGE = "Damage";
	private static final Comparison WITHOUT_DAMAGE =
		Comparison.compareData(DurableIngredientSupport::dataWithoutDamage);

	private DurableIngredientSupport() {
	}

	public static void register(EmiRegistry registry) {
		register(registry, BreadmodConfig.aggressiveDurabilityNormalization());
	}

	public static void register(EmiRegistry registry, boolean aggressive) {
		for (Item item : ForgeRegistries.ITEMS) {
			ItemStack def = item.getDefaultInstance();
			if (!def.isDamageableItem()) {
				continue;
			}
			if (aggressive || declaresCraftingRemainder(item, def)) {
				registry.setDefaultComparison(item, WITHOUT_DAMAGE);
			}
		}
	}

	private static boolean declaresCraftingRemainder(Item item, ItemStack def) {
		try {
			if (item.hasCraftingRemainingItem(def)) {
				return true;
			}
			ItemStack remainder = item.getCraftingRemainingItem(def);
			return remainder != null && !remainder.isEmpty();
		} catch (RuntimeException | LinkageError exception) {
			MixinDiagnostics.warnOnce("remainder-probe",
				"could not probe crafting remainder for " + ForgeRegistries.ITEMS.getKey(item),
				exception);
			return false;
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
