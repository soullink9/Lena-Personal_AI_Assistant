# Privacy and Security

[← Back to README](../README.md)

Lena is built so that your conversations go to your server and nowhere else.

## What Lena connects to

Your Open WebUI address, and whatever that page itself loads. Nothing else: no analytics, no crash reporting, no update checks, and no server run by anyone else.

Links to other sites open in your normal browser, not in Lena, so those sites never see Lena's storage or your sign-in.

## What Lena stores

| What | Where |
|---|---|
| Your Open WebUI address, model ID and the call-mode switch | Lena's private app settings |
| Your Open WebUI sign-in, and anything Open WebUI keeps in the browser | Lena's private web view storage |

Both stay on the phone. Lena turns off Android backups and phone-to-phone transfer for all of its data, so your sign-in never reaches Google's cloud or a new phone. Uninstalling Lena deletes everything.

## What Lena turns off

- **Google Safe Browsing.** Normally Android's web view checks pages against Google's list of dangerous sites. Lena only opens your own server, so it turns this off rather than send lookups to Google.
- **Web view usage reports.** Lena opts out of the usage metrics Android's web view can send to Google.
- **Plain `http://`.** All traffic must be encrypted, and pages can't load insecure content.
- **Third-party cookies, file access, location and pop-up windows.**

## The microphone

Only your Open WebUI address can use the microphone, and only after you've granted Lena the permission. Lena never asks for the camera, so requests for video are refused.

Android itself blocks apps from listening while they're in the background, and shows its privacy indicator whenever the microphone is in use.

## Certificates

Lena trusts the normal public certificate authorities, plus any you've installed on the phone yourself. That second part is what makes a homelab certificate authority work.

If you only use public certificates (for example, Let's Encrypt or Tailscale), you can make Lena stricter: delete the `<certificates src="user" />` line from `app/src/main/res/xml/network_security_config.xml` and rebuild.

## Your signing key

Every APK you build is signed with your own key, which lives only in your repository's secrets and wherever you saved the file. Android refuses to install an update signed with a different key, so nobody else can publish an update that installs over your copy.

## Keeping it secure

- Keep **Android System WebView** up to date in the Play Store. It's the engine Lena uses, and it gets security fixes there, not in Lena's updates.
- Keep your Open WebUI updated. Lena shows whatever your server sends.
- The code is short enough to read in one sitting: start with `MainActivity.java`.

## Reporting a problem

Open an issue in this repository.
