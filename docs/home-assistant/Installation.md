# Home Assistant installation

[← Back to Getting started](../Getting-Started.md)

The **Lena** integration gives her a voice in your house, and her feelings and shared memory everywhere. You copy in one folder, add the integration, and tick one box in your Ollama agent. It creates everything it needs itself, so there are no helpers, blueprints, scripts or templates to set up, and nothing in YAML.

Menu names change between Home Assistant versions; if a path doesn't match, look for the closest equivalent.

## What you need

- Home Assistant 2026.9 or newer (the integration is tested against 2026.9 and the latest release).
- Lena built in Ollama ([Getting started, part 1](../Getting-Started.md#1-her-model)).
- For voice: speech-to-text and text-to-speech connected to Home Assistant, for example Whisper and Piper through the Wyoming integration ([Piper](../voice/Piper.md)). Not needed if you only type to her.
- To install by hand rather than with HACS: a way to copy a folder into your Home Assistant config folder (the File editor or Studio Code Server add-on, a Samba share, or SSH).

## 1. Install the integration

**With HACS, or Home Assistant's Marketplace** (Home Assistant 2026.11 and newer, where HACS is built in): add this repository as a custom repository, type **Integration**:

```
https://github.com/soullink9/Lena-Personal_AI_Assistant
```

Then find **Lena**, download it, and restart Home Assistant. Updates then show up there whenever a new version is released. It offers released versions only, never work in progress.

**By hand:** copy `custom_components/lena` from this repository into your Home Assistant config folder (the one with `configuration.yaml`), so you have:

```
config/custom_components/lena/__init__.py
config/custom_components/lena/feelings.yaml
...
```

Then restart Home Assistant (Settings → System → Restart).

## 2. Add Lena

Settings → Devices & services → **Add integration** → **Lena** → Submit. There's only ever one Lena.

It creates a **Lena** device with:

| Entity | What it is |
|---|---|
| `select.lena_mood` | Her mood. The reason, if there is one, is in its `reason` attribute. |
| `select.lena_energy` | Her energy through the day. |
| `select.lena_moment` | A reaction to something that just happened, or **Nothing**. Also has a `reason`. |
| `todo.lena_memory` | Her memory, shared with Open WebUI and the app. |

You can change any of the three dropdowns by hand, use them in automations, or put them on a dashboard. Details: [the integration](Integration.md).

## 3. Tell her about your house

Settings → Devices & services → Lena → **Configure**:

- **People she misses:** coming home after a while makes her delighted; an empty house makes her restless.
- **Weather:** a weather entity, such as your forecast. Rain, storms, snow and sunshine colour her mood.
- **Your weather station:** your own weather sensors (an Ecowitt in the backyard: temperature, humidity, wind, rain). She uses them for what it's like outside right now, before any forecast, in conversations and her morning briefing.
- **Special days calendars:** birthdays, anniversaries or holidays. Anything on them today puts her in a good mood, and she knows why.
- **Things that worry her:** problem sensors like leaks, smoke or a garage door left open. She's worried until they clear, then relieved.
- **Her day:** when she wakes up and winds down.
- **Alerts:** the phones she tells when something worries her (they need the Home Assistant companion app), and your Lena agent, so she writes each alert herself. Tapping an alert opens the Lena app, listening. See [House alerts](../app/How-It-Works.md#house-alerts).
- **How quickly her feelings move:** the defaults are a good start. See [Her feelings](../personality/Moods.md).
- **Her memory:** how often she tidies it (every 7 days by default; 0 never) and with which AI Task. See [Memory](Memory.md).

All optional; she still has a day of her own without them.

## 4. Connect Ollama

1. Settings → Devices & services → Add integration → **Ollama**, with your Ollama address (`http://<server>:11434`).
2. Add a conversation agent with the model `lena`, and set:
   - **Control Home Assistant:** tick **Assist** *and* **Lena**. Assist lets her control your devices; Lena gives her feelings, memory and her tools (remember, correct and forget, recording how something made her feel, calendar events, timers, alarms and reminders on her displays, the volume, the intercom, and the house's history).
   - **Instructions:** leave empty. Her character is in the model, and the Lena choice adds the time, who's home, how she feels and what she remembers to every request.
   - **Context window size:** 32768, the same as `num_ctx` in the Modelfile.
   - **Keep alive:** -1, so the model stays loaded.
   - **Think before responding** (if your version has it): off. Thinking adds seconds to every spoken reply.

## 5. Choose what she can see

Settings → Voice assistants → **Expose**: expose only the devices you'd mention out loud. Under about a hundred entities is faster and more accurate, and give duplicates distinct names (four lights all called "Light Switch" cause wrong-device errors). Her own entities don't need exposing; the Lena choice gives her everything she needs about herself.

## 6. Make a voice assistant

Settings → Voice assistants → Add assistant:

- **Conversation agent:** your Lena (Ollama) agent.
- **Speech-to-text** and **Text-to-speech:** your Whisper and Piper.
- **Prefer handling commands locally:** on. Simple commands like "turn on the kitchen light" are then handled instantly without the model, and Lena handles everything else.

## 7. Try it

- "Who are you?"
- "Turn on the kitchen light."
- "Remember that Alex likes the office at seventy degrees." Then look at **Lena Memory** in the To-do lists panel, and ask "What do you remember about Alex?"
- Set **Lena Mood** to **Grumpy** and ask how her day is going. Then **Fond**, and ask again.
- "Thank you, Lena, that was perfect." **Lena Moment** may change to **Pleased**.

If something's wrong, see [Troubleshooting](Troubleshooting.md). Then finish the Open WebUI side so her feelings and memory reach your phone: [Lena in Open WebUI](../open-webui/Setup.md).

## Updating

With HACS or the Marketplace, update it there when a new version shows, then restart. By hand, copy the new `custom_components/lena` folder over the old one and restart. Either way, your edits to `feelings.yaml` are overwritten, so keep a copy if you've changed it.
