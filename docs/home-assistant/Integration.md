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
| Action | `lena.announce` | Says a message on Lena's displays, like Home Assistant Voice's announcements: choose displays (all of them if none), the message, and **Listen for an answer** to have her listen afterwards. |
| Action | `lena.tidy_memory`, `lena.undo_memory_tidy` | She tidies her memory now (combining, clarifying, retiring outdated lines; see [Memory](Memory.md)) and says what changed; undo puts it back as it was. She also tidies on her own, every 7 days by default. |
| Device | Each Lena display | Every Lena app with Home Assistant set up registers itself as a device under Lena, named after the tablet or phone. Put it in a room (its **Area**) and it's room-aware. Delete one you no longer use. |
| Assist satellite | `assist_satellite.<display>` | Each display as Home Assistant's own voice-assistant device, like a Voice Preview Edition: `assist_satellite.announce` and `start_conversation`, Home Assistant's broadcast, its timers, and the wake word choice all reach the tablet. |
| AI choice | **Lena** under *Control Home Assistant* | Adds her context and tools to a conversation agent. Tick it together with **Assist**. |
| Address | `GET /api/lena/context` | Her context as text and JSON, for Open WebUI and the Lena app. Any user's token works. |
| Address | `GET /api/lena/media` | Home Assistant's media folders and photos, for the app's slideshow. |
| Address | `POST /api/lena/display` | Where each Lena app registers as a display and learns its room, the room's speakers and its Assist satellite. |

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

## Displays and rooms

Like a Home Assistant Voice satellite, each Lena app knows where it is once you put it in a room:

1. Open the Lena app with Home Assistant set up (Settings → Connections → Home Assistant). It registers itself; it shows under **Settings → Devices & services → Lena** as a device named after the tablet.
2. Open that device and set its **Area**, the same as any device.

Then:

- **Room-aware commands:** with **Who answers** set to Assist, every question is sent with the display's device, so Home Assistant knows the room: "turn on the lights" means that room's lights. With Open WebUI, she's told the room with each question.
- **Music:** the app learns the room's media players (Music Assistant's first). Music, radio or podcasts asked for without naming a room play there (or on the speaker chosen in the app, Settings → Connections → Home Assistant → **Music plays on**). Name another room, or the whole home, and she finds the speakers there: Assist's own Music Assistant commands do this by area, and with Open WebUI she uses her Home Assistant tools.
- **Announcements:** each display is an Assist satellite, so Home Assistant's own `assist_satellite.announce` and `assist_satellite.start_conversation` (and its "broadcast" in Assist) work on it: the chime, then Lena says the message in her own voice, or plays the sound you sent; starting a conversation then listens, and the answer carries on that conversation. `lena.announce` does the same by room name (**room**), and needs no text-to-speech engine in Home Assistant. A wall display says it within a few seconds while Lena is on screen; during a conversation, once the conversation ends.
- **Timers:** Home Assistant's own. Each display is a satellite with timers, so "set a 10 minute timer" in Assist uses Home Assistant's timer for that display, and "how long is left?" and "cancel the timer" are Home Assistant's too. The display keeps a copy that rings on time even if it loses its connection, shows the countdown, and cancelling it there cancels it in Home Assistant.
- **Wake word:** the satellite's page in Home Assistant lists the app's wake words; choosing one there sets it on the display.

## Her tools on the displays

Her tools in Assist (and the same ones in the Open WebUI tool) act on a display: the room named, else the one she was asked through, else the only one there is; with several and no room, she asks which.

| Tool | Action | What it does |
|---|---|---|
| (Assist's own timer tools) | `lena.set_timer` | Home Assistant's own timer for the display (seconds, an optional name), through its timer intents. In Assist she uses Home Assistant's timer tools directly; the action is for Open WebUI and automations. |
| `set_alarm` | `lena.set_alarm` | An alarm at the next time the clock shows `HH:MM`. |
| `set_reminder` | `lena.set_reminder` | She says the message at a time (`19:00`, or a date and time up to a week away) or after `in_seconds`. |
| `cancel_alarm_or_reminder`, `list_alarms_and_reminders` | `lena.cancel_timer`, `lena.list_timers` | Alarms and reminders from what each display reports it has (the one named, all of a kind, or everywhere). The actions also cover Home Assistant's timers on the display, through its timer intents. |
| `set_volume` | `lena.set_volume` | Her speaking volume: a level (0 to 100) or quieter / louder. On a display that speaks through a Home Assistant speaker, that speaker's volume. |
| `announce` | `lena.announce` | The intercom: says something in another room, optionally listening for an answer there. |
| `update_fact` | `lena.update_fact` | Corrects a memory in place. |
| `house_history` | `lena.history` | `last_change` (when the garage door last opened, and for how long), `how_long` (the latest time, and the total today), `what_happened` (people, doors, windows, locks, covers, alarms and leaks since midnight). From the recorder; only what's exposed to Assist. |

Displays collect what's asked of them from `POST /api/lena/display/sync` (a wall display every few seconds, any display right after an answer), and report their timers, alarms, reminders and volume there. Anything not collected within 15 minutes is dropped.

## Speaking first

A display with a **Speaking first** sensor (chosen in the app) asks `POST /api/lena/display/briefing` when someone comes in. The answer is usually nothing; it's the morning briefing once a day per display (from her morning time until noon), or a worry she hasn't mentioned there yet, and never within half an hour of the last time. The briefing is written in her words by the conversation agent chosen for alerts (Configure), or Home Assistant's AI Task, from the facts: today, your weather station, the forecast, today's calendar events, sensors named for trash or recycling, and anything that needs attention. What's been said is kept across restarts.

```yaml
action: lena.announce
data:
  message: Dinner's ready!
  device_id: [<the kitchen display's device>]
```

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

## /api/lena/display

```
POST /api/lena/display   {"id": "<the app's own id>", "name": "Kitchen tablet"}
```

Registers the app as a display the first time (and adds its Assist satellite), then just reads it. Returns its `device_id`, `name`, `area` (`{"id", "name"}`, or null), the room's `speakers` (`entity_id`, `name`, `music_assistant`; Music Assistant's first) and its `satellite`. Any user's token works.

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
