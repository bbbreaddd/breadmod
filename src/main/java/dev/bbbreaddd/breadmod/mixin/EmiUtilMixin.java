package dev.bbbreaddd.breadmod.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.emi.emi.EmiUtil;
import dev.emi.emi.api.recipe.EmiPlayerInventory;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.bom.BoM;
import dev.bbbreaddd.breadmod.IngredientPreference;

@Mixin(value = EmiUtil.class, remap = false)
public class EmiUtilMixin {
	@Inject(
		method = "getPreferredRecipe(Ljava/util/List;Ldev/emi/emi/api/recipe/EmiPlayerInventory;Z)Ldev/emi/emi/api/recipe/EmiRecipe;",
		at = @At("RETURN"),
		cancellable = true,
		require = 0
	)
	private static void breadmod$improveAutomaticRecipe(List<EmiRecipe> recipes,
			EmiPlayerInventory inventory, boolean requireCraftable,
			CallbackInfoReturnable<EmiRecipe> cir) {
		if (requireCraftable || !dev.bbbreaddd.breadmod.BreadmodConfig.canonicalRecipePreference()) {
			return;
		}
		try {
			cir.setReturnValue(IngredientPreference.improvePreferred(recipes, cir.getReturnValue()));
		} catch (Throwable ignored) {
		}
	}

	@Inject(method = "getRecipeResolution", at = @At("HEAD"), cancellable = true, require = 0)
	private static void breadmod$preferDefaultRecipe(EmiIngredient ingredient,
			EmiPlayerInventory inventory, CallbackInfoReturnable<EmiRecipe> cir) {
		if (ingredient.getEmiStacks().size() != 1 || ingredient.isEmpty()
				|| !dev.bbbreaddd.breadmod.BreadmodConfig.canonicalRecipePreference()) {
			return;
		}
		try {
			EmiStack output = ingredient.getEmiStacks().get(0);
			EmiRecipe preferred = BoM.getRecipe(ingredient);
			if (preferred != null
					&& preferred.supportsRecipeTree()
					&& preferred.getOutputs().stream().anyMatch(stack -> stack.isEqual(output))) {
				cir.setReturnValue(preferred);
			}
		} catch (Throwable ignored) {
		}
	}
}
