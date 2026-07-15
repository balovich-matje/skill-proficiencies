package com.specialities.client;

import com.specialities.skills.Skill;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;

/**
 * Item-atlas sprites for each skill. Sprites (rather than
 * {@code GuiGraphicsExtractor.item}) so icons can be drawn translucent — item
 * rendering ignores tint/alpha. All mapped textures are flat item textures.
 */
public final class SkillIcons {
	private SkillIcons() {
	}

	public static TextureAtlasSprite sprite(final Skill skill) {
		return Minecraft.getInstance().getAtlasManager()
				.getAtlasOrThrow(AtlasIds.ITEMS)
				.getSprite(texture(skill));
	}

	private static Identifier texture(final Skill skill) {
		return switch (skill) {
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
}
