"""Lena's state in Home Assistant: her feelings, her memory, and what moves them.

`brain.py` decides; this file listens to Home Assistant (the clock, people, the weather, worry
sensors), applies what the brain decides, saves it, and tells her entities to update.
"""

from __future__ import annotations

import asyncio
import logging
import random
import uuid
from datetime import datetime, time, timedelta
from pathlib import Path
from typing import Any

import yaml

from homeassistant.components import persistent_notification
from homeassistant.config_entries import ConfigEntry
from homeassistant.const import STATE_HOME, STATE_UNAVAILABLE, STATE_UNKNOWN
from homeassistant.core import CALLBACK_TYPE, Event, EventStateChangedData, HomeAssistant, callback
from homeassistant.helpers import area_registry as ar, device_registry as dr, entity_registry as er
from homeassistant.helpers.dispatcher import async_dispatcher_send
from homeassistant.helpers.event import (
    async_call_later,
    async_track_state_change_event,
    async_track_time_change,
)
from homeassistant.helpers.storage import Store
from homeassistant.util import dt as dt_util

from . import brain, tidy
from .const import (
    CONF_ALERT_AGENT,
    CONF_ALERT_ALL_CLEAR,
    CONF_TIDY_DAYS,
    CONF_TIDY_TASK,
    CONF_ALERT_PHONES,
    CONF_AWAY_HOURS,
    CONF_BEDTIME,
    CONF_CALENDARS,
    CONF_EMPTY_HOURS,
    CONF_MOMENT_MINUTES,
    CONF_MOOD_CHANGE_CHANCE,
    CONF_MOOD_HOLD_HOURS,
    CONF_MORNING,
    CONF_PEOPLE,
    CONF_WEATHER,
    CONF_WEATHER_CHANCE,
    CONF_WORRIES,
    DOMAIN,
    OPEN_LENA,
    SIGNAL_UPDATED,
)

_LOGGER = logging.getLogger(__name__)
FEELINGS_FILE = Path(__file__).parent / "feelings.yaml"


def load_catalog() -> brain.Catalog:
    """Read feelings.yaml (call it in the executor; it's file I/O)."""
    data = yaml.safe_load(FEELINGS_FILE.read_text(encoding="utf-8")) or {}

    def section(name: str) -> dict[str, str]:
        """One section of feelings.yaml as name -> description, with the description's line breaks
        folded into single spaces (they're written as YAML block text) and empty entries dropped.
        """
        raw = data.get(name) or {}
        return {str(k): " ".join(str(v).split()) for k, v in raw.items() if v}

    catalog = brain.Catalog(moods=section("moods"), energies=section("energy"), moments=section("moments"))
    if not catalog.moods or not catalog.energies:
        raise ValueError("feelings.yaml needs at least one mood and one energy")
    return catalog


def _parse_time(value: str, fallback: str) -> time:
    """A time setting ("06:30:00"), or the default if it's missing or unreadable, so a bad option never
    stops her day from running.
    """
    try:
        return time.fromisoformat(value)
    except (TypeError, ValueError):
        return time.fromisoformat(fallback)


def _ts(value: datetime | None) -> str | None:
    """A datetime as text for the store; the store is JSON."""
    return value.isoformat() if value else None


def _dt(value: str | None) -> datetime | None:
    """Reads back what _ts wrote."""
    return dt_util.parse_datetime(value) if value else None


