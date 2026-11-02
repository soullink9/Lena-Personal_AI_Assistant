@file:OptIn(ExperimentalFoundationApi::class)

package io.github.soullink9.lena

import android.content.Context
import android.text.format.DateFormat
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/*
 * Your chats as lists, shared by the drawer (MainActivity) and All chats (ChatsActivity): the rows in each
 * list style, grouping by day, the long-press menu (pin, move to a folder, rename, delete) and its dialogs.
 */

/** The label of the pinned chats' group. */
const val PINNED_LABEL = "Pinned"

/**
 * Pinned chats in your order (Prefs.pinnedOrder, set with Reorder in the menu). Chats pinned since you
 * last reordered come first, newest first, so a new pin is easy to find.
 */
fun inYourOrder(context: Context, pinned: List<ChatItem>): List<ChatItem> {
    val position = Prefs.pinnedOrder(context).withIndex().associate { it.value to it.index }
    val (known, fresh) = pinned.partition { it.id in position }
    return fresh.sortedByDescending { it.updatedMillis } + known.sortedBy { position[it.id] }
}

/**
 * Put things in order: each row has up and down arrows. onDone gets the ids in the new order, or null if
 * you cancel. Used for pinned chats and folders in the menu.
 */
@Composable
fun ReorderDialog(title: String, items: List<Pair<String, String>>, onDone: (List<String>?) -> Unit) {
    var list by remember { mutableStateOf(items) }

    /** Moves the row at [from] by [by] places (-1 up, +1 down). */
    fun move(from: Int, by: Int) {
        val to = from + by
        if (to !in list.indices) return
        val next = list.toMutableList()
        next.add(to, next.removeAt(from))
        list = next
    }

    AlertDialog(
        onDismissRequest = { onDone(null) },
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                list.forEachIndexed { i, (_, label) ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(label, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f))
                        IconButton(onClick = { move(i, -1) }, enabled = i > 0) { Icon(Icons.Rounded.KeyboardArrowUp, "Move $label up") }
                        IconButton(onClick = { move(i, 1) }, enabled = i < list.size - 1) { Icon(Icons.Rounded.KeyboardArrowDown, "Move $label down") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onDone(list.map { it.first }) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = { onDone(null) }) { Text("Cancel") } },
    )
}

/**
 * What can be done to a chat from a list, and the dialogs that ask first. Both chat lists use one of
 * these, so the menu and dialogs work the same everywhere. onChanged reloads the list after a change;
 * onDeleted hears which chat was deleted (the main screen leaves it if it was open).
 */
