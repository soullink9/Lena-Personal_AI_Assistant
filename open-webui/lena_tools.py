"""
title: Lena (memory, feelings, calendar, timers, intercom, history)
author: soullink9
version: 0.6.11
license: GPL-3.0
description: Lets Lena save, correct and remove her Home Assistant memories, record how something made her feel, add calendar events, set timers, alarms and reminders on her displays, change her volume, speak in another room, and look back at the house's history, from Open WebUI and the Lena app.
"""

# =============================================================================
# Lena - Open WebUI tools
# =============================================================================
# WHERE THIS GOES
#   Open WebUI > Workspace > Tools > + (New tool). Paste this whole file and
#   save. Then attach it to the "lena" model: Workspace > Models > lena >
#   Tools > tick "Lena (memory, feelings, calendar, timers, intercom, history)". The Lena app uses the
#   model's tools automatically.
#   Fill in the settings (the gear icon next to the tool): the same Home
#   Assistant address and token as the Lena filter. Any user's token works.
#
# WHAT IT DOES
#   remember / forget  add to or remove from her memory list (todo.lena_memory),
#                      the same list she uses in Home Assistant.
#   feel               runs the lena.feel action, the same way her voice does,
#                      so her reaction carries everywhere.
#   add_calendar_event runs the lena.add_event action, which adds the event with
#                      Home Assistant's calendar.create_event, to a calendar
#                      exposed to Assist that takes new events.
#   update_fact        corrects a memory in place (lena.update_fact).
#   set_timer, set_alarm, set_reminder, cancel_timer, list_timers, set_volume
#                      act on a Lena display: the room named, or the one you're
#                      talking through (the Lena app says which with each
#                      question). They ring and speak there.
#   announce           the intercom: says something on another room's display,
#                      and can listen there for an answer.
#   house_history      when something last happened, how long it lasted, or what
#                      happened today, from Home Assistant's history.
#
# The moment names must match feelings.yaml in the Lena integration.
# tools/check.py checks that.
# =============================================================================

import asyncio
import json
import urllib.error
import urllib.request
from typing import Literal

from pydantic import BaseModel, Field


