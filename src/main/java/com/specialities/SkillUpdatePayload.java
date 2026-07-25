package com.specialities;

// The whole vanilla payload stack is 1.20.5+: CustomPacketPayload, StreamCodec,
// ByteBufCodecs and RegistryFriendlyByteBuf are all absent on 1.20.1 (checked against
// that version's mojmap — none of the four classes exists). 0.92.11's replacement is
// fabric-api's own `FabricPacket` + `PacketType`, so the record's SUPERTYPE moves and
// the codec becomes a write method plus a reader function on the type. Everything else
// — the record components, their order, and `levelUp()` — is shared.
//
// THE WIRE FORMAT IS UNCHANGED, and that is the point of writing it out by hand rather
// than reaching for a different encoding: `ByteBufCodecs.STRING_UTF8` is `writeUtf` and
// `VAR_INT` is `writeVarInt`, in this order, on every node. A 1.20.1 client and a
// 1.20.1 server built from any two variants of this mod stay compatible.
//
// THE THIRD ARM IS THE LOADER AXIS, and it exists because `<1.20.5` alone is not the same
// set as "the nodes with fabric-api's FabricPacket": `1.20.1-forge` is also below that line
// and `net.fabricmc.fabric.api.networking.v1.FabricPacket` is a FABRIC type, so the arm that
// names it is scoped `fabric` (conventions §4's loader-fork rule — the same frozen boundary
// scoped to the loader that owns the API, not a new predicate). On the forge node the record
// carries NO supertype at all: `platform/ForgeNet` owns the encode/decode functions, because
// LexForge's `SimpleChannel.registerMessage(index, class, encoder, decoder, handler)` takes
// them as arguments instead of reading them off the payload type. This answers the prep
// plan's Q6 for this node: no `TYPE`, no `write`, no `getType` here.
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
 * Server -> client notification that a skill gained XP (and possibly leveled up).
 * Carries the previous state so the client can animate the bar and show
 * "Increased x -> y" on level-up toasts.
 */
// THE COMPONENT LIST IS NOT DUPLICATED, deliberately. It IS the frozen wire format (id
// `specialities:skill_update` carries skillId, fromTotalXp, totalXp, fromLevel, level in
// that order), so repeating it once per arm — which is what a three-arm chain over the whole
// record declaration would cost — is the silent-divergence hazard §3 warns about. Only the
// `implements` FRAGMENT forks, block-form because three arms cannot be inline (conventions
// §4: a single-line `elif` closes the block, so it can only be the last arm). Stonecutter is
// textual and does not parse Java, so an arm that supplies nothing but `{` is fine — the
// shared body below closes it.
public record SkillUpdatePayload(String skillId, int fromTotalXp, int totalXp, int fromLevel, int level)
		//? if >=1.20.5 {
		implements CustomPacketPayload {
		//?} elif fabric {
		/*implements FabricPacket {
		*///?} else {
		/*{
		*///?}
	//? if >=1.20.5 {
	public static final CustomPacketPayload.Type<SkillUpdatePayload> TYPE =
			new CustomPacketPayload.Type<>(Specialities.id("skill_update"));

	public static final StreamCodec<RegistryFriendlyByteBuf, SkillUpdatePayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, SkillUpdatePayload::skillId,
			ByteBufCodecs.VAR_INT, SkillUpdatePayload::fromTotalXp,
			ByteBufCodecs.VAR_INT, SkillUpdatePayload::totalXp,
			ByteBufCodecs.VAR_INT, SkillUpdatePayload::fromLevel,
			ByteBufCodecs.VAR_INT, SkillUpdatePayload::level,
			SkillUpdatePayload::new);
	//?} elif fabric {
	/*public static final PacketType<SkillUpdatePayload> TYPE = PacketType.create(
			Specialities.id("skill_update"),
			buf -> new SkillUpdatePayload(buf.readUtf(), buf.readVarInt(), buf.readVarInt(),
					buf.readVarInt(), buf.readVarInt()));
	*///?}

	public boolean levelUp() {
		return this.level > this.fromLevel;
	}

	// Nothing on the forge arm: `ForgeNet.writeSkillUpdate` / `readSkillUpdate` carry exactly
	// this encoding, in exactly this order, and are handed to `registerMessage` as method
	// references. The two halves cannot drift silently because they are the only two copies
	// and both are named in ForgeNet's javadoc.
	//? if >=1.20.5 {
	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
	//?} elif fabric {
	/*@Override
	public void write(final FriendlyByteBuf buf) {
		buf.writeUtf(this.skillId);
		buf.writeVarInt(this.fromTotalXp);
		buf.writeVarInt(this.totalXp);
		buf.writeVarInt(this.fromLevel);
		buf.writeVarInt(this.level);
	}

	@Override
	public PacketType<?> getType() {
		return TYPE;
	}
	*///?}
}
