#!/usr/bin/env bash
# Records real footage of Cascade on the emulator for the ads.
set -x
mkdir -p footage
adb install -r app/build/outputs/apk/release/app-release.apk
adb shell settings put system screen_off_timeout 600000
adb shell settings put global hide_error_dialogs 1 || true
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard || true
adb shell settings put global sysui_demo_allowed 1
dm() { adb shell am broadcast -a com.android.systemui.demo -e command "$@" > /dev/null; }
dm enter
dm clock -e hhmm 0941
dm battery -e level 100 -e plugged false
dm network -e wifi show -e level 4
dm network -e mobile show -e datatype none -e level 4
dm notifications -e visible false
W=$(adb shell wm size | grep -o '[0-9]*x[0-9]*' | cut -dx -f1); H=$(adb shell wm size | grep -o '[0-9]*x[0-9]*' | cut -dx -f2)
D=$(adb shell wm density | grep -o '[0-9]*' | tail -1)
dp() { echo $(( $1 * D / 160 )); }
OX=$((W - $(dp 36))); OY=$(dp 150)   # roughly where the style orb sits
P="--ez cascade_pro true --ez cascade_clean true"

# rec <name> <seconds> <am start args...> ; then optional actions run via ACT env
rec() {
  local name=$1 secs=$2; shift 2
  adb shell am force-stop com.bidule.cascade
  adb shell am start -W -n com.bidule.cascade/.MainActivity "$@"
  sleep 4
  adb shell screenrecord --size 720x1600 --bit-rate 12000000 --time-limit "$secs" /sdcard/clip.mp4 &
  local pid=$!
  sleep 0.6
  if [ -n "$ACT" ]; then eval "$ACT"; fi
  wait $pid
  adb pull /sdcard/clip.mp4 "footage/$name.mp4"
  ACT=""
}

rec idle-water 7 --ez cascade_demo true $P --es cascade_style Water --es cascade_theme Dark
rec idle-neon 7 $P --es cascade_style Neon --es cascade_theme Dark
rec idle-cosmos 7 $P --es cascade_style Cosmos --es cascade_theme Dark
rec idle-ink 6 $P --es cascade_style Ink --es cascade_theme Light
rec idle-stone 6 $P --es cascade_style Stone --es cascade_theme Light
rec idle-paper 6 $P --es cascade_style Paper --es cascade_theme Light

# Style switches, each from the orb.
for pair in "Classic:Water" "Water:Ink" "Ink:Stone" "Stone:Paper" "Paper:Neon" "Neon:Cosmos" "Cosmos:Water"; do
  from=${pair%%:*}; to=${pair##*:}
  theme=Dark; [ "$from" = "Ink" ] || [ "$from" = "Stone" ] || [ "$from" = "Paper" ] && theme=Light
  rec "switch-$(echo $from | tr A-Z a-z)-$(echo $to | tr A-Z a-z)" 7 $P --es cascade_style $from --es cascade_theme $theme \
     --es cascade_switch_to $to --ei cascade_switch_x $OX --ei cascade_switch_y $OY
done

# Classic: sliding a thumb along the alphabet.
ACT="adb shell input swipe $((W - $(dp 18))) $((H * 22 / 100)) $((W - $(dp 18))) $((H * 78 / 100)) 2600; adb shell input swipe $((W - $(dp 18))) $((H * 70 / 100)) $((W - $(dp 18))) $((H * 40 / 100)) 1600"
rec scrub-classic 7 $P --es cascade_style Classic --es cascade_theme Dark
# Ink: thumb index.
ACT="adb shell input swipe $((W - $(dp 14))) $((H * 30 / 100)) $((W - $(dp 14))) $((H * 75 / 100)) 2600"
rec scrub-ink 6 $P --es cascade_style Ink --es cascade_theme Light
# Water: pull the water up for all apps.
ACT="sleep 1.5; adb shell input swipe $((W / 2)) $((H * 88 / 100)) $((W / 2)) $((H * 45 / 100)) 700"
rec pull-water 6 $P --es cascade_style Water --es cascade_theme Dark
# Picker.
rec picker-water 5 $P --es cascade_style Water --es cascade_theme Dark --ez cascade_picker true
# Stills for compositing.
for s in Water:Dark Ink:Light Stone:Light Paper:Light Neon:Dark Cosmos:Dark Classic:Dark; do
  st=${s%%:*}; th=${s##*:}
  adb shell am force-stop com.bidule.cascade
  adb shell am start -W -n com.bidule.cascade/.MainActivity $P --es cascade_style $st --es cascade_theme $th
  sleep 7
  adb exec-out screencap -p > "footage/still-$(echo $st | tr A-Z a-z).png"
done
adb shell wm size
ls -la footage
