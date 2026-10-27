# Getting started

[← Back to the README](../README.md)

This page takes you from nothing to Lena everywhere: in Open WebUI, in the Lena app on your phone, and as your Home Assistant voice. Do the parts in order; each one builds on the last, and each ends with a quick test. You can stop after any part.

| Part | You get | Time |
|---|---|---|
| [1. Her model](#1-her-model) | Lena herself, in Ollama | 10 minutes |
| [2. Open WebUI](#2-open-webui) | Chatting and calling her in your browser | 10 minutes |
| [3. The Lena app](#3-the-lena-app) | Lena as your phone's assistant | 10 minutes |
| [4. Home Assistant](#4-home-assistant) | Her voice in your house, her feelings and her shared memory | 45 minutes |

Parts 2 and 3 work on their own, but her feelings and memory come from Home Assistant. Until part 4 is done she's always her baseline self in Open WebUI and the app, and remembers nothing between chats.

## 1. Her model

You need a machine running [Ollama](https://ollama.com/) with a GPU that fits `gemma4:12b` and a 32k context (16 GB of VRAM is comfortable).

1. Copy `ollama/Modelfile` to that machine.
2. Build her:
   ```
   ollama create lena -f Modelfile
   ```
3. Talk to her: `ollama run lena`. Say hello and ask who she is. Press Ctrl+D to leave.
4. Check her character survives an app's own instructions, which is what Home Assistant and Open WebUI send:
   ```
   curl http://localhost:11434/api/chat -d '{"model": "lena", "stream": false,
     "messages": [{"role": "system", "content": "You are a helpful assistant."},
                  {"role": "user", "content": "Who are you?"}]}'
   ```
   She should still answer as Lena. If she sounds like a generic assistant, see [Troubleshooting](home-assistant/Troubleshooting.md#she-sounds-like-a-generic-assistant).

To change her later, edit the Modelfile and run the same `ollama create` command again. What's in it and how to edit it: [Her personality](personality/Personality.md). Keeping her loaded and fast: [Performance tuning](home-assistant/Performance-Tuning.md).

## 2. Open WebUI

Full details, including the filter and tool settings: [Lena in Open WebUI](open-webui/Setup.md). In short:

1. **Connect Ollama:** Admin Panel → Settings → Connections → Ollama API, with your Ollama address. The `lena` model appears in the model list.
2. **Set up the model:** Workspace → Models → `lena`. Leave **System Prompt** empty (her character is already in the model) and set **Function Calling** to **Native** under Advanced Params.
3. **Add her feelings and memory:** paste `open-webui/lena_filter.py` as a new function (Admin Panel → Functions) and `open-webui/lena_tools.py` as a new tool (Workspace → Tools), then tick both on the `lena` model. They need your Home Assistant address and a token, so you can come back to this after part 4.
4. **Optional voice:** to give her the same Piper voice as in your house, set up the [voice bridge](voice/Voice-Bridge.md).

**Test:** start a chat with `lena` and ask who she is.

## 3. The Lena app

Full details: [App installation](app/Installation.md) and [Open WebUI setup for the app](app/Open-WebUI-Setup.md).

1. Install `lena-<version>.apk` from the [latest release](../../../releases/latest) (Android 16).
2. Enter your `https://` Open WebUI address (and your sign-in page, if you use Authentik or similar) and sign in.
3. In the app's settings, set **Chats → Model for new chats** to `lena`, and leave **Lena's context size** at 32,768 to match the Modelfile.
4. Make her your assistant: Android Settings → Apps → Default apps → **Digital assistant app** → Lena.
5. After part 4, come back to **Settings → Home Assistant** in the app: your Home Assistant's `https://` address and a long-lived access token. The sheet then shows how she feels, and **Memory & knowledge** gets a **Shared** tab with her shared memory.

The app talks to Open WebUI, so the filter and tool from part 2 work there with nothing extra: same feelings, same memory, same tools.

**Test:** use your phone's assistant gesture and ask her something out loud.

## 4. Home Assistant

Full details: [Home Assistant installation](home-assistant/Installation.md). The steps, all done in the Home Assistant UI apart from copying three files:

1. **Connect Ollama:** add the Ollama integration, choose `lena`, turn on **Control Home Assistant**, set the context window to 32768 and keep-alive to -1.
2. **Create her helpers:** three dropdowns and two text helpers for her feelings, plus a to-do list for her memory. Names and options: [Helpers](home-assistant/Helpers.md).
3. **Copy in her files:** `custom_templates/lena.jinja` (how each feeling sounds) and the `blueprints` folder.
4. **Create the mood engine** automation from its blueprint, and the **Lena feel** and **Lena recall** scripts from theirs.
5. **Expose** the two scripts and the memory list to Assist.
6. **Paste the prompt** `home-assistant/lena_prompt.txt` into the Ollama agent's Instructions.
7. **Make a voice assistant** with Lena as the conversation agent and your Whisper and Piper.

**Test:** "Who are you?", "Remember that Alex likes the office at seventy degrees", then change her mood helper by hand and ask the same thing again.

Now go back to Open WebUI (part 2, step 3) and fill in the filter and tool settings. Ask her on your phone what she remembers: it's the same list.

## What next

- [How her feelings work](personality/Moods.md), and how to change them.
- [Overview](Overview.md) of how all the pieces fit together.
