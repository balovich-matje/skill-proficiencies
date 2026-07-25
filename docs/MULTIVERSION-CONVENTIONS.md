# Multi-version conventions (FROZEN in Stage 1 — read before writing a `//?` block)

This file is the **normative** half of the port. The full reasoning lives in
[`MULTIVERSION.md`](MULTIVERSION.md) (the design doc, verbatim). The same content is
mirrored into the gitignored `CLAUDE.md` so a fresh session sees it; **this file is the
versioned copy and wins if the two ever disagree.**

Frozen in Stage 1 (branch `workspace`). Registered nodes as of Stage 5: **`26.2-fabric`
(active + VCS), `26.1-fabric`, `1.21.11-fabric`, `1.21.1-fabric`, `1.20.1-fabric`** — all five
build, and all five boot a real headless dedicated server clean. Phase A is complete.

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
| `>=26.2` | 26.2 | `BlockItemTags`; `advancements.triggers`; `Minecraft.gui.setScreen`/`toastManager()` |
| `>=26.1` | 26.2, 26.1 | Java 25; Cloth/ModMenu artifacts; `ItemInstance` (the `Block.getDrops` tool param and the matching `EnchantmentHelper.getItemEnchantmentLevel` overload); the extract-vs-immediate render hooks (`GuiGraphicsExtractor`, `Screen`/`AbstractWidget`/`Toast`/`HudElement` `extract*` vs `render*`, `text()` vs `drawString()`, `fakeItem()` vs `renderFakeItem()`); **fabric-api:** `PayloadTypeRegistry.clientboundPlay()/serverboundPlay()` (vs `playS2C()/playC2S()`), `creativetab.v1.CreativeModeTabEvents` (vs `itemgroup.v1.ItemGroupEvents`), `Screens.getWidgets` (vs `getButtons`) |
| `>=1.21.11` | 26.x, 1.21.11 | `Identifier`; **`net.minecraft.util.Util`**; `HudElementRegistry`/`VanillaHudElements`/`HudElement` (the whole `client.rendering.v1.hud` package); `.projectile.arrow` package; `ItemTags.SPEARS`; **`Items.IRON_SPEAR`**; `getAtlasManager`; `AtlasIds`; `getFieldOfViewModifier(ZF)F`; `MouseButtonEvent`; `pose()` returning `Matrix3x2fStack`; **`PermissionCheck.Require`** / the `net.minecraft.server.permissions` stack; copper armor; `advancements.criterion` (vs `critereon` below) — **added in Stage 4a:** `net.minecraft.util.ARGB` (vs `FastColor.ARGB32`); `RenderPipelines`; the 3-arg `Toast.render` interface shape and `ToastManager` (vs `ToastComponent` + a `Visibility render(GuiGraphics, ToastComponent, long)` that draws *and* returns visibility, with no `getSoundEvent()`); `Minecraft.getToastManager()` (vs `getToasts()`); `client.input.MouseButtonEvent` in widget/screen click signatures (vs `onClick(double,double)` / `mouseClicked(double,double,int)`); `setTooltipForNextFrame` (vs `renderTooltip(Font,List,Optional,int,int)`); `Item$Properties.setId`; jspecify-vs-jetbrains `@Nullable` (§5e-bis); `ServerPlayer.jumpFromGround` (it is on `Player` below, and runs on both sides there); `EnchantmentHelper.getComponentType` being **public** (private on 1.21.1); `client.renderer.item.properties.numeric.UseDuration` + `net.minecraft.world.entity.ItemOwner` |
| `>=1.21.2` | 26.x, 1.21.11 | `LivingEntity.hurtServer` (this is §3.2's worked example, and it had **never actually landed** until Stage 4a); `InteractionResult` returns (`Item.use` returns `InteractionResultHolder<ItemStack>` below); `Equippable` / the `minecraft:equippable` component (below: `net.minecraft.world.item.Equipable`, implemented by both `ArmorItem` and `ElytraItem`); `MobEffects.SPEED` (the rename of `MOVEMENT_SPEED`) |
| `>=1.21` | 26.x, 1.21.11, 1.21.1 | `Holder<Attribute>`; `ResourceKey` enchantments; data components; **singular `tags/item/` datapack directory** — **Stage 5 added:** id-keyed `AttributeModifier` (below: `(UUID, String name, double, Operation)`, `getAmount()` not `amount()`, and the three Operation constants are ADDITION/MULTIPLY_BASE/MULTIPLY_TOTAL); `Attributes.*` typed `Holder<Attribute>`; `EnchantmentHelper.getDamageProtection(ServerLevel,LivingEntity,DamageSource)F` (below: `(Iterable,DamageSource)I`, no victim); `getFishingLuckBonus`/`getFishingTimeReduction` carrying a fisher (below: `getFishingLuckBonus(ItemStack)I` / `getFishingSpeedBonus(ItemStack)I`); the looting loot path being `getEnchantmentLevel(Holder,LivingEntity)` (below: `getMobLooting(LivingEntity)I`); `Attributes.SWEEPING_DAMAGE_RATIO` (below: `EnchantmentHelper.getSweepingDamageRatio(LivingEntity)F`); `ItemEnchantments`/`DataComponents.ENCHANTMENTS` (below: `EnchantmentHelper.getEnchantments`/`setEnchantments` over a `Map<Enchantment,Integer>`); `EnchantmentTags` (below: `Enchantment.isDiscoverable`/`isTreasureOnly`/`isCurse`); `Identifier.withDefaultNamespace`/`fromNamespaceAndPath` (below: the two constructors); `AbstractArrow.getWeaponItem()`; the `Arrow(Level,d,d,d,ItemStack,ItemStack)` constructor; `EquipmentSlot.Type.HUMANOID_ARMOR` (below: `ARMOR`); `net.minecraft.client.DeltaTracker`; `FastColor.ARGB32.color(int,int)` and `colorFromFloat` (below: only the four-channel `color(a,r,g,b)`); the GUI **sprite atlas** as a whole — no `Minecraft.getGuiSprites()`, no `blitSprite`, no `hud/`, `toast/` or `transferable_list/` sprite ids |
| `>=1.20.5` | everything but 1.20.1 | `StreamCodec`/`RegistryFriendlyByteBuf`; the whole payload stack; Java 21 — **Stage 5 added:** `AttachmentType.syncWith` + `AttachmentSyncPredicate` + `AttachmentRegistry.create(Identifier,Consumer)` (0.92.11 has the builder form and no sync at all); `ServerLivingEntityEvents.AFTER_DAMAGE`; `ServerPlayerEvents.JOIN`/`LEAVE`; `Component.translatableEscape`; `Math.clamp` (a Java 21 method, and 1.20.1 is the Java 17 node); the four-double `mouseScrolled` (the horizontal axis arrived in 1.20.2); `Screen.render` drawing the background (below it draws the registered widgets and nothing else) |

Loader constants (from `constants { match(loader, …) }`): **`fabric`**, **`neoforge`**,
**`forge`**. Used as `//? if fabric {` / `//?} elif neoforge {` / `//?} elif forge {`.

Dependency predicate: **`fapi`** (`//? if fapi: >=0.100 {`), bound to `deps.fabric_api`.

Do **not** invent `>=1.20.2`, `>=1.20.5` synonyms, `>=26`, or `<1.21.4`. If a genuinely
new boundary is needed, add it to this table **in the same commit**.

**Five rows moved in Stage 2** (1.21.11), each because the shipped code proved the
boundary was one step off. Recorded so nobody "restores" them:

- `net.minecraft.util.Util`, `Items.IRON_SPEAR` and `PermissionCheck.Require` were on
  `>=26.1`. All three already exist on 1.21.11, so they are `>=1.21.11` facts. Mojmap:
  `net.minecraft.util.Util -> bhs` with **no** `net.minecraft.Util` on 1.21.11 (1.21.1 has
  `net.minecraft.Util -> ad`, 1.20.1 `-> ac`); the whole spear family `WOODEN_SPEAR -> xI`
  … `IRON_SPEAR -> xL` plus `assets/minecraft/textures/item/iron_spear.png`;
  `PermissionCheck -> bbj` / `PermissionCheck$Require -> bbj$b` / `Permissions -> bbr` with
  `Commands.hasPermission(PermissionCheck)`, proven end to end by `javap` of vanilla
  `GameModeCommand` and then by `help skillprof` printing the full command tree on a real
  1.21.11 dedicated server. The `Util` row being one step high was a **live build break**,
  not a cosmetic error — replacements are directional, so a false `>=26.1` rewrote the
  shared tree's `net.minecraft.util.Util` DOWN to a class 1.21.11 does not have.
- `CreativeModeTabEvents` was on `>=26.2`. `fabric-creative-tab-api-v1` 5.0.11 (the 26.1
  pin) already ships `creativetab.v1.CreativeModeTabEvents`, so the boundary is `>=26.1` —
  the same one `build.fabric.gradle.kts` already used for the module swap.
- `ItemInstance` and the extract-vs-immediate render surface were never listed at all; they
  are the two largest `>=26.1` deltas in the tree and now say so.

**Stage 5 added rows but invented no predicate either.** All 1.20.1 blocks sit on `>=1.21`
(the vanilla API rework) or `>=1.20.5` (fabric-api, the payload stack, the Java level), and
several existing two-branch blocks became three- and four-branch `elif` chains. The one
version number Stage 5 wrote that is NOT in this table is `1.21.4`, in
`build.fabric.gradle.kts` — see §6 R-16's item-model note for why a build script is allowed
to name the true boundary.

**Stage 4a added rows but invented no predicate.** All 67 new blocks for `1.21.1-fabric` sit
on `>=1.21.11` (57) or `>=1.21.2` (10), and six existing two-branch blocks became three-branch
`elif` chains (`>=26.2` / `>=26.1` / `>=1.21.11` / else). Two process notes worth keeping:

- **Chains, never nesting.** Where a line already forked at `>=26.1` and needed a third
  behaviour at 1.21.1, the fix is another `elif` arm on the *same* block, not a `//?` inside a
  disabled branch. Nesting needs the `*` → `^` marker escalation of §4 and fails silently when
  got wrong.
- **Nine of the deltas were on nobody's list.** `Item$Properties.setId`, `MobEffects.SPEED`,
  the `InteractionResult` return, both `Artisan` blocks, the whole Toast-interface rewrite,
  `getToasts()`, jspecify, `LivingEntityMixin.hurtServer` and
  `ServerPlayerMixin.jumpFromGround` came out of the compiler, the boot log and the target
  audit — not out of the design's §3.3/§3.4 prediction, which covered roughly half the real
  surface. Budget a mapping-audit pass per node, and expect the boot to find the rest.

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

### `//?` DOES NOT WORK IN ANY `.json` FILE — corrected in Stage 4a

**This table said "yes" for the two mixin configs until Stage 4a, and that error is
crash-level. Do not restore it.** Stage 2 verified only that Mixin's *parser* tolerates a
leftover directive. It never verified that Stonecutter would ever put one there. **It will
not: Stonecutter does not process `.json` at all.** Ground truth, read out of the 0.9.7
sources jar: `dev/kikugie/stonecutter/controller/file/Defaults.kt` registers file handlers
for exactly `java, scala, sc, groovy, gradle, json5, kt, kts, fsh, vsh, cfg, aw,
accesswidener, ct, classtweaker, yml, yaml` — **`json5` is there, `json` is not** — and
`StonecutterBuildImpl` filters the prepare task's input to that extension set.

Measured, not reasoned: with a `//?` directive written in place in
`src/client/resources/specialities.client.mixins.json`, the shipped **1.21.1 jar carried the
directive verbatim** — a config still listing `UseDurationMixin` (absent on that node) and
commenting out `GuiMixin` (required on that node). That is a guaranteed hard crash on the
first client launch, produced by a green build.

| File | `//?` processed by Stonecutter? | Verdict |
|---|---|---|
| `specialities.mixins.json`, `specialities.client.mixins.json` | **no** — `json` is not a registered file handler | **NO. The directive ships as literal text.** Mixin's lenient Gson would *tolerate* it, which is exactly why the failure is silent in the build and fatal at runtime |
| `fabric.mod.json` | **no** (same reason) — and even if it were, Fabric's own bundled `JsonReader` has `lenient = false` in the ctor (`iconst_0`), never set true in `ModMetadataParser`, and `checkLenient()` throws on a leading `/` | **NO — unloadable mod** |
| `data/**` tag JSONs, `assets/**` | **no** (same reason) | **NO** — and unnecessary anyway, see §6 R-16 |
| a file renamed to `.json5` | yes | works, but Fabric/Mixin/vanilla all read by literal path, so renaming is not an option for any file the game loads by name |

**The two mechanisms that do work**, in preference order:

1. **`processResources` conditioning** — plain Kotlin `if (sc.current.parsed …)` in
   `build.fabric.gradle.kts`, `filter`/`eachFile` on the copy spec. This is how the
   `modmenu` entrypoint is blanked below 26.1 and how R-16's `tags/item/` → `tags/items/`
   rename will be done for 1.20.1. **It leaves the 26.x nodes' resource bytes untouched**,
   which is what keeps the reproduction gate sharp.
2. **A per-node file override at `versions/<node>/src/…`** — Stonecutter's generate task
   honours it by construction (`exclude { relativePath.getFile(localSourceFile).exists() }`).
   This is what Stage 4a used for the 1.21.1 client mixin config. Cost: a duplicated ~20-line
   file per legacy node, and a **drift hazard every time a client mixin is added or removed** —
   the override does not inherit. Prefer (1) for anything that will be touched again.

**Rejected, deliberately:** registering `json` in the controller's
`stonecutter parameters { }` file handlers so the in-place `//?` starts working. It is one
line, but it routes **every** JSON resource on every non-active node through the generator,
where the controller's `replacements` also apply — so it can move resource bytes on nodes
that are supposed to be byte-inert, and it would need its own regression pass. If it is ever
done, it must be its own commit with the four-node resource-byte gate re-run.

### A LIVE branch may not consist only of `//` comment lines — measured in Stage 5

Stonecutter cannot tell a branch whose every line starts with `//` from the *disabled*
single-line-comment form, so when it makes that branch live it strips one `//` layer off every
line. A "nothing to do on this node" branch written as

```java
//? if >=1.20.5 {
// Nothing to do: the platform already syncs this.
//?} else {
```

became 27 syntax errors on the 26.1 node. Two rules follow:

- **Explanations go ABOVE the whole block**, and the branch that has no code stays empty
  (`//? if >=1.20.5 {` immediately followed by `//?} else {`). That empty form is used all
  over the tree and is safe.
- **A comment inside a disabled branch belongs INSIDE the `/* … */`**, not between
  `//?} else {` and the `/*`. A line in that gap is part of the branch but outside the block
  comment, so enabling the branch eats its marker the same way. Hit twice in Stage 5, in both
  shapes.

An alternative for a whole file that must vanish on a node is the source-set exclusion of
§5e-ter, or Stage 4a's variant of it: put the file's **entire body including the type
declaration** inside the `//?` block, leaving only the `package` line live. A `.java` file
with no type declaration is a legal, empty compilation unit and produces no `.class` — which
is how `client/mixin/GuiMixin.java` exists in all four generated trees but is compiled into
the 1.21.1 jar only. (Its javadoc had to be rewritten as `//` comments first: a `*/` inside a
disabled branch closes the branch comment early — §5e-ter.)

For `fabric.mod.json`, condition the content in `processResources`. An `expand`
placeholder is also wrong: it makes the *raw* template invalid JSON, and Loom parses that
file at configuration time for mod-id detection ("Failed to parse fabric.mod.json").
Stage 2 gates the `modmenu` entrypoint by blanking its class line below 26.1, which leaves
an inert `"modmenu": []` and touches the 26.x nodes not at all.

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

**5e. Java 17 is the shared-code ceiling, and from Stage 5 it is ENFORCED** (R-15). `1.20.1`
is the Java 17 node — piston-meta's `javaVersion.majorVersion` for it is 17 and
`build.fabric.gradle.kts`'s existing `else` arm already resolved to VERSION_17 — so a Java 21+
API in shared code is now a build failure on that node instead of a latent one. Records are
fine; pattern-matching `switch`, sealed types and unnamed patterns are not. The audit found
exactly one violation in the tree, `Math.clamp` in `SkillManager.setLevel`, which is a Java 21
*method* rather than a language feature and therefore invisible to every earlier node. Flow
scoping of `instanceof` pattern variables, switch expressions over enums, `Stream.toList()` and
text blocks are all Java 17 and needed nothing.

**5e-bis. `org.jspecify.annotations.Nullable` needs no fork down to 1.21.11 — and forks at
1.21.1.** `org.jspecify:jspecify:1.0.0` is one of 1.21.11's *own* vanilla libraries (its
version manifest lists it), so the import resolves on that node's compile classpath with no
block — confirmed by `:1.21.11-fabric:build` compiling `LivingEntityMixin` and
`SkillCategories` untouched. It looks 26.x-only and is not.

