# Tracking Integrity Diagnostics Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Detect 30-second GPS gaps and suspicious trajectory/event sequences without changing route data, send gap-opened/recovered alerts immediately through the existing Gmail SMTP account, and attach detailed diagnostics to scheduled reports.

**Architecture:** `MovementDetector` remains the sole route-event authority. A new `TrackingIntegrityMonitor` observes raw fixes and persisted movement outcomes, delegates pure gap/trajectory/sequence analysis to focused Kotlin classes, and persists incidents/evidence through a separate Room store. Immediate alert workers and the existing scheduled report share multi-attachment SMTP primitives but retain separate delivery state; no backend API or general email outbox is introduced.

**Tech Stack:** Kotlin 2.2.10, Android SDK 36/minSdk 29, Room 2.7.2, WorkManager 2.10.3, Google Play Services Location 21.3.0, JavaMail 1.6.7, coroutines 1.10.2, JUnit 4, AndroidX instrumentation, Gradle 8.13, PowerShell/Pester, ADB.

## Global Constraints

- Keep `PRIORITY_HIGH_ACCURACY` location requests at 10 seconds in every movement mode.
- Open exactly one gap after 30 seconds without a callback; health checks remain 10 seconds and recovery registration is throttled to once per five minutes.
- Keep moving persistence at two minutes and preserve existing `START`, `PERIODIC`, `TEMP_STOP`, and `STOP` behavior.
- Never delete, modify, suppress, or delay a route record because diagnostics considers it suspicious; poor accuracy alone is never an incident.
- Use an in-memory 60-second-before/30-second-after evidence window; do not persist every normal 10-second fix.
- Send one immediate attempt for each `GPS_GAP_OPENED` and `GPS_GAP_RECOVERED` phase; failure falls back to the next scheduled report and never loops every 10 seconds.
- Keep Gmail SMTP and current user-entered sender/password/recipient configuration. SMTP acceptance is not a backend acknowledgement.
- Keep detailed diagnostics out of Status, History, and Settings. Do not add diagnostics UI or change PIN behavior.
- Retain incident summaries for one year; retain successfully reported samples for 30 days and unreported samples for at most one year.
- Do not add a backend API, JSON replacement, map matching, route correction, battery optimization, or a general durable email outbox.
- Release the completed feature as `2.1.0` (`versionCode = 6`) with the existing signing certificate; never overwrite an existing release APK.

## File Structure

New focused production files:

- `diagnostics/DiagnosticModels.kt`: Room entities, enums, evidence samples, finding and bundle models.
- `diagnostics/DiagnosticDao.kt`: persistence contract and Room queries only.
- `diagnostics/DiagnosticRepository.kt`: incident lifecycle, delivery-state updates, report selection, and retention.
- `diagnostics/GpsGapDetector.kt`: pure monotonic 30-second gap state machine.
- `diagnostics/TrajectoryAnomalyDetector.kt`: pure ring-buffer and multi-signal trajectory analysis.
- `diagnostics/EventSequenceValidator.kt`: pure movement transition and `TEMP_STOP` validation.
- `diagnostics/TrackingIntegrityMonitor.kt`: orchestration across detectors, repository, and alert scheduler.
- `diagnostics/DiagnosticCsv.kt`: deterministic summary/evidence CSV encoding.
- `diagnostics/DiagnosticAlertDelivery.kt`: one-phase SMTP attempt and persisted result.
- `diagnostics/DiagnosticAlertWorker.kt`: WorkManager entry point for immediate alerts.
- `diagnostics/WorkManagerDiagnosticAlertScheduler.kt`: unique work naming and input data.
- `mail/DeliveryTelemetry.kt`: minimal scheduled SMTP attempt/success/failure state contract.

Existing files modified together with those units:

- `data/AppDatabase.kt`: schema 4, DAOs, and migration 3-to-4.
- `tracking/TrackingCoordinator.kt`: expose persisted movement outcomes without moving decision ownership.
- `tracking/TrackingPreferences.kt`: scalar callback heartbeat, recovery cause, and delivery telemetry.
- `tracking/TrackingService.kt`: monitor lifecycle, health timer, callback receipt, and forced re-registration.
- `schedule/ScheduleReceiver.kt`: pass explicit recovery causes for reboot/package replacement/time changes.
- `mail/ReportMessageFactory.kt` and `mail/GmailSmtpSender.kt`: multi-attachment messages and report metadata.
- `mail/ReportDelivery.kt`: route-plus-diagnostics selection with failure isolation.
- `report/ReportRun.kt`, `report/ReportWorker.kt`, and `schedule/WorkManagerReportScheduler.kt`: carry the stable scheduled timestamp and clean diagnostics.
- `AppContainer.kt` and `TrackerApplication.kt`: dependency wiring and worker ownership.
- Release scripts/docs: promote and verify `2.1.0 (6)`.

---

### Task 1: Diagnostic Room Schema and Repository

**Files:**
- Create: `app/src/main/java/com/internal/tracker/diagnostics/DiagnosticModels.kt`
- Create: `app/src/main/java/com/internal/tracker/diagnostics/DiagnosticDao.kt`
- Create: `app/src/main/java/com/internal/tracker/diagnostics/DiagnosticRepository.kt`
- Modify: `app/src/main/java/com/internal/tracker/data/AppDatabase.kt`
- Create: `app/src/test/java/com/internal/tracker/diagnostics/DiagnosticRepositoryTest.kt`
- Modify: `app/src/androidTest/java/com/internal/tracker/data/AppDatabaseMigrationTest.kt`

**Interfaces:**
- Produces: `DiagnosticStore`, `DiagnosticRepository`, `DiagnosticIncident`, `DiagnosticSample`, `DiagnosticBundle`.
- Produces: `AppDatabase.MIGRATION_3_4`, `diagnosticDao()`, and schema version 4.
- Consumes: device-local epoch milliseconds; no Android APIs in repository tests.

- [ ] **Step 1: Write failing repository tests**

Cover one open gap per type, recovery of the same UUID, deterministic oldest-first pending selection, sample lookup by incident ID, independent opened/recovered/report states, and retention boundaries. Use fixed values and assert exact rows:

```kotlin
@Test fun recoveringGapUpdatesTheSameIncident() = runTest {
    val store = FakeDiagnosticStore()
    val repository = DiagnosticRepository(store, incidentIds = { "gap-1" })

    val opened = repository.openGap(openedAt = 1_000, condition = DeviceCondition.PROVIDER_SILENT)
    val recovered = repository.recoverGap(
        incidentId = opened.incidentId,
        recoveredAt = 41_000,
        firstCapturedAt = 41_000,
        firstLatitude = 10.0,
        firstLongitude = 106.0,
        evidenceComplete = true,
    )

    assertEquals("gap-1", recovered.incidentId)
    assertEquals(IncidentState.RECOVERED, recovered.state)
    assertEquals(40_000, recovered.recoveredAt!! - recovered.openedAt)
    assertEquals(1, store.incidents.size)
}

```

For cleanup, insert three explicit fixtures through `FakeDiagnosticStore.upsertIncident/insertSamples`: reported before the 30-day boundary, unreported newer than the one-year boundary, and unreported older than one year. After cleanup, assert only the newer unreported sample remains.

