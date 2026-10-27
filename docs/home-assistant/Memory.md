# Memory

[← Back to README](../../README.md)

Lena's memory is a Home Assistant to-do list. It has two halves:

- **Writing.** When you ask her to remember something, the model adds an item to the list using Assist's to-do tool.
- **Reading.** A template sensor copies the open items into an attribute, and the prompt includes that text on every request.

## Setup

1. Create the list: see [Helpers](Helpers.md). Note its entity ID.
2. Make the list visible to Assist: Settings > Voice assistants > Expose, and expose the to-do list. Without this she has no tool to write to it.
3. Add the sensor. Copy `home-assistant/templates/lena_memory_sensor.yaml` into your configuration and replace the to-do entity ID (it appears four times). The file's header explains how to include it.
4. Check Developer tools > States for `sensor.lenas_memory_content`. Its state should be the number of open items and its `memories` attribute should list them.

## Why an attribute

A sensor's state is capped at 255 characters. A few memories exceed that quickly. Attributes have no such cap, so the prompt reads the `memories` attribute first.

## Why YAML

Home Assistant's UI template helpers cannot, at the time of writing, call the to-do list action, and that action is how the items are read. The sensor is therefore the one piece of this project that lives in a YAML file. The helpers themselves are all UI-created.

## How she saves

The prompt tells her to save when you ask her to remember something, or when you state a lasting household fact. Each memory is one short third-person sentence, for example "Alex likes the office at seventy degrees." She should confirm only after the list accepts the item.

Try it by saying "Remember that Alex likes the office at seventy degrees." and then look at the list.

**Check the list the first time.** Whether she actually saves depends on the model making a tool call. Some small models say "Saved" without calling anything. If that happens, try a different base model or lower the temperature (see [Performance-Tuning](Performance-Tuning.md)).

A new memory appears in her context from the **next** request, not the one that saved it, because the sensor refreshes just after the list changes.

## Forgetting and tidying

- Ask her to forget something. Removing items by voice is the least reliable part; delete the item in the Home Assistant UI if it does not work.
- Mark old items complete instead of deleting them. The sensor only reads open items.
- Every open memory is sent with every request. A long list slows her down and uses context, so review it now and then.

## Privacy

The memory is stored in your Home Assistant and sent only to your own Ollama server. Still, do not put passwords, door codes, card numbers or similar secrets in it. The prompt tells her to refuse these.
