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
	// Attributes are `Holder<Attribute>`-typed from 1.21 up and bare `Attribute` below, so
	// the `getAttributeValue` overload this wraps changes descriptor. The OWNER does not:
	// `javap -c` of 1.20.1's `AbstractClientPlayer.getFieldOfViewModifier()F` shows
	// `invokevirtual … // Method b:(Lbhb;)D` with no owner prefix — i.e. the class itself,
	// not LivingEntity — which is the CLAUDE.md `getAttributeValue` owner gotcha, checked
	// rather than assumed, on this node as on 1.21.11. It occurs exactly ONCE in that
	// method, so there is still no ordinal.
	//? if >=1.21 {
	@ModifyExpressionValue(
			method = "getFieldOfViewModifier",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/player/AbstractClientPlayer;getAttributeValue(Lnet/minecraft/core/Holder;)D"))
	//?} else {
	/*@ModifyExpressionValue(
			method = "getFieldOfViewModifier",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/player/AbstractClientPlayer;"
							+ "getAttributeValue(Lnet/minecraft/world/entity/ai/attributes/Attribute;)D"))
	*///?}
	private double specialities$hideSprintBonusFromFov(final double original) {
		AbstractClientPlayer self = (AbstractClientPlayer) (Object) this;
		AttributeInstance speed = self.getAttribute(Attributes.MOVEMENT_SPEED);

		if (speed == null) {
			return original;
		}

		// This is the other half of design §3.4's cross-set coupling: the argument type
		// here is whatever `AthleticsTicker.sprintModifierId()` returns — a UUID below 1.21,
		// a ResourceLocation above — so the call site itself needs no fork, and the two files
		// land in one commit. Only the accessor name below moves (`amount()` was
		// `getAmount()` before the record rewrite); the division is the same arithmetic.
		AttributeModifier modifier = speed.getModifier(AthleticsTicker.sprintModifierId());
		//? if >=1.21 {
		if (modifier == null || modifier.amount() <= 0) {
			return original;
		}

		return original / (1.0 + modifier.amount());
		//?} else {
		/*if (modifier == null || modifier.getAmount() <= 0) {
			return original;
		}

		return original / (1.0 + modifier.getAmount());
		*///?}
	}
}
