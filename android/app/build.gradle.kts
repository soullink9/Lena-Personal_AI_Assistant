plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "io.github.soullink9.lena"
    // Android 17: the newest Jetpack libraries build only against it. It decides which APIs the build can see, not
    // how the app behaves (that's targetSdk, still Android 16).
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.soullink9.lena"
        // Android 15, on phones, tablets and Android TV alike (the owner's choice): no code for older Android.
        // Anything newer than Android 15 is used only where it's there (a Build.VERSION check), and the lint
        // block below fails the build on any call that isn't.
        minSdk = 35
        targetSdk = 36
        versionCode = 87
        versionName = "0.6.53"
        // Phones and tablets are 64-bit; Android TVs and boxes may still run 32-bit Android on 64-bit chips, so
        // the wake word's native code (LiteRT) comes for 32-bit ARM too.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
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

    // Kotlin's JVM target follows targetCompatibility (built-in Kotlin), so this sets both.
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // A call to anything newer than Android 15 (minSdk) without a Build.VERSION check would crash on an
    // Android 15 device, and an Android 16 phone would never show it: make it fail every release build
    // instead. (Release builds run lint's "vital" checks; this adds NewApi to them.)
    lint {
        fatal += "NewApi"
        checkReleaseBuilds = true
    }

    // Leave out the Google-encrypted dependency report blob.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

// Google's Jetpack libraries, the newest stable releases that build against compileSdk 37 (Compose 1.13 and
// Material 3 1.5 need 37.1, newer than the Android Gradle Plugin supports; see docs/development/App.md).
dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    // The Material icons aren't released with Compose any more; 1.7.8 is the last version.
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.1")
    implementation("androidx.webkit:webkit:1.18.0")
    implementation("androidx.work:work-runtime-ktx:2.12.0")
    implementation("androidx.glance:glance-appwidget:1.2.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    // Runs the wake word models on the phone (Google's LiteRT, formerly TensorFlow Lite; no Play services).
    // Stays on 1.x: LiteRT 2 drops the Interpreter API WakeWord.kt uses for its CompiledModel API.
    implementation("com.google.ai.edge.litert:litert:1.4.0")
}
