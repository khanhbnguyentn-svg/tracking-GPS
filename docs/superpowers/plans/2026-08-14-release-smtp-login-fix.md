# Release SMTP Login Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Publish a signed 2.0.1 APK that can load JavaMail's SMTP provider under R8 and cannot be packaged without valid Gmail build credentials.

**Architecture:** Keep release minification enabled and preserve only the SMTP implementation and MIME handlers that JavaMail loads by resource-declared class name. Validate normalized Gmail build inputs before release signing/configuration, while leaving debug builds credential-optional. Reuse the stable signing and artifact-verification pipeline for version 2.0.1.

**Tech Stack:** Android Gradle Plugin, Kotlin DSL, R8/ProGuard, JavaMail for Android, PowerShell/Pester, Gradle 8.13, Android SDK 36.

## Global Constraints

- Release version is `2.0.1` with `versionCode` 3.
- Release shrinking remains enabled.
- The sender must be a syntactically valid email address.
- The Gmail App Password must contain exactly 16 non-whitespace characters.
- No real Gmail address or App Password may appear in source control, build output, logs, tests, or user-visible errors.
- Debug and ordinary unit-test builds continue to allow empty Gmail defaults.
- The release certificate SHA-256 remains `8F:19:12:A3:4E:D2:CB:9D:DF:88:40:DB:49:A7:69:13:42:51:B3:29:74:84:33:36:78:E2:C6:79:CA:E4:F5:85`.
- Real Gmail authentication is a manual Android-device acceptance step and must not be claimed without device evidence.

## File Map

- `app/build.gradle.kts`: normalize and validate Gmail defaults for release packaging; set version 2.0.1.
- `app/proguard-rules.pro`: preserve dynamically loaded SMTP provider and MIME handler class names.
- `scripts/tests/ReleaseBuildPolicy.Tests.ps1`: behavioral regression tests for missing/invalid release credentials and static keep-rule policy.
- `scripts/tests/BuildReleaseCommand.Tests.ps1`: verify the promoted 2.0.1 artifact and the post-R8 provider mapping.
- `scripts/build-release-apk.ps1`: require and promote version 2.0.1 / code 3.
- `docs/stable-apk-update-runbook.md`: document the new artifact and update acceptance path.

---

### Task 1: Reject Invalid Gmail Credentials During Release Packaging

**Files:**
- Modify: `scripts/tests/ReleaseBuildPolicy.Tests.ps1`
- Modify: `app/build.gradle.kts`

**Interfaces:**
- Consumes: Gradle inputs `SMTP_USER` and `SMTP_APP_PASSWORD` from Gradle properties, environment variables, or ignored `gmail-secrets.properties`.
- Produces: release configuration that fails with redacted messages `Release SMTP user is missing or invalid.` or `Release SMTP App Password must contain exactly 16 non-whitespace characters.`

- [ ] **Step 1: Add failing release credential tests**

Add two Pester cases to `ReleaseBuildPolicy.Tests.ps1`. Each case sets `TRACKER_SIGNING_PROPERTIES` to a missing test path so the credential guard must run before signing validation, configures `JAVA_HOME`, `GRADLE_USER_HOME`, and `ANDROID_USER_HOME` as existing tests do, and runs `:app:assembleRelease --offline --no-daemon`.

```powershell
It 'fails release assembly before signing when the SMTP user is missing' {
    $projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
    $gradle = Join-Path $projectRoot '.tools\gradle-8.13\bin\gradle.bat'
    $previous = @{
        Signing = $env:TRACKER_SIGNING_PROPERTIES
        SmtpUser = $env:SMTP_USER
        SmtpPassword = $env:SMTP_APP_PASSWORD
        JavaHome = $env:JAVA_HOME
        GradleHome = $env:GRADLE_USER_HOME
        AndroidHome = $env:ANDROID_USER_HOME
    }
    try {
        $env:TRACKER_SIGNING_PROPERTIES = Join-Path $TestDrive 'missing-signing.properties'
        $env:SMTP_USER = ''
        $env:SMTP_APP_PASSWORD = 'abcdefghijklmnop'
        $env:JAVA_HOME = Join-Path $projectRoot '.tools\jdk-17.0.20+8'
        $env:GRADLE_USER_HOME = Join-Path $projectRoot '.tools\gradle-home'
        $env:ANDROID_USER_HOME = Join-Path $projectRoot '.tools\android-home'
        Push-Location $projectRoot
        $output = & $gradle :app:assembleRelease --offline --no-daemon 2>&1
        $exitCode = $LASTEXITCODE
    } finally {
        Pop-Location
        $env:TRACKER_SIGNING_PROPERTIES = $previous.Signing
        $env:SMTP_USER = $previous.SmtpUser
        $env:SMTP_APP_PASSWORD = $previous.SmtpPassword
        $env:JAVA_HOME = $previous.JavaHome
        $env:GRADLE_USER_HOME = $previous.GradleHome
        $env:ANDROID_USER_HOME = $previous.AndroidHome
    }

    $exitCode | Should Not Be 0
    ($output -join "`n") | Should Match 'Release SMTP user is missing or invalid'
    ($output -join "`n") | Should Not Match 'abcdefghijklmnop'
}

