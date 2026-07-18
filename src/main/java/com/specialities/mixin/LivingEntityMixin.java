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
 * Combat skill damage multiplier — weapons only (melee weapons by tag,
 * projectiles by damage source) — and the acrobatics fall-protection uncap.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
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

		if (!SkillCategories.isWeaponAttack(attacker, source)) {
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

		if (!SkillCategories.isWeaponAttack(attacker, source)) {
			return damage;
		}

		if (Boolean.TRUE.equals(((AttachmentTarget) mob).getAttached(ModAttachments.STEALTH_CRIT_DONE))) {
			return damage;
		}

		((AttachmentTarget) mob).setAttached(ModAttachments.STEALTH_CRIT_DONE, true);
		int sneakLevel = SkillManager.get(attacker).level(Skill.SNEAKING);
		return damage * Tuning.stealthCritMultiplier(sneakLevel);
	}
}
