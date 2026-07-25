package com.specialities.platform;

import java.util.function.Consumer;

import com.specialities.SkillUpdatePayload;
import com.specialities.StealthStatePayload;

import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import net.minecraft.server.level.ServerPlayer;

/**
 * NeoForge implementation of {@link Net} for the {@code 1.21.1-neoforge} node.
 *
 * <p>ARTIFACT PROVENANCE — all read out of {@code neoforge-21.1.243-sources.jar}:
 *
 * <ul>
 * <li>{@code network/event/RegisterPayloadHandlersEvent.java} —
 *     {@code extends Event implements IModBusEvent} (so: MOD bus, not the game bus), with
 *     exactly one method, {@code PayloadRegistrar registrar(String version)}.</li>
 * <li>{@code network/registration/PayloadRegistrar.java:44} —
 *     {@code <T extends CustomPacketPayload> PayloadRegistrar playToClient(
 *     CustomPacketPayload.Type<T>, StreamCodec<? super RegistryFriendlyByteBuf,T>,
 *     IPayloadHandler<T>)}.</li>
 * <li>{@code PayloadRegistrar.java:29} — {@code private HandlerThread thread =
 *     HandlerThread.MAIN}, and {@code HandlerThread.MAIN} is documented as "the main thread
 *     of the receiving side… on the logical client, this is the Render Thread". So the
 *     registrar wraps our handler in a {@code MainThreadPayloadHandler} for us and
 *     {@code IPayloadContext.enqueueWork} is NOT needed here — unlike Forge's
 *     {@code SimpleChannel}, which is why {@code ForgeNet} differs on this point.</li>
 * <li>{@code network/PacketDistributor.java:50} —
 *     {@code static void sendToPlayer(ServerPlayer, CustomPacketPayload, CustomPacketPayload...)}.</li>
 * <li>{@code network/handling/IPayloadContext.java} — exposes {@code player()},
 *     {@code enqueueWork(...)}, {@code flow()}; there is <b>no {@code client()}</b>, unlike
 *     fabric-api's context, which is why the sink consumers below take the payload alone and
 *     the client half supplies {@code Minecraft.getInstance()} itself.</li>
 * </ul>
 *
 * <p><b>THE STRUCTURAL PROBLEM THIS FILE SOLVES.</b> Design §2.4 deviation 6 kept
 * {@code registerClientReceivers()} off {@code Net} because on Fabric registration and
 * reception are two separate calls in two separate source sets. Here they are ONE call:
 * {@code playToClient} takes the handler as its third argument, and it has to run in common
 * code because a dedicated server must register the payload types too or
 * {@code PacketDistributor.sendToPlayer} fails. But the handler body needs
 * {@code client/SkillHudState} and {@code client/StealthVignette}, and {@code src/main} can
 * never see {@code src/client} on ANY node — that is a source-set direction, not a classpath
 * question, so the merged NeoForge dev jar does not help. Hence {@link Net#clientReceivers}:
 * the client installs two {@code Consumer}s, the handlers registered here forward to
 * whatever is installed, and this class's constant pool names no client type at all.
 *
 * <p><b>The ordering that makes it safe is a FACT about mod loading, not a hope.</b>
 * {@code registerClientbound()} only adds a mod-bus listener; the {@code playToClient} calls
 * happen when {@code RegisterPayloadHandlersEvent} is posted, which is after ALL mod
 * construction. {@code Mod}'s own javadoc (loader-4.0.43,
 * {@code net/neoforged/fml/common/Mod.java}) says "a mod loaded with the {@code javafml}
 * language loader may have multiple entrypoints. Entrypoints for all {@code dist}s are
 * always run before entrypoints for a single {@code dist}" — so the common
 * {@code SpecialitiesNeoForge} constructor runs first, then the {@code Dist.CLIENT}
 * entrypoint installs the sinks, and both are done before the event fires. Nothing in this
 * class depends on WHICH of the two ran first, only that both precede the event.
 *
 * <p>Do NOT replace the sinks with direct lambdas or method references into
 * {@code com.specialities.client}: besides not compiling, it would put a client type in this
 * class's constant pool and break a dedicated server.
 */
final class NeoForgeNet implements Net {
	/**
	 * Network protocol version handed to {@code registrar(...)}. It gates the handshake, so
	 * it moves only when a payload's field list moves — and the wire ids and field order are
	 * frozen by the {@link Net} contract, so it stays "1".
	 */
	private static final String PROTOCOL = "1";

	/**
	 * Installed by the client entrypoint before the registration event fires; left as no-op
	 * sinks on a dedicated server, which registers the payload TYPES (so it can send) and
	 * never receives them. {@code volatile} because installation and delivery are on
	 * different threads (mod construction vs the render thread).
	 */
	private static volatile Consumer<SkillUpdatePayload> skillUpdateSink = payload -> {
	};

	private static volatile Consumer<StealthStatePayload> stealthStateSink = payload -> {
	};

	@Override
	public void registerClientbound() {
		SpecialitiesNeoForge.modEventBus().addListener(RegisterPayloadHandlersEvent.class,
				NeoForgeNet::onRegisterPayloads);
	}

	@Override
	public void clientReceivers(final Consumer<SkillUpdatePayload> onSkillUpdate,
			final Consumer<StealthStatePayload> onStealthState) {
		skillUpdateSink = onSkillUpdate;
		stealthStateSink = onStealthState;
	}

	private static void onRegisterPayloads(final RegisterPayloadHandlersEvent event) {
		PayloadRegistrar registrar = event.registrar(PROTOCOL);

		// Both payload records are shared, UNFORKED code on this node: 1.21.1 has
		// CustomPacketPayload, StreamCodec, ByteBufCodecs and RegistryFriendlyByteBuf, so TYPE
		// and CODEC here are literally the fields the five Fabric nodes register. Wire ids
		// (specialities:skill_update, specialities:stealth_state) and field order are frozen by
		// the Net contract so a client and a server built from different nodes of the same
		// Minecraft version stay compatible.
		registrar.playToClient(SkillUpdatePayload.TYPE, SkillUpdatePayload.CODEC,
				(payload, context) -> skillUpdateSink.accept(payload));
		registrar.playToClient(StealthStatePayload.TYPE, StealthStatePayload.CODEC,
				(payload, context) -> stealthStateSink.accept(payload));
	}

	@Override
	public void sendSkillUpdate(final ServerPlayer player, final String skillId,
			final int fromTotalXp, final int totalXp, final int fromLevel, final int level) {
		PacketDistributor.sendToPlayer(player,
				new SkillUpdatePayload(skillId, fromTotalXp, totalXp, fromLevel, level));
	}

	@Override
	public void sendStealthState(final ServerPlayer player, final int state) {
		PacketDistributor.sendToPlayer(player, new StealthStatePayload(state));
	}

	// NOTE: sendSkillsFull is NOT implemented and must not be. It is gated
	// `//? if >=1.20.5 { } else { … }` in Net, so it is absent from this node's copy of the
	// interface — NeoForge attachments sync themselves, including the login push
	// (NeoForgeSkillStore's javadoc has the patch line that proves it).
}
