# Troubleshooting

[← Back to README](../../README.md)

Problems met while building this project, with their fixes.

## She replies with generic assistant phrases

Example: "Sure thing. What's up?" That means the persona is not reaching the model, or the prompt is being cut off.

1. Open the assistant's debug view (Settings > Voice assistants > your assistant > Debug) and read the system prompt that was sent. Is the full text there?
2. Home Assistant may replace the Modelfile's system prompt with the agent's Instructions, so the Instructions must contain her character. `home-assistant/lena_prompt.txt` does.
3. Make sure the context window is large enough (see [Performance-Tuning](Performance-Tuning.md)). A 4096 context can drop the start of the prompt.

## The memory sensor does not load

Home Assistant config errors usually name the file and line. Common ones:

| Message | Cause and fix |
| --- | --- |
| `'template' is an invalid option for 'template'` | The file has its own `template:` key but is already included under `template:`. Remove the first line so the file starts with `- triggers:` |
| `'lena_memory' is an invalid option for 'template'` | You wrapped the list in a key of your own. Remove it. The `!include_dir_merge_named` variant also causes this; use `!include_dir_merge_list` |
| `'NodeListClass' object has no attribute 'keys'` | A list file was given to an include that expects a dictionary (the `_named` includes). Use `!include_dir_merge_list templates/` or a single `!include` |
| `Referenced entities todo.… are missing or not currently available` | The to-do entity ID in the sensor file is wrong. Look it up in Developer tools > States and replace all four occurrences |

If the sensor exists but has no `memories` attribute, an older sensor with the same name is still defined (look in Settings > Devices & services > Helpers and in your YAML files). Remove it, run the configuration check, then reload template entities.

The sensor stays `unknown` until a trigger fires. It fires when the list changes, on startup and on a template reload.

## She says she saved a memory but nothing is in the list

The model did not make the tool call. Check that the to-do list is exposed to Assist, then try again. Some small models claim success without calling the tool; a different base model or a lower temperature can help.

## Moods never change

- Check the automation exists and is enabled, and open its trace.
- Test by changing the mood helper by hand: Delivery and Flavor should update.
- If a value never appears, the helper may lack that option (names are case-sensitive).

## A blueprint step fails

Open the automation's trace. Errors from empty lists were fixed in 1.0.0; if you edit the pools, keep every pool non-empty.

## Wrong light turns on, or she cannot find a device

Give every exposed entity a distinct name. Several entities called "Light Switch" in different rooms confuse the model. Rename them to "Kitchen Light", "Office Light" and so on, and expose fewer entities.

## The voice has background noise

See [Voice-Piper](../voice/Piper.md).

## Replies are slow

See [Performance-Tuning](Performance-Tuning.md).
