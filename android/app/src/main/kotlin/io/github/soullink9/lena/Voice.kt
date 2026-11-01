package io.github.soullink9.lena

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.media.MediaRecorder
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.sqrt

/** The phone's on-device speech model isn't installed yet (Android starts downloading it). */
class EarUnavailable : Exception("On-device speech recognition isn't ready")

/** Turns your voice into text. */
interface Ear {
    /** Listens until you stop talking and returns what you said, or "" if nothing was heard. */
    suspend fun listen(onPartial: (String) -> Unit, onLevel: (Float) -> Unit): String

    /** Stop listening now and use what was heard so far. */
    fun finish()

    /** Frees the microphone and any recognizer. Called when the sheet closes; an ear can't be used after it. */
    fun release()
}

/** The phone's recognizer reported an error. [code] is one of SpeechRecognizer's ERROR_ constants. */
class EarError(val code: Int) : IOException("Speech recognition error $code") {
    /** Errors that usually clear if you just try again. */
    val transient: Boolean
        get() = code in setOf(
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY, SpeechRecognizer.ERROR_CLIENT,
            SpeechRecognizer.ERROR_SERVER_DISCONNECTED, SpeechRecognizer.ERROR_TOO_MANY_REQUESTS,
            SpeechRecognizer.ERROR_SERVER,
        )

    /** The error in plain words, for the note under the mic and the logs. */
    fun describe(): String = when (code) {
        SpeechRecognizer.ERROR_AUDIO -> "the microphone is in use"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "no microphone permission"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "the phone's recognizer was busy"
        else -> "error $code"
    }
}

/**
 * Android's on-device speech recognizer: shows your words as you speak, and the audio never
 * leaves the phone. One recognizer is kept for the whole conversation; creating a new one
 * right after destroying the last often fails with "busy".
 */
class PhoneEar(private val context: Context) : Ear {

    companion object {
        /** Whether this phone has an on-device recognizer at all (some don't, and then the server is used). */
        fun available(c: Context) = SpeechRecognizer.isOnDeviceRecognitionAvailable(c)
    }

    private var recognizer: SpeechRecognizer? = null

    /** Free-form speech with partial results, so your words appear as you speak. */
    private fun request() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)

    /**
     * Listens once. If the phone's recognizer was busy or its service died (both happen after another app
     * used it), it's released and tried once more before giving up.
     */
    override suspend fun listen(onPartial: (String) -> Unit, onLevel: (Float) -> Unit): String =
        try {
            once(onPartial, onLevel)
        } catch (e: EarError) {
            if (!e.transient) throw e
            release() // start over with a fresh recognizer
            delay(400)
            once(onPartial, onLevel)
        }

    /**
     * One recognition, turned from the recognizer's callbacks into a suspending call that returns the text.
     * Recognizers must be driven from the main thread.
     */
    private suspend fun once(onPartial: (String) -> Unit, onLevel: (Float) -> Unit): String =
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                val r = recognizer ?: SpeechRecognizer.createOnDeviceSpeechRecognizer(context).also { recognizer = it }
                r.cancel() // clear anything left from the last turn
                var latest = ""
                /** Finishes with the text, once: the recognizer can call back more than once. */
                fun done(text: String) {
                    if (cont.isActive) cont.resume(text)
                }
                /** Fails with the error, once. */
                fun fail(e: Exception) {
                    if (cont.isActive) cont.resumeWithException(e)
                }
                r.setRecognitionListener(object : RecognitionListener {
                    /**
                     * The input level for the mic animation: the recognizer's dB value (about -2 to 10)
                     * mapped to 0..1.
                     */
                    override fun onRmsChanged(rmsdB: Float) = onLevel(((rmsdB + 2f) / 12f).coerceIn(0f, 1f))

                    // Callbacks the recognizer requires but Lena doesn't need: she only uses the level,
                    // the partial and final results, and errors.
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}

                    /**
                     * Keeps the newest partial result, both to show it and to fall back on if the final one
                     * comes back empty.
                     */
                    override fun onPartialResults(partialResults: Bundle?) {
                        partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                            ?.takeIf { it.isNotBlank() }?.let { latest = it; onPartial(it) }
                    }

                    /** The final text, or the last partial one if the recognizer returned nothing better. */
                    override fun onResults(results: Bundle?) {
                        val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                        done(text?.takeIf { it.isNotBlank() } ?: latest)
                    }

                    /**
                     * No match or a timeout means you said nothing, not a failure. Anything else, after
                     * you'd been heard, still uses what was heard.
                     */
                    override fun onError(error: Int) {
                        when {
                            latest.isNotBlank() -> done(latest) // we heard you; use it
                            error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> done("")
                            error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ||
                                error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> {
                                runCatching { r.triggerModelDownload(request()) }
                                fail(EarUnavailable())
                            }
                            else -> fail(EarError(error))
                        }
                    }
                })
                r.startListening(request())
                cont.invokeOnCancellation { runCatching { r.cancel() } }
            }
        }

    /** Ends listening now; the recognizer then delivers what it has. */
    override fun finish() {
        recognizer?.stopListening()
    }

    /** Destroys the recognizer, which holds the microphone and a connection to the speech service. */
    override fun release() {
        recognizer?.destroy()
        recognizer = null
    }
}

