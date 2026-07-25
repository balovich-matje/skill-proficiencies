pluginManagement {
	repositories {
		mavenCentral()
		gradlePluginPortal()
		maven("https://maven.fabricmc.net/") { name = "FabricMC" }
		maven("https://maven.kikugie.dev/releases") { name = "KikuGieReleases" }
		// Phase B (loader axis) mavens. Kept here so adding a NeoForge/Forge node is a
		// one-line change in the tree below. `maven.minecraftforge.net` is NOT optional
		// for Architectury Loom: its own buildscript classpath needs
		// `de.oceanlabs.mcp:mcinjector` from there (design R-01 / §1.6).
		maven("https://maven.neoforged.net/releases/") { name = "NeoForged" }
		maven("https://maven.architectury.dev/") { name = "Architectury" }
		maven("https://maven.minecraftforge.net/") { name = "Forge" }
	}
}

plugins {
	id("dev.kikugie.stonecutter") version "0.9.7"
	// Picks fabric-loom (26.x, unobfuscated) or fabric-loom-remap (<=1.21.11) per node.
	id("dev.kikugie.loom-back-compat") version "0.4.1"
	// Provisions the JDK a node's toolchain asks for (17/21/25).
	id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

stonecutter {
	create(rootProject) {
		/**
		 * Creates `versions/<project>-<loader>` nodes, each on `build.<loader>.gradle.kts`.
		 * `project` is the folder name part, `version` is the real Minecraft version.
		 */
		fun match(project: String, vararg loaders: String, version: String = project) {
			for (loader in loaders) version("$project-$loader", version).buildscript("build.$loader.gradle.kts")
		}

		// ---- Phase A: both shipping versions and the three legacy nodes. ----
		// ---- Phase B: the loader axis, one node per loader. See docs/MULTIVERSION.md §1.3.
		match("26.2", "fabric")
		match("26.1", "fabric", version = "26.1.2")
		// First OBFUSCATED node. Nothing extra is needed to get the remap pipeline:
		// loom-back-compat asks Stonecutter to compare the node version against "26"
		// and applies `net.fabricmc.fabric-loom-remap` when it sorts below
		// (LoomCompatProjectExtension.isUnobfuscated, read out of the 0.4.1 bytecode —
		// both plugin ids ship in the same fabric-loom 1.17.17 jar, so `fabricApi.module`
		// and `officialMojangMappings` exist on either pipeline).
		match("1.21.11", "fabric")
		// FIRST NON-FABRIC NODE (Phase B), registered as its own commit per the
		// bottleneck-file rule (conventions §1). `build.neoforge.gradle.kts` is a second node
		// script, not a fork of the Fabric one — ModDevGradle instead of Loom, a plain
		// `client` source set instead of split environments, `META-INF/neoforge.mods.toml`
		// instead of `fabric.mod.json`.
		//
		// The shared tree does NOT compile for this node yet: `com.specialities.platform`
		// still wires its `INSTANCE`s to the Fabric impls and there is no `@Mod` entrypoint,
		// so `:1.21.1-neoforge:build` FAILS by design until the NeoForge half lands.
		// `:1.21.1-neoforge:stonecutterGenerate` and `:printPublishMetadata` are green, and
		// every Fabric node is unaffected.
		match("1.21.1", "fabric", "neoforge")
		// Registered by Stage 5 as its own commit (the bottleneck-file rule, conventions
		// §1). The shared tree does NOT compile for this node yet — the `//?` forks
		// §3.3/§3.4 queue up for it are the rest of Stage 5 — so `./gradlew build` and
		// `buildAndCollect` FAIL until they land. Per-node tasks on the four older nodes
		// are unaffected and `:1.20.1-fabric:stonecutterGenerate` is green.
		//
		// This is the ONLY Java-17 node (piston-meta javaVersion.majorVersion = 17 for
		// 1.20.1), which is what makes conventions §5e enforceable: from here on the
		// shared tree is compiled at source level 17 somewhere, so a Java 21+ API in
		// shared code is a build failure on this node instead of a latent one.
		//
		// The `forge` half is the SECOND Phase B node and the only one that is not on a
		// fabric-family loom: `build.forge.gradle.kts` applies Architectury Loom, and
		// `versions/1.20.1-forge/gradle.properties` carries `loom.platform=forge` because Arch
		// Loom reads it during plugin apply. It is also the only node with no template of any
		// kind behind its script (design R-12), the only one that must jar-in-jar MixinExtras
		// (R-10), and — being below 1.20.5 — the only one that lands on the shared tree's
		// `<1.20.5` branches while carrying a non-Fabric loader.
		//
		// Same as the NeoForge node: `:1.20.1-forge:build` FAILS by design until the Forge
		// seam impls land; `stonecutterGenerate` and `printPublishMetadata` are green.
		match("1.20.1", "fabric", "forge")

		// The node whose state the shared `src/` is committed in.
		vcsVersion = "26.2-fabric"
	}
}

// Keeps the jar base name and the mavenLocal coordinate Archetypes depends on.
rootProject.name = "specialities"
