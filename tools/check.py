#!/usr/bin/env python3
"""Sanity checks for the Lena repository. Run from the repo root:

    pip install pyyaml jinja2
    python3 tools/check.py

It does NOT need Home Assistant, Ollama or the Android SDK. It catches the
mistakes that are easy to make when editing: broken YAML, broken Jinja, the
persona words in the blueprint drifting out of step with the legend in the
prompt, the context size drifting between the Modelfile and the app, and
private data slipping into the repository.

Exit code 0 means every check passed.
"""
import datetime
import py_compile
import re
import sys
from pathlib import Path

import jinja2
import yaml

ROOT = Path(__file__).resolve().parent.parent
HA = ROOT / "home-assistant"
PROMPT = HA / "lena_prompt.txt"
BLUEPRINT = HA / "blueprints" / "automation" / "soullink9" / "ai_persona_engine.yaml"
SENSOR = HA / "templates" / "lena_memory_sensor.yaml"
COMPOSE_FILES = [
    ROOT / "ollama" / "docker-compose.example.yml",
    ROOT / "voice" / "piper" / "docker-compose.example.yml",
    ROOT / "voice" / "bridge" / "compose.yaml",
]
MODELFILE = ROOT / "ollama" / "Modelfile"
APP_PREFS = ROOT / "android" / "app" / "src" / "main" / "kotlin" / "io" / "github" / "soullink9" / "lena" / "Prefs.kt"
BRIDGE = ROOT / "voice" / "bridge" / "bridge.py"
WORKFLOW = ROOT / ".github" / "workflows" / "build.yml"

failures = []


def check(ok, message):
    """Record a pass or a failure and print it."""
    print(("PASS  " if ok else "FAIL  ") + message)
    if not ok:
        failures.append(message)


class HALoader(yaml.SafeLoader):
    """YAML loader that tolerates Home Assistant's !input tag."""


HALoader.add_constructor("!input", lambda loader, node: "INPUT:" + loader.construct_scalar(node))


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


# ---- 1. YAML files parse ----------------------------------------------------
parsed = {}
for path in [BLUEPRINT, SENSOR, *COMPOSE_FILES, WORKFLOW]:
    try:
        parsed[path] = yaml.load(path.read_text(), HALoader)
        check(True, f"{path.relative_to(ROOT)} is valid YAML")
    except yaml.YAMLError as err:
        check(False, f"{path.relative_to(ROOT)} is not valid YAML: {err}")

# ---- 2. Every Jinja template parses -----------------------------------------
env = jinja2.Environment()
for path in [BLUEPRINT, SENSOR]:
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
    check(bad == 0, f"{path.relative_to(ROOT)} templates parse")

# ---- 3. The prompt renders cleanly with stand-in data -----------------------
class FakePerson:
    def __init__(self, name, state):
        self.name, self.state = name, state


class FakeStates:
    """Stand-in for Home Assistant's `states` object."""

    person = [FakePerson("Alex", "home"), FakePerson("Sam", "not_home")]

    def __call__(self, entity_id):
        return "unknown"  # worst case: every helper missing


try:
    rendered = env.from_string(PROMPT.read_text()).render(
        states=FakeStates(),
        state_attr=lambda entity, attr: None,
        now=lambda: datetime.datetime(2026, 1, 1, 9, 0),
    )
    check(True, "lena_prompt.txt renders with every helper missing")
    leftovers = [m for m in ("{#", "#}", "{%", "%}", "{{", "}}") if m in rendered]
    check(not leftovers, "rendered prompt has no leftover template markers")
    check("Mood: confident" in rendered, "missing helpers fall back to defaults")
    check("unknown" not in rendered.split("## Expression modifiers")[1].split("How to apply")[0],
          "no raw 'unknown' leaks into the modifiers")
except jinja2.TemplateError as err:
    rendered = ""
    check(False, f"lena_prompt.txt failed to render: {err}")

