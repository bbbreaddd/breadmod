package dev.bbbreaddd.breadmod;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import dev.bbbreaddd.breadmod.GridSnapshot.StoredStack;
import dev.emi.emi.api.recipe.EmiPlayerInventory;
import dev.emi.emi.api.recipe.EmiRecipe;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.registries.ForgeRegistries;

final class CraftabilityChecker {
	private CraftabilityChecker() {
	}

	record CraftabilityResult(boolean craftable, List<Ingredient> unmatched, int[] assignment) {
	}

	static boolean canCraftBacking(EmiRecipe recipe, EmiPlayerInventory inventory, GridSnapshot snapshot) {
		Recipe<?> backing = recipe.getBackingRecipe();
		if (backing == null) {
			return inventory.canCraft(recipe);
		}
		try {
			return solve(backing, snapshot).craftable();
		} catch (RuntimeException | LinkageError exception) {
			MixinDiagnostics.warnOnce("craftability-matcher",
				"craftability matcher failed; falling back to EMI inventory", exception);
			return inventory.canCraft(recipe);
		}
	}

	static boolean satisfiesNbtJointly(EmiRecipe recipe, GridSnapshot snapshot) {
		Recipe<?> backing = recipe.getBackingRecipe();
		if (backing == null) {
			return true;
		}
		try {
			List<Ingredient> sensitive = new ArrayList<>();
			for (Ingredient ingredient : backing.getIngredients()) {
				if (!ingredient.isEmpty() && namesStoredNbt(ingredient, snapshot)) {
					sensitive.add(ingredient);
				}
			}
			if (sensitive.isEmpty()) {
				return true;
			}
			return solveIngredients(sensitive, snapshot).craftable();
		} catch (RuntimeException | LinkageError exception) {
			MixinDiagnostics.warnOnce("nbt-matcher",
				"NBT matcher failed; assuming NBT satisfiable", exception);
			return true;
		}
	}

	static List<String> missingIngredients(EmiRecipe recipe, GridSnapshot snapshot) {
		List<String> missing = new ArrayList<>();
		Recipe<?> backing = recipe == null ? null : recipe.getBackingRecipe();
		if (backing == null) {
			return missing;
		}
		try {
			CraftabilityResult result = solve(backing, snapshot);
			for (Ingredient ingredient : result.unmatched()) {
				missing.add(describeIngredient(ingredient));
			}
		} catch (RuntimeException | LinkageError exception) {
			MixinDiagnostics.warnOnce("missing-ingredients",
				"missing-ingredient report failed", exception);
		}
		return missing;
	}

	private static CraftabilityResult solve(Recipe<?> backing, GridSnapshot snapshot) {
		List<Ingredient> needed = new ArrayList<>();
		for (Ingredient ingredient : backing.getIngredients()) {
			if (!ingredient.isEmpty()) {
				needed.add(ingredient);
			}
		}
		return solveIngredients(needed, snapshot);
	}

