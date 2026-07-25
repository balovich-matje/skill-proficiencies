package com.specialities.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;

import com.specialities.Specialities;

import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;

/**
 * The LexForge half of the CLIENT event wiring for the {@code 1.20.1-forge} node — the three
 * fabric-api screen calls {@code SpecialitiesClient} forks on, and nothing else. Every line of
 * tab/button construction and every anchor computation stays shared.
 *
 * <p>This lives in {@code com.specialities.client} rather than behind the
 * {@code com.specialities.platform} seam because it has to: the seam is in {@code src/main},
 * which cannot see {@code net.minecraft.client} on any node (measured, conventions §5g). The
 * file is named after its loader so the node scripts' {@code com/specialities/client/Forge*}
 * exclusion glob keeps it off the six nodes that must not compile it — and {@code Forge*} is
 * anchored, so it does not match {@code NeoForge*}.
 *
 * <p>ARTIFACT PROVENANCE — {@code forge-1.20.1-47.4.22-sources.jar}:
 * <ul>
 * <li>{@code client/event/ScreenEvent.java} — {@code Init.Post extends Init extends
 *     ScreenEvent}, with {@code Screen getScreen()},
 *     {@code List<GuiEventListener> getListenersList()} and
 *     {@code void addListener(GuiEventListener)}.
 *     {@code patches/net/minecraft/client/gui/screens/Screen.java.patch} shows {@code Init.Post}
 *     posted immediately after the screen's own {@code init()} has run, and shows that the
 *     {@code add} consumer it is constructed with is {@code Screen::addEventWidget}, which
 *     appends to BOTH the {@code renderables} and {@code children} lists. That is exactly what
 *     {@code Screens.getWidgets(screen).add(w)} does on Fabric.</li>
 * <li>{@code event/TickEvent.java} — {@code ClientTickEvent extends TickEvent} with the
 *     inherited {@code public final Phase phase}. {@code ScreenEvent} has NO tick member: the
 *     families it declares are {@code Init}, {@code Render}, {@code BackgroundRendered},
 *     {@code RenderInventoryMobEffects}, the mouse and key ones, {@code CharacterTyped},
 *     {@code Opening} and {@code Closing}.</li>
 * </ul>
 *
 * <p><b>Nothing here is registered until {@code SpecialitiesClient} asks for it.</b> There is no
 * {@code initialize()} to forget to call: each method registers what it needs on first use, so
 * the shared call sites in {@code SpecialitiesClient} are the only wiring.
 */
final class ForgeClientEvents {
	/**
	 * The per-screen tick listeners, and the one genuine RE-ROOTING in this file.
	 *
	 * <p>LexForge 1.20.1 has no per-screen tick event (see the {@code ScreenEvent} inventory
	 * above), so {@code ScreenEvents.afterTick(screen)} re-roots onto a single permanent
	 * {@code ClientTickEvent} listener at {@code Phase.END} that dispatches to whatever is
	 * registered for the screen currently open. That fires once per client tick while the screen
	 * is open, which is what {@code Screen.tick()} does, so the re-anchor cadence is unchanged.
	 * {@code ScreenEvent.Render} was rejected: it fires per FRAME, not per tick.
	 *
	 * <p>{@code WeakHashMap} because a Forge bus listener cannot be removed once added — this map
	 * is the only thing holding these lambdas, so a closed screen and its listeners become
	 * collectable.
	 *
	 * <p>Keyed by screen IDENTITY, and CLEARED on every {@code Init.Post} for that screen. Both
	 * matter: an {@code AbstractContainerScreen} re-runs {@code init()} on the same object when
	 * the recipe book is toggled — which is the exact case the re-anchor exists for — so without
	 * the clear each toggle would leave another copy of the same anchor callback behind. Clearing
	 * is also what fabric-api does, since it rebuilds a screen's extensions on every init.
	 */
	private static final Map<Screen, List<Consumer<Screen>>> SCREEN_TICKERS = new WeakHashMap<>();