**Measured in Stage 4a: it does NOT resolve on 1.21.1.** That version manifest lists no
jspecify artifact, and the node failed with `package org.jspecify.annotations does not exist`
in **nine** files (`MeleeSwing`, `platform/SkillStore`, `platform/FabricSkillStore`,
`mixin/LivingEntityMixin`, `skills/Artisan`, `skills/SkillCategories`, `client/SkillsScreen`,
`client/SkillLevelUpToast`, `client/SkillHudState`). `org.jetbrains:annotations:26.0.2` **is**
on that node's `compileClasspath` (exactly one annotations artifact in
`:1.21.1-fabric:dependencies`) and its `@Nullable` includes `TYPE_USE` and has the same simple
name, so the fork is **import-only, on `>=1.21.11`**, and no shared branch loses an
annotation. Assume 1.20.1 behaves like 1.21.1 until measured.

The cheaper-looking alternative — `if (sc.current.parsed < "1.21.11")
compileOnly("org.jspecify:jspecify:1.0.0")` in `build.fabric.gradle.kts` (mavenCentral is on
that node's repository list as Loom's "MavenRepo") — was **not** taken. Nine import-only
blocks are self-documenting and keep the node's compile classpath equal to what the game
actually ships; adding a dependency the target version never had hides the boundary from the
next porter. Do not "simplify" it back.

