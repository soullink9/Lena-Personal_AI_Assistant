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
 * Every change she makes stays between her quietest and loudest (Settings, under Adjust her volume; each
 * kind of device has its own unless you choose, see [Prefs.quietestVolume]), so she's never silent. On a TV
 * box that matters most: its volume is the volume of everything on the TV.
 *
 * Her voice plays as the assistant (USAGE_ASSISTANT), which Android puts on the media volume.
 */
object Loudness {
    private const val STREAM = AudioManager.STREAM_MUSIC
    private const val STEP = 15
    private const val NOISY_DB = -45.0

    /**
     * Where the volume goes back to when she's done, kept on disk as well: if Lena is closed while she
     * speaks (the TV's Home button, Android stopping her), the next time she speaks puts it back first.
     */
    private const val SAVED = "lena_volume"

    private val lock = Any()

    /** How many replies are being spoken at once: one may start before the last has quite finished. */
    private var speaking = 0

    private fun audio(c: Context) = c.getSystemService(AudioManager::class.java)

    private fun saved(c: Context) = c.getSharedPreferences(SAVED, Context.MODE_PRIVATE)

    /** The volume now, 0 to 100. */
    fun level(c: Context): Int {
        val a = audio(c)
        val max = a.getStreamMaxVolume(STREAM).coerceAtLeast(1)
        return (a.getStreamVolume(STREAM) * 100.0 / max).roundToInt()
    }

    /** Her quietest to her loudest on this device; a quietest above the loudest counts as the loudest. */
    fun limits(c: Context): IntRange {
        val loudest = Prefs.loudestVolume(c).coerceIn(0, 100)
        return Prefs.quietestVolume(c).coerceIn(0, loudest)..loudest
    }

    /**
     * Sets the volume (0 to 100), within her quietest and loudest; [log] for a change someone asked for.
     * Returns Android's volume step it set, or null where the volume is fixed (some TV boxes leave it to the
     * TV, over HDMI).
     */
    fun set(c: Context, percent: Int, log: Boolean = true): Int? {
        val wanted = percent.coerceIn(limits(c))
        if (log && wanted != percent.coerceIn(0, 100)) Logs.i("Volume", "Asked for ${percent.coerceIn(0, 100)}%; kept within her limits (Settings → Talking to Lena)")
        return setExactly(c, wanted, log)
    }

    /** Sets the volume as given, without her limits: for putting yours back. */
    private fun setExactly(c: Context, percent: Int, log: Boolean): Int? {
        val a = audio(c)
        if (a.isVolumeFixed) {
            if (log) Logs.i("Volume", "This device's volume is fixed; change it on the TV or speaker")
            return null
        }
        val max = a.getStreamMaxVolume(STREAM)
        val step = (percent.coerceIn(0, 100) * max / 100.0).roundToInt()
        a.setStreamVolume(STREAM, step, 0)
        if (log) Logs.i("Volume", "Set to ${percent.coerceIn(0, 100)}%")
        return step
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
     * nothing with Adjust her volume off.
     */
    suspend fun <T> whileSpeaking(c: Context, block: suspend () -> T): T {
        if (!Prefs.autoVolume(c)) return block()
        runCatching { start(c) }.onFailure { Logs.w("Volume", "Couldn't set her speaking volume", it) }
        try {
            return block()
        } finally {
            runCatching { done(c) }.onFailure { Logs.w("Volume", "Couldn't put the volume back", it) }
        }
    }

    /**
     * She starts speaking: the volume for the moment, within her limits. A reply that starts while another
     * is still being spoken keeps the volume as it is. Before, each one lowered it again from the already
     * lowered volume and the first to finish put back the wrong one, so in quiet hours a conversation
     * ratcheted the volume down to nothing.
     */
    private fun start(c: Context) {
        synchronized(lock) {
            speaking++
            if (speaking > 1) return
            putBack(c) // left from a time she was closed mid-sentence
            if (audio(c).isVolumeFixed) return
            val before = level(c)
            val noise = RoomNoise.now()
            val wanted = when {
                noise != null && noise > NOISY_DB -> before + 25
                quietHours() -> (before * 0.6).roundToInt()
                else -> before
            }
            // Within her limits even when the moment asks for no change: at 0 she couldn't be heard at all.
            val target = wanted.coerceIn(limits(c))
            if (target == before) return
            val why = when {
                target > before && wanted <= before -> "her quietest"
                target < before && wanted >= before -> "her loudest"
                target > before -> "the room is noisy"
                else -> "quiet hours"
            }
            Logs.i("Volume", "Speaking at ${target}% (${why}; was ${before}%)")
            val step = setExactly(c, target, log = false) ?: return
            saved(c).edit().putInt("put_back", before).putInt("set_step", step).apply()
        }
    }

    /** She's done speaking: the volume goes back, once the last reply being spoken has finished. */
    private fun done(c: Context) {
        synchronized(lock) {
            speaking = (speaking - 1).coerceAtLeast(0)
            if (speaking == 0) putBack(c)
        }
    }

    /**
     * Puts back the volume from before she spoke, if she changed it. If someone changed it meanwhile (the
     * remote's volume, "speak louder"), theirs stays.
     */
    private fun putBack(c: Context) {
        val kept = saved(c)
        val before = kept.getInt("put_back", -1)
        val step = kept.getInt("set_step", -1)
        if (before < 0) return
        kept.edit().clear().apply()
        if (audio(c).getStreamVolume(STREAM) == step) setExactly(c, before, log = false)
        else Logs.i("Volume", "The volume was changed while she spoke; it stays")
    }
}
