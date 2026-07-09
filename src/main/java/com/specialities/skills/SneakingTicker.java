package com.specialities.skills;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.specialities.StealthStatePayload;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;

/**
 * Sneaking server logic, scanning nearby hostiles every half second:
 * proximity XP (once a second) while unaware hostiles are near, and the
 * stealth state (hidden/detected/none) pushed to the client for the vignette.
 */
public final class SneakingTicker {
	private static final int SCAN_INTERVAL_TICKS = 10;

	private static final Map<UUID, Integer> sneakTicks = new HashMap<>();
	private static final Map<UUID, Boolean> xpParity = new HashMap<>();
	private static final Map<UUID, Integer> lastState = new HashMap<>();

	private SneakingTicker() {
	}

	public static void onEndServerTick(final MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!player.isDiscrete() || player.isCreative() || player.isSpectator()) {
				sneakTicks.remove(player.getUUID());
				updateState(player, StealthStatePayload.NONE);
				continue;
			}

			int ticks = sneakTicks.merge(player.getUUID(), 1, Integer::sum);
			if (ticks < SCAN_INTERVAL_TICKS) {
				continue;
			}

			sneakTicks.put(player.getUUID(), 0);

			List<Mob> hostiles = player.level().getEntitiesOfClass(Mob.class,
					player.getBoundingBox().inflate(Tuning.SNEAK_XP_RANGE),
					mob -> mob instanceof Enemy && mob.isAlive());

			boolean detected = hostiles.stream().anyMatch(mob -> mob.getTarget() == player);
			List<Mob> unaware = hostiles.stream().filter(mob -> mob.getTarget() != player).toList();

			if (detected) {
				updateState(player, StealthStatePayload.DETECTED);
			} else if (!unaware.isEmpty()) {
				updateState(player, StealthStatePayload.HIDDEN);
			} else {
				updateState(player, StealthStatePayload.NONE);
			}

			// XP once a second (every second scan), only while undetected near
			// unaware hostiles and below the level cap.
			boolean awardTurn = Boolean.TRUE.equals(xpParity.merge(player.getUUID(), true, (a, b) -> !a));
			if (!awardTurn || detected || unaware.isEmpty()
					|| SkillManager.get(player).level(Skill.SNEAKING) >= Tuning.MAX_LEVEL) {
				continue;
			}

			double nearest = Math.sqrt(unaware.stream()
					.mapToDouble(player::distanceToSqr)
					.min()
					.orElse(Tuning.SNEAK_XP_RANGE * Tuning.SNEAK_XP_RANGE));

			// x10 at <=1 block, fading linearly to x1 at the edge of range.
			float closeness = (float) Mth.clamp(
					(Tuning.SNEAK_XP_RANGE - nearest) / (Tuning.SNEAK_XP_RANGE - 1.0), 0.0, 1.0);
			float multiplier = 1.0F + (Tuning.SNEAK_XP_MAX_MULTIPLIER - 1.0F) * closeness;

			SkillManager.addXp(player, Skill.SNEAKING,
					Math.round(Tuning.SNEAK_XP_BASE_PER_SECOND * multiplier));
		}
	}

	private static void updateState(final ServerPlayer player, final int state) {
		Integer previous = lastState.put(player.getUUID(), state);

		if (previous == null || previous != state) {
			ServerPlayNetworking.send(player, new StealthStatePayload(state));
		}
	}

	public static void onLeave(final ServerPlayer player) {
		sneakTicks.remove(player.getUUID());
		xpParity.remove(player.getUUID());
		lastState.remove(player.getUUID());
	}
}
