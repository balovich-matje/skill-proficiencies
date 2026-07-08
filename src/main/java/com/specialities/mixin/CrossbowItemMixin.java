package com.specialities.mixin;

import com.specialities.skills.Skill;
import com.specialities.skills.SkillManager;
import com.specialities.skills.Tuning;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;

/**
 * Archery draw speed for crossbows: shorten the charge duration on top of any
 * quick charge enchantment (which is applied inside the original method).
 */
@Mixin(CrossbowItem.class)
public abstract class CrossbowItemMixin {
	@ModifyReturnValue(method = "getChargeDuration", at = @At("RETURN"))
	private static int specialities$fasterCharge(final int original, final ItemStack crossbow,
			final LivingEntity user) {
		if (!(user instanceof Player player)) {
			return original;
		}

		int skillLevel = SkillManager.get(player).level(Skill.ARCHERY);
		if (skillLevel <= 0) {
			return original;
		}

		return Math.max(1, (int) (original * Tuning.recoveryTimeMultiplier(skillLevel)));
	}
}
