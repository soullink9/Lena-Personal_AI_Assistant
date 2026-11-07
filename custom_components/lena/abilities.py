"""Her abilities on the displays: timers, alarms, reminders, volume, saying something in another room, and
correcting what she remembers.

Timers are Home Assistant's own (its timer intents, for the display as an Assist satellite); alarms,
reminders and the volume are Lena's, since Home Assistant has none of its own.

Shared by her tools in Assist (llm_api.py) and the lena.* actions that her Open WebUI tools call, so she
behaves the same whichever answers. Each returns a small dict for her to read back, or raises
AbilityError with a sentence she can act on (ask which room, say it differently).

Timers, alarms and reminders ring on the display: the one named ("remind the kitchen…"), else the one
she was asked through. They're queued in the hub for that display to collect (see hub.py).
"""

from __future__ import annotations

from datetime import datetime, timedelta
import logging
import re
from typing import Any

from homeassistant.core import HomeAssistant
from homeassistant.helpers import intent
from homeassistant.util import dt as dt_util

from . import brain
from .hub import KINDS, TargetError

_LOGGER = logging.getLogger(__name__)

MAX_SECONDS = 24 * 60 * 60
MAX_TEXT = 200


class AbilityError(ValueError):
    """Couldn't do it; the message says why, in words she can pass on or act on."""


def _targets(core, device_id: str | list[str] | None, room: str | None):
    """The displays meant: the room named, else the display given (the one she was asked through)."""
    ids = [device_id] if isinstance(device_id, str) else list(device_id or [])
    try:
        if room and room.strip():
            return core.hub.targets(room=room)
        return core.hub.targets(asking=ids[0] if len(ids) == 1 else None, device_ids=ids if len(ids) > 1 else None)
    except TargetError as error:
        raise AbilityError(str(error)) from error


def _names(devices) -> str:
    """The displays' names, for her answer."""
    return ", ".join(d.name_by_user or d.name for d in devices)


def _spoken(seconds: int) -> str:
    """"5 minutes", "1 hour 30 minutes", "20 seconds"."""
    h, rest = divmod(int(seconds), 3600)
    m, s = divmod(rest, 60)
    parts = [f"{h} hour{'s' if h != 1 else ''}" if h else "", f"{m} minute{'s' if m != 1 else ''}" if m else "",
             f"{s} second{'s' if s != 1 else ''}" if s and not h else ""]
    return " ".join(p for p in parts if p) or "0 seconds"


def _label(text: str | None) -> str:
    return (text or "").strip()[:MAX_TEXT]


async def _timer_intent(core, device, intent_type: str, slots: dict[str, Any]) -> str:
    """Runs one of Home Assistant's timer intents for a display, and returns what Home Assistant says."""
    try:
        response = await intent.async_handle(
            core.hass, "lena", intent_type, {k: {"value": v} for k, v in slots.items() if v not in (None, "")},
            language=core.hass.config.language, device_id=device.id,
        )
    except intent.IntentHandleError as error:
        raise AbilityError(str(error) or "Home Assistant couldn't do that with its timers") from error
    # Outside a conversation Home Assistant gives the facts (its "speech slots") rather than a sentence;
    # the timers' status is put into words here.
    timers = (response.speech_slots or {}).get("timers")
    if intent_type == "HassTimerStatus" and timers is not None:
        if not timers:
            return "no timers"
        return "; ".join(
            f"{t.get('name') + ' timer' if t.get('name') else 'timer'}, {_spoken(int(t.get('total_seconds_left', 0)))} left"
            + ("" if t.get("is_active", True) else " (paused)")
            for t in timers
        )
    return response.speech.get("plain", {}).get("speech", "") or "Done."


async def set_timer(core, seconds: int, label: str | None = None, device_id=None, room: str | None = None) -> dict[str, Any]:
    """A Home Assistant timer for [seconds] on the display, with an optional name ("pasta")."""
    seconds = int(seconds)
    if not 1 <= seconds <= MAX_SECONDS:
        raise AbilityError("A timer can be from one second to 24 hours")
    devices = _targets(core, device_id, room)
    hours, rest = divmod(seconds, 3600)
    minutes, secs = divmod(rest, 60)
    for device in devices:
        await _timer_intent(core, device, "HassStartTimer", {"hours": hours or None, "minutes": minutes or None,
                                                              "seconds": secs or None, "name": _label(label)})
    return {"set": f"{_label(label) + ' timer' if label else 'Timer'} for {_spoken(seconds)}", "on": _names(devices)}


