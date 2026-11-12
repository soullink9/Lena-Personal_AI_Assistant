package io.github.soullink9.lena

import java.io.BufferedInputStream
import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * "Set up from your phone": a small web page this device serves on your home network for a few minutes,
 * where your phone fills in every address and sign-in Lena uses, instead of typing them with a TV remote or
 * a wall tablet's keyboard ([PhoneSetupActivity] shows its address and a QR code).
 *
 * What the page covers ([PhoneFields]): Open WebUI's address, sign-in address, email and password; Home
 * Assistant's address and token (or **Sign in with Home Assistant**: your phone goes to your Home
 * Assistant's own sign-in page, which sends it back here with a one-time code, and this device makes its own
 * long-lived token, [HaSignIn]); your Gitea for updates; and the two motion sensors.
 *
 * How it stays safe:
 * - **Write-only.** The page is never sent a saved password, email or token, only whether each is set.
 *   Addresses are filled in, so you can correct them.
 * - **Encrypted in the page.** The QR code carries a one-time key after a `#`, which browsers never send
 *   over the network. The page encrypts what you type with it (ChaCha20-Poly1305, [PHONE_CIPHER_JS]) and
 *   this device decrypts it ([decrypt]): someone listening on the Wi-Fi sees only ciphertext. (A phone
 *   browser only has its own crypto on https pages, and this one is plain http on your network, hence the
 *   page's own small cipher, checked against Java's in tools/pairing_check.)
 * - **Confirmed on the device.** Nothing the phone sends is saved until someone presses OK on this device
 *   ([Handler.propose]). Signing in with Home Assistant is the exception: that needs your Home Assistant
 *   password, typed on Home Assistant's own https page.
 * - **One phone, a few tries.** The first phone to open the page holds it (a cookie); any other is told
 *   it's in use. Five requests with a wrong code close it.
 * - **Short-lived.** It stops when the screen closes, once saved, or after [LIFETIME_MS].
 *
 * What it can't stop: someone already inside your network, actively rewriting the page as it loads, during
 * those minutes. The page says so, and Home Assistant's own sign-in avoids it for that password.
 *
 * Plain Kotlin and java.net only, so it can be checked away from Android.
 */
class PairingServer(private val handler: Handler) : Closeable {

    /** What the page does; [PhoneSetupActivity] answers, off the main thread. */
    interface Handler {
        /** What the page shows: addresses filled in, which secrets are saved (never their values), sensors. */
        fun known(): Known

        /**
         * Where to send the phone to sign in, for the address typed on it: Home Assistant's sign-in page
         * with this page as the app asking ([clientId]) and where to come back to ([redirectUri]). An
         * exception's message is shown on the phone.
         */
        fun signInPage(address: String, clientId: String, redirectUri: String, state: String): String

        /** Finishes signing in with Home Assistant's one-time [code]; returns what to tell the phone. */
        fun signedIn(address: String, code: String, clientId: String): String

        /** A phone opened the page (and now holds it). */
        fun phoneConnected() {}

        /**
         * The phone sent these values (decrypted, only [PhoneFields.ALL]): show what would change on the
         * device, and call [answer] once it's saved, refused there, or failed.
         */
        fun propose(changes: Map<String, String>, answer: (Outcome) -> Unit)
    }

    /**
     * What the page is filled in with. [addresses]: every field that isn't secret, as set now. [saved]: which
     * of [PhoneFields.SECRETS] are set. [sensors]: motion sensors Home Assistant has, to pick from.
     * [device]: "this TV", "this tablet" or "this phone".
     */
    data class Known(val addresses: Map<String, String>, val saved: Set<String>, val sensors: List<String>, val device: String)

    /** How a save went: [state] is saved, refused or failed; [message] is shown on the phone. */
    data class Outcome(val state: String, val message: String)

