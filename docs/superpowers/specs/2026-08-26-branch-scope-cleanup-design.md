# Branch Scope Cleanup Design

**Date:** 2026-08-26  
**Status:** Approved  
**Branches:** `feature/android-set-3.0-design`, `feature/periodic-email-reports`  
**Archive/source branch:** `main`

## 1. Goal

Reduce repository context per working branch so later development sessions and context rollovers only load source code and documentation relevant to that branch.

The cleanup must preserve a buildable Android project on both feature branches and must not change `main`, which remains the source/archive for the server, GPS receiver, and historical work.

## 2. Repository Model

Each long-lived branch is a focused workspace rather than a copy of every historical subsystem:

- `main` remains unchanged and retains the complete historical repository, including `server/` and `gps-receiver/`.
- `feature/android-set-3.0-design` contains only the Android SET 3.0 application, its current specifications, implementation plans, build tooling, and tests.
- `feature/periodic-email-reports` contains only the Android 2.1 periodic-email pilot, its operational documentation, build tooling, and tests.

No history rewriting or force-push is required. Cleanup is expressed as ordinary commits on each feature branch.

## 3. Android SET 3.0 Branch

### 3.1 Keep

- Android Gradle wrapper and build configuration required to compile the app.
- GitHub Android build workflow and release-signing scripts that remain applicable.
- Android SET 3.0 Phase 1 core source and tests.
- A minimal Compose application shell that compiles and launches.
- `SYSTEM_REQUIREMENT_SPECIFICATION.md` version 1.2.
- `ANDROID_TECHNICAL_BUILD_SPEC` version 1.2.
- Android SET 3.0 design, roadmap, active phase plan, and this cleanup design/plan.
- A branch-focused README that identifies authoritative documents and implemented status.

### 3.2 Remove

- Android 2.1 periodic-email business code and its tests.
- `server/` and `gps-receiver/`.
- Traccar-specific configuration and setup guides.
- Historical specifications and plans unrelated to Android SET 3.0.
- Root scratch plans and obsolete handover documents.
- Build artifacts, generated files, credentials, and secrets.

### 3.3 Buildability

Cleanup must not leave a documentation-only or broken project. Legacy activity, application, service, receiver, provider, and dependency declarations are replaced by the smallest SET 3.0 Compose shell needed to install and launch the app. The shell must consume the existing Phase 1 platform holder without restoring email-pilot behavior.

The branch README and both authoritative specifications must explicitly state:

- this branch excludes production server and receiver implementations;
- Android 2.1 email-pilot source is not authoritative for SET 3.0;
- the specification describes the target system while the repository currently implements only the recorded phase status.

## 4. Periodic Email Reports Branch

### 4.1 Keep

- Android Gradle wrapper and build configuration required by the email pilot.
- The periodic email/tracking Android source, resources, migrations, and tests.
- GitHub Android workflow and release-signing scripts.
- Email-branch technical reference and pilot handover.
- Gmail build-secrets example, Android device-test checklist, and stable APK update runbook.
- Only design and implementation-plan documents that directly explain the periodic-email Android application, its tracking integrity, recovery behavior, SMTP delivery, user guide, and release process.
- A branch-focused README that declares this branch as the scoped Android 2.1 email pilot and not Android SET 3.0.

### 4.2 Remove

- `server/` and `gps-receiver/`.
- Traccar server/client, Node receiver, fleet website, Cloudflare tunnel, and Windows server material.
- Android SET 3.0 SRS, technical specification, source, and plans.
- Root scratch plans, unrelated configuration, build artifacts, generated files, credentials, and secrets.

### 4.3 Dependency Rule

A file is retained only when it is imported by the periodic Android build, used by its tests or release workflow, or directly documents operation and maintenance of that application. Mere historical presence is not sufficient.

## 5. Documentation Authority

On the SET 3.0 branch, authority is ordered as follows:

1. `SYSTEM_REQUIREMENT_SPECIFICATION.md`
2. `ANDROID_TECHNICAL_BUILD_SPEC`
3. Approved SET 3.0 design
4. Roadmap and active phase implementation plan

If implementation is incomplete, README status must say so instead of weakening or silently changing the specifications.

On the periodic branch, the branch README points to the technical reference, pilot handover, operational checklist/runbook, and retained feature designs. SET 3.0 documents are deliberately absent to prevent cross-version ambiguity.

## 6. Safety and Git Strategy

- Record the starting commit of all three remote branches before mutation.
- Never check out, commit to, merge into, or push `main` during cleanup.
- Use a dedicated worktree for `feature/periodic-email-reports` so branch contents cannot be mixed accidentally.
- Delete only exact reviewed paths; do not use broad recursive targets based on unresolved variables or globs.
- Commit documentation/scoping changes separately from source cleanup when that improves reviewability.
- Push normally to the matching remote feature branch; never force-push.

## 7. Verification

For each cleaned feature branch:

1. Confirm the worktree contains no unexpected changes before edits.
2. Verify forbidden directories and unrelated document names are absent.
3. Run the branch's unit-test task.
4. Run Android lint.
5. Assemble a debug APK.
6. Inspect `git diff --check` and the final tracked-file inventory.
7. Confirm no secrets or generated build outputs are tracked.
8. Commit and push only after all required checks pass.

After both pushes, verify remote branch heads and re-check that `origin/main` still points to its original commit.

## 8. Acceptance Criteria

- `main` is byte-for-byte unchanged at the branch-head level.
- SET 3.0 contains no server, receiver, Traccar, or Android 2.1 email-pilot implementation.
- SET 3.0 remains buildable and launches a minimal Compose shell.
- SET 3.0 SRS and Android Technical specification agree with the branch boundary and current phase status.
- Periodic email reports contains no server, receiver, SET 3.0, or unrelated infrastructure material.
- Periodic email reports retains all source, tests, and operational material needed to build and maintain the Android 2.1 pilot.
- Both feature branches pass their applicable test, lint, and assemble checks.
- Both cleaned commits are available on their corresponding remote branches without force-push.
