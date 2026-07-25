// Node script for EVERY NeoForge node. Today that is `1.21.1-neoforge` only.
//
// BASE: the maintained multiloader template's own build.neoforge.gradle.kts, fetched
// verbatim 2026-07-25 via
//   gh api repos/stonecutter-versioning/stonecutter-template-multiloader/contents/build.neoforge.gradle.kts
// Deltas from it are marked "DELTA:". Never fetch stonecutter.kikugie.dev (design R-13).
//
// NOTE, same as build.fabric.gradle.kts: Stonecutter `//?` comments do NOT work in build
// scripts (conventions §5f) — use `sc.current.parsed >= "…"`.
//
// TRIAL EVIDENCE for the pins in here: scratchpad/phase-b-prep/trial-neoforge built green
// with ModDevGradle 2.0.142 + neoforge 21.1.243 on Gradle 9.6.1 / JDK-25 Gradle JVM /
// Java 21 toolchain — `BUILD SUCCESSFUL in 1m 44s` from cold, of which 63s was the NeoForm
// pipeline; `compileClientJava` for a second (non-split) `client` source set was green too.
plugins {
	id("net.neoforged.moddev") version "2.0.142"
	// buildSrc script plugin, design R-14. See buildSrc/src/main/kotlin/neoforge-mutex.gradle.kts.
	id("neoforge-mutex")
	// DELTA: mod-publish-plugin, matching build.fabric.gradle.kts. Version pinned here for
	// the same reason it is pinned there — settings.gradle.kts is a single-writer bottleneck.
	id("me.modmuss50.mod-publish-plugin") version "2.1.1"
}

version = "${property("mod.version")}+${sc.current.version}"
// DELTA-CRITICAL: the template's `"${mod.id}-neoforge"` suffix is NOT cosmetic here. Two
// nodes at the same Minecraft version on different loaders would otherwise both produce
// `specialities-1.5.0+1.21.1.jar` and collide in `build/libs/<version>/` — the hazard
// build.fabric.gradle.kts already flags at its `modrinthVersion` comment. Fabric keeps the
// bare `mod.id` so its published file names never change.
base.archivesName = "${property("mod.id") as String}-neoforge"

val requiredJava: JavaVersion = when {
	sc.current.parsed >= "26.1" -> JavaVersion.VERSION_25
	sc.current.parsed >= "1.20.5" -> JavaVersion.VERSION_21
	else -> JavaVersion.VERSION_17
}

val compatibleVersions: List<String> = sc.properties.rawOrNull("mod", "mc_releases")
	?.asList().orEmpty().map { it.toString() }

// DESIGN R-09 AS A BUILD GATE, not as a comment. Data-attachment SYNC — `AttachmentType
// .Builder.sync(...)` and the `AttachmentSync.syncInitialPlayerAttachments` call the patched
// PlayerList makes on login — arrived at NeoForge 21.1.200. `NeoForgeSkillStore.resyncSkills`
// is a NO-OP *because* of it, so a pin below the floor would build, load, and then never give
// any client its skill map: no crash, no log line, an entirely blank HUD bar and skills screen.
// That is the exact failure class this project keeps failing the build on instead
// (conventions §5d's reasoning applied to a dependency pin), hence a configuration-time
// `require` rather than a runtime check.
val neoForgeVersion: String = sc.properties["deps.neoforge"]

run {
	val parts = neoForgeVersion.split('.', '-')
	val major = parts.getOrNull(0)?.toIntOrNull() ?: 0
	val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
	val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
	val hasAttachmentSync = major > 21 || (major == 21 && minor > 1) || (major == 21 && minor == 1 && patch >= 200)
	require(hasAttachmentSync) {
		"deps.neoforge for node ${sc.current.project} is $neoForgeVersion, which is below the " +
			"21.1.200 data-attachment-sync floor (design R-09). Either raise the pin or make " +
			"SkillStore.resyncSkills real on this node — which is a third wire id and needs the user's go-ahead."
	}
}

repositories {
	fun strictMaven(url: String, alias: String, vararg groups: String) = exclusiveContent {
		forRepository { maven(url) { name = alias } }
		filter { groups.forEach(::includeGroup) }
	}
	strictMaven("https://api.modrinth.com/maven", "Modrinth", "maven.modrinth")
}

// DELTA: a `client` source set, created BEFORE the neoForge block because
// `enable { enabledSourceSets = … }` resolves it eagerly (measured: putting it after fails).
//
// This is NOT `loom.splitEnvironmentSourceSets()`. MDG has no such concept — NeoForge dev
// is a merged jar — so `client` is a plain source set that inherits main's classpath, and
// runtime separation is `Dist`/`@OnlyIn`'s job (design §1.4 already says this). Keeping the
// source set at all is what makes Stonecutter preprocess `src/client` on this node
// (StonecutterBuildImpl walks `project.sourceSets.all`), so it is mandatory, not optional.
val clientSourceSet: SourceSet = sourceSets.create("client") {
	compileClasspath += sourceSets.main.get().output
	runtimeClasspath += sourceSets.main.get().output
}

