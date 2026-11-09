// Checks "Connect from your phone" away from Android: the QR code Lena draws (Qr.kt) and the page the device
// serves (Pairing.kt), the way a phone uses it. Run by run.sh next to this file.
import io.github.soullink9.lena.MiniWebSocket
import io.github.soullink9.lena.PairingServer
import io.github.soullink9.lena.Qr
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URL
import java.security.MessageDigest

private var failures = 0

private fun check(ok: Boolean, what: String) {
    println((if (ok) "ok    " else "FAIL  ") + what)
    if (!ok) failures++
}

/**
 * QR codes whose drawing was read back by a QR reader (OpenCV) when Qr.kt was written: a change to Qr.kt
 * that alters them needs checking with a phone's camera again, and new fingerprints here.
 */
private val READ_BACK = listOf(
    Triple("Lena", 1, "c3bd954d3367023fb24e522c8892a384c0a0e71c09c3ea6945e1abb61fd4e452"),
    Triple("http://lena-tv.local:8765/K7Q2XM/", 3, "1a35d0c4312dbba68171de268fa49684e09aa1238193274520f61800ad1b0ba0"),
    Triple("https://ha.example.com/auth/authorize?x=1", 3, "d38c6cde601ca2756539dfc9f4af953c417cc9ce6ecb3f3ffa2d3d41f88bd76a"),
    Triple("http://lena-living-room-tv.local:8765/ABCDEFGH/connect-from-your-phone", 5, "92a6fc208b9d75428d232c234b902f020de32abac53b9f263692b859685cdb64"),
    Triple("Lena on the Shield ✓ " + "y".repeat(70), 6, "b0fe5ab7672179d95e57bc8b27567913b89eab39eee2919a16fc73c43608dce8"),
)

private fun fingerprint(m: Array<BooleanArray>): String {
    val text = m.joinToString("\n") { row -> row.joinToString("") { if (it) "1" else "0" } }
    return MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
}

/** One request as a phone's browser would make it: status, Location, and the page. */
private fun fetch(url: String, post: String? = null): Triple<Int, String?, String> {
    val conn = URL(url).openConnection() as HttpURLConnection
    conn.instanceFollowRedirects = false
    if (post != null) {
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        conn.outputStream.use { it.write(post.toByteArray()) }
    }
    val code = conn.responseCode
    val body = runCatching { (if (code < 400) conn.inputStream else conn.errorStream)?.bufferedReader()?.readText() }.getOrNull() ?: ""
    return Triple(code, conn.getHeaderField("Location"), body)
}

private fun title(page: String) = Regex("<h1>([^<]*)</h1>").find(page)?.groupValues?.get(1)

fun main() {
    for ((text, version, expected) in READ_BACK) {
        val m = Qr.encode(text)
        check(m.size == version * 4 + 17 && fingerprint(m) == expected, "QR code for \"${text.take(40)}\" is version $version, as read back by a QR reader")
    }
    check(runCatching { Qr.encode("x".repeat(200)) }.isFailure, "text too long for a QR code here is refused, not drawn wrong")

    var signedIn: String? = null
    val server = PairingServer(object : PairingServer.Handler {
        override fun knownAddress() = "https://ha.example.com/"
        override fun signInPage(address: String, clientId: String, redirectUri: String, state: String) =
            "https://ha.example.com/auth/authorize?client_id=$clientId&redirect_uri=$redirectUri&state=$state"
        override fun signedIn(address: String, code: String, clientId: String): String {
            signedIn = "$address $code $clientId"
            return "Connected."
        }
        override fun pastedToken(address: String, token: String): String =
            if (token == "good") "Connected." else throw IllegalStateException("Home Assistant didn't accept that token.")
    })
    server.start("127.0.0.1")
    val page = server.address
    val base = page.removeSuffix("/")
    check(page.matches(Regex("http://127\\.0\\.0\\.1:\\d+/[A-Z2-9]{6}/")) && page.contains(server.code), "the address carries the code shown on screen ($page)")
    check(fetch(page.replace(server.code, "AAAAAA")).first == 404, "an address without the right code gets nothing")
    val form = fetch(page).third
    check(title(form) == "Connect Lena to Home Assistant" && form.contains("value=\"https://ha.example.com/\""), "the page asks to sign in, with the known address filled in")

    val (status, location, _) = fetch("$base/sign-in?ha=https%3A%2F%2Fha.example.com%2F")
    val state = location?.substringAfter("state=")
    check(status == 302 && location!!.startsWith("https://ha.example.com/auth/authorize?client_id=$page&redirect_uri=${page}done"),
        "Sign in sends the phone to Home Assistant, with this page as the app and where to come back")
    check(title(fetch("$base/done?code=abc&state=forged").third) == "Start again" && signedIn == null, "a reply that wasn't started here is refused")
    check(title(fetch("$base/token", "ha=https%3A%2F%2Fha.example.com%2F&token=bad").third) == "That didn't work", "a token Home Assistant refuses: the page says why")
    check(title(fetch("$base/done?code=abc123&state=$state").third) == "Connected" && signedIn == "https://ha.example.com/ abc123 $page" && server.done,
        "Home Assistant's code is passed on with the address and client id, and the TV knows it's done")
    check(title(fetch(page).third) == "Already connected", "once connected, the page does nothing more")
    server.close()

    // The WebSocket client, against a tiny server that answers like Home Assistant's first message.
    val listener = ServerSocket(0)
    Thread {
        listener.accept().use { c ->
            val input = c.getInputStream().bufferedReader(Charsets.ISO_8859_1)
            var key = ""
            while (true) {
                val line = input.readLine() ?: break
                if (line.isEmpty()) break
                if (line.lowercase().startsWith("sec-websocket-key:")) key = line.substringAfter(':').trim()
            }
            val accept = java.util.Base64.getEncoder().encodeToString(
                MessageDigest.getInstance("SHA-1").digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").toByteArray()))
            val out = c.getOutputStream()
            out.write("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: $accept\r\n\r\n".toByteArray())
            val hello = """{"type":"auth_required"}""".toByteArray()
            out.write(byteArrayOf(0x81.toByte(), hello.size.toByte()) + hello)
            out.flush()
            Thread.sleep(500)
        }
    }.start()
    val ws = MiniWebSocket("ws://127.0.0.1:${listener.localPort}/api/websocket")
    check(ws.receive() == """{"type":"auth_required"}""", "the WebSocket client connects and reads Home Assistant's first message")
    ws.close()

    println()
    if (failures > 0) {
        println("$failures check(s) failed.")
        kotlin.system.exitProcess(1)
    }
    println("Connect from your phone works as expected.")
}
