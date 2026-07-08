package com.specialities;

import com.specialities.skills.PlayerSkills;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

public final class ModAttachments {
	/**
	 * Server-authoritative skill data; persisted with the player and synced to the
	 * owning client only.
	 */
	public static final AttachmentType<PlayerSkills> SKILLS = AttachmentRegistry.create(
			Specialities.id("skills"),
			builder -> builder
					.initializer(() -> PlayerSkills.EMPTY)
					.persistent(PlayerSkills.CODEC)
					.syncWith(PlayerSkills.STREAM_CODEC, AttachmentSyncPredicate.targetOnly())
					.copyOnDeath());

	/** Remaining ricochets on an arrow spawned by the archery passive. Transient. */
	public static final AttachmentType<Integer> RICOCHET_BOUNCES =
			AttachmentRegistry.create(Specialities.id("ricochet_bounces"));

	/** Entity id a ricochet arrow flies away from and must not hit again. Transient. */
	public static final AttachmentType<Integer> RICOCHET_IGNORE =
			AttachmentRegistry.create(Specialities.id("ricochet_ignore"));

	/** Set on a mob once it has been stealth-critted — each enemy only falls for it once. Transient. */
	public static final AttachmentType<Boolean> STEALTH_CRIT_DONE =
			AttachmentRegistry.create(Specialities.id("stealth_crit_done"));

	private ModAttachments() {
	}

	public static void initialize() {
		// Forces static initialization at mod init time.
	}
}
