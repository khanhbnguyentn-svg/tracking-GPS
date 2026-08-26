# Periodic Email Branch Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Convert `feature/periodic-email-reports` into a focused, buildable Android 2.1 periodic-email pilot without server, receiver, SET 3.0, or unrelated infrastructure material.

**Architecture:** Execute in a dedicated Git worktree, retain the complete tested Android email/tracking dependency graph and only its operational/design documents, then prove the branch builds independently before a normal push.

**Tech Stack:** Git worktrees, PowerShell, Android Gradle Plugin, Kotlin, Jetpack Compose, Room, WorkManager, Gmail SMTP, JUnit 4

**Spec:** `docs/superpowers/specs/2026-08-26-branch-scope-cleanup-design.md`

## Global Constraints

- Create the isolated worktree using `superpowers:using-git-worktrees` before edits.
- Work only on `feature/periodic-email-reports`; never commit to or push `main`.
- Preserve the existing periodic-email Android source, schema migrations, tests, release workflow, signing tools, package name, and release identity.
- Do not introduce any Android SET 3.0 core source or specifications.
- Do not remove a library until `rg` confirms the retained application has no imports or Gradle use for it.
- Use ordinary commits and a normal push; never force-push.

---

### Task 1: Create and validate the isolated periodic worktree

**Files:**
- Create worktree directory: `D:\GPS Innova\tracking-GPS-periodic-cleanup`

**Interfaces:**
- Consumes: `origin/feature/periodic-email-reports`
- Produces: clean checked-out `feature/periodic-email-reports` worktree

- [ ] **Step 1: Read and follow the worktree skill**

Read `superpowers:using-git-worktrees` completely and use its directory-selection and ignore-verification procedure.

- [ ] **Step 2: Record branch heads and create the worktree**

```powershell
git rev-parse origin/main
git rev-parse origin/feature/periodic-email-reports
git worktree list
git worktree add 'D:\GPS Innova\tracking-GPS-periodic-cleanup' feature/periodic-email-reports
```

Expected: the new worktree is on `feature/periodic-email-reports` and clean. If the worktree skill detects a repository-local convention that requires another path, update this plan with the concrete selected path before running the command.

- [ ] **Step 3: Establish a passing baseline**

From the periodic worktree:

```powershell
.\gradlew.bat testDebugUnitTest
```

Expected: PASS. If baseline fails, stop cleanup and diagnose before deleting files.

---

### Task 2: Add a periodic branch-scope regression test

**Files:**
- Create: `app/src/test/java/com/internal/tracker/PeriodicBranchScopeTest.kt`

**Interfaces:**
- Consumes: repository root resolved from `settings.gradle.kts`
- Produces: test preventing server, receiver, SET 3.0 specifications, and unrelated configuration from returning

- [ ] **Step 1: Write the failing test**

```kotlin
class PeriodicBranchScopeTest {
    private fun repositoryRoot(): File {
        val start = File(System.getProperty("user.dir"))
        return generateSequence(start) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile }
            ?: error("Repository root not found from ${start.absolutePath}")
    }

    @Test
    fun `periodic branch excludes unrelated subsystems`() {
        val root = repositoryRoot()
        listOf(
            "server",
            "gps-receiver",
            "config",
            "SYSTEM_REQUIREMENT_SPECIFICATION.md",
            "ANDROID_TECHNICAL_BUILD_SPEC",
        ).forEach { path ->
            assertFalse("Unrelated path must be absent: $path", File(root, path).exists())
        }
    }
}
```

- [ ] **Step 2: Confirm it fails before cleanup**

```powershell
.\gradlew.bat testDebugUnitTest --tests com.internal.tracker.PeriodicBranchScopeTest
```

Expected: FAIL for the existing unrelated paths.

- [ ] **Step 3: Commit the regression test**

```powershell
git add app/src/test/java/com/internal/tracker/PeriodicBranchScopeTest.kt
git commit -m "test: lock periodic email branch scope"
```

---

### Task 3: Remove server, receiver, configuration, and unrelated root files

**Files:**
- Delete: `server/`
- Delete: `gps-receiver/`
- Delete: `config/`
- Delete: `Huong-dan-ky-thuat-setup-Traccar-Server-noi-bo.md`
- Delete: `plan.md`
- Delete: `plan_sample.md`

**Interfaces:**
- Consumes: periodic Android app, which has no build dependency on these trees
- Produces: Android-only repository root

- [ ] **Step 1: Verify no retained Android or workflow references require the deletion targets**

```powershell
rg -n "gps-receiver|server/|traccar-profile|Huong-dan-ky-thuat" app scripts .github build.gradle.kts settings.gradle.kts gradle.properties
```

Expected: no dependency that participates in the Android build or release workflow.

- [ ] **Step 2: Delete the exact tracked paths with patch-based edits**

Do not delete any Android source, Gradle wrapper/configuration, `.github/workflows/android.yml`, or release script.

- [ ] **Step 3: Run the scope test**

```powershell
.\gradlew.bat testDebugUnitTest --tests com.internal.tracker.PeriodicBranchScopeTest
```

Expected: PASS unless SET 3.0 root specifications exist on that branch; if present, delete them as required by the approved design and rerun.

