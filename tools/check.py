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
  - the release scripts in scripts/ going wrong (tests/test_release.py).

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
# The app's default comes from adjustments/defaults.env by way of Defaults.kt.
APP_DEFAULTS = APP_PREFS.parent / "Defaults.kt"
app_ctx = re.search(r"CONTEXT_TOKENS: Int = ([\d_]+)", APP_DEFAULTS.read_text()) if APP_DEFAULTS.exists() else None
if ctx and app_ctx:
    check(int(ctx.group(1)) == int(app_ctx.group(1).replace("_", "")),
          f"Modelfile num_ctx ({ctx.group(1)}) matches the app's default context size ({app_ctx.group(1)})")
else:
    check(False, "found num_ctx in the Modelfile and the context size default in Defaults.kt")

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
APPROVED_PYTHON = {"homeassistant", "aiohttp", "voluptuous", "probatio", "yaml", "pydantic"}

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
for path in shipped_python:
    for module in re.findall(r"^(?:from|import) +([\w]+)", path.read_text(), re.M):
        if module in sys.stdlib_module_names or module in local_modules or module == "__future__":
            continue
        check(module in APPROVED_PYTHON, f"{rel(path)} imports {module}, which isn't approved")
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

# ---- 13. HACS and Home Assistant's Marketplace ---------------------------------------------------
# They install the one integration under custom_components/ at the repository's root, need hacs.json,
# these manifest keys, and brand images in the integration (https://hacs.xyz/docs/publish/integration).

hacs = json.loads((ROOT / "hacs.json").read_text())
check(bool(hacs.get("name")), "hacs.json has a name")
integrations = [p.name for p in (ROOT / "custom_components").iterdir() if p.is_dir() and p.name != "__pycache__"]
check(integrations == ["lena"], f"custom_components/ holds only lena (found {integrations})")
for key in ("domain", "documentation", "issue_tracker", "codeowners", "name", "version"):
    check(bool(manifest.get(key)), f"the integration's manifest.json has {key}")
check((LENA / "brand" / "icon.png").exists(), "custom_components/lena/brand/icon.png exists")
# Gitea's runner runs steps with sh unless told otherwise; the steps are written for bash, as on GitHub.
for workflow in sorted((ROOT / ".gitea" / "workflows").glob("*.yml")):
    shell = (yaml.safe_load(workflow.read_text()).get("defaults") or {}).get("run", {}).get("shell")
    check(shell == "bash", f"{workflow.relative_to(ROOT)} runs its steps in bash (defaults: run: shell: bash)")

# The tests run on GitHub and on Gitea, before every release too, each with the same list of Home Assistant releases.
for workflow in (".github/workflows/home-assistant.yml", ".github/workflows/release.yml",
                 ".gitea/workflows/test.yml", ".gitea/workflows/release.yml"):
    oldest = re.search(r'home-assistant: \[.*"(\d{4}\.\d+)"\]', (ROOT / workflow).read_text())
    check(oldest is not None and hacs.get("homeassistant", "").startswith(oldest.group(1) + "."),
          f"hacs.json's minimum Home Assistant is the oldest one {workflow} tests on")

# ---- 14. The wall display debloat script ------------------------------------------------------------
# tools/wall-display-debloat.sh must parse, and its lists (written by tools/debloat_lists.py) must never
# remove a package it protects.

import shutil  # noqa: E402
import subprocess  # noqa: E402

debloat = ROOT / "tools/wall-display-debloat.sh"
if shutil.which("bash"):
    parsed = subprocess.run(["bash", "-n", str(debloat)], capture_output=True, text=True)
    check(parsed.returncode == 0, f"tools/wall-display-debloat.sh parses ({parsed.stderr.strip() or 'bash -n'})")
import debloat_lists  # noqa: E402 - the tools folder, added above

debloat_text = debloat.read_text(encoding="utf-8")
removing = set(re.findall(r"^([A-Za-z][\w.]*)\|", debloat_text, re.M))
check(len(removing) > 100, "tools/wall-display-debloat.sh has its package lists")
overlap = sorted(removing & debloat_lists.protected())
check(not overlap, f"the debloat script never removes a protected package ({', '.join(overlap) or 'none'})")

# ---- 14b. Android TV ---------------------------------------------------------------------------------
# The app installs on Android 11 TV boxes (minSdk 30) while phones use Android 16's features, so every
# newer call must be behind a version check: the build's lint fails on any that isn't (NewApi, made fatal
# in build.gradle.kts). tools/android-tv-home.sh, which makes Lena a TV's home screen, must parse.

gradle_text = (ROOT / "android/app/build.gradle.kts").read_text()
check(re.search(r"^\s*minSdk = 30\b", gradle_text, re.M) is not None, "the app's minSdk is 30 (Android 11, for Android TV boxes)")
check('fatal += "NewApi"' in gradle_text, "release builds fail on a newer-Android call without a version check (lint NewApi is fatal)")
manifest_text = (ROOT / "android/app/src/main/AndroidManifest.xml").read_text()
check("android.intent.category.LEANBACK_LAUNCHER" in manifest_text and 'android:banner="@drawable/tv_banner"' in manifest_text,
      "the app shows in Android TV's apps, with its banner")
if shutil.which("bash"):
    parsed = subprocess.run(["bash", "-n", str(ROOT / "tools/android-tv-home.sh")], capture_output=True, text=True)
    check(parsed.returncode == 0, f"tools/android-tv-home.sh parses ({parsed.stderr.strip() or 'bash -n'})")

# ---- 15. The release scripts ---------------------------------------------------------------------
# scripts/ checks the version, writes the notes, checks the APK and publishes on Gitea, in both release
# workflows; tests/test_release.py tries each of them (against a fake Gitea), so a mistake shows up here
# rather than halfway through a release.

released = subprocess.run([sys.executable, str(ROOT / "tests/test_release.py")], capture_output=True, text=True)
for line in released.stdout.splitlines():
    if line.startswith("FAIL"):
        check(False, f"tests/test_release.py: {line[5:]}")
check(released.returncode == 0, f"tests/test_release.py passes ({released.stdout.strip().splitlines()[-1] if released.stdout.strip() else released.stderr.strip()[-200:]})")

print()
if failures:
    print(f"{len(failures)} check(s) failed.")
    sys.exit(1)
print("All checks passed.")
