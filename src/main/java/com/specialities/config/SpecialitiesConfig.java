package com.specialities.config;

/**
 * Player-editable balance knobs, persisted to {@code config/specialities.json}
 * and surfaced through the Mod Menu / Cloth Config screen. Every field here is
 * read by {@link com.specialities.skills.Tuning} (or {@code SkillManager}) at
 * runtime, so the skills screen's displayed numbers follow whatever the player
 * sets — the config is the single source of truth for these knobs.
 *
 * <p>Defaults are the 1.3.0 rebalance. To restore the 1.2.0 feel set
 * {@code combatDamageMaxBonus = 1.0} and {@code attackSpeedMaxReduction = 0.5}.
 *
 * <p>Plain public fields with a no-arg constructor: this is (de)serialized
 * directly by Gson, so a missing field in an older JSON just keeps its default.
 */
public final class SpecialitiesConfig {
	/**
	 * Extra weapon damage at combat level 100, as a fraction of base damage.
	 * 0.5 = +50% (x1.5) at max. 1.2.0 shipped 1.0 (+100%, x2.0).
	 */
	public double combatDamageMaxBonus = 0.5;

	/**
	 * Fraction of attack-recovery / bow-draw time removed at arms-mastery /
	 * archery level 100. 0.3 leaves 70% of the time (~1.4x faster). 1.2.0
	 * shipped 0.5 (half the time, 2x faster).
	 */
	public double attackSpeedMaxReduction = 0.3;

	/**
	 * Extra block-breaking speed at mining level 100, as a fraction.
	 * 1.0 = +100% (x2.0) at max — unchanged from 1.2.0.
	 */
	public double miningSpeedMaxBonus = 1.0;

	/** Global multiplier applied to every skill XP gain. 1.0 = normal, 2.0 = double, 0.0 = disabled. */
	public double xpRateMultiplier = 1.0;

	/** Skill levels required per +1 passive Fortune/Looting. Lower = luck comes faster. */
	public int luckLevelsPerBonus = 20;

	/** Clamp every field into a sane range so a hand-edited file can't break the math (e.g. divide-by-zero). */
	public void sanitize() {
		combatDamageMaxBonus = clamp(combatDamageMaxBonus, 0.0, 5.0);
		attackSpeedMaxReduction = clamp(attackSpeedMaxReduction, 0.0, 0.9);
		miningSpeedMaxBonus = clamp(miningSpeedMaxBonus, 0.0, 10.0);
		xpRateMultiplier = clamp(xpRateMultiplier, 0.0, 100.0);
		luckLevelsPerBonus = (int) clamp(luckLevelsPerBonus, 1, 100);
	}

	private static double clamp(final double value, final double lo, final double hi) {
		return Math.max(lo, Math.min(hi, value));
	}
}
