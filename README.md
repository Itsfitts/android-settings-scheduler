# Android Settings Scheduler

Schedule any settings that are in the Android Settings app.

### Credits

The Shizuku and permissions part of this app is adapted from [SimpleAppProjects/SimpleWear](https://github.com/SimpleAppProjects/SimpleWear) by [thewizrd](https://github.com/thewizrd)

## Overview

With Android Settings Scheduler, uou can schedule any settings (booleans, strings, numbers) that are stored in `Settings.Secure`, `Settings.System`, and `Settings.Global`, which are usually set by the Android Settings app, to be changed.

## Installation

See the latest GitHub release to download the APK. `WRITE_SECURE_SETTINGS` is required for the app to run, which can be granted by:

- Shizuku
- ADB (`adb shell pm grant com.turtlepaw.scheduler android.permission.WRITE_SECURE_SETTINGS`)
