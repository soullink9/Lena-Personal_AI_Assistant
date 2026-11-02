# Adjustments: Lena's default settings

[`defaults.csv`](defaults.csv) lists every setting that has a default, one per row, with four columns:

| Column | What it is |
|---|---|
| **Setting** | Where it is in Lena's Settings, such as `Talking to Lena › Wake word › Her name`. |
| **Change to** | Empty, or the new default you want. It must be one of the possible values, spelled the same way. |
| **Possible values** | What it can be, separated by semicolons. |
| **Current default** | What a new install, and **Return to defaults**, uses today. |

## Changing a default

1. Open `defaults.csv` on GitHub and edit it (the pencil icon), or download it, change it in a spreadsheet and upload it again (**Add file → Upload files**, same name).
2. In **Change to**, type the new value for each setting you want to change, copied from **Possible values**. Leave the rest empty.
3. Commit.

Before every build, the changes are reviewed and applied: `python3 tools/defaults.py apply` checks each value, writes it into the app (`Defaults.kt`), moves it to **Current default** and empties **Change to**. Until that's done, `python3 tools/check.py` fails and the release workflow won't publish, so a request is never skipped. Each build's summary lists any changes still waiting.

## What a default changes

A default is where a setting starts: on a new install, and after **Settings → About → Return to defaults**. It doesn't change what you've already picked on your phone. Connections (Open WebUI, Home Assistant), the model, dashboards, the screensaver's album and your prompts have no defaults here; Return to defaults keeps them.
