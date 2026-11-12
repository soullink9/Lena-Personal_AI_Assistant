@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package io.github.soullink9.lena

import android.content.Context
import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.text.format.DateFormat
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.AddComment
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Open WebUI inside a native shell: top bar, drawer with your chats, and the locks around the page. */
class MainActivity : ComponentActivity() {

    // Dark on a TV (Tv.darkOnTv).
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase)
        Tv.darkOnTv(this, newBase)
    }

    companion object {
        const val ACTION_NEW_CHAT = "io.github.soullink9.lena.NEW_CHAT"
        const val ACTION_TEMP_CHAT = "io.github.soullink9.lena.TEMP_CHAT"
        const val ACTION_OPEN_CHAT = "io.github.soullink9.lena.OPEN_CHAT"
        const val ACTION_SETTINGS = "io.github.soullink9.lena.SETTINGS"
        const val EXTRA_CHAT_ID = "chat_id"
        const val EXTRA_TEXT = "text"

        /**
         * Hides Open WebUI's own header and sidebar; the drawer and top bar replace them. Top frame only:
         * Android adds the script to every frame on your server's address, which (with Open WebUI's
         * "allow same origin" setting for iframes on) includes artifacts and previews, whose own
         * navigation or sidebar must not disappear.
         */
        private const val HIDE_CSS =
            "nav.drag-region{display:none!important}" +
                "div[role=\"navigation\"][data-state]{display:none!important}" +
                "#sidebar{display:none!important}"
        private const val ADD_STYLE =
            "(function(){if(window.top!==window||document.getElementById('lena-shell'))return;" +
                "var s=document.createElement('style');s.id='lena-shell';s.textContent='$HIDE_CSS';" +
                "(document.head||document.documentElement).appendChild(s);})();"
        private const val REMOVE_STYLE =
            "(function(){var s=document.getElementById('lena-shell');if(s)s.remove();})();"

        /**
         * Hides Open WebUI's voice-call button, since Lena has no calls, whichever look is on. It sits where
         * Send is while the box is empty; its English labels are its only stable handle.
         */
        private const val NO_CALLS_CSS =
            "button[aria-label=\"Voice mode\"],button[aria-label=\"Return to call\"],button.call-active{display:none!important}"
    }

    private lateinit var web: WebView
    private lateinit var server: Uri
    private lateinit var api: Api
    private val signIn = mutableListOf<Uri>()
    private var shellScript: ScriptHandler? = null

    private var themeHandle: ScriptHandler? = null

    /** Lena's look for Open WebUI's pages (assets/css/open_webui.css), read once. */
    private val lookCss by lazy { assets.open("css/open_webui.css").bufferedReader().use { it.readText() } }

    /**
     * The style Lena adds to Open WebUI's pages, in the top frame only like the shell style above: her
     * look when it's on (Settings → Chat screen → Lena's look), and always the hidden call button. Run
     * again, it replaces the style already there, so switching the look takes effect at once.
     */
    private fun themeScript(look: Boolean): String {
        val css = (if (look) lookCss + "\n" else "") + NO_CALLS_CSS
        return "(function(){if(window.top!==window)return;" +
            "var s=document.getElementById('lena-theme')||document.createElement('style');s.id='lena-theme';" +
            "s.textContent=${JSONObject.quote(css)};" +
            "if(!s.parentNode)(document.head||document.documentElement).appendChild(s);})();"
    }

    private val currentUrl = mutableStateOf("")
    private val canGoBack = mutableStateOf(false)
    private val showMenus = mutableStateOf(false)

    /** Whether the Chat | Home switch shows: Home Assistant is set up and the switch is on in Settings. */
    private val hasHome = mutableStateOf(false)

    /** Whether Lena's look is on for Open WebUI's pages (the top bar matches the page either way). */
    private val lenaLook = mutableStateOf(true)

    /**
     * Dashboard mode: Lena's wall display is this device's home screen. Then (and only then) this screen is
     * full screen like the dashboards, and goes back to them and their screen saver when left alone.
     * Otherwise both are normal screens with the status and navigation bars.
     */
    private var dashboardMode = false

    /** [dashboardMode], for the screen to draw from: Apps is in the menu only then. */
    private val wallDisplay = mutableStateOf(false)

    /** Whether the apps list is showing (from Apps at the bottom of the menu). */
    private val showApps = mutableStateOf(false)

    /** The Refresh generation this page was loaded at; a newer one reloads it (see [Refresh]). */
    private var refreshSeen = Refresh.generation

    /**
     * The wake word, when it's set to listen whenever Lena is open (Settings → Talking to Lena → Wake word
     * → Listens). Stopped while you're away, and while the page itself uses the microphone.
     */
    private var wake: WakeListener? = null

    private var pendingMic: PermissionRequest? = null
    private var pendingFiles: ValueCallback<Array<Uri>>? = null

    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        pendingMic?.let { if (granted) it.grant(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE)) else it.deny() }
        pendingMic = null
    }

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val filePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        val uris: Array<Uri>? = if (result.resultCode == RESULT_OK && data != null) {
            data.clipData?.let { clip -> Array(clip.itemCount) { clip.getItemAt(it).uri } }
                ?: data.data?.let { arrayOf(it) }
        } else null
        pendingFiles?.onReceiveValue(uris)
        pendingFiles = null
    }

    /**
     * Without a server there's nothing to show, so it goes straight to Settings. Otherwise it builds the
     * web view once (it's kept across the screen's rebuilds) and opens what the intent asked for, or the
     * page it was on before a theme change.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A TV has no chat (no keyboard to type with): a widget, shortcut or notification opens the wall display.
        if (Tv.isTv(this) && intent?.action != ACTION_SETTINGS) {
            startActivity(Intent(this, DashboardActivity::class.java))
            finish()
            return
        }
        // With Assist answering (Settings → Who answers), Lena's own chat is the chat screen.
        if (Prefs.answers(this) == Answerer.ASSIST && intent?.action != ACTION_SETTINGS) {
            startActivity(Intent(this, AssistChatActivity::class.java))
            finish()
            return
        }
        val url = Prefs.server(this)
        if (url == null || intent?.action == ACTION_SETTINGS) {
            startActivity(Intent(this, SettingsActivity::class.java))
            finish()
            return
        }
        server = Uri.parse(url)
        api = Api(url, applicationContext)
        Prefs.signIn(this).split(" ").filter { it.isNotBlank() }.forEach { signIn += Uri.parse(it) }
        showMenus.value = Prefs.showOpenWebUiMenus(this)
        hasHome.value = HomeAssistant.from(this) != null && Prefs.homeSwitch(this)
        lenaLook.value = Prefs.lenaLook(this)

        enableEdgeToEdge()
        web = createWebView()
        applyTheme()
        applyShellStyle()
        setContent {
            LenaTheme {
                Box(Modifier.fillMaxSize()) {
                    Shell()
                    // The apps list, from Apps at the bottom of the menu (wall display only).
                    if (showApps.value) AppsDrawer(onClose = { showApps.value = false })
                }
            }
        }
        // Switching dark mode rebuilds the screen so the page re-themes; come back to the same chat.
        val restored = savedInstanceState?.getString("url")?.takeIf { isServer(Uri.parse(it)) }
        if (restored != null) web.loadUrl(restored) else handle(intent, fresh = true)
        askForNotificationsOnce()
        applyWindowMode()
        // The wall display's screensaver, here too; not while she's still answering the open chat.
        WallDisplay.watch(this, stillWorking = { answering() })
        watchForIdle()
    }

    /**
     * Full screen in dashboard mode (the wall display is the home screen), so switching between Chat and
     * Home keeps the same view; a normal screen otherwise. Checked again on every return, since the home
     * app can be changed in Android's settings meanwhile.
     */
    private fun applyWindowMode() {
        dashboardMode = DashboardActivity.isHomeScreen(this)
        wallDisplay.value = dashboardMode
        WindowCompat.getInsetsController(window, window.decorView).apply {
            if (dashboardMode) {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    /** Every touch or key on this screen: the wall display's timers start again (see [WallDisplay]). */
    override fun onUserInteraction() {
        super.onUserInteraction()
        WallDisplay.touched()
    }

    /** Whether she's still answering in the open chat (Open WebUI is still working on it). */
    private suspend fun answering(): Boolean =
        chatIdOf(currentUrl.value)?.let { id -> runCatching { api.runningTasks(id) }.getOrDefault(0) > 0 } == true

    /**
     * In dashboard mode, left alone for a while (Settings → Wall display → Screensaver → Back from your
     * chats after): back to the dashboards. The screensaver keeps its own timer (Screensaver after, on
     * the same clock as every screen), so whichever is shorter comes first. Not while the keyboard is up,
     * while you're talking to her in the sheet, or while she's still answering; the wait starts again
     * once she's done.
     */
    private fun watchForIdle() {
        lifecycleScope.launch {
            while (true) {
                delay(5_000)
                val seconds = Prefs.returnSeconds(this@MainActivity)
                if (!dashboardMode || seconds <= 0 || !lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) continue
                // Typing, or talking to her in the sheet (listening, thinking, speaking), counts as being here.
                if (WallDisplay.typing(this@MainActivity) || Busy.talking) {
                    WallDisplay.touched()
                    continue
                }
                if (WallDisplay.idleMillis() < seconds * 1000L) continue
                if (answering()) {
                    WallDisplay.touched()
                    continue
                }
                Logs.i("Chats", "Left alone for a while; back to the wall display")
                startActivity(
                    Intent(this@MainActivity, DashboardActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                )
                finish()
                return@launch
            }
        }
    }

    /** Remembers the page, so switching dark mode (which rebuilds the screen) comes back to the same chat. */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::web.isInitialized) outState.putString("url", currentUrl.value)
    }

    /** "Lena finished" notifications are on by default, so ask for the permission the first time. */
    private fun askForNotificationsOnce() {
        val prefs = getSharedPreferences("lena", MODE_PRIVATE)
        if (prefs.getBoolean("asked_notifications", false) || !Prefs.notifyReplies(this)) return
        prefs.edit().putBoolean("asked_notifications", true).apply()
        if (!Compat.notificationsAllowed(this)) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /**
     * Lena is singleTask, so a widget, notification or shortcut reaches the running screen here instead of
     * starting another.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        WallDisplay.touched()
        handle(intent, fresh = false)
    }

    /**
     * Opens what an intent asks for: a new or temporary chat, a chat by id, or Settings. From the
     * launcher, a fresh start opens a new chat and an already-open Lena just comes back as it was. (An
     * old "Voice call" shortcut from before calls were removed lands here too, like the launcher.)
     */
    private fun handle(i: Intent?, fresh: Boolean) {
        when (i?.action) {
            ACTION_NEW_CHAT -> openNew(text = i.getStringExtra(EXTRA_TEXT))
            ACTION_TEMP_CHAT -> openNew(temporary = true)
            ACTION_OPEN_CHAT -> i.getStringExtra(EXTRA_CHAT_ID)?.let { openChat(it) } ?: run { if (fresh) openNew() }
            ACTION_SETTINGS -> startActivity(Intent(this, SettingsActivity::class.java))
            else -> if (fresh) openNew() // from the launcher, an already-open Lena just comes back as it was
        }
    }

    // ---- Navigation -------------------------------------------------------------------------

    /**
     * A new chat with your model, through Open WebUI's own URL parameters: a temporary chat, or text
     * already in the box (never sent by itself).
     */
    private fun openNew(temporary: Boolean = false, text: String? = null) {
        val b = server.buildUpon()
        Prefs.effectiveModel(this)?.let { b.appendQueryParameter("model", it) }
        if (temporary) b.appendQueryParameter("temporary-chat", "true")
        if (!text.isNullOrBlank()) b.appendQueryParameter("q", text) // fills the box; never sends by itself
        web.loadUrl(b.build().toString())
    }

    /** Opens one of your chats by id. */
    private fun openChat(id: String) {
        web.loadUrl(server.buildUpon().appendPath("c").appendPath(id).build().toString())
    }

    /**
     * The Chat | Home switch: Home opens the dashboards, in the same view as this screen (full screen in
     * dashboard mode, normal otherwise). In dashboard mode your chats were a visit from the home screen,
     * so they close, as when they're left alone.
     */
    private fun switchTo(screen: MainScreen) {
        if (screen != MainScreen.HOME) return
        startActivity(Intent(this, DashboardActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        if (dashboardMode) finish()
    }

    /** The chat id in a page address (…/c/<id>), allowing for Open WebUI served under a path. */
    private fun chatIdOf(url: String): String? {
        val path = Uri.parse(url).path ?: return null
        val base = server.path?.trimEnd('/') ?: ""
        return Regex("^${Regex.escape(base)}/c/([^/]+)").find(path)?.groupValues?.get(1)
    }

    // ---- The page ---------------------------------------------------------------------------

    /**
     * The web view for Open WebUI, locked down to what it needs: JavaScript and storage on, files, content
     * URIs, location and pop-up windows off, and plain http never mixed in.
     */
    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(): WebView = WebView(this).apply {
        // Fill the space Compose gives it. Without this the web view sizes itself to the page,
        // and Open WebUI (a full-height app) ends up laid out off screen.
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        setBackgroundColor(getColor(R.color.bg))
        settings.apply {
            javaScriptEnabled = true                 // Open WebUI is a JavaScript app
            domStorageEnabled = true                 // keeps you signed in
            mediaPlaybackRequiresUserGesture = false // lets call-mode replies play out loud
            allowFileAccess = false
            allowContentAccess = false
            setGeolocationEnabled(false)
            setSupportMultipleWindows(false)         // new-tab links come through shouldOverrideUrlLoading
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
        webViewClient = Client()
        webChromeClient = Chrome()
    }

    /** Your server's origin (scheme, host and port), which the shell style and sign-in checks are scoped to. */
    private fun serverOrigin(): String = "https://${server.host}" + if (server.port == -1) "" else ":${server.port}"

    /** Puts the current look (and the hidden call button) on Open WebUI's pages, now and on every page load. */
    private fun applyTheme() {
        themeHandle?.remove()
        themeHandle = null
        val script = themeScript(lenaLook.value)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            themeHandle = WebViewCompat.addDocumentStartJavaScript(web, script, setOf(serverOrigin()))
        }
        web.evaluateJavascript(script, null)
    }

    /** Adds or removes the style that hides Open WebUI's header, now and on every page load. */
    private fun applyShellStyle() {
        shellScript?.remove()
        shellScript = null
        if (!showMenus.value && WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            shellScript = WebViewCompat.addDocumentStartJavaScript(web, ADD_STYLE, setOf(serverOrigin()))
        }
        web.evaluateJavascript(if (showMenus.value) REMOVE_STYLE else ADD_STYLE, null)
    }

    /** Whether an address is your Open WebUI. */
    private fun isServer(u: Uri?) = sameOrigin(u, server)
    /** Whether an address may load inside Lena: your Open WebUI, or one of your sign-in addresses. */
    private fun isAllowed(u: Uri?) = isServer(u) || signIn.any { sameOrigin(u, it) }

    /** Same origin, the browser's way: https, the same host and the same port. Only https is ever trusted. */
    private fun sameOrigin(u: Uri?, base: Uri): Boolean =
        u != null && u.scheme == "https" && base.host != null &&
            base.host.equals(u.host, ignoreCase = true) && port(u) == port(base)

    /** The port, with https's default filled in, so https://host and https://host:443 count as the same. */
    private fun port(u: Uri) = if (u.port == -1) 443 else u.port

    /** Only your server and sign-in addresses load in the app. Other links open in your normal browser. */
    private inner class Client : WebViewClient() {
        /**
         * Lets your server and sign-in pages load here; sends ordinary links to your browser; blocks
         * everything else (intent:, file:, javascript: and the like) so a page can't use Lena to open other
         * apps.
         */
        override fun shouldOverrideUrlLoading(view: WebView, req: WebResourceRequest): Boolean {
            val u = req.url
            if (isAllowed(u)) return false
            val scheme = u.scheme
            if (!req.isForMainFrame) {
                // Open WebUI draws some previews in frames built from local content; allow those only.
                return !(scheme == "about" || scheme == "blob" || scheme == "data")
            }
            if (scheme == "https" || scheme == "http") {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, u).addCategory(Intent.CATEGORY_BROWSABLE))
                } catch (_: ActivityNotFoundException) {
                }
            }
            return true // intent:, file:, javascript: and anything else is blocked
        }

        /**
         * A page that didn't load goes in the log (for Settings → Software version → Logs), without its query, which can hold
         * tokens.
         */
        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: android.webkit.WebResourceError) {
            if (request.isForMainFrame) Logs.w("Open WebUI", "Page didn't load: ${error.description} (${request.url.path})")
        }

        /** The same for a page that answered with an HTTP error. */
        override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: android.webkit.WebResourceResponse) {
            if (request.isForMainFrame) Logs.w("Open WebUI", "Page answered HTTP ${response.statusCode} (${request.url.path})")
        }

        /**
         * Every navigation, including Open WebUI's in-page ones: track the address (it decides the title
         * and the chat), and sign in by herself if it landed on the sign-in page.
         */
        override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
            currentUrl.value = url ?: ""
            canGoBack.value = view.canGoBack()
            maybeSignIn(url)
        }

        /**
         * On web views without document-start scripts, Lena's look and the shell style go in once the page
         * has loaded instead.
         */
        override fun onPageFinished(view: WebView, url: String?) {
            canGoBack.value = view.canGoBack()
            val older = !WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
            if (older && isServer(url?.let { Uri.parse(it) })) view.evaluateJavascript(themeScript(lenaLook.value), null)
            if (!showMenus.value && shellScript == null && isServer(url?.let { Uri.parse(it) })) {
                view.evaluateJavascript(ADD_STYLE, null) // older web views without document-start scripts
            }
        }
    }

    /** Mic for your server only; no camera, no location. Plus the file picker for attachments. */
    private inner class Chrome : WebChromeClient() {
        /**
         * The page may use the microphone (voice input) only when it's your server asking, and
         * only after Lena has Android's permission; the camera and everything else are refused.
         */
        override fun onPermissionRequest(req: PermissionRequest) {
            val mic = PermissionRequest.RESOURCE_AUDIO_CAPTURE in req.resources
            if (!mic || !isServer(req.origin)) {
                req.deny()
                return
            }
            // Only one thing can hold the microphone: the page's voice input wins over the wake word until
            // you next come back to this screen.
            wake?.stop()
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                req.grant(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE))
            } else {
                pendingMic?.deny()
                pendingMic = req
                micPermission.launch(Manifest.permission.RECORD_AUDIO)
            }
        }

        /**
         * Open WebUI's attach button: Android's file picker, allowing several files when the page asks for
         * that.
         */
        override fun onShowFileChooser(view: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
            pendingFiles?.onReceiveValue(null)
            pendingFiles = callback
            val pick = params.createIntent()
            if (params.mode == FileChooserParams.MODE_OPEN_MULTIPLE) pick.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            return try {
                filePicker.launch(pick)
                true
            } catch (_: ActivityNotFoundException) {
                pendingFiles = null
                false
            }
        }
    }

    private var signingIn = false

    /**
     * Open WebUI sent the page to its sign-in screen (its session ended, for example because the
     * server restarted). With automatic sign-in on, sign in with the saved details, hand the page
     * its new session the way its own sign-in does, and go back to where it was.
     */
    private fun maybeSignIn(url: String?) {
        val u = url?.let { Uri.parse(it) } ?: return
        val base = server.path?.trimEnd('/') ?: ""
        if (signingIn || !isServer(u) || u.path?.trimEnd('/') != "$base/auth" || !OpenWebUiSignIn.configured(this)) return
        signingIn = true
        lifecycleScope.launch {
            val token = OpenWebUiSignIn.signIn(this@MainActivity)
            signingIn = false
            if (token == null) return@launch
            val back = u.getQueryParameter("redirect")?.takeIf { it.startsWith("/") && !it.startsWith("//") }
            val target = serverOrigin() + (back ?: "$base/")
            web.evaluateJavascript(
                "try{localStorage.setItem('token',${JSONObject.quote(token)});}catch(e){}location.replace(${JSONObject.quote(target)});",
                null,
            )
        }
    }

    // ---- Lifecycle --------------------------------------------------------------------------

    /** Back on screen with a chat open: no need for a "Lena finished" notification about it any more. */
    override fun onStart() {
        super.onStart()
        if (::web.isInitialized) chatIdOf(currentUrl.value)?.let { ReplyWorker.cancel(this, it) }
    }

    /**
     * Picks up changes made in Settings while away: the Chat | Home switch (and whether Home Assistant is
     * set up), full screen in dashboard mode, the wake word, Lena's look, and whether Open WebUI's own
     * menus show.
     */
    override fun onResume() {
        super.onResume()
        // Switched to Assist answering in Settings meanwhile: Lena's own chat takes over.
        if (Prefs.answers(this) == Answerer.ASSIST) {
            startActivity(Intent(this, AssistChatActivity::class.java))
            finish()
            return
        }
        hasHome.value = HomeAssistant.from(this) != null && Prefs.homeSwitch(this)
        applyWindowMode()
        listenForName()
        if (::web.isInitialized && refreshSeen != Refresh.generation) {
            refreshSeen = Refresh.generation
            web.reload()
        }
        if (::web.isInitialized) {
            val look = Prefs.lenaLook(this)
            if (look != lenaLook.value) {
                lenaLook.value = look
                applyTheme()
            }
            web.onResume()
            // "Show Open WebUI's own menus" lives in Settings; pick up a change made there.
            val wanted = Prefs.showOpenWebUiMenus(this)
            if (wanted != showMenus.value) {
                showMenus.value = wanted
                applyShellStyle()
            }
        }
    }

    /** Saves cookies to disk now, so the sign-in survives Android stopping the app in the background. */
    override fun onPause() {
        wake?.stop()
        if (::web.isInitialized) {
            web.onPause()
            CookieManager.getInstance().flush()
        }
        super.onPause()
    }

    /**
     * Listens for her name while this screen is showing, when the wake word is on and set to listen
     * whenever Lena is open. Hearing it opens the sheet, listening, over your chats.
     */
    private fun listenForName() {
        if (!Prefs.wakeWord(this) || !Prefs.wakeEverywhere(this)) {
            wake?.stop()
            return
        }
        val listener = wake ?: WakeListener(this, onHeard = {
            wake?.stop()
            startActivity(AskActivity.intent(this, talk = true))
        }).also { wake = it }
        listener.start() // false without the microphone permission or the model; then it simply doesn't listen
    }

    /** Leaving while Lena is still answering: watch for the end and notify. */
    override fun onStop() {
        super.onStop()
        if (!::web.isInitialized || !Prefs.notifyReplies(this)) return
        if (!Compat.notificationsAllowed(this)) return
        val chatId = chatIdOf(currentUrl.value) ?: return
        val appContext = applicationContext
        lifecycleScope.launch {
            if (runCatching { api.runningTasks(chatId) }.getOrDefault(0) > 0) ReplyWorker.enqueue(appContext, chatId)
        }
    }

    /** Destroys the web view with the screen, or it keeps running scripts and holding memory. */
    override fun onDestroy() {
        if (::web.isInitialized) web.destroy()
        super.onDestroy()
    }

    // ---- The native shell -------------------------------------------------------------------

    /**
     * The whole screen: the drawer (her status, then as many pinned chats, folders and recent chats as
     * fit, View all chats, and two buttons); the top bar with the Chat | Home switch and
     * the new-chat buttons; and the page.
     */
    @Composable
    private fun Shell() {
        val scope = rememberCoroutineScope()
        val drawer = rememberDrawerState(DrawerValue.Closed)
        var recent by remember { mutableStateOf<List<ChatItem>?>(null) }
        var pinned by remember { mutableStateOf<List<ChatItem>>(emptyList()) }
        var folders by remember { mutableStateOf<List<Api.Folder>>(emptyList()) }
        val openFolders = remember { mutableStateMapOf<String, Boolean>() }
        val folderChats = remember { mutableStateMapOf<String, List<ChatItem>>() }
        var chatsError by remember { mutableStateOf<String?>(null) }
        var version by remember { mutableStateOf<String?>(null) }
        var feelings by remember { mutableStateOf<Feelings?>(null) }
        var checked by remember { mutableStateOf(false) }
        var refresh by remember { mutableIntStateOf(0) }
        var query by remember { mutableStateOf("") }
        var results by remember { mutableStateOf<List<ChatItem>?>(null) }
        var reordering by remember { mutableStateOf<Reorder?>(null) }
        val chatId = chatIdOf(currentUrl.value)
        val actions = remember {
            ChatActions(this, api, scope, onChanged = { refresh++ }, onDeleted = { id -> if (id == chatIdOf(currentUrl.value)) openNew() })
        }

        // Remember which model is Lena, for new chats and the sheet.
        LaunchedEffect(Unit) {
            val models = runCatching { api.models() }.getOrDefault(emptyList())
            if (models.isNotEmpty()) Prefs.rememberLena(this@MainActivity, models)
        }

        // Fill the drawer when it opens, and after a change made from it.
        LaunchedEffect(drawer.isOpen, refresh) {
            if (!drawer.isOpen) return@LaunchedEffect
            version = api.version()
            checked = true
            feelings = HomeAssistant.from(this@MainActivity)?.let { runCatching { it.feelings() }.getOrNull() }
            try {
                pinned = runCatching { inYourOrder(this@MainActivity, api.pinned()) }.getOrDefault(pinned)
                // None when they're off in Settings → Chat screen, or turned off on the server.
                folders = if (!Prefs.drawerFolders(this@MainActivity)) emptyList()
                    else runCatching { api.folders(Prefs.folderOrder(this@MainActivity)) }.getOrDefault(emptyList())
                folders.forEach { f ->
                    if (openFolders[f.id] == null) openFolders[f.id] = f.expanded
                    if (openFolders[f.id] == true) folderChats[f.id] = runCatching { api.folderChats(f.id) }.getOrDefault(emptyList())
                }
                recent = api.chats()
                chatsError = null
            } catch (_: AuthException) {
                chatsError = "Sign in to see your chats"
            } catch (_: Exception) {
                chatsError = if (version == null) null else "Couldn't load your chats"
            }
        }

        // Search as you type (after a short pause), from the drawer's search box.
        LaunchedEffect(query) {
            if (query.isBlank()) { results = null; return@LaunchedEffect }
            delay(300)
            results = runCatching { api.search(query.trim()) }.getOrDefault(emptyList())
        }

        /**
         * Closes the drawer and does the action at the same time, so the page starts loading while the
         * drawer slides away.
         */
        fun closeThen(action: () -> Unit) {
            scope.launch { drawer.close() }
            action()
        }

        BackHandler(enabled = drawer.isOpen) { scope.launch { drawer.close() } }
        BackHandler(enabled = !drawer.isOpen && canGoBack.value) { web.goBack() }

        ModalNavigationDrawer(
            drawerState = drawer,
            gesturesEnabled = drawer.isOpen, // swipes stay with the page; open with the menu button
            drawerContent = {
                // 320dp, or less on a narrow outer screen, so a strip of the page always shows beside it.
                val drawerWidth = minOf(320.dp, (LocalConfiguration.current.screenWidthDp * 0.85f).dp)
                ModalDrawerSheet(Modifier.width(drawerWidth)) {
                    Column(Modifier.fillMaxHeight()) {
                        // Who and how she is
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.padding(start = 20.dp, top = 20.dp, end = 16.dp),
                        ) {
                            LenaAvatar(44.dp)
                            Column {
                                Text("Lena", style = MaterialTheme.typography.titleLarge)
                                Status(checked, version)
                            }
                        }
                        feelings?.let { FeelingsLine(it, Modifier.padding(start = 20.dp, end = 16.dp, top = 12.dp)) }
                        SearchField(query, "Search chats", Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)) { query = it }

                        // Search results, or pinned, folders, then recent: as many as fit, never scrolling.
                        FitColumn(Modifier.weight(1f).padding(start = 12.dp, end = 12.dp, top = 8.dp)) {
                            if (query.isNotBlank()) DrawerResults(results, pinned.map { it.id }.toSet(), chatId, actions) { id ->
                                closeThen { openChat(id) }
                            } else DrawerChats(
                                pinned = pinned.take(Prefs.pinnedShown(this@MainActivity)),
                                pinnedIds = pinned.map { it.id }.toSet(),
                                folders = folders,
                                openFolders = openFolders,
                                folderChats = folderChats,
                                recent = recent,
                                error = chatsError,
                                currentId = chatId,
                                actions = actions,
                                onOpen = { id -> closeThen { openChat(id) } },
                                onReorderPinned = { reordering = Reorder.PINNED },
                                onReorderFolders = { reordering = Reorder.FOLDERS },
                                onFolder = { f ->
                                    val open = openFolders[f.id] != true
                                    openFolders[f.id] = open
                                    scope.launch {
                                        runCatching { api.setFolderExpanded(f.id, open) }
                                        if (open) folderChats[f.id] = runCatching { api.folderChats(f.id) }.getOrDefault(emptyList())
                                    }
                                },
                            )
                        }

                        // Everything else: pinned, folders and your whole history, with search (and while
                        // searching, every result, since the menu shows only what fits).
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .clickable {
                                    val all = Intent(this@MainActivity, ChatsActivity::class.java)
                                    if (query.isNotBlank()) all.putExtra(ChatsActivity.EXTRA_QUERY, query.trim())
                                    closeThen { startActivity(all) }
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        ) {
                            Icon(Icons.AutoMirrored.Outlined.List, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(if (query.isNotBlank()) "See all results" else "View all chats",
                                style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        // Settings, memory and (on the wall display, where there's no launcher) the apps list, side
                        // by side. Reloading is Settings → Refresh all connections.
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        Row(
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            DrawerIcon(Icons.Outlined.Settings, "Settings") {
                                closeThen { startActivity(Intent(this@MainActivity, SettingsActivity::class.java)) }
                            }
                            DrawerIcon(Icons.Outlined.Psychology, "Memory and knowledge") {
                                closeThen { startActivity(Intent(this@MainActivity, MemoryActivity::class.java)) }
                            }
                            if (wallDisplay.value) {
                                DrawerIcon(Icons.Outlined.Apps, "Apps") { closeThen { showApps.value = true } }
                            }
                        }
                    }
                }
            },
        ) {
            Scaffold(
                containerColor = pageBackground(lenaLook.value),
                topBar = {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = pageBackground(lenaLook.value)),
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawer.open() } }) { Icon(Icons.Rounded.Menu, "Menu") }
                        },
                        title = {
                            // The switch between your chats and the dashboards (when Home Assistant is set
                            // up; without it there's only one screen). Her logo is in the drawer.
                            if (hasHome.value) ScreenSwitcher(MainScreen.CHAT, ::switchTo)
                            else Text("Lena", style = MaterialTheme.typography.titleMedium)
                        },
                        actions = {
                            IconButton(onClick = { openNew(temporary = true) }) { Icon(Icons.Outlined.VisibilityOff, "Temporary chat") }
                            IconButton(onClick = { openNew() }) { Icon(Icons.Outlined.AddComment, "New chat") }
                        },
                    )
                },
            ) { padding ->
                Box(Modifier.padding(padding).consumeWindowInsets(padding).imePadding().fillMaxSize()) {
                    AndroidView(factory = { web }, modifier = Modifier.fillMaxSize())
                    EdgeSwipe(Modifier.align(Alignment.CenterStart)) { scope.launch { drawer.open() } }
                }
            }
        }

        actions.Dialogs()

        when (reordering) {
            Reorder.PINNED -> ReorderDialog("Order of pinned chats", pinned.map { it.id to it.title }) { ids ->
                reordering = null
                if (ids != null) { Prefs.setPinnedOrder(this@MainActivity, ids); refresh++ }
            }
            Reorder.FOLDERS -> ReorderDialog("Order of folders", folders.filter { it.depth == 0 }.map { it.id to it.name }) { ids ->
                reordering = null
                if (ids != null) { Prefs.setFolderOrder(this@MainActivity, ids); refresh++ }
            }
            null -> Unit
        }
    }

    /** What's being put in order from the menu: your pinned chats, or your folders. */
    private enum class Reorder { PINNED, FOLDERS }

    /** Search results in the drawer, as many as fit; See all results below opens the rest in All chats. */
    @Composable
    private fun DrawerResults(results: List<ChatItem>?, pinnedIds: Set<String>, currentId: String?, actions: ChatActions, onOpen: (String) -> Unit) {
        when {
            results == null -> ListHint("Searching…")
            results.isEmpty() -> ListHint("No matching chats")
            else -> {
                DrawerHeading("Results")
                results.forEach { c ->
                    actions.WithMenu(c, c.id in pinnedIds) { longPress ->
                        ChatLine(c, c.id == currentId, null, 40.dp, 16.dp, RoundedCornerShape(20.dp), Color.Transparent, { onOpen(c.id) }, longPress)
                    }
                }
            }
        }
    }

    /**
     * The drawer's chats, as children of a FitColumn (so whatever doesn't fit is left out): your pinned
     * chats, then your folders (tap one to open or close it; open ones show their chats), then your recent
     * chats. Pinned chats and folders are in your own order (the Reorder button on their heading).
     * Long-press a chat for Pin, Move to folder, Rename and Delete.
     */
    @Composable
    private fun DrawerChats(
        pinned: List<ChatItem>,
        pinnedIds: Set<String>,
        folders: List<Api.Folder>,
        openFolders: Map<String, Boolean>,
        folderChats: Map<String, List<ChatItem>>,
        recent: List<ChatItem>?,
        error: String?,
        currentId: String?,
        actions: ChatActions,
        onOpen: (String) -> Unit,
        onReorderPinned: () -> Unit,
        onReorderFolders: () -> Unit,
        onFolder: (Api.Folder) -> Unit,
    ) {
        /** One chat in the drawer, indented inside a folder. */
        @Composable
        fun Line(c: ChatItem, indent: Boolean = false) {
            actions.WithMenu(c, c.id in pinnedIds) { longPress ->
                ChatLine(c, c.id == currentId, null, 40.dp, if (indent) 48.dp else 16.dp, RoundedCornerShape(20.dp),
                    Color.Transparent, { onOpen(c.id) }, longPress)
            }
        }

        if (pinned.isNotEmpty()) {
            DrawerHeading(PINNED_LABEL, if (pinned.size > 1) onReorderPinned else null)
            pinned.forEach { Line(it) }
        }
        if (folders.isNotEmpty()) DrawerHeading("Folders", if (folders.count { it.depth == 0 } > 1) onReorderFolders else null)
        folders.forEach { f ->
            val open = openFolders[f.id] == true
            FolderLine(f.label, open, 40.dp, 16.dp, RoundedCornerShape(20.dp), Color.Transparent, onToggle = { onFolder(f) }, onLongClick = { onFolder(f) })
            if (open) {
                val chats = folderChats[f.id]
                when {
                    chats == null -> ListHint("Loading…", Modifier.padding(start = 28.dp))
                    chats.isEmpty() -> ListHint("No chats in this folder", Modifier.padding(start = 28.dp))
                    else -> chats.forEach { Line(it, indent = true) }
                }
            }
        }
        when {
            error != null -> ListHint(error)
            recent == null -> ListHint("Loading…")
            recent.isEmpty() && pinned.isEmpty() && folders.isEmpty() -> ListHint("No chats yet")
            recent.isNotEmpty() -> {
                DrawerHeading("Recent")
                recent.forEach { Line(it) }
            }
        }
    }

    /**
     * A small coloured label over a part of the drawer (Pinned, Folders, Recent), with a Reorder button
     * when that part can be put in your own order.
     */
    @Composable
    private fun DrawerHeading(text: String, onReorder: (() -> Unit)? = null) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.layoutId(FIT_HEADING).fillMaxWidth().padding(start = 16.dp, top = if (onReorder != null) 4.dp else 12.dp),
        ) {
            Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f).padding(bottom = if (onReorder != null) 0.dp else 4.dp))
            if (onReorder != null) IconButton(onClick = onReorder) {
                Icon(Icons.Outlined.SwapVert, "Reorder $text", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    /**
     * A strip down the left edge that opens the menu when you swipe right from it, instead of
     * Android's back gesture. Android lets an app take over at most 200dp of each edge, so it
     * covers the middle of the screen; above and below it, the edge still goes back (and the
     * right edge always does). It's as wide as the phone's own back-gesture area.
     */
    @Composable
    private fun EdgeSwipe(modifier: Modifier, onOpen: () -> Unit) {
        val inset = WindowInsets.systemGestures.asPaddingValues().calculateLeftPadding(LocalLayoutDirection.current)
        val threshold = with(LocalDensity.current) { 24.dp.toPx() }
        Box(
            modifier.width(maxOf(inset, 20.dp)).height(200.dp)
                .systemGestureExclusion()
                .pointerInput(Unit) {
                    var dragged = 0f
                    detectHorizontalDragGestures(onDragStart = { dragged = 0f }) { change, amount ->
                        change.consume()
                        dragged += amount
                        if (dragged > threshold) {
                            dragged = Float.NEGATIVE_INFINITY // once per swipe
                            onOpen()
                        }
                    }
                },
        )
    }

    /** One of the icon buttons along the bottom of the drawer. */
    @Composable
    private fun DrawerIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
        FilledTonalIconButton(onClick = onClick, modifier = Modifier.size(48.dp)) { Icon(icon, label) }
    }

    /** Whether your server answered, with its Open WebUI version, under her name in the drawer. */
    @Composable
    private fun Status(checked: Boolean, version: String?) {
        val (color, text) = when {
            !checked -> MaterialTheme.colorScheme.outline to "Checking your server…"
            version == null -> MaterialTheme.colorScheme.error to "Can't reach your server"
            else -> MaterialTheme.colorScheme.primary to "Connected · Open WebUI $version"
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(top = 2.dp),
        ) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(color))
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
