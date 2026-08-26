# Android SET 3.0

This branch is the focused Android SET 3.0 workspace. It does not contain the production server, GPS receiver, or Android 2.1 periodic-email pilot.

## Document authority

1. `SYSTEM_REQUIREMENT_SPECIFICATION.md`
2. `ANDROID_TECHNICAL_BUILD_SPEC`
3. `docs/superpowers/specs/2026-08-25-android-set-3.0-design.md`
4. `docs/superpowers/plans/2026-08-25-android-set-3.0-roadmap.md`

The SRS defines approved business behavior. Android Technical pins implementation detail. The design records approved rationale, and the roadmap records delivery order and current completion.

## Current status

Phase 1 platform foundation is implemented. The app currently launches a minimal Compose shell; later business functionality remains governed by the roadmap.

Implemented foundation includes:

- Android 3.0 release identity and API baseline;
- business time and UUID sources;
- stable hashed Device ID with encrypted cache;
- Android Keystore-wrapped database passphrase;
- Room and SQLCipher integration probe;
- platform dependency holder and launchable shell.

## Build on Windows

Requirements: JDK 17, Android SDK Platform 36, and Build Tools 36.0.0.

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug
```

Release signing remains managed by the checked-in scripts. Signing properties and keystores are local-only and must never be committed.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\prepare-release-signing.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\build-release-apk.ps1
```

## Branch boundaries

- `main`: historical source/archive, including server and GPS receiver.
- `feature/android-set-3.0-design`: Android SET 3.0 only.
- `feature/periodic-email-reports`: Android 2.1 periodic-email pilot only.

Do not copy complete legacy modules between branches. Reuse must be isolated, reviewed against the current specifications, and covered by tests.
