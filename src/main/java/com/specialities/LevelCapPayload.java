package com.specialities;

// See SkillUpdatePayload for why the supertype forks below 1.20.5, why the `FabricPacket` arm
// is scoped `fabric` once a non-Fabric node below that line exists, why the forge arm carries
// no supertype at all, and why the wire format does not fork on any of it. This record is the
// same shape as StealthStatePayload, field for field: one varint.
//? if >=1.20.5 {
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?} elif fabric {
/*import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;

import net.minecraft.network.FriendlyByteBuf;
*///?}

/**
 * Server -> client: the effective skill level cap on this server (100, or the
 * {@code extendedMaxLevel} when extended levels are on — GitHub issue #6). Sent on
 * every join and again whenever an integrated server's config changes; the client
 * derives every displayed level from stored XP through this cap, so a client with a
 * different local config still shows the server's levels. Wire id
 * {@code specialities:level_cap}, body one varint.
 */
public record LevelCapPayload(int maxLevel)
		//? if >=1.20.5 {
		implements CustomPacketPayload {
		//?} elif fabric {
		/*implements FabricPacket {
		*///?} else {
		/*{
		*///?}
	//? if >=1.20.5 {
	public static final CustomPacketPayload.Type<LevelCapPayload> TYPE =
			new CustomPacketPayload.Type<>(Specialities.id("level_cap"));

	public static final StreamCodec<RegistryFriendlyByteBuf, LevelCapPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, LevelCapPayload::maxLevel,
			LevelCapPayload::new);
	//?} elif fabric {
	/*public static final PacketType<LevelCapPayload> TYPE = PacketType.create(
			Specialities.id("level_cap"),
			buf -> new LevelCapPayload(buf.readVarInt()));
	*///?}

	// Nothing on the forge arm — `ForgeNet.writeLevelCap` / `readLevelCap` are this
	// payload's codec there.
	//? if >=1.20.5 {
	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
	//?} elif fabric {
	/*@Override
	public void write(final FriendlyByteBuf buf) {
		buf.writeVarInt(this.maxLevel);
	}

	@Override
	public PacketType<?> getType() {
		return TYPE;
	}
	*///?}
}
