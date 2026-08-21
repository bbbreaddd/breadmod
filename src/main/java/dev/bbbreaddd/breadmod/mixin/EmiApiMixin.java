package dev.bbbreaddd.breadmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.bbbreaddd.breadmod.RecipeLookupFallback;

/**
 * Retries a recipe lookup that found nothing with the plain form of the item.
 *
 * <p>The substitution happens here rather than in the recipe manager because the caller filters what
 * the manager returns: {@code displayRecipes} keeps only the recipes whose outputs compare equal to
 * the stack it was given, and {@code displayUses} does the same with inputs. Recipes recovered by a
 * plain-form lookup do not survive that filter when the stack still carries the NBT that made the
 * lookup miss, so the stack itself has to be replaced before EMI starts.
 *
 * <p>Re-entering through the public method rather than rewriting the argument keeps the rest of EMI's
 * work — favourites, tags, the bill of materials — on its own code path. The second pass hands in a
 * stack with no NBT, which never qualifies for a retry, so it terminates.
 *
 * @see RecipeLookupFallback
 */
@Mixin(value = EmiApi.class, remap = false)
public class EmiApiMixin {
	@Inject(method = "displayRecipes", at = @At("HEAD"), cancellable = true)
	private static void breadmod$retrySourcesWithPlainForm(EmiIngredient stack, CallbackInfo ci) {
		EmiStack plain = RecipeLookupFallback.forSources(stack);
		if (plain != null) {
			ci.cancel();
			EmiApi.displayRecipes(plain);
		}
	}

	@Inject(method = "displayUses", at = @At("HEAD"), cancellable = true)
	private static void breadmod$retryUsesWithPlainForm(EmiIngredient stack, CallbackInfo ci) {
		EmiStack plain = RecipeLookupFallback.forUses(stack);
		if (plain != null) {
			ci.cancel();
			EmiApi.displayUses(plain);
		}
	}
}
