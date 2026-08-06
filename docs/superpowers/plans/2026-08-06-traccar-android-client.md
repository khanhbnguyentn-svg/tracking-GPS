# Traccar Android Client Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a Vietnamese Android 14+ app that reliably queues and sends phone locations to adjustable Traccar OsmAnd endpoints, with fleet onboarding, diagnostics, TLS controls, CI, and an IT handover.

**Architecture:** One `app` module uses Compose and ViewModels for UI, Room for profiles and pending positions, an encrypted preferences file for sensitive profile fields, OkHttp for OsmAnd/TLS, a location foreground service for collection, and WorkManager for retry uploads. Manual dependency wiring in `TrackerApplication` avoids a DI framework; pure Kotlin policy and codec classes carry most unit-testable behavior.

**Tech Stack:** Kotlin, Android SDK 34–36, AGP 9.2, Gradle 9.4.1, JDK 17, Jetpack Compose BOM 2026.06.00, Room, WorkManager, Google Play Services Location, OkHttp, AndroidX Security Crypto, JUnit 4.

## Global Constraints

- `minSdk = 34`, `compileSdk = 36`, `targetSdk = 36`; test only Android 14, 15, and 16.
- Default language is Vietnamese; status and errors must not rely on color alone.
- Never trust all certificates, bypass hostname verification, or log full endpoints, Device IDs, or locations in release builds.
- Runtime-adjustable HTTP requires app-level cleartext permission because Android cannot dynamically whitelist a configured host; only the active profile may use it and UI must identify it as insecure.
- Default update interval is 60 seconds; pending queue limit is 10,000 rows and drains FIFO.
- A location is persisted before upload and deleted only after a successful OsmAnd response.
- WorkManager uploads queued data but never starts the location foreground service.
- Device ID is `AND-<16 lowercase hex>` from `ANDROID_ID`, with one stored UUID fallback.
- Shared JSON config version is exactly `1` and never contains Device ID or CA certificate bytes.
- Use one app module, manual dependency injection, OkHttp without Retrofit, and no custom server dashboard.

---

## File Map

