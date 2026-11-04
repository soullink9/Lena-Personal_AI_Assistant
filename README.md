# Disclosure

This project was primarily created for personal use and for my family. While I may provide updates, they are not guaranteed. However, if you've stumbled upon this and find it interesting, feel free to download and use it. I had a great time building this and hope you enjoy it.

Note: Built with the assistance of Claude.ai. I'm putting it here now so you don't have to hunt for it below.

<p align="center"><img src="docs/assets/lena-logo.png" width="200" alt="Lena's logo"></p>

<p align="center"><img src="home-assistant/custom_components/lena/brand/dark_logo.png" width="200" alt="Lena's logo"></p>

# Lena: a personal AI assistant on your own hardware

> **v0.6.6.** Everything works day to day; settings may still change before 1.0. See the [changelog](CHANGELOG.md).

Lena is a Jarvis-style assistant that runs entirely on your own servers. She's one character everywhere you meet her: in Home Assistant's voice assistant, in Open WebUI, and in an Android app that makes her your phone's assistant. She's sharp, sassy and quietly caring. She runs your home, uses your Open WebUI tools, remembers what you tell her, and has feelings that change for real reasons.

- **One personality, everywhere.** Her whole character is built into her Ollama model, so she's the same Lena in every app, whatever instructions the app adds.
- **Feelings with reasons.** Home Assistant gives her a mood, an energy level and short-lived reactions that change with her day, the weather, people coming and going, worries in the house and how she's treated. Only how she feels right now reaches her, and she knows why.
- **Voice control for your home.** Short spoken replies, honest reporting of what actually happened, and real help beyond switching lights.
- **One shared memory.** Tell her something in the kitchen and she knows it on your phone.
- **Your phone's assistant.** The **Lena** Android app (Android 16) puts your Open WebUI, with all its tools, in a native shell. The assistant gesture opens her already listening, she answers out loud, and it's one ongoing conversation, "Assistant Lena".
- **A Home Assistant dashboard and photo frame for the wall.** On a tablet, the app becomes an always-on Home Assistant dashboard (it can be the tablet's home screen) that turns into a photo frame when no one's around, and you call her by name ("Hey Lena") from across the room. The photos come from any album in Home Assistant's media: add the **Immich** integration to Home Assistant and your Immich albums, people and favorites show up there, ready to pick.
- **Your own voice stack.** Piper and Whisper speak and listen for Home Assistant, and a small bridge lets Open WebUI and the app use the same voices.
- **Nothing leaves your network.** No cloud model, no analytics, no third-party apps holding your data.

## What's in this repository

| Folder | What it is |
|---|---|
| [`ollama/`](ollama) | Her character and model settings (`Modelfile`), and an example Ollama setup that keeps her loaded |
| [`home-assistant/`](home-assistant) | The **Lena** integration: her feelings, shared memory and tools, in one folder |
| [`open-webui/`](open-webui) | A filter and a tool that bring her feelings and memory into Open WebUI and the app |
| [`android/`](android) | The **Lena** app for Android 16, built and signed by GitHub Actions |
| [`voice/`](voice) | An example Piper container, and the bridge that gives Open WebUI your Piper and Whisper voices |
| [`tools/`](tools) | `check.py`, offline checks to run before committing |
| [`docs/`](docs) | Everything in detail (see below) |

## Get started

**[Getting started](docs/Getting-Started.md)** walks through it in four parts, each with a quick test:

1. **Her model:** build `lena` in Ollama from the Modelfile.
2. **Open WebUI:** chat with her in the browser.
3. **The app:** install the APK from the [latest release](../../releases/latest) and make her your phone's assistant.
4. **Home Assistant:** her voice in your house, plus her feelings and shared memory everywhere.

You can stop after any part. You'll need a machine running [Ollama](https://ollama.com/) with a GPU that fits a 12B model (16 GB of VRAM is comfortable).

## Documentation

Start with the [Overview](docs/Overview.md): how the pieces connect and which part does what.

| Area | Pages |
|---|---|
| **Setup** | [Getting started](docs/Getting-Started.md) · [Lena in Open WebUI](docs/open-webui/Setup.md) · [Home Assistant installation](docs/home-assistant/Installation.md) · [App installation](docs/app/Installation.md) |
| **Her personality** | [Personality](docs/personality/Personality.md) · [Feelings](docs/personality/Moods.md) · [Memory](docs/home-assistant/Memory.md) |
| **Home Assistant** | [The Lena integration](docs/home-assistant/Integration.md) · [Performance tuning](docs/home-assistant/Performance-Tuning.md) · [Troubleshooting](docs/home-assistant/Troubleshooting.md) |
| **Android app** | [Open WebUI setup](docs/app/Open-WebUI-Setup.md) · [How it works](docs/app/How-It-Works.md) · [Privacy and security](docs/app/Privacy-and-Security.md) · [Troubleshooting](docs/app/Troubleshooting.md) |
| **Voice** | [Piper](docs/voice/Piper.md) · [Voice bridge](docs/voice/Voice-Bridge.md) |
| **Development** | [Architecture](ARCHITECTURE.md) · [Repository guide](docs/Development.md) · [App development](docs/app/Development.md) · [Changelog](CHANGELOG.md) · [Contributing](CONTRIBUTING.md) |

## Privacy

Everything runs on your own hardware. The model is on your Ollama server, her feelings and memory are in your Home Assistant, Open WebUI talks only to your Home Assistant to fetch them, and the app talks only to your Open WebUI address, your sign-in page and, if you add it, your Home Assistant, with no analytics and no backups of your sign-in. Her memory is shared with everyone who talks to her and added to her prompts, so don't store passwords, door codes or similar secrets in it. See [App privacy and security](docs/app/Privacy-and-Security.md) and [Memory](docs/home-assistant/Memory.md).

## Contributing

Issues and suggestions are welcome, with the caveat from the disclosure: updates aren't guaranteed. See [CONTRIBUTING.md](CONTRIBUTING.md).

## Credits

This project builds on, and wouldn't exist without:

- [Home Assistant](https://www.home-assistant.io/) and its Assist, Ollama and Wyoming integrations.
- [Ollama](https://ollama.com/) for running the model locally. The base model named in the Modelfile is Google's Gemma 4; other choices are listed in the file.
- [Open WebUI](https://openwebui.com/), which the app wraps and talks to. Lena is an independent client and isn't affiliated with Open WebUI.
- [Piper](https://docs.linuxserver.io/images/docker-piper/) text-to-speech and [Whisper](https://github.com/openai/whisper) speech-to-text, packaged as containers by [LinuxServer.io](https://www.linuxserver.io/), and the [Wyoming protocol](https://github.com/OHF-Voice/wyoming) the bridge speaks to them.
- [FFmpeg](https://ffmpeg.org/), which the voice bridge uses to convert audio.
- Google's open-source [Android Jetpack](https://developer.android.com/jetpack) libraries (Compose, Material 3, WebKit, WorkManager and Glance) and JetBrains' [kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines), all Apache License 2.0.
- [microWakeWord](https://github.com/kahrendt/microWakeWord) and Tater Totterson's ["Hey Lena"](https://github.com/TaterTotterson/Tater-Wake-Words) and ["Lena"](https://github.com/TaterTotterson/microWakeWords) wake word models (MIT); ESPHome's voice activity model from the [Open Home Foundation's model collection](https://github.com/esphome/micro-wake-word-models) (Apache 2.0); TensorFlow Lite Micro's audio front end (Apache 2.0) and [KISS FFT](https://github.com/mborgerding/kissfft) (BSD), ported to the app.
- [openWakeWord](https://github.com/dscripka/openWakeWord) for the alternative "Hey Jarvis" wake word (code Apache 2.0; the wake word model CC BY-NC-SA 4.0, for non-commercial use), run with Google's [LiteRT](https://ai.google.dev/edge/litert) (Apache 2.0).
- Glyphs from Google's [Material Icons](https://github.com/google/material-design-icons) (Apache License 2.0).
- [curl](https://curl.se/), used by the Ollama watchdog example.
- The idea of a Jarvis-style assistant comes from the J.A.R.V.I.S. of Marvel's *Iron Man*. Lena is an original character and this project isn't affiliated with Marvel.

No code was copied from another project; everything else was written for this one, with help from Claude, Anthropic's AI model.

## License

[GNU General Public License v3.0](LICENSE). You may use, study, share and change Lena; if you share a changed version, share its source under the same licence. Third-party models and code included in the repository keep their own licences: see [Third-party notices](THIRD_PARTY_NOTICES.md).
