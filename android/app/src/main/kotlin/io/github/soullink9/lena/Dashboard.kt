package io.github.soullink9.lena

import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.outlined.Videocam
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
import androidx.lifecycle.lifecycleScope
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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

    // Dark on a TV (Tv.darkOnTv).
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase)
        Tv.darkOnTv(this, newBase)
    }

    companion object {
        /** Go straight to the screen saver (another Lena screen was left alone for a while, see [WallDisplay]). */
        const val EXTRA_IDLE = "idle"

        /** The remote's volume buttons: always the TV's, never Lena's. */
        private val VOLUME_KEYS = setOf(
            android.view.KeyEvent.KEYCODE_VOLUME_UP, android.view.KeyEvent.KEYCODE_VOLUME_DOWN, android.view.KeyEvent.KEYCODE_VOLUME_MUTE,
        )

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
    /**
     * What Home Assistant's header would have shown, and whether Lena's Apps and Settings made it into
     * Home Assistant's menu (menuItems); until they have, her bar keeps its own buttons for them.
     */
    private data class Header(
        val views: List<View> = emptyList(),
        val current: Int = 0,
        val subview: Boolean = false,
        val menuItems: Boolean = false,
    )

    /** Whether the list of the device's other apps is showing (from Apps in the bar or Home Assistant's menu). */
    private val showApps = mutableStateOf(false)

    private val header = mutableStateOf(Header())
    private val canGoBack = mutableStateOf(false)

    private lateinit var web: WebView
    private lateinit var house: HomeAssistant
    private lateinit var base: Uri
    private var token = ""
    private var loaded = false

    private val tab = mutableIntStateOf(0)
    private val dimmed = mutableStateOf(false)

    /**
     * On a TV, the dashboard open full screen; null shows the TV home's rows instead ([TvHome]). Back from
     * a dashboard's first page, or the Home button, returns to the rows.
     */
    private val tvDashboard = mutableStateOf<Dashboard?>(null)
    private val listening = mutableStateOf(Listening.OFF)
    private var wake: WakeListener? = null

    /**
     * Whether this is the home screen (see [isHomeScreen]): full screen and the screen saver. On a TV it
     * always is: the wall display is all Lena is there, whichever app the Home button opens.
     */
    private var dashboardMode = false

    /** Whether this is an Android TV: no chat, and the remote instead of touch ([Tv]). */
    private val tv by lazy { Tv.isTv(this) }

    /** The TV remote: OK held (or its search button) to talk, Menu for Settings. */
    private val remote by lazy {
        Remote(onTalk = { wakeUp(); talk() }, onMenu = { startActivity(Intent(this, SettingsActivity::class.java)) })
    }

    /** [dashboardMode], for the screen to draw from (Apps shows only then). */
    private val wallDisplay = mutableStateOf(false)

    /** The Refresh generation the dashboards were loaded at; a newer one reloads them (see [Refresh]). */
    private var refreshSeen = Refresh.generation

    /** What Home Assistant still needs before there's anything to show, in words; null when it's all set. */
    private fun missing(): String? {
        val server = Prefs.haServer(this)
        val token = Secrets.get(this, Secrets.HA_TOKEN)
        return when {
            server == null && token == null -> "Add your Home Assistant's address and a long-lived token"
            server == null -> "Add your Home Assistant's address"
            token == null -> "Add a long-lived token from your Home Assistant profile"
            HomeAssistant.from(this) == null -> "Check your Home Assistant's address"
            else -> null
        }
    }

    /**
     * Needs Home Assistant's address and token. Without them it opens Settings, except where it's the home
     * screen (or a TV's wall display): there, leaving Settings opens the home screen again, which would open
     * Settings again, with no way out. So there it says what's missing, with Settings and your apps.
     * Full screen and kept on in dashboard mode, and asks for the microphone if the wake word is on.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ha = HomeAssistant.from(this)
        val server = Prefs.haServer(this)
        val saved = Secrets.get(this, Secrets.HA_TOKEN)
        if (ha == null || server == null || saved == null) {
            if (isHomeScreen(this) || tv) {
                enableEdgeToEdge()
                setContent { LenaTheme { NotSetUp(missing() ?: "Check your Home Assistant connection") } }
            } else {
                // Nothing to show without Home Assistant: go and set it up.
                startActivity(Intent(this, SettingsActivity::class.java))
                finish()
            }
            return
        }
        house = ha
        base = Uri.parse(server)
        token = saved

        enableEdgeToEdge()
        applyWindowMode()
        if (Prefs.wakeWord(this) && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            askMicrophone.launch(Manifest.permission.RECORD_AUDIO)
        }
        // The web view kept from the last dashboard screen, page and all (see DashboardWeb), or a new one.
        val (view, fresh) = DashboardWeb.take(this, "$server|$saved") { createWebView(it) }
        web = view
        attachClients(web)
        if (fresh) {
            bridge(web)
            open(Prefs.dashboards(this).first(), reload = true)
        } else {
            loaded = true
            canGoBack.value = web.canGoBack()
            callPage("report") // the bar needs the page's views again
            callPage("apps", if (dashboardMode) "on" else "off")
        }
        setContent { LenaTheme { Screen() } }
        if (dashboardMode && intent?.getBooleanExtra(EXTRA_IDLE, false) == true) dim()
        // On the wall all day: keep Open WebUI and Home Assistant ready for whenever someone talks to her.
        Connections.keepAlive(this)
        DisplaySync.watch(this, AskActivity.From.DASHBOARD)
    }

    /**
     * Dashboard mode (the home screen): full screen, kept on, and the screen saver allowed. Otherwise a
     * normal screen with Android's bars, which sleeps as usual. Checked again on every return, since the
     * home app can be changed in Android's settings meanwhile.
     */
    private fun applyWindowMode() {
        dashboardMode = isHomeScreen(this) || tv
        wallDisplay.value = dashboardMode
        // The page's Apps row in Home Assistant's menu follows the same rule (assets/js/home_assistant.js).
        if (::web.isInitialized) callPage("apps", if (dashboardMode) "on" else "off")
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
    private fun createWebView(context: android.content.Context): WebView = WebView(context).apply {
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
    }

    /**
     * The web view's clients, which belong to this screen: set again whenever a screen takes the kept
     * web view. Your Home Assistant loads here; other sites open in your browser.
     */
    private fun attachClients(view: WebView) = view.apply {
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
                callPage("apps", if (dashboardMode) "on" else "off")
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
        // Registered once per web view, which outlives its screen: the message goes to the screen showing it now.
        WebViewCompat.addWebMessageListener(view, "lenaBridge", origins) { v, message, source, isMainFrame, _ ->
            DashboardWeb.host?.onPageMessage(v, message.data, source, isMainFrame)
        }
        // Lena's side of the page (assets/js/home_assistant.js): signing in, hiding Home Assistant's
        // header, reporting the views, and window.lena for the calls below. Android runs it in every
        // frame on this address; the script itself stops in frames and on pages that aren't the frontend.
        val script = assets.open("js/home_assistant.js").bufferedReader().use { it.readText() }
        WebViewCompat.addDocumentStartJavaScript(view, script, origins)
    }

    /**
     * A message from the page's script (see [bridge]), for the screen showing the web view. Answered only
     * from the top frame, on Home Assistant's address, while it shows Home Assistant's own frontend.
     */
    fun onPageMessage(v: WebView, data: String?, source: Uri, isMainFrame: Boolean) {
        if (!isMainFrame || source.toString().trimEnd('/') != origin()) return
        if (!isFrontend(v.url)) {
            Logs.w("Dashboard", "Ignored a request from a page that isn't Home Assistant's frontend (${v.url?.let { Uri.parse(it).path }})")
            return
        }
        val m = runCatching { JSONObject(data ?: "") }.getOrNull() ?: return
        when (m.optString("kind")) {
            "auth" -> {
                Logs.i("Dashboard", "Home Assistant asked to sign in; gave it the saved token")
                val callback = JSONObject(m.optString("payload", "{}")).optString("callback").ifBlank { "externalAuthSetToken" }
                if (!callback.matches(Regex("[A-Za-z_][A-Za-z0-9_]*"))) return
                val auth = JSONObject().put("access_token", token).put("expires_in", 1800)
                v.evaluateJavascript("window.$callback(true, $auth);", null)
            }
            "header" -> {
                val h = runCatching { JSONObject(m.optString("payload", "{}")) }.getOrNull() ?: return
                val views = h.optJSONArray("views")
                header.value = Header(
                    views = List(views?.length() ?: 0) { i ->
                        val v = views!!.getJSONObject(i)
                        View(v.optInt("i"), v.optString("title").ifBlank { v.optString("path") }, v.optString("path"))
                    },
                    current = h.optInt("current"),
                    subview = h.optBoolean("subview"),
                    menuItems = h.optBoolean("menuItems"),
                )
            }
            // Lena's rows at the bottom of Home Assistant's menu.
            "apps" -> { showApps.value = true }
            "settings" -> { startActivity(Intent(this, SettingsActivity::class.java)) }
            "revoke" -> {
                val callback = JSONObject(m.optString("payload", "{}")).optString("callback").ifBlank { "externalAuthRevokeToken" }
                if (callback.matches(Regex("[A-Za-z_][A-Za-z0-9_]*"))) v.evaluateJavascript("window.$callback(true);", null)
            }
            "bus" -> {
                val msg = runCatching { JSONObject(m.optString("payload", "{}")) }.getOrNull() ?: return
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

    /**
     * The Chat | Home switch's Chat: your chats, in the same view as this screen. In dashboard mode they're
     * a visit, and come back here after a while alone (Settings → Wall display → When no one's around).
     */
    private fun openChats() {
        // Open WebUI's chats, or Lena's own chat when Assist answers.
        startActivity(AssistChatActivity.intent(this).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
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
    /**
     * On a TV, the remote is someone being there, like a touch. On the screen saver the button that wakes
     * it does nothing else (the volume buttons stay the TV's); otherwise the remote's own buttons are
     * handled by [remote]. Phones and tablets keep Android's usual keys.
     */
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (!tv || event.keyCode in VOLUME_KEYS) return super.dispatchKeyEvent(event)
        if (event.action == android.view.KeyEvent.ACTION_DOWN) {
            WallDisplay.touched()
            if (dimmed.value) {
                if (event.repeatCount == 0) wakeUp()
                return true
            }
        }
        return remote.dispatch(event) { super.dispatchKeyEvent(forWebPage(it)) }
    }

    /**
     * A Home Assistant dashboard is a web page, which a TV's web view doesn't move around with the arrows
     * (they scroll it). So on a TV, while the page has the remote, the arrows become Tab (down and right:
     * the next card or button) and Shift+Tab (up and left: the one before), and OK becomes Enter, which
     * presses it, as with a keyboard. Home Assistant outlines the one that's chosen. Shift+Tab from the
     * page's first card leaves the page for Lena's bar above it.
     */
    private fun forWebPage(e: android.view.KeyEvent): android.view.KeyEvent {
        if (tvDashboard.value == null || !::web.isInitialized || !web.hasFocus()) return e
        val (code, back) = when (e.keyCode) {
            android.view.KeyEvent.KEYCODE_DPAD_DOWN, android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> android.view.KeyEvent.KEYCODE_TAB to false
            android.view.KeyEvent.KEYCODE_DPAD_UP, android.view.KeyEvent.KEYCODE_DPAD_LEFT -> android.view.KeyEvent.KEYCODE_TAB to true
            android.view.KeyEvent.KEYCODE_DPAD_CENTER -> android.view.KeyEvent.KEYCODE_ENTER to false
            else -> return e
        }
        val meta = if (back) android.view.KeyEvent.META_SHIFT_ON or android.view.KeyEvent.META_SHIFT_LEFT_ON else 0
        return android.view.KeyEvent(e.downTime, e.eventTime, e.action, code, e.repeatCount, meta, e.deviceId, e.scanCode, e.flags, e.source)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            WallDisplay.touched()
            if (dimmed.value) {
                wakeUp()
                return true // the touch that wakes it doesn't also press a button
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    /** Back to the dashboards at the tablet's normal brightness. */
    private fun wakeUp() {
        WallDisplay.touched()
        dimmed.value = false
        brightness(-1f)
        // Someone's here and may talk to her: check the connections now rather than at the next round.
        lifecycleScope.launch { Connections.ensure(this@DashboardActivity) }
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
     * Back on screen: the web view runs again and the wake word listens again. The screensaver's wait
     * carries on from the other screens (one clock, see [WallDisplay]); time spent talking to her counts as
     * a touch, since the sheet marks the end of a conversation as one.
     */
    override fun onResume() {
        super.onResume()
        if (!::web.isInitialized) {
            // Back from Settings with Home Assistant now set up: start again, dashboards and all.
            if (!isFinishing && missing() == null) recreate()
            return
        }
        applyWindowMode()
        if (refreshSeen != Refresh.generation) {
            refreshSeen = Refresh.generation
            web.reload()
        }
        web.onResume()
        if (Prefs.wakeWord(this)) {
            // Heard her name: she talks over whatever's showing (dimmed stays dimmed underneath) and comes back to it.
            val w = wake ?: WakeListener(this, onHeard = { talk() }, onSilenced = { listening.value = Listening.BLOCKED })
                .also { wake = it }
            listening.value = if (w.start()) Listening.ON else Listening.OFF
        }
    }

    /** The microphone, for the wake word; whatever was answered, carry on (with or without it). */
    private val askMicrophone = registerForActivityResult(ActivityResultContracts.RequestPermission()) { onResume() }

    /** Off screen: stop listening and pause the page. */
    override fun onPause() {
        wake?.stop()
        if (listening.value == Listening.ON) listening.value = Listening.OFF
        if (::web.isInitialized) web.onPause()
        super.onPause()
    }

    /**
     * Home pressed while this is the home screen: back to the first dashboard. Another screen left alone,
     * with EXTRA_IDLE: straight to the screen saver.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Another Lena screen was left alone for a while: straight to the screen saver.
        if (dashboardMode && intent.getBooleanExtra(EXTRA_IDLE, false) && ::web.isInitialized) {
            dim()
            return
        }
        // Pressing home while it's the home screen: back to the first dashboard (on a TV, to the rows).
        if (intent.hasCategory(Intent.CATEGORY_HOME) && ::web.isInitialized) {
            wakeUp()
            tvDashboard.value = null
            tab.intValue = 0
            open(Prefs.dashboards(this).first())
        }
    }

    /** Sets the web view aside for the next dashboard screen (see [DashboardWeb]), page and all. */
    override fun onDestroy() {
        if (::web.isInitialized) DashboardWeb.putAside(this)
        super.onDestroy()
    }

    // ---- The screen -----------------------------------------------------------------------

    /**
     * The home screen before Home Assistant is set up: what's missing, Settings, and your apps, so the
     * device is never left without a way to open anything.
     */
    @Composable
    private fun NotSetUp(what: String) {
        var apps by remember { mutableStateOf(false) }
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(32.dp),
            ) {
                Text("Connect Home Assistant", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "The wall display shows your Home Assistant dashboards. $what in Settings → Home Assistant, or set it up from your phone: no typing here.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Signing in from a phone comes first: on a TV it's the only practical way ([PhoneSetupActivity]).
                    FilledTonalButton(onClick = { startActivity(Intent(this@DashboardActivity, PhoneSetupActivity::class.java)) }) { Text("Set up from your phone") }
                    androidx.compose.material3.OutlinedButton(onClick = { startActivity(Intent(this@DashboardActivity, SettingsActivity::class.java)) }) { Text("Settings") }
                    androidx.compose.material3.OutlinedButton(onClick = { apps = true }) { Text("Apps") }
                }
            }
            if (apps) AppsDrawer(onClose = { apps = false })
        }
    }

    /**
     * The bar (or the worried banner), the page, the screen saver over it, and the apps list, plus the
     * loops that keep the clock, her feelings and the screen saver going.
     */
    @Composable
    private fun Screen() {
        val dashboards = remember { Prefs.dashboards(this) }
        var now by remember { mutableStateOf(HouseNow(null, null)) }
        var clock by remember { mutableStateOf(Date()) }
        var apps by showApps
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
        // The screen saver after a while without a touch on any Lena screen (in dashboard mode only); while
        // it shows, watch the motion sensor.
        LaunchedEffect(Unit) {
            val motion = Prefs.motionEntity(this@DashboardActivity).trim()
            while (true) {
                delay(3_000)
                // Read each time, so a change in Settings counts without restarting the wall display.
                val seconds = Prefs.dimSeconds(this@DashboardActivity)
                // Talking to her (listening, thinking, speaking) and typing count as someone being there,
                // so the wait starts again once they're done rather than ending mid-answer.
                if (Busy.talking || WallDisplay.typing(this@DashboardActivity)) WallDisplay.touched()
                if (dashboardMode && !dimmed.value && seconds > 0 && WallDisplay.idleMillis() > seconds * 1000L &&
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
                // On a TV, a dashboard's first page goes back to the rows.
                tv && tvDashboard.value != null && !header.value.subview && !web.canGoBack() -> tvDashboard.value = null
                tv && tvDashboard.value == null -> Unit // the TV home: nowhere further back
                header.value.subview || web.canGoBack() -> back()
                // As the home screen, back has nowhere to go; otherwise it leaves (its page is kept, see
                // DashboardWeb, so coming back is quick).
                !isHomeScreen(this@DashboardActivity) -> finish()
            }
        }

        Box(Modifier.fillMaxSize().background(Color.Black)) {
            if (tv && tvDashboard.value == null) {
                // A TV's home: rows for the remote, rather than the tablet's bar over a dashboard.
                TvHome(
                    now = now, clock = clock, dashboards = dashboards,
                    onTalk = ::talk,
                    onDashboard = { d ->
                        open(d)
                        tvDashboard.value = d
                        // The remote starts on the page (the arrows then move between its cards, see
                        // forWebPage), once the web view is back on screen.
                        web.post { web.requestFocus() }
                    },
                    onAllApps = { apps = true },
                    onSettings = { startActivity(Intent(this@DashboardActivity, SettingsActivity::class.java)) },
                )
            } else Column(
                Modifier.fillMaxSize().navigationBarsPadding()
                    // A TV crops the picture's edges: keep the bar and the dashboard inside its safe area.
                    .then(if (tv) Modifier.padding(horizontal = 48.dp, vertical = 27.dp) else Modifier)
            ) {
                // Lena's strip: or, when she's worried, what's wrong.
                if (worried) WorriedBar(f!!.momentWhy)
                else Strip(now, clock, dashboards, onTalk = ::talk, onApps = { apps = true })
                AndroidView(factory = { web }, modifier = Modifier.fillMaxWidth().weight(1f))
            }
            // Dimmed: Lena's night clock over everything, at the lowest brightness.
            AnimatedVisibility(visible = dimmed.value, enter = fadeIn(), exit = fadeOut()) {
                LenaDisplay(locked = false, listening = listening.value, onTalk = ::wakeUp)
            }
            if (apps) AppsDrawer(onClose = { apps = false })
        }
    }

    /**
     * The one bar across the top: Home Assistant's menu (back is the back gesture), the Chat | Home switch,
     * the time and weather, the next timer or alarm, the dashboard's views, how she feels (tap to talk), and apps and settings.
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
                // A TV has no keyboard to chat with: the wall display is all there is ([Tv]).
                if (!tv) ScreenSwitcher(MainScreen.HOME, { if (it == MainScreen.CHAT) openChats() })
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
                // The next timer or alarm, counting down; a tap shows them all.
                NextCountdown(
                    modifier = Modifier.clip(RoundedCornerShape(20.dp)).clickable {
                        startActivity(Intent(this@DashboardActivity, SettingsActivity::class.java).setAction(SettingsActivity.ACTION_ALARMS))
                    }.background(MaterialTheme.colorScheme.secondaryContainer).padding(horizontal = 12.dp, vertical = 6.dp),
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
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
                // Camera mode, when it's on here (Settings → Cameras).
                if (Cameras.available(this@DashboardActivity)) {
                    IconButton(onClick = { Cameras.open(this@DashboardActivity) }) { Icon(Icons.Outlined.Videocam, "Cameras") }
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
                // Apps only on the wall display (dashboard mode), where there's no launcher to open them.
                val withApps = wallDisplay.value
                // Apps and Settings live at the bottom of Home Assistant's menu once the page script has put
                // them there; until then (or if a frontend update stops it), they stay here.
                if (!h.menuItems) {
                    if (narrow) Box {
                        IconButton(onClick = { more = true }) { Icon(Icons.Rounded.MoreVert, "More") }
                        DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                            if (withApps) {
                                DropdownMenuItem(text = { Text("Apps") }, leadingIcon = { Icon(Icons.Outlined.Apps, null) },
                                    onClick = { more = false; onApps() })
                            }
                            DropdownMenuItem(text = { Text("Settings") }, leadingIcon = { Icon(Icons.Outlined.Settings, null) },
                                onClick = { more = false; openSettings() })
                        }
                    } else {
                        if (withApps) FilledTonalIconButton(onClick = onApps) { Icon(Icons.Outlined.Apps, "Apps") }
                        FilledTonalIconButton(onClick = openSettings) { Icon(Icons.Outlined.Settings, "Settings") }
                    }
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
}
