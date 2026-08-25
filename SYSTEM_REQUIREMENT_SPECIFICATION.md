# SYSTEM REQUIREMENT SPECIFICATION

## Vehicle Usage Management System

Android SET + On-Premise Factory Backend + WS/PIC Web Portal

| Item | Value |
|---|---|
| Version | 1.2 |
| Date | 2026-08-25 |
| Status | Approved functional baseline for Android SET 3.0 design |
| Scope | Android SET, one-way email interface, backend, web portal, AI review, Vendor reconciliation |
| Android release baseline | 3.0.0 (`versionCode 7`) |

### Version history

| Version | Change |
|---|---|
| 1.0 | Consolidated M0-M9 functional baseline. |
| 1.1 | Replaced shared sender with Vendor-specific Gmail gateways. |
| 1.2 | Added always-on raw GPS black box, explicit Trip-only transmission, portable SET vehicle declarations, Fixed User model, Daily PIN rules, three-working-day History, revised expense/email/storage/Maintenance rules, and Android 3.0 scope. |

This SRS is normative for business behavior. The Android Technical Build Specification defines implementation detail. The approved Android design is `docs/superpowers/specs/2026-08-25-android-set-3.0-design.md`.

# 1. Purpose and scope

The system manages use of company and rented vehicles. An Android SET records continuous raw GPS evidence, while Trips remain explicit business records created by a User or Driver. SET sends only Trip-related evidence and approved operational messages to an on-premise backend by outbound email. Backend performs official GPS processing, AI review, PIC control, Vendor reconciliation, reporting, and settlement.

## 1.1 In scope

- Android SET 3.0 installed on portable company-controlled Android devices.
- Always-on raw GPS evidence retained locally.
- Fixed User, Single Trip, and Driver Initiated flows.
- Explicit Start/End, historical Missing Trip, Adjustment, and vehicle-change Journey segments.
- Fixed, Daily, and Maintenance PIN namespaces.
- Signed company Master Package distributed as a file.
- Vendor Gmail gateways, durable queue, failover, and idempotent email packages.
- Backend ingestion/recovery, GPS processing, web portal, AI/PIC review, Vendor settlement, reporting, audit, and finalization.

## 1.2 Out of scope for Android 3.0 phase

- Production backend, portal, AI, and Vendor reconciliation implementation.
- Automatic backend-to-SET push or realtime acknowledgement API.
- Automatic creation of business Trips from vehicle movement.
- Sending private/out-of-Trip GPS in normal operation.
- Offline maps and Google Maps on SET.
- Migration from Android app 2.1.0; clean install is permitted for 3.0.0.

# 2. Actors and authority

| Actor | Authority |
|---|---|
| Fixed User | Authenticate with Fixed PIN, review identity, Start/End eligible Trips. |
| Single Trip User | Authenticate with Daily PIN and self-declare identity. |
| Driver | Driver Initiated Trip, vehicle declaration/change, History and Adjustment, expense, resend, Master file selection. |
| PIC/WS | Authoritative business corrections, AI exceptions, recovery, Vendor reconciliation, finalization. |
| Maintenance Admin | PIN-protected diagnostics, PIN reveal, export, tracking pause, reset/re-provision. |
| AI | Decision support only; never overrides PIC. |
| Vendor | Receives/replies to reports; no internal portal access. |

PIC is authoritative when PIC intervenes. SET preserves every original declaration and evidence version.

# 3. Architecture and system boundary

```text
ANDROID SET -- outbound email --> MAIL INFRASTRUCTURE --> ON-PREMISE BACKEND
                                                             |
                                                      INTERNAL PORTAL
                                                             |
                                                  GPS / AI / Contract / Audit
```

**ARCH-001** Device ID shall be the stable technical source identifier across vehicle changes.

**ARCH-002** Core SET operation shall work without backend/network connectivity.

**ARCH-003** Operational communication is outbound email only; no backend-to-SET API or acknowledgement is required.

**ARCH-004** Backend and portal operate in the internal company network.

**ARCH-005** At most one physical Trip segment may be IN_PROGRESS on a SET.

**ARCH-006** AI/backend outage shall not block SET operation.

**ARCH-007** SET may move between vehicles. Driver-declared vehicle context is versioned by effective time and never rewrites prior context.

# 4. Black-box GPS evidence

**SET-GPS-001** SET shall run location tracking continuously after permission setup, independent of Trip state.

**SET-GPS-002** SET shall persist at most one ordinary raw sample in each approximately 10-second window, including when no Trip exists.

