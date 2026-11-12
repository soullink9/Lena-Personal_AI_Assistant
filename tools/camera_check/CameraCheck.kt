// Checks camera mode's choices away from Android: which cameras and sensors it finds in Home Assistant's
// entities (CameraFinder.kt, on UniFi Protect's names) and what it shows from moment to moment
// (CameraDirector.kt). Run by run.sh next to this file.
import io.github.soullink9.lena.CameraDirector
import io.github.soullink9.lena.CameraFinder
import io.github.soullink9.lena.CameraView
import io.github.soullink9.lena.HaEntity
import io.github.soullink9.lena.MotionKind

private var failures = 0

private fun check(ok: Boolean, what: String) {
    println((if (ok) "ok    " else "FAIL  ") + what)
    if (!ok) failures++
}

fun main() {
    // ---- Finding cameras: UniFi Protect's entities for a doorbell and a camera, and a plain camera. ----
    val entities = listOf(
        HaEntity("camera.front_door_high_resolution_channel", "d1", name = "Front door High resolution channel"),
        HaEntity("camera.front_door_low_resolution_channel", "d1", name = "Front door Low resolution channel"),
        HaEntity("camera.front_door_package_camera", "d1", name = "Front door Package camera"),
        HaEntity("binary_sensor.front_door_motion", "d1", "motion"),
        HaEntity("binary_sensor.front_door_person_detected", "d1", "occupancy"),
        HaEntity("event.front_door_doorbell", "d1", eventTypes = listOf("ring")),
        HaEntity("camera.yellow_shed_high_resolution_channel", "d2", name = "Yellow shed High resolution channel"),
        HaEntity("camera.yellow_shed_medium_resolution_channel", "d2", name = "Yellow shed Medium resolution channel"),
        HaEntity("binary_sensor.yellow_shed_motion", "d2", "motion"),
        HaEntity("camera.garage", null, name = "Garage"),
        HaEntity("binary_sensor.hallway_motion", "d9", "motion"),
    )
    val found = CameraFinder.find(entities, mapOf("d1" to "Front door"))
    check(found.map { it.name } == listOf("Front door", "Garage", "Yellow shed"), "one camera per device, named after it, sorted (${found.map { it.name }})")
    val door = found.first { it.id == "d1" }
    check(door.still == "camera.front_door_low_resolution_channel" && door.live == "camera.front_door_high_resolution_channel",
        "the grid uses the low stream and the spotlight the high one; the package camera isn't a stream")
    val shed = found.first { it.id == "d2" }
    check(shed.still == "camera.yellow_shed_medium_resolution_channel" && shed.live == "camera.yellow_shed_high_resolution_channel",
        "no low stream: the medium one (and \"yellow\" isn't mistaken for low)")
    check(door.motion == listOf("binary_sensor.front_door_motion") && door.detected == listOf("binary_sensor.front_door_person_detected") &&
        door.doorbell == listOf("event.front_door_doorbell"), "a camera's motion, person and doorbell come from its own device")
    check(found.first { it.id == "camera.garage" }.sensors.isEmpty(), "a camera without a device has no sensors, not someone else's")
    check(CameraFinder.triggers(door, MotionKind.PEOPLE_VEHICLES_DOORBELL, null) == listOf("binary_sensor.front_door_person_detected", "event.front_door_doorbell"),
        "people, vehicles and the doorbell: the detections and the ring, not plain motion")
    check(CameraFinder.triggers(shed, MotionKind.PEOPLE_VEHICLES_DOORBELL, null) == listOf("binary_sensor.yellow_shed_motion"),
        "a camera that can't tell people apart falls back to its motion sensor")
    check(CameraFinder.triggers(door, MotionKind.DOORBELL, null) == listOf("event.front_door_doorbell") &&
        CameraFinder.triggers(door, MotionKind.ANY, "binary_sensor.hallway_motion") == listOf("binary_sensor.hallway_motion") &&
        CameraFinder.triggers(door, MotionKind.ANY, "").isEmpty(), "the doorbell only; a sensor chosen by hand; none")
    check(CameraFinder.isMotion("binary_sensor.x", "on") && !CameraFinder.isMotion("binary_sensor.x", "off") &&
        CameraFinder.isMotion("event.x", "2026-11-12T20:00:00+00:00") && !CameraFinder.isMotion("event.x", "unavailable"),
        "a sensor turning on is motion; any new state of an event entity is a moment of it")

    // ---- What's on screen. Cameras a, b, c; 10 s turns; 30 s after motion stops. ----
    val all = listOf("a", "b", "c")
    val d = CameraDirector(all, emptySet(), CameraView.SHOW_ALL, rotateMs = 10_000, holdMs = 30_000)
    check(d.screen(0) == CameraDirector.Screen(all, null, emptySet()), "Show all: a grid of every included camera")
    d.motion("b", true, 1_000)
    check(d.screen(1_000).spotlight == null && d.screen(1_000).moving == setOf("b"), "in a grid, motion only colors the picture's edge")
    d.choose("c")
    check(d.screen(2_000).spotlight == "c", "OK on a picture: that camera live")
    d.next(2_000)
    check(d.screen(2_000).spotlight == "a", "right goes round to the next camera")
    check(d.back(3_000) && d.screen(3_000).spotlight == null && !d.back(3_000), "Back: to the grid, then out of camera mode")

    d.toggleSelected("c"); d.toggleSelected("a")
    d.switchTo(CameraView.SELECTED, 4_000)
    check(d.screen(4_000).grid == listOf("a", "c"), "Selected only: the selected cameras, in their order")
    d.toggleSelected("a"); d.toggleSelected("c")
    check(d.screen(4_000).grid == all, "with none selected, Selected only shows them all")

    d.switchTo(CameraView.MOTION, 5_000)
    d.motion("b", false, 5_000)
    check(d.screen(5_000).spotlight == "b", "Spotlight on motion: the camera that just moved is live")
    check(d.screen(34_000).spotlight == "b" && d.screen(35_000).spotlight == null, "it stays 30 s after its motion stops, then back to the grid")
    d.motion("a", true, 40_000)
    d.pulse("c", 41_000)
    check(d.screen(42_000).spotlight == "c", "two cameras see motion: the newest wins (a doorbell ring)")
    check(d.back(42_000) && d.screen(42_000).spotlight == null && d.view == CameraView.MOTION,
        "Back sends a motion spotlight away and stays in Spotlight on motion")
    d.pulse("b", 50_000)
    check(d.screen(50_000).spotlight == "b", "…until something moves again")
    d.motion("a", false, 51_000)

    val r = CameraDirector(all, setOf("a", "c"), CameraView.ROTATE, rotateMs = 10_000, holdMs = 30_000)
    check(r.screen(0).spotlight == "a" && r.screen(9_999).spotlight == "a" && r.screen(10_000).spotlight == "c" && r.screen(20_000).spotlight == "a",
        "Rotate: the selected cameras live in turn, 10 s each")
    r.pulse("b", 21_000)
    check(r.screen(22_000).spotlight == "b" && r.screen(51_000).spotlight != "b", "motion interrupts Rotate, then it carries on")
    r.next(52_000)
    val after = r.screen(52_000).spotlight
    check(after != null && after != "b", "right in Rotate moves to the next turn")

    val s = CameraDirector(all, emptySet(), CameraView.SPOTLIGHT, rotateMs = 10_000, holdMs = 30_000)
    check(s.screen(0).spotlight == "a", "Spotlight select starts on the first camera")
    s.previous(0)
    check(s.screen(0).spotlight == "c" && s.back(0) && s.screen(0).spotlight == "a" && s.back(0) && s.view == CameraView.SHOW_ALL,
        "left goes round backwards; Back returns to it, then to Show all")

    println()
    if (failures > 0) {
        println("$failures check(s) failed.")
        kotlin.system.exitProcess(1)
    }
    println("Camera mode chooses as expected.")
}
