# Android SET 3.0 Branch Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Convert `feature/android-set-3.0-design` into a focused, buildable Android SET 3.0 workspace with no Android 2.1, server, receiver, or unrelated historical material.

**Architecture:** Preserve the Phase 1 `core` platform and wrap it in a minimal Compose application shell. Repository-scope tests and documentation make the branch boundary explicit; exact legacy paths are then deleted and the complete Android build is verified before a normal push.

**Tech Stack:** Git, PowerShell, Android Gradle Plugin, Kotlin, Jetpack Compose, JUnit 4, Room, SQLCipher

**Spec:** `docs/superpowers/specs/2026-08-26-branch-scope-cleanup-design.md`

## Global Constraints

- Work only on `feature/android-set-3.0-design` in the current worktree.
- Do not check out, merge, commit to, or push `main`.
- Preserve application ID `com.internal.tracker`, minimum SDK 26, target/compile SDK 36, version code 7, and version name `3.0.0`.
- Preserve SQLCipher 4.17.0, Room 2.8.4, AndroidX SQLite 2.7.0, and Java/Kotlin 17.
- Preserve `SYSTEM_REQUIREMENT_SPECIFICATION.md` and `ANDROID_TECHNICAL_BUILD_SPEC` as target-system authorities.
- Keep the app buildable; do not restore periodic-email behavior in the replacement shell.
- Use ordinary commits and a normal push; never force-push.

---

### Task 1: Lock the V3 branch boundary with a failing repository test

**Files:**
- Modify: `app/src/test/java/com/internal/tracker/ProjectConfigTest.kt`

**Interfaces:**
- Consumes: repository root found from `settings.gradle.kts`
- Produces: `android SET branch excludes legacy subsystems and declarations` regression test

- [ ] **Step 1: Record immutable starting heads**

Run:

```powershell
git rev-parse origin/main
git rev-parse origin/feature/android-set-3.0-design
git rev-parse origin/feature/periodic-email-reports
git status --short --branch
```

Expected: current branch is `feature/android-set-3.0-design`, status is clean, and all three hashes are recorded in the execution notes.

- [ ] **Step 2: Add the failing branch-scope test**

Refactor the existing root lookup into `repositoryRoot()` and add:

```kotlin
@Test
fun `android SET branch excludes legacy subsystems and declarations`() {
    val root = repositoryRoot()
    val manifest = File(root, "app/src/main/AndroidManifest.xml").readText()

    listOf("server", "gps-receiver", "config").forEach { path ->
        assertFalse("Legacy path must be absent: $path", File(root, path).exists())
    }
    listOf(
        ".schedule.ScheduleReceiver",
        ".tracking.TrackingService",
        ".tracking.VehicleActivityReceiver",
        "androidx.core.content.FileProvider",
    ).forEach { declaration ->
        assertFalse("Legacy manifest declaration must be absent: $declaration", manifest.contains(declaration))
    }
}
```

Add imports for `assertFalse` and keep existing release-identity assertions.

- [ ] **Step 3: Run the new test and confirm the boundary is currently violated**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests com.internal.tracker.ProjectConfigTest
```

Expected: FAIL because `server`, `gps-receiver`, `config`, and legacy manifest declarations still exist.

- [ ] **Step 4: Commit the regression test**

```powershell
git add app/src/test/java/com/internal/tracker/ProjectConfigTest.kt
git commit -m "test: lock Android SET branch scope"
```

---

### Task 2: Replace the Android 2.1 runtime with a minimal SET 3.0 shell

**Files:**
- Create: `app/src/main/java/com/internal/tracker/core/platform/SetPlatformFactory.kt`
- Create: `app/src/main/java/com/internal/tracker/ui/SetHomeShell.kt`
- Modify: `app/src/main/java/com/internal/tracker/TrackerApplication.kt`
- Modify: `app/src/main/java/com/internal/tracker/MainActivity.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Delete: `app/src/main/res/xml/file_paths.xml`

**Interfaces:**
- Consumes: `SetPlatformModule`, `SystemBusinessClock`, `RandomUuidSource`, `StableDeviceIdProvider`, `AndroidDatabasePassphraseStore`, `SqlCipherFactoryProvider`
- Produces: `SetPlatformFactory.create(Context): SetPlatformModule`, `TrackerApplication.platform: SetPlatformModule`, `SetHomeShell()`

- [ ] **Step 1: Add the platform factory**

Implement the Android dependency wiring exactly as:

```kotlin
object SetPlatformFactory {
    fun create(context: Context): SetPlatformModule {
        val appContext = context.applicationContext
        return SetPlatformModule(
            clock = SystemBusinessClock,
            uuidSource = RandomUuidSource,
            deviceIdProvider = StableDeviceIdProvider(
                androidId = {
                    Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ANDROID_ID)
                },
                applicationId = appContext.packageName,
                hasher = DeviceIdHasher(),
                cache = EncryptedDeviceIdCache(appContext),
            ),
            databasePassphraseStore = AndroidDatabasePassphraseStore(appContext),
            sqlCipherFactoryProvider = SqlCipherFactoryProvider(),
        )
    }
}
```

