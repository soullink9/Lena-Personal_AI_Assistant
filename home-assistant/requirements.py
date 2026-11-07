"""Prints the Python packages Home Assistant pins for the integrations Lena loads (and what they load),
read from this Home Assistant's own manifests, so CI installs exactly those versions.

    python home-assistant/requirements.py conversation assist_satellite intent
"""

import json
import pathlib
import sys

import homeassistant

COMPONENTS = pathlib.Path(homeassistant.__file__).parent / "components"


def requirements(names: list[str]) -> list[str]:
    """Every requirement of these integrations and of everything they depend on."""
    seen: set[str] = set()
    found: list[str] = []
    todo = list(names)
    while todo:
        name = todo.pop()
        if name in seen or not (COMPONENTS / name / "manifest.json").exists():
            continue
        seen.add(name)
        manifest = json.loads((COMPONENTS / name / "manifest.json").read_text())
        found += [r for r in manifest.get("requirements", []) if r not in found]
        todo += manifest.get("dependencies", []) + manifest.get("after_dependencies", [])
    return found


if __name__ == "__main__":
    print(" ".join(requirements(sys.argv[1:])))
