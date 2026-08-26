# Remove Cold-Start PIN Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Mở ứng dụng trực tiếp tại Status mà không hỏi PIN, đồng thời giữ nguyên PIN theo phiên cho Cấu hình và PIN riêng cho dừng tracking/xóa dữ liệu.

**Architecture:** `AppUiPolicy` tiếp tục là policy thuần Kotlin có unit test và khai báo destination ban đầu cùng tập destination có thể truy cập. `TrackerApp` luôn dựng navigation chính, bỏ toàn bộ cold-start gate nhưng giữ nguyên `settingsUnlocked` và `PinVerificationDialog` cho các thao tác được bảo vệ.

**Tech Stack:** Kotlin 2.2, Jetpack Compose Material 3, JUnit 4, Gradle 8.13.

## Global Constraints

- Làm việc trực tiếp trên nhánh `feature/periodic-email-reports` theo lựa chọn inline execution đã được duyệt.
- Cold start phải mở thẳng `Destination.STATUS`; Status và History không yêu cầu PIN.
- Cấu hình yêu cầu PIN ở lần truy cập đầu tiên trong mỗi phiên app và không hỏi lại sau khi xác thực đúng trong phiên đó.
- Dừng tracking, xóa theo bộ lọc và xóa tất cả luôn yêu cầu PIN riêng cho từng thao tác.
- Không thay đổi logic GPS, báo cáo email, database, bộ lọc History hoặc cách lưu/xác minh PIN.
- Mỗi thay đổi production phải theo RED → GREEN → REFACTOR.

---

## File map

- `app/src/main/java/com/internal/tracker/ui/AppUiPolicy.kt`: khai báo destination ban đầu, destination có thể truy cập và policy PIN.
- `app/src/test/java/com/internal/tracker/ui/AppUiPolicyTest.kt`: bảo vệ hành vi cold start/điều hướng và các thao tác yêu cầu PIN.
- `app/src/main/java/com/internal/tracker/ui/TrackerApp.kt`: dựng navigation trực tiếp, giữ dialog PIN theo action.
- `README.md`: hướng dẫn sử dụng hiện hành.
- `docs/periodic-gmail-pilot-handover.md`: hướng dẫn cấp phát, vận hành và kiểm thử thiết bị.
- `docs/superpowers/specs/2026-08-13-continuous-adaptive-gps-tracking-design.md`: cập nhật phần UI/PIN theo addendum đã duyệt.
- `docs/superpowers/plans/2026-08-13-continuous-adaptive-gps-tracking.md`: ghi nhận yêu cầu cold-start cũ đã bị thay thế.
- `docs/superpowers/specs/2026-08-13-remove-cold-start-pin-design.md`: cập nhật trạng thái sau triển khai.

---

### Task 1: Startup navigation policy

**Files:**
- Modify: `app/src/test/java/com/internal/tracker/ui/AppUiPolicyTest.kt`
- Modify: `app/src/main/java/com/internal/tracker/ui/AppUiPolicy.kt`

**Interfaces:**
- Produces: `AppUiPolicy.initialDestination: Destination` với giá trị `Destination.STATUS`.
- Produces: `AppUiPolicy.destinations(): Set<Destination>` gồm `STATUS`, `SETTINGS`, `HISTORY`.
- Preserves: `AppUiPolicy.requiresPin(action, settingsUnlocked)`.

- [ ] **Step 1: Write failing startup policy tests**

Thay test cold-start PIN cũ bằng hai assertion độc lập:

```kotlin
@Test fun appStartsOnStatus() {
    assertEquals(Destination.STATUS, AppUiPolicy.initialDestination)
}

@Test fun mainNavigationExposesStatusSettingsAndHistory() {
    assertEquals(
        setOf(Destination.STATUS, Destination.SETTINGS, Destination.HISTORY),
        AppUiPolicy.destinations(),
    )
}
```

Các test này bắt lỗi nếu app quay lại màn PIN khi khởi động hoặc ẩn một tab chính.

- [ ] **Step 2: Run focused test and verify RED**

Run:

```powershell
./.tools/gradle-8.13/bin/gradle.bat :app:testDebugUnitTest --tests "com.internal.tracker.ui.AppUiPolicyTest" --offline --no-daemon
```

Expected: compilation fails because `initialDestination` and zero-argument `destinations()` do not exist yet.

- [ ] **Step 3: Implement minimal startup policy**

Remove `Destination.PIN`, then implement:

