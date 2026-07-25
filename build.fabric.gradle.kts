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
	/** Pulls only the Fabric API modules the mod imports, per node. */
	fun fapi(vararg modules: String) {
		for (it in modules) modImplementation(fabricApi.module(it, sc.properties["deps.fabric_api"]))
	}

	minecraft("com.mojang:minecraft:${sc.current.version}")
	// No-op on 26.x (already unobfuscated); layers Mojang mappings on obfuscated nodes.
	loomx.applyMojangMappings()
	// `mod*` configurations exist on both loom pipelines — loom-back-compat aliases them.
	modImplementation("net.fabricmc:fabric-loader:${property("deps.fabric_loader")}")

	// The nine modules `src/` actually imports. Verified present in both
	// 0.154.2+26.1.2 and 0.155.2+26.2 — including fabric-creative-tab-api-v1, which
	// design §1.10 wrongly gates at >=26.2 (26.1 uses `creativetab.v1` too; there is
	// no ModItems delta between main and the 26.1 branch).
	fapi(
		"fabric-data-attachment-api-v1", // attachment.v1
		"fabric-networking-api-v1", // networking.v1 + client.networking.v1
		"fabric-entity-events-v1", // entity.event.v1
		"fabric-lifecycle-events-v1", // event.lifecycle.v1
		"fabric-events-interaction-v0", // event.player
		"fabric-command-api-v2", // command.v2
		"fabric-screen-api-v1", // client.screen.v1
		"fabric-rendering-v1", // client.rendering.v1.hud (absent < 1.21.11)
		"fabric-creative-tab-api-v1", // creativetab.v1 (fabric-item-group-api-v1 below 26.1)
	)

	// Optional config UI. Compiled against, present in the dev client, soft deps at
	// runtime ("suggests" in fabric.mod.json + isModLoaded guards).
	if (sc.current.parsed >= "26.1") {
		modImplementation("maven.modrinth:modmenu:${property("deps.modmenu")}")
		modImplementation("maven.modrinth:cloth-config:${property("deps.cloth_config")}")
	}
}

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

tasks.withType<ProcessResources>().configureEach {
	val mixinJava = "JAVA_${requiredJava.majorVersion}"
	metadataProps.forEach { (k, v) -> inputs.property(k, v) }
	inputs.property("mixinJava", mixinJava)

	filesMatching("fabric.mod.json") { expand(metadataProps) }
	filesMatching("*.mixins.json") { expand("java" to mixinJava) }
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
