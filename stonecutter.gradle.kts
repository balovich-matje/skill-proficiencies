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
	// BOTH entries are inert on the nodes registered so far and stay inert until 1.21.1
	// lands: 1.21.11 is the OLDEST version that already spells it `Identifier`, AND the
	// oldest that already spells it `net.minecraft.util.Util`.
	//
	// The Util boundary was `>=26.1` when the 1.21.11 node was registered. That was WRONG
	// and would have broken this node on its first build: the rewrite is directional, so
	// `>=26.1` being false for 1.21.11 rewrote the shared tree's `net.minecraft.util.Util`
	// DOWN to `net.minecraft.Util` — a class that does not exist there. Mojmap ground
	// truth (scratchpad/mappings/*-client.txt): 1.21.11 has `net.minecraft.util.Util -> bhs`
	// and no `net.minecraft.Util` at all; 1.21.1 has `net.minecraft.Util -> ad`; 1.20.1 has
	// `net.minecraft.Util -> ac`. The package move therefore happens at exactly 1.21.11.
	// Importers in the shared tree: client/SkillHudState.java, client/StealthVignette.java.
	// A `//?` block cannot fix this from the source side — replacements are applied to the
	// generated text regardless of branch state, so only this condition can.
	replacements {
		string(current.parsed >= "1.21.11") {
			replace("ResourceLocation", "Identifier")
		}

		string(current.parsed >= "1.21.11") {
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