**5e-ter. A whole file that must vanish on a node leaves via the source set, not via `//?`.**
`client/config/ClothConfigScreen.java` and `ModMenuIntegration.java` have no classpath below
26.1 (Mod Menu / Cloth Config are gated there and the legacy nodes declare no pins), so
Stage 2 excludes them in `build.fabric.gradle.kts`:
`if (sc.current.parsed < "26.1") sourceSets["client"].java.exclude("com/specialities/client/config/**")`.
Wrapping each file in a `//?` block was rejected: both carry `/** … */` javadoc, so a
hand-written disabled branch contains a `*/` that closes the branch comment early, which
needs the `*` → `^` escalation of §4 and fails silently when got wrong. Any entrypoint or
mixin-config entry naming an excluded class must be gated in the same commit.

**5g. The seam layer is `com.specialities.platform`, and it is common-side only.**
Landed in Stage 3: `SkillStore` (attached state), `Net` (clientbound payloads),
`Platform` (loader services), each a public interface with a `static INSTANCE` field and
a **package-private** implementation (`FabricSkillStore`, `FabricNet`, `FabricPlatform`).
Three rules that fall out of it:

- **Nothing else in the tree may name `FabricLoader`, `AttachmentTarget`/`AttachmentType`,
  `PayloadTypeRegistry` or `ServerPlayNetworking`.** Those four symbols now appear in
  exactly one file each; a new call site outside `platform/` is the drift to catch in
  review (`grep` is enough).
