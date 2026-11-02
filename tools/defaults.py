#!/usr/bin/env python3
"""Lena's default settings, kept in adjustments/defaults.csv.

The CSV has one row per setting, with four columns:

    Setting          where it is in Lena's Settings ("Talking to Lena › Wake word › Her name")
    Change to        empty, or the new default you want (one of the possible values)
    Possible values  what it can be, separated by semicolons
    Current default  what a new install (and Return to defaults) uses today

The app reads its defaults from android/.../Defaults.kt, which this script writes from the CSV, so the
two never disagree. Before every build:

    python3 tools/defaults.py waiting   lists the changes asked for (exit 1 if there are any)
    python3 tools/defaults.py apply     applies them
    python3 tools/defaults.py           checks the CSV and Defaults.kt match (what tools/check.py runs)

checks every "Change to", writes the new defaults into Defaults.kt, moves each value to "Current
default" and empties "Change to". tools/check.py fails while any "Change to" is filled in or
Defaults.kt doesn't match the CSV, and the release workflow won't publish.

A default is only where a setting starts: changing one doesn't change what someone has already picked
on their phone (Return to defaults does).

Standard library only.
"""

from __future__ import annotations

import csv
import io
import sys
from dataclasses import dataclass
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CSV_PATH = ROOT / "adjustments/defaults.csv"
KOTLIN_PATH = ROOT / "android/app/src/main/kotlin/io/github/soullink9/lena/Defaults.kt"
HEADER = ["Setting", "Change to", "Possible values", "Current default"]

ON_OFF = {"On": "true", "Off": "false"}


@dataclass
class Setting:
    """One default: its name in Settings, its name in Defaults.kt, its Kotlin type, and its possible
    values (as shown in the app) with the Kotlin each one becomes. A number setting has no list, only
    a range."""

    name: str
    const: str
    kotlin_type: str
    values: dict[str, str] | None = None
    number_range: tuple[int, int] | None = None

    def possible(self) -> str:
        """The Possible values column."""
        if self.number_range:
            low, high = self.number_range
            return f"A whole number from {low} to {high}"
        return "; ".join(self.values)

    def kotlin(self, label: str) -> str:
        """The Kotlin for one of its values."""
        if self.number_range:
            return f"{int(label):_}"
        return self.values[label]

    def accepts(self, label: str) -> bool:
        """Whether a value is one this setting can have."""
        if self.number_range:
            low, high = self.number_range
            return label.isdigit() and low <= int(label) <= high
        return label in self.values


def seconds(*options: tuple[str, int]) -> dict[str, str]:
    """Durations as the app words them, and their seconds."""
    return {label: str(value) for label, value in options}


