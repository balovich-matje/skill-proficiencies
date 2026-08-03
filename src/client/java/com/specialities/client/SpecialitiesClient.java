package com.specialities.client;

import com.specialities.Specialities;
import com.specialities.SkillUpdatePayload;
import com.specialities.config.ConfigManager;
//? if >=1.20.5 {
//?} else {
/*import com.specialities.SkillsFullPayload;
import com.specialities.platform.SkillStore;
*///?}
import com.specialities.StealthStatePayload;

import com.specialities.client.mixin.AbstractContainerScreenAccessor;

// LOADER AXIS: this file keeps its class, its HUD_SHIFT constant (GuiMixin reads it) and every
// line of UI logic on every loader. Only the WIRING forks — the entrypoint interface, the two
// payload receivers, and the four fabric-api screen calls — and each loader's arm hands the
// same shared lambda to a client-side helper the node's own agent writes. A client-side helper
// is unavoidable rather than a seam member: `com.specialities.platform` lives in `src/main`,
// which cannot see `net.minecraft.client` at all (measured, conventions §5g).
//? if fabric {
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
//?} elif neoforge {
/*import com.specialities.platform.Net;
*///?} elif forge {
/*import com.specialities.platform.Net;
*///?}
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
// The loader arms need no import for their helper: `NeoForgeClientEvents` / `ForgeClientEvents`
// live in this same package (they have to — a client helper cannot live behind the seam).
//? if fabric {
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
//?}

//? if >=1.21.11 {
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
//?}

// Needed wherever the receiver handler does not get a context object that already carries the
// client: below 1.20.5 on Fabric, and on both loader-axis loaders (their sink consumers take
// the payload and nothing else). `fabric &&` scopes the fabric-api boundary to Fabric; it is
// not a new one.
//? if fabric && >=1.20.5 {
//?} else {
/*import net.minecraft.client.Minecraft;
*///?}
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

// Only the declaration forks — see the same shape on `Specialities`. On the loader axis
// `onInitializeClient()` is a plain public method the node's client-setup hook calls.
//? if fabric {
public class SpecialitiesClient implements ClientModInitializer {
//?} else {
/*public class SpecialitiesClient {
*///?}
	/**
	 * How far the vanilla bottom HUD (XP bar, level number, hearts, food, armor,
	 * air, mount health) is raised to make room for the skill XP bar, which takes
	 * over the vanilla XP bar's original position.
	 *
	 * <p>Published constant, part of the Archetypes collision contract
	 * ({@code ../archetypes/notes/design.md}). It is the SIZE of the raise and never moves;
	 * whether the raise applies on a given frame is {@link #hudShift()}.
	 */
	public static final int HUD_SHIFT = 7;

	/**
	 * THE ONE GATE FOR THE SKILL XP BAR, read live every frame — a client-local display
	 * preference ({@code config/specialities.json}, key {@code showXpHudBar}), never a synced
	 * balance knob. See {@code SpecialitiesConfig#showXpHudBar}: nothing in this mod puts config
	 * on the wire, so a server cannot hide a connected client's bar.
	 *
	 * <p>Every node's draw path funnels through {@code SkillXpHudBar.render}, which asks this
	 * first — the >=1.21.11 Fabric {@code HudElementRegistry} element, the {@code GuiMixin} TAIL
	 * inject below it, NeoForge's {@code registerAbove} layer and the Forge node's
	 * {@code ForgeGuiMixin} TAIL inject all call that same shared method. So the check lives once
	 * for all seven nodes and no per-loader draw hook needs to know about it.
	 */
	public static boolean hudBarVisible() {
		return ConfigManager.get().showXpHudBar;
	}

