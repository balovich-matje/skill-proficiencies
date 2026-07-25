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
	// Spears were introduced IN 1.21.11 (mojmap `WOODEN_SPEAR -> xI` … `IRON_SPEAR -> xL`),
	// so below that node the arms-mastery icon needs a substitute; design §3.4 names
	// IRON_SWORD. Keep the two halves (item + `iconTexture()` arm) in step.
	ARMS_MASTERY("arms_mastery", 0xFFAAB4C4,
			/*? if >=1.21.11 {*/() -> Items.IRON_SPEAR),
			/*?} else *///() -> Items.IRON_SWORD),
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
	// Every arm forks below 1.21 and all fifteen fork the same way: `withDefaultNamespace`
	// and `fromNamespaceAndPath` are 1.21 additions, made when the two `ResourceLocation`
	// constructors were taken private. On 1.20.1 the constructor IS the API (mojmap has
	// neither static; `tryParse` exists but returns @Nullable, which is a worse contract).
	//
	// The class name itself is NOT forked: the controller's `ResourceLocation` <-> `Identifier`
	// replacement rewrites it per node (conventions §3), which is why the legacy branches
	// below say `new Identifier(...)` — that text becomes `new ResourceLocation(...)` on
	// every node where the branch is live.
	//
	// A `replacements` rule of its own (`Identifier.withDefaultNamespace(` <-> `new
	// Identifier(`) would collapse all fifteen to one line and was rejected: it would have
	// to run in a fixed order relative to the existing class-name rule, and getting that
	// wrong is silent — exactly the failure mode conventions §3 records for the `Util` rule.
	@Override
	public Identifier iconTexture() {
		return switch (this) {
			case MINING ->
					/*? if >=1.21 {*/Identifier.withDefaultNamespace("item/iron_pickaxe");
					/*?} else *///new Identifier("item/iron_pickaxe");
			case WOODCUTTING ->
					/*? if >=1.21 {*/Identifier.withDefaultNamespace("item/iron_axe");
					/*?} else *///new Identifier("item/iron_axe");
			case COMBAT ->
					/*? if >=1.21 {*/Identifier.withDefaultNamespace("item/iron_sword");
					/*?} else *///new Identifier("item/iron_sword");
			// Three arms rather than two: the spear is >=1.21.11, and BELOW 1.21 the
			// identifier itself has to be built with the constructor (see the note on
			// iconTexture). Written as a statement block, not the inline form — an
			// `elif` needs one.
			//? if >=1.21.11 {
			case ARMS_MASTERY -> Identifier.withDefaultNamespace("item/iron_spear");
			//?} elif >=1.21 {
			/*case ARMS_MASTERY -> Identifier.withDefaultNamespace("item/iron_sword");
			*///?} else {
			/*case ARMS_MASTERY -> new Identifier("item/iron_sword");
			*///?}
			case ARCHERY ->
					/*? if >=1.21 {*/Identifier.withDefaultNamespace("item/bow");
					/*?} else *///new Identifier("item/bow");
			case HARVESTING ->
					/*? if >=1.21 {*/Identifier.withDefaultNamespace("item/iron_hoe");
					/*?} else *///new Identifier("item/iron_hoe");
			case EXCAVATION ->
					/*? if >=1.21 {*/Identifier.withDefaultNamespace("item/iron_shovel");
					/*?} else *///new Identifier("item/iron_shovel");
			case FISHING ->
					/*? if >=1.21 {*/Identifier.withDefaultNamespace("item/fishing_rod");
					/*?} else *///new Identifier("item/fishing_rod");
			case DEFENCE ->
					/*? if >=1.21 {*/Identifier.withDefaultNamespace("item/iron_chestplate");
					/*?} else *///new Identifier("item/iron_chestplate");
			case ACROBATICS ->
					/*? if >=1.21 {*/Identifier.withDefaultNamespace("item/feather");
					/*?} else *///new Identifier("item/feather");
			case ATHLETICS ->
					/*? if >=1.21 {*/Identifier.withDefaultNamespace("item/golden_boots");
					/*?} else *///new Identifier("item/golden_boots");
			case SNEAKING ->
					/*? if >=1.21 {*/Identifier.withDefaultNamespace("item/leather_boots");
					/*?} else *///new Identifier("item/leather_boots");
			case SMITHING ->
					/*? if >=1.21 {*/Identifier.withDefaultNamespace("item/iron_ingot");
					/*?} else *///new Identifier("item/iron_ingot");
			case ALCHEMY ->
					/*? if >=1.21 {*/Identifier.withDefaultNamespace("item/brewing_stand");
					/*?} else *///new Identifier("item/brewing_stand");
			case ENCHANTING ->
					/*? if >=1.21 {*/Identifier.withDefaultNamespace("item/enchanted_book");
					/*?} else *///new Identifier("item/enchanted_book");
		};
	}

	@Override
	public Component displayName() {
		return Component.translatable("skill.specialities." + this.id);
	}
}
