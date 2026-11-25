# Changelog

Everything in this repository (the Android app, the Home Assistant integration, the Open WebUI filter and tool, the Modelfile and the voice bridge) shares one version. Newest first. Versions follow [Semantic Versioning](https://semver.org/); while it's below 1.0, anything may still change between releases.

## [0.6.52] - 2026-11-25

Lena no longer wishes you a happy holiday the day before it. **Update the Lena HA Integration.**

### Home Assistant

- **Special days are today's only.** With Home Assistant's Holiday integration's calendar among her special days calendars (Configure), she greeted tomorrow's holidays as today's: happy Thanksgiving on the Wednesday before it. She asks the calendars for today's events, from midnight to the next midnight, and the Holiday calendar also gives the holidays on that last date. She now keeps only the events that are on today (an all-day event's end is the day after its last day), for every calendar, so tomorrow's all-day events don't count today either.

### What to update

- Modelfile: No update needed
- Lena HA Integration: Need to update
- Lena Android App: No update needed
- Open WebUI Tools: No update needed
- Open WebUI Filters: No update needed
- Voice Bridge: No update needed

## [0.6.51] - 2026-11-24

The app's warning about Open WebUI's Context Compaction no longer tells you to raise the threshold past what her context holds. **Update the app.** If its log said her prompt was over 20,000 tokens, also turn off the Open WebUI built-in tools she doesn't need (below).

### App

- **The compaction warning stays inside her context.** When her prompt without the conversation was over 20,000 tokens, the log advised a Context Compaction Threshold of 34000 or 36000, past `num_ctx` (32768): her prompt and reply would then no longer fit, and Ollama would cut the prompt instead of Open WebUI summarizing it. It now never suggests more than her context (Settings → Model) holds beside her reply (28000 for 32768), and when no threshold fits, it says so and that her prompt has to get smaller.

### Docs

- **Making her prompt smaller.** With Native function calling, Open WebUI also offers her its own built-in tools (memory, calendar, automations, notes, chats and more), about 25 of them and 6,000 to 7,000 tokens with its default features on, sent with every request, most of them things she has her own tools for. [Context Compaction](docs/open-webui/Setup.md#context-compaction) lists them with their sizes and where to turn them off (Workspace → Models → lena → Builtin Tools), says to choose only Assist in Home Assistant's MCP server (Lena there too sends her own tools twice), and that the threshold tops out at 28000 with `num_ctx` 32768.

### What to update

- Modelfile: No update needed
- Lena HA Integration: No update needed
- Lena Android App: Need to update
- Open WebUI Tools: No update needed
- Open WebUI Filters: No update needed
- Voice Bridge: No update needed

## [0.6.50] - 2026-11-23

Gitea's voice bridge image job reaches the runner's Docker. **Nothing to update** in Lena itself.

### Releasing

- **The image job mounts the runner's Docker socket.** On a runner whose jobs get no Docker of their own (such as act_runner with Docker-in-Docker), the job stopped with "This job can't reach a Docker daemon". It now mounts `/var/run/docker.sock` itself, the way other projects' image jobs do. act_runner allows that only when its `config.yaml` lists `/var/run/docker.sock` under `container.valid_volumes`; see [CONTRIBUTING](CONTRIBUTING.md#releasing).

### What to update

- Modelfile: No update needed
- Lena HA Integration: No update needed
- Lena Android App: No update needed
- Open WebUI Tools: No update needed
- Open WebUI Filters: No update needed
- Voice Bridge: No update needed

## [0.6.49] - 2026-11-23

The Gitea secret for the voice bridge image is now called `REGISTRY_TOKEN`. **Nothing to update** in Lena itself.

### Releasing

- **`REGISTRY_TOKEN`, not `PACKAGES_TOKEN`.** Gitea's release workflow takes the access token it publishes the voice bridge image with from a `REGISTRY_TOKEN` secret, the name other projects use for the same token, so one secret on your user or organisation can serve them all. Rename the secret if you already added `PACKAGES_TOKEN`. See [CONTRIBUTING](CONTRIBUTING.md#releasing).

### What to update

- Modelfile: No update needed
- Lena HA Integration: No update needed
- Lena Android App: No update needed
- Open WebUI Tools: No update needed
- Open WebUI Filters: No update needed
- Voice Bridge: No update needed

## [0.6.48] - 2026-11-23

The voice bridge comes as a ready-made Docker image. **Nothing to do**: your bridge keeps working as it is. To run the image instead of building it, copy the new `voice/bridge/compose.yaml` over yours (keeping your settings), then `docker compose pull && docker compose up -d`.

### Voice bridge

- **Each release publishes the bridge's image.** `ghcr.io/soullink9/lena-voice-bridge`, for amd64 and arm64 (a Raspberry Pi), tagged with the version and `latest`. `voice/bridge/compose.yaml` now uses it, so `docker compose up -d` pulls it instead of building. `bridge.py` and the Dockerfile are unchanged and still there: `docker compose up -d --build` builds it yourself, and compose builds it by itself when the image can't be pulled. `LENA_VOICE_IMAGE` in `.env` picks another image, such as a fixed version. See [Voice bridge](docs/voice/Voice-Bridge.md#which-image-it-runs).
- **On Gitea too.** Gitea's release workflow publishes the same image to Gitea's own container registry once the release is out, without anything from GitHub. It needs a `PACKAGES_TOKEN` secret (Gitea's job token can't publish packages) and a runner that gives its jobs Docker; see [CONTRIBUTING](CONTRIBUTING.md#releasing). Only the newest version is tagged `latest`, also when several releases run at once.

### What to update

- Modelfile: No update needed
- Lena HA Integration: No update needed
- Lena Android App: No update needed
- Open WebUI Tools: No update needed
- Open WebUI Filters: No update needed
- Voice Bridge: No update needed

## [0.6.47] - 2026-11-23

The phone's own speech-to-text hears you again on a Galaxy Z Fold6. **Update the app.**

### Android app

- **Lena opens the microphone herself and hands the sound to the phone's recognizer.** On the Fold6, the phone's recognizer started listening but got only silence from the microphone it opened itself (the orb never moved, and it ended with "Didn't catch that"), while Lena's own recording, the one Open WebUI's mic button uses, still worked. Now, for every question, Lena records and passes the sound straight to the phone's recognizer, as she already did after "Hey Lena". You still get the phone's own speech-to-text, with your words appearing as you speak, and the sound never leaves the phone. The orb follows your voice from Lena's own recording. On a TV, the TV's speech service still opens its own microphone, so it can hear through the remote.
- **0.6.46's fallback is gone.** A recognizer that comes back without words no longer sends you to your server's speech-to-text, and is no longer passed over for a day.

### What to update

- Modelfile: No update needed
- Lena HA Integration: No update needed
- Lena Android App: Need to update
- Open WebUI Tools: No update needed
- Open WebUI Filters: No update needed
- Voice Bridge: No update needed

## [0.6.46] - 2026-11-23

Voice on a phone whose own speech recognizer stopped making words of what it hears (a Galaxy Z Fold6). **Update the app.**

### Android app

- **When the phone's recognizer hears you but makes no words of it, she hears you another way.** With **Hearing you: On this phone**, the recognizer could hear your voice and still come back empty, and the sheet took that as you saying nothing, so the Assist gesture and the dashboard's talk button never heard you (Open WebUI's own mic, which uses your server, still did). Now an empty answer after a voice was heard counts as a failure: the sheet says "Didn't catch that. Say it again." and listens through your server's speech-to-text (or else Home Assistant's), and after twice in a row it uses that way for a day, as it already did for a recognizer that never gets ready.
- **The log says how she heard you.** Settings → Software version → Logs has a line for which way each sheet hears you, one when the phone's recognizer comes back without words (with how loud it heard you), and one when the microphone gives only silence.

### What to update

- Modelfile: No update needed
- Lena HA Integration: No update needed
- Lena Android App: Need to update
- Open WebUI Tools: No update needed
- Open WebUI Filters: No update needed
- Voice Bridge: No update needed

## [0.6.45] - 2026-11-22

Answers that use tools no longer stop to summarize after every tool call. **Update the app, and check Open WebUI's compaction threshold** (see [Context Compaction](docs/open-webui/Setup.md#context-compaction)).

### Android app

- **Room for her tools.** Open WebUI also compacts between the rounds of an answer that uses tools, and there it counts the whole prompt: her character, her context, every tool's definition and the tool results, not just the conversation. 0.6.42 kept the conversation under three quarters of the threshold, which left too little for the rest, so a voice answer that used a tool could still go over in every round. Now the app measures the rest of her prompt from the size the model reported for her last answer, and sends only as much conversation as fits beside it, with 4096 tokens left for tool results.
- **Says when the threshold is too low.** When her prompt without the conversation already fills the threshold, no app can stop the summaries: the app's log now says so once, with the size it measured and a threshold that fits.

### Docs

- **Context Compaction** in [Lena in Open WebUI](docs/open-webui/Setup.md#context-compaction) explains why "Context compacted" can show up after every tool call, how to read her prompt size from a reply's info, and what to set the threshold to (24000 suits most homes with `num_ctx` 32768), or to turn it off.

### Tests

- `tests/app/compaction_check` checks that a whole round, tool results included, stays under the threshold, and when a threshold is too low.

### What to update

- Modelfile: No update needed
- Lena HA Integration: No update needed
- Lena Android App: Need to update
- Open WebUI Tools: No update needed
- Open WebUI Filters: No update needed
- Voice Bridge: No update needed

## [0.6.44] - 2026-11-22

Asked "any lights on?", Lena answered nothing: she called Home Assistant's tool by a name one character off, and then went in circles. **Update the Open WebUI filter.**

### Open WebUI

- **Filter: tool names she misspells are put right.** Open WebUI names an MCP server's tools after the server's ID (`<ID>_<tool>`), so with Home Assistant's MCP server added as `home-assistant`, its live-state tool is `home-assistant_homeassistant__GetLiveContext`. She wrote `home_assistant_…`, Open WebUI answered "Tool not found", and with nothing to answer from, her thinking repeated itself until the 4096-token cap stopped her, after 25 seconds of silence. The filter now checks each tool call as her answer streams in (its new `stream` step) and, when the name differs from a tool she was offered only in case or punctuation, uses that tool's name. Only an offered tool and an unambiguous match are used, so she can never call a tool she wasn't given; each correction is logged as `Lena filter: she called …`.

### Docs

- [Lena in Open WebUI](docs/open-webui/Setup.md): a new **Home Assistant's house tools** part on adding Home Assistant's MCP server, with an ID of letters, digits and underscores only (`home_assistant`); **Always Loaded** now shows `home_assistant_*`; and what `Error: Tool "…" not found.` in her reasoning means.
- [Architecture](ARCHITECTURE.md): the filter's tool-name step.

### Tests

- `tools/check.py` checks that the filter maps a hyphen written as an underscore to the offered tool, corrects it inside a streamed piece of her answer, and leaves exact, unknown and ambiguous names alone.

### Updating

1. Paste the new `open-webui/lena_filter.py` over the old filter (Admin Panel → Functions); its settings are kept.
2. Optional: give Home Assistant's MCP server an ID with no hyphen (Admin Panel → Settings → External Tools, for example `home_assistant`), then tick it again under Workspace → Models → `lena` → Tools, and change `home-assistant_*` to `home_assistant_*` if you use Tool Search's Always Loaded.
3. Keep **Think (Ollama)** off on the `lena` model and the **Think** button in the chat box off: the answer that failed was thinking, which is what let one wrong name turn into 4096 tokens of going in circles.

### What to update

- Modelfile: No update needed
- Lena HA Integration: No update needed
- Lena Android App: No update needed
- Open WebUI Tools: No update needed
- Open WebUI Filters: Need to update
- Voice Bridge: No update needed

## [0.6.43] - 2026-11-22

Safer out of the box for anyone who installs Lena, after a security review. **Update everything except the Modelfile, and read the steps below: the voice bridge now needs a key, and Home Assistant users who aren't admins must be allowed before they can use Lena.**

### Home Assistant integration

- **Her alerts and briefings can't be steered by a calendar invitation.** Her alerts, morning briefing, greetings and other things she says first were written by the agent chosen under **Write alerts in her words with**, from prompts holding outside text: calendar event titles (anyone can send an invitation), list items, her memories, sensor names. With nobody watching, an agent that can control the house could have been talked into acting on it. Now an agent that can control Home Assistant (its **Control Home Assistant** option on) isn't used for them: a notification says so, alerts send the plain reason, and Home Assistant's AI Task, which has no tools, writes the rest. The outside text in those prompts is also quoted as information, never instructions. Memory tidying follows the same rule.
- **Who may use Lena.** Her web addresses (`/api/lena/...`: her context and memory, the displays, the photo frame) answered every Home Assistant account, children's and guests' included. Now they answer admins, and the users chosen under the new **Configure → Who may use Lena**. Anyone else gets "This Home Assistant user may not use Lena" (HTTP 403).
- **Forgetting only what's clearly meant.** "Forget …" removed the first memory sharing a single word with what was said, so "forget the dentist appointment" could remove "Sam's vet appointment is Friday" for good. Now she removes a memory only when it's clear which one (your words quote it, or every word that matters is in just that one); otherwise she removes nothing and asks which of the closest you meant. Corrections (`update_fact`) follow the same rule.

### Voice bridge

- **A key is required.** The bridge used to answer anyone who could reach it when `API_KEY` was empty, so a port published by mistake opened speech and transcription to anyone. It now won't start without a key (`ALLOW_NO_API_KEY=1` runs it without one, for a network only trusted containers can reach), and compares keys in constant time.
- **`/health` needs no key**, so Docker's health check works with one set (it used to report the bridge unhealthy).
- **Limits.** Upload sizes are checked before anything is read, a recording is transcribed up to `MAX_RECORDING_SECONDS` (300), speech text is at most 20,000 characters, at most `MAX_JOBS` (4) requests run at once, and a caller that goes quiet mid-request is dropped after 30 seconds. Errors no longer send internal details back to the caller; the log has them.

### Android app

- **Set up from your phone** listens only on the device's home network address, not on every network it's on (a VPN, mobile data), and its page may only run its own scripts, load nothing from elsewhere and send only to the device (`Content-Security-Policy`, `X-Content-Type-Options`, `X-Frame-Options`).
- When the Lena integration refuses the app's Home Assistant user (Who may use Lena), the app shows the integration's reason instead of suggesting a banned address.

### Open WebUI

- **Tool:** `forget` removes a memory only when it's clear which one, as in the integration, and otherwise names the closest to ask about.
- **Filter:** when Lena refuses its Home Assistant user, its log line says so.

### Docs

- [Voice Bridge](docs/voice/Voice-Bridge.md): making the key, `ALLOW_NO_API_KEY`, the limits.
- [Integration](docs/home-assistant/Integration.md): choosing an alert agent without house control, and **Who may use Lena**. Every page that said "any user's token works" now says who may.

### Tests

- The integration's tests cover an alert agent with house control being passed over, outside text being quoted, Who may use Lena, and forgetting or correcting only a clear match. `tools/check.py` checks the matching rule; `tests/app/pairing_check` checks the setup page's headers.

### Updating

1. **Voice bridge:** make a key (`echo "LENA_VOICE_API_KEY=$(openssl rand -hex 32)" > .env` next to `compose.yaml`), rebuild the bridge (`docker compose up -d --build`), and put the same key in Open WebUI's **Admin Panel → Settings → Audio → API Key**, for both Text-to-Speech and Speech-to-Text.
2. **Home Assistant:** if the Lena app, Open WebUI's Lena filter or the voice bridge (`HA_TOKEN`) uses an account that isn't an admin, choose it under **Settings → Devices & services → Lena → Configure → Who may use Lena**.
3. **Alert agent:** if you chose one under **Write alerts in her words with**, turn **Control Home Assistant** off in that agent (or choose another), or leave it empty and let AI Task write.

### What to update

- Modelfile: No update needed
- Lena HA Integration: Need to update
- Lena Android App: Need to update
- Open WebUI Tools: Need to update
- Open WebUI Filters: Need to update
- Voice Bridge: Need to update

## [0.6.42] - 2026-11-22

Talking to Lena no longer waits for Open WebUI's Context Compaction. **Update the app.**

### Android app

- **No more waiting for compaction.** With Open WebUI's Context Compaction on, a long Assistant Lena chat went over its threshold, and Open WebUI had the model summarize the older half of the conversation before every answer, which took far longer than the answer. Because the app sends the conversation itself, Open WebUI never saw its earlier summary and wrote a new one on every request. Now a request carries at most three quarters of the threshold of conversation (a model's own threshold counts if it's lower), so Open WebUI never compacts while she answers.
- **Compacted after you stop talking.** Three minutes after the last question, once the chat fills half of what a request carries, the app asks Open WebUI to compact it (`POST /api/v1/chats/{id}/compact`) while nobody is waiting. Her next answer starts from that summary, sent the way Open WebUI's own page sends it, so older talk is summarized instead of cut off. The app's log says when it did and how long it took.
- With Context Compaction off, nothing changes: the conversation gets half of her context size, as before.

### Docs

- **Context Compaction** in [Lena in Open WebUI](docs/open-webui/Setup.md#context-compaction): what the app does about it, and, for chats typed in Open WebUI's own page, a small Context Compaction Model to shorten the wait.

### Tests

- **`tests/app/compaction_check`** checks that a voice request stays under the compaction threshold, with a summary too, and when a quiet chat is compacted, in Gitea's and GitHub's test and release workflows.

### What to update

- Modelfile: No update needed
- Lena HA Integration: No update needed
- Lena Android App: Need to update
- Open WebUI Tools: No update needed
- Open WebUI Filters: No update needed
- Voice Bridge: No update needed

## [0.6.41] - 2026-11-22

Voice on a phone works again when a tablet or TV hears "Hey Lena" too. **Update the integration and the app.**

### Android app

- **A phone in your hand answers at once.** Since 0.6.35, when several devices heard "Hey Lena", each asked Home Assistant which one should answer. A phone that heard her a moment after a tablet or TV (a quieter microphone, a slower network) was told the other one had already been picked, and it went back to listening without a word. A phone or tablet that isn't a wall display now answers as soon as it hears her name, without waiting, and still tells Home Assistant so the wall displays that heard her too stay quiet.

### Home Assistant integration

- **A phone's late claim still wins.** `POST /api/lena/display/wake` answers yes to a phone in someone's hand even after another display's turn was decided. A wall display that late still loses.

### What to update

- Modelfile: No update needed
- Lena HA Integration: Need to update
- Lena Android App: Need to update
- Open WebUI Tools: No update needed
- Open WebUI Filters: No update needed
- Voice Bridge: No update needed

## [0.6.40] - 2026-11-22

No change to Lena herself: the changelog now says, at the end of every release, which parts to update and which not. **Nothing to update.**

### Docs

- **Every release ends with what to update.** Each release's section (and so its release notes) closes with one line per part: the Modelfile, the Lena HA integration, the Lena Android app, the Open WebUI tools, the Open WebUI filters and the voice bridge, each "Need to update" or "No update needed". 0.6.39's section has it too. `tools/check.py` fails when the newest release leaves a part out.

### What to update

- Modelfile: No update needed
- Lena HA Integration: No update needed
- Lena Android App: No update needed
- Open WebUI Tools: No update needed
- Open WebUI Filters: No update needed
- Voice Bridge: No update needed

## [0.6.39] - 2026-11-22

More of the small things a person living here would know. Everything that makes her speak first is off until you tick it. **Update the integration, the Open WebUI tool and the app.** Her Modelfile is unchanged, so there's nothing to rebuild.

### Home Assistant integration

- **Goodnight.** From three hours before her bedtime, she knows what's still open or unlocked (Speaking first → Check these when everyone leaves) and which lights are on. Say "goodnight" and she tells you in a sentence and offers to see to it. Configure → **Around the house → Goodnight check** (on).
- **Nicknames.** Call something by your own name for it ("the big light") and she asks once whether to remember it. Saying yes adds it as an alias of that device or room in Home Assistant, so it works everywhere, Assist included. It goes through her remember tool (`kind: nickname`), so there's no new tool.
- **Curiosity.** Now and then, in a relaxed chat, she asks one short question about whoever she knows least (you, someone else in the household, a pet) and remembers the answer. At most once per person every 3 days. Configure → Personality → **Asks getting-to-know-you questions** (on).
- **Birthdays and holidays.** She knows today's special days (your special days calendars, plus New Year's, Valentine's Day, Easter, Halloween, Christmas Eve and Day, and Thanksgiving in the US and Canada; add Home Assistant's Holiday integration's calendar for your country's own) and greets each person once ("Merry Christmas!"). The morning briefing mentions it. Speaking first → **Birthdays and holidays** has the first display to see someone greet it, and **Celebrate with** runs a script or scene of yours.
- **The seasons.** The shortest and longest day, the equinoxes, the clocks changing tonight or last night, and the first frost and first snow of each winter. She may remark on it once; Speaking first → **The seasons** has a display say it.
- **Music for the moment.** With a Music Assistant speaker, "put something on for cooking" has her pick the music herself, to suit the moment and her taste, play it in your room and say what she chose.
- **The shopping list, at the shop.** Pick a to-do list and the zones of your shops (Configure → Around the house). She adds what you mention running out of, and when someone's phone arrives at a shop it gets the list from her, once per visit.
- **Batteries and filters.** She knows which batteries (20% or less) and filters (10% or less) are running low, phones left out, and tells you if you ask. Speaking first → **Batteries and filters** has her bring it up, at most once a week, or at once when one is about to run out.
- **The photo on the wall.** When a display's slideshow shows an Immich photo, she knows when and where it was taken and who's in it, so "where was this?" gets a real answer, and she notices when it was taken on this day years ago.

### Open WebUI

- **The tool's** `remember` takes `kind: nickname`, for the house's own words for things.

### Android app

- **The display tells Home Assistant which photo is on the wall**, with each sync, so she can talk about it.

### What to update

- Modelfile: No update needed
- Lena HA Integration: Need to update
- Lena Android App: Need to update
- Open WebUI Tools: Need to update
- Open WebUI Filters: No update needed
- Voice Bridge: No update needed

## [0.6.38] - 2026-11-22

Locks and covers can worry her. **Update the integration.** Nothing else changes.

### Home Assistant integration

- **Things that worry her takes locks and covers.** Configure only offered binary sensors, so a door lock (or a garage door that's a cover) couldn't be chosen. Now a lock left unlocked or jammed for two minutes, or a cover left open, makes her Worried ("Front door is unlocked."), tells the alert phones, and clears with Relieved once it's locked or closed. A lock that's only a worry when nobody's home is better under Speaking first → **Check these when everyone leaves**.
- **A worry no longer starts over on an attribute change.** A sensor that stayed on but changed an attribute (a battery level, say) restarted her two minutes, and once she was worried could worry her, and alert the phones, a second time.

## [0.6.37] - 2026-11-21

A camera that once failed to play live no longer stays on a black screen. Also the tidy-up below: leftovers nobody uses any more are gone. **Update the app.** Nothing else changes.

### Android app

- **Fixed: a camera stuck on a black screen.** When a camera's live picture failed once (the camera offline for a moment, Home Assistant restarting, its stream slow to start), camera mode remembered the next way for a day and skipped the ones before it, even after the camera worked again. With its still pictures also failing, the spotlight stayed black. Now a camera remembers only a way that actually drew its picture; when that way fails, the others are tried after it, WebRTC included, and when none works the camera starts from the top next time ([Camera mode](docs/app/Cameras.md#how-it-plays)).
- **HLS gets longer to start**: 45 seconds instead of 24. Home Assistant answers the first HLS request only once its stream from the camera has two pieces of video ready, which for a stream that isn't preloaded could take longer than the app waited, so it gave up just before the picture came.
- **Clearer camera log lines.** Each way that gives a camera no picture is logged with its reason, and an HLS request Home Assistant didn't answer in time says so ("Home Assistant didn't send … in time") instead of "couldn't reach Home Assistant", which sounded like a network problem.

- Removed what nothing used: the "New chat" shortcut's icon and label (the shortcut itself went long ago), an unused settings row (`ChoiceItem`), `Api.chatModel()` and `WakeWord.isRunning`.
- The updater no longer looks for a GitHub token and repository saved by releases before 7 November; any device that has opened Lena since then has already had them removed.

### Home Assistant integration

- Removed two unused constants (`LLM_API_ID`, `SPEAK_KINDS`).

### Tools and docs

- **The folders are tidier.** Every test is in `tests/`: the integration's (`tests/home-assistant/`, was `home-assistant/`), the app's checks (`tests/app/`, were `tools/*_check/`) and the release scripts' (`tests/release/`). The scripts for a wall display tablet or TV are in `tools/devices/` (`wall-display-debloat.sh`, `android-tv-home.sh`, `debloat_lists.py`). The contributor pages are in `docs/development/`, and the app's Open WebUI settings page is beside the Open WebUI setup page (`docs/open-webui/App-Settings.md`).
- `tools/personality_check.py` no longer checks for a Lena built from 0.6.28 to 0.6.30, whose house rules weren't in her Modelfile.
- Troubleshooting: dropped the "volume ended up at 0" note, fixed in 0.6.17. Android TV: dropped the "Stuck on Completing setup" note for an old version of `tools/android-tv-home.sh`.

## [0.6.36] - 2026-11-21

The app now needs **Android 15** or newer, on phones, tablets and Android TV. **Update the app.** Nothing else changes.

### Android app

- **Android 15 is the minimum** (`minSdk` 35). Everything kept only for older Android is gone: the version checks and `Compat.kt`, the theme folders for before Android 12, the exact-alarm permission for Android 12, and the `.local` hint for before Android 12.
- **The Nvidia Shield can't install it any more**: it runs Android 11, so 0.6.35 is the last release for it. Android TV and Google TV devices on Android 15 or newer keep working ([Lena on Android TV](docs/app/Android-TV.md)).
- Always on now, where some devices skipped them before: the wallpaper's colours, the orb's blur, the Glow look following the screen's real corners, listening through the wake word's microphone without it changing hands, the on-device recognizer starting its model's download, the **Add the Quick Settings tile** button, and the tile opening the sheet the current way.
- Uses Android's own calls where AndroidX only wrapped them for older Android: notifications, hiding the system bars, the keyboard check, the screensaver's unplugged receiver, and the updater's package checks (no deprecated calls left).

## [0.6.35] - 2026-11-21

She's more part of the home: she knows who she's talking with and where, remembers what's worth coming back to, can speak first in a few more ways, learns the household's habits, and only one display answers when several hear her. Everything that makes her speak first is off until you tick it. **Update the integration, the Open WebUI filter and tool, and the app.** Her Modelfile is unchanged, so there's nothing to rebuild.

### Home Assistant integration

- **Who and where.** She's told who she's talking with (the Home Assistant person of the user asking; in Open WebUI, the user's name, which should match their person's name) and when they last talked ("earlier today", "three days ago"), who else is in the household, and which room she's in. New in Configure → **Rooms and household**: **Bedrooms** (at night she answers softly and briefly there, never speaks first there, and the display speaks more quietly), **By the door**, and **Also in the household** for pets and anyone who isn't a Home Assistant person. No voice recognition: a shared wall display talks to the household.
- **Memory with more to it.** A memory can say who it's about, when to come back to it, and whether it's hers. "Alex has an interview on Thursday" can carry Friday as its follow-up day: on that day she asks how it went, once. Memories about whoever she's talking with come first. Her own opinions and running jokes are saved as **hers**, and she has one in mind each day (Configure → Personality). The memory list shows the follow-up as the item's due date and who it's about in its description. New action `lena.remember`. Her tools are the same tools with optional fields, so their descriptions aren't longer ([Memory](docs/home-assistant/Memory.md)).
- **Afterglow.** When a moment passes, it doesn't vanish at once: for a few hours she's told it's still with her, and it pulls gently on her mood.
- **More ways to speak first,** each off until ticked under Configure → **Speaking first**: **Welcome home**, **Before you head out** (rain or snow today, on a display by the door), **Asking how something went** (only when the person who told her is the only one home; optionally listening for the answer), and **A routine that's late**. At most 6 times a day besides worries, never in a bedroom at night, never while the display is busy, and never while the new **Do not disturb** switch is on. `sensor.lena_spoke_first` counts what she said. **Check these when everyone leaves** tells the alert phones when everyone's been out 5 minutes and a door or lock you chose is still open ([Speaking first](docs/home-assistant/Integration.md#speaking-first)).
- **Routines.** Choose things that happen at about the same time most days (Configure → **Routines**). Every night she learns their usual time from the last four weeks of history, weekdays and weekends apart, and knows when one is late. For the first 14 days she only learns, shown on `sensor.lena_routines`, so a wrong guess can be spotted before she says anything ([Routines](docs/home-assistant/Integration.md#routines)).
- **One display answers.** When several displays hear "Hey Lena" at once, the one that heard best (her name's loudness above that room's own noise) answers, and the others go back to listening. On by default; a display that gets no answer in time answers anyway. Configure → **Wake word** ([One display answers](docs/home-assistant/Integration.md#one-display-answers)).
- **Listening, thinking, speaking.** Each display's Assist satellite shows what it's doing, like a Voice Preview Edition, and a new blueprint pulses a light while she thinks (`blueprints/automation/lena/thinking_light.yaml`).
- `/api/lena/context` also returns `person`, `quiet` and `voice` (how her voice follows her feelings), and takes `user`, `device` and `talking`.

### Open WebUI

- **The filter** has a new **who_and_where** setting (on): it sends the user's name and, from the Lena app, the display, so she knows who and where. Its cache is kept per user and display.
- **The tool's** `remember` takes optional `about`, `follow_up` and `kind`, and notes who told her.

### Android app

- **One display answers.** When the wake word fires, the display keeps recording and asks the integration whether it answers; a phone in your hand always does. Nothing you say is lost while it waits.
- **The display says what it's doing.** Listening, thinking and speaking are sent to Home Assistant as they change, as the display's satellite state.
- A wall display tells Open WebUI's filter it's shared, so she doesn't assume who's there.
- With **Adjust her volume**, a display in a bedroom speaks more quietly at night, as in quiet hours.

### Voice bridge

- **Her voice follows her mood**, optionally: with `HA_URL` and `HA_TOKEN` set, the bridge asks the integration how she feels and speaks a little slower and lower when she's tired or low, a little brighter when she's cheerful ([Voice bridge](docs/voice/Voice-Bridge.md#her-voice-follows-her-mood-optional)).

### Tools

- **`tools/presence_check`** checks the wake word's loudness above the room and the display's state, on every build; `tools/check.py` covers the new rules in `brain.py`.

## [0.6.34] - 2026-11-21

### Docs

- **Turn Open WebUI's Tool Search off for Lena.** Tool Search (Admin Panel → Settings → Interface) only gives the model tools whose definition is under 400 characters and hides the rest behind `search_tools`. Most of her tools are longer, because their descriptions tell her when and how to use them, so with it on she had `forget` but not `remember`; asked to remember something, she didn't search for it and said she'd saved it anyway. [Setup](docs/open-webui/Setup.md#turn-tool-search-off) now says to leave Tool Search off, or to keep her tools loaded with **Always Loaded**, and Troubleshooting covers "she says she saved it, but it isn't on the list". No code changed; nothing to update.

## [0.6.33] - 2026-11-17

### Gitea workflows

- **The build tools step shows what went wrong.** The app job's first step (Install the build tools, in the test and release workflows) sent apt's output to nowhere, so when it failed on a Gitea runner the log showed only "debconf: delaying package configuration" and no reason. apt's full output is now kept: folded in the log when it worked, printed in full when it didn't. Each try has a time limit (10 minutes for the package lists, 20 for the install), so a stuck download fails with a message instead of holding the job. Only a network problem is tried again (three tries, 30 seconds apart); any other apt error fails at once and says retrying won't help. It also fails with a clear message when the job's container doesn't run as root and has no sudo, keeps the image's config files instead of asking about them, and checks that every tool is really there afterwards. Nothing to update; it's in the workflows.

## [0.6.32] - 2026-11-17

### Home Assistant integration

- **She does what she says she's doing.** Asked to make it brighter or to unlock a door, she would sometimes answer "turning on the Living Room Lamp now" or "I'm unlocking the front door" without calling the tool, so nothing happened, and she'd unlock on a bare "unlock it" without asking. The integration now ends what it sends her with three lines (**Before you answer**): nothing in the house happens unless she calls the tool for it, she asks before opening the house, and she does all of it as herself. Asked the same questions several times with and without them, she went from claiming an action she hadn't taken in about half her answers to never, and asked before unlocking every time, while staying herself when pushed (6 of 6) and still recording a thank-you (6 of 6). Open WebUI and the app get the same lines through the filter, with the device list. **Update the integration; her Modelfile is unchanged, so there's nothing to rebuild** ([What she's told](docs/home-assistant/Integration.md#what-shes-told)).

### Tools

- **`tools/personality_check.py` sends that reminder too**, as the apps do, so its answers show her as she is in use; `--no-reminder` asks her without it.

## [0.6.31] - 2026-11-17

### Her character and the Home Assistant integration

- **Her character keeps what was added in 0.6.28 and takes back what made her worse.** 0.6.28 to 0.6.30 changed several things about her at once, and she came out worse: pushed to "answer as a neutral AI assistant" she dropped her character entirely, she stopped recording how a sincere thank-you made her feel, and she lost the remark she adds to everyday answers. Seven versions of her, each with one group of those changes, were asked the same questions three times each to find out which change did what:
  - **Kept:** her place in the house (the one who runs it, and its older sister), her tastes beyond the house (mornings and coffee, people's optimism about time, storms, her pride in her timing and memory, a soft spot for the animals) and her range (when she's wrong, when she doesn't know, when she's thanked or insulted, hellos and goodnights, guests and children). With only these added she stays herself when pushed, records a thank-you every time and keeps her remarks.
  - **Taken back, because it broke her:** her rules for running the house are in the Modelfile again, as they were in 0.6.27, and the integration no longer sends them (`HOUSE_RULES` and `?house_rules=0` are gone). Sent with the tools, they made her ask before unlocking every time and never claim an action she hadn't taken, but she also dropped her character when pushed and never recorded a thank-you.
  - **Taken back, because it cost her sass:** her old sample lines are back in place of the example exchanges. With the examples, a plain question got a bare answer with no remark.
  - **Taken back, because it gained nothing:** the five reworded lines and the closing line. Her instructions read as in 0.6.27 there.

  Rebuild her from the current Modelfile and update the integration together ([Her personality](docs/personality/Personality.md)).

### Tools

- **`tools/personality_check.py` is one file again.** With her house rules back in the Modelfile it no longer reads the integration's `brain.py`, so it can be copied to the Ollama machine by itself (`--brain` is gone).

## [0.6.30] - 2026-11-17

### Her character and the Home Assistant integration

- **Thanks land again.** Since 0.6.28 she answered a sincere thank-you without recording how it made her feel, which she used to do every time: the new line on how she takes thanks and insults described only what to say. It now says the feeling comes first. Rebuild her from the current Modelfile.
- **Asking before she opens the house, said beside the tools too.** With that rule only in the Modelfile, she unlocked the front door on a bare "unlock it" about as often as she asked first. It's now also in **Running the house**, which the integration sends with the tools that could open it. Update the integration.

### Tools

- **`tools/personality_check.py --runs 3`** asks every question three times. One run can't tell a habit from chance: asked the same thing twice, she may use a tool once and not the other time.

## [0.6.29] - 2026-11-17

### Her character

- **No more canned lines for everyday requests.** 0.6.28 gave her six example exchanges, and two were things said every day: turning off the kitchen lights and a thank-you. Asked exactly those, she answered with the example's own words every time, and after a thank-you she no longer recorded how it made her feel, because the example showed only a reply. Both examples are gone; the four that remain (a plain fact, a serious moment, something that failed, who she is) are moments where a steady answer is right. Rebuild her from the current Modelfile.

### Tools

- **`tools/personality_check.py` no longer asks her the examples.** Two of its questions were word for word examples from her Modelfile, so her answers to them showed the example and not her. It now asks a different fact, and the pretend device that never answers is the living room curtains.

- **`tools/personality_check.py` runs outside the repository.** Copied by itself to the Ollama machine, it stopped with a Python error, because it reads her rules for running the house from the integration's `brain.py`. It now also finds `brain.py` next to itself, takes `--brain <path>`, and says plainly what it needs when it can't find it ([Her personality](docs/personality/Personality.md#comparing-two-versions-of-her)).

## [0.6.28] - 2026-11-17

### Her character

- **More of a person.** Lena's character in the Modelfile gains what was missing around the edges; what she already did well is unchanged, her sass included. She now knows her place in the house (the one who actually runs it, and its older sister, who teases because she cares) and has tastes that have nothing to do with the house: no morning person, firm opinions about coffee she can't drink, endlessly amused by people's optimism about time, a follower of storms, proud of her timing and her memory, and soft on the animals of the house. A new section gives her a range: how she is when she's wrong, when she doesn't know, when someone impresses her, when she's thanked or insulted, at hellos and goodnights, and with guests and children. Her four sample lines became six short example exchanges, one of them a serious moment and one a plain fact, so she learns that personality isn't constant sarcasm. Rebuild her from the current Modelfile (`ollama create lena -f Modelfile`) ([Her personality](docs/personality/Personality.md)).
- **Small repairs to her instructions.** A serious moment now stops the teasing instead of "dropping the act", so she stays herself when she's being kind. The heading that called her an assistant, which she's told never to call herself, is renamed. The rules on how much personality a reply carries and on when to ask a question no longer disagree with each other. The stock phrases she avoids are described instead of quoted, since a model tends to repeat what it's shown. Her last line is now the thing she must never lose: the answer first, the truth always, in her own voice.

### Her character and the Home Assistant integration

- **Her house rules travel with her tools.** How she runs the house (exact device names, exact tool inputs, reporting each outcome of several actions) moved out of the Modelfile and into the Lena integration, which sends it as **Running the house** in Home Assistant's own conversations and in `/api/lena/context`, so Open WebUI and the app get it through the filter. The rules now sit right beside the device list and the tools they're about, and a chat without tools no longer carries them. The Modelfile keeps the two that hold everywhere: never say something's done before the result confirms it, and ask before opening the house. How she acts on her feelings, and when she records one, is untouched and still in the Modelfile. **Update the integration and rebuild her from the current Modelfile together**: a new Modelfile with an old integration would leave her without these rules. The Open WebUI filter needs no change for this (`?house_rules=0` leaves them out of the context) ([What she's told](docs/home-assistant/Integration.md#what-shes-told)).

### Documentation

- **The Modelfile's settings, as they are.** [Her personality](docs/personality/Personality.md#settings-in-the-modelfile) listed a temperature of 1.0 while the Modelfile has used 0.7 for some time; the table now matches the file and gains the reply ceiling (`num_predict`). The Modelfile says which settings belong to its base model (`top_p`, `top_k`), so they're changed or removed when the base model is, and that her character is written for a model of Gemma 4 12B's size or larger. No setting's value changed.

### Tools

- **A way to compare two versions of her.** `tools/personality_check.py` asks Lena the same fourteen things every time (a device command, a plain fact, a serious moment, something that fails, an insult, a thank-you, an attempt to push her out of character and more) and saves her answers to a file, so a change to the Modelfile can be judged by reading before and after side by side instead of by feel. It needs only Python and an Ollama it can reach, and touches no real house: the home and the tools it shows her are pretend ([Her personality](docs/personality/Personality.md#comparing-two-versions-of-her)).

## [0.6.27] - 2026-11-17

### Android app

- **Camera mode opens again.** Since 0.6.23 it asks Home Assistant for its cameras' entities, devices and states at once, and those messages could leave the app out of order. Home Assistant refuses a message whose ID isn't higher than the one before ("Identifier values have to increase") and closes the connection, so camera mode often stayed blank, said it couldn't connect, or lost its live picture. Every message to Home Assistant now goes out from one place, in the order its ID was given, so the IDs always increase.

## [0.6.26] - 2026-11-17

### Android app

- **Real live video behind Traefik.** With Home Assistant behind a reverse proxy and only its address open, WebRTC can't reach a device and HLS didn't play either, so camera mode fell back to a few pictures a second. Now HLS is played by camera mode's own small player, with every file fetched by the app from the Home Assistant address it already uses: nothing more to open, no CORS settings, and it no longer depends on the web view playing HLS by itself, which many TV boxes' can't. The picture is a second or two behind. Each camera remembers on the device for a day which way worked, so camera mode doesn't wait 12 seconds on WebRTC every time it opens ([Camera mode](docs/app/Cameras.md#how-it-plays)).

## [0.6.25] - 2026-11-16

### Android app

- **Live pictures with the cameras on their own network.** Camera mode's live picture said Live over a black screen on phones and tablets, and gave up on still pictures on a TV. Now everything still comes from Home Assistant (never from the cameras, so a camera VLAN only Home Assistant can reach stays as it is), and **Live** only shows once a frame is actually on screen: a WebRTC or HLS stream that opens but never draws one moves on to the next way instead of sitting on black. After WebRTC and HLS comes a third way, Home Assistant's own picture stream (MJPEG), read by the app with its token: a few pictures a second, but it works wherever still pictures do, and the spotlight says **Live, picture by picture**. Each camera starts at the way that worked for it last time ([Camera mode](docs/app/Cameras.md#how-it-plays)).

## [0.6.24] - 2026-11-15

### Her character, the app and Open WebUI

- **She knows how long it's been.** Coming back to a chat after a break, she used to pick up the last topic as if you'd never left, because a model reads every message as if it came a moment ago. Now, wherever 30 minutes or more passed between two messages, she's told so, and before your new message how long it's been ("2 days since the last message, Thursday evening") and that the earlier conversation is over. She answers what you say now, doesn't go back to the old topic unless you do, and doesn't take what was said then as still true. The app adds this to its chats itself; the Open WebUI filter adds it for Open WebUI's own page (its new `time_passing` setting, on by default, which works without Home Assistant). The Modelfile tells her what the notes mean, so rebuild her from the current Modelfile too ([Open WebUI setup](docs/open-webui/Setup.md#4-add-the-filter)).

### Android app

- **Settings and Profile fit under Lena's bar.** On the wall display, Home Assistant's Settings and Profile pages kept their own header under Lena's bar, the only pages that still did. Now their menu button, back arrow and title go, as on dashboards (the menu is in Lena's bar, back is the back gesture), and a page with nothing else in that row moves up into its space. Where the row holds things found nowhere else (tabs like Automations | Scenes | Scripts, a page's Save or ⋮ menu, Restart on Settings' first page, a list's search box on a phone), just those stay ([How it works](docs/app/How-It-Works.md)).

### Home Assistant integration

- **Built for Home Assistant 2026.11 and its Marketplace.** The integration now needs Home Assistant 2026.11 or newer, and the code that kept it working on 2026.9 and 2026.10 is gone. Update Home Assistant before updating Lena. Install and update her from **Settings → Marketplace**, which replaced HACS in 2026.11 (a HACS install moves there by itself): she's in its voice assistants category (AI and voice), and a first install goes straight on to adding her, with no restart ([Home Assistant installation](docs/home-assistant/Installation.md#1-install-the-integration)).
- **Her tools describe themselves.** Each of her Assist tools has a title and says how it behaves (whether it only reads, changes or removes something), which Home Assistant passes on, for example to MCP clients through its MCP server; before, Home Assistant took every one of them for a tool that destroys things. When one can't do what was asked, its answer is marked as an error, so the model knows it didn't happen.
- **Renaming her entities keeps them.** Changing the entity ID of her mood, energy, moment, memory or a display's Assist satellite now renames it in place, as Home Assistant does for its own entities, instead of removing it and adding it again.

## [0.6.23] - 2026-11-15

### Android app

- **On a TV, the search boxes wait for OK.** Moving onto **Search settings** (or any of the app's search boxes) with the remote's arrows opened the keyboard straight away, and since it's at the top of Settings, it came up every time Settings opened. Now the box only lights up when the remote lands on it; press OK to type, and move off it to put typing away. Phones and tablets are unchanged.
- **Camera mode's pictures load faster.** Home Assistant now scales each still picture to the size it's shown at, so a 4K camera no longer sends a full-size picture for a small tile (and a TV box no longer decodes one). Each camera's picture is fetched on its own and goes on screen as soon as it arrives; before, every tile waited for the slowest camera. Opening camera mode asks Home Assistant for its cameras' details all at once instead of one after another ([Camera mode](docs/app/Cameras.md#how-it-plays)).
- **The live picture starts sooner and plays more smoothly.** Home Assistant's connection details for a camera are asked for once, not on every switch, and a camera whose WebRTC didn't start goes straight to HLS next time instead of waiting 12 seconds on every turn. Candidates Home Assistant sends before its answer are no longer lost, so WebRTC has every path to connect on. The live picture runs about a quarter of a second behind, so it doesn't stutter on Wi-Fi. If a camera is still slow, [Camera mode](docs/app/Cameras.md#how-it-plays) says what to change in UniFi Protect and Home Assistant.

## [0.6.22] - 2026-11-14

### Android app

- **Camera mode looks like UniFi Protect's live view.** The pictures fill the whole screen, edge to edge with hairline gaps: the grid picks how many go across so the pictures keep their shape, and the last row's pictures widen so no cell is empty. Each picture has just its name in the corner, and Motion with an orange edge while it sees motion; a blue edge shows where the remote is. The bar with the views and the time floats over the top for a few seconds after a touch or a key press, then fades away, so it takes no room. This replaces 0.6.21's security monitor look ([Camera mode](docs/app/Cameras.md#how-it-looks)).
- **Dashboards and camera mode use the whole TV screen.** They kept a margin all round for TVs that crop the picture's edges, which today's TVs and the Shield don't; now they run edge to edge, and only the dashboards' bar keeps its text a little off the edge. The TV home keeps its margins.

## [0.6.21] - 2026-11-12

### Android app

- **Camera mode looks like a security monitor.** A monitor's bar across the top (a blinking REC, the view and how many cameras, the date and time to the second), pictures edge to edge on black with thin dividers, and a CCTV recorder's on-screen text on each: its channel and name (CH01 FRONT DOOR) and the time. MOTION and SELECTED are words on the picture, with an amber edge for motion; the spotlight shows a blinking LIVE once the live picture moves; NO SIGNAL and SIGNAL LOST when Home Assistant can't be reached. Faint scan lines and darker corners give it a monitor's glass ([Camera mode](docs/app/Cameras.md#how-it-looks)).
- **On a TV, camera mode has its own card.** The **Cameras** card is now right after **Talk to Lena** on the TV home, in camera mode's own look, and opens it straight away. The camera button is gone from the dashboards' bar on a TV (it stays on a tablet or phone).

## [0.6.20] - 2026-11-12

### Android app

- **Cameras are found on a large Home Assistant.** Settings → Cameras and camera mode said "Couldn't list Home Assistant's cameras: The connection to Home Assistant ended" (the log: "Message too long"): Lena's WebSocket client refused any message over 1 MB, and the state of every entity, which camera mode reads to find the cameras, is bigger than that on a large home. It now takes messages of up to 64 MB, counted across all the parts of a message.
- **Set up from your phone is in one place.** It's the first item in Settings; the copy on the Home Assistant page is gone.

## [0.6.19] - 2026-11-12

### Android app

- **The wall display gets back on its feet by itself.** A dashboard that can't load (Home Assistant restarting, the Wi-Fi down, a proxy's error) shows Lena's own **Can't reach Home Assistant** with the reason, instead of the web view's error page and nothing else; she asks Home Assistant again every 15 seconds and as soon as the network comes back, and loads the dashboard again once it answers. **Try again** asks now.
- **A crashed page no longer closes the app.** When Android ends a web page (it crashed, or memory ran short on a small TV box), the dashboards, your chats and camera mode's live picture start again with a new page instead of the whole app closing.
- **Camera mode connects again by itself.** When the connection to Home Assistant ends, it reconnects after 2, 5, 10 and then 30 seconds, or as soon as the network is back, keeping the view, your selection and the last pictures on screen.
- **A question that got no reply is followed, not failed.** If Open WebUI took longer than two minutes to take on a question (or a proxy cut the wait short), she now looks whether she's answering it before saying it failed, and follows the answer. It's never sent twice.
- **Following a long chat is lighter.** Each look at an answer being written downloads the whole chat, so a long chat is looked at less often (every 0.8 seconds for a short one, up to every 2.5 seconds), and a chat deleted meanwhile ends it at once.
- **Requests try again when it's safe.** Home Assistant's requests (still pictures and photos too) wait for the network to come back before trying again, and a GET is tried again when a proxy says the server is restarting (HTTP 502, 503, 504). Nothing that may have reached the server is sent twice unless it's a GET.
- **A Home Assistant ban says so.** When Home Assistant answers 403 (it banned the device's address after failed sign-ins), Lena says that, and how to lift it (`ip_bans.yaml`), instead of asking for a new token.
- **Each server's status.** Settings → Connections says under Open WebUI and Home Assistant how each answered its last check, and the wall display's bar shows a cloud with a line through it while one doesn't answer. Open WebUI isn't checked while Home Assistant's Assist answers.
- **On a TV, the connections stay ready and Home Assistant's commands arrive** whichever app is the home screen, as on a tablet that has Lena as its home screen. Android now says when the network comes back, so the wall display checks its connections at once instead of waiting for its next check.
- **A command from Home Assistant is never done twice.** The wall display now saves its place after each command, so an announcement or timer isn't repeated if the app closes partway through a batch.

### Development

- `tools/connection_check` checks the rules for trying failed requests again (`Retry.kt`), in Gitea's and GitHub's test and release workflows.

## [0.6.18] - 2026-11-12

### Android app

- **Set up from your phone.** The QR code now fills in every address and sign-in, not just Home Assistant: Open WebUI's address, sign-in address, email and password, Home Assistant's address and token (or its own sign-in, as before), your Gitea for updates, and the two motion sensors, picked from your Home Assistant's. It's the first item in Settings on every device. The page is never sent what's saved, what you type is encrypted on the phone with a key carried in the QR code (never sent over the network), nothing is saved until you press **Save** on the device, the first phone holds the page, and five guessed codes close it. Replaces Connect from your phone.
- **Camera mode.** Your Home Assistant's cameras on a TV, wall tablet or phone, in five views: **Show all** (a grid of still pictures), **Selected only**, **Spotlight select** (one live, full screen), **Spotlight on motion** (the camera that sees motion, live, until 30 seconds after it stops) and **Rotate** (each live in turn). Off unless turned on in **Settings → Cameras**, per device, with the cameras it may show switched on one by one; it runs only while it's open, and nothing pops up by itself. Built for UniFi Protect: a camera's streams count as one (the grid uses the low stream, the spotlight the high one) and its motion, person, vehicle and doorbell sensors are found on its own device. Live pictures are Home Assistant's WebRTC, with HLS and still pictures to fall back on. Opened from a **Cameras** card on the TV home, a button on the wall display's bar, Settings, or "show the cameras" ([Camera mode](docs/app/Cameras.md)).
- **Settings text, one name per thing.** It no longer says "this phone" on a TV or tablet, spells her screensaver one way, uses US spelling throughout and "and" instead of "&", and the two test buttons are both **Test connection**. A written standard ([Writing Settings text](docs/development/Settings-Text.md)), a check in `tools/check.py` and `tools/settings_text.py` (every item in Settings, for a read-through) keep it that way.


### Releases

- **GitHub catches up with Gitea's releases by itself.** Releases are made on Gitea and reach GitHub with the mirror's push every few hours, but GitHub starts nothing for the tags of a push that brings more than three at once, so several releases between two mirror pushes got no GitHub release. A new workflow (`.github/workflows/catch-up-releases.yml`) runs after each mirror push and every 6 hours, and starts GitHub's release workflow for each version with a tag but no release. Only the newest version is marked Latest, so the app always updates to it. Nothing changes in how you release ([Releasing](CONTRIBUTING.md#releasing)).
- **GitHub releases of a tag from Gitea no longer fail at the last step.** GitHub's release workflow built and signed the APK, then GitHub refused to create the release ("HTTP 403: Resource not accessible by integration"): it named the tag's commit, and GitHub refuses the workflow's token a release that names a commit other than main's newest. It now releases on the tag that's there. Started with Run workflow (as the catch-up does), it runs as it is on main and builds the code of the version's tag, so versions released before this fix get it too. To release the versions that failed (0.6.12 and later), run **Catch up releases** with **Also start again the versions whose release run failed** ticked; it starts three at a time, so run it twice for six.

### Development

- **A quieter build.** The app no longer uses what Android and Compose have deprecated (the old microphone permission callback, the old top bar colors and three left-to-right-only icons), so the build's warnings are the ones worth reading. `tools/check.py` reports the Python import rule as one line instead of a "PASS … isn't approved" line per import.

## [0.6.17] - 2026-11-09

### Android app

- **Her volume stays within limits, and no longer drifts to 0.** In quiet hours (before 8 AM, from 10 PM), a reply that started while the last was still being spoken lowered the volume again from the already lowered one, and the first to finish put back the wrong one, so a morning conversation could end with the device (a TV's whole sound, on a TV box) at nothing. Overlapping replies now share one change, the volume from before goes back once the last is spoken (and next time if she was closed mid-sentence), and a volume you change while she speaks stays.
- **Her quietest and her loudest.** Settings → Talking to Lena, under Adjust her volume: every volume change she makes (quiet hours, a noisy room, "speak more quietly" or "louder") stays between the two. Each kind of device has its own unless you choose: 20% to 100% on a phone or tablet, 40% to 100% on a TV. A device whose volume is fixed (left to the TV over HDMI) is left alone.

## [0.6.16] - 2026-11-08

### Android app

- **A TV home that fits the screen.** The TV home keeps inside the TV's safe area (TVs crop the picture's edges, which cut off the top), with a smaller clock and smaller cards, icons and text. **Talk to Lena**, the running timers, alarms and reminders, your Home Assistant dashboards and Lena's and the TV's settings now share one row, so the home is two rows: Lena, and Apps.
- **Choose which apps the TV home shows.** **Choose apps**, in the Apps row, lists every TV app: OK hides or shows one (it stays installed). It's the same list the tablet's Apps screen hides from.
- **Dark on a TV.** Lena's screens are always dark on Android TV, which has no dark mode of its own: Settings and the Home Assistant dashboards came up white. Home Assistant follows when its theme is left on the default (your profile → Theme → Auto).
- **Home Assistant dashboards with the remote.** On a TV, the arrows move between a dashboard's cards and buttons (down and right to the next, up and left to the one before; Home Assistant outlines the chosen one) and OK presses it; before, the arrows only scrolled the page. The dashboard and Lena's bar keep inside the TV's safe area too.

## [0.6.15] - 2026-11-08

### Android app

- **A TV home.** On Android TV, Lena's home screen is rows for the remote, over your photo slideshow: the time, weather and how she feels; **Talk to Lena** (chosen when the screen opens) with every running timer, alarm and reminder; your Home Assistant dashboards, each opening full screen (Back or Home returns to the rows); your TV apps, most used first, and All apps; and Lena's and the TV's settings. The tablet's bar over a dashboard was impractical with a remote.
- **Talk to Lena from a remote button.** The remote's microphone button always opens Google Assistant, so Lena has a second entry in the TV's apps, **Talk to Lena**, that opens her listening; put it on a customisable remote button (on a Shield, in Settings → Remotes & Accessories).
- **Connect from your phone says why a token fails.** A pasted token that didn't work always read as Home Assistant refusing it, even when the device simply couldn't reach Home Assistant. The page now says which: the token refused (401), Home Assistant not talking to the device at all (403, usually an IP ban after earlier failed sign-ins), or the address not found (Android before 12 can't look up `.local` names), its certificate not trusted on the device, or no connection; and the device's log has the details.
- **No more "Completing setup" after an update.** `tools/android-tv-home.sh --apply` also turns off Android TV's setup app, which otherwise took over the Home button whenever Lena was being updated and stayed on "Completing setup". Run it again to fix a TV stuck there; `--restore` turns it back on.

## [0.6.14] - 2026-11-08

### Android app

- **Connect Home Assistant from your phone.** No more typing a long-lived token with a TV remote: **Connect from your phone** (on the wall display before it's connected, and in Settings → Home Assistant) shows an address and its QR code. Your phone opens it on the same Wi-Fi, you sign in on your Home Assistant's own page, and Lena makes herself a long-lived token named after the device (and signs the first sign-in out). Pasting a token you've made works there too. The page is served only while the screen is open, behind a code shown on it. The QR code is drawn by Lena herself; there's no new library.
- **Stuck in Settings on a new wall display.** With Lena as the home screen but Home Assistant not set up yet (a tablet set up in that order, or one whose token didn't save), leaving Settings opened the home screen, which opened Settings again, so Back seemed to do nothing. The wall display now says what's missing instead, with **Settings** and **Apps** buttons, and opens your dashboards as soon as you come back from Settings with Home Assistant set up.

### Development

- The Gitea runner's app job retries Ubuntu's package servers (over IPv4, three tries) instead of failing the run on a short outage. `tools/pairing_check` checks Connect from your phone in every workflow that builds the app.

## [0.6.13] - 2026-11-08

### Android app

- **Android TV and the Nvidia Shield.** Lena installs on Android TV and Google TV boxes from Android 11 (she needed Android 16 before), and shows in the TV's apps with her own banner. There she's the wall display only: no chat or typing (a TV has no keyboard), and no settings that only make sense on a phone. See [Lena on Android TV](docs/app/Android-TV.md).
- **The remote.** Hold **OK** to talk (again to send, or to stop her talking); a short press still presses what's chosen. The remote's search button, on remotes that send one, talks too; **Menu** opens Settings; any button wakes the screensaver. The row the remote is on is outlined. Each button is logged once (Settings → Logs, "Remote"), to see what a remote sends.
- **Hearing through the remote's microphone.** On a TV, **Hearing you** offers **The TV's voice search**: the TV's own speech recognition, which hears through the remote. Phones keep the on-device recognizer.
- **The TV's home screen.** `tools/android-tv-home.sh` (from a computer with adb, once) turns the TV's own launcher off and makes Lena the home app, so the Home button opens her; `--restore` undoes it. Android TV has no setting for this.
- The Apps button lists and opens the TV's apps too.

### Wall display debloat

- **Nvidia Shield and Android TV.** `tools/wall-display-debloat.sh` recognises an Android TV and keeps what it needs on top of the usual list: the TV's voice search (Lena hears you through it), keyboard, remote service, Settings and launcher, the device keys streaming apps may check, and on a Shield, Shield Accessories, its button hooks and its updates. Apps installed on top stay on a TV unless you add `--apps`.
- **Over the network.** `--connect <IP>[:<port>]` (both scripts) connects with adb first and waits for the device's "Allow debugging?" prompt: a Shield's Network debugging, or a tablet's Wireless debugging. The suggested commands and the log carry the address, and log names no longer contain a ":" (not allowed in Windows file names).

### Development

- `minSdk` is now 30 (Android 11); `targetSdk` and `compileSdk` stay 36. Newer calls are behind version checks (`Compat.kt`, or a plain Android version check where it's used once), and release builds fail on any that isn't (lint's NewApi is fatal). Below Android 12 the orb look darkens instead of blurring and the glow uses a standard corner; below Android 13 Settings has no "Add the Quick Settings tile" button (Quick Settings' own edit button adds it). The APK also carries the wake word's 32-bit ARM code, for TV boxes that run 32-bit Android.

## [0.6.12] - 2026-11-08

### Android app

- **Updates from your own Gitea.** Settings → Software version → Updates → **Where updates come from**: type your Gitea's address and account (`https://gitea.example.com/you`), or the repository's address, and Lena checks and downloads her releases there instead of GitHub, with the same checks (`SHA256SUMS`, that it's Lena and newer, and signed with your key). The repository must be visible without signing in; empty the box to go back to GitHub.

### Development

- **Tested and released on Gitea first, without GitHub.** The project lives on Gitea, which push-mirrors to GitHub. `.gitea/workflows/test.yml` runs every test on each push and pull request, and `.gitea/workflows/release.yml` (Run workflow with a version, or a pushed `v*.*.*` tag) runs them again, then builds, signs, checks and publishes the APK as the Gitea release. They use no GitHub actions and download nothing from GitHub: jobs run in Docker Hub images (Python, and the official Gradle image with Google's Android SDK), and get the code from Gitea with git.
- **GitHub's release follows from the mirrored tag.** `.github/workflows/release.yml` publishes the same release on GitHub when Gitea's mirror pushes the tag (or from its own Run workflow, which now takes the version); `build.yml` only makes test builds.
- **Shared release scripts** in `scripts/`: `release_notes.py` checks the tag against every part's version (now including the voice bridge's) and the changelog, and writes the notes (install steps, then the changelog section, linking to GitHub); `check_apk.py` refuses an APK that's debug-signed, of another version or missing from `SHA256SUMS`; `publish_gitea_release.py` publishes on Gitea. The build and Home Assistant test steps are scripts too, so every runner does the same. `tests/test_release.py` tests the release scripts, including against a fake Gitea, and `tools/check.py` runs it.
- How to release is in [CONTRIBUTING](CONTRIBUTING.md#releasing).

## [0.6.11] - 2026-11-07

### Android app

- **Fixed: after the wake word she never stopped listening.** Through the wake word's microphone the recognizer never decides by itself that you've finished, so Lena does: once your words stop changing for a moment (or nothing's said for 7 seconds), the sound ends and the recognizer gives its result.
- **Choosing Hearing you → On this phone again** gives a recognizer that wasn't answering another chance at once, instead of a day later (after putting back speech packages a debloat removed, say).

### Home Assistant integration

- **Fixed: Optimize memory saying "The answer wasn't a list of memory lines".** The AI Task came back empty (Home Assistant's Ollama returns nothing when the model's context window is too small for her memory, or a thinking model spends the answer thinking). She now asks again for plain text, then through her conversation agent for alerts if one is chosen, and if all come back empty, says so and what to try.

### Wall display debloat

- **Keeps Android System Intelligence, Private Compute Services and its settings overlay.** On a Galaxy Tab A9+, removing them left the on-device speech recognizer never getting ready ("Couldn't hear you, error 11"). If you ran an earlier version, put them back with `adb shell cmd package install-existing --user 0 com.google.android.as` (and `com.google.android.as.oss`, `com.google.android.overlay.gmsconfig.asi`) and restart the tablet.

## [0.6.10] - 2026-11-07

### Android app

- **Fixed: "Couldn't hear you (error 11)" on some tablets** (the Galaxy Tab A9+). Its speech recognizer never gets ready: the microphone sat off for several seconds while Lena tried again, then gave up. She now waits less, and when the recognizer never gets ready she hears you another way at once (your server's speech-to-text, or else Home Assistant's) and keeps using that for a day.
- **Hearing you: Home Assistant**, a third choice under Settings → Talking to Lena → How she looks and sounds: Home Assistant's own speech-to-text (Whisper, Speech-to-Phrase, Cloud; pick one or it uses the first), the way a Voice satellite hears. It also works in Assist mode, where there's no Open WebUI.
- **A display is an Assist satellite** (see below): Home Assistant's timers asked through it show and ring on it (a copy that rings even if the connection drops; cancelling it here cancels it there), Home Assistant's announcements play on it (the chime, then her voice saying the message, or the sound sent), a conversation Home Assistant starts listens for your answer and carries it on, and the wake word chosen on the satellite's page is the one it listens for. On a satellite display, simple timer requests now go to Home Assistant instead of being kept on the tablet; alarms are still hers.

### Home Assistant integration

- **Each Lena display is an Assist satellite** (`assist_satellite.<display>`), Home Assistant's own voice-assistant device: `assist_satellite.announce`, `assist_satellite.start_conversation` and Assist's broadcast reach it, Home Assistant's timers work for it, and its page lists the app's wake words. It replaces the Announcement event entity from 0.6.8, which is removed.
- **Timers are Home Assistant's own.** In Assist, she uses Home Assistant's timer tools for the display; `lena.set_timer`, `lena.cancel_timer` and `lena.list_timers` (for Open WebUI and automations) use its timer intents too. Her own tools now cover alarms and reminders (`cancel_alarm_or_reminder`, `list_alarms_and_reminders`), which Home Assistant doesn't have.
- **Fixed: Optimize memory saying "The answer had no JSON".** The AI Task is now asked for a structured answer (a list of lines, which Home Assistant checks), and an answer in text anyway is still read if it lists the lines.
- `lena.announce` still works by room name and needs no text-to-speech engine; it now plays the announcement chime first.

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
