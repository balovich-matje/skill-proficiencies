package com.specialities.client.mixin;

import com.specialities.skills.AthleticsTicker;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * The athletics sprint-speed bonus must not zoom the FOV: the FOV modifier is
 * derived from the movement speed attribute, so we divide our multiplier back
 * out of the attribute value it reads.
 */
@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {
	@ModifyExpressionValue(
			method = "getFieldOfViewModifier",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/player/AbstractClientPlayer;getAttributeValue(Lnet/minecraft/core/Holder;)D"))
	private double specialities$hideSprintBonusFromFov(final double original) {
		AbstractClientPlayer self = (AbstractClientPlayer) (Object) this;
		AttributeInstance speed = self.getAttribute(Attributes.MOVEMENT_SPEED);

		if (speed == null) {
			return original;
		}

		AttributeModifier modifier = speed.getModifier(AthleticsTicker.sprintModifierId());
		if (modifier == null || modifier.amount() <= 0) {
			return original;
		}

		return original / (1.0 + modifier.amount());
	}
}
