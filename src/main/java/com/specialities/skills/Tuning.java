package com.specialities.skills;

import com.specialities.config.ConfigManager;

/**
 * All balance knobs in one place. A handful of them (combat damage, attack
 * speed, mining speed, luck breakpoint, XP rate) are player-editable and read
 * from {@link ConfigManager}; the rest are compile-time constants.
 */
public final class Tuning {
	public static final int MAX_LEVEL = 100;
	/** Combat XP per point of damage dealt. */
	public static final float COMBAT_XP_PER_DAMAGE = 2.0F;
	/** Every N arms mastery levels grant +1 passive sweeping edge. */
	public static final int SWEEP_BREAKPOINT = 25;
	/** How far a ricochet arrow searches for its next hostile target, in blocks. */
	public static final double RICOCHET_RANGE = 8.0;

	private Tuning() {
	}

	/**
	 * Multiplier for attack recovery time (arms mastery) and bow/crossbow draw
	 * time (archery): 1.0 at level 0, down to (1 - attackSpeedMaxReduction) at
	 * level 100 (default 0.7, i.e. ~1.4x faster; 1.2.0 used 0.5 / 2x faster).
	 */
	public static float recoveryTimeMultiplier(final int level) {
		return (float) (1.0 - ConfigManager.get().attackSpeedMaxReduction * level / MAX_LEVEL);
	}

	/** Passive sweeping edge levels from arms mastery. */
	public static int sweepingBonus(final int level) {
		return level / SWEEP_BREAKPOINT;
	}

	/** Two-step milestone bonus: +1 at level 50, +2 at level 100. */
	public static int milestoneTier(final int level) {
		if (level >= MAX_LEVEL) {
			return 2;
		}

		return level >= 50 ? 1 : 0;
	}

	/** Number of arrow ricochets: +1 at level 50, +1 at level 100. */
	public static int ricochets(final int level) {
		return milestoneTier(level);
	}

	// --- Fishing ---
	public static final int FISHING_XP_PER_CATCH = 15;
	/** Extra lure levels (5s wait reduction each): +1 at 50, +2 at 100. */
	public static int lureBonus(final int level) {
		return milestoneTier(level);
	}

	// --- Defence ---
	public static final float DEFENCE_XP_PER_DAMAGE = 2.0F;
	public static final float DEFENCE_ENVIRONMENT_RATE = 0.5F;
	public static final float DEFENCE_MOB_RATE = 1.0F;
	public static final float DEFENCE_CREEPER_RATE = 2.0F;

	/** +1 armor toughness per 25 levels. */
	public static int toughnessBonus(final int level) {
		return level / 25;
	}

	/** +1 heart (2 max health) per 10 levels. */
	public static int maxHealthBonus(final int level) {
		return 2 * (level / 10);
	}

	// --- Acrobatics ---
	public static final float ACROBATICS_XP_PER_DAMAGE = 3.0F;

	/**
	 * Fall-damage protection points fed into the vanilla enchantment protection
	 * pool (each point = 4% reduction). 13 points at level 100: together with
	 * Feather Falling IV (12 points) that reaches the 25-point mark = immunity
	 * (the vanilla 20-point cap is lifted for fall damage only).
	 */
	public static float acrobaticsProtectionPoints(final int level) {
		return 0.13F * level;
	}

	// --- Athletics ---
	/** Sprint speed bonus per swiftness tier, and the total cap across all sources. */
	public static final float SWIFTNESS_PER_TIER = 0.2F;
	public static final float SPRINT_SPEED_CAP = 0.8F;
	public static final int SPRINT_XP_INTERVAL_TICKS = 60;
	public static final int SPRINT_XP_PER_INTERVAL = 5;
	/**
	 * Jump XP mirrors vanilla's exhaustion split — a sprint jump costs 0.2
	 * exhaustion against a standing jump's 0.05 — so the hungriest way to move
	 * is also the fastest way to train.
	 */
	public static final int JUMP_XP = 1;
	public static final int SPRINT_JUMP_XP = 4;

	/** Swiftness tier while sprinting: I at 50, II at 100. */
	public static int swiftnessTier(final int level) {
		return milestoneTier(level);
	}

