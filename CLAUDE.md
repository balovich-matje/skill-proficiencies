# Specialities — Fabric mod for Minecraft 26.2

## Environment (pinned versions — do not change without checking https://fabricmc.net/develop)

| Component     | Version                                              |
| ------------- | ---------------------------------------------------- |
| Minecraft     | 26.2 (Java Edition)                                  |
| Fabric Loader | 0.19.3                                               |
| Fabric API    | 0.154.2+26.2                                         |
| Fabric Loom   | 1.17.13                                              |
| Gradle        | 9.5.1 (via wrapper)                                  |
| JDK           | OpenJDK 25 (Homebrew `openjdk@25`, currently 25.0.3) |

- JDK path on this machine: `/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home`, also symlinked at `~/Library/Java/JavaVirtualMachines/openjdk-25.jdk` so `/usr/bin/java` and `/usr/libexec/java_home -v 25` resolve it. If `./gradlew` can't find Java, run `export JAVA_HOME=$(/usr/libexec/java_home -v 25)` first.
- Mappings: no yarn entry — Loom 1.17 uses official Mojang mappings for 26.2. Do not add a yarn `mappings` dependency.
- Sources are split (`loom.splitEnvironmentSourceSets()`): common code in `src/main`, client-only code in `src/client`.
- Mod id: `specialities` (final, checked free on Modrinth/CurseForge 2026-07-08). If it ever changes, update together: `fabric.mod.json`, `settings.gradle` (`rootProject.name`), `build.gradle` (`loom.mods` block), `src/main/resources/specialities.mixins.json`, `src/client/resources/specialities.client.mixins.json`, `src/main/resources/assets/specialities/`, and `MOD_ID` in `Specialities.java` — rename all together.

## Standing instruction: consult docs before writing API code

Minecraft 26.2 **changed the rendering and registration APIs**. Do NOT rely on pre-26.2 Fabric/Minecraft code patterns from memory — verify against:

- Official Fabric docs: https://docs.fabricmc.net
- 26.2 announcement / porting notes: https://fabricmc.net/2026/06/15/262.html

Known 26.2 breaking changes (from the official announcement):

- **Registration**: block and item IDs are stored separately in `BlockIds`, `BlockItemIds`, and `ItemIds`; `valueLookupBuilder` was removed — IDs are now separate from `Block`/`Item` instances.
- **Rendering**: OpenGL backend is planned for removal once the Vulkan backend is stable — use the Blaze3D API, never raw GL calls.
- **GUI**: screen management moved off `Minecraft` into dedicated `Gui`/`Hud` classes (e.g. `Minecraft.getInstance().gui.setScreen(...)`, not `Minecraft.getInstance().setScreen(...)`).
- Fabric API 0.150.1+ added enum extensions, tag removal, and an experimental Fluid Interaction API; 0.152.0+ added attended client commands.

When unsure about any API surface, check the docs/porting notes first rather than guessing from older Minecraft versions.

Best ground truth on this machine: the decompiled 26.2 sources. Run `./gradlew genSources`, then grep the jars under `.gradle/loom-cache/minecraftMaven/net/minecraft/*/26.2/*-sources.jar`. Fabric API module jars for exact signatures: `~/.gradle/caches/modules-2/files-2.1/net.fabricmc.fabric-api/`.

Verified 26.2 specifics (differ from pre-26.2 knowledge):

- `net.minecraft.Util` → `net.minecraft.util.Util`; `ResourceLocation` → `net.minecraft.resources.Identifier`.
- Ore/log tags moved to `net.minecraft.tags.BlockItemTags` (e.g. `BlockItemTags.DIAMOND_ORES.block()` / `.item()`); `BlockTags` keeps only a few convenience constants (GOLD/IRON/COPPER_ORES, LOGS, CROPS, MINEABLE_WITH_*). `ItemTags.SPEARS` exists (spears are new weapons).
- GUI rendering is extract-based: `GuiGraphicsExtractor` (not `GuiGraphics`), `Screen.extractRenderState(graphics, mouseX, mouseY, a)` (not `render`), `Toast.extractRenderState(...)`. Screens are opened via the public field `Minecraft.gui` → `gui.setScreen(...)`. The XP bar is a `ContextualBar` (182x5 at `guiHeight - 29`).
- `ItemStack` implements the new `ItemInstance` interface; loot context TOOL param and `EnchantmentHelper.getItemEnchantmentLevel` take `ItemInstance`.
- Fortune is read from the TOOL stack in loot context (`ApplyBonusCount`); looting from `EnchantmentHelper.getEnchantmentLevel(enchantment, livingEntity)` (`EnchantedCountIncreaseFunction`).
- Fabric: HUD via `HudElementRegistry.attachElementAfter/Before(VanillaHudElements.X, id, element)` (package `...client.rendering.v1.hud`); creative tabs via `CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.X)`; damage events: `ServerLivingEntityEvents.AFTER_DAMAGE` exists, but there is no MODIFY_DAMAGE (use a `hurtServer` mixin); item model definitions are `assets/<ns>/items/<id>.json` (`{"model": {"type": "minecraft:model", "model": "..."}}`).
- In creative mode `InventoryScreen.init` redirects to `CreativeModeInventoryScreen` — UI injected into the survival inventory must also handle the creative screen.