	/**
	 * How far to raise the vanilla bottom HUD THIS FRAME: {@link #HUD_SHIFT} while the skill XP
	 * bar occupies that row, {@code 0} when it does not — otherwise hiding the bar would leave a
	 * seven-pixel gap of nothing above the hotbar.
	 *
	 * <p>The four raise implementations (26.x/1.21.11 {@code HudElementRegistry.replaceElement}
	 * via {@link #raised}, {@code GuiMixin.specialities$shifted}, {@code NeoForgeClientEvents
	 * .raise}, {@code ForgeGuiMixin.specialities$shifted}) exist separately only because the pose
	 * API and the hook shape differ per node; all four read this one method, so the DECISION is
	 * single-implementation the way conventions §5a requires of balance logic.
	 *
	 * <p>The Archetypes carve-out that also returned {@code HUD_SHIFT} on
	 * {@code isModLoaded("archetypes")} was RETIRED 2026-08-01: Archetypes >=1.2.0 reads this
	 * method live per frame ({@code compat/SpecialitiesBridge#hudShift}) instead of hardcoding 7
	 * on presence, so its rows now follow the toggle down and nothing needs stranding.
	 */
	public static int hudShift() {
		return hudBarVisible() ? HUD_SHIFT : 0;
	}

	//? if fabric {
	@Override
	//?}
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
		//
		// And below 1.20.5 they do fork: 0.92.11's typed receiver is
		// `PlayPacketHandler.receive(T, LocalPlayer, PacketSender)` instead of
		// `(payload, context)`. It runs on the client thread just like the modern one —
		// `ClientPlayNetworking$1.receive` checks `Minecraft.isSameThread()` and otherwise
		// hands the call to `Minecraft.execute` (read off the module's bytecode) — so
		// touching client state straight out of the handler is as safe there as here.
		//
		// The third receiver is design R-03's other half: that node has no attachment
		// sync, so the client's own copy of the skills attachment is maintained from the
		// wire. The join payload seeds the whole map here and every later gain patches one
		// entry in SkillHudState.onUpdate, which keeps `SkillManager.get(minecraft.player)`
		// — what the HUD bar and the skills screen both read — correct without either of
		// them knowing the difference.
		//
		// ON THE LOADER AXIS THERE IS NOTHING TO REGISTER HERE, and that is not the seam being
		// widened by the back door: NeoForge's `playToClient(TYPE, CODEC, handler)` and Forge's
		// `registerMessage(index, class, encoder, decoder, handler)` take the handler as an
		// argument to the ONE registration call, which has to run in common init because a
		// dedicated server must register the type too. So the client hands its consumers DOWN
		// through `Net.clientReceivers` and the registration still happens in
		// `Net.registerClientbound()`. Nothing in `src/main` names a client type; this line is
		// the only direction that crosses.
		//
		// It must run BEFORE the platform's registration event fires. Both loaders' client-setup
		// hooks run after mod construction, so the node's client entrypoint installs the sinks
		// from the mod constructor, not from client setup — the node's own agent owns that
		// ordering and must assert it on the Tier-2 boot.
		//? if fabric && >=1.20.5 {
		ClientPlayNetworking.registerGlobalReceiver(SkillUpdatePayload.TYPE,
				(payload, context) -> SkillHudState.onUpdate(payload, context.client()));
		ClientPlayNetworking.registerGlobalReceiver(StealthStatePayload.TYPE,
				(payload, context) -> StealthVignette.onUpdate(payload, context.client()));
		//?} elif neoforge {
		/*Net.INSTANCE.clientReceivers(
				payload -> SkillHudState.onUpdate(payload, Minecraft.getInstance()),
				payload -> StealthVignette.onUpdate(payload, Minecraft.getInstance()));
		*///?} elif forge {
		/*Net.INSTANCE.clientReceivers(
				payload -> SkillHudState.onUpdate(payload, Minecraft.getInstance()),
				payload -> StealthVignette.onUpdate(payload, Minecraft.getInstance()),
				payload -> SkillStore.INSTANCE.setSkills(Minecraft.getInstance().player, payload.skills()));
		*///?} else {
		/*ClientPlayNetworking.registerGlobalReceiver(SkillUpdatePayload.TYPE,
				(payload, player, responseSender) -> SkillHudState.onUpdate(payload, Minecraft.getInstance()));
		ClientPlayNetworking.registerGlobalReceiver(StealthStatePayload.TYPE,
				(payload, player, responseSender) -> StealthVignette.onUpdate(payload, Minecraft.getInstance()));
		ClientPlayNetworking.registerGlobalReceiver(SkillsFullPayload.TYPE,
				(payload, player, responseSender) -> SkillStore.INSTANCE.setSkills(player, payload.skills()));
		*///?}

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

