package com.specialities.mixin;

import com.specialities.skills.Artisan;
import com.specialities.skills.Skill;
import com.specialities.skills.SkillManager;
import com.specialities.skills.Tuning;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Three spellings of one package, all three now exercised by a registered node:
//   26.2                -> advancements.triggers
//   26.1 and 1.21.11    -> advancements.criterion
//   1.21.1 and below    -> advancements.critereon  (sic — the historical typo)
// 1.21.1 mojmap: `net.minecraft.advancements.critereon.EnchantedItemTrigger -> bp`, with
// `EnchantedItemTrigger$TriggerInstance -> bp$a`. `trigger(ServerPlayer,ItemStack,int)` is
// AS-IS, so only the import forks.
//? if >=26.2 {
import net.minecraft.advancements.triggers.EnchantedItemTrigger;
//?} elif >=1.21.11 {
/*import net.minecraft.advancements.criterion.EnchantedItemTrigger;
*///?} else {
/*import net.minecraft.advancements.critereon.EnchantedItemTrigger;
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
