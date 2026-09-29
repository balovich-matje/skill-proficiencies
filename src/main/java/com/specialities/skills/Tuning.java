package com.specialities.skills;

import com.specialities.config.ConfigManager;

/**
 * All balance knobs in one place. A handful of them (combat damage, attack
 * speed, mining speed, luck breakpoint, XP rate) are player-editable and read
 * from {@link ConfigManager}; the rest are compile-time constants.
 *
 * <p><b>Extended levels (GitHub issue #6).</b> Every formula below is written
 * against {@link #MAX_LEVEL} as its REFERENCE point ("+50% at level 100") and
 * simply keeps going past it when the server raises the cap
 * ({@link #maxLevel()}): at 200 a linear bonus is exactly twice its level-100
 * value, a per-N-levels step keeps stepping. The only exceptions are values
 * that stop making physical sense past 100, and each one is clamped right
 * here, in the formula, with the level where it binds written next to it. At
 * or below level 100 every clamp is inert, so the standard game is unchanged.
 */
public final class Tuning {
	/**
	 * The standard level cap, and the reference level every formula below is
	 * phrased against. Stays 100 whether or not extended levels are on — the
	 * effective cap is {@link #maxLevel()}. Kept as a constant because it is
	 * public API: Dire Difficulty reads this field reflectively.
	 */
	public static final int MAX_LEVEL = 100;
	/** Combat XP per point of damage dealt. */
	public static final float COMBAT_XP_PER_DAMAGE = 2.0F;
	/** Every N arms mastery levels grant +1 passive sweeping edge. */
	public static final int SWEEP_BREAKPOINT = 25;
	/** How far a ricochet arrow searches for its next hostile target, in blocks. */
	public static final double RICOCHET_RANGE = 8.0;
	/**
	 * Protection points at which fall damage reaches 100% reduction. Vanilla's own
	 * pool formula is points/25 with a 20-point clamp; acrobatics uncaps that band.
	 */
	public static final float FALL_IMMUNITY_POINTS = 25.0F;

	/**
	 * Floor on every time/cost multiplier that shrinks with level (attack
	 * recovery, bow/crossbow draw, sprint hunger). 0.1 is the same 90% ceiling
	 * {@code attackSpeedMaxReduction} is already sanitised to at level 100, so it
	 * is inert up to 100 for every legal config; past it, a multiplier reaching 0
	 * divides by zero in {@code getAttackStrengthScale} and in the bow's draw
	 * maths, and a negative one would turn sprint hunger into food.
	 */
	public static final float MIN_TIME_MULTIPLIER = 0.1F;

	private Tuning() {
	}

	/**
	 * The effective level cap: {@link #MAX_LEVEL} unless the server turned on
	 * extended levels. Synced to clients, so this reads the SERVER's value on
	 * both sides — see {@link LevelCap}.
	 */
	public static int maxLevel() {
		return LevelCap.get();
	}

	/**
	 * Multiplier for attack recovery time (arms mastery) and bow/crossbow draw
	 * time (archery): 1.0 at level 0, down to (1 - attackSpeedMaxReduction) at
	 * level 100 (default 0.7, i.e. ~1.4x faster; 1.2.0 used 0.5 / 2x faster).
	 *
	 * <p>CLAMP: never below {@link #MIN_TIME_MULTIPLIER}. With the default
	 * reduction of 0.3 that binds at level 300 (at the config maximum 0.9, at 100).
	 */
	public static float recoveryTimeMultiplier(final int level) {
		return (float) Math.max(MIN_TIME_MULTIPLIER,
				1.0 - ConfigManager.get().attackSpeedMaxReduction * level / MAX_LEVEL);
	}

	/** Passive sweeping edge levels from arms mastery. */
	public static int sweepingBonus(final int level) {
		return level / SWEEP_BREAKPOINT;
	}

	/** Levels per milestone step. */
	public static final int MILESTONE_STEP = 50;

