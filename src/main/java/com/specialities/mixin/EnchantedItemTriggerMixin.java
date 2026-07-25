package com.specialities.mixin;

import com.specialities.skills.Artisan;
import com.specialities.skills.Skill;
import com.specialities.skills.SkillManager;
import com.specialities.skills.Tuning;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// 26.2 renamed the package `advancements.criterion` -> `advancements.triggers`.
// 26.1 and 1.21.11 both spell it `advancements.criterion`, so the else branch serves both
// (verified: net.minecraft.advancements.criterion.EnchantedItemTrigger in the 1.21.11 mojmap).
// Below 1.21.11 it becomes `advancements.critereon` (sic); that third branch lands with the
// 1.21.1 node, not with 1.21.11.
//? if >=26.2 {
import net.minecraft.advancements.triggers.EnchantedItemTrigger;
//?} else {
/*import net.minecraft.advancements.criterion.EnchantedItemTrigger;
*///?}
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Enchanting XP + luck. This criterion fires exactly once per enchanting-table
 * use, after the enchantments are applied — a stable hook with the final item.
 */
@Mixin(EnchantedItemTrigger.class)
public abstract class EnchantedItemTriggerMixin {
	@Inject(method = "trigger", at = @At("HEAD"))
	private void specialities$enchanting(final ServerPlayer player, final ItemStack itemStack, final int levels,
			final CallbackInfo ci) {
		if (player.isCreative()) {
			return;
		}

		SkillManager.addXp(player, Skill.ENCHANTING, levels * Tuning.ENCHANT_XP_PER_TIER);

		int skillLevel = SkillManager.get(player).level(Skill.ENCHANTING);
		if (skillLevel > 0 && player.getRandom().nextFloat() < Tuning.enchantLuckChance(skillLevel)) {
			Artisan.applyEnchantLuck(player, itemStack);
		}
	}
}
