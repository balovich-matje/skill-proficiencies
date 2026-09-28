package com.specialities.client;

import com.specialities.Specialities;
import com.specialities.StealthStatePayload;

// `DeltaTracker` is 1.21+; below it the frame delta is a bare float. Never read here.
//? if >=1.21 {
import net.minecraft.client.DeltaTracker;
//?}
import net.minecraft.client.Minecraft;
// 26.x GUI rendering is extract-based; 1.21.11 and below draw immediately.
//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}
// `RenderPipelines` and `net.minecraft.util.ARGB` are both 1.21.11-and-up; below that the
// packing helpers live on `FastColor.ARGB32` (same method names and shapes) and a textured
// blit takes no colour argument at all, so the tint goes through `GuiGraphics.setColor`.
//? if >=1.21.11 {
import net.minecraft.client.renderer.RenderPipelines;
//?} else {
/*import com.mojang.blaze3d.systems.RenderSystem;
*///?}
import net.minecraft.resources.Identifier;
//? if >=1.21.11 {
import net.minecraft.util.ARGB;
//?} else {
/*import net.minecraft.util.FastColor;
*///?}
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

/**
 * Sneaking feedback overlay. While sneaking undetected near hostiles the
 * screen edges tint dark purple (distinguishable from plain darkness at
 * night); when a hostile spots the player it flips to a light vignette that
 * fades out over ~2 seconds.
 */
public final class StealthVignette {
	private static final Identifier TEXTURE = Specialities.id("textures/misc/stealth_vignette.png");

	private static final float DARK_MAX_ALPHA = 0.50F;
	// Deep violet, so the stealth tint reads as "sneak mode" rather than "night".
	private static final float DARK_RED = 0.30F;
	private static final float DARK_GREEN = 0.05F;
	private static final float DARK_BLUE = 0.45F;
	private static final float LIGHT_MAX_ALPHA = 0.55F;
	private static final long FLASH_DURATION_MS = 2000;
	/** Per-second exponential approach rate for the dark vignette fade in/out. */
	private static final float DARK_FADE_RATE = 6.0F;

	private static int state = StealthStatePayload.NONE;
	private static float darkAlpha;
	private static long flashStartMs = Long.MIN_VALUE;
	private static long lastFrameMs = Util.getMillis();

	// Whether vanilla's own vignette disappears with F1 on this version. MEASURED on both sides of
	// the fork, by javap of the vanilla Gui: 1.21.1 builds its camera-overlay layers (vignette
	// included) into a LayeredDraw added with a `!options.hideGui` supplier, and 26.1/1.21.11
	// (hideGui) and 26.2 (Hud.isHidden) call the camera overlays inside the same hidden check;
	// 1.20.1 calls renderVignette at offset 48 of Gui.render, before the first hideGui read, and
	// LexForge's VIGNETTE overlay has no hideGui check either. The fork sits on `>=1.21` because
	// that is the row this class's render signature already forks on (DeltaTracker, i.e. the
	// LayeredDraw.Layer shape); no node exists between 1.20.1 and 1.21.1 to pin it tighter. On
	// 26.x/1.21.11 the check is redundant — the Fabric path already skips this call when hidden.
	//? if >=1.21 {
	private static final boolean HIDES_WITH_HUD = true;
	//?} else {
	/*private static final boolean HIDES_WITH_HUD = false;
	*///?}

	private StealthVignette() {
	}

	public static void onUpdate(final StealthStatePayload payload, final Minecraft client) {
		if (payload.state() == StealthStatePayload.DETECTED && state != StealthStatePayload.DETECTED) {
			flashStartMs = Util.getMillis();
		}

		state = payload.state();
	}

