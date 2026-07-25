package com.specialities.platform;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.specialities.Specialities;
import com.specialities.api.SkillsEntrypoint;

import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;

/**
 * LexForge implementation of {@link Platform} for the {@code 1.20.1-forge} node.
 *
 * <p>ARTIFACT PROVENANCE — every signature below was read out of the jars pulled into
 * {@code scratchpad/phase-b-prep/art/}, not recalled:
 * <ul>
 * <li>{@code fmlcore-1.20.1-47.4.22-sources.jar},
 *     {@code net/minecraftforge/fml/loading/FMLPaths.java} — {@code enum} with
 *     {@code CONFIGDIR} and {@code public Path get()}.</li>
 * <li>{@code net/minecraftforge/fml/ModList.java} — {@code static ModList get()},
 *     {@code boolean isLoaded(String)}, {@code List<IModInfo> getMods()}.</li>
 * <li>{@code forgespi-7.0.1-sources.jar},
 *     {@code net/minecraftforge/forgespi/language/IModInfo.java} — {@code String getModId()},
 *     {@code Map<String,Object> getModProperties()}.</li>
 * </ul>
 *
 * <p>{@code getMods()} is the SORTED mod list, so third-party skills register in a
 * deterministic order — which the skills screen depends on, since it shows them in
 * registration order. That is the property {@link Platform#skillProviders()} promises.
 *
 * <p><b>{@code skillProviders()} is a second published API surface, and it is the one method
 * on this seam with real per-platform weight.</b> {@code specialities:skills} is a FABRIC
 * entrypoint; LexForge has no entrypoint concept. The mechanism chosen is a
 * {@code [modproperties.<modid>]} key in the contributing mod's own {@code mods.toml}:
 *
 * <pre>
 *   [modproperties.theirmodid]
 *   specialities_skills = "com.their.mod.TheirSkillsEntrypoint"
 * </pre>
 *
 * <p>The two alternatives were rejected for measurable reasons, not taste.
 * {@code InterModComms} messages are only readable at {@code InterModProcessEvent}, which runs
 * long after {@code SkillTypes.pullEntrypoints()} does — so the registry would be empty when
 * the skills screen is first built. {@code ServiceLoader} cannot name the owning mod, and
 * {@link Platform#skillProviders()} has to supply that id because
 * {@code SkillTypes.pullEntrypoints} puts it in two rejection messages and in the "Registered
 * skill" log line. The key is the same string on NeoForge, so a mod shipping for both loaders
 * declares it once.
 */
final class ForgePlatform implements Platform {
	/** The {@code [modproperties.<modid>]} key a contributing mod declares. */
	private static final String SKILLS_PROPERTY = "specialities_skills";

	@Override
	public Path configDir() {
		return FMLPaths.CONFIGDIR.get();
	}

	@Override
	public boolean isModLoaded(final String id) {
		return ModList.get().isLoaded(id);
	}

	@Override
	public List<SkillProvider> skillProviders() {
		List<SkillProvider> providers = new ArrayList<>();

		for (var info : ModList.get().getMods()) {
			Object declared = info.getModProperties().get(SKILLS_PROPERTY);
			if (!(declared instanceof String className) || className.isBlank()) {
				continue;
			}

			try {
				Class<?> type = Class.forName(className, false, ForgePlatform.class.getClassLoader());
				Object instance = type.getDeclaredConstructor().newInstance();
				providers.add(new SkillProvider(info.getModId(), (SkillsEntrypoint) instance));
			} catch (ReflectiveOperationException | ClassCastException failure) {
				Specialities.LOGGER.error("Mod '{}' declared {} = '{}' but it could not be loaded as a SkillsEntrypoint",
						info.getModId(), SKILLS_PROPERTY, className, failure);
			}
		}

		return List.copyOf(providers);
	}
}