class LenaCore:
    """Everything the integration's entities, tools and view share."""

    def __init__(self, hass: HomeAssistant, entry: ConfigEntry, catalog: brain.Catalog) -> None:
        """Holds her state and settings. Nothing runs until async_start: Home Assistant may still be
        starting when the entry is set up.
        """
        self.hass = hass
        self.entry = entry
        self.catalog = catalog
        self.feelings = brain.Feelings()
        self.memory: list[dict[str, str]] = []  # {"uid", "summary", "status": "needs_action" | "completed", "added"}
        self.tidied: datetime | None = None  # when she last tidied her memory
        self.memory_backup: dict[str, Any] | None = None  # {"at", "items"}: the list before her last tidy
        self._rng = random.Random()
        self._state_store: Store[dict[str, Any]] = Store(hass, 1, f"{DOMAIN}.feelings")
        self._memory_store: Store[dict[str, Any]] = Store(hass, 1, f"{DOMAIN}.memory")
        self._unsubs: list[CALLBACK_TYPE] = []
        self._timers: dict[str, CALLBACK_TYPE] = {}

    # ---- Settings ----------------------------------------------------------------------------

    def opt(self, key: str) -> Any:
        """A setting from Configure, or its default when it was never set."""
        return self.entry.options.get(key, brain.DEFAULTS.get(key))

    @property
    def people(self) -> list[str]:
        """The person entities she watches come and go."""
        return list(self.opt(CONF_PEOPLE) or [])

    @property
    def worries(self) -> list[str]:
        """The sensors that worry her when they turn on."""
        return list(self.opt(CONF_WORRIES) or [])

    @property
    def weather_entity(self) -> str | None:
        """The weather entity she notices, if one was chosen."""
        return self.opt(CONF_WEATHER) or None

    # ---- Starting and stopping -------------------------------------------------------------

    async def async_start(self) -> None:
        """Loads how she felt and what she remembers from before the restart, then starts her day:
        morning, bedtime and hourly timers and listeners for people, weather and worries. Saved
        feelings win over the defaults, so a restart doesn't reset her mood.
        """
        saved = await self._state_store.async_load() or {}
        f = self.feelings
        f.mood = saved.get("mood", f.mood)
        f.mood_reason = saved.get("mood_reason", "")
        f.mood_since = _dt(saved.get("mood_since")) or dt_util.now()
        f.energy = saved.get("energy", f.energy)
        f.moment = saved.get("moment", brain.NOTHING)
        f.moment_reason = saved.get("moment_reason", "")
        f.moment_since = _dt(saved.get("moment_since"))
        if f.mood not in self.catalog.moods:
            f.mood = next(iter(self.catalog.moods))
        if f.energy not in self.catalog.energies:
            f.energy = "Steady" if "Steady" in self.catalog.energies else next(iter(self.catalog.energies))
        if f.moment != brain.NOTHING and f.moment not in self.catalog.moments:
            f.moment = brain.NOTHING
        saved_memory = await self._memory_store.async_load() or {}
        self.memory = saved_memory.get("items", [])
        self.tidied = _dt(saved_memory.get("tidied"))
        self.memory_backup = saved_memory.get("backup")

        hass = self.hass
        wake = _parse_time(self.opt(CONF_MORNING), brain.DEFAULTS["morning"])
        bed = _parse_time(self.opt(CONF_BEDTIME), brain.DEFAULTS["bedtime"])
        self._unsubs += [
            async_track_time_change(hass, self._on_morning, hour=wake.hour, minute=wake.minute, second=wake.second),
            async_track_time_change(hass, self._on_bedtime, hour=bed.hour, minute=bed.minute, second=bed.second),
            async_track_time_change(hass, self._on_hour, minute=7, second=0),
        ]
        if self.people:
            self._unsubs.append(async_track_state_change_event(hass, self.people, self._on_person))
        if self.weather_entity:
            self._unsubs.append(async_track_state_change_event(hass, [self.weather_entity], self._on_weather))
        if self.worries:
            self._unsubs.append(async_track_state_change_event(hass, self.worries, self._on_worry))
        self._schedule_fade()
        self._check_empty_house()

    @callback
    def async_stop(self) -> None:
        """Stops every timer and listener, for unloading or reloading after Configure."""
        for unsub in self._unsubs:
            unsub()
        self._unsubs.clear()
        for cancel in self._timers.values():
            cancel()
        self._timers.clear()

    # ---- Applying changes ------------------------------------------------------------------

    @callback
    def apply(self, change: brain.Change, source: str) -> None:
        """Set what an event decided, save it, and update her entities."""
        if not change:
            return
        f, now = self.feelings, dt_util.now()
        if change.energy and change.energy != f.energy:
            f.energy = change.energy
        if change.mood and change.mood != f.mood:
            f.mood, f.mood_reason, f.mood_since = change.mood, change.mood_reason, now
        elif change.reason is not None:
            f.mood_reason = change.reason
        if change.moment is not None:
            f.moment = change.moment
            f.moment_reason = change.moment_reason if change.moment != brain.NOTHING else ""
            f.moment_since = now if change.moment != brain.NOTHING else None
            self._schedule_fade()
        _LOGGER.debug("%s: mood %s, energy %s, moment %s", source, f.mood, f.energy, f.moment)
        self._changed()

    @callback
    def _changed(self) -> None:
        """Saves her feelings (batched: async_delay_save writes at most once a second) and tells the
        entities to update.
        """
        f = self.feelings
        self._state_store.async_delay_save(
            lambda: {
                "mood": f.mood, "mood_reason": f.mood_reason, "mood_since": _ts(f.mood_since),
                "energy": f.energy, "moment": f.moment, "moment_reason": f.moment_reason,
                "moment_since": _ts(f.moment_since),
            },
            1,
        )
        async_dispatcher_send(self.hass, SIGNAL_UPDATED)

    @callback
    def set_by_hand(self, kind: str, value: str) -> None:
        """You picked a feeling in a dropdown. The old reason no longer fits, so it's cleared."""
        if kind == "mood":
            self.apply(brain.Change(mood=value), "set by hand")
        elif kind == "energy":
            self.apply(brain.Change(energy=value), "set by hand")
        else:
            self.apply(brain.Change(moment=value), "set by hand")

    @callback
    def feel(self, moment: str, reason: str) -> bool:
        """She (or an automation) records how something made her feel."""
        if moment not in self.catalog.moments:
            return False
        self.apply(brain.Change(moment=moment, moment_reason=reason.strip()[:300]), "feel")
        return True

    # ---- Her memory --------------------------------------------------------------------------

    def memories(self) -> list[str]:
        """What she remembers: the open items on her memory list. Ticked-off items are kept in the list
        but retired from her prompt.
        """
        return [m["summary"] for m in self.memory if m.get("status") != "completed"]

    @callback
    def memory_changed(self) -> None:
        """Saves the memory list and updates the to-do entity."""
        self._memory_store.async_delay_save(lambda: {
            "items": self.memory,
            "tidied": self.tidied.isoformat() if self.tidied else None,
            "backup": self.memory_backup,
        }, 1)
        async_dispatcher_send(self.hass, SIGNAL_UPDATED)

    @callback
    def remember(self, fact: str) -> dict[str, str]:
        """Adds a fact to her memory and returns the new item."""
        item = {"uid": uuid.uuid4().hex, "summary": fact.strip(), "status": "needs_action", "added": dt_util.now().isoformat()}
        self.memory.append(item)
        self.memory_changed()
        return item

    @callback
    def forget(self, fact: str) -> str | None:
        """Removes the saved memory that best matches, and returns it."""
        open_items = [m for m in self.memory if m.get("status") != "completed"]
        match = brain.best_match(fact, [m["summary"] for m in open_items])
        match = next((m for m in open_items if m["summary"] == match), None)
        if match is None:
            return None
        self.memory.remove(match)
        self.memory_changed()
        return match["summary"]

    @property
    def tidy_due(self) -> bool:
        """Whether it's time for her to tidy her memory (Configure: every so many days; 0 never)."""
        days = int(self.opt(CONF_TIDY_DAYS) or 0)
        if days <= 0:
            return False
        return self.tidied is None or dt_util.now() - self.tidied >= timedelta(days=days)

    async def async_tidy_memory(self) -> tidy.Plan | None:
        """Tidies her memory with Home Assistant's AI Task: combines, clarifies and retires lines (see
        tidy.py), keeping a copy first so lena.undo_memory_tidy can put it back, and tells you what changed
        in a notification. Returns what changed, or None when there was too little to tidy. Raises
        tidy.TidyError when the AI Task isn't there or its answer can't be used; nothing changes then.
        """
        open_items = [m for m in self.memory if m.get("status") != "completed"]
        if len(open_items) < tidy.MIN_ITEMS:
            return None
        if not self.hass.services.has_service("ai_task", "generate_data"):
            raise tidy.TidyError("Home Assistant has no AI Task set up (Settings > System > General > AI suggestions)")
        now = dt_util.now()
        request: dict[str, Any] = {"task_name": "Tidy Lena's memory", "instructions": tidy.instructions(open_items, now)}
        if task := self.opt(CONF_TIDY_TASK):
            request["entity_id"] = task
        try:
            reply = await self.hass.services.async_call("ai_task", "generate_data", request, blocking=True, return_response=True)
        except Exception as err:  # noqa: BLE001 - any failure of the model just means no tidy this time
            raise tidy.TidyError(f"The AI Task didn't answer: {err}") from err
        before = [dict(m) for m in self.memory]
        result = tidy.plan(self.memory, (reply or {}).get("data"), now)
        self.tidied = now
        if result.changed:
            self.memory_backup = {"at": now.isoformat(), "items": before}
            self.memory[:] = result.memory
            persistent_notification.async_create(
                self.hass,
                f"{result.summary()}\n\nTo put it back as it was, run the action **Lena: Undo memory tidy**.",
                title="Lena tidied her memory",
                notification_id="lena_memory_tidy",
            )
            _LOGGER.info("Tidied her memory: %s combined, %s clarified, %s retired",
                         len(result.combined), len(result.clarified), len(result.retired))
        self.memory_changed()
        return result

    @callback
    def undo_memory_tidy(self) -> bool:
        """Puts her memory back as it was before her last tidy. False if there's nothing to undo."""
        if not self.memory_backup:
            return False
        self.memory[:] = self.memory_backup["items"]
        self.memory_backup = None
        self.memory_changed()
        persistent_notification.async_dismiss(self.hass, "lena_memory_tidy")
        return True

    async def _tidy_if_due(self) -> None:
        """At bedtime, when it's time: tidies her memory, and only logs it if that didn't work."""
        if not self.tidy_due:
            return
        try:
            await self.async_tidy_memory()
        except tidy.TidyError as err:
            _LOGGER.warning("Lena didn't tidy her memory: %s", err)

    # ---- What she's told -------------------------------------------------------------------

    def _clock(self, value: str | None) -> str | None:
        """An ISO time from the sun's attributes as a local clock time ("5:41 PM")."""
        when = dt_util.parse_datetime(value) if isinstance(value, str) else None
        return dt_util.as_local(when).strftime("%I:%M %p").lstrip("0") if when else None

    def time_of_day(self) -> str:
        """The time, said so it can't be misread: the part of the day, and whether it's light out."""
        now = dt_util.now()
        line = f"It's {now.strftime('%A, %B')} {now.day}, {now.year}, {now.strftime('%I:%M %p').lstrip('0')}: {brain.part_of_day(now.hour)}"
        light = self._daylight()
        sun = self.hass.states.get("sun.sun")
        if sun is not None and sun.state in ("above_horizon", "below_horizon"):
            if light and (sets := self._clock(sun.attributes.get("next_setting"))):
                line += f", and light out (the sun sets at {sets})."
            elif not light and (rises := self._clock(sun.attributes.get("next_rising"))):
                line += f", and dark out (the sun rises at {rises})."
            else:
                line += ", and light out." if light else ", and dark out."
        else:
            line += "."
        return line

    def devices(self, limit: int = 400) -> str:
        """
        The exact names of everything exposed to Assist, by room and kind, so she can ask the
        house tools for the right thing the first time. Home Assistant matches names exactly
        ("curtains" doesn't find "Living Room Curtains"), so without this she guesses. Names only,
        never states: states change, and she checks those live.
        """
        try:
            from homeassistant.components.homeassistant.exposed_entities import async_should_expose
        except ImportError:
            return ""
        entities, devices, areas = er.async_get(self.hass), dr.async_get(self.hass), ar.async_get(self.hass)
        rooms: dict[str, dict[str, list[str]]] = {}
        count, more = 0, False
        for state in sorted(self.hass.states.async_all(), key=lambda s: s.name.lower()):
            # Scripts and to-do lists have tools of their own. Calendars stay: their tool wants the exact name.
            if state.domain in ("script", "todo"):
                continue
            try:
                if not async_should_expose(self.hass, "conversation", state.entity_id):
                    continue
            except KeyError:  # Home Assistant's exposure settings aren't loaded
                return ""
            if count >= limit:
                more = True
                break
            entry = entities.async_get(state.entity_id)
            area_id = entry.area_id if entry else None
            if area_id is None and entry is not None and entry.device_id:
                device = devices.async_get(entry.device_id)
                area_id = device.area_id if device else None
            area = areas.async_get_area(area_id) if area_id else None
            aliases = sorted(a for a in (entry.aliases if entry else ()) if isinstance(a, str) and a and a != state.name)
            label = state.name + (f" (also {', '.join(aliases)})" if aliases else "")
            rooms.setdefault(area.name if area else "", {}).setdefault(state.domain, []).append(label)
            count += 1
        if not rooms:
            return ""
        lines = [
            "## Your home",
            "Everything you can check or control, by room and kind, with its exact name. The house tools match names "
            "exactly, so use a name from here, or ask by kind (domain) and room. Check states live; this list has none.",
        ]
        for room in sorted(rooms, key=lambda r: (r == "", r.lower())):
            kinds = "; ".join(f"{kind}: {', '.join(names)}" for kind, names in sorted(rooms[room].items()))
            lines.append(f"- {room or 'No room'}: {kinds}")
        if more:
            lines.append(f"(Only the first {limit} are listed; ask by kind and room for the rest.)")
        return "\n".join(lines)

    def context(self, with_reasons: bool = True, memory: bool = True, devices: bool = False) -> str:
        """The time, who's home, how she feels and what she remembers (and, if asked, the home's devices)."""
        home = [s.name for s in self.hass.states.async_all("person") if s.state == STATE_HOME]
        parts = [
            "## Right now\n"
            f"{self.time_of_day()}\n"
            f"At home: {', '.join(home) if home else 'nobody'}.",
            brain.describe(self.feelings, self.catalog, with_reasons),
        ]
        if memory:
            parts.append(brain.remembered(self.memories()))
        if devices and (index := self.devices()):
            parts.append(index)
        return "\n\n".join(parts)

    # ---- Events ------------------------------------------------------------------------------

    def _wake_bed(self) -> tuple[time, time]:
        """Her wake-up and bedtime from Configure."""
        return (
            _parse_time(self.opt(CONF_MORNING), brain.DEFAULTS["morning"]),
            _parse_time(self.opt(CONF_BEDTIME), brain.DEFAULTS["bedtime"]),
        )

    def _weather(self) -> str | None:
        """The current weather condition, or None without a weather entity."""
        state = self.hass.states.get(self.weather_entity) if self.weather_entity else None
        return state.state if state else None

    def _daylight(self) -> bool:
        """Whether the sun's up: Home Assistant's sun, or the clock if that's not there."""
        sun = self.hass.states.get("sun.sun")
        if sun and sun.state in ("above_horizon", "below_horizon"):
            return sun.state == "above_horizon"
        return 7 <= dt_util.now().hour < 19

    def _home_counts(self) -> tuple[int, int]:
        """How many of her people are home, and how many there are."""
        states = [self.hass.states.get(p) for p in self.people]
        return sum(1 for s in states if s and s.state == STATE_HOME), len(self.people)

    def _worries_on(self) -> int:
        """How many worry sensors are on right now."""
        return sum(1 for w in self.worries if (s := self.hass.states.get(w)) and s.state == "on")

    async def _on_morning(self, now: datetime) -> None:
        """Her morning: she wakes up, and today's special days from the chosen calendars (birthdays and
        the like) can set her mood and its reason.
        """
        special: list[str] = []
        calendars = list(self.opt(CONF_CALENDARS) or [])
        if calendars:
            start = dt_util.start_of_local_day()
            try:
                found = await self.hass.services.async_call(
                    "calendar", "get_events",
                    {"entity_id": calendars, "start_date_time": start, "end_date_time": start + timedelta(days=1)},
                    blocking=True, return_response=True,
                )
                for cal in (found or {}).values():
                    for event in cal.get("events", []):
                        if (summary := event.get("summary")) and summary not in special:
                            special.append(summary)
            except Exception:  # noqa: BLE001 - a calendar problem shouldn't stop her waking up
                _LOGGER.warning("Couldn't read the special days calendars", exc_info=True)
        self.apply(brain.morning(self.feelings, self.catalog, special, self._weather(), self._rng, self._daylight()),
                   "morning")

    @callback
    def _on_bedtime(self, now: datetime) -> None:
        """Bedtime: she winds down, and now and then tidies her memory."""
        self.apply(brain.bedtime(self.catalog), "bedtime")
        self.hass.async_create_task(self._tidy_if_due())

    @callback
    def _on_hour(self, now: datetime) -> None:
        """Every hour: her energy follows her day, her mood's reason is kept true, and a mood that's
        lasted may drift.
        """
        wake, bed = self._wake_bed()
        home, total = self._home_counts()
        change = brain.hourly(
            self.feelings, self.catalog, dt_util.now(), wake, bed, self._weather(), home, total,
            float(self.opt(CONF_MOOD_HOLD_HOURS)), int(self.opt(CONF_MOOD_CHANGE_CHANCE)), self._rng,
            self._daylight(),
        )
        self.apply(change, "hourly")

    @callback
    def _on_person(self, event: Event[EventStateChangedData]) -> None:
        """Someone's presence changed: a welcome back after a long time away, and the empty-house timer
        started or stopped.
        """
        old, new = event.data["old_state"], event.data["new_state"]
        if new is None:
            return
        if old is not None and new.state == STATE_HOME and old.state not in (STATE_HOME, STATE_UNKNOWN, STATE_UNAVAILABLE):
            away = (new.last_changed - old.last_changed).total_seconds() / 3600
            self.apply(brain.arrived(self.catalog, new.name, away, float(self.opt(CONF_AWAY_HOURS))), "arrived")
        self._check_empty_house()

    @callback
    def _check_empty_house(self) -> None:
        """Start the lonely timer when everyone's out; stop it when someone's back."""
        home, total = self._home_counts()
        if total and home == 0:
            if "empty" not in self._timers:
                self._timers["empty"] = async_call_later(
                    self.hass, timedelta(hours=float(self.opt(CONF_EMPTY_HOURS))), self._on_empty
                )
        elif cancel := self._timers.pop("empty", None):
            cancel()

    @callback
    def _on_empty(self, now: datetime) -> None:
        """The house has been empty for the set number of hours."""
        self._timers.pop("empty", None)
        if self._home_counts()[0] == 0:
            self.apply(brain.house_empty(self.feelings, self.catalog, self._rng), "house empty")

    @callback
    def _on_weather(self, event: Event[EventStateChangedData]) -> None:
        """The weather changed. She waits a quarter of an hour before reacting, so a passing shower or
        a flickering weather service doesn't jerk her mood around.
        """
        old, new = event.data["old_state"], event.data["new_state"]
        if new is None or old is None or old.state == new.state:
            return
        before, after = old.state, new.state
        if cancel := self._timers.pop("weather", None):
            cancel()

        @callback
        def settled(now: datetime) -> None:
            # Only react if it's still like this a quarter of an hour later.
            """Fifteen minutes on: react only if the weather is still what it changed to."""
            self._timers.pop("weather", None)
            if self._weather() == after:
                change = brain.weather_turned(self.feelings, self.catalog, before, after,
                                              int(self.opt(CONF_WEATHER_CHANCE)), self._rng, self._daylight())
                self.apply(change, "weather")

        self._timers["weather"] = async_call_later(self.hass, timedelta(minutes=15), settled)

    @callback
    def _on_worry(self, event: Event[EventStateChangedData]) -> None:
        """A worry sensor changed. Turning on only counts after two minutes (a door opened and shut
        again isn't a worry); clearing counts at once.
        """
        new, entity_id = event.data["new_state"], event.data["entity_id"]
        if new is None:
            return
        key = f"worry:{entity_id}"
        if cancel := self._timers.pop(key, None):
            cancel()
        if new.state == "on":
            @callback
            def still_on(now: datetime) -> None:
                """Two minutes on: if the sensor is still on, she's worried and the phones are told."""
                self._timers.pop(key, None)
                state = self.hass.states.get(entity_id)
                if state and state.state == "on":
                    change = brain.worried(self.catalog, state.name, state.attributes.get("device_class"))
                    self.apply(change, "worry")
                    if change:
                        self.hass.async_create_task(self.alert(change.moment, change.moment_reason))

            self._timers[key] = async_call_later(self.hass, timedelta(minutes=2), still_on)
        elif new.state == "off":
            change = brain.worry_cleared(self.feelings, self.catalog, new.name, self._worries_on())
            self.apply(change, "worry cleared")
            if change and change.moment != brain.NOTHING:
                self.hass.async_create_task(self.alert(change.moment, change.moment_reason))

    @callback
    def _schedule_fade(self) -> None:
        """Sets the timer that ends her current moment, unless it's Nothing. Worried has no end of its
        own: it waits for the sensors to clear.
        """
        if cancel := self._timers.pop("fade", None):
            cancel()
        f = self.feelings
        if f.moment == brain.NOTHING:
            return
        due = (f.moment_since or dt_util.now()) + timedelta(minutes=float(self.opt(CONF_MOMENT_MINUTES)))
        delay = max((due - dt_util.now()).total_seconds(), 1)
        self._timers["fade"] = async_call_later(self.hass, delay, self._on_fade)

    @callback
    def _on_fade(self, now: datetime) -> None:
        """Her moment's time is up: back to Nothing, unless something still worries her."""
        self._timers.pop("fade", None)
        change = brain.fade(self.feelings, self._worries_on())
        if change:
            self.apply(change, "fade")
        elif self.feelings.moment != brain.NOTHING:
            # Still worried about something that hasn't cleared: check again later.
            self._timers["fade"] = async_call_later(self.hass, timedelta(minutes=5), self._on_fade)

    # ---- Alerts ------------------------------------------------------------------------------

    async def alert(self, moment: str, reason: str) -> None:
        """Tell the chosen phones (through the Home Assistant companion app) that she's worried, or
        that it's sorted. In her own words when an agent is chosen, otherwise the plain reason."""
        phones = [p for p in self.opt(CONF_ALERT_PHONES) or [] if p.startswith("notify.")]
        if not phones or (moment != "Worried" and not self.opt(CONF_ALERT_ALL_CLEAR)):
            return
        message = await self._in_her_words(moment, reason) or reason
        worried = moment == "Worried"
        for phone in phones:
            service = phone.removeprefix("notify.")
            if not self.hass.services.has_service("notify", service):
                _LOGGER.warning("Can't alert %s: there's no such notify action", phone)
                continue
            await self.hass.services.async_call("notify", service, {
                "title": "Lena",
                "message": message,
                "data": {
                    "tag": "lena-alert",  # the all-clear replaces the worry
                    "clickAction": OPEN_LENA,
                    "channel": "Lena",
                    "importance": "high" if worried else "default",
                    "priority": "high" if worried else "normal",
                    "ttl": 0,
                },
            })

    async def _in_her_words(self, moment: str, reason: str) -> str | None:
        """The alert written by her: the chosen conversation agent is asked to say it in her own words.
        Returns None (and the plain reason is used) if no agent is set, it fails, or it takes longer
        than 45 seconds, so an alert is never held up for long.
        """
        agent = self.opt(CONF_ALERT_AGENT)
        if not agent:
            return None
        what = f"Something at home needs attention: {reason}" if moment == "Worried" else f"It's sorted: {reason}"
        prompt = (f"{what} Write the phone notification you'd send the household about it, in your own voice: "
                  "one or two short sentences, no greeting, no emoji. Reply with only the notification.")
        try:
            async with asyncio.timeout(45):
                result = await self.hass.services.async_call(
                    "conversation", "process", {"text": prompt, "agent_id": agent},
                    blocking=True, return_response=True,
                )
            response = (result or {}).get("response", {})
            if response.get("response_type") == "error":
                return None
            text = response.get("speech", {}).get("plain", {}).get("speech", "").strip().strip('"')
            return text[:300] or None
        except Exception:  # noqa: BLE001 - the plain reason is a fine fallback
            _LOGGER.debug("Couldn't write the alert in her words", exc_info=True)
            return None

    def weather_now(self) -> dict[str, Any] | None:
        """The weather right now, for the Lena app's display."""
        state = self.hass.states.get(self.weather_entity) if self.weather_entity else None
        if state is None or state.state in (STATE_UNAVAILABLE, STATE_UNKNOWN):
            return None
        return {
            "condition": state.state,
            "temperature": state.attributes.get("temperature"),
            "unit": state.attributes.get("temperature_unit"),
        }
