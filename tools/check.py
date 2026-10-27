#!/usr/bin/env python3
"""Sanity checks for the Lena repository. Run from the repo root:

    pip install pyyaml
    python3 tools/check.py

It does NOT need Home Assistant, Ollama, Open WebUI or the Android SDK (the Home Assistant
integration's full tests run on GitHub against a real Home Assistant; see
.github/workflows/home-assistant.yml). It catches the mistakes that are easy to make when
editing:

  - broken YAML or JSON;
  - feelings.yaml missing a feeling her mood engine uses, or a description that isn't text;
  - each of her feelings events choosing something that isn't defined;
  - the Open WebUI tool offering moments the integration doesn't have;
  - the Modelfile losing her character or its context size drifting from the app's;
  - Python that doesn't compile, and private data slipping into the repository.

Exit code 0 means every check passed.
"""
import importlib.util
import json
import py_compile
import random
import re
import sys
from datetime import datetime, time, timedelta
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parent.parent
LENA = ROOT / "home-assistant" / "custom_components" / "lena"
FEELINGS = LENA / "feelings.yaml"
OWUI_FILTER = ROOT / "open-webui" / "lena_filter.py"
OWUI_TOOLS = ROOT / "open-webui" / "lena_tools.py"
COMPOSE_FILES = [
    ROOT / "ollama" / "docker-compose.example.yml",
    ROOT / "voice" / "piper" / "docker-compose.example.yml",
    ROOT / "voice" / "bridge" / "compose.yaml",
]
WORKFLOWS = sorted((ROOT / ".github" / "workflows").glob("*.yml"))
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
check(brain.energy_pool(now.replace(hour=2), time(6, 30), time(22, 30)) == ["Drained"], "she's drained in the night")

text = brain.describe(brain.Feelings(mood="Grumpy", mood_reason="It's raining.", moment="Pleased"), catalog)
others = [m for m in catalog.moods if m != "Grumpy" and f"Mood: {m}." in text]
check("Mood: Grumpy." in text and "Why: It's raining." in text and "Just now: Pleased." in text and not others,
      "her prompt describes only the feelings she has right now")
check("Why:" not in brain.describe(brain.Feelings(mood="Grumpy", mood_reason="x"), catalog, with_reasons=False),
      "reasons can be left out")
items = ["Alex likes the office at seventy degrees.", "Sam is allergic to cats."]
check(brain.best_match("office temperature", items) == items[0] and brain.best_match("Sam allergy", items) == items[1]
      and brain.best_match("quantum physics", items) is None, "forgetting finds the memory meant, and only that")

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
app_ctx = re.search(r"contextTokens: Int = ([\d_]+)", APP_PREFS.read_text()) if APP_PREFS.exists() else None
if ctx and app_ctx:
    check(int(ctx.group(1)) == int(app_ctx.group(1).replace("_", "")),
          f"Modelfile num_ctx ({ctx.group(1)}) matches the app's default context size ({app_ctx.group(1)})")
else:
    check(False, "found num_ctx in the Modelfile and the context size default in Prefs.kt")

# ---- 7. Python files compile ---------------------------------------------------------------------
for path in [BRIDGE, OWUI_FILTER, OWUI_TOOLS, *sorted(LENA.glob("*.py")), *sorted((ROOT / "home-assistant" / "tests").glob("*.py"))]:
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

print()
if failures:
    print(f"{len(failures)} check(s) failed.")
    sys.exit(1)
print("All checks passed.")
