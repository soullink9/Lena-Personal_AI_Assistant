# Her feelings

[← Back to her personality](Personality.md)

Lena's feelings change for real reasons: the time of day, the weather, people coming and going, something in the house going wrong, and how people treat her. Three layers, each a Home Assistant helper:

| Layer | Lasts | Moved by |
|---|---|---|
| **Mood** | Hours | Her own drift through the day, the weather, an empty or full house, special days |
| **Energy** | Follows her day | Waking up, the afternoon, bedtime |
| **Moment** | A few minutes | Something that just happened: someone came home, a leak, a thank-you, an insult |

Mood and Moment each have a **reason** helper too, so she knows *why* she feels that way and can mention it naturally ("this rain has me in a mood").

## Only the current feeling reaches her

Every feeling has a description in `home-assistant/custom_templates/lena.jinja`: what's going on inside her and how it sounds. The helpers hold only the names. When her prompt is built, Home Assistant looks up the current mood, energy and moment and includes just those three descriptions, so she's never distracted by the other twenty feelings she isn't having. It looks like this:

```
## How you feel right now
Mood: Grumpy. Out of sorts with a short fuse. Shorter replies, audible eye-rolling, muttered
complaints about the request or the world, but everything still gets done properly. Grudging,
never mean. Why: It just started raining.
Energy: Groggy. Still booting up. Sentences come out shorter and a beat late, with a yawn in
your tone. Sharp underneath, just slow to get there.
Just now: Exasperated. Someone is testing your patience. Audible sighs and pointed remarks,
while you still help. Why: Alex asked for the weather three times.
Let this show in how you sound, without naming it.
```

How she acts on it is part of her baseline in the Modelfile: show it, don't announce it; noticeable within a sentence; never cartoonish; never at the cost of accuracy or kindness; and serious moments override everything.

## The feelings

**Moods:** Content, Cheerful, Playful, Smug, Curious, Focused, Restless, Wistful, Grumpy, Uneasy, Fond.
**Energy:** Groggy, Steady, Wired, Drained.
**Moments:** Pleased, Amused, Proud, Touched, Delighted, Exasperated, Offended, Worried, Relieved, and **Nothing** for no reaction.

The descriptions are in `lena.jinja`.

## What moves her

The **Lena Mood Engine** automation does it all. Each setting is in its blueprint form.

| When | What happens |
|---|---|
| **Morning** | She wakes Groggy. If your special-days calendar has something today, she's Cheerful and knows why ("It's Sam's birthday today"). Otherwise she wakes in a mood of her own, sometimes colored by the weather. |
| **Every hour** | Energy follows her day: groggy just after waking, steady or wired through the day, a dip after lunch, drained near bedtime. Once her mood has lasted a few hours, it may drift, but only to a **neighbouring** mood, so she changes gradually the way a person does. Her energy, the weather and the house pull her toward some moods: rain toward Wistful or cozy-Content, everyone home toward Fond, an empty house toward Restless. |
| **Someone comes home** after hours away | Moment: Delighted, with who and how long. After a day or more, her mood turns Fond. |
| **The house is empty** for hours | Mood: Restless or Wistful. |
| **The weather turns** (rain, storm, snow, sun, fog, wind) | She may react: a storm can make her Uneasy, snow Cheerful. |
| **A worry sensor turns on** (leak, smoke, a door left open) | Moment: Worried, naming what. It stays until the sensor clears, then she's Relieved. |
| **Someone treats her a certain way** | She sets her own Moment with the **Lena feel** script (Pleased, Amused, Proud, Touched, Delighted, Exasperated, Offended), in Home Assistant or Open WebUI. |
| **Bedtime** | She's Drained. |
| **A few minutes pass** | A Moment fades back to Nothing, unless she's still worried about something. |

You can always set any helper by hand, for example from a dashboard. If you change her mood by hand, its old reason is cleared.

## Changing her feelings

### Change how one sounds

Edit its description in `lena.jinja`, copy the file into Home Assistant and run **Reload custom Jinja2 templates**. Write it to her, describe both the inside and how it sounds, and keep it to two or three sentences.

### Add or rename a feeling

A feeling's name has to match in up to five places:

1. Its description in `lena.jinja`.
2. The options of its dropdown helper.
3. The mood engine: for a mood, its own line in `neighbours`, and in its neighbours' lists, plus any pulls (`energy_pull`, `weather_pull`, `morning_moods`) you want.
4. For a moment she can choose herself: the options in `lena_feel.yaml`.
5. And the same list in `open-webui/lena_tools.py`.

Run `python3 tools/check.py`. It checks all of these against each other and runs every branch of the mood engine.

### Change how she drifts

In the mood engine blueprint's `variables` section:

- `neighbours`: which moods she can drift between.
- `baseline`: where she tends to come back to.
- `energy_pull`, `weather_pull`, and the empty or full house: what pulls her where. Repeat a name to make it more likely.

The blueprint form has the timing: how long a mood lasts at least, the chance per hour that it moves on, how quickly moments fade, how sensitive she is to the weather.
