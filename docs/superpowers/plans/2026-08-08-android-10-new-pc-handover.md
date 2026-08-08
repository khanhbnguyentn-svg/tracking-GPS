# Android 10+ and New-PC Handover Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver one internally distributed APK for Android 10-16, an Android-supported reboot reminder and continuous-tracking workflow, and complete reproducible Windows new-PC deployment/test documentation.

**Architecture:** Keep the existing user-started location foreground service and upload queue. Add SDK-aware permission decisions, a small persisted reboot-resume flag with a notification-only boot receiver, and pure state helpers for import secrets so security behavior is unit-testable outside Compose. Documentation remains the deployment source of truth and records verified evidence separately from pending physical-device work.

**Tech Stack:** Kotlin, Jetpack Compose, AndroidX, Fused Location Provider, WorkManager, JUnit 4, Gradle/AGP, Node.js, PowerShell/Pester, PostgreSQL/PostGIS, WinSW, Cloudflare Quick Tunnel.

## Global Constraints

- Use one APK with `minSdk = 29`, `compileSdk = 36`, and `targetSdk = 36`.
- Support Android 10 through Android 16 with explicit SDK guards; do not add a second legacy APK.
- Start the location foreground service only from a visible user action.
- After reboot, post a notification asking the user to open the app and resume; never start location tracking silently from `BOOT_COMPLETED`.
- WorkManager retries queued uploads only and must never be described as a location-service restart mechanism.
- A force-stopped app cannot recover until the user opens it; state this explicitly.
- The persistent foreground notification remains the primary indicator that tracking is running.
- Imported bearer tokens remain encrypted and hidden; editing endpoint or TLS trust clears a pending imported token.
- No real token, tunnel profile, password, GPS history, DPAPI blob, runtime log, database data, or APK binary may be committed.
- Android 10-13 physical-device validation is a pending improvement until suitable phones are available.
- Route-history dashboard work is out of scope.
- Keep all work on `feature/cloudflare-quick-tunnel-pilot`; do not merge to `main` in this plan.

---

### Task 1: Bind imported tokens to unchanged endpoint and TLS state

**Files:**
- Modify: `app/src/main/java/com/internal/tracker/ui/TrackerApp.kt`
- Modify: `app/src/test/java/com/internal/tracker/ui/ProfileConfigJsonTest.kt`

**Interfaces:**
- Consumes: `ImportedProfile.ingestToken`, Compose profile form state, and existing `buildConfigJson(...)`.
- Produces: `ProfileFormSecrets` with `fromImport(ImportedProfile)`, `clearToken()`, and `clearForImport(ImportedProfile)` behavior used by the profile screen.

- [ ] **Step 1: Write failing secret-state tests**

Add tests proving that an unchanged import preserves the token, importing a system-CA profile clears stale custom-CA bytes, and changing a custom CA clears the token:

```kotlin
@Test
fun importedSystemCaProfileDropsStaleCustomCaButKeepsToken() {
    val state = ProfileFormSecrets(byteArrayOf(1), null).fromImport(imported(token = TOKEN))
    assertNull(state.customCa)
    assertEquals(TOKEN, state.ingestToken)
}

@Test
fun changingCustomCaClearsImportedToken() {
    val state = ProfileFormSecrets(null, TOKEN).withCustomCa(byteArrayOf(2))
    assertNull(state.ingestToken)
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```powershell
./gradlew.bat testDebugUnitTest --tests 'com.internal.tracker.ui.ProfileConfigJsonTest' --no-daemon
```

Expected: compilation failure because `ProfileFormSecrets` does not exist.

- [ ] **Step 3: Implement the minimal form-secret state**

Add an internal immutable state holder near `buildConfigJson`:

```kotlin
internal data class ProfileFormSecrets(
    val customCa: ByteArray?,
    val ingestToken: String?,
) {
    fun fromImport(profile: ImportedProfile) = ProfileFormSecrets(null, profile.ingestToken)
    fun withCustomCa(value: ByteArray?) = ProfileFormSecrets(value, null)
    fun clearToken() = copy(ingestToken = null)
}
```

Use one `ProfileFormSecrets` Compose state instead of independent `customCa` and `ingestToken` variables. Import confirmation must call `fromImport`. Host, port, scheme, TLS mode, and pin edits must call `clearToken`. CA selection must call `withCustomCa`. Successful save resets the state to `ProfileFormSecrets(null, null)`.

- [ ] **Step 4: Verify focused and full Android unit tests**

Run the focused command, then:

```powershell
./gradlew.bat testDebugUnitTest --no-daemon
```

Expected: all tests pass and no token value appears in test output.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/internal/tracker/ui/TrackerApp.kt app/src/test/java/com/internal/tracker/ui/ProfileConfigJsonTest.kt
git commit -m "fix: bind imported token to profile trust"
```

