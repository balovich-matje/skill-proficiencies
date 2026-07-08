package com.specialities.items;

import com.specialities.skills.Skill;
import com.specialities.skills.SkillManager;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
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
	public InteractionResult use(final Level level, final Player player, final InteractionHand hand) {
		if (player instanceof ServerPlayer serverPlayer) {
			SkillManager.addLevels(serverPlayer, this.skill, this.levels);

			if (!serverPlayer.getAbilities().instabuild) {
				serverPlayer.getItemInHand(hand).shrink(1);
			}
		}

		return InteractionResult.SUCCESS;
	}
}
