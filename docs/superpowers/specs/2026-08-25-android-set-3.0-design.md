# Android SET 3.0 Design

**Date:** 2026-08-25

**Status:** Approved design baseline pending final document review

**Release:** `3.0.0` (`versionCode 7`)

**Application ID:** `com.internal.tracker`
**Scope:** Android SET and email contract fixtures only

## 1. Purpose

Android SET 3.0 is a clean-install replacement for the email pilot. The device acts as an always-on GPS black box while keeping Trip creation as an explicit human business action. It must preserve raw evidence even when Driver or User actions are missing, but it must not automatically turn private vehicle movement into business Trips or send private movement to the backend.

The release is a clean modular monolith. It reuses only isolated, verified parts of the existing email branch, such as SMTP transport, WorkManager patterns, release signing utilities, and selected diagnostics.

Production backend, web portal, AI review, and Vendor reconciliation are separate later phases. This phase defines stable package contracts and golden fixtures for them.

## 2. Fixed decisions

- Clean install is permitted for 3.0.0; no migration from the 2.1.0 database is required.
- Keep `com.internal.tracker` and the existing release signing identity.
- Use Room schema version 1 for the new 3.x database line.
- Future 3.x releases must use non-destructive migrations and preserve data.
- Business timezone is `Asia/Ho_Chi_Minh`.
- Android baseline is min SDK 26, compile SDK 36, target SDK 36, and Java 17.
- UI uses Jetpack Compose, Material 3, Navigation Compose, ViewModel, StateFlow, and coroutines.
- The app is a single Gradle application module organized into strongly bounded packages.
- No production backend, web portal, or AI implementation is included in this phase.

## 3. Architecture

```text
Compose UI
    |
ViewModel / UI state
    |
Domain use cases and state machines
    |
Repositories
    |
SQLCipher Room / Keystore / staged files / WorkManager / SMTP
```

Suggested source boundaries:

```text
core/database        Room, transactions, migrations
core/security        Keystore, encryption, HMAC, PIN primitives
core/time            business clock and business calendar
core/device          stable Device ID and device telemetry
core/audit           transactional audit contracts
tracking             always-on service, raw recorder, health, movement
trip                 Journey, Trip segment, historical creation, boundaries
identity             Fixed User, Daily PIN, Master Package
vehicle              declaration and binding history
expense              editing, deadlines, versioned updates
mail                 packaging, persistent queue, SMTP, gateway policy
maintenance          diagnostics, PIN reveal, recovery, pause, reset
ui                    feature screens and navigation
```

UI code must not call a DAO or SMTP sender directly. Clock, UUID generation, location capture, file staging, and SMTP transport use interfaces so tests are deterministic. Business logic and serialization remain Android-free Kotlin where practical.

Room is authoritative. Derived files are disposable. A large single composition root is avoided; manual dependency containers are scoped by feature.

## 4. Stable device identity

`device_id` identifies the physical SET as the source of all records and helps backend operators detect fleet-wide or device-specific failures.

- Derive Device ID from Android ID and package identity using SHA-256.
- Preserve it in encrypted preferences and Room.
- Do not use IMEI or hardware serial.
- Include it in every outbound message and recovery export.
- A normal reinstall with the same package/signing identity should retain the same derived identity when Android ID remains stable.
- A factory reset or mainboard change can create a new ID. PIC may link the new identity to the old device lineage with an audited backend action.
- Reset/Re-provision inside the app must not change Device ID.

## 5. Always-on black-box tracking

### 5.1 Lifecycle

Tracking is independent of Trip state.

- Start a location foreground service after initial permission setup.
- Reconcile it after boot, package replacement, and app launch.
- Use a persistent notification without a Driver/User stop action.
- Swiping the app from recents must not stop tracking.
- Only Maintenance Mode can pause tracking, with re-authentication, reason, audit, and optional automatic resume time.
- An indefinite maintenance pause remains prominently visible until resumed.
- Android force-stop or permission revocation cannot be bypassed. The app records the condition when it becomes active again.

### 5.2 Permissions and degraded operation

Required or requested permissions are precise location, background location, notifications where applicable, foreground-service location, boot completed, and Internet. Activity Recognition is optional.

Missing permission, disabled Location, or unavailable GPS must not block Trip Start/End after the user acknowledges the warning. The app records a gap and evidence warning. Permission prompts are not shown in a loop; the UI exposes a deliberate **Khac phuc** action.

