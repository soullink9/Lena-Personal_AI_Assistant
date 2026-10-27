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
- **Search** and your **recent chats**, grouped by day. A spinner marks a chat that's still being answered.
- **Server status**: whether your server is reachable, and its Open WebUI version.
- **Open WebUI menus**: shows Open WebUI's own header again, for things the shell doesn't cover, such as chat controls, switching models mid-chat, your Open WebUI settings and signing out.
- **Reload** and **Settings**.

Lena hides Open WebUI's own header and sidebar because the top bar and drawer replace them. If an Open WebUI update changes its page, the header may simply reappear until Lena catches up; nothing breaks.

**New chats** use your chosen model (Settings → Chats), unless you pick another from the title.

**Back** goes back within Open WebUI, then closes Lena to the background.

## Ask Lena sheet

A quick question over whatever app you're in. Open it with the assistant gesture (if that's what you chose in settings), the Ask button on the widget, the app icon's long-press menu, or by sharing text to Lena.

- Type a question, or tap one of your **quick prompts**.
- The answer is written on your server with your model's tools (the tools attached to that model in Open WebUI), and appears as it's written.
- **Continue in Lena** opens it as a normal chat. Every question you ask here is saved in your Open WebUI history, titled automatically.
- The **voice** button switches to a call, and the **expand** button opens the full app.
- Tap outside the sheet to close it. If Lena is still answering, you get a notification when she's done.

## The assistant gesture

In Settings → Assistant gesture, pick what it opens:

| Choice | What happens |
|---|---|
| **Ask Lena sheet** (default) | The sheet above, over your current app. |
| **Voice call** | The main app, in a new chat, straight into call mode. |
| **New chat** | The main app on a new chat. |

On the lock screen, Android asks you to unlock first.

## Share to Lena

Share text or a link from any app and pick **Ask Lena**. It goes into the Ask sheet's box so you can add what you want ("Summarise this", "Is this right?") before sending. Nothing is sent until you tap send.

## Voice calls

Start one from the top bar, the drawer, the Quick Settings tile, the widget, the app icon's long-press menu, or the assistant gesture. Calls use Open WebUI's own call mode.

- The screen stays on during a call.
- The first call asks for microphone permission.
- If you leave Lena mid-call, Android cuts the app off from the microphone until you come back.

## Quick Settings tile

Settings → On your phone → **Add Quick Settings tile**. Tapping **Call Lena** in the pull-down shade starts a call (after unlocking).

## Home screen widget

Settings → On your phone → **Add home screen widget**, or add it from your launcher's widget list. It has **Voice call**, **Ask** and **New chat** buttons, and your first three quick prompts, which open the Ask sheet and send straight away. It uses your wallpaper's colours.

## Quick prompts

Settings → Quick prompts. Up to six. They appear as chips in the Ask sheet; the first three also appear on the widget and on the app icon's long-press menu.

## "Lena finished" notifications

If you leave Lena (or close the Ask sheet) while she's still answering, Lena keeps an eye on the chat and notifies you with the start of the reply when it's done. Tap it to open the chat. Turn it off in Settings → Notifications.

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
| Microphone | Call mode and voice input. Asked the first time, and only ever granted to your Open WebUI address. |
| Notifications | "Lena finished" and scheduled prompts. Asked the first time you open Lena. |
| Run at startup | Sets your scheduled prompts again after the phone restarts. |
| Change audio settings | Needed by Android's web view to use the microphone. It never prompts. |

WorkManager, the Jetpack library that runs the background checks, also adds its own no-prompt permissions for network status and keeping the phone awake during a check. Lena never asks for camera, location, contacts or storage.
