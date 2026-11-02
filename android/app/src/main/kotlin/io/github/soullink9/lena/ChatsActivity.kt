@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.soullink9.lena

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * All chats: everything the drawer has no room for. Your pinned chats, your folders with their chats,
 * and your whole history (a page at a time), with search. Long-press a chat to pin it, move it to a
 * folder, rename or delete it; long-press a folder to rename or delete it. Opening a chat goes back to
 * the main screen with it.
 */
class ChatsActivity : ComponentActivity() {

    private lateinit var api: Api

    /** Needs your Open WebUI; without one there's nothing to list. */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        api = Api.from(this) ?: run { finish(); return }
        enableEdgeToEdge()
        setContent { LenaTheme { Screen() } }
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

    /** Opens a chat on the main screen (which is single-task, so it's the same screen) and closes this one. */
    private fun openChat(id: String) {
        startActivity(
            Intent(this, MainActivity::class.java)
                .setAction(MainActivity.ACTION_OPEN_CHAT)
                .putExtra(MainActivity.EXTRA_CHAT_ID, id)
        )
        finish()
    }

    /** One folder in the list: whether it's open, and its chats loaded so far (null until it's opened). */
    private class FolderState(val folder: Api.Folder) {
        var open by mutableStateOf(folder.expanded)
        var chats by mutableStateOf<List<ChatItem>?>(null)
        var more by mutableStateOf(false)
        var page by mutableIntStateOf(0)
    }