### 5.3 Raw sampling

- Persist at most one ordinary raw sample in each 10-second sampling window.
- Raw rows are append-only and never rounded or rewritten.
- Duplicate or burst callbacks do not inflate ordinary sample counts.
- Start and End boundary capture may add extra raw rows outside the ordinary 10-second cap.
- Missing callbacks create gaps; SET never fabricates or interpolates coordinates.

Raw fields are:

```text
sequence_number
captured_at_utc
captured_at_device_offset
elapsed_realtime_nanos
latitude
longitude
altitude_m
horizontal_accuracy_m
vertical_accuracy_m
speed_mps
speed_accuracy_mps
bearing_deg
bearing_accuracy_deg
provider
is_mock
boot_session_id
```

Unavailable values are null, not zero. Sequence numbers are persisted and monotonic. `elapsed_realtime_nanos` and `boot_session_id` distinguish clock changes and reboot boundaries.

### 5.4 Boundary capture

Start and End actions record the business action timestamp immediately and do not block on GPS.

- Start a high-accuracy burst at about one-second cadence for at most ten seconds.
- Store the first suitable fresh fix as a `START_BOUNDARY` or `END_BOUNDARY` raw sample.
- Preserve action time separately from capture time, latency, and accuracy.
- If no fix arrives, complete the action and record `BOUNDARY_GPS_UNAVAILABLE`.
- Historical Trips cannot obtain a past boundary fix; they use existing raw samples only.

### 5.5 Movement classification

Movement classification is derived local data and never changes raw rows.

TEMP_STOP starts when:

- speed remains below 1 m/s;
- all samples remain within 20 metres of the initial candidate;
- the condition lasts at least 60 seconds.

After confirmation, `TEMP_STOP_STARTED` uses the first candidate sample time and also stores `confirmed_at`. A failed candidate creates no TEMP_STOP event.

TEMP_STOP ends after two consecutive moving samples where speed is at least 1 m/s or the device leaves the 20-metre radius. `TEMP_STOP_ENDED/RESUME` uses the first confirmed moving sample time.

Movement events support local UI only and are omitted from normal Trip email. Backend reconstructs movement from raw GPS.

### 5.6 GPS health

A 30-second callback absence opens a local GPS-gap incident and triggers location-registration recovery.

- Outside an active Trip, show only device notification/UI warnings; do not email backend.
- During a Trip, a gap lasting five minutes sends `TRIP_GPS_GAP_ALERT`.
- Recovery sends `TRIP_GPS_GAP_RECOVERED` if the opened alert was sent, even if the Trip has ended.
- A gap that begins outside a Trip and remains open at Start begins its five-minute email threshold from Trip Start.
- Alerts contain device, Trip, time, service, permission, and provider status, but no private coordinates outside the Trip.
- One incident sends at most one opened and one recovered message.

Trajectory anomaly, official quality filtering, route reconstruction, and official distance remain backend responsibilities. SET preserves `is_mock`, accuracy, clock, provider, and gap evidence without making official validity decisions.

## 6. Local data and storage

### 6.1 Database

Use Room with SQLCipher. Generate a random database passphrase on first run and wrap it with Android Keystore. Enable WAL. A Keystore/database opening failure enters a recovery state and must never trigger automatic database deletion.

Main entity groups are:

- black box: `gps_raw_sample`, `tracking_incident`, `movement_event`, `boot_session`;
- vehicle/journey: `vehicle_binding`, `journey`, `trip_segment`, `trip_boundary_capture`, `trip_gps_reference`, `trip_user_snapshot`, `trip_adjustment_request`;
- identity/config: `master_package_version`, `fixed_user`, `daily_pin_context`, `business_calendar`, `maintenance_pin`, `app_configuration`;
- expense/confirmation: `expense`, `expense_version`, `confirmation`, `identity_mismatch`;
- mail: `email_queue_item`, `email_transmission`, `message_snapshot`, `gateway_account`, `gateway_state`, `delivery_telemetry`;
- control: `audit_log`, `maintenance_log`, `storage_cleanup_event`, `recovery_export_record`.

Business entities use UUIDs. Row IDs are internal. At most one physical Trip segment may be active. User, vehicle, and message snapshots are immutable versions rather than mutable history.

Every business mutation and its audit record commit in the same Room transaction. External side effects are represented by durable intent/outbox state because SMTP, files, notifications, and WorkManager scheduling cannot be atomically rolled back with Room.

