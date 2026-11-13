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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.BackHandler
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Videocam
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
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Date

/**
 * Lena's home screen on a TV ([Tv]): rows you move through with the remote, as on any TV, instead of the
 * tablet's bar and dashboard. Over your photo slideshow (dimmed), with the time, the weather and how she
 * feels at the top, inside the TV's safe area (TVs crop the picture's edges):
 *
 * - **Lena**: Talk to Lena (chosen when the screen opens, so OK talks straight away), every timer, alarm
 *   and reminder that's running, your Home Assistant dashboards (one opens full screen, Back returns
 *   here), and Lena's and the TV's settings.
 * - **Apps**: your TV apps, the ones you open most first (Lena is the TV's home screen, so this is where
 *   Netflix and the rest are), Choose apps (which ones show here) and All apps.
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
    var choosing by remember { mutableStateOf(false) }
    val talkFocus = remember { FocusRequester() }
    // A second's tick for the timers' time left.
    var tick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            tick = System.currentTimeMillis()
        }
    }
    // The TV's apps, again after choosing which show.
    LaunchedEffect(choosing) {
        if (!choosing) apps = withContext(Dispatchers.IO) { TvApps.list(context, withHidden = false) }
    }
    // Talk to Lena is chosen when the screen opens, and again after Choose apps.
    LaunchedEffect(choosing) { if (!choosing) talkFocus.focusWhenShown() }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // Your photos behind, when an album is chosen (Settings → Wall display → Photo slideshow); not
        // something the remote can land on.
        if (slides.album.isNotBlank()) PhotoFrame(slides.copy(overlay = false), clock, null, locked = false, listening = Listening.OFF, onTalk = null)
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color(0xCC000000), Color(0x99000000), Color(0xE6000000)))
            )
        )
        // Choose apps takes the screen on its own, so the remote can't wander to the rows behind it.
        if (choosing) ChooseApps(onDone = { choosing = false })
        // Android TV's safe area: 48dp at the sides, 27dp top and bottom.
        else Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 48.dp, vertical = 27.dp),
        ) {
            Header(now, clock)
            TvRow("Lena") {
                item {
                    TvCard(TALK_WIDTH, Modifier.focusRequester(talkFocus), onClick = onTalk) {
                        CardLabel(Icons.Outlined.Mic, "Talk to Lena", "Or hold OK anywhere")
                    }
                }
                // Camera mode, when it's on here (Settings → Cameras): its own card, next to talking to her, that
                // opens it straight away. Opened only from here (or by asking), never by itself, and it shows no
                // pictures here: the cameras run only while camera mode is open.
                if (Cameras.available(context)) item {
                    TvCard(TALK_WIDTH, onClick = { Cameras.open(context) }) { CamerasCard(Cameras.included(context).size) }
                }
                items(countdowns.orEmpty(), key = { it.id }) { c ->
                    TvCard(CARD_WIDTH, onClick = onTalk) {
                        CardLabel(countdownIcon(c.kind), c.name(context), left(context, c, tick))
                    }
                }
                items(dashboards, key = { "dashboard:" + it.path }) { d ->
                    TvCard(CARD_WIDTH, onClick = { onDashboard(d) }) { CardLabel(Icons.Outlined.Dashboard, d.name, "Home Assistant") }
                }
                item { TvCard(CARD_WIDTH, onClick = onSettings) { CardLabel(Icons.Outlined.Settings, "Lena's settings", null) } }
                item {
                    TvCard(CARD_WIDTH, onClick = {
                        runCatching { context.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    }) { CardLabel(Icons.Outlined.Tv, "TV settings", null) }
                }
            }
            TvRow("Apps") {
                items(apps, key = { it.packageName }) { a ->
                    TvCard(CARD_WIDTH, onClick = { TvApps.open(context, a.packageName) }) { AppPicture(a) }
                }
                item { TvCard(CARD_WIDTH, onClick = { choosing = true }) { CardLabel(Icons.Outlined.Edit, "Choose apps", "Which show here") } }
                item { TvCard(CARD_WIDTH, onClick = onAllApps) { CardLabel(Icons.Outlined.Apps, "All apps", null) } }
            }
        }
    }
}

/**
 * Gives a card the remote as soon as it's on screen. Cards in a row are laid out a moment after the
 * screen asks (rows only make the cards they show), and asking before that does nothing, so it tries again
 * for up to a second.
 */
private suspend fun FocusRequester.focusWhenShown() {
    repeat(20) {
        if (runCatching { requestFocus() }.isSuccess) return
        delay(50)
    }
}

/** Cards are 16:9 like the TV's own; Talk to Lena a little bigger. Sized for a TV seen from the sofa. */
private val TALK_WIDTH = 208.dp
private val CARD_WIDTH = 176.dp

