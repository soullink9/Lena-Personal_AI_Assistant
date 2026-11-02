@file:OptIn(ExperimentalLayoutApi::class)

package io.github.soullink9.lena

import android.Manifest
import android.app.KeyguardManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.RoundedCorner
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.AddComment
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.updateAll
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Ask Lena: talk (or type) to Lena over whatever app you're in, like Google Assistant or Home
 * Assistant's Assist. [Sheet] holds the conversation; one of six looks (Settings → Appearance)
 * draws it. Answers are real Open WebUI chats, written on your server with your
 * model's tools; follow-ups continue the same chat, and "Continue in Lena" opens it.
 */
class AskActivity : ComponentActivity() {

    companion object {
        const val ACTION_ASK = "io.github.soullink9.lena.ASK"   // keyboard
        const val ACTION_TALK = "io.github.soullink9.lena.TALK" // listening
        private const val EXTRA_PROMPT = "prompt"
        private const val EXTRA_KEY = "key"

        private const val EXTRA_FROM = "from"

        /**
         * Opens the sheet; with [prompt], sends it straight away (only Lena's own widget and shortcuts
         * use that). [from]: the wall screen that called her, which she goes back to when the
         * conversation's over.
         */
        fun intent(c: Context, prompt: String? = null, talk: Boolean = false, from: From? = null): Intent =
            Intent(c, AskActivity::class.java)
                .setAction(if (talk) ACTION_TALK else ACTION_ASK)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .apply {
                    if (prompt != null) putExtra(EXTRA_PROMPT, prompt).putExtra(EXTRA_KEY, Prefs.launchKey(c))
                    if (from != null) putExtra(EXTRA_FROM, from.name)
                }

        /**
         * Things people say to end a conversation. Said on their own, she doesn't answer them; the
         * sheet just closes (and goes back to the wall display or screen saver).
         */
        private val GOODBYES = setOf(
            "stop", "cancel", "never mind", "nevermind", "nothing", "that's all", "that is all", "that's it",
            "thats all", "thats it", "thank you", "thanks", "thanks a lot", "thank you very much", "no thanks", "no thank you",
            "goodbye", "good bye", "bye", "bye bye", "all done", "done", "i'm done", "im done", "i am done", "we're done",
            "go away", "dismiss", "close", "that'll be all", "thatll be all", "okay thanks", "ok thanks", "okay thank you",
            "ok thank you", "got it thanks", "perfect thanks", "great thanks", "good night", "goodnight",
        )

        /**
         * True when everything you said is just a goodbye ("thanks", "that's all", "stop"), ignoring her
         * name and punctuation. The whole phrase must match, so "thanks, and turn off the lights" is still
         * a request.
         */
        fun isGoodbye(said: String): Boolean {
            val words = said.lowercase().replace('’', '\'').replace(Regex("[^a-z' ]"), " ")
                .split(' ').filter { it.isNotBlank() && it != "lena" && it != "jarvis" && it != "hey" }
            return words.isNotEmpty() && words.joinToString(" ") in GOODBYES
        }
    }

    /** The wall screen that called her: she goes back to it when the conversation's over. */
    enum class From { DASHBOARD, DISPLAY, SCREENSAVER }

    private var from: From? = null

    /** A touch on the sheet: stops it closing by itself, since you're using it. */
    private var onTouch: (() -> Unit)? = null

