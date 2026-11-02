package io.github.soullink9.lena

import android.app.Activity
import android.content.Intent
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The wall display on every Lena screen, not only the dashboards.
 *
 * In dashboard mode (Lena's wall display is this device's home screen, see
 * [DashboardActivity.isHomeScreen]) every screen keeps the display on, so it never goes dark. After
 * Settings → Wall display → Screensaver → Screen saver after without a touch, on whichever screen is
 * showing (a chat, All chats, Memory, Settings, Logs), the dashboard comes forward with its screensaver
 * on, and a touch on the screensaver shows the dashboard.
 *
 * One clock is shared by every screen, so moving from one to another doesn't start the wait again.
 * Typing, talking to her in the sheet ([Busy.talking]) and, on a chat, her still answering count as
 * someone being there: the wait starts again once they're done.
 *
 * Each screen calls [watch] in onCreate and [touched] from onUserInteraction(). The dashboard has the
 * screensaver itself, so it only uses the clock.
 */
object WallDisplay {

    /** When someone last touched any Lena screen, on the clock that doesn't jump when the time is set. */
    @Volatile
    private var lastTouch = SystemClock.elapsedRealtime()

    /** Someone touched a screen (or just finished talking to her): the wait starts again. */
    fun touched() {
        lastTouch = SystemClock.elapsedRealtime()
    }

    /** How long since anyone touched a Lena screen. */
    fun idleMillis(): Long = SystemClock.elapsedRealtime() - lastTouch

    /** Whether the keyboard is up on this screen; typing counts as someone being there. */
    fun typing(a: Activity): Boolean =
        ViewCompat.getRootWindowInsets(a.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == true

    /** In dashboard mode the display stays on; otherwise it sleeps as usual. */
    fun keepScreenOn(a: Activity, on: Boolean) {
        if (on) a.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else a.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    /**
     * While [a] is on screen: in dashboard mode, keeps the display on and brings up the screensaver once no
     * one has touched any screen for the set time. [stillWorking] is asked only when the time is up, so a
     * check that needs the network (is she still answering?) isn't made every few seconds.
     */
    fun watch(a: ComponentActivity, stillWorking: suspend () -> Boolean = { false }) {
        a.lifecycleScope.launch {
            a.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                // Checked on every return, since the home app can be changed in Android's settings meanwhile.
                val home = DashboardActivity.isHomeScreen(a)
                keepScreenOn(a, home)
                if (!home) return@repeatOnLifecycle
                while (true) {
                    delay(3_000)
                    val seconds = Prefs.dimSeconds(a)
                    if (seconds <= 0) continue
                    if (Busy.talking || typing(a)) {
                        touched()
                        continue
                    }
                    if (idleMillis() < seconds * 1000L) continue
                    if (stillWorking()) {
                        touched()
                        continue
                    }
                    showScreensaver(a)
                    return@repeatOnLifecycle
                }
            }
        }
    }

    /**
     * The dashboard, straight to its screensaver. This screen closes, so a touch on the screensaver shows
     * the dashboard rather than coming back here.
     */
    private fun showScreensaver(a: Activity) {
        Logs.i("Wall display", "No one around; the screensaver")
        a.startActivity(
            Intent(a, DashboardActivity::class.java)
                .putExtra(DashboardActivity.EXTRA_IDLE, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
        a.finish()
    }
}
