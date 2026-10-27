#!/usr/bin/env python3
"""Sanity checks for the Lena repository. Run from the repo root:

    pip install pyyaml jinja2
    python3 tools/check.py

It does NOT need Home Assistant, Ollama, Open WebUI or the Android SDK. It
catches the mistakes that are easy to make when editing:

  - broken YAML or Jinja in the Home Assistant files;
  - a feeling name used somewhere (the mood engine, the Lena feel script, the
    Open WebUI tool) that has no definition in custom_templates/lena.jinja;
  - the mood engine choosing something it can't write, or a template error
    in any of its branches (each branch is run against stand-in states);
  - the Modelfile losing her character or its context size drifting from
    the app's;
  - private data slipping into the repository.

Exit code 0 means every check passed.
"""
import ast
import datetime as dt
import py_compile
import random
import re
import sys
from pathlib import Path
from types import SimpleNamespace as NS

import jinja2
import yaml

ROOT = Path(__file__).resolve().parent.parent
HA = ROOT / "home-assistant"
PROMPT = HA / "lena_prompt.txt"
FEELINGS = HA / "custom_templates" / "lena.jinja"
ENGINE = HA / "blueprints" / "automation" / "soullink9" / "lena_mood_engine.yaml"
FEEL = HA / "blueprints" / "script" / "soullink9" / "lena_feel.yaml"
RECALL = HA / "blueprints" / "script" / "soullink9" / "lena_recall.yaml"
OWUI_FILTER = ROOT / "open-webui" / "lena_filter.py"
OWUI_TOOLS = ROOT / "open-webui" / "lena_tools.py"
COMPOSE_FILES = [
    ROOT / "ollama" / "docker-compose.example.yml",
    ROOT / "voice" / "piper" / "docker-compose.example.yml",
    ROOT / "voice" / "bridge" / "compose.yaml",
]
MODELFILE = ROOT / "ollama" / "Modelfile"
APP_PREFS = ROOT / "android" / "app" / "src" / "main" / "kotlin" / "io" / "github" / "soullink9" / "lena" / "Prefs.kt"
BRIDGE = ROOT / "voice" / "bridge" / "bridge.py"
WORKFLOW = ROOT / ".github" / "workflows" / "build.yml"
NEUTRAL_MOMENT = "Nothing"  # the Moment dropdown's "no reaction" option

failures = []


def check(ok, message):
    """Record a pass or a failure and print it."""
    print(("PASS  " if ok else "FAIL  ") + message)
    if not ok:
        failures.append(message)


def rel(path):
    return path.relative_to(ROOT)


class HALoader(yaml.SafeLoader):
    """YAML loader that keeps Home Assistant's !input tags."""


HALoader.add_constructor("!input", lambda loader, node: NS(input=loader.construct_scalar(node)))


def walk_strings(node):
    """Yield every string inside a nested YAML structure."""
    if isinstance(node, dict):
        for value in node.values():
            yield from walk_strings(value)
    elif isinstance(node, list):
        for value in node:
            yield from walk_strings(value)
    elif isinstance(node, str):
        yield node


# ---- A small stand-in for Home Assistant's template engine -------------------
NOW = dt.datetime(2026, 1, 1, 9, 7)
STATE = {}


def stand_in_state(entity_id, state, attributes=None, hours_ago=5):
    when = NOW - dt.timedelta(hours=hours_ago)
    attributes = attributes or {}
    name = attributes.get("friendly_name", entity_id.split(".")[1].replace("_", " ").title())
    return NS(entity_id=entity_id, state=state, attributes=attributes, name=name,
              last_changed=when, last_updated=when)


class States:
    def __call__(self, entity_id):
        return STATE[entity_id].state if entity_id in STATE else "unknown"

    def __getitem__(self, entity_id):
        return STATE.get(entity_id)

    def __getattr__(self, name):
        if "." in name:
            return STATE.get(name)
        return [s for e, s in STATE.items() if e.startswith(name + ".")]


