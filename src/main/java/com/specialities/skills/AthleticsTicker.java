package com.specialities.skills;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.specialities.Specialities;

//? if >=1.21 {
import net.minecraft.resources.Identifier;
//?}
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
	// Attribute modifiers are keyed by a UUID + a display name below 1.21, and by a
	// ResourceLocation from 1.21 up. The UUID must be a STABLE literal, not a fresh random
	// one: DefencePassives' modifiers are permanent, so they are written into the player's
	// Attributes NBT and a per-session id would pile up duplicates on every login. This
	// one is transient, but it is derived the same way for consistency.
	//
	// Each literal is `UUID.nameUUIDFromBytes("<the 1.21+ identifier>".getBytes(UTF_8))`, so
	// it is reproducible from the id it replaces rather than invented — one line of Java or
	// a version-3 MD5 name UUID in any other language re-derives it.
	//? if >=1.21 {
	private static final Identifier SPRINT_MODIFIER_ID = Specialities.id("athletics_sprint");
	//?} else {
	/*private static final UUID SPRINT_MODIFIER_ID = UUID.fromString("0fb147b0-ee56-3419-a1e6-c099b25a86fe");
	private static final String SPRINT_MODIFIER_NAME = "specialities:athletics_sprint";
	*///?}
	private static final Map<UUID, Integer> sprintTicks = new HashMap<>();

	private AthleticsTicker() {
	}

	// The return type is what `client/mixin/AbstractClientPlayerMixin` passes to
	// `AttributeInstance.getModifier(...)`. The two must fork TOGETHER — flagged as a
	// cross-set coupling hazard by Stage 2e and recorded in design §3.4 — which is why both
	// halves land in one commit.
	//? if >=1.21 {
	public static Identifier sprintModifierId() {
	//?} else {
	/*public static UUID sprintModifierId() {
	*///?}
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
				// 1.21.2 shortened MOVEMENT_SPEED to SPEED (1.21.1 mojmap: `Holder
				// MOVEMENT_SPEED -> a`); the Holder-typed getEffect overload is AS-IS.
				MobEffectInstance effect = player.getEffect(
						/*? if >=1.21.2 {*/MobEffects.SPEED);
						/*?} else *///MobEffects.MOVEMENT_SPEED);
				double effectBonus = effect == null ? 0.0 : Tuning.SWIFTNESS_PER_TIER * (effect.getAmplifier() + 1);
				desired = Math.min(Tuning.SWIFTNESS_PER_TIER * tier,
						Math.max(0.0, Tuning.SPRINT_SPEED_CAP - effectBonus));
			}
		}

		AttributeModifier existing = speed.getModifier(SPRINT_MODIFIER_ID);
		// `AttributeModifier.amount()` was `getAmount()` before the record rewrite, and the
		// three Operation constants were renamed (ADD_VALUE/ADD_MULTIPLIED_BASE/
		// ADD_MULTIPLIED_TOTAL were ADDITION/MULTIPLY_BASE/MULTIPLY_TOTAL). Same operation,
		// same arithmetic — only the names and the extra display-name argument move.
		//? if >=1.21 {
		double current = existing == null ? 0.0 : existing.amount();
		//?} else {
		/*double current = existing == null ? 0.0 : existing.getAmount();
		*///?}

		if (current == desired) {
			return;
		}

		speed.removeModifier(SPRINT_MODIFIER_ID);
		if (desired > 0) {
			//? if >=1.21 {
			speed.addTransientModifier(new AttributeModifier(SPRINT_MODIFIER_ID, desired,
					AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			//?} else {
			/*speed.addTransientModifier(new AttributeModifier(SPRINT_MODIFIER_ID, SPRINT_MODIFIER_NAME, desired,
					AttributeModifier.Operation.MULTIPLY_TOTAL));
			*///?}
		}
	}
}