/**
 * Records until you stop talking, then sends the audio to Open WebUI's speech-to-text
 * (the same engine call mode uses).
 */
class ServerEar(private val api: Api) : Ear {

    @Volatile
    private var finishNow = false

    /**
     * Records until you stop, then sends it to Open WebUI's speech-to-text. The text appears all at once,
     * after you stop, unlike on the phone.
     */
    override suspend fun listen(onPartial: (String) -> Unit, onLevel: (Float) -> Unit): String {
        finishNow = false
        val pcm = withContext(Dispatchers.IO) { record(onLevel) }
        if (pcm.isEmpty()) return ""
        onLevel(0f)
        return api.transcribe(wav(pcm, RATE)).trim()
    }

    /**
     * Records 16 kHz mono until 1.2 seconds of quiet after you've started talking (or 30 seconds, or
     * finish()), so you don't have to tap to stop. "Talking" is measured against the room: the first
     * 200 ms learn its background noise. Returns the raw audio, or nothing if you didn't speak within
     * 7 seconds.
     */
    private suspend fun record(onLevel: (Float) -> Unit): ByteArray {
        val frame = RATE / 50 // 20 ms
        val min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION, RATE,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, frame * 2 * 10),
        )
        val out = ByteArrayOutputStream()
        val buf = ShortArray(frame)
        var noise = 0.0
        var frames = 0
        var speaking = false
        var silentMs = 0
        var totalMs = 0
        rec.startRecording()
        try {
            while (currentCoroutineContext().isActive && !finishNow) {
                val n = rec.read(buf, 0, frame)
                if (n <= 0) break
                var sum = 0.0
                for (i in 0 until n) {
                    val s = buf[i].toInt()
                    sum += (s * s).toDouble()
                    out.write(s and 0xff)
                    out.write((s shr 8) and 0xff)
                }
                val rms = sqrt(sum / n)
                totalMs += 20
                frames++
                if (frames <= 10) { // first 200 ms: learn the room's background noise
                    noise += (rms - noise) / frames
                    continue
                }
                val threshold = maxOf(noise * 2.5, 300.0)
                onLevel((rms / (threshold * 4)).toFloat().coerceIn(0f, 1f))
                if (rms > threshold) {
                    speaking = true
                    silentMs = 0
                } else if (speaking) {
                    silentMs += 20
                }
                if (speaking && silentMs >= 1200) break // you stopped talking
                if (!speaking && totalMs >= 7000) return ByteArray(0) // nothing said
                if (totalMs >= 30_000) break
            }
        } finally {
            rec.stop()
            rec.release()
        }
        return if (speaking || finishNow) out.toByteArray() else ByteArray(0)
    }

    /** Stops recording at the next 20 ms frame. */
    override fun finish() {
        finishNow = true
    }

    /** Nothing to free: the recorder is closed after each recording. */
    override fun release() {}

    companion object {
        private const val RATE = 16_000

        /** Raw 16-bit mono PCM with a WAV header, which every speech-to-text engine accepts. */
        fun wav(pcm: ByteArray, rate: Int): ByteArray {
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
                put("RIFF".toByteArray()); putInt(36 + pcm.size); put("WAVE".toByteArray())
                put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1)
                putInt(rate); putInt(rate * 2); putShort(2); putShort(16)
                put("data".toByteArray()); putInt(pcm.size)
            }
            return header.array() + pcm
        }
    }
}

