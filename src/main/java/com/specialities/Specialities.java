package com.specialities;

import com.specialities.command.SkillCommands;
import com.specialities.config.ConfigManager;
import com.specialities.platform.Net;
import com.specialities.platform.SkillStore;
import com.specialities.skills.SkillEvents;
import com.specialities.skills.SkillTypes;

//? if fabric {
import net.fabricmc.api.ModInitializer;
//?} elif neoforge {
/*import com.specialities.platform.NeoForgeEvents;
*///?}

import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// THE ENTRYPOINT SPLIT, and why it is a declaration fork rather than an extracted `init()`.
//
// Only the class declaration and the `@Override` fork; the init body itself is shared by every
// loader, unchanged. `implements ModInitializer` and `@Override` are the two Fabric-only tokens
// in this file — `ModInitializer` does not exist on NeoForge or LexForge, and an `@Override`
// that overrides nothing is a compile error.
//
// On the loader axis `onInitialize()` is therefore a plain public instance method, and each
// loader's `@Mod` entrypoint calls `new Specialities().onInitialize()` from its constructor.
// The obvious alternative — extract the body into a shared `public static void init()` and have
// `onInitialize()` delegate — was NOT taken at this stage: it adds a method and rewrites
// `onInitialize` on five Fabric nodes that are required to stay instruction-identical while the
// loader axis lands. Renaming it to `init()` is a follow-up for after those nodes are re-gated,
// not something to do silently.
//
// The init ORDER below is a Tier-2 assertion (design §7): ConfigManager.load ->
// SkillTypes.pullEntrypoints -> SkillStore.INSTANCE.initialize -> ModItems ->
// Net.INSTANCE.registerClientbound -> SkillEvents.register -> SkillCommands.register. Every
// loader runs exactly this sequence, in this order, because there is only one copy of it.
//? if fabric {
public class Specialities implements ModInitializer {
//?} else {
/*public class Specialities {
*///?}
	public static final String MOD_ID = "specialities";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	//? if fabric {
	@Override
	//?}
	public void onInitialize() {
		ConfigManager.load();
		// Other mods' skills come in before anything can touch player state.
		SkillTypes.pullEntrypoints();
		SkillStore.INSTANCE.initialize();
		// THE ITEM-REGISTRY WINDOW — the one init step whose TIMING forks, and the only reason
		// it does is that `Item.<init>` itself asks the ITEM registry for an intrusive holder.
		// Measured on both loaders, and it is a hard boot crash, not a warning:
		//   IllegalStateException: Registry is already frozen
		//     at MappedRegistry.createIntrusiveHolder <- Item.<init> <- SkillBookItem.<init>
		//     <- ModItems.registerBook <- ModItems.<clinit>
		// So the thirty SkillBookItem objects cannot be CONSTRUCTED at mod-construct time on a
		// loader that freezes its registries — moving only the `register` call is not enough,
		// the whole class initialiser has to run inside the registration window.
		//
		// NEOFORGE arms that window and returns; the effect lands in RegisterEvent(ITEM). The
		// rest of init deliberately stays at CONSTRUCT here, because NeoForgeSkillStore binds a
		// DeferredRegister to the mod bus and `DeferredRegister.register(bus)` adds a
		// RegisterEvent listener — adding one WHILE RegisterEvent is being dispatched mutates
		// the ListenerList being iterated (bus-8.0.5 EventBus.addToListeners ->
		// getListenerList(eventType)), so the whole-init deferral the forge node uses is not
		// available on this loader.
		// FORGE calls it straight, because SpecialitiesForge defers this entire method into
		// RegisterEvent(ITEM) already (it has no DeferredRegister anywhere, so it has no such
		// hazard) and its capability registration is hoisted to CONSTRUCT instead.
		// Both workarounds are retired by the same follow-up: lazy item construction plus a
		// per-loader register, which rewrites this shared file and is blocked today only by the
		// five Fabric nodes' instruction-identity gate.
		//? if fabric {
		ModItems.initialize();
		//?} elif neoforge {
		/*NeoForgeEvents.registerItems(ModItems::initialize);
		*///?} elif forge {
		/*ModItems.initialize();
		*///?}

		Net.INSTANCE.registerClientbound();

		SkillEvents.register();
		SkillCommands.register();

		LOGGER.info("Skills mod initialized");
	}

	// `fromNamespaceAndPath` is a 1.21 addition (it and `withDefaultNamespace` replaced the
	// two constructors when those were made private). Below that the constructor is the
	// API. The class name is handled by the controller's replacement rule, so the legacy
	// branch says `Identifier` and is generated as `ResourceLocation`.
	public static Identifier id(String path) {
		//? if >=1.21 {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
		//?} else {
		/*return new Identifier(MOD_ID, path);
		*///?}
	}
}
