"""Around the house: the small things a person living here would know.

- Goodnight: in the evening she knows what's still open or unlocked (Configure: Check these when everyone
  leaves) and which lights are on, so "goodnight" gets "the back door's still unlocked, shall I lock it?".
- Celebrations: today's special days (the special days calendars and the holidays she knows, such as
  Christmas), greeted once per person; a display can say it first, and run a script or scene of yours.
- The seasons: the solstices and equinoxes, the clocks changing, and the first frost and first snow of the
  winter (from your own weather station, or the weather entity).
- Curiosity: now and then she asks one getting-to-know-you question, about whoever she knows least.
- Music for the moment, on a Music Assistant speaker in the room.
- The shopping list: she keeps it, and when someone's phone arrives at a shop (Configure: Shops) it gets
  the list.
- The house's upkeep: batteries and filters running low, mentioned only if asked, or once a week first.
- The photo on the wall: what the display's slideshow is showing (from Immich: when, where, who).
- Nicknames: "the big light" saved as an alias of the device in Home Assistant, so it works everywhere.

What she's said and when is kept in .storage (lena.around), so a restart doesn't repeat anything.
"""

from __future__ import annotations

import asyncio
from datetime import date, datetime, timedelta
import logging
import re
from typing import Any

from homeassistant.components.homeassistant.exposed_entities import async_should_expose
from homeassistant.const import STATE_UNAVAILABLE, STATE_UNKNOWN
from homeassistant.core import CALLBACK_TYPE, Event, EventStateChangedData, HomeAssistant, callback
from homeassistant.exceptions import ServiceValidationError
from homeassistant.helpers import area_registry as ar, device_registry as dr, entity_registry as er
from homeassistant.helpers.aiohttp_client import async_get_clientsession
from homeassistant.helpers.event import async_track_state_change_event, async_track_time_change
from homeassistant.helpers.storage import Store
from homeassistant.util import dt as dt_util, slugify

from . import brain
from .const import (
    CONF_CALENDARS,
    CONF_CELEBRATE,
    CONF_CURIOUS,
    CONF_GOODNIGHT,
    CONF_LOCAL_WEATHER,
    CONF_SHOPPING_LIST,
    CONF_SHOPS,
    CONF_UPKEEP,
    DOMAIN,
    OPEN_LENA,
)
from .displays import speakers

_LOGGER = logging.getLogger(__name__)

SHOP_AGAIN = timedelta(hours=2)  # a second arrival at a shop this soon gets no second list
PHOTO_CACHE = 64  # photos whose details are kept
_IMMICH = re.compile(r"^/immich/([^/]+)/([^/]+)/")


