# StopTime — Android Bluetooth Timer

A ready-to-open Android Studio project built as **HTML/CSS/JavaScript inside a WebView**, with a minimal **Java Bluetooth bridge** for real Android-to-Android Bluetooth Classic (RFCOMM).

## What is included

- Controller mode and Display mode
- Real Bluetooth Classic RFCOMM connection between two Android phones
- Pairing through the Android Bluetooth system UI
- Controller host + Display connect to a paired device
- 3-point NTP-style clock offset estimation after connection
- Start / Stop / Reset commands sent over Bluetooth
- Periodic synchronization messages to keep the Display aligned
- High-resolution browser timer rendering with `performance.now()` on the Controller
- Black LED-style UI with a large circular button
- No external JavaScript libraries or runtime server
- Android 12+ Bluetooth runtime permissions
- Optional English/Arabic UI toggle for the main controls

## Pair two phones

1. Install/build the same StopTime APK on both Android phones.
2. On both phones, turn Bluetooth on.
3. In Android system Bluetooth settings, pair the two phones.
4. On phone A open StopTime -> **Controller** -> **START HOST**.
5. On phone A, **MAKE VISIBLE** can be used if the phones are not already paired.
6. On phone B open StopTime -> **Display** -> **REFRESH**.
7. Tap **CONNECT** for phone A.
8. Wait for `Connected • clock synchronized`.
9. Press START/STOP on the Controller. The Display mirrors the timer.

## Build in Android Studio

Use Android Studio with a JDK 17 installation. The project uses Android Gradle Plugin 9.4.0 and Gradle 9.6.

Open the `StopTime` folder as an Android project and build the `app` module.

The project targets API 37 and has minSdk 23.

## Build from a Gradle-enabled environment

```bash
./gradlew assembleDebug
```

The debug APK is generated under:

`app/build/outputs/apk/debug/app-debug.apk`

## Architecture

`app/src/main/assets/index.html` contains the UI, timer display, Bluetooth protocol messages, and sync logic.

`app/src/main/java/com/stoptime/app/MainActivity.java` exposes a small JavaScript bridge and implements Bluetooth Classic RFCOMM sockets.

No internet server is required for the Controller/Display connection: the two phones communicate directly over Bluetooth.
