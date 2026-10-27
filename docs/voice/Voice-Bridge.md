# Voice Bridge

[← Back to README](../README.md)

Open WebUI can't use the Wyoming Piper and Whisper containers Home Assistant uses: Wyoming is Home Assistant's own protocol, and Open WebUI only talks to OpenAI-style speech servers. The voice bridge is a tiny container that sits between them, so Lena speaks with the **same Piper voice** in Open WebUI, call mode, the Lena app and Home Assistant.

It lives in [`voice-bridge/`](../voice-bridge): one Python file (standard library only), a Dockerfile and a compose file. It has no ports of its own on your host; only containers on the same Docker network can reach it.

## 1. Set it up

Copy the `voice-bridge` folder to your Docker host, then edit `compose.yaml`:

| Setting | What to put |
|---|---|
| `PIPER_HOST`, `PIPER_PORT` | Your Piper container's name on the network, and its Wyoming port (usually `10200`). |
| `PIPER_VOICE` | Optional. The voice Home Assistant uses, for example `en_US-lessac-medium`. You can also pick it in Open WebUI instead. |
| `WHISPER_HOST`, `WHISPER_PORT` | Optional. Your Whisper container and its Wyoming port (usually `10300`). Leave them out to keep Open WebUI's built-in Whisper. |
| `WHISPER_LANGUAGE` | Optional. For example `en`, so Whisper doesn't have to guess. |
| `API_KEY` | Optional. If set, Open WebUI must send the same key. |
| `networks` | The name of the Docker network Open WebUI and your Wyoming containers share. |

Then, in that folder:

```sh
docker compose up -d --build
docker logs lena-voice
```

The log's first line shows which Piper and Whisper it's using.

If Open WebUI, Piper and Whisper are on different networks, attach the bridge to each of them.

## 2. Point Open WebUI at it

**Admin Panel → Settings → Audio**:

**Text-to-Speech**
| Field | Value |
|---|---|
| Text-to-Speech Engine | **OpenAI** |
| API Base URL | `http://lena-voice:8000/v1` |
| API Key | Anything (for example `lena`), or your `API_KEY` |
| TTS Model | `piper` |
| TTS Voice | Pick your Piper voice from the list |

**Speech-to-Text** (only if you set `WHISPER_HOST`; otherwise keep **Whisper (Local)**)
| Field | Value |
|---|---|
| Speech-to-Text Engine | **OpenAI** |
| API Base URL | `http://lena-voice:8000/v1` |
| API Key | The same as above |
| STT Model | `whisper` |

Save, then try the speaker icon on any reply in Open WebUI. In the Lena app, **Settings → Talking to Lena → Lena's voice → Your server's voice** now uses Piper.

## How it works

| Open WebUI asks for | The bridge does |
|---|---|
| `POST /v1/audio/speech` | Sends the text to Piper over Wyoming and converts the audio to MP3 (or WAV, Opus, AAC or FLAC if asked) with ffmpeg. Open WebUI's default voice names, such as `alloy`, mean your default Piper voice. An unknown voice falls back to Piper's default. |
| `POST /v1/audio/transcriptions` | Converts your recording (WebM or Ogg from call mode, WAV from the Lena app, and others) to 16 kHz audio with ffmpeg and sends it to Whisper over Wyoming. |
| `GET /v1/audio/voices` | Lists the Piper voices you've installed, for Open WebUI's voice list. |
| `GET /health` | Answers `ok`, for Docker's health check. |

## Troubleshooting

- **`docker logs lena-voice` shows "Connection refused" or "Name does not resolve":** the bridge can't reach Piper or Whisper. Check the container names, ports and that they share a network.
- **The voice list in Open WebUI is empty:** same as above, or no voices are installed in your Piper container yet.
- **Open WebUI still uses a generic voice:** check the engine is **OpenAI** and the URL ends in `/v1`, then refresh the page.
