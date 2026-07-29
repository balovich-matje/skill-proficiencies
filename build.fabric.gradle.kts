// Node script for EVERY Fabric node. There are no per-node build scripts and there
// must never be: version-conditional build logic goes in plain Kotlin `if`s below.
//
// NOTE: Stonecutter `//?` comments do NOT work in build scripts — the preprocessor
// only walks the source sets (StonecutterBuildImpl: `project.sourceSets.all { … }`).
// Use `sc.current.parsed >= "…"` here and `//?` only inside `src/`.

plugins {
	// Applies fabric-loom on 26.x (unobfuscated) or fabric-loom-remap below it.
	id("dev.kikugie.loom-back-compat")
	// Only the 26.2 node actually creates a publication (see the bottom of this file);
	// applied unconditionally so the Kotlin DSL accessors exist.
	id("maven-publish")
	// Modrinth uploads, one version per node (see the block at the bottom of this file).
	// The version is pinned HERE and not in `settings.gradle.kts` on purpose: that file is a
	// single-writer bottleneck (conventions §1) and this stage does not own it. Every Fabric
	// node requests the same plugin at the same version, which is what the plugins DSL wants.
	id("me.modmuss50.mod-publish-plugin") version "2.1.1"
}

// DO NOT set `group = …` here — the publication pins its own coordinate.
version = "${property("mod.version")}+${sc.current.version}"
base.archivesName = property("mod.id") as String

val requiredJava: JavaVersion = when {
	sc.current.parsed >= "26.1" -> JavaVersion.VERSION_25
	sc.current.parsed >= "1.20.5" -> JavaVersion.VERSION_21
	else -> JavaVersion.VERSION_17
}

// Game versions this node's jar gets marked compatible with when published.
val compatibleVersions: List<String> = sc.properties.rawOrNull("mod", "mc_releases")
	?.asList().orEmpty().map { it.toString() }

repositories {
	/** Restricts [groups] to one maven so every other lookup skips it. */
	fun strictMaven(url: String, alias: String, vararg groups: String) = exclusiveContent {
		forRepository { maven(url) { name = alias } }
		filter { groups.forEach(::includeGroup) }
	}
	// Mod Menu + Cloth Config (optional config UI, soft deps at runtime).
	strictMaven("https://api.modrinth.com/maven", "Modrinth", "maven.modrinth")
}

loom {
	splitEnvironmentSourceSets()

	mods {
		create("specialities") {
			sourceSet(sourceSets.main.get())
			sourceSet(sourceSets["client"])
		}
	}

	runConfigs.all {
		preferGradleTask = true
		runDirectory = rootProject.file("run") // shared between nodes, incl. run/mods/
		jvmArguments.add("-Dmixin.debug.export=true") // dumps transformed classes to run/.mixin.out
	}
}