- **`INSTANCE` stays unconditional until a non-Fabric node exists.** A `//? if fabric`
  block there cannot be exercised by any registered node, and a disabled branch naming a
  class that does not exist is the silent-failure case of §4. Phase B adds the block and
  the source-set exclusion (§5e-ter) together; each interface's javadoc spells out the
  form.
- **A seam in `src/main` cannot reach client-only API.** Measured, not assumed:
  `net.minecraft.client` is not on `src/main`'s compile classpath, so a common
  implementation calling `ClientPlayNetworking.registerGlobalReceiver` fails with
  "cannot access Minecraft". Client-side registration therefore stays in
  `client/SpecialitiesClient` and forks in place — same reasoning design §2 uses to reject
  a HUD seam. Do not "complete" the `Net` interface with a client method.

**5h. Below `>=1.21.11` the HUD is a `Gui` mixin, and every `@Mixin` member reference on
`Gui` must carry a FULL DESCRIPTOR.** Landed in Stage 4a for `1.21.1-fabric`; 1.20.1 will need
the same shape. There is no `client.rendering.v1.hud` package in fabric-api 0.116.14+1.21.1,
so `HudElementRegistry` / `VanillaHudElements` / `HudElement` are all absent and nothing can
be registered. `client/mixin/GuiMixin.java` replaces them: one `@Inject` at `TAIL` of
`Gui.render(GuiGraphics,DeltaTracker)V` that draws through the **same shared**
`SkillXpHudBar.render` / `StealthVignette.render` the other nodes call, plus five
`@WrapMethod`s that translate the pose by `-HUD_SHIFT`, all delegating to one shared
`@Unique` helper (§5a). This is R-11's "decide explicitly" decision, taken to preserve
`HUD_SHIFT = 7`, which is part of the Archetypes collision contract.