    companion object {
        /** The port to try first, so the address stays the same from one time to the next. */
        const val PORT = 8765

        /** How long the page is served at most. */
        const val LIFETIME_MS = 15 * 60_000L

        /** Requests with a wrong code before the page closes (a code is about a billion guesses). */
        const val WRONG_CODES = 5

        /** Letters and digits that can't be mistaken for each other when read off a TV. */
        private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

        /** This device's address on the home network (IPv4, Wi-Fi or Ethernet), or null when it has none. */
        fun localAddress(): String? = runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback && !it.isVirtual }
                .sortedBy { if (it.name.startsWith("wlan") || it.name.startsWith("eth")) 0 else 1 }
                .flatMap { it.inetAddresses.toList() }
                .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }
                ?.hostAddress
        }.getOrNull()

        /** ChaCha20-Poly1305: Android's name for it, then Java's (the checks run on a computer's Java). */
        private fun chacha(): Cipher = runCatching { Cipher.getInstance("ChaCha20/Poly1305/NoPadding") }
            .getOrElse { Cipher.getInstance("ChaCha20-Poly1305") }

        /** Encrypts as the page does; for the checks, which play the phone. */
        fun encrypt(key: ByteArray, nonce: ByteArray, aad: ByteArray, plain: ByteArray): ByteArray {
            val cipher = chacha()
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "ChaCha20"), IvParameterSpec(nonce))
            cipher.updateAAD(aad)
            return cipher.doFinal(plain)
        }
    }

    private val random = SecureRandom()

    /** The code in every address, also shown on the screen. */
    val code: String = (1..6).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("")

    /** The page's key, only ever in the QR code (after the #) and in memory here. */
    val key: ByteArray = ByteArray(32).also { random.nextBytes(it) }

    /** The sign-in being waited for: Home Assistant sends this back, so an old or forged reply is refused. */
    private val state: String = (1..24).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("")

    /** The address being signed in to, between leaving for Home Assistant's page and coming back. */
    @Volatile private var signingInTo: String? = null

    /** The phone holding the page: its cookie. */
    @Volatile private var holder: String? = null

    /** Requests that guessed a code; [WRONG_CODES] of them close the page. */
    private var wrongCodes = 0

    /** Nonces already used, so a message sent again isn't taken twice. */
    private val usedNonces = HashSet<String>()

    /** The last save: waiting, saved, refused or failed. */
    @Volatile private var outcome: Outcome? = null

    private var socket: ServerSocket? = null
    @Volatile private var host: String = "127.0.0.1"
    private val started = System.currentTimeMillis()

    /** Whether it's done (saved), after which it answers no more. */
    @Volatile var done = false
        private set

    /** Whether it closed itself after too many wrong codes. */
    @Volatile var closedForWrongCodes = false
        private set

    /** The page's short address, for typing (without the key, only signing in with Home Assistant works). */
    val address: String get() = "http://$host:${socket?.localPort ?: PORT}/$code/"

    /** The address in the QR code: the short one and the key after #, which the phone keeps to itself. */
    val addressWithKey: String get() = address + "#k=" + Base64.getUrlEncoder().withoutPadding().encodeToString(key)

    /**
     * Starts serving on [host] (this device's address on the network), on [PORT] if it's free or any
     * other port if not. Each phone request is answered on a thread of its own.
     */
    fun start(host: String) {
        this.host = host
        val server = ServerSocket()
        server.reuseAddress = true
        try {
            server.bind(InetSocketAddress(PORT))
        } catch (e: java.io.IOException) {
            server.bind(InetSocketAddress(0))
        }
        server.soTimeout = 1_000 // to notice when it's time to stop
        socket = server
        Thread({
            while (!server.isClosed) {
                if (System.currentTimeMillis() - started > LIFETIME_MS) break
                val client = try {
                    server.accept()
                } catch (e: SocketTimeoutException) {
                    continue
                } catch (e: java.io.IOException) {
                    break
                }
                Thread({ client.use { answer(it) } }, "Lena phone setup request").apply { isDaemon = true }.start()
            }
            runCatching { server.close() }
        }, "Lena phone setup").apply { isDaemon = true }.start()
    }

    override fun close() {
        runCatching { socket?.close() }
    }

    /** Decrypts what the page sent (base64 nonce and ciphertext); null if it isn't from the page with this key. */
    fun decrypt(nonce: String, ciphertext: String): String? = runCatching {
        val iv = Base64.getDecoder().decode(nonce)
        require(iv.size == 12)
        synchronized(usedNonces) { require(usedNonces.add(nonce)) }
        val cipher = chacha()
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "ChaCha20"), IvParameterSpec(iv))
        cipher.updateAAD(code.toByteArray(Charsets.UTF_8))
        String(cipher.doFinal(Base64.getDecoder().decode(ciphertext)), Charsets.UTF_8)
    }.getOrNull()

    // ---- The page ----------------------------------------------------------------------------

    /** Reads one request from the phone and answers it. */
    private fun answer(client: Socket) {
        client.soTimeout = 30_000
        val input = BufferedInputStream(client.getInputStream())
        val out = client.getOutputStream()
        val requestLine = readLine(input) ?: return
        val headers = HashMap<String, String>()
        while (true) {
            val line = readLine(input) ?: return
            if (line.isEmpty()) break
            val colon = line.indexOf(':')
            if (colon > 0) headers[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
        }
        val parts = requestLine.split(" ")
        if (parts.size < 2) return
        val method = parts[0]
        val target = runCatching { URI(parts[1]) }.getOrNull() ?: return
        val length = headers["content-length"]?.toIntOrNull()?.coerceAtMost(64 * 1024) ?: 0
        val body = if (length > 0) String(readExactly(input, length), Charsets.UTF_8) else ""
        val query = parseForm(target.rawQuery ?: "")
        val cookie = headers["cookie"].orEmpty().split(";").map { it.trim() }
            .firstOrNull { it.startsWith("lena_setup=") }?.substringAfter('=')

        val prefix = "/$code/"
        val path = target.rawPath ?: "/"
        if (!path.startsWith(prefix) && path != "/$code") {
            countWrongCode(path)
            return send(out, 404, page("Not found", "<p>This address isn't open. Use the one on the screen.</p>"))
        }
        val route = path.removePrefix(prefix).removePrefix("/$code")
        if (done) {
            // The page's script still asks how the save went; everything else is over.
            if (route == "status" && cookie != null && cookie == holder) return sendJson(out, outcome ?: Outcome("saved", ""))
            return send(out, 200, page("Saved", "<p>Lena has what you sent. You can close this page.</p>"))
        }
        val clientId = "http://$host:${socket?.localPort ?: PORT}$prefix"

        // The first phone to open the page holds it; the others are told so.
        var setCookie: String? = null
        if (holder == null && route == "") {
            holder = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(16).also { random.nextBytes(it) })
            setCookie = holder
            handler.phoneConnected()
        } else if (cookie == null || cookie != holder) {
            return send(out, 200, page("Already in use", "<p>Another phone is already using this page. Close it on the screen and open it again to start over.</p>"))
        }

        when (route) {
            "save" -> {
                if (method != "POST") return send(out, 404, page("Not found", ""))
                val sent = parseForm(body)
                val plain = decrypt(sent["n"].orEmpty(), sent["c"].orEmpty())
                    ?: return sendJson(out, Outcome("failed", "That didn't come from this page. Scan the code on the screen again."))
                val values = parseForm(plain)
                val changes = PhoneFields.ALL.filter { it in values }.associateWith { values[it]!!.trim() }
                val waiting = Outcome("waiting", "Waiting for OK on ${handler.known().device}.")
                outcome = waiting
                handler.propose(changes) { answer ->
                    outcome = answer
                    if (answer.state == "saved") done = true
                }
                return sendJson(out, waiting)
            }
            "status" -> return sendJson(out, outcome ?: Outcome("idle", ""))
        }
        val result = runCatching {
            when (route) {
                "" -> form()
                "sign-in" -> {
                    val ha = query["ha"].orEmpty()
                    signingInTo = ha
                    return redirect(out, handler.signInPage(ha, clientId, clientId + "done", state))
                }
                "done" -> {
                    val ha = signingInTo
                    when {
                        query["state"] != state || ha == null -> page("Start again", "<p>That sign-in wasn't started from this page. Go back to the address on the screen and sign in again.</p>")
                        query["code"].isNullOrBlank() -> page("Not signed in", "<p>Home Assistant didn't sign you in. ${htmlEscape(query["error"] ?: "")}</p>$back")
                        else -> page("Home Assistant connected", "<p>${htmlEscape(handler.signedIn(ha, query["code"]!!, clientId))}</p>" +
                            "<p><a href=\"/$code/\">Fill in the rest</a> (scan the code on the screen again if the page asks for its key).</p>")
                    }
                }
                else -> page("Not found", "<p>There's nothing here. Use the address on the screen.</p>")
            }
        }.getOrElse { page("That didn't work", "<p>${htmlEscape(it.message ?: "Something went wrong.")}</p>$back") }
        send(out, 200, result, setCookie)
    }

    /** A request for another code counts towards closing the page; a browser's own requests (its icon) don't. */
    private fun countWrongCode(path: String) {
        val first = path.trim('/').substringBefore('/')
        if (!Regex("[A-Z2-9]{6}").matches(first)) return
        synchronized(this) {
            wrongCodes++
            if (wrongCodes >= WRONG_CODES) {
                closedForWrongCodes = true
                close()
            }
        }
    }

    private val back get() = "<p><a href=\"/$code/\">Try again</a></p>"

    /** The form: Home Assistant's sign-in first (the easy way), then every field, encrypted when sent. */
    private fun form(): String {
        val known = handler.known()
        fun value(name: String) = htmlEscape(known.addresses[name].orEmpty())
        fun secret(name: String) = if (name in known.saved) "Saved: leave empty to keep it" else "Not set"
        val sensors = known.sensors.joinToString("") { "<option value=\"${htmlEscape(it)}\">" }
        val device = htmlEscape(known.device)
        return page("Set up Lena", """
            <p>Fill in what Lena on $device needs. Nothing is saved until you press OK on $device.</p>
            <p id="nokey" class="note" hidden>This page was opened without its key, so it can only sign in with Home Assistant. Scan the QR code on the screen to fill in the rest.</p>
            <h2>Home Assistant</h2>
            <label>Address<input id="ha" name="ha" type="url" value="${value(PhoneFields.HA)}" placeholder="https://ha.example.com" autocomplete="url"></label>
            <button type="button" id="signin">Sign in with Home Assistant</button>
            <p class="note">You sign in on your Home Assistant's own page, and Lena makes her own token.</p>
            <form id="setup">
              <label>Or a long-lived token (your profile → Security)<input name="ha_token" type="password" placeholder="${secret(PhoneFields.HA_TOKEN)}" autocomplete="off"></label>
              <h2>Open WebUI</h2>
              <label>Address<input name="owui" type="url" value="${value(PhoneFields.OWUI)}" placeholder="https://ai.example.com"></label>
              <label>Sign-in address (optional, e.g. Authentik)<input name="owui_sign_in" type="text" value="${value(PhoneFields.OWUI_SIGN_IN)}" placeholder="https://auth.example.com"></label>
              <label>Email<input name="owui_email" type="email" placeholder="${secret(PhoneFields.OWUI_EMAIL)}" autocomplete="username"></label>
              <label>Password<input name="owui_password" type="password" placeholder="${secret(PhoneFields.OWUI_PASSWORD)}" autocomplete="current-password"></label>
              <h2>Updates</h2>
              <label>Your Gitea (optional)<input name="gitea" type="url" value="${value(PhoneFields.GITEA)}" placeholder="https://gitea.example.com/you"></label>
              <h2>Motion sensors</h2>
              <label>Wakes the screensaver (optional)<input name="wake_sensor" list="sensors" value="${value(PhoneFields.WAKE_SENSOR)}" placeholder="binary_sensor.hallway_motion"></label>
              <label>Her briefing when someone comes in (optional)<input name="briefing_sensor" list="sensors" value="${value(PhoneFields.BRIEFING_SENSOR)}" placeholder="binary_sensor.kitchen_motion"></label>
              <datalist id="sensors">$sensors</datalist>
              <button type="submit">Send to $device</button>
            </form>
            <p id="status" role="status"></p>
            <p class="note">What you type is encrypted on this phone with a key from the QR code, so others on your Wi-Fi can't read it. Someone already inside your network who rewrites this page as it loads could still see it; for Home Assistant, signing in on its own page avoids that.</p>
            <script>${PHONE_CIPHER_JS}</script>
            <script>${PHONE_PAGE_JS.replace("CODE", code)}</script>
        """.trimIndent())
    }

    /** A whole page in Lena's colors, readable on a phone, with nothing loaded from elsewhere. */
    private fun page(title: String, content: String) = """
        <!doctype html><html lang="en"><head><meta charset="utf-8">
        <meta name="viewport" content="width=device-width,initial-scale=1"><title>${htmlEscape(title)}</title>
        <style>
          body{font-family:system-ui,sans-serif;background:#12131a;color:#e8e6f0;margin:0;padding:24px;line-height:1.5}
          main{max-width:440px;margin:0 auto} h1{font-size:1.4em} h2{font-size:1.1em;margin-top:28px} a{color:#b9a7ff}
          label{display:block;margin:16px 0 4px;color:#b8b4c8}
          input{display:block;width:100%;box-sizing:border-box;margin-top:6px;padding:12px;border-radius:10px;border:1px solid #444;background:#1d1f29;color:inherit;font-size:1em}
          button{margin-top:16px;width:100%;padding:14px;border:0;border-radius:24px;background:#b9a7ff;color:#12131a;font-size:1em;font-weight:600}
          .note{color:#9a96ab;font-size:.9em} #status{font-weight:600;margin-top:16px}
        </style></head><body><main><h1>${htmlEscape(title)}</h1>$content</main></body></html>
    """.trimIndent()

    private fun send(out: OutputStream, status: Int, html: String, cookie: String? = null) {
        val bytes = html.toByteArray(Charsets.UTF_8)
        val reason = if (status == 200) "OK" else "Not Found"
        // Lax, not Strict: Home Assistant's sign-in sends the phone back here from its own site.
        val setCookie = cookie?.let { "Set-Cookie: lena_setup=$it; Path=/$code/; HttpOnly; SameSite=Lax\r\n" } ?: ""
        out.write(("HTTP/1.1 $status $reason\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${bytes.size}\r\n" +
            setCookie + "Cache-Control: no-store\r\nReferrer-Policy: no-referrer\r\nConnection: close\r\n\r\n").toByteArray())
        out.write(bytes)
        out.flush()
    }

    /** An [Outcome] as the page's script reads it: {"state": ..., "message": ...}. */
    private fun sendJson(out: OutputStream, outcome: Outcome) {
        val json = "{\"state\":${jsonString(outcome.state)},\"message\":${jsonString(outcome.message)}}"
        val bytes = json.toByteArray(Charsets.UTF_8)
        out.write(("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\n" +
            "Cache-Control: no-store\r\nConnection: close\r\n\r\n").toByteArray())
        out.write(bytes)
        out.flush()
    }

    private fun redirect(out: OutputStream, location: String) {
        out.write(("HTTP/1.1 302 Found\r\nLocation: $location\r\nContent-Length: 0\r\nCache-Control: no-store\r\n" +
            "Connection: close\r\n\r\n").toByteArray())
        out.flush()
    }
}

