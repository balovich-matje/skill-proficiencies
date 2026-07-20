# Skill Proficiencies — Architecture

Skill Proficiencies (mod id and code namespace: `specialities`) is a Fabric mod (Minecraft 26.2) that adds an mcMMO-style skill
system: fifteen built-in skills that level from 0 to 100 through ordinary
vanilla play, each granting passive bonuses on the same activity that trains it.
This document is for contributors extending the mod — it explains how the pieces
fit and where to hook new behaviour.

Every skill is server-authoritative and per-player, and every bonus is designed
to stack *additively* with the matching vanilla enchantment rather than replace
or gate it.

---

## 1. Big picture

### Entry point

`com.specialities.Specialities` is the common `ModInitializer`. Its
`onInitialize()` runs, in order:

1. `ConfigManager.load()` — read `config/specialities.json`.
2. `SkillTypes.pullEntrypoints()` — collect externally-registered skills
   *before* any player state can exist.
3. `ModAttachments.initialize()` and `ModItems.initialize()`.
4. Register the two clientbound payloads (`SkillUpdatePayload`,
   `StealthStatePayload`).
5. `SkillEvents.register()` — wire the XP-gain and lifecycle event handlers.

`Specialities.MOD_ID` is `"specialities"` and `Specialities.id(path)` builds a
namespaced `Identifier`. `SpecialitiesClient` is the separate client
initializer (sources are split: common code in `src/main`, client-only code in
`src/client`).

### The skill engine

`SkillManager` (in `skills/`) is the **only** place skill XP or levels change.
Everything that awards XP calls one of:

- `SkillManager.addXp(ServerPlayer, SkillType, int)` — the universal award path.
  Applies the config XP-rate multiplier, clamps to the level-100 total, writes
  the updated attachment, and notifies the owning client.
- `SkillManager.addLevels(ServerPlayer, SkillType, int)` — used by the knowledge
  books; jumps whole levels (progress snaps to the level start).

Both funnel through the private `apply(...)`, which is the single write site: it
sets `ModAttachments.SKILLS`, re-applies `DefencePassives` if the skill is
`Skill.DEFENCE`, and sends a `SkillUpdatePayload`.

### Persistence: `ModAttachments.SKILLS`

Player progress lives in the `PlayerSkills` record, an immutable
`Map<String, Integer>` of **skill-id string → total accumulated XP**. Levels are
never stored — they are derived from total XP via `Tuning.levelForTotalXp`.
Keying by string (not enum ordinal) is deliberate: externally-contributed skills
persist through the identical path, and save data stays stable regardless of
skill order.

`ModAttachments.SKILLS` is a Fabric `AttachmentType<PlayerSkills>` with
`.persistent(PlayerSkills.CODEC)`, `.syncWith(..., targetOnly())` (owning client
only), and `.copyOnDeath()`; `SkillManager.get(player)` reads it, defaulting to
`PlayerSkills.EMPTY`. The same file declares the transient attachments used by
individual features: `RICOCHET_BOUNCES` / `RICOCHET_IGNORE` (arrows),
`STEALTH_CRIT_DONE` (mobs), `BREWING_OWNER` (a brewing-stand block entity).

### Sync: `SkillUpdatePayload`

`SkillUpdatePayload(skillId, fromTotalXp, totalXp, fromLevel, level)` is sent
server→client on every XP change. It carries the *previous* state so the client
can animate the HUD bar and render an "Increased x → y" toast (`levelUp()`
reports whether a boundary was crossed). Attachment sync keeps the client's full
`PlayerSkills` current; the payload is the event stream layered on top.

### Balance: `Tuning`

`skills/Tuning` is the single source of truth for every balance number — the XP
curve, all bonus formulas, and named constants. The XP curve is:

- `xpToNext(level)` = `50 + 15 * level`
- `totalXpForLevel(level)` and `levelForTotalXp(totalXp)` are the closed-form /
  loop inverses used everywhere.

A handful of knobs (`combatDamageMaxBonus`, `attackSpeedMaxReduction`,
`miningSpeedMaxBonus`, `luckLevelsPerBonus`) are read live from
`ConfigManager.get()` inside `Tuning` (the fifth knob, `xpRateMultiplier`, is
applied in `SkillManager.addXp`), so player config edits take effect without
touching the formulas. The skills screen reads the same `Tuning`
methods, so displayed numbers always match actual behaviour.

