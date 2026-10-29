#!/usr/bin/env bash
set -x
mkdir -p shots
adb install -r app/build/outputs/apk/sideload/app-sideload.apk
adb shell settings put system screen_off_timeout 600000
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard || true
shot() {
  local name=$1; shift
  adb shell am force-stop com.bidule.cascade
  adb shell am start -W -n com.bidule.cascade/.MainActivity "$@"
  sleep 7
  adb exec-out screencap -p > "shots/$name.png"
}
shot paper-light --ez cascade_demo true --es cascade_style Paper --es cascade_theme Light
shot picker --es cascade_style Water --es cascade_theme Dark --ez cascade_picker true
shot picker-ink --es cascade_style Ink --es cascade_theme Light --ez cascade_picker true
ls -la shots
