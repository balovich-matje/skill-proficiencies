package com.specialities.mixin;

import com.specialities.skills.Skill;
import com.specialities.skills.SkillManager;
import com.specialities.skills.Tuning;
//? if >=1.21 {
//?} else {
/*import com.specialities.platform.SkillStore;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
*///?}
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.Mixin;
//? if >=1.21 {
//?} else {
/*import org.spongepowered.asm.mixin.Unique;
*///?}
import org.spongepowered.asm.mixin.injection.At;

//? if >=1.21 {
//?} else {
/*import net.minecraft.world.entity.Entity;
*///?}
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
//? if >=1.21 {
//?} else {
/*import net.minecraft.world.level.Level;
*///?}

/**
 * Archery draw speed for crossbows: shorten the charge duration on top of any
 * quick charge enchantment (which is applied inside the original method).
 *
 * <p>Below 1.21 this class also stamps the crossbow onto the projectile it fires,
 * because that version's {@code AbstractArrow} cannot be asked what fired it
 * (design R-04).
 */
@Mixin(CrossbowItem.class)
public abstract class CrossbowItemMixin {
	// `getChargeDuration` loses its `LivingEntity user` parameter below 1.21 — it is
	// `getChargeDuration(ItemStack)I` there — so the return value cannot be scaled per
	// player from inside it (design §3.3: "1.20.1 loses per-player charge; needs its own
	// hook"). This method therefore stops being the injector on that node and becomes the
	// shared implementation the injector below delegates to (conventions §5a), which is the
	// same shape PlayerMixin's sweeping edge uses and keeps the four nodes above 1.21 on the
	// annotation, body and bytecode they already had.
	//? if >=1.21 {
	@ModifyReturnValue(method = "getChargeDuration", at = @At("RETURN"))
	//?} else {
	/*@Unique
	*///?}
	private static int specialities$fasterCharge(final int original, final ItemStack crossbow,
			final LivingEntity user) {
		if (!(user instanceof Player player)) {
			return original;
		}

		int skillLevel = SkillManager.get(player).level(Skill.ARCHERY);
		if (skillLevel <= 0) {
			return original;
		}

		return Math.max(1, (int) (original * Tuning.recoveryTimeMultiplier(skillLevel)));
	}

	// The 1.20.1 injector: the ONE call site of `getChargeDuration` that knows the user.
	// `onUseTick(Level, LivingEntity, ItemStack, int)` calls it exactly once (counted in the
	// 1.20.1 bytecode) to compute the charge fraction
	// `(useDuration - remainingUseTicks) / chargeDuration`, and shrinking that divisor is
	// what makes the crossbow reach `isCharged` early — precisely the bonus.
	//
	// `getUseDuration(ItemStack)` divides by it too and is deliberately NOT hooked: it has
	// no user either, and it is only the maximum hold time, so leaving it at vanilla makes
	// the item holdable slightly longer than it needs to be and nothing else. The
	// client-side `pull` model property is likewise unscaled there — the charge ANIMATION
	// runs at vanilla speed while the charge itself completes early, the same cosmetic gap
	// `UseDurationMixin` closes for bows only above 1.21.4.
	//? if >=1.21 {
	//?} else {
	/*@ModifyExpressionValue(
			method = "onUseTick",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/item/CrossbowItem;"
							+ "getChargeDuration(Lnet/minecraft/world/item/ItemStack;)I"))
	private int specialities$fasterChargeLegacy(final int original,
			@Local(argsOnly = true) final ItemStack crossbow,
			@Local(argsOnly = true) final LivingEntity user) {
		return specialities$fasterCharge(original, crossbow, user);
	}

	*///?}
	// R-04's crossbow half; see BowItemMixin for the shape and the reasoning.
	// `shootProjectile` is `private static` here and takes two ItemStacks — the crossbow at
	// arg 3 and the ammo at arg 4 — so the sugar needs `ordinal = 0` to pick the crossbow.
	// One `addFreshEntity` call in the method, counted in the bytecode.
	//? if >=1.21 {
	//?} else {
	/*@WrapOperation(
			method = "shootProjectile",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
	private static boolean specialities$stampCrossbowWeapon(final Level level, final Entity projectile,
			final Operation<Boolean> original, @Local(argsOnly = true, ordinal = 0) final ItemStack crossbow) {
		SkillStore.INSTANCE.setFiringWeapon(projectile, crossbow);
		return original.call(level, projectile);
	}
	*///?}
}
