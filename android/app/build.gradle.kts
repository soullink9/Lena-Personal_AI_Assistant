import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "io.github.soullink9.lena"
    compileSdk = 36 // Android 16; don't raise until the phone runs a newer Android

    defaultConfig {
        applicationId = "io.github.soullink9.lena"
        minSdk = 36
        targetSdk = 36
        versionCode = 43
        versionName = "0.6.9"
        // Android 16 phones and tablets are 64-bit; leaving out 32-bit keeps the wake word's native code small.
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }

    // The release key comes from CI secrets (see docs/app/Installation.md). Without it the APK is debug-signed.
    val keystore = System.getenv("LENA_KEYSTORE")
    signingConfigs {
        if (keystore != null) create("release") {
            storeFile = file(keystore)
            storePassword = System.getenv("LENA_KEYSTORE_PASSWORD")
            keyAlias = "lena"
            keyPassword = System.getenv("LENA_KEYSTORE_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName(if (keystore != null) "release" else "debug")
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Leave out the Google-encrypted dependency report blob.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// Google's Jetpack libraries only, pinned to versions that build against SDK 36.
dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.10.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("androidx.webkit:webkit:1.14.0")
    implementation("androidx.work:work-runtime-ktx:2.10.1")
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    // Runs the wake word models on the phone (Google's LiteRT, formerly TensorFlow Lite; no Play services).
    implementation("com.google.ai.edge.litert:litert:1.4.0")
}
