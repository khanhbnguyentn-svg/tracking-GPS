# Android SET 3.0 Phase 2 Black-Box Design

**Date:** 2026-08-26  
**Status:** Approved in design review  
**Branch:** `feature/android-set-3.0-design`  
**Target build:** `3.1.0 (8)`

## 1. Purpose and authority

Phase 2 turns the Phase 1 encrypted platform foundation into an always-on GPS black box. It creates the authoritative encrypted raw store, continuously requests high-accuracy location, records truthful ten-second evidence, derives local movement and health state without modifying raw data, and recovers tracking after ordinary Android lifecycle interruptions.

This design implements the Phase 2 scope in:

- `SYSTEM_REQUIREMENT_SPECIFICATION.md`;
- `ANDROID_TECHNICAL_BUILD_SPEC`;
- `docs/superpowers/specs/2026-08-25-android-set-3.0-design.md`;
- `docs/superpowers/plans/2026-08-25-android-set-3.0-roadmap.md`.

Where this document adds detail, it must preserve the behavior and safety boundaries of those authorities. Any discovered conflict must be resolved in the SRS, technical specification, parent design, and this document before implementation continues.

## 2. Approved delivery boundary

Phase 2 includes:

- the production SQLCipher Room database for black-box data;
- boot sessions, exact raw GPS, tracking incidents, movement events, sequence allocation, and cleanup audit;
- Fused Location Provider with continuous high-accuracy requests;
- a thin always-on foreground tracking service;
- app-launch, boot, package-replacement, and process-restart reconciliation;
- a reusable minimal permission onboarding and repair flow;
- ordinary ten-second persistence gating;
- boundary-capture infrastructure for later Trip Start/End consumers;
- TEMP_STOP and Resume derivation;
- GPS callback-gap detection and local recovery;
- one-year retention, storage telemetry, warnings, and protected whole-day cleanup;
- a minimal Phase 2 status Home and persistent tracking notification;
- automated and physical-device acceptance for this scope.

Phase 2 does not include:

- Trip, Journey, Fixed User, Single Trip, vehicle, PIN, calendar, History, confirmation, Adjustment, or expense domain flows;
- email packaging, queueing, Gmail gateways, or backend delivery;
- the complete production Home or Maintenance UI;
- normal transmission of any GPS data;
- a native `LocationManager` location source running beside Fused;
- automatic Trip creation from movement.

Phase 2 may be installed once as a clean install on the SET to establish the production raw schema. Starting with `3.1.0 (8)`, every later build must update in place and prove retention of Device ID, tracking expectation, raw row count, raw sequence, and encrypted database accessibility.

## 3. Architecture

Phase 2 uses small vertical components behind interfaces. `AlwaysOnTrackingService` owns lifecycle orchestration but contains no storage or movement policy.

```text
Permission UI / Reconcilers
            |
            v
AlwaysOnTrackingService
            |
            v
   FusedLocationSource  ----> TrackingHealthMonitor
            |
            v
    RawLocationRecorder ----> SQLCipher Room
            |
            +-----------> MovementStateMachine

StorageMonitor ---------> RetentionCoordinator
BoundaryCaptureManager -> Fused high-accuracy burst -> SQLCipher Room
```

Required boundaries are:

- `LocationSource`: register/remove ordinary and boundary location requests;
- `RawLocationRepository`: deterministic gating, sequence allocation, and transactional persistence;
- `MovementClassifier`: pure transition logic over committed raw samples;
- `TrackingHealthMonitor`: pure callback-gap and recovery policy;
- `TrackingReconciler`: determine whether and how tracking should be running;
- `StorageProbe`: app-visible filesystem and database sizing;
- `ProtectedRawDayResolver`: identify business days that cleanup must preserve;
- `RetentionCoordinator`: apply one-year and critical cleanup policy.

Production uses Fused Location Provider. Tests substitute deterministic fakes without Android or Google Play dependencies.

## 4. Encrypted database

Room with SQLCipher is the authoritative local data store. Daily raw data means rows indexed and managed by business day inside Room; Phase 2 does not create duplicate daily CSV or plaintext raw files.

