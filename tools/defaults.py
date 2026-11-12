#!/usr/bin/env python3
"""Lena's default settings, kept in adjustments/defaults.env.

The file has one block per setting: a few comment lines, then the setting's line.

    # Talking to Lena › Wake word › Her name
    # Possible values: Hey Lena | Lena | Hey Jarvis
    # Current default: Hey Lena
    WAKE_PHRASE=

The first comment is where the setting is in Lena's Settings. To ask for a new default, type one of the
possible values after the = sign (no quotes needed) and commit. An empty value means "no change".

The app reads its defaults from android/.../Defaults.kt, which this script writes from the file, so the
two never disagree. Before every build:

    python3 tools/defaults.py waiting   lists the changes asked for (exit 1 if there are any)
    python3 tools/defaults.py apply     applies them
    python3 tools/defaults.py           checks the file and Defaults.kt match (what tools/check.py runs)

apply checks every value asked for, writes the new defaults into Defaults.kt, moves each one to
"Current default" and empties it again (the whole file is rewritten from SETTINGS, so the comments and
possible values always match the app). tools/check.py fails while a change is waiting or Defaults.kt
doesn't match the file, and the release workflow won't publish.

A default is only where a setting starts: changing one doesn't change what someone has already picked
on their phone (Return to defaults does).

Standard library only.
"""

from __future__ import annotations

import re
import sys
from dataclasses import dataclass
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ENV_PATH = ROOT / "adjustments/defaults.env"
KOTLIN_PATH = ROOT / "android/app/src/main/kotlin/io/github/soullink9/lena/Defaults.kt"

ON_OFF = {"On": "true", "Off": "false"}

# The comment line that holds a setting's current default, and a setting's own line.
CURRENT_LINE = re.compile(r"^#\s*Current default:\s*(.*)$")
SETTING_LINE = re.compile(r"^([A-Z][A-Z0-9_]*)\s*=(.*)$")

HEADER = """\
# Lena's default settings
#
# Where each setting starts on a new install, and what Settings → Software version → Return to defaults goes back to.
#
# To change one: type one of its possible values after its = sign, exactly as listed (no quotes needed),
# and commit. Leave every other setting empty. Before the next build the change is applied: it becomes
# the "Current default" and the value after = is emptied again. See README.md in this folder.
"""


@dataclass
class Setting:
    """One default: its name in Settings, its name in Defaults.kt (also its name in defaults.env), its
    Kotlin type, and its possible values (as shown in the app) with the Kotlin each one becomes. A number
    setting has no list, only a range. reset=False marks one that Return to defaults leaves as it is (see
    Prefs.defaultsKeeping): its default is only where a new install starts."""

    name: str
    const: str
    kotlin_type: str
    values: dict[str, str] | None = None
    number_range: tuple[int, int] | None = None
    reset: bool = True

    def possible(self) -> str:
        """The Possible values comment."""
        if self.number_range:
            low, high = self.number_range
            return f"a whole number from {low} to {high}"
        return " | ".join(self.values)

    def kotlin(self, label: str) -> str:
        """The Kotlin for one of its values."""
        if self.number_range:
            return f"{int(label):_}"
        return self.values[label]

    def canonical(self, label: str) -> str | None:
        """The value as the app spells it, ignoring capitals, spaces and a stray plural s, or None if this
        setting can't have it."""
        label = label.strip()
        if self.number_range:
            low, high = self.number_range
            digits = label.replace(",", "").replace("_", "")
            if digits.isdigit() and low <= int(digits) <= high:
                return str(int(digits))
            return None
        for known in self.values:
            if known.lower() == label.lower():
                return known
        # A slip like "1 minutes" for "1 minute" still means one value, so it's read as that one.
        for known in self.values:
            if loosely(known) == loosely(label):
                return known
        return None


def loosely(label: str) -> str:
    """A value with capitals, extra spaces and each word's final s ignored ("1 Minutes" reads as "1 minute")."""
    return " ".join(word.removesuffix("s") for word in label.lower().split())


def percents(*options: int) -> dict[str, str]:
    """Volumes as the app words them ("40%"), and their numbers."""
    return {f"{value}%": str(value) for value in options}


