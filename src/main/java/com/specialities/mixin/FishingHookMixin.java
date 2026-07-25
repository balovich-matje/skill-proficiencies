package com.specialities.mixin;

import com.specialities.skills.Skill;
import com.specialities.skills.SkillManager;
import com.specialities.skills.Tuning;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
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
 *
 * <p>Below 1.21 this class also carries the two fishing <em>enchantment</em>
 * passives, which live in {@code EnchantmentHelperMixin} everywhere else — see
 * the block at the bottom (design R-05).
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

	// ---------------------------------------------------------------------------
	// R-05, the fishing half. Below 1.21 the two EnchantmentHelper methods the passives
	// hook lose their fisher parameter — `getFishingLuckBonus(ItemStack)I` and
	// `getFishingSpeedBonus(ItemStack)I`, the second also renamed and int-valued — so
	// there is no way to know whose rod it is from inside them. Re-rooted here, where the
	// hook owns both values and knows its player.
	//
	// The arithmetic is EXACTLY equivalent, not merely similar:
	//   * luck: modern adds `luckBonus(level)` to the int `getFishingLuckBonus` returns and
	//     vanilla feeds that to `withLuck`. Here the same value is added to the `luck`
	//     field at its single read inside `retrieve`, which is the same `withLuck` call.
	//   * lure: modern adds `5.0F * lureBonus(level)` SECONDS to `getFishingTimeReduction`.
	//     Here `lureBonus(level)` is added to `lureSpeed` at its single read inside
	//     `catchingFish`, where vanilla computes `timeUntilLured -= lureSpeed * 20 * 5` —
	//     100 ticks, i.e. 5 seconds, per lure level. Same number of ticks.
	// Both fields are read exactly ONCE in the respective method (`javap -c` of the 1.20.1
	// class: `luck` once in `retrieve`, `lureSpeed` once in `catchingFish`), so neither
	// injection point needs an ordinal.
	//? if >=1.21 {
	//?} else {
	/*// Fishing passive: +1 Luck of the Sea per 20 levels (0..5), additive with the enchant.
	@ModifyExpressionValue(
			method = "retrieve",
			at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/projectile/FishingHook;luck:I"))
	private int specialities$legacyFishingLuck(final int original) {
		FishingHook self = (FishingHook) (Object) this;

		if (!(self.getPlayerOwner() instanceof ServerPlayer player)) {
			return original;
		}

		return original + Tuning.luckBonus(SkillManager.get(player).level(Skill.FISHING));
	}

	// Fishing passive: +1 Lure at 50, +2 at 100 (each lure level = 5s wait reduction).
	@ModifyExpressionValue(
			method = "catchingFish",
			at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/projectile/FishingHook;lureSpeed:I"))
	private int specialities$legacyFishingLure(final int original) {
		FishingHook self = (FishingHook) (Object) this;

		if (!(self.getPlayerOwner() instanceof ServerPlayer player)) {
			return original;
		}

		return original + Tuning.lureBonus(SkillManager.get(player).level(Skill.FISHING));
	}
	*///?}
}
