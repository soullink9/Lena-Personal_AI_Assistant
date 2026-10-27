# Overview

[← Back to the README](../README.md)

Lena is one character reached three ways: by voice through Home Assistant, by typing or calling in Open WebUI, and through the Lena app on your phone. All of it runs on your own hardware.

## How the pieces fit together

```
 Lena app (phone)
        │ HTTPS
        ▼
   Open WebUI ──speech──► voice bridge ──Wyoming──► Whisper + Piper ◄──Wyoming── Home Assistant Assist
        │                                                                              │
        └──────────────────────────► Ollama, model "lena" ◄────────────────────────────┘
                                  (Modelfile = her character)
```

- **Home Assistant** listens with Whisper, builds a prompt from `lena_prompt.txt` plus the state of your home, her memory and today's mood, sends it to the `lena` model, carries out any device actions and speaks the reply with Piper.
- **Open WebUI** uses the same `lena` model with your tools, knowledge collections and memories.
- **The Lena app** is a native Android shell around your Open WebUI, plus extras a web page can't do: being your phone's assistant, a voice sheet, a widget, a Quick Settings tile, Share to Lena, notifications and scheduled prompts. Everything it starts is saved in your Open WebUI history.
- **The voice bridge** lets Open WebUI (and so the app) use the same Piper voice and Whisper as Home Assistant. Without it, Open WebUI uses its own speech engines.

## Who decides what

Three things shape how she talks:

| Part | What it decides | Where it applies |
|---|---|---|
| `ollama/Modelfile` | Who she is: character, humour, values, model settings | Everywhere the `lena` model is used |
| `home-assistant/lena_prompt.txt` | Where she is right now: spoken replies only, the house, what she remembers, her current mood | Home Assistant only |
| The persona engine blueprint | How her mood, energy, delivery and flavour change through the day | Home Assistant only |

In Open WebUI and the app she gets her character from the Modelfile and her context from Open WebUI's own system prompt, tools and memories. More in the [Prompt reference](home-assistant/Prompt-Reference.md).

## Her two memories

Lena currently remembers things in two separate places:

| Memory | Where it's kept | Used by | How to see it |
|---|---|---|---|
| **Lena's Memory** to-do list | Home Assistant | Home Assistant voice | The to-do list in Home Assistant. See [Memory](home-assistant/Memory.md). |
| **Open WebUI memories** | Open WebUI | Open WebUI and the app | **Memory & knowledge** in the app's drawer, or Open WebUI's settings |

Something you tell her in the kitchen isn't automatically known on your phone, and the other way round.

## One context size

The model's context window is set in three places, and they should match. All three are **32768** by default:

1. `num_ctx` in `ollama/Modelfile`.
2. **Context window size** in Home Assistant's Ollama agent options.
3. **Lena's context size** in the app's settings, which gives the ongoing conversation half of it.

See [Performance tuning](home-assistant/Performance-Tuning.md) if you need to change it.
