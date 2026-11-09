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
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * "Connect from your phone": a small web page this device serves on your home network for a few minutes,
 * so Home Assistant can be connected from your phone's browser instead of typing a long token with a TV
 * remote ([HaPairActivity] shows its address and a QR code).
 *
 * The page offers two ways:
 * - **Sign in with Home Assistant.** Your phone goes to your Home Assistant's own sign-in page (your
 *   password manager works there), which sends it back here with a one-time code. This device swaps the
 *   code for a sign-in and asks Home Assistant for a long-lived token named after it, the same kind you'd
 *   make in your profile ([HaSignIn]).
 * - **Paste a long-lived token**, made in your profile on the phone.
 *
 * Every address carries a random code that's also on the TV's screen, so nothing else on the network can
 * use the page; it stops when the screen closes, after it's done, or after [LIFETIME_MS]. The token only
 * ever travels between this device and your Home Assistant (and, if you paste one, from your phone).
 *
 * Plain Kotlin and java.net only, so it can be checked away from Android.
 */
class PairingServer(private val handler: Handler) : Closeable {

    /** What the page does; [HaPairActivity] answers, off the main thread. */
    interface Handler {
        /** The Home Assistant address to fill in on the page (the one already set, if any). */
        fun knownAddress(): String

        /**
         * Where to send the phone to sign in, for the address typed on it: Home Assistant's sign-in page
         * with this page as the app asking ([clientId]) and where to come back to ([redirectUri]). An
         * exception's message is shown on the phone.
         */
        fun signInPage(address: String, clientId: String, redirectUri: String, state: String): String

        /** Finishes signing in with Home Assistant's one-time [code]; returns what to tell the phone. */
        fun signedIn(address: String, code: String, clientId: String): String

        /** Uses a pasted token; returns what to tell the phone. */
        fun pastedToken(address: String, token: String): String
    }

    companion object {
        /** The port to try first, so the address stays the same from one time to the next. */
        const val PORT = 8765

        /** How long the page is served at most. */
        const val LIFETIME_MS = 15 * 60_000L

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
    }

    /** The code in every address, also shown on the screen. */
    val code: String = SecureRandom().let { random -> (1..6).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("") }

    /** The sign-in being waited for: Home Assistant sends this back, so an old or forged reply is refused. */
    private val state: String = SecureRandom().let { random -> (1..24).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("") }

    /** The address being signed in to, between leaving for Home Assistant's page and coming back. */
    @Volatile private var signingInTo: String? = null

    private var socket: ServerSocket? = null
    @Volatile private var host: String = "127.0.0.1"
    private val started = System.currentTimeMillis()

    /** Whether it's done (connected), after which it answers no more. */
    @Volatile var done = false
        private set

