package com.specialities.skills;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/**
 * XP gain sources and lifecycle wiring: block breaking, dealing weapon damage
 * (combat + specializations), taking damage (defence/acrobatics), sprinting
 * (athletics), and attribute passive re-application on join/respawn.
 */
public final class SkillEvents {
	/** Protection points at which fall damage reaches 100% reduction. */
	public static final float FALL_IMMUNITY_POINTS = 25.0F;

	private SkillEvents() {
	}

	public static void register() {
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			if (!(player instanceof ServerPlayer serverPlayer) || serverPlayer.isCreative()) {
				return;
			}

			Skill skill = SkillCategories.blockBreakSkill(state);
			if (skill == null) {
				return;
			}

			int xp = SkillCategories.blockBreakXp(serverPlayer, state);
			if (xp > 0) {
				SkillManager.addXp(serverPlayer, skill, xp);
			}
		});

		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (blocked || damageTaken <= 0.0F) {
				return;
			}

			awardAttackerXp(entity, source, damageTaken);
			awardVictimXp(entity, source, damageTaken);
		});

		// Acrobatics: with enough combined protection (Feather Falling + skill),
		// fall damage is negated entirely — no hurt flash, no knockback.
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (!(entity instanceof ServerPlayer player) || !source.is(DamageTypeTags.IS_FALL)) {
				return true;
			}

			if (!(player.level() instanceof ServerLevel serverLevel)) {
				return true;
			}

			return EnchantmentHelper.getDamageProtection(serverLevel, player, source) < FALL_IMMUNITY_POINTS;
		});

		ServerPlayerEvents.JOIN.register(DefencePassives::apply);
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> DefencePassives.apply(newPlayer));
		ServerPlayerEvents.LEAVE.register(player -> {
			AthleticsTicker.onLeave(player);
			SneakingTicker.onLeave(player);
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			AthleticsTicker.onEndServerTick(server);
			SneakingTicker.onEndServerTick(server);
		});
	}

	private static void awardAttackerXp(final LivingEntity victim, final DamageSource source, final float damageTaken) {
		if (!(source.getEntity() instanceof ServerPlayer attacker) || attacker == victim || attacker.isCreative()) {
			return;
		}

		Skill specialization = SkillCategories.specializationSkill(attacker, source);
		if (specialization == null) {
			return;
		}

		int xp = Math.max(1, Math.round(damageTaken * Tuning.COMBAT_XP_PER_DAMAGE));
		SkillManager.addXp(attacker, Skill.COMBAT, xp);
		SkillManager.addXp(attacker, specialization, xp);
	}

	private static void awardVictimXp(final LivingEntity victim, final DamageSource source, final float damageTaken) {
		if (!(victim instanceof ServerPlayer player) || source.getEntity() == player) {
			return;
		}

		if (source.is(DamageTypeTags.IS_FALL)) {
			SkillManager.addXp(player, Skill.ACROBATICS,
					Math.max(1, Math.round(damageTaken * Tuning.ACROBATICS_XP_PER_DAMAGE)));
			return;
		}

		float rate;
		if (source.getEntity() instanceof Creeper) {
			rate = Tuning.DEFENCE_CREEPER_RATE;
		} else if (source.getEntity() instanceof LivingEntity) {
			rate = Tuning.DEFENCE_MOB_RATE;
		} else {
			rate = Tuning.DEFENCE_ENVIRONMENT_RATE;
		}

		int xp = Math.max(1, Math.round(damageTaken * Tuning.DEFENCE_XP_PER_DAMAGE * rate));
		SkillManager.addXp(player, Skill.DEFENCE, xp);
	}
}
