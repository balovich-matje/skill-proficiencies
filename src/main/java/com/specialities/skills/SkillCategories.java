package com.specialities.skills;

import com.specialities.MeleeSwing;
import com.specialities.ModTags;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockItemTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Maps blocks/tools/attacks to skills and XP amounts.
 */
public final class SkillCategories {
	private SkillCategories() {
	}

	/** Which skill speeds up breaking this block (proficiency multiplier). */
	public static @Nullable Skill breakSpeedSkill(final BlockState state) {
		if (state.is(BlockTags.MINEABLE_WITH_PICKAXE)) {
			return Skill.MINING;
		}

		if (state.is(BlockTags.MINEABLE_WITH_AXE)) {
			return Skill.WOODCUTTING;
		}

		if (state.is(BlockTags.MINEABLE_WITH_HOE)) {
			return Skill.HARVESTING;
		}

		if (state.is(BlockTags.MINEABLE_WITH_SHOVEL)) {
			return Skill.EXCAVATION;
		}

		return null;
	}

	/** The skill a held tool/weapon belongs to, e.g. for the HUD bar display. */
	public static @Nullable Skill toolSkill(final ItemStack stack) {
		if (stack.is(Items.FISHING_ROD)) {
			return Skill.FISHING;
		}

		if (stack.is(Items.SHIELD)) {
			return Skill.DEFENCE;
		}

		if (stack.is(ItemTags.PICKAXES)) {
			return Skill.MINING;
		}

		if (stack.is(ItemTags.AXES)) {
			return Skill.WOODCUTTING;
		}

		if (stack.is(ItemTags.HOES)) {
			return Skill.HARVESTING;
		}

		if (stack.is(ItemTags.SHOVELS)) {
			return Skill.EXCAVATION;
		}

		if (stack.is(ModTags.RANGED_WEAPONS)) {
			return Skill.ARCHERY;
		}

		if (stack.is(ModTags.MELEE_WEAPONS)) {
			return Skill.ARMS_MASTERY;
		}

		return null;
	}

	/** Whether the held item is the matching tool for a skill's proficiency bonus. */
	public static boolean toolMatches(final Skill skill, final ItemStack stack) {
		return switch (skill) {
			case MINING -> stack.is(ItemTags.PICKAXES);
			case WOODCUTTING -> stack.is(ItemTags.AXES);
			case HARVESTING -> stack.is(ItemTags.HOES);
			case EXCAVATION -> stack.is(ItemTags.SHOVELS);
			case COMBAT -> stack.is(ModTags.WEAPONS);
			case ARMS_MASTERY -> stack.is(ModTags.MELEE_WEAPONS);
			case ARCHERY -> stack.is(ModTags.RANGED_WEAPONS);
			case FISHING -> stack.is(Items.FISHING_ROD);
			case DEFENCE -> stack.is(Items.SHIELD);
			case ACROBATICS, ATHLETICS, SNEAKING, SMITHING, ALCHEMY, ENCHANTING -> false;
		};
	}

	/**
	 * Which skill's passive fortune bonus applies to this block's loot.
	 * Logs are handled separately (vanilla log loot has no fortune function).
	 */
	public static @Nullable Skill fortuneSkill(final BlockState state) {
		if (isCropLike(state)) {
			return Skill.HARVESTING;
		}

		if (state.is(BlockTags.MINEABLE_WITH_PICKAXE)) {
			return Skill.MINING;
		}

		if (state.is(BlockTags.MINEABLE_WITH_SHOVEL)) {
			return Skill.EXCAVATION;
		}

		return null;
	}

	public static boolean isCropLike(final BlockState state) {
		Block block = state.getBlock();
		return block instanceof CropBlock
				|| block instanceof NetherWartBlock
				|| state.is(BlockTags.CROPS)
				|| state.is(Blocks.MELON)
				|| state.is(Blocks.PUMPKIN);
	}

	private static boolean isMatureCrop(final BlockState state) {
		if (state.getBlock() instanceof CropBlock crop) {
			return crop.isMaxAge(state);
		}

		if (state.getBlock() instanceof NetherWartBlock) {
			return state.getValue(NetherWartBlock.AGE) >= NetherWartBlock.MAX_AGE;
		}

		return state.is(Blocks.MELON) || state.is(Blocks.PUMPKIN);
	}

	/** XP awarded for breaking a block, or 0 if no skill applies. Assumes a survival-mode server player. */
	public static int blockBreakXp(final ServerPlayer player, final BlockState state) {
		if (isCropLike(state)) {
			// Only fully grown crops grant XP, otherwise re-planting spam would farm levels.
			return isMatureCrop(state) ? 15 : 0;
		}

		if (state.is(BlockTags.LOGS)) {
			return 10;
		}

		if (state.is(BlockTags.MINEABLE_WITH_PICKAXE) && player.hasCorrectToolForDrops(state)) {
			return miningXp(state);
		}

		// Dirt/sand/etc. drop without any tool, so require an actual shovel for XP.
		if (state.is(BlockTags.MINEABLE_WITH_SHOVEL) && toolMatches(Skill.EXCAVATION, player.getMainHandItem())) {
			return excavationXp(state);
		}

		return 0;
	}

