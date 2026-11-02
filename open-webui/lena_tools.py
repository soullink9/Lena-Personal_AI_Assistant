"""
title: Lena (remember, forget, feel, calendar)
author: soullink9
version: 0.6.0-beta
license: GPL-3.0
description: Lets Lena save to and remove from her Home Assistant memory list, record how something made her feel, and add events to your Home Assistant calendars, from Open WebUI and the Lena app.
"""

# =============================================================================
# Lena - Open WebUI tools
# =============================================================================
# WHERE THIS GOES
#   Open WebUI > Workspace > Tools > + (New tool). Paste this whole file and
#   save. Then attach it to the "lena" model: Workspace > Models > lena >
#   Tools > tick "Lena (remember, forget, feel, calendar)". The Lena app uses the
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
