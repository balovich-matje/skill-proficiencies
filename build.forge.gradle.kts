// Node script for EVERY LexForge node. Today that is `1.20.1-forge` only.
//
// THERE IS NO TEMPLATE FOR THIS FILE. The maintained multiloader template ships
// build.fabric.gradle.kts and build.neoforge.gradle.kts and nothing else (checked
// 2026-07-25: `gh api repos/stonecutter-versioning/stonecutter-template-multiloader/contents`
// lists exactly those two), and the abandoned Architectury template predates the wall
// (design R-12). So this script is bespoke, and every line below that is not obvious was
// measured in scratchpad/phase-b-prep/trial-forge.
//
// TRIAL EVIDENCE: trial-forge built green — `Architectury Loom: 1.17.491`,
// `loom.platform=FORGE`, remapJar ran, `BUILD SUCCESSFUL in 16s` — on Gradle 9.6.1 with a
// JDK-25 Gradle JVM and a Java 17 toolchain, against LexForge 1.20.1-47.4.22, with
// mixinextras-forge 0.5.4 jar-in-jar'd and a @WrapOperation/@ModifyReturnValue/@Local mixin
// compiling and remapping.
//
// `//?` does not work in build scripts (conventions §5f).
plugins {
	// Applied DIRECTLY in the node script, not in settings.gradle.kts. Together with
	// `loom.platform=forge` in versions/1.20.1-forge/gradle.properties (read during plugin
	// apply, which is why it is a per-node property file and not a toml key), this is what
	// keeps exactly one loom on this node's buildscript classpath. The other half of that is
	// the R-01 `[fabric]` table in stonecutter.properties.toml — see the measured note there.
	id("dev.architectury.loom") version "1.17.491"
	id("me.modmuss50.mod-publish-plugin") version "2.1.1"
}

version = "${property("mod.version")}+${sc.current.version}"
// Loader suffix, same reason as the NeoForge node: `1.20.1-fabric` and `1.20.1-forge`
// otherwise both produce `specialities-1.5.0+1.20.1.jar`.
base.archivesName = "${property("mod.id") as String}-forge"

// 1.20.1 is Java 17 — which is also the shared-code ceiling (R-15).
val requiredJava: JavaVersion = JavaVersion.VERSION_17

val compatibleVersions: List<String> = sc.properties.rawOrNull("mod", "mc_releases")
	?.asList().orEmpty().map { it.toString() }

repositories {
	// mixinextras-common / mixinextras-forge live on Maven Central. Loom adds its own
	// repositories (MavenRepo included) but naming it here keeps the resolution explicit.
	mavenCentral()
	fun strictMaven(url: String, alias: String, vararg groups: String) = exclusiveContent {
		forRepository { maven(url) { name = alias } }
		filter { groups.forEach(::includeGroup) }
	}
	strictMaven("https://api.modrinth.com/maven", "Modrinth", "maven.modrinth")
}

// MEASURED HARD FAILURE: `loom.splitEnvironmentSourceSets()` throws on the forge platform —
//   > Failed to setup Minecraft, java.lang.UnsupportedOperationException:
//     Using Forge with split jars is not supported!
// (Arch Loom 1.17.491, trial-forge). So `client` is a PLAIN source set inheriting main's
// classpath, exactly as design §1.4 anticipated for the merged-dev-jar loaders. Runtime
// separation is `Dist`/`@OnlyIn`'s job. Declaring the source set is still mandatory: it is
// what makes Stonecutter preprocess `src/client` on this node.
val clientSourceSet: SourceSet = sourceSets.create("client") {
	compileClasspath += sourceSets.main.get().compileClasspath + sourceSets.main.get().output
	runtimeClasspath += sourceSets.main.get().runtimeClasspath + sourceSets.main.get().output
}

loom {
	silentMojangMappingsLicense()

	mods {
		create(sc.properties.get<String>("mod.id")) {
			sourceSet(sourceSets.main.get())
			sourceSet(clientSourceSet)
		}
	}

	forge {
		// LexForge finds mixin configs through the jar MANIFEST attribute `MixinConfigs`
		// (mixin-0.8.5.jar: `Constants$ManifestAttributes.MIXINCONFIGS = "MixinConfigs"`),
		// NOT through mods.toml — Forge's own mods.toml has no [[mixins]] table, unlike
		// NeoForge's. This call is what writes the attribute; measured in trial-forge, whose
		// remapped jar's MANIFEST.MF read `MixinConfigs: trialforge.mixins.json`.
		//
		// BOTH configs are listed, and there is no per-config `environment` key the way
		// fabric.mod.json has. A client mixin whose target is absent on a dedicated server
		// is therefore a boot crash under `injectors.defaultRequire: 1` — this is the single
		// most likely way this node dies on its first Tier-2 run. Same exposure as the
		// NeoForge node.
		mixinConfig(
			"specialities.mixins.json",
			"specialities.client.mixins.json",
		)
	}

	runConfigs.all {
		runDirectory = rootProject.file("run")
		jvmArguments.add("-Dmixin.debug.export=true")
	}
}

