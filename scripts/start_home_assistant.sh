#!/usr/bin/env bash
# Starts the newest Home Assistant on PyPI with Lena, even before its test harness is out: Lena must load, her
# context must answer over HTTP, and nothing it logs about her may be an error (home-assistant/startup_check.py).
#
#   scripts/start_home_assistant.sh
#
# Used by the workflows on Gitea and GitHub, like scripts/test_home_assistant.sh. It pip installs, so run it in a
# container or a virtualenv. Everything comes from PyPI.
set -euo pipefail
repo="$(cd "$(dirname "$0")/.." && pwd)"
work="$(mktemp -d)"
export PIP_ROOT_USER_ACTION=ignore PIP_DISABLE_PIP_VERSION_CHECK=1

pip install -q homeassistant
# Without its frontend Home Assistant starts in recovery mode, which leaves custom integrations out; her tools
# need the conversation integration, and each display is an Assist satellite. All at the versions it pins.
reqs=$(python "$repo/home-assistant/requirements.py" frontend conversation assist_satellite intent)
echo "Installing: $reqs"
# shellcheck disable=SC2086 # one word per requirement
pip install -q $reqs

ha=$(python -c "import homeassistant.const as c; print(c.__version__)")
if ! python "$repo/home-assistant/startup_check.py" "$work/config" 2>&1 | tee "$work/startup.log"; then
  echo "::error title=Home Assistant $ha::$(grep -E "^(ERROR|CRITICAL|Home Assistant|Her context)" "$work/startup.log" | head -20 | sed ':a;N;$!ba;s/%/%25/g;s/\n/%0A/g')"
  exit 1
fi
echo "::notice title=Home Assistant $ha::$(grep -E "^(Home Assistant|Her context)" "$work/startup.log" | sed ':a;N;$!ba;s/\n/ · /g')"
{ grep -E "^WARNING:" "$work/startup.log" || true; } | head -20 \
  | while read -r line; do echo "::warning title=Home Assistant $ha says::$line"; done
