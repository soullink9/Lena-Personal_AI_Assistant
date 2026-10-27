# Development

[← Back to README](../README.md)

Lena is plain Java against the Android framework, with no libraries. That's deliberate: there's nothing to audit beyond the files below.

## Files

| File | What it does |
|---|---|
| `app/src/main/java/.../MainActivity.java` | The web view: opening your server, call mode, the link lock, microphone and file picker, back button. |
| `app/src/main/java/.../SettingsActivity.java` | The settings screen, built in code. |
| `app/src/main/java/.../Prefs.java` | Stores the three settings and checks the address is `https://`. |
| `app/src/main/AndroidManifest.xml` | Permissions, the assistant registration, backup and Safe Browsing switches. |
| `app/src/main/res/xml/network_security_config.xml` | HTTPS only; which certificate authorities to trust. |
| `app/src/main/res/xml/data_extraction_rules.xml` | Keeps all app data out of backups and transfers. |
| `app/src/main/res/xml/shortcuts.xml` | The Call and Settings shortcuts. |
| `app/src/main/res/` (the rest) | Name, theme colours and icons. |
| `.github/workflows/build.yml` | Builds, signs and publishes the APK. |

## Versions

| Item | Version |
|---|---|
| Minimum Android | 11 (API 30) |
| Target and compile SDK | 36 |
| Android Gradle Plugin | 8.13.0 |
| Gradle | 8.14.3 |
| Java | 17 language level; builds on JDK 17 or 21 |

## Building locally

There's no Gradle wrapper in the repository. Use Android Studio, or install Gradle 8.14.3, a JDK 17 or 21, and the Android SDK, then:

```sh
gradle assembleRelease
```

The APK lands in `app/build/outputs/apk/release/`. To sign with your key, set these first; otherwise it's debug-signed:

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

1. In `app/build.gradle.kts`, raise `versionCode` by one and set `versionName` (for example, `1.0.1`).
2. Add a line to `CHANGELOG.md`.
3. Commit and push to `main`.
4. Tag and push the tag:
   ```sh
   git tag v1.0.1
   git push origin v1.0.1
   ```

The release appears under **Releases** a couple of minutes later.
