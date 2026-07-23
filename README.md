# Charge Monitor

A fast, foreground-only Android charging dashboard designed for an unrooted Pixel 10. It distinguishes wired, USB, wireless, and dock power sources and shows the battery data exposed by Android in a polished, live energy-console UI.

## What it shows

- Charging state and connected power source
- Android system charging profile (standard, temperature protection, adaptive, or long-life)
- Battery-side net wattage (`voltage × instantaneous current`)
- Advertised source power ceiling when the Pixel supplies it
- Instantaneous and average current
- Voltage, temperature, health, and percentage
- Time to full, remaining charge, remaining energy, cycle count, and chemistry when the phone exposes them
- A rolling 30-second in-memory power chart
- Automatic foreground charging-session diagnostics with one-second min/average/max buckets
- Five-session history with power/temperature graphs, event markers, and likely-cause analysis
- JSON and CSV session sharing

The sampler runs every 250 ms only while the activity is `RESUMED`. It stops when the app loses
focus and never uses a service, network permission, or background process. While power is
connected, a private SQLite database keeps the latest five foreground sessions. A session that
resumes within five minutes with the same source is joined with a visible gap marker; otherwise a
new session begins. The recorder stores one bucket per second while the dashboard continues
updating every 250 ms.

Live numbers use a 160 ms interpolation, so changes settle smoothly before the next 250 ms sample without reducing measurement speed.

> Android exposes battery-side net current, not the USB-PD or wireless charger's negotiated input power. Device hardware may publish a new reading less frequently than the app polls. Unsupported readings are shown as unavailable rather than as zero.

## Build

The project targets Android 16 / API 36 and uses JDK 17.

This workspace already contains an ignored local JDK/Android/Gradle toolchain. On this Mac, build with:

```sh
JAVA_HOME="$PWD/.local-tooling/jdk/Contents/Home" \
GRADLE_USER_HOME="$PWD/.local-tooling/gradle-home" \
"$PWD/.local-tooling/gradle/bin/gradle" testDebugUnitTest assembleDebug
```

On a machine with JDK 17 and an Android SDK configured globally, use the checked-in wrapper:

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
uses Android's public thermal status, charge profile, source capability hints, battery level, and
connection events. These are evidence-based clues rather than a direct view of USB-PD, PPS, or Qi
negotiation.
