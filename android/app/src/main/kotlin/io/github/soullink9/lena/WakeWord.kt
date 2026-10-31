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
import org.json.JSONObject
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** What one 80 ms piece of audio told the wake word. */
data class WakeResult(
    /** How sure it is, 0 to 1 (the meter in Settings → Wake word → Try it). */
    val score: Float,
    /** It's her name and every check passed: wake up. */
    val heard: Boolean = false,
    /** It sounded like her name, but the voice check said nobody was speaking (a TV jingle, a clatter). */
    val notSpeech: Boolean = false,
)

/** One wake word model: fed 80 ms of 16 kHz audio at a time. */
interface WakeEngine : Closeable {
    /** At or above this (on average over the model's window), it's her name. */
    val threshold: Float
    fun listen(chunk: ShortArray): WakeResult
}

/** How sure she must be before she wakes: fewer false starts, or easier to wake from across the room. */
enum class WakeSensitivity(val label: String, val detail: String) {
    STRICT("Fewer false starts", "She needs to be surer, for longer. Speak clearly and a little louder."),
    BALANCED("Balanced", "The model's own setting"),
    RELAXED("Easier to wake", "Hears you more easily from across the room, but starts by mistake more often"),
}

/** The wake words: picking one, and whether this build of the app has its model. */
object WakeWord {
    const val CHUNK = 1280 // 80 ms at 16 kHz
    private const val DIR = "wakeword"

    fun available(c: Context, phrase: WakePhrase = Prefs.wakePhrase(c)): Boolean =
        runCatching { c.assets.list(DIR)?.contains(phrase.file) == true }.getOrDefault(false)

    fun open(c: Context, phrase: WakePhrase = Prefs.wakePhrase(c), sensitivity: WakeSensitivity = Prefs.wakeSensitivity(c)): WakeEngine =
        when (phrase) {
            WakePhrase.HEY_LENA, WakePhrase.LENA -> MicroWakeWord(c, phrase.file, sensitivity)
            WakePhrase.JARVIS -> OpenWakeWord(c, phrase.file, sensitivity)
        }

    internal fun has(c: Context, name: String) = runCatching { c.assets.list(DIR)?.contains(name) == true }.getOrDefault(false)

    internal fun load(c: Context, name: String): ByteBuffer {
        val bytes = c.assets.open("$DIR/$name").use { it.readBytes() }
        return ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).put(bytes).also { it.rewind() }
    }

    /** A microWakeWord model's own cutoff and window, from the .json that comes with it. */
    internal fun settings(c: Context, model: String, cutoff: Float, window: Int): Pair<Float, Int> = runCatching {
        val json = c.assets.open("$DIR/${model.removeSuffix(".tflite")}.json").use { String(it.readBytes()) }
        val micro = JSONObject(json).getJSONObject("micro")
        micro.optDouble("probability_cutoff", cutoff.toDouble()).toFloat() to micro.optInt("sliding_window_size", window)
    }.getOrDefault(cutoff to window)

    internal fun floats(n: Int): ByteBuffer = ByteBuffer.allocateDirect(4 * n).order(ByteOrder.nativeOrder())
}

/**
 * One streaming microWakeWord model (a wake word, or the voice activity model): fed the front
 * end's 40 numbers every 10 ms; every few frames (the model's stride) it gives a probability.
 * The model keeps its own memory between calls.
 */
private class MicroModel(c: Context, file: String) : Closeable {
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
    private val pending = ArrayList<IntArray>()
    private val input = ByteBuffer.allocateDirect(stride * MicroFrontend.CHANNELS).order(ByteOrder.nativeOrder())
    private val output = ByteBuffer.allocateDirect(outTensor.numBytes()).order(ByteOrder.nativeOrder())

    /** One frame of features in; a probability when the model ran, otherwise null. */
    fun feed(frame: IntArray): Float? {
        pending += frame
        if (pending.size < stride) return null
        // The features as floats (value / 25.6), quantized the way the model expects.
        input.rewind()
        for (f in pending) for (v in f) {
            val q = Math.rint(v * 0.0390625 / inScale + inZero).toInt()
            input.put((if (inSigned) q.coerceIn(-128, 127) else q.coerceIn(0, 255)).toByte())
        }
        pending.clear()
        input.rewind()
        output.rewind()
        model.run(input, output)
        val raw = output.get(0).toInt().let { if (outSigned) it else it and 0xFF }
        return (raw - outZero) * outScale
    }

