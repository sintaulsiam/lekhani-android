// app/build.gradle.kts — Lekhani Android
import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.lekhani.android"
    compileSdk = 35
    ndkVersion = "28.0.12433566"

    defaultConfig {
        applicationId = "com.lekhani.android"
        minSdk = 24          // Android 7.0 — covers 95%+ of active devices
        targetSdk = 35
        versionCode = 3
        versionName = "0.3.0"

        // ABI split: ship all three ABIs in a single universal APK for now.
        // Switch to per-ABI APK splits or AAB when Play Store upload is ready.
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    signingConfigs {
        create("release") {
            val keystorePropsFile = listOf(
                rootProject.file("keystore.properties"),
                file("keystore.properties"),
                File(System.getProperty("user.home"), ".gradle/lekhani-keystore.properties")
            ).firstOrNull { it.exists() }

            val keystoreProps = Properties().apply {
                if (keystorePropsFile != null) {
                    FileInputStream(keystorePropsFile).use { load(it) }
                }
            }

            val keystorePath = findProperty("LEKHANI_KEYSTORE_PATH") as? String
                ?: System.getenv("LEKHANI_KEYSTORE_PATH")
                ?: keystoreProps.getProperty("LEKHANI_KEYSTORE_PATH")
            val keystorePass = findProperty("LEKHANI_KEYSTORE_PASSWORD") as? String
                ?: System.getenv("LEKHANI_KEYSTORE_PASSWORD")
                ?: keystoreProps.getProperty("LEKHANI_KEYSTORE_PASSWORD")
            val keyAliasStr = findProperty("LEKHANI_KEY_ALIAS") as? String
                ?: System.getenv("LEKHANI_KEY_ALIAS")
                ?: keystoreProps.getProperty("LEKHANI_KEY_ALIAS")
            val keyPass = findProperty("LEKHANI_KEY_PASSWORD") as? String
                ?: System.getenv("LEKHANI_KEY_PASSWORD")
                ?: keystoreProps.getProperty("LEKHANI_KEY_PASSWORD")

            val resolvedKeystore = keystorePath?.let {
                listOf(
                    rootProject.file(it),
                    file(it),
                    File(it)
                ).firstOrNull { f -> f.exists() }
            }

            if (resolvedKeystore != null && resolvedKeystore.exists() && !keystorePass.isNullOrEmpty()) {
                storeFile = resolvedKeystore
                storePassword = keystorePass
                keyAlias = keyAliasStr ?: "lekhani"
                keyPassword = keyPass ?: keystorePass
            } else {
                // Fallback to debug keystore if no release keystore is supplied
                val debugKeystore = signingConfigs.getByName("debug").storeFile
                storeFile = debugKeystore
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
            enableV4Signing = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
        }
    }

    splits {
        abi {
            val isBundleTask = gradle.startParameter.taskNames.any { it.contains("bundle", ignoreCase = true) }
            isEnable = !isBundleTask
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = true
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets {
        getByName("main") {
            // Assets are maintained in src/main/assets — populated by the copyLekhaniAssets task.
            // We do NOT use srcDirs to pull in the entire root data/ directory; that would bundle
            // icons, JSON source files, dev-only models, and v1 fallback binaries into the APK.
            assets.srcDir("src/main/assets")
        }
    }

    androidResources {
        // Prevent aapt2 from compressing binary model and JSON files — they are read as raw
        // byte streams by the Rust engine and must remain uncompressed for mmap-like access.
        noCompress += listOf("bin", "json")
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

val cargoBuild = tasks.register<Exec>("cargoBuild") {
    val cargoHome = File(System.getProperty("user.home"), ".cargo/bin")
    val pathEnv = if (cargoHome.exists()) {
        "${cargoHome.absolutePath}:${System.getenv("PATH") ?: ""}"
    } else {
        System.getenv("PATH") ?: ""
    }
    environment("PATH", pathEnv)

    val sdkDir = File(System.getProperty("user.home"), "Android/Sdk")
    val ndkDir = File(sdkDir, "ndk/28.0.12433566")
    if (ndkDir.exists()) {
        environment("ANDROID_NDK_HOME", ndkDir.absolutePath)
        environment("NDK_HOME", ndkDir.absolutePath)
    }

    workingDir = rootDir.parentFile

    val isRelease = gradle.startParameter.taskNames.any { it.contains("Release", ignoreCase = true) }
    val cmd = mutableListOf(
        "cargo", "ndk",
        "-t", "arm64-v8a",
        "-t", "armeabi-v7a",
        "-t", "x86_64",
        "-o", "android/app/src/main/jniLibs",
        "build",
        "-p", "lekhani-android"
    )
    if (isRelease) {
        cmd.add("--release")
    }
    commandLine(cmd)

    onlyIf {
        val cargoBin = File(cargoHome, "cargo")
        cargoBin.exists() || (System.getenv("PATH")?.split(":")?.any { File(it, "cargo").exists() } == true)
    }
}

// ── Lekhani Asset Staging ──────────────────────────────────────────────────────────────────────
//
// The canonical source of all runtime data files is the lekhani-engine repository's data/
// directory. This task copies ONLY the files the Android app actually loads at runtime into
// src/main/assets, making that directory the one true staging area for the APK asset pipeline.
//
// Files intentionally excluded from the APK:
//   ✗ icons/**                – Desktop/web UI only; Android uses res/drawable
//   ✗ phonetic_overrides.json – 1.8 MB JSON fallback; .bin is always present in prod
//   ✗ bengali_gru.bin         – GRU v1 fallback; engine picks v2 first, v1 unreachable
//   ✗ bengali_vocab.bin       – Paired with v1 GRU; same reasoning
//   ✗ neural_weights.json     – Dev-only JSON path in get_neural_predictor(); never used in prod
//   ✗ neural_vocab.json       – Paired with dev JSON pair; same reasoning
//   ✗ neural_weights_v2.json  – Not referenced anywhere in the codebase
//
val engineDataDir = file("${rootDir.parentFile.parentFile}/lekhani-engine/data")

/** Files loaded at runtime from the dictionaries/ asset folder. */
val RUNTIME_DICTIONARIES = listOf(
    "dictionary.bin",          // 5.0 MB – PrefixTrie; primary path
    "dictionary.json",         // 4.1 MB – JSON source; cold-install fallback only
    "bengali_lm.bin",          // 5.1 MB – N-gram language model
    "english_lm.bin",          //  40 KB – English N-gram LM
    "bengali_gru_v2.bin",      // 592 KB – GRU neural predictor weights (v2, preferred)
    "bengali_vocab_v2.json",   //  68 KB – BPE vocabulary for gru_v2
    "phonetic_overrides.bin",  // 916 KB – Supervised phonetic overrides (binary)
    "autocorrect.json",        //  72 KB – Autocorrect rules
    "suffix.json",             //  24 KB – Morphological suffixes
    "rank_weights_v2.json",    //   4 KB – Perceptron rank weights
    "bengali_embeddings.bin",   // 594 KB – 32-D dense semantic word embeddings
    "english_dict.bin"         // 996 KB – English prefix trie for QWERTY mode
)

/** Fixed layout JSON files used by the engine session. */
val RUNTIME_LAYOUTS = listOf(
    "avrophonetic.json",
    "National_Jatiya.json",
    "Probhat.json"
)

val copyLekhaniAssets = tasks.register<Copy>("copyLekhaniAssets") {
    description = "Stages runtime-only data files from lekhani-engine/data/ into src/main/assets."
    group = "lekhani"

    // Primary source: lekhani-engine is the single source of truth for shared data files.
    // Fallback: if lekhani-engine is not checked out alongside lekhani-android, use
    // the local data/ directory in this repo (kept in sync manually).
    val engineSource = if (engineDataDir.isDirectory) engineDataDir else file("${rootDir.parentFile}/data")

    // Android-specific source: files that only exist in lekhani-android (e.g., English models).
    // The engine repo does not include these — they are Android-platform-specific assets.
    val androidSource = file("${rootDir.parentFile}/data")

    // Layer 1: shared files from lekhani-engine (or local fallback)
    from(file("${engineSource}/dictionaries")) {
        include(RUNTIME_DICTIONARIES)
        into("dictionaries")
    }
    from(file("${engineSource}/layouts")) {
        include(RUNTIME_LAYOUTS)
        into("layouts")
    }
    // Layer 2: Android-only files that lekhani-engine does not have.
    // Only active when lekhani-engine is the primary source (avoids double-copying in fallback mode).
    if (engineDataDir.isDirectory && androidSource.isDirectory) {
        from(file("${androidSource}/dictionaries")) {
            include(
                "english_dict.bin",  // 996 KB – English PrefixTrie, Android-only
                "english_lm.bin"     //  40 KB – English N-gram LM, Android-only
            )
            into("dictionaries")
        }
    }

    // Destination is the canonical assets staging dir
    into(file("src/main/assets"))

    outputs.dir(file("src/main/assets"))
}

tasks.named("preBuild").configure {
    dependsOn(cargoBuild, copyLekhaniAssets)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        if (System.getenv("CI") == "true") {
            allWarningsAsErrors.set(true)
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.customview)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation("sh.calvin.reorderable:reorderable:2.4.0")

    // UniFFI JNA runtime — required by the generated Kotlin bindings to load
    // liblekhani_android.so from the jniLibs directory.
    // NOTE: Must use @aar so Gradle extracts libjnidispatch.so for Android ABIs!
    // JNA 5.16.0+ is required for Android 15/16 16 KB page-size compliance.
    implementation("net.java.dev.jna:jna:5.16.0@aar")

    testImplementation(libs.junit)
    testImplementation("org.json:json:20240303")

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
