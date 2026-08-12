# Periodic Gmail Location Report Design

## Purpose

Replace the prototype's continuous OsmAnd/server upload workflow with scheduled location reports sent directly from the Android app through a dedicated Gmail account. The app supports Android 10 and newer, retains local history, and remains useful when network service is unavailable.

This is an internal pilot for 100 numbered devices. The accepted security trade-off is that a shared Gmail App Password is present in the installed APK and can be replaced manually by an administrator.

## Scope

The pilot includes:

- Android 10+ (`minSdk 29`).
- One automatically generated, immutable Device ID per installation.
- One administrator-assigned device number from `001` through `100`.
- Scheduled reporting every 6, 12, or 24 hours.
- Direct Gmail SMTP delivery with a dedicated account and 16-character App Password.
- Local Room history and daily CSV backup/export.
- Automatic retry by including unsent records in the next scheduled email.
- PIN-gated application access and configuration, with test-build default PIN `18758691`.
- Permission recovery actions and schedule restoration after device reboot.

The pilot does not include Traccar, the internal GPS receiver, Cloudflare Tunnel, a mail relay, OAuth, real-time tracking, or official app-store distribution.

## User Experience

The test build uses the default administrator PIN `18758691`. The first launch is an administrator provisioning session: the administrator unlocks with that PIN and completes the required settings before tracking can start. The app contains a verifier for the default PIN rather than storing it as plaintext; a changed PIN is stored as a salted password hash. Every cold app launch opens the PIN screen. One successful unlock lasts until the app process is closed. After successful entry, the main screen contains only:

- current tracking state;
- current permission/GPS readiness;
- `Cap quyen thiet bi` when action is required;
- `Bat dau theo doi` or `Dung theo doi`;
- last captured location time;
- last email result and next expected reporting window.

The permission action opens the relevant Android permission, location, notification, or battery settings screen when Android no longer permits an in-app prompt.

An administrator settings screen, reached after PIN unlock, contains:

- device number `001` through `100`;
- recipient email address;
- interval selector: 6, 12, or 24 hours;
- Gmail sender address;
- masked 16-character Gmail App Password;
- `Luu va kiem tra` for SMTP validation;
- PIN change.

The generated Device ID is visible for support and is never editable. Gmail credentials are not shown on the normal status screen.

## Scheduling

Reporting is anchored to local calendar time in the device's current time zone:

- 6 hours: 00:00, 06:00, 12:00, and 18:00;
- 12 hours: 00:00 and 12:00;
- 24 hours: 00:00.

Devices are distributed deterministically across the 59 minutes following each anchor. For device number `n` in a 100-device fleet, the offset is `(n - 1) * 59 minutes / 99`. The same device keeps the same offset at every anchor.

A unique one-time WorkManager request is scheduled for the next calculated anchor plus offset. After every execution, reboot, time-zone change, clock change, interval change, or delayed run, the app recalculates from calendar anchors. It does not schedule the next report relative to the delayed completion time, which prevents cumulative drift.

WorkManager is inexact under Doze and vendor power management. The UI describes the result as an expected reporting window, not an exact alarm. The app never claims guaranteed delivery at a precise minute.

## Data Capture And Storage

When scheduled work runs, the app requests one current location, reads battery percentage, and calculates elapsed tracking time from the latest successful `Bat dau theo doi` action. Stopping and restarting tracking begins a new elapsed-time session.

Before any network attempt, the app writes a Room history record containing:

- monotonically increasing local record number;
- administrator device number;
- immutable Device ID;
- capture timestamp with UTC offset and time-zone ID;
- latitude and longitude;
- accuracy in metres when available;
- battery percentage;
- elapsed tracking duration;
- delivery state and attempt metadata.

Delivery states are `PENDING`, `SENT`, and `RETRYING`. Authentication or network errors do not create a terminal `FAILED` state because records remain eligible for a later retry. The UI presents the most recent failure reason separately.

Room is the source of truth. Sent rows are retained as history rather than deleted. CSV files are derived backups, split by local capture date and named `GPS-<device-number>-YYYY-MM-DD.csv`. CSV generation occurs after the Room transaction and before SMTP delivery. The history screen can filter by date and share a selected daily file or a newly generated complete export through Android FileProvider.

## Email Format

Each scheduled execution sends at most one message. It contains the new record and every older unsent record in one CSV attachment.

Subject example:

```text
[GPS][Thiet bi 001] Bao cao 2026-08-13 00:00
```

The body contains device number, Device ID, send time, new-record count, backlog count, latest Google Maps link, and app version. The attachment columns are:

```text
record_number,device_number,device_id,captured_at,timezone,latitude,longitude,accuracy_m,battery_percent,tracked_duration,delivery_state
```