/** An app's banner (the wide picture TV apps have), or its icon and name when it has none. */
@Composable
private fun AppPicture(a: TvApp) {
    if (a.banner != null) {
        Image(a.banner, a.label, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    } else {
        Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Image(a.icon, null, modifier = Modifier.size(36.dp))
            Spacer(Modifier.height(6.dp))
            Text(a.label, style = MaterialTheme.typography.bodySmall, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * Choose apps: every TV app, the hidden ones faded with "Hidden" on them; OK shows or hides one (it stays
 * installed). Back is done. The same list the tablet's Apps screen hides from.
 */
@Composable
private fun ChooseApps(onDone: () -> Unit) {
    val context = LocalContext.current
    var all by remember { mutableStateOf<List<TvApp>?>(null) }
    var hidden by remember { mutableStateOf(Prefs.hiddenApps(context)) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { all = withContext(Dispatchers.IO) { TvApps.list(context, withHidden = true) } }
    BackHandler { onDone() }
    Box(Modifier.fillMaxSize().background(Color(0xF2101014))) {
        Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 27.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Choose apps", style = MaterialTheme.typography.headlineSmall, color = Color.White)
            Text("Press OK on an app to show or hide it on the home screen. It stays installed. Back when you're done.",
                style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.75f))
            val list = all
            if (list == null) {
                Text("Loading…", color = Color.White)
            } else {
                LazyVerticalGrid(GridCells.Adaptive(CARD_WIDTH + 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(10.dp)) {
                    items(list.size, key = { list[it].packageName }) { i ->
                        val a = list[i]
                        val isHidden = a.packageName in hidden
                        TvCard(CARD_WIDTH, if (i == 0) Modifier.focusRequester(first) else Modifier, onClick = {
                            hidden = if (isHidden) hidden - a.packageName else hidden + a.packageName
                            Prefs.setHiddenApps(context, hidden)
                        }) {
                            Box(Modifier.fillMaxSize().alpha(if (isHidden) 0.35f else 1f)) { AppPicture(a) }
                            if (isHidden) Text("Hidden", style = MaterialTheme.typography.labelLarge, color = Color.White,
                                modifier = Modifier.align(Alignment.Center).background(Color(0xCC000000), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 2.dp))
                        }
                    }
                }
                LaunchedEffect(list) { first.focusWhenShown() }
            }
        }
    }
}

/** The time, the date and weather, and how she feels: the top of the TV home. */
@Composable
private fun Header(now: HouseNow, clock: Date) {
    val context = LocalContext.current
    Column {
        Text(android.text.format.DateFormat.getTimeFormat(context).format(clock), style = MaterialTheme.typography.displaySmall, color = Color.White)
        val weather = now.weather?.let { w ->
            listOfNotNull(w.temperature?.let { "%.0f%s".format(it, w.unit.ifBlank { "°" }) },
                w.condition.replace('-', ' ').replaceFirstChar { it.uppercase() }).joinToString(" ")
        }
        Text(listOfNotNull(android.text.format.DateFormat.format("EEEE, MMMM d", clock).toString(), weather).joinToString(" · "),
            style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.85f))
        now.feelings?.let { f ->
            Text("Lena feels ${f.mood.lowercase()}" + if (f.moodWhy.isNotBlank()) ": ${f.moodWhy}" else "",
                style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** A row's title and its cards, side by side; the remote moves along it with left and right. */
@Composable
private fun TvRow(title: String, cards: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            // Room for a chosen card to grow without being cut off.
            contentPadding = PaddingValues(vertical = 8.dp, horizontal = 8.dp),
            content = cards,
        )
    }
}

/**
 * One card, 16:9 like the TV's own: the one the remote is on grows a little and gets an outline, so it's
 * clear from the sofa.
 */
@Composable
private fun TvCard(width: Dp, modifier: Modifier = Modifier, onClick: () -> Unit, content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val grow by animateFloatAsState(if (focused) 1.08f else 1f, label = "card")
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier
            .scale(grow)
            .size(width, width * 9 / 16)
            .clip(shape)
            .background(Color(0xFF24252E))
            .border(if (focused) 3.dp else 0.dp, if (focused) Color.White else Color.Transparent, shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        content = content,
    )
}

/**
 * Camera mode's card, in camera mode's own look ([CameraLook]): a dark monitor with its glass, a blinking
 * REC, and how many cameras it shows.
 */
@Composable
private fun CamerasCard(count: Int) {
    Box(Modifier.fillMaxSize().background(CameraLook.screen).monitorGlass()) {
        Box(Modifier.align(Alignment.TopStart).padding(12.dp)) { CameraLook.LiveDot("REC", size = 12.sp) }
        Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
            Icon(Icons.Outlined.Videocam, null, tint = CameraLook.osd, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(6.dp))
            Text("CAMERAS", style = CameraLook.text(15.sp, bold = true))
            Text(
                when (count) {
                    0 -> "NONE INCLUDED YET"
                    1 -> "1 CAMERA"
                    else -> "$count CAMERAS"
                },
                style = CameraLook.text(11.sp, CameraLook.osdDim), maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A card's icon, name and a line under it. */
@Composable
private fun CardLabel(icon: ImageVector, title: String, detail: String?) {
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.Bottom) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(6.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
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
     * The TV's apps (Android TV's own launcher category), without Lena and (unless [withHidden]) the ones
     * hidden with Choose apps, most opened first, then by name. Slow (it loads pictures): call off the main thread.
     */
    fun list(c: Context, withHidden: Boolean): List<TvApp> {
        val pm = c.packageManager
        val opened = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val hidden = if (withHidden) emptySet() else Prefs.hiddenApps(c)
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
