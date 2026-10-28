@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.soullink9.lena

import android.Manifest
import android.app.StatusBarManager
import android.app.TimePickerDialog
import android.appwidget.AppWidgetManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon as PlatformIcon
import android.net.Uri
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddToHomeScreen
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Hearing
import androidx.compose.material.icons.outlined.NotificationImportant
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PhotoAlbum
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Slideshow
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.ViewSidebar
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/**
 * Lena's settings: a short list of categories, each opening its own page. Changes save as you
 * make them; addresses are saved once they're valid https:// addresses.
 */
class SettingsActivity : ComponentActivity() {

    private enum class Page(val title: String) {
        MAIN("Settings"), OPEN_WEBUI("Open WebUI"), HOME_ASSISTANT("Home Assistant"), MODEL("Model"),
        GESTURE("Assistant gesture"), VOICE("Voice"), CONVERSATION("Conversation"), QUICK("Quick prompts"),
        SCHEDULED("Scheduled prompts"), PHONE("Widget & tile"), APPEARANCE("Appearance"), DISPLAY("Display & screen saver"),
        DASHBOARD("Dashboard mode"), WAKE("Wake word"),
    }

    private lateinit var original: Settings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        original = Prefs.load(this)
        enableEdgeToEdge()
        setContent { LenaTheme { Screen() } }
    }

    /** Things that follow from the settings (alarms, shortcuts, the widget), once you leave. */
    override fun onPause() {
        super.onPause()
        WidgetMoodWorker.sync(this)
        Alerts.sync(this)
        Schedules.rescheduleAll(this)
        Shortcuts.update(this)
        val app = applicationContext
        lifecycleScope.launch { runCatching { LenaWidget().updateAll(app) } }
    }

    /** Leaving settings: if you changed which server Lena uses, start her again on it. */
    private fun leave() {
        val now = Prefs.load(this)
        if (now.server.isNotBlank() && (now.server != original.server || now.signIn != original.signIn)) {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        }
        finish()
    }

    @Composable
    private fun Screen() {
        var s by remember { mutableStateOf(original) }
        var page by remember { mutableStateOf(Page.MAIN) }
        var models by remember { mutableStateOf<List<Model>>(emptyList()) }
        val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

        LaunchedEffect(Unit) {
            Api.from(this@SettingsActivity)?.let { api ->
                models = runCatching { api.models() }.getOrDefault(emptyList())
                if (models.isNotEmpty()) Prefs.rememberLena(this@SettingsActivity, models)
            }
        }

        /** Saves a change straight away. Addresses only once they're valid; the page shows why not. */
        fun set(n: Settings) {
            s = n
            val saved = Prefs.load(this@SettingsActivity)
            Prefs.save(
                this@SettingsActivity,
                n.copy(
                    server = Prefs.normalize(n.server) ?: saved.server,
                    signIn = Prefs.normalizeSignIn(n.signIn) ?: saved.signIn,
                    haServer = if (n.haServer.isBlank()) "" else Prefs.normalize(n.haServer) ?: saved.haServer,
                ),
            )
            val wantsNotifications = n.notifyReplies || n.schedules.any { it.enabled }
            if (wantsNotifications && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        fun back() {
            if (page == Page.MAIN) leave() else page = Page.MAIN
        }
        BackHandler { back() }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.surface,
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text(page.title, style = MaterialTheme.typography.titleLarge) },
                    navigationIcon = { IconButton(onClick = { back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                )
            },
        ) { padding ->
            AnimatedContent(
                targetState = page,
                transitionSpec = {
                    val forward = targetState != Page.MAIN
                    (slideInHorizontally { w -> if (forward) w / 4 else -w / 4 } + fadeIn()) togetherWith
                        (slideOutHorizontally { w -> if (forward) -w / 4 else w / 4 } + fadeOut())
                },
                modifier = Modifier.padding(padding).fillMaxSize(),
                label = "page",
            ) { shown ->
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 24.dp).navigationBarsPadding(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    when (shown) {
                        Page.MAIN -> Main(s, models, ::set) { page = it }
                        Page.OPEN_WEBUI -> OpenWebUiPage(s, ::set)
                        Page.HOME_ASSISTANT -> HomeAssistantPage(s, ::set)
                        Page.MODEL -> ModelPage(s, models, ::set)
                        Page.GESTURE -> GesturePage(s, ::set)
                        Page.VOICE -> VoicePage(s, ::set)
                        Page.CONVERSATION -> ConversationPage(s, ::set)
                        Page.QUICK -> QuickPage(s, ::set)
                        Page.SCHEDULED -> ScheduledPage(s, ::set)
                        Page.PHONE -> PhonePage(s, ::set)
                        Page.APPEARANCE -> AppearancePage(s, ::set)
                        Page.DISPLAY -> DisplayPage(s, ::set) { page = it }
                        Page.DASHBOARD -> DashboardPage(s, ::set) { page = it }
                        Page.WAKE -> WakePage(s, ::set)
                    }
                }
            }
        }
    }

    // ---- The list ---------------------------------------------------------------------------

    @Composable
    private fun Main(s: Settings, models: List<Model>, set: (Settings) -> Unit, open: (Page) -> Unit) {
        val house = Prefs.haServer(this) != null && Secrets.has(this, Secrets.HA_TOKEN)
        Group {
            Item(Icons.Outlined.Language, "Open WebUI", host(s.server) ?: "Not set") { open(Page.OPEN_WEBUI) }
            Item(Icons.Outlined.Home, "Home Assistant", if (house) host(s.haServer) ?: "Connected" else "Not connected") {
                open(Page.HOME_ASSISTANT)
            }
        }
        Group {
            Item(Icons.Outlined.SmartToy, "Model", models.firstOrNull { it.id == s.model }?.name ?: s.model.ifBlank { "Lena, automatically" }) {
                open(Page.MODEL)
            }
            Item(Icons.Outlined.TouchApp, "Assistant gesture", gestureLabel(s.assist) + if (s.lockScreen) " · lock screen" else "") {
                open(Page.GESTURE)
            }
            Item(Icons.Outlined.RecordVoiceOver, "Voice", voiceLabel(s)) { open(Page.VOICE) }
            Item(Icons.Outlined.Palette, "Appearance", "${s.askStyle.label} · ${s.chatList.label.lowercase()}") { open(Page.APPEARANCE) }
            Item(Icons.Outlined.Forum, "Conversation",
                if (s.ongoing) "One ongoing conversation · %,d tokens".format(s.contextTokens) else "A new chat each time") {
                open(Page.CONVERSATION)
            }
        }
        Group {
            Item(Icons.Outlined.Bolt, "Quick prompts", count(s.quickPrompts.size, "prompt", "None yet")) { open(Page.QUICK) }
            Item(Icons.Outlined.Schedule, "Scheduled prompts",
                count(s.schedules.count { it.enabled }, "daily prompt", if (s.schedules.isEmpty()) "None yet" else "All paused")) {
                open(Page.SCHEDULED)
            }
        }
        Group {
            SwitchItem(Icons.Outlined.Notifications, "Reply notifications", "When she finishes a reply you left", s.notifyReplies) {
                set(s.copy(notifyReplies = it))
            }
            SwitchItem(Icons.Outlined.ViewSidebar, "Open WebUI's own menus", "Its header and sidebar, under Lena's", s.openWebUiMenus) {
                set(s.copy(openWebUiMenus = it))
            }
            Item(Icons.Outlined.Widgets, "Widget & tile", if (s.widgetMood) "Widget shows her mood" else null) { open(Page.PHONE) }
        }
        Group {
            Item(Icons.Outlined.Dashboard, "Dashboard mode", "Your Home Assistant dashboards, always on") { open(Page.DASHBOARD) }
            Item(Icons.Outlined.Tv, "Display & screen saver",
                if (s.slides.on && s.slides.albumName.isNotBlank()) "Slideshow: ${s.slides.albumName}" else "Clock, weather and her mood, full screen") { open(Page.DISPLAY) }
            Item(Icons.Outlined.Hearing, "Wake word", if (s.wakeWord) "Listens for \u201C${s.wakePhrase.label}\u201D" else "Off") { open(Page.WAKE) }
        }
        Group {
            val version = remember { runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = rowModifier().padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
                LenaAvatar(32.dp)
                Spacer(Modifier.width(18.dp))
                Column {
                    Text("Lena ${version ?: ""}", style = MaterialTheme.typography.titleMedium)
                    Text("Your own assistant, on your own hardware", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    // ---- Pages ------------------------------------------------------------------------------

    @Composable
    private fun OpenWebUiPage(s: Settings, set: (Settings) -> Unit) {
        Group {
            FieldItem {
                OutlinedTextField(
                    value = s.server, onValueChange = { set(s.copy(server = it)) },
                    label = { Text("Address") }, placeholder = { Text("https://ai.example.com") },
                    isError = s.server.isNotBlank() && Prefs.normalize(s.server) == null,
                    supportingText = { Text(if (s.server.isNotBlank() && Prefs.normalize(s.server) == null) "Use your https:// address" else "Where your Open WebUI is. HTTPS only.") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth(),
                )
            }
            FieldItem {
                val bad = Prefs.normalizeSignIn(s.signIn) == null
                OutlinedTextField(
                    value = s.signIn, onValueChange = { set(s.copy(signIn = it)) },
                    label = { Text("Sign-in address (optional)") }, placeholder = { Text("https://auth.example.com") },
                    isError = bad,
                    supportingText = { Text(if (bad) "Use https:// addresses" else "Only if you sign in through another site, such as Authentik. Separate several with spaces.") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Footnote("Changing these signs you in again when you leave settings.")
    }

    @Composable
    private fun HomeAssistantPage(s: Settings, set: (Settings) -> Unit) {
        val scope = rememberCoroutineScope()
        var token by remember { mutableStateOf("") }
        var tokenSaved by remember { mutableStateOf(Secrets.has(this, Secrets.HA_TOKEN)) }
        var check by remember { mutableStateOf<String?>(null) }
        Footnote("Optional. With the Lena integration in your Home Assistant, the app shows how she feels and lets you see and edit her memory.")
        Group {
            FieldItem {
                val bad = s.haServer.isNotBlank() && Prefs.normalize(s.haServer) == null
                OutlinedTextField(
                    value = s.haServer, onValueChange = { set(s.copy(haServer = it)); check = null },
                    label = { Text("Address") }, placeholder = { Text("https://ha.example.com") },
                    isError = bad, supportingText = if (bad) { { Text("Use your https:// address") } } else null,
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth(),
                )
            }
            FieldItem {
                OutlinedTextField(
                    value = token,
                    onValueChange = {
                        token = it
                        check = null
                        if (it.isNotBlank()) {
                            Secrets.put(this@SettingsActivity, Secrets.HA_TOKEN, it.trim())
                            tokenSaved = true
                        }
                    },
                    label = { Text(if (tokenSaved && token.isBlank()) "Access token (saved)" else "Long-lived access token") },
                    placeholder = { Text(if (tokenSaved) "Paste a new one to replace it" else "Your profile → Security") },
                    supportingText = { Text("Stored encrypted on this phone and sent only to your Home Assistant.") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        if (tokenSaved) IconButton(onClick = {
                            Secrets.put(this@SettingsActivity, Secrets.HA_TOKEN, null)
                            tokenSaved = false
                            token = ""
                            check = null
                        }) { Icon(Icons.Outlined.Delete, "Remove the saved token") }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Item(null, "Test connection", check ?: "Checks the address, the token and the Lena integration") {
                val url = Prefs.normalize(s.haServer)
                val saved = Secrets.get(this@SettingsActivity, Secrets.HA_TOKEN)
                if (url == null || saved == null) check = "Add the address and token first."
                else scope.launch {
                    check = "Checking…"
                    val house = HomeAssistant(url, saved)
                    check = if (!house.check()) "Couldn't connect. Check the address and token."
                    else when (val f = runCatching { house.feelings() }.getOrNull()) {
                        null -> "Connected, but the Lena integration isn't set up in Home Assistant."
                        else -> "Connected. She's ${f.mood.lowercase()}."
                    }
                }
            }
        }
        Group {
            SwitchItem(Icons.Outlined.NotificationImportant, "Alerts on this phone",
                "Checks every 15 minutes and tells you when she's worried about something at home", s.alerts) {
                set(s.copy(alerts = it))
                if (it && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 2)
                }
            }
        }
        Footnote("For instant alerts, written by her, pick this phone under Tell these phones when she's worried in the Lena integration's Configure (needs the Home Assistant companion app). Use this switch on phones without it.")
    }

    @Composable
    private fun ModelPage(s: Settings, models: List<Model>, set: (Settings) -> Unit) {
        Footnote("Used for new chats, voice calls, the sheet and scheduled prompts.")
        Group {
            ChoiceItem("Lena, automatically", "The model built from the Modelfile, or Open WebUI's default if there isn't one", s.model.isBlank()) {
                set(s.copy(model = ""))
            }
            models.forEach { m -> ChoiceItem(m.name, m.id.takeIf { it != m.name }, s.model == m.id) { set(s.copy(model = m.id)) } }
        }
        if (models.isEmpty()) {
            Group {
                FieldItem {
                    OutlinedTextField(
                        value = s.model, onValueChange = { set(s.copy(model = it)) },
                        label = { Text("Model ID") }, supportingText = { Text("Sign in to pick from your models, or type one, e.g. lena") },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }

    @Composable
    private fun GesturePage(s: Settings, set: (Settings) -> Unit) {
        Footnote("What happens when you use your phone's assistant gesture.")
        Group {
            AssistAction.entries.forEach { a ->
                ChoiceItem(gestureLabel(a), gestureDetail(a), s.assist == a) { set(s.copy(assist = a)) }
            }
        }
        Group {
            SwitchItem(Icons.Outlined.Lock, "On the lock screen", "Talk to her without unlocking", s.lockScreen) { set(s.copy(lockScreen = it)) }
        }
        Footnote(
            "On the lock screen she starts a fresh conversation with your history hidden, and nothing opens until you unlock. " +
                "She's asked not to unlock, open or share anything personal, but that's an instruction, not a lock: anyone holding " +
                "your phone can talk to her. Leave it off if she controls locks, doors or alarms."
        )
        Group {
            Item(Icons.AutoMirrored.Outlined.OpenInNew, "Make Lena your digital assistant", "Android settings → Default apps") {
                try {
                    startActivity(Intent(AndroidSettings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
                } catch (_: ActivityNotFoundException) {
                }
            }
        }
    }

    @Composable
    private fun VoicePage(s: Settings, set: (Settings) -> Unit) {
        val phoneEar = remember { PhoneEar.available(this) }
        Section("Hearing you") {
            ChoiceItem("On this phone",
                if (phoneEar) "Your words appear as you speak. The audio never leaves the phone." else "Not available on this phone; your server is used.",
                s.hearing == Hearing.PHONE) { set(s.copy(hearing = Hearing.PHONE)) }
            ChoiceItem("Your server", "Open WebUI's speech-to-text, the same as call mode", s.hearing == Hearing.SERVER) {
                set(s.copy(hearing = Hearing.SERVER))
            }
        }
        Section("Her voice") {
            ChoiceItem("Your server's voice", "Open WebUI's text-to-speech, the same as call mode", s.speaking == Speaking.SERVER) {
                set(s.copy(speaking = Speaking.SERVER))
            }
            ChoiceItem("The phone's voice", "Android's text-to-speech", s.speaking == Speaking.PHONE) { set(s.copy(speaking = Speaking.PHONE)) }
            ChoiceItem("Don't read replies aloud", "Show them only", s.speaking == Speaking.OFF) { set(s.copy(speaking = Speaking.OFF)) }
        }
        Group {
            SwitchItem(null, "Her voice follows her mood", "Slower when she's groggy, brighter when she's cheerful. Needs Home Assistant.", s.moodVoice) {
                set(s.copy(moodVoice = it))
            }
        }
        Section("Keep listening") {
            ChoiceItem("After every reply", "Talk back without tapping. Stays quiet if you say nothing.", s.followUp == FollowUp.ALWAYS) {
                set(s.copy(followUp = FollowUp.ALWAYS))
            }
            ChoiceItem("When she asks a question", "Like Home Assistant's \"continue conversation\"", s.followUp == FollowUp.QUESTIONS) {
                set(s.copy(followUp = FollowUp.QUESTIONS))
            }
            ChoiceItem("Off", "Tap the mic to talk again", s.followUp == FollowUp.OFF) { set(s.copy(followUp = FollowUp.OFF)) }
        }
    }

    @Composable
    private fun ConversationPage(s: Settings, set: (Settings) -> Unit) {
        Group {
            SwitchItem(null, "One ongoing conversation", "Talking to Lena continues the same chat, so she remembers earlier conversations", s.ongoing) {
                set(s.copy(ongoing = it))
            }
            if (s.ongoing) FieldItem {
                var text by remember { mutableStateOf(s.contextTokens.toString()) }
                OutlinedTextField(
                    value = text,
                    onValueChange = { v ->
                        text = v.filter { it.isDigit() }.take(7)
                        text.toIntOrNull()?.let { set(s.copy(contextTokens = it)) }
                    },
                    label = { Text("Her context size (tokens)") },
                    supportingText = { Text("Half holds the conversation; the rest is for her prompt, tools and reply. Match num_ctx in the Modelfile.") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Footnote("Start over any time with New conversation in the sheet. The old chat stays in your history.")
    }

    @Composable
    private fun QuickPage(s: Settings, set: (Settings) -> Unit) {
        var newPrompt by remember { mutableStateOf("") }
        Footnote("One tap from the sheet, the widget and the app icon's long-press menu. Up to six.")
        if (s.quickPrompts.isNotEmpty()) Group {
            s.quickPrompts.forEachIndexed { i, p ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = rowModifier().padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 4.dp)) {
                    Text(p, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    IconButton(onClick = { set(s.copy(quickPrompts = s.quickPrompts.filterIndexed { j, _ -> j != i })) }) {
                        Icon(Icons.Outlined.Delete, "Remove")
                    }
                }
            }
        }
        if (s.quickPrompts.size < 6) Group {
            FieldItem {
                OutlinedTextField(
                    value = newPrompt, onValueChange = { newPrompt = it },
                    placeholder = { Text("e.g. What's my day look like?") }, singleLine = true,
                    trailingIcon = {
                        IconButton(enabled = newPrompt.isNotBlank(), onClick = {
                            set(s.copy(quickPrompts = s.quickPrompts + newPrompt.trim()))
                            newPrompt = ""
                        }) { Icon(Icons.Outlined.Add, "Add") }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    @Composable
    private fun ScheduledPage(s: Settings, set: (Settings) -> Unit) {
        Footnote("Lena asks these on her own every day and notifies you with the answer. They're saved as chats.")
        s.schedules.forEachIndexed { i, sch ->
            Group {
                Column(rowModifier().padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = {
                            TimePickerDialog(
                                this@SettingsActivity,
                                { _, h, m -> set(s.copy(schedules = s.schedules.mapIndexed { j, o -> if (j == i) o.copy(hour = h, minute = m) else o })) },
                                sch.hour, sch.minute, DateFormat.is24HourFormat(this@SettingsActivity),
                            ).show()
                        }) { Text(timeLabel(sch.hour, sch.minute), style = MaterialTheme.typography.headlineSmall) }
                        Text("every day", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Switch(checked = sch.enabled, onCheckedChange = { on ->
                            set(s.copy(schedules = s.schedules.mapIndexed { j, o -> if (j == i) o.copy(enabled = on) else o }))
                        })
                        IconButton(onClick = { set(s.copy(schedules = s.schedules.filterIndexed { j, _ -> j != i })) }) {
                            Icon(Icons.Outlined.Delete, "Remove")
                        }
                    }
                    OutlinedTextField(
                        value = sch.prompt,
                        onValueChange = { t -> set(s.copy(schedules = s.schedules.mapIndexed { j, o -> if (j == i) o.copy(prompt = t) else o })) },
                        label = { Text("Prompt") },
                        modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                    )
                }
            }
        }
        Group {
            Item(Icons.Outlined.Add, "Add a scheduled prompt", null) {
                set(s.copy(schedules = s.schedules + Schedule(prompt = "Give me my morning briefing.")))
            }
        }
    }

    @Composable
    private fun PhonePage(s: Settings, set: (Settings) -> Unit) {
        Group {
            SwitchItem(null, "Her mood on the widget", "Updated every half hour. Only the mood, never the reasons. Needs Home Assistant.", s.widgetMood) {
                set(s.copy(widgetMood = it))
            }
        }
        Group {
            Item(Icons.Outlined.AddToHomeScreen, "Add the widget", "Talk, call, type, new chat and your quick prompts") { addWidget() }
            Item(Icons.Outlined.TouchApp, "Add the Quick Settings tile", "Start a voice call from the notification shade") { addTile() }
        }
    }

    @Composable
    private fun DisplayPage(s: Settings, set: (Settings) -> Unit, open: (Page) -> Unit) {
        Footnote("A clock, the weather, how she feels and the last thing she said, on black. For a docked phone or a bedside table. Tap anywhere to talk; back to leave.")
        Group {
            Item(Icons.Outlined.PlayArrow, "Start display mode", null) {
                startActivity(Intent(this@SettingsActivity, DisplayActivity::class.java))
            }
            Item(Icons.AutoMirrored.Outlined.OpenInNew, "Use it as the screen saver", "Android settings → Screen saver → Lena. It shows while the phone charges.") {
                try {
                    startActivity(Intent(AndroidSettings.ACTION_DREAM_SETTINGS))
                } catch (_: ActivityNotFoundException) {
                }
            }
            Item(Icons.Outlined.Hearing, "Wake word", if (s.wakeWord) "Listens for \u201C${s.wakePhrase.label}\u201D" else "Off") { open(Page.WAKE) }
        }
        SlideshowSettings(s, set, open)
    }

    /** The photo slideshow: shown by the screen saver, display mode and dashboard mode when it dims. */
    @Composable
    private fun SlideshowSettings(s: Settings, set: (Settings) -> Unit, open: (Page) -> Unit) {
        val sl = s.slides
        fun put(n: Slides) = set(s.copy(slides = n))
        var picking by remember { mutableStateOf(false) }
        val house = Prefs.haServer(this) != null && Secrets.has(this, Secrets.HA_TOKEN)
        SectionTitle("Photo slideshow")
        Footnote("Photos from Home Assistant's media, such as an Immich album, instead of the clock: in the screen saver, display mode, and dashboard mode when it dims. Needs the Lena integration 1.2.0 or newer.")
        if (!house) {
            Group { Item(Icons.Outlined.Home, "Connect Home Assistant first", null) { open(Page.HOME_ASSISTANT) } }
            return
        }
        Group {
            SwitchItem(Icons.Outlined.Slideshow, "Show photos", null, sl.on) { on ->
                put(sl.copy(on = on))
                if (on && sl.album.isBlank()) picking = true
            }
            Item(Icons.Outlined.PhotoAlbum, "Album", sl.albumName.ifBlank { "Choose one" }) { picking = true }
        }
        if (sl.on) {
            Section("Each photo for") {
                listOf(5 to "5 seconds", 10 to "10 seconds", 15 to "15 seconds", 30 to "30 seconds", 60 to "1 minute", 300 to "5 minutes").forEach { (sec, label) ->
                    ChoiceItem(label, null, sl.seconds == sec) { put(sl.copy(seconds = sec)) }
                }
            }
            Section("Transition") {
                SlideTransition.entries.forEach { t -> ChoiceItem(t.label, null, sl.transition == t) { put(sl.copy(transition = t)) } }
            }
            Group {
                SwitchItem(null, "Shuffle", "Otherwise in the album's order", sl.shuffle) { put(sl.copy(shuffle = it)) }
                SwitchItem(null, "Fill the screen", "Crops the edges. Off shows the whole photo on a blurred copy of itself.", sl.fill) { put(sl.copy(fill = it)) }
                SwitchItem(null, "Time and her mood over the photos", null, sl.overlay) { put(sl.copy(overlay = it)) }
                SwitchItem(null, "Just the clock at night", "10 PM to 6 AM, and when she's drained, so it doesn't light up a room", sl.clockAtNight) {
                    put(sl.copy(clockAtNight = it))
                }
            }
        }
        if (picking) AlbumPicker(
            onPick = { id, name ->
                picking = false
                put(sl.copy(album = id, albumName = name, on = true))
            },
            onDismiss = { picking = false },
        )
    }

    @Composable
    private fun DashboardPage(s: Settings, set: (Settings) -> Unit, open: (Page) -> Unit) {
        val house = Prefs.haServer(this) != null && Secrets.has(this, Secrets.HA_TOKEN)
        Footnote("Your Home Assistant dashboards, always on, with Lena across the top: the time, the weather, how she feels and a tap to talk. " +
            "For a tablet on the wall. After a while without a touch it dims to her night clock.")
        if (!house) {
            Group { Item(Icons.Outlined.Home, "Connect Home Assistant first", "Its address and a token") { open(Page.HOME_ASSISTANT) } }
            return
        }
        Group {
            Item(Icons.Outlined.PlayArrow, "Start dashboard mode", null) {
                startActivity(Intent(this@SettingsActivity, DashboardActivity::class.java))
            }
            Item(Icons.Outlined.Home, "Make it this tablet's home screen", "Android settings → Default apps → Home app → Lena") {
                try {
                    startActivity(Intent(AndroidSettings.ACTION_HOME_SETTINGS))
                } catch (_: ActivityNotFoundException) {
                    startActivity(Intent(AndroidSettings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
                }
            }
            Item(Icons.Outlined.Hearing, "Wake word", if (s.wakeWord) "Listens for \u201C${s.wakePhrase.label}\u201D" else "Off") { open(Page.WAKE) }
        }

        SectionTitle("Dashboards")
        Footnote("Each one is a tab. The path is what comes after your Home Assistant's address, like lovelace or dashboard-floorplan/0.")
        Group {
            s.dashboards.forEachIndexed { i, d ->
                FieldItem {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = d.name, onValueChange = { v -> set(s.copy(dashboards = s.dashboards.mapIndexed { j, o -> if (j == i) o.copy(name = v) else o })) },
                            label = { Text("Name") }, singleLine = true, modifier = Modifier.weight(0.4f),
                        )
                        OutlinedTextField(
                            value = d.path, onValueChange = { v -> set(s.copy(dashboards = s.dashboards.mapIndexed { j, o -> if (j == i) o.copy(path = v.trim()) else o })) },
                            label = { Text("Path") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            modifier = Modifier.weight(0.6f),
                        )
                        if (s.dashboards.size > 1) IconButton(onClick = { set(s.copy(dashboards = s.dashboards.filterIndexed { j, _ -> j != i })) }) {
                            Icon(Icons.Outlined.Delete, "Remove")
                        }
                    }
                }
            }
            Item(Icons.Outlined.Add, "Add a dashboard", null) { set(s.copy(dashboards = s.dashboards + Dashboard("", ""))) }
        }

        Section("Dim to the clock after") {
            listOf(2, 5, 10, 30, 0).forEach { m ->
                ChoiceItem(if (m == 0) "Never" else "$m minutes without a touch", null, s.dimMinutes == m) { set(s.copy(dimMinutes = m)) }
            }
        }
        Group {
            FieldItem {
                OutlinedTextField(
                    value = s.motionEntity, onValueChange = { set(s.copy(motionEntity = it)) },
                    label = { Text("Wake when this sensor sees someone (optional)") },
                    placeholder = { Text("binary_sensor.hallway_motion") },
                    supportingText = { Text("A motion or presence sensor near the tablet. Checked every few seconds while it's dimmed.") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Footnote("She also lights it up when she's worried about something at home, with what's wrong across the top.")
    }

    @Composable
    private fun WakePage(s: Settings, set: (Settings) -> Unit) {
        Footnote("Talk to her across the room in display mode, dashboard mode and the screen saver. It listens on this device only; nothing is recorded or sent until she hears her name.")
        Group {
            SwitchItem(Icons.Outlined.Hearing, "Listen for \u201C${s.wakePhrase.label}\u201D", null, s.wakeWord) {
                set(s.copy(wakeWord = it))
                if (it && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 3)
                }
            }
            WakeTest(s.wakePhrase)
        }
        Section("Her name") {
            WakePhrase.entries.forEach { w ->
                val there = remember(w) { WakeWord.available(this@SettingsActivity, w) }
                ChoiceItem(w.label, if (there) w.detail else "Not in this build", s.wakePhrase == w) { if (there) set(s.copy(wakePhrase = w)) }
            }
        }
        Footnote("Some phones don't let an app use the microphone from the screen saver; it then says so and you tap to talk.")
    }

    /** Listens for the wake word right here and shows how close each try gets. */
    @Composable
    private fun WakeTest(phrase: WakePhrase) {
        var on by remember { mutableStateOf(false) }
        var level by remember { mutableStateOf(0f) }
        var heard by remember { mutableStateOf(0) }
        val listener = remember {
            WakeListener(this, onHeard = { heard++; on = false }, onScore = { level = it })
        }
        DisposableEffect(Unit) { onDispose { listener.stop() } }
        Item(
            Icons.Outlined.GraphicEq,
            if (on) "Listening… say \u201C${phrase.label}\u201D" else "Try it",
            when {
                on -> "Tap to stop"
                heard > 0 -> "Heard you. Tap to try again."
                else -> "Checks the wake word hears you from where you'll be"
            },
            trailing = if (on) { { LinearProgressIndicator(progress = { level.coerceIn(0f, 1f) }, modifier = Modifier.width(72.dp).padding(end = 12.dp)) } } else null,
        ) {
            if (on) {
                listener.stop()
                on = false
            } else if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 3)
            } else {
                level = 0f
                on = listener.start()
            }
        }
    }

    @Composable
    private fun AppearancePage(s: Settings, set: (Settings) -> Unit) {
        Section("Talking to Lena") {
            AskStyle.entries.forEach { a -> ChoiceItem(a.label, a.detail, s.askStyle == a) { set(s.copy(askStyle = a)) } }
        }
        Group {
            Item(Icons.Outlined.PlayArrow, "Try it", "Opens Lena the way your assistant gesture does") {
                startActivity(AskActivity.intent(this@SettingsActivity, talk = s.assist != AssistAction.TYPE))
            }
        }
        Footnote("Every look listens, answers and speaks the same way. The sheet is the only one that shows earlier turns; the others show what's happening now.")
        Section("Chat list") {
            ChatListStyle.entries.forEach { c -> ChoiceItem(c.label, c.detail, s.chatList == c) { set(s.copy(chatList = c)) } }
        }
    }

    // ---- Words for the list -----------------------------------------------------------------

    private fun host(address: String): String? = Uri.parse(address.trim()).host?.ifBlank { null }

    private fun count(n: Int, noun: String, none: String) = if (n == 0) none else "$n $noun${if (n == 1) "" else "s"}"

    private fun gestureLabel(a: AssistAction) = when (a) {
        AssistAction.ASK -> "Talk to Lena"
        AssistAction.TYPE -> "Type to Lena"
        AssistAction.CALL -> "Voice call"
        AssistAction.CHAT -> "New chat"
    }

    private fun gestureDetail(a: AssistAction) = when (a) {
        AssistAction.ASK -> "Listens straight away, over whatever you're doing"
        AssistAction.TYPE -> "The same sheet, with the keyboard"
        AssistAction.CALL -> "Straight into a voice call"
        AssistAction.CHAT -> "Opens Lena on a new chat"
    }

    private fun voiceLabel(s: Settings): String {
        val ear = if (s.hearing == Hearing.PHONE) "Phone listens" else "Server listens"
        val mouth = when (s.speaking) {
            Speaking.SERVER -> "server voice"
            Speaking.PHONE -> "phone voice"
            Speaking.OFF -> "replies not read aloud"
        }
        return "$ear · $mouth" + if (s.moodVoice) " · follows her mood" else ""
    }

    private fun timeLabel(h: Int, m: Int): String =
        if (DateFormat.is24HourFormat(this)) "%02d:%02d".format(h, m)
        else "%d:%02d %s".format(if (h % 12 == 0) 12 else h % 12, m, if (h < 12) "AM" else "PM")

    private fun addTile() {
        getSystemService(StatusBarManager::class.java).requestAddTileService(
            ComponentName(this, LenaTile::class.java),
            getString(R.string.tile_label),
            PlatformIcon.createWithResource(this, R.drawable.ic_glyph_call),
            mainExecutor,
        ) { }
    }

    private fun addWidget() {
        val manager = getSystemService(AppWidgetManager::class.java)
        if (manager.isRequestPinAppWidgetSupported) {
            manager.requestPinAppWidget(ComponentName(this, LenaWidgetReceiver::class.java), null, null)
        }
    }
}
