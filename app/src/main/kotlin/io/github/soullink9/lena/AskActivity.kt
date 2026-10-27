@file:OptIn(ExperimentalLayoutApi::class)

package io.github.soullink9.lena

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.HapticFeedbackConstants
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The Ask Lena sheet: talk (or type) to Lena over whatever app you're in, like Google Assistant
 * or Home Assistant's Assist. Answers are real Open WebUI chats, written on your server with your
 * model's tools; follow-ups continue the same chat, and "Continue in Lena" opens it.
 */
class AskActivity : ComponentActivity() {

    companion object {
        const val ACTION_ASK = "io.github.soullink9.lena.ASK"   // keyboard
        const val ACTION_TALK = "io.github.soullink9.lena.TALK" // listening
        private const val EXTRA_PROMPT = "prompt"
        private const val EXTRA_KEY = "key"

        /** Opens the sheet; with [prompt], sends it straight away. Only Lena's own widget and shortcuts use this. */
        fun intent(c: Context, prompt: String? = null, talk: Boolean = false): Intent = Intent(c, AskActivity::class.java)
            .setAction(if (talk) ACTION_TALK else ACTION_ASK)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .apply { if (prompt != null) putExtra(EXTRA_PROMPT, prompt).putExtra(EXTRA_KEY, Prefs.launchKey(c)) }
    }

    private enum class Phase { IDLE, LISTENING, THINKING, SPEAKING }

    private class Turn(val question: String) {
        var answer by mutableStateOf("")
        var done by mutableStateOf(false)
    }

    private lateinit var api: Api
    private var ear: Ear? = null
    private var mouth: Mouth? = null
    private var running: Api.Started? = null
    private var working = false
    private val restart = mutableIntStateOf(0) // bumped when the gesture is used again while the sheet is open
    private var startTalking = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val server = Prefs.server(this)
        if (server == null) {
            startActivity(Intent(this, SettingsActivity::class.java))
            finish()
            return
        }
        api = Api(server)

        // The assistant gesture can open a call or a chat instead of the sheet.
        if (intent.action == Intent.ACTION_ASSIST) {
            when (Prefs.assistAction(this)) {
                AssistAction.CALL -> return forward(MainActivity.ACTION_CALL)
                AssistAction.CHAT -> return forward(MainActivity.ACTION_NEW_CHAT)
                else -> Unit
            }
        }
        startTalking = wantsTalk(intent)

        // Shared text only fills the box. A prompt is sent automatically only with Lena's own key.
        val shared = if (intent.action == Intent.ACTION_SEND) {
            listOfNotNull(intent.getStringExtra(Intent.EXTRA_SUBJECT), intent.getStringExtra(Intent.EXTRA_TEXT))
                .distinct().joinToString("\n").trim()
        } else ""
        val auto = intent.getStringExtra(EXTRA_PROMPT)
            ?.takeIf { intent.getStringExtra(EXTRA_KEY) == Prefs.launchKey(this) }

