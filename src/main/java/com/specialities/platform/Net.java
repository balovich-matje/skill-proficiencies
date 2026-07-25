package com.specialities.platform;

//? if >=1.20.5 {
//?} else {
/*import com.specialities.skills.PlayerSkills;
*///?}

// Only the loaders whose payload registration takes the client handler as an argument need
// these — see `clientReceivers` at the bottom of this file.
//? if fabric {
//?} else {
/*import java.util.function.Consumer;

import com.specialities.SkillUpdatePayload;
import com.specialities.StealthStatePayload;
*///?}
// The third wire id exists only below 1.20.5 (design R-03), so only the loader that is below
// that line has a third sink to install.
//? if forge {
/*import com.specialities.SkillsFullPayload;
*///?}

import net.minecraft.server.level.ServerPlayer;

/**
 * Seam 2 of three (design {@code docs/MULTIVERSION.md} §2) — clientbound
 * payloads: their registration, and the two sends the server does.
 *
 * <p>Why this is a seam and not a {@code //?} block: on 1.20.1 the whole payload
 * stack is missing. {@code CustomPacketPayload}, {@code StreamCodec},
 * {@code ByteBufCodecs}, {@code RegistryFriendlyByteBuf} and
 * {@code PayloadTypeRegistry} are all absent, and the replacement
 * ({@code FabricPacket} + {@code PacketType.create}) needs different payload
 * *types*, not just a different call. NeoForge and Forge fold registration and
 * the receiver into one call each.
 *
 * <p><b>The wire ids and the field order are frozen</b> —
 * {@code specialities:skill_update} carries (skillId, fromTotalXp, totalXp,
 * fromLevel, level) and {@code specialities:stealth_state} carries (state) — so a
 * client and a server built from different nodes of the same Minecraft version
 * stay compatible. That is why the send methods take loose values rather than a
 * payload object: below 1.20.5 the payload class itself has a different
 * supertype, so only the implementation may name it.
 *
 * <p><b>On Fabric, client-side receiver registration is deliberately NOT here</b>
 * — a seam in {@code src/main} cannot reach client-only API (measured; conventions
 * §5g), so it lives in {@code client/SpecialitiesClient}. That still holds. What
 * Phase B adds is not a receiver but a SINK INSTALLER: NeoForge's
 * {@code PayloadRegistrar.playToClient(TYPE, CODEC, handler)} and Forge's
 * {@code SimpleChannel.registerMessage(index, class, encoder, decoder, handler)}
 * take the handler as an ARGUMENT to the one registration call, which has to run
 * in common init because a dedicated server must register the type too. So the
 * client hands its two (or three) consumers down, and registration still happens
 * exactly where it does on every other node. Nothing in {@code src/main} names a
 * client type. See {@link #clientReceivers} at the bottom.
 */
public interface Net {
	// See the note on Platform.INSTANCE for the form, and for the source-set exclusion the three
	// mutually exclusive implementations need.
	//? if fabric {
	Net INSTANCE = new FabricNet();
	//?} elif neoforge {
	/*Net INSTANCE = new NeoForgeNet();
	*///?} elif forge {
	/*Net INSTANCE = new ForgeNet();
	*///?}

	/** Registers the clientbound payload types. Called once from common init. */
	void registerClientbound();

	void sendSkillUpdate(ServerPlayer player, String skillId,
			int fromTotalXp, int totalXp, int fromLevel, int level);

	void sendStealthState(ServerPlayer player, int state);

	// The R-03 method design §2.4 deviation 5 deferred to this stage, landing exactly as
	// that note said it would: additively, together with the payload it needs, and gated
	// so the nodes that sync attachments themselves neither declare nor register a third
	// wire id. `SkillStore.resyncSkills` is its only caller.
	//
	// This one takes the whole PlayerSkills rather than loose values, unlike the two
	// sends above. The reason the others take loose values is that the payload *record*
	// has a different supertype below 1.20.5 and only the implementation may name it —
	// PlayerSkills is a plain record with no Minecraft supertype at all, so it crosses
	// the seam unchanged.
	//? if >=1.20.5 {
	//?} else {
	/*void sendSkillsFull(ServerPlayer player, PlayerSkills skills);
	*///?}

	// THE SINK INSTALLER — the one member on this seam that does not exist on Fabric.
	//
	// Installs the client-side handlers that a one-call payload registration needs. Called from
	// client init, BEFORE the registration event fires and therefore before any packet can
	// arrive; an implementation stores the consumers and reads them from inside the handler it
	// hands the platform. The registration itself stays in `registerClientbound()`, i.e. in
	// common init, which is what keeps a dedicated server able to send.
	//
	// Two gated declarations rather than one with a nested arity fork: the alternative needs a
	// directive inside an already-disabled branch and the `*` -> `^` marker escalation
	// (conventions §4), which is the case that fails silently. The arity difference is real —
	// below 1.20.5 there is a third wire id (design R-03) and above it there is not.
	//
	// Consumers are called on the client thread; the implementation is responsible for
	// scheduling (`enqueueWork` on both loaders), exactly as fabric-api's typed receiver does
	// with `Minecraft.execute`.
	//? if fabric {
	//?} elif neoforge {
	/*void clientReceivers(Consumer<SkillUpdatePayload> onSkillUpdate,
			Consumer<StealthStatePayload> onStealthState);
	*///?} elif forge {
	/*void clientReceivers(Consumer<SkillUpdatePayload> onSkillUpdate,
			Consumer<StealthStatePayload> onStealthState,
			Consumer<SkillsFullPayload> onSkillsFull);
	*///?}
}