def seconds(*options: tuple[str, int]) -> dict[str, str]:
    """Durations as the app words them, and their seconds."""
    return {label: str(value) for label, value in options}


# Every setting with a default, in the order Settings shows them. The labels are the app's own, so the
# file reads like the screen. Keep in step with Prefs.kt (which uses Defaults.*) and the Settings pages.
SETTINGS = [
    Setting("Connections › Who answers › Lena answers through", "ANSWERS", "Answerer", {
        "Open WebUI": "Answerer.OPEN_WEBUI", "Home Assistant Assist": "Answerer.ASSIST"}, reset=False),
    Setting("Connections › Model › Her context size", "CONTEXT_TOKENS", "Int", number_range=(2048, 1_048_576),
            reset=False),
    Setting("Wall display › Screensaver › Screensaver after", "DIM_SECONDS", "Int", seconds(
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
    # Camera mode (CameraActivity): off unless switched on, per device.
    Setting("Wall display › Cameras › Camera mode", "CAMERA_MODE", "Boolean", ON_OFF),
    Setting("Wall display › Cameras › Show at first", "CAMERA_VIEW", "CameraView", {
        "Show all": "CameraView.SHOW_ALL", "Selected only": "CameraView.SELECTED", "Spotlight select": "CameraView.SPOTLIGHT",
        "Spotlight on motion": "CameraView.MOTION", "Rotate": "CameraView.ROTATE"}),
    Setting("Wall display › Cameras › What counts as motion", "CAMERA_MOTION", "MotionKind", {
        "People, vehicles and the doorbell": "MotionKind.PEOPLE_VEHICLES_DOORBELL", "Any motion": "MotionKind.ANY",
        "The doorbell only": "MotionKind.DOORBELL"}),
    Setting("Wall display › Cameras › Take turns every", "CAMERA_ROTATE_SECONDS", "Int", seconds(
        ("5 seconds", 5), ("10 seconds", 10), ("20 seconds", 20), ("30 seconds", 30))),
    Setting("Wall display › Cameras › Stay on motion for", "CAMERA_HOLD_SECONDS", "Int", seconds(
        ("15 seconds", 15), ("30 seconds", 30), ("1 minute", 60))),
    Setting("Wall display › Cameras › Sound", "CAMERA_SOUND", "Boolean", ON_OFF),
    Setting("Talking to Lena › Wake word › Listen for her name", "WAKE_WORD", "Boolean", ON_OFF, reset=False),
    Setting("Talking to Lena › Wake word › Listens", "WAKE_EVERYWHERE", "Boolean", {
        "Only on the wall display": "false", "Whenever Lena is open": "true"}),
    Setting("Talking to Lena › Wake word › Her name", "WAKE_PHRASE", "WakePhrase", {
        "Hey Lena": "WakePhrase.HEY_LENA", "Lena": "WakePhrase.LENA", "Hey Jarvis": "WakePhrase.JARVIS",
        "Okay Nabu": "WakePhrase.OKAY_NABU", "Hey Mycroft": "WakePhrase.HEY_MYCROFT", "Alexa": "WakePhrase.ALEXA",
        "Hey Home Assistant": "WakePhrase.HEY_HOME_ASSISTANT"}),
    Setting("Talking to Lena › Wake word › How sure she must be", "WAKE_SENSITIVITY", "WakeSensitivity", {
        "Fewer false starts": "WakeSensitivity.STRICT", "Balanced": "WakeSensitivity.BALANCED",
        "Easier to wake": "WakeSensitivity.RELAXED"}),
    Setting("Talking to Lena › How she looks and sounds › Her look", "ASK_STYLE", "AskStyle", {
        "Orb": "AskStyle.ORB", "Sheet": "AskStyle.SHEET", "Glow": "AskStyle.GLOW", "Bar": "AskStyle.BAR",
        "Island": "AskStyle.ISLAND", "Subtitles": "AskStyle.SUBTITLES"}),
    Setting("Talking to Lena › How she looks and sounds › Hearing you", "HEARING", "Hearing", {
        "On this phone": "Hearing.PHONE", "Your server": "Hearing.SERVER", "Home Assistant": "Hearing.HOME_ASSISTANT"}),
    Setting("Talking to Lena › How she looks and sounds › Her voice", "SPEAKING", "Speaking", {
        "Your server's voice": "Speaking.SERVER", "The phone's voice": "Speaking.PHONE",
        "Don't read replies aloud": "Speaking.OFF"}),
    Setting("Talking to Lena › How she looks and sounds › Her voice follows her mood", "MOOD_VOICE", "Boolean", ON_OFF),
    Setting("Talking to Lena › How she looks and sounds › Adjust her volume", "AUTO_VOLUME", "Boolean", ON_OFF),
    # Her volume's limits ([Loudness]): what she keeps her own volume changes within. Each kind of device
    # has its own, since a TV box's volume is the volume of everything on the TV (Prefs.quietestVolume).
    Setting("Talking to Lena › How she looks and sounds › Her quietest (phones and tablets)", "QUIETEST_VOLUME", "Int",
            percents(0, 10, 20, 30, 40, 50, 60)),
    Setting("Talking to Lena › How she looks and sounds › Her loudest (phones and tablets)", "LOUDEST_VOLUME", "Int",
            percents(50, 60, 70, 80, 90, 100)),
    Setting("Talking to Lena › How she looks and sounds › Her quietest (TVs)", "TV_QUIETEST_VOLUME", "Int",
            percents(0, 10, 20, 30, 40, 50, 60)),
    Setting("Talking to Lena › How she looks and sounds › Her loudest (TVs)", "TV_LOUDEST_VOLUME", "Int",
            percents(50, 60, 70, 80, 90, 100)),
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
    Setting("Device customization › Notifications › Alerts on this device", "ALERTS", "Boolean", ON_OFF),
    Setting("Device customization › Widgets and tile › Her mood on the widget", "WIDGET_MOOD", "Boolean", ON_OFF),
]

BY_CONST = {s.const: s for s in SETTINGS}


@dataclass
class Entry:
    """One setting as found in defaults.env: the value asked for after = (empty for none) and the
    Current default comment above it."""

    asked: str
    current: str


def unquote(value: str) -> str:
    """A value with spaces around it and one pair of matching quotes removed (env files often quote)."""
    value = value.strip()
    if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
        return value[1:-1].strip()
    return value


def read_env() -> tuple[dict[str, Entry], list[str]]:
    """The file's settings by name, and lines it couldn't make sense of. A Current default comment
    belongs to the next setting line below it."""
    entries: dict[str, Entry] = {}
    odd: list[str] = []
    if not ENV_PATH.exists():
        # Say so plainly (a missing file would otherwise just be every setting missing).
        return entries, ["adjustments/defaults.env is missing (is it committed? .gitignore must keep it)"]
    current = ""
    for number, raw in enumerate(ENV_PATH.read_text(encoding="utf-8").splitlines(), start=1):
        line = raw.strip()
        if not line:
            continue
        if line.startswith("#"):
            found = CURRENT_LINE.match(line)
            if found:
                current = found.group(1).strip()
            continue
        found = SETTING_LINE.match(line)
        if found is None:
            odd.append(f"Line {number} isn't a setting (NAME=value) or a comment (# ...): \"{line}\"")
            continue
        name = found.group(1)
        if name in entries:
            odd.append(f"Line {number}: {name} is in the file twice")
        entries[name] = Entry(asked=unquote(found.group(2)), current=current)
        current = ""
    return entries, odd


def write_env(current: dict[str, str]) -> None:
    """Writes the whole file from SETTINGS: every setting with its comments and an empty value."""
    blocks = [HEADER]
    for setting in SETTINGS:
        kept = "" if setting.reset else "# Only for new installs: Return to defaults leaves it as it is.\n"
        blocks.append(
            f"# {setting.name}\n"
            f"{kept}"
            f"# Possible values: {setting.possible()}\n"
            f"# Current default: {current[setting.const]}\n"
            f"{setting.const}=\n"
        )
    ENV_PATH.write_text("\n".join(blocks), encoding="utf-8")


def kotlin(current: dict[str, str]) -> str:
    """Defaults.kt for these current defaults."""
    lines = [
        "// Written by tools/defaults.py from adjustments/defaults.env. Don't edit it by hand: type the new",
        "// value after the setting's = sign there and run `python3 tools/defaults.py apply`.",
        "",
        "package io.github.soullink9.lena",
        "",
        "/**",
        " * Where every setting starts on a new install, and what Return to defaults goes back to. Prefs.kt",
        " * reads these; adjustments/defaults.env is where they're changed.",
        " */",
        "object Defaults {",
    ]
    for setting in SETTINGS:
        label = current[setting.const]
        keyword = "const val" if setting.kotlin_type in ("Int", "Boolean") else "val"
        lines.append(f"    /** {setting.name}: {label} */")
        lines.append(f"    {keyword} {setting.const}: {setting.kotlin_type} = {setting.kotlin(label)}")
    lines.append("}")
    return "\n".join(lines) + "\n"


def problems(entries: dict[str, Entry], odd: list[str]) -> list[str]:
    """What's wrong with the file: lines it can't read, unknown or missing settings, and values a setting
    can't have."""
    found = list(odd)
    if not ENV_PATH.exists():
        return found  # one line says it; listing every setting as missing too would bury it
    for name in entries:
        if name not in BY_CONST:
            found.append(f"Unknown setting: {name}")
    for setting in SETTINGS:
        entry = entries.get(setting.const)
        if entry is None:
            found.append(f"Missing setting: {setting.const} ({setting.name})")
            continue
        if not entry.current:
            found.append(f"{setting.const} has no \"# Current default:\" line above it")
        elif setting.canonical(entry.current) is None:
            found.append(f"{setting.const}: Current default \"{entry.current}\" isn't one of: {setting.possible()}")
        if entry.asked and setting.canonical(entry.asked) is None:
            found.append(f"{setting.const}: \"{entry.asked}\" isn't one of: {setting.possible()}")
    return found


def current_defaults(entries: dict[str, Entry]) -> dict[str, str]:
    """Every setting's current default, spelled as the app spells it."""
    return {s.const: s.canonical(entries[s.const].current) for s in SETTINGS}


def asked_for(entries: dict[str, Entry]) -> list[tuple[Setting, str, str]]:
    """The changes asked for: (setting, current default, new value), skipping a value that's already the
    default."""
    changes = []
    for setting in SETTINGS:
        entry = entries[setting.const]
        if not entry.asked:
            continue
        old = setting.canonical(entry.current)
        new = setting.canonical(entry.asked)
        if new is not None and new != old:
            changes.append((setting, old, new))
    return changes


def apply() -> int:
    """Makes every value asked for the current default, writes Defaults.kt, and empties the values."""
    entries, odd = read_env()
    wrong = problems(entries, odd)
    if wrong:
        print("Nothing changed. Fix these in adjustments/defaults.env first:")
        for line in wrong:
            print(f"  - {line}")
        return 1
    current = current_defaults(entries)
    changes = asked_for(entries)
    for setting, _old, new in changes:
        current[setting.const] = new
    write_env(current)
    KOTLIN_PATH.write_text(kotlin(current), encoding="utf-8")
    if changes:
        print("New defaults:")
        for setting, old, new in changes:
            print(f"  - {setting.name}: {old} → {new}")
    else:
        print("No changes asked for. Defaults.kt matches defaults.env.")
    return 0


def check() -> list[str]:
    """For tools/check.py: what stops a build. Problems in the file, changes not applied yet, or a
    Defaults.kt that doesn't match it."""
    entries, odd = read_env()
    found = problems(entries, odd)
    if found:
        return found
    for setting in SETTINGS:
        if entries[setting.const].asked:
            found.append(f"{setting.const} asks for \"{entries[setting.const].asked}\": run python3 tools/defaults.py apply")
    if KOTLIN_PATH.read_text(encoding="utf-8") != kotlin(current_defaults(entries)):
        found.append("Defaults.kt doesn't match adjustments/defaults.env: run python3 tools/defaults.py apply")
    return found


def waiting() -> list[str]:
    """The changes asked for and not applied yet, as "setting: current → new", for the build's summary.
    A file with problems lists those instead, so nothing is silently skipped."""
    entries, odd = read_env()
    wrong = problems(entries, odd)
    if wrong:
        return wrong
    return [f"{setting.name}: {old} → {new}" for setting, old, new in asked_for(entries)]


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
