package io.github.soullink9.lena

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.outlined.AddComment
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Lena's own chat, when Home Assistant's Assist answers instead of Open WebUI (Settings → Connections →
 * Who answers), for people who don't use Open WebUI at all. One conversation kept on this device
 * ([AssistLog]): what you type here, what you say in the sheet, quick prompts and scheduled prompts, with
 * her full answers. The chat screen (MainActivity) hands over to this one in Assist mode.
 *
 * It behaves like the chat screen around it: the Chat | Home switch, the wall display's screensaver and
 * Back from your chats after ([WallDisplay]), and listening for her name when that's set to whenever
 * Lena is open.
 */
class AssistChatActivity : ComponentActivity() {

    companion object {
        /** The chat screen to open: this one when Assist answers, otherwise Open WebUI's. */
        fun intent(c: Context): Intent = Intent(
            c,
            if (Prefs.answers(c) == Answerer.ASSIST) AssistChatActivity::class.java else MainActivity::class.java,
        )
    }

    private var wake: WakeListener? = null
    private val wallDisplay = mutableStateOf(false)
    private val hasHome = mutableStateOf(false)
    private val showApps = mutableStateOf(false)

    /** Every touch or key: someone's using the wall display, so its timers start again (see [WallDisplay]). */
    override fun onUserInteraction() {
        super.onUserInteraction()
        WallDisplay.touched()
    }