---

## 2. How activity becomes XP

### Routing: `SkillCategories`

`skills/SkillCategories` maps game facts to skills, using vanilla tags wherever
possible so modded blocks/tools are covered automatically:

- `breakSpeedSkill(state)` — `MINEABLE_WITH_PICKAXE/AXE/HOE/SHOVEL` →
  mining / woodcutting / harvesting / excavation (drives the break-speed bonus).
- `toolSkill(stack)` / `toolMatches(skill, stack)` — which skill a held item
  belongs to, and whether an item is the right tool for a skill's bonus.
- `fortuneSkill(state)` — which skill's passive Fortune applies to a block's
  loot.
- `blockBreakXp(player, state)` + `blockBreakSkill(state)` — the XP table for
  breaking a block. These two must stay mirrored. Only mature crops grant crop
  XP; ore XP requires the correct tool for drops.
- `specializationSkill(attacker, source)` — routes combat XP: an
  `AbstractArrow` whose `getWeaponItem()` is in `ModTags.RANGED_WEAPONS` →
  `ARCHERY`; melee hits with a `MELEE_WEAPONS` mainhand → `ARMS_MASTERY`.
  This is the loose test, and XP is all it is allowed to decide.
- `isMeleeSwing` / `isRangedWeaponShot` / `isThrownMeleeWeapon` — the strict
  tests, for damage *bonuses*. A `DamageSource` cannot tell a swing from a
  damage-over-time or a thorns reflect (both name the player as the causing
  AND the direct entity), so `isMeleeSwing` asks `MeleeSwing` whether the
  player is actually inside `Player.attack` right now. The combat multiplier
  takes all three; the sneaking stealth crit takes the first two, since a
  returning trident is a weapon attack but not a stealth opener.

### Event-driven XP: `SkillEvents`

`SkillEvents.register()` attaches the Fabric callbacks that award XP without a
mixin:

- `PlayerBlockBreakEvents.AFTER` → block-break XP (mining/woodcutting/harvesting/
  excavation).
- `ServerLivingEntityEvents.AFTER_DAMAGE` → attacker combat XP (combat + the
  specialization) and victim XP (defence, or acrobatics for fall damage). Rates
  come from `Tuning` (creepers pay a higher defence rate).
- `ServerLivingEntityEvents.ALLOW_DAMAGE` → cancels fall damage entirely once
  acrobatics + Feather Falling protection reaches `FALL_IMMUNITY_POINTS` (25).
- `ServerPlayerEvents.JOIN` / `AFTER_RESPAWN` → re-apply `DefencePassives`.
- `ServerTickEvents.END_SERVER_TICK` → drive `AthleticsTicker` and
  `SneakingTicker`; `LEAVE` clears their per-player maps.

### The mixin map

Mixins are used only where no event exists, or where a vanilla value has to be
modified in place. Server/common mixins live in `com.specialities.mixin`
(`specialities.mixins.json`); client mixins in `com.specialities.client.mixin`
(`specialities.client.mixins.json`). MixinExtras annotations
(`@ModifyReturnValue`, `@ModifyExpressionValue`, `@WrapOperation`, `@Local`
sugar) are used throughout.

