package com.specialities;

import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

/**
 * Whether the damage being resolved right now came out of a player actually
 * swinging their weapon.
 *
 * <h2>Why this has to exist</h2>
 * A {@code DamageSource} cannot answer the question. Damage-over-time and
 * reflect effects are routinely built as {@code indirectMagic(player, player)}
 * or {@code thorns(player)} — deliberately, so a bleed kill still credits the
 * player who opened the wound — and in both of those the causing entity AND
 * the direct entity are the player, which is exactly what a swing looks like
 * to a {@code getDirectEntity() == player} test. So "is this a melee hit?"
 * answered yes for every tick of every passive proc, and the sneaking skill's
 * stealth crit was multiplying other mods' damage-over-time (the author found
 * it via Archetypes' "Death's Hand").
 *
 * <p>The fix is a positive signal rather than a list of exceptions. A swing is
 * a swing because it came through {@code Player.attack} (see
 * {@link com.specialities.mixin.PlayerMixin}), which is the one path a mouse
 * click takes. Anything a mod triggers off the side of a hit is outside that
 * call and reads as what it is.
 *
 * <h2>Nesting</h2>
 * {@link #begin} returns the previous swinger and {@link #end} puts it back, so
 * a swing that provokes a swing unwinds correctly instead of clearing the flag
 * its caller was relying on. Server thread only: the mixin does not open a
 * swing for the client-side copy of {@code attack}, and every reader is inside
 * {@code hurtServer}.
 */
public final class MeleeSwing {
	private static @Nullable Entity swinger;

	private MeleeSwing() {
	}

	/** Open a swing by this entity; hand the return value back to {@link #end}. */
	public static @Nullable Entity begin(final Entity entity) {
		Entity previous = swinger;
		swinger = entity;
		return previous;
	}

	/**
	 * Close the swing, restoring whatever was open around it. Always in a
	 * {@code finally}: a swing that throws must not leave the flag standing, or
	 * every later hit on the server reads as a swing until something overwrites
	 * it.
	 */
	public static void end(final @Nullable Entity previous) {
		swinger = previous;
	}

	/** Whether this entity is mid-swing right now. */
	public static boolean isSwinging(final Entity entity) {
		return swinger == entity;
	}
}
