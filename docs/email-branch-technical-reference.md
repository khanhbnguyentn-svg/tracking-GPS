# Email Branch Technical Reference

> Canonical handover document for the `feature/periodic-email-reports` branch.
> Last reviewed: 2026-08-17.
> Release described: **2.1.0** (`versionCode 6`).
> Android package: `com.internal.tracker`

## 1. Purpose and scope

This branch turns the application from a periodic single-point reporter into a continuous, local-first GPS tracker with scheduled email delivery.

The application:

- monitors location continuously while tracking is enabled;
- requests a high-accuracy location callback every 10 seconds;
- stores route events locally rather than emailing every callback;
- records movement at two-minute intervals plus start and stop transitions;
- preserves short stops as `TEMP_STOP` and stops of at least two minutes as `STOP`;
- sends finalized, unsent route data as CSV every 6, 12, or 24 hours;
- observes GPS gaps and suspicious trajectories without deleting or changing route data;
- sends immediate diagnostic email when possible and includes unresolved diagnostics in the next scheduled report;
- restores the user's tracking state after application or device restart;
- protects Settings, stopping, and deletion with an eight-digit administrator PIN;
- retains Room as the authoritative data store and writes CSV files as recoverable exports.

This document describes the implemented state of the branch. Earlier design documents remain useful history, but this file should be the first reference for a new development session.

## 2. Release identity

| Item | Value |
|---|---|
| Version name | `2.1.0` |
| Version code | `6` |
| Application ID | `com.internal.tracker` |
| Minimum Android | API 29 |
| Target / compile API | 36 |
| JVM language level | Java 17 |
| Official APK | `dist/tracking-gps-2.1.0.apk` |
| APK SHA-256 | `19F6299421BF3879466158619A973BC3AB36F4349557F014834844FBF39955C1` |
| Signing certificate SHA-256 | `8F:19:12:A3:4E:D2:CB:9D:DF:88:40:DB:49:A7:69:13:42:51:B3:29:74:84:33:36:78:E2:C6:79:CA:E4:F5:85` |
| APK signing | APK Signature Scheme v2, one signer |

The release artifact is intentionally ignored by Git. Verify its checksum and certificate before field installation.

## 3. Technology stack

- Kotlin 2.2.10
- Android Gradle Plugin 8.13
- Jetpack Compose with Material 3
- Room 2.7.2
- WorkManager 2.10.3
- Google Play Services Location 21.3
- AndroidX Security 1.1
- JavaMail for Android 1.6.7
- Java 17 toolchain

Dependency versions are centralized in the Gradle version catalog. The application uses a small manual dependency-injection container rather than a DI framework.

## 4. High-level architecture

```text
MainActivity / TrackerApp
          |
       AppContainer
       /    |      \
 Room DB  Tracking  Reporting / SMTP
   |         |             |
 History  Foreground    WorkManager
   |       Service          |
 CSV export + diagnostics + scheduled email
```

`AppContainer` creates and shares the main services:

- encrypted pilot configuration and administrator PIN storage;
- stable device identity;
- Room database and repositories;
- movement and tracking coordination;
- diagnostic monitoring and alert scheduling;
- report scheduling, report execution, and SMTP delivery.

Room database `tracker.db`, schema version 4, is the authoritative source. CSV files are derived exports and must not be treated as the primary database.

## 5. User interface and PIN policy

The Compose interface is implemented primarily in `ui/TrackerApp.kt` and contains Status, History, and Settings.

### 5.1 Access policy

There is no cold-start login PIN.

- **Status:** immediately visible.
- **History:** immediately visible.
- **Settings:** requires the administrator PIN on first access in the current UI session.
- **Stop tracking:** always requires PIN confirmation.
- **Delete filtered data / delete all:** always requires a confirmation step and PIN.
- **Start tracking:** does not require the PIN.

The Settings unlock state is session-scoped using saveable UI state. A new application/UI session requires verification again.

### 5.2 Administrator PIN

- exactly eight numeric digits;
- salted PBKDF2-HMAC-SHA256;
- 120,000 iterations;
- random 16-byte salt;
- 256-bit derived hash;
- stored in encrypted shared preferences.

