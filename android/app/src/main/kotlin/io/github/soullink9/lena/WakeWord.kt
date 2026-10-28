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
import org.tensorflow.lite.Interpreter
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Listens for "Hey Jarvis" on the phone itself, with openWakeWord's models (run by LiteRT).
 * Nothing leaves the phone: the audio goes through three small models and is thrown away.
 *
 * The pipeline is openWakeWord's own streaming one: every 80 ms of 16 kHz audio becomes eight
 * rows of a mel spectrogram; the last 76 rows become one 96-number "speech embedding"; the last
 * 16 embeddings (about 1.3 seconds) go to the wake word model, which scores 0 to 1.
 */
class WakeWord(c: Context) : Closeable {

    companion object {
        const val PHRASE = "Hey Jarvis"
        const val CHUNK = 1280 // 80 ms at 16 kHz
        private const val CONTEXT = 480 // the melspectrogram looks a little further back
        private const val ROWS = 76
        private const val BINS = 32
        private const val FEATURES = 16
        private const val EMBEDDING = 96
        private const val DIR = "wakeword"
        private const val WORD = "hey_jarvis_v0.1.tflite"

        /** The models are added when the app is built; a build without them has no wake word. */
        fun available(c: Context): Boolean = runCatching { c.assets.list(DIR)?.contains(WORD) == true }.getOrDefault(false)

        private fun load(c: Context, name: String): ByteBuffer {
            val bytes = c.assets.open("$DIR/$name").use { it.readBytes() }
            return ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).put(bytes).also { it.rewind() }
        }

        private fun floats(n: Int): ByteBuffer = ByteBuffer.allocateDirect(4 * n).order(ByteOrder.nativeOrder())
    }

    private val mel = Interpreter(load(c, "melspectrogram.tflite")).apply {
        resizeInput(0, intArrayOf(1, CHUNK + CONTEXT))
        allocateTensors()
    }
    private val embed = Interpreter(load(c, "embedding_model.tflite"))
    private val word = Interpreter(load(c, WORD))

    private val melFrames = mel.getOutputTensor(0).shape().let { if (it.size >= 2) it[it.size - 2] else 8 }
    private val audio = FloatArray(CHUNK + CONTEXT)
    private val rows = ArrayDeque<FloatArray>().apply { repeat(ROWS) { add(FloatArray(BINS) { 1f }) } }
    private val features = ArrayDeque<FloatArray>()

    private val melIn = floats(CHUNK + CONTEXT)
    private val melOut = floats(melFrames * BINS)
    private val embedIn = floats(ROWS * BINS)
    private val embedOut = floats(EMBEDDING)
    private val wordIn = floats(FEATURES * EMBEDDING)
    private val wordOut = floats(1)

    /** Feeds the next 80 ms and returns how sure it is that someone just said the phrase. */
    fun score(chunk: ShortArray): Float {
        // Keep the last CONTEXT samples and add the new chunk (as int16 values in a float).
        System.arraycopy(audio, CHUNK, audio, 0, CONTEXT)
        for (i in 0 until CHUNK) audio[CONTEXT + i] = chunk[i].toFloat()

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
 * Runs [WakeWord] on the microphone in the background until [stop]. Calls back on the main thread:
 * [onScore] about 12 times a second, [onHeard] once when it hears the phrase (and then stops,
 * freeing the mic for Lena), and [onSilenced] if Android gives it only silence, which is what
 * happens when the phone doesn't let an app listen from where it is (the screen saver, on some phones).
 */
class WakeListener(
    private val context: Context,
    private val onHeard: () -> Unit,
    private val onSilenced: () -> Unit = {},
    private val onScore: (Float) -> Unit = {},
    private val threshold: Float = 0.5f,
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
        val model = runCatching { WakeWord(context) }.getOrElse {
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
                if (s >= threshold) {
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
