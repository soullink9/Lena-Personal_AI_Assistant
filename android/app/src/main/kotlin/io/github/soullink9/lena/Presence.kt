package io.github.soullink9.lena

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * What this device is doing, in the words of Home Assistant's Assist satellites: idle, listening, thinking
 * (processing) or speaking (responding). [DisplaySync] reports it to the Lena integration at once, and the
 * display's Assist satellite follows it, so a light can pulse while she thinks.
 *
 * Each screen marks itself ([set]) rather than sharing one value, as [Busy] does: a sheet closing just as a
 * new one opens never resets the new one. When two screens are doing something, the later stage wins
 * (speaking over thinking over listening).
 *
 * Plain Kotlin, checked by tools/presence_check.
 */
object DisplayPhase {
    enum class Phase(val ha: String) { IDLE("idle"), LISTENING("listening"), PROCESSING("processing"), RESPONDING("responding") }

    private val owners = ConcurrentHashMap<Any, Phase>()
    private val listeners = CopyOnWriteArrayList<(Phase) -> Unit>()

    /** What the device is doing now. */
    @Volatile
    var now: Phase = Phase.IDLE
        private set

    /** [owner] (a screen) is now doing [phase]; IDLE when it's done. Listeners hear of a change at once. */
    @Synchronized
    fun set(owner: Any, phase: Phase) {
        if (phase == Phase.IDLE) owners.remove(owner) else owners[owner] = phase
        val was = now
        now = owners.values.maxByOrNull { it.ordinal } ?: Phase.IDLE
        if (now != was) listeners.forEach { it(now) }
    }

    /** Calls [listener] with each change, until the returned function is called. */
    fun listen(listener: (Phase) -> Unit): () -> Unit {
        listeners.add(listener)
        return { listeners.remove(listener) }
    }
}

/**
 * How clearly the wake word heard her name, for deciding which display answers when several hear it
 * (the integration's wake.py): the loudest moment of the last second and a half, in decibels above the
 * room's background noise as it was just before. So a display near the person wins over one across the
 * house, and a display next to a TV doesn't win by being noisy.
 *
 * Fed every 80 ms by the wake word's listening loop ([hear]). Plain Kotlin, checked by tools/presence_check.
 */
class WakeLevel {
    private companion object {
        const val SIZE = 25 // 2 seconds of 80 ms pieces
        const val SPEECH = 18 // her name and a moment either side: the last 1.4 seconds
    }

    private val levels = DoubleArray(SIZE) { -90.0 }
    private val floors = DoubleArray(SIZE) { -70.0 }
    private var next = 0
    private var count = 0

    /** One piece's level (dB below full scale) and the room's background level at the time. */
    fun hear(db: Double, floor: Double) {
        levels[next] = db
        floors[next] = floor
        next = (next + 1) % SIZE
        count = minOf(count + 1, SIZE)
    }

    /** Decibels above the room's noise: the loudest recent piece, over the background before it began. */
    fun aboveNoise(): Double {
        if (count == 0) return 0.0
        val recent = minOf(SPEECH, count)
        var peak = -90.0
        for (i in 1..recent) peak = maxOf(peak, levels[(next - i + SIZE) % SIZE])
        val before = floors[(next - count + SIZE) % SIZE] // the oldest one kept: from before she was called
        return maxOf(0.0, peak - before)
    }
}
