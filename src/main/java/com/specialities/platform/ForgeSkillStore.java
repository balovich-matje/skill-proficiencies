package com.specialities.platform;

import com.specialities.Specialities;
import com.specialities.skills.PlayerSkills;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.IEventBus;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;

import org.jetbrains.annotations.Nullable;

/**
 * LexForge implementation of {@link SkillStore} for the {@code 1.20.1-forge} node.
 *
 * <p>ARTIFACT PROVENANCE — every signature read out of
 * {@code forge-1.20.1-47.4.22-sources.jar} (in {@code scratchpad/phase-b-prep/art/}), not
 * recalled:
 *
 * <ul>
 * <li>{@code common/capabilities/CapabilityManager.java} —
 *     {@code static <T> Capability<T> get(CapabilityToken<T>)}.</li>
 * <li>{@code common/capabilities/RegisterCapabilitiesEvent.java} —
 *     {@code final class … extends Event implements IModBusEvent} with
 *     {@code <T> void register(Class<T>)}. {@code IModBusEvent}, so it needs the MOD bus
 *     {@code SpecialitiesForge} stashes, not {@code MinecraftForge.EVENT_BUS}.</li>
 * <li>{@code common/capabilities/ICapabilitySerializable.java} —
 *     {@code interface ICapabilitySerializable<T extends Tag> extends ICapabilityProvider,
 *     INBTSerializable<T>}; {@code common/util/INBTSerializable.java} is
 *     {@code T serializeNBT()} / {@code void deserializeNBT(T)} with <b>no
 *     {@code HolderLookup.Provider} parameter</b> — that arrived later, do not copy a
 *     1.21-era signature here.</li>
 * <li>{@code common/capabilities/ICapabilityProvider.java} —
 *     {@code <T> LazyOptional<T> getCapability(Capability<T>, Direction)} plus the
 *     {@code getCapability(Capability<T>)} default; {@code Capability.java}
 *     {@code <R> LazyOptional<R> orEmpty(Capability<R>, LazyOptional<T>)}.</li>
 * <li>{@code event/AttachCapabilitiesEvent.java} — {@code class AttachCapabilitiesEvent<T>
 *     extends GenericEvent<T>} with {@code T getObject()} and
 *     {@code void addCapability(ResourceLocation, ICapabilityProvider)}. Being a
 *     {@code GenericEvent} it needs {@code IEventBus.addGenericListener(Class<F>,
 *     Consumer<T>)} ({@code eventbus-6.2.33-sources.jar}).</li>
 * <li>{@code Entity} and {@code BlockEntity} both {@code extends CapabilityProvider<…>} —
 *     read off {@code patches/net/minecraft/world/entity/Entity.java.patch} and
 *     {@code …/block/entity/BlockEntity.java.patch}, which also show
 *     {@code gatherCapabilities()} in the constructor and {@code invalidateCaps()} on
 *     removal. {@code reviveCaps()}/{@code invalidateCaps()} are public on
 *     {@code CapabilityProvider}.</li>
 * </ul>
 *
 * <p><b>Design §2's Forge row, checked item by item against those artifacts, and it is right
 * on all four counts:</b> capabilities with (1) <b>no codec</b> — bridged here through
 * {@code PlayerSkills.CODEC} + {@code NbtOps.INSTANCE}, so the persisted shape is the same
 * unbounded {@code Map<String,Integer>} every other node writes; (2) <b>no copyOnDeath</b> —
 * {@code PlayerEvent.Clone} plus the {@code reviveCaps()}/{@code invalidateCaps()} dance; (3)
 * <b>no sync</b> — {@link #resyncSkills} is mandatory and is the only path by which this
 * node's client ever learns the map its HUD bar and skills screen render (both call
 * {@code SkillManager.get(minecraft.player)}, i.e. this store, on the CLIENT); (4)
 * {@code getSkills} resolves a {@code LazyOptional}.
 *
 * <p><b>WORLD COMPATIBILITY WITH THE FABRIC 1.20.1 JAR — what is achieved, and why the rest
 * is impossible.</b> The VALUE bytes match exactly, and {@link SkillsProvider} carries the
 * measurement: both loaders write {@code specialities:skills =
 * TAG_Compound{ <skillId>: TAG_Int }} from the same {@code PlayerSkills.CODEC} through the
 * same {@code NbtOps}. The ENCLOSING CONTAINER does not and cannot match: fabric-api nests
 * attachments under {@code fabric:attachments} and Forge nests capability providers under
 * {@code ForgeCaps}. Both containers are loader-owned, not mod-owned — no code in this file
 * reaches either name — so a world carried from the Fabric jar to this one will not find its
 * skills unless something deliberately re-reads the other container, which neither loader
 * does. DOCUMENTED AS IMPOSSIBLE, per the seam's "match it or say why not"; what IS matched is
 * the part a future one-shot importer would actually need.
 *
 * <p><b>Why the capability value type is a mutable holder and not {@code PlayerSkills}.</b>
 * {@code LazyOptional} caches the instance its supplier returned, so the object identity
 * behind a capability cannot be swapped. {@code PlayerSkills} is an immutable record and
 * {@link #setSkills} replaces it wholesale. Hence {@link SkillsHolder}: one mutable field
 * whose value is the shared immutable record.
 *
 * <p><b>Why the four transient values share ONE capability.</b> Attaching a provider costs an
 * object per entity per capability and {@code AttachCapabilitiesEvent<Entity>} fires for every
 * entity in the world. So the five transient slots become two capabilities attached narrowly:
 * {@link EntityData} (ricochet bounces, ricochet ignore, stealth-crit flag, firing weapon) on
 * {@code Projectile} and {@code Mob} only, and {@link StandData} (brewing owner) on brewing
 * stands only. {@code Projectile} rather than {@code AbstractArrow} is deliberate and
 * measured: {@code BowItemMixin} and {@code CrossbowItemMixin} stamp the firing weapon by
 * wrapping {@code Level.addFreshEntity(Entity)} inside {@code releaseUsing} /
 * {@code shootProjectile}, and a firework crossbow spawns a {@code FireworkRocketEntity}
 * there — not an {@code AbstractArrow}. Nothing reads that stamp back
 * ({@code SkillCategories.weaponItem} takes an {@code AbstractArrow}), but a {@code set} that
 * silently evaporates is the failure class R-20 was about, so the capability covers every
 * entity either mixin can hand it.
 *
 * <p>The "absent vs zero" distinction {@link SkillStore#getRicochetBounces} depends on — an
 * original shot has no value, a spent bounce arrow has zero — is preserved by the holder's
 * fields being {@code @Nullable Integer}, NOT by the capability being absent.
 *
 * <p><b>This node sees the two {@code <1.21} interface members</b>
 * ({@code getFiringWeapon} / {@code setFiringWeapon}) because {@link SkillStore} gates them on
 * {@code >=1.21} and 1.20.1 is below the line. Design R-04 is why:
 * {@code AbstractArrow.getWeaponItem()} does not exist here.
 */
