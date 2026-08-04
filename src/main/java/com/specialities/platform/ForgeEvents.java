package com.specialities.platform;

import java.util.function.Consumer;

import com.mojang.brigadier.CommandDispatcher;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
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
 * The LexForge half of the event seam for the {@code 1.20.1-forge} node: one small method per
 * fabric-api registration that {@code skills/SkillEvents}, {@code ModItems} and
 * {@code command/SkillCommands} fork on. Every listener body those files pass in stays shared —
 * only the registration crosses into this file, which is why this file names LexForge API and
 * nothing outside {@code com.specialities.platform} does (conventions §5g).
 *
 * <p>THE CONTRACT EACH METHOD OWES ITS CALLER is written at the top of
 * {@code skills/SkillEvents}, and R-20's first bullet is why it is written there rather than
 * assumed: a re-rooted event has to reproduce the original's CONTRACT, not merely fire
 * somewhere plausible. What follows is that contract checked one method at a time against
 * {@code forge-1.20.1-47.4.22-sources.jar} — specifically its {@code patches/} tree, which is
 * the only place the FIRING SITE of a Forge event can be read.
 *
 * <p><b>{@code afterDamage} is deliberately absent, and that is a deviation from the plan.</b>
 * The prep plan gave this file nine methods including one for the AFTER_DAMAGE substitute.
 * There must not be one: {@code mixin/LivingEntityMixin}'s
 * {@code specialities$afterDamageXp} is gated {@code //? if >=1.20.5 { } else { … }}, i.e. on
 * the VERSION alone, so it is live on this node too and already calls
 * {@code SkillEvents.afterDamage} from TAIL of {@code LivingEntity.hurt}. Registering a
 * {@code LivingHurtEvent} listener on top of it would award every combat, defence and
 * acrobatics XP gain TWICE. Conventions settle which one wins — "a mixin whose target resolves
 * on the platform stays a mixin there, even when the platform offers a tidier event" — and
 * {@code hurt(DamageSource,F)Z} resolves here unchanged (Forge patches the method but neither
 * its signature nor its TAIL). The mixin also already has R-20's three semantics
 * adversarially reviewed; a fresh re-rooting onto {@code LivingHurtEvent} would need that
 * review again from scratch. So {@code SkillEvents}' {@code elif forge} arm registers nothing
 * and says so.
 */
public final class ForgeEvents {
	private ForgeEvents() {
	}

	/**
	 * Fires after a successful player block break, with the pre-break {@code BlockState}.
	 *
	 * <p><b>THIS IS A RE-ROOTING, NOT A RENAME, AND IT IS THE ONE PLACE THIS NODE IS KNOWINGLY
	 * NOT EXACT.</b> LexForge 1.20.1 has no "after break" event at all. The only player-break
	 * event is {@code BlockEvent$BreakEvent}, which
	 * {@code patches/net/minecraft/server/level/ServerPlayerGameMode.java.patch} shows being
	 * posted from {@code ForgeHooks.onBlockBreakEvent} at the TOP of
	 * {@code destroyBlock(BlockPos)} — before the block is removed.
	 *
	 * <p>What that costs, exactly. The listener is registered at {@link EventPriority#LOWEST}
	 * and skips a cancelled event, so every other mod's veto and Forge's own pre-cancel (which
	 * {@code ForgeHooks.onBlockBreakEvent} applies for the wrong-tool, spectator and
	 * adventure-restriction cases — read from the same file) have already been applied. What
	 * remains are three vanilla early-returns AFTER the event: a {@code GameMasterBlock}
	 * without permission, an item whose {@code onBlockStartBreak} returns true, and
	 * {@code Player.blockActionRestricted}. In those three cases this node awards XP for a
	 * break that did not happen. None of the four XP-bearing skills
	 * (mining/woodcutting/excavation/harvesting) can be earned off a game-master block, and
	 * the other two are modded-item and spawn-protection edge cases.
	 *
	 * <p>The RIGHT fix is the one the prep plan's Q1 identifies and does not own: a mixin on
	 * {@code ServerPlayerGameMode.destroyBlock}, which resolves on all seven nodes and would
	 * make this the shared implementation everywhere and delete the Fabric event too. That is
	 * a cross-cutting change to the shared tree and is reported, not made here.
	 *
	 * <p>The {@code BlockEntity} handed to the listener is read from the level at the event's
	 * position, which is still present at this point — so on this node it is the live block
	 * entity rather than a detached copy. The shared listener does not use it.
	 */
	public static void afterBlockBreak(final BlockBreakListener listener) {
		MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, (BlockEvent.BreakEvent event) -> {
			if (event.isCanceled()) {
				return;
			}

			// BlockEvent.getLevel() is typed LevelAccessor, but BreakEvent's only constructor
			// takes a Level and passes it straight to super — so this is always a Level.
			if (!(event.getLevel() instanceof Level level)) {
				return;
			}

			BlockPos pos = event.getPos();
			listener.afterBlockBreak(level, event.getPlayer(), pos, event.getState(),
					level.getBlockEntity(pos));
		});
	}

	/**
	 * A boolean veto on incoming damage: {@code false} cancels the damage entirely, and it must
	 * run before armor.
	 *
	 * <p>{@code LivingAttackEvent} is the exact counterpart.
	 * {@code patches/net/minecraft/world/entity/LivingEntity.java.patch} shows
	 * {@code ForgeHooks.onLivingAttack} as the FIRST statement of
	 * {@code hurt(DamageSource,float)} — ahead of {@code isInvulnerableTo}, the shield block,
	 * freezing and everything in {@code actuallyHurt} — and the event is {@code @Cancelable},
	 * with the hook returning {@code !event.isCanceled()} straight into
	 * {@code if (!…) return false}. That is precisely
	 * {@code ServerLivingEntityEvents.ALLOW_DAMAGE}'s contract: a false vote makes
	 * {@code hurt} return false with no hurt flash and no knockback, which is what the
	 * acrobatics fall-immunity passive needs.
	 */
	public static void allowDamage(final AllowDamageListener listener) {
		MinecraftForge.EVENT_BUS.addListener((LivingAttackEvent event) -> {
			if (!listener.allowDamage(event.getEntity(), event.getSource(), event.getAmount())) {
				event.setCanceled(true);
			}
		});
	}

	/**
	 * Fires once the joining player's connection can receive packets — which is mandatory here,
	 * because {@code SkillStore.resyncSkills} sends one and it is this node's only sync.
	 *
	 * <p>{@code patches/net/minecraft/server/players/PlayerList.java.patch} puts
	 * {@code firePlayerLoggedIn(player)} at the very END of {@code placeNewPlayer}, after the
	 * {@code ServerGamePacketListenerImpl} is installed and after the login/difficulty/
	 * abilities/recipes/tags packets have already been written to it. So the channel is open.
	 */
	public static void playerJoin(final Consumer<ServerPlayer> listener) {
		MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
			if (event.getEntity() instanceof ServerPlayer player) {
				listener.accept(player);
			}
		});
	}

	/**
	 * Fires with the NEW player instance after a respawn.
	 *
	 * <p>Same patch file: {@code firePlayerRespawnEvent(serverplayer, endConquered)} is posted
	 * from {@code PlayerList.respawn} after the new {@code ServerPlayer} has been put into the
	 * player map and its health restored, and {@code serverplayer} IS the new instance. That is
	 * what {@code DefencePassives.apply} needs — it writes attribute modifiers onto the object
	 * the client will see.
	 *
	 * <p><b>It has a SECOND consumer since the desync fix, and the R-20 ordering is what makes
	 * that safe.</b> {@code javap -c} of {@code PlayerList.respawn} on the merged jar:
	 * {@code ClientboundRespawnPacket} is built at 467/542, the new player goes into the map at
	 * 709, {@code initInventoryMenu} at 717, {@code setHealth} at 727 and
	 * {@code firePlayerRespawnEvent} at 733 — so the client has already been told to rebuild its
	 * {@code LocalPlayer} by the time this fires, which is exactly the window
	 * {@code ForgeSkillStore}'s resync needs. A death respawn is one of the three vanilla senders
	 * of that packet on this version (the others are {@code changeDimension} and the
	 * cross-dimension {@code teleportTo}); see {@link #playerChangedDimension} for the other two.
	 */
	public static void afterRespawn(final Consumer<ServerPlayer> listener) {
		MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerRespawnEvent event) -> {
			if (event.getEntity() instanceof ServerPlayer player) {
				listener.accept(player);
			}
		});
	}

	/**
	 * Fires with the player AFTER it has arrived in a new dimension — every portal (vanilla and
	 * modded alike), every cross-dimension teleport.
	 *
	 * <p><b>WHY THIS EXISTS AND NO OTHER NODE HAS IT.</b> A dimension change makes the vanilla
	 * server send {@code ClientboundRespawnPacket}, and every client that receives one throws
	 * its {@code LocalPlayer} away and builds a new one. On the five Fabric nodes that costs
	 * nothing, because fabric-api's own
	 * {@code mixin/attachment/client/ClientPlayNetworkHandlerMixin} wraps that very call and
	 * copies the old player's attachments onto the new one — present and identical in
	 * {@code fabric-data-attachment-api-v1} <b>0.92.11</b> and <b>0.116.14</b> (javap: one
	 * {@code AttachmentTargetImpl.transfer(old, new, !packet.shouldKeep(KEEP_ATTRIBUTES))}), and
	 * in the 1.8.48 / 2.2.x rewrites as {@code ClientPacketListenerMixin}. On NeoForge it costs
	 * nothing either, because NeoForge patches {@code AttachmentSync.syncInitialPlayerAttachments}
	 * into {@code ServerPlayer.changeDimension} itself (javap of the 21.1.243 merged jar: offset
	 * 407, i.e. AFTER the respawn packet at 145).
	 *
	 * <p><b>LexForge patches in no such thing, and capabilities have no sync at all</b>, so this
	 * node's client rebuilt its player, got a fresh empty {@link ForgeSkillStore.SkillsHolder}
	 * from {@code AttachCapabilitiesEvent}, and nothing ever re-sent the map. Server state was
	 * never involved: {@code ServerPlayer.changeDimension} calls {@code revive()} (javap offset
	 * 237), which is {@code unsetRemoved(); reviveCaps();}. Only the client mirror died.
	 *
	 * <p><b>R-20 ORDERING, and it is the whole reason this is safe</b> — a resync fired before
	 * the client swaps its {@code LocalPlayer} would write into an object about to be discarded,
	 * fail silently, and look exactly like no fix at all. Measured with {@code javap -c} on the
	 * {@code forge-1.20.1-47.4.22} merged jar, both firing sites, and the event is LAST in both:
	 *
	 * <pre>
	 *   ServerPlayer.changeDimension(ServerLevel, ITeleporter)
	 *     129/179  new/&lt;init&gt; ClientboundRespawnPacket
	 *     233      removePlayerImmediately
	 *     237      revive()
	 *     473      ForgeEventFactory.firePlayerChangedDimensionEvent   &lt;-- here
	 *
	 *   ServerPlayer.teleportTo(ServerLevel, double, double, double, float, float)
	 *      63/113  new/&lt;init&gt; ClientboundRespawnPacket
	 *     168      revive()
	 *     257      ForgeEventFactory.firePlayerChangedDimensionEvent   &lt;-- here
	 * </pre>
	 *
	 * <p>Both are confirmed in {@code patches/net/minecraft/server/level/ServerPlayer.java.patch}
	 * as well. The event carries the same {@code ServerPlayer} throughout — a dimension change
	 * does not clone the player, unlike a respawn — so no "which instance" question arises.
	 */
	public static void playerChangedDimension(final Consumer<ServerPlayer> listener) {
		MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent event) -> {
			if (event.getEntity() instanceof ServerPlayer player) {
				listener.accept(player);
			}
		});
	}

	/**
	 * Fires when a player's game mode changes.
	 *
	 * <p><b>THIS ONE IS PURE BELT, and the honest measurement is that nothing is lost here on
	 * any node.</b> {@code javap -c} of {@code ServerPlayerGameMode.changeGameModeForPlayer} on
	 * the 1.20.1 merged jar shows it sending {@code ClientboundPlayerInfoUpdatePacket}
	 * ({@code UPDATE_GAME_MODE}) plus {@code onUpdateAbilities} and
	 * {@code updateSleepingPlayerList} — and <b>no {@code ClientboundRespawnPacket}</b>. So the
	 * client does not rebuild its {@code LocalPlayer} and the skill map is untouched. A
	 * "switching to creative wiped my skills" report is therefore never this event; it is the
	 * dimension change that came before it.
	 *
	 * <p>It is registered anyway because the guarantee this node needs is "every client-visible
	 * player-lifecycle transition re-pushes the map", and an invariant that holds only because
	 * of a vanilla implementation detail is one refactor away from being false. One packet on an
	 * event a player fires by hand costs nothing.
	 *
	 * <p>Fired from {@code ForgeHooks.onChangeGameType}, which {@code ServerPlayer.setGameMode}
	 * calls at offset 9 — i.e. BEFORE the change is applied, and cancellably. That is fine for a
	 * resync and would not be for a state write: the value being pushed is the skill map, which
	 * this event never touches, so pushing it for a change that is subsequently vetoed is
	 * idempotent rather than wrong.
	 */
	public static void playerChangeGameMode(final Consumer<ServerPlayer> listener) {
		MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangeGameModeEvent event) -> {
			if (event.getEntity() instanceof ServerPlayer player) {
				listener.accept(player);
			}
		});
	}

	/**
	 * Fires before the player object is discarded.
	 *
	 * <p>Same patch file: {@code firePlayerLoggedOut(player)} is the FIRST statement of
	 * {@code PlayerList.remove}, ahead of the stat save and the removal from the level. The two
	 * tickers only need the player's identity, and they still have it.
	 */
	public static void playerLeave(final Consumer<ServerPlayer> listener) {
		MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
			if (event.getEntity() instanceof ServerPlayer player) {
				listener.accept(player);
			}
		});
	}

	/**
	 * Fires once per server tick, at the END of it.
	 *
	 * <p>{@code TickEvent.ServerTickEvent} carries a {@code public final Phase phase} and a
	 * {@code MinecraftServer getServer()}; the {@code Phase.END} filter is what makes this
	 * {@code ServerTickEvents.END_SERVER_TICK} rather than {@code START_SERVER_TICK}. Without
	 * the filter the athletics and sneaking tickers would run twice per tick.
	 */
	public static void endServerTick(final Consumer<MinecraftServer> listener) {
		MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent event) -> {
			if (event.phase == TickEvent.Phase.END) {
				listener.accept(event.getServer());
			}
		});
	}

	/**
	 * Adds entries to one creative tab.
	 *
	 * <p>{@code BuildCreativeModeTabContentsEvent implements CreativeModeTab.Output}, which is
	 * the whole reason {@code ModItems}' thirty {@code output.accept(...)} lines stay outside
	 * the conditional on this node exactly as they do on Fabric. It also
	 * {@code implements IModBusEvent}, so it goes on the MOD bus, and {@code getTabKey()} is
	 * the filter that replaces fabric-api's per-tab event object.
	 */
	public static void creativeTabOutput(final ResourceKey<CreativeModeTab> tab,
			final Consumer<CreativeModeTab.Output> filler) {
		SpecialitiesForge.modEventBus().addListener((BuildCreativeModeTabContentsEvent event) -> {
			if (event.getTabKey() == tab) {
				filler.accept(event);
			}
		});
	}

	/**
	 * Hands the command dispatcher over so the shared command tree can be registered.
	 *
	 * <p>{@code RegisterCommandsEvent} exposes {@code getDispatcher()},
	 * {@code getCommandSelection()} and {@code getBuildContext()} — the same three things
	 * fabric-api's {@code CommandRegistrationCallback} passes — and the shared registration
	 * only wants the first, so the other two are dropped here exactly as the Fabric arm drops
	 * them in its lambda. Game bus.
	 */
	public static void registerCommands(final Consumer<CommandDispatcher<CommandSourceStack>> listener) {
		MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent event) ->
				listener.accept(event.getDispatcher()));
	}

	/**
	 * The five parameters {@code PlayerBlockBreakEvents.AFTER} passes, in that order, so the
	 * shared lambda in {@code SkillEvents} infers them and needs no change.
	 */
	@FunctionalInterface
	public interface BlockBreakListener {
		void afterBlockBreak(Level level, Player player, BlockPos pos, BlockState state,
				@Nullable BlockEntity blockEntity);
	}

	/** {@code ServerLivingEntityEvents.ALLOW_DAMAGE}'s shape: false cancels the damage. */
	@FunctionalInterface
	public interface AllowDamageListener {
		boolean allowDamage(LivingEntity entity, DamageSource source, float amount);
	}
}
