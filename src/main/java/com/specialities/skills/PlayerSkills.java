package com.specialities.skills;

import java.util.HashMap;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.specialities.api.SkillType;

// StreamCodec/RegistryFriendlyByteBuf are 1.20.5+. The DFU `CODEC` below is portable and
// is the one that matters — it is the on-disk format of `specialities:skills`.
//? if >=1.20.5 {
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
//?}

/**
 * Immutable per-player skill progress, stored as total accumulated XP per skill id.
 * Levels are derived from total XP via {@link Tuning}.
 */
public record PlayerSkills(Map<String, Integer> xp) {
	public static final PlayerSkills EMPTY = new PlayerSkills(Map.of());

	public static final Codec<PlayerSkills> CODEC = Codec.unboundedMap(Codec.STRING, Codec.INT)
			.xmap(PlayerSkills::new, PlayerSkills::xp);

	// The attachment's sync codec. Below 1.20.5 there is no attachment sync to give it to
	// and no StreamCodec to write it with, so it is gone there and `SkillsFullPayload`
	// carries exactly this encoding by hand instead (design R-03).
	//? if >=1.20.5 {
	public static final StreamCodec<RegistryFriendlyByteBuf, PlayerSkills> STREAM_CODEC = StreamCodec.of(
			(buf, skills) -> {
				buf.writeVarInt(skills.xp().size());
				skills.xp().forEach((id, total) -> {
					buf.writeUtf(id);
					buf.writeVarInt(total);
				});
			},
			buf -> {
				int size = buf.readVarInt();
				Map<String, Integer> map = new HashMap<>(size);
				for (int i = 0; i < size; i++) {
					map.put(buf.readUtf(), buf.readVarInt());
				}
				return new PlayerSkills(Map.copyOf(map));
			});
	//?}

	public int totalXp(final SkillType skill) {
		return this.xp.getOrDefault(skill.id(), 0);
	}

	public int level(final SkillType skill) {
		return Tuning.levelForTotalXp(this.totalXp(skill));
	}

	public boolean discovered(final SkillType skill) {
		return this.level(skill) > 0;
	}

	public PlayerSkills withTotalXp(final SkillType skill, final int totalXp) {
		Map<String, Integer> map = new HashMap<>(this.xp);
		map.put(skill.id(), totalXp);
		return new PlayerSkills(Map.copyOf(map));
	}
}
