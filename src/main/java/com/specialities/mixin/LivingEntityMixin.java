package com.specialities.mixin;

import com.specialities.skills.Skill;
import com.specialities.skills.SkillCategories;
//? if >=1.20.5 {
//?} else {
/*import com.specialities.skills.SkillEvents;
*///?}
import com.specialities.skills.SkillManager;
import com.specialities.skills.Tuning;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
//? if >=1.20.5 {
//?} else {
/*import org.spongepowered.asm.mixin.injection.Inject;
*///?}
import org.spongepowered.asm.mixin.injection.ModifyVariable;
//? if >=1.20.5 {
//?} else {
/*import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
*///?}

import com.specialities.ModTags;
import com.specialities.platform.SkillStore;
// `>=1.21` on both, which is one step wider than the `>=1.20.5` block below: the two
// re-rooted EnchantmentHelper handlers at the end of this file are `<1.21` and the
// AFTER_DAMAGE substitute is `<1.20.5`, and `Local` is used by all three.
//? if >=1.21 {
//?} else {
/*import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
*///?}
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
//? if >=1.21 {
//?} else {
/*import com.llamalad7.mixinextras.sugar.Local;
*///?}

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
// jspecify is one of the game's OWN libraries only from 1.21.11 up (conventions
// §5e-bis); below that it is absent and org.jetbrains:annotations 26.0.2 (on the
// compile classpath via fabric-loader) supplies a @Nullable that is @Target(TYPE_USE)
// as well, so nothing but the import forks.
//? if >=1.21.11 {
import org.jspecify.annotations.Nullable;
//?} else {
/*import org.jetbrains.annotations.Nullable;
*///?}

