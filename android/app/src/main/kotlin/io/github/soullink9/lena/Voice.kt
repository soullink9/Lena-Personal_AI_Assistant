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
import kotlinx.coroutines.launch
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
        SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> "the phone's recognizer was still busy with the last time (error 10)"
        else -> "error $code"
    }
}

/**
 * Android's on-device speech recognizer: shows your words as you speak, and the audio never
 * leaves the phone. On Android TV, and before Android 12, it's the device's own speech service instead
 * ([onDevice]): on a TV, that's what hears through the remote's microphone.
 *
 * One recognizer per sheet, destroyed when the sheet closes: a recognizer kept longer can outlive the
 * speech service it's connected to (Android restarts it while the tablet sits idle) and then never
 * answers. Within a sheet, the speech service runs one session at a time and refuses a new one while the
 * last is still closing ("error 10", ERROR_TOO_MANY_REQUESTS), so a new session waits a moment after the
 * last is cancelled, and a busy recognizer gets a pause and another try. A session that never gets ready
 * is given up after a few seconds, so listening can't hang; the sheet then uses your server.
 */
class PhoneEar(private val context: Context) : Ear {

    companion object {
        /** After the recognizer never gets ready, how long it's passed over for another way of hearing. */
        private const val UNRELIABLE_MS = 24 * 60 * 60_000L

        /**
         * Whether this phone has a recognizer that works: one that never got ready lately (some tablets have
         * one that never answers) counts as not having one, for a day.
         */
        fun available(c: Context): Boolean {
            val exists = if (onDevice(c)) SpeechRecognizer.isOnDeviceRecognitionAvailable(c) else SpeechRecognizer.isRecognitionAvailable(c)
            return exists &&
                System.currentTimeMillis() - c.getSharedPreferences("lena_voice", Context.MODE_PRIVATE).getLong("phone_unreliable", 0) > UNRELIABLE_MS
        }

        /**
         * Whether to use the on-device recognizer (Android 12 and later), which never sends your voice
         * anywhere. On a TV it's the TV's own speech recognition instead: that's the one that can hear
         * through the remote's microphone, as the TV's voice search does. Before Android 12 there's only
         * the device's speech service (Google's, on most).
         */
        @androidx.annotation.ChecksSdkIntAtLeast(api = android.os.Build.VERSION_CODES.S)
        fun onDevice(c: Context): Boolean = Compat.onDeviceRecognizer && !Tv.isTv(c)

        /** Gives the phone's recognizer another chance (choosing On this phone again in Settings). */
        fun trustAgain(c: Context) {
            c.getSharedPreferences("lena_voice", Context.MODE_PRIVATE).edit().remove("phone_unreliable").apply()
        }

        /** The recognizer never got ready: use another way of hearing for a day (see [available]). */
        fun markUnreliable(c: Context) {
            Logs.w("Voice", "The phone's recognizer never got ready; using another way of hearing for a day")
            c.getSharedPreferences("lena_voice", Context.MODE_PRIVATE).edit().putLong("phone_unreliable", System.currentTimeMillis()).apply()
        }

        /** How long the speech service needs to close a cancelled session before the next can start. */
        private const val SETTLE_MS = 300L

        /** How long a session may take to get ready before it's given up. */
        private const val READY_TIMEOUT_MS = 3_000L

        /** Through the wake word's microphone: your words unchanged this long means you've finished. */
        private const val END_OF_SPEECH_MS = 1_400L

        /** Through the wake word's microphone: nothing said in this long means nothing's coming. */
        private const val NOTHING_SAID_MS = 7_000L

        /** Through the wake word's microphone: the longest a question can be. */
        private const val LONGEST_MS = 30_000L

        /** After ending the sound, how long to wait for the recognizer's final result. */
        private const val RESULT_WAIT_MS = 2_500L

        /** Waits before each retry when the service was busy: it's usually free again within a second. */
        private val BUSY_RETRY_MS = listOf(500L, 1_200L)

        /** Errors meaning the service is still busy with an earlier session; waiting is enough. */
        private val BUSY = setOf(SpeechRecognizer.ERROR_TOO_MANY_REQUESTS, SpeechRecognizer.ERROR_RECOGNIZER_BUSY)
    }

