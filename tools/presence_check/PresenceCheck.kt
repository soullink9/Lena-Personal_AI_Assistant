// Checks what a display reports it's doing (DisplayPhase) and how clearly it heard her name (WakeLevel),
// away from Android. Run by run.sh next to this file.
import io.github.soullink9.lena.DisplayPhase
import io.github.soullink9.lena.DisplayPhase.Phase
import io.github.soullink9.lena.WakeLevel

private var failures = 0

private fun check(ok: Boolean, what: String) {
    println((if (ok) "ok    " else "FAIL  ") + what)
    if (!ok) failures++
}

fun main() {
    val sheet = Any()
    val next = Any()
    val heard = mutableListOf<Phase>()
    val stop = DisplayPhase.listen { heard += it }
    check(DisplayPhase.now == Phase.IDLE, "a display starts idle")
    DisplayPhase.set(sheet, Phase.LISTENING)
    check(DisplayPhase.now.ha == "listening", "listening is reported in Home Assistant's words")
    DisplayPhase.set(next, Phase.PROCESSING)
    DisplayPhase.set(sheet, Phase.IDLE)
    check(DisplayPhase.now == Phase.PROCESSING, "a sheet closing doesn't reset a newer one that's thinking")
    DisplayPhase.set(sheet, Phase.RESPONDING)
    check(DisplayPhase.now == Phase.RESPONDING, "speaking wins over thinking")
    DisplayPhase.set(sheet, Phase.IDLE)
    DisplayPhase.set(next, Phase.IDLE)
    check(DisplayPhase.now == Phase.IDLE, "idle once every screen is done")
    stop()
    DisplayPhase.set(sheet, Phase.LISTENING)
    DisplayPhase.set(sheet, Phase.IDLE)
    check(heard == listOf(Phase.LISTENING, Phase.PROCESSING, Phase.RESPONDING, Phase.PROCESSING, Phase.IDLE),
        "each change is heard once, and not after the listener stops ($heard)")

    val near = WakeLevel()
    val far = WakeLevel()
    repeat(10) { near.hear(-60.0, -60.0); far.hear(-62.0, -62.0) }
    repeat(8) { near.hear(-30.0, -55.0); far.hear(-50.0, -60.0) }
    check(near.aboveNoise() > far.aboveNoise(), "the display near the person heard her name more clearly")
    check(near.aboveNoise() in 29.0..31.0, "the level is measured over the noise from before she was called (${near.aboveNoise()})")
    val tv = WakeLevel()
    repeat(10) { tv.hear(-35.0, -35.0) }
    repeat(8) { tv.hear(-32.0, -34.0) }
    check(tv.aboveNoise() < far.aboveNoise(), "a display next to a TV doesn't win by being noisy")
    check(WakeLevel().aboveNoise() == 0.0, "nothing heard, nothing claimed")

    if (failures > 0) {
        println("$failures check(s) failed")
        kotlin.system.exitProcess(1)
    }
    println("All presence checks passed.")
}
