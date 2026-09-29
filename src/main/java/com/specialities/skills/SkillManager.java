package com.specialities.skills;

import com.specialities.api.SkillType;
import com.specialities.config.ConfigManager;
import com.specialities.platform.Net;
import com.specialities.platform.SkillStore;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Server-authoritative skill mutations. All XP/level changes go through here so
 * the attachment stays in sync and the owning client is notified.
 */
public final class SkillManager {
	private SkillManager() {
	}

	public static PlayerSkills get(final Player player) {
		return SkillStore.INSTANCE.getSkills(player);
	}

	public static void addXp(final ServerPlayer player, final SkillType skill, final int amount) {
		if (amount <= 0) {
			return;
		}

		// Global XP-rate knob (config): 1.0 = normal, 0.0 = disabled.
		int scaled = (int) Math.round(amount * ConfigManager.get().xpRateMultiplier);
		if (scaled <= 0) {
			return;
		}

		PlayerSkills old = get(player);
		int cap = Tuning.totalXpForLevel(Tuning.maxLevel());
		int oldTotal = old.totalXp(skill);

		// At or above the cap: nothing to gain, and — the data-safety rule of issue #6 — a
		// total ABOVE the cap (earned with extended levels on, toggle since turned off) is
		// kept exactly as it is rather than being "clamped" down, so turning extended levels
		// back on restores the level.
		if (oldTotal >= cap) {
			return;
		}

		int newTotal = Math.min(cap, oldTotal + scaled);

		apply(player, skill, old, newTotal);
	}

	/** Used by knowledge books: jump ahead a number of levels (progress resets to the level start). */
	public static void addLevels(final ServerPlayer player, final SkillType skill, final int levels) {
		PlayerSkills old = get(player);
		int newLevel = Math.min(Tuning.maxLevel(), old.level(skill) + levels);
		int newTotal = Tuning.totalXpForLevel(newLevel);

		if (newTotal <= old.totalXp(skill)) {
			return;
		}

		apply(player, skill, old, newTotal);
	}

	/**
	 * Put a skill at exactly {@code level}, up or down (progress resets to that
	 * level's start), clamped to 0..{@link Tuning#maxLevel()}. Used by the operator
	 * commands; unlike {@link #addLevels} this is not clamped to only ever move
	 * forward, so it can undo a test — which also means it is the ONE path that can
	 * lower a total stored above the current cap, and only because an operator asked.
	 *
	 * @return true if anything changed
	 */
	public static boolean setLevel(final ServerPlayer player, final SkillType skill, final int level) {
		PlayerSkills old = get(player);
		// `Math.clamp` is a Java 21 method and 1.20.1 is the Java 17 node (conventions
		// §5e). Same value, same clamp order, one `invokestatic` more.
		//? if >=1.20.5 {
		int newTotal = Tuning.totalXpForLevel(Math.clamp(level, 0, Tuning.maxLevel()));
		//?} else {
		/*int newTotal = Tuning.totalXpForLevel(Math.max(0, Math.min(Tuning.maxLevel(), level)));
		*///?}

		if (newTotal == old.totalXp(skill)) {
			return false;
		}

		apply(player, skill, old, newTotal);
		return true;
	}

	private static void apply(final ServerPlayer player, final SkillType skill, final PlayerSkills old, final int newTotal) {
		PlayerSkills updated = old.withTotalXp(skill, newTotal);
		SkillStore.INSTANCE.setSkills(player, updated);

		if (skill == Skill.DEFENCE) {
			DefencePassives.apply(player);
		}

		Net.INSTANCE.sendSkillUpdate(player,
				skill.id(), old.totalXp(skill), newTotal, old.level(skill), updated.level(skill));
	}
}