	// --- Sneaking ---
	/** Detection range reduction from skill alone at level 100 (invisibility stacks on top). */
	public static final float SNEAK_MAX_DETECTION_REDUCTION = 0.9F;
	/** Each worn heavy armor piece removes this fraction of the sneak bonus. */
	public static final float HEAVY_ARMOR_PENALTY = 0.25F;
	/** Radius scanned for nearby unaware hostiles, in blocks. */
	public static final double SNEAK_XP_RANGE = 16.0;
	public static final int SNEAK_XP_BASE_PER_SECOND = 3;
	public static final float SNEAK_XP_MAX_MULTIPLIER = 10.0F;

	/** Multiplier applied to the player's visibility while sneaking (lower = harder to detect). */
	public static float sneakVisibilityMultiplier(final int level, final int heavyArmorPieces) {
		float armorFactor = Math.max(0.0F, 1.0F - HEAVY_ARMOR_PENALTY * heavyArmorPieces);
		return 1.0F - SNEAK_MAX_DETECTION_REDUCTION * (level / (float) MAX_LEVEL) * armorFactor;
	}

	/** Stealth crit multiplier: x2.0 base, +0.25 at 25/50/75/100 -> x3.0 at max. */
	public static float stealthCritMultiplier(final int level) {
		return 2.0F + 0.25F * (level / 25);
	}

	// --- Smithing ---
	/**
	 * Resourcefulness: chance that the n-th (1-based) bonus material is returned
	 * from a craft. At level 100: 100% for one item, 50% for a second, 25% for a
	 * third, and so on.
	 */
	public static float smithingReturnChance(final int level, final int nthItem) {
		return (level / (float) MAX_LEVEL) / (1 << (nthItem - 1));
	}

	/** Smelting multicraft: chances (at the given level) for x8 / x4 / x2 output. */
	public static float smeltChanceX8(final int level) {
		return 0.05F * level / MAX_LEVEL;
	}

	public static float smeltChanceX4(final int level) {
		return 0.10F * level / MAX_LEVEL;
	}

	public static float smeltChanceX2(final int level) {
		return 0.25F * level / MAX_LEVEL;
	}

	// --- Alchemy ---
	/** XP per brewing cycle (one ingredient, up to three bottles). */
	public static final int ALCHEMY_BREW_XP = 100;

	/** Chance the brewing ingredient is not consumed: up to 50% at level 100. */
	public static float alchemyReturnChance(final int level) {
		return 0.5F * level / MAX_LEVEL;
	}

	// --- Enchanting ---
	/** XP per enchant, multiplied by the lapis tier used (1-3). */
	public static final int ENCHANT_XP_PER_TIER = 120;

	/** Chance an enchant costs ~50% less XP levels: 100% at level 100. */
	public static float enchantDiscountChance(final int level) {
		return level / (float) MAX_LEVEL;
	}

	/** Chance for a free enchantment upgrade/addition: capped at 50% at level 100. */
	public static float enchantLuckChance(final int level) {
		return 0.5F * level / MAX_LEVEL;
	}

	/** XP needed to go from {@code level} to {@code level + 1}. */
	public static int xpToNext(final int level) {
		return level >= MAX_LEVEL ? 0 : 50 + 15 * level;
	}

	/** Total accumulated XP required to reach {@code level}. */
	public static int totalXpForLevel(final int level) {
		int clamped = Math.min(level, MAX_LEVEL);
		// sum of (50 + 15*i) for i in [0, clamped)
		return 50 * clamped + 15 * clamped * (clamped - 1) / 2;
	}

	public static int levelForTotalXp(final int totalXp) {
		int level = 0;
		while (level < MAX_LEVEL && totalXp >= totalXpForLevel(level + 1)) {
			level++;
		}
		return level;
	}

	/** Block breaking speed multiplier: +miningSpeedMaxBonus at level 100 (default +100%, x2.0). */
	public static float breakSpeedMultiplier(final int level) {
		return (float) (1.0 + ConfigManager.get().miningSpeedMaxBonus * level / MAX_LEVEL);
	}

	/** Skill levels required per +1 passive Fortune/Looting (player-configurable). */
	public static int luckBreakpoint() {
		return ConfigManager.get().luckLevelsPerBonus;
	}

	/** Passive fortune/looting levels granted by a skill level. */
	public static int luckBonus(final int level) {
		return level / luckBreakpoint();
	}

	/** Weapon damage multiplier: +0% at level 0, +combatDamageMaxBonus at level 100 (default +50%, x1.5; 1.2.0 was x2.0). */
	public static float damageMultiplier(final int level) {
		return (float) (1.0 + ConfigManager.get().combatDamageMaxBonus * level / MAX_LEVEL);
	}
}