# ---- 4. Blueprint persona words are all explained in the prompt -------------
# Every capitalised word in single quotes inside the blueprint (Lazy, Smug,
# Disgusted, ...) is something the model will be told about; it must have a
# line in the prompt's legend, or the model has to guess what it means.
blueprint_text = BLUEPRINT.read_text()
words = sorted(set(re.findall(r"'([A-Z][a-z]+)'", blueprint_text)))
prompt_text = PROMPT.read_text()
missing = [w for w in words if w not in prompt_text]
check(len(words) > 0, f"found {len(words)} persona words in the blueprint")
check(not missing, "every blueprint word appears in the prompt legend"
      + (f" (missing: {', '.join(missing)})" if missing else ""))

# ---- 5. Docker compose: $ must be escaped as $$ in shell commands -----------
compose = parsed.get(COMPOSE_FILES[0])
if compose:
    command = "\n".join(walk_strings(compose["services"]["ollama-watchdog"]["command"]))
    bare = re.findall(r"(?<!\$)\$(?![$])", command)
    check(not bare, "watchdog command escapes every dollar sign as $$")

# ---- 6. Modelfile basics ----------------------------------------------------
modelfile = MODELFILE.read_text()
check(len(re.findall(r"^FROM ", modelfile, re.M)) == 1, "Modelfile has exactly one active FROM line")
check(modelfile.count('"""') == 2, "Modelfile SYSTEM block opens and closes")

# ---- 7. One context size ----------------------------------------------------
# num_ctx in the Modelfile and the app's default "Lena's context size" should match.
ctx = re.search(r"^PARAMETER num_ctx (\d+)", modelfile, re.M)
app_ctx = re.search(r"contextTokens: Int = ([\d_]+)", APP_PREFS.read_text()) if APP_PREFS.exists() else None
if ctx and app_ctx:
    check(int(ctx.group(1)) == int(app_ctx.group(1).replace("_", "")),
          f"Modelfile num_ctx ({ctx.group(1)}) matches the app's default context size ({app_ctx.group(1)})")
else:
    check(False, "found num_ctx in the Modelfile and the context size default in Prefs.kt")

# ---- 8. The voice bridge compiles --------------------------------------------
try:
    py_compile.compile(str(BRIDGE), cfile=str(ROOT / ".check-bridge.pyc"), doraise=True)
    check(True, "voice/bridge/bridge.py compiles")
except py_compile.PyCompileError as err:
    check(False, f"voice/bridge/bridge.py does not compile: {err.msg}")
finally:
    (ROOT / ".check-bridge.pyc").unlink(missing_ok=True)

# ---- 9. No private data left in the repository ------------------------------
# Home network addresses, and anything that looks like a real secret assigned
# to a password/token/key field. ${{ secrets.X }} references and $VARIABLES are
# fine, since they only name a secret. Edit these to match what you must never publish.
SKIP_DIRS = {".git", ".gradle", "build", ".idea", "__pycache__"}
SCANNED = {".md", ".txt", ".yml", ".yaml", ".py", ".kt", ".kts", ".xml", ".properties", ".pro", ""}
private_ip = re.compile(r"\b(192\.168\.\d{1,3}\.\d{1,3}|10\.\d{1,3}\.\d{1,3}\.\d{1,3}|172\.(1[6-9]|2\d|3[01])\.\d{1,3}\.\d{1,3})\b")
secret = re.compile(r"(password|passwd|token|api[_-]?key|secret)\s*[:=]\s*[\"']?([A-Za-z0-9_\-+/]{16,})", re.I)
hits = []
for path in ROOT.rglob("*"):
    rel = path.relative_to(ROOT)
    if not path.is_file() or SKIP_DIRS & set(rel.parts) or path.suffix not in SCANNED:
        continue
    if path.name in {"LICENSE", "check.py"}:
        continue
    for number, line in enumerate(path.read_text(errors="ignore").splitlines(), 1):
        if private_ip.search(line):
            hits.append(f"{rel}:{number} (IP address)")
        for match in secret.finditer(line):
            if "${{" not in line and not match.group(2).isupper():
                hits.append(f"{rel}:{number} ({match.group(1)} value)")
check(not hits, "no obvious private data found" + (f" ({', '.join(hits[:10])})" if hits else ""))

print()
if failures:
    print(f"{len(failures)} check(s) failed.")
    sys.exit(1)
print("All checks passed.")
