# Prompt reference

Lena's instructions come from two places. Understanding the split makes editing safe.

## The split

| | `ollama/Modelfile` | `home_assistant/lena_prompt.txt` |
| --- | --- | --- |
| Answers | Who is Lena? | Where is she, and what is happening right now? |
| Used by | Anything that loads the model | Only Home Assistant |
| Contains | Character, humor, honesty, how she talks, hard rules | Voice rules, accuracy rules, memory rules, live data, mood, legend |
| Changes | Rarely | Every request (it is a template) |

Home Assistant may **replace** the Modelfile's system prompt with its own instructions rather than add to it. Because of that, `lena_prompt.txt` repeats the core of her character in its "Who you are" section. Keep the two in agreement.

Home Assistant also **adds its own block after your instructions**: the list of exposed devices and rules for calling its tools. Do not duplicate that in the prompt.

## How the prompt is built

The file is a Jinja template. Home Assistant renders it before every request:

- `{{ ... }}` parts are replaced with live values (time, who is home, memory text, the five mood helpers).
- `{# ... #}` parts are comments and are removed, so they cost nothing.
- `pick('entity', 'fallback')` is a small helper at the top. It returns an entity's state, or the fallback when the entity is missing or unavailable, so the model never sees the word "unknown".

To preview the result, paste the file into Developer tools > Template in Home Assistant, or run `python3 tools/check.py` for a test render with stand-in data.

## Sections

| Section | Purpose |
| --- | --- |
| Who you are | Short copy of her character, in case the Modelfile prompt is replaced |
| Voice rules | Everything is spoken: no markdown, lists or symbols; numbers said the way a person says them |
| Accuracy | Never claim success without confirmation; report each outcome of multi-part requests |
| Interpreting requests | Room handling, sensible inference, confirmation before opening the house |
| Being a real assistant | Keeps her useful beyond device control |
| Your memory | When and how to write to the memory list |
| Household context | Time, who is home, the memory text |
| Expression modifiers | Current mood, energy, reaction, delivery, flavor, plus a legend for every value |
| Never | Limits that hold in every state |

## Editing tips

- Change a personality trait in **both** files.
- If you add an option to a helper, add its meaning to the legend. Otherwise the model guesses.
- The confirmation before unlocking doors, opening the garage or gate, or disarming an alarm is in "Interpreting requests". Delete that bullet if you do not want it.
- Keep it short. Every token is sent on every request, and small models follow short, concrete rules better than long ones.
- Use quoted "never say X" phrases sparingly. Small models sometimes repeat the very phrases they are told to avoid. The prompt names a few generic filler phrases because a model without them replied with exactly that filler; prefer describing the behavior wanted for everything else.
- After editing, test with one plain command, one question, one joke and one serious topic.
