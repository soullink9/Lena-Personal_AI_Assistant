package io.github.soullink9.lena

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.OffsetDateTime

/**
 * Announcements from Home Assistant (the lena.announce action), like Home Assistant Voice's: the wall
 * display watches its own Announcement entity ([Room]) and, when it fires, says the message in the sheet
 * and, if the announcement asks, listens for an answer.
 *
 * It's looked at every few seconds while a Lena screen is showing in dashboard mode (not while the sheet
 * is: an announcement made during a conversation is said once it ends, if that's within ten minutes).
 */
object Announcements {
    private const val PREFS = "lena_room"
    private const val KEY = "announced"
    private const val POLL_MS = 3_000L
    private const val AFTER_FAILURE_MS = 30_000L
    private const val TOO_OLD_MS = 10 * 60_000L

    /** While [a] is showing in dashboard mode, says announcements meant for this display. */
    fun watch(a: ComponentActivity, from: AskActivity.From? = null) {
        a.lifecycleScope.launch {
            a.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                if (!DashboardActivity.isHomeScreen(a)) return@repeatOnLifecycle
                while (true) {
                    val ok = runCatching { check(a, from) }.isSuccess
                    delay(if (ok) POLL_MS else AFTER_FAILURE_MS)
                }
            }
        }
    }

    /**
     * Looks at the Announcement once. The first look only remembers where it stands, so an old one isn't
     * said again after a restart. Throws when Home Assistant can't be reached.
     */
    private suspend fun check(a: ComponentActivity, from: AskActivity.From?) {
        val entity = Room.info(a).value?.announceEntity ?: return
        val ha = HomeAssistant.from(a) ?: return
        val state = ha.stateWithAttributes(entity) ?: return
        val stamp = state.optString("state")
        val seen = seen(a)
        if (stamp == seen) return
        remember(a, stamp)
        if (seen == null) return
        val at = runCatching { OffsetDateTime.parse(stamp).toInstant().toEpochMilli() }.getOrNull() ?: return
        if (System.currentTimeMillis() - at > TOO_OLD_MS) return
        val attributes = state.optJSONObject("attributes") ?: return
        val message = attributes.optString("message").trim()
        if (message.isEmpty()) return
        Logs.i("Announcements", "From Home Assistant: $message")
        a.startActivity(AskActivity.announce(a, message, attributes.optBoolean("listen"), from))
    }

    private fun seen(c: Context): String? = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)

    private fun remember(c: Context, stamp: String) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, stamp).apply()
    }
}
