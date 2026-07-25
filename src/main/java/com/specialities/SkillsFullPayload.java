package com.specialities;

// THIS FILE EXISTS ON TWO NODES, and on neither of the other five does it declare a type.
// Everything below the `package` line, including the type declaration, sits inside the `//?`
// chain, so above 1.20.5 this is a legal compilation unit with no type in it and produces no
// `.class` at all (conventions §4; `client/mixin/GuiMixin` is the other file in the tree built
// this way).
//
// Why it is a below-1.20.5 type rather than a shared one: neither `1.20.1-fabric` nor
// `1.20.1-forge` can push the skills state to its client for free. fabric-api 0.92.11 has no
// attachment sync and Forge capabilities have no sync mechanism at all (design R-03), so this
// full-state push is the only way either node's client ever learns the skill map it renders.
// Every node from 1.20.5 up gets the same data pushed by
// `syncWith(PlayerSkills.STREAM_CODEC, targetOnly())` and registering a third wire id there
// would be a protocol change for no gain.
//
// Wire id `specialities:skills_full`; body is a varint count followed by that many
// (utf, varint) pairs — byte-for-byte the encoding PlayerSkills.STREAM_CODEC already uses on
// the syncing nodes, so the two halves of R-03 cannot drift.
//
// THE CHAIN IS FLAT, THREE ARMS, and it is flat on purpose. The obvious shape — keep the
// existing `>=1.20.5 / else` gate and nest a `fabric` vs `forge` fork inside the disabled
// arm — needs a directive inside an already-disabled branch and therefore the `*` -> `^`
// marker escalation (conventions §4), which is the case that fails silently when it is got
// wrong. A flat `>=1.20.5 / elif fabric / else` chain needs none of that. The price is that
// the record HEADER (one component) and the `PlayerSkills` import appear in two arms; forking
// four separate fragments inside one shared body would have saved those two lines and cost
// far more readability.
//
// THE FORGE ARM is a plain record with no supertype and no members: LexForge's
// `SimpleChannel.registerMessage(index, class, encoder, decoder, handler)` takes the codec
// functions as arguments, so `platform/ForgeNet.writeSkillsFull` / `readSkillsFull` are this
// payload's encoding there — the same varint-count-then-pairs body as the Fabric arm.
//
// Javadoc is written with `//` here: a `*/` inside a disabled branch would close the
// branch comment early (conventions §5e-ter).
//? if >=1.20.5 {
//?} elif fabric {
/*import com.specialities.skills.PlayerSkills;

import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.network.FriendlyByteBuf;

// Server -> client push of a player's WHOLE skill map. Sent on join by
// SkillStore.resyncSkills; the incremental SkillUpdatePayload carries every later
// change, so this is not repeated.
public record SkillsFullPayload(PlayerSkills skills) implements FabricPacket {
	public static final PacketType<SkillsFullPayload> TYPE = PacketType.create(
			Specialities.id("skills_full"),
			SkillsFullPayload::read);

	private static SkillsFullPayload read(final FriendlyByteBuf buf) {
		int size = buf.readVarInt();
		Map<String, Integer> map = new HashMap<>(size);

		for (int i = 0; i < size; i++) {
			map.put(buf.readUtf(), buf.readVarInt());
		}

		return new SkillsFullPayload(new PlayerSkills(Map.copyOf(map)));
	}

	@Override
	public void write(final FriendlyByteBuf buf) {
		buf.writeVarInt(this.skills.xp().size());
		this.skills.xp().forEach((id, total) -> {
			buf.writeUtf(id);
			buf.writeVarInt(total);
		});
	}

	@Override
	public PacketType<?> getType() {
		return TYPE;
	}
}
*///?} else {
/*import com.specialities.skills.PlayerSkills;

// Server -> client push of a player's WHOLE skill map. Sent on join by
// SkillStore.resyncSkills; the incremental SkillUpdatePayload carries every later change, so
// this is not repeated. No TYPE, no write, no getType: ForgeNet registers the class against
// wire index 2 and supplies the codec functions.
public record SkillsFullPayload(PlayerSkills skills) {
}
*///?}