**SET-GPS-003** Raw samples are immutable and retain source timestamp, elapsed realtime, coordinates, altitude, accuracy, speed, bearing, provider, mock flag, sequence, and boot session when available.

**SET-GPS-004** Missing callbacks shall remain explicit gaps. SET shall never fabricate or interpolate coordinates.

**SET-GPS-005** Normal email shall include only raw samples inside an explicit Trip interval. GPS outside Trips remains local except authorized Maintenance Recovery.

**SET-GPS-006** Start/End shall record action time immediately and attempt a non-blocking high-accuracy boundary burst for at most ten seconds.

**SET-GPS-007** TEMP_STOP is derived local state: speed below 1 m/s, inside a 20-metre radius, sustained for at least 60 seconds. Resume requires two consecutive moving samples. Derived movement never mutates raw evidence and is not sent in normal Trip email.

**SET-GPS-008** SET Estimated Distance is UI-only. Backend calculates official GPS Distance from raw evidence.

**SET-GPS-009** A 30-second callback gap opens a local incident. Outside a Trip it produces only device warnings. During a Trip, a five-minute gap sends one opened alert and later one recovery message.

**SET-GPS-010** Driver/User shall not have a tracking-stop action. Maintenance may pause tracking only with PIN, reason, audit, and optional resume time.

# 5. Local storage and offline operation

**SET-DATA-001** Start, End, GPS, confirmation, expense, History, vehicle change, and queue operations shall work offline.

**SET-DATA-002** Raw GPS, Trip, vehicle binding, confirmation, expense, audit, and email queue shall survive reboot/power loss.

**SET-DATA-003** Normal raw retention is one year.

**SET-DATA-004** Room is the authoritative encrypted data store. Derived email/export files are disposable.

**SET-DATA-005** Below 500 MiB free storage, SET shall warn Driver/Admin and report telemetry.

**SET-DATA-006** Below 200 MiB free storage, SET may delete whole oldest raw days until at least 200 MiB is free. It shall protect unfinished/unsent/unresolved Trip evidence and audit each cleanup.

**SET-DATA-007** A storage warning shall never block a current Trip.

**SET-DATA-008** Every outbound package shall contain storage/database/retention telemetry.

**SET-DATA-009** A restart during an active Trip restores the Trip and leaves the unavailable interval as a gap.

# 6. User and PIN model

## 6.1 Fixed User

**SET-FIX-001** The former Fixed Schedule flow is renamed Fixed User.

**SET-FIX-002** Fixed Users are company-wide, have no vehicle assignment/schedule, and may authenticate on any SET containing the current company Master Package.

**SET-FIX-003** Fixed flow is: choose Fixed User, enter six-digit Fixed PIN, display master identity, User confirms, then Start.

**SET-FIX-004** Fixed PIN shall be unique within a Master Package version.

## 6.2 Daily PIN and Single Trip

**SET-PIN-001** Fixed, Daily, and Maintenance PINs are separate namespaces; cross-namespace numeric equality is allowed.

**SET-PIN-002** Daily PIN is six digits and may begin with zero. Maintenance PIN is eight digits.

**SET-PIN-003** At 00:00 `Asia/Ho_Chi_Minh`, each SET creates one Daily PIN and queues it to backend/PIC. Driver cannot view it; PIC provides it to User.

**SET-PIN-004** PIN effective time is independent of actual email delivery. Retry/reboot shall not generate a different PIN for the same date.

**SET-PIN-005** Single Trip User enters Daily PIN plus self-declared name, employee ID, and department. SET does not claim independent identity verification.

**SET-PIN-006** A Trip started with the preceding day's PIN may cross midnight. End after 08:30 completes normally but creates `PIN_VALIDITY_EXCEPTION` for PIC.

**SET-PIN-007** The first five failed attempts retry immediately; attempt six introduces 30-second cooldown; ten consecutive failures introduce five-minute cooldown. No permanent offline lockout is allowed.

## 6.3 Driver Initiated confirmation

**SET-DRV-001** Driver Initiated Trip may Start/End without User PIN and becomes `PENDING_USER_CONFIRMATION`.

**SET-DRV-002** Confirmation is available locally for 48 real hours.

**SET-DRV-003** User selects Fixed User or Single Trip. Fixed User enters PIN then reviews master identity. Single Trip enters the Daily PIN applicable to Trip start plus personal information.

**SET-DRV-004** Driver-declared and User-confirmed identity are retained separately. Mismatch is flagged for PIC.

**SET-DRV-005** After 48 hours, local PIN confirmation is disabled; Trip remains and backend/PIC continues processing.

# 7. Master Package and business calendar

