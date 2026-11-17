package io.github.soullink9.lena

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.json.JSONObject

/**
 * Camera mode's choices that Return to defaults leaves alone: which cameras this device may show
 * (**included**, in Settings → Cameras), which of those are **selected** (in camera mode, for Selected only
 * and Rotate), a motion sensor picked by hand for a camera, and the way each camera's live picture last worked
 * ([liveWay]). Kept on this device only.
 *
 * A camera is known by its Home Assistant device ([FoundCamera.id]), so a stream entity being renamed or a
 * new stream quality turned on doesn't lose it.
 */
object Cameras {
    private fun p(c: Context) = c.getSharedPreferences("lena_cameras", Context.MODE_PRIVATE)

    /** The included cameras, in the order they were switched on. */
    fun included(c: Context): List<String> = p(c).getString("included", "").orEmpty().split('\n').filter { it.isNotBlank() }

    fun setIncluded(c: Context, cameras: List<String>) = p(c).edit().putString("included", cameras.distinct().joinToString("\n")).apply()

    fun selected(c: Context): Set<String> = p(c).getString("selected", "").orEmpty().split('\n').filter { it.isNotBlank() }.toSet()

    fun setSelected(c: Context, cameras: Set<String>) = p(c).edit().putString("selected", cameras.joinToString("\n")).apply()

    /** The motion sensor picked for [camera] in Settings: null to use the ones found on its device, "" for none. */
    fun chosenSensor(c: Context, camera: String): String? =
        if (p(c).contains("sensor:$camera")) p(c).getString("sensor:$camera", "") else null

    fun chooseSensor(c: Context, camera: String, entity: String?) {
        val edit = p(c).edit()
        if (entity == null) edit.remove("sensor:$camera") else edit.putString("sensor:$camera", entity)
        edit.apply()
    }

    /** How long a camera's live picture starts at the way that last worked, before trying the earlier ways again. */
    private const val LIVE_WAY_MS = 24 * 60 * 60 * 1000L

    /**
     * The way [camera]'s live picture (a stream entity) starts with on this device: past the ways that gave it
     * no picture in the last day ([CameraLive.Way]), so camera mode doesn't wait through them every time it
     * opens. Null to start with WebRTC.
     */
    fun liveWay(c: Context, camera: String): CameraLive.Way? {
        val saved = p(c).getString("way:$camera", null) ?: return null
        val name = saved.substringBefore('@')
        val since = saved.substringAfter('@', "").toLongOrNull() ?: return null
        if (System.currentTimeMillis() - since !in 0..LIVE_WAY_MS) return null
        return CameraLive.Way.entries.firstOrNull { it.name == name }
    }

    fun setLiveWay(c: Context, camera: String, way: CameraLive.Way) =
        p(c).edit().putString("way:$camera", "${way.name}@${System.currentTimeMillis()}").apply()

    /** Whether camera mode is on here (Settings → Cameras → Camera mode), with Home Assistant connected. */
    fun available(c: Context): Boolean = Prefs.cameras(c).on && Prefs.haServer(c) != null && Secrets.has(c, Secrets.HA_TOKEN)

    /**
     * Opens camera mode, on someone's say (a card, a button, or asking her); never on its own. [asked]: what
     * was said ("show me the front door camera"), so a camera named in it opens in the spotlight.
     */
    fun open(c: Context, asked: String = "") {
        c.startActivity(Intent(c, CameraActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(CameraActivity.EXTRA_ASKED, asked))
    }

    /** The camera whose name is in [asked] (every word of it), the longest name first; null when none is. */
    fun named(cameras: List<FoundCamera>, asked: String): FoundCamera? {
        val said = asked.lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotBlank() }.toSet()
        if (said.isEmpty()) return null
        return cameras.sortedByDescending { it.name.length }.firstOrNull { camera ->
            val name = camera.name.lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotBlank() }
            name.isNotEmpty() && name.all { it in said }
        }
    }

    /**
     * Every camera Home Assistant has, paired with its device's motion sensors and events: from its entity
     * registry (which entity is on which device; the version any user may read), its device registry (the
     * devices' names, when this user may read it) and the entities' states (device classes, event types).
     * The three are asked for at once, so opening camera mode waits for the slowest, not for all three in turn.
     */
    suspend fun find(socket: HaSocket): List<FoundCamera> = coroutineScope {
        val displayAsked = async { socket.call(JSONObject().put("type", "config/entity_registry/list_for_display")) }
        val devicesAsked = async { runCatching { socket.list("config/device_registry/list") }.getOrNull() }
        val statesAsked = async { socket.list("get_states") }
        val display = displayAsked.await().optJSONObject("result")
        val registry = display?.optJSONArray("entities")
        val deviceOf = HashMap<String, String>()
        if (registry != null) for (i in 0 until registry.length()) {
            val e = registry.getJSONObject(i)
            val device = e.optString("di")
            if (device.isNotBlank()) deviceOf[e.optString("ei")] = device
        }
        val deviceNames = HashMap<String, String>()
        devicesAsked.await()?.let { devices ->
            for (i in 0 until devices.length()) {
                val d = devices.getJSONObject(i)
                deviceNames[d.optString("id")] = d.optString("name_by_user").takeIf { it.isNotBlank() && it != "null" } ?: d.optString("name")
            }
        }
        val states = statesAsked.await()
        val entities = (0 until states.length()).map { i ->
            val state = states.getJSONObject(i)
            val id = state.optString("entity_id")
            val attributes = state.optJSONObject("attributes") ?: JSONObject()
            val events = attributes.optJSONArray("event_types")
            HaEntity(
                entityId = id,
                deviceId = deviceOf[id],
                deviceClass = attributes.optString("device_class"),
                eventTypes = if (events == null) emptyList() else (0 until events.length()).map { events.optString(it) },
                name = attributes.optString("friendly_name"),
            )
        }.filter { it.entityId.startsWith("camera.") || it.entityId.startsWith("binary_sensor.") || it.entityId.startsWith("event.") }
        CameraFinder.find(entities, deviceNames)
    }
}
