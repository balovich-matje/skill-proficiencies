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
// The Toast interface is a different shape below 1.21.11. From 1.21.11 up it is four
// members (getWantedVisibility / update(ToastManager,long) / getSoundEvent / a void draw);
// on 1.21.1 it is ONE method — `Toast.Visibility render(GuiGraphics, ToastComponent, long)`
// — which both draws and returns the next visibility, `ToastManager` is called
// `ToastComponent`, and there is no per-toast sound hook at all (Visibility.SHOW/HIDE carry
// the vanilla whoosh and the manager plays those), so the milestone jingle has to be played
// by the toast itself, once. `RenderPipelines` is likewise 1.21.11-and-up.
//? if >=1.21.11 {
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.renderer.RenderPipelines;
//?} else {
/*import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
*///?}
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
// jspecify is one of the game's OWN libraries only from 1.21.11 up (conventions
// §5e-bis); below that it is absent and org.jetbrains:annotations 26.0.2 (on the
// compile classpath via fabric-loader) supplies a @Nullable that is @Target(TYPE_USE)
// as well, so nothing but the import forks.
//? if >=1.21.11 {
import org.jspecify.annotations.Nullable;
//?} else {
/*import org.jetbrains.annotations.Nullable;
*///?}

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
	// 1.20.1 has no GUI sprite atlas, so there is no `toast/advancement` sprite. The backdrop
	// comes from the sheet the Toast interface itself points at — `Toast.TEXTURE`
	// (`textures/gui/toasts.png`), a PUBLIC interface field on that version — at u=0, v=0,
	// which is exactly what vanilla's own AdvancementToast blits there.
	//? if >=1.21 {
	private static final Identifier BACKGROUND_SPRITE = Identifier.withDefaultNamespace("toast/advancement");
	//?}
	private static final long DISPLAY_TIME_MS = 5000;

	private final SkillType skill;
	private final int fromLevel;
	private final int newLevel;
	private final ItemStack icon;
	//? if >=1.21.11 {
	private Toast.Visibility wantedVisibility = Toast.Visibility.HIDE;
	//?} else {
	/*private boolean soundPlayed;
	*///?}

	public SkillLevelUpToast(final SkillType skill, final int fromLevel, final int newLevel) {
		this.skill = skill;
		this.fromLevel = fromLevel;
		this.newLevel = newLevel;
		this.icon = new ItemStack(skill.icon());
	}

	//? if >=1.21.11 {
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
	//?} else {
	/*// The single 1.21.1 hook: draw, decide the next visibility, and — since the version has
	// no getSoundEvent equivalent — play the milestone jingle on the first frame. The timeout
	// arithmetic and the milestone rule are the same expressions as above, so behaviour does
	// not fork, only the plumbing does.
	@Override
	public Toast.Visibility render(final GuiGraphics graphics, final ToastComponent manager,
			final long fullyVisibleForMs) {
		if (!this.soundPlayed) {
			this.soundPlayed = true;

			if (this.crossesMilestone()) {
				Minecraft.getInstance().getSoundManager()
						.play(SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0F));
			}
		}

		this.draw(graphics, Minecraft.getInstance().font);

		return fullyVisibleForMs >= DISPLAY_TIME_MS * manager.getNotificationDisplayTimeMultiplier()
				? Toast.Visibility.HIDE
				: Toast.Visibility.SHOW;
	}
	*///?}

	private boolean crossesMilestone() {
		return (this.fromLevel < 50 && this.newLevel >= 50) || (this.fromLevel < 100 && this.newLevel >= 100);
	}

	// Toast's draw hook is extractRenderState on 26.x and render on 1.21.11; `text` is
	// `drawString` and `fakeItem` is `renderFakeItem` there. On 1.21.1 the interface has no
	// draw hook of its own, so this becomes a plain private method called from render(...)
	// above — which is also why the @Override moved inside the chain.
	//? if >=26.1 {
	@Override
	public void extractRenderState(final GuiGraphicsExtractor graphics, final Font font, final long fullyVisibleForMs) {
	//?} elif >=1.21.11 {
	/*@Override
	public void render(final GuiGraphics graphics, final Font font, final long fullyVisibleForMs) {
	*///?} else {
	/*private void draw(final GuiGraphics graphics, final Font font) {
	*///?}
		//? if >=1.21.11 {
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BACKGROUND_SPRITE, 0, 0, this.width(), this.height());
		//?} elif >=1.21 {
		/*graphics.blitSprite(BACKGROUND_SPRITE, 0, 0, this.width(), this.height());
		*///?} else {
		/*graphics.blit(Toast.TEXTURE, 0, 0, 0, 0, this.width(), this.height());
		*///?}
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
