# Troubleshooting

[← Back to Home Assistant installation](Installation.md)

Problems met while building this project, with their fixes.

## She sounds like a generic assistant

Example: "Sure thing! How can I help you today?" Her character isn't reaching the model.

1. Run the test from [Getting started, part 1](../Getting-Started.md#1-her-model). If she fails it, the model was built from an older Modelfile with `SYSTEM` instead of `MESSAGE system`, or not rebuilt after editing. Run `ollama create lena -f Modelfile` again.
2. In Home Assistant, check your agent's Instructions don't describe a different personality. They can be left empty.
3. Check the app is using the `lena` model, not its base model (`gemma4:12b`).
4. In Open WebUI, check the `lena` model's System Prompt is empty, or at least doesn't describe a different personality.
5. Make sure the context window is large enough (see [Performance tuning](Performance-Tuning.md)). Her character is never trimmed, but a tiny window crowds out everything else.

## Lena isn't in the Add integration list

The folder isn't where Home Assistant looks, or Home Assistant hasn't restarted since you copied it. It must be at `config/custom_components/lena/` with `manifest.json` directly inside. Restart, then refresh the browser page. Settings → System → Logs shows any error loading it.

## Lena shows "icon not available" instead of her logo

Check `config/custom_components/lena/brand/` came across with the rest of the folder, restart, then hard-refresh the browser (or clear the companion app's cache): images are cached for a long time.

## There's no Lena under Control Home Assistant

Add the Lena integration first (Settings → Devices & services → Add integration → Lena). The choice appears once it's set up.

## Her feelings don't change

- Open **Configure** on the Lena integration and check her day, people, weather and sensors.
- Her mood only drifts once it has lasted the "A mood lasts at least" time, and then only with the hourly chance you set. Set **Lena Mood** by hand to see her react straight away.
- Settings → System → Logs, filtered for `lena`, shows what moved her if you turn on debug logging for the integration (Settings → Devices & services → Lena → ⋮ → Enable debug logging).

## Alerts don't arrive

- The phone must appear under **Tell these phones when she's worried**; it does once the Home Assistant companion app is signed in on it. Test it from Developer tools → Actions with `notify.mobile_app_…`.
- The sensor has to stay on for two minutes before she worries, so a quick blip doesn't wake anyone.
- Alerts that arrive with the plain reason instead of her words: check the agent under **Write alerts in her words with** answers in Developer tools → Actions → `conversation.process`.

## Her feelings don't show in Open WebUI or the app

See [Lena in Open WebUI](../open-webui/Setup.md#if-somethings-wrong). In short: the filter must be on and ticked on the `lena` model, its Home Assistant address must work from inside the Open WebUI container, and the integration must be set up. Open `https://<your Home Assistant>/api/lena/context` with the token to test.

## I edited feelings.yaml and nothing changed

Reload the integration (Settings → Devices & services → Lena → ⋮ → Reload). If it then fails to load, the file isn't valid YAML any more: the log says where. Keep the two-space indents and the `>-` after each name.

## She overdoes a mood

Make its description in `feelings.yaml` calmer, or lower **Chance per hour a mood moves on** so big moods are rarer. If she names her mood out loud a lot ("I'm feeling grumpy"), take the mood's name out of its own description.

## She records feelings all the time, or never

- All the time: smaller models over-use tools. Lower the Modelfile's temperature.
- Never: check **Lena** is ticked under Control Home Assistant (in Home Assistant), or the Lena tool is ticked on the model with Function Calling set to Native (in Open WebUI). She's told to use it only when something genuinely lands, so a plain request won't trigger it.

## She says she saved a memory but nothing is in the list

The model didn't call the tool. Check **Lena** is ticked under Control Home Assistant (Home Assistant), or the Lena tool is ticked on the model (Open WebUI), then try again. Some small models claim success without calling the tool; a lower temperature can help.

## Wrong light turns on, or she cannot find a device

Give every exposed entity a distinct name. Several entities called "Light Switch" in different rooms confuse the model. Rename them to "Kitchen Light", "Office Light" and so on, and expose fewer entities.

## The voice has background noise

See [Piper](../voice/Piper.md).

## Replies are slow

See [Performance tuning](Performance-Tuning.md).