	/** Which skill the block-break XP goes to; must mirror {@link #blockBreakXp}. */
	public static @Nullable Skill blockBreakSkill(final BlockState state) {
		if (isCropLike(state)) {
			return Skill.HARVESTING;
		}

		if (state.is(BlockTags.LOGS)) {
			return Skill.WOODCUTTING;
		}

		if (state.is(BlockTags.MINEABLE_WITH_PICKAXE)) {
			return Skill.MINING;
		}

		if (state.is(BlockTags.MINEABLE_WITH_SHOVEL)) {
			return Skill.EXCAVATION;
		}

		return null;
	}

	private static int excavationXp(final BlockState state) {
		if (state.is(Blocks.CLAY) || state.is(Blocks.SOUL_SAND) || state.is(Blocks.SOUL_SOIL)) {
			return 5;
		}

		if (state.is(Blocks.GRAVEL) || state.is(Blocks.SUSPICIOUS_SAND) || state.is(Blocks.SUSPICIOUS_GRAVEL)) {
			return 3;
		}

		return 2;
	}

	private static int miningXp(final BlockState state) {
		if (state.is(Blocks.ANCIENT_DEBRIS)) {
			return 75;
		}

		if (state.is(BlockItemTags.DIAMOND_ORES.block()) || state.is(BlockItemTags.EMERALD_ORES.block())) {
			return 50;
		}

		if (state.is(BlockTags.GOLD_ORES)) {
			return 20;
		}

		if (state.is(BlockTags.IRON_ORES) || state.is(BlockItemTags.LAPIS_ORES.block())) {
			return 15;
		}

		if (state.is(BlockItemTags.REDSTONE_ORES.block())) {
			return 12;
		}

		if (state.is(BlockItemTags.COAL_ORES.block()) || state.is(Blocks.NETHER_QUARTZ_ORE)) {
			return 10;
		}

		if (state.is(BlockTags.COPPER_ORES)) {
			return 8;
		}

		// Plain stone & friends.
		return 1;
	}

	/** Whether this damage counts as a weapon attack for the combat skill. */
	public static boolean isWeaponAttack(final ServerPlayer attacker, final DamageSource source) {
		return specializationSkill(attacker, source) != null;
	}

	/**
	 * Which combat specialization this damage trains, or null if it isn't a
	 * weapon attack at all. Arrows fired from bows/crossbows train archery;
	 * melee hits and thrown weapons (tridents, spears) train arms mastery.
	 */
	public static @Nullable Skill specializationSkill(final ServerPlayer attacker, final DamageSource source) {
		if (source.getDirectEntity() instanceof AbstractArrow arrow) {
			ItemStack weapon = arrow.getWeaponItem();

			if (weapon != null && weapon.is(ModTags.RANGED_WEAPONS)) {
				return Skill.ARCHERY;
			}

			return Skill.ARMS_MASTERY;
		}

		if (source.getDirectEntity() == attacker && attacker.getMainHandItem().is(ModTags.MELEE_WEAPONS)) {
			return Skill.ARMS_MASTERY;
		}

		return null;
	}

	/**
	 * Whether this damage is the attacker landing an actual melee swing with a
	 * melee weapon — the strict test, for bonuses that must not ride on
	 * damage-over-time, thorns or any other passive that borrows the player as
	 * its damage source. {@link MeleeSwing} explains why the damage source
	 * alone cannot answer this; the weapon-in-hand check is still the thing
	 * that keeps a bare fist or a pickaxe out.
	 */
	public static boolean isMeleeSwing(final ServerPlayer attacker, final DamageSource source) {
		return MeleeSwing.isSwinging(attacker)
				&& source.getDirectEntity() == attacker
				&& attacker.getMainHandItem().is(ModTags.MELEE_WEAPONS);
	}

	/**
	 * Whether this damage is an arrow the attacker fired from a bow or a
	 * crossbow. Reuses the mod's existing definition of a ranged weapon
	 * ({@code specialities:ranged_weapons}, the same tag archery trains off),
	 * so a thrown trident, a snowball or a splash potion is not ranged.
	 */
	public static boolean isRangedWeaponShot(final ServerPlayer attacker, final DamageSource source) {
		if (!(source.getDirectEntity() instanceof AbstractArrow arrow) || arrow.getOwner() != attacker) {
			return false;
		}

		ItemStack weapon = arrow.getWeaponItem();
		return weapon != null && weapon.is(ModTags.RANGED_WEAPONS);
	}
}