    private var recognizer: SpeechRecognizer? = null

    /** Whether a session is open: started, and no result or error yet (main thread only). */
    private var sessionOpen = false

    /** Counts sessions, so a late cancel only ever touches its own session (main thread only). */
    private var session = 0

    /** Whether the last session listened through the wake word's microphone ([MicRelay]). */
    private var lastRelayed = false

    /**
     * Free-form speech with partial results, so your words appear as you speak. With [relay], the sound
     * comes from the wake word's microphone, which never closed, instead of the recognizer opening its own.
     */
    private fun request(relay: MicRelay.Relay? = null) = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        .apply {
            if (relay != null) {
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, relay.pipe)
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, android.media.AudioFormat.ENCODING_PCM_16BIT)
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, MicRelay.RATE)
            }
        }

    /** The on-device recognizer, or the device's speech service (see [onDevice]). */
    private fun newRecognizer(): SpeechRecognizer =
        if (onDevice(context)) SpeechRecognizer.createOnDeviceSpeechRecognizer(context) else SpeechRecognizer.createSpeechRecognizer(context)

    /**
     * Listens once. If the service was still busy (error 10, or "busy"), waits and tries again, up to
     * twice. If the recognizer broke or never got ready, starts over with a new one once.
     */
    override suspend fun listen(onPartial: (String) -> Unit, onLevel: (Float) -> Unit): String {
        var reconnected = false
        var busyTries = 0
        var relayFailed = false
        while (true) {
            try {
                // Only Android 13 and later can listen through the wake word's microphone.
                val relay = if (relayFailed || !Compat.recognizerTakesAudio) null.also { MicRelay.drop() } else MicRelay.take()
                return once(onPartial, onLevel, relay = relay)
            } catch (e: EarError) {
                when {
                    // The recognizer couldn't use the wake word's microphone: let it open its own.
                    lastRelayed && !relayFailed -> {
                        Logs.i("Voice", "The recognizer couldn't take the wake word's microphone (${e.describe()}); it opens its own")
                        relayFailed = true
                        delay(SETTLE_MS)
                    }
                    e.code in BUSY && busyTries < BUSY_RETRY_MS.size -> {
                        Logs.i("Voice", "The phone's recognizer was busy (${e.describe()}); trying again")
                        delay(BUSY_RETRY_MS[busyTries++])
                    }
                    e.transient && !reconnected -> {
                        Logs.i("Voice", "The phone's recognizer stopped answering (${e.describe()}); starting a new one")
                        withContext(Dispatchers.Main) { release() }
                        reconnected = true
                        delay(SETTLE_MS)
                    }
                    else -> throw e
                }
            }
        }
    }

    /**
     * One recognition, turned from the recognizer's callbacks into a suspending call that returns the text.
     * Recognizers must be driven from the main thread.
     */
    private suspend fun once(onPartial: (String) -> Unit, onLevel: (Float) -> Unit, relay: MicRelay.Relay? = null): String =
        withContext(Dispatchers.Main) {
            lastRelayed = relay != null
            val r = recognizer ?: newRecognizer().also { recognizer = it }
            if (sessionOpen) {
                // Close the last session and let the service catch up, or this one is refused.
                r.cancel()
                sessionOpen = false
                delay(SETTLE_MS)
            }
            val mine = ++session
            val main = android.os.Handler(android.os.Looper.getMainLooper())
            suspendCancellableCoroutine { cont ->
                var latest = ""
                var ready = false
                lateinit var watchdog: Runnable
                lateinit var endpointer: Runnable
                /** Ends this session's bookkeeping, if it's still the current one, and lets go of the relay. */
                fun close() {
                    main.removeCallbacks(watchdog)
                    main.removeCallbacks(endpointer)
                    relay?.close()
                    if (session == mine) sessionOpen = false
                }
                /** Finishes with the text, once: the recognizer can call back more than once. */
                fun done(text: String) {
                    close()
                    if (cont.isActive) cont.resume(text)
                }
                /** Fails with the error, once. */
                fun fail(e: Exception) {
                    close()
                    if (cont.isActive) cont.resumeWithException(e)
                }
                // Listening through the wake word's microphone, the recognizer never decides by itself that
                // you've finished (the sound keeps coming), so Lena does: once your words stop changing for a
                // moment, or nothing is said at all, the sound ends and the recognizer gives its result.
                val started = android.os.SystemClock.elapsedRealtime()
                var changed = started
                var stopping = false
                endpointer = Runnable {
                    val now = android.os.SystemClock.elapsedRealtime()
                    val quiet = now - changed
                    val finished = (latest.isNotBlank() && quiet > END_OF_SPEECH_MS) ||
                        (latest.isBlank() && now - started > NOTHING_SAID_MS) || now - started > LONGEST_MS
                    if (!stopping && finished && session == mine) {
                        stopping = true
                        relay?.close() // the end of the sound
                        runCatching { r.stopListening() }
                        // If the result still doesn't come, use what was heard.
                        main.postDelayed({ if (session == mine) { runCatching { r.cancel() }; done(latest) } }, RESULT_WAIT_MS)
                    } else if (!stopping) {
                        main.postDelayed(endpointer, 200)
                    }
                }
                // Never got ready: the service isn't answering. Give up on it rather than wait forever.
                watchdog = Runnable {
                    if (!ready && session == mine) {
                        runCatching { r.cancel() }
                        fail(EarError(SpeechRecognizer.ERROR_SERVER_DISCONNECTED))
                    }
                }
                r.setRecognitionListener(object : RecognitionListener {
                    /**
                     * The input level for the mic animation: the recognizer's dB value (about -2 to 10)
                     * mapped to 0..1.
                     */
                    override fun onRmsChanged(rmsdB: Float) = onLevel(((rmsdB + 2f) / 12f).coerceIn(0f, 1f))

                    /** The microphone is open: from here on it's only a matter of you speaking. */
                    override fun onReadyForSpeech(params: Bundle?) {
                        ready = true
                        main.removeCallbacks(watchdog)
                        if (relay != null) main.postDelayed(endpointer, 200)
                    }

                    // Callbacks the recognizer requires but Lena doesn't need: she only uses readiness,
                    // the level, the partial and final results, and errors.
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
                            ?.takeIf { it.isNotBlank() }?.let {
                                if (it != latest) changed = android.os.SystemClock.elapsedRealtime()
                                latest = it
                                onPartial(it)
                            }
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
                                // Android 13 can start the on-device model's download itself.
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                    runCatching { r.triggerModelDownload(request()) }
                                }
                                fail(EarUnavailable())
                            }
                            else -> fail(EarError(error))
                        }
                    }
                })
                sessionOpen = true
                r.startListening(request(relay))
                main.postDelayed(watchdog, READY_TIMEOUT_MS)
                cont.invokeOnCancellation {
                    // Can come from any thread; the recognizer only takes calls on the main one. Only this
                    // session is cancelled, never a newer one that started meanwhile.
                    main.post {
                        main.removeCallbacks(watchdog)
                        relay?.close()
                        if (session == mine && sessionOpen) {
                            runCatching { r.cancel() }
                            sessionOpen = false
                        }
                    }
                }
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
        sessionOpen = false
    }
}

