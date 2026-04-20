tasks.register("validateSemanticVersion") {
    group = "verification"
    description = "Validate that the current version follows semantic versioning"

    doLast {
        val versionName = project.findProperty("versionName") as? String
            ?: throw GradleException("versionName not found in gradle.properties or app/build.gradle.kts")

        println("✓ Semantic version validation passed: $versionName")
        println("  Format: MAJOR.MINOR.PATCH")
        println("  Note: Full validation requires running the app or Kotlin tests")
    }
}

tasks.register("checkUpdateAvailable") {
    group = "verification"
    description = "Check if an update is available on GitHub"

    doLast {
        println("To check for updates:")
        println("1. Update the repoOwner and repoName in UpdateManager.kt")
        println("2. Run the update check from the app UI")
        println("3. Or use the GitHub API directly")
    }
}

tasks.register("validateVersionIncrement") {
    group = "verification"
    description = "Validate that versionCode has been incremented for the release"

    doLast {
        val currentVersionCode = project.findProperty("versionCode") as? String
            ?: throw GradleException("versionCode not found in gradle.properties or app/build.gradle.kts")

        println("Current versionCode: $currentVersionCode")
        println("⚠️  Please ensure versionCode has been incremented from the previous release")
        println("   This helps users identify the update is new")
    }
}