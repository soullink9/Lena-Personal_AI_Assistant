package io.github.soullink9.lena

import android.os.ParcelFileDescriptor
import android.os.SystemClock
import java.io.Closeable
import java.io.FileInputStream
import java.io.IOException

/**
 * The microphone handed from the wake word straight to the sheet, so it never goes off in between.
 *
 * Before, the wake word closed the microphone when it heard her name, the sheet opened, and the speech
 * recognizer opened it again: a second or two with no microphone, so what you said straight after her
 * name was lost, and sometimes the recognizer found the microphone still busy. Now the wake word keeps
 * recording and passes the sound on through a pipe ([offer]); the sheet takes it ([take]) and gives it to
 * the recognizer (Android's EXTRA_AUDIO_SOURCE) or to your server's speech-to-text. Closing the relay
 * ends the hand-off, and the wake word lets go of the microphone.
 *
 * The audio is 16 kHz, 16-bit, mono PCM, little-endian, the same the wake word listens to.
 */
object MicRelay {
    /** An offered relay not taken within this long is closed: the sheet didn't come. */
    private const val TAKE_WITHIN_MS = 8_000L

    const val RATE = 16_000

    /** The sound from the wake word's microphone, until [close]. */
    class Relay(val pipe: ParcelFileDescriptor, private val onClose: () -> Unit) : Closeable {
        val offeredAt = SystemClock.elapsedRealtime()
        @Volatile private var closed = false

        /** Ends the hand-off: the wake word stops recording for it. Safe to call more than once. */
        override fun close() {
            if (closed) return
            closed = true
            runCatching { pipe.close() }
            onClose()
        }
    }

    private var pending: Relay? = null

    /** The wake word heard her name and is passing its microphone on. Replaces an older, untaken relay. */
    @Synchronized
    fun offer(relay: Relay) {
        pending?.close()
        pending = relay
    }

    /** Whether a relay is waiting to be taken: the sheet then listens at once, without waiting for the mic. */
    @Synchronized
    fun waiting(): Boolean {
        val r = pending ?: return false
        if (SystemClock.elapsedRealtime() - r.offeredAt <= TAKE_WITHIN_MS) return true
        r.close()
        pending = null
        return false
    }

    /** Takes the waiting relay, if there's a fresh one. The taker must close it when done. */
    @Synchronized
    fun take(): Relay? {
        if (!waiting()) return null
        return pending.also { pending = null }
    }

    /** Closes a waiting relay nobody took (the sheet was closed before listening). */
    @Synchronized
    fun drop() {
        pending?.close()
        pending = null
    }

    /** Reads a relay as 16-bit samples, the way an AudioRecord would. */
    class Reader(private val relay: Relay) : Closeable {
        private val input = FileInputStream(relay.pipe.fileDescriptor)
        private var bytes = ByteArray(0)

        /** Fills up to [count] samples into [into] from [offset]; returns how many, or -1 at the end. */
        fun read(into: ShortArray, offset: Int, count: Int): Int {
            if (bytes.size < count * 2) bytes = ByteArray(count * 2)
            var got = 0
            try {
                while (got < count * 2) {
                    val n = input.read(bytes, got, count * 2 - got)
                    if (n < 0) break
                    got += n
                    if (got % 2 == 0 && got > 0) break // hand back whole samples as they come
                }
            } catch (_: IOException) {
                if (got == 0) return -1
            }
            if (got == 0) return -1
            val samples = got / 2
            for (i in 0 until samples) {
                into[offset + i] = ((bytes[2 * i].toInt() and 0xff) or (bytes[2 * i + 1].toInt() shl 8)).toShort()
            }
            return samples
        }

        override fun close() {
            runCatching { input.close() }
            relay.close()
        }
    }
}
