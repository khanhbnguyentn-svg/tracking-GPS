# Continuous Adaptive GPS Tracking Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ghi nhận GPS bằng foreground service mỗi 10 giây, lưu điểm hành trình mỗi 2 phút và tại các chuyển tiếp bắt đầu/dừng, rồi gửi một CSV tổng hợp theo lịch 6h/12h/24h.

**Architecture:** `TrackingService` chỉ làm Android lifecycle/location/activity adapters; `MovementDetector` và `TrackingCoordinator` giữ logic chuyển trạng thái có thể unit test. Room là nguồn dữ liệu bền vững cho record và ứng viên `TEMP_STOP`; ReportWorker chỉ cleanup, gửi record đã hoàn tất và lên lịch kỳ sau.

**Tech Stack:** Kotlin 2.2, Android SDK 36/minSdk 29, Jetpack Compose Material 3, Room 2.7, WorkManager 2.10, Google Play Services Location 21.3, JUnit 4, kotlinx-coroutines-test.

## Global Constraints

- Làm việc trên nhánh `feature/periodic-email-reports` và giữ tương thích Android 10+ (`minSdk = 29`).
- Quan sát location với requested interval 10 giây; lưu `PERIODIC` mỗi 120 giây.
- Không loại bỏ callback vì timestamp cũ hoặc accuracy lớn; luôn giữ accuracy/timestamp gốc trên record được lưu.
- Dùng `START`, `PERIODIC`, `TEMP_STOP`, `STOP`; ứng viên dừng chưa kết luận có `isFinalized = false` và không được gửi email.
- `TEMP_STOP` được nâng cấp trên cùng row thành `STOP` sau 120 giây đứng yên; không tạo row STOP trùng.
- Giữ CSV, thêm `record_type`; không chuyển sang JSON.
- PIN bắt buộc cho Settings lần đầu mỗi session, mỗi lần dừng tracking, và mọi thao tác xóa.
- History xóa theo bộ lọc Năm/Tháng hoặc xóa tất cả; không có nút “Xóa tháng này”.
- ReportWorker xóa record cũ hơn một năm trước delivery và không tự chụp GPS.
- Mỗi thay đổi production phải theo RED → GREEN → REFACTOR; không gộp nhiều hành vi vào một test.

---

## File map

- `history/LocationRecord.kt`: schema event type/finalization.
- `history/LocationRecordDao.kt`: queries gửi, restore candidate, lọc và xóa.
- `history/LocationHistoryRepository.kt`: API persistence có múi giờ và event lifecycle.
- `data/AppDatabase.kt`: Room v3 và migration 2→3.
- `tracking/MovementDetector.kt`: state machine thuần Kotlin.
- `tracking/TrackingCoordinator.kt`: thực thi detector actions qua repository theo thứ tự.
- `tracking/TrackingService.kt`: foreground service, Fused Location callbacks và notification.
- `tracking/VehicleActivityMonitor.kt`: đăng ký Activity Transition API.
- `tracking/VehicleActivityReceiver.kt`: chuyển transition broadcast vào service.
- `report/ReportRun.kt`: cleanup → delivery → schedule, không capture.
- `AppContainer.kt`: composition root và start/stop/reconcile tracking.
- `ui/HistoryFilter.kt`: tính khoảng tháng/năm và delete labels.
- `ui/AppUiPolicy.kt`: policy PIN/session có thể unit test.
- `ui/StatusUiModel.kt`: các row được Status render, chủ động loại Device ID.
- `ui/TrackerApp.kt`: dialogs, permission, History controls và screens.

---

### Task 1: Event schema, finalized delivery queries, retention and migration

**Files:**
- Modify: `app/src/main/java/com/internal/tracker/history/LocationRecord.kt`
- Modify: `app/src/main/java/com/internal/tracker/history/LocationRecordDao.kt`
- Modify: `app/src/main/java/com/internal/tracker/history/LocationHistoryRepository.kt`
- Modify: `app/src/main/java/com/internal/tracker/data/AppDatabase.kt`
- Modify: `app/src/test/java/com/internal/tracker/history/LocationHistoryRepositoryTest.kt`
- Modify: `app/src/test/java/com/internal/tracker/mail/ReportDeliveryTest.kt`
- Create: `app/src/androidTest/java/com/internal/tracker/data/AppDatabaseMigrationTest.kt`
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

