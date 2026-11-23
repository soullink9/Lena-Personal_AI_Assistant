"""
title: Lena (feelings and memory)
author: soullink9
version: 0.6.49
license: GPL-3.0
description: Adds the time at home, who she's talking with, Lena's current feelings and her shared memory to every chat with her, from the Lena integration in Home Assistant, tells her how much time passed between messages, and puts right a tool name she gets wrong only in case or punctuation.
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
#   home, how she feels, what she remembers and the exact names of the devices
#   exposed to Assist (Home Assistant's tools match names exactly, so she'd
#   otherwise guess "curtains" for "Living Room Curtains"). It adds that to the chat's
#   system message. It also says who's asking (your Open WebUI name, matched
#   to the person of that name in Home Assistant, so she knows when you last
#   talked) and, from the Lena app, which display, so she knows the room and
#   keeps it soft in a bedroom at night (who_and_where). Her character itself comes from the Modelfile and is
#   always there; this only adds what's current. Answers are cached for a
#   minute, and if Home Assistant can't be reached she simply chats without it.
#
#   It also tells her how much time passed between messages (time_passing):
#   a model reads every message as if it came a moment ago, so coming back
#   to a chat days later, she'd pick up the old topic as if you'd never left.
#   Wherever 30 minutes or more passed between two messages, she gets a short
#   note there, and before your new message one saying how long it's been and
#   that the earlier conversation is over. The times come from the chat in
#   Open WebUI; the notes go with this request only and aren't saved. The
#   Lena app adds the same notes itself (TimePassing.kt, in the same words),
#   so its requests are left alone here. This works without Home Assistant.
#
#   And it checks the names of the tools she calls as her answer streams in:
#   Open WebUI calls an MCP server's tools "<server ID>_<tool>", and a model
#   tends to write a hyphen in the ID as an underscore. Open WebUI then
#   answers "Tool not found" and she has nothing to answer with. A name that
#   differs from one of the tools she was given only in case or punctuation
#   is put right; any other name is left alone.
#
# THE TOKEN
#   A Home Assistant admin's long-lived access token, or one of a user allowed
#   in Lena's options (Who may use Lena), works (Profile >
#   Security > Long-lived access tokens). A user just for Lena, without admin
#   rights, is the safer choice. Open WebUI keeps it on your server; only
#   Open WebUI admins can see function settings.
# =============================================================================

import asyncio
import inspect
import json
import logging
import re
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timezone
from typing import Optional
from zoneinfo import ZoneInfo

from pydantic import BaseModel, Field

log = logging.getLogger(__name__)


def _get(url: str, token: str, timeout: float) -> dict:
    """GET JSON from Home Assistant. Standard library only."""
    request = urllib.request.Request(url, headers={"Authorization": f"Bearer {token}"})
    with urllib.request.urlopen(request, timeout=timeout) as reply:
        return json.loads(reply.read().decode())


# A pause this long or longer ends a conversation: what came before it is an earlier one.
NEW_CONVERSATION_SECONDS = 30 * 60

# The Lena app's requests: it adds the notes on time passing itself (see TimePassing.kt).
APP_SESSION_PREFIX = "lena-"


def _seconds(t) -> Optional[float]:
    """A message's time in seconds. Open WebUI has stored seconds, milliseconds or nanoseconds in
    different places; this tells them apart by size, as the app does.
    """
    try:
        t = float(t)
    except (TypeError, ValueError):
        return None
    if t <= 0:
        return None
    if t > 1e18:
        return t / 1e9
    if t > 1e12:
        return t / 1e3
    return t


def _text(content) -> str:
    """A message's text, whether it's a string or a list of parts (text and images)."""
    if isinstance(content, str):
        return content
    if isinstance(content, list):
        return "\n".join(p.get("text", "") for p in content if isinstance(p, dict) and p.get("type") == "text")
    return ""


def _count(n: int, one: str, many: str) -> str:
    return one if n == 1 else f"{n} {many}"


def since(gap_seconds: float) -> str:
    """A pause the way a person says it: "45 minutes", "an hour", "3 hours", "a day", "5 days",
    "2 weeks", "4 months", "over a year", to the nearest of each. The same as the app's
    TimePassing.since.
    """
    minutes = max(1, int(gap_seconds // 60))
    days = (minutes + 720) // 1440
    if minutes < 60:
        return _count(minutes, "a minute", "minutes")
    if minutes < 23 * 60 + 30:
        return _count((minutes + 30) // 60, "an hour", "hours")
    if days < 14:
        return _count(days, "a day", "days")
    if days < 60:
        return _count((days + 3) // 7, "a week", "weeks")
    if days < 365:
        return _count((days + 15) // 30, "a month", "months")
    return "over a year"


def part_of_day(hour: int) -> str:
    """The part of the day an hour falls in, the same split as the app's."""
    if 5 <= hour <= 11:
        return "morning"
    if 12 <= hour <= 16:
        return "afternoon"
    if 17 <= hour <= 20:
        return "evening"
    return "night"


