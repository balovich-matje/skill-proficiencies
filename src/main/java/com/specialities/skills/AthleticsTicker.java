package com.specialities.skills;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.specialities.Specialities;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Athletics per-tick logic: the swiftness-style sprint speed bonus (capped so
 * potions/beacons + skill never exceed +80%) and sprint-time XP.
 */
public final class AthleticsTicker {
	private static final Identifier SPRINT_MODIFIER_ID = Specialities.id("athletics_sprint");
	private static final Map<UUID, Integer> sprintTicks = new HashMap<>();

	private AthleticsTicker() {
	}

	public static Identifier sprintModifierId() {
		return SPRINT_MODIFIER_ID;
	}

	public static void onEndServerTick(final MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			int level = SkillManager.get(player).level(Skill.ATHLETICS);
			boolean sprinting = player.isSprinting();

			updateSpeedModifier(player, level, sprinting);

			if (sprinting && !player.isCreative() && level < Tuning.MAX_LEVEL) {
				int ticks = sprintTicks.merge(player.getUUID(), 1, Integer::sum);

				if (ticks >= Tuning.SPRINT_XP_INTERVAL_TICKS) {
					sprintTicks.put(player.getUUID(), 0);
					SkillManager.addXp(player, Skill.ATHLETICS, Tuning.SPRINT_XP_PER_INTERVAL);
				}
			}
		}
	}

	public static void onLeave(final ServerPlayer player) {
		sprintTicks.remove(player.getUUID());
	}

	private static void updateSpeedModifier(final ServerPlayer player, final int level, final boolean sprinting) {
		AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed == null) {
			return;
		}

		double desired = 0.0;

		if (sprinting) {
			int tier = Tuning.swiftnessTier(level);

			if (tier > 0) {
				MobEffectInstance effect = player.getEffect(MobEffects.SPEED);
				double effectBonus = effect == null ? 0.0 : Tuning.SWIFTNESS_PER_TIER * (effect.getAmplifier() + 1);
				desired = Math.min(Tuning.SWIFTNESS_PER_TIER * tier,
						Math.max(0.0, Tuning.SPRINT_SPEED_CAP - effectBonus));
			}
		}

		AttributeModifier existing = speed.getModifier(SPRINT_MODIFIER_ID);
		double current = existing == null ? 0.0 : existing.amount();

		if (current == desired) {
			return;
		}

		speed.removeModifier(SPRINT_MODIFIER_ID);
		if (desired > 0) {
			speed.addTransientModifier(new AttributeModifier(SPRINT_MODIFIER_ID, desired,
					AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
	}
}