**Interfaces:**
- Produces: `RecordType`, `LocationHistoryRepository.capture(..., recordType, isFinalized)`, `finalizeStopCandidate(id, type)`, `activeStopCandidate(since)`, `latestSince(since)`, `deleteOlderThan(date)`, `deleteBetween(from, until)`, `deleteAll()`, `observeOldestCapturedAt()`.
- Consumes: existing `LocationRecordStore`, Room database v2, `CapturedLocation`.

- [ ] **Step 1: Write repository tests that fail for event/finalization and retention APIs**

Add focused tests and extend the fake store with wished-for methods:

```kotlin
@Test fun unfinishedStopIsNotEligibleForDelivery() = runTest {
    val repository = LocationHistoryRepository(FakeLocationRecordStore())
    repository.capture(sample(), 82, 0, "001", "AND-1", RecordType.TEMP_STOP, false)
    assertEquals(emptyList<LocationRecord>(), repository.unsent(100))
}

@Test fun finalizingCandidateChangesSameRowToStop() = runTest {
    val repository = LocationHistoryRepository(FakeLocationRecordStore())
    val id = repository.capture(sample(), 82, 0, "001", "AND-1", RecordType.TEMP_STOP, false)
    repository.finalizeStopCandidate(id, RecordType.STOP)
    assertEquals(RecordType.STOP, repository.get(id)!!.recordType)
    assertEquals(true, repository.get(id)!!.isFinalized)
}

@Test fun deleteOlderThanUsesStartOfRequestedDate() = runTest {
    val zone = ZoneId.of("Asia/Bangkok")
    val store = FakeLocationRecordStore()
    val repository = LocationHistoryRepository(store) { zone }
    repository.deleteOlderThan(LocalDate.of(2026, 1, 1))
    assertEquals(LocalDate.of(2026, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli(), store.lastDeleteBefore)
}
```

- [ ] **Step 2: Run the focused repository test and verify RED**

Run: `./gradlew.bat testDebugUnitTest --tests "com.internal.tracker.history.LocationHistoryRepositoryTest" --no-daemon`

Expected: compilation fails because `RecordType`, capture parameters and lifecycle/delete methods do not exist.

- [ ] **Step 3: Add the minimal model, DAO and repository implementation**

Use these exact model defaults and store signatures:

```kotlin
enum class RecordType { START, PERIODIC, TEMP_STOP, STOP }

data class LocationRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deviceNumber: String,
    val deviceId: String,
    val capturedAt: Long,
    val timezone: String,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Double?,
    val batteryPercent: Int?,
    val trackedDurationMillis: Long,
    val source: RecordSource = RecordSource.CURRENT,
    val state: DeliveryState = DeliveryState.PENDING,
    val attemptCount: Int = 0,
    val lastError: String? = null,
    val sentAt: Long? = null,
    val recordType: RecordType = RecordType.PERIODIC,
    val isFinalized: Boolean = true,
)

interface LocationRecordStore {
    // existing methods
    suspend fun activeStopCandidate(since: Long): LocationRecord?
    suspend fun latestSince(since: Long): LocationRecord?
    fun observeOldestCapturedAt(): Flow<Long?>
    suspend fun deleteOlderThan(beforeMillis: Long)
    suspend fun deleteBetween(from: Long, until: Long)
    suspend fun deleteAll()
    suspend fun finalizeStopCandidate(id: Long, recordType: RecordType)
}
```

Use DAO queries:

```kotlin
@Query("SELECT * FROM location_records WHERE state != 'SENT' AND isFinalized = 1 ORDER BY capturedAt, id LIMIT :limit")
override suspend fun unsent(limit: Int): List<LocationRecord>

@Query("SELECT * FROM location_records WHERE recordType = 'TEMP_STOP' AND isFinalized = 0 AND capturedAt >= :since ORDER BY capturedAt DESC, id DESC LIMIT 1")
override suspend fun activeStopCandidate(since: Long): LocationRecord?

@Query("UPDATE location_records SET recordType = :recordType, isFinalized = 1 WHERE id = :id AND isFinalized = 0")
override suspend fun finalizeStopCandidate(id: Long, recordType: RecordType)

@Query("DELETE FROM location_records WHERE capturedAt < :beforeMillis")
override suspend fun deleteOlderThan(beforeMillis: Long)
```

