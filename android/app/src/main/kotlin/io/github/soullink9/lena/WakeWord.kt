package io.github.soullink9.lena

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** One wake word model: fed 80 ms of 16 kHz audio at a time, it says how sure it is. */
interface WakeEngine : Closeable {
    /** At or above this, she was called. */
    val threshold: Float
    fun score(chunk: ShortArray): Float
}

/** The wake words: picking one, and whether this build of the app has its model. */
object WakeWord {
    const val CHUNK = 1280 // 80 ms at 16 kHz
    private const val DIR = "wakeword"

    fun available(c: Context, phrase: WakePhrase = Prefs.wakePhrase(c)): Boolean =
        runCatching { c.assets.list(DIR)?.contains(phrase.file) == true }.getOrDefault(false)

    fun open(c: Context, phrase: WakePhrase = Prefs.wakePhrase(c)): WakeEngine = when (phrase) {
        WakePhrase.LENA -> MicroWakeWord(c, phrase.file)
        WakePhrase.JARVIS -> OpenWakeWord(c, phrase.file)
    }

    internal fun load(c: Context, name: String): ByteBuffer {
        val bytes = c.assets.open("$DIR/$name").use { it.readBytes() }
        return ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).put(bytes).also { it.rewind() }
    }

    internal fun floats(n: Int): ByteBuffer = ByteBuffer.allocateDirect(4 * n).order(ByteOrder.nativeOrder())
}

/**
 * "Lena": a microWakeWord model, the kind Home Assistant's voice satellites run. Its audio goes
 * through [MicroFrontend] (the same features an ESP32 computes, bit for bit), 40 numbers every
 * 10 ms; every few frames the model gives a probability, and the average of the last few has to
 * pass the model's cutoff. The model keeps its own memory between calls (a streaming model).
 */
class MicroWakeWord(c: Context, file: String) : WakeEngine {
    // From the model's .json: probability_cutoff and sliding_window_size.
    override val threshold = 0.76f
    private val window = 3

    private val model = Interpreter(WakeWord.load(c, file))
    private val inTensor = model.getInputTensor(0)
    private val outTensor = model.getOutputTensor(0)
    private val stride = inTensor.shape().let { if (it.size >= 2) it[1] else 3 }
    private val inScale = inTensor.quantizationParams().scale.toDouble()
    private val inZero = inTensor.quantizationParams().zeroPoint
    private val inSigned = inTensor.dataType() == DataType.INT8
    private val outScale = outTensor.quantizationParams().scale
    private val outZero = outTensor.quantizationParams().zeroPoint
    private val outSigned = outTensor.dataType() == DataType.INT8

    private val frontend = MicroFrontend()
    private val pending = ArrayList<IntArray>()
    private val recent = ArrayDeque<Float>()
    private val input = ByteBuffer.allocateDirect(stride * MicroFrontend.CHANNELS).order(ByteOrder.nativeOrder())
    private val output = ByteBuffer.allocateDirect(outTensor.numBytes()).order(ByteOrder.nativeOrder())

    override fun score(chunk: ShortArray): Float {
        var best = 0f
        var at = 0
        while (at < chunk.size) {
            at += frontend.process(chunk, at, chunk.size - at)
            if (!frontend.ready) continue
            pending += frontend.features.copyOf()
            if (pending.size < stride) continue

            // The features as floats (value / 25.6), quantized the way the model expects.
            input.rewind()
            for (frame in pending) for (v in frame) {
                val q = Math.rint(v * 0.0390625 / inScale + inZero).toInt()
                input.put((if (inSigned) q.coerceIn(-128, 127) else q.coerceIn(0, 255)).toByte())
            }
            pending.clear()
            input.rewind()
            output.rewind()
            model.run(input, output)
            val raw = output.get(0).toInt().let { if (outSigned) it else it and 0xFF }
            recent.addLast((raw - outZero) * outScale)
            while (recent.size > window) recent.removeFirst()
            if (recent.size == window) best = maxOf(best, recent.average().toFloat())
        }
        return best
    }

    override fun close() = model.close()
}

/**
 * "Hey Jarvis", with openWakeWord's models. Every 80 ms of audio becomes eight rows of a mel
 * spectrogram; the last 76 rows become one 96-number speech embedding; the last 16 embeddings
 * (about 1.3 seconds) go to the wake word model, which scores 0 to 1.
 */
class OpenWakeWord(c: Context, file: String) : WakeEngine {
    override val threshold = 0.5f

    private companion object {
        const val CONTEXT = 480 // the melspectrogram looks a little further back
        const val ROWS = 76
        const val BINS = 32
        const val FEATURES = 16
        const val EMBEDDING = 96
    }

