package io.github.soullink9.lena

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.view.ContextThemeWrapper
import android.view.KeyEvent

/**
 * Lena on Android TV (an Nvidia Shield, a TV with Android TV or Google TV built in): she's the wall display
 * only, on the big screen, driven by the remote. There's no keyboard to type with, so there's no chat;
 * you talk to her with the remote instead (see [Remote]). See docs/app/Android-TV.md.
 */
object Tv {
    /** Whether this is an Android TV device ("leanback" is Android's name for the TV interface). */
    fun isTv(c: Context): Boolean = c.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)

    /**
     * Lena's screens are always dark on a TV. A TV is in a living room, often in the evening, and Android
     * TV leaves apps in light mode (it has no dark mode setting), so Settings and the Home Assistant
     * dashboards came up white. Called from each screen's attachBaseContext: night mode for that screen
     * alone, which picks the dark themes (res/values-night), Compose's dark colours (isSystemInDarkTheme)
     * and, through the dark theme, a web page's prefers-color-scheme: dark, which Home Assistant follows
     * when its theme is left on the default. Android keeps it through configuration changes.
     */
    fun darkOnTv(screen: ContextThemeWrapper, base: Context) {
        if (!isTv(base)) return
        screen.applyOverrideConfiguration(Configuration().apply { uiMode = Configuration.UI_MODE_NIGHT_YES })
    }
}

/**
 * The TV remote on one of Lena's screens: OK held down, or the remote's search / voice button when Android
 * lets it through, starts her listening; Menu opens Settings. A short press of OK still presses whatever
 * is chosen on screen.
 *
 * The voice button is Android's own on most TVs: Google Assistant takes it before any app sees it
 * (KEYCODE_ASSIST). Some remotes send it as search (KEYCODE_SEARCH) instead, which reaches the app on
 * screen first, so Lena takes that one. Every key is logged the first time it's pressed (Settings → Logs,
 * "Remote"), so it's easy to see what a remote's buttons send.
 */
class Remote(private val onTalk: () -> Unit, private val onMenu: (() -> Unit)? = null) {

    companion object {
        /** Held this long, OK means "listen" rather than "press". */
        private const val HOLD_MS = 600L

        /** Buttons that mean "listen" when they reach Lena. */
        private val TALK_KEYS = setOf(
            KeyEvent.KEYCODE_SEARCH, KeyEvent.KEYCODE_VOICE_ASSIST, KeyEvent.KEYCODE_ASSIST,
        )

        /** OK on the remote, and Enter on a keyboard or game pad. */
        private val OK_KEYS = setOf(
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_BUTTON_A,
        )

        /** Keys already logged, so each shows once per run. */
        private val logged = mutableSetOf<Int>()
    }

    /** The OK press waiting to see whether it's held (not yet passed to the screen). */
    private var okDown: KeyEvent? = null
    private var held = false

    /**
     * Handles [e] if it's one of the remote's keys for Lena, or passes it on with [pass] (the activity's own
     * dispatchKeyEvent). Returns whether the key was used.
     *
     * OK is held back until it's released: released quickly, the press and release go on to the screen as
     * if nothing happened; held, she listens and the screen never sees it, so nothing is pressed by accident.
     */
    fun dispatch(e: KeyEvent, pass: (KeyEvent) -> Boolean): Boolean {
        if (e.action == KeyEvent.ACTION_DOWN && e.repeatCount == 0 && logged.add(e.keyCode)) {
            Logs.i("Remote", "Button pressed: ${KeyEvent.keyCodeToString(e.keyCode)} (${e.keyCode})")
        }
        when (e.keyCode) {
            in TALK_KEYS -> {
                if (e.action == KeyEvent.ACTION_UP) onTalk()
                return true
            }
            KeyEvent.KEYCODE_MENU -> {
                val menu = onMenu ?: return pass(e)
                if (e.action == KeyEvent.ACTION_UP) menu()
                return true
            }
            in OK_KEYS -> {
                if (e.action == KeyEvent.ACTION_DOWN) {
                    if (e.repeatCount == 0) {
                        okDown = e
                        held = false
                    } else if (!held && e.eventTime - e.downTime >= HOLD_MS) {
                        held = true
                        onTalk()
                    }
                    return true
                }
                if (e.action == KeyEvent.ACTION_UP) {
                    val down = okDown
                    okDown = null
                    if (held) {
                        held = false
                        return true
                    }
                    if (down != null) pass(down)
                    return pass(e)
                }
            }
        }
        return pass(e)
    }
}
