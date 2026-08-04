package com.specialities.client.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import com.specialities.client.SkillXpHudBar;
import com.specialities.client.SpecialitiesClient;
import com.specialities.client.StealthVignette;

import net.minecraftforge.client.gui.overlay.ForgeGui;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.PlayerRideableJumping;

/**
 * THE R-11 DECISION FOR {@code 1.20.1-forge}, and it is neither of the three options R-11
 * listed. This is a PER-NODE OVERRIDE source file — it lives under
 * {@code versions/1.20.1-forge/src/}, so it exists on this node and nowhere else, which is why
 * it can name {@code net.minecraftforge} without any exclusion glob (the globs in the three
 * node scripts only cover {@code com/specialities/client/Forge*.java}, one package up).
 *
 * <p><b>WHY THE SHARED {@code GuiMixin} CANNOT BE USED HERE, measured.</b>
 * {@code net.minecraftforge.client.gui.overlay.ForgeGui extends Gui} and Forge installs it as
 * the live {@code Minecraft.gui}. Reading {@code ForgeGui.java} out of
 * {@code forge-1.20.1-47.4.22-sources.jar}: it OVERRIDES {@code Gui.render(GuiGraphics,float)}
 * and never calls {@code super} — the override throws away vanilla's whole HUD body and
 * dispatches {@code GuiOverlayManager.getOverlays()} instead. The entire class contains exactly
 * FOUR {@code super.} calls: {@code renderPortalOverlay}, {@code renderExperienceBar},
 * {@code renderJumpMeter} and {@code onDisconnected}. So of {@code GuiMixin}'s five handlers on
 * this Minecraft version:
 *
 * <ul>
 * <li>{@code @Inject} TAIL of {@code Gui.render} — APPLIES BUT NEVER RUNS. The skill XP bar and
 *     the stealth vignette would simply never be drawn.</li>
 * <li>{@code @WrapMethod Gui.renderPlayerHealth} — never runs; {@code ForgeGui} reimplements
 *     hearts, armor, food and air as its own {@code renderHealth}/{@code renderArmor}/
 *     {@code renderFood}/{@code renderAir}, called from {@code VanillaGuiOverlay}.</li>
 * <li>{@code @WrapMethod Gui.renderVehicleHealth} — never runs; {@code renderHealthMount}
 *     replaces it.</li>
 * <li>{@code @WrapMethod Gui.renderExperienceBar} / {@code Gui.renderJumpMeter} — these two DO
 *     run, through the two {@code super.} calls above.</li>
 * </ul>
 *
 * <p>Listing the shared {@code GuiMixin} on this node would therefore produce a HALF-SHIFTED
 * HUD — experience bar and jump meter raised, hearts/armor/food/air/mount not, and no mod HUD
 * at all — with no error anywhere. That is why this node's
 * {@code specialities.client.mixins.json} override does not list it, and lists this class
 * instead. {@code GuiMixin}'s own body is version-gated, not loader-gated, so its class is
 * still compiled into the jar; it is simply not in any config, so Mixin never loads it.
 *
 * <p><b>WHY THIS IS A MIXIN AND NOT {@code RegisterGuiOverlaysEvent}.</b> R-11 is right that
 * {@code RegisterGuiOverlaysEvent} has no {@code wrapLayer} — it has only
 * {@code registerAbove}/{@code registerBelow}/{@code registerAboveAll}/{@code registerBelowAll},
 * which control ORDER and nothing else, so it cannot raise a vanilla element at all. Its two
 * cousins were both considered and rejected with reasons:
 *
 * <ul>
 * <li><b>The {@code ForgeGui.leftHeight}/{@code rightHeight} fields</b> (public, reset to 39 at
 *     the top of every {@code render}, and read as {@code height - leftHeight} by the five
 *     survival elements). Bumping them by 7 would raise all five in one line — but
 *     {@code VanillaGuiOverlay.ITEM_NAME} and {@code ForgeGui.renderRecordOverlay} also read
 *     {@code Math.max(leftHeight, rightHeight)}, so the held-item name and the record overlay
 *     would move too. Neither of their 26.x counterparts (HELD_ITEM_TOOLTIP, and no counterpart
 *     at all) is in the raised set, so that is a per-node divergence in what HUD_SHIFT means.
 *     Rejected for that reason, not for cost.</li>
 * <li><b>Cancel-and-redraw via {@code RenderGuiOverlayEvent$Pre}</b> — a second copy of five
 *     vanilla draw calls, i.e. exactly the "balance logic duplicated per node" that conventions
 *     §5a forbids for mixins and that applies just as well to UI.</li>
 * </ul>
 *
 * <p>So: {@code HUD_SHIFT = 7} is honoured on this node, the published Archetypes collision
 * contract is intact, and no note in {@code ../archetypes/notes/design.md} is needed. What is
 * raised here is element-for-element the same set the other six nodes raise — the seven
 * {@code VanillaHudElements} ids of 26.x map onto seven {@code ForgeGui} methods:
 *
 * <pre>
 *   INFO_BAR          -&gt; renderJumpMeter(PlayerRideableJumping,GuiGraphics,I)V
 *                     -&gt; renderExperience(ILnet/minecraft/client/gui/GuiGraphics;)V
 *   EXPERIENCE_LEVEL  -&gt; renderExperience as well: there is no renderExperienceLevel on
 *                        1.20.1, the bar and the level number are drawn by the one method
 *                        (measured in GuiMixin's header), and ForgeGui.renderExperience is the
 *                        only caller of it here.
 *   HEALTH_BAR        -&gt; renderHealth(IILnet/minecraft/client/gui/GuiGraphics;)V
 *   ARMOR_BAR         -&gt; renderArmor(Lnet/minecraft/client/gui/GuiGraphics;II)V   [param order!]
 *   FOOD_BAR          -&gt; renderFood(IILnet/minecraft/client/gui/GuiGraphics;)V
 *   AIR_BAR           -&gt; renderAir(IILnet/minecraft/client/gui/GuiGraphics;)V
 *   MOUNT_HEALTH      -&gt; renderHealthMount(IILnet/minecraft/client/gui/GuiGraphics;)V
 * </pre>
 *
 * <p>Every one of those seven descriptors was read from {@code javap} of
 * {@code forge-1.20.1-47.4.22-universal.jar}'s {@code ForgeGui}, not from the sources jar and
 * not from memory. {@code renderArmor} really does take {@code (GuiGraphics, int, int)} while
 * its four neighbours take {@code (int, int, GuiGraphics)}.
 *
 * <p><b>Visibility is matched deliberately.</b> {@code @WrapMethod} moves the original body to
 * {@code $mixinextras$wrapped$…} and gives the handler the target's name and descriptor, and
 * {@code VanillaGuiOverlay} calls all seven of these {@code invokevirtual} on a public or
 * protected member. A {@code private} handler could therefore leave a private
 * {@code ForgeGui.renderHealth} behind and turn a working HUD into an
 * {@code IllegalAccessError}. So each handler carries its target's access modifier, unlike the
 * shared {@code GuiMixin}, whose five targets are all private {@code Gui} methods.
 *
 * <p><b>The two mod draws stay a TAIL inject</b>, as on every other node below 1.21.11 — moved
 * from {@code Gui.render} to {@code ForgeGui.render}, which is the override that actually runs.
 * {@code ForgeGui.render} has two returns (the early one when {@code RenderGuiEvent.Pre} is
 * cancelled), and {@code @At("TAIL")} takes the LAST — after the overlay loop and after
 * {@code RenderGuiEvent.Post} — so a cancelled HUD correctly hides the mod's elements too.
 *
 * <p><b>UNVERIFIED AT RUNTIME.</b> Nothing in this file has been seen to draw: there is no
 * Minecraft client in this workspace, and a dedicated server never loads {@code ForgeGui}. What
 * IS verified is static: the seven descriptors against the universal jar, and the SRG names in
 * the SHIPPED jar's annotations via {@code javap -v} (conventions §5h — Architectury Loom
 * remaps mixin annotations in place and emits no refmap, so what is in the jar is literally
 * what Mixin will use). First in-game launch on this node remains the real gate and it is the
 * user's.
 */
