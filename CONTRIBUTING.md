# Contributing

This is a personal project shared in case it helps someone, so replies and merges may be slow. Issues and pull requests are still welcome.

## Changing a default setting

Where every setting starts (on a new install, and after Return to defaults) is in [`adjustments/defaults.env`](adjustments/defaults.env): each setting's name, with comments above it saying where it is in Settings, its possible values and its current default. Type the new value after its `=` and commit; it's applied before the next build. See [adjustments/README.md](adjustments/README.md).

## Reporting a problem

Open an issue using the bug report template, and include:

- Which part is affected: the app, the Modelfile, the Home Assistant integration (feelings, memory, tools), the Open WebUI filter or tool, the voice bridge or Piper.
- Versions: the app version (bottom of its settings), Android version and phone, Open WebUI, Home Assistant and Ollama, the model you used and your GPU.
- What you said or did, what you expected, and what happened.
- For Home Assistant prompt problems, the rendered prompt: **Settings → Voice assistants → your assistant → Debug** shows what was sent.
- Relevant log lines (Home Assistant, Open WebUI or the voice bridge), with anything private removed.

## Development rules

These apply to every change, by people and by AI assistants alike.

**Versions**
- One version for everything. The app, the Home Assistant integration, the Open WebUI files, the voice bridge and the changelog always carry the same version and move together, so every part of a release is known to work with every other. `tools/check.py` fails if they disagree.
- No 1.0 yet. Versions stay 0.x until the owner says otherwise; `tools/check.py` fails on 1.0 or higher. The app's `versionCode` always goes up by one per release. Versions are plain numbers, such as `0.6.3`, with no "beta" suffix.

**Code and documentation**
- Consistent naming: follow the names already used for the same thing elsewhere (Kotlin `camelCase` and `PascalCase`, Python `snake_case`, settings keys `snake_case`), and call one concept by one name everywhere: in code, settings, the app's words and the docs.
- Documentation moves with the code: when a function, class or piece of logic changes, its comment, the docs pages that describe it and the changelog change in the same commit.
- Readable over clever: plain steps over dense one-liners, named helpers over nested expressions, and a comment that says why wherever the reason isn't obvious.
- Nothing breaks: refactors and new features keep existing behavior and pass `tools/check.py`, the Home Assistant tests and the app build.

