plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-kapt")
    id("com.google.dagger.hilt.android") version "2.51"
}

android {
    namespace = "com.taizi"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.taizi"
        minSdk = 26
        targetSdk = 35
        versionCode = 36
        versionName = "1.10.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // Only ARM64 for RG DS
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    signingConfigs {
        create("release") {
            val releaseKeystore = file("${rootDir}/app/keystore/release.keystore")

            if (releaseKeystore.exists()) {
                storeFile = releaseKeystore
                storePassword = "taizirelease"
                keyAlias = "taizi"
                keyPassword = "taizirelease"
            } else {
                // Fallback to debug keystore; generate one if missing (CI environments)
                val debugConfig = signingConfigs.getByName("debug")
                val debugKeystore = debugConfig.storeFile
                if (debugKeystore == null || !debugKeystore.exists()) {
                    val generated = file("${layout.buildDirectory.get().asFile}/generated-debug.keystore")
                    generated.parentFile.mkdirs()
                    exec {
                        commandLine(
                            "keytool", "-genkey", "-v",
                            "-keystore", generated.absolutePath,
                            "-alias", "androiddebugkey",
                            "-keypass", "android",
                            "-storepass", "android",
                            "-keyalg", "RSA",
                            "-keysize", "2048",
                            "-validity", "10000",
                            "-dname", "CN=Android Debug,O=Android,C=US"
                        )
                        isIgnoreExitValue = false
                    }
                    storeFile = generated
                    storePassword = "android"
                    keyAlias = "androiddebugkey"
                    keyPassword = "android"
                } else {
                    storeFile = debugKeystore
                    storePassword = debugConfig.storePassword
                    keyAlias = debugConfig.keyAlias
                    keyPassword = debugConfig.keyPassword
                }
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isMinifyEnabled = false
            isDebuggable = true
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            isCrunchPngs = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-Xopt-in=kotlin.RequiresOptIn",
            "-P", "plugin:androidx.compose.compiler.plugins.kotlin:reportsDestination=${project.buildDir.absolutePath}/compose_metrics"
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "**/kotlin/**.kotlin_builtins"
            excludes += "**/META-INF/DEPENDENCIES"
            excludes += "**/META-INF/NOTICE"
            excludes += "**/META-INF/LICENSE"
            excludes += "**/META-INF/LICENSE.txt"
            excludes += "**/META-INF/NOTICE.txt"
            excludes += "META-INF/gradle/incremental.annotation.processors"
        }
    }

    // Only include essential resources
    androidResources {
        ignoreAssetsPattern += "!**/ic_launcher*.*,!**/ic_launcher_round*.*"
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    // Core Android
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.3")

    // Compose
    implementation("androidx.compose.ui:ui:1.7.8")
    implementation("androidx.compose.ui:ui-tooling-preview:1.7.8")
    implementation("androidx.compose.material3:material3:1.2.1")
    implementation("androidx.compose.material:material-icons-extended:1.2.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.navigation:navigation-compose:2.8.4")

    // Material Components (for theme)
    implementation("com.google.android.material:material:1.12.0")

    // Image loading
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // JSON
    implementation("com.google.code.gson:gson:2.10.1")

    // HTTP Client
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Archive central-directory reads for ROM validation
    implementation("com.squareup.okio:okio:3.9.0")

    // Room (box art database)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")

    // Hilt (DI)
    implementation("com.google.dagger:hilt-android:2.51")
    kapt("com.google.dagger:hilt-compiler:2.51")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // File access (SAF)
    implementation("androidx.documentfile:documentfile:1.0.1")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.7.8")
    debugImplementation("androidx.compose.ui:ui-tooling:1.7.8")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.7.8")
}