- [ ] **Step 2: Run the focused JVM test and verify RED**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.internal.tracker.diagnostics.DiagnosticRepositoryTest" --offline --no-daemon
```

Expected: compilation fails because the diagnostics package and types do not exist.

- [ ] **Step 3: Define the entities and store contract**

Use stable string enums and explicit delivery phases:

```kotlin
enum class IncidentType { GPS_GAP, SUSPECTED_GPS_JUMP, TIMESTAMP_ANOMALY, EVENT_SEQUENCE_ANOMALY, UNRESOLVED_TEMP_STOP }
enum class IncidentState { OPEN, RECOVERED }
enum class ConfidenceBand { LOW, MEDIUM, HIGH }
enum class EvidenceRole { BEFORE, TRIGGER, AFTER }
enum class DeviceCondition { NORMAL, LOCATION_DISABLED, PERMISSION_MISSING, PROVIDER_SILENT, REBOOT, PACKAGE_REPLACED, PROCESS_RECREATED, UNKNOWN }
enum class DiagnosticDeliveryState { NOT_REQUIRED, PENDING, ACCEPTED, FAILED }

@Entity(tableName = "diagnostic_incidents", indices = [Index("openedAt"), Index("reportedAt"), Index("type")])
data class DiagnosticIncident(
    @PrimaryKey val incidentId: String,
    val type: IncidentType,
    val reasonCodes: String,
    val openedAt: Long,
    val recoveredAt: Long?,
    val state: IncidentState,
    val confidenceScore: Int,
    val confidenceBand: ConfidenceBand,
    val deviceCondition: DeviceCondition,
    val evidenceComplete: Boolean,
    val lastCapturedAt: Long?, val lastLatitude: Double?, val lastLongitude: Double?,
    val firstCapturedAt: Long?, val firstLatitude: Double?, val firstLongitude: Double?,
    val openedAlertState: DiagnosticDeliveryState,
    val openedAlertAttempts: Int, val openedAlertSentAt: Long?, val openedAlertError: String?,
    val recoveredAlertState: DiagnosticDeliveryState,
    val recoveredAlertAttempts: Int, val recoveredAlertSentAt: Long?, val recoveredAlertError: String?,
    val reportedAt: Long?,
)