@Mixin(ForgeGui.class)
public abstract class ForgeGuiMixin {
	/**
	 * SHARED — one implementation of the shift for all seven wrapped elements (§5a). The AMOUNT
	 * comes from {@code SpecialitiesClient.hudShift()}, the same shared decision the other three
	 * raise paths read: the configured {@code hudShiftAmount} while the skill XP bar occupies the
	 * row, {@code 0} once the client's {@code showXpHudBar} hides it, so hiding the bar does not
	 * leave seven pixels of nothing above the hotbar. Read per call, not cached, so the toggle
	 * applies on the next frame.
	 *
	 * <p>A shift of {@code 0} calls the original with the pose stack untouched — no push, no
	 * translate, no pop. {@code hudShiftAmount: 0} is the issue-#4 compat escape for players
	 * running another HUD-moving mod, and it has to mean this mod performs no pose manipulation
	 * at all; on this node that also keeps us out of {@code ForgeGui}'s own
	 * {@code leftHeight}/{@code rightHeight} bookkeeping the same way it always did.
	 */
	@Unique
	private void specialities$shifted(final GuiGraphics graphics, final Operation<Void> original,
			final Object... args) {
		int shift = SpecialitiesClient.hudShift();

		if (shift == 0) {
			original.call(args);
			return;
		}

		graphics.pose().pushPose();
		graphics.pose().translate(0.0F, (float) -shift, 0.0F);
		original.call(args);
		graphics.pose().popPose();
	}