	/**
	 * Milestone bonus: +1 per 50 levels — +1 at 50, +2 at 100, and on past it
	 * (+4 at 200) when extended levels are on. Identical to the old two-step
	 * table for every level 0..100.
	 */
	public static int milestoneTier(final int level) {
		return level / MILESTONE_STEP;
	}

	/** Number of arrow ricochets: +1 per 50 levels. */
	public static int ricochets(final int level) {
		return milestoneTier(level);
	}

	// --- Fishing ---
	public static final int FISHING_XP_PER_CATCH = 15;
	/** Seconds of bite-wait each lure level removes (vanilla's own lure enchantment value). */
	public static final float LURE_SECONDS_PER_LEVEL = 5.0F;
	/**
	 * Highest TOTAL lure (enchantment + skill) that still catches fish. Vanilla
	 * rolls the wait as 100..600 ticks minus 100 ticks per lure level and simply
	 * re-rolls a result at or below zero, so at six levels every roll fails and
	 * nothing ever bites. Five is also exactly what the skill's own level-100
	 * bonus reaches on a Lure III rod.
	 */
	public static final int MAX_TOTAL_LURE = 5;

	/** Extra lure levels (5s wait reduction each): +1 per 50 levels. */
	public static int lureBonus(final int level) {
		return milestoneTier(level);
	}

	/**
	 * Enchantment lure plus the skill bonus, in lure LEVELS (the pre-1.21 hook).
	 *
	 * <p>CLAMP: the total never exceeds {@link #MAX_TOTAL_LURE} — unless the rod
	 * alone already does, which is left exactly as vanilla has it. Binds at level
	 * 150 on a Lure III rod, 300 on an unenchanted one.
	 */
	public static int lureLevelsWithBonus(final int enchantLure, final int level) {
		return Math.min(enchantLure + lureBonus(level), Math.max(enchantLure, MAX_TOTAL_LURE));
	}

