// app/build.gradle.kts — Lekhani Android
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

    commandLine(
        "cargo", "ndk",
        "-t", "arm64-v8a",
        "-t", "x86_64",
        "-o", "android/app/src/main/jniLibs",
        "build",
        "-p", "lekhani-android"
    )

    onlyIf {
        val cargoBin = File(cargoHome, "cargo")
        cargoBin.exists() || (System.getenv("PATH")?.split(":")?.any { File(it, "cargo").exists() } == true)
    }
}

tasks.named("preBuild").configure {
    dependsOn(cargoBuild)
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
    // NOTE: Must use @aar so Gradle extracts libjnidispatch.so for Android ABIs!
    // JNA 5.16.0+ is required for Android 15/16 16 KB page-size compliance.
    implementation("net.java.dev.jna:jna:5.16.0@aar")

    testImplementation(libs.junit)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
