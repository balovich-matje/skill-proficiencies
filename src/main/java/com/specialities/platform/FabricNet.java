package com.specialities.platform;

import com.specialities.SkillUpdatePayload;
import com.specialities.StealthStatePayload;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.server.level.ServerPlayer;

/**
 * Fabric implementation of {@link Net}: the two {@code CustomPacketPayload}
 * records, registered on the play-phase clientbound registry and sent with
 * {@code ServerPlayNetworking.send}.
 */
final class FabricNet implements Net {
	@Override
	public void registerClientbound() {
		// fabric-api renamed the play-phase payload registries: `clientboundPlay()`
		// / `serverboundPlay()` on the 26.x pins (fabric-networking-api-v1 6.3.1 and
		// 6.3.3), `playS2C()` / `playC2S()` in 0.141.5 (5.1.6, the 1.21.11 pin).
		// Only the accessor name moved — both return
		// PayloadTypeRegistry<RegistryFriendlyByteBuf>, so the payload types and
		// codecs are unchanged and the wire ids stay frozen.
		//? if >=26.1 {
		PayloadTypeRegistry.clientboundPlay().register(SkillUpdatePayload.TYPE, SkillUpdatePayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(StealthStatePayload.TYPE, StealthStatePayload.CODEC);
		//?} else {
		/*PayloadTypeRegistry.playS2C().register(SkillUpdatePayload.TYPE, SkillUpdatePayload.CODEC);
		PayloadTypeRegistry.playS2C().register(StealthStatePayload.TYPE, StealthStatePayload.CODEC);
		*///?}
	}

	@Override
	public void sendSkillUpdate(final ServerPlayer player, final String skillId,
			final int fromTotalXp, final int totalXp, final int fromLevel, final int level) {
		ServerPlayNetworking.send(player,
				new SkillUpdatePayload(skillId, fromTotalXp, totalXp, fromLevel, level));
	}

	@Override
	public void sendStealthState(final ServerPlayer player, final int state) {
		ServerPlayNetworking.send(player, new StealthStatePayload(state));
	}
}