	@Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;F)V", at = @At("TAIL"))
	private void specialities$renderSkillHud(final GuiGraphics graphics, final float partialTick,
			final CallbackInfo ci) {
		SkillXpHudBar.render(graphics, partialTick);
		StealthVignette.render(graphics, partialTick);
	}

	// Raises the bar AND the level number: on 1.20.1 `Gui.renderExperienceBar` draws both, and
	// this is its only caller on this loader.
	@WrapMethod(method = "renderExperience(ILnet/minecraft/client/gui/GuiGraphics;)V")
	protected void specialities$raiseExperience(final int x, final GuiGraphics graphics,
			final Operation<Void> original) {
		this.specialities$shifted(graphics, original, x, graphics);
	}

	@WrapMethod(method = "renderJumpMeter(Lnet/minecraft/world/entity/PlayerRideableJumping;Lnet/minecraft/client/gui/GuiGraphics;I)V")
	public void specialities$raiseJumpMeter(final PlayerRideableJumping jumpable, final GuiGraphics graphics,
			final int x, final Operation<Void> original) {
		this.specialities$shifted(graphics, original, jumpable, graphics, x);
	}

	@WrapMethod(method = "renderHealth(IILnet/minecraft/client/gui/GuiGraphics;)V")
	public void specialities$raiseHealth(final int width, final int height, final GuiGraphics graphics,
			final Operation<Void> original) {
		this.specialities$shifted(graphics, original, width, height, graphics);
	}

	// NOTE the parameter order — GuiGraphics FIRST here, unlike its four neighbours.
	@WrapMethod(method = "renderArmor(Lnet/minecraft/client/gui/GuiGraphics;II)V")
	protected void specialities$raiseArmor(final GuiGraphics graphics, final int width, final int height,
			final Operation<Void> original) {
		this.specialities$shifted(graphics, original, graphics, width, height);
	}

	@WrapMethod(method = "renderFood(IILnet/minecraft/client/gui/GuiGraphics;)V")
	public void specialities$raiseFood(final int width, final int height, final GuiGraphics graphics,
			final Operation<Void> original) {
		this.specialities$shifted(graphics, original, width, height, graphics);
	}

	@WrapMethod(method = "renderAir(IILnet/minecraft/client/gui/GuiGraphics;)V")
	protected void specialities$raiseAir(final int width, final int height, final GuiGraphics graphics,
			final Operation<Void> original) {
		this.specialities$shifted(graphics, original, width, height, graphics);
	}

	@WrapMethod(method = "renderHealthMount(IILnet/minecraft/client/gui/GuiGraphics;)V")
	protected void specialities$raiseHealthMount(final int width, final int height, final GuiGraphics graphics,
			final Operation<Void> original) {
		this.specialities$shifted(graphics, original, width, height, graphics);
	}
}