Never add the deployed PIN to source code, documentation, logs, screenshots, or test fixtures.

### 5.3 Status

Status shows operational state such as tracking state and report information. The device ID is deliberately not displayed on Status. It remains available in protected Settings and may be included in operational exports and email.

### 5.4 History filtering and deletion

History supports:

- year selection from available data through the current year;
- month selection from 1 through 12;
- an **All** option;
- half-open date ranges, `[start, end)`, calculated in the device time zone.

The **Delete** action applies to the currently selected filter. **Delete all** removes all Room route records after confirmation and PIN verification.

Deletion currently affects Room route records only. Previously generated app-scoped CSV files are not explicitly removed or rebuilt by the History deletion flow. This is a known consistency limitation.

## 6. Configuration

`PilotConfig` is stored in encrypted shared preferences.

Validated settings include:

- device number from `001` through `100`;
- report interval of 6, 12, or 24 hours;
- syntactically valid sender and recipient email addresses;
- Gmail application password normalized by removing whitespace and requiring 16 characters.

The sender address and application password are entered by the user during setup. They do not require a pre-created local secrets file. Gmail settings are saved only after a successful credential test. Leaving the password field blank while editing other settings preserves the stored password.

Build-time SMTP values remain supported as a compatibility path, but interactive encrypted configuration is the field deployment path.

### 6.1 Device identity

`DeviceIdProvider` uses the Android ID when available and an encrypted persistent fallback otherwise. The identity should remain stable across ordinary upgrades performed with `adb install -r`.

## 7. Continuous tracking lifecycle

Tracking is implemented by `tracking/TrackingService.kt`.

When tracking is enabled:

1. the service enters foreground mode;
2. a persistent notification is shown;
3. high-accuracy location callbacks are requested every 10 seconds;
4. every callback is evaluated by movement and diagnostic logic;
5. only meaningful route events are persisted;
6. tracking health is checked every 10 seconds.

Important service properties:

- foreground notification ID: `701`;
- notification channel: `continuous_tracking`;
- service restart mode: `START_STICKY`;
- all movement modes retain high-accuracy requests;
- activity-recognition failure does not disable GPS tracking.

The 10-second request interval is a monitoring cadence, not a promise that Android will always deliver a callback at exactly that time. Device power management, satellite visibility, permissions, and system scheduling may delay callbacks. Delays are recorded by diagnostics rather than silently interpreted as movement events.

## 8. Movement event model

The app does not save every 10-second callback as a route row. It evaluates them continuously and stores a compact event stream.

### 8.1 Thresholds

| Rule | Implemented value |
|---|---|
| Moving threshold | speed at least 5 km/h |
| Stopped threshold | speed below 3 km/h |
| Periodic moving record | every 120 seconds |
| Confirmed stop duration | at least 120 seconds |
| Stop-radius constraint | within 30 metres |
| GPS-only start confirmation | two consecutive moving fixes |
| Activity-recognition start | immediate for `IN_VEHICLE` |

The gap between 3 and 5 km/h provides hysteresis and reduces state flapping.

### 8.2 Event types

- `START`: movement begins.
- `PERIODIC`: vehicle remains moving and the two-minute save interval elapses.
- `TEMP_STOP`: a stop candidate that has not remained valid for two minutes.
- `STOP`: the same candidate row after a valid stop lasts at least two minutes.

### 8.3 Stop candidate lifecycle

When speed drops below the stop threshold, the app inserts one unfinished candidate immediately. This protects evidence of a short interruption or service termination.

- If movement resumes before two minutes, the row is finalized as `TEMP_STOP`.
- If the device leaves the stop radius before confirmation, it is finalized as `TEMP_STOP`.
- If it remains within 30 metres for at least two minutes, the same row is finalized as `STOP`.
- If the user stops tracking while a candidate is open, it is finalized as `TEMP_STOP`.

A confirmed stop therefore produces one finalized stop row, not a row every two minutes and not separate “stop started” and “stop ended” rows. The later `START` records resumed movement.

### 8.4 Accuracy policy

