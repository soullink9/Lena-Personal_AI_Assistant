# Disclosure

This project was primarily created for personal use and for my family. While I may provide updates, they are not guaranteed. However, if you've stumbled upon this and find it interesting, feel free to download and use it. I had a great time building this and hope you enjoy it.

Note: Built with the assistance of Claude.ai. I'm putting it here now so you don't have to hunt for it below.

# Lena

A tiny Android app for your own Open WebUI. Make it your phone's assistant, and one gesture drops you straight into a voice call with your local model, with all your tools.

- **one server only**: it opens your Open WebUI and nothing else; other links go to your normal browser
- **your phone's assistant**: long-press power or home (or swipe from a corner) to start a fresh chat
- **straight into a call**: the assistant gesture opens Open WebUI's call mode on the model you pick
- **Call shortcut**: long-press the icon, or drag Call onto your home screen for one-tap calls
- **your tools, as they are**: it's your real Open WebUI, so every tool, knowledge base and setting just works
- **attachments**: photos and files through Android's own picker
- **light and dark**: follows your phone's theme
- **nothing extra**: three Java files, no libraries, no analytics, two permissions (internet and microphone)

## Install

**Needs:** Android 11 or newer, and Open WebUI reachable over **HTTPS** with server-side speech-to-text and text-to-speech. See [Open WebUI Setup](docs/Open-WebUI-Setup.md).

1. **Get the APK.** Download `lena-<version>.apk` from the [latest release](https://github.com/soullink9/lena-android/releases/latest) and install it. Your browser will ask to allow installs the first time.
2. **Connect.** Enter your `https://` Open WebUI address and, if you like, the model to use for new chats. Sign in once.
3. **Make it your assistant.** Settings → Apps → Default apps → **Digital assistant app** → Lena. The button in Lena's settings takes you there.

Building your own copy, with your own signing key, is covered in [Installation](docs/Installation.md).

## Documentation

| Page | What's in it |
|---|---|
| [Installation](docs/Installation.md) | Your signing key, building with GitHub Actions, installing, first run and updating. |
| [Open WebUI Setup](docs/Open-WebUI-Setup.md) | HTTPS, speech engines, picking a model and sign-in. |
| [How It Works](docs/How-It-Works.md) | The assistant gesture, call mode, shortcuts, links, back button and permissions. |
| [Privacy and Security](docs/Privacy-and-Security.md) | What the app stores, what it sends, and what it blocks. |
| [Troubleshooting](docs/Troubleshooting.md) | No mic, no voice, certificate errors, updates that won't install. |
| [Development](docs/Development.md) | The file map, building locally and making a release. |

## Privacy

Lena talks to one place: the Open WebUI address you enter. It has no analytics, turns off Google Safe Browsing lookups and WebView usage reports, and keeps your sign-in out of Google backups and phone-to-phone transfers. See [Privacy and Security](docs/Privacy-and-Security.md).

## Credits

Code from other people in this project:
- `ic_shortcut_call.xml` and `ic_shortcut_settings.xml`: the microphone and sliders glyphs are from Google's [Material Icons](https://github.com/google/material-design-icons) (Apache License 2.0).

Everything else was written for this project, with help from Claude, Anthropic's AI model. Lena is an independent client and isn't affiliated with Open WebUI.

## License

[GNU General Public License v3.0](LICENSE). You may use, study, share and change Lena; if you share a changed version, share its source under the same licence.
