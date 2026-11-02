package io.github.soullink9.lena

/**
 * Whether Lena is in the middle of a conversation in the sheet: listening, thinking or speaking. The
 * timers that act on "no one's touched it for a while" (the wall display's screensaver, and going back
 * from your chats to the dashboards) wait while she is, and start counting again only once she's done,
 * so a long think never ends in the screensaver.
 */
object Busy {
    /** Set by the sheet (AskActivity) whenever its phase changes; false once it's idle or closed. */
    @Volatile
    var talking: Boolean = false
}
