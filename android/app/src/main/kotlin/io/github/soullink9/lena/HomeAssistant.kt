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

/**
 * Home Assistant answered with an error. The message stays "HTTP <code>" (what callers check); [detail] is
 * Home Assistant's own explanation, when it gave one ("There's no tidy to undo").
 */
class HaHttpException(val code: Int, val detail: String?) : IOException("HTTP $code")

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

/** One of Home Assistant's conversation agents (Assist itself, or an AI one such as Ollama's). */
data class AssistAgent(val id: String, val name: String)

/** Assist's answer, and the conversation it belongs to (sent back with the next question, to continue it). */
data class AssistReply(val speech: String, val conversationId: String?)

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

    /**
     * One request to Home Assistant with the token. A 401 or 403 means the token was refused, which the
     * screens report differently from Home Assistant being unreachable. A dropped connection is tried
     * again (see [Connections.retrying]).
     */
    private suspend fun call(
        method: String,
        path: String,
        body: JSONObject? = null,
        readTimeoutMs: Int = 30_000, // a large album takes a moment to list
    ): String = Connections.retrying(null, method, "Home Assistant $method ${path.substringBefore('?')}") {
        callOnce(method, path, body, readTimeoutMs)
    }

    /** One request, as [call] describes; [call] tries it again if the connection dropped. */
    private suspend fun callOnce(
        method: String,
        path: String,
        body: JSONObject?,
        readTimeoutMs: Int,
    ): String = withContext(Dispatchers.IO) {
        val conn = URL(base + path).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 8_000
            conn.readTimeout = readTimeoutMs
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
                throw HaHttpException(code, runCatching { JSONObject(text).optString("message") }.getOrNull()?.ifBlank { null })
            }
            text
        } finally {
            conn.disconnect()
        }
    }

    /** A full address for a path Home Assistant gave (its own sounds and voices start with /api/). */
    fun absolute(url: String): String = if (url.startsWith("http://") || url.startsWith("https://")) url else base + url

    /** Encodes part of a path. */
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

    /** The language each speech-to-text engine was found to take, so it's asked only once. */
    private val sttLanguages = mutableMapOf<String, String>()

    /**
     * Turns speech into text with one of Home Assistant's speech-to-text engines (an stt entity, the same
     * its voice assistants use): 16 kHz, 16-bit mono PCM, as a Home Assistant Voice satellite sends it.
     * Returns "" when it heard nothing.
     */
    suspend fun transcribe(engine: String, pcm: ByteArray): String = withContext(Dispatchers.IO) {
        val language = sttLanguages[engine] ?: sttLanguage(engine).also { sttLanguages[engine] = it }
        val conn = URL("$base/api/stt/${enc(engine)}").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 8_000
            conn.readTimeout = 60_000
            conn.doOutput = true
            conn.setFixedLengthStreamingMode(pcm.size)
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Content-Type", "application/octet-stream")
            conn.setRequestProperty("X-Speech-Content",
                "format=wav; codec=pcm; sample_rate=16000; bit_rate=16; channel=1; language=$language")
            conn.outputStream.use { it.write(pcm) }
            val code = conn.responseCode
            if (code == 401 || code == 403) throw HaAuthException()
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.use { String(it.readBytes()) } ?: ""
            if (code !in 200..299) {
                Logs.w("Home Assistant", "Speech-to-text ($engine): HTTP $code ${text.take(200)}")
                throw HaHttpException(code, null)
            }
            val reply = JSONObject(text)
            if (reply.optString("result") != "success") "" else reply.optString("text")
        } finally {
            conn.disconnect()
        }
    }

    /**
     * The language to ask an engine for: Home Assistant's own (its general settings), in the engine's own
     * spelling ("en" or "en-US"), or the engine's first.
     */
    private suspend fun sttLanguage(engine: String): String {
        val house = runCatching { JSONObject(call("GET", "/api/config")).optString("language") }.getOrNull()?.ifBlank { null } ?: "en"
        val offered = runCatching { JSONObject(call("GET", "/api/stt/${enc(engine)}")).optJSONArray("languages") }.getOrNull()
        val languages = List(offered?.length() ?: 0) { offered!!.optString(it) }.filter { it.isNotBlank() }
        return languages.firstOrNull { it.equals(house, ignoreCase = true) }
            ?: languages.firstOrNull { it.lowercase().replace('_', '-').startsWith(house.lowercase() + "-") }
            ?: languages.firstOrNull { house.lowercase().startsWith(it.lowercase()) }
            ?: languages.firstOrNull()
            ?: house
    }

    /** Home Assistant's speech-to-text engines (stt entities), by name, for Hearing you. */
    suspend fun sttEngines(): List<Speaker> {
        val states = JSONArray(call("GET", "/api/states"))
        return List(states.length()) { states.getJSONObject(it) }
            .filter { it.optString("entity_id").startsWith("stt.") }
            .map { o ->
                val id = o.optString("entity_id")
                Speaker(id, o.optJSONObject("attributes")?.optString("friendly_name")?.ifBlank { null } ?: id, false)
            }
            .sortedBy { it.name.lowercase() }
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

    /**
     * Home Assistant's conversation agents, for Settings → Who answers. Each is a `conversation.*` entity
     * (Home Assistant's own Assist, and any AI agent added by an integration such as Ollama), so the plain
     * states list has them all; the REST API has no separate list.
     */
    suspend fun agents(): List<AssistAgent> {
        val states = JSONArray(call("GET", "/api/states"))
        return List(states.length()) { states.getJSONObject(it) }
            .filter { it.optString("entity_id").startsWith("conversation.") }
            .map { o ->
                val id = o.optString("entity_id")
                AssistAgent(id, o.optJSONObject("attributes")?.optString("friendly_name")?.ifBlank { null } ?: id)
            }
            .sortedBy { it.name.lowercase() }
    }

    /**
     * Sends what you said or typed to Assist (Home Assistant's /api/conversation/process) and returns its
     * spoken answer. [agentId] null uses the agent picked in Home Assistant's own Assist settings;
     * [conversationId] continues an earlier conversation, so the agent remembers what was just said. An
     * AI agent can take a while to answer, hence the long wait. The language is Home Assistant's own.
     * [deviceId] is this display's device ([Room]), so Assist knows which room it's hearing from, as with a
     * Home Assistant Voice satellite.
     */
    suspend fun converse(text: String, agentId: String?, conversationId: String?, deviceId: String? = null): AssistReply {
        val body = JSONObject().put("text", text)
        if (agentId != null) body.put("agent_id", agentId)
        if (deviceId != null) body.put("device_id", deviceId)
        if (conversationId != null) body.put("conversation_id", conversationId)
        val r = JSONObject(call("POST", "/api/conversation/process", body, readTimeoutMs = 180_000))
        val speech = r.optJSONObject("response")
            ?.optJSONObject("speech")
            ?.optJSONObject("plain")
            ?.optString("speech")
            .orEmpty()
        val conversation = r.optString("conversation_id").takeIf { it.isNotBlank() && !r.isNull("conversation_id") }
        return AssistReply(speech, conversation)
    }

    /**
     * Registers this app as one of Lena's displays (or reads it, once registered) with the Lena integration's
     * /api/lena/display: its device, room, the room's speakers and its Announcement entity. Throws
     * IOException("HTTP 404") without the integration.
     */
    suspend fun registerDisplay(id: String, name: String): JSONObject =
        JSONObject(call("POST", "/api/lena/display", JSONObject().put("id", id).put("name", name)))

    /**
     * Asks Lena to tidy her memory now (lena.tidy_memory): returns whether anything changed and what. Her
     * model takes a while, hence the long wait. Throws [HaHttpException] with her reason when she can't
     * (no AI Task set up, an answer she couldn't use).
     */
    suspend fun tidyMemory(): Pair<Boolean, String> {
        val r = JSONObject(call("POST", "/api/services/lena/tidy_memory?return_response", JSONObject(), readTimeoutMs = 300_000))
        val answer = r.optJSONObject("service_response") ?: JSONObject()
        return answer.optBoolean("changed") to answer.optString("summary")
    }

    /** Puts her memory back as it was before her last tidy (lena.undo_memory_tidy). */
    suspend fun undoMemoryTidy() {
        call("POST", "/api/services/lena/undo_memory_tidy", JSONObject())
    }

    /**
     * Says [text] on a media player with Home Assistant's text-to-speech (tts.speak with the [voice], a TTS
     * entity). Returns once Home Assistant has started it; the speaker plays it from there.
     */
    suspend fun speak(voice: String, speaker: String, text: String) {
        call("POST", "/api/services/tts/speak", JSONObject()
            .put("entity_id", voice).put("media_player_entity_id", speaker).put("message", text).put("cache", true),
            readTimeoutMs = 60_000)
    }

    /** Cancels one of Home Assistant's timers by its id (lena.cancel_timer). */
    suspend fun cancelHaTimer(timerId: String) {
        call("POST", "/api/services/lena/cancel_timer", JSONObject().put("timer_id", timerId))
    }

    /** Stops a media player (her voice on a speaker, when you stop her). */
    suspend fun stopPlaying(speaker: String) {
        call("POST", "/api/services/media_player/media_stop", JSONObject().put("entity_id", speaker))
    }

    /** Sets a media player's volume (0 to 100). */
    suspend fun setSpeakerVolume(speaker: String, percent: Int) {
        call("POST", "/api/services/media_player/volume_set", JSONObject()
            .put("entity_id", speaker).put("volume_level", percent.coerceIn(0, 100) / 100.0))
    }

    /** A media player's volume (0 to 100), or null if it doesn't say. */
    suspend fun speakerVolume(speaker: String): Int? =
        stateWithAttributes(speaker)?.optJSONObject("attributes")?.takeIf { it.has("volume_level") }
            ?.let { (it.optDouble("volume_level") * 100).toInt() }

    /** Home Assistant's text-to-speech voices (tts entities), by name, for Speak through. */
    suspend fun voices(): List<Speaker> {
        val states = JSONArray(call("GET", "/api/states"))
        return List(states.length()) { states.getJSONObject(it) }
            .filter { it.optString("entity_id").startsWith("tts.") }
            .map { o ->
                val id = o.optString("entity_id")
                Speaker(id, o.optJSONObject("attributes")?.optString("friendly_name")?.ifBlank { null } ?: id, false)
            }
            .sortedBy { it.name.lowercase() }
    }

    /**
     * Reports this display's timers, alarms, reminders and volume to the Lena integration and collects what
     * she's asked it to do (POST /api/lena/display/sync; see [DisplaySync]).
     */
    suspend fun syncDisplay(body: JSONObject): JSONObject =
        JSONObject(call("POST", "/api/lena/display/sync", body, readTimeoutMs = 15_000))

    /**
     * Someone came into the room: asks the integration whether there's something to say first (the
     * morning's briefing, or a worry not yet mentioned here), and returns it, or "" when there's nothing.
     */
    suspend fun briefing(displayId: String): String =
        JSONObject(call("POST", "/api/lena/display/briefing", JSONObject().put("id", displayId), readTimeoutMs = 120_000))
            .optString("text")

    /** Every media player in Home Assistant, by name, for choosing where music plays. */
    suspend fun speakers(): List<Speaker> {
        val states = JSONArray(call("GET", "/api/states"))
        return List(states.length()) { states.getJSONObject(it) }
            .filter { it.optString("entity_id").startsWith("media_player.") }
            .map { o ->
                val id = o.optString("entity_id")
                Speaker(id, o.optJSONObject("attributes")?.optString("friendly_name")?.ifBlank { null } ?: id, false)
            }
            .sortedBy { it.name.lowercase() }
    }

    /**
     * An entity's state and attributes (an Announcement's time, message and listen), or null if it doesn't
     * exist.
     */
    suspend fun stateWithAttributes(entityId: String): JSONObject? = try {
        JSONObject(call("GET", "/api/states/${enc(entityId)}", readTimeoutMs = 10_000))
    } catch (e: IOException) {
        if (e.message == "HTTP 404") null else throw e
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

    /**
     * Calls one of Home Assistant's to-do actions on her memory list, asking for the response when reading
     * it.
     */
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

    /** Adds a fact to her shared memory. */
    suspend fun addMemory(list: String, text: String) {
        todo("add_item", list, JSONObject().put("item", text))
    }

    /** Changes a fact's text, keeping its place in the list. */
    suspend fun updateMemory(list: String, m: HouseMemory, text: String) {
        todo("update_item", list, JSONObject().put("item", m.uid).put("rename", text))
    }

    /** Removes a fact from her shared memory. */
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

    /** Where the encrypted values are kept (excluded from backups like the rest of the app's data). */
    private fun p(c: Context) = c.getSharedPreferences("lena-secrets", Context.MODE_PRIVATE)

    /**
     * The AES key in Android's keystore, made the first time. It never leaves the keystore (and on most
     * phones lives in secure hardware), so a copy of the app's files can't be decrypted elsewhere.
     */
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

    /** Whether a value is saved, without decrypting it (the screens only need to show "(saved)"). */
    fun has(c: Context, name: String): Boolean = p(c).contains(name)

    /** Encrypts and saves a value with AES-GCM and a fresh IV each time; blank removes it. */
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

    /**
     * Decrypts a saved value, or null if there's none or it can't be read (for example after the keystore
     * was reset).
     */
    fun get(c: Context, name: String): String? = runCatching {
        val (iv, sealed) = p(c).getString(name, null)!!.split(":").map { Base64.decode(it, Base64.NO_WRAP) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv)) }
        String(cipher.doFinal(sealed))
    }.getOrNull()
}
