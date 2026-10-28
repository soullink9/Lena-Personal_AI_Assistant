"""GET /api/lena/context: how Lena feels and what she remembers, for Open WebUI and the Lena app.

Any Home Assistant user's long-lived access token can read it; no admin rights needed.
Also carries the weather, for the Lena app's display. Add ?memory=0 to leave her memory out, or ?reasons=0 to leave out why she feels the way she does.
"""

from __future__ import annotations

import re

from aiohttp import web

from homeassistant.helpers.http import KEY_HASS, HomeAssistantView

from .const import DOMAIN


class LenaContextView(HomeAssistantView):
    url = "/api/lena/context"
    name = "api:lena:context"
    requires_auth = True

    async def get(self, request: web.Request) -> web.Response:
        hass = request.app[KEY_HASS]
        core = hass.data.get(DOMAIN)
        if core is None:
            return self.json_message("Lena isn't set up", status_code=404)
        f = core.feelings
        return self.json({
            # Ready to add to a chat's system message: the time, who's home, feelings, memory.
            "context": core.context(
                with_reasons=request.query.get("reasons") != "0",
                memory=request.query.get("memory") != "0",
            ),
            "feelings": {
                "mood": f.mood,
                "mood_reason": f.mood_reason,
                "energy": f.energy,
                "moment": f.moment,
                "moment_reason": f.moment_reason,
            },
            "memory": core.memories(),
            "weather": core.weather_now(),
        })


class LenaMediaView(HomeAssistantView):
    """GET /api/lena/media: Home Assistant's media, for the Lena app's photo slideshow.

    ?id=<media-source://...> lists a folder (no id: the top level, such as Immich and My media):
    {"title", "children": [{"id", "title", "folder", "photo"}]}. Add &photos=1 to get the folder's
    photos instead, as addresses on this Home Assistant: {"title", "photos": ["/immich/...", ...]}.
    Any Home Assistant user's token works, the same as browsing media in Home Assistant itself.
    """

    url = "/api/lena/media"
    name = "api:lena:media"
    requires_auth = True

    async def get(self, request: web.Request) -> web.Response:
        hass = request.app[KEY_HASS]
        if hass.data.get(DOMAIN) is None:
            return self.json_message("Lena isn't set up", status_code=404)
        if "media_source" not in hass.config.components:
            return self.json_message("Home Assistant's media browser isn't set up", status_code=404)
        from homeassistant.components import media_source  # only when it's there

        media_id = request.query.get("id") or None
        try:
            folder = await media_source.async_browse_media(hass, media_id, content_filter=_is_photo)
        except Exception as err:  # noqa: BLE001 - shown to the person picking an album
            return self.json_message(f"Couldn't open that: {err}", status_code=400)
        children = folder.children or []

        if request.query.get("photos") != "1":
            return self.json({
                "title": folder.title,
                "children": [
                    {"id": c.media_content_id, "title": c.title, "folder": c.can_expand, "photo": _is_photo(c)}
                    for c in children
                ],
            })

        photos: list[str] = []
        for c in children:
            if c.can_expand or not _is_photo(c):
                continue
            try:
                resolved = await media_source.async_resolve_media(hass, c.media_content_id, None)
            except Exception:  # noqa: BLE001 - skip what can't be shown
                continue
            photos.append(_screen_sized(resolved.url))
            if len(photos) >= MAX_PHOTOS:
                break
        return self.json({"title": folder.title, "photos": photos})


MAX_PHOTOS = 5000

# Immich serves originals at /immich/<account>/<asset>/fullsize/<type>: often huge, or HEIC.
# Its "preview" size is a JPEG made for screens.
_IMMICH = re.compile(r"^/immich/([^/]+)/([^/]+)/fullsize/")


def _screen_sized(url: str) -> str:
    if m := _IMMICH.match(url):
        return f"/immich/{m.group(1)}/{m.group(2)}/preview/image/jpeg"
    return url


def _is_photo(item) -> bool:
    return str(getattr(item, "media_content_type", "") or "").startswith("image/") or \
        str(getattr(item, "media_class", "")) == "image"
