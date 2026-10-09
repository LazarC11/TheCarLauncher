# TheCarLauncher

A home-screen launcher for Android head units, built for an **Opel Corsa C** double-DIN
multimedia unit. Since v2.0 its interface recreates the look of Volkswagen's MIB 2.5
(Discover Media) infotainment: MENU tiles with blue underlines, soft-key bars, bordered
source popups and light settings bars. Text is in Serbian (Latin) by default; English can
be picked under *Podešavanja → Jezik/Language*.

> The previous dashboard-style UI (v1.1.0) is preserved as release
> [build-3](../../releases/tag/build-3) (commit `b14af91`).

## Screens

| Screen | What it does |
| --- | --- |
| **MENU** | 4×2 tiles: Radio, Mediji, Aplikacije, App-Connect, Telefon, Vozilo, Navigacija, Podešavanja. Swipe for a second page of pinned apps. Android's Home button (or the compass icon in the status bar) always returns here. |
| **Mediji** | Soft keys *Izvor · Izbor · Prethodni · Reproduk./Pauza · Naredni · Setup*. *Izvor* opens the source popup (USB / SD, BT-audio, Spotify and other players, App-Connect, Radio); *Izbor* browses songs, albums, artists and folders. |
| **Radio** | Built-in FM radio: big frequency with the *<* *>* seek boxes, three pages of six presets with RDS station names (tap to tune, long-press to store, an empty slot says *Prazno*), soft keys *Lista stan. · Ručno · Setup*. *Lista stan.* scans the band into a station list; *Ručno* shows the factory FM dial for drag-tuning, and the arrows then step 0.1 MHz. |
| **Navigacija** | Beige day map with the red arrow-and-ring position marker, square map buttons, and soft keys *Novo odr. · Moja odred. · Pogled · Mediji · Aplikacija · Setup*. Routes are handed to Google Maps, Waze or another navigation app. |
| **Vozilo (CAR)** | Paged with the *<* *>* boxes like the factory menu. *Status vozila*: the Opel Corsa C picture plus reminders (oil change, annual service, registration expiry, winter tyres 1 Nov – 1 Apr, low fuel) or *Ne postoje unosi.*. *Od starta* / *Dugoročno*: GPS driving time, distance and average speed. *Od dopune* and *Gorivo*: a fuel log you fill in at the pump, which gives average consumption, range and cost per 100 km. *Servis*: odometer, oil-change km and date, registration date, tyres, tank size. No diagnostic adapter needed. |
| **Aplikacije** | All installed apps as tiles. Long-press to add to MENU or use as the phone/radio app. |
| **Podešavanja** | *Podešavanje sistema* list: screen, time, language, units, navigation, media, Bluetooth audio, App-Connect, phone and radio, vehicle, Android settings and system information. |

**Sources:** the media player switches between **USB / SD** (built-in player), **BT-audio**
(music streamed from your phone through the head unit's own Bluetooth app) and any other media app.
Picking a source pauses the previous one and starts the new one, like the factory unit.

Steering-wheel media keys work with the built-in player because it uses a standard Android
`MediaSession`. While the radio is the source, next/previous seek to the next station.

**Radio:** the launcher drives the FM tuner of QuickFish (QF) units such as the **Roco K706
(UIS7862S)** directly through the MCU service (`mcu_service`), using the protocol documented by
[OpenRadioFM](https://github.com/kapi21/OpenRadioFM) (Apache-2.0, see `NOTICE`). On a unit without
that service, the Radio tile opens the factory radio app instead (*Podešavanja → Radio* shows
the tuner status and lets you pick that app). The radio has not been tested on real hardware yet.

## Install on the head unit

1. Download `TheCarLauncher.apk` from the latest release on the
   [Releases page](../../releases), or from the *Build APK* workflow artifacts.
2. Copy it to a USB stick, or download it on the unit, and open it. Allow
   *Install unknown apps* if Android asks.
3. Press the unit's Home button and pick **Car Launcher** → *Always*.
   (Or *Podešavanja → Android sistem → Podrazumevani pokretač*.)
4. On first start, allow **Location** (map and speed) and **Music** (local files).
   To control Spotify and other apps, open *Mediji → Izvor → Poveži aplikacije* and enable
   *Car Launcher*.

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

## Fonts and artwork

Icons and tile artwork in `app/src/main/res/drawable-nodpi/vw_*.png` are original MIB 2
system graphics supplied by the owner of this head unit. They are Volkswagen's copyrighted
material, used here only for a personal, non-commercial launcher. **Keep this repository and
its releases private** and don't redistribute the APK.

Text uses **Source Sans 3** (SIL Open Font License, `app/src/main/assets/fonts/OFL.txt`) as a
stand-in for VW's own typeface.

## Map data

Map tiles come from © OpenStreetMap contributors, styled by © CARTO (dark basemap) and cached
on the unit for 30 days. The map style can be switched to dimmed OpenStreetMap or turned off
in *Settings → Navigation*.
