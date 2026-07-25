package com.specialities.client;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.specialities.Specialities;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;

/**
 * The NeoForge client entrypoint for the {@code 1.21.1-neoforge} node, and the client half of
 * the event seam: the three helpers {@code SpecialitiesClient} forks onto
 * ({@link #afterScreenInit}, {@link #afterScreenTick}, {@link #addWidget}) plus the whole HUD
 * wiring. Not one line of UI logic lives here — the bookmark tab, the creative button, the
 * anchoring maths and both HUD draws are all in {@code SpecialitiesClient} /
 * {@code SkillXpHudBar} / {@code StealthVignette}, unforked.
 *
 * <p><b>WHY THIS CLASS IS THE {@code @Mod} ENTRYPOINT and not just a helper.</b> The payload
 * sinks {@code NeoForgeNet} needs must be installed from client code, before
 * {@code RegisterPayloadHandlersEvent} fires. {@code SpecialitiesNeoForge} cannot do it: it is
 * in {@code src/main}, and {@code src/main} cannot see {@code src/client} on any node — a
 * source-set direction, not a classpath question (measured; conventions §5g, the same fact
 * that keeps the two Fabric receivers out of the {@code Net} seam). So the client half needs
 * its own entrypoint, and {@code Mod}'s javadoc ({@code loader-4.0.43-sources.jar},
 * {@code net/neoforged/fml/common/Mod.java}) says the loader supports exactly that: "a mod
 * loaded with the {@code javafml} language loader may have multiple entrypoints. Entrypoints
 * for all {@code dist}s are always run before entrypoints for a single {@code dist}." The
 * common entrypoint therefore runs first, this one second, and both complete before any
 * registration event is posted. Rather than add a class whose only job is one constructor,
 * the client entrypoint IS this helper.
 *
 * <p>{@code Dist.CLIENT} is what keeps a dedicated server from ever loading it —
 * {@code FMLModContainer.constructMod} only instantiates entrypoints whose {@code dist}
 * matches, so no client class is touched server-side.
 *
 * <p><b>Spelling note for the next porter: {@code ResourceLocation}, not {@code Identifier},
 * is correct here and must not be "fixed".</b> The controller's replacement
 * ({@code stonecutter.gradle.kts}) is {@code string(current.parsed >= "1.21.11") {
 * replace("ResourceLocation", "Identifier") }} and it is DIRECTIONAL: on this node the
 * condition is false, so the rewrite runs {@code Identifier -> ResourceLocation} and this
 * spelling is already the generated one. It also matches the artifact this file was written
 * against — {@code RegisterGuiLayersEvent} and {@code VanillaGuiLayers} both say
 * {@code ResourceLocation}.
 */
@Mod(value = Specialities.MOD_ID, dist = Dist.CLIENT)
public final class NeoForgeClientEvents {
	/**
	 * The screen-init listeners {@code SpecialitiesClient} registers. A list rather than a
	 * single slot because {@code Net}'s shape is "register once from init" and a second caller
	 * would otherwise silently replace the first.
	 */
	private static final List<AfterScreenInit> INIT_LISTENERS = new ArrayList<>();

	/**
	 * Per-screen tick callbacks — the re-anchor-every-tick behaviour, which has no direct
	 * NeoForge analogue. See {@link #afterScreenTick} for the lifecycle these two fields
	 * implement.
	 */
	private static final List<Consumer<Screen>> TICK_LISTENERS = new ArrayList<>();

	private static Screen tickScreen;

	/**
	 * The {@code Init.Post} event currently being dispatched, so {@link #addWidget} can reach
	 * its {@code addListener} without the shared caller having to carry the event around.
	 * Non-null only for the duration of one dispatch, all of it on the render thread.
	 */
	private static ScreenEvent.Init.Post currentInit;

	public NeoForgeClientEvents(final IEventBus modBus, final ModContainer container) {
		// RegisterGuiLayersEvent is an IModBusEvent (its class declaration says so), the other
		// two are posted on NeoForge.EVENT_BUS.
		modBus.addListener(RegisterGuiLayersEvent.class, NeoForgeClientEvents::onRegisterGuiLayers);
		NeoForge.EVENT_BUS.addListener(ScreenEvent.Init.Post.class, NeoForgeClientEvents::onScreenInitPost);
		NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, NeoForgeClientEvents::onClientTickPost);