- The seven raised `VanillaHudElements` ids map onto exactly **five** 1.21.1 methods:
  `renderJumpMeter` + `renderExperienceBar` (INFO_BAR), `renderExperienceLevel`,
  `renderPlayerHealth` (armor + hearts + food + air are all inside it), `renderVehicleHealth`.
  `renderSelectedItemName`, `renderEffects` and the hotbar are deliberately **not** wrapped —
  their 26.x counterparts are not raised either. That is also why wrapping
  `renderHotbarAndDecorations` instead is wrong.
- **Bare method names are not acceptable here.** After remap these five plus `render` become
  `a`/`b`/`c`/`n`, and `a` alone is overloaded ~20× on `Gui`. Write the full descriptor on all
  six and verify it in the *shipped* jar against that node's `intermediary` `mappings.tiny`
  (loom remaps mixin annotations in place — there is no refmap, so what is in the jar is
  literally what Mixin will use).
- Blend state: the legacy 14-arg `innerBlit` ends with an unconditional
  `RenderSystem.disableBlend()`, so inject at/after the experience bar or at `TAIL` of
  `Gui.render` — see R-17's blend-state note.
- The whole file is gated `<1.21.11` by putting its **entire body including the class
  declaration** inside the `//?` block (see §4) — so it compiles to no class on 26.x/1.21.11,
  and `GuiMixin` is listed only in the 1.21.1 per-node mixin config.

**5i. Below `>=1.21` the HUD mixin wraps FOUR methods, not five.** The 1.20.1 arm of
`client/mixin/GuiMixin.java`, landed in Stage 5. `Gui.render` takes `(GuiGraphics, float)`
there — `DeltaTracker` is 1.21+ — and it is the whole HUD rather than a LayeredDraw wrapper,
with exactly ONE `return` (offset 1537, right after `renderSavingIndicator`), so the TAIL
inject that draws the two mod elements is unambiguous and always reached.

There is **no `renderExperienceLevel`** on 1.20.1. `renderExperienceBar(GuiGraphics,I)V` draws
the bar AND the level number, the second under its own `expLevel` profiler section with the
familiar five `drawString` calls — measured with `javap -c` of `eow.a(eox,int)`, not assumed.
So wrapping the bar raises the number with it and the seven raised `VanillaHudElements` ids
still map completely: INFO_BAR and EXPERIENCE_LEVEL → `renderExperienceBar` +
`renderJumpMeter`, HEALTH/ARMOR/FOOD/AIR → `renderPlayerHealth`, MOUNT_HEALTH →
`renderVehicleHealth`. `pose()` is a `PoseStack`, so the shift is pushPose/translate/popPose.

`HudRenderCallback.onHudRender(GuiGraphics,float)` **does** exist in fabric-rendering-v1 3.0.9
and would serve for the two mod draws. It is deliberately unused: it cannot raise a vanilla
element, so the mixin must exist for `HUD_SHIFT` regardless, and splitting the HUD across an
event and a mixin would make the draw order depend on event-vs-mixin ordering for no gain.

