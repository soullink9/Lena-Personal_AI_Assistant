# How It Works

[← Back to README](../../README.md)

Lena is two things working together:

- **Your real Open WebUI page** for chatting, inside Android's built-in web view (the same engine as Chrome, updated through the Play Store). Everything Open WebUI does, including your tools, works as it does in a browser.
- **A native Android shell** around it, plus features that talk to Open WebUI's API directly: the drawer's chats and folders, All chats, the Ask sheet, notifications and scheduled prompts. They use the sign-in you already made inside Lena; there's no API key to set up.

## The main screen

**Top bar**
- **☰** opens the drawer.
- The **Chat | Home** switch (Lena's logo is in the drawer, which leaves the bar more room): **Home** opens your Home Assistant dashboards (the wall display), and the same switch there brings you back to your chats. It shows once Home Assistant is set up in Settings; turn it off in Settings → Chat screen.
- **Temporary chat** (one Open WebUI doesn't save) and **New chat** on the right.

**Drawer**
- **Lena** at the top, with whether your server is reachable (and its Open WebUI version), and **how she feels** if you've connected Home Assistant: her mood, energy and any reaction, and why ("Grumpy · Groggy, It just started raining").
- **Search chats.** Results replace the list, as many as fit; **See all results** at the bottom opens every one in All chats.
- Then your chats, in this order, as many as fit. The drawer never scrolls:
  - **Pinned** chats, the same as Open WebUI's sidebar (up to five; Settings → Chat screen → **Pinned chats**: 3, 5, 8 or 12).
  - Your **folders** (Open WebUI's folders). Tap one to open or close it; open folders show their chats. Open WebUI remembers which are open, so its sidebar matches. (Settings → Chat screen → **Folders** leaves them out of the menu.)
  - **Recent** chats, newest first.
- **Your order:** the **Reorder** button (⇅) on the Pinned and Folders headings puts them in the order you like, with up and down arrows. Open WebUI keeps no order for either, so it's kept on this phone; All chats uses it too. Chats you pin later come first until you reorder again, and folders inside folders stay under their parent.
- **View all chats** opens a page with everything: all your pinned chats, every folder and your whole history (a page at a time), with **search**. Its history is listed the way you choose (Settings → Chat screen → All chats list style): slim groups (the default), rounded groups, a flat list, title and time, a timeline, or days that fold open. There you can also make a **new folder** (the folder button at the top) and long-press a folder to **rename** or **delete** it (its chats are kept and move back to your chat list).
- **Long-press** any chat, in the drawer or All chats, to **pin** (or unpin) it, **move it to a folder** (or out of one, or into a new folder), **rename** or **delete** it (deleting removes it from Open WebUI too). A spinner marks a chat that's still being answered.
- Along the bottom, two buttons: **Settings** and **Memory & knowledge** (what Lena remembers and what's in your knowledge collections; more below). To reload, use Settings → **Refresh all connections**.
- **Folders look like chats**: in the drawer and in All chats a folder is a row like the chats around it (in your list style), with a folder icon.

**Lena's look for Open WebUI.** Open WebUI's pages get a warm, quiet style in the spirit of Claude.ai: off-white and warm charcoal instead of white and grey, a clay accent (the send button, links and switches), your messages in a soft bubble and her replies in a serif. It follows light and dark mode, uses the phone's own fonts, and applies only inside the app (the browser keeps Open WebUI's own look). Turn it off in Settings → Chat screen → **Lena's look**. It recolours Open WebUI through its colour settings, so an Open WebUI update can at worst bring back parts of its own look; nothing breaks.

**Open WebUI's own menus** are hidden because the top bar and drawer replace them. To get them back, for things the shell doesn't cover such as chat controls, your Open WebUI settings and signing out, turn on **Open WebUI's own menus** in Settings → Chat screen.

Lena hides Open WebUI's own header and sidebar because the top bar and drawer replace them. If an Open WebUI update changes its page, the header may simply reappear until Lena catches up; nothing breaks.

**New chats** use your chosen model (Settings → Model). To pick another model for one chat, turn on **Open WebUI's own menus** and use its model selector.

**Back** goes back within Open WebUI, then closes Lena to the background. **Swiping right from the middle of the left edge** opens the drawer instead of going back; Android lets an app take over only part of an edge, so higher or lower on the left edge, and the right edge, still go back.

## Settings

Open them from the drawer (or long-press Lena's icon). **Search settings** at the top finds any setting by its name or by what it's about ("photos", "voice", "token", "folders") and says where it lives.

Below it, the settings are in sections, and every row says how it's set and opens its own page. A setting with several choices shows **only the current one**; tap it for the list, with a line on each choice. Rows with ↗ open Android's own settings, for the three things only Android can change.

| Section | Rows |
|---|---|
| **Connections** | **Open WebUI** (address, sign-in address, signing in by herself), **Home Assistant** (address, token, test), **Who answers** (Open WebUI or Home Assistant's Assist, [below](#who-answers)), **Model** (the model for new chats, the sheet and scheduled prompts, and the Assistant Lena conversation with her context size) and **Refresh all connections** (checks both servers, and reloads your chats and dashboards when you go back to them). |
| **Wall display** | **Make it this device's home screen** ↗ (dashboard mode), **Make it this device's screensaver** ↗ (Android's screen saver while charging), **Screensaver** (what shows when no one's around, when, and what wakes it) and **Dashboards**. |
| **Talking to Lena** | **Make Lena your digital assistant** ↗, **Wake word** (on or off, her name, how sure she must be, and whether she listens only on the wall display or whenever Lena is open), **How she looks and sounds** (her look, who hears you, her voice, keep listening) and **Interacting with Lena** (what the assistant gesture opens, and the lock screen). |
| **Chat screen configuration** | **Chat screen**: **Lena's look** for Open WebUI's pages, **Open WebUI's own menus**, the **Chat \| Home switch**, how many **pinned chats** and whether **folders** show in the menu, and the **All chats** list style. |
| **Device customization** | **Notifications** (reply notifications and house alerts), **Widgets & tile**, **Quick prompts** and **Scheduled prompts**. |
| **About** | The **software version**. Tap it for **Development**, a page with no row of its own: **Updates**, **Logs** and **Return to defaults**. |

**Return to defaults** puts timings, the wake word's options, how she looks and sounds, the chat screen, notifications and the screensaver's options back to how a new install starts. It keeps your connections (Open WebUI, Home Assistant, signing in by herself, who answers and Assist's agent), the model and context size, your dashboards and motion sensor, the screensaver's album, whether she listens for her name (**Listen for her name** stays as you set it: switching the microphone on or off is never a reset's call), your quick and scheduled prompts, and your order of pinned chats and folders. It asks first.

### Who answers

Settings → Connections → **Who answers** picks who replies when you talk to her: **Open WebUI** (the default: your Lena model, with every conversation kept in your chats) or **Home Assistant Assist** (Home Assistant's conversation agent, through its `/api/conversation/process`). It's there so Lena works without Open WebUI at all. With Assist, everything that asks her goes to Home Assistant: the sheet, however it's opened (the assistant gesture, the wake word, the tile, the widget, quick prompts, sharing to her), typing in the chat, and scheduled prompts.

- **Conversation agent:** Home Assistant's choice (the assistant set in Home Assistant → Settings → Voice assistants), or any agent it has, listed from its `conversation.*` entities (Home Assistant's own Assist, or an AI agent such as Ollama's).
- Talking to her continues one Assist conversation, the way the Assistant Lena chat does with Open WebUI; **New conversation** starts another. Home Assistant forgets a conversation after a while of quiet, and then the next one simply starts fresh.
- Assist takes words only, so the photo buttons are hidden. It can switch things in your home, so from the lock screen it waits until you unlock.
- **The chat screen is Lena's own:** one conversation kept on this device, newest at the bottom. Type to her there, or tap the mic for the sheet. Everything you ask her, typed, spoken, quick prompts and scheduled prompts (marked as such), is kept there with her full answer; a scheduled prompt's notification opens it. **New conversation** at the top starts a new Assist conversation and marks where it began. Its menu has Settings, her memory and, on the wall display, Apps.
- **Settings that are only about Open WebUI step aside:** its connection and the model, the chat screen's look, menu and list options (the Chat | Home switch stays), reply notifications, a scheduled prompt's chat, and "your server" for hearing and her voice (the phone hears you and speaks). **Memory** shows only her own memory, from the Lena integration, as does her mood.

**Any screen size.** On a foldable's outer screen (or any narrow screen) her clock, the photo frame's clock and the orb shrink to fit, and the drawer leaves a strip of the page showing. On a wide inner screen or a tablet, Settings, All chats and Memory & knowledge stay a readable width in the middle. Folding or unfolding keeps your chat open.

## Wall display

The **Wall display** section of Settings turns a tablet on the wall (or a docked phone) into a home panel: your Home Assistant dashboards while someone's using it, a photo frame or her clock when no one is, and her name to talk to her from across the room.

**Dashboard mode is the home screen.** Only when Lena's wall display is the device's home screen are the dashboards and your chats **full screen**, kept on, and only then does the **screen saver** come on by itself and your chats go back to it when left alone. Anywhere else (on your phone, say), **Home** shows your dashboards as a normal screen with Android's bars, which sleeps as usual. The **Chat | Home** switch keeps the same view both ways.

| Row | What it does |
|---|---|
| **Make it this device's home screen** | Android's choice of home app; pick Lena. That's dashboard mode: full screen and the screensaver. Home then always brings the wall display back, and **Apps** (in Home Assistant's menu, or the bar, and at the bottom of your chats' menu) lists everything else on the tablet. Apps only shows on the wall display, where there's no launcher; its pencil chooses which apps it lists, without changing anything in Android. |
| **Make it this device's screensaver** | Android's screen saver setting; pick Lena, and the same idle screen appears by itself whenever the device charges. |
| **Screensaver** | When it starts, what it shows (her clock or a photo frame), when your chats give way back to it, and what wakes it up. |
| **Dashboards** | The Home Assistant dashboards it shows. |

Open the dashboards with the **Home** switch on the main screen. The wake word is under **Talking to Lena**.

A tablet that's only a wall display can be stripped down to Lena and a few apps you choose: see [Preparing a tablet for the wall display](Wall-Display-Tablet.md).

### Dashboards

Your dashboards are signed in with the token from Lena's settings, through the same "external auth" the Home Assistant companion app uses, so there's no login page. Add each one by name and path (the part after your Home Assistant's address, such as `lovelace` or `dashboard-floorplan/0`). Links that lead away from Home Assistant open in your browser; iframe and camera cards load inside it.

**One bar across the top.** Home Assistant's own header (the dashboard's name, its view tabs, search, Assist and edit) is replaced by Lena's bar, so there's a single bar:

| From left to right | What it does |
|---|---|
| **Menu** | Home Assistant's sidebar, with all its dashboards, settings and the rest, and at its bottom Lena's own **Apps** (the tablet's other apps) and **Lena settings**. Back is your device's back gesture (or button): up out of a subview, or back through what you opened. |
| **Chat \| Home** | The same switch as on the main screen: **Chat** opens your chats. |
| **Time, date and weather** | |
| **The dashboard's views** | Its view tabs, as chips (only when it has more than one). |
| **Dashboards** | Switches between the dashboards you added here (only when there's more than one). |
| **Lena** | Her mood and why. Tap to talk to her. |
| **Apps, Settings** | Only if they couldn't be added to Home Assistant's menu (after a Home Assistant update, say): then they stay in the bar, in a **⋮** menu on a narrow screen. On a narrow screen the time and her mood's words also step aside to leave the views room. To reload the dashboard, use Settings → **Refresh all connections**. |

The same goes for Home Assistant's own pages opened from its menu (Overview, Energy, Calendar, To-do, Activity, Map, History, Media and so on): their header goes too and the page moves up into its space, though a second row of filters stays where a page has one, and buttons that lived only in a page's header (such as Calendar's refresh or To-do's ⋮ menu) go with it. Apps served through the Ingress Proxy integration (Music Assistant and the like) lose the proxy's toolbar the same way. Home Assistant's settings pages keep their own headers. The dashboard's name isn't shown. Editing a dashboard (from Home Assistant's menu) brings its own header back while you edit, since its Done button lives there. **When she's worried** about something at home, the bar turns into a red banner with what's wrong and a **Talk to her** button, and the screen wakes up if it was idle.

**Your chats, then back** (dashboard mode only). Open your chats with the **Chat** switch and walk away, and after a minute without a touch (Settings → Screensaver → **Back from your chats after**: 30 seconds to 10 minutes, or never) it goes back to the dashboards. The screen saver keeps its own time (below), on every screen, so whichever is shorter comes first. It waits while the keyboard is up, while you're talking to her, and while she's still thinking or answering, and starts counting again once she's done. Switching back to **Home** yourself does the same at once. It applies however your chats were opened.

### Screensaver

In dashboard mode the screen never goes dark, on any of Lena's screens. After a while without a touch (**Screen saver after**: 30 seconds, 1, 2, 5, 10, 15 or 30 minutes, an hour, or never; 2 minutes by default) the screen saver comes on, whichever screen is showing: the dashboards, your chats, All chats, Memory, Settings or Logs. It's one clock for all of them, so moving between screens doesn't start the wait again. Typing, talking to her (listening, thinking, speaking) and her still answering in a chat count as someone being there, so it never comes on mid-answer. **A touch on the screen saver always shows the dashboard**, whatever screen it came from. Android's own screen saver shows the same.

- **Her clock:** the time, the date, the weather, how she feels and the last thing she said, on black. It drifts a few pixels each minute so it doesn't burn into the screen, and late at night (or when she's drained) it goes dim and warm and hides what she said.
- **Photo frame:** an album from Home Assistant's media: Immich's albums, people, tags and favorites (with Home Assistant's Immich integration), your My media folders, or any other media source with photos. Choose how long each photo shows (5 seconds to 5 minutes; a minute by default), the transition (fade, slide, slow zoom and fade, or none), shuffle, how photos fit the screen, and whether the time and her mood sit in a corner.

  **Fit:** **Smart** (the default) fills the screen when little would be cut off, and shows portrait photos, panoramas and other odd shapes whole on a blurred copy of themselves, so a portrait photo on a landscape tablet keeps everyone's heads. **Fill the screen** always fills it. **Whole photo** never crops. Whenever a photo is cropped, the crop is centred on the faces in it (found on the device by Android's own face finder), or on the upper third of a portrait photo when there are none, and the slow zoom moves in towards them.

  To use your Immich photos, add the **Immich** integration to Home Assistant (Settings → Devices & services → Add integration → Immich). Its albums, people, tags and favorites then appear in Home Assistant's media, and **Album** lists them. **Her dim clock at night** (on by default) swaps in the night clock from 10 PM to 6 AM and whenever she's drained, so it doesn't light up the room. Immich photos come at its screen-sized "preview" quality, and the album is read again every half hour so new photos join in.

On the wall display, the screen goes to 40% brightness for photos and 2% for the night clock. **A touch wakes it**, and so does a **motion or presence sensor** you name (checked every few seconds while idle), or Lena being worried.

**As the screen saver** over the lock screen it shows her mood but not the reasons, nor anything she said. Tapping it opens Lena listening (after you unlock, unless **On the lock screen** is on).

### Wake word

**Settings → Wake word** lets Lena listen for her name. **Listens** chooses where: **Only on the wall display** (the dashboards and the screensaver), or **Whenever Lena is open** (the default), your chats too (it stops when you leave Lena, and while Open WebUI's own voice input uses the microphone). Nothing listens until you turn it on, and even then it only ever opens Lena when it hears her name; the wall display never starts listening by itself when it starts. Choose:

- **Hey Lena:** a [microWakeWord](https://github.com/kahrendt/microWakeWord) model by [Tater Totterson](https://github.com/TaterTotterson/Tater-Wake-Words), the same kind of model Home Assistant's voice satellites run, trained and measured at about one false start every ten hours of background sound. Two words are much harder to say by accident than one.
- **Lena** (the default for new setups): the same kind of model for just her name. Quicker to say, but ordinary speech and TV set it off more often, which is why **Fewer false starts** (below) is the default too.
- **Hey Jarvis:** [openWakeWord](https://github.com/dscripka/openWakeWord)'s ready-made model, free for personal, non-commercial use.
- **Okay Nabu**, **Hey Mycroft** and **Alexa:** Home Assistant's standard wake words, the microWakeWord models its Voice Preview Edition runs (Apache 2.0). An Echo speaker in the room will answer to Alexa too.
- **Hey Home Assistant:** Home Assistant's experimental model; it may start by mistake more often than the others.

**"Stop"**, like on Home Assistant Voice: say it while a timer or alarm rings to stop it, or, with the wake word on, while she's speaking to stop her mid-sentence. It's ESPHome's own stop model, always at its own setting.

The app computes exactly the sound features Home Assistant's voice devices do: its front end is a line-for-line port of TensorFlow Lite Micro's, checked bit for bit against the original on every build.

**How sure she must be:** **Balanced** uses each model's own setting. **Fewer false starts** (the default) needs her to be surer, for longer (the average over a longer stretch of sound has to pass a higher bar). **Easier to wake** lowers the bar, for a big room.

**Against false starts**, she decides the way ESPHome does on Home Assistant's voice devices:

- She can't be woken in the first second after she starts listening, while the sound features settle (that's when a false start is likeliest, for example as the wall display comes up after a restart), nor again until she's calmed down after the last time.
- ESPHome's own **voice check** (a small model that tells speech from other sound) has to agree someone is speaking, so music, a TV jingle or a dropped pan can't wake her. What it turns away is noted in Settings → Software version → Logs.
- Each time she wakes, Settings → Software version → Logs says how sure she was and what she needed, which helps when choosing the setting above.

### Timers and alarms

Lena keeps her own timers and alarms on the device. Ask her: "set a 10 minute pasta timer", "timer for an hour and a half", "wake me up at 6:45" (the morning, unless you say otherwise), "set an alarm for 7 pm", "how long is left?", "when is my alarm?", "cancel the pasta timer", "cancel all alarms". She understands these herself, in English, before anything is sent anywhere: they don't go to Open WebUI or Assist (in Assist mode they're still kept in her chat), they answer at once, and they work without a connection. A longer or less clear sentence goes on to her as usual.

They're set on Android's alarm clock, like a clock app's, so they ring on time while the tablet sleeps, and they're set again after a restart (one missed by less than five minutes while it was off rings when it's back). When one rings, the alarm sound plays at the alarm volume and a full-screen **Stop** appears (over the lock screen too), with **Snooze** for 10 minutes on an alarm; saying "stop" works on that screen. It stops by itself after 10 minutes. The next one counts down on the dashboards' bar and on the screensaver; tap it, or open Settings → Device customization → **Alarms & timers**, to see them all, cancel one, or set a quick timer or an alarm by hand.

**When the conversation's over she goes back by herself** to the dashboard (dimmed, if it was), or to the screen saver: when you say nothing after her answer, when she's answered and isn't listening for more, or straight away when you say something like "thanks", "that's all", "never mind" or "stop" on its own. Touch the screen and she stays until you close her. Then the wake word is listening again.

It runs entirely on the device: the sound is scored a few times a second and thrown away, and nothing is recorded or sent until she hears her name; then the microphone is handed to Lena as if you'd tapped. **Try it** shows how close each try gets, so you can check it hears you from where you'll stand. It only listens while the wall display or the screen saver is showing (or, set to listen whenever Lena is open, while Lena is on screen), and Android's microphone indicator stays on while it does. Some phones don't let an app use the microphone from the screen saver; when that happens it says so and you tap to talk.

### Rooms, music and announcements

With the Lena integration, the app registers itself in Home Assistant as a display: a device under Lena, named after the tablet. Put that device in a room (its **Area**, in Home Assistant) and Lena knows where she is, like a Home Assistant Voice satellite. Settings → Connections → Home Assistant shows **This display's room**.

- **Room-aware commands:** in Assist mode every question says which display it came from, so "turn on the lights" means that room's. With Open WebUI, she's told the room.
- **Music:** asked for without naming a room, it plays on the room's speaker (Music Assistant's first), or on the one picked in **Music plays on**. Name another room, or the whole home, and she finds the speakers there.
- **Announcements:** Home Assistant's `lena.announce` action is said on the wall display within a few seconds, in the sheet like one of her answers, and she listens for an answer if it asks. During a conversation it waits until the conversation ends.

## House alerts

When something in your house worries her (a leak, smoke, a door left open: the sensors you chose in the Lena integration), she can tell you on your phone, and tapping the alert opens Lena listening so you can ask what's going on.

- **Instant, in her words:** in Home Assistant, Lena integration → Configure → **Tell these phones when she's worried**, pick your phones (they need the Home Assistant companion app), and choose your Lena agent under **Write alerts in her words with**. Each alert is written by her, and an "all clear" replaces it when the sensor clears.
- **Backup on this phone:** Settings → Notifications → **Alerts on this phone**. The app checks every 15 minutes, the shortest Android allows an app without Google's push service, so an alert can arrive up to 15 minutes late and uses the plain reason ("Leak sensor is detecting water."). Use it on phones without the companion app; on phones with both, you'd get each alert twice.

## Share to Lena

Share text or a link from any app and pick **Ask Lena**. It goes into the sheet's text box so you can add what you want ("Summarise this", "Is this right?") before sending. Nothing is sent until you tap send.

## No voice calls

Lena has no voice calls: talking to her is the Ask sheet (the assistant gesture, the tile, the widget, the wake word), which listens, answers aloud and listens again. Open WebUI's own call button is hidden inside the app too. To hide it in the browser as well, an Open WebUI admin can turn off the **Call** permission for users (Admin Panel → Users → Groups, or the default permissions).

## Quick Settings tile

Settings → Widgets & tile → **Add the Quick Settings tile**. Tapping **Talk to Lena** in the pull-down shade opens the sheet, listening, the same as the assistant gesture (including its lock-screen setting).

## Home screen widget

Settings → Widgets & tile → **Add the widget**, or add it from your launcher's widget list. It has **Talk**, **Type** and **New chat** buttons, and your first three quick prompts, which open the sheet and send straight away. It uses your wallpaper's colours.

Turn on **Her mood on the widget** (Settings → Widgets & tile, needs Home Assistant) to show how she feels under her name. It updates every half hour and after you talk to her. It shows only her mood, never the reasons, since anyone can see your home screen.

## Quick prompts

Settings → Quick prompts. Up to six. They appear as chips in the sheet when typing; the first three also appear on the widget and on the app icon's long-press menu.

## "Lena finished" notifications

If you leave Lena (or close the sheet) while she's still answering, Lena keeps an eye on the chat and notifies you with the start of the reply when it's done. Tap it to open the chat. It's off on a new setup: turn it on with **Reply notifications** in Settings → Notifications.

Lena watches for up to about nine minutes. Android may delay the check a little to save battery.

## Scheduled prompts

Settings → Scheduled prompts lists them by **name**, each with when it runs and an on/off switch. Tap one to open it:

| Setting | What it does |
|---|---|
| **Name** | What it's called in the list and on its notification, such as "Morning briefing". |
| **What she's asked** | The prompt. |
| **Show the prompt in the chat** | Off (the default): the chat shows just the prompt's name ("Morning briefing") and her answer, and the prompt itself goes to her with that one request, unsaved. On: the chat shows the whole prompt, like any question. |
| **Time** and **Repeats** | Every day, weekdays, weekends, on the days you pick, every few hours (1 to 12, from the time until midnight), once a month (on a day you pick; the 29th to 31st mean the month's last day in shorter months) or once (on a date). The next run is shown under **On**. |
| **Chat** | Every run goes into the chat with this name, made the first time: **Assistant Lena** (the one talking to her uses) unless you change it. Give several prompts the same chat to keep them in one history, such as a morning and an evening briefing in "Daily briefings". Empty: a new chat of its own each time. |
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

**Settings → Software version → Logs** shows what's been going on: **Problems** (warnings and errors), **Everything** (what she was doing too), and **Android** (Android's own log for Lena's current run, for crashes inside the web pages). Tap an entry for the details, such as where a crash happened. Search, copy, share or clear it from the top bar. The Logs row in settings says how many problems there were in the last day.

## Updates

**Settings → Software version → Updates** checks your GitHub repository's latest release, downloads it, checks it, and installs it, with a token for a private repository. **Re-download and install** gets the latest release again even if you already have it, to repair an install or after the version number was reset. See [Installation → Updating](Installation.md#updating).

## Signing in by herself

Open WebUI can sign out every device when it restarts (it does whenever its secret key changes), which would leave a wall tablet on its sign-in page and scheduled prompts failing. **Settings → Open WebUI → Sign in by herself** fixes that: choose **Email and password** or **LDAP**, enter them once, and **Test**. From then on, whenever Open WebUI ends her session, Lena signs in again by herself, in the app's chat page, the Ask screen, the wall display, scheduled prompts and alerts alike, and carries on where it was. A wrong password is tried at most once every half minute.

The details are stored encrypted with a key that never leaves the phone (Android's keystore) and sent only to your Open WebUI's sign-in. It can't do single sign-on (Authentik, Google and the like). The better fix, if you run the server, is to give Open WebUI a fixed `WEBUI_SECRET_KEY`, so restarts don't sign anyone out; this setting then just covers the rare time it does.

## Permissions

| Permission | Why |
|---|---|
| Internet | To reach your Open WebUI (and your Home Assistant, if you add it). |
| Microphone | Talking to Lena, Open WebUI's voice input and the wake word. Asked the first time. In the main app, only your Open WebUI address can use it. |
| Notifications | "Lena finished", scheduled prompts and house alerts. Asked the first time you open Lena. |
| Run at startup | Sets your scheduled prompts again after the phone restarts. |
| Change audio settings | Needed by Android's web view to use the microphone. It never prompts. |
| Install apps | Installing an update you downloaded in Settings → Software version → Updates. You allow it in Android's settings, and every install asks you to confirm. |

WorkManager, the Jetpack library that runs the background checks, also adds its own no-prompt permissions for network status and keeping the phone awake during a check. Lena never asks for camera, location, contacts or storage.
