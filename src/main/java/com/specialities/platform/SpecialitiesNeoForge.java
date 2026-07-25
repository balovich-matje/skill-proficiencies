package com.specialities.platform;

import com.specialities.Specialities;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * The common {@code javafml} entrypoint for the {@code 1.21.1-neoforge} node — the
 * counterpart of {@code Specialities implements ModInitializer} on the Fabric nodes.
 *
 * <p>ARTIFACT PROVENANCE ({@code loader-4.0.43-sources.jar}):
 *
 * <ul>
 * <li>{@code net/neoforged/fml/common/Mod.java} — {@code @Retention(RUNTIME) @Target(TYPE)},
 *     members {@code String value()} and
 *     {@code Dist[] dist() default { Dist.CLIENT, Dist.DEDICATED_SERVER }}. This class takes
 *     the default, so it runs on both sides — which is what makes it the analogue of the
 *     Fabric {@code main} entrypoint.</li>
 * <li>{@code net/neoforged/fml/javafmlmod/FMLModContainer.java} {@code constructMod()} — a mod
 *     class "must have exactly 1 public constructor", and the injection whitelist is literally
 *     {@code Map.of(IEventBus.class, eventBus, ModContainer.class, this,
 *     FMLModContainer.class, this, Dist.class, FMLLoader.getDist())}, with
 *     {@code "Mod constructor has unsupported argument …"} for anything else. So
 *     {@code (IEventBus, ModContainer)} is exactly what may be asked for, and the
 *     {@code IEventBus} handed over is the MOD bus ({@code FMLModContainer.eventBus}). There
 *     is no {@code FMLJavaModLoadingContext} on NeoForge 21.1 — that class is LexForge-only,
 *     which is the one difference from {@code SpecialitiesForge}'s constructor.</li>
 * </ul>
 *
 * <p><b>Why this class lives in {@code com.specialities.platform}</b>: both
 * {@code NeoForgeSkillStore.initialize()} and {@code NeoForgeNet.registerClientbound()} need
 * the MOD event bus, and {@code SkillStore.initialize()} / {@code Net.registerClientbound()}
 * take no arguments — changing those signatures would touch every node. Stashing the bus in a
 * package-private static keeps the whole hand-off inside {@code platform/}, so the public
 * surface stays "three interfaces plus one loader entrypoint per non-Fabric node"
 * (conventions §5g). The alternative,
 * {@code ModLoadingContext.get().getActiveContainer().getEventBus()}, is an ambient lookup
 * whose validity during construction was not verified from the artifact, so it was not used.
 *
 * <p><b>The client half is a SECOND entrypoint, in {@code src/client}, and it has to be.</b>
 * The sinks {@code NeoForgeNet} needs are installed by
 * {@code com.specialities.client.NeoForgeClientEvents}, which is annotated
 * {@code @Mod(value = MOD_ID, dist = Dist.CLIENT)}. This class cannot do it: it is in
 * {@code src/main}, and {@code src/main} cannot see {@code src/client} on any node — a
 * source-set direction, not a classpath question (measured, conventions §5g). {@code Mod}'s
 * own javadoc pins the ordering that makes the split safe: "a mod loaded with the
 * {@code javafml} language loader may have multiple entrypoints. <b>Entrypoints for all
 * {@code dist}s are always run before entrypoints for a single {@code dist}</b>" — so this
 * constructor runs first, the client one second, and both finish before
 * {@code RegisterPayloadHandlersEvent} is posted (that event is a mod-bus event fired after
 * construction, and {@code registerClientbound()} only adds a listener for it).
 */
@Mod(Specialities.MOD_ID)
public final class SpecialitiesNeoForge {
	private static IEventBus modEventBus;

	public SpecialitiesNeoForge(final IEventBus modBus, final ModContainer container) {
		modEventBus = modBus;

		// The SAME body every other node runs, in the same order (the init order is a Tier-2
		// assertion, design §7): ConfigManager.load -> SkillTypes.pullEntrypoints ->
		// SkillStore.initialize -> ModItems.initialize -> Net.registerClientbound ->
		// SkillEvents.register -> SkillCommands.register. `onInitialize()` is a plain public
		// method here rather than a ModInitializer override — see Specialities' own header for
		// why the declaration forks instead of the body being extracted.
		new Specialities().onInitialize();
	}

	/**
	 * The MOD event bus, for the two seams and the creative-tab helper that register on it.
	 * Never null after construction; reading it earlier is a coding error, hence the throw
	 * rather than a null return that would fail somewhere less obvious.
	 */
	static IEventBus modEventBus() {
		if (modEventBus == null) {
			throw new IllegalStateException("The NeoForge mod event bus was read before mod construction");
		}

		return modEventBus;
	}
}