**5j. `processResources` transforms are invisible to Gradle's up-to-date check.** Copy-spec
ACTIONS — `eachFile`, `filter` — are not task inputs. Measured in Stage 5: the first build
after adding the tag-directory rename and the item-model conversion reported
`processResources UP-TO-DATE` and shipped the untransformed resources. Any new conditional
transform needs its decision declared with `inputs.property(...)` in the same edit.

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

**LANDED IN STAGE 4** (commit `20bdd72`, with the 1.21.1 node). Stage 2 wrote and measured
the six `required:false` edits but parked them on branch `stage4a-tags-required-false`,
because they were a no-op until 1.21.1 existed and were the one change that would have moved
the 26.x jars' resource bytes. Stage 4a cherry-picked that one commit; the branch's other five
commits were a parallel rebase of work already on `workspace` (patch-ids identical), so the
branch was **deleted, local and remote, in Stage 4** — nothing was lost.

Stage 2 re-proved both halves rather than taking them on trust — `TagEntry`'s codec is
`ExtraCodecs.TAG_OR_ELEMENT_ID.fieldOf("id")` +
`Codec.BOOL.optionalFieldOf("required", TRUE)`, bytecode-identical in all five target jars
(so `required:false` is valid syntax everywhere, including 1.20.1, and `#`-prefixed tag
references accept it too), and a real 1.21.11 dedicated server logged **no**
`missing following references`.

**CLOSED COMPLETELY IN STAGE 5.** The `processResources` rename landed exactly as prescribed
(`eachFile { if (path.contains("/tags/item/")) path = path.replace("/tags/item/", "/tags/items/") }`
under `if (sc.current.parsed < "1.21")`), and the 1.20.1 jar ships
`data/specialities/tags/items/` while every other node's resource bytes are untouched. Proven
loaded on a real 1.20.1 server with the positive probe below: all six tags answered
`No player was found` (i.e. resolved at command parse time) and the deliberately absent control
answered `Unknown item tag 'specialities:does_not_exist'`. Zero `missing following references`,
and `/reload` re-ran the datapack load clean. The cascade did not fire.

**A SECOND resource-layer bug, found in Stage 5 and NOT part of R-16**: item model
DEFINITIONS (`assets/<ns>/items/<id>.json`) are **1.21.4+**. Below that an item binds to
`assets/<ns>/models/item/<id>.json` by id and the file is a model, not a definition — so the
thirty knowledge books had no model at all on the **1.21.1** node from Stage 4a onward, shipped
verbatim into a directory that version does not read. Invisible because no client below 26.2
has ever been launched. All thirty definitions name one vanilla model and nothing else, so the
fix is mechanical (`{"model":{"type":"minecraft:model","model":"X"}}` → `{"parent":"X"}` plus
the path move) and is applied for `< 1.21.4` in `processResources`, fixing both legacy nodes.
That version number is deliberately the TRUE boundary rather than the nearest frozen predicate:
no registered node sits between 1.21.2 and 1.21.11, §5f already requires plain Kotlin
comparisons in build scripts, and the real number means a future 1.21.4-1.21.10 node behaves
correctly with no edit. This relocation is the one deliberate resource change Stage 5 made to a
prior node.

**Two corrections from the 1.21.1 measurement:**

