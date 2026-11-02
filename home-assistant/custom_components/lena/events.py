"""Adding events to your calendars for her.

Home Assistant gives conversation agents a tool to read calendars but none to write to them, so
when someone asks her to put something on the calendar she has nothing to do it with. This adds
it, through Home Assistant's own calendar.create_event action, in two places that share the code
below:

- the add_calendar_event tool, for Home Assistant's agents (llm_api.py), and
- the lena.add_event action, which Open WebUI's Lena tool calls over REST (lena_tools.py).

She may add to the calendars exposed to Assist that accept new events (local calendars, Google,
CalDAV and the like; read-only ones such as holidays don't). She names a calendar as it's shown,
and times as the local time of the house, the way people say them.
"""

from __future__ import annotations

from datetime import date, datetime, timedelta
from typing import Any

from homeassistant.core import Context, HomeAssistant
from homeassistant.exceptions import HomeAssistantError
from homeassistant.util import dt as dt_util

from .compat import vol

# CalendarEntityFeature.CREATE_EVENT; a plain number, so this file doesn't depend on the calendar
# integration being loaded.
CREATE_EVENT = 1

# A timed event with no end lasts an hour, as in Home Assistant's own calendar dialog.
DEFAULT_LENGTH = timedelta(hours=1)


class EventError(Exception):
    """Why an event couldn't be added, in words she can pass on or correct."""


def writable_calendars(hass: HomeAssistant) -> dict[str, str]:
    """The calendars she may add to, by name: exposed to Assist and able to take new events.

    If Home Assistant's exposure settings can't be read (they always can in a running Home
    Assistant), every calendar that takes new events counts.
    """
    try:
        from homeassistant.components.homeassistant.exposed_entities import async_should_expose
    except ImportError:
        async_should_expose = None

    calendars: dict[str, str] = {}
    for state in hass.states.async_all("calendar"):
        features = state.attributes.get("supported_features", 0) or 0
        if not features & CREATE_EVENT:
            continue
        if async_should_expose is not None:
            try:
                if not async_should_expose(hass, "conversation", state.entity_id):
                    continue
            except KeyError:  # exposure settings not loaded: fall back to every writable calendar
                pass
        calendars[state.name] = state.entity_id
    return calendars


def _pick_calendar(calendars: dict[str, str], name: str | None) -> tuple[str, str]:
    """The calendar she meant, as (name, entity id): an exact name, ignoring case, or the only one."""
    if not calendars:
        raise EventError(
            "There's no calendar you can add to. A calendar must be exposed to Assist and accept new events."
        )
    wanted = (name or "").strip().lower()
    if not wanted and len(calendars) == 1:
        return next(iter(calendars.items()))
    for calendar_name, entity_id in calendars.items():
        if calendar_name.lower() == wanted:
            return calendar_name, entity_id
    choices = ", ".join(sorted(calendars))
    raise EventError(f"No calendar is called \"{name}\". Use one of these names exactly: {choices}.")


def _when(value: str, field: str) -> date | datetime:
    """A day ("2026-11-14") or a time of day ("2026-11-14T15:30"), read as the house's local time."""
    text = (value or "").strip()
    if not text:
        raise EventError(f"\"{field}\" is empty.")
    has_time = "T" in text or " " in text or ":" in text
    if not has_time:
        day = dt_util.parse_date(text)
        if day is None:
            raise EventError(f"\"{field}\" isn't a date like 2026-11-14: \"{value}\".")
        return day
    moment = dt_util.parse_datetime(text.replace(" ", "T", 1))
    if moment is None:
        raise EventError(f"\"{field}\" isn't a date and time like 2026-11-14T15:30: \"{value}\".")
    if moment.tzinfo is None:
        moment = moment.replace(tzinfo=dt_util.get_default_time_zone())
    return moment


def _times(start: str, end: str | None) -> dict[str, str]:
    """The start and end as create_event wants them.

    An all-day event is given by days, and its end is the last day it covers (people say "Friday to
    Sunday"); Home Assistant's end date is the day after, so one day is added. A timed event without
    an end lasts an hour.
    """
    begins = _when(start, "start")
    ends = _when(end, "end") if end and end.strip() else None

    if not isinstance(begins, datetime):
        if isinstance(ends, datetime):
            raise EventError("An all-day event's end must be a day too, like 2026-11-16, without a time.")
        last_day = ends or begins
        if last_day < begins:
            raise EventError("The end is before the start.")
        return {"start_date": begins.isoformat(), "end_date": (last_day + timedelta(days=1)).isoformat()}

    if ends is not None and not isinstance(ends, datetime):
        raise EventError("A timed event's end needs a time too, like 2026-11-14T16:30.")
    finish = ends or begins + DEFAULT_LENGTH
    if finish <= begins:
        raise EventError("The end must be after the start.")
    return {"start_date_time": begins.isoformat(), "end_date_time": finish.isoformat()}


async def add_event(
    hass: HomeAssistant,
    context: Context | None,
    calendar: str | None,
    title: str,
    start: str,
    end: str | None = None,
    location: str | None = None,
    description: str | None = None,
) -> dict[str, Any]:
    """Adds one event with calendar.create_event and says what was added.

    Raises EventError with a message she can act on (a wrong calendar name, a badly written time)
    or pass on (the calendar refused it).
    """
    summary = (title or "").strip()
    if not summary:
        raise EventError("The event needs a title.")
    calendar_name, entity_id = _pick_calendar(writable_calendars(hass), calendar)

    data: dict[str, Any] = {"entity_id": entity_id, "summary": summary, **_times(start, end)}
    if location and location.strip():
        data["location"] = location.strip()
    if description and description.strip():
        data["description"] = description.strip()

    try:
        await hass.services.async_call("calendar", "create_event", data, blocking=True, context=context)
    except (HomeAssistantError, vol.Invalid, ValueError) as error:  # refused, or the schema didn't take it
        raise EventError(f"{calendar_name} didn't accept it: {error}") from error

    added = {"added": summary, "calendar": calendar_name}
    added["start"] = data.get("start_date_time") or data["start_date"]
    added["end"] = data.get("end_date_time") or data["end_date"]
    if "start_date" in data:
        added["note"] = "All day; the end is the day after the last day, as calendars store it."
    return added
