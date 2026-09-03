# Android SET 3.0 Phase 2 Continuation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete the partially implemented Phase 2 work and deliver Android SET `3.1.0 (8)` as an encrypted, always-on, high-accuracy GPS black box with verified lifecycle recovery and truthful local diagnostics.

**Architecture:** Preserve the committed Phase 1 platform and Phase 2 raw repository. Complete the pure movement/health state machines first, then add Android location/lifecycle adapters, whole-day retention, and the minimal setup/status UI. Keep policy in pure Kotlin and Android orchestration thin so JVM tests establish behavior before instrumented and physical-device verification.

**Tech Stack:** Kotlin 2.2.10, Android API 26–36, Compose Material 3, Room 2.8.4, SQLCipher Android 4.17.0, Google Play Services Location 21.3.0, JUnit 4, AndroidX instrumentation.

**Spec:** `docs/superpowers/specs/2026-08-26-android-set-3.0-phase-2-black-box-design.md`

## Global Constraints

- Work only on `feature/android-set-3.0-design`; preserve the current uncommitted Task 3 files until Task 1 below either completes them or proves they must be replaced.
- Set exactly `versionName = "3.1.0"` and `versionCode = 8` only in the version/UI task.
- Never interpolate, filter, overwrite, or fabricate source locations.
- Use only Fused Location Provider with `PRIORITY_HIGH_ACCURACY`; ordinary interval and minimum interval are exactly `10_000L`, and boundary interval is exactly `1_000L`.
- Room plus SQLCipher is authoritative. Never use destructive migration or automatic database deletion.
- Never commit raw database files, coordinates, full Device IDs, PINs, credentials, signing material, or device-private identifiers.
- Keep raw data for one year in `Asia/Ho_Chi_Minh`; warn below 500 MiB and delete only eligible whole business days below 200 MiB.
- Driver/User receives no tracking-stop action. Phase 2 contains no Trip, email, vehicle, PIN, expense, or Maintenance behavior.
- Every behavior change follows red-green-refactor, exact-path staging, and a focused commit.
- Before claiming a task complete, run its focused tests, full JVM tests, `lintDebug`, `assembleDebug`, and `git diff --check`.

## Current Baseline and File Map

Committed and verified foundations:

- `tracking/core/**`: business time, UUID, stable Device ID, wrapped SQLCipher passphrase.
- `tracking/database/TrackingDatabase.kt`: encrypted Room schema through committed version 2.
- `tracking/raw/**`: boot sessions, exact raw rows, monotonic ten-second gate, atomic sequence allocation.

Uncommitted Task 3 work requiring correction:

- `tracking/database/TrackingDao.kt`, `TrackingDatabase.kt`, `TrackingEntities.kt`: draft schema version 3.
- `tracking/movement/**`, `tracking/health/**`, `tracking/model/MovementEvent.kt`, `HealthDirective.kt`: draft policies.
- `app/src/test/**/movement`, `app/src/test/**/health`, `app/src/androidTest/**/health`: incomplete tests.

New responsibility boundaries:

- `tracking/movement/**`: pure, immutable movement transition function and event values.
- `tracking/health/**`: pure callback-gap policy plus incident repository and coordinate-free recovery marker.
- `tracking/location/**`: exact Android `Location` mapper, ordinary Fused registration, and boundary capture.
- `tracking/service/**`: expectation persistence, reconciliation, foreground service, boot/package receiver, notification.
- `tracking/storage/**`: filesystem/database probe, threshold policy, protected-day contract, retention coordinator.
- `tracking/permission/**`: permission snapshot reader and explicit user-driven setup coordinator.
- `ui/SetHomeShell.kt`: Phase 2 status only.

---

### Task 1: Complete the Movement State Machine and Persisted Event Contract

**Files:**

- Modify: `app/src/main/java/com/internal/tracker/tracking/model/MovementEvent.kt`
- Modify: `app/src/main/java/com/internal/tracker/tracking/movement/Haversine.kt`
- Modify: `app/src/main/java/com/internal/tracker/tracking/movement/MovementClassifier.kt`
- Modify: `app/src/main/java/com/internal/tracker/tracking/database/TrackingEntities.kt`
- Modify: `app/src/main/java/com/internal/tracker/tracking/database/TrackingDao.kt`
- Modify: `app/src/test/java/com/internal/tracker/tracking/movement/MovementClassifierTest.kt`
- Modify: `app/src/androidTest/java/com/internal/tracker/tracking/health/TrackingIncidentRepositoryTest.kt`

