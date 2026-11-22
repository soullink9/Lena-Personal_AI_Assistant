#!/usr/bin/env python3
"""Sanity checks for the Lena repository. Run from the repo root:

    pip install pyyaml
    python3 tools/check.py

It does NOT need Home Assistant, Ollama, Open WebUI or the Android SDK (the Home Assistant
integration's full tests run against a real Home Assistant on the Gitea runner and on GitHub; see
.gitea/workflows/test.yml and .github/workflows/home-assistant.yml). It catches the mistakes that
are easy to make when editing:

  - broken YAML or JSON;
  - feelings.yaml missing a feeling her mood engine uses, or a description that isn't text;
  - each of her feelings events choosing something that isn't defined;
  - the Open WebUI tool offering moments the integration doesn't have;
  - the Modelfile losing her character or its context size drifting from the app's;
  - Python that doesn't compile, and private data slipping into the repository;
  - the integration's brand images missing or the wrong size;
  - the parts' versions disagreeing, or reaching 1.0 before that's approved;
  - a library or Python package that hasn't been approved;
  - the release scripts in scripts/ going wrong (tests/release/test_release.py).

Exit code 0 means every check passed.
"""
import importlib.util
import json
import py_compile
import random
import re
import sys
import types
from datetime import datetime, time, timedelta
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parent.parent
LENA = ROOT / "custom_components" / "lena"
FEELINGS = LENA / "feelings.yaml"
OWUI_FILTER = ROOT / "open-webui" / "lena_filter.py"
OWUI_TOOLS = ROOT / "open-webui" / "lena_tools.py"
COMPOSE_FILES = [
    ROOT / "ollama" / "docker-compose.example.yml",
    ROOT / "voice" / "piper" / "docker-compose.example.yml",
    ROOT / "voice" / "bridge" / "compose.yaml",
]
# GitHub's workflows and Gitea's (.gitea/workflows, run on the owner's own runner; see CONTRIBUTING.md).
WORKFLOWS = sorted((ROOT / ".github" / "workflows").glob("*.yml")) + sorted((ROOT / ".gitea" / "workflows").glob("*.yml"))
MODELFILE = ROOT / "ollama" / "Modelfile"
APP_PREFS = ROOT / "android" / "app" / "src" / "main" / "kotlin" / "io" / "github" / "soullink9" / "lena" / "Prefs.kt"
BRIDGE = ROOT / "voice" / "bridge" / "bridge.py"

failures = []


def check(ok, message):
    """Record a pass or a failure and print it."""
    print(("PASS  " if ok else "FAIL  ") + message)
    if not ok:
        failures.append(message)


def rel(path):
    """A path relative to the repository, for readable messages."""
    return path.relative_to(ROOT)


# ---- 1. YAML and JSON parse -------------------------------------------------------------------
for path in [FEELINGS, LENA / "services.yaml", *COMPOSE_FILES, *WORKFLOWS]:
    try:
        yaml.safe_load(path.read_text())
        check(True, f"{rel(path)} is valid YAML")
    except yaml.YAMLError as err:
        check(False, f"{rel(path)} is not valid YAML: {err}")

for path in [LENA / "manifest.json", LENA / "strings.json", LENA / "translations" / "en.json"]:
    try:
        json.loads(path.read_text())
        check(True, f"{rel(path)} is valid JSON")
    except json.JSONDecodeError as err:
        check(False, f"{rel(path)} is not valid JSON: {err}")
check((LENA / "strings.json").read_text() == (LENA / "translations" / "en.json").read_text(),
      "the integration's strings.json and translations/en.json match")

# ---- 2. Her feelings -------------------------------------------------------------------------
# brain.py is plain Python, so it can be loaded without Home Assistant.
spec = importlib.util.spec_from_file_location("lena_brain", LENA / "brain.py")
brain = importlib.util.module_from_spec(spec)
sys.modules["lena_brain"] = brain
spec.loader.exec_module(brain)

raw = yaml.safe_load(FEELINGS.read_text()) or {}
catalog = brain.Catalog(
    moods={k: " ".join(str(v).split()) for k, v in (raw.get("moods") or {}).items()},
    energies={k: " ".join(str(v).split()) for k, v in (raw.get("energy") or {}).items()},
    moments={k: " ".join(str(v).split()) for k, v in (raw.get("moments") or {}).items()},
)
check(set(raw) <= {"moods", "energy", "moments"}, "feelings.yaml has only moods, energy and moments")
for section in ("moods", "energy", "moments"):
    entries = raw.get(section) or {}
    check(len(entries) > 0 and all(isinstance(v, str) and len(v.split()) >= 3 for v in entries.values()),
          f"feelings.yaml: every {section} entry has a description")
check(brain.NOTHING not in catalog.moments, f"'{brain.NOTHING}' (no reaction) isn't defined as a moment")

used_moods = set(brain.NEIGHBOURS) | {m for v in brain.NEIGHBOURS.values() for m in v} | set(brain.BASELINE) \
    | set(brain.MORNING_MOODS) | {m for v in brain.ENERGY_PULL.values() for m in v} \
    | {m for v in brain.WEATHER_PULL.values() for m in v} | {"Cheerful", "Fond", "Restless", "Wistful"}
used_energy = set(brain.ENERGY_PULL) | {"Groggy", "Steady", "Wired", "Drained"}
used_moments = {"Delighted", "Worried", "Relieved"}
for kind, used, defined in [("mood", used_moods, catalog.moods), ("energy", used_energy, catalog.energies),
                            ("moment", used_moments, catalog.moments)]:
    missing = sorted(used - set(defined))
    check(not missing, f"every {kind} the mood engine uses is in feelings.yaml" + (f" (missing: {missing})" if missing else ""))

