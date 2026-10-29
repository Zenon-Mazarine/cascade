#!/usr/bin/env bash
# Renders Cascade on the emulator: store screenshots (store-*) and UI checks (check-*).
set -x
mkdir -p shots
adb install -r app/build/outputs/apk/release/app-release.apk
adb shell settings put system screen_off_timeout 600000
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard || true
# A tidy status bar for store screenshots; keep system "not responding" dialogs out of the way.
adb shell settings put global hide_error_dialogs 1 || true
adb shell pm disable-user --user 0 com.google.android.apps.nexuslauncher || true
adb shell settings put global sysui_demo_allowed 1
dm() { adb shell am broadcast -a com.android.systemui.demo -e command "$@" > /dev/null; }
dm enter
dm clock -e hhmm 1042
dm battery -e level 100 -e plugged false
dm network -e wifi show -e level 4
dm network -e mobile show -e datatype none -e level 4
dm notifications -e visible false

shot() {
  local name=$1; shift
  adb shell am force-stop com.bidule.cascade
  adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS > /dev/null || true
  adb shell am start -W -n com.bidule.cascade/.MainActivity "$@"
  sleep 8
  adb exec-out screencap -p > "shots/$name.png"
}
P="--ez cascade_pro true --ez cascade_clean true"
shot store-1-water --ez cascade_demo true $P --es cascade_style Water --es cascade_theme Dark
shot store-2-ink $P --es cascade_style Ink --es cascade_theme Light
shot store-3-neon $P --es cascade_style Neon --es cascade_theme Dark
shot store-4-stone $P --es cascade_style Stone --es cascade_theme Light
shot store-5-cosmos $P --es cascade_style Cosmos --es cascade_theme Dark
shot store-6-paper $P --es cascade_style Paper --es cascade_theme Light
shot store-7-picker $P --es cascade_style Water --es cascade_theme Dark --ez cascade_picker true
W=$(adb shell wm size | grep -o '[0-9]*x[0-9]*' | cut -dx -f1); H=$(adb shell wm size | grep -o '[0-9]*x[0-9]*' | cut -dx -f2)
shot store-8-index $P --es cascade_style Ink --es cascade_theme Dark
adb shell input tap $((W - 30)) $((H / 2))
sleep 3
adb exec-out screencap -p > shots/store-8-index.png

# Freemium checks: no Pro, paywall for a Pro style, and first-run onboarding.
shot check-paywall --ez cascade_pro false --es cascade_style Water --es cascade_theme Dark --es cascade_paywall Neon
shot check-onboarding --ez cascade_pro false --ez cascade_onboarding true --es cascade_style Water --es cascade_theme Dark
adb shell input tap $((W / 2)) $((H * 93 / 100))
sleep 2
adb exec-out screencap -p > shots/check-onboarding-2.png
ls -la shots
