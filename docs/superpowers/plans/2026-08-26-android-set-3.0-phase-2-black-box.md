# Android SET 3.0 Phase 2 Black-Box Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build Android SET `3.1.0 (8)`: an encrypted always-on high-accuracy GPS black box that stores exact ordinary raw GPS at most once per monotonic ten-second window without creating a Trip.

**Architecture:** `AlwaysOnTrackingService` owns Android lifecycle only. It delegates Fused callbacks to a transactional SQLCipher Room repository and pure movement, health, and storage policies. Phase 2 has no Trip, email, or Maintenance business flow.

**Tech Stack:** Kotlin 2.2.10, Android API 26–36, Compose Material 3, Room 2.8.4, SQLCipher Android 4.17.0, Google Play Services Location 21.3.0, JUnit 4, AndroidX instrumentation.

**Spec:** `docs/superpowers/specs/2026-08-26-android-set-3.0-phase-2-black-box-design.md`

## Global Constraints

- Work only on `feature/android-set-3.0-design`; start each task with a clean worktree.
- Set exactly `versionName = "3.1.0"` and `versionCode = 8`.
- `3.1.0 (8)` is the one approved clean-install build. Every later build must update in place and retain Device ID, tracking expectation, raw count, sequence, and SQLCipher access.
- Never interpolate, filter, overwrite, or fabricate source locations.
- Use only Fused Location Provider, always `PRIORITY_HIGH_ACCURACY`, interval/minimum interval `10_000L`.
- `LocationManager` is provider-status only. Driver/User receives no tracking-stop action.
- Room plus SQLCipher is authoritative. Never commit raw database files, coordinates, full Device IDs, PINs, credentials, or signing material.
- Keep raw one year; warn below 500 MiB; below 200 MiB remove only eligible whole `Asia/Ho_Chi_Minh` business days until free space reaches 200 MiB.
- Use `apply_patch` for edits and exact-path Git staging. Commit after every completed task.

## File structure

| Path | Responsibility |
| --- | --- |
| `tracking/database/**` | SQLCipher Room database, entities, DAO, and factory. |
| `tracking/model/**` | Immutable raw, incident, movement, boundary, and storage values. |
| `tracking/raw/**` | Monotonic gate, sequence allocation, and raw persistence. |
| `tracking/movement/**` | Pure TEMP_STOP state machine. |
| `tracking/health/**` | Pure callback-gap/recovery policy. |
| `tracking/location/**` | Fused adapter, Android mapper, and boundary burst manager. |
| `tracking/storage/**` | Probe, protected-day contract, retention, and cleanup. |
| `tracking/service/**` | Foreground service, notification, expectation, reconciliation, receiver. |
| `tracking/permission/**` | Permission snapshot and setup effects. |
| `ui/SetHomeShell.kt` | Minimal Phase 2 status/setup surface. |

## Task 1: Add Fused dependency and encrypted production database

**Files:**

- Modify: `gradle/libs.versions.toml`, `app/build.gradle.kts`
- Create: `tracking/database/TrackingDatabase.kt`, `TrackingDatabaseFactory.kt`, `DatabasePragmas.kt`
- Test: `app/src/androidTest/java/com/internal/tracker/tracking/database/TrackingDatabaseIntegrationTest.kt`

**Interfaces produced:**

```kotlin
abstract class TrackingDatabase : RoomDatabase()
class TrackingDatabaseFactory(
    private val context: Context,
    private val passphraseStore: DatabasePassphraseStore,
    private val factoryProvider: SqlCipherFactoryProvider,
) { fun open(): TrackingDatabase }
```

- [ ] **Step 1: Write the failing test.**

```kotlin
@Test fun opensWithSqlCipherWalAndForeignKeys() {
    val db = factory.open().openHelper.writableDatabase
    assertEquals("wal", db.query("PRAGMA journal_mode").use { it.moveToFirst(); it.getString(0).lowercase() })
    assertEquals(1, db.query("PRAGMA foreign_keys").use { it.moveToFirst(); it.getInt(0) })
}
```

