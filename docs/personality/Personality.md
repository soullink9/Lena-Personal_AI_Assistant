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

### Comparing two versions of her

`tools/personality_check.py` asks her the same fourteen things every time (a device command, a plain fact, a joke, a serious moment, "who are you?", something that fails, a greeting at night, a vague request, an insult, a thank-you, unlocking a door, a device that doesn't exist, an opinion, and an attempt to push her out of character) and saves her answers to a file:

```sh
python3 tools/personality_check.py --out before.md     # before you edit the Modelfile
ollama create lena -f ollama/Modelfile                 # after editing
python3 tools/personality_check.py --out after.md
```

Add `--url http://<address>:11434` if Ollama runs on another machine. The script is one file and needs nothing else from the repository, so it can be copied to the Ollama machine by itself. It needs only Python and touches no real house: it sends a fixed evening, a fixed mood, a small pretend home, the integration's **Before you answer** reminder (`--no-reminder` leaves it out) and pretend tools, answers for the tools itself (the pretend living room curtains never answer, to see how she reports a failure), and writes down each tool she used. Her answers vary from run to run, and so does whether she uses a tool, so add `--runs 3` to ask everything three times, and read the two files for changes in kind (a made-up device, a lost personality, a joke at the wrong moment, a tool she stopped using), not in wording.

Tips:

- Write to her ("You are...") and describe behavior, not adjectives alone. "Lead with the answer, then one touch of personality" works better than "be witty".
- Every word is sent with every message. Keep it tight: a model follows short, concrete rules better than long ones.
- Use quoted "never say X" phrases sparingly. Models sometimes repeat the very phrases they're told to avoid.
- Change what works one piece at a time, and test it. Three well-meant changes made her worse in 0.6.28 to 0.6.30 and were taken out again: replacing her sample lines with example exchanges cost her the remark she adds to everyday answers; moving her house rules out of this file and sending them with the tools made her drop her character when pushed and stop recording how a thank-you made her feel; and a closing line added nothing. `tools/personality_check.py --runs 3` shows this kind of thing; one run doesn't.
- What she's sent after her character matters as much as the character. The integration ends her context with three lines (**Before you answer**, `HOUSE_REMINDER` in its `brain.py`) that make her call a tool before saying something's done. The same lines placed before her context, or without their last one ("You do all of this as yourself"), made her give up her character when told to. Test any change there the same way.
- Keep anything that changes (moods, the time, the house) out of the Modelfile. That belongs to Home Assistant.
- Her likes and dislikes are a good place to make her yours.

## Settings in the Modelfile

| Setting | Value | Why |
|---|---|---|
| `FROM` | `gemma4:12b` | Fits a 16 GB GPU with a 32k context, supports tools, holds a character well. Alternatives are listed in the file. Her character is written for a model of this size or larger. |
| `num_ctx` | 32768 | Room for her character, Home Assistant's device list and tools, and a long chat. Keep it the same in Home Assistant and the app. |
| `temperature` | 0.7 | Google recommends 1.0 for Gemma 4, but at 1.0 she misspelled tool names and guessed device names. Go back to 1.0 if she starts to sound flat. |
| `top_p`, `top_k` | 0.95, 64 | Google's recommended settings for Gemma 4. They belong to the base model: with another one, set its recommended values or delete both lines to use the ones it ships with. |
| `num_predict` | 4096 | A ceiling on one reply, thinking included, so a model stuck redrafting a sentence stops instead of running on. |

More on speed and memory use: [Performance tuning](../home-assistant/Performance-Tuning.md). How her feelings work: [Her feelings](Moods.md).