dependencies {
	/**
	 * Pulls only the Fabric API modules the mod imports, per node — COMPILE
	 * classpath only. The dev RUNTIME gets the full fabric-api umbrella below
	 * instead: the shipped fabric.mod.json declares `depends: fabric-api`, and
	 * only the umbrella jar carries that mod id — per-module jars declare their
	 * own ids, so a modules-only dev run dies at loader resolution ("requires
	 * any version of fabric-api, which is missing"), which is exactly how the
	 * Desktop launcher failed on 2026-07-26. Modules must NOT also be on the
	 * runtime classpath or every one of them is a duplicate of its copy nested
	 * in the umbrella.
	 */
	fun fapi(vararg modules: String) {
		for (it in modules) modCompileOnly(fabricApi.module(it, sc.properties["deps.fabric_api"]))
	}

	// The whole thing, dev runs only. Production never sees this: users install
	// fabric-api themselves, and the headless smoke servers drop the same jar
	// into mods/.
	modLocalRuntime("net.fabricmc.fabric-api:fabric-api:${sc.properties["deps.fabric_api"] as String}")

	minecraft("com.mojang:minecraft:${sc.current.version}")
	// No-op on 26.x (already unobfuscated); layers Mojang mappings on obfuscated nodes.
	loomx.applyMojangMappings()
	// `mod*` configurations exist on both loom pipelines — loom-back-compat aliases them.
	modImplementation("net.fabricmc:fabric-loader:${property("deps.fabric_loader")}")

	// Eight of the nine modules `src/` imports, spelled the same on every node so far.
	// Verified present in 0.141.5+1.21.11, 0.154.2+26.1.2 and 0.155.2+26.2 (bundle POMs).
	fapi(
		"fabric-data-attachment-api-v1", // attachment.v1
		"fabric-networking-api-v1", // networking.v1 + client.networking.v1
		"fabric-entity-events-v1", // entity.event.v1
		"fabric-lifecycle-events-v1", // event.lifecycle.v1
		"fabric-events-interaction-v0", // event.player
		"fabric-command-api-v2", // command.v2
		"fabric-screen-api-v1", // client.screen.v1
		"fabric-rendering-v1", // client.rendering.v1.hud (absent < 1.21.11)
	)

	// The ninth is the ONE module that is renamed across the range, so it is a swap and
	// not an addition: no fabric-api version ships both names. `fabric-item-group-api-v1`
	// (4.2.36 in 0.141.5+1.21.11, `api.itemgroup.v1.ItemGroupEvents`) becomes
	// `fabric-creative-tab-api-v1` (`api.creativetab.v1.CreativeModeTabEvents`) from 26.1
	// — design §1.10 puts that boundary at >=26.2, which is wrong: 26.1 already uses
	// creativetab.v1 (checked in 0.154.2+26.1.2's POM, hence no ModItems delta between
	// main and the old 26.1 branch).
	if (sc.current.parsed >= "26.1") {
		fapi("fabric-creative-tab-api-v1") // creativetab.v1
	} else {
		fapi("fabric-item-group-api-v1") // itemgroup.v1
	}

	// Optional config UI. Compiled against, present in the dev client, soft deps at
	// runtime ("suggests" in fabric.mod.json + isModLoaded guards).
	if (sc.current.parsed >= "26.1") {
		modImplementation("maven.modrinth:modmenu:${property("deps.modmenu")}")
		modImplementation("maven.modrinth:cloth-config:${property("deps.cloth_config")}")
	}
}

// Companion to the Mod Menu / Cloth Config gate above: those two source files import
// `me.shedaniel.clothconfig2.api.*` and `com.terraformersmc.modmenu.api.*`, so below 26.1
// they have no classpath and must leave the source set entirely. This is done here rather
// than with a `//?` block wrapping each file because both carry `/** … */` javadoc: a
// hand-written disabled branch would contain a `*/` that closes the branch comment early,
// which needs the `*` -> `^` marker escalation of conventions §4 and is easy to get
// silently wrong. `fabric.mod.json`'s `modmenu` entrypoint is gated to match.
if (sc.current.parsed < "26.1") {
	sourceSets["client"].java.exclude("com/specialities/client/config/**")
}

// LOADER-AXIS EXCLUSIONS (conventions §5e-ter), and they must be here BEFORE the first
// NeoForge/Forge file lands or every Fabric node breaks the moment one does.
//
// The three seam implementations are mutually exclusive: `platform/{SkillStore,Net,Platform}`
// pick theirs with an inline `//? if fabric / elif neoforge / elif forge` on the INSTANCE
// initializer, and the two classes a node does not pick name loader API it has no classpath
// for. Same for each loader's `@Mod` entrypoint and its event/client helpers.
//
// NAMING RULE these globs depend on: a file that exists for one loader only is named after
// that loader — `NeoForge*` / `Forge*` in `com/specialities/platform/` and
// `com/specialities/client/`, plus the two entrypoint classes. `Forge*` does not match
// `NeoForge*` (the pattern is anchored), which is what lets each node exclude exactly the
// other's files.
sourceSets["main"].java.exclude(
	"com/specialities/platform/NeoForge*.java",
	"com/specialities/platform/Forge*.java",
	"com/specialities/platform/SpecialitiesNeoForge.java",
	"com/specialities/platform/SpecialitiesForge.java",
)
sourceSets["client"].java.exclude(
	"com/specialities/client/NeoForge*.java",
	"com/specialities/client/Forge*.java",
)