def when_was(then: datetime, now: datetime) -> str:
    """When the earlier message was: "earlier today", "yesterday evening", "Thursday evening" within
    the week, else its date ("November 2", with the year if it was another year).
    """
    days = (now.date() - then.date()).days
    if days <= 0:
        return "earlier today"
    if days == 1:
        return f"yesterday {part_of_day(then.hour)}"
    if days < 7:
        return f"{then.strftime('%A')} {part_of_day(then.hour)}"
    date = f"{then.strftime('%B')} {then.day}"
    return date if then.year == now.year else f"{date}, {then.year}"


def before_new_message(then: datetime, now: datetime) -> str:
    """The note before the new message: how long it's been, and that the earlier conversation is over.
    Word for word the app's TimePassing.beforeNewMessage.
    """
    return (
        f"It's been {since((now - then).total_seconds())} since the last message in this chat "
        f"({when_was(then, now)}). That conversation is over: answer what they say now, and don't go back "
        "to its topic unless they do. What was said then was true then, not necessarily now."
    )


def between(then: datetime, after: datetime) -> str:
    """The note between two earlier messages with a long pause between them (the app's TimePassing.between)."""
    clock = f"{after.strftime('%A, %B')} {after.day}, {after.hour % 12 or 12}:{after.minute:02d} {'AM' if after.hour < 12 else 'PM'}"
    return f"Time passed: the next message is from {clock}, {since((after - then).total_seconds())} after the one above."


def earlier_messages(history: dict, start_id: Optional[str], new_text: str) -> list[dict]:
    """The chat's questions and answers before the new message, oldest first, from Open WebUI's saved
    history: the branch from [start_id] (the reply being written, or the chat's latest message) back to
    the start. Open WebUI keeps a chat as a tree, so it's found by parent links, not list order. The new
    message and its empty reply may or may not be saved yet; the new message is recognised by its text.
    """
    messages = history.get("messages") or {}
    chain: list[dict] = []
    seen: set = set()
    cursor = start_id if start_id in messages else history.get("currentId")
    while cursor and cursor in messages and cursor not in seen:
        seen.add(cursor)
        chain.append(messages[cursor])
        cursor = messages[cursor].get("parentId")
    # chain is newest first. Drop the new turn: everything down to the new message, if it's there.
    for i, m in enumerate(chain):
        if m.get("role") == "user" and _text(m.get("content")).strip() == new_text.strip():
            chain = chain[i + 1:]
            break
    else:
        chain = [m for m in chain if _text(m.get("content")).strip()]
    return [m for m in reversed(chain) if m.get("role") in ("user", "assistant")]


def with_time_notes(messages: list[dict], earlier: list[dict], now: float, zone) -> list[dict]:
    """[messages] (the request's, new message last) with a system note before each message that came
    after a pause of NEW_CONVERSATION_SECONDS or more. The request's messages carry no times, so they're
    matched with [earlier] (the saved ones) from the end, as long as the roles agree.
    """
    turns = [i for i, m in enumerate(messages) if m.get("role") in ("user", "assistant")]
    if not turns or messages[turns[-1]].get("role") != "user":
        return messages
    times: dict[int, float] = {turns[-1]: now}
    for index, saved in zip(reversed(turns[:-1]), reversed(earlier)):
        if messages[index].get("role") != saved.get("role"):
            break
        at = _seconds(saved.get("timestamp"))
        if at is not None:
            times[index] = at
    # The new message is measured from the latest saved message, even one the request left out.
    latest = next((t for t in (_seconds(m.get("timestamp")) for m in reversed(earlier)) if t is not None), None)

    out: list[dict] = []
    previous: Optional[float] = None
    for i, m in enumerate(messages):
        at = times.get(i)
        if i == turns[-1]:
            previous = latest if latest is not None else previous
        if previous is not None and at is not None and at - previous >= NEW_CONVERSATION_SECONDS:
            then = datetime.fromtimestamp(previous, zone)
            later = datetime.fromtimestamp(at, zone)
            text = before_new_message(then, later) if i == turns[-1] else between(then, later)
            out.append({"role": "system", "content": text})
        out.append(m)
        if at is not None:
            previous = at
    return out


def _tool_key(name: str) -> str:
    """A tool name with case and punctuation ignored: "home-assistant_homeassistant__GetLiveContext"
    and "home_assistant_homeassistant__getlivecontext" give the same key.
    """
    return re.sub(r"[^0-9a-z]+", "_", name.casefold()).strip("_")


