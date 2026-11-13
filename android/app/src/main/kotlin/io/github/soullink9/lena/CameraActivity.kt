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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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

        /** The waits before connecting again after the connection to Home Assistant ends, longer each time. */
        private val RECONNECT_MS = listOf(2_000L, 5_000L, 10_000L, 30_000L)

        /** More cameras than fit a screen (3 × 3): their pictures refresh half as often. */
        private const val MANY = 9
    }

    private val tv by lazy { Tv.isTv(this) }

    private var socket: HaSocket? = null

    /** The live picture's player; state, so a new one (after a reconnect, or a crashed page) goes on screen. */
    private var live by mutableStateOf<CameraLive?>(null)
    private var director: CameraDirector? = null
    private var running: Job? = null

    /** The included cameras, in their order. */
    private var cameras by mutableStateOf<List<FoundCamera>>(emptyList())
    private var screen by mutableStateOf<CameraDirector.Screen?>(null)
    private var problem by mutableStateOf<String?>(null)

    /** While the connection is down with cameras on screen: what's happening, in a line over them. */
    private var reconnecting by mutableStateOf<String?>(null)
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
        reconnecting = null
        liveState = CameraLive.State.CONNECTING
        stills.clear()
        activeSensors.clear()
        super.onStop()
    }

    /**
     * Connects, finds the included cameras, watches their motion and keeps the screen up to date. When the
     * connection to Home Assistant ends (it restarted, the Wi-Fi dropped), it connects again by itself,
     * waiting a little longer each time ([RECONNECT_MS]) or until the network comes back, and keeps the view,
     * the selection and the last pictures on screen meanwhile. A refused token, camera mode turned off or no
     * cameras included stop it: those need Settings, not another try.
     */
    private suspend fun runCameras() {
        if (!Prefs.cameras(this).on) {
            problem = "Camera mode is off on ${Tv.thisDevice(this)}. Turn it on in Settings → Cameras."
            return
        }
        if (!Cameras.available(this)) {
            problem = "Camera mode needs Home Assistant. Connect it in Settings → Home Assistant."
            return
        }
        var tries = 0
        while (true) {
            val why = try {
                connectAndRun { tries = 0 }
            } catch (e: CancellationException) {
                throw e
            } catch (e: HaAuthException) {
                problem = e.explained("Home Assistant didn't accept Lena's token. Connect it again in Settings → Home Assistant.")
                return
            } catch (e: NoCameras) {
                problem = e.message
                return
            } catch (e: Exception) {
                Logs.w(TAG, "Camera mode couldn't connect", e)
                "Couldn't reach Home Assistant's cameras (${e.message ?: e.javaClass.simpleName})."
            }
            closeConnection()
            val wait = RECONNECT_MS[minOf(tries, RECONNECT_MS.lastIndex)]
            tries++
            // With cameras on screen already, they stay, with a line saying what's happening; before then,
            // the reason fills the screen.
            val waiting = "$why Trying again in ${wait / 1000} seconds…"
            if (director == null) problem = waiting else reconnecting = waiting
            val seen = Connections.networkReturns.value
            withTimeoutOrNull(wait) { Connections.networkReturns.first { it != seen } }
            reconnecting = if (director == null) null else "Connecting to Home Assistant again…"
            if (director == null) problem = null
        }
    }

    /** Camera mode has nothing to show until something changes in Settings. */
    private class NoCameras(message: String) : Exception(message)

    /**
     * One connection to Home Assistant, from opening it to its end: returns why it ended. [onConnected] is
     * called once the cameras are found, so a working connection starts the waits from the shortest again.
     * The director (the view, the selection, a camera in the spotlight) is made the first time and kept
     * across connections while the same cameras are included.
     */
    private suspend fun connectAndRun(onConnected: () -> Unit): String {
        val settings = Prefs.cameras(this)
        // If camera mode closes while it connects, the connection made meanwhile is closed, not left open.
        var opened: HaSocket? = null
        val sock = try {
            withContext(Dispatchers.IO) { HaSocket.open(this@CameraActivity).also { opened = it } }
        } catch (e: CancellationException) {
            opened?.close()
            throw e
        }
        socket = sock
        val ended = CompletableDeferred<Unit>()
        sock.onClosed = { ended.complete(Unit) }
        if (sock.closed) ended.complete(Unit)
        val found = Cameras.find(sock)
        val list = Cameras.included(this).mapNotNull { id -> found.firstOrNull { it.id == id } }
        if (list.isEmpty()) throw NoCameras("No cameras are included on ${Tv.thisDevice(this)} yet. Choose them in Settings → Cameras.")
        val kept = director?.takeIf { it.included == list.map { camera -> camera.id } }
        val d = kept ?: CameraDirector(list.map { it.id }, Cameras.selected(this), settings.view,
            settings.rotateSeconds * 1000L, settings.holdSeconds * 1000L)
        cameras = list
        director = d
        selected = d.selected
        view = d.view
        // Asked for one by name: it opens in the spotlight (once; Back goes to the grid).
        intent.getStringExtra(EXTRA_ASKED)?.let { asked -> Cameras.named(list, asked)?.let { d.choose(it.id) } }
        intent.removeExtra(EXTRA_ASKED)
        Logs.i(TAG, "Camera mode: ${list.size} camera(s), ${d.view.label}" + if (kept != null) " (connected again)" else "")
        onConnected()
        reconnecting = null

        // Motion starts again from Home Assistant's first report on the new connection.
        activeSensors.clear()
        watchMotion(sock, list, settings.motion)
        val base = Prefs.haServer(this) ?: throw NoCameras("Camera mode needs Home Assistant. Connect it in Settings → Home Assistant.")
        live = newPlayer(sock, base, settings.sound)
        coroutineScope {
            val loops = launch {
                launch {
                    while (true) {
                        tick()
                        delay(TICK_MS)
                    }
                }
                launch { refreshStills() }
            }
            ended.await()
            loops.cancel()
        }
        return "The connection to Home Assistant ended."
    }

    /**
     * The live picture's player on [sock]. If Android ends its web view's page (it crashed, or memory ran
     * short), a new player takes its place and the camera in the spotlight plays again.
     */
    private fun newPlayer(sock: HaSocket, base: String, sound: Boolean): CameraLive =
        CameraLive(this, sock, base, sound, lifecycleScope, onState = { liveState = it }, onGone = {
            live?.destroy()
            live = if (sock.closed) null else newPlayer(sock, base, sound)
            liveState = CameraLive.State.CONNECTING
            tick()
        })

    /** Closes the player and the connection, keeping what's on screen for when it's back. */
    private fun closeConnection() {
        live?.destroy()
        live = null
        socket?.close()
        socket = null
        liveState = CameraLive.State.CONNECTING
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

    /**
     * The monitor: its bar across the top, and under it the grid or the spotlight, with the live picture's
     * web view always there underneath (so it's made once). The clock ticks every second for the OSD.
     */
    @Composable
    private fun Screen() {
        BackHandler {
            val d = director
            if (d != null && d.back(System.currentTimeMillis())) tick() else finish()
        }
        var clock by remember { mutableLongStateOf(System.currentTimeMillis()) }
        LaunchedEffect(Unit) {
            while (true) {
                clock = System.currentTimeMillis()
                delay(1_000 - clock % 1_000)
            }
        }
        // A TV crops the picture's edges: the monitor stays inside its safe area, with black around it.
        val safe = if (tv) Modifier.padding(horizontal = 48.dp, vertical = 27.dp) else Modifier.safeDrawingPadding()
        Box(Modifier.fillMaxSize().background(CameraLook.screen)) {
            val now = screen
            val message = problem
            when {
                message != null -> Problem(message)
                now == null -> Text(
                    "SEARCHING FOR CAMERAS…", style = CameraLook.text(15.sp, CameraLook.osdDim),
                    modifier = Modifier.align(Alignment.Center),
                )
                else -> Column(Modifier.fillMaxSize().then(safe)) {
                    MonitorBar(now, clock)
                    Box(Modifier.fillMaxWidth().weight(1f).background(CameraLook.divider)) {
                        live?.let { player ->
                            key(player) { AndroidView(factory = { player.view }, modifier = Modifier.fillMaxSize()) }
                        }
                        if (now.spotlight != null) Spotlight(now.spotlight, now.moving, clock) else Grid(now, clock)
                    }
                }
            }
            reconnecting?.let { line ->
                if (message == null && now != null) Row(
                    Modifier.align(Alignment.BottomCenter).then(safe).padding(16.dp)
                        .background(Color.Black.copy(alpha = 0.75f)).padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CameraLook.LiveDot("SIGNAL LOST", CameraLook.motion)
                    Spacer(Modifier.width(12.dp))
                    Text(line, style = CameraLook.text(13.sp, CameraLook.osd))
                }
            }
        }
        if (choosingView) ViewPicker()
    }

    /**
     * The monitor's bar: what's recording (a blinking REC), the view and how many cameras, and the date and
     * time. On a touch screen it also has back and the five views; on a TV, a reminder that Menu chooses
     * the view.
     */
    @Composable
    private fun MonitorBar(now: CameraDirector.Screen, clock: Long) {
        Column(Modifier.fillMaxWidth().background(Color(0xFF0A0C0D))) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = if (tv) 16.dp else 4.dp, vertical = if (tv) 10.dp else 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!tv) IconButton(onClick = { finish() }) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Close camera mode", tint = CameraLook.osd)
                }
                CameraLook.LiveDot("REC")
                Spacer(Modifier.width(16.dp))
                Text("CAMERAS", style = CameraLook.text(15.sp, bold = true))
                Spacer(Modifier.width(16.dp))
                Text(
                    "${view.label.uppercase()} · ${now.grid.size} CH",
                    style = CameraLook.text(13.sp, CameraLook.osdDim), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (tv) {
                    Text("MENU: VIEW", style = CameraLook.text(12.sp, CameraLook.osdDim))
                    Spacer(Modifier.width(20.dp))
                }
                Text(CameraLook.date(clock) + "  " + CameraLook.time(this@CameraActivity, clock), style = CameraLook.text(15.sp))
                if (!tv) Spacer(Modifier.width(8.dp))
            }
            if (!tv) Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CameraView.entries.forEach { option -> ViewTab(option) }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(CameraLook.divider))
        }
    }

    /** One of the views along the top on a touch screen, as a recorder's mode button: outlined when it's on. */
    @Composable
    private fun ViewTab(option: CameraView) {
        val on = view == option
        Text(
            option.label.uppercase(),
            style = CameraLook.text(12.sp, if (on) CameraLook.osd else CameraLook.osdDim, bold = on),
            modifier = Modifier
                .border(1.dp, if (on) CameraLook.osd else CameraLook.divider)
                .clickable { switchView(option) }
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }

    /** Why camera mode can't show anything, and where to fix it: the monitor's NO SIGNAL. */
    @Composable
    private fun Problem(message: String) {
        Column(Modifier.fillMaxSize().monitorGlass().padding(48.dp), verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text("NO SIGNAL", style = CameraLook.text(28.sp, CameraLook.osdDim, bold = true))
            Text(message, style = CameraLook.text(15.sp), textAlign = TextAlign.Center)
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

    /** The grid of still pictures: up to 3 across, edge to edge with thin dividers, as on a recorder's monitor. */
    @Composable
    private fun Grid(now: CameraDirector.Screen, clock: Long) {
        val columns = when (now.grid.size) {
            1 -> 1
            in 2..4 -> 2
            else -> 3
        }
        val first = remember { FocusRequester() }
        LazyVerticalGrid(
            GridCells.Fixed(columns),
            contentPadding = PaddingValues(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.fillMaxSize().background(CameraLook.divider),
        ) {
            items(now.grid, key = { it }) { id ->
                val camera = cameras.firstOrNull { it.id == id } ?: return@items
                Tile(camera, id in now.moving, id in selected, clock, if (id == now.grid.first()) Modifier.focusRequester(first) else Modifier)
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
     * One camera's picture in the grid, with a recorder's OSD: its channel and name, the time, MOTION while
     * it sees motion (and an amber edge), and SELECTED when it's selected. A white edge when the remote is on
     * it. OK or a tap: the spotlight; OK held or a long press: select it.
     */
    @Composable
    private fun Tile(camera: FoundCamera, moving: Boolean, isSelected: Boolean, clock: Long, modifier: Modifier) {
        val interaction = remember { MutableInteractionSource() }
        val focused by interaction.collectIsFocusedAsState()
        var held by remember { mutableStateOf(false) }
        val edge = when {
            focused -> CameraLook.osd
            moving -> CameraLook.motion
            else -> Color.Transparent
        }
        Box(
            modifier
                .aspectRatio(16f / 9f)
                .background(CameraLook.emptyPicture)
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
            Box(Modifier.fillMaxSize().monitorGlass()) {
                if (picture != null) {
                    Image(picture, camera.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Text("CONNECTING…", style = CameraLook.text(12.sp, CameraLook.osdDim), modifier = Modifier.align(Alignment.Center))
                }
            }
            val small = if (cameras.size > MANY) 11.sp else 13.sp
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${channelOf(camera)} ${camera.name.uppercase()}", style = CameraLook.text(small),
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                Text(CameraLook.time(this@CameraActivity, clock), style = CameraLook.text(small, CameraLook.osdDim))
            }
            Row(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (moving) CameraLook.Tag("MOTION", CameraLook.motion)
                Spacer(Modifier.weight(1f))
                if (isSelected) CameraLook.Tag("SELECTED", CameraLook.osd)
            }
            if (focused || moving) Box(Modifier.fillMaxSize().border(if (focused) 3.dp else 2.dp, edge))
        }
    }

    /** A camera's channel: its place among the included cameras (CH01 for the first), as a recorder numbers them. */
    private fun channelOf(camera: FoundCamera): String = CameraLook.channel(cameras.indexOf(camera).coerceAtLeast(0))

    private fun toggleSelected(camera: String) {
        val d = director ?: return
        d.toggleSelected(camera)
        selected = d.selected
        Cameras.setSelected(this, d.selected)
        tick()
    }

    /**
     * One camera live, filling the monitor: its still picture shows until the live picture moves (and stays,
     * if live pictures don't work here). The OSD over it: channel and name, LIVE (or what it's waiting for),
     * the date and time, the view, MOTION; on a touch screen, arrows to the camera before and after.
     */
    @Composable
    private fun Spotlight(id: String, moving: Set<String>, clock: Long) {
        val camera = cameras.firstOrNull { it.id == id } ?: return
        Box(Modifier.fillMaxSize()) {
            if (liveState != CameraLive.State.PLAYING) {
                Box(Modifier.fillMaxSize().background(CameraLook.screen))
                stills[id]?.let { Image(it, camera.name, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) }
            }
            // The monitor's glass over the live picture too, lighter, so the picture stays clear.
            Box(Modifier.fillMaxSize().monitorGlass(lines = 0.06f, corners = 0.35f))
            Column(Modifier.align(Alignment.TopStart).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${channelOf(camera)} ${camera.name.uppercase()}", style = CameraLook.text(20.sp, bold = true))
                when (liveState) {
                    CameraLive.State.PLAYING -> CameraLook.LiveDot("LIVE")
                    CameraLive.State.CONNECTING -> CameraLook.Tag("CONNECTING…", CameraLook.motion, 13.sp)
                    CameraLive.State.STILL -> CameraLook.Tag("STILL PICTURES · NO LIVE PICTURE FROM HOME ASSISTANT", CameraLook.osdDim, 13.sp)
                }
            }
            Text(
                CameraLook.date(clock) + "  " + CameraLook.time(this@CameraActivity, clock), style = CameraLook.text(20.sp),
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
            )
            Row(Modifier.align(Alignment.BottomStart).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(view.label.uppercase(), style = CameraLook.text(14.sp, CameraLook.osdDim))
                if (id in moving) CameraLook.Tag("MOTION", CameraLook.motion, 13.sp)
            }
            if (!tv && cameras.size > 1) {
                IconButton(onClick = { director?.previous(System.currentTimeMillis()); tick() }, modifier = Modifier.align(Alignment.CenterStart)) {
                    Icon(Icons.Rounded.ChevronLeft, "Camera before", tint = CameraLook.osd, modifier = Modifier.size(40.dp))
                }
                IconButton(onClick = { director?.next(System.currentTimeMillis()); tick() }, modifier = Modifier.align(Alignment.CenterEnd)) {
                    Icon(Icons.Rounded.ChevronRight, "Next camera", tint = CameraLook.osd, modifier = Modifier.size(40.dp))
                }
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