### 6.2 Capacity and retention

At one sample per ten seconds, the design cap is 8,640 rows/day and 3,153,600 rows/year. Plan for up to about 1 GiB/year for raw rows and indexes and about 1.5 GiB total app working space including WAL, business data, and staged packages.

- Normal raw retention is one year.
- Storage telemetry is included in every outbound package.
- Warn Driver/Admin when free storage is below 500 MiB.
- Enter critical cleanup below 200 MiB.
- Delete whole oldest raw days until free space returns to at least 200 MiB.
- Protect evidence referenced by unfinished Trips, unsent messages, pending confirmation, or unresolved adjustment.
- Record each cleanup range, sample count, freed bytes, trigger, and timestamp.
- Admin is expected to act after warnings; cleanup does not aggressively restore 500 MiB.

Telemetry includes total/free storage, database bytes, raw row count, oldest/newest raw time, retained days, pending package bytes, storage status, and cleanup count.

## 7. Vehicle declaration and switching

SET is portable and is not permanently bound to one vehicle.

- Driver declares plate number and Vendor at initial setup.
- Optional vehicle type and note may be supplied.
- The declaration remains active across days, reboot, and update until explicitly changed.
- Driver input may be inaccurate and is preserved as original evidence.
- Backend/Vendor may map or correct it without overwriting the original declaration.
- Vehicle ID is not shown in the SET UI.
- `vehicle_binding_id` is an internal ID for a declaration period, not a corporate billing Vehicle ID.
- Device ID remains the stable technical source identifier.
- Business settlement uses the Driver-declared vehicle information and its audited backend/Vendor corrections.

Outside a Trip, changing vehicle closes the old binding and opens a new binding. Reason is optional. During an active Trip, the **Doi xe** action requires a reason and atomically:

1. closes the old physical Trip segment with `VEHICLE_CHANGE_OUT`;
2. closes the old vehicle binding;
3. opens the new binding;
4. opens a new physical segment with `VEHICLE_CHANGE_IN`;
5. preserves the same logical `journey_id`, user, purpose, and confirmation context.

Each physical segment is packaged separately for the appropriate declared Vendor. Raw sampling never pauses during the transaction. A transaction failure keeps the old segment/binding active.

## 8. Identity, PINs, and Master Package

### 8.1 Fixed User

The old **Fixed Schedule** name is replaced with **Fixed User / Nguoi dung co dinh**.

- Fixed Users have company-wide full-time eligibility and can use any SET/vehicle.
- There is no Fixed Assignment or schedule.
- A single company-wide package contains all Fixed Users.
- Fixed PIN must be unique inside a package version.
- Authentication is PIN, then display name/employee ID/department, then explicit confirmation.
- Trip identity is snapshotted so later master changes do not rewrite history.

### 8.2 Daily PIN

- Daily PIN is six digits, including possible leading zero, generated securely per SET.
- It becomes effective at 00:00 in `Asia/Ho_Chi_Minh` regardless of email delivery time.
- At 00:00 SET queues a Daily PIN message for backend/PIC.
- Driver cannot view Daily PIN. PIC supplies it to the User.
- Delayed/retried email retains the scheduled date and original PIN.
- Reboot never regenerates an already issued PIN.
- A Trip started with the previous day's PIN may end after midnight.
- If it ends after 08:30, the Trip still completes but receives `PIN_VALIDITY_EXCEPTION` for PIC review.
- PIN timing rules do not block History Adjustment Requests.

### 8.3 PIN namespaces and retry

- Fixed PIN: six digits, package-provisioned.
- Daily PIN: six digits, SET-generated.
- Maintenance PIN: eight digits, package-provisioned.
- Namespaces are independent, so numeric equality across namespaces is allowed.

The first five failed attempts retry immediately. Beginning at attempt six, apply a 30-second cooldown. After ten consecutive failures, apply a five-minute cooldown. A successful authentication resets the namespace counter. Never permanently lock offline operation. Audit counts and namespace, never entered PIN values.

### 8.4 Driver-initiated confirmation

For Fixed User confirmation, User selects Fixed User, enters PIN, reviews master identity, and confirms.

For Single Trip confirmation, User selects Single Trip, enters the Daily PIN applicable to the Trip start date, enters name/employee ID/department, reviews the Trip summary, and confirms.

