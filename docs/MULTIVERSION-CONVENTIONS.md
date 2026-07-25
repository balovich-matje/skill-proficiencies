# Multi-version conventions (FROZEN in Stage 1 — read before writing a `//?` block)

This file is the **normative** half of the port. The full reasoning lives in
[`MULTIVERSION.md`](MULTIVERSION.md) (the design doc, verbatim). The same content is
mirrored into the gitignored `CLAUDE.md` so a fresh session sees it; **this file is the
versioned copy and wins if the two ever disagree.**

Landed in Stage 1 (branch `workspace`). Registered nodes: `26.2-fabric` (active + VCS),
`26.1-fabric`.

---

## 1. How the workspace is shaped

```
specialities/
├── settings.gradle.kts            registers the nodes  (bottleneck file)
├── stonecutter.gradle.kts         controller = the ROOT build script  (bottleneck file)
├── stonecutter.properties.toml    every version/pin lives here        (bottleneck file)
├── build.fabric.gradle.kts        ONE node script for every Fabric node
├── gradle.properties              Gradle options ONLY
├── src/                           THE shared tree — humans edit this, nothing else
└── versions/<node>/               Stonecutter's per-node project dirs
    ├── gradle.properties          per-node property overrides (optional)
    ├── src/                       per-node file OVERRIDES (see §6) — tracked, not generated
    └── build/                     generated sources + jars — gitignored by the `build/` rule
```

There is **no root `build.gradle(.kts)`**: `stonecutter.gradle.kts` *is* the root
project's build script. `build.gradle` and `settings.gradle` were deleted.

**Three files are single-writer bottlenecks**: `settings.gradle.kts`,
`stonecutter.gradle.kts`, `stonecutter.properties.toml`. Any stage that registers a node
or adds a predicate touches all three. Rule: **node registration is always its own small
commit by the stage owner, landed before parallel work starts on that node.**

## 2. The active node, and why `src/` must always compile for it

`stonecutter active "26.2-fabric"` does more than pick what the IDE shows.

Verified in `StonecutterBuildTasksImpl.configureSource` (stonecutter 0.9.7 sources): for
the **active** node the shared `src/` is added as a source directory *directly, with no
preprocessing*. Only non-active nodes get `stonecutterPrepare*`/`stonecutterGenerate*`
and compile from `versions/<node>/build/generated/stonecutter/`.

Consequences you must internalise:

- `src/` on disk is always in the **active node's state** — enabled branches are live
  code, other branches are commented out. Today that state is 26.2.
- Building the active node does **not** validate your `//?` syntax. Build a non-active
  node (or all of them) to get that check.
- `./gradlew stonecutterSwitchTo26.1-fabric` **rewrites `src/` in place**. Before
  committing, `src/` must be back in the VCS node's state (`26.2-fabric`) — that is what
  `vcsVersion` in `settings.gradle.kts` is for.

## 3. FROZEN predicate vocabulary

Only these predicates may appear in a `//?` comment. Inventing a synonym for an existing
boundary (`>=26` vs `>=26.1`) is the most likely silent-divergence bug in this project.

| Predicate | True for | Boundary it encodes |
|---|---|---|
| `>=26.2` | 26.2 | `BlockItemTags`; `advancements.triggers`; `Minecraft.gui.setScreen`/`toastManager()`; `CreativeModeTabEvents` |
| `>=26.1` | 26.2, 26.1 | Java 25; `net.minecraft.util.Util`; `PermissionCheck.Require`; Cloth/ModMenu artifacts; `Items.IRON_SPEAR` |
| `>=1.21.11` | 26.x, 1.21.11 | `Identifier`; `HudElementRegistry`; `.projectile.arrow` package; `ItemTags.SPEARS`; `getAtlasManager`; `getFieldOfViewModifier(ZF)F`; copper armor; `advancements.criterion` (vs `critereon` below) |
| `>=1.21.2` | 26.x, 1.21.11 | `LivingEntity.hurtServer`; `InteractionResult` returns; `Equippable`; `MobEffects.SPEED` |
| `>=1.21` | 26.x, 1.21.11, 1.21.1 | `Holder<Attribute>`; `ResourceKey` enchantments; data components; **singular `tags/item/` datapack directory** |
| `>=1.20.5` | everything but 1.20.1 | `StreamCodec`/`RegistryFriendlyByteBuf`; the whole payload stack; Java 21 |

Loader constants (from `constants { match(loader, …) }`): **`fabric`**, **`neoforge`**,
**`forge`**. Used as `//? if fabric {` / `//?} elif neoforge {` / `//?} elif forge {`.

