package dev.bbbreaddd.breadmod;

import dev.emi.emi.api.recipe.EmiRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.registries.ForgeRegistries;

public final class CucumberSupport {
	private static final ResourceLocation SHAPED_NO_MIRROR =
		ResourceLocation.tryParse("cucumber:shaped_no_mirror");

	private CucumberSupport() {
	}

	public static boolean hasAbsolutePosition(EmiRecipe emiRecipe) {
		Recipe<?> recipe = emiRecipe.getBackingRecipe();
		if (recipe == null) {
			return false;
		}
		ResourceLocation serializer = ForgeRegistries.RECIPE_SERIALIZERS.getKey(recipe.getSerializer());
		return SHAPED_NO_MIRROR.equals(serializer);
	}
}
