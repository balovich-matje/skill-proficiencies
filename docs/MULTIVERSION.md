<!--
  VERBATIM COPY of the Stage-0 port design doc (scratchpad/port-design.md), committed so
  every later stage reads the same law. Where this file and docs/MULTIVERSION-CONVENTIONS.md
  disagree, the CONVENTIONS file wins — it carries the artifact-verified corrections found
  after the design was written (see its §5f and §6).
-->

I have everything I need — including the authoritative Stonecutter comment syntax from the maintained template and the proven trialE build scripts. Here is the design.

---

# Multi-Version / Multi-Loader Workspace Design — Skill Proficiencies (`specialities`)

**Verification note.** Every plugin id, version pin, API shape and syntax form below was read from an artifact or a working build, not from memory. New verification done in this pass (on top of the scouts'):

- Repo ground truth: `main↔26.1` diff is exactly 7 files / 15+ / 16− (`git diff --stat`); `src/` is 4,385 Java LOC across 57 files; wrapper is Gradle **9.5.1**; `build.gradle` uses `loom.splitEnvironmentSourceSets()` and `maven-publish`/`mavenJava`.
- **Stonecutter comment syntax** — read from the live maintained template `stonecutter-versioning/stonecutter-template-multiloader` (`gh api .../contents/...`, files `ModLoaderAccess.java`, `TemplateModCommon.java`, `TemplateModFabric.java`). This is the authoritative form; I did not invent it.
- **Stonecutter processes every source set**: `StonecutterBuildImpl.kt:36` `project.sourceSets.all { … }` → `configureSource(SourceSet)` (`StonecutterBuildTasksImpl.kt:43`) iterating `src.allSources()`. This decides the `src/client` question below.
- **`StonecutterControllerTasks`** API read from `scsrc/.../controller/task/StonecutterControllerTasks.kt`: `named(name, cls, filter): MapProperty<ProjectNode, TaskProvider<T>>`, `order(name, ordering, filter)`, `switch`. Confirms the scout: no `chiseled` anything.
- **`loomx` API** read from `lbc-src.jar` → `LoomCompatPlugin.kt`: `isUnobfuscated` (line 85; decided at 144-160 by `build.compare(build.current.version, "26") > 0`), `modJar`/`modSourcesJar` (86-87, → `jar` or `remapJar`), `applyMojangMappings()` (203), property keys `loomx.loom_version` / `loomx.unobfuscated` / `loomx.obfuscated` (19-25), and the `mod*` configuration aliases synthesised at 123-137.
- Template node scripts (`build.fabric.gradle.kts`, `build.neoforge.gradle.kts`, `buildSrc/.../neoforge-mutex.gradle.kts`) and `stonecutter.properties.toml` fetched verbatim — my sketches are edits of working files.

---

## 1. WORKSPACE LAYOUT

### 1.1 Decision: 26.x folds in **NOW, first, and it is the validation vehicle**

The tempting order is "build the tree around a legacy beachhead, fold 26.x in later". That is wrong here, for three evidence-backed reasons:

1. **Zero-risk exercise of the mechanism.** The `main↔26.1` delta is 4 code sites in 4,385 LOC (0.09%), all renames/package moves, verified. Expressing exactly those 4 sites as `//?` blocks is the cheapest possible end-to-end test of Stonecutter — and you have two known-good jars as an oracle. If the tree can't reproduce today's two builds, nothing else matters.
2. **Deferring keeps the duplication you are paying to remove.** Until 26.2 and 26.1 are nodes, every balance change lands in the tree *and* on two branches. The stated goal fails for the two versions that are actually shipping.
3. **It is proven to work.** TrialE built `:26.2-fabric` (resolving the mapping-free `minecraft-merged-deobf:26.2`) in the same invocation as four obfuscated nodes. The wall is not an obstacle to co-residency.

The "balance-file copy discipline" fallback is explicitly **not needed** and should not be built.

### 1.2 Decision: **new branch in the existing repo**, not a new repo

`git checkout -b workspace` off `main`. Reasons: same Modrinth project (`d4TtjlpN`) and slug, mod id `specialities` is frozen forever, CI/issues/history continuity, and Archetypes compiles against `com.specialities:specialities` from `mavenLocal` out of this working tree.

**Migration of the two existing branches:**

| Item | Action |
|---|---|
| `main` | After the tree reproduces both jars, fast-forward `main` to `workspace`. |
| `26.1` | `git tag archive/26.1-final 26.1 && git push --tags`, then delete the branch (local + origin). Its content survives as the `26.1-fabric` node. |
| `specialities/CLAUDE.md` | Rewrite the "Branches" section. **It is currently wrong**: it claims one code difference between main and 26.1; there are four (both GUI-routing renames and the `BlockItemTags` restructure are also branch deltas). Also fix the `TextureAtlas.LOCATION_ITEMS` claim (code uses `net.minecraft.data.AtlasIds.ITEMS`) and the ARCHITECTURE.md init-step list (omits `SkillCommands.register()`). |
| `~/Desktop/"Specialities + Archetypes Dev 26.2.command"` | **Breaks.** It does `git checkout main` + `./gradlew runClient`; the root project will have no `runClient`. Must become `./gradlew :26.2-fabric:runClient`. Flag to the user before landing — this is their playtest path. |
| `.github/workflows/build.yml` | `./gradlew build` → `./gradlew buildAndCollect`; keep JDK 25 as the *Gradle* JVM (9.6.1 runs on it, verified trialD) and let foojay provision 17/21. |

### 1.3 Variant list — phased, and the phasing is the main recommendation

The five demand targets are not equal-cost, and the loader axis is where the cost concentrates. Split into two phases:

**Phase A — Fabric axis only (five nodes, zero loader seams needed):**

| Node | MC | Java | Loom pipeline | fabric-api | Cost |
|---|---|---|---|---|---|
| `26.2-fabric` | 26.2 | 25 | `fabric-loom` (no-remap) | `0.155.2+26.2` | free (existing) |
| `26.1-fabric` | 26.1.2 | 25 | `fabric-loom` (no-remap) | `0.154.2+26.1.2` | free (4 `//?` sites) |
| `1.21.11-fabric` | 1.21.11 | 21 | `fabric-loom-remap` | `0.141.5+1.21.11` | **beachhead** — client de-extraction + ~10 renames |
| `1.21.1-fabric` | 1.21.1 | 21 | `fabric-loom-remap` | `0.116.14+1.21.1` | + HUD via `Gui` mixin (no element registry) |
| `1.20.1-fabric` | 1.20.1 | 17 | `fabric-loom-remap` | `0.92.11+1.20.1` | largest Fabric rewrite |

**Phase B — loader axis (two nodes, needs the full seam layer):**

| Node | Platform | Java | Plugin | Cost |
|---|---|---|---|---|
| `1.21.1-neoforge` | NeoForge **≥21.1.200** (pin 21.1.243) | 21 | `net.neoforged.moddev` 2.0.142 | platform layer; HUD is *easier* than fabric-1.21.1 |
| `1.20.1-forge` | LexForge 47.4.22 | 17 | `dev.architectury.loom` 1.17.491, `loom.platform=forge` | most expensive of all five; bespoke |

Rationale for the split: Phase A delivers the Fabric share of the measured demand (1,490 + 770 + 610 = **2,870 fresh packs**) plus both shipping 26.x versions, and needs **no loader abstraction at all** — every difference is version-conditional. Phase B adds 1,425 packs but requires capabilities/SimpleChannel/JiJ'd MixinExtras (Forge) and a parallel event/HUD/config layer. Design the seams now (§2) so Phase B is additive; do not build them out until Phase A ships.

### 1.4 Decision: **keep the split source sets** (`src/main` + `src/client`)

Verified enabler: Stonecutter iterates `project.sourceSets.all` and creates prepare/generate/merge tasks per source set (`StonecutterBuildImpl.kt:36`, `StonecutterBuildTasksImpl.kt:43-49`), so `src/client` is preprocessed exactly like `src/main` with no extra configuration. `loom.splitEnvironmentSourceSets()` is available on both loom pipelines (the repo already uses it on 26.2).

Why not collapse into one source set (as the template does): the compile-time client/common boundary is load-bearing for a mod that must run on dedicated servers, all 13 client files are already on the correct side of it, and collapsing is a 13-file refactor with no Phase A benefit. Phase B registers `client` as a plain source set on the moddev/archloom nodes (both ship merged dev jars) and relies on `Dist`/`@OnlyIn` for runtime separation.

### 1.5 Directory tree

```
specialities/                                  # same repo, branch `workspace`
├── CLAUDE.md                                  # rewritten (see 1.2)
├── settings.gradle.kts                        # NEW  (replaces settings.gradle)
├── stonecutter.gradle.kts                     # NEW  controller
├── stonecutter.properties.toml                # NEW  the single place versions live
├── gradle.properties                          # SHRINKS to Gradle-only options
├── build.fabric.gradle.kts                    # NEW  node script, all Fabric nodes
├── build.neoforge.gradle.kts                  # Phase B
├── build.forge.gradle.kts                     # Phase B
├── build.gradle                               # DELETED
├── buildSrc/                                  # Phase B only (neoforge mutex)
│   ├── build.gradle.kts
│   └── src/main/kotlin/neoforge-mutex.gradle.kts
├── gradle/wrapper/gradle-wrapper.properties   # 9.5.1 -> 9.6.1
├── src/                                       # THE SHARED TREE — edited by humans
│   ├── main/java/com/specialities/…           # 44 files, unchanged paths
│   ├── main/resources/…
│   ├── client/java/com/specialities/client/…  # 13 files, unchanged paths
│   └── client/resources/specialities.client.mixins.json
├── versions/                                  # GENERATED by Stonecutter — gitignore contents
│   ├── 26.2-fabric/gradle.properties          # tracked: per-node overrides only
│   ├── 26.1-fabric/gradle.properties
│   ├── 1.21.11-fabric/gradle.properties
│   ├── 1.21.1-fabric/gradle.properties
│   ├── 1.20.1-fabric/gradle.properties
│   ├── 1.21.1-neoforge/gradle.properties      # Phase B
│   └── 1.20.1-forge/gradle.properties         # Phase B
├── run/                                       # shared dev run dir (keep as-is, incl. mods/)
└── .github/workflows/build.yml                # buildAndCollect + toolchains
```

`.gitignore` additions: `versions/*/src/`, `versions/*/build/`, `versions/*/.gradle/` — track only each node's `gradle.properties`.

### 1.6 `settings.gradle.kts`

Derived from trialE (which configured all six nodes successfully) plus the template's repo list. **The two Forge/NeoForge mavens in `pluginManagement` are not optional** — Arch Loom's own buildscript classpath dies on `de.oceanlabs.mcp:mcinjector` without `maven.minecraftforge.net`.

```kotlin
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/")        { name = "FabricMC" }
        maven("https://maven.kikugie.dev/releases") { name = "KikuGieReleases" }
        maven("https://maven.neoforged.net/releases/") { name = "NeoForged" }   // Phase B
        maven("https://maven.architectury.dev/")    { name = "Architectury" }   // Phase B
        maven("https://maven.minecraftforge.net/")  { name = "Forge" }          // Phase B, REQUIRED by Arch Loom
    }
}

plugins {
    id("dev.kikugie.stonecutter")                              version "0.9.7"
    id("dev.kikugie.loom-back-compat")                         version "0.4.1"
    id("org.gradle.toolchains.foojay-resolver-convention")     version "1.0.0"
}

stonecutter {
    create(rootProject) {
        // versions/<project>-<loader>, each on build.<loader>.gradle.kts
        fun match(project: String, vararg loaders: String, version: String = project) {
            for (loader in loaders) version("$project-$loader", version).buildscript("build.$loader.gradle.kts")
        }
        // ---- Phase A ----
        match("26.2",    "fabric")
        match("26.1",    "fabric", version = "26.1.2")
        match("1.21.11", "fabric")
        match("1.21.1",  "fabric")           // + "neoforge" in Phase B
        match("1.20.1",  "fabric")           // + "forge"    in Phase B
        vcsVersion = "26.2-fabric"           // the node whose sources git sees by default
    }
}

rootProject.name = "specialities"            // keeps the jar base name
```

### 1.7 `stonecutter.gradle.kts` (controller)

```kotlin
plugins { id("dev.kikugie.stonecutter") }

stonecutter active "26.2-fabric"             // the node the IDE edits

stonecutter parameters {
    val (version, loader) = current.project.split('-', limit = 2)

    properties { tags(version, loader) }     // unlocks [<version>] / [<loader>."<version>"] sections
    constants  { match(loader, "fabric", "neoforge", "forge") }

    // Swaps: the value includes quotes + trailing semicolon (template form)
    swaps["mod_version"] = "\"${properties.get<String>("mod.version")}\";"
    swaps["minecraft"]   = "\"${node.metadata.version}\";"

    dependencies["fapi"] = properties.getOrNull<String>("deps.fabric_api") ?: "0"

    replacements {
        // Source is written in the LEGACY spelling; replaced upward.
        string(current.parsed >= "1.21.11") { replace("ResourceLocation", "Identifier") }
        string(current.parsed >= "26.1")    { replace("net.minecraft.Util", "net.minecraft.util.Util") }
    }
}

// Sequence cross-node uploads so they don't race Modrinth's rate limiter.
// publishMods is NOT an endpoint task — order its children (verified in the 0.9.7 KDoc).
stonecutter tasks {
    order("publishModrinth")
    order("publishCurseforge")
}
```

### 1.8 `stonecutter.properties.toml` — the single source of version truth

This file replaces the divergent `gradle.properties` on two branches. A balance release edits `mod.version` **here, once**.

```toml
mod.id      = "specialities"
mod.name    = "Skill Proficiencies"
mod.group   = "com.specialities"
mod.version = "1.5.0"

loomx.loom_version   = "1.17.17"     # NOTE: must NOT go in root gradle.properties (see R-01)
deps.fabric_loader   = "0.19.3"      # build-time loader for every Fabric node

["26.2"]
mod.mc_releases = ["26.2"]
[fabric."26.2"]
mod.mc_compat        = "~26.2"
mod.loader_floor     = ">=0.19.3"
deps.fabric_api      = "0.155.2+26.2"
deps.modmenu         = "20.0.1"
deps.cloth_config    = "26.2.155+fabric"

["26.1"]
mod.mc_releases = ["26.1"]
[fabric."26.1"]
mod.mc_compat        = "~26.1"
mod.loader_floor     = ">=0.19.3"
deps.fabric_api      = "0.154.2+26.1.2"
deps.modmenu         = "18.0.0"
deps.cloth_config    = "26.1.154+fabric"

["1.21.11"]
mod.mc_releases = ["1.21.11"]
[fabric."1.21.11"]
mod.mc_compat        = "1.21.11"
mod.loader_floor     = ">=0.17.3"    # fabric-api 0.141.5 demands it; bundles MixinExtras 0.5.0
deps.fabric_api      = "0.141.5+1.21.11"

["1.21.1"]
mod.mc_releases = ["1.21", "1.21.1"]
[fabric."1.21.1"]
mod.mc_compat        = ">=1.21 <=1.21.1"
mod.loader_floor     = ">=0.16.3"    # fabric-api only demands 0.15.11 -> MixinExtras 0.3.5, TOO OLD for @WrapMethod
deps.fabric_api      = "0.116.14+1.21.1"

["1.20.1"]
mod.mc_releases = ["1.20", "1.20.1"]
[fabric."1.20.1"]
mod.mc_compat        = ">=1.20 <=1.20.1"
mod.loader_floor     = ">=0.16.10"   # fabric-api 0.92.11 demands it; bundles MixinExtras 0.4.1
deps.fabric_api      = "0.92.11+1.20.1"
```

**The `mod.loader_floor` column is a real finding, not boilerplate.** The mod uses one `@WrapMethod` (PlayerMixin's MeleeSwing gate), which needs MixinExtras ≥ 0.4.0. Loader→MixinExtras is bundled, and the scout bisected real jars: 0.15.11→0.3.5 (no `WrapMethod.class`), 0.16.3→0.4.1, 0.16.10→0.4.1, 0.17.3→0.5.0, 0.19.3→0.5.4. fabric-api's own floors cover 1.20.1 (0.16.10) and 1.21.11 (0.17.3) but **not 1.21.1** (0.116.14 declares only 0.15.11). So `1.21.1-fabric` must declare `>=0.16.3` itself or players on loader 0.15.x get a cryptic mixin-apply crash instead of "update your loader". Do **not** copy `main`'s `>=0.19.3` down — that locks out most of the legacy audience for nothing.

### 1.9 Per-node `versions/<node>/gradle.properties`

Only overrides live here. Phase A Fabric nodes need **nothing** (loom version comes from the toml). Phase B:

```properties
# versions/1.20.1-forge/gradle.properties
loom.platform=forge
```

`1.21.1-neoforge` needs no file (moddev takes its version from the toml).

### 1.10 `build.fabric.gradle.kts` — one script, all five Fabric nodes

Edited from the template's working file. Key deltas from the template: split source sets, our nine fabric-api modules by name instead of the bundle, Cloth/ModMenu gated to 26.x only, and the publication coordinate pinned for Archetypes.

```kotlin
plugins { id("dev.kikugie.loom-back-compat") }

// DO NOT set `group = …` — the template warns against it; the publication sets its own coordinate.
version = "${property("mod.version")}+${sc.current.version}"
base.archivesName = property("mod.id") as String        // keeps `specialities-<ver>+<mc>.jar`

val requiredJava: JavaVersion = when {
    sc.current.parsed >= "26.1"    -> JavaVersion.VERSION_25
    sc.current.parsed >= "1.20.5"  -> JavaVersion.VERSION_21
    else                           -> JavaVersion.VERSION_17
}   // cross-checked against piston-meta javaVersion.majorVersion for all five targets

repositories {
    fun strictMaven(url: String, alias: String, vararg groups: String) = exclusiveContent {
        forRepository { maven(url) { name = alias } }
        filter { groups.forEach(::includeGroup) }
    }
    strictMaven("https://api.modrinth.com/maven", "Modrinth", "maven.modrinth")
}

loom {
    splitEnvironmentSourceSets()
    mods { create("specialities") { sourceSet(sourceSets.main.get()); sourceSet(sourceSets["client"]) } }
    runConfigs.all {
        preferGradleTask = true
        runDirectory = rootProject.file("run")          // keeps the existing run/mods/ intact
        jvmArguments.add("-Dmixin.debug.export=true")
    }
}

dependencies {
    fun fapi(vararg modules: String) =
        modules.forEach { modImplementation(fabricApi.module(it, sc.properties["deps.fabric_api"])) }

    minecraft("com.mojang:minecraft:${sc.current.version}")
    loomx.applyMojangMappings()                          // no-op on 26.x, layered mojmap on legacy
    modImplementation("net.fabricmc:fabric-loader:${property("deps.fabric_loader")}")

    // The nine modules actually used (scout attributed each by unzipping module jars).
    fapi("fabric-data-attachment-api-v1", "fabric-networking-api-v1", "fabric-entity-events-v1",
         "fabric-lifecycle-events-v1", "fabric-events-interaction-v0", "fabric-command-api-v2",
         "fabric-screen-api-v1")
    //? if >=1.21.11 {
    fapi("fabric-rendering-v1")                          // hud/ package exists from 1.21.11
    //?}
    //? if >=26.2 {
    /*fapi("fabric-creative-tab-api-v1")
    *///?} else
    fapi("fabric-item-group-api-v1")                     // ItemGroupEvents on every legacy target

    //? if >=26.1 {
    modImplementation("maven.modrinth:modmenu:${property("deps.modmenu")}")
    modImplementation("maven.modrinth:cloth-config:${property("deps.cloth_config")}")
    //?}
}

java {
    withSourcesJar()
    sourceCompatibility = requiredJava
    targetCompatibility = requiredJava
    toolchain { vendor = JvmVendorSpec.ADOPTIUM; languageVersion = JavaLanguageVersion.of(requiredJava.majorVersion) }
}

tasks {
    processResources {
        val props = mapOf(
            "id"        to sc.properties.get<String>("mod.id"),
            "name"      to sc.properties.get<String>("mod.name"),
            "version"   to project.version.toString(),
            "minecraft" to sc.properties.get<String>("mod.mc_compat"),
            "loader"    to sc.properties.get<String>("mod.loader_floor"),
            "java"      to "JAVA_${requiredJava.majorVersion}",
        )
        props.forEach { (k, v) -> inputs.property(k, v) }
        filesMatching("fabric.mod.json")  { expand(props) }
        filesMatching("*.mixins.json")    { expand(props) }   // compatibilityLevel: "${java}"
    }

    register<Copy>("buildAndCollect") {
        group = "build"
        from(loomx.modJar.flatMap { it.archiveFile }, loomx.modSourcesJar.flatMap { it.archiveFile })
        into(rootProject.layout.buildDirectory.file("libs/${property("mod.version")}"))
    }
}

// Archetypes' mavenLocal handshake: keep the coordinate EXACTLY as it is today.
//? if >=26.2 {
apply(plugin = "maven-publish")
publishing {
    publications.create<MavenPublication>("mavenJava") {
        from(components["java"])
        groupId = sc.properties["mod.group"]
        artifactId = sc.properties["mod.id"]
        version = sc.properties["mod.version"]          // "1.5.0", NOT "1.5.0+26.2"
    }
}
//?}
```

`processResources` driving `"compatibilityLevel": "${java}"` in both mixin configs is what solves the JAVA_25/21/17 problem with zero per-variant files — and it automatically satisfies Forge 1.20.1's Mixin-0.8.5 ceiling, because that node is Java 17 anyway.

---

## 2. PLATFORM ABSTRACTION SEAMS

**Governing rule — write it into CLAUDE.md:**

> **Mixins are the portable layer; platform events are the fallback.** A mixin whose target resolves on a platform stays a mixin there, even when the platform offers a tidier event. One mixin = one implementation = one balance behaviour. Use an event only where the mixin *cannot* work (target absent, or the hook has no access to the player).

This directly answers the scout's flagged tension ("platform events let you delete mixins, which fights one shared source tree"). Forge's `PlayerEvent$BreakSpeed`, `LootingLevelEvent`, `ItemCraftedEvent`, `ItemSmeltedEvent` and NeoForge's `LivingIncomingDamageEvent` are *rejected* as primary implementations: `Player.getDestroySpeed`, `ResultSlot.onTake`, `FurnaceResultSlot.onTake` and `BrewingStandMenu.<init>` all resolve identically on all five targets, so the mixin is the shared one. Events get used exactly three times, all on 1.20.1, all forced.

**Seam count: three.** Justified by call-site count — a seam is only worth it where the call sites are many *and* the platform difference is unavoidable within Phase A.

### Seam 1 — `SkillStore` (attachment / persistence / sync)

Replaces the direct `(AttachmentTarget)` casts in **6 files**: `ModAttachments`, `SkillManager`, `Artisan`, `mixin/LivingEntityMixin`, `mixin/AbstractArrowMixin`, `mixin/BrewingStandMenuMixin`. Needed **in Phase A**, because `AttachmentType.syncWith` and `AttachmentSyncPredicate` do not exist in fabric-api 0.92.11 (verified: absent from the module jar; and `AttachmentRegistry.create(Identifier, Consumer<Builder>)` is absent too).

```java
package com.specialities.platform;

public interface SkillStore {
    SkillStore INSTANCE = /*? if fabric {*/new FabricSkillStore();
                          /*?} elif neoforge *///new NeoForgeSkillStore();
                          /*?} elif forge    *///new ForgeSkillStore();

    // --- player skills (persistent, owner-synced, survives death) ---
    PlayerSkills   getSkills(Player player);
    void           setSkills(Player player, PlayerSkills skills);
    /** Full-state push. No-op where the platform syncs attachments itself. */
    void           resyncSkills(ServerPlayer player);

    // --- transient bookkeeping ---
    int      getRicochetBounces(Entity arrow);           void setRicochetBounces(Entity arrow, int n);
    int      getRicochetIgnore(Entity arrow);            void setRicochetIgnore(Entity arrow, int entityId);
    boolean  isStealthCritDone(Mob mob);                 void markStealthCritDone(Mob mob);
    @Nullable String getBrewingOwner(BlockEntity stand); void setBrewingOwner(BlockEntity stand, String uuid);
}
```

Per-platform sketch:

| Platform | Implementation |
|---|---|
| Fabric ≥1.21.1 | `AttachmentRegistry.create(id, b -> b.initializer(…).persistent(CODEC).syncWith(STREAM_CODEC, AttachmentSyncPredicate.targetOnly()).copyOnDeath())`; `resyncSkills` = no-op. Verified present on 0.116.14 / 0.141.5 / 0.155.2. |
| Fabric 1.20.1 | `AttachmentRegistry.builder().initializer(…).persistent(CODEC).copyOnDeath().buildAndRegister(id)` — **no `syncWith`**; `resyncSkills` sends a new `SkillsFullPayload` on join. Transient four use `AttachmentRegistry.create(id)` (present on 0.92.11). |
| NeoForge ≥21.1.200 | `AttachmentType.builder(…).serialize(CODEC).copyOnDeath().sync((holder, p) -> holder == p, STREAM_CODEC)` into `NeoForgeRegistries.ATTACHMENT_TYPES`; `getData`/`setData`. **≥21.1.200 is a hard floor** — `sync()` overload count is 0 on 21.1.195 and 3 on 21.1.200 (bisected against real jars). |
| Forge 1.20.1 | `Capability<PlayerSkills>` + `CapabilityToken`, `ICapabilitySerializable<CompoundTag>` provider, `AttachCapabilitiesEvent<Entity>`; `getSkills` resolves a `LazyOptional`. **No Codec** (bridge `PlayerSkills.CODEC` through `NbtOps`), **no copyOnDeath** (`PlayerEvent$Clone`, handle `isWasDeath` + End-return), **no sync** (`resyncSkills` is mandatory). |

Note the design payoff: the `resyncSkills` method exists solely because two of the seven nodes lack attachment sync, and it is a no-op on the other five. That is the whole cost of that platform difference.

### Seam 2 — `Net` (payload send / receive / registration)

Replaces registration in `Specialities` (2 lines), sends in `SkillManager` (1) and `SneakingTicker` (1), receivers in `SpecialitiesClient` (2) — **~6 sites in 4 files**. Needed in Phase A: `PayloadTypeRegistry`, `CustomPacketPayload`, `StreamCodec`, `ByteBufCodecs` and `RegistryFriendlyByteBuf` are all absent on 1.20.1 (mojmap class-existence checked).

```java
public interface Net {
    Net INSTANCE = /*? if fabric {*/new FabricNet();/*?} …*/

    void registerClientbound();                                   // called from common init
    void sendSkillUpdate(ServerPlayer p, String skillId, int fromXp, int xp, int fromLvl, int lvl);
    void sendStealthState(ServerPlayer p, int state);
    void sendSkillsFull(ServerPlayer p, PlayerSkills skills);      // only used where sync is absent
    void registerClientReceivers();                               // called from client init
}
```

**Wire ids and field order are frozen** (`specialities:skill_update`, `specialities:stealth_state`) so a client and server on different variants of the same MC version stay compatible.

| Platform | Implementation |
|---|---|
| Fabric ≥1.21.1 | Today's code: the two records implementing `CustomPacketPayload`, `PayloadTypeRegistry.clientboundPlay().register`, `ServerPlayNetworking.send`, `ClientPlayNetworking.registerGlobalReceiver`. |
| Fabric 1.20.1 | Records reimplemented as `FabricPacket` (`write(FriendlyByteBuf)` + `getType()`), types via `PacketType.create(Identifier, Function<FriendlyByteBuf,P>)` — registration is implicit, so `registerClientbound()` becomes a no-op. |
| NeoForge | `RegisterPayloadHandlersEvent` → `event.registrar("1").playToClient(TYPE, CODEC, handler)` (one call replaces Fabric's register + receiver pair); send via `PacketDistributor.sendToPlayer`. Payload records unchanged — vanilla `CustomPacketPayload` exists on 1.21.1. |
| Forge 1.20.1 | `NetworkRegistry.newSimpleChannel` + `registerMessage(index, …)` (indices 0/1/2 fixed); send via `channel.send(PacketDistributor.PLAYER.with(…), msg)`; handlers `ctx.enqueueWork`. |

### Seam 3 — `Platform` (loader services)

Free, three trivial methods, modelled on the template's `ModLoaderAccess`. Replaces `FabricLoader` use in `ConfigManager` (`getConfigDir`), `SkillTypes` (entrypoints), `ModMenuIntegration` (`isModLoaded`).

```java
public interface Platform {
    Platform INSTANCE = /*? if fabric {*/new FabricPlatform();/*?} …*/
    Path configDir();
    boolean isModLoaded(String id);
    boolean isClient();
    /** Externally-registered skills. Fabric: entrypoints. NeoForge/Forge: an IMC/service-loader equivalent. */
    List<SkillsEntrypoint> skillProviders();
}
```

`skillProviders()` is the one method with genuine per-platform weight: the `specialities:skills` entrypoint is the published third-party API surface, and NeoForge/Forge have no entrypoint concept. Phase B must pick a mechanism (`InterModComms` or `ServiceLoader`) and document it as a *second* API surface — the `SkillType`/`SkillRegistrar` interfaces themselves stay byte-identical.

### Explicitly rejected seams (and why)

| Candidate | Verdict | Reason |
|---|---|---|
| Event registration | **No seam.** `skills/SkillEvents.java` *is* the seam. | One file, one `register()` method. 1.20.1-fabric loses `AFTER_DAMAGE`/`JOIN`/`LEAVE` (verified absent on 0.92.11) → `//?` block substituting a `hurt` RETURN mixin + `ServerPlayConnectionEvents.JOIN/DISCONNECT`. An interface would add indirection for one call site. |
| HUD hook | **No seam.** One `//?`-branched method in `SpecialitiesClient`. | `HudElementRegistry` on 26.x/1.21.11; `Gui` mixin on 1.21.1/1.20.1; `RegisterGuiLayersEvent` on NeoForge; `RegisterGuiOverlaysEvent` on Forge. Four bodies, one registration site — a seam buys nothing. |
| Command registration | **No seam.** `//?` in `SkillCommands`. | `CommandRegistrationCallback.EVENT` is byte-identical on all four Fabric targets (verified). Only the permission gate differs (26.x `PermissionCheck.Require` vs legacy `src -> src.hasPermission(2)`). |
| Config | **No seam.** Keep the hand-rolled JSON `ConfigManager` on every platform. | Zero `net.minecraft` imports, Gson is bundled everywhere. Cloth/ModMenu stay behind `//? if fabric` + `>=26.1`. Lowest-risk cross-platform choice by a wide margin. |
| Creative tab / item registry | **No seam.** `//?` in `ModItems`. | One line each (`CreativeModeTabEvents.modifyOutputEvent` vs `ItemGroupEvents.modifyEntriesEvent`; `Properties().setId(key)` vs `Registry.register(Registries.ITEM, id, item)`). |

---

## 3. VERSION DELTAS

### 3.1 The architectural trick that makes "balance lands once" true *inside mixins*

A mixin whose target signature changes still has **one** balance implementation, because only the annotation and the parameter list go inside the `//?` block. The logic lives in a shared `@Unique` method:

```java
// SHARED — never inside a conditional block. One implementation, all seven nodes.
@Unique
private float specialities$combatDamage(final float damage, final DamageSource source) {
    ...   // the entire balance behaviour
}
```

Apply this to all 31 injection points. The conditional surface then shrinks to annotation text, which is exactly what Stonecutter is good at.

### 3.2 Worked example — the worst file: `mixin/LivingEntityMixin.java`

Worst because: three `@ModifyVariable` handlers share one injection point; the host method is renamed *and* loses a parameter before 1.21.2; and on legacy the method runs on both logical sides, so a guard the 26.x code does not need becomes mandatory.

Syntax is the template's verified form: `//? if <predicate> {` … `//?} else {` … `//?}`; disabled multi-line branches are wrapped in `/* … */`; **nested** Stonecutter comments inside a disabled block escalate `*` → `^` (`/^? if … {^/`).

```java
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    // ---------- SHARED BALANCE LOGIC: one implementation, every node ----------
    @Unique private float specialities$combatDamage(float damage, DamageSource source)      { /* … */ }
    @Unique private float specialities$uncapFall(float damage, DamageSource source)         { /* … */ }
    @Unique private float specialities$stealthCrit(float damage, DamageSource source)       { /* … */ }

    //? if >=1.21.2 {
    // hurtServer is server-only by construction — no isClientSide guard needed.
    @ModifyVariable(method = "hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z",
                    at = @At("HEAD"), argsOnly = true)
    private float specialities$applyCombatDamage(final float damage, final ServerLevel level,
                                                 final DamageSource source, final float originalDamage) {
        return specialities$combatDamage(damage, source);
    }

    @ModifyVariable(method = "hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z",
                    at = @At("HEAD"), argsOnly = true)
    private float specialities$applyFallUncap(final float damage, final ServerLevel level,
                                              final DamageSource source, final float originalDamage) {
        return specialities$uncapFall(damage, source);
    }

    @ModifyVariable(method = "hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z",
                    at = @At("HEAD"), argsOnly = true)
    private float specialities$applyStealthCrit(final float damage, final ServerLevel level,
                                                final DamageSource source, final float originalDamage) {
        return specialities$stealthCrit(damage, source);
    }
    //?} else {
    /*// 1.20.1 / 1.21.1: hurt(DamageSource,F)Z — no ServerLevel arg, and it runs on BOTH sides.
    // The isClientSide early-out is MANDATORY: MeleeSwing is a server-thread-only static flag.
    @ModifyVariable(method = "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z",
                    at = @At("HEAD"), argsOnly = true)
    private float specialities$applyCombatDamage(final float damage, final DamageSource source,
                                                 final float originalDamage) {
        if (((LivingEntity) (Object) this).level().isClientSide()) return damage;
        return specialities$combatDamage(damage, source);
    }

    @ModifyVariable(method = "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z",
                    at = @At("HEAD"), argsOnly = true)
    private float specialities$applyFallUncap(final float damage, final DamageSource source,
                                              final float originalDamage) {
        if (((LivingEntity) (Object) this).level().isClientSide()) return damage;
        return specialities$uncapFall(damage, source);
    }

    @ModifyVariable(method = "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z",
                    at = @At("HEAD"), argsOnly = true)
    private float specialities$applyStealthCrit(final float damage, final DamageSource source,
                                                final float originalDamage) {
        if (((LivingEntity) (Object) this).level().isClientSide()) return damage;
        return specialities$stealthCrit(damage, source);
    }
    *///?}

    // getVisibilityPercent(Entity)D exists unchanged on ALL five targets — no conditional.
    @ModifyReturnValue(method = "getVisibilityPercent", at = @At("RETURN"))
    private double specialities$sneakVisibility(final double original, final @Nullable Entity targetingEntity) {
        /* … */
    }
}
```

**Ordering hazard to record in-code.** The three handlers share one injection point and Mixin does not guarantee their relative order. They commute today only because all three are multiplications of the same arg — that is luck, not design. Add a comment at the top of the shared block: *"All three handlers MUST remain pure multiplications of `damage`. A non-multiplicative fourth handler silently changes balance."* On 1.20.1 only two remain here (fall protection re-roots — see the table).

### 3.3 Authoritative mixin-target reference

Reproduced from the target-API scout (mojmap `client.txt` greps + `javap -c` of obf classes + the 26.2 sources jar). `AS-IS` = descriptor identical to 26.2.

| Target symbol | 1.20.1 | 1.21.1 | 1.21.11 | Handling |
|---|---|---|---|---|
| `LivingEntity.hurtServer(ServerLevel,DamageSource,F)Z` | `hurt(DamageSource,F)Z`, both sides | `hurt(DamageSource,F)Z`, both sides | **AS-IS** | `//? if >=1.21.2` (§3.2) |
| `LivingEntity.getVisibilityPercent(Entity)D` | AS-IS | AS-IS | AS-IS | none |
| `Player.attack(Entity)V` (`@WrapMethod`) | AS-IS | AS-IS | AS-IS | none; needs MixinExtras ≥0.4 (loader floor) |
| `Player.doSweepAttack(…)V` + `SWEEPING_DAMAGE_RATIO` | **absent**; sweep inline in `attack`, no attribute → target `EnchantmentHelper.getSweepingDamageRatio(LivingEntity)F` (1 site, offset 563) | **absent**; inline in `attack`; attribute exists → `@ModifyExpressionValue` on `Player.getAttributeValue(Holder)D` **`ordinal = 1`** (0 = ATTACK_DAMAGE @627 vs @35) | **AS-IS**, only `getAttributeValue` in method, no ordinal | 3-branch `//?`. **`ordinal=1` on 1.21.1 is mandatory** — ordinal 0 silently multiplies base attack damage |
| `Player.getDestroySpeed(BlockState)F` | AS-IS | AS-IS | AS-IS | none (mixin kept on Forge too, per §2 rule) |
| `Player.getCurrentItemAttackStrengthDelay()F` | AS-IS | AS-IS | AS-IS | none |
| `Player.causeFoodExhaustion(F)V` | AS-IS | AS-IS | AS-IS | none |
| `Player.onEnchantmentPerformed(ItemStack,I)V` | AS-IS | AS-IS | AS-IS | none |
| `Block.getDrops(…,ItemInstance)List` | last arg `ItemStack` | `ItemStack` | `ItemStack` | `replacements` on `>=26.1`: `ItemStack;)Ljava/util/List;` → `ItemInstance;)…`; drop the `instanceof` narrowing |
| `EnchantmentHelper.getEnchantmentLevel(Holder,LivingEntity)I` | raw `Enchantment`, **not the loot path** → retarget `getMobLooting(LivingEntity)I` | AS-IS | AS-IS | `//? if <1.21` |
| `EnchantmentHelper.getFishingLuckBonus(ServerLevel,ItemStack,Entity)I` | `(ItemStack)I` — **no fisher** | AS-IS | AS-IS | **re-root** onto `FishingHook` on 1.20.1 |
| `EnchantmentHelper.getFishingTimeReduction(…)F` | `getFishingSpeedBonus(ItemStack)I` — renamed, **int levels**, no fisher | AS-IS | AS-IS | **re-root**; the `+5.0F*lureBonus` arithmetic does not transfer (return level+bonus) |
| `EnchantmentHelper.getDamageProtection(ServerLevel,LivingEntity,DamageSource)F` | `(Iterable,DamageSource)I` — **no victim**, int | AS-IS | AS-IS | **re-root** onto `CombatRules.getDamageAfterMagicAbsorb` / `actuallyHurt` |
| `CombatRules.getDamageAfterMagicAbsorb(FF)F` (20-pt clamp) | AS-IS | AS-IS | AS-IS | the `points/25` correction math stays valid everywhere |
| `EnchantmentHelper.getItemEnchantmentLevel(Holder,ItemInstance)I` | raw `Enchantment`,`ItemStack` | `Holder`,`ItemStack` | `Holder`,`ItemStack` | covered by the `ItemInstance` replacement + `//? if <1.21` |
| `BowItem.releaseUsing(…)` / `getPowerForTime(I)F` | `…)V` | `…)V` | **`…)Z`** (= 26.2) | bare `"releaseUsing"` needs no change; only if a descriptor is spelled out |
| `CrossbowItem.getChargeDuration(ItemStack,LivingEntity)I` | `(ItemStack)I` — **no user** | AS-IS | AS-IS | 1.20.1 loses per-player charge; needs its own hook |
| `AbstractArrow` package / `onHitEntity` / `baseDamage` | `…projectile.AbstractArrow`, both AS-IS | `…projectile.AbstractArrow` | **`…projectile.arrow.AbstractArrow`** (= 26.2) | `//? if >=1.21.11` on the import; `@Accessor("baseDamage")` valid everywhere (mojmap) |
| `AbstractArrow.getWeaponItem()ItemStack` | **ABSENT ANYWHERE in 1.20.1** | AS-IS | AS-IS | **hard blocker** — stamp the weapon onto the arrow via a transient attachment in Bow/Crossbow/Trident mixins |
| `FishingHook.retrieve(ItemStack)I` + `nibble` | AS-IS | AS-IS | AS-IS | none |
| `ServerPlayer.jumpFromGround()V` | **not on ServerPlayer** | **not on ServerPlayer** | AS-IS | `//? if <1.21.11` → `@Mixin(Player.class)` + `!isClientSide` guard |
| `ResultSlot.onTake` / `FurnaceResultSlot.onTake` | both AS-IS | AS-IS | AS-IS | none (mixin kept on Forge/NeoForge) |
| `BrewingStandMenu.<init>(I,Inventory,Container,ContainerData)V` | AS-IS | AS-IS | AS-IS | none |
| `BrewingStandBlockEntity.doBrew(Level,BlockPos,NonNullList)V` | AS-IS | AS-IS | AS-IS | none |
| `EnchantedItemTrigger.trigger(ServerPlayer,ItemStack,I)V` | pkg `advancements.critereon` | `advancements.critereon` | `advancements.criterion` | **3-way** `//?`: `>=26.2`→`triggers`, `>=1.21.11`→`criterion`, else `critereon` |
| `Mob.getTarget()LivingEntity` | AS-IS | AS-IS | AS-IS | none |
| `AbstractClientPlayer.getFieldOfViewModifier` | `()F` | `()F` | `(ZF)F` (= 26.2) | `//? if >=1.21.11` |
| `AbstractContainerScreen` `leftPos`/`topPos`/`imageWidth` | all AS-IS | AS-IS | AS-IS | none |
| `UseDuration.get(…)` (client, bow pull) | **class absent** | **class absent** | present, `LivingEntity` not `ItemOwner` | 1.21.4+ only → model predicate on 1.21.1/1.20.1 |
| `DataComponents.ENCHANTMENTS` / `ItemEnchantments(.Mutable)` | **ALL ABSENT** | present | present | 1.20.1 → raw NBT rewrite (BlockMixin fortune + Artisan) |
| `Enchantments.*` | raw `Enchantment` instances, **renamed** (FORTUNE→BLOCK_FORTUNE, LOOTING→MOB_LOOTING, LUCK_OF_THE_SEA→FISHING_LUCK, LURE→FISHING_SPEED, FEATHER_FALLING→FALL_PROTECTION) | `ResourceKey`, registry lookup works | same | `//? if <1.21` |
| `Attributes.*` typing | raw `Attribute`; UUID+name modifiers; no SWEEPING_DAMAGE_RATIO / BLOCK_BREAK_SPEED / MINING_EFFICIENCY | `Holder<Attribute>`, all present | same | `//? if <1.21` in `DefencePassives`, `AthleticsTicker` |
| `BlockItemTags` | absent | absent | absent | 26.2-only → write `BlockTags.*`, `//? if >=26.2` block for the 5 ore lookups |
| `ItemTags.SPEARS` | absent | absent | **present** | `//? if >=1.21.11` in `ModTags` + the tag JSON |

### 3.4 Handling for every version-sensitive non-mixin file

| File | Deltas | Mechanism |
|---|---|---|
| `skills/Tuning.java` | none — pure, sole import `ConfigManager` | **shared verbatim.** Fold in `FALL_IMMUNITY_POINTS=25.0F` from `SkillEvents` while restructuring (fixes the documented single-source-of-truth violation) |
| `config/SpecialitiesConfig.java` | none — zero imports | shared verbatim |
| `MeleeSwing`, `api/SkillRegistrar`, `api/SkillsEntrypoint`, `lang/en_us.json` | none | shared verbatim |
| `skills/MaterialValues.java` | every `Items`/`ItemTags` constant exists on all five | shared verbatim |
| `Specialities.java` | `Identifier.fromNamespaceAndPath` vs `new ResourceLocation(...)` | `replacements` (`ResourceLocation`→`Identifier` on `>=1.21.11`) + `//? if <1.21` for the constructor form (template's exact `TemplateModCommon.id()` pattern) |
| `ModTags.java`, `api/SkillType.java` | identifier type only | `replacements` |
| `ModItems.java` | `setId(key)` registry (26.x) vs `Registry.register(Registries.ITEM, id, item)`; tab event | two `//?` blocks |
| `skills/Skill.java` | `Items.IRON_SPEAR` + `minecraft:item/iron_spear` are 26.x-only | `//? if >=26.1` for the ARMS_MASTERY icon; substitute (e.g. `IRON_SWORD`) below |
| `skills/SkillCategories.java` | `BlockItemTags` (26.2), `.arrow` package, `getWeaponItem()` | `//? if >=26.2` for the 5 ore lookups; `//? if >=1.21.11` for the import; `//? if <1.21` for the weapon-source substitute. **Split the XP numbers into constants first** so only the lookups are conditional |
| `skills/SkillEvents.java` | `AFTER_DAMAGE`/`JOIN`/`LEAVE` absent on 0.92.11; `getDamageProtection` 3-arg is 1.21+ | one `//? if <1.21` block over `register()`; this file *is* the event seam |
| `skills/SkillManager.java` | none vanilla | routes through `SkillStore` + `Net` |
| `skills/PlayerSkills.java` | `StreamCodec`/`RegistryFriendlyByteBuf` are 1.20.5+ | `//? if <1.20.5` for `STREAM_CODEC`; the DFU `CODEC` half is portable |
| `SkillUpdatePayload`, `StealthStatePayload` | whole stack is 1.20.5+ | `//? if <1.20.5` → `FabricPacket` bodies; wire ids frozen |
| `skills/DefencePassives.java`, `AthleticsTicker.java` | id-keyed `AttributeModifier` + `Holder<Attribute>` are 1.21+; `MobEffects.SPEED` is 1.21.2+ | `//? if <1.21` / `<1.21.2` |
| `skills/Artisan.java` | `Equippable`/`DataComponents`/`ItemEnchantments.Mutable` (1.21.2+/1.20.5+); Holder enchantments | `//? if <1.21` (NBT path) + `//? if <1.21.2` (equippable) |
| `skills/SneakingTicker.java` | none vanilla | `Net` seam only |
| `command/SkillCommands.java` | `net.minecraft.server.permissions` is 26.x-only | `//? if >=26.1` → `PermissionCheck.Require`, else `.requires(s -> s.hasPermission(2))` |
| `items/SkillBookItem.java` | `InteractionResult` return is 1.21.2+ | `//? if <1.21.2` → `InteractionResultHolder<ItemStack>` |
| `config/ClothConfigScreen`, `ModMenuIntegration` | Fabric-only third-party, per-target artifacts | whole files inside `//? if fabric` + `>=26.1`; Phase B needs `ModConfigSpec`/`ForgeConfigSpec` or nothing |
| **`client/` (7 render files)** | `GuiGraphicsExtractor`/`extractRenderState`/`MouseButtonEvent`/`pose().pushMatrix` are 26.x-only; `HudElementRegistry` absent <1.21.11; `getAtlasManager` 1.21.11+; `AtlasIds` vs `TextureAtlas.LOCATION_ITEMS`; `Screens.getWidgets`→`getButtons`; `gui.setScreen`→`setScreen` | **the real work.** `//? if >=26.1` for extract-vs-immediate bodies; `//? if >=26.2` for `gui.setScreen`/`toastManager()`; `//? if <1.21.11` for the `Gui`-mixin HUD. `BookmarkTab` can extend `AbstractButton` on legacy (`renderWidget` not final) — keep `AbstractWidget` everywhere anyway for one implementation |
| `data/…/tags/item/*.json` | `#minecraft:spears` (26.x-only… **1.21.11+**), `minecraft:mace` (1.21+), copper armor (26.x), `#minecraft:pickaxes` etc. (1.21+); dir `tags/item` vs `tags/items` (1.20.1) | `//?` works in JSON (hash/slash scanners both exist). Simplest robust fix: make every questionable entry `{"id": …, "required": false}` — unknown *optional* entries do not fail tag loading. Directory rename needs per-variant resource override on 1.20.1 |
| `assets/…/items/*.json` (30) | item-model definitions are 1.21.4+ | per-variant override dir for 1.21.1/1.20.1 → `assets/…/models/item/*.json` (parent+textures form) |
| `fabric.mod.json` | version/mc range/loader floor | `processResources` expand of `${id} ${name} ${version} ${minecraft} ${loader}` |
| `*.mixins.json` | `compatibilityLevel` JAVA_25/21/17 | `"compatibilityLevel": "${java}"` + expand. Keep `defaultRequire: 1` — it is the drift detector that forces all 31 injectors to resolve per variant |

---

## 4. BALANCE-CHANGE WORKFLOW

This is the core requirement. After Stage 1, a balance patch is:

```bash
cd /Users/german-mac-mini/repos/mc-modding/specialities

# 1. Edit the numbers. ONE file, no conditionals in it, no per-variant copies.
#    src/main/java/com/specialities/skills/Tuning.java
#    (config defaults/clamps: src/main/java/com/specialities/config/SpecialitiesConfig.java)
#    The skills screen reads Tuning directly, so displayed numbers follow automatically.

# 2. Bump the version in ONE place (replaces bumping gradle.properties on two branches).
#    stonecutter.properties.toml -> mod.version = "1.6.0"

# 3. Build every node. Unqualified task name => runs in all node subprojects.
#    stonecutterGenerate is a dependency; no manual invocation needed.
./gradlew buildAndCollect
#    -> build/libs/1.6.0/specialities-1.6.0+26.2.jar
#                        specialities-1.6.0+26.1.2.jar
#                        specialities-1.6.0+1.21.11.jar   (+ -sources jars)

# 4. Sanity-check each jar boots a dedicated server (see §7).

# 5. Publish all nodes in one ordered pass.
MODRINTH_TOKEN=$(cat ~/.config/modrinth/token) ./gradlew publishMods

# 6. Tag and push.
git commit -am "Balance: <terse summary>" && git tag Release-1.6.0 && git push --follow-tags
```

**Notes that matter:**

- There is **no chiseled task.** `chiseledBuild` was removed; anything describing it is 0.4-era. Aggregation is the unqualified task name plus `stonecutter.tasks.order(...)` for endpoint sequencing (verified against `StonecutterControllerTasks.kt` and a zero-hit `grep -ci chisel` over the 0.9.7 jar).
- `stonecutter active "…"` only chooses which node the **IDE** edits. Building a non-active node needs no switching — each node generates into its own build dir. (Confirm once, 5 min: `./gradlew :1.21.11-fabric:build` while active is `26.2-fabric`.)
- To work on one node in the IDE: `./gradlew stonecutterSwitchTo1.21.11-fabric` (or edit `stonecutter active`).
- `publishMods` must be `mod-publish-plugin` 2.1.1 in each node script, taking the jar as `file = loomx.modJar.flatMap { it.archiveFile }` (works for both loom pipelines) and `modrinth { minecraftVersions.addAll(compatibleVersions) }` from `mod.mc_releases`. Ordering the two child tasks (not `publishMods`) is required — the 0.9.7 KDoc says so explicitly.
- Publish **one Modrinth version per node**, not one version listing five game versions: the loader differs and the jars are genuinely different artifacts. Modrinth tag validation is pre-checked — `/v2/tag/loader` has fabric/forge/neoforge; `/v2/tag/game_version` has all five target strings as `release`.
- Token discipline unchanged: PAT at `~/.config/modrinth/token`, never created or pasted by Claude.
- **Archetypes handshake:** `./gradlew :26.2-fabric:publishToMavenLocal` still yields `com.specialities:specialities:1.5.0` because the publication pins the coordinate to `mod.version` (§1.10). No change needed in the other repo.

---

## 5. IMPLEMENTATION PLAN

### Conflict-bottleneck rule (state this to every sub-agent)

Three files are single-writer bottlenecks — **`settings.gradle.kts`, `stonecutter.gradle.kts`, `stonecutter.properties.toml`**. Any stage that registers a node or adds a predicate touches them. Rule: **node registration is always its own small commit by the stage owner, landed before parallel work starts on that node.** Everything else conflicts only on real source files.

Second rule: the `//?` **predicate vocabulary is frozen in Stage 1** and written into CLAUDE.md — `>=26.2`, `>=26.1`, `>=1.21.11`, `>=1.21.2`, `>=1.21`, `>=1.20.5`, plus loader constants `fabric`/`neoforge`/`forge`. Two agents inventing `>=26` vs `>=26.1` for the same boundary is the most likely silent-divergence bug.

### Stages

**Stage 0 — Toolchain bump on `main`. Sequential, alone. (~1h)**
Gradle 9.5.1→9.6.1, loom 1.17.13→1.17.17, fabric-api 0.154.2→0.155.2+26.2. Build, diff the jar's behaviour, commit alone.
*Why alone:* if the bump breaks the shipping branch, it must be attributable. Folding main into the workspace forces one wrapper for all nodes, so this bump is mandatory anyway.

**Stage 1 — Scaffold the tree with `26.2-fabric` + `26.1-fabric` only. Sequential, one agent. (~1 day)**
Create the six new build files, delete `build.gradle`/`settings.gradle`, shrink `gradle.properties`. `src/` does not move. Express the four known 26.1 deltas as the first `//?` blocks. Convert both mixin configs to `${java}`, `fabric.mod.json` to the expanded props. Freeze the predicate vocabulary + the shared-`@Unique` mixin convention in CLAUDE.md. Fold `FALL_IMMUNITY_POINTS` into `Tuning`.
**Acceptance gate:** both nodes build; each jar is behaviourally identical to its current branch build; `:26.2-fabric:publishToMavenLocal` produces `com.specialities:specialities:1.5.0`; Archetypes still compiles.
*Why alone:* it creates every file every later stage edits.

**Stage 2 — `1.21.11-fabric` beachhead. Node registration sequential, then 4 parallel worktrees. (~3-4 days)**

Beachhead choice confirmed as **`1.21.11-fabric`** — and the scouts' data strengthens the case beyond "one step below the wall":
- `hurtServer`, `doSweepAttack`, `ServerPlayer.jumpFromGround`, the `.arrow` subpackage, `releaseUsing()Z`, `ItemTags.SPEARS`, `MouseButtonEvent`, `getAtlasManager` **all match 26.2** — the mixin layer ports essentially verbatim, so the highest-risk balance surface is untouched.
- fabric-api 0.141.5 ships the **same `hud/` package with identical `HudElementRegistry` arity**, so `HUD_SHIFT` and the Archetypes collision contract survive.
- Attachment (`syncWith` + `AttachmentSyncPredicate`) and payload networking are identical → **no seam needed yet**.
- It is the largest legacy Fabric audience (1,490 fresh packs).
So the beachhead isolates exactly one hard problem: **immediate-mode rendering**. That is the right first fight.

| Sub-stage | Files owned | Parallel? |
|---|---|---|
| 2a | node registration in the 3 bottleneck files | **first, alone** |
| 2b | `SkillCategories`, `ModItems`, `ModTags`, `Skill`, `Specialities`, `command/SkillCommands`, tag JSONs | ✅ |
| 2c | `mixin/*` (11 files) — `BlockMixin` descriptor, `EnchantedItemTriggerMixin` package, `ServerPlayerMixin` retarget, `AbstractArrowMixin` import | ✅ |
| 2d | `client/*` (10 files) — de-extraction to `render(GuiGraphics, DeltaTracker)`, `getButtons`, `setScreen`, `AtlasIds` | ✅ |
| 2e | `client/mixin/*` (3) — `getFieldOfViewModifier(ZF)F`, `UseDuration` | ✅ (2d touches no file here) |

Disjoint file sets → four concurrent worktrees. Cross-set coupling to watch: `client/SkillXpHudBar` reads `SkillIcons` (both in 2d, fine) and `client/mixin/AbstractClientPlayerMixin` reads `AthleticsTicker.sprintModifierId()` (2e reads a 2b-adjacent file — read-only, no conflict).

**Stage 3 — Seams (`SkillStore`, `Net`, `Platform`). Sequential, one agent. (~1-2 days)**
Deliberately **after** the beachhead: 1.21.11 needs none of them, so introducing them earlier would be speculative. Now they are introduced with two proven nodes as regression oracles. Touches `ModAttachments`, `SkillManager`, `Artisan`, 3 main mixins, `Specialities`, `SneakingTicker`, `SpecialitiesClient`, `ConfigManager`, both payloads.
*Why alone:* it edits files owned by 2b, 2c and 2d.

**Stage 4 — Parallel. (~2-3 days)**
- 4a `1.21.1-fabric`: mostly mechanical except the HUD — `HudElementRegistry` absent, so `HUD_SHIFT` needs a `Gui.render(GuiGraphics,DeltaTracker)V` mixin. Owns `client/*` + a new `client/mixin/GuiMixin`.
- 4b Publishing + CI: `mod-publish-plugin` in the node script, controller `order(...)`, `build.yml`. Owns build files + `.github/`. **Zero overlap with 4a.**

**Stage 5 — `1.20.1-fabric`. Sequential-ish, one agent (splittable 3 ways). (~1-2 weeks)**
The five hard blockers land here: no data components (NBT rewrite of `BlockMixin` + `Artisan`), no `getWeaponItem` (weapon-stamping mixins), fishing/protection re-rooting, no attachment sync, no `AFTER_DAMAGE`/`JOIN`/`LEAVE`, raw `Attribute`, no HUD registry. Sub-splittable into (i) persistence+networking+events, (ii) enchantment/NBT, (iii) client HUD — but they interact through `SkillStore`, so prefer one owner with checkpoints.

**Stage 6 — Phase B, parallel, only after Phase A ships. (~2-3 weeks)**
- 6a `1.21.1-neoforge`: `SkillStore`/`Net`/`Platform` NeoForge impls, `RegisterGuiLayersEvent` (its `wrapLayer` makes `HUD_SHIFT` *easier* than fabric-1.21.1), `neoforge.mods.toml`, buildSrc mutex.
- 6b `1.20.1-forge`: capabilities, `SimpleChannel`, JiJ'd `mixinextras-forge:0.5.4`, `RegisterGuiOverlaysEvent` (no wrap → `HUD_SHIFT` needs a decision). **Consider dropping** if 601 packs don't justify it.

---

## 6. RISK REGISTER

| # | Risk (all scout-flagged) | Mitigation / resolving experiment | Size |
|---|---|---|---|
| R-01 | **Arch Loom and Fabric Loom cannot share a buildscript classpath** — same `net.fabricmc.loom` package, same `loom` extension name. `loomx.loom_version` in the *root* `gradle.properties` leaks fabric-loom onto every node and breaks the forge node with "Unresolved reference platform". | Keep it out of root; set it in `stonecutter.properties.toml` (§1.8) or per-node. Verified fix: same build then printed `Fabric Loom: 1.17.17` for fabric nodes and `Architectury Loom: 1.17.491` for `:1.20.1-forge`. **Whoever sets this up will hit this if they put the property in the obvious place.** | resolved |
| R-02 | Shared source cannot express 26.2 + 1.20.1 rendering. `GuiGraphicsExtractor`/`extractRenderState`/`MouseButtonEvent`/`pose().pushMatrix` are 26.x-only; `HudElementRegistry` absent <1.21.11. Not string-replaceable. | Accepted as designed cost: genuine per-version bodies behind `//?`. Scope control = Stage 2 is one node; add nodes only after seams are known. | days per node |
| R-03 | `1.20.1` has no attachment sync (Fabric) and none at all (Forge). | `SkillStore.resyncSkills` + a `SkillsFullPayload` on join; no-op on the five nodes that sync. Designed in. | hours |
| R-04 | `AbstractArrow.getWeaponItem()` absent in 1.20.1 → archery XP routing, ranged damage multiplier and ranged stealth crit have no data source. | Stamp the firing weapon onto the arrow via a transient attachment in Bow/Crossbow/Trident mixins; read back in the damage hooks. | ~1 day |
| R-05 | 1.20.1 `getFishingLuckBonus`/`getFishingSpeedBonus`/`getDamageProtection` have no entity param. | Re-root: fishing onto `FishingHook`, fall protection onto `CombatRules.getDamageAfterMagicAbsorb` (verified AS-IS on all three). | ~1 day |
| R-06 | 1.20.1 has no data components → `BlockMixin` fortune + `Artisan` need an NBT rewrite; `Enchantments.*` renamed and not a registry. | Isolate both behind `//? if <1.21`. | ~2 days |
| R-07 | **`ordinal = 1` on the 1.21.1 sweeping-edge hook.** `Player.attack` has exactly two `getAttributeValue(Holder)D` calls; ordinal 0 is ATTACK_DAMAGE. Getting it wrong silently multiplies base melee damage. | Written into the table; verify per node by exporting transformed classes (`-Dmixin.debug.export=true`, already in the run config) and reading the injected bytecode. | 30 min/node |
| R-08 | Three `@ModifyVariable` handlers share one injection point; order undefined, commutes only because all three are multiplications. | In-code invariant comment (§3.2). A non-multiplicative fourth handler is a balance change. | 5 min |
| R-09 | **NeoForge attachment sync needs ≥21.1.200** (bisected: 0 `sync()` overloads at 21.1.195, 3 at 21.1.200). Docs are stale and say you must send packets yourself. | Pin 21.1.243; assert the floor in the node script. | resolved |
| R-10 | **Forge 1.20.1 does not bundle MixinExtras** (Mixin 0.8.5 only, zero `llamalad7` hits). Breaks 5 mixin files. | `jarJar(implementation("io.github.llamalad7:mixinextras-forge:0.5.4"))` + `compileOnly`/`annotationProcessor`; self-bootstrapping coremod, no manual init. Match 0.5.4 to what loader bundles. | ~1h |
| R-11 | Forge `RegisterGuiOverlaysEvent` has no `replaceLayer`/`wrapLayer` → `HUD_SHIFT=7` (a published Archetypes contract) cannot be implemented by wrapping. | Decide explicitly: `ForgeGui` height fields, cancel via `RenderGuiOverlayEvent$Pre` + redraw shifted, or drop `HUD_SHIFT` on that node. Same decision needed for fabric 1.20.1/1.21.1. **Coordinate with `../archetypes/notes/design.md`.** | ~half day + contract note |
| R-12 | **The official Architectury Stonecutter template is abandoned** (last push 2025-04-28, versions 1.20.1/1.20.6/1.21.1, predates the wall). The hypothesis "Stonecutter + Architectury" should be revised. | Build off `stonecutter-template-multiloader` (pushed 2026-07-17) — which is what §1.6-1.10 are edits of. Arch Loom used narrowly for the one LexForge node. | resolved |
| R-13 | **stonecutter.kikugie.dev serves deliberate nonsense to AI scrapers.** Any summary of those docs from a scraper is untrustworthy. | Read `stonecutter-<ver>-sources.jar` (done) and the template repo (done). Do not WebFetch that site. Record in CLAUDE.md. | resolved |
| R-14 | First full build is a cliff: foojay provisions Temurin 17 (~2 min) and 21, five MC versions download, NeoForge runs a 10-step neoform pipeline. | Copy the template's `buildSrc/.../neoforge-mutex.gradle.kts` (its own comment: prevents "frying your computer"). Do not run the first full build with `org.gradle.parallel=true` and no mutex. Only the *Gradle* JVM must pre-exist; JDK 25 already does. | ~15 min first run |
| R-15 | 1.20.1 caps shared code at **Java 17** (records fine; pattern-matching switch, sealed types, unnamed patterns not). | `sourceCompatibility` per node catches it at compile time on that node. Note the constraint in CLAUDE.md. | ongoing |
| R-16 | Tag JSONs fail loading on unknown entries (`#minecraft:spears`, `mace`, copper armor, `#minecraft:pickaxes`); `tags/item` vs `tags/items` directory on 1.20.1. | Rewrite questionable entries as `{"id":…,"required":false}`; per-variant resource override for the 1.20.1 directory. **Verify the directory name against a real 1.20.1 jar before porting** — flagged unverified by the scout. | 1h + 15 min check |
| R-17 | **Unverified:** ARGB-tinting `blitSprite`/`blit` overload for translucent item sprites on 1.20.1/1.21.1 — the HUD bar's core trick. | Experiment: grep the 1.21.1 and 1.20.1 mojmap `client.txt` for `GuiGraphics` blit overloads; if no tint overload, the bar draws opaque or uses a shader-colour path. **Do this before Stage 4a**, it can change the client design. | ~1h |
| R-18 | **Unverified:** building a non-active Stonecutter node without switching. | `./gradlew :1.21.11-fabric:build` while active is `26.2-fabric`. If it fails, CI/publish needs an explicit switch loop. | 5 min |
| R-19 | **Unverified:** `1.20.1` `LivingEntity.hurt` runs on both sides; the mod's `MeleeSwing` static flag is server-thread-only. The guard is written and the target resolves, but parity was never observed. | Only resolvable at runtime → **user manual check** at Stage 5 (§7). | user |
| R-20 | **Everything the scouts proved stopped at `probe` + dependency resolution.** No node compiled real mod source; no jar was remapped; no mixin was applied; nothing launched. | Treat pins/plumbing as solid and the port as unstarted. Stage 1's acceptance gate is the first real compile. Trials at `…/scratchpad/trialA..trialE` if anyone wants to push them further. | — |
| R-21 | User's Desktop playtest launcher and `docs`/`CLAUDE.md` all break/go stale on restructure. | §1.2 migration table; land the CLAUDE.md rewrite inside Stage 1, not after. | 1h |

---

## 7. TEST / VERIFY STRATEGY

Three tiers. **Claude does tiers 1 and 2 only. The mod is never launched as a client and no game is played.**

### Tier 1 — Build (every node, every stage, automatable)

```bash
./gradlew buildAndCollect                      # all nodes; stonecutterGenerate runs first
./gradlew :<node>:build                        # one node
```

What this actually catches, per node: mojmap resolution, Java level, **and — because both mixin configs keep `injectors.defaultRequire: 1` — every one of the 31 injection points must resolve or the build's mixin-apply stage fails hard.** That is the single most valuable regression signal in this project; keep `defaultRequire: 1` on every variant.

Add per node: `-Dmixin.debug.export=true` (already in the run config) writes transformed classes to `run/.mixin.out/` — the way to confirm R-07's ordinal and the R-08 handler stacking without playing.

### Tier 2 — Headless dedicated-server boot smoke test (Claude-runnable, no client, no gameplay)

```bash
./gradlew :<node>:runServer          # accept EULA once per run dir; kill after boot
```

Assert from `run/logs/latest.log`:

| Check | Signal |
|---|---|
| Mixin application | zero `Mixin apply failed` / `InvalidInjectionException`; with `defaultRequire: 1` a bad target is fatal |
| Common init order | `ConfigManager.load` → `SkillTypes.pullEntrypoints` → `ModAttachments` → `ModItems` → payload registration → `SkillEvents.register` → `SkillCommands.register` (**note: `docs/ARCHITECTURE.md` lists only 5 steps and omits the last — fix the doc**) |
| Skill registry validation | 15 built-ins accepted, no `IllegalArgumentException` from `SkillTypes` |
| Item registration | 30 knowledge books registered |
| Datapack tags | **no tag-loading errors** — this is the check that catches R-16 (`#minecraft:spears`, `mace`, copper armor, `#minecraft:pickaxes`) |
| Config round-trip | `config/specialities.json` written with all five knobs after first boot |
| Commands | `/skillprof` present in the dispatcher (server console `help skillprof`) |
| Clean shutdown | `stop` on stdin exits 0 |

Server boot exercises everything server-authoritative — which is where all the balance code lives. It cannot exercise rendering at all.

### Tier 3 — Manual in-game, user only (per node)

Claude cannot verify any of these; they are the sign-off list to hand the user with each new node.

**Rendering / client (the whole 2d/4a surface):**
1. Skill XP bar draws at the vanilla XP-bar slot, translucent, correct skill colour — **this is where R-17 shows up** (tinted sprite draw).
2. `HUD_SHIFT` — vanilla health/armor/food/air/XP/mount rows raised by 7px, no overlap with the bar. On 1.21.1/1.20.1/forge this is a reimplementation, not a port (R-11).
3. Level-up toast: name, "Increased x → y", icon; challenge jingle only at 50/100.
4. Skills screen: rows, greyed undiscovered, expand arrows, scroll + scissor clipping, tooltips not clipped, Done.
5. Bookmark tab in survival inventory **and** the creative "S" button; both re-anchor when the recipe book opens.
6. Stealth vignette fade-in while hidden, flash on detection.
7. Bow pull animation speed matches actual draw speed (`UseDuration` has no equivalent below 1.21.4).

**Balance parity (compare against a 26.2 world):**
8. Melee/ranged damage multiplier applies on real swings and arrows but **not** on thorns/poison/bleeds.
9. **R-19 specifically on 1.20.1:** melee crit and stealth crit still gated correctly given `hurt` runs on both sides.
10. Fall-protection uncap in the 20→25 point band, and full immunity at ≥25.
11. Passive Fortune on ores/crops/logs; passive Looting on mob drops.
12. Smithing/smelt/alchemy/enchant resourcefulness rolls.
13. Archery ricochet chains only to hostiles, never friendly-fires, passes through its previous victim.

**Persistence (the contract that orphans worlds if broken):**
14. Load an existing 26.2 world in the 26.2 node → all skill XP intact (`specialities:skills`, `Map<String,Integer>`).
15. Die → skills survive (`copyOnDeath`). Rejoin → client HUD and screen show correct values (this is the R-03 `resyncSkills` path on 1.20.1/forge).
16. Interop: with Archetypes installed, its Spellcasting skill still registers and its tab sits to the right of the bookmark (`HEIGHT=15` + `widthFor(label)` contract).