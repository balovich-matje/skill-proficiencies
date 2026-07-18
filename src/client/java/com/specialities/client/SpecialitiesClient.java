package com.specialities.client;

import com.specialities.Specialities;
import com.specialities.SkillUpdatePayload;
import com.specialities.StealthStatePayload;

import com.specialities.client.mixin.AbstractContainerScreenAccessor;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;

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
		ClientPlayNetworking.registerGlobalReceiver(SkillUpdatePayload.TYPE,
				(payload, context) -> SkillHudState.onUpdate(payload, context.client()));
		ClientPlayNetworking.registerGlobalReceiver(StealthStatePayload.TYPE,
				(payload, context) -> StealthVignette.onUpdate(payload, context.client()));

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

		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (screen instanceof InventoryScreen) {
				// Survival inventory: a bookmark on the top edge, clear of
				// the effect list vanilla draws to the panel's right. The
				// recipe book shifts leftPos without re-running init, so it
				// re-anchors every tick.
				BookmarkTab tab = new BookmarkTab(Component.translatable("screen.specialities.skills"),
						() -> client.gui.setScreen(new SkillsScreen(screen)));
				anchorTab((AbstractContainerScreen<?>) screen, tab);
				Screens.getWidgets(screen).add(tab);

				ScreenEvents.afterTick(screen).register(
						s -> anchorTab((AbstractContainerScreen<?>) s, tab));
			} else if (screen instanceof CreativeModeInventoryScreen) {
				// Creative keeps the compact square to the panel's right:
				// the top edge belongs to the real creative tabs, and
				// creative shows no effect list to collide with.
				Button button = Button.builder(Component.literal("S"),
								b -> client.gui.setScreen(new SkillsScreen(screen)))
						.bounds(0, 0, 20, 20)
						.tooltip(Tooltip.create(Component.translatable("screen.specialities.skills")))
						.build();
				anchorButton((AbstractContainerScreen<?>) screen, button);
				Screens.getWidgets(screen).add(button);

				ScreenEvents.afterTick(screen).register(
						s -> anchorButton((AbstractContainerScreen<?>) s, button));
			}
		});
	}

	private static HudElement raised(final HudElement element) {
		return (graphics, deltaTracker) -> {
			graphics.pose().pushMatrix();
			graphics.pose().translate(0.0F, (float) -HUD_SHIFT);
			element.extractRenderState(graphics, deltaTracker);
			graphics.pose().popMatrix();
		};
	}

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
