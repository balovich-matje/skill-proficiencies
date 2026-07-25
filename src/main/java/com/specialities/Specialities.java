package com.specialities;

import com.specialities.command.SkillCommands;
import com.specialities.config.ConfigManager;
import com.specialities.skills.SkillEvents;
import com.specialities.skills.SkillTypes;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

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
		ModAttachments.initialize();
		ModItems.initialize();

		// fabric-api renamed the play-phase payload registries: `clientboundPlay()`
		// / `serverboundPlay()` on the 26.x pins (fabric-networking-api-v1 6.3.1 and
		// 6.3.3), `playS2C()` / `playC2S()` in 0.141.5 (5.1.6, the 1.21.11 pin).
		// Only the accessor name moved — both return
		// PayloadTypeRegistry<RegistryFriendlyByteBuf>, so the payload types and
		// codecs are unchanged and the wire ids stay frozen.
		//? if >=26.1 {
		PayloadTypeRegistry.clientboundPlay().register(SkillUpdatePayload.TYPE, SkillUpdatePayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(StealthStatePayload.TYPE, StealthStatePayload.CODEC);
		//?} else {
		/*PayloadTypeRegistry.playS2C().register(SkillUpdatePayload.TYPE, SkillUpdatePayload.CODEC);
		PayloadTypeRegistry.playS2C().register(StealthStatePayload.TYPE, StealthStatePayload.CODEC);
		*///?}

		SkillEvents.register();
		SkillCommands.register();

		LOGGER.info("Skills mod initialized");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
