package io.github.soullink9.lena

import android.content.Context
import android.content.Intent
import org.json.JSONObject

/**
 * Camera mode's choices that Return to defaults leaves alone: which cameras this device may show
 * (**included**, in Settings → Cameras), which of those are **selected** (in camera mode, for Selected only
 * and Rotate), and a motion sensor picked by hand for a camera. Kept on this device only.
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
     */
    suspend fun find(socket: HaSocket): List<FoundCamera> {
        val display = socket.call(JSONObject().put("type", "config/entity_registry/list_for_display")).optJSONObject("result")
        val registry = display?.optJSONArray("entities")
        val deviceOf = HashMap<String, String>()
        if (registry != null) for (i in 0 until registry.length()) {
            val e = registry.getJSONObject(i)
            val device = e.optString("di")
            if (device.isNotBlank()) deviceOf[e.optString("ei")] = device
        }
        val deviceNames = HashMap<String, String>()
        runCatching { socket.list("config/device_registry/list") }.getOrNull()?.let { devices ->
            for (i in 0 until devices.length()) {
                val d = devices.getJSONObject(i)
                deviceNames[d.optString("id")] = d.optString("name_by_user").takeIf { it.isNotBlank() && it != "null" } ?: d.optString("name")
            }
        }
        val states = socket.list("get_states")
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
        return CameraFinder.find(entities, deviceNames)
    }
}
