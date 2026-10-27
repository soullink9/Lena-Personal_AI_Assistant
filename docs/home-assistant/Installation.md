# Installation

[← Back to README](../../README.md)

Menu names in Home Assistant change between versions. Where a path below does not match what you see, look for the closest equivalent.

## What you need

- Home Assistant 2024.10 or newer with Assist set up.
- A machine running [Ollama](https://ollama.com/). This was built on an RTX 4070 Ti Super with 16 GB of VRAM; a 9B-class model with a 32k context fits on a card that size (use 16k on a smaller card).
- A speech-to-text and a text-to-speech service connected to Home Assistant (for example Whisper and Piper through the Wyoming integration). Not needed if you only type to her.

## 1. Build the model

Copy `ollama/Modelfile` to the machine running Ollama. Open it and check the `FROM` line: it names the base model, and Ollama must be able to download it. Then run:

```
ollama create lena -f Modelfile
ollama run lena
```

Say hello and check that she has a personality. Press Ctrl+D to leave. To change her later, edit the file and run the same `ollama create` command again.

Optional: the Docker example in `ollama/docker-compose.example.yml` runs Ollama with a watchdog that keeps the model loaded. See [Performance-Tuning](Performance-Tuning.md).

## 2. Connect Ollama to Home Assistant

1. Settings > Devices & services > Add integration > **Ollama**.
2. Enter the URL of your Ollama server in the form `http://<server-address>:11434`, and choose the model `lena`.
3. Open the settings for the conversation agent that was created and set:
   - **Control Home Assistant**: enabled, using the Assist API. Without this she can talk but cannot control anything.
   - **Context window**: 32768, the same number as `num_ctx` in the Modelfile.
   - **Keep alive**: -1, so the model stays loaded.
   - **Think / reasoning** (if your version has it): off, for voice. Thinking adds long delays.

## 3. Create the helpers

Create the five dropdown helpers and the to-do list. Names and options are on the [Helpers](Helpers.md) page. Create them through the UI; the blueprint does not create them.

## 4. Import the blueprint

The blueprint is `home-assistant/blueprints/automation/soullink9/ai_persona_engine.yaml`. Either:

- **Copy it in** (works for a private repository): copy the `home-assistant/blueprints` folder into your Home Assistant configuration folder, so the file ends up at `config/blueprints/automation/soullink9/ai_persona_engine.yaml`, then reload automations. The folder layout already matches what Home Assistant expects.
- **Import it** (public repositories only): Settings > Automations & scenes > Blueprints > Import blueprint, and paste the file's GitHub URL. Home Assistant can't read files from a private repository.

Then create an automation from it, choose your five helpers, and save. The defaults are fine to start. See [Persona-Engine](Persona-Engine.md) for what each setting does.

To see it work without waiting hours, change the mood helper by hand: the Delivery and Flavor helpers should update within a second.

## 5. Add the prompt

Open `home-assistant/lena_prompt.txt`, change the entity IDs to yours (the header comment lists them), and paste the whole file into the conversation agent's **Instructions** field. Details in [Prompt-Reference](Prompt-Reference.md).

You can paste it into Developer tools > Template first to see exactly what the model will receive.

## 6. Add memory (optional)

Follow [Memory](Memory.md) to create the to-do list and add the sensor.

## 7. Create a voice assistant

1. Settings > Voice assistants > Add assistant.
2. Conversation agent: **Lena** (the Ollama agent).
3. Speech-to-text and text-to-speech: your Whisper and Piper entries.
4. Consider enabling **Prefer handling commands locally**. Simple commands like "turn on the kitchen light" are then handled instantly without the model, and Lena handles everything else.

Voice tuning is on [Voice-Piper](../voice/Piper.md).

## 8. Choose what she can see

Settings > Voice assistants > Expose. Expose only what you would say out loud. Fewer than about a hundred entities is faster and more accurate. Give duplicate names distinct names (four lights all called "Light Switch" cause wrong-device errors), and leave her own helpers and memory list unexposed unless you want to change them by voice.

## 9. Try it

- "Turn on the kitchen light."
- "Who are you?"
- "Remember that Alex likes the office at seventy degrees." Then check the to-do list.
- Change the mood helper and ask the same thing twice.

If something is wrong, see [Troubleshooting](Troubleshooting.md).
