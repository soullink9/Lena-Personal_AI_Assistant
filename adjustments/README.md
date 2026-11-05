# Adjustments: Lena's default settings

[`defaults.env`](defaults.env) lists every setting that has a default. Each one is a short block:

```
# Talking to Lena › Wake word › Her name
# Possible values: Hey Lena | Lena | Hey Jarvis
# Current default: Hey Lena
WAKE_PHRASE=
```

| Line | What it is |
|---|---|
| First comment | Where the setting is in Lena's Settings. |
| **Possible values** | What it can be, separated by a vertical bar (`\|`). |
| **Current default** | What a new install, and **Return to defaults**, uses today. |
| `NAME=` | Empty, or the new default you want after the `=`. |

## Changing a default

1. Open `defaults.env` on GitHub and edit it (the pencil icon).
2. After the `=` of each setting you want to change, type its new value, copied from **Possible values** (for example `WAKE_PHRASE=Hey Jarvis`). Quotes are optional, and capitals or a stray plural ("1 minutes") don't matter. Leave the rest empty.
3. Commit.

Before every build, the changes are reviewed and applied: `python3 tools/defaults.py apply` checks each value, writes it into the app (`Defaults.kt`), moves it to **Current default** and empties the value again. Until that's done, `python3 tools/check.py` fails and the release workflow won't publish, so a request is never skipped. Each build's summary lists any changes still waiting, or anything in the file it couldn't read.

Only comments (`#`) and `NAME=value` lines belong in the file. `apply` rewrites it from the app's list of settings, so comments you add yourself aren't kept.

## What a default changes

A default is where a setting starts: on a new install, and after **Settings → Software version → Return to defaults**. It doesn't change what you've already picked on your phone. Connections (Open WebUI, Home Assistant, who answers and Assist's agent), the model, dashboards, the screensaver's album, **Listen for her name** and your prompts aren't changed by Return to defaults; for those, the default is only where a new install starts.
