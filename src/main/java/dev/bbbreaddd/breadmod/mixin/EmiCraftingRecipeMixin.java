package dev.bbbreaddd.breadmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import dev.bbbreaddd.breadmod.CucumberSupport;
import dev.emi.emi.api.recipe.EmiCraftingRecipe;

@Mixin(value = EmiCraftingRecipe.class, remap = false)
public class EmiCraftingRecipeMixin {
	@Redirect(
		method = "addWidgets",
		at = @At(
			value = "INVOKE",
			target = "Ldev/emi/emi/api/recipe/EmiCraftingRecipe;canFit(II)Z"
		),
		require = 0
	)
	private boolean breadmod$doNotCenterAbsoluteRecipes(EmiCraftingRecipe recipe,
			int width, int height) {
		return !CucumberSupport.hasAbsolutePosition(recipe) && recipe.canFit(width, height);
	}
}
