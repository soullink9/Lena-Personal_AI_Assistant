package io.github.soullink9.lena

import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Set up from your phone: this screen shows an address (and its QR code) that your phone opens on the same
 * Wi-Fi, where you fill in every address and sign-in Lena uses ([PairingServer]). For a TV, where typing
 * with the remote isn't realistic, and handy on any wall display or phone.
 *
 * The page is served only while this screen is open. What the phone sends is listed here and saved only
 * when you press OK ([Confirm]); signing in with Home Assistant saves straight away, since it needs your
 * Home Assistant password on its own page. Once saved the screen closes after a moment.
 */
class PhoneSetupActivity : ComponentActivity() {

    // Dark on a TV (Tv.darkOnTv).
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase)
        Tv.darkOnTv(this, newBase)
    }

    /** What the screen shows. */
    private sealed class State {
        data class Waiting(val address: String, val qr: String, val code: String) : State()
        data class Saved(val message: String) : State()
        object NoNetwork : State()
        object TooManyTries : State()
    }

    /** What the phone sent, waiting for OK here: one line per change, and where the answer goes. */
    private class Confirm(val lines: List<String>, val changes: Map<String, String>, val answer: (PairingServer.Outcome) -> Unit)

    private var server: PairingServer? = null
    private var state by mutableStateOf<State>(State.NoNetwork)
    private var phone by mutableStateOf(false)
    private var note by mutableStateOf<String?>(null)
    private var confirm by mutableStateOf<Confirm?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { LenaTheme { Screen() } }
    }

    /** Serves the page while the screen shows. */
    override fun onStart() {
        super.onStart()
        if (state is State.Saved) return
        val host = PairingServer.localAddress()
        if (host == null) {
            state = State.NoNetwork
            return
        }
        server = PairingServer(Handler()).also {
            runCatching { it.start(host) }
                .onSuccess { _ -> state = State.Waiting(it.address, it.addressWithKey, it.code) }
                .onFailure { e -> Logs.w(TAG, "Couldn't serve the page", e); state = State.NoNetwork }
        }
    }

    override fun onStop() {
        server?.close()
        server = null
        // A change waiting for OK isn't saved once the screen goes.
        confirm?.answer?.invoke(PairingServer.Outcome("refused", "The screen was closed, so nothing was saved."))
        confirm = null
        phone = false
        super.onStop()
    }

    /** The phone page's work, on the page's own threads. */
    private inner class Handler : PairingServer.Handler {
        private val c get() = this@PhoneSetupActivity

        /** Home Assistant's motion sensors, read once (only when it's connected). */
        private val sensors: List<String> by lazy { motionSensors() }

        override fun known(): PairingServer.Known {
            val s = Prefs.load(c)
            val addresses = mapOf(
                PhoneFields.OWUI to s.server,
                PhoneFields.OWUI_SIGN_IN to s.signIn,
                PhoneFields.HA to s.haServer,
                PhoneFields.GITEA to Updates.giteaAddress(c),
                PhoneFields.WAKE_SENSOR to s.motionEntity,
                PhoneFields.BRIEFING_SENSOR to Room.briefingSensor(c),
            )
            val saved = buildSet {
                if (Secrets.has(c, OpenWebUiSignIn.USER)) add(PhoneFields.OWUI_EMAIL)
                if (Secrets.has(c, OpenWebUiSignIn.PASSWORD)) add(PhoneFields.OWUI_PASSWORD)
                if (Secrets.has(c, Secrets.HA_TOKEN)) add(PhoneFields.HA_TOKEN)
            }
            return PairingServer.Known(addresses, saved, sensors, Tv.thisDevice(c))
        }

        override fun signInPage(address: String, clientId: String, redirectUri: String, state: String): String {
            val base = Prefs.normalize(address) ?: throw IOException("Use your Home Assistant's https:// address, the one you open it with.")
            return base + "auth/authorize?" + formOf(
                mapOf("response_type" to "code", "client_id" to clientId, "redirect_uri" to redirectUri, "state" to state)
            )
        }

        override fun signedIn(address: String, code: String, clientId: String): String {
            val base = Prefs.normalize(address) ?: throw IOException("Use your Home Assistant's https:// address.")
            val token = HaSignIn.longLivedToken(base, code, clientId, tokenName())
            Secrets.put(c, Secrets.HA_TOKEN, token)
            Prefs.save(c, Prefs.load(c).copy(haServer = base))
            Logs.i(TAG, "Connected to Home Assistant at $base from a phone")
            runOnUiThread { note = "Home Assistant connected." }
            return "Lena on ${Tv.thisDevice(c)} is connected to your Home Assistant."
        }

        override fun phoneConnected() {
            runOnUiThread { phone = true }
        }

        override fun propose(changes: Map<String, String>, answer: (PairingServer.Outcome) -> Unit) {
            val lines = describe(changes)
            if (lines.isEmpty()) {
                answer(PairingServer.Outcome("saved", "Nothing to change: it's all as Lena has it."))
                return
            }
            runOnUiThread { confirm = Confirm(lines, changes, answer) }
        }
    }

    /**
     * What would change, one line each: addresses with their new value, secrets only as "new" (they're
     * never shown, here or on the phone). Values that are already set are left out.
     */
    private fun describe(changes: Map<String, String>): List<String> {
        val s = Prefs.load(this)
        val now = mapOf(
            PhoneFields.OWUI to s.server, PhoneFields.OWUI_SIGN_IN to s.signIn, PhoneFields.HA to s.haServer,
            PhoneFields.GITEA to Updates.giteaAddress(this), PhoneFields.WAKE_SENSOR to s.motionEntity,
            PhoneFields.BRIEFING_SENSOR to Room.briefingSensor(this),
        )
        val names = mapOf(
            PhoneFields.OWUI to "Open WebUI's address", PhoneFields.OWUI_SIGN_IN to "Open WebUI's sign-in address",
            PhoneFields.OWUI_EMAIL to "Open WebUI email", PhoneFields.OWUI_PASSWORD to "Open WebUI password",
            PhoneFields.HA to "Home Assistant's address", PhoneFields.HA_TOKEN to "Home Assistant token",
            PhoneFields.GITEA to "Your Gitea", PhoneFields.WAKE_SENSOR to "Sensor that wakes the screensaver",
            PhoneFields.BRIEFING_SENSOR to "Sensor for her briefing",
        )
        return PhoneFields.ALL.mapNotNull { field ->
            val value = changes[field] ?: return@mapNotNull null
            when {
                field in PhoneFields.SECRETS -> if (value.isBlank()) null else "${names[field]}: new"
                value == now[field] || (value.isNotBlank() && Prefs.normalize(value) != null && Prefs.normalize(value) == now[field]) -> null
                value.isBlank() -> "${names[field]}: removed"
                else -> "${names[field]}: $value"
            }
        }
    }

    /**
     * Checks and saves what the phone sent, after OK here; returns what to tell the phone. Every address is
     * checked first, and the Home Assistant token against Home Assistant, so a mistake saves nothing. The
     * Open WebUI sign-in is tried once saved, and the phone is told how it went.
     */
    private fun save(changes: Map<String, String>): PairingServer.Outcome {
        val c = this
        try {
            var s = Prefs.load(c)
            changes[PhoneFields.OWUI]?.let {
                s = s.copy(server = if (it.isBlank()) "" else Prefs.normalize(it) ?: throw IOException("Open WebUI's address has to be an https:// address."))
            }
            changes[PhoneFields.OWUI_SIGN_IN]?.let {
                s = s.copy(signIn = Prefs.normalizeSignIn(it) ?: throw IOException("Sign-in addresses have to be https:// addresses."))
            }
            changes[PhoneFields.HA]?.let {
                s = s.copy(haServer = if (it.isBlank()) "" else Prefs.normalize(it) ?: throw IOException("Home Assistant's address has to be an https:// address."))
            }
            val haToken = changes[PhoneFields.HA_TOKEN]?.takeIf { it.isNotBlank() }
            if (haToken != null) {
                val base = s.haServer.ifBlank { throw IOException("Fill in Home Assistant's address with its token.") }
                HaSignIn.verify(base, haToken)
            }
            val gitea = changes[PhoneFields.GITEA]
            if (!gitea.isNullOrBlank() && UpdateSource.from(gitea) == null) {
                throw IOException("Your Gitea has to be an https:// address, like https://gitea.example.com/you.")
            }
            val entity = Regex("[a-z_]+\\.[a-z0-9_]+")
            changes[PhoneFields.WAKE_SENSOR]?.let {
                if (it.isNotBlank() && !entity.matches(it)) throw IOException("$it isn't a sensor's entity ID, like binary_sensor.hallway_motion.")
                s = s.copy(motionEntity = it)
            }
            changes[PhoneFields.BRIEFING_SENSOR]?.let {
                if (it.isNotBlank() && !entity.matches(it)) throw IOException("$it isn't a sensor's entity ID, like binary_sensor.kitchen_motion.")
            }
            val email = changes[PhoneFields.OWUI_EMAIL]?.takeIf { it.isNotBlank() }
            val password = changes[PhoneFields.OWUI_PASSWORD]?.takeIf { it.isNotBlank() }
            // Her email and password are for signing in by herself: sent from the phone, that's turned on.
            if ((email != null || password != null) && s.autoSignIn == AutoSignIn.OFF) s = s.copy(autoSignIn = AutoSignIn.PASSWORD)

            Prefs.save(c, s)
            if (haToken != null) Secrets.put(c, Secrets.HA_TOKEN, haToken)
            if (email != null) Secrets.put(c, OpenWebUiSignIn.USER, email)
            if (password != null) Secrets.put(c, OpenWebUiSignIn.PASSWORD, password)
            if (gitea != null) Updates.setGiteaAddress(c, gitea)
            changes[PhoneFields.BRIEFING_SENSOR]?.let { Room.chooseBriefingSensor(c, it) }
            Refresh.request() // the chats and dashboards pick up new addresses when they're next shown
            Logs.i(TAG, "Saved from a phone: ${changes.keys.filter { it !in PhoneFields.SECRETS || changes[it]!!.isNotBlank() }.joinToString()}")

            val signedIn = if (email != null || password != null) {
                " " + runBlocking { OpenWebUiSignIn.test(c) }
            } else ""
            return PairingServer.Outcome("saved", "Saved on ${Tv.thisDevice(c)}.$signedIn")
        } catch (e: IOException) {
            Logs.w(TAG, "Not saved from the phone: ${e.message}")
            return PairingServer.Outcome("failed", "Nothing was saved: ${e.message}")
        }
    }

    /** Home Assistant's motion and presence sensors, for the phone page to pick from; empty without it. */
    private fun motionSensors(): List<String> = runCatching {
        val ha = HomeAssistant.from(this) ?: return emptyList()
        val states = org.json.JSONArray(String(runBlocking { ha.bytes("/api/states") }))
        (0 until states.length()).mapNotNull { i ->
            val item = states.getJSONObject(i)
            val id = item.optString("entity_id")
            val kind = item.optJSONObject("attributes")?.optString("device_class").orEmpty()
            id.takeIf { it.startsWith("binary_sensor.") && kind in setOf("motion", "occupancy", "presence") }
        }.sorted()
    }.getOrDefault(emptyList())

    /** The token's name in your Home Assistant profile: this device and when, so it's easy to find (and unique). */
    private fun tokenName(): String =
        "Lena on ${Build.MODEL} (${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())})"

    @Composable
    private fun Screen() {
        val s = state
        val scope = rememberCoroutineScope()
        if (s is State.Saved) {
            LaunchedEffect(Unit) {
                delay(3_000)
                setResult(RESULT_OK)
                finish()
            }
        }
        // Anything saved (Home Assistant signed in) tells Settings to look again, even if closed with Back.
        if (note != null) SideEffect { setResult(RESULT_OK) }
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).safeDrawingPadding().padding(32.dp), contentAlignment = Alignment.Center) {
            when (s) {
                is State.Waiting -> Row(horizontalArrangement = Arrangement.spacedBy(48.dp), verticalAlignment = Alignment.CenterVertically) {
                    QrCode(s.qr)
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.widthIn(max = 520.dp)) {
                        Text("Set up from your phone", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "Scan this with your phone's camera, on the same Wi-Fi. Fill in Lena's addresses and sign-ins there: " +
                                "they're encrypted on the phone and saved here only when you press OK.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(s.address, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                        Text("Typed into a browser, the address only signs in with Home Assistant; the QR code also carries the page's key.",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (phone) Text("Phone connected", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        note?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
                        OutlinedButton(onClick = { finish() }) { Text(if (note != null) "Done" else "Cancel") }
                    }
                }
                is State.Saved -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Saved", style = MaterialTheme.typography.headlineMedium)
                    Text(s.message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                }
                State.NoNetwork -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Not on a network", style = MaterialTheme.typography.headlineSmall)
                    Text("Connect this device to your home Wi-Fi or Ethernet, then try again.", style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                    OutlinedButton(onClick = { finish() }) { Text("Back") }
                }
                State.TooManyTries -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Closed", style = MaterialTheme.typography.headlineSmall)
                    Text("Something on the network kept guessing the page's code, so it was closed. Open it again for a new code.",
                        style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                    OutlinedButton(onClick = { finish() }) { Text("Back") }
                }
            }
        }
        // Too many wrong codes closes the page; say so instead of showing a dead address.
        LaunchedEffect(s) {
            while (state is State.Waiting) {
                delay(1_000)
                if (server?.closedForWrongCodes == true) state = State.TooManyTries
            }
        }
        confirm?.let { asked ->
            AlertDialog(
                onDismissRequest = {},
                title = { Text("Save these from the phone?") },
                text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { asked.lines.forEach { Text(it) } } },
                confirmButton = {
                    TextButton(onClick = {
                        confirm = null
                        scope.launch {
                            val outcome = withContext(Dispatchers.IO) { save(asked.changes) }
                            asked.answer(outcome)
                            if (outcome.state == "saved") state = State.Saved(outcome.message) else note = outcome.message
                        }
                    }) { Text("Save") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        confirm = null
                        asked.answer(PairingServer.Outcome("refused", "Not saved: it was turned down on ${Tv.thisDevice(this@PhoneSetupActivity)}."))
                    }) { Text("Don't save") }
                },
            )
        }
    }

    /** The address as a QR code: dark on white with a white border, whatever the theme, so cameras read it. */
    @Composable
    private fun QrCode(text: String) {
        val modules = remember(text) { Qr.encode(text) }
        Canvas(Modifier.size(260.dp).background(Color.White)) {
            val quiet = 4 // the white border QR codes need, in modules
            val cell = size.minDimension / (modules.size + quiet * 2)
            for (y in modules.indices) for (x in modules.indices) {
                if (modules[y][x]) {
                    drawRect(Color.Black, Offset((x + quiet) * cell, (y + quiet) * cell), Size(cell + 0.5f, cell + 0.5f))
                }
            }
        }
    }

    companion object {
        private const val TAG = "Pairing"
    }
}

