package com.specialities.client;

import com.specialities.skills.PlayerSkills;
import com.specialities.api.SkillType;
import com.specialities.skills.SkillCategories;
import com.specialities.skills.SkillManager;
import com.specialities.skills.Tuning;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
// 26.x GUI rendering is extract-based; 1.21.11 and below draw immediately. Every
// draw call this file makes exists verbatim on GuiGraphics — including the
// TextureAtlasSprite blitSprite overload that takes an ARGB int (R-17).
//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}
// `RenderPipelines` and `net.minecraft.util.ARGB` are 1.21.11-and-up. Below that the colour
// helpers are `FastColor.ARGB32` (same names, same shapes) and the tinted sprite draw is
// the public float-RGBA `blit(x, y, z, w, h, sprite, r, g, b, a)` — R-17. Do NOT reach for
// `blitSprite(sprite, ...)` there: both sprite-taking blitSprite overloads are PRIVATE on
// 1.21.1 (`private void a(gql, int, int, int, int, int)`) and that trailing int is
// blitOffset/z, not a colour.
//? if >=1.21.11 {
import net.minecraft.client.renderer.RenderPipelines;
//?}
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
//? if >=1.21.11 {
import net.minecraft.util.ARGB;
//?} else {
/*import net.minecraft.util.FastColor;
*///?}
import net.minecraft.util.Mth;

/**
 * Always-visible skill XP bar sitting right above the vanilla experience bar,
 * showing the most recent skill that gained XP. Kept vanilla-slick: just the
 * bar and the skill level in the middle, drawn fully opaque like the vanilla
 * experience bar. On each XP gain two icons of the skill's tool converge into
 * the bar (fading in), then the bar grows.
 */
public final class SkillXpHudBar {
	private static final Identifier BAR_BACKGROUND_SPRITE = Identifier.withDefaultNamespace("hud/experience_bar_background");
	private static final int BAR_WIDTH = 182;
	private static final int BAR_HEIGHT = 5;
	/**
	 * The vanilla XP bar's original slot (guiHeight - 29); the real vanilla bar
	 * and everything above it are raised by {@link SpecialitiesClient#HUD_SHIFT}.
	 */
	private static final int BOTTOM_OFFSET = 29;

	/** Opaque, matching the vanilla XP bar; also the peak alpha of the converging-icon fade. */
	private static final float BASE_ALPHA = 1.0F;
	private static final long ICON_ANIM_MS = 450;
	private static final long GROW_ANIM_MS = 250;
	/** After a gain, keep showing the gaining skill this long before returning to the held tool's skill. */
	private static final long GAIN_LINGER_MS = 3000;
	private static final int ICON_SIZE = 12;

	private SkillXpHudBar() {
	}

	// Matches HudElement's functional method: extractRenderState on 26.x, render below.
	//? if >=26.1 {
	public static void render(final GuiGraphicsExtractor graphics, final DeltaTracker deltaTracker) {
	//?} else {
	/*public static void render(final GuiGraphics graphics, final DeltaTracker deltaTracker) {
	*///?}
		Minecraft minecraft = Minecraft.getInstance();

		if (minecraft.player == null) {
			return;
		}

		// The held tool/weapon picks the displayed skill; a recent XP gain
		// temporarily overrides it (with the gain animation), then lingers a
		// moment before switching back.
		SkillType gained = SkillHudState.skill();
		long age = SkillHudState.animAgeMs();
		SkillType held = SkillCategories.toolSkill(minecraft.player.getMainHandItem());

		if (held == null) {
			// Shields usually sit in the offhand.
			held = SkillCategories.toolSkill(minecraft.player.getOffhandItem());
		}
		boolean showGained = gained != null && (age < GAIN_LINGER_MS || held == null);
		SkillType skill = showGained ? gained : held;

		if (skill == null) {
			return;
		}

		float progress;
		int shownLevel;

		if (showGained) {
			progress = displayedProgress(age);
			shownLevel = age < ICON_ANIM_MS + GROW_ANIM_MS / 2 ? SkillHudState.fromLevel() : SkillHudState.level();
		} else {
			PlayerSkills data = SkillManager.get(minecraft.player);
			progress = progressWithin(data.totalXp(skill), data.level(skill));
			shownLevel = data.level(skill);
		}

		int left = (graphics.guiWidth() - BAR_WIDTH) / 2;
		int top = graphics.guiHeight() - BOTTOM_OFFSET;

		// Bar background + fill.
		//? if >=1.21.11 {
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BAR_BACKGROUND_SPRITE, left, top, BAR_WIDTH, BAR_HEIGHT, BASE_ALPHA);
		//?} else {
		/*// Same float-alpha path as the icons: resolve the GUI sprite by hand
		// (`Minecraft.getGuiSprites().getSprite(...)`) and use the public float-RGBA blit,
		// so BASE_ALPHA keeps meaning something here instead of being silently dropped.
		// `hud/experience_bar_background` ships no `.mcmeta`, i.e. plain STRETCH scaling, so
		// this is geometrically identical to vanilla's own blitSprite of it.
		graphics.blit(left, top, 0, BAR_WIDTH, BAR_HEIGHT,
				Minecraft.getInstance().getGuiSprites().getSprite(BAR_BACKGROUND_SPRITE),
				1.0F, 1.0F, 1.0F, BASE_ALPHA);
		*///?}