dependencies {
	minecraft("com.mojang:minecraft:${sc.current.version}")
	mappings(loom.officialMojangMappings())
	// `forge` is Arch Loom's own configuration name; the string form avoids depending on a
	// generated Kotlin accessor.
	"forge"("net.minecraftforge:forge:${sc.current.version}-${sc.properties.get<String>("deps.forge")}")

	// ---- R-10: MixinExtras is NOT bundled by LexForge, and the design's one-liner is wrong
	// in two ways. MEASURED, from forge-1.20.1-47.4.22-userdev.jar's own config.json library
	// list: it contains `org.spongepowered:mixin:0.8.5` and ZERO llamalad7 entries. The mod
	// needs MixinExtras >= 0.4.0 for its one @WrapMethod plus @WrapOperation /
	// @ModifyReturnValue / @ModifyExpressionValue / @Local across five mixin files.
	//
	// Correction 1 — WHICH ARTIFACT CARRIES WHAT. `mixinextras-forge-0.5.4.jar` is a THIN
	// CONTAINER: `unzip -l` shows 13 entries, namely one class
	// (com/llamalad7/mixinextras/platform/forge/MixinExtrasConfigPlugin), its
	// `mixinextras.init.mixins.json`, `META-INF/jarjar/metadata.json`, and a NESTED
	// `META-INF/jars/MixinExtras-0.5.4.jar` (715 KB) holding the real classes. Its MANIFEST
	// carries `MixinConfigs: mixinextras.init.mixins.json` and `FMLModType: GAMELIBRARY`,
	// which is the "self-bootstrapping, no manual init" R-10 describes. Putting THAT artifact
	// on compileOnly gives the compiler nothing: @WrapOperation is not in it.
	// `mixinextras-common-0.5.4.jar` is the one with the annotations (505 classes, including
	// injector/wrapoperation/WrapOperation, injector/wrapmethod/WrapMethod,
	// injector/ModifyReturnValue, injector/ModifyExpressionValue, sugar/Local) AND with
	// `META-INF/services/javax.annotation.processing.Processor` ->
	// `com.llamalad7.mixinextras.ap.MixinExtrasAP`. So `-common` goes on compileOnly AND
	// annotationProcessor; `-forge` is the jar-in-jar payload and nothing else.
	//
	// Correction 2 — HOW TO JAR-IN-JAR IT. The design writes
	// `jarJar(implementation("…"))`, which is ForgeGradle syntax and does not exist here.
	// Arch Loom's own JiJ configuration is `include`, and it emits the Forge layout:
	// `JarNester.handleForgeJarJar` (javap of architectury-loom 1.17.491). Measured in
	// trial-forge — the remapped jar contained `META-INF/jarjar/metadata.json` naming
	// `io.github.llamalad7:mixinextras-forge` with range `[0.5.4,)` and the file at
	// `META-INF/jars/mixinextras-forge-0.5.4.jar`. Note it is the CONTAINER that gets
	// nested, so the result is doubly nested (our jar -> mixinextras-forge -> MixinExtras);
	// that is the shape MixinExtras publishes for exactly this purpose, but the agent that
	// owns this node must confirm on a real server boot that Forge's JarJarSelector recurses
	// into it and that `mixinextras.init.mixins.json` applies. Neither the trial nor any
	// artifact read can tell us that — it is a runtime fact.
	//
	// Version choice: 0.5.4 is what fabric-loader 0.19.3 bundles, i.e. what the 26.x nodes
	// run against, so the mixin behaviour is the same library everywhere.
	// Pinned in stonecutter.properties.toml as `deps.mixinextras` under [forge."1.20.1"].
	val mixinExtras: String = sc.properties["deps.mixinextras"]
	compileOnly("io.github.llamalad7:mixinextras-common:$mixinExtras")
	annotationProcessor("io.github.llamalad7:mixinextras-common:$mixinExtras")
	"include"("io.github.llamalad7:mixinextras-forge:$mixinExtras")
	// ---- end R-10
}

