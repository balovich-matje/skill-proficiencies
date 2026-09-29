package com.specialities.mixin;

import java.util.ArrayList;
import java.util.List;

import com.specialities.skills.Artisan;
import com.specialities.skills.MaterialValues;
import com.specialities.skills.Skill;
import com.specialities.skills.SkillManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Smithing: crafting tools/weapons/armor grants XP proportional to the value
 * of consumed materials, and resourcefulness returns some of them. Runs at
 * HEAD so the crafting grid still holds the about-to-be-consumed ingredients.
 */
@Mixin(ResultSlot.class)
public abstract class ResultSlotMixin {
	@Shadow
	@Final
	private CraftingContainer craftSlots;

	@Inject(method = "onTake", at = @At("HEAD"))
	private void specialities$smithing(final Player player, final ItemStack carried, final CallbackInfo ci) {
		if (!(player instanceof ServerPlayer serverPlayer) || serverPlayer.isCreative()
				|| !Artisan.isSmithingResult(carried)) {
			return;
		}

		List<Item> consumed = new ArrayList<>();
		int xp = 0;

		for (int i = 0; i < this.craftSlots.getContainerSize(); i++) {
			ItemStack ingredient = this.craftSlots.getItem(i);
			if (!ingredient.isEmpty()) {
				consumed.add(ingredient.getItem());
				xp += MaterialValues.value(ingredient);
			}
		}

		if (consumed.isEmpty()) {
			return;
		}

		SkillManager.addXp(serverPlayer, Skill.SMITHING, xp);

		int level = SkillManager.get(serverPlayer).level(Skill.SMITHING);
		RandomSource random = serverPlayer.getRandom();
		int returns = Artisan.rollSmithingReturns(random, level, consumed.size());

		for (int i = 0; i < returns; i++) {
			Item material = consumed.get(random.nextInt(consumed.size()));
			serverPlayer.getInventory().placeItemBackInInventory(new ItemStack(material));
		}
	}
}