- [ ] **Step 4: Commit root cleanup**

```powershell
git add -A
git commit -m "chore: remove non-email subsystems"
```

---

### Task 4: Retain only periodic-email documentation

**Files:**
- Keep: `docs/email-branch-technical-reference.md`
- Keep: `docs/periodic-gmail-pilot-handover.md`
- Keep: `docs/gmail-build-secrets.example.properties`
- Keep: `docs/android-14-device-test-checklist.md`
- Keep: `docs/stable-apk-update-runbook.md`
- Keep the 2026-08-12 through 2026-08-17 periodic-email designs/plans listed below
- Delete all other `docs/superpowers/specs/` and `docs/superpowers/plans/` entries
- Modify: `README.md`

**Interfaces:**
- Consumes: periodic pilot behavior and operational history
- Produces: minimal document set for build, device operation, SMTP, recovery, diagnostics, and release

- [ ] **Step 1: Keep this exact design list**

```text
2026-08-12-periodic-gmail-location-report-design.md
2026-08-13-continuous-adaptive-gps-tracking-design.md
2026-08-13-remove-cold-start-pin-design.md
2026-08-14-basic-daily-user-guide-design.md
2026-08-14-release-smtp-login-fix-design.md
2026-08-14-stable-manual-apk-updates-design.md
2026-08-17-continuous-high-accuracy-recovery-design.md
2026-08-17-post-update-tracking-recovery-design.md
2026-08-17-tracking-integrity-diagnostics-design.md
```

- [ ] **Step 2: Keep this exact implementation-plan list**

```text
2026-08-12-periodic-gmail-location-report.md
2026-08-13-continuous-adaptive-gps-tracking.md
2026-08-14-basic-daily-user-guide.md
2026-08-14-release-smtp-login-fix.md
2026-08-14-remove-cold-start-pin.md
2026-08-14-stable-manual-apk-updates.md
2026-08-17-continuous-high-accuracy-recovery.md
2026-08-17-post-update-tracking-recovery.md
2026-08-17-tracking-integrity-diagnostics.md
```

- [ ] **Step 3: Delete every other superpowers document**

Print `git ls-files docs/superpowers` after deletion and compare it line-for-line with the two keep lists. The Android 10 handover, Traccar, fleet, Node receiver, Cloudflare, Windows server, and SET 3.0 documents must be absent.

- [ ] **Step 4: Rewrite README as a branch navigation page**

The opening must be:

```markdown
# Android 2.1 Periodic Email Pilot

This branch contains only the Android periodic-email tracking pilot. It does not contain the server, GPS receiver, or Android SET 3.0 design.
```

Then link the five root operational documents, retain accurate Windows build/test/release commands, and retain Gmail configuration guidance without embedding real credentials.

- [ ] **Step 5: Commit documentation cleanup**

```powershell
git add README.md docs
git commit -m "docs: focus periodic email branch"
```

---

### Task 5: Verify the retained email pilot dependency graph

**Files:**
- Verify: `app/**`
- Verify: `.github/workflows/android.yml`
- Verify: `scripts/**`
- Verify: Gradle files and wrapper

**Interfaces:**
- Consumes: cleaned periodic branch
- Produces: evidence that source cleanup did not remove a runtime, schema, test, or release dependency

- [ ] **Step 1: Confirm all expected functional packages remain**

```powershell
git ls-files app/src/main/java/com/internal/tracker
git ls-files app/src/test/java/com/internal/tracker
git ls-files app/src/androidTest/java/com/internal/tracker
```

Expected: config, data, diagnostics, export, history, mail, report, schedule, tracking, UI, and migration coverage remain exactly as present at starting commit `b795501`. Do not infer additional packages from a diff against another branch.

- [ ] **Step 2: Run complete verification**

```powershell
.\gradlew.bat clean testDebugUnitTest lintDebug assembleDebug
```

Expected: BUILD SUCCESSFUL; unit tests pass; lint has zero errors; debug APK exists.

- [ ] **Step 3: Audit diff, artifacts, and secrets**

```powershell
git diff --check origin/feature/periodic-email-reports...HEAD
git status --short
git ls-files server gps-receiver config
git ls-files | rg "(^|/)(build|\.gradle)/|gmail-secrets\.properties|\.jks$|\.keystore$"
```

Expected: clean status, no forbidden paths, no generated build outputs, and no real secrets.

---

### Task 6: Publish periodic cleanup and verify `main`

**Files:**
- Verify: Git refs only

**Interfaces:**
- Consumes: verified periodic commits
- Produces: cleaned remote periodic branch with archive branch unchanged

- [ ] **Step 1: Compare `origin/main` to the recorded starting hash**

```powershell
git rev-parse origin/main
```

Expected: exact match with Task 1; no `main` commit or tree changed.

- [ ] **Step 2: Push the periodic branch normally**

```powershell
git push origin feature/periodic-email-reports
git ls-remote --heads origin feature/periodic-email-reports
```

Expected: remote hash equals the periodic worktree `HEAD`; no force option is used.

- [ ] **Step 3: Report final inventories**

Report local/remote hashes for `main`, SET 3.0, and periodic branches; test/lint/assemble results; and the exact high-level directories retained in each cleaned feature branch.
