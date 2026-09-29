package com.specialities.mixin;

import com.specialities.ModTags;
import com.specialities.skills.Artisan;
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
import net.minecraft.world.inventory.FurnaceResultSlot;
import net.minecraft.world.item.ItemStack;

/**
 * Smithing smelting multicraft: withdrawing smelted metal from a furnace can
 * multiply it x2/x4/x8 per item (rarer for higher multipliers).
 *
 * <p>Hooked at HEAD of {@code checkTakeAchievements}, NOT {@code onTake} (GitHub issue #9).
 * {@code checkTakeAchievements} is the one call every withdrawal path funnels through with
 * the real stack, and {@code removeCount} is vanilla's own tally of how many items that
 * withdrawal took (the same number it pays furnace XP orbs on):
 *
 * <ul>
 * <li>click / drop-key / number-key swap: {@code remove(n)} adds to {@code removeCount},
 *     then {@code onTake(player, taken)} calls it with the taken stack;</li>
 * <li>SHIFT-click: {@code AbstractFurnaceMenu.quickMoveStack} first moves the stack into the
 *     inventory, then calls {@code onQuickCraft(copy, moved)} (which calls it with an
 *     undrained copy and {@code removeCount = moved}), and only THEN {@code onTake(player,
 *     drained)} — with the slot's own stack, already emptied by the move. So the old
 *     {@code onTake} hook saw an empty stack on every shift-click and never rolled; a partial
 *     shift-click rolled on the count LEFT BEHIND instead of the count taken.</li>
 * </ul>
 *
 * {@code removeCount > 0} makes it fire exactly once per withdrawal: the second call on the
 * shift-click path arrives after vanilla zeroed it. Same shape on all seven nodes — method,
 * descriptor and both fields are identical in every target jar (javap-checked, NeoForge's
 * patched copy included: its only addition is {@code firePlayerSmeltedEvent} at the tail).
 * Nothing here holds state across calls, so nothing can be left stuck by a throw.
 */
@Mixin(FurnaceResultSlot.class)
public abstract class FurnaceResultSlotMixin {
	@Shadow
	@Final
	private Player player;

	@Shadow
	private int removeCount;

	@Inject(method = "checkTakeAchievements", at = @At("HEAD"))
	private void specialities$smeltMulticraft(final ItemStack taken, final CallbackInfo ci) {
		int count = this.removeCount;

		if (count <= 0 || !(this.player instanceof ServerPlayer serverPlayer) || serverPlayer.isCreative()
				|| taken.isEmpty() || !taken.is(ModTags.SMELTED_METALS)) {
			return;
		}

		int level = SkillManager.get(serverPlayer).level(Skill.SMITHING);
		if (level <= 0) {
			return;
		}

		RandomSource random = serverPlayer.getRandom();
		int extra = 0;

		for (int i = 0; i < count; i++) {
			extra += Artisan.rollSmeltMultiplier(random, level) - 1;
		}

		int maxStack = Math.max(1, taken.getMaxStackSize());

		while (extra > 0) {
			int stack = Math.min(extra, maxStack);
			serverPlayer.getInventory().placeItemBackInInventory(new ItemStack(taken.getItem(), stack));
			extra -= stack;
		}
	}
}