The Phase 1 random database passphrase and Android Keystore wrapping remain authoritative. Database creation enables WAL, foreign keys, and incremental auto-vacuum. No destructive fallback is permitted. An unwrap or open failure enters a blocking recovery state while preserving all files.

### 4.1 Phase 2 entities

The production database contains:

- `device_identity`;
- `app_configuration`;
- `boot_session`;
- `gps_raw_sample`;
- `tracking_incident`;
- `movement_event`;
- `sequence_state`;
- `storage_cleanup_audit`.

`gps_raw_sample` preserves:

- a globally increasing unique sequence;
- source UTC timestamp and captured device offset;
- `elapsedRealtimeNanos`;
- latitude and longitude;
- altitude when available;
- horizontal and vertical accuracy when available;
- speed and speed accuracy when available;
- bearing and bearing accuracy when available;
- provider and mock flag;
- boot session;
- sample kind: `ORDINARY`, `START_BOUNDARY`, or `END_BOUNDARY`.

All nullable source fields remain nullable. Missing values are not synthesized.

Phase 2 intentionally has no Trip or vehicle tables. Phase 3 adds the approved nullable domain references and foreign keys through a tested Room migration. That migration may rebuild affected tables, but it must retain every Phase 2 raw sequence and value exactly.

### 4.2 Atomic write path

An ordinary insert transaction:

1. verifies the monotonic window is not already represented;
2. allocates the next sequence through `sequence_state`;
3. inserts the immutable raw row;
4. commits before the sample is offered to movement classification.

Writes are serialized inside the repository. Callers never calculate sequence values. A failed transaction creates no partial raw row or sequence advance.

## 5. Location source and sampling

Phase 2 uses the field-tested `FusedLocationProviderClient` path from Android 2.1.0, but does not copy the pilot service architecture.

- Priority is always `PRIORITY_HIGH_ACCURACY`.
- Requested interval and minimum update interval are approximately 10,000 milliseconds.
- Activity Recognition is optional supplementary evidence and never lowers or gates GPS accuracy.
- `LocationManager` is used only to inspect whether system Location/providers are enabled.
- Google Play Services failure is a degraded condition: record/report locally, retry safely, and never crash the process.
- No simultaneous native location fallback is included in Phase 2 because duplicate sources would complicate ordering, gating, and incident interpretation without field evidence that the fleet needs it.

For each `LocationResult`:

1. note callback receipt for health monitoring;
2. sort contained locations by `elapsedRealtimeNanos`;
3. offer each location to `RawLocationRecorder`;
4. persist the first eligible sample not already represented in each monotonic ten-second window;
5. pass only successfully committed ordinary rows to movement classification.

A batched callback spanning multiple ten-second windows may produce one row per represented window. Epoch-clock changes do not alter monotonic gating. Accuracy, age, or suspicious geometry never causes source values to be rewritten.

## 6. Boundary capture foundation

`BoundaryCaptureManager` is implemented and tested in Phase 2; Phase 3 supplies the Trip Start/End action.

For a boundary request it:

1. records action UTC and monotonic time immediately;
2. starts a separate Fused `PRIORITY_HIGH_ACCURACY` request at approximately one second;
3. waits no longer than ten seconds;
4. accepts the first valid location whose monotonic timestamp is not earlier than the action;
5. inserts that exact location as `START_BOUNDARY` or `END_BOUNDARY` regardless of the ordinary ten-second window;
6. returns capture time, latency, accuracy, raw sequence, or `BOUNDARY_GPS_UNAVAILABLE`.

Boundary requests do not block the UI action and do not suspend ordinary tracking. Concurrent requests are keyed and independently finalized without allowing duplicate completion. Historical Trips never create past boundary fixes.

## 7. Movement derivation

Movement is derived local state. It never updates or deletes a raw row and is omitted from normal Trip email.

States are `MOVING`, `STOP_CANDIDATE`, and `TEMP_STOP`.

- Start a candidate when effective speed is below 1 m/s.
- Anchor the candidate to the first committed raw sample.
- Cancel before confirmation if the speed/radius condition fails.
- Confirm after at least 60 monotonic seconds when all observed committed samples remain within 20 metres of the anchor.
- Store `TEMP_STOP_STARTED` with the anchor effective time, separate confirmation time, first raw sequence, confirming raw sequence, and algorithm version.
- End TEMP_STOP after two consecutive moving samples whose effective speed is at least 1 m/s or which leave the 20-metre radius.
- Store Resume effective time at the first of the two confirmed moving samples.