/** Reads replies aloud. */
/**
 * How her voice sounds right now: a touch slower and lower when she's groggy or wistful, quicker
 * and brighter when she's wired or cheerful. Small changes on purpose; she should sound like
 * herself in a mood, not like a different voice.
 */
data class VoiceTone(val speed: Float = 1f, val pitch: Float = 1f) {
    val neutral get() = speed == 1f && pitch == 1f

    companion object {
        private val energy = mapOf(
            "Groggy" to (0.93f to 0.97f), "Wired" to (1.08f to 1.03f), "Drained" to (0.88f to 0.95f),
        )
        private val mood = mapOf(
            "Cheerful" to (1.03f to 1.03f), "Playful" to (1.04f to 1.03f), "Smug" to (0.98f to 1f),
            "Curious" to (1.02f to 1.02f), "Focused" to (1.03f to 1f), "Restless" to (1.05f to 1f),
            "Wistful" to (0.94f to 0.97f), "Grumpy" to (0.98f to 0.96f), "Uneasy" to (1.04f to 1.01f),
            "Fond" to (0.97f to 1.01f),
        )

        /**
         * Her tone for how she feels: the energy and mood adjustments multiplied together. Unknown feelings
         * change nothing.
         */
        fun of(f: Feelings?): VoiceTone {
            if (f == null) return VoiceTone()
            val (es, ep) = energy[f.energy] ?: (1f to 1f)
            val (ms, mp) = mood[f.mood] ?: (1f to 1f)
            return VoiceTone((es * ms).coerceIn(0.85f, 1.15f), (ep * mp).coerceIn(0.92f, 1.08f))
        }
    }
}

/** Reads Lena's replies aloud. */
interface Mouth {
    /** Set before [say]; neutral unless "Her voice follows her mood" is on. */
    var tone: VoiceTone
    /** Speaks the text and returns when she's finished (or was stopped). */
    suspend fun say(text: String)
    /** Stops speaking now. */
    fun stop()
    /** Frees the player or speech engine. */
    fun release()
}

private val speechAttributes: AudioAttributes = AudioAttributes.Builder()
    .setUsage(AudioAttributes.USAGE_ASSISTANT)
    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
    .build()

/** Splits a reply into short pieces so speaking can start before the whole reply is turned into audio. */
private fun pieces(text: String): List<String> {
    val sentences = text.split(Regex("(?<=[.!?])\\s+|\\n+")).map { it.trim() }.filter { it.isNotEmpty() }
    val out = mutableListOf<String>()
    val sb = StringBuilder()
    for (s in sentences) {
        val limit = if (out.isEmpty()) 160 else 400 // a short first piece starts speaking sooner
        if (sb.isNotEmpty() && sb.length + s.length > limit) {
            out += sb.toString()
            sb.clear()
        }
        if (sb.isNotEmpty()) sb.append(' ')
        sb.append(s)
    }
    if (sb.isNotEmpty()) out += sb.toString()
    return out
}

/** Your Open WebUI's text-to-speech: the same voice as call mode. */
class ServerMouth(private val context: Context, private val api: Api) : Mouth {

    private var player: MediaPlayer? = null
    override var tone = VoiceTone()

