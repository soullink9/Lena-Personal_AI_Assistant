# Working on Lena

Instructions for AI coding assistants working in this repository. The same rules for people are in [CONTRIBUTING.md](CONTRIBUTING.md); read it, [ARCHITECTURE.md](ARCHITECTURE.md) and [docs/Development.md](docs/Development.md) before changing anything.

## Rules

- **Versions move together.** The app (`versionName` in `android/app/build.gradle.kts`), the integration's `manifest.json`, the `version:` line of both Open WebUI files, the voice bridge's `LenaVoiceBridge/` User-Agent and the newest `CHANGELOG.md` section always carry the same version. `versionCode` goes up by one per release. Versions are plain numbers (`0.6.3`), with no "beta" or other suffix.
- **No 1.0.** Stay on 0.x until the owner explicitly says otherwise.
- **Consistent naming.** Use the names already used for the same thing; one concept, one name, in code, settings, UI text and docs.
- **Documentation sync.** Whenever a function, class or logic block changes, update its comment, the docs that describe it and the changelog in the same commit. Comments explain why.
- **Readability over cleverness.** No dense one-liners or clever shortcuts.
- **Test integrity.** Keep existing behaviour. Run `python3 tools/check.py` (it also runs `tests/test_release.py`); every job of `.gitea/workflows/test.yml` (the Home Assistant tests, the app's checks and build) must pass.
- **Gitea first.** The project lives on Gitea (`.gitea/workflows/`), which push-mirrors to GitHub, the public copy. Gitea's workflows use no `uses:` actions and nothing from GitHub, so they work when GitHub is down, and run their steps in bash (`defaults: run: shell: bash`; Gitea's runner would use sh); shared steps live in `scripts/` (plain shell, and Python with its standard library only), used by both Gitea's and GitHub's workflows. Public links (README, docs, release notes) point at GitHub. Releasing is in CONTRIBUTING.md.
- **Conventional Commits** for every commit (`feat:`, `fix:`, `refactor:`, `docs:`, `test:`, `build:`, `ci:`, `chore:`, `perf:`, with an optional scope).
- **Commits are the owner's.** Author and commit every change as `Soullink <344372660+soullink9@users.noreply.github.com>`, with no AI co-author, session or "generated with" lines in commit messages or pull requests.
- **Default settings first.** Before every build and release, check `adjustments/defaults.env` (see its README) for values typed after a setting's `=`, review them, and run `python3 tools/defaults.py apply`, which writes them into `Defaults.kt`, moves them to its **Current default** comment and empties the value. Never edit `Defaults.kt` by hand. A new setting with a default gets an entry (add it to `SETTINGS` in `tools/defaults.py`, then apply). `tools/check.py` fails while a change is waiting, and the release workflow won't publish.
- **No new dependencies** (Gradle libraries or plugins, Python packages) without the owner's explicit approval. `tools/check.py` enforces the approved list.

## Standing constraints

- Android: `targetSdk` and `compileSdk` stay at 36. `minSdk` is 30 (Android 11), approved by the owner for Android TV boxes like the Nvidia Shield; anything newer than Android 11 goes behind a version check (`Compat.kt`), and the build's lint fails on any call that isn't (NewApi is fatal).
- Home Assistant: everything is set up in the UI; nothing in YAML.
- Home Assistant baseline: the owner runs 2026.11. Before building something, check whether Home Assistant 2026.11 already does it, and use that instead of building it again.
- No downloads at build time beyond Gradle's own (models and the C reference are committed).
- Never commit secrets, tokens, signing keys or personal data. Never ask anyone to paste tokens into a chat.
- Public README: short, with the Disclosure section at the top; details live in `docs/`.
