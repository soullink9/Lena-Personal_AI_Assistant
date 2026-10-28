package io.github.soullink9.lena

import android.content.Context
import android.graphics.ImageDecoder
import android.text.format.DateFormat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.util.Date
import kotlin.random.Random

/** One photo on screen, with a number so the same photo twice in a row still animates. */
private data class Shown(val bitmap: ImageBitmap, val n: Int)

/**
 * The photo slideshow: an album from Home Assistant's media (Immich, a local folder...) through
 * the Lena integration, one photo at a time with the time and how she feels in a corner. Used
 * by [LenaDisplay] instead of the clock when the slideshow is on. Tap to talk.
 */
@Composable
fun PhotoFrame(slides: Slides, now: Date, house: HouseNow?, locked: Boolean, listening: Listening, onTalk: () -> Unit) {
    val context = LocalContext.current
    var shown by remember { mutableStateOf<Shown?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }

    // The album's photos, every half hour (so new ones show up), and one photo after another.
    LaunchedEffect(slides) {
        val ha = HomeAssistant.from(context) ?: run { problem = "Connect Home Assistant in Lena's settings."; return@LaunchedEffect }
        var list = emptyList<String>()
        var listedAt = 0L
        var at = 0
        var n = 0
        while (true) {
            if (list.isEmpty() || System.currentTimeMillis() - listedAt > 30 * 60_000L) {
                list = runCatching { ha.photos(slides.album) }.getOrElse { emptyList() }
                    .let { if (slides.shuffle) it.shuffled() else it }
                listedAt = System.currentTimeMillis()
                at = 0
                if (list.isEmpty()) {
                    problem = "No photos in ${slides.albumName.ifBlank { "that album" }}, or Home Assistant can't be reached."
                    delay(60_000)
                    continue
                }
            }
            val started = System.currentTimeMillis()
            val bitmap = runCatching { load(context, ha, list[at % list.size]) }.getOrNull()
            at++
            if (bitmap != null) {
                problem = null
                shown = Shown(bitmap, n++)
                delay((slides.seconds * 1000L - (System.currentTimeMillis() - started)).coerceAtLeast(1_000))
            } else {
                delay(1_000) // skip a photo that won't load
            }
        }
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClickLabel = "Talk to Lena", onClick = onTalk),
    ) {
        val current = shown
        when (slides.transition) {
            SlideTransition.SLIDE -> AnimatedContent(
                targetState = current,
                transitionSpec = {
                    (slideInHorizontally(tween(900)) { it } + fadeIn(tween(900))) togetherWith
                        (slideOutHorizontally(tween(900)) { -it } + fadeOut(tween(900)))
                },
                label = "slide",
            ) { s -> Photo(s, slides) }
            SlideTransition.NONE -> Photo(current, slides)
            else -> Crossfade(current, animationSpec = tween(1500), label = "fade") { s -> Photo(s, slides) }
        }

        if (slides.overlay) Overlay(now, house, locked, listening)
        problem?.let {
            Text(it, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.align(Alignment.Center).padding(32.dp))
        }
    }
}

@Composable
private fun Photo(s: Shown?, slides: Slides) {
    if (s == null) return
    // Slow zoom: each photo drifts in a little over the time it's shown (Ken Burns).
    val zoom = remember(s.n) { Animatable(1f) }
    val drift = remember(s.n) { Random.nextFloat() * 2 - 1 to Random.nextFloat() * 2 - 1 }
    if (slides.transition == SlideTransition.ZOOM) {
        LaunchedEffect(s.n) { zoom.animateTo(1.12f, tween(slides.seconds * 1000 + 1500, easing = LinearEasing)) }
    }
    val moving = Modifier.fillMaxSize().graphicsLayer {
        scaleX = zoom.value
        scaleY = zoom.value
        translationX = drift.first * (zoom.value - 1f) * size.width / 2
        translationY = drift.second * (zoom.value - 1f) * size.height / 2
    }
    Box(Modifier.fillMaxSize()) {
        if (slides.fill) {
            Image(s.bitmap, null, contentScale = ContentScale.Crop, modifier = moving)
        } else {
            // The whole photo, on a blurred, darkened copy of itself instead of black bars.
            Image(s.bitmap, null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(40.dp).graphicsLayer { alpha = 0.55f })
            Image(s.bitmap, null, contentScale = ContentScale.Fit, modifier = moving)
        }
    }
}