Daily PIN history is retained sufficiently for the 48-hour local confirmation window, but an expired PIN cannot start a new Trip. After 48 hours local confirmation is disabled and backend/PIC handles it.

Driver-declared and User-confirmed identities are stored separately. A mismatch is flagged and both versions are sent; SET never overwrites one with the other.

### 8.5 Signed Master Package

The backend later provides a package builder. PIC updates data through the portal; backend validates, increments version, builds canonical payload, encrypts, signs, stores audit, and exposes the complete file for manual distribution. There is no backend-to-SET push channel.

The company-wide package contains:

- Fixed Users and Fixed PINs;
- Business Calendar dates and working-day overrides;
- Maintenance PIN updates;
- optional Gmail gateway catalog updates;
- package/schema/key versions.

Use AES-256-GCM for payload encryption with a practical shared decryption key embedded in the APK, and RSA-PSS SHA-256 for authenticity using a backend-only private key and app-embedded public key. The shared AES key is extractable by a determined attacker and provides practical confidentiality, not hardware-bound security. Signature verification is the authoritative tamper protection.

SET verifies signature, decrypts, validates full schema/version/uniqueness, and commits atomically. Equal/older versions and any invalid record reject the whole update. Each attempt is audited.

## 9. Business Calendar and History

Driver History covers three working days, not three calendar days.

- Saturday and Sunday are excluded unless marked working-day overrides.
- Package-provided company holidays are excluded.
- On a working day, include today and two preceding working days.
- On a non-working day, include the three preceding working days.
- A Trip that starts inside the window is shown completely.
- Confirmation remains a real 48-hour window; it is not converted to working hours.
- Expense deadlines remain calendar-based.

Driver can create a Missing Trip or Adjustment from History using a maximum 48-hour interval. Raw data older than the Driver window remains in Room and is accessible only through Maintenance/Recovery.

## 10. Trip and Journey model

Trip creation is always an explicit human action. Vehicle movement never creates Candidate Trips or automatic business Trips.

The app supports:

- real-time Fixed User Trip;
- real-time Single Trip using Daily PIN and self-declared identity;
- Driver Initiated Trip with post-Trip confirmation;
- historical Missing Trip/Adjustment referencing raw GPS;
- logical Journey continuation across explicit vehicle changes.

State flow:

```text
CREATED -> IN_PROGRESS -> ENDED -> [PENDING_USER_CONFIRMATION]
        -> CONFIRMED -> COMPLETED
```

Trip state and email delivery state are independent. A completed Trip is never reopened. Corrections create versioned Adjustment Requests and preserve original Start, End, declared End, vehicle, user, expense, and GPS evidence.

Historical intervals overlapping an existing physical Trip or duplicate request are rejected. Historical selection is at most 48 hours. An active Trip reaching 48 hours produces a severe warning but is not automatically ended. Raw tracking continues and multipart packaging remains a safety fallback.

### 10.1 GPS interval privacy

Normal email includes only raw samples whose timestamps lie within the Trip interval. Do not include bracketing samples outside the interval. Manifest records offsets between Start/End and first/last included samples. A Historical Trip may naturally have up to approximately one sampling interval at each boundary; SET does not interpolate.

For a real-time Trip, preserve raw evidence through actual `end_action_time` even if `declared_end_time` is earlier.

### 10.2 Estimated Distance

UI Estimated Distance is non-authoritative. Sum Haversine distance between consecutive raw samples classified as moving. Do not bridge gaps longer than 60 seconds and exclude derived segments whose implied speed exceeds 200 km/h. Preserve all excluded raw rows. Store algorithm version and excluded-segment count. Backend calculates official distance independently.

## 11. Expense

Expense is entered after a Trip segment ends.

- Wait one hour from `end_action_time` before sending the initial Trip email.
- Expenses saved during that hour are included in the Trip package.
- After initial send, Driver changes remain local drafts until **Hoan tat expense** is pressed.
- Pressing completion creates a versioned `EXPENSE_UPDATE` snapshot.
- Multiple later completions create newer versions; backend retains history and applies the latest view.
- If unsent changes remain at the deadline, SET automatically queues the latest snapshot with source `SYSTEM_DEADLINE_SEND`.

The deadline is 00:00 on the date two calendar days after the Trip end date in `Asia/Ho_Chi_Minh`:

- End 23:30 on day 10 -> lock 00:00 on day 12.
- End 00:30 on day 11 -> lock 00:00 on day 13.