# ---- 3. Every event chooses only defined feelings -----------------------------------------
rng = random.Random(7)
now = datetime(2026, 1, 1, 9, 7)
problems = []


def ok(change, name):
    """Records a problem if an event chose a feeling that feelings.yaml doesn't define, which would
    leave her selects showing an unknown option.
    """
    if change.mood is not None and change.mood not in catalog.moods:
        problems.append(f"{name} chose mood {change.mood}")
    if change.energy is not None and change.energy not in catalog.energies:
        problems.append(f"{name} chose energy {change.energy}")
    if change.moment is not None and change.moment not in catalog.moments and change.moment != brain.NOTHING:
        problems.append(f"{name} chose moment {change.moment}")


for mood in catalog.moods:
    for energy in catalog.energies:
        f = brain.Feelings(mood=mood, energy=energy, mood_since=now - timedelta(hours=5))
        for hour in range(24):
            at = now.replace(hour=hour)
            for weather in (None, "pouring", "sunny", "lightning", "snowy", "cloudy"):
                ok(brain.hourly(f, catalog, at, time(6, 30), time(22, 30), weather, 0, 2, 3, 100, rng), "hourly")
        ok(brain.house_empty(f, catalog, rng), "house_empty")
        ok(brain.weather_turned(f, catalog, "cloudy", "pouring", 100, rng), "weather_turned")
f = brain.Feelings()
ok(brain.morning(f, catalog, [], "pouring", rng), "morning")
special = brain.morning(f, catalog, ["Sam's Birthday"], None, rng)
ok(special, "morning (special day)")
ok(brain.bedtime(catalog), "bedtime")
arrival = brain.arrived(catalog, "Alex", 30, 4)
ok(arrival, "arrived")
ok(brain.worried(catalog, "Leak sensor", "moisture"), "worried")
ok(brain.worry_cleared(brain.Feelings(moment="Worried"), catalog, "Leak sensor", 0), "worry_cleared")
check(not problems, "every feelings event chooses only defined feelings" + (f" ({'; '.join(problems[:5])})" if problems else ""))
check(special.mood_reason == "It's Sam's Birthday today.", "a special day gives her the reason")
check(arrival.moment_reason == "Alex just got home after 30 hours away.", "coming home says who and how long")
check(
    brain.worrying("binary_sensor.leak", "on") and brain.worrying("lock.front_door", "unlocked")
    and brain.worrying("lock.front_door", "jammed") and brain.worrying("cover.garage_door", "open")
    and not brain.worrying("lock.front_door", "locked") and not brain.worrying("lock.front_door", "unavailable")
    and brain.worry_clear("lock.front_door", "locked") and brain.worry_clear("cover.garage_door", "closed")
    and not brain.worry_clear("lock.front_door", "unavailable"),
    "locks and covers can worry her: unlocked, jammed or open, and clear when locked or closed",
)
check(
    brain.worried(catalog, "Front door", "lock", "lock.front_door", "unlocked").moment_reason == "Front door is unlocked."
    and brain.worried(catalog, "Front door", None, "lock.front_door", "jammed").moment_reason == "Front door is jammed."
    and brain.worried(catalog, "Garage door", "garage", "cover.garage_door", "open").moment_reason == "Garage door is open.",
    "a worried lock or cover says what's wrong",
)
check(
    all(d in brain.WORRY_DOMAINS for d in ("binary_sensor", "lock", "cover")),
    "locks and covers can be chosen as worries",
)
check(brain.energy_pool(now.replace(hour=2), time(6, 30), time(22, 30)) == ["Drained"], "she's drained in the night")

text = brain.describe(brain.Feelings(mood="Grumpy", mood_reason="It's raining.", moment="Pleased"), catalog)
others = [m for m in catalog.moods if m != "Grumpy" and f"Mood: {m}." in text]
check("Mood: Grumpy." in text and "Why: It's raining." in text and "Just now: Pleased." in text and not others,
      "her prompt describes only the feelings she has right now")
check("Why:" not in brain.describe(brain.Feelings(mood="Grumpy", mood_reason="x"), catalog, with_reasons=False),
      "reasons can be left out")
items = ["Alex likes the office at seventy degrees.", "Sam is allergic to cats.", "Sam's vet appointment is Friday."]
check(brain.find_memory("office", items) == (items[0], []) and brain.find_memory("allergy", items) == (items[1], [])
      and brain.find_memory("quantum physics", items) == (None, []), "forgetting finds the memory meant, and only that")
check(brain.find_memory("office temperature", items) == (None, [items[0]])
      and brain.find_memory("dentist appointment", items) == (None, [items[2]])
      and brain.find_memory("Sam", items)[0] is None,
      "a memory that's only partly matched, or quoted in several, is offered back instead of removed")

# ---- 3b. Who and where, one display answering, memory, speaking first, routines, afterglow, voice ------
# The newer rules are plain functions in brain.py too, so they're tried here with fixed times.
from datetime import date  # noqa: E402

evening = datetime(2026, 11, 21, 19, 30)
check(brain.when_words(datetime(2026, 11, 20, 18, 0), evening) == "yesterday evening"
      and brain.when_words(datetime(2026, 11, 21, 2, 0), evening) == "last night"
      and brain.when_words(datetime(2026, 11, 17, 9, 0), evening) == "on Tuesday"
      and brain.when_words(datetime(2026, 11, 21, 19, 0), evening) == "less than an hour ago",
      "she says when something happened the way a person would")
talk = brain.record_talk({"last": datetime(2026, 11, 20, 18, 0).isoformat()}, evening)
check(brain.talk_line("Soul", talk, evening) == "You're talking with Soul. Before this, you last talked yesterday evening."
      and brain.talk_line("Soul", brain.record_talk(talk, evening + timedelta(minutes=5)), evening + timedelta(minutes=5))
      == "You're talking with Soul. Before this, you last talked yesterday evening."
      and brain.talk_line("Sam", None, evening) == "You're talking with Sam.",
      "who she's talking with, and when they last talked before this conversation")
