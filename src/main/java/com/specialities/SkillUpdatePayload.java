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
//? if >=1.20.5 {
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?} else {
/*import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;

import net.minecraft.network.FriendlyByteBuf;
*///?}

/**
 * Server -> client notification that a skill gained XP (and possibly leveled up).
 * Carries the previous state so the client can animate the bar and show
 * "Increased x -> y" on level-up toasts.
 */
public record SkillUpdatePayload(String skillId, int fromTotalXp, int totalXp, int fromLevel, int level)
		/*? if >=1.20.5 {*/implements CustomPacketPayload {
		/*?} else *///implements FabricPacket {
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
	//?} else {
	/*public static final PacketType<SkillUpdatePayload> TYPE = PacketType.create(
			Specialities.id("skill_update"),
			buf -> new SkillUpdatePayload(buf.readUtf(), buf.readVarInt(), buf.readVarInt(),
					buf.readVarInt(), buf.readVarInt()));
	*///?}

	public boolean levelUp() {
		return this.level > this.fromLevel;
	}

	//? if >=1.20.5 {
	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
	//?} else {
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
