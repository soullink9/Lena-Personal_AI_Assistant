package io.github.soullink9.lena

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Home Assistant turned the token down: it was revoked, or it's wrong. */
class HaAuthException : IOException("Home Assistant token rejected")

/** How Lena feels right now, from her helpers in Home Assistant. */
data class Feelings(val mood: String, val moodWhy: String, val energy: String, val moment: String?, val momentWhy: String)

/** The weather at home, from the weather entity chosen in the Lena integration. */
data class Weather(val condition: String, val temperature: Double?, val unit: String)

/** How she feels and the weather, together, for the display. */
data class HouseNow(val feelings: Feelings?, val weather: Weather?)

/** A folder or photo in Home Assistant's media (Immich albums, My media...). */
data class MediaItem(val id: String, val title: String, val folder: Boolean, val photo: Boolean)

/** One item on her shared memory list. */
data class HouseMemory(val uid: String, val text: String)

/**
 * The optional link to your Home Assistant, where Lena's feelings and shared memory live.
 *
 * Uses a long-lived access token you paste into settings. It's stored encrypted with a key that
 * never leaves the phone's keystore, and sent only to the Home Assistant address you enter.
 */
class HomeAssistant(server: String, private val token: String) {

    private val base = server.trimEnd('/')

    companion object {
        /** The neutral moment, when nothing in particular just happened. */
        const val NO_MOMENT = "Nothing"

        /** Null until both an address and a token are set. */
        fun from(c: Context): HomeAssistant? {
            val url = Prefs.haServer(c) ?: return null
            val token = Secrets.get(c, Secrets.HA_TOKEN) ?: return null
            return HomeAssistant(url, token)
        }
    }

