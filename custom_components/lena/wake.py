"""Only one display answers: when several of Lena's displays hear her name at once, the one that heard you
best answers and the others go back to listening.

Each display that hears her name keeps recording (so nothing you say is lost) and claims the turn here
(POST /api/lena/display/wake) with how clearly it heard you: your voice's level above that room's own
background noise, and how sure its wake word was. Claims that arrive within a short window (Configure:
350 ms) are one "Hey Lena"; then brain.wake_winner picks one, and every display gets its answer on the
same request. A claim that comes later, but soon enough to be the same words heard late (WAKE_LINGER_MS),
loses to the one already chosen, unless it's a phone in someone's hand: that one always answers, and its
app doesn't wait for the answer (a phone that heard her a moment after a tablet used to stay silent).

A display that gets no answer in time (Home Assistant restarting, a slow network) answers anyway: two
answers are better than none. With Configure's "Only one display answers" off, every claim wins at once.
"""

from __future__ import annotations

import asyncio
import time
from dataclasses import dataclass, field
from typing import Any

from aiohttp import web

from homeassistant.core import HomeAssistant
from homeassistant.helpers.http import KEY_HASS, HomeAssistantView

from . import access, brain
from .const import CONF_WAKE_ONE, CONF_WAKE_WINDOW, DOMAIN
from .displays import display_id_of, displays


@dataclass
class _Round:
    """One "Hey Lena", as heard by one or more displays."""

    started: float
    claims: list[dict[str, Any]] = field(default_factory=list)
    decided: asyncio.Event = field(default_factory=asyncio.Event)
    winner: str | None = None


class Arbiter:
    """Gathers the claims for each "Hey Lena" and decides who answers."""

    def __init__(self, hass: HomeAssistant, core) -> None:
        self.hass = hass
        self.core = core
        self._round: _Round | None = None
        self.last: dict[str, Any] | None = None  # the last decision, for the log and diagnostics

    async def claim(self, device_id: str, level: float, score: float, handheld: bool) -> bool:
        """Claims the turn for a display, and waits until it's decided. True if this display answers."""
        if not self.core.opt(CONF_WAKE_ONE):
            return True
        window = max(0.1, min(1.0, float(self.core.opt(CONF_WAKE_WINDOW) or brain.WAKE_WINDOW_MS) / 1000))
        now = time.monotonic()
        current = self._round
        if current is None or now - current.started > brain.WAKE_LINGER_MS / 1000:
            current = self._round = _Round(started=now)
            self.hass.loop.call_later(window, self._decide, current)
        if current.decided.is_set():
            # The same words heard late: the display that already answered keeps the turn, except that a phone
            # in someone's hand always answers (the app doesn't wait for this answer there anyway).
            return handheld or current.winner == device_id
        current.claims.append({
            "id": device_id, "level": level, "score": score, "handheld": handheld, "order": len(current.claims),
        })
        if handheld:
            self._decide(current)  # someone's holding a phone and talking to it: no need to wait
        try:
            async with asyncio.timeout(window + 1.0):
                await current.decided.wait()
        except TimeoutError:
            return True
        return current.winner == device_id

    def _decide(self, current: _Round) -> None:
        """Picks the display that heard best, once, and lets every waiting claim know."""
        if current.decided.is_set():
            return
        current.winner = brain.wake_winner(current.claims)
        self.last = {"winner": current.winner, "claims": list(current.claims)}
        current.decided.set()


class LenaWakeView(HomeAssistantView):
    """POST /api/lena/display/wake {"id", "level", "score", "handheld"}: a display heard her name. Answers
    {"answer": true} if it should answer, or {"answer": false, "by": "Kitchen tablet"} when another display
    does.
    """

    url = "/api/lena/display/wake"
    name = "api:lena:display:wake"
    requires_auth = True

    async def post(self, request: web.Request) -> web.Response:
        hass = request.app[KEY_HASS]
        if denied := access.refused(self, request):  # admins and the users chosen in her options
            return denied
        core = hass.data.get(DOMAIN)
        if core is None or core.arbiter is None:
            return self.json_message("Lena isn't set up", status_code=404)
        try:
            body = await request.json()
        except ValueError:
            return self.json_message("Expected JSON", status_code=400)
        everyone = displays(hass, core.entry.entry_id)
        device = next((d for d in everyone if display_id_of(d) == str(body.get("id") or "")), None)
        if device is None:
            return self.json_message("Not a registered display", status_code=404)
        try:
            level = float(body.get("level") or 0.0)
            score = float(body.get("score") or 0.0)
        except (TypeError, ValueError):
            return self.json_message("level and score are numbers", status_code=400)
        answer = await core.arbiter.claim(device.id, level, score, bool(body.get("handheld")))
        if answer:
            return self.json({"answer": True})
        winner = next((d for d in everyone if d.id == (core.arbiter.last or {}).get("winner")), None)
        return self.json({"answer": False, "by": (winner.name_by_user or winner.name) if winner else None})
