package com.specialities.client.config;

import com.specialities.config.ConfigManager;
import com.specialities.config.SpecialitiesConfig;
import com.specialities.skills.LevelCap;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;

/**
 * Builds the Cloth Config screen for our knobs. Kept in its own class so it is
 * only ever class-loaded once Cloth Config is confirmed present (see
 * {@link ModMenuIntegration}), keeping Cloth a soft dependency.
 *
 * <p>The save consumers mutate the live {@link ConfigManager#get()} instance in
 * place; {@code setSavingRunnable} then persists (and sanitizes) it.
 */
public final class ClothConfigScreen {
	private ClothConfigScreen() {
	}

	public static Screen create(final Screen parent) {
		SpecialitiesConfig config = ConfigManager.get();

		ConfigBuilder builder = ConfigBuilder.create()
				.setParentScreen(parent)
				.setTitle(Component.translatable("config.specialities.title"))
				.setSavingRunnable(ClothConfigScreen::save);

		ConfigEntryBuilder eb = builder.entryBuilder();

		ConfigCategory combat = builder.getOrCreateCategory(Component.translatable("config.specialities.category.combat"));
		combat.addEntry(eb.startDoubleField(Component.translatable("config.specialities.combatDamageMaxBonus"), config.combatDamageMaxBonus)
				.setDefaultValue(0.5)
				.setMin(0.0).setMax(5.0)
				.setTooltip(Component.translatable("config.specialities.combatDamageMaxBonus.tooltip"))
				.setSaveConsumer(v -> config.combatDamageMaxBonus = v)
				.build());
		combat.addEntry(eb.startDoubleField(Component.translatable("config.specialities.attackSpeedMaxReduction"), config.attackSpeedMaxReduction)
				.setDefaultValue(0.3)
				.setMin(0.0).setMax(0.9)
				.setTooltip(Component.translatable("config.specialities.attackSpeedMaxReduction.tooltip"))
				.setSaveConsumer(v -> config.attackSpeedMaxReduction = v)
				.build());

		ConfigCategory skills = builder.getOrCreateCategory(Component.translatable("config.specialities.category.skills"));
		skills.addEntry(eb.startDoubleField(Component.translatable("config.specialities.miningSpeedMaxBonus"), config.miningSpeedMaxBonus)
				.setDefaultValue(1.0)
				.setMin(0.0).setMax(10.0)
				.setTooltip(Component.translatable("config.specialities.miningSpeedMaxBonus.tooltip"))
				.setSaveConsumer(v -> config.miningSpeedMaxBonus = v)
				.build());
		skills.addEntry(eb.startIntField(Component.translatable("config.specialities.luckLevelsPerBonus"), config.luckLevelsPerBonus)
				.setDefaultValue(20)
				.setMin(1).setMax(100)
				.setTooltip(Component.translatable("config.specialities.luckLevelsPerBonus.tooltip"))
				.setSaveConsumer(v -> config.luckLevelsPerBonus = v)
				.build());
		// GitHub issue #6. Ranges match SpecialitiesConfig.sanitize() exactly, for the same reason
		// the two HUD knobs below say so.
		skills.addEntry(eb.startBooleanToggle(Component.translatable("config.specialities.extendedLevels"), config.extendedLevels)
				.setDefaultValue(false)
				.setTooltip(Component.translatable("config.specialities.extendedLevels.tooltip"))
				.setSaveConsumer(v -> config.extendedLevels = v)
				.build());
		skills.addEntry(eb.startIntField(Component.translatable("config.specialities.extendedMaxLevel"), config.extendedMaxLevel)
				.setDefaultValue(LevelCap.ABSOLUTE_MAX)
				.setMin(LevelCap.EXTENDED_MIN).setMax(LevelCap.ABSOLUTE_MAX)
				.setTooltip(Component.translatable("config.specialities.extendedMaxLevel.tooltip"))
				.setSaveConsumer(v -> config.extendedMaxLevel = v)
				.build());

		ConfigCategory general = builder.getOrCreateCategory(Component.translatable("config.specialities.category.general"));
		general.addEntry(eb.startDoubleField(Component.translatable("config.specialities.xpRateMultiplier"), config.xpRateMultiplier)
				.setDefaultValue(1.0)
				.setMin(0.0).setMax(100.0)
				.setTooltip(Component.translatable("config.specialities.xpRateMultiplier.tooltip"))
				.setSaveConsumer(v -> config.xpRateMultiplier = v)
				.build());

		// Its own category on purpose: every knob above is a balance number the skill logic reads
		// where it runs, this one is a client-local display preference that is never synced. The
		// tooltip says so too, because "I turned it off on the server" is the obvious wrong guess.
		// This screen exists on the 26.x nodes only (Cloth/Mod Menu are gated >=26.1 and the
		// client source set excludes com/specialities/client/config below that), so on the five
		// legacy/loader nodes the knobs in config/skill-proficiencies.json are the whole UI.
		ConfigCategory ui = builder.getOrCreateCategory(Component.translatable("config.specialities.category.interface"));
		ui.addEntry(eb.startBooleanToggle(Component.translatable("config.specialities.showXpHudBar"), config.showXpHudBar)
				.setDefaultValue(true)
				.setTooltip(Component.translatable("config.specialities.showXpHudBar.tooltip"))
				.setSaveConsumer(v -> config.showXpHudBar = v)
				.build());
		// The two issue-#4 compat knobs. Ranges match SpecialitiesConfig.sanitize() exactly — Cloth
		// clamps the widget, sanitize() clamps a hand-edited file, and the two must not disagree
		// or the screen would silently show a value the mod never uses.
		ui.addEntry(eb.startIntField(Component.translatable("config.specialities.hudShiftAmount"), config.hudShiftAmount)
				.setDefaultValue(7)
				.setMin(0).setMax(32)
				.setTooltip(Component.translatable("config.specialities.hudShiftAmount.tooltip"))
				.setSaveConsumer(v -> config.hudShiftAmount = v)
				.build());
		ui.addEntry(eb.startIntField(Component.translatable("config.specialities.hudBarYOffset"), config.hudBarYOffset)
				.setDefaultValue(0)
				.setMin(-64).setMax(64)
				.setTooltip(Component.translatable("config.specialities.hudBarYOffset.tooltip"))
				.setSaveConsumer(v -> config.hudBarYOffset = v)
				.build());

		return builder.build();
	}

	/**
	 * Persist, then settle the level cap. The cap is the SERVER's setting (skills/LevelCap), so
	 * what an edit here may touch depends on where this client is:
	 *
	 * <ul>
	 * <li>in its own singleplayer/LAN world — the integrated server adopts the new cap on its
	 *     own thread and resends it to every player, guests included;</li>
	 * <li>at the title screen — adopt it directly (the next world's join re-asserts it anyway);</li>
	 * <li>on a REMOTE server — do not touch it: that server's cap stays in force, and this
	 *     file's value applies the next time this client hosts a world.</li>
	 * </ul>
	 */
	private static void save() {
		ConfigManager.save();

		Minecraft minecraft = Minecraft.getInstance();
		IntegratedServer server = minecraft.getSingleplayerServer();

		if (server != null) {
			server.execute(() -> LevelCap.applyConfigAndResync(server));
		} else if (minecraft.getConnection() == null) {
			LevelCap.applyConfig();
		}
	}
}