**Interfaces:**

- Consumes: `PersistedRawSample(sequenceNumber: Long, sample: RawLocationSample)` from Phase 2 Task 2.
- Produces: `MovementTransition`, `MovementSnapshot`, and `MovementEvent` containing type, effective/confirmed UTC times, first/confirming raw sequences, and algorithm version `1`.

- [ ] **Step 1: Extend failing movement tests.** Assert exact 19.9 m acceptance and 20.1 m cancellation; source-speed precedence; accuracy-subtracted fallback speed; invalid latitude/longitude and non-increasing elapsed time returning `UNKNOWN`; confirmation only after 60 seconds; one moving sample not resuming; the second moving sample emitting `RESUME` whose effective time/sequence come from the first moving sample.

```kotlin
assertEquals(emptyList<MovementEvent>(), classifier.onSample(persistedStopped(0)))
assertEquals(TEMP_STOP_STARTED, classifier.onSample(persistedStopped(60)).events.single().type)
assertEquals(firstMoving.sample.capturedUtcMillis, resumed.events.single().effectiveAtUtcMillis)
assertEquals(firstMoving.sequenceNumber, resumed.events.single().firstSourceSequenceNumber)
```

- [ ] **Step 2: Run the movement test and verify RED.**

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.internal.tracker.tracking.movement.MovementClassifierTest" --offline --no-daemon --console=plain
```

Expected: FAIL because the draft event lacks effective/confirmation times and source sequences and the draft state machine does not retain the first resume sample.

- [ ] **Step 3: Replace hidden mutable output with an explicit transition contract.** Retain only bounded classifier state; validate finite coordinates in legal ranges and strictly increasing monotonic time; never mutate `RawLocationSample`; use source speed when non-null; otherwise compute `max(0, distance - priorAccuracy - currentAccuracy) / elapsedSeconds` only for classification.

```kotlin
data class MovementEvent(
    val type: MovementEventType,
    val effectiveAtUtcMillis: Long,
    val confirmedAtUtcMillis: Long,
    val firstSourceSequenceNumber: Long,
    val confirmingSourceSequenceNumber: Long,
    val algorithmVersion: Int = 1,
)
```

- [ ] **Step 4: Align Room entity/DAO with the event contract.** Store both timestamps and both source sequences; add foreign keys to `gps_raw_sample(sequenceNumber)` with deletion behavior compatible with whole-day cleanup; keep the time index required for retention queries.

- [ ] **Step 5: Run focused JVM and encrypted Room tests.**

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.internal.tracker.tracking.movement.MovementClassifierTest" --offline --no-daemon --console=plain
.\gradlew.bat assembleDebugAndroidTest --offline --no-daemon --console=plain
```

Expected: movement JVM tests PASS and androidTest APK compiles.

- [ ] **Step 6: Commit only the completed movement files.**

```powershell
git add app/src/main/java/com/internal/tracker/tracking/model/MovementEvent.kt app/src/main/java/com/internal/tracker/tracking/movement app/src/main/java/com/internal/tracker/tracking/database/TrackingEntities.kt app/src/main/java/com/internal/tracker/tracking/database/TrackingDao.kt app/src/test/java/com/internal/tracker/tracking/movement app/src/androidTest/java/com/internal/tracker/tracking/health/TrackingIncidentRepositoryTest.kt app/schemas/com.internal.tracker.tracking.database.TrackingDatabase/3.json
git commit -m "feat: derive and persist movement state"
```

### Task 2: Complete GPS Health, Typed Incidents, and Recovery Marker

**Files:**

- Create: `app/src/main/java/com/internal/tracker/tracking/model/TrackingIncident.kt`
- Modify: `app/src/main/java/com/internal/tracker/tracking/model/HealthDirective.kt`
- Modify: `app/src/main/java/com/internal/tracker/tracking/health/TrackingHealthMonitor.kt`
- Create: `app/src/main/java/com/internal/tracker/tracking/health/TrackingIncidentRepository.kt`
- Create: `app/src/main/java/com/internal/tracker/tracking/health/IncidentRecoveryMarker.kt`
- Modify: `app/src/main/java/com/internal/tracker/tracking/database/TrackingEntities.kt`
- Modify: `app/src/main/java/com/internal/tracker/tracking/database/TrackingDao.kt`
- Modify: `app/src/test/java/com/internal/tracker/tracking/health/TrackingHealthMonitorTest.kt`
- Modify: `app/src/androidTest/java/com/internal/tracker/tracking/health/TrackingIncidentRepositoryTest.kt`

