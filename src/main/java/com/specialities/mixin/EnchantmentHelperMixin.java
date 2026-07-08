package com.specialities.mixin;

import com.specialities.skills.Skill;
import com.specialities.skills.SkillManager;
import com.specialities.skills.Tuning;
import com.specialities.ModTags;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * Passive looting from the combat skill: mob loot rolls read the attacker's
 * looting level through {@code getEnchantmentLevel(enchantment, entity)}
 * (see {@code EnchantedCountIncreaseFunction}), so the bonus stacks additively
 * with whatever is on the weapon.
 */
@Mixin(EnchantmentHelper.class)
public abstract class EnchantmentHelperMixin {
	@ModifyReturnValue(method = "getEnchantmentLevel", at = @At("RETURN"))
	private static int specialities$boostLooting(final int original, final Holder<Enchantment> enchantment,
			final LivingEntity entity) {
		if (!enchantment.is(Enchantments.LOOTING) || !(entity instanceof ServerPlayer player)) {
			return original;
		}

		if (!player.getMainHandItem().is(ModTags.WEAPONS)) {
			return original;
		}

		int bonus = Tuning.luckBonus(SkillManager.get(player).level(Skill.COMBAT));
		return bonus > 0 ? original + bonus : original;
	}

	/** Fishing passive: +1 Luck of the Sea per 20 levels (0..5), additive with the enchant. */
	@ModifyReturnValue(method = "getFishingLuckBonus", at = @At("RETURN"))
	private static int specialities$fishingLuck(final int original, final ServerLevel serverLevel,
			final ItemStack rod, final Entity fisher) {
		if (!(fisher instanceof ServerPlayer player)) {
			return original;
		}

		return original + Tuning.luckBonus(SkillManager.get(player).level(Skill.FISHING));
	}

	/** Fishing passive: +1 Lure at 50, +2 at 100 (each lure level = 5s wait reduction). */
	@ModifyReturnValue(method = "getFishingTimeReduction", at = @At("RETURN"))
	private static float specialities$fishingLure(final float original, final ServerLevel serverLevel,
			final ItemStack rod, final Entity fisher) {
		if (!(fisher instanceof ServerPlayer player)) {
			return original;
		}

		return original + 5.0F * Tuning.lureBonus(SkillManager.get(player).level(Skill.FISHING));
	}

	/** Acrobatics: extra fall-damage protection points on top of Feather Falling. */
	@ModifyReturnValue(method = "getDamageProtection", at = @At("RETURN"))
	private static float specialities$fallProtection(final float original, final ServerLevel serverLevel,
			final LivingEntity victim, final DamageSource source) {
		if (!(victim instanceof ServerPlayer player) || !source.is(DamageTypeTags.IS_FALL)) {
			return original;
		}

		return original + Tuning.acrobaticsProtectionPoints(SkillManager.get(player).level(Skill.ACROBATICS));
	}
}
