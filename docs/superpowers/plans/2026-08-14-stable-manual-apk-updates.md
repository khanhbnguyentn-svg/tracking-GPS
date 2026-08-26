# Stable Manual APK Updates Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Phát hành `tracking-gps-2.0.0.apk` với version `2.0.0 (2)` và signing identity hiện tại để Android cập nhật đè mà giữ nguyên dữ liệu ứng dụng.

**Architecture:** Một PowerShell module chứa logic kiểm tra fingerprint, tạo keystore phát hành và xác minh APK để có thể kiểm thử bằng Pester. Hai script mỏng điều phối setup một lần và release build; Gradle chỉ ký release khi có private `.signing/signing.properties` hợp lệ và tuyệt đối không fallback sang debug key.

**Tech Stack:** Kotlin 2.2, Android Gradle Plugin/Gradle 8.13, JDK 17 `keytool`, Android Build Tools 36 `apksigner`/`aapt`, Windows PowerShell 5.1, Pester 3.4+.

## Global Constraints

- Làm việc trên branch `feature/periodic-email-reports` với package `com.internal.tracker` và Android 10+ (`minSdk = 29`).
- Bản phát hành mới là `versionName = "2.0.0"`, `versionCode = 2`.
- Certificate SHA-256 bắt buộc là `8F:19:12:A3:4E:D2:CB:9D:DF:88:40:DB:49:A7:69:13:42:51:B3:29:74:84:33:36:78:E2:C6:79:CA:E4:F5:85`.
- APK chính thức chỉ được build trên máy giữ `.signing/`; không thêm updater trong app và không upload signing secrets lên GitHub.
- `.signing/`, `dist/`, keystore, password, signing properties và APK không được Git theo dõi.
- Release build thiếu/sai signing material phải fail; không fallback sang debug signing hoặc tạo khóa mới.
- Mỗi production/script behavior phải theo RED → GREEN → REFACTOR.
- Không gỡ app trên điện thoại acceptance; nếu signature mismatch thì dừng và bảo toàn dữ liệu.

---

## File map

- `scripts/release/ReleaseTools.psm1`: các hàm thuần/biên rõ ràng cho fingerprint, setup signing và APK identity verification.
- `scripts/prepare-release-signing.ps1`: entry point setup một lần; hard-code expected fingerprint đã duyệt.
- `scripts/build-release-apk.ps1`: entry point test/lint/assemble/verify/copy APK.
- `scripts/tests/ReleaseTools.Tests.ps1`: Pester behavior tests dùng keystore tạm được tạo trong `$TestDrive`.
- `scripts/tests/ReleaseBuildPolicy.Tests.ps1`: chứng minh release build không có signing config phải fail.
- `app/build.gradle.kts`: version `2.0.0 (2)` và stable release signing config.
- `.gitignore`: loại `.signing/` và `dist/`.
- `.github/workflows/android.yml`: giữ CI test/lint/debug compile nhưng không quảng bá debug artifact.
- `docs/stable-apk-update-runbook.md`: backup, build, install/update và recovery SOP.
- `README.md`, `docs/periodic-gmail-pilot-handover.md`, `docs/android-14-device-test-checklist.md`: trỏ sang release APK/runbook và checklist data retention.

---

### Task 1: Testable release-signing primitives

**Files:**
- Create: `scripts/release/ReleaseTools.psm1`
- Create: `scripts/tests/ReleaseTools.Tests.ps1`

**Interfaces:**
- Produces: `Normalize-Fingerprint([string]) -> string`.
- Produces: `Get-KeyStoreFingerprint(-KeyToolPath, -KeyStorePath, -StorePassword, -Alias) -> string`.
- Produces: `New-ReleasePassword(-Length 32) -> string`.
- Produces: `Initialize-ReleaseSigning(-KeyToolPath, -SourceKeyStore, -DestinationDirectory, -ExpectedFingerprint) -> PSCustomObject` with non-secret `KeyStorePath`, `PropertiesPath`, `Alias`, `Fingerprint`.
- Produces: `Get-ApkIdentity(-AaptPath, -ApkSignerPath, -ApkPath) -> PSCustomObject` with `PackageName`, `VersionCode`, `VersionName`, `Fingerprint`.
- Produces: `Assert-ApkIdentity(...expected values...)` that throws on any mismatch.

- [ ] **Step 1: Write RED fingerprint/password tests**

Create `scripts/tests/ReleaseTools.Tests.ps1` and import the wished-for module:

