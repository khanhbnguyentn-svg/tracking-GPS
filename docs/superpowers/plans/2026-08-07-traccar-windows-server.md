# Traccar Windows Server Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Cài và vận hành Traccar 6.13.3, PostgreSQL và Caddy trực tiếp trên Windows để tối đa 350 điện thoại Android trong LAN gửi GPS qua HTTPS.

**Architecture:** PowerShell module thuần quản lý validation, template và automation; các entry-point script nhỏ gọi module dùng chung. Traccar và PostgreSQL chỉ bind loopback phía ứng dụng, Caddy terminate TLS trên LAN, còn Task Scheduler chạy backup/health/snooze automation.

**Tech Stack:** Windows PowerShell 5.1+, Pester 5, Traccar 6.13.3, PostgreSQL 17.x, Caddy 2.x, Windows Service, Task Scheduler, Windows Firewall.

## Global Constraints

- Chỉ dùng phần mềm miễn phí; không dùng Docker Desktop, WSL hay cloud trả phí.
- Không ghi password, token, vị trí, Device ID hoặc URL chứa dữ liệu GPS vào source/log.
- Traccar ghim đúng `6.13.3`; mọi download phải HTTPS và xác minh SHA-256 từ manifest local.
- Public ports là HTTPS `5055` và `8082`; upstream chỉ bind loopback `15055` và `18082`; PostgreSQL chỉ bind loopback `5432`.
- Firewall chỉ mở profile Private và remote subnet/address được cấu hình.
- Uninstall mặc định không xóa database, backup, certificate hoặc dữ liệu.
- Mọi chức năng PowerShell mới phải có test Pester thất bại trước khi có implementation.

---

### Task 1: Configuration contract and validation

**Files:**
- Create: `server/config/server-config.example.psd1`
- Create: `server/modules/TraccarServer/TraccarServer.psd1`
- Create: `server/modules/TraccarServer/TraccarServer.psm1`
- Create: `server/tests/Config.Tests.ps1`
- Modify: `.gitignore`

**Interfaces:**
- Produces: `Read-ServerConfig -Path <string> -> hashtable`; `Test-ServerConfig -Config <hashtable> -> void/throw`.
- Config keys: `LanAddress`, `LanPrefixLength`, `AdminRemoteAddress`, `PublicGpsPort`, `PublicWebPort`, `TraccarGpsPort`, `TraccarWebPort`, `PostgresPort`, `InstallRoot`, `DataRoot`, `BackupRoot`, `BackupRetentionDays`, `DiskWarningPercent`.

- [ ] Write Pester tests proving valid config loads and invalid IP, duplicate/out-of-range ports, unsafe root paths, retention below 7 days, and disk threshold outside 50–95 throw deterministic messages.
- [ ] Run `Invoke-Pester server/tests/Config.Tests.ps1`; expect failures because module/functions do not exist.
- [ ] Add example PSD1 with `192.168.1.10/24`, admin source `192.168.1.0/24`, ports `5055/8082/15055/18082/5432`, roots under `C:\ProgramData\InternalTraccar`, retention 30 and threshold 85.
- [ ] Implement strict schema validation, canonical absolute paths, port uniqueness and no unknown keys. Add `server/config/server-config.psd1` and runtime secret files to `.gitignore`.
- [ ] Re-run Pester; expect all Task 1 tests pass.

### Task 2: Deterministic templates

**Files:**
- Create: `server/templates/traccar.xml.template`
- Create: `server/templates/Caddyfile.template`
- Create: `server/modules/TraccarServer/Private/Templates.ps1`
- Create: `server/tests/Templates.Tests.ps1`

**Interfaces:**
- Consumes: validated config from Task 1.
- Produces: `New-TraccarConfig -Config -DatabaseUser -DatabasePassword -PendingGroupId`; `New-CaddyConfig -Config -ServerName`.

- [ ] Write tests asserting XML parses, password is XML-escaped, PostgreSQL JDBC URL uses loopback, regex is exactly `^AND-[0-9A-F]{16}$`, and Caddy binds public TLS ports while proxying only to loopback.
- [ ] Run template tests; expect missing-command failures.
- [ ] Implement templates with explicit tokens and rendering that rejects any unresolved `@@TOKEN@@` value.
- [ ] Assert Caddyfile contains `tls internal`, GPS proxy `127.0.0.1:15055`, Web proxy `127.0.0.1:18082`, and never exposes `5432`.
- [ ] Run Task 1–2 tests; expect pass.

