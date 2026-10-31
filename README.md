# StealthCalc — calculator + RF signal monitor

A working Android calculator that hides a WiFi / Bluetooth / cellular signal monitor
behind a secret unlock code.

## What it does

- **Calculator**: full basic calculator (`+ − × ÷ %`, sign, decimal, clear, backspace),
  same feel as a stock phone calculator.
- **Hidden unlock**: type the code (default **21042**) and the calculator switches to the
  **Signal Monitor** screen. The code is just digits typed into the calculator.
- **Scanning** (logged with timestamps):
  - **WiFi** access points — SSID, BSSID, channel, signal (dBm), security.
  - **Bluetooth** — classic discovery + BLE advertisements (name, MAC, RSSI).
  - **Cellular** — visible towers via the radio (type, MCC/MNC, cell ID, area code, dBm).
  - **Rogue-cell heuristics** — flags (in red, tagged `ALERT`) possible fake base
    stations: operator mismatch vs. your SIM, a 2G/GSM serving cell (downgrade),
    incomplete serving-cell identity, and a never-seen serving cell with abnormally
    strong signal. These are *indicators, not proof*.
  - **GNSS satellites** — every GPS/GLONASS/Galileo/BeiDou/QZSS/SBAS/IRNSS satellite in
    view, C/N0 signal strength, and how many are used in the fix (10s summary).
  - **Location trail** — timestamped lat/lon, altitude, speed, accuracy (~10s), so scans
    can be mapped to where the phone was.
  - **Magnetometer** — magnetic field in µT; deviations from Earth's ~25–65 µT (nearby
    metal/magnets/electronics) are flagged as `ALERT`.
  - **Barometer / Light / Proximity** — environment sensor readings (hPa / lux / cm).
  - **Motion** — accelerometer magnitude + stationary/moving state.
  - **Microphone level meter** — ambient loudness in dBFS only (no audio is recorded or
    stored). Needs the microphone permission.
  - **Tracker/AirTag detection** — flags Apple Find My, Tile and Samsung SmartTag-style
    BLE advertisements as `ALERT`.
  - **Follower/tail detection** — flags a Bluetooth device that keeps reappearing over a
    long span during a session.
  - **Home/away geofence** — set a home point; crossing ~150 m in/out is flagged.

- **Control panel** (Settings): on/off switch for every scanner and feature above, plus
  scan interval, battery-guard threshold, "set home", and the codes. Everything persists.
- **Quality-of-life**: in-app **log viewer** (view/share/**export GPX**/delete), **signal
  graph** of recent dBm, in-app **Map** (OpenStreetMap via osmdroid — draws the location
  trail, no API key), **dedup** of repeated lines, **session summary** written on stop,
  **battery guard** that auto-pauses scanning below a set %, and **auto-start after reboot**.
- **Decoy & panic codes**: a decoy code opens an empty "nothing here" screen; a panic code
  stops scanning and wipes all logs, then behaves like a normal calculator.
- **Background + re-lock**: scanning runs in a foreground service, so it keeps going with
  the screen off. When the screen turns off the monitor closes back to the calculator —
  you must re-enter the code to see it again.
- **Background toggle** (`BG: On` / `BG: Off` button in the monitor): when **On**, scanning
  continues after the screen turns off. When **Off**, scanning stops as soon as the screen
  turns off. The setting persists.
- **Settings** (inside the monitor): change the unlock code and the log file name. Both
  persist and are reused until changed again.

## Log location

```
Android/data/com.hotscan.calc/files/logs/<your log name>
```
Pull it with a file manager, or over USB / `adb pull`.

## Build

Needs Android Studio (Giraffe or newer) with Android SDK 34.

1. Open this folder in Android Studio (**File ▸ Open**).
2. Let it sync Gradle (it will download the Gradle 8.2 wrapper and dependencies, and
   generate the wrapper jar automatically). If you prefer the command line and have
   Gradle installed, run `gradle wrapper` once first, then `./gradlew assembleDebug`.
3. Plug in a phone with USB debugging on, or use an emulator, and press **Run**.
4. First run inside the monitor will prompt for Location + Bluetooth (and Notifications
   on Android 13+) permissions — these are required and Android always shows them.

## Honest limitations (Android, not the code)

- **Permission prompts are visible.** Android forces the location/Bluetooth prompts the
  first time it scans — this can't be hidden by any app.
- **Foreground-service notification is visible.** Continuous background scanning legally
  requires it on Android 12+. It's styled minimally ("Calculator / Running").
- **WiFi scans are throttled** to a few per ~2 minutes for foreground apps; the app also
  reads the system's cached results each cycle to fill the gaps.
- **Location must be ON** for WiFi and cell results to be returned at all.
- **Rogue-cell detection is heuristic.** Real baseband/encryption-downgrade detection
  needs root + a chipset with diagnostic access (e.g. SnoopSnitch on Qualcomm). This app
  works with the public `TelephonyManager` data every app can read.
- **NFC is foreground-only.** Android only delivers NFC taps to an app that's open on
  screen, so NFC logging works while the monitor is in front, not in the background.
- **Auto-start after reboot is best-effort.** Android 12+ restricts starting a location
  foreground service from the background, so after a reboot you may need to open the app
  once for scanning to resume.
- **Getting data off the phone is manual, by design.** Use the log viewer's Share/Export
  to send a log or GPX wherever you want. The app does not auto-upload anything to a
  server — that keeps it a local tool rather than something that quietly exfiltrates.
- **The Map needs internet for tiles.** osmdroid downloads OpenStreetMap tiles the first
  time you view an area (then caches them). The trail itself is drawn from your local log,
  not uploaded — only map tiles are fetched, the same as any map app.

## Use responsibly

Scan networks and devices you own or are authorized to survey. Logging nearby RF beacons
is legal in most places, but how you use the data is on you.
