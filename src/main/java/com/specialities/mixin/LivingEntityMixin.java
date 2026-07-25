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
/*import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
*///?}

import com.specialities.ModTags;
import com.specialities.platform.SkillStore;
//? if >=1.20.5 {
//?} else {
/*import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
*///?}
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
//? if >=1.20.5 {
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
	// Two handlers that exist on the 1.20.1 node only, because the fabric-api event and
	// the EnchantmentHelper overload they stand in for both arrived later. Neither adds
	// balance logic: the first calls the same SkillEvents code the real event calls, the
	// second adds the same Tuning value EnchantmentHelperMixin adds.
	//? if >=1.20.5 {
	//?} else {
	/*// ServerLivingEntityEvents.AFTER_DAMAGE does not exist in fabric-api 0.92.11
	// (SkillEvents has the evidence), and nothing else there reports the damage actually
	// taken. This is where fabric-api's own implementation of that event injects.
	//
	// The two filters the event's consumer applies are reproduced by the injection point
	// itself plus one check: `actuallyHurt` returns EARLY (offset 8) when the entity is
	// invulnerable and EARLY (offset 113) when the post-absorption amount is exactly 0 —
	// which is where a shield-blocked hit ends up, since `hurt` zeroes the damage before
	// calling this — so TAIL, the last RETURN, is reached only on the path that really
	// reduced health. At that point argument slot 2 holds the post-armor,
	// post-absorption amount, i.e. the event's `damageTaken`. Structure read off
	// `javap -c` of the 1.20.1 class, not assumed.
	@Inject(method = "actuallyHurt(Lnet/minecraft/world/damagesource/DamageSource;F)V", at = @At("TAIL"))
	private void specialities$afterDamageXp(final DamageSource source, final float damageTaken,
			final CallbackInfo ci) {
		if (damageTaken <= 0.0F) {
			return;
		}

		SkillEvents.afterDamage((LivingEntity) (Object) this, source, damageTaken);
	}

	// Acrobatics protection points, re-rooted (design R-05). Above 1.21 this is
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
	*///?}
}
