// app/build.gradle.kts — Lekhani Android
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.lekhani.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.lekhani.android"
        minSdk = 24          // Android 7.0 — covers 95%+ of active devices
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        // ABI split: ship all three ABIs in a single universal APK for now.
        // Switch to per-ABI APK splits or AAB when Play Store upload is ready.
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
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
        }
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
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

    kotlinOptions {
        jvmTarget = "17"
        // Treat all Kotlin warnings as errors in CI
        if (System.getenv("CI") == "true") {
            freeCompilerArgs += "-Werror"
        }
    }

    // ── Packaging ──────────────────────────────────────────────────────────────
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        // Include the compiled Rust native library from cargo-ndk output.
        // The CI workflow places stripped .so files under jniLibs/.
        jniLibs {
            srcDirs("src/main/jniLibs")
        }
    }

    // ── Source sets ────────────────────────────────────────────────────────────
    sourceSets {
        getByName("main") {
            java.srcDirs("src/main/kotlin")
        }
        getByName("test") {
            java.srcDirs("src/test/kotlin")
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)

    // UniFFI JNA runtime — required by the generated Kotlin bindings to load
    // liblekhani_android.so from the jniLibs directory.
    implementation(libs.uniffi.runtime)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
