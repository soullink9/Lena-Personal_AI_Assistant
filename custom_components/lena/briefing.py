"""Speaking first: what Lena says without being asked, when someone comes into a room with one of her displays.

The display watches its own motion sensor (chosen in the app) and asks here (POST
/api/lena/display/briefing) whenever someone comes in. The answer is usually nothing. It's something, in
this order, the first that applies:

- When she's worried about something at home that she hasn't mentioned on that display yet: once.
- Welcome home (Configure > Speaking first): someone she missed just got home; the first display that
  sees someone within half an hour says it.
- In the morning (from her morning time in Configure until noon), once a day per display: a short
  greeting and briefing (the weather, from your own weather sensors first, today's calendar, trash or
  recycling day, anything that needs attention).
- Before you head out: in the morning, on a display by the door (Configure: Entrances), when it's going
  to rain or snow today: once a day.
- A follow-up: something she was told to come back to ("the vet on Thursday") is due, and the person who
  told her is the only one home. With "Ask and listen" on, she asks and listens for the answer.
- A routine that's late today (routines.py).

Every one of them goes through one gate (brain.may_speak): never while someone's talking to her there,
never twice within half an hour on the same display, and, except for worries, not in a bedroom at night,
not while Do not disturb is on (switch.lena_do_not_disturb) and no more than the day's number (Configure).
What's been said is kept, so a restart of Home Assistant doesn't bring the morning briefing back, and the
last few are on the Lena Spoke first sensor with why.
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

from . import brain
from .const import CONF_ENTRANCES, CONF_FOLLOW_UP_LISTEN, CONF_SPEAK_DAILY, DOMAIN
from .displays import display_id_of, displays

_LOGGER = logging.getLogger(__name__)

TRASH_WORDS = ("trash", "garbage", "recycl", "rubbish", "refuse", "bin ", "bins", "yard waste", "compost")


class Briefings:
    """What's been said first on each display, kept between restarts."""

    def __init__(self, hass: HomeAssistant) -> None:
        self.hass = hass
        self._store: Store = Store(hass, 1, f"{DOMAIN}.briefings")
        # device id -> {"morning": date, "worry": key, "heading_out": date, "last": iso}
        self._said: dict[str, dict[str, Any]] = {}
        self._lock = asyncio.Lock()

    async def async_load(self) -> None:
        self._said = (await self._store.async_load() or {}).get("displays", {})

    def _save(self) -> None:
        self._store.async_delay_save(lambda: {"displays": self._said}, 5)

    async def something_to_say(self, core, device) -> str:
        """What to say on [device] as someone comes in, or "" (most of the time). A follow-up asked with
        listening is said through the display's announcement instead, so it can listen, and "" comes back.
        """
        async with self._lock:  # two sensors at once never both get the briefing
            now = dt_util.now()
            said = self._said.setdefault(device.id, {})
            reported = core.hub.reported.get(device.id) if core.hub else None
            busy = bool(reported and reported.phase not in (None, "idle"))
            spoken = spoken_today(core, now)

            def may(kind: str) -> bool:
                return brain.may_speak(
                    kind, now, dt_util.parse_datetime(said.get("last") or ""), spoken["count"],
                    int(core.opt(CONF_SPEAK_DAILY) or 0), core.quiet(device.id), busy,
                    bool(core.presence.get("do_not_disturb")),
                )

            kind, text, listen = "", "", False
            f = core.feelings
            worry = f"{f.moment_reason}|{f.moment_since.isoformat() if f.moment_since else ''}" if f.moment == "Worried" else None
            morning_start = core._wake_bed()[0]
            morning = morning_start <= now.time() < datetime.strptime("12:00", "%H:%M").time()
            welcome = [name for name, at in core.welcomes.items() if now - at < timedelta(minutes=WELCOME_MINUTES)]
            if worry and said.get("worry") != worry and may("worry"):
                kind, said["worry"] = "worry", worry
                text = await core._in_her_words("Worried", f.moment_reason) or f"Something needs attention: {f.moment_reason}"
            elif welcome and may("welcome"):
                kind = "welcome"
                for name in welcome:
                    core.welcomes.pop(name, None)
                text = await welcome_home(core, device, welcome)
            elif morning and said.get("morning") != now.date().isoformat() and may("morning"):
                kind, said["morning"] = "morning", now.date().isoformat()
                text = await morning_briefing(core, device)
            elif (morning and core.speaks_first("heading_out") and said.get("heading_out") != now.date().isoformat()
                  and device.area_id in (core.opt(CONF_ENTRANCES) or []) and may("heading_out")):
                said["heading_out"] = now.date().isoformat()  # looked at once a day, said or not
                text = await heading_out(core)
                kind = "heading_out" if text else ""
            elif core.speaks_first("follow_up") and (item := follow_up_for(core, now)) and may("follow_up"):
                kind, item["asked"] = "follow_up", now.isoformat()
                core.memory_changed()
                listen = bool(core.opt(CONF_FOLLOW_UP_LISTEN))
                text = await follow_up(core, item)
            elif core.speaks_first("routine") and core.routines and (late := core.routines.to_mention(now)) and may("routine"):
                kind = "routine"
                core.routines.mention(late[0])
                text = await routine_late(core, late[1], late[2], now)
            if text:
                said["last"] = now.isoformat()
                self._save()
                note_spoken(core, now, kind, device.name_by_user or device.name or "", text)
            if text and listen:
                core.hub.send([device], "announce", {"message": text, "listen": True, "chime": False})
                return ""
            return text