late = datetime(2026, 11, 21, 23, 45)
check("bedroom" in brain.room_manner("Bedroom", True, late, time(6, 30), time(22, 30))
      and brain.room_manner("Kitchen", False, evening, time(6, 30), time(22, 30)) == ""
      and brain.quiet_now(True, late, time(6, 30), time(22, 30))
      and not brain.quiet_now(False, late, time(6, 30), time(22, 30)),
      "a bedroom at night makes her quiet; the evening doesn't")
claims = [{"id": "kitchen", "level": 18, "score": 0.9, "order": 0}, {"id": "living", "level": 9, "score": 0.99, "order": 1}]
check(brain.wake_winner(claims) == "kitchen"
      and brain.wake_winner(claims + [{"id": "phone", "level": 2, "score": 0.6, "handheld": True, "order": 2}]) == "phone"
      and brain.wake_winner([{"id": "a", "level": 10, "score": 0.9, "order": 0}, {"id": "b", "level": 10, "score": 0.9, "order": 1}]) == "a"
      and brain.wake_winner([]) is None,
      "the display that heard best answers the wake word (a phone in the hand always does)")
names = ["Soul", "Alex Smith", "Yuki (dog)"]
check(brain.match_name("yuki", names) == "Yuki (dog)" and brain.match_name("Alex", names) == "Alex Smith"
      and brain.match_name("Bob", names) is None and brain.match_name("", names) is None,
      "a memory is only about someone she knows")
today = date(2026, 11, 21)
memory = [
    {"summary": "Yuki has a vet visit on Friday.", "about": "Yuki (dog)", "by": "Soul", "follow_up": "2026-11-20", "status": "needs_action"},
    {"summary": "Alex likes jazz.", "about": "Alex Smith", "status": "needs_action"},
    {"summary": "The wifi password is on the fridge.", "status": "needs_action"},
    {"summary": "Soul takes coffee black.", "about": "Soul", "status": "needs_action"},
    {"summary": "Pineapple on pizza is a crime.", "kind": "hers", "status": "needs_action"},
]
section = brain.remembered_items(memory, ["Soul"], today)
check(section.index("Soul takes coffee") < section.index("wifi") < section.index("Alex likes")
      and "Due to come back to" in section and section.count("vet visit") == 1 and "Pineapple" not in section,
      "her memory puts who's here first, lists a due follow-up once, and keeps her own opinions apart")
check("Due to come back to" not in brain.remembered_items(memory, ["Alex Smith"], today)
      and not brain.follow_up_due({**memory[0], "asked": "yes"}, today)
      and not brain.follow_up_due(memory[0], date(2026, 11, 30)),
      "a follow-up is only for whoever told her, once, and not long after its day")
hers = [{"summary": "a", "kind": "hers", "offered": "2026-11-20", "status": "needs_action"},
        {"summary": "b", "kind": "hers", "offered": "2026-11-10", "status": "needs_action"}]
check(brain.pick_hers(hers, today)["summary"] == "b" and brain.pick_hers(hers[:1], today) is None
      and brain.pick_hers([{**hers[0], "offered": "2026-11-21"}], today)["summary"] == "a",
      "one of her own opinions a day, never one offered in the last few days")
glow = brain.afterglow_of("Delighted", "Alex just got home after 30 hours away.", evening - timedelta(hours=6), evening)
check(glow is not None and brain.afterglow_of("Worried", "x", evening, evening) is None
      and "Still with you from" in brain.afterglow_line([glow], evening)
      and brain.afterglow_line([glow], evening + timedelta(hours=20)) == ""
      and brain.lingering([glow], evening + timedelta(days=2)) == []
      and "Fond" in brain.afterglow_pull([glow], evening),
      "a good moment lingers for a day, fades, and pulls her mood its way meanwhile")
unknown = [m for m in set(sum(brain.AFTERGLOW_PULL.values(), [])) if m not in catalog.moods] \
    + [m for m in brain.AFTERGLOW_HOURS if m not in catalog.moments]
check(not unknown, "every feeling an afterglow uses is in feelings.yaml" + (f" (missing: {unknown})" if unknown else ""))
f_lingering = brain.Feelings(mood="Wistful", mood_since=now - timedelta(hours=5))
ok(brain.hourly(f_lingering, catalog, now, time(6, 30), time(22, 30), None, 1, 2, 3, 100, rng, True, ["Fond"]), "hourly (lingering)")
check(not problems, "drifting with an afterglow chooses only defined feelings")
noon = datetime(2026, 11, 21, 12, 0)
check(brain.may_speak("worry", noon, None, 99, 6, True, False, True)
      and not brain.may_speak("welcome", noon, None, 0, 6, True, False, False)
      and not brain.may_speak("welcome", noon, None, 6, 6, False, False, False)
      and not brain.may_speak("worry", noon, noon - timedelta(minutes=10), 0, 6, False, False, False)
      and not brain.may_speak("morning", noon, None, 0, 6, False, True, False)
      and not brain.may_speak("follow_up", noon, None, 0, 6, False, False, True)
      and brain.may_speak("follow_up", noon, None, 0, 6, False, False, False),
      "speaking first: worries always, the rest within the day's budget, never in a quiet room or Do not disturb")