The app deliberately does not discard a callback merely because it is old or has poor reported accuracy. Route interpretation and accuracy weighting can be handled by the backend. On-device diagnostics flag suspicious patterns but do not delete, rewrite, or suppress route records.

## 9. Restart and recovery

Operational tracking intent is persisted separately from protected configuration.

Recovery mechanisms include:

- `START_STICKY` service restart;
- boot and package-replacement receivers;
- reconciliation on application launch;
- persisted movement state and open stop-candidate identity;
- WorkManager rescheduling for reports and diagnostic delivery.

After a reboot or package upgrade, the application should restore tracking automatically when it was enabled before the interruption. The user or administrator should not need to open the application or press Start.

The coordinator restores an unfinished stop candidate using the same Room row. It must not create a synthetic STOP/START pair solely because the process or device restarted.

## 10. Database

Room database: `tracker.db`
Current schema: version 4

### 10.1 Location records

The `location_records` table includes:

- `id`
- `deviceNumber`
- `deviceId`
- `capturedAt`
- `timezone`
- `latitude`
- `longitude`
- `accuracy`
- `batteryPercent`
- `trackedDurationMillis`
- `source`
- `state`
- `attemptCount`
- `lastError`
- `sentAt`
- `recordType`
- `isFinalized`

Delivery states:

- `PENDING`
- `RETRYING`
- `SENT`

Sources:

- `CURRENT`
- `LEGACY_IMPORT`

Only finalized records whose state is not `SENT` are eligible for email delivery.

### 10.2 Diagnostics tables

Schema version 4 adds diagnostic incidents and associated samples. Samples are linked to incidents with cascade cleanup. Diagnostics are separate from route records so anomaly analysis cannot mutate the route history.

### 10.3 Migrations

- **1 → 2:** imports legacy location history into the current record model.
- **2 → 3:** adds continuous-movement fields and event semantics.
- **3 → 4:** adds diagnostic incidents and samples.

Migration behavior is covered by Android instrumented tests. Do not use destructive fallback migration in production.

### 10.4 Retention

At the start of every report run:

- route records older than one year, using a local-date boundary, are deleted;
- diagnostic summaries are retained for one year;
- already reported diagnostic samples are retained for 30 days.

Cleanup failure is recorded but does not prevent report delivery from being attempted.

## 11. CSV exports

### 11.1 Route CSV

The route CSV header is:

```csv
record_number,device_number,device_id,captured_at,timezone,latitude,longitude,accuracy_m,battery_percent,tracked_duration,delivery_state,record_type
```

Timestamp and time-zone fields allow backend reconstruction without assuming the backend time zone.

### 11.2 Local daily export

The app maintains app-scoped daily CSV copies beneath:

```text
/sdcard/Android/data/com.internal.tracker/files/reports/
```

Files are written using a temporary file followed by a move, limiting partial-file exposure.

Room remains authoritative. Candidate finalization updates Room immediately, but the derived CSV is not guaranteed to refresh at that exact transition; a later export refresh may be required. This and History deletion behavior are the two known local-CSV consistency limitations.

### 11.3 Diagnostic attachments

Diagnostic emails and combined scheduled reports can include:

- incident summary CSV;
- diagnostic sample CSV;
- route CSV when route rows are pending.

Attachments are capped by delivery limits described below.

## 12. Report scheduling

Reports are calendar-aligned at 6-, 12-, or 24-hour intervals.

A fleet offset spreads SMTP load across devices:

```text
offsetSeconds = ((deviceNumber - 1) * 3540) / 99
```

This maps device `001` to zero offset and device `100` to 59 minutes.

The unique WorkManager job is `scheduled-location-report` and uses replacement semantics when reconciled. Each run receives an exact `scheduled_for` epoch value. This creates a stable reporting window even if Android starts the worker late.

The next calendar run is scheduled in a `finally` path so an individual cleanup or delivery failure does not end the schedule chain.

## 13. Scheduled report execution

`ReportRun` orchestrates:

1. retention cleanup;
2. selection of finalized unsent route rows;
3. selection of unreported diagnostic incidents;
4. CSV construction and attachment sizing;
5. SMTP delivery;
6. delivery-state updates;
7. scheduling of the next calendar run.

Limits:

