// Top-level build file for Taizi - Lightweight Android Launcher
plugins {
    id("com.android.application") version "8.4.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    id("com.google.dagger.hilt.android") version "2.51" apply false
}

apply(from = "gradle/validation.gradle.kts")

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