/**
 * Combat skill damage multiplier — real weapon attacks only — and the
 * acrobatics fall-protection uncap.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
	/**
	 * Combat: more damage per level, on a weapon attack and nothing else.
	 *
	 * <p>Three things qualify, and they are spelled out here rather than hidden
	 * behind one predicate because the sneaking skill below takes two of the
	 * same three: a real melee swing (the strict test — see
	 * {@link com.specialities.MeleeSwing}, a damage source cannot be trusted to
	 * say what a swing is), an arrow from a bow or crossbow, and a thrown
	 * melee weapon, which in vanilla means a trident. A trident stab is already
	 * a swing, so both halves of the trident are covered.
	 *
	 * <p>Everything else is out: bleeds, poison, magic, thorns reflects and any
	 * other passive proc that borrows the player as its damage source, from
	 * this mod or any other. They all name the player as both the causing and
	 * the direct entity, which is why the damage source alone cannot be asked.
	 */
	// design §3.2's worked example, and the ONE injection point in the tree whose host method
	// is renamed AND loses a parameter below 1.21.2: `hurtServer(ServerLevel,DamageSource,F)Z`
	// becomes `hurt(DamageSource,F)Z` (1.21.1 mojmap: `1133:1278:boolean hurt(DamageSource,
	// float) -> a`, and no `hurtServer` on LivingEntity at all). Only the annotation, the
	// parameter list and the mandatory both-sides guard are inside the block — every line of
	// balance logic below it is shared, so this stays one implementation (conventions §5a).
	//? if >=1.21.2 {
	@ModifyVariable(
			method = "hurtServer(Lnet/minecraft/server/level/ServerLevel;"
					+ "Lnet/minecraft/world/damagesource/DamageSource;F)Z",
			at = @At("HEAD"),
			argsOnly = true)
	private float specialities$applyCombatDamage(final float damage, final ServerLevel level, final DamageSource source,
			final float originalDamage) {
	//?} else {
	/*@ModifyVariable(
			method = "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z",
			at = @At("HEAD"),
			argsOnly = true)
	private float specialities$applyCombatDamage(final float damage, final DamageSource source,
			final float originalDamage) {
		// `hurt` runs on BOTH logical sides below 1.21.2 and MeleeSwing's flag is
		// server-thread-only, so this early-out is mandatory, not defensive.
		if (((LivingEntity) (Object) this).level().isClientSide()) {
			return damage;
		}

	*///?}
		if (!(source.getEntity() instanceof ServerPlayer attacker) || (Object) this == attacker) {
			return damage;
		}

		if (!SkillCategories.isMeleeSwing(attacker, source)
				&& !SkillCategories.isRangedWeaponShot(attacker, source)
				&& !SkillCategories.isThrownMeleeWeapon(attacker, source)) {
			return damage;
		}

		int combatLevel = SkillManager.get(attacker).level(Skill.COMBAT);
		if (combatLevel <= 0) {
			return damage;
		}

		return damage * Tuning.damageMultiplier(combatLevel);
	}

	/**
	 * Vanilla clamps protection points at 20 (80% reduction). For fall damage on
	 * players we let the 20..25 point range scale on to 100%: the pre-reduction
	 * damage is scaled so the final result matches an uncapped formula.
	 * (25+ points never reaches here — ALLOW_DAMAGE cancels the hit entirely.)
	 */
	//? if >=1.21.2 {
	@ModifyVariable(
			method = "hurtServer(Lnet/minecraft/server/level/ServerLevel;"
					+ "Lnet/minecraft/world/damagesource/DamageSource;F)Z",
			at = @At("HEAD"),
			argsOnly = true)
	private float specialities$uncapFallProtection(final float damage, final ServerLevel level,
			final DamageSource source, final float originalDamage) {
	//?} else {
	/*@ModifyVariable(
			method = "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z",
			at = @At("HEAD"),
			argsOnly = true)
	private float specialities$uncapFallProtection(final float damage, final DamageSource source,
			final float originalDamage) {
		if (((LivingEntity) (Object) this).level().isClientSide()) {
			return damage;
		}

	*///?}
		if (!((Object) this instanceof ServerPlayer player) || !source.is(DamageTypeTags.IS_FALL)) {
			return damage;
		}

		// getDamageProtection(ServerLevel, LivingEntity, DamageSource) is AS-IS on 1.21.1
		// (mojmap 164:166); the only thing missing there is the `level` parameter this
		// handler no longer receives, and the ServerPlayer just above supplies it.
		//
		// Below 1.21 the method is `(Iterable<ItemStack>, DamageSource)I` — no victim, so
		// EnchantmentHelperMixin cannot add the acrobatics points to it and this call site
		// adds them, rounded to the int the pool is made of on that version. The SAME
		// rounding is applied by `specialities$legacyFallProtectionPoints` below and by
		// SkillEvents' immunity check; all three must agree or the correction on the next
		// line stops matching the reduction vanilla actually applied.
		//? if >=1.21.2 {
		float points = EnchantmentHelper.getDamageProtection(level, player, source);
		//?} elif >=1.21 {
		/*float points = EnchantmentHelper.getDamageProtection(player.serverLevel(), player, source);
		*///?} else {
		/*float points = EnchantmentHelper.getDamageProtection(player.getArmorSlots(), source)
				+ Math.round(Tuning.acrobaticsProtectionPoints(SkillManager.get(player).level(Skill.ACROBATICS)));
		*///?}
		if (points <= 20.0F) {
			return damage;
		}

		// Vanilla will apply (1 - 20/25); correct it to (1 - points/25).
		float capped = Math.min(points, Tuning.FALL_IMMUNITY_POINTS);
		return damage * (Tuning.FALL_IMMUNITY_POINTS - capped) / 5.0F;
	}

	/**
	 * Sneaking: harder to detect while sneaking. Multiplies the same visibility
	 * value invisibility uses, so both stack; each worn heavy armor piece strips
	 * 25% of the skill bonus.
	 */
	@ModifyReturnValue(method = "getVisibilityPercent", at = @At("RETURN"))
	private double specialities$sneakVisibility(final double original, final @Nullable Entity targetingEntity) {
		if (!((Object) this instanceof ServerPlayer player) || !player.isDiscrete()) {
			return original;
		}

		int level = SkillManager.get(player).level(Skill.SNEAKING);
		if (level <= 0) {
			return original;
		}

		int heavyPieces = 0;
		for (EquipmentSlot slot : new EquipmentSlot[] {
				EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET }) {
			if (player.getItemBySlot(slot).is(ModTags.HEAVY_ARMOR)) {
				heavyPieces++;
			}
		}

		return original * Tuning.sneakVisibilityMultiplier(level, heavyPieces);
	}

	/**
	 * Sneaking: guaranteed critical hit on an unaware target — once per enemy.
	 *
	 * <p>Two tiers, and nothing else qualifies for either. A melee swing (the
	 * strict test — see {@link com.specialities.MeleeSwing}, a damage source
	 * cannot be trusted to say what a swing is) gets the full multiplier; an
	 * arrow from a bow or crossbow gets the weaker ranged one. Magic, bleeds,
	 * poison, thorns and every other passive that borrows the player as its
	 * damage source get nothing.
	 *
	 * <p>Narrower than the combat multiplier above, which also takes a thrown
	 * trident. That difference is deliberate and stays where the author put it:
	 * the ranged tier here is the {@code specialities:ranged_weapons} tag, the
	 * same thing archery trains off, and widening it is a balance call rather
	 * than something to slip in while fixing combat's scope.
	 */
	//? if >=1.21.2 {
	@ModifyVariable(
			method = "hurtServer(Lnet/minecraft/server/level/ServerLevel;"
					+ "Lnet/minecraft/world/damagesource/DamageSource;F)Z",
			at = @At("HEAD"),
			argsOnly = true)
	private float specialities$stealthCrit(final float damage, final ServerLevel level, final DamageSource source,
			final float originalDamage) {
	//?} else {
	/*@ModifyVariable(
			method = "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z",
			at = @At("HEAD"),
			argsOnly = true)
	private float specialities$stealthCrit(final float damage, final DamageSource source,
			final float originalDamage) {
		if (((LivingEntity) (Object) this).level().isClientSide()) {
			return damage;
		}

	*///?}
		if (!(source.getEntity() instanceof ServerPlayer attacker) || !attacker.isDiscrete()) {
			return damage;
		}

		if (!((Object) this instanceof Mob mob) || mob.getTarget() == attacker) {
			return damage;
		}

		int sneakLevel = SkillManager.get(attacker).level(Skill.SNEAKING);
		float multiplier;

		if (SkillCategories.isMeleeSwing(attacker, source)) {
			multiplier = Tuning.stealthCritMeleeMultiplier(sneakLevel);
		} else if (SkillCategories.isRangedWeaponShot(attacker, source)) {
			multiplier = Tuning.stealthCritRangedMultiplier(sneakLevel);
		} else {
			return damage;
		}

		if (SkillStore.INSTANCE.isStealthCritDone(mob)) {
			return damage;
		}

		SkillStore.INSTANCE.markStealthCritDone(mob);
		return damage * multiplier;
	}

	// ---------------------------------------------------------------------------
	// Handlers that exist on the 1.20.1 node only, because the fabric-api event and the
	// EnchantmentHelper overloads they stand in for all arrived later. None of them adds
	// balance logic: the first calls the same SkillEvents code the real event calls, the
	// second adds the same Tuning value EnchantmentHelperMixin adds, and the third keeps the
	// re-rooted looting hook as narrow as the modern one.
	//
	// TWO boundaries, not one, because each substitute carries the boundary of the API it
	// substitutes FOR: `ServerLivingEntityEvents.AFTER_DAMAGE` is a `>=1.20.5` fabric-api
	// row, while both EnchantmentHelper re-roots are `>=1.21` vanilla rows. Identical node
	// sets today — nothing is registered between 1.20.5 and 1.21 — and deliberately not
	// collapsed into one block for that reason: writing the wrong-but-equivalent predicate is
	// how a node landing in that gap gets a handler with no counterpart on either side.
	//? if >=1.20.5 {
	//?} else {
	/*// ServerLivingEntityEvents.AFTER_DAMAGE does not exist in fabric-api 0.92.11
	// (SkillEvents has the evidence), and nothing else there reports the damage actually
	// taken. So this reproduces the event at the site fabric-api's own implementation uses
	// — TAIL of the `hurt`/`hurtServer` family — with fabric-api's own three semantics.
	// All three are load-bearing, and the earlier `actuallyHurt` TAIL inject got each of
	// them wrong; this is the regression it replaces.
	//
	// 1. IT HAS TO FIRE FOR PLAYERS. `Player` OVERRIDES `actuallyHurt` on 1.20.1 and never
	//    calls super (mojmap `Player -> byo`, `actuallyHurt -> f`; that method's `javap -c`
	//    contains ZERO `invokespecial`), so a TAIL inject on `LivingEntity.actuallyHurt`
	//    was dead for every player — DEFENCE XP, ACROBATICS XP and the attacker half of
	//    PvP with it. `hurt` is the one site every entity shares: `Player.hurt` ends in
	//    `invokespecial LivingEntity.hurt` (offset 137) and `ServerPlayer.hurt` ends in
	//    `invokespecial Player.hurt` (offset 149).
	// 2. `damageTaken` IS THE PRE-ARMOR AMOUNT. The event reports the damage after shields
	//    and extra freezing damage and explicitly NOT after armor or enchantment reduction
	//    (`ServerLivingEntityEvents.AFTER_DAMAGE`'s own javadoc). `actuallyHurt` is where
	//    armor, magic absorption and absorption hearts are applied, so its argument is
	//    post-everything and under-reported every hit on an armoured target. The value the
	//    event reports is `hurt`'s own argument slot 2 as mutated in place: `fstore_2` at
	//    offset 110 (a shield block zeroes it) and at 179 (freezing x5), read back by the
	//    `actuallyHurt` calls at 234 and 262. A Mixin callback loads the target's args from
	//    their slots at the injection point, so the parameter below IS that local.
	// 3. IT MUST NOT FIRE ON A KILLING BLOW. "This event is not fired if the entity was
	//    killed by the damage" — fabric-api enforces that with a `!isDeadOrDying()` guard
	//    inside the handler (0.116.14's compiled handler is `method_29504` / `ifne`), and
	//    vanilla's own second `isDeadOrDying()` at offset 618 has already run by TAIL.
	//
	// The remaining two filters are the event CONSUMER's, and this file applies them
	// because it calls the consumer directly: positive damage, and not shield-blocked.
	// `blocked` is vanilla's `flag`, the first boolean local (`istore 4` at offset 82,
	// `iconst_1` at 148 in the shield branch) — the same local fabric-api captures, and
	// the same ordinal, since 1.20.1's frame at TAIL is slot 3 = the pre-shield amount,
	// slot 4 = blocked, exactly like 1.21.1's. Everything above is `javap -c` of the
	// vanilla 1.20.1 class plus `javap -v` of fabric-entity-events-v1 0.116.14, not
	// assumed.
	@Inject(method = "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z", at = @At("TAIL"))
	private void specialities$afterDamageXp(final DamageSource source, final float damageTaken,
			final CallbackInfoReturnable<Boolean> cir, @Local(ordinal = 0) final boolean blocked) {
		LivingEntity self = (LivingEntity) (Object) this;

		if (blocked || damageTaken <= 0.0F || self.isDeadOrDying()) {
			return;
		}

		SkillEvents.afterDamage(self, source, damageTaken);
	}
	*///?}

	//? if >=1.21 {
	//?} else {
	/*// Acrobatics protection points, re-rooted (design R-05). Above 1.21 this is
	// EnchantmentHelperMixin's @ModifyReturnValue on
	// `getDamageProtection(ServerLevel, LivingEntity, DamageSource)F`, which cannot work
	// here because the legacy overload takes no victim. The call site inside
	// `getDamageAfterMagicAbsorb` does have one — `this` — so the bonus is added there.
	//
	// Modifying the value BEFORE vanilla's `if (points > 0)` gate matters: without it a
	// player with no armor enchantments at all would skip `CombatRules` entirely and get
	// no acrobatics reduction. The pool is an int on this version, hence Math.round.
	@ModifyExpressionValue(
			method = "getDamageAfterMagicAbsorb(Lnet/minecraft/world/damagesource/DamageSource;F)F",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/item/enchantment/EnchantmentHelper;"
							+ "getDamageProtection(Ljava/lang/Iterable;Lnet/minecraft/world/damagesource/DamageSource;)I"))
	private int specialities$legacyFallProtectionPoints(final int original,
			@Local(argsOnly = true) final DamageSource source) {
		if (!((Object) this instanceof ServerPlayer player) || !source.is(DamageTypeTags.IS_FALL)) {
			return original;
		}

		return original + Math.round(Tuning.acrobaticsProtectionPoints(SkillManager.get(player).level(Skill.ACROBATICS)));
	}

	// Passive looting has to reach the LOOT TABLE and nothing else, and below 1.21 the hook
	// that delivers it is wider than that. Census of every `getMobLooting(LivingEntity)I`
	// caller in the 1.20.1 client jar, by constant-pool scan rather than by grep, is exactly
	// three:
	//
	//   LootingEnchantFunction                    loot table  — the modern
	//                                             EnchantedCountIncreaseFunction
	//   LootItemRandomChanceWithLootingCondition  loot table  — the modern
	//                                             LootItemRandomChanceWithEnchantedBonusCondition
	//   LivingEntity.dropAllDeathLoot             NOT loot    — it passes the value to
	//                                             dropCustomDeathLoot(DamageSource,I,Z),
	//                                             where Mob turns each looting level into
	//                                             +1% mob EQUIPMENT drop chance
	//                                             (`iload_2` inside its nextFloat test)
	//
	// The third has no counterpart on the four newer nodes: `dropCustomDeathLoot` LOST its
	// looting parameter in 1.21, so the modern hook cannot raise an equipment drop and this
	// node must not either. Hence: EnchantmentHelperMixin adds the bonus to every
	// `getMobLooting` return, and this handler takes the same number — one shared definition,
	// so they cancel exactly — back off at the single call site that feeds equipment drops.
	// Vanilla's own looting level and any other mod's contribution are untouched, which
	// subtracting rather than recomputing is what buys.
	//
	// ONE `getMobLooting` call in `dropAllDeathLoot` (offset 16, stored to the `i` that
	// offset 74 hands to `dropCustomDeathLoot`), so no ordinal. `dropFromLootTable` at offset
	// 66 reads looting again, through the loot function, and that read still gets the bonus.
	@ModifyExpressionValue(
			method = "dropAllDeathLoot(Lnet/minecraft/world/damagesource/DamageSource;)V",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/item/enchantment/EnchantmentHelper;"
							+ "getMobLooting(Lnet/minecraft/world/entity/LivingEntity;)I"))
	private int specialities$equipmentDropsWithoutPassiveLooting(final int original,
			@Local(argsOnly = true) final DamageSource source) {
		return original - SkillCategories.passiveLootingBonus(source.getEntity());
	}
	*///?}
}
