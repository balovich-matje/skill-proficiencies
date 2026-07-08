package com.specialities.mixin;

import java.util.Comparator;
import java.util.List;

import com.specialities.ModAttachments;
import com.specialities.ModTags;
import com.specialities.skills.Skill;
import com.specialities.skills.SkillManager;
import com.specialities.skills.Tuning;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Archery ricochet: at skill 50/100, arrows fired from bows/crossbows bounce
 * off a hostile target to the nearest other hostile. Ricochet arrows never hit
 * anything that isn't hostile (no friendly fire on players/neutrals/pets).
 */
@Mixin(AbstractArrow.class)
public abstract class AbstractArrowMixin {
	@Unique
	private double specialities$impactSpeed;

	@Inject(method = "onHitEntity", at = @At("HEAD"), cancellable = true)
	private void specialities$guardRicochetTargets(final EntityHitResult hitResult, final CallbackInfo ci) {
		AbstractArrow self = (AbstractArrow) (Object) this;
		this.specialities$impactSpeed = self.getDeltaMovement().length();

		if (!(self.level() instanceof ServerLevel)) {
			return;
		}

		if (((AttachmentTarget) self).getAttached(ModAttachments.RICOCHET_BOUNCES) == null) {
			return;
		}

		Entity victim = hitResult.getEntity();
		Integer ignoredId = ((AttachmentTarget) self).getAttached(ModAttachments.RICOCHET_IGNORE);

		if (ignoredId != null && victim.getId() == ignoredId) {
			// Still overlapping the previous victim: fly through it.
			ci.cancel();
			return;
		}

		if (!(victim instanceof Enemy)) {
			// Ricochet arrows must never friendly-fire; vanish instead.
			self.discard();
			ci.cancel();
		}
	}

	@Inject(method = "onHitEntity", at = @At("TAIL"))
	private void specialities$ricochet(final EntityHitResult hitResult, final CallbackInfo ci) {
		AbstractArrow self = (AbstractArrow) (Object) this;

		if (!(self.level() instanceof ServerLevel serverLevel)) {
			return;
		}

		// Only a consumed (non-piercing, non-deflected) hit chains onward.
		if (!self.isRemoved() || !(self.getOwner() instanceof ServerPlayer player)) {
			return;
		}

		Entity victim = hitResult.getEntity();
		if (!(victim instanceof Enemy)) {
			return;
		}

		ItemStack weapon = self.getWeaponItem();
		Integer remaining = ((AttachmentTarget) self).getAttached(ModAttachments.RICOCHET_BOUNCES);
		int bounces;

		if (remaining != null) {
			bounces = remaining;
		} else {
			// Original arrow: only bow/crossbow shots ricochet.
			if (weapon == null || !weapon.is(ModTags.RANGED_WEAPONS)) {
				return;
			}

			bounces = Tuning.ricochets(SkillManager.get(player).level(Skill.ARCHERY));
		}

		if (bounces <= 0) {
			return;
		}

		LivingEntity target = specialities$nearestHostile(serverLevel, victim);
		if (target == null) {
			return;
		}

		Vec3 from = victim.position().add(0.0, victim.getBbHeight() * 0.5, 0.0);
		Vec3 to = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0);
		Vec3 direction = to.subtract(from).normalize();

		Arrow next = new Arrow(serverLevel, from.x, from.y, from.z, new ItemStack(Items.ARROW), weapon);
		next.setOwner(player);
		next.pickup = AbstractArrow.Pickup.DISALLOWED;
		next.setBaseDamage(((AbstractArrowAccessor) self).specialities$getBaseDamage());
		((AttachmentTarget) next).setAttached(ModAttachments.RICOCHET_BOUNCES, bounces - 1);
		((AttachmentTarget) next).setAttached(ModAttachments.RICOCHET_IGNORE, victim.getId());

		float speed = (float) Math.max(this.specialities$impactSpeed, 1.5);
		next.shoot(direction.x, direction.y, direction.z, speed, 0.0F);
		serverLevel.addFreshEntity(next);
		serverLevel.playSound(null, from.x, from.y, from.z, SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 0.6F, 1.4F);
	}

	@Unique
	private static LivingEntity specialities$nearestHostile(final ServerLevel level, final Entity victim) {
		List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class,
				victim.getBoundingBox().inflate(Tuning.RICOCHET_RANGE),
				candidate -> candidate instanceof Enemy && candidate.isAlive() && candidate != victim);

		return candidates.stream()
				.min(Comparator.comparingDouble(victim::distanceToSqr))
				.orElse(null);
	}
}
