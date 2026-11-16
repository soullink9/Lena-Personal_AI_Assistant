package io.github.soullink9.lena

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection

/**
 * Camera mode's live picture: one camera at a time. Everything comes from Home Assistant, never from the
 * camera itself, so it works with the cameras on a network only Home Assistant can reach. Three ways, each
 * tried when the one before doesn't give a picture ([Way]):
 *
 * 1. **WebRTC**, in a web view showing a small page from the app's assets (assets/camera/live.html): this
 *    asks Home Assistant for its ICE servers, passes the page's offer and candidates over the app's own
 *    connection ([HaSocket]) and hands back the answer, so the page never holds the token.
 * 2. **HLS**, Home Assistant's stream address played by the same page.
 * 3. **Pictures**: Home Assistant's camera picture stream (MJPEG, [PictureStream]), read by the app with its
 *    token and shown picture by picture over the still picture ([onPicture]). Fewer pictures a second, but
 *    it works wherever still pictures do.
 *
 * Only a picture actually drawn counts as live: the page says [Bridge.playing] once a frame is on screen, not
 * when the stream merely opens (a WebRTC or HLS stream can open and never draw a frame, which used to show
 * Live over a black screen). WebRTC and HLS each get [WEBRTC_MS] (HLS twice that, it starts slower) to draw one.
 *
 * To start sooner: each camera's ICE servers are asked for once per player ([iceConfigs]), not on every
 * switch, and a camera remembers the way that worked ([startWith]), so Rotate doesn't wait through the ways
 * that failed on every turn.
 *
 * Only the camera in the spotlight plays, and only while camera mode is on screen ([show], [stop]).
 */
