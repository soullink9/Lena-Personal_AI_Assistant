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

# Sent whenever her feelings or memory change, so her entities update.
SIGNAL_UPDATED = "lena_updated"

# Moments she can set herself with the feel tool; the rest come from the house.
HOUSE_MOMENTS = {"Worried", "Relieved"}

LLM_API_ID = "lena"
