@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.soullink9.lena

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager

import android.net.Uri
import android.os.Bundle
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
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddComment
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar

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
        api = Api(url)
        Prefs.signIn(this).split(" ").filter { it.isNotBlank() }.forEach { signIn += Uri.parse(it) }
        showMenus.value = Prefs.showOpenWebUiMenus(this)

        enableEdgeToEdge()
        web = createWebView()
        applyShellStyle()
        setContent { LenaTheme { Shell() } }
        // Switching dark mode rebuilds the screen so the page re-themes; come back to the same chat.
        val restored = savedInstanceState?.getString("url")?.takeIf { isServer(Uri.parse(it)) }
        if (restored != null) web.loadUrl(restored) else handle(intent, fresh = true)
        askForNotificationsOnce()
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
        (model ?: Prefs.model(this))?.let { b.appendQueryParameter("model", it) }
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

        override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
            currentUrl.value = url ?: ""
            canGoBack.value = view.canGoBack()
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

    // ---- Lifecycle --------------------------------------------------------------------------

    override fun onStart() {
        super.onStart()
        if (::web.isInitialized) chatIdOf(currentUrl.value)?.let { ReplyWorker.cancel(this, it) }
    }

    override fun onResume() {
        super.onResume()
        if (::web.isInitialized) web.onResume()
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
        var chatsError by remember { mutableStateOf<String?>(null) }
        var query by remember { mutableStateOf("") }
        var version by remember { mutableStateOf<String?>(null) }
        var checked by remember { mutableStateOf(false) }
        var title by remember { mutableStateOf("Lena") }
        var modelMenu by remember { mutableStateOf(false) }
        val url = currentUrl.value
        val chatId = chatIdOf(url)

        LaunchedEffect(Unit) {
            models = runCatching { api.models() }.getOrDefault(emptyList())
        }

        // Title: the current chat's model, or the model a new chat will use.
        LaunchedEffect(chatId, url, models) {
            val id = if (chatId != null) runCatching { api.chatModel(chatId) }.getOrNull()
            else Uri.parse(url).getQueryParameter("model") ?: Prefs.model(this@MainActivity)
            title = models.firstOrNull { it.id == id }?.name ?: id ?: "Lena"
        }

        // Fill the drawer when it opens, and as you search.
        LaunchedEffect(drawer.isOpen, query) {
            if (!drawer.isOpen) return@LaunchedEffect
            if (query.isNotBlank()) delay(300)
            version = api.version()
            checked = true
            try {
                chats = if (query.isBlank()) api.chats() else api.search(query.trim())
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
                        Text(
                            "Lena",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(start = 28.dp, top = 20.dp, end = 16.dp),
                        )
                        Status(checked, version)
                        Spacer(Modifier.height(8.dp))
                        DrawerItem("New chat", Icons.Outlined.AddComment) { closeThen { openNew() } }
                        DrawerItem("Temporary chat", Icons.Outlined.VisibilityOff) { closeThen { openNew(temporary = true) } }
                        DrawerItem("Voice call", Icons.Rounded.GraphicEq) { closeThen { openNew(call = true) } }
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text("Search chats") },
                            leadingIcon = { Icon(Icons.Outlined.Search, null) },
                            trailingIcon = {
                                if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, "Clear") }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(28.dp),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                        ChatList(chats, chatsError, query.isNotBlank(), chatId, Modifier.weight(1f)) { id ->
                            closeThen { openChat(id) }
                        }
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                        NavigationDrawerItem(
                            label = { Text("Open WebUI menus") },
                            icon = { Icon(Icons.Outlined.Tune, null) },
                            badge = { Switch(checked = showMenus.value, onCheckedChange = null) },
                            selected = false,
                            onClick = {
                                showMenus.value = !showMenus.value
                                Prefs.setShowOpenWebUiMenus(this@MainActivity, showMenus.value)
                                applyShellStyle()
                            },
                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                        )
                        DrawerItem("Reload", Icons.Outlined.Refresh) { closeThen { web.reload() } }
                        DrawerItem("Settings", Icons.Outlined.Settings) {
                            closeThen { startActivity(Intent(this@MainActivity, SettingsActivity::class.java)) }
                        }
                        Spacer(Modifier.height(12.dp))
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
                            IconButton(onClick = { openNew() }) { Icon(Icons.Outlined.AddComment, "New chat") }
                            IconButton(onClick = { openNew(call = true) }) { Icon(Icons.Rounded.GraphicEq, "Voice call") }
                        },
                    )
                },
            ) { padding ->
                AndroidView(
                    factory = { web },
                    modifier = Modifier.padding(padding).consumeWindowInsets(padding).imePadding().fillMaxSize(),
                )
            }
        }
    }

    @Composable
    private fun DrawerItem(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
        NavigationDrawerItem(
            label = { Text(label) },
            icon = { Icon(icon, null) },
            selected = false,
            onClick = onClick,
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
        )
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
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(start = 28.dp, top = 4.dp, end = 16.dp),
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    @Composable
    private fun ChatList(
        chats: List<ChatItem>?,
        error: String?,
        searching: Boolean,
        currentId: String?,
        modifier: Modifier,
        onOpen: (String) -> Unit,
    ) {
        LazyColumn(modifier) {
            when {
                error != null -> item { Hint(error) }
                chats == null -> item { Hint("Loading…") }
                chats.isEmpty() -> item { Hint(if (searching) "No matching chats" else "No chats yet") }
                else -> {
                    val groups = if (searching) listOf("Results" to chats) else group(chats)
                    groups.forEach { (label, list) ->
                        item(key = "h-$label") {
                            Text(
                                label,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 28.dp, top = 12.dp, bottom = 4.dp),
                            )
                        }
                        items(list, key = { it.id }) { c ->
                            NavigationDrawerItem(
                                label = { Text(c.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                selected = c.id == currentId,
                                onClick = { onOpen(c.id) },
                                badge = {
                                    if (c.active) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                                },
                                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding).height(48.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun Hint(text: String) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp),
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
