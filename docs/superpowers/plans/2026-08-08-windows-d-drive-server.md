# Windows D Drive Server Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the Windows GPS server install safely and idempotently under `D:\InternalGPS` using only the pinned free/open-source runtime components already used by the project.

**Architecture:** A small shared PowerShell path module derives every project-owned directory from one normalized `-RootPath`. Database, receiver and uninstall scripts consume this model; Pester proves validation, `-WhatIf`, ACL and purge behavior before any production command is documented.

**Tech Stack:** Windows PowerShell 5.1, Pester 3.4+, Node.js 24.18.1, PostgreSQL 17.10, PostGIS 3.6.2, WinSW 2.12.0.

## Global Constraints

- Default root is exactly `D:\InternalGPS`.
- PostgreSQL listens only on `127.0.0.1:5432`; no firewall rule opens port 5432.
- Receiver listens on port `5055`; firewall scope remains `Private` and `LocalSubnet`.
- All artifact SHA-256 checks, DPAPI protection, ACL restrictions, authentication and CSRF remain enabled.
- Default uninstall preserves database, GPS history, logs, secrets and `Backup`.
- `-PurgeData` must reject drive roots, parent directories, reparse points and paths outside the normalized deployment root.
- Production installation is not run automatically from tests.

---

### Task 1: Shared D-drive path and prerequisite validation

**Files:**
- Create: `gps-receiver/windows/DeploymentPaths.ps1`
- Modify: `gps-receiver/test/windows-scripts.Tests.ps1`

**Interfaces:**
- Produces: `Resolve-DeploymentPaths([string]$RootPath) -> PSCustomObject` with `Root`, `PostgresRoot`, `PostgresDataRoot`, `ReceiverRoot`, `ReceiverDataRoot`, and `BackupRoot`.
- Produces: `Assert-DeploymentDrive([PSCustomObject]$Paths, [int64]$MinimumFreeBytes = 20GB)`.
- Consumes: Windows `Get-Volume`, `Get-Item`, `Get-PSDrive`, and `System.IO.Path`.

- [ ] **Step 1: Add failing Pester tests for the path model**

