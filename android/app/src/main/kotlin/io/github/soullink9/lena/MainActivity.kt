@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package io.github.soullink9.lena

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.text.format.DateFormat
import android.view.ViewGroup
import android.view.WindowManager
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
import androidx.compose.material.icons.outlined.AddComment
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.GraphicEq
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
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

    companion object {
        const val ACTION_CALL = "io.github.soullink9.lena.CALL"
        const val ACTION_NEW_CHAT = "io.github.soullink9.lena.NEW_CHAT"
        const val ACTION_TEMP_CHAT = "io.github.soullink9.lena.TEMP_CHAT"
        const val ACTION_OPEN_CHAT = "io.github.soullink9.lena.OPEN_CHAT"
        const val ACTION_SETTINGS = "io.github.soullink9.lena.SETTINGS"
        const val EXTRA_CHAT_ID = "chat_id"
        const val EXTRA_TEXT = "text"
        /** Opened from the wall display: after a while without a touch, go back to it. */
        const val EXTRA_FROM_WALL = "from_wall"
        private const val PINNED = "Pinned"

        /** Hides Open WebUI's own header and sidebar; the drawer and top bar replace them. */
        private const val HIDE_CSS =
            "nav.drag-region{display:none!important}" +
                "div[role=\"navigation\"][data-state]{display:none!important}" +
                "#sidebar{display:none!important}"
        private const val ADD_STYLE =
            "(function(){if(document.getElementById('lena-shell'))return;" +
                "var s=document.createElement('style');s.id='lena-shell';s.textContent='$HIDE_CSS';" +
                "(document.head||document.documentElement).appendChild(s);})();"
        private const val REMOVE_STYLE =
            "(function(){var s=document.getElementById('lena-shell');if(s)s.remove();})();"
    }

    private lateinit var web: WebView
    private lateinit var server: Uri
    private lateinit var api: Api
    private val signIn = mutableListOf<Uri>()
    private var shellScript: ScriptHandler? = null

    private val currentUrl = mutableStateOf("")
    private val canGoBack = mutableStateOf(false)
    private val showMenus = mutableStateOf(false)
    private val chatStyle = mutableStateOf(ChatListStyle.SLIM)

    private var fromWall = false
    private var lastInteraction = System.currentTimeMillis()

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        chatStyle.value = Prefs.chatList(this)

        enableEdgeToEdge()
        web = createWebView()
        applyShellStyle()
        setContent { LenaTheme { Shell() } }
        // Switching dark mode rebuilds the screen so the page re-themes; come back to the same chat.
        val restored = savedInstanceState?.getString("url")?.takeIf { isServer(Uri.parse(it)) }
        if (restored != null) web.loadUrl(restored) else handle(intent, fresh = true)
        askForNotificationsOnce()
        fromWall = intent?.getBooleanExtra(EXTRA_FROM_WALL, false) == true || wallIsHome()
        watchForIdle()
    }

    /** The wall display is this tablet's home screen, so your chats are a visit from it. */
    private fun wallIsHome(): Boolean {
        val home = packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)?.activityInfo
        return home?.packageName == packageName && home.name == DashboardActivity::class.java.name
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        lastInteraction = System.currentTimeMillis()
    }

    /**
     * Opened from the wall display and then left alone for a while (Settings → Wall display → When
     * no one's around): back to the dashboards, straight to the screen saver. Not while the
     * keyboard is up, during a voice call, or while she's still answering.
     */
    private fun watchForIdle() {
        lifecycleScope.launch {
            while (true) {
                delay(5_000)
                val seconds = Prefs.returnSeconds(this@MainActivity)
                if (!fromWall || seconds <= 0 || !lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) continue
                val typing = androidx.core.view.ViewCompat.getRootWindowInsets(window.decorView)
                    ?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true
                val calling = Uri.parse(currentUrl.value).getQueryParameter("call") == "true"
                if (typing || calling) { lastInteraction = System.currentTimeMillis(); continue }
                if (System.currentTimeMillis() - lastInteraction < seconds * 1000L) continue
                val answering = chatIdOf(currentUrl.value)?.let { id -> runCatching { api.runningTasks(id) }.getOrDefault(0) > 0 } == true
                if (answering) { lastInteraction = System.currentTimeMillis(); continue }
                Logs.i("Chats", "Left alone for a while; back to the wall display")
                startActivity(
                    Intent(this@MainActivity, DashboardActivity::class.java)
                        .putExtra(DashboardActivity.EXTRA_IDLE, true)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                )
                fromWall = false
                finish()
                return@launch
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::web.isInitialized) outState.putString("url", currentUrl.value)
    }

    /** "Lena finished" notifications are on by default, so ask for the permission the first time. */
    private fun askForNotificationsOnce() {
        val prefs = getSharedPreferences("lena", MODE_PRIVATE)
        if (prefs.getBoolean("asked_notifications", false) || !Prefs.notifyReplies(this)) return
        prefs.edit().putBoolean("asked_notifications", true).apply()
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        fromWall = intent.getBooleanExtra(EXTRA_FROM_WALL, false) || wallIsHome()
        lastInteraction = System.currentTimeMillis()
        handle(intent, fresh = false)
    }

    private fun handle(i: Intent?, fresh: Boolean) {
        when (i?.action) {
            ACTION_CALL -> openNew(call = true)
            ACTION_NEW_CHAT -> openNew(text = i.getStringExtra(EXTRA_TEXT))
            ACTION_TEMP_CHAT -> openNew(temporary = true)
            ACTION_OPEN_CHAT -> i.getStringExtra(EXTRA_CHAT_ID)?.let { openChat(it) } ?: run { if (fresh) openNew() }
            ACTION_SETTINGS -> startActivity(Intent(this, SettingsActivity::class.java))
            else -> if (fresh) openNew() // from the launcher, an already-open Lena just comes back as it was
        }
    }

    // ---- Navigation -------------------------------------------------------------------------

    private fun openNew(model: String? = null, call: Boolean = false, temporary: Boolean = false, text: String? = null) {
        val b = server.buildUpon()
        (model ?: Prefs.effectiveModel(this))?.let { b.appendQueryParameter("model", it) }
        if (call) b.appendQueryParameter("call", "true")
        if (temporary) b.appendQueryParameter("temporary-chat", "true")
        if (!text.isNullOrBlank()) b.appendQueryParameter("q", text) // fills the box; never sends by itself
        keepScreenOn(call)
        web.loadUrl(b.build().toString())
    }

    private fun openChat(id: String) {
        keepScreenOn(false)
        web.loadUrl(server.buildUpon().appendPath("c").appendPath(id).build().toString())
    }

    private fun keepScreenOn(on: Boolean) {
        if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun chatIdOf(url: String): String? {
        val path = Uri.parse(url).path ?: return null
        val base = server.path?.trimEnd('/') ?: ""
        return Regex("^${Regex.escape(base)}/c/([^/]+)").find(path)?.groupValues?.get(1)
    }

    // ---- The page ---------------------------------------------------------------------------

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

    private fun serverOrigin(): String = "https://${server.host}" + if (server.port == -1) "" else ":${server.port}"

    /** Adds or removes the style that hides Open WebUI's header, now and on every page load. */
    private fun applyShellStyle() {
        shellScript?.remove()
        shellScript = null
        if (!showMenus.value && WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            shellScript = WebViewCompat.addDocumentStartJavaScript(web, ADD_STYLE, setOf(serverOrigin()))
        }
        web.evaluateJavascript(if (showMenus.value) REMOVE_STYLE else ADD_STYLE, null)
    }

    private fun isServer(u: Uri?) = sameOrigin(u, server)
    private fun isAllowed(u: Uri?) = isServer(u) || signIn.any { sameOrigin(u, it) }

    private fun sameOrigin(u: Uri?, base: Uri): Boolean =
        u != null && u.scheme == "https" && base.host != null &&
            base.host.equals(u.host, ignoreCase = true) && port(u) == port(base)

    private fun port(u: Uri) = if (u.port == -1) 443 else u.port

    /** Only your server and sign-in addresses load in the app. Other links open in your normal browser. */
    private inner class Client : WebViewClient() {
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

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: android.webkit.WebResourceError) {
            if (request.isForMainFrame) Logs.w("Open WebUI", "Page didn't load: ${error.description} (${request.url.path})")
        }

        override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: android.webkit.WebResourceResponse) {
            if (request.isForMainFrame) Logs.w("Open WebUI", "Page answered HTTP ${response.statusCode} (${request.url.path})")
        }

        override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
            currentUrl.value = url ?: ""
            canGoBack.value = view.canGoBack()
            maybeSignIn(url)
        }

        override fun onPageFinished(view: WebView, url: String?) {
            canGoBack.value = view.canGoBack()
            if (!showMenus.value && shellScript == null && isServer(url?.let { Uri.parse(it) })) {
                view.evaluateJavascript(ADD_STYLE, null) // older web views without document-start scripts
            }
        }
    }

    /** Mic for your server only; no camera, no location. Plus the file picker for attachments. */
    private inner class Chrome : WebChromeClient() {
        override fun onPermissionRequest(req: PermissionRequest) {
            val mic = PermissionRequest.RESOURCE_AUDIO_CAPTURE in req.resources
            if (!mic || !isServer(req.origin)) {
                req.deny()
                return
            }
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                req.grant(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE))
            } else {
                pendingMic?.deny()
                pendingMic = req
                micPermission.launch(Manifest.permission.RECORD_AUDIO)
            }
        }

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

    override fun onStart() {
        super.onStart()
        if (::web.isInitialized) chatIdOf(currentUrl.value)?.let { ReplyWorker.cancel(this, it) }
    }

    override fun onResume() {
        super.onResume()
        chatStyle.value = Prefs.chatList(this) // chosen in Settings → Appearance
        if (::web.isInitialized) {
            web.onResume()
            // "Show Open WebUI's own menus" lives in Settings; pick up a change made there.
            val wanted = Prefs.showOpenWebUiMenus(this)
            if (wanted != showMenus.value) {
                showMenus.value = wanted
                applyShellStyle()
            }
        }
    }

    override fun onPause() {
        if (::web.isInitialized) {
            web.onPause()
            CookieManager.getInstance().flush()
        }
        super.onPause()
    }

    /** Leaving while Lena is still answering: watch for the end and notify. */
    override fun onStop() {
        super.onStop()
        if (!::web.isInitialized || !Prefs.notifyReplies(this)) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val chatId = chatIdOf(currentUrl.value) ?: return
        val appContext = applicationContext
        lifecycleScope.launch {
            if (runCatching { api.runningTasks(chatId) }.getOrDefault(0) > 0) ReplyWorker.enqueue(appContext, chatId)
        }
    }

    override fun onDestroy() {
        if (::web.isInitialized) web.destroy()
        super.onDestroy()
    }

    // ---- The native shell -------------------------------------------------------------------

    @Composable
    private fun Shell() {
        val scope = rememberCoroutineScope()
        val drawer = rememberDrawerState(DrawerValue.Closed)
        var models by remember { mutableStateOf<List<Model>>(emptyList()) }
        var chats by remember { mutableStateOf<List<ChatItem>?>(null) }
        var pinned by remember { mutableStateOf<List<ChatItem>>(emptyList()) }
        var allPinned by remember { mutableStateOf(false) }
        var chatsError by remember { mutableStateOf<String?>(null) }
        var query by remember { mutableStateOf("") }
        var version by remember { mutableStateOf<String?>(null) }
        var feelings by remember { mutableStateOf<Feelings?>(null) }
        var checked by remember { mutableStateOf(false) }
        var title by remember { mutableStateOf("Lena") }
        var titleIsLena by remember { mutableStateOf(false) }
        var modelMenu by remember { mutableStateOf(false) }
        var refresh by remember { mutableIntStateOf(0) }
        var renaming by remember { mutableStateOf<ChatItem?>(null) }
        var deleting by remember { mutableStateOf<ChatItem?>(null) }
        val url = currentUrl.value
        val chatId = chatIdOf(url)

        LaunchedEffect(Unit) {
            models = runCatching { api.models() }.getOrDefault(emptyList())
            if (models.isNotEmpty()) Prefs.rememberLena(this@MainActivity, models)
        }

        // Title: the current chat's model, or the model a new chat will use.
        LaunchedEffect(chatId, url, models) {
            val id = if (chatId != null) runCatching { api.chatModel(chatId) }.getOrNull()
            else Uri.parse(url).getQueryParameter("model") ?: Prefs.effectiveModel(this@MainActivity)
            title = models.firstOrNull { it.id == id }?.name ?: id ?: "Lena"
            titleIsLena = id != null && id == Prefs.lenaIn(models)?.id
        }

        // Fill the drawer when it opens, and as you search.
        LaunchedEffect(drawer.isOpen, query, refresh) {
            if (!drawer.isOpen) return@LaunchedEffect
            if (query.isNotBlank()) delay(300)
            version = api.version()
            checked = true
            feelings = HomeAssistant.from(this@MainActivity)?.let { runCatching { it.feelings() }.getOrNull() }
            try {
                chats = if (query.isBlank()) api.chats() else api.search(query.trim())
                pinned = if (query.isBlank()) runCatching { api.pinned() }.getOrDefault(pinned) else emptyList()
                chatsError = null
            } catch (_: AuthException) {
                chatsError = "Sign in to see your chats"
            } catch (_: Exception) {
                chatsError = if (version == null) null else "Couldn't load your chats"
            }
        }

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
                ModalDrawerSheet(Modifier.width(320.dp)) {
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

                        // Search, then your chats
                        SearchField(query, "Search chats", Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)) { query = it }
                        ChatList(
                            chats, chatsError, query.isNotBlank(), chatId, chatStyle.value, Modifier.weight(1f),
                            pinned = pinned,
                            pinnedLimit = if (allPinned) Int.MAX_VALUE else Prefs.pinnedShown(this@MainActivity),
                            onAllPinned = { allPinned = !allPinned },
                            onOpen = { id -> closeThen { openChat(id) } },
                            onPin = { c ->
                                scope.launch {
                                    if (runCatching { api.togglePin(c.id) }.isFailure) toast("Couldn't pin the chat")
                                    refresh++
                                }
                            },
                            onRename = { renaming = it },
                            onDelete = { deleting = it },
                        )

                        // Settings, memory and reload, side by side
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        Row(
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            DrawerIcon(Icons.Outlined.Settings, "Settings") {
                                closeThen { startActivity(Intent(this@MainActivity, SettingsActivity::class.java)) }
                            }
                            DrawerIcon(Icons.Outlined.Psychology, "Memory & knowledge") {
                                closeThen { startActivity(Intent(this@MainActivity, MemoryActivity::class.java)) }
                            }
                            DrawerIcon(Icons.Outlined.Refresh, "Reload") { closeThen { web.reload() } }
                        }
                    }
                }
            },
        ) {
            Scaffold(
                containerColor = pageBackground(),
                topBar = {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = pageBackground()),
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawer.open() } }) { Icon(Icons.Rounded.Menu, "Menu") }
                        },
                        title = {
                            Box {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clip(RoundedCornerShape(12.dp))
                                        .clickable(enabled = models.isNotEmpty()) { modelMenu = true }
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                ) {
                                    if (titleIsLena) LenaAvatar(26.dp, Modifier.padding(end = 10.dp))
                                    Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                                    if (models.isNotEmpty()) Icon(Icons.Rounded.ExpandMore, null)
                                }
                                DropdownMenu(expanded = modelMenu, onDismissRequest = { modelMenu = false }) {
                                    Text(
                                        "New chat with…",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    )
                                    models.forEach { m ->
                                        DropdownMenuItem(text = { Text(m.name) }, onClick = {
                                            modelMenu = false
                                            openNew(model = m.id)
                                        })
                                    }
                                }
                            }
                        },
                        actions = {
                            IconButton(onClick = { openNew(temporary = true) }) { Icon(Icons.Outlined.VisibilityOff, "Temporary chat") }
                            IconButton(onClick = { openNew() }) { Icon(Icons.Outlined.AddComment, "New chat") }
                            IconButton(onClick = { openNew(call = true) }) { Icon(Icons.Rounded.GraphicEq, "Voice call") }
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

        renaming?.let { c ->
            var newTitle by remember(c.id) { mutableStateOf(c.title) }
            AlertDialog(
                onDismissRequest = { renaming = null },
                title = { Text("Rename chat") },
                text = { OutlinedTextField(value = newTitle, onValueChange = { newTitle = it }, singleLine = true) },
                confirmButton = {
                    TextButton(enabled = newTitle.isNotBlank(), onClick = {
                        val t = newTitle.trim()
                        renaming = null
                        scope.launch {
                            if (runCatching { api.renameChat(c.id, t) }.isFailure) toast("Couldn't rename the chat")
                            refresh++
                        }
                    }) { Text("Rename") }
                },
                dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } },
            )
        }

        deleting?.let { c ->
            AlertDialog(
                onDismissRequest = { deleting = null },
                title = { Text("Delete chat?") },
                text = { Text("\u201C${c.title}\u201D will be deleted from Open WebUI too.") },
                confirmButton = {
                    TextButton(
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        onClick = {
                            deleting = null
                            scope.launch {
                                if (runCatching { api.deleteChat(c.id) }.isFailure) toast("Couldn't delete the chat")
                                else if (c.id == chatIdOf(currentUrl.value)) openNew()
                                refresh++
                            }
                        },
                    ) { Text("Delete") }
                },
                dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
            )
        }
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

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

    @Composable
    private fun ChatList(
        chats: List<ChatItem>?,
        error: String?,
        searching: Boolean,
        currentId: String?,
        style: ChatListStyle,
        modifier: Modifier,
        pinned: List<ChatItem>,
        pinnedLimit: Int,
        onAllPinned: () -> Unit,
        onOpen: (String) -> Unit,
        onPin: (ChatItem) -> Unit,
        onRename: (ChatItem) -> Unit,
        onDelete: (ChatItem) -> Unit,
    ) {
        val folded = remember { mutableStateMapOf<String, Boolean>() } // folding days: which are open
        val pinnedIds = pinned.map { it.id }.toSet()
        val side = when (style) {
            ChatListStyle.GROUPS, ChatListStyle.SLIM -> 12.dp
            ChatListStyle.TIMED -> 0.dp
            else -> 8.dp
        }
        LazyColumn(modifier, contentPadding = PaddingValues(start = side, end = side, top = 4.dp, bottom = 12.dp)) {
            when {
                error != null -> item { Hint(error) }
                chats == null -> item { Hint("Loading…") }
                chats.isEmpty() && (searching || pinned.isEmpty()) -> item { Hint(if (searching) "No matching chats" else "No chats yet") }
                else -> {
                    // Pinned chats first, as their own group: up to the limit, then "View all".
                    val shownPinned = pinned.take(pinnedLimit)
                    val groups = if (searching) listOf("Results" to chats)
                    else (if (shownPinned.isNotEmpty()) listOf(PINNED to shownPinned) else emptyList()) + group(chats)
                    groups.forEachIndexed { index, (label, list) ->
                        val row: @Composable (ChatItem, @Composable (ChatItem, () -> Unit) -> Unit) -> Unit = { c, look ->
                            WithChatMenu(c.id in pinnedIds, { onPin(c) }, { onRename(c) }, { onDelete(c) }) { longPress -> look(c, longPress) }
                        }
                        when (style) {
                            ChatListStyle.GROUPS -> item(key = "g-$label") {
                                // One rounded group per day, like the rest of the app.
                                Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    SectionTitle(label, Modifier.padding(start = 4.dp))
                                    Group {
                                        list.forEach { c ->
                                            row(c) { it, longPress ->
                                                Item(
                                                    icon = null, title = it.title, summary = null,
                                                    selected = it.id == currentId,
                                                    container = MaterialTheme.colorScheme.surfaceContainerHigh,
                                                    maxTitleLines = 1,
                                                    trailing = if (it.active) { { Spinner(Modifier.padding(end = 12.dp)) } } else null,
                                                    onLongClick = longPress,
                                                    onClick = { onOpen(it.id) },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            ChatListStyle.SLIM -> item(key = "s-$label") {
                                Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(start = 16.dp, top = 2.dp))
                                    Column(Modifier.clip(RoundedCornerShape(16.dp)), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                        list.forEach { c ->
                                            row(c) { it, longPress ->
                                                ChatLine(it, it.id == currentId, null, 44.dp, 16.dp, RectangleShape,
                                                    MaterialTheme.colorScheme.surfaceContainerHigh, { onOpen(it.id) }, longPress)
                                            }
                                        }
                                    }
                                }
                            }
                            ChatListStyle.FLAT, ChatListStyle.TIMED -> {
                                val timed = style == ChatListStyle.TIMED
                                item(key = "h-$label") {
                                    Text(
                                        label,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (timed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(start = if (timed) 20.dp else 12.dp, top = 14.dp, bottom = 4.dp),
                                    )
                                }
                                items(list, key = { it.id }) { c ->
                                    row(c) { it, longPress ->
                                        if (timed) ChatLine(it, it.id == currentId, whenLabel(it.updatedMillis), 44.dp, 20.dp, RectangleShape,
                                            Color.Transparent, { onOpen(it.id) }, longPress)
                                        else ChatLine(it, it.id == currentId, null, 40.dp, 12.dp, RoundedCornerShape(20.dp),
                                            Color.Transparent, { onOpen(it.id) }, longPress)
                                    }
                                }
                            }
                            ChatListStyle.TIMELINE -> item(key = "t-$label") {
                                val line = MaterialTheme.colorScheme.outlineVariant
                                val dot = if (index == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                val filled = index == 0
                                Column(
                                    Modifier.fillMaxWidth()
                                        .drawBehind {
                                            val x = 14.dp.toPx()
                                            val y = 12.dp.toPx()
                                            val r = 5.dp.toPx()
                                            drawLine(line, Offset(x, y + r + 4.dp.toPx()), Offset(x, size.height), strokeWidth = 2.dp.toPx())
                                            if (filled) drawCircle(dot, r, Offset(x, y))
                                            else drawCircle(dot, r - 1.dp.toPx(), Offset(x, y), style = Stroke(2.dp.toPx()))
                                        }
                                        .padding(start = 28.dp, top = 4.dp, bottom = 8.dp),
                                ) {
                                    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(start = 10.dp, bottom = 4.dp))
                                    list.forEach { c ->
                                        row(c) { it, longPress ->
                                            ChatLine(it, it.id == currentId, null, 36.dp, 10.dp, RoundedCornerShape(18.dp),
                                                Color.Transparent, { onOpen(it.id) }, longPress)
                                        }
                                    }
                                }
                            }
                            ChatListStyle.FOLDING -> {
                                val open = folded[label] ?: (searching || label == PINNED || label == "Today" || label == "Yesterday")
                                item(key = "f-$label") { FoldHeader(label, if (label == PINNED) pinned.size else list.size, open) { folded[label] = !open } }
                                if (open) items(list, key = { it.id }) { c ->
                                    row(c) { it, longPress ->
                                        ChatLine(it, it.id == currentId, null, 40.dp, 24.dp, RoundedCornerShape(20.dp),
                                            Color.Transparent, { onOpen(it.id) }, longPress)
                                    }
                                }
                            }
                        }
                        if (label == PINNED && (pinned.size > pinnedLimit || pinnedLimit == Int.MAX_VALUE && pinned.size > Prefs.pinnedShown(this@MainActivity))) {
                            item(key = "pinned-more") {
                                val all = pinnedLimit == Int.MAX_VALUE
                                TextButton(onClick = onAllPinned, modifier = Modifier.padding(start = 4.dp)) {
                                    Text(if (all) "Show fewer" else "View all ${pinned.size} pinned")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /** Long-press any chat to pin, rename or delete it. */
    @Composable
    private fun WithChatMenu(
        pinned: Boolean, onPin: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit,
        content: @Composable (longPress: () -> Unit) -> Unit,
    ) {
        var menu by remember { mutableStateOf(false) }
        val haptics = LocalHapticFeedback.current
        Box {
            content {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                menu = true
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(if (pinned) "Unpin" else "Pin") },
                    leadingIcon = { Icon(Icons.Outlined.PushPin, null) },
                    onClick = { menu = false; onPin() },
                )
                DropdownMenuItem(
                    text = { Text("Rename") },
                    leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                    onClick = { menu = false; onRename() },
                )
                DropdownMenuItem(
                    text = { Text("Delete") },
                    leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                    onClick = { menu = false; onDelete() },
                )
            }
        }
    }

    /** A one-line chat row: the slim, flat, timed, timeline and folding lists. */
    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun ChatLine(
        c: ChatItem, selected: Boolean, time: String?, height: Dp, start: Dp, shape: Shape, idle: Color,
        onOpen: () -> Unit, onLongClick: () -> Unit,
    ) {
        val cs = MaterialTheme.colorScheme
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().height(height).clip(shape)
                .background(if (selected) cs.secondaryContainer else idle)
                .combinedClickable(onClick = onOpen, onLongClick = onLongClick)
                .padding(start = start, end = if (time != null) 20.dp else 12.dp),
        ) {
            Text(
                c.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                color = if (selected) cs.onSecondaryContainer else cs.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            when {
                c.active -> Spinner(Modifier.padding(start = 12.dp))
                time != null -> Text(time, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp))
            }
        }
    }

    /** A day in the folding list: tap to open or close it. */
    @Composable
    private fun FoldHeader(label: String, count: Int, open: Boolean, onToggle: () -> Unit) {
        val turn by animateFloatAsState(if (open) 180f else 0f, label = "fold")
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(22.dp))
                .clickable(onClickLabel = if (open) "Close $label" else "Open $label", onClick = onToggle)
                .padding(horizontal = 12.dp),
        ) {
            Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(
                count.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
            Icon(Icons.Rounded.ExpandMore, null, Modifier.rotate(turn), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    @Composable
    private fun Spinner(modifier: Modifier = Modifier) {
        CircularProgressIndicator(modifier.size(14.dp), strokeWidth = 2.dp)
    }

    /** When a chat was last used: the time today and yesterday, the weekday this week, else the date. */
    private fun whenLabel(t: Long): String {
        val day = 24 * 60 * 60 * 1000L
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val date = Date(t)
        val pattern = { skeleton: String -> SimpleDateFormat(DateFormat.getBestDateTimePattern(Locale.getDefault(), skeleton), Locale.getDefault()) }
        return when {
            t >= today - day -> DateFormat.getTimeFormat(this).format(date)
            t >= today - 6 * day -> pattern("EEE").format(date)
            Calendar.getInstance().apply { timeInMillis = t }.get(Calendar.YEAR) == Calendar.getInstance().get(Calendar.YEAR) ->
                pattern("MMMd").format(date)
            else -> pattern("yMMMd").format(date)
        }
    }

    @Composable
    private fun Hint(text: String) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }

    private fun group(chats: List<ChatItem>): List<Pair<String, List<ChatItem>>> {
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val day = 24 * 60 * 60 * 1000L
        val label = { t: Long ->
            when {
                t >= today -> "Today"
                t >= today - day -> "Yesterday"
                t >= today - 7 * day -> "Previous 7 days"
                t >= today - 30 * day -> "Previous 30 days"
                else -> "Older"
            }
        }
        return chats.groupBy { label(it.updatedMillis) }.toList()
    }
}
