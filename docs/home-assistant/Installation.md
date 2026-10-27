# Home Assistant installation

[← Back to Getting started](../Getting-Started.md)

This gives Lena her voice in your house, and her feelings and shared memory everywhere. Everything is created in the Home Assistant UI; the only files you copy in are her feelings template and three blueprints. Menu names change between Home Assistant versions; if a path doesn't match, look for the closest equivalent.

## What you need

- Home Assistant 2025.4 or newer with Assist.
- Lena built in Ollama ([Getting started, part 1](../Getting-Started.md#1-her-model)).
- For voice: speech-to-text and text-to-speech connected to Home Assistant, for example Whisper and Piper through the Wyoming integration ([Piper](../voice/Piper.md)). Not needed if you only type to her.
- A way to copy files into your Home Assistant config folder: the File editor or Studio Code Server add-on, a Samba share, or SSH.

## 1. Connect Ollama

1. Settings → Devices & services → Add integration → **Ollama**, with your Ollama address (`http://<server>:11434`).
2. Add a conversation agent and choose the model `lena`. Then set:
   - **Instructions:** leave for now; you'll paste the prompt in step 7.
   - **Control Home Assistant:** on, with **Assist**. Without it she can talk but can't control anything or use her scripts.
   - **Context window size:** 32768, the same as `num_ctx` in the Modelfile.
   - **Keep alive:** -1, so the model stays loaded.
   - **Think before responding** (if your version has it): off. Thinking adds seconds to every spoken reply.

## 2. Create her helpers

Settings → Devices & services → Helpers → Create helper. Three **Dropdown** helpers and two **Text** helpers hold her feelings, and an optional **Local To-do** list holds her memory. The exact names and options are on the [Helpers](Helpers.md) page. Names are case-sensitive.

## 3. Copy in her files

From this repository's `home-assistant` folder, copy into your Home Assistant config folder (the one with `configuration.yaml`):

| Copy | To |
|---|---|
| `custom_templates/lena.jinja` | `config/custom_templates/lena.jinja` |
| `blueprints/automation/soullink9/lena_mood_engine.yaml` | `config/blueprints/automation/soullink9/` |
| `blueprints/script/soullink9/lena_feel.yaml` and `lena_recall.yaml` | `config/blueprints/script/soullink9/` |

The folder layout matches what Home Assistant expects, so you can copy the whole `custom_templates` and `blueprints` folders in as they are.

Then load them, from Developer tools → Actions:

- **Home Assistant Core Integration: Reload custom Jinja2 templates** (`homeassistant.reload_custom_templates`).
- **Automation: Reload** and **Script: Reload**, so the new blueprints are listed.

Check her feelings template in Developer tools → Template:

```
{% from 'lena.jinja' import feelings %}{{ feelings() }}
```

You should see "## How you feel right now" with her mood, energy and a description.

> If this repository is public, you can import the blueprints instead: Settings → Automations & scenes → Blueprints → Import blueprint, with each file's GitHub address. Home Assistant can't import from a private repository, so copy them in.

## 4. Start her mood engine

Settings → Automations & scenes → Blueprints → **Lena Mood Engine** → Create automation.

- **Her feelings helpers:** the five helpers from step 2.
- **Her day:** when she wakes and goes to bed, and optionally a calendar with birthdays and holidays.
- **Her house:** the people she misses, a weather entity, and binary sensors that should worry her (leak, smoke, a door or garage left open). All optional.
- **Timing:** the defaults are a good start.

Save. To see it work without waiting, set her mood helper to something else by hand and watch the automation's trace. What each setting does: [Her feelings](../personality/Moods.md).

## 5. Create her scripts

Settings → Automations & scenes → Scripts → Create script → from a blueprint.

1. **Lena feel**: pick her Moment dropdown and Moment reason text helper. Name it `Lena feel` (so it becomes `script.lena_feel`).
2. **Lena recall** (only if you want memory): pick her memory to-do list. Name it `Lena recall`.

Don't give either script a description of your own. The blueprint's description is what tells her when to use it, and one you add replaces it.

## 6. Choose what she can see

Settings → Voice assistants → **Expose**:

- Expose **Lena feel**, **Lena recall** and her **Lena's Memory** to-do list. These are her tools.
- Don't expose her five feelings helpers; she gets those through her prompt, and exposing them invites her to change her own mood on request.
- Expose only devices you'd mention out loud. Under about a hundred entities is faster and more accurate, and give duplicates distinct names (four lights all called "Light Switch" cause wrong-device errors).

## 7. Paste her prompt

Open `home-assistant/lena_prompt.txt` and paste the whole file into the Ollama agent's **Instructions** (step 1). It's short: her character is in the model, so this only adds the time, who's home, how she feels and how to use her memory.

- If your helpers have different entity IDs, pass them to `feelings()` (the comment under it shows how).
- If you skipped memory, delete the "Your memory" section at the end.

Paste it into Developer tools → Template first to see exactly what she'll receive. More in the [Prompt reference](Prompt-Reference.md).

## 8. Make a voice assistant

Settings → Voice assistants → Add assistant:

- **Conversation agent:** your Lena (Ollama) agent.
- **Speech-to-text** and **Text-to-speech:** your Whisper and Piper.
- **Prefer handling commands locally:** on. Simple commands like "turn on the kitchen light" are then handled instantly without the model, and Lena handles everything else.

## 9. Try it

- "Who are you?"
- "Turn on the kitchen light."
- "Remember that Alex likes the office at seventy degrees." Check the to-do list, then ask "What do you remember about Alex?"
- Set her mood to **Grumpy** by hand and ask how her day is going. Then **Fond**, and ask again.
- "Thank you, Lena, that was perfect." Her Moment may change to **Pleased**.

If something's wrong, see [Troubleshooting](Troubleshooting.md). Then finish the Open WebUI side so her feelings and memory reach your phone: [Lena in Open WebUI](../open-webui/Setup.md).
