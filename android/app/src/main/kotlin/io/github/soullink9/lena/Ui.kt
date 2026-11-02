package io.github.soullink9.lena

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Home
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints

// Lena's look: rows in rounded groups with a thin gap between them, like a phone's own
// settings. Settings, Memory & knowledge and the drawer all use these.

/** A row's background. [container] defaults to the page's raised surface. */
@Composable
fun rowModifier(container: Color = MaterialTheme.colorScheme.surfaceContainer, minHeight: Dp = 64.dp): Modifier =
    Modifier.fillMaxWidth().heightIn(min = minHeight).background(container)

/** Rows that belong together: rounded as a whole, with a thin gap between rows. */
@Composable
fun Group(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)), verticalArrangement = Arrangement.spacedBy(2.dp), content = content)
}

/** A tappable row: icon, title, optional summary, optional trailing content. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Item(
    icon: ImageVector?,
    title: String,
    summary: String?,
    trailing: (@Composable () -> Unit)? = null,
    selected: Boolean = false,
    container: Color = MaterialTheme.colorScheme.surfaceContainer,
    maxTitleLines: Int = 2,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val background = if (selected) MaterialTheme.colorScheme.secondaryContainer else container
    val ink = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = rowModifier(background, if (summary == null) 56.dp else 64.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = 20.dp, end = if (trailing != null) 8.dp else 20.dp, top = 12.dp, bottom = 12.dp),
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(24.dp), tint = ink)
            Spacer(Modifier.width(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = ink, maxLines = maxTitleLines, overflow = TextOverflow.Ellipsis)
            if (summary != null) Text(summary, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        trailing?.invoke()
    }
}

/** A row with an on/off switch; tapping anywhere on the row flips it. */
@Composable
fun SwitchItem(icon: ImageVector?, title: String, summary: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = rowModifier().clickable { onChange(!checked) }.padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(24.dp))
            Spacer(Modifier.width(20.dp))
        }
        Column(Modifier.weight(1f).padding(end = 16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (summary != null) Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** One of several choices, with a radio button; tapping anywhere on the row picks it. */
@Composable
fun ChoiceItem(title: String, summary: String?, selected: Boolean, onSelect: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = rowModifier().clickable(onClick = onSelect).padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Column(Modifier.weight(1f).padding(end = 16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (summary != null) Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        RadioButton(selected = selected, onClick = null)
    }
}

/** A row holding a text field or other free-form content. */
@Composable
fun FieldItem(content: @Composable () -> Unit) {
    Box(rowModifier().padding(horizontal = 16.dp, vertical = 12.dp)) { content() }
}

/** A titled group, for pages with several kinds of thing. */
@Composable
fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(title)
        Group(content = content)
    }
}

/** A small coloured heading above a group. */
@Composable
fun SectionTitle(title: String, modifier: Modifier = Modifier) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 20.dp, top = 4.dp))
}

/** A short explanation between groups. */
@Composable
fun Footnote(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 20.dp))
}

/**
 * A row showing only the current choice; tapping it opens every option to pick from. Settings uses it for
 * anything with more than two choices, so pages stay short. detail adds a line under an option in the
 * list; enabled greys out options that can't be picked (detail can say why).
 */
