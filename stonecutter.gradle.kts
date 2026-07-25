plugins {
	id("dev.kikugie.stonecutter")
}

// The node the IDE edits, and the state `src/` is physically in on disk.
// Stonecutter compiles the ACTIVE node straight from `src/` with no preprocessing,
// so `src/` must always be valid for whatever this line names.
// Switch with `./gradlew stonecutterSwitchTo26.1-fabric` (rewrites `src/` in place).
stonecutter active "26.2-fabric"

stonecutter parameters {
	val (version, loader) = current.project.split('-', limit = 2)

	// Unlocks the `["<version>"]` and `[<loader>."<version>"]` sections of
	// stonecutter.properties.toml as plain project properties.
	properties {
		tags(version, loader)
	}

	// FROZEN LOADER CONSTANTS — see docs/MULTIVERSION-CONVENTIONS.md.
	// Usable as `//? if fabric {` / `//?} elif neoforge {` / `//?} elif forge {`.
	constants {
		match(loader, "fabric", "neoforge", "forge")
	}

	// `/*$ mod_version*/ "1.5.0"` style substitutions. Unused today; declared so the
	// version string never has to be duplicated into Java when it is needed.
	swaps["mod_version"] = "\"${properties.get<String>("mod.version")}\";"
	swaps["minecraft"] = "\"${node.metadata.version}\";"

	// Enables `//? if fapi: >=0.100 {` predicates.
	dependencies["fapi"] = properties.getOrNull<String>("deps.fabric_api") ?: "0"

	// Replacements are DIRECTIONAL, not one-way: `string(cond) { replace(a, b) }`
	// rewrites a->b when `cond` holds and b->a when it does not (verified in
	// StringSpecImpl, stonecutter 0.9.7 sources). So the shared tree may be authored
	// in either spelling — it is authored in the 26.x spelling, matching the active node.
	//
	// Registering 1.21.11-fabric makes the SECOND entry live for the first time: that
	// node gets `net.minecraft.util.Util` rewritten back down to `net.minecraft.Util`,
	// so no `//?` block is needed for the Util import. The first entry stays inert until
	// 1.21.1 lands — 1.21.11 is the OLDEST version that already spells it `Identifier`.
	replacements {
		string(current.parsed >= "1.21.11") {
			replace("ResourceLocation", "Identifier")
		}

		string(current.parsed >= "26.1") {
			replace("net.minecraft.Util", "net.minecraft.util.Util")
		}
	}
}

// Stage 4b (publishing) turns this on. `publishMods` is NOT an endpoint task, so its
// two children get ordered instead — otherwise the nodes race Modrinth's rate limiter.
// stonecutter tasks {
//     order("publishModrinth")
//     order("publishCurseforge")
// }
