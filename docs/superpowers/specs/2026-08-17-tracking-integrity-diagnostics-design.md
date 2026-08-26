# Tracking Integrity Diagnostics Design

## Goal

Add background diagnostics to the GPS tracker after operational use exposed missing route segments and suspicious event sequences. The app must detect gaps and suspicious data, preserve the original GPS records unchanged, and send diagnostics to the existing email recipient for backend analysis.

This release prioritizes completeness and observability. Battery optimization is explicitly out of scope.

## Product Decisions

- Continue requesting high-accuracy GPS every 10 seconds while tracking is enabled.
- Open one `GPS_GAP` incident after 30 seconds without a location callback.
- Send `GPS_GAP_OPENED` immediately and `GPS_GAP_RECOVERED` immediately when GPS returns.
- Keep the existing 2-minute moving-record cadence and the existing `START`, `PERIODIC`, `TEMP_STOP`, and `STOP` semantics.
- Diagnose suspicious GPS points and event sequences without deleting, changing, suppressing, or delaying route records.
- Send non-gap diagnostics with the next scheduled 6/12/24-hour email.
- Keep diagnostics out of the user interface. The app remains a user-facing tracker; detailed analysis belongs to the backend/admin workflow.
- Keep Gmail SMTP and the current recipient configuration. No HTTP API or backend acknowledgement is available.
- Do not introduce a general durable email outbox. Admin handles a missing one or two report periods operationally.

## Boundaries

The app reports evidence and a conservative suspicion score. It does not make the final business decision that a GPS point is invalid. The backend remains responsible for route reconstruction, distance calculation, and final data-quality classification.

SMTP success means only that Gmail accepted the message. It does not prove that the downstream backend received or processed it. Deterministic IDs support later deduplication and reconciliation, but exactly-once delivery is not possible without a backend acknowledgement.

## Architecture

### Existing movement path

`TrackingService` continues to send every location fix to `MovementDetector`. `MovementDetector` remains the only component that creates or finalizes `START`, `PERIODIC`, `TEMP_STOP`, and `STOP` actions. Its approved thresholds and persistence behavior do not change.

### TrackingIntegrityMonitor

Add a single diagnostics subsystem named `TrackingIntegrityMonitor`. It observes two inputs:

1. Every raw location callback received by `TrackingService`.
2. The movement action and resulting persisted record produced for that callback, when any.

The monitor owns:

- the 10-second health check and 30-second gap state;
- a bounded in-memory ring buffer containing the previous 60 seconds of fixes;
- collection of up to 30 seconds of fixes after a suspected anomaly;
- multi-signal trajectory analysis;
- validation of movement-event sequences, including the `TEMP_STOP` lifecycle;
- orchestration of incident persistence and immediate gap notifications.

The monitor delegates Room operations to a small `DiagnosticStore`/DAO boundary. Analysis and database implementation stay separately testable even though they form one diagnostics subsystem.

The data flow is:

`TrackingService -> MovementDetector -> TrackingIntegrityMonitor -> DiagnosticStore`

Diagnostics never feed decisions back into `MovementDetector`.

### Diagnostic storage

Add two Room entities through a non-destructive migration.

`DiagnosticIncident` contains:

- stable UUID `incidentId`;
- incident type and stable reason codes;
- `openedAt` and nullable `recoveredAt`;
- `OPEN` or `RECOVERED` lifecycle state;
- confidence score and `LOW`, `MEDIUM`, or `HIGH` confidence band;
- last known fix before the incident and first fix after recovery when available;
- detected device condition such as permission missing, Location disabled, reboot, service recovery, provider silence, or unknown;
- `evidenceComplete` flag;
- independent SMTP result fields for opened alert, recovered alert, and scheduled-report inclusion;
- attempt counts and sanitized public errors; credentials are never stored in diagnostics.

`DiagnosticSample` contains:

- `incidentId` foreign key;
- sequence and `BEFORE`, `TRIGGER`, or `AFTER` role;
- GPS capture time and app receive time;
- latitude, longitude, accuracy, and device-reported speed when present;
- derived distance, speed, and signal flags used by the detector.

The normal 10-second stream is not written continuously. Samples are copied from the ring buffer only when an incident is created. If the process dies before evidence can be copied, the recovered incident is retained with `evidenceComplete = false`.

`TrackingPreferences` adds a scalar `lastGpsCallbackAt` heartbeat. It is updated for each callback independently of the 2-minute route-record cadence. It stores no coordinates or raw samples and is used only to infer a gap across process death or reboot. Using the latest route record for this purpose is prohibited because a stationary vehicle intentionally may not create route rows.

## GPS Gap Detection

Use `SystemClock.elapsedRealtime()` for the live 30-second deadline so wall-clock and timezone changes cannot create or close a gap. Store wall-clock timestamps for reports.

While tracking is enabled:

1. Each location callback refreshes the last-callback monotonic timestamp.
2. A health check runs every 10 seconds.
3. At 30 seconds without a callback, create exactly one open `GPS_GAP` incident.
4. Enqueue one uniquely named immediate job for `GPS_GAP_OPENED` using `incidentId + phase` and WorkManager `KEEP` semantics.
5. Re-register location updates once when the gap opens.
6. If the gap remains open, check every 10 seconds but do not create more incidents or emails. Retry location registration at most once every five minutes.
7. The first later callback closes the same incident, records the total duration, captures the first recovered fix, and enqueues one `GPS_GAP_RECOVERED` job.
8. GPS recovery does not create a `START`, `STOP`, or other movement event by itself.

If permission is missing or Location is disabled, record that condition and avoid a failing tight retry loop. Recheck the condition at the five-minute recovery interval and whenever the app/service receives a relevant lifecycle event.

### Reboot and process recovery

Version 2.0.3 boot, package-replacement, and app-launch reconciliation remains authoritative. When tracking was enabled, service startup compares the dedicated `lastGpsCallbackAt` heartbeat with the current startup time. An interval of at least 30 seconds is recorded as one recovered `GPS_GAP` with reason `REBOOT_OR_PROCESS_STOP`; evidence may be incomplete. When tracking was intentionally disabled, startup creates no gap. The receiver/reconciliation path supplies the recovery cause when Android exposes it so package replacement, reboot, and ordinary process recreation can be distinguished without guessing from coordinates.

An unfinished `TEMP_STOP` continues to be restored by the existing coordinator. After the first recovered callback has been processed by `MovementDetector`, the integrity monitor verifies that the candidate was finalized or remains valid.

## Trajectory Anomaly Detection

### Incident types

- `GPS_GAP`: missing callback interval.
- `SUSPECTED_GPS_JUMP`: spatially inconsistent point or short group of points.
- `TIMESTAMP_ANOMALY`: non-monotonic, duplicate-abnormal, stale, or wall/receive-time-inconsistent GPS timestamp.
- `EVENT_SEQUENCE_ANOMALY`: invalid ordering of movement events.
- `UNRESOLVED_TEMP_STOP`: a pending stop that should have been finalized but was not.

Types are broad; stable reason codes preserve the exact triggers without proliferating email categories.

### Multi-signal assessment

No fixed maximum road speed is the sole rejection rule. The monitor evaluates:

- distance after accounting for the accuracy envelopes of neighboring fixes;
- implied segment speed versus device-reported speed;
- implied speed and acceleration versus the median of neighboring segments;
- directional continuity before and after the candidate;
- whether a candidate leaves the local trajectory and returns within the 30-second future window;
- GPS capture-time ordering and its difference from app receive time;
- accuracy as a confidence input only.

The monitor waits for the future window before finalizing non-gap spatial incidents. It creates `SUSPECTED_GPS_JUMP` only when at least two independent non-accuracy signals agree. A sustained, internally consistent high-speed sequence is not suspicious merely because its absolute speed is high. Poor accuracy alone never creates an incident.

Each incident stores the triggered signals and their measured inputs so backend logic can tune or replace the app's classification later. Low-confidence incidents are included in scheduled diagnostics but never trigger an immediate email.

### Event-sequence validation

The monitor observes persisted movement actions and the recovered movement state. It reports, but does not correct:

- `START` while a previous trip is still active;
- `TEMP_STOP` or `STOP` without an active trip;
- repeated equivalent events without a valid intervening transition;
- a `TEMP_STOP` that remains unfinished after a callback at least 120 seconds after its candidate timestamp has been processed;
- a candidate that remains unresolved after service recovery and the next callback;
- a transition whose surrounding raw fixes trigger the multi-signal spatial detector.

The normal sequences `START -> TEMP_STOP -> START` for a stop under two minutes and `START -> TEMP_STOP -> STOP` for a stop of at least two minutes must not be reported as anomalies.

## Email Delivery

### Immediate gap alerts

The opened and recovered phases are separate messages with the same `incidentId`. The subject and body include device number, phase, incident ID, event time, duration when known, reason, and app version. Each message attaches incident and available sample CSV rows for backend ingestion. The opened message contains evidence available before the gap; the recovered message contains the completed before/after evidence and last-before/first-after coordinates when available.

Each phase gets one immediate SMTP attempt. Failure does not stop tracking, loop every 10 seconds, or repeatedly generate email. The failure and sanitized reason stay on the incident for scheduled reporting.

A crash after Gmail accepts a message but before Room records success may cause a duplicate. The stable `incidentId` and phase let admin/backend identify it.

### Scheduled report

The existing ReportWorker and route CSV behavior remain intact. A scheduled run also selects diagnostics not yet included in a successful scheduled report. It sends an email when either route records or diagnostics are pending; an empty route set must not prevent a diagnostics-only report.