Dependency predicate: **`fapi`** (`//? if fapi: >=0.100 {`), bound to `deps.fabric_api`.

Do **not** invent `>=1.20.2`, `>=1.20.5` synonyms, `>=26`, or `<1.21.4`. If a genuinely
new boundary is needed, add it to this table **in the same commit**.

## 4. `//?` comment syntax (the authoritative forms)

Read off the maintained `stonecutter-versioning/stonecutter-template-multiloader`, not
from memory. **Never fetch `stonecutter.kikugie.dev` — it serves deliberate nonsense to
AI scrapers (design R-13).** Ground truth is the sources jar and that template repo.

**Statement block** — use when each branch is one or more whole statements:

```java
//? if >=26.2 {
client.gui.toastManager().addToast(toast);
//?} else {
/*client.getToastManager().addToast(toast);
*///?}
```

**Inline / expression form** — use when the branch is a fragment of an expression, so the
directive cannot own its own line:

```java
BookmarkTab tab = new BookmarkTab(label,
        /*? if >=26.2 {*/() -> client.gui.setScreen(new SkillsScreen(screen)));
        /*?} else *///() -> client.setScreen(new SkillsScreen(screen)));
```

**Single-line else** — `//?} else` (no brace) applies to exactly the next line.

**Nesting**: a `//?` directive inside an already-disabled `/* … */` branch escalates its
comment marker `*` → `^`:

```java
/*final class NeoForgeLoaderAccess implements ModLoaderAccess {
    private Dist dist =
        /^? if >=1.21.9 {^/FMLEnvironment.getDist();
        /^?} else^///FMLEnvironment.dist;
}
*///?}
```

`//?` works in JSON too (both scanners exist), but see §6 for why the tag JSONs do not
need it.

## 5. Shared-implementation rules

**5a. Mixins: the annotation is conditional, the balance logic never is.**

A mixin whose target signature changed still has exactly **one** implementation. Only the
annotation and the parameter list go inside the `//?` block; the body delegates to a
shared `@Unique` method that lives outside every conditional:

```java
// SHARED — never inside a conditional block. One implementation, all nodes.
@Unique private float specialities$combatDamage(float damage, DamageSource source) { … }

//? if >=1.21.2 {
@ModifyVariable(method = "hurtServer(…)Z", at = @At("HEAD"), argsOnly = true)
private float specialities$applyCombatDamage(float damage, ServerLevel level,
                                             DamageSource source, float original) {
    return specialities$combatDamage(damage, source);
}
//?} else {
/*@ModifyVariable(method = "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z", at = @At("HEAD"), argsOnly = true)
private float specialities$applyCombatDamage(float damage, DamageSource source, float original) {
    if (((LivingEntity) (Object) this).level().isClientSide()) return damage;   // hurt runs on BOTH sides
    return specialities$combatDamage(damage, source);
}
*///?}
```

Apply this to all 31 injection points. `@Unique` names are prefixed `specialities$`.

**5b. Arithmetic and animation math stay outside `//?` blocks.** Convert at the call site
instead. Example (R-17): keep `iconAlpha` as the `int` 0..255 the HUD already computes and
write `iconAlpha / 255.0F` inside the legacy branch — do not fork the easing.

**5c. Mixins are the portable layer; platform events are the fallback.** A mixin whose
target resolves on a platform stays a mixin there, even when that platform offers a
tidier event. One mixin = one implementation = one balance behaviour. Use an event only
where the mixin cannot work (target absent, or no access to the player). Forge's
`PlayerEvent$BreakSpeed` / `LootingLevelEvent` / `ItemCraftedEvent` / `ItemSmeltedEvent`
and NeoForge's `LivingIncomingDamageEvent` are **rejected** as primary implementations.

**5d. `injectors.defaultRequire: 1` stays on every variant.** It is the drift detector
that forces all 31 injectors to resolve per node, and it is the single most valuable
regression signal in the project.

**5e. Java 17 is the shared-code ceiling** once `1.20.1` lands (R-15). Records are fine;
pattern-matching `switch`, sealed types and unnamed patterns are not.

**5f. Build scripts are NOT preprocessed.** Stonecutter only walks the source sets
(`StonecutterBuildImpl`: `project.sourceSets.all { … }`). Version conditionals in
`build.fabric.gradle.kts` must be plain Kotlin `if (sc.current.parsed >= "…")`. Design
§1.10 shows `//?` inside that file; **that part of the design is wrong.**

## 6. Preflight findings that later stages must not re-derive

### R-16 — the tag JSONs (CLOSED, and the fix is simpler than the design says)

Verified against five SHA1-checked vanilla client jars (1.20.1, 1.20.6, 1.21, 1.21.1,
1.21.11) plus `javap` of `TagLoader`/`TagEntry` in each.