Effective speed is the untouched source speed when present. When source speed is absent, classification may calculate a fallback from Haversine displacement and monotonic elapsed time. The calculation subtracts the two reported horizontal-accuracy radii before deriving speed, with effective displacement floored at zero. This fallback exists only in bounded movement state; `gps_raw_sample.speed_mps` remains null. Invalid coordinates or time order produce `UNKNOWN`, and that observation cannot confirm the 60-second condition.

The state machine keeps only a bounded rolling state. Persisted movement events include an algorithm version so later consumers can distinguish classifications.

## 8. Tracking health and incidents

Health uses callback receipt monotonic time, not raw persistence time.

- At 30 seconds without a callback while tracking is expected, open one `GPS_GAP` incident and request forced Fused re-registration.
- Re-registration retries no more often than once per five minutes.
- The next callback closes the same open incident.
- Missing permission, disabled provider, service-start rejection, persistence failure, and clock change use distinct typed/detail states rather than fabricating a GPS gap.
- Phase 2 exposes incidents only through local notification/status. Phase 3 and Phase 4 later add active-Trip timing and opened/recovered email intents.
- A gap remains truthful through process death and reboot. No coordinates are interpolated.

If storage is too full to persist an incident, an encrypted preference marker stores only type and timing, never coordinates. Once Room writes recover, the repository records the unavailable interval and clears the marker.

## 9. Permission onboarding and service lifecycle

The Phase 2 Home contains a reusable setup/repair flow. It requests permissions only after an explicit user action and does not loop prompts.

The sequence is:

1. request precise foreground location while the Activity is visible;
2. request notifications on API 33 and later;
3. explain the black-box need for background location;
4. on Android 11 and later, open app permission settings for the user to select the localized “allow all the time” option;
5. verify precise and background location plus enabled system Location;
6. persist `TRACKING_EXPECTED` and start the foreground service while the Activity is visible.

Denial leaves the app usable in a degraded state with one deliberate **Khắc phục** action. Driver/User receives no tracking-stop action.

`AlwaysOnTrackingService`:

- is declared as foreground service type `location`;
- enters foreground immediately with a persistent notification;
- returns `START_STICKY`;
- owns one ordinary Fused registration and the serialized processing scope;
- delegates every policy and persistence decision to its components;
- catches sanitized operational failures and keeps health/reconciliation active where Android permits.

