package com.specialities.platform;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import com.specialities.SkillUpdatePayload;
import com.specialities.SkillsFullPayload;
import com.specialities.Specialities;
import com.specialities.StealthStatePayload;
import com.specialities.skills.PlayerSkills;

import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * LexForge implementation of {@link Net} for the {@code 1.20.1-forge} node.
 *
 * <p>ARTIFACT PROVENANCE — {@code forge-1.20.1-47.4.22-sources.jar}:
 *
 * <ul>
 * <li>{@code network/NetworkRegistry.java} —
 *     {@code static SimpleChannel newSimpleChannel(ResourceLocation name,
 *     Supplier<String> networkProtocolVersion, Predicate<String> clientAcceptedVersions,
 *     Predicate<String> serverAcceptedVersions)}. Still present and non-deprecated at
 *     47.4.22; {@code ChannelBuilder} is the newer alternative and is not needed here.</li>
 * <li>{@code network/simple/SimpleChannel.java} —
 *     {@code <MSG> IndexedMessageCodec.MessageHandler<MSG> registerMessage(int index,
 *     Class<MSG> messageType, BiConsumer<MSG,FriendlyByteBuf> encoder,
 *     Function<FriendlyByteBuf,MSG> decoder,
 *     BiConsumer<MSG,Supplier<NetworkEvent.Context>> messageConsumer)} and
 *     {@code <MSG> void send(PacketDistributor.PacketTarget, MSG)}.</li>
 * <li>{@code network/PacketDistributor.java} —
 *     {@code public static final PacketDistributor<ServerPlayer> PLAYER} and
 *     {@code PacketTarget with(Supplier<T>)}.</li>
 * <li>{@code network/NetworkEvent.java} — {@code void setPacketHandled(boolean)},
 *     {@code CompletableFuture<Void> enqueueWork(Runnable)}.</li>
 * </ul>
 *
 * <p><b>Three ways this differs from every other node, all forced:</b>
 *
 * <p>1. <b>The payload records carry no supertype here.</b> The three records fork on
 * {@code >=1.20.5 / elif fabric / else}; this node lands on the {@code else} arm, which is a
 * plain record with no {@code TYPE}, no {@code write} and no {@code getType}, because
 * {@code registerMessage} takes the encoder and decoder as ARGUMENTS. The six codec methods at
 * the bottom of this file are therefore the only encoding of those three payloads on this
 * node, and they reproduce the frozen field order exactly.
 *
 * <p>2. <b>Handlers run on the network thread.</b> Fabric's typed client receiver and
 * NeoForge's {@code PayloadRegistrar} both hand you the main thread; {@code SimpleChannel} does
 * not. So every handler wraps its body in {@code context.get().enqueueWork(...)} and then
 * calls {@code setPacketHandled(true)}. Without {@code enqueueWork} the HUD state is mutated
 * off-thread; without {@code setPacketHandled} Forge logs the packet as unhandled.
 *
 * <p>3. <b>The client receivers arrive through a sink.</b> {@code registerMessage} takes the
 * handler at registration time and registration must happen in common init so a dedicated
 * server can also send, but the handler bodies need {@code client/SkillHudState},
 * {@code client/StealthVignette} and the client's own {@code SkillStore} write — all in
 * {@code src/client}, which {@code src/main} can never see. So the client hands its three
 * consumers DOWN through {@link Net#clientReceivers}, from its entrypoint, before the sends
 * can start. See {@link Net}'s javadoc.
 *
 * <p><b>Message indices are frozen</b> the day this node ships: 0 = skill update, 1 = stealth
 * state, 2 = skills full. {@code IndexedMessageCodec} writes the index as the discriminator
 * byte, so reordering them silently mis-decodes. The BODY bytes match every other node's
 * payload exactly, but the FRAME does not — a Forge channel prefixes its own channel name and
 * index — and that is fine: {@link Net}'s contract freezes the ids and the field order, and a
 * Forge client cannot join a Fabric server in any case.
 */
final class ForgeNet implements Net {
	/** Channel id. In the mod's own namespace so it cannot collide. */
	private static final ResourceLocation CHANNEL_ID = new ResourceLocation(Specialities.MOD_ID, "main");

	private static final String PROTOCOL = "1";

	private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
			CHANNEL_ID, () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

	/** FROZEN discriminators — see the class javadoc. */
	private static final int INDEX_SKILL_UPDATE = 0;
	private static final int INDEX_STEALTH_STATE = 1;
	private static final int INDEX_SKILLS_FULL = 2;

	// Written once from the client entrypoint, read from the network thread, so volatile. The
	// no-op defaults are what a dedicated server keeps: it registers the same three message
	// types (it has to, or `send` cannot encode them) and never receives any of them.
	private static volatile Consumer<SkillUpdatePayload> skillUpdateSink = payload -> { };
	private static volatile Consumer<StealthStatePayload> stealthStateSink = payload -> { };
	private static volatile Consumer<SkillsFullPayload> skillsFullSink = payload -> { };

	@Override
	public void registerClientbound() {
		CHANNEL.registerMessage(INDEX_SKILL_UPDATE, SkillUpdatePayload.class,
				ForgeNet::writeSkillUpdate, ForgeNet::readSkillUpdate,
				(payload, context) -> {
					context.get().enqueueWork(() -> skillUpdateSink.accept(payload));
					context.get().setPacketHandled(true);
				});

		CHANNEL.registerMessage(INDEX_STEALTH_STATE, StealthStatePayload.class,
				ForgeNet::writeStealthState, ForgeNet::readStealthState,
				(payload, context) -> {
					context.get().enqueueWork(() -> stealthStateSink.accept(payload));
					context.get().setPacketHandled(true);
				});

		CHANNEL.registerMessage(INDEX_SKILLS_FULL, SkillsFullPayload.class,
				ForgeNet::writeSkillsFull, ForgeNet::readSkillsFull,
				(payload, context) -> {
					context.get().enqueueWork(() -> skillsFullSink.accept(payload));
					context.get().setPacketHandled(true);
				});
	}

	@Override
	public void clientReceivers(final Consumer<SkillUpdatePayload> onSkillUpdate,
			final Consumer<StealthStatePayload> onStealthState,
			final Consumer<SkillsFullPayload> onSkillsFull) {
		skillUpdateSink = onSkillUpdate;
		stealthStateSink = onStealthState;
		skillsFullSink = onSkillsFull;
	}

	@Override
	public void sendSkillUpdate(final ServerPlayer player, final String skillId,
			final int fromTotalXp, final int totalXp, final int fromLevel, final int level) {
		CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
				new SkillUpdatePayload(skillId, fromTotalXp, totalXp, fromLevel, level));
	}

	@Override
	public void sendStealthState(final ServerPlayer player, final int state) {
		CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new StealthStatePayload(state));
	}

	@Override
	public void sendSkillsFull(final ServerPlayer player, final PlayerSkills skills) {
		CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new SkillsFullPayload(skills));
	}

	// ---------------------------------------------------------------------------------
	// Codecs. The field order is FROZEN by the Net contract and the body bytes match every
	// other node's payload exactly:
	//   skill_update  = utf, varint, varint, varint, varint
	//   stealth_state = varint
	//   skills_full   = varint count, then count * (utf, varint)
	// The other copy of each is the payload record's own `<1.20.5` Fabric arm; these two are
	// the only two, and both are named in the record's javadoc.
	// ---------------------------------------------------------------------------------

	private static void writeSkillUpdate(final SkillUpdatePayload payload, final FriendlyByteBuf buf) {
		buf.writeUtf(payload.skillId());
		buf.writeVarInt(payload.fromTotalXp());
		buf.writeVarInt(payload.totalXp());
		buf.writeVarInt(payload.fromLevel());
		buf.writeVarInt(payload.level());
	}

	private static SkillUpdatePayload readSkillUpdate(final FriendlyByteBuf buf) {
		return new SkillUpdatePayload(buf.readUtf(), buf.readVarInt(), buf.readVarInt(),
				buf.readVarInt(), buf.readVarInt());
	}

	private static void writeStealthState(final StealthStatePayload payload, final FriendlyByteBuf buf) {
		buf.writeVarInt(payload.state());
	}

	private static StealthStatePayload readStealthState(final FriendlyByteBuf buf) {
		return new StealthStatePayload(buf.readVarInt());
	}

	private static void writeSkillsFull(final SkillsFullPayload payload, final FriendlyByteBuf buf) {
		buf.writeVarInt(payload.skills().xp().size());
		payload.skills().xp().forEach((id, total) -> {
			buf.writeUtf(id);
			buf.writeVarInt(total);
		});
	}

	private static SkillsFullPayload readSkillsFull(final FriendlyByteBuf buf) {
		int size = buf.readVarInt();
		Map<String, Integer> map = new HashMap<>(size);

		for (int i = 0; i < size; i++) {
			map.put(buf.readUtf(), buf.readVarInt());
		}

		return new SkillsFullPayload(new PlayerSkills(Map.copyOf(map)));
	}
}