    @Composable
    private fun Screen() {
        val scope = rememberCoroutineScope()
        val style = remember { Prefs.chatList(this) }
        val folded = remember { mutableStateMapOf<String, Boolean>() }
        var refresh by remember { mutableIntStateOf(0) }
        var query by remember { mutableStateOf("") }
        var results by remember { mutableStateOf<List<ChatItem>?>(null) }
        var pinned by remember { mutableStateOf<List<ChatItem>>(emptyList()) }
        var folders by remember { mutableStateOf<List<FolderState>?>(null) } // null: none, or folders are off
        var history by remember { mutableStateOf<List<ChatItem>?>(null) }
        var historyPage by remember { mutableIntStateOf(1) }
        var historyMore by remember { mutableStateOf(false) }
        var problem by remember { mutableStateOf<String?>(null) }
        var newFolder by remember { mutableStateOf(false) }
        var renamingFolder by remember { mutableStateOf<Api.Folder?>(null) }
        var deletingFolder by remember { mutableStateOf<Api.Folder?>(null) }

        val actions = remember { ChatActions(this, api, scope, onChanged = { refresh++ }) }

        /** Loads a folder's next page of chats and adds it. */
        suspend fun loadFolder(f: FolderState) {
            val next = f.page + 1
            runCatching { api.folderChats(f.folder.id, next) }
                .onSuccess { list ->
                    f.chats = (if (next == 1) emptyList() else f.chats.orEmpty()) + list
                    f.page = next
                    f.more = list.size >= Api.FOLDER_CHATS_PER_PAGE
                }
                .onFailure { if (f.chats == null) f.chats = emptyList() }
        }

        // Everything, again after any change: pinned, folders (and the chats of open ones), history page 1.
        LaunchedEffect(refresh) {
            try {
                pinned = runCatching { api.pinned() }.getOrDefault(pinned)
                val loaded = runCatching { api.folders() }.getOrNull()
                val before = folders.orEmpty().associateBy { it.folder.id }
                folders = loaded?.map { f -> FolderState(f).also { s -> before[f.id]?.let { s.open = it.open } } }
                folders?.filter { it.open }?.forEach { loadFolder(it) }
                val first = api.chats(1)
                history = first
                historyPage = 1
                historyMore = first.size >= Api.CHATS_PER_PAGE
                problem = null
            } catch (_: AuthException) {
                problem = "Sign in to see your chats"
            } catch (_: Exception) {
                problem = "Couldn't load your chats"
            }
        }

        // Search, as you type.
        LaunchedEffect(query, refresh) {
            if (query.isBlank()) { results = null; return@LaunchedEffect }
            delay(300)
            results = runCatching { api.search(query.trim()) }.getOrDefault(emptyList())
        }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.surface,
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("All chats") },
                    navigationIcon = { IconButton(onClick = { finish() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                    actions = {
                        if (folders != null) IconButton(onClick = { newFolder = true }) { Icon(Icons.Outlined.CreateNewFolder, "New folder") }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                )
            },
        ) { padding ->
            val pinnedIds = pinned.map { it.id }.toSet()
            LazyColumn(
                Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 32.dp),
            ) {
                item(key = "search") { SearchField(query, "Search chats", Modifier.padding(horizontal = 4.dp, vertical = 8.dp)) { query = it } }
                val found = results
                when {
                    query.isNotBlank() && found == null -> item { ListHint("Searching…") }
                    found != null -> {
                        if (found.isEmpty()) item { ListHint("No matching chats") }
                        else chatGroups(listOf("Results" to found), style, null, pinnedIds, actions, folded, true, ::openChat)
                    }
                    problem != null -> item { ListHint(problem!!) }
                    history == null -> item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                    else -> {
                        if (pinned.isNotEmpty()) {
                            chatGroups(listOf(PINNED_LABEL to pinned), style, null, pinnedIds, actions, folded, false, ::openChat)
                        }

                        // Folders: tap to open, long-press to rename or delete; their chats in slim rows.
                        val list = folders
                        if (!list.isNullOrEmpty()) {
                            item(key = "folders-title") { SectionTitle("Folders", Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)) }
                            list.forEach { f ->
                                item(key = "folder-${f.folder.id}") {
                                    FolderRow(f, onRename = { renamingFolder = f.folder }, onDelete = { deletingFolder = f.folder }) {
                                        f.open = !f.open
                                        scope.launch {
                                            runCatching { api.setFolderExpanded(f.folder.id, f.open) }
                                            if (f.open && f.chats == null) loadFolder(f)
                                        }
                                    }
                                }
                                if (f.open) {
                                    val chats = f.chats
                                    when {
                                        chats == null -> item(key = "folder-${f.folder.id}-loading") { ListHint("Loading…", Modifier.padding(start = 24.dp)) }
                                        chats.isEmpty() -> item(key = "folder-${f.folder.id}-empty") { ListHint("No chats in this folder", Modifier.padding(start = 24.dp)) }
                                        else -> items(chats, key = { "folder-${f.folder.id}-${it.id}" }) { c ->
                                            actions.WithMenu(c, c.id in pinnedIds) { longPress ->
                                                ChatLine(c, false, null, 40.dp, 44.dp, RoundedCornerShape(20.dp), Color.Transparent, { openChat(c.id) }, longPress)
                                            }
                                        }
                                    }
                                    if (f.more) item(key = "folder-${f.folder.id}-more") {
                                        TextButton(onClick = { scope.launch { loadFolder(f) } }, modifier = Modifier.padding(start = 32.dp)) { Text("Show more") }
                                    }
                                }
                            }
                        }

                        // Your history, by day, in your chosen style, a page at a time.
                        val chats = history.orEmpty()
                        if (chats.isEmpty() && pinned.isEmpty() && list.isNullOrEmpty()) item { ListHint("No chats yet") }
                        chatGroups(groupByDay(chats), style, null, pinnedIds, actions, folded, false, ::openChat)
                        if (historyMore) item(key = "history-more") {
                            Box(Modifier.fillMaxWidth().padding(top = 12.dp), contentAlignment = Alignment.Center) {
                                OutlinedButton(onClick = {
                                    scope.launch {
                                        val next = historyPage + 1
                                        runCatching { api.chats(next) }.onSuccess { more ->
                                            history = chats + more.filter { m -> chats.none { it.id == m.id } }
                                            historyPage = next
                                            historyMore = more.size >= Api.CHATS_PER_PAGE
                                        }.onFailure { toast("Couldn't load more chats") }
                                    }
                                }) { Text("Show more") }
                            }
                        }
                    }
                }
            }
        }

        actions.Dialogs()

        if (newFolder) NameDialog("New folder", "", "Make") { name ->
            newFolder = false
            if (name != null) scope.launch {
                if (runCatching { api.createFolder(name) }.isFailure) toast("Couldn't make the folder")
                refresh++
            }
        }

        renamingFolder?.let { f ->
            NameDialog("Rename folder", f.name, "Rename") { name ->
                renamingFolder = null
                if (name != null) scope.launch {
                    if (runCatching { api.renameFolder(f.id, name) }.isFailure) toast("Couldn't rename the folder")
                    refresh++
                }
            }
        }

        deletingFolder?.let { f ->
            AlertDialog(
                onDismissRequest = { deletingFolder = null },
                title = { Text("Delete folder?") },
                text = { Text("“${f.label}” and any folders inside it go. Their chats are kept and move to your chat list.") },
                confirmButton = {
                    TextButton(
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        onClick = {
                            deletingFolder = null
                            scope.launch {
                                if (runCatching { api.deleteFolder(f.id) }.isFailure) toast("Couldn't delete the folder")
                                refresh++
                            }
                        },
                    ) { Text("Delete") }
                },
                dismissButton = { TextButton(onClick = { deletingFolder = null }) { Text("Cancel") } },
            )
        }
    }

    /** A folder's row: tap to open or close it, long-press for Rename and Delete. */
    @Composable
    private fun FolderRow(f: FolderState, onRename: () -> Unit, onDelete: () -> Unit, onToggle: () -> Unit) {
        var menu by remember { mutableStateOf(false) }
        Box {
            FoldHeader(
                label = f.folder.label,
                count = null,
                open = f.open,
                icon = if (f.open) Icons.Outlined.FolderOpen else Icons.Outlined.Folder,
                onLongClick = { menu = true },
                onToggle = onToggle,
            )
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Rename") }, leadingIcon = { Icon(Icons.Outlined.Edit, null) }, onClick = { menu = false; onRename() })
                DropdownMenuItem(text = { Text("Delete") }, leadingIcon = { Icon(Icons.Outlined.Delete, null) }, onClick = { menu = false; onDelete() })
            }
        }
    }

    /** Asks for a name; onDone gets it trimmed, or null when cancelled. */
    @Composable
    private fun NameDialog(title: String, start: String, confirm: String, onDone: (String?) -> Unit) {
        var name by remember { mutableStateOf(start) }
        AlertDialog(
            onDismissRequest = { onDone(null) },
            title = { Text(title) },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Name") }) },
            confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onDone(name.trim()) }) { Text(confirm) } },
            dismissButton = { TextButton(onClick = { onDone(null) }) { Text("Cancel") } },
        )
    }
}
