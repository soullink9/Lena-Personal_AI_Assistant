package io.github.soullink9.lena

import android.content.Context
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.io.Closeable
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * A connection to Home Assistant's WebSocket API that stays open, for camera mode: its cameras and their
 * devices (only the WebSocket API lists those), their motion as it happens, and the messages that start a
 * live picture (WebRTC). Signed in with Lena's token, on [MiniWebSocket].
 *
 * One thread reads every message and hands each to whoever asked ([call]) or subscribed ([subscribe]);
 * one thread sends every message ([sender]), giving each its ID as it goes out, because Home Assistant
 * refuses a message whose ID isn't higher than the last one it got ("Identifier values have to increase")
 * and drops the connection; another pings every 30 seconds, so a connection that died quietly is noticed and [onClosed] is told.
 * It's opened when camera mode opens and closed when it closes ([CameraActivity]).
 */
class HaSocket private constructor(private val ws: MiniWebSocket) : Closeable {

    companion object {
        private const val TAG = "Home Assistant"
        private const val PING_MS = 30_000L

        /** Connects to this device's Home Assistant and signs in. Blocking: call it off the main thread. */
        fun open(c: Context): HaSocket {
            val base = Prefs.haServer(c) ?: throw IOException("Home Assistant isn't connected")
            val token = Secrets.get(c, Secrets.HA_TOKEN) ?: throw IOException("Home Assistant isn't connected")
            val url = base.trimEnd('/').replaceFirst("https://", "wss://").replaceFirst("http://", "ws://") + "/api/websocket"
            val ws = MiniWebSocket(url)
            try {
                ws.receive() // auth_required
                ws.send(JSONObject().put("type", "auth").put("access_token", token).toString())
                val auth = JSONObject(ws.receive())
                if (auth.optString("type") != "auth_ok") throw HaAuthException()
            } catch (e: Exception) {
                ws.close()
                throw e
            }
            ws.readTimeout(0) // the pings notice a dead connection
            return HaSocket(ws).also { it.start() }
        }
    }

    /**
     * Every message goes out on this one thread, and takes its ID there ([nextId], only ever touched on it),
     * so IDs reach Home Assistant in increasing order even when several are asked for at once (camera mode
     * asks for its cameras' entities, devices and states together). Taking the ID first and sending it later
     * from whichever thread let a higher ID overtake a lower one.
     */
    private val senderThread = Executors.newSingleThreadExecutor { r ->
        Thread(r, "Lena Home Assistant send").apply { isDaemon = true }
    }
    private val sender = senderThread.asCoroutineDispatcher()
    private var nextId = 1
    private val waiting = ConcurrentHashMap<Int, CompletableDeferred<JSONObject>>()
    private val listeners = ConcurrentHashMap<Int, (JSONObject) -> Unit>()

    @Volatile var closed = false
        private set

    /** Told once, on a background thread, when the connection ends for any reason but [close]. */
    @Volatile var onClosed: (() -> Unit)? = null

    private fun start() {
        Thread({ read() }, "Lena Home Assistant socket").apply { isDaemon = true }.start()
        Thread({ ping() }, "Lena Home Assistant ping").apply { isDaemon = true }.start()
    }

    private fun read() {
        try {
            while (!closed) {
                val message = JSONObject(ws.receive())
                val id = message.optInt("id", -1)
                when (message.optString("type")) {
                    "result", "pong" -> waiting.remove(id)?.complete(message)
                    "event" -> listeners[id]?.let { listener ->
                        runCatching { listener(message.optJSONObject("event") ?: JSONObject()) }
                            .onFailure { Logs.w(TAG, "A camera mode event couldn't be handled", it) }
                    }
                }
            }
        } catch (e: Exception) {
            if (!closed) Logs.w(TAG, "The WebSocket connection ended: ${e.message}")
        }
        end()
    }

