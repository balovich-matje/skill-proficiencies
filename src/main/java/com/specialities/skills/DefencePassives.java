package com.specialities.skills;

//? if >=1.21 {
//?} else {
/*import java.util.UUID;
*///?}

import com.specialities.Specialities;

//? if >=1.21 {
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
//?}
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
	// See AthleticsTicker for how these UUIDs are derived and why they have to be stable
	// literals. It matters MORE here than there: these modifiers are permanent, so they are
	// serialised into the player's Attributes NBT, and an id that changed between sessions
	// would leave the old modifier behind on every login, stacking max health forever.
	//? if >=1.21 {
	private static final Identifier HEALTH_MODIFIER_ID = Specialities.id("defence_health");
	private static final Identifier TOUGHNESS_MODIFIER_ID = Specialities.id("defence_toughness");
	//?} else {
	/*private static final UUID HEALTH_MODIFIER_ID = UUID.fromString("22d92d5a-6c53-31a4-b9ef-a491d1f5bb2f");
	private static final UUID TOUGHNESS_MODIFIER_ID = UUID.fromString("4e699a56-e78c-301e-98cc-6a852cfe5338");
	private static final String HEALTH_MODIFIER_NAME = "specialities:defence_health";
	private static final String TOUGHNESS_MODIFIER_NAME = "specialities:defence_toughness";
	*///?}

	private DefencePassives() {
	}

	public static void apply(final ServerPlayer player) {
		int level = SkillManager.get(player).level(Skill.DEFENCE);
		//? if >=1.21 {
		set(player, Attributes.MAX_HEALTH, HEALTH_MODIFIER_ID, Tuning.maxHealthBonus(level));
		set(player, Attributes.ARMOR_TOUGHNESS, TOUGHNESS_MODIFIER_ID, Tuning.toughnessBonus(level));
		//?} else {
		/*set(player, Attributes.MAX_HEALTH, HEALTH_MODIFIER_ID, HEALTH_MODIFIER_NAME,
				Tuning.maxHealthBonus(level));
		set(player, Attributes.ARMOR_TOUGHNESS, TOUGHNESS_MODIFIER_ID, TOUGHNESS_MODIFIER_NAME,
				Tuning.toughnessBonus(level));
		*///?}
	}

	// `Attributes.MAX_HEALTH` is a `Holder<Attribute>` from 1.21 up and a bare `Attribute`
	// below, and the modifier carries a display name as well as an id there. Only the
	// signature and the two lines that name those things fork; the null guard, the
	// idempotence check and the remove-then-add order are the shared implementation.
	//? if >=1.21 {
	private static void set(final ServerPlayer player, final Holder<Attribute> attribute, final Identifier id,
			final double amount) {
	//?} else {
	/*private static void set(final ServerPlayer player, final Attribute attribute, final UUID id, final String name,
			final double amount) {
	*///?}
		AttributeInstance instance = player.getAttribute(attribute);
		if (instance == null) {
			return;
		}

		AttributeModifier existing = instance.getModifier(id);
		//? if >=1.21 {
		if (existing != null && existing.amount() == amount) {
		//?} else {
		/*if (existing != null && existing.getAmount() == amount) {
		*///?}
			return;
		}

		instance.removeModifier(id);
		if (amount > 0) {
			//? if >=1.21 {
			instance.addPermanentModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_VALUE));
			//?} else {
			/*instance.addPermanentModifier(
					new AttributeModifier(id, name, amount, AttributeModifier.Operation.ADDITION));
			*///?}
		}
	}
}
