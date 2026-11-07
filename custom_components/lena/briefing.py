"""Speaking first: what Lena says when someone comes into a room with one of her displays.

The display watches its own motion sensor (chosen in the app) and asks here (POST
/api/lena/display/briefing) whenever someone comes in. The answer is usually nothing. It's something:

- In the morning (from her morning time in Configure until noon), once a day per display: a short
  greeting and briefing (the weather, from your own weather sensors first, today's calendar, trash or
  recycling day, anything that needs attention).
- When she's worried about something at home that she hasn't mentioned on that display yet: once.

Never more than once every half hour on the same display, so walking in and out doesn't make her repeat
herself. What's been said is kept, so a restart of Home Assistant doesn't bring the morning briefing back.
"""

from __future__ import annotations

import asyncio
from datetime import datetime, timedelta
import logging
from typing import Any

from aiohttp import web

from homeassistant.core import HomeAssistant
from homeassistant.helpers import area_registry as ar
from homeassistant.helpers.http import KEY_HASS, HomeAssistantView
from homeassistant.helpers.storage import Store
from homeassistant.util import dt as dt_util

from .const import CONF_ALERT_AGENT, CONF_TIDY_TASK, DOMAIN
from .displays import display_id_of, displays

_LOGGER = logging.getLogger(__name__)

QUIET_MINUTES = 30
TRASH_WORDS = ("trash", "garbage", "recycl", "rubbish", "refuse", "bin ", "bins", "yard waste", "compost")


class Briefings:
    """What's been said first on each display, kept between restarts."""

    def __init__(self, hass: HomeAssistant) -> None:
        self.hass = hass
        self._store: Store = Store(hass, 1, f"{DOMAIN}.briefings")
        self._said: dict[str, dict[str, Any]] = {}  # device id -> {"morning": date, "worry": key, "last": iso}
        self._lock = asyncio.Lock()

    async def async_load(self) -> None:
        self._said = (await self._store.async_load() or {}).get("displays", {})

    def _save(self) -> None:
        self._store.async_delay_save(lambda: {"displays": self._said}, 5)

    async def something_to_say(self, core, device) -> str:
        """What to say on [device] as someone comes in, or "" (most of the time)."""
        async with self._lock:  # two sensors at once never both get the briefing
            now = dt_util.now()
            said = self._said.setdefault(device.id, {})
            last = dt_util.parse_datetime(said.get("last") or "")
            if last and now - last < timedelta(minutes=QUIET_MINUTES):
                return ""
            text = ""
            f = core.feelings
            worry = f"{f.moment_reason}|{f.moment_since.isoformat() if f.moment_since else ''}" if f.moment == "Worried" else None
            morning_start = core._wake_bed()[0]
            if worry and said.get("worry") != worry:
                said["worry"] = worry
                text = await core._in_her_words("Worried", f.moment_reason) or f"Something needs attention: {f.moment_reason}"
            elif morning_start <= now.time() < datetime.strptime("12:00", "%H:%M").time() and said.get("morning") != now.date().isoformat():
                said["morning"] = now.date().isoformat()
                text = await morning_briefing(core, device)
            if text:
                said["last"] = now.isoformat()
                self._save()
            return text


async def facts(core, device) -> list[str]:
    """What a morning briefing can mention: today, the weather (your own sensors first), today's calendar,
    trash and recycling, and any worry.
    """
    hass = core.hass
    now = dt_util.now()
    out = [f"It's {now.strftime('%A, %B')} {now.day}, {now.strftime('%I:%M %p').lstrip('0')}."]
    if device.area_id and (area := ar.async_get(hass).async_get_area(device.area_id)):
        out.append(f"They just came into the {area.name}.")
    if local := core.local_weather():
        out.append("Outside right now, from the house's own weather station: " + "; ".join(local) + ".")
    if weather := core.weather_entity:
        state = hass.states.get(weather)
        if state and state.state not in ("unavailable", "unknown"):
            out.append(f"Forecast service says: {state.state.replace('-', ' ')}"
                       + (f", {state.attributes.get('temperature')}{state.attributes.get('temperature_unit', '')}" if state.attributes.get("temperature") is not None else "")
                       + ".")
        try:
            async with asyncio.timeout(10):
                reply = await hass.services.async_call("weather", "get_forecasts", {"entity_id": weather, "type": "daily"},
                                                       blocking=True, return_response=True)
            today = ((reply or {}).get(weather) or {}).get("forecast") or []
            if today:
                day = today[0]
                bits = [f"high {day.get('temperature')}" if day.get("temperature") is not None else "",
                        f"low {day.get('templow')}" if day.get("templow") is not None else "",
                        f"{day.get('precipitation_probability')}% chance of rain" if day.get("precipitation_probability") is not None else "",
                        str(day.get("condition") or "").replace("-", " ")]
                out.append("Today's forecast: " + ", ".join(b for b in bits if b) + ".")
        except Exception:  # noqa: BLE001 - not every weather service gives a daily forecast
            pass
    events = await todays_events(hass)
    out.append(("On the calendar today: " + "; ".join(events) + ".") if events else "Nothing on the calendar today.")
    for state in hass.states.async_all():
        name = state.name.lower()
        if state.domain in ("sensor", "binary_sensor", "calendar") and any(w in name + " " for w in TRASH_WORDS) \
                and state.state not in ("unavailable", "unknown"):
            out.append(f"{state.name}: {state.state}{' ' + state.attributes['unit_of_measurement'] if state.attributes.get('unit_of_measurement') else ''}.")
    if core.feelings.moment == "Worried" and core.feelings.moment_reason:
        out.append(f"Needs attention: {core.feelings.moment_reason}.")
    return out