java {
	// Loom attaches this to remapSourcesJar and to `build` automatically.
	withSourcesJar()
	sourceCompatibility = requiredJava
	targetCompatibility = requiredJava

	// Deliberately no `vendor = ADOPTIUM`: an exact-version local JDK is preferred,
	// which keeps 26.x bytecode identical to the shipped 1.5.0 jars instead of
	// recompiling them with a freshly provisioned Temurin.
	toolchain {
		languageVersion = JavaLanguageVersion.of(requiredJava.majorVersion)
	}
}

// fabric.mod.json lives in `main`, but specialities.client.mixins.json lives in
// `client` — with split source sets there are TWO ProcessResources tasks and both
// need the substitutions, or the client mixin config ships a literal `${java}`.
val metadataProps: Map<String, String> = mapOf(
	"id" to sc.properties["mod.id"],
	"name" to sc.properties["mod.name"],
	"minecraft" to sc.properties["mod.mc_compat"],
	"loader" to sc.properties["mod.loader_floor"],
	// `1.5.0+26.2`, not bare `1.5.0` — the jar has to name its game version.
	"version" to project.version.toString(),
	// fabric.mod.json wants `>=25`; the mixin configs want `JAVA_25`.
	"java_floor" to requiredJava.majorVersion,
)

// Below 26.1 `client/config/**` leaves the source set (see the Mod Menu dependency gate),
// so fabric.mod.json's `modmenu` entrypoint would name a class that is not in the jar.
// Fabric resolves entrypoints lazily, so nothing breaks while Mod Menu is absent — but Mod
// Menu ships for 1.21.x, and a user who installed it would get a ClassNotFoundException the
// moment it queried the entrypoint. Blanking the class line leaves `"modmenu": []`, which is
// valid JSON and inert.
//
// Three mechanisms were rejected for this, and the reasons are worth keeping:
//   * A `//?` block. Conventions §4 says "`//?` also works in JSON". That holds for the
//     *mixin* configs — Mixin reads those with `new Gson().fromJson(Reader, Class)`, and
//     Gson's read path flips the reader to lenient, so `//` and `/* */` survive — but it is
//     FALSE for fabric.mod.json. Fabric parses that with its own bundled JsonReader:
//     `lenient = false` in the constructor (`iconst_0`), never set true anywhere in
//     ModMetadataParser, and `checkLenient()` throws on a leading `/`. Verified by javap of
//     fabric-loader 0.19.3. A leftover directive line there is an unloadable mod.
//   * An `expand` placeholder for the entry. Measured: it makes the *raw* template invalid
//     JSON, and Loom parses that file at configuration time to auto-detect the mod id —
//     "Failed to parse fabric.mod.json" appears for every node, including 26.x.
//   * A `versions/<node>/src` override. Duplicates the whole file, so the two copies drift.
// Doing it here instead means the 26.x nodes get no transform at all, which is what keeps
// their jars byte-identical to the shipped 1.5.0 resources.
val strippedEntrypoints: List<String> =
	if (sc.current.parsed >= "26.1") emptyList()
	else listOf("com.specialities.client.config.ModMenuIntegration")