@Composable
fun <T> PickItem(
    icon: ImageVector?,
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    detail: (T) -> String? = { null },
    enabled: (T) -> Boolean = { true },
    onPick: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Item(icon, title, options.firstOrNull { it.first == selected }?.second ?: "Choose") { open = true }
    if (open) AlertDialog(
        onDismissRequest = { open = false },
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                options.forEach { (value, label) ->
                    val can = enabled(value)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = can) { onPick(value); open = false }.padding(vertical = 4.dp),
                    ) {
                        RadioButton(selected = value == selected, onClick = null, enabled = can, modifier = Modifier.padding(horizontal = 8.dp))
                        Column(Modifier.padding(vertical = 8.dp)) {
                            Text(label, style = MaterialTheme.typography.bodyLarge,
                                color = if (can) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                            detail(value)?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
    )
}

// ---- Lena's two main screens ---------------------------------------------------------------

/** Lena's two main screens: your chats (Open WebUI) and Home Assistant's dashboards. */
enum class MainScreen(val label: String) { CHAT("Chat"), HOME("Home") }

/**
 * The small Chat | Home switch at the top left of both main screens, beside Lena's logo. The current
 * screen is highlighted; tapping the other one goes there.
 */
@Composable
fun ScreenSwitcher(current: MainScreen, onSwitch: (MainScreen) -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier.clip(RoundedCornerShape(50)).background(cs.surfaceContainerHigh).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        MainScreen.entries.forEach { screen ->
            val on = screen == current
            val icon = if (screen == MainScreen.CHAT) Icons.Outlined.ChatBubbleOutline else Icons.Outlined.Home
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.clip(RoundedCornerShape(50))
                    .background(if (on) cs.secondaryContainer else Color.Transparent)
                    .semantics { selected = on; role = Role.Tab }
                    .clickable(enabled = !on) { onSwitch(screen) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                val ink = if (on) cs.onSecondaryContainer else cs.onSurfaceVariant
                Icon(icon, null, tint = ink, modifier = Modifier.size(18.dp))
                Text(screen.label, style = MaterialTheme.typography.labelLarge, color = ink)
            }
        }
    }
}

// ---- A column that never scrolls ------------------------------------------------------------

/** Marks a heading in a FitColumn, so it's left out when nothing under it fits. */
const val FIT_HEADING = "heading"

/**
 * A column that shows only what fits, top to bottom, and never scrolls: the first child that doesn't
 * fit, and everything after it, is left out. A heading (Modifier.layoutId(FIT_HEADING)) left at the
 * bottom with nothing under it goes too. The drawer uses it, with View all chats for the rest.
 */
@Composable
fun FitColumn(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content, modifier) { measurables, constraints ->
        val each = constraints.copy(minWidth = 0, minHeight = 0, maxHeight = Constraints.Infinity)
        val height = constraints.maxHeight
        val shown = mutableListOf<Int>()
        val placeables = mutableListOf<androidx.compose.ui.layout.Placeable>()
        var used = 0
        for ((index, measurable) in measurables.withIndex()) {
            val placeable = measurable.measure(each)
            if (used + placeable.height > height) break
            placeables += placeable
            shown += index
            used += placeable.height
        }
        // A heading at the very end has nothing under it on screen: leave it out.
        while (shown.isNotEmpty() && measurables[shown.last()].layoutId == FIT_HEADING) {
            shown.removeAt(shown.size - 1)
            used -= placeables.removeAt(placeables.size - 1).height
        }
        layout(constraints.maxWidth, height) {
            var y = 0
            placeables.forEach { p ->
                p.place(0, y)
                y += p.height
            }
        }
    }
}

// ---- Fitting any screen ---------------------------------------------------------------------
//
// Foldables have a narrow outer screen and a wide inner one, and Lena runs on both (and on tablets).
// Big display elements shrink on small screens, pages of rows stop growing on wide ones, and the
// wall display's bar folds its extras into a menu when it's narrow.

/**
 * How much big display elements (her clock, the orb) are scaled for this screen: 1 on an ordinary
 * phone and anything larger, less on a narrow outer screen (down to 0.6). Based on the screen's
 * shorter side, so turning the device doesn't change it.
 */
@Composable
fun displayScale(): Float {
    val c = androidx.compose.ui.platform.LocalConfiguration.current
    return (minOf(c.screenWidthDp, c.screenHeightDp) / 400f).coerceIn(0.6f, 1f)
}

/** The widest a page of rows (Settings, All chats, Memory & knowledge) gets; on wider screens it's centred. */
val PAGE_MAX_WIDTH: Dp = 720.dp

/** Whether this screen is narrow, like a foldable's outer screen or a phone held upright (under 600dp across). */
@Composable
fun isNarrowScreen(): Boolean = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 600