- up to 10,000 route rows per report;
- up to 1,000 diagnostic incidents per report;
- up to 20 MiB total attachments.

A scheduled email may be:

- route-only;
- diagnostics-only;
- combined route and diagnostics.

The report ID is deterministic: SHA-256 over the device ID, scheduled time, sorted route row IDs, and sorted diagnostic incident IDs. This helps administrators identify duplicates.

On SMTP acceptance:

- route rows become `SENT`;
- included diagnostics become reported;
- success telemetry is stored.

On failure:

- route rows become `RETRYING`;
- diagnostics remain unreported;
- failure details are retained for later inspection.

`ReportWorker` returns successful WorkManager completion after orchestration; retry is handled by the retained backlog and next calendar run rather than WorkManager's exponential retry.

There is no durable serialized email outbox. A delayed retry builds a new message from current database state and the currently installed `BuildConfig` version. Consequently, the version shown in an email identifies the build that constructed that message, not necessarily the build that originally captured every attached row.

## 14. SMTP delivery

SMTP configuration:

- host: `smtps.gmail.com`;
- port: `465`;
- SSL: enabled;
- authentication: enabled;
- connection, read, and write timeouts: 30 seconds.

Implemented error categories include:

- authentication;
- TLS;
- network;
- unknown.

A rate-limited result exists in the delivery model, but the current sender does not explicitly classify a server response into that category.

Release minification rules retain JavaMail SMTP and MIME activation handlers. These rules are important: release 2.0.0 exposed a release-only SMTP login/configuration problem that did not represent the intended interactive setup workflow.

## 15. Tracking diagnostics

Diagnostics provide evidence for missing or suspicious GPS behavior while leaving route data unchanged.

### 15.1 GPS callback gap

A GPS gap opens after 30 seconds without a callback while tracking is expected to be active.

Properties:

- only one gap incident may remain open at a time;
- location registration recovery is retried every 300 seconds while needed;
- recovery closes the same incident;
- immediate OPENED and RECOVERED alerts use unique WorkManager names with `KEEP` semantics;
- failed immediate delivery leaves the incident available for the scheduled report.

The immediate diagnostic worker performs one SMTP attempt and then reports WorkManager success. It does not create an uncontrolled WorkManager retry loop.

### 15.2 Trajectory anomaly detection

The detector observes a rolling window of 10 fixes and starts evaluating after six prior samples.

Key thresholds/signals include:

- stale time jump over 60 seconds;
- minimum spatial jump of 100 metres;
- minimum spike speed of 15 m/s;
- return ratio at or below 0.35;
- reversed direction cosine below -0.5;
- effective distance adjusted using reported accuracy.

An incident requires spatial isolation plus at least one other anomaly signal. Poor accuracy alone is not an incident.

### 15.3 Event-sequence diagnostics

Possible reasons include:

- `DUPLICATE_START`
- `ORPHAN_STOP`
- `TEMP_STOP_OVERDUE`
- `RESTORED_CANDIDATE_UNRESOLVED`

The unified integrity monitor also treats existing temporary-stop state as diagnostic context, avoiding a separate competing abnormal-event pipeline.

### 15.4 Incident types

- `GPS_GAP`
- `SUSPECTED_GPS_JUMP`
- `TIMESTAMP_ANOMALY`
- `EVENT_SEQUENCE_ANOMALY`
- `UNRESOLVED_TEMP_STOP`

## 16. Permissions and Android components

The manifest requests:

- Internet;
- coarse, fine, and background location;
- notification permission;
- ignore-battery-optimization request;
- boot completed;
- foreground service and foreground-service location;
- activity recognition.

The main activity is exported for launcher use. Internal service, receiver, and provider exposure is restricted as appropriate.

`allowBackup` is disabled.

The manifest currently permits cleartext traffic. SMTP itself uses TLS, but `usesCleartextTraffic=true` is a hardening opportunity if no remaining component requires HTTP.

## 17. Source map

### Application and composition

