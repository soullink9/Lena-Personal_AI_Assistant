package io.github.soullink9.lena

import java.util.concurrent.ConcurrentHashMap

/**
 * Whether Lena is in the middle of something: a conversation in the sheet (listening, thinking or
 * speaking), or her own chat waiting for Assist. The timers that act on "no one's touched it for a while"
 * (the wall display's screensaver, and going back from your chats to the dashboards) wait while she is,
 * and start counting again only once she's done, so a long think never ends in the screensaver.
 *
 * Each screen marks itself ([set]) rather than sharing one flag: a sheet closing just as a new one opens
 * (the wake word right after a conversation) used to clear the flag for the new one, which then thought
 * for minutes with the screensaver counting down.
 */
object Busy {
    private val owners = ConcurrentHashMap.newKeySet<Any>()

    /** True while any screen says it's busy. */
    val talking: Boolean get() = owners.isNotEmpty()

    /** [owner] (a screen) is busy, or done. */
    fun set(owner: Any, busy: Boolean) {
        if (busy) owners.add(owner) else owners.remove(owner)
    }
}

/**
 * Whether one of Lena's screens is on screen, counted from every activity's start and stop (set up in
 * [LenaApp]). A timer that rings while she's showing (the wall display) brings up its ringing screen
 * straight away; otherwise Android shows it from the notification.
 */
object AppVisible {
    @Volatile
    private var started = 0

    /** True while at least one Lena screen is showing. */
    val now: Boolean get() = started > 0

    /** Counts Lena's screens as they come and go. */
    fun track(app: android.app.Application) {
        app.registerActivityLifecycleCallbacks(object : android.app.Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: android.app.Activity) { started++ }
            override fun onActivityStopped(activity: android.app.Activity) { started = maxOf(0, started - 1) }
            override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: android.os.Bundle?) {}
            override fun onActivityResumed(activity: android.app.Activity) {}
            override fun onActivityPaused(activity: android.app.Activity) {}
            override fun onActivitySaveInstanceState(activity: android.app.Activity, outState: android.os.Bundle) {}
            override fun onActivityDestroyed(activity: android.app.Activity) {}
        })
    }
}
