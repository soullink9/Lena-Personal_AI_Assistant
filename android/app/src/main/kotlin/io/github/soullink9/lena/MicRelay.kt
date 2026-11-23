package io.github.soullink9.lena

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import java.io.Closeable
import java.io.FileInputStream
import java.io.IOException
import kotlin.math.sqrt

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

/**
 * Lena's own microphone, piped to the phone's recognizer for one question ([PhoneEar]), the same way the
 * wake word hands its microphone on ([MicRelay]). The recognizer then never opens the microphone itself:
 * on a Galaxy Z Fold6 it stopped getting any sound that way (the level never moved, and it heard nothing),
 * while Lena's own recording, the one Open WebUI's mic button uses, still worked. The words still come
 * from the phone's own speech-to-text, and the sound never leaves the phone.
 *
 * [onLevel] gets the loudness of each 20 ms (0 to 1), for the orb: the recognizer may not report a level
 * for sound it's given. Returns null when the microphone can't be opened; the recognizer then opens its own.
 */
object MicFeed {
    private const val FRAME = MicRelay.RATE / 50 // 20 ms
    private const val MAX_MS = 45_000L

    @SuppressLint("MissingPermission") // the sheet asks for the microphone before it listens
    fun open(onLevel: (Float) -> Unit): MicRelay.Relay? {
        val min = AudioRecord.getMinBufferSize(MicRelay.RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = runCatching {
            AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, MicRelay.RATE, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, maxOf(min, FRAME * 2 * 10))
        }.getOrNull()
        if (rec == null || rec.state != AudioRecord.STATE_INITIALIZED) {
            Logs.w("Voice", "Couldn't open the microphone for the phone's recognizer; it opens its own")
            rec?.release()
            return null
        }
        val (read, write) = ParcelFileDescriptor.createPipe()
        val stopped = java.util.concurrent.atomic.AtomicBoolean(false)
        val relay = MicRelay.Relay(read) { stopped.set(true) }
        Thread({
            val out = ParcelFileDescriptor.AutoCloseOutputStream(write)
            val buf = ShortArray(FRAME)
            val bytes = ByteArray(FRAME * 2)
            val until = SystemClock.elapsedRealtime() + MAX_MS
            try {
                rec.startRecording()
                while (!stopped.get() && SystemClock.elapsedRealtime() < until) {
                    val n = rec.read(buf, 0, FRAME)
                    if (n <= 0) break
                    var sum = 0.0
                    for (i in 0 until n) {
                        val v = buf[i].toInt()
                        sum += (v * v).toDouble()
                        bytes[2 * i] = (v and 0xff).toByte()
                        bytes[2 * i + 1] = ((v shr 8) and 0xff).toByte()
                    }
                    onLevel((sqrt(sum / n) / 3_000.0).toFloat().coerceIn(0f, 1f))
                    out.write(bytes, 0, n * 2)
                }
            } catch (_: IOException) {
                // The recognizer closed its side: it has what it needs.
            } catch (e: IllegalStateException) {
                Logs.w("Voice", "The microphone stopped recording for the phone's recognizer", e)
            } finally {
                runCatching { rec.stop() }
                rec.release()
                runCatching { out.close() }
            }
        }, "lena-mic-feed").start()
        return relay
    }
}
