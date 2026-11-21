"""Routines she notices: the household's habits, learned from Home Assistant's history.

You choose what she watches (Configure > Routines): the coffee machine's plug, the back door for the dog's
walk, the bathroom light in the morning. Every night she reads the last four weeks of each from the
recorder and learns when it usually first happens in a day, separately for weekdays and weekends; a habit
only counts when it's regular (brain.learn_routine). During the day, when a regular habit is well past its
usual time and hasn't happened yet, she knows ("Not like most weekdays: the coffee machine usually goes on
around 7:05"), and can mention it if she speaks first.

For the first two weeks after something is chosen (Configure: Just watch for) she only learns: what she
believes is shown on the Lena Routines sensor, but she never comments, so a wrong guess can be spotted
first.
"""

from __future__ import annotations

from datetime import date, datetime, timedelta
import logging
from typing import Any

from homeassistant.core import CALLBACK_TYPE, Event, EventStateChangedData, HomeAssistant, callback
from homeassistant.helpers.dispatcher import async_dispatcher_send
from homeassistant.helpers.event import async_track_state_change_event, async_track_time_change
from homeassistant.helpers.storage import Store
from homeassistant.util import dt as dt_util

from . import brain, history
from .const import CONF_ROUTINE_WATCH_DAYS, CONF_ROUTINES, DOMAIN, SIGNAL_UPDATED

_LOGGER = logging.getLogger(__name__)

LEARN_DAYS = 28  # how far back she looks (as far as the recorder keeps)