# Every setting with a default, in the order Settings shows them. The labels are the app's own, so the
# CSV reads like the screen. Keep in step with Prefs.kt (which uses Defaults.*) and the Settings pages.
SETTINGS = [
    Setting("Connections › Model › Her context size", "CONTEXT_TOKENS", "Int", number_range=(2048, 1_048_576)),
    Setting("Wall display › Screensaver › Screen saver after", "DIM_SECONDS", "Int", seconds(
        ("30 seconds", 30), ("1 minute", 60), ("2 minutes", 120), ("5 minutes", 300), ("10 minutes", 600),
        ("15 minutes", 900), ("30 minutes", 1800), ("1 hour", 3600), ("Never", 0))),
    Setting("Wall display › Screensaver › Back from your chats after", "RETURN_SECONDS", "Int", seconds(
        ("30 seconds", 30), ("1 minute", 60), ("2 minutes", 120), ("5 minutes", 300), ("10 minutes", 600), ("Never", 0))),
    Setting("Wall display › Screensaver › Each photo for", "SLIDE_SECONDS", "Int", seconds(
        ("5 seconds", 5), ("10 seconds", 10), ("15 seconds", 15), ("30 seconds", 30), ("1 minute", 60), ("5 minutes", 300))),
    Setting("Wall display › Screensaver › Transition", "SLIDE_TRANSITION", "SlideTransition", {
        "Fade": "SlideTransition.FADE", "Slide": "SlideTransition.SLIDE",
        "Slow zoom and fade": "SlideTransition.ZOOM", "None": "SlideTransition.NONE"}),
    Setting("Wall display › Screensaver › Shuffle", "SLIDE_SHUFFLE", "Boolean", ON_OFF),
    Setting("Wall display › Screensaver › Fit", "SLIDE_FRAMING", "Framing", {
        "Smart": "Framing.SMART", "Fill the screen": "Framing.FILL", "Whole photo": "Framing.WHOLE"}),
    Setting("Wall display › Screensaver › Time and her mood on top", "SLIDE_OVERLAY", "Boolean", ON_OFF),
    Setting("Wall display › Screensaver › Her dim clock at night", "SLIDE_CLOCK_AT_NIGHT", "Boolean", ON_OFF),
    Setting("Talking to Lena › Wake word › Listen for her name", "WAKE_WORD", "Boolean", ON_OFF),
    Setting("Talking to Lena › Wake word › Listens", "WAKE_EVERYWHERE", "Boolean", {
        "Only on the wall display": "false", "Whenever Lena is open": "true"}),
    Setting("Talking to Lena › Wake word › Her name", "WAKE_PHRASE", "WakePhrase", {
        "Hey Lena": "WakePhrase.HEY_LENA", "Lena": "WakePhrase.LENA", "Hey Jarvis": "WakePhrase.JARVIS"}),
    Setting("Talking to Lena › Wake word › How sure she must be", "WAKE_SENSITIVITY", "WakeSensitivity", {
        "Fewer false starts": "WakeSensitivity.STRICT", "Balanced": "WakeSensitivity.BALANCED",
        "Easier to wake": "WakeSensitivity.RELAXED"}),
    Setting("Talking to Lena › How she looks and sounds › Her look", "ASK_STYLE", "AskStyle", {
        "Orb": "AskStyle.ORB", "Sheet": "AskStyle.SHEET", "Glow": "AskStyle.GLOW", "Bar": "AskStyle.BAR",
        "Island": "AskStyle.ISLAND", "Subtitles": "AskStyle.SUBTITLES"}),
    Setting("Talking to Lena › How she looks and sounds › Hearing you", "HEARING", "Hearing", {
        "On this phone": "Hearing.PHONE", "Your server": "Hearing.SERVER"}),
    Setting("Talking to Lena › How she looks and sounds › Her voice", "SPEAKING", "Speaking", {
        "Your server's voice": "Speaking.SERVER", "The phone's voice": "Speaking.PHONE",
        "Don't read replies aloud": "Speaking.OFF"}),
    Setting("Talking to Lena › How she looks and sounds › Her voice follows her mood", "MOOD_VOICE", "Boolean", ON_OFF),
    Setting("Talking to Lena › How she looks and sounds › Keep listening", "FOLLOW_UP", "FollowUp", {
        "After every reply": "FollowUp.ALWAYS", "When she asks a question": "FollowUp.QUESTIONS", "Off": "FollowUp.OFF"}),
    Setting("Talking to Lena › Interacting with Lena › The assistant gesture opens", "ASSIST", "AssistAction", {
        "Talk to Lena": "AssistAction.ASK", "Type to Lena": "AssistAction.TYPE", "New chat": "AssistAction.CHAT"}),
    Setting("Talking to Lena › Interacting with Lena › On the lock screen", "LOCK_SCREEN", "Boolean", ON_OFF),
    Setting("Chat screen configuration › Chat screen › Lena's look", "LENA_LOOK", "Boolean", ON_OFF),
    Setting("Chat screen configuration › Chat screen › Open WebUI's own menus", "OPEN_WEBUI_MENUS", "Boolean", ON_OFF),
    Setting("Chat screen configuration › Chat screen › Chat | Home switch", "HOME_SWITCH", "Boolean", ON_OFF),
    Setting("Chat screen configuration › Chat screen › Pinned chats", "PINNED_SHOWN", "Int", {
        "3": "3", "5": "5", "8": "8", "12": "12"}),
    Setting("Chat screen configuration › Chat screen › Folders", "DRAWER_FOLDERS", "Boolean", ON_OFF),
    Setting("Chat screen configuration › Chat screen › All chats list style", "CHAT_LIST", "ChatListStyle", {
        "Slim groups": "ChatListStyle.SLIM", "Rounded groups": "ChatListStyle.GROUPS", "Flat list": "ChatListStyle.FLAT",
        "Title and time": "ChatListStyle.TIMED", "Timeline": "ChatListStyle.TIMELINE", "Folding days": "ChatListStyle.FOLDING"}),
    Setting("Device customization › Notifications › Reply notifications", "NOTIFY_REPLIES", "Boolean", ON_OFF),
    Setting("Device customization › Notifications › Alerts on this phone", "ALERTS", "Boolean", ON_OFF),
    Setting("Device customization › Widgets & tile › Her mood on the widget", "WIDGET_MOOD", "Boolean", ON_OFF),
]

BY_NAME = {s.name: s for s in SETTINGS}


def read_csv() -> list[dict[str, str]]:
    """The CSV's rows, as dictionaries keyed by the header."""
    with CSV_PATH.open(newline="", encoding="utf-8") as f:
        return [{k: (v or "").strip() for k, v in row.items()} for row in csv.DictReader(f)]


