package com.specialities.skills;

import java.util.Locale;
import java.util.function.Supplier;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public enum Skill {
	MINING("mining", 0xFF55CCFF, () -> Items.IRON_PICKAXE),
	WOODCUTTING("woodcutting", 0xFFC08040, () -> Items.IRON_AXE),
	COMBAT("combat", 0xFFFF5555, () -> Items.IRON_SWORD),
	ARMS_MASTERY("arms_mastery", 0xFFAAB4C4, () -> Items.IRON_SPEAR),
	ARCHERY("archery", 0xFF7FCF9F, () -> Items.BOW),
	HARVESTING("harvesting", 0xFF77CC44, () -> Items.IRON_HOE),
	EXCAVATION("excavation", 0xFFE8C060, () -> Items.IRON_SHOVEL),
	FISHING("fishing", 0xFF5599EE, () -> Items.FISHING_ROD),
	DEFENCE("defence", 0xFFD4A32C, () -> Items.IRON_CHESTPLATE),
	ACROBATICS("acrobatics", 0xFFCC66CC, () -> Items.FEATHER),
	ATHLETICS("athletics", 0xFF55DDCC, () -> Items.GOLDEN_BOOTS),
	SNEAKING("sneaking", 0xFF9977DD, () -> Items.LEATHER_BOOTS);

	private final String id;
	private final int color;
	private final Supplier<Item> icon;

	Skill(final String id, final int color, final Supplier<Item> icon) {
		this.id = id;
		this.color = color;
		this.icon = icon;
	}

	public String id() {
		return this.id;
	}

	public int color() {
		return this.color;
	}

	public Item icon() {
		return this.icon.get();
	}

	public Component displayName() {
		return Component.translatable("skill.specialities." + this.id);
	}

	public static Skill byId(final String id) {
		return valueOf(id.toUpperCase(Locale.ROOT));
	}
}
