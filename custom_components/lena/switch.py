"""Do not disturb (switch.lena_do_not_disturb): while it's on, she doesn't speak first in the rooms.

Worries still reach the displays and the phones: that's what she speaks up for. It's an ordinary switch,
so Home Assistant's own automations can turn it on for a nap, a call or guests.
"""

from __future__ import annotations

from typing import Any

from homeassistant.components.switch import SwitchEntity
from homeassistant.core import HomeAssistant, callback
from homeassistant.helpers.dispatcher import async_dispatcher_connect
from homeassistant.helpers.entity_platform import AddConfigEntryEntitiesCallback

from .const import SIGNAL_UPDATED
from .core import LenaCore
from .select import device_info


async def async_setup_entry(hass: HomeAssistant, entry, async_add_entities: AddConfigEntryEntitiesCallback) -> None:
    """Her Do not disturb switch."""
    async_add_entities([DoNotDisturb(entry.runtime_data)])


class DoNotDisturb(SwitchEntity):
    """switch.lena_do_not_disturb, kept between restarts."""

    _attr_has_entity_name = True
    _attr_should_poll = False
    _attr_name = "Do not disturb"
    _attr_icon = "mdi:account-voice-off"
    _attr_unique_id = "lena_do_not_disturb"

    def __init__(self, core: LenaCore) -> None:
        self._core = core
        self._attr_device_info = device_info()

    @property
    def is_on(self) -> bool:
        return bool(self._core.presence.get("do_not_disturb"))

    async def async_turn_on(self, **kwargs: Any) -> None:
        self._core.presence["do_not_disturb"] = True
        self._core.presence_changed()

    async def async_turn_off(self, **kwargs: Any) -> None:
        self._core.presence["do_not_disturb"] = False
        self._core.presence_changed()

    async def async_added_to_hass(self) -> None:
        """Follow the core's changes for as long as the entity exists."""
        self.async_on_remove(async_dispatcher_connect(self.hass, SIGNAL_UPDATED, self._updated))

    @callback
    def async_entity_id_changed(self, old_entity_id: str) -> None:
        """Renaming the entity ID changes it in place: nothing here is keyed on it."""
        super().async_entity_id_changed(old_entity_id)

    @callback
    def _updated(self) -> None:
        self.async_write_ha_state()
