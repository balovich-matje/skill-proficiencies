package com.specialities.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.entity.projectile.arrow.AbstractArrow;

@Mixin(AbstractArrow.class)
public interface AbstractArrowAccessor {
	@Accessor("baseDamage")
	double specialities$getBaseDamage();
}