    override fun close() = model.close()
}

/**
 * The voice activity model that comes with microWakeWord (ESPHome's): is someone speaking? A wake
 * word only counts when it says yes, so music, a TV jingle or a dropped pan can't wake her.
 */
private class VoiceActivity(c: Context) : Closeable {
    private companion object {
        const val GRACE = 100 // frames: a wake word is often recognised just after its last sound, so a second's grace
    }

    private val model = MicroModel(c, "vad.tflite")
    private val settings = WakeWord.settings(c, "vad.tflite", 0.5f, 5)
    private val recent = ArrayDeque<Float>()
    private var sinceVoice = Int.MAX_VALUE / 2

    /** One frame of features; call it for every frame, so the grace is counted in time. */
    fun feed(frame: IntArray) {
        sinceVoice++
        val p = model.feed(frame) ?: return
        recent.addLast(p)
        while (recent.size > settings.second) recent.removeFirst()
        if (recent.size == settings.second && recent.average() > settings.first) sinceVoice = 0
    }

    /** Someone spoke within the last second. */
    val speaking: Boolean get() = sinceVoice <= GRACE

    override fun close() = model.close()
}

/**
 * "Hey Lena" and "Lena": microWakeWord models, the kind Home Assistant's voice satellites run.
 * Audio goes through [MicroFrontend] (the same features an ESP32 computes, bit for bit), and it
 * decides the way ESPHome does: the average of the last few probabilities has to pass the model's
 * cutoff, and not in its first second of listening (the front end and the model are still
 * settling then, which is when a false start is likeliest), nor until it has calmed down after
 * the last time. ESPHome's voice activity model has to agree someone is speaking.
 */
class MicroWakeWord(c: Context, file: String, sensitivity: WakeSensitivity) : WakeEngine {
    private companion object {
        const val SETTLE = 100 // frames (10 ms each) below the cutoff before she can be woken, as in ESPHome
    }

    override val threshold: Float
    private val window: Int

    init {
        val (cutoff, size) = WakeWord.settings(c, file, 0.76f, 3)
        when (sensitivity) {
            WakeSensitivity.STRICT -> { threshold = cutoff + (1 - cutoff) / 2; window = size + 2 }
            WakeSensitivity.BALANCED -> { threshold = cutoff; window = size }
            WakeSensitivity.RELAXED -> { threshold = maxOf(0.5f, cutoff - (1 - cutoff) / 2); window = size }
        }
    }

    private val model = MicroModel(c, file)
    private val voice = if (WakeWord.has(c, "vad.tflite")) runCatching { VoiceActivity(c) }.getOrNull() else null
    private val frontend = MicroFrontend()
    private val recent = ArrayDeque<Float>()
    private var settle = -SETTLE

    override fun listen(chunk: ShortArray): WakeResult {
        var best = 0f
        var heard = false
        var notSpeech = false
        var at = 0
        while (at < chunk.size) {
            at += frontend.process(chunk, at, chunk.size - at)
            if (!frontend.ready) continue
            val frame = frontend.features.copyOf()
            voice?.feed(frame)
            model.feed(frame)?.let { p ->
                recent.addLast(p)
                while (recent.size > window) recent.removeFirst()
                if (recent.size == window) {
                    val average = recent.average().toFloat()
                    best = maxOf(best, average)
                    if (settle >= 0 && average > threshold && !heard) {
                        if (voice == null || voice.speaking) {
                            heard = true
                            recent.clear()
                            settle = -SETTLE
                        } else notSpeech = true
                    }
                }
            }
            // It only settles while it's sure it isn't hearing her name.
            if ((recent.lastOrNull() ?: 0f) < threshold) settle = minOf(settle + 1, 0)
        }
        return WakeResult(best, heard, notSpeech && !heard)
    }

    override fun close() {
        model.close()
        voice?.close()
    }
}

/**
 * "Hey Jarvis", with openWakeWord's models. Every 80 ms of audio becomes eight rows of a mel
 * spectrogram; the last 76 rows become one 96-number speech embedding; the last 16 embeddings
 * (about 1.3 seconds) go to the wake word model, which scores 0 to 1. The same voice activity
 * check as microWakeWord's applies, and it waits a second after starting.
 */
