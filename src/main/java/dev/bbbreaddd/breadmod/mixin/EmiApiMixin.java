package dev.bbbreaddd.breadmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.emi.emi.api.EmiApi;
import dev.emi.emi.EmiUtil;
import dev.emi.emi.api.recipe.EmiPlayerInventory;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.bbbreaddd.breadmod.RecipeLookupFallback;
import dev.emi.emi.bom.BoM;
import net.minecraft.client.Minecraft;

@Mixin(value = EmiApi.class, remap = false)
public class EmiApiMixin {
	@Redirect(
		method = "displayRecipes",
		at = @At(value = "INVOKE", target = "Ldev/emi/emi/bom/BoM;getRecipe(Ldev/emi/emi/api/stack/EmiIngredient;)Ldev/emi/emi/api/recipe/EmiRecipe;"),
		require = 0
	)
	private static EmiRecipe breadmod$focusUsefulRecipe(EmiIngredient ingredient) {
		try {
			EmiRecipe explicit = BoM.getRecipe(ingredient);
			if (explicit != null) {
				return explicit;
			}
			return EmiUtil.getPreferredRecipe(ingredient,
				EmiPlayerInventory.of(Minecraft.getInstance().player), false);
		} catch (Throwable ignored) {
			return null;
		}
	}

	@Inject(method = "displayRecipes", at = @At("HEAD"), cancellable = true, require = 0)
	private static void breadmod$retrySourcesWithPlainForm(EmiIngredient stack, CallbackInfo ci) {
		if (!dev.bbbreaddd.breadmod.BreadmodConfig.nbtLookupFallback()) {
			return;
		}
		try {
			EmiStack plain = RecipeLookupFallback.forSources(stack);
			if (plain != null) {
				ci.cancel();
				EmiApi.displayRecipes(plain);
			}
		} catch (Throwable ignored) {
		}
	}

	@Inject(method = "displayUses", at = @At("HEAD"), cancellable = true, require = 0)
	private static void breadmod$retryUsesWithPlainForm(EmiIngredient stack, CallbackInfo ci) {
		if (!dev.bbbreaddd.breadmod.BreadmodConfig.nbtLookupFallback()) {
			return;
		}
		try {
			EmiStack plain = RecipeLookupFallback.forUses(stack);
			if (plain != null) {
				ci.cancel();
				EmiApi.displayUses(plain);
			}
		} catch (Throwable ignored) {
		}
	}
}
