# Lena

A tiny Android app for your own Open WebUI server. It opens your server full screen, can be set as the phone's digital assistant, and starts a voice call straight from the assistant gesture.

> **Disclosure:** Written with AI assistance (Claude). It's a personal project with no warranty. Read the code before you install it; it's three Java files, under 400 lines with comments. Needs Android 11 or newer.

## What it does

- Opens only your server. Links to anywhere else open in your normal browser.
- Assistant gesture (or the **Call** shortcut) starts a new chat in call mode, on the model you choose.
- HTTPS only. Mic only, for your server only. No camera, no location.
- No libraries, no analytics, no Safe Browsing lookups, no backups of your sign-in.

## Set up

1. **Make a private GitHub repo** and push this folder to `main`.
2. **Make a signing key once** (any machine with Docker):
   ```sh
   docker run --rm -it -v "$PWD:/out" eclipse-temurin:21-jre \
     keytool -genkeypair -keystore /out/lena.jks -storetype PKCS12 -alias lena \
     -keyalg RSA -keysize 4096 -validity 36500 -dname "CN=Lena"
   base64 -w0 lena.jks > lena.jks.b64
   ```
   Keep `lena.jks` and its password safe. Updates only install over a build signed with the same key.
3. **Add two repo secrets** (Settings → Secrets and variables → Actions):
   `KEYSTORE_B64` (contents of `lena.jks.b64`) and `KEYSTORE_PASSWORD`.
4. **Build:** Actions → Build APK → Run workflow. Download `lena-apk`, check `SHA256SUMS`, install the APK.
5. **In the app:** enter your `https://` address and, if you want, your model's ID (e.g. `lena`). Sign in once.
6. **Make it the assistant:** Settings → Apps → Default apps → Digital assistant app → Lena.

## Open WebUI must have

- **HTTPS.** Android blocks the mic over plain `http://`. A homelab CA works if you installed its certificate on the phone.
- **Server-side speech.** Set Speech-to-Text and Text-to-Speech to a server engine (e.g. Whisper and your TTS), not "Web API". The in-app browser has no built-in speech engine.
- **Password sign-in.** Sign-in through another site (SSO/OAuth) would be sent to your browser and won't complete.

## Limits

- No wake word. It opens from the gesture, the icon, or the Call shortcut.
- On the lock screen, Android asks you to unlock first. That's on purpose.
