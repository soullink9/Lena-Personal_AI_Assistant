package io.github.soullink9.lena

/** Camera mode's five views (Settings → Cameras → Show at first, and Menu in camera mode). */
enum class CameraView(val label: String, val detail: String) {
    SHOW_ALL("Show all", "Every included camera"),
    SELECTED("Selected only", "The cameras you selected"),
    SPOTLIGHT("Spotlight select", "One camera you choose, live"),
    MOTION("Spotlight on motion", "The camera that sees motion, live"),
    ROTATE("Rotate", "Each camera live in turn"),
}

/**
 * What camera mode shows, from moment to moment: a grid of still pictures, or one camera live (the
 * spotlight). Plain Kotlin, with the time passed in, so tools/camera_check can play it through.
 *
 * - **Show all** / **Selected only**: a grid of every included camera, or of the selected ones (all of
 *   them when none are selected).
 * - **Spotlight select**: one camera live; [next] and [previous] go round them.
 * - **Spotlight on motion**: the grid until a camera sees motion, then that camera live until [holdMs]
 *   after its motion stops. When two see motion, the newest wins.
 * - **Rotate**: each selected camera (or each included one) live in turn, [rotateMs] each; motion
 *   interrupts the same way, then it carries on.
 *
 * In any grid, choosing a camera ([choose]) puts it in the spotlight until [back].
 *
 * @param included the cameras switched on in Settings → Cameras, in their order there (device IDs).
 */
class CameraDirector(
    included: List<String>,
    selected: Set<String>,
    var view: CameraView,
    private val rotateMs: Long,
    private val holdMs: Long,
) {
    /** What's on screen: the grid's cameras, and the one live in the spotlight (null: the grid). */
    data class Screen(val grid: List<String>, val spotlight: String?, val moving: Set<String>)

    var included: List<String> = included
        private set

    /** The cameras selected for Selected only and Rotate (kept to the included ones). */
    var selected: Set<String> = selected.filter { it in included }.toSet()
        private set

    /** A camera chosen by hand, shown live until [back]. */
    private var chosen: String? = null

    /** When each camera's motion started, while it's moving. */
    private val movingSince = HashMap<String, Long>()

    /** When each camera's motion last stopped. */
    private val stoppedAt = HashMap<String, Long>()

    /** Motion before this was sent away with Back (in Spotlight on motion), until a camera moves again. */
    private var dismissedBefore = Long.MIN_VALUE

    /** Where Rotate is: which camera, and since when. */
    private var turn = 0
    private var turnSince = -1L

    /** The grid's cameras now: the selected ones in Selected only (all when none are), every included one otherwise. */
    fun grid(): List<String> =
        if (view == CameraView.SELECTED && selected.isNotEmpty()) included.filter { it in selected } else included

    /** The cameras Rotate takes turns with: the selected ones, or every included one when none are selected. */
    private fun turns(): List<String> = if (selected.isNotEmpty()) included.filter { it in selected } else included

    /** A camera's motion started ([moving] true) or stopped, at [now]. A doorbell's ring is a start and a stop at once. */
    fun motion(camera: String, moving: Boolean, now: Long) {
        if (camera !in included) return
        if (moving) {
            if (camera !in movingSince) movingSince[camera] = now
        } else {
            movingSince.remove(camera)?.let { stoppedAt[camera] = now }
        }
    }

    /** A moment's motion with no stop of its own (a doorbell ring, a detection event). */
    fun pulse(camera: String, now: Long) {
        if (camera !in included) return
        movingSince.remove(camera)
        stoppedAt[camera] = now
    }

    /**
     * The camera motion puts in the spotlight at [now]: of those moving, or stopped less than [holdMs] ago,
     * the one whose motion started (or, for a pulse, happened) last. Null when nothing is.
     */
    private fun byMotion(now: Long): String? {
        val candidates = included.mapNotNull { camera ->
            val started = movingSince[camera]
            val stopped = stoppedAt[camera]
            when {
                started != null -> camera to started
                stopped != null && now - stopped < holdMs -> camera to stopped
                else -> null
            }
        }.filter { it.second > dismissedBefore }
        return candidates.maxByOrNull { it.second }?.first
    }

    /** The cameras showing motion now (moving, or within [holdMs] of stopping), for the grid's colored edges. */
    private fun moving(now: Long): Set<String> = included.filter { camera ->
        camera in movingSince || (stoppedAt[camera]?.let { now - it < holdMs } ?: false)
    }.toSet()

    /** What to show at [now]. */
    fun screen(now: Long): Screen {
        val grid = grid()
        chosen?.let { if (it in included) return Screen(grid, it, moving(now)) else chosen = null }
        val spotlight = when (view) {
            CameraView.SHOW_ALL, CameraView.SELECTED -> null
            CameraView.SPOTLIGHT -> grid.firstOrNull()
            CameraView.MOTION -> byMotion(now)
            CameraView.ROTATE -> byMotion(now) ?: rotating(now)
        }
        return Screen(grid, spotlight, moving(now))
    }

    /** Rotate's camera at [now], moving on every [rotateMs]. */
    private fun rotating(now: Long): String? {
        val list = turns()
        if (list.isEmpty()) return null
        if (turnSince < 0) turnSince = now
        while (now - turnSince >= rotateMs) {
            turn++
            turnSince += rotateMs
        }
        return list[Math.floorMod(turn, list.size)]
    }

    /** Puts [camera] in the spotlight by hand (OK or a tap on its picture). */
    fun choose(camera: String) {
        if (camera in included) chosen = camera
    }

    /** Selects or unselects [camera] (OK held, or a long press), for Selected only and Rotate. */
    fun toggleSelected(camera: String) {
        if (camera !in included) return
        selected = if (camera in selected) selected - camera else selected + camera
    }

    /** Switches view (Menu); a camera chosen by hand goes back to its grid. */
    fun switchTo(newView: CameraView, now: Long) {
        view = newView
        chosen = null
        turnSince = now
    }

    /** The next camera in the spotlight (right, or a swipe): by hand from then on, except in Rotate. */
    fun next(now: Long) = step(+1, now)

    /** The camera before (left). */
    fun previous(now: Long) = step(-1, now)

    private fun step(by: Int, now: Long) {
        val current = screen(now).spotlight ?: return
        if (view == CameraView.ROTATE && chosen == null) {
            turn += by
            turnSince = now
            return
        }
        val list = if (view == CameraView.ROTATE) turns() else grid()
        val at = list.indexOf(current)
        if (list.isEmpty()) return
        chosen = list[Math.floorMod((if (at < 0) 0 else at) + by, list.size)]
    }

    /**
     * Back: a camera chosen by hand goes back to the grid; in Spotlight on motion, the camera motion put
     * there goes back to the grid until something moves again; Spotlight select and Rotate go to Show all.
     * Returns false when there's nothing to go back from (leave camera mode).
     */
    fun back(now: Long): Boolean {
        if (chosen != null) {
            chosen = null
            return true
        }
        if (view == CameraView.MOTION && screen(now).spotlight != null) {
            dismissedBefore = now
            return true
        }
        if (screen(now).spotlight != null) {
            switchTo(CameraView.SHOW_ALL, now)
            return true
        }
        return false
    }
}