**Interfaces:**

- Produces: typed `GPS_GAP`, `PERMISSION`, `PROVIDER`, `SERVICE`, `PERSISTENCE`, and `CLOCK_CHANGE` incidents; `TrackingIncidentRepository.open`, `close`, and `recoverMarker`; a pure monitor that identifies the same open gap across callbacks.

- [ ] **Step 1: Add failing health tests.** Assert no gap at 29.999 seconds; one open at 30 seconds; no duplicate open; immediate forced re-registration directive when the gap opens; further re-registration no more often than every five minutes; callback closes the same incident; restart from persisted open-gap state; non-gap incident types never contain coordinates.

- [ ] **Step 2: Run focused tests and verify RED.**

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.internal.tracker.tracking.health.TrackingHealthMonitorTest" --offline --no-daemon --console=plain
```

- [ ] **Step 3: Implement pure gap timing.** Pass the last callback, open-gap identity, and last registration-attempt elapsed time through explicit state. Use callback receipt elapsed time only; return directives containing the incident identifier/timing required by the service.

- [ ] **Step 4: Implement repository and encrypted marker.** `TrackingIncidentRepository` owns DAO calls and enforces a singleton open GPS gap. `IncidentRecoveryMarker` uses encrypted preferences and stores only type/opened timing/closed timing; on the next writable database transaction, persist the interval then clear the marker.

- [ ] **Step 5: Run JVM and instrumentation compilation.**

```powershell
.\gradlew.bat testDebugUnitTest --offline --no-daemon --console=plain
.\gradlew.bat assembleDebugAndroidTest --offline --no-daemon --console=plain
```

- [ ] **Step 6: Commit the health slice.**

```powershell
git add app/src/main/java/com/internal/tracker/tracking/model/HealthDirective.kt app/src/main/java/com/internal/tracker/tracking/model/TrackingIncident.kt app/src/main/java/com/internal/tracker/tracking/health app/src/main/java/com/internal/tracker/tracking/database app/src/test/java/com/internal/tracker/tracking/health app/src/androidTest/java/com/internal/tracker/tracking/health app/schemas/com.internal.tracker.tracking.database.TrackingDatabase/3.json
git commit -m "feat: persist GPS health incidents"
```

### Task 3: Add Exact Fused Mapping and Boundary Capture

**Files:**

- Create: `app/src/main/java/com/internal/tracker/tracking/location/LocationSource.kt`
- Create: `app/src/main/java/com/internal/tracker/tracking/location/AndroidLocationMapper.kt`
- Create: `app/src/main/java/com/internal/tracker/tracking/location/FusedLocationSource.kt`
- Create: `app/src/main/java/com/internal/tracker/tracking/location/BoundaryCaptureManager.kt`
- Create: `app/src/test/java/com/internal/tracker/tracking/location/BoundaryCaptureManagerTest.kt`
- Create: `app/src/androidTest/java/com/internal/tracker/tracking/location/AndroidLocationMapperTest.kt`

**Interfaces:**

- Consumes: `RawLocationRepository.persistOrdinary` and `persistBoundary`.
- Produces: one ordinary Fused registration and keyed, independently completed boundary requests.

- [ ] **Step 1: Write failing mapper tests.** Construct `Location` values with each optional field present and absent; assert exact UTC, elapsed time, coordinates, provider/mock flag, and null preservation.
- [ ] **Step 2: Write failing boundary tests.** Assert stale fixes are ignored, first fresh fix wins exactly once, timeout returns `Unavailable`, concurrent request IDs are isolated, and ordinary registration is never stopped.
- [ ] **Step 3: Run the focused tests and verify RED.**
- [ ] **Step 4: Implement `AndroidLocationMapper`.** Call each `hasAltitude`, `hasAccuracy`, `hasVerticalAccuracy`, `hasSpeed`, `hasSpeedAccuracy`, `hasBearing`, and `hasBearingAccuracy` only to select exact value versus null.
- [ ] **Step 5: Implement `FusedLocationSource`.** Use one ordinary `LocationRequest` with high accuracy, `10_000L` interval and `10_000L` minimum interval; sort batches by `elapsedRealtimeNanos`; surface Google Play Services failures without crashing.
- [ ] **Step 6: Implement boundary capture.** Record action UTC/elapsed time first, run a separate one-second high-accuracy request, accept the first fix not older than the action, persist one boundary row, and finalize after ten seconds with `BOUNDARY_GPS_UNAVAILABLE`.
- [ ] **Step 7: Run focused/full tests and commit.**

```powershell
.\gradlew.bat testDebugUnitTest assembleDebugAndroidTest --offline --no-daemon --console=plain
git add app/src/main/java/com/internal/tracker/tracking/location app/src/test/java/com/internal/tracker/tracking/location app/src/androidTest/java/com/internal/tracker/tracking/location
git commit -m "feat: capture exact fused locations"
```

### Task 4: Add Always-On Service and Reconciliation

**Files:**

- Create: `app/src/main/java/com/internal/tracker/tracking/service/TrackingExpectationStore.kt`
- Create: `app/src/main/java/com/internal/tracker/tracking/service/TrackingReconciler.kt`
- Create: `app/src/main/java/com/internal/tracker/tracking/service/TrackingNotificationFactory.kt`
- Create: `app/src/main/java/com/internal/tracker/tracking/service/AlwaysOnTrackingService.kt`
- Create: `app/src/main/java/com/internal/tracker/tracking/service/TrackingBootReceiver.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/internal/tracker/TrackerApplication.kt`
- Create: `app/src/test/java/com/internal/tracker/tracking/service/TrackingReconcilerTest.kt`
- Create: `app/src/androidTest/java/com/internal/tracker/tracking/service/AlwaysOnTrackingServiceTest.kt`

- [ ] **Step 1: Write failing reconciler tests.** Cover not-configured, expected/ready, permission missing, provider disabled, locked profile after boot, background-start rejection, package replacement, process recreation, and idempotent already-running states.
- [ ] **Step 2: Run focused tests and verify RED.**
- [ ] **Step 3: Implement encrypted expectation storage and pure reconciliation.** Persist only `NOT_CONFIGURED` or `EXPECTED` in Phase 2; return typed start/repair/incident effects.
- [ ] **Step 4: Implement the foreground service.** Call `startForeground` immediately, return `START_STICKY`, create one boot session, own one ordinary location registration, record callback receipt before persistence, commit raw before movement, and keep reconciliation alive after sanitized operational failures.
- [ ] **Step 5: Implement receiver and manifest declarations.** Declare the non-exported location FGS and non-exported receiver for `BOOT_COMPLETED` and `MY_PACKAGE_REPLACED`; wait for an unlocked profile before opening SQLCipher.
- [ ] **Step 6: Add an instrumented assertion for exactly one high-accuracy ordinary registration and no notification Stop action.**
- [ ] **Step 7: Run tests/lint/build and commit.**

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest --offline --no-daemon --console=plain
git add app/src/main/AndroidManifest.xml app/src/main/java/com/internal/tracker/TrackerApplication.kt app/src/main/java/com/internal/tracker/tracking/service app/src/test/java/com/internal/tracker/tracking/service app/src/androidTest/java/com/internal/tracker/tracking/service
git commit -m "feat: run always-on GPS tracking"
```

