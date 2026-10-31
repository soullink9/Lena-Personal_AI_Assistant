package io.github.soullink9.lena

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Refresh
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
         * Home Assistant's own header goes, so Lena's bar is the only one: the dashboard's views,
         * back and the sidebar menu move into it. Edit mode keeps the header (its save button lives
         * there). The page tells Lena its views as they change.
         */
        private const val HEADER_CSS =
            "div:not(.edit-mode)>.header{display:none!important}" +
                "div:not(.edit-mode)>hui-view-container{padding-top:var(--safe-area-inset-top,0px)!important}"

        private val HEADER_SCRIPT = """
            (function () {
              if (window.__lena) return; window.__lena = true;
              var post = function (kind, payload) { try { lenaBridge.postMessage(JSON.stringify({kind: kind, payload: payload})); } catch (e) {} };
              var sr = function (el) { return el && el.shadowRoot; };
              window.lenaMain = function () {
                var ha = document.querySelector('home-assistant');
                return sr(ha) && ha.shadowRoot.querySelector('home-assistant-main');
              };
              window.lenaRoot = function () {
                var main = window.lenaMain();
                var panel = sr(main) && main.shadowRoot.querySelector('ha-panel-lovelace');
                return sr(panel) && panel.shadowRoot.querySelector('hui-root');
              };
              var last = '';
              setInterval(function () {
                var state = {found: false};
                try {
                  var r = window.lenaRoot();
                  if (r && r.shadowRoot) {
                    if (!r.shadowRoot.getElementById('lena-header')) {
                      var st = document.createElement('style'); st.id = 'lena-header'; st.textContent = '$HEADER_CSS';
                      r.shadowRoot.appendChild(st);
                    }
                    var cfg = (r.lovelace && r.lovelace.config) || {};
                    var all = (cfg.views || []).map(function (v, i) {
                      return {i: i, title: v.title || '', path: String(v.path || i), sub: !!v.subview, hidden: v.visible === false};
                    });
                    var cur = typeof r._curView === 'number' ? r._curView : 0;
                    state = {found: true, current: cur, subview: !!(all[cur] && all[cur].sub), edit: !!(r.lovelace && r.lovelace.editMode),
                      views: all.filter(function (v) { return !v.sub && !v.hidden; })};
                  }
                } catch (e) {}
                var s = JSON.stringify(state);
                if (s !== last) { last = s; post('header', s); }
              }, 600);
            })();
        """.trimIndent()
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

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        if (Prefs.wakeWord(this) && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        }
        web = createWebView()
        open(Prefs.dashboards(this).first(), reload = true)
        setContent { LenaTheme { Screen() } }
        if (intent?.getBooleanExtra(EXTRA_IDLE, false) == true) dim()
    }

    // ---- Home Assistant in a web view ------------------------------------------------------

    private fun origin(): String = "https://${base.host}" + if (base.port == -1) "" else ":${base.port}"

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

            override fun onPageFinished(view: WebView, url: String?) {
                loaded = true
                canGoBack.value = view.canGoBack()
            }

            override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                canGoBack.value = view.canGoBack()
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: android.webkit.WebResourceError) {
                if (request.isForMainFrame) Logs.w("Dashboard", "Page didn't load: ${error.description} (${request.url.path})")
            }

            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: android.webkit.WebResourceResponse) {
                if (request.isForMainFrame) Logs.w("Dashboard", "Page answered HTTP ${response.statusCode} (${request.url.path})")
            }
        }
        webChromeClient = object : android.webkit.WebChromeClient() {
            override fun onConsoleMessage(m: android.webkit.ConsoleMessage): Boolean {
                if (m.messageLevel() == android.webkit.ConsoleMessage.MessageLevel.ERROR) {
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
     * its settings. The bridge exists only for your Home Assistant's address, in the top frame,
     * so iframe cards from other sites can't use it.
     */
    private fun bridge(view: WebView) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) ||
            !WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
        ) return
        val origins = setOf(origin())
        WebViewCompat.addWebMessageListener(view, "lenaBridge", origins) { v, message, source, isMainFrame, _ ->
            if (!isMainFrame || source.toString().trimEnd('/') != origin()) return@addWebMessageListener
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
        // The externalApp object the frontend looks for, and the sidebar folded away (Lena's tabs replace it).
        WebViewCompat.addDocumentStartJavaScript(
            view,
            """
            (function () {
              var send = function (kind, payload) { lenaBridge.postMessage(JSON.stringify({kind: kind, payload: payload})); };
              window.externalApp = {
                getExternalAuth: function (p) { send('auth', p); },
                revokeExternalAuth: function (p) { send('revoke', p); },
                externalBus: function (p) { send('bus', p); }
              };
              try { localStorage.setItem('dockedSidebar', '"always_hidden"'); } catch (e) {}
            })();
            """.trimIndent(),
            origins,
        )
        WebViewCompat.addDocumentStartJavaScript(view, HEADER_SCRIPT, origins)
    }

    /** Home Assistant's back: up from a subview, otherwise back through what you opened. */
    private fun back() {
        if (header.value.subview) web.evaluateJavascript("(function(){var r=window.lenaRoot&&lenaRoot();if(r&&r._goBack)r._goBack();else history.back();})();", null)
        else if (web.canGoBack()) web.goBack()
    }

    /** Home Assistant's sidebar: every dashboard, settings and the rest. */
    private fun menu() {
        web.evaluateJavascript(
            "(function(){var m=window.lenaMain&&lenaMain();if(m)m.dispatchEvent(new CustomEvent('hass-toggle-menu',{bubbles:true,composed:true}));})();",
            null,
        )
    }

    /** One of the dashboard's views, the way its own tabs switch. */
    private fun showView(v: View) {
        val path = JSONObject.quote(v.path)
        web.evaluateJavascript(
            "(function(){var r=window.lenaRoot&&lenaRoot();if(r&&r._navigateToView){r._navigateToView($path);return;}" +
                "var p=(r&&r.route&&r.route.prefix)||location.pathname.split('/').slice(0,2).join('/');" +
                "history.pushState(null,'',p+'/'+$path);window.dispatchEvent(new CustomEvent('location-changed',{detail:{replace:false}}));})();",
            null,
        )
    }

    /** Shows a dashboard: a quick in-page switch once Home Assistant is running, a full load otherwise. */
    private fun open(d: Dashboard, reload: Boolean = false) {
        val path = "/" + d.path.trim().trimStart('/')
        if (!reload && loaded) {
            val js = JSONObject.quote(path)
            web.evaluateJavascript(
                "history.pushState(null, '', $js); window.dispatchEvent(new CustomEvent('location-changed', {detail: {replace: false}}));",
                null,
            )
        } else {
            loaded = false
            web.loadUrl(origin() + path + "?external_auth=1")
        }
    }

    // ---- Touches, dimming and waking -------------------------------------------------------

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

    private fun wakeUp() {
        lastTouch.longValue = System.currentTimeMillis()
        dimmed.value = false
        brightness(-1f)
    }

    private fun dim() {
        dimmed.value = true
        // Photos need some light; the night clock (and the slideshow's night) as little as possible.
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val slides = Prefs.slides(this)
        val photos = slides.on && slides.album.isNotBlank() && !(slides.clockAtNight && (hour >= 22 || hour < 6))
        brightness(if (photos) 0.4f else 0.02f)
    }

    private fun brightness(level: Float) {
        window.attributes = window.attributes.also {
            it.screenBrightness = if (level < 0) WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE else level
        }
    }

    private fun talk() {
        wake?.stop()
        startActivity(AskActivity.intent(this, talk = true, from = AskActivity.From.DASHBOARD))
    }

    override fun onResume() {
        super.onResume()
        if (!::web.isInitialized) return
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

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        onResume()
    }

    override fun onPause() {
        wake?.stop()
        if (listening.value == Listening.ON) listening.value = Listening.OFF
        if (::web.isInitialized) web.onPause()
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Back from your chats after a while: straight to the screen saver.
        if (intent.getBooleanExtra(EXTRA_IDLE, false) && ::web.isInitialized) {
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

    override fun onDestroy() {
        if (::web.isInitialized) web.destroy()
        super.onDestroy()
    }

    // ---- The screen -----------------------------------------------------------------------

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
        // The screen saver after a while without a touch; while it shows, watch the motion sensor.
        LaunchedEffect(Unit) {
            val seconds = Prefs.dimSeconds(this@DashboardActivity)
            val motion = Prefs.motionEntity(this@DashboardActivity).trim()
            while (true) {
                delay(3_000)
                if (!dimmed.value && seconds > 0 && System.currentTimeMillis() - lastTouch.longValue > seconds * 1000L &&
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
                !isHome() -> finish()
            }
        }

        Box(Modifier.fillMaxSize().background(Color.Black)) {
            Column(Modifier.fillMaxSize()) {
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

    private fun isHome(): Boolean {
        val home = packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
        return home?.activityInfo?.packageName == packageName
    }

    /**
     * The one bar across the top: Home Assistant's back and menu, the time and weather, the
     * dashboard's views, how she feels (tap to talk), and your chats, reload, apps and settings.
     */
    @Composable
    private fun Strip(now: HouseNow, clock: Date, dashboards: List<Dashboard>, onTalk: () -> Unit, onApps: () -> Unit) {
        val h = header.value
        var pickDashboard by remember { mutableStateOf(false) }
        Surface(color = MaterialTheme.colorScheme.surface) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            ) {
                IconButton(onClick = ::back, enabled = h.subview || canGoBack.value) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                IconButton(onClick = ::menu) { Icon(Icons.Rounded.Menu, "Home Assistant's menu") }
                Column(Modifier.padding(start = 4.dp, end = 6.dp)) {
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
                    Column(Modifier.width(180.dp)) {
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
                FilledTonalIconButton(onClick = {
                    startActivity(
                        Intent(this@DashboardActivity, MainActivity::class.java)
                            .putExtra(MainActivity.EXTRA_FROM_WALL, true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }) { Icon(Icons.Outlined.Forum, "Chats") }
                FilledTonalIconButton(onClick = { web.reload() }) { Icon(Icons.Outlined.Refresh, "Reload the dashboard") }
                FilledTonalIconButton(onClick = onApps) { Icon(Icons.Outlined.Apps, "Apps") }
                FilledTonalIconButton(onClick = {
                    startActivity(Intent(this@DashboardActivity, SettingsActivity::class.java))
                }) { Icon(Icons.Outlined.Settings, "Settings") }
            }
        }
    }

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

    private data class App(val label: String, val packageName: String, val icon: ImageBitmap)

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