def offered_tool_names(body: Optional[dict], metadata: Optional[dict]) -> set:
    """The names of the tools this request offered her: those sent to the model, and those Open WebUI
    resolved for it (which include any Tool Search held back).
    """
    names = set()
    for tool in (body or {}).get("tools") or []:
        if isinstance(tool, dict):
            name = (tool.get("function") or tool).get("name")
            if isinstance(name, str) and name:
                names.add(name)
    tools = (metadata or {}).get("tools")
    if isinstance(tools, dict):
        names.update(name for name in tools if isinstance(name, str) and name)
    return names


def matching_tool_name(name: str, offered: set) -> Optional[str]:
    """The offered tool [name] was meant to be, when she misspelt it only in case or punctuation, else None.

    Open WebUI names an MCP server's tools "<server ID>_<tool>", so with Home Assistant's server added
    under the ID "home-assistant", its GetLiveContext is "home-assistant_homeassistant__GetLiveContext".
    A model tends to write that "home_assistant_...", and Open WebUI answers "Tool not found" to a name
    that's off by one character; she then had nothing to answer with. Only a tool she was offered, and
    only one matching exactly one, is used, so this never gives her a tool she didn't have.
    """
    if not name or name in offered:
        return None
    key = _tool_key(name)
    matches = [tool for tool in offered if _tool_key(tool) == key]
    return matches[0] if len(matches) == 1 else None


def fix_tool_names(event: dict, offered: set) -> list:
    """Puts the right names on the tool calls in one streamed piece of her answer (see
    matching_tool_name). Returns the (wrong, right) pairs it changed.
    """
    changed = []
    for choice in (event or {}).get("choices") or []:
        if not isinstance(choice, dict):
            continue
        for part in (choice.get("delta"), choice.get("message")):
            if not isinstance(part, dict):
                continue
            for call in part.get("tool_calls") or []:
                function = call.get("function") if isinstance(call, dict) else None
                if not isinstance(function, dict) or not isinstance(function.get("name"), str):
                    continue
                right = matching_tool_name(function["name"], offered)
                if right:
                    changed.append((function["name"], right))
                    function["name"] = right
    return changed


async def _saved_chat(chat_id: str) -> Optional[dict]:
    """The chat as Open WebUI saved it (with each message's time), or None. Open WebUI's own models are
    only importable inside Open WebUI; some of its versions make this call async.
    """
    from open_webui.models.chats import Chats  # noqa: PLC0415 - only exists inside Open WebUI

    chat = Chats.get_chat_by_id(chat_id)
    if inspect.isawaitable(chat):
        chat = await chat
    return getattr(chat, "chat", None) if chat is not None else None