final class ForgeSkillStore implements SkillStore {
	/**
	 * The persistent player capability. The {@code new CapabilityToken<>(){}} anonymous
	 * subclass is mandatory, not stylistic: {@code CapabilityToken.getType()} is implemented
	 * by a Forge class transformer that reads the generic argument off the subclass
	 * signature, so a raw token or a shared instance loses the type and
	 * {@code CapabilityManager.get} returns the wrong capability.
	 */
	private static final Capability<SkillsHolder> SKILLS =
			CapabilityManager.get(new CapabilityToken<SkillsHolder>() { });

	private static final Capability<EntityData> ENTITY_DATA =
			CapabilityManager.get(new CapabilityToken<EntityData>() { });

	private static final Capability<StandData> STAND_DATA =
			CapabilityManager.get(new CapabilityToken<StandData>() { });

	/**
	 * Capability provider keys. These are not the Fabric attachment ids — Forge keys namespace
	 * a provider inside {@code CapabilityDispatcher}, which writes each one as an NBT tag name
	 * under {@code ForgeCaps}. So this ResourceLocation's string IS world format from the day
	 * this node first ships: freeze them then, exactly as the attachment ids are frozen.
	 */
	private static final ResourceLocation SKILLS_KEY = new ResourceLocation(Specialities.MOD_ID, "skills");
	private static final ResourceLocation ENTITY_DATA_KEY = new ResourceLocation(Specialities.MOD_ID, "entity_data");
	private static final ResourceLocation STAND_DATA_KEY = new ResourceLocation(Specialities.MOD_ID, "stand_data");

