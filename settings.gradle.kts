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
		// Later stages add: "neoforge" to the 1.21.1 line and "forge" to the 1.20.1
		// line (Phase B).  See docs/MULTIVERSION.md §1.3.
		match("26.2", "fabric")
		match("26.1", "fabric", version = "26.1.2")
		// First OBFUSCATED node. Nothing extra is needed to get the remap pipeline:
		// loom-back-compat asks Stonecutter to compare the node version against "26"
		// and applies `net.fabricmc.fabric-loom-remap` when it sorts below
		// (LoomCompatProjectExtension.isUnobfuscated, read out of the 0.4.1 bytecode —
		// both plugin ids ship in the same fabric-loom 1.17.17 jar, so `fabricApi.module`
		// and `officialMojangMappings` exist on either pipeline).
		match("1.21.11", "fabric")
		match("1.21.1", "fabric")
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
		match("1.20.1", "fabric")

		// The node whose state the shared `src/` is committed in.
		vcsVersion = "26.2-fabric"
	}
}

// Keeps the jar base name and the mavenLocal coordinate Archetypes depends on.
rootProject.name = "specialities"
