"""Each Lena display as an Assist satellite: Home Assistant's own voice-assistant device, like a Home
Assistant Voice Preview Edition.

So Home Assistant's own voice features reach the tablets, rather than Lena copying them:

- Announcements: assist_satellite.announce (and Home Assistant's "broadcast" intent) say a message, or play
  a sound, on the display. Lena says the message in her own voice, after the pre-announcement chime.
- Start a conversation: assist_satellite.start_conversation says something and then listens; the answer
  continues that conversation in Assist.
- Timers: Home Assistant's timers ("set a 10 minute timer" in Assist) are kept by Home Assistant for the
  display it was asked through; the display shows them and rings when they're done.
- Wake word: the display's wake word can be chosen on the satellite's page, like any satellite's.

The display does its own listening (wake word and speech), so Home Assistant's own voice pipeline isn't
run for it; the app asks Assist through the conversation API with this device, which makes it room-aware.
"""

from __future__ import annotations

import asyncio
from typing import Any

from homeassistant.components.assist_satellite import (
    AssistSatelliteAnnouncement,
    AssistSatelliteConfiguration,
    AssistSatelliteEntity,
    AssistSatelliteEntityFeature,
    AssistSatelliteWakeWord,
)
from homeassistant.components.intent import TimerEventType, TimerInfo, async_register_timer_handler
from homeassistant.core import HomeAssistant, callback
from homeassistant.helpers import device_registry as dr
from homeassistant.helpers.dispatcher import async_dispatcher_connect
from homeassistant.helpers.entity_platform import AddConfigEntryEntitiesCallback

from .const import DOMAIN
from .displays import SIGNAL_NEW_DISPLAY, display_id_of, display_info, displays

# The app's wake words (its WakePhrase names), as a satellite's wake word choices.
WAKE_WORDS = {
    "HEY_LENA": "Hey Lena",
    "LENA": "Lena",
    "JARVIS": "Hey Jarvis",
    "OKAY_NABU": "Okay Nabu",
    "HEY_MYCROFT": "Hey Mycroft",
    "ALEXA": "Alexa",
    "HEY_HOME_ASSISTANT": "Hey Home Assistant",
}

COLLECT_SECONDS = 20  # how long an announcement waits for the display to pick it up
WORDS_PER_SECOND = 2.5  # roughly how fast she speaks, to wait until she's done


async def async_setup_entry(hass: HomeAssistant, entry, async_add_entities: AddConfigEntryEntitiesCallback) -> None:
    """A satellite for every display registered so far, and for each new one as it registers."""
    async_add_entities([LenaSatellite(d) for d in displays(hass, entry.entry_id)])

    @callback
    def added(device_id: str) -> None:
        if device := dr.async_get(hass).async_get(device_id):
            async_add_entities([LenaSatellite(device)])

    entry.async_on_unload(async_dispatcher_connect(hass, SIGNAL_NEW_DISPLAY, added))


class LenaSatellite(AssistSatelliteEntity):
    """One Lena display as an Assist satellite (assist_satellite.<display>)."""

    _attr_has_entity_name = True
    _attr_name = None
    _attr_supported_features = AssistSatelliteEntityFeature.ANNOUNCE | AssistSatelliteEntityFeature.START_CONVERSATION

    def __init__(self, device: dr.DeviceEntry) -> None:
        display_id = display_id_of(device) or device.id
        self._device_id = device.id
        self._attr_unique_id = f"{display_id}_satellite"
        self._attr_device_info = display_info(display_id, device.name or "Lena display")

    @property
    def _core(self):
        return self.hass.data[DOMAIN]

    def _device(self) -> dr.DeviceEntry:
        device = dr.async_get(self.hass).async_get(self._device_id)
        if device is None:
            raise RuntimeError("This display was removed")
        return device

    async def async_added_to_hass(self) -> None:
        """Home Assistant's timers asked through this display are shown and rung on it."""
        await super().async_added_to_hass()
        self.async_on_remove(async_register_timer_handler(self.hass, self._device_id, self._on_timer))

    # ---- Timers ------------------------------------------------------------------------------

    @callback
    def _on_timer(self, event: TimerEventType, timer: TimerInfo) -> None:
        """A Home Assistant timer for this display started, changed or was cancelled: the display keeps a
        copy that rings on time even if it loses its connection. (Finishing needs nothing: the copy rings.)
        """
        hub = self._core.hub
        device = [self._device()]
        if event == TimerEventType.STARTED:
            hub.send(device, "timer", {"duration": timer.seconds_left, "label": timer.name or "", "ha_id": timer.id})
        elif event == TimerEventType.UPDATED:
            hub.send(device, "timer_update", {"ha_id": timer.id, "seconds": timer.seconds_left, "active": timer.is_active})
        elif event == TimerEventType.CANCELLED:
            hub.send(device, "cancel", {"ha_id": timer.id, "kind": "timer", "label": "", "all": False})

    # ---- Announcements and conversations -----------------------------------------------------

    async def async_announce(self, announcement: AssistSatelliteAnnouncement) -> None:
        """Says the message (or plays the sound) on the display, and returns once it's likely done."""
        await self._say(announcement, listen=False)

    async def async_start_conversation(self, start_announcement: AssistSatelliteAnnouncement) -> None:
        """Says the message, then the display listens; the answer continues this conversation."""
        await self._say(start_announcement, listen=True)

    async def _say(self, announcement: AssistSatelliteAnnouncement, listen: bool) -> None:
        """Queues it for the display, waits for the display to pick it up, then for about as long as it
        takes to say (Home Assistant waits for an announcement to finish before the next).
        """
        data: dict[str, Any] = {
            "message": announcement.message,
            "listen": listen,
            "chime": bool(announcement.preannounce_media_id),
            # A sound to play rather than words (a doorbell, music): Home Assistant's own address for it.
            "media": announcement.media_id if announcement.media_id_source != "tts" or not announcement.message else "",
        }
        if listen and getattr(self, "_conversation_id", None):
            data["conversation_id"] = self._conversation_id
        (command,) = self._core.hub.send([self._device()], "announce", data)
        if not await self._core.hub.collected(command, COLLECT_SECONDS):
            raise TimeoutError("The display didn't pick up the announcement: is Lena on screen?")
        words = len(announcement.message.split())
        await asyncio.sleep(min(60.0, 1.5 + words / WORDS_PER_SECOND))

    @callback
    def on_pipeline_event(self, event) -> None:
        """The display runs its own listening, so Home Assistant's voice pipeline never runs here."""

    # ---- Wake word ---------------------------------------------------------------------------

    @callback
    def async_get_configuration(self) -> AssistSatelliteConfiguration:
        """The app's wake words, and the one it listens for (as it last reported)."""
        reported = self._core.hub.reported.get(self._device_id)
        active = reported.wake_word if reported else None
        return AssistSatelliteConfiguration(
            available_wake_words=[AssistSatelliteWakeWord(id=k, wake_word=v, trained_languages=["en"]) for k, v in WAKE_WORDS.items()],
            active_wake_words=[active] if active in WAKE_WORDS else [],
            max_active_wake_words=1,
        )

    async def async_set_configuration(self, config: AssistSatelliteConfiguration) -> None:
        """A wake word chosen on the satellite's page: the display listens for it (or for none)."""
        chosen = next((w for w in config.active_wake_words if w in WAKE_WORDS), None)
        self._core.hub.send([self._device()], "wake_word", {"phrase": chosen or ""})
