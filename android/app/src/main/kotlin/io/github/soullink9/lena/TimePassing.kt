package io.github.soullink9.lena

import org.json.JSONObject
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * How much time passed between the messages of a chat, told to her so she talks like someone who noticed.
 * The Ask screen's chat with her never ends, and a model reads every message as if it came a moment ago:
 * without this, a question after three days got an answer that picked up where the last talk left off.
 *
 * The thread she's sent gets a short note wherever [NEW_CONVERSATION] or more passed between two
 * messages, and one just before your new message saying how long it's been and that the earlier talk is
 * over. They go with this request only; the chat itself is unchanged. The Open WebUI filter does the same
 * for Open WebUI's own page, in the same words (`open-webui/lena_filter.py`); it leaves the app's
 * requests alone, since the app has done it already. The Modelfile tells her what the notes mean.
 */
object TimePassing {
    /** A pause this long or longer ends a conversation: what came before it is an earlier one. */
    val NEW_CONVERSATION: Duration = Duration.ofMinutes(30)

    /**
     * The key a thread's message carries its time under (milliseconds) until [annotate] takes it off.
     * It's never sent: Open WebUI and the model know nothing of it.
     */
    const val AT = "lena_at"

    /**
     * The [thread] (oldest first, your new message last) with a note before each message that came after a
     * pause of [NEW_CONVERSATION] or more, and the [AT] times taken off. Messages without a time get no note
     * and don't count as the latest one.
     */
    fun annotate(thread: List<JSONObject>, zone: ZoneId = ZoneId.systemDefault()): List<JSONObject> {
        val out = mutableListOf<JSONObject>()
        var previous: Long? = null
        for ((i, message) in thread.withIndex()) {
            val at = if (message.has(AT)) message.optLong(AT) else null
            message.remove(AT)
            val last = previous
            if (last != null && at != null && at - last >= NEW_CONVERSATION.toMillis()) {
                val then = Instant.ofEpochMilli(last).atZone(zone)
                val now = Instant.ofEpochMilli(at).atZone(zone)
                val text = if (i == thread.lastIndex) beforeNewMessage(then, now) else between(then, now)
                out.add(JSONObject().put("role", "system").put("content", text))
            }
            out.add(message)
            if (at != null) previous = at
        }
        return out
    }

    /** The note before your new message: how long it's been, and that the earlier conversation is over. */
    fun beforeNewMessage(then: ZonedDateTime, now: ZonedDateTime): String =
        "It's been ${since(Duration.between(then, now))} since the last message in this chat " +
            "(${whenWas(then, now)}). That conversation is over: answer what they say now, and don't go back " +
            "to its topic unless they do. What was said then was true then, not necessarily now."

    /** The note between two earlier messages with a long pause between them. */
    fun between(then: ZonedDateTime, next: ZonedDateTime): String =
        "Time passed: the next message is from ${next.format(DAY_AND_TIME)}, " +
            "${since(Duration.between(then, next))} after the one above."

    /**
     * A pause the way a person says it: "45 minutes", "an hour", "3 hours", "a day", "5 days", "2 weeks",
     * "4 months", "over a year", to the nearest of each.
     */
    fun since(gap: Duration): String {
        val minutes = gap.toMinutes().coerceAtLeast(1)
        val days = (minutes + 720) / 1440
        return when {
            minutes < 60 -> count(minutes, "a minute", "minutes")
            minutes < 23 * 60 + 30 -> count((minutes + 30) / 60, "an hour", "hours")
            days < 14 -> count(days, "a day", "days")
            days < 60 -> count((days + 3) / 7, "a week", "weeks")
            days < 365 -> count((days + 15) / 30, "a month", "months")
            else -> "over a year"
        }
    }

    /**
     * When the earlier message was, from [now]: "earlier today", "yesterday evening", "Thursday
     * evening" within the week, else its date ("November 2", with the year if it was another year).
     */
    fun whenWas(then: ZonedDateTime, now: ZonedDateTime): String {
        val days = ChronoUnit.DAYS.between(then.toLocalDate(), now.toLocalDate())
        return when {
            days <= 0L -> "earlier today"
            days == 1L -> "yesterday ${partOfDay(then.hour)}"
            days < 7L -> "${then.format(WEEKDAY)} ${partOfDay(then.hour)}"
            then.year == now.year -> then.format(DATE)
            else -> then.format(DATE_AND_YEAR)
        }
    }

    /** The part of the day an hour falls in, the same split as the phone's time note to her. */
    fun partOfDay(hour: Int): String = when (hour) {
        in 5..11 -> "morning"
        in 12..16 -> "afternoon"
        in 17..20 -> "evening"
        else -> "night"
    }

    private fun count(n: Long, one: String, many: String) = if (n == 1L) one else "$n $many"

    private val DAY_AND_TIME = DateTimeFormatter.ofPattern("EEEE, MMMM d, h:mm a", Locale.US)
    private val WEEKDAY = DateTimeFormatter.ofPattern("EEEE", Locale.US)
    private val DATE = DateTimeFormatter.ofPattern("MMMM d", Locale.US)
    private val DATE_AND_YEAR = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.US)
}