neoForge {
	// `version = "…"` is shorthand for `enable { version = "…" }` (NeoForgeExtension.setVersion,
	// read from moddev-gradle-2.0.142-sources.jar), so the long form is required whenever
	// enabledSourceSets is also set — calling both enables modding twice.
	enable {
		version = sc.properties["deps.neoforge"]
		enabledSourceSets = setOf(sourceSets.main.get(), clientSourceSet)
	}

	mods {
		register(sc.properties.get<String>("mod.id")) {
			sourceSet(sourceSets.main.get())
			sourceSet(clientSourceSet)
		}
	}

	runs {
		register("client") {
			// Same shared run dir the Fabric nodes use, so run/mods/ (player-animation-library,
			// the Archetypes jar) and run/logs/latest.log stay in one place.
			gameDirectory = rootProject.layout.projectDirectory.dir("run")
			client()
		}

		register("server") {
			gameDirectory = rootProject.layout.projectDirectory.dir("run")
			server()
		}
	}
}

// LOADER-AXIS EXCLUSIONS (conventions §5e-ter), the mirror of build.fabric.gradle.kts's block:
// this node compiles the NeoForge seam implementations and neither the Fabric nor the Forge
// ones. The naming rule the globs depend on is documented there.
sourceSets["main"].java.exclude(
	"com/specialities/platform/Fabric*.java",
	"com/specialities/platform/Forge*.java",
	"com/specialities/platform/SpecialitiesForge.java",
)
sourceSets["client"].java.exclude(
	"com/specialities/client/Forge*.java",
)
// The optional Cloth Config / Mod Menu UI is Fabric-only tooling and neither is pinned for this
// node, so those two files have no classpath here either — same reason the Fabric nodes below
// 26.1 exclude them.
sourceSets["client"].java.exclude("com/specialities/client/config/**")

java {
	withSourcesJar()
	sourceCompatibility = requiredJava
	targetCompatibility = requiredJava
	// DELTA: no `vendor = ADOPTIUM`, matching build.fabric.gradle.kts's reasoning — an
	// exact-version local JDK is preferred over a freshly provisioned Temurin. foojay
	// already has 17 and 21 under ~/.gradle/jdks on this machine.
	toolchain {
		languageVersion = JavaLanguageVersion.of(requiredJava.majorVersion)
	}
}

// DELTA: the same metadata map build.fabric.gradle.kts builds, plus the three values only
// the loader-axis metadata files need. Keep the KEY NAMES identical to the Fabric script's
// so the two never drift on `${version}`/`${name}` semantics.
val metadataProps: Map<String, String> = mapOf(
	"id" to sc.properties["mod.id"],
	"name" to sc.properties["mod.name"],
	"version" to project.version.toString(),
	// `mod.mc_compat` is FABRIC range syntax (">=1.21 <=1.21.1"). mods.toml wants a Maven
	// range, so the toml needs its own key per non-Fabric node: `mod.mc_range`.
	"minecraft_range" to sc.properties["mod.mc_range"],
	"neoforge_floor" to neoForgeVersion,
	"java_floor" to requiredJava.majorVersion,
)

