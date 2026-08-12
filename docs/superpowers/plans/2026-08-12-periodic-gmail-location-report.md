# Periodic Gmail Location Report Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace continuous server tracking with Android 10+ scheduled Gmail reports that retain local history and survive network outages and reboot.

**Architecture:** Room is the durable source of truth. A unique one-time WorkManager job calculates the next calendar anchor, captures one location, writes Room and daily CSV, sends one batched SMTP message, then schedules the next anchor. Compose exposes a PIN gate, a minimal operations screen, administrator settings, and retained history.

**Tech Stack:** Kotlin, Jetpack Compose, Room, WorkManager, Fused Location Provider, Android Security Crypto/Keystore, Android Mail 1.6.7, FileProvider, JUnit 4.

## Global Constraints

- Support Android 10+ with `minSdk 29`, `targetSdk 36`, and Java 17.
- Accepted intervals are exactly 6, 12, and 24 hours.
- Anchor schedules use local 00:00/06:00/12:00/18:00 time and deterministic device `001`-`100` offsets over 59 minutes.
- WorkManager timing is approximate; no exact-alarm permission or continuous foreground location service.
- Test-build default PIN is `18758691`; changed PINs are salted hashes.
- Gmail SMTP uses TLS and a 16-character App Password; secrets never enter Git or logs.
- Room and daily CSV must be durable before SMTP begins; sent history is retained.
- One scheduled execution sends at most one email and batches all eligible backlog rows.
- No Traccar, receiver, Cloudflare Tunnel, relay endpoint, OAuth, or Play Store distribution in the pilot.

---

### Task 1: Android 10 Baseline And Secure Pilot Configuration

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/java/com/internal/tracker/config/PilotConfig.kt`
- Create: `app/src/main/java/com/internal/tracker/config/AdminPinStore.kt`
- Test: `app/src/test/java/com/internal/tracker/config/AdminPinStoreTest.kt`
- Modify: `.gitignore`
- Create: `docs/gmail-build-secrets.example.properties`

**Interfaces:**
- Produces: `PilotConfig(deviceNumber, recipient, intervalHours, sender, appPassword)`, `PilotConfigStore.load/save`, and `AdminPinStore.verify/change`.
- Consumes: Android Keystore-backed encrypted preferences pattern already used by `EncryptedProfileSecrets`.

- [ ] **Step 1: Add failing configuration and PIN tests**

```kotlin
@Test fun acceptsOnlyPilotIntervalsAndDeviceRange() {
    assertTrue(PilotConfig("001", "pic@example.com", 6, "sender@gmail.com", "abcdefghijklmnop").isValid())
    assertFalse(PilotConfig("000", "pic@example.com", 6, "sender@gmail.com", "abcdefghijklmnop").isValid())
    assertFalse(PilotConfig("001", "pic@example.com", 8, "sender@gmail.com", "abcdefghijklmnop").isValid())
}