@Stable
class ChatActions(
    private val context: Context,
    private val api: Api,
    private val scope: CoroutineScope,
    private val onChanged: () -> Unit,
    private val onDeleted: (String) -> Unit = {},
) {
    private var renaming by mutableStateOf<ChatItem?>(null)
    private var deleting by mutableStateOf<ChatItem?>(null)
    private var moving by mutableStateOf<ChatItem?>(null)

    /** Your folders for the Move dialog, loaded when it opens; null while loading, empty when there are none. */
    private var folders by mutableStateOf<List<Api.Folder>?>(null)
    private var foldersProblem by mutableStateOf<String?>(null)

    private fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

    /** Pins the chat, or unpins it. */
    fun pin(c: ChatItem) {
        scope.launch {
            if (runCatching { api.togglePin(c.id) }.isFailure) toast("Couldn't pin the chat")
            onChanged()
        }
    }

    /** Asks for a new name. */
    fun rename(c: ChatItem) { renaming = c }

    /** Asks before deleting. */
    fun delete(c: ChatItem) { deleting = c }

    /** Asks which folder to move the chat to, loading your folders for the choice. */
    fun move(c: ChatItem) {
        moving = c
        folders = null
        foldersProblem = null
        scope.launch {
            runCatching { api.folders() }
                .onSuccess { folders = it }
                .onFailure {
                    foldersProblem = if (it is ForbiddenException) "Folders are turned off on your server, or your account can't use them."
                    else "Couldn't load your folders."
                }
        }
    }

    /** Moves the chat, then reloads the list. */
    private fun moveTo(c: ChatItem, folderId: String?) {
        scope.launch {
            if (runCatching { api.moveChat(c.id, folderId) }.isFailure) toast("Couldn't move the chat")
            onChanged()
        }
    }

    /** The rename, delete and move dialogs, whichever is open. Place once on the screen. */
    @Composable
    fun Dialogs() {
        renaming?.let { c -> RenameDialog(c) }
        deleting?.let { c -> DeleteDialog(c) }
        moving?.let { c -> MoveDialog(c) }
    }

    @Composable
    private fun RenameDialog(c: ChatItem) {
        var title by remember(c.id) { mutableStateOf(c.title) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Rename chat") },
            text = { OutlinedTextField(value = title, onValueChange = { title = it }, singleLine = true) },
            confirmButton = {
                TextButton(enabled = title.isNotBlank(), onClick = {
                    val t = title.trim()
                    renaming = null
                    scope.launch {
                        if (runCatching { api.renameChat(c.id, t) }.isFailure) toast("Couldn't rename the chat")
                        onChanged()
                    }
                }) { Text("Rename") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } },
        )
    }

    @Composable
    private fun DeleteDialog(c: ChatItem) {
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete chat?") },
            text = { Text("“${c.title}” will be deleted from Open WebUI too.") },
            confirmButton = {
                TextButton(
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        deleting = null
                        scope.launch {
                            if (runCatching { api.deleteChat(c.id) }.isFailure) toast("Couldn't delete the chat")
                            else onDeleted(c.id)
                            onChanged()
                        }
                    },
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }

    /** Pick a folder, take the chat out of its folder, or make a new folder and put it there. */
    @Composable
    private fun MoveDialog(c: ChatItem) {
        var naming by remember(c.id) { mutableStateOf(false) }
        var name by remember(c.id) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { moving = null },
            title = { Text(if (naming) "New folder" else "Move to folder") },
            text = {
                if (naming) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Name") })
                } else {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        val list = folders
                        when {
                            foldersProblem != null -> Text(foldersProblem!!)
                            list == null -> CircularProgressIndicator(Modifier.padding(16.dp).size(24.dp))
                            else -> {
                                if (c.folderId != null) {
                                    DialogRow(Icons.Outlined.FolderOff, "Out of its folder") { moving = null; moveTo(c, null) }
                                }
                                list.filter { it.id != c.folderId }.forEach { f ->
                                    DialogRow(Icons.Outlined.Folder, f.label) { moving = null; moveTo(c, f.id) }
                                }
                                DialogRow(Icons.Outlined.CreateNewFolder, "New folder…") { naming = true }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (naming) TextButton(enabled = name.isNotBlank(), onClick = {
                    val folderName = name.trim()
                    moving = null
                    scope.launch {
                        val made = runCatching { api.createFolder(folderName) }
                        if (made.isFailure) {
                            toast("Couldn't make the folder")
                            return@launch
                        }
                        moveTo(c, made.getOrThrow().id)
                    }
                }) { Text("Make and move") }
            },
            dismissButton = { TextButton(onClick = { moving = null }) { Text("Cancel") } },
        )
    }

    @Composable
    private fun DialogRow(icon: ImageVector, text: String, onClick: () -> Unit) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 12.dp),
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }

    /** Long-press any chat for Pin, Move to folder, Rename and Delete. */
    @Composable
    fun WithMenu(c: ChatItem, pinned: Boolean, content: @Composable (longPress: () -> Unit) -> Unit) {
        var menu by remember { mutableStateOf(false) }
        val haptics = LocalHapticFeedback.current
        Box {
            content {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                menu = true
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(if (pinned) "Unpin" else "Pin") },
                    leadingIcon = { Icon(Icons.Outlined.PushPin, null) },
                    onClick = { menu = false; pin(c) },
                )
                DropdownMenuItem(
                    text = { Text("Move to folder") },
                    leadingIcon = { Icon(Icons.Outlined.DriveFileMove, null) },
                    onClick = { menu = false; move(c) },
                )
                DropdownMenuItem(
                    text = { Text("Rename") },
                    leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                    onClick = { menu = false; rename(c) },
                )
                DropdownMenuItem(
                    text = { Text("Delete") },
                    leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                    onClick = { menu = false; delete(c) },
                )
            }
        }
    }
}