	/** {@link #lureLevelsWithBonus} in the SECONDS unit the 1.21+ hook carries. Same clamp. */
	public static float lureSecondsWithBonus(final float enchantSeconds, final int level) {
		return Math.min(enchantSeconds + LURE_SECONDS_PER_LEVEL * lureBonus(level),
				Math.max(enchantSeconds, LURE_SECONDS_PER_LEVEL * MAX_TOTAL_LURE));
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

	/**
	 * Swiftness tier while sprinting: I at 50, II at 100, one more per 50 levels
	 * past it. The total sprint bonus stays under {@link #SPRINT_SPEED_CAP}
	 * (the existing all-sources cap), which tier IV reaches at level 200.
	 */
	public static int swiftnessTier(final int level) {
		return milestoneTier(level);
	}

	// --- Sneaking ---
	/** Detection range reduction from skill alone at level 100 (invisibility stacks on top). */
	public static final float SNEAK_MAX_DETECTION_REDUCTION = 0.9F;
	/**
	 * Floor on the sneaking visibility multiplier, i.e. at most a 95% detection
	 * range reduction. The linear formula reaches 100% at level 112 (no heavy
	 * armor) and goes negative after it. Inert up to level 100, where the
	 * lowest possible value is 0.1.
	 */
	public static final float MIN_SNEAK_VISIBILITY = 0.05F;
	/** Each worn heavy armor piece removes this fraction of the sneak bonus. */
	public static final float HEAVY_ARMOR_PENALTY = 0.25F;
	/** Radius scanned for nearby unaware hostiles, in blocks. */
	public static final double SNEAK_XP_RANGE = 16.0;
	public static final int SNEAK_XP_BASE_PER_SECOND = 3;
	public static final float SNEAK_XP_MAX_MULTIPLIER = 10.0F;

	/**
	 * Multiplier applied to the player's visibility while sneaking (lower = harder to detect).
	 *
	 * <p>CLAMP: never below {@link #MIN_SNEAK_VISIBILITY}. Binds at level 106 with no
	 * heavy armor (one piece: 141, two: 212, three: 423; four pieces never).
	 */
	public static float sneakVisibilityMultiplier(final int level, final int heavyArmorPieces) {
		float armorFactor = Math.max(0.0F, 1.0F - HEAVY_ARMOR_PENALTY * heavyArmorPieces);
		return Math.max(MIN_SNEAK_VISIBILITY,
				1.0F - SNEAK_MAX_DETECTION_REDUCTION * (level / (float) MAX_LEVEL) * armorFactor);
	}

	/**
	 * Stealth crit multiplier for a melee swing: x2.0 base, +0.25 at
	 * 25/50/75/100 -> x3.0 at max.
	 *
	 * <p>Keep this and {@link #stealthCritRangedMultiplier} the same shape —
	 * base plus a quarter-mark step — so a change to one reads as an obvious
	 * omission in the other.
	 */
	public static float stealthCritMeleeMultiplier(final int level) {
		return 2.0F + 0.25F * (level / 25);
	}

	/**
	 * Stealth crit multiplier for an arrow from a bow or crossbow: x1.5 base,
	 * +0.125 at 25/50/75/100 -> x2.0 at max. Weaker than the melee tier because
	 * a shot from stealth risks nothing — no approach, no counterattack — and
	 * archery already stacks its own bonuses on top.
	 */
	public static float stealthCritRangedMultiplier(final int level) {
		return 1.5F + 0.125F * (level / 25);
	}

	// --- Smithing ---
	/**
	 * Resourcefulness: chance that the n-th (1-based) bonus material is returned
	 * from a craft. At level 100: 100% for one item, 50% for a second, 25% for a
	 * third, and so on. Past 100 the whole ladder keeps sliding up: at 200 the
	 * second item is certain too, at 400 the third.
	 *
	 * <p>CLAMP: each chance is a probability, capped at 100% — the n-th item's
	 * chance binds at level 100 * 2^(n-1) (first item from 100, already true
	 * today). How many can come back is capped separately, by
	 * {@link #maxSmithingReturns}.
	 */
	public static float smithingReturnChance(final int level, final int nthItem) {
		return Math.min(1.0F, (level / (float) MAX_LEVEL) / (1 << (nthItem - 1)));
	}

	/**
	 * CLAMP (extended levels only): past level 100 a craft never returns MORE
	 * materials than it consumed — beyond that, crafting is a duplication loop
	 * that grows with every level. Gated to {@code level > MAX_LEVEL} so the
	 * standard 0..100 game is unchanged (there, a craft can already return one
	 * more than it consumed — 12.5% of 2-ingredient crafts at level 100, 1.6% of
	 * 3-ingredient ones — and that pre-existing behaviour is deliberately left
	 * alone). Where it binds past 100, per craft: 2 ingredients (shears) 50% at
	 * 200, always from 400; 3 (sword, shovel) 12.5% at 200, 50% at 400; 5
	 * (pickaxe, axe, helmet) ~0.1% at 200, 19.5% at 999. A full refund stays possible.
	 */
	public static int maxSmithingReturns(final int ingredientsConsumed, final int level) {
		return level > MAX_LEVEL ? ingredientsConsumed : Integer.MAX_VALUE;
	}

	/**
	 * Smelting multicraft: chances (at the given level) for x8 / x4 / x2 output.
	 * They are rolled as stacked bands (x8 first, then x4, then x2), so once
	 * their sum passes 100% — level 250 — the lower tiers are crowded out by the
	 * higher ones and every smelt is at least doubled; see {@link #smeltShare}.
	 *
	 * <p>CLAMP: each is a probability, capped at 100%: x2 binds at level 400
	 * (x4 would at 1000 and x8 at 2000, both beyond the 999 hard cap).
	 */
	public static float smeltChanceX8(final int level) {
		return Math.min(1.0F, 0.05F * level / MAX_LEVEL);
	}

	public static float smeltChanceX4(final int level) {
		return Math.min(1.0F, 0.10F * level / MAX_LEVEL);
	}

	public static float smeltChanceX2(final int level) {
		return Math.min(1.0F, 0.25F * level / MAX_LEVEL);
	}

	/**
	 * The ACTUAL chance of an x{@code multiplier} smelt (8, 4 or 2) once the
	 * stacked bands of {@code Artisan.rollSmeltMultiplier} are applied: equal to
	 * the raw chance until the bands overflow 100% at level 250, then whatever
	 * is left for the tier. What the skills screen shows.
	 */
	public static float smeltShare(final int level, final int multiplier) {
		float x8 = Math.min(1.0F, smeltChanceX8(level));
		float x4 = Math.min(1.0F, x8 + smeltChanceX4(level));
		float x2 = Math.min(1.0F, x4 + smeltChanceX2(level));

		return switch (multiplier) {
			case 8 -> x8;
			case 4 -> x4 - x8;
			case 2 -> x2 - x4;
			default -> 0.0F;
		};
	}

	// --- Alchemy ---
	/** XP per brewing cycle (one ingredient, up to three bottles). */
	public static final int ALCHEMY_BREW_XP = 100;

	/**
	 * Chance the brewing ingredient is not consumed: up to 50% at level 100.
	 *
	 * <p>CLAMP: a probability, capped at 100%; binds at level 200 (from there no
	 * ingredient is ever consumed).
	 */
	public static float alchemyReturnChance(final int level) {
		return Math.min(1.0F, 0.5F * level / MAX_LEVEL);
	}

	// --- Enchanting ---
	/** XP per enchant, multiplied by the lapis tier used (1-3). */
	public static final int ENCHANT_XP_PER_TIER = 120;

	/**
	 * Chance an enchant costs ~50% less XP levels: 100% at level 100.
	 *
	 * <p>CLAMP: a probability, capped at 100% — it already reaches that at level
	 * 100, so this bonus does not grow past it (the discount SIZE is fixed).
	 */
	public static float enchantDiscountChance(final int level) {
		return Math.min(1.0F, level / (float) MAX_LEVEL);
	}

	/**
	 * Chance for a free enchantment upgrade/addition: 50% at level 100.
	 *
	 * <p>CLAMP: a probability, capped at 100%; binds at level 200.
	 */
	public static float enchantLuckChance(final int level) {
		return Math.min(1.0F, 0.5F * level / MAX_LEVEL);
	}

	/** XP needed to go from {@code level} to {@code level + 1}; 0 at the effective cap. */
	public static int xpToNext(final int level) {
		return level >= maxLevel() ? 0 : 50 + 15 * level;
	}

	/**
	 * Total accumulated XP required to reach {@code level}, clamped to the
	 * effective cap. The curve itself is the same one on either side of 100.
	 */
	public static int totalXpForLevel(final int level) {
		int clamped = Math.max(0, Math.min(level, maxLevel()));
		// sum of (50 + 15*i) for i in [0, clamped)
		return 50 * clamped + 15 * clamped * (clamped - 1) / 2;
	}

	/**
	 * The level a stored XP total amounts to, never above the effective cap. A
	 * total ABOVE the cap's (left there by an extended-levels world whose toggle
	 * was later turned off) simply reads as the cap; the XP itself is kept.
	 *
	 * <p>Binary search over the monotonic curve rather than a walk: with the cap at
	 * 999 this runs from per-tick mixins, and it is also public API (Dire
	 * Difficulty calls it reflectively), so the signature must not change.
	 */
	public static int levelForTotalXp(final int totalXp) {
		int low = 0;
		int high = maxLevel();

		// Invariant: totalXpForLevel(low) <= totalXp (true for low = 0 and any totalXp >= 0).
		while (low < high) {
			int mid = (low + high + 1) >>> 1;

			if (totalXp >= totalXpForLevel(mid)) {
				low = mid;
			} else {
				high = mid - 1;
			}
		}

		return low;
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
