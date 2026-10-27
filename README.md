# Disclosure

This project was primarily created for personal use and for my family. While I may provide updates, they are not guaranteed. However, if you've stumbled upon this and find it interesting, feel free to download and use it. I had a great time building this and hope you enjoy it.

Note: Built with the assistance of Claude.ai. I'm putting it here now so you don't have to hunt for it below.

# Lena: a personal AI assistant on your own hardware

Lena is a Jarvis-style assistant that runs entirely on your own servers. She's one character everywhere you meet her: in Home Assistant's voice assistant, in Open WebUI, and in an Android app that makes her your phone's assistant. She's sharp, dryly funny and quietly caring, she controls your home, uses your Open WebUI tools, remembers what you tell her, and her mood drifts through the day.

- **One personality, everywhere.** An Ollama `Modelfile` carries her character, so she's the same Lena in every app that uses the model.
- **Voice control for your home.** A Home Assistant prompt built for short spoken replies, honest reporting of what actually happened, and real help beyond switching lights.
- **Moods that change.** A Home Assistant blueprint moves her mood, energy, delivery and flavour through the day, and the prompt makes the difference audible.
- **Your phone's assistant.** The **Lena** Android app (Android 16) puts your Open WebUI, with all its tools, in a native shell. The assistant gesture opens a sheet that's already listening, she answers out loud, and it's one ongoing conversation she remembers.
- **Your own voice stack.** Piper and Whisper speak and listen for Home Assistant, and a small bridge lets Open WebUI and the app use the same voices.
- **Nothing leaves your network.** No cloud model, no analytics, no third-party apps holding your data.

## What's in this repository

| Folder | What it is |
|---|---|
| [`ollama/`](ollama) | Lena's character and model settings (`Modelfile`), and an example Ollama setup that keeps her loaded |
| [`home-assistant/`](home-assistant) | The voice assistant prompt, the mood blueprint and the memory sensor |
| [`android/`](android) | The **Lena** app for Android 16, built and signed by GitHub Actions |
| [`voice/`](voice) | An example Piper container, and the bridge that gives Open WebUI your Piper and Whisper voices |
| [`tools/`](tools) | `check.py`, offline checks to run before committing |
| [`docs/`](docs) | Everything in detail (see below) |

You don't need every part. The model plus Home Assistant gives you a talking house; the model plus Open WebUI plus the app gives you Lena on your phone.

## Get started

You need a machine running [Ollama](https://ollama.com/) with a GPU that fits a 9B-class model. Then add the parts you want:

1. **Build the model.** From the `ollama` folder: `ollama create lena -f Modelfile`. See [Home Assistant installation](docs/home-assistant/Installation.md#1-build-the-model).
2. **Home Assistant voice.** Create the helpers in the UI, add the blueprint and paste the prompt into your Ollama agent. See [Home Assistant installation](docs/home-assistant/Installation.md).
3. **The app.** Install `lena-<version>.apk` from the [latest release](../../releases/latest), enter your `https://` Open WebUI address, and make Lena your digital assistant app. See [App installation](docs/app/Installation.md).
4. **Voices for Open WebUI (optional).** Run the voice bridge next to your Piper and Whisper containers. See [Voice bridge](docs/voice/Voice-Bridge.md).

## Documentation

Start with the [Overview](docs/Overview.md): how the pieces connect and which part does what.

| Area | Pages |
|---|---|
| **Home Assistant** | [Installation](docs/home-assistant/Installation.md) · [Helpers](docs/home-assistant/Helpers.md) · [Prompt reference](docs/home-assistant/Prompt-Reference.md) · [Persona engine](docs/home-assistant/Persona-Engine.md) · [Memory](docs/home-assistant/Memory.md) · [Performance tuning](docs/home-assistant/Performance-Tuning.md) · [Troubleshooting](docs/home-assistant/Troubleshooting.md) |
| **Android app** | [Installation](docs/app/Installation.md) · [Open WebUI setup](docs/app/Open-WebUI-Setup.md) · [How it works](docs/app/How-It-Works.md) · [Privacy and security](docs/app/Privacy-and-Security.md) · [Troubleshooting](docs/app/Troubleshooting.md) |
| **Voice** | [Piper](docs/voice/Piper.md) · [Voice bridge](docs/voice/Voice-Bridge.md) |
| **Development** | [Repository guide](docs/Development.md) · [App development](docs/app/Development.md) · [Changelog](CHANGELOG.md) |

## Privacy

Everything runs on your own hardware. The model is on your Ollama server, her Home Assistant memory is a to-do list in your Home Assistant, and the app talks only to your Open WebUI address and your sign-in page, with no analytics and no backups of your sign-in. Her memory is added to her prompt, so don't store passwords, door codes or similar secrets in it. See [App privacy and security](docs/app/Privacy-and-Security.md) and [Memory](docs/home-assistant/Memory.md).

## Contributing

Issues and suggestions are welcome, with the caveat from the disclosure: updates aren't guaranteed. See [CONTRIBUTING.md](CONTRIBUTING.md).

## Credits

This project builds on, and wouldn't exist without:

- [Home Assistant](https://www.home-assistant.io/) and its Assist, Ollama, Wyoming and blueprint features.
- [Ollama](https://ollama.com/) for running the model locally. The base model named in the Modelfile is a Qwen model from Alibaba Cloud; other choices are listed in the file.
- [Open WebUI](https://openwebui.com/), which the app wraps and talks to. Lena is an independent client and isn't affiliated with Open WebUI.
- [Piper](https://docs.linuxserver.io/images/docker-piper/) text-to-speech and [Whisper](https://github.com/openai/whisper) speech-to-text, packaged as containers by [LinuxServer.io](https://www.linuxserver.io/), and the [Wyoming protocol](https://github.com/OHF-Voice/wyoming) the bridge speaks to them.
- [FFmpeg](https://ffmpeg.org/), which the voice bridge uses to convert audio.
- Google's open-source [Android Jetpack](https://developer.android.com/jetpack) libraries (Compose, Material 3, WebKit, WorkManager and Glance) and JetBrains' [kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines), all Apache License 2.0.
- Glyphs from Google's [Material Icons](https://github.com/google/material-design-icons) (Apache License 2.0).
- [curl](https://curl.se/), used by the Ollama watchdog example.
- The idea of a Jarvis-style assistant comes from the J.A.R.V.I.S. of Marvel's *Iron Man*. Lena is an original character and this project isn't affiliated with Marvel.

No code was copied from another project; everything else was written for this one, with help from Claude, Anthropic's AI model.

## License

[GNU General Public License v3.0](LICENSE). You may use, study, share and change Lena; if you share a changed version, share its source under the same licence.
