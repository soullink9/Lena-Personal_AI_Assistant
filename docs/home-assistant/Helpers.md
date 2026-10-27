# Helpers

[← Back to README](../../README.md)

The blueprint reads and writes five dropdown helpers, and the prompt reads them back. Create them in the Home Assistant UI: Settings > Devices & services > Helpers > Create helper > **Dropdown**.

Option names are **case-sensitive**. If a helper lacks an option the blueprint wants to use, that option is skipped silently, so a missing word just means she never uses it.

## The five dropdowns

Suggested names and entity IDs (the prompt uses these IDs; change them in the prompt if yours differ):

| Name | Entity ID | Options |
| --- | --- | --- |
| Lena's Mood | `input_select.lenas_mood` | Content, Confident, Interested, Appreciative, Joyful, Surprised, Disgusted, Anxious, Angry, Sad |
| Lena's Energy Level | `input_select.lenas_energy_level` | Exhausted, Lazy, Caffeinated, Hyperactive |
| Lena's Reaction | `input_select.lenas_reaction` | Neutral, Irritated, Frustrated, Impressed, Curious, Protective |
| Lena's Delivery | `input_select.lenas_delivery` | Deadpan, Dry, Witty, Sharp, Smug, Playful, Warm, Subtle, Dramatic |
| Lena's Delivery Flavor | `input_select.lenas_delivery_flavor` | Mischievous, Observational, Curious, Philosophical, Protective, Playful, Dramatic, Subtle |

Home Assistant builds the entity ID from the name when you create the helper. Check the real ID in Developer tools > States.

### What each one means

- **Mood**: her emotional tone. Changes every few hours.
- **Energy**: how much effort and speed shows. Follows the time of day.
- **Reaction**: her attitude to the most recent request. Nothing in this repository sets it; use your own scripts or automations if you want it to react to events. It returns to Neutral on its own.
- **Delivery**: how she phrases things. Derived from the other three.
- **Delivery flavor**: the extra touch she adds. Derived from the other three.

The prompt has a one-line meaning for every option. If you add an option, add its meaning to the prompt too, or the model has to guess. `tools/check.py` warns if a word used by the blueprint is missing from the prompt.

## The memory list

Her memory is a to-do list. Create it with Settings > Devices & services > Add integration > **Local To-do**, and name it `Lena's Memory`. Check its entity ID in Developer tools > States; you will need it for the [memory sensor](Memory.md).
