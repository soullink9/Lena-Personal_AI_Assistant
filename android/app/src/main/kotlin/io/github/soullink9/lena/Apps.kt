package io.github.soullink9.lena

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** An app in the apps list. */
private data class LauncherApp(val label: String, val packageName: String, val icon: ImageBitmap)

/**
 * Every app with a launcher icon, except Lena, sorted by name. On a TV that's the TV's apps (Android TV's
 * own "leanback" launcher category) as well, since many TV apps have no phone icon. Slow (it loads
 * icons): call off the main thread.
 */
private fun launcherApps(c: Context): List<LauncherApp> {
    val pm = c.packageManager
    val categories = if (Tv.isTv(c)) listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER) else listOf(Intent.CATEGORY_LAUNCHER)
    return categories.flatMap { pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(it), 0) }
        .filter { it.activityInfo.packageName != c.packageName }
        .map { r ->
            LauncherApp(
                r.loadLabel(pm).toString(),
                r.activityInfo.packageName,
                r.loadIcon(pm).toBitmap(96, 96, Bitmap.Config.ARGB_8888).asImageBitmap(),
            )
        }
        .distinctBy { it.packageName }
        .sortedBy { it.label.lowercase() }
}

/**
 * The apps list, for the wall display (where Lena is the home screen and there's no other way to open
 * apps): from Apps on the dashboards' bar or Home Assistant's menu, and at the bottom of the chats' menu.
 *
 * **Edit** chooses which apps it shows. Hidden apps only leave this list: Android, its launcher and its
 * settings don't change. The choice stays through Return to defaults ([Prefs.hiddenApps]).
 */
@Composable
fun AppsDrawer(onClose: () -> Unit) {
    val context = LocalContext.current
    var list by remember { mutableStateOf<List<LauncherApp>?>(null) }
    var hidden by remember { mutableStateOf(Prefs.hiddenApps(context)) }
    var editing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        list = withContext(Dispatchers.IO) { launcherApps(context) }
    }
    // Back finishes choosing first, then closes the list.
    BackHandler { if (editing) editing = false else onClose() }
    // Always light text on near-black, whatever the theme, so every app name is readable.
    Surface(color = Color(0xF2101014), contentColor = Color.White, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.statusBarsPadding().navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(if (editing) "Choose apps" else "Apps", style = MaterialTheme.typography.headlineSmall, color = Color.White)
                    if (editing) {
                        Text("Tap an app to show or hide it here. It stays installed.", style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f))
                    }
                }
                if (editing) {
                    TextButton(onClick = { editing = false }) { Text("Done", color = Color.White) }
                } else {
                    IconButton(onClick = { editing = true }) { Icon(Icons.Outlined.Edit, "Choose apps", tint = Color.White) }
                    IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Close", tint = Color.White) }
                }
            }
            val apps = list
            if (apps == null) {
                Text("Loading…", Modifier.padding(24.dp), color = Color.White)
                return@Column
            }
            // Editing shows every app (hidden ones faded); otherwise only the ones you show.
            val shown = if (editing) apps else apps.filter { it.packageName !in hidden }
            if (shown.isEmpty()) {
                Text("Every app is hidden. Tap the pencil to show some.", Modifier.padding(24.dp), color = Color.White)
                return@Column
            }
            LazyVerticalGrid(GridCells.Adaptive(104.dp), Modifier.fillMaxSize().padding(12.dp)) {
                items(shown, key = { it.packageName }) { a ->
                    val isHidden = a.packageName in hidden
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable {
                            if (editing) {
                                hidden = if (isHidden) hidden - a.packageName else hidden + a.packageName
                                Prefs.setHiddenApps(context, hidden)
                            } else {
                                onClose()
                                // A TV app may only have a TV entry (leanback), and a phone app only a phone one.
                                (context.packageManager.getLeanbackLaunchIntentForPackage(a.packageName)
                                    ?: context.packageManager.getLaunchIntentForPackage(a.packageName))?.let { context.startActivity(it) }
                            }
                        }.padding(vertical = 14.dp).alpha(if (editing && isHidden) 0.4f else 1f),
                    ) {
                        Box {
                            Image(a.icon, null, Modifier.size(56.dp))
                            if (editing) {
                                Icon(
                                    if (isHidden) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    if (isHidden) "Hidden" else "Shown",
                                    tint = Color.White,
                                    modifier = Modifier.align(Alignment.BottomEnd).size(20.dp),
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(a.label, style = MaterialTheme.typography.labelLarge, color = Color.White, maxLines = 2, textAlign = TextAlign.Center,
                            overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