env = jinja2.Environment(loader=jinja2.FileSystemLoader(str(FEELINGS.parent)))
env.globals.update(
    states=States(),
    state_attr=lambda e, a: STATE[e].attributes.get(a) if e in STATE else None,
    now=lambda: NOW,
    timedelta=dt.timedelta,
    today_at=lambda t="00:00": dt.datetime.combine(NOW.date(), dt.time.fromisoformat(t)),
)
env.tests["is_state"] = lambda e, v: e in STATE and STATE[e].state == v
env.tests["eq"] = lambda a, b: a == b


def ha_result(text):
    """Turn rendered text into a value the way Home Assistant does."""
    text = text.strip()
    try:
        value = ast.literal_eval(text)
        return text if isinstance(value, str) else value
    except (ValueError, SyntaxError, TypeError):
        return text


# ---- 1. YAML files parse ------------------------------------------------------
parsed = {}
for path in [ENGINE, FEEL, RECALL, *COMPOSE_FILES, WORKFLOW]:
    try:
        parsed[path] = yaml.load(path.read_text(), HALoader)
        check(True, f"{rel(path)} is valid YAML")
    except yaml.YAMLError as err:
        check(False, f"{rel(path)} is not valid YAML: {err}")

# ---- 2. Every Jinja template parses -------------------------------------------
for path in [ENGINE, FEEL, RECALL]:
    if path not in parsed:
        continue
    bad = 0
    for text in walk_strings(parsed[path]):
        if "{{" in text or "{%" in text:
            try:
                env.parse(text)
            except jinja2.TemplateSyntaxError as err:
                bad += 1
                print(f"      template error: {err} in: {text[:60]!r}")
    check(bad == 0, f"{rel(path)} templates parse")

# ---- 3. The feelings and their definitions ------------------------------------
# Read the names defined in lena.jinja straight from its three tables.
feelings_text = FEELINGS.read_text()


def table(name):
    block = re.search(r"set " + name + r" = \{(.*?)\} -%\}", feelings_text, re.S)
    return re.findall(r"^\s*'(\w+)':", block.group(1), re.M) if block else []


moods, energies, moments = table("moods"), table("energies"), table("moments")
check(len(moods) > 0 and len(energies) > 0 and len(moments) > 0,
      f"lena.jinja defines {len(moods)} moods, {len(energies)} energies, {len(moments)} moments")
check(NEUTRAL_MOMENT not in moments, f"'{NEUTRAL_MOMENT}' (no reaction) has no definition, so it adds nothing")

# Every combination renders, and only the current feeling's definition appears.
try:
    feel_template = env.from_string("{% from 'lena.jinja' import feelings %}{{ feelings() }}")
    STATE.clear()
    empty = feel_template.render()
    check("Mood: Content." in empty and "Energy: Steady." in empty, "missing helpers fall back to Content and Steady")
    wrong = []
    for mood in moods:
        for energy in energies:
            for moment in moments + [NEUTRAL_MOMENT]:
                STATE.clear()
                for entity, value in [("input_select.lena_mood", mood), ("input_select.lena_energy", energy),
                                      ("input_select.lena_moment", moment),
                                      ("input_text.lena_mood_reason", "Test reason."),
                                      ("input_text.lena_moment_reason", "")]:
                    STATE[entity] = stand_in_state(entity, value)
                out = feel_template.render()
                others = [m for m in moods if m != mood and f"Mood: {m}." in out]
                if f"Mood: {mood}." not in out or f"Energy: {energy}." not in out or others \
                        or (moment in moments) != (f"Just now: {moment}." in out) or "{" in out:
                    wrong.append(f"{mood}/{energy}/{moment}")
    check(not wrong, f"feelings() renders all {len(moods) * len(energies) * (len(moments) + 1)} combinations correctly"
          + (f" (wrong: {', '.join(wrong[:5])})" if wrong else ""))
except jinja2.TemplateError as err:
    check(False, f"lena.jinja failed to render: {err}")