		int fillWidth = (int) (progress * (BAR_WIDTH - 2));
		if (fillWidth > 0) {
			int fillColor = (skill.color() & 0x00FFFFFF) | 0xFF000000;
			graphics.fill(left + 1, top + 1, left + 1 + fillWidth, top + BAR_HEIGHT - 1, fillColor);
		}

		// Skill level just past the bar's right end (the space above belongs to
		// the raised vanilla level number now); black outline, colored center.
		String label = Integer.toString(shownLevel);
		int textX = left + BAR_WIDTH + 4;
		int textY = top - 2;
		//? if >=1.21.11 {
		int outline = ARGB.color(0xFF, 0x000000);
		//?} else {
		/*int outline = FastColor.ARGB32.color(0xFF, 0x000000);
		*///?}
		// 26.x names the text draw `text`; below it is `drawString`. Same overload set. The
		// third branch exists only because ARGB moves to FastColor.ARGB32 below 1.21.11 —
		// an elif chain rather than a nested block, so every branch stays flat.
		//? if >=26.1 {
		graphics.text(minecraft.font, label, textX + 1, textY, outline, false);
		graphics.text(minecraft.font, label, textX - 1, textY, outline, false);
		graphics.text(minecraft.font, label, textX, textY + 1, outline, false);
		graphics.text(minecraft.font, label, textX, textY - 1, outline, false);
		graphics.text(minecraft.font, label, textX, textY, ARGB.color(0xFF, skill.color()), false);
		//?} elif >=1.21.11 {
		/*graphics.drawString(minecraft.font, label, textX + 1, textY, outline, false);
		graphics.drawString(minecraft.font, label, textX - 1, textY, outline, false);
		graphics.drawString(minecraft.font, label, textX, textY + 1, outline, false);
		graphics.drawString(minecraft.font, label, textX, textY - 1, outline, false);
		graphics.drawString(minecraft.font, label, textX, textY, ARGB.color(0xFF, skill.color()), false);
		*///?} else {
		/*graphics.drawString(minecraft.font, label, textX + 1, textY, outline, false);
		graphics.drawString(minecraft.font, label, textX - 1, textY, outline, false);
		graphics.drawString(minecraft.font, label, textX, textY + 1, outline, false);
		graphics.drawString(minecraft.font, label, textX, textY - 1, outline, false);
		graphics.drawString(minecraft.font, label, textX, textY, FastColor.ARGB32.color(0xFF, skill.color()), false);
		*///?}

		// Converging tool icons while the animation runs.
		if (age < ICON_ANIM_MS) {
			float eased = 1.0F - Mth.square(1.0F - age / (float) ICON_ANIM_MS);
			int iconAlpha = (int) (BASE_ALPHA * 255.0F * (1.0F - eased));

			if (iconAlpha > 8) {
				TextureAtlasSprite sprite = SkillIcons.sprite(skill);

				int iconY = top - (ICON_SIZE - BAR_HEIGHT) / 2;
				int centerX = left + BAR_WIDTH / 2 - ICON_SIZE / 2;
				int leftX = Math.round(Mth.lerp(eased, left - ICON_SIZE - 8, centerX));
				int rightX = Math.round(Mth.lerp(eased, left + BAR_WIDTH + 8, centerX));
				//? if >=1.21.11 {
				int color = ARGB.color(iconAlpha, 0xFFFFFF);

				graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, leftX, iconY, ICON_SIZE, ICON_SIZE, color);
				graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, rightX, iconY, ICON_SIZE, ICON_SIZE, color);
				//?} else {
				/*// R-17's legacy path, verbatim: the public float-RGBA blit. `iconAlpha` stays
				// the int 0..255 the animation computes and is converted here at the call
				// site (conventions §5b) — the easing is never forked.
				float iconAlphaF = iconAlpha / 255.0F;

				graphics.blit(leftX, iconY, 0, ICON_SIZE, ICON_SIZE, sprite, 1.0F, 1.0F, 1.0F, iconAlphaF);
				graphics.blit(rightX, iconY, 0, ICON_SIZE, ICON_SIZE, sprite, 1.0F, 1.0F, 1.0F, iconAlphaF);
				*///?}
			}
		}
	}

	/** Bar fill: hold the old value while icons fly, then grow to the new one. */
	private static float displayedProgress(final long age) {
		float from = progressWithin(SkillHudState.fromTotalXp(), SkillHudState.fromLevel());
		float to = progressWithin(SkillHudState.totalXp(), SkillHudState.level());

		if (age >= ICON_ANIM_MS + GROW_ANIM_MS) {
			return to;
		}

		if (age < ICON_ANIM_MS) {
			return from;
		}

		float grow = (age - ICON_ANIM_MS) / (float) GROW_ANIM_MS;

		if (SkillHudState.level() > SkillHudState.fromLevel()) {
			// Fill to the end of the old level, then restart into the new one.
			return grow < 0.5F ? Mth.lerp(grow * 2.0F, from, 1.0F) : Mth.lerp(grow * 2.0F - 1.0F, 0.0F, to);
		}

		return Mth.lerp(grow, from, to);
	}

	private static float progressWithin(final int totalXp, final int level) {
		if (level >= Tuning.MAX_LEVEL) {
			return 1.0F;
		}

		int intoLevel = totalXp - Tuning.totalXpForLevel(level);
		return Mth.clamp(intoLevel / (float) Tuning.xpToNext(level), 0.0F, 1.0F);
	}
}