weekdays = {date(2026, 11, 2) + timedelta(days=i): (7 * 60 + 5 + (i % 3)) for i in range(19) if (date(2026, 11, 2) + timedelta(days=i)).weekday() < 5}
weekdays[date(2026, 11, 4)] = None  # one day without coffee doesn't make it irregular
learned = brain.learn_routine(weekdays)
monday = datetime(2026, 11, 23, 8, 0)
check("weekday" in learned and "weekend" not in learned and abs(learned["weekday"]["usual"] - 426) <= 2
      and brain.routine_late(learned, monday, False) and not brain.routine_late(learned, monday, True)
      and not brain.routine_late(learned, monday.replace(hour=7, minute=20), False)
      and not brain.routine_late(learned, monday.replace(hour=13), False)
      and "around 7:0" in brain.routine_line("The coffee machine", learned, monday),
      "routines: a regular habit is learned, and noticed only when it's well past its time")
check(brain.learn_routine({date(2026, 11, 2) + timedelta(days=i): (i * 97) % 1440 for i in range(10)}) == {},
      "an irregular habit isn't a routine")
check(brain.voice_tone(brain.Feelings(mood="Cheerful", energy="Wired")) == {"speed": 1.112, "pitch": 1.061, "volume": 1.0}
      and brain.voice_tone(brain.Feelings(), quiet=True)["volume"] < 1,
      "her voice follows her feelings, and softens in a quiet room")
# The app's VoiceTone (Voice.kt) and the integration's (for the voice bridge) are the same numbers.
voice_kt = (ROOT / "android/app/src/main/kotlin/io/github/soullink9/lena/Voice.kt").read_text()
kt_pairs = {name: (float(a), float(b)) for name, a, b in re.findall(r'"(\w+)" to \(([\d.]+)f to ([\d.]+)f\)', voice_kt)}
check(kt_pairs == {**brain.VOICE_ENERGY, **brain.VOICE_MOOD},
      "her voice's tones are the same in the app (Voice.kt) and the integration (brain.py)")

# Around the house: goodnight, celebrations and holidays, the seasons, curiosity, shopping, upkeep, photos.
from zoneinfo import ZoneInfo  # noqa: E402
check(brain.goodnight_time(datetime(2026, 11, 22, 20, 0), time(6, 30), time(22, 30))
      and brain.goodnight_time(datetime(2026, 11, 22, 2, 0), time(6, 30), time(22, 30))
      and not brain.goodnight_time(datetime(2026, 11, 22, 18, 0), time(6, 30), time(22, 30)),
      "the goodnight check runs from three hours before her bedtime until she wakes")
check("Front door is unlocked; lights on: Kitchen: Ceiling." in brain.goodnight_section(["Front door is unlocked"], {"Kitchen": ["Ceiling"]})
      and "Nothing on the list is open" in brain.goodnight_section([], {}),
      "goodnight says what's still open or on, or that all's well")
check(brain.easter(2026) == date(2026, 4, 5) and brain.easter(2027) == date(2027, 3, 28)
      and brain.holidays(date(2026, 11, 26), "US") == ["Thanksgiving"] and brain.holidays(date(2026, 11, 26), "GB") == []
      and brain.holidays(date(2026, 12, 25)) == ["Christmas Day"],
      "she knows the holidays: Christmas, Easter, Thanksgiving where it's kept")
check(brain.merge_special(["Christmas", "Alex's Birthday"], ["Christmas Day"]) == ["Christmas", "Alex's Birthday"],
      "a holiday on a calendar isn't said twice")
check("Merry Christmas" in brain.celebration_line(["Christmas Day"], True)
      and "first time" not in brain.celebration_line(["Christmas Day"], False) and brain.celebration_line([], True) == "",
      "special days are greeted once per person")
ny = ZoneInfo("America/New_York")
check(brain.season_days(date(2026, 12, 21), ny) == ["the shortest day of the year (the winter solstice)"]
      and brain.season_days(date(2026, 12, 21), ny, southern=True) == ["the longest day of the year (the summer solstice)"]
      and brain.season_days(date(2026, 6, 21), ny) == ["the longest day of the year (the summer solstice)"]
      and brain.season_days(date(2026, 3, 20), ny) == ["the spring equinox"]
      and brain.season_days(date(2026, 9, 22), ny) == ["the autumn equinox"]
      and brain.season_days(date(2026, 10, 31), ny) == ["the clocks go back an hour tonight"]
      and brain.season_days(date(2026, 11, 1), ny) == ["the clocks went back an hour last night"]
      and brain.season_days(date(2027, 3, 14), ny) == ["the clocks went forward an hour last night"]
      and brain.season_days(date(2026, 11, 22), ny) == [],
      "the solstices, equinoxes and the clocks changing fall on the right days")
check(brain.winter_of(date(2026, 11, 1)) == "2026-27" == brain.winter_of(date(2027, 2, 1))
      and brain.freezing(31, "°F") and not brain.freezing(33, "°F") and brain.freezing(-1, "°C"),
      "the first frost is the first of each winter, in either unit")
check(brain.curious_subject("Soul", ["Soul", "Alex", "Yuki (dog)"], [{"about": "Alex"}, {"about": "Soul"}]) == "Yuki (dog)"
      and brain.curious_subject("Soul", ["Soul"], []) == "Soul"
      and brain.curious_due(None, date(2026, 11, 22)) and not brain.curious_due("2026-11-21", date(2026, 11, 22))
      and brain.curious_due("2026-11-19", date(2026, 11, 22)),
      "she's curious about whoever she knows least, at most every few days")
check(brain.shopping_message("Market Basket", ["milk", "eggs", "bread"]) == "You're at Market Basket. On the list: milk, eggs and bread."
      and brain.shopping_message("X", ["milk"]) == "You're at X. On the list: milk.",
      "the shopping list reads naturally")
