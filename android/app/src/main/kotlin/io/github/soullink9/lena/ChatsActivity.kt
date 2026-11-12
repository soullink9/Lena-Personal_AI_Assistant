@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.soullink9.lena

import android.content.Context
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
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.widthIn
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
 * All chats: everything the drawer has no room for. Your pinned chats and your folders (in the order you
 * set in the menu) with their chats, and your whole history (a page at a time), with search. Long-press a chat to pin it, move it to a
 * folder, rename or delete it; long-press a folder to rename or delete it. Opening a chat goes back to
 * the main screen with it.
 */
class ChatsActivity : ComponentActivity() {

    // Dark on a TV (Tv.darkOnTv).
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase)
        Tv.darkOnTv(this, newBase)
    }

    companion object {
        /** Search for this straight away (See all results, from the menu's search). */
        const val EXTRA_QUERY = "query"
    }

    private lateinit var api: Api

    /** Every touch or key: someone's using the wall display, so its screensaver waits (see [WallDisplay]). */
    override fun onUserInteraction() {
        super.onUserInteraction()
        WallDisplay.touched()
    }

    /** Needs your Open WebUI; without one there's nothing to list. */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A TV has no chat (no keyboard to type with): the wall display instead.
        if (Tv.isTv(this)) {
            startActivity(Intent(this, DashboardActivity::class.java))
            finish()
            return
        }
        WallDisplay.watch(this)
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
        var query by remember { mutableStateOf(intent.getStringExtra(EXTRA_QUERY).orEmpty()) }
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
                pinned = runCatching { inYourOrder(this@ChatsActivity, api.pinned()) }.getOrDefault(pinned)
                val loaded = runCatching { api.folders(Prefs.folderOrder(this@ChatsActivity)) }.getOrNull()
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
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                )
            },
        ) { padding ->
            val pinnedIds = pinned.map { it.id }.toSet()
            // Centred and no wider than PAGE_MAX_WIDTH, so the list stays readable on an unfolded screen.
            LazyColumn(
                Modifier.padding(padding).fillMaxSize().wrapContentWidth().widthIn(max = PAGE_MAX_WIDTH).fillMaxWidth(),
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

                        // Folders: drawn exactly like the chats in your list style. Tap one to open it,
                        // long-press to rename or delete it; its chats show under it, indented.
                        val list = folders
                        if (!list.isNullOrEmpty()) item(key = "folders") {
                            Folders(list, style, pinnedIds, actions,
                                onRename = { renamingFolder = it }, onDelete = { deletingFolder = it },
                                onMore = { f -> scope.launch { loadFolder(f) } },
                            ) { f ->
                                f.open = !f.open
                                scope.launch {
                                    runCatching { api.setFolderExpanded(f.folder.id, f.open) }
                                    if (f.open && f.chats == null) loadFolder(f)
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

    /**
     * Your folders under a Folders label, each row (and the chats in an open one) drawn with the same look
     * as the chats in your list style (lineLook), so folders and chats match.
     */
    @Composable
    private fun Folders(
        list: List<FolderState>,
        style: ChatListStyle,
        pinnedIds: Set<String>,
        actions: ChatActions,
        onRename: (Api.Folder) -> Unit,
        onDelete: (Api.Folder) -> Unit,
        onMore: (FolderState) -> Unit,
        onToggle: (FolderState) -> Unit,
    ) {
        val look = lineLook(style)
        val inside = look.start + 32.dp // a folder's chats sit under its name, past the folder icon
        Column {
            GroupLabel("Folders", style)
            Column(
                if (look.grouped) Modifier.clip(RoundedCornerShape(if (style == ChatListStyle.GROUPS) 24.dp else 16.dp)) else Modifier,
                verticalArrangement = Arrangement.spacedBy(if (look.grouped) 1.dp else 0.dp),
            ) {
                list.forEach { f ->
                    FolderRow(f, look, onRename = { onRename(f.folder) }, onDelete = { onDelete(f.folder) }) { onToggle(f) }
                    if (f.open) {
                        val chats = f.chats
                        when {
                            chats == null -> ListHint("Loading…", Modifier.padding(start = inside))
                            chats.isEmpty() -> ListHint("No chats in this folder", Modifier.padding(start = inside))
                            else -> chats.forEach { c ->
                                actions.WithMenu(c, c.id in pinnedIds) { longPress ->
                                    ChatLine(c, false, null, look.height, inside, look.shape, look.idle, { openChat(c.id) }, longPress)
                                }
                            }
                        }
                        if (f.more) TextButton(onClick = { onMore(f) }, modifier = Modifier.padding(start = inside - 12.dp)) { Text("Show more") }
                    }
                }
            }
        }
    }

    /** A folder's row: tap to open or close it, long-press for Rename and Delete. */
    @Composable
    private fun FolderRow(f: FolderState, look: LineLook, onRename: () -> Unit, onDelete: () -> Unit, onToggle: () -> Unit) {
        var menu by remember { mutableStateOf(false) }
        Box {
            FolderLine(f.folder.label, f.open, look.height, look.start, look.shape, look.idle, onToggle = onToggle, onLongClick = { menu = true })
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