It 'fails release assembly before signing when the SMTP App Password is invalid' {
    $projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
    $gradle = Join-Path $projectRoot '.tools\gradle-8.13\bin\gradle.bat'
    $previous = @{
        Signing = $env:TRACKER_SIGNING_PROPERTIES
        SmtpUser = $env:SMTP_USER
        SmtpPassword = $env:SMTP_APP_PASSWORD
        JavaHome = $env:JAVA_HOME
        GradleHome = $env:GRADLE_USER_HOME
        AndroidHome = $env:ANDROID_USER_HOME
    }
    try {
        $env:TRACKER_SIGNING_PROPERTIES = Join-Path $TestDrive 'missing-signing.properties'
        $env:SMTP_USER = 'sender@example.com'
        $env:SMTP_APP_PASSWORD = 'short'
        $env:JAVA_HOME = Join-Path $projectRoot '.tools\jdk-17.0.20+8'
        $env:GRADLE_USER_HOME = Join-Path $projectRoot '.tools\gradle-home'
        $env:ANDROID_USER_HOME = Join-Path $projectRoot '.tools\android-home'
        Push-Location $projectRoot
        $output = & $gradle :app:assembleRelease --offline --no-daemon 2>&1
        $exitCode = $LASTEXITCODE
    } finally {
        Pop-Location
        $env:TRACKER_SIGNING_PROPERTIES = $previous.Signing
        $env:SMTP_USER = $previous.SmtpUser
        $env:SMTP_APP_PASSWORD = $previous.SmtpPassword
        $env:JAVA_HOME = $previous.JavaHome
        $env:GRADLE_USER_HOME = $previous.GradleHome
        $env:ANDROID_USER_HOME = $previous.AndroidHome
    }

    $exitCode | Should Not Be 0
    ($output -join "`n") | Should Match 'Release SMTP App Password must contain exactly 16 non-whitespace characters'
    ($output -join "`n") | Should Not Match 'short'
}
```

- [ ] **Step 2: Run the new cases and verify RED**

Run:

```powershell
powershell.exe -NoProfile -Command "Invoke-Pester -Path '.\scripts\tests\ReleaseBuildPolicy.Tests.ps1'"
```

Expected: the two new cases fail because Gradle reaches the existing signing error instead of either SMTP validation message.

- [ ] **Step 3: Normalize and validate release Gmail inputs**

In `app/build.gradle.kts`, define resolved values once after `secret()`:

```kotlin
val smtpUser = secret("SMTP_USER").trim()
val smtpAppPassword = secret("SMTP_APP_PASSWORD").filterNot(Char::isWhitespace)
val emailPattern = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