After lock, SET is read-only and changes require Adjustment/PIC. A vehicle-change Journey assigns expenses to a specific physical segment.

## 12. Email gateways

Use Gmail personal accounts with App Passwords over SMTPS `smtps.gmail.com:465`. Credentials are bundled for ease of deployment and are not strongly protected from APK reverse engineering. Never display, log, export, or copy them.

There are four Vendors. Each Vendor has two active Primary accounts and one Backup. SETs are stably distributed between the two Primaries. Backup is used only after eligible account-specific failures.

Failure policy:

- authentication/account disabled: fail over after two failed attempts;
- quota/rate limit: fail over after three failures within one hour;
- offline/DNS/timeout: retry without counting against gateway;
- TLS/system time: warn about device/infrastructure, do not fail over;
- invalid package/oversized attachment: package error, do not fail over.

After failover, continue using Backup. After 24 hours, send a small Primary health-check. A successful test returns to Primary; a failed test retains Backup and retries after another 24 hours. All checks and switches are audited. Trip operation and local recording never depend on gateway health.

Initial credentials are bundled in APK. Later catalog rotation can be delivered through the signed Master Package. Driver selects only Vendor and cannot see accounts or secrets.

## 13. Email package contract

One normal physical Trip segment produces one logical package after the one-hour delay. The ZIP contains:

```text
manifest.json
trip.csv
gps_raw.csv
expenses.csv
```

Normal email does not contain movement events or any raw GPS outside the Trip. Confirmation, Daily PIN, Expense Update, diagnostic, and health-check messages are separate, smaller message types.

Manifest includes:

- message ID/type/version/schema, created and scheduled times;
- Device ID;
- vehicle binding ID and Driver-declared plate/Vendor/type;
- app version/code, Android version, manufacturer/model;
- timezone, boot session, service/provider/permission/battery state;
- raw interval, sample/sequence counts, boundary offsets and gap counts;
- storage/database/retention telemetry;
- gateway Vendor/account role and attempt metadata;
- file sizes and SHA-256 hashes;
- `integrity_key_version` and HMAC-SHA256.

Do not include credentials, PINs except the authorized Daily PIN message, IMEI/hardware serial, or private GPS outside the Trip.

Use canonical manifest serialization. Shared HMAC secret is bundled in APK/backend for practical integrity/authentication. It shares the acknowledged extraction risk of bundled gateway secrets. HMAC failure prevents automatic import but backend retains the package for PIC investigation.

Cap ZIP attachments at 15 MiB. A normal maximum two-day Trip is safely below this cap. Multipart packaging is a defensive fallback: time-ordered parts share a message group, declare part count/ranges/hashes, and backend stages partial groups until complete. Full-year recovery prefers file/USB rather than Gmail.

## 14. Queue, idempotency, and delivery

Message priority is:

1. Daily PIN Update;
2. Post-Trip Confirmation;
3. Trip Package;
4. Expense Update;
5. Health Check/Diagnostic.

Use FIFO inside a priority. A backoff-delayed item does not block eligible later work. Do not send concurrently through one Gmail account. Apply fairness limits so low-priority items cannot starve.

Each logical package has a stable `message_id`. Automatic retries preserve message ID, version, hashes, and bytes while creating a new `transmission_id`. Manual resend of unchanged data also keeps the logical identity and creates a new transmission. Changed business data creates a new message/version rather than modifying an old package.

SMTP acceptance sets local `EMAIL_SENT`; it does not prove backend import. Delete staged ZIP after SMTP success. Preserve message snapshot metadata and immutable business versions so resend can be deterministically reconstructed. Backend compares content, retains receipt history, treats true duplicates idempotently, and never lets an older version roll back its current view.

## 15. UI design baseline

Home exposes large actions:

- Fixed User;
- Single Trip;
- Driver Area;
- Maintenance;
- Active Trip when present.

It also shows GPS, network, mail queue, and storage status plus permission/gateway/master warnings. There is no tracking stop action.

Maintenance is visible but always PIN-protected. Sensitive actions require re-authentication.

Pre-Start uses a text Current Location panel rather than Google Maps. Android Geocoder is best-effort and displays street, ward/commune, district, and province/city when available. Failure falls back to rounded display coordinates and accuracy without blocking Start. Address is derived display data and never replaces raw GPS.

History shows time and Estimated Distance rather than a route map. UI prioritizes large controls, minimal re-entry, explicit confirmation, and visible pending/error state.