@Test fun defaultPinAndChangedPinAreVerified() {
    val store = AdminPinStore(InMemoryPinPreferences())
    assertTrue(store.verify("18758691"))
    store.change("18758691", "24681357").getOrThrow()
    assertFalse(store.verify("18758691"))
    assertTrue(store.verify("24681357"))
}
```

- [ ] **Step 2: Run the focused test and verify it fails because the new types do not exist**

Run: `./gradlew.bat testDebugUnitTest --tests '*AdminPinStoreTest' --no-daemon --console=plain`

- [ ] **Step 3: Implement minimal validated config and PIN verification**

Use `PBKDF2WithHmacSHA256`, constant-time byte comparison, a predefined verifier for the documented test PIN, and a random 16-byte salt for changed PINs. Normalize App Password by removing whitespace and require exactly 16 remaining characters. Store runtime configuration in `EncryptedSharedPreferences`; never place the current PIN or App Password in ordinary preferences.

- [ ] **Step 4: Lower `minSdk` and add build-time Gmail defaults without committing secrets**

Set `minSdk = 29`. Read `SMTP_USER` and `SMTP_APP_PASSWORD` from Gradle properties first and environment variables second; use empty defaults for ordinary CI. Add ignored `gmail-secrets.properties` and document only these key names in the example file:

```properties
SMTP_USER=sender@gmail.com
SMTP_APP_PASSWORD=abcdefghijklmnop
```

Add Android Mail and Activation version `1.6.7` to the version catalog. Do not print Gradle property values.

- [ ] **Step 5: Run configuration tests and lint**

Run: `./gradlew.bat testDebugUnitTest --tests '*AdminPinStoreTest' lintDebug --no-daemon --console=plain`

- [ ] **Step 6: Commit**

```bash
git add .gitignore gradle/libs.versions.toml app/build.gradle.kts app/src/main/java/com/internal/tracker/config app/src/test/java/com/internal/tracker/config/AdminPinStoreTest.kt docs/gmail-build-secrets.example.properties
git commit -m "feat: add secure pilot configuration"
```

### Task 2: Anchored Report Scheduling

**Files:**
- Create: `app/src/main/java/com/internal/tracker/schedule/ReportSchedule.kt`
- Create: `app/src/main/java/com/internal/tracker/schedule/ReportScheduler.kt`
- Test: `app/src/test/java/com/internal/tracker/schedule/ReportScheduleTest.kt`

**Interfaces:**
- Consumes: `PilotConfig.deviceNumber` and `PilotConfig.intervalHours` from Task 1.
- Produces: `ReportSchedule.nextRun(now: ZonedDateTime, intervalHours: Int, deviceNumber: Int): ZonedDateTime` and `ReportScheduler.reconcile(enabled: Boolean)`.

- [ ] **Step 1: Write failing anchor and offset tests**

```kotlin
@Test fun sixHourScheduleReturnsNextCalendarAnchor() {
    val now = ZonedDateTime.parse("2026-08-12T01:00:00+07:00[Asia/Ho_Chi_Minh]")
    assertEquals("2026-08-12T06:00+07:00[Asia/Ho_Chi_Minh]", ReportSchedule.nextRun(now, 6, 1).toString())
}

@Test fun deviceOneAndOneHundredSpanApprovedWindow() {
    val now = ZonedDateTime.parse("2026-08-12T23:00:00+07:00[Asia/Ho_Chi_Minh]")
    assertEquals(LocalTime.of(0, 0), ReportSchedule.nextRun(now, 24, 1).toLocalTime())
    assertEquals(LocalTime.of(0, 59), ReportSchedule.nextRun(now, 24, 100).toLocalTime())
}

@Test fun aDelayedRunDoesNotDriftTheFollowingAnchor() {
    val delayed = ZonedDateTime.parse("2026-08-12T06:40:00+07:00[Asia/Ho_Chi_Minh]")
    assertEquals(12, ReportSchedule.nextRun(delayed, 6, 1).hour)
}
```

- [ ] **Step 2: Run the test and verify missing scheduler failures**

Run: `./gradlew.bat testDebugUnitTest --tests '*ReportScheduleTest' --no-daemon --console=plain`

- [ ] **Step 3: Implement calendar calculation and unique work reconciliation**

Calculate anchors from `now.toLocalDate().atStartOfDay(zone)` and integer second offsets `((deviceNumber - 1) * 3540) / 99`. Use one unique work name `scheduled-location-report`, `ExistingWorkPolicy.REPLACE` only during explicit reconciliation, and `NetworkType.NOT_REQUIRED` because capture and backup must run offline.

- [ ] **Step 4: Add clock/time-zone change entry points**

Keep receiver code out of this task; expose `reconcile` so Task 6 can invoke the same path for reboot, time-zone, and clock changes.

- [ ] **Step 5: Run schedule tests**

Run: `./gradlew.bat testDebugUnitTest --tests '*ReportScheduleTest' --no-daemon --console=plain`

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/internal/tracker/schedule app/src/test/java/com/internal/tracker/schedule
git commit -m "feat: schedule reports from calendar anchors"
```

### Task 3: Retained Location History And Database Migration

