// Checks "Set up from your phone" away from Android: the QR code Lena draws (Qr.kt) and the page the device
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

/** One request as a phone's browser would make it, with the page's cookie: status, Location, the page, and a new cookie. */
private fun fetchWithCookie(url: String, post: String? = null, cookie: String? = null): List<String?> {
    val conn = URL(url).openConnection() as HttpURLConnection
    conn.instanceFollowRedirects = false
    if (cookie != null) conn.setRequestProperty("Cookie", "lena_setup=$cookie")
    if (post != null) {
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        conn.outputStream.use { it.write(post.toByteArray()) }
    }
    val code = conn.responseCode
    val body = runCatching { (if (code < 400) conn.inputStream else conn.errorStream)?.bufferedReader()?.readText() }.getOrNull() ?: ""
    val newCookie = conn.getHeaderField("Set-Cookie")?.substringAfter("lena_setup=")?.substringBefore(';')
    return listOf(code.toString(), conn.getHeaderField("Location"), body, newCookie)
}

/** The same, without a new cookie: status, Location, and the page. */
private fun fetch(url: String, post: String? = null, cookie: String? = null): Triple<Int, String?, String> {
    val (code, location, body) = fetchWithCookie(url, post, cookie)
    return Triple(code!!.toInt(), location, body ?: "")
}

/**
 * Runs the phone page's ChaCha20-Poly1305 (PHONE_CIPHER_JS) in Node on inputs of several lengths, and checks
 * each against Java's own ChaCha20-Poly1305 on the same key, nonce and data. Skipped where Node isn't installed.
 */
private fun cipherInNode() {
    val node = listOf("node", "nodejs").firstOrNull { runCatching { ProcessBuilder(it, "--version").start().waitFor() == 0 }.getOrDefault(false) }
    if (node == null) {
        println("skip  the page's cipher against Java's (Node isn't installed here)")
        return
    }
    val random = java.util.Random(8439)
    val cases = listOf(0, 1, 15, 16, 17, 63, 64, 65, 200, 1000).map { size ->
        val key = ByteArray(32).also { random.nextBytes(it) }
        val nonce = ByteArray(12).also { random.nextBytes(it) }
        val aad = ByteArray(size % 23).also { random.nextBytes(it) }
        val plain = ByteArray(size).also { random.nextBytes(it) }
        listOf(key, nonce, aad, plain)
    }
    val hex = { b: ByteArray -> b.joinToString("") { "%02x".format(it) } }
    val script = StringBuilder(io.github.soullink9.lena.PHONE_CIPHER_JS)
    script.append("\nfunction h(s) { return Uint8Array.from((s.match(/../g) || []).map(function (x) { return parseInt(x, 16); })); }\n")
    for ((key, nonce, aad, plain) in cases) {
        script.append("console.log(Buffer.from(LenaCipher.seal(h('${hex(key)}'), h('${hex(nonce)}'), h('${hex(aad)}'), h('${hex(plain)}'))).toString('hex'));\n")
    }
    val file = java.io.File.createTempFile("lena-cipher", ".js").apply { writeText(script.toString()); deleteOnExit() }
    val process = ProcessBuilder(node, file.path).redirectErrorStream(true).start()
    val lines = process.inputStream.bufferedReader().readLines()
    process.waitFor()
    val expected = cases.map { (key, nonce, aad, plain) -> hex(PairingServer.encrypt(key, nonce, aad, plain)) }
    check(lines == expected, "the page's ChaCha20-Poly1305 (JavaScript) matches Java's on 10 inputs from 0 to 1000 bytes")
}

private fun title(page: String) = Regex("<h1>([^<]*)</h1>").find(page)?.groupValues?.get(1)