### Task 5: Add Storage Telemetry, Whole-Day Retention, and One-Retry Recovery

**Files:**

- Create: `app/src/main/java/com/internal/tracker/tracking/model/StorageSnapshot.kt`
- Create: `app/src/main/java/com/internal/tracker/tracking/storage/StoragePolicy.kt`
- Create: `app/src/main/java/com/internal/tracker/tracking/storage/StorageProbe.kt`
- Create: `app/src/main/java/com/internal/tracker/tracking/storage/ProtectedRawDayResolver.kt`
- Create: `app/src/main/java/com/internal/tracker/tracking/storage/RetentionCoordinator.kt`
- Modify: `app/src/main/java/com/internal/tracker/tracking/database/TrackingDao.kt`
- Modify: `app/src/main/java/com/internal/tracker/tracking/database/TrackingEntities.kt`
- Create: `app/src/test/java/com/internal/tracker/tracking/storage/StoragePolicyTest.kt`
- Create: `app/src/androidTest/java/com/internal/tracker/tracking/storage/RetentionCoordinatorTest.kt`

- [ ] **Step 1: Write failing threshold/day tests.** Assert `500 MiB` is `NORMAL`, one byte below is `WARNING`, `200 MiB` is `WARNING`, one byte below is `CRITICAL`; map UTC rows to `Asia/Ho_Chi_Minh` whole days; never choose protected/current/partial days.
- [ ] **Step 2: Write failing coordinator tests.** Assert one-year cleanup, one day per critical iteration, stop at 200 MiB, movement reference cleanup, cleanup audit values, WAL checkpoint/incremental vacuum, and exactly one insert retry after storage pressure.
- [ ] **Step 3: Run focused tests and verify RED.**
- [ ] **Step 4: Implement probe and pure policy.** Report total/free/database/WAL/staging bytes, row count, oldest/newest time, retained days, status, and cleanup count. Phase 2 protected-day resolver returns `emptySet()` behind the interface.
- [ ] **Step 5: Implement transactional whole-day deletion and bounded reclaim.** Delete raw and derived movement references in one business-day range, record the audit, checkpoint WAL, run bounded incremental vacuum, re-probe, and repeat only while below 200 MiB.
- [ ] **Step 6: Wire probes at app/service start, every 15 minutes, and after storage-related write failure; retry a failed raw insert once only.**
- [ ] **Step 7: Run tests/lint/build and commit.**

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebugAndroidTest --offline --no-daemon --console=plain
git add app/src/main/java/com/internal/tracker/tracking/model/StorageSnapshot.kt app/src/main/java/com/internal/tracker/tracking/storage app/src/main/java/com/internal/tracker/tracking/database app/src/test/java/com/internal/tracker/tracking/storage app/src/androidTest/java/com/internal/tracker/tracking/storage app/schemas/com.internal.tracker.tracking.database.TrackingDatabase/3.json
git commit -m "feat: retain raw GPS by whole business day"
```

### Task 6: Deliver Explicit Setup/Repair and Phase 2 Status UI

**Files:**

- Modify: `app/src/main/java/com/internal/tracker/tracking/PermissionState.kt`
- Create: `app/src/main/java/com/internal/tracker/tracking/permission/AndroidPermissionSnapshotReader.kt`
- Create: `app/src/main/java/com/internal/tracker/tracking/permission/TrackingSetupCoordinator.kt`
- Modify: `app/src/main/java/com/internal/tracker/MainActivity.kt`
- Modify: `app/src/main/java/com/internal/tracker/ui/SetHomeShell.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/test/java/com/internal/tracker/ProjectConfigTest.kt`
- Create: `app/src/test/java/com/internal/tracker/tracking/permission/TrackingSetupCoordinatorTest.kt`
- Create: `app/src/androidTest/java/com/internal/tracker/tracking/permission/PermissionOnboardingTest.kt`

- [ ] **Step 1: Write failing setup tests.** Assert visible-activity fine request, API 33+ notification request, API 30+ background-location explanation/settings, no prompt loops after denial, provider repair, and persistence of `EXPECTED` before service start only when ready.
- [ ] **Step 2: Add failing UI/version assertions.** Require `3.1.0 (8)`; require tracking/provider/latest raw/today count/storage/version; forbid coordinates, Device ID, Trip, email, Maintenance, and `Dừng theo dõi`.
- [ ] **Step 3: Run focused tests and verify RED.**
- [ ] **Step 4: Implement coordinator and Activity result flow.** Permissions begin only after the large user action; denial leaves one deliberate `Khắc phục` action.
- [ ] **Step 5: Implement status Home and notification text.** Show only Phase 2 state; notification text is `SET đang theo dõi GPS`, opens the app, and has no actions.
- [ ] **Step 6: Change version to `3.1.0` / `8`; run full JVM, lint, debug APK, and androidTest APK builds.**
- [ ] **Step 7: Commit.**

```powershell
git add app/build.gradle.kts app/src/main/java/com/internal/tracker/MainActivity.kt app/src/main/java/com/internal/tracker/tracking/PermissionState.kt app/src/main/java/com/internal/tracker/tracking/permission app/src/main/java/com/internal/tracker/ui/SetHomeShell.kt app/src/main/res/values/strings.xml app/src/test/java/com/internal/tracker/ProjectConfigTest.kt app/src/test/java/com/internal/tracker/tracking/permission app/src/androidTest/java/com/internal/tracker/tracking/permission
git commit -m "feat: add phase 2 tracking setup status"
```

### Task 7: Verify Schema Update Contract and Physical Samsung Acceptance

**Files:**

- Create: `app/src/androidTest/java/com/internal/tracker/tracking/database/TrackingDatabaseUpdateRetentionTest.kt`
- Create: `docs/android-set-3.1.0-device-acceptance.md`
- Modify: `README.md`

- [ ] **Step 1: Add a migration fixture test.** Create the prior committed encrypted schema with boot/raw rows and sequence state, open schema v3 through an explicit non-destructive migration, and assert exact raw values, sequence continuity, incident access, and passphrase reuse.
- [ ] **Step 2: Run the fixture and verify RED before registering the migration; implement the explicit migration and rerun to PASS.**
- [ ] **Step 3: Run the static release gate.**

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest --offline --no-daemon --console=plain
git diff --check
git status --short
```

