package com.specialities.mixin;

import com.specialities.skills.Skill;
import com.specialities.skills.SkillManager;
import com.specialities.skills.Tuning;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.level.ServerPlayer;
// `jumpFromGround()V` is declared on ServerPlayer only from 1.21.11 up. On 1.21.1 the
// ServerPlayer mojmap entry has no such method and it lives on Player instead
// (`1596:1604:void jumpFromGround() -> ff`), where it runs on BOTH logical sides — hence the
// retarget plus a server-side check in place of the cast. LivingEntity.aiStep calls it on the
// server too, so the XP still fires.
//? if >=1.21.11 {
//?} else {
/*import net.minecraft.world.entity.player.Player;
*///?}

/**
 * Athletics XP for jumping. Vanilla charges a sprint jump 4x the exhaustion of
 * a standing jump, and the XP follows the same ratio — hunger is the natural
 * limiter on how fast this can be trained.
 */
//? if >=1.21.11 {
@Mixin(ServerPlayer.class)
//?} else {
/*@Mixin(Player.class)
*///?}
public abstract class ServerPlayerMixin {
	@Inject(method = "jumpFromGround", at = @At("TAIL"))
	private void specialities$athleticsJump(final CallbackInfo ci) {
		//? if >=1.21.11 {
		ServerPlayer self = (ServerPlayer) (Object) this;
		//?} else {
		/*if (!((Object) this instanceof ServerPlayer)) {
			return;
		}

		ServerPlayer self = (ServerPlayer) (Object) this;
		*///?}

		if (self.isCreative()) {
			return;
		}

		SkillManager.addXp(self, Skill.ATHLETICS,
				self.isSprinting() ? Tuning.SPRINT_JUMP_XP : Tuning.JUMP_XP);
	}
}
