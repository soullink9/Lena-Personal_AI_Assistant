"""Who may use Lena's web addresses (/api/lena/...): Home Assistant's admins, and the users an admin picks
in Lena's options (Configure → Who may use Lena).

Home Assistant lets every signed-in user call any web address an integration adds. Lena's give out her
memory (what the household told her about each other), who's home, the house's photos, and let a caller
speak for a display. In a household with children's or guests' accounts that's too much, so only admins
get in by default; the accounts the Lena app, Open WebUI's filter and the voice bridge sign in with are
added there when they aren't admins.
"""

from __future__ import annotations

from aiohttp import web

from homeassistant.auth.models import User
from homeassistant.components.http import KEY_HASS_USER
from homeassistant.helpers.http import KEY_HASS, HomeAssistantView

from .const import CONF_ACCESS_USERS, DOMAIN


def allowed(core, user: User | None) -> bool:
    """Whether this Home Assistant user may use Lena's web addresses: an admin, or a user chosen in her
    options. A request without a user (it can't happen behind requires_auth) is refused.
    """
    if user is None or not user.is_active:
        return False
    return user.is_admin or user.id in (core.opt(CONF_ACCESS_USERS) or [])


def refused(view: HomeAssistantView, request: web.Request) -> web.Response | None:
    """The 403 answer for a user who may not use Lena's web addresses, or None to go on. Before she's set
    up there's nothing to give out, and the view says so itself.
    """
    core = request.app[KEY_HASS].data.get(DOMAIN)
    if core is None or allowed(core, request.get(KEY_HASS_USER)):
        return None
    return view.json_message(
        "This Home Assistant user may not use Lena. An admin can allow it in Lena's options (Who may use Lena), "
        "or sign in with an admin account.",
        status_code=403,
    )
