package com.specialities.skills;

import com.specialities.platform.SkillStore;

// THE EVENT SEAM IS THIS FILE, and on the loader axis it stays this file: only the five
// REGISTRATION calls fork, and each loader's arm hands the very same lambda to a helper in
// `com.specialities.platform` (the one package allowed to name loader API, conventions §5g).
// The bodies — every filter, every XP formula — are outside every conditional, which is
// §5a applied to events instead of to mixins.
//
// WHAT A LOADER HELPER OWES THE CALLER, per R-20's first bullet, because a re-rooted event
// must reproduce the event's CONTRACT and not merely fire somewhere plausible:
//   * afterBlockBreak — fires AFTER the block is gone, once per successful player break, with
//     the PRE-break BlockState. Neither loader has an "after break" event (NeoForge and
//     LexForge both have a cancellable PRE `BlockEvent$BreakEvent`), so this one is a
//     re-rooting and not a rename; design §5c prefers a mixin on the destroy path over a
//     synthesised event, and that decision belongs to the node's own agent.
//   * afterDamage — hand it `SkillEvents::afterDamage` and reproduce all three AFTER_DAMAGE
//     semantics first: the value must be POST-shield/freezing and PRE-armor, the invocation
//     must be skipped when the victim `isDeadOrDying()`, and the two consumer filters
//     (`blocked`, `damageTaken <= 0`) must be applied. The Fabric arms below and the shipped
//     1.20.1 mixin already do exactly this.
//   * allowDamage — a boolean VETO: false cancels the damage entirely. Must run before armor.
//   * playerJoin — once the connection can receive packets (resyncSkills sends one).
//   * afterRespawn — with the NEW player instance.
//   * playerLeave — before the player object is discarded.
//   * endServerTick — once per server tick, at the END of it.
//? if fabric {
//?} elif neoforge {
/*import com.specialities.platform.NeoForgeEvents;
*///?} elif forge {
/*import com.specialities.platform.ForgeEvents;
*///?}

