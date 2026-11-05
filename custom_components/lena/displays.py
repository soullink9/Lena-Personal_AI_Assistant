"""Lena's displays: each tablet or phone running the Lena app, as a device in Home Assistant.

The app registers itself (POST /api/lena/display) and becomes a device under Lena, which you put in a
room the usual way (Settings > Devices & services > Lena > the display > Area). From then on:

- Assist knows where it's being spoken to: the app sends its device with each question, so "turn on the
  lights" means that room's, as on a Home Assistant Voice satellite.
- The app learns its room and the speakers in it, so music asked for without a room plays there, and
  Lena is told which room she's in when Open WebUI answers.
- The lena.announce action speaks a message on one display, several, or all of them, through each
  display's Announcement event (event.<display>_announcement), which the app watches.
"""

from __future__ import annotations

import asyncio
from typing import Any

from aiohttp import web

from homeassistant.core import HomeAssistant, callback
from homeassistant.helpers import area_registry as ar, device_registry as dr, entity_registry as er
from homeassistant.helpers.device_registry import DeviceInfo
from homeassistant.helpers.dispatcher import async_dispatcher_send
from homeassistant.helpers.http import KEY_HASS, HomeAssistantView

from .const import DOMAIN

# Sent with a display's device id when a new display registers, so its Announcement entity is added.
SIGNAL_NEW_DISPLAY = "lena_new_display"

# A display's identifier in the device registry: (lena, "display:<the app's own id>").
DISPLAY_PREFIX = "display:"

MAX_ID = 64
MAX_NAME = 60


def display_info(display_id: str, name: str) -> DeviceInfo:
    """A display's device: under Lena, named after the tablet or phone."""
    return DeviceInfo(
        identifiers={(DOMAIN, DISPLAY_PREFIX + display_id)},
        name=name,
        manufacturer="Lena",
        model="Lena app",
    )


def display_id_of(device: dr.DeviceEntry) -> str | None:
    """The app's own id for a display device, or None for any other device (Lena herself)."""
    for domain, ident in device.identifiers:
        if domain == DOMAIN and ident.startswith(DISPLAY_PREFIX):
            return ident.removeprefix(DISPLAY_PREFIX)
    return None


@callback
def displays(hass: HomeAssistant, entry_id: str) -> list[dr.DeviceEntry]:
    """Every registered display, as devices."""
    return [d for d in dr.async_entries_for_config_entry(dr.async_get(hass), entry_id) if display_id_of(d)]


@callback
def speakers(hass: HomeAssistant, area_id: str | None) -> list[dict[str, Any]]:
    """The media players in a room (by their own area, or their device's), Music Assistant's first,
    since those can play music by name.
    """
    if not area_id:
        return []
    entities, devices = er.async_get(hass), dr.async_get(hass)
    found = []
    for entity in entities.entities.values():
        if entity.domain != "media_player" or entity.disabled:
            continue
        area = entity.area_id
        if area is None and entity.device_id and (device := devices.async_get(entity.device_id)):
            area = device.area_id
        if area != area_id:
            continue
        state = hass.states.get(entity.entity_id)
        found.append({
            "entity_id": entity.entity_id,
            "name": (state.name if state else None) or entity.name or entity.original_name or entity.entity_id,
            "music_assistant": entity.platform == "music_assistant",
        })
    return sorted(found, key=lambda s: (not s["music_assistant"], s["name"].lower()))


@callback
def describe(hass: HomeAssistant, device: dr.DeviceEntry) -> dict[str, Any]:
    """What the app needs to know about its display: its device, its room and that room's speakers, and
    its Announcement entity.
    """
    area = ar.async_get(hass).async_get_area(device.area_id) if device.area_id else None
    announce = next(
        (e.entity_id for e in er.async_entries_for_device(er.async_get(hass), device.id) if e.domain == "event"),
        None,
    )
    return {
        "device_id": device.id,
        "name": device.name_by_user or device.name,
        "area": {"id": area.id, "name": area.name} if area else None,
        "speakers": speakers(hass, device.area_id),
        "announce_entity": announce,
    }


class LenaDisplayView(HomeAssistantView):
    """POST /api/lena/display {"id": "<the app's own id>", "name": "Kitchen tablet"}: registers the app
    as a display (once; afterwards it only reads), and answers with describe(). Any signed-in user may
    call it, the same token the app already uses.
    """

    url = "/api/lena/display"
    name = "api:lena:display"
    requires_auth = True

    async def post(self, request: web.Request) -> web.Response:
        """Registers or looks up the display and says where it is."""
        hass = request.app[KEY_HASS]
        core = hass.data.get(DOMAIN)
        if core is None:
            return self.json_message("Lena isn't set up", status_code=404)
        try:
            body = await request.json()
        except ValueError:
            return self.json_message("Expected JSON", status_code=400)
        display_id = str(body.get("id") or "").strip()
        name = str(body.get("name") or "").strip()[:MAX_NAME] or "Lena display"
        if not display_id or len(display_id) > MAX_ID or not all(ch.isalnum() or ch == "-" for ch in display_id):
            return self.json_message("A display needs an id of letters, digits and dashes", status_code=400)

        registry = dr.async_get(hass)
        # Looked up among Lena's own devices: Home Assistant's lookups by identifier have changed between
        # versions.
        existing = next((d for d in displays(hass, core.entry.entry_id) if display_id_of(d) == display_id), None)
        device = registry.async_get_or_create(config_entry_id=core.entry.entry_id, **display_info(display_id, name))
        if existing is None:
            async_dispatcher_send(hass, SIGNAL_NEW_DISPLAY, device.id)
            # Its Announcement entity is added a moment later; wait briefly so the answer can name it.
            for _ in range(40):
                if any(e.domain == "event" for e in er.async_entries_for_device(er.async_get(hass), device.id)):
                    break
                await asyncio.sleep(0.05)
        return self.json(describe(hass, registry.async_get(device.id) or device))
