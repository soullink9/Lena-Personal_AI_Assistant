# Changelog

Lena's parts are versioned separately: the **app** has its own version (shown in its settings and on each release), and her **personality** (the Modelfile, the Home Assistant files and the Open WebUI files) has another. Newest first. Versions follow [Semantic Versioning](https://semver.org/).

- [App](#app)
- [Personality](#personality)

## App

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
