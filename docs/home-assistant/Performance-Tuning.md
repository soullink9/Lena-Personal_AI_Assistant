# Performance tuning

[← Back to her personality](../personality/Personality.md)

A voice assistant needs to answer in a few seconds. This page lists what affects speed, in the order worth checking.

> **Status:** With the earlier Qwen 9B model, a short request took about 34 seconds in Home Assistant on the author's setup, and thinking mode was the leading suspect. The model is now Gemma 4 12B, which only thinks when asked; speed with it hasn't been measured yet. Treat the checks below as a diagnosis procedure.

## 1. Is the model fully on the GPU?

```
ollama ps
```

Check these columns:

- **PROCESSOR** should say `100% GPU`. A split such as `18%/82% CPU/GPU` means the model plus its context does not fit in VRAM, and everything slows down. Lower `num_ctx`, choose a smaller model, or stop other things using the GPU (speech-to-text, for example).
- **CONTEXT** should match the context you set in the Modelfile and in Home Assistant. If Home Assistant requests a different size than the loaded model has, Ollama may reload the model on requests.
- **UNTIL** should say `Forever` if you want it to stay loaded.

## 2. Thinking mode

Some models write a long hidden chain of thought before answering. For voice this is pure delay. Compare, on the Ollama machine:

```
ollama run lena --verbose --think=false "I need you to do me a favor. Can you do that?"
ollama run lena --verbose --think=true  "I need you to do me a favor. Can you do that?"
```

Compare total duration and the eval rate (tokens per second). If the first is a couple of seconds and the second is much longer, thinking is the cause. Turn it off in the Ollama integration's agent settings (**Think before responding**). Gemma 4 doesn't think unless asked, so this should already be fast.

## 3. Raw generation speed

If both runs above are slow, read the eval rate. A 12B model fully on a modern 16 GB card should generate far faster than speech. A low rate points to hardware, drivers, or part of the model on the CPU.

## 4. Context size

Every request carries her character, Home Assistant's list of exposed entities and the tool definitions. A bigger context costs VRAM and time.

- The Modelfile uses 32768, which leaves room for long conversations in the Lena app. If `ollama ps` shows part of the model on the CPU, drop to 16384.
- Use the same number in the Modelfile, Home Assistant's agent and the Lena app's **Her context size** (Settings → Model) setting.
- Expose fewer entities. Under about a hundred is better.
- Keep her memory list short; Open WebUI adds it to every message.

## 5. Let Home Assistant handle simple commands

Enable **Prefer handling commands locally** on the voice assistant. "Turn on the light" is then handled instantly without the model.

## 6. Keep the model loaded

`OLLAMA_KEEP_ALIVE=-1` on the Ollama server keeps models loaded. Clients that send their own keep-alive value override it, so set keep alive to -1 in Home Assistant's agent options and in Open WebUI as well. The watchdog in `ollama/docker-compose.example.yml` is a safety net that reloads the model if it ever drops out.

## 7. Choosing a model

Smaller and faster models follow a persona less richly but answer sooner; larger ones the reverse. Models that always reason first (the gpt-oss family, for example) are a poor fit for voice. Try two or three with your own test lines. The Modelfile lists alternatives to start with.

## 8. Two variants of one model (optional)

Chat and tool calls want slightly different settings. You can create a second model that reuses the same weights, for example `lena-home`, and point only Home Assistant at it:

```
FROM lena
PARAMETER temperature 0.7
PARAMETER num_predict 300
```

Build it with `ollama create lena-home -f <file>`. It inherits her character from `lena`. A lower temperature tends to help tool-call accuracy and the cap keeps spoken replies short. This is an idea, not a tested recommendation.

## 9. Share the GPU carefully

Speech-to-text and the model share the same VRAM if they run on the same GPU. Leave headroom. `OLLAMA_FLASH_ATTENTION=1` reduces the memory used by long contexts.