```powershell
$modulePath = Join-Path $PSScriptRoot '..\release\ReleaseTools.psm1'
Import-Module $modulePath -Force

Describe 'Release identity primitives' {
    It 'normalizes a SHA-256 fingerprint without changing its bytes' {
        Normalize-Fingerprint 'aa:BB:01' | Should Be 'AABB01'
    }

    It 'creates a non-default 32-character release password' {
        $password = New-ReleasePassword -Length 32
        $password.Length | Should Be 32
        $password | Should Not Be 'android'
        $password | Should Match '[A-Z]'
        $password | Should Match '[a-z]'
        $password | Should Match '[0-9]'
    }
}
```

- [ ] **Step 2: Run Pester and verify RED**

Run:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "Invoke-Pester '.\scripts\tests\ReleaseTools.Tests.ps1' -EnableExit"
```

Expected: FAIL because `ReleaseTools.psm1` does not exist.

- [ ] **Step 3: Implement normalization and cryptographic password generation**

Implement `Normalize-Fingerprint` by removing `:`/whitespace, uppercasing, and rejecting anything other than an even number of hex characters. Implement `New-ReleasePassword` with `System.Security.Cryptography.RandomNumberGenerator`, a 64-character alphanumeric alphabet, rejection sampling (discard bytes outside the largest multiple of alphabet length), and replacement of the first three characters when needed so uppercase/lowercase/digit are always present. Do not use `Get-Random`.

- [ ] **Step 4: Add RED real-keystore tests**

In `BeforeAll`, resolve the project JDK `keytool.exe`. For each test, create an ephemeral PKCS12 source keystore under `$TestDrive`:

```powershell
& $keyTool -genkeypair -alias source -keyalg RSA -keysize 2048 `
    -validity 365 -dname 'CN=Release Test' -storetype PKCS12 `
    -keystore $source -storepass 'SourcePass123' -keypass 'SourcePass123' | Out-Null
$fingerprint = Get-KeyStoreFingerprint -KeyToolPath $keyTool -KeyStorePath $source `
    -StorePassword 'SourcePass123' -Alias 'source'
```

Assert these real behaviors:

```powershell
It 'rejects a source signer with the wrong approved fingerprint' {
    { Initialize-ReleaseSigning -KeyToolPath $keyTool -SourceKeyStore $source `
        -SourceStorePassword 'SourcePass123' -SourceAlias 'source' `
        -DestinationDirectory (Join-Path $TestDrive 'signing') `
        -ExpectedFingerprint ('0' * 64) } | Should Throw '*fingerprint*'
}