	/**
	 * The {@code Init.Post} event currently being dispatched, so {@link #addWidget} can reach the
	 * event's {@code add} consumer while being handed only the {@code Screen}.
	 *
	 * <p>Not a trick for its own sake: {@code Screens.getWidgets(screen)} needs nothing but the
	 * screen, so the shared call site passes only the screen, and widening that signature would
	 * fork a line that is otherwise identical on all seven nodes. Written and cleared around one
	 * synchronous dispatch on the client thread; saved and restored rather than nulled, so a
	 * nested screen init could not strand it.
	 */
	private static ScreenEvent.Init currentInit;

	private static boolean tickPumpInstalled;

	private ForgeClientEvents() {
	}

	/**
	 * Fires after a screen's widgets exist, with the same four parameters in the same order
	 * fabric-api's {@code ScreenEvents.AFTER_INIT} passes.
	 *
	 * <p>{@code Init.Post} is posted after {@code Screen.init()}, so the vanilla widgets are
	 * already in the lists — which the shared listener needs, because it reads
	 * {@code leftPos}/{@code topPos} off the container screen to anchor the tab.
	 *
	 * <p>The scaled width and height come from the window rather than off the event, which
	 * carries neither. They are the same numbers {@code Screen.width}/{@code height} hold. The
	 * shared listener does not read them today; passing the real values keeps the signature
	 * honest if it ever does.
	 */
	static void afterScreenInit(final ScreenInitListener listener) {
		MinecraftForge.EVENT_BUS.addListener((ScreenEvent.Init.Post event) -> {
			// See SCREEN_TICKERS: a re-init of the same screen object must not accumulate a
			// second copy of the anchor callback the listener is about to register.
			SCREEN_TICKERS.remove(event.getScreen());

			Minecraft minecraft = Minecraft.getInstance();
			ScreenEvent.Init previous = currentInit;
			currentInit = event;

			try {
				listener.afterScreenInit(minecraft, event.getScreen(),
						minecraft.getWindow().getGuiScaledWidth(),
						minecraft.getWindow().getGuiScaledHeight());
			} finally {
				currentInit = previous;
			}
		});
	}

	/** Registers a per-tick callback for one screen. See {@link #SCREEN_TICKERS}. */
	static void afterScreenTick(final Screen screen, final Consumer<Screen> listener) {
		installTickPump();
		SCREEN_TICKERS.computeIfAbsent(screen, key -> new ArrayList<>()).add(listener);
	}

	/**
	 * Adds a widget to the screen's renderable AND event-listener lists.
	 *
	 * <p>This is the one call in the shared client wiring with no vanilla equivalent —
	 * {@code Screen.addRenderableWidget} is protected — which is why fabric-api has
	 * {@code Screens.getWidgets} at all. {@code ScreenEvent.Init.addListener} routes to
	 * {@code Screen::addEventWidget}, which appends to both lists, so a tab added through it both
	 * draws and takes clicks.
	 */
	static void addWidget(final Screen screen, final GuiEventListener widget) {
		ScreenEvent.Init init = currentInit;

		if (init != null && init.getScreen() == screen) {
			init.addListener(widget);
			return;
		}

		// Should be unreachable: the shared call site only runs inside the afterScreenInit
		// dispatch. Loud rather than silent, because a widget added to nothing is an invisible
		// tab and no error anywhere.
		Specialities.LOGGER.error("addWidget called outside a screen-init dispatch for {}; the widget was dropped",
				screen.getClass().getName());
	}

	private static void installTickPump() {
		if (tickPumpInstalled) {
			return;
		}

		tickPumpInstalled = true;

		MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
			if (event.phase != TickEvent.Phase.END) {
				return;
			}

			Screen screen = Minecraft.getInstance().screen;
			if (screen == null) {
				return;
			}

			List<Consumer<Screen>> tickers = SCREEN_TICKERS.get(screen);
			if (tickers == null) {
				return;
			}

			for (Consumer<Screen> ticker : tickers) {
				ticker.accept(screen);
			}
		});
	}

	/**
	 * The four parameters {@code ScreenEvents.AFTER_INIT} passes, in that order, so the shared
	 * lambda in {@code SpecialitiesClient} infers them and needs no change.
	 */
	@FunctionalInterface
	interface ScreenInitListener {
		void afterScreenInit(Minecraft client, Screen screen, int scaledWidth, int scaledHeight);
	}
}
