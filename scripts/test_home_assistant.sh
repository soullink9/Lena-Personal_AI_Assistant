#!/usr/bin/env bash
# Tests the Lena integration in a real Home Assistant: installs Home Assistant's test harness for one release,
# and what Lena loads at the versions that release pins, then runs home-assistant/tests.
#
#   scripts/test_home_assistant.sh latest     the newest release the harness supports
#   scripts/test_home_assistant.sh 2026.9     the newest harness for that release (the oldest Lena supports)
#
# Used by the workflows on Gitea (.gitea/workflows/test.yml, release.yml) and GitHub (home-assistant.yml,
# release.yml), so every runner tests the same way. It pip installs, so run it in a container or a virtualenv.
# Everything comes from PyPI, so it works when GitHub can't be reached.
#
# Failures and anything Home Assistant says Lena should change are also printed as ::error / ::warning lines,
# which GitHub and Gitea show on the run.
set -euo pipefail
release="${1:-latest}"
repo="$(cd "$(dirname "$0")/.." && pwd)"
log="$(mktemp)"
export PIP_ROOT_USER_ACTION=ignore PIP_DISABLE_PIP_VERSION_CHECK=1  # containers run as root; that's fine here

if [ "$release" = "latest" ]; then
  pip install -q pytest-homeassistant-custom-component
else
  # The test harness is released once per Home Assistant version; find the newest one for this release.
  harness=$(python - "$release" <<'PY'
import json, sys, urllib.request
want = sys.argv[1] + "."
info = json.load(urllib.request.urlopen("https://pypi.org/pypi/pytest-homeassistant-custom-component/json"))
for version in sorted(info["releases"], key=lambda v: [int(x) if x.isdigit() else 0 for x in v.split(".")], reverse=True):
    meta = json.load(urllib.request.urlopen(f"https://pypi.org/pypi/pytest-homeassistant-custom-component/{version}/json"))
    if any(r.replace(" ", "").startswith("homeassistant==" + want) for r in meta["info"].get("requires_dist") or []):
        print(version)
        break
PY
  )
  if [ -z "$harness" ]; then echo "::error::No test harness for Home Assistant $release on PyPI"; exit 1; fi
  echo "Test harness $harness"
  pip install -q "pytest-homeassistant-custom-component==$harness"
fi
# Her tools are called through the conversation integration, and each display is an Assist satellite with
# Home Assistant's timers: install what those load, at the versions this Home Assistant pins.
reqs=$(python "$repo/home-assistant/requirements.py" conversation assist_satellite intent)
echo "Installing: $reqs"
# shellcheck disable=SC2086 # one word per requirement
pip install -q $reqs
ha=$(python -c "import homeassistant.const as c; print(c.__version__)")
echo "Home Assistant $ha"

cd "$repo/home-assistant"
# Warnings are logged as they happen, so Home Assistant's notes about Lena using something deprecated (they
# name "custom integration 'lena'") can be picked out below.
if ! python -m pytest -q -p no:cacheprovider -W default -o log_cli=true --log-cli-level=WARNING 2>&1 | tee "$log"; then
  # The failures as one message on the run, so they can be read without opening the full log.
  msg=$(grep -E "^(FAILED|ERROR)|Error|assert |^E  " "$log" | head -80 | sed ':a;N;$!ba;s/%/%25/g;s/\n/%0A/g')
  echo "::error title=Tests failed (Home Assistant $ha)::${msg:-see the log}"
  exit 1
fi
echo "::notice title=Home Assistant $ha::$(tail -1 "$log")"
# Anything this Home Assistant says Lena should change before a later release, as a warning on the run (once each).
{ grep -iE "custom integration 'lena'|custom_components/lena.*(deprecat|will stop working|no longer)" "$log" || true; } \
  | sed 's/^.*\(Detected\|custom_components\)/\1/' | sort -u | head -20 \
  | while read -r line; do echo "::warning title=Home Assistant $ha says::$line"; done
