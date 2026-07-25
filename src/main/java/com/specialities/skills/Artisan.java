package com.specialities.skills;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.specialities.ModTags;
import com.specialities.platform.SkillStore;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
// The `minecraft:equippable` component (and its `equipment.Equippable` record) is
// 1.21.2+. On 1.21.1 the same question is asked through the `Equipable` interface:
// mojmap has `net.minecraft.world.item.Equipable` with a static `get(ItemStack) -> c_`
// and `getEquipmentSlot() -> m`, and ElytraItem/ArmorItem both implement it, so the
// legacy branch keeps the elytra in scope exactly like the component form does.
//? if >=1.21.2 {
import net.minecraft.world.item.equipment.Equippable;
//?} else {
/*import net.minecraft.world.item.Equipable;
*///?}
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
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
 * Shared helpers for the artisan skills (smithing, alchemy, enchanting).
 */
public final class Artisan {
	private Artisan() {
	}

	/** Tools, weapons, and armor pieces train smithing when crafted. */
	public static boolean isSmithingResult(final ItemStack stack) {
		if (stack.is(ModTags.SMITHING_ITEMS)) {
			return true;
		}

		//? if >=1.21.2 {
		Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
		return equippable != null
				&& equippable.slot().getType() == EquipmentSlot.Type.HUMANOID_ARMOR
				&& stack.isDamageableItem();
		//?} else {
		/*Equipable equippable = Equipable.get(stack);
		return equippable != null
				&& equippable.getEquipmentSlot().getType() == EquipmentSlot.Type.HUMANOID_ARMOR
				&& stack.isDamageableItem();
		*///?}
	}

	/** Number of bonus materials returned by smithing resourcefulness. */
	public static int rollSmithingReturns(final RandomSource random, final int level) {
		int returned = 0;
		while (random.nextFloat() < Tuning.smithingReturnChance(level, returned + 1)) {
			returned++;
		}

		return returned;
	}

	/** Smelting multicraft multiplier for a single smelted item: 1, 2, 4, or 8. */
	public static int rollSmeltMultiplier(final RandomSource random, final int level) {
		float roll = random.nextFloat();

		if (roll < Tuning.smeltChanceX8(level)) {
			return 8;
		}

		if (roll < Tuning.smeltChanceX8(level) + Tuning.smeltChanceX4(level)) {
			return 4;
		}

		if (roll < Tuning.smeltChanceX8(level) + Tuning.smeltChanceX4(level) + Tuning.smeltChanceX2(level)) {
			return 2;
		}

		return 1;
	}

	/** The player who last opened this brewing stand, if online. */
	public static @Nullable ServerPlayer brewingOwner(final Level level, final BlockPos pos) {
		if (!(level instanceof ServerLevel serverLevel)
				|| !(level.getBlockEntity(pos) instanceof BrewingStandBlockEntity stand)) {
			return null;
		}

		String uuid = SkillStore.INSTANCE.getBrewingOwner(stand);
		if (uuid == null) {
			return null;
		}

		return serverLevel.getServer().getPlayerList().getPlayer(UUID.fromString(uuid));
	}

	/**
	 * Enchanting luck: upgrade a random existing enchantment by one level, or
	 * add a compatible non-curse table enchantment at level 1.
	 */
	public static void applyEnchantLuck(final ServerPlayer player, final ItemStack stack) {
		// `EnchantmentHelper.getComponentType` is PRIVATE on 1.21.1 (`private static
		// kp<dai> d(cuq)`), so the legacy branch inlines its exact body, read off the
		// bytecode: `stack.is(Items.ENCHANTED_BOOK) ? STORED_ENCHANTMENTS : ENCHANTMENTS`
		// (`cut.uw` = Items.ENCHANTED_BOOK, `kq.y`/`kq.k` = the two DataComponents).
		//? if >=1.21.11 {
		DataComponentType<ItemEnchantments> componentType = EnchantmentHelper.getComponentType(stack);
		//?} else {
		/*DataComponentType<ItemEnchantments> componentType = stack.is(Items.ENCHANTED_BOOK)
				? DataComponents.STORED_ENCHANTMENTS
				: DataComponents.ENCHANTMENTS;
		*///?}
		ItemEnchantments current = stack.getOrDefault(componentType, ItemEnchantments.EMPTY);
		RandomSource random = player.getRandom();

		List<Holder<Enchantment>> upgradable = current.keySet().stream()
				.filter(holder -> current.getLevel(holder) < holder.value().getMaxLevel())
				.toList();

		List<Holder<Enchantment>> addable = addableEnchantments(player, stack, current);

		boolean upgrade;
		if (!upgradable.isEmpty() && !addable.isEmpty()) {
			upgrade = random.nextBoolean();
		} else if (!upgradable.isEmpty()) {
			upgrade = true;
		} else if (!addable.isEmpty()) {
			upgrade = false;
		} else {
			return;
		}

		ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(current);

		if (upgrade) {
			Holder<Enchantment> chosen = upgradable.get(random.nextInt(upgradable.size()));
			mutable.set(chosen, current.getLevel(chosen) + 1);
		} else {
			Holder<Enchantment> chosen = addable.get(random.nextInt(addable.size()));
			mutable.set(chosen, 1);
		}

		stack.set(componentType, mutable.toImmutable());
	}

	private static List<Holder<Enchantment>> addableEnchantments(final ServerPlayer player, final ItemStack stack,
			final ItemEnchantments current) {
		Optional<HolderSet.Named<Enchantment>> tableEnchants = player.level().registryAccess()
				.lookupOrThrow(Registries.ENCHANTMENT)
				.get(EnchantmentTags.IN_ENCHANTING_TABLE);

		if (tableEnchants.isEmpty()) {
			return List.of();
		}

		boolean book = stack.is(Items.ENCHANTED_BOOK);
		List<Holder<Enchantment>> result = new ArrayList<>();

		for (Holder<Enchantment> candidate : tableEnchants.get()) {
			if (candidate.is(EnchantmentTags.CURSE) || current.getLevel(candidate) > 0) {
				continue;
			}

			if (!book && !candidate.value().canEnchant(stack)) {
				continue;
			}

			if (!EnchantmentHelper.isEnchantmentCompatible(current.keySet(), candidate)) {
				continue;
			}

			result.add(candidate);
		}

		return result;
	}
}
