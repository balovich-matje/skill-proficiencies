package com.specialities;

// THIS FILE EXISTS ON ONE NODE. Everything below the `package` line, including the type
// declaration, sits inside the `//?` block, so on every other node this is a legal
// compilation unit with no type in it and produces no `.class` at all (conventions §4;
// `client/mixin/GuiMixin` is the other file in the tree built this way).
//
// Why it is a 1.20.1-only type rather than a shared one: fabric-api 0.92.11 has no
// attachment sync (design R-03), so this full-state push is the only way that node's
// client ever learns the skill map it renders. Every node from 1.20.5 up gets the same
// data pushed by `syncWith(PlayerSkills.STREAM_CODEC, targetOnly())` and registering a
// third wire id there would be a protocol change for no gain.
//
// Wire id `specialities:skills_full`; body is a varint count followed by that many
// (utf, varint) pairs — byte-for-byte the encoding PlayerSkills.STREAM_CODEC already
// uses on the syncing nodes, so the two halves of R-03 cannot drift.
//
// Javadoc is written with `//` here: a `*/` inside a disabled branch would close the
// branch comment early (conventions §5e-ter).
//? if >=1.20.5 {
//?} else {
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
*///?}
