package io.github.soullink9.lena

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.EOFException
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Keeping Lena's connections to Open WebUI and Home Assistant ready, so asking her doesn't start with
 * "couldn't connect".
 *
 * Three things go wrong on a device that's on all day:
 * - the network is briefly down (Wi-Fi reconnecting as the screen wakes, a DNS hiccup);
 * - a connection kept open for reuse was closed by the server or a proxy while idle, so the next request
 *   sent on it fails at once;
 * - the Open WebUI session ended (Open WebUI restarted, say).
 *
 * So every request tries a dropped connection again once the network is back ([retrying]); opening the
 * sheet checks both servers while you talk, signing in again by herself if needed ([ensure]); and the wall
 * display does the same every few minutes, and when it wakes ([keepAlive]).
 */
object Connections {
    private const val TAG = "Connections"

    /** How long to wait for the network to come back before giving up on a request. */
    private const val NETWORK_WAIT_MS = 8_000L

    /** The waits before each new try of a dropped connection. */
    private val RETRY_WAITS_MS = listOf(700L, 2_000L)

    /** How often the wall display checks its connections. */
    private const val KEEP_ALIVE_MS = 4 * 60_000L

    /** When [ensure] last found everything working (elapsed time), so checks close together are skipped. */
    @Volatile
    private var lastGood = 0L

    /** One check at a time: a second caller waits for the first, and then finds it fresh. */
    private val checking = Mutex()

    /**
     * Whether the device is on a network. Not "validated" (reaching the internet): your servers may be at
     * home on a network without it.
     */
    fun online(c: Context): Boolean {
        val manager = c.getSystemService(ConnectivityManager::class.java) ?: return true
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /** Waits for the network, for up to [maxMs]. False if it didn't come back. */
    suspend fun awaitNetwork(c: Context, maxMs: Long = NETWORK_WAIT_MS): Boolean {
        val start = SystemClock.elapsedRealtime()
        while (!online(c)) {
            if (SystemClock.elapsedRealtime() - start > maxMs) return false
            delay(250)
        }
        return true
    }

    /**
     * Runs one HTTP request, and tries it again (up to twice, after waiting for the network) when the
     * connection itself failed. A request that may already have reached the server (no answer in time, the
     * connection reset mid-answer) is only repeated if it's a GET, so nothing is ever sent twice.
     */
    suspend fun <T> retrying(c: Context?, method: String, what: String, request: suspend () -> T): T {
        var attempt = 0
        while (true) {
            try {
                return request()
            } catch (e: IOException) {
                if (attempt >= RETRY_WAITS_MS.size || !retryable(e, method)) throw e
                Logs.i(TAG, "$what: the connection dropped (${e.javaClass.simpleName}); trying again")
                delay(RETRY_WAITS_MS[attempt++])
                if (c != null) awaitNetwork(c)
            }
        }
    }

    /**
     * Whether a failed request is worth trying again. Answers from the server (signed out, not allowed,
     * HTTP errors) aren't: trying again would get the same answer.
     */
    private fun retryable(e: IOException, method: String): Boolean = when {
        e is AuthException || e is ForbiddenException || e is ConflictException || e is HaAuthException -> false
        e.message?.startsWith("HTTP ") == true -> false
        // Never reached the server: no route, no name, no connection.
        e is ConnectException || e is UnknownHostException || e is NoRouteToHostException -> true
        e is SocketTimeoutException && e.message?.contains("connect", ignoreCase = true) == true -> true
        // A reused connection the server had already closed: the request was never read.
        e is EOFException || e.message?.contains("unexpected end of stream", ignoreCase = true) == true -> true
        // Anything else may have reached the server; only a GET is safe to send again.
        method == "GET" -> e is SocketTimeoutException || e is SocketException || e is SSLException
        else -> false
    }

    /**
     * Makes sure Open WebUI and Home Assistant are reachable before an action: waits for the network, checks
     * the Open WebUI session (signing in again by herself when it ended and that's set up), and checks Home
     * Assistant's token. Each check also leaves a fresh connection for the request that follows. Skipped if
     * everything worked less than [fresherThanMs] ago. Problems are logged; the action itself still says
     * what went wrong if it fails.
     */
    suspend fun ensure(c: Context, fresherThanMs: Long = 60_000): Unit = checking.withLock {
        if (SystemClock.elapsedRealtime() - lastGood < fresherThanMs) return@withLock
        if (!awaitNetwork(c)) {
            Logs.w(TAG, "No network")
            return@withLock
        }
        val openWebUi = Api.from(c)?.ensureSession() ?: true
        val homeAssistant = HomeAssistant.from(c)?.check() ?: true
        if (!openWebUi) Logs.w(TAG, "Open WebUI isn't answering, or the session ended and she can't sign in by herself")
        if (!homeAssistant) Logs.w(TAG, "Home Assistant isn't answering, or didn't accept Lena's token")
        if (openWebUi && homeAssistant) lastGood = SystemClock.elapsedRealtime()
        // Which room this display is in, now and then (see Room).
        if (homeAssistant) Room.refresh(c)
    }

    /**
     * For the wall display (dashboard mode): checks the connections every few minutes while it's on
     * screen, so they're ready whenever someone talks to her.
     */
    fun keepAlive(a: ComponentActivity) {
        a.lifecycleScope.launch {
            a.repeatOnLifecycle(Lifecycle.State.STARTED) {
                if (!DashboardActivity.isHomeScreen(a)) return@repeatOnLifecycle
                while (true) {
                    ensure(a, fresherThanMs = 0)
                    delay(KEEP_ALIVE_MS)
                }
            }
        }
    }
}
