package com.specialities.mixin;

import com.specialities.skills.Skill;
import com.specialities.skills.SkillCategories;
import com.specialities.skills.SkillManager;
import com.specialities.skills.Tuning;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.specialities.ModAttachments;
import com.specialities.ModTags;
import com.specialities.skills.SkillEvents;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.jspecify.annotations.Nullable;

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
	@ModifyVariable(
			method = "hurtServer(Lnet/minecraft/server/level/ServerLevel;"
					+ "Lnet/minecraft/world/damagesource/DamageSource;F)Z",
			at = @At("HEAD"),
			argsOnly = true)
	private float specialities$applyCombatDamage(final float damage, final ServerLevel level, final DamageSource source,
			final float originalDamage) {
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
	@ModifyVariable(
			method = "hurtServer(Lnet/minecraft/server/level/ServerLevel;"
					+ "Lnet/minecraft/world/damagesource/DamageSource;F)Z",
			at = @At("HEAD"),
			argsOnly = true)
	private float specialities$uncapFallProtection(final float damage, final ServerLevel level,
			final DamageSource source, final float originalDamage) {
		if (!((Object) this instanceof ServerPlayer player) || !source.is(DamageTypeTags.IS_FALL)) {
			return damage;
		}

		float points = EnchantmentHelper.getDamageProtection(level, player, source);
		if (points <= 20.0F) {
			return damage;
		}

		// Vanilla will apply (1 - 20/25); correct it to (1 - points/25).
		float capped = Math.min(points, SkillEvents.FALL_IMMUNITY_POINTS);
		return damage * (SkillEvents.FALL_IMMUNITY_POINTS - capped) / 5.0F;
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
	@ModifyVariable(
			method = "hurtServer(Lnet/minecraft/server/level/ServerLevel;"
					+ "Lnet/minecraft/world/damagesource/DamageSource;F)Z",
			at = @At("HEAD"),
			argsOnly = true)
	private float specialities$stealthCrit(final float damage, final ServerLevel level, final DamageSource source,
			final float originalDamage) {
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

		if (Boolean.TRUE.equals(((AttachmentTarget) mob).getAttached(ModAttachments.STEALTH_CRIT_DONE))) {
			return damage;
		}

		((AttachmentTarget) mob).setAttached(ModAttachments.STEALTH_CRIT_DONE, true);
		return damage * multiplier;
	}
}
