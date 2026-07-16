package com.specialities.client;

import com.specialities.SkillUpdatePayload;
import com.specialities.api.SkillType;
import com.specialities.skills.SkillTypes;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

/**
 * Client-side state for the skill XP HUD bar. The bar stays visible for the
 * rest of the session once any skill gains XP; each gain (re)starts the icon
 * convergence + bar grow animation.
 */
public final class SkillHudState {
	private static @Nullable SkillType skill;
	private static int fromTotalXp;
	private static int totalXp;
	private static int fromLevel;
	private static int level;
	private static long animStartMs;

	private SkillHudState() {
	}

	public static void onUpdate(final SkillUpdatePayload payload, final Minecraft client) {
		SkillType updated = SkillTypes.byId(payload.skillId());

		// An id nothing registered (mismatched mod sets) is dropped, not fatal.
		if (updated == null) {
			return;
		}

		skill = updated;
		fromTotalXp = payload.fromTotalXp();
		totalXp = payload.totalXp();
		fromLevel = payload.fromLevel();
		level = payload.level();
		animStartMs = Util.getMillis();

		if (payload.levelUp()) {
			client.gui.toastManager().addToast(new SkillLevelUpToast(updated, payload.fromLevel(), payload.level()));
		}
	}

	public static @Nullable SkillType skill() {
		return skill;
	}

	public static int fromTotalXp() {
		return fromTotalXp;
	}

	public static int totalXp() {
		return totalXp;
	}

	public static int fromLevel() {
		return fromLevel;
	}

	public static int level() {
		return level;
	}

	/** Milliseconds since the last XP gain started animating. */
	public static long animAgeMs() {
		return Util.getMillis() - animStartMs;
	}
}
