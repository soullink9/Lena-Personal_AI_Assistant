"""Constants for the Lena integration."""

DOMAIN = "lena"
NAME = "Lena"

# Options (Settings > Devices & services > Lena > Configure).
CONF_PEOPLE = "people"
CONF_WEATHER = "weather"
CONF_CALENDARS = "special_days"
CONF_WORRIES = "worries"
CONF_MORNING = "morning"
CONF_BEDTIME = "bedtime"
CONF_AWAY_HOURS = "away_hours"
CONF_EMPTY_HOURS = "empty_hours"
CONF_WEATHER_CHANCE = "weather_chance"
CONF_MOOD_HOLD_HOURS = "mood_hold_hours"
CONF_MOOD_CHANGE_CHANCE = "mood_change_chance"
CONF_MOMENT_MINUTES = "moment_minutes"
CONF_ALERT_PHONES = "alert_phones"
CONF_ALERT_AGENT = "alert_agent"
CONF_ALERT_ALL_CLEAR = "alert_all_clear"

# Tapping an alert on a phone opens the Lena app, listening (through the Home Assistant companion app).
OPEN_LENA = "intent:#Intent;action=io.github.soullink9.lena.TALK;launchFlags=0x10000000;component=io.github.soullink9.lena/.AskActivity;end"

# Sent whenever her feelings or memory change, so her entities update.
SIGNAL_UPDATED = "lena_updated"

# Moments she can set herself with the feel tool; the rest come from the house.
HOUSE_MOMENTS = {"Worried", "Relieved"}

LLM_API_ID = "lena"
