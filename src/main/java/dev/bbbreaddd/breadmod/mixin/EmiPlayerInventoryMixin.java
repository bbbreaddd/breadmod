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
import dev.bbbreaddd.breadmod.RefinedStorageSupport;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

@Mixin(value = EmiPlayerInventory.class, remap = false)
public abstract class EmiPlayerInventoryMixin {
	@Inject(method = "getCraftables", at = @At("HEAD"), cancellable = true)
	private void breadmod$deduplicateOutputs(CallbackInfoReturnable<List<EmiIngredient>> cir) {
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
		// One pass: the first recipe that both passes the predicate and produces an output we have
		// not emitted yet wins, so an output is only tested until something can craft it.
		Set<Object> emitted = new HashSet<>();
		List<EmiFavorite.Craftable> craftables = new ArrayList<>();
		for (EmiRecipe recipe : RefinedStorageSupport.candidates(self)) {
			// A display-only recipe is listed under what the Grid would actually craft from it,
			// which is the rebuilt form; the recipe as registered may not name an output at all.
			EmiRecipe fillable = RefinedStorageSupport.fillable(recipe);
			if (recipe.hideCraftable() || !RefinedStorageSupport.isCraftable(fillable)) {
				continue;
			}
			Object key = RefinedStorageSupport.outputKey(fillable.getOutputs().get(0));
			if (emitted.contains(key) || !predicate.test(recipe)) {
				continue;
			}
			emitted.add(key);
			craftables.add(new EmiFavorite.Craftable(fillable));
		}
		// Look each sort index up once rather than on every comparison; the lookup hashes an
		// EmiStack, which is not cheap enough to repeat O(n log n) times.
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
			// Items EMI's index does not rank all share one index, so break the tie on something
			// stable; otherwise their relative order follows hash iteration and reshuffles.
			return names.get(a).compareTo(names.get(b));
		});
		RefinedStorageSupport.debugDump(craftables);
		cir.setReturnValue(new ArrayList<>(craftables));
	}
}