Dot-source `DeploymentPaths.ps1`, resolve `D:\InternalGPS`, and assert the exact six paths. Add rejection cases for `D:\`, `D:\InternalGPS\..`, relative paths, non-NTFS volumes, less than `20GB` free, and an existing root carrying the `ReparsePoint` attribute.

```powershell
$paths = Resolve-DeploymentPaths -RootPath 'D:\InternalGPS'
$paths.PostgresRoot | Should Be 'D:\InternalGPS\PostgreSQL'
$paths.PostgresDataRoot | Should Be 'D:\InternalGPS\PostgreSQLData'
$paths.ReceiverRoot | Should Be 'D:\InternalGPS\Receiver'
$paths.ReceiverDataRoot | Should Be 'D:\InternalGPS\ReceiverData'
$paths.BackupRoot | Should Be 'D:\InternalGPS\Backup'
```

- [ ] **Step 2: Run Pester and verify RED**

Run:

```powershell
Set-ExecutionPolicy -Scope Process Bypass -Force
Invoke-Pester .\gps-receiver\test\windows-scripts.Tests.ps1
```

Expected: FAIL because `DeploymentPaths.ps1` does not exist.

- [ ] **Step 3: Implement normalized paths and prerequisite checks**

Use `GetFullPath().TrimEnd('\')`, require an absolute child directory rather than a drive root, reject `FileAttributes.ReparsePoint`, require `DriveType Fixed`, `FileSystemType NTFS`, and `SizeRemaining >= 20GB`. Return only derived paths; do not create directories inside either function.

```powershell
function Resolve-DeploymentPaths {
    param([string]$RootPath = 'D:\InternalGPS')
    $root = [IO.Path]::GetFullPath($RootPath).TrimEnd('\')
    if ([IO.Path]::GetPathRoot($root).TrimEnd('\') -eq $root) { throw 'Deployment root cannot be a drive root.' }
    [pscustomobject]@{
        Root = $root
        PostgresRoot = Join-Path $root 'PostgreSQL'
        PostgresDataRoot = Join-Path $root 'PostgreSQLData'
        ReceiverRoot = Join-Path $root 'Receiver'
        ReceiverDataRoot = Join-Path $root 'ReceiverData'
        BackupRoot = Join-Path $root 'Backup'
    }
}
```

- [ ] **Step 4: Run Pester and parser verification**

Run Pester and parse every `gps-receiver/windows/*.ps1` with `System.Management.Automation.Language.Parser::ParseFile`. Expected: zero failures and zero parse errors.

- [ ] **Step 5: Commit**

```powershell
git add gps-receiver/windows/DeploymentPaths.ps1 gps-receiver/test/windows-scripts.Tests.ps1
git commit -m "feat: validate D drive deployment paths"
```

### Task 2: Install PostgreSQL and receiver from one RootPath

**Files:**
- Modify: `gps-receiver/windows/Install-FleetDatabase.ps1`
- Modify: `gps-receiver/windows/Install-GpsReceiver.ps1`
- Modify: `gps-receiver/test/windows-scripts.Tests.ps1`

**Interfaces:**
- Consumes: `Resolve-DeploymentPaths` and `Assert-DeploymentDrive` from Task 1.
- Produces: both installers with `[string]$RootPath = 'D:\InternalGPS'` and no independent production directory defaults.

- [ ] **Step 1: Add failing installer behavior tests**

Assert both scripts expose `RootPath`, dot-source `DeploymentPaths.ps1`, and use only derived directory properties. Invoke each with `-RootPath (Join-Path $TestDrive 'InternalGPS') -WhatIf`; assert no directory, service or firewall resource is created. Keep existing assertions for loopback PostgreSQL, DPAPI, ACL, PostGIS hash and private-LAN firewall.

- [ ] **Step 2: Run Pester and verify RED**

Expected: FAIL because installers still expose separate `InstallRoot`, `DataRoot`, `PostgresRoot`, and `PostgresDataRoot` defaults on C.

- [ ] **Step 3: Update database installer**

Replace production directory parameters with `RootPath`, resolve the shared model, validate prerequisites after the early `-WhatIf` branch, and pass these exact paths to the pinned native PostgreSQL installer:

```powershell
& $NativeInstallerPath `
    -InstallRoot $paths.PostgresRoot `
    -DataRoot $paths.PostgresDataRoot `
    -ServiceName 'InternalTraccar-PostgreSQL'
```

Store receiver DPAPI/config under `$paths.ReceiverDataRoot`. Create `$paths.BackupRoot` without modifying existing contents. Preserve PostgreSQL/PostGIS idempotency and hash validation.

- [ ] **Step 4: Update receiver installer**

Use `$paths.ReceiverRoot` for Node/WinSW/application and `$paths.ReceiverDataRoot` for data/config/logs. Require the protected environment file created by the database installer, run `npm ci`, run migration, then install/refresh/start WinSW. Keep TCP 5055 restricted to `Private` and `LocalSubnet`.

- [ ] **Step 5: Run Pester, parser and WhatIf verification**

```powershell
Invoke-Pester .\gps-receiver\test\windows-scripts.Tests.ps1
.\gps-receiver\windows\Install-FleetDatabase.ps1 -RootPath 'D:\InternalGPS' -WhatIf
.\gps-receiver\windows\Install-GpsReceiver.ps1 -RootPath 'D:\InternalGPS' -WhatIf
```

Expected: zero Pester failures; WhatIf describes only `D:\InternalGPS` resources and does not create the root.

- [ ] **Step 6: Commit**

```powershell
git add gps-receiver/windows/Install-FleetDatabase.ps1 gps-receiver/windows/Install-GpsReceiver.ps1 gps-receiver/test/windows-scripts.Tests.ps1
git commit -m "ops: install GPS server under D drive root"
```

### Task 3: Safe uninstall, documentation and full verification

**Files:**
- Modify: `gps-receiver/windows/Uninstall-GpsReceiver.ps1`
- Modify: `gps-receiver/docs/OPERATIONS.md`
- Modify: `gps-receiver/README.md`
- Modify: `README.md`
- Modify: `gps-receiver/test/windows-scripts.Tests.ps1`
- Modify: `plan_sample.md`

**Interfaces:**
- Consumes: shared path model and both RootPath installers.
- Produces: uninstall with `[string]$RootPath = 'D:\InternalGPS'`, default data preservation, and exact-root purge gate.

- [ ] **Step 1: Add failing uninstall safety tests**

Assert default uninstall resolves `D:\InternalGPS`, removes receiver binary only, and preserves `PostgreSQLData`, `ReceiverData`, and `Backup`. Assert `-PurgeData` checks exact normalized root and rejects a drive root, parent path and reparse point before `Remove-Item` can run.

- [ ] **Step 2: Run Pester and verify RED**

Expected: FAIL because uninstall still accepts independent C-drive roots and hard-codes `C:\ProgramData\InternalGpsReceiver` as its purge target.

- [ ] **Step 3: Implement RootPath uninstall**

Resolve the shared model. Always remove only WinSW service, receiver firewall rule and `$paths.ReceiverRoot`. Without `-PurgeData`, print the three preserved paths. With `-PurgeData`, repeat exact-root/reparse validation, require `ShouldProcess`, and remove only `$paths.ReceiverDataRoot`; do not remove PostgreSQL or Backup.

- [ ] **Step 4: Update nontechnical and IT documentation**

Document these exact Administrator commands:

```powershell
Set-ExecutionPolicy -Scope Process Bypass -Force
.\gps-receiver\windows\Install-FleetDatabase.ps1 -RootPath 'D:\InternalGPS' -WhatIf
.\gps-receiver\windows\Install-GpsReceiver.ps1 -RootPath 'D:\InternalGPS' -WhatIf
.\gps-receiver\windows\Install-FleetDatabase.ps1 -RootPath 'D:\InternalGPS'
.\gps-receiver\windows\Install-GpsReceiver.ps1 -RootPath 'D:\InternalGPS'
```

Include disk prerequisites, resulting directories, service checks, phone URL format `http://<IP-WINDOWS>:5055`, login/bootstrap command, backup, upgrade, default uninstall and explicit purge warning.

- [ ] **Step 5: Run complete verification**

Start the disposable PostgreSQL test runtime and run:

```powershell
$env:GPS_TEST_DATABASE_URL='postgres://postgres@127.0.0.1:55432/fleet_test_repository'
npm.cmd --prefix .\gps-receiver test
Invoke-Pester .\gps-receiver\test\windows-scripts.Tests.ps1
```

Also run PowerShell parser verification, both installer `-WhatIf` commands, `git diff --check`, and a secret scan. Expected: all Node/Pester tests pass, no parse errors, no writes from WhatIf, no secrets, and no whitespace errors.

- [ ] **Step 6: Record limits and stop test services**

Update `plan_sample.md` with test counts and state that production installation still requires an Administrator session on the target Windows PC. Stop the portable PostgreSQL runtime and verify port 55432 no longer responds.

- [ ] **Step 7: Commit**

```powershell
git add gps-receiver/windows/Uninstall-GpsReceiver.ps1 gps-receiver/docs/OPERATIONS.md gps-receiver/README.md README.md gps-receiver/test/windows-scripts.Tests.ps1 plan_sample.md
git commit -m "docs: hand over D drive GPS server setup"
```