@Entity(
    tableName = "diagnostic_samples",
    primaryKeys = ["incidentId", "sequence"],
    foreignKeys = [ForeignKey(
        entity = DiagnosticIncident::class,
        parentColumns = ["incidentId"], childColumns = ["incidentId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("incidentId")],
)
data class DiagnosticSample(
    val incidentId: String,
    val sequence: Int,
    val role: EvidenceRole,
    val capturedAt: Long,
    val receivedAt: Long,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Double?,
    val speedMetersPerSecond: Double?,
    val derivedDistanceMeters: Double?,
    val derivedSpeedMetersPerSecond: Double?,
    val signalFlags: String,
)

data class DiagnosticBundle(
    val incidents: List<DiagnosticIncident>,
    val samples: List<DiagnosticSample>,
)
```

`DiagnosticStore` exposes `upsertIncident`, `incident`, `openIncident(type)`, `insertSamples`, `pendingForReport(limit)`, `samplesFor(incidentIds)`, `deleteIncidentsBefore`, `deleteReportedSamplesBefore`, and `deleteUnreportedSamplesBefore`. `DiagnosticRepository` owns copy/update semantics and never exposes DAO mutation details to detectors.

- [ ] **Step 4: Add migration 3-to-4 and instrumentation coverage**

Set `@Database(entities = [LocationRecord::class, DiagnosticIncident::class, DiagnosticSample::class], version = 4)` and create both tables plus the exact indices/foreign key in `MIGRATION_3_4`. Extend `AppContainer` migration wiring only in Task 5; this task exposes the migration constant.

```kotlin
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS diagnostic_incidents (
                incidentId TEXT NOT NULL PRIMARY KEY,
                type TEXT NOT NULL, reasonCodes TEXT NOT NULL,
                openedAt INTEGER NOT NULL, recoveredAt INTEGER,
                state TEXT NOT NULL, confidenceScore INTEGER NOT NULL,
                confidenceBand TEXT NOT NULL, deviceCondition TEXT NOT NULL,
                evidenceComplete INTEGER NOT NULL,
                lastCapturedAt INTEGER, lastLatitude REAL, lastLongitude REAL,
                firstCapturedAt INTEGER, firstLatitude REAL, firstLongitude REAL,
                openedAlertState TEXT NOT NULL, openedAlertAttempts INTEGER NOT NULL,
                openedAlertSentAt INTEGER, openedAlertError TEXT,
                recoveredAlertState TEXT NOT NULL, recoveredAlertAttempts INTEGER NOT NULL,
                recoveredAlertSentAt INTEGER, recoveredAlertError TEXT,
                reportedAt INTEGER
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_diagnostic_incidents_openedAt ON diagnostic_incidents (openedAt)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_diagnostic_incidents_reportedAt ON diagnostic_incidents (reportedAt)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_diagnostic_incidents_type ON diagnostic_incidents (type)")
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS diagnostic_samples (
                incidentId TEXT NOT NULL, sequence INTEGER NOT NULL,
                role TEXT NOT NULL, capturedAt INTEGER NOT NULL, receivedAt INTEGER NOT NULL,
                latitude REAL NOT NULL, longitude REAL NOT NULL, accuracy REAL,
                speedMetersPerSecond REAL, derivedDistanceMeters REAL,
                derivedSpeedMetersPerSecond REAL, signalFlags TEXT NOT NULL,
                PRIMARY KEY (incidentId, sequence),
                FOREIGN KEY (incidentId) REFERENCES diagnostic_incidents(incidentId) ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_diagnostic_samples_incidentId ON diagnostic_samples (incidentId)")
    }
}
```

Add a version-3 fixture containing one existing `location_records` row, open through Room with all migrations, then assert the route row remains unchanged and both diagnostic tables accept/query rows. Run compilation now; run the connected migration test in Task 10.

- [ ] **Step 5: Run repository tests GREEN and commit**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.internal.tracker.diagnostics.DiagnosticRepositoryTest" --offline --no-daemon
git add app/src/main/java/com/internal/tracker/diagnostics app/src/main/java/com/internal/tracker/data/AppDatabase.kt app/src/test/java/com/internal/tracker/diagnostics app/src/androidTest/java/com/internal/tracker/data/AppDatabaseMigrationTest.kt
git commit -m "feat: add diagnostic incident storage"
```

Expected: focused JVM tests pass; Android test sources compile.

---

### Task 2: Pure GPS Gap Detector and Persistent Heartbeat

**Files:**
- Create: `app/src/main/java/com/internal/tracker/diagnostics/GpsGapDetector.kt`
- Create: `app/src/test/java/com/internal/tracker/diagnostics/GpsGapDetectorTest.kt`
- Modify: `app/src/main/java/com/internal/tracker/tracking/TrackingPreferences.kt`

**Interfaces:**
- Produces: `GapState`, `GapAction`, and `GpsGapDetector.onStarted/onTick/onCallback`.
- Produces: `TrackingPreferences.lastGpsCallbackAt`, `recoveryCause`, `lastEmailAttemptTime`, `consecutiveEmailFailures`, `lastEmailFailure`.
- Consumes later: elapsed realtime for live thresholds and wall time only for persistence/reporting.

- [ ] **Step 1: Write gap state-machine tests RED**

```kotlin
@Test fun opensAtThirtySecondsAndDoesNotDuplicate() {
    val detector = GpsGapDetector()
    var state = detector.onStarted(nowElapsed = 0, gapAlreadyOpen = false)
    assertTrue(detector.onTick(state, 29_999).actions.isEmpty())

    val opened = detector.onTick(state, 30_000)
    assertEquals(listOf(GapAction.Open), opened.actions)
    state = opened.state
    assertTrue(detector.onTick(state, 40_000).actions.isEmpty())
}

@Test fun recoversOpenGapAndThrottlesRegistration() {
    val detector = GpsGapDetector()
    val open = GapState(lastCallbackElapsed = 0, gapOpen = true, lastRecoveryAttemptElapsed = 30_000)
    assertTrue(detector.onTick(open, 329_999).actions.isEmpty())
    assertEquals(listOf(GapAction.RetryRegistration), detector.onTick(open, 330_000).actions)
    assertEquals(listOf(GapAction.Recover), detector.onCallback(open, 331_000).actions)
}
```

Also prove a wall-clock/timezone change cannot affect the detector because no wall time enters its API.

- [ ] **Step 2: Run focused test RED**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.internal.tracker.diagnostics.GpsGapDetectorTest" --offline --no-daemon
```

Expected: missing `GpsGapDetector` symbols.

- [ ] **Step 3: Implement the minimal pure state machine**

```kotlin
data class GapState(
    val lastCallbackElapsed: Long,
    val gapOpen: Boolean,
    val lastRecoveryAttemptElapsed: Long?,
)
enum class GapAction { Open, Recover, RetryRegistration }
data class GapTransition(val state: GapState, val actions: List<GapAction>)

class GpsGapDetector {
    fun onStarted(nowElapsed: Long, gapAlreadyOpen: Boolean): GapState
    fun onTick(state: GapState, nowElapsed: Long): GapTransition
    fun onCallback(state: GapState, nowElapsed: Long): GapTransition

    companion object {
        const val GAP_THRESHOLD_MILLIS = 30_000L
        const val RECOVERY_RETRY_MILLIS = 300_000L
    }
}
```

`Open` sets `gapOpen = true` and records the first recovery attempt. `RetryRegistration` updates only the recovery-attempt time. `Recover` refreshes the callback timestamp and closes the gap.

- [ ] **Step 4: Add scalar preference fields without coordinates**

Add long/string/int accessors to `TrackingPreferences`; keep `lastLocationTime` unchanged because it means the last persisted route record. Use a stable recovery enum string and default `PROCESS_RECREATED`. Do not add raw GPS arrays to preferences.

```kotlin
var lastGpsCallbackAt: Long
    get() = preferences.getLong("last_gps_callback", 0)
    set(value) = preferences.edit().putLong("last_gps_callback", value).apply()

var recoveryCause: String?
    get() = preferences.getString("recovery_cause", null)
    set(value) = preferences.edit().putString("recovery_cause", value).apply()
```

- [ ] **Step 5: Run tests GREEN and commit**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.internal.tracker.diagnostics.GpsGapDetectorTest" --offline --no-daemon
git add app/src/main/java/com/internal/tracker/diagnostics/GpsGapDetector.kt app/src/main/java/com/internal/tracker/tracking/TrackingPreferences.kt app/src/test/java/com/internal/tracker/diagnostics/GpsGapDetectorTest.kt
git commit -m "feat: detect missing GPS callbacks"
```

---

### Task 3: Multi-Signal Trajectory Anomaly Detector

**Files:**
- Create: `app/src/main/java/com/internal/tracker/diagnostics/TrajectoryAnomalyDetector.kt`
- Create: `app/src/test/java/com/internal/tracker/diagnostics/TrajectoryAnomalyDetectorTest.kt`

**Interfaces:**
- Consumes: `TrackingFix` plus app `receivedAt`.
- Produces: `ObservedFix`, `DiagnosticFinding`, and `TrajectoryAnomalyDetector.onFix(): List<DiagnosticFinding>`.
- Preserves: a bounded ten-fix ring (six prior 10-second samples plus trigger plus three future samples).

- [ ] **Step 1: Write representative RED tests**

Tests must cover consistent 180 km/h travel, isolated jump-and-return, a sustained shifted track, poor accuracy alone, non-increasing timestamps, and stale capture time. Example:

```kotlin
@Test fun isolatedJumpAndReturnProducesOneFindingAfterThreeFutureFixes() {
    val detector = TrajectoryAnomalyDetector()
    normalFixes.take(6).forEach { assertTrue(detector.onFix(it).isEmpty()) }
    assertTrue(detector.onFix(jumpFix).isEmpty())
    assertTrue(detector.onFix(after1).isEmpty())
    assertTrue(detector.onFix(after2).isEmpty())
    val findings = detector.onFix(after3)

    assertEquals(1, findings.size)
    assertEquals(IncidentType.SUSPECTED_GPS_JUMP, findings.single().type)
    assertTrue("SPATIAL_ISOLATION" in findings.single().reasonCodes)
    assertEquals(10, findings.single().samples.size)
}

@Test fun poorAccuracyAloneNeverCreatesAnIncident() {
    val detector = TrajectoryAnomalyDetector()
    val findings = poorAccuracyButContinuous.fold(emptyList<DiagnosticFinding>()) { all, fix ->
        all + detector.onFix(fix)
    }
    assertTrue(findings.isEmpty())
}
```

- [ ] **Step 2: Run focused tests RED**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.internal.tracker.diagnostics.TrajectoryAnomalyDetectorTest" --offline --no-daemon
```

- [ ] **Step 3: Implement bounded evidence and deterministic signals**

Define:

```kotlin
data class ObservedFix(val fix: TrackingFix, val receivedAt: Long)
data class DiagnosticFinding(
    val type: IncidentType,
    val reasonCodes: Set<String>,
    val confidenceScore: Int,
    val confidenceBand: ConfidenceBand,
    val openedAt: Long,
    val recoveredAt: Long?,
    val samples: List<ObservedFix>,
)
```

Implement Haversine distance once in an internal utility. Subtract both accuracy radii before treating displacement as meaningful. Score `SPATIAL_ISOLATION = 3`, `SPEED_DISAGREEMENT = 2`, `VELOCITY_SPIKE = 2`, `DIRECTION_RETURN = 2`, `TIMESTAMP_ORDER = 4`, and `STALE_TIMESTAMP = 2`; accuracy contributes zero. Finalize a jump only when at least two non-accuracy spatial/velocity signals agree after three future fixes. Emit timestamp findings independently for non-increasing capture time or absolute capture/receive drift over 60 seconds. Bands are `LOW = 1..5`, `MEDIUM = 6..7`, `HIGH >= 8`.

Keep only ten observed fixes; after emitting or clearing a candidate, retain the newest seven so the next trigger can still inspect six preceding intervals. A continuing displaced track must become the new local trajectory rather than repeatedly report isolated jumps.

- [ ] **Step 4: Run all detector tests GREEN and commit**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.internal.tracker.diagnostics.TrajectoryAnomalyDetectorTest" --offline --no-daemon
git add app/src/main/java/com/internal/tracker/diagnostics/TrajectoryAnomalyDetector.kt app/src/test/java/com/internal/tracker/diagnostics/TrajectoryAnomalyDetectorTest.kt
git commit -m "feat: flag suspicious GPS trajectories"
```

---

### Task 4: Persisted Movement Outcomes and Event Sequence Validation

**Files:**
- Modify: `app/src/main/java/com/internal/tracker/tracking/TrackingCoordinator.kt`
- Modify: `app/src/test/java/com/internal/tracker/tracking/TrackingCoordinatorTest.kt`
- Create: `app/src/main/java/com/internal/tracker/diagnostics/EventSequenceValidator.kt`
- Create: `app/src/test/java/com/internal/tracker/diagnostics/EventSequenceValidatorTest.kt`

**Interfaces:**
- Produces: `PersistedMovementAction`, `TrackingOutcome`, and `TrackingCoordinator.restore(): MovementState`.
- Consumes: existing `MovementAction`; does not move any decision out of `MovementDetector`.
- Produces: `EventSequenceValidator.onOutcome(outcome): List<DiagnosticFinding>`.

- [ ] **Step 1: Write coordinator outcome tests RED**

```kotlin
@Test fun returnsInsertedAndFinalizedRecordIds() = runTest {
    val store = FakeTrackingStore()
    val coordinator = coordinator(store)
    coordinator.restore(1_000)
    val start = coordinator.onFix(fix(2_000, 2.0), inVehicle = true)
    assertEquals(PersistedMovementAction.Inserted(1, RecordType.START, true), start.actions.single())

    coordinator.onFix(fix(10_000, 0.0), false)
    val stop = coordinator.onFix(fix(130_000, 0.0), false)
    assertEquals(PersistedMovementAction.Finalized(2, RecordType.STOP), stop.actions.single())
}
```

Change existing restore assertions to `coordinator.restore(...).mode` and keep every existing persistence assertion.

- [ ] **Step 2: Run coordinator test RED, then expose outcomes minimally**

```kotlin
sealed interface PersistedMovementAction {
    data class Inserted(val recordId: Long, val type: RecordType, val finalized: Boolean) : PersistedMovementAction
    data class Finalized(val recordId: Long, val type: RecordType) : PersistedMovementAction
}
data class TrackingOutcome(
    val previousState: MovementState,
    val currentState: MovementState,
    val actions: List<PersistedMovementAction>,
)
```

Make `execute()` return persisted outcomes and `onFix()` return `TrackingOutcome`. `restore()` returns the restored `MovementState`. Do not change `MovementDetector` thresholds or actions.

- [ ] **Step 3: Write event validator tests RED**

Construct `TrackingOutcome` values directly and assert normal short/long stop paths are empty. Assert stable reason codes for `DUPLICATE_START`, `ORPHAN_STOP`, `REPEATED_EVENT`, `TEMP_STOP_OVERDUE`, and `RESTORED_CANDIDATE_UNRESOLVED`.

```kotlin
@Test fun normalLongStopIsNotAnomaly() {
    val validator = EventSequenceValidator()
    validator.onOutcome(startOutcome)
    validator.onOutcome(tempStopInsertedOutcome)
    assertTrue(validator.onOutcome(stopFinalizedOutcome).isEmpty())
}
```

- [ ] **Step 4: Implement the pure validator**

Track only route-active state, last persisted type/ID, and candidate timestamp/ID. Use previous/current `MovementState` plus persisted actions. On a callback at least 120 seconds after a candidate, report overdue only if the outcome still contains the same unfinished candidate. Never classify `START -> TEMP_STOP -> START` or `START -> TEMP_STOP -> STOP` as invalid.

```kotlin
fun onOutcome(outcome: TrackingOutcome): List<DiagnosticFinding> = buildList {
    outcome.actions.forEach { action ->
        when (action) {
            is PersistedMovementAction.Inserted -> validateInsert(action, outcome.previousState, this)
            is PersistedMovementAction.Finalized -> validateFinalize(action, outcome.previousState, this)
        }
    }
    validateCandidateDeadline(outcome.currentState, this)
}
```

- [ ] **Step 5: Run both suites GREEN and commit**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.internal.tracker.tracking.TrackingCoordinatorTest" --tests "com.internal.tracker.diagnostics.EventSequenceValidatorTest" --offline --no-daemon
git add app/src/main/java/com/internal/tracker/tracking/TrackingCoordinator.kt app/src/main/java/com/internal/tracker/diagnostics/EventSequenceValidator.kt app/src/test/java/com/internal/tracker/tracking/TrackingCoordinatorTest.kt app/src/test/java/com/internal/tracker/diagnostics/EventSequenceValidatorTest.kt
git commit -m "feat: expose movement outcomes for diagnostics"
```

---

### Task 5: Tracking Integrity Orchestration

**Files:**
- Create: `app/src/main/java/com/internal/tracker/diagnostics/TrackingIntegrityMonitor.kt`
- Create: `app/src/test/java/com/internal/tracker/diagnostics/TrackingIntegrityMonitorTest.kt`
- Modify: `app/src/main/java/com/internal/tracker/AppContainer.kt`

**Interfaces:**
- Consumes: `GpsGapDetector`, `TrajectoryAnomalyDetector`, `EventSequenceValidator`, `DiagnosticRepository`, and `DiagnosticAlertScheduler`.
- Produces: `onStarted`, `onHealthTick`, `onLocationReceived`, `onMovementProcessed`, and `IntegrityDirective.ReRegisterLocation`.
- Provides to Task 6: a container-owned monitor whose methods are serialized by TrackingService's processing mutex.

- [ ] **Step 1: Write orchestration tests RED**

Use fake repository and scheduler. Assert:

- one 30-second tick opens one incident, persists six prior samples, schedules `OPENED`, and returns re-register;
- later ticks neither create nor schedule duplicates;
- callback recovery updates the same UUID, schedules `RECOVERED`, and stores after evidence;
- a detector finding persists but does not schedule immediate email;
- a 30-second-or-longer startup heartbeat gap creates one already-recovered incident with incomplete evidence;
- tracking-disabled startup creates none.

```kotlin
@Test fun gapOpenAndRecoveryShareIncidentId() = runTest {
    val monitor = monitor(ids = { "gap-1" })
    monitor.onStarted(trackingEnabled = true, nowWall = 1_000, nowElapsed = 0, lastCallbackWall = 1_000, condition = DeviceCondition.NORMAL)
    assertEquals(IntegrityDirective.ReRegisterLocation, monitor.onHealthTick(31_000, 30_000, DeviceCondition.PROVIDER_SILENT))
    monitor.onLocationReceived(fix(41_000), receivedAt = 41_000, elapsedAt = 40_000)
    assertEquals(listOf("gap-1:OPENED", "gap-1:RECOVERED"), scheduler.keys)
}
```

- [ ] **Step 2: Run monitor tests RED**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.internal.tracker.diagnostics.TrackingIntegrityMonitorTest" --offline --no-daemon
```

- [ ] **Step 3: Implement monitor lifecycle and evidence conversion**

Define:

```kotlin
enum class DiagnosticAlertPhase { OPENED, RECOVERED }
fun interface DiagnosticAlertScheduler { fun enqueue(incidentId: String, phase: DiagnosticAlertPhase) }
enum class IntegrityDirective { None, ReRegisterLocation }
```

`onLocationReceived` first updates the gap state/recovery, then feeds the trajectory detector. `onMovementProcessed` feeds the sequence validator. Findings become incidents with stable UUIDs and copied `DiagnosticSample` rows. Repository/scheduler exceptions are caught and returned through a sanitized `onError(String)` callback; detector state continues.

At startup, load any open persisted gap before initializing the pure detector. Infer a recovered historical gap only from a positive `lastGpsCallbackAt`, never `lastLocationTime`, and only when the interval is at least 30 seconds. A zero heartbeat on first-ever tracking start creates no historical gap.

- [ ] **Step 4: Wire schema 4 and monitor dependencies in AppContainer**

Add `MIGRATION_3_4`, `database.diagnosticDao()`, repository, detectors, and monitor. Inject a temporary no-op `DiagnosticAlertScheduler` in this task so code compiles; Task 8 replaces it with WorkManager. Keep all UI-exposed container members unchanged.

```kotlin
private val diagnostics = DiagnosticRepository(database.diagnosticDao())
val trackingIntegrityMonitor = TrackingIntegrityMonitor(
    gapDetector = GpsGapDetector(),
    trajectoryDetector = TrajectoryAnomalyDetector(),
    sequenceValidator = EventSequenceValidator(),
    repository = diagnostics,
    alertScheduler = DiagnosticAlertScheduler { _, _ -> },
    onError = { trackingPreferences.lastError = it },
)
```

- [ ] **Step 5: Run focused tests and commit**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.internal.tracker.diagnostics.*" --offline --no-daemon
git add app/src/main/java/com/internal/tracker/diagnostics app/src/main/java/com/internal/tracker/AppContainer.kt app/src/test/java/com/internal/tracker/diagnostics
git commit -m "feat: orchestrate tracking integrity incidents"
```

---

### Task 6: TrackingService Health Loop and Recovery Causes

**Files:**
- Modify: `app/src/main/java/com/internal/tracker/tracking/TrackingService.kt`
- Modify: `app/src/main/java/com/internal/tracker/schedule/ScheduleReceiver.kt`
- Modify: `app/src/main/java/com/internal/tracker/TrackerApplication.kt`
- Modify: `app/src/main/java/com/internal/tracker/AppContainer.kt`
- Modify: `app/src/test/java/com/internal/tracker/schedule/ScheduleReceiverPolicyTest.kt`
- Create: `app/src/test/java/com/internal/tracker/tracking/TrackingServicePolicyTest.kt`

**Interfaces:**
- Consumes: Task 5 monitor and Task 4 `TrackingOutcome`.
- Produces: `RecoveryCause` mapping and a pure `TrackingHealthPolicy.deviceCondition(...)` for JVM tests.
- Preserves: foreground service, `START_STICKY`, Activity Recognition fallback, and high-accuracy 10-second request.

- [ ] **Step 1: Write recovery/health policy tests RED**

Assert exact intent mappings: `BOOT_COMPLETED -> REBOOT`, `MY_PACKAGE_REPLACED -> PACKAGE_REPLACED`, time/timezone changes and app launch -> `PROCESS_RECREATED`. Assert permission missing takes precedence over Location disabled, then provider silent.

- [ ] **Step 2: Run focused policy tests RED**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.internal.tracker.schedule.ScheduleReceiverPolicyTest" --tests "com.internal.tracker.tracking.TrackingServicePolicyTest" --offline --no-daemon
```

- [ ] **Step 3: Pass explicit recovery cause through reconciliation**

Define `enum class RecoveryCause { REBOOT, PACKAGE_REPLACED, TIME_CHANGED, TIMEZONE_CHANGED, APP_LAUNCH, PROCESS_RECREATED }`. Change `ScheduleOwner.reconcileBackgroundWork(cause: RecoveryCause)` and make the receiver map the actual action before calling it. Persist the cause in `TrackingPreferences` before starting the service. Add an atomic `consumeRecoveryCause()` accessor: `TrackingService` consumes it at the beginning of every `ACTION_START`; a newly created tracker passes it to monitor startup, while an already-running service clears it without reinitializing or inferring a gap. App launch calls tracking-only reconciliation with `APP_LAUNCH` and must still not replace scheduled report work.

```kotlin
object RecoveryCausePolicy {
    fun fromAction(action: String?): RecoveryCause = when (action) {
        Intent.ACTION_BOOT_COMPLETED -> RecoveryCause.REBOOT
        Intent.ACTION_MY_PACKAGE_REPLACED -> RecoveryCause.PACKAGE_REPLACED
        Intent.ACTION_TIME_CHANGED -> RecoveryCause.TIME_CHANGED
        Intent.ACTION_TIMEZONE_CHANGED -> RecoveryCause.TIMEZONE_CHANGED
        else -> RecoveryCause.PROCESS_RECREATED
    }
}
```

- [ ] **Step 4: Integrate callbacks and serialized health loop**

In `TrackingService`:

```kotlin
private var healthJob: Job? = null

private fun startHealthChecks() {
    healthJob?.cancel()
    healthJob = scope.launch {
        while (isActive) {
            delay(TrackingService.LOCATION_INTERVAL_MILLIS)
            processingMutex.withLock {
                when (container.trackingIntegrityMonitor.onHealthTick(
                    nowWall = System.currentTimeMillis(),
                    nowElapsed = SystemClock.elapsedRealtime(),
                    condition = currentDeviceCondition(),
                )) {
                    IntegrityDirective.ReRegisterLocation -> registerLocationUpdates(Priority.PRIORITY_HIGH_ACCURACY, force = true)
                    IntegrityDirective.None -> Unit
                }
            }
        }
    }
}
```

Call `monitor.onStarted` after coordinator restore and before registering updates. For each sorted fix, capture `receivedAt`/`elapsedAt`, update `lastGpsCallbackAt`, call `onLocationReceived`, process the coordinator outcome, then call `onMovementProcessed`. Change `registerLocationUpdates(priority, force = false)` so recovery can bypass the existing same-priority early return. Cancel the health job on stop/destroy.

Never start a second service, create a movement event, or reset `startedAt` from the health loop.

- [ ] **Step 5: Run tracking and schedule suites GREEN and commit**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.internal.tracker.tracking.*" --tests "com.internal.tracker.schedule.*" --offline --no-daemon
git add app/src/main/java/com/internal/tracker/tracking app/src/main/java/com/internal/tracker/schedule app/src/main/java/com/internal/tracker/TrackerApplication.kt app/src/main/java/com/internal/tracker/AppContainer.kt app/src/test/java/com/internal/tracker/tracking app/src/test/java/com/internal/tracker/schedule
git commit -m "feat: monitor and recover GPS callback gaps"
```

---

### Task 7: Multi-Attachment Mail and Diagnostic CSV

**Files:**
- Create: `app/src/main/java/com/internal/tracker/diagnostics/DiagnosticCsv.kt`
- Create: `app/src/test/java/com/internal/tracker/diagnostics/DiagnosticCsvTest.kt`
- Modify: `app/src/main/java/com/internal/tracker/mail/ReportMessageFactory.kt`
- Modify: `app/src/main/java/com/internal/tracker/mail/GmailSmtpSender.kt`
- Modify: `app/src/test/java/com/internal/tracker/mail/ReportMessageFactoryTest.kt`
- Create: `app/src/test/java/com/internal/tracker/mail/MailAttachmentTest.kt`

**Interfaces:**
- Produces: `MailAttachment(name, contentType, bytes)` and `ReportMessage.attachments`.
- Produces: `DiagnosticCsv.summary(bundle)` and `DiagnosticCsv.samples(bundle)`.
- Preserves: UTF-8 JavaMail body, TLS/auth classification, and existing route CSV bytes.

- [ ] **Step 1: Write CSV and message-model tests RED**

Assert RFC-4180 escaping, stable ordering by `openedAt, incidentId` and `incidentId, sequence`, exact headers, empty sample output with a header, and no password/PIN/error stack content.

```kotlin
assertEquals(
    "incident_id,type,state,opened_at,recovered_at,duration_ms,reason_codes,confidence_score,confidence_band,device_condition,evidence_complete,opened_alert_state,recovered_alert_state,reported_at\r\n",
    DiagnosticCsv.summary(DiagnosticBundle(emptyList(), emptyList())),
)
```

Change existing factory assertions from `attachmentName/attachment` to a single route `attachments` element.

- [ ] **Step 2: Run export/mail tests RED**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.internal.tracker.diagnostics.DiagnosticCsvTest" --tests "com.internal.tracker.mail.*" --offline --no-daemon
```

- [ ] **Step 3: Implement multi-attachment message primitives**

```kotlin
data class MailAttachment(val name: String, val contentType: String, val bytes: ByteArray)
data class ReportMessage(
    val subject: String,
    val body: String,
    val attachments: List<MailAttachment>,
    val recordIds: List<Long> = emptyList(),
    val incidentIds: List<String> = emptyList(),
    val reportId: String? = null,
)
```

In `GmailSmtpSender`, add one `MimeBodyPart` per attachment using its content type and name. Keep body first. Do not log `PilotConfig` or JavaMail exceptions.

- [ ] **Step 4: Implement deterministic diagnostic encoders**

Use ISO-offset timestamps derived from each incident's stored timezone-independent epoch and device timezone supplied to the encoder. Summary has one row per incident; samples reference `incident_id` and contain role, capture/receive time, coordinates, accuracy, device speed, derived distance/speed, and signal flags. Reuse a small CSV escaping helper rather than duplicating route exporter behavior.

```kotlin
object DiagnosticCsv {
    fun summary(bundle: DiagnosticBundle, zone: ZoneId): String = encodeRows(
        SUMMARY_HEADER,
        bundle.incidents.sortedWith(compareBy(DiagnosticIncident::openedAt, DiagnosticIncident::incidentId)).map { summaryRow(it, zone) },
    )
    fun samples(bundle: DiagnosticBundle, zone: ZoneId): String = encodeRows(
        SAMPLE_HEADER,
        bundle.samples.sortedWith(compareBy(DiagnosticSample::incidentId, DiagnosticSample::sequence)).map { sampleRow(it, zone) },
    )
}
```

- [ ] **Step 5: Run tests GREEN and commit**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.internal.tracker.diagnostics.DiagnosticCsvTest" --tests "com.internal.tracker.mail.*" --offline --no-daemon
git add app/src/main/java/com/internal/tracker/diagnostics/DiagnosticCsv.kt app/src/main/java/com/internal/tracker/mail app/src/test/java/com/internal/tracker/diagnostics/DiagnosticCsvTest.kt app/src/test/java/com/internal/tracker/mail
git commit -m "feat: export diagnostics as email attachments"
```

---

### Task 8: Immediate Gap Alert Worker

**Files:**
- Create: `app/src/main/java/com/internal/tracker/diagnostics/DiagnosticAlertDelivery.kt`
- Create: `app/src/main/java/com/internal/tracker/diagnostics/DiagnosticAlertWorker.kt`
- Create: `app/src/main/java/com/internal/tracker/diagnostics/WorkManagerDiagnosticAlertScheduler.kt`
- Create: `app/src/test/java/com/internal/tracker/diagnostics/DiagnosticAlertDeliveryTest.kt`
- Create: `app/src/test/java/com/internal/tracker/diagnostics/DiagnosticAlertSchedulerPolicyTest.kt`
- Modify: `app/src/main/java/com/internal/tracker/TrackerApplication.kt`
- Modify: `app/src/main/java/com/internal/tracker/AppContainer.kt`

**Interfaces:**
- Produces: `DiagnosticAlertDelivery.deliver(incidentId, phase): DiagnosticAlertOutcome`.
- Produces: unique work key `diagnostic-alert-<incidentId>-<phase>` with `ExistingWorkPolicy.KEEP`.
- Consumes: Task 7 multi-attachment sender and Task 1 repository.

- [ ] **Step 1: Write alert delivery tests RED**

Assert OPENED and RECOVERED subjects share the incident ID; opened includes prior samples, recovered includes completed samples/duration; `Accepted` marks only the requested phase; SMTP failure increments only that phase, stores one sanitized category, and returns without scheduling a retry.

```kotlin
@Test fun networkFailureStaysPendingForScheduledReport() = runTest {
    val outcome = delivery(MailResult.NetworkFailure).deliver("gap-1", DiagnosticAlertPhase.OPENED)
    assertEquals("NETWORK", outcome.publicError)
    assertEquals(DiagnosticDeliveryState.FAILED, repository.incident("gap-1")!!.openedAlertState)
    assertNull(repository.incident("gap-1")!!.reportedAt)
}
```

- [ ] **Step 2: Run alert tests RED**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.internal.tracker.diagnostics.DiagnosticAlert*" --offline --no-daemon
```

- [ ] **Step 3: Implement delivery, worker, and unique scheduler**

`DiagnosticAlertWorker` reads `incident_id` and `phase`, resolves an application owner, calls delivery once, and returns `Result.success()` after the result is persisted. It returns `Result.failure()` only for malformed input or missing owner; SMTP failure must not invoke WorkManager retry.

```kotlin
interface DiagnosticAlertOwner { val diagnosticAlertDelivery: DiagnosticAlertDelivery }
object DiagnosticAlertWork {
    const val KEY_INCIDENT_ID = "incident_id"
    const val KEY_PHASE = "phase"
    fun uniqueName(id: String, phase: DiagnosticAlertPhase) = "diagnostic-alert-$id-${phase.name}"
}
```

- [ ] **Step 4: Replace the Task 5 no-op scheduler in AppContainer**

Expose delivery through `TrackerApplication`, inject `WorkManagerDiagnosticAlertScheduler(appContext)` into the monitor, and keep credentials loaded only at actual send time through `pilotConfig.load()`.

```kotlin
private val diagnosticAlertScheduler = WorkManagerDiagnosticAlertScheduler(appContext)
val diagnosticAlertDelivery = DiagnosticAlertDelivery(
    repository = diagnostics,
    config = pilotConfig::load,
    sender = gmail,
    appVersion = BuildConfig.VERSION_NAME,
)
```

- [ ] **Step 5: Run tests GREEN and commit**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.internal.tracker.diagnostics.DiagnosticAlert*" --offline --no-daemon
git add app/src/main/java/com/internal/tracker/diagnostics app/src/main/java/com/internal/tracker/TrackerApplication.kt app/src/main/java/com/internal/tracker/AppContainer.kt app/src/test/java/com/internal/tracker/diagnostics
git commit -m "feat: send immediate GPS gap alerts"
```

---

### Task 9: Scheduled Diagnostics, Report IDs, Telemetry, and Cleanup

**Files:**
- Create: `app/src/main/java/com/internal/tracker/mail/DeliveryTelemetry.kt`
- Create: `app/src/test/java/com/internal/tracker/mail/ReportIdTest.kt`
- Modify: `app/src/main/java/com/internal/tracker/mail/ReportMessageFactory.kt`
- Modify: `app/src/main/java/com/internal/tracker/mail/ReportDelivery.kt`
- Modify: `app/src/test/java/com/internal/tracker/mail/ReportMessageFactoryTest.kt`
- Modify: `app/src/test/java/com/internal/tracker/mail/ReportDeliveryTest.kt`
- Modify: `app/src/main/java/com/internal/tracker/report/ReportRun.kt`
- Modify: `app/src/main/java/com/internal/tracker/report/ReportWorker.kt`
- Modify: `app/src/test/java/com/internal/tracker/report/ReportRunTest.kt`
- Modify: `app/src/main/java/com/internal/tracker/schedule/WorkManagerReportScheduler.kt`
- Modify: `app/src/main/java/com/internal/tracker/tracking/TrackingPreferences.kt`
- Modify: `app/src/main/java/com/internal/tracker/AppContainer.kt`

**Interfaces:**
- Produces: `ReportId.create(deviceId, scheduledFor, recordIds, incidentIds): String` using SHA-256 hex.
- Produces: `DeliveryTelemetryStore.snapshot/accepted/failed`, implemented by `TrackingPreferences`.
- Changes: `ReportRun.execute(scheduledFor: Long)`, `ReportDelivery.deliverPending(scheduledFor: Long)`.
- Changes: `DeliveryOutcome(sent, diagnosticsSent, remaining, diagnosticsRemaining, publicError)` so route and diagnostic counts remain explicit.
- Consumes: Task 1 diagnostic bundles and Task 7 multi-attachment messages.

- [ ] **Step 1: Write deterministic ID and diagnostics-only delivery tests RED**

Assert sorted equivalent ID sets give the same ID, scheduled timestamp/content changes alter it, route-only behavior remains, diagnostics-only sends, accepted mail marks both selected kinds, failed mail marks neither sent/reported, and diagnostics query/export failure still sends route-only.

```kotlin
@Test fun diagnosticsOnlyRunSendsAndMarksIncluded() = runTest {
    val delivery = delivery(routes = emptyList(), incidents = listOf(incident("gap-1")), sender = AcceptedSender())
    val result = delivery.deliverPending(scheduledFor = 100_000)
    assertEquals(0, result.sent)
    assertEquals(1, result.diagnosticsSent)
    assertNotNull(diagnostics.incident("gap-1")!!.reportedAt)
}
```

- [ ] **Step 2: Run report/mail suites RED**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.internal.tracker.mail.*" --tests "com.internal.tracker.report.*" --offline --no-daemon
```

- [ ] **Step 3: Add deterministic report metadata and telemetry**

```kotlin
data class DeliveryTelemetry(
    val lastAttemptAt: Long,
    val lastSuccessAt: Long,
    val consecutiveFailures: Int,
    val lastFailure: String?,
)
interface DeliveryTelemetryStore {
    fun snapshot(): DeliveryTelemetry
    fun accepted(at: Long)
    fun failed(at: Long, category: String)
}
```

`ReportId` hashes UTF-8 fields separated by newline: device ID, `scheduledFor`, sorted route IDs, and sorted incident IDs. Pass `deviceId.get()` explicitly to `ReportMessageFactory`; never derive identity from the latest route row because a diagnostics-only report has none. The report body includes report ID, scheduled window (`scheduledFor - intervalHours` through `scheduledFor`), counts, earliest/latest capture times when present, app version, previous SMTP success, and consecutive failure count. Never include the password or raw exception.

- [ ] **Step 4: Extend ReportDelivery with failure isolation**

Select route records as before. Obtain diagnostics with `runCatching`; on failure continue route-only and expose `DIAGNOSTICS_READ` only if no more important SMTP error occurs. Build attachments conditionally. On acceptance, mark exactly the selected route IDs and incident IDs and update telemetry. On failure, mark route retrying, leave diagnostics unreported, and update telemetry.

```kotlin
val diagnosticResult = runCatching { diagnostics.pendingBundle(MAX_INCIDENTS) }
val bundle = diagnosticResult.getOrDefault(DiagnosticBundle(emptyList(), emptyList()))
if (routes.isEmpty() && bundle.incidents.isEmpty()) {
    return@withLock DeliveryOutcome(0, 0, 0, 0, diagnosticResult.exceptionOrNull()?.let { "DIAGNOSTICS_READ" })
}
val message = ReportMessageFactory.create(
    config = config(), deviceId = deviceId(), routes = routes, diagnostics = bundle,
    telemetry = telemetry.snapshot(), appVersion = appVersion,
    scheduledFor = scheduledFor, nowMillis = nowMillis(),
)
```

Preserve the 20 MiB cap across the sum of all attachment bytes. Prefer route data, then summary, then as many complete incident evidence groups as fit; never split one incident's samples. Incidents that do not fit remain unreported for the next scheduled run.

- [ ] **Step 5: Carry stable scheduled time through WorkManager**

Use `Data.Builder().putLong(ReportWorker.KEY_SCHEDULED_FOR, scheduledEpochMillis)` when enqueueing. Worker rejects a missing/non-positive value, otherwise calls `reportRun.execute(scheduledFor)`. Update schedule and ReportRun tests to prove `finally` still schedules the next run after cleanup/delivery failure.

```kotlin
val request = OneTimeWorkRequestBuilder<ReportWorker>()
    .setInputData(workDataOf(ReportWorker.KEY_SCHEDULED_FOR to time.toInstant().toEpochMilli()))
    .setInitialDelay(delay, TimeUnit.MILLISECONDS)
    .build()
```

- [ ] **Step 6: Add independent route/diagnostics cleanup**

In the AppContainer cleanup closure, attempt route one-year cleanup and diagnostic cleanup independently, collect the first sanitized failure, and still call delivery. Compute summary boundary from `LocalDate.now(zone).minusYears(1)`, sample boundary from `Instant.now().minus(30, DAYS)`, and pass both exact epoch values to `DiagnosticRepository.cleanup`.

```kotlin
cleanup = {
    val zone = ZoneId.systemDefault()
    val summaryBefore = LocalDate.now(zone).minusYears(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val samplesBefore = Instant.now().minus(30, ChronoUnit.DAYS).toEpochMilli()
    val routeFailure = runCatching { history.deleteOlderThan(LocalDate.now(zone).minusYears(1)) }.exceptionOrNull()
    val diagnosticFailure = runCatching { diagnostics.cleanup(summaryBefore, samplesBefore) }.exceptionOrNull()
    (routeFailure ?: diagnosticFailure)?.let { throw it }
},
```

- [ ] **Step 7: Run all report suites GREEN and commit**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.internal.tracker.mail.*" --tests "com.internal.tracker.report.*" --tests "com.internal.tracker.schedule.*" --offline --no-daemon
git add app/src/main/java/com/internal/tracker/mail app/src/main/java/com/internal/tracker/report app/src/main/java/com/internal/tracker/schedule app/src/main/java/com/internal/tracker/tracking/TrackingPreferences.kt app/src/main/java/com/internal/tracker/AppContainer.kt app/src/test/java/com/internal/tracker/mail app/src/test/java/com/internal/tracker/report app/src/test/java/com/internal/tracker/schedule
git commit -m "feat: include diagnostics in scheduled reports"
```

---

### Task 10: Full Regression and Device-Test Preparation

**Files:**
- Modify: `docs/android-14-device-test-checklist.md`
- Modify: `docs/periodic-gmail-pilot-handover.md`
- Test: all `app/src/test` and `app/src/androidTest` sources.

**Interfaces:**
- Consumes: complete feature implementation.
- Produces: passing JVM/lint/debug builds, migration evidence on a disposable target, and a repeatable Samsung checklist without touching the production-signed installation.

- [ ] **Step 1: Run fresh complete Android verification**

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --offline --no-daemon
```

Expected: exit code 0, zero unit-test failures, lint success, debug APK generated.

- [ ] **Step 2: Run migration instrumentation only on a disposable target**

Start/select an emulator or a device explicitly approved for destructive test installs, set its serial in `ANDROID_SERIAL`, then:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest --offline --no-daemon
```

Expected: migration 3-to-4 preserves route rows and diagnostic entities operate. Do not run this command against the connected Samsung containing the signed 2.0.3 app: installing a debug target would cause a signature conflict or require destructive uninstall. If no disposable target is available, run `:app:assembleDebugAndroidTest` to prove compilation and leave the runtime migration gate open until Task 11's signed in-place update.

- [ ] **Step 3: Add the exact signed-build GPS gap procedure to the checklist**

Document: with tracking enabled and network available, record the current app/service state; disable Location for at least 40 seconds; verify service remains foreground, one OPENED message is produced, and no repeated incident appears at each 10-second check; re-enable Location and verify RECOVERED uses the same incident ID and reports duration plus before/after evidence.

Do not print email credentials or full coordinates in saved test logs; retain only incident ID, timestamps, counts, service state, and redacted email subject evidence.

- [ ] **Step 4: Add network-loss and reboot procedures to the checklist**

Document: disable network before a second controlled gap and confirm SMTP failure does not stop tracking; restore network and trigger/await the scheduled report; verify summary/evidence attachments contain the pending incident; reboot while tracking is enabled, unlock once, and verify foreground service/high-accuracy 10-second request recovery plus one inferred gap; confirm moving route rows still follow the two-minute cadence and normal `TEMP_STOP`/`STOP` creates no anomaly.

- [ ] **Step 5: Update operational documents and commit**

Document exact repeatable checks for OPENED/RECOVERED, shared incident ID, diagnostics attachments, network fallback, reboot, no diagnostics UI, and redaction. Preserve all existing Gmail setup and update instructions.

```powershell
git add docs/android-14-device-test-checklist.md docs/periodic-gmail-pilot-handover.md
git commit -m "docs: add tracking diagnostics device checks"
```

---

### Task 11: Promote and Verify Release 2.1.0

**Files:**
- Modify: `scripts/tests/BuildReleaseCommand.Tests.ps1`
- Modify: `app/build.gradle.kts`
- Modify: `scripts/build-release-apk.ps1`
- Modify: `README.md`
- Modify: `docs/stable-apk-update-runbook.md`
- Modify: `docs/android-14-device-test-checklist.md`

**Interfaces:**
- Produces: signed `dist/tracking-gps-2.1.0.apk`, package `com.internal.tracker`, version code 6, existing certificate fingerprint.
- Preserves: user-entered SMTP credentials, Room data, PIN, settings, tracking state, and update-without-uninstall workflow.

- [ ] **Step 1: Change release command expectations first and verify RED**

Update only `BuildReleaseCommand.Tests.ps1` to require `tracking-gps-2.1.0.apk` and output `2.1.0 (6)`.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "Invoke-Pester '.\scripts\tests\BuildReleaseCommand.Tests.ps1' -EnableExit"
```

Expected: FAIL because production build metadata still says `2.0.3 (5)`.

- [ ] **Step 2: Promote production metadata GREEN**

Set exactly:

```kotlin
versionCode = 6
versionName = "2.1.0"
```

Change `scripts/build-release-apk.ps1` to `-ExpectedVersionCode '6'` and `-ExpectedVersionName '2.1.0'`. Update README/runbook/checklist paths and upgrade source from `2.0.3 (5)` to `2.1.0 (6)`. Rerun the focused Pester file and expect PASS.

- [ ] **Step 3: Run complete release policy verification**

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "Invoke-Pester '.\scripts\tests' -EnableExit"
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --offline --no-daemon
```

Expected: zero failures. Tests that require `TEST_RELEASE_APK` may remain explicitly inconclusive only when that variable is unset.

- [ ] **Step 4: Build and cryptographically verify the signed APK**

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\build-release-apk.ps1
Get-FileHash .\dist\tracking-gps-2.1.0.apk -Algorithm SHA256
```

Also run the repository-selected `aapt dump badging` and `apksigner verify --verbose --print-certs`. Expected: package `com.internal.tracker`, `versionCode='6'`, `versionName='2.1.0'`, and certificate SHA-256 `8F:19:12:A3:4E:D2:CB:9D:DF:88:40:DB:49:A7:69:13:42:51:B3:29:74:84:33:36:78:E2:C6:79:CA:E4:F5:85`.

- [ ] **Step 5: Install in place and verify post-update recovery**

Back up/record pre-update version and service state. Install only with:

```powershell
adb install -r .\dist\tracking-gps-2.1.0.apk
```

Do not uninstall or use downgrade flags. Verify `dumpsys package` reports 2.1.0 (6), existing History/config/PIN/email settings remain, and `MY_PACKAGE_REPLACED` restores active foreground tracking without reboot.

Execute every signed-build procedure prepared in Task 10: one network-available 40-second Location-off/on gap, one network-unavailable gap followed by scheduled fallback, reboot while tracking is enabled, high-accuracy 10-second request recovery, two-minute moving persistence, and normal `TEMP_STOP`/`STOP` behavior. Verify OPENED and RECOVERED messages share one incident ID, scheduled email contains both diagnostic CSV attachments, and the app exposes no diagnostics UI.

- [ ] **Step 6: Commit release metadata and verification documentation**

Record the actual APK SHA-256 and device result in the runbook/checklist without committing the APK, credentials, diagnostic dumps, or coordinates.

```powershell
git add app/build.gradle.kts scripts/build-release-apk.ps1 scripts/tests/BuildReleaseCommand.Tests.ps1 README.md docs/stable-apk-update-runbook.md docs/android-14-device-test-checklist.md
git commit -m "build: release tracking diagnostics 2.1.0"
git status --short
```

Expected final status: only pre-existing ignored/untracked diagnostic or driver directories remain; no source, test, or documentation file is unstaged.
