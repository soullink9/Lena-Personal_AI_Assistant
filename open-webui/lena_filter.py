"""
title: Lena (feelings and memory)
author: soullink9
version: 2.0.0
license: GPL-3.0
description: Adds the time at home, Lena's current feelings and her shared memory to every chat with her, from the Lena integration in Home Assistant.
"""

# =============================================================================
# Lena - Open WebUI filter
# =============================================================================
# WHERE THIS GOES
#   Open WebUI > Admin Panel > Functions > + (New function). Paste this whole
#   file, save, and switch it on. Then attach it to the "lena" model only:
#   Workspace > Models > lena > Filters > tick "Lena (feelings and memory)".
#   Fill in the settings (the gear icon next to the function): your Home
#   Assistant address and a long-lived access token.
#
# WHAT IT DOES
#   Before each message reaches the model, it reads /api/lena/context from
#   the Lena integration in Home Assistant: the date and time at home, who's
#   home, how she feels and what she remembers. It adds that to the chat's
#   system message. Her character itself comes from the Modelfile and is
#   always there; this only adds what's current. Answers are cached for a
#   minute, and if Home Assistant can't be reached she simply chats without it.
#
# THE TOKEN
#   Any Home Assistant user's long-lived access token works (Profile >
#   Security > Long-lived access tokens). A user just for Lena, without admin
#   rights, is the safer choice. Open WebUI keeps it on your server; only
#   Open WebUI admins can see function settings.
# =============================================================================

import asyncio
import json
import logging
import time
import urllib.request
from typing import Optional

from pydantic import BaseModel, Field

log = logging.getLogger(__name__)


def _get(url: str, token: str, timeout: float) -> dict:
    """GET JSON from Home Assistant. Standard library only."""
    request = urllib.request.Request(url, headers={"Authorization": f"Bearer {token}"})
    with urllib.request.urlopen(request, timeout=timeout) as reply:
        return json.loads(reply.read().decode())


class Filter:
    class Valves(BaseModel):
        ha_url: str = Field(
            default="http://homeassistant.local:8123",
            description="Your Home Assistant address, as Open WebUI's server can reach it.",
        )
        ha_token: str = Field(default="", description="A long-lived access token from Home Assistant.")
        memory: bool = Field(default=True, description="Include her shared memory.")
        cache_seconds: int = Field(default=60, description="How long to reuse Home Assistant's answer.")
        priority: int = Field(default=0, description="Order among filters (lower runs first).")

    def __init__(self):
        self.valves = self.Valves()
        self._cache: tuple[float, str] = (0.0, "")

    async def _context(self) -> str:
        url = self.valves.ha_url.rstrip("/") + "/api/lena/context" + ("" if self.valves.memory else "?memory=0")
        try:
            body = await asyncio.to_thread(_get, url, self.valves.ha_token, 4)
            return (body.get("context") or "").strip()
        except Exception as error:  # noqa: BLE001 - she should still chat if Home Assistant is down
            log.warning("Lena filter: Home Assistant request failed: %s", error)
            return ""

    async def inlet(self, body: dict, __user__: Optional[dict] = None) -> dict:
        if not self.valves.ha_token:
            return body

        cached_at, text = self._cache
        if time.monotonic() - cached_at > self.valves.cache_seconds:
            text = await self._context()
            self._cache = (time.monotonic(), text)
        if not text:
            return body

        messages = body.get("messages") or []
        if messages and messages[0].get("role") == "system" and isinstance(messages[0].get("content"), str):
            messages[0]["content"] = f"{messages[0]['content']}\n\n{text}"
        else:
            messages.insert(0, {"role": "system", "content": text})
        body["messages"] = messages
        return body
