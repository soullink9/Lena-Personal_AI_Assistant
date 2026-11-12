package io.github.soullink9.lena

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException

/** The servers Lena talks to, for their status in Settings and on the wall display's bar ([Connections.state]). */
enum class Server(val label: String) {
    OPEN_WEBUI("Open WebUI"),
    HOME_ASSISTANT("Home Assistant"),
}

/**
 * How a server was the last time Lena checked: working ([problem] null), or what went wrong, in words.
 * [checkedAt] is the wall-clock time of the check, for "checked at 9:41" in Settings.
 */
data class ServerStatus(val problem: String?, val checkedAt: Long) {
    val ok: Boolean get() = problem == null
}

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
 * sheet checks the servers in use while you talk, signing in again by herself if needed ([ensure]); and the
 * wall display does the same every few minutes, and as soon as the network comes back ([keepAlive]).
 * Android tells Lena when the network changes ([watchNetwork]), so nothing waits on a timer for it. What
 * each check found is kept per server ([state]), for Settings and the wall display's bar.
 */
object Connections {
    private const val TAG = "Connections"

    /** How long to wait for the network to come back before giving up on a request. */
    private const val NETWORK_WAIT_MS = 8_000L

    /** How often the wall display checks its connections. */
    private const val KEEP_ALIVE_MS = 4 * 60_000L

    /** When [ensure] last found everything working (elapsed time), so checks close together are skipped. */
    @Volatile
    private var lastGood = 0L

    /** One check at a time: a second caller waits for the first, and then finds it fresh. */
    private val checking = Mutex()

    private val statuses = MutableStateFlow<Map<Server, ServerStatus>>(emptyMap())

    /**
     * What the last check of each server in use found. A server that isn't set up, or isn't used (Open
     * WebUI while Home Assistant's Assist answers), isn't in it.
     */
    val state: StateFlow<Map<Server, ServerStatus>> = statuses.asStateFlow()

    private val returns = MutableStateFlow(0)

    /**
     * Goes up by one each time the network comes back after being down, so a screen can wait for it
     * (`networkReturns.first { it != seen }`) instead of checking on a timer.
     */
    val networkReturns: StateFlow<Int> = returns.asStateFlow()

    /** Whether Android last said there's a network; null until [watchNetwork] starts. */
    @Volatile
    private var onNetwork: Boolean? = null

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

    /**
     * Asks Android to say whenever the device's network changes, for as long as the app runs (started with
     * the app, [LenaApp]). A return counts in [networkReturns], which wakes [awaitNetwork], the wall
     * display's checks and a dashboard that couldn't load.
     */
    @Synchronized
    fun watchNetwork(c: Context) {
        if (onNetwork != null) return
        val app = c.applicationContext
        val manager = app.getSystemService(ConnectivityManager::class.java) ?: return
        onNetwork = online(app)
        manager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                networkIs(capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
            }

            override fun onLost(network: Network) = networkIs(false)
        })
    }

    /** Android's latest word on the network: a return after it was down counts in [networkReturns]. */
    private fun networkIs(up: Boolean) {
        val was = onNetwork
        onNetwork = up
        if (up && was == false) {
            Logs.i(TAG, "The network is back")
            returns.update { it + 1 }
        }
    }

    /**
     * Waits for the network, for up to [maxMs]. False if it didn't come back. Wakes as soon as Android says
     * it's back ([networkReturns]), and looks again every second anyway, in case a change was missed.
     */
    suspend fun awaitNetwork(c: Context, maxMs: Long = NETWORK_WAIT_MS): Boolean {
        val start = SystemClock.elapsedRealtime()
        while (!online(c)) {
            val left = maxMs - (SystemClock.elapsedRealtime() - start)
            if (left <= 0) return false
            val seen = returns.value
            withTimeoutOrNull(minOf(left, 1_000L)) { returns.first { it != seen } }
        }
        return true
    }

    /**
     * Runs one HTTP request, and tries it again (up to twice, after waiting for the network) when the
     * connection itself failed, or a proxy said the server is restarting ([Retry.retryable]). A request that
     * may already have reached the server is only repeated if it's a GET, so nothing is ever sent twice.
     */
    suspend fun <T> retrying(c: Context?, method: String, what: String, request: suspend () -> T): T {
        var attempt = 0
        while (true) {
            try {
                return request()
            } catch (e: IOException) {
                if (attempt >= Retry.WAITS_MS.size || !Retry.retryable(e, method)) throw e
                Logs.i(TAG, "$what: the connection dropped (${e.javaClass.simpleName}); trying again")
                delay(Retry.WAITS_MS[attempt++])
                if (c != null) awaitNetwork(c)
            }
        }
    }

    /**
     * Makes sure the servers in use are reachable before an action: waits for the network, checks the Open
     * WebUI session (signing in again by herself when it ended and that's set up), and checks Home
     * Assistant's token. Open WebUI isn't checked while Home Assistant's Assist answers, since nothing then
     * uses it. Each check also leaves a fresh connection for the request that follows, and its result goes
     * in [state]. Skipped if everything worked less than [fresherThanMs] ago. Problems are logged; the
     * action itself still says what went wrong if it fails.
     */
    suspend fun ensure(c: Context, fresherThanMs: Long = 60_000): Unit = checking.withLock {
        if (SystemClock.elapsedRealtime() - lastGood < fresherThanMs) return@withLock
        if (!awaitNetwork(c)) {
            Logs.w(TAG, "No network")
            return@withLock
        }
        val api = if (Prefs.answers(c) == Answerer.ASSIST) null else Api.from(c)
        val house = HomeAssistant.from(c)
        val openWebUi = api?.let {
            if (it.ensureSession()) null else "Isn't answering, or the session ended and Lena couldn't sign in again by herself."
        }
        val homeAssistant = house?.problem()
        report(Server.OPEN_WEBUI, if (api == null) null else ServerStatus(openWebUi, System.currentTimeMillis()))
        report(Server.HOME_ASSISTANT, if (house == null) null else ServerStatus(homeAssistant, System.currentTimeMillis()))
        if (openWebUi != null) Logs.w(TAG, "Open WebUI isn't answering, or the session ended and she can't sign in by herself")
        if (homeAssistant != null) Logs.w(TAG, "Home Assistant: $homeAssistant")
        if (openWebUi == null && homeAssistant == null) lastGood = SystemClock.elapsedRealtime()
        // Which room this display is in, now and then (see Room).
        if (house != null && homeAssistant == null) Room.refresh(c)
    }

    /**
     * Records what a check of [server] found ([ensure], or a screen's own, like the dashboard's while it
     * waits for Home Assistant); null takes it out of [state] (not set up, or not in use).
     */
    fun report(server: Server, status: ServerStatus?) {
        statuses.update { if (status == null) it - server else it + (server to status) }
    }

    /**
     * For the wall display (the home screen, or a TV: [DashboardActivity.isWallDisplay]): checks the
     * connections every few minutes while it's on screen, and at once when the network comes back, so
     * they're ready whenever someone talks to her.
     */
    fun keepAlive(a: ComponentActivity) {
        a.lifecycleScope.launch {
            a.repeatOnLifecycle(Lifecycle.State.STARTED) {
                if (!DashboardActivity.isWallDisplay(a)) return@repeatOnLifecycle
                while (true) {
                    ensure(a, fresherThanMs = 0)
                    val seen = returns.value
                    withTimeoutOrNull(KEEP_ALIVE_MS) { returns.first { it != seen } }
                }
            }
        }
    }
}
