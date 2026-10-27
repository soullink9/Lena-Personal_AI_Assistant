"""Lena: her feelings, her memory and her tools for Home Assistant's voice assistants.

Sets up one Lena (there's only ever one), her Mood, Energy and Moment dropdowns, her memory
to-do list, the "Lena" choice under Control Home Assistant in a conversation agent, the
lena.feel action, and /api/lena/context for Open WebUI and the Lena app.
"""

from __future__ import annotations

from homeassistant.config_entries import ConfigEntry
from homeassistant.const import Platform
from homeassistant.core import HomeAssistant, ServiceCall, SupportsResponse
from homeassistant.exceptions import ServiceValidationError
from homeassistant.helpers import config_validation as cv, llm

from .compat import vol
from .const import DOMAIN
from .core import LenaCore, load_catalog
from .llm_api import LenaAPI
from .view import LenaContextView

PLATFORMS = [Platform.SELECT, Platform.TODO]

type LenaConfigEntry = ConfigEntry[LenaCore]


async def async_setup_entry(hass: HomeAssistant, entry: LenaConfigEntry) -> bool:
    catalog = await hass.async_add_executor_job(load_catalog)
    core = LenaCore(hass, entry, catalog)
    await core.async_start()
    entry.runtime_data = core
    hass.data[DOMAIN] = core

    entry.async_on_unload(core.async_stop)
    entry.async_on_unload(llm.async_register_api(hass, LenaAPI(hass=hass, id="lena", name="Lena")))

    if not hass.data.get(f"{DOMAIN}_view"):
        hass.http.register_view(LenaContextView())
        hass.data[f"{DOMAIN}_view"] = True

    async def feel(call: ServiceCall) -> None:
        current = hass.data.get(DOMAIN)
        moment = call.data["moment"]
        if current is None or not current.feel(moment, call.data.get("reason", "")):
            raise ServiceValidationError(f"{moment} isn't one of her moments in feelings.yaml")

    hass.services.async_register(
        DOMAIN, "feel", feel,
        schema=vol.Schema({vol.Required("moment"): cv.string, vol.Optional("reason", default=""): cv.string}),
        supports_response=SupportsResponse.NONE,
    )
    entry.async_on_unload(lambda: hass.services.async_remove(DOMAIN, "feel"))

    await hass.config_entries.async_forward_entry_setups(entry, PLATFORMS)
    return True


async def async_unload_entry(hass: HomeAssistant, entry: LenaConfigEntry) -> bool:
    unloaded = await hass.config_entries.async_unload_platforms(entry, PLATFORMS)
    if unloaded:
        hass.data.pop(DOMAIN, None)
    return unloaded

