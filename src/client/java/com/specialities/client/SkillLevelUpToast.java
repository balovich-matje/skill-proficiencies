package com.specialities.client;

import com.specialities.api.SkillType;

import net.minecraft.client.gui.Font;
// 26.x GUI rendering is extract-based; 1.21.11 and below draw immediately.
//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Popup shown when a skill levels up:
 *
 * <pre>
 * Skillname
 * Increased x -> y
 * </pre>
 *
 * The "epic" challenge-complete jingle only plays when the level-up crosses 50
 * or 100; otherwise just the regular quiet toast whoosh.
 */
public class SkillLevelUpToast implements Toast {
	private static final Identifier BACKGROUND_SPRITE = Identifier.withDefaultNamespace("toast/advancement");
	private static final long DISPLAY_TIME_MS = 5000;

	private final SkillType skill;
	private final int fromLevel;
	private final int newLevel;
	private final ItemStack icon;
	private Toast.Visibility wantedVisibility = Toast.Visibility.HIDE;

	public SkillLevelUpToast(final SkillType skill, final int fromLevel, final int newLevel) {
		this.skill = skill;
		this.fromLevel = fromLevel;
		this.newLevel = newLevel;
		this.icon = new ItemStack(skill.icon());
	}

	@Override
	public Toast.Visibility getWantedVisibility() {
		return this.wantedVisibility;
	}

	@Override
	public void update(final ToastManager manager, final long fullyVisibleForMs) {
		this.wantedVisibility = fullyVisibleForMs >= DISPLAY_TIME_MS * manager.getNotificationDisplayTimeMultiplier()
				? Toast.Visibility.HIDE
				: Toast.Visibility.SHOW;
	}

	@Override
	public @Nullable SoundEvent getSoundEvent() {
		return this.crossesMilestone() ? SoundEvents.UI_TOAST_CHALLENGE_COMPLETE : null;
	}

	private boolean crossesMilestone() {
		return (this.fromLevel < 50 && this.newLevel >= 50) || (this.fromLevel < 100 && this.newLevel >= 100);
	}

	@Override
	// Toast's draw hook is extractRenderState on 26.x and render below; `text` is
	// `drawString` and `fakeItem` is `renderFakeItem` there.
	//? if >=26.1 {
	public void extractRenderState(final GuiGraphicsExtractor graphics, final Font font, final long fullyVisibleForMs) {
	//?} else {
	/*public void render(final GuiGraphics graphics, final Font font, final long fullyVisibleForMs) {
	*///?}
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BACKGROUND_SPRITE, 0, 0, this.width(), this.height());
		//? if >=26.1 {
		graphics.text(font, this.skill.displayName(), 30, 7, this.skill.color(), false);
		graphics.text(font, Component.translatable("toast.specialities.levelup.desc", this.fromLevel, this.newLevel),
				30, 18, 0xFFFFFFFF, false);
		graphics.fakeItem(this.icon, 8, 8);
		//?} else {
		/*graphics.drawString(font, this.skill.displayName(), 30, 7, this.skill.color(), false);
		graphics.drawString(font, Component.translatable("toast.specialities.levelup.desc", this.fromLevel, this.newLevel),
				30, 18, 0xFFFFFFFF, false);
		graphics.renderFakeItem(this.icon, 8, 8);
		*///?}
	}
}