    /** Without Assist answering there's nothing for this screen to do: Open WebUI's chat screen instead. */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Prefs.answers(this) != Answerer.ASSIST) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }
        WallDisplay.watch(this, backToDashboard = true)
        enableEdgeToEdge()
        setContent {
            LenaTheme {
                Box(Modifier.fillMaxSize()) {
                    Screen()
                    if (showApps.value) AppsDrawer(onClose = { showApps.value = false })
                }
            }
        }
    }

    /** Back on screen: the switch, the wall display's Apps and her name follow the settings. */
    override fun onResume() {
        super.onResume()
        // Switched back to Open WebUI in Settings meanwhile: its chat screen takes over.
        if (Prefs.answers(this) != Answerer.ASSIST) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }
        hasHome.value = HomeAssistant.from(this) != null && Prefs.homeSwitch(this)
        wallDisplay.value = DashboardActivity.isHomeScreen(this)
        if (Prefs.wakeWord(this) && Prefs.wakeEverywhere(this)) {
            val listener = wake ?: WakeListener(this, onHeard = {
                wake?.stop()
                startActivity(AskActivity.intent(this, talk = true))
            }).also { wake = it }
            listener.start()
        } else {
            wake?.stop()
        }
    }

    /** The microphone is let go while another screen shows. */
    override fun onPause() {
        wake?.stop()
        super.onPause()
    }

    /** Home: the dashboards. In dashboard mode this screen closes, the way the chat screen does. */
    private fun goHome() {
        startActivity(Intent(this, DashboardActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        if (DashboardActivity.isHomeScreen(this)) finish()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun Screen() {
        val scope = rememberCoroutineScope()
        val drawer = rememberDrawerState(DrawerValue.Closed)
        val changed by AssistLog.changed.collectAsState()
        var turns by remember { mutableStateOf<List<AssistTurn>>(emptyList()) }
        var text by remember { mutableStateOf("") }
        var waiting by remember { mutableStateOf<String?>(null) } // a typed question still being answered
        val list = rememberLazyListState()

        LaunchedEffect(changed) { turns = AssistLog.all(this@AssistChatActivity) }
        // The newest exchange in view whenever one is added (or one is being answered).
        LaunchedEffect(turns.size, waiting) {
            val count = turns.size + if (waiting != null) 1 else 0
            if (count > 0) list.animateScrollToItem(count - 1)
        }

        /**
         * Sends what you typed to Assist; the answer shows when it comes. Timers and alarms are answered here
         * by Lena herself ([Commands]).
         */
        fun send() {
            val question = text.trim()
            if (question.isEmpty() || waiting != null) return
            text = ""
            waiting = question
            scope.launch {
                try {
                    val own = Commands.handle(this@AssistChatActivity, question)
                    if (own != null) AssistLog.add(this@AssistChatActivity, question, own)
                    else AssistChat.ask(this@AssistChatActivity, question)
                } catch (e: HaAuthException) {
                    toast("Home Assistant didn't accept Lena's token. Check Settings → Connections → Home Assistant.")
                } catch (e: IllegalStateException) {
                    toast(e.message ?: "Set up Home Assistant first.")
                } catch (e: Exception) {
                    Logs.w("Assist chat", "Couldn't ask Assist", e)
                    toast("Couldn't reach Home Assistant.")
                    text = question // give it back to try again
                }
                waiting = null
            }
        }

        ModalNavigationDrawer(
            drawerState = drawer,
            drawerContent = {
                ModalDrawerSheet(Modifier.widthIn(max = 320.dp)) {
                    Column(Modifier.fillMaxHeight().navigationBarsPadding()) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(20.dp)) {
                            LenaAvatar(40.dp)
                            Column(Modifier.padding(start = 14.dp)) {
                                Text("Lena", style = MaterialTheme.typography.titleLarge)
                                Text("Answering through Home Assistant Assist", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth().padding(16.dp, 8.dp)) {
                            DrawerIcon(Icons.Outlined.Settings, "Settings") {
                                scope.launch { drawer.close() }
                                startActivity(Intent(this@AssistChatActivity, SettingsActivity::class.java))
                            }
                            DrawerIcon(Icons.Outlined.Psychology, "Her memory") {
                                scope.launch { drawer.close() }
                                startActivity(Intent(this@AssistChatActivity, MemoryActivity::class.java))
                            }
                            if (wallDisplay.value) {
                                DrawerIcon(Icons.Outlined.Apps, "Apps") {
                                    scope.launch { drawer.close() }
                                    showApps.value = true
                                }
                            }
                        }
                    }
                }
            },
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        navigationIcon = { IconButton(onClick = { scope.launch { drawer.open() } }) { Icon(Icons.Rounded.Menu, "Menu") } },
                        title = {
                            if (hasHome.value) ScreenSwitcher(MainScreen.CHAT, { if (it == MainScreen.HOME) goHome() })
                            else Text("Lena")
                        },
                        actions = {
                            IconButton(onClick = { scope.launch { AssistChat.newConversation(this@AssistChatActivity) } }) {
                                Icon(Icons.Outlined.AddComment, "New conversation")
                            }
                        },
                    )
                },
                bottomBar = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding()
                            .wrapContentWidth().widthIn(max = PAGE_MAX_WIDTH).padding(12.dp),
                    ) {
                        OutlinedTextField(
                            value = text, onValueChange = { text = it },
                            placeholder = { Text("Message Lena") }, maxLines = 5,
                            shape = RoundedCornerShape(24.dp), modifier = Modifier.weight(1f),
                        )
                        if (text.isBlank()) {
                            IconButton(onClick = { startActivity(AskActivity.intent(this@AssistChatActivity, talk = true)) }) {
                                Icon(Icons.Rounded.Mic, "Talk to Lena", tint = MaterialTheme.colorScheme.primary)
                            }
                        } else {
                            IconButton(onClick = { send() }, enabled = waiting == null) {
                                Icon(Icons.AutoMirrored.Rounded.Send, "Send", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                },
            ) { padding ->
                if (turns.isEmpty() && waiting == null) {
                    Box(Modifier.padding(padding).fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("Ask Lena anything. She answers through Home Assistant's Assist, and your conversation is kept on this device.",
                            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    return@Scaffold
                }
                LazyColumn(
                    state = list,
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.padding(padding).fillMaxSize().wrapContentWidth().widthIn(max = PAGE_MAX_WIDTH),
                ) {
                    items(turns) { turn -> Exchange(turn) }
                    waiting?.let { question ->
                        item { Exchange(AssistTurn(System.currentTimeMillis(), question, "…")) }
                    }
                }
            }
        }
    }

    /** One exchange: a label for where it came from, your words on the right, her answer on the left. */
    @Composable
    private fun Exchange(turn: AssistTurn) {
        if (turn.divider) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                HorizontalDivider(Modifier.weight(1f))
                Text("New conversation · ${whenText(turn.time)}", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 10.dp))
                HorizontalDivider(Modifier.weight(1f))
            }
            return
        }
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(listOfNotNull(turn.label, whenText(turn.time)).joinToString(" · "), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterHorizontally))
            Text(
                turn.question,
                style = MaterialTheme.typography.bodyLarge,
                fontStyle = if (turn.label != null) FontStyle.Italic else FontStyle.Normal,
                modifier = Modifier.align(Alignment.End).widthIn(max = 520.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(18.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )
            Text(Markdown.plain(turn.answer), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 4.dp))
        }
    }

    /** "3:05 PM", or "Yesterday, 3:05 PM" and so on for older ones. */
    private fun whenText(time: Long): String =
        DateUtils.getRelativeDateTimeString(this, time, DateUtils.DAY_IN_MILLIS, DateUtils.WEEK_IN_MILLIS, DateUtils.FORMAT_SHOW_TIME).toString()

    @Composable
    private fun DrawerIcon(icon: ImageVector, label: String, onClick: () -> Unit) {
        FilledTonalIconButton(onClick = onClick, modifier = Modifier.size(48.dp)) { Icon(icon, label) }
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()
}