It 'imports the same certificate and writes a usable private signing config' {
    $result = Initialize-ReleaseSigning -KeyToolPath $keyTool -SourceKeyStore $source `
        -SourceStorePassword 'SourcePass123' -SourceAlias 'source' `
        -DestinationDirectory (Join-Path $TestDrive 'signing') `
        -ExpectedFingerprint $fingerprint
    $result.Fingerprint | Should Be $fingerprint
    Test-Path $result.KeyStorePath | Should Be $true
    Test-Path $result.PropertiesPath | Should Be $true
    (Get-Content -Raw $result.PropertiesPath) | Should Match 'keyAlias=tracker-release'
}

It 'never overwrites an existing destination keystore' {
    $destination = Join-Path $TestDrive 'existing'
    New-Item -ItemType Directory $destination | Out-Null
    [IO.File]::WriteAllText((Join-Path $destination 'tracker-release.p12'), 'keep')
    { Initialize-ReleaseSigning -KeyToolPath $keyTool -SourceKeyStore $source `
        -SourceStorePassword 'SourcePass123' -SourceAlias 'source' `
        -DestinationDirectory $destination -ExpectedFingerprint $fingerprint } |
        Should Throw '*already exists*'
    (Get-Content -Raw (Join-Path $destination 'tracker-release.p12')) | Should Be 'keep'
}
```

- [ ] **Step 5: Run Pester and verify the new tests RED**

Run the Step 2 command. Expected: failures because keystore functions are absent.

- [ ] **Step 6: Implement real keystore setup**

`Get-KeyStoreFingerprint` must export the certificate to a temporary DER file with `keytool -exportcert`, load it with `System.Security.Cryptography.X509Certificates.X509Certificate2`, call `GetCertHashString(HashAlgorithmName.SHA256)`, and remove the temporary file in `finally`.

`Initialize-ReleaseSigning` must validate the source fingerprint before creating destination files, generate one password, call `keytool -importkeystore` into `tracker-release.p12` with destination alias `tracker-release`, write these exact property keys, then re-read the destination fingerprint:

```properties
storeFile=.signing/tracker-release.p12
storePassword=<generated secret>
keyAlias=tracker-release
keyPassword=<same generated secret>
```

Never return or emit the password. If import or verification fails, remove only incomplete destination files created by the current call.

- [ ] **Step 7: Add and implement APK identity behavior**

Add a test that calls `Get-ApkIdentity` against an APK path supplied through `$env:TEST_RELEASE_APK` when present, otherwise marks the test pending. Implement real parsing of `aapt dump badging` and `apksigner verify --print-certs`; `Assert-ApkIdentity` compares literal package/version/fingerprint values and throws a field-specific error.

- [ ] **Step 8: Run Pester GREEN and commit**

Run the Step 2 command. Expected: all non-APK tests pass; the APK integration test is skipped only when `TEST_RELEASE_APK` is absent.

```powershell
git add scripts/release/ReleaseTools.psm1 scripts/tests/ReleaseTools.Tests.ps1
git commit -m "test: define stable release identity tools"
```

---

### Task 2: One-time signing preparation and private-file boundaries

**Files:**
- Create: `scripts/prepare-release-signing.ps1`
- Modify: `.gitignore`
- Modify: `scripts/tests/ReleaseTools.Tests.ps1`

**Interfaces:**
- Consumes: `Initialize-ReleaseSigning()` from Task 1.
- Produces: `.signing/tracker-release.p12` and `.signing/signing.properties`, both untracked.
- Preserves: approved fingerprint constant and source signing identity.

- [ ] **Step 1: Write a RED wrapper behavior test**

Invoke `prepare-release-signing.ps1` with a temporary source keystore and destination, passing test-only `-ExpectedFingerprint` and source alias/password. Capture all output; assert exit code 0, destination files exist, the source still exists, and output contains fingerprint/path but not either source or generated password. Expected RED: script missing.

- [ ] **Step 2: Implement the thin preparation script**

The script resolves repository paths, default JDK path `.tools/jdk-17.0.20+8/bin/keytool.exe`, default source `.tools/android-home/debug.keystore`, source alias `androiddebugkey`, source password `android`, destination `.signing`, and approved fingerprint. It imports `ReleaseTools.psm1`, calls `Initialize-ReleaseSigning`, then prints only destination path, alias and normalized fingerprint.

Keep `-ExpectedFingerprint`, `-SourceStorePassword`, `-SourceAlias`, and `-DestinationDirectory` parameters so Pester can exercise the real wrapper with disposable material; defaults remain production-safe.

- [ ] **Step 3: Ignore private/generated release files**

Add exact root rules:

```gitignore
/.signing/
/dist/
```

- [ ] **Step 4: Run Pester GREEN and parser verification**

Run:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "Invoke-Pester '.\scripts\tests\ReleaseTools.Tests.ps1' -EnableExit"
```

Then parse all new `.ps1`/`.psm1` files with `System.Management.Automation.Language.Parser.ParseFile`; expect zero syntax errors.

- [ ] **Step 5: Prepare the actual release keystore once**

Run:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\prepare-release-signing.ps1
```

Expected fingerprint: `8F1912A34ED2CB9DDF8840DB49A769134251B3297484333678E2C679CAE4F585`. Confirm `git status --short --untracked-files=all` does not list `.signing`.

- [ ] **Step 6: Commit the wrapper and ignore rules**

```powershell
git add .gitignore scripts/prepare-release-signing.ps1 scripts/tests/ReleaseTools.Tests.ps1
git commit -m "build: prepare private release signing"
```

---

### Task 3: Version 2.0.0 and fail-closed Gradle release signing

**Files:**
- Modify: `app/build.gradle.kts`
- Create: `scripts/tests/ReleaseBuildPolicy.Tests.ps1`

**Interfaces:**
- Consumes: `.signing/signing.properties` keys `storeFile`, `storePassword`, `keyAlias`, `keyPassword`.
- Produces: signed `app/build/outputs/apk/release/app-release.apk` with `2.0.0 (2)`.

- [ ] **Step 1: Write the RED missing-signing-config test**

The Pester test temporarily sets `TRACKER_SIGNING_PROPERTIES` to a nonexistent file and invokes:

```powershell
& '.\.tools\gradle-8.13\bin\gradle.bat' :app:assembleRelease --offline --no-daemon
$LASTEXITCODE | Should Not Be 0
```

Capture output and assert it contains `Release signing properties not found`. Current behavior returns success with an unsigned APK, so this test must fail before implementation.

- [ ] **Step 2: Run the policy test and verify RED**

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "Invoke-Pester '.\scripts\tests\ReleaseBuildPolicy.Tests.ps1' -EnableExit"
```

Expected: FAIL because the current release task does not reject missing signing properties.

- [ ] **Step 3: Implement version and fail-closed signing config**

In `app/build.gradle.kts`:

```kotlin
versionCode = 2
versionName = "2.0.0"
```

Load the signing properties path from `TRACKER_SIGNING_PROPERTIES`, defaulting to `.signing/signing.properties`. Detect whether any requested Gradle task name contains `release` case-insensitively. For release tasks, require the properties file and every exact key, resolve `storeFile` from repository root, require the keystore file, create `signingConfigs.create("stableRelease")`, and assign it to `buildTypes.release.signingConfig`.

Do not read or print property values in errors. Debug/test tasks must remain runnable without `.signing`.

- [ ] **Step 4: Run missing-config policy GREEN**

Run Step 2. Expected: Pester passes because Gradle exits nonzero with the exact safe message.

- [ ] **Step 5: Build the real signed release and verify metadata**

Clear `TRACKER_SIGNING_PROPERTIES`, set the existing project JDK/Gradle/Android user homes, then run:

```powershell
./.tools/gradle-8.13/bin/gradle.bat :app:testDebugUnitTest :app:lintDebug :app:assembleRelease --offline --no-daemon
```

Set `$env:TEST_RELEASE_APK` to `app/build/outputs/apk/release/app-release.apk` and rerun `ReleaseTools.Tests.ps1`. Expected package/version/fingerprint literals: `com.internal.tracker`, `2`, `2.0.0`, approved SHA-256.

- [ ] **Step 6: Commit Gradle policy and tests**

```powershell
git add app/build.gradle.kts scripts/tests/ReleaseBuildPolicy.Tests.ps1
git commit -m "build: sign version 2 release APK"
```

---

### Task 4: Verified release build command

**Files:**
- Create: `scripts/build-release-apk.ps1`
- Modify: `scripts/tests/ReleaseTools.Tests.ps1`

**Interfaces:**
- Consumes: Task 1 `Assert-ApkIdentity`, Task 3 Gradle signing, Android SDK build tools.
- Produces: ignored `dist/tracking-gps-2.0.0.apk` only after all verification gates pass.

- [ ] **Step 1: Write RED failure/promotion tests**

Add wrapper-level tests using a temporary fake Gradle command and fake verifier paths:

- nonzero test/lint/build exit leaves no file under `dist`;
- identity mismatch leaves no promoted file;
- successful gates copy exactly one file named from the verified version: `tracking-gps-2.0.0.apk`.

Dependency paths are explicit script parameters (`-GradlePath`, `-AaptPath`, `-ApkSignerPath`, `-OutputDirectory`) so tests do not change production code or global commands. Expected RED: script missing.

- [ ] **Step 2: Implement fail-closed build script**

Default paths resolve project `.tools` and latest build-tools directory. Run one Gradle invocation:

```text
:app:testDebugUnitTest :app:lintDebug :app:assembleRelease --offline --no-daemon
```

Require exit 0 and the exact release APK path. Call `Assert-ApkIdentity` with package `com.internal.tracker`, version code `2`, version name `2.0.0`, and approved fingerprint. Create `dist` only after verification, copy to a `.partial` file, then atomically rename to `tracking-gps-2.0.0.apk`. Remove `.partial` in `finally` after failures.

- [ ] **Step 3: Run Pester and parser checks GREEN**

Run both release Pester files and parse all `scripts/**/*.ps1`/`.psm1`. Expected: zero failures and syntax errors.

- [ ] **Step 4: Run the real release command**

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\build-release-apk.ps1
```

Expected: `dist/tracking-gps-2.0.0.apk` exists, is ignored, and reports exact package/version/fingerprint.

- [ ] **Step 5: Commit the release build command**

```powershell
git add scripts/build-release-apk.ps1 scripts/tests/ReleaseTools.Tests.ps1
git commit -m "build: verify and package release updates"
```

---

### Task 5: CI and operator runbook

**Files:**
- Modify: `.github/workflows/android.yml`
- Create: `docs/stable-apk-update-runbook.md`
- Modify: `README.md`
- Modify: `docs/periodic-gmail-pilot-handover.md`
- Modify: `docs/android-14-device-test-checklist.md`

**Interfaces:**
- Produces: one authoritative manual release/update SOP.
- Preserves: CI unit/lint/debug compile without signing secrets.

- [ ] **Step 1: Stop advertising CI debug APK as an official update**

Keep `./gradlew testDebugUnitTest lintDebug assembleDebug` in `.github/workflows/android.yml`; remove only the `upload-artifact` step named `gps-email-pilot-debug`. CI remains a verification gate and never receives `.signing`.

- [ ] **Step 2: Write the stable update runbook**

Document exact preparation/build commands, fingerprint, version rules, secure backup/restore drill, file paths, Package Installer steps, `adb install -r` alternative, post-update app-open/reconcile, and failure handling. State explicitly:

- never uninstall after signature conflict before assessing data;
- never rebuild a distributed versionCode;
- loss of keystore prevents future updates;
- the inherited debug private key remains a security limitation;
- `.signing/signing.properties` contains plaintext local secrets and must have restricted access/secure backup.

- [ ] **Step 3: Align current operator docs**

README build output becomes `dist/tracking-gps-2.0.0.apk` via `build-release-apk.ps1`; debug APK is developer-only. Handover references the same release command/runbook. Device checklist adds update from `1.0 (1)` to `2.0.0 (2)` without uninstall, then checks Room history, config, PIN, tracking state, service and report schedule.

- [ ] **Step 4: Run documentation/secret boundary checks**

Run:

```powershell
rg -n "gps-email-pilot-debug|app-debug.apk" README.md docs/periodic-gmail-pilot-handover.md .github/workflows/android.yml
git status --short --untracked-files=all
git check-ignore -v .signing/tracker-release.p12 .signing/signing.properties dist/tracking-gps-2.0.0.apk
git diff --check
```

Expected: no current operator reference treats debug APK as release; all private/generated files are ignored; no whitespace errors.

- [ ] **Step 5: Commit docs and CI**

```powershell
git add .github/workflows/android.yml README.md docs/stable-apk-update-runbook.md docs/periodic-gmail-pilot-handover.md docs/android-14-device-test-checklist.md
git commit -m "docs: operate stable manual APK updates"
```

---

### Task 6: Full verification and update acceptance

**Files:**
- Verify: all files from Tasks 1–5.
- Generated/ignored: `.signing/*`, `dist/tracking-gps-2.0.0.apk`.

**Interfaces:**
- Produces: verified release APK and branch commits ready for PR #2.

- [ ] **Step 1: Run all automated gates fresh**

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "Invoke-Pester '.\scripts\tests\ReleaseTools.Tests.ps1','scripts\tests\ReleaseBuildPolicy.Tests.ps1' -EnableExit"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\build-release-apk.ps1
```

Also run the complete Android debug verification separately:

```powershell
./.tools/gradle-8.13/bin/gradle.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --offline --no-daemon
```

Expected: zero Pester/Android test failures, lint success, debug build success, release package success.

- [ ] **Step 2: Verify release artifact independently**

Use Build Tools 36 `apksigner verify --print-certs` and `aapt dump badging` directly on `dist/tracking-gps-2.0.0.apk`. Confirm exact package, `versionCode='2'`, `versionName='2.0.0'`, and approved SHA-256. Calculate and record the APK file SHA-256 for handoff integrity.

- [ ] **Step 3: Review repository boundaries**

Run `git diff --check`, `git status --short --untracked-files=all`, `git ls-files '*.jks' '*.p12' '*.keystore' '*.apk' '*signing.properties'`, and inspect the branch diff. Expected: no secret/generated signing file or APK is tracked and no GPS/Room/report behavior changed.

- [ ] **Step 4: Perform device update acceptance when a phone is available**

On a phone with installed `1.0 (1)` signed by the approved certificate, install with:

```powershell
adb install -r .\dist\tracking-gps-2.0.0.apk
```

Do not use `-d` and do not uninstall. Open app, then verify History rows, device/Gmail/interval settings, PIN, tracking enabled state, foreground notification and next report schedule. If no phone is attached, report this entire step as pending rather than passed.

- [ ] **Step 5: Commit any verification-only corrections and push**

After fresh verification and diff review:

```powershell
git push origin feature/periodic-email-reports
```

Report commit SHAs, PR #2 URL, release APK path/hash/fingerprint, exact automated test counts, and device acceptance status.