| Mixin | Target | Hook | Purpose |
| --- | --- | --- | --- |
| `PlayerMixin` | `Player` | `getDestroySpeed` (return) | Proficiency: matching-tool break-speed multiplier |
| | | `getCurrentItemAttackStrengthDelay` (return) | Arms-mastery faster attack recovery |
| | | `doSweepAttack` (`getAttributeValue` expr) | Passive sweeping edge, recomputed as effective level |
| | | `onEnchantmentPerformed` (arg) | Enchanting XP-cost discount |
| | | `causeFoodExhaustion` (arg) | Athletics reduced sprint hunger |
| | | `attack` (wrap) | Opens/closes `MeleeSwing` so a real swing is distinguishable from any damage source that merely names the player |
| `BlockMixin` | `Block` | `getDrops` (tool arg + return) | Passive Fortune (boosted tool copy) + manual log-drop bonus |
| `LivingEntityMixin` | `LivingEntity` | `hurtServer` (damage arg, ×3) | Combat damage multiplier, fall-protection uncap, stealth crit |
| | | `getVisibilityPercent` (return) | Sneaking detection reduction |
| `EnchantmentHelperMixin` | `EnchantmentHelper` | `getEnchantmentLevel` (return) | Passive Looting from combat |
| | | `getFishingLuckBonus` / `getFishingTimeReduction` (return) | Fishing Luck of the Sea / Lure |
| | | `getDamageProtection` (return) | Acrobatics fall-protection points |
| `BowItemMixin` | `BowItem` | `releaseUsing` (`getPowerForTime`) | Archery faster bow draw |
| `CrossbowItemMixin` | `CrossbowItem` | `getChargeDuration` (return) | Archery faster crossbow charge (stacks with Quick Charge) |
| `AbstractArrowMixin` (+ `AbstractArrowAccessor`) | `AbstractArrow` | `onHitEntity` (head + tail) | Archery ricochet chaining to nearby hostiles |
| `FishingHookMixin` | `FishingHook` | `retrieve` (head) | Fishing XP on a successful reel-in |
| `ServerPlayerMixin` | `ServerPlayer` | `jumpFromGround` (tail) | Athletics jump XP |
| `ResultSlotMixin` | `ResultSlot` | `onTake` (head) | Smithing XP + resourcefulness on craft |
| `FurnaceResultSlotMixin` | `FurnaceResultSlot` | `onTake` (head) | Smithing smelt multicraft on withdrawal |
| `BrewingStandMenuMixin` | `BrewingStandMenu` | `<init>` (tail) | Record the brewing-stand's last opener |
| `BrewingStandBlockEntityMixin` | `BrewingStandBlockEntity` | `doBrew` (`shrink`) | Alchemy XP + ingredient-return on each brew |
| `EnchantedItemTriggerMixin` | `EnchantedItemTrigger` | `trigger` (head) | Enchanting XP + luck (fires once per table enchant) |

