package com.specialities;

import com.specialities.command.SkillCommands;
import com.specialities.config.ConfigManager;
import com.specialities.platform.Net;
import com.specialities.platform.SkillStore;
import com.specialities.skills.SkillEvents;
import com.specialities.skills.SkillTypes;

import net.fabricmc.api.ModInitializer;

import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Specialities implements ModInitializer {
	public static final String MOD_ID = "specialities";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ConfigManager.load();
		// Other mods' skills come in before anything can touch player state.
		SkillTypes.pullEntrypoints();
		SkillStore.INSTANCE.initialize();
		ModItems.initialize();

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
