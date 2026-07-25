package com.specialities.platform;

import java.nio.file.Path;
import java.util.List;

import com.specialities.api.SkillsEntrypoint;

/**
 * Seam 3 of three (design {@code docs/MULTIVERSION.md} §2) — loader services.
 * Everything the mod asks the mod loader itself, as opposed to the game: where
 * the config directory is, whether another mod is present, and which other mods
 * contribute skills.
 *
 * <p>All three methods have call sites today ({@code config/ConfigManager},
 * {@code skills/SkillTypes}, {@code client/config/ModMenuIntegration}); nothing
 * here is speculative. Phase B (NeoForge/Forge) replaces the implementation, not
 * the interface — {@link #skillProviders()} is the one method with real
 * per-platform weight, because {@code specialities:skills} is a Fabric entrypoint
 * and the other loaders have no entrypoint concept (design §2 requires them to
 * pick {@code InterModComms} or {@code ServiceLoader} and document it as a second
 * published API surface; {@code api/SkillType} and {@code api/SkillRegistrar}
 * stay byte-identical either way).
 *
 * <p>{@link #INSTANCE} is unconditional on purpose. Every registered node is a
 * Fabric node, so a loader-constant block around it would be a branch no build
 * can exercise, and a disabled branch naming a class that does not exist yet is
 * the silently-wrong case. Phase B forks exactly this line using the INLINE
 * (expression) directive form — the initializer is a fragment, not a whole
 * statement, so the directive cannot own its own line; conventions §4 has the
 * literal syntax, and {@code client/SpecialitiesClient} has two worked examples
 * of it in the tree. It must also exclude the unused implementation class from
 * that node's source set, the way {@code client/config} is excluded below 26.1
 * (conventions §5e-ter).
 *
 * <p>No directive token is written out here on purpose: Stonecutter scans
 * javadoc like any other text, so a sample directive in a comment is a live
 * directive.
 */
public interface Platform {
	Platform INSTANCE = new FabricPlatform();

	/** The instance config directory; the mod writes {@code specialities.json} into it. */
	Path configDir();

	/** True if a mod with this id is loaded. Used for the optional Cloth Config UI. */
	boolean isModLoaded(String id);

	/**
	 * Every other mod contributing skills, in mod-load order — the order the
	 * skills screen shows, so it has to be deterministic.
	 *
	 * <p>The owning mod's id travels with the entrypoint because
	 * {@code SkillTypes.pullEntrypoints} names it in two rejection messages and
	 * in the "Registered skill" log line; returning bare entrypoints would change
	 * that output.
	 */
	List<SkillProvider> skillProviders();

	/** One contributing mod: its id, and the entrypoint instance it registered. */
	record SkillProvider(String modId, SkillsEntrypoint entrypoint) {
	}
}
