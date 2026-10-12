# Charge Monitor

A fast Android charging dashboard and diagnostic recorder designed for an unrooted Pixel 10. It distinguishes wired, USB, wireless, and dock power sources and presents the battery data exposed by Android in a focused Material 3 interface that follows the
system light or dark theme.

## Layout

- **Live** — one battery ring with net watts at its centre, a 30-second power chart, current /
  voltage / temperature, and a single card for the connected charger, the session being recorded,
  and screen-off recording. Secondary battery facts sit in a collapsed "Battery details" card. A
  warning banner appears only when Android reports throttling or forecasts it within 30 seconds.
- **Sessions** — the last five charging sessions with a one-line verdict each, plus Compare (pick
  two) and the guided A/B charger test. Tapping a session opens its report: likely cause, key stats,
  a power + temperature trace with labelled axes and event markers, details, events, and CSV/JSON
  sharing.

## What it shows

- Charging state and connected power source
- Android system charging profile (standard, temperature protection, adaptive, or long-life)
- Battery-side net wattage (`voltage × instantaneous current`)
- Advertised source power ceiling when the Pixel supplies it
- Instantaneous and average current
- Voltage, temperature, health, and percentage
- Current thermal status and Android's 30-second thermal-headroom forecast
- Time to full, remaining charge, remaining energy, cycle count, and chemistry when the phone exposes them
- A rolling 30-second in-memory power chart
- Automatic foreground charging-session diagnostics with one-second min/average/max buckets
- Five-session history with power/temperature graphs, event markers, and likely-cause analysis
- Named charger/pad profiles with baselines built from comparable cool, lower-charge samples
- Side-by-side normalized session comparison with a test-confidence score
- A guided A/B workflow for testing two chargers, cables, or pads under similar conditions; each
  window closes automatically at five minutes and the screen stays on while it runs
- Optional user-started recording while the display is off
- JSON and CSV session sharing

The live sampler runs every 250 ms only while the activity is `RESUMED`. By default it stops when
the app loses focus. While power is connected, a private SQLite database keeps the latest five
sessions. A foreground session that resumes within five minutes with the same source is joined
with a visible gap marker; otherwise a new session begins. The recorder stores one bucket per
second while the dashboard continues updating every 250 ms.

Screen-off recording is strictly opt-in. Starting it from the dashboard runs a foreground service
at 1 Hz, shows a persistent notification, and holds a partial wake lock so the trace remains
continuous. It stops when the user taps Stop or when power disconnects. The live activity and
service share one recorder, so opening the app during an extended trace does not duplicate
samples.

Live numbers use a 160 ms interpolation, so changes settle smoothly before the next 250 ms sample
without reducing measurement speed.

> Android exposes battery-side net current, not the USB-PD or wireless charger's negotiated input power. Device hardware may publish a new reading less frequently than the app polls. Unsupported readings are shown as unavailable rather than as zero.

Charger/pad identity is a best-effort local fingerprint based on connection type and optional
source-capability values from the battery broadcast. Android does not expose USB or Qi serial
identifiers to this app, so two accessories reporting the same capability may share a profile.

## Build

The project targets Android 16 / API 36 and uses JDK 17.

This workspace already contains an ignored local JDK/Android/Gradle toolchain. On this Mac, build with:

```sh
JAVA_HOME="$PWD/.local-tooling/jdk/Contents/Home" \
GRADLE_USER_HOME="$PWD/.local-tooling/gradle-home" \
"$PWD/.local-tooling/gradle/bin/gradle" testDebugUnitTest assembleDebug
```

On a machine with JDK 17 and an Android SDK configured globally (or `sdk.dir` set in an ignored
`local.properties`), use the checked-in wrapper:

```sh
./gradlew testDebugUnitTest assembleDebug
```

The debug APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Install it on a USB-connected phone with:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

No release signing configuration is required. Gradle's standard debug keystore signs the debug APK automatically.

## Measurement behavior

The app combines the sticky `ACTION_BATTERY_CHANGED` state with `BatteryManager` properties:

- `BATTERY_PROPERTY_CURRENT_NOW`
- `BATTERY_PROPERTY_CURRENT_AVERAGE`
- `BATTERY_PROPERTY_CHARGE_COUNTER`
- `BATTERY_PROPERTY_ENERGY_COUNTER`
- `computeChargeTimeRemaining()`

All unit conversions and derived power calculations preserve Android's signed current convention:
positive is energy entering the battery and negative is energy leaving it. Session diagnosis also
uses Android's public thermal status and headroom forecast, charge profile, source capability
hints, battery level, and connection events. These are evidence-based clues rather than a direct
view of USB-PD, PPS, or Qi negotiation.
