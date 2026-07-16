package com.specialities.client.config;

import com.specialities.config.ConfigManager;
import com.specialities.config.SpecialitiesConfig;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;

import net.minecraft.client.gui.screens.Screen;
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
				.setSavingRunnable(ConfigManager::save);

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

		ConfigCategory general = builder.getOrCreateCategory(Component.translatable("config.specialities.category.general"));
		general.addEntry(eb.startDoubleField(Component.translatable("config.specialities.xpRateMultiplier"), config.xpRateMultiplier)
				.setDefaultValue(1.0)
				.setMin(0.0).setMax(100.0)
				.setTooltip(Component.translatable("config.specialities.xpRateMultiplier.tooltip"))
				.setSaveConsumer(v -> config.xpRateMultiplier = v)
				.build());

		return builder.build();
	}
}
