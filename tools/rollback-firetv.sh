#!/usr/bin/env bash
# Put a Fire TV back on an earlier launcher build (e.g. the one running before an upgrade).
#
#   Keep this script in a folder with the saved APK and its .sha256, then:
#   TV_HOST=<stick ip> ./rollback-firetv.sh
#
# Before upgrading, save the installed build next to it:
#   adb pull $(adb shell pm path com.gathin.tvhome | cut -d: -f2) installed-before-redesign.apk
#   sha256sum installed-before-redesign.apk > installed-before-redesign.apk.sha256
# Works offline. Same package, key and versionCode, so `install -r` works in either direction.
set -euo pipefail
cd "$(dirname "$0")"
: "${TV_HOST:?set TV_HOST to the Fire TV IP address, e.g. TV_HOST=192.168.1.50 $0}"
T="$TV_HOST:5555"
SVC=com.gathin.tvhome/com.gathin.tvhome.TvAccessibilityService

# The exact APK pulled from the TV before the new install (step 7), else the one rebuilt from
# the known-good-2026-11-03 tag. Both are checked against their saved SHA-256.
APK=installed-before-redesign.apk
[ -f "$APK" ] || APK=launcher-known-good-d3d3ec9.apk
sha256sum -c "$APK.sha256" >/dev/null || { echo "Checksum mismatch for $APK, not installing."; exit 1; }

adb connect "$T" >/dev/null
adb -s "$T" install -r "$APK"
adb -s "$T" shell pm grant com.gathin.tvhome android.permission.ACCESS_FINE_LOCATION || true

# The new build pointed the screensaver at its own clock, which the old launcher doesn't have.
# Restore what the TV had before (saved in step 7); without that file, hand it back to Amazon.
if [ -f settings-before.txt ]; then
  while IFS='=' read -r key value; do
    if [ "$value" = "null" ] || [ -z "$value" ]; then
      adb -s "$T" shell settings delete secure "$key" >/dev/null || true
    else
      adb -s "$T" shell settings put secure "$key" "$value" || true
    fi
  done < settings-before.txt
else
  echo "No settings-before.txt: resetting the screensaver to Fire OS's default."
  adb -s "$T" shell settings delete secure screensaver_components >/dev/null || true
fi

# A reinstall unbinds the Home override; enable it again and wait until it is bound.
adb -s "$T" shell settings delete secure enabled_accessibility_services >/dev/null
sleep 1
adb -s "$T" shell settings put secure enabled_accessibility_services "$SVC"
adb -s "$T" shell settings put secure accessibility_enabled 1
for _ in $(seq 1 20); do
  adb -s "$T" shell dumpsys accessibility | grep -q "Enabled services:{{$SVC}}" && break
  sleep 0.5
done
sleep 1
adb -s "$T" shell am start -n com.gathin.tvhome/.MainActivity >/dev/null
echo "Rolled back to $APK. Press Home on the remote to check."