async def todays_events(hass: HomeAssistant) -> list[str]:
    """Every calendar's events for the rest of today, "3:00 PM Dentist", all-day ones first."""
    calendars = [s.entity_id for s in hass.states.async_all("calendar")]
    if not calendars:
        return []
    now = dt_util.now()
    end = dt_util.start_of_local_day() + timedelta(days=1)
    try:
        async with asyncio.timeout(10):
            reply = await hass.services.async_call("calendar", "get_events", {
                "entity_id": calendars, "start_date_time": now.isoformat(), "end_date_time": end.isoformat(),
            }, blocking=True, return_response=True)
    except Exception:  # noqa: BLE001 - the briefing goes on without the calendar
        _LOGGER.debug("Couldn't read today's calendar", exc_info=True)
        return []
    found: list[tuple[str, str]] = []
    for result in (reply or {}).values():
        for event in result.get("events", []):
            start = event.get("start", "")
            if "T" in start:
                when = dt_util.parse_datetime(start)
                found.append((start, f"{dt_util.as_local(when).strftime('%I:%M %p').lstrip('0')} {event.get('summary', '')}" if when else event.get("summary", "")))
            else:
                found.append(("", f"all day: {event.get('summary', '')}"))
    return [text for _, text in sorted(found)][:10]


async def morning_briefing(core, device) -> str:
    """The morning greeting and briefing, in her own words: through her conversation agent (Configure:
    alerts in her words) if one is chosen, else Home Assistant's AI Task, else plainly from the facts.
    """
    lines = await facts(core, device)
    prompt = (
        "It's morning and someone just walked into the room where your wall display is. Greet them and give a short "
        "spoken briefing: two to four natural sentences, no lists, no emoji, nothing made up. Mention the weather (use "
        "the house's own weather station for what it's like now), anything on the calendar today, and trash or recycling "
        "only if it's due today or tonight. Leave out anything unremarkable. Reply with only what you'd say.\n\n"
        + "\n".join(lines)
    )
    hass = core.hass
    try:
        async with asyncio.timeout(60):
            if agent := core.opt(CONF_ALERT_AGENT):
                result = await hass.services.async_call("conversation", "process", {"text": prompt, "agent_id": agent},
                                                        blocking=True, return_response=True)
                text = (result or {}).get("response", {}).get("speech", {}).get("plain", {}).get("speech", "")
            elif hass.services.has_service("ai_task", "generate_data"):
                request = {"task_name": "Lena's morning briefing", "instructions": "You are Lena, the household's assistant. " + prompt}
                if task := core.opt(CONF_TIDY_TASK):
                    request["entity_id"] = task
                result = await hass.services.async_call("ai_task", "generate_data", request, blocking=True, return_response=True)
                text = str((result or {}).get("data") or "")
            else:
                text = ""
        if text.strip():
            return text.strip().strip('"')[:600]
    except Exception:  # noqa: BLE001 - the plain briefing is a fine fallback
        _LOGGER.debug("Couldn't write the briefing in her words", exc_info=True)
    return "Good morning. " + " ".join(l for l in lines[1:] if not l.startswith("They just came"))[:600]


class LenaBriefingView(HomeAssistantView):
    """POST /api/lena/display/briefing {"id"}: someone came into the display's room. Answers {"text"}:
    what to say, or "" for nothing.
    """

    url = "/api/lena/display/briefing"
    name = "api:lena:display:briefing"
    requires_auth = True

    async def post(self, request: web.Request) -> web.Response:
        hass = request.app[KEY_HASS]
        core = hass.data.get(DOMAIN)
        if core is None:
            return self.json_message("Lena isn't set up", status_code=404)
        try:
            body = await request.json()
        except ValueError:
            return self.json_message("Expected JSON", status_code=400)
        device = next((d for d in displays(hass, core.entry.entry_id) if display_id_of(d) == str(body.get("id") or "")), None)
        if device is None:
            return self.json_message("Not a registered display", status_code=404)
        return self.json({"text": await core.briefings.something_to_say(core, device)})
