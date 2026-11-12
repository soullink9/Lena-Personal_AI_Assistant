package io.github.soullink9.lena

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Camera mode's live picture: one camera at a time, in a web view showing a small page from the app's
 * assets (assets/camera/live.html). Home Assistant streams it with WebRTC: this asks Home Assistant for its
 * ICE servers, passes the page's offer and candidates over the app's own connection ([HaSocket]) and hands
 * back the answer, so the page never holds the token. If WebRTC doesn't start within [WEBRTC_MS], it asks
 * for Home Assistant's HLS stream instead; if that fails too, camera mode keeps showing still pictures.
 *
 * Only the camera in the spotlight plays, and only while camera mode is on screen ([show], [stop]).
 */
class CameraLive(
    context: Context,
    private val socket: HaSocket,
    private val base: String,
    private val sound: Boolean,
    private val scope: CoroutineScope,
    private val onState: (State) -> Unit,
) {
    /** Where the live picture is: starting, moving, or given up (camera mode shows still pictures then). */
    enum class State { CONNECTING, PLAYING, STILL }

    companion object {
        private const val TAG = "Cameras"
        private const val WEBRTC_MS = 12_000L
        private const val PAGE = "https://appassets.androidplatform.net/assets/camera/live.html"
    }

    private var entity: String? = null
    private var subscription: Int? = null
    private var session: String? = null
    private val pending = mutableListOf<JSONObject>()
    private var triedHls = false
    private var timeout: Job? = null
    private var pageReady = false
    private var startWhenReady: (() -> Unit)? = null

    /** Each camera shown gets a new number, so a late answer for the last one is ignored. */
    private var generation = 0

    @SuppressLint("SetJavaScriptEnabled")
    val view: WebView = WebView(context).apply {
        setBackgroundColor(Color.TRANSPARENT)
        settings.javaScriptEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        isFocusable = false // the remote stays with camera mode's own buttons
        val assets = WebViewAssetLoader.Builder().addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context)).build()
        webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                assets.shouldInterceptRequest(request.url)

            // The page is the app's own and goes nowhere else.
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true

            override fun onPageFinished(view: WebView, url: String) {
                pageReady = true
                startWhenReady?.invoke()
                startWhenReady = null
            }
        }
        addJavascriptInterface(Bridge(), "LenaCamera")
        loadUrl(PAGE)
    }

    /** Plays [camera] (a camera entity), or stops when null. */
    fun show(camera: String?) {
        if (camera == entity) return
        stop()
        entity = camera ?: return
        val mine = ++generation
        onState(State.CONNECTING)
        scope.launch {
            try {
                val config = socket.call(JSONObject().put("type", "camera/webrtc/get_client_config").put("entity_id", camera))
                    .optJSONObject("result")?.optJSONObject("configuration") ?: JSONObject()
                if (mine != generation) return@launch
                val start = { js("lenaStart(${config}, $sound)") }
                if (pageReady) start() else startWhenReady = start
                timeout = scope.launch {
                    delay(WEBRTC_MS)
                    if (mine == generation) giveUp(mine, "WebRTC didn't start")
                }
            } catch (e: Exception) {
                if (mine == generation) giveUp(mine, "WebRTC isn't offered for $camera: ${e.message}")
            }
        }
    }

    /** Stops whatever plays. */
    fun stop() {
        generation++
        timeout?.cancel()
        subscription?.let { socket.unsubscribe(it) }
        subscription = null
        session = null
        pending.clear()
        triedHls = false
        entity = null
        startWhenReady = null
        if (pageReady) js("lenaStop()")
    }

    fun destroy() {
        stop()
        view.destroy()
    }

    private fun js(code: String) {
        view.post { view.evaluateJavascript(code, null) }
    }

    /** WebRTC didn't work: HLS next, then still pictures. */
    private fun giveUp(mine: Int, why: String) {
        val camera = entity ?: return
        timeout?.cancel()
        subscription?.let { socket.unsubscribe(it) }
        subscription = null
        if (triedHls) {
            Logs.w(TAG, "No live picture for $camera ($why); showing still pictures")
            onState(State.STILL)
            return
        }
        triedHls = true
        Logs.i(TAG, "$why; trying HLS for $camera")
        scope.launch {
            try {
                val url = socket.call(JSONObject().put("type", "camera/stream").put("entity_id", camera).put("format", "hls"))
                    .optJSONObject("result")?.optString("url").orEmpty()
                if (mine != generation) return@launch
                if (url.isBlank()) throw IllegalStateException("no stream address")
                js("lenaHls(${JSONObject.quote(base.trimEnd('/') + url)}, $sound)")
                timeout = scope.launch {
                    delay(WEBRTC_MS * 2)
                    if (mine == generation) giveUp(mine, "HLS didn't start")
                }
            } catch (e: Exception) {
                if (mine == generation) giveUp(mine, "HLS isn't offered: ${e.message}")
            }
        }
    }

    /** The page's side; called on the web view's own thread, so everything goes to [scope] (the main thread). */
    private inner class Bridge {
        @JavascriptInterface
        fun offer(sdp: String) {
            scope.launch {
                val camera = entity ?: return@launch
                val mine = generation
                try {
                    subscription = socket.subscribe(
                        JSONObject().put("type", "camera/webrtc/offer").put("entity_id", camera).put("offer", sdp)
                    ) { event -> scope.launch { if (mine == generation) answer(mine, camera, event) } }
                } catch (e: Exception) {
                    if (mine == generation) giveUp(mine, "Home Assistant refused the offer: ${e.message}")
                }
            }
        }

        @JavascriptInterface
        fun candidate(json: String) {
            scope.launch {
                val candidate = runCatching { JSONObject(json) }.getOrNull() ?: return@launch
                val camera = entity ?: return@launch
                val id = session
                if (id == null) pending += candidate else sendCandidate(camera, id, candidate)
            }
        }

        @JavascriptInterface
        fun failed(why: String) {
            scope.launch { giveUp(generation, why) }
        }

        @JavascriptInterface
        fun playing() {
            scope.launch {
                timeout?.cancel()
                onState(State.PLAYING)
            }
        }
    }

    /** One of Home Assistant's messages about the offer: its session, its answer, its candidates, or an error. */
    private fun answer(mine: Int, camera: String, event: JSONObject) {
        when (event.optString("type")) {
            "session" -> {
                val id = event.optString("session_id")
                session = id
                pending.forEach { sendCandidate(camera, id, it) }
                pending.clear()
            }
            "answer" -> js("lenaAnswer(${JSONObject.quote(event.optString("answer"))})")
            "candidate" -> event.optJSONObject("candidate")?.let { js("lenaCandidate($it)") }
            "error" -> giveUp(mine, "Home Assistant: ${event.optString("message")}")
        }
    }

    private fun sendCandidate(camera: String, session: String, candidate: JSONObject) {
        socket.send(JSONObject().put("type", "camera/webrtc/candidate").put("entity_id", camera)
            .put("session_id", session).put("candidate", candidate))
    }
}