if (releaseRequested) {
    require(emailPattern.matches(smtpUser)) {
        "Release SMTP user is missing or invalid."
    }
    require(smtpAppPassword.length == 16) {
        "Release SMTP App Password must contain exactly 16 non-whitespace characters."
    }
    // Existing signing-property validation follows these checks.
}
```

Move the existing release signing validation below these values without changing its behavior. Use the normalized values in `buildConfigField`:

```kotlin
buildConfigField("String", "SMTP_USER", quoted(smtpUser))
buildConfigField("String", "SMTP_APP_PASSWORD", quoted(smtpAppPassword))
```

- [ ] **Step 4: Run credential and existing release-policy tests and verify GREEN**

Run the same `Invoke-Pester` command. Expected: all cases in `ReleaseBuildPolicy.Tests.ps1` pass and no test output contains either test password.

- [ ] **Step 5: Commit the credential guard**

```powershell
git add app/build.gradle.kts scripts/tests/ReleaseBuildPolicy.Tests.ps1
git commit -m "fix: require Gmail credentials for release builds"
```

---

### Task 2: Preserve JavaMail Runtime Providers Under R8

**Files:**
- Modify: `scripts/tests/ReleaseBuildPolicy.Tests.ps1`
- Modify: `app/proguard-rules.pro`

**Interfaces:**
- Consumes: `META-INF/javamail.default.providers` and `META-INF/mailcap` class names supplied by the JavaMail/Activation dependencies.
- Produces: stable runtime names for `com.sun.mail.smtp.**` and `com.sun.mail.handlers.**` in the release DEX.

- [ ] **Step 1: Add a failing keep-rule policy test**

Append this source-policy case:

```powershell
It 'preserves JavaMail classes loaded by META-INF provider resources' {
    $projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
    $rules = Get-Content -Raw (Join-Path $projectRoot 'app\proguard-rules.pro')

    $rules | Should Match '(?m)^-keep class com\.sun\.mail\.smtp\.\*\* \{ \*; \}$'
    $rules | Should Match '(?m)^-keep class com\.sun\.mail\.handlers\.\*\* \{ \*; \}$'
}
```

- [ ] **Step 2: Run the policy test and verify RED**

Run the Task 1 Pester command. Expected: only the new keep-rule case fails because `proguard-rules.pro` contains no JavaMail rules.

- [ ] **Step 3: Add the minimal R8 rules**

Replace the inaccurate comment in `app/proguard-rules.pro` with:

```proguard
# JavaMail discovers these implementations by class name in META-INF resources.
-keep class com.sun.mail.smtp.** { *; }
-keep class com.sun.mail.handlers.** { *; }
```

Do not disable minification and do not keep unused IMAP/POP3 implementations.

- [ ] **Step 4: Run the policy test and verify GREEN**

Run the Task 1 Pester command. Expected: all release-policy cases pass.

- [ ] **Step 5: Commit the provider fix**

```powershell
git add app/proguard-rules.pro scripts/tests/ReleaseBuildPolicy.Tests.ps1
git commit -m "fix: preserve JavaMail providers in release builds"
```

---

### Task 3: Promote Version 2.0.1 Through the Stable Release Pipeline

**Files:**
- Modify: `scripts/tests/BuildReleaseCommand.Tests.ps1`
- Modify: `app/build.gradle.kts`
- Modify: `scripts/build-release-apk.ps1`

**Interfaces:**
- Consumes: signed `app/build/outputs/apk/release/app-release.apk` and R8 mapping at `app/build/outputs/mapping/release/mapping.txt`.
- Produces: `dist/tracking-gps-2.0.1.apk`, package `com.internal.tracker`, version `2.0.1 (3)`, and unchanged certificate fingerprint.

- [ ] **Step 1: Update the release command test first**

Change the test name and expectations to version 2.0.1:

```powershell
It 'builds and promotes only the approved version 2.0.1 APK' {
    $projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
    $scriptPath = Join-Path $projectRoot 'scripts\build-release-apk.ps1'
    $outputDirectory = Join-Path $TestDrive 'dist'

    $output = & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $scriptPath `
        -OutputDirectory $outputDirectory 2>&1
    $exitCode = $LASTEXITCODE

    if ($exitCode -ne 0) {
        throw "Release build command failed: $($output -join ' ')"
    }
    $releaseApk = Join-Path $outputDirectory 'tracking-gps-2.0.1.apk'
    Test-Path $releaseApk | Should Be $true
    @(Get-ChildItem $outputDirectory -File).Count | Should Be 1
    ($output -join "`n") | Should Match 'com.internal.tracker'
    ($output -join "`n") | Should Match '2.0.1 \(3\)'
    ($output -join "`n") | Should Match '8F1912A34ED2CB9DDF8840DB49A769134251B3297484333678E2C679CAE4F585'

    $mapping = Get-Content -Raw (Join-Path $projectRoot 'app\build\outputs\mapping\release\mapping.txt')
    $mapping | Should Match '(?m)^com\.sun\.mail\.smtp\.SMTPSSLTransport -> com\.sun\.mail\.smtp\.SMTPSSLTransport:$'
    $mapping | Should Match '(?m)^com\.sun\.mail\.handlers\.text_plain -> com\.sun\.mail\.handlers\.text_plain:$'
}
```

- [ ] **Step 2: Run the release command test and verify RED**

Prerequisite: create ignored `gmail-secrets.properties` from `docs/gmail-build-secrets.example.properties` with the dedicated sender and valid App Password. Never print or commit the file.

Run:

```powershell
powershell.exe -NoProfile -Command "Invoke-Pester -Path '.\scripts\tests\BuildReleaseCommand.Tests.ps1'"
```

Expected: FAIL because the production build and release script still declare version 2.0.0 / code 2.

- [ ] **Step 3: Increment application and release-script identity**

In `app/build.gradle.kts` set:

```kotlin
versionCode = 3
versionName = "2.0.1"
```

In `scripts/build-release-apk.ps1` set:

```powershell
-ExpectedVersionCode '3' `
-ExpectedVersionName '2.0.1' `
```

Keep the existing expected package and certificate fingerprint unchanged.

- [ ] **Step 4: Run the release command test and verify GREEN**

Run the Task 3 Pester command. Expected: PASS; it creates exactly one 2.0.1 APK in the test output directory and confirms both provider names are unchanged in the mapping.

- [ ] **Step 5: Commit the version promotion**

```powershell
git add app/build.gradle.kts scripts/build-release-apk.ps1 scripts/tests/BuildReleaseCommand.Tests.ps1
git commit -m "build: promote SMTP fix as version 2.0.1"
```

---

### Task 4: Update Operations Documentation and Verify the Final Artifact

**Files:**
- Modify: `docs/stable-apk-update-runbook.md`
- Modify: `docs/android-14-device-test-checklist.md`
- Output: `dist/tracking-gps-2.0.1.apk`

**Interfaces:**
- Consumes: the 2.0.1 signed artifact from Task 3.
- Produces: operator instructions, final SHA-256, and an explicit device-test status.

- [ ] **Step 1: Update versioned operating instructions**

Replace current distributed identity and command examples from `2.0.0 (2)` / `tracking-gps-2.0.0.apk` to `2.0.1 (3)` / `tracking-gps-2.0.1.apk`. Update acceptance wording to test the supported upgrade path from 2.0.0 to 2.0.1 without uninstalling. Add these failure entries:

```markdown
- Missing/invalid release Gmail defaults: create the ignored `gmail-secrets.properties` with a valid sender and 16-character App Password; never bypass the release guard.
- `UnknownFailure` during SMTP setup in an older release: install 2.0.1 or newer, whose R8 rules preserve JavaMail providers.
```

Add a checklist item to `docs/android-14-device-test-checklist.md` requiring `Lưu và kiểm tra` to authenticate Gmail successfully after updating to 2.0.1.

- [ ] **Step 2: Run all automated verification**

Run:

```powershell
powershell.exe -NoProfile -Command "Invoke-Pester -Path '.\scripts\tests'"
```

Then run the canonical release builder:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\build-release-apk.ps1
```

