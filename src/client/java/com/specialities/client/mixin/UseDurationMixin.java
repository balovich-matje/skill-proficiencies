package com.specialities.client.mixin;

import com.specialities.skills.Skill;
import com.specialities.skills.SkillManager;
import com.specialities.skills.Tuning;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.numeric.UseDuration;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;

/**
 * The bow's pull animation is driven by the {@code minecraft:use_duration}
 * item model property (vanilla has no bow quick charge, so it never scales).
 * Scale the reported use time for bows so the visual draw matches the archery
 * skill's faster charge from {@code BowItemMixin}.
 */
@Mixin(UseDuration.class)
public abstract class UseDurationMixin {
	@ModifyReturnValue(method = "get", at = @At("RETURN"))
	private float specialities$fasterBowPull(final float original, final ItemStack itemStack,
			final ClientLevel level, final ItemOwner owner, final int seed) {
		if (original <= 0.0F || !(itemStack.getItem() instanceof BowItem)) {
			return original;
		}

		if (((UseDuration) (Object) this).remaining()) {
			return original;
		}

		LivingEntity entity = owner == null ? null : owner.asLivingEntity();
		if (!(entity instanceof Player player)) {
			return original;
		}

		int skillLevel = SkillManager.get(player).level(Skill.ARCHERY);
		if (skillLevel <= 0) {
			return original;
		}

		return original / Tuning.recoveryTimeMultiplier(skillLevel);
	}
}