The app does not send phone number, contacts, Google account profile data, or other personal data. A record is marked `SENT` only after the SMTP server accepts the message. All attached pending records receive the same successful delivery timestamp.

## Gmail Authentication

The dedicated Gmail account uses Google's SMTP service over TLS and a Gmail App Password. Google requires 2-Step Verification to create an App Password; phones do not perform an interactive second-factor check during SMTP delivery.

Initial sender credentials are injected at build time from secrets outside source control. They must not appear in source files, sample configuration, documentation values, Git history, build logs, Room, CSV, email content, or Logcat. The accepted limitation is that a capable person can recover embedded secrets from an APK.

An administrator may paste a replacement App Password. Spaces are removed and the resulting value must contain 16 characters. `Luu va kiem tra` authenticates before activating the replacement; failure retains the previous credential. Runtime credentials use Android Keystore-backed encrypted preferences and are excluded from Android backup. Authentication errors shown to users never include a credential.

With 100 devices and a six-hour minimum interval, the fleet sends at most 400 scheduled messages per 24 hours before retries. Backlog records are batched, not sent as individual messages. This stays below the documented 500-message daily ceiling for a personal Gmail account while retaining operational headroom; Gmail can still rate-limit or suspend automated sending, so the pilot must monitor delivery results.

## Permissions And Restart Behaviour

The app requests fine location first and background location through the Android-version-appropriate second step. Android 13+ notification permission is requested for operational failure notifications. No broad storage permission is used.

Tracking cannot bypass Android or device-vendor restrictions. WorkManager persists scheduled work across normal reboot. A boot receiver only restores missing work when tracking was enabled; it does not start a continuous location foreground service. The status screen clearly reports missing permissions, disabled GPS, stopped scheduling, and the last successful capture/send times.

## Failure Handling

- No network, DNS, TLS, timeout, or temporary SMTP failure: retain records and schedule the next anchored run.
- Gmail authentication failure: retain records, notify the administrator, and allow App Password replacement.
- Missing permission or disabled location: record the operational error without fabricating a location and direct the administrator to the correct setting.
- Location timeout: retain an error event, show it in status/history, and try again at the next anchor.
- Process death, reboot, time-zone change, or clock change: reconcile exactly one future work request.
- Duplicate worker execution: serialize report creation and use stable record IDs so a record is not intentionally attached twice in concurrent sends.
- CSV write failure: do not attempt email for that batch because local backup must precede delivery.

Missing data does not imply an accident. The app reports only verified technical state and timestamps.

## Migration From The Current Prototype

Reusable parts are the generated Device ID, Room foundation, encrypted preferences pattern, Compose navigation, permission policy, and WorkManager dependency.

The following current behaviour is replaced:

- continuous `LocationForegroundService` capture;
- OsmAnd request/client and connection diagnostics;
- endpoint profiles, TLS modes, certificate import, and server tokens;
- queue logic that deletes rows after upload;
- receiver/Quick Tunnel operational dependency.

Existing installed prototype data is pilot-only. The new database migration preserves location rows where practical, assigns them a legacy/imported state, and never treats their prior server-upload status as Gmail delivery confirmation.

## Verification And Acceptance

Automated tests cover:

- next-anchor calculation for 6/12/24 hours, device offsets, midnight, DST/time-zone changes, and delayed execution;
- CSV escaping and deterministic column order;
- Room delivery-state transitions and retained sent history;
- batching and retry without one-message-per-record amplification;
- App Password normalization, validation, failed replacement, and redacted errors;
- permission-state decisions and reboot reconciliation;
- PIN gating and settings validation.

Android lint, unit tests, and debug APK assembly must pass. Manual acceptance covers Android 10, 12, and 14+, reboot, Doze/battery restriction, revoked permissions, disabled GPS, offline backlog recovery, invalid/replaced App Password, all three intervals, devices `001` and `100`, history filtering, and CSV sharing.

## Definition Of Done

- The APK installs and runs on Android 10+.
- Admin PIN gates application access and all configuration.
- The normal screen exposes only tracking, readiness, permission recovery, and operational status.
- Device number and immutable Device ID appear in history, CSV, and email.
- Reports remain anchored near 00:00 and the corresponding 6/12-hour anchors, distributed over the approved 59-minute window.
- Room and CSV are written before SMTP; outages do not lose captured records.
- A future successful message batches and clears the delivery backlog without deleting history.
- Gmail App Password can be replaced and validated without rebuilding the APK.
- Gmail credentials and changed PIN values are absent from repository content, logs, exports, and user-visible errors. The documented test-build default PIN is intentionally public and is not treated as a security boundary.
- The app clearly reports Android scheduling limitations and technical interruptions.
