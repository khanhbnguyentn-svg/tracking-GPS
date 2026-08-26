# Android 2.1 Periodic Email Pilot

This branch contains only the Android periodic-email tracking pilot. It does not contain the server, GPS receiver, or Android SET 3.0 design.

## Release scope

- Application ID: `com.internal.tracker`
- Version: `2.1.0` (`versionCode 6`)
- Android baseline: API 29-36
- GPS tracking: foreground service with local Room history
- Delivery: periodic CSV reports through Gmail SMTP
- Recovery: boot/update reconciliation, tracking integrity diagnostics, and durable scheduled work

This branch is maintained independently from `feature/android-set-3.0-design`. Its source and documents describe the 2.1 pilot only and are not authoritative for SET 3.0.

## Operational documents

- `docs/email-branch-technical-reference.md`: current technical reference and source map.
- `docs/periodic-gmail-pilot-handover.md`: build, configuration, and operating handover.
- `docs/android-14-device-test-checklist.md`: physical-device acceptance checklist.
- `docs/stable-apk-update-runbook.md`: stable signing and APK update procedure.
- `docs/gmail-build-secrets.example.properties`: local Gmail build defaults template.

Feature rationale and implementation history are limited to the periodic-email documents under `docs/superpowers/specs/` and `docs/superpowers/plans/`.

## Build on Windows

Requirements: JDK 17, Android SDK Platform 36, and Build Tools 36.0.0.

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug
```

Gmail credentials are configured on each device. For optional internal build defaults, copy the example file and keep the result local:

```powershell
Copy-Item .\docs\gmail-build-secrets.example.properties .\gmail-secrets.properties
```

Never commit `gmail-secrets.properties`, Gmail App Passwords, signing properties, or keystores.

## Stable release

Prepare signing once on a build machine:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\prepare-release-signing.ps1
```

Build and verify the signed APK:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\build-release-apk.ps1
```

The distributable is `dist/tracking-gps-2.1.0.apk`. Use the stable update runbook before installing it on managed phones.
