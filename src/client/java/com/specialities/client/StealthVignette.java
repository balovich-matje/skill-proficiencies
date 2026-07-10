package com.specialities.client;

import com.specialities.Specialities;
import com.specialities.StealthStatePayload;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
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

	private StealthVignette() {
	}

	public static void onUpdate(final StealthStatePayload payload, final Minecraft client) {
		if (payload.state() == StealthStatePayload.DETECTED && state != StealthStatePayload.DETECTED) {
			flashStartMs = Util.getMillis();
		}

		state = payload.state();
	}

	public static void render(final GuiGraphicsExtractor graphics, final DeltaTracker deltaTracker) {
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
			draw(graphics, ARGB.colorFromFloat(darkAlpha, DARK_RED, DARK_GREEN, DARK_BLUE));
		}

		// Detection flash: light vignette fading out.
		long flashAge = now - flashStartMs;
		if (flashAge >= 0 && flashAge < FLASH_DURATION_MS) {
			float fade = 1.0F - flashAge / (float) FLASH_DURATION_MS;
			float alpha = LIGHT_MAX_ALPHA * Mth.square(fade);
			draw(graphics, ARGB.colorFromFloat(alpha, 1.0F, 1.0F, 1.0F));
		}
	}

	private static void draw(final GuiGraphicsExtractor graphics, final int color) {
		graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, 0, 0, 0.0F, 0.0F,
				graphics.guiWidth(), graphics.guiHeight(), graphics.guiWidth(), graphics.guiHeight(), color);
	}
}
