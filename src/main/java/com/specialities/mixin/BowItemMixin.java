package com.specialities.mixin;

import com.specialities.skills.Skill;
import com.specialities.skills.SkillManager;
import com.specialities.skills.Tuning;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;

/**
 * Archery draw speed for bows: scale the held time fed into the power curve,
 * so full draw is reached in half the time at level 100.
 */
@Mixin(BowItem.class)
public abstract class BowItemMixin {
	@WrapOperation(
			method = "releaseUsing",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/item/BowItem;getPowerForTime(I)F"))
	private float specialities$fasterDraw(final int timeHeld, final Operation<Float> original,
			@Local(argsOnly = true) final LivingEntity entity) {
		int effectiveTime = timeHeld;

		if (entity instanceof Player player) {
			int skillLevel = SkillManager.get(player).level(Skill.ARCHERY);

			if (skillLevel > 0) {
				effectiveTime = (int) (timeHeld / Tuning.recoveryTimeMultiplier(skillLevel));
			}
		}

		return original.call(effectiveTime);
	}
}
