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
        val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

        LaunchedEffect(Unit) {
            Api.from(this@SettingsActivity)?.let { api -> models = runCatching { api.models() }.getOrDefault(emptyList()) }
        }

        fun askForNotifications() {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        fun save() {
            val server = Prefs.normalize(s.server)
            val signIn = Prefs.normalizeSignIn(s.signIn)
            serverError = if (server == null) "Use your https:// address" else null
            signInError = if (signIn == null) "Use https:// addresses" else null
            if (server == null || signIn == null) return
            val saved = s.copy(server = server, signIn = signIn)
            Prefs.save(this, saved)
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
                        placeholder = { Text("Open WebUI's default") },
                        supportingText = {
                            Text(models.firstOrNull { it.id == s.model }?.name ?: "The model's ID in Open WebUI, e.g. lena")
                        },
                        singleLine = true,
                        trailingIcon = {
                            if (models.isNotEmpty()) IconButton(onClick = { modelMenu = true }) { Icon(Icons.Rounded.ExpandMore, "Pick a model") }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    DropdownMenu(expanded = modelMenu, onDismissRequest = { modelMenu = false }) {
                        DropdownMenuItem(text = { Text("Open WebUI's default") }, onClick = { s = s.copy(model = ""); modelMenu = false })
                        models.forEach { m ->
                            DropdownMenuItem(text = { Text(m.name) }, onClick = { s = s.copy(model = m.id); modelMenu = false })
                        }
                    }
                }

                Section("Assistant gesture")
                Choice("Ask Lena sheet", "A quick question over whatever you're doing", s.assist == AssistAction.ASK) {
                    s = s.copy(assist = AssistAction.ASK)
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
                Text(
                    "Lena ${version ?: ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
