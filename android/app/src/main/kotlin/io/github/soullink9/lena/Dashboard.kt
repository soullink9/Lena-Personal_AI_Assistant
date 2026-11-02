package io.github.soullink9.lena

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.text.format.DateFormat
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.Date

/**
 * Dashboard mode: your Home Assistant dashboards, always on, inside a Lena frame (the time, the
 * weather, how she feels, a talk button and your other apps), for a tablet on the wall. It can be
 * the tablet's home screen. After a few minutes without a touch it dims to Lena's night clock; a
 * touch, a motion sensor you pick, or her being worried about something brings it back.
 *
 * Home Assistant signs in with the token from Lena's settings, through the same "external auth"
 * the companion app uses, so there's no login page and no password stored in the web view.
 */
class DashboardActivity : ComponentActivity() {

    companion object {
        /** Go straight to the screen saver (coming back from your chats after a while). */
        const val EXTRA_IDLE = "idle"

        /**
         * Dashboard mode: the wall display is this device's home screen (Android settings → Default apps →
         * Home app → Lena). Only then are the dashboards and your chats full screen, and only then does the
         * screen saver come on by itself. Anywhere else, the wall display is a normal screen.
         */
        fun isHomeScreen(c: Context): Boolean {
            val home = c.packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)?.activityInfo
            return home?.packageName == c.packageName && home.name == DashboardActivity::class.java.name
        }

        /**
         * Paths on Home Assistant's address that aren't its frontend: other apps served through it
         * (add-on ingress, the Ingress Proxy integration and other integrations' views, all under
         * /api/), your own files in www (/local/), HACS's files and the frontend's static files.
         * They share Home Assistant's address, so they'd otherwise get Lena's script and bridge too.
         * Keep in step with NOT_FRONTEND in assets/js/home_assistant.js.
         */
        private val NOT_FRONTEND = listOf("/api/", "/local/", "/hacsfiles/", "/static/")

