"""GET /api/lena/context: how Lena feels and what she remembers, for Open WebUI and the Lena app.

Any Home Assistant user's long-lived access token can read it; no admin rights needed.
Add ?memory=0 to leave her memory out, or ?reasons=0 to leave out why she feels the way she does.
"""

from __future__ import annotations

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
        })