**SET-MASTER-001** Backend shall automatically build one company-wide, versioned, encrypted, signed Master Package after PIC changes approved data.

**SET-MASTER-002** Package may contain Fixed Users/PINs, Business Calendar, Maintenance PIN rotation, Gmail gateway catalog updates, and schema/key versions.

**SET-MASTER-003** Backend validates, increments version, canonicalizes, encrypts, signs, audits, and exposes the finished file for manual distribution. No direct push is required.

**SET-MASTER-004** SET verifies signature, decrypts, validates all records/version/uniqueness, and commits atomically. Any failure retains the prior package. Equal/older version is rejected.

**SET-MASTER-005** Business Calendar defines holidays and working-day overrides. Saturday/Sunday are non-working unless overridden.

# 8. Vehicle declaration and change

**SET-VEH-001** Driver declares plate number and Vendor on first setup. Vehicle type/note are optional. Context persists until explicitly changed.

**SET-VEH-002** Vehicle ID is not displayed. Device ID identifies the technical source. Settlement uses Driver-declared vehicle information plus audited backend/Vendor corrections.

**SET-VEH-003** SET accepts declarations for vehicles not present in a master list. Backend shall not reject an otherwise readable Trip solely because vehicle mapping is missing.

**SET-VEH-004** Backend/Vendor correction shall preserve Driver's original declaration and full revision audit.

**SET-VEH-005** Outside a Trip, vehicle change creates a new effective binding; reason is optional.

**SET-VEH-006** During a Trip, **Doi xe** requires reason and atomically closes the old physical segment and opens a new one under the same logical Journey. User/Purpose/confirmation context carries forward.

**SET-VEH-007** Each physical segment belongs to one vehicle declaration/Vendor and sends a separate Trip package.

# 9. Trip lifecycle and History

```text
CREATED -> IN_PROGRESS -> ENDED -> [PENDING_USER_CONFIRMATION]
        -> CONFIRMED -> COMPLETED
```

**SET-TRIP-001** Trip is created only by explicit User/Driver action. Movement shall never create an automatic business Trip.

**SET-TRIP-002** Start records Trip/Journey/segment, User context, vehicle binding, action time, and boundary status.

**SET-TRIP-003** End preserves immutable `end_action_time`, optional earlier `declared_end_time`, raw evidence through actual action, and boundary status.

**SET-TRIP-004** Normal Fixed/Single Trips may complete without later confirmation. Driver Initiated Trips follow section 6.3.

**SET-TRIP-005** Completed Trips are never reopened. Corrections are versioned Adjustment Requests preserving original evidence.

**SET-HIST-001** Driver History covers three working days using Business Calendar. On a workday it includes today plus two preceding workdays; on a non-workday it includes the three preceding workdays.

**SET-HIST-002** Driver may create Missing Trip/Adjustment using an interval no longer than 48 hours inside History.

**SET-HIST-003** An interval overlapping an existing physical Trip or duplicate request is rejected.

**SET-HIST-004** History Adjustment is not blocked by Daily PIN midnight/08:30 rules. It may be confirmed locally or sent `PENDING_CONFIRMATION` for backend/PIC.

**SET-HIST-005** Active Trip reaching 48 hours produces severe warning but is never automatically ended.

# 10. Expense

**SET-EXP-001** Types are Parking, Toll Fee, and Other. Amount and Type are required; Note is required for Other. Receipt is optional.

**SET-EXP-002** Driver enters expense after a physical Trip segment ends.

**SET-EXP-003** Initial Trip email waits one hour after End and includes expenses saved during that hour.

**SET-EXP-004** Later edits remain drafts until Driver selects **Hoan tat expense**, producing versioned `EXPENSE_UPDATE`.

**SET-EXP-005** If unsent changes remain at deadline, SET queues the latest snapshot as `SYSTEM_DEADLINE_SEND`.

**SET-EXP-006** Expense locks at 00:00 two calendar dates after the Trip End date in `Asia/Ho_Chi_Minh`.

**SET-EXP-007** After lock, SET is read-only; further changes require Adjustment/PIC. Every version is audited.

# 11. SET-to-backend email

**SET-MAIL-001** One physical Trip segment produces one logical ZIP package after the one-hour expense delay.

**SET-MAIL-002** Package contains `manifest.json`, `trip.csv`, `gps_raw.csv`, and `expenses.csv`.

**SET-MAIL-003** `gps_raw.csv` contains exact raw samples inside the Trip interval only. Movement events are omitted. Backend processes movement/TEMP_STOP.

**SET-MAIL-004** Daily PIN, Confirmation, Expense Update, GPS-gap alert/recovery, health-check, and diagnostic are separate message types.

