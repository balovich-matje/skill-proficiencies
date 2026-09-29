package com.specialities.skills;

import com.specialities.config.ConfigManager;
import com.specialities.config.SpecialitiesConfig;
import com.specialities.platform.Net;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The effective skill level cap: {@link Tuning#MAX_LEVEL} (100) normally, or the server's
 * {@code extendedMaxLevel} when {@code extendedLevels} is on (GitHub issue #6).
 *
 * <p><b>The cap is the SERVER's setting, never the client's.</b> Every level in the mod is
 * derived from stored total XP through {@link Tuning#levelForTotalXp}, and the client derives
 * its own copy for the HUD bar, the skills screen and the break-speed prediction. So the one
 * number that decides where that derivation stops has to be the server's, and a client with a
 * different local config must not use its own. The value lives in ONE static that is written
 * from exactly three places:
 *
 * <ul>
 * <li>{@link #applyConfig()} — this JVM's own config. Common init (a dedicated server, or a
 *     client before any world), every player join on a logical server (so an integrated server
 *     re-asserts its own config after the client last visited a remote one), and the Mod Menu
 *     screen's save while a singleplayer/LAN world is open.</li>
 * <li>{@link #acceptFromServer(int)} — a physical client receiving
 *     {@code specialities:level_cap}, which the server sends on every join.</li>
 * </ul>
 *
 * <p>One static serves both logical sides in singleplayer on purpose: the integrated server
 * and its client read the same config file, and the value the server sends back to its own
 * client is the value already there. On a remote server there is no local logical server to
 * disagree with.
 *
 * <p><b>Nothing here ever touches stored XP.</b> Lowering the cap (turning the toggle off)
 * only changes where the derivation stops; {@link SkillManager} never lowers a stored total
 * that already sits above it, so turning the toggle back on restores every level.
 */
public final class LevelCap {
	/**
	 * Hard ceiling for {@code extendedMaxLevel}. Three digits is what every level readout
	 * (HUD bar, skills-screen row, toast) is laid out for, and the XP curve's total at this
	 * level (7,527,465) is comfortably inside an {@code int}.
	 */
	public static final int ABSOLUTE_MAX = 999;

	/** Smallest meaningful extended cap: anything at or below 100 is just the standard cap. */
	public static final int EXTENDED_MIN = Tuning.MAX_LEVEL + 1;

	private static volatile int current = Tuning.MAX_LEVEL;

	private LevelCap() {
	}

	/** The effective cap on this side right now. */
	public static int get() {
		return current;
	}

	/** The cap this JVM's own config asks for. */
	public static int fromConfig() {
		SpecialitiesConfig config = ConfigManager.get();
		return config.extendedLevels ? clamp(config.extendedMaxLevel) : Tuning.MAX_LEVEL;
	}

	/** Adopt this JVM's own config. Logical-server side, or a client with no server connected. */
	public static void applyConfig() {
		current = fromConfig();
	}

	/** A physical client adopting the connected server's cap. */
	public static void acceptFromServer(final int cap) {
		current = clamp(cap);
	}

	/**
	 * Join hook, called from every loader's join arm in {@link SkillEvents} BEFORE the defence
	 * passives are applied (they read the level). Re-asserts the server's own config and tells
	 * the client what it is.
	 */
	public static void onPlayerJoin(final ServerPlayer player) {
		applyConfig();
		Net.INSTANCE.sendLevelCap(player, current);
	}

	/**
	 * Re-apply after a config edit on a running logical server (the Mod Menu screen in a
	 * singleplayer or LAN world): adopt the new cap, resend it to everyone, and redo the one
	 * passive that is a stored attribute rather than a per-use read. Must run on the server
	 * thread.
	 */
	public static void applyConfigAndResync(final MinecraftServer server) {
		applyConfig();

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Net.INSTANCE.sendLevelCap(player, current);
			DefencePassives.apply(player);
		}
	}

	private static int clamp(final int cap) {
		return Math.max(Tuning.MAX_LEVEL, Math.min(ABSOLUTE_MAX, cap));
	}
}
