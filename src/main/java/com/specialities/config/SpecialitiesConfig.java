package com.specialities.config;

/**
 * Player-editable knobs, persisted to {@code config/skill-proficiencies.json} and
 * surfaced through the Mod Menu / Cloth Config screen. Every BALANCE field here
 * is read by {@link com.specialities.skills.Tuning} (or {@code SkillManager}) at
 * runtime, so the skills screen's displayed numbers follow whatever the player
 * sets — the config is the single source of truth for these knobs.
 *
 * <p>Two kinds of field live in this one file, and the difference matters:
 * the balance knobs are read wherever the skill logic runs (the server, or the
 * integrated server in singleplayer), while {@link #showXpHudBar} is read only
 * on a physical client, out of that client's own copy of the file. Nothing here
 * is ever synced — see that field's note.
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

	/**
	 * Draw the skill XP bar above the hotbar. {@code false} hides the bar and, with it, the
	 * {@code HUD_SHIFT} raise of the vanilla bottom HUD; toasts, the skills screen and the
	 * stealth vignette are unaffected.
	 *
	 * <p><b>This one knob is CLIENT-LOCAL, unlike every field above it.</b> It is read only by
	 * {@code client/SkillXpHudBar} and {@code client/SpecialitiesClient}, i.e. only ever on a
	 * physical client, out of that client's own {@code config/skill-proficiencies.json}. Nothing in the
	 * mod puts config values on the wire (the three payloads carry skill state and nothing else),
	 * so a server setting this to {@code false} cannot hide a connected player's bar, and a player
	 * setting it to {@code false} changes nothing on the server. On a dedicated server the field
	 * is written to the file and then ignored.
	 */
	public boolean showXpHudBar = true;

	/**
	 * How many pixels the vanilla bottom HUD is raised while the skill XP bar is shown. 7 is the
	 * bar's own height plus its gap and is what every release up to 1.6.1 hardcoded.
	 *
	 * <p><b>0 means the mod never translates the vanilla HUD at all</b> — not "translate by zero":
	 * every raise path skips its push/translate/pop entirely, so nothing this mod does can
	 * interfere with another mod's matrix. That is the point of the knob. GitHub issue #4: mods
	 * that move the HUD themselves (Raised, Nostalgic Tweaks, Melancholic Hunger) and this mod
	 * both shift the same rows, and the shifts add up. At 0 the skill bar still draws, in the slot
	 * the vanilla XP bar occupies — the two OVERLAP by design, which is the trade being opted
	 * into; {@link #hudBarYOffset} is how you move ours out of the way instead.
	 *
	 * <p>Client-local like {@link #showXpHudBar}, and for the same reason — see that field's note.
	 */
	public int hudShiftAmount = 7;

	/**
	 * Extra pixels to move the skill XP bar UP from the vanilla XP bar's slot. 0 keeps the
	 * position every release up to 1.6.1 had; negative moves it down.
	 *
	 * <p>Also GitHub issue #4, and it is the half {@link #hudShiftAmount} cannot fix. The bar is
	 * anchored to the bottom of the screen (the vanilla XP bar's own y), so a mod that RAISES the
	 * hotbar leaves our bar behind, drawn across the hotbar's item slots — which is what the
	 * issue's screenshot shows. Nothing tells us where another mod put the hotbar, so this is the
	 * manual answer: nudge the bar until it sits where you want it.
	 *
	 * <p>Client-local like {@link #showXpHudBar}, and for the same reason — see that field's note.
	 */
	public int hudBarYOffset = 0;

	/** Clamp every field into a sane range so a hand-edited file can't break the math (e.g. divide-by-zero). */
	public void sanitize() {
		combatDamageMaxBonus = clamp(combatDamageMaxBonus, 0.0, 5.0);
		attackSpeedMaxReduction = clamp(attackSpeedMaxReduction, 0.0, 0.9);
		miningSpeedMaxBonus = clamp(miningSpeedMaxBonus, 0.0, 10.0);
		xpRateMultiplier = clamp(xpRateMultiplier, 0.0, 100.0);
		luckLevelsPerBonus = (int) clamp(luckLevelsPerBonus, 1, 100);
		hudShiftAmount = (int) clamp(hudShiftAmount, 0, 32);
		hudBarYOffset = (int) clamp(hudBarYOffset, -64, 64);
	}

	private static double clamp(final double value, final double lo, final double hi) {
		return Math.max(lo, Math.min(hi, value));
	}
}