readings = [
    {"id": "a", "name": "Hallway smoke alarm", "kind": "battery", "level": 10},
    {"id": "b", "name": "Back door sensor", "kind": "battery", "level": None},
    {"id": "c", "name": "Purifier", "kind": "filter", "level": 2},
    {"id": "d", "name": "Kitchen motion", "kind": "battery", "level": 80},
]
low = brain.upkeep_low(readings)
check([r["id"] for r in low] == ["c", "a", "b"]
      and brain.upkeep_line(low).startswith("Running low around the house: Purifier filter 2%, Hallway smoke alarm battery 10%, Back door sensor battery low.")
      and brain.upkeep_due("2026-11-20", date(2026, 11, 22), low, []) == low
      and brain.upkeep_due("2026-11-20", date(2026, 11, 22), low, ["c"]) == []
      and brain.upkeep_due("2026-11-14", date(2026, 11, 22), low, ["c"]) == low
      and brain.upkeep_due(None, date(2026, 11, 22), [], []) == [],
      "batteries and filters: lowest first, weekly, or at once when one's about to run out")
check(brain.photo_line({"taken": "2025-11-22", "place": "Narragansett, Rhode Island", "people": ["Alex", "Yuki"]}, date(2026, 11, 22))
      == "On this display's screen right now: a photo from November 22, 2025, in Narragansett, Rhode Island, with Alex and Yuki. "
         "It was taken on this day a year ago. If someone asks about it, that's what it is."
      and brain.photo_line({"taken": "", "place": "", "people": []}, date(2026, 11, 22)) == "" and brain.photo_line(None, date(2026, 11, 22)) == "",
      "the photo on the wall: when, where, who, and its anniversary")
check(set(brain.SPEAK_OPTIONAL) >= {"celebration", "season", "upkeep"} and brain.DEFAULTS["speak_first"] == []
      and "nickname" in brain.TOOL_KINDS and "nickname" not in brain.KINDS,
      "the new ways of speaking first are off until chosen, and a nickname isn't a memory")

# ---- 4. The Open WebUI tool offers the integration's own moments -------------------------------
const = (LENA / "const.py").read_text()
house_moments = set(re.findall(r'"(\w+)"', re.search(r"HOUSE_MOMENTS = \{(.*?)\}", const).group(1)))
tool_literal = re.search(r"moment: Literal\[(.*?)\]", OWUI_TOOLS.read_text(), re.S)
tool_options = set(re.findall(r'"(\w+)"', tool_literal.group(1))) if tool_literal else set()
wanted = set(catalog.moments) - house_moments
check(tool_options == wanted, "the Open WebUI tool offers the moments she can set herself"
      + (f" (tool: {sorted(tool_options)}, feelings.yaml: {sorted(wanted)})" if tool_options != wanted else ""))

# ---- 5. Docker compose: $ must be escaped as $$ in shell commands ------------------------------
compose = yaml.safe_load(COMPOSE_FILES[0].read_text())
command = "\n".join(str(c) for c in compose["services"]["ollama-watchdog"]["command"])
check(not re.findall(r"(?<!\$)\$(?![$])", command), "watchdog command escapes every dollar sign as $$")

# ---- 6. Modelfile ------------------------------------------------------------------------------
modelfile = MODELFILE.read_text()
check(len(re.findall(r"^FROM ", modelfile, re.M)) == 1, "Modelfile has exactly one active FROM line")
check(re.search(r'^MESSAGE system """', modelfile, re.M) is not None and modelfile.count('"""') == 2,
      "Modelfile carries her character in one MESSAGE system block")
check(re.search(r"^SYSTEM ", modelfile, re.M) is None,
      "Modelfile has no SYSTEM line (apps would replace it; her character belongs in MESSAGE system)")
ctx = re.search(r"^PARAMETER num_ctx (\d+)", modelfile, re.M)
# The app's default comes from adjustments/defaults.env by way of Defaults.kt.
APP_DEFAULTS = APP_PREFS.parent / "Defaults.kt"
app_ctx = re.search(r"CONTEXT_TOKENS: Int = ([\d_]+)", APP_DEFAULTS.read_text()) if APP_DEFAULTS.exists() else None
if ctx and app_ctx:
    check(int(ctx.group(1)) == int(app_ctx.group(1).replace("_", "")),
          f"Modelfile num_ctx ({ctx.group(1)}) matches the app's default context size ({app_ctx.group(1)})")
else:
    check(False, "found num_ctx in the Modelfile and the context size default in Defaults.kt")

# The reminder the integration sends last, and the copy the personality test sends. Both are plain
# Python, so they're loaded and compared.
def _loaded(name: str, path: Path):
    """A Python file loaded as a module, without running it as a script."""
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    sys.modules[name] = module  # dataclasses look their module up by name
    spec.loader.exec_module(module)
    return module


check(_loaded("lena_brain_reminder", ROOT / "custom_components" / "lena" / "brain.py").HOUSE_REMINDER
      == _loaded("lena_personality_check", ROOT / "tools" / "personality_check.py").HOUSE_REMINDER,
      "tools/personality_check.py sends the same reminder as the integration (HOUSE_REMINDER in brain.py)")

# The Open WebUI filter puts right a tool name she gets wrong only in case or punctuation, and never
# gives her a tool she wasn't offered. Open WebUI's pydantic is stood in for when it isn't installed.
if importlib.util.find_spec("pydantic") is None:
    _pydantic = types.ModuleType("pydantic")
    _pydantic.BaseModel = object
    _pydantic.Field = lambda default=None, **_: default
    sys.modules["pydantic"] = _pydantic
_filter = _loaded("lena_owui_filter", OWUI_FILTER)
_offered = {"home-assistant_homeassistant__GetLiveContext", "home-assistant_homeassistant__HassTurnOn", "remember"}
check(_filter.matching_tool_name("home_assistant_homeassistant__GetLiveContext", _offered)
      == "home-assistant_homeassistant__GetLiveContext",
      "the filter maps home_assistant_… to the offered home-assistant_… tool")
