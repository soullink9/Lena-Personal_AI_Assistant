# Contributing

This is a personal project shared in case it helps someone, so replies and merges may be slow. Issues and pull requests are still welcome.

## Reporting a problem

Open an issue using the bug report template, and include:

- Which part is affected: the app, the Modelfile, Home Assistant (prompt, feelings, mood engine, scripts), the Open WebUI filter or tool, the voice bridge or Piper.
- Versions: the app version (bottom of its settings), Android version and phone, Open WebUI, Home Assistant and Ollama, the model you used and your GPU.
- What you said or did, what you expected, and what happened.
- For Home Assistant prompt problems, the rendered prompt: **Settings → Voice assistants → your assistant → Debug** shows what was sent.
- Relevant log lines (Home Assistant, Open WebUI or the voice bridge), with anything private removed.

## Changing things

- Read the [Development](docs/Development.md) page first. It explains the layout, what must stay in step and the checks to run. For the app, also read [App development](docs/app/Development.md).
- Run `python3 tools/check.py` before committing.
- Keep comments readable by someone new to the project. Explain why, not just what.
- Add an entry to `CHANGELOG.md`, under **App** or **Personality**.
- Never put private data in the repository: IP addresses, tokens, signing keys, real names, or the contents of a real memory list. Use `Alex` and `Sam` in examples.

### Personality and Home Assistant

- Her character lives only in `ollama/Modelfile`. Don't copy it into the Home Assistant prompt or Open WebUI.
- If you add or rename a feeling, follow [Her feelings](docs/personality/Moods.md#add-or-rename-a-feeling); `tools/check.py` tells you what's out of step.
- Helpers are created through the Home Assistant UI, never in YAML.

### The app

- The app targets Android 16 only: keep `minSdk`, `targetSdk` and `compileSdk` at 36.
- Keep it talking only to the user's own Open WebUI and sign-in page. No analytics, no new third-party services, and new libraries only when there's a good reason.
- Raise `versionCode` and `versionName` in `android/app/build.gradle.kts` for anything you plan to release.
