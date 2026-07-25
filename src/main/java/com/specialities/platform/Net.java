package com.specialities.platform;

import net.minecraft.server.level.ServerPlayer;

/**
 * Seam 2 of three (design {@code docs/MULTIVERSION.md} §2) — clientbound
 * payloads: their registration, and the two sends the server does.
 *
 * <p>Why this is a seam and not a {@code //?} block: on 1.20.1 the whole payload
 * stack is missing. {@code CustomPacketPayload}, {@code StreamCodec},
 * {@code ByteBufCodecs}, {@code RegistryFriendlyByteBuf} and
 * {@code PayloadTypeRegistry} are all absent, and the replacement
 * ({@code FabricPacket} + {@code PacketType.create}) needs different payload
 * *types*, not just a different call. NeoForge and Forge fold registration and
 * the receiver into one call each.
 *
 * <p><b>The wire ids and the field order are frozen</b> —
 * {@code specialities:skill_update} carries (skillId, fromTotalXp, totalXp,
 * fromLevel, level) and {@code specialities:stealth_state} carries (state) — so a
 * client and a server built from different nodes of the same Minecraft version
 * stay compatible. That is why the send methods take loose values rather than a
 * payload object: below 1.20.5 the payload class itself has a different
 * supertype, so only the implementation may name it.
 *
 * <p>Client-side receiver registration is deliberately NOT here; see the note in
 * {@code client/SpecialitiesClient}.
 */
public interface Net {
	Net INSTANCE = new FabricNet();

	/** Registers the clientbound payload types. Called once from common init. */
	void registerClientbound();

	void sendSkillUpdate(ServerPlayer player, String skillId,
			int fromTotalXp, int totalXp, int fromLevel, int level);

	void sendStealthState(ServerPlayer player, int state);
}