Expected: all commands PASS; status contains only expected source/docs changes and no private artifact.

- [ ] **Step 4: Connect Samsung `RF8XA015NLV` by USB with USB debugging authorized.** Confirm it appears in `adb devices -l`; do not install or grant permissions until requested evidence from any existing app is preserved.
- [ ] **Step 5: Perform the approved one-time clean installation and manual onboarding.** Do not use `adb pm grant`. Record time, model/API, build identity/certificate, expected result, observed result, and evidence path.
- [ ] **Step 6: Execute physical checks.** Verify ten-second high-accuracy registration, stationary/moving raw cadence, lock screen, recents swipe, 45-second Location off/on producing one gap/recovery, process replacement, reboot/unlock, TEMP_STOP/Resume during a real drive, boundary harness, and absence of crash/ANR/security/FGS exceptions.
- [ ] **Step 7: Build version code 9 from the same signing identity in a disposable verification branch or worktree and install with `adb install -r`.** Assert Device ID, expectation, raw count, next sequence, incidents, and encrypted database accessibility are retained. Do not merge the temporary version bump.
- [ ] **Step 8: Mark every unperformed physical row `PENDING`; never infer a PASS. Commit only factual acceptance evidence.**

```powershell
git add app/src/androidTest/java/com/internal/tracker/tracking/database/TrackingDatabaseUpdateRetentionTest.kt docs/android-set-3.1.0-device-acceptance.md README.md
git commit -m "test: record phase 2 device acceptance"
```