# ---- 4. Her Home Assistant prompt renders ---------------------------------------
try:
    STATE.clear()
    STATE["person.alex"] = stand_in_state("person.alex", "home", {"friendly_name": "Alex"})
    STATE["person.sam"] = stand_in_state("person.sam", "not_home", {"friendly_name": "Sam"})
    rendered = env.from_string(PROMPT.read_text()).render()
    check(True, "lena_prompt.txt renders")
    check("## How you feel right now" in rendered, "the prompt includes her feelings")
    check("At home: Alex." in rendered, "the prompt lists who's home")
    leftovers = [m for m in ("{#", "#}", "{%", "%}", "{{", "}}") if m in rendered]
    check(not leftovers, "rendered prompt has no leftover template markers")
except jinja2.TemplateError as err:
    check(False, f"lena_prompt.txt failed to render: {err}")

# ---- 5. Every feeling the other pieces use has a definition ---------------------
engine = parsed.get(ENGINE, {})
variables = engine.get("variables", {})


def names_in(value):
    if isinstance(value, dict):
        return [n for v in value.values() for n in names_in(v)]
    if isinstance(value, list):
        return [n for v in value for n in names_in(v)]
    return [value] if isinstance(value, str) else []


engine_moods = set(variables.get("neighbours", {})) | set(names_in(variables.get("neighbours", {}))) \
    | set(names_in(variables.get("baseline", []))) | set(names_in(variables.get("morning_moods", []))) \
    | set(names_in(variables.get("energy_pull", {}))) | set(names_in(variables.get("weather_pull", {})))
engine_text = ENGINE.read_text()
quoted = set(re.findall(r"'([A-Z][a-z]+)'", engine_text)) | set(re.findall(r"next_(?:mood|energy|moment): ([A-Z][a-z]+)\s*$", engine_text, re.M))
engine_moods |= {w for w in quoted if w in moods or w in ("Cheerful", "Fond", "Restless", "Wistful")}
engine_energies = set(variables.get("energy_pull", {})) | set(variables.get("energy_why", {})) \
    | {w for w in quoted if w in ("Groggy", "Steady", "Wired", "Drained")}
engine_moments = {w for w in quoted if w in ("Delighted", "Worried", "Relieved")} \
    | set(re.findall(r"next_moment: ([A-Z][a-z]+)", engine_text))
check(engine_moods <= set(moods), "every mood the mood engine uses is defined"
      + (f" (missing: {sorted(engine_moods - set(moods))})" if engine_moods - set(moods) else ""))
check(engine_energies <= set(energies), "every energy the mood engine uses is defined"
      + (f" (missing: {sorted(engine_energies - set(energies))})" if engine_energies - set(energies) else ""))
check(engine_moments <= set(moments) | {NEUTRAL_MOMENT}, "every moment the mood engine uses is defined"
      + (f" (missing: {sorted(engine_moments - set(moments) - {NEUTRAL_MOMENT})})" if engine_moments - set(moments) - {NEUTRAL_MOMENT} else ""))
neighbour_keys = set(variables.get("neighbours", {}))
check(neighbour_keys == set(moods), "every mood has a list of neighbours in the mood engine"
      + (f" (no neighbours: {sorted(set(moods) - neighbour_keys)})" if set(moods) - neighbour_keys else ""))

feel_options = parsed.get(FEEL, {}).get("fields", {}).get("moment", {}).get("selector", {}).get("select", {}).get("options", [])
check(feel_options and set(feel_options) <= set(moments), "every moment the Lena feel script offers is defined")
tool_literal = re.search(r"moment: Literal\[(.*?)\]", OWUI_TOOLS.read_text(), re.S)
tool_options = re.findall(r'"(\w+)"', tool_literal.group(1)) if tool_literal else []
check(tool_options == feel_options, "the Open WebUI tool offers the same moments as the Lena feel script")


