package io.github.soullink9.lena

/**
 * Settings → Connections → Refresh all connections, in place of separate reload buttons: it checks Open
 * WebUI and Home Assistant again, and every open screen (your chats, the dashboards) reloads its page the
 * next time it's shown.
 */
object Refresh {
    /** Goes up by one on each refresh; a screen compares it with the one it last loaded at. */
    @Volatile
    var generation = 0
        private set

    /** Asks every open screen to reload its page when it's next shown. */
    fun request() {
        generation++
    }
}
