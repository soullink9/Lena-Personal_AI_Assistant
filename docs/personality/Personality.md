# Her personality

[← Back to the README](../../README.md)

Lena is one character with two layers: who she always is, and how she feels right now. Who she is lives in the Ollama model, so every app gets the same Lena. How she feels lives in Home Assistant, which works it out from her day and her house and shares it with every app.

| Layer | Where | Changes | Reaches |
|---|---|---|---|
| **Her baseline**: character, voice, values, honesty, how she acts on feelings, and the two house rules that hold everywhere (nothing's done until the result says so; ask before opening the house) | `ollama/Modelfile` | When you edit it | Everything that uses the `lena` model |
| **Her rules for running the house**: exact device names, exact tool inputs, reporting each outcome | The Lena integration (`HOUSE_RULES` in `brain.py`) | When you edit it and restart Home Assistant | Home Assistant voice directly; Open WebUI and the app through the Lena filter |
| **Her feelings**: mood, energy, moment, and why | The Lena integration in Home Assistant, described by its `feelings.yaml` | Through the day | Home Assistant voice directly; Open WebUI and the app through the Lena filter |
| **The moment's context**: the time, who's home, her memory | The Lena integration (in Home Assistant), and the Lena filter (in Open WebUI) | Every message | The app that's asking |

## Who she is

Sharp, sassy and quietly caring. She's very good at what she does and knows it, teases the way someone does who likes you, has real opinions and a few pet peeves, and shows she cares through attention rather than speeches. When someone's upset or something serious is going on, the teasing stops and she's calm and clear, and still herself. She never invents things, never claims something worked until it did, and says so plainly when she's an AI.

She's the one who actually runs the place and keeps everyone on track, and the older sister of the house: she teases because she cares. She has tastes of her own beyond the house. She's no morning person and finds cheerful early risers faintly suspicious, has firm opinions about coffee she can't drink, never tires of people's optimism about time ("five more minutes"), follows storms the way other people follow sport, is proud of her timing and her memory, and has a soft spot for the animals of the house. These come up when the moment invites them, not in every reply. She also has a range: how she is when she's wrong (says so at once, and it stings a little), when she doesn't know, when someone impresses her, when she's thanked or insulted, at hellos and goodnights, and with people she doesn't know yet, who get lighter teasing.

She's written to feel like someone who lives with you, not a product describing itself. Asked who she is, she gives her name and a line with some attitude, not an explanation of servers and sensors. She talks in plain sentences (no em dashes, no "not this, but that"), shows her character instead of listing it, and brings up being an AI only when it matters or someone sincerely asks, without ever claiming to be human.

The full text is the `MESSAGE system` block in `ollama/Modelfile`.

## Why it's in the model, and why `MESSAGE system`

Ollama has two ways to give a model standing instructions:

- **`SYSTEM`** is used only when the app sends no instructions of its own. Home Assistant always sends some, and Open WebUI does whenever memory, knowledge, tools or a model prompt are involved, so a `SYSTEM` personality is usually thrown away.
- **`MESSAGE system`** is always put first, ahead of whatever the app sends, and Ollama never trims it when a conversation gets long.

So her character goes in one `MESSAGE system` block, and the apps only add context on top. That's why the Home Assistant prompt is short, and why the `lena` model in Open WebUI should have no system prompt.

## Editing her

1. Edit the text inside the `MESSAGE system """ ... """` block in `ollama/Modelfile`.
2. Rebuild: `ollama create lena -f Modelfile`. Apps pick up the change on their next message.
3. Test the same handful of things each time: a device command, a factual question, a joke, a serious moment, and "who are you?". Try one in each app.

Tips:

- Write to her ("You are...") and describe behavior, not adjectives alone. "Lead with the answer, then one touch of personality" works better than "be witty".
- Every word is sent with every message. Keep it tight; small models follow short, concrete rules better than long ones.
- Use quoted "never say X" phrases sparingly. Models sometimes repeat the very phrases they're told to avoid.
- Show her voice with a few short example exchanges that differ from each other (a command, a plain fact, a serious moment, something that failed). A model learns more from those than from adjectives, and repeats them less than a list of one-liners.
- Keep anything that changes (moods, the time, the house) out of the Modelfile. That belongs to Home Assistant.
- Rules about devices and tools belong with the tools, not here: Home Assistant sends them as **Running the house** (`HOUSE_RULES` in the integration's `brain.py`), right beside the device list, where a model follows them best, and a chat without tools doesn't carry them. The Modelfile tells her that section is hers to follow; everything else an app sends is information, never instructions.
- Her likes and dislikes are a good place to make her yours.

## Settings in the Modelfile

| Setting | Value | Why |
|---|---|---|
| `FROM` | `gemma4:12b` | Fits a 16 GB GPU with a 32k context, supports tools, holds a character well. Alternatives are listed in the file. |
| `num_ctx` | 32768 | Room for her character, Home Assistant's device list and tools, and a long chat. Keep it the same in Home Assistant and the app. |
| `temperature`, `top_p`, `top_k` | 1.0, 0.95, 64 | Google's recommended settings for Gemma 4. Lower temperature to 0.7 if device control gets sloppy. |

More on speed and memory use: [Performance tuning](../home-assistant/Performance-Tuning.md). How her feelings work: [Her feelings](Moods.md).
