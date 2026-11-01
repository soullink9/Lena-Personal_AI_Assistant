@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.soullink9.lena

import android.os.Bundle
import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * What Lena knows: her own memory in Home Assistant (if you've connected it), the memories
 * Open WebUI keeps about you, and your knowledge collections. Fix or forget a memory, or take a
 * document out of a collection, when something went in by mistake.
 */
class MemoryActivity : ComponentActivity() {

    private lateinit var api: Api
    private var house: HomeAssistant? = null

    /** One list of memories: where they come from and how to change them. */
    private class Store(
        val intro: String,
        val load: suspend () -> List<Entry>,
        val add: suspend (String) -> Unit,
        val update: suspend (Entry, String) -> Unit,
        val delete: suspend (Entry) -> Unit,
        val problem: (Throwable) -> String,
    )

    /** A memory as the list shows it, whichever store it came from. */
    private data class Entry(val id: String, val content: String, val detail: String)

    private val openWebUi by lazy {
        Store(
            intro = "Things Open WebUI remembers about you. Tap one to fix it.",
            load = { api.memories().map { m -> Entry(m.id, m.content, listOfNotNull(m.path, ago(m.updatedMillis).ifBlank { null }).joinToString(" · ")) } },
            add = { api.addMemory(it) },
            update = { item, text -> api.updateMemory(item.id, text) },
            delete = { api.deleteMemory(it.id) },
            problem = { problem(it, "Memory") },
        )
    }

    private val hers by lazy {
        val h = house!!
        val list = DEFAULT_MEMORY_LIST
        var current = emptyList<HouseMemory>()
        Store(
            intro = "Her own memory, kept by the Lena integration in Home Assistant. She uses it in the house, in Open WebUI and here. Tap one to fix it.",
            load = { h.memories(list).also { current = it }.map { Entry(it.uid, it.text, "") } },
            add = { h.addMemory(list, it) },
            update = { item, text -> h.updateMemory(list, current.first { it.uid == item.id }, text) },
            delete = { item -> h.removeMemory(list, current.first { it.uid == item.id }) },
            problem = {
                when (it) {
                    is HaAuthException -> "Home Assistant turned down the token. Check it in settings."
                    else -> "Couldn't reach her memory. Check Home Assistant in settings, and that the Lena integration is set up."
                }
            },
        )
    }

    /** Needs your Open WebUI; Home Assistant is optional (without it there's no Hers tab). */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        api = Api.from(this) ?: run { finish(); return }
        house = HomeAssistant.from(this)
        enableEdgeToEdge()
        setContent { LenaTheme { Screen() } }
    }

    /** A short message at the bottom of the screen. */
    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

    /**
     * A failure in plain words: signed out, the feature switched off on the server, or the server
     * unreachable.
     */
    private fun problem(e: Throwable, what: String): String = when (e) {
        is AuthException -> "Sign in to Lena first."
        is ForbiddenException -> "$what is turned off on your server, or your account can't use it."
        else -> "Couldn't reach your server."
    }

    /** "5 minutes ago", or nothing when the server gave no time. */
    private fun ago(millis: Long): String =
        if (millis <= 0) "" else DateUtils.getRelativeTimeSpanString(millis, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()

    /**
     * Tabs for her shared memory (when Home Assistant is connected), Open WebUI's memories, and knowledge
     * collections, with a collection's documents opening over them.
     */
    @Composable
    private fun Screen() {
        // Hers (Home Assistant) first when it's connected, then Open WebUI's memories, then knowledge.
        val tabs = remember { listOfNotNull(if (house != null) "Hers" else null, if (house != null) "Open WebUI" else "Memories", "Knowledge") }
        var tab by remember { mutableIntStateOf(0) }
        val knowledgeTab = tabs.size - 1
        var openKnowledge by remember { mutableStateOf<Api.Knowledge?>(null) }

        BackHandler(enabled = openKnowledge != null) { openKnowledge = null }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.surface,
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text(openKnowledge?.name ?: "Memory & knowledge", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        IconButton(onClick = { if (openKnowledge != null) openKnowledge = null else finish() }) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                )
            },
        ) { padding ->
            Column(
                Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 24.dp).navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (openKnowledge == null) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        tabs.forEachIndexed { i, name ->
                            SegmentedButton(
                                selected = tab == i,
                                onClick = { tab = i },
                                shape = SegmentedButtonDefaults.itemShape(index = i, count = tabs.size),
                            ) { Text(name, maxLines = 1) }
                        }
                    }
                }
                when {
                    openKnowledge != null -> Files(openKnowledge!!)
                    tab == knowledgeTab -> Collections { openKnowledge = it }
                    else -> {
                        val store = if (house != null && tab == 0) hers else openWebUi
                        key(store) { Memories(store) }
                    }
                }
            }
        }
    }

    // ---- Memories ---------------------------------------------------------------------------

    /**
     * One memory store as an editable list: tap to change, swipe or long-press to delete, a field at the
     * bottom to add. Both stores use it, through Store.
     */
    @Composable
    private fun Memories(store: Store) {
        val scope = rememberCoroutineScope()
        var memories by remember { mutableStateOf<List<Entry>?>(null) }
        var error by remember { mutableStateOf<String?>(null) }
        var query by remember { mutableStateOf("") }
        var adding by remember { mutableStateOf(false) }
        var editing by remember { mutableStateOf<Entry?>(null) }
        var deleting by remember { mutableStateOf<Entry?>(null) }
        var refresh by remember { mutableIntStateOf(0) }

        LaunchedEffect(refresh) {
            runCatching { store.load() }
                .onSuccess { memories = it; error = null }
                .onFailure { error = store.problem(it) }
        }

        SearchField(query, "Search memories") { query = it }
        Footnote(store.intro)
        val shown = memories?.filter { query.isBlank() || it.content.contains(query.trim(), ignoreCase = true) }
        when {
            error != null -> Footnote(error!!)
            shown == null -> Loading()
            else -> Group {
                if (query.isBlank()) Item(Icons.Outlined.Add, "Add a memory", null) { adding = true }
                if (shown.isEmpty()) {
                    Item(null, if (query.isBlank()) "Nothing saved yet" else "No matching memories", null) {}
                }
                shown.forEach { m ->
                    Item(
                        icon = null,
                        title = m.content,
                        summary = m.detail.ifBlank { null },
                        maxTitleLines = 6,
                        trailing = { IconButton(onClick = { deleting = m }) { Icon(Icons.Outlined.Delete, "Forget") } },
                    ) { editing = m }
                }
            }
        }

        if (adding || editing != null) {
            val current = editing
            var text by remember(current?.id, adding) { mutableStateOf(current?.content ?: "") }
            AlertDialog(
                onDismissRequest = { editing = null; adding = false },
                title = { Text(if (current == null) "New memory" else "Edit memory") },
                text = { OutlinedTextField(value = text, onValueChange = { text = it }, minLines = 2, modifier = Modifier.fillMaxWidth()) },
                confirmButton = {
                    TextButton(enabled = text.isNotBlank(), onClick = {
                        val content = text.trim()
                        editing = null
                        adding = false
                        scope.launch {
                            val r = runCatching { if (current == null) store.add(content) else store.update(current, content) }
                            if (r.isFailure) toast(store.problem(r.exceptionOrNull()!!)) else refresh++
                        }
                    }) { Text("Save") }
                },
                dismissButton = { TextButton(onClick = { editing = null; adding = false }) { Text("Cancel") } },
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
                                val r = runCatching { store.delete(m) }
                                if (r.isFailure) toast(store.problem(r.exceptionOrNull()!!)) else refresh++
                            }
                        },
                    ) { Text("Forget") }
                },
                dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
            )
        }
    }

    // ---- Knowledge --------------------------------------------------------------------------

    /** Your knowledge collections; tap one to see its documents. */
    @Composable
    private fun Collections(onOpen: (Api.Knowledge) -> Unit) {
        var bases by remember { mutableStateOf<List<Api.Knowledge>?>(null) }
        var error by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(Unit) {
            runCatching { api.knowledgeBases() }
                .onSuccess { bases = it }
                .onFailure { error = problem(it, "Knowledge") }
        }
        Footnote("Your knowledge collections. Open one to see its documents.")
        when {
            error != null -> Footnote(error!!)
            bases == null -> Loading()
            bases!!.isEmpty() -> Footnote("You don't have any knowledge collections.")
            else -> Group {
                bases!!.forEach { k ->
                    val detail = listOfNotNull(k.description.ifBlank { null }, if (k.canEdit) null else "View only").joinToString(" · ")
                    Item(Icons.Outlined.Folder, k.name, detail.ifBlank { null }) { onOpen(k) }
                }
            }
        }
    }

    /**
     * A collection's documents, loaded a page at a time as you scroll, with remove for collections you may
     * edit.
     */
    @Composable
    private fun Files(k: Api.Knowledge) {
        val scope = rememberCoroutineScope()
        var files by remember(k.id) { mutableStateOf<List<Api.KnowledgeFile>?>(null) }
        var total by remember(k.id) { mutableIntStateOf(0) }
        var page by remember(k.id) { mutableIntStateOf(1) }
        var error by remember(k.id) { mutableStateOf<String?>(null) }
        var removing by remember { mutableStateOf<Api.KnowledgeFile?>(null) }
        var loadingMore by remember { mutableStateOf(false) }

        /** Loads page p and adds it to the list (page 1 replaces it). */
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
            error != null -> Footnote(error!!)
            files == null -> Loading()
            files!!.isEmpty() -> Footnote("This collection is empty.")
            else -> {
                Footnote(
                    if (k.canEdit) "$total documents. Remove one if it was added by mistake."
                    else "$total documents. This collection is view only for you."
                )
                Group {
                    files!!.forEach { f ->
                        Item(
                            icon = Icons.Outlined.Description,
                            title = f.name,
                            summary = ago(f.addedMillis).ifBlank { null }?.let { "Added $it" },
                            trailing = if (k.canEdit) {
                                { IconButton(onClick = { removing = f }) { Icon(Icons.Outlined.Delete, "Remove") } }
                            } else null,
                        ) {}
                    }
                }
                if (files!!.size < total) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
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

    /** A spinner while a list loads. */
    @Composable
    private fun Loading() {
        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    }
}

/** The rounded, filled search box used in the drawer and Memory & knowledge. */
@Composable
fun SearchField(value: String, placeholder: String, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    TextField(
        value = value,
        onValueChange = onChange,
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Outlined.Search, null) },
        trailingIcon = { if (value.isNotEmpty()) IconButton(onClick = { onChange("") }) { Icon(Icons.Outlined.Close, "Clear") } },
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}