**SET-MAIL-005** Manifest includes message/schema versions, Device ID, vehicle declaration, app/device/tracking status, GPS ranges/counts/gaps, boundary offsets, storage telemetry, gateway role, file hashes, integrity key version, and HMAC.

**SET-MAIL-006** Package file hashes use SHA-256 and canonical manifest integrity/authentication uses HMAC-SHA256.

**SET-MAIL-007** ZIP cap is 15 MiB. Multipart by ordered time range is a defensive fallback. Full-year recovery prefers file/USB.

**SET-MAIL-008** Persistent priority is Daily PIN, Confirmation, Trip, Expense Update, then health/diagnostic. FIFO applies inside priority; backoff items do not head-of-line block eligible work.

**SET-MAIL-009** Stable logical Message ID/version and per-attempt Transmission ID provide idempotent retry/resend. Changed content creates a new version; unchanged resend remains a true duplicate.

**SET-MAIL-010** SMTP acceptance means `EMAIL_SENT`, not backend import. Package staging may be deleted after SMTP success because immutable source/version metadata remains reconstructable.

# 12. Gmail Vendor gateway policy

**SET-GW-001** Four Vendors each have two Primary Gmail personal accounts and one Backup, using App Password over SMTPS port 465.

**SET-GW-002** SETs are stably distributed between the two Primaries. Driver selects Vendor but cannot see account address/password.

**SET-GW-003** Authentication/account-disabled failures fail over after two attempts. Quota/rate-limit failures fail over after three failures in one hour.

**SET-GW-004** Offline/DNS/timeout does not count against an account. TLS/system-time and package errors do not trigger account failover.

**SET-GW-005** Backup remains active. After 24 hours, SET sends a Primary health-check; success fails back, failure retries after another 24 hours.

**SET-GW-006** Initial catalog is bundled in APK. Backend-built signed Master Package may rotate/add/disable accounts atomically.

**SET-GW-007** Gateway failure never blocks Trip or local data. All failover/failback/rotation actions are audited and reported.

# 13. Backend ingestion and GPS processing

**BE-IMP-001** Backend supports automatic mailbox ingestion and PIC manual upload/recovery.

**BE-IMP-002** Verify HMAC/hash/schema and stage multipart before business import.

**BE-IMP-003** Import valid records where safe; retain unreadable/invalid portions and warnings. Integrity failure requires PIC review rather than silent discard.

**BE-IMP-004** Compare content for true duplicate, avoid duplicate business data, and retain receipt/transmission history.

**BE-IMP-005** Newer message/business versions advance current view. Older late messages never roll back it.

**BE-IMP-006** Preserve Driver vehicle declaration, User confirmation, Vendor/PIC correction, and raw evidence as distinct versions.

**BE-GPS-001** Backend performs quality filtering, gap detection, movement/TEMP_STOP classification, route reconstruction, and official GPS Distance.

**BE-GPS-002** Raw GPS remains immutable. Backend/AI/PIC/Vendor/contract processing shall not alter it.

**BE-GPS-003** Backend uses Device ID, sequence, boot session, timestamps, and boundary metadata for source/error analysis.

# 14. Web, AI, financial, and Vendor baseline

Unchanged business baseline remains:

- Portal Dashboard, Trip List, Trip Detail, route view, search/filter, Adjustment, and audit history.
- AI states `ANALYZING`, `PASSED`, `FLAGGED`, `AI_NOT_AVAILABLE`; AI is advisory and outages do not block service.
- PIC is authoritative and can preserve multiple audited corrections.
- GPS Distance is immutable upstream evidence. Vendor Proposed, PIC decision, Adjusted, Contract Calculated, Payable, and Final Agreed distances remain traceable versions.
- Contract rules support Contract + Vehicle Type and optional vehicle override, effective dates, retroactive recalculation, and retained history.
- Daily Vendor Report is explicitly requested by authenticated PIC, snapshots current data, and supports Excel reply/reconciliation.
- Vendor response versions and PIC Accept/Refuse revisions are retained.
- Monthly Finalization is by Vendor + Month, blocked by unconfirmed lines, immutable when finalized, and corrected only by post-finalization revision.
- Excel/PDF exports and detailed GPS investigation remain backend functions.

# 15. Maintenance, security, and recovery

**SET-MNT-001** Maintenance button is visible on Home but all Maintenance content requires Maintenance PIN.

**SET-MNT-002** Maintenance includes diagnostics, Fixed/Daily PIN reveal, Master import, email test, encrypted recovery export, tracking pause/resume, and Reset/Re-provision.