class Routines:
    """The habits she's learned, when each one was chosen, and what's happened today."""

    def __init__(self, hass: HomeAssistant, core) -> None:
        self.hass = hass
        self.core = core
        self.learned: dict[str, dict[str, Any]] = {}  # entity_id -> {"name", "groups": brain.learn_routine()}
        self.watching_since: dict[str, str] = {}  # entity_id -> the day it was first chosen
        self.seen: dict[str, str] = {}  # entity_id -> the last day it happened
        self.mentioned: dict[str, str] = {}  # entity_id -> the last day she spoke first about it
        self._store: Store[dict[str, Any]] = Store(hass, 1, f"{DOMAIN}.routines")
        self._unsubs: list[CALLBACK_TYPE] = []

    @property
    def entities(self) -> list[str]:
        """What she's been asked to watch."""
        return list(self.core.opt(CONF_ROUTINES) or [])

    async def async_start(self) -> None:
        """Loads what she learned before, starts watching, and learns at once if she never has."""
        saved = await self._store.async_load() or {}
        self.learned = saved.get("learned") or {}
        self.watching_since = saved.get("watching_since") or {}
        self.seen = saved.get("seen") or {}
        self.mentioned = saved.get("mentioned") or {}
        today = dt_util.now().date().isoformat()
        for entity_id in self.entities:
            self.watching_since.setdefault(entity_id, today)
        # Forget what's no longer chosen.
        for table in (self.learned, self.watching_since, self.seen, self.mentioned):
            for entity_id in [e for e in table if e not in self.entities]:
                table.pop(entity_id)
        self._save()
        if not self.entities:
            return
        self._unsubs += [
            async_track_state_change_event(self.hass, self.entities, self._on_change),
            async_track_time_change(self.hass, self._on_night, hour=3, minute=17, second=0),
        ]
        if any(e not in self.learned for e in self.entities):
            self.hass.async_create_task(self.async_learn())

    @callback
    def async_stop(self) -> None:
        """Stops watching."""
        for unsub in self._unsubs:
            unsub()
        self._unsubs.clear()

    def _save(self) -> None:
        self._store.async_delay_save(lambda: {
            "learned": self.learned, "watching_since": self.watching_since,
            "seen": self.seen, "mentioned": self.mentioned,
        }, 2)

    @callback
    def _on_change(self, event: Event[EventStateChangedData]) -> None:
        """It happened (turned on, opened): today's done."""
        new = event.data["new_state"]
        if new is not None and new.state in history.ACTIVE:
            self.seen[event.data["entity_id"]] = dt_util.as_local(new.last_changed).date().isoformat()
            self._save()

    async def _on_night(self, now: datetime) -> None:
        await self.async_learn()

    async def async_learn(self) -> None:
        """Reads the last four weeks of each from the recorder and learns its usual time. A recorder
        problem leaves what she knew as it was.
        """
        now = dt_util.now()
        start = dt_util.start_of_local_day() - timedelta(days=LEARN_DAYS)
        for entity_id in self.entities:
            try:
                changes = (await history._changes(self.hass, [entity_id], start)).get(entity_id, [])
            except Exception as err:  # noqa: BLE001 - try again tomorrow night
                _LOGGER.debug("Couldn't read %s's history: %s", entity_id, err)
                continue
            state = self.hass.states.get(entity_id)
            self.learned[entity_id] = {
                "name": state.name if state else entity_id,
                "groups": brain.learn_routine(_firsts(changes, start, now.date())),
            }
        self._save()
        async_dispatcher_send(self.hass, SIGNAL_UPDATED)  # the Lena Routines sensor shows what she learned

    def watching_only(self, entity_id: str, today: date) -> bool:
        """Whether it's still in its first days, when she only learns."""
        since = dt_util.parse_date(self.watching_since.get(entity_id) or "") or today
        days = int(self.core.opt(CONF_ROUTINE_WATCH_DAYS) or 0)
        return (today - since).days < days

    def late(self, now: datetime) -> list[tuple[str, str, dict]]:
        """The regular habits that are late today, as (entity_id, name, learned groups)."""
        today = now.date()
        found = []
        for entity_id, info in self.learned.items():
            if entity_id not in self.entities or self.watching_only(entity_id, today):
                continue
            seen_today = self.seen.get(entity_id) == today.isoformat()
            if brain.routine_late(info.get("groups") or {}, now, seen_today):
                found.append((entity_id, info.get("name") or entity_id, info["groups"]))
        return found

    def late_lines(self, now: datetime) -> list[str]:
        """The late habits as lines of her prompt."""
        return [brain.routine_line(name, groups, now) for _, name, groups in self.late(now)]

    def mention(self, entity_id: str) -> None:
        """She's spoken first about it today, so she won't again."""
        self.mentioned[entity_id] = dt_util.now().date().isoformat()
        self._save()

    def to_mention(self, now: datetime) -> tuple[str, str, dict] | None:
        """A late habit she hasn't spoken about today, if there is one."""
        today = now.date().isoformat()
        return next((late for late in self.late(now) if self.mentioned.get(late[0]) != today), None)

    def describe(self) -> dict[str, str]:
        """What she believes, by name, for the Lena Routines sensor: "around 7:05 AM on weekdays (still
        just watching)", or "not regular yet".
        """
        today = dt_util.now().date()
        out = {}
        for entity_id in self.entities:
            info = self.learned.get(entity_id) or {}
            groups = info.get("groups") or {}
            name = info.get("name") or entity_id
            if not groups:
                out[name] = "not regular yet"
                continue
            text = "; ".join(f"around {brain.clock_words(g['usual'])} on {group}s" for group, g in groups.items())
            out[name] = text + (" (still just watching)" if self.watching_only(entity_id, today) else "")
        return out


def _firsts(changes: list, start: datetime, today: date) -> dict[date, int | None]:
    """The minutes past midnight it first became active on each whole day before today (None: not that
    day). Days before the first change the recorder has are left out: the recorder may only keep ten days
    (its default), and a day it doesn't know about isn't a day the habit was skipped.
    """
    recorded = [s for s in changes if s.last_changed > start]
    if not recorded:
        return {}
    firsts: dict[date, int | None] = {}
    day = dt_util.as_local(recorded[0].last_changed).date()
    while day < today:
        firsts[day] = None
        day += timedelta(days=1)
    for state in recorded:
        if state.state not in history.ACTIVE:
            continue
        local = dt_util.as_local(state.last_changed)
        if local.date() in firsts and firsts[local.date()] is None:
            firsts[local.date()] = local.hour * 60 + local.minute
    return firsts