| File | Responsibility |
|---|---|
| `MainActivity.kt` | Android activity and Compose entry point |
| `TrackerApplication.kt` | process initialization and reconciliation |
| `AppContainer.kt` | manual dependency graph |
| `ui/TrackerApp.kt` | tabs, screens, PIN dialogs, configuration flow |
| `ui/HistoryFilter.kt` | year/month range calculation |
| `ui/StatusUiModel.kt` | user-facing operational status |
| `ui/AppUiPolicy.kt` | UI access and action policy |

### Configuration and identity

| File | Responsibility |
|---|---|
| `config/AdminPinStore.kt` | PIN hashing, storage, and verification |
| `config/PilotConfig.kt` | encrypted device/report/email configuration |
| `config/DeviceIdProvider.kt` | stable device identity |

### Tracking

| File | Responsibility |
|---|---|
| `tracking/TrackingService.kt` | foreground service, location callbacks, health checks |
| `tracking/TrackingCoordinator.kt` | route-state orchestration and persistence |
| `tracking/MovementDetector.kt` | movement/stop state machine |
| `tracking/TrackingPreferences.kt` | persisted operational tracking state |
| `tracking/TrackingHealthPolicy.kt` | callback-health timing |
| `tracking/LocationPriorityPolicy.kt` | location priority decisions |
| `tracking/VehicleActivityMonitor.kt` | activity-recognition registration |
| `tracking/VehicleActivityReceiver.kt` | activity-recognition results |
| `tracking/PermissionState.kt` | permission state interpretation |

### Data and export

| File | Responsibility |
|---|---|
| `data/AppDatabase.kt` | Room schema and migrations |
| `history/LocationRecord.kt` | route entity and enums |
| `history/LocationRecordDao.kt` | route queries and mutations |
| `history/LocationHistoryRepository.kt` | date filters, retention, delivery updates |
| `export/LocationCsv.kt` | route CSV serialization |
| `export/DailyCsvStore.kt` | app-scoped atomic CSV copies |

### Reports and email

| File | Responsibility |
|---|---|
| `report/ReportRun.kt` | cleanup and delivery orchestration |
| `report/ReportWorker.kt` | WorkManager entry point |
| `report/BatteryReader.kt` | battery capture |
| `report/LocationSnapshotProvider.kt` | legacy/current snapshot support |
| `schedule/ReportSchedule.kt` | interval and fleet-offset calculations |
| `schedule/WorkManagerReportScheduler.kt` | unique scheduled work |
| `schedule/ScheduleReceiver.kt` | boot/package schedule recovery |
| `mail/GmailSmtpSender.kt` | authenticated SSL SMTP |
| `mail/ReportDelivery.kt` | batches, results, and state transitions |
| `mail/ReportMessageFactory.kt` | subject, body, and attachments |
| `mail/ReportId.kt` | deterministic report identity |
| `mail/DeliveryTelemetry.kt` | last delivery status |
| `mail/MailResult.kt` | delivery result model |

### Diagnostics

| File | Responsibility |
|---|---|
| `diagnostics/TrackingIntegrityMonitor.kt` | central diagnostic orchestration |
| `diagnostics/GpsGapDetector.kt` | gap open/recovery state |
| `diagnostics/TrajectoryAnomalyDetector.kt` | spatial and timing anomaly signals |
| `diagnostics/EventSequenceValidator.kt` | route-event sequence checks |
| `diagnostics/DiagnosticModels.kt` | incident/sample entities and types |
| `diagnostics/DiagnosticDao.kt` | diagnostic persistence queries |
| `diagnostics/DiagnosticRepository.kt` | incident lifecycle and retention |
| `diagnostics/DiagnosticCsv.kt` | diagnostic attachment serialization |
| `diagnostics/DiagnosticAlertDelivery.kt` | immediate email construction/delivery |
| `diagnostics/DiagnosticAlertWorker.kt` | one-attempt WorkManager execution |
| `diagnostics/WorkManagerDiagnosticAlertScheduler.kt` | unique alert jobs |

## 18. Testing

The repository contains JVM unit tests for configuration, UI policy, filters, scheduling, SMTP construction, report state transitions, tracking state, movement rules, diagnostic detection, and CSV output.

At the time of this reference:

- 50 Kotlin files exist under the main source tree;
- 101 JVM `@Test` cases exist;
- two Android instrumented migration tests cover Room upgrade paths.

