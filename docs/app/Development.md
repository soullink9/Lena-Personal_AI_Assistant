# Development

[← Back to README](../../README.md)

Lena is Kotlin with Jetpack Compose. The only libraries are Google's Android Jetpack libraries and JetBrains' coroutines library; network calls use Android's built-in HTTP client and JSON parser.

## Files

The app is the Gradle project in `android/`. All app code is in `android/app/src/main/kotlin/io/github/soullink9/lena/`, and resources are in `android/app/src/main/res/`.

| File | What it does |
|---|---|
| `MainActivity.kt` | The main screen: the web view and its locks (link lock, microphone, file picker), the native top bar (logo and Chat \| Home switch) and drawer (pinned chats, folders and recent chats, only what fits), Lena's look for Open WebUI's pages, hiding Open WebUI's header, signing in by herself on Open WebUI's sign-in page, intents from shortcuts and notifications, and going back to the wall display when left alone. |
| `AskActivity.kt` | Talking to Lena: the six looks, listening, sending into the Assistant Lena chat, speaking, follow-ups, the lock-screen mode, closing by itself on a wall screen, Share to Lena. |
| `Voice.kt` | Listening (on-device recognizer, or recording for your server) and speaking (your server's voice or the phone's), and her tone of voice. |
| `Api.kt` | Open WebUI's API with the page's sign-in: models, chats, pins, search, memories, notes, knowledge, speech, and starting and following an answer. |
| `SignIn.kt` | Signing in to Open WebUI by herself with details kept encrypted. |
| `SettingsActivity.kt` | The settings screen and all its pages, in sections (Connections, Talking to Lena, Chats, Prompts, This phone, Wall display, About), with **Search settings**. Its `findable` list is what search finds: a new setting needs a line there too. |
| `Ui.kt` | The shared rows and groups the settings and lists are built from. |
| `Prefs.kt` | Stored settings, scheduled prompts, and address checks. |
| `HomeAssistant.kt` | The optional Home Assistant link: her feelings, weather, shared memory, media, and the encrypted secret store (`Secrets`). |
| `Dashboard.kt` | The wall display: Home Assistant in a web view signed in through "external auth", Lena's single bar, the screen saver, the apps list. |
| `assets/css/open_webui.css` | Lena's look for Open WebUI's pages (warm colours, a clay accent, a serif for replies), added to the top frame when **Lena's look** is on. It recolours through Tailwind's colour variables, so it doesn't depend on Open WebUI's markup. (Open WebUI's call button is hidden by `NO_CALLS_CSS` in `MainActivity.kt`, whichever look is on.) |
| `assets/js/home_assistant.js` | The wall display's script inside Home Assistant's page (top frame of the frontend only, not frames or proxied apps): sign-in bridge, hiding its header (and the Ingress Proxy integration's toolbar), Lena's Apps and Settings at the bottom of its menu, reporting its views. |
| `Display.kt` | Her clock and the screen saver (`DisplayActivity` and `LenaDream`). |
| `Slideshow.kt` | The photo frame: loading albums, face-aware framing, transitions, the album picker. |
| `WakeWord.kt` | The wake word engines (microWakeWord and openWakeWord with LiteRT), the voice check and the listening loop. |
| `java/…/MicroFrontend.java` | A line-for-line port of TensorFlow Lite Micro's audio front end, checked bit for bit against the C on every build. |
| `ChatsActivity.kt` | All chats: pinned chats, folders (new, rename, delete) and your whole history, with search. |
| `Defaults.kt` | Every setting's default, written from `adjustments/defaults.csv` by `tools/defaults.py`. Don't edit it by hand. |
| `Refresh.kt` | Settings → Refresh all connections: open screens reload their page when next shown. |
| `Chats.kt` | What the drawer and All chats share: chat and folder rows in each list style (one row look for both), grouping by day, and the long-press menu (pin, move to folder, rename, delete) with its dialogs. |
| `MemoryActivity.kt` | Memory & knowledge: her shared memory, your Open WebUI memories, notes (with a note editor) and knowledge collections. |
| `Photos.kt` | Photos for the sheet: the camera hand-off, shrinking and encoding. |
| `Background.kt` | Notifications, the finished-reply and scheduled-prompt workers, schedule alarms, boot handling and quick prompt shortcuts. |
| `Alerts.kt` | This phone's own 15-minute check for her worries. |
| `Updates.kt` | Updating from GitHub releases: checking, downloading, verifying and installing. |
| `Logs.kt`, `LogsActivity.kt` | Lena's own log file and Settings → Logs. |
| `Widgets.kt` | The Quick Settings tile (Talk to Lena) and the home screen widget. |
| `Markdown.kt` | The small Markdown renderer for the Ask sheet and notifications. |
| `Theme.kt` | Material You colours. |

| File | What it does |
|---|---|
| `android/app/src/main/AndroidManifest.xml` | Permissions, activities, the assistant and share registrations, tile, widget and receivers. |
| `res/xml/network_security_config.xml` | HTTPS only; which certificate authorities to trust. |
| `res/xml/data_extraction_rules.xml` | Keeps all app data out of backups and transfers. |
| `res/xml/shortcuts.xml` | The app icon's long-press shortcuts. |
| `res/xml/lena_widget_info.xml` | The widget's size and description. |
| `.github/workflows/build.yml` | Builds, signs and publishes the APK. |

## Versions

| Item | Version |
|---|---|
| Android | 16 (API 36) minimum, target and compile SDK. Don't raise these until the phone runs a newer Android. |
| Android Gradle Plugin | 8.13.0 |
| Gradle | 8.14.3 |
| Kotlin | 2.2.20 |
| Compose BOM | 2025.10.00 (Material 3) |
| Other Jetpack | activity-compose 1.11.0, core-ktx 1.17.0, lifecycle 2.9.4, webkit 1.14.0, work 2.10.1, glance-appwidget 1.1.1 |
| Java | 17 language level; builds on JDK 17 or 21 |

Newer Jetpack releases may need a newer compile SDK. Check before raising a version.

## How Lena talks to Open WebUI

Lena never asks for an API key. It reads Open WebUI's `token` cookie from the web view's cookie store and sends it as a bearer token, along with the other cookies for that address, only to the configured server. The calls it makes:

| Call | Used for |
|---|---|
| `GET /api/version` | Server status in the drawer |
| `GET /api/models` | Settings → Model; finding Lena; tools and default features for the Ask sheet |
| `GET /api/v1/chats/?page=`, `GET /api/v1/chats/search` | Recent chats in the drawer, history and search in All chats |
| `GET /api/v1/folders/`, `POST /api/v1/folders/`, `POST /api/v1/folders/{id}/update`, `POST /api/v1/folders/{id}/update/expanded`, `DELETE /api/v1/folders/{id}?delete_contents=false` | Folders: listing, new, rename, open or closed (shared with Open WebUI's sidebar), delete keeping the chats |
| `GET /api/v1/chats/folder/{id}/list?page=`, `POST /api/v1/chats/{id}/folder` | A folder's chats; moving a chat into or out of a folder |
| `GET /api/v1/chats/{id}` | Chat titles and models, and following an answer as it's written |
| `GET /api/tasks/chat/{id}` | Whether an answer is still running |
| `POST /api/v1/chats/new`, `POST /api/chat/completions` | Starting a chat from the sheet or a scheduled prompt, using Open WebUI's server-side tool loop |
| `POST /api/v1/chats/{id}` | Adding a follow-up to a sheet conversation; renaming a chat |
| `GET /api/v1/chats/pinned`, `POST /api/v1/chats/{id}/pin` | Pinned chats in the drawer and All chats, and Pin/Unpin; pinning **Assistant Lena** when it's created |
| `POST /api/v1/auths/signin`, `POST /api/v1/auths/ldap` | Signing in by herself, only if you turn it on |
| `GET /api/v1/users/user/settings`, `GET /api/config` | Whether to use Open WebUI's memory feature (your setting, or the server default) |
| `GET /api/v1/memories/`, `POST /api/v1/memories/add`, `POST /api/v1/memories/{id}/update`, `DELETE /api/v1/memories/{id}` | Memories |
| `GET /api/v1/notes/?page=`, `GET /api/v1/notes/{id}`, `POST /api/v1/notes/create`, `POST /api/v1/notes/{id}/update`, `POST /api/v1/notes/{id}/pin`, `DELETE /api/v1/notes/{id}/delete` | Notes: the list, opening, writing, changing (with the page's revision, so a change made in the browser meanwhile is answered with HTTP 409 instead of being overwritten), pinning and deleting |
| `GET /api/v1/knowledge/`, `GET /api/v1/knowledge/{id}/files`, `POST /api/v1/knowledge/{id}/file/remove` | Knowledge collections and their documents |
| `GET /api/v1/auths/` | After an HTTP 401, telling an expired sign-in apart from a missing chat or a feature your account isn't allowed to use (Open WebUI answers all three with 401) |
| `DELETE /api/v1/chats/{id}` | Deleting a chat |
| `POST /api/v1/audio/transcriptions`, `POST /api/v1/audio/speech` | Your server's speech-to-text and voice for the sheet |

And, only if you add it, your Home Assistant, with the token you give it:

| Call | What for |
|---|---|
| `GET /api/` | **Test connection** in settings |
| `GET /api/lena/context?memory=0` | How she feels and the weather, from the Lena integration (the sheet, the drawer, the wall display, house alerts) |
| `GET /api/states/{entity}` | The wall display's motion sensor, while it's idle |
| `GET /api/lena/media`, `?photos=1` | Picking the slideshow's album, and its photos |
| The photos' own paths (`/immich/…`, `/media/…`) | The slideshow; only paths on your Home Assistant, never another address |
| Home Assistant's own pages | The wall display's dashboards, in a web view signed in with the token |
| `POST /api/services/todo/get_items?return_response`, `add_item`, `update_item`, `remove_item` | Her shared memory list, `todo.lena_memory` |

## The wake word models

`WakeWord.kt` runs two kinds of model with LiteRT:

- **Hey Lena** (`hey_lena.tflite`) and **Lena** (`lena.tflite`): microWakeWord streaming models, each with its `.json` (the cutoff and window are read from it), plus ESPHome's voice activity model (`vad.tflite`, `vad.json`), which runs on the same features. Detection follows ESPHome's `streaming_model.cpp`: the average over the window must pass the cutoff, not within 100 frames (one second) of starting or of the last detection, counted only while the latest probability is under the cutoff, and only if the voice model heard speech in the last second. **How sure she must be** moves the cutoff halfway towards 1 and widens the window by two (Fewer false starts), or lowers it by the same step (Easier to wake).
- Their input is the output of TensorFlow Lite Micro's audio front end, so `MicroFrontend.java` is a line-for-line port of that C code (and of KISS FFT's fixed-point FFT). It's Java because it's all integer arithmetic that relies on C's overflow behaviour, which Java's `int`, `long` and `short` reproduce exactly. `tools/frontend_check/run.sh` builds the original C and compares both on test audio; the build workflow runs it, and it must match bit for bit.
- **Hey Jarvis**: openWakeWord's `melspectrogram.tflite`, `embedding_model.tflite` and `hey_jarvis_v0.1.tflite`.

All the models are committed in `app/src/main/assets/wakeword/`, so the build downloads nothing for the wake word: `hey_lena.tflite` and `hey_lena.json` from [TaterTotterson/Tater-Wake-Words](https://github.com/TaterTotterson/Tater-Wake-Words) `microWakeWordsV6` at commit `6fb0b15` (MIT), `lena.tflite` and its `lena.json` from [TaterTotterson/microWakeWords](https://github.com/TaterTotterson/microWakeWords) at commit `022fd4b`, `vad.tflite` and `vad.json` from [esphome/micro-wake-word-models](https://github.com/esphome/micro-wake-word-models) `models/v2` at commit `40ff33f` (Apache 2.0, Open Home Foundation), and the three Hey Jarvis files from openWakeWord's v0.5.1 release. The C that `tools/frontend_check` compares against is in `tools/frontend_check/vendor/` for the same reason. (Gradle and the app's libraries still come from their official servers, as in any Android build.) Licences: openWakeWord's code Apache 2.0, its Hey Jarvis model CC BY-NC-SA 4.0 (non-commercial); the Hey Lena and Lena models MIT; the front end is Apache 2.0 (TensorFlow) and BSD (KISS FFT).

## Building locally

There's no Gradle wrapper in the repository. Use Android Studio, or install Gradle 8.14.3, a JDK 17 or 21, and the Android SDK, then:

```sh
cd android
gradle assembleRelease
```

The APK lands in `android/app/build/outputs/apk/release/`. To sign with your key, set these first; otherwise it's debug-signed:

```sh
export LENA_KEYSTORE=/path/to/lena.jks
export LENA_KEYSTORE_PASSWORD='your password'
```

## How the build works

`build.yml` runs on GitHub's Ubuntu runner, using the JDK and Android SDK already installed there.

1. Downloads Gradle straight from gradle.org and checks it against the published SHA-256. No third-party setup actions.
2. Loads your signing key from the secrets, if they're set.
3. Builds and signs the release APK, names it `lena-<version>.apk` (or `lena-<commit>.apk` for test builds), and writes `SHA256SUMS`.
4. Uploads both as the run's artifact.
5. For version tags only, a separate step with permission to write releases publishes them as a GitHub Release. The build step itself only has read access.

## Making a release

1. In `android/app/build.gradle.kts`, raise `versionCode` by one and set `versionName` (for example, `0.5.1`). `versionCode` must always go up, whatever the name says: Android compares it, not the name.
2. Add a section to `CHANGELOG.md`, and set the same version in `home-assistant/custom_components/lena/manifest.json` and the `version:` line of the two Open WebUI files if they changed.
3. Commit and push to `main`.
4. **Actions → Build APK → Run workflow**, tick **Publish a release**, and run it.

The build reads the version from `versionName`, creates the tag (for example, `v0.5.1`) and publishes the release with the APK attached. It appears under **Releases** a couple of minutes later. Running it twice for the same version fails rather than overwriting the first release.

Pushing a tag yourself (`git tag v0.5.1 && git push origin v0.5.1`) does the same thing. The tag must match `versionName`, or the build stops.