- [ ] **Step 2: Run `./gradlew.bat connectedDebugAndroidTest --tests "com.internal.tracker.tracking.database.TrackingDatabaseIntegrationTest"`.** Expected: FAIL because factory/schema are absent.
- [ ] **Step 3: Add `play-services-location` version `21.3.0`, `implementation(libs.play.services.location)`, and Room schema output to `schemas/`.**
- [ ] **Step 4: Implement factory using the Phase 1 passphrase, SQLCipher factory, database name `tracking-set.db`, WAL, foreign keys, and `auto_vacuum=INCREMENTAL` before first schema creation.** Never add destructive migration.
- [ ] **Step 5: Re-run Step 2.** Expected: PASS.
- [ ] **Step 6: Commit exact files with message `feat: bootstrap encrypted tracking database`.**

## Task 2: Define raw schema, ten-second gate, and atomic persistence

**Files:**

- Create: `tracking/model/RawLocationSample.kt`
- Create: `tracking/database/TrackingEntities.kt`, `TrackingDao.kt`
- Modify: `tracking/database/TrackingDatabase.kt`
- Create: `tracking/raw/RawSampleGate.kt`, `RawLocationRepository.kt`, `RoomRawLocationRepository.kt`
- Test: `app/src/test/java/com/internal/tracker/tracking/raw/RawSampleGateTest.kt`
- Test: `app/src/androidTest/java/com/internal/tracker/tracking/raw/RoomRawLocationRepositoryTest.kt`

**Interfaces produced:**

```kotlin
enum class RawSampleKind { ORDINARY, START_BOUNDARY, END_BOUNDARY }
enum class BootReason { PROCESS_START, BOOT, PACKAGE_REPLACED }
data class PersistedRawSample(val sequenceNumber: Long, val sample: RawLocationSample)
interface RawLocationRepository {
    suspend fun startBootSession(reason: BootReason, nowUtcMillis: Long, nowElapsedNanos: Long): String
    suspend fun persistOrdinary(sample: RawLocationSample, bootSessionId: String): PersistedRawSample?
    suspend fun persistBoundary(sample: RawLocationSample, bootSessionId: String): PersistedRawSample
}
```

- [ ] **Step 1: Write failing tests.**

```kotlin
@Test fun acceptsOnlyOneOrdinarySamplePerTenSecondWindow() {
    assertTrue(gate.shouldPersist(sample(10.secondsNanos), null))
    assertFalse(gate.shouldPersist(sample(15.secondsNanos), 10.secondsNanos))
    assertTrue(gate.shouldPersist(sample(20.secondsNanos), 10.secondsNanos))
}
@Test fun rejectedWindowDoesNotAdvanceSequence() = runTest {
    assertEquals(1L, repository.persistOrdinary(sample(10.secondsNanos), bootId)?.sequenceNumber)
    assertNull(repository.persistOrdinary(sample(15.secondsNanos), bootId))
}
```

- [ ] **Step 2: Run the JVM and instrumented raw tests.** Expected: FAIL because model, DAO, and repository are absent.
- [ ] **Step 3: Implement entities for boot session, singleton sequence state, `gps_raw_sample`, Device ID/configuration.** Raw stores UTC, captured offset, elapsed realtime, coordinates, every nullable source altitude/accuracy/speed/bearing field, provider, mock flag, boot session, kind, and unique sequence. Index capture time and `(bootSessionId, elapsedRealtimeNanos)`.
- [ ] **Step 4: Implement gate.** Ordinary samples require elapsed advance `>= 10_000_000_000L`; non-increasing elapsed is rejected; boundary kinds bypass the gate. Never inspect accuracy, coordinate, speed, or epoch clock.
- [ ] **Step 5: Implement repository as a `Mutex` plus `RoomDatabase.withTransaction`: query last ordinary elapsed, gate, increment sequence state, insert exact row, return persisted sample.** A failed insert must roll back sequence allocation.
- [ ] **Step 6: Add exact-value/reopen and insert-rollback tests, then rerun focused tests.** Expected: PASS.
- [ ] **Step 7: Commit exact files with message `feat: persist encrypted raw GPS samples`.**

## Task 3: Implement movement, gap health, and incident persistence

**Files:**

- Create: `tracking/model/MovementEvent.kt`, `TrackingIncident.kt`
- Create: `tracking/movement/Haversine.kt`, `MovementClassifier.kt`
- Create: `tracking/health/TrackingHealthMonitor.kt`
- Modify: `tracking/database/TrackingEntities.kt`, `TrackingDao.kt`
- Test: `app/src/test/java/com/internal/tracker/tracking/movement/MovementClassifierTest.kt`
- Test: `app/src/test/java/com/internal/tracker/tracking/health/TrackingHealthMonitorTest.kt`
- Test: `app/src/androidTest/java/com/internal/tracker/tracking/health/TrackingIncidentRepositoryTest.kt`

