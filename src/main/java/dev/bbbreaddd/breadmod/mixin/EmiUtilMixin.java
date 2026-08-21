package dev.bbbreaddd.breadmod.mixin;

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

/** Makes recipe-tree auto-resolution honor the same default recipe as ordinary recipe views. */
@Mixin(value = EmiUtil.class, remap = false)
public class EmiUtilMixin {
	@Inject(method = "getRecipeResolution", at = @At("HEAD"), cancellable = true)
	private static void breadmod$preferDefaultRecipe(EmiIngredient ingredient,
			EmiPlayerInventory inventory, CallbackInfoReturnable<EmiRecipe> cir) {
		if (ingredient.getEmiStacks().size() != 1 || ingredient.isEmpty()) {
			return;
		}
		EmiStack output = ingredient.getEmiStacks().get(0);
		EmiRecipe preferred = BoM.getRecipe(ingredient);
		if (preferred != null
				&& preferred.supportsRecipeTree()
				&& preferred.getOutputs().stream().anyMatch(stack -> stack.isEqual(output))) {
			cir.setReturnValue(preferred);
		}
	}
}
