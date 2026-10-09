# TheCarLauncher

A home-screen launcher for Android head units, built for an **Opel Corsa C** double-DIN
multimedia unit. The look takes after Volkswagen's MIB infotainment: dark tiles, thin type,
a glowing accent line. The layout and details are its own.

## Screens

| Screen | What it does |
| --- | --- |
| **Home** | Live map tile with your position, Home/Work quick navigation, now-playing tile with album art and controls, a GPS speed gauge, and 4 favourite apps. |
| **Navigation** | Full map (pan, pinch/double-tap zoom, re-centre), destination search, saved Home/Work, heading/altitude/satellites/accuracy, and hand-off to Google Maps, Waze or any other navigation app. |
| **Media** | Built-in music player for files on the unit, SD card or USB stick (songs/albums/artists/folders browser, shuffle, repeat, equalizer). Also controls Spotify, YouTube Music, Bluetooth audio and other players once *Connect apps* is allowed. |
| **Phone** | Opens your phone/Bluetooth app (you choose which one the first time). |
| **Apps** | All installed apps with search. Long-press an app to pin it to Home, set it as the phone app, open its info, or uninstall it. |
| **Settings** | Accent colour (7 choices), background style, 24h clock, full-screen mode, vehicle name, km/h or mph, navigation app, saved places, map style, music resume, permissions and Android settings shortcuts. |

**Sources:** the media player switches between **USB / Music** (built-in player), **Bluetooth**
(music streamed from your phone through the head unit's own Bluetooth app) and any other media app.
Picking a source pauses the previous one and starts the new one, like the factory unit. The
launcher connects to the system Bluetooth media service when the unit has one (full song info).
Otherwise it drives the vendor Bluetooth app with media keys. If a CarPlay/Android Auto app such
as ZLink is installed, it gets a chip there too. *Settings → About → Diagnostics* shows which
method your unit supports.

Steering-wheel media keys work with the built-in player because it uses a standard Android
`MediaSession`. With *Resume music on start* on, the last song carries on when the car is started.

## Install on the head unit

1. Download `TheCarLauncher.apk` from the latest release on the
   [Releases page](../../releases), or from the *Build APK* workflow artifacts.
2. Copy it to a USB stick, or download it on the unit, and open it. Allow
   *Install unknown apps* if Android asks.
3. Press the unit's Home button and pick **Car Launcher** → *Always*.
   (Or go to *Settings → System → Set as default launcher*.)
4. On first start, allow **Location** (map and speed) and **Music** (local files).
   To control Spotify and other apps from the dashboard, tap **+ Connect apps** on the
   Media screen and enable *Car Launcher*.

Requires Android 5.0 or newer. Tested layouts: 800×480, 1024×600 (mdpi and hdpi) and 1280×720
in landscape.

## Building

**With Gradle** (Android SDK installed):

```sh
./gradlew assembleRelease
# -> app/build/outputs/apk/release/app-release.apk
```

GitHub Actions runs this on every push and publishes the APK as a release.

**Without Gradle or the Google SDK** (Debian/Ubuntu packages only), see `scripts/build-local.sh`:

```sh
sudo apt install aapt dalvik-exchange zipalign apksigner
ANDROID_JAR=/path/to/android-all-14.jar FRAMEWORK_RES=/path/to/framework-res-34.zip \
  scripts/build-local.sh
# -> build/TheCarLauncher.apk
```

Both builds sign with `keystore/release.jks`, so either APK installs as an update over the
other. That key is committed on purpose so builds stay update-compatible. It is fine for a
personal head unit, but don't reuse it for anything published.

## Map data

Map tiles come from © OpenStreetMap contributors, styled by © CARTO (dark basemap) and cached
on the unit for 30 days. The map style can be switched to dimmed OpenStreetMap or turned off
in *Settings → Navigation*.
