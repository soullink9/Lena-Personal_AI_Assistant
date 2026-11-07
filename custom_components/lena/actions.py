"""The lena.* actions behind her newer tools, for Open WebUI (her tools there call these over Home
Assistant's REST API) and for automations: timers, alarms, reminders, volume, the intercom, correcting a
memory, and the house's history. Her tools in Assist call the same code directly (llm_api.py).

device_id is the display she was asked through (the Lena app sends it to Open WebUI with each question);
room, when given, picks the room's display instead.
"""

from __future__ import annotations

from collections.abc import Awaitable, Callable
from typing import Any

from homeassistant.core import HomeAssistant, ServiceCall, SupportsResponse
from homeassistant.exceptions import ServiceValidationError
from homeassistant.helpers import config_validation as cv

from . import abilities, history
from .compat import vol
from .const import DOMAIN

TARGET = {
    vol.Optional("device_id"): vol.All(cv.ensure_list, [cv.string]),
    vol.Optional("room"): cv.string,
}


def register(hass: HomeAssistant, entry) -> None:
    """Registers the actions, removed again when Lena is unloaded."""
    core = entry.runtime_data

    def add(name: str, schema: dict, run: Callable[[dict[str, Any]], Any], response: SupportsResponse) -> None:
        async def handle(call: ServiceCall) -> dict | None:
            try:
                result = run(dict(call.data))
                if isinstance(result, Awaitable):
                    result = await result
            except (abilities.AbilityError, history.HistoryError) as error:
                raise ServiceValidationError(str(error)) from error
            return result if response != SupportsResponse.NONE else None

        hass.services.async_register(DOMAIN, name, handle, schema=vol.Schema(schema), supports_response=response)
        entry.async_on_unload(lambda: hass.services.async_remove(DOMAIN, name))

    def where(d: dict[str, Any]) -> dict[str, Any]:
        return {"device_id": d.get("device_id"), "room": d.get("room")}

    optional = SupportsResponse.OPTIONAL
    add("set_timer", {vol.Required("seconds"): vol.All(vol.Coerce(int), vol.Range(min=1)), vol.Optional("label"): cv.string, **TARGET},
        lambda d: abilities.set_timer(core, d["seconds"], d.get("label"), **where(d)), optional)
    add("set_alarm", {vol.Required("time"): cv.string, vol.Optional("label"): cv.string, **TARGET},
        lambda d: abilities.set_alarm(core, d["time"], d.get("label"), **where(d)), optional)
    add("set_reminder", {vol.Required("message"): cv.string, vol.Optional("at"): cv.string,
                         vol.Optional("in_seconds"): vol.All(vol.Coerce(int), vol.Range(min=1)), **TARGET},
        lambda d: abilities.set_reminder(core, d["message"], d.get("at"), d.get("in_seconds"), **where(d)), optional)
    add("cancel_timer", {vol.Optional("kind"): cv.string, vol.Optional("label"): cv.string,
                         vol.Optional("all", default=False): cv.boolean, **TARGET},
        lambda d: abilities.cancel(core, d.get("kind"), d.get("label"), d["all"], **where(d)), optional)
    add("list_timers", {vol.Optional("everywhere", default=False): cv.boolean, **TARGET},
        lambda d: abilities.list_countdowns(core, everywhere=d["everywhere"], **where(d)), SupportsResponse.ONLY)
    add("set_volume", {vol.Optional("level"): vol.All(vol.Coerce(int), vol.Range(min=0, max=100)),
                       vol.Optional("change"): vol.In(["quieter", "louder"]), **TARGET},
        lambda d: abilities.set_volume(core, d.get("level"), d.get("change"), **where(d)), optional)
    add("update_fact", {vol.Required("old"): cv.string, vol.Required("new"): cv.string},
        lambda d: abilities.update_fact(core, d["old"], d["new"]), optional)
    add("history", {vol.Required("question"): vol.In(["last_change", "how_long", "what_happened"]),
                    vol.Optional("name"): cv.string, vol.Optional("state"): cv.string,
                    vol.Optional("hours"): vol.All(vol.Coerce(float), vol.Range(min=0.1, max=24 * 30))},
        lambda d: ask_history(hass, d), SupportsResponse.ONLY)


async def ask_history(hass: HomeAssistant, d: dict[str, Any]) -> dict[str, Any]:
    """One question about the house's history, by kind."""
    question = d["question"]
    if question == "what_happened":
        return await history.what_happened(hass, d.get("hours"))
    if not d.get("name"):
        raise history.HistoryError("Say what to look up (a door, a lock, a person)")
    if question == "last_change":
        days = int(d["hours"] // 24) if d.get("hours") else 7
        return await history.last_change(hass, d["name"], d.get("state"), max(1, days))
    return await history.how_long(hass, d["name"], d.get("state"), d.get("hours"))
