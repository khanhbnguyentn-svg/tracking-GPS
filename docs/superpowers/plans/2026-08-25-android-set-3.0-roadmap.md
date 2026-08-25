# Android SET 3.0 Delivery Roadmap

**Goal:** Deliver Android SET 3.0 as six independently reviewable and testable implementation plans.

**Design authority:** `docs/superpowers/specs/2026-08-25-android-set-3.0-design.md`

## Why the work is split

Android SET 3.0 contains several independently rejectable subsystems: encrypted platform foundation, always-on tracking, Trip/identity business logic, email transport, UI/Maintenance, and release integration. Combining them into one execution plan would make tests, reviews, and rollback boundaries too large.

Each phase must finish with a compiling application and fresh tests. A later phase may consume only interfaces and behavior explicitly produced by an earlier phase.

## Phase sequence

1. **Platform foundation**
   - Release 3.0 build baseline.
   - Room 2.8.4 + SQLCipher 4.17.0 integration (latest approved pin compatible with compile SDK 36).
   - Business clock, UUID source, stable Device ID primitives.
   - Keystore-wrapped database passphrase and encrypted database probe.
   - Plan: `2026-08-25-android-set-3.0-phase-1-foundation.md`.

2. **Black-box tracking and raw storage**
   - Full raw/boot/incident/movement schema.
   - Always-on foreground service and boot reconciliation.
   - Ten-second persistence gate, boundary burst, TEMP_STOP, gap policy.
   - Capacity/retention/storage telemetry foundations.

3. **Trip, identity, vehicle, calendar, and expense domain**
   - Master Package, Fixed/Daily/Maintenance PIN.
   - Business Calendar and three-working-day History.
   - Journey/Trip segments, vehicle switching, confirmation, Adjustment.
   - Expense one-hour delay, deadlines, and immutable versions.

4. **Email contract, queue, and Gmail gateways**
   - Deterministic CSV/manifest/ZIP/HMAC fixtures.
   - Durable priority queue and idempotency.
   - SMTP classification, Primary distribution, Backup failover/failback.
   - Trip privacy and multipart contract tests.

5. **Compose UI and Maintenance**
   - Home, Fixed, Single, Driver, Active Trip, History, Expense.
   - Text Current Location panel.
   - Maintenance PIN reveal, diagnostics, pause, Recovery, Reset.
   - Accessibility, large-control, and state restoration tests.

6. **Integration, capacity, device acceptance, and release**
   - Full composition root and deletion of superseded 2.1 flows.
   - 3,153,600-row performance/capacity validation.
   - API 26/29/31/33/35-36 device matrix.
   - Signed 3.0.0 artifact verification and field runbook.

## Cross-phase gates

- No phase begins with a dirty working tree.
- Every behavior change follows red-green-refactor.
- Every task uses explicit-path staging and a focused commit.
- No destructive Room migration or automatic database deletion.
- No credential, PIN, signing key, private coordinate capture, or recovery key enters Git/logs.
- Phase completion requires unit tests, relevant instrumented tests, lint/build, `git diff --check`, and spec trace review.
- If implementation reveals a required design change, update SRS, technical spec, design, and the active plan before code proceeds.