def _clock(text: str) -> tuple[int, int]:
    """"07:30", "7:30", "19:00" (24-hour), or "7:30 pm" / "7 am"."""
    match = re.fullmatch(r"\s*(\d{1,2})(?::(\d{2}))?\s*([ap]\.?m\.?)?\s*", text.lower())
    if not match:
        raise AbilityError(f"Couldn't read the time {text!r}: give it as HH:MM, like 07:30 or 19:00")
    hour, minute = int(match.group(1)), int(match.group(2) or 0)
    if match.group(3):
        if not 1 <= hour <= 12:
            raise AbilityError(f"{text} isn't a time")
        hour = hour % 12 + (12 if match.group(3).startswith("p") else 0)
    if not (0 <= hour <= 23 and 0 <= minute <= 59):
        raise AbilityError(f"{text} isn't a time")
    return hour, minute


def set_alarm(core, at: str, label: str | None = None, device_id=None, room: str | None = None) -> dict[str, Any]:
    """An alarm at the next [at] (a time of day) on the display."""
    hour, minute = _clock(at)
    devices = _targets(core, device_id, room)
    core.hub.send(devices, "alarm", {"hour": hour, "minute": minute, "label": _label(label)})
    when = _next(hour, minute)
    return {"set": f"Alarm for {when.strftime('%I:%M %p').lstrip('0')} {_day(when)}", "on": _names(devices)}


def _next(hour: int, minute: int) -> datetime:
    """The next time the clock shows hour:minute, in the house's time zone."""
    now = dt_util.now()
    when = now.replace(hour=hour, minute=minute, second=0, microsecond=0)
    return when if when > now else when + timedelta(days=1)


def _day(when: datetime) -> str:
    days = (when.date() - dt_util.now().date()).days
    return "today" if days == 0 else "tomorrow" if days == 1 else when.strftime("on %A")


def set_reminder(core, message: str, at: str | None = None, in_seconds: int | None = None,
                 device_id=None, room: str | None = None) -> dict[str, Any]:
    """A reminder: [message] said on the display at a time ([at]: "19:00" for the next 7 PM, or a date and
    time like 2026-11-08T07:30) or after [in_seconds].
    """
    message = _label(message)
    if not message:
        raise AbilityError("Say what to remind them of")
    now = dt_util.now()
    if in_seconds:
        seconds = int(in_seconds)
        if not 1 <= seconds <= 7 * MAX_SECONDS:
            raise AbilityError("A reminder can be from one second to a week away")
        when = now + timedelta(seconds=seconds)
    elif at and at.strip():
        parsed = dt_util.parse_datetime(at.strip())
        if parsed is not None:
            when = parsed if parsed.tzinfo else parsed.replace(tzinfo=dt_util.get_default_time_zone())
            if when <= now:
                raise AbilityError(f"{at} has already passed")
        else:
            when = _next(*_clock(at))
    else:
        raise AbilityError("Say when: a time (at) or how long from now (in_seconds)")
    if when - now > timedelta(days=7):
        raise AbilityError("A reminder can be at most a week away; put it on the calendar instead")
    devices = _targets(core, device_id, room)
    core.hub.send(devices, "reminder", {"at": int(when.timestamp() * 1000), "message": message})
    soon = when - now < timedelta(hours=1)
    said = f"in {_spoken(int((when - now).total_seconds()))}" if soon else f"at {when.strftime('%I:%M %p').lstrip('0')} {_day(when)}"
    return {"set": f"Reminder {said}: {message}", "on": _names(devices)}


async def cancel(core, kind: str | None = None, label: str | None = None, everything: bool = False,
                 device_id=None, room: str | None = None, timer_id: str | None = None) -> dict[str, Any]:
    """Cancels timers, alarms or reminders on the display: the one named ([label]), all of a [kind], or the
    only one there is. Timers are Home Assistant's (one can be named by its [timer_id]); alarms and
    reminders are the display's own, cancelled from what it reported.
    """
    kind = (kind or "").lower().rstrip("s") or None
    if kind not in (None, *KINDS):
        raise AbilityError("Cancel a timer, an alarm or a reminder")
    if timer_id:
        from homeassistant.components.intent.timers import TIMER_DATA

        try:
            core.hass.data[TIMER_DATA].cancel_timer(timer_id)
        except Exception as error:  # noqa: BLE001 - an unknown or finished timer
            raise AbilityError("That timer isn't running") from error
        return {"cancelled": ["timer"]}
    devices = _targets(core, device_id, room)
    own = [c for c in core.hub.countdowns(devices) if c["kind"] in ("alarm", "reminder") and (kind is None or c["kind"] == kind)]
    label_l = _label(label).lower()
    named_own = [c for c in own if label_l and (label_l in c.get("label", "").lower() or c.get("label", "").lower() in label_l)]
    if kind == "timer" or (kind is None and not named_own):
        said = []
        for device in devices:
            if everything:
                said.append(await _timer_intent(core, device, "HassCancelAllTimers", {}))
            else:
                said.append(await _timer_intent(core, device, "HassCancelTimer", {"name": _label(label)}))
        return {"cancelled": said}
    have = own
    label = _label(label).lower()
    if label:
        meant = [c for c in have if label in c.get("label", "").lower() or c.get("label", "").lower() in label]
    else:
        meant = have
    if not meant:
        return {"cancelled": [], "note": "Nothing like that is set" + (f" (set: {', '.join(_describe(c) for c in have)})" if have else "")}
    if len(meant) > 1 and not everything:
        raise AbilityError("Several are set: " + "; ".join(_describe(c) for c in meant) + ". Say which, or cancel all of them")
    core.hub.send(devices, "cancel", {"kind": kind or meant[0]["kind"], "label": label, "all": bool(everything)})
    return {"cancelled": [_describe(c) for c in meant]}


