# Build StopTime APK on GitHub

1. Create a GitHub repository.
2. Upload the contents of this folder (including `.github/workflows/build-apk.yml`).
3. Open **Actions** → **StopTime - Build APK** → **Run workflow**.
4. When the job finishes, open the run and download the **StopTime-debug-apk** artifact.
5. Extract the artifact ZIP to get `StopTime-debug.apk` and install it on Android.

The debug APK is unsigned for Play Store distribution, but it is suitable for testing on an Android device.

The app requests the Bluetooth permissions required by Android 12+ at runtime.
