# How It Works

[← Back to README](../../README.md)

Lena is two things working together:

- **Your real Open WebUI page** for chatting, inside Android's built-in web view (the same engine as Chrome, updated through the Play Store). Everything Open WebUI does, including your tools, works as it does in a browser.
- **A native Android shell** around it, plus features that talk to Open WebUI's API directly: the drawer's chat list, the Ask sheet, notifications and scheduled prompts. They use the sign-in you already made inside Lena; there's no API key to set up.

## The main screen

**Top bar**
- **☰** opens the drawer.
- **The title** is the current chat's model, with her avatar when it's Lena. Tap it to start a new chat with another model.
- **Temporary chat** (one Open WebUI doesn't save), **New chat** and **Voice call** on the right.

**Drawer**
- **Lena** at the top, with whether your server is reachable (and its Open WebUI version), and **how she feels** if you've connected Home Assistant: her mood, energy and any reaction, and why ("Grumpy · Groggy, It just started raining").
- **Search**, then your **pinned chats** and your **recent chats**, grouped by day, taking up most of the drawer. How they're listed is up to you (Settings → Appearance → Chat list): slim groups (the default), rounded groups, a flat list, title and time, a timeline, or days that fold open. A spinner marks a chat that's still being answered.
- **Pinned chats** come first, the same as Open WebUI's sidebar. Up to five show (Settings → Appearance → **Pinned chats in the menu**: 3, 5, 8 or 12); with more, **View all** shows the rest and **Show fewer** folds them away again.
- **Long-press** a chat to **pin** (or unpin), **rename** or **delete** it (deleting removes it from Open WebUI too).
- Along the bottom, three buttons: **Settings**, **Memory & knowledge** (what Lena remembers and what's in your knowledge collections; more below) and **Reload**.

**Open WebUI's own menus** are hidden because the top bar and drawer replace them. To get them back, for things the shell doesn't cover such as chat controls, your Open WebUI settings and signing out, turn on **Open WebUI's own menus** in Settings.

Lena hides Open WebUI's own header and sidebar because the top bar and drawer replace them. If an Open WebUI update changes its page, the header may simply reappear until Lena catches up; nothing breaks.

**New chats** use your chosen model (Settings → Model), unless you pick another from the title.

**Back** goes back within Open WebUI, then closes Lena to the background. **Swiping right from the middle of the left edge** opens the drawer instead of going back; Android lets an app take over only part of an edge, so higher or lower on the left edge, and the right edge, still go back.

## Talking to Lena

The assistant gesture opens Lena over whatever you're doing, already listening, like Google Assistant or Home Assistant's Assist.

**How it looks** is your choice (Settings → Appearance → Talking to Lena, with **Try it** to see it):

| Look | What you see |
|---|---|
| **Orb** (default) | The screen darkens (and blurs, on phones that support it), her logo pulses in the middle, and her words appear underneath like captions. Tap the orb to talk or stop her. |
| **Sheet** | A panel from the bottom with the whole conversation. Everything below describes this one. |
| **Glow** | Lena's colours run around the edges of the screen, brighter as you speak, with a small caption card for your words and her answer. |
| **Bar** | A compact bar at the bottom to type or talk in; her answer appears in a card above it, with Copy, Open in Lena and New. |
| **Island** | A small black pill at the top showing what's happening; her answer drops down under it like a notification. Tap her avatar to talk. |
| **Subtitles** | No panel at all: her words appear at the bottom of the screen over a soft fade, like film subtitles. |

Every look listens, answers, speaks and keeps listening the same way; only the Sheet shows earlier turns. In the others, **⋮** has everything that doesn't fit: type or talk, new conversation, photos, copy, voice call and open in Lena. Tapping empty space closes any of them.

1. **Talk.** Your words appear as you speak. When you stop, Lena sends them. Tap the mic to send early.
2. **Lena answers** on your server with your model's tools, shows the reply as it's written, then **reads it aloud**. Tap the button while she's talking to stop her. When you talk (rather than type), Lena tells her the reply will be read aloud, so she answers the way she would in the kitchen: short, plain sentences, no lists or formatting.
3. **Then she listens again**, so you can carry on the conversation without tapping. If you say nothing, the mic closes quietly. Tap the mic any time to talk. Saying just "thanks", "that's all", "never mind" or "stop" closes the sheet without asking her anything.

**One conversation: "Assistant Lena".** Every time you talk to Lena, she continues the same chat, titled **Assistant Lena** and pinned in Open WebUI, so she remembers what you talked about before, even days later, and she's the same Lena with the same history on every screen. The app finds it by its name, so it's the same chat after reinstalling the app or on another phone. The sheet opens on the whole conversation, scrolled to the latest.

- **New conversation** (the speech-bubble button in the sheet) starts over: the old chat stays in your history, unpinned and renamed with the date ("Assistant Lena (until Oct 31, 2026)"), and a new Assistant Lena begins.
- Delete the **Assistant Lena** chat (from the drawer or Open WebUI) and the next time you talk, she starts a fresh one.
- A conversation that never ends would outgrow any model, so Lena gives it **half of the model's context** (set **Her context size** under Settings → Conversation; 32,768 by default), filled from the newest exchange back. For short spoken exchanges that's usually 50 to 80 of them. The other half stays free for her system prompt, tools, tool results and reply. The chat itself keeps everything, and **Continue in Lena** opens it in the full app.

- The **keyboard** button switches to typing (and the mic button back). Lena only reads replies aloud when you spoke.
- The **waveform** button switches to a full voice call, and the **expand** button opens the full app.
- Your **quick prompts** appear as chips when typing.
- Tap outside the sheet to close it. If Lena is still answering, you get a notification when she's done.

**Settings → Voice** chooses who hears you and who speaks:

| Setting | Choices |
|---|---|
| Hearing you | **On this phone** (default): Android's on-device speech recognition, which shows your words as you speak, and the audio never leaves the phone. **Your server**: Open WebUI's speech-to-text, the same as call mode. |
| Lena's voice | **Your server's voice** (default): Open WebUI's text-to-speech, the same voice as call mode. **The phone's voice**: Android's text-to-speech. **Off**. |
| Keep listening | **After every reply** (default), **Only when Lena asks a question** (like Home Assistant's "continue conversation"), or **Off**. |
| Her context size (Conversation) | Your model's context length in tokens (32,768 by default). Half of it holds the ongoing conversation. |

If the phone's speech model isn't installed yet, Android starts downloading it and Lena uses your server meanwhile. If the phone's recognizer can't listen for any other reason, Lena uses your server for that turn. If your server's voice isn't set up, Lena uses the phone's.

Other ways in: **Talk to Lena** on the widget and the app icon's long-press menu, and **Type to Lena** for the keyboard version.

**Show her a photo.** The camera button in the sheet takes a photo or lets you choose one ("What's this plant?", "Read this label"). Ask about it by typing or by tapping the mic, or just send it. The photo is shrunk to a sensible size, sent with your question, and saved in the chat like any other. Lena needs a model that can see images; `gemma4:12b` can. The camera app saves the picture into Lena's private storage and it's deleted as soon as it's read; choosing a photo uses Android's photo picker, so Lena only ever sees the one you pick.

**Her voice follows her mood** (Settings → Voice, off by default, needs Home Assistant). She speaks a little slower and lower when she's groggy, drained or wistful, and a little quicker and brighter when she's wired, cheerful or playful. The changes are small on purpose. It works with your server's voice and the phone's.

**On the lock screen** (Settings → Assistant gesture → On the lock screen, off by default). With it off, Android asks you to unlock before the sheet opens. With it on, the gesture opens her straight over the lock screen in a private mode:

- She starts a fresh conversation. Your **Assistant Lena** chat and its history stay hidden.
- Her mood shows, but not the reasons.
- No photos, and opening the app, a chat or a call asks you to unlock first.
- She's told the person may not be you, so she shouldn't unlock, open or disarm anything, or share personal details, earlier conversations or what she remembers.

That last part is an instruction to her, not a lock: anyone holding your phone can still talk to her, and a model can be talked round. If your Home Assistant exposes locks, garage doors or alarms to her, think about that before turning this on.

## Memory & knowledge

Open it from the drawer. It shows what Lena draws on beyond the conversation, so you can fix anything that went in by mistake.

- **Hers** (only if you've connected Home Assistant): Lena's own memory, the to-do list the Lena integration keeps in Home Assistant, which she uses in the house, in Open WebUI and here. Search, correct, forget or add, the same as below.
- **Memories** (called **Open WebUI** when Hers is there): the facts Open WebUI keeps about you. Search them, tap one to correct it, delete one to make Lena forget it, or add one with **+**.
- **Knowledge**: your knowledge collections and the documents in each. Remove a document from a collection you can edit (by default, Open WebUI also deletes the file).

Talking to Lena uses Open WebUI's memories when its memory setting is on for you (**Settings → Personalization → Memory** in Open WebUI), the same as the web page. Her shared memory reaches her through the Lena filter in Open WebUI; see [Lena in Open WebUI](../open-webui/Setup.md).

## How she feels

Connect Home Assistant under **Settings → Home Assistant**: its address and a long-lived access token (your Home Assistant profile → Security). Any user's token works. **Test connection** checks it, and tells you if the [Lena integration](../home-assistant/Installation.md) isn't set up yet. The Ask sheet then shows how she feels under her name, refreshed after each reply in case something you said landed, and the drawer shows it too.

## Wall display

**Settings → Wall display** turns a tablet on the wall (or a docked phone) into a home panel: your Home Assistant dashboards while someone's using it, a photo frame or her clock when no one is, and her name to talk to her from across the room. Everything for it is on that one page:

| On the page | What it does |
|---|---|
| **Start the wall display** | Full screen and kept on; back leaves it. Without Home Assistant connected it's just the idle screen. |
| **Make it this tablet's home screen** | Android's choice of home app; pick Lena. Home then always brings the wall display back, and **Apps** in its bar lists everything else on the tablet. |
| **Dashboards** | The Home Assistant dashboards it shows. |
| **When no one's around** | When the screen saver starts, what it shows (her clock or a photo frame), when your chats give way back to it, and what wakes it up. |
| **Wake word** | Saying her name to talk to her. |
| **Use it as the screen saver** | The same idle screen as Android's screen saver, so it appears by itself whenever the device charges. |

### Dashboards

Your dashboards are signed in with the token from Lena's settings, through the same "external auth" the Home Assistant companion app uses, so there's no login page. Add each one by name and path (the part after your Home Assistant's address, such as `lovelace` or `dashboard-floorplan/0`). Links that lead away from Home Assistant open in your browser; iframe and camera cards load inside it.

**One bar across the top.** Home Assistant's own header (the dashboard's name, its view tabs, search, Assist and edit) is replaced by Lena's bar, so there's a single bar:

| From left to right | What it does |
|---|---|
| **Menu** | Home Assistant's sidebar, with all its dashboards, settings and the rest. Back is your device's back gesture (or button): up out of a subview, or back through what you opened. |
| **Time, date and weather** | |
| **The dashboard's views** | Its view tabs, as chips (only when it has more than one). |
| **Dashboards** | Switches between the dashboards you added here (only when there's more than one). |
| **Lena** | Her mood and why. Tap to talk to her. |
| **Chats, Reload, Apps, Settings** | Your chats, reload the dashboard, the tablet's other apps, and Lena's settings. |

The same goes for Home Assistant's own pages opened from its menu (Overview, Energy, Calendar, To-do, Map, Logbook, History, Media and so on): their header goes too, though a second row of filters stays where a page has one, and buttons that lived only in a page's header (such as Calendar's refresh or To-do's ⋮ menu) go with it. Its settings pages keep their own headers. The dashboard's name isn't shown. Editing a dashboard (from Home Assistant's menu) brings its own header back while you edit, since its Done button lives there. **When she's worried** about something at home, the bar turns into a red banner with what's wrong and a **Talk to her** button, and the screen wakes up if it was idle.

**Your chats, then back.** Open your chats from the bar and walk away, and after a minute without a touch (Settings → Wall display → When no one's around → **Back from your chats after**: 30 seconds to 10 minutes, or never) it goes back to the dashboards and straight to the screen saver. It waits while the keyboard is up, during a voice call, and while she's still answering. On a tablet where the wall display is the home screen, this applies however your chats were opened.

### When no one's around

After a while without a touch (**Screen saver after**: 30 seconds, 1, 2, 5, 10, 15 or 30 minutes, an hour, or never; 5 minutes by default) the dashboards give way to the screen saver, which shows one of these. Android's own screen saver shows the same.

- **Her clock:** the time, the date, the weather, how she feels and the last thing she said, on black. It drifts a few pixels each minute so it doesn't burn into the screen, and late at night (or when she's drained) it goes dim and warm and hides what she said.
- **Photo frame:** an album from Home Assistant's media: Immich's albums, people, tags and favorites (with Home Assistant's Immich integration), your My media folders, or any other media source with photos. Choose how long each photo shows (5 seconds to 5 minutes), the transition (fade, slide, slow zoom and fade, or none), shuffle, how photos fit the screen, and whether the time and her mood sit in a corner.

  **Fit:** **Smart** (the default) fills the screen when little would be cut off, and shows portrait photos, panoramas and other odd shapes whole on a blurred copy of themselves, so a portrait photo on a landscape tablet keeps everyone's heads. **Fill the screen** always fills it. **Whole photo** never crops. Whenever a photo is cropped, the crop is centred on the faces in it (found on the device by Android's own face finder), or on the upper third of a portrait photo when there are none, and the slow zoom moves in towards them.

  To use your Immich photos, add the **Immich** integration to Home Assistant (Settings → Devices & services → Add integration → Immich). Its albums, people, tags and favorites then appear in Home Assistant's media, and **Album** lists them. **Her dim clock at night** (on by default) swaps in the night clock from 10 PM to 6 AM and whenever she's drained, so it doesn't light up the room. Immich photos come at its screen-sized "preview" quality, and the album is read again every half hour so new photos join in.

On the wall display, the screen goes to 40% brightness for photos and 2% for the night clock. **A touch wakes it**, and so does a **motion or presence sensor** you name (checked every few seconds while idle), or Lena being worried.

**As the screen saver** over the lock screen it shows her mood but not the reasons, nor anything she said. Tapping it opens Lena listening (after you unlock, unless **On the lock screen** is on).

### Wake word

**Wall display → Wake word** lets the wall display and the screen saver listen for her name. Nothing listens until you turn it on, and even then it only ever opens Lena when it hears her name; the wall display never starts listening by itself when it starts. Choose:

- **Hey Lena** (the default for new setups): a [microWakeWord](https://github.com/kahrendt/microWakeWord) model by [Tater Totterson](https://github.com/TaterTotterson/Tater-Wake-Words), the same kind of model Home Assistant's voice satellites run, trained and measured at about one false start every ten hours of background sound. Two words are much harder to say by accident than one.
- **Lena:** the same kind of model for just her name. Quicker to say, but ordinary speech and TV set it off more often.
- **Hey Jarvis:** [openWakeWord](https://github.com/dscripka/openWakeWord)'s ready-made model, free for personal, non-commercial use.

The app computes exactly the sound features Home Assistant's voice devices do: its front end is a line-for-line port of TensorFlow Lite Micro's, checked bit for bit against the original on every build.

**How sure she must be:** **Balanced** uses each model's own setting. **Fewer false starts** needs her to be surer, for longer (the average over a longer stretch of sound has to pass a higher bar). **Easier to wake** lowers the bar, for a big room.

**Against false starts**, she decides the way ESPHome does on Home Assistant's voice devices:

- She can't be woken in the first second after she starts listening, while the sound features settle (that's when a false start is likeliest, for example as the wall display comes up after a restart), nor again until she's calmed down after the last time.
- ESPHome's own **voice check** (a small model that tells speech from other sound) has to agree someone is speaking, so music, a TV jingle or a dropped pan can't wake her. What it turns away is noted in Settings → Logs.
- Each time she wakes, Settings → Logs says how sure she was and what she needed, which helps when choosing the setting above.

**When the conversation's over she goes back by herself** to the dashboard (dimmed, if it was), or to the screen saver: when you say nothing after her answer, when she's answered and isn't listening for more, or straight away when you say something like "thanks", "that's all", "never mind" or "stop" on its own. Touch the screen and she stays until you close her. Then the wake word is listening again.

It runs entirely on the device: the sound is scored a few times a second and thrown away, and nothing is recorded or sent until she hears her name; then the microphone is handed to Lena as if you'd tapped. **Try it** shows how close each try gets, so you can check it hears you from where you'll stand. It only listens while the wall display or the screen saver is showing, and Android's microphone indicator stays on while it does. Some phones don't let an app use the microphone from the screen saver; when that happens it says so and you tap to talk.

## House alerts

When something in your house worries her (a leak, smoke, a door left open: the sensors you chose in the Lena integration), she can tell you on your phone, and tapping the alert opens Lena listening so you can ask what's going on.

- **Instant, in her words:** in Home Assistant, Lena integration → Configure → **Tell these phones when she's worried**, pick your phones (they need the Home Assistant companion app), and choose your Lena agent under **Write alerts in her words with**. Each alert is written by her, and an "all clear" replaces it when the sensor clears.
- **Backup on this phone:** Settings → Home Assistant → **Alerts on this phone**. The app checks every 15 minutes, the shortest Android allows an app without Google's push service, so an alert can arrive up to 15 minutes late and uses the plain reason ("Leak sensor is detecting water."). Use it on phones without the companion app; on phones with both, you'd get each alert twice.

## Share to Lena

Share text or a link from any app and pick **Ask Lena**. It goes into the sheet's text box so you can add what you want ("Summarise this", "Is this right?") before sending. Nothing is sent until you tap send.

## Voice calls

Start one from the top bar, the drawer, the Quick Settings tile, the widget, the app icon's long-press menu, or the assistant gesture. Calls use Open WebUI's own call mode.

- The screen stays on during a call.
- The first call asks for microphone permission.
- If you leave Lena mid-call, Android cuts the app off from the microphone until you come back.

## Quick Settings tile

Settings → Widget & tile → **Add the Quick Settings tile**. Tapping **Call Lena** in the pull-down shade starts a call (after unlocking).

## Home screen widget

Settings → Widget & tile → **Add the widget**, or add it from your launcher's widget list. It has **Talk**, **Voice call**, **Type** and **New chat** buttons, and your first three quick prompts, which open the sheet and send straight away. It uses your wallpaper's colours.

Turn on **Her mood on the widget** (Settings → Widget & tile, needs Home Assistant) to show how she feels under her name. It updates every half hour and after you talk to her. It shows only her mood, never the reasons, since anyone can see your home screen.

## Quick prompts

Settings → Quick prompts. Up to six. They appear as chips in the sheet when typing; the first three also appear on the widget and on the app icon's long-press menu.

## "Lena finished" notifications

If you leave Lena (or close the sheet) while she's still answering, Lena keeps an eye on the chat and notifies you with the start of the reply when it's done. Tap it to open the chat. Turn it off with **Reply notifications** in Settings.

Lena watches for up to about nine minutes. Android may delay the check a little to save battery.

## Scheduled prompts

Settings → Scheduled prompts lists them by **name**, each with when it runs and an on/off switch. Tap one to open it:

| Setting | What it does |
|---|---|
| **Name** | What it's called in the list and on its notification, such as "Morning briefing". |
| **What she's asked** | The prompt. |
| **Show the prompt in the chat** | Off (the default): the chat shows just the prompt's name ("Morning briefing") and her answer, and the prompt itself goes to her with that one request, unsaved. On: the chat shows the whole prompt, like any question. |
| **Time** and **Repeats** | Every day, weekdays, weekends, on the days you pick, every few hours (1 to 12, from the time until midnight), once a month (on a day you pick; the 29th to 31st mean the month's last day in shorter months) or once (on a date). The next run is shown under **On**. |
| **Chat** | Optional. Every run goes into the chat with this name, made the first time; give several prompts the same chat to keep them in one history, such as a morning and an evening briefing in "Daily briefings". Empty: each run is a chat of its own. |
| **Run it now** | Asks her straight away, to try it out. |

At each time Lena sends the prompt to your chosen model, waits for the answer and notifies you with it. The conversation is saved in your Open WebUI history. Android runs these alarms within a few minutes of the time to save battery, and after a restart Lena sets them again.

## Links

- Pages on your Open WebUI address open inside Lena.
- Your sign-in addresses (such as Authentik), if you've added any, also open inside Lena so sign-in can finish.
- `http://` and `https://` links to anywhere else (citations, web search results and so on) open in your normal browser.
- Everything else, such as `intent:` and `file:` links, is blocked.

## Attachments

Open WebUI's attach button opens Android's file picker. Lena has no camera permission; photos come from the picker.

## Logs

**Settings → Logs** shows what's been going on: **Problems** (warnings and errors), **Everything** (what she was doing too), and **Android** (Android's own log for Lena's current run, for crashes inside the web pages). Tap an entry for the details, such as where a crash happened. Search, copy, share or clear it from the top bar. The Logs row in settings says how many problems there were in the last day.

## Updates

**Settings → Updates** checks your GitHub repository's latest release, downloads it, checks it, and installs it, with a token for a private repository. **Re-download and install** gets the latest release again even if you already have it, to repair an install or after the version number was reset. See [Installation → Updating](Installation.md#updating).

## Signing in by herself

Open WebUI can sign out every device when it restarts (it does whenever its secret key changes), which would leave a wall tablet on its sign-in page and scheduled prompts failing. **Settings → Open WebUI → Sign in by herself** fixes that: choose **Email and password** or **LDAP**, enter them once, and **Test**. From then on, whenever Open WebUI ends her session, Lena signs in again by herself, in the app's chat page, the Ask screen, the wall display, scheduled prompts and alerts alike, and carries on where it was. A wrong password is tried at most once every half minute.

The details are stored encrypted with a key that never leaves the phone (Android's keystore) and sent only to your Open WebUI's sign-in. It can't do single sign-on (Authentik, Google and the like). The better fix, if you run the server, is to give Open WebUI a fixed `WEBUI_SECRET_KEY`, so restarts don't sign anyone out; this setting then just covers the rare time it does.

## Permissions

| Permission | Why |
|---|---|
| Internet | To reach your Open WebUI (and your Home Assistant, if you add it). |
| Microphone | Talking to Lena, call mode, voice input and the wake word. Asked the first time. In the main app, only your Open WebUI address can use it. |
| Notifications | "Lena finished", scheduled prompts and house alerts. Asked the first time you open Lena. |
| Run at startup | Sets your scheduled prompts again after the phone restarts. |
| Change audio settings | Needed by Android's web view to use the microphone. It never prompts. |
| Install apps | Installing an update you downloaded in Settings → Updates. You allow it in Android's settings, and every install asks you to confirm. |

WorkManager, the Jetpack library that runs the background checks, also adds its own no-prompt permissions for network status and keeping the phone awake during a check. Lena never asks for camera, location, contacts or storage.
