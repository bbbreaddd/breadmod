package dev.bbbreaddd.breadmod.mixin;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiPlayerInventory;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.registry.EmiStackList;
import dev.emi.emi.runtime.EmiFavorite;
import dev.bbbreaddd.breadmod.MixinDiagnostics;
import dev.bbbreaddd.breadmod.RefinedStorageSupport;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

@Mixin(value = EmiPlayerInventory.class, remap = false)
public abstract class EmiPlayerInventoryMixin {
	@Inject(method = "getCraftables", at = @At("HEAD"), cancellable = true, require = 0)
	private void breadmod$deduplicateOutputs(CallbackInfoReturnable<List<EmiIngredient>> cir) {
		if (!dev.bbbreaddd.breadmod.BreadmodConfig.rsCraftablesReplacement()) {
			return;
		}
		try {
			breadmod$buildCraftables(cir);
		} catch (RuntimeException | LinkageError exception) {
			MixinDiagnostics.warnOnce("craftables",
				"RS craftables replacement failed", exception);
		}
	}

	private void breadmod$buildCraftables(CallbackInfoReturnable<List<EmiIngredient>> cir) {
		AbstractContainerScreen<?> screen = EmiApi.getHandledScreen();
		if (!RefinedStorageSupport.isGridScreen(screen)) {
			return;
		}
		if (!RefinedStorageSupport.isCraftingGrid(screen)) {
			cir.setReturnValue(List.of());
			return;
		}
		EmiPlayerInventory self = (EmiPlayerInventory) (Object) this;
		Predicate<EmiRecipe> predicate = self.getPredicate();
		if (predicate == null) {
			cir.setReturnValue(List.of());
			return;
		}
		Set<Object> emitted = new HashSet<>();
		List<EmiFavorite.Craftable> craftables = new ArrayList<>();
		for (EmiRecipe recipe : RefinedStorageSupport.candidates(self)) {
			EmiRecipe fillable = RefinedStorageSupport.fillable(recipe);
			boolean hidden = recipe.hideCraftable();
			boolean craftable = RefinedStorageSupport.isCraftable(fillable);
			if (hidden || !craftable) {
				continue;
			}
			Object key = RefinedStorageSupport.outputKey(fillable.getOutputs().get(0));
			boolean duplicate = emitted.contains(key);
			boolean predicateAccepted = !duplicate && predicate.test(recipe);
			if (duplicate || !predicateAccepted) {
				continue;
			}
			emitted.add(key);
			craftables.add(new EmiFavorite.Craftable(fillable));
		}
		Map<EmiIngredient, Integer> order = new IdentityHashMap<>();
		Map<EmiIngredient, String> names = new IdentityHashMap<>();
		for (EmiFavorite.Craftable craftable : craftables) {
			order.put(craftable, EmiStackList.getIndex(craftable.getStack()));
			names.put(craftable, RefinedStorageSupport.sortKey(craftable));
		}
		craftables.sort((a, b) -> {
			int index = Integer.compare(order.get(a), order.get(b));
			if (index != 0) {
				return index;
			}
			int amount = Long.compare(a.getAmount(), b.getAmount());
			if (amount != 0) {
				return amount;
			}
			return names.get(a).compareTo(names.get(b));
		});
		RefinedStorageSupport.debugDump(craftables);
		cir.setReturnValue(new ArrayList<>(craftables));
	}
}
