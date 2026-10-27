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
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.ViewSidebar
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
        SCHEDULED("Scheduled prompts"), PHONE("Widget & tile"),
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
            val version = remember { runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = rowModifier.padding(horizontal = 20.dp, vertical = 16.dp),
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
        Footnote("Optional. Where her feelings and shared memory live, so Lena can show how she feels and let you edit her memory.")
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
            FieldItem {
                OutlinedTextField(
                    value = s.haMemoryList, onValueChange = { set(s.copy(haMemoryList = it)) },
                    label = { Text("Her memory list") }, supportingText = { Text("The to-do list entity, e.g. $DEFAULT_MEMORY_LIST") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
            }
            Item(null, "Test connection", check ?: "Checks the address, the token and her feelings helpers") {
                val url = Prefs.normalize(s.haServer)
                val saved = Secrets.get(this@SettingsActivity, Secrets.HA_TOKEN)
                if (url == null || saved == null) check = "Add the address and token first."
                else scope.launch {
                    check = "Checking…"
                    val house = HomeAssistant(url, saved)
                    check = if (!house.check()) "Couldn't connect. Check the address and token."
                    else when (val f = runCatching { house.feelings() }.getOrNull()) {
                        null -> "Connected. Her feelings helpers weren't found."
                        else -> "Connected. She's ${f.mood.lowercase()}."
                    }
                }
            }
        }
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
                Row(verticalAlignment = Alignment.CenterVertically, modifier = rowModifier.padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 4.dp)) {
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
                Column(rowModifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
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

    // ---- Building blocks: rows in rounded groups, like a phone's own settings ---------------

    private val rowModifier: Modifier
        @Composable get() = Modifier.fillMaxWidth().heightIn(min = 64.dp).background(MaterialTheme.colorScheme.surfaceContainer)

    /** Rows that belong together: rounded as a whole, with a thin gap between rows. */
    @Composable
    private fun Group(content: @Composable ColumnScope.() -> Unit) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)), verticalArrangement = Arrangement.spacedBy(2.dp), content = content)
    }

    @Composable
    private fun Item(icon: ImageVector?, title: String, summary: String?, onClick: () -> Unit) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = rowModifier.clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
        ) {
            if (icon != null) {
                Icon(icon, null, Modifier.size(26.dp))
                Spacer(Modifier.width(20.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (summary != null) Text(summary, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }

    @Composable
    private fun SwitchItem(icon: ImageVector?, title: String, summary: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = rowModifier.clickable { onChange(!checked) }.padding(horizontal = 20.dp, vertical = 14.dp),
        ) {
            if (icon != null) {
                Icon(icon, null, Modifier.size(26.dp))
                Spacer(Modifier.width(20.dp))
            }
            Column(Modifier.weight(1f).padding(end = 16.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (summary != null) Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }

    @Composable
    private fun ChoiceItem(title: String, summary: String?, selected: Boolean, onSelect: () -> Unit) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = rowModifier.clickable(onClick = onSelect).padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Column(Modifier.weight(1f).padding(end = 16.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (summary != null) Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            RadioButton(selected = selected, onClick = null)
        }
    }

    @Composable
    private fun FieldItem(content: @Composable () -> Unit) {
        Box(rowModifier.padding(horizontal = 16.dp, vertical = 12.dp)) { content() }
    }

    /** A titled group, for pages with several kinds of choice. */
    @Composable
    private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 20.dp, top = 4.dp))
            Group(content)
        }
    }

    @Composable
    private fun Footnote(text: String) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp))
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
