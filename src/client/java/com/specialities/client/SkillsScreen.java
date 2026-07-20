package com.specialities.client;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.specialities.api.SkillType;
import com.specialities.skills.PlayerSkills;
import com.specialities.skills.Skill;
import com.specialities.skills.SkillManager;
import com.specialities.skills.SkillTypes;
import com.specialities.skills.Tuning;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * Lists every skill — greyed out until you start levelling it, so new players
 * can see what exists. The arrow button beside each skill opens a note on where
 * that skill's XP comes from; hovering the skill shows the bonuses it provides.
 */
public class SkillsScreen extends Screen {
	/**
	 * One sprite for both states, rotated a quarter turn when open — vanilla's
	 * own down arrow draws a smaller triangle, which made the two states
	 * mismatch in size.
	 */
	private static final Identifier ARROW = Identifier.withDefaultNamespace("transferable_list/select");
	private static final Identifier ARROW_HOVER = Identifier.withDefaultNamespace("transferable_list/select_highlighted");

	private static final int ROW_HEIGHT = 24;
	private static final int ROW_WIDTH = 240;
	private static final int ICON_SIZE = 16;
	/** Native sprite is 32x32; halving keeps the pixel art crisp. */
	private static final int ARROW_SIZE = 16;
	private static final int ARROW_GAP = 4;
	private static final int TOTAL_WIDTH = ARROW_SIZE + ARROW_GAP + ROW_WIDTH;
	private static final int LINE_HEIGHT = 10;
	private static final int TITLE_Y = 12;
	private static final int LIST_TOP = 30;
	/** Space reserved at the bottom for the Done button. */
	private static final int LIST_BOTTOM_MARGIN = 38;

	private static final int DIM_ALPHA = 0x55;
	private static final int FULL_ALPHA = 0xFF;

	private final @Nullable Screen parent;
	private final Set<SkillType> expanded = new LinkedHashSet<>();
	private double scroll;

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

	private int listBottom() {
		return this.height - LIST_BOTTOM_MARGIN;
	}

	private int viewportHeight() {
		return Math.max(0, this.listBottom() - LIST_TOP);
	}

	private List<FormattedCharSequence> sourceLines(final SkillType skill) {
		return this.font.split(
				Component.translatable("screen.specialities.skills.source." + skill.id()), ROW_WIDTH - 16);
	}

	/** Height of a row's expanded panel: header line + wrapped source lines + padding. */
	private int expandedHeight(final SkillType skill) {
		return LINE_HEIGHT * (1 + this.sourceLines(skill).size()) + 5;
	}

	private int contentHeight() {
		int height = 0;

		for (SkillType skill : SkillTypes.all()) {
			height += ROW_HEIGHT + (this.expanded.contains(skill) ? this.expandedHeight(skill) : 0);
		}

		return height;
	}

	private double maxScroll() {
		return Math.max(0, this.contentHeight() - this.viewportHeight());
	}

	private void clampScroll() {
		this.scroll = Mth.clamp(this.scroll, 0.0, this.maxScroll());
	}

	@Override
	public boolean mouseScrolled(final double x, final double y, final double scrollX, final double scrollY) {
		if (this.maxScroll() > 0) {
			this.scroll = Mth.clamp(this.scroll - scrollY * LINE_HEIGHT, 0.0, this.maxScroll());
			return true;
		}

		return super.mouseScrolled(x, y, scrollX, scrollY);
	}

	@Override
	public boolean mouseClicked(final MouseButtonEvent event, final boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}

		if (event.button() != 0) {
			return false;
		}

		int left = (this.width - TOTAL_WIDTH) / 2;

		// Only the arrow button toggles; the skill row itself is not clickable.
		if (event.x() < left || event.x() >= left + ARROW_SIZE
				|| event.y() < LIST_TOP || event.y() >= this.listBottom()) {
			return false;
		}

		int y = LIST_TOP - (int) this.scroll;

		for (SkillType skill : SkillTypes.all()) {
			int arrowTop = y + (ROW_HEIGHT - 2 - ARROW_SIZE) / 2;

			if (event.y() >= arrowTop && event.y() < arrowTop + ARROW_SIZE) {
				if (!this.expanded.remove(skill)) {
					this.expanded.add(skill);
				}

				this.clampScroll();
				this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
				return true;
			}

			y += ROW_HEIGHT + (this.expanded.contains(skill) ? this.expandedHeight(skill) : 0);
		}

