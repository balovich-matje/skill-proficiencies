package com.specialities.platform;

import java.util.function.Supplier;

import com.specialities.Specialities;
import com.specialities.skills.PlayerSkills;

import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;

// jspecify is one of the game's own libraries only from 1.21.11 up (conventions §5e-bis) and
// this node is 1.21.1, so the same import the shared tree forks to on this node is the one
// used here: org.jetbrains:annotations. MEASURED on this node rather than assumed —
// `:1.21.1-neoforge:dependencies --configuration compileClasspath` resolves
// `org.jetbrains:annotations:24.1.0` transitively through `net.neoforged:neoform:1.21.1-...`,
// and zero jspecify artifacts. So no node-script dependency was added: this is what the
// platform already ships, which is the condition §5e-bis puts on not adding one.
import org.jetbrains.annotations.Nullable;

/**
 * NeoForge implementation of {@link SkillStore} for the {@code 1.21.1-neoforge} node — the
 * counterpart of {@code FabricSkillStore}, holding the same five attachment ids in the same
 * order. The ids are the on-disk world format and are frozen forever.
 *
 * <p>ARTIFACT PROVENANCE — every signature below was read out of
 * {@code neoforge-21.1.243-sources.jar} and the patch set in
 * {@code neoforge-21.1.243-userdev.jar}, not from docs:
 *
 * <ul>
 * <li>{@code attachment/AttachmentType.java:95} {@code static <T> Builder<T> builder(Supplier<T>)};
 *     {@code :184} {@code Builder.serialize(Codec<T>)}; {@code :224} {@code Builder.copyOnDeath()};
 *     {@code :273} {@code Builder.sync(BiPredicate<IAttachmentHolder,ServerPlayer>,
 *     StreamCodec<? super RegistryFriendlyByteBuf,T>)}; {@code :294} {@code build()}.
 *     Design R-09's {@code >=21.1.200} sync floor is real and 21.1.243 clears it — the
 *     builder declares three {@code sync} overloads. The node script asserts the floor.</li>
 * <li>{@code registries/NeoForgeRegistries.java:58} {@code Keys.ATTACHMENT_TYPES}, typed
 *     {@code ResourceKey<Registry<AttachmentType<?>>>}.</li>
 * <li>{@code registries/DeferredRegister.java:113} {@code create(ResourceKey<? extends
 *     Registry<T>>, String namespace)}; {@code :214} {@code <I extends T> DeferredHolder<T,I>
 *     register(String, Supplier<? extends I>)}; {@code :315} {@code register(IEventBus)}.</li>
 * <li><b>No cast to {@code IAttachmentHolder} anywhere below.</b> The userdev patches make
 *     {@code Entity} and {@code BlockEntity} EXTEND the holder outright —
 *     {@code patches/net/minecraft/world/entity/Entity.java.patch}:
 *     {@code public abstract class Entity extends net.neoforged.neoforge.attachment.AttachmentHolder
 *     implements …}, and the same line for {@code BlockEntity}. That is the one structural
 *     difference from {@code FabricSkillStore}, where fabric-api injects
 *     {@code AttachmentTarget} by mixin and every call site has to cast.</li>
 * </ul>
 *
 * <p><b>TWO SEMANTIC TRAPS, both of which would be silent balance/persistence bugs.</b>
 *
 * <p>1. {@code getData} is NOT {@code getAttached}. {@code AttachmentHolder.getData}
 * (AttachmentHolder.java:73-84, read from the artifact): when the attachment is absent it
 * calls {@code type.defaultValueSupplier}, <b>puts the result in the holder</b> and calls
 * {@code syncData(type)}. Fabric's {@code getAttachedOrElse(SKILLS, EMPTY)} stores nothing.
 * So every read of a player who has never gained XP would materialise — and, for SKILLS,
 * serialise and broadcast — an empty attachment; and for the four transient values the
 * interface contract is "{@code null} means absent", which {@code getData} can never
 * report. <b>Every getter here uses {@code getExistingDataOrNull}</b> (AttachmentHolder.java
 * :86-92: a plain map lookup, null when the map is null), which is the exact analogue of
 * {@code AttachmentTarget.getAttached}.
 *
 * <p>2. {@code setData} syncs by itself. {@code AttachmentHolder.setData} (:96-103) calls
 * {@code syncData(type)} after the put, and {@code AttachmentSync.syncEntityUpdate} adds the
 * {@code ServerPlayer} to its own watcher list ("Players do not track themselves", :133-139),
 * so {@code setSkills} pushes to the owning client with no extra call — the same behaviour
 * fabric-api's {@code syncWith} gives the 26.x nodes.
 *
 * <p><b>PREP Q2 IS ANSWERED YES, FROM THE ARTIFACT, so {@code resyncSkills} stays a no-op
 * and this node needs no third wire id.</b> {@code AttachmentSync
 * .syncInitialPlayerAttachments(ServerPlayer)} has no caller inside
 * {@code neoforge-21.1.243-universal.jar} because its call site is in PATCHED VANILLA, which
 * that jar does not contain — but the userdev patch set does:
 * {@code patches/net/minecraft/server/players/PlayerList.java.patch} inserts
 * {@code AttachmentSync.syncInitialPlayerAttachments(p_11263_)} into {@code placeNewPlayer},
 * immediately after {@code initInventoryMenu()} and immediately BEFORE
 * {@code EventHooks.firePlayerLoggedIn(p_11263_)}; the same call is inserted into
 * {@code respawn} before {@code firePlayerRespawnEvent}. So the login push fires, and it
 * fires before the join hook that would have had to compensate for it. The
 * {@code >=1.20.5} gate on {@code Net.sendSkillsFull} correctly hides that method from this
 * node, and no protocol change is needed. Confirm on the Tier-2 boot; do not
 * re-derive it from the universal jar, which cannot show it.
 *
 * <p><b>ONE ACCEPTED DEVIATION from the Fabric nodes, deliberately not "fixed".</b> Fabric
 * registers the skills attachment with {@code AttachmentSyncPredicate.targetOnly()}, so a
 * player's skill map reaches only that player's own client. NeoForge honours the
 * {@code sendToPlayer} predicate on the INCREMENTAL path ({@code AttachmentSync.syncUpdate},
 * :85-112, which tests it twice) but NOT on initial entity sync:
 * {@code syncInitialAttachments} (:152-186) writes every attachment that has a
 * {@code syncHandler} and never consults the predicate, and
 * {@code patches/net/minecraft/server/level/ServerEntity.java.patch:48} calls
 * {@code syncInitialEntityAttachments(this.entity, player, …)} from {@code sendPairingData}
 * for every tracked entity, players included. Consequence: when client A starts tracking
 * player B, A receives B's skill map once. It is not a crash and not a wrong render — the
 * payload carries an {@code EntityTarget(entity.getId())} so it attaches to the right entity
 * object, and this mod's client only ever reads {@code minecraft.player}'s skills. The only
 * alternative is dropping {@code sync()} and pushing the whole map by hand, which needs a
 * THIRD wire id on a {@code >=1.20.5} node — a protocol change, so it is the user's call and
 * not a silent one.
 */