- Build and CI: `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, `app/build.gradle.kts`, `gradle/wrapper/*`, `.github/workflows/android.yml`.
- App wiring: `TrackerApplication.kt`, `AppContainer.kt`, `MainActivity.kt`, `TrackerApp.kt`.
- Profiles/onboarding: `Profile.kt`, `ProfileDao.kt`, `AppDatabase.kt`, `ProfileRepository.kt`, `ConfigFileCodec.kt`, `DeviceIdProvider.kt`.
- Transport: `OsmAndRequestFactory.kt`, `TlsClientFactory.kt`, `OsmAndClient.kt`, `ConnectionTester.kt`.
- Queue/retry: `PendingLocation.kt`, `PendingLocationDao.kt`, `LocationQueueRepository.kt`, `UploadWorker.kt`.
- Tracking: `TrackingPreferences.kt`, `PermissionState.kt`, `LocationForegroundService.kt`.
- UI: `StatusScreen.kt`, `ProfilesScreen.kt`, `DiagnosticsScreen.kt`, `PermissionActions.kt`.
- Docs: `README.md`, `config/traccar-profile.example.json`, `Huong-dan-ky-thuat-setup-Traccar-Server-noi-bo.md`.

### Task 1: Buildable Android shell and CI

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`
- Create: `gradle/wrapper/gradle-wrapper.properties`, `gradlew`, `gradlew.bat`
- Create: `app/build.gradle.kts`, `app/proguard-rules.pro`, `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/com/internal/tracker/TrackerApplication.kt`
- Create: `app/src/main/java/com/internal/tracker/MainActivity.kt`
- Create: `app/src/main/java/com/internal/tracker/ui/TrackerApp.kt`
- Create: `app/src/main/res/values/strings.xml`, `themes.xml`, `colors.xml`
- Create: `app/src/test/java/com/internal/tracker/ProjectConfigTest.kt`
- Create: `.github/workflows/android.yml`

**Interfaces:**
- Produces: Android application id `com.internal.tracker`, `TrackerApplication`, and a Compose `TrackerApp()` entry point.

- [ ] **Step 1: Write the failing configuration test**

```kotlin
class ProjectConfigTest {
    @Test fun packageNameIsStable() {
        assertEquals("com.internal.tracker", BuildConfig.APPLICATION_ID)
    }
}
```

- [ ] **Step 2: Run `./gradlew testDebugUnitTest` and verify it fails because no Android project exists.**

- [ ] **Step 3: Add the minimal Gradle/Compose project** using AGP `9.2`, Gradle `9.4.1`, JDK 17, SDK 34/36, Room, WorkManager, Play Services Location, OkHttp, Security Crypto, Navigation Compose, Lifecycle Compose, and JUnit. Set `android:usesCleartextTraffic="true"` because runtime hostnames cannot be listed statically; the app must expose no network destination except the validated active profile.

- [ ] **Step 4: Add CI** that runs `./gradlew testDebugUnitTest lintDebug assembleDebug` and uploads `app/build/outputs/apk/debug/app-debug.apk` as `traccar-tracker-debug`.

- [ ] **Step 5: Run `./gradlew testDebugUnitTest lintDebug assembleDebug` and verify all three tasks pass.**

- [ ] **Step 6: Commit** with `git commit -m "build: scaffold Android tracker app"`.

### Task 2: Device identity and shared JSON configuration

**Files:**
- Create: `app/src/main/java/com/internal/tracker/config/DeviceIdProvider.kt`
- Create: `app/src/main/java/com/internal/tracker/config/ImportedProfile.kt`
- Create: `app/src/main/java/com/internal/tracker/config/ConfigFileCodec.kt`
- Create: `app/src/test/java/com/internal/tracker/config/DeviceIdProviderTest.kt`
- Create: `app/src/test/java/com/internal/tracker/config/ConfigFileCodecTest.kt`
- Create: `config/traccar-profile.example.json`

**Interfaces:**
- Produces: `DeviceIdProvider.get(): String`, `ConfigFileCodec.decode(String): Result<ImportedProfile>`, and `ConfigFileCodec.encodeTemplate(): String`.
- `ImportedProfile(name, host, port, scheme, intervalSeconds, tlsMode, certificatePin)` is consumed by Task 3.

- [ ] **Step 1: Write failing Device ID tests** proving valid Android IDs become `AND-0123456789abcdef`, invalid values use a persisted UUID-derived fallback, and repeated calls return the same ID.

- [ ] **Step 2: Run `./gradlew testDebugUnitTest --tests '*DeviceIdProviderTest'` and verify missing-class failures.**

- [ ] **Step 3: Implement `DeviceIdProvider`** with injected Android-ID and fallback stores so the production adapter reads `Settings.Secure.ANDROID_ID` and encrypted preferences.

- [ ] **Step 4: Run the Device ID tests and verify they pass.**

- [ ] **Step 5: Write failing codec tests** for the exact version-1 JSON, unknown fields, unsupported versions, invalid schemes/ports/intervals, conditional certificate pin, and encode/decode round trip.

- [ ] **Step 6: Run `./gradlew testDebugUnitTest --tests '*ConfigFileCodecTest'` and verify expected failures.**

- [ ] **Step 7: Implement the codec** using `org.json.JSONObject`; accept only `http|https`, ports `1..65535`, intervals `15..86400`, and `system|customCa|pinning`. Require `sha256/` plus a valid Base64 SHA-256 value for pinning.

- [ ] **Step 8: Add `config/traccar-profile.example.json`** matching the approved schema and run both test classes.

- [ ] **Step 9: Commit** with `git commit -m "feat: add device identity and config import"`.

### Task 3: Profile persistence and activation

**Files:**
- Create: `app/src/main/java/com/internal/tracker/data/AppDatabase.kt`
- Create: `app/src/main/java/com/internal/tracker/profile/Profile.kt`
- Create: `app/src/main/java/com/internal/tracker/profile/ProfileDao.kt`
- Create: `app/src/main/java/com/internal/tracker/profile/ProfileRepository.kt`
- Create: `app/src/test/java/com/internal/tracker/profile/ProfileRepositoryTest.kt`

**Interfaces:**
- Consumes: `ImportedProfile` from Task 2.
- Produces: `ProfileRepository.observeAll(): Flow<List<Profile>>`, `active(): Flow<Profile?>`, `save(ImportedProfile)`, `activate(Long)`, and `delete(Long)`.

- [ ] **Step 1: Write failing repository tests** for saving a profile, keeping only one active profile, rejecting activation while tracking is enabled, and deleting custom CA material with a profile.

- [ ] **Step 2: Run the profile tests and verify missing implementation failures.**

- [ ] **Step 3: Implement Room entity/DAO and repository.** Store non-secret indexes in Room; store host, pin, and imported CA bytes in one `EncryptedSharedPreferences` entry keyed by profile id. Use a Room transaction for active-profile changes.

- [ ] **Step 4: Run the profile tests and full unit suite.**

- [ ] **Step 5: Commit** with `git commit -m "feat: persist connection profiles"`.

### Task 4: Pending location queue

**Files:**
- Create: `app/src/main/java/com/internal/tracker/queue/PendingLocation.kt`
- Create: `app/src/main/java/com/internal/tracker/queue/PendingLocationDao.kt`
- Create: `app/src/main/java/com/internal/tracker/queue/LocationQueueRepository.kt`
- Modify: `app/src/main/java/com/internal/tracker/data/AppDatabase.kt`
- Create: `app/src/test/java/com/internal/tracker/queue/LocationQueueRepositoryTest.kt`

**Interfaces:**
- Produces: `enqueue(LocationSample): Long`, `oldest(limit: Int): List<PendingLocation>`, `markSent(id: Long)`, `incrementRetry(id: Long)`, and `count(): Flow<Int>`.

- [ ] **Step 1: Write failing tests** proving FIFO order, persistence before send, deletion only after success, retry increment after failure, and eviction of the oldest row at 10,001 entries.

- [ ] **Step 2: Run queue tests and verify expected failures.**

- [ ] **Step 3: Implement the Room entity, indexed timestamp query, DAO transaction, and repository** with `MAX_PENDING = 10_000`.

- [ ] **Step 4: Run queue tests and full unit suite.**

- [ ] **Step 5: Commit** with `git commit -m "feat: add durable location queue"`.

### Task 5: OsmAnd transport and TLS

**Files:**
- Create: `app/src/main/java/com/internal/tracker/network/OsmAndRequestFactory.kt`
- Create: `app/src/main/java/com/internal/tracker/network/TlsClientFactory.kt`
- Create: `app/src/main/java/com/internal/tracker/network/OsmAndClient.kt`
- Create: `app/src/main/java/com/internal/tracker/network/SendResult.kt`
- Create: `app/src/test/java/com/internal/tracker/network/OsmAndRequestFactoryTest.kt`
- Create: `app/src/test/java/com/internal/tracker/network/TlsClientFactoryTest.kt`
- Create: `app/src/test/java/com/internal/tracker/network/OsmAndClientTest.kt`

**Interfaces:**
- Consumes: active `Profile`, device ID, and `PendingLocation`.
- Produces: `OsmAndClient.send(Profile, String, PendingLocation): SendResult` where result is `Success`, `DnsFailure`, `Refused`, `Timeout`, `TlsFailure`, or `HttpFailure(code)`.

- [ ] **Step 1: Write failing URL tests** for scheme/host/port/path, encoded Device ID, latitude/longitude/timestamp/speed/accuracy, IPv4, and DNS hostname.

- [ ] **Step 2: Run URL tests and verify expected failures.**

- [ ] **Step 3: Implement request creation** with `HttpUrl.Builder` and no string concatenation.

- [ ] **Step 4: Write failing TLS tests** for system trust, scoped custom CA, valid hostname pin, malformed certificate and mismatched pin. Assert no trust-all manager or permissive hostname verifier exists.

- [ ] **Step 5: Implement TLS clients** using the Android system trust manager, an additional X.509 trust manager built from the imported CA, or OkHttp `CertificatePinner`. For HTTP, allow only the selected profile through the single transport client and show its insecure state.

- [ ] **Step 6: Write failing client tests** with MockWebServer for 200, 400, 500, timeout, and connection failure classification.

- [ ] **Step 7: Implement `OsmAndClient`, run network tests, then run the full suite.**

- [ ] **Step 8: Commit** with `git commit -m "feat: send OsmAnd locations securely"`.

### Task 6: Queue upload and WorkManager retry

**Files:**
- Create: `app/src/main/java/com/internal/tracker/worker/QueueUploader.kt`
- Create: `app/src/main/java/com/internal/tracker/worker/UploadWorker.kt`
- Create: `app/src/test/java/com/internal/tracker/worker/QueueUploaderTest.kt`
- Create: `app/src/test/java/com/internal/tracker/worker/UploadWorkerTest.kt`

**Interfaces:**
- Produces: `QueueUploader.drain(maxItems: Int = 100): UploadSummary` and unique work name `pending-location-upload`.

- [ ] **Step 1: Write failing uploader tests** proving sequential FIFO sends, stop-on-first-failure, successful deletion, retry increment, and a maximum batch of 100.

- [ ] **Step 2: Run uploader tests and verify expected failures.**

- [ ] **Step 3: Implement `QueueUploader`** by composing the queue, active profile, Device ID and OsmAnd client.

- [ ] **Step 4: Write failing worker tests** proving network constraint, exponential backoff, success for an empty/drained queue, and retry for transport failure.

- [ ] **Step 5: Implement `UploadWorker` and its enqueue helper.** Do not reference or start `LocationForegroundService` from this worker.

- [ ] **Step 6: Run worker tests and full unit suite.**

- [ ] **Step 7: Commit** with `git commit -m "feat: retry queued locations"`.

### Task 7: Permission policy and foreground tracking service

**Files:**
- Create: `app/src/main/java/com/internal/tracker/tracking/PermissionState.kt`
- Create: `app/src/main/java/com/internal/tracker/tracking/TrackingPreferences.kt`
- Create: `app/src/main/java/com/internal/tracker/tracking/LocationForegroundService.kt`
- Create: `app/src/main/java/com/internal/tracker/tracking/TrackingController.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `app/src/test/java/com/internal/tracker/tracking/PermissionStateTest.kt`
- Create: `app/src/test/java/com/internal/tracker/tracking/TrackingControllerTest.kt`

**Interfaces:**
- Produces: `TrackingController.start(): StartResult`, `stop()`, `isTracking: Flow<Boolean>` and permission actions `RequestFine`, `RequestBackground`, `RequestNotifications`, `OpenLocationSettings`, `OpenAppSettings`, `RequestBatteryExemption`, `Ready`.

- [ ] **Step 1: Write failing permission-policy tests** for every missing prerequisite and the exact ordering defined in the spec.

- [ ] **Step 2: Run permission tests and verify expected failures.**

- [ ] **Step 3: Implement the pure permission policy and Android adapters** using Activity Result APIs and Settings intents.

- [ ] **Step 4: Write failing controller tests** proving tracking cannot start without a visible activity/ready permissions, starts once, stops idempotently, and never claims running after service stop.

- [ ] **Step 5: Implement the foreground service** with `foregroundServiceType="location"`, `FOREGROUND_SERVICE_LOCATION`, ongoing notification, stop action, Fused Location Provider balanced accuracy, configured interval, queue-first persistence, immediate upload attempt, and upload work scheduling after failure.

- [ ] **Step 6: Run tracking tests and full unit suite.**

- [ ] **Step 7: Commit** with `git commit -m "feat: track location in foreground service"`.

### Task 8: Connection diagnostics

**Files:**
- Create: `app/src/main/java/com/internal/tracker/network/ConnectionTester.kt`
- Create: `app/src/main/java/com/internal/tracker/network/DiagnosticResult.kt`
- Create: `app/src/test/java/com/internal/tracker/network/ConnectionTesterTest.kt`

**Interfaces:**
- Produces: `testNetwork(Profile): DiagnosticResult` and `sendLatest(Profile, LocationSample): DiagnosticResult`.

- [ ] **Step 1: Write failing tests** for invalid config, DNS, refused connection, timeout, TLS error, HTTP error, reachable endpoint, missing real GPS fix, and successful test point.

- [ ] **Step 2: Run diagnostics tests and verify expected failures.**

- [ ] **Step 3: Implement two independent diagnostic operations** with five-second timeouts and Vietnamese user messages. Never substitute coordinates `0,0`.

- [ ] **Step 4: Run diagnostics tests and full unit suite.**

- [ ] **Step 5: Commit** with `git commit -m "feat: diagnose Traccar connections"`.

### Task 9: Compose UI and file picker flows

**Files:**
- Modify: `app/src/main/java/com/internal/tracker/ui/TrackerApp.kt`
- Create: `app/src/main/java/com/internal/tracker/ui/status/StatusScreen.kt`
- Create: `app/src/main/java/com/internal/tracker/ui/status/StatusViewModel.kt`
- Create: `app/src/main/java/com/internal/tracker/ui/profiles/ProfilesScreen.kt`
- Create: `app/src/main/java/com/internal/tracker/ui/profiles/ProfilesViewModel.kt`
- Create: `app/src/main/java/com/internal/tracker/ui/diagnostics/DiagnosticsScreen.kt`
- Create: `app/src/main/java/com/internal/tracker/ui/diagnostics/DiagnosticsViewModel.kt`
- Create: `app/src/main/java/com/internal/tracker/ui/permissions/PermissionActions.kt`
- Create: `app/src/test/java/com/internal/tracker/ui/profiles/ProfilesViewModelTest.kt`
- Create: `app/src/test/java/com/internal/tracker/ui/status/StatusViewModelTest.kt`

**Interfaces:**
- Consumes all repositories/controllers from Tasks 2–8.
- Produces three destinations: `status`, `profiles`, `diagnostics`.

- [ ] **Step 1: Write failing ViewModel tests** for status aggregation, profile validation/import preview/confirmation, active-profile protection during tracking, template export, permission actions and diagnostic state transitions.

- [ ] **Step 2: Run ViewModel tests and verify expected failures.**

- [ ] **Step 3: Implement the three ViewModels** as immutable `StateFlow` state holders.

- [ ] **Step 4: Build the Compose screens** with bottom navigation, compact status rows, accessible labels, explicit text status, system document create/open launchers, profile preview dialog, certificate picker, copy Device ID action, and Settings actions.

- [ ] **Step 5: Add all Vietnamese strings to `strings.xml`; run unit tests, lint and assemble.**

- [ ] **Step 6: Commit** with `git commit -m "feat: add tracker configuration UI"`.

### Task 10: Application wiring and end-to-end repository checks

**Files:**
- Create: `app/src/main/java/com/internal/tracker/AppContainer.kt`
- Modify: `app/src/main/java/com/internal/tracker/TrackerApplication.kt`
- Modify: `app/src/main/java/com/internal/tracker/MainActivity.kt`
- Create: `app/src/test/java/com/internal/tracker/AppContainerTest.kt`

**Interfaces:**
- Produces one production object graph shared by activities, service, worker and ViewModels.

- [ ] **Step 1: Write a failing wiring test** proving one database, profile repository, queue repository, Device ID provider, transport, uploader and tracking controller are reused.

- [ ] **Step 2: Run the wiring test and verify expected failures.**

- [ ] **Step 3: Implement `AppContainer` and connect Activity, service and worker** without reflection or a DI framework.

- [ ] **Step 4: Run `./gradlew testDebugUnitTest lintDebug assembleDebug` and verify success.**

- [ ] **Step 5: Commit** with `git commit -m "feat: wire tracker application"`.

### Task 11: IT handover and operator README

**Files:**
- Create: `README.md`
- Modify: `Huong-dan-ky-thuat-setup-Traccar-Server-noi-bo.md`
- Create: `docs/android-14-device-test-checklist.md`

**Interfaces:**
- Documents the exact app/server contract and GitHub workflow produced by Tasks 1–10.

- [ ] **Step 1: Update the IT guide** with pinned Traccar image guidance, PostgreSQL, reverse proxy TLS, OsmAnd endpoint, capacity notes for 350 devices, `database.registerUnknown` regex/quarantine group, `event.status.enable`, 5/10-minute escalation, recovery notifications, and expiring alert suppression.

- [ ] **Step 2: Write the operator README** with Android Studio prerequisites, GitHub repository creation, push commands, Actions artifact download, shared JSON import, Device ID confirmation and APK installation.

- [ ] **Step 3: Add the Android 14–16 real-device checklist** for permissions, background tracking, screen lock, swipe-away, network loss/recovery, TLS modes, queue replay and vendor battery restrictions.

- [ ] **Step 4: Run `rg -n "trust.?all|hostnameVerifier.*true|http://" app/src/main`** and inspect every match; release code must contain no TLS bypass or hardcoded HTTP endpoint. Confirm the manifest cleartext permission is documented and the only outbound destination comes from the validated active profile.

- [ ] **Step 5: Run `./gradlew testDebugUnitTest lintDebug assembleDebug` and inspect the generated APK path.**

- [ ] **Step 6: Commit** with `git commit -m "docs: add deployment and device handover"`.

### Task 12: GitHub readiness and final verification

**Files:**
- Modify only files required by fresh verification failures.

**Interfaces:**
- Produces a clean `main` branch ready to push to a new GitHub repository.

- [ ] **Step 1: Run `git status --short` and review `git diff --check`.**

- [ ] **Step 2: Run the full build:** `./gradlew --no-daemon clean testDebugUnitTest lintDebug assembleDebug`.

- [ ] **Step 3: Inspect test reports, lint report and `app/build/outputs/apk/debug/app-debug.apk`; record exact results in the final response.**

- [ ] **Step 4: Compare every design-spec section against the implementation and document only real-device checks that remain unverified locally.**

- [ ] **Step 5: Commit any verification-only fixes with `git commit -m "fix: resolve final verification findings"`; do not create an empty commit.**