tasks.withType<ProcessResources>().configureEach {
	val mixinJava = "JAVA_${requiredJava.majorVersion}"
	metadataProps.forEach { (k, v) -> inputs.property(k, v) }
	inputs.property("mixinJava", mixinJava)

	inputs.property("strippedEntrypoints", strippedEntrypoints)

	// Copy-spec ACTIONS are not task inputs — `eachFile`/`filter` blocks are invisible to
	// up-to-date checking, so without these two properties a node that gained or lost either
	// transform below would keep serving the previous run's output. Measured: the first build
	// after the transforms were added reported processResources UP-TO-DATE and shipped the
	// untransformed resources.
	inputs.property("legacyTagDir", sc.current.parsed < "1.21")
	inputs.property("legacyItemModels", sc.current.parsed < "1.21.4")

	filesMatching("fabric.mod.json") {
		expand(metadataProps)
		if (strippedEntrypoints.isNotEmpty()) {
			filter { line -> if (strippedEntrypoints.any(line::contains)) "" else line }
		}
	}
	filesMatching("*.mixins.json") { expand("java" to mixinJava) }

	// ---- R-16: the datapack tag directory is PLURAL below 1.21 ----
	//
	// The flip is at exactly 1.21 (last plural release 1.20.6, first singular 1.21), and
	// 1.20.1's tag loader hardcodes the plural literal, so the six shared tag files are read
	// by NOTHING on that node unless they move. Done here rather than as a per-node override
	// so there stays exactly ONE copy of the six JSONs: `FileCopyDetails.path` is settable,
	// the nodes at or above 1.21 get no transform at all (which is what keeps their resource
	// bytes identical), and `unzip -l` on the produced jar confirms it.
	//
	// The six files' own content needs nothing: the entries that do not resolve on 1.20.1 —
	// the four copper armour pieces, `#minecraft:spears` and `minecraft:mace` — are already
	// `{"id": …, "required": false}` in the shared tree, landed with the 1.21.1 node.
	if (sc.current.parsed < "1.21") {
		eachFile {
			if (path.contains("/tags/item/")) {
				path = path.replace("/tags/item/", "/tags/items/")
			}
		}
	}

	// ---- Item MODEL DEFINITIONS are 1.21.4+; below that they are plain item models ----
	//
	// `assets/<ns>/items/<id>.json` (the model-definition layer, one per item id) arrived in
	// 1.21.4. Below it an item is bound to `assets/<ns>/models/item/<id>.json` by id, and the
	// file is a model, not a definition. All thirty of this mod's definitions do the same
	// trivial thing — name one vanilla model — so the conversion is mechanical:
	//
	//     {"model": {"type": "minecraft:model", "model": "minecraft:item/book"}}
	//  -> {"parent": "minecraft:item/book"}
	//
	// which is a path move plus a two-line-to-one-line rewrite. The dropped lines are blanked
	// rather than removed (Gradle's line filter cannot delete a line without a nullable
	// transformer) — JSON does not care, and it keeps the diff obvious in the built jar.
	//
	// NOTE the version number: the real boundary is 1.21.4, which is NOT one of the frozen
	// `//?` predicates because no registered node sits between 1.21.2 and 1.21.11. This is a
	// build script, where §5f already requires plain Kotlin comparisons, so the true boundary
	// is written instead of the nearest frozen one — a future 1.21.4-1.21.10 node then
	// behaves correctly with no edit here.
	//
	// This also fixes the 1.21.1 node, where the thirty books have had no model at all since
	// Stage 4a: `items/` was shipped verbatim, which that version does not read. It has never
	// been visible because no client on any node below 26.2 has ever been launched.
	if (sc.current.parsed < "1.21.4") {
		// Anchored on `assets/`: the tag rename above has already turned `data/…/tags/item/`
		// into `data/…/tags/items/` by the time this action runs on the same file, and a
		// loose `/items/` test would then mangle the six tag JSONs into item models.
		val definitionPath = Regex("""^assets/[^/]+/items/[^/]+\.json$""")
		val modelLine = Regex("""^\s*"model"\s*:\s*"([^"]+)"\s*$""")
		eachFile {
			if (definitionPath.matches(path)) {
				path = path.replace("/items/", "/models/item/")
				filter { line ->
					val match = modelLine.matchEntire(line)
					when {
						// the inner `"model": "<id>"` becomes the whole file's body
						match != null -> "\t\"parent\": \"${match.groupValues[1]}\""
						// the `"model": {` wrapper, its `"type"`, and its own closing brace
						line.contains("\"model\"") || line.contains("\"type\"") -> ""
						line.startsWith("\t") && line.trim() == "}" -> ""
						else -> line
					}
				}
			}
		}
	}
}

