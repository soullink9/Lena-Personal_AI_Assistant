# Troubleshooting

[← Back to README](../../README.md)

## The page won't load

- **Check the address** in Lena's settings (long-press the icon → Settings). It must start with `https://` and match how you reach Open WebUI in a browser, including any port.
- **Certificate errors** show as a blank page. Make sure the certificate is valid for that name, or that your certificate authority is installed on the phone. See [Open WebUI Setup](Open-WebUI-Setup.md#https).
- **Away from home**, the phone needs a route to your server, such as your VPN or Tailscale.

## Call mode opens but doesn't hear me

- **Microphone permission:** Settings → Apps → Lena → Permissions → Microphone → Allow while using the app.
- **Speech-to-text engine:** must be a server engine, not "Web API". See [Open WebUI Setup](Open-WebUI-Setup.md#speech-engines).
- **Another app has the microphone**, such as a phone call or a voice recorder. Close it and try again.

## Call mode hears me but doesn't answer out loud

- **Text-to-speech engine:** must be a server engine, not "Web API".
- **Media volume:** call replies play at media volume, not call volume.

## The assistant gesture opens something else

- Check **Settings → Apps → Default apps → Digital assistant app** is set to Lena.
- Some phones also bind the power button to their own assistant. Look under **Settings → System → Gestures** (or your phone's button settings) for a "press and hold power button" option.

## Lena isn't listed as a digital assistant

Restart the phone after installing, then look again. A few phone makers hide this setting or limit it to their own assistant; the Call shortcut on your home screen works everywhere.

## Open WebUI's header shows up again

An Open WebUI update probably changed its page, so Lena's hiding no longer matches. Everything still works; the page just shows its own header under Lena's. Check **Open WebUI's own menus** is off in Settings, then open an issue so Lena can catch up.

## The drawer says "Sign in to see your chats"

Lena's native features use your sign-in from the page. Sign in on the main screen (or sign in again if your session expired), then reopen the drawer.

## The Ask sheet says "Couldn't reach your server"

Check you can open your Open WebUI in Lena's main screen. Away from home, the phone needs a route to your server (VPN or Tailscale).

## Memory & knowledge says a feature is turned off

Memories need **Admin Panel → Settings → General → Memories** (or your account's memory permission) in Open WebUI; knowledge needs knowledge access for your account. An admin can turn these on.

## Her feelings or the Hers tab don't show

- **Settings → Home Assistant → Test connection.** "Connected, but the Lena integration isn't set up" means the app reached Home Assistant but the [integration](../home-assistant/Installation.md) hasn't been added there yet.
- If the test fails, check the address is the `https://` one you use from your phone, and that the token hasn't been revoked. Paste a new one to replace it.
- The **Hers** tab reads `todo.lena_memory`. If you renamed that entity in Home Assistant, rename it back.

## The wall display shows Home Assistant's login page

The page didn't get Lena's token. Check **Settings → Home Assistant → Test connection**, and that the dashboard path is right (open `https://<your Home Assistant>/<path>` in a browser). Updating **Android System WebView** helps if it's very old; the sign-in needs its newer features.

## The wake word starts by mistake

Open **Settings → Logs** and look for "Heard …": each says how sure she was and what she needed.

- Switch to **Hey Lena** (Settings → Wall display → Wake word). One word on its own, "Lena", is far easier to hear in ordinary talk and on TV.
- Choose **Fewer false starts** under **How sure she must be**.
- Keep the tablet away from a TV or speaker if you can. Music and jingles are turned away by the voice check ("wasn't a voice; ignored" in the logs), but a voice on TV is still a voice.

Before 2.17.0 she could also wake herself the moment the wall display started (after a restart, say), while the sound features were still settling; that's fixed.

## Open WebUI keeps signing me out when it restarts

Open WebUI ends every session when its secret key changes, and if `WEBUI_SECRET_KEY` isn't set it may make a new one each time it starts. Set it to a long random value in your Open WebUI container's environment and restart once; after that, restarts keep everyone signed in. For anything that still slips through, turn on **Settings → Open WebUI → Sign in by herself**.

## Portrait photos lose their heads in the photo frame

Choose **Fit → Smart** (Settings → Wall display → When no one's around). Portrait and panorama photos then show whole on a blurred copy of themselves, and cropped photos are centred on the faces in them. **Fill the screen** crops every photo, around its faces.

## The wake word never hears me

Use **Settings → Wall display → Wake word → Try it** from where you'll stand and watch the bar. Speak at a normal volume, try **Easier to wake** under **How sure she must be**, and try another name to compare. Note she ignores her name for the first second after she starts listening. In the screen saver some phones block the microphone; it says so on screen, and the wall display itself will hear you.

## Talking to Lena doesn't hear me

- Allow the microphone when asked, or in **Settings → Apps → Lena → Permissions**.
- With **Hearing you: On this phone**, Android may need to download its speech model first; Lena uses your server meanwhile. You can also switch to **Your server** in Lena's settings.
- With **Your server**, Open WebUI's speech-to-text must be set up (see [Open WebUI Setup](Open-WebUI-Setup.md#speech-engines)). Lena also uses it whenever the phone's recognizer can't listen, so it's worth setting up either way.
- The warning says what went wrong, for example that the microphone is in use by another app.
- If the mic doesn't open again after Lena replies, check **Keep listening** in Lena's settings.

## Lena doesn't speak her replies

- Check **Lena's voice** in Lena's settings isn't **Off**.
- She only reads replies aloud when you spoke. Typed questions get text replies.
- With **Your server's voice**, Open WebUI's text-to-speech must be set up; if it isn't, Lena uses the phone's voice.
- Replies play at media volume.

## No "Lena finished" or scheduled prompt notifications

- **Settings → Apps → Lena → Notifications** must be allowed, with both channels on.
- **Reply notifications** in Lena's settings must be on (for finished replies).
- If they arrive late or not at all, set **Settings → Apps → Lena → Battery** to **Unrestricted**. Android delays background work for apps it's saving battery on.
- Scheduled prompts run within a few minutes of their time, not to the second.

## Sign-in opens my browser

You sign in through another website, such as Authentik. Add its address under **Sign-in address** in Lena's settings (long-press the icon → Settings), for example `https://auth.example.com`, and sign in again. If a different address still opens in the browser, add that one too. See [Open WebUI Setup](Open-WebUI-Setup.md#sign-in).

## Sign-in stops at a passkey or security key

Android's web view can't use passkeys in Lena. Pick another method in your sign-in page, such as a one-time code or push approval.

## An update won't install

"App not installed" or "conflicts with an existing package" means the new APK was signed with a different key from the one on your phone. This happens when going between a debug-signed test build and a properly signed one, or after changing keys.

Uninstall Lena, install the new APK, and sign in again. Check that both secrets are set (see [Installation](Installation.md#2-add-the-secrets)) so future builds use the same key.

"Its build number is lower than this Lena's" means the release's `versionCode` went down (after resetting the version, say). Android never installs a lower build number over a higher one: raise `versionCode` above the installed one and build again, or uninstall Lena first.

## The build fails

Open the failed run under **Actions** and expand the red step.

- **Load signing key** or **Build** failing right after you changed secrets usually means `KEYSTORE_B64` isn't the complete one-line text from `lena.jks.b64`, or `KEYSTORE_PASSWORD` doesn't match. Make the `.b64` file again with `base64 -w0` (no line breaks) and paste it again.
- Anything else, open an issue with the log.