- [ ] **Step 2: Replace the application and activity wiring**

`TrackerApplication` becomes:

```kotlin
class TrackerApplication : Application() {
    val platform: SetPlatformModule by lazy { SetPlatformFactory.create(this) }
}
```

`MainActivity` must initialize `platform` and render the shell:

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (application as TrackerApplication).platform
        setContent { SetHomeShell() }
    }
}
```

- [ ] **Step 3: Add the minimal Compose shell**

Create `SetHomeShell.kt` with a Material 3 surface, title from `R.string.app_name`, and body text from `R.string.phase_status`. Do not add Trip, tracking, mail, Maintenance, or fake status behavior.

```kotlin
@Composable
fun SetHomeShell() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(text = stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(12.dp))
                Text(text = stringResource(R.string.phase_status))
            }
        }
    }
}
```

Set strings to `Android SET 3.0` and `Phase 1 platform foundation`.

- [ ] **Step 4: Reduce the manifest to the shell**

Keep the approved permissions and application security attributes, but keep only `.MainActivity` under `<application>`. Remove FileProvider, ScheduleReceiver, TrackingService, and VehicleActivityReceiver declarations.

- [ ] **Step 5: Continue directly to Task 3 before compiling**

The legacy services and UI compile against `TrackerApplication.container`. Replacing the application wiring intentionally removes that API, so the shell replacement and Task 3 legacy deletions form one atomic compile change.

- [ ] **Step 6: Commit the shell replacement together with Task 3**

Do not create a known-broken intermediate commit. Use the Task 3 commit after the legacy references and dependencies have been removed.

---

### Task 3: Delete Android 2.1 source and narrow build dependencies

**Files:**
- Delete: `app/src/main/java/com/internal/tracker/AppContainer.kt`
- Delete: `app/src/main/java/com/internal/tracker/config/`
- Delete: `app/src/main/java/com/internal/tracker/data/`
- Delete: `app/src/main/java/com/internal/tracker/diagnostics/`
- Delete: `app/src/main/java/com/internal/tracker/export/`
- Delete: `app/src/main/java/com/internal/tracker/history/`
- Delete: `app/src/main/java/com/internal/tracker/mail/`
- Delete: `app/src/main/java/com/internal/tracker/report/`
- Delete: `app/src/main/java/com/internal/tracker/schedule/`
- Delete all of `app/src/main/java/com/internal/tracker/tracking/` except `PermissionState.kt`
- Delete all legacy tests under the matching packages, preserving `tracking/PermissionStateTest.kt`
- Delete: `app/src/androidTest/java/com/internal/tracker/data/`
- Modify: `app/build.gradle.kts`
- Modify: `gradle/libs.versions.toml`

**Interfaces:**
- Consumes: minimal shell and Phase 1 `core/**`
- Produces: compile graph containing only the SET shell, `core/**`, `PermissionState`, and their tests

- [ ] **Step 1: Delete the exact legacy source/test paths**

Use patch-based deletions. Preserve:

```text
app/src/main/java/com/internal/tracker/MainActivity.kt
app/src/main/java/com/internal/tracker/TrackerApplication.kt
app/src/main/java/com/internal/tracker/core/**
app/src/main/java/com/internal/tracker/tracking/PermissionState.kt
app/src/main/java/com/internal/tracker/ui/SetHomeShell.kt
app/src/test/java/com/internal/tracker/ProjectConfigTest.kt
app/src/test/java/com/internal/tracker/core/**
app/src/test/java/com/internal/tracker/tracking/PermissionStateTest.kt
app/src/androidTest/java/com/internal/tracker/core/**
```

- [ ] **Step 2: Remove email-pilot configuration from Gradle**

Remove `gmailSecrets`, `secret`, `quoted`, `SMTP_USER`, `SMTP_APP_PASSWORD`, WorkManager, Play Services Location, Android Mail, and Android Activation. Preserve `buildConfig = true` because `ProjectConfigTest` reads `BuildConfig.APPLICATION_ID`; also preserve release signing, Compose, Room, SQLite, SQLCipher, Security Crypto, required test dependencies, and KSP.

Remove now-unused version-catalog aliases only after `rg` confirms zero references.

- [ ] **Step 3: Run the retained source tests**

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.internal.tracker.core.*" --tests "com.internal.tracker.tracking.PermissionStateTest"
```

Expected: all retained Phase 1 core and permission-state tests PASS. The branch-scope test remains intentionally red until Task 4 removes root legacy paths.

- [ ] **Step 4: Commit the source cleanup**

```powershell
git add app gradle/libs.versions.toml
git commit -m "refactor: replace email pilot with SET shell"
```

---

### Task 4: Remove unrelated repository trees and historical documents

**Files:**
- Delete: `server/`
- Delete: `gps-receiver/`
- Delete: `config/`
- Delete: `Huong-dan-ky-thuat-setup-Traccar-Server-noi-bo.md`
- Delete: `plan.md`
- Delete: `plan_sample.md`
- Delete all `docs/superpowers/specs/*` except the 2026-08-25 SET design and 2026-08-26 cleanup design
- Delete all `docs/superpowers/plans/*` except the 2026-08-25 SET roadmap, 2026-08-25 Phase 1 plan, and the two 2026-08-26 cleanup plans

**Interfaces:**
- Consumes: approved keep list in the cleanup design
- Produces: branch inventory containing only SET 3.0 implementation and authoritative/current documentation

- [ ] **Step 1: Print and review the exact deletion inventory**

```powershell
git ls-files server gps-receiver config
git ls-files docs/superpowers/specs docs/superpowers/plans
```

Expected: every path is categorized by the keep/delete list above before deletion.

- [ ] **Step 2: Delete only the reviewed paths**

Apply explicit file deletions. Do not use a recursive command against a computed directory.

- [ ] **Step 3: Run the branch-scope test**

```powershell
.\gradlew.bat testDebugUnitTest --tests com.internal.tracker.ProjectConfigTest
```

Expected: PASS.

- [ ] **Step 4: Commit repository cleanup**

```powershell
git add -A
git commit -m "chore: remove unrelated repository history"
```

---

### Task 5: Synchronize SRS, Android Technical, and README

**Files:**
- Modify: `SYSTEM_REQUIREMENT_SPECIFICATION.md`
- Modify: `ANDROID_TECHNICAL_BUILD_SPEC`
- Modify: `README.md`

**Interfaces:**
- Consumes: authority order from the cleanup design
- Produces: unambiguous target-vs-current status and branch ownership statements

- [ ] **Step 1: Add a repository-boundary note to the SRS**

Immediately after section 1.2, add a short section stating that this branch contains Android SET and contract fixtures only; production backend/portal/AI and receiver implementations live outside this branch; `main` retains historical server/receiver code; and the SRS remains a target-system specification rather than a claim that all sections are implemented.

- [ ] **Step 2: Add current implementation status to Android Technical**

Under `0. Document authority and use`, state that the cleaned branch currently implements Phase 1 platform foundation plus a minimal shell; Android 2.1 email-pilot classes are deliberately excluded; and later phases must follow the roadmap rather than copying old email-pilot architecture wholesale.

- [ ] **Step 3: Replace README with a SET 3.0 navigation page**

The README must contain:

```markdown
# Android SET 3.0

This branch is the focused Android SET 3.0 workspace. It does not contain the production server, GPS receiver, or Android 2.1 periodic-email pilot.

## Document authority

1. `SYSTEM_REQUIREMENT_SPECIFICATION.md`
2. `ANDROID_TECHNICAL_BUILD_SPEC`
3. `docs/superpowers/specs/2026-08-25-android-set-3.0-design.md`
4. `docs/superpowers/plans/2026-08-25-android-set-3.0-roadmap.md`

## Current status

Phase 1 platform foundation is implemented. The app currently launches a minimal Compose shell; later business functionality remains governed by the roadmap.
```

Also retain concise Windows build/test commands without Gmail setup instructions.

- [ ] **Step 4: Check terminology and links**

```powershell
rg -n "GPS Email Pilot|Traccar|gps-receiver|server/|SMTP_USER|SMTP_APP_PASSWORD" README.md SYSTEM_REQUIREMENT_SPECIFICATION.md ANDROID_TECHNICAL_BUILD_SPEC
```

Expected: only intentional historical/boundary references remain.

- [ ] **Step 5: Commit synchronized documentation**

```powershell
git add README.md SYSTEM_REQUIREMENT_SPECIFICATION.md ANDROID_TECHNICAL_BUILD_SPEC
git commit -m "docs: align SET specifications with branch scope"
```

---

### Task 6: Verify and publish the cleaned V3 branch

**Files:**
- Verify: entire tracked tree

**Interfaces:**
- Consumes: Tasks 1-5
- Produces: tested remote `feature/android-set-3.0-design` head

- [ ] **Step 1: Run the complete Android verification**

```powershell
.\gradlew.bat clean testDebugUnitTest lintDebug assembleDebug
```

Expected: BUILD SUCCESSFUL; all JVM tests pass; lint has zero errors; debug APK exists.

- [ ] **Step 2: Audit inventory and diff quality**

```powershell
git diff --check origin/feature/android-set-3.0-design...HEAD
git status --short
git ls-files server gps-receiver config
git ls-files | rg "(^|/)(build|\.gradle)/|gmail-secrets\.properties|\.jks$|\.keystore$"
```

Expected: clean status, no forbidden trees, no tracked artifacts or secrets.

- [ ] **Step 3: Verify archive branch safety**

```powershell
git rev-parse origin/main
```

Expected: exactly the starting `origin/main` hash recorded in Task 1.

- [ ] **Step 4: Push normally and verify the remote head**

```powershell
git push origin feature/android-set-3.0-design
git ls-remote --heads origin feature/android-set-3.0-design
```

Expected: remote hash equals local `HEAD`; no force option is used.
