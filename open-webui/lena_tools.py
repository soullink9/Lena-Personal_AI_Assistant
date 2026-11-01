"""
title: Lena (remember, forget, feel)
author: soullink9
version: 0.5.1-beta
license: GPL-3.0
description: Lets Lena save to and remove from her Home Assistant memory list, and record how something made her feel, from Open WebUI and the Lena app.
"""

# =============================================================================
# Lena - Open WebUI tools
# =============================================================================
# WHERE THIS GOES
#   Open WebUI > Workspace > Tools > + (New tool). Paste this whole file and
#   save. Then attach it to the "lena" model: Workspace > Models > lena >
#   Tools > tick "Lena (remember, forget, feel)". The Lena app uses the
#   model's tools automatically.
#   Fill in the settings (the gear icon next to the tool): the same Home
#   Assistant address and token as the Lena filter. Any user's token works.
#
# WHAT IT DOES
#   remember / forget  add to or remove from her memory list (todo.lena_memory),
#                      the same list she uses in Home Assistant.
#   feel               runs the lena.feel action, the same way her voice does,
#                      so her reaction carries everywhere.
#
# The moment names must match feelings.yaml in the Lena integration.
# tools/check.py checks that.
# =============================================================================

import asyncio
import json
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