def _describe(item: dict[str, Any]) -> str:
    """"pasta timer, 3 minutes left", "alarm at 7:30 AM", "reminder at 7:00 PM: take the trash out"."""
    at = dt_util.as_local(dt_util.utc_from_timestamp(item.get("at", 0) / 1000))
    left = max(0, int((at - dt_util.now()).total_seconds()))
    label = item.get("label") or ""
    if item["kind"] == "timer":
        return f"{label + ' timer' if label else 'timer'}, {_spoken(left)} left"
    clock = at.strftime("%I:%M %p").lstrip("0")
    if item["kind"] == "alarm":
        return f"alarm at {clock} {_day(at)}" + (f" ({label})" if label else "")
    return f"reminder at {clock} {_day(at)}: {label}"


async def list_countdowns(core, device_id=None, room: str | None = None, everywhere: bool = False,
                          timers: bool = True) -> dict[str, Any]:
    """What's set: Home Assistant's timers (as it says them) and the display's alarms and reminders, on
    the display (or the room's), or on every display.
    """
    from .displays import displays

    devices = displays(core.hass, core.entry.entry_id) if everywhere else _targets(core, device_id, room)
    lines = []
    for device in devices if timers else []:
        try:
            said = await _timer_intent(core, device, "HassTimerStatus", {})
            lines.append(f"Timers on {device.name_by_user or device.name}: {said}")
        except AbilityError:
            pass
    items = [c for c in core.hub.countdowns(devices) if c["kind"] in ("alarm", "reminder")]
    lines += [f"{_describe(c)} (on {c['display']})" for c in items]
    return {"set": lines or ["Nothing is set."]}


def set_volume(core, level: int | None = None, change: str | None = None, device_id=None, room: str | None = None) -> dict[str, Any]:
    """Her speaking volume on the display: a level (0-100) or "quieter"/"louder"."""
    if level is None and change not in ("quieter", "louder"):
        raise AbilityError("Give a level from 0 to 100, or say quieter or louder")
    devices = _targets(core, device_id, room)
    data: dict[str, Any] = {"level": max(0, min(100, int(level)))} if level is not None else {"change": change}
    core.hub.send(devices, "volume", data)
    return {"volume": data.get("level", change), "on": _names(devices)}


async def announce(core, message: str, listen: bool = False, device_id=None, room: str | None = None) -> dict[str, Any]:
    """Says [message] on the display(s) in [room] (the intercom), after the announcement chime, and with
    [listen] listens there for an answer. Sent straight to the displays, so unlike Home Assistant's
    assist_satellite.announce (which also works on them) it needs no text-to-speech engine: she says it in
    her own voice.
    """
    message = (message or "").strip()
    if not message:
        raise AbilityError("Say what to announce")
    devices = _targets(core, device_id, room)
    core.hub.send(devices, "announce", {"message": message[:1000], "listen": bool(listen), "chime": True})
    return {"announced": message, "on": _names(devices), "listening": bool(listen)}


def update_fact(core, old: str, new: str) -> dict[str, Any]:
    """Corrects a memory: the saved line that best matches [old] becomes [new], in its place."""
    new = (new or "").strip()
    if not new:
        raise AbilityError("Say what the memory should say now")
    match = brain.best_match(old or "", core.memories())
    if match is None:
        raise AbilityError("Nothing like that is saved; save it as a new memory instead")
    for item in core.memory:
        if item.get("status") != "completed" and item["summary"] == match:
            item["summary"] = new[:300]
            break
    core.memory_changed()
    return {"was": match, "now": new[:300]}