**Files:**
- Create: `app/src/main/java/com/internal/tracker/history/LocationRecord.kt`
- Create: `app/src/main/java/com/internal/tracker/history/LocationRecordDao.kt`
- Create: `app/src/main/java/com/internal/tracker/history/LocationHistoryRepository.kt`
- Modify: `app/src/main/java/com/internal/tracker/data/AppDatabase.kt`
- Modify: `app/src/main/java/com/internal/tracker/TrackerApplication.kt`
- Test: `app/src/test/java/com/internal/tracker/history/LocationHistoryRepositoryTest.kt`
- Test: `app/src/androidTest/java/com/internal/tracker/data/Migration1To2Test.kt`

**Interfaces:**
- Produces: `LocationRecord`, `DeliveryState`, `RecordSource`, `LocationRecordDao.observeBetween/unsent/insert/markRetrying/markSent`, and `LocationHistoryRepository`.
- Consumes: existing version-1 `pending_locations` rows for migration.

- [ ] **Step 1: Write failing repository state-transition tests**

```kotlin
@Test fun sentRowsRemainInHistory() = runTest {
    val repo = LocationHistoryRepository(FakeLocationRecordDao())
    val id = repo.capture(sample(), batteryPercent = 82, trackedMillis = 10_000, deviceNumber = "001", deviceId = "AND-1")
    repo.markSent(listOf(id), 20_000)
    assertEquals(DeliveryState.SENT, repo.get(id)!!.deliveryState)
    assertEquals(1, repo.count())
}

@Test fun retryKeepsRecordEligible() = runTest {
    val repo = LocationHistoryRepository(FakeLocationRecordDao())
    val id = repo.capture(sample(), 82, 10_000, "001", "AND-1")
    repo.markRetrying(listOf(id), "NETWORK")
    assertEquals(listOf(id), repo.unsent(100).map { it.id })
}
```

- [ ] **Step 2: Run the repository test and verify it fails**

Run: `./gradlew.bat testDebugUnitTest --tests '*LocationHistoryRepositoryTest' --no-daemon --console=plain`

- [ ] **Step 3: Add the version-2 schema and repository**

Create `location_records` with indexed `capturedAt`, `deliveryState`, and `source`; include battery, elapsed time, device metadata, attempt count, last error category, and sent timestamp. Keep record IDs auto-incrementing for the local sequence number.

- [ ] **Step 4: Add explicit migration `1 -> 2`**

Create the new table and copy remaining `pending_locations` rows as `source='LEGACY_IMPORT'`, `deliveryState='PENDING'`, nullable battery, and elapsed time zero. Leave unrelated legacy tables intact in SQLite but remove them from active Room entities. Register the migration without destructive fallback.

- [ ] **Step 5: Run repository and migration tests**

Run: `./gradlew.bat testDebugUnitTest --tests '*LocationHistoryRepositoryTest' --no-daemon --console=plain`

Run when an API 29+ emulator/device is available: `./gradlew.bat connectedDebugAndroidTest --no-daemon --console=plain`

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/internal/tracker/history app/src/main/java/com/internal/tracker/data/AppDatabase.kt app/src/main/java/com/internal/tracker/TrackerApplication.kt app/src/test/java/com/internal/tracker/history app/src/androidTest/java/com/internal/tracker/data
git commit -m "feat: retain scheduled location history"
```

### Task 4: Daily CSV Backup And Sharing

**Files:**
- Create: `app/src/main/java/com/internal/tracker/export/LocationCsv.kt`
- Create: `app/src/main/java/com/internal/tracker/export/DailyCsvStore.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/res/xml/file_paths.xml`
- Test: `app/src/test/java/com/internal/tracker/export/LocationCsvTest.kt`

**Interfaces:**
- Consumes: `LocationRecord` from Task 3.
- Produces: `LocationCsv.encode(records): String`, `DailyCsvStore.writeDay`, `writeAffectedDays`, and `shareUri`.

- [ ] **Step 1: Write a failing CSV escaping and column-order test**

```kotlin
@Test fun writesStableHeaderAndEscapesValues() {
    val csv = LocationCsv.encode(listOf(record(lastError = "timeout, retry")))
    assertTrue(csv.startsWith("record_number,device_number,device_id,captured_at,timezone,latitude,longitude,accuracy_m,battery_percent,tracked_duration,delivery_state"))
    assertTrue(csv.contains("\"timeout, retry\""))
}
```

- [ ] **Step 2: Run the CSV test and verify it fails**

Run: `./gradlew.bat testDebugUnitTest --tests '*LocationCsvTest' --no-daemon --console=plain`

- [ ] **Step 3: Implement deterministic CSV with atomic daily replacement**

Use `StringBuilder`, RFC 4180 quoting, UTF-8, a temporary file, and atomic rename into `getExternalFilesDir("reports")`. Generate `GPS-001-2026-08-13.csv`. Write once before SMTP and rewrite affected dates after successful Room status updates.

- [ ] **Step 4: Register a non-exported FileProvider**

Grant temporary read permission only to the share target. Do not request storage permissions.

- [ ] **Step 5: Run CSV tests and lint**

Run: `./gradlew.bat testDebugUnitTest --tests '*LocationCsvTest' lintDebug --no-daemon --console=plain`

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/internal/tracker/export app/src/main/AndroidManifest.xml app/src/main/res/xml/file_paths.xml app/src/test/java/com/internal/tracker/export
git commit -m "feat: back up and share daily location CSV"
```

