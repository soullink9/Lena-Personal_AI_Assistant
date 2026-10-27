# Installation

[← Back to README](../../README.md)

Lena isn't on any app store. You build it in your own GitHub repository, signed with a key only you hold, and install the APK yourself.

## 1. Make your signing key (once)

Android only installs an update over an app signed with the same key, so you make one key and keep it forever.

On Linux or macOS (on Windows, use the Git Bash terminal that comes with Git for Windows), in an empty folder:

```sh
openssl req -x509 -newkey rsa:4096 -nodes -days 36500 -subj "/CN=Lena" \
  -keyout lena.key -out lena.crt
openssl pkcs12 -export -name lena -inkey lena.key -in lena.crt -out lena.jks
rm lena.key lena.crt
base64 -w0 lena.jks > lena.jks.b64
```

The second command asks for a password twice. On a Mac, use `base64 -i lena.jks -o lena.jks.b64` for the last line.

If you have Java installed, `keytool` works too:

```sh
keytool -genkeypair -keystore lena.jks -storetype PKCS12 -alias lena \
  -keyalg RSA -keysize 4096 -validity 36500 -dname "CN=Lena"
```

The key's alias must be `lena`, and the key password must match the keystore password (both commands above do this for you).

**Keep `lena.jks` and its password safe**, for example in your password manager. If you lose them, you'll have to uninstall Lena (and sign in again) before installing a build made with a new key. Delete `lena.jks.b64` once it's saved as a secret.

## 2. Add the secrets

In your repository: **Settings → Secrets and variables → Actions → New repository secret**.

| Secret | Value |
|---|---|
| `KEYSTORE_B64` | The full contents of `lena.jks.b64` |
| `KEYSTORE_PASSWORD` | The password you chose |

Without these, builds still work but are debug-signed with a throwaway key, and each one has to be uninstalled before the next will install. The build shows a warning when this happens.

## 3. Build

Every push to `main` builds the app. To build by hand: **Actions → Build APK → Run workflow**.

- **Test builds** (pushes to `main`) appear under the run's **Artifacts** as `lena-apk`, a zip holding `lena-<commit>.apk` and `SHA256SUMS`.
- **Releases** are built when you run the workflow with **Publish a release** ticked (or push a version tag). They appear under **Releases** with the APK attached directly. See [Development](Development.md#making-a-release).

## 4. Install

Download the APK on your phone and open it. Android asks once to allow installs from your browser or file manager.

To check the file, compare its SHA-256 with the `SHA256SUMS` file from the same build.

Lena needs **Android 16** or newer.

## 5. First run

Lena opens on its settings screen.

1. Enter your **Open WebUI address**, starting with `https://`.
2. If you sign in through Authentik or another single sign-on, enter its **sign-in address** (see [Open WebUI Setup](Open-WebUI-Setup.md#sign-in)).
3. Tap **Save** and sign in to Open WebUI. You stay signed in from then on.
4. Allow notifications when asked, if you want "Lena finished" and scheduled prompt notifications.

Then open Settings again from the drawer to pick your model for new chats, what the assistant gesture opens, quick prompts and scheduled prompts. The model list fills in once you're signed in.

## 6. Make Lena your assistant

**Settings → Apps → Default apps → Digital assistant app → Lena.** The **Make Lena your digital assistant** button in Lena's settings takes you there. On Samsung phones the path is **Settings → Apps → Choose default apps → Digital assistant app**.

How you trigger the assistant depends on your phone and navigation style: usually long-pressing the power button, long-pressing home, or swiping up from a bottom corner.

## Updating

Install the new APK over the old one. Your settings and sign-in stay. If Android says the app isn't installed or conflicts with an existing one, see [Troubleshooting](Troubleshooting.md#an-update-wont-install).

## Changing your settings later

Open the drawer and tap **Settings**, or long-press Lena's icon and tap **Settings**.

## Updating from 1.x

2.0 needs Android 16. Install it over 1.x as usual; your address and sign-in carry over. The assistant gesture now opens the **Ask Lena sheet** by default; switch it back to **Voice call** in Settings → Assistant gesture if you prefer.
