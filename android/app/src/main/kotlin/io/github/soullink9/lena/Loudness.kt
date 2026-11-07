package io.github.soullink9.lena

import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import java.util.Calendar
import kotlin.math.log10
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * How noisy the room is, measured by the wake word as it listens (no extra microphone use): a slow
 * average of the sound level, in dB below full scale (about -70 in a quiet room, -40 with a TV on).
 */
object RoomNoise {
    private const val FRESH_MS = 2 * 60_000L

    @Volatile private var level = -70.0
    @Volatile private var measured = 0L

    /** Takes 80 ms of sound into the average (from the wake word's listening loop). */
    fun hear(chunk: ShortArray) {
        var sum = 0.0
        for (s in chunk) sum += s.toDouble() * s
        val rms = sqrt(sum / chunk.size).coerceAtLeast(1.0)
        val db = 20 * log10(rms / 32768.0)
        // Rises quickly with noise, falls slowly, so a pause in the TV doesn't count as quiet.
        level += (db - level) * if (db > level) 0.05 else 0.01
        measured = SystemClock.elapsedRealtime()
    }

    /** The room's level, or null if the wake word hasn't listened lately. */
    fun now(): Double? = level.takeIf { SystemClock.elapsedRealtime() - measured < FRESH_MS }
}

/**
 * Her speaking volume on this device: set by her set_volume tool ("speak more quietly"), and, with
 * Settings → Talking to Lena → Adjust her volume, changed while she speaks: quieter early in the morning
 * and late at night, louder when the room is noisy. It goes back to where it was once she's done.
 *
 * Her voice plays as the assistant (USAGE_ASSISTANT), which Android puts on the media volume.
 */
object Loudness {
    private const val STREAM = AudioManager.STREAM_MUSIC
    private const val STEP = 15
    private const val NOISY_DB = -45.0

    private fun audio(c: Context) = c.getSystemService(AudioManager::class.java)

    /** The volume now, 0 to 100. */
    fun level(c: Context): Int {
        val a = audio(c)
        val max = a.getStreamMaxVolume(STREAM).coerceAtLeast(1)
        return (a.getStreamVolume(STREAM) * 100.0 / max).roundToInt()
    }

    /** Sets the volume (0 to 100); [log] for a change someone asked for. */
    fun set(c: Context, percent: Int, log: Boolean = true) {
        val a = audio(c)
        val max = a.getStreamMaxVolume(STREAM)
        a.setStreamVolume(STREAM, (percent.coerceIn(0, 100) * max / 100.0).roundToInt(), 0)
        if (log) Logs.i("Volume", "Set to ${percent.coerceIn(0, 100)}%")
    }

    /** A step quieter or louder. */
    fun change(c: Context, louder: Boolean) = set(c, level(c) + if (louder) STEP else -STEP)

    /** Whether it's early morning or late night, when she speaks more quietly (before 8 AM, from 10 PM). */
    private fun quietHours(): Boolean {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return hour < 8 || hour >= 22
    }

    /**
     * Runs [block] (her speaking) at the volume that suits the moment, then puts the volume back. Does
     * nothing with Adjust her volume off, or when neither the hour nor the room calls for a change.
     */
    suspend fun <T> whileSpeaking(c: Context, block: suspend () -> T): T {
        if (!Prefs.autoVolume(c)) return block()
        val before = runCatching { level(c) }.getOrNull() ?: return block()
        val noise = RoomNoise.now()
        val target = when {
            noise != null && noise > NOISY_DB -> (before + 25).coerceAtMost(100)
            quietHours() -> (before * 0.6).roundToInt().coerceAtLeast(10)
            else -> before
        }
        if (target == before) return block()
        Logs.i("Volume", "Speaking at ${target}% (${if (target > before) "the room is noisy" else "quiet hours"})")
        runCatching { set(c, target, log = false) }
        try {
            return block()
        } finally {
            runCatching { set(c, before, log = false) }
        }
    }
}
