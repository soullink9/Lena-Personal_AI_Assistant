"""What Lena asks her displays to do, and what they tell her back.

Her tools (in Assist and in Open WebUI) set timers, alarms and reminders, change the volume, and say
things on displays: those happen on the tablet or phone, so they're queued here for the display and the
app collects them (POST /api/lena/display/sync). A wall display syncs every few seconds; a phone right
after each answer. At the same time the app reports its own timers, alarms and reminders and its volume,
so she can say how long is left on the pasta, or cancel the right one.

A command is for one display: the one named ("tell the kitchen"), or the one she was asked through.
Nothing here is kept over a restart of Home Assistant: commands are collected within seconds, and
what's set lives on the displays.
"""

from __future__ import annotations

import asyncio
import re
import time
import uuid
from dataclasses import dataclass, field
from typing import Any

from aiohttp import web

from homeassistant.core import HomeAssistant, callback
from homeassistant.helpers import area_registry as ar, device_registry as dr
from homeassistant.helpers.http import KEY_HASS, HomeAssistantView

from .const import DOMAIN
from .displays import display_id_of, displays

KEEP_SECONDS = 15 * 60  # a command no display collected in this long is dropped
KINDS = ("timer", "alarm", "reminder")


class TargetError(ValueError):
    """Which display was meant couldn't be worked out; the message says what to ask."""


@dataclass
class Command:
    """One thing for one display to do."""

    seq: int
    device_id: str
    type: str  # timer, alarm, reminder, cancel, volume, announce
    data: dict[str, Any]
    at: float = field(default_factory=time.time)
    collected: asyncio.Event = field(default_factory=asyncio.Event)

    def as_dict(self) -> dict[str, Any]:
        """What the app gets."""
        return {"seq": self.seq, "type": self.type, **self.data}


@dataclass
class Reported:
    """What a display last said it has: its timers, alarms and reminders, and its volume (0-100)."""

    countdowns: list[dict[str, Any]] = field(default_factory=list)
    volume: int | None = None
    seen: float = 0.0
    wake_word: str | None = None  # the phrase it listens for (WakePhrase name), or None when it doesn't listen


class Hub:
    """The queue of commands for each display, and what each display last reported."""

    def __init__(self, hass: HomeAssistant, entry_id: str) -> None:
        """Empty: nothing queued, nothing reported. boot changes with every start, so an app whose
        numbers came from before a restart starts again from the beginning.
        """
        self.hass = hass
        self.entry_id = entry_id
        self.boot = uuid.uuid4().hex
        self._seq = 0
        self._queue: list[Command] = []
        self.reported: dict[str, Reported] = {}

    # ---- Which display -----------------------------------------------------------------------

    @callback
    def targets(self, device_ids: list[str] | None = None, room: str | None = None, asking: str | None = None) -> list[dr.DeviceEntry]:
        """The displays meant: the ones chosen, else the ones in the room named, else the one asked
        through, else the only one there is. Raises TargetError saying what's missing.
        """
        everyone = displays(self.hass, self.entry_id)
        if not everyone:
            raise TargetError("There are no Lena displays yet: the Lena app registers itself once Home Assistant is set up in it")
        if device_ids:
            chosen = [d for d in everyone if d.id in device_ids]
            if len(chosen) != len(set(device_ids)):
                raise TargetError("Choose Lena displays (devices under the Lena integration)")
            return chosen
        if room and room.strip():
            area = find_area(self.hass, room)
            if area is None:
                names = sorted({a.name for a in ar.async_get(self.hass).async_list_areas()})
                raise TargetError(f"There's no room called {room}. The rooms are: {', '.join(names)}")
            here = [d for d in everyone if d.area_id == area.id]
            if not here:
                with_displays = sorted({a.name for d in everyone if d.area_id and (a := ar.async_get(self.hass).async_get_area(d.area_id))})
                raise TargetError(
                    f"There's no Lena display in the {area.name}"
                    + (f"; there are in: {', '.join(with_displays)}" if with_displays else "")
                )
            return here
        if asking and any(d.id == asking for d in everyone):
            return [d for d in everyone if d.id == asking]
        if len(everyone) == 1:
            return everyone
        rooms = sorted({a.name for d in everyone if d.area_id and (a := ar.async_get(self.hass).async_get_area(d.area_id))})
        raise TargetError("Say which room" + (f": {', '.join(rooms)}" if rooms else ""))

    # ---- Commands ----------------------------------------------------------------------------

    @callback
    def send(self, devices: list[dr.DeviceEntry], kind: str, data: dict[str, Any]) -> list[Command]:
        """Queues a command for each display, and returns them (to wait for with [collected])."""
        self._drop_old()
        sent = []
        for device in devices:
            self._seq += 1
            command = Command(self._seq, device.id, kind, data)
            self._queue.append(command)
            sent.append(command)
        return sent

    async def collected(self, command: Command, timeout: float) -> bool:
        """Waits until the display has collected [command]; False if it didn't within [timeout] seconds."""
        try:
            async with asyncio.timeout(timeout):
                await command.collected.wait()
            return True
        except TimeoutError:
            return False

    @callback
    def collect(self, device_id: str, after: int, boot: str | None) -> tuple[list[dict[str, Any]], int]:
        """The commands for a display since it last looked (all of this start's, if its numbers are
        from before a restart), and the number to send next time.
        """
        self._drop_old()
        if boot != self.boot:
            after = 0
        mine = [c for c in self._queue if c.device_id == device_id and c.seq > after]
        for command in mine:
            command.collected.set()
        return [c.as_dict() for c in mine], self._seq

    def _drop_old(self) -> None:
        """Forgets commands nobody collected in time."""
        oldest = time.time() - KEEP_SECONDS
        self._queue = [c for c in self._queue if c.at >= oldest]

    # ---- What displays have ------------------------------------------------------------------

    @callback
    def report(self, device_id: str, countdowns: list[dict[str, Any]], volume: int | None, wake_word: str | None = None) -> None:
        """A display's own list, volume and wake word, as it just reported them."""
        self.reported[device_id] = Reported(countdowns, volume, time.time(), wake_word)

    @callback
    def countdowns(self, devices: list[dr.DeviceEntry]) -> list[dict[str, Any]]:
        """Every timer, alarm and reminder the displays have, soonest first, with the display's name."""
        out = []
        for device in devices:
            for item in self.reported.get(device.id, Reported()).countdowns:
                out.append({**item, "display": device.name_by_user or device.name})
        return sorted(out, key=lambda c: c.get("at") or 0)