Expected: all Pester tests, Android unit tests, lint, and release assembly pass; the canonical builder produces `dist/tracking-gps-2.0.1.apk`.

- [ ] **Step 3: Verify artifact identity without exposing credentials**

Run:

```powershell
Get-FileHash .\dist\tracking-gps-2.0.1.apk -Algorithm SHA256
.\.tools\android-sdk\build-tools\36.0.0\aapt.exe dump badging .\dist\tracking-gps-2.0.1.apk
.\.tools\android-sdk\build-tools\36.0.0\apksigner.bat verify --verbose --print-certs .\dist\tracking-gps-2.0.1.apk
```

Confirm package `com.internal.tracker`, version code 3, version name 2.0.1, the approved certificate fingerprint, and record the APK SHA-256 in the handoff.

- [ ] **Step 4: Run device acceptance when a phone is attached**

Use `adb devices -l`. If a device is present, install with:

```powershell
adb install -r .\dist\tracking-gps-2.0.1.apk
```

Do not uninstall and do not clear package data. Open Settings, enter the second-layer PIN, press `Lưu và kiểm tra`, and require the success message. Confirm prior History, configuration, PIN, tracking state, foreground notification, and schedule remain intact. If no device is attached, mark this step pending.

- [ ] **Step 5: Commit documentation and final evidence metadata**

Do not commit the APK unless the repository's existing release policy explicitly tracks `dist`; keep ignored secrets excluded in all cases.

```powershell
git add docs/stable-apk-update-runbook.md docs/android-14-device-test-checklist.md
git commit -m "docs: release Gmail login fix 2.0.1"
git status --short
```

Expected: clean worktree except intentionally untracked/ignored local release assets, and `gmail-secrets.properties` never appears in Git status.
