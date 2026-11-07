"""The house's history, from Home Assistant's recorder: when something last happened, how long it lasted,
and what happened today.

"When did the garage door last open?", "how long was the back door open?", "what did I miss today?".
She only sees what's exposed to Assist (the same as her other house tools), named the way you named it.
"""

from __future__ import annotations

from datetime import datetime, timedelta
from typing import Any

from homeassistant.core import HomeAssistant, State
from homeassistant.util import dt as dt_util

# The states that mean something is "happening": open, on, unlocked, someone home.
ACTIVE = {"on", "open", "opening", "unlocked", "unlocking", "home", "playing", "detected", "triggered", "jammed"}

# What "what did I miss" looks at: comings and goings, doors and windows, locks, covers, alarms and leaks.
NOTABLE_DOMAINS = {"person", "lock", "cover", "alarm_control_panel"}
NOTABLE_CLASSES = {"door", "garage_door", "window", "opening", "lock", "moisture", "smoke", "gas", "safety", "problem", "tamper"}
QUIET = {"unavailable", "unknown"}
MAX_LINES = 40


class HistoryError(ValueError):
    """Couldn't answer: no recorder, or no such thing in the house. The message says which."""


def _exposed(hass: HomeAssistant, entity_id: str) -> bool:
    """Whether she's allowed to see it: exposed to Assist, as her other house tools are."""
    try:
        from homeassistant.components.homeassistant.exposed_entities import async_should_expose

        return async_should_expose(hass, "conversation", entity_id)
    except (ImportError, KeyError):
        return True


def find(hass: HomeAssistant, name: str) -> State:
    """The thing someone means by its name ("garage door", "the back door", "Alex"): exact, then ignoring
    case and "the", then the one whose name contains it. Raises HistoryError listing close names.
    """
    wanted = name.strip().lower().removeprefix("the ").strip()
    if not wanted:
        raise HistoryError("Say what to look up")
    states = [s for s in hass.states.async_all() if _exposed(hass, s.entity_id)]
    for test in (
        lambda s: s.entity_id == name.strip(),
        lambda s: s.name.lower() == wanted,
        lambda s: wanted in s.name.lower(),
        lambda s: all(w in s.name.lower() for w in wanted.split()),
    ):
        found = [s for s in states if test(s)]
        if len(found) == 1:
            return found[0]
        if len(found) > 1:
            # Prefer doors, locks and people over their batteries and signal strengths.
            useful = [s for s in found if s.domain in NOTABLE_DOMAINS or s.attributes.get("device_class") in NOTABLE_CLASSES]
            if len(useful) == 1:
                return useful[0]
            raise HistoryError(f"Which one: {', '.join(sorted(s.name for s in found)[:8])}?")
    raise HistoryError(f"Nothing called {name} is shared with you")


async def _changes(hass: HomeAssistant, entity_ids: list[str] | None, start: datetime) -> dict[str, list[State]]:
    """Every recorded change since [start] (with the state at [start] first)."""
    if "recorder" not in hass.config.components:
        raise HistoryError("Home Assistant isn't recording history (the recorder is off)")
    from homeassistant.components.recorder import get_instance, history

    def read() -> dict[str, list[State]]:
        if entity_ids is not None and len(entity_ids) == 1:
            return history.state_changes_during_period(hass, start, entity_id=entity_ids[0], include_start_time_state=True)
        return history.get_significant_states(hass, start, entity_ids=entity_ids, significant_changes_only=True)

    return await get_instance(hass).async_add_executor_job(read)


def _when(moment: datetime) -> str:
    """"8:01 AM today", "6:30 PM yesterday", "Tuesday at 7:00 AM"."""
    local = dt_util.as_local(moment)
    days = (dt_util.now().date() - local.date()).days
    clock = local.strftime("%I:%M %p").lstrip("0")
    return f"{clock} today" if days == 0 else f"{clock} yesterday" if days == 1 else f"{local.strftime('%A')} at {clock}"


def _length(delta: timedelta) -> str:
    seconds = int(delta.total_seconds())
    h, rest = divmod(seconds, 3600)
    m, s = divmod(rest, 60)
    if h:
        return f"{h} hour{'s' if h != 1 else ''}" + (f" {m} minute{'s' if m != 1 else ''}" if m else "")
    if m:
        return f"{m} minute{'s' if m != 1 else ''}"
    return f"{s} second{'s' if s != 1 else ''}"


def _said(state: State) -> str:
    """The state as she'd say it ("open", "home", "on"), with the unit for a number."""
    unit = state.attributes.get("unit_of_measurement")
    return f"{state.state} {unit}" if unit else state.state


