package io.github.soullink9.lena

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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Connect Home Assistant from your phone: this screen shows an address (and its QR code) that your phone
 * opens on the same Wi-Fi, where you sign in to Home Assistant or paste a token ([PairingServer]). For a TV,
 * where typing a long token with the remote isn't realistic, and handy on any wall display.
 *
 * The page is served only while this screen is open. Once Home Assistant is connected it says so and
 * closes; the wall display (or Settings) carries on from there.
 */
class HaPairActivity : ComponentActivity() {

    /** What the screen shows. */
    private sealed class State {
        data class Waiting(val address: String, val code: String) : State()
        data class Connected(val message: String) : State()
        object NoNetwork : State()
    }

    private var server: PairingServer? = null
    private var state by mutableStateOf<State>(State.NoNetwork)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { LenaTheme { Screen() } }
    }

    /** Serves the page while the screen shows. */
    override fun onStart() {
        super.onStart()
        if (state is State.Connected) return
        val host = PairingServer.localAddress()
        if (host == null) {
            state = State.NoNetwork
            return
        }
        server = PairingServer(Handler()).also {
            runCatching { it.start(host) }
                .onSuccess { _ -> state = State.Waiting(it.address, it.code) }
                .onFailure { e -> Logs.w(TAG, "Couldn't serve the page", e); state = State.NoNetwork }
        }
    }

    override fun onStop() {
        server?.close()
        server = null
        super.onStop()
    }

    /** The phone page's work, on the page's own threads. */
    private inner class Handler : PairingServer.Handler {
        override fun knownAddress(): String = Prefs.haServer(this@HaPairActivity) ?: ""

        override fun signInPage(address: String, clientId: String, redirectUri: String, state: String): String {
            val base = Prefs.normalize(address) ?: throw IOException("Use your Home Assistant's https:// address, the one you open it with.")
            return base + "auth/authorize?" + formOf(
                mapOf("response_type" to "code", "client_id" to clientId, "redirect_uri" to redirectUri, "state" to state)
            )
        }

        override fun signedIn(address: String, code: String, clientId: String): String {
            val base = Prefs.normalize(address) ?: throw IOException("Use your Home Assistant's https:// address.")
            val token = HaSignIn.longLivedToken(base, code, clientId, tokenName())
            return save(base, token)
        }

        override fun pastedToken(address: String, token: String): String {
            val base = Prefs.normalize(address) ?: throw IOException("Use your Home Assistant's https:// address.")
            val trimmed = token.trim()
            if (trimmed.isEmpty()) throw IOException("Paste the token first.")
            // Says exactly what went wrong (refused, or not reached, and why), on the phone and in the log.
            HaSignIn.verify(base, trimmed)
            return save(base, trimmed)
        }

        /** Keeps the address and token, as typing them in Settings would, and says so on both screens. */
        private fun save(base: String, token: String): String {
            val c = this@HaPairActivity
            Secrets.put(c, Secrets.HA_TOKEN, token)
            Prefs.save(c, Prefs.load(c).copy(haServer = base))
            Logs.i(TAG, "Connected to Home Assistant at $base from a phone")
            val message = "Lena on this ${if (Tv.isTv(c)) "TV" else "device"} is connected to your Home Assistant."
            runOnUiThread { state = State.Connected(message) }
            return message
        }
    }

    /** The token's name in your Home Assistant profile: this device and when, so it's easy to find (and unique). */
    private fun tokenName(): String =
        "Lena on ${Build.MODEL} (${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())})"

    @Composable
    private fun Screen() {
        val s = state
        if (s is State.Connected) {
            LaunchedEffect(Unit) {
                delay(2_500)
                setResult(RESULT_OK)
                finish()
            }
        }
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).safeDrawingPadding().padding(32.dp), contentAlignment = Alignment.Center) {
            when (s) {
                is State.Waiting -> Row(horizontalArrangement = Arrangement.spacedBy(48.dp), verticalAlignment = Alignment.CenterVertically) {
                    QrCode(s.address)
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.widthIn(max = 520.dp)) {
                        Text("Connect Home Assistant from your phone", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "Scan this with your phone's camera, or open the address below in its browser. The phone needs to be on the same Wi-Fi. Then sign in to Home Assistant there.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(s.address, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                        Text("Code ${s.code}: it's in the address, so only someone who can see this screen can use the page.",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedButton(onClick = { finish() }) { Text("Cancel") }
                    }
                }
                is State.Connected -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Connected", style = MaterialTheme.typography.headlineMedium)
                    Text(s.message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                }
                State.NoNetwork -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Not on a network", style = MaterialTheme.typography.headlineSmall)
                    Text("Connect this device to your home Wi-Fi or Ethernet, then try again.", style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                    OutlinedButton(onClick = { finish() }) { Text("Back") }
                }
            }
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
 * Lena works exactly as with one made by hand ([HaPairActivity]).
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