Repository constructor becomes `LocationHistoryRepository(store, zoneId: () -> ZoneId = ZoneId::systemDefault)` and converts `LocalDate` at start-of-day. Update both fake stores in `LocationHistoryRepositoryTest` and `ReportDeliveryTest` to implement the new methods; their `unsent()` implementations must filter `isFinalized` exactly like Room.

- [ ] **Step 4: Run repository tests and verify GREEN**

Run the Step 2 command.

Expected: all `LocationHistoryRepositoryTest` tests pass.

- [ ] **Step 5: Write a failing Room migration instrumentation test**

Add AndroidX Test aliases `androidx-test-core = 1.6.1` and `androidx-test-junit = 1.2.1`, then use `MigrationTestHelper` to create v2, insert one row, migrate to v3 and assert:

```kotlin
assertEquals("PERIODIC", cursor.getString(cursor.getColumnIndexOrThrow("recordType")))
assertEquals(1, cursor.getInt(cursor.getColumnIndexOrThrow("isFinalized")))
```

- [ ] **Step 6: Implement and compile Room migration v2→v3**

Set database version to 3 and add:

```kotlin
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE location_records ADD COLUMN recordType TEXT NOT NULL DEFAULT 'PERIODIC'")
        db.execSQL("ALTER TABLE location_records ADD COLUMN isFinalized INTEGER NOT NULL DEFAULT 1")
    }
}
```

Register both migrations in `AppContainer`. Run `./gradlew.bat assembleDebugAndroidTest --no-daemon`; if an emulator is available, also run `./gradlew.bat connectedDebugAndroidTest --no-daemon` and confirm the migration test passes.

- [ ] **Step 7: Commit Task 1**

```powershell
git add app/src/main/java/com/internal/tracker/history app/src/main/java/com/internal/tracker/data/AppDatabase.kt app/src/test/java/com/internal/tracker/history app/src/androidTest gradle/libs.versions.toml app/build.gradle.kts
git commit -m "feat: persist finalized tracking events"
```

---

### Task 2: MovementDetector state machine

**Files:**
- Create: `app/src/main/java/com/internal/tracker/tracking/MovementDetector.kt`
- Create: `app/src/test/java/com/internal/tracker/tracking/MovementDetectorTest.kt`

**Interfaces:**
- Consumes: `RecordType` from Task 1.
- Produces: `TrackingFix`, `MovementMode`, `MovementState`, `MovementAction`, `MovementTransition`, `MovementDetector.onFix()`, `onTrackingStopped()`, `attachCandidateId()`.

- [ ] **Step 1: Write RED tests for START and PERIODIC**

Use deterministic fixes with `speedMetersPerSecond` and assert:

```kotlin
val first = detector.onFix(MovementState(), fix(at = 0, speed = 2.0), inVehicle = false)
val second = detector.onFix(first.state, fix(at = 10_000, speed = 2.0), inVehicle = false)
assertEquals(listOf(RecordType.START), second.actions.filterIsInstance<MovementAction.Insert>().map { it.type })

val early = detector.onFix(second.state, fix(at = 129_999, speed = 10.0), false)
assertTrue(early.actions.isEmpty())
val due = detector.onFix(early.state, fix(at = 130_000, speed = 10.0), false)
assertEquals(RecordType.PERIODIC, (due.actions.single() as MovementAction.Insert).type)
```

Also assert `inVehicle = true` creates START from IDLE with one fix.

- [ ] **Step 2: Run detector tests and verify RED**

Run: `./gradlew.bat testDebugUnitTest --tests "com.internal.tracker.tracking.MovementDetectorTest" --no-daemon`

Expected: compilation fails because detector types do not exist.

- [ ] **Step 3: Implement minimal START/PERIODIC transitions**

Define constants exactly:

```kotlin
const val PERIODIC_MILLIS = 120_000L
const val MOVING_SPEED_MPS = 5.0 / 3.6
const val STOPPED_SPEED_MPS = 3.0 / 3.6
const val STOP_RADIUS_METERS = 30.0
```

`TrackingFix` includes latitude, longitude, accuracy, capturedAt, timezone and nullable speed. Preserve every value without an accuracy/age rejection branch.

Define the transition API exactly so later tasks use the same names:

```kotlin
data class TrackingFix(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Double?,
    val capturedAt: Long,
    val timezone: String,
    val speedMetersPerSecond: Double?,
)

enum class MovementMode { IDLE, MOVING, STOP_CANDIDATE }
data class StopCandidate(val recordId: Long?, val fix: TrackingFix)
data class MovementState(
    val mode: MovementMode = MovementMode.IDLE,
    val consecutiveMovingFixes: Int = 0,
    val lastObservedFix: TrackingFix? = null,
    val lastStoredAt: Long? = null,
    val stopCandidate: StopCandidate? = null,
)

sealed interface MovementAction {
    data class Insert(val type: RecordType, val fix: TrackingFix, val finalized: Boolean) : MovementAction
    data class FinalizeCandidate(val recordId: Long, val type: RecordType) : MovementAction
}

data class MovementTransition(val state: MovementState, val actions: List<MovementAction>)
```

- [ ] **Step 4: Verify START/PERIODIC tests GREEN**

Run the Step 2 command and confirm the focused tests pass.

- [ ] **Step 5: Add RED tests for TEMP_STOP lifecycle and stale/inaccurate input**

Cover these exact assertions:

```kotlin
assertEquals(RecordType.TEMP_STOP, beginStopInsert.type)
assertFalse(beginStopInsert.finalized)
assertEquals(RecordType.TEMP_STOP, resumeBeforeTwoMinutes.type)
assertEquals(RecordType.STOP, remainStoppedForTwoMinutes.type)
assertEquals(candidateId, remainStoppedForTwoMinutes.recordId)
assertEquals(1, allActions.count { it is MovementAction.FinalizeCandidate })
assertEquals(9999.0, storedAction.fix.accuracy)
assertEquals(oldTimestamp, storedAction.fix.capturedAt)
```

- [ ] **Step 6: Implement stop candidate transitions and haversine distance**

Use one `Insert(TEMP_STOP, finalized = false)` on entry. `attachCandidateId(state, id)` stores the inserted id. Resume before 120 seconds returns `FinalizeCandidate(id, TEMP_STOP)`; staying within 30 m and below 3 km/h for 120 seconds returns `FinalizeCandidate(id, STOP)`. Both transitions clear the candidate; only STOP changes mode to IDLE.

- [ ] **Step 7: Run all detector tests and refactor while green**

Run the Step 2 command. Keep geographic distance calculation private and remove duplicate transition code without changing thresholds.

- [ ] **Step 8: Commit Task 2**

```powershell
git add app/src/main/java/com/internal/tracker/tracking/MovementDetector.kt app/src/test/java/com/internal/tracker/tracking/MovementDetectorTest.kt
git commit -m "feat: detect adaptive movement events"
```

---

### Task 3: TrackingCoordinator persistence and recovery

**Files:**
- Create: `app/src/main/java/com/internal/tracker/tracking/TrackingCoordinator.kt`
- Create: `app/src/test/java/com/internal/tracker/tracking/TrackingCoordinatorTest.kt`

**Interfaces:**
- Consumes: Task 1 repository APIs and Task 2 detector APIs.
- Produces: `TrackingCoordinator.restore(startedAt)`, `onFix(fix, inVehicle)`, `stop()`.

- [ ] **Step 1: Write failing coordinator tests**

Use a fake `TrackingEventRepository` adapter and assert observable rows, not mock call counts:

```kotlin
coordinator.restore(startedAt = 1_000)
coordinator.onFix(fix(2_000, 2.0), false)
coordinator.onFix(fix(12_000, 2.0), false)
assertEquals(listOf(RecordType.START), store.rows.map { it.recordType })

coordinator.onFix(stoppedFix(20_000), false)
assertFalse(store.rows.last().isFinalized)
coordinator.onFix(stoppedFix(140_000), false)
assertEquals(RecordType.STOP, store.rows.last().recordType)
assertTrue(store.rows.last().isFinalized)
```

Add recovery tests where the fake contains an unfinished TEMP_STOP after `startedAt`; `restore()` must rebuild `STOP_CANDIDATE` with the same row id and anchor.

- [ ] **Step 2: Run coordinator tests and verify RED**

Run: `./gradlew.bat testDebugUnitTest --tests "com.internal.tracker.tracking.TrackingCoordinatorTest" --no-daemon`

Expected: compilation fails because `TrackingCoordinator` does not exist.

- [ ] **Step 3: Implement coordinator with a serial Mutex**

Constructor:

```kotlin
class TrackingCoordinator(
    private val history: LocationHistoryRepository,
    private val detector: MovementDetector,
    private val persist: suspend (TrackingFix, RecordType, Boolean) -> Long,
    private val onPersisted: (TrackingFix) -> Unit,
)
```

