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
import androidx.compose.material.icons.outlined.Folder
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material.icons.outlined.Animation
import androidx.compose.material.icons.outlined.Timer
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import android.widget.Toast
import androidx.compose.runtime.produceState
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.InstallMobile
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Crop
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

    /**
     * The pages of Settings. One activity shows them all, so changes save as you go and the update download
     * carries on while you look at other pages.
     */
    private enum class Page(val title: String) {
        MAIN("Settings"),
        // Connections
        OPEN_WEBUI("Open WebUI"), HOME_ASSISTANT("Home Assistant"),
        // Talking to Lena
        GESTURE("Assistant gesture"), LOOK("How she looks"), VOICE("Voice"), CONVERSATION("Conversation"),
        // Chats
        MODEL("Model"), CHAT_SCREEN("Chat screen"),
        // Prompts
        QUICK("Quick prompts"), SCHEDULED("Scheduled prompts"), SCHEDULE("Scheduled prompt"),
        // This phone
        NOTIFICATIONS("Notifications"), PHONE("Widget & tile"),
        // Wall display
        WALL("Wall display"), WALL_DASHBOARDS("Dashboards"), WALL_IDLE("When no one's around"), WAKE("Wake word"),
        // About
        UPDATES("Updates");

        /** Where back goes: the wall display's own pages sit under it. */
        val parent: Page?
            get() = when (this) {
                MAIN -> null
                WALL_DASHBOARDS, WALL_IDLE, WAKE -> WALL
                SCHEDULE -> SCHEDULED
                else -> MAIN
            }

        val depth: Int get() = parent?.let { it.depth + 1 } ?: 0
    }

    private lateinit var original: Settings

    /** Keeps the settings as they were on arrival, to tell on leaving whether the server changed. */
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

    /**
     * The current page with a top bar; moving deeper slides in from the right, going back from the left.
     * set() saves every change straight away.
     */
    @Composable
    private fun Screen() {
        var s by remember { mutableStateOf(original) }
        var page by remember { mutableStateOf(Page.MAIN) }
        var editing by remember { mutableStateOf<String?>(null) }
        var query by remember { mutableStateOf("") } // Search settings; kept while you look at a result
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

        /** Back: up to the page this one belongs to, or out of Settings from the main list. */
        fun back() {
            page.parent?.let { page = it } ?: leave()
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
                    val forward = targetState.depth > initialState.depth
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
                        Page.MAIN -> Main(s, models, query, { query = it }) { page = it }
                        Page.OPEN_WEBUI -> OpenWebUiPage(s, ::set)
                        Page.HOME_ASSISTANT -> HomeAssistantPage(s, ::set) { page = it }
                        Page.MODEL -> ModelPage(s, models, ::set)
                        Page.GESTURE -> GesturePage(s, ::set)
                        Page.LOOK -> LookPage(s, ::set)
                        Page.VOICE -> VoicePage(s, ::set)
                        Page.CONVERSATION -> ConversationPage(s, ::set)
                        Page.CHAT_SCREEN -> ChatScreenPage(s, ::set)
                        Page.QUICK -> QuickPage(s, ::set)
                        Page.SCHEDULED -> ScheduledPage(s, ::set) { id -> editing = id; page = Page.SCHEDULE }
                        Page.SCHEDULE -> SchedulePage(s, editing, ::set) { page = Page.SCHEDULED }
                        Page.NOTIFICATIONS -> NotificationsPage(s, ::set)
                        Page.PHONE -> PhonePage(s, ::set)
                        Page.WALL -> WallPage(s) { page = it }
                        Page.WALL_DASHBOARDS -> DashboardsPage(s, ::set) { page = it }
                        Page.WALL_IDLE -> IdlePage(s, ::set) { page = it }
                        Page.WAKE -> WakePage(s, ::set)
                        Page.UPDATES -> UpdatesPage()
                    }
                }
            }
        }
    }

    // ---- The list ---------------------------------------------------------------------------

    /**
     * The main list, in sections by what you're doing: connecting Lena, talking to her, your chats, prompts,
     * this phone, the wall display, and about. Each row says how it's set. The search box at the top finds
     * any setting by name or by what it's about (see [findable]).
     */
    @Composable
    private fun Main(s: Settings, models: List<Model>, query: String, onQuery: (String) -> Unit, open: (Page) -> Unit) {
        val house = hasHouse()
        SearchField(query, "Search settings") { onQuery(it) }
        if (query.isNotBlank()) {
            SearchResults(query, open)
            return
        }
        Section("Connections") {
            Item(Icons.Outlined.Language, "Open WebUI", host(s.server) ?: "Not set") { open(Page.OPEN_WEBUI) }
            Item(Icons.Outlined.Home, "Home Assistant", if (house) host(s.haServer) ?: "Connected" else "Not connected") {
                open(Page.HOME_ASSISTANT)
            }
        }
        Section("Talking to Lena") {
            Item(Icons.Outlined.TouchApp, "Assistant gesture", gestureLabel(s.assist) + if (s.lockScreen) " · lock screen" else "") {
                open(Page.GESTURE)
            }
            Item(Icons.Outlined.Palette, "How she looks", s.askStyle.label) { open(Page.LOOK) }
            Item(Icons.Outlined.RecordVoiceOver, "Voice", voiceLabel(s)) { open(Page.VOICE) }
            Item(Icons.Outlined.Forum, "Conversation", "“$ASSISTANT_CHAT” · %,d tokens".format(s.contextTokens)) {
                open(Page.CONVERSATION)
            }
        }
        Section("Chats") {
            Item(Icons.Outlined.SmartToy, "Model", models.firstOrNull { it.id == s.model }?.name ?: s.model.ifBlank { "Lena, automatically" }) {
                open(Page.MODEL)
            }
            Item(Icons.Outlined.ViewSidebar, "Chat screen", chatScreenLabel(s)) { open(Page.CHAT_SCREEN) }
        }
        Section("Prompts") {
            Item(Icons.Outlined.Bolt, "Quick prompts", count(s.quickPrompts.size, "prompt", "None yet")) { open(Page.QUICK) }
            Item(Icons.Outlined.Schedule, "Scheduled prompts",
                if (s.schedules.isEmpty()) "None yet" else s.schedules.filter { it.enabled }.joinToString(", ") { it.title }.ifBlank { "All paused" }) {
                open(Page.SCHEDULED)
            }
        }
        Section("This phone") {
            Item(Icons.Outlined.Notifications, "Notifications", notificationsLabel(s)) { open(Page.NOTIFICATIONS) }
            Item(Icons.Outlined.Widgets, "Widget & tile", if (s.widgetMood) "Widget shows her mood" else "Talk to Lena from the home screen or the shade") {
                open(Page.PHONE)
            }
        }
        Section("Wall display") {
            Item(Icons.Outlined.Tv, "Wall display", "Dashboards when you need them, a photo frame when you don't") { open(Page.WALL) }
        }
        Section("About") {
            val waiting = remember { Updates.pending(this@SettingsActivity) }
            Item(Icons.Outlined.SystemUpdate, "Updates",
                waiting?.let { "${it.first} downloaded, ready to install" } ?: upd.available ?: "From your GitHub releases") { open(Page.UPDATES) }
            val problems by produceState<Int?>(null) {
                value = withContext(Dispatchers.IO) {
                    val day = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
                    Logs.read().count { it.level != Logs.Level.INFO && it.time > day }
                }
            }
            Item(Icons.Outlined.BugReport, "Logs", when (problems) {
                null -> null
                0 -> "No problems in the last day"
                1 -> "1 problem in the last day"
                else -> "$problems problems in the last day"
            }) { openLogs() }
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

    private fun openLogs() = startActivity(Intent(this, LogsActivity::class.java))

    // ---- Search -----------------------------------------------------------------------------

    /**
     * One setting the search box can find: its name, the words someone might look for it by, and the page
     * it's on (null for Logs, which is its own screen).
     */
    private class Findable(val title: String, val words: String, val page: Page?)

    /**
     * Every setting, by name and by what people call it. Keep this in step with the pages: a new setting
     * goes here too, under the page it's on.
     */
    private val findable = listOf(
        Findable("Open WebUI address", "server url https chat connect", Page.OPEN_WEBUI),
        Findable("Sign-in address", "authentik proxy sso single sign-on login", Page.OPEN_WEBUI),
        Findable("Sign in by herself", "auto sign-in automatic login password email ldap signed out session restart", Page.OPEN_WEBUI),
        Findable("Home Assistant address and token", "ha url connect long-lived access token test connection integration", Page.HOME_ASSISTANT),
        Findable("Assistant gesture", "digital assistant power button corner swipe default assistant open", Page.GESTURE),
        Findable("On the lock screen", "locked privacy unlock lockscreen", Page.GESTURE),
        Findable("How she looks when you talk to her", "orb sheet glow bar island subtitles style appearance ask overlay", Page.LOOK),
        Findable("Hearing you", "speech-to-text stt microphone recognition on device whisper", Page.VOICE),
        Findable("Her voice", "text-to-speech tts read aloud piper speak mute", Page.VOICE),
        Findable("Her voice follows her mood", "mood voice pitch speed", Page.VOICE),
        Findable("Keep listening", "follow-up continue conversation listen again mic", Page.VOICE),
        Findable("Assistant Lena chat", "conversation history new conversation one chat", Page.CONVERSATION),
        Findable("Her context size", "tokens num_ctx memory length context window", Page.CONVERSATION),
        Findable("Model", "llm ollama default model lena gemma new chats", Page.MODEL),
        Findable("Lena's look for Open WebUI", "theme colours colors claude warm serif font appearance style dark light", Page.CHAT_SCREEN),
        Findable("Open WebUI's own menus", "header sidebar model selector chat controls sign out", Page.CHAT_SCREEN),
        Findable("Chat | Home switch", "dashboard switcher home assistant toggle top bar", Page.CHAT_SCREEN),
        Findable("Pinned chats in the menu", "drawer pinned how many limit sidebar", Page.CHAT_SCREEN),
        Findable("Folders in the menu", "drawer folders groups sidebar", Page.CHAT_SCREEN),
        Findable("All chats list style", "chat list slim groups flat timeline folding days drawer history appearance", Page.CHAT_SCREEN),
        Findable("Quick prompts", "shortcuts one tap chips widget prompts", Page.QUICK),
        Findable("Scheduled prompts", "briefing morning schedule repeat timer daily recurring automatic", Page.SCHEDULED),
        Findable("Reply notifications", "lena finished notify answer done", Page.NOTIFICATIONS),
        Findable("Alerts on this phone", "worried leak alarm house warning notify", Page.NOTIFICATIONS),
        Findable("Widget", "home screen launcher mood on widget", Page.PHONE),
        Findable("Quick Settings tile", "notification shade pull-down tile talk", Page.PHONE),
        Findable("Wall display", "tablet kiosk dashboard home screen start", Page.WALL),
        Findable("Dashboards", "lovelace path tabs home assistant views", Page.WALL_DASHBOARDS),
        Findable("Screen saver after", "idle dim timeout sleep", Page.WALL_IDLE),
        Findable("Back from your chats after", "return idle wall display timeout", Page.WALL_IDLE),
        Findable("Photo frame", "photos album immich slideshow pictures shuffle transition fit crop faces", Page.WALL_IDLE),
        Findable("Her clock", "clock screen saver night dim time weather", Page.WALL_IDLE),
        Findable("Motion sensor", "presence wake up binary_sensor motion", Page.WALL_IDLE),
        Findable("Wake word", "hey lena jarvis listen name hotword sensitivity false starts", Page.WAKE),
        Findable("Android screen saver", "dream charging daydream", Page.WALL),
        Findable("Updates", "install download version github auto blocker re-download reinstall release", Page.UPDATES),
        Findable("Logs", "problems errors debug log troubleshooting", null),
    )

    /** The settings that match what you typed, each with where it lives; tap one to go there. */
    @Composable
    private fun SearchResults(query: String, open: (Page) -> Unit) {
        val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        val found = findable.filter { f ->
            val text = (f.title + " " + f.words + " " + (f.page?.title ?: "")).lowercase()
            words.all { it in text }
        }
        if (found.isEmpty()) {
            Footnote("Nothing matches. Try another word, like voice, photos or notifications.")
            return
        }
        Group {
            found.forEach { f ->
                Item(null, f.title, f.page?.let { where(it) } ?: "About") {
                    if (f.page == null) openLogs() else open(f.page)
                }
            }
        }
    }

    /** Where a page is, as a path from the main list: "Wall display › When no one's around". */
    private fun where(page: Page): String {
        val trail = generateSequence(page) { it.parent }.takeWhile { it != Page.MAIN }.toList().reversed()
        return trail.joinToString(" › ") { it.title }
    }

    // ---- Pages ------------------------------------------------------------------------------

    /** Update state lives with the activity, so a download carries on while you look at other pages. */
    private class UpdateState {
        var release by mutableStateOf<Updates.Release?>(null)
        var status by mutableStateOf<String?>(null)
        var progress by mutableStateOf<Float?>(null)
        var available by mutableStateOf<String?>(null)
        var checked = false
    }

    private val upd = UpdateState()

    /**
     * Asks GitHub for the latest release and says whether it's newer. A different, lower version is pointed
     * at Re-download and install, since that's what a reset version number looks like.
     */
    private fun checkForUpdate() {
        lifecycleScope.launch {
            upd.status = "Checking…"
            try {
                val r = Updates.latest(this@SettingsActivity)
                upd.release = r
                val installed = Updates.installed(this@SettingsActivity)
                val newer = Updates.newer(r.version, installed)
                upd.available = if (newer) "${r.version} is available" else null
                upd.status = when {
                    newer -> "Lena ${r.version} is available"
                    Updates.different(r.version, this@SettingsActivity) ->
                        "The latest release is ${r.version} (you have $installed). After a version reset, use Re-download and install."
                    else -> "You have the latest ($installed)"
                }
            } catch (e: Exception) {
                Logs.w("Updates", "Couldn't check for updates", e)
                upd.status = e.message ?: "Couldn't reach GitHub."
            }
        }
    }

    /**
     * Downloads and checks a release in the activity's scope, so it continues if you move to another page.
     * With reinstall, the same build may be installed again.
     */
    private fun download(r: Updates.Release, reinstall: Boolean = false, done: () -> Unit) {
        if (upd.progress != null) return
        lifecycleScope.launch {
            upd.progress = 0f
            try {
                Updates.download(this@SettingsActivity, r, reinstall) { upd.progress = it }
                upd.status = "Downloaded and checked ${r.version}"
                done()
            } catch (e: Exception) {
                upd.status = e.message ?: "The download failed."
            } finally {
                upd.progress = null
            }
        }
    }

    /**
     * Updates: what's waiting to install and the steps for it, checking, downloading, Re-download and
     * install, and where releases come from.
     */
    @Composable
    private fun UpdatesPage() {
        val installed = remember { Updates.installed(this) }
        var pending by remember { mutableStateOf(Updates.pending(this)) }
        var mayInstall by remember { mutableStateOf(Updates.mayInstall(this)) }
        var repo by remember { mutableStateOf(Updates.repo(this)) }
        var token by remember { mutableStateOf("") }
        var tokenSaved by remember { mutableStateOf(Secrets.has(this, Updates.GITHUB_TOKEN)) }

        // Coming back from Android's settings: see what changed.
        val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
        DisposableEffect(owner) {
            val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                    mayInstall = Updates.mayInstall(this@SettingsActivity)
                    pending = Updates.pending(this@SettingsActivity)
                }
            }
            owner.lifecycle.addObserver(observer)
            onDispose { owner.lifecycle.removeObserver(observer) }
        }
        LaunchedEffect(Unit) {
            if (!upd.checked && pending == null) {
                upd.checked = true
                checkForUpdate()
            }
        }

        Footnote("This is Lena $installed. Updates come from the releases in your GitHub repository.")

        pending?.let { (version, file) ->
            Section("Ready to install $version") {
                Item(Icons.Outlined.AdminPanelSettings, "1. Let Lena install apps",
                    if (mayInstall) "Allowed" else "Not allowed yet: tap, turn on Allow from this source, then come back",
                    trailing = if (mayInstall) { { Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 12.dp)) } } else null,
                ) {
                    startActivity(Intent(AndroidSettings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
                }
                Item(Icons.Outlined.Shield, "2. If Auto Blocker stops it",
                    "Opens Security and privacy: turn Auto Blocker off, then come back here and install. Lena reminds you to turn it back on afterwards.") {
                    Updates.remindAutoBlocker(this@SettingsActivity, true)
                    try {
                        startActivity(Updates.autoBlocker())
                        Toast.makeText(this@SettingsActivity, "Auto Blocker is under Security and privacy", Toast.LENGTH_LONG).show()
                    } catch (_: ActivityNotFoundException) {
                        Toast.makeText(this@SettingsActivity, "Open Settings → Security and privacy → Auto Blocker", Toast.LENGTH_LONG).show()
                    }
                }
                Item(Icons.Outlined.InstallMobile, "3. Install $version", "Android asks you to confirm. The download is deleted once the update is in.") {
                    if (!mayInstall) {
                        startActivity(Intent(AndroidSettings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
                    } else {
                        try {
                            Updates.install(this@SettingsActivity)
                        } catch (e: Exception) {
                            Logs.e("Updates", "Couldn't start the install", e)
                            upd.status = "Couldn't start the install. See Settings → Logs."
                        }
                    }
                }
                Item(Icons.Outlined.Delete, "Delete the download", "%.1f MB".format(file.length() / 1_048_576.0)) {
                    Updates.discard(this@SettingsActivity)
                    pending = null
                }
            }
        }

        Group {
            Item(Icons.Outlined.Refresh, "Check for updates", upd.status ?: "From $repo") { checkForUpdate() }
            val r = upd.release
            if (r != null && Updates.newer(r.version, installed) && pending?.first != r.version) {
                val p = upd.progress
                Item(
                    Icons.Outlined.Download,
                    if (p != null) "Downloading ${r.version}… ${(p * 100).toInt()}%" else "Download ${r.version}",
                    "%.1f MB, checked against the release's SHA256SUMS and your signing key".format(r.size / 1_048_576.0),
                    trailing = if (p != null) { { LinearProgressIndicator(progress = { p }, modifier = Modifier.width(72.dp).padding(end = 12.dp)) } } else null,
                ) { download(r) { pending = Updates.pending(this@SettingsActivity) } }
                if (r.notes.isNotBlank()) FieldItem {
                    Column {
                        Text("What's new", style = MaterialTheme.typography.titleSmall)
                        Text(r.notes.trim().take(1500), style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (pending == null) Group {
            val p = upd.progress
            val r = upd.release
            Item(
                Icons.Outlined.Replay,
                if (p != null && r != null) "Downloading ${r.version}… ${(p * 100).toInt()}%" else "Re-download and install",
                "Gets the latest release again even if you have it: to repair Lena, or after the version number was reset. Checked the same way.",
                trailing = if (p != null) { { LinearProgressIndicator(progress = { p }, modifier = Modifier.width(72.dp).padding(end = 12.dp)) } } else null,
            ) {
                val again = { rel: Updates.Release -> download(rel, reinstall = true) { pending = Updates.pending(this@SettingsActivity) } }
                if (r != null) again(r) else lifecycleScope.launch {
                    upd.status = "Checking…"
                    try {
                        val latest = Updates.latest(this@SettingsActivity)
                        upd.release = latest
                        upd.status = "The latest release is ${latest.version}"
                        again(latest)
                    } catch (e: Exception) {
                        upd.status = e.message ?: "Couldn't reach GitHub."
                    }
                }
            }
        }

        Section("Your repository") {
            FieldItem {
                OutlinedTextField(
                    value = repo, onValueChange = { repo = it.trim(); Updates.setRepo(this@SettingsActivity, it) },
                    label = { Text("owner/repository") }, singleLine = true,
                    supportingText = { Text("Where the releases are. Lena's own is ${Updates.DEFAULT_REPO}.") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            FieldItem {
                OutlinedTextField(
                    value = token,
                    onValueChange = {
                        token = it
                        if (it.isNotBlank()) {
                            Secrets.put(this@SettingsActivity, Updates.GITHUB_TOKEN, it.trim())
                            tokenSaved = true
                        }
                    },
                    label = { Text(if (tokenSaved && token.isBlank()) "GitHub token (saved)" else "GitHub token") },
                    placeholder = { Text(if (tokenSaved) "Paste a new one to replace it" else "Needed for a private repository") },
                    supportingText = { Text("Stored encrypted on this phone and sent only to GitHub.") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        if (tokenSaved) IconButton(onClick = {
                            Secrets.put(this@SettingsActivity, Updates.GITHUB_TOKEN, null)
                            tokenSaved = false
                            token = ""
                        }) { Icon(Icons.Outlined.Delete, "Remove the saved token") }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Footnote("For a private repository, make a token on GitHub: Settings → Developer settings → Fine-grained tokens → Generate. " +
            "Choose only this repository, and under Repository permissions give Contents read-only access. Nothing else.")
    }

    /**
     * Your Open WebUI's address, optional sign-in addresses (for a sign-in proxy), and signing in by
     * herself.
     */
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

        AutoSignInSection(s, set)
    }

    /** Signing in again by itself when Open WebUI ends the session, with details kept encrypted on the phone. */
    @Composable
    private fun AutoSignInSection(s: Settings, set: (Settings) -> Unit) {
        val scope = rememberCoroutineScope()
        var user by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var userSaved by remember { mutableStateOf(Secrets.has(this, OpenWebUiSignIn.USER)) }
        var passwordSaved by remember { mutableStateOf(Secrets.has(this, OpenWebUiSignIn.PASSWORD)) }
        var check by remember { mutableStateOf<String?>(null) }
        Section("Sign in by herself") {
            PickItem(Icons.Outlined.Key, "When Open WebUI signs her out", AutoSignIn.entries.map { it to it.label }, s.autoSignIn) {
                set(s.copy(autoSignIn = it))
                check = null
            }
            if (s.autoSignIn != AutoSignIn.OFF) {
                FieldItem {
                    OutlinedTextField(
                        value = user,
                        onValueChange = {
                            user = it
                            check = null
                            if (it.isNotBlank()) { Secrets.put(this@SettingsActivity, OpenWebUiSignIn.USER, it.trim()); userSaved = true }
                        },
                        label = { Text((if (s.autoSignIn == AutoSignIn.LDAP) "User name" else "Email") + if (userSaved && user.isBlank()) " (saved)" else "") },
                        placeholder = { Text(if (userSaved) "Type a new one to replace it" else "") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = if (s.autoSignIn == AutoSignIn.LDAP) KeyboardType.Text else KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                FieldItem {
                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            check = null
                            if (it.isNotEmpty()) { Secrets.put(this@SettingsActivity, OpenWebUiSignIn.PASSWORD, it); passwordSaved = true }
                        },
                        label = { Text(if (passwordSaved && password.isEmpty()) "Password (saved)" else "Password") },
                        placeholder = { Text(if (passwordSaved) "Type a new one to replace it" else "") },
                        supportingText = { Text("Stored encrypted on this phone and sent only to your Open WebUI.") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            if (userSaved || passwordSaved) IconButton(onClick = {
                                Secrets.put(this@SettingsActivity, OpenWebUiSignIn.USER, null)
                                Secrets.put(this@SettingsActivity, OpenWebUiSignIn.PASSWORD, null)
                                userSaved = false; passwordSaved = false; user = ""; password = ""; check = null
                            }) { Icon(Icons.Outlined.Delete, "Forget the saved sign-in") }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Item(null, "Test", check ?: "Signs in once with these details") {
                    scope.launch {
                        check = "Signing in…"
                        check = OpenWebUiSignIn.test(this@SettingsActivity)
                    }
                }
            }
        }
        Footnote("When Open WebUI restarts it can sign out every device, which leaves a wall tablet stuck on its sign-in page. " +
            "With this on, Lena signs in again by herself, in the app and for scheduled prompts and the wall display. " +
            "It can't do single sign-on (Authentik, Google). Better still: set WEBUI_SECRET_KEY on your Open WebUI server, " +
            "so a restart doesn't sign anyone out in the first place.")
    }

    /**
     * Home Assistant's address and token (kept encrypted), and a connection test that also checks for the
     * Lena integration. This phone's alerts are under Notifications.
     */
    @Composable
    private fun HomeAssistantPage(s: Settings, set: (Settings) -> Unit, open: (Page) -> Unit) {
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
            Item(Icons.Outlined.NotificationImportant, "Alerts on this phone", if (s.alerts) "On" else "Off") { open(Page.NOTIFICATIONS) }
            Item(Icons.Outlined.Tv, "Wall display", "Your dashboards on a tablet") { open(Page.WALL) }
        }
        Footnote("Home Assistant also gives the wall display its dashboards and photos, and the Chat | Home switch somewhere to go.")
    }

    /** What Lena tells you about: replies you left, and worries at home. */
    @Composable
    private fun NotificationsPage(s: Settings, set: (Settings) -> Unit) {
        Group {
            SwitchItem(Icons.Outlined.Notifications, "Reply notifications", "When she finishes a reply you left before it was done", s.notifyReplies) {
                set(s.copy(notifyReplies = it))
            }
        }
        Footnote("Scheduled prompts always notify you with their answer.")
        Group {
            SwitchItem(Icons.Outlined.NotificationImportant, "Alerts on this phone",
                "Checks every 15 minutes and tells you when she's worried about something at home. Needs Home Assistant.", s.alerts) {
                set(s.copy(alerts = it))
                if (it && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 2)
                }
            }
        }
        Footnote("For instant alerts, written by her, pick this phone under Tell these phones when she's worried in the Lena integration's Configure (needs the Home Assistant companion app). Use this switch on phones without it.")
        Group {
            Item(Icons.AutoMirrored.Outlined.OpenInNew, "Android's notification settings", "Sounds, and which kinds of notification show") {
                startActivity(Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(AndroidSettings.EXTRA_APP_PACKAGE, packageName))
            }
        }
    }

    /** Which model talks to you: Lena (found by name on your server) unless you pick another. */
    @Composable
    private fun ModelPage(s: Settings, models: List<Model>, set: (Settings) -> Unit) {
        Footnote("Used for new chats, the sheet and scheduled prompts.")
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

    /**
     * What the assistant gesture opens, and whether it works on the lock screen, with what that means for
     * privacy.
     */
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

    /** Who hears you, who speaks, whether her voice follows her mood, and when she listens again. */
    @Composable
    private fun VoicePage(s: Settings, set: (Settings) -> Unit) {
        val phoneEar = remember { PhoneEar.available(this) }
        Section("Hearing you") {
            ChoiceItem("On this phone",
                if (phoneEar) "Your words appear as you speak. The audio never leaves the phone." else "Not available on this phone; your server is used.",
                s.hearing == Hearing.PHONE) { set(s.copy(hearing = Hearing.PHONE)) }
            ChoiceItem("Your server", "Open WebUI's speech-to-text, the same as its voice input", s.hearing == Hearing.SERVER) {
                set(s.copy(hearing = Hearing.SERVER))
            }
        }
        Section("Her voice") {
            ChoiceItem("Your server's voice", "Open WebUI's text-to-speech, the same as reading a reply aloud there", s.speaking == Speaking.SERVER) {
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

    /** The one Assistant Lena chat, and the model's context size, half of which holds that conversation. */
    @Composable
    private fun ConversationPage(s: Settings, set: (Settings) -> Unit) {
        Group {
            Item(Icons.Outlined.Forum, "“$ASSISTANT_CHAT”", "Talking to Lena always continues this one chat, so she's the same Lena with the same history on every screen") { }
            FieldItem {
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
        Footnote("It's found by its name, so it's the same chat after reinstalling or on another phone. Start over with New conversation: " +
            "the old one is kept in your history, named with the date, and a new “$ASSISTANT_CHAT” begins.")
    }

    /** Up to six one-tap prompts for the sheet, the widget and the app icon's menu. */
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

    /** Your scheduled prompts by name; tap one to change it. */
    @Composable
    private fun ScheduledPage(s: Settings, set: (Settings) -> Unit, open: (String) -> Unit) {
        Footnote("Lena asks these on her own and notifies you with the answer. Give several the same chat to keep them in one history, like a morning and an evening briefing.")
        if (s.schedules.isNotEmpty()) Group {
            s.schedules.forEach { sch ->
                Item(Icons.Outlined.Schedule, sch.title, scheduleSummary(sch),
                    trailing = {
                        Switch(checked = sch.enabled, modifier = Modifier.padding(end = 12.dp), onCheckedChange = { on ->
                            set(s.copy(schedules = s.schedules.map { if (it.id == sch.id) it.copy(enabled = on) else it }))
                        })
                    },
                ) { open(sch.id) }
            }
        }
        Group {
            Item(Icons.Outlined.Add, "Add a scheduled prompt", null) {
                val n = Schedule(name = "Morning briefing", prompt = "Give me my morning briefing.")
                set(s.copy(schedules = s.schedules + n))
                open(n.id)
            }
        }
    }

    /** One scheduled prompt: its name, what she's asked, when, and which chat it goes into. */
    @Composable
    private fun SchedulePage(s: Settings, id: String?, set: (Settings) -> Unit, done: () -> Unit) {
        val sch = s.schedules.firstOrNull { it.id == id } ?: run { Footnote("That scheduled prompt was removed."); return }
        /** Saves a change to this one scheduled prompt. */
        fun put(n: Schedule) = set(s.copy(schedules = s.schedules.map { if (it.id == sch.id) n else it }))
        Group {
            FieldItem {
                OutlinedTextField(value = sch.name, onValueChange = { put(sch.copy(name = it)) }, label = { Text("Name") },
                    placeholder = { Text("e.g. Morning briefing") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            FieldItem {
                OutlinedTextField(value = sch.prompt, onValueChange = { put(sch.copy(prompt = it)) }, label = { Text("What she's asked") },
                    minLines = 2, modifier = Modifier.fillMaxWidth())
            }
            SwitchItem(null, "Show the prompt in the chat",
                if (sch.showPrompt) "The chat shows what she was asked, then her answer"
                else "The chat shows just \u201C${sch.title}\u201D and her answer; the prompt goes to her quietly", sch.showPrompt) {
                put(sch.copy(showPrompt = it))
            }
            SwitchItem(null, "On", if (sch.enabled) Schedules.next(sch)?.let { "Next: " + nextLabel(it) } ?: "Won't run again" else "Paused", sch.enabled) {
                put(sch.copy(enabled = it))
            }
        }

        Section("When") {
            Item(Icons.Outlined.Timer, if (sch.repeat == Repeat.HOURS) "Starting at" else "Time", timeLabel(sch.hour, sch.minute)) {
                TimePickerDialog(this@SettingsActivity, { _, h, m -> put(sch.copy(hour = h, minute = m)) },
                    sch.hour, sch.minute, DateFormat.is24HourFormat(this@SettingsActivity)).show()
            }
            PickItem(Icons.Outlined.Schedule, "Repeats", Repeat.entries.map { it to it.label }, sch.repeat) {
                put(sch.copy(repeat = it, date = if (it == Repeat.ONCE && sch.date == 0L) java.time.LocalDate.now().toEpochDay() else sch.date))
            }
            when (sch.repeat) {
                Repeat.DAYS -> FieldItem {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        java.time.DayOfWeek.entries.forEach { d ->
                            val on = d.value in sch.days
                            FilterChip(
                                selected = on,
                                onClick = { put(sch.copy(days = if (on) sch.days - d.value else sch.days + d.value)) },
                                label = { Text(d.getDisplayName(java.time.format.TextStyle.NARROW, java.util.Locale.getDefault())) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
                Repeat.HOURS -> PickItem(null, "Every", listOf(1, 2, 3, 4, 6, 8, 12).map { it to if (it == 1) "Hour, until midnight" else "$it hours, until midnight" },
                    sch.everyHours) { put(sch.copy(everyHours = it)) }
                Repeat.MONTHLY -> PickItem(null, "On the", (1..31).map { it to if (it >= 29) "${ordinal(it)} (or the month's last day)" else ordinal(it) },
                    sch.dayOfMonth) { put(sch.copy(dayOfMonth = it)) }
                Repeat.ONCE -> {
                    val day = if (sch.date > 0) java.time.LocalDate.ofEpochDay(sch.date) else java.time.LocalDate.now()
                    Item(null, "On", java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.LONG).format(day)) {
                        android.app.DatePickerDialog(this@SettingsActivity, { _, y, m, d ->
                            put(sch.copy(date = java.time.LocalDate.of(y, m + 1, d).toEpochDay()))
                        }, day.year, day.monthValue - 1, day.dayOfMonth).show()
                    }
                }
                else -> Unit
            }
        }

        Section("Where it goes") {
            FieldItem {
                OutlinedTextField(
                    value = sch.chat, onValueChange = { put(sch.copy(chat = it)) },
                    label = { Text("Chat (optional)") }, placeholder = { Text("e.g. Daily briefings") }, singleLine = true,
                    supportingText = { Text("Every run goes into the chat with this name, made the first time. Give several prompts the same one to share a history. Empty: a new chat each time.") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            val others = s.schedules.map { it.chat.trim() }.filter { it.isNotEmpty() && it != sch.chat.trim() }.distinct()
            if (others.isNotEmpty()) FieldItem {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    others.take(4).forEach { name -> AssistChip(onClick = { put(sch.copy(chat = name)) }, label = { Text(name) }) }
                }
            }
        }

        Group {
            Item(Icons.Outlined.PlayArrow, "Run it now", "Asks her straight away and notifies you with the answer") {
                PromptWorker.enqueue(this@SettingsActivity, sch.id)
                Toast.makeText(this@SettingsActivity, "Asked. The answer comes as a notification.", Toast.LENGTH_SHORT).show()
            }
            Item(Icons.Outlined.Delete, "Delete", null) {
                set(s.copy(schedules = s.schedules.filter { it.id != sch.id }))
                done()
            }
        }
    }

    /** 1st, 2nd, 3rd, 4th, 11th, 12th, 13th, 21st… */
    private fun ordinal(n: Int) = "$n" + if (n in 11..13) "th" else when (n % 10) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }

    /** "7:00 AM · Weekdays · into Daily briefings" */
    private fun scheduleSummary(sch: Schedule): String {
        val time = timeLabel(sch.hour, sch.minute)
        val days = java.time.DayOfWeek.entries.filter { it.value in sch.days }
            .joinToString(" ") { it.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault()) }
        val whenText = when (sch.repeat) {
            Repeat.DAILY -> "$time every day"
            Repeat.WEEKDAYS -> "$time on weekdays"
            Repeat.WEEKENDS -> "$time at weekends"
            Repeat.DAYS -> "$time · ${days.ifBlank { "no days picked" }}"
            Repeat.HOURS -> "Every ${if (sch.everyHours == 1) "hour" else "${sch.everyHours} hours"} from $time"
            Repeat.MONTHLY -> "$time on the ${ordinal(sch.dayOfMonth)} of each month"
            Repeat.ONCE -> "Once, " + (if (sch.date > 0) java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM)
                .format(java.time.LocalDate.ofEpochDay(sch.date)) else "date not set") + " at $time"
        }
        return whenText + if (sch.chat.isNotBlank()) " · into “${sch.chat.trim()}”" else ""
    }

    /** When a scheduled prompt runs next, in the phone's 12- or 24-hour style. */
    private fun nextLabel(millis: Long): String =
        DateFormat.format(if (DateFormat.is24HourFormat(this)) "EEE, MMM d, HH:mm" else "EEE, MMM d, h:mm a", millis).toString()

    /** The widget and the Quick Settings tile. */
    @Composable
    private fun PhonePage(s: Settings, set: (Settings) -> Unit) {
        Group {
            SwitchItem(null, "Her mood on the widget", "Updated every half hour. Only the mood, never the reasons. Needs Home Assistant.", s.widgetMood) {
                set(s.copy(widgetMood = it))
            }
        }
        Group {
            Item(Icons.Outlined.AddToHomeScreen, "Add the widget", "Talk, type, new chat and your quick prompts") { addWidget() }
            Item(Icons.Outlined.TouchApp, "Add the Quick Settings tile", "Talk to Lena from the notification shade") { addTile() }
        }
    }

    /**
     * Whether Home Assistant is set up (address and token), which the wall display's dashboards and photos
     * need.
     */
    private fun hasHouse() = Prefs.haServer(this) != null && Secrets.has(this, Secrets.HA_TOKEN)

    /** A delay in plain words: 30 seconds, 1 minute, 5 minutes, 1 hour. */
    private fun duration(seconds: Int): String = when {
        seconds == 0 -> "Never"
        seconds < 60 -> "$seconds seconds"
        seconds == 60 -> "1 minute"
        seconds < 3600 -> "${seconds / 60} minutes"
        seconds == 3600 -> "1 hour"
        else -> "${seconds / 3600} hours"
    }

    private val screenSaverDelays = listOf(30, 60, 120, 300, 600, 900, 1800, 3600, 0)

    /**
     * The wall display: one place for a tablet on the wall. Your Home Assistant dashboards while
     * someone's using it; the photo frame (or her clock) when no one is; her name to talk to her.
     */
    @Composable
    private fun WallPage(s: Settings, open: (Page) -> Unit) {
        val house = hasHouse()
        val sl = s.slides
        Footnote("For a tablet on the wall: your Home Assistant dashboards while you're using it, a photo frame or her clock when no one's around, and her name to talk to her from across the room.")
        Group {
            Item(Icons.Outlined.PlayArrow, "Start the wall display",
                if (house) "Back leaves it" else "Clock and photos only until Home Assistant is connected") {
                startActivity(Intent(this@SettingsActivity, if (house) DashboardActivity::class.java else DisplayActivity::class.java))
            }
            Item(Icons.Outlined.Home, "Make it this tablet's home screen", "Then Home always brings it back") {
                try {
                    startActivity(Intent(AndroidSettings.ACTION_HOME_SETTINGS))
                } catch (_: ActivityNotFoundException) {
                    startActivity(Intent(AndroidSettings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
                }
            }
        }

        SectionTitle("Set it up")
        Group {
            Item(Icons.Outlined.Dashboard, "Dashboards",
                if (house) s.dashboards.joinToString(", ") { it.name.ifBlank { it.path } } else "Connect Home Assistant first") {
                open(if (house) Page.WALL_DASHBOARDS else Page.HOME_ASSISTANT)
            }
            Item(Icons.Outlined.PhotoAlbum, "When no one's around",
                (if (sl.on && sl.albumName.isNotBlank()) "Photo frame: ${sl.albumName}" else "Her clock") +
                    if (s.dimSeconds == 0) " · never on its own" else " · after ${duration(s.dimSeconds).lowercase()}") {
                open(Page.WALL_IDLE)
            }
            Item(Icons.Outlined.Hearing, "Wake word", if (s.wakeWord) "Listens for \u201C${s.wakePhrase.label}\u201D" else "Off") { open(Page.WAKE) }
        }

        SectionTitle("While it charges")
        Group {
            Item(Icons.AutoMirrored.Outlined.OpenInNew, "Use it as the screen saver",
                "The same idle screen, on its own whenever the device charges. Android settings → Screen saver → Lena.") {
                try {
                    startActivity(Intent(AndroidSettings.ACTION_DREAM_SETTINGS))
                } catch (_: ActivityNotFoundException) {
                }
            }
        }
    }

    /** The dashboards it shows, as tabs. */
    @Composable
    private fun DashboardsPage(s: Settings, set: (Settings) -> Unit, open: (Page) -> Unit) {
        if (!hasHouse()) {
            Group { Item(Icons.Outlined.Home, "Connect Home Assistant first", "Its address and a token") { open(Page.HOME_ASSISTANT) } }
            return
        }
        Footnote("Each one is a tab across the top. The path is what comes after your Home Assistant's address, like lovelace or dashboard-floorplan/0.")
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
        Footnote("Home Assistant's own header is replaced by Lena's bar: Home Assistant's menu (back is the back gesture), the time and the weather, the dashboard's views as tabs, how she feels (tap to talk), and buttons to reload, for your other apps and for settings; the Chat | Home switch beside the menu opens your chats. With more than one dashboard here, a dashboards button switches between them. When she's worried about something at home the bar turns into a banner saying what's wrong. Editing a dashboard brings Home Assistant's header back while you edit.")
    }

    /** What it shows when no one's using it, and what wakes it up. Also the screen saver. */
    @Composable
    private fun IdlePage(s: Settings, set: (Settings) -> Unit, open: (Page) -> Unit) {
        val sl = s.slides
        /** Saves a change to the photo frame's settings. */
        fun put(n: Slides) = set(s.copy(slides = n))
        var picking by remember { mutableStateOf(false) }
        val house = hasHouse()

        Footnote("After a while without a touch the dashboards give way to this screen saver. Android's own screen saver shows it too.")
        Group {
            PickItem(Icons.Outlined.Timer, "Screen saver after",
                screenSaverDelays.map { it to if (it == 0) "Never" else "${duration(it)} without a touch" }, s.dimSeconds) {
                set(s.copy(dimSeconds = it))
            }
            PickItem(Icons.Outlined.Forum, "Back from your chats after",
                listOf(30, 60, 120, 300, 600, 0).map { it to if (it == 0) "Never" else "${duration(it)} without a touch" }, s.returnSeconds) {
                set(s.copy(returnSeconds = it))
            }
        }
        Footnote("Opened your chats from the wall display and walked away? After this long it goes back to the dashboards and straight to the screen saver. Not while you're typing or while she's still answering.")

        SectionTitle("Show")
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(selected = !sl.on, onClick = { put(sl.copy(on = false)) }, shape = SegmentedButtonDefaults.itemShape(0, 2)) {
                Text("Her clock")
            }
            SegmentedButton(
                selected = sl.on, enabled = house,
                onClick = { put(sl.copy(on = true)); if (sl.album.isBlank()) picking = true },
                shape = SegmentedButtonDefaults.itemShape(1, 2),
            ) { Text("Photo frame") }
        }
        if (!sl.on) {
            Footnote(if (house) "The time, the weather, how she feels and the last thing she said, on black. It drifts a little each minute so it doesn't burn in, and goes dim and warm at night."
                else "The time and the date on black. Connect Home Assistant for the weather, her mood and the photo frame.")
        } else {
            Group {
                Item(Icons.Outlined.PhotoAlbum, "Album", sl.albumName.ifBlank { "Choose one" }) { picking = true }
                PickItem(Icons.Outlined.Schedule, "Each photo for",
                    listOf(5 to "5 seconds", 10 to "10 seconds", 15 to "15 seconds", 30 to "30 seconds", 60 to "1 minute", 300 to "5 minutes"),
                    sl.seconds) { put(sl.copy(seconds = it)) }
                PickItem(Icons.Outlined.Animation, "Transition", SlideTransition.entries.map { it to it.label }, sl.transition) {
                    put(sl.copy(transition = it))
                }
            }
            Group {
                SwitchItem(null, "Shuffle", "Otherwise in the album's order", sl.shuffle) { put(sl.copy(shuffle = it)) }
                PickItem(Icons.Outlined.Crop, "Fit", Framing.entries.map { it to "${it.label}: ${it.detail}" }, sl.framing) { put(sl.copy(framing = it)) }
                SwitchItem(null, "Time and her mood on top", null, sl.overlay) { put(sl.copy(overlay = it)) }
                SwitchItem(null, "Her dim clock at night", "10 PM to 6 AM, and when she's drained, so it doesn't light up the room", sl.clockAtNight) {
                    put(sl.copy(clockAtNight = it))
                }
            }
            Footnote("Albums come from Home Assistant's media: Immich albums, people and favorites, your My media folders. Needs the Lena integration in Home Assistant.")
        }

        SectionTitle("Waking it up")
        Group {
            FieldItem {
                OutlinedTextField(
                    value = s.motionEntity, onValueChange = { set(s.copy(motionEntity = it)) },
                    label = { Text("Motion or presence sensor (optional)") },
                    placeholder = { Text("binary_sensor.hallway_motion") },
                    supportingText = { Text("Brings the dashboards back when it sees someone. Checked every few seconds while it's idle.") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Footnote("A touch always wakes it, and so does Lena being worried about something at home.")

        if (picking) AlbumPicker(
            onPick = { id, name ->
                picking = false
                put(sl.copy(album = id, albumName = name, on = true))
            },
            onDismiss = { picking = false; if (sl.album.isBlank()) put(sl.copy(on = false)) },
        )
    }

    /**
     * The wake word: on or off, a live test, which phrase, how sure she must be, and what to do about false
     * starts.
     */
    @Composable
    private fun WakePage(s: Settings, set: (Settings) -> Unit) {
        Footnote("Talk to her from across the room while the wall display or the screen saver is showing. It listens on this device only; nothing is recorded or sent until she hears her name.")
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
        Section("How sure she must be") {
            WakeSensitivity.entries.forEach { v ->
                ChoiceItem(v.label, v.detail, s.wakeSensitivity == v) { set(s.copy(wakeSensitivity = v)) }
            }
        }
        Footnote("Started by mistake? “Hey Lena” is far less likely to be heard in ordinary talk or on TV than “Lena” on its own, and " +
            "Fewer false starts makes any of them stricter. She also ignores her name until she's been listening for a second, and when " +
            "a voice check says nobody's speaking (music, a jingle, a clatter). Each start is in Settings → Logs with how sure she was.")
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

    /** How talking to Lena looks over other apps, with a button to try it. */
    @Composable
    private fun LookPage(s: Settings, set: (Settings) -> Unit) {
        Group {
            AskStyle.entries.forEach { a -> ChoiceItem(a.label, a.detail, s.askStyle == a) { set(s.copy(askStyle = a)) } }
        }
        Group {
            Item(Icons.Outlined.PlayArrow, "Try it", "Opens Lena the way your assistant gesture does") {
                startActivity(AskActivity.intent(this@SettingsActivity, talk = s.assist != AssistAction.TYPE))
            }
        }
        Footnote("Every look listens, answers and speaks the same way. The sheet is the only one that shows earlier turns; the others show what's happening now.")
    }

    /**
     * The main screen: how Open WebUI's pages look, what the top bar shows, and the menu and All chats
     * (how many pinned chats, folders, the list style).
     */
    @Composable
    private fun ChatScreenPage(s: Settings, set: (Settings) -> Unit) {
        Section("The page") {
            SwitchItem(Icons.Outlined.Palette, "Lena's look", "Warm colours, a clay accent and replies in a serif. Off: Open WebUI's own look.", s.lenaLook) {
                set(s.copy(lenaLook = it))
            }
            SwitchItem(Icons.Outlined.ViewSidebar, "Open WebUI's own menus", "Its header and sidebar, under Lena's, for its model selector, chat controls and settings", s.openWebUiMenus) {
                set(s.copy(openWebUiMenus = it))
            }
        }
        Section("Top bar") {
            SwitchItem(Icons.Outlined.Home, "Chat | Home switch",
                if (hasHouse()) "Beside her logo, to switch to your dashboards" else "Shows once Home Assistant is connected", s.homeSwitch) {
                set(s.copy(homeSwitch = it))
            }
        }
        Section("The menu") {
            PickItem(Icons.Outlined.PushPin, "Pinned chats", listOf(3, 5, 8, 12).map { it to "Up to $it" }, s.pinnedShown) {
                set(s.copy(pinnedShown = it))
            }
            SwitchItem(Icons.Outlined.Folder, "Folders", "Your Open WebUI folders, between pinned and recent chats", s.drawerFolders) {
                set(s.copy(drawerFolders = it))
            }
        }
        Footnote("The menu shows your pinned chats, your folders and your latest chats, as many as fit, and never scrolls. View all chats at its bottom has everything. Long-press a chat to pin it, move it to a folder, rename or delete it.")
        Section("All chats list style") {
            ChatListStyle.entries.forEach { c -> ChoiceItem(c.label, c.detail, s.chatList == c) { set(s.copy(chatList = c)) } }
        }
    }

    // ---- Words for the list -----------------------------------------------------------------

    /** Just the host of an address, for the main list's summaries. */
    private fun host(address: String): String? = Uri.parse(address.trim()).host?.ifBlank { null }

    /** "3 prompts", "1 prompt", or the text for none. */
    private fun count(n: Int, noun: String, none: String) = if (n == 0) none else "$n $noun${if (n == 1) "" else "s"}"

    /** The name of each assistant-gesture choice. */
    private fun gestureLabel(a: AssistAction) = when (a) {
        AssistAction.ASK -> "Talk to Lena"
        AssistAction.TYPE -> "Type to Lena"
        AssistAction.CHAT -> "New chat"
    }

    /** What each assistant-gesture choice does. */
    private fun gestureDetail(a: AssistAction) = when (a) {
        AssistAction.ASK -> "Listens straight away, over whatever you're doing"
        AssistAction.TYPE -> "The same sheet, with the keyboard"
        AssistAction.CHAT -> "Opens Lena on a new chat"
    }

    /** The Chat screen row's summary: the look, and what the menu shows. */
    private fun chatScreenLabel(s: Settings): String =
        listOf(
            if (s.lenaLook) "Lena's look" else "Open WebUI's look",
            if (s.drawerFolders) "pinned, folders, recent" else "pinned, recent",
            s.chatList.label.lowercase(),
        ).joinToString(" · ")

    /** The Notifications row's summary: which of the two are on. */
    private fun notificationsLabel(s: Settings): String = when {
        s.notifyReplies && s.alerts -> "Replies and house alerts"
        s.notifyReplies -> "Replies you left"
        s.alerts -> "House alerts"
        else -> "Off"
    }

    /** The Voice row's summary: who listens, who speaks, and whether it follows her mood. */
    private fun voiceLabel(s: Settings): String {
        val ear = if (s.hearing == Hearing.PHONE) "Phone listens" else "Server listens"
        val mouth = when (s.speaking) {
            Speaking.SERVER -> "server voice"
            Speaking.PHONE -> "phone voice"
            Speaking.OFF -> "replies not read aloud"
        }
        return "$ear · $mouth" + if (s.moodVoice) " · follows her mood" else ""
    }

    /** A time in the phone's 12- or 24-hour style. */
    private fun timeLabel(h: Int, m: Int): String =
        if (DateFormat.is24HourFormat(this)) "%02d:%02d".format(h, m)
        else "%d:%02d %s".format(if (h % 12 == 0) 12 else h % 12, m, if (h < 12) "AM" else "PM")

    /** Asks Android to add the Talk to Lena tile to Quick Settings; Android shows its own confirmation. */
    private fun addTile() {
        getSystemService(StatusBarManager::class.java).requestAddTileService(
            ComponentName(this, LenaTile::class.java),
            getString(R.string.tile_label),
            PlatformIcon.createWithResource(this, R.drawable.ic_glyph_talk),
            mainExecutor,
        ) { }
    }

    /** Asks the launcher to place Lena's widget, where the launcher supports that. */
    private fun addWidget() {
        val manager = getSystemService(AppWidgetManager::class.java)
        if (manager.isRequestPinAppWidgetSupported) {
            manager.requestPinAppWidget(ComponentName(this, LenaWidgetReceiver::class.java), null, null)
        }
    }
}
