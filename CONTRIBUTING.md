# Contributing

This is a personal project shared in case it helps someone, so replies and merges may be slow. Issues and pull requests are still welcome.

## Reporting a problem

Open an issue using the bug report template, and include:

- Which part is affected: the app, the Modelfile, the Home Assistant integration (feelings, memory, tools), the Open WebUI filter or tool, the voice bridge or Piper.
- Versions: the app version (bottom of its settings), Android version and phone, Open WebUI, Home Assistant and Ollama, the model you used and your GPU.
- What you said or did, what you expected, and what happened.
- For Home Assistant prompt problems, the rendered prompt: **Settings → Voice assistants → your assistant → Debug** shows what was sent.
- Relevant log lines (Home Assistant, Open WebUI or the voice bridge), with anything private removed.

## Changing things

- Read [ARCHITECTURE.md](ARCHITECTURE.md) for how the parts fit together, then the [Development](docs/Development.md) page: the layout, what must stay in step and the checks to run. For the app, also read [App development](docs/app/Development.md).
- Run `python3 tools/check.py` before committing.
- Keep comments readable by someone new to the project. Every function and class gets a short comment, and it explains why (the reason for a choice, the trap it avoids), not just what the code already says. Prefer plain, step-by-step code over clever one-liners.
- Add a line to the next release's section of `CHANGELOG.md` (one changelog for the whole project), saying which parts people need to update.
- Never put private data in the repository: IP addresses, tokens, signing keys, real names, or the contents of a real memory list. Use `Alex` and `Sam` in examples.

### Personality and Home Assistant

- Her character lives only in `ollama/Modelfile`. Don't copy it into the Home Assistant prompt or Open WebUI.
- If you add or rename a feeling, follow [Her feelings](docs/personality/Moods.md#adding-or-removing-one); `tools/check.py` tells you what's out of step.
- Nothing in Home Assistant is set up in YAML: the integration creates what it needs and is configured in the UI.

### The app

- The app targets Android 16 only: keep `minSdk`, `targetSdk` and `compileSdk` at 36.
- Keep it talking only to the user's own Open WebUI and sign-in page. No analytics, no new third-party services, and new libraries only when there's a good reason.
- Raise `versionCode` and `versionName` in `android/app/build.gradle.kts` for anything you plan to release. `versionCode` must always go up, even if the version name starts again.

## Licensing

- Lena is licensed under the [GPL-3.0](LICENSE). By contributing, you agree that your contribution is licensed under it too.
- Add third-party code, models or assets only when their licence allows redistribution under these terms, and list them in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) with their licence. Licences that must travel with the app go in `android/app/src/main/assets/wakeword/licenses/` (or a similar `licenses/` folder next to the asset).
- Never commit secrets. Configuration that differs per person goes in the apps' settings or in environment variables; [.env.example](.env.example) lists the ones the project reads.
