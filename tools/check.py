#!/usr/bin/env python3
"""Sanity checks for the Lena repository. Run from the repo root:

    pip install pyyaml jinja2
    python3 tools/check.py

It does NOT need Home Assistant or Ollama. It catches the mistakes that are
easy to make when editing: broken YAML, broken Jinja, and the persona words in
the blueprint drifting out of step with the legend in the prompt.

Exit code 0 means every check passed.
"""
import datetime
import re
import sys
from pathlib import Path

import jinja2
import yaml

ROOT = Path(__file__).resolve().parent.parent
PROMPT = ROOT / "home_assistant" / "lena_prompt.txt"
BLUEPRINT = ROOT / "home_assistant" / "blueprints" / "automation" / "soullink9" / "ai_persona_engine.yaml"
SENSOR = ROOT / "home_assistant" / "templates" / "lena_memory_sensor.yaml"
COMPOSE_FILES = [ROOT / "ollama" / "docker-compose.example.yml", ROOT / "piper" / "docker-compose.example.yml"]
MODELFILE = ROOT / "ollama" / "Modelfile"

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
for path in [BLUEPRINT, SENSOR, *COMPOSE_FILES]:
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

# ---- 7. No private data left in the repository ------------------------------
# Edit this list to match anything you must never publish.
private_markers = ["192.168.", "10.0.0.", "api_key", "password:", "token:"]
hits = []
for path in ROOT.rglob("*"):
    if path.is_file() and ".git" not in path.parts and path.suffix in {".md", ".txt", ".yml", ".yaml", ".py", ""} and path.name != "LICENSE":
        text = path.read_text(errors="ignore").lower()
        hits += [f"{path.relative_to(ROOT)}: {m}" for m in private_markers if m in text and path.name != "check.py"]
check(not hits, "no obvious private data found" + (f" ({hits})" if hits else ""))

print()
if failures:
    print(f"{len(failures)} check(s) failed.")
    sys.exit(1)
print("All checks passed.")