# ---- 6. Run each mood engine branch against stand-in states ---------------------
def run_engine():
    """A rough Home Assistant stand-in: evaluates each branch and records what it writes."""
    inputs = {
        "mood_entity": "input_select.lena_mood", "energy_entity": "input_select.lena_energy",
        "moment_entity": "input_select.lena_moment", "mood_reason_entity": "input_text.lena_mood_reason",
        "moment_reason_entity": "input_text.lena_moment_reason", "morning_time": "06:30:00",
        "bedtime": "22:30:00", "special_days": ["calendar.special"], "people": ["person.alex", "person.sam"],
        "away_hours": 4, "empty_hours": 3, "weather": ["weather.home"], "weather_chance": 100,
        "worries": ["binary_sensor.leak"], "mood_hold_hours": 3, "mood_change_chance": 100, "moment_minutes": 20,
    }

    def resolve(v):
        if isinstance(v, NS):
            return inputs[v.input]
        if isinstance(v, dict):
            return {k: resolve(x) for k, x in v.items()}
        if isinstance(v, list):
            return [resolve(x) for x in v]
        return v

    def render(v, ctx):
        v = resolve(v)
        if isinstance(v, str):
            return ha_result(env.from_string(v).render(**ctx)) if "{" in v else ha_result(v)
        if isinstance(v, dict):
            return {k: render(x, ctx) for k, x in v.items()}
        if isinstance(v, list):
            return [render(x, ctx) for x in v]
        return v

    def passes(conditions, ctx, trigger_id):
        for c in conditions:
            if c["condition"] == "trigger" and c["id"] != trigger_id:
                return False
            if c["condition"] == "template" and render(c["value_template"], ctx) is not True:
                return False
        return True

    def run(sequence, ctx, trigger_id, writes):
        for step in sequence:
            if "variables" in step:
                for k, v in step["variables"].items():
                    ctx[k] = render(v, ctx)
            elif "choose" in step:
                for option in step["choose"]:
                    if passes(option["conditions"], ctx, trigger_id):
                        run(option["sequence"], ctx, trigger_id, writes)
                        break
            elif "if" in step:
                if passes(step["if"], ctx, trigger_id):
                    run(step["then"], ctx, trigger_id, writes)
            elif step.get("action") == "calendar.get_events":
                ctx[step["response_variable"]] = {"calendar.special": {"events": [{"summary": "Sam's Birthday"}]}}
            elif "action" in step:
                entity = render(step["target"]["entity_id"], ctx)
                data = render(step.get("data", {}), ctx)
                writes.append((entity, data.get("option", data.get("value"))))

    def scenario(trigger_id, trigger=None, mood="Content", energy="Steady", moment=NEUTRAL_MOMENT,
                 alex="home", sam="home", weather="pouring", leak="off"):
        STATE.clear()
        for entity, value, options in [("input_select.lena_mood", mood, moods),
                                       ("input_select.lena_energy", energy, energies),
                                       ("input_select.lena_moment", moment, moments + [NEUTRAL_MOMENT])]:
            STATE[entity] = stand_in_state(entity, value, {"options": options})
        for entity in ("input_text.lena_mood_reason", "input_text.lena_moment_reason"):
            STATE[entity] = stand_in_state(entity, "", {"max": 255}, hours_ago=6)
        STATE["person.alex"] = stand_in_state("person.alex", alex, {"friendly_name": "Alex"})
        STATE["person.sam"] = stand_in_state("person.sam", sam, {"friendly_name": "Sam"})
        STATE["weather.home"] = stand_in_state("weather.home", weather)
        STATE["binary_sensor.leak"] = stand_in_state("binary_sensor.leak", leak,
                                                     {"friendly_name": "Leak sensor", "device_class": "moisture"})
        ctx = {k: resolve(v) for k, v in engine.get("trigger_variables", {}).items()}
        ctx["trigger"] = trigger or NS(id=trigger_id)
        for k, v in engine["variables"].items():
            ctx[k] = render(v, ctx)
        writes = []
        run(engine["actions"], ctx, trigger_id, writes)
        return writes

    away = stand_in_state("person.alex", "not_home", hours_ago=30)
    back = stand_in_state("person.alex", "home", {"friendly_name": "Alex"}, hours_ago=0)
    leak_on = stand_in_state("binary_sensor.leak", "on", {"friendly_name": "Leak sensor", "device_class": "moisture"})
    leak_off = stand_in_state("binary_sensor.leak", "off", {"friendly_name": "Leak sensor"})
    cases = [("morning", None, {}), ("bedtime", None, {})]
    cases += [("hourly", None, {"mood": m, "energy": e}) for m in moods for e in energies]
    cases += [
        ("hourly", None, {"alex": "not_home", "sam": "not_home", "weather": "sunny"}),
        ("arrived", NS(id="arrived", from_state=away, to_state=back), {}),
        ("empty", None, {"alex": "not_home", "sam": "not_home"}),
        ("weather", NS(id="weather", from_state=stand_in_state("weather.home", "cloudy"),
                       to_state=stand_in_state("weather.home", "lightning-rainy")), {}),
        ("worry_on", NS(id="worry_on", to_state=leak_on), {"leak": "on"}),
        ("worry_off", NS(id="worry_off", to_state=leak_off), {"moment": "Worried"}),
        ("fade", None, {"moment": "Amused"}),
        ("fade", None, {"moment": "Worried", "leak": "on"}),
        ("mood_changed", None, {}),
    ]
    allowed = {"input_select.lena_mood": set(moods), "input_select.lena_energy": set(energies),
               "input_select.lena_moment": set(moments) | {NEUTRAL_MOMENT}}
    problems, wrote = [], set()
    random.seed(7)
    for trigger_id, trigger, states in cases:
        try:
            for entity, value in scenario(trigger_id, trigger, **states):
                wrote.add(trigger_id)
                if entity in allowed and value not in allowed[entity]:
                    problems.append(f"{trigger_id} wrote {value!r} to {entity}")
                if entity.startswith("input_text") and len(str(value)) > 255:
                    problems.append(f"{trigger_id} wrote a reason over 255 characters")
        except Exception as err:  # noqa: BLE001 - report any template failure
            problems.append(f"{trigger_id}: {type(err).__name__}: {err}")
    silent = {"morning", "bedtime", "arrived", "empty", "weather", "worry_on", "worry_off", "fade"} - wrote
    return problems, silent