	/**
	 * The ONE piece of this store's wiring that cannot wait for {@link #initialize()}, and the
	 * reason it is a separate static method called straight from {@code SpecialitiesForge}'s
	 * constructor.
	 *
	 * <p>MEASURED from {@code ForgeStatesProvider} in
	 * {@code forge-1.20.1-47.4.22-universal.jar}, which is the whole mod-loading order in one
	 * class:
	 *
	 * <pre>
	 *   CONSTRUCT
	 *     CREATE_REGISTRIES     NewRegistryEvent
	 *     OBJECT_HOLDERS
	 *     INJECT_CAPABILITIES   RegisterCapabilitiesEvent      &lt;-- this listener must exist by here
	 *     UNFREEZE_DATA         GameData.unfreezeData()
	 *     LOAD_REGISTRIES       GameData.postRegisterEvents()  &lt;-- where onInitialize() runs
	 *   … CONFIG_LOAD, COMMON_SETUP, SIDED_SETUP …
	 *     FREEZE_DATA / NETWORK_LOCK
	 * </pre>
	 *
	 * <p>{@code SpecialitiesForge} defers the shared {@code onInitialize()} to the item
	 * {@code RegisterEvent} (its javadoc says why), and that lands in LOAD_REGISTRIES — one
	 * state AFTER {@code RegisterCapabilitiesEvent} has already been posted. A capability that
	 * is never passed to {@code RegisterCapabilitiesEvent.register} stays
	 * {@code isRegistered() == false}, and {@code Capability.orEmpty} then returns empty for
	 * every lookup: no exception, no log line, every skill silently reads
	 * {@code PlayerSkills.EMPTY} forever. So this half is registered at CONSTRUCT and the rest
	 * from {@link #initialize()}.
	 */
	static void registerCapabilities(final IEventBus modEventBus) {
		// Two buses, and putting a listener on the wrong one is a silent no-op.
		// RegisterCapabilitiesEvent implements IModBusEvent -> the MOD bus.
		modEventBus.addListener(ForgeSkillStore::onRegisterCapabilities);
	}

