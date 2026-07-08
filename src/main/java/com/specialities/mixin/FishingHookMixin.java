package com.specialities.mixin;

import com.specialities.skills.Skill;
import com.specialities.skills.SkillManager;
import com.specialities.skills.Tuning;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.FishingHook;

/**
 * Fishing XP: awarded when reeling in with a fish on the hook (the same
 * condition vanilla uses to roll the fishing loot table).
 */
@Mixin(FishingHook.class)
public abstract class FishingHookMixin {
	@Shadow
	private int nibble;

	@Inject(method = "retrieve", at = @At("HEAD"))
	private void specialities$fishingXp(final CallbackInfoReturnable<Integer> cir) {
		FishingHook self = (FishingHook) (Object) this;

		if (this.nibble <= 0 || !(self.level() instanceof ServerLevel)) {
			return;
		}

		if (self.getPlayerOwner() instanceof ServerPlayer player && !player.isCreative()) {
			SkillManager.addXp(player, Skill.FISHING, Tuning.FISHING_XP_PER_CATCH);
		}
	}
}
