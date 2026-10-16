#!/bin/bash
# Packs the Magisk module that installs Car Launcher as a privileged system app.
#   scripts/build-magisk.sh [path/to/TheCarLauncher.apk]   -> build/TheCarLauncher-magisk.zip
set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APK="${1:-$ROOT/build/TheCarLauncher.apk}"
[ -f "$APK" ] || { echo "APK not found: $APK" >&2; exit 1; }
VNAME=$(grep -oP "versionName '\K[^']+" "$ROOT/app/build.gradle")
VCODE=$(grep -oP 'versionCode \K[0-9]+' "$ROOT/app/build.gradle")
WORK=$(mktemp -d)
cp -r "$ROOT/magisk/." "$WORK/"
mkdir -p "$WORK/system/priv-app/TheCarLauncher"
cp "$APK" "$WORK/system/priv-app/TheCarLauncher/TheCarLauncher.apk"
sed -i "s/^version=.*/version=$VNAME/; s/^versionCode=.*/versionCode=$VCODE/" "$WORK/module.prop"
mkdir -p "$ROOT/build"
OUT="$ROOT/build/TheCarLauncher-magisk.zip"
rm -f "$OUT"
(cd "$WORK" && zip -qr9 "$OUT" .)
rm -rf "$WORK"
echo "> Done: $OUT"
