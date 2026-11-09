# Lena on Android TV

Lena runs on Android TV and Google TV boxes, such as the **Nvidia Shield**, and on TVs with Android TV built in. There she's the wall display only: your Home Assistant dashboards with her bar, the screensaver and photo frame, timers and alarms, announcements, and talking to her with the remote. There's no chat, since a TV has no keyboard to type with.

It's the same app as on a phone, from the same releases. It needs Android 11 or newer, which every Shield on current software has.

## Install

1. On the TV, allow installs from one app: **Settings → Device Preferences → Security & restrictions → Unknown sources** (Shield and Android TV; on Google TV it's under **Settings → Privacy → Security & restrictions**), and turn it on for the app you'll install with (a file manager, or Downloader).
2. Download `lena-<version>.apk` from the [latest release](../../../../releases/latest) with that app and open it. With adb on a computer, `adb install lena-<version>.apk` does the same.
3. Open **Lena** from the TV's apps.

Later updates come through Lena herself, as on a phone (**Settings → Software version → Updates**). The first time, Android asks you to allow Lena to install apps.

## Set her up

Lena opens on **Connect Home Assistant**. Choose **Connect from your phone**: the TV shows an address and its QR code.

1. **Home Assistant, from your phone**: scan the QR code with your phone's camera (or type the short address into its browser), on the same Wi-Fi as the TV. On the page that opens, check your Home Assistant's address and tap **Sign in with Home Assistant**; you sign in on your Home Assistant's own page (your password manager works there), and Lena on the TV makes herself a long-lived token, named after the TV, which you'll see in your Home Assistant profile under **Security**. The page also takes a token you've made yourself, pasted. The TV closes the screen once it's connected, and the wall display starts. It's also in **Settings → Home Assistant → Connect from your phone**.

For anything else you'd type on the TV, the **Google TV** app's remote on your phone has a keyboard that types into the TV, and you can paste there too.
2. **Who answers**: Home Assistant's Assist is the simplest on a TV. To use Open WebUI instead, enter its address and turn on **Sign in by herself** with your Open WebUI email and password: a TV can't open Open WebUI's sign-in page.
3. **Hearing you**: **The TV's voice search** (the default). It's the TV's own speech recognition, the one that hears through the remote's microphone.

## Make her the TV's home screen

Android TV has no setting to choose another home screen. Without one, Lena opens from the TV's apps, and the Home button still goes to the TV's own home screen. To make the Home button open Lena, run [`tools/android-tv-home.sh`](../../tools/android-tv-home.sh) once from a computer with adb:

1. On the TV: **Settings → Device Preferences → About**, click **Build** 7 times, then turn on **Network debugging** (Shield) or **USB debugging** in **Developer options**.
2. On the computer: `./tools/android-tv-home.sh --connect <the TV's IP address>` shows what it would change, and accept the "Allow network debugging?" prompt on the TV.
3. `./tools/android-tv-home.sh --connect <the TV's IP address> --apply` makes the change.

It turns off the TV's own launcher and its setup app (nothing is uninstalled) and makes Lena the home app. The setup app is a home screen too: with only the launcher off, Android falls back to it whenever Lena is briefly gone, which is every time she updates, and it then sits on **Completing setup** for good. With it off as well, the TV just waits for Lena to come back. `--restore` turns both back on, as does a factory reset. A system update may turn the launcher back on; run `--apply` again if the Home button stops opening Lena.

**Stuck on "Completing setup"** (from an earlier version of the script): run `./tools/android-tv-home.sh --connect <the TV's IP address> --apply` again. It turns the setup app off and goes to Lena's home screen.

To strip the TV down as well (Nvidia's and Google's extras, keeping what the TV and Lena need), [`tools/wall-display-debloat.sh`](../../tools/wall-display-debloat.sh) works over the network the same way: see [On an Nvidia Shield or Android TV](Wall-Display-Tablet.md#on-an-nvidia-shield-or-android-tv).

## The TV home

On a TV, Lena's home screen is rows you move through with the remote, over your photo slideshow (dimmed) if you've chosen an album:

- At the top, the time, the date and weather, and how Lena feels.
- **Lena**: **Talk to Lena**, chosen when the screen opens, so pressing OK talks straight away; and every timer, alarm and reminder that's running, with the time left.
- **Home Assistant**: your wall display dashboards (Settings → Home Assistant → Wall display dashboards). One opens full screen with Lena's bar; Back from its first page, or Home, returns to the rows.
- **Apps**: your TV apps, the ones you open most first, and **All apps** (where you can hide some from the row).
- **Settings**: Lena's settings and the TV's.

## The remote

| Button | What it does |
|---|---|
| **OK**, held down | She listens, anywhere. Hold it again to send what you said, or to stop her talking. |
| **OK**, pressed | Presses whatever is chosen on screen, as usual. |
| **Search** button, on remotes that send one | She listens. |
| **Menu** (☰) | Lena's settings. |
| **Back** | Back, as usual; closes her when she's open. |
| Any button, on the screensaver | Wakes the screen, and does nothing else. |

The arrows move along the rows, and around the bar and a dashboard. On a dashboard (a web page), the TV moves between its cards and buttons.

**The remote's microphone button** opens Google Assistant: Android keeps that button for its own assistant, so no app can have it. Two ways round it:

- **A button of its own.** Lena has a second entry in the TV's apps, **Talk to Lena**, which opens her listening. Put it on a button the remote lets you customise: on a Shield, **Settings → Remotes & Accessories**, then the remote's customisable button (the menu button's double press, or the Netflix button on remotes that have one), and choose **Talk to Lena**. It works from any app, not only Lena's home.
- **Hold OK**, on Lena's screens.

Either way she listens through **The TV's voice search**. Some remotes send their microphone button as "search", and then Lena takes it directly. To see what your remote's buttons send, press them and look in **Settings → Logs** for lines from **Remote**.

## What's different from a phone

- **No chat** and no typing: talk to her. A chat link (from a notification, say) opens the wall display.
- **Hearing**: **The TV's voice search** uses the remote's microphone. **Your server** and **Home Assistant** record the sound themselves, which needs a microphone plugged into the TV box: Android doesn't give apps the remote's.
- **Wake word**: needs a microphone plugged into the TV box (USB), as the remote's microphone is only on while you hold its button.
- **Android 11** (Shield): her words don't appear as you speak on every TV (it depends on the TV's voice search), and after her name the microphone briefly changes hands, as on phones before Android 13.
- Settings that only make sense on a phone (the assistant gesture, widgets, the chat screen) aren't shown.

## Not yet checked on a real TV

This was built without a Shield to try it on. If something here doesn't work on yours, the log (**Settings → Logs**) says what happened; send it along.

- Whether the Shield's voice search hears through the remote when Lena asks, from a customised button or OK held (it does for other apps' voice search).
- The Shield's settings for customising a remote button (they differ between remotes and Shield versions).
- Moving around a Home Assistant dashboard with the arrows.