`restore(startedAt)` uses `activeStopCandidate(startedAt)` and `latestSince(startedAt)` to rebuild state. `onFix` runs inside `Mutex.withLock`, executes Insert actions in order, calls `attachCandidateId` after inserting an unfinished TEMP_STOP, then executes finalization actions and returns the resulting `MovementMode`. `stop()` finalizes an open candidate as TEMP_STOP.

- [ ] **Step 4: Run coordinator and history tests GREEN**

Run: `./gradlew.bat testDebugUnitTest --tests "com.internal.tracker.tracking.*" --tests "com.internal.tracker.history.*" --no-daemon`

- [ ] **Step 5: Commit Task 3**

```powershell
git add app/src/main/java/com/internal/tracker/tracking/TrackingCoordinator.kt app/src/test/java/com/internal/tracker/tracking/TrackingCoordinatorTest.kt
git commit -m "feat: persist and recover tracking state"
```

---

### Task 4: Foreground service and optional Activity Recognition

**Files:**
- Create: `app/src/main/java/com/internal/tracker/tracking/TrackingService.kt`
- Create: `app/src/main/java/com/internal/tracker/tracking/VehicleActivityMonitor.kt`
- Create: `app/src/main/java/com/internal/tracker/tracking/VehicleActivityReceiver.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/internal/tracker/AppContainer.kt`
- Modify: `app/src/main/java/com/internal/tracker/TrackerApplication.kt`
- Modify: `app/src/main/java/com/internal/tracker/schedule/ScheduleReceiver.kt`
- Modify: `app/src/test/java/com/internal/tracker/schedule/ScheduleReceiverPolicyTest.kt`

**Interfaces:**
- Consumes: `TrackingCoordinator`, `TrackingPreferences.enabled/startedAt`, battery/config/device providers.
- Produces: `AppContainer.startTracking()`, `stopTracking()`, `reconcileTracking()`, `persistTrackingEvent()`, and service actions `START`, `STOP`, `VEHICLE_ENTER`, `VEHICLE_EXIT`.

- [ ] **Step 1: Extend receiver policy test RED for tracking reconciliation**

Change `ScheduleOwner` to expose `reconcileBackgroundWork()`. Introduce `ReconcileAction { TRACKING, SCHEDULE }` and drive the receiver through this pure policy:

```kotlin
@Test fun enabledTrackingRestoresServiceAndSchedule() {
    assertEquals(
        setOf(ReconcileAction.TRACKING, ReconcileAction.SCHEDULE),
        ScheduleReceiverPolicy.actions(trackingEnabled = true),
    )
}

@Test fun disabledTrackingOnlyReconcilesSchedule() {
    assertEquals(setOf(ReconcileAction.SCHEDULE), ScheduleReceiverPolicy.actions(false))
}
```

- [ ] **Step 2: Run schedule tests and verify RED**

Run: `./gradlew.bat testDebugUnitTest --tests "com.internal.tracker.schedule.*" --no-daemon`

- [ ] **Step 3: Add service/receiver declarations and compile minimal adapters**