The CI workflow runs debug unit tests, lint, and debug assembly for pull requests, manual workflow dispatch, and pushes to `main`. A direct feature-branch push is not by itself equivalent to a CI run unless the workflow is manually dispatched or a pull request is opened.

## 19. Build and release

The release script is `scripts/build-release-apk.ps1`.

It performs an offline Gradle release pipeline equivalent to:

```text
:app:testDebugUnitTest :app:lintDebug :app:assembleRelease
```

It also verifies package identity, version, signing certificate, and avoids silently overwriting an existing official artifact.

Signing configuration is private and loaded from `.signing/signing.properties` or the path supplied by `TRACKER_SIGNING_PROPERTIES`. Never commit signing stores or passwords.

### Stable field update

Use an in-place Android package replacement:

```powershell
adb install -r dist/tracking-gps-2.1.0.apk
```

Do not uninstall, clear application data, or install a downgrade. Those operations can remove the local database, tracking intent, identity fallback, PIN, and email configuration.

After updating, verify:

- package version is 2.1.0 / code 6;
- tracking returns to the prior enabled state;
- foreground notification is present;
- location permission remains granted;
- battery optimization exemption remains appropriate;
- existing Room record count is retained;
- next scheduled report exists.

## 20. Real-device validation evidence

Sensitive identifiers and coordinates are intentionally omitted.

### Samsung SM-A566B

Observed during the 2.0.3 to 2.1.0 update:

- approximately 2,100 existing route records were retained;
- package replacement completed without clearing app data;
- reboot restored foreground/high-accuracy tracking;
- a 45-second location-off test exercised the gap path;
- combined network/location interruption and recovery were exercised;
- the report worker was forced for device-side verification.

Mailbox receipt/content confirmation remains an administrator-side check.

### Samsung SM-S918B, Android 15

Observed during the 2.0.1 to 2.1.0 update:

- application data was retained;
- a 45-second mobile-data and Location interruption did not kill the foreground service;
- callback and network recovery were observed;
- reboot recovery completed without opening the app;
- service/GPS recovery was observed approximately 35–40 seconds after boot;
- reboot did not create a false STOP/START pair;
- no crash, ANR, security exception, or foreground-service exception was observed;
- diagnostic workers executed on device.

A device-side successful worker execution is not proof that a message reached the recipient mailbox. Mailbox confirmation is a separate operational check.

## 21. Known limitations and operational risks

1. **Local CSV deletion consistency:** deleting History rows does not remove or rebuild existing daily/all CSV exports.
2. **Candidate export lag:** converting an unfinished stop candidate to `TEMP_STOP` or `STOP` updates Room immediately but may not refresh the derived CSV at the same instant.
3. **No durable email outbox:** a later retry reconstructs mail from current state and current build version.
4. **Immediate diagnostic alerts are single-attempt:** a failed alert waits for inclusion in a scheduled report; it does not continuously retry.
5. **Email is the only transport:** there is no backend API acknowledgment channel. Administrators must investigate one or two missed scheduled periods.
6. **Android callback timing is not exact:** the 10-second request cadence can be delayed by the platform.
7. **Raw GPS policy:** poor accuracy and stale fixes are preserved by design, so the backend must interpret quality fields.
8. **Cleartext permission:** the manifest permits cleartext traffic and should be narrowed if legacy HTTP is no longer required.
9. **History privacy:** Status and History are deliberately view-only without login; History therefore exposes detailed coordinates to a person holding the unlocked device.
10. **Feature-branch CI:** direct pushes to this branch may need manual CI dispatch or a pull request for hosted verification.

## 22. Troubleshooting guide

### Tracking does not resume after reboot

Check, in order:

1. installed version and package identity;
2. persisted tracking-enabled state;
3. boot receiver execution;
4. foreground-service notification and process state;
5. fine/background location permissions;
6. battery optimization restrictions;
7. Location provider state;
8. diagnostic incidents around boot.

Do not clear app data during diagnosis.

### Data appears missing

Compare:

