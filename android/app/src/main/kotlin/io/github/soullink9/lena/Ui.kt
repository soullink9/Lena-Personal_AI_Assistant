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

/** A row showing the current choice; tapping it opens a short list to pick from. Keeps long option lists off the page. */
@Composable
fun <T> PickItem(icon: ImageVector?, title: String, options: List<Pair<T, String>>, selected: T, onPick: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Item(icon, title, options.firstOrNull { it.first == selected }?.second ?: "Choose") { open = true }
    if (open) AlertDialog(
        onDismissRequest = { open = false },
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (value, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .clickable { onPick(value); open = false }.padding(vertical = 4.dp),
                    ) {
                        RadioButton(selected = value == selected, onClick = null, modifier = Modifier.padding(horizontal = 8.dp))
                        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 10.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
    )
}