- Exactly **five** direct entries fail on 1.21.1 — the four `minecraft:copper_*` armor pieces
  and `#minecraft:spears` (no `data/minecraft/tags/item/spears.json` in that jar).
  **`minecraft:mace` DOES resolve on 1.21.1** (it is in that version's mojmap `Items`), so
  design §3.4's parenthetical is wrong and the bullet above is right. Marking `mace` optional
  anyway is harmless and it stays marked — the entry that *is* present is unaffected.
- The `tags/item/` (singular) directory is **already correct for 1.21.1** (`>=1.21`), so the
  `processResources` rename is a **1.20.1-only** concern. Nothing was needed on this node.

**Verified on the real 1.21.1 server in Stage 4**, both directions: zero
`missing following references` / `Couldn't load tag`, *and* a positive probe —
`clear @a #specialities:<tag>` for all six tags answered `No player was found` (i.e. the tag
resolved at parse time) while a deliberately absent control id answered `Unknown item tag`.
That is what actually rules out the cascade; the absence of an error line does not, because a
`required:false` drop is by design silent. `/reload` re-ran the datapack load clean. All four
nodes' servers were re-booted after this change with zero tag diagnostics.

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
- **Stage 2 closed this for 1.21.11 and confirmed the branch shape.** The ARGB-int sprite
  overload `blitSprite(RenderPipeline, TextureAtlasSprite, int,int,int,int, int)` is present
  **and public** on 1.21.11 (`javap -p gir` → `public void a(RenderPipeline, ilp, int, int,
  int, int, int)`, unlike 1.21.1 where the sprite-taking overloads are private), and its
  trailing `int` really is a colour there, not the `blitOffset`/z of the 1.21.1 trap:
  `javap -c` shows it forwarding to `innerBlit(pipeline, Identifier, x1,x2,y1,y2,
  u0,u1,v0,v1, colour)` with `iload 7` landing in the colour slot, and the 6-arg overload
  passing `iconst_m1` (0xFFFFFFFF, opaque white). So the icon tint keeps the 26.x ARGB path
  on this node and the float-RGBA blit is needed **strictly below 1.21.11** — the
  `//? if <1.21.11` shape above is right as written, and no new predicate is required.
  `SkillIcons` needed no block at all.
- **Blend-state hazard**: the legacy 14-arg `innerBlit` ends with an unconditional
  `RenderSystem.disableBlend()`. Since the HUD is drawn from a `Gui` mixin on those
  nodes, inject at/after the experience bar or at `TAIL` of `Gui.render`; anywhere else,
  follow the draws with `RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();`.
- Rejected fallbacks, so nobody re-derives them: `setColor` wrap (~30 min, and `setColor`
  is gone in 1.21.6+ so it can never be the shared path), an `@Invoker` onto the private
  `blitSprite` (pointless — that int is z), a hand-rolled `POSITION_TEX_COLOR` buffer
  (~half a day), and dropping the converging-icon fade (real feature loss — do not take
  it).

- **CLOSED at 1.21.1 in Stage 4a, exactly as written above — trap included.** `javap -p` of
  `GuiGraphics` on the real 1.21.1 client jar: the float-RGBA blit
  `a(int,int,int,int,int,TextureAtlasSprite,float,float,float,float)` is **public**, both
  sprite-taking `blitSprite` overloads are **private**, and `setColor(float,float,float,float)`
  and the two `blitSprite(ResourceLocation, …)` forms are public. Every tinted sprite draw went
  through the public float-RGBA blit; `iconAlpha` stayed an int 0..255 divided at the call site
  (§5b) and no easing was forked.
- **What R-17 did not predict, and it is the bigger half of the client work on this node:**
  `net.minecraft.util.ARGB` is absent below 1.21.11 → `FastColor.ARGB32` (identical member
  names and shapes: `color(int,int)`, `colorFromFloat`, `alpha`/`red`/`green`/`blue`). Those
  lines already forked at `>=26.1`, which is what turned three blocks into `elif` chains.
  `StealthVignette` has no colour-taking blit overload at all on 1.21.1, so it calls `setColor`
  first — which is how vanilla's own `Gui.renderTextureOverlay` tints there — with blend armed
  by hand per the blend-state note. And the bar background keeps honouring `BASE_ALPHA` via
  `Minecraft.getGuiSprites().getSprite(...)` + the float-RGBA blit; `hud/experience_bar_background`
  ships no `.mcmeta` in the 1.21.1 jar (plain STRETCH), so the draw is geometrically identical
  to vanilla's `blitSprite` of it. All four sprite ids used were checked present in that jar.
- **`setColor` is still not the shared path** and the rejection above stands: it is gone in
  1.21.6+. It is used on 1.21.1 only for the one draw that has no tinted overload.
- **CLOSED AT 1.20.1 IN STAGE 5, and R-17 was right about every one of its 1.20.1 claims.**
  The public float-RGBA `blit(x, y, z, w, h, sprite, r, g, b, a)` is present and identical, so
  the converging icons needed no third branch at all; `Gui.GUI_ICONS_LOCATION` really is
  private (all six of `Gui`'s ResourceLocation fields are), so the XP bar background names
  `textures/gui/icons.png` literally, at u=0, v=64, 182x5 — where vanilla's own
  `renderExperienceBar` reads it. What R-17 did not cover is that 1.20.1 has **no GUI sprite
  atlas at all**, so two more sprite ids had to become sheet coordinates:
  - the toast backdrop → `Toast.TEXTURE` (`textures/gui/toasts.png`) at u=0, v=0, a PUBLIC
    interface field there and the same thing vanilla's AdvancementToast blits;
  - the two expand-arrow sprites → `textures/gui/resource_packs.png`, and WHICH cells was
    settled by pixel comparison rather than by reading the selection-list code: every 32x32
    cell of the 1.20.1 sheet was compared against the 1.21.1 sprite PNGs, giving
    `transferable_list/select` = (0, 0) and `select_highlighted` = (0, 32). (By-product:
    unselect = u32, move_down = u64, move_up = u96; the highlighted row is always v32.)
  The arrow draw needs the overload that takes a destination size AND a source region —
  `blit(rl, x, y, w, h, u, v, uWidth, vHeight, texW, texH)`, confirmed from its own bytecode to
  forward to `innerBlit` with `x2 = x + w` — because that is the one that SCALES the native
  32x32 into ARROW_SIZE the way `blitSprite` does above. The plain 7-arg
  `blit(rl, x, y, u, v, w, h)` would have cropped instead, silently.
- **`FastColor.ARGB32` on 1.20.1 has only the four-channel `color(a, r, g, b)`** — no
  `color(alpha, rgb)`, no `colorFromFloat` — so both are restored as one-line private helpers
  from the channel accessors that version does have, rather than growing four arguments at each
  of seven call sites. Same value, bit for bit.
- **A rendering bug no build could catch, and the reason a client-side port needs a reader as
  well as a compiler:** on 1.20.1 `Screen.render` draws the registered widgets AND NOTHING
  ELSE. Drawing the background became its job in 1.20.2, and every vanilla screen on that
  version calls `renderBackground` itself first. Without that added call `SkillsScreen`
  compiles, applies, and renders over the live world.

Also stale in `CLAUDE.md`: the HUD bar is **opaque** (`BASE_ALPHA = 1.0F`), not "~50%
alpha".

### R-07 — the sweeping-edge `ordinal` (CLOSED at 1.21.1, and the answer differs per node)

Do **not** let the 1.21.11 evidence talk anyone out of the `ordinal` on the legacy nodes.

- **1.21.11 and 26.x: no `ordinal`.** `doSweepAttack` exists as its own method and contains
  exactly one `getAttributeValue(Holder)D` call, on `SWEEPING_DAMAGE_RATIO`.
- **1.21.1: `method = "attack"` + `ordinal = 1`.** `doSweepAttack` does not exist; the sweep is
  inline in `Player.attack(Entity)V`, which contains **two** `getAttributeValue` calls —
  ordinal 0 is `ATTACK_DAMAGE` (base melee damage) and ordinal 1 is `SWEEPING_DAMAGE_RATIO`.
  Getting this wrong silently multiplies base attack damage instead of the sweep ratio: no
  crash, no log line, a large balance change.
- **Proven at the bytecode level twice** (Stage 4a from `javap -c` of the vanilla class, Stage 4
  independently from the server's `-Dmixin.debug.export` dump). In the transformed
  `class_1657`, `@WrapMethod` moves the body to `method_7324$mixinextras$wrapped$219`; inside
  it the *only* specialities handler is
  `modifyExpressionValue$…$specialities$passiveSweepingEdge:(D)D` at offset 633, sitting
  immediately after `624: getstatic class_5134.field_51577` / `627: invokevirtual
  method_45325`, feeding `d2f; fload_2; fmul; fadd`. The ordinal-0 call at offset 35
  (`field_23721`) goes straight to `d2f; fstore_2` — untouched. Field identities resolved from
  the real 1.21.1 artifacts, not memory: intermediary `class_5134` = obf `buw` = mojmap
  `Attributes`; `field_23721` = obf `c` = `ATTACK_DAMAGE`; `field_51577` = obf `D` =
  `SWEEPING_DAMAGE_RATIO`.
- Side result worth keeping: **`@WrapMethod` and an ordinal-N `@ModifyExpressionValue` coexist
  correctly in one method** on MixinExtras 0.4.1 / loader 0.16.14. That combination exists on
  no other node.
- Both `getAttributeValue` constants in that method carry no owner prefix, i.e. the bytecode
  owner really is `Player`, so the `@At` target descriptor is unchanged from 26.x. This is the
  CLAUDE.md "`this.getAttributeValue(...)` inside `Player` compiles with owner `Player`"
  gotcha — check it with `javap -c` per node rather than assuming either way.

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
# 3. Write the release notes: changelogs/<mod.version>.md
#    line 1 "# <release title>" becomes the Modrinth version name; the rest is the body.
# 4. Build every node (stonecutterGenerate runs automatically):
./gradlew buildAndCollect          # -> build/libs/<mod.version>/specialities-<ver>+<mc>.jar
# 5. Boot-smoke each jar on a headless dedicated server (design §7 Tier 2).
./gradlew printPublishMetadata     # 6. pre-flight: what each node WOULD upload. No network.
./gradlew publishMods              # 7. full dry run — the checked-in default, no token needed
# 8. THE upload (needs the user's go-ahead; only this step touches Modrinth):
#    MODRINTH_TOKEN=$(cat ~/.config/modrinth/token) \
#      ./gradlew --no-daemon -PpublishLive=true publishMods
# 9. git commit -am "Release <v>" && git tag Release-<v> && git push --follow-tags
```

Steps 6-9 are Stage 4b's as-built wiring; `docs/MULTIVERSION.md` §4/§4.1 is the full account.
Two safety gates make step 7 un-dangerous: no lifecycle task depends on `publishModrinth`
(measured — `build --dry-run` lists neither publish task on any node), and `dryRun` is set on
the *extension* so `-PpublishLive=true` is the only way off it. In dry run the plugin never
calls `accessToken.get()`, so a mistaken invocation cannot leak a token. `--no-daemon` on the
live step is deliberate: a credential-carrying invocation must not leave a daemon holding
`MODRINTH_TOKEN` in its environment. **Claude never creates, pastes or reads the PAT.**

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
