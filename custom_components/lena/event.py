"""Each display's Announcement: what lena.announce sends it to say.

event.<display>_announcement fires with the message (and whether to listen afterwards); the Lena app
on that display watches it, says the message and, if asked, listens for an answer. It also shows in the
display's logbook, and can trigger automations like any event entity.
"""

from __future__ import annotations

from homeassistant.components.event import EventEntity
from homeassistant.core import HomeAssistant, callback
from homeassistant.helpers import device_registry as dr
from homeassistant.helpers.dispatcher import async_dispatcher_connect
from homeassistant.helpers.entity_platform import AddConfigEntryEntitiesCallback

from .const import DOMAIN
from .displays import SIGNAL_NEW_DISPLAY, display_id_of, display_info, displays

ANNOUNCE = "announce"
SIGNAL_ANNOUNCE = "lena_announce_{}"


async def async_setup_entry(hass: HomeAssistant, entry, async_add_entities: AddConfigEntryEntitiesCallback) -> None:
    """An Announcement for every display registered so far, and for each new one as it registers."""
    async_add_entities([AnnouncementEvent(d) for d in displays(hass, entry.entry_id)])

    @callback
    def added(device_id: str) -> None:
        """A display registered: give it its Announcement."""
        if device := dr.async_get(hass).async_get(device_id):
            async_add_entities([AnnouncementEvent(device)])

    entry.async_on_unload(async_dispatcher_connect(hass, SIGNAL_NEW_DISPLAY, added))


class AnnouncementEvent(EventEntity):
    """event.<display>_announcement: fires "announce" with message and listen."""

    _attr_has_entity_name = True
    _attr_translation_key = "announcement"
    _attr_event_types = [ANNOUNCE]
    _attr_should_poll = False

    def __init__(self, device: dr.DeviceEntry) -> None:
        """One display's Announcement, on that display's device."""
        display_id = display_id_of(device) or device.id
        self._device_id = device.id
        self._attr_unique_id = f"{display_id}_announcement"
        self._attr_device_info = display_info(display_id, device.name or "Lena display")

    async def async_added_to_hass(self) -> None:
        """Listens for lena.announce aimed at this display."""
        self.async_on_remove(async_dispatcher_connect(self.hass, SIGNAL_ANNOUNCE.format(self._device_id), self._announce))

    @callback
    def _announce(self, message: str, listen: bool) -> None:
        """Fires the announcement; the app sees the new event and speaks it."""
        self._trigger_event(ANNOUNCE, {"message": message, "listen": listen})
        self.async_write_ha_state()
