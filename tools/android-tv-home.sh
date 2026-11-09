#!/usr/bin/env bash
# android-tv-home.sh: makes Lena's wall display an Android TV's home screen (Nvidia Shield, Android TV and
# Google TV), so the remote's Home button opens her.
#
# Android TV has no setting for choosing another home screen, so this turns the TV's own launcher off, the
# way every replacement launcher for Android TV does, and tells Android to use Lena's. Nothing is
# uninstalled: --restore turns the TV's launcher back on, and a factory reset does too. You only need this
# once (and again if a system update turns the TV's launcher back on). Lena works without it, opened from
# the TV's apps; this only changes what the Home button opens. More in docs/app/Android-TV.md.
#
# Run it from a computer with adb (Android platform-tools). On the TV: Settings → Device Preferences →
# About → click Build 7 times; then Settings → Device Preferences → Developer options → Network debugging
# (Shield) or USB debugging. Then, on the computer:  adb connect <the TV's IP address>
#
#   ./android-tv-home.sh             Preview: shows the TV, its launcher and what would change. Changes nothing.
#   ./android-tv-home.sh --apply     Makes Lena the home screen.
#   ./android-tv-home.sh --restore   Gives the TV its own home screen back.
#   Add -s <serial> to pick a device when more than one is connected (see `adb devices`).
#
# Works with the bash that comes with macOS (3.2), Linux, and Git Bash or WSL on Windows.

set -u

LENA=io.github.soullink9.lena
LENA_HOME="$LENA/.DashboardActivity"

# The TV launchers this knows, one per Android TV flavour. Only the ones installed and turned on are touched.
LAUNCHERS=(
    com.google.android.tvlauncher         # Android TV's home screen (Nvidia Shield, most Android TVs)
    com.google.android.apps.tv.launcherx  # Google TV's home screen
    com.google.android.leanbacklauncher   # The older Android TV home screen
    com.amazon.tv.launcher                # Fire TV (Fire OS often turns it back on by itself)
)

mode=preview
serial=()
while [ $# -gt 0 ]; do
    case "$1" in
        --apply) mode=apply ;;
        --restore) mode=restore ;;
        -s) serial=(-s "${2:?-s needs a device serial}"); shift ;;
        -h|--help) sed -n '2,20p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
        *) echo "Unknown option: $1 (try --help)" >&2; exit 2 ;;
    esac
    shift
done

# One adb command on the TV, without the carriage returns some adb versions add.
on_tv() { adb "${serial[@]+"${serial[@]}"}" shell "$@" 2>&1 | tr -d '\r'; }

command -v adb >/dev/null || { echo "adb isn't installed: get Android platform-tools first." >&2; exit 1; }
if ! adb "${serial[@]+"${serial[@]}"}" get-state >/dev/null 2>&1; then
    echo "No TV connected. Turn on network or USB debugging on the TV, then: adb connect <its IP address>" >&2
    exit 1
fi

model="$(on_tv getprop ro.product.model)"
android="$(on_tv getprop ro.build.version.release)"
echo "TV: $model, Android $android"

if ! on_tv getprop ro.build.characteristics | grep -q tv && ! on_tv pm list features | grep -q android.software.leanback; then
    echo "This isn't an Android TV. For a tablet, choose Lena as the home app in Android's settings instead." >&2
    exit 1
fi
if ! on_tv pm path "$LENA" | grep -q package:; then
    echo "Lena isn't installed on this TV yet: install her first (docs/app/Android-TV.md)." >&2
    exit 1
fi

# The launchers installed here, and which of them are turned on.
installed=()
enabled=()
for pkg in "${LAUNCHERS[@]}"; do
    if on_tv pm list packages "$pkg" | grep -qx "package:$pkg"; then
        installed+=("$pkg")
        on_tv pm list packages -d "$pkg" | grep -qx "package:$pkg" || enabled+=("$pkg")
    fi
done
echo "The TV's launcher: ${installed[*]:-none found} (turned on: ${enabled[*]:-none})"
echo "What the Home button opens now: $(on_tv cmd package resolve-activity --brief -c android.intent.category.HOME -a android.intent.action.MAIN | tail -1)"

case "$mode" in
    preview)
        echo
        echo "--apply would turn off: ${enabled[*]:-nothing (already off)}, and make the Home button open $LENA_HOME."
        echo "--restore turns ${installed[*]:-the TV launcher} back on."
        ;;
    apply)
        for pkg in "${enabled[@]+"${enabled[@]}"}"; do
            echo "Turning off $pkg: $(on_tv pm disable-user --user 0 "$pkg")"
        done
        # Android 7 and later: the home app, as choosing it in a phone's settings would.
        on_tv cmd package set-home-activity "$LENA_HOME" >/dev/null
        echo "The Home button now opens: $(on_tv cmd package resolve-activity --brief -c android.intent.category.HOME -a android.intent.action.MAIN | tail -1)"
        echo "Press Home on the remote to try it. To undo: $0 --restore"
        ;;
    restore)
        for pkg in "${installed[@]+"${installed[@]}"}"; do
            echo "Turning on $pkg: $(on_tv pm enable --user 0 "$pkg")"
        done
        echo "The TV's own home screen is back. (Lena stays in the TV's apps.)"
        ;;
esac