---

### Task 2: Lower the Android floor to API 29 and make permission flow SDK-aware

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/java/com/internal/tracker/tracking/PermissionState.kt`
- Modify: `app/src/main/java/com/internal/tracker/ui/TrackerApp.kt`
- Modify: `app/src/test/java/com/internal/tracker/tracking/PermissionStateTest.kt`

**Interfaces:**
- Consumes: runtime `Build.VERSION.SDK_INT`, `PermissionSnapshot`, and existing activity-result launchers.
- Produces: `PermissionAction.OpenBackgroundSettings` and SDK-correct `PermissionPolicy.next(...)` decisions for API 29, 30-32, 33, and 34-36.

- [ ] **Step 1: Write failing permission matrix tests**

Extend `PermissionSnapshot` test construction with `sdkInt` and cover:

```kotlin
assertEquals(RequestBackground, next(snapshot(sdkInt = 29, background = false)))
assertEquals(OpenBackgroundSettings, next(snapshot(sdkInt = 30, background = false)))
assertEquals(Ready, next(snapshot(sdkInt = 32, notifications = false)))
assertEquals(RequestNotifications, next(snapshot(sdkInt = 33, notifications = false)))
```

Keep the existing location-disabled, fine-location, permanently-denied, and ready cases.

- [ ] **Step 2: Run the permission test and verify RED**

```powershell
./gradlew.bat testDebugUnitTest --tests 'com.internal.tracker.tracking.PermissionStateTest' --no-daemon
```

Expected: compilation failure for missing `sdkInt` and `OpenBackgroundSettings`.

- [ ] **Step 3: Implement SDK-aware permission policy**

Set `minSdk = 29`. Add `sdkInt` to `PermissionSnapshot` and implement this order:

```kotlin
!locationEnabled -> OpenLocationSettings
!fineLocation && finePermanentlyDenied -> OpenAppSettings
!fineLocation -> RequestFine
!backgroundLocation && sdkInt == 29 -> RequestBackground
!backgroundLocation -> OpenBackgroundSettings
sdkInt >= 33 && !notifications -> RequestNotifications
else -> Ready
```

In the runtime snapshot, treat notifications as granted below API 33. Handle `OpenBackgroundSettings` with `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` for the app package. Never launch `POST_NOTIFICATIONS` below API 33.

- [ ] **Step 4: Run compatibility gates**

```powershell
./gradlew.bat testDebugUnitTest lintDebug assembleDebug --no-daemon
```

Expected: build success with no `NewApi` lint errors for API 29.

- [ ] **Step 5: Commit**

```powershell
git add app/build.gradle.kts app/src/main/java/com/internal/tracker/tracking/PermissionState.kt app/src/main/java/com/internal/tracker/ui/TrackerApp.kt app/src/test/java/com/internal/tracker/tracking/PermissionStateTest.kt
git commit -m "feat: support Android 10 permission flow"
```

---

### Task 3: Add a notification-only reboot resume workflow

**Files:**
- Create: `app/src/main/java/com/internal/tracker/tracking/ResumeTrackingReceiver.kt`
- Create: `app/src/main/java/com/internal/tracker/tracking/ResumeReminderPolicy.kt`
- Create: `app/src/test/java/com/internal/tracker/tracking/ResumeReminderPolicyTest.kt`
- Modify: `app/src/main/java/com/internal/tracker/tracking/TrackingPreferences.kt`
- Modify: `app/src/main/java/com/internal/tracker/tracking/TrackingController.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: persisted `TrackingPreferences.enabled` at `BOOT_COMPLETED` and `MY_PACKAGE_REPLACED`.
- Produces: `ResumeReminderPolicy.onSystemEvent(action, wasTracking): ResumeDecision`, persisted `resumeRequired`, and notification channel `tracking_resume` that opens `MainActivity` without starting location.

- [ ] **Step 1: Write failing reboot decision tests**

Test these exact decisions:

```kotlin
assertEquals(PROMPT, onSystemEvent(Intent.ACTION_BOOT_COMPLETED, true))
assertEquals(PROMPT, onSystemEvent(Intent.ACTION_MY_PACKAGE_REPLACED, true))
assertEquals(IGNORE, onSystemEvent(Intent.ACTION_BOOT_COMPLETED, false))
assertEquals(IGNORE, onSystemEvent("unexpected", true))
```

- [ ] **Step 2: Run the focused test and verify RED**

```powershell
./gradlew.bat testDebugUnitTest --tests 'com.internal.tracker.tracking.ResumeReminderPolicyTest' --no-daemon
```

Expected: compilation failure because the policy is missing.

- [ ] **Step 3: Implement policy, state, and receiver**

Add `resumeRequired` to `TrackingPreferences`. On a prompt decision, the receiver must:

1. Set `enabled = false` so the UI never reports a nonexistent post-reboot service.
2. Set `resumeRequired = true`.
3. Create a normal-importance reminder channel.
4. Post a notification whose immutable activity `PendingIntent` opens `MainActivity`.
5. Never call `startService`, `startForegroundService`, WorkManager, or the location client.

`TrackingController.start()` and `.stop()` both clear `resumeRequired`. Add `RECEIVE_BOOT_COMPLETED` and a receiver for `BOOT_COMPLETED` and `MY_PACKAGE_REPLACED`; set `android:exported="false"` unless manifest verification proves the platform requires otherwise.

- [ ] **Step 4: Verify policy, manifest, lint, and assembly**

```powershell
./gradlew.bat testDebugUnitTest --tests 'com.internal.tracker.tracking.ResumeReminderPolicyTest' --no-daemon
./gradlew.bat lintDebug assembleDebug --no-daemon
```

Also inspect the merged manifest and assert that the receiver contains no service-start code.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main app/src/test/java/com/internal/tracker/tracking/ResumeReminderPolicyTest.kt
git commit -m "feat: remind users to resume tracking after reboot"
```

---

### Task 4: Write the complete Windows and Android handover

**Files:**
- Create: `docs/NEW-PC-DEPLOYMENT.md`
- Create: `docs/REQUIREMENTS-TESTING.md`
- Modify: `README.md`
- Modify: `gps-receiver/docs/ANDROID.md`
- Modify: `gps-receiver/docs/OPERATIONS.md`

**Interfaces:**
- Consumes: all existing Windows installers, Quick Tunnel commands, Android build commands, and Tasks 1-3 behavior.
- Produces: one nontechnical entry point plus exact IT commands and a verified/pending test matrix.

- [ ] **Step 1: Write a documentation acceptance checklist before prose**

Create headings and checkbox content for:

- Windows 10/11 x64, Administrator, fixed NTFS `D:`, 20 GB free, Internet, and no-sleep requirements.
- Git/JDK 17/Android SDK 36 installation and verification commands.
- Clone, checkout of `feature/cloudflare-quick-tunnel-pilot`, safe-directory recovery, Node/WinSW cache paths, PostgreSQL/PostGIS install, receiver install, health/listener/service checks, local APK build, and GitHub Actions.
- Quick Tunnel Start/Status/Stop and profile handling without exposing secrets.
- Backup/restore/uninstall boundaries and exact runtime paths excluded from Git.
- Company-owned and personal-phone settings.
- Foreground notification, reboot reminder, force-stop limitation, vendor battery restriction, and WorkManager boundary.
- Verified test evidence versus pending emulator/physical-device work.

- [ ] **Step 2: Fill `NEW-PC-DEPLOYMENT.md` with exact commands**

Commands must be safe for the repository path containing spaces. Include the corrected installer invocation with explicit cache paths:

```powershell
./gps-receiver/windows/Install-GpsReceiver.ps1 `
  -RootPath 'D:\InternalGPS' `
  -NodeArchivePath 'D:\app android\server\cache\node-v24.18.1-win-x64.zip' `
  -WinSWPath 'D:\app android\server\cache\WinSW-x64-v2.12.0.exe'
```

Explain that the fixed installer does not use the unsupported WinSW 2.12 `refresh` command.

- [ ] **Step 3: Fill `REQUIREMENTS-TESTING.md`**

Record required versions and settings, then separate results into:

- Verified now: Node, Pester, Android unit/lint/build, Android 14+ phone, authenticated public tunnel, local/public health, and GPS receipt.
- Required emulator work: API 29, 31, 34, and 36 system images.
- Pending improvement: physical Android 10-13 tests.
- Not guaranteed: force-stop recovery and vendor-specific service survival.

