#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
#
# wall-display-debloat.sh: strips a Samsung Android tablet down to a Lena wall display.
#
# Keeps:   Lena, Google Play Store, Firefox, Calculator, Clock, My Files, Settings
#          (and what those need to work; see PROTECT below).
# Removes: every other Samsung, Google, carrier and preinstalled app this script knows to be safe to
#          remove, plus every app installed on top (Facebook, Netflix, Microsoft and so on).
#
# Run it from a computer with adb (Android platform-tools), with the tablet connected and USB debugging on
# (Settings → About tablet → Software information → tap Build number 7 times; then Settings → Developer
# options → USB debugging).
#
#   ./wall-display-debloat.sh              Preview: lists what would be removed. Changes nothing.
#   ./wall-display-debloat.sh --apply      Removes it (asks you to type REMOVE first). Writes a log.
#   ./wall-display-debloat.sh --restore debloat-<serial>-<date>.log
#                                          Puts back everything that log removed.
#   Add -s <serial> to pick a device when more than one is connected (see `adb devices`).
#
# How it removes: `pm uninstall -k --user 0` (or `pm disable-user` when Android refuses). System apps
# stay in the firmware, so `--restore`, or a factory reset, brings them back. Apps that were installed
# on top (not part of the firmware) are uninstalled for real: reinstall those from the Play Store.
#
# Which packages: the Universal Android Debloater Next Generation list (GPL-3.0,
# github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation, list of
# 2026-11-02): its "Recommended" and "Advanced" tiers for Samsung, Google and AOSP packages, its
# "Recommended" tier for carrier and miscellaneous preloads, and a few hand-picked "Expert" ones that
# are safe on a Wi-Fi tablet. Nothing it rates "Unsafe", and none of its other "Expert" packages
# (Wi-Fi, Bluetooth, navigation bar, media storage, share sheet, the Samsung launcher and software
# updates are among those). Packages it doesn't know are listed at the end of the preview for you to
# review, never removed on their own; add any you want gone to EXTRA_REMOVE. To bring the lists up to
# date with a newer UAD release: python3 tools/debloat_lists.py path/to/uad_lists.json
# More in docs/app/Wall-Display-Tablet.md.
#
# Works with the bash that comes with macOS (3.2), Linux, and Git Bash or WSL on Windows.

set -u
# One sort order everywhere: comm only works on lists sorted the same way.
export LC_ALL=C

# ---- Yours to edit ----------------------------------------------------------------------------------

# Never removed. The apps the wall display keeps first (change these to yours), then what they need.
PROTECT=(
    # The apps you keep
    io.github.soullink9.lena                       # Lena
    com.android.vending                            # Google Play Store
    org.mozilla.firefox                            # Firefox
    org.mozilla.firefox_beta                       # Firefox Beta, if that's the one you use
    org.mozilla.fenix                              # Firefox Nightly, likewise
    com.sec.android.app.popupcalculator            # Calculator
    com.sec.android.app.clockpackage               # Clock
    com.sec.android.app.myfiles                    # My Files
    com.android.settings                           # Settings

    # Play Store: Google Play services, sign-in and Play system updates
    com.google.android.gms
    com.google.android.gsf
    com.google.android.gsf.login
    com.google.android.setupwizard                 # removing it breaks Google account sync
    com.google.android.overlay.permissioncontroller   # removing it breaks Google Play system updates
    com.google.mainline.telemetry                  # Play system updates check this

    # Lena: her whole screen is a WebView; her hearing ("On this phone") and voice use Google's
    # speech engine; you need a keyboard to sign in
    com.google.android.webview
    com.android.webview
    com.google.android.tts                         # on-device speech recognition and text-to-speech
    com.samsung.android.honeyboard                 # Samsung Keyboard (works before unlock, unlike most)

    # Clock: alarm tones, the lock screen clock, time zone data
    com.google.android.soundpicker
    com.samsung.android.app.aodservice
    com.samsung.android.timezone.autoupdate_N
    com.samsung.android.tzdata.update_M

    # Settings: Battery, Storage and Device care pages, biometrics page
    com.samsung.android.lool
    com.samsung.android.biometrics.app.setting

    # Security and system updates (Settings → Software update keeps working)
    com.samsung.sdm
    com.samsung.sdm.sdmviewer
    com.samsung.android.spdclient

    # Wi-Fi sign-in pages
    com.android.captiveportallogin
    com.google.android.captiveportallogin
)

# Removed too, on top of the list below. Put packages here from the "Not in the list" part of the
# preview once you've checked what they are.
EXTRA_REMOVE=(
)

# ---- Below here: how it works -----------------------------------------------------------------------

LOG_PREFIX="debloat"
MODE="preview"
SERIAL=""
RESTORE_LOG=""

usage() {
    sed -n '3,/^# Works with/p' "$0" | sed 's/^# \{0,1\}//'
    exit "${1:-0}"
}

while [ $# -gt 0 ]; do
    case "$1" in
        --apply) MODE="apply" ;;
        --restore) MODE="restore"; RESTORE_LOG="${2:-}"; shift ;;
        -s) SERIAL="${2:-}"; shift ;;
        -h|--help) usage 0 ;;
        *) echo "Unknown option: $1"; usage 1 ;;
    esac
    shift
done

if ! command -v adb >/dev/null 2>&1; then
    echo "adb isn't installed or isn't on your PATH. Install Android platform-tools first."
    exit 1
fi

ADB=(adb)
if [ -n "$SERIAL" ]; then
    ADB=(adb -s "$SERIAL")
fi

# Runs a command on the tablet; drops the carriage returns adb adds on some systems.
on_device() {
    "${ADB[@]}" shell "$@" </dev/null | tr -d '\r'
}

STATE="$("${ADB[@]}" get-state 2>&1 | tr -d '\r')"
if [ "$STATE" != "device" ]; then
    echo "No tablet ready: $STATE"
    echo "Connect it, turn on USB debugging, and accept the \"Allow USB debugging?\" prompt on its screen."
    echo "With more than one device connected, pick one with -s <serial> (see: adb devices)."
    exit 1
fi

DEVICE_SERIAL="$("${ADB[@]}" get-serialno | tr -d '\r')"
MODEL="$(on_device getprop ro.product.model)"
ANDROID="$(on_device getprop ro.build.version.release)"
ONEUI="$(on_device getprop ro.build.version.oneui)"
echo "Tablet: $MODEL ($DEVICE_SERIAL), Android $ANDROID${ONEUI:+, One UI $ONEUI}"
echo

# ---- Restore ------------------------------------------------------------------------------------------

if [ "$MODE" = "restore" ]; then
    if [ -z "$RESTORE_LOG" ] || [ ! -f "$RESTORE_LOG" ]; then
        echo "Give the log the --apply run wrote, e.g.: --restore ${LOG_PREFIX}-${DEVICE_SERIAL}-2026-11-04.log"
        exit 1
    fi
    restored=0
    failed=0
    while read -r action pkg; do
        case "$action" in
            removed|removed-app)
                out="$(on_device cmd package install-existing --user 0 "$pkg")"
                ;;
            disabled)
                out="$(on_device pm enable --user 0 "$pkg")"
                ;;
            *) continue ;;
        esac
        case "$out" in
            *"installed for user"*|*"new state: enabled"*|*Success*)
                echo "  back: $pkg"
                restored=$((restored + 1))
                ;;
            *)
                if [ "$action" = "removed-app" ]; then
                    echo "  reinstall from the Play Store: $pkg"
                else
                    echo "  couldn't restore $pkg: $out"
                fi
                failed=$((failed + 1))
                ;;
        esac
    done < "$RESTORE_LOG"
    echo
    echo "Restored $restored package(s); $failed need attention (above). Restart the tablet to finish."
    exit 0
fi

# ---- What's on the tablet, and what to do about it ----------------------------------------------------

WORK="$(mktemp -d 2>/dev/null || mktemp -d -t debloat)"
trap 'rm -rf "$WORK"' EXIT

# The lists embedded at the end of this file.
section() {
    awk -v name="$1" '$0 == "__" name "__" {on = 1; next} /^__[A-Z]+__$/ {on = 0} on && NF' "$0"
}
section REMOVE > "$WORK/list.txt"                         # package|tier|what it is
cut -d'|' -f1 "$WORK/list.txt" | sort -u > "$WORK/list-names.txt"
section KNOWN | sort -u > "$WORK/known.txt"               # known to UAD, left alone on purpose

# Installed for the main user (packages already removed don't show).
on_device pm list packages | sed 's/^package://' | sort -u > "$WORK/installed.txt"
on_device pm list packages -3 | sed 's/^package://' | sort -u > "$WORK/apps.txt"
comm -23 "$WORK/installed.txt" "$WORK/apps.txt" > "$WORK/system.txt"

if [ ! -s "$WORK/installed.txt" ]; then
    echo "Couldn't list the tablet's packages. Is the screen unlocked and USB debugging allowed?"
    exit 1
fi

# Protected: the list above, plus what this tablet actually uses right now.
: > "$WORK/protect.txt"
for pkg in "${PROTECT[@]}"; do
    echo "$pkg" >> "$WORK/protect.txt"
done

why_kept=""
note_kept() {
    # $1 package, $2 why. Remembers it for the preview.
    [ -n "$1" ] || return 0
    echo "$1" >> "$WORK/protect.txt"
    why_kept="${why_kept}  $1: $2
"
}

keyboard="$(on_device settings get secure default_input_method)"
note_kept "${keyboard%%/*}" "your keyboard"

voice="$(on_device settings get secure tts_default_synth)"
[ "$voice" = "null" ] || note_kept "$voice" "your text-to-speech engine"

# Lena hears you on the tablet itself through Android's on-device speech recognizer: Google's speech
# engine (kept above) or Android System Intelligence, whichever this tablet has. Without one she sends
# your voice to your server instead. (The Google app's recognizer is the online one: not needed.)
on_device cmd package query-services --brief -a android.speech.RecognitionService \
    | sed -n 's#^\(com\.google\.android\.as\)/.*#\1#p' | sort -u > "$WORK/hearing.txt"
while read -r pkg; do
    note_kept "$pkg" "speech recognition (Lena hears you on the tablet)"
done < "$WORK/hearing.txt"

webview="$(on_device dumpsys webviewupdate | sed -n 's/.*Current WebView package (name, version): (\([^,]*\),.*/\1/p' | head -1)"
note_kept "$webview" "the WebView Lena runs in"
grep -E '^com\.google\.android\.trichromelibrary' "$WORK/installed.txt" | while read -r lib; do
    echo "$lib" >> "$WORK/protect.txt"
done

home="$(on_device cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1)"
home="${home%%/*}"
note_kept "$home" "the home app"
if [ "$home" != "io.github.soullink9.lena" ]; then
    echo "Note: Lena isn't the home app yet (it's: ${home:-none chosen}). Make it the home app in Lena's"
    echo "Settings → Wall display before you remove things, so there's always a home screen."
    echo
fi

sort -u -o "$WORK/protect.txt" "$WORK/protect.txt"

# Firmware packages on the list, plus EXTRA_REMOVE, minus protected.
: > "$WORK/extra.txt"
for pkg in "${EXTRA_REMOVE[@]+"${EXTRA_REMOVE[@]}"}"; do
    echo "$pkg" >> "$WORK/extra.txt"
done
sort -u "$WORK/list-names.txt" "$WORK/extra.txt" > "$WORK/wanted.txt"
comm -12 "$WORK/system.txt" "$WORK/wanted.txt" | comm -23 - "$WORK/protect.txt" > "$WORK/remove-system.txt"

# Apps installed on top of the firmware, minus protected.
comm -23 "$WORK/apps.txt" "$WORK/protect.txt" > "$WORK/remove-apps.txt"

# Firmware packages nobody has rated: shown for review, left alone.
sort -u "$WORK/list-names.txt" "$WORK/known.txt" "$WORK/protect.txt" "$WORK/extra.txt" > "$WORK/rated.txt"
comm -23 "$WORK/system.txt" "$WORK/rated.txt" > "$WORK/unknown.txt"

describe() {
    # "  package   what it is (tier)" for each package in file $1.
    while read -r pkg; do
        line="$(awk -F'|' -v p="$pkg" '$1 == p {print; exit}' "$WORK/list.txt")"
        if [ -n "$line" ]; then
            tier="$(echo "$line" | cut -d'|' -f2)"
            what="$(echo "$line" | cut -d'|' -f3-)"
            printf '  %-58s %s (%s)\n' "$pkg" "$what" "$tier"
        else
            printf '  %-58s %s\n' "$pkg" "(you added it)"
        fi
    done < "$1"
}

count() { grep -c . "$1" | tr -d ' '; }

n_system="$(count "$WORK/remove-system.txt")"
n_apps="$(count "$WORK/remove-apps.txt")"
n_unknown="$(count "$WORK/unknown.txt")"

if [ "$MODE" = "preview" ]; then
    echo "== Would remove $n_system Samsung, Google and preinstalled packages"
    describe "$WORK/remove-system.txt"
    echo
    echo "== Would uninstall $n_apps app(s) installed on top (reinstall from the Play Store if wanted)"
    sed 's/^/  /' "$WORK/remove-apps.txt"
    echo
    echo "== Kept for this tablet"
    printf '%s' "$why_kept"
    echo
    echo "== Not in the list: $n_unknown firmware package(s) left alone"
    echo "   Mostly Android's own parts. Newer Samsung extras (Galaxy AI and so on) can show up here too:"
    echo "   look up any you don't recognise, and add the ones you want gone to EXTRA_REMOVE."
    sed 's/^/  /' "$WORK/unknown.txt"
    echo
    echo "Nothing was changed. To remove the $((n_system + n_apps)) above: $0 --apply${SERIAL:+ -s $SERIAL}"
    exit 0
fi

# ---- Apply --------------------------------------------------------------------------------------------

total=$((n_system + n_apps))
if [ "$total" -eq 0 ]; then
    echo "Nothing left to remove."
    exit 0
fi

echo "This removes $n_system firmware package(s) and uninstalls $n_apps app(s) from $MODEL."
echo "Run without --apply first to see the list."
printf 'Type REMOVE to go ahead: '
read -r answer
if [ "$answer" != "REMOVE" ]; then
    echo "Stopped. Nothing was changed."
    exit 1
fi

LOG="${LOG_PREFIX}-${DEVICE_SERIAL}-$(date +%Y-%m-%d-%H%M).log"
{
    echo "# wall-display-debloat.sh on $MODEL ($DEVICE_SERIAL), Android $ANDROID, $(date)"
    echo "# Undo with: $0 --restore $LOG${SERIAL:+ -s $SERIAL}"
} > "$LOG"

removed=0
disabled=0
failed=0

remove_one() {
    # $1 package, $2 "removed" or "removed-app" (what the log calls a success).
    out="$(on_device pm uninstall -k --user 0 "$1")"
    case "$out" in
        *Success*)
            echo "$2 $1" >> "$LOG"
            echo "  removed   $1"
            removed=$((removed + 1))
            return
            ;;
    esac
    # Some packages can't be uninstalled for a user; switching them off does nearly the same.
    out2="$(on_device pm disable-user --user 0 "$1")"
    case "$out2" in
        *"new state: disabled"*)
            echo "disabled $1" >> "$LOG"
            echo "  disabled  $1"
            disabled=$((disabled + 1))
            ;;
        *)
            echo "# couldn't remove $1: $out $out2" >> "$LOG"
            echo "  skipped   $1 (Android refused: $out)"
            failed=$((failed + 1))
            ;;
    esac
}

echo
echo "Removing firmware packages..."
while read -r pkg; do
    remove_one "$pkg" removed
done < "$WORK/remove-system.txt"

echo
echo "Uninstalling apps installed on top..."
while read -r pkg; do
    remove_one "$pkg" removed-app
done < "$WORK/remove-apps.txt"

echo
echo "Done: $removed removed, $disabled switched off, $failed refused by Android."
echo "The log is $LOG; keep it. To undo: $0 --restore $LOG${SERIAL:+ -s $SERIAL}"
echo
printf 'Restart the tablet now? [y/N] '
read -r answer
case "$answer" in
    y|Y|yes|YES) "${ADB[@]}" reboot && echo "Restarting." ;;
    *) echo "Restart it when you can; some removed services keep running until then." ;;
esac
exit 0