    /**
     * Speaks a reply piece by piece: while one piece plays, the next is already being turned into audio, so
     * long replies start quickly and don't pause between sentences.
     */
    override suspend fun say(text: String) = coroutineScope {
        val parts = pieces(text)
        if (parts.isEmpty()) return@coroutineScope
        var next = async { api.speech(parts[0]) }
        for (i in parts.indices) {
            val audio = next.await()
            if (i + 1 < parts.size) next = async { api.speech(parts[i + 1]) }
            play(audio)
        }
    }

    /**
     * Plays one piece of audio and waits for it to end. The audio goes through a temporary file because
     * MediaPlayer can't play from memory reliably on every phone.
     */
    private suspend fun play(audio: ByteArray) {
        val file = File.createTempFile("lena-", ".audio", context.cacheDir)
        try {
            file.writeBytes(audio)
            withContext(Dispatchers.Main) {
                suspendCancellableCoroutine { cont ->
                    val p = MediaPlayer()
                    player = p
                    p.setAudioAttributes(speechAttributes)
                    p.setDataSource(file.path)
                    p.setOnCompletionListener { if (cont.isActive) cont.resume(Unit) }
                    p.setOnErrorListener { _, _, _ -> if (cont.isActive) cont.resume(Unit); true }
                    p.prepare()
                    p.start()
                    if (!tone.neutral) runCatching { p.playbackParams = PlaybackParams().setSpeed(tone.speed).setPitch(tone.pitch) }
                    cont.invokeOnCancellation { runCatching { p.stop() } }
                }
            }
        } finally {
            player?.release()
            player = null
            file.delete()
        }
    }

    /** Stops the current piece; say() then ends. */
    override fun stop() {
        runCatching { player?.stop() }
    }

    /** Frees the media player. */
    override fun release() {
        player?.release()
        player = null
    }
}

/** The phone's own text-to-speech voice. */
class PhoneMouth(context: Context) : Mouth {

    private val ready = CompletableDeferred<Boolean>()
    override var tone = VoiceTone()
    private val tts = TextToSpeech(context.applicationContext) { status -> ready.complete(status == TextToSpeech.SUCCESS) }

    init {
        tts.setAudioAttributes(speechAttributes)
    }

    /** Speaks with the phone's engine at her tone's speed and pitch, waiting for the utterance to finish. */
    override suspend fun say(text: String) {
        if (!ready.await()) return
        tts.setSpeechRate(tone.speed)
        tts.setPitch(tone.pitch)
        for (part in pieces(text)) {
            suspendCancellableCoroutine { cont ->
                val id = UUID.randomUUID().toString()
                tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    /** Nothing to do when an utterance starts. */
                    override fun onStart(utteranceId: String?) {}

                    /** Finished: say() moves on to the next piece. */
                    override fun onDone(utteranceId: String?) {
                        if (cont.isActive) cont.resume(Unit)
                    }

                    /** Older engines report failures here; a failed piece is skipped rather than stopping her. */
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        if (cont.isActive) cont.resume(Unit)
                    }

                    /** The same, with the engine's error code. */
                    override fun onError(utteranceId: String?, errorCode: Int) {
                        if (cont.isActive) cont.resume(Unit)
                    }
                })
                tts.speak(part, TextToSpeech.QUEUE_FLUSH, null, id)
                cont.invokeOnCancellation { tts.stop() }
            }
        }
    }

    /** Stops speaking now. */
    override fun stop() {
        tts.stop()
    }

    /** Shuts the phone's speech engine down. */
    override fun release() {
        tts.shutdown()
    }
}

/** Ducks music and other audio while Lena talks. */
suspend fun <T> withSpeechFocus(context: Context, block: suspend () -> T): T {
    val audio = context.getSystemService(AudioManager::class.java)
    val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(speechAttributes)
        .build()
    audio.requestAudioFocus(request)
    try {
        return block()
    } finally {
        audio.abandonAudioFocusRequest(request)
    }
}