/** The fields the phone page can fill in; the names it sends. */
object PhoneFields {
    const val OWUI = "owui"
    const val OWUI_SIGN_IN = "owui_sign_in"
    const val OWUI_EMAIL = "owui_email"
    const val OWUI_PASSWORD = "owui_password"
    const val HA = "ha"
    const val HA_TOKEN = "ha_token"
    const val GITEA = "gitea"
    const val WAKE_SENSOR = "wake_sensor"
    const val BRIEFING_SENSOR = "briefing_sensor"

    val ALL = listOf(OWUI, OWUI_SIGN_IN, OWUI_EMAIL, OWUI_PASSWORD, HA, HA_TOKEN, GITEA, WAKE_SENSOR, BRIEFING_SENSOR)

    /** Never sent to the page, only whether they're set; left empty on the page, they stay as they are. */
    val SECRETS = setOf(OWUI_EMAIL, OWUI_PASSWORD, HA_TOKEN)
}

/**
 * The page's script: reads the key from the address (after #), encrypts the form with it and sends it,
 * then shows what the device says. Plain JavaScript, no library; CODE is replaced with the page's code.
 * Empty secret fields are left out, so a saved password stays.
 */
private const val PHONE_PAGE_JS = """
(function () {
  var params = new URLSearchParams(location.hash.slice(1));
  var keyText = params.get('k');
  var status = document.getElementById('status');
  var form = document.getElementById('setup');
  var haField = document.getElementById('ha');
  document.getElementById('signin').onclick = function () {
    location.href = '/CODE/sign-in?ha=' + encodeURIComponent(haField.value);
  };
  if (!keyText) {
    document.getElementById('nokey').hidden = false;
    form.hidden = true;
    return;
  }
  var key = LenaCipher.fromBase64(keyText.replace(/-/g, '+').replace(/_/g, '/'));
  var secrets = ['ha_token', 'owui_email', 'owui_password'];
  function poll() {
    fetch('/CODE/status', { cache: 'no-store' }).then(function (r) { return r.json(); }).then(function (s) {
      status.textContent = s.message;
      if (s.state === 'waiting') setTimeout(poll, 1000);
    }).catch(function () { setTimeout(poll, 2000); });
  }
  form.onsubmit = function (event) {
    event.preventDefault();
    var values = new URLSearchParams();
    values.append('ha', haField.value);
    Array.prototype.forEach.call(form.elements, function (field) {
      if (!field.name) return;
      if (secrets.indexOf(field.name) >= 0 && !field.value) return;
      values.append(field.name, field.value);
    });
    var nonce = new Uint8Array(12);
    crypto.getRandomValues(nonce);
    var sealed = LenaCipher.seal(key, nonce, new TextEncoder().encode('CODE'), new TextEncoder().encode(values.toString()));
    status.textContent = 'Sending…';
    var body = new URLSearchParams();
    body.append('n', LenaCipher.toBase64(nonce));
    body.append('c', LenaCipher.toBase64(sealed));
    fetch('/CODE/save', { method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, body: body.toString() })
      .then(function (r) { return r.json(); })
      .then(function (s) { status.textContent = s.message; if (s.state === 'waiting') setTimeout(poll, 1000); })
      .catch(function () { status.textContent = 'Couldn\'t reach the device. Is the screen still open?'; });
  };
})();
"""

