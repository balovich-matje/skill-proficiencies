package com.specialities.client.mixin;

// `net.minecraft.client.renderer.item.properties.numeric.UseDuration` is the class this
// mixin targets and it does not exist below 1.21.11 — no such package in the 1.21.1 mojmap,
// and no `net.minecraft.world.entity.ItemOwner` either (the item-model property system that
// owns it arrived at 1.21.4). The real boundary is therefore above 1.21.1 and at or below
// 1.21.11; `>=1.21.11` is the frozen predicate that classifies every registered node
// correctly.
//
// The class body is only HALF of it: a mixin config naming a class that is not in the jar is
// a hard crash at load, not a warning, so the entry has to leave
// `specialities.client.mixins.json` on the same nodes. It CANNOT be done with a `//?` block
// in that file, whatever conventions §4 says — measured: Stonecutter 0.9.7's default file
// handlers cover `java, scala, sc, groovy, gradle, json5, kt, kts, fsh, vsh, cfg, aw,
// accesswidener, ct, classtweaker, yml, yaml` and NOT `json`
// (`controller/file/Defaults.kt`), and `StonecutterBuildImpl` filters the prepare task's
// input to exactly those extensions, so a directive in a `.json` file is copied through
// verbatim and does nothing. It is instead done with a per-node override at
// `versions/1.21.1-fabric/src/client/resources/specialities.client.mixins.json`, which the
// generate task honours (`exclude { relativePath.getFile(localSourceFile).exists() }`).
// Keep the two in step when adding a client mixin.
//? if >=1.21.11 {
import com.specialities.skills.Skill;
import com.specialities.skills.SkillManager;
import com.specialities.skills.Tuning;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.numeric.UseDuration;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;

// The bow's pull animation is driven by the `minecraft:use_duration` item model property
// (vanilla has no bow quick charge, so it never scales). Scale the reported use time for
// bows so the visual draw matches the archery skill's faster charge from BowItemMixin.
//
// Written with `//` comments, not javadoc, on purpose: this class body is a Stonecutter
// branch and a disabled branch is wrapped in `/* ... *\/`, so a `*\/` inside it would close
// the branch comment early (conventions §5e-ter).
@Mixin(UseDuration.class)
public abstract class UseDurationMixin {
	@ModifyReturnValue(method = "get", at = @At("RETURN"))
	private float specialities$fasterBowPull(final float original, final ItemStack itemStack,
			final ClientLevel level, final ItemOwner owner, final int seed) {
		if (original <= 0.0F || !(itemStack.getItem() instanceof BowItem)) {
			return original;
		}

		if (((UseDuration) (Object) this).remaining()) {
			return original;
		}

		LivingEntity entity = owner == null ? null : owner.asLivingEntity();
		if (!(entity instanceof Player player)) {
			return original;
		}

		int skillLevel = SkillManager.get(player).level(Skill.ARCHERY);
		if (skillLevel <= 0) {
			return original;
		}

		return original / Tuning.recoveryTimeMultiplier(skillLevel);
	}
}
//?}
