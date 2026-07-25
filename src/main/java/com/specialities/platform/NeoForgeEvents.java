package com.specialities.platform;

import java.util.function.Consumer;

import com.mojang.brigadier.CommandDispatcher;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.Nullable;

/**
 * The NeoForge half of the event seam: one static registration helper per fabric-api event
 * the shared tree registers, each taking the very same lambda the Fabric arm hands to
 * fabric-api. Nothing in here contains a line of balance logic — that is
 * {@code skills/SkillEvents}, {@code ModItems} and {@code command/SkillCommands}, unforked
 * (conventions §5a applied to events instead of mixins).
 *
 * <p>This class exists because {@code com.specialities.platform} is the only package allowed
 * to name loader API (conventions §5g). It is {@code public} rather than package-private
 * because its callers are in {@code com.specialities} and {@code com.specialities.skills};
 * the Fabric and Forge node scripts exclude it by the {@code NeoForge*} glob (§5e-ter).
 *
 * <p><b>Every method below states which contract from the top of
 * {@code skills/SkillEvents.java} it owes the caller, and — where the loader has no
 * equivalent event — exactly how far the substitute is from it, with the artifact line that
 * proves the claim. That accounting is R-20's first bullet: a re-rooted event must reproduce
 * the event's own contract, not merely fire somewhere plausible.</b>
 *
 * <p>Bus assignment, from the artifacts: {@code BuildCreativeModeTabContentsEvent} is the
 * only one here that {@code implements IModBusEvent} (its class declaration says so), so it
 * goes on the mod bus; every other one is posted on {@code NeoForge.EVENT_BUS}. Registration
 * happens during mod construction, from the shared init the {@code @Mod} constructor calls,
 * and the mod bus is available because {@code SpecialitiesNeoForge} stashes it first.
 */
public final class NeoForgeEvents {
	/**
	 * Cached so the {@code afterDamage} hot path does not clone the enum's values array on
	 * every hit. Summing over ALL constants rather than the four the patched
	 * {@code actuallyHurt} happens to set today is deliberate: an unset reduction reads 0.0F
	 * ({@code DamageContainer.getReduction} is {@code reductions.getOrDefault(type, 0f)}), so
	 * the sum stays correct if NeoForge ever starts recording {@code INNATE_RESISTANCE} —
	 * the one constant with no writer in 21.1.243.
	 */
	private static final DamageContainer.Reduction[] REDUCTIONS = DamageContainer.Reduction.values();

	private NeoForgeEvents() {
	}

	// ------------------------------------------------------------------ block breaking

	/**
	 * The listener shape of {@code PlayerBlockBreakEvents.After}, parameter for parameter:
	 * {@code (Level, Player, BlockPos, BlockState, @Nullable BlockEntity)}.
	 */
	@FunctionalInterface
	public interface AfterBlockBreak {
		void afterBlockBreak(Level level, Player player, BlockPos pos, BlockState state,
				@Nullable BlockEntity blockEntity);
	}

