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
        SCHEDULED("Scheduled prompts"), PHONE("Widget & tile"), APPEARANCE("Appearance"), UPDATES("Updates"),
        WALL("Wall display"), WALL_DASHBOARDS("Dashboards"), WALL_IDLE("When no one's around"), WAKE("Wake word");

        /** Where back goes: the wall display's own pages sit under it. */
        val parent: Page?
            get() = when (this) {
                MAIN -> null
                WALL_DASHBOARDS, WALL_IDLE, WAKE -> WALL
                else -> MAIN
            }

        val depth: Int get() = parent?.let { it.depth + 1 } ?: 0
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
            Item(Icons.Outlined.Tv, "Wall display", "Dashboards when you need them, a photo frame when you don't") { open(Page.WALL) }
        }
        Group {
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
            }) { startActivity(Intent(this@SettingsActivity, LogsActivity::class.java)) }
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

    /** Update state lives with the activity, so a download carries on while you look at other pages. */
    private class UpdateState {
        var release by mutableStateOf<Updates.Release?>(null)
        var status by mutableStateOf<String?>(null)
        var progress by mutableStateOf<Float?>(null)
        var available by mutableStateOf<String?>(null)
        var checked = false
    }

    private val upd = UpdateState()

    private fun checkForUpdate() {
        lifecycleScope.launch {
            upd.status = "Checking…"
            try {
                val r = Updates.latest(this@SettingsActivity)
                upd.release = r
                val installed = Updates.installed(this@SettingsActivity)
                val newer = Updates.newer(r.version, installed)
                upd.available = if (newer) "${r.version} is available" else null
                upd.status = if (newer) "Lena ${r.version} is available" else "You have the latest ($installed)"
            } catch (e: Exception) {
                Logs.w("Updates", "Couldn't check for updates", e)
                upd.status = e.message ?: "Couldn't reach GitHub."
            }
        }
    }

    private fun download(r: Updates.Release, done: () -> Unit) {
        if (upd.progress != null) return
        lifecycleScope.launch {
            upd.progress = 0f
            try {
                Updates.download(this@SettingsActivity, r) { upd.progress = it }
                upd.status = "Downloaded and checked ${r.version}"
                done()
            } catch (e: Exception) {
                upd.status = e.message ?: "The download failed."
            } finally {
                upd.progress = null
            }
        }
    }

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
                Item(Icons.Outlined.Shield, "2. If Play Protect stops it",
                    "Pause scanning in Play Protect, then come back here and install. Lena reminds you to turn it back on afterwards.") {
                    Updates.remindPlayProtect(this@SettingsActivity, true)
                    try {
                        startActivity(Updates.playProtect())
                    } catch (_: ActivityNotFoundException) {
                        Toast.makeText(this@SettingsActivity, "Open the Play Store → your picture → Play Protect", Toast.LENGTH_LONG).show()
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

    private fun hasHouse() = Prefs.haServer(this) != null && Secrets.has(this, Secrets.HA_TOKEN)

    private fun dimLabel(m: Int) = if (m == 0) "Never" else "After $m minutes without a touch"

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
                    if (s.dimMinutes == 0) " · never steps aside" else " · after ${s.dimMinutes} min") {
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
        Footnote("Lena's strip across the top shows the time, the weather and how she feels, with buttons to talk to her, open your chats, your other apps and settings. When she's worried about something at home it turns into a banner saying what's wrong.")
    }

    /** What it shows when no one's using it, and what wakes it up. Also the screen saver. */
    @Composable
    private fun IdlePage(s: Settings, set: (Settings) -> Unit, open: (Page) -> Unit) {
        val sl = s.slides
        fun put(n: Slides) = set(s.copy(slides = n))
        var picking by remember { mutableStateOf(false) }
        val house = hasHouse()

        Footnote("After a while without a touch the dashboards step aside for this. The screen saver shows it too.")
        Group {
            PickItem(Icons.Outlined.Timer, "Step aside", listOf(2, 5, 10, 30, 0).map { it to dimLabel(it) }, s.dimMinutes) {
                set(s.copy(dimMinutes = it))
            }
        }

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
                SwitchItem(null, "Fill the screen", "Crops the edges. Off shows the whole photo on a blurred copy of itself.", sl.fill) { put(sl.copy(fill = it)) }
                SwitchItem(null, "Time and her mood on top", null, sl.overlay) { put(sl.copy(overlay = it)) }
                SwitchItem(null, "Her dim clock at night", "10 PM to 6 AM, and when she's drained, so it doesn't light up the room", sl.clockAtNight) {
                    put(sl.copy(clockAtNight = it))
                }
            }
            Footnote("Albums come from Home Assistant's media: Immich albums, people and favorites, your My media folders. Needs the Lena integration 1.2.0 or newer.")
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