Manifest additions:

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />
<uses-permission android:name="android.permission.ACTIVITY_RECOGNITION" />
<service android:name=".tracking.TrackingService" android:exported="false" android:foregroundServiceType="location" />
<receiver android:name=".tracking.VehicleActivityReceiver" android:exported="false" />
```

Build location requests with interval and minimum interval `10_000L`. Use `Priority.PRIORITY_BALANCED_POWER_ACCURACY` in `IDLE`, and `Priority.PRIORITY_HIGH_ACCURACY` in `MOVING` or `STOP_CANDIDATE`. Because `TrackingCoordinator.onFix()` returns `MovementMode`, re-register only when the required priority changes. Convert every callback into `TrackingFix` without age/accuracy filtering and process it on `Dispatchers.IO`.

- [ ] **Step 4: Implement Activity Transition registration with fallback**

Register ENTER/EXIT transitions for `DetectedActivity.IN_VEHICLE`. Catch missing permission/API failures, set a public error code, and leave location updates active. `VehicleActivityReceiver` parses `ActivityTransitionResult` and forwards enter/exit to the already-enabled service.

- [ ] **Step 5: Wire AppContainer lifecycle and recovery**

`startTracking()` sets a new `startedAt` only when transitioning false→true, enables preferences, starts service, and reconciles schedule. `stopTracking()` asks coordinator to finalize an open candidate, disables preferences, stops service, and cancels/reconciles schedule. `reconcileBackgroundWork()` calls both `reconcileTracking()` and `reconcileSchedule()`.

- [ ] **Step 6: Compile and run schedule tests GREEN**

Run: `./gradlew.bat testDebugUnitTest --tests "com.internal.tracker.schedule.*" assembleDebug --no-daemon`

Expected: tests pass and debug APK compiles with manifest/service declarations.

- [ ] **Step 7: Commit Task 4**

```powershell
git add app/src/main/AndroidManifest.xml app/src/main/java/com/internal/tracker/tracking app/src/main/java/com/internal/tracker/AppContainer.kt app/src/main/java/com/internal/tracker/TrackerApplication.kt app/src/main/java/com/internal/tracker/schedule app/src/test/java/com/internal/tracker/schedule
git commit -m "feat: run adaptive tracking foreground service"
```

---

### Task 5: ReportRun cleanup-only reporting and CSV event type

**Files:**
- Modify: `app/src/main/java/com/internal/tracker/report/ReportRun.kt`
- Modify: `app/src/main/java/com/internal/tracker/AppContainer.kt`
- Modify: `app/src/main/java/com/internal/tracker/export/LocationCsv.kt`
- Modify: `app/src/test/java/com/internal/tracker/report/ReportRunTest.kt`
- Modify: `app/src/test/java/com/internal/tracker/export/LocationCsvTest.kt`
- Modify: `app/src/test/java/com/internal/tracker/mail/ReportDeliveryTest.kt`

**Interfaces:**
- Consumes: finalized-only `unsent`, `deleteOlderThan(LocalDate)`, existing `ReportDelivery`.
- Produces: `ReportRun(cleanup, deliver, scheduleNext)` and `ReportRunResult(sent, error)`.

- [ ] **Step 1: Rewrite ReportRun tests RED**

Replace capture tests with:

```kotlin
@Test fun cleanupRunsBeforeDeliveryAndSchedule() = runTest {
    val events = mutableListOf<String>()
    val run = ReportRun(
        cleanup = { events += "cleanup" },
        deliver = { events += "mail"; DeliveryOutcome(3, 0, null) },
        scheduleNext = { events += "schedule" },
    )
    assertEquals(3, run.execute().sent)
    assertEquals(listOf("cleanup", "mail", "schedule"), events)
}
```

Add a test where cleanup throws but delivery still executes, and one where delivery throws but schedule still executes.

- [ ] **Step 2: Run ReportRun tests and verify RED**

Run: `./gradlew.bat testDebugUnitTest --tests "com.internal.tracker.report.ReportRunTest" --no-daemon`

- [ ] **Step 3: Implement cleanup → delivery → finally schedule**

Remove `LocationSnapshotProvider`, battery and backup dependencies from ReportRun. Run cleanup with `runCatching`; delivery remains attempted. Prefer delivery error over cleanup error in the public result.

Configure cleanup as:

```kotlin
cleanup = { history.deleteOlderThan(LocalDate.now(ZoneId.systemDefault()).minusYears(1)) }
```

- [ ] **Step 4: Add RED CSV test for record_type**

Assert the exact header ends with `,delivery_state,record_type` and a STOP record emits `,pending,STOP\r\n`.

- [ ] **Step 5: Add `record_type` to CSV and verify mail tests**

Append `record.recordType.name` after delivery state. Extend mail fakes so unfinished rows are excluded; assert one message contains only finalized records and successful SMTP marks only those ids SENT.

Run: `./gradlew.bat testDebugUnitTest --tests "com.internal.tracker.report.*" --tests "com.internal.tracker.export.*" --tests "com.internal.tracker.mail.*" --no-daemon`

- [ ] **Step 6: Commit Task 5**

```powershell
git add app/src/main/java/com/internal/tracker/report/ReportRun.kt app/src/main/java/com/internal/tracker/AppContainer.kt app/src/main/java/com/internal/tracker/export/LocationCsv.kt app/src/test/java/com/internal/tracker/report app/src/test/java/com/internal/tracker/export app/src/test/java/com/internal/tracker/mail
git commit -m "feat: report finalized tracking backlog"
```

---

### Task 6: PIN and History filter policies

**Files:**
- Modify: `app/src/main/java/com/internal/tracker/ui/AppUiPolicy.kt`
- Create: `app/src/main/java/com/internal/tracker/ui/HistoryFilter.kt`
- Modify: `app/src/test/java/com/internal/tracker/ui/AppUiPolicyTest.kt`
- Create: `app/src/test/java/com/internal/tracker/ui/HistoryFilterTest.kt`

**Interfaces:**
- Produces: `ProtectedAction`, `AppUiPolicy.requiresPin()`, `HistoryFilter`, `HistoryTimeRange`, `normalizeMonthSelection()`, `deleteConfirmationLabel()`.
- Consumes: Java Time `YearMonth`, `LocalDate`, `ZoneId`.

- [ ] **Step 1: Write PIN policy tests RED**

```kotlin
assertTrue(AppUiPolicy.requiresPin(ProtectedAction.OPEN_SETTINGS, settingsUnlocked = false))
assertFalse(AppUiPolicy.requiresPin(ProtectedAction.OPEN_SETTINGS, settingsUnlocked = true))
assertTrue(AppUiPolicy.requiresPin(ProtectedAction.STOP_TRACKING, settingsUnlocked = true))
assertTrue(AppUiPolicy.requiresPin(ProtectedAction.DELETE_FILTERED, settingsUnlocked = true))
assertTrue(AppUiPolicy.requiresPin(ProtectedAction.DELETE_ALL, settingsUnlocked = true))
```

- [ ] **Step 2: Write History filter/delete tests RED**

Test all/year/month ranges at `ZoneId.of("Asia/Bangkok")`, including December→January. Assert month selection with no year becomes current year. Assert filtered delete is disabled for all/all, enabled for a selected year, and labels are exactly `Xóa dữ liệu tháng 08/2026?` and `Xóa toàn bộ dữ liệu năm 2026?`.

- [ ] **Step 3: Run UI policy tests and verify RED**

Run: `./gradlew.bat testDebugUnitTest --tests "com.internal.tracker.ui.*" --no-daemon`

- [ ] **Step 4: Implement minimal pure policies**

Use a half-open range:

```kotlin
data class HistoryTimeRange(val from: Long, val until: Long)
data class HistoryFilter(val year: Int? = null, val month: Int? = null) {
    fun range(zone: ZoneId): HistoryTimeRange {
        if (year == null) return HistoryTimeRange(0L, Long.MAX_VALUE)
        val start = if (month == null) LocalDate.of(year, 1, 1) else YearMonth.of(year, month).atDay(1)
        val end = if (month == null) start.plusYears(1) else start.plusMonths(1)
        return HistoryTimeRange(
            start.atStartOfDay(zone).toInstant().toEpochMilli(),
            end.atStartOfDay(zone).toInstant().toEpochMilli(),
        )
    }
    val canDeleteFiltered: Boolean get() = year != null
}
```

`normalizeMonthSelection(month, selectedYear, currentYear)` returns `HistoryFilter(currentYear, month)` if month is non-null and year was null.

- [ ] **Step 5: Run UI policy tests GREEN and commit**

Run the Step 3 command, then:

```powershell
git add app/src/main/java/com/internal/tracker/ui/AppUiPolicy.kt app/src/main/java/com/internal/tracker/ui/HistoryFilter.kt app/src/test/java/com/internal/tracker/ui
git commit -m "feat: define protected actions and history ranges"
```

---

### Task 7: Compose UI integration

**Files:**
- Modify: `app/src/main/java/com/internal/tracker/ui/TrackerApp.kt`
- Create: `app/src/main/java/com/internal/tracker/ui/StatusUiModel.kt`
- Create: `app/src/test/java/com/internal/tracker/ui/StatusUiModelTest.kt`

**Interfaces:**
- Consumes: Task 4 AppContainer start/stop APIs, Task 6 policies/ranges, repository delete/oldest APIs.
- Produces: PIN dialog workflow, optional Activity Recognition permission launcher, History dropdowns/delete dialogs.

- [ ] **Step 1: Add RED behavior tests for Status rows**

Test the real model consumed by Compose rather than grepping source text:

```kotlin
val model = StatusUiModel.create(
    tracking = true,
    deviceNumber = "001",
    lastLocationTime = 1_000,
    lastSendTime = 2_000,
    nextRunTime = 3_000,
)
assertEquals(listOf("Trạng thái", "Thiết bị", "GPS cuối", "Email cuối", "Kỳ gửi dự kiến"), model.rows.map { it.label })
assertFalse(model.rows.any { it.label == "Device ID" })
```

- [ ] **Step 2: Run StatusUiModelTest and verify RED**

Run: `./gradlew.bat testDebugUnitTest --tests "com.internal.tracker.ui.StatusUiModelTest" --no-daemon`

- [ ] **Step 3: Implement reusable PIN verification dialog and Settings session unlock**

Keep cold-start `PinScreen`. Add `settingsUnlocked` with `rememberSaveable`, intercept Settings tab selection, and navigate only after `adminPin.verify(pin)`. Stop tracking always opens a fresh PIN dialog and calls `container.stopTracking()` only on success.

- [ ] **Step 4: Implement optional Activity Recognition permission UI**

On Android 10+, when location is ready and activity permission is absent, show explanatory text plus a separate launcher for `Manifest.permission.ACTIVITY_RECOGNITION`. Starting tracking remains enabled when the optional permission is denied.

- [ ] **Step 5: Implement History filters and PIN-gated deletes**

Collect `observeOldestCapturedAt()` for year options. Use `remember(filter) { history.observeBetween(range.from, range.until) }`. Add Year/Month dropdowns, `Xóa theo bộ lọc`, and `Xóa tất cả`. Confirmation dialog must display Task 6 label, then open PIN dialog; only verified callbacks invoke `deleteBetween()` or `deleteAll()`.

Display `record.recordType.name` in each history row and export only the currently filtered records.

- [ ] **Step 6: Remove Status Device ID and show fixed sampling policy in Settings**

Delete only the Status row. Keep Settings Device ID. Add read-only text: `Giám sát GPS: 10 giây` and `Lưu khi đang chạy: 2 phút`.

- [ ] **Step 7: Run UI policy/source tests and compile Compose**

Run: `./gradlew.bat testDebugUnitTest --tests "com.internal.tracker.ui.*" assembleDebug --no-daemon`

Expected: tests pass and Compose compiles without experimental API or missing permission errors.

- [ ] **Step 8: Commit Task 7**

```powershell
git add app/src/main/java/com/internal/tracker/ui/TrackerApp.kt app/src/main/java/com/internal/tracker/ui/StatusUiModel.kt app/src/test/java/com/internal/tracker/ui/StatusUiModelTest.kt
git commit -m "feat: secure tracking and history controls"
```

---

### Task 8: Documentation and full verification

**Files:**
- Modify: `README.md`
- Modify: `docs/periodic-gmail-pilot-handover.md`

**Interfaces:**
- Consumes: completed behavior from Tasks 1–7.
- Produces: operator instructions and verified APK.

- [ ] **Step 1: Update operator documentation**

Replace “lấy một điểm GPS theo lịch” with the exact split architecture: observe 10 seconds, store every 2 minutes plus START/TEMP_STOP/STOP, report every 6h/12h/24h. Document Activity Recognition as optional fallback, foreground notification, PIN-protected operations, filtered deletion and one-year retention.

- [ ] **Step 2: Run focused regression tests**

Run:

```powershell
./gradlew.bat testDebugUnitTest --tests "com.internal.tracker.tracking.*" --tests "com.internal.tracker.history.*" --tests "com.internal.tracker.report.*" --tests "com.internal.tracker.mail.*" --tests "com.internal.tracker.ui.*" --no-daemon
```

Expected: zero failed tests.

- [ ] **Step 3: Run the full verification pipeline**

Run:

```powershell
./gradlew.bat clean testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest --no-daemon
```

Expected: `BUILD SUCCESSFUL`, zero test failures and zero lint errors. Confirm `app/build/outputs/apk/debug/app-debug.apk` exists. If an emulator/device is attached, run `connectedDebugAndroidTest` and confirm migration passes; otherwise report that device-only migration execution remains for the physical-device checklist.

- [ ] **Step 4: Review the requirements against the diff**

Run `git diff origin/feature/periodic-email-reports...HEAD --stat` and `git status --short`. Check every Global Constraint against code/tests; working tree must contain only the documentation changes from this task before committing them.

- [ ] **Step 5: Commit documentation**

```powershell
git add README.md docs/periodic-gmail-pilot-handover.md
git commit -m "docs: describe continuous GPS reporting"
```

- [ ] **Step 6: Re-run final proof after the last commit**

Run `./gradlew.bat testDebugUnitTest lintDebug assembleDebug --no-daemon` and `git status --short` fresh. Report exact test/build outcome, APK path, commit list, and any device-only validation not executed.
