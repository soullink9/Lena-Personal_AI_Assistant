package io.github.soullink9.lena

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
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
import java.io.ByteArrayInputStream
import java.io.FilterInputStream
import java.net.HttpURLConnection

/**
 * Camera mode's live picture: one camera at a time. Everything comes from Home Assistant, never from the
 * camera itself, so it works with the cameras on a network only Home Assistant can reach. Three ways, each
 * tried when the one before doesn't give a picture ([Way]):
 *
 * 1. **WebRTC**, in a web view showing a small page from the app's assets (assets/camera/live.html): this
 *    asks Home Assistant for its ICE servers, passes the page's offer and candidates over the app's own
 *    connection ([HaSocket]) and hands back the answer, so the page never holds the token.
 * 2. **HLS**, Home Assistant's stream played by the same page with its own small player (Media Source
 *    Extensions), every request going through the app ([hlsThroughApp]) to Home Assistant's own address. So it
 *    works with only that address reachable (Home Assistant behind a reverse proxy such as Traefik), and on
 *    web views that can't play HLS by themselves. A web view without Media Source Extensions for the stream's
 *    codecs is given the stream's address to try by itself.
 * 3. **Pictures**: Home Assistant's camera picture stream (MJPEG, [PictureStream]), read by the app with its
 *    token and shown picture by picture over the still picture ([onPicture]). Fewer pictures a second, but
 *    it works wherever still pictures do.
 *
 * Only a picture actually drawn counts as live: the page says [Bridge.playing] once a frame is on screen, not
 * when the stream merely opens (a WebRTC or HLS stream can open and never draw a frame, which used to show
 * Live over a black screen). WebRTC gets [WEBRTC_MS] to draw one, HLS [HLS_MS]: Home Assistant answers the
 * first HLS request only once its stream from the camera has two segments ready, which takes a while.
 *
 * To start sooner: each camera's ICE servers are asked for once per player ([iceConfigs]), not on every
 * switch, and a camera starts at the way that last drew a picture for it, on this device ([Cameras.liveWay]),
 * so neither Rotate nor opening camera mode again waits through the ways that failed (behind a reverse proxy,
 * WebRTC's video can't reach the device at all). Only a way that drew a picture is remembered: one that fails
 * is never a reason to skip the others. When the remembered way fails, the others are tried after it, WebRTC
 * included, and when none gives a picture the camera is forgotten, so the next time starts from the top.
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
        private const val HLS_MS = 45_000L
        private const val PICTURES_MS = 15_000L
        private const val ASSETS = "appassets.androidplatform.net"
        private const val PAGE = "https://$ASSETS/assets/camera/live.html"

        /** The page asks for Home Assistant's HLS files here; [hlsThroughApp] fetches them from Home Assistant. */
        private const val HLS_THROUGH_APP = "/ha/api/hls/"
    }

    private var entity: String? = null
    private var subscription: Int? = null
    private var session: String? = null
    private val pending = mutableListOf<JSONObject>()
    /** The way being tried for the camera now. */
    private var way = Way.WEBRTC

    /** The ways still to try for the camera now, after [way], in order. */
    private val waysLeft = ArrayDeque<Way>()
    private var timeout: Job? = null
    private var pictures: Job? = null

    /** The picture stream's open connection, so stopping can end a read that waits on the network. */
    @Volatile private var picturesConnection: HttpURLConnection? = null
    private var pageReady = false
    private var startWhenReady: (() -> Unit)? = null

    /** Each camera's WebRTC client configuration (its ICE servers), asked for the first time it's shown. */
    private val iceConfigs = HashMap<String, JSONObject>()

    private val appContext = context.applicationContext

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
            // On a web view thread of its own, so fetching from Home Assistant here holds nothing else up.
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                val url = request.url
                if (url.host == ASSETS && url.encodedPath.orEmpty().startsWith(HLS_THROUGH_APP)) return hlsThroughApp(url)
                return assets.shouldInterceptRequest(url)
            }

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
        // The way that last drew a picture first, then the rest in their usual order.
        val first = Cameras.liveWay(appContext, camera) ?: Way.WEBRTC
        waysLeft.clear()
        waysLeft.addAll(Way.entries.filter { it != first })
        play(mine, first, if (first == Way.WEBRTC) "Starting" else "$first drew $camera's picture last time")
    }

    /** Tries [next] for the camera now. */
    private fun play(mine: Int, next: Way, why: String) {
        val camera = entity ?: return
        when (next) {
            Way.WEBRTC -> playWebRtc(mine, camera)
            Way.HLS -> playHls(mine, why)
            Way.PICTURES -> playPictures(mine, why)
        }
    }

    /**
     * The way being tried drew a picture: the camera starts with it from now on, on this device. Saved only
     * when it's a different way, so the day counts from when it first worked: then the camera tries the ways
     * before it again, and a camera that fell back to the picture stream doesn't stay on it for good.
     */
    private fun worked() {
        val camera = entity ?: return
        if (Cameras.liveWay(appContext, camera) != way) Cameras.setLiveWay(appContext, camera, way)
    }

    /** Starts WebRTC in the page; [giveUp] moves on to the next way if no picture is drawn within [WEBRTC_MS]. */
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
        waysLeft.clear()
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
     * The way being tried didn't give a picture: the next one left ([waysLeft]), and still pictures after the
     * last. Each failure is logged with its reason, so a camera that shows no live picture can be traced in
     * the app's log. When none worked, the camera's remembered way is forgotten: a way that worked once and
     * then failed (the camera was offline, Home Assistant restarted) mustn't keep the camera from the others.
     */
    private fun giveUp(mine: Int, why: String) {
        if (mine != generation) return
        val camera = entity ?: return
        timeout?.cancel()
        subscription?.let { socket.unsubscribe(it) }
        subscription = null
        Logs.w(TAG, "$way gave $camera no picture: $why")
        if (way == Way.PICTURES) stopPictures()
        val next = waysLeft.removeFirstOrNull()
        if (next != null) {
            play(mine, next, why)
            return
        }
        Cameras.forgetLiveWay(appContext, camera)
        if (pageReady) js("lenaStop()")
        Logs.w(TAG, "No live picture for $camera; showing still pictures")
        onState(State.STILL)
    }

    /**
     * Asks Home Assistant for the camera's HLS stream and plays it in the page: its player fetches the files
     * through the app ([HLS_THROUGH_APP]); the direct address is for a web view that can only play it itself.
     */
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
                if (!url.startsWith("/api/hls/")) throw IllegalStateException("an unexpected stream address")
                val throughApp = JSONObject.quote("https://$ASSETS/ha$url")
                val direct = JSONObject.quote(base.trimEnd('/') + url)
                val start = { js("lenaHls($throughApp, $direct, $sound)") }
                if (pageReady) start() else startWhenReady = start
                timeout = scope.launch {
                    delay(HLS_MS)
                    if (mine == generation) giveUp(mine, "HLS didn't draw a picture within ${HLS_MS / 1000} s")
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
                                worked()
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

    /**
     * One of Home Assistant's HLS files (playlist, init or segment) for the page's player, fetched from Home
     * Assistant's own address with the app's token and handed over as it arrives. Only HLS paths: the page
     * can't reach anything else of Home Assistant this way. The page is same-origin with these answers, so
     * Home Assistant needs no CORS settings. An error is passed on as its HTTP status, for the player to report.
     */
    private fun hlsThroughApp(url: Uri): WebResourceResponse {
        val path = url.encodedPath.orEmpty().removePrefix("/ha") + (url.encodedQuery?.let { "?$it" } ?: "")
        if (path.contains("..")) return failure(400)
        return try {
            // Home Assistant holds the first playlist until its stream has two segments: allow for a slow start.
            val conn = ha.openStream(path, readTimeoutMs = HLS_MS.toInt())
            val type = conn.contentType?.substringBefore(';')?.trim()?.ifBlank { null } ?: "application/octet-stream"
            WebResourceResponse(type, null, 200, "OK", mapOf("Cache-Control" to "no-store"), Disconnecting(conn))
        } catch (e: HaHttpException) {
            failure(if (e.code in 400..599) e.code else 502)
        } catch (e: HaForbiddenException) {
            failure(403)
        } catch (e: HaAuthException) {
            failure(401)
        } catch (e: java.net.SocketTimeoutException) {
            // Connected, but no answer: Home Assistant's stream from the camera didn't start in time.
            Logs.w(TAG, "HLS: Home Assistant didn't send ${path.substringAfterLast('/').substringBefore('?')} in time (${e.message})")
            failure(504)
        } catch (e: Exception) {
            Logs.w(TAG, "HLS: couldn't reach Home Assistant: ${e.message}")
            failure(502)
        }
    }

    private fun failure(code: Int) =
        WebResourceResponse("text/plain", "utf-8", code, "Error", mapOf("Cache-Control" to "no-store"), ByteArrayInputStream(ByteArray(0)))

    /** A response's body that lets its connection go once the web view has read it. */
    private class Disconnecting(private val conn: HttpURLConnection) : FilterInputStream(conn.inputStream) {
        override fun close() {
            try {
                super.close()
            } finally {
                conn.disconnect()
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
            scope.launch { if (way != Way.PICTURES) giveUp(generation, why) }
        }

        /** The page drew its first picture (not merely opened the stream). */
        @JavascriptInterface
        fun playing() {
            scope.launch {
                if (entity == null || way == Way.PICTURES) return@launch
                timeout?.cancel()
                worked()
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
