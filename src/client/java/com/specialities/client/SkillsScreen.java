package com.specialities.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.specialities.skills.PlayerSkills;
import com.specialities.skills.Skill;
import com.specialities.skills.SkillManager;
import com.specialities.skills.Tuning;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Lists discovered skills (level > 0) with their level and XP; hovering a row
 * shows the bonuses that skill currently provides.
 */
public class SkillsScreen extends Screen {
	private static final int ROW_HEIGHT = 24;
	private static final int ROW_WIDTH = 220;
	private static final int LIST_TOP = 40;

	private final @Nullable Screen parent;

	public SkillsScreen(final @Nullable Screen parent) {
		super(Component.translatable("screen.specialities.skills"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose())
				.bounds(this.width / 2 - 60, this.height - 32, 120, 20)
				.build());
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(this.parent);
	}

	@Override
	public void extractRenderState(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);

		graphics.text(this.font, this.title, (this.width - this.font.width(this.title)) / 2, 16, 0xFFFFFFFF, true);

		if (this.minecraft.player == null) {
			return;
		}

		PlayerSkills skills = SkillManager.get(this.minecraft.player);
		List<Skill> discovered = new ArrayList<>();

		for (Skill skill : Skill.values()) {
			if (skills.discovered(skill)) {
				discovered.add(skill);
			}
		}

		if (discovered.isEmpty()) {
			Component none = Component.translatable("screen.specialities.skills.none");
			graphics.text(this.font, none, (this.width - this.font.width(none)) / 2, LIST_TOP + 20, 0xFFAAAAAA, true);
			return;
		}

		int left = (this.width - ROW_WIDTH) / 2;

