# Voice Bridge

[← Back to README](../../README.md)

Open WebUI can't use the Wyoming Piper and Whisper containers Home Assistant uses: Wyoming is Home Assistant's own protocol, and Open WebUI only talks to OpenAI-style speech servers. The voice bridge is a tiny container that sits between them, so Lena speaks with the **same Piper voice** in Open WebUI, call mode, the Lena app and Home Assistant.

It lives in [`voice/bridge/`](../../voice/bridge): one Python file (standard library only), a Dockerfile and a compose file. It has no ports of its own on your host; only containers on the same Docker network can reach it, and only with its API key.

## 1. Set it up

Copy the `voice/bridge` folder to your Docker host. Next to `compose.yaml`, make an `.env` file with a key only Open WebUI will know:

```sh
echo "LENA_VOICE_API_KEY=$(openssl rand -hex 32)" > .env
```

Then edit `compose.yaml`:

| Setting | What to put |
|---|---|
| `PIPER_HOST`, `PIPER_PORT` | Your Piper container's name on the network, and its Wyoming port (usually `10200`). |
| `PIPER_VOICE` | Optional. The voice Home Assistant uses, for example `en_US-lessac-medium`. You can also pick it in Open WebUI instead. |
| `WHISPER_HOST`, `WHISPER_PORT` | Optional. Your Whisper container and its Wyoming port (usually `10300`). Leave them out to keep Open WebUI's built-in Whisper. |
| `WHISPER_LANGUAGE` | Optional. For example `en`, so Whisper doesn't have to guess. |
| `API_KEY` | Required. Comes from `LENA_VOICE_API_KEY` in `.env`; Open WebUI sends the same key. The bridge won't start without one. To run it without a key anyway, on a network only trusted containers can reach, set `ALLOW_NO_API_KEY: "1"` instead. |
| `MAX_RECORDING_SECONDS`, `MAX_JOBS` | Optional. How much of a recording is transcribed (default `300` seconds) and how many requests run at once (default `4`; more wait, then get "busy"). |
| `networks` | The name of the Docker network Open WebUI and your Wyoming containers share. The example uses `llm`, the network from `ollama/docker-compose.example.yml`. |

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
| API Key | Your key: the `LENA_VOICE_API_KEY` from `.env` |
| TTS Model | `piper` |
| TTS Voice | Pick your Piper voice from the list |

**Speech-to-Text** (only if you set `WHISPER_HOST`; otherwise keep **Whisper (Local)**)
| Field | Value |
|---|---|
| Speech-to-Text Engine | **OpenAI** |
| API Base URL | `http://lena-voice:8000/v1` |
| API Key | The same as above |
| STT Model | `whisper` |

Save, then try the speaker icon on any reply in Open WebUI. In the Lena app, **Settings → How she looks and sounds → Your server's voice** now uses Piper.

## How it works

| Open WebUI asks for | The bridge does |
|---|---|
| `POST /v1/audio/speech` | Sends the text to Piper over Wyoming and converts the audio to MP3 (or WAV, Opus, AAC or FLAC if asked) with ffmpeg. Open WebUI's default voice names, such as `alloy`, mean your default Piper voice. An unknown voice falls back to Piper's default. |
| `POST /v1/audio/transcriptions` | Converts your recording (WebM or Ogg from call mode, WAV from the Lena app, and others) to 16 kHz audio with ffmpeg and sends it to Whisper over Wyoming. |
| `GET /v1/audio/voices` | Lists the Piper voices you've installed, for Open WebUI's voice list. |
| (with `HA_URL` and `HA_TOKEN`) | Asks the Lena integration how she feels and lets her voice follow it. See below. |
| `GET /health` | Answers `ok`, for Docker's health check. The only request that needs no key; it tells nothing else. |

## Her voice follows her mood (optional)

Add `HA_URL` (your Home Assistant URL, as the bridge's container reaches it) and `HA_TOKEN` (a long-lived access token from a Home Assistant admin, or from a user allowed under **Who may use Lena** in Lena's options) to the bridge's environment. Before speaking, the bridge asks the Lena integration how she feels (`GET /api/lena/context`, at most once a minute) and adjusts Piper's audio with ffmpeg: a little slower and lower when she's tired or low, a little quicker and brighter when she's cheerful. The changes are small, a few percent. If Home Assistant can't be reached she sounds as usual, and `docker logs lena-voice` says why.

The Lena app's **Her voice follows her mood** setting does the same for the app's own speech. If the app speaks through this bridge, use one or the other, so her mood isn't applied twice.

## Troubleshooting

- **The container stops at once, and its log says "Set API_KEY":** make the `.env` file from step 1 (or set `ALLOW_NO_API_KEY`), then `docker compose up -d`.
- **Open WebUI says "Invalid API key":** the API Key in Open WebUI's audio settings (both Text-to-Speech and Speech-to-Text) must be the bridge's `LENA_VOICE_API_KEY`.

- **`docker logs lena-voice` shows "Connection refused" or "Name does not resolve":** the bridge can't reach Piper or Whisper. Check the container names, ports and that they share a network.
- **The voice list in Open WebUI is empty:** same as above, or no voices are installed in your Piper container yet.
- **Open WebUI still uses a generic voice:** check the engine is **OpenAI** and the URL ends in `/v1`, then refresh the page.
