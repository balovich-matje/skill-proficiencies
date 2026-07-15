package com.specialities.mixin;

import com.specialities.skills.Skill;
import com.specialities.skills.SkillManager;
import com.specialities.skills.Tuning;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.level.ServerPlayer;

/**
 * Athletics XP for jumping. Vanilla charges a sprint jump 4x the exhaustion of
 * a standing jump, and the XP follows the same ratio — hunger is the natural
 * limiter on how fast this can be trained.
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {
	@Inject(method = "jumpFromGround", at = @At("TAIL"))
	private void specialities$athleticsJump(final CallbackInfo ci) {
		ServerPlayer self = (ServerPlayer) (Object) this;

		if (self.isCreative()) {
			return;
		}

		SkillManager.addXp(self, Skill.ATHLETICS,
				self.isSprinting() ? Tuning.SPRINT_JUMP_XP : Tuning.JUMP_XP);
	}
}
