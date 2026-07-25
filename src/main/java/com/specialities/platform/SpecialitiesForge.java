package com.specialities.platform;

import com.specialities.Specialities;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.RegisterEvent;

import net.minecraft.core.registries.Registries;

/**
 * The {@code javafml} entrypoint for the {@code 1.20.1-forge} node.
 *
 * <p>ARTIFACT PROVENANCE — the constructor shape is NOT the NeoForge one, and getting it wrong
 * is a hard crash at mod construction, so it was read out of
 * {@code javafmllanguage-1.20.1-47.4.22-sources.jar},
 * {@code net/minecraftforge/fml/javafmlmod/FMLModContainer.java}:
 *
 * <pre>
 *   constructor = modClass.getDeclaredConstructor(context.getClass());   // FMLJavaModLoadingContext
 *   … catch (NoSuchMethodException …) { constructor = modClass.getDeclaredConstructor(); }
 * </pre>
 *
 * So LexForge 47.4.22 accepts exactly TWO constructor shapes — {@code (FMLJavaModLoadingContext)}
 * or no-arg — and nothing else. It is not the NeoForge injection whitelist
 * ({@code IEventBus}/{@code ModContainer}/{@code Dist}), and {@code IEventBus} is NOT
 * injectable here. Taking the context argument is also the non-deprecated path:
 * {@code FMLJavaModLoadingContext.get()} carries {@code @Deprecated(forRemoval = true)} with
 * the message "use {@code FMLJavaModLoadingContext} in your mod constructor".
 *
 * <p>{@code getModEventBus()} returns the MOD bus. The GAME bus is the static
 * {@code MinecraftForge.EVENT_BUS}; the two are different objects and registering on the wrong
 * one is a silent no-op, which is why {@code ForgeSkillStore.initialize()} names both
 * explicitly.
 *
 * <p><b>It calls {@code onInitialize()}, not an extracted {@code init()}.</b> The prep plan
 * drafted a shared {@code Specialities.init()}; the shared-tree commit that landed before this
 * node deliberately did not create one, because adding a method and rewriting
 * {@code onInitialize} would have moved bytecode on five Fabric nodes that had to stay
 * instruction-identical. On the loader axis {@code onInitialize()} is a plain public instance
 * method with no interface behind it, and this is its one caller.
 *
 * <p><b>WHY COMMON INIT DOES NOT RUN IN THIS CONSTRUCTOR — measured on a real server boot, and
 * the largest single surprise on this node.</b> Calling {@code onInitialize()} from CONSTRUCT
 * crashes the server:
 *
 * <pre>
 *   java.lang.IllegalStateException: Registry is already frozen
 *     at net.minecraftforge.registries.NamespacedWrapper.createIntrusiveHolder
 *     at net.minecraft.world.item.Item.&lt;init&gt;
 *     at com.specialities.items.SkillBookItem.&lt;init&gt;
 *     at com.specialities.ModItems.registerBook
 *     at com.specialities.ModItems.&lt;clinit&gt;
 *     at com.specialities.Specialities.onInitialize
 * </pre>
 *
 * <p>Forge keeps the vanilla registries FROZEN outside the registration window, and note where
 * it throws: not at the {@code Registry.register} call but inside {@code Item.<init>}, which
 * asks the item registry for an intrusive holder. So on this loader the thirty
 * {@code SkillBookItem} objects cannot even be CONSTRUCTED at mod-construct time, which rules
 * out every fix that keeps {@code ModItems}' static fields where they are and only moves the
 * {@code register} call. Fabric has no such window and this is why the five Fabric nodes never
 * saw it.
 *
 * <p>The window is exact, and it was read out of {@code GameData.postRegisterEvents} in the
 * universal jar: for each registry in turn Forge calls {@code forgeRegistry.unfreeze()}, posts
 * {@code RegisterEvent} for that registry, then {@code freeze()}s it again. So this class defers
 * the whole shared init into {@code RegisterEvent} for {@code Registries.ITEM}.
 *
 * <p>Three things that makes this SAFE rather than merely working:
 * <ul>
 * <li><b>The init ORDER is untouched</b>, and it is a Tier-2 assertion (design §7):
 *     {@code ConfigManager.load} → {@code SkillTypes.pullEntrypoints} →
 *     {@code SkillStore.INSTANCE.initialize} → {@code ModItems} →
 *     {@code Net.INSTANCE.registerClientbound} → {@code SkillEvents.register} →
 *     {@code SkillCommands.register}. Only the TIMING of the whole block moves; there is still
 *     exactly one copy of the sequence and no shared file changed.</li>
 * <li><b>Nothing in that block is too late at LOAD_REGISTRIES.</b> Game-bus listeners may be
 *     added any time before a world loads; {@code NetworkRegistry} is locked only at
 *     NETWORK_LOCK, after COMPLETE; {@code BuildCreativeModeTabContentsEvent} and
 *     {@code RegisterCommandsEvent} both fire much later; {@code ModList} is populated before
 *     CONSTRUCT, so {@code ForgePlatform.skillProviders()} still sees every mod.</li>
 * <li><b>The one piece that WOULD be too late is registered here instead.</b>
 *     {@code RegisterCapabilitiesEvent} is posted at INJECT_CAPABILITIES, one state BEFORE
 *     LOAD_REGISTRIES, and an unregistered capability fails silently rather than loudly. Hence
 *     the {@code ForgeSkillStore.registerCapabilities} call below, and hence that method
 *     existing at all — its javadoc has the full state list.</li>
 * </ul>
 *
 * <p>The alternative — teach {@code ModItems} to build its thirty items lazily and register them
 * through a per-loader registration event — is the RIGHT long-term shape and is cross-cutting:
 * it rewrites a shared file that five Fabric nodes are required to stay instruction-identical
 * on, and NeoForge freezes its registries the same way, so {@code 1.21.1-neoforge} needs the
 * same answer. Reported, not done here.
 *
 * <p><b>What {@code 1.21.1-neoforge} does instead, and why this node does NOT copy it.</b> That
 * node keeps the shared init at CONSTRUCT and defers only {@code ModItems.initialize()}, through
 * the {@code neoforge} arm of a three-arm chain in {@code Specialities.onInitialize()}. It has
 * to: {@code NeoForgeSkillStore.initialize()} calls {@code ATTACHMENTS.register(modEventBus)},
 * which ADDS a {@code RegisterEvent} listener, and doing that from inside a {@code RegisterEvent}
 * dispatch mutates the {@code ListenerList} the bus is iterating. This node has no
 * {@code DeferredRegister} at all — capabilities are not a registry on 1.20.1 and the items are
 * registered directly — so the whole-init deferral has no such hazard here, and it is the shape
 * that was booted green. The {@code forge} arm of that chain is therefore a plain
 * {@code ModItems.initialize()}.
 *
 * <p>Same placement rationale as {@code SpecialitiesNeoForge}: this lives in
 * {@code com.specialities.platform} so the bus hand-off to {@code ForgeSkillStore} stays
 * package-private, and so the whole file is caught by the node scripts' {@code Forge*} /
 * {@code SpecialitiesForge} exclusion globs on the nodes that must not compile it.
 */
@Mod(Specialities.MOD_ID)
public final class SpecialitiesForge {
	private static IEventBus modEventBus;

	private static boolean initialized;

	public SpecialitiesForge(final FMLJavaModLoadingContext context) {
		IEventBus modBus = context.getModEventBus();
		modEventBus = modBus;

		// CONSTRUCT phase, and it has to be: RegisterCapabilitiesEvent is posted before the
		// registration window the init below waits for. See ForgeSkillStore.registerCapabilities.
		ForgeSkillStore.registerCapabilities(modBus);

		// The SAME body the Fabric ModInitializer runs, in the same order — there is only one
		// copy of it — deferred into the item registration window. See the class javadoc.
		modBus.addListener((RegisterEvent event) -> {
			if (!Registries.ITEM.equals(event.getRegistryKey()) || initialized) {
				return;
			}

			initialized = true;
			new Specialities().onInitialize();
		});
	}

	/** The MOD event bus. Never null after construction. */
	static IEventBus modEventBus() {
		if (modEventBus == null) {
			throw new IllegalStateException("The Forge mod event bus was read before mod construction");
		}

		return modEventBus;
	}
}
