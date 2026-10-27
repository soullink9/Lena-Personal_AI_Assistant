# Her feelings

[← Back to her personality](Personality.md)

Lena's feelings change for real reasons: the time of day, the weather, people coming and going, something in the house going wrong, and how people treat her. The **Lena** integration in Home Assistant keeps them, in three layers:

| Layer | Lasts | Moved by |
|---|---|---|
| **Mood** | Hours | Her own drift through the day, the weather, an empty or full house, special days |
| **Energy** | Follows her day | Waking up, the afternoon, bedtime |
| **Moment** | A few minutes | Something that just happened: someone came home, a leak, a thank-you, an insult |

Mood and Moment come with a **reason**, so she knows *why* she feels that way and can mention it naturally ("this rain has me in a mood").

## Only the current feeling reaches her

Every feeling has a description in `feelings.yaml`, in the integration's folder: what's going on inside her and how it sounds. When she's asked something, the integration includes just her current mood, energy and moment, so she's never distracted by the other twenty feelings she isn't having. It looks like this:

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

## What moves her

| When | What happens |
|---|---|
| **Morning** | She wakes Groggy. If your special-days calendar has something today, she's Cheerful and knows why ("It's Sam's birthday today"). Otherwise she wakes in a mood of her own, sometimes coloured by the weather. |
| **Every hour** | Energy follows her day: groggy just after waking, steady or wired through the day, a dip after lunch, drained near bedtime. Once her mood has lasted a few hours, it may drift, but only to a **neighbouring** mood, so she changes gradually the way a person does. Her energy, the weather and the house pull her toward some moods: rain toward Wistful or cozy-Content, everyone home toward Fond, an empty house toward Restless. |
| **Someone comes home** after hours away | Moment: Delighted, with who and how long. After a day or more, her mood turns Fond. |
| **The house is empty** for hours | Mood: Restless or Wistful. |
| **The weather turns** (rain, storm, snow, sun, fog, wind) and stays that way for a quarter of an hour | She may react: a storm can make her Uneasy, snow Cheerful. |
| **A worry sensor turns on** (leak, smoke, a door left open) for two minutes | Moment: Worried, naming what. It stays until the sensor clears, then she's Relieved. |
| **Someone treats her a certain way** | She records her own moment with her `record_feeling` tool (Pleased, Amused, Proud, Touched, Delighted, Exasperated, Offended), in Home Assistant or Open WebUI. |
| **Bedtime** | She's Drained. |
| **A few minutes pass** | A moment fades back to Nothing, unless she's still worried about something. |

You can set any of her dropdowns by hand at any time. Setting her mood by hand clears its old reason.

## Changing her feelings

### How one sounds

Edit its description in `feelings.yaml` (in `custom_components/lena` in your Home Assistant config folder), then reload the integration: Settings → Devices & services → Lena → ⋮ → **Reload**. Write it to her, describe both the inside and how it sounds, and keep it to two or three sentences.

### Adding or removing one

Add a name and description under `moods`, `energy` or `moments` in `feelings.yaml` and reload. It appears in her dropdowns, you can set it by hand or with `lena.feel`, and `record_feeling` offers new moments to her. The automatic drift only uses the moods it knows, which are listed in `brain.py`; to make a new mood part of her drift, add it there too. `python3 tools/check.py` checks that every feeling her drift uses is defined.

### How she drifts

In the integration's **Configure** dialog:

| Setting | What it does |
|---|---|
| She wakes up at, She winds down at | Her day, for her energy. |
| Missed after being away | How long someone has to be out for her to be delighted when they're back. |
| Lonely after the house is empty for | How long before an empty house makes her restless. |
| Weather sensitivity | The chance she reacts when the weather turns. |
| A mood lasts at least | How long a mood holds before it can drift. Moods with a reason stick. |
| Chance per hour a mood moves on | Once it has lasted long enough. |
| Reactions fade after | How long a moment lasts. |

Her temperament (which moods sit next to each other, and what pulls her where) is a set of short, commented lists at the top of `brain.py` in the integration's folder.
