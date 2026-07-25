package com.specialities.platform;

import com.specialities.Specialities;
import com.specialities.skills.PlayerSkills;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
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
 * Fabric implementation of {@link SkillStore}, holding the five attachment types
 * that used to live in {@code com.specialities.ModAttachments} — same ids, same
 * builders, same order. The ids are part of the on-disk world format
 * ({@code specialities:skills} carries every player's progress), so they are
 * frozen forever; renaming one orphans every existing world.
 */
final class FabricSkillStore implements SkillStore {
	/**
	 * Server-authoritative skill data; persisted with the player and synced to the
	 * owning client only.
	 */
	private static final AttachmentType<PlayerSkills> SKILLS = AttachmentRegistry.create(
			Specialities.id("skills"),
			builder -> builder
					.initializer(() -> PlayerSkills.EMPTY)
					.persistent(PlayerSkills.CODEC)
					.syncWith(PlayerSkills.STREAM_CODEC, AttachmentSyncPredicate.targetOnly())
					.copyOnDeath());

	/** Remaining ricochets on an arrow spawned by the archery passive. Transient. */
	private static final AttachmentType<Integer> RICOCHET_BOUNCES =
			AttachmentRegistry.create(Specialities.id("ricochet_bounces"));

	/** Entity id a ricochet arrow flies away from and must not hit again. Transient. */
	private static final AttachmentType<Integer> RICOCHET_IGNORE =
			AttachmentRegistry.create(Specialities.id("ricochet_ignore"));

	/** Set on a mob once it has been stealth-critted — each enemy only falls for it once. Transient. */
	private static final AttachmentType<Boolean> STEALTH_CRIT_DONE =
			AttachmentRegistry.create(Specialities.id("stealth_crit_done"));

	/** UUID of the player who last opened a brewing stand, for alchemy attribution. Transient. */
	private static final AttachmentType<String> BREWING_OWNER =
			AttachmentRegistry.create(Specialities.id("brewing_owner"));

	@Override
	public void initialize() {
		// The five AttachmentRegistry.create calls above run as this class's static
		// initializer, which the SkillStore.INSTANCE field forces on first touch.
		// Keeping an explicit call means that happens at the same point in common
		// init it always did, rather than whenever some unrelated code first reads
		// a skill.
	}

	@Override
	public PlayerSkills getSkills(final Player player) {
		return ((AttachmentTarget) player).getAttachedOrElse(SKILLS, PlayerSkills.EMPTY);
	}

	@Override
	public void setSkills(final Player player, final PlayerSkills skills) {
		((AttachmentTarget) player).setAttached(SKILLS, skills);
	}

	@Override
	public void resyncSkills(final ServerPlayer player) {
		// Nothing to do: `syncWith(STREAM_CODEC, targetOnly())` above makes
		// fabric-api push the attachment to the owning client on join and on every
		// change. The 1.20.1 node's implementation of this method is where design
		// R-03's full-state payload goes.
	}

	@Override
	public @Nullable Integer getRicochetBounces(final Entity arrow) {
		return ((AttachmentTarget) arrow).getAttached(RICOCHET_BOUNCES);
	}

	@Override
	public void setRicochetBounces(final Entity arrow, final int bounces) {
		((AttachmentTarget) arrow).setAttached(RICOCHET_BOUNCES, bounces);
	}

	@Override
	public @Nullable Integer getRicochetIgnore(final Entity arrow) {
		return ((AttachmentTarget) arrow).getAttached(RICOCHET_IGNORE);
	}

	@Override
	public void setRicochetIgnore(final Entity arrow, final int entityId) {
		((AttachmentTarget) arrow).setAttached(RICOCHET_IGNORE, entityId);
	}

	@Override
	public boolean isStealthCritDone(final Mob mob) {
		return Boolean.TRUE.equals(((AttachmentTarget) mob).getAttached(STEALTH_CRIT_DONE));
	}

	@Override
	public void markStealthCritDone(final Mob mob) {
		((AttachmentTarget) mob).setAttached(STEALTH_CRIT_DONE, true);
	}

	@Override
	public @Nullable String getBrewingOwner(final BlockEntity stand) {
		return ((AttachmentTarget) stand).getAttached(BREWING_OWNER);
	}

	@Override
	public void setBrewingOwner(final BlockEntity stand, final String uuid) {
		((AttachmentTarget) stand).setAttached(BREWING_OWNER, uuid);
	}
}