**Interfaces produced:**

```kotlin
enum class MovementState { MOVING, STOP_CANDIDATE, TEMP_STOP }
enum class MovementEventType { TEMP_STOP_STARTED, TEMP_STOP_ENDED, RESUME }
sealed interface HealthDirective { data object None; data object OpenGap; data object ReRegister; data object CloseGap }
```

- [ ] **Step 1: Write failing tests.**

```kotlin
@Test fun confirmsTempStopAtSixtySecondsInsideTwentyMeters() {
    val candidate = classifier.onSample(MOVING, stopped(0), null)
    val pending = classifier.onSample(candidate.state, stopped(50), stopped(0))
    assertTrue(pending.events.isEmpty())
    assertEquals(TEMP_STOP_STARTED, classifier.onSample(pending.state, stopped(60), stopped(50)).events.single().type)
}
@Test fun resumesAfterTwoMovingSamples() {
    val one = classifier.onSample(TEMP_STOP, moving(70), stopped(60))
    assertTrue(one.events.isEmpty())
    assertEquals(RESUME, classifier.onSample(one.state, moving(80), moving(70)).events.single().type)
}
@Test fun opensOneGapAtThirtySecondsAndReregistersAfterFiveMinutes() {
    monitor.onStarted(0)
    assertEquals(OpenGap, monitor.onTick(30.secondsNanos))
    assertEquals(None, monitor.onTick(40.secondsNanos))
    assertEquals(ReRegister, monitor.onTick(330.secondsNanos))
}
```

- [ ] **Step 2: Run movement/health JVM tests.** Expected: FAIL because policies are absent.
- [ ] **Step 3: Implement movement.** Use source speed if present. If absent, derive only classifier speed as `max(0, haversine - priorAccuracy - currentAccuracy) / elapsedSeconds`; never write it into raw. Invalid time/coordinates are `UNKNOWN` and cannot confirm. Confirm after 60 seconds under 1 m/s within 20 m; Resume after two moving/outside-radius samples; persist algorithm version `1` and source sequences.
- [ ] **Step 4: Implement health.** Use callback receipt elapsed time only. Open one `GPS_GAP` after 30 seconds, throttle re-register to five minutes, and close same incident on callback. Persist `GPS_GAP`, `PERMISSION`, `PROVIDER`, `SERVICE`, `CLOCK_CHANGE`; use coordinate-free encrypted marker if Room cannot write.
- [ ] **Step 5: Add 19.9/20.1 m, missing-speed, one-moving-sample, and close-gap cases. Rerun JVM plus incident instrumentation tests.** Expected: PASS.
- [ ] **Step 6: Commit exact files with message `feat: derive movement and GPS gap state`.**

## Task 4: Add Fused adapter, boundary capture, service, and reconciliation

**Files:**

- Create: `tracking/location/LocationSource.kt`, `AndroidLocationMapper.kt`, `FusedLocationSource.kt`, `BoundaryCaptureManager.kt`
- Create: `tracking/service/TrackingExpectationStore.kt`, `TrackingReconciler.kt`, `AlwaysOnTrackingService.kt`, `TrackingBootReceiver.kt`, `TrackingNotificationFactory.kt`
- Modify: `app/src/main/AndroidManifest.xml`, `TrackerApplication.kt`
- Test: `app/src/test/java/com/internal/tracker/tracking/location/BoundaryCaptureManagerTest.kt`
- Test: `app/src/test/java/com/internal/tracker/tracking/service/TrackingReconcilerTest.kt`
- Test: `app/src/androidTest/java/com/internal/tracker/tracking/service/AlwaysOnTrackingServiceTest.kt`

**Interfaces produced:**

```kotlin
interface LocationSource {
    fun startOrdinary(onLocations: (List<RawLocationSample>) -> Unit, onFailure: (Throwable) -> Unit)
    fun stopOrdinary()
    fun startBoundary(request: BoundaryRequest, onResult: (BoundaryResult) -> Unit)
}
data class BoundaryRequest(val requestId: String, val kind: RawSampleKind, val actionElapsedNanos: Long)
sealed interface BoundaryResult { data class Captured(val sample: RawLocationSample) : BoundaryResult; data object Unavailable : BoundaryResult }
enum class TrackingExpectation { NOT_CONFIGURED, EXPECTED, PAUSED_BY_MAINTENANCE }
sealed interface ReconcileAction { data object StartService : ReconcileAction; data object RecordPermissionIncident : ReconcileAction; data object None : ReconcileAction }
```

