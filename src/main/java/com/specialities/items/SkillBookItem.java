package com.specialities.items;

import com.specialities.skills.Skill;
import com.specialities.skills.SkillManager;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
// `Item.use` returns a bare InteractionResult from 1.21.2 up; below that it is
// InteractionResultHolder<ItemStack> (1.21.1 mojmap: `InteractionResultHolder
// success(Object) -> a`).
//? if >=1.21.2 {
import net.minecraft.world.InteractionResult;
//?} else {
/*import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.ItemStack;
*///?}
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * Testing item: right-click to gain a fixed number of levels in one skill.
 */
public class SkillBookItem extends Item {
	private final Skill skill;
	private final int levels;

	public SkillBookItem(final Skill skill, final int levels, final Item.Properties properties) {
		super(properties);
		this.skill = skill;
		this.levels = levels;
	}

	@Override
	//? if >=1.21.2 {
	public InteractionResult use(final Level level, final Player player, final InteractionHand hand) {
	//?} else {
	/*public InteractionResultHolder<ItemStack> use(final Level level, final Player player, final InteractionHand hand) {
	*///?}
		if (player instanceof ServerPlayer serverPlayer) {
			SkillManager.addLevels(serverPlayer, this.skill, this.levels);

			if (!serverPlayer.getAbilities().instabuild) {
				serverPlayer.getItemInHand(hand).shrink(1);
			}
		}

		//? if >=1.21.2 {
		return InteractionResult.SUCCESS;
		//?} else {
		/*return InteractionResultHolder.success(player.getItemInHand(hand));
		*///?}
	}
}