## Decisions Locked by This Continuation Plan

- The 10-second ordinary and 1-second boundary intervals are exact request-builder values; Android delivery timing remains observational and is not represented as exact cadence.
- Re-registration occurs immediately when a 30-second gap opens, then no more than once per five minutes while the gap remains open.
- Database schema v3 is not accepted merely because Room generated `3.json`; an explicit non-destructive migration from the committed schema is required before device installation.
- Movement event persistence stores effective and confirmation times plus both source sequence identifiers; raw rows remain immutable.
- Phase 2 retains Activity Recognition permission only as unused future-compatible declaration if lint/policy review permits it; no Activity Recognition logic is added.

## Deferred to Separate Plans

- Phase 3: Package/PIN/calendar/Trip/vehicle/expense domain and protected-day implementation.
- Phase 4: deterministic email package, durable queue, Gmail primary/backup transport.
- Phase 5: full Compose flows and Maintenance/Recovery/Reset.
- Phase 6: composition cleanup, 3,153,600-row qualification, API/device matrix, and signed 3.0 production release.

## Plan Self-Review

- Spec coverage: Tasks 1–7 cover movement, health, encrypted incident persistence, exact Fused capture, boundary capture, lifecycle recovery, storage/retention, setup/status UI, versioning, migration, and device acceptance.
- Scope exclusions: no Trip/email/Maintenance behavior enters Phase 2.
- Type consistency: all Android consumers use committed `RawLocationRepository`; movement consumes `PersistedRawSample`; service receives typed directives; cleanup uses `ProtectedRawDayResolver` for Phase 3 extension.
- Verification boundary: static verification can run locally; instrumentation and physical acceptance require an authorized Android device.
