# Working on Lena

Instructions for AI coding assistants working in this repository. The same rules for people are in [CONTRIBUTING.md](CONTRIBUTING.md); read it, [ARCHITECTURE.md](ARCHITECTURE.md) and [docs/Development.md](docs/Development.md) before changing anything.

## Rules

- **Versions move together.** The app (`versionName` in `android/app/build.gradle.kts`), the integration's `manifest.json`, the `version:` line of both Open WebUI files and the newest `CHANGELOG.md` section always carry the same version. `versionCode` goes up by one per release. Versions are plain numbers (`0.6.3`), with no "beta" or other suffix.
- **No 1.0.** Stay on 0.x until the owner explicitly says otherwise.
- **Consistent naming.** Use the names already used for the same thing; one concept, one name, in code, settings, UI text and docs.
- **Documentation sync.** Whenever a function, class or logic block changes, update its comment, the docs that describe it and the changelog in the same commit. Comments explain why.
- **Readability over cleverness.** No dense one-liners or clever shortcuts.
- **Test integrity.** Keep existing behaviour. Run `python3 tools/check.py`; the Home Assistant tests (`.github/workflows/home-assistant.yml`) and the app build (`.github/workflows/build.yml`) must pass.
- **Conventional Commits** for every commit (`feat:`, `fix:`, `refactor:`, `docs:`, `test:`, `build:`, `ci:`, `chore:`, `perf:`, with an optional scope).
- **Adjustments first.** Before every build and release, read every file in `adjustments/` (see its README). Answer each New one: set Status to Reviewed (with how and when under Review), Declined (with the reason) or Done (with the version), and move Done and Declined ones to `adjustments/done/`. `tools/check.py` fails while any is New, and the release workflow won't publish.
- **No new dependencies** (Gradle libraries or plugins, Python packages) without the owner's explicit approval. `tools/check.py` enforces the approved list.

## Standing constraints

- Android: `minSdk`, `targetSdk` and `compileSdk` stay at 36.
- Home Assistant: everything is set up in the UI; nothing in YAML.
- No downloads at build time beyond Gradle's own (models and the C reference are committed).
- Never commit secrets, tokens, signing keys or personal data. Never ask anyone to paste tokens into a chat.
- Public README: short, with the Disclosure section at the top; details live in `docs/`.
