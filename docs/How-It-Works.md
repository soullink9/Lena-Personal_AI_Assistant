# How It Works

[← Back to README](../README.md)

Lena is two things working together:

- **Your real Open WebUI page** for chatting, inside Android's built-in web view (the same engine as Chrome, updated through the Play Store). Everything Open WebUI does, including your tools, works as it does in a browser.
- **A native Android shell** around it, plus features that talk to Open WebUI's API directly: the drawer's chat list, the Ask sheet, notifications and scheduled prompts. They use the sign-in you already made inside Lena; there's no API key to set up.

## The main screen

**Top bar**
- **☰** opens the drawer.
- **The title** is the current chat's model. Tap it to start a new chat with another model.
- **New chat** and **Voice call** on the right.

**Drawer**
- **New chat**, **Temporary chat** (one Open WebUI doesn't save) and **Voice call**.
- **Search** and your **recent chats**, grouped by day. A spinner marks a chat that's still being answered. **Long-press** a chat to rename or delete it (deleting removes it from Open WebUI too).
- **Server status**: whether your server is reachable, and its Open WebUI version.
- **Open WebUI menus**: shows Open WebUI's own header again, for things the shell doesn't cover, such as chat controls, switching models mid-chat, your Open WebUI settings and signing out.
- **Reload** and **Settings**.

Lena hides Open WebUI's own header and sidebar because the top bar and drawer replace them. If an Open WebUI update changes its page, the header may simply reappear until Lena catches up; nothing breaks.

**New chats** use your chosen model (Settings → Chats), unless you pick another from the title.

**Back** goes back within Open WebUI, then closes Lena to the background.

## Talking to Lena

The assistant gesture opens a sheet over whatever you're doing, already listening, like Google Assistant or Home Assistant's Assist.

1. **Talk.** Your words appear as you speak. When you stop, Lena sends them. Tap the mic to send early.
2. **Lena answers** on your server with your model's tools, shows the reply as it's written, then **reads it aloud**. Tap the button while she's talking to stop her.
3. **Then she listens again**, so you can carry on the conversation without tapping. If you say nothing, the mic closes quietly. Tap the mic any time to talk.

**One ongoing conversation.** Every time you talk to Lena, she continues the same chat, titled **Lena** and pinned in Open WebUI, so she remembers what you talked about before, even days later. The sheet shows your last exchange or two when it opens.

- **New conversation** (the speech-bubble button in the sheet) starts over. The old chat stays in your history.
- Delete the **Lena** chat (from the drawer or Open WebUI) and the next time you talk, she starts a fresh one.
- A conversation that never ends would outgrow any model, so Lena gives it **half of the model's context** (set **Lena's context size** in settings; 32,768 by default), filled from the newest exchange back. For short spoken exchanges that's usually 50 to 80 of them. The other half stays free for her system prompt, tools, tool results and reply. The chat itself keeps everything, and **Continue in Lena** opens it in the full app.
- Prefer a new chat every time? Turn off **Settings → Talking to Lena → One ongoing conversation**.

- The **keyboard** button switches to typing (and the mic button back). Lena only reads replies aloud when you spoke.
- The **waveform** button switches to a full voice call, and the **expand** button opens the full app.
- Your **quick prompts** appear as chips when typing.
- Tap outside the sheet to close it. If Lena is still answering, you get a notification when she's done.

**Settings → Talking to Lena** chooses who hears you and who speaks:

| Setting | Choices |
|---|---|
| Hearing you | **On this phone** (default): Android's on-device speech recognition, which shows your words as you speak, and the audio never leaves the phone. **Your server**: Open WebUI's speech-to-text, the same as call mode. |
| Lena's voice | **Your server's voice** (default): Open WebUI's text-to-speech, the same voice as call mode. **The phone's voice**: Android's text-to-speech. **Off**. |
| Keep listening | **After every reply** (default), **Only when Lena asks a question** (like Home Assistant's "continue conversation"), or **Off**. |
| One ongoing conversation | On by default. Off starts a new chat each time you open the sheet. |
| Lena's context size | Your model's context length in tokens (32,768 by default). Half of it holds the ongoing conversation. |

If the phone's speech model isn't installed yet, Android starts downloading it and Lena uses your server meanwhile. If the phone's recognizer can't listen for any other reason, Lena uses your server for that turn. If your server's voice isn't set up, Lena uses the phone's.

Other ways in: **Talk to Lena** on the widget and the app icon's long-press menu, and **Type to Lena** for the keyboard version.

## Share to Lena

Share text or a link from any app and pick **Ask Lena**. It goes into the sheet's text box so you can add what you want ("Summarise this", "Is this right?") before sending. Nothing is sent until you tap send.

## Voice calls

Start one from the top bar, the drawer, the Quick Settings tile, the widget, the app icon's long-press menu, or the assistant gesture. Calls use Open WebUI's own call mode.

- The screen stays on during a call.
- The first call asks for microphone permission.
- If you leave Lena mid-call, Android cuts the app off from the microphone until you come back.

## Quick Settings tile

Settings → On your phone → **Add Quick Settings tile**. Tapping **Call Lena** in the pull-down shade starts a call (after unlocking).

## Home screen widget

Settings → On your phone → **Add home screen widget**, or add it from your launcher's widget list. It has **Talk**, **Voice call**, **Type** and **New chat** buttons, and your first three quick prompts, which open the sheet and send straight away. It uses your wallpaper's colours.

## Quick prompts

Settings → Quick prompts. Up to six. They appear as chips in the sheet when typing; the first three also appear on the widget and on the app icon's long-press menu.

## "Lena finished" notifications

If you leave Lena (or close the sheet) while she's still answering, Lena keeps an eye on the chat and notifies you with the start of the reply when it's done. Tap it to open the chat. Turn it off in Settings → Notifications.

Lena watches for up to about nine minutes. Android may delay the check a little to save battery.

## Scheduled prompts

Settings → Scheduled prompts. Each has a time, a prompt and an on/off switch, and runs every day.

At that time Lena sends the prompt to your chosen model, waits for the answer and notifies you with it. The conversation is saved in your Open WebUI history. Android runs these alarms within a few minutes of the time to save battery, and after a restart Lena sets them again.

## Links

- Pages on your Open WebUI address open inside Lena.
- Your sign-in addresses (such as Authentik), if you've added any, also open inside Lena so sign-in can finish.
- `http://` and `https://` links to anywhere else (citations, web search results and so on) open in your normal browser.
- Everything else, such as `intent:` and `file:` links, is blocked.

## Attachments

Open WebUI's attach button opens Android's file picker. Lena has no camera permission; photos come from the picker.

## Permissions

| Permission | Why |
|---|---|
| Internet | To reach your Open WebUI. |
| Microphone | Talking to Lena, call mode and voice input. Asked the first time. In the main app, only your Open WebUI address can use it. |
| Notifications | "Lena finished" and scheduled prompts. Asked the first time you open Lena. |
| Run at startup | Sets your scheduled prompts again after the phone restarts. |
| Change audio settings | Needed by Android's web view to use the microphone. It never prompts. |

WorkManager, the Jetpack library that runs the background checks, also adds its own no-prompt permissions for network status and keeping the phone awake during a check. Lena never asks for camera, location, contacts or storage.
