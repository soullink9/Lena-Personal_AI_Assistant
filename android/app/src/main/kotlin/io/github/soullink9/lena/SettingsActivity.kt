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
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.launch

/** Lena's own settings, as a native screen, grouped into cards. */
class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { LenaTheme { Screen() } }
    }

    @Composable
    private fun Screen() {
        val scope = rememberCoroutineScope()
        val original = remember { Prefs.load(this) }
        var s by remember { mutableStateOf(original) }
        var serverError by remember { mutableStateOf<String?>(null) }
        var signInError by remember { mutableStateOf<String?>(null) }
        var models by remember { mutableStateOf<List<Model>>(emptyList()) }
        var modelMenu by remember { mutableStateOf(false) }
        var newPrompt by remember { mutableStateOf("") }
        var haError by remember { mutableStateOf<String?>(null) }
        var haToken by remember { mutableStateOf("") } // a new token to save; the saved one is never shown
        var haTokenSaved by remember { mutableStateOf(Secrets.has(this, Secrets.HA_TOKEN)) }
        var haCheck by remember { mutableStateOf<String?>(null) }
        val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
        val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

        LaunchedEffect(Unit) {
            Api.from(this@SettingsActivity)?.let { api ->
                models = runCatching { api.models() }.getOrDefault(emptyList())
                if (models.isNotEmpty()) Prefs.rememberLena(this@SettingsActivity, models)
            }
        }

        fun askForNotifications() {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        fun save() {
            val server = Prefs.normalize(s.server)
            val signIn = Prefs.normalizeSignIn(s.signIn)
            val ha = if (s.haServer.isBlank()) "" else Prefs.normalize(s.haServer)
            serverError = if (server == null) "Use your https:// address" else null
            signInError = if (signIn == null) "Use https:// addresses" else null
            haError = if (ha == null) "Use your https:// address" else null
            if (server == null || signIn == null || ha == null) return
            val saved = s.copy(server = server, signIn = signIn, haServer = ha)
            Prefs.save(this, saved)
            if (haToken.isNotBlank()) Secrets.put(this, Secrets.HA_TOKEN, haToken.trim())
            WidgetMoodWorker.sync(this)
            Schedules.rescheduleAll(this)
            Shortcuts.update(this)
            if (saved.notifyReplies || saved.schedules.any { it.enabled }) askForNotifications()
            scope.launch { runCatching { LenaWidget().updateAll(this@SettingsActivity) } }
            if (server != original.server || signIn != original.signIn) {
                startActivity(
                    Intent(this, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                )
            }
            finish()
        }

        Scaffold(
            modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
            topBar = {
                LargeTopAppBar(
                    title = { Text("Settings") },
                    navigationIcon = {
                        IconButton(onClick = { finish() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                    },
                    actions = {
                        FilledTonalButton(onClick = { save() }, modifier = Modifier.padding(end = 8.dp)) { Text("Save") }
                    },
                    scrollBehavior = scroll,
                )
            },
        ) { padding ->
            Column(
                Modifier.padding(padding).verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp).padding(top = 4.dp).navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                // ---- Your servers ----------------------------------------------------------
                Group("Open WebUI", Icons.Outlined.Link) {
                    Field(
                        value = s.server,
                        onChange = { s = s.copy(server = it) },
                        label = "Address",
                        placeholder = "https://ai.example.com",
                        error = serverError,
                        uri = true,
                    )
                    Field(
                        value = s.signIn,
                        onChange = { s = s.copy(signIn = it) },
                        label = "Sign-in address (optional)",
                        placeholder = "https://auth.example.com",
                        error = signInError,
                        help = "Only if you sign in through another site, such as Authentik. Separate several with spaces.",
                        uri = true,
                    )
                }

                Group("Home Assistant", Icons.Outlined.Home) {
                    Note("Optional. Where her feelings and shared memory live, so the app can show how she feels and let you edit her memory.")
                    Field(
                        value = s.haServer,
                        onChange = { s = s.copy(haServer = it); haCheck = null },
                        label = "Address",
                        placeholder = "https://ha.example.com",
                        error = haError,
                        uri = true,
                    )
                    OutlinedTextField(
                        value = haToken,
                        onValueChange = { haToken = it; haCheck = null },
                        label = { Text(if (haTokenSaved) "Access token (saved)" else "Long-lived access token") },
                        placeholder = { Text(if (haTokenSaved) "Paste a new one to replace it" else "Your profile → Security") },
                        supportingText = { Text("Stored encrypted on this phone and sent only to your Home Assistant.") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            if (haTokenSaved) IconButton(onClick = {
                                Secrets.put(this@SettingsActivity, Secrets.HA_TOKEN, null)
                                haTokenSaved = false
                                haToken = ""
                                haCheck = null
                            }) { Icon(Icons.Outlined.Delete, "Remove the saved token") }
                        },
                        modifier = fieldModifier,
                    )
                    Field(
                        value = s.haMemoryList,
                        onChange = { s = s.copy(haMemoryList = it) },
                        label = "Her memory list",
                        help = "The to-do list entity, e.g. $DEFAULT_MEMORY_LIST",
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    ) {
                        OutlinedButton(
                            enabled = s.haServer.isNotBlank() && (haToken.isNotBlank() || haTokenSaved),
                            onClick = {
                                val url = Prefs.normalize(s.haServer)
                                val token = haToken.trim().ifBlank { Secrets.get(this@SettingsActivity, Secrets.HA_TOKEN) ?: "" }
                                if (url == null) haError = "Use your https:// address"
                                else scope.launch {
                                    haError = null
                                    haCheck = "Checking…"
                                    val house = HomeAssistant(url, token)
                                    haCheck = if (!house.check()) "Couldn't connect. Check the address and token."
                                    else when (val f = runCatching { house.feelings() }.getOrNull()) {
                                        null -> "Connected. Her feelings helpers weren't found."
                                        else -> "Connected. She's ${f.mood.lowercase()}."
                                    }
                                }
                            },
                        ) { Text("Test connection") }
                        haCheck?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f))
                        }
                    }
                }

                // ---- Chats -----------------------------------------------------------------
                Group("Chats", Icons.Outlined.Forum) {
                    Box {
                        OutlinedTextField(
                            value = s.model,
                            onValueChange = { s = s.copy(model = it) },
                            label = { Text("Model for new chats") },
                            placeholder = { Text("Lena, if your server has her") },
                            supportingText = {
                                Text(models.firstOrNull { it.id == s.model }?.name
                                    ?: "Empty uses Lena (the model built from the Modelfile), or Open WebUI's default if there isn't one.")
                            },
                            singleLine = true,
                            trailingIcon = {
                                if (models.isNotEmpty()) IconButton(onClick = { modelMenu = true }) { Icon(Icons.Rounded.ExpandMore, "Pick a model") }
                            },
                            modifier = fieldModifier,
                        )
                        DropdownMenu(expanded = modelMenu, onDismissRequest = { modelMenu = false }) {
                            DropdownMenuItem(text = { Text("Lena, if your server has her") }, onClick = { s = s.copy(model = ""); modelMenu = false })
                            models.forEach { m ->
                                DropdownMenuItem(text = { Text(m.name) }, onClick = { s = s.copy(model = m.id); modelMenu = false })
                            }
                        }
                    }
                    Divider()
                    Toggle(
                        "Notify me when a reply finishes",
                        "If you leave Lena while she's still answering.",
                        s.notifyReplies,
                    ) { s = s.copy(notifyReplies = it) }
                    Divider()
                    Toggle(
                        "Show Open WebUI's own menus",
                        "Its header and sidebar. Hidden by default, since Lena has her own top bar and drawer.",
                        s.openWebUiMenus,
                    ) { s = s.copy(openWebUiMenus = it) }
                }

                // ---- The assistant -----------------------------------------------------------
                Group("Assistant gesture", Icons.Outlined.TouchApp) {
                    Choice("Talk to Lena", "Listens straight away, over whatever you're doing", s.assist == AssistAction.ASK) {
                        s = s.copy(assist = AssistAction.ASK)
                    }
                    Choice("Type to Lena", "The same sheet, with the keyboard", s.assist == AssistAction.TYPE) {
                        s = s.copy(assist = AssistAction.TYPE)
                    }
                    Choice("Voice call", "Straight into a voice call", s.assist == AssistAction.CALL) {
                        s = s.copy(assist = AssistAction.CALL)
                    }
                    Choice("New chat", "Opens Lena on a new chat", s.assist == AssistAction.CHAT) {
                        s = s.copy(assist = AssistAction.CHAT)
                    }
                    Divider()
                    Toggle(
                        "On the lock screen",
                        "Opens her without unlocking, in a private mode: earlier conversations stay hidden and nothing opens until " +
                            "you unlock. Anyone holding your phone can still talk to her.",
                        s.lockScreen,
                    ) { s = s.copy(lockScreen = it) }
                    OutlinedButton(
                        onClick = {
                            try {
                                startActivity(Intent(AndroidSettings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
                            } catch (_: ActivityNotFoundException) {
                            }
                        },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    ) { Text("Make Lena your digital assistant") }
                }

                Group("Talking to Lena", Icons.Outlined.RecordVoiceOver) {
                    val phoneEar = remember { PhoneEar.available(this@SettingsActivity) }
                    Subheading("Hearing you")
                    Choice(
                        "On this phone",
                        if (phoneEar) "Shows your words as you speak. The audio never leaves the phone."
                        else "Not available on this phone; your server is used instead.",
                        s.hearing == Hearing.PHONE,
                    ) { s = s.copy(hearing = Hearing.PHONE) }
                    Choice("Your server", "Open WebUI's speech-to-text, the same as call mode", s.hearing == Hearing.SERVER) {
                        s = s.copy(hearing = Hearing.SERVER)
                    }
                    Divider()
                    Subheading("Her voice")
                    Choice("Your server's voice", "Open WebUI's text-to-speech, the same as call mode", s.speaking == Speaking.SERVER) {
                        s = s.copy(speaking = Speaking.SERVER)
                    }
                    Choice("The phone's voice", "Android's text-to-speech", s.speaking == Speaking.PHONE) {
                        s = s.copy(speaking = Speaking.PHONE)
                    }
                    Choice("Don't read replies aloud", "Show them only", s.speaking == Speaking.OFF) {
                        s = s.copy(speaking = Speaking.OFF)
                    }
                    Toggle(
                        "Her voice follows her mood",
                        "A little slower when she's groggy, quicker and brighter when she's wired or cheerful. Needs Home Assistant.",
                        s.moodVoice,
                    ) { s = s.copy(moodVoice = it) }
                    Divider()
                    Subheading("Keep listening")
                    Choice("After every reply", "Talk back without tapping. Stays quiet if you say nothing.",
                        s.followUp == FollowUp.ALWAYS) { s = s.copy(followUp = FollowUp.ALWAYS) }
                    Choice("Only when she asks a question", "Like Home Assistant's \"continue conversation\"",
                        s.followUp == FollowUp.QUESTIONS) { s = s.copy(followUp = FollowUp.QUESTIONS) }
                    Choice("Off", "Tap the mic to talk again", s.followUp == FollowUp.OFF) { s = s.copy(followUp = FollowUp.OFF) }
                    Divider()
                    Toggle(
                        "One ongoing conversation",
                        "Talking to Lena always continues the same chat, so she remembers earlier conversations. " +
                            "Start over with New conversation in the sheet.",
                        s.ongoing,
                    ) { s = s.copy(ongoing = it) }
                    if (s.ongoing) {
                        var contextText by remember { mutableStateOf(s.contextTokens.toString()) }
                        OutlinedTextField(
                            value = contextText,
                            onValueChange = { v ->
                                contextText = v.filter { it.isDigit() }.take(7)
                                contextText.toIntOrNull()?.let { s = s.copy(contextTokens = it) }
                            },
                            label = { Text("Her context size (tokens)") },
                            supportingText = {
                                Text("Half holds the conversation; the rest is for her prompt, tools and reply. Match num_ctx in the Modelfile.")
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = fieldModifier,
                        )
                    }
                }

                // ---- Prompts -----------------------------------------------------------------
                Group("Quick prompts", Icons.Outlined.Bolt) {
                    Note("One tap from the sheet, the widget and the app icon's long-press menu.")
                    s.quickPrompts.forEachIndexed { i, p ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp, end = 4.dp)) {
                            Text(p, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            IconButton(onClick = { s = s.copy(quickPrompts = s.quickPrompts.filterIndexed { j, _ -> j != i }) }) {
                                Icon(Icons.Outlined.Delete, "Remove")
                            }
                        }
                    }
                    if (s.quickPrompts.size < 6) {
                        OutlinedTextField(
                            value = newPrompt,
                            onValueChange = { newPrompt = it },
                            placeholder = { Text("e.g. What's my day look like?") },
                            singleLine = true,
                            trailingIcon = {
                                IconButton(enabled = newPrompt.isNotBlank(), onClick = {
                                    s = s.copy(quickPrompts = s.quickPrompts + newPrompt.trim())
                                    newPrompt = ""
                                }) { Icon(Icons.Outlined.Add, "Add") }
                            },
                            modifier = fieldModifier,
                        )
                    }
                }

                Group("Scheduled prompts", Icons.Outlined.Schedule) {
                    Note("Lena asks these on her own every day and notifies you with the answer. They're saved as chats.")
                    s.schedules.forEachIndexed { i, sch ->
                        if (i > 0) Divider()
                        ScheduleRow(
                            sch,
                            onChange = { changed -> s = s.copy(schedules = s.schedules.mapIndexed { j, o -> if (j == i) changed else o }) },
                            onDelete = { s = s.copy(schedules = s.schedules.filterIndexed { j, _ -> j != i }) },
                        )
                    }
                    TextButton(
                        onClick = { s = s.copy(schedules = s.schedules + Schedule(prompt = "Give me my morning briefing.")) },
                        modifier = Modifier.padding(horizontal = 8.dp),
                    ) {
                        Icon(Icons.Outlined.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Add a scheduled prompt")
                    }
                }

                // ---- The phone ---------------------------------------------------------------
                Group("On your phone", Icons.Outlined.Smartphone) {
                    Toggle(
                        "Her mood on the widget",
                        "Updated every half hour. Only the mood, never the reasons. Needs Home Assistant.",
                        s.widgetMood,
                    ) { s = s.copy(widgetMood = it) }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        OutlinedButton(onClick = { addWidget() }) { Text("Add widget") }
                        OutlinedButton(onClick = { addTile() }) { Text("Add Quick Settings tile") }
                    }
                }

                // ---- About -------------------------------------------------------------------
                val version = remember { runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 24.dp),
                ) {
                    LenaAvatar(56.dp)
                    Spacer(Modifier.height(8.dp))
                    Text("Lena ${version ?: ""}", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Your own assistant, on your own hardware",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    // ---- Building blocks --------------------------------------------------------------------

    private val fieldModifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)

    /** A titled card holding related settings. */
    @Composable
    private fun Group(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
            ) {
                Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            }
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(vertical = 8.dp), content = content)
            }
        }
    }

    @Composable
    private fun Field(
        value: String,
        onChange: (String) -> Unit,
        label: String,
        placeholder: String? = null,
        error: String? = null,
        help: String? = null,
        uri: Boolean = false,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text(label) },
            placeholder = if (placeholder != null) {
                { Text(placeholder) }
            } else null,
            isError = error != null,
            supportingText = if (error != null || help != null) {
                { Text(error ?: help ?: "") }
            } else null,
            singleLine = true,
            keyboardOptions = if (uri) KeyboardOptions(keyboardType = KeyboardType.Uri) else KeyboardOptions.Default,
            modifier = fieldModifier,
        )
    }

    @Composable
    private fun Note(text: String) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }

    @Composable
    private fun Subheading(text: String) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
        )
    }

    @Composable
    private fun Divider() {
        HorizontalDivider(
            Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
        )
    }

    @Composable
    private fun Choice(title: String, detail: String, selected: Boolean, onSelect: () -> Unit) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
                .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            RadioButton(selected = selected, onClick = null)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    @Composable
    private fun Toggle(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Column(Modifier.weight(1f).padding(end = 16.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }

    @Composable
    private fun ScheduleRow(s: Schedule, onChange: (Schedule) -> Unit, onDelete: () -> Unit) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = {
                    TimePickerDialog(
                        this@SettingsActivity,
                        { _, h, m -> onChange(s.copy(hour = h, minute = m)) },
                        s.hour, s.minute, DateFormat.is24HourFormat(this@SettingsActivity),
                    ).show()
                }) {
                    Text(timeLabel(s.hour, s.minute), style = MaterialTheme.typography.titleLarge)
                }
                Text("every day", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Switch(checked = s.enabled, onCheckedChange = { onChange(s.copy(enabled = it)) })
                IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, "Remove") }
            }
            OutlinedTextField(
                value = s.prompt,
                onValueChange = { onChange(s.copy(prompt = it)) },
                label = { Text("Prompt") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
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
