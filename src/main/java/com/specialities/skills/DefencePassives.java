package com.specialities.skills;

import com.specialities.Specialities;

import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Defence skill passives, applied as permanent attribute modifiers:
 * +1 armor toughness per 25 levels, +1 heart per 10 levels.
 * Re-applied on join, respawn, and defence level changes.
 */
public final class DefencePassives {
	private static final Identifier HEALTH_MODIFIER_ID = Specialities.id("defence_health");
	private static final Identifier TOUGHNESS_MODIFIER_ID = Specialities.id("defence_toughness");

	private DefencePassives() {
	}

	public static void apply(final ServerPlayer player) {
		int level = SkillManager.get(player).level(Skill.DEFENCE);
		set(player, Attributes.MAX_HEALTH, HEALTH_MODIFIER_ID, Tuning.maxHealthBonus(level));
		set(player, Attributes.ARMOR_TOUGHNESS, TOUGHNESS_MODIFIER_ID, Tuning.toughnessBonus(level));
	}

	private static void set(final ServerPlayer player, final Holder<Attribute> attribute, final Identifier id,
			final double amount) {
		AttributeInstance instance = player.getAttribute(attribute);
		if (instance == null) {
			return;
		}

		AttributeModifier existing = instance.getModifier(id);
		if (existing != null && existing.amount() == amount) {
			return;
		}

		instance.removeModifier(id);
		if (amount > 0) {
			instance.addPermanentModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_VALUE));
		}
	}
}
