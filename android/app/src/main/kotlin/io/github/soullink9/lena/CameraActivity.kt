package io.github.soullink9.lena

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Camera mode: the Home Assistant cameras included on this device (Settings → Cameras), as a grid of still
 * pictures or one live in the spotlight, in the five views of [CameraDirector]. Opened only by someone (the
 * Cameras card on the TV home, the bar's camera button, Settings, or asking her to show the cameras), and
 * everything stops when it closes: the connection to Home Assistant, the pictures, the motion it watches.
 *
 * The remote: arrows move between pictures, OK puts one in the spotlight, OK held selects it, left and right
 * change camera in the spotlight, Menu chooses the view, Back goes to the grid and then out. On a touch
 * screen: tap, long press, the arrows on the spotlight, and the views along the top.
 */
class CameraActivity : ComponentActivity() {

    // Dark on a TV (Tv.darkOnTv).
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase)
        Tv.darkOnTv(this, newBase)
    }

    companion object {
        private const val TAG = "Cameras"
        private const val TICK_MS = 500L
        private const val STILL_MS = 2_000L

        /** What was asked ("show me the front door camera"): a camera named in it opens in the spotlight. */
        const val EXTRA_ASKED = "asked"

        /** More cameras than fit a screen (3 × 3): their pictures refresh half as often. */
        private const val MANY = 9
    }

    private val tv by lazy { Tv.isTv(this) }

    private var socket: HaSocket? = null
    private var live: CameraLive? = null
    private var director: CameraDirector? = null
    private var running: Job? = null

    /** The included cameras, in their order. */
    private var cameras by mutableStateOf<List<FoundCamera>>(emptyList())
    private var screen by mutableStateOf<CameraDirector.Screen?>(null)
    private var problem by mutableStateOf<String?>(null)
    private var liveState by mutableStateOf(CameraLive.State.CONNECTING)
    private var choosingView by mutableStateOf(false)
    private var selected by mutableStateOf<Set<String>>(emptySet())
    private var view by mutableStateOf(CameraView.SHOW_ALL)

    /** The latest still picture of each camera (by camera ID), in memory only. */
    private val stills = mutableStateMapOf<String, ImageBitmap>()

    /** Which of each camera's motion sensors are on now: the camera moves while any is. */
    private val activeSensors = HashMap<String, MutableSet<String>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // A camera wall stays on while it's open; the rest of the time Android's own screen timeout applies.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent { LenaTheme { Screen() } }
    }

    /** Asked again while open ("show the back yard"): starts over with that camera. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val asked = intent.getStringExtra(EXTRA_ASKED).orEmpty()
        val d = director ?: return
        Cameras.named(cameras, asked)?.let { d.choose(it.id); tick() }
        intent.removeExtra(EXTRA_ASKED)
    }

    override fun onStart() {
        super.onStart()
        problem = null
        running = lifecycleScope.launch { runCameras() }
    }

    /** Everything stops when camera mode isn't on screen: cameras only run while it's open. */
    override fun onStop() {
        running?.cancel()
        running = null
        live?.destroy()
        live = null
        socket?.close()
        socket = null
        director?.let { Cameras.setSelected(this, it.selected) }
        director = null
        screen = null
        cameras = emptyList()
        liveState = CameraLive.State.CONNECTING
        stills.clear()
        activeSensors.clear()
        super.onStop()
    }

    /** Connects, finds the included cameras, watches their motion and keeps the screen up to date. */
    private suspend fun runCameras() {
        if (!Prefs.cameras(this).on) {
            problem = "Camera mode is off on ${Tv.thisDevice(this)}. Turn it on in Settings → Cameras."
            return
        }
        if (!Cameras.available(this)) {
            problem = "Camera mode needs Home Assistant. Connect it in Settings → Home Assistant."
            return
        }
        val settings = Prefs.cameras(this)
        try {
            // If camera mode closes while it connects, the connection made meanwhile is closed, not left open.
            var opened: HaSocket? = null
            val sock = try {
                withContext(Dispatchers.IO) { HaSocket.open(this@CameraActivity).also { opened = it } }
            } catch (e: CancellationException) {
                opened?.close()
                throw e
            }
            socket = sock
            sock.onClosed = { runOnUiThread { problem = "The connection to Home Assistant ended. Open camera mode again to reconnect." } }
            val found = Cameras.find(sock)
            val list = Cameras.included(this).mapNotNull { id -> found.firstOrNull { it.id == id } }
            if (list.isEmpty()) {
                problem = "No cameras are included on ${Tv.thisDevice(this)} yet. Choose them in Settings → Cameras."
                return
            }
            cameras = list
            val d = CameraDirector(list.map { it.id }, Cameras.selected(this), settings.view,
                settings.rotateSeconds * 1000L, settings.holdSeconds * 1000L)
            director = d
            selected = d.selected
            view = d.view
            // Asked for one by name: it opens in the spotlight (once; Back goes to the grid).
            intent.getStringExtra(EXTRA_ASKED)?.let { asked -> Cameras.named(list, asked)?.let { d.choose(it.id) } }
            intent.removeExtra(EXTRA_ASKED)
            Logs.i(TAG, "Camera mode: ${list.size} camera(s), ${d.view.label}")

            watchMotion(sock, list, settings.motion)
            val base = Prefs.haServer(this) ?: return
            live = CameraLive(this, sock, base, settings.sound, lifecycleScope) { liveState = it }
            coroutineScope {
                launch {
                    while (true) {
                        tick()
                        delay(TICK_MS)
                    }
                }
                launch { refreshStills() }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: HaAuthException) {
            problem = "Home Assistant didn't accept Lena's token. Connect it again in Settings → Home Assistant."
        } catch (e: Exception) {
            Logs.w(TAG, "Camera mode couldn't start", e)
            problem = "Couldn't reach Home Assistant's cameras: ${e.message}"
        }
    }

    /**
     * Subscribes to the motion sensors and events of the included cameras (only those), and tells the
     * director when each camera starts and stops moving. Home Assistant sends their states first (a sensor
     * already on counts as moving), then each change.
     */
    private suspend fun watchMotion(sock: HaSocket, list: List<FoundCamera>, kind: MotionKind) {
        val cameraOf = HashMap<String, String>()
        list.forEach { camera ->
            CameraFinder.triggers(camera, kind, Cameras.chosenSensor(this, camera.id)).forEach { cameraOf[it] = camera.id }
        }
        if (cameraOf.isEmpty()) return
        sock.subscribe(JSONObject().put("type", "subscribe_entities").put("entity_ids", JSONArray(cameraOf.keys.toList()))) { event ->
            val changes = mutableListOf<Triple<String, String, Boolean>>() // entity, state, whether it's the first report
            event.optJSONObject("a")?.let { added ->
                added.keys().forEach { entity -> changes += Triple(entity, added.optJSONObject(entity)?.optString("s").orEmpty(), true) }
            }
            event.optJSONObject("c")?.let { changed ->
                changed.keys().forEach { entity ->
                    // Only a new state counts; a change of attributes alone has no "s".
                    val plus = changed.optJSONObject(entity)?.optJSONObject("+")
                    if (plus != null && plus.has("s")) changes += Triple(entity, plus.optString("s"), false)
                }
            }
            if (changes.isEmpty()) return@subscribe
            runOnUiThread {
                val d = director ?: return@runOnUiThread
                val now = System.currentTimeMillis()
                for ((entity, state, first) in changes) {
                    val camera = cameraOf[entity] ?: continue
                    if (entity.startsWith("event.")) {
                        // An event entity's state is when it last fired: only a new one is a moment of motion.
                        if (!first && CameraFinder.isMotion(entity, state)) d.pulse(camera, now)
                        continue
                    }
                    val on = activeSensors.getOrPut(camera) { mutableSetOf() }
                    if (CameraFinder.isMotion(entity, state)) on += entity else on -= entity
                    d.motion(camera, on.isNotEmpty(), now)
                }
                tick()
            }
        }
    }

    /** What's on screen now, and the live picture to match. */
    private fun tick() {
        val d = director ?: return
        val now = d.screen(System.currentTimeMillis())
        if (now != screen) screen = now
        val camera = now.spotlight?.let { id -> cameras.firstOrNull { it.id == id } }
        live?.show(camera?.live)
    }

    /**
     * Still pictures, every [STILL_MS] (twice that with more than [MANY]): the grid's, from each camera's
     * lightest stream; behind the spotlight, its own camera's best stream, until the live picture moves.
     */
    private suspend fun refreshStills() {
        val ha = HomeAssistant.from(this) ?: return
        while (true) {
            val now = screen
            val wanted: List<Pair<String, String>> = when {
                now == null -> emptyList()
                now.spotlight != null -> {
                    val camera = cameras.firstOrNull { it.id == now.spotlight }
                    if (camera == null || liveState == CameraLive.State.PLAYING) emptyList() else listOf(camera.id to camera.live)
                }
                else -> now.grid.mapNotNull { id -> cameras.firstOrNull { it.id == id }?.let { it.id to it.still } }
            }
            coroutineScope {
                wanted.map { (id, entity) ->
                    async(Dispatchers.IO) {
                        runCatching {
                            val bytes = ha.bytes("/api/camera_proxy/$entity")
                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                        }.getOrNull()?.let { id to it }
                    }
                }.awaitAll().filterNotNull().forEach { (id, picture) -> stills[id] = picture }
            }
            delay(if (wanted.size > MANY) STILL_MS * 2 else STILL_MS)
        }
    }

    /** On a TV: Menu chooses the view; left and right change camera in the spotlight. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val d = director
        if (d != null && event.action == KeyEvent.ACTION_DOWN && !choosingView) {
            val spotlight = screen?.spotlight != null
            when (event.keyCode) {
                KeyEvent.KEYCODE_MENU -> { choosingView = true; return true }
                KeyEvent.KEYCODE_DPAD_RIGHT -> if (spotlight) { d.next(System.currentTimeMillis()); tick(); return true }
                KeyEvent.KEYCODE_DPAD_LEFT -> if (spotlight) { d.previous(System.currentTimeMillis()); tick(); return true }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun switchView(to: CameraView) {
        val d = director ?: return
        d.switchTo(to, System.currentTimeMillis())
        view = to
        tick()
    }

    @Composable
    private fun Screen() {
        BackHandler {
            val d = director
            if (d != null && d.back(System.currentTimeMillis())) tick() else finish()
        }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            val now = screen
            val message = problem
            when {
                message != null -> Problem(message)
                now == null -> Text("Looking for cameras…", color = Color.White, modifier = Modifier.align(Alignment.Center))
                else -> {
                    // The live picture is always there, under the grid, so its web view is made once.
                    live?.let { player ->
                        key(player) { AndroidView(factory = { player.view }, modifier = Modifier.fillMaxSize()) }
                    }
                    if (now.spotlight != null) Spotlight(now.spotlight, now.moving) else Grid(now)
                }
            }
            if (!tv && message == null && now != null) Views(Modifier.align(Alignment.TopStart))
        }
        if (choosingView) ViewPicker()
    }

    /** Why camera mode can't show anything, and where to fix it. */
    @Composable
    private fun Problem(message: String) {
        Column(Modifier.fillMaxSize().padding(48.dp), verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Cameras", style = MaterialTheme.typography.headlineSmall, color = Color.White)
            Text(message, style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.8f), textAlign = TextAlign.Center)
            val focus = remember { FocusRequester() }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(modifier = Modifier.focusRequester(focus), onClick = {
                    startActivity(Intent(this@CameraActivity, SettingsActivity::class.java).setAction(SettingsActivity.ACTION_CAMERAS))
                }) { Text("Settings") }
                OutlinedButton(onClick = { finish() }) { Text("Back") }
            }
            LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
        }
    }

    /** The grid of still pictures: up to 3 across, filling the screen inside a TV's safe area. */
    @Composable
    private fun Grid(now: CameraDirector.Screen) {
        val columns = when (now.grid.size) {
            1 -> 1
            in 2..4 -> 2
            else -> 3
        }
        val first = remember { FocusRequester() }
        LazyVerticalGrid(
            GridCells.Fixed(columns),
            contentPadding = if (tv) PaddingValues(horizontal = 48.dp, vertical = 27.dp) else PaddingValues(top = 64.dp, start = 8.dp, end = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize().safeDrawingPadding(),
        ) {
            items(now.grid, key = { it }) { id ->
                val camera = cameras.firstOrNull { it.id == id } ?: return@items
                Tile(camera, id in now.moving, id in selected, if (id == now.grid.first()) Modifier.focusRequester(first) else Modifier)
            }
        }
        LaunchedEffect(now.grid.firstOrNull()) {
            repeat(10) {
                if (runCatching { first.requestFocus() }.isSuccess) return@LaunchedEffect
                delay(100)
            }
        }
    }

    /**
     * One camera's picture in the grid: its name, a colored edge while it sees motion, a tick when it's
     * selected, and an outline when the remote is on it. OK or a tap: the spotlight; OK held or a long press:
     * select it.
     */
    @Composable
    private fun Tile(camera: FoundCamera, moving: Boolean, isSelected: Boolean, modifier: Modifier) {
        val interaction = remember { MutableInteractionSource() }
        val focused by interaction.collectIsFocusedAsState()
        var held by remember { mutableStateOf(false) }
        val shape = RoundedCornerShape(12.dp)
        val edge = when {
            focused -> Color.White
            moving -> Color(0xFFFFB300)
            else -> Color.Transparent
        }
        Box(
            modifier
                .aspectRatio(16f / 9f)
                .clip(shape)
                .background(Color(0xFF1C1D24))
                .border(if (focused || moving) 3.dp else 0.dp, edge, shape)
                // OK held: select, as a long press does (the remote's OK only ever clicks on its own).
                .onPreviewKeyEvent { e ->
                    val ok = e.key.nativeKeyCode in setOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER)
                    if (!ok) return@onPreviewKeyEvent false
                    when {
                        e.type == KeyEventType.KeyDown && e.nativeKeyEvent.repeatCount > 0 -> {
                            if (!held) {
                                held = true
                                toggleSelected(camera.id)
                            }
                            true
                        }
                        e.type == KeyEventType.KeyUp && held -> {
                            held = false
                            true
                        }
                        else -> false
                    }
                }
                .combinedClickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = { director?.choose(camera.id); tick() },
                    onLongClick = { toggleSelected(camera.id) },
                ),
        ) {
            val picture = stills[camera.id]
            if (picture != null) {
                Image(picture, camera.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Text("Loading…", color = Color.White.copy(alpha = 0.6f), modifier = Modifier.align(Alignment.Center))
            }
            Text(
                camera.name,
                color = Color.White,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xAA000000))))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
            if (isSelected) Icon(Icons.Rounded.CheckCircle, "Selected", tint = Color.White,
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(28.dp))
        }
    }

    private fun toggleSelected(camera: String) {
        val d = director ?: return
        d.toggleSelected(camera)
        selected = d.selected
        Cameras.setSelected(this, d.selected)
        tick()
    }

    /**
     * One camera live, full screen: its still picture shows until the live picture moves (and stays, if live
     * pictures don't work here). Its name, the view, and on a touch screen arrows to the camera before and after.
     */
    @Composable
    private fun Spotlight(id: String, moving: Set<String>) {
        val camera = cameras.firstOrNull { it.id == id } ?: return
        Box(Modifier.fillMaxSize()) {
            if (liveState != CameraLive.State.PLAYING) {
                stills[id]?.let { Image(it, camera.name, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) }
            }
            Column(Modifier.align(Alignment.BottomStart).padding(if (tv) 48.dp else 16.dp).padding(bottom = if (tv) 0.dp else 16.dp)) {
                Text(camera.name + if (id in moving) " · motion" else "", color = Color.White, style = MaterialTheme.typography.titleLarge)
                Text(
                    view.label + when (liveState) {
                        CameraLive.State.CONNECTING -> " · connecting…"
                        CameraLive.State.PLAYING -> ""
                        CameraLive.State.STILL -> " · still pictures (no live picture from Home Assistant)"
                    },
                    color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (!tv && cameras.size > 1) {
                IconButton(onClick = { director?.previous(System.currentTimeMillis()); tick() }, modifier = Modifier.align(Alignment.CenterStart)) {
                    Icon(Icons.Rounded.ChevronLeft, "Camera before", tint = Color.White, modifier = Modifier.size(40.dp))
                }
                IconButton(onClick = { director?.next(System.currentTimeMillis()); tick() }, modifier = Modifier.align(Alignment.CenterEnd)) {
                    Icon(Icons.Rounded.ChevronRight, "Next camera", tint = Color.White, modifier = Modifier.size(40.dp))
                }
            }
        }
    }

    /** On a touch screen: back, and the five views along the top. */
    @Composable
    private fun Views(modifier: Modifier) {
        Row(
            modifier.fillMaxWidth().safeDrawingPadding().background(Color(0x88000000)).horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { finish() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Close camera mode", tint = Color.White) }
            CameraView.entries.forEach { option ->
                FilterChip(selected = view == option, onClick = { switchView(option) }, label = { Text(option.label) })
            }
        }
    }

    /** Menu on the remote: the five views to choose from. */
    @Composable
    private fun ViewPicker() {
        AlertDialog(
            onDismissRequest = { choosingView = false },
            title = { Text("Show") },
            text = {
                Column {
                    CameraView.entries.forEach { option ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .clickable { switchView(option); choosingView = false }.padding(vertical = 4.dp),
                        ) {
                            RadioButton(selected = view == option, onClick = null, modifier = Modifier.padding(horizontal = 8.dp))
                            Column(Modifier.padding(vertical = 6.dp)) {
                                Text(option.label, style = MaterialTheme.typography.bodyLarge)
                                Text(option.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { choosingView = false }) { Text("Close") } },
        )
    }
}
