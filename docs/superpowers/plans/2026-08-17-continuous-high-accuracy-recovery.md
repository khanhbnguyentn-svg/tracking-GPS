# Continuous High-Accuracy Recovery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prevent route loss after a detected stop by keeping a 10-second high-accuracy location request active for every movement mode, then publish the signed correction as version 2.0.2.

**Architecture:** Extract the movement-mode-to-location-priority decision into a small unit-testable policy and make `TrackingService` use it whenever it starts or processes a fix. Preserve the movement detector and persistence rules unchanged. Promote the verified correction through the existing stable signed-release pipeline with a new immutable version code.

**Tech Stack:** Kotlin, Android foreground location service, Google Play Services Location, JUnit 4, Gradle, PowerShell/Pester, Android SDK Build Tools 36, ADB.

## Global Constraints

- Tracking requests use `PRIORITY_HIGH_ACCURACY` every 10 seconds in `IDLE`, `MOVING`, and `STOP_CANDIDATE`.
- Database persistence remains `START`, `TEMP_STOP`, `STOP`, plus `PERIODIC` every 2 minutes while moving.
- Stationary periods do not create a database row every 2 minutes.
- No location fix is discarded because of age or accuracy.
- Activity Recognition remains supplementary and does not gate high-accuracy GPS.
- Report scheduling, CSV generation, email delivery, and delivery states remain unchanged.
- Publish as `versionName = "2.0.2"`, `versionCode = 4`; never overwrite the distributed 2.0.1 artifact.

---

## File Structure

- Create `app/src/main/java/com/internal/tracker/tracking/LocationPriorityPolicy.kt`: own the pure movement-mode-to-Play-Services-priority mapping.
- Create `app/src/test/java/com/internal/tracker/tracking/LocationPriorityPolicyTest.kt`: lock high accuracy for every current movement mode.
- Modify `app/src/main/java/com/internal/tracker/tracking/TrackingService.kt`: delegate request priority selection to the policy.
- Modify `app/build.gradle.kts`: set version 2.0.2 / code 4.
- Modify `scripts/tests/BuildReleaseCommand.Tests.ps1`: expect the new immutable artifact identity.
- Modify `scripts/build-release-apk.ps1`: promote only 2.0.2 / code 4.
- Modify `README.md`, `docs/stable-apk-update-runbook.md`, and `docs/android-14-device-test-checklist.md`: point operators to the new release and upgrade checks.

### Task 1: Lock High Accuracy Across All Movement Modes

**Files:**
- Create: `app/src/test/java/com/internal/tracker/tracking/LocationPriorityPolicyTest.kt`
- Create: `app/src/main/java/com/internal/tracker/tracking/LocationPriorityPolicy.kt`
- Modify: `app/src/main/java/com/internal/tracker/tracking/TrackingService.kt`

**Interfaces:**
- Consumes: `MovementMode.IDLE`, `MovementMode.MOVING`, `MovementMode.STOP_CANDIDATE`.
- Produces: `LocationPriorityPolicy.forMode(mode: MovementMode): Int`.

- [ ] **Step 1: Write the failing policy test**

```kotlin
package com.internal.tracker.tracking

import com.google.android.gms.location.Priority
import org.junit.Assert.assertEquals
import org.junit.Test

class LocationPriorityPolicyTest {
    @Test
    fun everyTrackingModeKeepsHighAccuracyGpsActive() {
        MovementMode.entries.forEach { mode ->
            assertEquals(
                "$mode must not disable high-accuracy GPS",
                Priority.PRIORITY_HIGH_ACCURACY,
                LocationPriorityPolicy.forMode(mode),
            )
        }
    }
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.internal.tracker.tracking.LocationPriorityPolicyTest
```

Expected: compilation fails because `LocationPriorityPolicy` does not exist.

- [ ] **Step 3: Add the minimal policy**

```kotlin
package com.internal.tracker.tracking

import com.google.android.gms.location.Priority

object LocationPriorityPolicy {
    fun forMode(mode: MovementMode): Int = when (mode) {
        MovementMode.IDLE,
        MovementMode.MOVING,
        MovementMode.STOP_CANDIDATE,
        -> Priority.PRIORITY_HIGH_ACCURACY
    }
}
```

- [ ] **Step 4: Wire `TrackingService` to the policy**

Replace both `priorityFor(mode)` calls with `LocationPriorityPolicy.forMode(mode)`, remove the private `priorityFor` method, and remove the now-unused `Priority` import from `TrackingService.kt`. Do not alter `LOCATION_INTERVAL_MILLIS`, `MovementDetector`, or persistence code.

