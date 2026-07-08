package com.specialities.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
	@Accessor("leftPos")
	int specialities$getLeftPos();

	@Accessor("topPos")
	int specialities$getTopPos();

	@Accessor("imageWidth")
	int specialities$getImageWidth();
}
