package com.specialities.api;

/** Handed to {@link SkillsEntrypoint#registerSkills}; valid only during it. */
public interface SkillRegistrar {
	/**
	 * Registers a skill. Ids must be lowercase and unique across all mods —
	 * duplicates and malformed ids throw immediately, at init, where the
	 * developer is looking.
	 */
	void register(SkillType skill);
}