//? if fabric {
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
//?}
// 0.92.11's fabric-entity-events-v1 (1.6.1) declares only ALLOW_DAMAGE, ALLOW_DEATH,
// AFTER_DEATH and MOB_CONVERSION on ServerLivingEntityEvents — no AFTER_DAMAGE — and
// only COPY_FROM, AFTER_RESPAWN and ALLOW_DEATH on ServerPlayerEvents — no JOIN, no
// LEAVE. (Read off the module jar with javap, per design §3.4's "verify".) The join and
// leave hooks re-root onto fabric-networking-api-v1's connection events, which exist on
// every node and fire at the same points; AFTER_DAMAGE re-roots onto a mixin, because
// nothing in 0.92.11 reports the damage actually taken. This file IS the event seam
// (design §2's rejected-seams table), so all three substitutions live here.
//
// `fabric &&` is not a new boundary: this is a fabric-api substitute, so it is scoped to the
// loader whose API is missing it. The forge node is also below 1.20.5 and must not land here.
//? if fabric && <1.20.5 {
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
		// Only the registration call forks; the lambda below it is shared by every loader.
		// The helper's listener type must declare the same five parameters in the same order
		// (Level, Player, BlockPos, BlockState, BlockEntity) — they are inferred here.
		//? if fabric {
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
		//?} elif neoforge {
		/*NeoForgeEvents.afterBlockBreak((level, player, pos, state, blockEntity) -> {
		*///?} elif forge {
		/*ForgeEvents.afterBlockBreak((level, player, pos, state, blockEntity) -> {
		*///?}
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
		// Neither loader has the event either, so both hand their own substitute the same
		// shared `afterDamage`; the filters and the three R-20 semantics are the helper's job
		// there, exactly as they are the mixin's job on 1.20.1-fabric.
		//? if fabric && >=1.20.5 {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (blocked || damageTaken <= 0.0F) {
				return;
			}

			awardAttackerXp(entity, source, damageTaken);
			awardVictimXp(entity, source, damageTaken);
		});
		//?} elif neoforge {
		/*NeoForgeEvents.afterDamage(SkillEvents::afterDamage);
		*///?} elif forge {
		/*ForgeEvents.afterDamage(SkillEvents::afterDamage);
		*///?}

		// Acrobatics: with enough combined protection (Feather Falling + skill),
		// fall damage is negated entirely — no hurt flash, no knockback.
		//
		// Registration only; the veto predicate below — including its own `>=1.21` fork for the
		// re-rooted `getDamageProtection` — is shared. The helper's listener returns boolean and
		// false must cancel the damage, which is the ALLOW_DAMAGE contract.
		//? if fabric {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
		//?} elif neoforge {
		/*NeoForgeEvents.allowDamage((entity, source, amount) -> {
		*///?} elif forge {
		/*ForgeEvents.allowDamage((entity, source, amount) -> {
		*///?}
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

		//? if fabric && >=1.20.5 {
		ServerPlayerEvents.JOIN.register(player -> {
			DefencePassives.apply(player);
			// No-op on every node registered today — fabric-api syncs the skills
			// attachment itself. Mandatory on the nodes that cannot (design R-03),
			// and this is the join hook it needs, so the call site lands with the
			// seam rather than with the node.
			SkillStore.INSTANCE.resyncSkills(player);
		});
		//?} elif neoforge {
		/*NeoForgeEvents.playerJoin(SkillEvents::onPlayerJoin);
		*///?} elif forge {
		/*ForgeEvents.playerJoin(SkillEvents::onPlayerJoin);
		*///?} else {
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
		//? if fabric {
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> DefencePassives.apply(newPlayer));
		//?} elif neoforge {
		/*NeoForgeEvents.afterRespawn(DefencePassives::apply);
		*///?} elif forge {
		/*ForgeEvents.afterRespawn(DefencePassives::apply);
		*///?}
		//? if fabric && >=1.20.5 {
		ServerPlayerEvents.LEAVE.register(player -> {
			AthleticsTicker.onLeave(player);
			SneakingTicker.onLeave(player);
		});
		//?} elif neoforge {
		/*NeoForgeEvents.playerLeave(SkillEvents::onPlayerLeave);
		*///?} elif forge {
		/*ForgeEvents.playerLeave(SkillEvents::onPlayerLeave);
		*///?} else {
		/*ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			AthleticsTicker.onLeave(handler.player);
			SneakingTicker.onLeave(handler.player);
		});
		*///?}
		// Registration only; the two ticker calls are shared. `server` is a MinecraftServer on
		// every loader.
		//? if fabric {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
		//?} elif neoforge {
		/*NeoForgeEvents.endServerTick(server -> {
		*///?} elif forge {
		/*ForgeEvents.endServerTick(server -> {
		*///?}
			AthleticsTicker.onEndServerTick(server);
			SneakingTicker.onEndServerTick(server);
		});
	}

	// The join/leave bodies the loader helpers are handed as method references. They exist for
	// the same reason the two Fabric arms above cannot be shared with them: the legacy Fabric
	// arm reads its player off a connection handler, so there is no one lambda shape that fits
	// all four. Gated to the loader axis so no Fabric node carries an unreachable method — the
	// Fabric nodes are required to stay instruction-identical while this axis lands.
	//? if fabric {
	//?} else {
	/*public static void onPlayerJoin(final ServerPlayer player) {
		DefencePassives.apply(player);
		// NOT a no-op on either loader: NeoForge attachment sync is not proven to push on
		// login (Phase B prep Q2) and Forge capabilities have no sync at all, so this is
		// design R-03's mandatory half. Verify it on the Tier-2 boot.
		SkillStore.INSTANCE.resyncSkills(player);
	}

	public static void onPlayerLeave(final ServerPlayer player) {
		AthleticsTicker.onLeave(player);
		SneakingTicker.onLeave(player);
	}

	*///?}

	// The AFTER_DAMAGE substitute wherever the event is missing: below 1.20.5 on Fabric (a
	// mixin calls it) and on BOTH Phase B loaders (their helper calls it). Gated so the nodes
	// that have the real event do not carry an unreachable method.
	//
	// `fabric && >=1.20.5` is the loader-scoped form of the same fabric-api boundary, not a new
	// one (conventions §5k): `ServerLivingEntityEvents.AFTER_DAMAGE` is a fabric-api `>=1.20.5`
	// row, so a node that is not on Fabric at all never has it regardless of version.
	//
	// `damageTaken` is what the event reports: the amount after shields and extra freezing
	// damage and BEFORE armor and enchantment reduction (the event's javadoc says so
	// explicitly). The caller applies the event's own two consumer filters — positive damage,
	// not blocked — and reproduces fabric-api's `!isDeadOrDying()` gate on the invocation
	// itself. All three are what R-20 found the first re-rooting of this event got wrong.
	//? if fabric && >=1.20.5 {
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