/**
 * An ear that records until you stop talking and then has the recording turned into text somewhere else:
 * your Open WebUI ([ServerEar]) or Home Assistant ([HaEar]). The text appears all at once, after you stop,
 * unlike on the phone.
 */
abstract class RecordingEar : Ear {

    @Volatile
    private var finishNow = false

    /** Turns 16 kHz, 16-bit mono PCM into text. */
    protected abstract suspend fun transcribe(pcm: ByteArray): String

    override suspend fun listen(onPartial: (String) -> Unit, onLevel: (Float) -> Unit): String {
        finishNow = false
        val pcm = withContext(Dispatchers.IO) { record(onLevel) }
        if (pcm.isEmpty()) return ""
        onLevel(0f)
        return transcribe(pcm).trim()
    }

    /**
     * Records 16 kHz mono until 1.2 seconds of quiet after you've started talking (or 30 seconds, or
     * finish()), so you don't have to tap to stop. "Talking" is measured against the room: the first
     * 200 ms learn its background noise. Returns the raw audio, or nothing if you didn't speak within
     * 7 seconds.
     */
    private suspend fun record(onLevel: (Float) -> Unit): ByteArray {
        val frame = RATE / 50 // 20 ms
        // Opened by her name: the wake word's microphone, which never closed ([MicRelay]); otherwise its own.
        val relayed = MicRelay.take()?.let { MicRelay.Reader(it) }
        val rec = if (relayed != null) null else {
            val min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION, RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, frame * 2 * 10),
            )
        }
        val out = ByteArrayOutputStream()
        val buf = ShortArray(frame)
        var noise = 0.0
        var frames = 0
        var speaking = false
        var silentMs = 0
        var totalMs = 0
        rec?.startRecording()
        try {
            while (currentCoroutineContext().isActive && !finishNow) {
                val n = rec?.read(buf, 0, frame) ?: relayed!!.read(buf, 0, frame)
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
            rec?.stop()
            rec?.release()
            relayed?.close()
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
        const val RATE = 16_000

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

/** Open WebUI's speech-to-text (the same engine call mode uses). */
class ServerEar(private val api: Api) : RecordingEar() {
    override suspend fun transcribe(pcm: ByteArray): String = api.transcribe(wav(pcm, RATE))
}

/**
 * Home Assistant's speech-to-text (Settings → Talking to Lena → Hearing you → Home Assistant), the engine
 * its voice assistants use: Whisper, Speech-to-Phrase, Home Assistant Cloud. Like a Home Assistant Voice
 * satellite, the display only records; Home Assistant does the hearing.
 */
class HaEar(private val ha: HomeAssistant, private val engine: String) : RecordingEar() {
    /** The chosen engine, or Home Assistant's first when none is chosen. */
    override suspend fun transcribe(pcm: ByteArray): String {
        val use = engine.ifBlank { ha.sttEngines().firstOrNull()?.entityId }
            ?: throw IllegalStateException("Home Assistant has no speech-to-text set up (Settings → Voice assistants).")
        return ha.transcribe(use, pcm)
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

/**
 * Ducks music and other audio while Lena talks, at the volume that suits the moment ([Loudness]). On a
 * Home Assistant speaker ([HaSpeakerMouth]) neither applies: the tablet stays as it is.
 */
suspend fun <T> withSpeechFocus(context: Context, block: suspend () -> T): T {
    if (Room.speaksThroughSpeaker(context)) return block()
    val audio = context.getSystemService(AudioManager::class.java)
    val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(speechAttributes)
        .build()
    audio.requestAudioFocus(request)
    try {
        return Loudness.whileSpeaking(context) { block() }
    } finally {
        audio.abandonAudioFocusRequest(request)
    }
}

/**
 * Lena's voice on a speaker in Home Assistant (Settings → Connections → Home Assistant → Speak through):
 * everything she says is sent to Home Assistant's text-to-speech (tts.speak, with the voice chosen there)
 * on that media player. It returns once the speaker has finished, so listening afterwards still works.
 */
class HaSpeakerMouth(private val ha: HomeAssistant, private val speaker: String, private val voice: String) : Mouth {
    override var tone: VoiceTone = VoiceTone()

    override suspend fun say(text: String) {
        if (text.isBlank()) return
        ha.speak(voice, speaker, text)
        // Wait for it to start (the voice is made first), then to finish.
        var started = false
        val begin = System.currentTimeMillis()
        while (System.currentTimeMillis() - begin < 10 * 60_000L) {
            delay(500)
            val state = runCatching { ha.state(speaker) }.getOrNull() ?: break
            if (state == "playing" || state == "buffering") started = true
            else if (started || System.currentTimeMillis() - begin > 12_000) break
        }
    }

    override fun stop() {
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch { runCatching { ha.stopPlaying(speaker) } }
    }

    override fun release() {}
}
