// VERBATIM from the maintained multiloader template
// (`gh api repos/stonecutter-versioning/stonecutter-template-multiloader/contents/buildSrc/build.gradle.kts`,
// re-fetched and diffed 2026-07-25). Do not embellish it: buildSrc is compiled before every
// other script in the build, so anything added here is paid on every invocation of every node.
plugins {
	`kotlin-dsl`
}

repositories {
	mavenCentral()
}
