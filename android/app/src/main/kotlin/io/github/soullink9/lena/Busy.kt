package io.github.soullink9.lena

/**
 * Whether Lena is in the middle of a conversation in the sheet: listening, thinking or speaking. The
 * timers that act on "no one's touched it for a while" (the wall display's screensaver, and going back
 * from your chats to the dashboards) wait while she is, and start counting again only once she's done,
 * so a long think never ends in the screensaver.
 */
object Busy {
    /** Set by the sheet (AskActivity) whenever its phase changes; false once it's idle or closed. */
    @Volatile
    var talking: Boolean = false
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
