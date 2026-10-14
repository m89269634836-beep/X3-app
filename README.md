# X3Remote

Initial Android UI prototype for Insta360 X3.

**This version does not connect to or control the camera yet.** The buttons are deliberately disabled. BLE communication, recording commands, battery status, volume-button shortcuts, and background service are planned for later versions.

## Build APK
Push all files (including `.github/workflows/android.yml`) to the `main` branch. Go to **Actions → Android APK → latest successful run → Artifacts → X3Remote-debug-apk**. Download and extract the artifact ZIP to obtain `app-debug.apk`.

Requires Android 8.0 or later. GitHub Actions builds with Gradle 8.9, Android Gradle Plugin 8.7.3 and JDK 17. This project has not been build-tested locally.