final class NeoForgeSkillStore implements SkillStore {
	/**
	 * Namespace is {@code mod.id}, so the registered ids are byte-identical to the Fabric
	 * nodes' attachment ids — {@code specialities:skills} plus the four transient ones.
	 */
	private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
			DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Specialities.MOD_ID);

	/**
	 * Server-authoritative skill data; persisted with the player and synced to the owning
	 * client. Same {@code PlayerSkills.CODEC} and {@code PlayerSkills.STREAM_CODEC} as every
	 * other node, so the on-disk and on-wire shapes cannot drift.
	 *
	 * <p>Builder order matters: {@code copyOnDeath()} throws
	 * {@code IllegalStateException("copyOnDeath requires a serializer")} unless
	 * {@code serialize} ran first (AttachmentType.java:225).
	 *
	 * <p>The predicate is {@code holder == to} — the literal translation of Fabric's
	 * {@code AttachmentSyncPredicate.targetOnly()}. See the class javadoc for where NeoForge
	 * honours it and where it does not.
	 */
	private static final Supplier<AttachmentType<PlayerSkills>> SKILLS = ATTACHMENTS.register("skills",
			() -> AttachmentType.builder(() -> PlayerSkills.EMPTY)
					.serialize(PlayerSkills.CODEC)
					.copyOnDeath()
					.sync((holder, to) -> holder == to, PlayerSkills.STREAM_CODEC)
					.build());

	/** Remaining ricochets on an arrow spawned by the archery passive. Transient. */
	private static final Supplier<AttachmentType<Integer>> RICOCHET_BOUNCES =
			ATTACHMENTS.register("ricochet_bounces", () -> AttachmentType.builder(() -> 0).build());

	/** Entity id a ricochet arrow flies away from and must not hit again. Transient. */
	private static final Supplier<AttachmentType<Integer>> RICOCHET_IGNORE =
			ATTACHMENTS.register("ricochet_ignore", () -> AttachmentType.builder(() -> 0).build());

	/** Set on a mob once it has been stealth-critted — each enemy only falls for it once. Transient. */
	private static final Supplier<AttachmentType<Boolean>> STEALTH_CRIT_DONE =
			ATTACHMENTS.register("stealth_crit_done", () -> AttachmentType.builder(() -> Boolean.FALSE).build());

	/** UUID of the player who last opened a brewing stand, for alchemy attribution. Transient. */
	private static final Supplier<AttachmentType<String>> BREWING_OWNER =
			ATTACHMENTS.register("brewing_owner", () -> AttachmentType.builder(() -> "").build());

	// The four transient types deliberately get NO `serialize` and NO `sync` — that is what
	// makes them transient, and it is the exact analogue of `AttachmentRegistry.create(id)`
	// with no builder on Fabric. Their default-value suppliers are never reached by this
	// class (every read goes through getExistingDataOrNull); they exist only because
	// AttachmentType.builder demands one, and they stay cheap and side-effect-free in case
	// anything in the tree ever calls getData.

	@Override
	public void initialize() {
		// Two things happen here, in this order, and both have to happen at exactly the point
		// ModAttachments.initialize() used to occupy in common init:
		//   1. touching this class runs the five register(...) calls above (its static init),
		//      which is the Fabric impl's only job here too;
		//   2. the DeferredRegister is bound to the MOD event bus, which is the only bus
		//      RegisterEvent is posted on. `SpecialitiesNeoForge` stashes the bus in its
		//      constructor, before it calls the shared init, so it is available.
		ATTACHMENTS.register(SpecialitiesNeoForge.modEventBus());
	}

	@Override
	public PlayerSkills getSkills(final Player player) {
		PlayerSkills stored = player.getExistingDataOrNull(SKILLS.get());
		return stored == null ? PlayerSkills.EMPTY : stored;
	}

	@Override
	public void setSkills(final Player player, final PlayerSkills skills) {
		player.setData(SKILLS.get(), skills);
	}

	@Override
	public void resyncSkills(final ServerPlayer player) {
		// No-op, exactly like the Fabric >=1.20.5 branch. The sync handler above pushes on
		// every change (setData -> syncData), and the patched PlayerList pushes the whole set
		// on login and on respawn. Prep Q2 is answered in this class's javadoc, with the
		// patch line; there is nothing left for this method to do on this node.
	}

	@Override
	public @Nullable Integer getRicochetBounces(final Entity arrow) {
		return arrow.getExistingDataOrNull(RICOCHET_BOUNCES.get());
	}

	@Override
	public void setRicochetBounces(final Entity arrow, final int bounces) {
		arrow.setData(RICOCHET_BOUNCES.get(), bounces);
	}

	@Override
	public @Nullable Integer getRicochetIgnore(final Entity arrow) {
		return arrow.getExistingDataOrNull(RICOCHET_IGNORE.get());
	}

	@Override
	public void setRicochetIgnore(final Entity arrow, final int entityId) {
		arrow.setData(RICOCHET_IGNORE.get(), entityId);
	}

	@Override
	public boolean isStealthCritDone(final Mob mob) {
		return Boolean.TRUE.equals(mob.getExistingDataOrNull(STEALTH_CRIT_DONE.get()));
	}

	@Override
	public void markStealthCritDone(final Mob mob) {
		mob.setData(STEALTH_CRIT_DONE.get(), Boolean.TRUE);
	}

	@Override
	public @Nullable String getBrewingOwner(final BlockEntity stand) {
		return stand.getExistingDataOrNull(BREWING_OWNER.get());
	}

	@Override
	public void setBrewingOwner(final BlockEntity stand, final String uuid) {
		// No setChanged() after this one on purpose. AttachmentType's javadoc asks for it on
		// BlockEntity attachments so the change is persisted, but BREWING_OWNER is transient
		// and never serialised — matching the Fabric nodes, where the same attachment is
		// registered with no `persistent(...)`.
		stand.setData(BREWING_OWNER.get(), uuid);
	}

	// NOTE: getFiringWeapon/setFiringWeapon are NOT implemented and must not be. That pair is
	// gated `//? if >=1.21 { } else { … }` in SkillStore (conventions §5k — the boundary of
	// `AbstractArrow.getWeaponItem()`, the API it substitutes for), so it is absent from this
	// node's copy of the interface. 1.21.1 has getWeaponItem(), so nothing needs stamping.
}