- [ ] **Step 1: Write failing boundary/reconciler tests.**

```kotlin
@Test fun boundaryCapturesFirstFreshFixOnlyOnce() = runTest { manager.capture(request); fake.emit(sample(request.actionElapsedNanos + 1)); assertIs<BoundaryResult.Captured>(results.single()) }
@Test fun expectedReadyStateStartsService() { assertEquals(StartService, reconciler.action(EXPECTED, readySnapshot)) }
```

- [ ] **Step 2: Run focused JVM tests.** Expected: FAIL because adapter/reconciler are absent.
- [ ] **Step 3: Implement mapper and Fused source.** Map every Android `Location` field exactly; use `has*()` only to choose null. Ordinary request is high accuracy with interval/minimum `10_000L`.
- [ ] **Step 4: Implement boundary manager.** High accuracy, one-second boundary request, first location with elapsed time after action, ten-second timeout, exactly one result, ordinary request remains active.
- [ ] **Step 5: Implement service/receiver.** Start foreground immediately, `START_STICKY`, boot session, health receipt, raw commit then movement. Declare non-exported location FGS and non-exported `BOOT_COMPLETED`/`MY_PACKAGE_REPLACED` receiver. On Android background-start rejection record repair state; do not crash or create fake raw.
- [ ] **Step 6: Add/run integration test asserting one ordinary high-accuracy registration.** Expected: PASS.
- [ ] **Step 7: Commit exact files with message `feat: run always-on high accuracy tracking`.**

## Task 5: Implement storage and protected whole-day cleanup

**Files:**

- Create: `tracking/storage/StoragePolicy.kt`, `StorageProbe.kt`, `ProtectedRawDayResolver.kt`, `RetentionCoordinator.kt`
- Modify: `tracking/database/TrackingDao.kt`
- Test: `app/src/test/java/com/internal/tracker/tracking/storage/StoragePolicyTest.kt`
- Test: `app/src/androidTest/java/com/internal/tracker/tracking/storage/RetentionCoordinatorTest.kt`

- [ ] **Step 1: Write failing tests.**

```kotlin
@Test fun mapsThresholdsExactly() {
    assertEquals(NORMAL, policy.status(500L * 1024 * 1024))
    assertEquals(WARNING, policy.status(499L * 1024 * 1024))
    assertEquals(CRITICAL, policy.status(199L * 1024 * 1024))
}
@Test fun choosesOldestEligibleWholeBusinessDay() = runTest { assertEquals(day("2025-01-01"), coordinator.nextDayToDelete(today, setOf(day("2024-12-31")))) }
```

- [ ] **Step 2: Run storage JVM test.** Expected: FAIL because policy/cleanup are absent.
- [ ] **Step 3: Implement probe/policy/resolver.** Probe free/database/WAL bytes. Phase 2 `ProtectedRawDayResolver` returns `emptySet()`; later Phase 3 protects Trip/queue/Adjustment/Recovery days through the same contract.
- [ ] **Step 4: Implement cleanup.** Delete exactly one eligible local business-day raw range and derived movement references, audit trigger/day/count/bytes, checkpoint WAL, perform bounded incremental vacuum, re-probe, and stop at 200 MiB. Run one-year retention daily, probe on service/app start, every 15 minutes, and after storage write failure.
- [ ] **Step 5: Add one-retry storage-failure test and instrumented fake-probe cleanup test.** Expected: exactly one retry and no physical SET disk filling.
- [ ] **Step 6: Commit exact files with message `feat: retain and clean raw GPS by whole day`.**

## Task 6: Deliver setup/repair UI, notification, and version

**Files:**

- Modify: `tracking/PermissionState.kt`, `MainActivity.kt`, `ui/SetHomeShell.kt`, `res/values/strings.xml`, `app/build.gradle.kts`, `ProjectConfigTest.kt`
- Create: `tracking/permission/AndroidPermissionSnapshotReader.kt`, `TrackingSetupCoordinator.kt`
- Test: `app/src/test/java/com/internal/tracker/tracking/permission/TrackingSetupCoordinatorTest.kt`
- Test: `app/src/androidTest/java/com/internal/tracker/tracking/permission/PermissionOnboardingTest.kt`

- [ ] **Step 1: Write failing setup/version tests.**