### Task 5: Direct Gmail SMTP Batching

**Files:**
- Create: `app/src/main/java/com/internal/tracker/mail/MailResult.kt`
- Create: `app/src/main/java/com/internal/tracker/mail/GmailSmtpSender.kt`
- Create: `app/src/main/java/com/internal/tracker/mail/ReportMessageFactory.kt`
- Create: `app/src/main/java/com/internal/tracker/mail/ReportDelivery.kt`
- Test: `app/src/test/java/com/internal/tracker/mail/ReportMessageFactoryTest.kt`
- Test: `app/src/test/java/com/internal/tracker/mail/ReportDeliveryTest.kt`

**Interfaces:**
- Consumes: `PilotConfig`, `LocationRecord`, `LocationHistoryRepository`, and `DailyCsvStore`.
- Produces: `MailSender.send(message): MailResult`, `ReportDelivery.deliverPending(): DeliveryOutcome`, and `GmailSmtpSender.testCredentials`.

- [ ] **Step 1: Write failing message and batching tests**

```kotlin
@Test fun oneMessageContainsEveryPendingRecord() = runTest {
    val sender = FakeMailSender(MailResult.Accepted)
    val delivery = deliveryWith(records = listOf(record(1), record(2)), sender = sender)
    delivery.deliverPending()
    assertEquals(1, sender.messages.size)
    assertEquals(listOf(1L, 2L), sender.messages.single().recordIds)
}

@Test fun rejectedAuthenticationRetainsBacklogAndRedactsSecret() = runTest {
    val delivery = deliveryWith(records = listOf(record(1)), sender = FakeMailSender(MailResult.AuthenticationRejected))
    val result = delivery.deliverPending()
    assertEquals("AUTHENTICATION", result.publicError)
    assertEquals(DeliveryState.RETRYING, history.get(1)!!.deliveryState)
}
```

- [ ] **Step 2: Run mail tests and verify they fail**

Run: `./gradlew.bat testDebugUnitTest --tests '*ReportMessageFactoryTest' --tests '*ReportDeliveryTest' --no-daemon --console=plain`

- [ ] **Step 3: Implement MIME creation and SMTP transport**

Use `smtp.gmail.com`, port `465`, TLS/SSL enabled, authentication required, bounded connection/read/write timeouts, UTF-8 text, and one CSV attachment. Include the latest Google Maps URL in the body. Map exceptions to `AUTHENTICATION`, `NETWORK`, `TLS`, `RATE_LIMIT`, or `UNKNOWN`; never return raw exception text to UI or logs.

- [ ] **Step 4: Implement transactional delivery transitions**

Select the oldest unsent rows whose generated CSV stays within 20 MiB, mark them retrying for the attempt, ensure affected daily CSV files exist, send once, then mark all accepted IDs sent and regenerate their daily CSV files. Leave overflow rows pending. On rejection, increment attempt metadata without deleting rows. Serialize delivery using a process-wide `Mutex`.