	// Matches HudElement's functional method: extractRenderState on 26.x, render below. On
	// 1.20.1 there is no HudElement and no DeltaTracker; GuiMixin calls this directly.
	//? if >=26.1 {
	public static void render(final GuiGraphicsExtractor graphics, final DeltaTracker deltaTracker) {
	//?} elif >=1.21 {
	/*public static void render(final GuiGraphics graphics, final DeltaTracker deltaTracker) {
	*///?} else {
	/*public static void render(final GuiGraphics graphics, final float tickDelta) {
	*///?}
		// F1 (GitHub issue #8): this overlay follows VANILLA's vignette, which is hidden by F1 on
		// some versions and not on others — so it hides only where vanilla's does.
		if (HIDES_WITH_HUD && SpecialitiesClient.hudHidden()) {
			return;
		}

		long now = Util.getMillis();
		float dt = Math.min((now - lastFrameMs) / 1000.0F, 0.1F);
		lastFrameMs = now;

		if (Minecraft.getInstance().player == null) {
			darkAlpha = 0.0F;
			return;
		}

		// Dark vignette eases toward its target: on while hidden, off otherwise.
		float target = state == StealthStatePayload.HIDDEN ? DARK_MAX_ALPHA : 0.0F;
		darkAlpha += (target - darkAlpha) * Math.min(1.0F, DARK_FADE_RATE * dt);

		if (darkAlpha > 0.01F) {
			//? if >=1.21.11 {
			draw(graphics, ARGB.colorFromFloat(darkAlpha, DARK_RED, DARK_GREEN, DARK_BLUE));
			//?} elif >=1.21 {
			/*draw(graphics, FastColor.ARGB32.colorFromFloat(darkAlpha, DARK_RED, DARK_GREEN, DARK_BLUE));
			*///?} else {
			/*draw(graphics, colorFromFloat(darkAlpha, DARK_RED, DARK_GREEN, DARK_BLUE));
			*///?}
		}

		// Detection flash: light vignette fading out.
		long flashAge = now - flashStartMs;
		if (flashAge >= 0 && flashAge < FLASH_DURATION_MS) {
			float fade = 1.0F - flashAge / (float) FLASH_DURATION_MS;
			float alpha = LIGHT_MAX_ALPHA * Mth.square(fade);
			//? if >=1.21.11 {
			draw(graphics, ARGB.colorFromFloat(alpha, 1.0F, 1.0F, 1.0F));
			//?} elif >=1.21 {
			/*draw(graphics, FastColor.ARGB32.colorFromFloat(alpha, 1.0F, 1.0F, 1.0F));
			*///?} else {
			/*draw(graphics, colorFromFloat(alpha, 1.0F, 1.0F, 1.0F));
			*///?}
		}
	}

	// `FastColor.ARGB32.colorFromFloat` does not exist on 1.20.1 — only the four-int
	// `color(a, r, g, b)`. This is vanilla's own body for it (each channel scaled by 255 and
	// truncated), kept as a helper so the two call sites above stay one line each and the
	// fade arithmetic never forks (conventions §5b).
	//? if >=1.21 {
	//?} else {
	/*private static int colorFromFloat(final float a, final float r, final float g, final float b) {
		return FastColor.ARGB32.color((int) (a * 255.0F), (int) (r * 255.0F), (int) (g * 255.0F),
				(int) (b * 255.0F));
	}
	*///?}

	//? if >=26.1 {
	private static void draw(final GuiGraphicsExtractor graphics, final int color) {
	//?} else {
	/*private static void draw(final GuiGraphics graphics, final int color) {
	*///?}
		//? if >=1.21.11 {
		graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, 0, 0, 0.0F, 0.0F,
				graphics.guiWidth(), graphics.guiHeight(), graphics.guiWidth(), graphics.guiHeight(), color);
		//?} else {
		/*// No blit overload below 1.21.11 takes a colour, so the tint is set on the graphics
		// object first — which is exactly how vanilla's own Gui.renderTextureOverlay tints
		// on this version. Blend has to be armed by hand (the legacy 14-arg innerBlit ends
		// with an unconditional RenderSystem.disableBlend(), R-17's blend-state hazard).
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		graphics.setColor(FastColor.ARGB32.red(color) / 255.0F, FastColor.ARGB32.green(color) / 255.0F,
				FastColor.ARGB32.blue(color) / 255.0F, FastColor.ARGB32.alpha(color) / 255.0F);
		graphics.blit(TEXTURE, 0, 0, 0.0F, 0.0F,
				graphics.guiWidth(), graphics.guiHeight(), graphics.guiWidth(), graphics.guiHeight());
		graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
		*///?}
	}
}
