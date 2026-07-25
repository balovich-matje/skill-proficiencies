package com.specialities.mixin;

import java.util.Comparator;
import java.util.List;

import com.specialities.ModTags;
import com.specialities.platform.SkillStore;
import com.specialities.skills.Skill;
//? if >=1.21 {
//?} else {
/*import com.specialities.skills.SkillCategories;
*///?}
import com.specialities.skills.SkillManager;
import com.specialities.skills.Tuning;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
// The arrow classes moved into a `.arrow` subpackage at 1.21.11; below that they sit
// directly in `...entity.projectile` (1.21.1 mojmap: `net.minecraft.world.entity
// .projectile.AbstractArrow -> cnd`, `...projectile.Arrow -> cnf`). This boundary cuts
// across three files (SkillCategories + AbstractArrowMixin + AbstractArrowAccessor) —
// they must always fork together.
//? if >=1.21.11 {
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
//?} else {
/*import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
*///?}
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
// Only the bounce arrow's Punch copy names these, and only below 1.21, where the arrow
// carries its knockback in a field instead of re-deriving it from `firedFromWeapon`.
//? if >=1.21 {
//?} else {
/*import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
*///?}
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

		if (SkillStore.INSTANCE.getRicochetBounces(self) == null) {
			return;
		}

		Entity victim = hitResult.getEntity();
		Integer ignoredId = SkillStore.INSTANCE.getRicochetIgnore(self);

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

		// Design R-04: no `getWeaponItem()` below 1.21. SkillCategories owns the substitute
		// so that every read of "what fired this projectile" goes through one definition.
		//? if >=1.21 {
		ItemStack weapon = self.getWeaponItem();
		//?} else {
		/*ItemStack weapon = SkillCategories.weaponItem(self);
		*///?}
		Integer remaining = SkillStore.INSTANCE.getRicochetBounces(self);
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

		// The pickup-item and fired-from-weapon constructor arguments are 1.21 additions; on
		// 1.20.1 `Arrow(Level, double, double, double)` is the whole constructor and the
		// pickup item is implicitly `Items.ARROW`. The bounce arrow inherits the firing
		// weapon by being stamped with it, which is what keeps `weapon` meaningful for the
		// NEXT bounce — the chain reads its own weapon on every hop.
		//
		// What `firedFromWeapon` buys the bounce arrow on the newer nodes, and what has to be
		// reproduced here, is exactly ONE thing: PUNCH. Measured rather than assumed, both
		// halves:
		//
		//   - `AbstractArrow.doKnockback` on 1.21.1 reads `firedFromWeapon` (offset 1/31) and
		//     calls `EnchantmentHelper.modifyKnockback(level, that stack, target, source, 0F)`
		//     at offset 37, then scales `value * 0.6 * max(0, 1 - KNOCKBACK_RESISTANCE)` and
		//     `push(x, 0.1, z)`. 1.20.1 does the same arithmetic from the arrow's own
		//     `knockback` field (offsets 346-387 of `onHitEntity`), which vanilla's BowItem
		//     sets at SHOT time — so the bounce arrow, which no bow ever shot, had none.
		//     `punch.json` in the 1.21.1 jar is `minecraft:knockback` / `add` / `linear base
		//     1.0 per_level_above_first 1.0`, i.e. the value is the level, so copying the
		//     level into `setKnockback` is the same number, not an approximation.
		//   - FIRE IS DELIBERATELY NOT COPIED, though the review asked whether it should be.
		//     `flame.json` in the same jar is `minecraft:projectile_spawned` / `ignite`, and
		//     that effect is applied by the weapon's own shoot path
		//     (`EnchantmentHelper.onProjectileSpawned`) — which a ricochet bypasses on EVERY
		//     node, `firedFromWeapon` or not. A modern bounce arrow is not on fire either, so
		//     copying the original arrow's fire state here would not close a gap, it would
		//     open a 1.20.1-only one. Same reasoning retires Piercing and tipped-arrow
		//     effects: shot-time on 1.20.1, spawn-time effects on 1.21+, absent from the
		//     bounce arrow everywhere. Power needs nothing — `setBaseDamage` below copies it
		//     on this node because BowItem bakes it into `baseDamage`, and the newer nodes
		//     re-derive it from the weapon at hit time.
		//? if >=1.21 {
		Arrow next = new Arrow(serverLevel, from.x, from.y, from.z, new ItemStack(Items.ARROW), weapon);
		//?} else {
		/*Arrow next = new Arrow(serverLevel, from.x, from.y, from.z);

		if (weapon != null) {
			SkillStore.INSTANCE.setFiringWeapon(next, weapon);
			next.setKnockback(EnchantmentHelper.getItemEnchantmentLevel(Enchantments.PUNCH_ARROWS, weapon));
		}
		*///?}
		next.setOwner(player);
		next.pickup = AbstractArrow.Pickup.DISALLOWED;
		next.setBaseDamage(((AbstractArrowAccessor) self).specialities$getBaseDamage());
		SkillStore.INSTANCE.setRicochetBounces(next, bounces - 1);
		SkillStore.INSTANCE.setRicochetIgnore(next, victim.getId());

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
