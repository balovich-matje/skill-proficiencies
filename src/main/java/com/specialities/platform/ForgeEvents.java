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
	 */
	public static void afterRespawn(final Consumer<ServerPlayer> listener) {
		MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerRespawnEvent event) -> {
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