/**
 * Signing in to Home Assistant from the phone page and turning that into a long-lived token, so the rest of
 * Lena works exactly as with one made by hand ([PhoneSetupActivity]).
 */
object HaSignIn {
    /**
     * Checks a token against Home Assistant, the way every later request will use it. Throws with what
     * went wrong, in words for the phone: Home Assistant refusing the token, or this device not reaching
     * Home Assistant at all, which looks the same from the phone but has nothing to do with the token (a
     * name this device can't look up, a certificate it doesn't trust, a network it isn't on).
     */
    fun verify(base: String, token: String) {
        val conn = URL(base + "api/").openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("Authorization", "Bearer $token")
            val code = try {
                conn.responseCode
            } catch (e: IOException) {
                Logs.w("Pairing", "Couldn't reach Home Assistant at $base", e)
                throw IOException(unreachable(base, e))
            }
            when {
                code == 401 -> throw IOException(
                    "Home Assistant at $base refused that token (HTTP 401). Make a new one in your profile → Security, and paste all of it."
                )
                // Home Assistant answers a wrong token with 401. A 403 means it (or something in front of it)
                // won't talk to this device whatever the token: most often Home Assistant has banned the
                // device's address after failed sign-ins, such as a token mistyped with a remote.
                code == 403 -> throw IOException(
                    "Home Assistant at $base won't talk to this device (HTTP 403), whatever the token. Most likely it banned " +
                        "this device's address after earlier failed sign-ins: remove the address from ip_bans.yaml in Home " +
                        "Assistant's config folder and restart Home Assistant (its notifications say which address was banned). " +
                        "Otherwise a proxy or firewall in front of Home Assistant is blocking this device."
                )
                code in 300..399 -> throw IOException(
                    "$base sends this device somewhere else (HTTP $code, to ${conn.getHeaderField("Location")}). Use the address it ends up at."
                )
                code !in 200..299 -> throw IOException("Home Assistant at $base answered HTTP $code. Check the address.")
            }
        } finally {
            conn.disconnect()
        }
    }

    /** Why this device couldn't reach Home Assistant, in words, with what usually fixes it. */
    private fun unreachable(base: String, e: IOException): String {
        val host = runCatching { URL(base).host }.getOrDefault(base)
        return when {
            e is java.net.UnknownHostException && host.endsWith(".local") && android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S ->
                "This device can't look up $host: Android before 12 doesn't find .local names. Use the address you use away from home, or one your router's DNS knows."
            e is java.net.UnknownHostException -> "This device can't look up $host. Check the address, and that this device uses the same network and DNS as your phone."
            e is javax.net.ssl.SSLException || e.cause is java.security.cert.CertificateException ->
                "This device doesn't trust $host's certificate (${e.message}). A certificate from your own authority has to be installed on this device too; one from Let's Encrypt or Nabu Casa just works."
            e is java.net.SocketTimeoutException || e is java.net.ConnectException ->
                "This device couldn't connect to $host (${e.message}). Check that it's reachable from this device's network."
            else -> "This device couldn't reach Home Assistant at $base: ${e.message}"
        }
    }

    /**
     * Swaps Home Assistant's one-time [code] for a sign-in, asks for a long-lived token called [name] (only
     * its WebSocket API makes them), and signs that first sign-in out again, so only the token is left.
     * Blocking; throws with a message for the phone when anything fails.
     */
    fun longLivedToken(base: String, code: String, clientId: String, name: String): String {
        val signIn = JSONObject(post(base + "auth/token", mapOf("grant_type" to "authorization_code", "code" to code, "client_id" to clientId)))
        val access = signIn.optString("access_token").ifEmpty { throw IOException("Home Assistant didn't sign you in. Try again.") }
        val refresh = signIn.optString("refresh_token")
        try {
            val socketUrl = base.replaceFirst("https://", "wss://").replaceFirst("http://", "ws://") + "api/websocket"
            MiniWebSocket(socketUrl).use { ws ->
                ws.receive() // auth_required
                ws.send(JSONObject().put("type", "auth").put("access_token", access).toString())
                val auth = JSONObject(ws.receive())
                if (auth.optString("type") != "auth_ok") throw IOException("Home Assistant refused the sign-in: ${auth.optString("message")}")
                ws.send(JSONObject().put("id", 1).put("type", "auth/long_lived_access_token")
                    .put("client_name", name).put("lifespan", 3650).toString())
                while (true) {
                    val reply = JSONObject(ws.receive())
                    if (reply.optInt("id") != 1 || reply.optString("type") != "result") continue
                    if (!reply.optBoolean("success")) {
                        throw IOException("Home Assistant didn't make a token: ${reply.optJSONObject("error")?.optString("message") ?: "no reason given"}")
                    }
                    return reply.getString("result")
                }
            }
        } finally {
            // The sign-in itself isn't needed once the token exists (it would stay listed in your profile).
            if (refresh.isNotEmpty()) runCatching { post(base + "auth/revoke", mapOf("token" to refresh)) }
        }
        throw IOException("Home Assistant closed the connection before making a token.")
    }

    /** A form posted to Home Assistant's sign-in API; the answer's text. */
    private fun post(url: String, values: Map<String, String>): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 10_000
            conn.readTimeout = 20_000
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            conn.outputStream.use { it.write(formOf(values).toByteArray()) }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) throw IOException("Home Assistant answered $code: ${text.take(200)}")
            return text
        } finally {
            conn.disconnect()
        }
    }
}