def _periods(changes: list[State], active: set[str]) -> list[tuple[datetime, datetime | None]]:
    """When it went into an active state and when it came out (None: still is)."""
    periods: list[tuple[datetime, datetime | None]] = []
    started: datetime | None = None
    for state in changes:
        if state.state in QUIET:
            continue
        if state.state in active and started is None:
            started = state.last_changed
        elif state.state not in active and started is not None:
            periods.append((started, state.last_changed))
            started = None
    if started is not None:
        periods.append((started, None))
    return periods


async def last_change(hass: HomeAssistant, name: str, state: str | None = None, days: int = 7) -> dict[str, Any]:
    """When it last changed (to [state], if given: "open", "on", "home"), looking back [days]."""
    target = find(hass, name)
    start = dt_util.utcnow() - timedelta(days=max(1, min(days, 30)))
    changes = [s for s in (await _changes(hass, [target.entity_id], start)).get(target.entity_id, []) if s.state not in QUIET]
    wanted = state.strip().lower() if state else None
    active = {wanted} if wanted else ACTIVE
    if not changes:
        return {"answer": f"{target.name} hasn't changed in the last {days} days; it's {_said(target)}"}
    periods = _periods(changes, active)
    # Without a state, something that's never "active" (a number, a mode) is answered by its last change.
    if not periods:
        if wanted:
            return {"answer": f"{target.name} hasn't been {wanted} in the last {days} days; it's {_said(target)}"}
        last = changes[-1]
        return {"answer": f"{target.name} last changed at {_when(last.last_changed)}, to {_said(last)}"}
    began, ended = periods[-1]
    what = wanted or next((s.state for s in reversed(changes) if s.state in active), "on")
    if ended is None:
        return {"answer": f"{target.name} has been {what} since {_when(began)} ({_length(dt_util.utcnow() - began)} so far)"}
    return {"answer": f"{target.name} was last {what} at {_when(began)}, for {_length(ended - began)} (until {_when(ended)})"}


async def how_long(hass: HomeAssistant, name: str, state: str | None = None, hours: float | None = None) -> dict[str, Any]:
    """How long it was (or has been) [state] ("open" by default for doors): the latest time, and the
    total and count over the last [hours] (since midnight by default).
    """
    target = find(hass, name)
    now = dt_util.utcnow()
    start = now - timedelta(hours=hours) if hours else dt_util.start_of_local_day()
    changes = (await _changes(hass, [target.entity_id], min(start, now - timedelta(days=7)))).get(target.entity_id, [])
    wanted = state.strip().lower() if state else None
    periods = _periods(changes, {wanted} if wanted else ACTIVE)
    if not periods:
        return {"answer": f"{target.name} hasn't been {wanted or 'open or on'} in the last week"}
    began, ended = periods[-1]
    latest = (f"the last time, from {_when(began)} for {_length((ended or now) - began)}"
              + (" (and still is)" if ended is None else ""))
    within = [(max(b, start), e or now) for b, e in periods if (e or now) > start]
    total = sum(((e - b) for b, e in within), timedelta())
    span = f"in the last {hours:g} hours" if hours else "today"
    return {"answer": f"{target.name}: {latest}. {span.capitalize()}: {len(within)} time{'s' if len(within) != 1 else ''}, {_length(total)} in all"}


async def what_happened(hass: HomeAssistant, hours: float | None = None) -> dict[str, Any]:
    """What happened in the house over the last [hours] (since midnight by default): people coming and
    going, doors, windows, locks, the garage, alarms and leaks. Not lights or motion, which are too many.
    """
    now = dt_util.utcnow()
    start = now - timedelta(hours=hours) if hours else dt_util.start_of_local_day()
    notable = [
        s.entity_id for s in hass.states.async_all()
        if (s.domain in NOTABLE_DOMAINS or (s.domain == "binary_sensor" and s.attributes.get("device_class") in NOTABLE_CLASSES))
        and _exposed(hass, s.entity_id)
    ]
    if not notable:
        return {"answer": "No doors, locks, people or alarms are shared with you, so there's nothing to look back on"}
    changes = await _changes(hass, notable, start)
    events: list[tuple[datetime, str]] = []
    for entity_id, states in changes.items():
        current = hass.states.get(entity_id)
        name = current.name if current else entity_id
        previous = None
        for state in states:
            if state.state in QUIET or state.last_changed < start:
                previous = state.state
                continue
            if state.state != previous and previous is not None:
                events.append((state.last_changed, f"{_when(state.last_changed)}: {name} {_said(state)}"))
            previous = state.state
    events.sort()
    lines = [line for _, line in events]
    span = f"the last {hours:g} hours" if hours else "today"
    if not lines:
        return {"answer": f"Nothing happened {span}: no one came or went, and no doors, locks or alarms changed"}
    if len(lines) > MAX_LINES:
        lines = [f"(the first {len(lines) - MAX_LINES} left out)"] + lines[-MAX_LINES:]
    return {"since": span, "happened": lines}