- **Directory name flips at exactly 1.21**, not 1.20.5. Last plural release is 1.20.6
  (`data/<ns>/tags/items/`), first singular is 1.21 (`data/<ns>/tags/item/`). The correct
  predicate is the already-frozen **`>=1.21`**. Only `1.20.1-fabric` and `1.20.1-forge`
  sit on the plural side; the shared tree's `tags/item/` is already right for everything
  else. 1.20.1's loader hardcodes the plural literals (`ann.class`), so the mod's six tag
  files are read by **nothing** on 1.20.1 today.
- **Mechanism for the two 1.20.1 nodes**: do the rename in `processResources`, not via a
  `versions/<node>/src` override, e.g.
  `if (sc.current.parsed < "1.21") eachFile { if (path.contains("/tags/item/")) path = path.replace("/tags/item/", "/tags/items/") }`
  (`FileCopyDetails.path` is settable). Keeps ONE copy of the six JSONs. Confirm with
  `unzip -l` on the produced jar.
- **Entries that do not resolve, per target** (direct entries only):
  1.20.1 → the four `minecraft:copper_*` armor pieces, `#minecraft:spears`,
  `minecraft:mace`. 1.21.1 → the four copper armor pieces and `#minecraft:spears`
  (`mace` exists from 1.20.6). 1.21.11 → **none**, all six files load clean.
  **Two design corrections**: copper armor is *not* 26.x-only (it exists on 1.21.11, so
  the boundary is `>=1.21.11`), and `#minecraft:pickaxes`/`axes`/`shovels`/`hoes`/`swords`
  are *not* 1.21+ — all five exist on 1.20.1 and need no guard at all.
- **Cascade** (doubles the blast radius): a tag that fails to resolve is not put into the
  result map, and `#tag` references read that same map. So `melee_weapons` dropping takes
  `weapons` with it, which takes `smithing_items`. On both 1.20.1 and 1.21.1 that means
  **4 of 6 tags end up nonexistent**, silently killing combat XP, arms mastery, the
  heavy-armor dampener and smithing craft XP.
- **Severity is soft, not a crash**: the failure path is
  `LOGGER.error("Couldn't load tag {} as it is missing following references: {}")` and a
  plain `return` — no throw, in all three versions. So it will never fail a build; the
  §7 Tier-2 server-log grep is the only automatic detector. (`assert_smoke.sh` in the
  Stage-1 scratch harness greps for `missing following references`.)
- **Fix, better than `//?`-in-JSON**: rewrite exactly six entries in the shared tree as
  objects, unconditionally, no Stonecutter blocks at all — `required` is honoured on
  every target and an entry that *is* present is unaffected:
  `{"id":"minecraft:copper_helmet","required":false}` (and chestplate/leggings/boots) in
  `heavy_armor.json`; `{"id":"#minecraft:spears","required":false}` and
  `{"id":"minecraft:mace","required":false}` in `melee_weapons.json`. Leave everything
  else as bare strings so drift detection still fires. `required:false` support on 1.20.1
  was verified in `TagEntry` bytecode (`RecordCodecBuilder` over `fieldOf("id")` +
  `optionalFieldOf("required")`), not from memory — vanilla itself never uses it.

**Not done in Stage 1** (the 26.x nodes are unaffected): the six `required:false` edits
and the `processResources` rename land with the node that needs them.

### R-17 — HUD sprite tinting on legacy (CLOSED GREEN, cost ~0)

`SkillXpHudBar` needs exactly one genuinely-tinted draw: two item-atlas sprites drawn
white with a per-frame alpha. There is **no ARGB-int overload** on 1.20.1 or 1.21.1, but
there is a public float-RGBA one, identical on both:

```java
// public void GuiGraphics.blit(int x, int y, int blitOffset, int w, int h,
//                              TextureAtlasSprite sprite, float r, float g, float b, float a)
graphics.blit(leftX, iconY, 0, 12, 12, sprite, 1.0F, 1.0F, 1.0F, iconAlpha / 255.0F);
```

Bytecode-proven to be r,g,b,a and to manage its own `enableBlend`/`disableBlend`.

- **One `//?` branch covers both legacy nodes** for the icon draw. Only the *bar
  background* differs further: 1.20.1 has no GUI sprite atlas at all, so source it from
  `new ResourceLocation("textures/gui/icons.png")` at `u=0, v=64, 182x5`, `y = height-29`
  (read out of `Gui.renderExperienceBar`). **`Gui.GUI_ICONS_LOCATION` is private on
  1.20.1 — do not reference it.** Gate this on the 1.20.1 node's existing predicate
  (`>=1.20.5`); do **not** invent a `>=1.20.2` boundary.