### Task 3: Download manifest and safe installers

**Files:**
- Create: `server/config/artifacts.psd1`
- Create: `server/modules/TraccarServer/Private/Artifacts.ps1`
- Create: `server/scripts/Install-TraccarServer.ps1`
- Create: `server/tests/Artifacts.Tests.ps1`
- Create: `server/tests/InstallPlan.Tests.ps1`

**Interfaces:**
- Produces: `Get-VerifiedArtifact -Name -Destination`; `Get-InstallPlan -Config`; entry point supports `-ConfigPath`, `-WhatIf`, and `-Resume`.

- [ ] Write tests that reject HTTP URLs, missing/invalid SHA-256, hash mismatch and filenames escaping the cache directory; test install plan order `preflight,postgres,traccar,caddy,firewall,tasks,smoke` and idempotent resource names.
- [ ] Run tests; expect failures for missing functions.
- [ ] Add pinned official HTTPS URLs and exact hashes for Traccar 6.13.3 Windows, supported PostgreSQL installer, Caddy Windows archive and Pester offline package if required.
- [ ] Implement streaming download to `.partial`, SHA-256 verification and atomic rename. Never execute an unverified artifact.
- [ ] Implement preflight checks for Administrator, Windows version, Private network, available memory/disk, port conflicts and non-overlapping install/data roots. `-WhatIf` performs no mutation.
- [ ] Run Task 1–3 tests; expect pass.

### Task 4: Native service installation and credentials

**Files:**
- Create: `server/modules/TraccarServer/Private/Services.ps1`
- Create: `server/modules/TraccarServer/Private/Credentials.ps1`
- Create: `server/tests/Services.Tests.ps1`

**Interfaces:**
- Produces: `Install-PostgresComponent`, `Install-TraccarComponent`, `Install-CaddyComponent`, `Get-TraccarCredential`, `Set-TraccarCredential`.

- [ ] Write tests against generated process specifications and service descriptors, asserting silent installer arguments do not expose passwords in logs, service names are stable, recovery actions are configured and re-run updates rather than duplicates.
- [ ] Run tests; expect missing-command failures.
- [ ] Implement credential prompt via `Read-Host -AsSecureString`; persist secrets only with Windows DPAPI scoped to the scheduled-task identity in `DataRoot\secrets`, protected with restrictive ACL.
- [ ] Implement PostgreSQL unattended install on loopback, database/user creation through `psql`, Traccar install/configuration, and Caddy service creation through `sc.exe` with automatic delayed start and restart-on-failure.
- [ ] Implement checkpoint state after each successful component so `-Resume` continues safely.
- [ ] Run Task 1–4 tests; expect pass.

### Task 5: Firewall, scheduled tasks and machine safety

**Files:**
- Create: `server/modules/TraccarServer/Private/WindowsResources.ps1`
- Create: `server/tests/WindowsResources.Tests.ps1`

**Interfaces:**
- Produces: `Get-FirewallPlan`, `Set-TraccarFirewall`, `Get-ScheduledTaskPlan`, `Set-TraccarScheduledTasks`.

- [ ] Write tests proving only TCP 5055/8082 are exposed, profile equals Private, GPS/admin sources differ according to config, rules/tasks use project-owned names, and upstream/PostgreSQL ports never appear in allow rules.
- [ ] Run tests; expect failures.
- [ ] Implement idempotent firewall rules and scheduled tasks for daily backup, 5-minute health check and 5-minute expired-suspension processing. Do not disable sleep automatically; preflight emits a blocking remediation message when production readiness requires it.
- [ ] Run Task 1–5 tests; expect pass.

### Task 6: Backup, restore and health automation

**Files:**
- Create: `server/scripts/Backup-TraccarDatabase.ps1`
- Create: `server/scripts/Test-TraccarRestore.ps1`
- Create: `server/scripts/Test-TraccarHealth.ps1`
- Create: `server/modules/TraccarServer/Private/Operations.ps1`
- Create: `server/tests/Operations.Tests.ps1`

**Interfaces:**
- Produces: `Invoke-TraccarBackup`, `Invoke-TraccarRestoreDrill`, `Get-TraccarHealth` returning structured records and process exit codes.

