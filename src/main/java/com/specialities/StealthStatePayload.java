package com.specialities;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client stealth state transition, driving the sneaking vignette:
 * NONE (no overlay), HIDDEN (dark edges: sneaking near unaware hostiles),
 * DETECTED (a hostile spotted the sneaking player: light flash + cue).
 */
public record StealthStatePayload(int state) implements CustomPacketPayload {
	public static final int NONE = 0;
	public static final int HIDDEN = 1;
	public static final int DETECTED = 2;

	public static final CustomPacketPayload.Type<StealthStatePayload> TYPE =
			new CustomPacketPayload.Type<>(Specialities.id("stealth_state"));

	public static final StreamCodec<RegistryFriendlyByteBuf, StealthStatePayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, StealthStatePayload::state,
			StealthStatePayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