class AroundTheHouse:
    """Goodnight, celebrations, seasons, curiosity, music, shopping, upkeep and the photo on the wall."""

    def __init__(self, hass: HomeAssistant, core) -> None:
        self.hass = hass
        self.core = core
        # firsts: {"frost"|"snow": {"winter", "date"}}; greeted: {"date", "who": [...]}; curious: {person: date};
        # upkeep: {"last": date, "critical": [entity ids]}; shops: {person: iso}; said: {kind: date}
        self.data: dict[str, Any] = {}
        self.special: tuple[str, list[str]] = ("", [])  # (date, today's special days)
        self.photos: dict[str, dict[str, Any] | None] = {}  # display device id -> what its photo shows
        self._shown: dict[str, str] = {}  # display device id -> the photo's address
        self._photo_info: dict[str, dict[str, Any] | None] = {}  # address -> details, newest last
        self._store: Store[dict[str, Any]] = Store(hass, 1, f"{DOMAIN}.around")
        self._unsubs: list[CALLBACK_TYPE] = []

    # ---- Starting and stopping -------------------------------------------------------------

    async def async_start(self) -> None:
        """Loads what's been said, learns today's special days, and starts watching the weather (for the
        first frost and snow) and the people (for arriving at a shop).
        """
        saved = await self._store.async_load() or {}
        self.data = {key: saved.get(key) or {} for key in ("firsts", "greeted", "curious", "upkeep", "shops", "said")}
        hass = self.hass
        self._unsubs += [
            async_track_time_change(hass, self._on_hour, minute=12, second=0),
            async_track_time_change(hass, self._on_midnight, hour=0, minute=0, second=30),
        ]
        if self.core.opt(CONF_SHOPPING_LIST) and self.core.opt(CONF_SHOPS):
            people = [s.entity_id for s in hass.states.async_all("person")]
            if people:
                self._unsubs.append(async_track_state_change_event(hass, people, self._on_person))
        hass.async_create_task(self.async_refresh_special())

    @callback
    def async_stop(self) -> None:
        for unsub in self._unsubs:
            unsub()
        self._unsubs.clear()

    def _save(self) -> None:
        self._store.async_delay_save(lambda: self.data, 2)

    def said(self, kind: str) -> str | None:
        """The day she last said something of this kind first (celebration, season, upkeep)."""
        return self.data["said"].get(kind)

    def note_said(self, kind: str, day: date) -> None:
        self.data["said"][kind] = day.isoformat()
        self._save()

    # ---- Her context -------------------------------------------------------------------------

    def context(self, person: str | None, device_id: str | None, talking: bool) -> list[str]:
        """Her context's parts from around the house: a Goodnight check in the evening, and a few lines of
        what she knows today. [talking] when it's for a message someone just sent, which counts as having
        greeted them, and as having had the chance to ask a question.
        """
        now = dt_util.now()
        today = now.date()
        parts = []
        if self.core.opt(CONF_GOODNIGHT):
            wake, bed = self.core._wake_bed()
            if brain.goodnight_time(now, wake, bed):
                parts.append(brain.goodnight_section(self.core.left_open(), self.lights_on()))
        lines = []
        special = self.special_days()
        who = person or (f"display:{device_id}" if device_id else "anyone")
        greeted = self.data["greeted"]
        if greeted.get("date") != today.isoformat():
            greeted.clear()
            greeted.update({"date": today.isoformat(), "who": []})
        if line := brain.celebration_line(special, who not in greeted["who"]):
            lines.append(line)
            if talking and who not in greeted["who"]:
                greeted["who"].append(who)
                self._save()
        if line := brain.seasonal_line(self.season_today(today)):
            lines.append(line)
        if person and self.core.opt(CONF_CURIOUS) and brain.curious_due(self.data["curious"].get(person), today):
            lines.append(brain.curious_line(person, brain.curious_subject(person, self.core.names(), self.core.memory)))
            if talking:
                self.data["curious"][person] = today.isoformat()
                self._save()
        lines.append(brain.NICKNAME_LINE)
        if line := self.music(device_id):
            lines.append(line)
        if (todo := self.core.opt(CONF_SHOPPING_LIST)) and (state := self.hass.states.get(todo)):
            lines.append(brain.shopping_line(state.name))
        if self.core.opt(CONF_UPKEEP) and (line := brain.upkeep_line(self.low())):
            lines.append(line)
        if device_id and (line := brain.photo_line(self.photos.get(device_id), today)):
            lines.append(line)
        if lines:
            parts.append("## Around the house\n" + "\n".join(lines))
        return parts

    # ---- Goodnight ---------------------------------------------------------------------------

    def lights_on(self) -> dict[str, list[str]]:
        """The lights that are on, among those exposed to Assist, by room ("" for no room)."""
        entities, devices, areas = er.async_get(self.hass), dr.async_get(self.hass), ar.async_get(self.hass)
        found: dict[str, list[str]] = {}
        for state in self.hass.states.async_all("light"):
            if state.state != "on":
                continue
            try:
                if not async_should_expose(self.hass, "conversation", state.entity_id):
                    continue
            except KeyError:  # Home Assistant's exposure settings aren't loaded
                return {}
            entry = entities.async_get(state.entity_id)
            area_id = entry.area_id if entry else None
            if area_id is None and entry is not None and entry.device_id and (device := devices.async_get(entry.device_id)):
                area_id = device.area_id
            area = areas.async_get_area(area_id) if area_id else None
            found.setdefault(area.name if area else "", []).append(state.name)
        return found

    # ---- Special days ------------------------------------------------------------------------

    def special_days(self) -> list[str]:
        """Today's special days, as last read (each midnight and morning)."""
        day, names = self.special
        today = dt_util.now().date()
        if day == today.isoformat():
            return names
        return brain.holidays(today, self.hass.config.country)

    async def async_refresh_special(self) -> list[str]:
        """Reads today's events from the special days calendars (Configure) and adds the holidays she knows."""
        found: list[str] = []
        calendars = list(self.core.opt(CONF_CALENDARS) or [])
        start = dt_util.start_of_local_day()
        if calendars:
            try:
                async with asyncio.timeout(15):
                    reply = await self.hass.services.async_call(
                        "calendar", "get_events",
                        {"entity_id": calendars, "start_date_time": start, "end_date_time": start + timedelta(days=1)},
                        blocking=True, return_response=True,
                    )
                for cal in (reply or {}).values():
                    for event in cal.get("events", []):
                        if (summary := event.get("summary")) and summary not in found:
                            found.append(summary)
            except Exception:  # noqa: BLE001 - a calendar problem shouldn't stop her day
                _LOGGER.warning("Couldn't read the special days calendars", exc_info=True)
        names = brain.merge_special(found, brain.holidays(start.date(), self.hass.config.country))
        self.special = (start.date().isoformat(), names)
        return names

    async def _on_midnight(self, now: datetime) -> None:
        await self.async_refresh_special()

    async def celebrate(self) -> None:
        """Runs the script or scene chosen to celebrate with (Configure), if one is."""
        if target := self.core.opt(CONF_CELEBRATE):
            try:
                await self.hass.services.async_call("homeassistant", "turn_on", {"entity_id": target}, blocking=False)
            except Exception:  # noqa: BLE001 - a celebration without its lights is still one
                _LOGGER.warning("Couldn't run %s to celebrate", target, exc_info=True)

    # ---- The seasons ---------------------------------------------------------------------------

    @property
    def southern(self) -> bool:
        return (self.hass.config.latitude or 0) < 0

    def season_today(self, today: date) -> list[str]:
        """What's seasonal about today: an equinox or solstice, the clocks, or the winter's first frost or snow."""
        found = brain.season_days(today, dt_util.get_default_time_zone(), self.southern)
        for kind, words in brain.FIRSTS.items():
            if (self.data["firsts"].get(kind) or {}).get("date") == today.isoformat():
                found.append(words)
        return found

    @callback
    def _on_hour(self, now: datetime) -> None:
        """Every hour: is it the winter's first frost, or first snow?"""
        today = dt_util.now().date()
        winter = brain.winter_of(today, self.southern)
        firsts = self.data["firsts"]
        if (firsts.get("frost") or {}).get("winter") != winter and self._freezing():
            firsts["frost"] = {"winter": winter, "date": today.isoformat()}
            self._save()
        weather = self.core.weather_entity
        state = self.hass.states.get(weather) if weather else None
        if state is not None and state.state in brain.SNOWY and (firsts.get("snow") or {}).get("winter") != winter:
            firsts["snow"] = {"winter": winter, "date": today.isoformat()}
            self._save()

    def _freezing(self) -> bool:
        """Whether it's freezing outside: your own weather station's temperature first, else the weather entity's."""
        for entity_id in self.core.opt(CONF_LOCAL_WEATHER) or []:
            state = self.hass.states.get(entity_id)
            unit = state.attributes.get("unit_of_measurement") if state else None
            if state is None or not unit or unit not in ("°C", "°F"):
                continue
            try:
                return brain.freezing(float(state.state), unit)
            except ValueError:
                continue
        state = self.hass.states.get(self.core.weather_entity) if self.core.weather_entity else None
        if state is not None and isinstance(temp := state.attributes.get("temperature"), (int, float)):
            return brain.freezing(float(temp), state.attributes.get("temperature_unit"))
        return False

    # ---- Music ---------------------------------------------------------------------------------

    def music(self, device_id: str | None) -> str:
        """How to pick music for the moment, when there's a Music Assistant speaker to play it on."""
        area_id, room = self.core.area_of(device_id)
        if area_id and any(s["music_assistant"] for s in speakers(self.hass, area_id)):
            return brain.music_line(room)
        entities = er.async_get(self.hass)
        if any((e := entities.async_get(s.entity_id)) and e.platform == "music_assistant"
               for s in self.hass.states.async_all("media_player")):
            return brain.music_line(None)
        return ""

    # ---- The shopping list -------------------------------------------------------------------

    @callback
    def _on_person(self, event: Event[EventStateChangedData]) -> None:
        """Someone's phone arrived somewhere: if it's one of the shops, it gets the list."""
        new, old = event.data["new_state"], event.data["old_state"]
        if new is None or old is None or new.state == old.state:
            return
        shops = {
            state.name: zone for zone in self.core.opt(CONF_SHOPS) or []
            if (state := self.hass.states.get(zone)) is not None
        }
        if new.state not in shops:
            return
        last = dt_util.parse_datetime(self.data["shops"].get(new.entity_id) or "")
        if last is not None and dt_util.now() - last < SHOP_AGAIN:
            return
        self.data["shops"][new.entity_id] = dt_util.now().isoformat()
        self._save()
        self.hass.async_create_task(self.send_list(new.entity_id, new.state))

    async def send_list(self, person: str, shop: str) -> bool:
        """Sends the open items on the shopping list to [person]'s phones, as from Lena. False when there's
        nothing on it, or no phone to send to.
        """
        todo = self.core.opt(CONF_SHOPPING_LIST)
        try:
            async with asyncio.timeout(15):
                reply = await self.hass.services.async_call(
                    "todo", "get_items", {"entity_id": todo, "status": "needs_action"},
                    blocking=True, return_response=True,
                )
        except Exception:  # noqa: BLE001 - no list, no message
            _LOGGER.warning("Couldn't read the shopping list %s", todo, exc_info=True)
            return False
        items = [i["summary"] for i in ((reply or {}).get(todo) or {}).get("items", []) if i.get("summary")]
        phones = self.phones_of(person)
        if not items or not phones:
            return False
        message = brain.shopping_message(shop, items)
        for phone in phones:
            await self.hass.services.async_call("notify", phone, {
                "title": "Lena", "message": message,
                "data": {"tag": "lena-shopping", "clickAction": OPEN_LENA},
            }, blocking=False)
        return True

    def phones_of(self, person: str) -> list[str]:
        """The companion app notify actions of a person's phones (their device trackers' devices)."""
        state = self.hass.states.get(person)
        entities, devices = er.async_get(self.hass), dr.async_get(self.hass)
        found = []
        for tracker in (state.attributes.get("device_trackers") or []) if state else []:
            entry = entities.async_get(tracker)
            device = devices.async_get(entry.device_id) if entry and entry.device_id else None
            if device is None or entry.platform != "mobile_app":
                continue
            service = f"mobile_app_{slugify(device.name or '')}"
            if self.hass.services.has_service("notify", service) and service not in found:
                found.append(service)
        return found

    # ---- The house's upkeep ------------------------------------------------------------------

    def readings(self) -> list[dict[str, Any]]:
        """Every battery and filter in the house: {"id", "name", "kind", "level"} (level None for a battery
        that only says it's low). Phones and tablets are left out: they're charged, not changed.
        """
        entities = er.async_get(self.hass)
        out = []
        for state in self.hass.states.async_all(("sensor", "binary_sensor")):
            if state.state in (STATE_UNAVAILABLE, STATE_UNKNOWN):
                continue
            entry = entities.async_get(state.entity_id)
            if entry is not None and entry.platform in ("mobile_app", DOMAIN):
                continue
            device_class = state.attributes.get("device_class")
            if state.domain == "binary_sensor":
                if device_class == "battery" and state.state == "on":
                    out.append({"id": state.entity_id, "name": _thing(state.name), "kind": "battery", "level": None})
                continue
            if state.attributes.get("unit_of_measurement") != "%":
                continue
            kind = "battery" if device_class == "battery" else "filter" if "filter" in (state.entity_id + state.name).lower() else None
            if kind is None:
                continue
            try:
                level = float(state.state)
            except ValueError:
                continue
            out.append({"id": state.entity_id, "name": _thing(state.name), "kind": kind, "level": level})
        return out

    def low(self) -> list[dict[str, Any]]:
        """What's running low, lowest first."""
        return brain.upkeep_low(self.readings())

    def upkeep_to_mention(self, today: date) -> list[dict[str, Any]]:
        """What to bring up first, or [] (brain.upkeep_due)."""
        upkeep = self.data["upkeep"]
        return brain.upkeep_due(upkeep.get("last"), today, self.low(), list(upkeep.get("critical") or []))

    def upkeep_mentioned(self, low: list[dict[str, Any]], today: date) -> None:
        upkeep = self.data["upkeep"]
        upkeep["last"] = today.isoformat()
        upkeep["critical"] = [r["id"] for r in low if brain.upkeep_critical(r)]
        self._save()

    # ---- The photo on the wall ---------------------------------------------------------------

    @callback
    def photo_shown(self, device_id: str, photo: str | None) -> None:
        """A display reported the photo its slideshow is showing (its address on Home Assistant)."""
        if not photo:
            self._shown.pop(device_id, None)
            self.photos.pop(device_id, None)
            return
        if self._shown.get(device_id) == photo:
            return
        self._shown[device_id] = photo
        if photo in self._photo_info:
            self.photos[device_id] = self._photo_info[photo]
            return
        self.photos.pop(device_id, None)
        self.hass.async_create_task(self._learn_photo(device_id, photo))

    async def _learn_photo(self, device_id: str, photo: str) -> None:
        info = await self.photo_details(photo)
        self._photo_info[photo] = info
        while len(self._photo_info) > PHOTO_CACHE:
            self._photo_info.pop(next(iter(self._photo_info)))
        if self._shown.get(device_id) == photo:
            self.photos[device_id] = info

    async def photo_details(self, photo: str) -> dict[str, Any] | None:
        """When, where and who an Immich photo shows, from Immich itself (through the Immich integration's
        own connection details). None for other photos, or when Immich can't be reached.
        """
        if not (m := _IMMICH.match(photo)):
            return None
        account, asset = m.group(1), m.group(2)
        entry = self.hass.config_entries.async_entry_for_domain_unique_id("immich", account)
        if entry is None:
            return None
        data = entry.data
        url = f"{'https' if data.get('ssl') else 'http'}://{data.get('host')}:{data.get('port')}/api/assets/{asset}"
        try:
            session = async_get_clientsession(self.hass, bool(data.get("verify_ssl", True)))
            async with asyncio.timeout(10):
                async with session.get(url, headers={"x-api-key": data.get("api_key", ""), "Accept": "application/json"}) as reply:
                    reply.raise_for_status()
                    asset_info = await reply.json()
        except Exception:  # noqa: BLE001 - the photo just goes unexplained
            _LOGGER.debug("Couldn't read the photo's details from Immich", exc_info=True)
            return None
        exif = asset_info.get("exifInfo") or {}
        place = ", ".join(p for p in (exif.get("city"), exif.get("state") or exif.get("country")) if p)
        people = [p.get("name") for p in asset_info.get("people") or [] if isinstance(p, dict) and p.get("name")]
        return {
            "taken": str(asset_info.get("localDateTime") or exif.get("dateTimeOriginal") or asset_info.get("fileCreatedAt") or "")[:10],
            "place": place,
            "people": people,
            "description": (exif.get("description") or "").strip()[:200],
        }

    # ---- Nicknames ---------------------------------------------------------------------------

    @callback
    def add_nickname(self, thing: str, nickname: str) -> str:
        """Saves [nickname] as another name for a device or room exposed to Assist, so "the big light" works
        everywhere, Home Assistant's own Assist included. Returns the thing's name. Raises when it isn't one
        she can see, the nickname is already another thing's name, or it's blank.
        """
        nickname = nickname.strip().strip('"').strip()
        if not nickname or len(nickname) > 60:
            raise ServiceValidationError("Give a short nickname, like \"the big light\".")
        wanted = _plain(thing)
        if not wanted:
            raise ServiceValidationError("Say which device or room it's a nickname for, by its name.")
        entities, areas = er.async_get(self.hass), ar.async_get(self.hass)
        exposed = []
        for state in self.hass.states.async_all():
            try:
                if async_should_expose(self.hass, "conversation", state.entity_id):
                    exposed.append(state)
            except KeyError:
                raise ServiceValidationError("Home Assistant's Assist settings aren't loaded yet; try again in a minute.") from None
        taken = {_plain(s.name) for s in exposed} | {_plain(a.name) for a in areas.async_list_areas()}
        if _plain(nickname) in taken:
            raise ServiceValidationError(f"\"{nickname}\" is already the name of something here.")
        # Its exact name first (a device, then a room), then a device whose name contains it.
        matches = [s for s in exposed if _plain(s.name) == wanted]
        area = next((a for a in areas.async_list_areas() if _plain(a.name) == wanted), None)
        if not matches and area is None:
            matches = [s for s in exposed if wanted in _plain(s.name)]
        if len(matches) == 1 and (entry := entities.async_get(matches[0].entity_id)) is not None:
            # The registry's own collection, kept as it is (its kind and order), with the nickname added.
            if nickname not in entry.aliases:
                entities.async_update_entity(entry.entity_id, aliases=type(entry.aliases)([*entry.aliases, nickname]))
            return matches[0].name
        if area is not None and not matches:
            if nickname not in area.aliases:
                areas.async_update(area.id, aliases=type(area.aliases)([*area.aliases, nickname]))
            return area.name
        if len(matches) == 1:
            raise ServiceValidationError(f"{matches[0].name} can't take a nickname: Home Assistant can't rename it (it has no unique ID).")
        if len(matches) > 1:
            raise ServiceValidationError("That matches several things: " + ", ".join(s.name for s in matches[:6])
                                     + ". Ask which, and use its exact name.")
        raise ServiceValidationError(f"Nothing exposed to Assist is called \"{thing}\". Use the exact name from Your home.")


def _plain(text: str) -> str:
    """A name compared loosely: lower case, without "the" or extra spaces."""
    return re.sub(r"\s+", " ", re.sub(r"^the\s+", "", (text or "").strip().lower()))


def _thing(name: str) -> str:
    """A sensor's name as the thing it belongs to: "Hallway smoke alarm Battery" -> "Hallway smoke alarm"."""
    return re.sub(r"\s+(battery( level| low)?|filter( life| remaining)?)$", "", name, flags=re.I) or name
