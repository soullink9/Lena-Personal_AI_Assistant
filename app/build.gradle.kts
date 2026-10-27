plugins {
    id("com.android.application")
}

// No dependencies on purpose: the app uses only the Android framework.

android {
    namespace = "io.github.soullink9.lena"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.soullink9.lena"
        minSdk = 30 // Android 11
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    // The release key comes from CI secrets (see README). Without it the APK is debug-signed.
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
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName(if (keystore != null) "release" else "debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Leave out the Google-encrypted dependency report blob; there's nothing to report anyway.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}
