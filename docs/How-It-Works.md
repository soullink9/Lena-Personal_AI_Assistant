# How It Works

[← Back to README](../README.md)

Lena is one screen showing your Open WebUI, inside Android's built-in web view (the same engine as Chrome, updated through the Play Store). Everything you see is your server's page; Lena adds the ways to open it and the locks around it.

## Ways to open Lena

| How | What opens |
|---|---|
| **Assistant gesture** | A new chat on your chosen model. With **Assistant gesture starts a voice call** on (the default), it opens straight into call mode. |
| **Call shortcut** | A new chat on your chosen model, straight into call mode. Long-press Lena's icon to find it, or drag it to your home screen. |
| **App icon** | Wherever you left off. If Lena wasn't running, your Open WebUI home page. |
| **Settings shortcut** | Lena's settings. Long-press Lena's icon to find it. |

The assistant gesture and the Call shortcut always start fresh, even if Lena is already open on another chat.

Under the hood, Lena opens your address with Open WebUI's `?model=<id>` and `?call=true` options.

## During a call

- The screen stays on while a call is open, so the phone doesn't lock mid-sentence.
- The first call asks for microphone permission. After that, calls start right away.
- If you leave Lena mid-call, Android cuts the app off from the microphone until you come back.

## Links

- Pages on your Open WebUI address open inside Lena.
- `http://` and `https://` links to anywhere else (citations, web search results and so on) open in your normal browser.
- Everything else, such as `intent:` and `file:` links, is blocked.
- Previews that Open WebUI draws in frames from its own content still work.

## Back button

Back goes back within Open WebUI. When there's nothing left to go back to, Lena moves to the background and stays ready for next time.

## Attachments

Open WebUI's attach button opens Android's file picker, so you can send photos and files from your phone. Lena doesn't take photos itself; it has no camera permission.

## Permissions

| Permission | Why |
|---|---|
| Internet | To reach your Open WebUI. |
| Microphone | For call mode and voice input. Asked the first time you use it, and only granted to your Open WebUI address. |
| Change audio settings | Needed by Android's web view to use the microphone. It never prompts. |

Lena never asks for camera, location, contacts or storage.

## The lock screen

If you trigger the assistant from the lock screen, Android asks you to unlock first. Lena never shows over the lock screen, so nobody can read your chats without unlocking.