Client-side: `AbstractClientPlayerMixin` divides the athletics speed modifier
back out of `getFieldOfViewModifier` (so the sprint bonus doesn't zoom the FOV);
`UseDurationMixin` scales the bow's pull *animation* to match the gameplay draw
speed; `AbstractContainerScreenAccessor` exposes `leftPos`/`topPos`/`imageWidth`.

The **artisan skills** (smithing, alchemy, enchanting) are the mixin-heavy ones
because crafting has no event surface. Shared helpers live in `skills/Artisan`,
and `skills/MaterialValues` holds the smithing XP-per-material table. Note the
deliberate seams: smithing hooks `ResultSlot.onTake` at HEAD so the grid still
holds the ingredients; smelt multicraft fires on withdrawal, so hopper
extraction is excluded; alchemy attributes a brew to the stand's last human
opener via the `BREWING_OWNER` attachment.

---

## 3. How bonuses apply

There are two mechanisms, chosen by whether the bonus is a persistent stat or a
per-action modifier.

**Attribute-based passives** — expressed as vanilla `AttributeModifier`s:

- `DefencePassives` applies **permanent** modifiers (`+2 max health` per 10
  levels, `+1 armor toughness` per 25) and is re-applied on join, respawn, and
  every defence-level change (the `SkillManager.apply` special-case). Extra max
  health renders for free — vanilla stacks heart rows automatically.
- `AthleticsTicker` manages a **transient** movement-speed modifier per tick,
  only while sprinting, capped so potions + skill never exceed `SPRINT_SPEED_CAP`.

**Mixin-based multipliers** — everything else modifies a vanilla value at the
point it is read (see the mixin map).

### The additive-with-enchantments rule

The governing design rule: a skill bonus is expressed in the *same units* as the
matching enchantment and added to the vanilla value at the same read site, so the
two stack additively instead of overriding each other. Concretely:

- Passive Fortune: `BlockMixin` hands the loot roll a **copy of the tool** with
  `luckBonus` extra Fortune levels layered onto its real enchantment, so vanilla
  `ApplyBonusCount` sees `enchant + skill`.
- Passive Looting: `EnchantmentHelperMixin` adds `luckBonus` to the return of
  `getEnchantmentLevel(LOOTING, entity)`.
- Passive sweeping edge: `PlayerMixin` recomputes the sweep-ratio attribute as
  if the effective Sweeping Edge level were `enchant + bonus`.
- Fishing Luck/Lure and acrobatics fall protection likewise add onto the
  enchantment-derived value.

`Tuning.luckBonus(level)` (levels-per-bonus from config) is the shared "free
enchant levels" helper. So a maxed skill and a maxed enchant genuinely stack,
and — per the vanilla-plus philosophy — no item or enchant is ever *required* to
train or benefit from a skill.

---

## 4. The external-skill API

Another mod can register its own skills and have the engine treat them exactly
like the built-ins: same XP curve, same string-keyed persistence, same HUD bar,
level-up toast, and skills-screen row. The public surface is the three types in
`com.specialities.api` (plus `SkillManager.addXp` as the award path).

### Registering

Declare the `specialities:skills` entrypoint in your `fabric.mod.json`:

```json
"entrypoints": { "specialities:skills": ["com.yourmod.YourSkills"] }
```

Implement `SkillsEntrypoint`:

```java
public final class YourSkills implements SkillsEntrypoint {
    public void registerSkills(SkillRegistrar registrar) {
        registrar.register(new YourSkill());
    }
}
```

`registerSkills` is called during Specialities' common init, on **both** server
and client — register the same skills in the same order on both sides, so the
persisted ids and the on-screen order stay consistent. `SkillRegistrar.register`
validates ids: they must be lowercase and unique across all mods, or it throws
at init (see `SkillTypes.pullEntrypoints`, which also logs each registration).

### Implementing `SkillType`

```java
public interface SkillType {
    String id();                          // stable lowercase; the persistence + wire key
    int color();                          // ARGB accent for HUD bar, toast, screen row
    Item icon();                          // item-rendered contexts (the toast)
    Identifier iconTexture();             // a FLAT item texture in the item atlas,
                                          // e.g. yourmod:item/your_item
    default Component displayName();      // defaults to translatable("skill.specialities." + id())
    default List<Component> screenLines(int level);  // bonus lines for the screen hover tooltip
}
```

`iconTexture()` must be a flat *item* texture: the HUD draws icons translucent,
which 3D block models cannot do (this is why the built-in `SMITHING` borrows the
iron-ingot texture rather than the anvil block).

### What the engine provides vs. what you supply

The engine gives you, for free:

- The XP curve and level math (`Tuning`).
- Persistence and owner-only sync (your id becomes a key in `PlayerSkills`).
- The HUD XP bar, the level-up toast, and a row on the skills screen — the
  screen iterates `SkillTypes.all()` (built-ins first, externals in mod-load
  order); the bar and toast resolve your id via `SkillTypes.byId`.

You supply:

- **XP awards.** Call `SkillManager.addXp(serverPlayer, yourSkillType, amount)`
  from wherever your activity happens. The engine never guesses what trains your
  skill.
- **Bonuses.** The engine renders and persists a level; applying an effect at
  that level is your mod's job (an attribute modifier, your own mixin, etc.).
- **Two lang keys**, because the engine renders text it cannot invent:
  - `skill.specialities.<id>` — the display name (this is the default
    `displayName()` key; note the fixed `specialities` namespace. Lang keys are a
    global flat namespace, so define it in your own lang file, or override
    `displayName()` to point at a key you own).
  - `screen.specialities.skills.source.<id>` — the XP-source text shown under
    the engine's "EXP gained from:" header when the skill's row is expanded on
    the skills screen.
- **Hover bonus lines** via `screenLines(level)` — the skills-screen tooltip
  calls this for non-built-in skills (built-ins are hard-coded in
  `SkillsScreen.bonusLines`).

One caveat worth knowing: the always-visible HUD bar picks a skill to show from
the *held tool* via `SkillCategories.toolSkill`, which only knows the built-ins.
An external skill therefore appears on the HUD bar right after it gains XP (via
the `SkillUpdatePayload` animation) but not merely from holding a related item.

---

## 5. Client UI map

All client rendering is registered in `SpecialitiesClient.onInitializeClient()`.

- **`SkillXpHudBar`** — an always-visible bar (once any skill has gained XP this
  session) in the vanilla XP bar's old slot (`guiHeight - 29`, 182×5). It shows
  the held-tool skill, or a recently-gained skill during/after a gain (with a
  linger); on each gain two tool icons converge into the bar and the fill grows.
  Icons are item-atlas sprites (`SkillIcons`, via `AtlasIds.ITEMS`) drawn with
  `blitSprite(..., argb)` so they can be translucent — plain item rendering
  ignores alpha. `SkillHudState` holds the animation state, fed by the
  `SkillUpdatePayload` receiver.
- **`SkillLevelUpToast`** — shown when `SkillUpdatePayload.levelUp()`: skill name
  plus "Increased x → y". It plays the challenge-complete jingle only when the
  level-up crosses 50 or 100, otherwise the default quiet toast whoosh.
- **`SkillsScreen`** — lists every `SkillTypes.all()` skill, greyed until
  discovered, with an expand arrow (source panel from the lang key) and a hover
  tooltip of current bonuses. It opens from a widget injected into the inventory:
  a `BookmarkTab` on the survival inventory's top edge and a square "S" `Button`
  on the creative inventory (creative redirects `InventoryScreen` to
  `CreativeModeInventoryScreen`, so both are handled). Both are re-anchored every
  screen tick through `AbstractContainerScreenAccessor`, because the recipe book
  shifts `leftPos` without re-running `init`.
- **`StealthVignette`** — driven by `StealthStatePayload` (NONE/HIDDEN/DETECTED):
  a dark violet edge tint while sneaking undetected near hostiles, and a light
  flash fading over ~2s when a hostile spots you. Server logic is in
  `SneakingTicker`.
- **`BookmarkTab`** — a container-styled clickable built on `AbstractWidget`
  directly (not `AbstractButton`, whose render pass is finalized around the
  vanilla button sprite); it overrides `extractWidgetRenderState`, `onClick`
  (calling `playDownSound` itself), and `updateWidgetNarration`.

**`HUD_SHIFT`** is a published constant (`= 7`). To make room for the skill bar,
`SpecialitiesClient` raises the vanilla bottom-HUD elements (info bar, XP level,
health, armor, food, air, mount health) by `HUD_SHIFT` pixels via
`HudElementRegistry.replaceElement`, and attaches the skill bar after
`VanillaHudElements.HOTBAR` (not the experience-level element, which vanilla
skips at 0 XP). It is a stable value other HUD-adjacent mods can align to.

---

## 6. How-to: adding a 16th built-in skill

A worked checklist. (An external skill uses the API in §4 instead; this is for a
first-party skill wired directly into the engine.)

1. **Enum.** Add a constant to `skills/Skill` — `id`, ARGB `color`, icon `Item`
   supplier — and add a `case` to the `iconTexture()` switch with a flat item
   texture (`Identifier.withDefaultNamespace("item/...")`).
2. **Routing.** Teach `SkillCategories` where the XP and bonuses come from:
   the relevant methods among `breakSpeedSkill`, `toolSkill`, `toolMatches`,
   `fortuneSkill`, `blockBreakXp` + `blockBreakSkill`, or `specializationSkill`.
   Then award XP from the right place — a new `SkillEvents` callback, or a new
   mixin if the activity has no event.
3. **Tuning.** Add constants and bonus-formula methods to `skills/Tuning`. If a
   knob should be player-editable, add a field to `config/SpecialitiesConfig`
   (with a `sanitize()` clamp), an entry in `client/config/ClothConfigScreen`,
   and the `config.specialities.*` lang keys.
4. **Apply the bonus.** Either an attribute passive (model it on
   `DefencePassives` — and remember `SkillManager.apply` only re-applies
   `DefencePassives` for `DEFENCE`, so a new permanent-attribute skill needs its
   own re-apply branch and join/respawn wiring) or a mixin multiplier following
   the additive-with-enchantments rule (§3). Register any new mixin in
   `specialities.mixins.json` (or `specialities.client.mixins.json`).
5. **Skills-screen tooltip.** Add a `case` for the new skill to
   `SkillsScreen.bonusLines`, and the `tooltip.specialities.*` lang keys it uses.
6. **Lang.** Add `skill.specialities.<id>` (display name) and
   `screen.specialities.skills.source.<id>` (the XP-source panel text) to
   `assets/specialities/lang/en_us.json`.
7. **Knowledge books.** In `ModItems`, add two `registerBook(Skill.X, 25)` /
   `(…, 100)` fields and two matching `output.accept(...)` calls in the creative
   tab. Add the two item-model JSONs under `assets/specialities/items/`
   (`<id>-knowledge-25.json` etc.) and the two `item.specialities.*` lang
   entries. Ids use hyphens (`Skill.id().replace('_', '-')`).

Nothing else references the skill list by count, so those seven touchpoints are
the whole surface. `SkillTypes` picks up the new enum constant automatically.
