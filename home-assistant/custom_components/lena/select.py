"""Her Mood, Energy and Moment, as dropdowns you can watch, use in automations, or set by hand."""

from __future__ import annotations

from homeassistant.components.select import SelectEntity
from homeassistant.core import HomeAssistant, callback
from homeassistant.helpers.device_registry import DeviceEntryType, DeviceInfo
from homeassistant.helpers.dispatcher import async_dispatcher_connect
from homeassistant.helpers.entity_platform import AddConfigEntryEntitiesCallback

from .const import DOMAIN, NAME, SIGNAL_UPDATED
from .core import LenaCore


def device_info() -> DeviceInfo:
    return DeviceInfo(
        identifiers={(DOMAIN, "lena")},
        name=NAME,
        manufacturer="Lena",
        model="Personal AI assistant",
        entry_type=DeviceEntryType.SERVICE,
    )


async def async_setup_entry(hass: HomeAssistant, entry, async_add_entities: AddConfigEntryEntitiesCallback) -> None:
    core: LenaCore = entry.runtime_data
    async_add_entities([
        FeelingSelect(core, "mood", "Mood", "mdi:emoticon-outline"),
        FeelingSelect(core, "energy", "Energy", "mdi:battery-heart-variant"),
        FeelingSelect(core, "moment", "Moment", "mdi:lightning-bolt-outline"),
    ])


class FeelingSelect(SelectEntity):
    """select.lena_mood, select.lena_energy and select.lena_moment."""

    _attr_has_entity_name = True
    _attr_should_poll = False

    def __init__(self, core: LenaCore, kind: str, name: str, icon: str) -> None:
        self._core = core
        self._kind = kind
        self._attr_name = name
        self._attr_icon = icon
        self._attr_unique_id = f"lena_{kind}"
        self._attr_device_info = device_info()

    @property
    def options(self) -> list[str]:
        cat = self._core.catalog
        return {"mood": cat.mood_options, "energy": cat.energy_options}.get(self._kind, cat.moment_options)()

    @property
    def current_option(self) -> str:
        return getattr(self._core.feelings, self._kind)

    @property
    def extra_state_attributes(self) -> dict[str, str | None]:
        f = self._core.feelings
        if self._kind == "mood":
            return {"reason": f.mood_reason or None, "since": f.mood_since.isoformat() if f.mood_since else None}
        if self._kind == "moment":
            return {"reason": f.moment_reason or None, "since": f.moment_since.isoformat() if f.moment_since else None}
        return {}

    async def async_select_option(self, option: str) -> None:
        self._core.set_by_hand(self._kind, option)

    async def async_added_to_hass(self) -> None:
        self.async_on_remove(async_dispatcher_connect(self.hass, SIGNAL_UPDATED, self._updated))

    @callback
    def _updated(self) -> None:
        self.async_write_ha_state()