tasks.withType<ProcessResources>().configureEach {
	val mixinJava = "JAVA_${requiredJava.majorVersion}"
	metadataProps.forEach { (k, v) -> inputs.property(k, v) }
	inputs.property("mixinJava", mixinJava)
	// Conventions §5j: copy-spec ACTIONS are invisible to up-to-date checking, so every
	// conditional transform below must declare its decision as an input or a node that gains
	// or loses one keeps serving the previous run's output.
	inputs.property("legacyTagDir", sc.current.parsed < "1.21")
	inputs.property("legacyItemModels", sc.current.parsed < "1.21.4")

	// MEASURED TRAP, and it cost two build cycles: `expand` runs the WHOLE file through Groovy's
	// SimpleTemplateEngine, `#` comments included. A dollar sign followed by `{` or by a word is
	// a GString expression wherever it appears, so quoting the placeholder syntax in a comment
	// fails the COPY with "Failed to parse template script … Unexpected input: '('", and naming
	// a nested class with the JVM inner-class separator fails it with "Missing property (Inner)
	// for Groovy template expansion". The metadata file carries the same warning at the top; do
	// not "improve" its prose back into either shape.
	filesMatching("META-INF/neoforge.mods.toml") { expand(metadataProps) }
	// Mixin 0.8.7 ships with NeoForge 21.1.243 (POM: net.fabricmc:sponge-mixin:0.15.2+mixin.0.8.7)
	// and its CompatibilityLevel enum goes up to JAVA_22 (javap of the artifact), so JAVA_21
	// is accepted. The Forge node's Mixin 0.8.5 tops out at JAVA_18 — also measured — which
	// its Java 17 toolchain satisfies.
	filesMatching("*.mixins.json") { expand("java" to mixinJava) }

	// ---- R-16 + the item-model relocation, BOTH copied from build.fabric.gradle.kts ----
	//
	// Build-script logic does not inherit across node scripts, so these two transforms are
	// duplicated on purpose and the three copies must be kept in step. Read the long-form
	// reasoning at build.fabric.gradle.kts's copies; the short version is that the datapack
	// tag directory is PLURAL below 1.21, and item MODEL DEFINITIONS (`assets/<ns>/items/`)
	// only exist from 1.21.4 — below that an item binds to `assets/<ns>/models/item/<id>.json`
	// and the file is a model, not a definition.
	//
	// The first block is inert on 1.21.1 (`>=1.21`); it is written anyway because this script
	// configures every future NeoForge node too. The SECOND block is LIVE on this node:
	// 1.21.1 < 1.21.4, so without it the thirty knowledge books ship with no model at all —
	// the same bug Stage 5 found on 1.21.1-fabric, which no build can catch.
	if (sc.current.parsed < "1.21") {
		eachFile {
			if (path.contains("/tags/item/")) {
				path = path.replace("/tags/item/", "/tags/items/")
			}
		}
	}

	if (sc.current.parsed < "1.21.4") {
		// Anchored on `assets/`: the tag rename above may already have turned
		// `data/…/tags/item/` into `data/…/tags/items/` by the time this action runs on the
		// same file, and a loose `/items/` test would then mangle the six tag JSONs.
		val definitionPath = Regex("""^assets/[^/]+/items/[^/]+\.json$""")
		val modelLine = Regex("""^\s*"model"\s*:\s*"([^"]+)"\s*$""")
		eachFile {
			if (definitionPath.matches(path)) {
				path = path.replace("/items/", "/models/item/")
				filter { line ->
					val match = modelLine.matchEntire(line)
					when {
						match != null -> "\t\"parent\": \"${match.groupValues[1]}\""
						line.contains("\"model\"") || line.contains("\"type\"") -> ""
						line.startsWith("\t") && line.trim() == "}" -> ""
						else -> line
					}
				}
			}
		}
	}

	// DELTA-CRITICAL: fabric.mod.json lives in the SHARED src/main/resources and would ship
	// in this jar. Fabric-only metadata in a NeoForge jar is not fatal, but it is wrong and
	// it defeats the resource-byte gate. The template excludes it for the same reason.
	exclude("fabric.mod.json")

	// EVERY LOADER-ONLY RESOURCE ON THIS NODE IS A PER-NODE OVERRIDE (conventions §4
	// mechanism 2), living under
	//   versions/1.21.1-neoforge/src/{main,client}/resources/…
	// and NOT in the shared `src/`. There are three, and the reason is the same for all three:
	// the shared resource roots ship into every node's jar, `build.fabric.gradle.kts` excludes
	// the loader-only JAVA files by glob but has no resource exclusion, and that script is not
	// this node's to edit — so a shared copy would land in all five Fabric jars and move their
	// resource bytes, which is precisely the reproduction gate this stage has to keep. Verified
	// present in the produced jar with `unzip -l`, since an override that silently does not
	// apply looks exactly like a working one until the game says otherwise.
	//
	//   META-INF/neoforge.mods.toml   the mod metadata. Different FILE NAME from LexForge's
	//                                 META-INF/mods.toml, so the two Phase B nodes need no
	//                                 rename, only their own copies.
	//   pack.mcmeta                   this loader mounts neither assets/ nor data/ without one.
	//                                 Missing it reproduces the R-16 tag cascade by another
	//                                 route, and silently — the §7 Tier-2 positive tag probe is
	//                                 the only automatic detector. `pack_format` 48 +
	//                                 `supported_formats` here vs 15 and none on 1.20.1-forge,
	//                                 both read out of each loader's own universal jar, which is
	//                                 why one shared file with a substituted number was not the
	//                                 shape chosen.
	//   specialities.client.mixins.json   the shared config lists UseDurationMixin, whose target
	//                                 class is >=1.21.11. Same override 1.21.1-fabric already
	//                                 needs; this node's copy differs from that one in NOT
	//                                 listing GuiMixin (see NeoForgeClientEvents' HUD javadoc).
	//
	// Cost, and it is the usual one: an override does not inherit. Anything added to a shared
	// resource that this node needs has to be mirrored here by hand.
}