    /** The page's address for the phone, once started. */
    val address: String get() = "http://$host:${socket?.localPort ?: PORT}/$code/"

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
                Thread({ client.use { answer(it) } }, "Lena pairing request").apply { isDaemon = true }.start()
            }
            runCatching { server.close() }
        }, "Lena pairing").apply { isDaemon = true }.start()
    }

    override fun close() {
        runCatching { socket?.close() }
    }

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
        val target = URI(parts[1])
        val length = headers["content-length"]?.toIntOrNull()?.coerceAtMost(64 * 1024) ?: 0
        val body = if (length > 0) String(readExactly(input, length), Charsets.UTF_8) else ""
        val query = parseForm(target.rawQuery ?: "") + if (method == "POST") parseForm(body) else emptyMap()

        val prefix = "/$code/"
        val path = target.rawPath ?: "/"
        if (!path.startsWith(prefix) && path != "/$code") return send(out, 404, page("Not found", "<p>This address isn't open. Use the one on the TV.</p>"))
        if (done) return send(out, 200, page("Already connected", "<p>Lena is connected to Home Assistant. You can close this page.</p>"))
        val clientId = "http://$host:${socket?.localPort ?: PORT}$prefix"
        val result = runCatching {
            when (path.removePrefix(prefix).removePrefix("/$code")) {
                "" -> form()
                "sign-in" -> {
                    val ha = query["ha"].orEmpty()
                    signingInTo = ha
                    return redirect(out, handler.signInPage(ha, clientId, clientId + "done", state))
                }
                "done" -> {
                    val ha = signingInTo
                    when {
                        query["state"] != state || ha == null -> page("Start again", "<p>That sign-in wasn't started from this page. Go back to the TV's address and sign in again.</p>")
                        query["code"].isNullOrBlank() -> page("Not signed in", "<p>Home Assistant didn't sign you in. ${htmlEscape(query["error"] ?: "")}</p>$back")
                        else -> success(handler.signedIn(ha, query["code"]!!, clientId))
                    }
                }
                "token" -> if (method != "POST") form() else success(handler.pastedToken(query["ha"].orEmpty(), query["token"].orEmpty()))
                else -> page("Not found", "<p>There's nothing here. Use the address on the TV.</p>")
            }
        }.getOrElse { page("That didn't work", "<p>${htmlEscape(it.message ?: "Something went wrong.")}</p>$back") }
        send(out, 200, result)
    }

    private val back get() = "<p><a href=\"/$code/\">Try again</a></p>"

    /** The finished page; the TV sees [done] and closes its screen. */
    private fun success(message: String): String {
        done = true
        return page("Connected", "<p>${htmlEscape(message)}</p><p>You can close this page; the TV carries on by itself.</p>")
    }

    /** The two ways to connect: signing in (first, the easy one) or pasting a token. */
    private fun form(): String {
        val ha = htmlEscape(handler.knownAddress())
        return page("Connect Lena to Home Assistant", """
            <p>Lena on the TV needs your Home Assistant. Sign in from this phone and she makes her own token.</p>
            <form action="/$code/sign-in" method="get">
              <label>Your Home Assistant's address<input name="ha" type="url" value="$ha" placeholder="https://ha.example.com" required autocomplete="url"></label>
              <button type="submit">Sign in with Home Assistant</button>
            </form>
            <details>
              <summary>Or paste a long-lived token</summary>
              <form action="/$code/token" method="post">
                <label>Your Home Assistant's address<input name="ha" type="url" value="$ha" placeholder="https://ha.example.com" required></label>
                <label>Long-lived access token (your profile → Security)<input name="token" type="password" required autocomplete="off"></label>
                <button type="submit">Use this token</button>
              </form>
            </details>
        """.trimIndent())
    }

    /** A whole page in Lena's colours, readable on a phone, with nothing loaded from elsewhere. */
    private fun page(title: String, content: String) = """
        <!doctype html><html lang="en"><head><meta charset="utf-8">
        <meta name="viewport" content="width=device-width,initial-scale=1"><title>${htmlEscape(title)}</title>
        <style>
          body{font-family:system-ui,sans-serif;background:#12131a;color:#e8e6f0;margin:0;padding:24px;line-height:1.5}
          main{max-width:440px;margin:0 auto} h1{font-size:1.4em} a{color:#b9a7ff}
          label{display:block;margin:16px 0 4px;color:#b8b4c8}
          input{display:block;width:100%;box-sizing:border-box;margin-top:6px;padding:12px;border-radius:10px;border:1px solid #444;background:#1d1f29;color:inherit;font-size:1em}
          button{margin-top:16px;width:100%;padding:14px;border:0;border-radius:24px;background:#b9a7ff;color:#12131a;font-size:1em;font-weight:600}
          details{margin-top:28px} summary{cursor:pointer;color:#b8b4c8}
        </style></head><body><main><h1>${htmlEscape(title)}</h1>$content</main></body></html>
    """.trimIndent()

    private fun send(out: OutputStream, status: Int, html: String) {
        val bytes = html.toByteArray(Charsets.UTF_8)
        val reason = if (status == 200) "OK" else "Not Found"
        out.write(("HTTP/1.1 $status $reason\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${bytes.size}\r\n" +
            "Cache-Control: no-store\r\nReferrer-Policy: no-referrer\r\nConnection: close\r\n\r\n").toByteArray())
        out.write(bytes)
        out.flush()
    }

    private fun redirect(out: OutputStream, location: String) {
        out.write(("HTTP/1.1 302 Found\r\nLocation: $location\r\nContent-Length: 0\r\nCache-Control: no-store\r\n" +
            "Connection: close\r\n\r\n").toByteArray())
        out.flush()
    }
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
