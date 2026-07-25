package com.specialities.platform;

import java.nio.file.Path;
import java.util.List;

import com.specialities.api.SkillsEntrypoint;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Fabric implementation of {@link Platform}. Delegates to {@code FabricLoader}
 * exactly as the call sites did before the seam existed — the entrypoint name
 * {@code specialities:skills} is published third-party API and must not move.
 */
final class FabricPlatform implements Platform {
	@Override
	public Path configDir() {
		return FabricLoader.getInstance().getConfigDir();
	}

	@Override
	public boolean isModLoaded(final String id) {
		return FabricLoader.getInstance().isModLoaded(id);
	}

	@Override
	public List<SkillProvider> skillProviders() {
		return FabricLoader.getInstance()
				.getEntrypointContainers("specialities:skills", SkillsEntrypoint.class)
				.stream()
				.map(container -> new SkillProvider(
						container.getProvider().getMetadata().getId(),
						container.getEntrypoint()))
				.toList();
	}
}
