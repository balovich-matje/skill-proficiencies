package com.specialities.skills;

import com.specialities.MeleeSwing;
import com.specialities.ModTags;
//? if >=1.21 {
//?} else {
/*import com.specialities.mixin.AbstractArrowAccessor;
import com.specialities.platform.SkillStore;
*///?}

import net.minecraft.server.level.ServerPlayer;
// 26.2 moved the ore/log tags to BlockItemTags (paired block+item views);
// BlockTags kept only GOLD/IRON/COPPER_ORES, LOGS, CROPS and MINEABLE_WITH_*.
//? if >=26.2 {
import net.minecraft.tags.BlockItemTags;
//?}
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.damagesource.DamageSource;
// Only `passiveLootingBonus` names Entity, and only below 1.21.
//? if >=1.21 {
//?} else {
/*import net.minecraft.world.entity.Entity;
*///?}
// The arrow classes moved into a `.arrow` subpackage at 1.21.11; below that they sit
// directly in `...entity.projectile` (1.21.1 mojmap: `net.minecraft.world.entity
// .projectile.AbstractArrow -> cnd`, `...projectile.Arrow -> cnf`). This boundary cuts
// across three files (SkillCategories + AbstractArrowMixin + AbstractArrowAccessor) —
// they must always fork together.
//? if >=1.21.11 {
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
//?} else {
/*import net.minecraft.world.entity.projectile.AbstractArrow;
*///?}
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.state.BlockState;
// jspecify is one of the game's OWN libraries only from 1.21.11 up (conventions
// §5e-bis); below that it is absent and org.jetbrains:annotations 26.0.2 (on the
// compile classpath via fabric-loader) supplies a @Nullable that is @Target(TYPE_USE)
// as well, so nothing but the import forks.
//? if >=1.21.11 {
import org.jspecify.annotations.Nullable;
//?} else {
/*import org.jetbrains.annotations.Nullable;
*///?}

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

		//? if >=26.2 {
		if (state.is(BlockItemTags.DIAMOND_ORES.block()) || state.is(BlockItemTags.EMERALD_ORES.block())) {
		//?} else {
		/*if (state.is(BlockTags.DIAMOND_ORES) || state.is(BlockTags.EMERALD_ORES)) {
		*///?}
			return 50;
		}

		if (state.is(BlockTags.GOLD_ORES)) {
			return 20;
		}

		//? if >=26.2 {
		if (state.is(BlockTags.IRON_ORES) || state.is(BlockItemTags.LAPIS_ORES.block())) {
		//?} else {
		/*if (state.is(BlockTags.IRON_ORES) || state.is(BlockTags.LAPIS_ORES)) {
		*///?}
			return 15;
		}

		//? if >=26.2 {
		if (state.is(BlockItemTags.REDSTONE_ORES.block())) {
		//?} else {
		/*if (state.is(BlockTags.REDSTONE_ORES)) {
		*///?}
			return 12;
		}

		//? if >=26.2 {
		if (state.is(BlockItemTags.COAL_ORES.block()) || state.is(Blocks.NETHER_QUARTZ_ORE)) {
		//?} else {
		/*if (state.is(BlockTags.COAL_ORES) || state.is(Blocks.NETHER_QUARTZ_ORE)) {
		*///?}
			return 10;
		}

		if (state.is(BlockTags.COPPER_ORES)) {
			return 8;
		}

		// Plain stone & friends.
		return 1;
	}

	/**
	 * Which combat specialization this damage trains, or null if it isn't a
	 * weapon attack at all. Arrows fired from bows/crossbows train archery;
	 * melee hits and thrown weapons (tridents, spears) train arms mastery.
	 */
	public static @Nullable Skill specializationSkill(final ServerPlayer attacker, final DamageSource source) {
		if (source.getDirectEntity() instanceof AbstractArrow arrow) {
			//? if >=1.21 {
			ItemStack weapon = arrow.getWeaponItem();
			//?} else {
			/*ItemStack weapon = weaponItem(arrow);
			*///?}

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

		//? if >=1.21 {
		ItemStack weapon = arrow.getWeaponItem();
		//?} else {
		/*ItemStack weapon = weaponItem(arrow);
		*///?}
		return weapon != null && weapon.is(ModTags.RANGED_WEAPONS);
	}

	/**
	 * Whether this damage is a melee weapon the attacker threw — in vanilla a
	 * trident, and a spear too if one is ever made throwable, since the test is
	 * the {@code specialities:melee_weapons} tag rather than a hardcoded item.
	 * A thrown trident is a {@code ThrownTrident}, which is an
	 * {@link AbstractArrow} whose weapon item is the trident itself, so
	 * {@link #isRangedWeaponShot} (bows and crossbows, by the
	 * {@code specialities:ranged_weapons} tag) says no to it and this says yes.
	 *
	 * <p>Separate from that test because the two callers want different things:
	 * the combat skill counts a thrown trident as a weapon attack, the sneaking
	 * skill's stealth crit deliberately does not.
	 */
	public static boolean isThrownMeleeWeapon(final ServerPlayer attacker, final DamageSource source) {
		if (!(source.getDirectEntity() instanceof AbstractArrow projectile) || projectile.getOwner() != attacker) {
			return false;
		}

		//? if >=1.21 {
		ItemStack weapon = projectile.getWeaponItem();
		//?} else {
		/*ItemStack weapon = weaponItem(projectile);
		*///?}
		return weapon != null && weapon.is(ModTags.MELEE_WEAPONS);
	}

	// The `getWeaponItem()` substitute below 1.21 (design R-04), and the ONLY place the
	// three tests above differ on that node — each of them reads this instead, so all three
	// keep identical semantics rather than being individually approximated.
	//
	// Two sources, in order: the weapon BowItemMixin/CrossbowItemMixin stamped onto the
	// projectile as it was spawned, else the projectile's own pickup item, which is the
	// trident for a thrown trident and the arrow for an arrow. Case by case against what
	// `getWeaponItem()` returns above:
	//   bow/crossbow arrow  -> the bow/crossbow          (stamped)
	//   thrown trident      -> the trident               (pickup item; getWeaponItem agrees)
	//   dispenser/skeleton  -> the arrow item, vs null   — unreachable: all three callers
	//                          require the attacker to be the projectile's owner
	//   an unstamped player shot -> the arrow item, vs null — same answer from all three
	//                          tests either way, since an arrow is in neither weapon tag
	//
	// The stamp is transient, so an in-flight arrow that survives a save/load round trip
	// loses it and falls back to its pickup item. Modern versions persist `firedFromWeapon`
	// in the projectile's NBT, so that one case diverges; it needs the arrow to be saved
	// mid-flight and then hit a mob, and the cost of closing it is a persistent attachment
	// (and a new NBT key) on every arrow ever fired.
	// Public rather than private because AbstractArrowMixin's ricochet chain reads it too,
	// and that is the point: ONE definition of "what fired this projectile" on this node.
	//? if >=1.21 {
	//?} else {
	/*public static @Nullable ItemStack weaponItem(final AbstractArrow projectile) {
		ItemStack stamped = SkillStore.INSTANCE.getFiringWeapon(projectile);
		return stamped != null ? stamped : ((AbstractArrowAccessor) projectile).specialities$getPickupItem();
	}

	// The passive-looting bonus, and the same "ONE definition on this node" reasoning as
	// weaponItem above: below 1.21 the only looting read is
	// `EnchantmentHelper.getMobLooting(LivingEntity)I`, which vanilla uses for BOTH the loot
	// table and the mob equipment-drop chance, so EnchantmentHelperMixin adds this number to
	// it and LivingEntityMixin takes the same number back off at the one call site that
	// feeds equipment drops. Two copies of the predicate would be a silent balance drift the
	// first time either changed; there is exactly one, and the two hooks cancel exactly.
	public static int passiveLootingBonus(final @Nullable Entity killer) {
		if (!(killer instanceof ServerPlayer player) || !player.getMainHandItem().is(ModTags.WEAPONS)) {
			return 0;
		}

		return Math.max(0, Tuning.luckBonus(SkillManager.get(player).level(Skill.COMBAT)));
	}
	*///?}
}
