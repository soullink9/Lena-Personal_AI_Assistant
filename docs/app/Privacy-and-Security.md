# Privacy and Security

[← Back to README](../../README.md)

Lena is built so that your conversations go to your server and nowhere else.

## What Lena connects to

Your Open WebUI address, any sign-in addresses you add (such as Authentik), your Home Assistant address if you add one, and whatever those pages themselves load. Nothing else: no analytics, no crash reporting, no update checks, and no server run by anyone else.

Links to other sites open in your normal browser, not in Lena, so those sites never see Lena's storage or your sign-in.

## How the native features sign in

When you sign in inside Lena, Open WebUI stores your session in a cookie for your server's address. The drawer, Ask sheet, notifications and scheduled prompts read that cookie from Lena's own storage and send it only to your Open WebUI address, the same way the page does. If a proxy such as Authentik sits in front of Open WebUI, its cookie for that address is sent too.

- Lena never sees your password, and there's no API key to store.
- Signing out of Open WebUI (turn on **Settings → Chats → Show Open WebUI's own menus** to reach it) signs out the native features too.
- If a request is redirected to a sign-in page, Lena stops and asks you to sign in again rather than following it.

## Home Assistant (optional)

If you add your Home Assistant under **Settings → Home Assistant**, Lena uses a long-lived access token you create there to read her feelings helpers and her shared memory list, and to change that list when you edit it.

- The token is encrypted with a key kept in the phone's keystore, which can't be copied off the phone, and is sent only to the Home Assistant address you entered (HTTPS only).
- Lena never shows the saved token again. Paste a new one to replace it, or remove it with the bin icon.
- A token can do anything its Home Assistant user can, so consider a dedicated non-admin user for Lena. Reading her feelings and memory doesn't need admin rights.
- Revoke it any time in Home Assistant (your profile → Security), and Lena simply stops showing her feelings and shared memory.

## What Lena stores

| What | Where |
|---|---|
| Your addresses, model, gesture choice, quick prompts and scheduled prompts | Lena's private app settings |
| Your Open WebUI sign-in, and anything Open WebUI keeps in the browser | Lena's private web view storage |
| Your Home Assistant token, if you add one | Lena's private storage, encrypted with a keystore key |
| Pending background checks | Lena's private WorkManager database |

All of it stays on the phone. Lena turns off Android backups and phone-to-phone transfer for all of its data, so your sign-in never reaches Google's cloud or a new phone. Uninstalling Lena deletes everything.

Chats you start from the Ask sheet or a scheduled prompt are saved on your server, in your Open WebUI history, like any other chat.

## Who can make Lena send a prompt

Sending a prompt can run your tools, so Lena is careful about it:

- **Other apps** can open the Ask sheet (by sharing text, for example) but can only fill the box. Nothing is sent until you tap send.
- **Lena's own widget and quick prompt shortcuts** send straight away. They carry a random secret that only Lena knows, so another app can't fake them.
- **Scheduled prompts** only run what you set up in Lena's settings.

## Notifications

Notifications show the start of a reply. On a locked screen, Android hides the text and shows only "Lena replied", unless you've told Android to show sensitive notification content.

## What Lena turns off

- **Google Safe Browsing.** Lena only opens your own server, so it doesn't send page lookups to Google.
- **Web view usage reports.** Lena opts out of the usage metrics Android's web view can send to Google.
- **Plain `http://`.** All traffic, the page and the native features alike, must be encrypted.
- **Third-party cookies, file access, location and pop-up windows** in the page.

## The lock screen

Off by default. With it off, Android asks you to unlock before the sheet opens. With **Talk to Lena on the lock screen** on, the sheet opens over the lock screen in a private mode: a fresh conversation with your history hidden, no reasons behind her mood, no photos, and nothing opens without unlocking. She's also told the person may not be you and not to unlock, open or disarm anything or share personal details. That's an instruction to the model, not a lock, so anyone holding your phone can still ask her things. Leave it off if she can control locks, doors or alarms.

## Photos

Photos you show her are shrunk, sent to your Open WebUI with your question, and saved in that chat. Taking one hands the camera app a single file in Lena's private storage, deleted as soon as Lena has read it. Choosing one uses Android's photo picker, so Lena has no access to your gallery, only to the photo you pick. Lena asks for no camera or storage permission.

## The microphone

When you talk to Lena in the sheet:

- **On this phone** (the default) uses Android's on-device speech recognition. The audio stays on the phone; only the words go to your server.
- **Your server** records until you stop talking and sends the recording to your Open WebUI's speech-to-text.
- Spoken replies are made by your Open WebUI's text-to-speech (the reply text goes to your server, which already wrote it), or by the phone's own voice if you choose.

The sheet only listens while it shows "Listening…". In the main app, only your Open WebUI address can use the microphone (never your sign-in page), and only after you've granted Lena the permission. Lena never asks for the camera, so requests for video are refused.

Android blocks apps from listening while they're in the background, and shows its privacy indicator whenever the microphone is in use.

## Certificates

Lena trusts the normal public certificate authorities, plus any you've installed on the phone yourself. That second part is what makes a homelab certificate authority work.

If you only use public certificates (for example, Let's Encrypt or Tailscale), you can make Lena stricter: delete the `<certificates src="user" />` line from `app/src/main/res/xml/network_security_config.xml` and rebuild.

## Code from others

Lena uses Google's Android Jetpack libraries and JetBrains' coroutines library, all open source. There are no ad, analytics or tracking libraries, and nothing that talks to a service of its own.

## Your signing key

Every APK you build is signed with your own key, which lives only in your repository's secrets and wherever you saved the file. Android refuses to install an update signed with a different key, so nobody else can publish an update that installs over your copy.

## Keeping it secure

- Keep **Android System WebView** up to date in the Play Store. It gets security fixes there, not in Lena's updates.
- Keep your Open WebUI updated. Lena shows whatever your server sends.

## Reporting a problem

Open an issue in this repository.
