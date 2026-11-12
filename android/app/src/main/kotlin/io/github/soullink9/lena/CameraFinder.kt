package io.github.soullink9.lena

/** What counts as motion for camera mode's spotlight (Settings → Cameras → What counts as motion). */
enum class MotionKind(val label: String) {
    PEOPLE_VEHICLES_DOORBELL("People, vehicles and the doorbell"),
    ANY("Any motion"),
    DOORBELL("The doorbell only"),
}

/**
 * One of Home Assistant's entities, as much as camera mode needs: its device (null when it has none), its
 * device class, the events an event entity fires, and its name.
 */
data class HaEntity(
    val entityId: String,
    val deviceId: String?,
    val deviceClass: String = "",
    val eventTypes: List<String> = emptyList(),
    val name: String = "",
)

/**
 * A camera for camera mode: one per device. UniFi Protect makes a camera entity per stream quality on the
 * same device (high, medium, low, package), so the grid's still pictures come from the lightest one
 * ([still]) and the live spotlight from the best ([live]). Its motion sensors and events are the ones on
 * the same device.
 */
data class FoundCamera(
    val id: String,
    val name: String,
    val still: String,
    val live: String,
    val motion: List<String>,
    val detected: List<String>,
    val doorbell: List<String>,
    /** Every binary sensor and event entity on its device, to choose from by hand. */
    val sensors: List<String>,
)

/**
 * Finds the cameras in Home Assistant's entities and pairs each with its own motion sensors and events.
 * Plain Kotlin, so tools/camera_check can check it on UniFi Protect's entity names.
 */
object CameraFinder {
    /** Words in a camera entity's ID that say which stream it is, lightest first. */
    private val LIGHT_FIRST = listOf("low", "medium", "high")

    /** What a stream entity's name adds to the camera's own, taken off for its name in camera mode. */
    private val STREAM_SUFFIX = Regex("""\s*(high|medium|low|package)( resolution)?( channel)?( \(insecure\))?$""", RegexOption.IGNORE_CASE)

    /** Whether a camera entity is the [word] stream ("low" in camera.porch_low_resolution_channel, not in camera.yellow_door). */
    private fun stream(entity: HaEntity, word: String): Boolean = Regex("""[._]$word(_|$)""").containsMatchIn(entity.entityId)

    fun find(entities: List<HaEntity>, deviceNames: Map<String, String>): List<FoundCamera> {
        val cameras = entities.filter { it.entityId.startsWith("camera.") }
        // UniFi's package camera (a doorbell's second, downward lens) is a separate view, not a stream of the same.
        val byDevice = cameras.filterNot { stream(it, "package") }.groupBy { it.deviceId ?: it.entityId }
        return byDevice.map { (id, streams) ->
            val sorted = streams.sortedBy { it.entityId }
            val still = LIGHT_FIRST.firstNotNullOfOrNull { word -> sorted.firstOrNull { stream(it, word) } } ?: sorted.first()
            val live = LIGHT_FIRST.reversed().firstNotNullOfOrNull { word -> sorted.firstOrNull { stream(it, word) } } ?: sorted.first()
            val own = if (streams.first().deviceId == null) emptyList() else entities.filter { it.deviceId == id }
            val binary = own.filter { it.entityId.startsWith("binary_sensor.") }
            val events = own.filter { it.entityId.startsWith("event.") }
            FoundCamera(
                id = id,
                name = deviceNames[id]?.takeIf { it.isNotBlank() } ?: live.name.replace(STREAM_SUFFIX, "").ifBlank { live.entityId },
                still = still.entityId,
                live = live.entityId,
                motion = binary.filter { it.deviceClass == "motion" }.map { it.entityId },
                // UniFi's smart detections: person, vehicle (and its own "any object detected").
                detected = binary.filter { "person" in it.entityId || "vehicle" in it.entityId }.map { it.entityId } +
                    events.filter { "smart_detection" in it.entityId || "vehicle" in it.entityId }.map { it.entityId },
                doorbell = events.filter { "ring" in it.eventTypes }.map { it.entityId },
                sensors = (binary + events).map { it.entityId }.sorted(),
            )
        }.sortedBy { it.name.lowercase() }
    }

    /**
     * The entities whose changes put [camera] in the spotlight, for [kind]; [chosen] is the one picked by hand
     * in Settings ("" for none, null to use these). People and vehicles fall back to plain motion for a
     * camera that can't tell them apart.
     */
    fun triggers(camera: FoundCamera, kind: MotionKind, chosen: String?): List<String> {
        if (chosen != null) return if (chosen.isBlank()) emptyList() else listOf(chosen)
        return when (kind) {
            MotionKind.ANY -> camera.motion + camera.detected + camera.doorbell
            MotionKind.PEOPLE_VEHICLES_DOORBELL -> (camera.detected.ifEmpty { camera.motion }) + camera.doorbell
            MotionKind.DOORBELL -> camera.doorbell
        }.distinct()
    }

    /** Whether a new state means motion: on for a sensor; any new state for an event entity, which is a moment. */
    fun isMotion(entityId: String, state: String): Boolean =
        if (entityId.startsWith("event.")) state != "unavailable" && state != "unknown" else state == "on"
}
