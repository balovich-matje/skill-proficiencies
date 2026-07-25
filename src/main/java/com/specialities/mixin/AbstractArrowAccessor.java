package com.specialities.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
//? if >=1.21 {
//?} else {
/*import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.world.item.ItemStack;
*///?}

// The arrow classes moved into a `.arrow` subpackage at 1.21.11; below that they sit
// directly in `...entity.projectile` (1.21.1 mojmap: `net.minecraft.world.entity
// .projectile.AbstractArrow -> cnd`, `...projectile.Arrow -> cnf`). This boundary cuts
// across three files (SkillCategories + AbstractArrowMixin + AbstractArrowAccessor) —
// they must always fork together.
//? if >=1.21.11 {
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
//?} else {
/*import net.minecraft.world.entity.projectile.AbstractArrow;
*///?}

@Mixin(AbstractArrow.class)
public interface AbstractArrowAccessor {
	@Accessor("baseDamage")
	double specialities$getBaseDamage();

	// Below 1.21 there is no `AbstractArrow.getWeaponItem()` (design R-04). The bow and
	// crossbow stamp the firing weapon onto the projectile they spawn, but a thrown trident
	// cannot be stamped without a fourth mixin CLASS, and a new class means a per-node copy
	// of the 15-entry mixin config (`//?` does not work in JSON) whose drift would be silent
	// and fatal. It does not need one: on that version `getPickupItem()` is
	// `protected abstract` on AbstractArrow and `ThrownTrident` overrides it to return the
	// trident stack itself, so the projectile already knows. Reading it through this invoker
	// gives `SkillCategories.weaponItem` the same answer `getWeaponItem()` gives above for
	// every case those three balance tests can reach — arrows report the arrow item, which
	// is in neither weapon tag, exactly as a null weapon would be.
	//? if >=1.21 {
	//?} else {
	/*@Invoker("getPickupItem")
	ItemStack specialities$getPickupItem();
	*///?}
}
