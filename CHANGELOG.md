# Changelog

Lena's parts are versioned separately: the **app** has its own version (shown in its settings and on each release), and her **personality** (the Modelfile, the Home Assistant files and the Open WebUI files) has another. Newest first. Versions follow [Semantic Versioning](https://semver.org/).

- [App](#app)
- [Personality](#personality)

## App

### 2.17.3 (2026-10-31)

- **No back button in the wall display's bar.** Use the back gesture (or button): it still goes up out of a subview, or back through what you opened. The menu button stays.
- **Updates point at Auto Blocker, not Play Protect.** Step 2 now opens Security and privacy, where Samsung's Auto Blocker (which stops installs from outside the store) is switched off, and the reminder after the update says to turn Auto Blocker back on. Samsung doesn't let apps open the Auto Blocker page itself, so it opens the page it's listed on.

### 2.17.2 (2026-10-31)

- **One bar on Home Assistant's own pages too.** Its header was only hidden on your dashboards; now it's also gone on Overview, Energy, Calendar, To-do, Map, Logbook, History, Media and the other built-in pages that use its standard app bar. A second row of filters, where a page has one, stays.

### 2.17.1 (2026-10-31)

- **"Not signed in" in the logs is now only that.** Open WebUI answers a request for a chat that no longer exists with the same HTTP 401 as a signed-out one; Lena now reads its message, logs a deleted chat as "not found" (information, not a warning) and treats it as gone. A deleted "Assistant Lena" is forgotten so the next talk starts a fresh one, and the wall display's clock stops asking for it. A real sign-out says so, with Open WebUI's reason and, if it's off, a pointer to Sign in by herself.

### 2.17.0 (2026-10-31)

**Wall display**
- **One bar.** Home Assistant's own header is gone; Lena's bar has its back and menu (sidebar) buttons, the time and weather, the dashboard's views as tabs, her mood and talk button, and Chats, Reload, Apps and Settings. The dashboard's name isn't shown. With several dashboards set up, a dashboards button replaces the old tab row. Editing a dashboard brings Home Assistant's header back while you edit.
- **No more listening by itself.** The wake word could set itself off the moment the wall display came up (after a reboot, say), before its sound features had settled. Now she can't be woken in her first second of listening, nor again until she's calmed down, as on ESPHome's voice devices.
- **Your chats give way back to it.** Opened from the wall display and left alone (a minute by default), your chats go back to the dashboards and straight to the screen saver; not while you type, call or she's answering.
- **Screen saver after** now goes from 30 seconds to an hour.
- **Photo frame framing.** **Smart** (the default) shows portrait photos, panoramas and other odd shapes whole instead of cropping their middles (heads included), and every cropped photo is centred on its faces, found on the device. **Fill the screen** and **Whole photo** are the other choices.

**Wake word**
- **"Hey Lena"**, Tater Totterson's microWakeWord model, is in the app and the default for new setups: about one false start in ten hours of background sound.
- **Fewer false starts:** ESPHome's voice check must agree someone is speaking, each model's own cutoff and window are read from its file, and **How sure she must be** (Fewer false starts, Balanced, Easier to wake) is a setting. The logs say how sure she was each time.

**Open WebUI**
- **Sign in by herself** (Settings → Open WebUI): when Open WebUI ends her session, as it can on restart, Lena signs in again with your saved email (or LDAP user) and password, encrypted on the phone, everywhere from the chat page to scheduled prompts.
- **"Assistant Lena".** Talking to her always uses one chat by that name, found by name on any phone; the old "Lena" chat is renamed. New conversation keeps the old one, dated and unpinned.
- **Pinned chats** show at the top of the menu (up to five, then View all), and long-press has **Pin**.
- **The orb and slim groups** are the defaults and come first; anyone still on the old defaults moves to them.
- **She knows the time of day:** the phone's time and part of the day go with every question.

**Scheduled prompts**
- Each has a **name**, shown in Settings; tap it to open it.
- **A chat to go into:** prompts with the same chat name share one history (a morning and an evening briefing, say).
- **Repeats** every day, on weekdays, weekends or the days you pick, every few hours, once a month, or once. **Run it now** tries one out.

**Updates**
- **Re-download and install** gets the latest release again, to repair an install or after a version reset. Downloads are judged by build number, so a reset version name still updates, and a lower build number is refused with a clear reason.

### 2.16.0 (2026-10-28)

- **She goes back to the wall display by herself.** Called by her name (or the talk button) from the dashboard or the screen saver, she returns to it when the conversation's over: when you say nothing, when she's answered and isn't listening for more, or straight away on "thanks", "that's all", "never mind", "stop" and the like. The wake word then works again; before, the sheet stayed open on top and it stopped listening. A touch keeps her open.
- **Saying just "thanks" or "that's all"** closes the sheet anywhere, without asking her anything.
- **The dashboard stays dimmed** under her when the wake word wakes her from its idle screen, and comes back dimmed.
- **Reload button** in the wall display's top strip.
- **App names are readable** in the wall display's Apps list (white on dark).

### 2.15.0 (2026-10-28)

- **The screen stays on while she listens, thinks and speaks**, so a long question isn't cut off when the phone would normally sleep.
- **One Wall display section in Settings**, replacing Dashboard mode, Display & screen saver and the separate wake word entry. Its page has Start, Make it this tablet's home screen, and three sub-pages: **Dashboards**, **When no one's around** (when it steps aside, her clock or the photo frame and its options, and what wakes it up) and **Wake word**, plus the screen saver. Long option lists became single rows you tap to choose from.

### 2.14.0 (2026-10-28)

- **Logs** (Settings → Logs): Lena's own log of problems and what she was doing, crashes included, with a Problems view, search, copy, share and clear, plus Android's log for her current run. Errors and events only; never messages, answers or tokens.
- **Updates** (Settings → Updates): check your GitHub releases, download, and install from the app, with a read-only GitHub token for a private repository. Each download is checked against the release's SHA256SUMS, and that it's Lena, newer and signed with your key. It waits until you've allowed the install (and paused Play Protect if needed), so you don't download twice; it's deleted once the update is in, with a reminder to turn Play Protect back on.

### 2.13.0 (2026-10-28)

- **Photo slideshow** (Settings → Display & screen saver): an album from Home Assistant's media, such as an Immich album, instead of the clock in the screen saver, display mode and dashboard mode's idle screen. Choose how long each photo shows, a fade, slide, slow zoom or no transition, shuffle, fill or whole photo, the time and her mood on top, and the clock at night. Needs the Lena integration 1.2.0.
- **Nothing downloaded at build time for the wake word**: the Hey Jarvis models and the front end check's C sources are now in the repository, like the Lena model.

### 2.12.0 (2026-10-27)

- **Dashboard mode** (Settings → Dashboard mode): your Home Assistant dashboards on a tablet, always on, signed in with Lena's token (no login page), with Lena across the top: time, weather, her mood, talk, chats, apps and settings. Several dashboards become tabs. It dims to her night clock after a few idle minutes and wakes on a touch, a motion sensor you pick, or when she's worried, which also turns the top into a banner with what's wrong.
- **Lena can be a tablet's home screen**, with an Apps button for everything else.
- **"Lena" wake word**, now the default: a microWakeWord model, with the app computing exactly the sound features Home Assistant's voice satellites do (checked bit for bit against the original on every build). "Hey Jarvis" is still there to choose. The wake word has its own page and works in dashboard mode too.

### 2.11.0 (2026-10-27)

- **Display mode** (Settings → Display mode): the time, date, weather, how she feels and the last thing she said, full screen on black, for a docked phone or a tablet on the wall. Tap to talk. It drifts slightly against burn-in and goes dim and warm at night.
- **Lena as the screen saver** (Android settings → Display → Screen saver → Lena), so the display appears by itself while the phone charges. Over the lock screen it keeps her reasons and what she said private.
- **"Hey Jarvis" wake word** in display mode and the screen saver, off by default. It runs entirely on the phone with openWakeWord's models; **Try it** shows how well it hears you. Some phones block the microphone in the screen saver; it says so when that happens.
- **House alerts on this phone** (Settings → Home Assistant): a 15-minute backup check that tells you when she's worried about something at home. Tapping an alert opens Lena listening.

### 2.10.0 (2026-10-27)

- **Six looks for talking to Lena** (Settings → Appearance, with **Try it**): the **Sheet** you know; **Glow**, colours running round the screen's edges that follow your voice; **Bar**, a compact bar at the bottom with her answer in a card above it; **Orb**, her logo pulsing in the middle of a darkened screen with her words as captions; **Island**, a small pill at the top whose answer drops down under it; and **Subtitles**, just her words at the bottom of the screen.
- **Six ways to list your chats** in the drawer (Settings → Appearance): rounded groups, flat list, slim groups, title and time, timeline, and folding days.
- **Swipe right from the left edge to open the drawer** instead of going back (the middle stretch of the edge; Android reserves the rest for back).

### 2.9.0 (2026-10-27)

- **Works with the Lena integration in Home Assistant.** Her feelings come from it, so any Home Assistant user's token works (no admin rights). **Test connection** says if the integration isn't set up yet.
- **No memory list setting:** her memory is always the integration's `todo.lena_memory`. Its tab in **Memory & knowledge** is now called **Hers**.
- **Memory & knowledge and the drawer** use the same rounded, grouped look as Settings: tabs as a segmented switch, an **Add a memory** row, and chats grouped by day.

### 2.8.0 (2026-10-27)

- **Settings, redone like a phone's own settings:** one short list of categories in rounded groups, each showing what it's set to (Open WebUI, Home Assistant, Model, Assistant gesture, Voice, Conversation, Quick prompts, Scheduled prompts, Widget & tile). Tap one for its page. Reply notifications and Open WebUI's own menus are switches right on the list.
- **No Save button:** changes save as you make them. Addresses save once they're valid https:// addresses, and changing your server signs you in again when you leave settings.
- **Model** is now a list of your server's models to pick from, with **Lena, automatically** first.

### 2.7.0 (2026-10-27)

- **Settings, redesigned:** grouped into cards (Open WebUI, Home Assistant, Chats, Assistant gesture, Talking to Lena, Quick prompts, Scheduled prompts, On your phone) under a large collapsing title, with Save always at hand.
- **Show Open WebUI's own menus** moved from the drawer into Settings → Chats.
- **A roomier drawer:** New chat, Temporary chat and Voice call are now only in the top bar, and Settings, Memory & knowledge and Reload are three icon buttons along the bottom, so search and your chats get most of the space. Lena's name, server status and mood share one header.
- **Top bar:** Temporary chat joins New chat and Voice call, and her avatar shows next to the title when you're talking to Lena.
- **Ask sheet:** Voice call and Open in Lena moved into a ⋮ menu, leaving room for her name and mood.

### 2.6.0 (2026-10-27)

- **Show her a photo:** a camera button in the sheet takes or chooses a photo to ask about, by typing or by voice. It's saved in the chat. Uses Android's photo picker, so Lena never gets access to your gallery, and asks for no new permissions.
- **Her voice follows her mood** (off by default): a little slower and lower when she's groggy or wistful, quicker and brighter when she's wired or cheerful. Needs Home Assistant.
- **Her mood on the widget** (off by default): under her name, updated every half hour and after you talk to her. Mood only, never the reasons.
- **Talk to Lena on the lock screen** (off by default): the gesture opens her without unlocking, in a private mode: a fresh conversation with your history hidden, no reasons, no photos, nothing opens until you unlock, and she's asked not to unlock, open or share anything personal.

### 2.5.0 (2026-10-27)

- **New icon:** Lena's logo, on its own dark background, with a silhouette for Android's themed icons. The launch screen uses it too.
- Her logo as a round avatar in the sheet, the drawer, settings and the widget, and on "Lena finished" notifications. The notification icon is the logo's silhouette.

### 2.4.0 (2026-10-27)

- **Lena by default:** with no model chosen, new chats, the sheet and scheduled prompts use `lena` (the model built from the Modelfile) when your server has it.
- **Spoken replies sound spoken:** when you talk to her in the sheet, Lena tells her the reply will be read aloud, so she keeps it short and plain, with no lists or formatting. Typed chats are unchanged.
- **Home Assistant, optional:** a new settings section for your Home Assistant address and a long-lived access token (stored encrypted with a keystore key, HTTPS only), with a **Test** button.
- **How she feels** shows under her name in the sheet and in the drawer, with the reason, and refreshes after each reply.
- **Her shared memory** gets its own **Shared** tab in Memory & knowledge, next to Open WebUI's memories and your knowledge: search, fix, forget or add.

### 2.3.1 (2026-10-27)

- Fixed the Lena chat showing only her last reply when you opened it in the full app. Open WebUI 0.12 needs the question sent with each answer to keep the conversation linked; Lena now sends it.
- If your Lena chat already shows only the last reply, tap **New conversation** in the sheet once. The broken chat can't be repaired, but new ones keep their full history.

### 2.3.0 (2026-10-27)

- **Memory & knowledge** (in the drawer): see, search, fix, add and forget Lena's memories about you, and remove documents from your knowledge collections.
- Talking to Lena now uses Open WebUI's memory feature when it's on for you, like the web page. Before, it was always off.
- The sheet opens on the whole ongoing conversation instead of the last exchange or two.

### 2.2.1 (2026-10-27)

- The ongoing conversation now gets half of Lena's context instead of a fixed 15 exchanges: usually 50 to 80 spoken exchanges with a 32k model. New **Lena's context size** setting (32,768 by default).

### 2.2.0 (2026-10-27)

- **One ongoing conversation:** talking to Lena now continues the same chat every time (titled **Lena** and pinned in Open WebUI), so she remembers earlier conversations. The sheet shows where you left off. If you delete the chat, she starts a new one next time.
- **New conversation** button in the sheet to start over.
- The model sees the last 15 exchanges of a long conversation; the chat keeps everything.
- Setting to go back to a new chat each time.

### 2.1.1 (2026-10-27)

- Fixed "Couldn't hear you" after Lena spoke, which also stopped the mic from opening again. The phone's recognizer is now kept for the whole conversation, retried if it's busy, and your server's speech-to-text takes over if the phone can't listen.
- **Keep listening** now has three choices: **After every reply** (new default), **Only when Lena asks a question**, or **Off**. Saying nothing after a reply closes the mic quietly.
- Listening warnings now say what went wrong.

### 2.1.0 (2026-10-27)

- **Talk to Lena:** the assistant gesture now opens a sheet that's already listening, like Google Assistant or Home Assistant's Assist. Your words show as you speak, Lena answers with your tools and reads the reply aloud in your Open WebUI voice, and if she asks a question she listens for your answer. Follow-ups continue the same chat.
- New **Talking to Lena** settings: hear you on the phone (on-device) or with your server; speak with your server's voice, the phone's, or not at all; keep listening after questions.
- **Rename and delete chats:** long-press a chat in the drawer.
- **Talk to Lena** on the widget and the app icon's long-press menu; the typed version is now **Type to Lena**.

### 2.0.2 (2026-10-27)

- Open WebUI follows your phone's dark mode again, including when you switch it while Lena is open (you stay on the same chat).

### 2.0.1 (2026-10-27)

- Fixed the main screen showing blank white instead of Open WebUI.

### 2.0.0 (2026-10-27)

A native app around your Open WebUI. Needs Android 16.

- **Native shell:** a top bar with a model picker, New chat and Voice call, and a side drawer with your chats, search, Temporary chat, server status and settings, in Material You colours. Open WebUI's own header and sidebar are hidden (switch them back on from the drawer).
- **Ask Lena sheet:** quick questions over any app, answered on your server with your model's tools and saved to your history. The assistant gesture opens it by default.
- **Share to Lena** from any app.
- **Quick Settings tile** and **home screen widget**.
- **Quick prompts** in the Ask sheet, on the widget and on the app icon's long-press menu.
- **"Lena finished" notifications** when you leave mid-answer.
- **Scheduled prompts** that run daily and notify you with the answer.
- **New settings screen.**
- Rewritten in Kotlin with Jetpack Compose.

### 1.1.0 (2026-10-27)

- Single sign-on: a new **Sign-in address** setting lets Authentik (or another sign-in page) load inside Lena, so sign-in finishes in the app instead of your browser. Sign-in pages never get the microphone.

### 1.0.0 (2026-10-27)

First release.

- Open WebUI full screen, locked to one `https://` server
- Selectable as the phone's digital assistant; the gesture opens a new chat in call mode on a chosen model
- Call and Settings shortcuts
- Microphone for your server only; Android file picker for attachments
- No libraries, no analytics; Safe Browsing lookups, web view usage reports and backups turned off

## Personality

### 3.3.1 (2026-10-31)

- **No more going in circles.** With thinking on, she could spend minutes redrafting a one-line answer ("They're closed. I'm not sure what you're planning…") until the reply ran out. The Modelfile now tells her to think about facts and actions only, never wordings, and caps a reply at 4096 tokens (`num_predict`) so a loop can't run on. Turn thinking off for her in Open WebUI (Workspace → Models → lena → Advanced Params → Think) too: house questions don't need it.
- **Finding devices:** she asks Home Assistant for a kind or a room (curtains and blinds are covers) instead of guessing names, tries the other way once if nothing's found, and doesn't search for tools she already has. Rebuild the model: `ollama create lena -f Modelfile`.

### 3.3.0 (2026-10-31)

- **Time of day she can't get wrong.** The Lena integration (1.3.0) now tells her the part of the day and whether it's light or dark out, with the next sunrise or sunset from Home Assistant's sun ("8:05 PM: evening, and dark out (the sun rises at 7:16 AM)"), and the Modelfile tells her to go by it for greetings and anything about the time of day. Rebuild the model, copy the new `custom_components/lena` folder over the old one, and restart.

### 3.2.1 (2026-10-28)

- **No sunshine after dark.** Some weather services still report "sunny" at night; the Lena integration (1.2.1) now checks Home Assistant's sun (or the clock), calls it a clear night, and doesn't treat sunset (sunny to clear night) as news. She used to say "The sun just came out." at 8 PM.
- **Her mood's reason stays true.** Every hour it's checked: news like "It just started raining" becomes "It's raining." after an hour, and a reason that's no longer true (the weather changed, someone came home, her energy moved on) is dropped. The mood itself lingers, the way moods do. Copy the new `custom_components/lena` folder over the old one and restart.

### 3.2.0 (2026-10-28)

- The Lena integration (1.2.0) adds `/api/lena/media`: Home Assistant's media folders and photos (Immich albums included) for the app's photo slideshow. Copy the new `custom_components/lena` folder over the old one and restart.

### 3.1.0 (2026-10-27)

- **Alerts:** the Lena integration (1.1.0) can tell your phones when she's worried, instantly through the Home Assistant companion app, written in her own words by your Lena agent, with an all-clear when it's sorted. Tapping one opens the Lena app listening. Set it up under Configure.
- `/api/lena/context` now includes the weather, for the app's display mode.

### 3.0.1 (2026-10-27)

- The Lena integration shows her logo in Home Assistant: on Devices & services, the Add integration list and the integration's page, with a dark-theme version. Copy the new `custom_components/lena` folder over the old one and restart, then refresh the browser page.

### 3.0.0 (2026-10-27)

- **One Home Assistant integration replaces everything else there.** Copy `custom_components/lena` into Home Assistant, add **Lena** in Devices & services, and tick **Lena** next to **Assist** in your Ollama agent. It creates her mood, energy and moment dropdowns and her memory list, runs her day, and gives her the `remember_fact`, `forget_fact` and `record_feeling` tools. No helpers, blueprints, scripts, custom template or pasted prompt.
- **How each feeling sounds** lives on its own in `feelings.yaml`, in the integration's folder: edit it and reload the integration.
- **Settings in the UI:** the people she misses, weather, special-days calendars, things that worry her, her day, and how quickly her feelings move, under **Configure**.
- **Shared memory** is the integration's `todo.lena_memory`. She reads it on every request, so there's no recall step.
- **Open WebUI filter and tool 2.0.0:** the filter gets everything from the integration's new `/api/lena/context` address, and any user's token works. Paste both files over the old ones and set the tool's `memory_list` to `todo.lena_memory`.
- Tested automatically against Home Assistant 2026.9 and the latest release.
- **Upgrading from 2.x:** add the integration, then delete the old Lena mood engine automation, the Lena feel and Lena recall scripts, the blueprints, `custom_templates/lena.jinja`, her five feelings helpers and the old memory list (copy anything you want to keep into **Lena Memory** first), and empty the Ollama agent's Instructions.

### 2.0.2 (2026-10-27)

- Sounds more like a person. Asked who she is, she gives her name and a line with some attitude instead of describing servers, speakers and "not human, but not a gadget". New rules: show her character instead of describing it, plain punctuation with no em dashes, and no "not this, but that" or tidy summing up. Rebuild the model to pick it up.

### 2.0.1 (2026-10-27)

- Open WebUI and the app now tell her the date and time at home. The filter asks Home Assistant, so it's in your house's time zone; before, she had no clock in chats and guessed. Update the filter in Open WebUI by pasting the new `lena_filter.py` over the old one.
- Her Home Assistant prompt now includes the date as well as the time.

### 2.0.0 (2026-10-27)

A rebuild of how she's set up. Nothing from 1.x carries over: create the new helpers, and remove the old five helpers, the persona engine automation and the memory sensor if you had them.

- **One character, everywhere.** Her whole baseline is in the Modelfile as `MESSAGE system`, which Ollama always sends first, even when an app sends its own instructions. Before, Home Assistant and Open WebUI replaced it, which is why the Home Assistant prompt had to repeat her character. The prompt is now a few lines.
- **Base model `gemma4:12b`**, with Google's recommended sampling.
- **More sass, more alive:** a polished character with opinions, likes and pet peeves, a sense of living in the house, and clear rules for showing feelings without overdoing them.
- **Feelings that change for real reasons:** a new Lena Mood Engine replaces the persona engine. Three layers (mood, energy, and short-lived moments) instead of five, each with a reason she knows. Mood drifts only to neighbouring moods; the weather, people coming and going, an empty house, special days on your calendar and worry sensors all move her.
- **Only the current feeling reaches her:** each feeling's description lives in `custom_templates/lena.jinja`, and only the current ones are added to her prompt.
- **She reacts to how she's treated:** the Lena feel script lets her set her own moment when thanked, insulted or asked the same thing again.
- **One shared memory, no YAML:** her memory to-do list is read with the Lena recall script in Home Assistant, and shared with Open WebUI and the app. The YAML memory sensor is gone.
- **Open WebUI:** a filter brings her feelings and memory into every chat, and a tool lets her remember, forget and react, in the browser and in the Lena app.
- **Docs:** a step-by-step [Getting started](docs/Getting-Started.md) for Ollama, Open WebUI, the app and Home Assistant, and new pages on [her personality](docs/personality/Personality.md) and [her feelings](docs/personality/Moods.md).

#### Known issues

- Not yet tested end to end on real hardware: the templates and every mood engine branch are checked offline, and the Open WebUI files against a stand-in Home Assistant. Reply speed with Gemma 4 hasn't been measured.
- Smaller models may call the Lena feel script too often, or claim to have saved a memory without calling the tool. See [Troubleshooting](docs/home-assistant/Troubleshooting.md).

### 1.1.0 (2026-10-27)

- Moved into the Lena repository alongside the app, so there's one place for everything. Files are now in `ollama/`, `home-assistant/` and `voice/piper/`.
- Context size raised to 32,768 (`num_ctx` in the Modelfile and the advice for Home Assistant's agent), matching the app's default.
- The wiki became `docs/`, with a new [Overview](docs/Overview.md) of how Home Assistant, Open WebUI and the app fit together, and instructions for adding the blueprint from a private copy.

### 1.0.0 (2026-10-18)

First public release.

#### Included

- **Modelfile** for Lena with a portable personality, 16k context and sampling settings suited to a 16 GB GPU.
- **Home Assistant prompt** with voice rules, accuracy rules, a memory section, household context and a full legend for every mood, energy, delivery, flavor and reaction value.
- **AI Persona Engine blueprint** driving mood, energy, delivery, flavor and an automatic reaction reset. Every value is checked against the helper's real options, and no pool can be empty, so it does not error on missing options.
- **Memory** as a Home Assistant to-do list, with a template sensor that exposes the open items to the prompt.
- **Examples** for Ollama with a keep-alive watchdog, and for Piper text-to-speech.
- **Wiki source** under `wiki/` (now `docs/`).

#### Known issues

- **Slow replies.** On the author's setup (a 9B-class model fully on an RTX 4070 Ti Super), one short request took about 34 seconds in Home Assistant. Model thinking mode is the leading suspect and has not been ruled out. The checks to run are on the [Performance tuning](docs/home-assistant/Performance-Tuning.md) page. Treat reply speed as unverified until you test it on your own hardware.
- **Voice-saving memory is untested across models.** Saving a memory depends on the model making a to-do list tool call. Some small models claim to have saved something without calling the tool. Check the list after your first test.
- **The memory sensor is YAML.** Home Assistant's UI template helpers cannot currently call the to-do list action, so this one sensor is defined in a YAML file.