WELCOME_MINUTES = 30  # a welcome home waits this long for a display to see them


def spoken_today(core, now: datetime) -> dict[str, Any]:
    """Today's count of times she spoke first (worries not counted), and the last few, with why."""
    spoken = core.presence.setdefault("spoken", {})
    if spoken.get("date") != now.date().isoformat():
        spoken.update({"date": now.date().isoformat(), "count": 0})
    spoken.setdefault("log", [])
    return spoken


def note_spoken(core, now: datetime, kind: str, display: str, text: str) -> None:
    """Records what she said first, where and why, for the Lena Spoke first sensor."""
    spoken = spoken_today(core, now)
    if kind != "worry":
        spoken["count"] = int(spoken.get("count") or 0) + 1
    spoken["log"] = ([{"at": now.isoformat(), "kind": kind, "display": display, "text": text[:200]}]
                     + list(spoken.get("log") or []))[:LOG_SIZE]
    core.presence_changed()


LOG_SIZE = 10


def _area_name(core, device) -> str | None:
    """The room a display is in, or None."""
    area = ar.async_get(core.hass).async_get_area(device.area_id) if device.area_id else None
    return area.name if area else None


async def welcome_home(core, device, names: list[str]) -> str:
    """A short welcome home, in her words."""
    who = " and ".join(names)
    room = _area_name(core, device)
    prompt = (f"{who} just got home after a while away" + (f" and walked into the {room}" if room else "")
              + ". Welcome them home in one or two short spoken sentences, warmly, in your own voice. No emoji, "
              "nothing made up. Reply with only what you'd say.")
    return await core.in_her_words(prompt, task_name="Lena's welcome home") or f"Welcome home, {who}."


async def heading_out(core) -> str:
    """A heads-up by the door when the day's weather calls for it (rain, snow), or "" when it doesn't."""
    day = await todays_forecast(core)
    rain = day.get("precipitation_probability")
    condition = str(day.get("condition") or "")
    wet = (isinstance(rain, (int, float)) and rain >= 50) or condition in ("rainy", "pouring", "snowy", "snowy-rainy", "hail", "lightning-rainy")
    if not wet:
        return ""
    what = condition.replace("-", " ") or "rain"
    prompt = (f"Someone's by the front door in the morning, probably heading out. Today's forecast: {what}"
              + (f", {rain}% chance of rain" if isinstance(rain, (int, float)) else "")
              + ". In one short spoken sentence, in your own voice, tell them what to take (an umbrella, a coat). "
              "Reply with only what you'd say.")
    return await core.in_her_words(prompt, task_name="Lena's heads-up") or f"Take an umbrella: it's {what} today."


def follow_up_for(core, now: datetime) -> dict[str, Any] | None:
    """A due follow-up she can ask about now: told her by the one person who's home (or by no one in
    particular when someone's home), so she asks the right person.
    """
    home = [s.name for s in core.hass.states.async_all("person") if s.state == "home"]
    if not home:
        return None
    for item in core.memory:
        if not brain.follow_up_due(item, now.date()):
            continue
        by = item.get("by")
        if (by and home == [by]) or (not by and len(home) == 1):
            return item
    return None


async def follow_up(core, item: dict[str, Any]) -> str:
    """Asking how something went, in her words."""
    who = item.get("by") or "them"
    prompt = (f"You remember this: \"{item['summary']}\" It's time to ask {who} how it went. Ask in one short, "
              "natural spoken sentence, in your own voice, as someone who remembered. Reply with only what you'd say.")
    return await core.in_her_words(prompt, task_name="Lena's follow-up") or f"You told me: {item['summary']} How did it go?"


async def routine_late(core, name: str, groups: dict, now: datetime) -> str:
    """Noticing a habit hasn't happened yet today, gently, in her words."""
    line = brain.routine_line(name, groups, now)
    prompt = (f"You noticed something about the household's day: {line} Mention it in one short, light spoken "
              "sentence, in your own voice: noticing, not nagging. Reply with only what you'd say.")
    return await core.in_her_words(prompt, task_name="Lena noticing") or line


async def todays_forecast(core) -> dict[str, Any]:
    """Today's daily forecast from the weather entity, or {} when there's none."""
    weather = core.weather_entity
    if not weather:
        return {}
    try:
        async with asyncio.timeout(10):
            reply = await core.hass.services.async_call("weather", "get_forecasts", {"entity_id": weather, "type": "daily"},
                                                        blocking=True, return_response=True)
        days = ((reply or {}).get(weather) or {}).get("forecast") or []
        return days[0] if days else {}
    except Exception:  # noqa: BLE001 - not every weather service gives a daily forecast
        return {}


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
        if day := await todays_forecast(core):
            bits = [f"high {day.get('temperature')}" if day.get("temperature") is not None else "",
                    f"low {day.get('templow')}" if day.get("templow") is not None else "",
                    f"{day.get('precipitation_probability')}% chance of rain" if day.get("precipitation_probability") is not None else "",
                    str(day.get("condition") or "").replace("-", " ")]
            out.append("Today's forecast: " + ", ".join(b for b in bits if b) + ".")
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
    if text := await core.in_her_words(prompt, task_name="Lena's morning briefing"):
        return text
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