tasks {
	jar {
		// `project.name` is the NODE name here ("26.2-fabric"), so the license entry
		// has to be keyed off mod.id to stay `LICENSE_specialities`.
		val modId: String = sc.properties["mod.id"]
		inputs.property("modId", modId)
		from(rootProject.file("LICENSE")) { rename { "${it}_$modId" } }
	}

	register<Copy>("buildAndCollect") {
		group = "build"
		description = "Builds the mod jar and collects it into build/libs/<mod version>/"
		inputs.property("version", project.property("mod.version"))
		// loomx.mod(Sources)Jar resolves to jar/sourcesJar or remapJar/remapSourcesJar.
		from(loomx.modJar.flatMap { it.archiveFile }, loomx.modSourcesJar.flatMap { it.archiveFile })
		into(rootProject.layout.buildDirectory.file("libs/${project.property("mod.version")}"))
	}
}

// Archetypes compiles against `com.specialities:specialities:<mod.version>` from
// mavenLocal. Exactly ONE node may own that coordinate; the version string must stay
// bare (`1.5.0`, not `1.5.0+26.2`) so the other repo needs no change.
publishing {
	if (sc.current.parsed >= "26.2") {
		publications.create<MavenPublication>("mavenJava") {
			from(components["java"])
			groupId = sc.properties["mod.group"]
			artifactId = sc.properties["mod.id"]
			version = sc.properties["mod.version"]
		}
	}
}

// ---------------------------------------------------------------------------
// MODRINTH RELEASE UPLOAD — one Modrinth version per node.
//
// Plugin: `me.modmuss50.mod-publish-plugin` 2.1.1. Both facts were read off the artifact,
// not from memory: 2.1.1 is `<release>` in the plugin portal's
// me/modmuss50/mod-publish-plugin/…/maven-metadata.xml (checked 2026-07-25), and the id plus
// the `publishMods` extension/task names come from the jar's
// META-INF/gradle-plugins/me.modmuss50.mod-publish-plugin.properties and `MppPlugin`.
// The plugin registers one `PublishModTask` per platform — ours is `publishModrinth` — and an
// aggregate `publishMods` that depends on it. `stonecutter.gradle.kts` orders `publishModrinth`
// across nodes (the aggregate is not an endpoint task, so ordering it would do nothing).
//
// NOTHING here can run during a normal build: both tasks are in the `publishing` group and no
// lifecycle task depends on them. Beyond that, the CHECKED-IN configuration is a DRY RUN —
// a real upload needs BOTH `-PpublishLive=true` and a `MODRINTH_TOKEN` in the environment.
// The token is never checked in and never printed; see docs/MULTIVERSION.md §4 for the exact
// release command sequence.

/** The `<version>` half of the node name: `26.1-fabric` -> `26.1`. */
val nodeKey: String = sc.current.project.substringBeforeLast('-')

/** True when no registered node targets a newer Minecraft version than this one. */
val isNewestNode: Boolean = sc.versions.none { it.parsed > sc.current.parsed }

val modVersion: String = sc.properties["mod.version"]

