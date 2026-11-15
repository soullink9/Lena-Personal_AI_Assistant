"""Starts a real Home Assistant, adds Lena the way the UI does, and reports what it says about her.

The tests (tests/) need Home Assistant's test harness, which is published a little after each Home
Assistant release. This check needs only Home Assistant itself, so it can try a release the day it comes
out: Lena must load, her context endpoint must answer, one of her Assist tools must answer the way this
Home Assistant expects, renaming one of her entities must keep it, and she must unload cleanly. Every warning
or error Home Assistant logs about her (including its notes on things she uses that are deprecated) is listed.

    python startup_check.py CONFIG_DIR

CONFIG_DIR gets a minimal configuration and a copy of custom_components/lena. Exits 1 if she didn't
load or Home Assistant logged an error about her. Used by .github/workflows/home-assistant.yml.
"""

from __future__ import annotations

import asyncio
import logging
import shutil
import sys
from datetime import timedelta
from pathlib import Path

import aiohttp
from homeassistant import bootstrap, runner
from homeassistant.auth.models import TOKEN_TYPE_LONG_LIVED_ACCESS_TOKEN
from homeassistant.config_entries import ConfigEntryState
from homeassistant.const import __version__
from homeassistant.helpers import entity_registry as er, llm
from homeassistant.helpers.entity import _entity_class_requires_readd
from homeassistant.helpers.entity_platform import async_get_platforms

REPO = Path(__file__).resolve().parent.parent

CONFIGURATION = """\
homeassistant:
  name: Lena start-up check
  latitude: 41.8
  longitude: -71.4
  elevation: 0
  unit_system: us_customary
  time_zone: America/New_York
http:
conversation:
"""


class AboutLena(logging.Handler):
    """Keeps every warning and error that mentions Lena."""

    def __init__(self) -> None:
        super().__init__(level=logging.WARNING)
        self.lines: list[tuple[str, str]] = []

    def emit(self, record: logging.LogRecord) -> None:
        text = record.getMessage()
        if "has not been tested by Home Assistant" in text:
            return  # said of every custom integration
        if "lena" in f"{record.name} {text}".lower():
            self.lines.append((record.levelname, f"{record.name}: {text}"))


async def check(config_dir: Path) -> int:
    """Starts Home Assistant, adds Lena, reads her context once, stops. Returns the exit code."""
    hass = await bootstrap.async_setup_hass(runner.RuntimeConfig(config_dir=str(config_dir)))
    if hass is None:
        print("Home Assistant didn't start")
        return 1
    collected = AboutLena()
    logging.getLogger().addHandler(collected)
    await hass.async_start()
    if hass.config.recovery_mode:
        # Something Home Assistant itself needs didn't load; custom integrations are off in this mode.
        print("Home Assistant started in recovery mode, so Lena couldn't be tried (see its log above)")
        await hass.async_stop()
        return 1

    # Adding Lena, as Settings → Devices & services → Add integration does: one empty form.
    result = await hass.config_entries.flow.async_init("lena", context={"source": "user"})
    if result["type"] == "form":
        result = await hass.config_entries.flow.async_configure(result["flow_id"], {})
    await hass.async_block_till_done()
    entries = hass.config_entries.async_entries("lena")
    loaded = bool(entries) and entries[0].state is ConfigEntryState.LOADED
    print(f"Home Assistant {__version__}: Lena {'loaded' if loaded else 'did not load'}"
          f" ({entries[0].state.value if entries else 'no entry'})")

    # Her context over HTTP with a long-lived token, exactly as Open WebUI and the app ask for it, so
    # Home Assistant's own sign-in and HTTP layers are part of the check.
    context_ok = False
    if loaded:
        user = await hass.auth.async_create_user("Lena start-up check")
        refresh = await hass.auth.async_create_refresh_token(
            user,
            client_name="Lena start-up check",
            token_type=TOKEN_TYPE_LONG_LIVED_ACCESS_TOKEN,
            access_token_expiration=timedelta(hours=1),
        )
        token = hass.auth.async_create_access_token(refresh)
        try:
            async with aiohttp.ClientSession() as session, session.get(
                "http://127.0.0.1:8123/api/lena/context", headers={"Authorization": f"Bearer {token}"}
            ) as response:
                body = await response.json()
                context_ok = response.status == 200 and "context" in body and "feelings" in body
                print(f"Her context: HTTP {response.status}, mood {body.get('feelings', {}).get('mood')!r}")
        except Exception as e:  # noqa: BLE001 - any failure is the finding
            print(f"Her context didn't answer: {e!r}")

    # One of her Assist tools, called as a conversation agent calls it: the answer must be a ToolResult.
    tool_ok = False
    if loaded:
        try:
            api = await llm.async_get_api(hass, "lena", llm.LLMContext(
                platform="startup_check", context=None, language="en", assistant="conversation", device_id=None,
            ))
            result = await api.async_call_tool(
                llm.ToolInput(tool_name="remember_fact", tool_args={"fact": "The start-up check ran."})
            )
            tool_ok = isinstance(result, llm.ToolResult) and not result.error
            print(f"Her tools: {len(api.tools)}, remember_fact answered {result!r}")
        except Exception as e:  # noqa: BLE001 - any failure is the finding
            print(f"Her tools didn't answer: {e!r}")

    # Renaming an entity ID (Settings → Entities) changes each of her entities in place, as Home Assistant
    # does for its own, rather than removing it and adding it again (the fallback ends in 2027.11).
    rename_ok = False
    if loaded:
        readded = sorted({
            type(entity).__name__
            for platform in async_get_platforms(hass, "lena")
            for entity in platform.entities.values()
            if _entity_class_requires_readd(type(entity))
        })
        er.async_get(hass).async_update_entity("select.lena_mood", new_entity_id="select.lena_mood_renamed")
        await hass.async_block_till_done()
        moved = hass.states.get("select.lena_mood") is None and hass.states.get("select.lena_mood_renamed") is not None
        rename_ok = moved and not readded
        print(f"Renaming her entity IDs: {'in place' if rename_ok else f'removed and added again ({readded})'}")

    # Unloading, as disabling or reloading her does.
    unload_ok = False
    if loaded:
        unload_ok = await hass.config_entries.async_unload(entries[0].entry_id)
        await hass.async_block_till_done()
        print(f"Unloading her: {entries[0].state.value}")

    await hass.async_stop()
    errors = 0
    for level, line in dict.fromkeys(collected.lines):
        print(f"{level}: {line}")
        errors += level in ("ERROR", "CRITICAL")
    return 0 if loaded and context_ok and tool_ok and rename_ok and unload_ok and errors == 0 else 1


def main() -> int:
    config_dir = Path(sys.argv[1]).resolve()
    config_dir.mkdir(parents=True, exist_ok=True)
    (config_dir / "configuration.yaml").write_text(CONFIGURATION)
    shutil.copytree(REPO / "custom_components", config_dir / "custom_components", dirs_exist_ok=True)
    return asyncio.run(check(config_dir))


if __name__ == "__main__":
    sys.exit(main())
