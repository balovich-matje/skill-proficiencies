package com.specialities.client.mixin;

// This whole file exists only BELOW 1.21.11, where fabric-rendering-v1 has no `hud` package
// (0.116.14+1.21.1 ships fabric-rendering-v1 3.x) and there is therefore no
// HudElementRegistry to attach an element to or to wrap a vanilla one with. Design §5's
// Stage 4a line: `HUD_SHIFT` needs a `Gui.render(GuiGraphics, DeltaTracker)V` mixin, which
// R-11 flags as a decision rather than a port. The decision taken here is the one that keeps
// the published Archetypes collision contract (`HUD_SHIFT = 7`) intact and element-for-
// element faithful to what `SpecialitiesClient.raised(...)` does on the newer nodes:
//
//   * the two mod elements are DRAWN at TAIL of Gui.render, through the same shared
//     SkillXpHudBar.render / StealthVignette.render the other nodes call;
//   * the raised vanilla elements are WRAPPED one by one, translating the pose by
//     -HUD_SHIFT around each. On 26.x seven VanillaHudElements ids are replaced
//     (INFO_BAR, EXPERIENCE_LEVEL, HEALTH_BAR, ARMOR_BAR, FOOD_BAR, AIR_BAR,
//     MOUNT_HEALTH); on 1.21.1 those same seven live in five vanilla methods, all
//     verified in the mojmap and in `javap -c` of the obfuscated Gui (`fhy`):
//
//       INFO_BAR         -> renderJumpMeter(PlayerRideableJumping,GuiGraphics,I)V  (1658)
//                        -> renderExperienceBar(GuiGraphics,I)V                   (1723)
//       EXPERIENCE_LEVEL -> renderExperienceLevel(GuiGraphics,DeltaTracker)V       (1782)
//       HEALTH/ARMOR/    -> renderPlayerHealth(GuiGraphics)V                       (2231)
//         FOOD/AIR          (armor, hearts, food and air bubbles are all drawn inside it)
//       MOUNT_HEALTH     -> renderVehicleHealth(GuiGraphics)V                      (2927)
//
//     `renderSelectedItemName` and `renderEffects` are deliberately NOT wrapped: their 26.x
//     counterparts (HELD_ITEM_TOOLTIP, STATUS_EFFECTS) are not in the raised set either.
//     Neither is the hotbar itself, which is what forbids wrapping the enclosing
//     `renderHotbarAndDecorations` instead of its five children.
//
// This class is listed in `versions/1.21.1-fabric/src/client/resources/
// specialities.client.mixins.json` — a per-node override of the shared client mixin config,
// not a `//?` block inside it: `//?` does nothing in `.json` files (see UseDurationMixin's
// header for the measurement). The shared config is therefore untouched, which also keeps
// the 26.x jars' resource bytes where they were.
//
// Mechanism notes for whoever audits this:
//   * @WrapMethod (MixinExtras >= 0.4.0) is why `mod.loader_floor` is `>=0.16.3` on this
//     node in the first place; PlayerMixin's MeleeSwing gate already uses it.
//   * Full descriptors, never bare names: every one of these five is `a`/`b`/`c`/`l`/`n`
//     after remap and `a` alone is overloaded ~20 times on Gui, so a bare name would be
//     ambiguous at apply time.
//   * The five handlers all delegate to ONE shared @Unique implementation, per conventions
//     §5a — the annotation is what forks, the shift never is.
//   * TAIL of `Gui.render` sits after vanilla's `RenderSystem.disableDepthTest()` (the
//     method is a three-call wrapper around the LayeredDraw: enableDepthTest, layers.render,
//     disableDepthTest), so the two mod draws are unclipped by depth. Both manage their own
//     blend state.
//
// THE 1.20.1 ARM, and it is SMALLER, not bigger — four wrapped methods instead of five:
//
//   * `Gui.render` takes `(GuiGraphics, float)` there; `DeltaTracker` is 1.21+. That method
//     is the whole HUD rather than a LayeredDraw wrapper, and it has exactly ONE `return`
//     (offset 1537, right after `renderSavingIndicator`), so TAIL is unambiguous and always
//     reached.
//   * there is NO `renderExperienceLevel` on 1.20.1 — measured, not assumed:
//     `renderExperienceBar(GuiGraphics,I)V` draws the bar AND the level number, the second
//     under its own `expLevel` profiler section with the familiar five `drawString` calls
//     (four black offsets and the green centre, `javap -c` of `eow.a(eox,int)`). So wrapping
//     the bar raises the number with it and the seven raised VanillaHudElements ids still map
//     completely: INFO_BAR and EXPERIENCE_LEVEL -> renderExperienceBar + renderJumpMeter,
//     HEALTH/ARMOR/FOOD/AIR -> renderPlayerHealth, MOUNT_HEALTH -> renderVehicleHealth.
//   * `pose()` is a PoseStack, so the shift is pushPose/translate/popPose.
//   * fabric-rendering-v1 3.0.9 DOES have `HudRenderCallback.onHudRender(GuiGraphics,float)`,
//     and it would serve for the two mod DRAWS. It is deliberately not used: it cannot raise
//     a vanilla element, so the mixin has to exist for HUD_SHIFT regardless, and splitting
//     the HUD across two mechanisms would leave the draw order dependent on event-vs-mixin
//     ordering for no gain.
//? if >=1.21.11 {
//?} elif >=1.21 {
/*import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import com.specialities.client.SkillXpHudBar;
import com.specialities.client.SpecialitiesClient;
import com.specialities.client.StealthVignette;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.PlayerRideableJumping;

@Mixin(Gui.class)
public abstract class GuiMixin {
	// SHARED — one implementation for all five wrapped elements. The shift AMOUNT is decided in
	// SpecialitiesClient.hudShift(), shared with the other three raise paths: HUD_SHIFT while the
	// skill XP bar occupies the row, 0 once the client's showXpHudBar hides it, so no gap is left
	// above the hotbar. Read per call, so the toggle applies on the next frame.
	@Unique
	private void specialities$shifted(final GuiGraphics graphics, final Operation<Void> original,
			final Object... args) {
		graphics.pose().pushPose();
		graphics.pose().translate(0.0F, (float) -SpecialitiesClient.hudShift(), 0.0F);
		original.call(args);
		graphics.pose().popPose();
	}

	@Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V",
			at = @At("TAIL"))
	private void specialities$renderSkillHud(final GuiGraphics graphics, final DeltaTracker deltaTracker,
			final CallbackInfo ci) {
		SkillXpHudBar.render(graphics, deltaTracker);
		StealthVignette.render(graphics, deltaTracker);
	}

	@WrapMethod(method = "renderExperienceBar(Lnet/minecraft/client/gui/GuiGraphics;I)V")
	private void specialities$raiseExperienceBar(final GuiGraphics graphics, final int x,
			final Operation<Void> original) {
		this.specialities$shifted(graphics, original, graphics, x);
	}

	@WrapMethod(method = "renderJumpMeter(Lnet/minecraft/world/entity/PlayerRideableJumping;Lnet/minecraft/client/gui/GuiGraphics;I)V")
	private void specialities$raiseJumpMeter(final PlayerRideableJumping jumpable, final GuiGraphics graphics,
			final int x, final Operation<Void> original) {
		this.specialities$shifted(graphics, original, jumpable, graphics, x);
	}

	@WrapMethod(method = "renderExperienceLevel(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V")
	private void specialities$raiseExperienceLevel(final GuiGraphics graphics, final DeltaTracker deltaTracker,
			final Operation<Void> original) {
		this.specialities$shifted(graphics, original, graphics, deltaTracker);
	}

	@WrapMethod(method = "renderPlayerHealth(Lnet/minecraft/client/gui/GuiGraphics;)V")
	private void specialities$raisePlayerHealth(final GuiGraphics graphics, final Operation<Void> original) {
		this.specialities$shifted(graphics, original, graphics);
	}

	@WrapMethod(method = "renderVehicleHealth(Lnet/minecraft/client/gui/GuiGraphics;)V")
	private void specialities$raiseVehicleHealth(final GuiGraphics graphics, final Operation<Void> original) {
		this.specialities$shifted(graphics, original, graphics);
	}
}
*///?} else {
/*import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import com.specialities.client.SkillXpHudBar;
import com.specialities.client.SpecialitiesClient;
import com.specialities.client.StealthVignette;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.PlayerRideableJumping;

@Mixin(Gui.class)
public abstract class GuiMixin {
	// SHARED — one implementation for all four wrapped elements. Shift amount from
	// SpecialitiesClient.hudShift(), as in the >=1.21 arm above.
	@Unique
	private void specialities$shifted(final GuiGraphics graphics, final Operation<Void> original,
			final Object... args) {
		graphics.pose().pushPose();
		graphics.pose().translate(0.0F, (float) -SpecialitiesClient.hudShift(), 0.0F);
		original.call(args);
		graphics.pose().popPose();
	}

	@Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;F)V", at = @At("TAIL"))
	private void specialities$renderSkillHud(final GuiGraphics graphics, final float tickDelta,
			final CallbackInfo ci) {
		SkillXpHudBar.render(graphics, tickDelta);
		StealthVignette.render(graphics, tickDelta);
	}

	// Raises the bar AND the level number — they are drawn by the same method here.
	@WrapMethod(method = "renderExperienceBar(Lnet/minecraft/client/gui/GuiGraphics;I)V")
	private void specialities$raiseExperienceBar(final GuiGraphics graphics, final int x,
			final Operation<Void> original) {
		this.specialities$shifted(graphics, original, graphics, x);
	}

	@WrapMethod(method = "renderJumpMeter(Lnet/minecraft/world/entity/PlayerRideableJumping;Lnet/minecraft/client/gui/GuiGraphics;I)V")
	private void specialities$raiseJumpMeter(final PlayerRideableJumping jumpable, final GuiGraphics graphics,
			final int x, final Operation<Void> original) {
		this.specialities$shifted(graphics, original, jumpable, graphics, x);
	}

	@WrapMethod(method = "renderPlayerHealth(Lnet/minecraft/client/gui/GuiGraphics;)V")
	private void specialities$raisePlayerHealth(final GuiGraphics graphics, final Operation<Void> original) {
		this.specialities$shifted(graphics, original, graphics);
	}

	@WrapMethod(method = "renderVehicleHealth(Lnet/minecraft/client/gui/GuiGraphics;)V")
	private void specialities$raiseVehicleHealth(final GuiGraphics graphics, final Operation<Void> original) {
		this.specialities$shifted(graphics, original, graphics);
	}
}
*///?}