@callback
def find_area(hass: HomeAssistant, room: str) -> ar.AreaEntry | None:
    """The area someone means by a room's name: exact, then ignoring case and "the", then an alias, then
    one whose name contains it ("kitchen" for "Kitchen & dining").
    """
    areas = ar.async_get(hass).async_list_areas()

    def plain(text: str) -> str:
        return re.sub(r"^the\s+", "", text.strip().lower())

    wanted = plain(room)
    for test in (
        lambda a: a.name == room,
        lambda a: plain(a.name) == wanted,
        lambda a: any(plain(alias) == wanted for alias in a.aliases),
        lambda a: wanted in plain(a.name),
    ):
        found = [a for a in areas if test(a)]
        if len(found) == 1:
            return found[0]
    return None


class LenaSyncView(HomeAssistantView):
    """POST /api/lena/display/sync {"id", "after", "boot", "countdowns": [...], "volume": 0-100}: a
    display reports what it has and collects what it's been asked to do. Answers {"boot", "seq",
    "commands": [...]}.
    """

    url = "/api/lena/display/sync"
    name = "api:lena:display:sync"
    requires_auth = True

    async def post(self, request: web.Request) -> web.Response:
        """Reports and collects for one display."""
        hass = request.app[KEY_HASS]
        core = hass.data.get(DOMAIN)
        if core is None:
            return self.json_message("Lena isn't set up", status_code=404)
        try:
            body = await request.json()
        except ValueError:
            return self.json_message("Expected JSON", status_code=400)
        display_id = str(body.get("id") or "")
        device = next((d for d in displays(hass, core.entry.entry_id) if display_id_of(d) == display_id), None)
        if device is None:
            return self.json_message("Not a registered display: register first", status_code=404)
        countdowns = [
            {
                "kind": str(c.get("kind", "timer")).lower(),
                "label": str(c.get("label") or "")[:200],
                "at": int(c.get("at") or 0),
                "duration": int(c.get("duration") or 0),
                "ha_id": str(c.get("ha_id") or ""),
            }
            for c in (body.get("countdowns") or [])[:50]
            if isinstance(c, dict)
        ]
        volume = body.get("volume")
        wake = body.get("wake_word")
        core.hub.report(device.id, countdowns, int(volume) if isinstance(volume, (int, float)) else None,
                        str(wake) if isinstance(wake, str) and wake else None)
        try:
            after = int(body.get("after") or 0)
        except (TypeError, ValueError):
            after = 0
        commands, seq = core.hub.collect(device.id, after, body.get("boot"))
        return self.json({"boot": core.hub.boot, "seq": seq, "commands": commands})
