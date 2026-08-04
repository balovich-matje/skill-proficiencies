package com.specialities.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.specialities.Specialities;
import com.specialities.platform.Platform;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads/saves {@link SpecialitiesConfig} to {@code config/skill-proficiencies.json}
 * using the Gson bundled with Minecraft — no external dependency, so the config
 * works whether or not Mod Menu / Cloth Config are installed. The in-game GUI
 * (when Cloth is present) mutates the held instance and calls {@link #save()}.
 *
 * <p><b>The file NAME follows the published mod name; the mod ID does not.</b> GitHub issue #2:
 * the project is "Skill Proficiencies" on Modrinth while the file was {@code specialities.json},
 * which nothing in a big pack connects back to the mod. So the file is named after the display
 * name and {@link #FILE_NAME} is the only place that spelling lives. The mod id, the namespace,
 * the attachment keys and the source directory stay {@code specialities} FOREVER — changing
 * those would orphan every world's skill data (merged CLAUDE.md's hard rule).
 *
 * <p>{@link #load()} therefore MIGRATES: a pre-1.7.0 {@code specialities.json} with no new file
 * beside it is renamed in place, contents untouched, with one INFO line. Nothing is ever parsed
 * away or defaulted over — if the rename fails (read-only config dir, a lock) the old file is
 * still READ and the new one is written from it, so a settings loss needs both the rename and
 * the write to fail.
 */
public final class ConfigManager {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** Named after the published mod, not the mod id — see the class javadoc. */
	private static final String FILE_NAME = "skill-proficiencies.json";

	/** What every release up to 1.6.1 wrote. Migrated away from on first load, never written. */
	private static final String LEGACY_FILE_NAME = "specialities.json";

	private static final Path PATH = Platform.INSTANCE.configDir().resolve(FILE_NAME);
	private static final Path LEGACY_PATH = Platform.INSTANCE.configDir().resolve(LEGACY_FILE_NAME);

	private static SpecialitiesConfig instance = new SpecialitiesConfig();

	private ConfigManager() {
	}

	/** The live config. Never null; the fields are mutable so the GUI can edit in place. */
	public static SpecialitiesConfig get() {
		return instance;
	}

	/** Read the file if present (else keep defaults), sanitize, then rewrite so new fields are persisted. */
	public static void load() {
		Path source = migrate();

		if (source != null) {
			try (Reader reader = Files.newBufferedReader(source)) {
				SpecialitiesConfig loaded = GSON.fromJson(reader, SpecialitiesConfig.class);
				if (loaded != null) {
					instance = loaded;
				}
			} catch (IOException | JsonParseException e) {
				Specialities.LOGGER.warn("Couldn't read {} — using defaults", source, e);
			}
		}

		save();
	}

	/**
	 * Settle which file this launch reads, renaming the legacy one onto the new name if that is
	 * what is on disk. Returns the path to read, or {@code null} when neither file exists (first
	 * launch — defaults, then {@link #save()} writes the new name).
	 *
	 * <p>Order matters: the NEW name wins whenever it exists, so a stale {@code specialities.json}
	 * left behind by a failed rename or by a hand-copied instance can never overwrite newer
	 * settings.
	 */
	private static Path migrate() {
		if (Files.exists(PATH)) {
			return PATH;
		}

		if (!Files.exists(LEGACY_PATH)) {
			return null;
		}

		try {
			Files.move(LEGACY_PATH, PATH);
			Specialities.LOGGER.info("Renamed config {} to {} — same settings, name now matches the mod",
					LEGACY_FILE_NAME, FILE_NAME);
			return PATH;
		} catch (IOException e) {
			// Not fatal and deliberately not a rethrow: the old file is still perfectly readable,
			// and save() will write the new name from it a moment later.
			Specialities.LOGGER.warn("Couldn't rename {} to {} — reading the old file in place",
					LEGACY_PATH, FILE_NAME, e);
			return LEGACY_PATH;
		}
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