		// The same body the Fabric nodes run from ClientModInitializer.onInitializeClient: it
		// installs the two payload sinks through Net.clientReceivers and registers the screen
		// hooks below. Mod construction is before RegisterPayloadHandlersEvent, which is the
		// ordering NeoForgeNet's javadoc pins down.
		new SpecialitiesClient().onInitializeClient();
	}

	// ------------------------------------------------------------------ HUD

	/**
	 * <b>THE HUD DECISION FOR THIS NODE: {@code RegisterGuiLayersEvent}, NOT {@code GuiMixin} —
	 * and conventions §5c's preference for the mixin is overridden here on measured grounds,
	 * not on taste.</b>
	 *
	 * <p>§5c says a mixin whose target resolves on a platform stays a mixin there. On this node
	 * the five {@code Gui} methods {@code client/mixin/GuiMixin}'s {@code >=1.21} arm names all
	 * still EXIST — so the mixin would apply, all six injectors would satisfy
	 * {@code injectors.defaultRequire: 1}, the build would be green and the boot would be
	 * clean. It would also be <b>silently wrong</b>, because NeoForge patches {@code Gui} to
	 * split the bottom HUD into named layers and two of those five methods are no longer on the
	 * render path. From {@code patches/net/minecraft/client/gui/Gui.java.patch}:
	 *
	 * <ul>
	 * <li>{@code renderPlayerHealth} is annotated {@code @Deprecated // Neo: Split up into
	 *     different layers} and now merely calls {@code renderHealthLevel} /
	 *     {@code renderArmorLevel} / {@code renderFoodLevel} / {@code renderAirLevel}, each of
	 *     which is registered as its OWN layer. Nothing calls {@code renderPlayerHealth} any
	 *     more (its only caller, {@code renderHotbarAndDecorations}, is
	 *     {@code @Deprecated} and equally dead). A {@code @WrapMethod} on it resolves and never
	 *     runs — so hearts, armor, food and air would not be raised, and the skill XP bar would
	 *     draw straight through the hearts.</li>
	 * <li>{@code Gui.render} no longer calls {@code this.layers.render(...)}; the vanilla
	 *     {@code LayeredDraw layers} field is {@code @Deprecated} and "empty and unused", with a
	 *     {@code GuiLayerManager layerManager} in its place. A TAIL inject on {@code render}
	 *     would still draw, but splitting the HUD across a mixin and the layer API would make
	 *     the draw order depend on mixin-vs-event ordering — the same reason §5i refuses to use
	 *     {@code HudRenderCallback} for the two mod draws on 1.20.1.</li>
	 * <li>NeoForge also adds public {@code Gui.leftHeight} / {@code Gui.rightHeight} fields
	 *     (reset to 39 at the top of {@code render}) that every left/right stack element
	 *     positions itself from and then advances. Wrapping the layers translates the pose and
	 *     leaves that bookkeeping untouched, which is what keeps other HUD mods stacking
	 *     correctly above us. R-11 lists those fields as the FORGE node's option; on this node
	 *     they are a reason not to fight the layer system.</li>
	 * </ul>
	 *
	 * <p>So {@code GuiMixin} is deliberately NOT listed in this node's client mixin config.
	 * That config is a per-node override at
	 * {@code versions/1.21.1-neoforge/src/client/resources/specialities.client.mixins.json}
	 * (needed anyway, because the shared one lists {@code UseDurationMixin}, whose target class
	 * is {@code >=1.21.11}); it cannot carry the reasoning, because it is JSON — this javadoc is
	 * the note. Design §1.3's "the HUD is easier than fabric-1.21.1 here" is confirmed — but
	 * only through {@code wrapLayer}.
	 *
	 * <p><b>The mapping is seven Fabric ids onto EIGHT NeoForge layers</b>, because Fabric's
	 * {@code INFO_BAR} is two layers here and NeoForge's {@code PLAYER_HEALTH} is hearts only:
	 *
	 * <pre>
	 * INFO_BAR         -&gt; JUMP_METER + EXPERIENCE_BAR
	 * EXPERIENCE_LEVEL -&gt; EXPERIENCE_LEVEL
	 * HEALTH_BAR       -&gt; PLAYER_HEALTH      (renderHealthLevel: hearts)
	 * ARMOR_BAR        -&gt; ARMOR_LEVEL
	 * FOOD_BAR         -&gt; FOOD_LEVEL
	 * AIR_BAR          -&gt; AIR_LEVEL
	 * MOUNT_HEALTH     -&gt; VEHICLE_HEALTH
	 * </pre>
	 *
	 * {@code SELECTED_ITEM_NAME}, {@code EFFECTS}, {@code CROSSHAIR} and {@code HOTBAR} are
	 * deliberately not wrapped — their 26.x counterparts are not in the raised set either, which
	 * is the same reason {@code GuiMixin} refuses to wrap {@code renderHotbarAndDecorations}
	 * wholesale.
	 *
	 * <p><b>All eight ids are reachable from this event, which is not obvious and was
	 * verified.</b> {@code wrapLayer} walks one FLAT {@code List<NamedLayer>} and throws
	 * {@code IllegalArgumentException("Attempted to wrap layer with id '…', which does not
	 * exist!")} on a miss — a hard client crash if any id were nested. It is not:
	 * {@code GuiLayerManager.add(GuiLayerManager child, BooleanSupplier)} explicitly
	 * "flatten[s] the layers to allow mods to insert layers between vanilla layers", so the
	 * {@code playerHealthComponents} sub-manager that holds {@code PLAYER_HEALTH} /
	 * {@code ARMOR_LEVEL} / {@code FOOD_LEVEL} is spliced into the top-level list that
	 * {@code initModdedLayers()} hands the event.
	 */
	private static void onRegisterGuiLayers(final RegisterGuiLayersEvent event) {
		for (ResourceLocation raised : RAISED_LAYERS) {
			event.wrapLayer(raised, NeoForgeClientEvents::raise);
		}

		// The two mod layers, drawn through the SAME shared render methods every other node
		// calls. LayeredDraw.Layer's functional signature at 1.21.1 is (GuiGraphics,
		// DeltaTracker), which is exactly what SkillXpHudBar.render / StealthVignette.render
		// take on this node (their `>=1.21` arm), so no drawing code and no arithmetic forks
		// here — conventions §5b.
		//
		// The two anchors mirror the 26.x HudElementRegistry.attachElementAfter calls:
		// HOTBAR for the bar (not EXPERIENCE_LEVEL — vanilla skips that element at 0 levels and
		// an attached element is skipped with its anchor) and CAMERA_OVERLAYS for the vignette,
		// which is NeoForge's name for Fabric's MISC_OVERLAYS slot.
		event.registerAbove(VanillaGuiLayers.HOTBAR, Specialities.id("skill_xp_bar"),
				SkillXpHudBar::render);
		event.registerAbove(VanillaGuiLayers.CAMERA_OVERLAYS, Specialities.id("stealth_vignette"),
				StealthVignette::render);
	}

	/** The eight vanilla layers raised by {@code HUD_SHIFT}. See {@link #onRegisterGuiLayers}. */
	private static final ResourceLocation[] RAISED_LAYERS = {
			VanillaGuiLayers.JUMP_METER,
			VanillaGuiLayers.EXPERIENCE_BAR,
			VanillaGuiLayers.EXPERIENCE_LEVEL,
			VanillaGuiLayers.PLAYER_HEALTH,
			VanillaGuiLayers.ARMOR_LEVEL,
			VanillaGuiLayers.FOOD_LEVEL,
			VanillaGuiLayers.AIR_LEVEL,
			VanillaGuiLayers.VEHICLE_HEALTH,
	};

	private static LayeredDraw.Layer raise(final LayeredDraw.Layer inner) {
		return (graphics, deltaTracker) -> {
			// 1.21.1's GuiGraphics.pose() is a com.mojang.blaze3d.vertex.PoseStack, NOT the
			// org.joml.Matrix3x2fStack of >=1.21.11 — so pushPose/translate/popPose with a z
			// argument, exactly as the 1.21.1-fabric GuiMixin's shared helper does. HUD_SHIFT
			// itself is read from the shared constant and is never redefined here: it is part of
			// the published Archetypes collision contract.
			graphics.pose().pushPose();
			graphics.pose().translate(0.0F, (float) -SpecialitiesClient.HUD_SHIFT, 0.0F);
			inner.render(graphics, deltaTracker);
			graphics.pose().popPose();
		};
	}

	// ------------------------------------------------------------------ screens

	/** The listener shape of {@code ScreenEvents.AFTER_INIT}, parameter for parameter. */
	@FunctionalInterface
	public interface AfterScreenInit {
		void afterInit(Minecraft client, Screen screen, int scaledWidth, int scaledHeight);
	}

	/**
	 * {@code ScreenEvents.AFTER_INIT} — contract owed: fire AFTER the screen's widgets exist.
	 *
	 * <p>Met exactly. {@code patches/net/minecraft/client/gui/screens/Screen.java.patch} posts
	 * {@code ScreenEvent.Init.Post} immediately after {@code this.init(); this.setInitialFocus();}
	 * in both {@code init(Minecraft,int,int)} and {@code rebuildWidgets()} — the same two points
	 * fabric-screen-api-v1 fires AFTER_INIT from, so a window resize re-runs it on both loaders.
	 */
	public static void afterScreenInit(final AfterScreenInit listener) {
		INIT_LISTENERS.add(listener);
	}

	/**
	 * Adds a widget to the screen's own renderable + event lists — the one call in
	 * {@code SpecialitiesClient} with no vanilla equivalent, because
	 * {@code Screen.addRenderableWidget} is {@code protected}. Fabric reaches it through
	 * {@code Screens.getWidgets(screen)}; here it is {@code ScreenEvent.Init#addListener}.
	 *
	 * <p>Equivalence verified from the patch rather than from the javadoc: the {@code add}
	 * consumer the event is constructed with is {@code Screen::addEventWidget}, which the same
	 * patch adds as
	 * {@code if (b instanceof Renderable r) renderables.add(r); if (b instanceof NarratableEntry
	 * ne) narratables.add(ne); children.add(b);} — i.e. it is {@code addRenderableWidget} by
	 * another name. {@code AbstractWidget} implements all three interfaces, so both the bookmark
	 * tab and the creative button render, click and narrate.
	 *
	 * <p>The {@code screen} argument is taken for symmetry with the Fabric call and is asserted
	 * against the screen actually being initialised, because passing a different one would
	 * silently drop the widget.
	 */
	public static void addWidget(final Screen screen, final AbstractWidget widget) {
		if (currentInit == null || currentInit.getScreen() != screen) {
			throw new IllegalStateException(
					"addWidget must be called from an afterScreenInit listener, for the screen being initialised");
		}

		currentInit.addListener(widget);
	}

	/**
	 * {@code ScreenEvents.afterTick(screen)} — the re-anchor-every-tick behaviour, which exists
	 * because the recipe book moves {@code AbstractContainerScreen.leftPos} without re-running
	 * {@code init()}.
	 *
	 * <p><b>This is the one client hook with no NeoForge counterpart at all</b>, so it is built
	 * out of {@code ClientTickEvent.Post} plus a screen-identity check. Timing parity, measured:
	 * {@code patches/net/minecraft/client/Minecraft.java.patch} puts
	 * {@code ClientHooks.fireClientTickPost()} as the LAST statement of {@code Minecraft.tick()},
	 * and vanilla calls {@code this.screen.tick()} earlier inside that same method — so this
	 * fires once per client tick, after the screen ticked, before the frame is drawn. Fabric's
	 * {@code afterTick} fires directly after {@code Screen.tick()}; both land in the same tick,
	 * ahead of the same render, and the callback is an idempotent {@code setX}/{@code setY} so
	 * the few statements between them cannot matter.
	 *
	 * <p>Lifecycle, which is the part worth reviewing: fabric-screen-api-v1 hangs its per-screen
	 * event holders off the screen and rebuilds them on every {@code init}, so listeners from a
	 * previous screen — or from a previous {@code init} of the SAME screen after a resize —
	 * never survive. That is reproduced by clearing the list at the top of every
	 * {@code Init.Post} dispatch, and by clearing it again the first tick the recorded screen is
	 * no longer {@code Minecraft.screen} (which is what releases the reference when the player
	 * simply closes the inventory and no new screen opens).
	 */
	public static void afterScreenTick(final Screen screen, final Consumer<Screen> listener) {
		if (currentInit == null || currentInit.getScreen() != screen) {
			throw new IllegalStateException(
					"afterScreenTick must be called from an afterScreenInit listener, for the screen being initialised");
		}

		tickScreen = screen;
		TICK_LISTENERS.add(listener);
	}

	private static void onScreenInitPost(final ScreenEvent.Init.Post event) {
		TICK_LISTENERS.clear();
		tickScreen = null;

		currentInit = event;
		try {
			Minecraft client = Minecraft.getInstance();
			Screen screen = event.getScreen();
			// Fabric hands the listener the SCALED width/height, which is what Screen.width and
			// Screen.height already are (Screen.init(Minecraft, int, int) is called with
			// window.getGuiScaledWidth()/Height()). The shared listener ignores both, but the
			// parameter list has to match.
			for (AfterScreenInit listener : INIT_LISTENERS) {
				listener.afterInit(client, screen, screen.width, screen.height);
			}
		} finally {
			currentInit = null;
		}
	}

	private static void onClientTickPost(final ClientTickEvent.Post event) {
		if (TICK_LISTENERS.isEmpty()) {
			return;
		}

		Screen current = Minecraft.getInstance().screen;
		if (current == null || current != tickScreen) {
			TICK_LISTENERS.clear();
			tickScreen = null;
			return;
		}

		for (Consumer<Screen> listener : TICK_LISTENERS) {
			listener.accept(current);
		}
	}
}
