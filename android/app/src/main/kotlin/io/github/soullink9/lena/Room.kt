package io.github.soullink9.lena

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.provider.Settings as AndroidSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.UUID

/** A media player in Home Assistant. [musicAssistant]: Music Assistant's, which plays music by name. */
data class Speaker(val entityId: String, val name: String, val musicAssistant: Boolean)

/**
 * This display as Home Assistant knows it: its device (under the Lena integration), the room you put it
 * in there, that room's speakers, and the Announcement entity that lena.announce fires.
 */
data class RoomInfo(
    val deviceId: String,
    val name: String,
    val area: String?,
    val speakers: List<Speaker>,
    /** Its Assist satellite in Home Assistant (assist_satellite.…), once it's there. */
    val satellite: String?,
)

/**
 * Where this display is, like a Home Assistant Voice satellite: the app registers itself with the Lena
 * integration as a display (a device you put in a room in Home Assistant), and from then on:
 *
 * - Assist is told which device it's hearing from, so "turn on the lights" means this room's.
 * - Open WebUI is told the room, and where music goes when no room is named ([note]); for another room
 *   or the whole home she finds that room's speakers with her Home Assistant tools.
 * - Announcements (lena.announce) for this display are spoken here ([DisplaySync]).
 *
 * The registration is checked again every [REFRESH_MS], so moving the display to another room in Home
 * Assistant reaches the app within minutes.
 */
object Room {
    private const val PREFS = "lena_room"
    private const val REFRESH_MS = 10 * 60_000L

    private val current = MutableStateFlow<RoomInfo?>(null)
    private var loaded = false
    @Volatile private var lastRefresh = 0L

    /** This display as last seen in Home Assistant, or null before it's registered (or without Lena there). */
    fun info(c: Context): StateFlow<RoomInfo?> {
        if (!loaded) {
            loaded = true
            current.value = p(c).getString("info", null)?.let { runCatching { parse(JSONObject(it)) }.getOrNull() }
        }
        return current
    }

    /** This app's own id for its display, made once. */
    fun id(c: Context): String {
        p(c).getString("id", null)?.let { return it }
        val id = UUID.randomUUID().toString()
        p(c).edit().putString("id", id).apply()
        return id
    }

    /** The name the display gets in Home Assistant: the device's name in Android's settings, or its model. */
    fun deviceName(c: Context): String =
        runCatching { AndroidSettings.Global.getString(c.contentResolver, AndroidSettings.Global.DEVICE_NAME) }.getOrNull()
            ?.takeIf { it.isNotBlank() } ?: Build.MODEL

    /**
     * Registers this display with the Lena integration (or reads it, once it's there) and keeps what it
     * says. At most every ten minutes unless [force]. Without Home Assistant or the integration, it
     * forgets the room.
     */
    suspend fun refresh(c: Context, force: Boolean = false) {
        if (!force && SystemClock.elapsedRealtime() - lastRefresh < REFRESH_MS && lastRefresh != 0L) return
        val ha = HomeAssistant.from(c)
        if (ha == null) {
            save(c, null)
            return
        }
        lastRefresh = SystemClock.elapsedRealtime()
        try {
            val info = parse(ha.registerDisplay(id(c), deviceName(c)))
            if (info != current.value) Logs.i("Room", "This display: ${info.name}, ${info.area ?: "no room yet"}")
            save(c, info)
        } catch (e: IOException) {
            if (e.message == "HTTP 404") save(c, null) // the integration isn't there (or is too old)
            else Logs.w("Room", "Couldn't check this display's room", e)
        } catch (e: Exception) {
            Logs.w("Room", "Couldn't check this display's room", e)
        }
    }

    /** The speaker you chose for music here (Settings → Connections → Home Assistant), or "" for automatic. */
    fun chosenSpeaker(c: Context): String = p(c).getString("music", "") ?: ""

    /** Chooses where music goes when no room is named; "" leaves it to the room. */
    fun chooseSpeaker(c: Context, entityId: String) {
        p(c).edit().putString("music", entityId).apply()
    }

    /** Home Assistant's speech-to-text engine for Hearing you → Home Assistant, or "" for its first one. */
    fun sttEngine(c: Context): String = p(c).getString("stt_engine", "") ?: ""