// ---- Lists in each style ---------------------------------------------------------------------

/**
 * Groups of chats (a label and its chats, such as Pinned or Today) as list items, in the chosen style.
 * Every row gets the long-press menu. folded remembers which days are open in the folding style.
 */
fun LazyListScope.chatGroups(
    groups: List<Pair<String, List<ChatItem>>>,
    style: ChatListStyle,
    currentId: String?,
    pinnedIds: Set<String>,
    actions: ChatActions,
    folded: SnapshotStateMap<String, Boolean>,
    searching: Boolean,
    onOpen: (String) -> Unit,
) {
    groups.forEachIndexed { index, (label, list) ->
        val row: @Composable (ChatItem, @Composable (ChatItem, () -> Unit) -> Unit) -> Unit = { c, look ->
            actions.WithMenu(c, c.id in pinnedIds) { longPress -> look(c, longPress) }
        }
        when (style) {
            ChatListStyle.GROUPS -> item(key = "g-$label") {
                // One rounded group per day, like the rest of the app.
                Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionTitle(label, Modifier.padding(start = 4.dp))
                    Group {
                        list.forEach { c ->
                            row(c) { it, longPress ->
                                Item(
                                    icon = null, title = it.title, summary = null,
                                    selected = it.id == currentId,
                                    container = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    maxTitleLines = 1,
                                    trailing = if (it.active) { { Spinner(Modifier.padding(end = 12.dp)) } } else null,
                                    onLongClick = longPress,
                                    onClick = { onOpen(it.id) },
                                )
                            }
                        }
                    }
                }
            }
            ChatListStyle.SLIM -> item(key = "s-$label") {
                Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, top = 2.dp))
                    Column(Modifier.clip(RoundedCornerShape(16.dp)), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        list.forEach { c ->
                            row(c) { it, longPress ->
                                ChatLine(it, it.id == currentId, null, 44.dp, 16.dp, RectangleShape,
                                    MaterialTheme.colorScheme.surfaceContainerHigh, { onOpen(it.id) }, longPress)
                            }
                        }
                    }
                }
            }
            ChatListStyle.FLAT, ChatListStyle.TIMED -> {
                val timed = style == ChatListStyle.TIMED
                item(key = "h-$label") {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (timed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = if (timed) 20.dp else 12.dp, top = 14.dp, bottom = 4.dp),
                    )
                }
                items(list, key = { "$label-${it.id}" }) { c ->
                    val context = LocalContext.current
                    row(c) { it, longPress ->
                        if (timed) ChatLine(it, it.id == currentId, whenLabel(context, it.updatedMillis), 44.dp, 20.dp, RectangleShape,
                            Color.Transparent, { onOpen(it.id) }, longPress)
                        else ChatLine(it, it.id == currentId, null, 40.dp, 12.dp, RoundedCornerShape(20.dp),
                            Color.Transparent, { onOpen(it.id) }, longPress)
                    }
                }
            }
            ChatListStyle.TIMELINE -> item(key = "t-$label") {
                val line = MaterialTheme.colorScheme.outlineVariant
                val dot = if (index == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                val filled = index == 0
                Column(
                    Modifier.fillMaxWidth()
                        .drawBehind {
                            val x = 14.dp.toPx()
                            val y = 12.dp.toPx()
                            val r = 5.dp.toPx()
                            drawLine(line, Offset(x, y + r + 4.dp.toPx()), Offset(x, size.height), strokeWidth = 2.dp.toPx())
                            if (filled) drawCircle(dot, r, Offset(x, y))
                            else drawCircle(dot, r - 1.dp.toPx(), Offset(x, y), style = Stroke(2.dp.toPx()))
                        }
                        .padding(start = 28.dp, top = 4.dp, bottom = 8.dp),
                ) {
                    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 10.dp, bottom = 4.dp))
                    list.forEach { c ->
                        row(c) { it, longPress ->
                            ChatLine(it, it.id == currentId, null, 36.dp, 10.dp, RoundedCornerShape(18.dp),
                                Color.Transparent, { onOpen(it.id) }, longPress)
                        }
                    }
                }
            }
            ChatListStyle.FOLDING -> {
                val open = folded[label] ?: (searching || label == PINNED_LABEL || label == "Today" || label == "Yesterday")
                item(key = "f-$label") { FoldHeader(label, list.size, open) { folded[label] = !open } }
                if (open) items(list, key = { "$label-${it.id}" }) { c ->
                    row(c) { it, longPress ->
                        ChatLine(it, it.id == currentId, null, 40.dp, 24.dp, RoundedCornerShape(20.dp),
                            Color.Transparent, { onOpen(it.id) }, longPress)
                    }
                }
            }
        }
    }
}