    private suspend fun call(method: String, path: String, body: JSONObject? = null): String = withContext(Dispatchers.IO) {
        val conn = URL(base + path).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 8_000
            conn.readTimeout = 30_000 // a large album takes a moment to list
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Accept", "application/json")
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(body.toString().toByteArray()) }
            }
            val code = try {
                conn.responseCode
            } catch (e: IOException) {
                Logs.w("Home Assistant", "$method ${path.substringBefore('?')}: couldn't connect", e)
                throw e
            }
            if (code == 401 || code == 403) {
                Logs.w("Home Assistant", "$method ${path.substringBefore('?')}: the token wasn't accepted (HTTP $code)")
                throw HaAuthException()
            }
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.use { String(it.readBytes()) } ?: ""
            if (code !in 200..299) {
                Logs.w("Home Assistant", "$method ${path.substringBefore('?')}: HTTP $code ${text.take(200)}")
                throw IOException("HTTP $code")
            }
            text
        } finally {
            conn.disconnect()
        }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    /** A file from Home Assistant itself (only paths on it, so the token never goes elsewhere). */
    suspend fun bytes(path: String): ByteArray = withContext(Dispatchers.IO) {
        require(path.startsWith("/") && !path.startsWith("//")) { "Not a Home Assistant path" }
        val conn = URL(base + path).openConnection() as HttpURLConnection
        try {
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 8_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("Authorization", "Bearer $token")
            val code = conn.responseCode
            if (code == 401 || code == 403) throw HaAuthException()
            if (code !in 200..299) {
                Logs.w("Home Assistant", "GET ${path.substringBefore('?')}: HTTP $code")
                throw IOException("HTTP $code")
            }
            conn.inputStream.use { it.readBytes() }
        } finally {
            conn.disconnect()
        }
    }

    /** A folder of Home Assistant's media, through the Lena integration (null id: the top level). */
    suspend fun browse(id: String?): Pair<String, List<MediaItem>> {
        val r = JSONObject(call("GET", "/api/lena/media" + if (id != null) "?id=${enc(id)}" else ""))
        val a = r.optJSONArray("children") ?: JSONArray()
        return r.optString("title") to List(a.length()) { i ->
            val o = a.getJSONObject(i)
            MediaItem(o.optString("id"), o.optString("title"), o.optBoolean("folder"), o.optBoolean("photo"))
        }
    }

    /** The photos in a folder, as paths on Home Assistant. */
    suspend fun photos(id: String): List<String> {
        val a = JSONObject(call("GET", "/api/lena/media?id=${enc(id)}&photos=1")).optJSONArray("photos") ?: JSONArray()
        return List(a.length()) { a.getString(it) }.filter { it.startsWith("/") && !it.startsWith("//") }
    }

    /** True when the address and token work. */
    suspend fun check(): Boolean = runCatching { call("GET", "/api/") }.isSuccess

    /**
     * How she feels, from the Lena integration's /api/lena/context, or null if the integration
     * isn't set up.
     */
    suspend fun feelings(): Feelings? = now()?.feelings

    /** How she feels and the weather, or null if the integration isn't set up. */
    suspend fun now(): HouseNow? {
        val body = try {
            JSONObject(call("GET", "/api/lena/context?memory=0"))
        } catch (e: IOException) {
            if (e.message == "HTTP 404") return null else throw e
        }
        val feelings = body.optJSONObject("feelings")?.let { f ->
            f.optString("mood").takeIf { it.isNotBlank() }?.let { mood ->
                Feelings(
                    mood = mood,
                    moodWhy = f.optString("mood_reason"),
                    energy = f.optString("energy"),
                    moment = f.optString("moment").takeIf { it.isNotBlank() && it != NO_MOMENT },
                    momentWhy = f.optString("moment_reason"),
                )
            }
        }
        val weather = body.optJSONObject("weather")?.let { w ->
            Weather(
                condition = w.optString("condition"),
                temperature = if (w.isNull("temperature")) null else w.optDouble("temperature").takeIf { !it.isNaN() },
                unit = w.optString("unit").takeIf { !w.isNull("unit") } ?: "",
            )
        }
        return HouseNow(feelings, weather)
    }

    /** An entity's state ("on", "off", "home"...), or null if it doesn't exist. */
    suspend fun state(entityId: String): String? = try {
        JSONObject(call("GET", "/api/states/${enc(entityId)}")).optString("state")
    } catch (e: IOException) {
        if (e.message == "HTTP 404") null else throw e
    }

    private suspend fun todo(service: String, list: String, data: JSONObject = JSONObject(), response: Boolean = false): String =
        call("POST", "/api/services/todo/$service" + if (response) "?return_response" else "", data.put("entity_id", list))

    /** The open items on her memory list, newest last, as Home Assistant keeps them. */
    suspend fun memories(list: String): List<HouseMemory> {
        val r = JSONObject(todo("get_items", list, JSONObject().put("status", "needs_action"), response = true))
        val items = r.optJSONObject("service_response")?.optJSONObject(list)?.optJSONArray("items") ?: JSONArray()
        return List(items.length()) { i ->
            val o = items.getJSONObject(i)
            HouseMemory(o.optString("uid").ifBlank { o.optString("summary") }, o.optString("summary"))
        }.filter { it.text.isNotBlank() }
    }

    suspend fun addMemory(list: String, text: String) {
        todo("add_item", list, JSONObject().put("item", text))
    }

    suspend fun updateMemory(list: String, m: HouseMemory, text: String) {
        todo("update_item", list, JSONObject().put("item", m.uid).put("rename", text))
    }

    suspend fun removeMemory(list: String, m: HouseMemory) {
        todo("remove_item", list, JSONObject().put("item", m.uid))
    }
}

/** "Grumpy · Groggy · Exasperated" and why, under Lena's name. */
@Composable
fun FeelingsLine(f: Feelings, modifier: Modifier = Modifier) {
    val why = f.momentWhy.takeIf { f.moment != null && it.isNotBlank() } ?: f.moodWhy
    Column(modifier) {
        Text(
            listOfNotNull(f.mood, f.energy.ifBlank { null }, f.moment).joinToString(" · "),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        if (why.isNotBlank()) {
            Text(
                why,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Small secrets (the Home Assistant token), encrypted with an AES key kept in the Android
 * keystore. The key can't be exported, and the app's data is already kept out of backups.
 */
object Secrets {
    const val HA_TOKEN = "ha_token"
    private const val ALIAS = "lena-secrets"

    private fun p(c: Context) = c.getSharedPreferences("lena-secrets", Context.MODE_PRIVATE)

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    fun has(c: Context, name: String): Boolean = p(c).contains(name)

    fun put(c: Context, name: String, value: String?) {
        if (value.isNullOrBlank()) {
            p(c).edit().remove(name).apply()
            return
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val sealed = cipher.doFinal(value.toByteArray())
        val stored = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(sealed, Base64.NO_WRAP)
        p(c).edit().putString(name, stored).apply()
    }

    fun get(c: Context, name: String): String? = runCatching {
        val (iv, sealed) = p(c).getString(name, null)!!.split(":").map { Base64.decode(it, Base64.NO_WRAP) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv)) }
        String(cipher.doFinal(sealed))
    }.getOrNull()
}
