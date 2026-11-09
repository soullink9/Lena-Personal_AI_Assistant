package io.github.soullink9.lena

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast

/**
 * What differs between the Android versions Lena runs on: Android 16 on phones and tablets, back to
 * Android 11 on Android TV boxes like the Nvidia Shield (minSdk 30). Each newer feature is checked here
 * once, so the rest of the app doesn't need to know which Android it's on.
 *
 * @ChecksSdkIntAtLeast tells the build's lint check (NewApi, which fails the build) that code behind one of
 * these checks may use that Android version's calls.
 */
object Compat {
    /**
     * Whether Lena may show notifications. Android 13 added a permission for it; before that, apps
     * could unless you'd turned them off for the app, which Android then simply doesn't show.
     */
    fun notificationsAllowed(c: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Whether Android has a permission to ask for before showing notifications (Android 13 and later). */
    @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.TIRAMISU)
    val asksForNotifications: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    /**
     * Whether the speech recognizer can listen through a microphone Lena already holds (Android 13), so
     * the wake word's microphone never closes before your question ([MicRelay]).
     */
    @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.TIRAMISU)
    val recognizerTakesAudio: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    /** Whether Android can say if it has an on-device speech recognizer, and make one (Android 12). */
    @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.S)
    val onDeviceRecognizer: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    /** Whether the theme can take its colours from the wallpaper (Android 12). */
    @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.S)
    val wallpaperColours: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
}