/**
 * ChaCha20-Poly1305 (RFC 8439) in plain JavaScript, for the phone page: a phone browser only offers its own
 * crypto on https pages, and this page is plain http on your network. Encrypt only (the device decrypts with
 * Android's). tools/pairing_check runs it in Node against Java's ChaCha20-Poly1305 on the same inputs.
 */
const val PHONE_CIPHER_JS = """
var LenaCipher = (function () {
  function rotl(v, c) { return (v << c) | (v >>> (32 - c)); }
  function quarter(s, a, b, c, d) {
    s[a] = (s[a] + s[b]) | 0; s[d] = rotl(s[d] ^ s[a], 16);
    s[c] = (s[c] + s[d]) | 0; s[b] = rotl(s[b] ^ s[c], 12);
    s[a] = (s[a] + s[b]) | 0; s[d] = rotl(s[d] ^ s[a], 8);
    s[c] = (s[c] + s[d]) | 0; s[b] = rotl(s[b] ^ s[c], 7);
  }
  function word(b, i) { return (b[i] | (b[i + 1] << 8) | (b[i + 2] << 16) | (b[i + 3] << 24)) >>> 0; }
  function block(key, counter, nonce) {
    var start = new Uint32Array(16);
    start[0] = 0x61707865; start[1] = 0x3320646e; start[2] = 0x79622d32; start[3] = 0x6b206574;
    for (var i = 0; i < 8; i++) start[4 + i] = word(key, i * 4);
    start[12] = counter >>> 0;
    for (i = 0; i < 3; i++) start[13 + i] = word(nonce, i * 4);
    var w = new Uint32Array(start);
    for (i = 0; i < 10; i++) {
      quarter(w, 0, 4, 8, 12); quarter(w, 1, 5, 9, 13); quarter(w, 2, 6, 10, 14); quarter(w, 3, 7, 11, 15);
      quarter(w, 0, 5, 10, 15); quarter(w, 1, 6, 11, 12); quarter(w, 2, 7, 8, 13); quarter(w, 3, 4, 9, 14);
    }
    var out = new Uint8Array(64);
    for (i = 0; i < 16; i++) {
      var v = (w[i] + start[i]) >>> 0;
      out[i * 4] = v & 255; out[i * 4 + 1] = (v >>> 8) & 255; out[i * 4 + 2] = (v >>> 16) & 255; out[i * 4 + 3] = (v >>> 24) & 255;
    }
    return out;
  }
  function number(bytes) {
    var x = BigInt(0);
    for (var i = bytes.length - 1; i >= 0; i--) x = (x << BigInt(8)) | BigInt(bytes[i]);
    return x;
  }
  function poly1305(key, msg) {
    var clamp = BigInt('0x0ffffffc0ffffffc0ffffffc0fffffff');
    var r = number(key.subarray(0, 16)) & clamp;
    var s = number(key.subarray(16, 32));
    var p = (BigInt(1) << BigInt(130)) - BigInt(5);
    var acc = BigInt(0);
    for (var i = 0; i < msg.length; i += 16) {
      var chunk = msg.subarray(i, Math.min(i + 16, msg.length));
      acc = ((acc + number(chunk) + (BigInt(1) << BigInt(8 * chunk.length))) * r) % p;
    }
    acc = (acc + s) & ((BigInt(1) << BigInt(128)) - BigInt(1));
    var tag = new Uint8Array(16);
    for (i = 0; i < 16; i++) { tag[i] = Number(acc & BigInt(255)); acc = acc >> BigInt(8); }
    return tag;
  }
  function padded(length) { return (16 - (length % 16)) % 16; }
  function seal(key, nonce, aad, plain) {
    var sealed = new Uint8Array(plain.length + 16);
    for (var at = 0; at < plain.length; at += 64) {
      var stream = block(key, 1 + at / 64, nonce);
      for (var i = 0; i < 64 && at + i < plain.length; i++) sealed[at + i] = plain[at + i] ^ stream[i];
    }
    var ciphertext = sealed.subarray(0, plain.length);
    var macData = new Uint8Array(aad.length + padded(aad.length) + plain.length + padded(plain.length) + 16);
    macData.set(aad, 0);
    var end = aad.length + padded(aad.length);
    macData.set(ciphertext, end);
    end += plain.length + padded(plain.length);
    var lengths = [aad.length, plain.length];
    for (var n = 0; n < 2; n++) {
      var len = lengths[n];
      for (i = 0; i < 8; i++) { macData[end + n * 8 + i] = len & 255; len = Math.floor(len / 256); }
    }
    sealed.set(poly1305(block(key, 0, nonce).subarray(0, 32), macData), plain.length);
    return sealed;
  }
  function toBase64(bytes) {
    var text = '';
    for (var i = 0; i < bytes.length; i++) text += String.fromCharCode(bytes[i]);
    return btoa(text);
  }
  function fromBase64(text) {
    while (text.length % 4) text += '=';
    var raw = atob(text);
    var bytes = new Uint8Array(raw.length);
    for (var i = 0; i < raw.length; i++) bytes[i] = raw.charCodeAt(i);
    return bytes;
  }
  return { seal: seal, toBase64: toBase64, fromBase64: fromBase64 };
})();
"""

