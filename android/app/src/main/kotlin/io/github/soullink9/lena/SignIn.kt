package io.github.soullink9.lena

import android.content.Context
import android.webkit.CookieManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Signing in to Open WebUI again by itself. When Open WebUI restarts it can end every session
 * (it does whenever its secret key changes), which would leave a wall tablet, scheduled prompts
 * and alerts signed out until someone typed a password. With this on, Lena signs in with the
 * details saved in Settings → Open WebUI, the same way the sign-in page does, and puts the new
 * session where the web view keeps it. The details are kept encrypted with a key that never
 * leaves the device (like the Home Assistant token) and are sent only to your Open WebUI.
 *
 * Single sign-on (Authentik, Google and the like) can't be done this way; it needs you.
 */
object OpenWebUiSignIn {
    const val USER = "owui_user"
    const val PASSWORD = "owui_password"
    private const val TAG = "Sign-in"
    private val lock = Mutex()
    @Volatile private var lastTry = 0L

    fun configured(c: Context): Boolean =
        Prefs.autoSignIn(c) != AutoSignIn.OFF && Secrets.has(c, USER) && Secrets.has(c, PASSWORD)

    /** A JWT, nothing else: it's handed to the page's script, so nothing that could break out of a string. */
    private fun safe(token: String) = token.matches(Regex("[A-Za-z0-9._\\-]{20,4096}"))

    /**
     * Signs in with the saved details and keeps the new session. Returns the session token, or
     * null if it's off, failed, or was tried less than half a minute ago (so a wrong password
     * can't turn into a loop).
     */
    suspend fun signIn(c: Context, force: Boolean = false): String? = lock.withLock {
        if (!configured(c)) return null
        if (!force && System.currentTimeMillis() - lastTry < 30_000) return null
        lastTry = System.currentTimeMillis()
        val server = Prefs.server(c)?.trimEnd('/') ?: return null
        try {
            val token = request(c, server)
            withContext(Dispatchers.Main) {
                val cookies = CookieManager.getInstance()
                cookies.setCookie(server, "token=$token; Path=/; Secure; HttpOnly; SameSite=Lax")
                cookies.flush()
            }
            Logs.i(TAG, "Signed in to Open WebUI again by itself")
            token
        } catch (e: Exception) {
            Logs.w(TAG, "Couldn't sign in to Open WebUI by itself: ${e.message}")
            null
        }
    }

    /** For Settings: tries the saved details now and says how it went. */
    suspend fun test(c: Context): String {
        if (!configured(c)) return "Choose how, and fill in both fields first."
        val server = Prefs.server(c)?.trimEnd('/') ?: return "Set Open WebUI's address first."
        return try {
            request(c, server)
            "Signed in. She'll do this by herself whenever Open WebUI ends her session."
        } catch (e: IOException) {
            e.message ?: "Couldn't sign in."
        }
    }

    private suspend fun request(c: Context, server: String): String {
        val method = Prefs.autoSignIn(c)
        val user = Secrets.get(c, USER) ?: throw IOException("No saved sign-in.")
        val password = Secrets.get(c, PASSWORD) ?: throw IOException("No saved sign-in.")
        val (path, body) = when (method) {
            AutoSignIn.LDAP -> "/api/v1/auths/ldap" to JSONObject().put("user", user).put("password", password)
            else -> "/api/v1/auths/signin" to JSONObject().put("email", user).put("password", password)
        }
        // A sign-in proxy's own cookie for your server goes along, as the web view would send it.
        val cookies = withContext(Dispatchers.Main) { CookieManager.getInstance().getCookie(server) }
        return withContext(Dispatchers.IO) {
            val conn = URL(server + path).openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "POST"
                conn.instanceFollowRedirects = false
                conn.connectTimeout = 10_000
                conn.readTimeout = 30_000
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Accept", "application/json")
                cookies?.split(';')?.map { it.trim() }?.filter { !it.startsWith("token=") }?.joinToString("; ")
                    ?.ifBlank { null }?.let { conn.setRequestProperty("Cookie", it) }
                conn.outputStream.use { it.write(body.toString().toByteArray()) }
                val code = conn.responseCode
                if (code in 300..399) throw IOException("A sign-in page is in front of Open WebUI, so Lena can't sign in by herself.")
                if (code == 400 || code == 401 || code == 403) throw IOException("Open WebUI didn't accept that email (or user name) and password.")
                if (code != 200) throw IOException("Open WebUI answered HTTP $code.")
                val token = JSONObject(conn.inputStream.use { String(it.readBytes()) }).optString("token")
                if (!safe(token)) throw IOException("Open WebUI's answer had no session in it.")
                token
            } finally {
                conn.disconnect()
            }
        }
    }
}
