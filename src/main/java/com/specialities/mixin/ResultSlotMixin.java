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
 * of consumed materials, and resourcefulness returns some of them.
 *
 * <p>Hooked at HEAD of {@code checkTakeAchievements}, NOT {@code onTake} (GitHub issue #9).
 * On a SHIFT-click, {@code CraftingMenu}/{@code InventoryMenu.quickMoveStack} moves the result
 * into the inventory first and then calls {@code onTake(player, drained)} with the slot's own,
 * already-emptied stack — so the old {@code onTake} hook read an empty stack, failed
 * {@code isSmithingResult}, and every shift-click craft paid no smithing XP on every node.
 * {@code checkTakeAchievements} is reached on every take path with the real crafted stack
 * (shift-click: from {@code onQuickCraft(copy, moved)}, BEFORE {@code onTake}; click / drop /
 * swap: as the first statement of {@code onTake}), and in both cases the grid still holds the
 * about-to-be-consumed ingredients, because {@code onTake} consumes them after that call.
 *
 * <p>{@code removeCount > 0} makes it fire once per craft: vanilla zeroes the field at the end
 * of {@code checkTakeAchievements}, so the second call on the shift-click path (the one from
 * {@code onTake}) sees 0. Method, descriptor and all three fields are identical in every
 * target jar, NeoForge's and LexForge's patched copies included (their additions sit inside
 * the method body, after this HEAD inject). No state is held across calls.
 */
@Mixin(ResultSlot.class)
public abstract class ResultSlotMixin {
	@Shadow
	@Final
	private CraftingContainer craftSlots;

	@Shadow
	@Final
	private Player player;

	@Shadow
	private int removeCount;

	@Inject(method = "checkTakeAchievements", at = @At("HEAD"))
	private void specialities$smithing(final ItemStack crafted, final CallbackInfo ci) {
		if (this.removeCount <= 0 || !(this.player instanceof ServerPlayer serverPlayer) || serverPlayer.isCreative()
				|| crafted.isEmpty() || !Artisan.isSmithingResult(crafted)) {
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
		int returns = Artisan.rollSmithingReturns(random, level);

		for (int i = 0; i < returns; i++) {
			Item material = consumed.get(random.nextInt(consumed.size()));
			serverPlayer.getInventory().placeItemBackInInventory(new ItemStack(material));
		}
	}
}