- [ ] **Step 5: Connect credential replacement validation**

Normalize and validate the candidate, call `testCredentials`, and only then replace encrypted runtime credentials. Retain the previous value for every non-accepted result.

- [ ] **Step 6: Run mail tests**

Run: `./gradlew.bat testDebugUnitTest --tests '*ReportMessageFactoryTest' --tests '*ReportDeliveryTest' --no-daemon --console=plain`

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/internal/tracker/mail app/src/test/java/com/internal/tracker/mail
git commit -m "feat: send batched Gmail location reports"
```

### Task 6: Scheduled Capture, Restart Recovery, And Operational State

**Files:**
- Create: `app/src/main/java/com/internal/tracker/report/LocationSnapshotProvider.kt`
- Create: `app/src/main/java/com/internal/tracker/report/BatteryReader.kt`
- Create: `app/src/main/java/com/internal/tracker/report/ReportWorker.kt`
- Create: `app/src/main/java/com/internal/tracker/schedule/ScheduleReceiver.kt`
- Modify: `app/src/main/java/com/internal/tracker/tracking/TrackingPreferences.kt`
- Modify: `app/src/main/java/com/internal/tracker/AppContainer.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Test: `app/src/test/java/com/internal/tracker/report/ReportRunTest.kt`
- Test: `app/src/test/java/com/internal/tracker/schedule/ScheduleReceiverPolicyTest.kt`

**Interfaces:**
- Consumes: scheduler, config, history, CSV, and mail interfaces from Tasks 1-5.
- Produces: `ReportRun.execute(): ReportRunResult`, `ReportWorker`, operational timestamps/errors, and receiver reconciliation.

- [ ] **Step 1: Write failing end-to-end orchestration tests**

```kotlin
@Test fun captureIsPersistedAndBackedUpBeforeMail() = runTest {
    val events = mutableListOf<String>()
    val run = reportRun(events, mailResult = MailResult.Accepted)
    run.execute()
    assertEquals(listOf("capture", "room", "csv", "mail", "sent", "csv"), events)
}

@Test fun offlineRunStillCapturesAndSchedulesNextAnchor() = runTest {
    val result = reportRun(mailResult = MailResult.NetworkFailure).execute()
    assertEquals(DeliveryState.RETRYING, result.recordState)
    assertTrue(result.nextRunScheduled)
}
```

- [ ] **Step 2: Run report tests and verify they fail**

Run: `./gradlew.bat testDebugUnitTest --tests '*ReportRunTest' --tests '*ScheduleReceiverPolicyTest' --no-daemon --console=plain`

- [ ] **Step 3: Implement one-shot location and battery capture**

Use `FusedLocationProviderClient.getCurrentLocation()` with cancellation timeout. Store no fabricated row when permission, GPS, or timeout prevents a verified location; instead persist the operational error and next retry time.

- [ ] **Step 4: Implement worker orchestration and always reschedule**

In `finally`, calculate and enqueue the next anchored run when tracking remains enabled. WorkManager result is success after durable local capture even when SMTP fails; SMTP backlog is retried at the next scheduled capture rather than through rapid WorkManager backoff.

- [ ] **Step 5: Register restart/time receivers**

Add `RECEIVE_BOOT_COMPLETED` and a non-exported receiver for boot, time-zone, and clock changes. The receiver reconciles one future job only when tracking was enabled. Remove foreground-service permissions and declaration.

- [ ] **Step 6: Run report and schedule tests plus lint**