class CameraLive(
    context: Context,
    private val socket: HaSocket,
    private val ha: HomeAssistant,
    private val base: String,
    private val sound: Boolean,
    private val scope: CoroutineScope,
    private val onState: (State) -> Unit,
    /** A new picture for [camera] (its stream entity) from the picture stream, to show in its spotlight. */
    private val onPicture: (camera: String, picture: Bitmap) -> Unit,
    /** Android ended the web view's page (it crashed, or memory ran short): this player is done; make another. */
    private val onGone: () -> Unit,
) {
    /**
     * Where the live picture is: starting, moving in the web view, moving picture by picture over the still
     * picture ([Way.PICTURES]), or given up (camera mode shows still pictures then).
     */
    enum class State { CONNECTING, PLAYING, PICTURES, STILL }

    /** The ways to a live picture, in the order they're tried. */
    enum class Way { WEBRTC, HLS, PICTURES }

    companion object {
        private const val TAG = "Cameras"
        private const val WEBRTC_MS = 12_000L
        private const val PICTURES_MS = 15_000L
        private const val PAGE = "https://appassets.androidplatform.net/assets/camera/live.html"
    }

    private var entity: String? = null
    private var subscription: Int? = null
    private var session: String? = null
    private val pending = mutableListOf<JSONObject>()
    /** The way being tried for the camera now. */
    private var way = Way.WEBRTC
    private var timeout: Job? = null
    private var pictures: Job? = null

    /** The picture stream's open connection, so stopping can end a read that waits on the network. */
    @Volatile private var picturesConnection: HttpURLConnection? = null
    private var pageReady = false
    private var startWhenReady: (() -> Unit)? = null

    /** Each camera's WebRTC client configuration (its ICE servers), asked for the first time it's shown. */
    private val iceConfigs = HashMap<String, JSONObject>()

    /** The way to start each camera with on this player: past the ways that didn't give it a picture. */
    private val startWith = HashMap<String, Way>()

    /** The page was ended by Android: nothing more may be asked of the web view but to be destroyed. */
    private var gone = false

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

            /**
             * Android ended the page (it crashed, or memory ran short). Unhandled, that would end the whole
             * app; instead this player stops and camera mode makes a new one ([onGone]).
             */
            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                Logs.w(TAG, if (detail.didCrash()) "The live picture's page crashed; starting it again" else "Android closed the live picture's page to free memory; starting it again")
                gone = true
                scope.launch { onGone() }
                return true
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
        when (startWith[camera]) {
            Way.HLS -> playHls(mine, "WebRTC didn't give $camera a picture last time")
            Way.PICTURES -> playPictures(mine, "WebRTC and HLS didn't give $camera a picture last time")
            else -> playWebRtc(mine, camera)
        }
    }

    /** Starts WebRTC in the page; [giveUp] moves on to HLS if no picture is drawn within [WEBRTC_MS]. */
    private fun playWebRtc(mine: Int, camera: String) {
        way = Way.WEBRTC
        scope.launch {
            try {
                val config = iceConfig(camera)
                if (mine != generation) return@launch
                val start = { js("lenaStart(${config}, $sound)") }
                if (pageReady) start() else startWhenReady = start
                timeout = scope.launch {
                    delay(WEBRTC_MS)
                    if (mine == generation) giveUp(mine, "WebRTC didn't draw a picture")
                }
            } catch (e: Exception) {
                if (mine == generation) giveUp(mine, "WebRTC isn't offered for $camera: ${e.message}")
            }
        }
    }

    /** [camera]'s WebRTC client configuration: asked of Home Assistant the first time, then remembered. */
    private suspend fun iceConfig(camera: String): JSONObject {
        iceConfigs[camera]?.let { return it }
        val reply = socket.call(JSONObject().put("type", "camera/webrtc/get_client_config").put("entity_id", camera))
        val config = reply.optJSONObject("result")?.optJSONObject("configuration") ?: JSONObject()
        iceConfigs[camera] = config
        return config
    }

    /** Stops whatever plays. */
    fun stop() {
        generation++
        timeout?.cancel()
        stopPictures()
        subscription?.let { socket.unsubscribe(it) }
        subscription = null
        session = null
        pending.clear()
        way = Way.WEBRTC
        entity = null
        startWhenReady = null
        if (pageReady) js("lenaStop()")
    }

    fun destroy() {
        stop()
        (view.parent as? ViewGroup)?.removeView(view)
        view.destroy()
    }

    private fun js(code: String) {
        if (gone) return
        view.post { if (!gone) view.evaluateJavascript(code, null) }
    }

    /**
     * The way being tried didn't give a picture: the next one (WebRTC, then HLS, then the picture stream), and
     * still pictures after the last. A way that failed once for a camera (a stream WebRTC can't carry, such as
     * H.265; HLS this web view can't play) fails every time, so the camera starts past it from now on.
     */
    private fun giveUp(mine: Int, why: String) {
        if (mine != generation) return
        val camera = entity ?: return
        timeout?.cancel()
        subscription?.let { socket.unsubscribe(it) }
        subscription = null
        when (way) {
            Way.WEBRTC -> {
                startWith[camera] = Way.HLS
                playHls(mine, why)
            }
            Way.HLS -> {
                startWith[camera] = Way.PICTURES
                playPictures(mine, why)
            }
            Way.PICTURES -> {
                stopPictures()
                Logs.w(TAG, "No live picture for $camera ($why); showing still pictures")
                onState(State.STILL)
            }
        }
    }

    /** Asks Home Assistant for the camera's HLS stream and plays it in the page. */
    private fun playHls(mine: Int, why: String) {
        val camera = entity ?: return
        way = Way.HLS
        Logs.i(TAG, "$why; trying HLS for $camera")
        scope.launch {
            try {
                val url = socket.call(JSONObject().put("type", "camera/stream").put("entity_id", camera).put("format", "hls"))
                    .optJSONObject("result")?.optString("url").orEmpty()
                if (mine != generation) return@launch
                if (url.isBlank()) throw IllegalStateException("no stream address")
                val start = { js("lenaHls(${JSONObject.quote(base.trimEnd('/') + url)}, $sound)") }
                if (pageReady) start() else startWhenReady = start
                timeout = scope.launch {
                    delay(WEBRTC_MS * 2)
                    if (mine == generation) giveUp(mine, "HLS didn't draw a picture")
                }
            } catch (e: Exception) {
                if (mine == generation) giveUp(mine, "HLS isn't offered: ${e.message}")
            }
        }
    }

    /**
     * Reads Home Assistant's picture stream for the camera and hands each picture to [onPicture], scaled down
     * to the screen's width as it's decoded (a TV box needn't hold a 4K picture). The page is stopped first, so
     * nothing of WebRTC or HLS is left running. Still pictures if no picture comes within [PICTURES_MS], or the
     * stream ends.
     */
    private fun playPictures(mine: Int, why: String) {
        val camera = entity ?: return
        way = Way.PICTURES
        Logs.i(TAG, "$why; trying Home Assistant's picture stream for $camera")
        if (pageReady) js("lenaStop()") else startWhenReady = null
        val width = view.resources.displayMetrics.widthPixels
        timeout = scope.launch {
            delay(PICTURES_MS)
            if (mine == generation) giveUp(mine, "the picture stream sent no picture")
        }
        pictures = scope.launch {
            val ended = try {
                readPictures(mine, camera, width)
                "the picture stream ended"
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                "the picture stream isn't offered: ${e.message}"
            }
            if (mine == generation) giveUp(mine, ended)
        }
    }

    /**
     * Ends the picture stream: cancels its reading and disconnects it (off the main thread, as closing a
     * connection may write to the network), since a read waiting on the network doesn't notice cancelling.
     */
    private fun stopPictures() {
        pictures?.cancel()
        pictures = null
        // A thread of its own, not [scope]: when camera mode closes, its scope is already cancelled.
        picturesConnection?.let { conn -> Thread { conn.disconnect() }.start() }
        picturesConnection = null
    }

    /** The picture stream's reading, off the main thread; returns when the stream ends. */
    private suspend fun readPictures(mine: Int, camera: String, width: Int) {
        withContext(Dispatchers.IO) {
            val conn = ha.openStream("/api/camera_proxy_stream/$camera")
            picturesConnection = conn
            // Stopped while it was connecting: nothing will disconnect it but this.
            if (!isActive) {
                conn.disconnect()
                return@withContext
            }
            try {
                val stream = PictureStream(conn.inputStream.buffered())
                var first = true
                while (true) {
                    ensureActive()
                    val jpeg = stream.next() ?: return@withContext
                    val picture = decode(jpeg, width) ?: continue
                    withContext(Dispatchers.Main) {
                        if (mine == generation) {
                            onPicture(camera, picture)
                            if (first) {
                                timeout?.cancel()
                                onState(State.PICTURES)
                            }
                        }
                    }
                    first = false
                }
            } finally {
                conn.disconnect()
            }
        }
    }

    /** A JPEG decoded at the smallest size still at least [width] wide (halving, as BitmapFactory does). */
    private fun decode(jpeg: ByteArray, width: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= width) sample *= 2
        return BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, BitmapFactory.Options().apply { inSampleSize = sample })
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
            scope.launch { if (way != Way.PICTURES) giveUp(generation, why) }
        }

        /** The page drew its first picture (not merely opened the stream). */
        @JavascriptInterface
        fun playing() {
            scope.launch {
                if (entity == null || way == Way.PICTURES) return@launch
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