		return false;
	}

	@Override
	public void extractRenderState(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);

		graphics.text(this.font, this.title, (this.width - this.font.width(this.title)) / 2, TITLE_Y, 0xFFFFFFFF, true);

		if (this.minecraft.player == null) {
			return;
		}

		PlayerSkills skills = SkillManager.get(this.minecraft.player);
		int left = (this.width - TOTAL_WIDTH) / 2;
		int blockLeft = left + ARROW_SIZE + ARROW_GAP;
		int bottom = this.listBottom();
		this.clampScroll();

		List<Component> tooltip = null;
		boolean mouseInRows = mouseX >= blockLeft && mouseX < blockLeft + ROW_WIDTH
				&& mouseY >= LIST_TOP && mouseY < bottom;
		boolean mouseInArrows = mouseX >= left && mouseX < left + ARROW_SIZE
				&& mouseY >= LIST_TOP && mouseY < bottom;
		graphics.enableScissor(left, LIST_TOP, left + TOTAL_WIDTH, bottom);
		int y = LIST_TOP - (int) this.scroll;

		for (SkillType skill : SkillTypes.all()) {
			boolean visible = y + ROW_HEIGHT > LIST_TOP && y < bottom;
			boolean hovered = mouseInRows && mouseY >= y && mouseY < y + ROW_HEIGHT - 2;

			if (visible) {
				this.renderArrow(graphics, skill, left, y, mouseInArrows, mouseY);
				this.renderRow(graphics, skills, skill, blockLeft, y, hovered);

				if (hovered) {
					tooltip = this.bonusLines(skill, skills, skills.level(skill));
				}
			}

			y += ROW_HEIGHT;

			if (this.expanded.contains(skill)) {
				int panelHeight = this.expandedHeight(skill);

				if (y + panelHeight > LIST_TOP && y < bottom) {
					this.renderSourcePanel(graphics, skill, blockLeft, y);
				}

				y += panelHeight;
			}
		}

		graphics.disableScissor();
		this.renderScrollbar(graphics, left, bottom);

		// Outside the scissor: the deferred tooltip must not be clipped.
		if (tooltip != null) {
			graphics.setTooltipForNextFrame(this.font, tooltip, Optional.empty(), mouseX, mouseY);
		}
	}

	private void renderRow(final GuiGraphicsExtractor graphics, final PlayerSkills skills, final SkillType skill,
			final int left, final int top, final boolean hovered) {
		int level = skills.level(skill);
		boolean started = skills.discovered(skill);
		int alpha = started ? FULL_ALPHA : DIM_ALPHA;

		graphics.fill(left, top, left + ROW_WIDTH, top + ROW_HEIGHT - 2, hovered ? 0x66FFFFFF : 0x44000000);

		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SkillIcons.sprite(skill),
				left + 4, top + 3, ICON_SIZE, ICON_SIZE, ARGB.color(alpha, 0xFFFFFF));

		graphics.text(this.font, skill.displayName(), left + 25, top + 3, ARGB.color(alpha, skill.color()), true);
		graphics.text(this.font, Component.translatable("screen.specialities.skills.level", level),
				left + 25, top + 13, ARGB.color(alpha, 0xDDDDDD), false);

		// Progress bar on the right.
		int barLeft = left + 150;
		int barRight = left + ROW_WIDTH - 6;
		int barTop = top + 9;
		graphics.fill(barLeft, barTop, barRight, barTop + 5, ARGB.color(alpha, 0x222222));

		int intoLevel = skills.totalXp(skill) - Tuning.totalXpForLevel(level);
		int needed = Tuning.xpToNext(level);
		float progress = level >= Tuning.MAX_LEVEL ? 1.0F : Math.min(1.0F, intoLevel / (float) needed);
		int fill = (int) ((barRight - barLeft - 2) * progress);

		if (fill > 0) {
			graphics.fill(barLeft + 1, barTop + 1, barLeft + 1 + fill, barTop + 4, ARGB.color(alpha, skill.color()));
		}
	}

	/** The expand/collapse button: the vanilla resource-pack picker arrow. */
	private void renderArrow(final GuiGraphicsExtractor graphics, final SkillType skill, final int left, final int top,
			final boolean mouseInArrows, final int mouseY) {
		int arrowTop = top + (ROW_HEIGHT - 2 - ARROW_SIZE) / 2;
		boolean hovered = mouseInArrows && mouseY >= arrowTop && mouseY < arrowTop + ARROW_SIZE;
		Identifier sprite = hovered ? ARROW_HOVER : ARROW;

		if (this.expanded.contains(skill)) {
			// Quarter turn clockwise: the arrow points down while open.
			graphics.pose().pushMatrix();
			graphics.pose().rotateAbout((float) (Math.PI / 2.0),
					left + ARROW_SIZE / 2.0F, arrowTop + ARROW_SIZE / 2.0F);
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, left, arrowTop, ARROW_SIZE, ARROW_SIZE);
			graphics.pose().popMatrix();
		} else {
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, left, arrowTop, ARROW_SIZE, ARROW_SIZE);
		}
	}

	private void renderSourcePanel(final GuiGraphicsExtractor graphics, final SkillType skill, final int left, final int top) {
		int height = this.expandedHeight(skill);
		graphics.fill(left, top - 2, left + ROW_WIDTH, top + height - 4, 0x33000000);

		graphics.text(this.font, Component.translatable("screen.specialities.skills.source_header"),
				left + 6, top + 1, 0xFFAAAAAA, false);

		int lineY = top + 1 + LINE_HEIGHT;

		for (FormattedCharSequence line : this.sourceLines(skill)) {
			graphics.text(this.font, line, left + 12, lineY, 0xFFEEEEEE, false);
			lineY += LINE_HEIGHT;
		}
	}

	private void renderScrollbar(final GuiGraphicsExtractor graphics, final int left, final int bottom) {
		double max = this.maxScroll();

		if (max <= 0) {
			return;
		}

		int trackHeight = bottom - LIST_TOP;
		int trackX = left + TOTAL_WIDTH + 2;
		int thumbHeight = Math.max(16, (int) (trackHeight * (trackHeight / (float) this.contentHeight())));
		int thumbY = LIST_TOP + (int) ((trackHeight - thumbHeight) * (this.scroll / max));

		graphics.fill(trackX, LIST_TOP, trackX + 3, bottom, 0x44000000);
		graphics.fill(trackX, thumbY, trackX + 3, thumbY + thumbHeight, 0xAAFFFFFF);
	}

	private List<Component> bonusLines(final SkillType skill, final PlayerSkills skills, final int level) {
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

		// External skills describe themselves; the luck footer below is
		// built-in-only, so they are complete here.
		if (!(skill instanceof Skill builtin)) {
			lines.addAll(skill.screenLines(level));
			return lines;
		}

		switch (builtin) {
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
				int damageBonus = Math.round((Tuning.damageMultiplier(level) - 1.0F) * 100.0F);
				lines.add(Component.translatable("tooltip.specialities.damage", damageBonus));
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
						String.format("%.2f", Tuning.stealthCritMeleeMultiplier(level)),
						String.format("%.2f", Tuning.stealthCritRangedMultiplier(level))));
			}
			case SMITHING -> {
				lines.add(Component.translatable("tooltip.specialities.resourcefulness", level));
				lines.add(Component.translatable("tooltip.specialities.smelt_multicraft",
						Math.round(Tuning.smeltChanceX2(level) * 100.0F),
						Math.round(Tuning.smeltChanceX4(level) * 100.0F),
						Math.round(Tuning.smeltChanceX8(level) * 100.0F)));
			}
			case ALCHEMY -> lines.add(Component.translatable("tooltip.specialities.brew_return",
					Math.round(Tuning.alchemyReturnChance(level) * 100.0F)));
			case ENCHANTING -> {
				lines.add(Component.translatable("tooltip.specialities.enchant_discount",
						Math.round(Tuning.enchantDiscountChance(level) * 100.0F)));
				lines.add(Component.translatable("tooltip.specialities.enchant_luck",
						Math.round(Tuning.enchantLuckChance(level) * 100.0F)));
			}
		}

		boolean usesLuck = switch (builtin) {
			case MINING, WOODCUTTING, HARVESTING, EXCAVATION, COMBAT, FISHING -> true;
			default -> false;
		};
		if (usesLuck && level < Tuning.MAX_LEVEL && luck < Tuning.MAX_LEVEL / Tuning.luckBreakpoint()) {
			lines.add(Component.translatable("tooltip.specialities.next_luck", (luck + 1) * Tuning.luckBreakpoint()));
		}

		return lines;
	}
}