Run: `./gradlew.bat testDebugUnitTest --tests '*ReportRunTest' --tests '*ScheduleReceiverPolicyTest' lintDebug --no-daemon --console=plain`

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/internal/tracker/report app/src/main/java/com/internal/tracker/schedule/ScheduleReceiver.kt app/src/main/java/com/internal/tracker/tracking/TrackingPreferences.kt app/src/main/java/com/internal/tracker/AppContainer.kt app/src/main/AndroidManifest.xml app/src/test/java/com/internal/tracker/report app/src/test/java/com/internal/tracker/schedule/ScheduleReceiverPolicyTest.kt
git commit -m "feat: capture and restore scheduled reports"
```

### Task 7: PIN Gate, Minimal Operations UI, Settings, And History

**Files:**
- Modify: `app/src/main/java/com/internal/tracker/MainActivity.kt`
- Rewrite: `app/src/main/java/com/internal/tracker/ui/TrackerApp.kt`
- Create: `app/src/main/java/com/internal/tracker/ui/PinScreen.kt`
- Create: `app/src/main/java/com/internal/tracker/ui/StatusScreen.kt`
- Create: `app/src/main/java/com/internal/tracker/ui/AdminSettingsScreen.kt`
- Create: `app/src/main/java/com/internal/tracker/ui/HistoryScreen.kt`
- Modify: `app/src/main/java/com/internal/tracker/tracking/PermissionState.kt`
- Test: `app/src/test/java/com/internal/tracker/ui/AppUiPolicyTest.kt`
- Test: `app/src/test/java/com/internal/tracker/tracking/PermissionStateTest.kt`

**Interfaces:**
- Consumes: stores and use cases exposed by `AppContainer`.
- Produces: PIN-gated navigation, start/stop/reconcile actions, settings validation, permission intents, date-filtered history, and CSV share intent.

- [ ] **Step 1: Write failing UI policy tests**

```kotlin
@Test fun lockedAppExposesOnlyPinDestination() {
    assertEquals(setOf(Destination.PIN), AppUiPolicy.destinations(unlocked = false))
}

@Test fun normalStatusCommandsStayMinimal() {
    assertEquals(setOf(StatusCommand.GRANT_PERMISSION, StatusCommand.START_TRACKING), AppUiPolicy.commands(ready = false, tracking = false))
}
```

- [ ] **Step 2: Run UI and permission tests and verify failures**

Run: `./gradlew.bat testDebugUnitTest --tests '*AppUiPolicyTest' --tests '*PermissionStateTest' --no-daemon --console=plain`

- [ ] **Step 3: Implement the PIN gate and minimal status screen**

Keep unlock state in process memory only. Show explicit last capture, last accepted email, last technical error, next expected window, permission readiness, and tracking state. Starting tracking validates completed configuration and reconciles work; stopping cancels unique work and closes the elapsed-time session.

- [ ] **Step 4: Implement administrator settings**

Use dropdown/segmented controls for 6/12/24 hours, numeric device input `001`-`100`, recipient validation, masked App Password input, and `Luu va kiem tra`. Do not display or copy the existing App Password back into UI state.

- [ ] **Step 5: Implement history and export**

Observe Room by selected date range. Display record number, capture time, delivery state, battery, accuracy, and elapsed tracking duration. Detail shows coordinates and a map intent. Share daily or complete CSV through FileProvider.

- [ ] **Step 6: Implement Android-version-specific permission recovery**

Request fine location first, then background location. When Android requires manual action, open application details, location services, notification, or battery settings directly. UI copy must state that Android may delay or stop scheduled background work.

- [ ] **Step 7: Run UI policy tests and lint**

Run: `./gradlew.bat testDebugUnitTest --tests '*AppUiPolicyTest' --tests '*PermissionStateTest' lintDebug --no-daemon --console=plain`

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/internal/tracker/MainActivity.kt app/src/main/java/com/internal/tracker/ui app/src/main/java/com/internal/tracker/tracking/PermissionState.kt app/src/test/java/com/internal/tracker/ui app/src/test/java/com/internal/tracker/tracking/PermissionStateTest.kt
git commit -m "feat: add PIN-gated report operations UI"
```

### Task 8: Remove Prototype Transport And Complete Pilot Handover

