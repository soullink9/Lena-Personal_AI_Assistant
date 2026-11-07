"""Lena tidying her own memory, now and then: combining what belongs together, making vague lines clear,
and retiring what's outdated or was only ever for a moment.

The thinking is done by Home Assistant's AI Task (ai_task.generate_data, your own model); this file
writes the request, checks the answer and works out the changes, so nothing it's unsure of is applied.
The core keeps a copy first (lena.undo_memory_tidy puts it back) and tells you what changed.

Retired lines are ticked off, not deleted: they stay on the list, out of her prompt, as when you tick
one off yourself. Ticked-off lines are never touched.
"""

from __future__ import annotations

import json
import re
import uuid
from dataclasses import dataclass, field
from datetime import datetime
from typing import Any

MIN_ITEMS = 4  # fewer than this, there's nothing worth tidying
MAX_TEXT = 300
MAX_RETIRED_SHARE = 0.5  # never retire more than half of what she remembers in one go


class TidyError(ValueError):
    """The model's answer couldn't be used safely; nothing is changed."""


@dataclass
class Plan:
    """What a tidy would change. memory is the whole new list (ticked-off lines included, in place)."""

    memory: list[dict[str, Any]]
    combined: list[tuple[list[str], str]] = field(default_factory=list)  # (the lines, the new line)
    clarified: list[tuple[str, str]] = field(default_factory=list)  # (before, after)
    retired: list[str] = field(default_factory=list)

    @property
    def changed(self) -> bool:
        """Whether there's anything to apply."""
        return bool(self.combined or self.clarified or self.retired)

    def summary(self) -> str:
        """What changed, for the notification, in a few short lines."""
        lines = []
        for sources, new in self.combined:
            lines.append(f"- Combined {len(sources)} into: {new}")
        for before, after in self.clarified:
            lines.append(f"- Clarified: {before} → {after}")
        for old in self.retired:
            lines.append(f"- Retired (ticked off): {old}")
        return "\n".join(lines)


def _age(item: dict[str, Any], now: datetime) -> str:
    """How long ago a line was saved, in words, or "unknown" for lines from before it was recorded."""
    added = item.get("added")
    try:
        days = (now - datetime.fromisoformat(added)).days if added else None
    except (TypeError, ValueError):
        days = None
    if days is None:
        return "saved a while ago"
    if days < 1:
        return "saved today"
    return f"saved {days} day{'s' if days != 1 else ''} ago"


def instructions(items: list[dict[str, Any]], now: datetime) -> str:
    """The request for the AI Task: her open memory lines, numbered, with their ages, and the rules."""
    numbered = "\n".join(f"{i}. {m['summary']} ({_age(m, now)})" for i, m in enumerate(items, 1))
    return (
        "You are tidying the memory of Lena, a household assistant. Each line is one thing she remembers "
        "about the people and the home, written in the third person. Today is "
        f"{now:%A, %B} {now.day}, {now.year}.\n\n"
        f"Her memory:\n{numbered}\n\n"
        "Rewrite it, following these rules:\n"
        "- Combine lines that are about the same thing into one line (\"Alex likes coffee.\" and \"Alex takes "
        "it black.\" become \"Alex likes coffee, black.\").\n"
        "- Clarify a vague line into one clear sentence, using only what the lines say. Never invent or "
        "guess facts.\n"
        "- Leave out lines that are outdated (a date or event that has passed, a fact a newer line replaces) "
        "or that were only ever for a moment. Keep everything else, even if it's old.\n"
        "- Keep each line short, third person, one fact or a few closely related ones.\n"
        "- Keep lines that are fine as they are word for word.\n\n"
        "Give back the new memory as a list of lines. Start each line with the numbers of the lines it comes "
        "from, in brackets, then the line: \"[1, 2] Alex likes coffee, black.\" for two combined, \"[3] Sam has a "
        "dentist appointment on Friday.\" for one clarified, \"[5] Pat bikes to work.\" for one kept as it is. "
        "Each number may be used only once; lines you leave out are simply not listed."
    )


# What the AI Task is asked to give back: a list of lines (Home Assistant checks the answer has this shape,
# so the model can't answer in prose).
STRUCTURE = {
    "memories": {
        "description": "The new memory: one entry per line, each starting with its source numbers in brackets, like [1, 2] Alex likes coffee, black.",
        "required": True,
        "selector": {"text": {"multiple": True}},
    }
}

