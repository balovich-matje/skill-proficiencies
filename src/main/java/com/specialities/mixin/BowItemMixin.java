package com.specialities.mixin;

import com.specialities.skills.Skill;
import com.specialities.skills.SkillManager;
import com.specialities.skills.Tuning;
//? if >=1.21 {
//?} else {
/*import com.specialities.platform.SkillStore;
*///?}
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

//? if >=1.21 {
//?} else {
/*import net.minecraft.world.entity.Entity;
*///?}
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
//? if >=1.21 {
//?} else {
/*import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
*///?}

/**
 * Archery draw speed for bows: scale the held time fed into the power curve,
 * so full draw is reached in half the time at level 100.
 *
 * <p>Below 1.21 this class also stamps the bow onto the arrow it fires, because
 * that version's {@code AbstractArrow} cannot be asked what fired it (design
 * R-04).
 */
@Mixin(BowItem.class)
public abstract class BowItemMixin {
	@WrapOperation(
			method = "releaseUsing",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/item/BowItem;getPowerForTime(I)F"))
	private float specialities$fasterDraw(final int timeHeld, final Operation<Float> original,
			@Local(argsOnly = true) final LivingEntity entity) {
		int effectiveTime = timeHeld;

		if (entity instanceof Player player) {
			int skillLevel = SkillManager.get(player).level(Skill.ARCHERY);

			if (skillLevel > 0) {
				effectiveTime = (int) (timeHeld / Tuning.recoveryTimeMultiplier(skillLevel));
			}
		}

		return original.call(effectiveTime);
	}

	// R-04's bow half. `releaseUsing` spawns its arrow with exactly ONE
	// `Level.addFreshEntity(Entity)Z` call (counted in the 1.20.1 bytecode, offset 355), so
	// wrapping it is an unambiguous "the projectile has just been built, here is the bow
	// that built it". The stamp is read back by `SkillCategories.weaponItem`.
	//
	// Not guarded on `instanceof AbstractArrow`: nothing else reaches this call site here,
	// the store takes any Entity, and the guard would need the version-forked AbstractArrow
	// import for no behavioural gain.
	//? if >=1.21 {
	//?} else {
	/*@WrapOperation(
			method = "releaseUsing",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
	private boolean specialities$stampBowWeapon(final Level level, final Entity projectile,
			final Operation<Boolean> original, @Local(argsOnly = true) final ItemStack bow) {
		SkillStore.INSTANCE.setFiringWeapon(projectile, bow);
		return original.call(level, projectile);
	}
	*///?}
}
