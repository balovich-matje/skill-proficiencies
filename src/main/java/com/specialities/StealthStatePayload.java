package com.specialities;

// See SkillUpdatePayload for why the supertype forks below 1.20.5, why the `FabricPacket` arm
// is scoped `fabric` once a non-Fabric node below that line exists, why the forge arm carries
// no supertype at all, and why the wire format does not fork on any of it.
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
 * Server -> client stealth state transition, driving the sneaking vignette:
 * NONE (no overlay), HIDDEN (dark edges: sneaking near unaware hostiles),
 * DETECTED (a hostile spotted the sneaking player: light flash + cue).
 */
public record StealthStatePayload(int state)
		//? if >=1.20.5 {
		implements CustomPacketPayload {
		//?} elif fabric {
		/*implements FabricPacket {
		*///?} else {
		/*{
		*///?}
	public static final int NONE = 0;
	public static final int HIDDEN = 1;
	public static final int DETECTED = 2;

	//? if >=1.20.5 {
	public static final CustomPacketPayload.Type<StealthStatePayload> TYPE =
			new CustomPacketPayload.Type<>(Specialities.id("stealth_state"));

	public static final StreamCodec<RegistryFriendlyByteBuf, StealthStatePayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, StealthStatePayload::state,
			StealthStatePayload::new);
	//?} elif fabric {
	/*public static final PacketType<StealthStatePayload> TYPE = PacketType.create(
			Specialities.id("stealth_state"),
			buf -> new StealthStatePayload(buf.readVarInt()));
	*///?}

	// Nothing on the forge arm — `ForgeNet.writeStealthState` / `readStealthState` are this
	// payload's codec there. The three state constants above stay shared on every node: they
	// are the protocol's own values and `StealthVignette` reads them.
	//? if >=1.20.5 {
	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
	//?} elif fabric {
	/*@Override
	public void write(final FriendlyByteBuf buf) {
		buf.writeVarInt(this.state);
	}

	@Override
	public PacketType<?> getType() {
		return TYPE;
	}
	*///?}
}
