# The Lena integration

[← Back to Home Assistant installation](Installation.md)

What the integration creates, what she's told, and what else can use it. Everything lives in `custom_components/lena`.

## What it creates

| | Name | Notes |
|---|---|---|
| Dropdown | `select.lena_mood` | Her mood. Attributes: `reason`, `since`. Set it by hand and the old reason is cleared. |
| Dropdown | `select.lena_energy` | Her energy. |
| Dropdown | `select.lena_moment` | Her reaction to something that just happened, or **Nothing**. Attributes: `reason`, `since`. Fades on its own. |
| To-do list | `todo.lena_memory` | Her memory. Add, edit, reorder and delete items like any to-do list. Tick one off to retire it; she only uses open items. |
| Action | `lena.feel` | Sets her moment, with a reason. For your own automations, and used by Open WebUI. |
| Action | `lena.add_event` | Adds an event to a calendar with Home Assistant's `calendar.create_event`: a calendar's name, a title, a start (`2026-11-14` for all day, `2026-11-14T15:30` at a time), and optionally an end, a location and a description. Returns what was added. Used by Open WebUI's Lena tool. |
| AI choice | **Lena** under *Control Home Assistant* | Adds her context and tools to a conversation agent. Tick it together with **Assist**. |
| Address | `GET /api/lena/context` | Her context as text and JSON, for Open WebUI and the Lena app. Any user's token works. |
| Address | `GET /api/lena/media` | Home Assistant's media folders and photos, for the app's slideshow. |

Her feelings and memory are saved, so they survive restarts.

## What she's told

With **Assist** and **Lena** ticked, each request to the model carries, in this order:

1. **Her character**, from the Modelfile's `MESSAGE system`. Always first, whatever Home Assistant sends.
2. **Your agent's Instructions**, if you wrote any. Leave them empty unless there's something specific to your house you want her to know.
3. **Assist's part**: the devices and areas she can see, and how to control them.
4. **Lena's part**:
   - that her replies are usually spoken;
   - the date and time, said so it can't be misread (the part of the day, and whether it's light or dark out, with the next sunrise or sunset from Home Assistant's sun), and who's home;
   - how she feels right now: only her current mood, energy and moment, each with its description from `feelings.yaml` and the reason;
   - what she remembers, and when to remember or forget something.
5. **Tools**: Assist's device tools, plus `remember_fact`, `forget_fact`, `record_feeling` and, when there's a calendar she can write to, `add_calendar_event`.

### Calendars

Home Assistant's own tools let her **read** calendars but not add to them, so asking her to put something on the calendar used to fail. `add_calendar_event` fills that gap: it adds the event with Home Assistant's `calendar.create_event` action, the same as adding one in the calendar's own screen.

- She can add to calendars that are **exposed to Assist** and **take new events** (a Local Calendar, Google Calendar, CalDAV and the like; holiday and other read-only calendars can't), by their names as shown in Home Assistant.
- Times are the house's time zone. An all-day event is given by days, and its end is the last day it covers ("Friday to Sunday"); a timed event without an end lasts an hour, as in Home Assistant's own dialog.
- If something's wrong (a calendar name she got wrong, an end before the start, the calendar refusing), the tool tells her why so she can fix it or tell you, and nothing is added.

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
  "context": "## Right now\nIt's Tuesday, October 27, 2026, 6:40 PM: evening, and dark out (the sun rises at 7:12 AM).\nAt home: Alex.\n\n## How you feel right now\n...",
  "feelings": {"mood": "Grumpy", "mood_reason": "It just started raining.", "energy": "Steady", "moment": "Nothing", "moment_reason": ""},
  "memory": ["Alex likes the office at seventy degrees."],
  "weather": {"condition": "rainy", "temperature": 52, "unit": "°F"}
}
```

Add `?memory=0` to leave her memory out of `context`, `?reasons=0` to leave out the reasons, or `?devices=1` to add **Your home**: the exact names (and aliases) of everything exposed to Assist, by room and kind, without states, for Open WebUI, whose house tools match names exactly. It lists up to 400 (calendars included, since their tool wants the exact name), skipping scripts and to-do lists, which have tools of their own. Any user's token works; no admin rights are needed.

## /api/lena/media

For the Lena app's photo slideshow: Home Assistant's media browser (the same one as Media in the sidebar), without the websocket.

```
GET /api/lena/media                                  the top level: Immich, My media...
GET /api/lena/media?id=media-source://immich/...     a folder: {"title", "children": [{"id", "title", "folder", "photo"}]}
GET /api/lena/media?id=...&photos=1                  its photos: {"title", "photos": ["/immich/.../preview/image/jpeg", ...]}
```

Photos are paths on this Home Assistant (fetch them with the same token). Immich photos come at its screen-sized "preview" quality. Folders and photos only; videos and other files are left out. Any user's token works.

## Tests

`home-assistant/tests` runs the integration in a real Home Assistant on every change (`.github/workflows/home-assistant.yml`), against the newest release its test harness supports and 2026.9. To run them yourself:

```
pip install pytest-homeassistant-custom-component
cd home-assistant
python -m pytest
```

The same workflow also:

- starts the newest Home Assistant on PyPI with Lena added (`home-assistant/startup_check.py`), so a new release is tried the day it's out, before its test harness is published: she must load, `/api/lena/context` must answer with a token, and nothing Home Assistant logs about her may be an error;
- runs Home Assistant's own integration checks (hassfest) and HACS's;
- lists anything Home Assistant says Lena uses that's deprecated, as warnings on the run.