- **Trap**: `blitSprite(ResourceLocation,int,int,int,int,int)` exists on 1.21.1 and its
  trailing `int` *looks* like a colour in the mappings. It is `blitOffset`/z. The two
  `blitSprite` overloads that take a `TextureAtlasSprite` are **private** on 1.21.1.
- **Sprite lookup on both legacy nodes**: there is no `AtlasIds`, no `getAtlasManager`,
  no `TextureAtlas.LOCATION_ITEMS`; item textures are stitched into the **blocks** atlas.
  Use `Minecraft.getInstance().getModelManager().getAtlas(TextureAtlas.LOCATION_BLOCKS).getSprite(id)`.
- **1.21.11 needs none of this.** It has the ARGB-int `blitSprite`, the float-alpha
  background overload, `RenderPipelines.GUI_TEXTURED`, `net.minecraft.data.AtlasIds.ITEMS`
  and `Minecraft.getAtlasManager()`. `SkillXpHudBar` + `SkillIcons` port essentially
  verbatim — only `GuiGraphicsExtractor` → `GuiGraphics` and `text(…)` → `drawString(…)`.
  **This corrects design §3.4's client row**, which implies `AtlasIds` must become
  `TextureAtlas.LOCATION_ITEMS` on 1.21.11; it must not.
- **Blend-state hazard**: the legacy 14-arg `innerBlit` ends with an unconditional
  `RenderSystem.disableBlend()`. Since the HUD is drawn from a `Gui` mixin on those
  nodes, inject at/after the experience bar or at `TAIL` of `Gui.render`; anywhere else,
  follow the draws with `RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();`.
- Rejected fallbacks, so nobody re-derives them: `setColor` wrap (~30 min, and `setColor`
  is gone in 1.21.6+ so it can never be the shared path), an `@Invoker` onto the private
  `blitSprite` (pointless — that int is z), a hand-rolled `POSITION_TEX_COLOR` buffer
  (~half a day), and dropping the converging-icon fade (real feature loss — do not take
  it).

Also stale in `CLAUDE.md`: the HUD bar is **opaque** (`BASE_ALPHA = 1.0F`), not "~50%
alpha".

### R-18 — building a non-active node (CLOSED, measured in Stage 1)

`./gradlew :26.1-fabric:build` with `stonecutter active "26.2-fabric"` **works, with no
`stonecutterSwitch`**. Gradle pulls `stonecutterPrepare`/`stonecutterGenerate` (and the
`…Client` pair) in as dependencies of `compileJava`/`compileClientJava`, and the shared
`src/` is left untouched (only the explicit `stonecutterSwitchTo…` task runs `merge`,
which is what writes back). So:

- **CI needs no switch loop.** `./gradlew buildAndCollect` from a clean checkout is
  enough, and it builds every node in one invocation.
- Corollary already noted in §2: the *active* node is the one node whose `//?` syntax is
  never exercised by a build.

## 7. Release / balance workflow after Stage 1

```bash
# 1. Edit the numbers. ONE file, no conditionals in it, no per-variant copies.
#    src/main/java/com/specialities/skills/Tuning.java
#    (config defaults/clamps: src/main/java/com/specialities/config/SpecialitiesConfig.java)
# 2. Bump the version in ONE place: stonecutter.properties.toml -> mod.version
# 3. Build every node (stonecutterGenerate runs automatically):
./gradlew buildAndCollect          # -> build/libs/<mod.version>/specialities-<ver>+<mc>.jar
# 4. Boot-smoke each jar on a headless dedicated server (design §7 Tier 2).
# 5. Publish (Stage 4b wires this up), then tag and push.
```

- There is **no chiseled task.** `chiseledBuild` was removed; aggregation is the
  unqualified task name plus `stonecutter.tasks.order(...)` for endpoint sequencing.
- `./gradlew :<node>:runClient` — the root project has no `runClient` any more. The
  user's `~/Desktop/"Specialities + Archetypes Dev 26.2.command"` **must be updated** to
  `./gradlew :26.2-fabric:runClient`.
- Publish **one Modrinth version per node**, not one version listing several game
  versions: the jars are genuinely different artifacts.
- **Archetypes handshake**: `./gradlew :26.2-fabric:publishToMavenLocal` publishes
  `com.specialities:specialities:<mod.version>` (bare, no `+mc` suffix). Only the 26.2
  node owns that coordinate. Note the pre-existing mismatch: `archetypes/build.gradle`
  asks for `1.5.0-dev` while this tree publishes `1.5.0`.