## 16. Maintenance and recovery

Maintenance requires the eight-digit Maintenance PIN. It includes:

- device/GPS/storage/mail diagnostics;
- Fixed/Daily PIN reveal;
- signed Master Package import;
- encrypted selected-period/full-year recovery export;
- controlled tracking pause/resume;
- email test;
- Reset/Re-provision.

PIN reveal requires re-authentication, is masked by default, auto-hides after 30 seconds, blocks screenshots/recording with `FLAG_SECURE`, disables clipboard copy, and audits the reveal action without the PIN value. Fixed and still-relevant Daily PINs may be revealed. Maintenance PIN itself is never revealed; it is rotated by signed Master Package.

Recovery export uses a random AES-256-GCM content key. Encrypt that key with backend recovery RSA-OAEP SHA-256 public key. The backend/PIC recovery workstation alone holds the private key. Export records Device ID, range, schema/version, hashes, and audit reference. Successful export never deletes source data.

Reset requires re-authentication and a verified full recovery export. The app reads back and hashes the export before allowing final confirmation. Reset removes raw/business/mail/master/vehicle state but preserves stable Device ID, built-in gateway catalog, and a minimal reset lineage record. It recreates an empty encrypted database, resumes tracking, and requires Master import plus initial vehicle declaration.

## 17. Security and privacy

- SQLCipher protects Room at rest; Keystore wraps the database passphrase.
- `allowBackup=false`.
- Disable cleartext traffic unless a verified component requires it.
- Restrict exported Android components.
- Do not log PIN, Gmail credential, HMAC/AES secrets, full private coordinates, or recovery keys.
- Keep Driver/User GPS outside explicit Trips on SET only.
- Only Maintenance Recovery can export the full black-box timeline.
- Preserve Driver declaration and backend/Vendor correction as separate audited versions.
- Bundled Gmail, HMAC, and shared Master decryption secrets are acknowledged operational trade-offs, not hardware-grade protection.

## 18. Testing

Unit coverage includes Trip/Journey state machines, movement rules, Daily PIN timing and 08:30 exception, 48-hour confirmation, business calendar, expense deadlines/versioning, vehicle switching, historical overlap, Estimated Distance, queue priority, retry/failover/failback, serialization, HMAC, storage policy, and idempotency.

Instrumented coverage includes encrypted Room reopen, foreign keys, one-active-Trip constraint, atomic audit, retention and protected cleanup, process interruption, and representative 3,153,600-row performance/capacity tests.

Device matrix includes API 26, 29, 31, 33, and 35/36. Validate foreground lifecycle, reboot/update/process kill, permission loss, Location off/on, boundary burst, network backlog, Gmail errors, vehicle switching, clock/timezone changes, storage pressure, recovery/reset, and mailbox delivery. Worker success and mailbox receipt are separate acceptance checks.

Contract tests use golden ZIP fixtures for every message type, duplicates, out-of-order versions, multipart, corrupt hashes/HMAC, and privacy checks proving no raw GPS outside Trip and no unauthorized secret/PIN fields.

## 19. Release and verification

Release 3.0.0 uses `versionCode 7`, existing package ID, and stable release signer. The release pipeline runs unit tests, lint, and release assembly; verifies package/version/signer/checksum; and refuses silent official-artifact overwrite.

The official APK remains outside Git. Clean install is allowed for 3.0.0. Every later release must prove in-place update retention with Room, tracking intent, Device ID, Master data, queue, vehicle binding, and raw row count preserved.

## 20. Required source-document changes

Before implementation planning, update SRS and Android Technical Build Spec to reflect:

- always-on raw 10-second persistence rather than two-minute moving-only persistence;
- explicit Trip creation and private out-of-Trip GPS policy;
- Fixed User replacing Fixed Schedule and removing Fixed Assignment;
- portable SET and versioned vehicle binding/vehicle-change Journey segments;
- Daily PIN backend distribution and 08:30 exception;
- three working-day History and Business Calendar package;
- revised expense timing and updates;
- email package, priority, Gmail account topology, failover, and integrity;
- storage thresholds and telemetry;
- Current Location text panel replacing Google Maps;
- Maintenance PIN reveal, recovery, reset, and tracking-pause controls;
- clean 3.0 scope and later backend package builder.

These changes should advance both documents to version 1.2 and remove superseded or contradictory rules instead of leaving parallel interpretations.
