package com.specialities.skills;

import java.util.function.Supplier;

import com.specialities.api.SkillType;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public enum Skill implements SkillType {
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
	SNEAKING("sneaking", 0xFF9977DD, () -> Items.LEATHER_BOOTS),
	SMITHING("smithing", 0xFFE89040, () -> Items.ANVIL),
	ALCHEMY("alchemy", 0xFFEE6699, () -> Items.BREWING_STAND),
	ENCHANTING("enchanting", 0xFFAA55EE, () -> Items.ENCHANTED_BOOK);

	private final String id;
	private final int color;
	private final Supplier<Item> icon;

	Skill(final String id, final int color, final Supplier<Item> icon) {
		this.id = id;
		this.color = color;
		this.icon = icon;
	}

	@Override
	public String id() {
		return this.id;
	}

	@Override
	public int color() {
		return this.color;
	}

	public Item icon() {
		return this.icon.get();
	}

	/**
	 * The item-atlas texture for the icon. Usually the icon item's own
	 * texture, but always a flat item one — SMITHING's anvil is a block, so
	 * it borrows the iron ingot (block icons cannot be drawn translucent,
	 * which the HUD needs).
	 */
	@Override
	public Identifier iconTexture() {
		return switch (this) {
			case MINING -> Identifier.withDefaultNamespace("item/iron_pickaxe");
			case WOODCUTTING -> Identifier.withDefaultNamespace("item/iron_axe");
			case COMBAT -> Identifier.withDefaultNamespace("item/iron_sword");
			case ARMS_MASTERY -> Identifier.withDefaultNamespace("item/iron_spear");
			case ARCHERY -> Identifier.withDefaultNamespace("item/bow");
			case HARVESTING -> Identifier.withDefaultNamespace("item/iron_hoe");
			case EXCAVATION -> Identifier.withDefaultNamespace("item/iron_shovel");
			case FISHING -> Identifier.withDefaultNamespace("item/fishing_rod");
			case DEFENCE -> Identifier.withDefaultNamespace("item/iron_chestplate");
			case ACROBATICS -> Identifier.withDefaultNamespace("item/feather");
			case ATHLETICS -> Identifier.withDefaultNamespace("item/golden_boots");
			case SNEAKING -> Identifier.withDefaultNamespace("item/leather_boots");
			case SMITHING -> Identifier.withDefaultNamespace("item/iron_ingot");
			case ALCHEMY -> Identifier.withDefaultNamespace("item/brewing_stand");
			case ENCHANTING -> Identifier.withDefaultNamespace("item/enchanted_book");
		};
	}

	@Override
	public Component displayName() {
		return Component.translatable("skill.specialities." + this.id);
	}
}