## Mod architecture (mcMMO-style skills)

12 skills (mining, woodcutting, combat, arms_mastery, archery, harvesting, excavation, fishing, defence, acrobatics, athletics, sneaking), server-authoritative. Sneaking: detection reduction multiplies `LivingEntity.getVisibilityPercent` (same channel invisibility uses, so they stack; heavy-armor penalty via `specialities:heavy_armor` item tag), stealth crits via `hurtServer` mixin gated on `Mob.getTarget() != attacker` + one-shot `STEALTH_CRIT_DONE` attachment on the mob, proximity XP in `SneakingTicker` (only counts hostiles not targeting the player). Attribute-based passives (defence health/toughness — permanent modifiers reapplied on JOIN/AFTER_RESPAWN/level-change via `DefencePassives`; athletics sprint speed — transient modifier managed per-tick by `AthleticsTicker`, FOV-neutral via `AbstractClientPlayerMixin` dividing the bonus out of the attribute read in `getFieldOfViewModifier`). Fishing enchant bonuses hook `EnchantmentHelper.getFishingLuckBonus`/`getFishingTimeReduction` (fisher entity available). Acrobatics feeds points into `getDamageProtection` for IS_FALL (Feather Falling is flat points in 26.2: 3/level; pool formula is points/25 with a 20-point clamp in `CombatRules.getDamageAfterMagicAbsorb` — we uncap fall damage only: smooth 20→25 scaling in `LivingEntityMixin.hurtServer` + full cancel at ≥25 points via `ALLOW_DAMAGE`). Extra max health renders automatically — vanilla `Hud.extractPlayerHealth` stacks heart rows (`numHealthRows`, compressed `healthRowHeight`) and armor moves up with it. Key classes: `skills/Tuning` (all balance constants), `skills/SkillManager` (all XP/level mutations), `skills/SkillCategories` (block/tool/damage → skill mapping + XP table + melee/ranged specialization routing), `ModAttachments.SKILLS` (persistent player attachment, synced to owning client; also transient `RICOCHET_*` attachments on arrows), `SkillUpdatePayload` (drives client HUD bar + level-up toast).

Mixins: `PlayerMixin` (break speed, melee attack recovery via `getCurrentItemAttackStrengthDelay`, passive sweeping edge via the `SWEEPING_DAMAGE_RATIO` attribute read in `doSweepAttack` — enchant contributes N/(N+1), we recompute with effective level), `BlockMixin` (fortune via boosted tool copy in loot context + manual log bonus), `EnchantmentHelperMixin` (looting), `LivingEntityMixin` (combat damage), `BowItemMixin` (draw speed: scales `timeHeld` local in `releaseUsing`), `CrossbowItemMixin` (charge duration, stacks with quick charge), `AbstractArrowMixin` + `AbstractArrowAccessor` (ricochet: chains to nearest `Enemy` only, guard prevents friendly fire, `RICOCHET_IGNORE` lets the bounce arrow pass through its previous victim).

Client: `SkillXpHudBar` (always visible once any XP is gained in the session; ~50% alpha; converging item-sprite icons then bar-grow animation; item textures fetched via `Minecraft.getAtlasManager().getAtlasOrThrow(TextureAtlas.LOCATION_ITEMS).getSprite(...)` and drawn with `blitSprite(..., argbColor)` — plain `item()`/`fakeItem()` cannot render translucent), `SkillLevelUpToast` ("Skillname / Increased x -> y"; challenge jingle only when crossing level 50/100, otherwise default toast whoosh), `SkillsScreen` (+ "S" button injected into both inventory screens, re-anchored every screen tick via `AbstractContainerScreenAccessor` because the recipe book moves `leftPos` without re-init), `UseDurationMixin` (client mixin: bow pull animation runs off the `minecraft:use_duration` item model property — must be scaled separately from the gameplay-side `BowItemMixin`). Item tags: `specialities:weapons` = `melee_weapons` (swords/spears/trident/mace) + `ranged_weapons` (bow/crossbow). Combat XP goes to combat + the matching specialization (melee → arms_mastery, bow/crossbow arrows → archery via `AbstractArrow.getWeaponItem()`).

Mixin gotchas (hit in practice):

- `@ModifyVariable` targeting a LOCAL variable (`argsOnly=false`, `@At("STORE")`) with captured target args fails with "Scanned 0 target(s)" — even with a full method descriptor. It DOES work for `argsOnly=true` (see `LivingEntityMixin.hurtServer`). For locals, use MixinExtras instead: `@WrapOperation` on the consuming call + `@Local(argsOnly = true)` sugar for context (see `BowItemMixin`). Loader-bundled MixinExtras supports `@WrapOperation`, `@ModifyReturnValue`, `@ModifyExpressionValue`, and `@Local` sugar.
- `this.getAttributeValue(...)` inside `Player` compiles with bytecode owner `Player` (not `LivingEntity`) — check with `javap -c` before writing INVOKE targets.

## Commands

- `./gradlew build` — compile + package (jar in `build/libs/`)
- `./gradlew runClient` — launch the dev Minecraft client with the mod loaded
- `./gradlew runServer` — launch the dev server
