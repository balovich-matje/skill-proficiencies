package com.specialities.mixin;

import com.specialities.MeleeSwing;
import com.specialities.ModTags;
import com.specialities.skills.Skill;
import com.specialities.skills.SkillCategories;
import com.specialities.skills.SkillManager;
import com.specialities.skills.Tuning;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Proficiency: matching tool + matching block = faster breaking.
 * Arms mastery: faster attack recovery and passive sweeping edge.
 * Runs on both sides (the client needs it for break progress prediction);
 * skill data is synced to the owning client via the attachment.
 */
@Mixin(Player.class)
public abstract class PlayerMixin {
	@ModifyReturnValue(method = "getDestroySpeed", at = @At("RETURN"))
	private float specialities$applyProficiency(final float original, final BlockState state) {
		Player self = (Player) (Object) this;

		Skill skill = SkillCategories.breakSpeedSkill(state);
		if (skill == null || !SkillCategories.toolMatches(skill, self.getMainHandItem())) {
			return original;
		}

		int level = SkillManager.get(self).level(skill);
		if (level <= 0) {
			return original;
		}

		return original * Tuning.breakSpeedMultiplier(level);
	}

	@ModifyReturnValue(method = "getCurrentItemAttackStrengthDelay", at = @At("RETURN"))
	private float specialities$fasterAttackRecovery(final float original) {
		Player self = (Player) (Object) this;

		if (!self.getMainHandItem().is(ModTags.MELEE_WEAPONS)) {
			return original;
		}

		int level = SkillManager.get(self).level(Skill.ARMS_MASTERY);
		if (level <= 0) {
			return original;
		}

		return original * Tuning.recoveryTimeMultiplier(level);
	}

	/**
	 * Enchanting resourcefulness: chance (100% at level 100) that an enchanting
	 * table enchant deducts only half the XP levels. The full amount is still
	 * required to click the button; only the deduction shrinks.
	 */
	@ModifyVariable(method = "onEnchantmentPerformed", at = @At("HEAD"), argsOnly = true)
	private int specialities$enchantDiscount(final int cost) {
		Player self = (Player) (Object) this;

		if (self.level().isClientSide()) {
			return cost;
		}

		int level = SkillManager.get(self).level(Skill.ENCHANTING);
		if (level <= 0 || self.getRandom().nextFloat() >= Tuning.enchantDiscountChance(level)) {
			return cost;
		}

		return (cost + 1) / 2;
	}

	/** Athletics: up to -50% hunger cost while sprinting. */
	@ModifyVariable(method = "causeFoodExhaustion", at = @At("HEAD"), argsOnly = true)
	private float specialities$sprintExhaustion(final float amount) {
		Player self = (Player) (Object) this;

		if (!self.isSprinting()) {
			return amount;
		}

		int level = SkillManager.get(self).level(Skill.ATHLETICS);
		if (level <= 0) {
			return amount;
		}

		return amount * Tuning.recoveryTimeMultiplier(level);
	}

	/**
	 * Passive sweeping edge: the sweep ratio is an attribute fed by the
	 * enchantment as N/(N+1). We recompute it as if the effective level were
	 * (enchant + skill bonus), keeping the stacking additive in levels.
	 */
	@ModifyExpressionValue(
			method = "doSweepAttack",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/entity/player/Player;getAttributeValue(Lnet/minecraft/core/Holder;)D"))
	private double specialities$passiveSweepingEdge(final double original) {
		Player self = (Player) (Object) this;

		int bonus = Tuning.sweepingBonus(SkillManager.get(self).level(Skill.ARMS_MASTERY));
		if (bonus <= 0) {
			return original;
		}

		Holder<Enchantment> sweeping = self.level().registryAccess()
				.lookupOrThrow(Registries.ENCHANTMENT)
				.getOrThrow(Enchantments.SWEEPING_EDGE);
		int enchantLevel = EnchantmentHelper.getItemEnchantmentLevel(sweeping, self.getMainHandItem());
		int effectiveLevel = enchantLevel + bonus;

		double enchantRatio = enchantLevel > 0 ? (double) enchantLevel / (enchantLevel + 1) : 0.0;
		double effectiveRatio = (double) effectiveLevel / (effectiveLevel + 1);
		return original - enchantRatio + effectiveRatio;
	}

	/**
	 * Mark the one path a real melee swing takes, so the sneaking skill's
	 * stealth crit can tell a swing from everything else a player's damage
	 * source can be — see {@link MeleeSwing} for what that was costing.
	 *
	 * <p>Wrapped rather than a HEAD/RETURN injector pair because an exception
	 * anywhere under this call would leave the flag standing, and every later
	 * hit on the server would read as a swing until something else overwrote
	 * it. The {@code finally} makes that impossible.
	 *
	 * <p>Only the server copy opens a swing: {@code attack} also runs on the
	 * client for prediction, and in singleplayer that would race the integrated
	 * server's own thread over one static field. Every reader is server-side.
	 */
	@WrapMethod(method = "attack")
	private void specialities$markSwing(final Entity target, final Operation<Void> original) {
		Player self = (Player) (Object) this;

		if (self.level().isClientSide()) {
			original.call(target);
			return;
		}

		Entity previous = MeleeSwing.begin(self);

		try {
			original.call(target);
		} finally {
			MeleeSwing.end(previous);
		}
	}
}
