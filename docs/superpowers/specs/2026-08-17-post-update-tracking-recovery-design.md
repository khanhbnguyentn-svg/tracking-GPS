# Post-Update Tracking Recovery Design

## Problem

Installing version 2.0.2 in place preserved app data but stopped the foreground tracking service. Opening the app did not restart it. A reboot restored the service, report job, and 10-second high-accuracy request, but requiring a reboot after every APK update is not acceptable.

## Decision

- Register `ScheduleReceiver` for `android.intent.action.MY_PACKAGE_REPLACED` so an in-place package update reconciles tracking and the report schedule.
- On `MainActivity` creation, reconcile tracking only. Do not replace the report work merely because the UI opened.
- Persisted `trackingPreferences.enabled` remains authoritative: enabled tracking restarts; intentionally disabled tracking remains disabled.
- Keep the existing `BOOT_COMPLETED`, `TIME_SET`, and `TIMEZONE_CHANGED` behavior.

## Safety

`WorkManagerReportScheduler` uses `ExistingWorkPolicy.REPLACE`. Calling the full background reconciliation on every UI launch could replace an overdue report with the next calendar anchor. App-launch recovery therefore calls only `reconcileTracking()`.

## Release

Publish the correction as version 2.0.3 (`versionCode` 5) using the existing signing certificate. Do not overwrite the 2.0.2 APK.

## Verification

- A pure app-launch policy test proves enabled tracking requests service recovery and disabled tracking does nothing.
- Existing boot receiver policy tests continue to prove system events restore tracking and schedule.
- Release pipeline tests require 2.0.3 / code 5.
- Signed APK metadata, certificate, complete Android tests, lint, and Pester suite must pass.
