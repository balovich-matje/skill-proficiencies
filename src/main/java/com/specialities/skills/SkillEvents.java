package com.specialities.skills;

import com.specialities.platform.SkillStore;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
// 0.92.11's fabric-entity-events-v1 (1.6.1) declares only ALLOW_DAMAGE, ALLOW_DEATH,
// AFTER_DEATH and MOB_CONVERSION on ServerLivingEntityEvents — no AFTER_DAMAGE — and
// only COPY_FROM, AFTER_RESPAWN and ALLOW_DEATH on ServerPlayerEvents — no JOIN, no
// LEAVE. (Read off the module jar with javap, per design §3.4's "verify".) The join and
// leave hooks re-root onto fabric-networking-api-v1's connection events, which exist on
// every node and fire at the same points; AFTER_DAMAGE re-roots onto a mixin, because
// nothing in 0.92.11 reports the damage actually taken. This file IS the event seam
// (design §2's rejected-seams table), so all three substitutions live here.
//? if >=1.20.5 {
//?} else {
/*import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
*///?}
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

		// Below 1.20.5 there is no AFTER_DAMAGE to register at all: LivingEntityMixin's
		// TAIL hook on `hurt` — the same site fabric-api's own implementation injects at —
		// calls `afterDamage` below instead, applying the same two filters this lambda does.
		//? if >=1.20.5 {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (blocked || damageTaken <= 0.0F) {
				return;
			}

			awardAttackerXp(entity, source, damageTaken);
			awardVictimXp(entity, source, damageTaken);
		});
		//?}

		// Acrobatics: with enough combined protection (Feather Falling + skill),
		// fall damage is negated entirely — no hurt flash, no knockback.
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (!(entity instanceof ServerPlayer player) || !source.is(DamageTypeTags.IS_FALL)) {
				return true;
			}

			if (!(player.level() instanceof ServerLevel serverLevel)) {
				return true;
			}

			// `getDamageProtection` has no victim parameter below 1.21 (there it is
			// `(Iterable<ItemStack>, DamageSource)I`), so EnchantmentHelperMixin cannot add
			// the acrobatics points to its return value and this call site adds them itself.
			// The points are an int on that node, so the bonus is rounded — and it is
			// rounded THE SAME WAY in all three places that need the total (here, the uncap
			// handler in LivingEntityMixin, and LivingEntityMixin's substitute for the
			// EnchantmentHelperMixin hook). If those three ever disagree the 20->25
			// correction math silently stops matching what vanilla applied.
			//? if >=1.21 {
			return EnchantmentHelper.getDamageProtection(serverLevel, player, source) < Tuning.FALL_IMMUNITY_POINTS;
			//?} else {
			/*return EnchantmentHelper.getDamageProtection(player.getArmorSlots(), source)
					+ Math.round(Tuning.acrobaticsProtectionPoints(SkillManager.get(player).level(Skill.ACROBATICS)))
					< Tuning.FALL_IMMUNITY_POINTS;
			*///?}
		});

		//? if >=1.20.5 {
		ServerPlayerEvents.JOIN.register(player -> {
			DefencePassives.apply(player);
			// No-op on every node registered today — fabric-api syncs the skills
			// attachment itself. Mandatory on the nodes that cannot (design R-03),
			// and this is the join hook it needs, so the call site lands with the
			// seam rather than with the node.
			SkillStore.INSTANCE.resyncSkills(player);
		});
		//?} else {
		/*// ServerPlayConnectionEvents.JOIN fires once the play connection is ready, which
		// is where ServerPlayerEvents.JOIN fires too (fabric-api's own JOIN is a later
		// convenience wrapper around the same point). `handler.player` is the public field
		// on ServerGamePacketListenerImpl. resyncSkills is NOT a no-op on this node — it is
		// the only thing that ever gives the client its skill map (design R-03).
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			DefencePassives.apply(handler.player);
			SkillStore.INSTANCE.resyncSkills(handler.player);
		});
		*///?}
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> DefencePassives.apply(newPlayer));
		//? if >=1.20.5 {
		ServerPlayerEvents.LEAVE.register(player -> {
			AthleticsTicker.onLeave(player);
			SneakingTicker.onLeave(player);
		});
		//?} else {
		/*ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			AthleticsTicker.onLeave(handler.player);
			SneakingTicker.onLeave(handler.player);
		});
		*///?}
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			AthleticsTicker.onEndServerTick(server);
			SneakingTicker.onEndServerTick(server);
		});
	}

	// The AFTER_DAMAGE substitute below 1.20.5. Public because a mixin calls it; gated so
	// the nodes that have the real event do not carry an unreachable method. `damageTaken`
	// is what the event reports: the amount after shields and extra freezing damage and
	// BEFORE armor and enchantment reduction (the event's javadoc says so explicitly). The
	// caller applies the event's own two consumer filters — positive damage, not blocked —
	// and reproduces fabric-api's `!isDeadOrDying()` gate on the invocation itself.
	//? if >=1.20.5 {
	//?} else {
	/*public static void afterDamage(final LivingEntity entity, final DamageSource source, final float damageTaken) {
		awardAttackerXp(entity, source, damageTaken);
		awardVictimXp(entity, source, damageTaken);
	}

	*///?}
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