        enableEdgeToEdge()
        setContent { LenaTheme { Sheet(shared, auto) } }
    }

    private fun wantsTalk(i: Intent) = i.action == ACTION_TALK ||
        (i.action == Intent.ACTION_ASSIST && Prefs.assistAction(this) == AssistAction.ASK)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (wantsTalk(intent)) restart.intValue++
    }

    private fun forward(action: String) {
        startActivity(Intent(this, MainActivity::class.java).setAction(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    private fun openChat(chatId: String) {
        startActivity(
            Intent(this, MainActivity::class.java).setAction(MainActivity.ACTION_OPEN_CHAT)
                .putExtra(MainActivity.EXTRA_CHAT_ID, chatId).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        working = false // you'll see it finish there
        finish()
    }

    private fun newEar(): Ear =
        if (Prefs.hearing(this) == Hearing.PHONE && PhoneEar.available(this)) PhoneEar(this) else ServerEar(api)

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

    override fun onDestroy() {
        ear?.release()
        mouth?.release()
        super.onDestroy()
    }

    @Composable
    private fun Sheet(initial: String, autoPrompt: String?) {
        val scope = rememberCoroutineScope()
        val view = LocalView.current
        var typing by remember { mutableStateOf(!startTalking) }
        var text by remember { mutableStateOf(initial) }
        val turns = remember { mutableStateListOf<Turn>() }
        var phase by remember { mutableStateOf(Phase.IDLE) }
        var heard by remember { mutableStateOf("") }
        var level by remember { mutableStateOf(0f) }
        var note by remember { mutableStateOf<String?>(null) }
        var model by remember { mutableStateOf<Model?>(null) }
        var chatId by remember { mutableStateOf<String?>(null) }
        var lastReplyId by remember { mutableStateOf<String?>(null) }
        var job by remember { mutableStateOf<Job?>(null) }
        val prompts = remember { Prefs.quickPrompts(this) }
        val focus = remember { FocusRequester() }
        val scroll = rememberScrollState()
        val codeBg = MaterialTheme.colorScheme.surfaceVariant

        lateinit var listen: () -> Unit

        fun speakThenMaybeListen(answer: String) {
            val voice = mouth ?: newMouth().also { mouth = it }
            val plain = Markdown.plain(answer)
            if (voice == null || plain.isBlank()) {
                phase = Phase.IDLE
                return
            }
            phase = Phase.SPEAKING
            job = scope.launch {
                try {
                    withSpeechFocus(this@AskActivity) { voice.say(plain) }
                } catch (c: CancellationException) {
                    throw c
                } catch (_: Exception) {
                    // Your server's voice failed (for example, no text-to-speech engine set): use the phone's.
                    if (voice is ServerMouth) runCatching {
                        val phone = PhoneMouth(this@AskActivity).also { mouth = it }
                        withSpeechFocus(this@AskActivity) { phone.say(plain) }
                    }
                }
                // Like Home Assistant's "continue conversation": a question gets your answer.
                if (Prefs.followUps(this@AskActivity) && plain.trimEnd().endsWith("?")) listen() else phase = Phase.IDLE
            }
        }

        fun send(q: String, spoken: Boolean) {
            if (q.isBlank()) return
            val turn = Turn(q.trim())
            turns += turn
            text = ""
            note = null
            phase = Phase.THINKING
            job?.cancel()
            job = scope.launch {
                try {
                    val m = model ?: api.defaultModel(this@AskActivity).also { model = it }
                        ?: throw IllegalStateException("No models on your server.")
                    val started = api.ask(turn.question, m, chatId, lastReplyId)
                    running = started
                    working = true
                    chatId = started.chatId
                    api.follow(started.chatId, started.messageId).collect { r -> turn.answer = r.content }
                    lastReplyId = started.messageId
                    turn.done = true
                    working = false
                    if (spoken) speakThenMaybeListen(turn.answer) else phase = Phase.IDLE
                } catch (e: CancellationException) {
                    throw e
                } catch (_: AuthException) {
                    note = "Open Lena and sign in first."
                    phase = Phase.IDLE
                } catch (e: IllegalStateException) {
                    note = e.message
                    phase = Phase.IDLE
                } catch (_: Exception) {
                    if (phase == Phase.THINKING) {
                        note = "Couldn't reach your server."
                        phase = Phase.IDLE
                    }
                } finally {
                    working = false
                }
            }
        }

        val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) listen() else {
                note = "Lena needs the microphone to hear you."
                typing = true
            }
        }

        listen = listen@{
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
                val e = ear ?: newEar().also { ear = it }
                val said = try {
                    e.listen(onPartial = { heard = it }, onLevel = { level = it })
                } catch (c: CancellationException) {
                    throw c
                } catch (_: EarUnavailable) {
                    // The phone's speech model is still downloading: use your server meanwhile.
                    ear = ServerEar(api)
                    note = "The phone's speech model is downloading; using your server for now."
                    try {
                        ear!!.listen(onPartial = { heard = it }, onLevel = { level = it })
                    } catch (c: CancellationException) {
                        throw c
                    } catch (_: Exception) {
                        ""
                    }
                } catch (_: AuthException) {
                    note = "Open Lena and sign in first."
                    ""
                } catch (_: Exception) {
                    note = "Couldn't hear you. Check your server's speech-to-text, or switch it in Settings."
                    ""
                }
                level = 0f
                if (said.isBlank()) {
                    phase = Phase.IDLE
                    if (note == null) note = "Didn't catch that."
                } else {
                    heard = said
                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    send(said, spoken = true)
                }
            }
        }

        fun tapOrb() {
            when (phase) {
                Phase.LISTENING -> ear?.finish()
                Phase.SPEAKING -> {
                    job?.cancel()
                    mouth?.stop()
                    phase = Phase.IDLE
                }
                Phase.THINKING -> Unit
                Phase.IDLE -> listen()
            }
        }

        LaunchedEffect(Unit) {
            model = runCatching { api.defaultModel(this@AskActivity) }.getOrNull()
        }
        LaunchedEffect(restart.intValue) {
            when {
                restart.intValue == 0 && autoPrompt != null -> send(autoPrompt, spoken = false)
                restart.intValue > 0 || !typing -> listen()
                else -> runCatching { focus.requestFocus() }
            }
        }
        val lastAnswerLength = turns.lastOrNull()?.answer?.length ?: 0
        LaunchedEffect(turns.size, lastAnswerLength) { scroll.animateScrollTo(scroll.maxValue) }

        val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.85f).dp

        Box(
            Modifier.fillMaxSize()
                .background(Color.Black.copy(alpha = 0.32f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { finish() },
        ) {
            Surface(
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                tonalElevation = 3.dp,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
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
                        Column(Modifier.weight(1f)) {
                            Text("Lena", style = MaterialTheme.typography.titleMedium)
                            model?.let {
                                Text(it.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        IconButton(onClick = {
                            if (typing) listen() else {
                                job?.takeIf { phase == Phase.LISTENING }?.cancel()
                                ear?.release()
                                ear = null
                                if (phase == Phase.LISTENING) phase = Phase.IDLE
                                typing = true
                            }
                        }) {
                            Icon(if (typing) Icons.Rounded.Mic else Icons.Outlined.Keyboard, if (typing) "Talk" else "Type")
                        }
                        IconButton(onClick = { forward(MainActivity.ACTION_CALL) }) { Icon(Icons.Rounded.GraphicEq, "Voice call") }
                        IconButton(onClick = {
                            val id = chatId
                            if (id != null) openChat(id) else {
                                startActivity(Intent(this@AskActivity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                finish()
                            }
                        }) { Icon(Icons.Outlined.OpenInFull, "Open Lena") }
                    }

                    // The conversation so far
                    if (turns.isNotEmpty()) {
                        Column(Modifier.weight(1f, fill = false).verticalScroll(scroll).padding(end = 8.dp)) {
                            turns.forEachIndexed { i, t ->
                                Spacer(Modifier.height(if (i == 0) 8.dp else 16.dp))
                                Text(t.question, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(8.dp))
                                val shown = Markdown.clean(t.answer)
                                if (!t.done && shown.isBlank()) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                        Text(
                                            if (t.answer.isBlank()) "Thinking…" else "Using tools…",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                } else if (shown.isNotBlank()) {
                                    Text(Markdown.render(t.answer, codeBg), style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                    }

                    note?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 8.dp))
                    }

                    // Continue / copy once there's an answer
                    val last = turns.lastOrNull()
                    if (last != null && last.done && phase == Phase.IDLE) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
                            chatId?.let { id -> TextButton(onClick = { openChat(id) }) { Text("Continue in Lena") } }
                            TextButton(onClick = {
                                getSystemService(ClipboardManager::class.java)
                                    .setPrimaryClip(ClipData.newPlainText("Lena", Markdown.plain(last.answer)))
                            }) {
                                Icon(Icons.Outlined.ContentCopy, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Copy")
                            }
                        }
                    }

                    if (typing) {
                        if (turns.isEmpty() && prompts.isNotEmpty()) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp, end = 8.dp)) {
                                prompts.forEach { p -> AssistChip(onClick = { send(p, spoken = false) }, label = { Text(p, maxLines = 1) }) }
                            }
                        }
                        OutlinedTextField(
                            value = text,
                            onValueChange = { text = it },
                            placeholder = { Text(if (turns.isEmpty()) "Ask anything" else "Reply") },
                            shape = RoundedCornerShape(28.dp),
                            maxLines = 5,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { if (phase == Phase.IDLE) send(text, spoken = false) }),
                            trailingIcon = {
                                IconButton(onClick = { send(text, spoken = false) }, enabled = text.isNotBlank() && phase == Phase.IDLE) {
                                    Icon(Icons.AutoMirrored.Rounded.Send, "Send")
                                }
                            },
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp, end = 8.dp).focusRequester(focus),
                        )
                    } else {
                        VoiceBar(phase, heard, level, onTap = { tapOrb() })
                    }
                }
            }
        }
    }

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
}
