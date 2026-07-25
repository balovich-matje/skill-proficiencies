package com.specialities.platform;

import com.specialities.skills.PlayerSkills;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
//? if >=1.20.5 {
//?} else {
/*import net.minecraft.world.item.ItemStack;
*///?}
import net.minecraft.world.level.block.entity.BlockEntity;
// jspecify is one of the game's OWN libraries only from 1.21.11 up (conventions
// §5e-bis); below that it is absent and org.jetbrains:annotations 26.0.2 (on the
// compile classpath via fabric-loader) supplies a @Nullable that is @Target(TYPE_USE)
// as well, so nothing but the import forks.
//? if >=1.21.11 {
import org.jspecify.annotations.Nullable;
//?} else {
/*import org.jetbrains.annotations.Nullable;
*///?}

/**
 * Seam 1 of three (design {@code docs/MULTIVERSION.md} §2) — attached state:
 * the persistent, owner-synced player skill data plus the four transient
 * bookkeeping values the passives hang off entities and block entities.
 *
 * <p>Why this is a seam and not a {@code //?} block: the Fabric attachment API
 * itself changes shape below 1.21.1 ({@code AttachmentType.syncWith} and
 * {@code AttachmentSyncPredicate} are absent from fabric-api 0.92.11, as is the
 * {@code AttachmentRegistry.create(Identifier, Consumer)} overload), and
 * NeoForge/Forge have no attachment API at all — NeoForge has its own
 * {@code AttachmentType} registry, Forge 1.20.1 has capabilities with no codec,
 * no copy-on-death and no sync. Nine call sites across six files cast to
 * {@code AttachmentTarget} today; behind this interface they cast to nothing.
 *
 * <p>Note that only the *storage* moves. The balance logic that reads and writes
 * these values stays exactly where it was, shared by every node.
 */
public interface SkillStore {
	SkillStore INSTANCE = new FabricSkillStore();

	/**
	 * Registers whatever the platform needs before any world can load —
	 * attachment types on Fabric, and on Phase B's loaders the equivalent
	 * registry/event wiring. Called once from common init, at the exact point
	 * {@code ModAttachments.initialize()} used to occupy: after the external
	 * skill entrypoints have been pulled, before item registration.
	 */
	void initialize();

	// --- player skills: persistent, synced to the owning client, survives death ---

	/** Never null; a player who has never gained XP reads as {@link PlayerSkills#EMPTY}. */
	PlayerSkills getSkills(Player player);

	void setSkills(Player player, PlayerSkills skills);

	/**
	 * Pushes the player's whole skill state to their client.
	 *
	 * <p><b>No-op wherever the platform syncs the attachment itself</b>, which is
	 * every node registered today. It exists for design R-03: Fabric 1.20.1 has
	 * no attachment sync and Forge has none at all, so on those nodes this is the
	 * only way the client ever learns the state it renders, and it is mandatory.
	 * Called on player join.
	 */
	void resyncSkills(ServerPlayer player);

	// --- transient bookkeeping ---

	/**
	 * Remaining ricochets, or {@code null} if this arrow is not a ricochet arrow
	 * at all. The two states are NOT interchangeable: absent means "an original
	 * shot, decide from the firing weapon and the archery level", while zero means
	 * "a bounce arrow that has run out". That is why this returns a boxed
	 * {@code Integer} and not the {@code int} §2 sketches.
	 */
	@Nullable Integer getRicochetBounces(Entity arrow);

	void setRicochetBounces(Entity arrow, int bounces);

	/** Entity id a ricochet arrow flew away from and must pass through, or {@code null}. */
	@Nullable Integer getRicochetIgnore(Entity arrow);

	void setRicochetIgnore(Entity arrow, int entityId);

	/** True once this mob has been stealth-critted — each enemy only falls for it once. */
	boolean isStealthCritDone(Mob mob);

	void markStealthCritDone(Mob mob);

	/** UUID string of the player who last opened this brewing stand, for alchemy attribution. */
	@Nullable String getBrewingOwner(BlockEntity stand);

	void setBrewingOwner(BlockEntity stand, String uuid);

	// --- the fifth transient pair: a single-node member, and deliberately so ---
	//
	// `AbstractArrow.getWeaponItem()ItemStack` does not exist below 1.21.2 (design R-04:
	// "ABSENT ANYWHERE in 1.20.1"), and three balance tests in SkillCategories are built
	// on it — archery-vs-arms-mastery XP routing, the ranged half of the combat damage
	// multiplier, and the thrown-melee-weapon case. On that node the firing weapon is
	// stamped onto the projectile at the moment it is spawned (BowItemMixin,
	// CrossbowItemMixin) and read back here at damage time, which keeps all three tests
	// semantically identical instead of approximating them.
	//
	// Gated rather than declared for every node on purpose: a method no other node can
	// call is the speculative surface design §2 sets out to avoid (§2.4 deviations 5
	// and 7), and declaring it unconditionally would add an attachment id to worlds
	// that never use it. A Phase B loader below 1.21.2 implements this pair; every
	// other one does not see it.
	// A disabled branch may not carry `/** … */` javadoc — the `*/` would close the
	// branch comment early (conventions §5e-ter), so these two are documented with
	// line comments instead.
	//? if >=1.20.5 {
	//?} else {
	/*// The weapon a projectile was fired from, or null for a projectile nothing stamped
	// (a dispenser shot, a skeleton's arrow, a thrown trident — SkillCategories answers
	// that last case from the projectile's own pickup item instead).
	@Nullable ItemStack getFiringWeapon(Entity projectile);

	// Stamps the firing weapon onto a freshly spawned projectile. Stores a copy.
	void setFiringWeapon(Entity projectile, ItemStack weapon);
	*///?}
}
