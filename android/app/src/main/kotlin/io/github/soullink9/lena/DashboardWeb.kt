package io.github.soullink9.lena

import android.content.Context
import android.content.MutableContextWrapper
import android.view.ViewGroup
import android.webkit.WebView

/**
 * The dashboards' web view, kept for as long as the app runs.
 *
 * Chat and Home are two screens in one Android task: one app in Recents, and switching between them uses
 * the switch's own transition, closing one screen as the other opens. A new dashboard screen would start
 * Home Assistant from scratch, which is slow, so the web view outlives its screen: when the screen closes
 * it's set aside, page and all ([putAside]), and the next dashboard screen takes it back ([take]).
 *
 * It's made on a [MutableContextWrapper], so it always belongs to the screen showing it (and to the app
 * while none is). Messages from the page go to that screen, [host].
 */
object DashboardWeb {
    private var view: WebView? = null
    private var context: MutableContextWrapper? = null

    /** The Home Assistant address and token it was made for; a different one means a new web view. */
    private var madeFor = ""

    /** The dashboard screen showing the web view now, which answers the page's messages. */
    var host: DashboardActivity? = null
        private set

    /**
     * The web view for [activity]: the kept one if it was made for the same Home Assistant ([key]), or a
     * new one from [make]. Also says whether it's new, so the screen knows to load it.
     */
    fun take(activity: DashboardActivity, key: String, make: (Context) -> WebView): Pair<WebView, Boolean> {
        val kept = view
        if (kept != null && madeFor == key) {
            context?.baseContext = activity
            (kept.parent as? ViewGroup)?.removeView(kept)
            host = activity
            return kept to false
        }
        kept?.destroy()
        val wrapper = MutableContextWrapper(activity)
        val made = make(wrapper)
        view = made
        context = wrapper
        madeFor = key
        host = activity
        return made to true
    }

    /**
     * [activity] is closing: the web view is set aside for the next dashboard screen, not destroyed. A
     * screen that isn't the current host (an older one closing late) changes nothing.
     */
    fun putAside(activity: DashboardActivity) {
        if (host !== activity) return
        val kept = view ?: return
        (kept.parent as? ViewGroup)?.removeView(kept)
        context?.baseContext = activity.applicationContext
        host = null
    }
}