// The version number must keep the scheme the published releases already use — read back off
// `GET /v2/project/d4TtjlpN/version` rather than reconstructed: the newest node uploads the
// BARE mod version (`1.5.0`, game version 26.2) and every older node appends its node key
// (`1.5.0+26.1`, game versions 26.1/26.1.1/26.1.2). Note that is the NODE KEY, not
// `sc.current.version`: the 26.1 node's Minecraft version is 26.1.2, but every published 26.1
// version number has been `+26.1`. The jar file name keeps `project.version` (with the full
// `+26.1.2`) and is deliberately unaffected — Modrinth does not care what the file is called.
// PHASE B: two nodes at the same Minecraft version on different loaders would collide here.
// The non-Fabric node scripts must add their own loader suffix.
val modrinthVersion: String = if (isNewestNode) modVersion else "$modVersion+$nodeKey"

// Release notes live in `changelogs/<mod.version>.md` — ONE file for every node, so the notes
// cannot drift between the four uploads of a release. If the first line is an `# H1` it becomes
// the Modrinth version name and is stripped from the body; everything else is uploaded verbatim
// (the user's changelog style is terse bullets and nothing else). Read as bytes and decoded as
// UTF-8 explicitly: the notes contain `—` and `→`, and `asText` would use the platform default.
val changelogPath = "changelogs/$modVersion.md"
val changelogText: Provider<String> = providers
	.fileContents(rootProject.layout.projectDirectory.file(changelogPath))
	.asBytes.map { String(it, Charsets.UTF_8) }
	.orElse(providers.provider<String> { error("No release notes at $changelogPath — write them before publishing") })

/** The `# H1` title of the release notes, or `""` when there is none. */
val releaseTitle: Provider<String> = changelogText.map {
	it.trim().lineSequence().firstOrNull()?.takeIf { line -> line.startsWith("# ") }?.removePrefix("# ")?.trim().orEmpty()
}

/** The release notes with the title line removed. */
val releaseNotes: Provider<String> = changelogText.map {
	val lines = it.trim().lines()
	(if (lines.firstOrNull()?.startsWith("# ") == true) lines.drop(1) else lines).joinToString("\n").trim()
}

/** Matches the published naming: `1.5.0 — <title>`, plus ` (<node>)` on the older nodes. */
val modrinthDisplayName: Provider<String> = releaseTitle.map { title ->
	buildString {
		append(modVersion)
		if (title.isNotEmpty()) append(" — ").append(title)
		if (!isNewestNode) append(" (").append(nodeKey).append(')')
	}
}

// A live upload is opt-in, per invocation. `PublishModTask` copies the extension's `dryRun` into
// itself and finalises it as the task is created, so this has to be set on the extension.
val publishLive: Boolean = providers.gradleProperty("publishLive").map(String::toBoolean).getOrElse(false)

// MODRINTH'S 64-CHARACTER VERSION-NAME CAP, AS A BUILD GATE — measured against the LIVE 1.5.0
// versions, not read off the docs. The published `1.5.0+26.1` (`GET /v2/version/on9OIUum`) is
// named `1.5.0 — Bonuses only on real weapon attacks (26.1)`, 50 chars, while the published
// `1.5.0` (26.2) is named `1.5.0 — Combat & stealth bonuses only on real weapon attacks`, 60
// chars. The two live names carry DIFFERENT titles, and the short one is exactly the node whose
// ` (26.1)` suffix would have pushed the long title to 67 — so the cap is real and it was
// already worked around by hand at 1.5.0. `changelogs/<version>.md` holds ONE H1, so one title
// now has to fit EVERY node's suffix; the longest is ` (1.21.1 NeoForge)` at 18 characters.
//
// Deliberately NOT auto-truncated: silently renaming a release is what §4.1 forbids. And it
// fails the LIVE upload only, so the dry run stays usable as the pre-flight that prints the
// numbers — `printPublishMetadata` reports `name length` and marks anything over.
//
// The title budget is arithmetic on plain Strings on purpose. Reading `releaseTitle` here would
// move the "no release notes" failure from task time to CONFIGURATION time, i.e. a missing
// changelog would break `./gradlew build` (§4.1 is explicit that it must not).
val modrinthNameMax = 64
val modrinthNameSuffixLength = if (isNewestNode) 0 else " ($nodeKey)".length
val modrinthTitleBudget = modrinthNameMax - modVersion.length - " — ".length - modrinthNameSuffixLength

