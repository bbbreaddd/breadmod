package dev.bbbreaddd.breadmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.bom.BoM;
import dev.bbbreaddd.breadmod.IngredientPreference;

@Mixin(value = BoM.class, remap = false)
public class BoMMixin {
	@Inject(method = "getRecipe", at = @At("RETURN"), cancellable = true, require = 0)
	private static void breadmod$resolveAmbiguousIngredient(EmiIngredient ingredient,
			CallbackInfoReturnable<EmiRecipe> cir) {
		if (cir.getReturnValue() != null
				|| !dev.bbbreaddd.breadmod.BreadmodConfig.autoResolveAmbiguous()) {
			return;
		}
		try {
			EmiRecipe automatic = IngredientPreference.resolve(ingredient);
			if (automatic != null) {
				cir.setReturnValue(automatic);
			}
		} catch (Throwable ignored) {
		}
	}
}