		// Registration only — every line of the tab/button construction below, including its two
		// `>=26.2` screen-management forks, is shared. A loader helper's listener takes the same
		// four parameters in the same order (Minecraft, Screen, int, int) and must fire AFTER the
		// screen's widgets exist.
		//? if fabric {
		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
		//?} elif neoforge {
		/*NeoForgeClientEvents.afterScreenInit((client, screen, scaledWidth, scaledHeight) -> {
		*///?} elif forge {
		/*ForgeClientEvents.afterScreenInit((client, screen, scaledWidth, scaledHeight) -> {
		*///?}
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
				// fabric-screen-api-v1 renamed the accessor: getButtons below 26.1. The loader
				// helper adds the widget to the screen's own renderable+event lists; it is the
				// one call here with no vanilla equivalent, since `Screen.addRenderableWidget`
				// is protected.
				//? if fabric && >=26.1 {
				Screens.getWidgets(screen).add(tab);
				//?} elif fabric {
				/*Screens.getButtons(screen).add(tab);
				*///?} elif neoforge {
				/*NeoForgeClientEvents.addWidget(screen, tab);
				*///?} elif forge {
				/*ForgeClientEvents.addWidget(screen, tab);
				*///?}

				//? if fabric {
				ScreenEvents.afterTick(screen).register(
						s -> anchorTab((AbstractContainerScreen<?>) s, tab));
				//?} elif neoforge {
				/*NeoForgeClientEvents.afterScreenTick(screen,
						s -> anchorTab((AbstractContainerScreen<?>) s, tab));
				*///?} elif forge {
				/*ForgeClientEvents.afterScreenTick(screen,
						s -> anchorTab((AbstractContainerScreen<?>) s, tab));
				*///?}
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
				//? if fabric && >=26.1 {
				Screens.getWidgets(screen).add(button);
				//?} elif fabric {
				/*Screens.getButtons(screen).add(button);
				*///?} elif neoforge {
				/*NeoForgeClientEvents.addWidget(screen, button);
				*///?} elif forge {
				/*ForgeClientEvents.addWidget(screen, button);
				*///?}

				//? if fabric {
				ScreenEvents.afterTick(screen).register(
						s -> anchorButton((AbstractContainerScreen<?>) s, button));
				//?} elif neoforge {
				/*NeoForgeClientEvents.afterScreenTick(screen,
						s -> anchorButton((AbstractContainerScreen<?>) s, button));
				*///?} elif forge {
				/*ForgeClientEvents.afterScreenTick(screen,
						s -> anchorButton((AbstractContainerScreen<?>) s, button));
				*///?}
			}
		});
	}

	// HudElement only exists from 1.21.11 up, so this whole helper does. Written as a flat
	// three-branch chain rather than a `>=26.1` block nested inside a `>=1.21.11` one: a
	// directive inside an already-disabled branch needs the `*` -> `^` marker escalation
	// (conventions §4) and that is the case that fails silently when it is got wrong.
	// The wrapper is installed once at init and the shift is read INSIDE the returned lambda, so
	// hudShift() is evaluated per frame and the toggle takes effect immediately — no re-register,
	// no restart. Same property holds for the other three raise paths.
	//? if >=26.1 {
	private static HudElement raised(final HudElement element) {
		// The lambda's parameter types are inferred from HudElement, so only the
		// delegating call names the hook: extractRenderState on 26.x, render below.
		// pose() is org.joml.Matrix3x2fStack on both sides.
		return (graphics, deltaTracker) -> {
			graphics.pose().pushMatrix();
			graphics.pose().translate(0.0F, (float) -hudShift());
			element.extractRenderState(graphics, deltaTracker);
			graphics.pose().popMatrix();
		};
	}
	//?} elif >=1.21.11 {
	/*private static HudElement raised(final HudElement element) {
		return (graphics, deltaTracker) -> {
			graphics.pose().pushMatrix();
			graphics.pose().translate(0.0F, (float) -hudShift());
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