class Filter:
    """Open WebUI calls inlet() with every request to the model it's attached to (only lena), before
    the model sees it, and stream() with each piece of her answer as it comes.
    """
    class Valves(BaseModel):
        """The filter's settings, shown under its gear icon in Open WebUI and kept on the server."""
        ha_url: str = Field(
            default="http://homeassistant.local:8123",
            description="Your Home Assistant address, as Open WebUI's server can reach it.",
        )
        ha_token: str = Field(default="", description="A long-lived access token from Home Assistant.")
        memory: bool = Field(default=True, description="Include her shared memory.")
        devices: bool = Field(
            default=True,
            description="Include the exact names of the devices exposed to Assist, by room, so she finds them the first time.",
        )
        who_and_where: bool = Field(
            default=True,
            description="Tell Home Assistant who's asking (your Open WebUI name, matched to the person of that name) and from which Lena display, so she knows who she's talking with, when you last talked, and the room.",
        )
        time_passing: bool = Field(
            default=True,
            description="Tell her how much time passed between messages, so after a break she doesn't pick up the old topic as if you'd never left.",
        )
        cache_seconds: int = Field(default=60, description="How long to reuse Home Assistant's answer.")
        priority: int = Field(default=0, description="Order among filters (lower runs first).")

    def __init__(self):
        """Open WebUI creates the filter once and keeps it, so the cache lives across requests."""
        self.valves = self.Valves()
        # Home Assistant's answers, by who's asking and from where: {(user, display): (time, text)}.
        self._cache: dict[tuple[str, str], tuple[float, str]] = {}

    async def _context(self, user: str = "", display: str = "") -> str:
        """Fetches her context from Home Assistant, or an empty string if it can't be reached: she
        should still chat when Home Assistant is down. The request runs in a thread because urllib
        blocks and Open WebUI's server is async. [user] and [display] say who's asking and through which
        Lena display, when known.
        """
        params = {}
        if not self.valves.memory:
            params["memory"] = "0"
        if self.valves.devices:
            params["devices"] = "1"
        if user:
            params.update({"user": user, "talking": "1"})
        if display:
            params["device"] = display
        query = urllib.parse.urlencode(params)
        url = self.valves.ha_url.rstrip("/") + "/api/lena/context" + (f"?{query}" if query else "")
        try:
            body = await asyncio.to_thread(_get, url, self.valves.ha_token, 4)
            return (body.get("context") or "").strip()
        except urllib.error.HTTPError as error:
            # A 403 from Lena means the token's user isn't allowed (Who may use Lena); her answer says so.
            try:
                reason = json.loads(error.read()).get("message") or error
            except Exception:  # noqa: BLE001 - not her answer: the plain error says enough
                reason = error
            log.warning("Lena filter: Home Assistant request failed: %s", reason)
            return ""
        except Exception as error:  # noqa: BLE001 - she should still chat if Home Assistant is down
            log.warning("Lena filter: Home Assistant request failed: %s", error)
            return ""

    async def _time_notes(self, body: dict, metadata: dict) -> None:
        """Adds the notes on time passing to the request's messages (see the top of this file). Anything
        it can't work out (a temporary chat, an Open WebUI that keeps chats differently) leaves the
        request as it was: she chats as before.
        """
        if str(metadata.get("session_id") or "").startswith(APP_SESSION_PREFIX):
            return
        chat_id = metadata.get("chat_id")
        messages = body.get("messages") or []
        if not chat_id or str(chat_id).startswith("local:") or not messages:
            return
        try:
            saved = await _saved_chat(chat_id)
            history = (saved or {}).get("history") or {}
            new_text = _text(messages[-1].get("content"))
            earlier = earlier_messages(history, metadata.get("message_id"), new_text)
            if not earlier:
                return
            # Times in the person's time zone, as their browser gives it; else the server's.
            variables = metadata.get("variables") or {}
            try:
                zone = ZoneInfo(variables.get("{{CURRENT_TIMEZONE}}") or "")
            except Exception:  # noqa: BLE001 - an unknown or missing zone
                zone = datetime.now(timezone.utc).astimezone().tzinfo
            body["messages"] = with_time_notes(messages, earlier, time.time(), zone)
        except Exception as error:  # noqa: BLE001 - she should still chat if this can't be worked out
            log.warning("Lena filter: couldn't tell her how much time passed: %s", error)

    async def inlet(self, body: dict, __user__: Optional[dict] = None, __metadata__: Optional[dict] = None) -> dict:
        """Adds the notes on time passing between messages, then her context to the request's system
        message (or as one, if there isn't one). Home Assistant is asked at most once a minute for each
        person and display: a chat sends many requests, and her feelings don't change that fast.
        """
        if self.valves.time_passing:
            await self._time_notes(body, __metadata__ or {})
        if not self.valves.ha_token:
            return body

        user, display = "", ""
        if self.valves.who_and_where:
            variables = (__metadata__ or {}).get("variables") or {}
            display = str(variables.get("{{LENA_DISPLAY}}") or "")[:64]
            # A wall display is shared: the account signed in there isn't necessarily who's talking.
            if not variables.get("{{LENA_SHARED}}"):
                user = str((__user__ or {}).get("name") or "")[:100]
        cached_at, text = self._cache.get((user, display), (0.0, ""))
        if time.monotonic() - cached_at > self.valves.cache_seconds:
            text = await self._context(user, display)
            # Old answers are dropped, so the cache stays as small as the household.
            self._cache = {k: v for k, v in self._cache.items() if time.monotonic() - v[0] <= self.valves.cache_seconds}
            self._cache[(user, display)] = (time.monotonic(), text)
        if not text:
            return body

        messages = body.get("messages") or []
        if messages and messages[0].get("role") == "system" and isinstance(messages[0].get("content"), str):
            messages[0]["content"] = f"{messages[0]['content']}\n\n{text}"
        else:
            messages.insert(0, {"role": "system", "content": text})
        body["messages"] = messages
        return body

    def stream(self, event: dict, __body__: Optional[dict] = None, __metadata__: Optional[dict] = None) -> dict:
        """Corrects a tool name she got wrong only in case or punctuation before Open WebUI looks the
        tool up (see matching_tool_name). Anything unexpected leaves the piece as it was: Open WebUI
        stops the answer when a stream filter fails.
        """
        try:
            if not isinstance(event, dict) or not event.get("choices"):
                return event
            for wrong, right in fix_tool_names(event, offered_tool_names(__body__, __metadata__)):
                log.info("Lena filter: she called %r; using the tool she was given, %r", wrong, right)
        except Exception as error:  # noqa: BLE001 - never break her answer over this
            log.warning("Lena filter: couldn't check a tool name: %s", error)
        return event
