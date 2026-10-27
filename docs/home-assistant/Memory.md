# Memory

[← Back to Home Assistant installation](Installation.md)

Lena has one memory, shared everywhere she lives: a Home Assistant to-do list called **Lena's Memory**. What you tell her in the kitchen she knows on your phone, and the other way round.

| Where | How she reads it | How she saves and forgets |
|---|---|---|
| Home Assistant voice | The **Lena recall** script, when it's relevant or she's asked | Home Assistant's own to-do tools (add and remove items) |
| Open WebUI and the Lena app | The **Lena** filter adds it to every chat | The **Lena** tool's remember and forget |

It's optional. Without it, she simply doesn't remember things between conversations.

## Setup

1. Create the to-do list: [Helpers](Helpers.md#her-memory-optional).
2. Create the **Lena recall** script from its blueprint, and expose it and the list to Assist: [Installation, steps 5 and 6](Installation.md#5-create-her-scripts).
3. Keep the "Your memory" section at the end of her prompt.
4. In Open WebUI, set `memory_list` in the filter and tool: [Lena in Open WebUI](../open-webui/Setup.md).

## Why a script in Home Assistant

Home Assistant's templates can't read the items on a to-do list, so her prompt can't include them directly without a YAML template sensor. To keep everything in the UI, she looks her memory up with the Lena recall script instead. The trade-off: in Home Assistant she checks her memory when she thinks it matters, rather than always having it in front of her. In Open WebUI and the app it's always there.

## How she saves

When someone asks her to remember something, or tells her a lasting fact that clearly matters, she adds one short third-person line, like "Alex likes the office at seventy degrees." She's told not to save passing moods, one-off requests, duplicates or secrets, and to confirm only once the list accepts it.

Some small models say they saved something without actually doing it. Check the list after your first few tries.

## Forgetting and tidying

- Ask her to forget something, or delete the item in the to-do list yourself.
- **Tick an item off** instead of deleting it to retire it: she only reads open items.
- Keep the list short. In Open WebUI it's added to every message.

## Privacy

Everything on the list is shared with everyone who talks to Lena, in every app, and is sent to your own Ollama server with their messages. Don't keep passwords, door codes or anything similar in it. She's told to refuse to save those, but don't rely on that.