	@Override
	public void initialize() {
		// AttachCapabilitiesEvent is a GenericEvent, so it must be added with
		// addGenericListener and the class filter is what Forge dispatches on. Game bus, and
		// game-bus listeners may be added at any point before a world loads — which is why
		// only the mod-bus half above had to move earlier.
		MinecraftForge.EVENT_BUS.addGenericListener(Entity.class, ForgeSkillStore::onAttachEntity);
		MinecraftForge.EVENT_BUS.addGenericListener(BlockEntity.class, ForgeSkillStore::onAttachBlockEntity);

		// Replaces AttachmentType.copyOnDeath(). Registered here, with the store it belongs
		// to, rather than in SkillEvents: it is persistence plumbing, not a skill event.
		MinecraftForge.EVENT_BUS.addListener(ForgeSkillStore::onPlayerClone);

		// THE RE-SEND BELT. Same rule as the clone listener above — this is sync plumbing, not a
		// skill event, so it registers with the store and none of it reaches the shared tree.
		// (That is also what keeps the five Fabric jars instruction-identical: everything here
		// is inside a `platform/Forge*` file, which their source sets exclude by glob.)
		//
		// WHAT IT FIXES. `resyncSkills` is this node's ONLY sync, and until now its only caller
		// was the join hook — so the client's map was correct exactly once per login. Every
		// vanilla sender of `ClientboundRespawnPacket` rebuilds the client's `LocalPlayer`, it
		// gets a fresh empty SkillsHolder from AttachCapabilitiesEvent, and nothing re-sent the
		// map: the HUD bar and the skills screen (both of which call `SkillManager.get` on the
		// CLIENT store) read PlayerSkills.EMPTY until the next relog. On this version those
		// senders are exactly three — `ServerPlayer.changeDimension`, the cross-dimension
		// `ServerPlayer.teleportTo(ServerLevel, …)` and `PlayerList.respawn` — and the two
		// listeners below cover all three. Each helper's javadoc carries the R-20 proof that its
		// event is posted AFTER the packet and after `revive()`.
		//
		// Server-side state was never lost: `changeDimension` calls `revive()`, which is
		// `unsetRemoved(); reviveCaps();`, and we register no invalidation listener. This is a
		// mirror-only failure and a relog always cured it.
		ForgeEvents.playerChangedDimension(this::resyncSkills);
		ForgeEvents.afterRespawn(this::resyncSkills);
		// Belt on the belt: game mode provably sends no respawn packet, so nothing is lost here
		// on any node. Registered anyway so the guarantee does not rest on that staying true —
		// see ForgeEvents#playerChangeGameMode.
		ForgeEvents.playerChangeGameMode(this::resyncSkills);
	}

	private static void onRegisterCapabilities(final RegisterCapabilitiesEvent event) {
		event.register(SkillsHolder.class);
		event.register(EntityData.class);
		event.register(StandData.class);
	}

	private static void onAttachEntity(final AttachCapabilitiesEvent<Entity> event) {
		Entity entity = event.getObject();

		if (entity instanceof Player) {
			event.addCapability(SKILLS_KEY, new SkillsProvider());
		}

		// See the class javadoc for why this is `Projectile`, not `AbstractArrow`.
		if (entity instanceof Projectile || entity instanceof Mob) {
			event.addCapability(ENTITY_DATA_KEY, new EntityDataProvider());
		}
	}

	private static void onAttachBlockEntity(final AttachCapabilitiesEvent<BlockEntity> event) {
		// BrewingStandMenuMixin sets the owner from the menu constructor and Artisan reads it
		// in doBrew; both already hold a BrewingStandBlockEntity, and a modded stand subclass
		// passes this instanceof too.
		if (event.getObject() instanceof BrewingStandBlockEntity) {
			event.addCapability(STAND_DATA_KEY, new StandDataProvider());
		}
	}

	private static void onPlayerClone(final PlayerEvent.Clone event) {
		// reviveCaps()/invalidateCaps() are mandatory, and the artifact says why: the old
		// player has already been through Entity.remove, which calls invalidateCaps
		// (patches/net/minecraft/world/entity/Entity.java.patch), so getCapability on it
		// returns empty without the revive.
		event.getOriginal().reviveCaps();

		try {
			SkillsHolder from = event.getOriginal().getCapability(SKILLS).orElse(null);
			SkillsHolder to = event.getEntity().getCapability(SKILLS).orElse(null);

			if (from != null && to != null) {
				// Unconditional, NOT gated on isWasDeath(). ServerPlayer.restoreFrom fires
				// this for the End-return clone as well as the death clone
				// (patches/net/minecraft/server/level/ServerPlayer.java.patch:
				// `onPlayerClone(this, p_9016_, !p_9017_)`), and the Fabric attachment
				// carries over in both cases — copyOnDeath covers death and the non-death
				// clone copies by default.
				to.skills = from.skills;
			}
		} finally {
			event.getOriginal().invalidateCaps();
		}
	}

