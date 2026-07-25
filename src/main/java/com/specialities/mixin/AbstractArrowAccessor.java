package com.specialities.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

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
}