tasks {
	jar {
		// `project.name` is the node name, so key the license entry off mod.id, exactly as
		// the Fabric script does.
		val modId: String = sc.properties["mod.id"]
		inputs.property("modId", modId)
		from(rootProject.file("LICENSE")) { rename { "${it}_$modId" } }
		// DELTA: the `jar` task only bundles `main` by default. Measured in
		// trial-neoforge: the produced jar contained com/trial/TrialNeoForge*.class and NOT
		// com/trial/client/*.class until this line was added. Without it the whole client
		// half of the mod silently vanishes from the artifact and the mod loads but renders
		// nothing.
		from(clientSourceSet.output)
	}

	// DELTA-CRITICAL, from the template: MDG's artifact task has no idea Stonecutter exists,
	// so on a NON-ACTIVE node (which this always is — the active node is 26.2-fabric and
	// never changes, conventions §2) it would run before the generated sources exist.
	named("createMinecraftArtifacts") {
		dependsOn("stonecutterGenerate")
	}

	register<Copy>("buildAndCollect") {
		group = "build"
		description = "Builds the mod jar and collects it into build/libs/<mod version>/"
		inputs.property("version", project.property("mod.version"))
		// No loomx here — there is no loom on this node and no remap step. MDG produces the
		// final jar as plain `jar`.
		from(jar.flatMap { it.archiveFile }, named<Jar>("sourcesJar").flatMap { it.archiveFile })
		into(rootProject.layout.buildDirectory.file("libs/${project.property("mod.version")}"))
	}
}

// No `publishing { }` block: only the 26.2-fabric node owns the mavenLocal coordinate
// Archetypes depends on (conventions §7).

// ---------------------------------------------------------------------------
// MODRINTH RELEASE UPLOAD — mirrors build.fabric.gradle.kts, with the loader suffix the
// Fabric script's own comment demands ("PHASE B hazard: two nodes at the same Minecraft
// version on different loaders would collide here. The non-Fabric node scripts must add
// their own loader suffix.").
val nodeKey: String = sc.current.project.substringBeforeLast('-')
val modVersion: String = sc.properties["mod.version"]

// ALWAYS suffixed, never bare: the bare `mod.version` belongs to the newest FABRIC node and
// is already published. `isNewestNode` is deliberately not used here.
val modrinthVersion: String = "$modVersion+$nodeKey-neoforge"

val changelogPath = "changelogs/$modVersion.md"
val changelogText: Provider<String> = providers
	.fileContents(rootProject.layout.projectDirectory.file(changelogPath))
	.asBytes.map { String(it, Charsets.UTF_8) }
	.orElse(providers.provider<String> { error("No release notes at $changelogPath — write them before publishing") })

val releaseTitle: Provider<String> = changelogText.map {
	it.trim().lineSequence().firstOrNull()?.takeIf { line -> line.startsWith("# ") }?.removePrefix("# ")?.trim().orEmpty()
}

val releaseNotes: Provider<String> = changelogText.map {
	val lines = it.trim().lines()
	(if (lines.firstOrNull()?.startsWith("# ") == true) lines.drop(1) else lines).joinToString("\n").trim()
}

val modrinthDisplayName: Provider<String> = releaseTitle.map { title ->
	buildString {
		append(modVersion)
		if (title.isNotEmpty()) append(" — ").append(title)
		append(" (").append(nodeKey).append(" NeoForge)")
	}
}

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
val modrinthNameSuffixLength = " ($nodeKey NeoForge)".length
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
	file = tasks.jar.flatMap { it.archiveFile }
	version = modrinthVersion
	displayName = checkedDisplayName
	changelog = releaseNotes
	type = STABLE
	// Literal: this script is build.neoforge.gradle.kts.
	modLoaders.add("neoforge")

	modrinth {
		projectId = "d4TtjlpN"
		accessToken = providers.environmentVariable("MODRINTH_TOKEN")
		minecraftVersions.addAll(
			providers.provider {
				compatibleVersions.ifEmpty { error("`mod.mc_releases` is not declared for node ${sc.current.project}") }
			},
		)
		// NOT featured: four featured Fabric versions per release is already a lot, and this
		// node is new. Flip it only if the user asks.
		featured = false
	}
}

tasks.register("printPublishMetadata") {
	group = "publishing"
	description = "Prints the Modrinth metadata this node would upload. Builds nothing, uploads nothing."
	val rows = listOf(
		"node" to sc.current.project,
		"minecraft (jar)" to sc.current.version,
		"version_number" to modrinthVersion,
		"game_versions" to compatibleVersions.toString(),
		"loaders" to "[neoforge]",
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