        /** Whether an address on Home Assistant's origin is its own frontend (a dashboard, settings, a panel). */
        fun isFrontend(url: String?): Boolean {
            val path = url?.let { Uri.parse(it).path } ?: return false
            return NOT_FRONTEND.none { path.startsWith(it) }
        }
    }

    /** One of the dashboard's views, as a tab in Lena's bar. */
    private data class View(val index: Int, val title: String, val path: String)

    /** What Home Assistant's header would have shown. */
    private data class Header(val views: List<View> = emptyList(), val current: Int = 0, val subview: Boolean = false)

    private val header = mutableStateOf(Header())
    private val canGoBack = mutableStateOf(false)

    private lateinit var web: WebView
    private lateinit var house: HomeAssistant
    private lateinit var base: Uri
    private var token = ""
    private var loaded = false

    private val tab = mutableIntStateOf(0)
    private val dimmed = mutableStateOf(false)
    private val lastTouch = mutableLongStateOf(System.currentTimeMillis())
    private val listening = mutableStateOf(Listening.OFF)
    private var wake: WakeListener? = null

    /** Whether this is the home screen (see [isHomeScreen]): full screen and the screen saver. */
    private var dashboardMode = false

    /** The Refresh generation the dashboards were loaded at; a newer one reloads them (see [Refresh]). */
    private var refreshSeen = Refresh.generation

    /**
     * Needs Home Assistant's address and token; without them it opens Settings. Full screen and kept on in
     * dashboard mode, and asks for the microphone if the wake word is on.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ha = HomeAssistant.from(this)
        val server = Prefs.haServer(this)
        val saved = Secrets.get(this, Secrets.HA_TOKEN)
        if (ha == null || server == null || saved == null) {
            // Nothing to show without Home Assistant: go and set it up.
            startActivity(Intent(this, SettingsActivity::class.java))
            finish()
            return
        }
        house = ha
        base = Uri.parse(server)
        token = saved

        enableEdgeToEdge()
        applyWindowMode()
        if (Prefs.wakeWord(this) && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        }
        web = createWebView()
        open(Prefs.dashboards(this).first(), reload = true)
        setContent { LenaTheme { Screen() } }
        if (dashboardMode && intent?.getBooleanExtra(EXTRA_IDLE, false) == true) dim()
    }

    /**
     * Dashboard mode (the home screen): full screen, kept on, and the screen saver allowed. Otherwise a
     * normal screen with Android's bars, which sleeps as usual. Checked again on every return, since the
     * home app can be changed in Android's settings meanwhile.
     */
    private fun applyWindowMode() {
        dashboardMode = isHomeScreen(this)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            if (dashboardMode) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                show(WindowInsetsCompat.Type.systemBars())
                if (dimmed.value) wakeUp()
            }
        }
    }

    // ---- Home Assistant in a web view ------------------------------------------------------

    /** Home Assistant's origin, which the sign-in bridge and page script are limited to. */
    private fun origin(): String = "https://${base.host}" + if (base.port == -1) "" else ":${base.port}"

    /**
     * The web view for Home Assistant: JavaScript, storage and media autoplay (camera streams) on; files,
     * content URIs, location, pop-ups and plain http off.
     */
    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(): WebView = WebView(this).apply {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        setBackgroundColor(android.graphics.Color.BLACK)
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false // camera streams
            allowFileAccess = false
            allowContentAccess = false
            setGeolocationEnabled(false)
            setSupportMultipleWindows(false)
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }
        webViewClient = object : WebViewClient() {
            /**
             * Your Home Assistant loads here; other sites open in your browser. Frames (iframe and camera
             * cards) load whatever they show.
             */
            override fun shouldOverrideUrlLoading(view: WebView, req: WebResourceRequest): Boolean {
                if (!req.isForMainFrame) return false // iframe and camera cards load their own content
                val u = req.url
                if (u.scheme == "https" && u.host.equals(base.host, ignoreCase = true) && u.port == base.port) return false
                if (u.scheme == "https" || u.scheme == "http") {
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, u).addCategory(Intent.CATEGORY_BROWSABLE))
                    } catch (_: ActivityNotFoundException) {
                    }
                }
                return true
            }

            /** The page is up: from now on dashboards switch inside it instead of reloading. */
            override fun onPageFinished(view: WebView, url: String?) {
                loaded = true
                canGoBack.value = view.canGoBack()
            }

            /** Every navigation, including Home Assistant's in-page ones: whether back has somewhere to go. */
            override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                canGoBack.value = view.canGoBack()
            }

            /** A dashboard that didn't load goes in the log, by path only. */
            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: android.webkit.WebResourceError) {
                if (request.isForMainFrame) Logs.w("Dashboard", "Page didn't load: ${error.description} (${request.url.path})")
            }

            /** The same for an HTTP error. */
            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: android.webkit.WebResourceResponse) {
                if (request.isForMainFrame) Logs.w("Dashboard", "Page answered HTTP ${response.statusCode} (${request.url.path})")
            }
        }
        webChromeClient = object : android.webkit.WebChromeClient() {
            /**
             * Home Assistant's own script errors go in Lena's log, which helps when a frontend update
             * breaks the header hiding. Errors from apps served through Home Assistant (their scripts
             * load from under /api/) are theirs, not Lena's business, and stay out.
             */
            override fun onConsoleMessage(m: android.webkit.ConsoleMessage): Boolean {
                val fromFrontend = m.sourceId().isNullOrBlank() || isFrontend(m.sourceId())
                if (fromFrontend && m.messageLevel() == android.webkit.ConsoleMessage.MessageLevel.ERROR) {
                    Logs.w("Dashboard", "Home Assistant page: ${m.message()}")
                }
                return true
            }
        }
        bridge(this)
    }

    /**
     * Home Assistant's frontend asks `window.externalApp` for its token when the page is opened
     * with `?external_auth=1`, the way the companion app signs in. Lena answers with the token from
     * its settings.
     *
     * Android adds the bridge to every frame on Home Assistant's address, and other apps can live
     * there too (add-on ingress and the Ingress Proxy integration serve apps from it, in frames or,
     * after a "new window" link, as the whole page). So a message is answered only when it comes
     * from the top frame, on that address, while the top frame shows Home Assistant's own frontend
     * (isFrontend). The page script makes the same checks before it adds anything.
     */
    private fun bridge(view: WebView) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) ||
            !WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
        ) return
        val origins = setOf(origin())
        WebViewCompat.addWebMessageListener(view, "lenaBridge", origins) { v, message, source, isMainFrame, _ ->
            if (!isMainFrame || source.toString().trimEnd('/') != origin()) return@addWebMessageListener
            if (!isFrontend(v.url)) {
                Logs.w("Dashboard", "Ignored a request from a page that isn't Home Assistant's frontend (${v.url?.let { Uri.parse(it).path }})")
                return@addWebMessageListener
            }
            val m = runCatching { JSONObject(message.data ?: "") }.getOrNull() ?: return@addWebMessageListener
            when (m.optString("kind")) {
                "auth" -> {
                    Logs.i("Dashboard", "Home Assistant asked to sign in; gave it the saved token")
                    val callback = JSONObject(m.optString("payload", "{}")).optString("callback").ifBlank { "externalAuthSetToken" }
                    if (!callback.matches(Regex("[A-Za-z_][A-Za-z0-9_]*"))) return@addWebMessageListener
                    val auth = JSONObject().put("access_token", token).put("expires_in", 1800)
                    v.evaluateJavascript("window.$callback(true, $auth);", null)
                }
                "header" -> {
                    val h = runCatching { JSONObject(m.optString("payload", "{}")) }.getOrNull() ?: return@addWebMessageListener
                    val views = h.optJSONArray("views")
                    header.value = Header(
                        views = List(views?.length() ?: 0) { i ->
                            val v = views!!.getJSONObject(i)
                            View(v.optInt("i"), v.optString("title").ifBlank { v.optString("path") }, v.optString("path"))
                        },
                        current = h.optInt("current"),
                        subview = h.optBoolean("subview"),
                    )
                }
                "revoke" -> {
                    val callback = JSONObject(m.optString("payload", "{}")).optString("callback").ifBlank { "externalAuthRevokeToken" }
                    if (callback.matches(Regex("[A-Za-z_][A-Za-z0-9_]*"))) v.evaluateJavascript("window.$callback(true);", null)
                }
                "bus" -> {
                    val msg = runCatching { JSONObject(m.optString("payload", "{}")) }.getOrNull() ?: return@addWebMessageListener
                    if (msg.optString("type") == "config/get") {
                        val result = JSONObject()
                            .put("hasSettingsScreen", false).put("canWriteTag", false).put("hasExoPlayer", false)
                            .put("canCommissionMatter", false).put("canImportThreadCredentials", false)
                            .put("hasAssist", false).put("hasBarCodeScanner", 0).put("canSetupImprov", false)
                        val reply = JSONObject().put("id", msg.opt("id")).put("type", "result").put("success", true).put("result", result)
                        v.evaluateJavascript("window.externalBus && window.externalBus($reply);", null)
                    }
                }
            }
        }
        // Lena's side of the page (assets/js/home_assistant.js): signing in, hiding Home Assistant's
        // header, reporting the views, and window.lena for the calls below. Android runs it in every
        // frame on this address; the script itself stops in frames and on pages that aren't the frontend.
        val script = assets.open("js/home_assistant.js").bufferedReader().use { it.readText() }
        WebViewCompat.addDocumentStartJavaScript(view, script, origins)
    }

    /**
     * The Chat | Home switch's Chat: your chats, in the same view as this screen. In dashboard mode they're
     * a visit, and come back here after a while alone (Settings → Wall display → When no one's around).
     */
    private fun openChats() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /** Calls one of the page's `window.lena` functions (assets/js/home_assistant.js), if it's there yet. */
    private fun callPage(function: String, argument: String? = null) {
        val arg = argument?.let { JSONObject.quote(it) } ?: ""
        web.evaluateJavascript("window.lena && window.lena.$function($arg);", null)
    }

    /** The back gesture: up out of a subview the way Home Assistant does, otherwise back through what you opened. */
    private fun back() {
        if (header.value.subview) callPage("back") else if (web.canGoBack()) web.goBack()
    }

    /** Home Assistant's sidebar: every dashboard, settings and the rest. */
    private fun menu() = callPage("menu")

    /** One of the dashboard's views, the way its own tabs switch. */
    private fun showView(v: View) = callPage("showView", v.path)

    /** Shows a dashboard: a quick in-page switch once Home Assistant is running, a full load otherwise. */
    private fun open(d: Dashboard, reload: Boolean = false) {
        val path = "/" + d.path.trim().trimStart('/')
        if (!reload && loaded) {
            callPage("open", path)
        } else {
            loaded = false
            web.loadUrl(origin() + path + "?external_auth=1")
        }
    }

    // ---- Touches, dimming and waking -------------------------------------------------------

    /**
     * A touch counts as someone being there; on the screen saver, the touch that wakes it doesn't also
     * press whatever is underneath.
     */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            lastTouch.longValue = System.currentTimeMillis()
            if (dimmed.value) {
                wakeUp()
                return true // the touch that wakes it doesn't also press a button
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    /** Back to the dashboards at the tablet's normal brightness. */
    private fun wakeUp() {
        lastTouch.longValue = System.currentTimeMillis()
        dimmed.value = false
        brightness(-1f)
    }

    /** The screen saver, at low brightness: 40% for photos (they need light), 2% for the night clock. */
    private fun dim() {
        dimmed.value = true
        // Photos need some light; the night clock (and the slideshow's night) as little as possible.
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val slides = Prefs.slides(this)
        val photos = slides.on && slides.album.isNotBlank() && !(slides.clockAtNight && (hour >= 22 || hour < 6))
        brightness(if (photos) 0.4f else 0.02f)
    }

    /** Overrides the screen brightness for this window only; negative goes back to the system's. */
    private fun brightness(level: Float) {
        window.attributes = window.attributes.also {
            it.screenBrightness = if (level < 0) WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE else level
        }
    }

    /**
     * Opens her listening, over the dashboard. The wake word stops first: only one thing can hold the
     * microphone.
     */
    private fun talk() {
        wake?.stop()
        startActivity(AskActivity.intent(this, talk = true, from = AskActivity.From.DASHBOARD))
    }

    /**
     * Back on screen: the web view runs again, the wake word listens again, and time spent talking to her
     * counts as a touch.
     */
    override fun onResume() {
        super.onResume()
        if (!::web.isInitialized) return
        applyWindowMode()
        if (refreshSeen != Refresh.generation) {
            refreshSeen = Refresh.generation
            web.reload()
        }
        web.onResume()
        // Time spent talking to her counts as using it, so it doesn't dim the moment she's done.
        if (!dimmed.value) lastTouch.longValue = System.currentTimeMillis()
        if (Prefs.wakeWord(this)) {
            // Heard her name: she talks over whatever's showing (dimmed stays dimmed underneath) and comes back to it.
            val w = wake ?: WakeListener(this, onHeard = { talk() }, onSilenced = { listening.value = Listening.BLOCKED })
                .also { wake = it }
            listening.value = if (w.start()) Listening.ON else Listening.OFF
        }
    }

    /** Whatever was answered, carry on (with or without the wake word). */
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        onResume()
    }

    /** Off screen: stop listening and pause the page. */
    override fun onPause() {
        wake?.stop()
        if (listening.value == Listening.ON) listening.value = Listening.OFF
        if (::web.isInitialized) web.onPause()
        super.onPause()
    }

    /**
     * Home pressed while this is the home screen: back to the first dashboard. Back from your chats with
     * EXTRA_IDLE: straight to the screen saver.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Back from your chats after a while: straight to the screen saver.
        if (dashboardMode && intent.getBooleanExtra(EXTRA_IDLE, false) && ::web.isInitialized) {
            dim()
            return
        }
        // Pressing home while it's the home screen: back to the first dashboard.
        if (intent.hasCategory(Intent.CATEGORY_HOME) && ::web.isInitialized) {
            wakeUp()
            tab.intValue = 0
            open(Prefs.dashboards(this).first())
        }
    }

    /** Destroys the web view with the screen. */
    override fun onDestroy() {
        if (::web.isInitialized) web.destroy()
        super.onDestroy()
    }

    // ---- The screen -----------------------------------------------------------------------

    /**
     * The bar (or the worried banner), the page, the screen saver over it, and the apps list, plus the
     * loops that keep the clock, her feelings and the screen saver going.
     */
    @Composable
    private fun Screen() {
        val dashboards = remember { Prefs.dashboards(this) }
        var now by remember { mutableStateOf(HouseNow(null, null)) }
        var clock by remember { mutableStateOf(Date()) }
        var apps by remember { mutableStateOf(false) }
        val f = now.feelings
        val worried = f?.moment == "Worried"

        LaunchedEffect(Unit) {
            while (true) {
                clock = Date()
                delay(60_000L - System.currentTimeMillis() % 60_000L)
            }
        }
        // Her feelings and the weather every minute; she lights the screen up when she's worried.
        LaunchedEffect(Unit) {
            var wasWorried = false
            while (true) {
                runCatching { house.now() }.getOrNull()?.let { now = it }
                val isWorried = now.feelings?.moment == "Worried"
                if (isWorried && !wasWorried) wakeUp()
                wasWorried = isWorried
                delay(60_000)
            }
        }
        // The screen saver after a while without a touch (in dashboard mode only); while it shows, watch
        // the motion sensor.
        LaunchedEffect(Unit) {
            val seconds = Prefs.dimSeconds(this@DashboardActivity)
            val motion = Prefs.motionEntity(this@DashboardActivity).trim()
            while (true) {
                delay(3_000)
                // Talking to her (listening, thinking, speaking) counts as someone being there, so the
                // wait starts again once she's done rather than ending mid-answer.
                if (Busy.talking) lastTouch.longValue = System.currentTimeMillis()
                if (dashboardMode && !dimmed.value && seconds > 0 && System.currentTimeMillis() - lastTouch.longValue > seconds * 1000L &&
                    now.feelings?.moment != "Worried") dim()
                if (dimmed.value && motion.isNotEmpty()) {
                    val state = runCatching { house.state(motion) }.getOrNull()
                    if (state == "on" || state == "home" || state == "detected") wakeUp()
                }
            }
        }
        BackHandler(enabled = true) {
            when {
                apps -> apps = false
                header.value.subview || web.canGoBack() -> back()
                // As the home screen, back has nowhere to go; otherwise it leaves.
                !isHomeScreen(this@DashboardActivity) -> finish()
            }
        }

        Box(Modifier.fillMaxSize().background(Color.Black)) {
            Column(Modifier.fillMaxSize().navigationBarsPadding()) {
                // Lena's strip: or, when she's worried, what's wrong.
                if (worried) WorriedBar(f!!.momentWhy)
                else Strip(now, clock, dashboards, onTalk = ::talk, onApps = { apps = true })
                AndroidView(factory = { web }, modifier = Modifier.fillMaxWidth().weight(1f))
            }
            // Dimmed: Lena's night clock over everything, at the lowest brightness.
            AnimatedVisibility(visible = dimmed.value, enter = fadeIn(), exit = fadeOut()) {
                LenaDisplay(locked = false, listening = listening.value, onTalk = ::wakeUp)
            }
            if (apps) Apps(onClose = { apps = false })
        }
    }

    /**
     * The one bar across the top: Home Assistant's menu (back is the back gesture), the Chat | Home switch,
     * the time and weather, the dashboard's views, how she feels (tap to talk), and apps and settings.
     * (Reloading is Settings → Connections → Refresh all connections.)
     */
    @Composable
    private fun Strip(now: HouseNow, clock: Date, dashboards: List<Dashboard>, onTalk: () -> Unit, onApps: () -> Unit) {
        val h = header.value
        var pickDashboard by remember { mutableStateOf(false) }
        var more by remember { mutableStateOf(false) }
        // On a narrow screen (a foldable's outer screen, a phone upright) the time and her mood's words step
        // aside and Apps and Settings go into a ⋮ menu, so the views keep room.
        val narrow = isNarrowScreen()
        Surface(color = MaterialTheme.colorScheme.surface) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (narrow) 6.dp else 10.dp),
                modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(start = 8.dp, end = if (narrow) 8.dp else 16.dp, top = 8.dp, bottom = 8.dp),
            ) {
                IconButton(onClick = ::menu) { Icon(Icons.Rounded.Menu, "Home Assistant's menu") }
                ScreenSwitcher(MainScreen.HOME, { if (it == MainScreen.CHAT) openChats() })
                if (!narrow) Column(Modifier.padding(start = 4.dp, end = 6.dp)) {
                    Text(DateFormat.getTimeFormat(this@DashboardActivity).format(clock), style = MaterialTheme.typography.titleLarge)
                    Text(
                        listOfNotNull(
                            DateFormat.format("EEE, MMM d", clock).toString(),
                            now.weather?.let { w ->
                                listOfNotNull(w.temperature?.let { "%.0f%s".format(it, w.unit.ifBlank { "°" }) },
                                    w.condition.replace('-', ' ').replaceFirstChar { it.uppercase() }).joinToString(" ")
                            },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                    )
                }
                // The dashboard's views, where Home Assistant's tabs were.
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                ) {
                    if (h.views.size > 1) h.views.forEach { v ->
                        val on = v.index == h.current && !h.subview
                        FilterChip(selected = on, onClick = { showView(v) }, label = { Text(v.title, maxLines = 1) })
                    }
                }
                if (dashboards.size > 1) Box {
                    IconButton(onClick = { pickDashboard = true }) { Icon(Icons.Outlined.Dashboard, "Dashboards") }
                    DropdownMenu(expanded = pickDashboard, onDismissRequest = { pickDashboard = false }) {
                        dashboards.forEachIndexed { i, d ->
                            DropdownMenuItem(
                                text = { Text(d.name.ifBlank { d.path }) },
                                leadingIcon = { RadioButton(selected = tab.intValue == i, onClick = null) },
                                onClick = { pickDashboard = false; tab.intValue = i; open(d) },
                            )
                        }
                    }
                }
                // How she feels, and a tap to talk to her.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.clip(RoundedCornerShape(28.dp)).clickable(onClick = onTalk)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(start = 6.dp, end = 14.dp, top = 5.dp, bottom = 5.dp),
                ) {
                    LenaAvatar(32.dp)
                    if (!narrow) Column(Modifier.width(180.dp)) {
                        val f = now.feelings
                        Text(listOfNotNull("Lena", f?.mood, f?.moment).joinToString(" · "), style = MaterialTheme.typography.titleSmall,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val why = f?.let { it.momentWhy.takeIf { w -> it.moment != null && w.isNotBlank() } ?: it.moodWhy }
                        Text(
                            why?.ifBlank { null } ?: if (listening.value == Listening.ON) "Say “${Prefs.wakePhrase(this@DashboardActivity).label}” or tap" else "Tap to talk",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Icon(Icons.Rounded.Mic, "Talk to Lena", tint = MaterialTheme.colorScheme.primary)
                }
                val openSettings = { startActivity(Intent(this@DashboardActivity, SettingsActivity::class.java)) }
                if (narrow) Box {
                    IconButton(onClick = { more = true }) { Icon(Icons.Rounded.MoreVert, "More") }
                    DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                        DropdownMenuItem(text = { Text("Apps") }, leadingIcon = { Icon(Icons.Outlined.Apps, null) },
                            onClick = { more = false; onApps() })
                        DropdownMenuItem(text = { Text("Settings") }, leadingIcon = { Icon(Icons.Outlined.Settings, null) },
                            onClick = { more = false; openSettings() })
                    }
                } else {
                    FilledTonalIconButton(onClick = onApps) { Icon(Icons.Outlined.Apps, "Apps") }
                    FilledTonalIconButton(onClick = openSettings) { Icon(Icons.Outlined.Settings, "Settings") }
                }
            }
        }
    }

    /** Replaces the bar when she's worried: what's wrong, in red, with a button to talk to her. */
    @Composable
    private fun WorriedBar(reason: String) {
        Surface(color = MaterialTheme.colorScheme.errorContainer) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 14.dp),
            ) {
                Icon(Icons.Outlined.Warning, null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(32.dp))
                Column(Modifier.weight(1f)) {
                    Text("Lena's worried", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                    Text(reason, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onErrorContainer)
                }
                FilledTonalButton(onClick = ::talk) {
                    Icon(Icons.Rounded.Mic, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Talk to her")
                }
            }
        }
    }

    // ---- Apps, for when this is the home screen ----------------------------------------------

    /** An app in the apps list. */
    private data class App(val label: String, val packageName: String, val icon: ImageBitmap)

    /**
     * Every app with a launcher icon, for when the wall display is the tablet's home screen and there's no
     * other way to open them.
     */
    @Composable
    private fun Apps(onClose: () -> Unit) {
        var list by remember { mutableStateOf<List<App>?>(null) }
        LaunchedEffect(Unit) {
            list = withContext(Dispatchers.IO) {
                val pm = packageManager
                pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                    .filter { it.activityInfo.packageName != packageName }
                    .map { r ->
                        App(
                            r.loadLabel(pm).toString(),
                            r.activityInfo.packageName,
                            r.loadIcon(pm).toBitmap(96, 96, Bitmap.Config.ARGB_8888).asImageBitmap(),
                        )
                    }
                    .distinctBy { it.packageName }
                    .sortedBy { it.label.lowercase() }
            }
        }
        // Always light text on near-black, whatever the theme, so every app name is readable.
        Surface(color = Color(0xF2101014), contentColor = Color.White, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.statusBarsPadding().navigationBarsPadding()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 12.dp)) {
                    Text("Apps", style = MaterialTheme.typography.headlineSmall, color = Color.White, modifier = Modifier.weight(1f))
                    IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Close", tint = Color.White) }
                }
                val apps = list
                if (apps == null) Text("Loading…", Modifier.padding(24.dp), color = Color.White)
                else LazyVerticalGrid(GridCells.Adaptive(104.dp), Modifier.fillMaxSize().padding(12.dp)) {
                    items(apps, key = { it.packageName }) { a ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable {
                                onClose()
                                packageManager.getLaunchIntentForPackage(a.packageName)?.let { startActivity(it) }
                            }.padding(vertical = 14.dp),
                        ) {
                            Image(a.icon, null, Modifier.size(56.dp))
                            Spacer(Modifier.height(8.dp))
                            Text(a.label, style = MaterialTheme.typography.labelLarge, color = Color.White, maxLines = 2, textAlign = TextAlign.Center,
                                overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}