	private static CraftabilityResult solveIngredients(List<Ingredient> needed, GridSnapshot snapshot) {
		int n = needed.size();
		int m = snapshot.storedStacks.size();
		List<int[]> matches = new ArrayList<>(n);
		for (Ingredient ingredient : needed) {
			matches.add(candidates(ingredient, snapshot));
		}
		if (n == 0) {
			return new CraftabilityResult(true, List.of(), new int[0]);
		}
		long[] capacity = new long[m];
		for (int j = 0; j < m; j++) {
			capacity[j] = Math.min(snapshot.storedStacks.get(j).amount(), n);
		}
		int[] assignment = maxFlowAssignment(matches, capacity, m);
		List<Ingredient> unmatched = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			if (assignment[i] < 0) {
				unmatched.add(needed.get(i));
			}
		}
		return new CraftabilityResult(unmatched.isEmpty(), unmatched, assignment);
	}

	private static int[] candidates(Ingredient ingredient, GridSnapshot snapshot) {
		ItemStack[] shapes;
		try {
			shapes = ingredient.getItems();
		} catch (RuntimeException | LinkageError exception) {
			MixinDiagnostics.warnOnce("ingredient-shapes",
				"ingredient shapes unreadable; scanning stored stacks", exception);
			return scanAll(ingredient, snapshot);
		}
		if (shapes == null || shapes.length == 0) {
			return scanAll(ingredient, snapshot);
		}
		Set<Integer> indexed = new HashSet<>();
		for (ItemStack shape : shapes) {
			if (shape != null && !shape.isEmpty()) {
				indexed.addAll(snapshot.storedStackIndices.getOrDefault(shape.getItem(), List.of()));
			}
		}
		int[] accepted = new int[indexed.size()];
		int size = 0;
		for (int i : indexed) {
			if (ingredient.test(snapshot.storedStacks.get(i).stack())) {
				accepted[size++] = i;
			}
		}
		return Arrays.copyOf(accepted, size);
	}

	private static int[] scanAll(Ingredient ingredient, GridSnapshot snapshot) {
		int[] accepted = new int[snapshot.storedStacks.size()];
		int size = 0;
		for (int i = 0; i < snapshot.storedStacks.size(); i++) {
			StoredStack stored = snapshot.storedStacks.get(i);
			try {
				if (ingredient.test(stored.stack())) {
					accepted[size++] = i;
				}
			} catch (RuntimeException | LinkageError exception) {
				MixinDiagnostics.warnOnce("ingredient-test",
					"custom ingredient test failed; skipping stack", exception);
			}
		}
		return Arrays.copyOf(accepted, size);
	}

	private static boolean namesStoredNbt(Ingredient ingredient, GridSnapshot snapshot) {
		try {
			for (ItemStack shape : ingredient.getItems()) {
				if (shape != null && !shape.isEmpty()
						&& snapshot.nbtVariants.containsKey(shape.getItem())) {
					return true;
				}
			}
		} catch (RuntimeException | LinkageError exception) {
			MixinDiagnostics.warnOnce("nbt-shapes",
				"NBT shape probe failed; assuming NBT-sensitive", exception);
			return true;
		}
		return false;
	}

	private static int[] maxFlowAssignment(List<int[]> matches, long[] capacity, int storedCount) {
		int n = matches.size();
		Integer[] order = new Integer[n];
		for (int i = 0; i < n; i++) {
			order[i] = i;
		}
		Arrays.sort(order, (a, b) -> Integer.compare(matches.get(a).length, matches.get(b).length));
		int nodes = 2 + n + storedCount;
		int source = 0;
		int sink = nodes - 1;
		Dinic flow = new Dinic(nodes);
		for (int rank = 0; rank < n; rank++) {
			int i = order[rank];
			flow.addEdge(source, 1 + i, 1);
			int[] stacks = matches.get(i).clone();
			Arrays.sort(stacks);
			for (int stack : stacks) {
				flow.addEdge(1 + i, 1 + n + stack, 1);
			}
		}
		for (int j = 0; j < storedCount; j++) {
			if (capacity[j] > 0) {
				flow.addEdge(1 + n + j, sink, capacity[j] > Integer.MAX_VALUE ? Integer.MAX_VALUE
					: (int) capacity[j]);
			}
		}
		flow.maxFlow(source, sink);
		int[] assignment = new int[n];
		Arrays.fill(assignment, -1);
		for (int i = 0; i < n; i++) {
			for (Dinic.Edge edge : flow.graph[1 + i]) {
				if (edge.to >= 1 + n && edge.to < sink && edge.flow > 0) {
					assignment[i] = edge.to - (1 + n);
					break;
				}
			}
		}
		return assignment;
	}

	private static String describeIngredient(Ingredient ingredient) {
		ItemStack[] shapes;
		try {
			shapes = ingredient.getItems();
		} catch (RuntimeException | LinkageError exception) {
			return "<unreadable>";
		}
		if (shapes.length == 0) {
			return "<empty>";
		}
		String first = String.valueOf(ForgeRegistries.ITEMS.getKey(shapes[0].getItem()));
		return shapes.length == 1 ? first : first + " (or " + (shapes.length - 1) + " more)";
	}

	private static final class Dinic {
		final List<Edge>[] graph;
		private int[] level;
		private int[] next;

		@SuppressWarnings("unchecked")
		Dinic(int nodes) {
			graph = new List[nodes];
			for (int i = 0; i < nodes; i++) {
				graph[i] = new ArrayList<>();
			}
		}

		void addEdge(int from, int to, long cap) {
			Edge forward = new Edge(to, cap);
			Edge backward = new Edge(from, 0);
			forward.rev = backward;
			backward.rev = forward;
			graph[from].add(forward);
			graph[to].add(backward);
		}

		long maxFlow(int source, int sink) {
			long total = 0;
			while (bfs(source, sink)) {
				next = new int[graph.length];
				long pushed;
				while ((pushed = dfs(source, sink, Long.MAX_VALUE)) > 0) {
					total += pushed;
				}
			}
			return total;
		}

		private boolean bfs(int source, int sink) {
			level = new int[graph.length];
			Arrays.fill(level, -1);
			ArrayDeque<Integer> queue = new ArrayDeque<>();
			level[source] = 0;
			queue.add(source);
			while (!queue.isEmpty()) {
				int node = queue.removeFirst();
				for (Edge edge : graph[node]) {
					if (edge.remaining() > 0 && level[edge.to] < 0) {
						level[edge.to] = level[node] + 1;
						queue.add(edge.to);
					}
				}
			}
			return level[sink] >= 0;
		}

		private long dfs(int node, int sink, long available) {
			if (node == sink) {
				return available;
			}
			for (int i = next[node]; i < graph[node].size(); i++) {
				next[node] = i;
				Edge edge = graph[node].get(i);
				if (edge.remaining() > 0 && level[edge.to] == level[node] + 1) {
					long pushed = dfs(edge.to, sink, Math.min(available, edge.remaining()));
					if (pushed > 0) {
						edge.flow += pushed;
						edge.rev.flow -= pushed;
						return pushed;
					}
				}
			}
			return 0;
		}

		static final class Edge {
			final int to;
			final long cap;
			long flow;
			Edge rev;

			Edge(int to, long cap) {
				this.to = to;
				this.cap = cap;
			}

			long remaining() {
				return cap - flow;
			}
		}
	}
}