- [ ] **Step 5: Verify GREEN and regression behavior**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.internal.tracker.tracking.LocationPriorityPolicyTest --tests com.internal.tracker.tracking.MovementDetectorTest --tests com.internal.tracker.tracking.TrackingCoordinatorTest
```

Expected: all selected tests pass.

- [ ] **Step 6: Commit the behavior fix**

```powershell
.\.tools\mingit\cmd\git.exe add app/src/main/java/com/internal/tracker/tracking/LocationPriorityPolicy.kt app/src/main/java/com/internal/tracker/tracking/TrackingService.kt app/src/test/java/com/internal/tracker/tracking/LocationPriorityPolicyTest.kt
.\.tools\mingit\cmd\git.exe commit -m "fix: keep high accuracy GPS active after stops"
```

### Task 2: Promote the Fix as Version 2.0.2

**Files:**
- Modify: `scripts/tests/BuildReleaseCommand.Tests.ps1`
- Modify: `app/build.gradle.kts`
- Modify: `scripts/build-release-apk.ps1`

**Interfaces:**
- Consumes: existing stable signing configuration and release verification helpers.
- Produces: `dist/tracking-gps-2.0.2.apk`, package `com.internal.tracker`, version `2.0.2 (4)`.

- [ ] **Step 1: Change the release pipeline test first**

Update the Pester test name and all expected artifact/version values from `2.0.1 (3)` to `2.0.2 (4)`:

```powershell
It 'builds and promotes only the approved version 2.0.2 APK' {
    $projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
    $scriptPath = Join-Path $projectRoot 'scripts\build-release-apk.ps1'
    $outputDirectory = Join-Path $TestDrive 'dist'
    $previousSmtpUser = $env:SMTP_USER
    $previousSmtpPassword = $env:SMTP_APP_PASSWORD

    try {
        $env:SMTP_USER = ''
        $env:SMTP_APP_PASSWORD = ''
        $output = & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $scriptPath `
            -OutputDirectory $outputDirectory 2>&1
        $exitCode = $LASTEXITCODE
    } finally {
        $env:SMTP_USER = $previousSmtpUser
        $env:SMTP_APP_PASSWORD = $previousSmtpPassword
    }

    if ($exitCode -ne 0) {
        throw "Release build command failed: $($output -join ' ')"
    }
    $releaseApk = Join-Path $outputDirectory 'tracking-gps-2.0.2.apk'
    Test-Path $releaseApk | Should Be $true
    @(Get-ChildItem $outputDirectory -File).Count | Should Be 1
    ($output -join "`n") | Should Match 'com.internal.tracker'
    ($output -join "`n") | Should Match '2.0.2 \(4\)'
    ($output -join "`n") | Should Match '8F1912A34ED2CB9DDF8840DB49A769134251B3297484333678E2C679CAE4F585'
}
```

- [ ] **Step 2: Run the release test and verify RED**

Run:

```powershell
Invoke-Pester .\scripts\tests\BuildReleaseCommand.Tests.ps1 -Output Detailed
```

Expected: failure because production version/build script still target 2.0.1 / code 3.

- [ ] **Step 3: Apply the minimal version promotion**

Set in `app/build.gradle.kts`:

```kotlin
versionCode = 4
versionName = "2.0.2"
```

Set in `scripts/build-release-apk.ps1`:

```powershell
-ExpectedVersionCode '4' `
-ExpectedVersionName '2.0.2' `
```

- [ ] **Step 4: Verify GREEN**

Run the Task 2 Pester command again. Expected: PASS and the temporary test artifact is named `tracking-gps-2.0.2.apk`.

- [ ] **Step 5: Commit the version promotion**

```powershell
.\.tools\mingit\cmd\git.exe add app/build.gradle.kts scripts/build-release-apk.ps1 scripts/tests/BuildReleaseCommand.Tests.ps1
.\.tools\mingit\cmd\git.exe commit -m "build: promote GPS recovery as version 2.0.2"
```

### Task 3: Verify, Build, and Document the Stable Update

**Files:**
- Modify: `README.md`
- Modify: `docs/stable-apk-update-runbook.md`
- Modify: `docs/android-14-device-test-checklist.md`
- Create: `dist/tracking-gps-2.0.2.apk` through the canonical build script

**Interfaces:**
- Consumes: Task 1 behavior and Task 2 release identity.
- Produces: signed 2.0.2 APK plus operator instructions for an in-place update.

- [ ] **Step 1: Run the complete automated verification suite**

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
Invoke-Pester .\scripts\tests -Output Detailed
```

Expected: all Android unit tests, lint, debug compilation, and Pester tests pass.

- [ ] **Step 2: Build the signed immutable release**

```powershell
.\scripts\build-release-apk.ps1
```

Expected: creates `dist/tracking-gps-2.0.2.apk` without replacing `dist/tracking-gps-2.0.1.apk`.

- [ ] **Step 3: Verify artifact identity and signature**

```powershell
Get-FileHash .\dist\tracking-gps-2.0.2.apk -Algorithm SHA256
.\.tools\android-sdk\build-tools\36.0.0\aapt.exe dump badging .\dist\tracking-gps-2.0.2.apk
.\.tools\android-sdk\build-tools\36.0.0\apksigner.bat verify --verbose --print-certs .\dist\tracking-gps-2.0.2.apk
```

Expected: package `com.internal.tracker`, version code 4, version name 2.0.2, and the same approved signing certificate as 2.0.1.

- [ ] **Step 4: Update operator documentation**

Update the three documentation files to identify 2.0.2 as current, use `dist/tracking-gps-2.0.2.apk` in commands, and require this acceptance check:

> While tracking remains enabled and the vehicle has been stopped for more than two minutes, Android must continue to show a 10-second `HIGH_ACCURACY` request for `com.internal.tracker`; movement afterward must produce a new `START` without reopening the app.

- [ ] **Step 5: Install in place only after confirming the device is safe to interrupt briefly**

```powershell
.\.tools\android-sdk\platform-tools\adb.exe install -r .\dist\tracking-gps-2.0.2.apk
```

Do not uninstall and do not clear app storage. Open the app once after update so it reconciles the foreground service and report schedule. Confirm version 2.0.2, retained History/configuration/PIN, active foreground service, and a 10-second high-accuracy request.

- [ ] **Step 6: Commit documentation; do not commit private diagnostic exports**

```powershell
.\.tools\mingit\cmd\git.exe add README.md docs/stable-apk-update-runbook.md docs/android-14-device-test-checklist.md
.\.tools\mingit\cmd\git.exe commit -m "docs: release continuous GPS recovery 2.0.2"
```

Confirm `.diagnostics/` and `.driver-downloads/` remain untracked and absent from every commit.