    /** Sees every touch before the views do, so any touch counts as using the sheet. */
    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        if (ev.actionMasked == android.view.MotionEvent.ACTION_DOWN) onTouch?.invoke()
        return super.dispatchTouchEvent(ev)
    }

    /** Closes the sheet, back to wherever she was called from. */
    private fun goBack() {
        if (from == From.SCREENSAVER) startActivity(DisplayActivity.intent(this, screensaver = true))
        finish()
    }

    /** Which wall screen an intent says called her, if any. */
    private fun fromOf(i: Intent): From? = i.getStringExtra(EXTRA_FROM)?.let { runCatching { From.valueOf(it) }.getOrNull() }

    /** What the sheet is doing; it decides the mic button, the animation and whether it may close by itself. */
    private enum class Phase { IDLE, LISTENING, THINKING, SPEAKING }

    /** One question and its answer as shown on the sheet. The answer fills in as it's written. */
    private class Turn(val question: String, val image: ImageBitmap? = null) {
        var answer by mutableStateOf("")
        var done by mutableStateOf(false)
    }

    private lateinit var api: Api
    private var ear: Ear? = null
    private var fallbackEar: Ear? = null // your server, when the phone couldn't listen this time
    private var mouth: Mouth? = null
    private var running: Api.Started? = null
    private var working = false
    private val restart = mutableIntStateOf(0) // bumped when the gesture is used again while the sheet is open
    private var startTalking = false
    private var style = AskStyle.ORB

    /** Opened over the lock screen and still locked: private mode (see [unlockThen]). */
    private val locked = mutableStateOf(false)
    private val keyguard by lazy { getSystemService(KeyguardManager::class.java) }

    /**
     * Without a server, Settings first. Otherwise works out whether it's on the lock screen (and whether
     * that's allowed), where it was called from, and whether to start listening, typing or sending a
     * prompt.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val server = Prefs.server(this)
        if (server == null) {
            startActivity(Intent(this, SettingsActivity::class.java))
            finish()
            return
        }
        api = Api(server, applicationContext)

        // The assistant gesture can open a new chat instead of the sheet.
        if (intent.action == Intent.ACTION_ASSIST && Prefs.assistAction(this) == AssistAction.CHAT) {
            return forward(MainActivity.ACTION_NEW_CHAT)
        }
        startTalking = wantsTalk(intent)
        from = fromOf(intent)

        // Over the lock screen only if you turned it on; otherwise Android asks you to unlock first.
        if (Prefs.lockScreen(this) && intent.action != Intent.ACTION_SEND) setShowWhenLocked(true)
        locked.value = Prefs.lockScreen(this) && keyguard.isKeyguardLocked

        // Shared text only fills the box. A prompt is sent automatically only with Lena's own key.
        val shared = if (intent.action == Intent.ACTION_SEND) {
            listOfNotNull(intent.getStringExtra(Intent.EXTRA_SUBJECT), intent.getStringExtra(Intent.EXTRA_TEXT))
                .distinct().joinToString("\n").trim()
        } else ""
        val auto = intent.getStringExtra(EXTRA_PROMPT)
            ?.takeIf { intent.getStringExtra(EXTRA_KEY) == Prefs.launchKey(this) }

        style = Prefs.askStyle(this)
        // The orb blurs whatever's behind it, where the phone supports it (otherwise it just darkens).
        if (style == AskStyle.ORB) {
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            window.attributes = window.attributes.also { it.blurBehindRadius = 64 }
        }
        enableEdgeToEdge()
        setContent { LenaTheme { Sheet(shared, auto) } }
    }

    /**
     * Whether this intent should start listening straight away: Talk to Lena, or the assistant gesture set
     * to talk.
     */
    private fun wantsTalk(i: Intent) = i.action == ACTION_TALK ||
        (i.action == Intent.ACTION_ASSIST && Prefs.assistAction(this) == AssistAction.ASK)

    /**
     * Called again while already open (the gesture twice, the wake word): listen again rather than open a
     * second sheet.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        fromOf(intent)?.let { from = it }
        if (wantsTalk(intent)) restart.intValue++
    }

    /** Unlocked while the sheet was open: the private lock-screen mode ends. */
    override fun onResume() {
        super.onResume()
        if (locked.value) locked.value = keyguard.isKeyguardLocked
    }

    /** Runs [action] now, or once you've unlocked the phone if the sheet is on the lock screen. */
    private fun unlockThen(action: () -> Unit) {
        if (!keyguard.isKeyguardLocked) return action()
        keyguard.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
            /** Unlocked: leave the lock-screen mode and carry on with what was asked. */
            override fun onDismissSucceeded() {
                locked.value = false
                action()
            }
        })
    }

    /** Hands over to the full app (a new chat), after unlocking if needed, and closes the sheet. */
    private fun forward(action: String) {
        if (locked.value) return unlockThen { forward(action) }
        startActivity(Intent(this, MainActivity::class.java).setAction(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    /** Opens a chat in the full app, after unlocking if needed, and closes the sheet. */
    private fun openChat(chatId: String) {
        if (locked.value) return unlockThen { openChat(chatId) }
        startActivity(
            Intent(this, MainActivity::class.java).setAction(MainActivity.ACTION_OPEN_CHAT)
                .putExtra(MainActivity.EXTRA_CHAT_ID, chatId).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        working = false // you'll see it finish there
        finish()
    }

    /** The phone's recognizer when chosen and available, otherwise your server's speech-to-text. */
    private fun newEar(): Ear =
        if (Prefs.hearing(this) == Hearing.PHONE && PhoneEar.available(this)) PhoneEar(this) else ServerEar(api)

    /** The voice chosen in Settings, or none when replies aren't read aloud. */
    private fun newMouth(): Mouth? = when (Prefs.speaking(this)) {
        Speaking.SERVER -> ServerMouth(this, api)
        Speaking.PHONE -> PhoneMouth(this)
        Speaking.OFF -> null
    }

    /** Leaving mid-answer: Lena keeps watching and notifies you when it's done. */
    override fun onStop() {
        super.onStop()
        val started = running
        if (working && started != null && Prefs.notifyReplies(this) &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) {
            ReplyWorker.enqueue(applicationContext, started.chatId)
        }
        if (!isChangingConfigurations) finish()
    }

    /** Frees the microphone and the voice. */
    override fun onDestroy() {
        ear?.release()
        mouth?.release()
        super.onDestroy()
    }

    /**
     * The conversation: everything the sheet does (listening, sending, speaking, follow-ups, closing by
     * itself on a wall screen), drawn by whichever look is chosen.
     */
    @Composable
    private fun Sheet(initial: String, autoPrompt: String?) {
        val scope = rememberCoroutineScope()
        val view = LocalView.current
        var typing by remember { mutableStateOf(!startTalking) }
        var text by remember { mutableStateOf(initial) }
        val turns = remember { mutableStateListOf<Turn>() }
        var phase by remember { mutableStateOf(Phase.IDLE) }
        // Keep the screen on while she listens, thinks or speaks, so a long question isn't cut off.
        SideEffect { view.keepScreenOn = phase != Phase.IDLE }
        var heard by remember { mutableStateOf("") }
        var level by remember { mutableStateOf(0f) }
        var note by remember { mutableStateOf<String?>(null) }
        var model by remember { mutableStateOf<Model?>(null) }
        var feelings by remember { mutableStateOf<Feelings?>(null) }
        val house = remember { HomeAssistant.from(this) }
        var chatId by remember { mutableStateOf<String?>(null) }
        var lastReplyId by remember { mutableStateOf<String?>(null) }
        var job by remember { mutableStateOf<Job?>(null) }
        val prompts = remember { Prefs.quickPrompts(this) }
        // Locked: a fresh, private conversation; the ongoing one (and its history) stays hidden.
        val startedLocked = remember { locked.value }
        // Unlocked, talking to Lena always continues the one "Assistant Lena" chat.
        val ongoing = !startedLocked
        var photo by remember { mutableStateOf<Photo?>(null) }
        val ready = remember { CompletableDeferred<Unit>() } // the ongoing chat has been looked up
        val focus = remember { FocusRequester() }
        val scroll = rememberScrollState()

        lateinit var listen: (followUp: Boolean) -> Unit

        // Called from a wall screen: when the conversation's over, she goes back to it by herself.
        val handsFree = from != null
        var closer by remember { mutableStateOf<Job?>(null) }
        /**
         * On a wall screen only: closes the sheet and goes back after [ms], unless something happens first
         * (a touch, or her listening or speaking again).
         */
        fun closeAfter(ms: Long) {
            if (!handsFree) return
            closer?.cancel()
            closer = scope.launch {
                delay(ms)
                Logs.i("Ask", "Conversation over; back to the ${from?.name?.lowercase()}")
                goBack()
            }
        }
        LaunchedEffect(phase) { if (phase != Phase.IDLE) closer?.cancel() }
        DisposableEffect(Unit) {
            onTouch = { closer?.cancel() }
            onDispose { onTouch = null }
        }

        /**
         * Whether to listen again after her answer, by the Keep listening setting: always, only after a
         * question, or never.
         */
        fun listenAgainAfter(answer: String): Boolean = when (Prefs.followUp(this@AskActivity)) {
            FollowUp.ALWAYS -> true
            FollowUp.QUESTIONS -> answer.trimEnd().endsWith("?")
            FollowUp.OFF -> false
        }

        /**
         * Reads the answer aloud, then listens again or (on a wall screen) closes after a moment. If your
         * server's voice fails, the phone's voice takes over for the rest of the sheet.
         */
        fun speakThenMaybeListen(answer: String) {
            val voice = mouth ?: newMouth().also { mouth = it }
            val plain = Markdown.plain(answer)
            if (voice == null || plain.isBlank()) {
                if (plain.isNotBlank() && listenAgainAfter(plain)) listen(true) else {
                    phase = Phase.IDLE
                    // Not read aloud: time to read it first.
                    closeAfter((4_000L + plain.length * 50L).coerceAtMost(30_000L))
                }
                return
            }
            phase = Phase.SPEAKING
            val tone = if (Prefs.moodVoice(this@AskActivity)) VoiceTone.of(feelings) else VoiceTone()
            voice.tone = tone
            job = scope.launch {
                try {
                    withSpeechFocus(this@AskActivity) { voice.say(plain) }
                } catch (c: CancellationException) {
                    throw c
                } catch (e: Exception) {
                    // Your server's voice failed (for example, no text-to-speech engine set): use the phone's.
                    Logs.w("Voice", "Couldn't speak with ${if (voice is ServerMouth) "your server's voice; trying the phone's" else "the phone's voice"}", e)
                    if (voice is ServerMouth) runCatching {
                        val phone = PhoneMouth(this@AskActivity).also { mouth = it; it.tone = tone }
                        withSpeechFocus(this@AskActivity) { phone.say(plain) }
                    }
                }
                if (listenAgainAfter(plain)) listen(true) else {
                    phase = Phase.IDLE
                    closeAfter(5_000)
                }
            }
        }

        /**
         * Sends a question (with any photo) into the Assistant Lena chat, follows the answer as it's
         * written, then speaks it if you spoke. Errors become a short note on the sheet rather than a
         * crash.
         */
        fun send(q: String, spoken: Boolean) {
            val image = photo
            if (q.isBlank() && image == null) return
            val turn = Turn(q.trim().ifBlank { "What's in this photo?" }, image?.preview)
            photo = null
            turns += turn
            text = ""
            note = null
            phase = Phase.THINKING
            job?.cancel()
            job = scope.launch {
                try {
                    ready.await()
                    val m = model ?: api.defaultModel(this@AskActivity).also { model = it }
                        ?: throw IllegalStateException("No models on your server.")
                    val started = api.ask(
                        turn.question, m, chatId, lastReplyId,
                        newTitle = if (ongoing) ASSISTANT_CHAT else if (startedLocked) "Lena (lock screen)" else null,
                        pinNew = ongoing,
                        contextTokens = Prefs.contextTokens(this@AskActivity),
                        spoken = spoken,
                        image = image?.dataUrl,
                        locked = locked.value,
                    )
                    running = started
                    working = true
                    chatId = started.chatId
                    if (ongoing) Prefs.setAssistChat(this@AskActivity, started.chatId)
                    api.follow(started.chatId, started.messageId).collect { r -> turn.answer = r.content }
                    lastReplyId = started.messageId
                    turn.done = true
                    working = false
                    // She may have reacted to what you said; show how she feels now.
                    if (house != null) launch {
                        feelings = runCatching { house.feelings() }.getOrNull() ?: feelings
                        if (Prefs.widgetMood(this@AskActivity)) runCatching { LenaWidget().updateAll(this@AskActivity) }
                    }
                    if (spoken) speakThenMaybeListen(turn.answer) else phase = Phase.IDLE
                } catch (e: CancellationException) {
                    throw e
                } catch (_: AuthException) {
                    note = "Open Lena and sign in first."
                    phase = Phase.IDLE
                } catch (e: IllegalStateException) {
                    Logs.w("Ask", e.message ?: "Couldn't ask", e)
                    note = e.message
                    phase = Phase.IDLE
                } catch (e: Exception) {
                    Logs.e("Ask", "Couldn't get an answer", e)
                    if (phase == Phase.THINKING) {
                        note = "Couldn't reach your server."
                        phase = Phase.IDLE
                        closeAfter(8_000)
                    }
                } finally {
                    working = false
                }
            }
        }

        val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) listen(false) else {
                note = "Lena needs the microphone to hear you."
                typing = true
            }
        }

        val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) scope.launch { photo = Photos.load(this@AskActivity, uri) ?: run { note = "Couldn't open that photo."; null } }
        }
        val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
            if (taken) scope.launch {
                photo = Photos.load(this@AskActivity, Photos.cameraUri(this@AskActivity)) ?: run { note = "Couldn't read the photo."; null }
            }
        }

        // Your server's speech-to-text, for when the phone's recognizer can't listen this time.
        suspend fun hearViaServer(onPartial: (String) -> Unit, onLevel: (Float) -> Unit): String = try {
            ServerEar(api).also { fallbackEar = it }.listen(onPartial, onLevel)
        } catch (c: CancellationException) {
            throw c
        } catch (_: AuthException) {
            note = "Open Lena and sign in first."
            ""
        } catch (e: Exception) {
            Logs.w("Voice", "Your server's speech-to-text failed", e)
            note = "Couldn't hear you: your server's speech-to-text didn't answer. Check Open WebUI → Admin → Audio."
            ""
        }

        /**
         * Listens once and returns what you said. Falls back to your server's speech-to-text when the
         * phone's recognizer is still downloading or can't listen, and turns every failure into a note.
         */
        suspend fun hear(): String {
            val onPartial = { t: String -> heard = t }
            val onLevel = { l: Float -> level = l }
            val e = ear ?: newEar().also { ear = it }
            return try {
                e.listen(onPartial, onLevel)
            } catch (c: CancellationException) {
                throw c
            } catch (_: EarUnavailable) {
                // The phone's speech model is still downloading: use your server meanwhile.
                ear = ServerEar(api)
                note = "The phone's speech model is downloading; using your server for now."
                hearViaServer(onPartial, onLevel)
            } catch (err: EarError) {
                Logs.w("Voice", "The phone couldn't listen: ${err.describe()}")
                if (err.code == android.speech.SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                    note = "Lena needs the microphone to hear you."
                    ""
                } else {
                    // The phone couldn't listen this time; your server can.
                    hearViaServer(onPartial, onLevel).also {
                        if (it.isBlank() && note == null) note = "Couldn't hear you (${err.describe()})."
                    }
                }
            } catch (_: AuthException) {
                note = "Open Lena and sign in first."
                ""
            } catch (e: Exception) {
                Logs.w("Voice", "Listening failed", e)
                note = "Couldn't hear you: your server's speech-to-text didn't answer. Check Open WebUI → Admin → Audio."
                ""
            }
        }

        listen = listen@{ followUp ->
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                micPermission.launch(Manifest.permission.RECORD_AUDIO)
                return@listen
            }
            job?.cancel()
            mouth?.stop()
            typing = false
            note = null
            heard = ""
            level = 0f
            phase = Phase.LISTENING
            view.performHapticFeedback(HapticFeedbackConstants.GESTURE_START)
            job = scope.launch {
                // After Lena speaks, a short pause so the mic doesn't catch the end of her voice.
                if (followUp) delay(350)
                val said = hear()
                level = 0f
                if (said.isBlank()) {
                    phase = Phase.IDLE
                    // Saying nothing after a reply just ends the conversation; no warning.
                    if (note == null && !followUp) note = "Didn't catch that."
                    // From a wall screen, silence means you're done: back to it.
                    closeAfter(if (note != null && !followUp) 2_000 else 300)
                } else if (isGoodbye(said)) {
                    Logs.i("Ask", "Heard a goodbye; closing")
                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    phase = Phase.IDLE
                    goBack()
                } else {
                    heard = said
                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    send(said, spoken = true)
                }
            }
        }

        /** The big button: stop listening and send, stop her speaking, or start listening. */
        fun tapOrb() {
            when (phase) {
                Phase.LISTENING -> {
                    ear?.finish()
                    fallbackEar?.finish()
                }
                Phase.SPEAKING -> {
                    job?.cancel()
                    mouth?.stop()
                    phase = Phase.IDLE
                }
                Phase.THINKING -> Unit
                Phase.IDLE -> listen(false)
            }
        }

        LaunchedEffect(Unit) {
            // Pick up the "Assistant Lena" chat, and show where you left off. It's found by name too, so
            // it's the same chat after reinstalling, on another phone, or once it was made elsewhere.
            try {
                var saved = if (ongoing) Prefs.assistChat(this@AskActivity) else null
                var found = saved?.let { id -> runCatching { api.chatOrNull(id) } }
                if (ongoing && (saved == null || found?.getOrNull() == null && found?.isSuccess == true)) {
                    saved = runCatching { api.chatTitled(ASSISTANT_CHAT)?.id }.getOrNull()
                    Prefs.setAssistChat(this@AskActivity, saved)
                    found = saved?.let { id -> runCatching { api.chatOrNull(id) } }
                }
                when {
                    saved == null -> Unit // the first time: it's made when you first say something
                    found == null || found.isFailure -> chatId = saved // can't check right now; try it when you send
                    found.getOrNull() == null -> Prefs.setAssistChat(this@AskActivity, null) // deleted: start a new one
                    else -> {
                        chatId = saved
                        val chat = found.getOrNull()!!
                        // Found by its saved id but renamed (by hand, or by Open WebUI): give it its name back,
                        // so it can still be found by name from another phone.
                        val id: String = saved
                        if (chat.optString("title") != ASSISTANT_CHAT) runCatching { api.renameChat(id, ASSISTANT_CHAT) }
                        val earlier = api.recentTurns(chat, 50).map { (q, a) ->
                            Turn(q).also { it.answer = a; it.done = true }
                        }
                        turns.addAll(0, earlier)
                    }
                }
            } finally {
                ready.complete(Unit)
            }
            model = runCatching { api.defaultModel(this@AskActivity) }.getOrNull()
        }
        LaunchedEffect(Unit) {
            if (house != null) feelings = runCatching { house.feelings() }.getOrNull()
        }
        LaunchedEffect(restart.intValue) {
            when {
                restart.intValue == 0 && autoPrompt != null -> send(autoPrompt, spoken = false)
                restart.intValue > 0 || !typing -> listen(false)
                else -> runCatching { focus.requestFocus() }
            }
        }

        val f = feelings
        val ui = AskUi(
            phase = phase,
            typing = typing,
            text = text,
            turns = turns,
            heard = heard,
            level = level,
            note = note,
            // Locked: her mood, but not the reasons (they can mention people and the house).
            feelings = if (f != null && locked.value) f.copy(moodWhy = "", momentWhy = "") else f,
            model = model,
            chatId = chatId,
            photo = photo,
            prompts = prompts,
            lockedHint = startedLocked && turns.isEmpty(),
            locked = locked.value,
            scroll = scroll,
            focus = focus,
            onText = { text = it },
            send = { if (phase == Phase.IDLE) send(text, spoken = false) },
            sendPrompt = { send(it, spoken = false) },
            tapOrb = { tapOrb() },
            toggleTyping = {
                if (typing) listen(false) else {
                    job?.takeIf { phase == Phase.LISTENING }?.cancel()
                    ear?.release()
                    ear = null
                    if (phase == Phase.LISTENING) phase = Phase.IDLE
                    typing = true
                }
            },
            newConversation = {
                // Start over: the old conversation stays in your history under a dated name, unpinned,
                // and the next thing you say starts a new "Assistant Lena".
                chatId?.takeIf { ongoing }?.let { old ->
                    val day = java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date())
                    scope.launch {
                        runCatching { api.renameChat(old, "$ASSISTANT_CHAT (until $day)") }
                        runCatching { if (api.pinned().any { it.id == old }) api.togglePin(old) }
                    }
                }
                job?.cancel()
                mouth?.stop()
                ear?.finish()
                turns.clear()
                chatId = null
                lastReplyId = null
                note = null
                heard = ""
                if (ongoing) Prefs.setAssistChat(this@AskActivity, null)
                phase = Phase.IDLE
                if (!typing) listen(false)
            },
            takePhoto = {
                runCatching { takePhoto.launch(Photos.cameraUri(this@AskActivity)) }
                    .onFailure { note = "No camera app to take a photo with." }
            },
            choosePhoto = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            removePhoto = { photo = null },
            open = {
                val id = chatId
                if (id != null) openChat(id) else unlockThen {
                    startActivity(Intent(this@AskActivity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    finish()
                }
            },
            copy = {
                turns.lastOrNull()?.let {
                    getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Lena", Markdown.plain(it.answer)))
                }
            },
            close = { finish() },
        )

        when (style) {
            AskStyle.SHEET -> SheetLook(ui)
            AskStyle.GLOW -> GlowLook(ui)
            AskStyle.BAR -> BarLook(ui)
            AskStyle.ORB -> OrbLook(ui)
            AskStyle.ISLAND -> IslandLook(ui)
            AskStyle.SUBTITLES -> SubtitlesLook(ui)
        }
    }

    /** Everything a look needs: what's happening, and what it can do. All six looks share it. */
    private class AskUi(
        val phase: Phase,
        val typing: Boolean,
        val text: String,
        val turns: List<Turn>,
        val heard: String,
        val level: Float,
        val note: String?,
        val feelings: Feelings?,
        val model: Model?,
        val chatId: String?,
        val photo: Photo?,
        val prompts: List<String>,
        val lockedHint: Boolean,
        val locked: Boolean,
        val scroll: ScrollState,
        val focus: FocusRequester,
        val onText: (String) -> Unit,
        val send: () -> Unit,
        val sendPrompt: (String) -> Unit,
        val tapOrb: () -> Unit,
        val toggleTyping: () -> Unit,
        val newConversation: () -> Unit,
        val takePhoto: () -> Unit,
        val choosePhoto: () -> Unit,
        val removePhoto: () -> Unit,
        val open: () -> Unit,
        val copy: () -> Unit,
        val close: () -> Unit,
    ) {
        val last: Turn? get() = turns.lastOrNull()
        val canSend: Boolean get() = (text.isNotBlank() || photo != null) && phase == Phase.IDLE
        val answered: Boolean get() = last?.done == true && phase == Phase.IDLE

        /** One line saying what's happening, for the looks that don't show the whole answer. */
        val status: String
            get() = when (phase) {
                Phase.LISTENING -> heard.ifBlank { "Listening…" }
                Phase.THINKING -> "Thinking…"
                Phase.SPEAKING -> "Speaking"
                Phase.IDLE -> if (typing) "Type to Lena" else "Tap to talk"
            }
    }

    // ---- Shared pieces ------------------------------------------------------------------------

    /** The answer so far, or a spinner while she thinks. */
    @Composable
    private fun Answer(t: Turn, style: TextStyle, ink: Color, codeBg: Color, align: TextAlign = TextAlign.Start) {
        val shown = Markdown.clean(t.answer)
        if (!t.done && shown.isBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(Modifier.size(18.dp), color = ink, strokeWidth = 2.dp)
                Text(if (t.answer.isBlank()) "Thinking…" else "Using tools…", style = MaterialTheme.typography.bodyMedium, color = ink.copy(alpha = 0.75f))
            }
        } else if (shown.isNotBlank()) {
            Text(Markdown.render(t.answer, codeBg), style = style, color = ink, textAlign = align)
        }
    }

    /** Scrolls itself to the end as her answer streams in. */
    @Composable
    private fun FollowingScroll(a: AskUi, modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
        val scroll = rememberScrollState()
        val length = a.last?.answer?.length ?: 0
        LaunchedEffect(a.turns.size, length) { scroll.animateScrollTo(scroll.maxValue) }
        Column(modifier.verticalScroll(scroll), content = content)
    }

    /** Problems, and the lock-screen notice. */
    @Composable
    private fun Notes(a: AskUi, ink: Color, error: Color, align: TextAlign = TextAlign.Start) {
        if (a.lockedHint) Text(
            "Locked: earlier conversations stay hidden and nothing opens until you unlock.",
            style = MaterialTheme.typography.bodySmall, color = ink.copy(alpha = 0.7f), textAlign = align,
        )
        a.note?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = error, textAlign = align) }
    }

    /** The photo you're about to send, with a way to take it off. */
    @Composable
    private fun PhotoChip(a: AskUi, ink: Color) {
        val p = a.photo ?: return
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(p.preview, null, contentScale = ContentScale.Crop, modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)))
            Text(
                if (a.typing) "Photo attached. Ask about it, or just send." else "Photo attached. Tap the mic and ask about it.",
                style = MaterialTheme.typography.bodySmall, color = ink.copy(alpha = 0.8f),
                modifier = Modifier.weight(1f, fill = false).padding(horizontal = 10.dp),
            )
            IconButton(onClick = a.removePhoto) { Icon(Icons.Outlined.Close, "Remove the photo", tint = ink) }
        }
    }

    /** Everything that doesn't fit on a small look: talk or type, start over, photos, copy, call, open. */
    @Composable
    private fun MoreMenu(a: AskUi, tint: Color, everything: Boolean = true) {
        var open by remember { mutableStateOf(false) }
        Box {
            IconButton(onClick = { open = true }) { Icon(Icons.Rounded.MoreVert, "More", tint = tint) }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                if (everything) {
                    DropdownMenuItem(
                        text = { Text(if (a.typing) "Talk instead" else "Type instead") },
                        leadingIcon = { Icon(if (a.typing) Icons.Rounded.Mic else Icons.Outlined.Keyboard, null) },
                        onClick = { open = false; a.toggleTyping() },
                    )
                    if (a.turns.isNotEmpty() || a.chatId != null) DropdownMenuItem(
                        text = { Text("New conversation") },
                        leadingIcon = { Icon(Icons.Outlined.AddComment, null) },
                        onClick = { open = false; a.newConversation() },
                    )
                    if (!a.locked) {
                        DropdownMenuItem(
                            text = { Text("Take a photo") },
                            leadingIcon = { Icon(Icons.Outlined.PhotoCamera, null) },
                            onClick = { open = false; a.takePhoto() },
                        )
                        DropdownMenuItem(
                            text = { Text("Choose a photo") },
                            leadingIcon = { Icon(Icons.Outlined.Image, null) },
                            onClick = { open = false; a.choosePhoto() },
                        )
                    }
                    if (a.answered) DropdownMenuItem(
                        text = { Text("Copy her answer") },
                        leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) },
                        onClick = { open = false; a.copy() },
                    )
                }
                DropdownMenuItem(
                    text = { Text(if (a.chatId != null) "Open this chat in Lena" else "Open Lena") },
                    leadingIcon = { Icon(Icons.Outlined.OpenInFull, null) },
                    onClick = { open = false; a.open() },
                )
            }
        }
    }

    /** A rounded box to type in, with your quick prompts above it before you've said anything. */
    @Composable
    private fun TypeBar(a: AskUi, modifier: Modifier = Modifier, leading: (@Composable () -> Unit)? = null) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (a.turns.isEmpty() && a.prompts.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    a.prompts.forEach { p ->
                        AssistChip(
                            onClick = { a.sendPrompt(p) },
                            label = { Text(p, maxLines = 1) },
                            colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        )
                    }
                }
            }
            Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
                TextField(
                    value = a.text,
                    onValueChange = a.onText,
                    placeholder = { Text(if (a.turns.isEmpty()) "Ask Lena" else "Reply") },
                    maxLines = 5,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { a.send() }),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    leadingIcon = leading,
                    trailingIcon = {
                        IconButton(onClick = a.send, enabled = a.canSend) { Icon(Icons.AutoMirrored.Rounded.Send, "Send") }
                    },
                    modifier = Modifier.fillMaxWidth().focusRequester(a.focus),
                )
            }
        }
    }

    /** The round talk / stop button. */
    @Composable
    private fun TalkButton(a: AskUi, size: Dp, container: Color, ink: Color) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(size).clip(CircleShape).background(container)
                .clickable(enabled = a.phase != Phase.THINKING, onClick = a.tapOrb),
        ) {
            when (a.phase) {
                Phase.THINKING -> CircularProgressIndicator(Modifier.size(size * 0.4f), color = ink, strokeWidth = 2.dp)
                Phase.SPEAKING -> Icon(Icons.Rounded.Stop, "Stop", tint = ink)
                Phase.LISTENING -> Icon(Icons.Rounded.Mic, "Done talking", tint = ink)
                Phase.IDLE -> Icon(Icons.Rounded.Mic, "Talk", tint = ink)
            }
        }
    }

    /** Lena's colours, from her logo. */
    private val glowColors = listOf(
        Color(0xFF22D3EE), Color(0xFF3B82F6), Color(0xFFA855F7), Color(0xFFEC4899), Color(0xFFF97316), Color(0xFF22D3EE),
    )

    /** Bars that move with your voice while she listens, and gently while she speaks. */
    @Composable
    private fun Wave(a: AskUi, color: Color, bars: Int = 5, height: Dp = 18.dp, modifier: Modifier = Modifier) {
        val t = rememberInfiniteTransition(label = "wave")
        val phase by t.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "phase")
        val loud by animateFloatAsState(
            when (a.phase) {
                Phase.LISTENING -> 0.25f + a.level * 0.75f
                Phase.SPEAKING -> 0.6f
                Phase.THINKING -> 0.3f
                Phase.IDLE -> 0.15f
            },
            label = "loud",
        )
        Canvas(modifier.size(width = (bars * 6).dp, height = height)) {
            val w = size.width / (bars * 2 - 1)
            for (i in 0 until bars) {
                val h = size.height * (0.25f + 0.75f * loud * (0.5f + 0.5f * sin(phase + i * 1.3f)))
                drawRoundRect(
                    color,
                    topLeft = Offset(i * 2 * w, (size.height - h) / 2),
                    size = Size(w, h),
                    cornerRadius = CornerRadius(w / 2, w / 2),
                )
            }
        }
    }

    /** A tap on empty space closes the look. */
    private fun Modifier.closesOnTap(a: AskUi): Modifier =
        this.clickable(interactionSource = MutableInteractionSource(), indication = null, onClick = a.close)

    /** Stops a tap from falling through to [closesOnTap] behind it. */
    private fun Modifier.keepsTaps(): Modifier =
        this.clickable(interactionSource = MutableInteractionSource(), indication = null) { }

    // ---- 1. Sheet: the conversation in a panel from the bottom ---------------------------------

    /** The Sheet look: a panel from the bottom with the whole conversation, the input row and quick prompts. */
    @Composable
    private fun SheetLook(a: AskUi) {
        val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.85f).dp
        val codeBg = MaterialTheme.colorScheme.surfaceVariant
        val length = a.last?.answer?.length ?: 0
        LaunchedEffect(a.turns.size, length) { a.scroll.animateScrollTo(a.scroll.maxValue) }
        var photoMenu by remember { mutableStateOf(false) }

        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.32f)).closesOnTap(a)) {
            Surface(
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                tonalElevation = 3.dp,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().keepsTaps(),
            ) {
                Column(
                    Modifier.navigationBarsPadding().imePadding().heightIn(max = maxHeight)
                        .padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 12.dp),
                ) {
                    Box(
                        Modifier.align(Alignment.CenterHorizontally).padding(vertical = 6.dp)
                            .size(width = 32.dp, height = 4.dp)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(2.dp)),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LenaAvatar(36.dp, Modifier.padding(end = 12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Lena", style = MaterialTheme.typography.titleMedium)
                            val f = a.feelings
                            if (f != null) FeelingsLine(f)
                            else a.model?.let {
                                Text(it.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (a.turns.isNotEmpty() || a.chatId != null) IconButton(onClick = a.newConversation) {
                            Icon(Icons.Outlined.AddComment, "New conversation")
                        }
                        if (!a.locked) Box {
                            IconButton(onClick = { photoMenu = true }) { Icon(Icons.Outlined.AddAPhoto, "Show Lena a photo") }
                            DropdownMenu(expanded = photoMenu, onDismissRequest = { photoMenu = false }) {
                                DropdownMenuItem(text = { Text("Take a photo") }, onClick = { photoMenu = false; a.takePhoto() })
                                DropdownMenuItem(text = { Text("Choose a photo") }, onClick = { photoMenu = false; a.choosePhoto() })
                            }
                        }
                        IconButton(onClick = a.toggleTyping) {
                            Icon(if (a.typing) Icons.Rounded.Mic else Icons.Outlined.Keyboard, if (a.typing) "Talk" else "Type")
                        }
                        // Less-used actions, out of the way so her name and mood have room.
                        MoreMenu(a, MaterialTheme.colorScheme.onSurfaceVariant, everything = false)
                    }

                    if (a.lockedHint) {
                        Text(
                            "Your phone is locked, so earlier conversations stay hidden and she won't open or unlock anything. Unlock to see everything.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp, end = 8.dp),
                        )
                    }

                    // The conversation so far
                    if (a.turns.isNotEmpty()) {
                        Column(Modifier.weight(1f, fill = false).verticalScroll(a.scroll).padding(end = 8.dp)) {
                            a.turns.forEachIndexed { i, t ->
                                Spacer(Modifier.height(if (i == 0) 8.dp else 16.dp))
                                t.image?.let {
                                    Image(it, "Your photo", contentScale = ContentScale.Crop,
                                        modifier = Modifier.padding(bottom = 6.dp).size(width = 160.dp, height = 120.dp).clip(RoundedCornerShape(12.dp)))
                                }
                                Text(t.question, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(8.dp))
                                Answer(t, MaterialTheme.typography.bodyLarge, MaterialTheme.colorScheme.onSurface, codeBg)
                            }
                        }
                    }

                    a.note?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 8.dp))
                    }

                    // Continue / copy once there's an answer
                    if (a.answered) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
                            if (a.chatId != null) TextButton(onClick = a.open) { Text("Continue in Lena") }
                            TextButton(onClick = a.copy) {
                                Icon(Icons.Outlined.ContentCopy, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Copy")
                            }
                        }
                    }

                    if (a.photo != null) Box(Modifier.padding(top = 8.dp)) { PhotoChip(a, MaterialTheme.colorScheme.onSurface) }

                    if (a.typing) TypeBar(a, Modifier.padding(top = 12.dp, end = 8.dp))
                    else VoiceBar(a.phase, a.heard, a.level, onTap = a.tapOrb)
                }
            }
        }
    }

    /** The mic button with its status line and level ring, shared by several looks. */
    @Composable
    private fun VoiceBar(phase: Phase, heard: String, level: Float, onTap: () -> Unit) {
        val status = when (phase) {
            Phase.LISTENING -> heard.ifBlank { "Listening…" }
            Phase.THINKING -> heard.ifBlank { "Thinking…" }
            Phase.SPEAKING -> "Tap to stop"
            Phase.IDLE -> "Tap to talk"
        }
        val pulse = rememberInfiniteTransition(label = "pulse")
        val breathe by pulse.animateFloat(
            initialValue = 1f, targetValue = 1.08f,
            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "breathe",
        )
        val scale by animateFloatAsState(
            when (phase) {
                Phase.LISTENING -> 1f + level * 0.35f
                Phase.SPEAKING -> breathe
                else -> 1f
            },
            label = "scale",
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp, end = 8.dp),
        ) {
            Text(
                status,
                style = if (phase == Phase.LISTENING && heard.isNotBlank()) MaterialTheme.typography.titleMedium
                else MaterialTheme.typography.bodyMedium,
                color = if (phase == Phase.LISTENING && heard.isNotBlank()) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 3,
            )
            Spacer(Modifier.height(16.dp))
            val active = phase == Phase.LISTENING || phase == Phase.SPEAKING
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(72.dp)
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .clip(CircleShape)
                    .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer)
                    .clickable(enabled = phase != Phase.THINKING, onClick = onTap),
            ) {
                when (phase) {
                    Phase.THINKING -> CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                    Phase.SPEAKING -> Icon(Icons.Rounded.Stop, "Stop", tint = MaterialTheme.colorScheme.onPrimary)
                    Phase.LISTENING -> Icon(Icons.Rounded.Mic, "Done talking", tint = MaterialTheme.colorScheme.onPrimary)
                    Phase.IDLE -> Icon(Icons.Rounded.Mic, "Talk", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    // ---- 2. Glow: light around the edges of the screen, like Siri ------------------------------

    /**
     * The Glow look: Lena's colours run around the screen's edge, brighter as you speak, with a caption
     * card.
     */
    @Composable
    private fun GlowLook(a: AskUi) {
        val view = LocalView.current
        val density = LocalDensity.current
        // Follow the screen's own rounded corners, so the glow hugs the glass.
        val corner = remember(view) {
            view.rootWindowInsets?.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)?.radius?.toFloat()
        } ?: with(density) { 36.dp.toPx() }
        val spin = rememberInfiniteTransition(label = "spin")
        val turn by spin.animateFloat(
            0f, 360f,
            infiniteRepeatable(tween(if (a.phase == Phase.THINKING) 1400 else 4200, easing = LinearEasing)),
            label = "turn",
        )
        val breathe by spin.animateFloat(0.75f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "breathe")
        val strength by animateFloatAsState(
            when (a.phase) {
                Phase.LISTENING -> 0.55f + a.level * 0.45f
                Phase.THINKING -> 0.8f
                Phase.SPEAKING -> breathe
                Phase.IDLE -> 0.45f
            },
            animationSpec = tween(180), label = "strength",
        )
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.18f)).closesOnTap(a)) {
            // Soft outer glow, then a crisp line on the edge.
            Canvas(Modifier.fillMaxSize().blur(28.dp, BlurredEdgeTreatment.Unbounded)) {
                drawGlowFrame(turn, corner, with(density) { (14 + 22 * strength).dp.toPx() }, strength)
            }
            Canvas(Modifier.fillMaxSize()) { drawGlowFrame(turn, corner, with(density) { 3.dp.toPx() }, strength) }

            // A small floating caption above the navigation bar.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .navigationBarsPadding().imePadding().padding(horizontal = 16.dp, vertical = 20.dp),
            ) {
                val showCard = a.turns.isNotEmpty() || a.phase == Phase.LISTENING || a.note != null || a.lockedHint || a.photo != null
                if (showCard) Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
                    modifier = Modifier.fillMaxWidth().keepsTaps(),
                ) {
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val last = a.last
                        when {
                            a.phase == Phase.LISTENING -> Text(a.heard.ifBlank { "Listening…" }, style = MaterialTheme.typography.titleMedium,
                                color = if (a.heard.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                            last != null -> {
                                Text(last.question, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                                FollowingScroll(a, Modifier.heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.4f).dp)) {
                                    Answer(last, MaterialTheme.typography.bodyLarge, MaterialTheme.colorScheme.onSurface, MaterialTheme.colorScheme.surfaceVariant)
                                }
                            }
                        }
                        PhotoChip(a, MaterialTheme.colorScheme.onSurface)
                        Notes(a, MaterialTheme.colorScheme.onSurface, MaterialTheme.colorScheme.error)
                    }
                }
                if (a.typing) TypeBar(a, Modifier.keepsTaps(), leading = { MoreMenu(a, MaterialTheme.colorScheme.onSurfaceVariant) })
                else Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.keepsTaps(),
                ) {
                    MoreMenu(a, Color.White)
                    TalkButton(a, 56.dp, Color.White.copy(alpha = 0.92f), Color.Black)
                    IconButton(onClick = a.toggleTyping) { Icon(Icons.Outlined.Keyboard, "Type", tint = Color.White) }
                }
            }
        }
    }

    /** Lena's colours swept round the screen's edge, turning with [turn]. */
    private fun DrawScope.drawGlowFrame(turn: Float, corner: Float, width: Float, strength: Float) {
        drawRoundRect(
            brush = Brush.sweepGradient(glowColors.shifted(turn), center = center),
            topLeft = Offset(width / 2, width / 2),
            size = Size(size.width - width, size.height - width),
            cornerRadius = CornerRadius(corner, corner),
            style = Stroke(width),
            alpha = strength.coerceIn(0f, 1f),
        )
    }

    /** The colour wheel turned by [degrees]: the same colours, starting further round. */
    private fun List<Color>.shifted(degrees: Float): List<Color> {
        val steps = size - 1 // the last colour repeats the first, closing the circle
        val f = ((degrees / 360f) * steps) % steps
        val whole = f.toInt()
        val part = f - whole
        val ring = dropLast(1)
        return (0..steps).map { i -> lerp(ring[(i + whole) % ring.size], ring[(i + whole + 1) % ring.size], part) }
    }

    // ---- 3. Bar: a compact bar at the bottom, like Google's assistant ---------------------------

    /** The Bar look: a compact bar at the bottom, her answer in a card above it. */
    @Composable
    private fun BarLook(a: AskUi) {
        var photoMenu by remember { mutableStateOf(false) }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)).closesOnTap(a)) {
            Surface(
                shape = RoundedCornerShape(32.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 2.dp,
                shadowElevation = 8.dp,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .navigationBarsPadding().imePadding().padding(12.dp).keepsTaps(),
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Her answer, in a card that grows above the bar.
                    val last = a.last
                    if (last != null) Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                LenaAvatar(22.dp)
                                Text(last.question, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            }
                            FollowingScroll(a, Modifier.heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.45f).dp)) {
                                Answer(last, MaterialTheme.typography.bodyLarge, MaterialTheme.colorScheme.onSurface, MaterialTheme.colorScheme.surfaceVariant)
                            }
                            if (a.answered) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AssistChip(onClick = a.copy, label = { Text("Copy") }, leadingIcon = { Icon(Icons.Outlined.ContentCopy, null, Modifier.size(18.dp)) })
                                if (a.chatId != null) AssistChip(onClick = a.open, label = { Text("Open in Lena") },
                                    leadingIcon = { Icon(Icons.Outlined.OpenInFull, null, Modifier.size(18.dp)) })
                                AssistChip(onClick = a.newConversation, label = { Text("New") },
                                    leadingIcon = { Icon(Icons.Outlined.AddComment, null, Modifier.size(18.dp)) })
                            }
                        }
                    }
                    PhotoChip(a, MaterialTheme.colorScheme.onSurface)
                    Notes(a, MaterialTheme.colorScheme.onSurface, MaterialTheme.colorScheme.error)

                    // The bar: listening shows your words and a wave; otherwise a box to type in.
                    if (a.phase == Phase.LISTENING) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(28.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(start = 18.dp, end = 6.dp),
                        ) {
                            Wave(a, MaterialTheme.colorScheme.primary)
                            Text(a.heard.ifBlank { "Listening…" }, style = MaterialTheme.typography.bodyLarge, maxLines = 2,
                                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                                color = if (a.heard.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                            TalkButton(a, 44.dp, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary)
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TypeBar(
                                a, Modifier.weight(1f),
                                leading = if (a.locked) null else ({
                                    Box {
                                        IconButton(onClick = { photoMenu = true }) { Icon(Icons.Outlined.AddAPhoto, "Show Lena a photo") }
                                        DropdownMenu(expanded = photoMenu, onDismissRequest = { photoMenu = false }) {
                                            DropdownMenuItem(text = { Text("Take a photo") }, onClick = { photoMenu = false; a.takePhoto() })
                                            DropdownMenuItem(text = { Text("Choose a photo") }, onClick = { photoMenu = false; a.choosePhoto() })
                                        }
                                    }
                                }),
                            )
                            Spacer(Modifier.width(6.dp))
                            TalkButton(
                                a, 52.dp,
                                if (a.phase == Phase.SPEAKING) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
                                if (a.phase == Phase.SPEAKING) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            MoreMenu(a, MaterialTheme.colorScheme.onSurfaceVariant, everything = false)
                        }
                    }
                }
            }
        }
    }

    // ---- 4. Orb: her logo, alive in the middle of the screen ------------------------------------

    /** The Orb look: her logo pulsing in the middle of a darkened screen, her words underneath. */
    @Composable
    private fun OrbLook(a: AskUi) {
        val white = Color.White
        val dim = Color.White.copy(alpha = 0.7f)
        val pulse = rememberInfiniteTransition(label = "orb")
        val breathe by pulse.animateFloat(0f, 1f, infiniteRepeatable(tween(1600), RepeatMode.Reverse), label = "breathe")
        val ripple by pulse.animateFloat(0f, 1f, infiniteRepeatable(tween(2200, easing = LinearEasing)), label = "ripple")
        val grow by animateFloatAsState(
            when (a.phase) {
                Phase.LISTENING -> 1f + a.level * 0.3f
                Phase.SPEAKING -> 1f + breathe * 0.08f
                Phase.THINKING -> 0.9f + breathe * 0.05f
                Phase.IDLE -> 1f
            },
            label = "grow",
        )
        val accent = glowColors[2]

        Box(Modifier.fillMaxSize().background(Color(0xE6050513)).closesOnTap(a)) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = 24.dp),
            ) {
                // Who, and how she feels
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 24.dp)) {
                    Text("Lena", style = MaterialTheme.typography.titleLarge, color = white)
                    a.feelings?.let { f ->
                        Text(listOfNotNull(f.mood, f.energy.ifBlank { null }, f.moment).joinToString(" · "),
                            style = MaterialTheme.typography.labelLarge, color = dim)
                    }
                }
                Spacer(Modifier.weight(0.8f))

                // The orb, with rings that spread while she listens or speaks
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(260.dp)) {
                    Canvas(Modifier.fillMaxSize()) {
                        val base = 70.dp.toPx() * grow
                        val active = a.phase == Phase.LISTENING || a.phase == Phase.SPEAKING
                        drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.55f), Color.Transparent), radius = base * 1.9f), base * 1.9f)
                        if (active) for (k in 0..1) {
                            val p = (ripple + k * 0.5f) % 1f
                            drawCircle(glowColors[k * 3].copy(alpha = (1 - p) * 0.5f), base * (1f + p * 0.8f), style = Stroke(2.dp.toPx()))
                        }
                    }
                    Box(
                        Modifier.size(140.dp).graphicsLayer { scaleX = grow; scaleY = grow }.clip(CircleShape)
                            .clickable(enabled = a.phase != Phase.THINKING, onClickLabel = "Talk or stop", onClick = a.tapOrb),
                    ) { LenaAvatar(140.dp) }
                }
                Spacer(Modifier.height(24.dp))

                // Her words, as captions
                val last = a.last
                FollowingScroll(a, Modifier.weight(1f).fillMaxWidth()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        when {
                            a.phase == Phase.LISTENING -> Text(a.heard.ifBlank { "Listening…" }, style = MaterialTheme.typography.headlineSmall,
                                color = if (a.heard.isBlank()) dim else white, textAlign = TextAlign.Center)
                            last != null -> Answer(last, MaterialTheme.typography.titleLarge, white, Color.White.copy(alpha = 0.12f), TextAlign.Center)
                            else -> Text(if (a.typing) "Type to Lena" else "Tap me to talk", style = MaterialTheme.typography.titleMedium,
                                color = dim, textAlign = TextAlign.Center)
                        }
                        Spacer(Modifier.height(12.dp))
                        PhotoChip(a, white)
                        Notes(a, white, Color(0xFFFFB4AB), TextAlign.Center)
                    }
                }

                if (a.typing) TypeBar(a, Modifier.padding(bottom = 12.dp).keepsTaps(), leading = { MoreMenu(a, MaterialTheme.colorScheme.onSurfaceVariant) })
                else Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    modifier = Modifier.padding(bottom = 16.dp).keepsTaps(),
                ) {
                    IconButton(onClick = a.toggleTyping) { Icon(Icons.Outlined.Keyboard, "Type", tint = white) }
                    IconButton(onClick = a.close) { Icon(Icons.Outlined.Close, "Close", tint = white) }
                    MoreMenu(a, white)
                }
            }
        }
    }

    // ---- 5. Island: a small pill at the top that grows into a card -------------------------------

    /** The Island look: a small pill at the top that drops down to show her answer. */
    @Composable
    private fun IslandLook(a: AskUi) {
        val ink = Color.White
        val last = a.last
        val expanded = last != null || a.note != null || a.lockedHint || a.photo != null
        Box(Modifier.fillMaxSize().closesOnTap(a)) {
            Surface(
                shape = RoundedCornerShape(if (expanded) 32.dp else 28.dp),
                color = Color.Black,
                shadowElevation = 12.dp,
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 8.dp, start = 12.dp, end = 12.dp)
                    .animateContentSize().then(if (expanded) Modifier.fillMaxWidth() else Modifier.widthIn(min = 220.dp)).keepsTaps(),
            ) {
                Column(Modifier.padding(start = 10.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.clip(CircleShape).clickable(enabled = a.phase != Phase.THINKING, onClickLabel = "Talk or stop", onClick = a.tapOrb)) {
                            LenaAvatar(32.dp)
                        }
                        Text(
                            if (a.phase == Phase.IDLE && last?.done == true) "Lena" else a.status,
                            style = MaterialTheme.typography.titleSmall, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = expanded),
                        )
                        if (a.phase == Phase.THINKING) CircularProgressIndicator(Modifier.size(16.dp), color = glowColors[0], strokeWidth = 2.dp)
                        else Wave(a, glowColors[0], bars = 4, height = 16.dp)
                        MoreMenu(a, ink)
                    }
                    if (expanded) Column(
                        Modifier.padding(start = 6.dp, end = 10.dp, top = 8.dp, bottom = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (last != null) FollowingScroll(a, Modifier.heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.45f).dp)) {
                            Text(last.question, style = MaterialTheme.typography.bodySmall, color = ink.copy(alpha = 0.6f), maxLines = 2)
                            Spacer(Modifier.height(6.dp))
                            Answer(last, MaterialTheme.typography.bodyLarge, ink, Color.White.copy(alpha = 0.14f))
                        }
                        PhotoChip(a, ink)
                        Notes(a, ink, Color(0xFFFFB4AB))
                        if (a.answered) Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = a.tapOrb) { Text("Reply", color = glowColors[0]) }
                            TextButton(onClick = a.copy) { Text("Copy", color = glowColors[0]) }
                            if (a.chatId != null) TextButton(onClick = a.open) { Text("Open", color = glowColors[0]) }
                        }
                    }
                }
            }
            if (a.typing) TypeBar(
                a, Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().imePadding().padding(12.dp).keepsTaps(),
            )
        }
    }

    // ---- 6. Subtitles: her words at the bottom of the screen, nothing else ----------------------

    /** The Subtitles look: no panel, just her words at the bottom like film subtitles. */
    @Composable
    private fun SubtitlesLook(a: AskUi) {
        val ink = Color.White
        val shadow = Shadow(Color.Black, Offset(0f, 2f), 8f)
        val last = a.last
        Box(Modifier.fillMaxSize().closesOnTap(a)) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f), Color.Black.copy(alpha = 0.88f))))
                    .navigationBarsPadding().imePadding().padding(start = 24.dp, end = 24.dp, top = 96.dp, bottom = 12.dp),
            ) {
                Text("LENA", style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 3.sp, shadow = shadow), color = glowColors[0])
                FollowingScroll(a, Modifier.heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.4f).dp).fillMaxWidth()) {
                    val big = MaterialTheme.typography.titleLarge.copy(shadow = shadow, lineHeight = 30.sp)
                    when {
                        a.phase == Phase.LISTENING -> Text(
                            a.heard.ifBlank { "Listening…" }, style = big.copy(fontStyle = FontStyle.Italic),
                            color = ink.copy(alpha = 0.75f), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                        )
                        last != null -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Answer(last, big, ink, Color.White.copy(alpha = 0.16f), TextAlign.Center)
                        }
                        else -> Text(if (a.typing) "Type to Lena" else "Tap the mic to talk", style = big, color = ink.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }
                }
                PhotoChip(a, ink)
                Notes(a, ink, Color(0xFFFFB4AB), TextAlign.Center)
                if (a.typing) TypeBar(a, Modifier.keepsTaps(), leading = { MoreMenu(a, MaterialTheme.colorScheme.onSurfaceVariant) })
                else Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.keepsTaps(),
                ) {
                    IconButton(onClick = a.toggleTyping) { Icon(Icons.Outlined.Keyboard, "Type", tint = ink.copy(alpha = 0.8f)) }
                    Box(contentAlignment = Alignment.Center) {
                        TalkButton(a, 48.dp, Color.White.copy(alpha = 0.16f), ink)
                    }
                    MoreMenu(a, ink.copy(alpha = 0.8f))
                }
            }
        }
    }
}
