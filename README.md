# Disclosure

This project was primarily created for personal use and for my family. While I may provide updates, they are not guaranteed. However, if you've stumbled upon this and find it interesting, feel free to download and use it. I had a great time building this and hope you enjoy it.

Note: Built with the assistance of Claude.ai. I'm putting it here now so you don't have to hunt for it below.

# Lena

A native Android app for your own Open WebUI. Your real Open WebUI, with all your tools, inside a proper Android shell, plus the phone-level extras a website can't do.

- **feels like an app**: a native top bar and side drawer with your chats, search and model picker, in your wallpaper's Material You colours
- **your phone's assistant**: the assistant gesture opens the **Ask Lena** sheet over whatever you're doing, or straight into a voice call
- **voice calls**: one tap from the top bar, the Quick Settings tile, the widget or the app icon
- **share to Lena** from any app; the text lands in the Ask sheet for you to add a question
- **"Lena finished" notifications** when you leave mid-answer
- **scheduled prompts**: "every morning at 7, give me my briefing", answered and saved as a chat
- **home screen widget** and **quick prompts** on the app icon's long-press menu
- **your tools, as they are**: chats are answered on your server with your model's tools, and everything Lena starts shows up in your Open WebUI history
- **locked down**: talks only to your server (and your Authentik, if you use it), no analytics, no backups of your sign-in

## Install

**Needs:** Android 16 or newer, and Open WebUI reachable over **HTTPS** with server-side speech-to-text and text-to-speech. See [Open WebUI Setup](docs/Open-WebUI-Setup.md).

1. **Get the APK.** Download `lena-<version>.apk` from the [latest release](https://github.com/soullink9/lena-android/releases/latest) and install it.
2. **Connect.** Enter your `https://` Open WebUI address, plus your Authentik (or other sign-in) address if you use one. Sign in once.
3. **Make it your assistant.** Settings → Apps → Default apps → **Digital assistant app** → Lena.

Building your own copy, with your own signing key, is covered in [Installation](docs/Installation.md).

## Documentation

| Page | What's in it |
|---|---|
| [Installation](docs/Installation.md) | Your signing key, building with GitHub Actions, installing, first run and updating. |
| [Open WebUI Setup](docs/Open-WebUI-Setup.md) | HTTPS, speech engines, models and tools, and sign-in. |
| [How It Works](docs/How-It-Works.md) | The shell, the Ask sheet, sharing, the tile, the widget, notifications and scheduled prompts. |
| [Privacy and Security](docs/Privacy-and-Security.md) | What the app stores, what it sends, and what it blocks. |
| [Troubleshooting](docs/Troubleshooting.md) | Sign-in, mic and voice, notifications, the header coming back, updates that won't install. |
| [Development](docs/Development.md) | The file map, versions, building locally and making a release. |

## Privacy

Lena talks only to the Open WebUI address you enter, plus your sign-in page if you add one. It reuses the sign-in you made inside the app for its native features and sends it nowhere else. No analytics, no Google Safe Browsing lookups or web view usage reports, and nothing in Google backups or phone-to-phone transfers. See [Privacy and Security](docs/Privacy-and-Security.md).

## Credits

Code from other people in this project:
- The app is built with Google's open-source [Android Jetpack](https://developer.android.com/jetpack) libraries (Compose, Material 3, WebKit, WorkManager and Glance) and JetBrains' [kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines), all Apache License 2.0.
- The glyphs in `res/drawable` (microphone, chat, add-comment and sliders) are from Google's [Material Icons](https://github.com/google/material-design-icons) (Apache License 2.0).

Everything else was written for this project, with help from Claude, Anthropic's AI model. Lena is an independent client and isn't affiliated with Open WebUI.

## License

[GNU General Public License v3.0](LICENSE). You may use, study, share and change Lena; if you share a changed version, share its source under the same licence.
