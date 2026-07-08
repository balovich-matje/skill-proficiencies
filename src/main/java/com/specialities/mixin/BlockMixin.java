package com.specialities.mixin;

import java.util.List;

import com.specialities.skills.Skill;
import com.specialities.skills.SkillCategories;
import com.specialities.skills.SkillManager;
import com.specialities.skills.Tuning;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import net.minecraft.core.component.DataComponents;

/**
 * Passive fortune from skills.
 *
 * <p>Vanilla fortune is read from the TOOL stack in the loot context
 * ({@code ApplyBonusCount}), so for ores/crops we hand the loot roll a copy of
 * the tool with the skill's bonus fortune levels added on top of its real
 * enchantment. Log loot has no fortune function in vanilla, so bonus logs are
 * rolled manually using the ore-fortune formula.
 */
@Mixin(Block.class)
public abstract class BlockMixin {
	private static final String GET_DROPS = "getDrops(Lnet/minecraft/world/level/block/state/BlockState;"
			+ "Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;"
			+ "Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/Entity;"
			+ "Lnet/minecraft/world/item/ItemInstance;)Ljava/util/List;";

	@ModifyVariable(method = GET_DROPS, at = @At("HEAD"), argsOnly = true)
	private static ItemInstance specialities$boostToolFortune(final ItemInstance tool, final BlockState state,
			final ServerLevel level, final BlockPos pos, final BlockEntity blockEntity, final Entity breaker,
			final ItemInstance originalTool) {
		if (!(breaker instanceof ServerPlayer player) || !(tool instanceof ItemStack stack)) {
			return tool;
		}

		Skill skill = SkillCategories.fortuneSkill(state);
		if (skill == null) {
			return tool;
		}

		int bonus = Tuning.luckBonus(SkillManager.get(player).level(skill));
		if (bonus <= 0) {
			return tool;
		}

		Holder<Enchantment> fortune = level.registryAccess()
				.lookupOrThrow(Registries.ENCHANTMENT)
				.getOrThrow(Enchantments.FORTUNE);

		ItemStack boosted = stack.copy();
		ItemEnchantments current = boosted.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
		ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(current);
		mutable.set(fortune, mutable.getLevel(fortune) + bonus);
		boosted.set(DataComponents.ENCHANTMENTS, mutable.toImmutable());
		return boosted;
	}

	@Inject(method = GET_DROPS, at = @At("RETURN"))
	private static void specialities$bonusLogDrops(final BlockState state, final ServerLevel level, final BlockPos pos,
			final BlockEntity blockEntity, final Entity breaker, final ItemInstance tool,
			final CallbackInfoReturnable<List<ItemStack>> cir) {
		if (!(breaker instanceof ServerPlayer player) || !state.is(BlockTags.LOGS)) {
			return;
		}

		Holder<Enchantment> fortune = level.registryAccess()
				.lookupOrThrow(Registries.ENCHANTMENT)
				.getOrThrow(Enchantments.FORTUNE);

		int skillBonus = Tuning.luckBonus(SkillManager.get(player).level(Skill.WOODCUTTING));
		int totalFortune = EnchantmentHelper.getItemEnchantmentLevel(fortune, tool) + skillBonus;
		if (totalFortune <= 0) {
			return;
		}

		for (ItemStack drop : cir.getReturnValue()) {
			if (drop.is(ItemTags.LOGS)) {
				// Vanilla ore-fortune distribution: multiplier in [1, fortune + 1].
				int extra = Math.max(0, level.getRandom().nextInt(totalFortune + 2) - 1);
				drop.grow(drop.getCount() * extra);
			}
		}
	}
}
