#!/usr/bin/env bash
# Renders real Cascade footage frame by frame (FootageTest) and turns each clip into a 30 fps mp4.
set -x
mkdir -p footage
adb shell settings put system screen_off_timeout 1800000
adb shell settings put global hide_error_dialogs 1 || true
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard || true
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -e class com.bidule.cascade.FootageTest \
  com.bidule.cascade.test/androidx.test.runner.AndroidJUnitRunner | tail -20
adb pull /sdcard/Android/data/com.bidule.cascade/files/frames footage/
for d in footage/frames/*/; do
  n=$(basename "$d")
  ffmpeg -v error -y -framerate 30 -i "$d/%04d.jpg" -c:v libx264 -preset veryfast -crf 16 -pix_fmt yuv420p "footage/$n.mp4"
  cp "$d/0000.jpg" "footage/first-$n.jpg"
done
rm -rf footage/frames
ls -la footage