def _post(url: str, token: str, payload: dict) -> dict | list:
    """POST JSON to Home Assistant and return the decoded reply. Standard library only."""
    request = urllib.request.Request(
        url,
        data=json.dumps(payload).encode(),
        headers={"Authorization": f"Bearer {token}", "Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=10) as reply:
        return json.loads(reply.read().decode() or "null")


def _problem(error: Exception) -> str:
    """What went wrong, in Home Assistant's own words when it gave any: an action that refuses (an
    unknown calendar, say) answers HTTP 400 with a message saying how to fix it.
    """
    if isinstance(error, urllib.error.HTTPError):
        try:
            message = json.loads(error.read().decode() or "{}").get("message")
        except (ValueError, AttributeError):
            message = None
        return message or f"Home Assistant answered HTTP {error.code}"
    return str(error)


def _best_match(wanted: str, items: list[str]) -> str | None:
    """The saved memory someone most likely means: a direct quote first, then the one sharing the
    most meaningful words (compared by their first five letters). Same rule as the integration."""
    text = wanted.strip().lower()
    if not text:
        return None
    for item in items:
        if text in item.lower():
            return item

    def words(s: str) -> set[str]:
        """The words that matter, cut to five letters, the same rule as best_match in the integration's
        brain.py, so both pick the same memory to forget.
        """
        cleaned = (w.strip(".,!?'\"") for w in s.lower().split())
        return {w[:5] for w in cleaned if len(w) >= 4}

    want = words(text)
    scored = [(len(want & words(item)), item) for item in items]
    best = max(scored, default=(0, None), key=lambda pair: pair[0])
    return best[1] if best[0] > 0 else None


class Tools:
    """Open WebUI turns each public method's signature and docstring into a tool for the model; methods
    starting with _ are left out.
    """
    class Valves(BaseModel):
        """The tool's settings, shown under its gear icon in Open WebUI and kept on the server."""
        ha_url: str = Field(
            default="http://homeassistant.local:8123",
            description="Your Home Assistant address, as Open WebUI's server can reach it.",
        )
        ha_token: str = Field(default="", description="A long-lived access token from Home Assistant.")
        memory_list: str = Field(default="todo.lena_memory", description="Her memory to-do list.")

    def __init__(self):
        """Open WebUI creates the tool once and keeps it."""
        self.valves = self.Valves()

    async def _call(self, domain: str, service: str, data: dict, response: bool = False):
        """Calls a Home Assistant action over its REST API, optionally asking for the action's response
        (needed to read the memory list). The request runs in a thread because urllib blocks and
        Open WebUI's server is async.
        """
        url = f"{self.valves.ha_url.rstrip('/')}/api/services/{domain}/{service}"
        if response:
            url += "?return_response"
        return await asyncio.to_thread(_post, url, self.valves.ha_token, data)

    @staticmethod
    def _display(metadata: dict | None) -> list[str]:
        """The Lena display the question came from: the app sends its Home Assistant device with each
        question (as the variable {{LENA_DISPLAY}}). Empty from the web page.
        """
        device = ((metadata or {}).get("variables") or {}).get("{{LENA_DISPLAY}}")
        return [device] if isinstance(device, str) and device else []

    async def _do(self, service: str, data: dict, metadata: dict | None = None, response: bool = True) -> str:
        """Runs one of her lena.* actions on a display and returns its answer for her to read, or what went
        wrong in words she can act on (ask which room, say a time differently).
        """
        if not self.valves.ha_token:
            return "Not set up: the tool has no Home Assistant token."
        data = {key: value for key, value in data.items() if value not in (None, "")}
        if metadata is not None and "device_id" not in data and not data.get("room"):
            if display := self._display(metadata):
                data["device_id"] = display
        try:
            reply = await self._call("lena", service, data, response=response)
        except Exception as error:
            return f"Didn't work: {_problem(error)}"
        answer = reply.get("service_response") if isinstance(reply, dict) else None
        return json.dumps(answer) if answer else "Done."

    async def remember(self, fact: str) -> str:
        """
        Save something to your memory, which is shared with Home Assistant and the phone.
        Use it when someone asks you to remember something, or tells you a lasting fact that
        clearly matters (a preference, a routine, a name, a standing rule). Never save passing
        moods, one-off requests, things already saved, or secrets like passwords or door codes.
        :param fact: One short statement in the third person, under fifteen words, saying who it's about. For example "Alex likes the office at seventy degrees."
        """
        if not self.valves.ha_token:
            return "Memory isn't set up: the tool has no Home Assistant token."
        try:
            await self._call("todo", "add_item", {"entity_id": self.valves.memory_list, "item": fact.strip()})
        except Exception as error:
            return f"Saving failed: {error}. Tell the person it didn't save."
        return f"Saved: {fact.strip()}"

    async def forget(self, fact: str) -> str:
        """
        Remove something from your memory when someone asks you to forget it.
        :param fact: What to forget, in a few words. The closest saved memory is removed.
        """
        if not self.valves.ha_token:
            return "Memory isn't set up: the tool has no Home Assistant token."
        try:
            found = await self._call(
                "todo", "get_items", {"entity_id": self.valves.memory_list, "status": "needs_action"}, response=True
            )
            items = [
                i.get("summary", "")
                for i in found.get("service_response", {}).get(self.valves.memory_list, {}).get("items", [])
            ]
            match = _best_match(fact, items)
            if match is None:
                return "Nothing like that is saved, so there was nothing to forget."
            await self._call("todo", "remove_item", {"entity_id": self.valves.memory_list, "item": match})
        except Exception as error:
            return f"Forgetting failed: {error}. Tell the person it's still saved."
        return f"Forgot: {match}"

    async def feel(
        self,
        moment: Literal["Pleased", "Amused", "Proud", "Touched", "Delighted", "Exasperated", "Offended"],
        reason: str,
    ) -> str:
        """
        Record how something the person just said or did made you feel. Use it only when it
        genuinely affects you: real thanks or praise, a kind or personal word, something funny,
        rudeness, or the same request yet again. Never for ordinary requests, at most once per
        reply, and don't mention using it.
        :param moment: How it made you feel.
        :param reason: What happened, in a few words, written to yourself. For example "Alex thanked you for sorting the heating."
        """
        if not self.valves.ha_token:
            return "Not set up."
        try:
            await self._call("lena", "feel", {"moment": moment, "reason": reason.strip()})
        except Exception as error:
            return f"Couldn't record it: {error}"
        return "Noted."

    async def add_calendar_event(
        self,
        calendar: str,
        title: str,
        start: str,
        end: str = "",
        location: str = "",
        description: str = "",
    ) -> str:
        """
        Add an event to a Home Assistant calendar when someone asks you to put something on it, book it or
        remind them on a day. Times are the house's local time. Say what you added only after this succeeds.
        :param calendar: The calendar's exact name, as in your context's list of the home.
        :param title: What it is, in a few words. For example "Dentist".
        :param start: A date like 2026-11-14 for an all-day event, or a date and time like 2026-11-14T15:30.
        :param end: Optional. For an all-day event the last day (2026-11-16); for a timed event when it ends (2026-11-14T16:30). Leave empty for one day, or one hour.
        :param location: Optional. Where it is.
        :param description: Optional. Any details worth keeping.
        """
        if not self.valves.ha_token:
            return "The calendar isn't set up: the tool has no Home Assistant token."
        data = {"calendar": calendar.strip(), "title": title.strip(), "start": start.strip()}
        optional = {"end": end, "location": location, "description": description}
        data.update({key: value.strip() for key, value in optional.items() if value and value.strip()})
        try:
            reply = await self._call("lena", "add_event", data, response=True)
        except Exception as error:
            return f"Not added: {_problem(error)} Correct it as that says and try once more, or tell the person it failed."
        added = (reply or {}).get("service_response", {}) if isinstance(reply, dict) else {}
        when = added.get("start", data["start"])
        return f"Added \"{added.get('added', data['title'])}\" to {added.get('calendar', calendar)}, starting {when}."

    async def update_fact(self, old: str, new: str) -> str:
        """
        Correct something you remember when it's changed or was wrong (Alex moved to the night shift), instead
        of saving a second, conflicting memory.
        :param old: The memory to change, in a few words.
        :param new: What it should say now: one short third-person statement.
        """
        return await self._do("update_fact", {"old": old, "new": new})

    async def set_timer(self, seconds: int, label: str = "", room: str = "", __metadata__: dict | None = None) -> str:
        """
        Start a timer that rings on the Lena display. One call per timer: for several requests in one sentence
        (a pasta timer and a reminder), make each call.
        :param seconds: How long, in seconds (5 minutes is 300).
        :param label: Optional. What it's for, in a word or two, e.g. pasta.
        :param room: Optional. The room, when they name one; leave empty for the display you're talking through.
        """
        return await self._do("set_timer", {"seconds": seconds, "label": label, "room": room}, __metadata__)

    async def set_alarm(self, time: str, label: str = "", room: str = "", __metadata__: dict | None = None) -> str:
        """
        Set an alarm that rings on the Lena display at the next time the clock shows that time.
        :param time: 24-hour HH:MM, e.g. 06:45 or 19:00.
        :param label: Optional. What it's for.
        :param room: Optional. The room, when they name one; leave empty for the display you're talking through.
        """
        return await self._do("set_alarm", {"time": time, "label": label, "room": room}, __metadata__)

    async def set_reminder(self, message: str, at: str = "", in_seconds: int = 0, room: str = "", __metadata__: dict | None = None) -> str:
        """
        Remind them of something, said out loud on the Lena display: at a time ("at 7") or after a while ("in 20
        seconds"). Give either at or in_seconds. For a later day than next week, add a calendar event instead.
        :param message: What to remind them of, said to them, e.g. Time to flip the chicken.
        :param at: When: 24-hour HH:MM for the next time the clock shows it (19:00), or a date and time (2026-11-08T07:30).
        :param in_seconds: Or: how many seconds from now.
        :param room: Optional. The room, when they name one; leave empty for the display you're talking through.
        """
        return await self._do("set_reminder", {"message": message, "at": at, "in_seconds": in_seconds or None, "room": room}, __metadata__)

    async def cancel_timer(self, kind: Literal["timer", "alarm", "reminder", ""] = "", label: str = "", all: bool = False,
                           room: str = "", __metadata__: dict | None = None) -> str:
        """
        Cancel a timer, an alarm or a reminder on the Lena display: the one named, or all of a kind.
        :param kind: timer, alarm or reminder.
        :param label: Optional. Its name or what it's for, e.g. pasta.
        :param all: True to cancel all of them (of that kind).
        :param room: Optional. The room, when they name one.
        """
        return await self._do("cancel_timer", {"kind": kind, "label": label, "all": all, "room": room}, __metadata__)

    async def list_timers(self, everywhere: bool = False, room: str = "", __metadata__: dict | None = None) -> str:
        """
        What timers, alarms and reminders are set and how long is left, on this display or everywhere.
        :param everywhere: True for every Lena display in the house.
        :param room: Optional. The room, when they name one.
        """
        return await self._do("list_timers", {"everywhere": everywhere, "room": room}, __metadata__)

    async def set_volume(self, level: int = -1, change: Literal["quieter", "louder", ""] = "", room: str = "",
                         __metadata__: dict | None = None) -> str:
        """
        Change how loudly you speak on the Lena display: a level, or quieter or louder.
        :param level: 0 to 100 (leave as -1 to use change instead).
        :param change: quieter or louder.
        :param room: Optional. The room, when they name one.
        """
        return await self._do("set_volume", {"level": level if level >= 0 else None, "change": change, "room": room}, __metadata__)

    async def announce(self, message: str, room: str, listen: bool = False) -> str:
        """
        Say something out loud on the Lena display in another room, like an intercom ("tell the kitchen dinner's
        ready"). With listen, she listens there for an answer and answers it herself.
        :param message: What to say there, as you'd say it to them.
        :param room: The room to say it in.
        :param listen: True to listen for an answer after saying it.
        """
        result = await self._do("announce", {"message": message, "room": room, "listen": listen}, response=False)
        return f"Said in the {room}." if result == "Done." else result

    async def house_history(self, question: Literal["last_change", "how_long", "what_happened"], name: str = "",
                            state: str = "", hours: float = 0) -> str:
        """
        Look back at the house's history: when something last happened ("when did the garage door last open?"),
        how long something lasted ("how long was the back door open?"), or what happened today ("what did I
        miss?": people coming and going, doors, locks, alarms).
        :param question: last_change, how_long or what_happened.
        :param name: What to look up, by its name in the home (not for what_happened).
        :param state: Optional. The state asked about: open, on, home, locked.
        :param hours: Optional. How far back, in hours; today by default.
        """
        return await self._do("history", {"question": question, "name": name, "state": state, "hours": hours or None})