	@Override
	public PlayerSkills getSkills(final Player player) {
		SkillsHolder holder = player.getCapability(SKILLS).orElse(null);
		return holder == null ? PlayerSkills.EMPTY : holder.skills;
	}

	@Override
	public void setSkills(final Player player, final PlayerSkills skills) {
		player.getCapability(SKILLS).ifPresent(holder -> holder.skills = skills);
	}

	@Override
	public void resyncSkills(final ServerPlayer player) {
		// Design R-03, and on this node it is not an optimisation — it is the ONLY sync.
		// Capabilities have no sync mechanism, so without this the client's SkillXpHudBar and
		// SkillsScreen render PlayerSkills.EMPTY forever. Same one-line body as the Fabric
		// 1.20.1 branch, which is the point of the seam.
		Net.INSTANCE.sendSkillsFull(player, getSkills(player));
	}

	@Override
	public @Nullable Integer getRicochetBounces(final Entity arrow) {
		EntityData data = arrow.getCapability(ENTITY_DATA).orElse(null);
		return data == null ? null : data.ricochetBounces;
	}

	@Override
	public void setRicochetBounces(final Entity arrow, final int bounces) {
		arrow.getCapability(ENTITY_DATA).ifPresent(data -> data.ricochetBounces = bounces);
	}

	@Override
	public @Nullable Integer getRicochetIgnore(final Entity arrow) {
		EntityData data = arrow.getCapability(ENTITY_DATA).orElse(null);
		return data == null ? null : data.ricochetIgnore;
	}

	@Override
	public void setRicochetIgnore(final Entity arrow, final int entityId) {
		arrow.getCapability(ENTITY_DATA).ifPresent(data -> data.ricochetIgnore = entityId);
	}

	@Override
	public boolean isStealthCritDone(final Mob mob) {
		EntityData data = mob.getCapability(ENTITY_DATA).orElse(null);
		return data != null && data.stealthCritDone;
	}

	@Override
	public void markStealthCritDone(final Mob mob) {
		mob.getCapability(ENTITY_DATA).ifPresent(data -> data.stealthCritDone = true);
	}

	@Override
	public @Nullable String getBrewingOwner(final BlockEntity stand) {
		StandData data = stand.getCapability(STAND_DATA).orElse(null);
		return data == null ? null : data.brewingOwner;
	}

	@Override
	public void setBrewingOwner(final BlockEntity stand, final String uuid) {
		stand.getCapability(STAND_DATA).ifPresent(data -> data.brewingOwner = uuid);
	}

	@Override
	public @Nullable ItemStack getFiringWeapon(final Entity projectile) {
		EntityData data = projectile.getCapability(ENTITY_DATA).orElse(null);
		return data == null ? null : data.firingWeapon;
	}

	@Override
	public void setFiringWeapon(final Entity projectile, final ItemStack weapon) {
		// .copy() matches the Fabric 1.20.1 branch exactly: the stack the bow was fired from
		// can be mutated or consumed before the arrow lands.
		projectile.getCapability(ENTITY_DATA).ifPresent(data -> data.firingWeapon = weapon.copy());
	}

	// ---------------------------------------------------------------------------------
	// Capability value types. Mutable, because LazyOptional caches the resolved instance.
	// ---------------------------------------------------------------------------------

	/** The persistent one: holds the shared immutable record. */
	static final class SkillsHolder {
		PlayerSkills skills = PlayerSkills.EMPTY;
	}

	/**
	 * All four transient per-entity slots in one object. {@code Integer}, not {@code int}:
	 * {@link SkillStore}'s contract makes "absent" and "zero" different states and
	 * {@code AbstractArrowMixin} reads that difference.
	 */
	static final class EntityData {
		@Nullable Integer ricochetBounces;
		@Nullable Integer ricochetIgnore;
		boolean stealthCritDone;
		@Nullable ItemStack firingWeapon;
	}

	/** The brewing-stand slot. Transient: alchemy attribution does not survive a reload. */
	static final class StandData {
		@Nullable String brewingOwner;
	}