Never write a passing result until the command has run in Task 5.

- [ ] **Step 4: Update existing entry points and operational guides**

Link both new documents from the root README. Align `ANDROID.md` and `OPERATIONS.md` with API-29 permissions, the reboot reminder, separate company/personal settings, no-sleep/Internet requirements, and the Stop cleanup procedure.

- [ ] **Step 5: Run documentation safety scans and commit**

```powershell
rg -n 'tracking-pilot-profile.json|receiver.env|pilot-ingest.dpapi' docs README.md gps-receiver/docs
git grep -n -E 'https://[a-z0-9-]+\.trycloudflare\.com|"ingestToken"\s*:\s*"[A-Za-z0-9_-]{43}"'
git diff --check
git add README.md docs/NEW-PC-DEPLOYMENT.md docs/REQUIREMENTS-TESTING.md gps-receiver/docs/ANDROID.md gps-receiver/docs/OPERATIONS.md
git commit -m "docs: add complete new-PC deployment handover"
```

Expected: only placeholder runtime paths appear; no real URL or secret value is tracked.

---

### Task 5: Run the complete verification matrix and push the branch

**Files:**
- Modify: `docs/REQUIREMENTS-TESTING.md` only if fresh results differ from the provisional matrix.
- Build artifact only: `app/build/outputs/apk/debug/app-debug.apk`

**Interfaces:**
- Consumes: all Tasks 1-4 deliverables.
- Produces: fresh verification evidence, a debug APK, an explicit emulator/physical-device status, and a synchronized GitHub feature branch.

- [ ] **Step 1: Run receiver integration tests with portable PostgreSQL/PostGIS**

Start the existing portable test database on `127.0.0.1:55432`, set:

```powershell
$env:GPS_TEST_DATABASE_URL='postgres://postgres@127.0.0.1:55432/fleet_test_repository'
npm.cmd --prefix ./gps-receiver test
```

Stop the portable database in a `finally` block and verify `pg_isready` reports no response afterward.

- [ ] **Step 2: Run Windows script tests**

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "Invoke-Pester '.\gps-receiver\test\windows-scripts.Tests.ps1' -EnableExit"
```

Expected: every Pester test passes.

- [ ] **Step 3: Run Android unit, lint, and APK build gates**

```powershell
$env:JAVA_HOME=(Get-ChildItem 'C:\Program Files\Eclipse Adoptium' -Directory | Select-Object -First 1 -ExpandProperty FullName)
$env:ANDROID_HOME='C:\Users\khanh\AppData\Local\Android\Sdk'
$env:GRADLE_USER_HOME='D:\app android\.gradle'
$gradleArgs=@('testDebugUnitTest','lintDebug','assembleDebug','--no-daemon','--console=plain','-Pkotlin.compiler.execution.strategy=in-process')
./gradlew.bat $gradleArgs
```

Expected: build success and APK present. Record exact test totals and non-fatal pre-existing toolchain warnings separately.

- [ ] **Step 4: Audit emulator availability without overstating results**

List installed SDK packages and AVDs. For each installed API 29, 31, 34, and 36 image, boot an AVD, wait for `sys.boot_completed=1`, install the APK with `adb install -r`, launch `com.internal.tracker/.MainActivity`, and record the actual outcome. If an image is absent or an emulator cannot boot, record the exact missing prerequisite as pending; do not mark it passed.

- [ ] **Step 5: Update evidence, scan secrets, and verify Git state**

Update `docs/REQUIREMENTS-TESTING.md` only with observed results. Run:

```powershell
git grep -n -E 'https://[a-z0-9-]+\.trycloudflare\.com|"ingestToken"\s*:\s*"[A-Za-z0-9_-]{43}"'
git status --short
git diff --check
```

Commit an evidence-only change if required:

```powershell
git add docs/REQUIREMENTS-TESTING.md
git commit -m "docs: record Android 10 compatibility verification"
```

- [ ] **Step 6: Push and verify the remote commit**

```powershell
git push origin feature/cloudflare-quick-tunnel-pilot
$local = git rev-parse HEAD
$remote = (git ls-remote origin refs/heads/feature/cloudflare-quick-tunnel-pilot).Split("`t")[0]
if ($local -ne $remote) { throw 'GitHub branch does not match local HEAD.' }
```

Expected: local and remote hashes match. Runtime secrets, GPS data, profile JSON, logs, and APK remain untracked.
