# Changelog

Everything in this repository (the Android app, the Home Assistant integration, the Open WebUI filter and tool, the Modelfile and the voice bridge) shares one version. Newest first. Versions follow [Semantic Versioning](https://semver.org/); while it's below 1.0, anything may still change between releases.

## [0.6.9] - 2026-11-07

### Android app

- **Fixed: the microphone going off after the wake word.** Hearing her name used to close the microphone, then the sheet opened it again a second or two later, so what you said straight after her name was lost and sometimes the microphone was still busy. Now the wake word keeps recording and hands the sound straight to the listening sheet (to the phone's recognizer, or your server's speech-to-text), so she hears you from the moment she wakes. If the recognizer can't take it, it opens the microphone itself as before.
- **Fixed: the screensaver coming on while she's still thinking.** "Busy" was one flag shared by every sheet, so a sheet closing just as a new one opened (the wake word right after a conversation) cleared it for the new one, which then thought for minutes with the screensaver counting down. Each screen now marks itself busy, and Lena's own chat in Assist mode counts as busy while it waits for an answer.
- **Fixed: the wall display stuck on the sheet.** A touch on the sheet stopped it closing by itself for good, and an answer or a voice that never finished kept it up forever. On the wall display the sheet now always goes back once no one has touched any Lena screen for a minute and she's done, and it gives up on itself if she's been listening, thinking or speaking far longer than she ever should.
- **Optimize memory** in Settings → Software version → Development: runs her memory tidy now instead of waiting for her weekly one, shows exactly what changed, with **Undo**.
- **Reminders**, set by her tools: said on the display at the time (after a chime), or shown with one chime when Lena isn't on screen. Timers, alarms and reminders her tools set ring on the display you asked (or the room you named).
- **Her volume:** she can change it when you ask ("speak more quietly"). With **Adjust her volume** (Settings → Talking to Lena → How she looks and sounds, on by default) she also speaks more quietly before 8 AM and after 10 PM, and louder when the room is noisy (measured by the wake word, which is already listening), then the volume goes back to where it was.
- **Speak through a Home Assistant speaker:** Settings → Connections → Home Assistant → **Speak through** sends everything she says to a speaker in Home Assistant, with the text-to-speech voice you choose there (Piper, say). If it fails, the tablet speaks instead.
- **Updates need no setup:** the repository is public, so Settings → Software version → Updates checks Lena's releases without a repository name or a GitHub token. A token saved before is deleted.
- **"Her voice follows her mood" is off by default** for new installs and Return to defaults.
- **Speaking first:** pick a motion sensor under Settings → Wall display → Screensaver → **Speaking first** (it can differ from the one that wakes the screen, so a photo frame can stay a photo frame). When it sees someone come in, she gives a short morning briefing once a day (the weather from your own station, today's calendar, trash day, anything that needs attention), and mentions a worry at home once, never more than once every half hour.

### Home Assistant integration

- **New tools for her, in Assist and Open WebUI alike:** `set_timer`, `set_alarm`, `set_reminder` (at a time or after a while), `cancel_timer` and `list_timers` (from what each display reports), `set_volume`, `announce` (the intercom: "tell the kitchen dinner's ready", optionally listening there for an answer), `update_fact` (corrects a memory in place instead of adding a second one), and `house_history` (when something last happened, how long it lasted, what happened today, from Home Assistant's history, only for what's exposed to Assist). Several in one sentence work: each is its own call. The same are actions (`lena.set_timer` and the rest) for automations and Open WebUI.
- **Displays sync** with `POST /api/lena/display/sync`: they collect what her tools asked of them and report what's set and their volume. Commands not collected within 15 minutes are dropped.
- **Speaking first** (`POST /api/lena/display/briefing`): decides whether a display should say something as someone comes in: the morning briefing once a day per display (from her morning time until noon), a worry once, nothing within half an hour of the last time. Written in her words by her alert agent or Home Assistant's AI Task, or plainly from the facts. What's been said survives restarts.
- **Your weather station:** Configure → **Your weather station** takes your own sensors (an Ecowitt in the backyard). She uses them for what it's like outside right now, before any forecast service, in conversations and her briefing.
- `lena.announce` also takes a **room**.

### Open WebUI

- **The tool** has the new abilities too (timers, alarms, reminders, volume, the intercom, correcting a memory, the house's history). The Lena app tells it which display is asking (`{{LENA_DISPLAY}}`), so timers and reminders ring where you asked. Paste the new version over the old one.

## [0.6.8] - 2026-11-04

### Android app

- **Fixed: nothing heard after the wake word** (since 0.6.7). The fix for "error 10" kept one speech recognizer for the whole app; when Android restarted its speech service while the tablet sat idle, that recognizer never answered again. Each sheet has its own recognizer again, still with the pause between sessions and the retry that keep error 10 away, and a session that doesn't get ready within a few seconds is given up (once more with a new recognizer, then your server's speech-to-text) instead of leaving her listening to nothing.
- **One app again, with the Chat | Home transitions you had:** the dashboards are back in the same task as your chats (0.6.7 gave them their own, which showed Lena twice in Recents). Home still comes up at once: its page is now kept when the screen closes and handed to the next one, so Home Assistant isn't loaded again.
- **Settings:** **Updates** is no longer a row of its own. Tap **Software version** (it looks the same) for a hidden **Development** page with **Updates**, **Logs** and **Return to defaults**. Pages now scroll from anywhere on the screen, side margins included.
- **Apps:** also at the bottom of your chats' menu, as its third button. Apps (there, in Home Assistant's menu and on the dashboards' bar) now shows only on the wall display, where there's no launcher. Its pencil chooses which apps it lists; hidden apps only leave Lena's list.
- **Scheduled prompts go into Assistant Lena** by default, the chat talking to her uses. Ones you made before with no chat named move there too; empty the field for a chat of its own each time.
- **Assist mode works without Open WebUI.** With **Who answers** set to Home Assistant Assist, the chat screen is now Lena's own: one conversation kept on this device where you type to her or tap the mic, and where everything you ask her (spoken, typed, quick and scheduled prompts) is kept with her full answer. A scheduled prompt's notification opens it. **New conversation** starts afresh. Settings that are only about Open WebUI step aside in this mode (its connection, the model, the chat screen's Open WebUI options, reply notifications, a schedule's chat, "your server" for hearing and voice), Memory shows only her own memory, and Open WebUI doesn't need to be set up at all.
- **Lena's own timers and alarms.** Ask her: "set a 10 minute pasta timer", "timer for an hour and a half", "wake me up at 6:45", "set an alarm for 7 pm", "how long is left?", "when is my alarm?", "cancel the pasta timer", "cancel all alarms". She handles these herself, at once, on the device (they don't go to Open WebUI or Assist, and work without a connection). They ring with the alarm sound on Android's alarm clock, so on time even while the tablet sleeps and again after a restart, with a full-screen **Stop** (and **Snooze** for alarms) that also shows over the lock screen; they stop by themselves after 10 minutes. The next one counts down on the dashboards' bar and the screensaver; tap it, or Settings → Device customization → **Alarms & timers**, to see them all, cancel one, or set a quick timer or an alarm by hand.
- **Say "stop"**, like Home Assistant Voice: to a timer or alarm that's ringing, or while she's speaking (with the wake word on).
- **Room-aware, like Home Assistant Voice:** with the Lena integration, the app registers as a display (a device under Lena in Home Assistant) that you put in a room. Assist then hears which display a question came from, so "turn on the lights" means that room's; with Open WebUI she's told the room. Music asked for without a room plays on the room's speaker (Music Assistant's first), or on the one you choose in Settings → Connections → Home Assistant → **Music plays on**; name another room or the whole home and she finds the speakers there.
- **Announcements:** a wall display says what Home Assistant's new `lena.announce` action sends it, and listens for an answer if asked.
- **More wake words:** Home Assistant's standard ones, **Okay Nabu**, **Hey Mycroft** and **Alexa**, and its experimental **Hey Home Assistant**, beside Hey Lena, Lena and Hey Jarvis (Settings → Talking to Lena → Wake word → Her name). They're the microWakeWord models Home Assistant Voice runs, Apache 2.0.

### Home Assistant integration

- **Displays:** each Lena app registers itself (`POST /api/lena/display`) as a device under Lena. Put it in a room in Home Assistant and it becomes room-aware: Assist hears which display a question came from, and the app learns the room's speakers. Displays you no longer use can be deleted.
- **She tidies her own memory:** every 7 days by default, at bedtime, with Home Assistant's AI Task: she combines lines about the same thing, clarifies vague ones, and ticks off outdated ones (never more than half at once, never lines you ticked off). A notification says what changed, and **Lena: Undo memory tidy** puts it back. **Lena: Tidy memory** runs it now; Configure sets how often (0 never) and which AI Task.
- **`lena.announce`:** says a message on chosen displays, or all of them, optionally listening for an answer afterwards. Each display has an **Announcement** event entity that fires with it, usable in automations too.

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
