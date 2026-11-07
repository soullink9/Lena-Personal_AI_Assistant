"""Lena: her feelings, her memory and her tools for Home Assistant's voice assistants.

Sets up one Lena (there's only ever one), her Mood, Energy and Moment dropdowns, her memory
to-do list, the "Lena" choice under Control Home Assistant in a conversation agent, the
lena.feel, lena.add_event, lena.announce, lena.tidy_memory and lena.undo_memory_tidy actions and
the actions behind her newer tools (actions.py: timers, alarms, reminders, volume, correcting a memory,
the house's history), /api/lena/context for Open WebUI and the Lena app, /api/lena/media for the app's
photo slideshow, /api/lena/display, where each Lena app registers as a display you can put in a room
(see displays.py), and /api/lena/display/sync, where displays collect what they're asked to do (hub.py).
"""

from __future__ import annotations

from homeassistant.config_entries import ConfigEntry
from homeassistant.const import Platform
from homeassistant.core import HomeAssistant, ServiceCall, SupportsResponse
from homeassistant.exceptions import ServiceValidationError
from homeassistant.helpers import config_validation as cv, device_registry as dr, llm

from . import actions, events, tidy
from .compat import vol
from .const import DOMAIN
from .core import LenaCore, load_catalog
from .displays import LenaDisplayView, display_id_of, displays
from .briefing import Briefings, LenaBriefingView
from .hub import Hub, LenaSyncView, TargetError
from .llm_api import LenaAPI
from .view import LenaContextView, LenaMediaView

PLATFORMS = [Platform.EVENT, Platform.SELECT, Platform.TODO]

type LenaConfigEntry = ConfigEntry[LenaCore]


async def async_setup_entry(hass: HomeAssistant, entry: LenaConfigEntry) -> bool:
    """Starts Lena for this config entry: loads her feelings from feelings.yaml (in the executor, since
    it reads a file), starts the core that runs her day, then the entities, the views and the feel
    and add_event actions.
    """
    catalog = await hass.async_add_executor_job(load_catalog)
    core = LenaCore(hass, entry, catalog)
    core.hub = Hub(hass, entry.entry_id)
    core.briefings = Briefings(hass)
    await core.briefings.async_load()
    await core.async_start()
    entry.runtime_data = core
    hass.data[DOMAIN] = core

    entry.async_on_unload(core.async_stop)
    entry.async_on_unload(llm.async_register_api(hass, LenaAPI(hass=hass, id="lena", name="Lena")))

    if not hass.data.get(f"{DOMAIN}_view"):
        hass.http.register_view(LenaContextView())
        hass.http.register_view(LenaMediaView())
        hass.http.register_view(LenaDisplayView())
        hass.http.register_view(LenaSyncView())
        hass.http.register_view(LenaBriefingView())
        hass.data[f"{DOMAIN}_view"] = True

    async def feel(call: ServiceCall) -> None:
        """The lena.feel action: sets one of her moments by hand or from an automation. A name that
        isn't in feelings.yaml is an error the user sees, rather than a silent no-op.
        """
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

    async def add_event(call: ServiceCall) -> dict:
        """The lena.add_event action: what Open WebUI's Lena tool calls to put an event on a calendar,
        the same way her add_calendar_event tool does in Home Assistant. Returns what was added; a
        problem (an unknown calendar, a badly written time, the calendar refusing) is an error with
        a message she can act on.
        """
        try:
            return await events.add_event(
                hass, call.context, call.data.get("calendar"), call.data["title"], call.data["start"],
                call.data.get("end"), call.data.get("location"), call.data.get("description"),
            )
        except events.EventError as error:
            raise ServiceValidationError(str(error)) from error

    hass.services.async_register(
        DOMAIN, "add_event", add_event,
        schema=vol.Schema({
            vol.Optional("calendar"): cv.string,
            vol.Required("title"): cv.string,
            vol.Required("start"): cv.string,
            vol.Optional("end"): cv.string,
            vol.Optional("location"): cv.string,
            vol.Optional("description"): cv.string,
        }),
        supports_response=SupportsResponse.OPTIONAL,
    )
    entry.async_on_unload(lambda: hass.services.async_remove(DOMAIN, "add_event"))

    async def announce(call: ServiceCall) -> None:
        """The lena.announce action: says a message on the chosen displays, or the room's, or all of them
        when neither is chosen, like Home Assistant Voice's announcements. With listen, she then listens
        for an answer.
        """
        try:
            if call.data.get("device_id") or call.data.get("room"):
                devices = core.hub.targets(device_ids=call.data.get("device_id"), room=call.data.get("room"))
            else:
                devices = core.hub.targets(device_ids=[d.id for d in displays(hass, entry.entry_id)] or None)
        except TargetError as error:
            raise ServiceValidationError(str(error)) from error
        core.hub.send(devices, "announce", {"message": call.data["message"], "listen": call.data["listen"]})

    hass.services.async_register(
        DOMAIN, "announce", announce,
        schema=vol.Schema({
            vol.Optional("device_id"): vol.All(cv.ensure_list, [cv.string]),
            vol.Optional("room"): cv.string,
            vol.Required("message"): cv.string,
            vol.Optional("listen", default=False): cv.boolean,
        }),
        supports_response=SupportsResponse.NONE,
    )
    entry.async_on_unload(lambda: hass.services.async_remove(DOMAIN, "announce"))
    actions.register(hass, entry)

    async def tidy_memory(call: ServiceCall) -> dict:
        """The lena.tidy_memory action: she tidies her memory now (combining, clarifying, retiring) and
        says what changed. A problem (no AI Task, an answer that can't be used safely) is an error, and
        nothing changes.
        """
        try:
            result = await core.async_tidy_memory()
        except tidy.TidyError as error:
            raise ServiceValidationError(str(error)) from error
        if result is None:
            return {"changed": False, "summary": "There's too little in her memory to tidy."}
        return {"changed": result.changed, "summary": result.summary() or "Nothing needed changing."}

    hass.services.async_register(DOMAIN, "tidy_memory", tidy_memory, supports_response=SupportsResponse.OPTIONAL)
    entry.async_on_unload(lambda: hass.services.async_remove(DOMAIN, "tidy_memory"))

    async def undo_memory_tidy(call: ServiceCall) -> None:
        """The lena.undo_memory_tidy action: her memory as it was before her last tidy."""
        if not core.undo_memory_tidy():
            raise ServiceValidationError("There's no tidy to undo")

    hass.services.async_register(DOMAIN, "undo_memory_tidy", undo_memory_tidy, supports_response=SupportsResponse.NONE)
    entry.async_on_unload(lambda: hass.services.async_remove(DOMAIN, "undo_memory_tidy"))

    await hass.config_entries.async_forward_entry_setups(entry, PLATFORMS)
    return True


async def async_remove_config_entry_device(hass: HomeAssistant, entry: LenaConfigEntry, device: dr.DeviceEntry) -> bool:
    """A display can be deleted in Home Assistant (a tablet you no longer use); it registers again if
    its app is still set up. Lena herself can't be.
    """
    return display_id_of(device) is not None


async def async_unload_entry(hass: HomeAssistant, entry: LenaConfigEntry) -> bool:
    """Unloads the entities and forgets the core; the core's own timers and listeners stop through the
    entry's unload callbacks.
    """
    unloaded = await hass.config_entries.async_unload_platforms(entry, PLATFORMS)
    if unloaded:
        hass.data.pop(DOMAIN, None)
    return unloaded

