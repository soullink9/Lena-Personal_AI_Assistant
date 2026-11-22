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
CONF_ACCESS_USERS = "access_users"  # non-admin users who may use her web addresses (access.py)
CONF_ALERT_ALL_CLEAR = "alert_all_clear"
CONF_TIDY_DAYS = "memory_tidy_days"
CONF_LOCAL_WEATHER = "local_weather"
CONF_TIDY_TASK = "memory_tidy_task"
# Rooms and household (Configure > Rooms and household).
CONF_BEDROOMS = "bedrooms"
CONF_ENTRANCES = "entrances"
CONF_HOUSEHOLD = "household"
# Speaking first (Configure > Speaking first).
CONF_SPEAK = "speak_first"
CONF_SPEAK_DAILY = "speak_daily"
CONF_FOLLOW_UP_LISTEN = "follow_up_listen"
CONF_LEAVE_CHECKS = "leave_checks"
# The wake word (Configure > Wake word).
CONF_WAKE_ONE = "wake_one_answers"
CONF_WAKE_WINDOW = "wake_window"
# Routines (Configure > Routines).
CONF_ROUTINES = "routines"
CONF_ROUTINE_WATCH_DAYS = "routine_watch_days"
# Her own opinions, and her curiosity (Configure > Personality).
CONF_HERS_DAYS = "hers_days"
CONF_CURIOUS = "curious"
# Around the house (Configure > Around the house).
CONF_GOODNIGHT = "goodnight"
CONF_SHOPPING_LIST = "shopping_list"
CONF_SHOPS = "shops"
CONF_UPKEEP = "upkeep"
CONF_CELEBRATE = "celebrate_with"

# The sections of Configure that group the newer settings. Each section's values are saved flat with
# the rest (config_flow.py), so every setting is read the same way (LenaCore.opt).
SECTIONS = {
    "rooms": (CONF_BEDROOMS, CONF_ENTRANCES, CONF_HOUSEHOLD),
    "speaking_first": (CONF_SPEAK, CONF_SPEAK_DAILY, CONF_FOLLOW_UP_LISTEN, CONF_LEAVE_CHECKS),
    "wake_word": (CONF_WAKE_ONE, CONF_WAKE_WINDOW),
    "routines": (CONF_ROUTINES, CONF_ROUTINE_WATCH_DAYS),
    "personality": (CONF_HERS_DAYS, CONF_CURIOUS),
    "around_the_house": (CONF_GOODNIGHT, CONF_SHOPPING_LIST, CONF_SHOPS, CONF_UPKEEP, CONF_CELEBRATE),
    "access": (CONF_ACCESS_USERS,),
}

# Tapping an alert on a phone opens the Lena app, listening (through the Home Assistant companion app).
OPEN_LENA = "intent:#Intent;action=io.github.soullink9.lena.TALK;launchFlags=0x10000000;component=io.github.soullink9.lena/.AskActivity;end"

# Sent whenever her feelings or memory change, so her entities update.
SIGNAL_UPDATED = "lena_updated"
# Sent with a display's device id and what it's doing (idle, listening, processing, responding), so its
# Assist satellite shows it.
SIGNAL_PHASE = "lena_phase"

# Moments she can set herself with the feel tool; the rest come from the house.
HOUSE_MOMENTS = {"Worried", "Relieved"}

