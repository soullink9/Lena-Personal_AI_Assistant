// Written by tools/defaults.py from adjustments/defaults.env. Don't edit it by hand: type the new
// value after the setting's = sign there and run `python3 tools/defaults.py apply`.

package io.github.soullink9.lena

/**
 * Where every setting starts on a new install, and what Return to defaults goes back to. Prefs.kt
 * reads these; adjustments/defaults.env is where they're changed.
 */
object Defaults {
    /** Connections › Who answers › Lena answers through: Open WebUI */
    val ANSWERS: Answerer = Answerer.OPEN_WEBUI
    /** Connections › Model › Her context size: 32768 */
    const val CONTEXT_TOKENS: Int = 32_768
    /** Wall display › Screensaver › Screen saver after: 2 minutes */
    const val DIM_SECONDS: Int = 120
    /** Wall display › Screensaver › Back from your chats after: 1 minute */
    const val RETURN_SECONDS: Int = 60
    /** Wall display › Screensaver › Each photo for: 1 minute */
    const val SLIDE_SECONDS: Int = 60
    /** Wall display › Screensaver › Transition: Fade */
    val SLIDE_TRANSITION: SlideTransition = SlideTransition.FADE
    /** Wall display › Screensaver › Shuffle: On */
    const val SLIDE_SHUFFLE: Boolean = true
    /** Wall display › Screensaver › Fit: Smart */
    val SLIDE_FRAMING: Framing = Framing.SMART
    /** Wall display › Screensaver › Time and her mood on top: On */
    const val SLIDE_OVERLAY: Boolean = true
    /** Wall display › Screensaver › Her dim clock at night: On */
    const val SLIDE_CLOCK_AT_NIGHT: Boolean = true
    /** Talking to Lena › Wake word › Listen for her name: Off */
    const val WAKE_WORD: Boolean = false
    /** Talking to Lena › Wake word › Listens: Only on the wall display */
    const val WAKE_EVERYWHERE: Boolean = false
    /** Talking to Lena › Wake word › Her name: Hey Lena */
    val WAKE_PHRASE: WakePhrase = WakePhrase.HEY_LENA
    /** Talking to Lena › Wake word › How sure she must be: Balanced */
    val WAKE_SENSITIVITY: WakeSensitivity = WakeSensitivity.BALANCED
    /** Talking to Lena › How she looks and sounds › Her look: Orb */
    val ASK_STYLE: AskStyle = AskStyle.ORB
    /** Talking to Lena › How she looks and sounds › Hearing you: On this phone */
    val HEARING: Hearing = Hearing.PHONE
    /** Talking to Lena › How she looks and sounds › Her voice: Your server's voice */
    val SPEAKING: Speaking = Speaking.SERVER
    /** Talking to Lena › How she looks and sounds › Her voice follows her mood: Off */
    const val MOOD_VOICE: Boolean = false
    /** Talking to Lena › How she looks and sounds › Keep listening: After every reply */
    val FOLLOW_UP: FollowUp = FollowUp.ALWAYS
    /** Talking to Lena › Interacting with Lena › The assistant gesture opens: Talk to Lena */
    val ASSIST: AssistAction = AssistAction.ASK
    /** Talking to Lena › Interacting with Lena › On the lock screen: Off */
    const val LOCK_SCREEN: Boolean = false
    /** Chat screen configuration › Chat screen › Lena's look: On */
    const val LENA_LOOK: Boolean = true
    /** Chat screen configuration › Chat screen › Open WebUI's own menus: Off */
    const val OPEN_WEBUI_MENUS: Boolean = false
    /** Chat screen configuration › Chat screen › Chat | Home switch: On */
    const val HOME_SWITCH: Boolean = true
    /** Chat screen configuration › Chat screen › Pinned chats: 5 */
    const val PINNED_SHOWN: Int = 5
    /** Chat screen configuration › Chat screen › Folders: On */
    const val DRAWER_FOLDERS: Boolean = true
    /** Chat screen configuration › Chat screen › All chats list style: Slim groups */
    val CHAT_LIST: ChatListStyle = ChatListStyle.SLIM
    /** Device customization › Notifications › Reply notifications: On */
    const val NOTIFY_REPLIES: Boolean = true
    /** Device customization › Notifications › Alerts on this phone: Off */
    const val ALERTS: Boolean = false
    /** Device customization › Widgets & tile › Her mood on the widget: Off */
    const val WIDGET_MOOD: Boolean = false
}
