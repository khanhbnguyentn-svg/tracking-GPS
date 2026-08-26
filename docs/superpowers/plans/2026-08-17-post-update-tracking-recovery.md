# Post-Update Tracking Recovery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore enabled foreground tracking automatically after an in-place APK update and when the UI opens, without resetting the report schedule, then publish version 2.0.3.

**Architecture:** Add a package-replaced trigger to the existing system receiver for full reconciliation. Add a pure app-launch policy whose only possible action is tracking recovery, and invoke it from `MainActivity`. Promote through the immutable signed-release pipeline.

**Tech Stack:** Kotlin, Android BroadcastReceiver/Activity, WorkManager, JUnit 4, Gradle, PowerShell/Pester, ADB.

## Global Constraints

- `MY_PACKAGE_REPLACED` performs tracking and schedule reconciliation.
- App launch performs tracking reconciliation only and never replaces report work.
- Disabled tracking remains disabled.
- Existing reboot/time-change recovery remains unchanged.
- Release is `2.0.3 (5)` and does not overwrite 2.0.2.

---

### Task 1: Recover Tracking on App Launch

**Files:**
- Create: `app/src/test/java/com/internal/tracker/schedule/AppLaunchReconcilePolicyTest.kt`
- Modify: `app/src/main/java/com/internal/tracker/schedule/ScheduleReceiver.kt`
- Modify: `app/src/main/java/com/internal/tracker/AppContainer.kt`
- Modify: `app/src/main/java/com/internal/tracker/MainActivity.kt`

**Interfaces:**
- Produces: `AppLaunchReconcilePolicy.actions(trackingEnabled: Boolean): Set<ReconcileAction>` and `AppContainer.reconcileAppLaunch()`.

- [ ] Write this failing test:

```kotlin
class AppLaunchReconcilePolicyTest {
    @Test fun enabledTrackingRestoresOnlyTheService() {
        assertEquals(setOf(ReconcileAction.TRACKING), AppLaunchReconcilePolicy.actions(true))
    }

    @Test fun disabledTrackingDoesNothing() {
        assertEquals(emptySet<ReconcileAction>(), AppLaunchReconcilePolicy.actions(false))
    }
}
```

- [ ] Run `.\gradlew.bat :app:testDebugUnitTest --tests com.internal.tracker.schedule.AppLaunchReconcilePolicyTest` and confirm compilation fails because `AppLaunchReconcilePolicy` is absent.
- [ ] Implement:

```kotlin
object AppLaunchReconcilePolicy {
    fun actions(trackingEnabled: Boolean): Set<ReconcileAction> =
        if (trackingEnabled) setOf(ReconcileAction.TRACKING) else emptySet()
}
```

- [ ] Add `AppContainer.reconcileAppLaunch()` that evaluates the policy and calls only `reconcileTracking()` for `ReconcileAction.TRACKING`.
- [ ] Call `(application as TrackerApplication).container.reconcileAppLaunch()` from `MainActivity.onCreate()` before `setContent`.
- [ ] Run `:app:testDebugUnitTest` filtered to `AppLaunchReconcilePolicyTest`, `ScheduleReceiverPolicyTest`, `ReportScheduleTest`, and tracking tests; expect PASS.
- [ ] Commit with `fix: restore tracking when app opens`.

### Task 2: Recover After Package Replacement

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: existing `ScheduleReceiver` and `reconcileBackgroundWork()`.

- [ ] Add this action to the existing `ScheduleReceiver` intent filter:

```xml
<action android:name="android.intent.action.MY_PACKAGE_REPLACED" />
```

- [ ] Run `:app:assembleDebug`, then run `aapt dump xmltree app/build/outputs/apk/debug/app-debug.apk AndroidManifest.xml` and confirm the merged manifest contains `android.intent.action.MY_PACKAGE_REPLACED` under `ScheduleReceiver`.
- [ ] Commit with `fix: reconcile background work after updates`.

### Task 3: Promote and Build Version 2.0.3

**Files:**
- Modify: `scripts/tests/BuildReleaseCommand.Tests.ps1`
- Modify: `app/build.gradle.kts`
- Modify: `scripts/build-release-apk.ps1`
- Modify: `README.md`
- Modify: `docs/stable-apk-update-runbook.md`
- Modify: `docs/android-14-device-test-checklist.md`
- Create: `dist/tracking-gps-2.0.3.apk` through the build script.

**Interfaces:**
- Produces: signed package `com.internal.tracker`, version `2.0.3 (5)`.

- [ ] Change `BuildReleaseCommand.Tests.ps1` to expect `tracking-gps-2.0.3.apk` and output `2.0.3 (5)`; run that Pester file and confirm RED because production still targets 2.0.2.
- [ ] Set `versionCode = 5`, `versionName = "2.0.3"`, `-ExpectedVersionCode '5'`, and `-ExpectedVersionName '2.0.3'`; rerun the focused Pester test and expect PASS.
- [ ] Run `.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` and `Invoke-Pester .\scripts\tests`; expect no failures.
- [ ] Run `.\scripts\build-release-apk.ps1`; verify `dist/tracking-gps-2.0.3.apk` with `Get-FileHash`, `aapt dump badging`, and `apksigner verify --verbose --print-certs`.
- [ ] Update release/operator documentation to 2.0.3 and add acceptance that an in-place update restores enabled tracking without reboot after `MY_PACKAGE_REPLACED` or opening the app.
- [ ] Commit version changes as `build: promote update recovery as version 2.0.3` and documentation as `docs: release update recovery 2.0.3`; keep `.diagnostics/` and `.driver-downloads/` untracked.
