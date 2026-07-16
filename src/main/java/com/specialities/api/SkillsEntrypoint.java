package com.specialities.api;

/**
 * The {@code specialities:skills} entrypoint. Declare an implementation in
 * your fabric.mod.json to contribute skills:
 *
 * <pre>
 * "entrypoints": { "specialities:skills": ["com.yourmod.YourSkills"] }
 * </pre>
 *
 * <p>Called during Specialities' common initializer on both the server and
 * the client — register the same skills, in the same order, on both sides.
 * XP is awarded through {@link com.specialities.skills.SkillManager#addXp}.
 */
public interface SkillsEntrypoint {
	void registerSkills(SkillRegistrar registrar);
}