**SET-MNT-003** PIN reveal requires re-authentication, masks by default, auto-hides after 30 seconds, blocks screenshots/copy, and audits action without PIN value. Maintenance PIN itself is never revealed.

**SET-MNT-004** Full Recovery encrypts data with random AES-256-GCM key and wraps the key with backend RSA-OAEP SHA-256 public key.

**SET-MNT-005** Reset requires verified full recovery export, removes prior business/raw/config/queue/master state, preserves Device ID and reset lineage, recreates encrypted DB, resumes tracking, and requires new Master/vehicle setup.

**SEC-001** Room business/raw storage uses SQLCipher with random passphrase protected by Android Keystore.

**SEC-002** `allowBackup=false`; cleartext traffic is disabled unless a verified component requires it; Android components are minimally exported.

**SEC-003** Do not log/export credentials, cryptographic secrets, entered PINs, or private GPS outside authorized scope.

**SEC-004** Bundled Gmail/HMAC/shared Master decryption secrets are acknowledged operational trade-offs and not hardware-grade protection.

# 16. Android UI baseline

Home displays Fixed User, Single Trip, Driver Area, Maintenance, active Trip, GPS/network/email/storage status, and configuration warnings. It has no tracking-stop button.

Pre-Start uses a text Current Location panel. Best-effort geocoding shows street, ward/commune, district, and province/city. Failure shows coordinates/accuracy and never blocks Start after acknowledgement. Google Maps is not part of Android 3.0 baseline.

The UI uses large controls, few steps, visible pending/error state, and no Driver exposure of Daily PIN, Gmail credential, or technical Vehicle ID.

# 17. Non-functional requirements

**NFR-SET-001** Support API 26+ mid-range devices with approximately 3-4 GiB RAM.

**NFR-SET-002** Ordinary sampling/processing is O(1) per callback and uses bounded rolling state.

**NFR-SET-003** One-year raw capacity target is at most about 1 GiB plus working overhead.

**NFR-SET-004** Foreground tracking, queue, and active Trip recover after reboot/process replacement where Android permits.

**NFR-SET-005** Gmail/network/backend outage does not stop local business operation.

Backend sizing, SSO/AD, backup SLA, AI models, contract formulas, offline map data, and final report visuals remain backend/IT phase decisions.

# 18. Critical acceptance criteria

| ID | Pass condition |
|---|---|
| ACC-RAW-001 | Device stores at most one ordinary exact raw sample/10-second window outside and inside Trips without automatic Trip creation. |
| ACC-PRIV-001 | Normal package contains no raw GPS outside its explicit Trip interval. |
| ACC-BOUND-001 | Start/End action is immediate; burst capture adds truthful timing/accuracy or explicit unavailable status. |
| ACC-TEMP-001 | TEMP_STOP requires 60 seconds under 1 m/s inside 20 m; Resume requires two moving samples. |
| ACC-FIX-001 | Company-wide Fixed PIN identifies User on any SET and displays snapshot before Start. |
| ACC-PIN-001 | Daily PIN is queued at 00:00, hidden from Driver, stable across retry/reboot, and creates exception after prior-day 08:30 rule. |
| ACC-VEH-001 | Vehicle change inside Trip atomically creates linked physical segments without pausing raw tracking. |
| ACC-HIST-001 | History uses three Business Calendar workdays and supports a maximum 48-hour Missing/Adjustment interval. |
| ACC-EXP-001 | Trip waits one hour; later completed expense snapshots version correctly; deadline fallback sends unsent change. |
| ACC-MAIL-001 | Offline Trip completes locally; restoring network sends durable priority queue without duplicates. |
| ACC-GW-001 | Eligible Primary failures switch to Backup and later health-check/failback with audit. |
| ACC-STO-001 | Warning at 500 MiB and protected whole-day cleanup below 200 MiB preserve unresolved evidence. |
| ACC-MASTER-001 | One invalid Master record rejects the whole higher-version package and retains current data. |
| ACC-REC-001 | Maintenance produces verified encrypted recovery before Reset; Device ID remains unchanged. |
| ACC-AUD-001 | Simulated audit-write failure rolls back its business transaction. |

# 19. Traceability and implementation principle

The Android Technical Build Specification maps implementation components and tests to these requirements. Golden email fixtures define the cross-phase contract for backend development.

Implementation must not silently invent business/security behavior. Raw evidence, original declarations, and version history are preserved unless an explicit retention/reset requirement authorizes deletion. Any future behavior change updates SRS, technical spec, tests, operational documentation, and design reference in the same change.
