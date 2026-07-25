package com.specialities.client;

import com.specialities.api.SkillType;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
// `net.minecraft.data.AtlasIds` and `Minecraft.getAtlasManager()` both land at 1.21.11.
// Below that there is no per-atlas id registry and no dedicated items atlas at all: item
// textures are stitched into the BLOCKS atlas, reached through the model manager (R-17).
//? if >=1.21.11 {
import net.minecraft.data.AtlasIds;
//?} else {
/*import net.minecraft.client.renderer.texture.TextureAtlas;
*///?}

/**
 * Item-atlas sprites for each skill. Sprites (rather than the graphics object's
 * own item-draw call) so icons can be drawn translucent — item
 * rendering ignores tint/alpha. Each skill names its own flat item texture
 * via {@link SkillType#iconTexture()}.
 */
public final class SkillIcons {
	private SkillIcons() {
	}

	public static TextureAtlasSprite sprite(final SkillType skill) {
		//? if >=1.21.11 {
		return Minecraft.getInstance().getAtlasManager()
				.getAtlasOrThrow(AtlasIds.ITEMS)
				.getSprite(skill.iconTexture());
		//?} else {
		/*return Minecraft.getInstance().getModelManager()
				.getAtlas(TextureAtlas.LOCATION_BLOCKS)
				.getSprite(skill.iconTexture());
		*///?}
	}
}