def write_csv(rows: list[dict[str, str]]) -> None:
    """Writes the rows back, every setting in SETTINGS order with its possible values filled in."""
    out = io.StringIO()
    writer = csv.DictWriter(out, fieldnames=HEADER, lineterminator="\n")
    writer.writeheader()
    for row in rows:
        writer.writerow({key: row.get(key, "") for key in HEADER})
    CSV_PATH.write_text(out.getvalue(), encoding="utf-8")


def kotlin(rows: list[dict[str, str]]) -> str:
    """Defaults.kt for these current defaults."""
    current = {row["Setting"]: row["Current default"] for row in rows}
    lines = [
        "// Written by tools/defaults.py from adjustments/defaults.csv. Don't edit it by hand: change the CSV's",
        "// \"Change to\" column and run `python3 tools/defaults.py apply`.",
        "",
        "package io.github.soullink9.lena",
        "",
        "/**",
        " * Where every setting starts on a new install, and what Return to defaults goes back to. Prefs.kt",
        " * reads these; adjustments/defaults.csv is where they're changed.",
        " */",
        "object Defaults {",
    ]
    for setting in SETTINGS:
        value = setting.kotlin(current[setting.name])
        keyword = "const val" if setting.kotlin_type in ("Int", "Boolean") else "val"
        lines.append(f"    /** {setting.name}: {current[setting.name]} */")
        lines.append(f"    {keyword} {setting.const}: {setting.kotlin_type} = {value}")
    lines.append("}")
    return "\n".join(lines) + "\n"


def problems(rows: list[dict[str, str]]) -> list[str]:
    """What's wrong with the CSV: unknown or missing settings, and values a setting can't have."""
    found = []
    names = [row.get("Setting", "") for row in rows]
    for name in names:
        if name not in BY_NAME:
            found.append(f"Unknown setting: \"{name}\"")
    for setting in SETTINGS:
        if setting.name not in names:
            found.append(f"Missing setting: \"{setting.name}\"")
    for row in rows:
        setting = BY_NAME.get(row.get("Setting", ""))
        if setting is None:
            continue
        for column in ("Change to", "Current default"):
            value = row.get(column, "")
            if value and not setting.accepts(value):
                found.append(f"\"{setting.name}\": {column} \"{value}\" isn't one of: {setting.possible()}")
        if not row.get("Current default"):
            found.append(f"\"{setting.name}\" has no Current default")
    return found


def apply() -> int:
    """Makes every "Change to" the current default, writes Defaults.kt, and empties "Change to"."""
    rows = read_csv()
    wrong = problems(rows)
    if wrong:
        print("Nothing changed. Fix these in adjustments/defaults.csv first:")
        for line in wrong:
            print(f"  - {line}")
        return 1
    by_name = {row["Setting"]: row for row in rows}
    changed = []
    ordered = []
    for setting in SETTINGS:
        row = by_name[setting.name]
        if row["Change to"] and row["Change to"] != row["Current default"]:
            changed.append(f"{setting.name}: {row['Current default']} → {row['Change to']}")
            row["Current default"] = row["Change to"]
        row["Change to"] = ""
        row["Possible values"] = setting.possible()
        ordered.append(row)
    write_csv(ordered)
    KOTLIN_PATH.write_text(kotlin(ordered), encoding="utf-8")
    if changed:
        print("New defaults:")
        for line in changed:
            print(f"  - {line}")
    else:
        print("No changes asked for. Defaults.kt matches the CSV.")
    return 0


def check() -> list[str]:
    """For tools/check.py: what stops a build. Problems in the CSV, changes not applied yet, or a
    Defaults.kt that doesn't match the CSV."""
    rows = read_csv()
    found = problems(rows)
    if found:
        return found
    for row in rows:
        if row["Change to"]:
            found.append(f"\"{row['Setting']}\" asks for {row['Change to']}: run python3 tools/defaults.py apply")
    if KOTLIN_PATH.read_text(encoding="utf-8") != kotlin(rows):
        found.append("Defaults.kt doesn't match adjustments/defaults.csv: run python3 tools/defaults.py apply")
    return found


def waiting() -> list[str]:
    """The changes asked for and not applied yet, as "setting: current → new", for the build's summary."""
    return [
        f"{row['Setting']}: {row['Current default']} → {row['Change to']}"
        for row in read_csv()
        if row["Change to"]
    ]


if __name__ == "__main__":
    command = sys.argv[1] if len(sys.argv) > 1 else "check"
    if command == "apply":
        sys.exit(apply())
    if command == "waiting":
        # Prints each waiting change on its own line; exits 1 if there are any.
        changes = waiting()
        for line in changes:
            print(f"- {line}")
        sys.exit(1 if changes else 0)
    issues = check()
    for line in issues:
        print(line)
    sys.exit(1 if issues else 0)
