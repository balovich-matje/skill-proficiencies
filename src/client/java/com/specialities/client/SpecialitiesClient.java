package com.specialities.client;

import com.specialities.Specialities;
import com.specialities.SkillUpdatePayload;

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

		HudElementRegistry.attachElementAfter(VanillaHudElements.EXPERIENCE_LEVEL,
				Specialities.id("skill_xp_bar"), SkillXpHudBar::render);

		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (!(screen instanceof InventoryScreen) && !(screen instanceof CreativeModeInventoryScreen)) {
				return;
			}

			Button button = Button.builder(Component.literal("S"),
							b -> client.gui.setScreen(new SkillsScreen(screen)))
					.bounds(0, 0, 20, 20)
					.tooltip(Tooltip.create(Component.translatable("screen.specialities.skills")))
					.build();
			anchorButton((AbstractContainerScreen<?>) screen, button);
			Screens.getWidgets(screen).add(button);

			// The recipe book shifts leftPos without re-running init, so keep the
			// button glued to the panel's top-right corner every tick.
			ScreenEvents.afterTick(screen).register(
					s -> anchorButton((AbstractContainerScreen<?>) s, button));
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
}
