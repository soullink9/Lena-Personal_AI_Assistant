// The Android Gradle Plugin (9.x) compiles Kotlin itself ("built-in Kotlin"), so there's no Kotlin Android plugin.
// It brings an older Kotlin Gradle plugin of its own; the classpath line below moves it to the Kotlin the app uses,
// which must be the same version as the Compose compiler plugin.
buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.21")
    }
}

plugins {
    id("com.android.application") version "9.5.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
}
