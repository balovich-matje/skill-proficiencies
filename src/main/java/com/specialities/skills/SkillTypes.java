package com.specialities.skills;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.specialities.Specialities;
import com.specialities.api.SkillType;
import com.specialities.api.SkillsEntrypoint;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Every skill the engine knows: the fifteen built-ins in enum order, then
 * whatever other mods contributed through the {@code specialities:skills}
 * entrypoint, in mod-load order. That combined order is what the skills
 * screen shows, so it must be deterministic — which entrypoints are, given
 * the same mod set on both sides.
 */
public final class SkillTypes {
	private static final Map<String, SkillType> BY_ID = new LinkedHashMap<>();
	private static List<SkillType> all = List.of();

	static {
		for (Skill skill : Skill.values()) {
			BY_ID.put(skill.id(), skill);
		}

		all = List.copyOf(BY_ID.values());
	}

	private SkillTypes() {
	}

	/** Called once from common init, before any player state can exist. */
	public static void pullEntrypoints() {
		FabricLoader.getInstance()
				.getEntrypointContainers("specialities:skills", SkillsEntrypoint.class)
				.forEach(container -> container.getEntrypoint().registerSkills(skill -> {
					String id = skill.id();

					if (id == null || id.isBlank() || !id.equals(id.toLowerCase(Locale.ROOT))) {
						throw new IllegalArgumentException("Invalid skill id '" + id + "' from "
								+ container.getProvider().getMetadata().getId());
					}

					if (BY_ID.putIfAbsent(id, skill) != null) {
						throw new IllegalArgumentException("Duplicate skill id '" + id + "' from "
								+ container.getProvider().getMetadata().getId());
					}

					all = List.copyOf(BY_ID.values());
					Specialities.LOGGER.info("Registered skill '{}' from {}", id,
							container.getProvider().getMetadata().getId());
				}));
	}

	/** Built-ins and external skills, screen order. */
	public static List<SkillType> all() {
		return all;
	}

	/** Null for ids nothing registered — e.g. a payload from a mismatched mod set. */
	public static SkillType byId(final String id) {
		return BY_ID.get(id);
	}
}