	/**
	 * Mining / woodcutting / excavation / harvesting XP — a quarter of the mod.
	 *
	 * <p><b>THIS IS THE ONE GENUINE RE-ROOTING ON THIS NODE (prep Q1), AND IT IS A PRE EVENT
	 * STANDING IN FOR A POST ONE.</b> NeoForge has no "after break" event at all:
	 * {@code BlockEvent$BreakEvent} is cancellable and PRE, and {@code BlockDropsEvent} is
	 * not once-per-break. What each side actually does, measured:
	 *
	 * <ul>
	 * <li><b>Fabric</b> ({@code fabric-events-interaction-v0} 4.1.1, the 1.21.1 pin —
	 *     {@code mixin/event/interaction/ServerPlayerGameModeMixin.onBlockBroken}) injects at
	 *     {@code INVOKE Block.destroy(LevelAccessor,BlockPos,BlockState)V} inside
	 *     {@code ServerPlayerGameMode.destroyBlock}, capturing {@code @Local BlockState state}
	 *     and {@code @Local BlockEntity entity}. That call sits inside
	 *     {@code if (removeBlock(...)) { … }}, so AFTER fires only when the block really went
	 *     away — and it fires in creative too, because vanilla's {@code isCreative()} return
	 *     is further down.</li>
	 * <li><b>Here</b>: {@code BlockEvent.BreakEvent}, posted by
	 *     {@code CommonHooks.fireBlockBreak} ({@code common/CommonHooks.java:565-592}) from
	 *     the very top of the patched {@code ServerPlayerGameMode.destroyBlock}
	 *     ({@code patches/net/minecraft/server/level/ServerPlayerGameMode.java.patch}), i.e.
	 *     BEFORE removal. Same method, same once-per-player-break call site, same server-only
	 *     path.</li>
	 * </ul>
	 *
	 * <p>The three differences, and why each is either nil or negligible:
	 *
	 * <ol>
	 * <li><b>The block is still in the world when the callback runs.</b> No behavioural
	 *     effect: the shared lambda reads {@code state} (for
	 *     {@code SkillCategories.blockBreakSkill} / {@code blockBreakXp}) and touches the
	 *     level not at all. The {@code BlockEntity} handed over is fetched at PRE time, which
	 *     is what "the pre-break BlockEntity" means, so it is if anything more faithful than
	 *     reading it late.</li>
	 * <li><b>"Once per SUCCESSFUL break" is reproduced through cancellation, not through
	 *     ordering.</b> {@code fireBlockBreak} pre-cancels the event for exactly the three
	 *     conditions vanilla's {@code destroyBlock} used to early-return on — an item whose
	 *     {@code canAttackBlock} is false, {@code Player.blockActionRestricted}, and a
	 *     {@code GameMasterBlock} the player may not use — and the patch DELETED those three
	 *     vanilla checks in favour of the event. So an uncancelled event means the removal is
	 *     reached. The only residue is {@code BlockState.onDestroyedByPlayer} returning false
	 *     (i.e. {@code Level.setBlock} failing), which no vanilla block does in normal play.
	 *     This listener is registered at {@link EventPriority#LOWEST} with the default
	 *     {@code receiveCanceled = false} ({@code bus-8.0.5} {@code IEventBus.java:78/101}),
	 *     so it runs last and is skipped entirely if any other mod cancels the break — which
	 *     is the closest a PRE event can get to "the break happened".</li>
	 * <li><b>Creative parity is exact</b> — the event fires for creative breaks, the same way
	 *     Fabric's AFTER does, and the shared lambda's own {@code isCreative()} filter is what
	 *     rejects them on every node.</li>
	 * </ol>
	 *
	 * <p>Design §5c's preferred answer — a mixin on the destroy path, which would resolve on
	 * all seven nodes and delete this fabric-api event outright — is prep Q1 and is NOT taken
	 * here: it needs a new file in the shared {@code src/main/java/com/specialities/mixin/}
	 * and a new entry in the shared {@code specialities.mixins.json}, neither of which this
	 * node's agent owns. It is reported as cross-cutting instead.
	 */
	public static void afterBlockBreak(final AfterBlockBreak listener) {
		NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, BlockEvent.BreakEvent.class, event -> {
			// getLevel() is declared LevelAccessor on the BlockEvent base, but BreakEvent's own
			// constructor takes a Level (BlockEvent.java:71), so the cast is sound by
			// construction rather than by hope.
			Level level = (Level) event.getLevel();
			listener.afterBlockBreak(level, event.getPlayer(), event.getPos(), event.getState(),
					level.getBlockEntity(event.getPos()));
		});
	}

	// ------------------------------------------------------------------ damage

	/** The listener shape of the shared {@code SkillEvents.afterDamage} substitute. */
	@FunctionalInterface
	public interface AfterDamage {
		void afterDamage(LivingEntity entity, DamageSource source, float damageTaken);
	}

	/**
	 * Combat, arms-mastery, archery, defence and acrobatics XP — the stand-in for
	 * {@code ServerLivingEntityEvents.AFTER_DAMAGE}, which NeoForge does not have.
	 *
	 * <p><b>ALL THREE AFTER_DAMAGE SEMANTICS ARE REPRODUCED HERE, and R-20 exists because the
	 * first re-rooting of this event broke all three.</b> The reference is
	 * {@code fabric-entity-events-v1} 1.8.0 (the 1.21.1 pin),
	 * {@code mixin/entity/event/LivingEntityMixin.afterDamage}: an {@code @Inject} at
	 * {@code TAIL} of {@code LivingEntity.hurt(DamageSource,float)Z} with
	 * {@code LocalCapture.CAPTURE_FAILHARD}, capturing {@code float dealt} and
	 * {@code boolean blocked}, guarded by {@code if (!isDead())}, invoking
	 * {@code afterDamage(entity, source, dealt, amount, blocked)} — where {@code amount} is
	 * the METHOD ARGUMENT slot, mutated in place by the shield / freezing / helmet math and
	 * therefore post-shield and <b>PRE-ARMOR</b>. The event's own javadoc says the same in
	 * words: "after effects such as shields and extra freezing damage are applied. Damage
	 * taken does NOT include damage reduction from armor and enchantments."
	 *
	 * <ol>
	 * <li><b>Which value is reported.</b> {@code LivingDamageEvent.Post} carries the FINAL
	 *     health loss, which is post-armor — using it raw is precisely the R-20 bug. The
	 *     pre-armor figure is recovered exactly, not approximated:
	 *     {@code DamageContainer.setReduction} is {@code this.newDamage -= modifiedReduction}
	 *     ({@code common/damagesource/DamageContainer.java:169-173}), and every reduction the
	 *     patched {@code actuallyHurt} records is readable from the event
	 *     ({@code getReduction(Reduction)}), so
	 *     <b>{@code preArmor = getNewDamage() + Σ getReduction(r)}</b> over
	 *     {@code Reduction.values()}. Traced against the patched source for the four shapes
	 *     that matter: plain armoured hit, full shield block, freezing multiplier, and the
	 *     {@code invulnerableTime > 10} branch (where {@code INVULNERABILITY} holds
	 *     {@code lastHurt}, so the sum recovers the full amount Fabric reports rather than the
	 *     delta {@code actuallyHurt} was called with). Shield blocking is NOT in the reduction
	 *     map — {@code setBlockedDamage} subtracts it separately ({@code :161-167}) — which is
	 *     right, because Fabric's {@code amount} is post-shield too.</li>
	 * <li><b>The death gate.</b> {@code onLivingDamagePost} is called at the end of
	 *     {@code actuallyHurt}, after {@code setHealth}, and {@code hurt} calls {@code die}
	 *     only afterwards — so {@code entity.isDeadOrDying()} at this point is exactly what
	 *     Fabric's {@code !isDead()} at TAIL tests. Without it a killing blow pays XP, which
	 *     is R-20's third finding.</li>
	 * <li><b>The two consumer filters.</b> {@code blocked} and {@code damageTaken <= 0} are
	 *     applied here, because the shared {@code SkillEvents.afterDamage} does not (the
	 *     comment at its head says so explicitly, and the 1.20.1-fabric mixin applies them the
	 *     same way).</li>
	 * </ol>
	 *
	 * <p>Two honest residues, neither of them a balance channel:
	 *
	 * <ul>
	 * <li>{@code blocked} is derived as {@code getBlockedDamage() > 0}. Vanilla's {@code flag}
	 *     local is "the shield blocked"; the patch redefines it as {@code p_21017_ <= 0}
	 *     ("fully blocked") while still recording a positive {@code blockedDamage} for a
	 *     partial block. {@code > 0} is the VANILLA meaning, so this matches Fabric rather
	 *     than the patch. On a vanilla-only install a block is always total, so the two agree
	 *     regardless.</li>
	 * <li>A totem of undying restores health to 1 after {@code actuallyHurt} but before
	 *     Fabric's TAIL, so Fabric pays victim XP for a totem-saved blow and this node does
	 *     not. There is no post-{@code hurt} event on this loader to close it with, and the
	 *     size of the divergence is one defence-XP award per totem consumption.</li>
	 * </ul>
	 *
	 * <p>{@code hurt} runs on both logical sides at 1.21.1 (that is the whole reason
	 * {@code >=1.21.2}'s {@code hurtServer} exists), so this early-outs on the client the way
	 * conventions §5a requires of every legacy {@code hurt} hook.
	 */
	public static void afterDamage(final AfterDamage listener) {
		NeoForge.EVENT_BUS.addListener(LivingDamageEvent.Post.class, event -> {
			LivingEntity entity = event.getEntity();
			if (entity.level().isClientSide()) {
				return;
			}

			// AFTER_DAMAGE is not fired if the entity was killed by the damage.
			if (entity.isDeadOrDying()) {
				return;
			}

			boolean blocked = event.getBlockedDamage() > 0.0F;
			float damageTaken = event.getNewDamage();
			for (DamageContainer.Reduction reduction : REDUCTIONS) {
				damageTaken += event.getReduction(reduction);
			}

			if (blocked || damageTaken <= 0.0F) {
				return;
			}

			listener.afterDamage(entity, event.getSource(), damageTaken);
		});
	}

	/** The listener shape of {@code ServerLivingEntityEvents.AllowDamage} — false vetoes. */
	@FunctionalInterface
	public interface AllowDamage {
		boolean allowDamage(LivingEntity entity, DamageSource source, float amount);
	}

	/**
	 * The acrobatics fall-damage immunity veto — {@code ServerLivingEntityEvents.ALLOW_DAMAGE}.
	 *
	 * <p>This one is a rename rather than a re-rooting, and the sites line up exactly.
	 * Fabric's {@code beforeDamage} injects at {@code INVOKE LivingEntity.isSleeping()Z} in
	 * {@code hurt} and calls {@code cir.setReturnValue(false)} on a veto. The patched
	 * {@code LivingEntity.hurt} posts {@code LivingIncomingDamageEvent} on the line
	 * IMMEDIATELY BEFORE {@code if (this.isSleeping() && !this.level().isClientSide)}
	 * ({@code patches/net/minecraft/world/entity/LivingEntity.java.patch}:
	 * {@code if (CommonHooks.onEntityIncomingDamage(this, this.damageContainers.peek()))
	 * return false;}), and cancelling it makes {@code hurt} return false. So: same position,
	 * same "before armor" guarantee, same boolean-veto meaning.
	 *
	 * <p>{@code getAmount()} is {@code DamageContainer.getNewDamage()}, which at that point is
	 * still the raw argument {@code hurt} was called with — the value Fabric passes. No client
	 * guard is added: the shared predicate already returns {@code true} for anything whose
	 * level is not a {@code ServerLevel}, so a guard here would be dead weight and would hide
	 * that fact.
	 */
	public static void allowDamage(final AllowDamage listener) {
		NeoForge.EVENT_BUS.addListener(LivingIncomingDamageEvent.class, event -> {
			if (!listener.allowDamage(event.getEntity(), event.getSource(), event.getAmount())) {
				event.setCanceled(true);
			}
		});
	}

	// ------------------------------------------------------------------ player lifecycle

	/**
	 * {@code ServerPlayerEvents.JOIN} — the defence-passive re-application and the R-03 resync.
	 *
	 * <p>Contract owed: "once the connection can receive packets". Met, and provably so:
	 * {@code patches/net/minecraft/server/players/PlayerList.java.patch} fires
	 * {@code EventHooks.firePlayerLoggedIn} at the very end of {@code placeNewPlayer}, after
	 * every login packet has been sent and after {@code initInventoryMenu()} — and, on the
	 * line before it, after {@code AttachmentSync.syncInitialPlayerAttachments(player)}, which
	 * is the answer to prep Q2 and the reason {@code resyncSkills} is a no-op on this node
	 * (see {@code NeoForgeSkillStore}).
	 *
	 * <p>{@code PlayerEvent.getEntity()} is declared {@code Player}
	 * ({@code event/entity/player/PlayerEvent.java:45}), so the {@code ServerPlayer} narrowing
	 * happens here rather than in shared code. It is a filter, not a cast that can fail: the
	 * event is only ever posted from {@code PlayerList}.
	 */
	public static void playerJoin(final Consumer<ServerPlayer> listener) {
		NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedInEvent.class, event -> {
			if (event.getEntity() instanceof ServerPlayer player) {
				listener.accept(player);
			}
		});
	}

	/**
	 * {@code ServerPlayerEvents.AFTER_RESPAWN} — contract owed: the NEW player instance.
	 *
	 * <p>Met by construction: {@code EventHooks.firePlayerRespawnEvent(serverplayer, …)} is
	 * called from {@code PlayerList.respawn} with the freshly created {@code serverplayer}
	 * (the patch line sits immediately after {@code serverplayer.setHealth(...)} and after
	 * {@code syncInitialPlayerAttachments(serverplayer)}), and
	 * {@code PlayerEvent.PlayerRespawnEvent}'s constructor passes that same instance to
	 * {@code super(player)}. The Fabric arm hands {@code DefencePassives::apply} the
	 * {@code newPlayer} of its {@code (oldPlayer, newPlayer, alive)} triple; the old instance
	 * and the alive flag are unused on every node, so nothing is lost by not having them.
	 */
	public static void afterRespawn(final Consumer<ServerPlayer> listener) {
		NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerRespawnEvent.class, event -> {
			if (event.getEntity() instanceof ServerPlayer player) {
				listener.accept(player);
			}
		});
	}

	/**
	 * {@code ServerPlayerEvents.LEAVE} — contract owed: before the player object is discarded.
	 *
	 * <p>Met: {@code EventHooks.firePlayerLoggedOut} is the FIRST statement of the patched
	 * {@code PlayerList.remove}, ahead of the save and the removal, so the two tickers'
	 * {@code onLeave} bookkeeping still sees a live player.
	 */
	public static void playerLeave(final Consumer<ServerPlayer> listener) {
		NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedOutEvent.class, event -> {
			if (event.getEntity() instanceof ServerPlayer player) {
				listener.accept(player);
			}
		});
	}

	/**
	 * {@code ServerTickEvents.END_SERVER_TICK} — contract owed: once per server tick, at the
	 * END of it. {@code ServerTickEvent.Post}'s own javadoc is "fired once per server tick,
	 * after the server performs work for the current tick… only fires on the logical server",
	 * which is the contract verbatim.
	 */
	public static void endServerTick(final Consumer<MinecraftServer> listener) {
		NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, event -> listener.accept(event.getServer()));
	}

	// ------------------------------------------------------------------ registration events

	/**
	 * <b>THE ONE CROSS-CUTTING BLOCKER THIS NODE FOUND, and the loader-side half of its fix.
	 * As committed, nothing calls this method — the shared arm that would is reported rather
	 * than written, because {@code Specialities.java} is not this node's file.</b>
	 *
	 * <p>The Phase B prep plan costed {@code ModItems.java} at "one import + one inline
	 * registration line" (the creative tab). That is wrong, and the failure is a hard boot
	 * crash, measured on a real NeoForge 21.1.243 dedicated server:
	 *
	 * <pre>
	 * Failed to create mod instance. ModID: specialities, class …SpecialitiesNeoForge
	 * java.lang.ExceptionInInitializerError
	 *   at com.specialities.Specialities.onInitialize(Specialities.java:58)
	 * Caused by: java.lang.IllegalStateException: Registry is already frozen
	 *   at net.minecraft.core.MappedRegistry.validateWrite(MappedRegistry.java:111)
	 *   at net.minecraft.core.MappedRegistry.createIntrusiveHolder(MappedRegistry.java:384)
	 *   at net.minecraft.world.item.Item.&lt;init&gt;(Item.java:67)
	 *   at com.specialities.items.SkillBookItem.&lt;init&gt;(SkillBookItem.java:29)
	 *   at com.specialities.ModItems.registerBook(ModItems.java:75)
	 *   at com.specialities.ModItems.&lt;clinit&gt;(ModItems.java:33)
	 * </pre>
	 *
	 * <p>Note WHERE it throws: not in {@code Registry.register}, but in {@code Item}'s own
	 * constructor, which initialises {@code builtInRegistryHolder} through
	 * {@code BuiltInRegistries.ITEM.createIntrusiveHolder(this)}. So it is not enough to move
	 * the {@code Registry.register} call — the thirty {@code new SkillBookItem(...)}
	 * CONSTRUCTIONS, i.e. {@code ModItems}' whole class initialiser, have to happen while the
	 * ITEM registry is unfrozen. On Fabric it always is during mod init; on NeoForge the
	 * built-in registries are frozen before mod construction and are unfrozen for exactly one
	 * window: {@code CommonModLoader.begin} runs
	 * {@code RegistryManager.postNewRegistryEvent(); GameData.unfreezeData();
	 * GameData.postRegisterEvents(); GameData.freezeData();}
	 * ({@code internal/CommonModLoader.java:52-55}), and {@code GameData.unfreezeData()}
	 * unfreezes every {@code BaseMappedRegistry} in {@code BuiltInRegistries.REGISTRY}
	 * ({@code registries/GameData.java:63-66}). {@code RegisterEvent} is posted inside that
	 * window, one event per registry.
	 *
	 * <p>Hence this helper, and hence the shared arm that calls it — <b>landed in Stage 6's
	 * integration commit</b>, in {@code Specialities.onInitialize()}, replacing the
	 * unconditional {@code ModItems.initialize()} with a three-arm loader chain whose
	 * {@code neoforge} arm is {@code NeoForgeEvents.registerItems(ModItems::initialize)}. The
	 * {@code forge} arm is a plain {@code ModItems.initialize()} and needs no helper of its own:
	 * {@code SpecialitiesForge} defers the WHOLE shared init into {@code RegisterEvent(ITEM)},
	 * which it can do safely because that node has no {@code DeferredRegister} anywhere. This
	 * node cannot copy that: {@code NeoForgeSkillStore.initialize()} calls
	 * {@code ATTACHMENTS.register(modEventBus)}, i.e. it adds a {@code RegisterEvent} listener,
	 * and adding a listener for the event class currently being dispatched mutates the very
	 * {@code ListenerList} the bus is iterating.
	 *
	 * <b>MEASURED, not proposed:</b> with exactly that arm the node boots a real headless
	 * dedicated server clean — all thirty items registered, every common mixin applied, all six
	 * tags resolved, {@code /skillprof} present.
	 *
	 * <p>Three consequences worth carrying into the review of that arm:
	 *
	 * <ul>
	 * <li><b>The init ORDER design §7 asserts is preserved, but one step's EFFECT moves.</b> The
	 *     seven calls still run in the same sequence; on the loader axis the fourth one only
	 *     ARMS the item registration, which then happens at registry-init time. Nothing between
	 *     it and the end of init reads an item — {@code Net.registerClientbound},
	 *     {@code SkillEvents.register} and {@code SkillCommands.register} all touch none — and
	 *     {@code SkillTypes.pullEntrypoints}, which does feed the skill list the books are
	 *     derived from, runs BEFORE it on every node.</li>
	 * <li><b>The creative-tab registration rides along safely.</b> {@code ModItems.initialize()}
	 *     also calls {@link #creativeTabOutput}, which adds a mod-bus listener while the mod bus
	 *     is posting {@code RegisterEvent}. That is a listener for a DIFFERENT event class, so
	 *     the {@code ListenerList} being iterated is not the one mutated
	 *     ({@code bus-8.0.5 EventBus.addToListeners} → {@code getListenerList(eventType)}), and
	 *     {@code BuildCreativeModeTabContentsEvent} has not been posted yet.</li>
	 * <li><b>1.20.1-forge hit the identical wall</b>, independently and on its own first boot.
	 *     1.20.1's {@code Item} has the same intrusive-holder field initialiser and LexForge
	 *     freezes the built-in registries before mod construction too. It is contained
	 *     differently — see {@code SpecialitiesForge} — and the asymmetry is deliberate, for the
	 *     bus reason above.</li>
	 * </ul>
	 *
	 * @param initializer {@code ModItems::initialize} — run once, inside the ITEM
	 *                    {@code RegisterEvent}, which is what triggers that class's initialiser
	 *                    at a point where the registry accepts writes.
	 */
	public static void registerItems(final Runnable initializer) {
		SpecialitiesNeoForge.modEventBus().addListener(RegisterEvent.class, event -> {
			if (event.getRegistryKey().equals(Registries.ITEM)) {
				initializer.run();
			}
		});
	}

	/**
	 * The creative-tab hook {@code ModItems.initialize()} forks onto.
	 *
	 * <p>{@code BuildCreativeModeTabContentsEvent implements CreativeModeTab.Output} (its class
	 * declaration, {@code event/BuildCreativeModeTabContentsEvent.java:27}), which is what lets
	 * all thirty {@code output.accept(...)} lines stay outside the conditional in
	 * {@code ModItems} — the same trick the Fabric arms use with
	 * {@code FabricItemGroupEntries}. The event fires for EVERY tab, so the key filter is
	 * mandatory; {@code getTabKey()} returns the {@code ResourceKey<CreativeModeTab>}.
	 *
	 * <p>It is an {@code IModBusEvent}, so it goes on the mod bus. Its javadoc warns it "may
	 * be fired multiple times if the operator status of the local player or enabled feature
	 * flags changes" — harmless here, because the event's own {@code accept} is what dedupes
	 * (it throws on a duplicate entry only within one build, and each build starts from a
	 * fresh entry set).
	 */
	public static void creativeTabOutput(final ResourceKey<CreativeModeTab> tab,
			final Consumer<CreativeModeTab.Output> listener) {
		SpecialitiesNeoForge.modEventBus().addListener(BuildCreativeModeTabContentsEvent.class, event -> {
			if (event.getTabKey().equals(tab)) {
				listener.accept(event);
			}
		});
	}

	/**
	 * {@code CommandRegistrationCallback.EVENT} — {@code RegisterCommandsEvent} on the game
	 * bus, fired "whenever the {@code Commands} class is constructed", i.e. on server start and
	 * on every {@code /reload}, which is where the Fabric callback fires too. The shared tree
	 * uses only the dispatcher; the build context and the command selection are ignored on
	 * every node.
	 */
	public static void registerCommands(final Consumer<CommandDispatcher<CommandSourceStack>> listener) {
		NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class, event -> listener.accept(event.getDispatcher()));
	}
}
