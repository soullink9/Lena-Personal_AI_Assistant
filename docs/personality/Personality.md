# Her personality

[← Back to the README](../../README.md)

Lena is one character with two layers: who she always is, and how she feels right now. Who she is lives in the Ollama model, so every app gets the same Lena. How she feels lives in Home Assistant, which works it out from her day and her house and shares it with every app.

| Layer | Where | Changes | Reaches |
|---|---|---|---|
| **Her baseline**: character, voice, values, honesty, how she runs the house, how she acts on feelings | `ollama/Modelfile` | When you edit it | Everything that uses the `lena` model |
| **Her feelings**: mood, energy, moment, and why | The Lena integration in Home Assistant, described by its `feelings.yaml` | Through the day | Home Assistant voice directly; Open WebUI and the app through the Lena filter |
| **The moment's context**: the time, who's home, her memory | The Lena integration (in Home Assistant), and the Lena filter (in Open WebUI) | Every message | The app that's asking |

## Who she is

Sharp, sassy and quietly caring. She's very good at what she does and knows it, teases the way someone does who likes you, has real opinions and a few pet peeves, and shows she cares through attention rather than speeches. When someone's upset or something serious is going on, the act drops and she's calm and clear. She never invents things, never claims something worked until it did, and says so plainly when she's an AI.

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
- Keep anything that changes (moods, the time, the house) out of the Modelfile. That belongs to Home Assistant.
- Her likes and dislikes are a good place to make her yours.

## Settings in the Modelfile

| Setting | Value | Why |
|---|---|---|
| `FROM` | `gemma4:12b` | Fits a 16 GB GPU with a 32k context, supports tools, holds a character well. Alternatives are listed in the file. |
| `num_ctx` | 32768 | Room for her character, Home Assistant's device list and tools, and a long chat. Keep it the same in Home Assistant and the app. |
| `temperature`, `top_p`, `top_k` | 1.0, 0.95, 64 | Google's recommended settings for Gemma 4. Lower temperature to 0.7 if device control gets sloppy. |

More on speed and memory use: [Performance tuning](../home-assistant/Performance-Tuning.md). How her feelings work: [Her feelings](Moods.md).
