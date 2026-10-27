# Prompt reference

[← Back to Home Assistant installation](Installation.md)

What Lena receives from Home Assistant, in the order the model sees it:

| # | Part | From | Contains |
|---|---|---|---|
| 1 | Her character | `ollama/Modelfile` (`MESSAGE system`) | Who she is, how she talks, honesty, running the house, how to act on feelings. Always first, whatever Home Assistant sends. |
| 2 | Her prompt | `home-assistant/lena_prompt.txt`, pasted into the agent's Instructions | She's speaking aloud, the time, who's home, how she feels, how to use her memory |
| 3 | Home Assistant's block | Home Assistant itself | The devices and areas she can see, and how to use its tools |
| 4 | Tools | Home Assistant | Device control, timers, to-do lists, and her exposed scripts (Lena feel, Lena recall) |
| 5 | The conversation | Home Assistant | What was said |

Because part 1 always arrives, part 2 doesn't repeat her character. It only adds what's true right now in this house.

## The prompt file

`lena_prompt.txt` is a template that Home Assistant renders before every request:

- `{{ ... }}` parts become live values: the time, the people at home, her feelings.
- `{# ... #}` parts are comments and are removed, so they cost nothing.
- `{% from 'lena.jinja' import feelings %}` loads her feelings from `config/custom_templates/lena.jinja`. If that file is missing the whole prompt fails to render, so copy it in first.

To preview it, paste the file into Developer tools → Template, or run `python3 tools/check.py` for a test render.

## Sections

| Section | Purpose |
|---|---|
| Opening line | Everything she says is spoken, so her spoken-reply rules apply |
| Right now | Day and time, and who's home (remove it if you don't use person entities) |
| How you feel right now | From `feelings()`: her current mood, energy and moment, each with its description and reason |
| Your memory | When to check, save and forget, using her shared to-do list (delete it if you don't use memory) |

## Different helper names

If your helpers' entity IDs differ from the defaults, pass them in:

```
{{ feelings(mood_entity='input_select.my_mood', energy_entity='input_select.my_energy',
            moment_entity='input_select.my_moment', mood_reason_entity='input_text.my_mood_reason',
            moment_reason_entity='input_text.my_moment_reason') }}
```

The Open WebUI filter uses the defaults. If you change them, change `FEELINGS_TEMPLATE` at the top of `open-webui/lena_filter.py` the same way.

## Editing tips

- Character changes go in the Modelfile, not here, so every app gets them.
- Keep it short. Every word is sent with every request, alongside Home Assistant's device list.
- After editing, test with a command, a question, a joke and a serious topic.
