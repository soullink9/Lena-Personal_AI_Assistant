@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.soullink9.lena

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Date

/**
 * Settings → Logs: Lena's own log (problems first), and Android's log for her current run.
 * Copy or share what you see, for example to ask for help; nothing leaves the phone otherwise.
 */
class LogsActivity : ComponentActivity() {

    /** Edge to edge, like the rest of the app. */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { LenaTheme { Screen() } }
    }

    /** The three views: problems only, everything Lena logged, and Android's own log for this run. */
    private enum class Tab(val label: String) { PROBLEMS("Problems"), ALL("Everything"), ANDROID("Android") }

    /** The chosen view, newest first, with search, copy, share and clear in the top bar. */
    @Composable
    private fun Screen() {
        var tab by remember { mutableStateOf(Tab.PROBLEMS) }
        var query by remember { mutableStateOf("") }
        var entries by remember { mutableStateOf<List<Logs.Entry>?>(null) }
        var android by remember { mutableStateOf<List<String>?>(null) }
        var refresh by remember { mutableIntStateOf(0) }
        var clearing by remember { mutableStateOf(false) }

        LaunchedEffect(refresh, tab) {
            if (tab == Tab.ANDROID) android = withContext(Dispatchers.IO) { Logs.android() }
            else entries = withContext(Dispatchers.IO) { Logs.read() }
        }

        val shownEntries = entries.orEmpty()
            .filter { tab != Tab.PROBLEMS || it.level != Logs.Level.INFO }
            .filter { query.isBlank() || it.message.contains(query, true) || it.tag.contains(query, true) }
        val shownAndroid = android.orEmpty().filter { query.isBlank() || it.contains(query, true) }
        val asText = {
            if (tab == Tab.ANDROID) shownAndroid.joinToString("\n")
            else shownEntries.joinToString("\n") { "${stamp(it.time)}  ${it.level.letter}  ${it.tag}: ${it.message}" }
        }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.surface,
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("Logs") },
                    navigationIcon = { IconButton(onClick = { finish() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                    actions = {
                        IconButton(onClick = { refresh++ }) { Icon(Icons.Outlined.Refresh, "Refresh") }
                        IconButton(onClick = {
                            getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Lena logs", asText()))
                        }) { Icon(Icons.Outlined.ContentCopy, "Copy") }
                        IconButton(onClick = {
                            startActivity(Intent.createChooser(
                                Intent(Intent.ACTION_SEND).setType("text/plain")
                                    .putExtra(Intent.EXTRA_SUBJECT, "Lena ${Updates.installed(this@LogsActivity)} logs")
                                    .putExtra(Intent.EXTRA_TEXT, asText()),
                                "Share logs",
                            ))
                        }) { Icon(Icons.Outlined.Share, "Share") }
                        if (tab != Tab.ANDROID) IconButton(onClick = { clearing = true }) { Icon(Icons.Outlined.Delete, "Clear") }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                )
            },
        ) { padding ->
            Column(Modifier.padding(padding).fillMaxSize()) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Tab.entries.forEachIndexed { i, t ->
                        SegmentedButton(
                            selected = tab == t,
                            onClick = { tab = t },
                            shape = SegmentedButtonDefaults.itemShape(i, Tab.entries.size),
                        ) { Text(t.label) }
                    }
                }
                SearchField(query, "Search the log", Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { query = it }
                if (tab == Tab.ANDROID) {
                    Footnote("Android's log for Lena's current run (an app can only see its own), newest first. Useful for crashes in the web pages.")
                    if (android == null) Footnote("Loading…")
                    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                        itemsIndexed(shownAndroid) { _, line ->
                            Text(line, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 14.sp,
                                color = when {
                                    Regex("^\\S+ \\S+ E/").containsMatchIn(line) -> MaterialTheme.colorScheme.error
                                    Regex("^\\S+ \\S+ W/").containsMatchIn(line) -> Color(0xFFE0A030)
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.padding(vertical = 2.dp))
                        }
                    }
                } else {
                    when {
                        entries == null -> Footnote("Loading…")
                        shownEntries.isEmpty() -> Footnote(
                            if (tab == Tab.PROBLEMS && query.isBlank()) "No problems logged. Everything has shows what she's been doing." else "Nothing matches."
                        )
                    }
                    LazyColumn(
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        itemsIndexed(shownEntries) { i, e -> EntryRow(e, first = i == 0, last = i == shownEntries.lastIndex) }
                    }
                }
            }
        }

        if (clearing) AlertDialog(
            onDismissRequest = { clearing = false },
            title = { Text("Clear the log?") },
            text = { Text("Lena's own log is deleted from this phone.") },
            confirmButton = {
                TextButton(colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error), onClick = {
                    Logs.clear()
                    clearing = false
                    refresh++
                }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { clearing = false }) { Text("Cancel") } },
        )
    }

    /** One entry: tap to see all of it (stack traces fold away until then). */
    @Composable
    private fun EntryRow(e: Logs.Entry, first: Boolean, last: Boolean) {
        var open by remember(e) { mutableStateOf(false) }
        val (icon, tint) = when (e.level) {
            Logs.Level.ERROR -> Icons.Outlined.ErrorOutline to MaterialTheme.colorScheme.error
            Logs.Level.WARN -> Icons.Outlined.WarningAmber to Color(0xFFE0A030)
            Logs.Level.INFO -> Icons.Outlined.Info to MaterialTheme.colorScheme.onSurfaceVariant
        }
        val shape = RoundedCornerShape(
            topStart = if (first) 20.dp else 4.dp, topEnd = if (first) 20.dp else 4.dp,
            bottomStart = if (last) 20.dp else 4.dp, bottomEnd = if (last) 20.dp else 4.dp,
        )
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = rowModifier(minHeight = 0.dp).clip(shape).clickable { open = !open }.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Icon(icon, e.level.name, tint = tint, modifier = Modifier.size(20.dp).padding(top = 2.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(e.tag, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    Text(stamp(e.time), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val firstLine = e.message.substringBefore('\n')
                Text(firstLine, style = MaterialTheme.typography.bodyMedium, maxLines = if (open) Int.MAX_VALUE else 3, overflow = TextOverflow.Ellipsis)
                if (open && e.message.contains('\n')) {
                    Text(e.message.substringAfter('\n'), fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                } else if (e.message.contains('\n')) {
                    Text("Tap for details", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }

    /** An entry's time, to the second. */
    private fun stamp(t: Long): String = DateFormat.format("MMM d, HH:mm:ss", Date(t)).toString()
}
