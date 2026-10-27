@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.soullink9.lena

import android.os.Bundle
import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * What Lena knows: the memories Open WebUI keeps about you, and your knowledge collections.
 * Fix or forget a memory, or take a document out of a collection, when something went in by mistake.
 */
class MemoryActivity : ComponentActivity() {

    private lateinit var api: Api

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        api = Api.from(this) ?: run { finish(); return }
        enableEdgeToEdge()
        setContent { LenaTheme { Screen() } }
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

    private fun problem(e: Throwable, what: String): String = when (e) {
        is AuthException -> "Sign in to Lena first."
        is ForbiddenException -> "$what is turned off on your server, or your account can't use it."
        else -> "Couldn't reach your server."
    }

    private fun ago(millis: Long): String =
        if (millis <= 0) "" else DateUtils.getRelativeTimeSpanString(millis, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()

    @Composable
    private fun Screen() {
        var tab by remember { mutableIntStateOf(0) }
        var openKnowledge by remember { mutableStateOf<Api.Knowledge?>(null) }
        var adding by remember { mutableStateOf(false) }

        BackHandler(enabled = openKnowledge != null) { openKnowledge = null }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(openKnowledge?.name ?: "Memory & knowledge", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        IconButton(onClick = { if (openKnowledge != null) openKnowledge = null else finish() }) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                        }
                    },
                )
            },
            floatingActionButton = {
                if (tab == 0 && openKnowledge == null) {
                    FloatingActionButton(onClick = { adding = true }) { Icon(Icons.Outlined.Add, "Add a memory") }
                }
            },
        ) { padding ->
            Column(Modifier.padding(padding).fillMaxSize()) {
                if (openKnowledge == null) {
                    PrimaryTabRow(selectedTabIndex = tab) {
                        Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Memories") })
                        Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Knowledge") })
                    }
                }
                when {
                    openKnowledge != null -> Files(openKnowledge!!)
                    tab == 0 -> Memories(adding, onAddDone = { adding = false })
                    else -> Collections { openKnowledge = it }
                }
            }
        }
    }

    // ---- Memories ---------------------------------------------------------------------------

    @Composable
    private fun Memories(adding: Boolean, onAddDone: () -> Unit) {
        val scope = rememberCoroutineScope()
        var memories by remember { mutableStateOf<List<Api.Memory>?>(null) }
        var error by remember { mutableStateOf<String?>(null) }
        var query by remember { mutableStateOf("") }
        var editing by remember { mutableStateOf<Api.Memory?>(null) }
        var deleting by remember { mutableStateOf<Api.Memory?>(null) }
        var refresh by remember { mutableIntStateOf(0) }

        LaunchedEffect(refresh) {
            runCatching { api.memories() }
                .onSuccess { memories = it; error = null }
                .onFailure { error = problem(it, "Memory") }
        }

        Column(Modifier.fillMaxSize()) {
            Text(
                "Things Lena remembers about you. Tap one to fix it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp),
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search memories") },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            val shown = memories?.filter { query.isBlank() || it.content.contains(query.trim(), ignoreCase = true) }
            when {
                error != null -> Hint(error!!)
                shown == null -> Loading()
                shown.isEmpty() -> Hint(if (query.isBlank()) "Lena hasn't saved any memories yet." else "No matching memories.")
                else -> LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(shown, key = { it.id }) { m ->
                        Card(Modifier.fillMaxWidth().clickable { editing = m }) {
                            Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.Top) {
                                Column(Modifier.weight(1f)) {
                                    Text(m.content, style = MaterialTheme.typography.bodyLarge)
                                    val detail = listOfNotNull(m.path, ago(m.updatedMillis).ifBlank { null }).joinToString(" · ")
                                    if (detail.isNotBlank()) {
                                        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                IconButton(onClick = { deleting = m }) { Icon(Icons.Outlined.Delete, "Forget") }
                            }
                        }
                    }
                }
            }
        }

        if (adding || editing != null) {
            val current = editing
            var text by remember(current?.id, adding) { mutableStateOf(current?.content ?: "") }
            AlertDialog(
                onDismissRequest = { editing = null; onAddDone() },
                title = { Text(if (current == null) "New memory" else "Edit memory") },
                text = { OutlinedTextField(value = text, onValueChange = { text = it }, minLines = 2, modifier = Modifier.fillMaxWidth()) },
                confirmButton = {
                    TextButton(enabled = text.isNotBlank(), onClick = {
                        val content = text.trim()
                        editing = null
                        onAddDone()
                        scope.launch {
                            val r = runCatching {
                                if (current == null) api.addMemory(content) else api.updateMemory(current.id, content)
                            }
                            if (r.isFailure) toast(problem(r.exceptionOrNull()!!, "Memory")) else refresh++
                        }
                    }) { Text("Save") }
                },
                dismissButton = { TextButton(onClick = { editing = null; onAddDone() }) { Text("Cancel") } },
            )
        }

        deleting?.let { m ->
            AlertDialog(
                onDismissRequest = { deleting = null },
                title = { Text("Forget this?") },
                text = { Text(m.content) },
                confirmButton = {
                    TextButton(
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        onClick = {
                            deleting = null
                            scope.launch {
                                val r = runCatching { api.deleteMemory(m.id) }
                                if (r.isFailure) toast(problem(r.exceptionOrNull()!!, "Memory")) else refresh++
                            }
                        },
                    ) { Text("Forget") }
                },
                dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
            )
        }
    }

    // ---- Knowledge --------------------------------------------------------------------------

    @Composable
    private fun Collections(onOpen: (Api.Knowledge) -> Unit) {
        var bases by remember { mutableStateOf<List<Api.Knowledge>?>(null) }
        var error by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(Unit) {
            runCatching { api.knowledgeBases() }
                .onSuccess { bases = it }
                .onFailure { error = problem(it, "Knowledge") }
        }
        when {
            error != null -> Hint(error!!)
            bases == null -> Loading()
            bases!!.isEmpty() -> Hint("You don't have any knowledge collections.")
            else -> LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                items(bases!!, key = { it.id }) { k ->
                    ListItem(
                        headlineContent = { Text(k.name) },
                        supportingContent = {
                            val detail = listOfNotNull(k.description.ifBlank { null }, if (k.canEdit) null else "View only")
                            if (detail.isNotEmpty()) Text(detail.joinToString(" · "), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        },
                        leadingContent = { Icon(Icons.Outlined.Folder, null) },
                        modifier = Modifier.clickable { onOpen(k) },
                    )
                }
            }
        }
    }

    @Composable
    private fun Files(k: Api.Knowledge) {
        val scope = rememberCoroutineScope()
        var files by remember(k.id) { mutableStateOf<List<Api.KnowledgeFile>?>(null) }
        var total by remember(k.id) { mutableIntStateOf(0) }
        var page by remember(k.id) { mutableIntStateOf(1) }
        var error by remember(k.id) { mutableStateOf<String?>(null) }
        var removing by remember { mutableStateOf<Api.KnowledgeFile?>(null) }
        var loadingMore by remember { mutableStateOf(false) }

        suspend fun load(p: Int) {
            runCatching { api.knowledgeFiles(k.id, p) }
                .onSuccess { (list, count) ->
                    files = if (p == 1) list else (files ?: emptyList()) + list
                    total = count
                    page = p
                    error = null
                }
                .onFailure { error = problem(it, "Knowledge") }
        }

        LaunchedEffect(k.id) { load(1) }

        when {
            error != null -> Hint(error!!)
            files == null -> Loading()
            files!!.isEmpty() -> Hint("This collection is empty.")
            else -> LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                item {
                    Text(
                        if (k.canEdit) "$total documents. Remove one if it was added by mistake."
                        else "$total documents. This collection is view only for you.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
                items(files!!, key = { it.id }) { f ->
                    ListItem(
                        headlineContent = { Text(f.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { ago(f.addedMillis).ifBlank { null }?.let { Text("Added $it") } },
                        leadingContent = { Icon(Icons.Outlined.Description, null) },
                        trailingContent = {
                            if (k.canEdit) IconButton(onClick = { removing = f }) { Icon(Icons.Outlined.Delete, "Remove") }
                        },
                    )
                }
                if (files!!.size < total) item {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        if (loadingMore) CircularProgressIndicator(Modifier.size(24.dp))
                        else OutlinedButton(onClick = {
                            loadingMore = true
                            scope.launch { load(page + 1); loadingMore = false }
                        }) { Text("Show more") }
                    }
                }
            }
        }

        removing?.let { f ->
            AlertDialog(
                onDismissRequest = { removing = null },
                title = { Text("Remove from ${k.name}?") },
                text = { Text("“${f.name}” will no longer be used to answer you.") },
                confirmButton = {
                    TextButton(
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        onClick = {
                            removing = null
                            scope.launch {
                                val r = runCatching { api.removeKnowledgeFile(k.id, f.id) }
                                if (r.isFailure) toast(problem(r.exceptionOrNull()!!, "Knowledge")) else load(1)
                            }
                        },
                    ) { Text("Remove") }
                },
                dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel") } },
            )
        }
    }

    @Composable
    private fun Hint(text: String) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
        )
    }

    @Composable
    private fun Loading() {
        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    }
}
