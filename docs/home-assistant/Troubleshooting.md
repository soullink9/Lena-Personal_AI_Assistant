# Troubleshooting

[← Back to Home Assistant installation](Installation.md)

Problems met while building this project, with their fixes.

## She sounds like a generic assistant

Example: "Sure thing! How can I help you today?" Her character isn't reaching the model.

1. Run the test from [Getting started, part 1](../Getting-Started.md#1-her-model). If she fails it, the model was built from an older Modelfile with `SYSTEM` instead of `MESSAGE system`, or not rebuilt after editing. Run `ollama create lena -f Modelfile` again.
2. Check the app is using the `lena` model, not its base model (`gemma4:12b`).
3. In Open WebUI, check the `lena` model's System Prompt is empty, or at least doesn't describe a different personality.
4. Make sure the context window is large enough (see [Performance tuning](Performance-Tuning.md)). Her character is never trimmed, but a tiny window crowds out everything else.

## The prompt fails to render ("Error rendering prompt")

Usually `lena.jinja` isn't where Home Assistant looks. It must be at `config/custom_templates/lena.jinja`, and you need to run **Reload custom Jinja2 templates** (Developer tools → Actions) after copying or changing it. Test with `{% from 'lena.jinja' import feelings %}{{ feelings() }}` in Developer tools → Template.

## Her feelings don't change

- Check the **Lena Mood Engine** automation exists, is enabled, and has the right five helpers. Open its trace.
- Her mood only drifts once it has lasted the "Mood lasts at least" time, and then only with the hourly chance you set. Change it by hand to see her react straight away.
- A name the engine picks but a dropdown doesn't offer is skipped. Compare the dropdown options with [Helpers](Helpers.md); they're case-sensitive.
- Reasons cut off or not saved: set the Text helpers' maximum length to 255.

## Her feelings don't show in Open WebUI or the app

See [Lena in Open WebUI](../open-webui/Setup.md#if-somethings-wrong). In short: the filter must be on and ticked on the `lena` model, its Home Assistant address must work from inside the Open WebUI container, and rendering feelings needs an admin's token.

## She overdoes a mood

Make the description in `lena.jinja` calmer, or lower the mood engine's change chance so big moods are rarer. If she names her mood out loud a lot ("I'm feeling grumpy"), remove the mood's name from its own description.

## She calls Lena feel all the time, or never

- All the time: smaller models over-use tools. Lower the Modelfile's temperature, or don't expose **Lena feel** to Assist.
- Never: check the script is exposed to Assist (in Home Assistant) or the tool is ticked on the model with Function Calling set to Native (in Open WebUI). She's told to use it only when something genuinely lands, so a plain request won't trigger it.

## She says she saved a memory but nothing is in the list

The model didn't make the tool call. Check the to-do list is exposed to Assist (Home Assistant) or the Lena tool is ticked on the model (Open WebUI), then try again. Some small models claim success without calling the tool; a lower temperature can help.

## She doesn't remember things in Home Assistant

She reads her memory with the **Lena recall** script, which must be exposed to Assist. She checks when she thinks it matters; ask "what do you remember about Alex?" to test it directly.

## Wrong light turns on, or she cannot find a device

Give every exposed entity a distinct name. Several entities called "Light Switch" in different rooms confuse the model. Rename them to "Kitchen Light", "Office Light" and so on, and expose fewer entities.

## The voice has background noise

See [Piper](../voice/Piper.md).

## Replies are slow

See [Performance tuning](Performance-Tuning.md).
