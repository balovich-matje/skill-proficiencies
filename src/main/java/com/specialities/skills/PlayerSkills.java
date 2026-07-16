package com.specialities.skills;

import java.util.HashMap;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.specialities.api.SkillType;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * Immutable per-player skill progress, stored as total accumulated XP per skill id.
 * Levels are derived from total XP via {@link Tuning}.
 */
public record PlayerSkills(Map<String, Integer> xp) {
	public static final PlayerSkills EMPTY = new PlayerSkills(Map.of());

	public static final Codec<PlayerSkills> CODEC = Codec.unboundedMap(Codec.STRING, Codec.INT)
			.xmap(PlayerSkills::new, PlayerSkills::xp);

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
