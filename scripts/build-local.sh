#!/usr/bin/env bash
# Builds a signed APK without Gradle / the Google SDK manager, using:
#   - aapt2, dx, zipalign, apksigner from Debian/Ubuntu packages
#     (apt install aapt dalvik-exchange zipalign apksigner)
#   - an API 34 android.jar for compiling (e.g. Robolectric's android-all, from Maven Central)
#   - the matching framework resources (AndroidManifest.xml + resources.arsc zipped up)
#
# Usage: ANDROID_JAR=... FRAMEWORK_RES=... scripts/build-local.sh
# Output: build/TheCarLauncher.apk
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$ROOT/app/src/main"
OUT="$ROOT/build/local"
BT="${BUILD_TOOLS:-/usr/lib/android-sdk/build-tools/debian}"
ANDROID_JAR="${ANDROID_JAR:?set ANDROID_JAR to an API 34 android.jar / android-all jar}"
FRAMEWORK_RES="${FRAMEWORK_RES:?set FRAMEWORK_RES to a zip with the framework resources.arsc}"
PKG="com.thecarlauncher"
VERSION_CODE="${VERSION_CODE:-$(grep -m1 versionCode "$ROOT/app/build.gradle" | grep -o '[0-9]\+')}"
VERSION_NAME="${VERSION_NAME:-$(grep -m1 versionName "$ROOT/app/build.gradle" | grep -o "'[^']*'" | tr -d "'")}"

rm -rf "$OUT"
mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/compiled"

echo "> Compiling resources"
"$BT/aapt2" compile --dir "$SRC/res" -o "$OUT/compiled/res.zip"
sed "s|<manifest |<manifest package=\"$PKG\" |" "$SRC/AndroidManifest.xml" > "$OUT/AndroidManifest.xml"
"$BT/aapt2" link -o "$OUT/app.unsigned.apk" -I "$FRAMEWORK_RES" \
    --manifest "$OUT/AndroidManifest.xml" --java "$OUT/gen" -A "$SRC/assets" \
    --min-sdk-version 21 --target-sdk-version 34 \
    --version-code "$VERSION_CODE" --version-name "$VERSION_NAME" \
    "$OUT/compiled/res.zip"

echo "> Compiling Java"
find "$SRC/java" "$OUT/gen" -name '*.java' > "$OUT/sources.txt"
LIBS=$(ls "$ROOT"/app/libs/*.jar 2>/dev/null | tr '\n' ':')
javac -nowarn -Xlint:none --release 8 -encoding UTF-8 \
    -classpath "$ANDROID_JAR:$LIBS" -d "$OUT/classes" @"$OUT/sources.txt" 2>&1 \
    | grep -v -e '^warning: \[options\]' -e '^Picked up JAVA_TOOL_OPTIONS' -e '^[0-9]* warnings\?$' || true
test -f "$OUT/classes/com/thecarlauncher/LauncherActivity.class"

echo "> Dexing"
"$BT/dx" --dex --min-sdk-version=21 --output="$OUT/classes.dex" "$OUT/classes" "$ROOT"/app/libs/*.jar 2>&1 | grep -v JAVA_TOOL || true
(cd "$OUT" && zip -q app.unsigned.apk classes.dex)

echo "> Aligning and signing"
"$BT/zipalign" -f -p 4 "$OUT/app.unsigned.apk" "$OUT/app.aligned.apk"
"$BT/apksigner" sign --ks "$ROOT/keystore/release.jks" --ks-pass pass:carlauncher \
    --ks-key-alias carlauncher --key-pass pass:carlauncher \
    --out "$ROOT/build/TheCarLauncher.apk" "$OUT/app.aligned.apk" 2>&1 | grep -v JAVA_TOOL || true
"$BT/apksigner" verify "$ROOT/build/TheCarLauncher.apk" 2>&1 | grep -v JAVA_TOOL || true
echo "> Done: build/TheCarLauncher.apk ($(du -h "$ROOT/build/TheCarLauncher.apk" | cut -f1))"