**Files:**
- Delete: `app/src/main/java/com/internal/tracker/network/ConnectionTester.kt`
- Delete: `app/src/main/java/com/internal/tracker/network/DiagnosticResult.kt`
- Delete: `app/src/main/java/com/internal/tracker/network/OsmAndClient.kt`
- Delete: `app/src/main/java/com/internal/tracker/network/OsmAndRequestFactory.kt`
- Delete: `app/src/main/java/com/internal/tracker/network/SendResult.kt`
- Delete: `app/src/main/java/com/internal/tracker/network/TlsClientFactory.kt`
- Delete: `app/src/main/java/com/internal/tracker/profile/EncryptedProfileSecrets.kt`
- Delete: `app/src/main/java/com/internal/tracker/profile/Profile.kt`
- Delete: `app/src/main/java/com/internal/tracker/profile/ProfileDao.kt`
- Delete: `app/src/main/java/com/internal/tracker/profile/ProfileRepository.kt`
- Delete: `app/src/main/java/com/internal/tracker/queue/LocationQueueRepository.kt`
- Delete: `app/src/main/java/com/internal/tracker/queue/PendingLocation.kt`
- Delete: `app/src/main/java/com/internal/tracker/queue/PendingLocationDao.kt`
- Delete: `app/src/main/java/com/internal/tracker/tracking/LocationForegroundService.kt`
- Delete: `app/src/main/java/com/internal/tracker/tracking/TrackingController.kt`
- Delete: `app/src/main/java/com/internal/tracker/worker/QueueUploader.kt`
- Delete: `app/src/main/java/com/internal/tracker/worker/UploadWorker.kt`
- Delete: corresponding obsolete unit tests
- Modify: `README.md`
- Create: `docs/periodic-gmail-pilot-handover.md`

**Interfaces:**
- Consumes: completed application from Tasks 1-7.
- Produces: buildable pilot APK and operator/build handover without obsolete server instructions in the primary workflow.

- [ ] **Step 1: Remove unreachable server/profile/foreground-service code and tests**

Use `rg` before deletion to confirm every caller has moved to the new report flow. Retain `DeviceIdProvider`, shared permission logic, Room database migration SQL, and reusable Compose theme resources.

- [ ] **Step 2: Write the build and operation handover**

Document Windows prerequisites, local secret-property creation, debug APK command/location, first unlock PIN `18758691`, Gmail 2-Step Verification/App Password setup, device numbering, permission setup, interval semantics, CSV export, credential rotation, quota monitoring, and recovery after reboot or interruption.

- [ ] **Step 3: Run repository scans for forbidden credential patterns and obsolete runtime references**

Run:

```powershell
rg -n "SMTP_APP_PASSWORD|smtp\.gmail\.com|OsmAnd|LocationForegroundService|trycloudflare" app/src docs README.md
```

Expected: SMTP key name and host occur only where required; no credential value exists; no active Android reference to OsmAnd, foreground tracking, receiver, or tunnel remains.

- [ ] **Step 4: Run the complete Android verification suite**

Run: `./gradlew.bat clean testDebugUnitTest lintDebug assembleDebug --no-daemon --console=plain`

Expected: `BUILD SUCCESSFUL`; debug APK exists at `app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 5: Record unavailable device tests honestly**

If no emulator or phone is connected, leave Android 10/12/14+, reboot, Doze, and real Gmail SMTP checks as explicit pending acceptance items. Do not report them as passed from JVM tests.

- [ ] **Step 6: Commit**

```bash
git add -A app README.md docs/periodic-gmail-pilot-handover.md
git commit -m "docs: hand over periodic Gmail pilot"
```

### Task 9: Final Review And Branch Handoff

**Files:**
- Modify only files required by verified review findings.

**Interfaces:**
- Consumes: all prior tasks.
- Produces: reviewed branch ready to push and test on physical devices.

- [ ] **Step 1: Review the full branch diff against the approved design**

Run: `git diff feature/cloudflare-quick-tunnel-pilot...HEAD --stat` and inspect every changed file for data loss, secret exposure, scheduling drift, and Android 10 API compatibility.

- [ ] **Step 2: Run final verification after any review fixes**

Run: `./gradlew.bat clean testDebugUnitTest lintDebug assembleDebug --no-daemon --console=plain`

- [ ] **Step 3: Verify repository state and summarize commits**

Run: `git status --short` and `git log --oneline feature/cloudflare-quick-tunnel-pilot..HEAD`.

- [ ] **Step 4: Push the feature branch after user-approved network action**

Run: `git push -u origin feature/periodic-email-reports`.
