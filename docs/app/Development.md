# Development

[← Back to README](../../README.md)

Lena is Kotlin with Jetpack Compose. The only libraries are Google's Android Jetpack libraries and JetBrains' coroutines library; network calls use Android's built-in HTTP client and JSON parser.

## Files

The app is the Gradle project in `android/`. All app code is in `android/app/src/main/kotlin/io/github/soullink9/lena/`, and resources are in `android/app/src/main/res/`.

| File | What it does |
|---|---|
| `MainActivity.kt` | The main screen: the web view and its locks (link lock, microphone, file picker), the native top bar and drawer, hiding Open WebUI's header, intents from shortcuts and notifications. |
| `AskActivity.kt` | The talk/type sheet, the assistant gesture and Share to Lena. |
| `Voice.kt` | Listening (on-device recognizer, or recording for your server) and speaking (your server's voice or the phone's). |
| `SettingsActivity.kt` | The settings screen. |
| `MemoryActivity.kt` | Memory & knowledge: her shared memory, your Open WebUI memories and knowledge collections. |
| `Photos.kt` | Photos for the sheet: the camera hand-off, shrinking and encoding. |
| `HomeAssistant.kt` | The optional Home Assistant link: her feelings and shared memory list, the encrypted token store, and the feelings line. |
| `Api.kt` | Open WebUI's API, using the page's sign-in: models, chats, search, running tasks, and starting and following a chat. |
| `Background.kt` | Notifications, the finished-reply and scheduled-prompt workers, daily alarms, and quick prompt shortcuts. |
| `Widgets.kt` | The Quick Settings tile and the home screen widget. |
| `Prefs.kt` | Stored settings and address checks. |
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
| `GET /api/models` | Model picker; tools and default features for the Ask sheet |
| `GET /api/v1/chats/?page=1`, `GET /api/v1/chats/search` | The drawer's chat list and search |
| `GET /api/v1/chats/{id}` | Chat titles and models, and following an answer as it's written |
| `GET /api/tasks/chat/{id}` | Whether an answer is still running |
| `POST /api/v1/chats/new`, `POST /api/chat/completions` | Starting a chat from the sheet or a scheduled prompt, using Open WebUI's server-side tool loop |
| `POST /api/v1/chats/{id}` | Adding a follow-up to a sheet conversation; renaming a chat |
| `POST /api/v1/chats/{id}/pin` | Pinning the ongoing **Lena** chat when it's created |
| `GET /api/v1/users/user/settings`, `GET /api/config` | Whether to use Open WebUI's memory feature (your setting, or the server default) |
| `GET /api/v1/memories/`, `POST /api/v1/memories/add`, `POST /api/v1/memories/{id}/update`, `DELETE /api/v1/memories/{id}` | Memories |
| `GET /api/v1/knowledge/`, `GET /api/v1/knowledge/{id}/files`, `POST /api/v1/knowledge/{id}/file/remove` | Knowledge collections and their documents |
| `GET /api/v1/auths/` | Telling a deleted chat apart from an expired sign-in (Open WebUI answers both the same way) |
| `DELETE /api/v1/chats/{id}` | Deleting a chat |
| `POST /api/v1/audio/transcriptions`, `POST /api/v1/audio/speech` | Your server's speech-to-text and voice for the sheet |

And, only if you add it, your Home Assistant, with the token you give it:

| Call | What for |
|---|---|
| `GET /api/` | **Test connection** in settings |
| `GET /api/lena/context?memory=0` | How she feels, from the Lena integration |
| `POST /api/services/todo/get_items?return_response`, `add_item`, `update_item`, `remove_item` | Her shared memory list, `todo.lena_memory` |

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

1. In `android/app/build.gradle.kts`, raise `versionCode` by one and set `versionName` (for example, `1.0.1`).
2. Add a line under **App** in `CHANGELOG.md`.
3. Commit and push to `main`.
4. **Actions → Build APK → Run workflow**, tick **Publish a release**, and run it.

The build reads the version from `versionName`, creates the tag (for example, `v1.0.1`) and publishes the release with the APK attached. It appears under **Releases** a couple of minutes later. Running it twice for the same version fails rather than overwriting the first release.

Pushing a tag yourself (`git tag v1.0.1 && git push origin v1.0.1`) does the same thing. The tag must match `versionName`, or the build stops.
