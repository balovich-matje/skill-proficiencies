package com.specialities.client;

import com.specialities.api.SkillType;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.data.AtlasIds;

/**
 * Item-atlas sprites for each skill. Sprites (rather than
 * {@code GuiGraphicsExtractor.item}) so icons can be drawn translucent — item
 * rendering ignores tint/alpha. Each skill names its own flat item texture
 * via {@link SkillType#iconTexture()}.
 */
public final class SkillIcons {
	private SkillIcons() {
	}

	public static TextureAtlasSprite sprite(final SkillType skill) {
		return Minecraft.getInstance().getAtlasManager()
				.getAtlasOrThrow(AtlasIds.ITEMS)
				.getSprite(skill.iconTexture());
	}
}
