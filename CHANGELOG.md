# Changelog

Everything in this repository (the Android app, the Home Assistant integration, the Open WebUI filter and tool, the Modelfile and the voice bridge) shares one version. Newest first. Versions follow [Semantic Versioning](https://semver.org/); while it's below 1.0, anything may still change between releases.

## [0.6.7] - 2026-11-04

### Android app

- **New defaults** (asked for in the defaults file): the wake word is **Lena**, listens **Whenever Lena is open**, with **Fewer false starts**; **Her voice follows her mood** is on; **Reply notifications** are off. Only new installs and **Return to defaults** use them.
- **Fixed "Couldn't hear you (error 10)"** after the wake word or the assistant button. Android limits how many speech sessions an app may have open; Lena made a new recognizer for every sheet and started each session before the last had closed. Now one recognizer serves the whole app, a session waits for the last to close, a busy recognizer gets a moment and another try, and the sheet waits for the wake word to let go of the microphone.
- **Connections kept ready:** a request whose connection dropped (Wi-Fi reconnecting as the screen wakes, a connection the server closed while idle) waits for the network and tries again, where that can't send anything twice. Opening the sheet checks Open WebUI and Home Assistant while you talk, signing in again by herself if needed, and asking waits for that check. The wall display checks every few minutes, and when it wakes.
- **The Home dashboard loads once:** the dashboards now have their own task, so switching **Chat | Home** brings the other screen forward instead of destroying it, and Home Assistant no longer starts from scratch each time. Back on the dashboards (when it isn't the home screen) goes back to where you were and keeps them loaded. Lena's page script also looks at the page less often when nothing's changing, and not at all while it's hidden.
- **Return to defaults keeps "Listen for her name"**: switching the microphone on or off is never a reset's call. The defaults file marks the settings Return to defaults leaves alone.

### Home Assistant integration

- **Installs with HACS, and with Home Assistant 2026.11's Marketplace** (HACS built in): the integration moved to `custom_components/lena` at the repository's root, where they look for it, with a `hacs.json`. Released versions only. Copying the folder by hand still works the same. The integration's tests now also run Home Assistant's own checks (hassfest) and HACS's, and report anything Home Assistant says Lena should change.

### Project

- **`tools/wall-display-debloat.sh`** strips a Samsung tablet down to a Lena wall display from a computer with adb: it keeps Lena, the Play Store, Firefox, Calculator, Clock, My Files and Settings (edit the list to keep yours) and what they need, and removes every other Samsung, Google, carrier and preinstalled app it knows to be safe to remove. It previews first, logs what it removes, and `--restore` puts it all back. Its package lists come from the Universal Android Debloater Next Generation project; `tools/debloat_lists.py` updates them. See [Preparing a tablet for the wall display](docs/app/Wall-Display-Tablet.md).

## [0.6.6] - 2026-11-02

### Android app

- **Who answers** (Settings → Connections): Open WebUI, as before, or **Home Assistant Assist**. With Assist, everything that asks her goes to Home Assistant's conversation agent: the sheet however it's opened (assistant gesture, wake word, tile, widget, quick prompts, sharing) and scheduled prompts. Pick the agent, or leave it to Home Assistant. Talking to her continues one Assist conversation; **New conversation** starts another. Assist takes words only (no photos), waits for you to unlock before answering from the lock screen (it can control your home), and its answers aren't saved to your chats: they're on the sheet, or in a scheduled prompt's notification. The chats screen is still Open WebUI's.
- **The wall display's screensaver on every screen:** when Lena is the device's home screen, the screensaver now comes on after **Screen saver after** without a touch whichever screen is showing (the dashboards, your chats, All chats, Memory, Settings, Logs), on one clock shared by all of them. A touch on it always shows the dashboard. The screen never goes dark in this mode, on any screen.
- **Back from your chats after** still takes you back to the dashboards on its own timer, but no longer straight to the screensaver: that keeps its own time, so whichever is shorter comes first.

- **New defaults** (asked for in the defaults file): **Screen saver after** 2 minutes (was 5), and **Each photo for** 1 minute (was 15 seconds). Only new installs and **Return to defaults** use them; what you've already picked stays.

### Project

- **Default settings in an env file:** `adjustments/defaults.csv` is now `adjustments/defaults.env`, which is easier to edit on GitHub. Each setting has a few comment lines (where it is in Settings, its possible values, its current default) and a `NAME=` line: type the new value after the `=` and commit. It's applied before the next build as before (`python3 tools/defaults.py apply`), and a value it can't read is listed in the build's summary. Capitals and a stray plural ("1 minutes") don't matter. **Who answers** has a default there too.

## [0.6.5] - 2026-11-02

### Android app (wall display)

- **Apps and Settings moved to Home Assistant's menu:** they're now rows at the bottom of Home Assistant's sidebar (**Apps** and **Lena settings**), in its own style, so Lena's bar has more room. If a Home Assistant update ever stops them being added, they come back to the bar by themselves.
- **Calendar, To-do and Activity fit again:** their header was hidden but the page stayed below an empty gap. The page now moves up into the space, as on the other dashboards (newer Home Assistant frontends place the page differently from older ones; both work).
- **Apps through the Ingress Proxy** (Music Assistant and the like) no longer show the proxy's own toolbar under Lena's bar.

## [0.6.4] - 2026-11-02

### Android app

- **No more giving up while she thinks.** The wall display's screensaver and the return from your chats now wait while you're talking to her (listening, thinking or speaking) and start counting again once she's done, so a long think no longer ends in the screensaver. Following her answer is also more patient: a dropped connection mid-think is retried for about half a minute, and an answer with no words yet isn't mistaken for a finished one.

### Project

- **Default settings in one spreadsheet:** `adjustments/defaults.csv` lists every setting that has a default, with four columns: the setting (as it's named in Settings), **Change to**, its possible values, and the current default. Fill in **Change to** and commit; before the next build it's checked and applied (`python3 tools/defaults.py apply`) into the app's `Defaults.kt`, which is now the only place the app takes its defaults from. Checks fail and releases won't publish while a change is waiting. This replaces the adjustment template from 0.6.3.

## [0.6.3] - 2026-11-02

No longer called a beta: versions are plain numbers from here on (still below 1.0, so settings may still change).

### Android app

- **Return to defaults** (Settings → About): timings, the wake word, how she looks and sounds, the chat screen, notifications and the screensaver's options go back to how a new install starts. Your connections, model and context size, dashboards and motion sensor, the screensaver's album, your quick and scheduled prompts and your order of pinned chats and folders are kept.
- **Refresh all connections** (Settings → Connections) checks Open WebUI and Home Assistant and reloads your chats and dashboards when you go back to them. It replaces the Reload buttons in the menu and on the wall display.
- **More room at the top:** Lena's logo is no longer in the top bar (it's in the menu).
- **Folders look like chats:** in All chats (and the menu) a folder is a row like the chats around it, in your list style, with a folder icon.
- **Any screen size:** for foldables' outer and inner screens, her clock, the photo frame's clock and the orb shrink on a narrow screen, the menu is never wider than the screen allows, Settings, All chats and Memory & knowledge stay a readable width on a wide screen, and the wall display's bar folds Apps and Settings into a ⋮ menu (and leaves out the time and her mood's words) when it's narrow.

### Project

- **Adjustments:** a template for change requests in `adjustments/` (replaced in 0.6.4 by `adjustments/defaults.csv`).

## [0.6.2-beta] - 2026-11-02

### Android app

- **Settings, regrouped:** back to rows that each open their own page, in new sections. **Connections** (Open WebUI, Home Assistant, and Model, which now also holds the Assistant Lena conversation and her context size), **Wall display** (make it the home screen, make it the screensaver, Screensaver, Dashboards), **Talking to Lena** (make Lena your digital assistant, Wake word, How she looks and sounds, Interacting with Lena), **Chat screen configuration**, **Device customization** (Notifications, Widgets & tile, Quick prompts, Scheduled prompts) and **About** (Updates, Logs, software version). Search stays at the top. Rows that open Android's own settings say whether Lena is already set there.
- **Wake word everywhere:** it can now listen whenever Lena is open, your chats too, not only on the wall display (Wake word → Listens).
- "Start the wall display" is gone from Settings: the Home switch opens the dashboards.

## [0.6.1-beta] - 2026-11-02

### Android app

- **Search is back in the menu.** Results show as many as fit; **See all results** opens them all in All chats.
- **Your own order** for pinned chats and folders: the Reorder button on their headings in the menu, with up and down arrows. Kept on the phone (Open WebUI keeps no order) and used in All chats too.
- **Dashboard mode is the home screen.** Only when Lena's wall display is the device's home screen are the dashboards and your chats full screen, and only then does the screen saver start by itself (and your chats go back to it when left alone). Otherwise Home is a normal screen. The Chat | Home switch keeps the same view both ways.
- **Compact settings:** categories fold open with a summary of how they're set, and a setting with several choices shows only the current one; tap it for the list. How she looks and the model are picked in the main list, and the wall display's rows sit in its category.

## [0.6.0-beta] - 2026-11-02

### Home Assistant integration and Open WebUI

- **Calendar events work.** Home Assistant's own tools can only read calendars, so asking Lena to add an event had nothing to use. She now has `add_calendar_event`, which adds it with Home Assistant's `calendar.create_event`: in Home Assistant's voice assistant (her tool, through the Lena choice) and in Open WebUI and the app (the Lena tool, through the new `lena.add_event` action). It works with calendars exposed to Assist that take new events, in the house's time zone; all-day events take their last day, timed ones last an hour unless given an end, and a mistake comes back as a reason she can act on instead of a false "done". Update the Lena tool in Open WebUI to get it.

### Android app

- **Chat | Home:** Lena's logo and a switch between your chats and your Home Assistant dashboards now sit at the top left of both screens, in place of the model menu and the wall display's Chats button. (Pick a model for one chat with Open WebUI's own menus on; Settings → Model still sets the default.)
- **A new drawer:** pinned chats, your Open WebUI folders (tap to open or close) and recent chats, as many as fit, without scrolling. **View all chats** opens a page with every pinned chat, every folder and your whole history, with search, in your chosen list style.
- **Folders:** long-press any chat to **move it to a folder** (or out of one, or into a new one), as well as pin, rename and delete it. In All chats, make, rename and delete folders (deleting keeps the chats).
- **No more calls:** the call button, the sheet's Voice call, the widget's call button, the Voice call shortcut and the assistant gesture's call choice are gone, and Open WebUI's own call button is hidden in the app. The Quick Settings tile is now **Talk to Lena**.
- **A new look for Open WebUI's pages** in the app, in the spirit of Claude.ai: warm off-white and charcoal, a clay accent, your messages in a soft bubble and her replies in a serif, in light and dark mode. Settings → Chat screen → **Lena's look** turns it off.
- **Settings, reorganised:** a **Search settings** box finds any setting by name or by what it's about, and the list is now in sections by what you're doing: Connections, Talking to Lena, Chats, Prompts, This phone, Wall display and About. Appearance is split into **How she looks** (talking to Lena) and **Chat screen** (Lena's look, Open WebUI's own menus, the Chat | Home switch, pinned chats and folders in the menu, the All chats style). Reply notifications and house alerts share a new **Notifications** page.

## [0.5.2-beta] - 2026-11-02

### Android app

- **Wall display:** Lena's page script and sign-in bridge now stay out of everything on Home Assistant's address that isn't Home Assistant's own frontend in the top frame. Apps proxied through Home Assistant (add-on ingress or the Ingress Proxy integration, such as Music Assistant or Zigbee2MQTT) and iframe cards no longer get Lena's sign-in hooks, and an app opened as the whole page through a "new window" link can't ask the app for the Home Assistant token. Their script errors also stay out of Lena's log.
- **Main screen:** the style that hides Open WebUI's header now applies to the top frame only, so artifacts and previews shown with Open WebUI's "allow same origin" setting keep their own navigation.

### Releases

- Releases are titled with the plain version number, for example **v0.5.2**.

## [0.5.1-beta] - 2026-11-01

### Android app

- **Notes in Memory & knowledge:** a Notes tab for your Open WebUI notes, the same ones as in the browser. Search them, write a new one, change a note's title and text (Markdown), pin or unpin it, or delete it. A note changed in the browser while it was open here isn't overwritten: the app says so, and saving again is your choice. Notes with several pages, and notes shared with you to read only, open read-only (pin works for both).
- When Open WebUI turns a request down because your account isn't allowed to use a feature, the app now says that instead of asking you to sign in (Open WebUI answers both the same way, so the app checks the session first).

## [0.5.0-beta] - 2026-11-01

**V0.5.0 Beta**: the first public release. Lena is a Jarvis-style assistant that runs on your own hardware: one character, with feelings that change for real reasons, in Home Assistant's voice assistant, Open WebUI and an Android app.

### Her character (`ollama/Modelfile`)

- One personality everywhere, built into the Ollama model (Gemma 4 12B by default), so every app gets the same Lena whatever instructions it adds.
- Rules for running the house: report only what actually happened, act on every part of a request, confirm before opening the house to the outside, use friendly names.
- Finding devices reliably: exact names from her context, or by kind and room; tool inputs exactly as each tool defines them.
- Short spoken replies for voice, light formatting when typed; time-of-day awareness; no drafting loops while thinking, and a cap on runaway replies.

### Home Assistant integration (`home-assistant/custom_components/lena`)

- Her **mood, energy and moments** as select entities, changed by her day, the weather (day and night aware), people coming and going, worries in the house and how she's treated, each with a reason that's kept true.
- **Shared memory** as a to-do list, used by voice, Open WebUI and the app alike.
- An **LLM API** with her context and tools (remember, forget, record a feeling) for any conversation agent.
- `/api/lena/context` for Open WebUI and the app: the time said plainly (part of day, light or dark, next sunrise or sunset), who's home, her feelings, her memory and, on request, the exact names of the devices exposed to Assist, by room.
- `/api/lena/media` for the app's photo frame, including Immich albums through Home Assistant's media.
- **Alerts** to your phones through the companion app when something worries her, optionally written in her own words, with an all-clear.
- Configured entirely in the UI; tested against Home Assistant 2026.9 and the latest release.

### Open WebUI (`open-webui/`)

- A filter that adds her current context from Home Assistant to every chat with her, cached for a minute.
- A tool to remember, forget and record how something made her feel.

### Android app (`android/`, Android 16)

- **Your Open WebUI in a native shell:** a drawer with search, pinned chats (with View all) and your chats in six list styles; pin, rename and delete by long-press; Memory & knowledge.
- **Talking to Lena** over any app, already listening: six looks (Orb by default), on-device or server speech-to-text, your server's or the phone's voice, follow-up listening, photos, and an optional private lock-screen mode. It always continues one chat, "Assistant Lena".
- **Sign in by herself:** when Open WebUI ends the session (for example on restart), the app can sign in again with details kept encrypted on the phone.
- **Wall display** for a tablet: your Home Assistant dashboards with one combined bar (menu, views, weather, her mood, chats, reload, apps, settings), a screen saver showing her clock or a photo frame (smart, face-aware framing), Android's screen saver, motion wake-up, and a return to the screen saver after your chats are left alone.
- **Wake word** on the device: "Hey Lena" (default), "Lena" or "Hey Jarvis", with ESPHome-style settling, a voice check against false starts and a sensitivity setting.
- **Scheduled prompts** with names, optional shared chats, many repeat patterns, and the prompt kept out of the chat if you like.
- House alerts, a widget, a Quick Settings tile, quick prompts, share-to-Lena and "Lena finished" notifications.
- **Updates** from GitHub releases inside the app, checked against `SHA256SUMS` and your signing key, with Re-download and install.
- **Logs** in Settings, with secrets redacted.

### Voice (`voice/`)

- A bridge that gives Open WebUI and the app your Piper voices and Whisper speech-to-text over Wyoming, and an example Piper container.

### Known limitations

- Beta: expect rough edges, and settings that may change before 1.0.
- The wall display hides Home Assistant's own header by reaching into its page; a frontend update can bring the header back until the app catches up. Nothing breaks.
- Signing in by herself can't do single sign-on (Authentik, Google and the like).
- The wake word models have their own licences; see [Third-party notices](THIRD_PARTY_NOTICES.md).
