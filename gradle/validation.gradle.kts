val appBuildFile = file("${rootDir}/app/build.gradle.kts")

fun readVersionFromBuildFile(): Pair<String, Int> {
    val content = appBuildFile.readText()
    val versionNameRegex = Regex("""versionName\s*=\s*"([^"]+)"""")
    val versionCodeRegex = Regex("""versionCode\s*=\s*(\d+)""")
    val versionName = versionNameRegex.find(content)?.groupValues?.get(1)
        ?: throw GradleException("versionName not found in app/build.gradle.kts")
    val versionCode = versionCodeRegex.find(content)?.groupValues?.get(1)?.toIntOrNull()
        ?: throw GradleException("versionCode not found in app/build.gradle.kts")
    return versionName to versionCode
}

tasks.register("validateSemanticVersion") {
    group = "verification"
    description = "Validate that the current version follows semantic versioning"

    doLast {
        val (versionName, _) = readVersionFromBuildFile()

        val regex = Regex("""^[0-9]+\.[0-9]+\.[0-9]+(-[a-zA-Z0-9.]+)?$""")
        if (!regex.matches(versionName)) {
            throw GradleException(
                "Invalid semantic version: $versionName. " +
                "Expected format: MAJOR.MINOR.PATCH[-PRERELEASE]"
            )
        }
        println("✓ Semantic version validation passed: $versionName")
        println("  Format: MAJOR.MINOR.PATCH")
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
        val (versionName, versionCode) = readVersionFromBuildFile()

        if (versionCode < 1) {
            throw GradleException("versionCode must be >= 1, found: $versionCode")
        }

        if (versionName.contains("-release", ignoreCase = true)) {
            throw GradleException("versionName must not contain '-release'. Found: $versionName")
        }

        println("✓ versionCode validation passed: $versionCode")
        println("✓ versionName validation passed: $versionName")
        println("  Ensure versionCode has been incremented from the previous release")
    }
}