- Room row counts and timestamp ranges;
- finalized versus unfinished rows;
- `PENDING`, `RETRYING`, and `SENT` states;
- local CSV timestamps;
- GPS-gap incidents;
- event-sequence and trajectory incidents;
- service restarts and device reboot time;
- sender/recipient report schedule and fleet offset.

Remember that the compact event model stores starts, periodic moving points, and stop transitions—not every 10-second callback.

### Email login fails

Verify:

- correct sender account;
- Gmail two-step verification is enabled as required for an app password;
- a current 16-character app password was entered;
- whitespace was removed by the UI;
- network and system time are valid;
- release build includes JavaMail/MIME keep rules;
- credential test succeeded before settings were saved.

Do not ask the user to create or edit a secrets file for ordinary setup.

### Email arrives with an older version label

The label identifies the build that constructed the email. To determine whether it is a delayed old-build message, compare the deterministic Report ID, scheduled window, sent/received timestamps, attachment capture times, and device update time. Because there is no serialized outbox, do not infer capture version from the email label alone.

## 23. Security and privacy checklist

Before sharing code, logs, APKs, or documentation:

- remove Gmail addresses and app passwords;
- remove device serial numbers;
- remove raw private coordinates unless explicitly needed and authorized;
- do not commit `.signing/`, local properties, diagnostic captures, or downloaded drivers;
- verify the APK certificate fingerprint;
- use `adb install -r` for retention-safe updates;
- keep `allowBackup=false`;
- audit exported Android components and cleartext traffic;
- keep the administrator PIN out of source and documentation.

## 24. Git and repository hygiene

Expected branch:

```text
feature/periodic-email-reports
```

Local investigation directories such as `.diagnostics/` and `.driver-downloads/` are not project source and must remain untracked.

Before committing:

```powershell
git status --short
git diff --check
git diff --cached
```

Stage explicit paths only. Do not use a broad add command when local device data is present.

## 25. Verification commands

Set the repository-provided JDK and Gradle homes, then run:

```powershell
$env:JAVA_HOME = (Resolve-Path '.\.tools\jdk-17.0.20+8').Path
$env:GRADLE_USER_HOME = (Resolve-Path '.\.tools\gradle-home').Path
$env:ANDROID_USER_HOME = (Resolve-Path '.\.tools\android-home').Path
& '.\.tools\gradle-8.13\bin\gradle.bat' :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --offline --no-daemon
```

For a production artifact, use the release script and verify the resulting checksum, package, version, and signer.

## 26. Future-session start checklist

A future maintainer should begin with:

1. read this file and the newest relevant design document;
2. verify branch, HEAD, and working-tree status;
3. preserve untracked device diagnostics;
4. inspect installed device version before using ADB;
5. copy device data before destructive investigation;
6. reproduce failures with timestamps and compare Room, service, diagnostics, and mail state;
7. add or update tests before changing behavior;
8. run unit tests, lint, and assembly;
9. perform device reboot and network/location interruption tests when tracking lifecycle changes;
10. confirm mailbox delivery separately from worker execution;
11. update this reference when architecture or operational behavior changes.

## 27. Related documents

- `docs/periodic-gmail-pilot-handover.md`
- `docs/stable-apk-update-runbook.md`
- `docs/android-14-device-test-checklist.md`
- `docs/superpowers/specs/2026-08-13-continuous-adaptive-gps-tracking-design.md`
- `docs/superpowers/specs/2026-08-13-remove-cold-start-pin-design.md`
- `docs/superpowers/specs/2026-08-14-release-smtp-login-fix-design.md`
- `docs/superpowers/specs/2026-08-14-stable-manual-apk-updates-design.md`
- `docs/superpowers/specs/2026-08-17-continuous-high-accuracy-recovery-design.md`
- `docs/superpowers/specs/2026-08-17-post-update-tracking-recovery-design.md`
- `docs/superpowers/specs/2026-08-17-tracking-integrity-diagnostics-design.md`

## 28. Maintenance rule

Treat this document as the branch-level source of truth for handover. When behavior changes, update the matching implementation, tests, operational runbook if applicable, and this reference in the same change. Record verified behavior separately from assumptions, especially for Android restart timing and email mailbox delivery.
