# Changelog

## 2.0.2 (2026-10-27)

- Open WebUI follows your phone's dark mode again, including when you switch it while Lena is open (you stay on the same chat).

## 2.0.1 (2026-10-27)

- Fixed the main screen showing blank white instead of Open WebUI.

## 2.0.0 (2026-10-27)

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

## 1.1.0 (2026-10-27)

- Single sign-on: a new **Sign-in address** setting lets Authentik (or another sign-in page) load inside Lena, so sign-in finishes in the app instead of your browser. Sign-in pages never get the microphone.

## 1.0.0 (2026-10-27)

First release.

- Open WebUI full screen, locked to one `https://` server
- Selectable as the phone's digital assistant; the gesture opens a new chat in call mode on a chosen model
- Call and Settings shortcuts
- Microphone for your server only; Android file picker for attachments
- No libraries, no analytics; Safe Browsing lookups, web view usage reports and backups turned off