/** A one-line chat row: the slim, flat, timed, timeline and folding lists, and the drawer. */
@Composable
fun ChatLine(
    c: ChatItem, selected: Boolean, time: String?, height: Dp, start: Dp, shape: Shape, idle: Color,
    onOpen: () -> Unit, onLongClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().height(height).clip(shape)
            .background(if (selected) cs.secondaryContainer else idle)
            .combinedClickable(onClick = onOpen, onLongClick = onLongClick)
            .padding(start = start, end = if (time != null) 20.dp else 12.dp),
    ) {
        Text(
            c.title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color = if (selected) cs.onSecondaryContainer else cs.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        when {
            c.active -> Spinner(Modifier.padding(start = 12.dp))
            time != null -> Text(time, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp))
        }
    }
}

/** A day, or a folder, that opens and closes: tap it. A count shows beside the name when given. */
@Composable
fun FoldHeader(
    label: String,
    count: Int?,
    open: Boolean,
    icon: ImageVector? = null,
    onLongClick: (() -> Unit)? = null,
    onToggle: () -> Unit,
) {
    val turn by animateFloatAsState(if (open) 180f else 0f, label = "fold")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(22.dp))
            .combinedClickable(onClickLabel = if (open) "Close $label" else "Open $label", onLongClick = onLongClick, onClick = onToggle)
            .padding(horizontal = 12.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Text(label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (count != null) Text(
            count.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 8.dp, vertical = 2.dp),
        )
        Icon(Icons.Rounded.ExpandMore, null, Modifier.rotate(turn), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The small spinner on a chat that's still being answered. */
@Composable
fun Spinner(modifier: Modifier = Modifier) {
    CircularProgressIndicator(modifier.size(14.dp), strokeWidth = 2.dp)
}

/** A grey line in a list: loading, empty, or why it couldn't load. */
@Composable
fun ListHint(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

/** When a chat was last used: the time today and yesterday, the weekday this week, else the date. */
fun whenLabel(context: Context, t: Long): String {
    val day = 24 * 60 * 60 * 1000L
    val today = startOfToday()
    val date = Date(t)
    val pattern = { skeleton: String -> SimpleDateFormat(DateFormat.getBestDateTimePattern(Locale.getDefault(), skeleton), Locale.getDefault()) }
    return when {
        t >= today - day -> DateFormat.getTimeFormat(context).format(date)
        t >= today - 6 * day -> pattern("EEE").format(date)
        Calendar.getInstance().apply { timeInMillis = t }.get(Calendar.YEAR) == Calendar.getInstance().get(Calendar.YEAR) ->
            pattern("MMMd").format(date)
        else -> pattern("yMMMd").format(date)
    }
}

/**
 * Groups chats by when they were last used, as Open WebUI's sidebar does: Today, Yesterday, the
 * previous 7 and 30 days, and Older.
 */
fun groupByDay(chats: List<ChatItem>): List<Pair<String, List<ChatItem>>> {
    val today = startOfToday()
    val day = 24 * 60 * 60 * 1000L
    val label = { t: Long ->
        when {
            t >= today -> "Today"
            t >= today - day -> "Yesterday"
            t >= today - 7 * day -> "Previous 7 days"
            t >= today - 30 * day -> "Previous 30 days"
            else -> "Older"
        }
    }
    return chats.groupBy { label(it.updatedMillis) }.toList()
}

/** Midnight at the start of today, in this phone's time zone. */
private fun startOfToday(): Long = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis
