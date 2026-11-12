package io.github.soullink9.lena

import android.content.Context
import java.util.Calendar

/**
 * What Lena handles herself, on this device, before anything goes to Open WebUI or Assist: her timers and
 * alarms ([Timers]), and "stop" or "snooze" while one rings ([Ringing]). These answer at once and work
 * without a connection.
 *
 * It only takes a sentence that is plainly about a timer or an alarm ("set a 10 minute pasta timer",
 * "wake me up at 6:45", "how long is left", "cancel my alarm"); anything longer or less clear goes on to
 * whoever answers, as before. English only, as the wake words and the stop word are.
 */
object Commands {

    /** Longer than this, it's probably more than a timer request ("…and then remind me to…"): not ours. */
    private const val MAX_WORDS = 16

    private val CANCEL = setOf("cancel", "delete", "remove", "clear", "stop", "dismiss", "end", "kill")
    private val ASK = setOf("how", "what", "when", "which", "whats", "list", "show", "is", "are", "do", "any")
    private val MINE = setOf("my", "the", "any", "set", "have", "timers", "alarms", "next")
    private val UNITS = mapOf(
        "hour" to 3_600_000L, "hours" to 3_600_000L, "hr" to 3_600_000L, "hrs" to 3_600_000L, "h" to 3_600_000L,
        "minute" to 60_000L, "minutes" to 60_000L, "min" to 60_000L, "mins" to 60_000L, "m" to 60_000L,
        "second" to 1_000L, "seconds" to 1_000L, "sec" to 1_000L, "secs" to 1_000L, "s" to 1_000L,
    )
    private val ONES = mapOf(
        "zero" to 0, "oh" to 0, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6, "seven" to 7,
        "eight" to 8, "nine" to 9, "ten" to 10, "eleven" to 11, "twelve" to 12, "thirteen" to 13, "fourteen" to 14,
        "fifteen" to 15, "sixteen" to 16, "seventeen" to 17, "eighteen" to 18, "nineteen" to 19,
    )
    private val TENS = mapOf("twenty" to 20, "thirty" to 30, "forty" to 40, "fifty" to 50, "sixty" to 60, "ninety" to 90)

    /** Words that aren't a timer's name: what's left of "set me a timer for…" once the time is taken out. */
    private val FILLER = setOf(
        "set", "start", "make", "create", "put", "on", "add", "a", "an", "the", "my", "me", "timer", "timers", "for",
        "called", "named", "please", "new", "of", "and", "lena", "hey", "can", "you", "could", "would", "will", "i",
        "want", "need", "to", "give", "up", "okay", "ok", "now", "just", "another", "one", "go", "off", "in",
    )

    /**
     * Handles [question] if it's one of hers, and returns what she says back; null when it isn't, so it
     * goes to whoever answers. With [timersHere] false (the display is an Assist satellite, so Home
     * Assistant keeps its timers), timers go on to Assist or her tools, which use Home Assistant's own; the
     * copy that rings here comes back from Home Assistant.
     */
    fun handle(c: Context, question: String, timersHere: Boolean = true): String? {
        val words = words(question)
        if (words.isEmpty() || words.size > MAX_WORDS) return null
        val text = words.joinToString(" ")

        // While one rings: "stop", "snooze", "turn it off".
        if (Ringing.now.value != null) {
            if (text in setOf("stop", "stop it", "ok stop", "okay stop", "stop that", "turn it off", "dismiss", "enough", "thats enough", "cancel")) {
                Ringing.stop(c)
                return "Stopped."
            }
            if (words.first() == "snooze") {
                Ringing.snooze(c)
                return "Snoozed for 10 minutes."
            }
        }

        // "Show the cameras", "show me the front door camera": camera mode, when it's on here (Settings →
        // Cameras), with the camera named in the spotlight.
        if (words.any { it == "camera" || it == "cameras" } && words.any { it in setOf("show", "open", "see", "view") }) {
            if (!Cameras.available(c)) return null // Home Assistant or her may have something to say about cameras
            Cameras.open(c, asked = text)
            return "Here are the cameras."
        }

        val timer = words.any { it == "timer" || it == "timers" }
        val alarm = words.any { it == "alarm" || it == "alarms" } || text.startsWith("wake me")
        // "How long is left?" names no timer, but means one when one is running.
        if (!timer && !alarm) {
            val howLong = words.first() == "how" && words.any { it == "left" || it == "remaining" || it == "longer" }
            return if (timersHere && howLong && Timers.all(c).any { it.kind == CountdownKind.TIMER }) status(c, CountdownKind.TIMER, words) else null
        }
        if (timer && !timersHere) return null
        // "Remind me…", "turn on the alarm system", "the alarm panel": things for Home Assistant or her, not a clock.
        if (words.any { it in setOf("remind", "reminder", "system", "panel", "security", "arm", "disarm", "armed", "siren", "sensor") }) return null

        val first = words.first().let { if (it in setOf("please", "lena", "hey", "can", "could", "would")) words.firstOrNull { w -> w !in FILLER } ?: it else it }
        return when {
            first in CANCEL || text.contains("turn off") || text.contains("switch off") || text.contains("get rid of") ->
                cancel(c, words, if (timer) CountdownKind.TIMER else CountdownKind.ALARM)
            // A question about hers ("when is my alarm", "what timers do I have"), not about timers in
            // general ("what is a timer circuit", which goes on to whoever answers).
            words.any { it == "left" || it == "remaining" } || (first in ASK && words.any { it in MINE }) ->
                status(c, if (timer) CountdownKind.TIMER else CountdownKind.ALARM, words)
            timer -> setTimer(c, words)
            else -> setAlarm(c, words, text)
        }
    }

    // ---- Timers --------------------------------------------------------------------------------

    /** "set a 10 minute pasta timer", "timer for an hour and a half", "5 min timer". */
    private fun setTimer(c: Context, words: List<String>): String? {
        val (ms, used) = duration(words) ?: return if (words.size <= 4) "For how long? Say, for example, “set a 10 minute timer”." else null
        if (ms < 1_000 || ms > 24 * 3_600_000L) return "Timers can be from a second up to 24 hours."
        val label = words.filterIndexed { i, w -> i !in used && w !in FILLER && w !in UNITS }.joinToString(" ")
        Timers.addTimer(c, ms, label)
        val said = if (label.isNotBlank()) "$label timer, ${Timers.spoken(ms)}, starting now." else "${Timers.spoken(ms)} timer, starting now."
        return said.replaceFirstChar { it.uppercase() }
    }

    /**
     * Finds a length of time in the words: "10 minutes", "an hour and a half", "1 hour 30 minutes",
     * "half an hour", "90 seconds". Returns it and which words it took, or null if there's none.
     */
    private fun duration(words: List<String>): Pair<Long, Set<Int>>? {
        var total = 0.0
        val used = mutableSetOf<Int>()
        var i = 0
        while (i < words.size) {
            // "half an hour"
            if (words[i] == "half" && i + 2 < words.size && words[i + 1] in setOf("a", "an") && words[i + 2] in UNITS) {
                total += 0.5 * UNITS.getValue(words[i + 2])
                used += i..i + 2
                i += 3
                continue
            }
            val n = number(words, i)
            if (n != null) {
                val (value, next) = n
                if (next < words.size && words[next] in UNITS) {
                    val unit = UNITS.getValue(words[next])
                    total += value * unit
                    used += i..next
                    i = next + 1
                    // "an hour and a half"
                    if (i + 2 < words.size && words[i] == "and" && words[i + 1] == "a" && words[i + 2] == "half") {
                        total += 0.5 * unit
                        used += i..i + 2
                        i += 3
                    }
                    continue
                }
                // "one and a half hours"
                if (next + 3 < words.size && words[next] == "and" && words[next + 1] == "a" && words[next + 2] == "half" && words[next + 3] in UNITS) {
                    total += (value + 0.5) * UNITS.getValue(words[next + 3])
                    used += i..next + 3
                    i = next + 4
                    continue
                }
            }
            i++
        }
        return if (used.isEmpty()) null else Math.round(total) to used
    }

    /**
     * A number starting at [i]: digits ("10", "1.5"), words ("twenty five"), or "a"/"an" before a unit.
     * Returns it and the index after it.
     */
    private fun number(words: List<String>, i: Int): Pair<Double, Int>? {
        val w = words[i]
        w.toDoubleOrNull()?.let { return it to i + 1 }
        if ((w == "a" || w == "an") && i + 1 < words.size && words[i + 1] in UNITS) return 1.0 to i + 1
        if (w == "couple" || (w == "a" && words.getOrNull(i + 1) == "couple")) {
            val at = if (w == "a") i + 2 else i + 1
            return 2.0 to if (words.getOrNull(at) == "of") at + 1 else at
        }
        TENS[w]?.let { tens ->
            val one = words.getOrNull(i + 1)?.let { ONES[it] }
            return if (one != null && one in 1..9) (tens + one).toDouble() to i + 2 else tens.toDouble() to i + 1
        }
        ONES[w]?.let { if (w != "oh") return it.toDouble() to i + 1 }
        if (w == "hundred") return 100.0 to i + 1
        return null
    }

    // ---- Alarms --------------------------------------------------------------------------------

    /** "wake me up at 6:45", "set an alarm for 7 pm", "alarm at half past six", "alarm for noon". */
    private fun setAlarm(c: Context, words: List<String>, text: String): String? {
        val (hour, minute) = time(words, morning = text.startsWith("wake me")) ?: return if (words.size <= 5) "For what time? Say, for example, “wake me up at 6:30”." else null
        val item = Timers.addAlarm(c, hour, minute)
        return "Alarm set for ${Timers.clock(c, item.at)} ${Timers.day(item.at)}."
    }

    /**
     * Finds a time of day: "6:45", "6 45", "six forty five", "7 pm", "seven o'clock", "half past six",
     * "quarter to seven", "noon", "midnight". Without am or pm it picks the sooner of the two, except for
     * "wake me up", which means the morning. Returns the hour (0–23) and minute, or null.
     */
    private fun time(words: List<String>, morning: Boolean): Pair<Int, Int>? {
        val text = words.joinToString(" ")
        if (Regex("""\bnoon\b|\bmidday\b""").containsMatchIn(text)) return 12 to 0
        if (Regex("""\bmidnight\b""").containsMatchIn(text)) return 0 to 0
        val pm = Regex("""\b(pm|evening|tonight|afternoon|night)\b""").containsMatchIn(text)
        val am = Regex("""\b(am|morning)\b""").containsMatchIn(text)

        var hour: Int? = null
        var minute = 0
        // "half past six", "quarter past seven", "quarter to seven", "ten to seven", "twenty five past six"
        for (at in 1 until words.size - 1) {
            val way = words[at]
            if (way !in setOf("past", "after", "to", "till", "before")) continue
            val h = number(words, at + 1)?.first?.toInt() ?: continue
            val before = words[at - 1]
            val m = when {
                before == "half" -> 30
                before == "quarter" -> 15
                at >= 2 && words[at - 2] in TENS && (ONES[before] ?: 0) in 1..9 -> TENS.getValue(words[at - 2]) + ONES.getValue(before)
                else -> number(words, at - 1)?.takeIf { it.second == at }?.first?.toInt()
            } ?: continue
            if (h !in 0..23 || m !in 1..59) continue
            if (way in setOf("past", "after")) {
                hour = h; minute = m
            } else {
                hour = (h + 23) % 24; minute = 60 - m
            }
            break
        }
        if (hour == null) {
            // "6:45", "6.45", "645" and "6 45", "six forty five", "seven o'clock"
            val colon = Regex("""\b(\d{1,2})[:.](\d{2})\b""").find(text)
            if (colon != null) {
                hour = colon.groupValues[1].toInt(); minute = colon.groupValues[2].toInt()
            } else {
                val start = words.indexOfFirst { it in setOf("at", "for", "up") }.let { if (it < 0) 0 else it + 1 }
                var i = start
                while (i < words.size && hour == null) {
                    val n = number(words, i)
                    if (n != null && n.first.toInt() in 0..23 && n.first % 1 == 0.0 && words.getOrNull(n.second) !in UNITS) {
                        hour = n.first.toInt()
                        var next = n.second
                        if (words.getOrNull(next) in setOf("oclock", "o")) next++
                        // "six oh five"
                        if (words.getOrNull(next) == "oh") number(words, next + 1)?.let { (m, _) -> minute = m.toInt() }
                        else number(words, next)?.let { (m, _) -> if (m.toInt() in 0..59 && m % 1 == 0.0) minute = m.toInt() }
                    } else if (n != null && n.first.toInt() in 100..2359 && words[i].length in 3..4) {
                        // "645", "1830"
                        hour = n.first.toInt() / 100; minute = n.first.toInt() % 100
                    }
                    i++
                }
            }
        }
        val h = hour ?: return null
        if (h !in 0..23 || minute !in 0..59) return null
        return when {
            h > 12 || h == 0 -> h to minute
            pm -> (if (h == 12) 12 else h + 12) to minute
            am -> (if (h == 12) 0 else h) to minute
            morning -> (if (h == 12) 0 else h) to minute
            else -> sooner(h, minute)
        }
    }

    /** Without am or pm: whichever of h and h + 12 comes first from now. */
    private fun sooner(h: Int, minute: Int): Pair<Int, Int> {
        val now = Calendar.getInstance()
        val nowMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val morning = (if (h == 12) 0 else h) * 60 + minute
        val evening = morning + 12 * 60
        fun wait(at: Int) = (at - nowMinutes + 24 * 60) % (24 * 60)
        return if (wait(morning) <= wait(evening)) (morning / 60) to minute else (evening / 60) to minute
    }

    // ---- Asking and cancelling -----------------------------------------------------------------

    /** "how long is left", "how long is left on the pasta timer", "when is my alarm", "what timers do I have". */
    private fun status(c: Context, kind: CountdownKind, words: List<String>): String {
        val all = Timers.all(c).filter { it.kind == kind }
        val items = named(all, words).ifEmpty { all }
        val now = System.currentTimeMillis()
        return when (kind) {
            CountdownKind.TIMER -> when (items.size) {
                0 -> "You don't have any timers."
                1 -> "${Timers.spoken(items[0].at - now)} left on your ${items[0].name(c)}."
                else -> "You have ${items.size} timers: " + items.joinToString("; ") { "${it.name(c)}, ${Timers.spoken(it.at - now)} left" } + "."
            }
            CountdownKind.REMINDER, CountdownKind.ALARM -> when (items.size) {
                0 -> "You don't have any alarms."
                1 -> "Your alarm is set for ${Timers.clock(c, items[0].at)} ${Timers.day(items[0].at)}."
                else -> "You have ${items.size} alarms: " + items.joinToString("; ") { "${Timers.clock(c, it.at)} ${Timers.day(it.at)}" } + "."
            }
        }
    }

    /** "cancel the timer", "cancel all alarms", "stop the pasta timer", "turn off my alarm". */
    private fun cancel(c: Context, words: List<String>, kind: CountdownKind): String {
        // Saying it while one rings means that one.
        Ringing.now.value?.let { ringing ->
            if (ringing.kind == kind) {
                Ringing.stop(c)
                return "Stopped."
            }
        }
        val items = Timers.all(c).filter { it.kind == kind }
        val noun = if (kind == CountdownKind.TIMER) "timer" else "alarm"
        if (items.isEmpty()) return "You don't have any ${noun}s."
        val all = words.any { it == "all" || it == "every" || it == "${noun}s" }
        if (all || items.size == 1) {
            items.forEach { Timers.cancel(c, it.id) }
            return if (items.size == 1) "Cancelled your ${items[0].name(c)}." else "Cancelled all ${items.size} ${noun}s."
        }
        // One of several: by its name ("the pasta timer") or its time ("the 7 o'clock alarm"; without am
        // or pm, either).
        val match = named(items, words).singleOrNull() ?: time(words, morning = false)?.let { (h, m) ->
            items.singleOrNull { item ->
                val at = Calendar.getInstance().apply { timeInMillis = item.at }
                at.get(Calendar.MINUTE) == m && at.get(Calendar.HOUR_OF_DAY) % 12 == h % 12
            }
        }
        if (match != null) {
            Timers.cancel(c, match.id)
            return "Cancelled your ${match.name(c)}."
        }
        return "You have ${items.size} ${noun}s: " + items.joinToString(", ") { it.name(c) } +
            ". Which one? Or say “cancel all ${noun}s”."
    }

    /** The ones whose name is in the words ("the pasta timer"). */
    private fun named(items: List<Countdown>, words: List<String>): List<Countdown> =
        items.filter { item -> item.label.isNotBlank() && item.label.split(" ").all { it in words } }

    /**
     * The sentence as plain lowercase words: punctuation gone, "10min" split into "10 min", "a.m." as
     * "am", "o'clock" as "oclock", hyphens as spaces. Colons and points inside numbers stay ("6:45").
     */
    internal fun words(text: String): List<String> = text.lowercase()
        .replace("a.m.", "am").replace("p.m.", "pm").replace("o'clock", "oclock").replace("o’clock", "oclock")
        .replace("’", "'").replace("'", "")
        .replace(Regex("""(\d)([a-z])"""), "$1 $2")
        .replace(Regex("""[^a-z0-9:. ]"""), " ")
        .replace(Regex("""(?<!\d)[:.]|[:.](?!\d)"""), " ")
        .split(Regex("""\s+""")).filter { it.isNotBlank() }
}
