# Changelog

Everything in this repository (the Android app, the Home Assistant integration, the Open WebUI filter and tool, the Modelfile and the voice bridge) shares one version. Newest first. Versions follow [Semantic Versioning](https://semver.org/); while it's below 1.0, anything may still change between releases.

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
