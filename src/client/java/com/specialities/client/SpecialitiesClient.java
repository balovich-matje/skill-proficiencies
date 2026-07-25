package com.specialities.client;

import com.specialities.Specialities;
import com.specialities.SkillUpdatePayload;
import com.specialities.StealthStatePayload;

import com.specialities.client.mixin.AbstractContainerScreenAccessor;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
// The whole `client.rendering.v1.hud` package (HudElementRegistry / VanillaHudElements /
// HudElement) arrived in fabric-rendering-v1 16.x, i.e. at 1.21.11. 0.116.14+1.21.1 ships
// fabric-rendering-v1 3.x, which has no `hud` subpackage at all — checked by compiling this
// node, not by reading a changelog. Below 1.21.11 the HUD is done by mixin instead: see
// `client/mixin/GuiMixin`, which draws the two elements and applies HUD_SHIFT by wrapping
// the five vanilla bottom-HUD draws.
//? if >=1.21.11 {
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
//?}
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;

//? if >=1.21.11 {
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
//?}

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class SpecialitiesClient implements ClientModInitializer {
	/**
	 * How far the vanilla bottom HUD (XP bar, level number, hearts, food, armor,
	 * air, mount health) is raised to make room for the skill XP bar, which takes
	 * over the vanilla XP bar's original position.
	 */
	public static final int HUD_SHIFT = 7;

	@Override
	public void onInitializeClient() {
		// These two receivers stay here rather than behind the `Net` seam (design §2
		// puts a registerClientReceivers() on it). Measured reason: `Net` and its
		// implementation live in `src/main`, and `net.minecraft.client` is not on that
		// source set's compile classpath — a probe calling
		// ClientPlayNetworking.registerGlobalReceiver from `src/main` fails with
		// "cannot access Minecraft / class file for net.minecraft.client.Minecraft not
		// found". Keeping the split source sets is design §1.4, so the seam cannot
		// reach here. This file is Fabric-only anyway (it *implements*
		// ClientModInitializer), which is the same reason §2 gives for the HUD hook
		// not being a seam. Below 1.20.5 these two lines fork in place, like the
		// registration in FabricNet.
		ClientPlayNetworking.registerGlobalReceiver(SkillUpdatePayload.TYPE,
				(payload, context) -> SkillHudState.onUpdate(payload, context.client()));
		ClientPlayNetworking.registerGlobalReceiver(StealthStatePayload.TYPE,
				(payload, context) -> StealthVignette.onUpdate(payload, context.client()));

		// Nothing of this exists below 1.21.11 — the HUD is entirely GuiMixin's job there,
		// and the client mixin config gates that class in on exactly those nodes.
		//? if >=1.21.11 {
		Identifier[] raisedElements = {
				VanillaHudElements.INFO_BAR,
				VanillaHudElements.EXPERIENCE_LEVEL,
				VanillaHudElements.HEALTH_BAR,
				VanillaHudElements.ARMOR_BAR,
				VanillaHudElements.FOOD_BAR,
				VanillaHudElements.AIR_BAR,
				VanillaHudElements.MOUNT_HEALTH
		};

		for (Identifier element : raisedElements) {
			HudElementRegistry.replaceElement(element, SpecialitiesClient::raised);
		}

		// Anchor to HOTBAR, not EXPERIENCE_LEVEL: vanilla skips the experience
		// level element entirely when the player has 0 XP levels, and attached
		// elements are skipped with their anchor.
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR,
				Specialities.id("skill_xp_bar"), SkillXpHudBar::render);

		// Under the hotbar/health, alongside the vanilla vignette and overlays.
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS,
				Specialities.id("stealth_vignette"), StealthVignette::render);
		//?}

		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (screen instanceof InventoryScreen) {
				// Survival inventory: a bookmark on the top edge, clear of
				// the effect list vanilla draws to the panel's right. The
				// recipe book shifts leftPos without re-running init, so it
				// re-anchors every tick.
				// 26.2 moved screen management off Minecraft onto the Gui object.
				BookmarkTab tab = new BookmarkTab(Component.translatable("screen.specialities.skills"),
						/*? if >=26.2 {*/() -> client.gui.setScreen(new SkillsScreen(screen)));
						/*?} else *///() -> client.setScreen(new SkillsScreen(screen)));
				anchorTab((AbstractContainerScreen<?>) screen, tab);
				// fabric-screen-api-v1 renamed the accessor: getButtons below 26.1.
				//? if >=26.1 {
				Screens.getWidgets(screen).add(tab);
				//?} else {
				/*Screens.getButtons(screen).add(tab);
				*///?}

				ScreenEvents.afterTick(screen).register(
						s -> anchorTab((AbstractContainerScreen<?>) s, tab));
			} else if (screen instanceof CreativeModeInventoryScreen) {
				// Creative keeps the compact square to the panel's right:
				// the top edge belongs to the real creative tabs, and
				// creative shows no effect list to collide with.
				Button button = Button.builder(Component.literal("S"),
								/*? if >=26.2 {*/b -> client.gui.setScreen(new SkillsScreen(screen)))
								/*?} else *///b -> client.setScreen(new SkillsScreen(screen)))
						.bounds(0, 0, 20, 20)
						.tooltip(Tooltip.create(Component.translatable("screen.specialities.skills")))
						.build();
				anchorButton((AbstractContainerScreen<?>) screen, button);
				//? if >=26.1 {
				Screens.getWidgets(screen).add(button);
				//?} else {
				/*Screens.getButtons(screen).add(button);
				*///?}

				ScreenEvents.afterTick(screen).register(
						s -> anchorButton((AbstractContainerScreen<?>) s, button));
			}
		});
	}

	// HudElement only exists from 1.21.11 up, so this whole helper does. Written as a flat
	// three-branch chain rather than a `>=26.1` block nested inside a `>=1.21.11` one: a
	// directive inside an already-disabled branch needs the `*` -> `^` marker escalation
	// (conventions §4) and that is the case that fails silently when it is got wrong.
	//? if >=26.1 {
	private static HudElement raised(final HudElement element) {
		// The lambda's parameter types are inferred from HudElement, so only the
		// delegating call names the hook: extractRenderState on 26.x, render below.
		// pose() is org.joml.Matrix3x2fStack on both sides.
		return (graphics, deltaTracker) -> {
			graphics.pose().pushMatrix();
			graphics.pose().translate(0.0F, (float) -HUD_SHIFT);
			element.extractRenderState(graphics, deltaTracker);
			graphics.pose().popMatrix();
		};
	}
	//?} elif >=1.21.11 {
	/*private static HudElement raised(final HudElement element) {
		return (graphics, deltaTracker) -> {
			graphics.pose().pushMatrix();
			graphics.pose().translate(0.0F, (float) -HUD_SHIFT);
			element.render(graphics, deltaTracker);
			graphics.pose().popMatrix();
		};
	}
	*///?}

	private static void anchorButton(final AbstractContainerScreen<?> screen, final Button button) {
		AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor) screen;
		button.setX(accessor.specialities$getLeftPos() + accessor.specialities$getImageWidth() + 4);
		button.setY(accessor.specialities$getTopPos());
	}

	private static void anchorTab(final AbstractContainerScreen<?> screen, final BookmarkTab tab) {
		AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor) screen;
		tab.setX(accessor.specialities$getLeftPos() + 4);
		tab.setY(accessor.specialities$getTopPos() - BookmarkTab.HEIGHT);
	}
}
