package com.specialities;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client notification that a skill gained XP (and possibly leveled up).
 * Carries the previous state so the client can animate the bar and show
 * "Increased x -> y" on level-up toasts.
 */
public record SkillUpdatePayload(String skillId, int fromTotalXp, int totalXp, int fromLevel, int level)
		implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<SkillUpdatePayload> TYPE =
			new CustomPacketPayload.Type<>(Specialities.id("skill_update"));

	public static final StreamCodec<RegistryFriendlyByteBuf, SkillUpdatePayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, SkillUpdatePayload::skillId,
			ByteBufCodecs.VAR_INT, SkillUpdatePayload::fromTotalXp,
			ByteBufCodecs.VAR_INT, SkillUpdatePayload::totalXp,
			ByteBufCodecs.VAR_INT, SkillUpdatePayload::fromLevel,
			ByteBufCodecs.VAR_INT, SkillUpdatePayload::level,
			SkillUpdatePayload::new);

	public boolean levelUp() {
		return this.level > this.fromLevel;
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