_LINE = re.compile(r"^\s*[\[(]\s*(\d+(?:\s*[,;&]\s*\d+|\s+and\s+\d+)*)\s*[\])]\s*[:.-]?\s*(.+?)\s*$", re.DOTALL)


def _entry(item: Any) -> Any:
    """One line of the answer as {"text", "from"}: "[1, 2] the line" (what's asked for), or already an
    object. Anything else is passed on for plan() to refuse.
    """
    if isinstance(item, str):
        match = _LINE.match(item)
        if not match:
            raise TidyError(f"\"{item[:80]}\" didn't start with the numbers of the lines it came from")
        numbers = [int(n) for n in re.findall(r"\d+", match.group(1))]
        return {"text": match.group(2), "from": numbers}
    return item


def _parse(answer: Any) -> list[dict[str, Any]]:
    """The model's answer as a list of {"text", "from"}: the structured answer Home Assistant checked, or,
    from a model that answered in text anyway, JSON or bracketed lines found in it (a code block, a
    thinking section or words around them are allowed).
    """
    if isinstance(answer, str):
        text = re.sub(r"<think>.*?</think>", "", answer, flags=re.DOTALL)
        match = re.search(r"\{.*\}", text, re.DOTALL)
        parsed = None
        if match:
            try:
                parsed = json.loads(match.group(0))
            except ValueError:
                parsed = None
        if parsed is None:
            lines = [line.strip(" -*•\t") for line in text.splitlines() if _LINE.match(line.strip(" -*•\t"))]
            if not lines:
                raise TidyError(f"The answer wasn't a list of memory lines: \"{text.strip()[:120]}\"")
            parsed = {"memories": lines}
        answer = parsed
    memories = answer.get("memories") if isinstance(answer, dict) else answer if isinstance(answer, list) else None
    if not isinstance(memories, list):
        raise TidyError("The answer had no list of memories")
    return [_entry(m) for m in memories]


def plan(memory: list[dict[str, Any]], answer: Any, now: datetime) -> Plan:
    """Works out the new memory list from the model's answer, or raises TidyError if anything about it
    is off (an unknown or reused number, an empty or overlong line, too much retired at once).
    """
    open_items = [m for m in memory if m.get("status") != "completed"]
    entries = _parse(answer)
    used: set[int] = set()
    result = Plan(memory=[])
    replacement: dict[str, dict[str, Any] | None] = {}  # uid -> its new item (None: merged into another)

    for entry in entries:
        if not isinstance(entry, dict):
            raise TidyError("A memory in the answer wasn't an object")
        text = str(entry.get("text") or "").strip()
        sources = entry.get("from")
        if not text or len(text) > MAX_TEXT:
            raise TidyError("A memory in the answer was empty or too long")
        if not isinstance(sources, list) or not sources:
            raise TidyError(f"\"{text}\" didn't say which lines it came from")
        numbers = []
        for n in sources:
            if not isinstance(n, int) or isinstance(n, bool) or not 1 <= n <= len(open_items) or n in used:
                raise TidyError(f"\"{text}\" came from a line that doesn't exist or was already used")
            used.add(n)
            numbers.append(n)
        originals = [open_items[n - 1] for n in sorted(numbers)]
        first = originals[0]
        if len(originals) == 1:
            if text == first["summary"]:
                replacement[first["uid"]] = first
            else:
                replacement[first["uid"]] = {**first, "summary": text}
                result.clarified.append((first["summary"], text))
        else:
            replacement[first["uid"]] = {
                "uid": uuid.uuid4().hex, "summary": text, "status": "needs_action", "added": now.isoformat(),
            }
            for other in originals[1:]:
                replacement[other["uid"]] = None
            result.combined.append(([m["summary"] for m in originals], text))

    retired = [m for i, m in enumerate(open_items, 1) if i not in used]
    if not entries or len(retired) > len(open_items) * MAX_RETIRED_SHARE:
        raise TidyError(f"It would retire {len(retired)} of {len(open_items)} memories at once")
    result.retired = [m["summary"] for m in retired]

    for m in memory:
        if m.get("status") == "completed":
            result.memory.append(m)
        elif m["uid"] in replacement:
            if (new := replacement[m["uid"]]) is not None:
                result.memory.append(new)
        else:
            result.memory.append({**m, "status": "completed"})
    return result
