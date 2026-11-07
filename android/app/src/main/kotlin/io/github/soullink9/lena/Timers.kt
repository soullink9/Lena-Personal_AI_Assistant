package io.github.soullink9.lena

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.UUID

/**
 * A timer (rings after a while), an alarm (rings at a time of day), or a reminder (says something at a
 * time, set by her set_reminder tool).
 */
enum class CountdownKind { TIMER, ALARM, REMINDER }

/**
 * One of Lena's timers or alarms: when it rings ([at], wall-clock milliseconds), what it's for, and for a
 * timer how long it was set for.
 */
data class Countdown(
    val id: String,
    val kind: CountdownKind,
    val at: Long,
    val label: String = "",
    val durationMs: Long = 0,
) {
    /** "10 minute timer", "pasta timer", "7:30 AM alarm": how she speaks of it. */
    fun name(c: Context): String = when (kind) {
        CountdownKind.TIMER -> if (label.isNotBlank()) "$label timer" else "${Timers.spoken(durationMs)} timer"
        CountdownKind.ALARM -> "${Timers.clock(c, at)} alarm" + if (label.isNotBlank()) " ($label)" else ""
        CountdownKind.REMINDER -> "Reminder: $label"
    }
}

/**
 * Lena's own timers and alarms, on this device: set by voice (see [Commands]) or in Settings → Alarms &
 * timers. They ring here ([Ringing]) with the alarm sound, show on the dashboards and the screensaver,
 * and she can say how long is left or cancel them.
 *
 * Each is set with Android's alarm clock (AlarmManager.setAlarmClock), the same as a clock app's, so it
 * rings on time even while the tablet sleeps; they're set again after a restart ([BootReceiver]). The
 * list is kept in the app's preferences.
 */
object Timers {
    private const val PREFS = "lena_timers"
    private const val KEY = "countdowns"
    const val EXTRA_ID = "countdown_id"

    private val list = MutableStateFlow<List<Countdown>?>(null)

    /** Every timer and alarm still to ring, soonest first, for screens to show. */
    fun items(c: Context): StateFlow<List<Countdown>?> {
        if (list.value == null) list.value = read(c)
        return list
    }

    /** What's set now, soonest first. */
    fun all(c: Context): List<Countdown> = items(c).value.orEmpty()

    /** Sets a timer that rings after [durationMs]. */
    fun addTimer(c: Context, durationMs: Long, label: String = ""): Countdown =
        add(c, Countdown(UUID.randomUUID().toString(), CountdownKind.TIMER, System.currentTimeMillis() + durationMs, label.trim(), durationMs))

    /** Sets an alarm for the next [hour]:[minute] (today if it's still to come, otherwise tomorrow). */
    fun addAlarm(c: Context, hour: Int, minute: Int, label: String = ""): Countdown =
        add(c, Countdown(UUID.randomUUID().toString(), CountdownKind.ALARM, nextAt(hour, minute), label.trim()))

    /** Sets a reminder: [message] said at [at] (wall-clock milliseconds). */
    fun addReminder(c: Context, at: Long, message: String): Countdown =
        add(c, Countdown(UUID.randomUUID().toString(), CountdownKind.REMINDER, at, message.trim()))

    /** Cancels one. */
    fun cancel(c: Context, id: String) {
        disarm(c, id)
        save(c, all(c).filterNot { it.id == id })
    }

    /** Takes one off the list as it rings, and returns it. */
    fun fired(c: Context, id: String): Countdown? {
        val item = all(c).firstOrNull { it.id == id } ?: return null
        save(c, all(c).filterNot { it.id == id })
        return item
    }

    /**
     * Sets every alarm again (Android forgets them on a restart or an update). Ones that should have rung
     * while the tablet was off ring in a few seconds if they're less than five minutes late, and are dropped
     * otherwise. They go through the alarm clock too, since Android doesn't let an app start ringing
     * straight from its start-up.
     */
    fun rearm(c: Context) {
        val now = System.currentTimeMillis()
        val (missed, coming) = all(c).partition { it.at <= now }
        val late = missed.filter { now - it.at < 5 * 60_000 }.map { it.copy(at = now + 3_000) }
        val keep = coming + late
        save(c, keep)
        keep.forEach { arm(c, it) }
    }

    private fun add(c: Context, item: Countdown): Countdown {
        save(c, all(c) + item)
        arm(c, item)
        Logs.i("Timers", "Set: ${item.name(c)}")
        return item
    }

    /** The next time the clock shows [hour]:[minute]. */
    private fun nextAt(hour: Int, minute: Int): Long {
        val at = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (at.timeInMillis <= System.currentTimeMillis()) at.add(Calendar.DAY_OF_YEAR, 1)
        return at.timeInMillis
    }

