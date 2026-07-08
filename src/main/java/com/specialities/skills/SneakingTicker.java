package com.specialities.skills;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;

/**
 * Sneaking XP: once a second while sneaking near hostiles that haven't
 * noticed the player, scaling with proximity — x1 at the edge of range up to
 * x10 right next to the mob.
 */
public final class SneakingTicker {
	private static final Map<UUID, Integer> sneakTicks = new HashMap<>();

	private SneakingTicker() {
	}

	public static void onEndServerTick(final MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!player.isDiscrete() || player.isCreative()
					|| SkillManager.get(player).level(Skill.SNEAKING) >= Tuning.MAX_LEVEL) {
				sneakTicks.remove(player.getUUID());
				continue;
			}

			int ticks = sneakTicks.merge(player.getUUID(), 1, Integer::sum);
			if (ticks < 20) {
				continue;
			}

			sneakTicks.put(player.getUUID(), 0);

			List<Mob> unaware = player.level().getEntitiesOfClass(Mob.class,
					player.getBoundingBox().inflate(Tuning.SNEAK_XP_RANGE),
					mob -> mob instanceof Enemy && mob.isAlive() && mob.getTarget() != player);

			if (unaware.isEmpty()) {
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

	public static void onLeave(final ServerPlayer player) {
		sneakTicks.remove(player.getUUID());
	}
}
