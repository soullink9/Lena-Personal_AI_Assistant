@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.soullink9.lena

import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Tune
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Close
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.delay
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Speaker
import androidx.compose.material.icons.outlined.AutoFixHigh
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
import androidx.compose.material.icons.outlined.SettingsBackupRestore
import androidx.compose.material.icons.outlined.Assistant
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
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

    companion object {
        /** Opens Settings straight on Alarms & timers (from Android's alarm icon and the clock). */
        const val ACTION_ALARMS = "io.github.soullink9.lena.ALARMS"
    }

    /**
     * The pages of Settings. One activity shows them all, so changes save as you go and the update download
     * carries on while you look at other pages.
     */
    private enum class Page(val title: String, val section: Area?) {
        MAIN("Settings", null),
        OPEN_WEBUI("Open WebUI", Area.CONNECTIONS), HOME_ASSISTANT("Home Assistant", Area.CONNECTIONS),
        WHO_ANSWERS("Who answers", Area.CONNECTIONS), MODEL("Model", Area.CONNECTIONS),
        WALL_IDLE("Screensaver", Area.WALL), WALL_DASHBOARDS("Dashboards", Area.WALL),
        WAKE("Wake word", Area.TALKING), LOOK_SOUND("How she looks and sounds", Area.TALKING), GESTURE("Interacting with Lena", Area.TALKING),
        CHAT_SCREEN("Chat screen", Area.CHAT_SCREEN),
        NOTIFICATIONS("Notifications", Area.DEVICE), PHONE("Widgets & tile", Area.DEVICE),
        QUICK("Quick prompts", Area.DEVICE), SCHEDULED("Scheduled prompts", Area.DEVICE), SCHEDULE("Scheduled prompt", Area.DEVICE),
        ALARMS("Alarms & timers", Area.DEVICE),
        DEVELOPMENT("Development", Area.ABOUT), UPDATES("Updates", Area.ABOUT);

        /**
         * Where back goes: a scheduled prompt to the list, Updates to Development (the hidden page behind
         * Software version), everything else to the main list.
         */
        val parent: Page?
            get() = when (this) {
                MAIN -> null
                SCHEDULE -> SCHEDULED
                UPDATES -> DEVELOPMENT
                else -> MAIN
            }

        val depth: Int get() = parent?.let { it.depth + 1 } ?: 0
    }

    /** The sections of the main list, in order; each row in them opens a page (or Android's settings). */
    private enum class Area(val title: String) {
        CONNECTIONS("Connections"), WALL("Wall display"), TALKING("Talking to Lena"),
        CHAT_SCREEN("Chat screen configuration"), DEVICE("Device customization"), ABOUT("About"),
    }

    private lateinit var original: Settings

    /** Every touch or key: someone's using the wall display, so its screensaver waits (see [WallDisplay]). */
    override fun onUserInteraction() {
        super.onUserInteraction()
        WallDisplay.touched()
    }

    /** Keeps the settings as they were on arrival, to tell on leaving whether the server changed. */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WallDisplay.watch(this)
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
        var page by remember { mutableStateOf(if (intent.action == ACTION_ALARMS) Page.ALARMS else Page.MAIN) }
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
                // The whole screen scrolls, side margins included; the page inside is centred and no wider than
                // PAGE_MAX_WIDTH, so it stays readable on an unfolded screen.
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    Column(
                        Modifier.fillMaxWidth().wrapContentWidth().widthIn(max = PAGE_MAX_WIDTH).fillMaxWidth()
                            .padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 24.dp).navigationBarsPadding(),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        when (shown) {
                            Page.MAIN -> Main(s, models, ::set, query, { query = it }) { page = it }
                            Page.OPEN_WEBUI -> OpenWebUiPage(s, ::set)
                            Page.HOME_ASSISTANT -> HomeAssistantPage(s, ::set) { page = it }
                            Page.WHO_ANSWERS -> WhoAnswersPage(s, ::set) { page = it }
                            Page.MODEL -> ModelPage(s, models, ::set)
                            Page.GESTURE -> GesturePage(s, ::set)
                            Page.LOOK_SOUND -> LookSoundPage(s, ::set)
                            Page.CHAT_SCREEN -> ChatScreenPage(s, ::set)
                            Page.QUICK -> QuickPage(s, ::set)
                            Page.SCHEDULED -> ScheduledPage(s, ::set) { id -> editing = id; page = Page.SCHEDULE }
                            Page.SCHEDULE -> SchedulePage(s, editing, ::set) { page = Page.SCHEDULED }
                            Page.NOTIFICATIONS -> NotificationsPage(s, ::set)
                            Page.PHONE -> PhonePage(s, ::set)
                            Page.WALL_DASHBOARDS -> DashboardsPage(s, ::set) { page = it }
                            Page.WALL_IDLE -> IdlePage(s, ::set) { page = it }
                            Page.WAKE -> WakePage(s, ::set)
                            Page.ALARMS -> AlarmsPage()
                            Page.DEVELOPMENT -> DevelopmentPage(s, ::set) { page = it }
                            Page.UPDATES -> UpdatesPage()
                        }
                    }
                }
            }
        }
    }

    // ---- The list ---------------------------------------------------------------------------

    /**
     * The main list: a search box, then sections (Connections, Wall display, Talking to Lena, Chat screen
     * configuration, Device customization, About). Every row says how it's set and opens its own page, or
     * Android's own setting for the three things only Android can change.
     */
    @Composable
    private fun Main(s: Settings, models: List<Model>, set: (Settings) -> Unit, query: String, onQuery: (String) -> Unit, open: (Page) -> Unit) {
        val house = hasHouse()
        SearchField(query, "Search settings") { onQuery(it) }
        if (query.isNotBlank()) {
            SearchResults(query, open)
            return
        }
        val modelName = models.firstOrNull { it.id == s.model }?.name ?: s.model.ifBlank { "Lena, automatically" }
        Section(Area.CONNECTIONS.title) {
            // With Assist answering, Open WebUI isn't used at all, so its rows step aside.
            val assist = s.answers == Answerer.ASSIST
            if (!assist) Item(Icons.Outlined.Language, "Open WebUI", host(s.server) ?: "Not set") { open(Page.OPEN_WEBUI) }
            Item(Icons.Outlined.Home, "Home Assistant", if (house) host(s.haServer) ?: "Connected" else "Not connected") {
                open(Page.HOME_ASSISTANT)
            }
            Item(Icons.Outlined.Assistant, "Who answers", s.answers.label) { open(Page.WHO_ANSWERS) }
            if (!assist) Item(Icons.Outlined.SmartToy, "Model", "$modelName · %,d tokens".format(s.contextTokens)) { open(Page.MODEL) }
            RefreshItem()
        }
        Section(Area.WALL.title) {
            val home = remember { DashboardActivity.isHomeScreen(this@SettingsActivity) }
            Item(Icons.Outlined.Home, "Make it this device's home screen",
                if (home) "It is: the wall display, full screen, with the screensaver" else "Makes the wall display full screen, with the screensaver",
                trailing = { OpenInAndroid() }) { openHomeSettings() }
            val dream = remember { isScreenSaver() }
            Item(Icons.Outlined.Slideshow, "Make it this device's screensaver",
                when (dream) {
                    true -> "It is: Lena shows while the device charges"
                    false -> "Show Lena while the device charges"
                    null -> "Android settings → Screen saver → Lena"
                },
                trailing = { OpenInAndroid() }) { openDreamSettings() }
            Item(Icons.Outlined.PhotoAlbum, "Screensaver", idleLabel(s)) { open(Page.WALL_IDLE) }
            Item(Icons.Outlined.Dashboard, "Dashboards",
                if (house) s.dashboards.joinToString(", ") { it.name.ifBlank { it.path } } else "Connect Home Assistant first") {
                open(if (house) Page.WALL_DASHBOARDS else Page.HOME_ASSISTANT)
            }
        }
        Section(Area.TALKING.title) {
            val assistant = remember { isDigitalAssistant() }
            Item(Icons.Outlined.Assistant, "Make Lena your digital assistant",
                if (assistant) "She is: the assistant gesture opens her" else "So the assistant gesture opens her",
                trailing = { OpenInAndroid() }) { openAssistantSettings() }
            Item(Icons.Outlined.Hearing, "Wake word", wakeLabel(s)) { open(Page.WAKE) }
            Item(Icons.Outlined.RecordVoiceOver, "How she looks and sounds", "${s.askStyle.label} · ${voiceLabel(s)}") { open(Page.LOOK_SOUND) }
            Item(Icons.Outlined.TouchApp, "Interacting with Lena", gestureLabel(s.assist) + if (s.lockScreen) " · on the lock screen" else "") {
                open(Page.GESTURE)
            }
        }
        Section(Area.CHAT_SCREEN.title) {
            Item(Icons.Outlined.ViewSidebar, "Chat screen",
                if (s.answers == Answerer.ASSIST) (if (s.homeSwitch) "Chat | Home switch on" else "Chat | Home switch off") else chatScreenLabel(s)) {
                open(Page.CHAT_SCREEN)
            }
        }
        Section(Area.DEVICE.title) {
            Item(Icons.Outlined.Notifications, "Notifications", notificationsLabel(s)) { open(Page.NOTIFICATIONS) }
            Item(Icons.Outlined.Widgets, "Widgets & tile", if (s.widgetMood) "Widget shows her mood" else "Talk to Lena from the home screen or the shade") {
                open(Page.PHONE)
            }
            Item(Icons.Outlined.Bolt, "Quick prompts", count(s.quickPrompts.size, "prompt", "None yet")) { open(Page.QUICK) }
            Item(Icons.Outlined.Schedule, "Scheduled prompts",
                if (s.schedules.isEmpty()) "None yet" else s.schedules.filter { it.enabled }.joinToString(", ") { it.title }.ifBlank { "All paused" }) {
                open(Page.SCHEDULED)
            }
            val countdowns by Timers.items(this@SettingsActivity).collectAsState()
            Item(Icons.Outlined.Alarm, "Alarms & timers", countdowns.orEmpty().let { list ->
                if (list.isEmpty()) "None set" else list.joinToString(", ") { it.name(this@SettingsActivity) }
            }) { open(Page.ALARMS) }
        }
        Section(Area.ABOUT.title) {
            // Opens the Development page (Updates, Logs, Return to defaults), which has no row of its own.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = rowModifier().clickable { open(Page.DEVELOPMENT) }.padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
                LenaAvatar(32.dp)
                Spacer(Modifier.width(18.dp))
                Column {
                    Text("Software version", style = MaterialTheme.typography.titleMedium)
                    Text("Lena ${appVersion() ?: ""} · your own assistant, on your own hardware", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    /**
     * Alarms & timers: Lena's own, set here or by asking her ("set a 10 minute timer", "wake me up at 6:30").
     * Each shows when it rings and can be cancelled; quick timers and an alarm can be set right here.
     */
    @Composable
    private fun AlarmsPage() {
        val items by Timers.items(this).collectAsState()
        var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
        LaunchedEffect(Unit) {
            while (true) {
                now = System.currentTimeMillis()
                delay(1_000)
            }
        }
        val list = items.orEmpty()
        if (list.isNotEmpty()) Group {
            list.forEach { item ->
                val summary = when (item.kind) {
                    CountdownKind.TIMER -> "${Timers.countdown(item.at - now)} left · rings at ${Timers.clock(this@SettingsActivity, item.at)}"
                    CountdownKind.ALARM -> "Rings ${Timers.day(item.at)}"
                    CountdownKind.REMINDER -> "At ${Timers.clock(this@SettingsActivity, item.at)} ${Timers.day(item.at)}"
                }
                Item(
                    countdownIcon(item.kind),
                    item.name(this@SettingsActivity), summary,
                    trailing = {
                        IconButton(onClick = { Timers.cancelEverywhere(this@SettingsActivity, item.id) }) { Icon(Icons.Outlined.Close, "Cancel") }
                    },
                ) {}
            }
        }
        Group {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = rowModifier().padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                Icon(Icons.Outlined.Timer, null, Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                listOf(1, 5, 10, 15).forEach { minutes ->
                    AssistChip(onClick = { Timers.addTimer(this@SettingsActivity, minutes * 60_000L) }, label = { Text("$minutes min") })
                }
            }
            Item(Icons.Outlined.Alarm, "Set an alarm", "Rings at the next time you pick") {
                val at = java.util.Calendar.getInstance()
                android.app.TimePickerDialog(
                    this@SettingsActivity,
                    { _, hour, minute -> Timers.addAlarm(this@SettingsActivity, hour, minute) },
                    at.get(java.util.Calendar.HOUR_OF_DAY), at.get(java.util.Calendar.MINUTE),
                    android.text.format.DateFormat.is24HourFormat(this@SettingsActivity),
                ).show()
            }
        }
        Footnote("Or ask her: “set a 10 minute pasta timer”, “wake me up at 6:30”, “how long is left?”, “cancel my alarm”. " +
            "They ring on this device with the alarm sound, even while it sleeps, and show on the dashboards and the screensaver. " +
            "Say “stop” or tap Stop when one rings; an alarm can snooze for 10 minutes.")
    }

    /**
     * Development: the page behind Software version, for keeping the app itself in order rather than for
     * everyday settings. Updates, Logs and Return to defaults.
     */
    @Composable
    private fun DevelopmentPage(s: Settings, set: (Settings) -> Unit, open: (Page) -> Unit) {
        var resetting by askReset
        if (resetting) ResetDialog(
            onConfirm = {
                resetting = false
                set(Prefs.defaultsKeeping(s))
                Toast.makeText(this, "Back to defaults", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { resetting = false },
        )
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
            }) { openLogs() }
            Item(Icons.Outlined.SettingsBackupRestore, "Return to defaults", "Timings, wake word options, look and feel; keeps your connections") {
                resetting = true
            }
        }
        OptimizeMemory()
    }

    /**
     * Optimize memory: runs Lena's memory tidy now (combining, clarifying and retiring lines, as she does by
     * herself every week), to try it or to run it early, and shows what changed, with Undo.
     */
    @Composable
    private fun OptimizeMemory() {
        val ha = remember { HomeAssistant.from(this) } ?: return
        val scope = rememberCoroutineScope()
        var working by remember { mutableStateOf(false) }
        var result by remember { mutableStateOf<Pair<String, Boolean>?>(null) } // what happened, and whether Undo applies
        result?.let { (text, canUndo) ->
            AlertDialog(
                onDismissRequest = { result = null },
                title = { Text("Her memory") },
                text = { Text(text, modifier = Modifier.verticalScroll(rememberScrollState())) },
                confirmButton = { TextButton(onClick = { result = null }) { Text("OK") } },
                dismissButton = if (!canUndo) null else {
                    {
                        TextButton(onClick = {
                            result = null
                            scope.launch {
                                result = try {
                                    ha.undoMemoryTidy()
                                    "Back as it was before the tidy." to false
                                } catch (e: HaHttpException) {
                                    (e.detail ?: "Couldn't undo it.") to false
                                } catch (e: Exception) {
                                    "Couldn't reach Home Assistant." to false
                                }
                            }
                        }) { Text("Undo") }
                    }
                },
            )
        }
        Group {
            Item(Icons.Outlined.AutoFixHigh, "Optimize memory",
                if (working) "Optimizing… her model can take a minute" else "Tidy her memory now instead of waiting for her weekly tidy") {
                if (working) return@Item
                working = true
                scope.launch {
                    result = try {
                        val (changed, summary) = ha.tidyMemory()
                        (if (changed) "She tidied her memory:\n\n$summary" else summary) to changed
                    } catch (e: HaHttpException) {
                        (e.detail ?: if (e.code == 400) "She couldn't tidy it this time." else
                            "Home Assistant doesn't know this yet: update the Lena integration.") to false
                    } catch (e: Exception) {
                        "Couldn't reach Home Assistant." to false
                    } finally {
                        working = false
                    }
                }
            }
        }
        Footnote("Optimize memory asks her model (Home Assistant's AI Task) to combine lines about the same thing, make vague ones " +
            "clear and tick off outdated ones. You'll see exactly what changed, and Undo puts it back.")
    }

    /** Whether the Return to defaults question is showing. */
    private val askReset = mutableStateOf(false)

    /** Asks before Return to defaults, saying what changes and what's kept. */
    @Composable
    private fun ResetDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Return to defaults?") },
            text = {
                Text(
                    "Timings, the wake word's options, how she looks and sounds, the chat screen, notifications and the " +
                        "screensaver's options go back to how a new install starts.\n\nKept: your Open WebUI and Home Assistant " +
                        "connections, the model and context size, your dashboards, the screensaver's album, whether she listens " +
                        "for her name, your quick and scheduled prompts, and your order of pinned chats and folders."
                )
            },
            confirmButton = {
                TextButton(colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error), onClick = onConfirm) {
                    Text("Return to defaults")
                }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        )
    }

    /**
     * Refresh all connections: checks Open WebUI and Home Assistant again and says how each answered, and
     * has your chats and the dashboards reload their pages when you go back to them. (It replaces the
     * reload buttons that were in the menu and on the wall display.)
     */
    @Composable
    private fun RefreshItem() {
        val scope = rememberCoroutineScope()
        var result by remember { mutableStateOf<String?>(null) }
        Item(Icons.Outlined.Refresh, "Refresh all connections", result ?: "Checks both servers and reloads your chats and dashboards") {
            Refresh.request()
            result = "Checking…"
            scope.launch {
                val chat = Api.from(this@SettingsActivity)?.version()?.let { "Open WebUI $it" }
                    ?: if (Prefs.server(this@SettingsActivity) == null) "No Open WebUI" else "Open WebUI unreachable"
                val house = HomeAssistant.from(this@SettingsActivity)?.let { if (it.check()) "Home Assistant connected" else "Home Assistant unreachable" }
                    ?: "No Home Assistant"
                result = "$chat · $house · pages reload when you go back"
            }
        }
    }

    /** The small arrow on rows that open Android's own settings rather than a page of Lena's. */
    @Composable
    private fun OpenInAndroid() {
        Icon(Icons.AutoMirrored.Outlined.OpenInNew, "Opens Android's settings",
            tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 16.dp).size(20.dp))
    }

    /** Android's choice of home app (Lena's wall display is dashboard mode). */
    private fun openHomeSettings() {
        try {
            startActivity(Intent(AndroidSettings.ACTION_HOME_SETTINGS))
        } catch (_: ActivityNotFoundException) {
            startActivity(Intent(AndroidSettings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
        }
    }

    /** Android's screen saver settings, where Lena can be picked. */
    private fun openDreamSettings() {
        try {
            startActivity(Intent(AndroidSettings.ACTION_DREAM_SETTINGS))
        } catch (_: ActivityNotFoundException) {
        }
    }

    /** Android's default apps, where the digital assistant is chosen. */
    private fun openAssistantSettings() {
        try {
            startActivity(Intent(AndroidSettings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
        } catch (_: ActivityNotFoundException) {
        }
    }

    /** Whether Lena is the digital assistant (Android's assistant role). */
    private fun isDigitalAssistant(): Boolean =
        runCatching { getSystemService(android.app.role.RoleManager::class.java).isRoleHeld(android.app.role.RoleManager.ROLE_ASSISTANT) }
            .getOrDefault(false)

    /**
     * Whether Lena is Android's screen saver, or null when Android won't say (it keeps that setting
     * private on some devices).
     */
    private fun isScreenSaver(): Boolean? = runCatching {
        AndroidSettings.Secure.getString(contentResolver, "screensaver_components")?.contains(packageName)
    }.getOrNull()

    /** This app's version, as shown in About. */
    private fun appVersion(): String? = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull()

    private fun openLogs() = startActivity(Intent(this, LogsActivity::class.java))

    // ---- Search -----------------------------------------------------------------------------

    /**
     * One setting the search box can find: its name, the words someone might look for it by, and where it
     * is: a page, or (with run) something that opens elsewhere, like Logs or Android's own settings.
     */
    private class Findable(
        val title: String,
        val words: String,
        val page: Page?,
        val section: Area? = page?.section,
        val run: (() -> Unit)? = null,
    )

    /**
     * Every setting, by name and by what people call it. Keep this in step with the pages: a new setting
     * goes here too, under the page it's on.
     */
    private val findable by lazy {
        listOf(
            Findable("Open WebUI address", "server url https chat connect", Page.OPEN_WEBUI),
            Findable("Sign-in address", "authentik proxy sso single sign-on login", Page.OPEN_WEBUI),
            Findable("Sign in by herself", "auto sign-in automatic login password email ldap signed out session restart", Page.OPEN_WEBUI),
            Findable("Home Assistant address and token", "ha url connect long-lived access token test connection integration", Page.HOME_ASSISTANT),
            Findable("This display's room", "area room satellite voice preview where location", Page.HOME_ASSISTANT),
            Findable("Music plays on", "music speaker music assistant media player whole home audio radio", Page.HOME_ASSISTANT),
            Findable("Who answers", "open webui home assistant assist conversation agent voice assistant answers through", Page.WHO_ANSWERS),
            Findable("Assist's conversation agent", "home assistant assist agent ollama openai google conversation", Page.WHO_ANSWERS),
            Findable("Model", "llm ollama default model lena gemma new chats", Page.MODEL),
            Findable("Refresh all connections", "reload refresh reconnect check server page dashboard", null, Area.CONNECTIONS) {
                Refresh.request()
                Toast.makeText(this, "Your chats and dashboards reload when you go back to them", Toast.LENGTH_SHORT).show()
            },
            Findable("Assistant Lena chat", "conversation history new conversation one chat", Page.MODEL),
            Findable("Her context size", "tokens num_ctx memory length context window conversation", Page.MODEL),
            Findable("Make it this device's home screen", "home app launcher dashboard mode full screen kiosk tablet", null, Area.WALL, ::openHomeSettings),
            Findable("Make it this device's screensaver", "android screen saver dream charging daydream", null, Area.WALL, ::openDreamSettings),
            Findable("Screensaver", "idle dim timeout sleep when no one's around back from your chats", Page.WALL_IDLE),
            Findable("Photo frame", "photos album immich slideshow pictures shuffle transition fit crop faces", Page.WALL_IDLE),
            Findable("Her clock", "clock night dim time weather", Page.WALL_IDLE),
            Findable("Motion sensor", "presence wake up binary_sensor motion", Page.WALL_IDLE),
            Findable("Speaking first", "briefing morning greeting motion proactive announce alert", Page.WALL_IDLE),
            Findable("Speak through", "speaker tts voice media player sonos echo home assistant speaker", Page.HOME_ASSISTANT),
            Findable("Adjust her volume", "volume quiet loud noise morning night", Page.LOOK_SOUND),
            Findable("Dashboards", "lovelace path tabs home assistant views wall display", Page.WALL_DASHBOARDS),
            Findable("Make Lena your digital assistant", "default assistant power button corner swipe gesture", null, Area.TALKING, ::openAssistantSettings),
            Findable("Wake word", "hey lena jarvis listen name hotword sensitivity false starts always wall display", Page.WAKE),
            Findable("How she looks", "orb sheet glow bar island subtitles style appearance ask overlay look", Page.LOOK_SOUND),
            Findable("Hearing you", "speech-to-text stt microphone recognition on device whisper voice", Page.LOOK_SOUND),
            Findable("Her voice", "text-to-speech tts read aloud piper speak mute sounds", Page.LOOK_SOUND),
            Findable("Her voice follows her mood", "mood voice pitch speed", Page.LOOK_SOUND),
            Findable("Keep listening", "follow-up continue conversation listen again mic", Page.LOOK_SOUND),
            Findable("The assistant gesture opens", "assistant gesture talk type new chat interacting", Page.GESTURE),
            Findable("On the lock screen", "locked privacy unlock lockscreen", Page.GESTURE),
            Findable("Lena's look for Open WebUI", "theme colours colors claude warm serif font appearance style dark light", Page.CHAT_SCREEN),
            Findable("Open WebUI's own menus", "header sidebar model selector chat controls sign out", Page.CHAT_SCREEN),
            Findable("Chat | Home switch", "dashboard switcher home assistant toggle top bar", Page.CHAT_SCREEN),
            Findable("Pinned chats in the menu", "drawer pinned how many limit sidebar order reorder", Page.CHAT_SCREEN),
            Findable("Folders in the menu", "drawer folders groups sidebar order reorder", Page.CHAT_SCREEN),
            Findable("All chats list style", "chat list slim groups flat timeline folding days drawer history appearance", Page.CHAT_SCREEN),
            Findable("Reply notifications", "lena finished notify answer done", Page.NOTIFICATIONS),
            Findable("Alerts on this phone", "worried leak alarm house warning notify", Page.NOTIFICATIONS),
            Findable("Widget", "home screen launcher mood on widget", Page.PHONE),
            Findable("Quick Settings tile", "notification shade pull-down tile talk", Page.PHONE),
            Findable("Quick prompts", "shortcuts one tap chips widget prompts", Page.QUICK),
            Findable("Scheduled prompts", "briefing morning schedule repeat daily recurring automatic", Page.SCHEDULED),
            Findable("Alarms & timers", "alarm timer wake up countdown ring snooze kitchen clock", Page.ALARMS),
        )
    }

    /** The settings that match what you typed, each with where it lives; tap one to go there. */
    @Composable
    private fun SearchResults(query: String, open: (Page) -> Unit) {
        val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        // Open WebUI's own pages don't come up while Assist answers (they aren't in the list either).
        val openWebUiOnly = if (Prefs.answers(this) == Answerer.ASSIST) setOf(Page.OPEN_WEBUI, Page.MODEL) else emptySet()
        val found = findable.filter { f -> f.page !in openWebUiOnly }.filter { f ->
            val text = listOf(f.title, f.words, f.page?.title.orEmpty(), f.section?.title.orEmpty()).joinToString(" ").lowercase()
            words.all { it in text }
        }
        if (found.isEmpty()) {
            Footnote("Nothing matches. Try another word, like voice, photos or notifications.")
            return
        }
        Group {
            found.forEach { f ->
                Item(null, f.title, where(f)) {
                    val page = f.page
                    if (page != null) open(page) else f.run?.invoke()
                }
            }
        }
    }

    /** Where a setting is, from the main list: "Wall display › Screensaver". */
    private fun where(f: Findable): String {
        val trail = generateSequence(f.page) { it.parent }.takeWhile { it != Page.MAIN }.toList().reversed().map { it.title }
        return (listOfNotNull(f.section?.title) + trail).distinct().joinToString(" › ")
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
     * Updates: what's waiting to install and the steps for it, checking, downloading, and Re-download and
     * install. Releases come from Lena's public repository, so there's nothing to set up.
     */
    @Composable
    private fun UpdatesPage() {
        val installed = remember { Updates.installed(this) }
        var pending by remember { mutableStateOf(Updates.pending(this)) }
        var mayInstall by remember { mutableStateOf(Updates.mayInstall(this)) }

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

        Footnote("This is Lena $installed. Updates come from Lena's releases on GitHub.")

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
                            upd.status = "Couldn't start the install. See Settings → Software version → Logs."
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
            Item(Icons.Outlined.Refresh, "Check for updates", upd.status ?: "From Lena's releases on GitHub") { checkForUpdate() }
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
            Item(Icons.Outlined.Dashboard, "Wall display dashboards", "Your dashboards on a tablet") { open(Page.WALL_DASHBOARDS) }
        }
        DisplayRoom(scope)
        Footnote("Home Assistant also gives the wall display its dashboards and photos, and the Chat | Home switch somewhere to go.")
    }

    /**
     * This display in Home Assistant ([Room]): the room it's in (set in Home Assistant, on its device under
     * Lena) and where music plays when no room is named.
     */
    @Composable
    private fun DisplayRoom(scope: kotlinx.coroutines.CoroutineScope) {
        val room by Room.info(this).collectAsState()
        var chosen by remember { mutableStateOf(Room.chosenSpeaker(this)) }
        var all by remember { mutableStateOf<List<Speaker>>(emptyList()) }
        var voices by remember { mutableStateOf<List<Speaker>>(emptyList()) }
        var voiceSpeaker by remember { mutableStateOf(Room.voiceSpeaker(this)) }
        var voiceEngine by remember { mutableStateOf(Room.voiceEngine(this)) }
        LaunchedEffect(Unit) {
            Room.refresh(this@SettingsActivity, force = true)
            val ha = HomeAssistant.from(this@SettingsActivity)
            all = runCatching { ha?.speakers() }.getOrNull().orEmpty()
            voices = runCatching { ha?.voices() }.getOrNull().orEmpty()
        }
        val info = room ?: return
        Group {
            Item(Icons.Outlined.Place, "This display's room",
                info.area?.let { "${info.name} is in the $it" }
                    ?: "Not in a room yet. In Home Assistant, open “${info.name}” under the Lena integration and choose its area.") {
                scope.launch { Room.refresh(this@SettingsActivity, force = true) }
            }
            val automatic = info.speakers.firstOrNull()?.let { "With the room: ${it.name}" } ?: "With the room (no speaker there yet)"
            val options = listOf("" to automatic) + (info.speakers + all).distinctBy { it.entityId }.map { it.entityId to it.name }
            PickItem(Icons.Outlined.Speaker, "Music plays on", options, chosen,
                detail = { id -> if (id.isEmpty()) "The room's Music Assistant player first" else null }) {
                chosen = it
                Room.chooseSpeaker(this@SettingsActivity, it)
            }
            // Speak through: everything she says on a Home Assistant speaker instead of this device.
            val through = listOf("" to "This device") + (info.speakers + all).distinctBy { it.entityId }.map { it.entityId to it.name }
            PickItem(Icons.Outlined.RecordVoiceOver, "Speak through", through, voiceSpeaker,
                detail = { id -> if (id.isEmpty()) "Her own voice, on this device" else null }) {
                voiceSpeaker = it
                if (it.isNotEmpty() && voiceEngine.isEmpty()) voiceEngine = voices.firstOrNull()?.entityId.orEmpty()
                Room.chooseVoiceSpeaker(this@SettingsActivity, it, voiceEngine)
            }
            if (voiceSpeaker.isNotEmpty()) {
                PickItem(Icons.Outlined.GraphicEq, "Voice on that speaker", voices.map { it.entityId to it.name }, voiceEngine,
                    detail = { null }) {
                    voiceEngine = it
                    Room.chooseVoiceSpeaker(this@SettingsActivity, voiceSpeaker, it)
                }
            }
        }
        if (voiceSpeaker.isNotEmpty() && voices.isEmpty()) Footnote("Speak through needs a text-to-speech voice in Home Assistant (such as Piper): none was found.")
        Footnote("Like Home Assistant Voice: Assist hears which room you're in, so “turn on the lights” means this room's; " +
            "Lena is told the room too, and plays music here unless you name another room (or the whole home). " +
            "Announcements (the lena.announce action) are said here.")
    }

    /** What Lena tells you about: replies you left, and worries at home. */
    @Composable
    private fun NotificationsPage(s: Settings, set: (Settings) -> Unit) {
        // Reply notifications follow Open WebUI's answers, which aren't used while Assist answers.
        if (s.answers != Answerer.ASSIST) {
            Group {
                SwitchItem(Icons.Outlined.Notifications, "Reply notifications", "When she finishes a reply you left before it was done", s.notifyReplies) {
                    set(s.copy(notifyReplies = it))
                }
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

    /**
     * Which model talks to you (Lena, found by name on your server, unless you pick another), and the one
     * Assistant Lena conversation with her context size, half of which holds that conversation.
     */
    @Composable
    private fun ModelPage(s: Settings, models: List<Model>, set: (Settings) -> Unit) {
        Group {
            if (models.isNotEmpty()) {
                PickItem(Icons.Outlined.SmartToy, "Model", listOf("" to "Lena, automatically") + models.map { it.id to it.name }, s.model,
                    detail = { id -> if (id.isEmpty()) "The model built from the Modelfile, or Open WebUI's default" else id.takeIf { m -> models.any { it.id == m && it.name != m } } },
                ) { set(s.copy(model = it)) }
            } else {
                FieldItem {
                    OutlinedTextField(
                        value = s.model, onValueChange = { set(s.copy(model = it)) },
                        label = { Text("Model ID") }, supportingText = { Text("Sign in to pick from your models, or type one, e.g. lena. Empty: Lena, automatically.") },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        Footnote("Used for new chats, and for the sheet and scheduled prompts while Open WebUI answers (Connections → Who answers).")

        Section("Conversation") {
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

    /**
     * Who answers: Open WebUI or Home Assistant's Assist, and with Assist, which of Home Assistant's
     * conversation agents. The agents are listed from Home Assistant when the page opens.
     */
    @Composable
    private fun WhoAnswersPage(s: Settings, set: (Settings) -> Unit, open: (Page) -> Unit) {
        Group {
            PickItem(Icons.Outlined.Forum, "Lena answers through", Answerer.entries.map { it to it.label }, s.answers,
                detail = { it.detail }) { set(s.copy(answers = it)) }
        }
        Footnote(
            "With Assist, everything that asks her goes to Home Assistant: talking to her (the assistant gesture, the wake word, " +
                "the tile, the widget and quick prompts), typing in the chat and scheduled prompts."
        )
        if (s.answers != Answerer.ASSIST) return
        if (!hasHouse()) {
            Group { Item(Icons.Outlined.Home, "Connect Home Assistant first", "Its address and a token") { open(Page.HOME_ASSISTANT) } }
            return
        }
        // null while loading; empty when Home Assistant couldn't be asked.
        var agents by remember { mutableStateOf<List<AssistAgent>?>(null) }
        LaunchedEffect(Unit) {
            agents = runCatching { HomeAssistant.from(this@SettingsActivity)?.agents() }
                .onFailure { Logs.w("Settings", "Couldn't list Assist's conversation agents", it) }
                .getOrNull() ?: emptyList()
        }
        Section("Assist") {
            val found = agents
            when {
                found == null -> Item(Icons.Outlined.SmartToy, "Conversation agent", "Looking in Home Assistant…") { }
                found.isEmpty() && s.assistAgent.isEmpty() ->
                    Item(Icons.Outlined.SmartToy, "Conversation agent", "Home Assistant's choice (couldn't list the others)") { }
                else -> PickItem(
                    Icons.Outlined.SmartToy, "Conversation agent",
                    listOf("" to "Home Assistant's choice") + found.map { it.id to it.name } +
                        // A saved agent that's gone (or couldn't be listed) still shows as picked.
                        listOfNotNull(s.assistAgent.takeIf { id -> id.isNotEmpty() && found.none { it.id == id } }?.let { it to it }),
                    s.assistAgent,
                    detail = { id -> if (id.isEmpty()) "The assistant set in Home Assistant → Settings → Voice assistants" else id },
                ) { set(s.copy(assistAgent = it)) }
            }
        }
        Footnote(
            "Open WebUI isn't needed: the chat screen becomes Lena's own, kept on this device, where everything you ask (typed, spoken, " +
                "quick and scheduled prompts) is kept with her full answer, and the phone hears you and speaks. Assist takes words only, so " +
                "photos are off. It can switch things in your home, so from the lock screen it waits until you unlock."
        )
    }

    /**
     * Interacting with Lena: what the assistant gesture opens, and whether it works on the lock screen, with
     * what that means for privacy.
     */
    @Composable
    private fun GesturePage(s: Settings, set: (Settings) -> Unit) {
        Group {
            PickItem(Icons.Outlined.TouchApp, "The assistant gesture opens", AssistAction.entries.map { it to gestureLabel(it) }, s.assist,
                detail = { gestureDetail(it) }) { set(s.copy(assist = it)) }
            SwitchItem(Icons.Outlined.Lock, "On the lock screen", "Talk to her without unlocking", s.lockScreen) { set(s.copy(lockScreen = it)) }
        }
        Footnote(
            "On the lock screen she starts a fresh conversation with your history hidden, and nothing opens until you unlock. " +
                "She's asked not to unlock, open or share anything personal, but that's an instruction, not a lock: anyone holding " +
                "your phone can talk to her. Leave it off if she controls locks, doors or alarms."
        )
        Footnote("For the gesture to open her at all, make Lena your digital assistant (Settings → Talking to Lena).")
    }

    /**
     * How she looks and sounds: her look when you talk to her (with Try it), who hears you, who speaks,
     * whether her voice follows her mood, and when she listens again.
     */
    @Composable
    private fun LookSoundPage(s: Settings, set: (Settings) -> Unit) {
        Section("How she looks") {
            PickItem(Icons.Outlined.Palette, "Her look", AskStyle.entries.map { it to it.label }, s.askStyle, detail = { it.detail }) {
                set(s.copy(askStyle = it))
            }
            Item(Icons.Outlined.PlayArrow, "Try it", "Opens her the way your assistant gesture does") {
                startActivity(AskActivity.intent(this@SettingsActivity, talk = s.assist != AssistAction.TYPE))
            }
        }
        Footnote("Every look listens, answers and speaks the same way. The sheet is the only one that shows earlier turns; the others show what's happening now.")
        val phoneEar = remember { PhoneEar.available(this) }
        // "Your server" is Open WebUI's speech, which isn't used while Assist answers: the phone hears and speaks.
        val assist = s.answers == Answerer.ASSIST
        Section("How she sounds") {
            val hasHa = s.haServer.isNotBlank()
            PickItem(Icons.Outlined.Hearing, "Hearing you",
                listOfNotNull(
                    Hearing.PHONE to "On this phone",
                    if (assist) null else Hearing.SERVER to "Your server",
                    if (hasHa) Hearing.HOME_ASSISTANT to "Home Assistant" else null,
                ), s.hearing,
                detail = {
                    when (it) {
                        Hearing.PHONE -> if (phoneEar) "Your words appear as you speak. The audio never leaves the phone." else "Not working on this device lately; another way is used."
                        Hearing.SERVER -> "Open WebUI's speech-to-text, the same as its voice input"
                        Hearing.HOME_ASSISTANT -> "Home Assistant's speech-to-text, the one its voice assistants use (Whisper, Speech-to-Phrase, Cloud)"
                    }
                }) { set(s.copy(hearing = it)) }
            if (hasHa && s.hearing == Hearing.HOME_ASSISTANT) {
                var engines by remember { mutableStateOf<List<Speaker>>(emptyList()) }
                var engine by remember { mutableStateOf(Room.sttEngine(this@SettingsActivity)) }
                LaunchedEffect(Unit) {
                    engines = runCatching { HomeAssistant.from(this@SettingsActivity)?.sttEngines() }.getOrNull().orEmpty()
                }
                PickItem(Icons.Outlined.GraphicEq, "Speech-to-text in Home Assistant",
                    listOf("" to "Its first one") + engines.map { it.entityId to it.name }, engine, detail = { null }) {
                    engine = it
                    Room.chooseSttEngine(this@SettingsActivity, it)
                }
            }
            PickItem(Icons.Outlined.RecordVoiceOver, "Her voice",
                listOfNotNull(
                    if (assist) null else Speaking.SERVER to "Your server's voice",
                    Speaking.PHONE to "The phone's voice",
                    Speaking.OFF to "Don't read replies aloud",
                ),
                // Your server's voice reads as the phone's while Assist answers.
                if (assist && s.speaking == Speaking.SERVER) Speaking.PHONE else s.speaking,
                detail = {
                    when (it) {
                        Speaking.SERVER -> "Open WebUI's text-to-speech, the same as reading a reply aloud there"
                        Speaking.PHONE -> "Android's text-to-speech"
                        Speaking.OFF -> "Show them only"
                    }
                }) { set(s.copy(speaking = it)) }
            SwitchItem(null, "Adjust her volume", "Quieter before 8 AM and after 10 PM, louder when the room is noisy (heard by the wake word)", s.autoVolume) {
                set(s.copy(autoVolume = it))
            }
            SwitchItem(null, "Her voice follows her mood", "Slower when she's groggy, brighter when she's cheerful. Needs Home Assistant.", s.moodVoice) {
                set(s.copy(moodVoice = it))
            }
            PickItem(Icons.Outlined.Replay, "Keep listening",
                listOf(FollowUp.ALWAYS to "After every reply", FollowUp.QUESTIONS to "When she asks a question", FollowUp.OFF to "Off"), s.followUp,
                detail = {
                    when (it) {
                        FollowUp.ALWAYS -> "Talk back without tapping. Stays quiet if you say nothing."
                        FollowUp.QUESTIONS -> "Like Home Assistant's \"continue conversation\""
                        FollowUp.OFF -> "Tap the mic to talk again"
                    }
                }) { set(s.copy(followUp = it)) }
        }
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

        // The chat a prompt goes into is an Open WebUI chat; with Assist answering, answers land in Lena's own.
        if (s.answers != Answerer.ASSIST) {
            Section("Where it goes") {
                FieldItem {
                    OutlinedTextField(
                        value = sch.chat, onValueChange = { put(sch.copy(chat = it)) },
                        label = { Text("Chat") }, placeholder = { Text("Empty: a new chat each time") }, singleLine = true,
                        supportingText = { Text("Every run goes into the chat with this name ($ASSISTANT_CHAT unless you change it), made the first time. Give several prompts the same one to share a history.") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                val others = (listOf(ASSISTANT_CHAT) + s.schedules.map { it.chat.trim() }).filter { it.isNotEmpty() && it != sch.chat.trim() }.distinct()
                if (others.isNotEmpty()) FieldItem {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        others.take(4).forEach { name -> AssistChip(onClick = { put(sch.copy(chat = name)) }, label = { Text(name) }) }
                    }
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
        val intoChat = sch.chat.isNotBlank() && Prefs.answers(this) != Answerer.ASSIST
        return whenText + if (intoChat) " · into “${sch.chat.trim()}”" else ""
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

        Footnote("When Lena is this device's home screen, the screen never goes dark, and after a while without a touch on any of Lena's screens (the dashboards, your chats, Settings) this screen saver comes on. A touch on it shows the dashboard. Anywhere else Lena is a normal app that sleeps as usual. Android's own screen saver shows the same.")
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
        Footnote("Opened your chats on a device where Lena is the home screen and walked away? After this long it goes back to the dashboards; the screen saver keeps its own time, so whichever is shorter comes first. Neither while you're typing, talking to her, or while she's still answering.")

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

        SectionTitle("Speaking first")
        var briefingSensor by remember { mutableStateOf(Room.briefingSensor(this)) }
        Group {
            FieldItem {
                OutlinedTextField(
                    value = briefingSensor,
                    onValueChange = { briefingSensor = it; Room.chooseBriefingSensor(this@SettingsActivity, it) },
                    label = { Text("Motion sensor for her briefing (optional)") },
                    placeholder = { Text("binary_sensor.kitchen_motion") },
                    supportingText = { Text("When it sees someone come in, she speaks first. It can be a different sensor from the one above, so the screen can stay a photo frame.") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Footnote("In the morning she gives a short briefing once (the weather, today's calendar, trash day, anything that needs " +
            "attention), and later in the day she mentions a worry at home once, the first time someone comes in after it starts. " +
            "Never more than one every half hour. Needs the Lena integration, and this display put in a room.")

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
        Footnote("Say her name to talk to her. It listens on this device only; nothing is recorded or sent until she hears her name.")
        Group {
            SwitchItem(Icons.Outlined.Hearing, "Listen for \u201C${s.wakePhrase.label}\u201D", null, s.wakeWord) {
                set(s.copy(wakeWord = it))
                if (it && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 3)
                }
            }
            PickItem(Icons.Outlined.Tv, "Listens", listOf(false to "Only on the wall display", true to "Whenever Lena is open"), s.wakeEverywhere,
                detail = {
                    if (it) "Also while your chats are open. Stops when you leave Lena, and while a page uses the microphone."
                    else "While the dashboards or the screensaver are showing"
                }) { set(s.copy(wakeEverywhere = it)) }
            WakeTest(s.wakePhrase)
        }
        val there = remember { WakePhrase.entries.associateWith { WakeWord.available(this@SettingsActivity, it) } }
        Group {
            PickItem(Icons.Outlined.RecordVoiceOver, "Her name", WakePhrase.entries.map { it to it.label }, s.wakePhrase,
                detail = { if (there[it] == true) it.detail else "Not in this build" },
                enabled = { there[it] == true }) { set(s.copy(wakePhrase = it)) }
            PickItem(Icons.Outlined.Tune, "How sure she must be", WakeSensitivity.entries.map { it to it.label }, s.wakeSensitivity,
                detail = { it.detail }) { set(s.copy(wakeSensitivity = it)) }
        }
        Footnote("Started by mistake? “Hey Lena” is far less likely to be heard in ordinary talk or on TV than “Lena” on its own, and " +
            "Fewer false starts makes any of them stricter. She also ignores her name until she's been listening for a second, and when " +
            "a voice check says nobody's speaking (music, a jingle, a clatter). Each start is in Settings → Software version → Logs with how sure she was.")
        Footnote("Some phones don't let an app use the microphone from the screen saver; it then says so and you tap to talk.")
    }

    /** Listens for the wake word right here and shows how close each try gets. */
    @Composable
    private fun WakeTest(phrase: WakePhrase) {
        var on by remember { mutableStateOf(false) }
        var level by remember { mutableStateOf(0f) }
        var heard by remember { mutableStateOf(0) }
        val listener = remember {
            WakeListener(this, onHeard = { heard++; on = false }, onScore = { level = it }, relay = false)
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

    /**
     * The main screen: how Open WebUI's pages look, what the top bar shows, and the menu and All chats
     * (how many pinned chats, folders, the list style).
     */
    @Composable
    private fun ChatScreenPage(s: Settings, set: (Settings) -> Unit) {
        // With Assist answering the chat screen is Lena's own: only the Chat | Home switch applies.
        if (s.answers == Answerer.ASSIST) {
            Group {
                SwitchItem(Icons.Outlined.Home, "Chat | Home switch",
                    if (hasHouse()) "At the top of the chat, to switch to your dashboards" else "Shows once Home Assistant is connected", s.homeSwitch) {
                    set(s.copy(homeSwitch = it))
                }
            }
            Footnote("The other chat screen settings are for Open WebUI's chats, which aren't used while Assist answers (Connections → Who answers).")
            return
        }
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
        Group {
            PickItem(Icons.AutoMirrored.Outlined.List, "All chats list style", ChatListStyle.entries.map { it to it.label }, s.chatList,
                detail = { it.detail }) { set(s.copy(chatList = it)) }
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

    /** The Screensaver row's summary: what it shows, and after how long. */
    private fun idleLabel(s: Settings): String =
        (if (s.slides.on && s.slides.albumName.isNotBlank()) "Photo frame: ${s.slides.albumName}" else "Her clock") +
            if (s.dimSeconds == 0) " · never on its own" else " · after ${duration(s.dimSeconds).lowercase()}"

    /** The Wake word row's summary: off, or her name and where she listens. */
    private fun wakeLabel(s: Settings): String =
        if (!s.wakeWord) "Off"
        else "\u201C${s.wakePhrase.label}\u201D · " + if (s.wakeEverywhere) "whenever Lena is open" else "on the wall display"

    /** The Voice part of a summary: who listens, who speaks, and whether it follows her mood. */
    private fun voiceLabel(s: Settings): String {
        val ear = when (s.hearing) {
            Hearing.PHONE -> "Phone listens"
            Hearing.SERVER -> "Server listens"
            Hearing.HOME_ASSISTANT -> "Home Assistant listens"
        }
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