check(_filter.matching_tool_name("remember", _offered) is None
      and _filter.matching_tool_name("delete_everything", _offered) is None
      and _filter.matching_tool_name("a_b", {"a-b", "a.b"}) is None,
      "the filter leaves exact, unknown and ambiguous tool names alone")
_event = {"choices": [{"delta": {"tool_calls": [{"index": 0, "function": {"name": "Home_Assistant_HomeAssistant__GetLiveContext"}}]}}]}
_filter.fix_tool_names(_event, _offered)
check(_event["choices"][0]["delta"]["tool_calls"][0]["function"]["name"] == "home-assistant_homeassistant__GetLiveContext",
      "the filter corrects a tool call's name in a streamed piece of her answer")

# ---- 7. Python files compile ---------------------------------------------------------------------
for path in [BRIDGE, OWUI_FILTER, OWUI_TOOLS, *sorted(LENA.glob("*.py")), *sorted((ROOT / "tests" / "home-assistant" / "tests").glob("*.py"))]:
    out = ROOT / ".check.pyc"
    try:
        py_compile.compile(str(path), cfile=str(out), doraise=True)
        check(True, f"{rel(path)} compiles")
    except py_compile.PyCompileError as err:
        check(False, f"{rel(path)} does not compile: {err.msg}")
    finally:
        out.unlink(missing_ok=True)

# ---- 8. No private data left in the repository --------------------------------------------------
# Home network addresses, and anything that looks like a real secret assigned
# to a password/token/key field. ${{ secrets.X }} references and $VARIABLES are
# fine, since they only name a secret. Edit these to match what you must never publish.
SKIP_DIRS = {".git", ".gradle", "build", ".idea", "__pycache__"}
SCANNED = {".md", ".txt", ".yml", ".yaml", ".py", ".kt", ".kts", ".xml", ".properties", ".pro", ".json", ".ini", ""}
private_ip = re.compile(r"\b(192\.168\.\d{1,3}\.\d{1,3}|10\.\d{1,3}\.\d{1,3}\.\d{1,3}|172\.(1[6-9]|2\d|3[01])\.\d{1,3}\.\d{1,3})\b")
secret = re.compile(r"(password|passwd|token|api[_-]?key|secret)\s*[:=]\s*[\"']?([A-Za-z0-9_\-+/]{16,})", re.I)
hits = []
for path in ROOT.rglob("*"):
    relative = path.relative_to(ROOT)
    if not path.is_file() or SKIP_DIRS & set(relative.parts) or path.suffix not in SCANNED:
        continue
    if path.name in {"LICENSE", "check.py"}:
        continue
    for number, line in enumerate(path.read_text(errors="ignore").splitlines(), 1):
        if private_ip.search(line):
            hits.append(f"{relative}:{number} (IP address)")
        for match in secret.finditer(line):
            if "${{" not in line and not match.group(2).isupper():
                hits.append(f"{relative}:{number} ({match.group(1)} value)")
check(not hits, "no obvious private data found" + (f" ({', '.join(hits[:10])})" if hits else ""))

# ---- 9. Home Assistant brand images -----------------------------------------------------------
# Sizes from the home-assistant/brands rules; tools/make_images.py makes them. Read from the PNG
# header so the check needs no image library.
def png_size(path):
    """A PNG's width and height, read from its header so the check needs no image library."""
    head = path.read_bytes()[:24]
    if head[:8] != b"\x89PNG\r\n\x1a\n":
        return None
    return int.from_bytes(head[16:20], "big"), int.from_bytes(head[20:24], "big")

BRAND = ROOT / "custom_components/lena/brand"
for name, k in (("icon.png", 1), ("icon@2x.png", 2)):
    size = png_size(BRAND / name) if (BRAND / name).exists() else None
    check(size == (256 * k, 256 * k), f"brand/{name} is a {256 * k}x{256 * k} PNG (got {size})")
for name, k in (("logo.png", 1), ("logo@2x.png", 2), ("dark_logo.png", 1), ("dark_logo@2x.png", 2)):
    size = png_size(BRAND / name) if (BRAND / name).exists() else None
    check(size is not None and size[0] >= size[1] and 128 * k <= min(size) <= 256 * k,
          f"brand/{name} is a landscape PNG with its short side {128 * k}-{256 * k} (got {size})")

# ---- 10. One version everywhere ---------------------------------------------------------------
# The whole project shares one version: the app's versionName, the integration's manifest, the
# Open WebUI files' front matter and the newest CHANGELOG section. A mismatch means a release
# would ship parts that say different things about what they are.
# The places it's written are listed once, in scripts/release_notes.py, which checks them again before
# a release; the voice bridge's User-Agent is one of them.
sys.path.insert(0, str(ROOT / "scripts"))
import release_notes  # noqa: E402 - the scripts folder, added just above

gradle = (ROOT / "android/app/build.gradle.kts").read_text()
versions = release_notes.project_versions()
app_version = versions["android/app/build.gradle.kts (versionName)"]
newest = re.search(r"^## \[?v?([0-9][^\] ]*)", (ROOT / "CHANGELOG.md").read_text(), re.M)
versions["CHANGELOG.md (newest section)"] = newest.group(1) if newest else None
for where, v in versions.items():
    check(v == app_version, f"{where} is version {app_version} (got {v})")

# Every release says what to update, part by part, at the end of its changelog section.
newest_section = release_notes.changelog_section(app_version) or ""
missing_parts = release_notes.missing_update_lines(newest_section)
check(not missing_parts, f"CHANGELOG.md's {app_version} section ends with what to update for every part"
      + (f" (missing: {', '.join(missing_parts)})" if missing_parts else ""))

