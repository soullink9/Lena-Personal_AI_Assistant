"""Adding Lena (one click) and her settings (Configure): who she misses, what she notices, her day."""

from __future__ import annotations

from typing import Any

from homeassistant.config_entries import ConfigEntry, ConfigFlow, ConfigFlowResult, OptionsFlowWithReload
from homeassistant.core import callback
from homeassistant.helpers.selector import (
    BooleanSelector,
    ConversationAgentSelector,
    EntitySelector,
    EntitySelectorConfig,
    NumberSelector,
    NumberSelectorConfig,
    NumberSelectorMode,
    SelectSelector,
    SelectSelectorConfig,
    SelectSelectorMode,
    TimeSelector,
)

from . import brain
from .compat import vol
from .const import (
    CONF_ALERT_AGENT,
    CONF_ALERT_ALL_CLEAR,
    CONF_ALERT_PHONES,
    CONF_AWAY_HOURS,
    CONF_BEDTIME,
    CONF_CALENDARS,
    CONF_EMPTY_HOURS,
    CONF_MOMENT_MINUTES,
    CONF_MOOD_CHANGE_CHANCE,
    CONF_MOOD_HOLD_HOURS,
    CONF_MORNING,
    CONF_PEOPLE,
    CONF_TIDY_DAYS,
    CONF_TIDY_TASK,
    CONF_WEATHER,
    CONF_WEATHER_CHANCE,
    CONF_WORRIES,
    DOMAIN,
    NAME,
)


class LenaConfigFlow(ConfigFlow, domain=DOMAIN):
    """Adding Lena: one click, no questions. She's a single entry per Home Assistant
    (single_config_entry in the manifest); everything else is set later under Configure.
    """
    VERSION = 1

    async def async_step_user(self, user_input: dict[str, Any] | None = None) -> ConfigFlowResult:
        """The only step: create the entry with empty options, so every setting starts at its default."""
        if user_input is not None:
            return self.async_create_entry(title=NAME, data={}, options={})
        return self.async_show_form(step_id="user", data_schema=vol.Schema({}))

    @staticmethod
    @callback
    def async_get_options_flow(config_entry: ConfigEntry) -> LenaOptionsFlow:
        """Her settings (Configure), in an options flow so they can change without removing her."""
        return LenaOptionsFlow()


def _number(low: float, high: float, unit: str, step: float = 1) -> NumberSelector:
    """A number box (not a slider) with a unit, for settings where an exact value matters more than
    dragging.
    """
    return NumberSelector(NumberSelectorConfig(min=low, max=high, step=step, unit_of_measurement=unit, mode=NumberSelectorMode.BOX))


class LenaOptionsFlow(OptionsFlowWithReload):
    """Configure: her house, her day, how quickly her feelings move, alerts, and tidying her memory."""

    async def async_step_init(self, user_input: dict[str, Any] | None = None) -> ConfigFlowResult:
        """Configure: the people, sensors and calendars she watches, her day, how moody she is, and
        alerts. Saving reloads her with the new settings.
        """
        if user_input is not None:
            return self.async_create_entry(data=user_input)
        o = self.config_entry.options

        def default(key: str) -> dict[str, Any]:
            """Shows the saved value, or the default from brain.DEFAULTS, as the field's suggestion. A
            suggestion rather than a default lets a field be cleared.
            """
            value = o.get(key, brain.DEFAULTS.get(key))
            return {"suggested_value": value} if value is not None else {}

        phones = sorted(
            f"notify.{name}" for name in self.hass.services.async_services().get("notify", {})
            if name.startswith("mobile_app_")
        )
        schema = vol.Schema({
            vol.Optional(CONF_PEOPLE, description=default(CONF_PEOPLE)):
                EntitySelector(EntitySelectorConfig(domain="person", multiple=True)),
            vol.Optional(CONF_WEATHER, description=default(CONF_WEATHER)):
                EntitySelector(EntitySelectorConfig(domain="weather")),
            vol.Optional(CONF_CALENDARS, description=default(CONF_CALENDARS)):
                EntitySelector(EntitySelectorConfig(domain="calendar", multiple=True)),
            vol.Optional(CONF_WORRIES, description=default(CONF_WORRIES)):
                EntitySelector(EntitySelectorConfig(domain="binary_sensor", multiple=True)),
            vol.Required(CONF_MORNING, description=default(CONF_MORNING)): TimeSelector(),
            vol.Required(CONF_BEDTIME, description=default(CONF_BEDTIME)): TimeSelector(),
            vol.Required(CONF_AWAY_HOURS, description=default(CONF_AWAY_HOURS)): _number(1, 72, "h"),
            vol.Required(CONF_EMPTY_HOURS, description=default(CONF_EMPTY_HOURS)): _number(1, 24, "h"),
            vol.Required(CONF_WEATHER_CHANCE, description=default(CONF_WEATHER_CHANCE)): _number(0, 100, "%"),
            vol.Required(CONF_MOOD_HOLD_HOURS, description=default(CONF_MOOD_HOLD_HOURS)): _number(1, 12, "h"),
            vol.Required(CONF_MOOD_CHANGE_CHANCE, description=default(CONF_MOOD_CHANGE_CHANCE)): _number(0, 100, "%"),
            vol.Required(CONF_MOMENT_MINUTES, description=default(CONF_MOMENT_MINUTES)): _number(2, 240, "min"),
            # Alerts: the companion app's notify actions on this Home Assistant (notify.mobile_app_...).
            vol.Optional(CONF_ALERT_PHONES, description=default(CONF_ALERT_PHONES)): SelectSelector(SelectSelectorConfig(
                options=phones, multiple=True, custom_value=True, mode=SelectSelectorMode.DROPDOWN,
            )),
            vol.Optional(CONF_ALERT_AGENT, description=default(CONF_ALERT_AGENT)): ConversationAgentSelector(),
            vol.Optional(CONF_ALERT_ALL_CLEAR, default=o.get(CONF_ALERT_ALL_CLEAR, brain.DEFAULTS[CONF_ALERT_ALL_CLEAR])): BooleanSelector(),
            # Her memory: how often she tidies it, and with which AI Task (empty: Home Assistant's default).
            vol.Required(CONF_TIDY_DAYS, description=default(CONF_TIDY_DAYS)): _number(0, 90, "days"),
            vol.Optional(CONF_TIDY_TASK, description=default(CONF_TIDY_TASK)):
                EntitySelector(EntitySelectorConfig(domain="ai_task")),
        })
        return self.async_show_form(step_id="init", data_schema=schema)
