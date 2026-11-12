// Stand-ins for the parts of the app Commands.kt uses, recording what it asked for.
import java.util.Calendar

class Context

enum class CountdownKind { TIMER, ALARM, REMINDER }

data class Countdown(val id: String, val kind: CountdownKind, val at: Long, val label: String = "", val durationMs: Long = 0) {
    fun name(c: Context) = when (kind) {
        CountdownKind.TIMER -> if (label.isNotBlank()) "$label timer" else "${Timers.spoken(durationMs)} timer"
        CountdownKind.ALARM -> "${Timers.clock(c, at)} alarm"
        CountdownKind.REMINDER -> "Reminder: $label"
    }
}

class State<T>(var value: T)

/** What the last sentence did: "timer 600000 pasta", "alarm 06:45", "cancel 2", "stop", "snooze". */
val did = mutableListOf<String>()

object Ringing {
    val now = State<Countdown?>(null)
    fun stop(c: Context) { now.value = null; did += "stop" }
    fun snooze(c: Context) { now.value = null; did += "snooze" }
}

object Timers {
    val list = mutableListOf<Countdown>()
    private var n = 0
    fun all(c: Context) = list.sortedBy { it.at }
    fun addTimer(c: Context, ms: Long, label: String = ""): Countdown =
        Countdown("${n++}", CountdownKind.TIMER, System.currentTimeMillis() + ms, label.trim(), ms)
            .also { list += it; did += "timer $ms ${label.trim()}".trim() }
    fun addAlarm(c: Context, hour: Int, minute: Int, label: String = ""): Countdown {
        val at = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute); set(Calendar.SECOND, 0)
        }
        if (at.timeInMillis <= System.currentTimeMillis()) at.add(Calendar.DAY_OF_YEAR, 1)
        return Countdown("${n++}", CountdownKind.ALARM, at.timeInMillis, label).also { list += it; did += "alarm %02d:%02d".format(hour, minute) }
    }
    fun cancel(c: Context, id: String) { list.removeIf { it.id == id }; did += "cancel $id" }
    fun spoken(ms: Long): String = "${(ms + 500) / 1000} s"
    fun clock(c: Context, at: Long): String = java.text.SimpleDateFormat("HH:mm").format(java.util.Date(at))
    fun day(at: Long) = "soon"
}

/** Camera mode: on in these checks; opening it is recorded. */
object Cameras {
    var on = true
    fun available(c: Context) = on
    fun open(c: Context, asked: String = "") { did += "cameras" }
}
