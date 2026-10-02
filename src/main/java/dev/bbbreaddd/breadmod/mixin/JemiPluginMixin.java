package dev.bbbreaddd.breadmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.bbbreaddd.breadmod.DurableIngredientSupport;
import dev.bbbreaddd.breadmod.MekanismSupport;
import dev.bbbreaddd.breadmod.MixinDiagnostics;
import dev.bbbreaddd.breadmod.MysticalAgricultureSupport;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.jemi.JemiPlugin;

@Mixin(value = JemiPlugin.class, remap = false)
public class JemiPluginMixin {
	@Inject(method = "parseSubtypes", at = @At("RETURN"), require = 0)
	private void breadmod$restoreReusableIngredientComparisons(EmiRegistry registry,
			CallbackInfo ci) {
		if (!dev.bbbreaddd.breadmod.BreadmodConfig.reusableIngredientFix()) {
			return;
		}
		try {
			DurableIngredientSupport.register(registry);
		} catch (RuntimeException | LinkageError exception) {
			MixinDiagnostics.warnOnce("durable-comparison",
				"durability comparison fix failed", exception);
		}
		try {
			MysticalAgricultureSupport.register(registry);
		} catch (RuntimeException | LinkageError exception) {
			MixinDiagnostics.warnOnce("mystical-comparison",
				"mystical agriculture comparison fix failed", exception);
		}
		try {
			MekanismSupport.register(registry);
		} catch (RuntimeException | LinkageError exception) {
			MixinDiagnostics.warnOnce("mekanism-comparison",
				"mekanism comparison fix failed", exception);
		}
	}
}