/** Text as a JSON string, quoted. */
internal fun jsonString(text: String): String {
    val out = StringBuilder("\"")
    for (ch in text) {
        when {
            ch == '"' -> out.append("\\\"")
            ch == '\\' -> out.append("\\\\")
            ch < ' ' -> out.append("\\u%04x".format(ch.code))
            else -> out.append(ch)
        }
    }
    return out.append('"').toString()
}

/** Text made safe to put in a page. */
internal fun htmlEscape(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

/** "a=1&b=2" (a query, or a posted form) as a map, decoded. */
internal fun parseForm(encoded: String): Map<String, String> = encoded.split("&").filter { it.contains('=') }.associate {
    val (k, v) = it.split("=", limit = 2)
    URLDecoder.decode(k, "UTF-8") to URLDecoder.decode(v, "UTF-8")
}

/** "a=1&b=2" for these values, encoded. */
internal fun formOf(values: Map<String, String>): String =
    values.entries.joinToString("&") { URLEncoder.encode(it.key, "UTF-8") + "=" + URLEncoder.encode(it.value, "UTF-8") }

/** One line of an HTTP request or response, without its line ending; null at the end of the stream. */
private fun readLine(input: InputStream): String? {
    val line = StringBuilder()
    while (true) {
        val b = input.read()
        if (b == -1) return if (line.isEmpty()) null else line.toString()
        if (b == '\n'.code) return line.toString().trimEnd('\r')
        line.append(b.toChar())
        if (line.length > 16 * 1024) return null // nobody sends lines this long
    }
}

private fun readExactly(input: InputStream, count: Int): ByteArray {
    val bytes = ByteArray(count)
    var at = 0
    while (at < count) {
        val n = input.read(bytes, at, count - at)
        if (n == -1) break
        at += n
    }
    return bytes
}

/**
 * The smallest WebSocket client that can ask Home Assistant one thing: text messages only, sent and
 * received in turn. Home Assistant only gives out long-lived tokens over its WebSocket API (there's no
 * web address for it), and Lena has no WebSocket library, so it's written here (RFC 6455).
 *
 * wss:// checks the certificate and its name, as https does; ws:// is for tests only.
 */
class MiniWebSocket(url: String) : Closeable {
    private val socket: Socket
    private val input: InputStream
    private val output: OutputStream

    init {
        val uri = URI(url)
        val secure = uri.scheme == "wss"
        val port = if (uri.port > 0) uri.port else if (secure) 443 else 80
        socket = if (secure) {
            (SSLSocketFactory.getDefault().createSocket(uri.host, port) as SSLSocket).apply {
                // Check the certificate is for this name, as https does (sockets don't by themselves).
                sslParameters = sslParameters.apply { endpointIdentificationAlgorithm = "HTTPS" }
                startHandshake()
            }
        } else {
            Socket(uri.host, port)
        }
        socket.soTimeout = 20_000
        input = BufferedInputStream(socket.getInputStream())
        output = socket.getOutputStream()

        val key = Base64.getEncoder().encodeToString(ByteArray(16).also { SecureRandom().nextBytes(it) })
        val hostHeader = if (uri.port > 0) "${uri.host}:${uri.port}" else uri.host
        val path = (uri.rawPath ?: "/").ifEmpty { "/" } + (uri.rawQuery?.let { "?$it" } ?: "")
        output.write(("GET $path HTTP/1.1\r\nHost: $hostHeader\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n" +
            "Sec-WebSocket-Key: $key\r\nSec-WebSocket-Version: 13\r\n\r\n").toByteArray())
        output.flush()
        val status = readLine(input) ?: throw java.io.IOException("No answer from $url")
        if (!status.contains(" 101 ")) throw java.io.IOException("$url answered: $status")
        var accept: String? = null
        while (true) {
            val line = readLine(input) ?: throw java.io.IOException("$url closed the connection")
            if (line.isEmpty()) break
            if (line.lowercase().startsWith("sec-websocket-accept:")) accept = line.substringAfter(':').trim()
        }
        val expected = Base64.getEncoder().encodeToString(
            MessageDigest.getInstance("SHA-1").digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").toByteArray())
        )
        if (accept != expected) throw java.io.IOException("$url isn't a WebSocket")
    }

    /** Sends one text message (masked, as a client must). */
    fun send(text: String) {
        val payload = text.toByteArray(Charsets.UTF_8)
        writeFrame(0x1, payload)
    }

    /** The next text message, answering pings on the way. */
    fun receive(): String {
        val message = java.io.ByteArrayOutputStream()
        while (true) {
            val first = input.read()
            val second = input.read()
            if (first == -1 || second == -1) throw java.io.IOException("The connection closed")
            val fin = first and 0x80 != 0
            val opcode = first and 0x0F
            var length = (second and 0x7F).toLong()
            if (length == 126L) length = ((input.read() shl 8) or input.read()).toLong()
            else if (length == 127L) length = (0 until 8).fold(0L) { acc, _ -> (acc shl 8) or input.read().toLong() }
            val mask = if (second and 0x80 != 0) readExactly(input, 4) else null
            if (length > 1_000_000) throw java.io.IOException("Message too long")
            val payload = readExactly(input, length.toInt())
            if (mask != null) for (i in payload.indices) payload[i] = (payload[i].toInt() xor mask[i % 4].toInt()).toByte()
            when (opcode) {
                0x8 -> throw java.io.IOException("The connection closed")
                0x9 -> writeFrame(0xA, payload) // ping: pong
                0xA -> Unit
                0x0, 0x1 -> {
                    message.write(payload)
                    if (fin) return message.toString("UTF-8")
                }
            }
        }
    }

    /** One frame; synchronized, as camera mode sends from more than one thread. */
    @Synchronized
    private fun writeFrame(opcode: Int, payload: ByteArray) {
        val frame = java.io.ByteArrayOutputStream()
        frame.write(0x80 or opcode)
        when {
            payload.size < 126 -> frame.write(0x80 or payload.size)
            payload.size < 65536 -> {
                frame.write(0x80 or 126)
                frame.write(payload.size ushr 8)
                frame.write(payload.size and 0xFF)
            }
            else -> {
                frame.write(0x80 or 127)
                for (i in 7 downTo 0) frame.write(((payload.size.toLong() ushr (8 * i)) and 0xFF).toInt())
            }
        }
        val mask = ByteArray(4).also { SecureRandom().nextBytes(it) }
        frame.write(mask)
        for (i in payload.indices) frame.write(payload[i].toInt() xor mask[i % 4].toInt())
        output.write(frame.toByteArray())
        output.flush()
    }

    override fun close() {
        runCatching { writeFrame(0x8, ByteArray(0)) }
        runCatching { socket.close() }
    }
}
