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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.launch

/** Lena's own settings, as a native screen. */
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
            topBar = {
                TopAppBar(
                    title = { Text("Settings") },
                    navigationIcon = {
                        IconButton(onClick = { finish() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                    },
                    actions = { TextButton(onClick = { save() }) { Text("Save") } },
                )
            },
        ) { padding ->
            Column(
                Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Section("Connection")
                OutlinedTextField(
                    value = s.server,
                    onValueChange = { s = s.copy(server = it) },
                    label = { Text("Open WebUI address") },
                    placeholder = { Text("https://ai.example.com") },
                    isError = serverError != null,
                    supportingText = if (serverError != null) {
                        { Text(serverError ?: "") }
                    } else null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = s.signIn,
                    onValueChange = { s = s.copy(signIn = it) },
                    label = { Text("Sign-in address (optional)") },
                    placeholder = { Text("https://auth.example.com") },
                    isError = signInError != null,
                    supportingText = {
                        Text(signInError ?: "Only if you sign in through another site, such as Authentik. Separate several with spaces.")
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )

                Section("Chats")
                Box {
                    OutlinedTextField(
                        value = s.model,
                        onValueChange = { s = s.copy(model = it) },
                        label = { Text("Model for new chats (optional)") },
                        placeholder = { Text("Lena, if your server has her") },
                        supportingText = {
                            Text(models.firstOrNull { it.id == s.model }?.name
                                ?: "Leave empty to use Lena (the model built from the Modelfile), or Open WebUI's default if there isn't one.")
                        },
                        singleLine = true,
                        trailingIcon = {
                            if (models.isNotEmpty()) IconButton(onClick = { modelMenu = true }) { Icon(Icons.Rounded.ExpandMore, "Pick a model") }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    DropdownMenu(expanded = modelMenu, onDismissRequest = { modelMenu = false }) {
                        DropdownMenuItem(text = { Text("Lena, if your server has her") }, onClick = { s = s.copy(model = ""); modelMenu = false })
                        models.forEach { m ->
                            DropdownMenuItem(text = { Text(m.name) }, onClick = { s = s.copy(model = m.id); modelMenu = false })
                        }
                    }
                }

                Section("Home Assistant (optional)")
                Text(
                    "Where Lena's feelings and shared memory live. Lena shows how she's feeling and lets you see " +
                        "and edit her shared memory. Leave empty if you don't use them.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = s.haServer,
                    onValueChange = { s = s.copy(haServer = it); haCheck = null },
                    label = { Text("Home Assistant address") },
                    placeholder = { Text("https://ha.example.com") },
                    isError = haError != null,
                    supportingText = if (haError != null) {
                        { Text(haError ?: "") }
                    } else null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = haToken,
                    onValueChange = { haToken = it; haCheck = null },
                    label = { Text(if (haTokenSaved) "Access token (saved)" else "Long-lived access token") },
                    placeholder = { Text(if (haTokenSaved) "Paste a new one to replace it" else "From your Home Assistant profile → Security") },
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
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = s.haMemoryList,
                    onValueChange = { s = s.copy(haMemoryList = it) },
                    label = { Text("Her memory list") },
                    supportingText = { Text("The to-do list entity, e.g. $DEFAULT_MEMORY_LIST") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
                    ) { Text("Test") }
                    haCheck?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f)) }
                }

                Section("Assistant gesture")
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
                OutlinedButton(onClick = {
                    try {
                        startActivity(Intent(AndroidSettings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
                    } catch (_: ActivityNotFoundException) {
                    }
                }) { Text("Make Lena your digital assistant") }

                Section("Talking to Lena")
                val phoneEar = remember { PhoneEar.available(this@SettingsActivity) }
                Text("Hearing you", style = MaterialTheme.typography.labelLarge)
                Choice(
                    "On this phone",
                    if (phoneEar) "Shows your words as you speak. The audio never leaves the phone."
                    else "Not available on this phone; your server is used instead.",
                    s.hearing == Hearing.PHONE,
                ) { s = s.copy(hearing = Hearing.PHONE) }
                Choice("Your server", "Open WebUI's speech-to-text, the same as call mode", s.hearing == Hearing.SERVER) {
                    s = s.copy(hearing = Hearing.SERVER)
                }
                Text("Lena's voice", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
                Choice("Your server's voice", "Open WebUI's text-to-speech, the same as call mode", s.speaking == Speaking.SERVER) {
                    s = s.copy(speaking = Speaking.SERVER)
                }
                Choice("The phone's voice", "Android's text-to-speech", s.speaking == Speaking.PHONE) {
                    s = s.copy(speaking = Speaking.PHONE)
                }
                Choice("Don't read replies aloud", "Show them only", s.speaking == Speaking.OFF) {
                    s = s.copy(speaking = Speaking.OFF)
                }
                Text("Keep listening", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
                Choice("After every reply", "Talk back without tapping, like a conversation. Stays quiet if you say nothing.",
                    s.followUp == FollowUp.ALWAYS) { s = s.copy(followUp = FollowUp.ALWAYS) }
                Choice("Only when Lena asks a question", "Like Home Assistant's \"continue conversation\"",
                    s.followUp == FollowUp.QUESTIONS) { s = s.copy(followUp = FollowUp.QUESTIONS) }
                Choice("Off", "Tap the mic to talk again", s.followUp == FollowUp.OFF) { s = s.copy(followUp = FollowUp.OFF) }
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
                        label = { Text("Lena's context size (tokens)") },
                        supportingText = {
                            Text("Half of it holds the conversation; the rest is for her prompt, tools and reply. Match your model's context length.")
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Section("Notifications")
                Toggle(
                    "When a reply finishes",
                    "If you leave Lena while she's still answering",
                    s.notifyReplies,
                ) { s = s.copy(notifyReplies = it) }

                Section("Quick prompts")
                Text(
                    "One tap from the Ask sheet, the widget and the app icon's long-press menu.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                s.quickPrompts.forEachIndexed { i, p ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
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
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Section("Scheduled prompts")
                Text(
                    "Lena asks these on her own every day and notifies you with the answer. They're saved as chats.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                s.schedules.forEachIndexed { i, sch ->
                    ScheduleCard(
                        sch,
                        onChange = { changed -> s = s.copy(schedules = s.schedules.mapIndexed { j, o -> if (j == i) changed else o }) },
                        onDelete = { s = s.copy(schedules = s.schedules.filterIndexed { j, _ -> j != i }) },
                    )
                }
                FilledTonalButton(onClick = {
                    s = s.copy(schedules = s.schedules + Schedule(prompt = "Give me my morning briefing."))
                }) {
                    Icon(Icons.Outlined.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add scheduled prompt")
                }

                Section("On your phone")
                OutlinedButton(onClick = { addTile() }) { Text("Add Quick Settings tile") }
                OutlinedButton(onClick = { addWidget() }) { Text("Add home screen widget") }

                HorizontalDivider(Modifier.padding(top = 8.dp))
                val version = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull()
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LenaAvatar(32.dp)
                    Text(
                        "Lena ${version ?: ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    @Composable
    private fun Section(title: String) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 12.dp),
        )
    }

    @Composable
    private fun Choice(title: String, detail: String, selected: Boolean, onSelect: () -> Unit) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
        ) {
            RadioButton(selected = selected, onClick = null)
            Spacer(Modifier.width(12.dp))
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
            modifier = Modifier.fillMaxWidth().clickable { onChange(!checked) },
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }

    @Composable
    private fun ScheduleCard(s: Schedule, onChange: (Schedule) -> Unit, onDelete: () -> Unit) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    Text("every day", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = s.enabled, onCheckedChange = { onChange(s.copy(enabled = it)) })
                    IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, "Remove") }
                }
                OutlinedTextField(
                    value = s.prompt,
                    onValueChange = { onChange(s.copy(prompt = it)) },
                    label = { Text("Prompt") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
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