	// ---------------------------------------------------------------------------------
	// Providers.
	// ---------------------------------------------------------------------------------

	/**
	 * The only serialising provider. {@code PlayerSkills.CODEC} is bridged through
	 * {@code NbtOps.INSTANCE} and its output is returned <b>unwrapped</b> — no extra key of
	 * our own — so the value tag is byte-identical to what fabric-api writes.
	 *
	 * <p>MEASURED, both halves:
	 * <ul>
	 * <li>fabric-data-attachment-api-v1 1.0.2 ({@code javap -c AttachmentSerializingImpl})
	 *     puts each attachment's codec output under a compound named
	 *     {@code "fabric:attachments"}, keyed by {@code Identifier.toString()}.</li>
	 * <li>{@code CapabilityDispatcher.serializeNBT} puts each provider's tag under a compound
	 *     keyed by the provider's {@code ResourceLocation} string, and
	 *     {@code patches/…/Entity.java.patch} puts that under {@code "ForgeCaps"}.</li>
	 * </ul>
	 * So both write {@code …/<container>/specialities:skills = TAG_Compound{ <skillId>:
	 * TAG_Int }} and differ only in the container name, which is loader-owned. Returning the
	 * codec output unwrapped is the most a mod can do about it.
	 *
	 * <p>One residual difference, and it is harmless: Forge serialises every attached
	 * serialising provider, so a player who has never gained XP gets an empty
	 * {@code specialities:skills} compound written, where fabric-api writes nothing at all for
	 * an unset attachment.
	 */
	private static final class SkillsProvider implements ICapabilitySerializable<CompoundTag> {
		private final SkillsHolder holder = new SkillsHolder();
		private final LazyOptional<SkillsHolder> optional = LazyOptional.of(() -> this.holder);

		@Override
		public <T> LazyOptional<T> getCapability(final Capability<T> cap, final @Nullable Direction side) {
			return SKILLS.orEmpty(cap, this.optional);
		}

		@Override
		public CompoundTag serializeNBT() {
			// PlayerSkills.CODEC is an unbounded string->int map, so the encoded Tag is always
			// a CompoundTag. The instanceof is a guard, not a branch anyone expects to take.
			Tag written = PlayerSkills.CODEC.encodeStart(NbtOps.INSTANCE, this.holder.skills)
					.resultOrPartial(error -> Specialities.LOGGER.error("Failed to write skills: {}", error))
					.orElse(null);
			return written instanceof CompoundTag compound ? compound : new CompoundTag();
		}

		@Override
		public void deserializeNBT(final CompoundTag tag) {
			this.holder.skills = PlayerSkills.CODEC.parse(NbtOps.INSTANCE, tag)
					.resultOrPartial(error -> Specialities.LOGGER.error("Failed to read skills: {}", error))
					.orElse(PlayerSkills.EMPTY);
		}
	}

	/**
	 * Transient providers implement {@code ICapabilityProvider} only — no
	 * {@code INBTSerializable} — so Forge never writes them, which is exactly what
	 * {@code AttachmentRegistry.create(id)} with no {@code persistent(...)} gives on Fabric.
	 */
	private static final class EntityDataProvider implements ICapabilityProvider {
		private final EntityData data = new EntityData();
		private final LazyOptional<EntityData> optional = LazyOptional.of(() -> this.data);

		@Override
		public <T> LazyOptional<T> getCapability(final Capability<T> cap, final @Nullable Direction side) {
			return ENTITY_DATA.orEmpty(cap, this.optional);
		}
	}

	private static final class StandDataProvider implements ICapabilityProvider {
		private final StandData data = new StandData();
		private final LazyOptional<StandData> optional = LazyOptional.of(() -> this.data);

		@Override
		public <T> LazyOptional<T> getCapability(final Capability<T> cap, final @Nullable Direction side) {
			return STAND_DATA.orEmpty(cap, this.optional);
		}
	}
}
