package com.specialities;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

public final class ModTags {
	/** All weapons (melee + ranged), for the combat skill (see data/specialities/tags/item/). */
	public static final TagKey<Item> WEAPONS = TagKey.create(Registries.ITEM, Specialities.id("weapons"));
	/** Swords, spears, trident, mace — the arms mastery skill. */
	public static final TagKey<Item> MELEE_WEAPONS = TagKey.create(Registries.ITEM, Specialities.id("melee_weapons"));
	/** Bows and crossbows — the archery skill. */
	public static final TagKey<Item> RANGED_WEAPONS = TagKey.create(Registries.ITEM, Specialities.id("ranged_weapons"));
	/** Armor that dampens the sneaking skill's detection bonus (leather/elytra/heads excluded). */
	public static final TagKey<Item> HEAVY_ARMOR = TagKey.create(Registries.ITEM, Specialities.id("heavy_armor"));

	private ModTags() {
	}
}