```kotlin
enum class Destination { STATUS, SETTINGS, HISTORY }

object AppUiPolicy {
    val initialDestination: Destination = Destination.STATUS

    fun destinations(): Set<Destination> =
        setOf(Destination.STATUS, Destination.SETTINGS, Destination.HISTORY)

    // Keep commands() and requiresPin() unchanged.
}
```

- [ ] **Step 4: Run focused test and verify GREEN**

Run the Step 2 command. Expected: `AppUiPolicyTest` passes.

---

### Task 2: Remove the Compose cold-start gate

**Files:**
- Modify: `app/src/main/java/com/internal/tracker/ui/TrackerApp.kt`

**Interfaces:**
- Consumes: `AppUiPolicy.initialDestination`.
- Preserves: `settingsUnlocked`, `PinRequest`, `PinVerificationDialog`, `ProtectedAction.OPEN_SETTINGS`, `STOP_TRACKING`, `DELETE_FILTERED`, `DELETE_ALL`.

- [ ] **Step 1: Initialize navigation from the tested policy**

Use:

```kotlin
var destination by rememberSaveable { mutableStateOf(AppUiPolicy.initialDestination) }
```

Remove the `unlocked` state, the `if (!unlocked)` branch and `Destination.PIN` branch. Always render the existing `Scaffold`.

- [ ] **Step 2: Delete only the obsolete full-app PIN screen**

Delete `PinScreen`. Retain `PinVerificationDialog` and `PinField`, because Cấu hình, stop and delete continue to use them.

- [ ] **Step 3: Compile Compose and run all UI unit tests**

Run:

```powershell
./.tools/gradle-8.13/bin/gradle.bat :app:testDebugUnitTest --tests "com.internal.tracker.ui.*" :app:assembleDebug --offline --no-daemon
```

Expected: UI tests pass and debug APK compiles with exhaustive destination handling.

---

### Task 3: Align operator and design documentation

**Files:**
- Modify: `README.md`
- Modify: `docs/periodic-gmail-pilot-handover.md`
- Modify: `docs/superpowers/specs/2026-08-13-continuous-adaptive-gps-tracking-design.md`
- Modify: `docs/superpowers/plans/2026-08-13-continuous-adaptive-gps-tracking.md`
- Modify: `docs/superpowers/specs/2026-08-13-remove-cold-start-pin-design.md`

**Interfaces:**
- Produces: one consistent operator contract: no PIN at app launch; PIN remains for first Settings access per session, stop and deletes.

- [ ] **Step 1: Update active user documentation**

Replace instructions to open/login with PIN by instructions that the app opens at Status. State explicitly that History is directly accessible and Cấu hình asks PIN only at first access in a process session.

- [ ] **Step 2: Mark superseded design language**

Replace the active cold-start PIN bullets in the continuous-tracking design and implementation plan. Add a supersession note to the earlier periodic-email design/plan only if it is presented as current behavior; do not rewrite historical implementation steps.

- [ ] **Step 3: Record implementation status**

Change the remove-cold-start design status from “chưa triển khai” to implemented on 2026-08-14, after verification completes.

---

### Task 4: Full verification and branch update

**Files:**
- Verify all modified production, tests and documentation files.

**Interfaces:**
- Produces: tested commit on `feature/periodic-email-reports` and updated PR #2.

- [ ] **Step 1: Run full verification**

Run:

```powershell
./.tools/gradle-8.13/bin/gradle.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --offline --no-daemon
```

Expected: all unit tests pass, lint passes and debug APK builds.

- [ ] **Step 2: Review the diff against the approved design**

Confirm no changes were made to tracking, Room, report delivery, History filters or PIN storage. Confirm all protected actions still pass through `AppUiPolicy.requiresPin()` and `PinVerificationDialog`.

- [ ] **Step 3: Commit and push**

```powershell
git add app/src/main/java/com/internal/tracker/ui/AppUiPolicy.kt app/src/main/java/com/internal/tracker/ui/TrackerApp.kt app/src/test/java/com/internal/tracker/ui/AppUiPolicyTest.kt README.md docs/periodic-gmail-pilot-handover.md docs/superpowers/specs/2026-08-13-continuous-adaptive-gps-tracking-design.md docs/superpowers/plans/2026-08-13-continuous-adaptive-gps-tracking.md docs/superpowers/specs/2026-08-13-remove-cold-start-pin-design.md docs/superpowers/plans/2026-08-14-remove-cold-start-pin.md
git commit -m "feat: remove cold-start PIN gate"
git push origin feature/periodic-email-reports
```

Expected: PR #2 receives the verified implementation commit.
