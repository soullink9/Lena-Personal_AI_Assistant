package io.github.soullink9.lena

import android.content.Context
import android.content.Intent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Date

/**
 * Lena's home screen on a TV ([Tv]): rows you move through with the remote, as on any TV, instead of the
 * tablet's bar and dashboard. Over your photo slideshow (dimmed), with the time, the weather and how she
 * feels at the top:
 *
 * - **Lena**: Talk to Lena (chosen when the screen opens, so OK held or pressed talks straight away), and
 *   every timer, alarm and reminder that's running.
 * - **Home Assistant**: your wall display dashboards; one opens full screen, Back returns here.
 * - **Apps**: your TV apps, the ones you open most first (Lena is the TV's home screen, so this is where
 *   Netflix and the rest are), and All apps.
 * - **Settings**: Lena's, and the TV's.
 */
@Composable
fun TvHome(
    now: HouseNow,
    clock: Date,
    dashboards: List<Dashboard>,
    onTalk: () -> Unit,
    onDashboard: (Dashboard) -> Unit,
    onAllApps: () -> Unit,
    onSettings: () -> Unit,
) {
    val context = LocalContext.current
    val slides = remember { Prefs.slides(context) }
    val countdowns by Timers.items(context).collectAsState()
    var apps by remember { mutableStateOf<List<TvApp>>(emptyList()) }
    val talkFocus = remember { FocusRequester() }
    // A second's tick for the timers' time left.
    var tick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            tick = System.currentTimeMillis()
        }
    }
    // The TV's apps, again whenever the screen comes back (one may have been installed meanwhile).
    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) { TvApps.list(context) }
    }
    LaunchedEffect(Unit) { runCatching { talkFocus.requestFocus() } }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // Your photos behind, when an album is chosen (Settings → Wall display → Photo slideshow); not
        // something the remote can land on.
        if (slides.album.isNotBlank()) PhotoFrame(slides.copy(overlay = false), clock, null, locked = false, listening = Listening.OFF, onTalk = null)
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color(0xCC000000), Color(0x99000000), Color(0xE6000000)))
            )
        )
        LazyColumn(
            contentPadding = PaddingValues(start = 56.dp, end = 56.dp, top = 32.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item { Header(now, clock) }
            item {
                TvRow("Lena") {
                    item {
                        TvCard(320.dp, Modifier.focusRequester(talkFocus), onClick = onTalk) {
                            CardLabel(Icons.Outlined.Mic, "Talk to Lena", "Or hold OK anywhere")
                        }
                    }
                    items(countdowns.orEmpty(), key = { it.id }) { c ->
                        TvCard(240.dp, onClick = onTalk) {
                            CardLabel(countdownIcon(c.kind), c.name(context), left(context, c, tick))
                        }
                    }
                }
            }
            if (dashboards.isNotEmpty()) item {
                TvRow("Home Assistant") {
                    items(dashboards, key = { it.path }) { d ->
                        TvCard(240.dp, onClick = { onDashboard(d) }) { CardLabel(Icons.Outlined.Dashboard, d.name, null) }
                    }
                }
            }
            item {
                TvRow("Apps") {
                    items(apps, key = { it.packageName }) { a ->
                        TvCard(240.dp, onClick = { TvApps.open(context, a.packageName) }) {
                            if (a.banner != null) {
                                Image(a.banner, a.label, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            } else {
                                Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                                    Image(a.icon, null, modifier = Modifier.size(56.dp))
                                    Spacer(Modifier.height(8.dp))
                                    Text(a.label, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                    item { TvCard(240.dp, onClick = onAllApps) { CardLabel(Icons.Outlined.Apps, "All apps", null) } }
                }
            }
            item {
                TvRow("Settings") {
                    item { TvCard(240.dp, onClick = onSettings) { CardLabel(Icons.Outlined.Settings, "Lena's settings", null) } }
                    item {
                        TvCard(240.dp, onClick = {
                            runCatching { context.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        }) { CardLabel(Icons.Outlined.Tv, "TV settings", null) }
                    }
                }
            }
        }
    }
}

/** The time, the date and weather, and how she feels: the top of the TV home. */
@Composable
private fun Header(now: HouseNow, clock: Date) {
    val context = LocalContext.current
    Column {
        Text(android.text.format.DateFormat.getTimeFormat(context).format(clock), style = MaterialTheme.typography.displayLarge, color = Color.White)
        val weather = now.weather?.let { w ->
            listOfNotNull(w.temperature?.let { "%.0f%s".format(it, w.unit.ifBlank { "°" }) },
                w.condition.replace('-', ' ').replaceFirstChar { it.uppercase() }).joinToString(" ")
        }
        Text(listOfNotNull(android.text.format.DateFormat.format("EEEE, MMMM d", clock).toString(), weather).joinToString(" · "),
            style = MaterialTheme.typography.titleLarge, color = Color.White.copy(alpha = 0.85f))
        now.feelings?.let { f ->
            Text("Lena feels ${f.mood.lowercase()}" + if (f.moodWhy.isNotBlank()) ": ${f.moodWhy}" else "",
                style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** A row's title and its cards, side by side; the remote moves along it with left and right. */
@Composable
private fun TvRow(title: String, cards: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = Color.White)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            // Room for a chosen card to grow without being cut off.
            contentPadding = PaddingValues(vertical = 10.dp, horizontal = 10.dp),
            content = cards,
        )
    }
}

/**
 * One card, 16:9 like the TV's own: the one the remote is on grows a little and gets an outline, so it's
 * clear from the sofa.
 */
@Composable
private fun TvCard(width: Dp, modifier: Modifier = Modifier, onClick: () -> Unit, content: @Composable () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val grow by animateFloatAsState(if (focused) 1.08f else 1f, label = "card")
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier
            .scale(grow)
            .size(width, width * 9 / 16)
            .clip(shape)
            .background(Color(0xFF24252E))
            .border(if (focused) 3.dp else 0.dp, if (focused) Color.White else Color.Transparent, shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) { content() }
}

/** A card's icon, name and a line under it. */
@Composable
private fun CardLabel(icon: ImageVector, title: String, detail: String?) {
    Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.Bottom) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(36.dp))
        Spacer(Modifier.height(10.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (detail != null) Text(detail, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.7f), maxLines = 1)
    }
}

/** What's left: "4:32 left" for a timer, "7:30 AM" for an alarm or reminder. */
private fun left(c: Context, countdown: Countdown, now: Long): String = when (countdown.kind) {
    CountdownKind.TIMER -> {
        val seconds = ((countdown.at - now) / 1000).coerceAtLeast(0)
        if (seconds >= 3600) "%d:%02d:%02d left".format(seconds / 3600, seconds / 60 % 60, seconds % 60)
        else "%d:%02d left".format(seconds / 60, seconds % 60)
    }
    else -> Timers.clock(c, countdown.at)
}

/** A TV app on the home screen: its banner (the wide picture TV apps have) or, failing that, its icon. */
data class TvApp(val label: String, val packageName: String, val banner: ImageBitmap?, val icon: ImageBitmap)

/** The TV's apps for the Apps row, and opening them; the ones you open most come first. */
object TvApps {
    private const val PREFS = "lena_tv_apps"

    /**
     * The TV's apps (Android TV's own launcher category), without Lena or the ones hidden in the Apps list,
     * most opened first, then by name. Slow (it loads pictures): call off the main thread.
     */
    fun list(c: Context): List<TvApp> {
        val pm = c.packageManager
        val opened = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val hidden = Prefs.hiddenApps(c)
        return pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0)
            .filter { it.activityInfo.packageName != c.packageName && it.activityInfo.packageName !in hidden }
            .distinctBy { it.activityInfo.packageName }
            .map { r ->
                val banner = runCatching { r.activityInfo.loadBanner(pm) }.getOrNull()
                    ?: runCatching { pm.getApplicationBanner(r.activityInfo.packageName) }.getOrNull()
                TvApp(
                    label = r.loadLabel(pm).toString(),
                    packageName = r.activityInfo.packageName,
                    banner = banner?.toBitmap(320, 180)?.asImageBitmap(),
                    icon = r.loadIcon(pm).toBitmap(96, 96).asImageBitmap(),
                )
            }
            .sortedWith(compareByDescending<TvApp> { opened.getInt(it.packageName, 0) }.thenBy { it.label.lowercase() })
    }

    /** Opens an app through its TV entry (or its phone one), and counts it for the row's order. */
    fun open(c: Context, packageName: String) {
        val pm = c.packageManager
        val launch = pm.getLeanbackLaunchIntentForPackage(packageName) ?: pm.getLaunchIntentForPackage(packageName) ?: return
        val opened = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        opened.edit().putInt(packageName, opened.getInt(packageName, 0) + 1).apply()
        runCatching { c.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { Logs.w("TV", "Couldn't open $packageName", it) }
    }

}
