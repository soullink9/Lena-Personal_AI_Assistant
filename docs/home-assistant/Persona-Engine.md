# Persona Engine (the blueprint)

[← Back to README](../../README.md)

The blueprint `ai_persona_engine.yaml` is one automation that keeps Lena's mood, energy, delivery and flavor changing in a believable way. It never talks to the model. It only writes to the five helpers; the prompt reads them.

## What fires it

| Trigger | What happens |
| --- | --- |
| Bedtime (default 22:00) | Energy is set to Exhausted |
| Morning start (default 06:00) | Energy is set to Lazy or Caffeinated |
| Every 3 hours (default `/3`) | Energy re-rolls, weighted by time of day |
| Every 4 hours (default `/4`) | Mood re-rolls, weighted by energy and time of day |
| Mood, energy or reaction changes | Delivery and Flavor are recalculated |
| Reaction unchanged for 10 minutes (default) | Reaction returns to Neutral |

## How a roll works

Each choice is a list of words where repeats mean higher odds. For example `['Lazy','Lazy','Lazy','Caffeinated']` makes Lazy three times as likely as Caffeinated.

1. **Persistence.** A number from 1 to 100 is rolled. If it is within the persistence chance (default 35% for energy, 30% for mood), nothing changes. This gives moods some staying power.
2. **Build the pool.** Energy uses a pool for the time of day. Mood starts from a base pool, then adds extra words for the current energy, then extra words for the time of day.
3. **Clean the pool.** The current value is removed so she always changes to something different, and any word the helper does not offer is removed.
4. **Pick one.** If the pool is empty (for example overnight when she is already Exhausted), the current value is kept.

## Delivery and flavor

These are derived, not rolled freely. Energy, mood and reaction each add candidate words to a pool, and one is picked at random. If no rule matched, a safe default is used, so an unexpected value such as `unavailable` cannot cause an error.

## Changing the behavior

All the lists are plain text in the blueprint's actions section, with comments.

- Make a mood more common: add more copies of it to `base_mood_pool`.
- Make her grumpier at night: add `'Angry'` to the night branch of `time_adjusted_pool`.
- Change how long moods last: raise the persistence chance, or lengthen the interval (`/6` is every six hours).
- Add a new value: add it to the helper, to the pools, and to the legend in `lena_prompt.txt`. `python3 tools/check.py` will tell you if the prompt is missing a word.

## Reaction

Nothing in this repository sets Reaction. It exists so your own automations can: for example a script that sets it to Impressed after a successful command, or Protective when a door is left open. Whatever sets it, the blueprint will fold it into Delivery and Flavor and return it to Neutral after the quiet period.

## Requirements

Home Assistant 2024.10 or newer (the blueprint uses the plural `triggers:` and `actions:` syntax).