- [ ] Write tests for `.partial` handling, SHA-256 sidecar, retention only after current success, failed backup preserving old files, unique restore database name, service/HTTPS/DB/backup-age/disk checks and redacted logs.
- [ ] Run tests; expect failures.
- [ ] Implement `pg_dump --format=custom`, atomic promotion, checksum, retention, `createdb/pg_restore` drill into a temporary database and guaranteed cleanup guarded by an exact generated database prefix.
- [ ] Implement health results with severity, check name and remediation; write Windows Event Log when available and always return nonzero on critical failure.
- [ ] Run Task 1–6 tests; expect pass.

### Task 7: Timed notification suspension

**Files:**
- Create: `server/scripts/Suspend-TraccarAlert.ps1`
- Create: `server/scripts/Resume-TraccarAlert.ps1`
- Create: `server/scripts/Resume-ExpiredTraccarAlerts.ps1`
- Create: `server/modules/TraccarServer/Private/Suspensions.ps1`
- Create: `server/tests/Suspensions.Tests.ps1`

**Interfaces:**
- Produces: `Suspend-TraccarAlert -DeviceId -Until -Reason -ApprovedBy`; `Resume-TraccarAlert -DeviceId`; `Resume-ExpiredTraccarAlerts -Now`.

- [ ] Write tests requiring future expiry no longer than 24 hours, nonempty reason/approver, exact original group preservation, idempotent resume, retry state on API failure and append-only audit entries without API credentials.
- [ ] Run tests; expect failures.
- [ ] Implement Traccar API client limited to device/group operations, JSON state stored with restrictive ACL, atomic writes and audit logging. Never disable a device; only move between active and suspension groups.
- [ ] Run Task 1–7 tests; expect pass.

### Task 8: Finalize, uninstall and operator documentation

**Files:**
- Create: `server/scripts/Finalize-TraccarServer.ps1`
- Create: `server/scripts/Uninstall-TraccarServer.ps1`
- Create: `server/scripts/Test-TraccarAcceptance.ps1`
- Create: `server/tests/Lifecycle.Tests.ps1`
- Create: `server/README.md`
- Create: `server/docs/INSTALL-WINDOWS.md`
- Create: `server/docs/OPERATIONS-SOP.md`
- Create: `server/docs/CERTIFICATE-ANDROID.md`
- Create: `server/docs/ACCEPTANCE-CHECKLIST.md`
- Modify: `README.md`
- Modify: `config/traccar-profile.example.json`

**Interfaces:**
- Finalize consumes real group/notification IDs and server name after bootstrap.
- Uninstall defaults to services/tasks/firewall only; `-PurgeData` requires an additional interactive confirmation and validates every target is below configured `DataRoot`/`InstallRoot`.

- [ ] Write lifecycle tests proving finalize rejects invalid IDs, acceptance checks both allowed endpoints, uninstall targets only project-owned resources, default preserves data/backup/certificates, and purge rejects roots or paths outside configured roots.
- [ ] Run tests; expect failures.
- [ ] Implement finalize rendering/restart/smoke flow, safe uninstall and acceptance report.
- [ ] Document exact Administrator commands, CA installation, initial Traccar admin password change, group/notification creation, Android profile import, backup/restore, alert response, upgrade and recovery.
- [ ] Update Android example profile to the local HTTPS endpoint without embedding a real internal IP or secret; document which fields the installer prints for the operator to substitute.
- [ ] Run all Pester tests and PowerShell Script Analyzer if available; expect clean pass.
- [ ] Run installer with `-WhatIf`; verify no service, task, firewall rule or file outside a temporary test root is changed.

### Task 9: Install on this PC and acceptance evidence

**Files:**
- Create at runtime (ignored): `server/config/server-config.psd1`
- Create: `server/docs/installation-report.md`

**Interfaces:**
- Consumes approved Administrator/network values and credentials from the user at native prompts.
- Produces running Windows services, exported root CA, backup, restore-drill result and acceptance report.

- [ ] Detect the PC LAN IPv4/subnet and present the exact resolved installation targets before elevation.
- [ ] Run bootstrap as Administrator, reboot only if an installer explicitly requires it, then resume from checkpoint.
- [ ] Ask the user to change the Traccar admin password and create `Chờ PIC xác nhận`, active and suspension groups; capture numeric IDs through finalize parameters.
- [ ] Run finalize and install the root CA on the Windows admin client; provide the Android CA file and profile.
- [ ] Run automated health, backup, restore drill and endpoint smoke tests.
- [ ] Perform manual Android valid/invalid ID, 5/10-minute alert, queue recovery and timed-suspension tests with the user.
- [ ] Record versions, hashes, service states, firewall scopes and test outcomes without secrets in `installation-report.md`.