class OpenWakeWord(c: Context, file: String, sensitivity: WakeSensitivity) : WakeEngine {
    override val threshold = when (sensitivity) {
        WakeSensitivity.STRICT -> 0.7f
        WakeSensitivity.BALANCED -> 0.5f
        WakeSensitivity.RELAXED -> 0.35f
    }

    private companion object {
        const val CONTEXT = 480 // the melspectrogram looks a little further back
        const val ROWS = 76
        const val BINS = 32
        const val FEATURES = 16
        const val EMBEDDING = 96
        const val SETTLE = 12 // chunks (about a second) after the first full window
    }

    private val mel = Interpreter(WakeWord.load(c, "melspectrogram.tflite")).apply {
        resizeInput(0, intArrayOf(1, WakeWord.CHUNK + CONTEXT))
        allocateTensors()
    }
    private val embed = Interpreter(WakeWord.load(c, "embedding_model.tflite"))
    private val word = Interpreter(WakeWord.load(c, file))
    private val voice = if (WakeWord.has(c, "vad.tflite")) runCatching { VoiceActivity(c) }.getOrNull() else null
    private val frontend = MicroFrontend()

    private val melFrames = mel.getOutputTensor(0).shape().let { if (it.size >= 2) it[it.size - 2] else 8 }
    private val audio = FloatArray(WakeWord.CHUNK + CONTEXT)
    private val rows = ArrayDeque<FloatArray>().apply { repeat(ROWS) { add(FloatArray(BINS) { 1f }) } }
    private val features = ArrayDeque<FloatArray>()
    private var settle = SETTLE

    private val melIn = WakeWord.floats(WakeWord.CHUNK + CONTEXT)
    private val melOut = WakeWord.floats(melFrames * BINS)
    private val embedIn = WakeWord.floats(ROWS * BINS)
    private val embedOut = WakeWord.floats(EMBEDDING)
    private val wordIn = WakeWord.floats(FEATURES * EMBEDDING)
    private val wordOut = WakeWord.floats(1)

    override fun listen(chunk: ShortArray): WakeResult {
        if (voice != null) {
            var at = 0
            while (at < chunk.size) {
                at += frontend.process(chunk, at, chunk.size - at)
                if (frontend.ready) voice.feed(frontend.features.copyOf())
            }
        }
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
        if (features.size < FEATURES) return WakeResult(0f)

        wordIn.rewind()
        val w = wordIn.asFloatBuffer()
        features.forEach { w.put(it) }
        wordOut.rewind()
        word.run(wordIn, wordOut)
        wordOut.rewind()
        val score = wordOut.asFloatBuffer().get(0)
        if (settle > 0) {
            settle--
            return WakeResult(score)
        }
        if (score < threshold) return WakeResult(score)
        if (voice != null && !voice.speaking) return WakeResult(score, notSpeech = true)
        settle = SETTLE
        return WakeResult(score, heard = true)
    }

    override fun close() {
        mel.close()
        embed.close()
        word.close()
        voice?.close()
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
        val phrase = Prefs.wakePhrase(context)
        val model = runCatching { WakeWord.open(context) }.getOrElse {
            Logs.e("Wake word", "Couldn't load the ${phrase.label} model", it)
            running = false
            return
        }
        val min = AudioRecord.getMinBufferSize(16_000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val mic = runCatching {
            AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, 16_000, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, maxOf(min, WakeWord.CHUNK * 2 * 4))
        }.getOrNull()
        if (mic == null || mic.state != AudioRecord.STATE_INITIALIZED) {
            Logs.w("Wake word", "Couldn't open the microphone")
            mic?.release()
            model.close()
            running = false
            return
        }
        try {
            mic.startRecording()
            val chunk = ShortArray(WakeWord.CHUNK)
            var silent = 0
            var lastIgnored = 0L
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
                        Logs.w("Wake word", "The microphone only gives silence here (Android blocks it), so she can't listen")
                        running = false
                        main.post(onSilenced)
                        break
                    }
                } else silent = 0
                val r = model.listen(chunk)
                main.post { onScore(r.score) }
                if (r.notSpeech && System.currentTimeMillis() - lastIgnored > 60_000) {
                    lastIgnored = System.currentTimeMillis()
                    Logs.i("Wake word", "Something sounded like \u201C${phrase.label}\u201D (%.2f) but wasn't a voice; ignored".format(r.score))
                }
                if (r.heard) {
                    Logs.i("Wake word", "Heard \u201C${phrase.label}\u201D (%.2f, needs %.2f)".format(r.score, model.threshold))
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
