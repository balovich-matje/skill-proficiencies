package com.specialities.platform;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.specialities.Specialities;
import com.specialities.api.SkillsEntrypoint;

import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;

/**
 * NeoForge implementation of {@link Platform} for the {@code 1.21.1-neoforge} node.
 *
 * <p>ARTIFACT PROVENANCE — from {@code loader-4.0.43-sources.jar}
 * ({@code net.neoforged.fancymodloader:loader:4.0.43}, the version the neoforge 21.1.243 POM
 * depends on at {@code :41-43}):
 *
 * <ul>
 * <li>{@code net/neoforged/fml/loading/FMLPaths.java:20} {@code enum FMLPaths} with
 *     {@code CONFIGDIR("config")} at {@code :23} and {@code public Path get()} at {@code :89}.
 *     So {@code FMLPaths.CONFIGDIR.get()} is the exact analogue of
 *     {@code FabricLoader.getInstance().getConfigDir()} and resolves to the same
 *     {@code <instance>/config} directory — which keeps {@code config/skill-proficiencies.json} and
 *     every existing user config file valid.</li>
 * <li>{@code net/neoforged/fml/ModList.java:128} {@code List<IModInfo> getMods()},
 *     {@code :132} {@code boolean isLoaded(String modTarget)}.</li>
 * <li>{@code net/neoforged/neoforgespi/language/IModInfo.java:30} {@code String getModId()},
 *     {@code :44} {@code Map<String,Object> getModProperties()}.</li>
 * </ul>
 *
 * <p><b>{@link #skillProviders()} IS A SECOND PUBLISHED API SURFACE AND NEEDS THE USER'S
 * SIGN-OFF BEFORE IT IS DOCUMENTED AS ONE (prep Q5).</b> {@code specialities:skills} is a
 * Fabric ENTRYPOINT; NeoForge has no entrypoint concept, so a contributing mod has to declare
 * itself some other way. The mechanism implemented here is the third of three, and the two
 * rejections are recorded so nobody re-derives them:
 *
 * <ol>
 * <li><b>{@code InterModComms}</b> — exists ({@code net/neoforged/fml/InterModComms.java}),
 *     REJECTED on timing. Messages are only readable during {@code InterModProcessEvent},
 *     which fires well after mod construction, but {@code SkillTypes.pullEntrypoints()} runs
 *     in common init "before anything can touch player state" and {@code ModItems} reads the
 *     pulled skill list right after it. Moving the pull later would change registration order
 *     on this node alone.</li>
 * <li><b>{@code java.util.ServiceLoader}</b> — REJECTED because it cannot name the owning
 *     mod, and {@link Platform.SkillProvider} carries {@code modId} precisely because
 *     {@code SkillTypes.pullEntrypoints} prints it in two {@code IllegalArgumentException}
 *     messages and in the {@code "Registered skill '{}' from {}"} INFO line. Losing it
 *     changes observable output on one node.</li>
 * <li><b>A {@code [modproperties]} key, IMPLEMENTED.</b> A contributing mod puts
 *     {@code [modproperties.<their modid>]} / {@code specialities_skills = "com.their.Entry"}
 *     in its own {@code neoforge.mods.toml}; we walk {@code ModList.get().getMods()} — the
 *     sorted mod list, so the order the skills screen shows is deterministic exactly as the
 *     Fabric entrypoint order is — read the key, and instantiate the no-arg constructor. Mod
 *     id in hand, no new event, no timing change, and {@code api/SkillType} /
 *     {@code api/SkillRegistrar} stay byte-identical, which was the design's requirement.
 *     {@code [modproperties]} is standard on LexForge too, so the Forge node can use the
 *     identical key.</li>
 * </ol>
 *
 * <p>What is deliberately NOT done here: {@code README.md} is not edited. The key name is a
 * third-party contract and Archetypes is the first consumer, so publishing it is the user's
 * decision, and this node builds and boots either way — a world with no contributing mod
 * simply gets an empty list, exactly as an unused Fabric entrypoint does.
 */
final class NeoForgePlatform implements Platform {
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
				Class<?> type = Class.forName(className, false, NeoForgePlatform.class.getClassLoader());
				Object instance = type.getDeclaredConstructor().newInstance();
				providers.add(new SkillProvider(info.getModId(), (SkillsEntrypoint) instance));
			} catch (ReflectiveOperationException | ClassCastException | LinkageError failure) {
				// Same failure posture as Fabric: a bad contribution is logged and skipped, it
				// never takes the host mod down. SkillTypes.pullEntrypoints already rejects bad
				// SkillTypes with a named exception; this catches the earlier "the class you
				// named is wrong" case, which Fabric's own loader reports for us.
				Specialities.LOGGER.error(
						"Mod '{}' declared {} = '{}' but it could not be loaded as a SkillsEntrypoint",
						info.getModId(), SKILLS_PROPERTY, className, failure);
			}
		}

		return List.copyOf(providers);
	}
}
