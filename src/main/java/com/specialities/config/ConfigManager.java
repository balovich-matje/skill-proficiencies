package com.specialities.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.specialities.Specialities;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads/saves {@link SpecialitiesConfig} to {@code config/specialities.json}
 * using the Gson bundled with Minecraft — no external dependency, so the config
 * works whether or not Mod Menu / Cloth Config are installed. The in-game GUI
 * (when Cloth is present) mutates the held instance and calls {@link #save()}.
 */
public final class ConfigManager {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("specialities.json");

	private static SpecialitiesConfig instance = new SpecialitiesConfig();

	private ConfigManager() {
	}

	/** The live config. Never null; the fields are mutable so the GUI can edit in place. */
	public static SpecialitiesConfig get() {
		return instance;
	}

	/** Read the file if present (else keep defaults), sanitize, then rewrite so new fields are persisted. */
	public static void load() {
		if (Files.exists(PATH)) {
			try (Reader reader = Files.newBufferedReader(PATH)) {
				SpecialitiesConfig loaded = GSON.fromJson(reader, SpecialitiesConfig.class);
				if (loaded != null) {
					instance = loaded;
				}
			} catch (IOException | JsonParseException e) {
				Specialities.LOGGER.warn("Couldn't read {} — using defaults", PATH, e);
			}
		}

		save();
	}

	public static void save() {
		instance.sanitize();
		try {
			Files.createDirectories(PATH.getParent());
			try (Writer writer = Files.newBufferedWriter(PATH)) {
				GSON.toJson(instance, writer);
			}
		} catch (IOException e) {
			Specialities.LOGGER.warn("Couldn't write {}", PATH, e);
		}
	}
}