val checkedDisplayName: Provider<String> = modrinthDisplayName.map { name ->
	require(!publishLive || name.length <= modrinthNameMax) {
		"Modrinth caps a version name at $modrinthNameMax characters; this one is ${name.length}: " +
			"\"$name\". Shorten the `# ` title line in $changelogPath to at most " +
			"$modrinthTitleBudget characters — that is the budget every node can carry."
	}
	name
}


publishMods {
	dryRun = !publishLive
	// The mod jar for this node — `loomx.modJar` resolves to `jar` (26.x) or `remapJar`
	// (obfuscated nodes), and carries the task dependency with it. NEVER the `-sources` jar:
	// `additionalFiles` is deliberately left empty.
	file = loomx.modJar.flatMap { it.archiveFile }
	version = modrinthVersion
	displayName = checkedDisplayName
	changelog = releaseNotes
	type = STABLE
	// Literal: this script is `build.fabric.gradle.kts`, so every node it configures is Fabric.
	modLoaders.add("fabric")

	modrinth {
		projectId = "d4TtjlpN" // slug `skill-proficiencies`; the id is frozen, the slug is not
		// Read from the environment at publish time. The PAT lives at ~/.config/modrinth/token
		// and is never checked in, never printed, and never created by tooling.
		accessToken = providers.environmentVariable("MODRINTH_TOKEN")
		// `mod.mc_releases` from stonecutter.properties.toml — the 26.1 node's jar covers
		// 26.1/26.1.1/26.1.2, the 1.21.1 node's covers 1.21/1.21.1. Lazy so a node that forgot
		// the key fails when publishing rather than when building.
		minecraftVersions.addAll(
			providers.provider {
				compatibleVersions.ifEmpty { error("`mod.mc_releases` is not declared for node ${sc.current.project}") }
			},
		)
		// Matches every release so far. Modrinth keeps older featured versions featured, so if
		// the project page gets crowded once four nodes ship, this is the knob to turn off.
		featured = true
		// `environment` and `requires(...)` are deliberately unset: no published version of this
		// project declares either, and a publishing stage must not change release metadata.
	}
}

// Pre-flight check for the release: prints exactly what each node would upload, without
// building a jar or touching the network. This is the only way to inspect a node whose jar
// cannot be produced yet, which is why it exists alongside the plugin's own dry run.
tasks.register("printPublishMetadata") {
	group = "publishing"
	description = "Prints the Modrinth metadata this node would upload. Builds nothing, uploads nothing."
	// Everything the action needs is captured here, at configuration time — the action itself
	// touches no project state, and the token is reported as present/absent, never printed.
	val rows = listOf(
		"node" to sc.current.project,
		"minecraft (jar)" to sc.current.version,
		"version_number" to modrinthVersion,
		"game_versions" to compatibleVersions.toString(),
		"loaders" to "[fabric]",
		"changelog file" to changelogPath,
		"mode" to if (publishLive) "LIVE UPLOAD" else "dry run",
		"MODRINTH_TOKEN" to if (providers.environmentVariable("MODRINTH_TOKEN").isPresent) "present" else "absent",
	)
	val name = modrinthDisplayName
	val notes = releaseNotes
	doLast {
		rows.forEach { (k, v) -> logger.lifecycle("%-16s %s".format(k, v)) }
		val rendered = name.get()
		logger.lifecycle("%-16s %s".format("name", rendered))
		logger.lifecycle("%-16s %d / %d%s".format("name length", rendered.length, modrinthNameMax,
			if (rendered.length > modrinthNameMax) "   *** OVER THE CAP — a live upload will be refused ***" else ""))
		logger.lifecycle("--- changelog ---")
		logger.lifecycle(notes.get())
	}
}