fun main() {
    for ((text, version, expected) in READ_BACK) {
        val m = Qr.encode(text)
        check(m.size == version * 4 + 17 && fingerprint(m) == expected, "QR code for \"${text.take(40)}\" is version $version, as read back by a QR reader")
    }
    check(runCatching { Qr.encode("x".repeat(200)) }.isFailure, "text too long for a QR code here is refused, not drawn wrong")

    var signedIn: String? = null
    var proposed: Map<String, String>? = null
    var phoneArrived = false
    val server = PairingServer(object : PairingServer.Handler {
        override fun known() = PairingServer.Known(
            addresses = mapOf("ha" to "https://ha.example.com/", "owui" to "https://ai.example.com/"),
            saved = setOf("owui_password"), sensors = listOf("binary_sensor.hallway_motion"), device = "this TV",
        )
        override fun signInPage(address: String, clientId: String, redirectUri: String, state: String) =
            "https://ha.example.com/auth/authorize?client_id=$clientId&redirect_uri=$redirectUri&state=$state"
        override fun signedIn(address: String, code: String, clientId: String): String {
            signedIn = "$address $code $clientId"
            return "Lena on this TV is connected to your Home Assistant."
        }
        override fun phoneConnected() { phoneArrived = true }
        override fun propose(changes: Map<String, String>, answer: (PairingServer.Outcome) -> Unit) {
            proposed = changes
            answer(PairingServer.Outcome("saved", "Saved on this TV."))
        }
    })
    server.start("127.0.0.1")
    val page = server.address
    val base = page.removeSuffix("/")
    check(page.matches(Regex("http://127\\.0\\.0\\.1:\\d+/[A-Z2-9]{6}/")) && page.contains(server.code), "the address carries the code shown on screen ($page)")
    check(server.addressWithKey.startsWith("$page#k=") && Qr.encode(server.addressWithKey.replace("127.0.0.1", "lena-living-room-tv.local")).size <= 41,
        "the QR code's address carries the key after #, and fits a QR code Lena draws")
    check(fetch(page.replace(server.code, "AAAAAA")).first == 404, "an address without the right code gets nothing")
    val (_, _, formOrNull, cookie) = fetchWithCookie(page)
    val form = formOrNull ?: ""
    check(title(form) == "Set up Lena" && form.contains("value=\"https://ha.example.com/\"") && form.contains("value=\"https://ai.example.com/\"") &&
        form.contains("Saved: leave empty to keep it") && !form.contains("hunter2") && phoneArrived,
        "the page fills in addresses, says which secrets are saved without sending them, and the device knows a phone arrived")
    check(cookie != null && title(fetch(page).third) == "Already in use", "the first phone holds the page; another is told it's in use")

    val (status, location, _) = fetch("$base/sign-in?ha=https%3A%2F%2Fha.example.com%2F", cookie = cookie)
    val state = location?.substringAfter("state=")
    check(status == 302 && location!!.startsWith("https://ha.example.com/auth/authorize?client_id=$page&redirect_uri=${page}done"),
        "Sign in sends the phone to Home Assistant, with this page as the app and where to come back")
    check(title(fetch("$base/done?code=abc&state=forged", cookie = cookie).third) == "Start again" && signedIn == null, "a reply that wasn't started here is refused")
    check(title(fetch("$base/done?code=abc123&state=$state", cookie = cookie).third) == "Home Assistant connected" &&
        signedIn == "https://ha.example.com/ abc123 $page" && !server.done,
        "Home Assistant's code is passed on with the address and client id, and the page stays open for the rest")

    // The phone's side of a save: the form encrypted with the key from the QR code.
    val fields = "ha=https%3A%2F%2Fha.example.com%2F&owui_password=hunter2&gitea=https%3A%2F%2Fgitea.example.com%2Fyou&nonsense=1"
    val nonce = ByteArray(12) { it.toByte() }
    val sealed = PairingServer.encrypt(server.key, nonce, server.code.toByteArray(), fields.toByteArray())
    val b64 = java.util.Base64.getEncoder()
    val body = "n=" + java.net.URLEncoder.encode(b64.encodeToString(nonce), "UTF-8") + "&c=" + java.net.URLEncoder.encode(b64.encodeToString(sealed), "UTF-8")
    val wrongKey = PairingServer.encrypt(ByteArray(32), ByteArray(12) { 9 }, server.code.toByteArray(), fields.toByteArray())
    val forged = "n=" + java.net.URLEncoder.encode(b64.encodeToString(ByteArray(12) { 9 }), "UTF-8") + "&c=" + java.net.URLEncoder.encode(b64.encodeToString(wrongKey), "UTF-8")
    check(fetch("$base/save", forged, cookie).third.contains("\"failed\"") && proposed == null, "a save not encrypted with the page's key is refused")
    check(fetch("$base/save", body).third.let { title(it) == "Already in use" } && proposed == null, "a save from another phone is refused")
    val saved = fetch("$base/save", body, cookie).third
    check(saved.contains("\"waiting\"") && proposed == mapOf("ha" to "https://ha.example.com/", "owui_password" to "hunter2", "gitea" to "https://gitea.example.com/you"),
        "an encrypted save reaches the device, only the page's fields, waiting for OK there")
    check(fetch("$base/status", cookie = cookie).third.contains("\"saved\"") && server.done, "the phone sees it saved, and the page is done")
    check(fetch("$base/save", body, cookie).third.let { title(it) == "Saved" }, "once saved, the page does nothing more")
    server.close()

    // Five requests that guess a code close the page; a browser's own requests (its icon) don't count.
    val guessed = PairingServer(object : PairingServer.Handler {
        override fun known() = PairingServer.Known(emptyMap(), emptySet(), emptyList(), "this TV")
        override fun signInPage(address: String, clientId: String, redirectUri: String, state: String) = ""
        override fun signedIn(address: String, code: String, clientId: String) = ""
        override fun propose(changes: Map<String, String>, answer: (PairingServer.Outcome) -> Unit) {}
    })
    guessed.start("127.0.0.1")
    repeat(3) { fetch(guessed.address.substringBefore(guessed.code) + "favicon.ico") }
    check(!guessed.closedForWrongCodes, "a browser asking for its icon doesn't count as guessing")
    repeat(PairingServer.WRONG_CODES) { runCatching { fetch(guessed.address.replace(guessed.code, "ZZZZZ" + ('A' + it))) } }
    Thread.sleep(200)
    check(guessed.closedForWrongCodes && runCatching { fetch(guessed.address) }.isFailure, "five guessed codes close the page")

    // The page's own cipher (JavaScript, run in Node when it's here) against Java's ChaCha20-Poly1305.
    cipherInNode()

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
    println("Set up from your phone works as expected.")
}