# No 1.0 until the owner says so: every part stays 0.x.
check(app_version.split(".")[0] == "0", f"the version stays below 1.0 until it's approved (got {app_version})")

# ---- 11. No new dependencies without approval ---------------------------------------------------
# Every library the app pulls in, and every non-standard Python module the shipped code imports,
# must be on these lists. Adding one means asking first, then adding it here and to
# THIRD_PARTY_NOTICES.md in the same change.
APPROVED_GRADLE = {
    "androidx.compose:compose-bom", "androidx.compose.ui:ui", "androidx.compose.foundation:foundation",
    "androidx.compose.material3:material3", "androidx.compose.material:material-icons-extended",
    "androidx.activity:activity-compose", "androidx.core:core-ktx", "androidx.lifecycle:lifecycle-runtime-ktx",
    "androidx.webkit:webkit", "androidx.work:work-runtime-ktx", "androidx.glance:glance-appwidget",
    "org.jetbrains.kotlinx:kotlinx-coroutines-android", "com.google.ai.edge.litert:litert",
}
APPROVED_GRADLE_PLUGINS = {"com.android.application", "org.jetbrains.kotlin.android", "org.jetbrains.kotlin.plugin.compose"}
# What Home Assistant and Open WebUI already provide to the code that runs inside them.
APPROVED_PYTHON = {"homeassistant", "aiohttp", "probatio", "yaml", "pydantic"}

used_gradle = set(re.findall(r'(?:implementation|platform)\("([\w.\-]+:[\w.\-]+)', gradle))
for dep in sorted(used_gradle - APPROVED_GRADLE):
    check(False, f"android/app/build.gradle.kts uses {dep}, which isn't approved")
plugins = set(re.findall(r'id\("([\w.\-]+)"\)', gradle + (ROOT / "android/build.gradle.kts").read_text()))
for plugin in sorted(plugins - APPROVED_GRADLE_PLUGINS):
    check(False, f"the Gradle build uses the plugin {plugin}, which isn't approved")

shipped_python = (
    list((ROOT / "custom_components/lena").glob("*.py"))
    + list((ROOT / "open-webui").glob("*.py"))
    + [ROOT / "voice/bridge/bridge.py"]
)
local_modules = {p.stem for p in shipped_python}
# One line for all of them: each file's imports beyond the standard library and its own files, and only the
# ones that aren't provided by Home Assistant or Open WebUI named when it fails.
unapproved = set()
for path in shipped_python:
    for module in re.findall(r"^(?:from|import) +([\w]+)", path.read_text(), re.M):
        if module in sys.stdlib_module_names or module in local_modules or module == "__future__":
            continue
        if module not in APPROVED_PYTHON:
            unapproved.add(f"{rel(path)} imports {module}")
check(not unapproved, "the integration, Open WebUI's files and the voice bridge import only the standard library and what "
      "Home Assistant and Open WebUI provide" + (f" (not approved: {'; '.join(sorted(unapproved))})" if unapproved else ""))
manifest = json.loads((ROOT / "custom_components/lena/manifest.json").read_text())
check(manifest.get("requirements", []) == [], "the integration installs no Python packages (manifest requirements is empty)")

# ---- 12. Default settings: requested changes are applied before a build -------------------------
# adjustments/defaults.env is where defaults are changed (see adjustments/README.md). Every value asked
# for after a setting's = must be applied (python3 tools/defaults.py apply) before a build, and the app's Defaults.kt must match.

sys.path.insert(0, str(ROOT / "tools"))
import defaults  # noqa: E402 - the tools folder, added just above

issues = defaults.check()
for issue in issues:
    check(False, f"adjustments/defaults.env: {issue}")
check(not issues, "adjustments/defaults.env is applied and matches Defaults.kt")

# ---- 13. Home Assistant's Marketplace -------------------------------------------------------------
# It installs the one integration under custom_components/ at the repository's root, needs hacs.json, these
# manifest keys, and brand images in the integration (https://hacs.xyz/docs/publish/integration). It browses by
# the manifest's categories: one to three from https://contributors.home-assistant.io/core/integrations/categories/
# (its check fails on an unknown one).

hacs = json.loads((ROOT / "hacs.json").read_text())
check(bool(hacs.get("name")), "hacs.json has a name")
integrations = [p.name for p in (ROOT / "custom_components").iterdir() if p.is_dir() and p.name != "__pycache__"]
check(integrations == ["lena"], f"custom_components/ holds only lena (found {integrations})")
for key in ("domain", "documentation", "issue_tracker", "codeowners", "name", "version"):
    check(bool(manifest.get(key)), f"the integration's manifest.json has {key}")
check((LENA / "brand" / "icon.png").exists(), "custom_components/lena/brand/icon.png exists")
check(manifest.get("categories") == ["ai_voice.voice_assistants"],
      "the integration's manifest.json lists its Marketplace category (ai_voice.voice_assistants)")
# Gitea's runner runs steps with sh unless told otherwise; the steps are written for bash, as on GitHub.
for workflow in sorted((ROOT / ".gitea" / "workflows").glob("*.yml")):
    shell = (yaml.safe_load(workflow.read_text()).get("defaults") or {}).get("run", {}).get("shell")
    check(shell == "bash", f"{workflow.relative_to(ROOT)} runs its steps in bash (defaults: run: shell: bash)")

# The tests run on GitHub and on Gitea, before every release too, each with the same list of Home Assistant releases,
# the last being the one Lena targets: the one in hacs.json, the only one she supports along with newer ones.
for workflow in (".github/workflows/home-assistant.yml", ".github/workflows/release.yml",
                 ".gitea/workflows/test.yml", ".gitea/workflows/release.yml"):
    oldest = re.search(r'home-assistant: \[.*"(\d{4}\.\d+)"\]', (ROOT / workflow).read_text())
    check(oldest is not None and hacs.get("homeassistant", "").startswith(oldest.group(1) + "."),
          f"hacs.json's minimum Home Assistant is the release {workflow} tests on last")