```kotlin
@Test fun requestsFineThenNotificationThenBackgroundSettings() { assertEquals(RequestFineLocation, coordinator.next(missingFine)); assertEquals(RequestNotifications, coordinator.next(missingNotifications)); assertEquals(OpenBackgroundLocationSettings, coordinator.next(missingBackground)) }
@Test fun phase2BuildIdentityIs310Code8() { assertEquals("3.1.0", BuildConfig.VERSION_NAME); assertEquals(8, BuildConfig.VERSION_CODE) }
```

- [ ] **Step 2: Run focused tests.** Expected: FAIL because coordinator/version are not Phase 2.
- [ ] **Step 3: Implement setup.** Request fine from visible Activity; request notifications on API 33+; on API 30+ explain and open app settings for background location without looping. Persist `EXPECTED` before service start after precise/background/system Location are ready.
- [ ] **Step 4: Implement Home.** Show tracking/provider/latest raw/today count/storage/version and one large **Thiết lập theo dõi** or **Khắc phục** action. Show neither coordinates nor Device ID nor Trip/Maintenance/email/Stop controls. Notification says **SET đang theo dõi GPS**, has no Stop action, and opens the app.
- [ ] **Step 5: Set `3.1.0 (8)`, add a Compose assertion that Home has no “Dừng theo dõi”, rerun JVM/instrumentation tests.** Expected: PASS.
- [ ] **Step 6: Commit exact files with message `feat: add phase 2 tracking setup status`.**

## Task 7: Verify migration/update and conduct device acceptance

**Files:**

- Create: `app/src/androidTest/java/com/internal/tracker/tracking/database/TrackingDatabaseUpdateRetentionTest.kt`
- Create: `docs/android-set-3.1.0-device-acceptance.md`
- Modify: `README.md`

- [ ] **Step 1: Write failing migration fixture test.**

```kotlin
@Test fun nextSchemaMigrationRetainsRawSequencesValuesAndExpectation() {
    val phase2 = createPhase2DatabaseWithTwoRowsAndExpectedTracking()
    val next = openNextSchemaVersion(phase2)
    assertEquals(listOf(1L, 2L), next.rawSequences())
    assertEquals(EXPECTED, next.trackingExpectation())
}
```

- [ ] **Step 2: Run it.** Expected: FAIL until an explicit non-destructive migration fixture exists.
- [ ] **Step 3: Implement fixture and create checklist.** Checklist rows must record time, device/API, app version/certificate, expected/observed result, and evidence path. Cover onboarding, ten-second request, lock screen, recents swipe, 45-second Location off/on, process replacement, reboot/unlock, in-place update, stationary/moving cadence, 60-second TEMP_STOP, boundary harness, and crash logcat.
- [ ] **Step 4: Run full static verification.** `testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest`, `git diff --check`, and `git status --short`. Expected: tests pass, lint zero errors, APKs assemble, no private untracked artifact.
- [ ] **Step 5: Perform the approved one-time clean installation on `RF8XA015NLV`.** Preserve requested evidence first, do not use `adb pm grant`, complete setup manually, and record only observed facts. Mark unperformed drive/reboot/update rows `PENDING`.
- [ ] **Step 6: If Gradle UTP is network-blocked, run direct AndroidJUnitRunner using the built APKs; record exact test count/duration.** Expected: `OK`.
- [ ] **Step 7: Commit factual checklist evidence only with message `test: record phase 2 device acceptance`.**

## Spec coverage review

| Design requirement | Task |
| --- | --- |
| SQLCipher, WAL, foreign keys, incremental vacuum | 1–2 |
| Exact raw and monotonic gate | 2 |
| TEMP_STOP, speed fallback, Resume | 3 |
| 30-second GPS gap/recovery | 3 |
| Fused high accuracy and boundary burst | 4 |
| Foreground lifecycle/boot/package recovery | 4 |
| 500/200 MiB cleanup | 5 |
| Setup/repair UI, no Stop, `3.1.0 (8)` | 6 |
| Migration/in-place update and physical acceptance | 7 |

## Plan self-review

- Every Phase 2 design section maps to a task; the full 3,153,600-row capacity test and complete API matrix remain Phase 6 gates.
- The plan has no destructive migration, raw-coordinate filtering, or second location provider.
- `RawLocationSample` enters the repository; only `PersistedRawSample` enters movement; health consumes callback receipt time; retention consumes local business days.
