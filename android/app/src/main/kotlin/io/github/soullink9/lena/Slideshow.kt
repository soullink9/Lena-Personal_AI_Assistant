package io.github.soullink9.lena

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.PointF
import android.media.FaceDetector
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
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.util.Date
import kotlin.math.roundToInt

/**
 * One photo on screen, with a number so the same photo twice in a row still animates, and the
 * point to keep in view when it's cropped ([fx], [fy], 0 to 1 across and down): the faces in it,
 * or a little above the middle if there are none.
 */
private data class Shown(val bitmap: ImageBitmap, val n: Int, val fx: Float, val fy: Float)

/** A photo as loaded: the picture and its focus point. */
private class Loaded(val bitmap: ImageBitmap, val fx: Float, val fy: Float)

/**
 * Places a cropped photo so its focus point is as near the middle as it can be without showing
 * an edge; a photo smaller than the space is centred.
 */
private class FocusAlignment(private val fx: Float, private val fy: Float) : Alignment {
    /** Where the photo goes in the space, so the focus point is as central as it can be. */
    override fun align(size: IntSize, space: IntSize, layoutDirection: LayoutDirection): IntOffset {
        /**
         * One axis: centred if the photo is smaller, otherwise offset to put the focus point in the middle,
         * but never so far that an edge shows.
         */
        fun axis(content: Int, room: Int, f: Float): Int =
            if (content <= room) (room - content) / 2 else (room / 2f - f * content).roundToInt().coerceIn(room - content, 0)
        return IntOffset(axis(size.width, space.width, fx), axis(size.height, space.height, fy))
    }
}

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
                list = runCatching { ha.photos(slides.album) }.getOrElse { Logs.w("Slideshow", "Couldn't list ${slides.albumName}", it); emptyList() }
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
            val photo = runCatching { load(context, ha, list[at % list.size]) }
                .onFailure { Logs.w("Slideshow", "Couldn't show a photo", it) }.getOrNull()
            at++
            if (photo != null) {
                problem = null
                shown = Shown(photo.bitmap, n++, photo.fx, photo.fy)
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

/**
 * One photo, fitted the way the setting says, slowly zooming towards its faces if that transition is
 * chosen.
 */
@Composable
private fun Photo(s: Shown?, slides: Slides) {
    if (s == null) return
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val room = constraints
        val bw = s.bitmap.width.toFloat()
        val bh = s.bitmap.height.toFloat()
        // How much of the photo filling the screen would cut off: a portrait photo on a landscape
        // screen loses about half, which is where heads went missing.
        val photoShape = bw / bh
        val screenShape = room.maxWidth.toFloat() / room.maxHeight.coerceAtLeast(1)
        val lost = 1f - minOf(photoShape, screenShape) / maxOf(photoShape, screenShape)
        val crop = when (slides.framing) {
            Framing.FILL -> true
            Framing.WHOLE -> false
            Framing.SMART -> lost <= 0.3f
        }
        // Where the focus point lands on screen, so the slow zoom moves in on the faces.
        val scale = if (crop) maxOf(room.maxWidth / bw, room.maxHeight / bh) else minOf(room.maxWidth / bw, room.maxHeight / bh)
        /**
         * Where the focus point ends up on screen (0 to 1), so the zoom grows from there and the faces stay
         * in view.
         */
        fun pivot(content: Float, space: Int, f: Float): Float {
            if (space <= 0) return 0.5f
            val offset = if (content <= space) (space - content) / 2 else (space / 2f - f * content).coerceIn(space - content, 0f)
            return ((offset + f * content) / space).coerceIn(0f, 1f)
        }
        val origin = TransformOrigin(pivot(bw * scale, room.maxWidth, s.fx), pivot(bh * scale, room.maxHeight, s.fy))

        // Slow zoom: each photo drifts in a little over the time it's shown (Ken Burns), towards its faces.
        val zoom = remember(s.n) { Animatable(1f) }
        if (slides.transition == SlideTransition.ZOOM) {
            LaunchedEffect(s.n) { zoom.animateTo(1.12f, tween(slides.seconds * 1000 + 1500, easing = LinearEasing)) }
        }
        val moving = Modifier.fillMaxSize().graphicsLayer {
            scaleX = zoom.value
            scaleY = zoom.value
            transformOrigin = origin
        }
        val focus = remember(s.n) { FocusAlignment(s.fx, s.fy) }
        if (crop) {
            Image(s.bitmap, null, contentScale = ContentScale.Crop, alignment = focus, modifier = moving)
        } else {
            // The whole photo, on a blurred, darkened copy of itself instead of black bars.
            Image(s.bitmap, null, contentScale = ContentScale.Crop, alignment = focus,
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

/** Downloads a photo from Home Assistant, decodes it at about screen size, the right way up, and finds its faces. */
private suspend fun load(context: Context, ha: HomeAssistant, path: String): Loaded = withContext(Dispatchers.IO) {
    val bytes = ha.bytes(path)
    val screen = context.resources.displayMetrics
    val longest = maxOf(screen.widthPixels, screen.heightPixels).coerceAtLeast(1280)
    val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { decoder, info, _ ->
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE // readable, for finding faces
        val w = info.size.width
        val h = info.size.height
        val scale = longest.toFloat() / maxOf(w, h)
        if (scale < 1f) decoder.setTargetSize((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1))
    }
    val (fx, fy) = focusOf(bitmap)
    Loaded(bitmap.asImageBitmap(), fx, fy)
}

/**
 * Where to keep a photo's middle when it's cropped: the faces in it (Android's own face finder,
 * on the device), between the eyes and the chin and weighted by how sure it is; without faces,
 * the upper third of a portrait photo (where heads usually are) or just above the middle.
 */
private fun focusOf(b: Bitmap): Pair<Float, Float> {
    val fallback = 0.5f to if (b.height > b.width) 0.33f else 0.45f
    return runCatching {
        val scale = 480f / maxOf(b.width, b.height)
        val w = ((b.width * minOf(scale, 1f)).toInt() / 2 * 2).coerceAtLeast(2) // the face finder needs an even width
        val h = (b.height * minOf(scale, 1f)).toInt().coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(b, w, h, true).copy(Bitmap.Config.RGB_565, false)
        val faces = arrayOfNulls<FaceDetector.Face>(8)
        val found = FaceDetector(w, h, faces.size).findFaces(small, faces)
        small.recycle()
        if (found <= 0) return fallback
        var x = 0f
        var y = 0f
        var total = 0f
        val mid = PointF()
        for (i in 0 until found) {
            val f = faces[i] ?: continue
            f.getMidPoint(mid)
            val weight = f.confidence().coerceAtLeast(0.1f)
            x += mid.x * weight
            y += (mid.y + f.eyesDistance() * 0.3f) * weight
            total += weight
        }
        if (total == 0f) fallback else (x / total / w).coerceIn(0f, 1f) to (y / total / h).coerceIn(0f, 1f)
    }.getOrDefault(fallback)
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
            problem = if (e.message == "HTTP 404") "Update the Lena integration in Home Assistant to pick an album."
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
