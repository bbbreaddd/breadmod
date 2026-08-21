package dev.bbbreaddd.breadmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.bom.BoM;
import dev.bbbreaddd.breadmod.IngredientPreference;

/** Adds an automatic fallback only after EMI's saved and data-driven defaults found nothing. */
@Mixin(value = BoM.class, remap = false)
public class BoMMixin {
	@Inject(method = "getRecipe", at = @At("RETURN"), cancellable = true)
	private static void breadmod$resolveAmbiguousIngredient(EmiIngredient ingredient,
			CallbackInfoReturnable<EmiRecipe> cir) {
		if (cir.getReturnValue() == null) {
			EmiRecipe automatic = IngredientPreference.resolve(ingredient);
			if (automatic == null) {
				automatic = IngredientPreference.canonicalRecipe(ingredient);
				if (BoM.disabledRecipes.contains(automatic)) {
					automatic = null;
				}
			}
			if (automatic != null) {
				cir.setReturnValue(automatic);
			}
		}
	}
}
