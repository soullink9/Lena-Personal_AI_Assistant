package io.github.soullink9.lena

import android.app.Activity
import android.os.Bundle

/**
 * "Talk to Lena" in a TV's apps: opens her listening, and nothing else. It's its own entry so a remote's
 * customisable button can open it (on a Shield: Settings → Remotes & Accessories → customise a button →
 * Talk to Lena), since the remote's microphone button always belongs to Google Assistant. She then
 * listens through the TV's own speech service ([PhoneEar]); see docs/app/Android-TV.md.
 *
 * Shows nothing itself (Theme.NoDisplay), so it must finish before it would be drawn, which it does.
 */
class TalkActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Logs.i("Remote", "Talk to Lena opened from the TV's apps or a remote button")
        startActivity(AskActivity.intent(this, talk = true))
        finish()
    }
}
