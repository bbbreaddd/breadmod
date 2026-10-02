package dev.bbbreaddd.breadmod;

import net.minecraftforge.common.ForgeConfigSpec;

public final class BreadmodConfig {
	public static final ForgeConfigSpec SPEC;

	public static final ForgeConfigSpec.BooleanValue RS_CRAFTABLES_REPLACEMENT;
	public static final ForgeConfigSpec.BooleanValue AUTO_RESOLVE_AMBIGUOUS;
	public static final ForgeConfigSpec.BooleanValue CANONICAL_RECIPE_PREFERENCE;
	public static final ForgeConfigSpec.BooleanValue NBT_LOOKUP_FALLBACK;
	public static final ForgeConfigSpec.BooleanValue REUSABLE_INGREDIENT_FIX;
	public static final ForgeConfigSpec.IntValue SNAPSHOT_REFRESH_MS;
	public static final ForgeConfigSpec.BooleanValue DEBUG_DIAGNOSTICS;

	static {
		ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
		builder.push("compatibility");
		RS_CRAFTABLES_REPLACEMENT = builder
			.comment("Replace EMI's craftables list inside Refined Storage Grids with a snapshot-backed list.",
				"Disable if the sidebar misbehaves after an EMI or Refined Storage update.")
			.define("rsCraftablesReplacement", true);
		AUTO_RESOLVE_AMBIGUOUS = builder
			.comment("Automatically resolve ambiguous recipe ingredients to a sensible default.",
				"Explicit EMI choices and data-pack defaults always win; this only fills the gap.")
			.define("autoResolveAmbiguous", true);
		CANONICAL_RECIPE_PREFERENCE = builder
			.comment("Prefer canonical recipes (crafting grid first, primary output id, own-mod machine)",
				"when EMI must pick one recipe automatically.")
			.define("canonicalRecipePreference", true);
		NBT_LOOKUP_FALLBACK = builder
			.comment("Retry empty R/U recipe lookups with the item's plain (no-NBT) form.",
				"Fixes lookups for items whose mod registers an NBT-sensitive comparison (e.g. backpacks).")
			.define("nbtLookupFallback", true);
		REUSABLE_INGREDIENT_FIX = builder
			.comment("Apply reusable-ingredient comparison fixes (damage-insensitive tools,",
				"Mystical Agriculture infusion crystals, Mekanism energy tablets).")
			.define("reusableIngredientFix", true);
		SNAPSHOT_REFRESH_MS = builder
			.comment("How often the Refined Storage inventory snapshot refreshes, in milliseconds.",
				"Higher values reduce CPU on large networks; the snapshot also refreshes on screen change.")
			.defineInRange("snapshotRefreshMs", 250, 50, 5000);
		DEBUG_DIAGNOSTICS = builder
			.comment("Write the craftables list to breadmod-craftables.txt for debugging.",
				"Equivalent to -Dbreadmod.debug=true.")
			.define("debugDiagnostics", false);
		builder.pop();
		SPEC = builder.build();
	}

	private BreadmodConfig() {
	}

	private static boolean get(ForgeConfigSpec.BooleanValue value, boolean fallback) {
		try {
			return value == null ? fallback : value.get();
		} catch (RuntimeException ignored) {
			return fallback;
		}
	}

	public static boolean rsCraftablesReplacement() {
		return get(RS_CRAFTABLES_REPLACEMENT, true);
	}

	public static boolean autoResolveAmbiguous() {
		return get(AUTO_RESOLVE_AMBIGUOUS, true);
	}

	public static boolean canonicalRecipePreference() {
		return get(CANONICAL_RECIPE_PREFERENCE, true);
	}

	public static boolean nbtLookupFallback() {
		return get(NBT_LOOKUP_FALLBACK, true);
	}

	public static boolean reusableIngredientFix() {
		return get(REUSABLE_INGREDIENT_FIX, true);
	}

	public static int snapshotRefreshMs() {
		try {
			return SNAPSHOT_REFRESH_MS == null ? 250 : SNAPSHOT_REFRESH_MS.get();
		} catch (RuntimeException ignored) {
			return 250;
		}
	}

	public static boolean debugDiagnostics() {
		return Boolean.getBoolean("breadmod.debug") || get(DEBUG_DIAGNOSTICS, false);
	}
}