if engine:
    problems, silent = run_engine()
    check(not problems, "every mood engine branch runs and writes only defined feelings"
          + (f" ({'; '.join(problems[:5])})" if problems else ""))
    check(not silent, "every mood engine event changes something"
          + (f" (did nothing: {sorted(silent)})" if silent else ""))

# ---- 7. Docker compose: $ must be escaped as $$ in shell commands -------------
compose = parsed.get(COMPOSE_FILES[0])
if compose:
    command = "\n".join(walk_strings(compose["services"]["ollama-watchdog"]["command"]))
    bare = re.findall(r"(?<!\$)\$(?![$])", command)
    check(not bare, "watchdog command escapes every dollar sign as $$")

# ---- 8. Modelfile -----------------------------------------------------------------
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

# ---- 9. Python files compile ---------------------------------------------------------
for path in [BRIDGE, OWUI_FILTER, OWUI_TOOLS]:
    out = ROOT / ".check.pyc"
    try:
        py_compile.compile(str(path), cfile=str(out), doraise=True)
        check(True, f"{rel(path)} compiles")
    except py_compile.PyCompileError as err:
        check(False, f"{rel(path)} does not compile: {err.msg}")
    finally:
        out.unlink(missing_ok=True)

# ---- 10. No private data left in the repository --------------------------------------
# Home network addresses, and anything that looks like a real secret assigned
# to a password/token/key field. ${{ secrets.X }} references and $VARIABLES are
# fine, since they only name a secret. Edit these to match what you must never publish.
SKIP_DIRS = {".git", ".gradle", "build", ".idea", "__pycache__"}
SCANNED = {".md", ".txt", ".yml", ".yaml", ".py", ".kt", ".kts", ".xml", ".properties", ".pro", ".jinja", ""}
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