    /** A ping every [PING_MS]; no answer within 10 seconds ends the connection. */
    private fun ping() {
        while (!closed) {
            Thread.sleep(PING_MS)
            if (closed) return
            val pong = CompletableDeferred<JSONObject>()
            runCatching { senderThread.execute { sendNow(JSONObject().put("type", "ping")) { id -> waiting[id] = pong } } }
            Thread.sleep(10_000)
            if (!pong.isCompleted) {
                Logs.w(TAG, "Home Assistant didn't answer a ping; closing the connection")
                end()
            }
        }
    }

    private fun end() {
        if (closed) return
        closed = true
        senderThread.shutdown()
        runCatching { ws.close() }
        waiting.values.forEach { it.completeExceptionally(IOException("The connection to Home Assistant ended")) }
        waiting.clear()
        onClosed?.invoke()
    }

    override fun close() {
        onClosed = null
        end()
    }

    /**
     * Sends [message] with the next ID, on [senderThread] only. [before] is told the ID before the message
     * goes out, so an answer can't arrive before anyone waits for it. Returns the ID.
     */
    private fun sendNow(message: JSONObject, before: (Int) -> Unit = {}): Int {
        if (closed) throw IOException("The connection to Home Assistant ended")
        val id = nextId++
        before(id)
        ws.send(message.put("id", id).toString())
        return id
    }

    /** [sendNow] from a coroutine: waits for its turn on [senderThread]. */
    private suspend fun sendInTurn(message: JSONObject, before: (Int) -> Unit): Int = try {
        withContext(sender) { sendNow(message, before) }
    } catch (e: java.util.concurrent.RejectedExecutionException) {
        throw IOException("The connection to Home Assistant ended")
    }

    /** Sends [message] with a new ID, and waits for Home Assistant's result. Throws if it says no. */
    suspend fun call(message: JSONObject): JSONObject {
        val result = CompletableDeferred<JSONObject>()
        var id = -1
        try {
            sendInTurn(message) { taken ->
                id = taken
                waiting[taken] = result
            }
        } catch (e: Exception) {
            if (id >= 0) waiting.remove(id)
            throw e
        }
        val reply = try {
            withTimeout(20_000) { result.await() }
        } finally {
            waiting.remove(id)
        }
        if (!reply.optBoolean("success")) {
            val error = reply.optJSONObject("error")
            throw IOException("${message.optString("type")}: ${error?.optString("message") ?: "refused"}")
        }
        return reply
    }

    /** [call]'s result as a list (get_states, registries). */
    suspend fun list(type: String): JSONArray = call(JSONObject().put("type", type)).optJSONArray("result") ?: JSONArray()

    /**
     * Sends a subscription ([message], such as subscribe_entities or camera/webrtc/offer) and passes each of
     * its events to [onEvent], on the reading thread. Returns its ID, for [unsubscribe].
     */
    suspend fun subscribe(message: JSONObject, onEvent: (JSONObject) -> Unit): Int {
        val result = CompletableDeferred<JSONObject>()
        var id = -1
        try {
            sendInTurn(message) { taken ->
                id = taken
                listeners[taken] = onEvent
                waiting[taken] = result
            }
        } catch (e: Exception) {
            if (id >= 0) {
                listeners.remove(id)
                waiting.remove(id)
            }
            throw e
        }
        val reply = try {
            withTimeout(20_000) { result.await() }
        } catch (e: Exception) {
            listeners.remove(id)
            throw e
        } finally {
            waiting.remove(id)
        }
        if (!reply.optBoolean("success")) {
            listeners.remove(id)
            throw IOException("${message.optString("type")}: ${reply.optJSONObject("error")?.optString("message") ?: "refused"}")
        }
        return id
    }

    /** Ends a subscription; its events stop at once, whether or not Home Assistant answers. */
    fun unsubscribe(id: Int) {
        if (listeners.remove(id) == null || closed) return
        later(JSONObject().put("type", "unsubscribe_events").put("subscription", id))
    }

    /** Sends a message whose answer doesn't matter (a WebRTC candidate). */
    fun send(message: JSONObject) {
        if (closed) return
        later(message)
    }

    /** Queues [message] on [senderThread] without waiting; lost quietly if the connection has ended. */
    private fun later(message: JSONObject) {
        runCatching { senderThread.execute { runCatching { sendNow(message) } } }
    }
}