**Workflow**
- [Conventional Commits](https://www.conventionalcommits.org/) for every commit: `feat:`, `fix:`, `refactor:`, `docs:`, `test:`, `build:`, `ci:`, `chore:`, `perf:`, optionally with a scope such as `feat(app):` or `fix(integration):`. Turn on the check once per clone with `git config core.hooksPath tools/git-hooks`.
- No new libraries or dependencies without the owner's approval. `tools/check.py` lists the approved ones and fails on anything else; an approved addition goes on that list and in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) in the same change.

## How the repository is laid out

The full map is in [Development](docs/Development.md#layout); in short:

| Folder | What's in it |
|---|---|
| `android/` | The app (Gradle project) |
| `custom_components/lena/`, `home-assistant/` | The Home Assistant integration, and its tests in a real Home Assistant |
| `open-webui/`, `ollama/`, `voice/` | The Open WebUI filter and tool, her model, the voice bridge |
| `tools/` | Checks and helpers you run yourself (`check.py`, `defaults.py`, the wake word and voice command checks) |
| `scripts/` | What the workflows run, on Gitea and GitHub alike: the release scripts and the build and test steps |
| `tests/` | Tests of the release scripts ([tests/README.md](tests/README.md)) |
| `.gitea/workflows/` | Testing and releasing on the Gitea runner, where the project lives |
| `.github/workflows/` | The same on GitHub, the public mirror, plus Home Assistant's and HACS's own checks |

## Checking a change

```sh
pip install pyyaml
python3 tools/check.py                 # offline, seconds; also runs tests/test_release.py
tools/commands_check/run.sh            # timers and alarms by voice (needs kotlinc or Gradle)
tools/pairing_check/run.sh             # Set up from your phone: QR codes, the page, its cipher (kotlinc or Gradle; Node for the cipher)
tools/frontend_check/run.sh            # the wake word front end against the original C (needs g++ and a JDK)
scripts/test_home_assistant.sh latest  # the integration in a real Home Assistant (in a virtualenv: it pip installs)
```

Every push runs all of these, plus the app build, on the Gitea runner (`.gitea/workflows/test.yml`); GitHub runs its own copy when the mirror pushes.

## Releasing

The project lives on Gitea, which push-mirrors to GitHub. GitHub is the public copy: people install from its releases, and the app updates itself from them. Releasing works the same when GitHub can't be reached; GitHub catches up when the mirror next pushes.

1. Set the new version everywhere (see [Development](docs/Development.md#things-that-must-stay-in-step); `tools/check.py` catches a mismatch) and rename `## [Unreleased]` in `CHANGELOG.md` to `## [0.6.12] - <date>`. Apply any waiting default changes (`python3 tools/defaults.py apply`). Commit and push.
2. On Gitea: **Actions → Release → Run workflow**, and enter the version without the v (`0.6.12`). Or push a tag: `git tag v0.6.12 && git push origin v0.6.12`.
3. Gitea's release workflow (`.gitea/workflows/release.yml`) checks the version against every part and the changelog, runs every test, builds and signs the APK, checks it (`scripts/check_apk.py`) and publishes the Gitea release with the APK and `SHA256SUMS`. It creates the tag when you used the button.
4. The push mirror takes the tag to GitHub, where `.github/workflows/release.yml` does the same and publishes the GitHub release. It has its own **Run workflow** button with the same version box, for releasing from GitHub alone.
5. Nothing to do on GitHub. The tag usually starts its release by itself; when it doesn't (GitHub starts nothing for a push that brings more than three tags at once, which happens with several releases between two mirror pushes), `.github/workflows/catch-up-releases.yml` does. It runs after every mirror push and every 6 hours, finds Lena's versions with a tag but no release (newer than the newest released, see `scripts/missing_releases.py`), and starts the release workflow on each tag. Only the newest version is marked Latest, whatever order they finish in. It starts them on main, so today's release workflow builds each tag's own code. A release run that failed isn't started again by itself: the catch-up run shows a warning, and once it's fixed, **Actions → Catch up releases → Run workflow** with **Also start again the versions whose release run failed** ticked starts those again (re-running the failed run would run the old workflow again). If GitHub still refuses to create a release (HTTP 403), add a `RELEASE_TOKEN` Actions secret on GitHub, a fine-grained token with **Contents: Read and write** on this repository only, and the release workflow publishes with it instead.

Both refuse to publish when anything is off: a version mismatch, no changelog section, a default change waiting, a test failing, or an APK that isn't signed with your key. Release titles are the plain version (`v0.6.12`); there are no pre-releases.

What Gitea needs, once: Actions enabled for the repository; the `KEYSTORE_B64` and `KEYSTORE_PASSWORD` secrets (the same as GitHub's, see [Installation](docs/app/Installation.md#2-add-the-secrets)); a `RELEASE_TOKEN` secret only if the job's own token can't create releases; the `GITEA_INTERNAL_URL` Actions variable if the runner reaches Gitea at another address than its public one; and the push mirror to GitHub. Every tag must be on Gitea: the mirror makes GitHub's tags match Gitea's, so a tag only on GitHub would be deleted there and its release turned into a draft.

## Changing things

- Read [ARCHITECTURE.md](ARCHITECTURE.md) for how the parts fit together, then the [Development](docs/Development.md) page: the layout, what must stay in step and the checks to run. For the app, also read [App development](docs/app/Development.md).
- Run `python3 tools/check.py` before committing (see [Checking a change](#checking-a-change)).
- Keep comments readable by someone new to the project. Every function and class gets a short comment, and it explains why (the reason for a choice, the trap it avoids), not just what the code already says. Prefer plain, step-by-step code over clever one-liners.
- Add a line to the next release's section of `CHANGELOG.md` (one changelog for the whole project), saying which parts people need to update.
- Never put private data in the repository: IP addresses, tokens, signing keys, real names, or the contents of a real memory list. Use `Alex` and `Sam` in examples.

### Personality and Home Assistant

- Her character lives only in `ollama/Modelfile`. Don't copy it into the Home Assistant prompt or Open WebUI.
- If you add or rename a feeling, follow [Her feelings](docs/personality/Moods.md#adding-or-removing-one); `tools/check.py` tells you what's out of step.
- Nothing in Home Assistant is set up in YAML: the integration creates what it needs and is configured in the UI.

### The app

- The app targets Android 16 (`targetSdk` and `compileSdk` 36) and installs on Android 11 and later (`minSdk` 30) for Android TV boxes. Put anything newer than Android 11 behind a version check (`Compat.kt` has the common ones); release builds fail on a call that isn't.
- Keep it talking only to the user's own Open WebUI and sign-in page. No analytics, no new third-party services, and new libraries only when there's a good reason.
- Raise `versionCode` and `versionName` in `android/app/build.gradle.kts` for anything you plan to release. `versionCode` must always go up, even if the version name starts again.

## Licensing

- Lena is licensed under the [GPL-3.0](LICENSE). By contributing, you agree that your contribution is licensed under it too.
- Add third-party code, models or assets only when their licence allows redistribution under these terms, and list them in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) with their licence. Licences that must travel with the app go in `android/app/src/main/assets/wakeword/licenses/` (or a similar `licenses/` folder next to the asset).
- Never commit secrets. Configuration that differs per person goes in the apps' settings or in environment variables; [.env.example](.env.example) lists the ones the project reads.
