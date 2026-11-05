# Preparing a tablet for the wall display

A tablet that only runs Lena's wall display doesn't need most of what came on it. [`tools/wall-display-debloat.sh`](../../tools/wall-display-debloat.sh) strips a Samsung tablet down to Lena and a few apps you choose, so nothing else runs in the background, nags with notifications or updates itself overnight.

It's optional and fully reversible. It's written for Samsung tablets (One UI), and works on other Android devices too, but there it removes only the Google, AOSP and preinstalled packages it knows.

## What it keeps

The apps listed at the top of the script, in `PROTECT`. As shipped, that's Lena, Google Play Store, Firefox, Calculator, Clock, My Files and Settings. Edit the list to keep yours. It also keeps what those need:

| Kept | Why |
|---|---|
| Google Play services and sign-in, Android Setup, Play system updates | The Play Store, and Google account sync. |
| Android System WebView | Lena's chats and dashboards are web pages inside the app. |
| Google's speech engine (Speech Recognition and Synthesis), and Android System Intelligence where it does speech recognition | Lena hearing you on the tablet (**Hearing you: On this phone**). Without one she sends your voice to your server instead. |
| Samsung Keyboard (or whichever keyboard you use) | Signing in. Samsung's works before the tablet is unlocked, unlike most. |
| Your text-to-speech engine | Lena's voice when it's set to **The phone's voice**. |
| Alarm sounds, the lock screen clock, time zone data | Clock. |
| Device care, the biometrics page | Settings' Battery, Storage and Device care pages. |
| Samsung's software and security updates | **Settings → Software update** keeps working. |
| Wi-Fi sign-in pages | Networks with a sign-in page. |

The keyboard, text-to-speech engine, WebView, speech recognizer and home app are read from the tablet when the script runs, so it keeps the ones actually in use.

It also leaves alone everything the [Universal Android Debloater](https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation) list rates as only for experts or unsafe: Wi-Fi, Bluetooth, the navigation bar, media storage, the share sheet and Samsung's own home screen, among others. The home screen app is the one you might expect to see go. It stays because it's the Recents screen and the fallback home screen if Lena ever can't be.

## What it removes

- Every Samsung, Google, carrier and preinstalled package the list rates safe to remove ("Recommended" or "Advanced"), plus a few "Expert" ones that are safe on a Wi-Fi tablet: Galaxy Store, Modes and Routines, Samsung Contacts, Gallery's photo remaster and two system leftovers. That covers Bixby, Samsung account and cloud, Samsung Internet, Gallery, Camera, Notes, Gmail, YouTube, Maps, the Google app and more.
- Every app installed on top of the system: Netflix, Facebook, Microsoft and the like, and anything you installed yourself that isn't in `PROTECT`.

Things you'll notice afterwards:

- Without Gallery, tapping a photo in My Files doesn't open it.
- Without Galaxy Store, Samsung's own apps (Clock, Calculator, My Files) only update with system updates.

## Running it

You need a computer with [Android platform-tools](https://developer.android.com/tools/releases/platform-tools) (adb), and USB debugging on the tablet: **Settings → About tablet → Software information**, tap **Build number** seven times, then **Settings → Developer options → USB debugging**. Connect the tablet, and accept the "Allow USB debugging?" prompt on its screen.

Make Lena the home screen first (**Settings → Wall display → Make it this device's home screen** in Lena). The script warns you if she isn't.

1. **Preview** (changes nothing):

   ```
   ./tools/wall-display-debloat.sh
   ```

   It lists what it would remove with a line about each, what it's keeping for this tablet and why, and any system packages the list doesn't know.

2. **Apply:**

   ```
   ./tools/wall-display-debloat.sh --apply
   ```

   It asks you to type `REMOVE`, removes everything from the preview, writes a log named `debloat-<serial>-<date>.log` in the current folder, and offers to restart the tablet. Keep the log.

With more than one device connected, add `-s <serial>` (see `adb devices`). It runs in the bash that comes with macOS and Linux, and in Git Bash or WSL on Windows.

## Going further

The last part of the preview lists the system packages nobody has rated yet. Most are Android's own parts, but newer Samsung extras (Galaxy AI and the like) turn up there too. Look up any you don't recognise, and add the ones you want gone to `EXTRA_REMOVE` at the top of the script.

## Undoing it

```
./tools/wall-display-debloat.sh --restore debloat-<serial>-<date>.log
```

That puts back everything the log removed, then restart the tablet.

- **Samsung's and Google's system packages** are only removed for your user (`pm uninstall --user 0`) and stay in the firmware. `--restore`, or a factory reset, brings them back.
- **Apps that were installed on top** are really uninstalled, so reinstall those from the Play Store. `--restore` lists them.
- **Packages Android wouldn't uninstall** are switched off instead (`pm disable-user`), and `--restore` switches them back on.

## Keeping the list up to date

The package lists at the end of the script come from the Universal Android Debloater Next Generation project (GPL-3.0). To update them, download its [`resources/assets/uad_lists.json`](https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation/blob/main/resources/assets/uad_lists.json), then run:

```
python3 tools/debloat_lists.py path/to/uad_lists.json
```

Check the changes with `git diff`, and run a preview on a tablet before committing. Which tiers it takes, and the hand-picked "Expert" packages, are at the top of `tools/debloat_lists.py`. It never adds a package that's in the script's `PROTECT` list.