# ---- The lists ----------------------------------------------------------------------------------------
# Written by tools/debloat_lists.py from UAD's list; don't edit by hand (use PROTECT and EXTRA_REMOVE).
# REMOVE: package|UAD tier|what it is. KNOWN: rated by UAD and left alone on purpose.
: <<'__END__'
__REMOVE__
air.com.playtika.slotomania|Recommended|Slotomania  Preinstalled game on some Samsung phones.
android.auto_generated_rro__|Recommended|rro = Runtime Resources Overlay.
android.auto_generated_vendor_|Recommended|Auto generated vendor's stuff for Android Auto  Note: You don't need thi
android.autoinstalls.config.Nothing.Pong|Recommended|PlayAutoInstalls AutoInstalls a set of OEM apps on device setup (first b
android.autoinstalls.config.TCL.PAI|Recommended|AutoInstalls a set of OEM apps on device setup (first boot/factory reset
android.autoinstalls.config.Xiaomi.cactus|Recommended|Cactus is the device codename.
android.autoinstalls.config.Xiaomi.cepheus|Recommended|Cepheus is the device codename.
android.autoinstalls.config.Xiaomi.cereus|Recommended|Cepheus is the device codename.
android.autoinstalls.config.Xiaomi.daisy|Recommended|Daisy is the device codename.
android.autoinstalls.config.Xiaomi.dipper|Recommended|Dipper is the device codename.
android.autoinstalls.config.Xiaomi.land|Recommended|land is the device codename.
android.autoinstalls.config.Xiaomi.model|Recommended|PlayAutoInstalls AutoInstalls a set of OEM apps on device setup (first b
android.autoinstalls.config.Xiaomi.qssi|Recommended|PlayAutoInstalls AutoInstalls a set of OEM apps on device setup (first b
android.autoinstalls.config.Xiaomi.whyred|Recommended|App that configures preinstalled apps and initial preferences on Xiaomi
android.autoinstalls.config.Xiaomi.willow|Recommended|PlayAutoInstalls AutoInstalls a set of OEM apps on device setup (first b
android.autoinstalls.config.asus.pai|Recommended|PlayAutoInstalls AutoInstalls a set of OEM apps on device setup (first b
android.autoinstalls.config.google.nexus|Advanced|Auto-install configuration for Google apps on Nexus devices.
android.autoinstalls.config.google.sabrina|Recommended|AutoInstalls a set of OEM apps on device setup (first boot/factory reset
android.autoinstalls.config.lge.device|Recommended|PlayAutoInstalls AutoInstalls a set of OEM apps on device setup (first b
android.autoinstalls.config.motorola.layout|Recommended|Device configuration for Motorola AutoInstalls a set of OEM apps on devi
android.autoinstalls.config.oneplus|Recommended|Device configuration AutoInstalls a set of OEM apps on device setup (fir
android.autoinstalls.config.oppo|Recommended|PlayAutoInstalls AutoInstalls a set of OEM apps on device setup (first b
android.autoinstalls.config.samsung|Recommended|AutoInstalls a set of OEM apps on device setup (first boot/factory reset
android.autoinstalls.config.transsion.device|Recommended|PlayAutoInstalls AutoInstalls a set of OEM apps on device setup (first b
android.autoinstalls.config.vivo.devices|Recommended|PlayAutoInstalls AutoInstalls a set of OEM apps on device setup (first b
android.autoinstalls.config.xioami.mibox3|Recommended|AutoInstalls a set of OEM apps on device setup (first boot/factory reset
android.qvaoverlay.common|Advanced|This app has no code and is safe to remove.
android.ui.overlay.ct|Advanced|An overlay that customizes Android UI for the China Telecom operator (3r
ca.bell.selfserve.mybellmobile|Recommended|Bell's 'award winning' (no source) app for managing your Bell account an
ca.bell.wt.android.tunesappswidget|Recommended|App Widget Developped by Bell Canada, it is a home screen widget which s
cci.usage|Recommended|My Consumer Cellular  Lets you manage your Consumer Cellular account, tr
co.sitic.pp|Recommended|Designed to remotely lock the phone (by sending a simple SMS) in case yo
com.LogiaGroup.LogiaDeck|Recommended|Mobile Services Manager Seems to be a spyware.
com.Rogers.MyRogersTab|Recommended|Appears to be the tablet version of MyRogers  an app to manage your acco
com.UCMobile.intl|Recommended|UC Browser by Alibaba  !!
com.aaa.android.discounts|Recommended|AAA Mobile  Kind of GPS that helps you find Point of interest (POI) like
com.aaa.android.discounts.vpl|Recommended|AAA Mobile  Kind of GPS that helps you find Point of interest (POI) like
com.adups.fota.sysoper|Recommended|UpgradeSys FOTA = Firmware Over-the-air.
com.aetherpal.attdh.se|Recommended|Device Help for AT&T Samsung device Developed by Aetherpal, a company wh
com.aetherpal.attdh.zte|Recommended|Device Help for AT&T ZTE devices.
com.agui.toolbox|Recommended|Toolbox contains a bunch of small utilites, most have there own APP but
com.alibaba.aliexpresshd|Recommended|AliExpress shopping/marketplace.
com.altice.android.myapps|Recommended|MyApps it's probably for install apps but it's useless.
com.amazon.aa|Recommended|Amazon Assistant app.
com.amazon.aa.attribution|Recommended|Amazon Assistant Attribution.
com.amazon.appmanager|Recommended|Mobile Device Information Provider Seems related to Kindle
com.amazon.avod.thirdpartyclient|Recommended|Amazon Prime Video  VOD service from Amazon.
com.amazon.clouddrive.photos|Recommended|Amazon Photos
com.amazon.fv|Recommended|Amazon App suite.
com.amazon.kindle|Recommended|Amazon Kindle
com.amazon.mShop.android|Recommended|Amazon Shopping
com.amazon.mShop.android.shopping|Recommended|Amazon Shopping  Same package as com.amazon.mShop.android.
com.amazon.mShop.android.shopping.vpl|Recommended|Amazon Shopping  Same package as com.amazon.mShop.android.
com.amazon.mp3|Recommended|Amazon Music  Amazon streaming app
com.americanexpress.plenti|Recommended|Plenti Get points and promos with your American Express card
com.android.adservices.api|Recommended|Android AdServices.
com.android.apps.tag|Advanced|Support for NFC tags interactions (5 permissions, Contacts/Phone On by d
com.android.avatarpicker|Advanced|Lets you assign pictures to contacts.
com.android.backupconfirm|Expert|Restores Google settings with Google Backup restore.
com.android.bluetoothmidiservice|Advanced|Provides classes for using the MIDI protocol over Bluetooth.
com.android.bookmarkprovider|Advanced|Only exists for compatibility reasons to prevent apps querying it from g
com.android.browser|Advanced|Mi Browser and browser for the LDPlayer emulator You really should use s
com.android.browser.provider|Advanced|Old package (2014).
com.android.calculator2|Advanced|AOSP calculator app NOTE: Some OEMs (like Huawei & Xiaomi) use the same
com.android.calendar|Advanced|AOSP Calendar app NOTE: Some OEMs (like Huawei & Xiaomi) use the same pa
com.android.calllogbackup|Advanced|Call Logs Backup/Restore feature.
com.android.camera|Advanced|The stock AOSP camera app on many phones.
com.android.carrierconfig|Advanced|Dynamically provides configuration for the carrier network.
com.android.carrierconfig.overlay.common|Advanced|Needed for (com.android.carrierconfig).
com.android.carrierdefaultapp|Advanced|This package is a generic solution that allows carriers to indicate when
com.android.cellbroadcastreceiver.overlay.common|Advanced|In code I found unused things: show_brazil_settings, show_cmas_settings,
com.android.chrome|Advanced|Google Chrome  Occasionally runs in the background.
com.android.companiondevicemanager|Advanced|Companion Device Manager This handles connections to nearby (usually not
com.android.contacts|Advanced|AOSP Contacts Some OEMs(for example Xiaomi) use the same package name fo
com.android.credentialmanager|Advanced|Credential Manager Manages with Passwords, passkeys.
com.android.cts.ctsshim|Advanced|Compatibility Test Suite Used by manufacturer to test your copy of the d
com.android.deskclock|Advanced|AOSP Clock app Some OEMs (like Huawei & Xiaomi) use the same package nam
com.android.dreams.basic|Advanced|Daydream (not Google Daydream VR) is an interactive screensaver mode bui
com.android.dreams.phototable|Advanced|Photographic screensavers Daydream stuff, see com.android.dreams.basic
com.android.dreams.phototable.overlay|Advanced|Overlay for the phototable daydream?
com.android.dynsystem|Expert|Dynamic System Updates Runs on boot, but doesn't seem to run in the back
com.android.egg|Recommended|Android's easter egg feature (spam-tap on the android version in the set
com.android.email|Recommended|Xiaomi closed-source email app based on the AOSP version.
com.android.email.partnerprovider|Recommended|Lets Google partners (OEM in most of the case) to customize the default
com.android.emergency|Advanced|Emergency rescue Shows emergency info on lockscreen and power menu.
com.android.exchange|Advanced|Handles all aspects of starting, maintaining, and stopping the various s
com.android.ext.adservices.api|Recommended|Another component of Android AdServices.
com.android.facelock|Advanced|Face Unlock Safe to remove if you don't use Face Unlock.
com.android.federatedcompute.services|Recommended|FederatedCompute Another component of OnDevicePersonalization.
com.android.fmradio|Advanced|FM Radio Plug in Head phones & listen to the Radio!
com.android.frameworks.telresources|Recommended|Completely empty package.
com.android.galaxy4|Recommended|Built-in Dynamic wallpaper
com.android.gallery3d|Advanced|Huawei Gallery app and the AOSP Gallery app It may appear on Chinese OEM
com.android.health.connect.backuprestore|Advanced|Backups data from Health Connect app.
com.android.healthconnect.controller|Advanced|Health Connect Manage the health and fitness data on your phone, and con
com.android.hotspot2.osulogin|Advanced|The sole purpose of the OsuLogin App is to provision credentials from th
com.android.hotwordenrollment.okgoogle|Recommended|"OK Google" detection service.
com.android.hotwordenrollment.xgoogle|Recommended|"OK Google" detection service.
com.android.inputmethod.latin|Advanced|The AOSP keyboard app Make sure you have another installed before you di
com.android.internal.systemui.onehanded.gestural|Advanced|one-handed mode, which can be found in the settings.
com.android.ld.appstore|Recommended|LD Gaming Appstore LDPlayer is an Android Gaming emulator for PC
com.android.magicsmoke|Recommended|"Magic smoke" Live wallpaper.
com.android.mms|Advanced|AOSP SMS app.
com.android.musicfx|Advanced|Audio EQ(equalizer).
com.android.musicvis|Recommended|Music Visualization Wallpapers
com.android.nearby.halfsheet|Recommended|Useless frameworks to Wi-Fi connections, USB tethering, auto, usage.
com.android.noisefield|Recommended|"Noise filed" live wallpaper.
com.android.ondevicepersonalization.services|Recommended|OnDevicePersonalization.
com.android.onetimeinitializer|Recommended|Needed for first time setup.
com.android.partnerbrowsercustomizations.chromeHomepage|Recommended|Horrible stuff for Google Chrome.
com.android.partnerbrowsercustomizations.tmobile|Recommended|The proprietary application of the mobile operator T-Mobile, presumably
com.android.phasebeam|Recommended|"Phase beam" live wallpaper
com.android.phasebeamorange|Recommended|"Sun Beam" live wallpaper.
com.android.phone.recorder|Advanced|AOSP Call recorder function.
com.android.printservice.recommendation|Advanced|Recommends 3rd-party print services apps in the PlayStore.
com.android.protips|Recommended|Home screen tips Runs on boot.
com.android.providers.downloads.ui|Advanced|Downloads User interface for downloads.
com.android.providers.partnerbookmarks|Recommended|Provides bookmarks about partners of Google in Chrome.
com.android.providers.partnerbrowsercustomizations|Recommended|Provides browser customizations from partners (for chrome only).
com.android.quicksearchbox|Recommended|Google quick search box.
com.android.remoteprovisioner|Recommended|RemoteProvisioner.
com.android.safetycenter.resources|Advanced|Google/AOSP Safety Center resources.
com.android.se.overlay.target|Advanced|Looks like needed to 'com.android.se'.
com.android.sharedstoragebackup|Advanced|Used during backup.
com.android.smspush|Advanced|This service is used to push/send specially formatted SMS messages that
com.android.soundrecorder|Advanced|AOSP Sound recorder OEMs often use their own solution NOTE: On some phon
com.android.sprint.hiddenmenuapp|Recommended|Lets you access hidden features tests/settings (you need to type a speci
com.android.stk|Advanced|SIM toolkit Enables carriers to initiate "value-added services".
com.android.stk2|Advanced|SIM toolkit Special package for dual-sim devices?
com.android.storagemanager|Advanced|Storage manager (Maintenance/Storage panel in the settings) Clean up unu
com.android.systemui.accessibility.accessibilitymenu|Recommended|Hidden menu that only shows 2 buttons: Large buttons - that increases si
com.android.systemui.accessibility.accessibilitymenu.auto_generated_rro_product__|Advanced|Product RRO for Accessibility menu.
com.android.systemui.plugin.globalactions.wallet|Advanced|Apk file name: QuickAccessWallet This is the Google Pay widget in the po
com.android.theme.font.notoserifsource|Recommended|Noto Serif / Source Sans Pro
com.android.theme.icon.circle|Recommended|Android icon shape only for google pixel or aosp or motorola
com.android.theme.icon.pebble|Recommended|Android icon shape only for google pixel or aosp or motorola
com.android.theme.icon.square|Recommended|Android icon shape only for google pixel or aosp or motorola
com.android.theme.icon.taperedrect|Recommended|Android icon shape only for google pixel or aosp or motorola
com.android.theme.icon.vessel|Recommended|Android icon shape only for google pixel or aosp or motorola
com.android.theme.icon_pack.rounded.systemui|Recommended|Android icons pack [Rounded].
com.android.theme.icon_pack.rounded.themepicker|Recommended|Obviously related to the "rounded" icon pack but the full package is str
com.android.traceur|Recommended|System Tracing Recording device activity over a short period of time is
com.android.voicedialer|Advanced|AOSP Voice dialer.
com.android.wallpaper.holospiral|Advanced|"Holo spiral" Live wallpaper.
com.android.wallpaper.livepicker|Advanced|Enables you to pick a live wallpaper.
com.android.wallpaper.livepicker.overlay|Advanced|Overlay for live wallpaper picker?
com.android.wallpaperbackup|Advanced|Wallpaper Backup Backs up and restores wallpaper and metadata related to
com.android.wallpapercropper|Advanced|Wallpaper cropper.
com.android.wallpaperpicker|Advanced|Enables you to pick a wallpaper.
com.android.wifi.resources.overlay.WifiResScanCountryCode|Advanced|Related to overlay for Wi-Fi scanning for country code.
com.applovin.array.apphub.vincere|Recommended|AppHub Is known as tracker company, potentially adware!
com.aspiro.tidal|Recommended|Tidal Music  Music streaming app
com.aspiro.tidal.vpl|Recommended|Tidal Music  Music streaming app
com.asurion.android.mobilerecovery.att|Recommended|AT&T Protect Plus (discontinued.
com.asurion.android.mobilerecovery.sprint|Recommended|Sprint Protect Support app (see com.asurion.android.protech.att)
com.asurion.android.mobilerecovery.sprint.vpl|Recommended|Sprint Protect Support app (see com.asurion.android.protech.att)
com.asurion.android.protech.att|Recommended|AT&T ProTech  Help and support app.
com.asurion.android.verizon.vms|Recommended|Verizon Digital Secure  Note : This app is developed by Asurion, a US co
com.asurion.home.sprint|Recommended|Sprint Protect Support app (see com.asurion.android.protech.att) Sprint
com.asurion.home.sprint.vpl|Recommended|Tech Expert (for VPL devices/employees) ?
com.att.android.attsmartwifi|Recommended|AT&T Smart Wi-Fi  Finds and auto-connects to available hotspots to minim
com.att.callprotect|Recommended|AT&T Call Protect  Spam call blocking app provided by Hiya NOTE : You sh
com.att.csoiam.mobilekey|Recommended|AT&T Sign in Helper  Allows AT&T applications to securely authenticate o
com.att.deviceunlock|Recommended|Device Unlock Useless app from AT&T.
com.att.dh|Recommended|Device Help  Troubleshooting app.
com.att.dtv.shaderemote|Recommended|DIRECTV Remote App  Lets you control DIRECTV HD receivers in your home t
com.att.iqi|Recommended|Carrier IQ / Device Health Gathers, stores and forwards diagnostic measu
com.att.mobile.android.vvm|Recommended|AT&T Visual Voicemail  Lets you manage your voicemail directly from the
com.att.mobilesecurity|Recommended|AT&T Mobile Security  AT&T android antivirus.
com.att.mobiletransfer|Recommended|AT&T Mobile Transfer Lets you transfert user data from an older AT&T pho
com.att.myWireless|Recommended|My AT&T  Lets you manage your AT&T account.
com.att.personalcloud|Recommended|AT&T Personal Cloud  Note: it's a paid extra feature and data are obviou
com.att.tv|Recommended|AT&T TV  Lets you Stream TV live and on demand from your phone.
com.att.tv.watchtv|Recommended|AT&T WatchTV  Lets you stream TV live and VOD form your phone.
com.audible.application|Recommended|Cover Audible Audiobooks
com.aura.jet.att|Recommended|AT&T Hub App from AT&T.
com.aura.oobe.att|Recommended|AppCloud App from AT&T.
com.aura.oobe.deutsche|Recommended|Provides an Out Of The Box experience.
com.aura.oobe.kddi|Recommended|AppCloud Seems to exist to try to get you to download games and another
com.aura.oobe.motorola|Recommended|MotoApps It's something to install apps, but it's an advertising company
com.aura.oobe.ntt|Recommended|Aura from Unity Persistent notification until you click on it and agree
com.aura.oobe.rakuten|Recommended|Aura from Unity Persistent notification until you click on it and agree
com.aura.oobe.samsung|Recommended|AppCloud It offers the "Aura Out-Of-the-Box Experience" (OOBE) It is som
com.aura.oobe.samsung.gl|Recommended|AppCloud It offers the "Aura Out-Of-the-Box Experience" (OOBE) It is som
com.aura.oobe.softbank|Recommended|Aura from Unity Persistent notification until you click on it and agree
com.aura.oobe.vodafone|Recommended|Vodafone AppBox It is some kind of post-install recommended apps setup f
com.bc360.android.service|Recommended|Verizon Adaptive Sound Provides Voice Enhance, but according to the carr
com.bc360.control|Recommended|Verizon Adaptive Sound Provides Voice Enhance, but according to the carr
com.bell.ptt|Recommended|Bell Push to Talk app.
com.bitdefender.security.orange|Recommended|Orange Antivirus (powered by Bitdefender)
com.bleacherreport.android.teamstream|Recommended|Bleacher Report  Sports news site
com.block.juggle|Recommended|Block Blast.
com.block.puzzle.game.hippo.mi|Recommended|Block Puzzle Guardian.
com.blurb.checkout|Recommended|Blurb Checkout Provides book purchase and checkout for Samsung’s Story A
com.booking|Recommended|Booking.com app  Seriously, you shouldn't use it !
com.caf.fmradio|Recommended|
com.cequint.ecid|Recommended|Caller ID from Cequint   NOTE : Never trust a company which promotes cal
com.chrome.beta|Recommended|Google Chrome Beta
com.chrome.canary|Recommended|Google Chrome Canary (Nightly build)
com.chrome.dev|Recommended|Google Chrome (developer)
com.claroColombia.contenedor|Recommended|Claro It's something to install apps, but it's an advertising company.
com.cnn.mobile.android.phone|Recommended|CNN Breaking US & World News app
com.communitake.remotecontrolservice|Recommended|Remote management app installed by Bell on some devices.
com.contextlogic.wish|Recommended|Wish Shopping
com.cootek.smartinputv5.language.englishgb|Recommended|TouchPal Keyboard by Cootek a chinese company.
com.cootek.smartinputv5.language.spanishus|Recommended|TouchPal Keyboard by Cootek a chinese company.
com.cricketwireless.minus|Recommended|Cricket partner tab?
com.crowdcare.agent.k|Recommended|Crowdcare is now Wysdom.AI From their Twitter description: the easiest w
com.customermobile.preload.vzw|Recommended|Verizon Store/Retail Demo Mode This application requires a lot of permis
com.dailymotion.dailymotion|Recommended|Dailymotion Online video sharing platform.
com.davincikids.tv|Recommended|Davinci Media A 2 star streaming service.
com.debug.loggerui|Recommended|DebugLoggerUI Similar to logcat, in some sense.
com.devhd.feedly|Recommended|Feedly  News aggregator application (RSS) RSS aggregator are great but F
com.digitalturbine.android.apps.news.att|Recommended|Essentials This application results in fullscreen advertisements upon un
com.digitalturbine.toolbar|Recommended|Digital Turbine .
com.diotek.diodict4.EDictionary|Recommended|Dictionary app, only for japanese and korea
com.diotek.sec.lookup.dictionary|Recommended|Samsung dictionary from Diotek (Korean company)
com.directv.dvrscheduler|Recommended|DIRECTV  Offical app from DIRECTV (subsidiary of AT&T) Lets you watch Li
com.directv.promo.shade|Recommended|DIRECTV Remote Official app from DIRECTV (subsidiary of AT&T).
com.discoveryscreen|Recommended|Appflash  Verizon Spyware.
com.dna.solitaireapp|Recommended|Solitaire Game app from DNA company ?
com.dot.app.sancharsaathi|Recommended|Spyware billed as citizen-centric safety tool that is required by Goverm
com.draftkings.dknativermgGP.vpl|Recommended|DraftKings - Daily Fantasy Sports for Cash App has been removed from the
com.drivemode|Recommended|DraftKings - Daily Fantasy Sports for Cash App has been removed from the
com.dsi.ant.plugins.antplus|Recommended|ANT+ plugin service
com.dsi.ant.sample.acquirechannels|Recommended|I don't know why there is "sample" in the name.
com.dsi.ant.server|Recommended|ANT Radio Service Test.
com.dti.amx|Recommended|it's used to choose app install?
com.dti.att|Recommended|AT&T App Select I guess it lets you choose AT&T apps to install.
com.dti.bouyguestelecom|Recommended|Bouygues AppCloud it's probably for install apps but it's useless and ha
com.dti.cricket|Recommended|it's app for installing recommended apps?
com.dti.motorola|Recommended|Mobile Services Manager it's something for install apps but it's useless
com.dti.samsung|Recommended|Mobile Services Manager Digital Turbine app, pre-install some apps/games
com.dti.telefonica|Recommended|O2 & Telefonica carrier bloat.
com.dti.tim|Recommended|Mobile Service Manager It's a system app that can't be opened but keeps
com.dti.tracfone|Recommended|Installs sponsored apps automatically on Tracfone and affiliated carrier
com.eamobile.sims3_carrier|Recommended|Sims 3
com.ebay.carrier|Recommended|Kind of weird ebay apps pre-installed by carriers.
com.ebay.mobile|Recommended|Ebay app
com.ehernandez.radiolainolvidable|Recommended|Radio La Inolvidable Peru (no longer exist) Spanish Radio app.
com.emoji.keyboard.touchpal|Recommended|TouchPal Emoji Keyboard by Cootek a chinese company Adware (lots and lot
com.eterno|Recommended|Daily hunts News.
com.evernote|Recommended|Evernote app
com.evernote.promohelper|Recommended|Companion for the Evernote app, seems to drain battery.
com.example|Recommended|Auto Dialer test
com.example.android.notepad|Advanced|(Bad) notepad app.
com.example.myapplication|Recommended|Hidden app for testing: Battery, WiFi, Bluetooth
com.facebook.appmanager|Recommended|Facebook app manager handles Facebook apps updates.
com.facebook.katana|Recommended|Facebook app
com.facebook.lite|Recommended|Facebook Lite app
com.facebook.orca|Recommended|Facebook Messenger
com.facebook.services|Recommended|Facebook Services is a tool that lets you manage different Facebook serv
com.facebook.system|Recommended|Facebook App Installer (empty shell app which incites you to install the
com.fet.fridaywallet|Recommended|friDay Wealth Management Provides customized deposit goal progress manag
com.finshell.fin|Recommended|FinShell Pay  Provides various Payment and Financial Services.
com.fintech.life|Recommended|Chinese app that spoofs as a Singapore financial and/or payment app to s
com.fivemobile.myaccount|Recommended|App for managing your Rogers account.
com.flipkart.android|Recommended|Pre-installed Indian shopping bloatware with ads.
com.focaltech.fingerprint|Recommended|Another fingerprint sensor test tool but Chinese.
com.focaltech.fpsensormmitest|Recommended|Related to `com.focaltech.fingerprint`.
com.fw.upgrade.sysoper|Recommended|UpgradeSys FOTA = Firmware Over-the-air.
com.galaxyfirsatlari|Recommended|Galaxy Fırsatları  Samsung-only app for Turkish people Recommands you st
com.generalmobi.go2pay|Recommended|Go2Pay  Payment app that offers mobile pre-paid recharges and post-paid
com.glance.internet|Recommended|Displays unsolicited "trending" stories on Lockscreen
com.gohappy.mobileapp|Recommended|Shopping app of friDay
com.google.android.GoogleCamera|Advanced|Google Camera
com.google.android.accessibility.soundamplifier|Recommended|Accessibility sound amplifier
com.google.android.adservices.api|Recommended|Introduced in Android 13 privacy sandbox beta components disabled on def
com.google.android.androidforwork|Recommended|Assistant Android Work Not needed, theres only user consent activity abo
com.google.android.apps.access.wifi.consumer|Recommended|Google Wifi app
com.google.android.apps.adm|Recommended|Google Find my device app
com.google.android.apps.ads.publisher|Recommended|Google Adsense app
com.google.android.apps.adwords|Recommended|Google Ads app
com.google.android.apps.aiwallpapers|Recommended|Provides the ability to create wallpapers by using AI and several preset
com.google.android.apps.assistant|Recommended|Google Assistant Go  Go apps are lite-versions of their normal counterpa
com.google.android.apps.authenticator2|Recommended|Google Authenticator app
com.google.android.apps.bard|Recommended|Google Gemini, safe to remove
com.google.android.apps.blogger|Recommended|Google blogger app
com.google.android.apps.books|Recommended|Google Play Books
com.google.android.apps.cameralite|Recommended|Camera Go.
com.google.android.apps.chromecast.app|Recommended|Google Home
com.google.android.apps.cloudprint|Recommended|Cloud print
com.google.android.apps.cultural|Recommended|Google Arts & Culture
com.google.android.apps.currents|Recommended|Google Currents (discontinued)
com.google.android.apps.docs|Recommended|Google Drive
com.google.android.apps.docs.editors.docs|Recommended|Google Docs
com.google.android.apps.docs.editors.sheets|Recommended|Google Sheets
com.google.android.apps.docs.editors.slides|Recommended|Google Slides
com.google.android.apps.dynamite|Recommended|Hangout chat (discontinued)
com.google.android.apps.enterprise.cpanel|Recommended|Google Admin
com.google.android.apps.enterprise.dmagent|Recommended|Google apps device policy
com.google.android.apps.fireball|Recommended|Google Allo (discontinued)
com.google.android.apps.fitness|Recommended|Google Fit
com.google.android.apps.freighter|Recommended|Google Datally (discontinued)
com.google.android.apps.gcs|Advanced|Internet services for Pixel and Nexus devices, including Wi-Fi/Mobile da
com.google.android.apps.genie.geniewidget|Recommended|News and weather widget.
com.google.android.apps.giant|Recommended|Google Analytics
com.google.android.apps.googleassistant|Recommended|Google Assistant
com.google.android.apps.googlecamera.fishfood|Advanced|ApertureLensLauncher Not sure how it works but redirects to google app l
com.google.android.apps.handwriting.ime|Recommended|Google Handwriting Input
com.google.android.apps.hangoutsdialer|Recommended|Google Hangout Dialer
com.google.android.apps.inbox|Recommended|Inbox by Gmail (Discontinued)
com.google.android.apps.inputmethod.hindi|Advanced|Google Keyboard + Hinndi characters
com.google.android.apps.kids.familylink|Recommended|Google Family Link
com.google.android.apps.kids.familylinkhelper|Recommended|Google Family Link for children & teens
com.google.android.apps.m4b|Recommended|Google My Maps  It is NOT Google Maps
com.google.android.apps.magazines|Recommended|Google News
com.google.android.apps.maps|Recommended|Google Maps
com.google.android.apps.mapslite|Recommended|Google Maps Go (lite web app of Maps)
com.google.android.apps.mediahome.launcher|Recommended|Entertainment Space All-in-one application for entertainment purposes li
com.google.android.apps.meetings|Recommended|Hangout Meet (discontinued)
com.google.android.apps.messaging|Advanced|Google Messages  SMS and RCS app.
com.google.android.apps.navlite|Recommended|Google Maps GPS
com.google.android.apps.nbu.files|Recommended|Files by Google  Runs in the background.
com.google.android.apps.nbu.paisa.user|Recommended|Google Pay  Digital wallet and payment system List of all the informatio
com.google.android.apps.paidtasks|Recommended|Google Opinion Rewards
com.google.android.apps.pdfviewer|Advanced|Google PDF Viewer Shouldn't run in the background, so no reason to disab
com.google.android.apps.photos|Recommended|Google Photos
com.google.android.apps.photos.scanner|Recommended|Google PhotoScan
com.google.android.apps.photosgo|Recommended|Google Gallery
com.google.android.apps.pixel.subzero|Recommended|Daily Hub - useless a general-purpose news app
com.google.android.apps.plus|Recommended|Google+ (discontinued)
com.google.android.apps.podcasts|Recommended|Google Podcasts
com.google.android.apps.privacy.wildlife|Recommended|VPN by Google One.
com.google.android.apps.recorder|Recommended|Google (audio)Recorder
com.google.android.apps.restore|Recommended|The backup restore wizard used for pulling Android system backups from y
com.google.android.apps.safetyhub|Advanced|Personal Safety  Personal Safety is a Pixel app that helps you prepare a
com.google.android.apps.santatracker|Recommended|Google Santa Tracker (discontinued)
com.google.android.apps.scone|Recommended|Automatically switches basebands between LTE and 5G on demand
com.google.android.apps.searchlite|Recommended|Google Go  Go apps are lite-versions of their normal counterparts, made
com.google.android.apps.security.securityhub|Recommended|Checks security your phone(you can find it in settings).
com.google.android.apps.setupwizard.searchselector|Recommended|Search engine selector The search selection screen in the setupwizard yo
com.google.android.apps.speechservices|Recommended|Google Speech Services Absolute garbage.
com.google.android.apps.subscriptions.red|Recommended|Google One  Lets you manage your Google cloud storage.
com.google.android.apps.tachyon|Recommended|Google Duo (Video Calls)
com.google.android.apps.tasks|Recommended|Google Task (TODO list)
com.google.android.apps.translate|Recommended|Google Translate
com.google.android.apps.travel.onthego|Recommended|Google Trip (discontinued)
com.google.android.apps.tycho|Recommended|Google Fi Wireless
com.google.android.apps.uploader|Recommended|Picasa Uploader (discontinued)
com.google.android.apps.vega|Recommended|Google My Business
com.google.android.apps.walletnfcrel|Recommended|Google Pay
com.google.android.apps.wallpaper|Advanced|Google Wallpapers  Removes the wallpaper section in the settings app whe
com.google.android.apps.weather|Recommended|The new Weather app by Google.
com.google.android.apps.wellbeing|Advanced|Digital Wellbeing  Lets you track device and app usage and set usage lim
com.google.android.apps.work.oobconfig|Advanced|Device Setup Sets up device to be managed by EMM (Enterprise Mobility Ma
com.google.android.apps.youtube.creator|Recommended|YouTube Studio
com.google.android.apps.youtube.gaming|Recommended|YouTube Gaming -(discontinued in March 2019, features integrated in main
com.google.android.apps.youtube.kids|Recommended|YouTube Kids
com.google.android.apps.youtube.mango|Recommended|YouTube Go  Lite version of the YouTube app.
com.google.android.apps.youtube.music|Recommended|YouTube Music
com.google.android.apps.youtube.vr|Recommended|YouTube VR
com.google.android.as|Recommended|Android System Intelligence (previously Device Personalization Services)
com.google.android.as.oss|Recommended|Private Compute Services.
com.google.android.backup|Recommended|Allows Android apps to back up their data on Google servers.
com.google.android.backuptransport|Advanced|Allows Android apps to back up their data on Google servers.
com.google.android.calculator|Recommended|Google Calculator
com.google.android.calendar|Recommended|Google Calendar
com.google.android.carrierconfig|Advanced|Same as com.android.carrierconfig?
com.google.android.contactkeys|Expert|Android System Key Verifier is a system service for verifying interlocut
com.google.android.contacts|Advanced|Google Contacts  Occasionally runs in the background.
com.google.android.deskclock|Advanced|Google clock app  WARNING: on some phones, removing this makes it so ala
com.google.android.dialer|Advanced|Google Dialer  Default dialer on some phones.
com.google.android.email|Recommended|AOSP Mail client Newer versions of AOSP Mail are renamed to com.android.
com.google.android.federatedcompute|Recommended|FederatedCompute.
com.google.android.feedback|Recommended|This is the package that sends crash-report feedback to the Play Store?
com.google.android.gallery3d|Advanced|Built-in Gallery app.
com.google.android.gm|Recommended|Gmail
com.google.android.gm.exchange|Recommended|Microsoft Exchange ActiveSync support for Gmail.
com.google.android.gm.lite|Recommended|Gmail Go  Lite version of the Gmail app (hah!
com.google.android.gms.location.history|Recommended|Google Location History This app has nothing in the code.
com.google.android.gms.policy_sidecar_aps|Advanced|Not sure what purpose it has, but it gets some network and phone data an
com.google.android.gms.supervision|Recommended|Family Link parental controls It has something to Family Link parental c
com.google.android.googlequicksearchbox|Advanced|Google Search box  Runs in the background.
com.google.android.health.connect.backuprestore|Advanced|Health Connect for Android backup/restore.
com.google.android.healthconnect.controller|Advanced|Health Connect by Android gives you a simple way to share data between y
com.google.android.hotspot2.osulogin|Advanced|The sole purpose of the OsuLogin App is to provision credentials from th
com.google.android.ims|Advanced|Carrier Services  Runs in the background.
com.google.android.inputmethod.japanese|Advanced|Google Keyboard + Japanese characters
com.google.android.inputmethod.korean|Advanced|Google Keyboard + Korean characters
com.google.android.inputmethod.latin|Advanced|Gboard – the Google Keyboard  Sometimes the only keyboard app on a phone
com.google.android.inputmethod.pinyin|Advanced|Google Keyboard + Pinyin (Chinese) characters
com.google.android.instantapps.supervisor|Recommended|Lets you try new games directly on Google Play.
com.google.android.keep|Recommended|Google Keep
com.google.android.launcher|Advanced|Google Now Launcher  DO NOT REMOVE THIS IF YOU DON'T HAVE ANOTHER LAUNCH
com.google.android.location|Advanced|Handles location services on older devices.
com.google.android.markup|Advanced|Google Markup app made for modifying pictures, shipped by default on eve
com.google.android.marvin.talkback|Advanced|Android Accessibility Suite  Helps blind and visually impaired users.
com.google.android.music|Recommended|Google Play Music (discontinued)
com.google.android.nearby.halfsheet|Recommended|Useless frameworks to Wi-Fi connections, USB tethering, auto, usage.
com.google.android.ondevicepersonalization.services|Recommended|OnDevicePersonalization.
com.google.android.onetimeinitializer|Recommended|Provides first time setup, safe to remove.
com.google.android.overlay.gmsconfig|Advanced|Useless configurations about webview, wifi and bluetooth to scan for bet
com.google.android.overlay.gmsconfig.asi|Advanced|it's another component of Android System Intelligence (previously Device
com.google.android.overlay.gmsconfig.gsa|Advanced|configures default assistant?
com.google.android.overlay.gmsconfig.ww|Advanced|useless unused overlay gmsconfig to family link.
com.google.android.overlay.modules.captiveportallogin.forframework|Advanced|Configs default captiveportallogin.
com.google.android.overlay.modules.healthfitness.forframework|Recommended|Another part of health fitness system, useless for most users along with
com.google.android.overlay.modules.permissioncontroller|Advanced|I only detect on this app: help_app_permissions (link below).
com.google.android.overlay.setupwizard|Advanced|An overlay for com.google.android.setupwizard.
com.google.android.partnersetup|Advanced|Google Partner Setup Occasionally runs in the background.
com.google.android.pixel.setupwizard|Advanced|It's the basic configuration setup guides you through the basics of sett
com.google.android.play.games|Recommended|Google Play Games
com.google.android.printservice.recommendation|Advanced|I think this has to do with recommending a printservice app you can get
com.google.android.projection.gearhead|Advanced|Android Auto  No direct replacement appears to exist.
com.google.android.settings.intelligence|Advanced|Setting Services.
com.google.android.setupwizard.a_overlay|Advanced|Overlay for setupwizard?
com.google.android.speech.pumpkin|Advanced|PumpkinService Has something to speech.
com.google.android.street|Recommended|Google Street View
com.google.android.syncadapters.bookmarks|Recommended|Synchronisation for Google Chrome bookmarks
com.google.android.syncadapters.calendar|Advanced|Synchronisation for Google Calendar.
com.google.android.syncadapters.contacts|Advanced|Synchronisation for Google Contacts.
com.google.android.tag|Recommended|Support for NFC tags interactions (5 permissions : Contacts/Phone On by
com.google.android.talk|Recommended|Google Hangouts
com.google.android.turboadapter|Recommended|Device Health Services Adapter Another app for Device Health Services(di
com.google.android.tv.remote|Recommended|Android TV remote control
com.google.android.videoeditor|Recommended|Google Movie Studio (discontinued)
com.google.android.videos|Recommended|Google TV (previously Google Play Movies & TV)
com.google.android.voicesearch|Recommended|Google Voice Search (Speech-To-Text)
com.google.android.vr.home|Recommended|Daydream (VR stuff)
com.google.android.vr.inputmethod|Recommended|Daydream virtual keyboard (VR stuff)
com.google.android.wearable.app|Recommended|Wear OS Smartwatch
com.google.android.wifi.resources.overlay.common|Advanced|An overlay-google addition for `com.android.wifi.resources`, presumably
com.google.android.youtube|Recommended|YouTube
com.google.ar.core|Recommended|Google Play Services for AR (Augmented Reality)  Note: Disabling it can
com.google.ar.lens|Recommended|Google Lens (for AR too)
com.google.audio.hearing.visualization.accessibility.scribe|Advanced|Live Transcribe & Sound Notifications  Provides push notifications for c
com.google.chromeremotedesktop|Recommended|Chrome Remote Desktop
com.google.earth|Recommended|Google Earth
com.google.mainline.adservices|Recommended|Adservices Train Version Package.
com.google.marvin.talkback|Recommended|Android Accessibility Suite
com.google.omadm.trigger|Recommended|OemDmTrigger OMA Device Managment Verizon.
com.google.samples.apps.cardboarddemo|Recommended|Google Cardboard (VR stuff)
com.google.tango.measure|Recommended|Google Measure
com.google.vr.cyclops|Recommended|Google Cardboard Camera (VR stuff)
com.google.vr.expeditions|Recommended|Google Expedition (VR stuff)
com.google.vr.vrcore|Advanced|Google VR services
com.google.zxing.client.android|Recommended|Google Barcode Scanner (discontinued)
com.gotv.nflgamecenter.us.lite|Recommended|Football NFL
com.groupon|Recommended|Groupon  Online shopping deals and coupons.
com.hancom.office.editor.hidden|Recommended|Legacy Hancom Office Editor (Korean alternative to Microsoft Office).
com.handmark.expressweather|Recommended|1Weather  Forecasts alerts app (contain ads)
com.handmark.expressweather.vpl|Recommended|1Weather  Forecasts alerts app (contain ads)
com.haoba.btsmart|Recommended|Agui Unibuds Iot stuff.
com.haokan.pictorial|Recommended|92 Lock Screen for RealMe 92 is pronounce in Chinese as 'Hao Kan' (it's
com.hp.android.printservice|Recommended|Print to your HP printer using your Android phone.
com.huaqin.FM|Recommended|Radio app from huaqin a chinese company NOTE : Transistor [ is much bett
com.huaqin.batteryinfo|Recommended|Tests hardware things.
com.huaqin.clearefs|Recommended|Debugging logs Wifi, Bluetooth
com.huaqin.pocketbanreceiver|Recommended|I found in code useless permissions BAN_BROADCAST and hello world!
com.huaqin.sarcontroller|Recommended|It have something like sar sensor, idk if it's for testing but mainactiv
com.huaqin.shutdownservice|Recommended|ShutdownAfterScreenOff10MinutesService.
com.hulu.plus|Recommended|Hulu  Netflix competitor.
com.hyperlync.Sprint.Backup|Recommended|Sprint Backup"  Lets you backup your phone’s content to your Sprint Back
com.hyperlync.Sprint.CloudBinder|Recommended|Sprint Cloud Binder  Hub for all you cloud accounts.
com.idea.questionnare|Recommended|[NEED MORE INFO / NEED APK] Quizz app from MobileIdea company?
com.ideashower.readitlater.pro|Recommended|Pocket  Allows you to save an article or web page to remote servers for
com.imdb.mobile|Recommended|IMDb mobile app
com.infraware.polarisoffice5|Recommended|Polaris Office from US Infraware Inc company (Microsoft Office like)
com.inmobi.installer|Recommended|it's installer advertising company app.
com.instagram.android|Recommended|Instagram
com.ironsource.appcloud.oobe|Recommended|AppCloud (discontinued) from ironSource, an advertising company.
com.ironsource.appcloud.oobe.huawei|Recommended|Essentials apps App which promotes some other apps (and encourages you t
com.ironsource.appcloud.oobe.hutchison|Recommended|AppCloud (discontinued) from ironSource, an advertising company.
com.ironsoure.appcloud.oobe.wiko|Recommended|Provides first time setup for Wiko mobile
com.jewelsblast.ivygames.Adventure.free|Recommended|Jewels Blast.
com.jiiov.fingerprint_factorytest|Recommended|A factory test app for Jiiov fingerprint scanners (Jiiov fingerprint sca
com.kddi.android.cmail|Recommended|+Message A Japanese messaging and e-mail app.
com.king.candycrush4|Recommended|Candy Crush Friends Saga
com.king.candycrushsaga|Recommended|Candy Crush Saga  I don't understand why this game is so popular (I gues
com.king.candycrushsodasaga|Recommended|Candy Crush Soda Saga
com.kmsjp|Recommended|Kaspersky Anti-virus pre-installed on some Huawei phone's in Japanese.
com.knox.vpn.proxyhandler|Recommended|Samsung Knox allows business and personal content to "securely" coexist
com.kuackmedia.orange|Recommended|Altice Music It's a music application that allows you to stream music, b
com.lge.SprintHiddenMenu|Recommended|Hidden diagnostic menu for Sprint LG devices.
com.linkedin.android|Recommended|Linkedin app
com.locationlabs.cni.att|Recommended|AT&T Smart Limits Parental Control tool.
com.locationlabs.finder.sprint|Recommended|Sprint Family Locator  Lets you locate any phone registered under the Sp
com.locationlabs.finder.sprint.vpl|Recommended|Sprint Family Locator  Lets you locate any phone registered under the Sp
com.logame.eliminateintruder3d|Recommended|Dust Settle.
com.lookout|Recommended|Lookout Security & Antivirus
com.macropinch.swan|Recommended|Weather
com.magiear.handsfree.assistant|Recommended|Hands-Free Assistant Only has permission activity voice from mediatek.
com.matchboxmobile.wisp|Recommended|AT&T Hot Spots Run in background.
com.mediatek.aovtestapp|Recommended|It tests in camera something.
com.mediatek.atmwifimeta|Recommended|wifi data logger you don't want.
com.mediatek.autodialer|Recommended|autodialer, have a lot useless code.
com.mediatek.bluetooth.dtt|Recommended|Bluetooth logging.
com.mediatek.calendarimporter|Recommended|Useful in China where Google isn’t available, but not needed for Google
com.mediatek.carrierexpress|Recommended|Hidden operator configuration.
com.mediatek.dataprotection|Recommended|Possibly related to user partition encryption/decryption.
com.mediatek.emcamera|Recommended|Useless camera calibration hidden app.
com.mediatek.engineermode|Recommended|Engineer mode you can access by dialing a secret code (*#*#3646633#*#* o
com.mediatek.entitlement.fcm|Recommended|it's only FCM(Firebase Cloud Messaging).
com.mediatek.factorymode|Recommended|Tests hardware things.
com.mediatek.filemanager|Recommended|The com.mediatek.filemanager package is MediaTek’s proprietary file mana
com.mediatek.gbaservice|Recommended|Generic Bootstrapping Architecture.
com.mediatek.gnss.nonframeworklbs|Recommended|Have hidden activity and it's maybe for debug location requests, modem,
com.mediatek.gnssdebugreport|Recommended|Hidden debug stuff.
com.mediatek.gpslocationupdate|Recommended|Info about gps and notifications.
com.mediatek.lbs.em2.ui|Recommended|LocationEM2 Another GPS status/testing app.
com.mediatek.location.mtkgeofence|Recommended|Mtk Geofence Only logs in code
com.mediatek.magtapp|Recommended|MAGTApp Unknown, has GameEventService, MAGTEventAppReceiver, but code do
com.mediatek.mdmconfig|Recommended|Mobile Device Management (MDM) allows company’s IT department to reach i
com.mediatek.mdmlsample|Recommended|It looks like debugging app.
com.mediatek.mtklogger|Recommended|Logs debug data.
com.mediatek.mtklogger.proxy|Recommended|Logs debug data.
com.mediatek.sensorhub.ui|Recommended|Testing sensors.
com.mediatek.thermalmanager|Recommended|Hidden testing or logging app.
com.mediatek.videoplayer|Recommended|The com.mediatek.videoplayer package is MediaTek’s default video player
com.mediatek.voicecommand|Recommended|It's for voice commands like control music playing or voice control for
com.mediatek.voiceunlock|Recommended|It's for voice commands like control music playing or voice control for
com.mediatek.wfo.impl|Recommended|According to olorin  it's a MediaTek’s default fingerprint app (and he r
com.mediatek.ygps|Recommended|YGPS GPS test and bug report utilities, accessed via Engineer Mode.
com.micredit.in|Recommended|Mi Credit  App providing loans to MIUI users from India and China Note:
com.microsoft.appmanager|Recommended|Link to Windows  Microsoft app for synchronising your phone with a Windo
com.microsoft.office.excel|Recommended|Excel
com.microsoft.office.officehub|Recommended|Microsoft Office Mobile Includes the complete Word, PowerPoint, and Exce
com.microsoft.office.officehubhl|Recommended|Office Mobile hub (on Samsung Phone) Includes the complete Word, PowerPo
com.microsoft.office.officehubrow|Recommended|Microsoft Mobile Office Beta
com.microsoft.office.onenote|Recommended|OneNote  Microsoft and privacy you know...
com.microsoft.office.outlook|Recommended|Outlook
com.microsoft.office.powerpoint|Recommended|PowerPoint
com.microsoft.office.word|Recommended|Word
com.microsoft.skydrive|Recommended|Microsoft OneDrive (Cloud)
com.microsoft.translator|Recommended|Microsoft Translator app  )
com.mintgames.triplecrush.tile.fun|Recommended|Tile Fun.
com.mintgames.wordtrip|Recommended|Word Scenery Crossword.
com.miui.carrierinstructions|Recommended|Manual Shortcut to the carrier's online manual.
com.mobeam.barcodeService|Recommended|The Beaming Service enables your device to beam (relay) barcodes, as fou
com.mobitv.client.sprinttvng|Recommended|Sprint TV & Movies provided by mobitv  Lets you watch live TV and VOD.
com.mobitv.client.tmobiletvhd|Recommended|T-Mobile TV (discontinued, replaced by nl.tmobiletv.vinson) provided by
com.mobolize.sprint.securewifi|Recommended|Secure Wifi  Sprint VPN app provided by Mobolize.
com.monotype.android.font.applemint|Recommended|Font
com.monotype.android.font.chococooky|Recommended|Font
com.monotype.android.font.cooljazz|Recommended|Font
com.monotype.android.font.felbridge|Recommended|Felbridge FlipFont Changes the user interface font on your phone.
com.monotype.android.font.foundation|Recommended|Font
com.monotype.android.font.manroperegular|Recommended|ManropeRegular FlipFont Changes the user interface font on your phone.
com.monotype.android.font.mfinancehkbold|Recommended|MfinancehkBold FlipFont Changes the user interface font on your phone.
com.monotype.android.font.myinghei18030m|Recommended|Myinghei18030m FlipFont Changes the user interface font on your phone.
com.monotype.android.font.myuppymediumtc|Recommended|Myuppymediumtc FlipFont Changes the user interface font on your phone.
com.monotype.android.font.oxaniumregular|Recommended|OxaniumRegular FlipFont Changes the user interface font on your phone.
com.monotype.android.font.rajdhanimedium|Recommended|RajdhaniMedium FlipFont Changes the user interface font on your phone.
com.monotype.android.font.roboto|Advanced|Roboto Font
com.monotype.android.font.rosemary|Recommended|Font
com.monotype.android.font.samsungone|Recommended|Samsung One font
com.monotype.android.font.samsungsans|Recommended|SamsungSans font Font
com.monotype.android.font.syndor|Recommended|Syndor FlipFont Changes the user interface font on your phone.
com.monotype.android.font.tinkerbell|Recommended|Font
com.motorola.att.phone.extensions|Recommended|AT&T Phone Extension Provides access to AT&T extensions in your dialer.
com.motorola.attvowifi|Recommended|AT&T Wifi-calling
com.motorola.ltebroadcastservices_vzw|Recommended|LTE Broadcast services from Verizon.
com.motorola.mot5gmod|Recommended|5G Moto Mod
com.motorola.omadm.sprint|Recommended|Configuration of the device (including first time use), enabling and dis
com.motorola.omadm.usc|Recommended|OMA Device Management for Verizon Handles configuration of the device (i
com.motorola.omadm.vzw|Recommended|OMA Device Management for Verizon Handles configuration of the device (i
com.motorola.sprintwfc|Recommended|Sprint Wifi Calling Provides wifi calling to Sprint customers.
com.motorola.vzw.cloudsetup|Recommended|Cloud setup
com.motorola.vzw.loader|Recommended|????
com.motorola.vzw.mot5gmod|Recommended|5G Moto Mod
com.motorola.vzw.phone.extensions|Recommended|Free HD wallpaper from verizon
com.motorola.vzw.provider|Recommended|????
com.motricity.verizon.ssodownloadable|Recommended|Verizon Login by Motricity (now Voltari) Voltari provides relevance-driv
com.mtk.telephony|Recommended|SimRecoveryTestTool
com.naviexpert.NaviExpert|Recommended|Navigation T-Mobile
com.netflix.mediaclient|Recommended|Netflix app
com.netflix.partner.activation|Recommended|Apk file name: By_3rd_NetflixActivationOverSeas Some form of activation
com.nextbit.app|Recommended|docomo LIVE UX backup it's on some japanese phones
com.nextradioapp.nextradio|Recommended|NextRadio  3-party app which lets you experience live and local FM radio
com.nf.snake|Recommended|Snake Zoneio.
com.nhl.gcl.canada|Recommended|Rogers NHL app.
com.niksoftware.snapseed|Recommended|Snapseed
com.nim.rogers|Recommended|Texture, a digital magazine service created by Rogers Media.
com.nousguide.android.rbtv|Recommended|Red Bull TV A 1 star streaming service owned by a company that sells ele
com.nttdocomo.android.anmane2|Recommended|Likely a placeholder for docomo's corporate MDM service.
com.nttdocomo.android.anshinsecurity|Recommended|Anshin-security  docomo's security suite providing virus scanning, spam
com.nttdocomo.android.apnsw|Recommended|APN switching control Resource-only stub.
com.nttdocomo.android.databackup|Recommended|Backup feature for application of docomo's service, mainly provides sche
com.nttdocomo.android.devicehelp|Recommended|Recommended usage tips Intrusive.
com.nttdocomo.android.dmenu2|Recommended|d-menu Web page shortcut and notification receiver.
com.nttdocomo.android.iconcier_contents|Recommended|Diagnostics things only in this japanese app
com.nttdocomo.android.initialization|Recommended|DOCOMO initialization OOBE (Initial setup) component for docomo devices.
com.nttdocomo.android.mascot|Recommended|my daiz  docomo's information (e.g.
com.nttdocomo.android.msg|Recommended|+Message A messaging app using a carrier-customized RCS implementation b
com.nttdocomo.android.mymagazine|Recommended|SmartNews for docomo News app.
com.nttdocomo.android.networkservice|Recommended|Office Link Settings Provides "Office Link" service (docomo's enterprise
com.nttdocomo.android.pf.dcmippushaggregator|Recommended|A background platform service that manages docomo's proprietary IP push
com.nttdocomo.android.remotelock|Recommended|Omakase-lock  Remote device lock service.
com.nttdocomo.android.rwpushcontroller|Recommended|rwpushcontroller Another FeliCa Networks app, with japanese language Use
com.nttdocomo.android.schedulememo|Recommended|Schedule & Memo 2 different apps in one package; docomo's schedule and m
com.nttdocomo.android.store|Recommended|d-Point Market Web page shortcut and notification receiver.
com.nttdocomo.android.wipe|Recommended|Remote Wipe  Allows docomo customer support to wipe your device upon req
com.nuance.swype.input|Recommended|Swype keyboard by Nuance
com.oem.euiccpartnerapp|Recommended|EuiccPartnerApp Needed for eSIM (eUICC)?
com.oem.rftoolkit|Recommended|RfToolkit Testing things like Wi-Fi, lots of Chinese words
com.omusic.gPhone|Recommended|friDay music A treasure house of global music, thousands of situational
com.opera.branding|Recommended|Opera Branding Provider Don't know what it really does.
com.opera.branding.news|Recommended|Opera News Branding Provider Don't know what it really does.
com.opera.max.oem|Recommended|Opera Max (discontinued) System-wide data-saving proxy that funnell all
com.opera.max.preinstall|Recommended|Opera Max (discontinued) System-wide data-saving proxy that funnell all
com.opera.mini.native|Recommended|Opera Mini web browser
com.opera.preinstall|Recommended|Opera Preinstall Data Generates utm tracking stuff
com.opos.cs|Recommended|Hot Apps Generate app folders on home screen that recommended sponsored
com.orange.aura.oobe|Recommended|Orange Manual Selector Makes unnecessary notifications
com.orange.cloud.android|Recommended|Orange Cloud Preloaded on devices sold by Orange.
com.orange.contultauorange|Recommended|Contul meu Orange Orange account client preloaded on devices sold by Ora
com.orange.mail.fr|Recommended|Mail Orange
com.orange.miorange|Recommended|Lets you access to your Orange account
com.orange.mylivebox.fr|Recommended|Ma Livebox  Lets you manage your livebox from your phone.
com.orange.mysosh|Recommended|My Sosh  Lets you access to your Sosh account
com.orange.orangeetmoi|Recommended|Orange Et Moi  Orange customer space
com.orange.owtv|Recommended|TV d'Orange  Lets you watch TV/VOD on your phone.
com.orange.tdd|Recommended|Transfert de données  Lets you transfer wirelessly: contacts, SMS, call
com.orange.travel.orange|Recommended|Orange Travel Preloaded on devices sold by Orange.
com.orange.update|Recommended|Handles Orange apps updates.
com.orange.update.OrangeUpdateApplication|Recommended|Obviously related to update...
com.orange.vvm|Recommended|Messagerie vocale visuelle  Lets you manage your voicemail with an app.
com.orange.wifiorange|Recommended|Mon Réseau  Lets you measure your speed connection and find better Orang
com.orange.world|Recommended|Orange World Shortcut to orangeworld.com preloaded on devices sold by Or
com.ordinaryjoy.woodblast|Recommended|Wood Blast.
com.osp.app.signin|Advanced|Samsung Account app Lots of trackers in this app.
com.pandora.android|Recommended|Pandora  Very intrusive music and podcasts app.
com.particlenews.newsbreak|Recommended|News Break: Local & Breaking  News provided by NewsBreak  # FYI :
com.phonepe.app|Recommended|PhonePe  PhonePe is a payment app that allows indian users to use BHIM U
com.pinsight.dw|Recommended|App Stack Force-installed app by Sprint.
com.pinsight.v1|Recommended|App Spotlight Makes you discover new apps from the Google Play store.
com.playphone.gamestore|Recommended|Playphone Gamestore  "Helps" you discover the "best" Android games and c
com.playphone.gamestore.loot|Recommended|Loot Premium service from playphone ?
com.popcap.pvz_carrier|Recommended|Plants vs Zombies
com.ptc.osp.gnc|Recommended|Playing the Waiting Game by T-Mobile.
com.pure.indosat.care|Recommended|myIM3  App provided by Indosat Ooredoo, an Internet provider from Indone
com.qti.confuridialer|Recommended|Conference call service for digital signal (SIP/VoIP).
com.qti.dcf|Recommended|I found only: DCF Allows an application to share content using Bluetooth
com.qti.diagservices|Recommended|Starts process when plugged into a PC (with debugging on, haven't tried
com.qti.qcc|Recommended|QCC Have a lot of stuff about logs, testing framework for Android with R
com.qti.qualcomm.deviceinfo|Recommended|Device Info Hidden device info activity not available for users.
com.qti.qualcomm.mstatssystemservice|Recommended|Useless network statistics, package usage.
com.qti.service.colorservice|Recommended|Allows the application to directly affect the device's display paramter.
com.qti.slaservice|Recommended|Service Level Agreement (SLA) protocol that analyzes network performance
com.qti.snapdragon.qdcm_ff|Recommended|Qualcomm Display Color Management tool Attempts to "make colors look vib
com.qti.xdivert|Recommended|Smart-Divert If enabled, diverts your calls to another number.
com.qualcomm.atfwd|Recommended|Used to send AT command messages from/to the modem Attention commands co
com.qualcomm.atfwd2|Recommended|This is the same app as the 'com.qualcomm.atfwd'.
com.qualcomm.cabl|Recommended|Content Adaptative Backlight Settings CABL will try to adjust the image
com.qualcomm.embms|Recommended|Runs on boot, but not in the background beyond that?
com.qualcomm.interfacepermissions|Recommended|Completely empty package.
com.qualcomm.location|Recommended|LocationServices Runs in the background as part of the system.
com.qualcomm.location.XT|Recommended|Qualcomm IZat.
com.qualcomm.location.XT.setup|Recommended|Qualcomm IZat Its hidden Setup XT location for Qualcomm.
com.qualcomm.qcom_qmi|Recommended|qchat logo png founded also Hello World!
com.qualcomm.qti.accesscache|Recommended|CarrierAccessCacheService Well it's only name app and no code.
com.qualcomm.qti.auth.fidocryptoservice|Recommended|Qualcomm FIDO implementation.
com.qualcomm.qti.auth.sampleauthenticatorservice|Recommended|it's only Sample, nothing important found in the code
com.qualcomm.qti.auth.sampleextauthservice|Recommended|it's only Sample, nothing important found in the code
com.qualcomm.qti.auth.secureextauthservice|Recommended|it's only Sample, nothing important found in the code
com.qualcomm.qti.auth.securesampleauthservice|Recommended|it's only Sample, nothing important found in the code
com.qualcomm.qti.autoregistration|Recommended|Collects device activation data to remotely activate phone warranty.
com.qualcomm.qti.biometrics.fingerprint.service|Recommended|Fingerprint authentication not used by any app maybe only china, test te
com.qualcomm.qti.callenhancement|Recommended|Supposed to enhance call quality (I'll let you test if it really does) T
com.qualcomm.qti.callfeaturessetting|Recommended|CallFeatureSetting Hidden Call forwarding, Call waiting not available fo
com.qualcomm.qti.devicestatisticsservice|Recommended|Device statistics service Statistics of the phone's usage:data, Wifi, ba
com.qualcomm.qti.ims|Recommended|Hidden IMS Settings activity not available for users.
com.qualcomm.qti.loadcarrier|Recommended|CarrierLoadService Have only things like a: Find carrier, carrier switch
com.qualcomm.qti.modemtestmode|Recommended|Modem test
com.qualcomm.qti.perfdump|Recommended|Performance dump (logging) Enable a more accurate overview of the runnin
com.qualcomm.qti.performancemode|Recommended|Performance Mode Hidden Performance Mode activity not available for user
com.qualcomm.qti.powersavemode|Recommended|It have hidden power saving modes.
com.qualcomm.qti.qccauthmgr|Recommended|QCC-AUTHMGR It have something to (com.qualcomm.qti.smq).
com.qualcomm.qti.qccnetstat|Recommended|useless network statistics also QCC in name qualcomm app is for analytic
com.qualcomm.qti.qcolor|Recommended|QColor QTI enhanced color mode?
com.qualcomm.qti.qcom_accesslogkit|Recommended|Theres nothing about logs.
com.qualcomm.qti.qdcmtpg|Recommended|layout but not sure for what.
com.qualcomm.qti.qdma|Recommended|QDMA = Qualcomm Device Management and Analytics.
com.qualcomm.qti.qmmi|Recommended|QMMI is a test app made by Qualcomm.
com.qualcomm.qti.qms.service.connectionsecurity|Recommended|Telemetry service qms = quality management service Background-Connection
com.qualcomm.qti.qms.service.credentials|Recommended|QMS = Quality Management Service.
com.qualcomm.qti.qms.service.telemetry|Recommended|Qualcomm Mobile Security Telemetry service.
com.qualcomm.qti.qms.service.trustzoneaccess|Recommended|QMS always spying.
com.qualcomm.qti.qwes.AndroidService|Recommended|qms, qwes it's used for collecting user data.
com.qualcomm.qti.rcsimsbootstraputil|Recommended|RCS Service RCS = Rich Communication Services.
com.qualcomm.qti.remoteSimlockAuth|Recommended|Enable you to lock/unlock your eSIM remotely.
com.qualcomm.qti.ridemodeaudio|Recommended|Ridemode Recording list Hidden app, not available for users that should
com.qualcomm.qti.seccamservice|Recommended|SecCamService Only collects data about camera.
com.qualcomm.qti.seemp.service|Recommended|Useless code and it's probably to verify packages.
com.qualcomm.qti.server.qtiwifi|Recommended|it's made for analytics.
com.qualcomm.qti.server.wigig.tethering.rro|Recommended|Only found dhcp range?
com.qualcomm.qti.services.secureui|Recommended|Qualcomm Secure UI Service It collects your data about screen:gravity, o
com.qualcomm.qti.simcontacts|Recommended|SimContacts Hidden Qualcomm sim contacts editor app.
com.qualcomm.qti.smcinvokepkgmgr|Recommended|This app have nothing.
com.qualcomm.qti.smq|Recommended|QTR (Qualcomm Technology Reporting) Runs on boot.
com.qualcomm.qti.uim|Recommended|Runs "RemoteSimLockService" in the background.
com.qualcomm.qti.uimGbaApp|Recommended|Contains a "GbaService", Related to uim card (R-UIM is a type of SIM car
com.qualcomm.qti.xrcb|Recommended|Receive xrcb network signals for radio side.
com.qualcomm.qti.xrvd.service|Recommended|XRVD The real name of this app is XRVDTest.
com.qualcomm.qtil.aptxacu|Recommended|Hidden aptxals Audio Bluetooth sample improvement.
com.qualcomm.qtil.aptxals|Recommended|Hidden aptxals Audio Bluetooth sample improvement test
com.qualcomm.qtil.aptxui|Recommended|Hidden aptxals Audio Bluetooth sample improvement.
com.qualcomm.shutdownlistner|Recommended|uses library com.qualcomm.qcrilhook and logs shutdown phone but it's not
com.qualcomm.simcontacts|Recommended|Sim Contacts Probably handles syncing(exporting/importing) contacts to/f
com.qualcomm.svi|Recommended|Sunlight Visibility Improvement SVI Settings.
com.qualcomm.uimremoteclient|Recommended|Related to uim card (R-UIM is a type of SIM card mainly used in Asia)
com.qualcomm.uimremoteserver|Recommended|Related to uim card (R-UIM is a type of SIM card mainly used in Asia)
com.quicinc.voice.activation|Recommended|Qualcomm Voice Assist  Always-on voice detection, so obviously always ru
com.quickplay.android.bellmediaplayer|Recommended|App for watching your Bell Fibe TV on the go.
com.realvnc.android.remote|Recommended|Remote controle service by Realvnc   Not sure having a remote control ap
com.redteamobile.roaming|Recommended|ORoaming Lets you buy RedTeaMobile data plan to access Internet in forei
com.redteamobile.roaming.deamon|Recommended|ORoaming Redtea Roaming service deamon for com.redteamobile.roaming
com.remotefairy4|Recommended|AnyMote Universal Remote + Wifi Smart Home Control  IR Remote control ap
com.republicwireless.tel|Recommended|Republic  Lets you manage your Republic wireless account.
com.rhapsody|Recommended|Napster Music  Napster streaming app
com.rhapsody.vpl|Recommended|Napster Music  Napster streaming app
com.rlk.weathers|Recommended|Daily Weather  Weather app with ads and trackers.
com.rogers.npd.appzone|Recommended|An app store just for Rogers bloat.
com.s.antivirus|Recommended|AVG Antivirus  for Sony Xperia.
com.samsung.InputEventApp|Recommended|Hidden testing things also no activities
com.samsung.SMT|Advanced|Samsung TTS (Text-to-speech) Works with applications such as S Voice; tr
com.samsung.SMT.lang_en_us_f00|Advanced|Voice pack for Samsung TTS in English (United States).
com.samsung.SMT.lang_es_mx_f00|Advanced|Voice pack for Samsung TTS in Spanish (Mexico).
com.samsung.SMT.lang_pt_br_f00|Advanced|Voice pack for Samsung TTS in Portuguese (Brazil).
com.samsung.aasaservice|Recommended|Sometimes, eat a LOT of battery (according to some reddit users) Securit
com.samsung.accessory|Recommended|Samsung Accessory Service  Lets you transfer data between your Samsung p
com.samsung.accessory.beansmgr|Recommended|Gear IconX Plugin  Allows you to use features such as device settings an
com.samsung.accessory.budsunitemgr|Advanced|Provides the system component that manages Galaxy Buds features and conf
com.samsung.accessory.safiletransfer|Recommended|SASystemProviders Needed for Samsung Accessory Service
com.samsung.adaptivebrightnessgo|Recommended|CameraLightSensor App to test Adaptive Brightness.
com.samsung.advancedcallservice|Advanced|Advanced Calling feature on an Android is a feature that allows you to m
com.samsung.advp.imssettings|Advanced|Needed for VoLTE (Voice over LTE)  IMS(Ip Multimedia Subsystem) is an op
com.samsung.android.A10s.d01.wallpapermulti|Advanced|App with wallpapers to Samsung A10s.
com.samsung.android.A12.d01.wallpapermulti|Advanced|Default Multiple Wallpaper Resource A 0 bytes overlay app for Samsung Ga
com.samsung.android.A21.d01.wallpapermulti|Advanced|Dynamic lock screen wallpaper feature for the Samsung Galaxy A21.
com.samsung.android.a20.d01.wallpapermulti|Recommended|Seems like a completely useless empty package, but the default lock scre
com.samsung.android.a50.d01.wallpapermulti|Recommended|Provides the wallpapers used by the lockscreen multi-pack feature
com.samsung.android.accessibility.talkback|Advanced|Samsung TalkBack in accessibility.
com.samsung.android.activation|Recommended|Activation phone in china.
com.samsung.android.adaptivebrightnessgo|Recommended|Samsung Adaptive Brightness Feature of Samsung devices that, as the name
com.samsung.android.aircommandmanager|Recommended|AirCommandManager (FACM) Gives you access to signature S Pen features.
com.samsung.android.airtel.stubapp|Recommended|My Airtel Stub app My Airtel is a customer service app designed for Airt
com.samsung.android.alive.service|Advanced|Content suggestions service
com.samsung.android.aliveprivacy|Advanced|Content Suggestions is an AI-based app inside the Secure Folder, and its
com.samsung.android.allshare.service.fileshare|Advanced|Wi-Fi Direct Allows two devices to establish a direct Wi-Fi connection w
com.samsung.android.allshare.service.mediashare|Recommended|Samsung AllShare service (now called SmartView and/or Nearby Service).
com.samsung.android.app.accesscontrol|Recommended|Samsung Interaction control Settings > Accessibility > Dexterity and int
com.samsung.android.app.advsounddetector|Recommended|Samsung Sound detectors Uses microphone to identify recognizable sounds.
com.samsung.android.app.amcagent|Advanced|Advanced Management Console Agent Entreprise feature I guess.
com.samsung.android.app.appsedge|Advanced|Samsung apps edge  Displays your five most frequently used apps for you
com.samsung.android.app.appupdater|Recommended|Discontinued app to update Samsung's proprietary apps.
com.samsung.android.app.assistantmenu|Recommended|Assistant menu Designed for individuals with motor control or other phys
com.samsung.android.app.camera.sticker.facear.preload|Recommended|Annoying Stickers/stamps of the Samsung camera app.
com.samsung.android.app.camera.sticker.facear3d.preload|Recommended|Default 3D live stickers Annoying Stickers/stamps of the Samsung camera
com.samsung.android.app.camera.sticker.facearavatar.preload|Recommended|Annoying Stickers/stamps of the Samsung camera app.
com.samsung.android.app.camera.sticker.facearframe.preload|Recommended|Frames sticker?
com.samsung.android.app.camera.sticker.stamp.preload|Recommended|Annoying Stickers/stamps of the Samsung camera app.
com.samsung.android.app.clipboardedge|Recommended|Clipboard edge panel
com.samsung.android.app.cocktailbarservice|Recommended|Edge screen/panels Enables you to open your five most used apps by simpl
com.samsung.android.app.color|Recommended|Color adjustment Samsung's adaptive super AMOLED screen optimizes the co
com.samsung.android.app.colorblind|Advanced|This app provides colorblind accessibility features for Samsung devices,
com.samsung.android.app.contacts|Advanced|Samsung Contacts Safe to debloat if you use another contacts app.
com.samsung.android.app.dofviewer|Advanced|Live focus Allows you to adjust the level of background blur in the came
com.samsung.android.app.dressroom|Advanced|Samsung Wallpapers Wallpaper manager.
com.samsung.android.app.dtv.dmb|Recommended|TV app on Samsung phones and it's only available on Japanese or Korean v
com.samsung.android.app.earphonetypec|Recommended|Samsung USB-C headset not required unless you have a Samsung DAC or Sams
com.samsung.android.app.episodes|Recommended|Samsung story album
com.samsung.android.app.filterinstaller|Recommended|Filter installer I have no clue about the usefulness of this package.
com.samsung.android.app.galaxyfinder|Advanced|Samsung Finder is a search application that allows you to find what you
com.samsung.android.app.headlines|Recommended|Flipboard app for Samsung devices.
com.samsung.android.app.interactivepanoramaviewer|Recommended|Visual.
com.samsung.android.app.interpreter|Advanced|Samsung Interpreter app, enables Live translation of foreign languages
com.samsung.android.app.kfa|Recommended|KFA Another app from knox, has something to decrypt.
com.samsung.android.app.ledbackcover|Recommended|I think it enables doing things with LEDs on the cover  HOW IT WORKS :
com.samsung.android.app.ledcoverdream|Recommended|I think it enable doing things with LEDs on the cover  HOW IT WORKS :
com.samsung.android.app.memo|Recommended|Samsung Memo (was replaced by Samsung Notes app : com.samsung.android.ap
com.samsung.android.app.mhswrappertmo|Recommended|Mobile Hotspot Is it linked to T-Mobile ?
com.samsung.android.app.mirrorlink|Recommended|Used to connect your phone to a car (with  support) in order to provide
com.samsung.android.app.motionpanoramaviewer|Advanced|Motion panorama viewer Lets you see the result of a motion panorama
com.samsung.android.app.multiwindow|Advanced|Provides manifestly the ability to display multiple apps on the screen (
com.samsung.android.app.news|Recommended|News Samsung app Doesn't exist anymore?
com.samsung.android.app.notes|Recommended|Samsung Notes app
com.samsung.android.app.notes.addons|Advanced|Allows for marking up/drawing on notes in the Samsung Notes app (designe
com.samsung.android.app.omcagent|Recommended|Open Market Customization Agent (AKA 'Recommended Apps') Installs more S
com.samsung.android.app.panel.naver.v|Recommended|Naver V Panel Special samsung panel for the very useless V LIVE (formerl
com.samsung.android.app.parentalcare|Recommended|Parental control to block apps on new samsung phones
com.samsung.android.app.pinboard|Recommended|Samsung Scrapbook (discontinued)
com.samsung.android.app.reminder|Recommended|Samsung bixby reminder  Set up smart reminders to get notified when and
com.samsung.android.app.routines|Expert|Samsung Modes and Routines (previously aka Bixby Routines)  Automating a
com.samsung.android.app.sbrowseredge|Recommended|Related to internet browser.
com.samsung.android.app.scrollcapture|Advanced|Samsung's app for taking screenshots.
com.samsung.android.app.selfmotionpanoramaviewer|Advanced|Selfie panorama viewer Lets you see the result of a selfie motion panora
com.samsung.android.app.settings.bixby|Recommended|Bixby settings (Bixby = Samsung intelligence assistant)
com.samsung.android.app.shareaccessibilitysettings|Advanced|Seems to be like a package for saving your accessibility settings, likel
com.samsung.android.app.sharelive|Recommended|Samsung Quick Share (from the Galaxy Store)
com.samsung.android.app.simplesharing|Recommended|Samsung Link Sharing Lets you share large size files by using the Samsun
com.samsung.android.app.siofviewer|Advanced|Live focus Needed for Live Focus in camera app.
com.samsung.android.app.sketchbook|Recommended|Drawing Assistant This is an useless AI extension for Samsung Keyboard.
com.samsung.android.app.smartcapture|Advanced|Samsung Capture Samsung's implementation of screenshot.
com.samsung.android.app.smartwidget|Advanced|Samsung screenshot You will still be able to take screenshots without th
com.samsung.android.app.social|Recommended|I know this has been discontinued by Samsung but that it.
com.samsung.android.app.spage|Recommended|Samsung Free (previously known as 'Bixby Home') is a vertically scrollin
com.samsung.android.app.storyalbumwidget|Recommended|The Story Album widget enables you to access the Story Album app and cre
com.samsung.android.app.talkback|Recommended|Voice assistant.
com.samsung.android.app.taskedge|Recommended|Handle task edge panel Through Tasks edge, you can quickly perform frequ
com.samsung.android.app.telephonyui.esimclient|Recommended|only has ESimClient Webview Activity
com.samsung.android.app.tips|Recommended|Tips on how to use your phone"
com.samsung.android.app.updatecenter|Recommended|App update Installs app updates
com.samsung.android.app.vex.scanner|Advanced|Document Scanner.
com.samsung.android.app.vrsetupwizards|Recommended|Samsung Gear VR (Virtual Reality) setup wizard   Stub =  Setup wizard :
com.samsung.android.app.vrsetupwizardstub|Recommended|Samsung Gear VR (Virtual Reality) setup wizard   Stub =  Setup wizard :
com.samsung.android.app.watchmanager|Recommended|Samsung Galaxy Wearable (Samsung Gear)
com.samsung.android.app.watchmanagerstub|Recommended|Stub for the Galaxy Wearable app.
com.samsung.android.app.withtv|Recommended|WitTV (replaced by com.sec.android.app.withtv) Used to stream content fr
com.samsung.android.appseparation|Advanced|Separated apps Separated Apps isolates third-party apps in a sandboxed f
com.samsung.android.ardrawing|Recommended|AR Doodles (accessible through AR Zone) Lets you draw on your face using
com.samsung.android.aremoji|Recommended|AR Emoji mode for Samsung camera
com.samsung.android.aremojieditor|Recommended|AR Emoji Editor Edits those AR people emoji things
com.samsung.android.arzone|Recommended|AR Zone  Lets you access other AR apps.
com.samsung.android.asksmanager|Recommended|Samsung device protection manager.
com.samsung.android.audiomirroring|Advanced|AudioMirroring Audio Mirroring to Cast other devices will not work after
com.samsung.android.authfw|Recommended|Used by Samsung Pass Biometric authentication service that can be used t
com.samsung.android.authservice|Recommended|Runs in the background on Galaxy S7 and earlier, can't seem to find a pu
com.samsung.android.aware.service|Advanced|Samsung Quick Share Agent Use Wifi direct to share files between 2 Samsu
com.samsung.android.bbc.bbcagent|Recommended|BBCAgent (B.
com.samsung.android.bbc.fileprovider|Recommended|KNOX BBC Provider.
com.samsung.android.beaconmanager|Recommended|Replaced by Samsung Smart Things (com.samsung.android.ststub) Allows use
com.samsung.android.bio.face.service|Advanced|Handles Face recognition unlock  NOTE: removing this package causes biom
com.samsung.android.biometrics|Advanced|Provide biometric support
com.samsung.android.bixby.agent|Recommended|Removing this will disable the bixby hardware key without breaking Bixby
com.samsung.android.bixby.agent.dummy|Recommended|Bixby Voice Stub
com.samsung.android.bixby.es.globalaction|Recommended|Bixby stuff [MORE INFO NEEDED]
com.samsung.android.bixby.plmsync|Recommended|Bixby stuff [MORE INFO NEEDED]
com.samsung.android.bixby.service|Recommended|Bixby Service
com.samsung.android.bixby.voiceinput|Recommended|Bixby service needed for voice control
com.samsung.android.bixby.wakeup|Recommended|Bixby voice wake-up
com.samsung.android.bixbyhelper|Recommended|QQ music plugin Require QQ Music and it's Chinese.
com.samsung.android.bixbytouch|Recommended|Bixby Touch App for smart things, still only for China.
com.samsung.android.bixbyvision.framework|Recommended|BixbyVision Framework
com.samsung.android.bluelightfilter|Advanced|Blue light filter Using the sunrise/sunset option uses the ACCESS_FINE_L
com.samsung.android.calendar|Advanced|Samsung Calendar App
com.samsung.android.callbgprovider|Advanced|CallBGProvider Call Background.
com.samsung.android.cameraxservice|Recommended|it's hidden app for testing camera things also it's camera calibration
com.samsung.android.carkey|Recommended|Digital Key Framework Frameworks to CarKey app, only China.
com.samsung.android.carlink|Recommended|CarLife app only for China.
com.samsung.android.chnfileshare.kit|Recommended|S Share Chinese app to Secure transfer files using bluetooth.
com.samsung.android.cmfa.framework|Recommended|Continous Multi-Factor Authentication Framework Seems useless if you are
com.samsung.android.coldwalletservice|Recommended|Samsung Blockchain Keystore Secure and convenient place to manage your p
com.samsung.android.contacts|Expert|Samsung contacts app?
com.samsung.android.coreapps|Recommended|Samsung Enhanced features Files and profiles sharing feature.
com.samsung.android.da.daagent|Recommended|Samsung dual messenger  Allows you to use two separate accounts for the
com.samsung.android.dbsc|Recommended|Galaxy Service Setup Device-Based Service Consent.
com.samsung.android.dck.timesync|Recommended|DckTimeSyncService Only logs found
com.samsung.android.deviceidservice|Recommended|DeviceIdService Collects device id but it is not known what it is used f
com.samsung.android.dialer|Advanced|Stock Samsung Phone dialer
com.samsung.android.digitalkey|Recommended|Digital Key I found on this app only: Using your phone as a key to open
com.samsung.android.dkey|Recommended|Samsung Wallet Digital Key Used for cars, only China.
com.samsung.android.dlp.service|Recommended|SamsungDLPService (KNOX).
com.samsung.android.dqagent|Advanced|Samsung Device Quality Agent Logging agent for diagnostic device informa
com.samsung.android.drivelink.stub|Recommended|Stub for car mode REMINDER : Stub =
com.samsung.android.dsms|Recommended|Dsms Hidden diagmonagent logs components.
com.samsung.android.dynamiclock|Advanced|Samsung Wallpaper services (Dynamic Lock) NOTE: Breaks LockStar customiz
com.samsung.android.easysetup|Recommended|Samsung Nearby device scanning Requires "com.samsung.android.beaconmanag
com.samsung.android.email.composer|Recommended|another app related to Email app, bad privacy
com.samsung.android.email.provider|Recommended|Samsung email app
com.samsung.android.email.sync|Recommended|Sync to Email app, bad privacy
com.samsung.android.email.ui|Recommended|Email app with bad privacy
com.samsung.android.email.widget|Recommended|Email Widget on some old samsung phones like S6
com.samsung.android.emergency|Advanced|Emergency SOS Has a lot of permissions, lets you send SOS messages and c
com.samsung.android.emojiupdater|Recommended|AR Emoji updater This package has no permission so I wonder how it can u
com.samsung.android.fast|Recommended|Samsung Secure Wi-Fi Samsung VPN service powered by McAfee   Note: If yo
com.samsung.android.fingerprint.service|Advanced|Fingerprint service
com.samsung.android.fmm|Recommended|Find My Mobile Tracks down your device when it gets lost.
com.samsung.android.forest|Advanced|Digital Wellbeing (old version of com.samsung.android.wellbeing) That's
com.samsung.android.framework.res|Advanced|This package is part of Samsung’s custom system framework and manages va
com.samsung.android.galaxycontinuity|Recommended|Samsung Flow  It's a service to 'seamlessly' connect your devices.
com.samsung.android.gallery.plugins|Recommended|SamsungGallery Plugins Chinese mapcamera plugin.
com.samsung.android.gallery.watch|Advanced|Samsung Watch gallery app
com.samsung.android.game.gamehome|Recommended|Samsung Game Launcher  All in one hub for mobiles games
com.samsung.android.game.gametools|Recommended|Samsung Game Tools/Booster  Let you record and share screenshots of your
com.samsung.android.game.gos|Recommended|Samsung Game Optimizing Service Is supposed to "improve" game performanc
com.samsung.android.gametuner.thin|Recommended|Samsung Game Tuner  Game Tuner is advanced setting app.
com.samsung.android.gearoplugin|Recommended|Gear S Plugin  Plugin for com.samsung.android.app.watchmanager
com.samsung.android.hdmapp|Recommended|Hypervisor Device Manager App
com.samsung.android.hmt.vrshell|Recommended|Gear VR Shell Gear VR :
com.samsung.android.hmt.vrsvc|Recommended|Gear VR Service See above.
com.samsung.android.homemode|Recommended|Daily Board  Show a slideshow of your favourite pictures while your devi
com.samsung.android.hwresourceshare.storage|Recommended|Allows the storage on your phone, tablet, computer, and TV to connect so
com.samsung.android.icecone|Recommended|Keyboard Content Center Lets you choose media content (e.g.
com.samsung.android.imagepp|Recommended|Chat PP Only for China.
com.samsung.android.incall.contentprovider|Advanced|CallContentProvider After remove, some features will not be available: F
com.samsung.android.inputshare|Recommended|Samsung Multi-Control, allows certain apps to be continued on multiple G
com.samsung.android.intelligenceservice|Recommended|Completely empty package.
com.samsung.android.intelligenceservice2|Recommended|It seems that this package is a kind of spyware.
com.samsung.android.internal.overlay.config.default_contextual_search|Advanced|The code only assigns (com.google.android.googlequicksearchbox) as the d
com.samsung.android.ipsgeofence|Recommended|Samsung Visit In app IPSGeofence IPS = Indoor Positioning System.
com.samsung.android.keycustomizationinfobackupservice|Recommended|If you don't use backup service, it's safe to remove.
com.samsung.android.keyguardwallpaperupdator|Recommended|Lets you customize your Samsung device with different images (provided b
com.samsung.android.kidsinstaller|Recommended|Samsung Kids Home  Lets you shape a "safe environment" for your child.
com.samsung.android.kmxservice|Advanced|KmxService Knox Matrix.
com.samsung.android.knox.analytics.uploader|Recommended|Knox Analytics Uploader Sends analytics to Samsung
com.samsung.android.knox.attestation|Recommended|KNOX Attestation Lets you check the integrity of a Samsung Android devic
com.samsung.android.knox.containeragent|Recommended|KNOX Work profile/space Provides an isolated environment to store data (
com.samsung.android.knox.containercore|Recommended|KNOX Work profile/space Provides an isolated environment to store data (
com.samsung.android.knox.containerdesktop|Recommended|Knox Container Desktop Provides UI for the work(space) container?
com.samsung.android.knox.er|Advanced|Knox ER component.
com.samsung.android.knox.kpecore|Recommended|Samsung KPECore KPE stands for 'Knox Platform for Enterprise'
com.samsung.android.knox.mpos|Advanced|Knox mPOS Enabler offers a range of security/usability features, enablin
com.samsung.android.knox.pushmanager|Recommended|KnoxPushManager
com.samsung.android.knox.zt.framework|Recommended|KnoxZT Framework To learn how you type, Touch Dynamics will collect and
com.samsung.android.livestickers|Recommended|Deco Pic (accessible through AR Zone) Camera app with stickers and snapc
com.samsung.android.location|Advanced|Slocation.
com.samsung.android.mapsagent|Recommended|Provides the "recommendation" service.
com.samsung.android.mateagent|Recommended|Samsung Galaxy Friends is an accessory platform service that allows the
com.samsung.android.mcfds|Recommended|Continuity Service Chat with other peoples.
com.samsung.android.mcfserver|Advanced|Samsung Multi Connectivity See
com.samsung.android.mdagent|Advanced|MdAgent
com.samsung.android.mdecservice|Recommended|Samsung Call & Text.
com.samsung.android.mdm|Recommended|MDMApp (Mobile Device Management app) Used to monitor and manage remotel
com.samsung.android.mdx|Recommended|Link to Windows Service Works in conjunction with the Microsoft Your Pho
com.samsung.android.mdx.kit|Recommended|Quick Share Connectivity / MDE Service Framework MDX = Multi Devices Exp
com.samsung.android.mdx.quickboard|Advanced|Device control and Media output Kind of a hub in notification panel for
com.samsung.android.mediacontroller|Advanced|Ability to controls phone's audio from your watch.
com.samsung.android.messageguardsync|Advanced|Handles synchronizing and updating Samsung Message Guard policies.
com.samsung.android.messaging|Advanced|Samsung Messaging app
com.samsung.android.messaging.extension.chn|Recommended|MessagingExtensionCh Has too much spying things.
com.samsung.android.mfi|Recommended|Galaxy Widget  Just shows you ads of some of Samsung's services or other
com.samsung.android.mobileservice|Advanced|Group Sharing (earlier Samsung Experience Service).
com.samsung.android.mocca|Recommended|MoccaMobile Require google play services.
com.samsung.android.motionphoto.app|Advanced|Motion Photo.
com.samsung.android.motionphoto.viewer|Advanced|Motion photo viewer that only you can watch videos on it also diagmonage
com.samsung.android.mydevice|Advanced|This app shows your devices on the QuickPanel.
com.samsung.android.net.wifi.wifiguider|Advanced|Wi-Fi Tips Shows a question-mark button next to Wi-Fi networks that fail
com.samsung.android.networkdiagnostic|Recommended|Network Diagnostic Autostart after boot.
com.samsung.android.oneconnect|Recommended|Samsung Smart Things  Lets you manage all your Samsung and SmartThings-c
com.samsung.android.opencalendar|Recommended|Samsung Subscription Calendars.
com.samsung.android.peripheral.framework|Recommended|Peripheral Framework It's knox analytics.
com.samsung.android.personalpage.service|Recommended|Private mode (was replaced by Secure Folder)
com.samsung.android.photoremasterservice|Advanced|PhotoRemasterService If removed, Photo Remaster in the gallery won't wor
com.samsung.android.privacydashboard|Advanced|Privacy dashboard in settings:
com.samsung.android.privateaccesstokens|Recommended|Provides a system module used by Samsung Internet for an automatic verif
com.samsung.android.privateshare|Recommended|Blockchain-powered file sharing system for Samsung phones.
com.samsung.android.provider.filterprovider|Advanced|FilterProvider dependency to Samsung Camera Provides access to filters (
com.samsung.android.provider.shootingmodeprovider|Advanced|Provide camera modes (when you swipe left from the camera app) Safe to r
com.samsung.android.provider.stickerprovider|Advanced|One more package related to camera stickers.
com.samsung.android.providers.context|Recommended|Spyware   Content providers encapsulate data, providing centralized mana
com.samsung.android.providers.factory|Advanced|FactoryTestProvider Disabling breaks hwmoduletest.
com.samsung.android.radiobasedlocation|Recommended|Tracks location in the background.
com.samsung.android.radiobasedlocation.manager|Recommended|Tracks location in the background.
com.samsung.android.rajaampat|Recommended|Samsung Pay Samsung Pay Services for Indonesia.
com.samsung.android.rampart|Recommended|Auto Blocker Uses for blocking unknown apps install, commands.
com.samsung.android.rlc|Recommended|Samsung Biz Service Remote Lock Control?
com.samsung.android.rubin.app|Recommended|Customization Service Collects a massive amount of data  Basically every
com.samsung.android.samsungpass|Recommended|Samsung Pass app
com.samsung.android.samsungpassautofill|Recommended|Auto Fill for Samsung Pass Once your account information is registered,
com.samsung.android.samsungpositioning|Recommended|Run at startup and ask for an unknown permission SEC_FACTORY_PHONE This
com.samsung.android.scloud|Recommended|Samsung Cloud
com.samsung.android.scloud.auth|Recommended|Handle authentication for Samsung cloud
com.samsung.android.scloud.backup|Recommended|Samsung Backup only for Samsung Cloud
com.samsung.android.scloud.proxy.calendar|Recommended|Completely empty package.
com.samsung.android.scloud.proxy.contacts|Recommended|Completely empty package.
com.samsung.android.scloud.proxy.memo|Recommended|Completely empty package.
com.samsung.android.scloud.proxy.sbrowser|Recommended|Completely empty package.
com.samsung.android.scloud.proxy.snote3|Recommended|Completely empty package.
com.samsung.android.scloud.quota|Recommended|Literally all this app does is tell you when you're out of storage on yo
com.samsung.android.scloud.sync|Recommended|Samsung cloud synchronisation service
com.samsung.android.sconnect|Recommended|Samsung Quick connect (discontinued?) In theory, it lets you connect you
com.samsung.android.scpm|Advanced|Samsung Cloud Platform Manager Related to Samsung Cloud.
com.samsung.android.scs|Advanced|Samsung Core Services (SCS) It's an application for quick and easy provi
com.samsung.android.sdk.handwriting|Recommended|Handwriting Service Only for Samsung Note?
com.samsung.android.sdk.ocr|Advanced|It's for OCR (Optical Character Recognition), used among Samsung apps li
com.samsung.android.sdk.professionalaudio.app.audioconnectionservice|Recommended|AudioConnectionService I believe it allows to modulate an audio signal.
com.samsung.android.sdk.professionalaudio.utility.jammonitor|Recommended|Professional Audio Allows you to create virtual instrument applications
com.samsung.android.sdk.spenv10|Recommended|Samsung Pen engine, Pen settings, SPen Engine Update.
com.samsung.android.sdm.config|Recommended|Configuration Update for Samsung Deskphone Manager (SDM) SDM allows a us
com.samsung.android.securitylogagent|Recommended|Security Log Agent  Runs in the background and monitors your device for
com.samsung.android.server.iris|Advanced|Provides iris recognition feature NOTE: removing this package causes bio
com.samsung.android.server.wifi.mobilewips|Recommended|MobileWips Detects suspicious activities on Wi-Fi
com.samsung.android.server.wifi.mobilewips.client|Advanced|MobileWips Detects suspicious activity on Wi-Fi network.
com.samsung.android.service.health|Advanced|Health Platform  It is a data aggregator.
com.samsung.android.service.livedrawing|Recommended|Live Message enables you to draw your own animated GIFs or emojis.
com.samsung.android.service.peoplestripe|Recommended|People Edge Gives you immediate access to your favorite contacts from th
com.samsung.android.service.stplatform|Recommended|SmartThings Framework I guess it's app for installing sponsored apps.
com.samsung.android.service.tagservice|Advanced|Tags in Samsung Gallery app.
com.samsung.android.service.travel|Recommended|Samsung Travel Wallpaper (discontinued)
com.samsung.android.setupindiaservicestnc|Recommended|Samsung Services Responsible for the persistent notification after every
com.samsung.android.shealthmonitor|Advanced|Samsung Health Monitor Enables you to record ECG and blood pressure.
com.samsung.android.shortcutbackupservice|Recommended|ShortcutBNR Related to smartSwitch Samsung Cloud features
com.samsung.android.singletake.service|Advanced|Single Take It's face unlock I guess.
com.samsung.android.slinkcloud|Recommended|Samsung Cloud Gateway NEEDED FOR Scloud app A cloud storage gateway is d
com.samsung.android.sm|Recommended|Smart Manager app Provides pretty useless optimizing features using Chin
com.samsung.android.sm.devicesecurity|Advanced|Samsung Device security for the Smart Manager app using McAfee antivirus
com.samsung.android.sm.devicesecurity.tcm|Recommended|Device security Chinese scanning app for searching viruses.
com.samsung.android.sm.provider|Recommended|Smart Manager Provider SCPM Client?
com.samsung.android.smartcallprovider|Recommended|Samsung Smart Call Provides caller profile information to help consumers
com.samsung.android.smartface|Advanced|SmartFaceService Used to automatically detects faces when using the Sams
com.samsung.android.smartface.overlay|Advanced|Not needed overlay to smartface
com.samsung.android.smartfitting|Recommended|Smart Fitting Service Adapts the size/ratio of the screen.
com.samsung.android.smartmirroring|Advanced|Samsung Smart View Enable you to mirror screen your phone to a TV  Depen
com.samsung.android.smartprovider|Recommended|Samsung File Provider Provides intelligent classification, search and im
com.samsung.android.smartsuggestions|Recommended|Smart suggestions
com.samsung.android.smartswitchassistant|Recommended|Samsung SmartSwitch Lets you transfer your data from your old (Samsung)
com.samsung.android.softsim|Recommended|Softsim Service Installs softsim and it's full Chinese.
com.samsung.android.spay|Recommended|Samsung Pay  Samsung Pay is a mobile payment and digital wallet service
com.samsung.android.spayfw|Recommended|Samsung Pay Framework needed for Samsung Pay Samsung Pay is a mobile pay
com.samsung.android.spaymini|Recommended|Samsung Pay Mini Same service as Samsung Pay but for online payments onl
com.samsung.android.spdfnote|Recommended|Write on PDF  PDF annotator
com.samsung.android.sskds|Recommended|SoterSskdsService This app has something to 'com.tencent.soter.soterserv
com.samsung.android.stickercenter|Recommended|Sticker center.
com.samsung.android.stickerplugin|Recommended|StickerPlugin Not sure if this package also provides stickers for camera
com.samsung.android.ststub|Recommended|SmartThings Allows users to control, automate, and monitor their home en
com.samsung.android.sume.nn.service|Expert|Provides the photo remaster feature in the Gallery app.
com.samsung.android.svcagent|Recommended|The APK has various icons in it + DiagMon library (telemetry).
com.samsung.android.svoice|Recommended|Samsung Voice (S Voice) was replaced by bixby on Samsung Galaxy S8(+) an
com.samsung.android.svoiceime|Recommended|Samsung voice input Voice input powered by Bixby.
com.samsung.android.tadownloader|Advanced|Seems to check if a trusted application needs an update and downloads it
com.samsung.android.tapack.authfw|Advanced|AuthFw TaPack Authentication Framework for Trusted Application?
com.samsung.android.tencentwifisecurity|Recommended|Chinese Tencent flags risky networks and prevent to connect to them.
com.samsung.android.themecenter|Advanced|Galaxy Themes Service.
com.samsung.android.themestore|Advanced|Galaxy Themes.
com.samsung.android.tncpage|Recommended|TNCPageCN It's app for enhance lock screen.
com.samsung.android.tripwidget|Recommended|Discontinued package (used in Galaxy S4) handling trip wallpaper widget.
com.samsung.android.tzdata.update|Recommended|App for updating time zone changes on Samsung devices.
com.samsung.android.uds|Recommended|Ultra data saving Have boring notifications and theres no point of keepi
com.samsung.android.unifiedprofile|Recommended|My Profile Related to Samsung Members?
com.samsung.android.universalswitch|Recommended|Universal Switch lets you designate certain touches or gestures to contr
com.samsung.android.uwb|Recommended|UwbApplication It's used for SmartThings and new Smart Tags.
com.samsung.android.vdc|Recommended|Virtual Device Center It's Chinese app for Video calls.
com.samsung.android.vdcservice|Advanced|This app depends on Virtual Device Center app.
com.samsung.android.video|Advanced|Samsung Video Player
com.samsung.android.videolist|Advanced|Video player included on Samsung devices  Works fine enough as a basic v
com.samsung.android.visionarapps|Recommended|"AR apps"/"Makeup, Home Decor, and Sunglass Shack".
com.samsung.android.visioncloudagent|Recommended|VisionCloudAgent Cloud Agent is a service which automatically upload on
com.samsung.android.visionintelligence|Recommended|Bixby Vision Augmented reality camera that can identify objects in real-
com.samsung.android.visualars|Recommended|Smart Touch Call.
com.samsung.android.voc|Recommended|Samsung Members  The other version is "com.samsung.oh".
com.samsung.android.voicewakeup|Recommended|Voice wake-up for using Bixby
com.samsung.android.vtcamerasettings|Recommended|Video call effects Keep the focus on you during video calls by blurring
com.samsung.android.wallpaper.live|Recommended|Samsung official app for dynamic and live wallpapers with generative AI
com.samsung.android.wallpaper.res|Advanced|Pre-installed wallpapers Removing this package causes the background to
com.samsung.android.watch.alarm|Recommended|Samsung Alarm app.
com.samsung.android.watch.cameracontroller|Recommended|Mirrors phone's camera to your watch.
com.samsung.android.watch.compass|Recommended|Samsung Compass app.
com.samsung.android.watch.findmyphone|Advanced|The phone will start ringing, if connected to watch via BT or WiFi, when
com.samsung.android.watch.findmywatch|Advanced|The watch will start ringing, if connected to phone via BT or WiFi, when
com.samsung.android.watch.flashlight|Advanced|Samsung Flashlight
com.samsung.android.watch.runestone.app|Recommended|Customization Service from Samsung.
com.samsung.android.watch.screencapture|Advanced|Provides the ability to take screenshots from the smart watch.
com.samsung.android.watch.stopwatch|Recommended|Samsung Stopwatch
com.samsung.android.watch.timer|Recommended|Timer app from Samsung.
com.samsung.android.watch.watchface.analogmodular|Advanced|Preinstalled watchface.
com.samsung.android.watch.watchface.analoguefont|Advanced|Preinstalled watchface.
com.samsung.android.watch.watchface.animal|Advanced|Preinstalled watchface.
com.samsung.android.watch.watchface.aremoji|Advanced|Preinstalled watchface.
com.samsung.android.watch.watchface.basicclock|Advanced|Preinstalled watchface.
com.samsung.android.watch.watchface.basicdashboard|Advanced|Preinstalled watchface.
com.samsung.android.watch.watchface.bespoke|Advanced|Preinstalled watchface.
com.samsung.android.watch.watchface.bitmoji|Advanced|Preinstalled watchface.
com.samsung.android.watch.watchface.boldindex|Advanced|Preinstalled watchface.
com.samsung.android.watch.watchface.digitalfont|Advanced|Preinstalled watchface.
com.samsung.android.watch.watchface.digitalmodular|Advanced|Preinstalled watchface.
com.samsung.android.watch.watchface.dualwatch|Advanced|Preinstalled watchface.
com.samsung.android.watch.watchface.dynamicfont|Advanced|Preinstalled watchface.
com.samsung.android.watch.watchface.emergency|Advanced|Watchface in the emergency launcher.
com.samsung.android.watch.watchface.gradientfont|Advanced|Preinstalled watchface.
com.samsung.android.watch.watchface.healthmodular|Advanced|Preinstalled watchface.
com.samsung.android.watch.watchface.infomodular|Advanced|Preinstalled watchface.
com.samsung.android.watch.watchface.large|Advanced|Preinstalled watchface.
com.samsung.android.watch.watchface.livewallpaper|Advanced|Preinstalled watchface.
com.samsung.android.watch.watchface.mypebble|Advanced|Preinstalled watchface.
com.samsung.android.watch.watchface.myphoto|Advanced|Preinstalled watchface.
com.samsung.android.watch.watchface.mystyle|Advanced|Preinstalled watchface.
com.samsung.android.watch.watchface.premiumanalog|Advanced|Preinstalled watchface.
com.samsung.android.watch.watchface.simpleanalogue|Advanced|Preinstalled watchface.
com.samsung.android.watch.watchface.simpleclassic|Advanced|Preinstalled watchface.
com.samsung.android.watch.watchface.simplecomplication|Advanced|Preinstalled watchface.
com.samsung.android.watch.watchface.superfiction|Advanced|Preinstalled watchface.
com.samsung.android.watch.watchface.tickingsound|Advanced|Ticking sound on watchfaces that support it.
com.samsung.android.watch.watchface.together|Advanced|Preinstalled watchface.
com.samsung.android.watch.watchface.typography|Advanced|Preinstalled watchface.
com.samsung.android.watch.watchface.weather|Advanced|Preinstalled watchface.
com.samsung.android.watch.weather|Recommended|Weather application from Samsung.
com.samsung.android.watch.worldclock|Recommended|Worldclock app.
com.samsung.android.wcs.exstention|Advanced|Samsung Internet Extensions Samsung Internet for Android allows users to
com.samsung.android.wear.musictransfer|Advanced|Used to sync music with the watch.
com.samsung.android.wear.shealth|Advanced|Samsung Health app for WearOS.
com.samsung.android.wearable.setupwizard|Recommended|Samsung Wearable Setup Wizard The first time you turn your device on, a
com.samsung.android.weather|Recommended|Unified Daemon app.
com.samsung.android.wellbeing|Advanced|Digital Wellbeing That's an app for device and app usage tracking and li
com.samsung.android.widget.pictureframe|Advanced|Samsung gallery widget, shows your selected photos as widget
com.samsung.android.widgetapp.yahooedge.finance|Recommended|Special edge panel widget for Yahoo Finance
com.samsung.android.widgetapp.yahooedge.news|Recommended|Special edge panel widget for Yahoo News
com.samsung.android.widgetapp.yahooedge.sport|Recommended|Special edge panel widget for Yahoo Sport
com.samsung.android.wifi.ai|Advanced|No activities, provides WiFi diagnostics, has several ai models that it
com.samsung.android.wifi.p2paware.resources|Advanced|Related to WiFi Direct
com.samsung.android.wifi.sbs.resources|Advanced|WifiRROverlayAppSBS
com.samsung.android.wifinetworkdiagnostics|Recommended|WifiNetworkDiagnostics Diagnostics network connection.
com.samsung.app.highlightplayer|Recommended|Samsung Story Video Editor Lets you edit your videos stories
com.samsung.app.jansky|Recommended|Multi-lines settings Lets you have multiple virtual phone numbers.
com.samsung.app.newtrim|Advanced|Samsung Video trimmer (Editor Lite) Lets you quickly trim video files (f
com.samsung.app.slowmotion|Advanced|Slowmotion mode in camera app
com.samsung.app.video|Recommended|Can't figure out what this package does, but no adverse effects to remov
com.samsung.attribution|Recommended|Related to data collection (?):
com.samsung.attvvm|Recommended|Visual Voicemail Simple GUI for voicemail
com.samsung.carrier.logcollector|Recommended|it's the same like diagmonagent, collects dumplogs
com.samsung.chn.apps.devicemanagement|Recommended|AutoLoginService Register device with China Telecom.
com.samsung.clipboardsaveservice|Advanced|Clipboard Save service saves all the content you saved in the clipboard
com.samsung.cmfa.AuthTouch|Advanced|Continuous Multi-Factor Authentication AuthTouchService Related to `com.
com.samsung.cmh|Advanced|CMH Provider is a dependency for the the samsung gallery app.
com.samsung.commonimsservice|Advanced|Stat notify volte
com.samsung.crane|Recommended|Call+  Call+ features on Samsung dialer NOTE: I have the feeling that th
com.samsung.daydream.customization|Recommended|Samsung customization for Google Daydream VR headset  NOTE : Google disc
com.samsung.dcmservice|Advanced|Hard to find what it really does but I do know what DCM is in telecommun
com.samsung.desktopsystemui|Recommended|Samsung DeX UI Extends your smartphone into a "desktop computing experie
com.samsung.discover|Recommended|It shows up as a pane on OneUI home and prompts you to install all sorts
com.samsung.discover.sep|Recommended|Related to `com.samsung.discover` but doesnt seem to do much on its own.
com.samsung.ecomm|Recommended|Samsung Shop  App where you can buy all (and only) Samsung products.
com.samsung.ecomm.global.in|Recommended|Samsung Shop  App where you can buy all (and only) Samsung products.
com.samsung.emergencyreporthelper|Recommended|Only for KOREA emergency report.
com.samsung.enhanceservice|Recommended|Enhanced service is the process for Samsung cloud messaging (equivalent
com.samsung.everglades.video|Advanced|Part of the TouchWiz video player.
com.samsung.faceservice|Recommended|Face service detection Analyzes all the photos in the Samsung Gallery to
com.samsung.felicalock|Recommended|FeliCa Password NFC things, not needed
com.samsung.fresco.logging|Recommended|Fresco Logging Service Fresco is an android library for managing images
com.samsung.gpuwatchapp|Recommended|This app has GPU Dumping mode, developer mode, game debugging.
com.samsung.groupcast|Recommended|Samsung Group Play (discontinued) Allows you to share pictures , documen
com.samsung.helphub|Recommended|Not sure if this package still exist.
com.samsung.hidden.china|Recommended|China Hidden Menu Last call, secret code: 319712358.
com.samsung.hiddennetworksetting|Advanced|Set of hidden network settings (including frequency band choice).
com.samsung.hongbaoassistant|Recommended|Hongbao Assistant Chinese app.
com.samsung.hs20provider|Recommended|only found useless hotspot logs
com.samsung.huxextension|Recommended|Hux Extension Verizon activation, registration
com.samsung.ims.smk|Advanced|SimMobilityKit VoLTE calling, IMS.
com.samsung.inputshare|Recommended|MultiControl Its use is to connect 2 devices and control one with the ot
com.samsung.ipservice|Recommended|Set of hidden network settings (including frequency bands choice) How to
com.samsung.japan.initonce|Recommended|InitOnce I found in code only name app
com.samsung.klmsagent|Advanced|Checks the validity of your KLM/KPE (Knox Licence Manager) license.
com.samsung.knox.appsupdateagent|Advanced|This app is part of Samsung Knox, managing updates for apps within the K
com.samsung.knox.knoxtrustagent|Recommended|Knox Quick Access allows users to access the Knox Workspace container us
com.samsung.knox.kss|Recommended|Knox Keyguard.
com.samsung.knox.rcp.components|Recommended|Knox Content Mgr Security-related.
com.samsung.knox.securefolder|Recommended|Secure Folder  Create a secure space on your device to encrypt and store
com.samsung.knox.securefolder.setuppage|Recommended|Provides the setup process when opening secure folder (com.samsung.knox.
com.samsung.locationhistory|Recommended|It's only location logs
com.samsung.logwriter|Recommended|LogWriter Writes data in a logs SQL database.
com.samsung.mdl.radio|Recommended|Samsung Milk Music (discontinued in 2016) It was a freemium online music
com.samsung.mdl.radio.radiostub|Recommended|Milk Music (shut down by Samsung) It was a music streaming app
com.samsung.memorysaver|Advanced|Helps you to free up space by letting you delete duplicates and move con
com.samsung.mlp|Recommended|Samsung content recognition.
com.samsung.mobiletv|Recommended|Hidden testing mobiletv
com.samsung.oda.service|Advanced|SamsungOdaService Disabling causes SIM Manager to instantly crash on dua
com.samsung.oh|Recommended|Samsung Members  Samsung community.
com.samsung.pickuptutorial|Recommended|A guide to some gestures on Samsung devices, however it doesn't seem to
com.samsung.preloadapp|Recommended|Install app Installs recommended apps.
com.samsung.qosindicator|Recommended|QOS - quality of service, but it's only for diagnostics.
com.samsung.rcs|Recommended|RCS (Rich Communication Services) Has permissions linked to com.samsung.
com.samsung.safetyinformation|Recommended|Safety information telling you to be careful with the usage of your phon
com.samsung.sait.sohservice|Recommended|FactoryApp This weird app collects battery data and probably others, who
com.samsung.samsungpssdplus|Recommended|Samsung Magician  Allows users to conveniently manage their Samsung Port
com.samsung.sec.android.application.csc|Advanced|Do something related to Country Specific Code (CSC).
com.samsung.sec.android.teegris.tui_service|Recommended|TuiService Security-related, but not necessary.
com.samsung.sec.mtv|Recommended|Hidden testing mobiletv
com.samsung.slsi.audiologging|Recommended|It's hidden Audiologging.
com.samsung.slsi.telephony.silentlogging|Recommended|Hidden network logging.
com.samsung.sprint.chameleon|Recommended|Chameleon service which is a service designed to store sprint-specific p
com.samsung.storyservice|Advanced|Samsung StoryService Create stories in the Gallery from your pictures an
com.samsung.svoice.sync|Recommended|Samsung Voice service
com.samsung.systemui.bixby|Recommended|System UI for Bixby/Bixby2
com.samsung.systemui.bixby2|Recommended|System UI for Bixby/Bixby2
com.samsung.systemui.hidenotch|Advanced|Hide notch overlay.
com.samsung.systemui.hidenotch.withoutcornerround|Advanced|Hide notch overlay without corner round.
com.samsung.tmovvm|Recommended|Samsung Visual Voicemail (for T-mobile only) Allows you to review and ma
com.samsung.tmowfc.wfccontroller|Advanced|Wifi Calling for T-mobile clients only!
com.samsung.tmowfc.wfcpref|Advanced|Wifi Calling for T-mobile clients only!
com.samsung.ucs.agent.ese|Recommended|eSE UCS Plugin is another package from UCS.
com.samsung.ucs.ucspinpad|Recommended|UcsPinpad In this app u can setup pin,puk UCS is a company which has par
com.samsung.unifiedtp|Advanced|Unified tethering provisioner.
com.samsung.upsmtheme|Advanced|Handle the theme of UPSM (Ultra Power Saving Mode) Safe to remove
com.samsung.visionprovider|Recommended|Provider for Bixby Vision (com.samsung.android.visionintelligence) Manag
com.samsung.voiceserviceplatform|Recommended|Samsung Voice (for Galaxy S7) Virtual mobile personal assistant capable
com.samsung.vvm|Recommended|Samsung Verizon Voicemail Allows you to review and manage your voicemail
com.samsung.vvm.se|Recommended|Samsung Verizon Voicemail Allows you to review and manage your voicemail
com.sec.allsharecastplayer|Recommended|Screen Mirroring (only in Galaxy S6) Cast your mobile screen to a TV.
com.sec.android.AutoPreconfig|Recommended|Auto Preconfig Tells you to format the device when sim from other countr
com.sec.android.CcInfo|Recommended|CcInfo Samsung logs/analytics.
com.sec.android.GeoLookout|Recommended|Geo News for korea or japanese
com.sec.android.Kies|Recommended|Required for connecting your device to Samsung Kies, Samsung's long disc
com.sec.android.Preconfig|Recommended|On some phones founded testing things menu and on some not found any hid
com.sec.android.QRreader|Recommended|Samsung QR code reader.
com.sec.android.RilServiceModeApp|Recommended|Service mode RIL hidden app.
com.sec.android.SamsungDrmProvider|Recommended|Completely empty package.
com.sec.android.UsimRegistrationKOR|Advanced|USIM Registration.
com.sec.android.allshare.service.controlshare|Recommended|Completely empty package.
com.sec.android.allshare.service.fileshare|Recommended|Completely empty package.
com.sec.android.allshare.service.mediashare|Recommended|Share media to your other Samsung devices with Allshare.
com.sec.android.app.DataCreate|Recommended|Automation Test Another hidden test app.
com.sec.android.app.FileShareClient|Recommended|Receive files from Samsung devices with Allshare.
com.sec.android.app.FileShareServer|Recommended|Send files to Samsung devices with Allshare.
com.sec.android.app.SecSetupWizard|Advanced|Samsung Setup Wizard The first time you turn your device on, a welcome s
com.sec.android.app.SmartClipService|Advanced|Default image editor on older Samsung devices.
com.sec.android.app.aftersalecamera|Recommended|Calibration Camera app.
com.sec.android.app.apex|Advanced|Samsung ApexService Enables taking motion photos and provides a motion p
com.sec.android.app.applinker|Recommended|Related to FeliCa Networks  /  FeliCa is contactless RFID smart card sys
com.sec.android.app.bcocr|Recommended|Business card recognition Something about business cards not very useful
com.sec.android.app.billing|Recommended|Samsung billing/Checkout Used to purchase apps through Samsung Store app
com.sec.android.app.bluetoothagent|Recommended|Bluetooth Agent Bluetooth test service.
com.sec.android.app.bluetoothtest|Recommended|Bluetooth Test Hidden feature accessible by entering *#*#232331#*#* in t
com.sec.android.app.camera|Advanced|Samsung camera app Safe to remove (but not recommended)
com.sec.android.app.camera.plb|Recommended|Looks like an app to record videos for Facebook.
com.sec.android.app.camera.sticker|Recommended|Samsung DECO PIC.
com.sec.android.app.chromecustomizations|Recommended|Samsung stuff on the homepage of Google Chrome
com.sec.android.app.clipvideo|Recommended|App to create a video collage.
com.sec.android.app.desktoplauncher|Recommended|Samsung DeX Home (launcher) Dex enables users to extend their device int
com.sec.android.app.dexonpc|Recommended|Samsung DeX Extends your smartphone into a "desktop computing experience
com.sec.android.app.dictionary|Recommended|Samsung Dictionary is is an app that enables you to manage all the dicti
com.sec.android.app.easylauncher|Recommended|TouchWiz easy home -- gives you large icons and makes things the home sc
com.sec.android.app.easysetup|Recommended|Core of Samsung SmartThings (formerly Samsung Easy Setup) See com.samsun
com.sec.android.app.ewidgetatt|Recommended|Entertainment Widget AT&T Widget for OneUI
com.sec.android.app.factorykeystring|Recommended|DeviceKeyString : Dialable hidden diagnostic/debug app Dial *#0283# to o
com.sec.android.app.felicatest|Recommended|FeliCa Test, hidden debugs, logs HAL_Nfc
com.sec.android.app.filtermanager|Advanced|Manage camera filters on your Samsung device.
com.sec.android.app.firewall|Recommended|Blocked calls'msgs Chinese blocking calls and spam.
com.sec.android.app.fm|Advanced|Samsung Radio Listen to FM radio stations
com.sec.android.app.gamehub|Recommended|Samsung Game Hub Was replaced by "com.samsung.android.game.gamehome"
com.sec.android.app.hwmoduletest|Recommended|HwModuleTest, a hardware hidden test app (dial *#0*# to open it).
com.sec.android.app.interactivekeyguardtutorial|Recommended|A guide on how to unlock your phones screen, however it doesn't seem to
com.sec.android.app.kidshome|Recommended|This app provides a child-friendly interface and controls on Samsung dev
com.sec.android.app.magnifier|Recommended|Lets you use your device as a magnifying glass making it easier to read
com.sec.android.app.mediasync|Recommended|Share music for group play.
com.sec.android.app.minimode.res|Recommended|Minimode will be not available when this app be removed
com.sec.android.app.mobileprint|Advanced|Samsung print service for supported printers.
com.sec.android.app.mt|Recommended|Mobile tracker security feature.
com.sec.android.app.music|Recommended|Samsung's proprietary music app, allows you to play local music or conne
com.sec.android.app.mv.player|Recommended|Share videos to other devices.
com.sec.android.app.ocr|Recommended|Optical Read (feature replaced by Bixby Vision : com.samsung.android.vis
com.sec.android.app.parser|Recommended|DRParser Mode Secret code parser Support for hidden samsung apps launche
com.sec.android.app.personalization|Advanced|Has something to do with personalization?
com.sec.android.app.popupuireceiver|Recommended|Not needed popupui with battery animation charging or other system notif
com.sec.android.app.qsfastpairoverlay|Advanced|About half street overlay, has to gms.
com.sec.android.app.quicktool|Recommended|The Quick Tools panel includes a ruler, a compass and a torch.
com.sec.android.app.rakein|Recommended|Completely empty package.
com.sec.android.app.ringtoneBR|Recommended|Samsung ringtone backup/restore feature Where is this feature?
com.sec.android.app.safetyassurance|Advanced|Safety assurance is related to emergency features.
com.sec.android.app.samsungapps|Expert|Samsung Galaxy Store Samsung's app store, required for the Samsung Pay f
com.sec.android.app.saspmodemailerprovider|Recommended|docomo mail no activities, looks like more useless frameworks also it's
com.sec.android.app.sbrowser|Recommended|Samsung Internet  Don't use this browser.
com.sec.android.app.sbrowser.lite|Recommended|Samsung Internet Lite/Go Lite version of the Samsung browser (hah!
com.sec.android.app.sbrowsertry|Recommended|Samsung Internet browser, there are much better browsers available and S
com.sec.android.app.scloud|Recommended|I guess it's the core of Samsung Cloud.
com.sec.android.app.servicemodeapp|Recommended|SysDump hidden app Low-level debugging and diagnostics tools (dial *#990
com.sec.android.app.setupwizard|Recommended|Samsung Setupwizard The Welcome screen which guides you through the basi
com.sec.android.app.setupwizardlegalprovider|Recommended|SetupWizardLegalProvider All the legal terms you need to accept when you
com.sec.android.app.shealth|Recommended|Samsung Health  Serves to track various aspects of daily life contributi
com.sec.android.app.sns3|Recommended|Samsung Galaxy (Only installed on older phone before Galaxy S7) Don't re
com.sec.android.app.snsimagecache|Recommended|SnsImageCache A lot logs, probably it's for image cache.
com.sec.android.app.soundalive|Recommended|Responsible for Dolby Atmos and other equalizer stuff (accessible from t
com.sec.android.app.suwscriptplayer|Recommended|SuwScriptPlayer Seems to be another test app which test some "scripts"
com.sec.android.app.sysscope|Recommended|Checks after every boot if the ROM and kernel have been modified.
com.sec.android.app.taskmanager|Recommended|Hidden app that shows uninstall unused apps(+boring notifications that m
com.sec.android.app.tourviewer|Recommended|3d tour image, Virtual tour, Viewer used to view photos taken with the G
com.sec.android.app.translator|Recommended|Samsung Translater (S Translater)
com.sec.android.app.uwbtest|Recommended|UwbTest for testing UWB in the factory
com.sec.android.app.ve.vebgm|Recommended|Samsung Editing Assets ("Video Editor BackGround Music").
com.sec.android.app.vepreload|Advanced|Samsung video editor Lets you add add transitions, music, stickers and t
com.sec.android.app.videoplayer|Advanced|TouchWiz video player.
com.sec.android.app.voicenote|Recommended|Samsung Voice recorder
com.sec.android.app.volumemonitorprovider|Recommended|VolumeMonitorProvider used in Digital Wellbeing to average the dB heard
com.sec.android.app.wallpaperchooser|Advanced|This app provides the functionality to select and manage wallpapers on S
com.sec.android.app.wfdbroker|Recommended|I found it's for connecting to tv also settings but app size is too smal
com.sec.android.app.withtv|Recommended|Samsung Smart View Allows you to cast your phone screen to the TV.
com.sec.android.app.wlantest|Recommended|wlan test Hidden test app responding to the #232339*# and *#232338*# sec
com.sec.android.app.wluc|Recommended|Completely empty package.
com.sec.android.autodoodle.service|Recommended|Samsung Auto Doodle for Photo Editor
com.sec.android.automotive.drivelink|Recommended|Car mode it's hidden app and has a lot duplicate settings.
com.sec.android.automotive.drivelinkremote|Recommended|DriveLinkRemote I found only Hello world!
com.sec.android.casual2|Advanced|Casual theme
com.sec.android.classic2|Advanced|Classic theme
com.sec.android.cloudagent|Recommended|Part of Dropbox integration on TouchWiz
com.sec.android.cloudagent.dropboxoobe|Recommended|Get a free 50GB Dropbox account with your Samsung device!
com.sec.android.cover.ledcover|Recommended|Samsung LED cover service Display stuff on the LED case.
com.sec.android.daemonapp|Recommended|Unified Daemon app provides support for a number of different apps on yo
com.sec.android.desktopcommunity|Recommended|Samsung DeX panel
com.sec.android.desktopmode.uiservice|Recommended|Samsung DeX Extends your smartphone into a "desktop computing experience
com.sec.android.dexsystemui|Advanced|Samsung DeX System UI Safe to remove if not use connect phone for exampl
com.sec.android.diagmonagent|Advanced|Diagnostic Monitoring Agent Used to send diagnostic data to Samsung Data
com.sec.android.directshare|Recommended|Part of Samsung's SBeam service.
com.sec.android.easyMover|Recommended|Samsung Smart Switch Mobile  Allows you to easily transfer content (cont
com.sec.android.easyMover.Agent|Recommended|Smart Switch Agent Needed to use Smart Switch.
com.sec.android.easyonehand|Recommended|Samsung Easy One Hand mode Allows you to temporarily scale down the disp
com.sec.android.easysettings|Recommended|Settings app if you're using the Easy Mode feature on Samsung devices.
com.sec.android.emergencylauncher|Advanced|Samsung Launcher when in emergency(low battery) mode.
com.sec.android.emergencymode.service|Advanced|Emergency mode enables you to extend your device’s standby time when in
com.sec.android.fido.uaf.asm|Recommended|Fido is a set of open technical specifications for mechanisms of authent
com.sec.android.fido.uaf.client|Recommended|Fido UAF client.
com.sec.android.fotaclient|Advanced|OTA for legacy Samsung devices.
com.sec.android.fwupgrade|Recommended|Updates AllShare components.
com.sec.android.gallery3d|Advanced|Samsung Gallery app  Note: Samsung Gallery is a dependency for the camer
com.sec.android.gallery3d.panorama360view|Advanced|Let you see panoramic photos in the samsung Gallery.
com.sec.android.game.gamehome|Recommended|Samsung Game launcher Centralizes all your android games.
com.sec.android.iaft|Recommended|iaft Has permission dump, trace errors probably.
com.sec.android.inputmethod.beta|Advanced|Samsung Keyboard Neural Beta Smarter Samsung Keyboard with better predic
com.sec.android.inputmethod.iwnnime.japan|Advanced|Samsung Japanese keyboard
com.sec.android.kies|Recommended|No activities, only useless frameworks in classes.dex
com.sec.android.llmpolicy|Recommended|LLMPolicyService Low latency network to game?
com.sec.android.mimage.avatarstickers|Recommended|Samsung My Emoji Stickers Let you turn yourself into an emoji.
com.sec.android.mimage.gear360editor|Recommended|360 Photo Editor Lets you edit the 360-degree photos you took.
com.sec.android.mimage.photoretouching|Advanced|Samsung Photo Editor Disabling this will disable the inbuilt photo edito
com.sec.android.mmapp|Recommended|Required for Samsung's proprietary music app to work.
com.sec.android.nearby.mediaserver|Recommended|Samsung nearby share, part of AllShare.
com.sec.android.ofviewer|Advanced|Samsung selective focus camera mode.
com.sec.android.omc|Recommended|OM Customize I found only things for Vodafone.
com.sec.android.overlay.dcmconfig|Advanced|Static overlay that sets "config_systemContacts" to "docomo phonebook" (
com.sec.android.overlay.dcmconfig.sms|Advanced|Static overlay that sets "config_defaultSms" to "+Message" (com.nttdocom
com.sec.android.pagebuddynotisvc|Recommended|PageBuddyNotiSvc I only found it's for notifications to car, earphones,
com.sec.android.preloadinstaller|Recommended|Very shady apk.
com.sec.android.provider.badge|Advanced|Provider for the notification badges (which are not very useful IMHO) Pr
com.sec.android.provider.emergencymode|Advanced|Provider for emergency mode (com.sec.android.emergencylauncher) Content
com.sec.android.provider.logsprovider|Recommended|LogsProvider It's app used for logs.
com.sec.android.provider.snote|Recommended|Content provider for S Note  Content providers encapsulate data, providi
com.sec.android.providers.mapcon|Recommended|Mapcon Provider Only name app founded.
com.sec.android.providers.security|Recommended|Provider of password security policies?
com.sec.android.providers.tasks|Recommended|Tasks provider This app have nothing in code, only name and useless perm
com.sec.android.romantic2|Advanced|Sweet theme
com.sec.android.sdhms|Advanced|Samsung Device Health Manager Service Battery estimation service for Sam
com.sec.android.service.bezel|Recommended|Completely empty package.
com.sec.android.service.cm|Recommended|Completely empty package.
com.sec.android.service.health|Recommended|Samsung Health Service Needed for Samsung Health (com.sec.android.app.sh
com.sec.android.settingsmaps|Recommended|My places I found Search location also theres other stuff about location
com.sec.android.sidesync30|Recommended|SideSync (discontinued) Lets you share the screen and data between your
com.sec.android.smartfpsadjuster|Recommended|SmartFPSAdjuster Responsible for thermal camera adjustment and dynamic F
com.sec.android.splitsound|Advanced|SplitSoundService Provides ability to play music on the smartphone and a
com.sec.android.stub.paywithpaypal|Recommended|Pay with PayPal Hidden app that wanna to install PayPal to your phone.
com.sec.android.theme.natural|Advanced|Natural theme
com.sec.android.uibcvirtualsoftkey|Recommended|UIBC (User input back channel) Allows users to experience the dual monit
com.sec.android.usermanual|Recommended|Opens the device’s built‑in user manual PDF
com.sec.android.wallpapercropper2|Advanced|Samsung Wallpaper.
com.sec.android.wfds.printplugin|Advanced|Needed for Wi-Fi direct printing on Samsung devices.
com.sec.android.widget.samsungapps|Recommended|Widget for Galaxy Apps, showing app suggestions on your home screen.
com.sec.android.widgetapp.SPlannerAppWidget|Recommended|Calendar Widget
com.sec.android.widgetapp.activeapplicationwidget|Recommended|Completely empty package.
com.sec.android.widgetapp.ap.hero.accuweather|Recommended|Weather app
com.sec.android.widgetapp.ap.hero.weathernewsjp|Recommended|japanese samsung weather app
com.sec.android.widgetapp.digitalclock|Recommended|Clock (digital) widget
com.sec.android.widgetapp.digitalclockeasy|Recommended|Clock (digital easy) widget
com.sec.android.widgetapp.diotek.smemo|Recommended|Samsung Memo widget (was replaced by Samsung Note : com.samsung.android.
com.sec.android.widgetapp.dualclockdigital|Recommended|Dual Clock Widget
com.sec.android.widgetapp.easymodecontactswidget|Recommended|Favourite Contacts widget Lets you add favorite contacts to home screen
com.sec.android.widgetapp.samsungapps|Recommended|Galaxy Essential widget Galaxy Essentials is a collection of specially c
com.sec.android.widgetapp.webmanual|Recommended|User Manual
com.sec.android.yellowpage|Recommended|Yellow Page Contain phone numbers of companies and services.
com.sec.app.RilErrorNotifier|Recommended|RilNotifier Debug app for the RIL See "com.sec.android.RilServiceModeApp
com.sec.app.samsungprintservice|Recommended|Samsung Print Service Plugin.
com.sec.automation|Advanced|Tethering Automation enables sharing phone internet to the PC with a usb
com.sec.bcservice|Recommended|Broadcast Service Diagnostic/debug hidden app.
com.sec.clocationservice|Recommended|CLocationService Network location provider only to Chinese.
com.sec.downloadablekeystore|Recommended|Keystore is a secure place provided by Android to store cryptographic ke
com.sec.dsm.phone|Recommended|Completely empty package.
com.sec.dsm.system|Recommended|Part of Samsung's Find my Mobile service, appears this app is so you can
com.sec.enterprise.knox.attestation|Recommended|KNOX Attestation Lets you check the integrity of a Samsung Android devic
com.sec.enterprise.knox.cloudmdm.smdms|Recommended|Knox Enrollment Service mdm = mobile device management = software used b
com.sec.enterprise.knox.shareddevice.keyguard|Recommended|KNOX shared device keyguard.
com.sec.enterprise.mdm.services.simpin|Recommended|Enterprise Sim Pin Service I couldn't find information about this packag
com.sec.enterprise.mdm.vpn|Recommended|Enterprise VPN service I couldn't find information about this package.
com.sec.epdgtestapp|Recommended|Test app for ePDG (see com.sec.epdg)
com.sec.esdk.elm|Recommended|ELM Agent Hidden ELM for Developers Logs
com.sec.everglades|Recommended|Samsung Hub (discontinued) It was a cloud-based music service launched b
com.sec.everglades.update|Recommended|SamsungHub Updater (discontinued - See above)
com.sec.facatfunction|Recommended|FacAtFunction UI Display, Sensors Tests
com.sec.factory|Recommended|Device Test app Diagnostic hidden app.
com.sec.factory.camera|Recommended|Camera Test (dial *#34971539# to open CameraFirmware Standard)
com.sec.factory.cameralyzer|Recommended|Cameralyzer A factory testing app that allows manufacturers to check for
com.sec.factory.iris.usercamera|Recommended|Camera Iris User Test (by dialing *#0*#)
com.sec.hearingadjust|Recommended|Samsung Adapt Sound Configures a sound profile according to your ears.
com.sec.hiddenmenu|Recommended|IOTHiddenMenu Hidden menu used to access other hidden debug apps (those
com.sec.ims.android|Advanced|IMS Framework Needed for Wifi calling
com.sec.imslogger|Recommended|IMS Logger provides logging opt-ins.
com.sec.internal.vsim.VSimServiceApp|Advanced|Non Sim Device Solution (NSDS) needed for VoLTE and VoWifi (Wifi Calling
com.sec.kidsplat.installer|Recommended|Kids Mode (replaced by Kids Home : com.samsung.android.kidsinstaller) Sa
com.sec.knox.bluetooth|Recommended|KNOX bluetooth  NOTE : This does not affect regular bluetooth.
com.sec.knox.bridge|Recommended|Debug Bridge ?
com.sec.knox.containeragent2|Recommended|Samsung Knox Container (v2 ?)
com.sec.knox.foldercontainer|Recommended|Needed by KNOX Secure folder (com.samsung.knox.securefolder)
com.sec.knox.knoxsetupwizardclient|Recommended|KNOX SetupWizardClient The first time you turn your device on, a Welcome
com.sec.knox.packageverifier|Recommended|KNOX Verifier Used to scan installed packages
com.sec.knox.seandroid|Advanced|Samsung Knox security features.
com.sec.knox.shortcutsms|Recommended|Knox shortcut to switch to workspace
com.sec.knox.switcher|Advanced|Knox Secure Folder
com.sec.knox.switchknox|Recommended|Handles switches between KNOW/Work container and personal profile.
com.sec.knox.switchknoxI|Recommended|Handles switches between KNOW/Work container and personal profile.
com.sec.knox.switchknoxII|Recommended|Handles switches between KNOW/Work container and personal profile.
com.sec.location.nfwlocationprivacy|Recommended|Service provider location Not needed for location, I found something lik
com.sec.location.nsflp2|Recommended|Samsung Location SDK It seems to only be used by Samsung (Galaxy) apps
com.sec.mldapchecker|Recommended|MLDAP log LDAP (Lightweight Directory Access Protocol; I don't know what
com.sec.modem.settings|Recommended|Name : SilentLogging This package runs at startup and logs things (relat
com.sec.nfc.felicalocktest|Recommended|Testing FeliCa carrier remote lock.
com.sec.pcw.device|Recommended|Completely empty package.
com.sec.phone|Advanced|Another test/debug app used to test the proper functioning of phone call
com.sec.providers.assisteddialing|Recommended|Assisted Dialing I found mcc countrycode OTA lookup, not needed
com.sec.providers.settingsearch|Recommended|Completely empty package.
com.sec.readershub|Recommended|Samsung Books (discontinued) All-in-one e-Reading solution that offers i
com.sec.setdefaultlauncher|Recommended|Completely empty package.
com.sec.smartcard.manager|Recommended|Smart Card Manager Smart Card enables communication with Secure Elements
com.sec.spen.flashannotate|Recommended|Take notes on your screen.
com.sec.spp.push|Recommended|Samsung Push Service  Provides updates and notifications for services ex
com.sec.sprint.wfcstub|Recommended|Seems to be related to Wifi-Calling on Samsung phone.
com.sec.sve|Advanced|SecVideoEngineService is arguably a Samsung video engine service (handli
com.sec.svoice.lang.de_DE|Advanced|This is the German language pack for Samsung's old S Voice assistant, en
com.sec.svoice.lang.en_GB|Recommended|Language Pack for S-voice, the Samsung assistant (com.samsung.android.sv
com.sec.svoice.lang.en_US|Advanced|This is the English language pack for Samsung's old S Voice assistant, e
com.sec.svoice.lang.es_ES|Advanced|This is the Spanish language pack for Samsung's old S Voice assistant, e
com.sec.svoice.lang.fr_FR|Advanced|This is the French language pack for Samsung's old S Voice assistant, en
com.sec.svoice.lang.it_IT|Advanced|This is the Italian language pack for Samsung's old S Voice assistant, e
com.sec.svoice.lang.ru_RU|Advanced|This is the Russian language pack for Samsung's old S Voice assistant, e
com.sec.tcpdumpservice|Recommended|only found app name tcpdumpservice theres no code
com.sec.usbsettings|Advanced|USBSettings Hidden settings.
com.sec.vsim.ericssonnsds.webapp|Recommended|NSDSWebApp.
com.sec.vsimservice|Advanced|VSim Service Lets you use a virtual sim  Has a LOT of permissions (and i
com.sec.yosemite.phone|Recommended|Samsung WatchON (discontinued) It was a service allowing you to view pro
com.securityandprivacy.android.verizon.vms|Recommended|Digital Secure  I don't know why this apps is released twice on the Play
com.sem.factoryapp|Recommended|SEMFactoryApp Call home (172.217.168.14 --> Google IP).
com.sencatech.iwawa.iwawahome|Recommended|Pre-installed parental control bloatware with ads.
com.servicemagic.consumer|Recommended|HomeAdvisor: Contractors for Home Improvement  Helps you find local cont
com.setk.widget|Recommended|Galaxy Bizz  Useless app that recommands you stuff to do/buy nearby your
com.sfr.android.moncompte|Recommended|SFR & Moi  Lets you manage your SFR account
com.sfr.android.sfrcloud|Recommended|SFR Cloud  Cloud provided by SFR
com.sfr.android.sfrjeux|Recommended|My Games it's not useful app for games and better uninstall it
com.sfr.android.sfrmail|Recommended|SFR Mail
com.sfr.android.sfrplay|Recommended|SFR Play  VOD streaming from SFR.
com.sfr.android.vvm|Recommended|SFR Répondeur +  Lets you use your voice mail and manage your inbox with
com.sharecare.askmd|Recommended|AskMD (discontinued) provided by Sharecare Symptom checker app.
com.shopee.id|Recommended|Shopee 2.2  official app from Shopee, an e-commerce online shopping plat
com.skms.android.agent|Recommended|Samsung KMS agent service a client application for Android devices to su
com.skype.m2|Recommended|Skype Lite
com.skype.raider|Recommended|Skype
com.slacker.radio|Recommended|LiveXLive - Streaming Music and Live Events
com.smithmicro.netwise.director.comcast.oem|Recommended|XFINITY Wifi settings  Auto-connects you to XFINITY WiFi hotspot.
com.spotify.music|Recommended|Spotify app
com.sprint.android.musicplus2033|Recommended|Sprint Music Plus  Sprint’s official Music Store and player.
com.sprint.android.musicplus2033.vpl|Recommended|Sprint Music Plus  Sprint’s official Music Store and player
com.sprint.care|Recommended|My Sprint  Lets you manage your Sprint Account and pay your bill.
com.sprint.ce.updater|Recommended|Mobile Installer Used by Sprint to (force) install/update Sprint apps.
com.sprint.ecid|Recommended|Enhanced Caller ID Enable you to hide name and phone number when you mak
com.sprint.fng|Recommended|Sprint Spot  Provides Sprint customers a way to discover and access apps
com.sprint.international.message|Recommended|Sprint Worldwide Just an help page for international travelers.
com.sprint.ms.cdm|Recommended|Sprint Device Manager Mobile Device Management (MDM) allows company’s IT
com.sprint.ms.cnap|Recommended|Caller ID cnap = Caller Name Presentation  Lets you change the name that
com.sprint.ms.smf.services|Recommended|Sprint Hub  Enables Sprint features (including Wifi calling) and product
com.sprint.psdg.sw|Recommended|Carrier Setup Wizard The first time you turn your device on, a Welcome s
com.sprint.safefound|Recommended|Safe & Found  Mobile safety and security application that helps protect
com.sprint.safefound.vpl|Recommended|Safe & Found  Mobile safety and security application that helps protect
com.sprint.topup|Recommended|Doesn't exist anymore?
com.sprint.w.installer|Recommended|Sprint ID Provides mobile ID Packs featuring apps, ringers, wallpapers,
com.sprint.w.v8|Recommended|Old app Discover App (discontinued / new package name) Lets you discover
com.sprint.zone|Recommended|My Sprint Launcher?
com.staplegames.blocksClassicSGGP|Recommended|Block  Preinstalled game on some Samsung phones.
com.sukhavati.gotoplaying.bubble.BubbleShooter.mint|Recommended|Bubble Shooter And Friends.
com.swiftkey.swiftkeyconfigurator|Recommended|SwiftKey factory settings Used by commercial swiftkey partners to config
com.synchronoss.dcs.att.r2g|Recommended|AT&T Ready2Go (discontinued) Its purpose was to help you migrating your
com.taboola.mip|Recommended|Intrusive background module (Taboola) that keeps pushing a lock-screen s
com.talpa.hibrowser|Recommended|Hi Browser  Awful browser with embedded trackers and ads
com.talpa.share|Recommended|XShare Mini File Sharing App (via Bluetooth) with Google and Facebook tr
com.telcel.contenedor|Recommended|Telcel app, Advertising company to get promotions.
com.telecomsys.directedsms.android.SCG|Recommended|Verizon Location Agent Location tracking (does not impact GPS function i
com.telenav.app.android.bell|Recommended|Bell Navigator app.
com.telenav.app.android.cingular|Recommended|AT&T Navigator  Crappy GPS app provided by Telenav and rebranded by AT&T
com.telenav.app.android.scout_us|Recommended|Scout GPS Navigation & Meet Up  Bad GPS with bad chat features on top of
com.telus.checkup|Recommended|Checkup app (from Mobile Klinik; a Canadian store for buying, selling, a
com.telus.featuredapps|Recommended|Recommended apps from Telus.
com.telus.myaccount|Recommended|My TELUS app It's used for managing your telus account Safe to remove if
com.til.timesnews|Recommended|India News
com.tiqiaa.remote|Recommended|ZazaRemote  A Universal infrared control app full of trackers and with u
com.tmobile.echolocate|Recommended|T-Mobile Diagnostics
com.tmobile.pr.adapt|Recommended|Diagnostic tool.
com.tmobile.pr.mytmobile|Recommended|T-mobile app
com.tmobile.services.nameid|Recommended|Name ID T-Mobile (powered by Hiya or cequint if on Samsung devices) NOTE
com.tmobile.simlock|Recommended|Device Unlock.
com.tmobile.vvm.application|Recommended|T-Mobile Visual VoiceMail  Lets you use your voice mail and manage your
com.tracfone.preload.accountservices|Recommended|TracPhone / StraightTalk application.
com.tracker.t|Recommended|WTF is this ??
com.tripadvisor.tripadvisor|Recommended|Trip advisor  You should never trust and use trip advisor  (see 'Controv
com.tripledot.solitaire|Recommended|Solitaire  Preinstalled game on some Samsung phones.
com.tripledot.triple.tile.match.pair.game.three.master.object|Recommended|Triple Tile Pre-installed mobile game with ads and in-app purchases up t
com.tripledot.woodoku|Recommended|Woodoku  Preinstalled game on some Samsung phones.28 permissions, 24 tra
com.turner.cnvideoapp|Recommended|Cartoon network App
com.ubercab|Recommended|Uber  Uber Driver  Uber Eats  Uber does not protect personal user data a
com.ubercab.driver|Recommended|Uber  Uber Driver  Uber Eats  Uber does not protect personal user data a
com.ubercab.eats|Recommended|Uber  Uber Driver  Uber Eats  Uber does not protect personal user data a
com.ume.browser.northamerica|Recommended|UME Web Browser  Trackers and a LOT of permissions
com.vcast.mediamanager|Recommended|Verizon Cloud
com.verizon.cloudsetupwizard|Recommended|
com.verizon.llkagent|Recommended|Used for Verizon store demo mode.
com.verizon.loginengine.unbranded|Recommended|Carrier Login Engine Needed for wifi-calling.
com.verizon.messaging.vzmsgs|Recommended|Verizon Messages
com.verizon.obdm|Recommended|It's a set of metrics-related modules.
com.verizon.obdm_permissions|Recommended|D-MAT.
com.verizon.permissions.appdirectedsms|Recommended|Custom permissions for some verizon stuff?
com.verizon.permissions.vzwappapn|Recommended|Custom permissions used to set Verizon APN?
com.verizon.remoteSimlock|Recommended|VZWRemoteSimlockService Remote SimLock lock, unlock, looks more danger t
com.verizon.vzwavs|Recommended|Has a scary list of permissions.
com.verizontelematics.verizonhum|Recommended|Hum Family Locator
com.vznavigator.Generic|Recommended|VZ Navigator (GPS app)
com.vzw.ecid|Recommended|Verizon Call Filter  NOTE : Never trust a company which promotes call ID
com.vzw.hss.myverizon|Recommended|MyVerizon app
com.vzw.hss.widgets.infozone.large|Recommended|My InfoZone Widget Gives weekly tips, access to device info and account
com.vzw.qualitydatalog|Recommended|Logging stuff
com.wavemarket.waplauncher|Recommended|AT&T Secure Family  Parental control app.
com.wb.goog.got.conquest|Recommended|Game of Thrones: Conquest  Mobile game
com.whatsapp|Recommended|Whatsapp messenger.
com.whitepages.nameid.tmobile|Recommended|T-mobile NAME ID by WhitePages  Discontinued.
com.wing.wtsarcontrol|Recommended|Sar control, Chinese App, not available for normal users.
com.wingtech.sartest|Recommended|SarTest Secret code: 332.
com.wsomacp|Recommended|omacp = OMA Client Provisioning.
com.yahoo.mobile.client.android.liveweather|Recommended|Yahoo Weather  Please boycott Yahoo !
com.yellowpages.android.ypmobile|Recommended|Yellow Pages
com.yelp.android|Recommended|Yelp  Yelp lets users post reviews and rate businesses.
com.zaz.translate|Recommended|Hi Translate  Bloated translation app with a lot of trackers and permiss
com.zhiliaoapp.musically|Recommended|TikTok App Worth reading :
de.axelspringer.yana.zeropage|Recommended|Upday news for Samsung  Eats your phone's resources and runs in the back
de.telekom.tsc|Recommended|AppEnabler tsc = Telecom Service Center Used to display ads in notificat
deezer.android.app|Recommended|Deezer Online music streaming platform.
flipboard.app|Recommended|Flipboard News App
flipboard.boxer.app|Recommended|Briefing app  Same icon as Samsung Free
fr.bouyguestelecom.ecm.android|Recommended|Espace Client  Lets you manage your Bouygues account.
fr.bouyguestelecom.tv.android|Recommended|B.tv  Lets you watch TV from your phone.
fr.bouyguestelecom.vvmandroid|Recommended|This app wants to inject ads into your gallery.
fr.orange.cineday|Recommended|Orange cineday  Useless app but cineday is pretty nice.
hdopen.vivicitta|Recommended|Companion App for training, so bloated and from T-Mobile
hu.telekom.telekomapp|Recommended|Telekom App for subscriptions, so bloated (a lot spying) and from T-Mobi
hu.telekom.telekomtv|Recommended|Mobile shopping App for watch tv on phone, so bloated (a lot spying) and
id.co.babe|Recommended|BaBe  Indonesian news app.
in.amazon.mShop.android.shopping|Recommended|Amazon India
in.mohalla.sharechat|Recommended|ShareChat
in.playsimple.tripcross|Recommended|Crossword Jam  Preinstalled game on some Samsung phones.
in.playsimple.wordtrip|Recommended|World Trip  Word Count & word streak puzzle game 19 trackers + 11 permis
jp.co.daj.consumer.ifilter.aflauncher|Recommended|Something Japanese with useless code.
jp.co.nttdocomo.anshinmode|Recommended|Anshin-Filter for docomo  Carrier-provided parental controls.
jp.co.nttdocomo.carriermail|Recommended|docomo mail Carrier mail app.
jp.co.nttdocomo.lcsapp|Recommended|docomo location information Provides "Keitai-Osagashi Service" (Mobile P
jp.co.nttdocomo.saigaiban|Recommended|Disaster kit  A dedicated app for docomo's safety registration service u
jp.co.omronsoft.openwnn|Recommended|Japanese keyboard/IME (don't know why it's pre-installed on US/european
jp.co.omronsoft.wnnext.skin.std_dark_type2_HW|Recommended|Skin Dark mode to Japanese Keyboard.
jp.co.omronsoft.wnnext.skin.std_light_type2_HW|Recommended|Skin Light mode to Japanese Keyboard.
jp.co.yahoo.android.ebookjapan.preinstall|Recommended|eBookJapan Not needed japanese.
jp.gocro.smartnews.android|Recommended|SmartNews: Local Breaking News  Delivers the top trending news stories f
msgplus.jibe.sca.vpl|Recommended|Messaging Plus.
net.aetherpal.device|Recommended|AT&T Remote Support provided by aetherpal (was acquired by VMware) Allow
net.bat.store|Recommended|AHA Games Mobile game store.
net.fetnet.fetvod|Recommended|Streaming service Registered members can watch the movies, dramas, varie
net.sharewire.parkmobilev2|Recommended|ParkMobile - Find Parking
net.supertreat.solitaire|Recommended|Solitaire  Preinstalled game on some Samsung phones.
org.chromium.webview_shell|Recommended|Simple Browser for WebView tester used in AOSP.
org.codeaurora.gps.gpslogsave|Recommended|Saves GPS logs.
org.simalliance.openmobileapi.eseterminal|Advanced|Needed for NFC payments (e.g.
pl.tmobile.miboa|Recommended|My T-Mobile Has login activity and MailBox.
pl.tmobile.panel|Recommended|MyBox Require sim card to run app it's app store(discontinued?)
pl.zdunex25.updater|Recommended|Updater for the zdnex25's theme
ro.cosmote.aps.wnwlite|Recommended|TopApps(discontinued) it's non english app.
ro.orange.fotbal|Recommended|Orange Fotbal Preloaded on devices sold by Orange Romania.
ro.orange.orangefilm|Recommended|Orange Film Preloaded on devices sold by Orange Romania.
ro.orange.tvlive|Recommended|OrangeTV Go Preloaded on devices sold by Orange Romania.
telekom.hu.android.mobilvasarlas|Recommended|Mobile shopping App for shopping or paying, so bloated and from T-Mobile
tmobile.hu.android.epgmiab|Recommended|Newsreel Another app to tv things, so bloated and from T-Mobile, not nee
tv.fubo.mobile.vpl|Recommended|fuboTV  Lets you Watch live Sports, TV Shows, Movies & News
tv.peel.app|Recommended|Peel Universal Smart TV Remote Control (discontinued) Lets you remotely
uk.co.ee.myee|Recommended|Myee app  Lets you control your EE pay monthly, pay as you go and WiFi d
us.com.dt.iq.appsource.tmobile|Recommended|App Source (discontinued) This app aimed at organizing all of your exist
vendor.qti.qesdk.sysservice|Recommended|It's for debugging and logs.
zpub.res|Recommended|third-party app pre-installed on ZTE phones.
__KNOWN__
android.aosp.overlay
android.aosp.overlay.telephony
android.auto_generated_characteristics_rro
android.auto_generated_rro_product__
android.auto_generated_rro_vendor__
android.connectivity.base.overlay
android.ext.services
android.ext.shared
android.framework.res.rro.oneplus
android.frameworkres.overlay.Network
android.frameworkres.overlay.display.product
android.miui.home.launcher.res
android.miui.overlay
android.miui.overlay.telephony
android.miui.poco.launcher.res
android.overlay.common
android.overlay.common.all_devices
android.overlay.common.pong
android.overlay.common.spacewar
android.overlay.dynamicNavBar
android.overlay.gms.region.all
android.overlay.gms.region.eea
android.overlay.gms.region.ind
android.overlay.gms.region.row
android.overlay.multiuser
android.overlay.navbar
android.overlay.target
android.overlay.vivoresrro
android.overlay.vrro
android.res.overlay_305
android.res.overlay_310_311
android.romstats
android.rro_product
android.telephony.overlay.cmcc
com.android.BBKClock
com.android.BBKCrontab
com.android.DeviceAsWebcam
com.android.LGSetupWizard
com.android.SystemUIResOverlay
com.android.VideoPlayer
com.android.WingFactoryCamera
com.android.angle
com.android.backup
com.android.basicsmsreceiver
com.android.bbk.lockscreen3
com.android.bbklog
com.android.bbkmusic
com.android.bio.face.service
com.android.bips
com.android.bips.auto_generated_rro_product__
com.android.bips.overlay.pixel
com.android.bluetooth
com.android.bluetooth.auto_generated_rro_product__
com.android.bluetooth.bthelper
com.android.bluetooth.oplus.overlay
com.android.bluetooth.overlay
com.android.bluetooth.overlay.common
com.android.bluetooth.tempow
com.android.browser.res.overlay_305
com.android.camera.overlay
com.android.camera2
com.android.camerabigballconfig.overlay
com.android.cameraextensions
com.android.captiveportallogin
com.android.captiveportallogin.overlay
com.android.carrierconfig.overlay.miui
com.android.carrierconfig.overlay.product
com.android.carrierconfig.res.overlay_305
com.android.carrierconfig.res.overlay_310_311
com.android.cellbroadcast.overlay
com.android.cellbroadcastreceiver
com.android.cellbroadcastreceiver.basiccolorblack.overlay
com.android.cellbroadcastreceiver.basiccolorwhite.overlay
com.android.cellbroadcastreceiver.module
com.android.cellbroadcastreceiver.overlay.base.s600ww
com.android.cellbroadcastreceiver.overlay.pixel
com.android.cellbroadcastservice
com.android.cellbroadcastservice.overlay
com.android.cellbroadcastservice.overlay.pixel
com.android.certinstaller
com.android.companiondevicemanager.auto_generated_characteristics_rro
com.android.compos.payload
com.android.connectivity.resources
com.android.connectivity.resources.nt.overlay
com.android.connectivity.resources.overlay
com.android.cts.priv.ctsshim
com.android.defcontainer
com.android.devicediagnostics
com.android.devicediagnostics.auto_generated_rro_product__
com.android.devicediagnostics.auto_generated_rro_vendor__
com.android.devicelockcontroller
com.android.dialer
com.android.dialer.basiccolorblack.overlay
com.android.dialer.basiccolorwhite.overlay
com.android.dlna.service
com.android.documentsui
com.android.documentsui.a_overlay
com.android.dreams.overlay.basic
com.android.dreams.overlay.phototable
com.android.email.partnerprovider.overlay
com.android.emergency.basiccolorblack.overlay
com.android.emergency.basiccolorwhite.overlay
com.android.externalstorage
com.android.fileexplorer
com.android.flyme.bridge.softsim
com.android.frameworkhwext
com.android.frameworkhwext.dark
com.android.frameworkhwext.honor
com.android.frameworkhwnext.honor
com.android.frameworkres.overlay
com.android.gallery3d.refocus
com.android.globalFileexplorer
com.android.hbmsvmanager
com.android.hbmsvmanager.auto_generated_rro_product__
com.android.healthconnect.overlay
com.android.hotspot2
com.android.htmlviewer
com.android.huawei.HiMediaEngine
com.android.huawei.projectmenu
com.android.hwmirror
com.android.imedia.syncplay
com.android.imsserviceentitlement
com.android.incallui
com.android.incallui.overlay
com.android.inputdevices
com.android.inputmethod.pinyin
com.android.inputsettings.overlay.miui
com.android.intentresolver
com.android.internal.display.cutout.emulation.avoidAppsInCutout
com.android.internal.display.cutout.emulation.corner
com.android.internal.display.cutout.emulation.double
com.android.internal.display.cutout.emulation.hole
com.android.internal.display.cutout.emulation.narrow
com.android.internal.display.cutout.emulation.noCutout
com.android.internal.display.cutout.emulation.tall
com.android.internal.display.cutout.emulation.waterfall
com.android.internal.display.cutout.emulation.wide
com.android.internal.systemui.navbar.gestural
com.android.internal.systemui.navbar.gestural_extra_wide_back
com.android.internal.systemui.navbar.gestural_narrow_back
com.android.internal.systemui.navbar.gestural_wide_back
com.android.internal.systemui.navbar.hidegestural
com.android.internal.systemui.navbar.threebutton
com.android.internal.systemui.navbar.transparent
com.android.internal.systemui.navbar.twobutton
com.android.keychain
com.android.keyguard
com.android.launcher
com.android.launcher3
com.android.launcher3.tv
com.android.localtransport
com.android.location.fused
com.android.managedprovisioning
com.android.managedprovisioning.overlay
com.android.mediacenter
com.android.messaging
com.android.microdroid.empty_payload
com.android.midrive
com.android.mmi
com.android.mmifm
com.android.mmisubtest
com.android.mmitest
com.android.mms.overlay.cmcc
com.android.mms.overlay.ct
com.android.mms.service
com.android.mms.service.plugin.UaProfUrl
com.android.mms.service.plugin.UserAgent
com.android.modemnotifier
com.android.modulemetadata
com.android.mtp
com.android.music
com.android.networksettings.overlay.ct
com.android.networkstack.inprocess
com.android.networkstack.inprocess.overlay
com.android.networkstack.overlay
com.android.networkstack.overlay.miui
com.android.networkstack.permissionconfig
com.android.networkstack.tethering.inprocess
com.android.networkstack.tethering.inprocess.overlay
com.android.networkstack.tethering.overlay
com.android.nfc
com.android.nfc.auto_generated_rro_product__
com.android.nfc.auto_generated_rro_vendor__
com.android.nfc.overlay
com.android.nfc.overlay.common
com.android.omadm.service
com.android.omadm.service.auto_generated_rro_product__
com.android.omadm.service.auto_generated_rro_vendor__
com.android.ons
com.android.opasuwintegrationsample
com.android.otaprovisioningclient
com.android.overlay.calendar
com.android.overlay.camera2
com.android.overlay.circletosearch
com.android.overlay.contacts
com.android.overlay.dialer
com.android.overlay.dynamiciconconfig
com.android.overlay.email
com.android.overlay.emergency
com.android.overlay.fmradio
com.android.overlay.gallery3d
com.android.overlay.gmscontactprovider
com.android.overlay.gmssettingprovider
com.android.overlay.gmssettings
com.android.overlay.gmstelecomm
com.android.overlay.gmstelephony
com.android.overlay.iconrecentsconfig
com.android.overlay.launcher3
com.android.overlay.livepicker
com.android.overlay.logmanager
com.android.overlay.messaging
com.android.overlay.music
com.android.overlay.quicksearchbox
com.android.overlay.settings
com.android.overlay.soundrecorder
com.android.overlay.stk
com.android.overlay.systemui
com.android.overlay.wallpaper
com.android.overlay.wallpaperconfig
com.android.packageinstaller
com.android.pacprocessor
com.android.partnerbrowsercustomizations.btl.s600ww.overlay
com.android.partnerbrowsercustomizations.chromeHomepage.res.overlay_310_311
com.android.partnerbrowsercustomizations.example
com.android.pedometer
com.android.permissioncontroller
com.android.phone
com.android.phone.a_overlay
com.android.phone.auto_generated_characteristics_rro
com.android.phone.auto_generated_rro_product__
com.android.phone.auto_generated_rro_vendor__
com.android.phone.basiccolorblack.overlay
com.android.phone.basiccolorwhite.overlay
com.android.phone.cust.overlay.miui
com.android.phone.injection
com.android.phone.overlay.carriersettings
com.android.phone.overlay.common
com.android.phone.overlay.miui
com.android.phone.overlay.motcommon
com.android.phone.product.res.overlay
com.android.phone.product.res.overlay.no_imei_emergency_call
com.android.phone.res.overlay_305
com.android.pixeldisplayservice
com.android.pixeldisplayservice.auto_generated_rro_product__
com.android.poweroffhandlerapp
com.android.powertouch
com.android.printspooler
com.android.providers.applications
com.android.providers.blockednumber
com.android.providers.calendar
com.android.providers.calendar.overlay.base.s600ww
com.android.providers.contactkeys
com.android.providers.contacts
com.android.providers.contacts.auto_generated_rro_product__
com.android.providers.contacts.overlay.pixel
com.android.providers.downloads
com.android.providers.drm
com.android.providers.icccust
com.android.providers.media
com.android.providers.media.module
com.android.providers.media.overlay.pixel
com.android.providers.partnerbookmarks.res.overlay_305
com.android.providers.partnerbookmarks.res.overlay_310_311
com.android.providers.privacyprotection
com.android.providers.settings
com.android.providers.settings.auto_generated_rro_product__
com.android.providers.settings.auto_generated_rro_vendor__
com.android.providers.settings.btl.s600ww.overlay
com.android.providers.settings.overlay
com.android.providers.settings.overlay.base.s600ww
com.android.providers.settings.overlay.common
com.android.providers.settings.res.overlay_305
com.android.providers.tctdatahubprovider
com.android.providers.telephony
com.android.providers.telephony.auto_generated_characteristics_rro
com.android.providers.telephony.auto_generated_rro_product__
com.android.providers.telephony.overlay.carriersettings
com.android.providers.telephony.overlay.miui
com.android.providers.tv
com.android.providers.userdictionary
com.android.provision
com.android.provision.carrieroverlay.kddi
com.android.provision.overlay.miui
com.android.proxyhandler
com.android.qns
com.android.retaildemo.overlay.base.s600ww
com.android.rkpdapp
com.android.role.notes.enabled
com.android.runintest.ddrtest
com.android.safetycenter.resources.overlay
com.android.safetycenter.styles.overlay
com.android.safetyregulatoryinfo
com.android.safetyregulatoryinfo.auto_generated_rro_product__
com.android.screenshot
com.android.sdksandbox
com.android.sdm.plugins.connmo
com.android.sdm.plugins.dcmo
com.android.sdm.plugins.diagmon
com.android.se
com.android.secretcode
com.android.server.NetworkPermissionConfig
com.android.server.deviceconfig.resources
com.android.server.telecom
com.android.server.telecom.a_overlay
com.android.server.telecom.auto_generated_rro_product__
com.android.server.telecom.basiccolorblack.overlay
com.android.server.telecom.basiccolorwhite.overlay
com.android.server.telecom.overlay.common
com.android.server.telecom.overlay.miui
com.android.service.ims
com.android.settingaccessibility
com.android.settings
com.android.settings.auto_generated_rro_product__
com.android.settings.auto_generated_rro_vendor__
com.android.settings.basiccolorblack.overlay
com.android.settings.basiccolorwhite.overlay
com.android.settings.intelligence
com.android.settings.intelligence.basiccolorblack.overlay
com.android.settings.intelligence.basiccolorwhite.overlay
com.android.settings.os.overlay
com.android.settings.overlay.SettingsFuture
com.android.settings.overlay.cmcc
com.android.settings.overlay.common
com.android.settings.overlay.ct
com.android.settings.overlay.filesgoogle
com.android.settings.overlay.g1azg
com.android.settings.overlay.gb17l
com.android.settings.overlay.gb62z
com.android.settings.overlay.gx7as
com.android.settings.overlay.miui
com.android.settings.overlay.oversea
com.android.settings.overlay.personalsafety
com.android.settings.overlay.pixel2021
com.android.settings.overlay.product
com.android.settings.overlay.shiba
com.android.settings.overlay.turbo
com.android.settings.res.overlay_305
com.android.settings.res.overlay_310_311
com.android.settings.resoverlay
com.android.settings.shiba
com.android.settingsaccessibility
com.android.setupwizard.overlay
com.android.setupwizard.overlay.ontim
com.android.shell
com.android.simappdialog
com.android.simappdialog.auto_generated_rro_product__
com.android.sos
com.android.soundpicker
com.android.soundpicker.auto_generated_rro_product__
com.android.sprd.telephony.server
com.android.statementservice
com.android.stk.overlay.miui
com.android.stk.product.res.overlay.common
com.android.store
com.android.supl
com.android.systemui
com.android.systemui.auto_generated_rro_product__
com.android.systemui.auto_generated_rro_vendor__
com.android.systemui.clocks.bignum
com.android.systemui.clocks.calligraphy
com.android.systemui.clocks.flex
com.android.systemui.clocks.growth
com.android.systemui.clocks.inflate
com.android.systemui.clocks.numoverlap
com.android.systemui.clocks.weather
com.android.systemui.feature_cover
com.android.systemui.gesture.line.overlay
com.android.systemui.icon.overlay
com.android.systemui.miui.optimization.overlay
com.android.systemui.navigation.bar.overlay
com.android.systemui.notch.overlay
com.android.systemui.os.overlay
com.android.systemui.overlay
com.android.systemui.overlay.charging.anim.alita_supervooc2
com.android.systemui.overlay.charging.anim.siphon_wireless
com.android.systemui.overlay.cmcc
com.android.systemui.overlay.common
com.android.systemui.overlay.ct
com.android.systemui.overlay.fingerprint.anim.ccyh
com.android.systemui.overlay.fingerprint.anim.cosmos
com.android.systemui.overlay.fingerprint.anim.fireworks
com.android.systemui.overlay.fingerprint.anim.fy
com.android.systemui.overlay.fingerprint.anim.jhsy
com.android.systemui.overlay.fingerprint.anim.jslz
com.android.systemui.overlay.fingerprint.anim.lgsy
com.android.systemui.overlay.fingerprint.anim.nlgs
com.android.systemui.overlay.fingerprint.anim.none
com.android.systemui.overlay.fingerprint.anim.ripple
com.android.systemui.overlay.fingerprint.anim.stripe
com.android.systemui.overlay.fingerprint.anim.sw
com.android.systemui.overlay.fingerprint.anim.tyjw
com.android.systemui.overlay.fingerprint.anim.xklc
com.android.systemui.overlay.gms
com.android.systemui.overlay.miui
com.android.systemui.overlay.pong
com.android.systemui.overlay.spacewar
com.android.systemui.res.overlay_305
com.android.systemui.res.overlay_310_311
com.android.systemui.theme.dark
com.android.telephony.imsmedia
com.android.theme.color.amethyst
com.android.theme.color.aquamarine
com.android.theme.color.black
com.android.theme.color.cinnamon
com.android.theme.color.darklake
com.android.theme.color.dorange
com.android.theme.color.dpurple
com.android.theme.color.green
com.android.theme.color.lgreen
com.android.theme.color.ocean
com.android.theme.color.orchid
com.android.theme.color.parasailing
com.android.theme.color.purple
com.android.theme.color.saffron
com.android.theme.color.sand
com.android.theme.color.slate
com.android.theme.color.space
com.android.theme.color.tangerine
com.android.theme.font.ArchivoSemiBold
com.android.theme.font.Exo2Regular
com.android.theme.font.RobotoSlabRegular
com.android.theme.font.RookeryRegular
com.android.theme.icon.round
com.android.theme.icon.roundedrect
com.android.theme.icon.squircle
com.android.theme.icon.teardrop
com.android.theme.icon_pack.circular.android
com.android.theme.icon_pack.circular.launcher
com.android.theme.icon_pack.circular.settings
com.android.theme.icon_pack.circular.systemui
com.android.theme.icon_pack.circular.themepicker
com.android.theme.icon_pack.filled.android
com.android.theme.icon_pack.filled.launcher
com.android.theme.icon_pack.filled.settings
com.android.theme.icon_pack.filled.systemui
com.android.theme.icon_pack.filled.themepicker
com.android.theme.icon_pack.kai.android
com.android.theme.icon_pack.kai.launcher
com.android.theme.icon_pack.kai.settings
com.android.theme.icon_pack.kai.systemui
com.android.theme.icon_pack.kai.themepicker
com.android.theme.icon_pack.rounded.android
com.android.theme.icon_pack.rounded.launcher
com.android.theme.icon_pack.rounded.settings
com.android.theme.icon_pack.sam.android
com.android.theme.icon_pack.sam.launcher
com.android.theme.icon_pack.sam.settings
com.android.theme.icon_pack.sam.systemui
com.android.theme.icon_pack.sam.themepicker
com.android.theme.icon_pack.victor.android
com.android.theme.icon_pack.victor.launcher
com.android.theme.icon_pack.victor.settings
com.android.theme.icon_pack.victor.systemui
com.android.theme.icon_pack.victor.themepicker
com.android.thememanager
com.android.thememanager.customizethemeconfig.config.overlay
com.android.thememanager.gliobal_config.config.overlay
com.android.thememanager.module
com.android.timezone.updater
com.android.traceur.auto_generated_rro_product__
com.android.traceur.auto_generated_rro_vendor__
com.android.traceur.overlay.pixel
com.android.tv.frameworkpackagestubs
com.android.tv.settings
com.android.unisoc.telephony.server
com.android.updater
com.android.uwb.resources
com.android.uwb.resources.overlay.common
com.android.vending
com.android.vendors.bridge.softsim
com.android.virtualmachine.res
com.android.vivo.tws.vivotws
com.android.voicemailomtp
com.android.vpndialogs
com.android.wallpaper
com.android.watermark
com.android.webview
com.android.wifi.dialog
com.android.wifi.mainline.resources.overlay
com.android.wifi.resources
com.android.wifi.resources.overlay
com.android.wifi.resources.overlay.WifiRes6GhzEnable
com.android.wifi.resources.overlay.WifiResDualStaApEnable
com.android.wifi.resources.overlay.WifiResSoftap80211axEnable
com.android.wifi.resources.overlay.common
com.android.wifi.resources.overlay.kalama
com.android.wifi.resources.overlay.motCommon
com.android.wifi.resources.overlay.oplus
com.android.wifi.resources.overlay.pineapple
com.android.wifi.resources.overlay.target
com.android.wifi.resources.overlay.target.sm8635
com.android.wifi.resources.overlay.taro
com.android.wifi.resources.xiaomi
com.android.wifi.system.mainline.resources.overlay
com.android.wifi.system.resources.overlay
com.android.wm.shell
com.android.writeimei
com.android.yadayada
com.android.ztescreenshot
com.dsi.ant.service.socket
com.google.RilConfigService
com.google.SSRestartDetector
com.google.ambient.streaming
com.google.android.accessibility.switchaccess
com.google.android.aicore
com.google.android.apps.accessibility.voiceaccess
com.google.android.apps.betterbug
com.google.android.apps.camera.services
com.google.android.apps.carrier.carrierwifi
com.google.android.apps.carrier.log
com.google.android.apps.cbrsnetworkmonitor
com.google.android.apps.customization.pixel
com.google.android.apps.diagnosticstool
com.google.android.apps.diagnostictool
com.google.android.apps.dreamliner
com.google.android.apps.healthdata
com.google.android.apps.helprtc
com.google.android.apps.internal.betterbug
com.google.android.apps.mediashell
com.google.android.apps.nexuslauncher
com.google.android.apps.pixel.creativeassistant
com.google.android.apps.pixel.support
com.google.android.apps.pixelmigrate
com.google.android.apps.retaildemo.preload
com.google.android.apps.tips
com.google.android.apps.turbo
com.google.android.apps.tv.dreamx
com.google.android.apps.tv.launcherx
com.google.android.apps.tv.netoscope
com.google.android.apps.wallpaper.pixel
com.google.android.apps.wearable.retailattractloop
com.google.android.apps.wearable.settings
com.google.android.apps.wearables.maestro.companion
com.google.android.apps.work.clouddpc
com.google.android.apps.youtube.music.setupwizard
com.google.android.appsearch.apk
com.google.android.avatarpicker
com.google.android.backdrop
com.google.android.bluetooth
com.google.android.captiveportallogin
com.google.android.carrier
com.google.android.carrierlocation
com.google.android.carriersetup
com.google.android.cellbroadcastreceiver
com.google.android.cellbroadcastreceiver.overlay
com.google.android.cellbroadcastreceiver.overlay.miui
com.google.android.cellbroadcastreceiver.rro_common
com.google.android.cellbroadcastservice
com.google.android.cellbroadcastservice.overlay
com.google.android.cellbroadcastservice.overlay.miui
com.google.android.cellbroadcastservice.rro_common
com.google.android.chromecast.chromecastservice
com.google.android.chromecast.setupcustomization
com.google.android.clockwork.oemsetup
com.google.android.compos.payload
com.google.android.configupdater
com.google.android.connectivity.resources
com.google.android.connectivity.resources.overlay
com.google.android.connectivity.resources.overlay.oplus
com.google.android.connectivitythermalpowermanager
com.google.android.devicelockcontroller
com.google.android.dialer.rro_common
com.google.android.documentsui
com.google.android.documentsui.icon_overlay
com.google.android.documentsui.theme.pixel
com.google.android.dreamlinerupdater
com.google.android.euicc
com.google.android.euiccoverlay
com.google.android.ext.services
com.google.android.ext.shared
com.google.android.factoryota
com.google.android.flipendo
com.google.android.flipendo.auto_generated_rro_product__
com.google.android.flipendo.auto_generated_rro_vendor__
com.google.android.gms
com.google.android.gmsintegration
com.google.android.googlequicksearchbox.nga_resources
com.google.android.grilservice
com.google.android.gsf
com.google.android.gsf.login
com.google.android.hardwareinfo
com.google.android.iwlan
com.google.android.katniss
com.google.android.microdroid.empty_payload
com.google.android.modulemetadata
com.google.android.networkstack
com.google.android.networkstack.overlay
com.google.android.networkstack.permissionconfig
com.google.android.networkstack.tethering
com.google.android.networkstack.tethering.overlay
com.google.android.networkstack.tethering.overlay2021
com.google.android.odad
com.google.android.overlay.devicelockcontroller
com.google.android.overlay.flipendo
com.google.android.overlay.gmsagoconfig
com.google.android.overlay.gmsconfig.common
com.google.android.overlay.gmsconfig.comms
com.google.android.overlay.gmsconfig.geotz
com.google.android.overlay.gmsconfig.healthconnect
com.google.android.overlay.gmsconfig.nosafetycenter
com.google.android.overlay.gmsconfig.personalsafety
com.google.android.overlay.gmsconfig.photos
com.google.android.overlay.gmsconfig.searchlauncherqs
com.google.android.overlay.gmsconfig.searchselector
com.google.android.overlay.gmsconfig.tetheringentitlement
com.google.android.overlay.gmsconfig.tier1
com.google.android.overlay.gmsgoconfig
com.google.android.overlay.gmsgsaconfig
com.google.android.overlay.googleconfig
com.google.android.overlay.googlewebview
com.google.android.overlay.healthconnect
com.google.android.overlay.managedprovisioning
com.google.android.overlay.modules.cellbroadcastreceiver
com.google.android.overlay.modules.cellbroadcastservice
com.google.android.overlay.modules.documentsui
com.google.android.overlay.modules.ext.services
com.google.android.overlay.modules.modulemetadata.forframework
com.google.android.overlay.modules.packageinstaller
com.google.android.overlay.modules.permissioncontroller.forframework
com.google.android.overlay.permissioncontroller
com.google.android.overlay.pixelconfig2018
com.google.android.overlay.pixelconfig2019
com.google.android.overlay.pixelconfig2019midyear
com.google.android.overlay.pixelconfig2021
com.google.android.overlay.pixelconfigcommon
com.google.android.overlay.settingsProvider
com.google.android.overlay.trafficlightfaceoverlay
com.google.android.overlay.udfpsoverlay
com.google.android.packageinstaller
com.google.android.packageinstaller.a_overlay
com.google.android.permissioncontroller
com.google.android.permissioncontroller.overlay.nothing
com.google.android.permissioncontroller.overlay.oplus
com.google.android.photopicker
com.google.android.pixel.avatarpicker
com.google.android.pixel.setupwizard.auto_generated_rro_product__
com.google.android.pixel.setupwizard.overlay
com.google.android.pixel.setupwizard.overlay2019
com.google.android.pixel.setupwizard.overlay2021
com.google.android.pixelnfc
com.google.android.pixelsystemservice
com.google.android.providers.media.module
com.google.android.repairmode
com.google.android.retaildemo
com.google.android.rilextension
com.google.android.rkpdapp
com.google.android.safetycenter.resources
com.google.android.sdksandbox
com.google.android.server.deviceconfig.resources
com.google.android.settings.future.biometrics.faceenroll
com.google.android.settings.overlay.pixelvpnconfig
com.google.android.setupwizard
com.google.android.soundpicker
com.google.android.sss.authbridge
com.google.android.storagemanager
com.google.android.storagemanager.auto_generated_rro_product__
com.google.android.storagemanager.auto_generated_rro_vendor__
com.google.android.systemui.gxoverlay
com.google.android.systemui.overlay.pixelbatteryhealthconfig
com.google.android.systemui.overlay.pixelvpnconfig
com.google.android.telephony
com.google.android.tetheringentitlement
com.google.android.tts
com.google.android.tungsten.setupwraith
com.google.android.tv
com.google.android.tv.axel
com.google.android.tv.bugreportsender
com.google.android.tv.dfuservice
com.google.android.tv.frameworkpackagestubs
com.google.android.tv.remote.service
com.google.android.tv.remotecontrol.logging
com.google.android.tvlauncher
com.google.android.tvrecommendations
com.google.android.uvexposurereporter
com.google.android.uwb.resources
com.google.android.virtualmachine.res
com.google.android.wallpaper.effects
com.google.android.wearable.ambient
com.google.android.wearable.assistant
com.google.android.wearable.batteryservices
com.google.android.wearable.healthservices
com.google.android.wearable.pixel.aspen
com.google.android.webview
com.google.android.wfcactivation
com.google.android.wifi.dialog
com.google.android.wifi.resources
com.google.android.wifi.resources.overlay
com.google.android.wifi.resources.overlay.pineapple
com.google.android.wifi.resources.pixel
com.google.android.wifi.resources.xiaomi
com.google.android.youtube.tv
com.google.android.youtube.tvmusic
com.google.euiccpixel
com.google.euiccpixel.overlay.gs101
com.google.euiccpixel.overlay.zuma
com.google.euiccpixel.permissions
com.google.mainline.telemetry
com.google.pixel.camera.services
com.google.pixel.digitalkey.timesync
com.google.pixel.iwlan
com.google.pixel.livewallpaper
com.qti.backupagent
com.qti.dpmserviceapp
com.qti.ltebc
com.qti.pasrservice
com.qti.phone
com.qti.powermodule
com.qti.primarycardcontroller
com.qti.qualcomm.datastatusnotification
com.qti.vzw.ims.internal.tests
com.qualcomm.fastdormancy
com.qualcomm.qcrilmsgtunnel
com.qualcomm.qti.carrierconfigure
com.qualcomm.qti.cne
com.qualcomm.qti.confdialer
com.qualcomm.qti.dynamicddsservice
com.qualcomm.qti.extsettings
com.qualcomm.qti.gpudrivers.kalama.api33
com.qualcomm.qti.gpudrivers.kona.api30
com.qualcomm.qti.gpudrivers.lahaina.api30
com.qualcomm.qti.gpudrivers.lito.api30
com.qualcomm.qti.gpudrivers.pineapple.api34
com.qualcomm.qti.gpudrivers.sm6150.api30
com.qualcomm.qti.gpudrivers.taro.api31
com.qualcomm.qti.improvetouch.service
com.qualcomm.qti.lpa
com.qualcomm.qti.networksetting
com.qualcomm.qti.notificationservice
com.qualcomm.qti.optinoverlay
com.qualcomm.qti.poweroffalarm
com.qualcomm.qti.qs
com.qualcomm.qti.qtisettings
com.qualcomm.qti.qtisystemservice
com.qualcomm.qti.roamingsettings
com.qualcomm.qti.securemsm.mdtp
com.qualcomm.qti.services.systemhelper
com.qualcomm.qti.simsettings
com.qualcomm.qti.sva
com.qualcomm.qti.telephony.vodafonepack
com.qualcomm.qti.telephonyservice
com.qualcomm.qti.tetherservice
com.qualcomm.qti.tm
com.qualcomm.qti.trustedui
com.qualcomm.qti.uceShimService
com.qualcomm.qti.uceshimservice
com.qualcomm.qti.workloadclassifier
com.qualcomm.qtil.aptxalsOverlay
com.qualcomm.qtil.btdsda
com.qualcomm.timeservice
com.qualcomm.wfd.service
com.samsung.accessibility
com.samsung.android.ConnectivityOverlay
com.samsung.android.ConnectivityUxOverlay
com.samsung.android.MtpApplication
com.samsung.android.SettingsReceiver
com.samsung.android.app.aodservice
com.samsung.android.app.clockpack
com.samsung.android.app.soundpicker
com.samsung.android.app.telephonyui
com.samsung.android.applock
com.samsung.android.biometrics.app.setting
com.samsung.android.biometrics.service
com.samsung.android.brightnessbackupservice
com.samsung.android.callassistant
com.samsung.android.camerasdkservice
com.samsung.android.cidmanager
com.samsung.android.clipboarduiservice
com.samsung.android.communicationservice
com.samsung.android.container
com.samsung.android.globalpostprocmgr
com.samsung.android.gru
com.samsung.android.honeyboard
com.samsung.android.incallui
com.samsung.android.kgclient
com.samsung.android.knox.app.networkfilter
com.samsung.android.knox.kfbp
com.samsung.android.knox.knnr
com.samsung.android.knox.sandbox
com.samsung.android.localeoverlaymanager
com.samsung.android.lool
com.samsung.android.mcf.autohotspot
com.samsung.android.mtp
com.samsung.android.networkstack
com.samsung.android.networkstack.tethering.overlay
com.samsung.android.nfc
com.samsung.android.nfc.resources.default
com.samsung.android.nfc.resources.japan
com.samsung.android.packageinstaller
com.samsung.android.providers.carrier
com.samsung.android.providers.contacts
com.samsung.android.providers.media
com.samsung.android.providers.trash
com.samsung.android.secsoundpicker
com.samsung.android.service.pentastic
com.samsung.android.setting.multisound
com.samsung.android.sm.policy
com.samsung.android.sm_cn
com.samsung.android.spdclient
com.samsung.android.stextclassifier
com.samsung.android.storage.watchstoragemanager
com.samsung.android.timezone.autoupdate_N
com.samsung.android.timezone.autoupdate_O
com.samsung.android.timezone.data.P
com.samsung.android.timezone.data.updater
com.samsung.android.timezone.data_Q
com.samsung.android.timezone.data_R
com.samsung.android.timezone.updater
com.samsung.android.tzdata.update_M
com.samsung.android.watch.watchface.companionhelper
com.samsung.android.wcmurlsnetworkstack
com.samsung.android.wear.blockednumber
com.samsung.android.wear.contacts.sync
com.samsung.android.wearable.samsungaccount
com.samsung.android.wifi.decrease.scan.interval.resources
com.samsung.android.wifi.h2e.resources
com.samsung.android.wifi.increase.scan.interval.resources
com.samsung.android.wifi.largetcpbuffer.resources
com.samsung.android.wifi.resources
com.samsung.android.wifi.resources.qc
com.samsung.android.wifi.resources.wifilock
com.samsung.android.wifi.resources.wpa2
com.samsung.android.wifi.softap.resources
com.samsung.android.wifi.softapwpathree.resources
com.samsung.android.wifi.wapi.resources
com.samsung.drmboost
com.samsung.euicc
com.samsung.gamedriver.ex2100
com.samsung.gamedriver.sm8250
com.samsung.gamedriver.sm8550
com.samsung.huxplatform
com.samsung.internal.systemui.navbar.gestural_no_hint
com.samsung.internal.systemui.navbar.gestural_no_hint_extra_wide_back
com.samsung.internal.systemui.navbar.gestural_no_hint_narrow_back
com.samsung.internal.systemui.navbar.gestural_no_hint_wide_back
com.samsung.internal.systemui.navbar.sec_gestural
com.samsung.internal.systemui.navbar.sec_gestural_no_hint
com.samsung.knox.keychain
com.samsung.networkui
com.samsung.packageinstalleroverlay
com.samsung.phone.overlay.common
com.samsung.pregpudriver.ex2100
com.samsung.sdm
com.samsung.sdm.sdmviewer
com.samsung.slsi.telephony.oem.oemrilhookservice
com.samsung.slsi.telephony.oemril
com.samsung.ssu
com.samsung.ucs.agent.boot
com.samsung.vklayer.sm8250
com.samsung.vklayer.sm8350
com.sec.android.Cdfs
com.sec.android.app.camerasaver
com.sec.android.app.clockpackage
com.sec.android.app.launcher
com.sec.android.app.myfiles
com.sec.android.app.popupcalculator
com.sec.android.app.simsettingmgr
com.sec.android.app.tfstatus
com.sec.android.app.tfunlock
com.sec.android.inputmethod
com.sec.android.soagent
com.sec.android.systemupdate
com.sec.android.voltesettings
com.sec.app.TransmitPowerService
com.sec.epdg
com.sec.ims
com.sec.imsservice
com.sec.mhs.smarttethering
com.sec.omadm
com.sec.omadmspr
com.sec.omadmspr.syncmlphoneif
com.sec.unifiedwfc
com.wssyncmldm
vendor.qti.data.txpwradmin
vendor.qti.hardware.cacert.server
vendor.qti.imsdatachannel
vendor.qti.imsrcs
vendor.qti.iwlan
__END__
