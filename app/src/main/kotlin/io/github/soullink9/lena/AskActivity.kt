@file:OptIn(ExperimentalLayoutApi::class)

package io.github.soullink9.lena

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The Ask Lena sheet: a quick question over whatever app you're in. The answer is a real
 * Open WebUI chat, written on your server with your model's tools, so "Continue in Lena"
 * just opens it.
 */
class AskActivity : ComponentActivity() {

    companion object {
        const val ACTION_ASK = "io.github.soullink9.lena.ASK"
        private const val EXTRA_PROMPT = "prompt"
        private const val EXTRA_KEY = "key"

        /** Opens the sheet; with [prompt], sends it straight away. Only Lena's own widget and shortcuts use this. */
        fun intent(c: Context, prompt: String? = null): Intent = Intent(c, AskActivity::class.java)
            .setAction(ACTION_ASK)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .apply { if (prompt != null) putExtra(EXTRA_PROMPT, prompt).putExtra(EXTRA_KEY, Prefs.launchKey(c)) }
    }

    private enum class Phase { IDLE, WORKING, DONE, ERROR }

    private var running: Api.Started? = null
    private var working = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val server = Prefs.server(this)
        if (server == null) {
            startActivity(Intent(this, SettingsActivity::class.java))
            finish()
            return
        }

        // The assistant gesture can open a call or a chat instead of the sheet.
        if (intent.action == Intent.ACTION_ASSIST) {
            when (Prefs.assistAction(this)) {
                AssistAction.CALL -> return forward(MainActivity.ACTION_CALL)
                AssistAction.CHAT -> return forward(MainActivity.ACTION_NEW_CHAT)
                AssistAction.ASK -> Unit
            }
        }

        // Shared text only fills the box. A prompt is sent automatically only with Lena's own key.
        val shared = if (intent.action == Intent.ACTION_SEND) {
            listOfNotNull(intent.getStringExtra(Intent.EXTRA_SUBJECT), intent.getStringExtra(Intent.EXTRA_TEXT))
                .distinct().joinToString("\n").trim()
        } else ""
        val auto = intent.getStringExtra(EXTRA_PROMPT)
            ?.takeIf { intent.getStringExtra(EXTRA_KEY) == Prefs.launchKey(this) }

        enableEdgeToEdge()
        setContent { LenaTheme { Sheet(Api(server), shared, auto) } }
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

    @Composable
    private fun Sheet(api: Api, initial: String, autoPrompt: String?) {
        val scope = rememberCoroutineScope()
        var text by remember { mutableStateOf(initial) }
        var question by remember { mutableStateOf<String?>(null) }
        var answer by remember { mutableStateOf("") }
        var phase by remember { mutableStateOf(Phase.IDLE) }
        var error by remember { mutableStateOf("") }
        var model by remember { mutableStateOf<Model?>(null) }
        var chatId by remember { mutableStateOf<String?>(null) }
        var job by remember { mutableStateOf<Job?>(null) }
        val prompts = remember { Prefs.quickPrompts(this) }
        val focus = remember { FocusRequester() }
        val codeBg = MaterialTheme.colorScheme.surfaceVariant

        fun send(q: String) {
            if (q.isBlank()) return
            question = q.trim()
            text = ""
            answer = ""
            phase = Phase.WORKING
            job?.cancel()
            job = scope.launch {
                try {
                    val m = model ?: api.defaultModel(this@AskActivity).also { model = it }
                        ?: throw IllegalStateException("No models on your server")
                    val started = api.ask(q.trim(), m)
                    running = started
                    working = true
                    chatId = started.chatId
                    api.follow(started.chatId, started.messageId).collect { r ->
                        answer = r.content
                        if (r.done) phase = Phase.DONE
                    }
                    phase = Phase.DONE
                } catch (_: AuthException) {
                    error = "Open Lena and sign in first."
                    phase = Phase.ERROR
                } catch (e: IllegalStateException) {
                    error = e.message ?: "Something went wrong."
                    phase = Phase.ERROR
                } catch (_: Exception) {
                    error = "Couldn't reach your server."
                    phase = Phase.ERROR
                } finally {
                    working = false
                }
            }
        }

        LaunchedEffect(Unit) {
            model = runCatching { api.defaultModel(this@AskActivity) }.getOrNull()
            if (autoPrompt != null) send(autoPrompt) else runCatching { focus.requestFocus() }
        }

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
                    // Drag handle
                    Box(
                        Modifier.align(Alignment.CenterHorizontally).padding(vertical = 6.dp)
                            .size(width = 32.dp, height = 4.dp)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(2.dp)),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Ask Lena", style = MaterialTheme.typography.titleMedium)
                            model?.let {
                                Text(it.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        IconButton(onClick = { forward(MainActivity.ACTION_CALL) }) { Icon(Icons.Rounded.GraphicEq, "Voice call") }
                        IconButton(onClick = {
                            val id = chatId
                            if (id != null) openChat(id)
                            else {
                                startActivity(Intent(this@AskActivity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                finish()
                            }
                        }) { Icon(Icons.Outlined.OpenInFull, "Open Lena") }
                    }

                    if (question != null) {
                        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(end = 8.dp)) {
                            Spacer(Modifier.height(8.dp))
                            Text(question!!, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(12.dp))
                            val shown = Markdown.clean(answer)
                            if (phase == Phase.WORKING && shown.isBlank()) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                    Text(
                                        if (answer.isBlank()) "Thinking…" else "Using tools…",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            } else if (shown.isNotBlank()) {
                                Text(Markdown.render(answer, codeBg), style = MaterialTheme.typography.bodyLarge)
                            }
                            if (phase == Phase.ERROR) {
                                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        if (phase == Phase.DONE || phase == Phase.ERROR) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                                chatId?.let { id -> FilledTonalButton(onClick = { openChat(id) }) { Text("Continue in Lena") } }
                                if (phase == Phase.DONE) TextButton(onClick = {
                                    getSystemService(ClipboardManager::class.java)
                                        .setPrimaryClip(ClipData.newPlainText("Lena", Markdown.plain(answer)))
                                }) {
                                    Icon(Icons.Outlined.ContentCopy, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Copy")
                                }
                            }
                        }
                    } else if (prompts.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 8.dp, end = 8.dp),
                        ) {
                            prompts.forEach { p ->
                                AssistChip(onClick = { send(p) }, label = { Text(p, maxLines = 1) })
                            }
                        }
                    }

                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        placeholder = { Text(if (question == null) "Ask anything" else "Ask something else") },
                        shape = RoundedCornerShape(28.dp),
                        maxLines = 5,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { if (phase != Phase.WORKING) send(text) }),
                        trailingIcon = {
                            IconButton(onClick = { send(text) }, enabled = text.isNotBlank() && phase != Phase.WORKING) {
                                Icon(Icons.AutoMirrored.Rounded.Send, "Send")
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, end = 8.dp).focusRequester(focus),
                    )
                }
            }
        }
    }
}
