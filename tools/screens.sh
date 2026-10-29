#!/usr/bin/env bash
# Installs Cascade on the running emulator and screenshots each style × theme.
set -x
mkdir -p shots
adb install -r app/build/outputs/apk/release/app-release.apk
adb shell settings put system screen_off_timeout 600000
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard || true
shot() {
  local name=$1; shift
  adb shell am force-stop com.bidule.cascade
  adb shell am start -W -n com.bidule.cascade/.MainActivity "$@"
  sleep 6
  adb exec-out screencap -p > "shots/$name.png"
}
shot water-wallpaper --ez cascade_demo true --es cascade_style Water --es cascade_theme Wallpaper
shot water-dark --es cascade_style Water --es cascade_theme Dark
shot water-light --es cascade_style Water --es cascade_theme Light
shot ink-light --es cascade_style Ink --es cascade_theme Light
shot ink-dark --es cascade_style Ink --es cascade_theme Dark
shot ink-wallpaper --es cascade_style Ink --es cascade_theme Wallpaper
# Ink: open a thumb-index page (right edge, middle)
W=$(adb shell wm size | grep -o '[0-9]*x[0-9]*' | cut -dx -f1); H=$(adb shell wm size | grep -o '[0-9]*x[0-9]*' | cut -dx -f2)
adb shell input tap $((W - 30)) $((H / 2))
sleep 2
adb exec-out screencap -p > shots/ink-index-page.png
# Water: pull up for all apps
shot water-light-2 --es cascade_style Water --es cascade_theme Light
adb shell input swipe $((W / 2)) $((H * 85 / 100)) $((W / 2)) $((H * 40 / 100)) 400
sleep 2
adb exec-out screencap -p > shots/water-all-apps.png
# Classic for comparison
shot classic-dark --es cascade_style Classic --es cascade_theme Dark
ls -la shots
