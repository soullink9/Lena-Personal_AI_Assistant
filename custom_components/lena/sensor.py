"""What she's said without being asked, and the routines she's learned, as sensors you can look at.

- Lena Spoke first (sensor.lena_spoke_first): how many times she spoke first today (worries not counted),
  with the last few as attributes: when, on which display, why (worry, welcome, morning, heading_out,
  follow_up, routine) and what she said. For tuning Configure > Speaking first.
- Lena Routines (sensor.lena_routines): how many of the habits she watches are regular, with what she
  believes about each ("around 7:05 AM on weekdays"), so a wrong guess can be spotted.
"""

from __future__ import annotations

from typing import Any

from homeassistant.components.sensor import SensorEntity
from homeassistant.core import HomeAssistant, callback
from homeassistant.helpers.dispatcher import async_dispatcher_connect
from homeassistant.helpers.entity_platform import AddConfigEntryEntitiesCallback
from homeassistant.util import dt as dt_util

from .const import SIGNAL_UPDATED
from .core import LenaCore
from .select import device_info


async def async_setup_entry(hass: HomeAssistant, entry, async_add_entities: AddConfigEntryEntitiesCallback) -> None:
    """Her two sensors."""
    core: LenaCore = entry.runtime_data
    async_add_entities([SpokeFirstSensor(core), RoutinesSensor(core)])


class _LenaSensor(SensorEntity):
    """One of her sensors: on her device, updated whenever the core changes something."""

    _attr_has_entity_name = True
    _attr_should_poll = False

    def __init__(self, core: LenaCore) -> None:
        self._core = core
        self._attr_device_info = device_info()

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


class SpokeFirstSensor(_LenaSensor):
    """sensor.lena_spoke_first."""

    _attr_name = "Spoke first"
    _attr_icon = "mdi:account-voice"
    _attr_unique_id = "lena_spoke_first"
    _attr_native_unit_of_measurement = "times"

    @property
    def native_value(self) -> int:
        """Times today (worries not counted)."""
        spoken = self._core.presence.get("spoken") or {}
        return int(spoken.get("count") or 0) if spoken.get("date") == dt_util.now().date().isoformat() else 0

    @property
    def extra_state_attributes(self) -> dict[str, Any]:
        """The last few times, newest first."""
        return {"recent": list((self._core.presence.get("spoken") or {}).get("log") or [])}


class RoutinesSensor(_LenaSensor):
    """sensor.lena_routines."""

    _attr_name = "Routines"
    _attr_icon = "mdi:calendar-clock"
    _attr_unique_id = "lena_routines"

    @property
    def native_value(self) -> int:
        """How many of the habits she watches are regular."""
        routines = self._core.routines
        return sum(1 for text in (routines.describe().values() if routines else []) if text != "not regular yet")

    @property
    def extra_state_attributes(self) -> dict[str, Any]:
        """What she believes about each one."""
        return self._core.routines.describe() if self._core.routines else {}