Every scheduled email includes a deterministic `report_id` calculated from the device identity, scheduled window, and sorted IDs of the selected route records and incidents. Retrying the same selected content produces the same ID. The subject/body also includes scheduled window, route-record count, incident count, earliest/latest capture time, app version, previous SMTP-success time, and consecutive SMTP-failure count. This gives admin/backend enough metadata to identify missing periods, duplicate content, and backlog without changing transport.

Minimal delivery telemetry persists the last attempt time, last SMTP-success time, consecutive failure count, and sanitized last failure category. A later successful email reports the previous failure telemetry. It never includes SMTP credentials or exception text that may contain secrets.

Attachments are:

- the existing route CSV when route records are pending;
- `GPS-diagnostics.csv`, one row per incident;
- `GPS-diagnostic-samples.csv`, zero or more evidence rows keyed by `incident_id`.

Immediate-alert success and scheduled-report inclusion are independent states: an incident sent immediately still appears once in the next scheduled summary. Only SMTP success marks the selected incidents as included in a scheduled report. Existing route records are marked `SENT` exactly as before.

Diagnostics cleanup failure must not block route delivery. Diagnostics export or selection failure must also leave the existing route-only report deliverable and record a sanitized error for the next diagnostic attempt.

## Retention

- Incident summaries are retained for one year.
- Evidence included successfully in a scheduled report is retained for 30 additional days, then deleted.
- Evidence not yet included is retained until successful inclusion, up to a maximum of one year.
- Cleanup uses device-local calendar boundaries consistently with existing route retention.
- Cleanup never removes route records earlier than the existing one-year policy.

## User Interface and Security

No diagnostics screen, detailed log, export button, or additional user workflow is added. Existing Status, History, Settings, and PIN behavior remain unchanged.

Diagnostics may contain coordinates and device identifiers, so they are available only to internal persistence and the configured email recipient. Logs and public error strings must exclude the Gmail password, PIN, authentication material, and full exception dumps that could contain secrets.

## Failure Isolation

- A diagnostics database failure does not stop GPS collection or route persistence.
- A route database failure remains handled by the existing tracking error path; diagnostics may record it only when safe.
- An immediate SMTP failure does not affect TrackingService.
- A diagnostics attachment failure does not discard route records or mark diagnostics as reported.
- A location-registration failure keeps the gap open and follows the five-minute recovery cadence.
- Time or timezone changes do not alter an already measured gap duration because live timing is monotonic.

## Testing

### Unit and database tests

- 29 seconds without a callback does not open a gap; 30 seconds opens one.
- Repeated 10-second health checks create no duplicate incident or immediate job.
- Recovery closes the matching incident and calculates duration correctly.
- Wall-clock changes do not affect the monotonic live threshold.
- Poor accuracy alone creates no incident.
- A consistent high-speed trajectory creates no jump incident.
- An isolated jump and return creates one incident after future evidence arrives.
- Normal short and long stop sequences create no event anomaly.
- Every invalid sequence listed above creates the expected stable reason code.
- Diagnostics never modify or suppress the corresponding route records.
- Room migration preserves all existing data.
- Summary and evidence cleanup obey one-year and 30-day boundaries.
- Immediate SMTP failure remains pending for scheduled inclusion.
- Equivalent scheduled selections produce the same deterministic `report_id`; a changed selection produces a different ID.
- Successful and failed SMTP attempts update minimal delivery telemetry without exposing secrets.
- A diagnostics-only scheduled run sends; SMTP failure marks neither route nor diagnostics as sent.
- Diagnostics export failure does not prevent a route-only report attempt.

### Connected Samsung verification

- Disable Location for more than 30 seconds and verify one `GPS_GAP_OPENED` email.
- Re-enable Location and verify one `GPS_GAP_RECOVERED` email with the same incident ID.
- Remove network access during a gap and verify tracking remains active and the incident appears in the next successful scheduled report.
- Reboot with tracking enabled and verify service recovery plus one inferred interruption incident.
- Run a real journey and review false positives, including normal `TEMP_STOP` and `STOP` behavior.
- Confirm high-accuracy requests remain at 10 seconds and moving route records remain at two minutes.
- Confirm the user interface contains no diagnostics feature.

## Completion Criteria

- Each continuous missing-GPS interval creates one incident with opened and recovered phases.
- Immediate alerts are attempted without destabilizing tracking.
- Scheduled email contains pending diagnostic summary and evidence alongside route data.
- Original GPS records and movement semantics remain unchanged.
- Diagnostics survive process/service recovery where persistence is available.
- No new user/admin action is required after reboot or package update.
- Unit tests, Room migration tests, lint, build, and connected-device checks pass.

## Out of Scope

- Battery-use reduction or adaptive lowering of GPS priority.
- Backend API, delivery acknowledgement, or exactly-once email guarantees.
- Backend route correction, map matching, or official distance calculation.
- A diagnostics UI or user-facing technical controls.
- Replacing CSV attachments with JSON.
- A general-purpose durable email outbox.