    /** What fires it: [CountdownReceiver], one pending intent per countdown. */
    private fun firing(c: Context, id: String): PendingIntent = PendingIntent.getBroadcast(
        c, id.hashCode(),
        Intent(c, CountdownReceiver::class.java).putExtra(EXTRA_ID, id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Android's alarm clock, which also shows the alarm icon and opens Settings → Alarms & timers. */
    private fun arm(c: Context, item: Countdown) {
        val show = PendingIntent.getActivity(
            c, 0, Intent(c, SettingsActivity::class.java).setAction(SettingsActivity.ACTION_ALARMS),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val alarms = c.getSystemService(AlarmManager::class.java)
        try {
            alarms.setAlarmClock(AlarmManager.AlarmClockInfo(item.at, show), firing(c, item.id))
        } catch (e: SecurityException) {
            Logs.e("Timers", "Android didn't allow an exact alarm", e)
        }
    }

    private fun disarm(c: Context, id: String) {
        c.getSystemService(AlarmManager::class.java).cancel(firing(c, id))
    }

    private fun read(c: Context): List<Countdown> {
        val text = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]"
        val array = runCatching { JSONArray(text) }.getOrDefault(JSONArray())
        return List(array.length()) { i ->
            val o = array.getJSONObject(i)
            Countdown(
                id = o.optString("id"),
                kind = runCatching { CountdownKind.valueOf(o.optString("kind")) }.getOrDefault(CountdownKind.TIMER),
                at = o.optLong("at"),
                label = o.optString("label"),
                durationMs = o.optLong("duration"),
            )
        }.sortedBy { it.at }
    }

    private fun save(c: Context, items: List<Countdown>) {
        val sorted = items.sortedBy { it.at }
        val array = JSONArray()
        sorted.forEach {
            array.put(JSONObject().put("id", it.id).put("kind", it.kind.name).put("at", it.at).put("label", it.label).put("duration", it.durationMs))
        }
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, array.toString()).apply()
        list.value = sorted
    }

    // ---- Words ------------------------------------------------------------------------------

    /** "1 hour 5 minutes", "90 seconds", as she says a length of time. */
    fun spoken(ms: Long): String {
        val total = (ms + 500) / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        val parts = listOfNotNull(
            if (h > 0) "$h hour${if (h == 1L) "" else "s"}" else null,
            if (m > 0) "$m minute${if (m == 1L) "" else "s"}" else null,
            if (s > 0 && h == 0L) "$s second${if (s == 1L) "" else "s"}" else null,
        )
        return parts.joinToString(" ").ifEmpty { "0 seconds" }
    }

    /** "4:05" or "1:02:30", for a countdown on screen. */
    fun countdown(ms: Long): String {
        val total = (ms.coerceAtLeast(0) + 999) / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    /** The time of day in the phone's own format ("7:30 AM" or "07:30"). */
    fun clock(c: Context, at: Long): String = android.text.format.DateFormat.getTimeFormat(c).format(Date(at))

    /** "today", "tomorrow" or the date, for an alarm. */
    fun day(at: Long): String {
        val today = Calendar.getInstance()
        val then = Calendar.getInstance().apply { timeInMillis = at }
        val days = then.get(Calendar.DAY_OF_YEAR) - today.get(Calendar.DAY_OF_YEAR) +
            365 * (then.get(Calendar.YEAR) - today.get(Calendar.YEAR))
        return when (days) {
            0 -> "today"
            1 -> "tomorrow"
            else -> DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(at))
        }
    }
}

/** An alarm or timer's time came: it rings. */
class CountdownReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(Timers.EXTRA_ID) ?: return
        val item = Timers.fired(context, id) ?: return
        Ringing.start(context, item)
    }
}

/**
 * The next timer or alarm, small, for the dashboards' bar and the screensaver: a timer counts down by
 * the second, an alarm shows its time. Nothing when none is set.
 */
@Composable
fun NextCountdown(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    style: TextStyle = MaterialTheme.typography.labelLarge,
) {
    val context = LocalContext.current
    val items by Timers.items(context).collectAsState()
    val next = items?.firstOrNull() ?: return
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(next.id) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Icon(countdownIcon(next.kind), null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        val text = when (next.kind) {
            CountdownKind.TIMER -> Timers.countdown(next.at - now) + if (next.label.isNotBlank()) " · ${next.label}" else ""
            CountdownKind.ALARM -> Timers.clock(context, next.at)
            CountdownKind.REMINDER -> Timers.clock(context, next.at) + " · " + next.label
        }
        Text(text, color = color, style = style, maxLines = 1)
    }
}

/** The icon for a timer, an alarm or a reminder. */
fun countdownIcon(kind: CountdownKind) = when (kind) {
    CountdownKind.TIMER -> Icons.Outlined.Timer
    CountdownKind.ALARM -> Icons.Outlined.Alarm
    CountdownKind.REMINDER -> Icons.Outlined.NotificationsActive
}
