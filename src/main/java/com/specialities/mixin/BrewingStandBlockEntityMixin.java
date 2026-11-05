package com.specialities.mixin;

import com.specialities.skills.Artisan;
import com.specialities.skills.Skill;
import com.specialities.skills.SkillManager;
import com.specialities.skills.Tuning;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.core.BlockPos;
//? if >=26.3 {
/*import net.minecraft.server.level.ServerLevel;
*///?}
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;

/**
 * Alchemy: each brew cycle grants XP to the stand's last user, and
 * resourcefulness gives a chance that the ingredient is not consumed.
 */
@Mixin(BrewingStandBlockEntity.class)
public abstract class BrewingStandBlockEntityMixin {
	@WrapOperation(
			method = "doBrew",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;shrink(I)V"))
	private static void specialities$alchemy(final ItemStack ingredient, final int amount,
			final Operation<Void> original,
			//? if >=26.3 {
			/*@Local(argsOnly = true) final ServerLevel level, @Local(argsOnly = true) final BlockPos pos) {
			*///?} else {
			@Local(argsOnly = true) final Level level, @Local(argsOnly = true) final BlockPos pos) {
			//?}
		ServerPlayer owner = Artisan.brewingOwner(level, pos);

		if (owner != null && !owner.isCreative()) {
			SkillManager.addXp(owner, Skill.ALCHEMY, Tuning.ALCHEMY_BREW_XP);

			int skillLevel = SkillManager.get(owner).level(Skill.ALCHEMY);
			if (owner.getRandom().nextFloat() < Tuning.alchemyReturnChance(skillLevel)) {
				return; // Ingredient survives the brew.
			}
		}

		original.call(ingredient, amount);
	}
}
