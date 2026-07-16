package com.specialities.skills;

import com.specialities.ModAttachments;
import com.specialities.SkillUpdatePayload;
import com.specialities.config.ConfigManager;

import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
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
		return ((AttachmentTarget) player).getAttachedOrElse(ModAttachments.SKILLS, PlayerSkills.EMPTY);
	}

	public static void addXp(final ServerPlayer player, final Skill skill, final int amount) {
		if (amount <= 0) {
			return;
		}

		// Global XP-rate knob (config): 1.0 = normal, 0.0 = disabled.
		int scaled = (int) Math.round(amount * ConfigManager.get().xpRateMultiplier);
		if (scaled <= 0) {
			return;
		}

		PlayerSkills old = get(player);
		int cap = Tuning.totalXpForLevel(Tuning.MAX_LEVEL);
		int newTotal = Math.min(cap, old.totalXp(skill) + scaled);

		if (newTotal == old.totalXp(skill)) {
			return;
		}

		apply(player, skill, old, newTotal);
	}

	/** Used by knowledge books: jump ahead a number of levels (progress resets to the level start). */
	public static void addLevels(final ServerPlayer player, final Skill skill, final int levels) {
		PlayerSkills old = get(player);
		int newLevel = Math.min(Tuning.MAX_LEVEL, old.level(skill) + levels);
		int newTotal = Tuning.totalXpForLevel(newLevel);

		if (newTotal <= old.totalXp(skill)) {
			return;
		}

		apply(player, skill, old, newTotal);
	}

	private static void apply(final ServerPlayer player, final Skill skill, final PlayerSkills old, final int newTotal) {
		PlayerSkills updated = old.withTotalXp(skill, newTotal);
		((AttachmentTarget) player).setAttached(ModAttachments.SKILLS, updated);

		if (skill == Skill.DEFENCE) {
			DefencePassives.apply(player);
		}

		ServerPlayNetworking.send(player, new SkillUpdatePayload(
				skill.id(), old.totalXp(skill), newTotal, old.level(skill), updated.level(skill)));
	}
}
