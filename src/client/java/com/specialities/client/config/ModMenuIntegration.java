package com.specialities.client.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Mod Menu entrypoint. This class only references the Mod Menu API and our own
 * {@link ClothConfigScreen}; the actual Cloth Config classes live in that other
 * class, which is only touched when the screen is opened. So if Mod Menu is
 * present but Cloth Config is not, we simply report "no config screen" instead
 * of class-loading (and crashing on) the missing Cloth API.
 */
public final class ModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		FabricLoader loader = FabricLoader.getInstance();
		boolean clothPresent = loader.isModLoaded("cloth-config")
				|| loader.isModLoaded("cloth_config")
				|| loader.isModLoaded("cloth-config2");

		if (!clothPresent) {
			return parent -> null;
		}

		return ClothConfigScreen::create;
	}
}
