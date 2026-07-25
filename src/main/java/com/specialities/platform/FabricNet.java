package com.specialities.platform;

import com.specialities.SkillUpdatePayload;
//? if >=1.20.5 {
//?} else {
/*import com.specialities.SkillsFullPayload;
*///?}
import com.specialities.StealthStatePayload;
import com.specialities.skills.PlayerSkills;

//? if >=1.20.5 {
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
//?}
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
		//
		// Below 1.20.5 there is no PayloadTypeRegistry at all: 0.92.11's payload API is
		// `FabricPacket` + `PacketType.create(Identifier, Function<FriendlyByteBuf, P>)`,
		// where the PacketType *is* the registration — the id and the reader live on the
		// type object and the receiver is looked up by it. So there is genuinely nothing
		// to register here, and touching the three TYPE fields is what forces their
		// classes to initialise at the same point in common init as on every other node.
		//? if >=26.1 {
		PayloadTypeRegistry.clientboundPlay().register(SkillUpdatePayload.TYPE, SkillUpdatePayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(StealthStatePayload.TYPE, StealthStatePayload.CODEC);
		//?} elif >=1.20.5 {
		/*PayloadTypeRegistry.playS2C().register(SkillUpdatePayload.TYPE, SkillUpdatePayload.CODEC);
		PayloadTypeRegistry.playS2C().register(StealthStatePayload.TYPE, StealthStatePayload.CODEC);
		*///?} else {
		/*SkillUpdatePayload.TYPE.getId();
		StealthStatePayload.TYPE.getId();
		SkillsFullPayload.TYPE.getId();
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

	//? if >=1.20.5 {
	//?} else {
	/*@Override
	public void sendSkillsFull(final ServerPlayer player, final PlayerSkills skills) {
		ServerPlayNetworking.send(player, new SkillsFullPayload(skills));
	}
	*///?}
}