`TrackingReconciler` runs from app launch, `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, and service recreation. After reboot it waits for the user profile to be unlocked before opening SQLCipher and creating the new boot session. With background location already granted, the location foreground service may be restored from the boot/package broadcasts. A platform rejection is recorded and retried only from a later Android-permitted reconciliation point.

## 10. Storage, retention, and write failure

`StorageProbe` reports total/free bytes, database/WAL/staging bytes, raw row count, oldest/newest raw time, retained business days, status, and cleanup count.

The probe runs:

- at service startup;
- on app launch;
- every 15 minutes while tracking runs;
- immediately after a storage-related database write failure.

Storage status is:

- `NORMAL`: at least 500 MiB free;
- `WARNING`: below 500 MiB;
- `CRITICAL`: below 200 MiB.

Normal retention deletes whole business days older than one year. Critical cleanup deletes the oldest eligible whole business day, reclaims database pages through a bounded WAL checkpoint/incremental-vacuum sequence, probes storage again, and repeats only until free space reaches at least 200 MiB. It does not attempt to restore 500 MiB.

Every deletion calls `ProtectedRawDayResolver`. The Phase 2 resolver returns no protected Trip days because the Trip domain does not yet exist. Phase 3 extends it to protect active/unfinished Trips, unsent snapshots, pending confirmations, unresolved Adjustments, and active Recovery without changing cleanup policy.

Cleanup removes derived movement events that reference a deleted raw range. It records trigger, business-day range, raw count, bytes before/after, and timestamp. Cleanup never operates row by row and never deletes a protected day.

On a raw write failure caused by storage pressure:

1. store a coordinate-free encrypted recovery marker;
2. run critical cleanup;
3. retry the raw insert once;
4. if it still fails, keep the service and callbacks alive and show a severe local warning;
5. record the unavailable persistence interval when Room becomes writable again.

The app never reports a failed raw insert as successfully stored.

## 11. Phase 2 user-visible surface

The minimal Home shows:

- tracking running or missing-condition state;
- GPS/system Location state;
- latest successfully stored raw time;
- today’s raw row count;
- permission status with **Thiết lập theo dõi** or **Khắc phục**;
- storage state and free capacity;
- app version.

It does not show coordinates or Device ID. It does not expose fake Fixed User, Single Trip, Driver, Maintenance, mail, or Trip behavior before those phases exist.

The persistent notification says **SET đang theo dõi GPS**, contains no coordinates or Device ID, has no Stop action, and opens the app when tapped.

## 12. Version and update contract

Phase 2 increments the app from `3.0.0 (7)` to `3.1.0 (8)`. The clean install approved for introducing the production raw schema is recorded in the device acceptance log.

Every subsequent APK must:

- have a greater version code;
- use the approved update-compatible signing identity;
- install with `adb install -r` or the equivalent Package Installer update path;
- preserve stable Device ID, tracking expectation, raw rows, sequences, incidents, and database key access;
- never use destructive migration or automatic database deletion.

## 13. Verification strategy

### 13.1 JVM tests

Cover:

- ten-second gating across boundaries, batches, duplicate delivery, clock changes, and boot sessions;
- exact preservation of every nullable and non-null location field;
- atomic sequence allocation and rollback;
- TEMP_STOP 60-second/20-metre rules and two-sample Resume;
- raw-speed precedence, derived-speed fallback, accuracy subtraction, and `UNKNOWN` handling;
- singleton GPS-gap transitions and five-minute registration throttling;
- permission/reconciliation state transitions;
- retention cutoff, protected-day selection, 500/200 MiB states, and bounded retry;
- boundary timeout, fresh-fix acceptance, and concurrent-request isolation.

### 13.2 Instrumented tests

Cover:

- SQLCipher production schema creation and reopen;
- foreign-key/WAL/incremental-vacuum configuration;
- encrypted transaction rollback and sequence retention;
- Room migration retention beginning with the first post-Phase-2 schema change;
- foreground service creation with declared type and permissions;
- boundary capture through a controllable location adapter;
- cleanup reclaim behavior without filling the physical SET filesystem.

### 13.3 Physical Samsung acceptance

On the connected Samsung SET and later representative models:

- complete permission onboarding without ADB permission grants;
- verify persistent notification and Fused `HIGH_ACCURACY` request at about ten seconds;
- compare raw count/timestamps while stationary and moving;
- lock the screen and verify continued storage;
- swipe the app from recents and verify tracking remains expected/running;
- disable Location for at least 45 seconds and verify one gap plus recovery;
- exercise process replacement without data loss;
- reboot, unlock once, and verify boot session plus automatic tracking recovery;
- update to a greater version code in place and verify Device ID, raw count, sequence, and database access;
- perform real driving/stopping checks for cadence, 60-second TEMP_STOP, and two-sample Resume;
- run the boundary test harness and verify exact capture/action timing or explicit unavailable result;
- confirm no crash, ANR, `SecurityException`, or foreground-service exception.

Full 3,153,600-row capacity qualification and the complete API 26/29/31/33/35-36 matrix remain Phase 6 gates. Phase 2 must nevertheless include bounded performance tests and schema/index review that make those later tests feasible.

## 14. Acceptance contract

Phase 2 is complete only when:

- build `3.1.0 (8)` clean-installs once on the designated SET;
- every automatic test relevant to Phase 2 passes with fresh evidence;
- the physical Samsung acceptance above passes or each remaining device-only item is explicitly recorded as pending rather than implied complete;
- raw samples are exact, monotonic-window gated, encrypted, and independent of Trip creation;
- tracking remains continuously high accuracy after setup where Android permits;
- missing callbacks and failed persistence remain explicit gaps;
- no Driver/User stop action exists;
- the working tree contains no coordinate dump, database, signing key, PIN, credential, or device-private identifier;
- documentation and traceability are updated with any implementation-discovered behavior change.
