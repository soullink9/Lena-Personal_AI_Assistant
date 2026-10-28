# The Lena integration

[← Back to Home Assistant installation](Installation.md)

What the integration creates, what she's told, and what else can use it. Everything lives in `home-assistant/custom_components/lena`.

## What it creates

| | Name | Notes |
|---|---|---|
| Dropdown | `select.lena_mood` | Her mood. Attributes: `reason`, `since`. Set it by hand and the old reason is cleared. |
| Dropdown | `select.lena_energy` | Her energy. |
| Dropdown | `select.lena_moment` | Her reaction to something that just happened, or **Nothing**. Attributes: `reason`, `since`. Fades on its own. |
| To-do list | `todo.lena_memory` | Her memory. Add, edit, reorder and delete items like any to-do list. Tick one off to retire it; she only uses open items. |
| Action | `lena.feel` | Sets her moment, with a reason. For your own automations, and used by Open WebUI. |
| AI choice | **Lena** under *Control Home Assistant* | Adds her context and tools to a conversation agent. Tick it together with **Assist**. |
| Address | `GET /api/lena/context` | Her context as text and JSON, for Open WebUI and the Lena app. Any user's token works. |

Her feelings and memory are saved, so they survive restarts.

## What she's told

With **Assist** and **Lena** ticked, each request to the model carries, in this order:

1. **Her character**, from the Modelfile's `MESSAGE system`. Always first, whatever Home Assistant sends.
2. **Your agent's Instructions**, if you wrote any. Leave them empty unless there's something specific to your house you want her to know.
3. **Assist's part**: the devices and areas she can see, and how to control them.
4. **Lena's part**:
   - that her replies are usually spoken;
   - the date and time, and who's home;
   - how she feels right now: only her current mood, energy and moment, each with its description from `feelings.yaml` and the reason;
   - what she remembers, and when to remember or forget something.
5. **Tools**: Assist's device tools, plus `remember_fact`, `forget_fact` and `record_feeling`.

You can see exactly what was sent in the voice assistant's debug view (Settings → Voice assistants → your assistant → ⋮ → Debug).

## Alerts

Under **Configure**, **Tell these phones when she's worried** lists your phones' companion-app notify actions (`notify.mobile_app_…`). When a worry sensor has been on for two minutes, each phone gets a notification titled **Lena**. With an agent chosen under **Write alerts in her words with**, she writes it herself ("Water under the sink. Go look, now."); without one, or if she takes longer than 45 seconds, it's the plain reason ("Leak sensor is detecting water."). When the sensor clears, an all-clear replaces it, unless you turn **Also tell them when it's sorted** off. Tapping either opens the Lena app, listening.

## Editing her feelings

`feelings.yaml` holds nothing but her feelings and their descriptions. Edit a description, then reload the integration (Settings → Devices & services → Lena → ⋮ → Reload). Names can be added or removed too: a new one appears in her dropdowns after a reload. See [Her feelings](../personality/Moods.md).

## Using her in automations

Her dropdowns are ordinary entities. Some ideas:

- Show her mood on a dashboard, with the `reason` attribute underneath.
- `lena.feel` with moment **Proud** and a reason when a long backup finishes.
- Turn a light warmer when she's **Fond**.

## /api/lena/context

```
GET /api/lena/context
Authorization: Bearer <a long-lived access token>
```

Returns:

```json
{
  "context": "## Right now\nIt's Tuesday, October 27, 2026, 6:40 PM.\nAt home: Alex.\n\n## How you feel right now\n...",
  "feelings": {"mood": "Grumpy", "mood_reason": "It just started raining.", "energy": "Steady", "moment": "Nothing", "moment_reason": ""},
  "memory": ["Alex likes the office at seventy degrees."],
  "weather": {"condition": "rainy", "temperature": 52, "unit": "°F"}
}
```

Add `?memory=0` to leave her memory out of `context`, or `?reasons=0` to leave out the reasons. Any user's token works; no admin rights are needed.

## Tests

`home-assistant/tests` runs the integration in a real Home Assistant on every change (`.github/workflows/home-assistant.yml`), against the newest release and 2026.9. To run them yourself:

```
pip install pytest-homeassistant-custom-component
cd home-assistant
python -m pytest
```
