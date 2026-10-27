# Helpers

[← Back to Home Assistant installation](Installation.md)

Lena's feelings live in five helpers, and her memory in a to-do list. Create them all in the Home Assistant UI: Settings → Devices & services → Helpers → **Create helper**. The mood engine writes the feelings, her prompt reads them, and you can change them by hand at any time.

Names are **case-sensitive** and must match `custom_templates/lena.jinja`. A name a dropdown doesn't offer is simply never chosen, so leaving one out just means she never feels it.

## Her feelings

| Create a | Name | Entity ID | Options / settings |
|---|---|---|---|
| Dropdown | Lena Mood | `input_select.lena_mood` | Content, Cheerful, Playful, Smug, Curious, Focused, Restless, Wistful, Grumpy, Uneasy, Fond |
| Dropdown | Lena Energy | `input_select.lena_energy` | Groggy, Steady, Wired, Drained |
| Dropdown | Lena Moment | `input_select.lena_moment` | Nothing, Pleased, Amused, Proud, Touched, Delighted, Exasperated, Offended, Worried, Relieved |
| Text | Lena Mood Reason | `input_text.lena_mood_reason` | Maximum length **255** |
| Text | Lena Moment Reason | `input_text.lena_moment_reason` | Maximum length **255** |

- **Mood** is how she is today; it lasts hours.
- **Energy** follows her day: groggy in the morning, drained at night.
- **Moment** is a reaction to something that just happened. **Nothing** means no reaction; it's listed first so it's the starting value.
- The two **Reason** helpers hold why she feels that way ("It just started raining."), so she can mention it. Text helpers default to a maximum of 100 characters; set it to 255 under **Advanced settings** when you create them.

Home Assistant builds the entity ID from the name. If yours come out different, check them in Developer tools → States and pass them to `feelings()` in her prompt. What each feeling means and what moves her between them: [Her feelings](../personality/Moods.md).

## Her memory (optional)

Settings → Devices & services → Add integration → **Local To-do**, named `Lena's Memory`. It usually becomes `todo.lena_s_memory`, because Home Assistant turns the apostrophe into an underscore. Check in Developer tools → States.

This one list is her memory everywhere: in Home Assistant, in Open WebUI and in the Lena app. See [Memory](Memory.md).