# ---- 14. The wall display debloat script ------------------------------------------------------------
# tools/devices/wall-display-debloat.sh must parse, and its lists (written by tools/devices/debloat_lists.py) must never
# remove a package it protects.

import shutil  # noqa: E402
import subprocess  # noqa: E402

debloat = ROOT / "tools/devices/wall-display-debloat.sh"
if shutil.which("bash"):
    parsed = subprocess.run(["bash", "-n", str(debloat)], capture_output=True, text=True)
    check(parsed.returncode == 0, f"tools/devices/wall-display-debloat.sh parses ({parsed.stderr.strip() or 'bash -n'})")
sys.path.insert(0, str(ROOT / "tools" / "devices"))
import debloat_lists  # noqa: E402 - from tools/devices, added just above

debloat_text = debloat.read_text(encoding="utf-8")
removing = set(re.findall(r"^([A-Za-z][\w.]*)\|", debloat_text, re.M))
check(len(removing) > 100, "tools/devices/wall-display-debloat.sh has its package lists")
overlap = sorted(removing & debloat_lists.protected())
check(not overlap, f"the debloat script never removes a protected package ({', '.join(overlap) or 'none'})")

# ---- 14a. Settings text ------------------------------------------------------------------------------
# docs/development/Settings-Text.md: one name per thing in the words people read. The mechanical rules are checked here,
# in every string of the app's code (comments aside): US spelling, no "&" between words, "sign in" not
# "log in", and Lena's screensaver as one word (Android's own menu, quoted as "Screen saver →", is fine).

text_problems = []
NOT_WRITTEN = [(r"colour", "color"), (r"customis", "customiz"), (r"optimis", "optimiz"), (r"\blog ?in\b", "sign in"),
               (r"\w & \w", "and"), (r"[Ss]creen saver(?! →)", "screensaver")]
for kt in sorted((ROOT / "android/app/src/main/kotlin").rglob("*.kt")):
    for number, line in enumerate(kt.read_text(encoding="utf-8").splitlines(), 1):
        code = line.split("//")[0] if not line.lstrip().startswith(("*", "/*", "//")) else ""
        if "Findable(" in code:
            continue  # Settings' search words: what people might type, in any spelling
        for literal in re.findall(r'"((?:[^"\\]|\\.)*)"', code):
            if literal.startswith(("http", "/")) or "&amp;" in literal or "<" in literal:
                continue  # an address, a path, or a web page's markup
            for pattern, instead in NOT_WRITTEN:
                if re.search(pattern, literal):
                    text_problems.append(f"{kt.name}:{number} \"{literal[:40]}\" (write {instead})")
check(not text_problems, "the app's text follows docs/development/Settings-Text.md" + (f" ({'; '.join(text_problems[:12])})" if text_problems else ""))

# ---- 14b. Android TV ---------------------------------------------------------------------------------
# The app installs on Android 15 and later (minSdk 35), TVs included, and targets Android 16, so every
# call newer than Android 15 must be behind a version check: the build's lint fails on any that isn't
# (NewApi, made fatal in build.gradle.kts). Nothing is kept for older Android: no values-v31 style folders
# or Compat.kt shims. tools/devices/android-tv-home.sh, which makes Lena a TV's home screen, must parse.

gradle_text = (ROOT / "android/app/build.gradle.kts").read_text()
check(re.search(r"^\s*minSdk = 35\b", gradle_text, re.M) is not None, "the app's minSdk is 35 (Android 15)")
res_dir = ROOT / "android/app/src/main/res"
old_folders = sorted(p.name for p in res_dir.iterdir() if p.is_dir() and re.search(r"-v(\d+)$", p.name) and int(re.search(r"-v(\d+)$", p.name).group(1)) <= 35)
check(not old_folders, "no resource folders for older Android than the minSdk" + (f" ({', '.join(old_folders)})" if old_folders else ""))
check(not (ROOT / "android/app/src/main/kotlin/io/github/soullink9/lena/Compat.kt").exists(), "no Compat.kt: nothing older than Android 15 to check for")
check('fatal += "NewApi"' in gradle_text, "release builds fail on a newer-Android call without a version check (lint NewApi is fatal)")
manifest_text = (ROOT / "android/app/src/main/AndroidManifest.xml").read_text()
check("android.intent.category.LEANBACK_LAUNCHER" in manifest_text and 'android:banner="@drawable/tv_banner"' in manifest_text,
      "the app shows in Android TV's apps, with its banner")
if shutil.which("bash"):
    parsed = subprocess.run(["bash", "-n", str(ROOT / "tools/devices/android-tv-home.sh")], capture_output=True, text=True)
    check(parsed.returncode == 0, f"tools/devices/android-tv-home.sh parses ({parsed.stderr.strip() or 'bash -n'})")

# ---- 15. The release scripts ---------------------------------------------------------------------
# scripts/ checks the version, writes the notes, checks the APK and publishes on Gitea, in both release
# workflows; tests/release/test_release.py tries each of them (against a fake Gitea), so a mistake shows up here
# rather than halfway through a release.

released = subprocess.run([sys.executable, str(ROOT / "tests/release/test_release.py")], capture_output=True, text=True)
for line in released.stdout.splitlines():
    if line.startswith("FAIL"):
        check(False, f"tests/release/test_release.py: {line[5:]}")
check(released.returncode == 0, f"tests/release/test_release.py passes ({released.stdout.strip().splitlines()[-1] if released.stdout.strip() else released.stderr.strip()[-200:]})")

print()
if failures:
    print(f"{len(failures)} check(s) failed.")
    sys.exit(1)
print("All checks passed.")
