# Privacy and Security

[← Back to README](../../README.md)

Lena is built so that your conversations go to your server and nowhere else.

## What Lena connects to

Your Open WebUI address, any sign-in addresses you add (such as Authentik), your Home Assistant address if you add one, and whatever those pages themselves load. GitHub only when you check for or download an update in Settings → Software version → Updates (never in the background). Nothing else: no analytics, no crash reporting sent anywhere, and no server run by anyone else.

Links to other sites open in your normal browser, not in Lena, so those sites never see Lena's storage or your sign-in.

## How the native features sign in

When you sign in inside Lena, Open WebUI stores your session in a cookie for your server's address. The drawer, Ask sheet, notifications and scheduled prompts read that cookie from Lena's own storage and send it only to your Open WebUI address, the same way the page does. If a proxy such as Authentik sits in front of Open WebUI, its cookie for that address is sent too.

- Lena never sees your password, and there's no API key to store, unless you turn on **Sign in by herself** (Settings → Open WebUI), below.
- Signing out of Open WebUI (turn on **Open WebUI's own menus** in Settings → Chat screen to reach it) signs out the native features too.
- If a request is redirected to a sign-in page, Lena stops and asks you to sign in again rather than following it.

**Sign in by herself** (off by default) is for when Open WebUI signs every device out as it restarts, which strands a wall tablet. You give Lena your Open WebUI email (or LDAP user name) and password once; they're encrypted with the same kind of keystore key as the Home Assistant token, which can't be copied off the phone, and sent only to your Open WebUI's own sign-in address, and only when its session has really ended (a request was refused and a check of the session confirms it). A failed sign-in isn't retried for half a minute. The session it gets is stored the same way as when you sign in yourself. If you'd rather not keep a password on the device, give your Open WebUI server a fixed `WEBUI_SECRET_KEY` instead, so restarts don't sign anyone out.

## Home Assistant (optional)

If you add your Home Assistant under **Settings → Home Assistant**, Lena uses a long-lived access token you create there to read her feelings and the weather from the Lena integration, check for house alerts if you turn them on, read her shared memory list, and to change that list when you edit it.

- The token is encrypted with a key kept in the phone's keystore, which can't be copied off the phone, and is sent only to the Home Assistant address you entered (HTTPS only).
- Lena never shows the saved token again. Paste a new one to replace it, or remove it with the bin icon.
- A token can do anything its Home Assistant user can, so consider a dedicated non-admin user for Lena. Reading her feelings and memory doesn't need admin rights.
- Revoke it any time in Home Assistant (your profile → Security), and Lena simply stops showing her feelings and shared memory.

**Set up from your phone** (the top of Settings, Settings → Home Assistant, and the wall display before it's connected) serves a small page on your home network, over plain http, only while that screen is open (15 minutes at most, and until something is saved). There your phone fills in Open WebUI's address, email and password, Home Assistant's address and token, your Gitea and the motion sensors.

- **The page never gets what's saved.** Passwords, emails and tokens are never sent to the phone, only whether each is set; leave a field empty to keep it. Addresses are filled in so you can correct them.
- **Encrypted on the phone.** The QR code carries a one-time key after a `#`, which browsers never send over the network. The page encrypts what you type with it (ChaCha20-Poly1305) before sending, so someone listening on your Wi-Fi sees only ciphertext. The short address typed into a browser has no key, so then the page only offers Home Assistant's own sign-in.
- **Nothing is saved until you press Save on the device.** It lists what would change (secrets only as "new") and checks the Home Assistant token and every address first.
- **One phone, a few tries.** The first phone to open the page holds it; any other is told it's in use. Five requests guessing the code close the page.
- **Signing in with Home Assistant** happens on your Home Assistant's own https page; Home Assistant sends a one-time code back through the page, which Lena swaps for a long-lived token named after the device, then signs that first sign-in out.

What it can't stop: someone already inside your network who actively rewrites the page while it loads, during those few minutes. Signing in with Home Assistant avoids that for your Home Assistant password.

## Camera mode

[Camera mode](Cameras.md) is off unless you turn it on, on each device, and shows only the cameras you switch on there. It runs only while it's open: the connection to Home Assistant, the still pictures and the motion it watches all stop when it closes, and nothing opens it by itself. Still pictures are fetched by the app with its token and kept in memory, never saved. The live picture plays in a page from inside the app; the app does every message to Home Assistant, so that page never holds the token (an HLS stream's address carries Home Assistant's own short-lived stream token). Camera pictures never go into Lena's log.

## The wall display

The wall display shows your Home Assistant's own pages in a web view and signs them in with your saved token, handing it over only when Home Assistant's page asks (the way the companion app does), only on your Home Assistant's address, only to the top frame, and only while that frame shows Home Assistant's own frontend. Iframe and camera cards never get it, and neither do other apps served from your Home Assistant's address (add-on ingress or the Ingress Proxy integration, under `/api/`), whether they're in a frame or opened as the whole page; Lena's page script isn't added to them at all. The token is never put in an address or written into the page. Because the dashboard is your real Home Assistant, anyone at the tablet can do whatever that token's user can; for a shared tablet, use a token from a non-admin Home Assistant user.

## The photo slideshow

The slideshow asks the Lena integration for an album's photos and downloads each one from your Home Assistant with your token, decoded in memory and never saved. Lena only fetches paths on your Home Assistant's own address, so the token can't be sent anywhere else, even if a media source names another site.

## Logs

Settings → Software version → Logs shows Lena's own log: problems, warnings and what she was doing (a request that failed and why, the wake word hearing her, an alert sent, a crash with where it happened). It's two small files in Lena's private storage, the oldest part dropped as it fills, and it only leaves the phone if you copy or share it. It records errors and events, never your messages, her answers, tokens or passwords, and anything shaped like a token is blanked out as a precaution. Clear it any time.

## Updates

Checking and downloading happen only when you tap them, from Lena's public releases on GitHub (or your own Gitea, if you've given its address), without signing in to anything; the download itself comes from GitHub's file storage (or your Gitea), over https only. Before an update is offered, Lena checks it against the release's SHA-256 file, and that it's Lena, newer, and signed with the same key as the copy you have; Android refuses a different key again when installing. Installing always asks you to confirm.

## What Lena stores

| What | Where |
|---|---|
| Your addresses, model, gesture choice, quick prompts and scheduled prompts | Lena's private app settings |
| Your Open WebUI sign-in, and anything Open WebUI keeps in the browser | Lena's private web view storage |
| Your Home Assistant token, if you add one | Lena's private storage, encrypted with a keystore key |
| Your Open WebUI email and password, only if you turn on Sign in by herself | Lena's private storage, encrypted with a keystore key |
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

The sheet only listens while it shows "Listening…". The wake word (off unless you turn it on) listens the whole time the wall display or the screen saver is showing (or whenever Lena is on screen, if you choose that), but on the device only: the sound goes through two small models a few times a second (the wake word and a voice check) and is thrown away, and nothing is recorded or sent until it hears her name and hands over to the sheet. In the main app, only your Open WebUI address can use the microphone (never your sign-in page), and only after you've granted Lena the permission. Android can't tell the app which frame is asking, so if you turn on Open WebUI's "allow same origin" setting for iframes (off by default), artifacts and previews count as your address too; that setting also gives them your Open WebUI session, so leave it off unless you trust what they show. Lena never asks for the camera, so requests for video are refused.

Android blocks apps from listening while they're in the background, and shows its privacy indicator whenever the microphone is in use.

## Certificates

Lena trusts the normal public certificate authorities, plus any you've installed on the phone yourself. That second part is what makes a homelab certificate authority work.

If you only use public certificates (for example, Let's Encrypt or Tailscale), you can make Lena stricter: delete the `<certificates src="user" />` line from `app/src/main/res/xml/network_security_config.xml` and rebuild.

## Code from others

Lena uses Google's Android Jetpack libraries, Google's LiteRT (to run the wake word models on the phone) and JetBrains' coroutines library, all open source. The wake word models are openWakeWord's, downloaded and checked against pinned SHA-256 sums when the app is built. There are no ad, analytics or tracking libraries, and nothing that talks to a service of its own.

## Your signing key

Every APK you build is signed with your own key, which lives only in your repository's secrets and wherever you saved the file. Android refuses to install an update signed with a different key, so nobody else can publish an update that installs over your copy.

## Keeping it secure

- Keep **Android System WebView** up to date in the Play Store. It gets security fixes there, not in Lena's updates.
- Keep your Open WebUI updated. Lena shows whatever your server sends.

## Reporting a problem

Open an issue in this repository.