		for (int i = 0; i < discovered.size(); i++) {
			Skill skill = discovered.get(i);
			int top = LIST_TOP + i * ROW_HEIGHT;
			int level = skills.level(skill);

			boolean hovered = mouseX >= left && mouseX < left + ROW_WIDTH && mouseY >= top && mouseY < top + ROW_HEIGHT - 2;
			graphics.fill(left, top, left + ROW_WIDTH, top + ROW_HEIGHT - 2, hovered ? 0x66FFFFFF : 0x44000000);

			graphics.fakeItem(new ItemStack(skill.icon()), left + 3, top + 3);
			graphics.text(this.font, skill.displayName(), left + 26, top + 3, skill.color(), true);
			graphics.text(this.font, Component.translatable("screen.specialities.skills.level", level),
					left + 26, top + 13, 0xFFDDDDDD, false);

			// Mini progress bar on the right half of the row.
			int barLeft = left + 120;
			int barRight = left + ROW_WIDTH - 6;
			int barTop = top + 9;
			int intoLevel = skills.totalXp(skill) - Tuning.totalXpForLevel(level);
			int needed = Tuning.xpToNext(level);
			float progress = level >= Tuning.MAX_LEVEL ? 1.0F : Math.min(1.0F, intoLevel / (float) needed);
			graphics.fill(barLeft, barTop, barRight, barTop + 5, 0xFF222222);
			int fill = (int) ((barRight - barLeft - 2) * progress);
			if (fill > 0) {
				graphics.fill(barLeft + 1, barTop + 1, barLeft + 1 + fill, barTop + 4, skill.color());
			}

			if (hovered) {
				graphics.setTooltipForNextFrame(this.font, this.bonusLines(skill, skills, level), Optional.empty(), mouseX, mouseY);
			}
		}
	}

	private List<Component> bonusLines(final Skill skill, final PlayerSkills skills, final int level) {
		List<Component> lines = new ArrayList<>();
		lines.add(skill.displayName().copy().append(" — ").append(Component.translatable("screen.specialities.skills.level", level)));

		if (level >= Tuning.MAX_LEVEL) {
			lines.add(Component.translatable("screen.specialities.skills.max"));
		} else {
			int intoLevel = skills.totalXp(skill) - Tuning.totalXpForLevel(level);
			lines.add(Component.translatable("screen.specialities.skills.xp", intoLevel, Tuning.xpToNext(level)));
		}

		int speedBonus = Math.round((Tuning.breakSpeedMultiplier(level) - 1.0F) * 100.0F);
		int luck = Tuning.luckBonus(level);

		switch (skill) {
			case MINING -> {
				lines.add(Component.translatable("tooltip.specialities.break_speed", speedBonus, Component.translatable("tool.specialities.pickaxes")));
				lines.add(Component.translatable("tooltip.specialities.fortune", luck));
			}
			case WOODCUTTING -> {
				lines.add(Component.translatable("tooltip.specialities.break_speed", speedBonus, Component.translatable("tool.specialities.axes")));
				lines.add(Component.translatable("tooltip.specialities.fortune_logs", luck));
			}
			case HARVESTING -> {
				lines.add(Component.translatable("tooltip.specialities.break_speed", speedBonus, Component.translatable("tool.specialities.hoes")));
				lines.add(Component.translatable("tooltip.specialities.fortune", luck));
			}
			case EXCAVATION -> {
				lines.add(Component.translatable("tooltip.specialities.break_speed", speedBonus, Component.translatable("tool.specialities.shovels")));
				lines.add(Component.translatable("tooltip.specialities.fortune", luck));
			}
			case COMBAT -> {
				lines.add(Component.translatable("tooltip.specialities.damage", level));
				lines.add(Component.translatable("tooltip.specialities.looting", luck));
			}
			case ARMS_MASTERY -> {
				int recovery = Math.round((1.0F - Tuning.recoveryTimeMultiplier(level)) * 100.0F);
				int sweeping = Tuning.sweepingBonus(level);
				lines.add(Component.translatable("tooltip.specialities.attack_recovery", recovery));
				lines.add(Component.translatable("tooltip.specialities.sweeping", sweeping));

				if (level < Tuning.MAX_LEVEL) {
					lines.add(Component.translatable("tooltip.specialities.next_sweep", (sweeping + 1) * Tuning.SWEEP_BREAKPOINT));
				}
			}
			case ARCHERY -> {
				int draw = Math.round((1.0F - Tuning.recoveryTimeMultiplier(level)) * 100.0F);
				int ricochets = Tuning.ricochets(level);
				lines.add(Component.translatable("tooltip.specialities.draw_speed", draw));
				lines.add(Component.translatable("tooltip.specialities.ricochet", ricochets));

				if (ricochets < 2) {
					lines.add(Component.translatable("tooltip.specialities.next_ricochet", ricochets == 0 ? 50 : 100));
				}
			}
			case FISHING -> {
				lines.add(Component.translatable("tooltip.specialities.sea_luck", luck));
				lines.add(Component.translatable("tooltip.specialities.lure", Tuning.lureBonus(level)));
			}
			case DEFENCE -> {
				lines.add(Component.translatable("tooltip.specialities.hearts", Tuning.maxHealthBonus(level) / 2));
				lines.add(Component.translatable("tooltip.specialities.toughness", Tuning.toughnessBonus(level)));
			}
			case ACROBATICS -> {
				int reduction = Math.round(Tuning.acrobaticsProtectionPoints(level) * 4.0F);
				lines.add(Component.translatable("tooltip.specialities.fall_damage", Math.min(100, reduction)));
				lines.add(Component.translatable("tooltip.specialities.fall_immunity"));
			}
			case ATHLETICS -> {
				int hunger = Math.round((1.0F - Tuning.recoveryTimeMultiplier(level)) * 100.0F);
				lines.add(Component.translatable("tooltip.specialities.sprint_hunger", hunger));
				lines.add(Component.translatable("tooltip.specialities.swiftness", Tuning.swiftnessTier(level)));
			}
			case SNEAKING -> {
				int detection = Math.round((1.0F - Tuning.sneakVisibilityMultiplier(level, 0)) * 100.0F);
				lines.add(Component.translatable("tooltip.specialities.detection", detection));
				lines.add(Component.translatable("tooltip.specialities.heavy_armor"));
				lines.add(Component.translatable("tooltip.specialities.stealth_crit",
						String.format("%.2f", Tuning.stealthCritMultiplier(level))));
			}
		}

		boolean usesLuck = switch (skill) {
			case MINING, WOODCUTTING, HARVESTING, EXCAVATION, COMBAT, FISHING -> true;
			default -> false;
		};
		if (usesLuck && level < Tuning.MAX_LEVEL && luck < Tuning.MAX_LEVEL / Tuning.LUCK_BREAKPOINT) {
			lines.add(Component.translatable("tooltip.specialities.next_luck", (luck + 1) * Tuning.LUCK_BREAKPOINT));
		}

		return lines;
	}
}
