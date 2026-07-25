package com.specialities.mixin;

import com.specialities.platform.SkillStore;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;

/**
 * Alchemy attribution: remember the last player who opened a brewing stand so
 * brews that finish later can credit their skill.
 */
@Mixin(BrewingStandMenu.class)
public abstract class BrewingStandMenuMixin {
	@Inject(
			method = "<init>(ILnet/minecraft/world/entity/player/Inventory;"
					+ "Lnet/minecraft/world/Container;Lnet/minecraft/world/inventory/ContainerData;)V",
			at = @At("TAIL"))
	private void specialities$rememberOwner(final int containerId, final Inventory inventory,
			final Container brewingStand, final ContainerData data, final CallbackInfo ci) {
		if (brewingStand instanceof BrewingStandBlockEntity stand && inventory.player instanceof ServerPlayer player) {
			SkillStore.INSTANCE.setBrewingOwner(stand, player.getStringUUID());
		}
	}
}
