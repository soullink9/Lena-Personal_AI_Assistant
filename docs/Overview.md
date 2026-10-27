# Overview

[← Back to the README](../README.md)

Lena is one character reached three ways: by voice through Home Assistant, by typing or calling in Open WebUI, and through the Lena app on your phone. All of it runs on your own hardware.

## How the pieces fit together

```
 Lena app (phone)
        │ HTTPS
        ▼
   Open WebUI ──speech──► voice bridge ──Wyoming──► Whisper + Piper ◄──Wyoming── Home Assistant Assist
     │    ▲                                                                         │      │
     │    └──── Lena filter and tool: her feelings and shared memory ◄──────────────┼──────┤
     │                                                                              │  Lena integration:
     └────────────────────────► Ollama, model "lena" ◄──────────────────────────────┘  feelings,
                               (Modelfile = her character)                             memory list
```

- **Ollama** runs the `lena` model. Her whole character is built into it, so every app gets the same Lena.
- **Home Assistant** listens with Whisper, adds the time, who's home and how she feels to her prompt, sends it to `lena`, carries out device actions and speaks the reply with Piper. Its **Lena integration** keeps her **feelings** (mood, energy and moment) and her **memory** (a to-do list).
- **Open WebUI** uses the same `lena` model with your tools and knowledge. The **Lena filter** brings her current feelings and memory over from Home Assistant, and the **Lena tool** lets her save memories and react, so she's the same Lena in a chat as in the kitchen.
- **The Lena app** is a native Android shell around your Open WebUI, plus what a web page can't do: being your phone's assistant, a voice sheet, a widget, a Quick Settings tile, Share to Lena, notifications and scheduled prompts. It goes through Open WebUI, so the filter and tool work there too. Connect Home Assistant in its settings and it also shows how she feels and lets you edit her shared memory.
- **The voice bridge** lets Open WebUI (and so the app) use the same Piper voice and Whisper as Home Assistant. Without it, Open WebUI uses its own speech engines.

## Who decides what

| Part | Decides | Applies to |
|---|---|---|
| `ollama/Modelfile` | Who she is: character, voice, values, honesty, how she runs the house and acts on feelings | Everything that uses the `lena` model |
| The Lena integration in Home Assistant (`feelings.yaml` for how each feeling sounds) | How she feels right now, and why; the time, who's home and what she remembers | Home Assistant directly; Open WebUI and the app through the filter |
| The Lena filter in Open WebUI | Her feelings and memory in chats | Open WebUI and the app |

More: [Her personality](personality/Personality.md) and [Her feelings](personality/Moods.md).

## One memory

Her memory is one Home Assistant to-do list, **Lena Memory** (`todo.lena_memory`), shared everywhere. In Home Assistant the integration adds it to every request; in Open WebUI and the app the filter adds it to every chat, and the tool saves and forgets. See [Memory](home-assistant/Memory.md).

Open WebUI's own memory feature is separate; turn it off for a single shared memory. The app's **Memory & knowledge** screen shows her shared list (once Home Assistant is connected in the app), Open WebUI's memories and your knowledge.

## One context size

The model's context window is set in three places, and they should match. All three are **32768** by default:

1. `num_ctx` in `ollama/Modelfile`.
2. **Context window size** in Home Assistant's Ollama agent options.
3. **Her context size** (Settings → Conversation) in the app's settings, which gives the ongoing conversation half of it.

See [Performance tuning](home-assistant/Performance-Tuning.md) if you need to change it.
