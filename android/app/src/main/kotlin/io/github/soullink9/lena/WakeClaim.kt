package io.github.soullink9.lena

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.runBlocking

/**
 * Only one display answers: when this display hears her name, it claims the turn from the Lena integration
 * (wake.py), which waits a moment for other displays that heard the same words and picks the one that heard
 * best ([WakeLevel]). The wake word keeps recording meanwhile ([MicRelay]), so nothing said is lost.
 *
 * The claim is asked on its own thread ([start]) and the wake word looks at the answer as it listens: a
 * display that heard nothing back within [WAIT_MS] answers anyway (Home Assistant restarting, a slow
 * network), since two answers are better than none. Without Home Assistant, or before this display is
 * registered with the integration, there's nothing to ask and it answers.
 */
class WakeClaim private constructor(private val started: Long) {
    companion object {
        /** How long to wait for the answer before answering anyway. */
        const val WAIT_MS = 1_500L

        /** Starts claiming the turn for this display; look at [verdict] as audio comes in. */
        fun start(c: Context, level: Double, score: Float, handheld: Boolean): WakeClaim {
            val claim = WakeClaim(SystemClock.elapsedRealtime())
            val ha = HomeAssistant.from(c)
            if (ha == null || Room.info(c).value == null) {
                claim.answer = true
                return claim
            }
            val display = Room.id(c)
            Thread({
                claim.answer = runCatching { runBlocking { ha.wakeClaim(display, level, score, handheld) } }
                    .onFailure { Logs.w("Wake word", "Couldn't ask Home Assistant which display answers; answering here", it) }
                    .getOrDefault(true)
            }, "lena-wake-claim").start()
            return claim
        }
    }

    @Volatile private var answer: Boolean? = null

    /** True to answer here, false when another display answers, null while it's still being decided. */
    val verdict: Boolean?
        get() = answer ?: if (SystemClock.elapsedRealtime() - started > WAIT_MS) true else null
}