// LOADER-AXIS EXCLUSIONS (conventions §5e-ter), the mirror of build.fabric.gradle.kts's block:
// this node compiles the Forge seam implementations and neither the Fabric nor the NeoForge
// ones. `Forge*` is anchored, so it does NOT match `NeoForge*` — which is exactly why the two
// loader nodes can exclude each other by glob. The naming rule is documented in the Fabric
// script.
sourceSets["main"].java.exclude(
	"com/specialities/platform/Fabric*.java",
	"com/specialities/platform/NeoForge*.java",
	"com/specialities/platform/SpecialitiesNeoForge.java",
)
sourceSets["client"].java.exclude(
	"com/specialities/client/NeoForge*.java",
)
// Cloth Config / Mod Menu are Fabric-only tooling and are not pinned for this node.
sourceSets["client"].java.exclude("com/specialities/client/config/**")

java {
	withSourcesJar()
	sourceCompatibility = requiredJava
	targetCompatibility = requiredJava
	toolchain {
		languageVersion = JavaLanguageVersion.of(requiredJava.majorVersion)
	}
}

val metadataProps: Map<String, String> = mapOf(
	"id" to sc.properties["mod.id"],
	"name" to sc.properties["mod.name"],
	"version" to project.version.toString(),
	"minecraft_range" to sc.properties["mod.mc_range"],
	"forge_floor" to sc.properties["deps.forge"],
	"java_floor" to requiredJava.majorVersion,
)