/** The time, the date and how she feels, small, over a soft shade in the corner. */
@Composable
private fun Overlay(now: Date, house: HouseNow?, locked: Boolean, listening: Listening) {
    val context = LocalContext.current
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(0.6f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.6f)),
        ),
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().navigationBarsPadding()
                .padding(horizontal = 36.dp, vertical = 28.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    DateFormat.getTimeFormat(context).format(now).replace(Regex("\\s?[AaPp]\\.?[Mm]\\.?$"), ""),
                    color = Color.White, fontSize = 64.sp, fontWeight = FontWeight.Light, lineHeight = 64.sp,
                )
                val w = house?.weather
                Text(
                    listOfNotNull(
                        DateFormat.format("EEEE, MMMM d", now).toString(),
                        w?.temperature?.let { "%.0f%s".format(it, w.unit.ifBlank { "°" }) },
                    ).joinToString("  ·  "),
                    color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.titleMedium,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (listening == Listening.ON) Icon(Icons.Rounded.Mic, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                LenaAvatar(36.dp)
                val f = house?.feelings
                Column {
                    Text(listOfNotNull("Lena", f?.mood).joinToString(" · "), color = Color.White, style = MaterialTheme.typography.titleSmall)
                    val why = f?.let { it.momentWhy.takeIf { w -> it.moment != null && w.isNotBlank() } ?: it.moodWhy }
                    if (!locked && !why.isNullOrBlank()) Text(why, color = Color.White.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(end = 4.dp))
                }
            }
        }
    }
}

/** Downloads a photo from Home Assistant and decodes it at about screen size, the right way up. */
private suspend fun load(context: Context, ha: HomeAssistant, path: String): ImageBitmap = withContext(Dispatchers.IO) {
    val bytes = ha.bytes(path)
    val screen = context.resources.displayMetrics
    val longest = maxOf(screen.widthPixels, screen.heightPixels).coerceAtLeast(1280)
    ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { decoder, info, _ ->
        val w = info.size.width
        val h = info.size.height
        val scale = longest.toFloat() / maxOf(w, h)
        if (scale < 1f) decoder.setTargetSize((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1))
    }.asImageBitmap()
}

/**
 * Picks the slideshow's album from Home Assistant's media: Immich's albums, people and
 * favorites, My media's folders, and so on. Opens on the top level; tap a folder to go in.
 */
@Composable
fun AlbumPicker(onPick: (id: String, name: String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val trail = remember { mutableStateListOf<Pair<String?, String>>(null to "Media") }
    var items by remember { mutableStateOf<List<MediaItem>?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }
    val here = trail.last()

    LaunchedEffect(here) {
        items = null
        problem = null
        val ha = HomeAssistant.from(context) ?: run { problem = "Connect Home Assistant first."; return@LaunchedEffect }
        try {
            items = ha.browse(here.first).second
        } catch (e: Exception) {
            problem = if (e.message == "HTTP 404") "Update the Lena integration in Home Assistant (1.2.0 or newer) to pick an album."
            else "Couldn't open that folder."
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.statusBarsPadding().navigationBarsPadding()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
                    IconButton(onClick = { if (trail.size > 1) trail.removeAt(trail.size - 1) else onDismiss() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                    }
                    Text(here.second, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                }
                val list = items
                val photos = list?.count { it.photo } ?: 0
                if (here.first != null && photos > 0) {
                    Button(onClick = { onPick(here.first!!, here.second) }, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp).fillMaxWidth()) {
                        Text("Use this album ($photos photo${if (photos == 1) "" else "s"})")
                    }
                }
                when {
                    problem != null -> Text(problem!!, Modifier.padding(24.dp), color = MaterialTheme.colorScheme.error)
                    list == null -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    list.none { it.folder } && photos == 0 -> Text("Nothing here.", Modifier.padding(24.dp))
                    else -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                        items(list.filter { it.folder }, key = { it.id }) { f ->
                            Box(Modifier.padding(vertical = 1.dp)) {
                                Item(Icons.Outlined.Folder, f.title, null, container = MaterialTheme.colorScheme.surfaceContainer) {
                                    trail.add(f.id to f.title)
                                }
                            }
                        }
                        if (photos > 0 && list.any { it.folder }) item {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
                                Icon(Icons.Outlined.Image, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("  and $photos photo${if (photos == 1) "" else "s"}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}