    fun chooseSttEngine(c: Context, entityId: String) {
        p(c).edit().putString("stt_engine", entityId).apply()
    }

    /** The Home Assistant speaker she speaks through (Speak through), or "" for this device. */
    fun voiceSpeaker(c: Context): String = p(c).getString("voice_speaker", "") ?: ""

    /** Home Assistant's text-to-speech voice (a tts entity) used on that speaker. */
    fun voiceEngine(c: Context): String = p(c).getString("voice_engine", "") ?: ""

    /** Chooses the speaker and voice; empty speaker means she speaks on this device again. */
    fun chooseVoiceSpeaker(c: Context, speaker: String, engine: String) {
        p(c).edit().putString("voice_speaker", speaker).putString("voice_engine", engine).apply()
    }

    /** Whether everything she says goes to a Home Assistant speaker instead of this device. */
    fun speaksThroughSpeaker(c: Context): Boolean =
        voiceSpeaker(c).isNotBlank() && voiceEngine(c).isNotBlank() && HomeAssistant.from(c) != null

    /**
     * The motion sensor that makes her speak first (Settings → Wall display → Speaking first), or "".
     * Separate from the screensaver's, so a photo frame can stay a photo frame and still get a briefing.
     */
    fun briefingSensor(c: Context): String = p(c).getString("briefing_sensor", "") ?: ""

    fun chooseBriefingSensor(c: Context, entityId: String) {
        p(c).edit().putString("briefing_sensor", entityId.trim()).apply()
    }

    /** Where music goes when no room is named: the chosen speaker, or the room's (Music Assistant's first). */
    fun musicSpeaker(c: Context): Speaker? {
        val room = info(c).value
        val chosen = chosenSpeaker(c)
        if (chosen.isNotBlank()) return room?.speakers?.firstOrNull { it.entityId == chosen } ?: Speaker(chosen, chosen, false)
        return room?.speakers?.firstOrNull()
    }

    /**
     * For Open WebUI, with each request: which room she's in, and where music goes. Null when the display
     * isn't in a room and no speaker is chosen.
     */
    fun note(c: Context): String? {
        val room = info(c).value
        val speaker = musicSpeaker(c)
        val parts = mutableListOf<String>()
        if (room?.area != null) {
            parts += "You're speaking through Lena's display in the ${room.area} (a Home Assistant area). When they don't " +
                "name a room, \"here\", \"this room\" and things like \"the lights\" mean the ${room.area}."
        }
        if (speaker != null) {
            parts += "Music, radio or podcasts asked for without naming a room play on ${speaker.name} (${speaker.entityId})" +
                (if (speaker.musicAssistant) ", a Music Assistant player." else ".") +
                " If they name another room, or the whole home, use your Home Assistant tools to find the speakers there and play it on those."
        }
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" ")
    }

    private fun save(c: Context, info: RoomInfo?) {
        current.value = info
        loaded = true
        p(c).edit().apply {
            if (info == null) remove("info") else putString("info", toJson(info).toString())
        }.apply()
    }

    private fun parse(o: JSONObject): RoomInfo {
        val speakers = o.optJSONArray("speakers") ?: JSONArray()
        return RoomInfo(
            deviceId = o.getString("device_id"),
            name = o.optString("name"),
            area = o.optJSONObject("area")?.optString("name")?.ifBlank { null },
            speakers = List(speakers.length()) { i ->
                val s = speakers.getJSONObject(i)
                Speaker(s.optString("entity_id"), s.optString("name"), s.optBoolean("music_assistant"))
            },
            satellite = o.optString("satellite").takeIf { it.isNotBlank() && !o.isNull("satellite") },
        )
    }

    private fun toJson(info: RoomInfo): JSONObject = JSONObject()
        .put("device_id", info.deviceId)
        .put("name", info.name)
        .put("area", info.area?.let { JSONObject().put("name", it) } ?: JSONObject.NULL)
        .put("speakers", JSONArray().apply {
            info.speakers.forEach { put(JSONObject().put("entity_id", it.entityId).put("name", it.name).put("music_assistant", it.musicAssistant)) }
        })
        .put("satellite", info.satellite ?: JSONObject.NULL)

    private fun p(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