tasks.withType<ProcessResources>().configureEach {
	// Mixin 0.8.5's CompatibilityLevel enum tops out at JAVA_18 (javap of mixin-0.8.5.jar:
	// JAVA_6 … JAVA_17, JAVA_18 and nothing above), so `JAVA_17` is a legal value and the
	// `${java}` substitution needs no special case here — which is what design §1.10 predicted.
	//
	// MEASURED CORRECTION, from the Tier-2 boot: the enum having the constant is not the same as
	// the runtime ACCEPTING it. LexForge's bundled Mixin logs, once per config,
	//   [main/WARN] [mixin/]: Compatibility level JAVA_17 specified by specialities.mixins.json
	//   is higher than the maximum level supported by this version of mixin (JAVA_13).
	// and then clamps. It is benign — all 15 common mixins applied in the same run, under
	// `-Dmixin.checks=true -Dmixin.debug.countInjections=true`, with zero injection failures —
	// because the level governs which mixin-class features Mixin will vouch for, not whether ASM
	// can read a class-file-major-61 mixin. The Fabric nodes do not print it: fabric-loader
	// ships sponge-mixin 0.15.x (Mixin 0.8.7), whose supported maximum is higher.
	// DO NOT "fix" this by writing JAVA_13 into the shared config: that would lower the level for
	// every node and change five Fabric jars' resource bytes for a log line.
	val mixinJava = "JAVA_${requiredJava.majorVersion}"
	metadataProps.forEach { (k, v) -> inputs.property(k, v) }
	inputs.property("mixinJava", mixinJava)
	// Conventions §5j: copy-spec ACTIONS are invisible to up-to-date checking, so each
	// conditional transform below declares its decision as an input.
	inputs.property("legacyTagDir", sc.current.parsed < "1.21")
	inputs.property("legacyItemModels", sc.current.parsed < "1.21.4")

	filesMatching("META-INF/mods.toml") { expand(metadataProps) }
	filesMatching("*.mixins.json") { expand("java" to mixinJava) }

	// ---- R-16 + the item-model relocation, BOTH copied from build.fabric.gradle.kts ----
	//
	// Build-script logic does not inherit across node scripts, so these two transforms are
	// duplicated on purpose and the three copies must be kept in step. Long-form reasoning is
	// at build.fabric.gradle.kts's copies. Both are LIVE on this node: the datapack tag
	// directory is PLURAL below 1.21 and 1.20.1's tag loader hardcodes the plural literal, so
	// without the first block the six shared tag files are read by nothing and the R-16
	// cascade silently kills combat XP, arms mastery, the heavy-armor dampener and smithing
	// craft XP; and item MODEL DEFINITIONS (`assets/<ns>/items/`) only exist from 1.21.4, so
	// without the second the thirty knowledge books have no model at all.
	if (sc.current.parsed < "1.21") {
		eachFile {
			if (path.contains("/tags/item/")) {
				path = path.replace("/tags/item/", "/tags/items/")
			}
		}
	}

	if (sc.current.parsed < "1.21.4") {
		// Anchored on `assets/`: the tag rename above has already turned `data/…/tags/item/`
		// into `data/…/tags/items/` by the time this action runs on the same file, and a loose
		// `/items/` test would then mangle the six tag JSONs into item models.
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

	// fabric.mod.json is in the shared src/main/resources and must not ship here.
	exclude("fabric.mod.json")

	// PACK.MCMETA AND MODS.TOML, and where they are NOT: both live as PER-NODE OVERRIDES at
	//   versions/1.20.1-forge/src/main/resources/pack.mcmeta
	//   versions/1.20.1-forge/src/main/resources/META-INF/mods.toml
	// not in the shared `src/main/resources`, and for the same reason in both cases: this node
	// is the only one that wants them, and the shared tree is read by all seven. Fabric needs no
	// pack.mcmeta at all, and neither Fabric nor NeoForge wants `META-INF/mods.toml` — putting
	// either in `src/main/resources` would ship it inside all five Fabric jars and the NeoForge
	// one, moving their resource bytes and breaking the reproduction gate. There is no
	// `exclude("META-INF/mods.toml")` in build.fabric.gradle.kts to lean on, which is exactly
	// why the override route was taken instead of the shared-plus-exclude route that
	// `fabric.mod.json` uses in the other direction (that one predates the loader axis).
	//
	// pack.mcmeta is load-bearing, not cosmetic: without it this loader mounts neither the mod's
	// `assets/` nor its `data/` root, so the six tag JSONs are read by nothing and the R-16
	// cascade fires silently. `pack_format = 15` and no `supported_formats` — the value is what
	// `unzip -p forge-1.20.1-47.4.22-universal.jar pack.mcmeta` reports for Forge's own file.
	// 1.21.1-neoforge's copy is 48 plus `supported_formats`, so these are genuinely two files
	// and not one file with a substituted number.
	//
	// A trap in mods.toml worth knowing before editing it: `expand` runs the WHOLE file, comments
	// included, through Groovy's SimpleTemplateEngine, so a dollar sign anywhere in it is
	// template syntax. A literal `${…}` written inside a comment failed this task with
	// "SimpleTemplateScript1.groovy: Unexpected input: '('", and a bare `Foo$Inner` in a comment
	// would have been a missing-property error. Nothing before processResources catches either.
}

tasks {
	jar {
		val modId: String = sc.properties["mod.id"]
		inputs.property("modId", modId)
		from(rootProject.file("LICENSE")) { rename { "${it}_$modId" } }
		// The `client` source set's output is not in `jar` by default. Measured in
		// trial-forge: adding this put com/trial/client/*.class in the jar and it survived
		// remapJar.
		from(clientSourceSet.output)
	}

	register<Copy>("buildAndCollect") {
		group = "build"
		description = "Builds the mod jar and collects it into build/libs/<mod version>/"
		inputs.property("version", project.property("mod.version"))
		// NOT loomx.modJar — loom-back-compat is not applied on this node. Arch Loom's
		// remapped outputs are `remapJar` / `remapSourcesJar`.
		//
		// MEASURED: the type argument must be `org.gradle.jvm.tasks.Jar`, NOT the `Jar` the
		// Kotlin DSL imports by default. `AbstractRemapJarTask extends
		// org.gradle.jvm.tasks.Jar` (javap of architectury-loom 1.17.491), and
		// `org.gradle.api.tasks.bundling.Jar` is a SUBclass of that, so `named<Jar>("remapJar")`
		// fails at configuration time with "The task 'remapJar'
		// (net.fabricmc.loom.task.RemapJarTask) is not a subclass of the given type". This is
		// the same import loom-back-compat itself uses (LoomCompatPlugin.kt).
		from(
			named<org.gradle.jvm.tasks.Jar>("remapJar").flatMap { it.archiveFile },
			named<org.gradle.jvm.tasks.Jar>("remapSourcesJar").flatMap { it.archiveFile },
		)
		into(rootProject.layout.buildDirectory.file("libs/${project.property("mod.version")}"))
	}
}

// ---------------------------------------------------------------------------
// MODRINTH RELEASE UPLOAD — same shape as the other node scripts, loader-suffixed.
val nodeKey: String = sc.current.project.substringBeforeLast('-')
val modVersion: String = sc.properties["mod.version"]
val modrinthVersion: String = "$modVersion+$nodeKey-forge"

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
		append(" (").append(nodeKey).append(" Forge)")
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
val modrinthNameSuffixLength = " ($nodeKey Forge)".length
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
	// `tasks.` is required here: inside `publishMods { }` the receiver is the extension,
	// so a bare `named<Jar>(...)` does not resolve ("No applicable 'assign' function found").
	file = tasks.named<org.gradle.jvm.tasks.Jar>("remapJar").flatMap { it.archiveFile }
	version = modrinthVersion
	displayName = checkedDisplayName
	changelog = releaseNotes
	type = STABLE
	modLoaders.add("forge")

	modrinth {
		projectId = "d4TtjlpN"
		accessToken = providers.environmentVariable("MODRINTH_TOKEN")
		minecraftVersions.addAll(
			providers.provider {
				compatibleVersions.ifEmpty { error("`mod.mc_releases` is not declared for node ${sc.current.project}") }
			},
		)
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
		"loaders" to "[forge]",
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