    private val mel = Interpreter(WakeWord.load(c, "melspectrogram.tflite")).apply {
        resizeInput(0, intArrayOf(1, WakeWord.CHUNK + CONTEXT))
        allocateTensors()
    }
    private val embed = Interpreter(WakeWord.load(c, "embedding_model.tflite"))
    private val word = Interpreter(WakeWord.load(c, file))

    private val melFrames = mel.getOutputTensor(0).shape().let { if (it.size >= 2) it[it.size - 2] else 8 }
    private val audio = FloatArray(WakeWord.CHUNK + CONTEXT)
    private val rows = ArrayDeque<FloatArray>().apply { repeat(ROWS) { add(FloatArray(BINS) { 1f }) } }
    private val features = ArrayDeque<FloatArray>()

    private val melIn = WakeWord.floats(WakeWord.CHUNK + CONTEXT)
    private val melOut = WakeWord.floats(melFrames * BINS)
    private val embedIn = WakeWord.floats(ROWS * BINS)
    private val embedOut = WakeWord.floats(EMBEDDING)
    private val wordIn = WakeWord.floats(FEATURES * EMBEDDING)
    private val wordOut = WakeWord.floats(1)

    override fun score(chunk: ShortArray): Float {
        System.arraycopy(audio, WakeWord.CHUNK, audio, 0, CONTEXT)
        for (i in 0 until WakeWord.CHUNK) audio[CONTEXT + i] = chunk[i].toFloat()

        melIn.rewind(); melIn.asFloatBuffer().put(audio)
        melOut.rewind()
        mel.run(melIn, melOut)
        melOut.rewind()
        val m = melOut.asFloatBuffer()
        repeat(melFrames) { rows.addLast(FloatArray(BINS) { m.get() / 10f + 2f }) } // openWakeWord's own scaling
        while (rows.size > ROWS) rows.removeFirst()

        embedIn.rewind()
        val e = embedIn.asFloatBuffer()
        rows.forEach { e.put(it) }
        embedOut.rewind()
        embed.run(embedIn, embedOut)
        embedOut.rewind()
        features.addLast(FloatArray(EMBEDDING).also { embedOut.asFloatBuffer().get(it) })
        while (features.size > FEATURES) features.removeFirst()
        if (features.size < FEATURES) return 0f

        wordIn.rewind()
        val w = wordIn.asFloatBuffer()
        features.forEach { w.put(it) }
        wordOut.rewind()
        word.run(wordIn, wordOut)
        wordOut.rewind()
        return wordOut.asFloatBuffer().get(0)
    }

    override fun close() {
        mel.close()
        embed.close()
        word.close()
    }
}

/**
 * Runs the chosen wake word on the microphone in the background until [stop]. Calls back on the main thread:
 * [onScore] about 12 times a second, [onHeard] once when it hears the phrase (and then stops,
 * freeing the mic for Lena), and [onSilenced] if Android gives it only silence, which is what
 * happens when the phone doesn't let an app listen from where it is (the screen saver, on some phones).
 */
class WakeListener(
    private val context: Context,
    private val onHeard: () -> Unit,
    private val onSilenced: () -> Unit = {},
    private val onScore: (Float) -> Unit = {},
) {
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var running = false
    private var thread: Thread? = null

    val isRunning: Boolean get() = running

    fun start(): Boolean {
        if (running) return true
        if (!WakeWord.available(context)) return false
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return false
        running = true
        thread = Thread({ loop() }, "lena-wake").also { it.start() }
        return true
    }

    fun stop() {
        running = false
        thread = null
    }

    @SuppressLint("MissingPermission") // checked in start()
    private fun loop() {
        val model = runCatching { WakeWord.open(context) }.getOrElse {
            running = false
            return
        }
        val min = AudioRecord.getMinBufferSize(16_000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val mic = runCatching {
            AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, 16_000, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, maxOf(min, WakeWord.CHUNK * 2 * 4))
        }.getOrNull()
        if (mic == null || mic.state != AudioRecord.STATE_INITIALIZED) {
            mic?.release()
            model.close()
            running = false
            return
        }
        try {
            mic.startRecording()
            val chunk = ShortArray(WakeWord.CHUNK)
            var silent = 0
            while (running) {
                var read = 0
                while (read < chunk.size && running) {
                    val n = mic.read(chunk, read, chunk.size - read)
                    if (n <= 0) break
                    read += n
                }
                if (read < chunk.size) break
                // A blocked microphone delivers exact zeros; real rooms never do.
                if (chunk.all { it.toInt() == 0 }) {
                    if (++silent == 40) { // about 3 seconds
                        running = false
                        main.post(onSilenced)
                        break
                    }
                } else silent = 0
                val s = model.score(chunk)
                main.post { onScore(s) }
                if (s >= model.threshold) {
                    running = false
                    main.post(onHeard)
                }
            }
        } finally {
            runCatching { mic.stop() }
            mic.release()
            model.close()
        }
    }
}
