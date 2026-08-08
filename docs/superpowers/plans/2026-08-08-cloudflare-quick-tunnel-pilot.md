# Cloudflare Quick Tunnel Pilot Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build and deploy a two-day, one-phone HTTPS GPS pilot through Cloudflare Quick Tunnel with a shared Bearer token that never appears in URLs or the repository.

**Architecture:** Android profile JSON v2 optionally carries a 43-character base64url token and stores it with existing encrypted profile secrets. The receiver optionally requires that token on both ingestion routes, while a single Windows lifecycle script creates the DPAPI-protected server secret, runs `cloudflared`, exports the temporary phone profile, and safely tears the pilot down.

**Tech Stack:** Kotlin, Android 14+, OkHttp, EncryptedSharedPreferences, Node.js 24, PowerShell 5.1/Pester 3.4, Windows DPAPI, Cloudflare `cloudflared`, Gradle 8.13.

## Global Constraints

- This is a two-day test for exactly one Android 14+ phone, not a 100-350-device production deployment.
- Use a Cloudflare Quick Tunnel; do not open router ports and do not expose PostgreSQL port `5432`.
- Quick Tunnel URL changes after `cloudflared` restarts; the Windows server and Internet connection must remain on during the test.
- Use the `Authorization: Bearer TOKEN_VALUE` header; never put the token in a query string, log, process argument, git file, response, or UI after import.
- Generate exactly 32 random bytes and encode them as unpadded base64url, producing 43 characters.
- Keep Device ID automatic and independent from the shared pilot token.
- Store the Android token only in `EncryptedSharedPreferences`; do not change the Room schema.
- Store the server token as a LocalMachine DPAPI file under `D:\InternalGPS\ReceiverData\secrets`.
- Keep `/health` public and management routes protected by the existing admin session, cookie, role, and CSRF controls.
- On pilot shutdown, stop only the recorded `cloudflared` process, disable the token, remove plaintext profile material, restart the receiver, and retain logs.
- Use TDD for every behavior change and commit after every independently passing task.

---

### Task 1: Android profile v2 and encrypted token storage

**Files:**
- Modify: `app/src/main/java/com/internal/tracker/config/ImportedProfile.kt`
- Modify: `app/src/main/java/com/internal/tracker/config/ConfigFileCodec.kt`
- Modify: `app/src/main/java/com/internal/tracker/profile/Profile.kt`
- Modify: `app/src/main/java/com/internal/tracker/profile/EncryptedProfileSecrets.kt`
- Modify: `app/src/main/java/com/internal/tracker/profile/ProfileRepository.kt`
- Modify: `app/src/test/java/com/internal/tracker/config/ConfigFileCodecTest.kt`
- Modify: `app/src/test/java/com/internal/tracker/profile/ProfileRepositoryTest.kt`

**Interfaces:**
- Produces: `ImportedProfile.ingestToken: String?`, `ProfileSecret.ingestToken: String?`, and `Profile.ingestToken: String?`.
- Produces: `ConfigFileCodec.decode` accepting versions 1 and 2; version 2 accepts an optional valid token.
- Consumes: existing `ProfileSecrets` and `EncryptedSharedPreferences`; no Room migration.

- [ ] **Step 1: Add failing config codec tests**

Add tests that decode version 1 without a token, decode version 2 with the 43-character value `AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA`, reject version 2 with `"ingestToken":""`, reject invalid characters/length, and confirm the exported template is version 2 without a secret:

```kotlin
@Test
fun versionTwoAcceptsOnlyAValidOptionalPilotToken() {
    val token = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
    val valid = codec.decode("""{"version":2,"name":"Pilot","host":"a.trycloudflare.com","port":443,"scheme":"https","intervalSeconds":60,"tlsMode":"system","ingestToken":"$token"}""")
    val empty = codec.decode("""{"version":2,"name":"Pilot","host":"a.com","port":443,"scheme":"https","intervalSeconds":60,"tlsMode":"system","ingestToken":""}""")
    val invalid = codec.decode("""{"version":2,"name":"Pilot","host":"a.com","port":443,"scheme":"https","intervalSeconds":60,"tlsMode":"system","ingestToken":"not/a/token"}""")

    assertEquals(token, valid.getOrThrow().ingestToken)
    assertTrue(empty.isFailure)
    assertTrue(invalid.isFailure)
}

@Test
fun legacyVersionOneStillLoadsWithoutAToken() {
    val result = codec.decode("""{"version":1,"name":"LAN","host":"192.168.1.61","port":5055,"scheme":"http","intervalSeconds":60,"tlsMode":"system"}""").getOrThrow()
    assertEquals(null, result.ingestToken)
}
```

- [ ] **Step 2: Run the codec test and verify RED**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests '*ConfigFileCodecTest' --no-daemon
```

Expected: compilation fails because `ingestToken` does not exist and version 2 is rejected.

- [ ] **Step 3: Add failing encrypted profile repository test**

Change the fake profile to carry the same 43-character token and assert it is present in `FakeProfileSecrets.values[id]`, absent from `ProfileEntity`, and returned by `repository.get(id)`:

```kotlin
assertEquals(token, secrets.values.getValue(id).ingestToken)
assertEquals(token, repository.get(id)!!.ingestToken)
assertFalse(ProfileEntity::class.java.declaredFields.any { it.name == "ingestToken" })
```

- [ ] **Step 4: Run the repository test and verify RED**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests '*ProfileRepositoryTest' --no-daemon
```

Expected: compilation fails because the profile secret and profile do not expose `ingestToken`.

- [ ] **Step 5: Implement the minimal profile changes**

Add a trailing nullable property with a default to all three non-Room types:

```kotlin
val ingestToken: String? = null
```

In `ConfigFileCodec`, accept `version in setOf(1, 2)`, add `ingestToken` to allowed fields, read it only when the JSON key exists, and validate:

```kotlin
private val TOKEN = Regex("^[A-Za-z0-9_-]{43}$")
val token = if (json.has("ingestToken")) json.getString("ingestToken") else null
require(token == null || TOKEN.matches(token)) { "Token kết nối không hợp lệ" }
```

Export a version 2 template without `ingestToken`. Pass the token through `ProfileRepository.save`, `ProfileSecret`, `EncryptedProfileSecrets` JSON key `token`, and `toProfile`. Do not add it to `ProfileEntity`.

- [ ] **Step 6: Run focused Android tests**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests '*ConfigFileCodecTest' --tests '*ProfileRepositoryTest' --no-daemon
```

Expected: all selected tests pass.

- [ ] **Step 7: Commit**

```powershell
git add app/src/main/java/com/internal/tracker/config app/src/main/java/com/internal/tracker/profile app/src/test/java/com/internal/tracker/config app/src/test/java/com/internal/tracker/profile
git commit -m "feat: store pilot ingestion token securely"
```

---

### Task 2: Android Bearer transport and authentication diagnostics

**Files:**
- Modify: `app/src/main/java/com/internal/tracker/network/OsmAndRequestFactory.kt`
- Modify: `app/src/main/java/com/internal/tracker/network/OsmAndClient.kt`
- Modify: `app/src/main/java/com/internal/tracker/network/SendResult.kt`
- Modify: `app/src/main/java/com/internal/tracker/network/DiagnosticResult.kt`
- Modify: `app/src/main/java/com/internal/tracker/network/ConnectionTester.kt`
- Modify: `app/src/main/java/com/internal/tracker/ui/TrackerApp.kt`
- Modify: `app/src/test/java/com/internal/tracker/network/OsmAndTransportTest.kt`
- Modify: `app/src/test/java/com/internal/tracker/network/ConnectionTesterTest.kt`
- Modify: `app/src/test/java/com/internal/tracker/worker/QueueUploaderTest.kt`

**Interfaces:**
- Consumes: `Profile.ingestToken` from Task 1.
- Produces: `SendResult.AuthenticationFailure` and `DiagnosticResult.AuthenticationError`.
- Preserves: `QueueUploader` marks a row sent only for `SendResult.Success`.

- [ ] **Step 1: Add failing request and HTTP 401 tests**

Extend `OsmAndTransportTest`:

```kotlin
@Test
fun bearerTokenIsAHeaderAndNeverAQueryParameter() {
    val request = OsmAndRequestFactory().create(profile(ingestToken = token), "AND-0123456789abcdef", location())
    assertEquals("Bearer $token", request.header("Authorization"))
    assertEquals(null, request.url.queryParameter("ingestToken"))
}

@Test
fun unauthorizedResponseHasASpecificResult() {
    MockWebServer().use { server ->
        server.enqueue(MockResponse().setResponseCode(401))
        val result = OsmAndClient(OkHttpClient(), OsmAndRequestFactory()).send(
            profile(server.hostName, server.port, Scheme.HTTP), "AND-0123456789abcdef", location(),
        )
        assertEquals(SendResult.AuthenticationFailure, result)
    }
}
```

Also assert a profile without a token has no Authorization header.

- [ ] **Step 2: Run transport tests and verify RED**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests '*OsmAndTransportTest' --no-daemon
```

Expected: compilation fails because the profile helper and `AuthenticationFailure` are missing.

- [ ] **Step 3: Add failing diagnostic and queue retention tests**

Add `ConnectionTesterTest` assertion:

```kotlin
assertEquals(
    DiagnosticResult.AuthenticationError,
    ConnectionTester(NetworkProbe { SendResult.Success }, LocationSender { _, _, _ -> SendResult.AuthenticationFailure })
        .sendLatest(profile(), deviceId, sample),
)
```

Add a `QueueUploaderTest` case that returns `AuthenticationFailure` and asserts no row is deleted, the first row retry count increments, and the summary is `UploadSummary(0, 1)`.

- [ ] **Step 4: Run the two focused tests and verify RED**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests '*ConnectionTesterTest' --tests '*QueueUploaderTest' --no-daemon
```

Expected: compilation fails on the missing result types.

- [ ] **Step 5: Implement the minimal transport behavior**

In `OsmAndRequestFactory`, build the request first and add the header only for a non-null token:

```kotlin
return Request.Builder().url(url).get().apply {
    profile.ingestToken?.let { header("Authorization", "Bearer $it") }
}.build()
```

Map response code `401` to `SendResult.AuthenticationFailure`; keep other non-2xx codes as `HttpFailure`. Add `DiagnosticResult.AuthenticationError`, map it in `ConnectionTester`, and display this Vietnamese message in `TrackerApp`:

```text
Khóa kết nối không hợp lệ. Hãy nhập lại file cấu hình.
```

- [ ] **Step 6: Run focused Android tests**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests '*OsmAndTransportTest' --tests '*ConnectionTesterTest' --tests '*QueueUploaderTest' --no-daemon
```

Expected: all selected tests pass.

- [ ] **Step 7: Commit**

```powershell
git add app/src/main app/src/test/java/com/internal/tracker/network app/src/test/java/com/internal/tracker/worker
git commit -m "feat: authenticate Android GPS uploads"
```

---

### Task 3: Receiver ingestion token enforcement

**Files:**
- Modify: `gps-receiver/src/core/config.js`
- Modify: `gps-receiver/src/app.js`
- Modify: `gps-receiver/test/core-config.test.js`
- Modify: `gps-receiver/test/app.test.js`

**Interfaces:**
- Produces: `config.ingestToken: string|null` from `GPS_INGEST_TOKEN`.
- Consumes: `createApp({ ingestToken })` through the existing config spread in `src/index.js`.
- Preserves: `/health`, login, dashboard, admin API, and ingestion without a configured token.

- [ ] **Step 1: Add failing configuration tests**

Extend the explicit configuration fixture with a valid 43-character token and expect `ingestToken` in the frozen result. Add invalid cases for empty and malformed configured values:

```javascript
assert.throws(() => loadConfig({
  NODE_ENV: 'test', GPS_INGEST_TOKEN: 'short',
}), /Invalid GPS_INGEST_TOKEN/);
```

Verify the safe test default is `null`.

- [ ] **Step 2: Run the config test and verify RED**

Run:

```powershell
npm.cmd --prefix .\gps-receiver test -- --test-name-pattern="ingest token|explicit test configuration|safe test defaults"
```

Expected: the returned config lacks `ingestToken` and malformed values are accepted.

- [ ] **Step 3: Add failing app integration tests**

Start an app with `ingestToken: token` and a repository that counts inserts. Assert:

```javascript
assert.equal((await fetch(`${base}/?${params}`)).status, 401);
assert.equal((await fetch(`${base}/?${params}`, { headers: { authorization: 'Bearer wrong' } })).status, 401);
assert.equal(inserts, 0);
assert.equal((await fetch(`${base}/?${params}`, { headers: { authorization: `Bearer ${token}` } })).status, 200);
assert.equal((await fetch(`${base}/api/locations`, {
  method: 'POST',
  headers: { 'content-type': 'application/json', authorization: `Bearer ${token}` },
  body: JSON.stringify(payload),
})).status, 200);
assert.equal((await fetch(`${base}/health`)).status, 200);
```

For both unauthorized responses, assert the exact JSON is `{ accepted: false, error: 'UNAUTHORIZED_DEVICE' }` and does not contain the token.

- [ ] **Step 4: Run the app test and verify RED**

Run:

```powershell
npm.cmd --prefix .\gps-receiver test -- --test-name-pattern="ingestion token"
```

Expected: unauthenticated requests return the old acceptance/validation responses.

- [ ] **Step 5: Implement token parsing and constant-time authorization**

In config, accept absent values as `null` and validate configured values with `/^[A-Za-z0-9_-]{43}$/`.

In `app.js`, import `createHash` and `timingSafeEqual`, hash both fixed-length values, and authorize before rate limiting or parsing payloads:

```javascript
function authorized(request, expectedToken) {
  if (!expectedToken) return true;
  const match = /^Bearer ([A-Za-z0-9_-]{43})$/.exec(String(request.headers.authorization || ''));
  const supplied = match?.[1] || '';
  const expectedHash = createHash('sha256').update(expectedToken).digest();
  const suppliedHash = createHash('sha256').update(supplied).digest();
  return timingSafeEqual(expectedHash, suppliedHash);
}
```

At the top of `ingest`, return `401` and `UNAUTHORIZED_DEVICE` when false. Never include either token value in an error.

- [ ] **Step 6: Run receiver tests**

Run:

```powershell
npm.cmd --prefix .\gps-receiver test
```

Expected: every Node test passes, including token tests.

- [ ] **Step 7: Commit**

```powershell
git add gps-receiver/src/core/config.js gps-receiver/src/app.js gps-receiver/test/core-config.test.js gps-receiver/test/app.test.js
git commit -m "feat: require token for public GPS ingestion"
```

---

### Task 4: Protected Windows secret loading

**Files:**
- Modify: `gps-receiver/windows/Start-GpsReceiver.ps1`
- Modify: `gps-receiver/test/windows-scripts.Tests.ps1`

**Interfaces:**
- Consumes: optional env entry `GPS_INGEST_TOKEN_SECRET_FILE=D:\InternalGPS\ReceiverData\secrets\pilot-ingest.dpapi`.
- Produces: process-only `GPS_INGEST_TOKEN` after LocalMachine DPAPI decryption.
- Preserves: receiver startup when no pilot secret path is configured.

- [ ] **Step 1: Add a failing Pester assertion**

Require the launcher to check the optional secret path, call the existing `Unprotect-Text`, and set only the process environment:

```powershell
$launcher | Should Match 'if \(\$env:GPS_INGEST_TOKEN_SECRET_FILE\)'
$launcher | Should Match '\$env:GPS_INGEST_TOKEN\s*=\s*Unprotect-Text \$env:GPS_INGEST_TOKEN_SECRET_FILE'
$launcher | Should Not Match "SetEnvironmentVariable\([^\r\n]+GPS_INGEST_TOKEN[^\r\n]+(''User''|''Machine'')"
```

- [ ] **Step 2: Run Pester and verify RED**

Run:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "Invoke-Pester '.\gps-receiver\test\windows-scripts.Tests.ps1' -EnableExit"
```

Expected: the launcher assertions fail.

- [ ] **Step 3: Implement optional DPAPI token loading**

After reading `receiver.env`, add:

```powershell
if ($env:GPS_INGEST_TOKEN_SECRET_FILE) {
    $env:GPS_INGEST_TOKEN = Unprotect-Text $env:GPS_INGEST_TOKEN_SECRET_FILE
}
```

Do not print either value. Leave the variable absent when the file setting is absent.

- [ ] **Step 4: Run Pester and verify GREEN**

Run the same Pester command. Expected: all tests pass.

- [ ] **Step 5: Commit**

```powershell
git add gps-receiver/windows/Start-GpsReceiver.ps1 gps-receiver/test/windows-scripts.Tests.ps1
git commit -m "feat: load protected ingestion token on Windows"
```

---

### Task 5: Safe Quick Tunnel lifecycle script

**Files:**
- Create: `gps-receiver/windows/QuickTunnelPilot.ps1`
- Modify: `gps-receiver/test/windows-scripts.Tests.ps1`

**Interfaces:**
- Command: `QuickTunnelPilot.ps1 -Action Start -RootPath 'D:\InternalGPS' [-CloudflaredPath (Get-Command cloudflared.exe).Source]`.
- Command: `QuickTunnelPilot.ps1 -Action Status -RootPath 'D:\InternalGPS'`.
- Command: `QuickTunnelPilot.ps1 -Action Stop -RootPath 'D:\InternalGPS'`.
- Produces: `D:\InternalGPS\Pilot\tracking-pilot-profile.json`, `pilot-state.json`, and retained `cloudflared.log`.
- Updates: `receiver.env` key `GPS_INGEST_TOKEN_SECRET_FILE` and service `InternalGpsReceiver`.

- [ ] **Step 1: Add failing parser and static safety tests**

Dot-source the script, feed representative `cloudflared` log lines into `Find-QuickTunnelUrl`, and assert only a valid HTTPS `*.trycloudflare.com` URI is accepted. Static assertions must require:

```powershell
$script | Should Match '\[ValidateSet\(''Start'', ''Status'', ''Stop''\)\]'
$script | Should Match 'RandomNumberGenerator'
$script | Should Match 'DataProtectionScope\]::LocalMachine'
$script | Should Match 'RedirectStandardError'
$script | Should Match 'Start-Process[^\r\n]+-WindowStyle Hidden'
$script | Should Match 'pilot-state\.json'
$script | Should Match 'ExecutablePath'
$script | Should Match 'GPS_INGEST_TOKEN_SECRET_FILE'
$script | Should Not Match 'Write-(Host|Output)[^\r\n]+(token|secret)'
```

The parser test input includes `https://safe-name.trycloudflare.com`, `http://bad.trycloudflare.com`, and `https://trycloudflare.com.attacker.example`.

- [ ] **Step 2: Run Pester and verify RED**

Run the Windows Pester suite. Expected: failure because `QuickTunnelPilot.ps1` does not exist.

- [ ] **Step 3: Implement shared pure helpers first**

Implement these exact functions before action dispatch:

```powershell
function Find-QuickTunnelUrl([string[]]$Lines) { ... }          # returns [Uri] or $null
function Protect-PilotToken([string]$Value, [string]$Path) { ... }
function Set-ReceiverPilotSecret([string]$EnvPath, [string]$SecretPath) { ... }
function Remove-ReceiverPilotSecret([string]$EnvPath) { ... }
function Test-PilotProcess([pscustomobject]$State, [string]$ExpectedExe) { ... }
```

`Find-QuickTunnelUrl` must use `[Uri]::TryCreate`, require `Scheme -eq 'https'`, and require `Host -match '^[a-z0-9-]+\.trycloudflare\.com$'`. Env updates write to a sibling temporary file and atomically replace the original.

- [ ] **Step 4: Implement `Start`**

The action must:

1. Require Administrator and validate the fixed NTFS D-drive root with existing `DeploymentPaths.ps1`.
2. Resolve `cloudflared.exe`, require a valid Authenticode signature, and call `http://127.0.0.1:5055/health`.
3. Refuse to start when a valid recorded pilot process is already running.
4. Create `D:\InternalGPS\Pilot`, restrict inheritance, and grant only SYSTEM, Administrators, Local Service read where needed, and the current Windows user full control.
5. Generate 32 bytes with `RandomNumberGenerator`, encode unpadded base64url, and protect the server copy to `pilot-ingest.dpapi`.
6. Add the DPAPI path to `receiver.env`, restart `InternalGpsReceiver`, and verify `/health`.
7. Launch `cloudflared tunnel --url http://127.0.0.1:5055 --no-autoupdate` hidden, redirect output/error to the pilot log, and save PID plus resolved executable path to `pilot-state.json`.
8. Poll the log for at most 60 seconds and validate the URL with `Find-QuickTunnelUrl`.
9. Call the public root once without a token and require `401`; call it with the token plus `id=bad` and require `400`, proving authentication without creating a device.
10. Serialize the v2 JSON profile with `ConvertTo-Json`, not string concatenation, and write UTF-8 without BOM.
11. Clear byte arrays/plaintext variables in `finally` and print only URL, profile path, PID, and the warning that restart changes the URL.

If steps 6-10 fail, stop only the newly launched PID, remove the env setting and plaintext profile, restart the receiver, and rethrow.

- [ ] **Step 5: Implement `Status` and `Stop`**

`Status` reads state, verifies PID/executable/command line, checks local health and public health, and prints no token.

`Stop` must:

1. Read `pilot-state.json`.
2. Stop only when PID, resolved executable path, and command line match the saved pilot.
3. Remove `GPS_INGEST_TOKEN_SECRET_FILE` atomically from `receiver.env`.
4. Delete `pilot-ingest.dpapi`, `tracking-pilot-profile.json`, and state JSON; retain `cloudflared.log`.
5. Restart `InternalGpsReceiver` and verify LAN `/health`.

- [ ] **Step 6: Run Pester and parser checks**

Run:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "Invoke-Pester '.\gps-receiver\test\windows-scripts.Tests.ps1' -EnableExit"
```

Then parse every Windows script with `System.Management.Automation.Language.Parser.ParseFile`. Expected: all Pester tests pass and no parse errors.

- [ ] **Step 7: Commit**

```powershell
git add gps-receiver/windows/QuickTunnelPilot.ps1 gps-receiver/test/windows-scripts.Tests.ps1
git commit -m "feat: manage Cloudflare quick tunnel pilot"
```

---

### Task 6: Documentation, full verification, and production pilot deployment

**Files:**
- Modify: `gps-receiver/docs/ANDROID.md`
- Modify: `gps-receiver/docs/OPERATIONS.md`
- Modify: `gps-receiver/README.md`
- Runtime only, never commit: `D:\InternalGPS\Pilot\*`
- Build artifact: `app/build/outputs/apk/debug/app-debug.apk`

**Interfaces:**
- Consumes: all code and scripts from Tasks 1-5.
- Produces: updated installed receiver, running Quick Tunnel, one importable profile file, and a tested debug APK.

- [ ] **Step 1: Update operator and phone documentation**

Document exact commands:

```powershell
winget install --id Cloudflare.cloudflared --exact
.\gps-receiver\windows\QuickTunnelPilot.ps1 -Action Start -RootPath 'D:\InternalGPS'
.\gps-receiver\windows\QuickTunnelPilot.ps1 -Action Status -RootPath 'D:\InternalGPS'
.\gps-receiver\windows\QuickTunnelPilot.ps1 -Action Stop -RootPath 'D:\InternalGPS'
```

Document importing `D:\InternalGPS\Pilot\tracking-pilot-profile.json`, deleting it after successful import, keeping the PC awake, and the exact Wi-Fi/mobile-data/offline-queue checklist. State clearly that Quick Tunnel is not production and that Stop revokes the shared token.

- [ ] **Step 2: Run the complete isolated verification suite**

Start the portable PostgreSQL/PostGIS test instance on `127.0.0.1:55432`, then run:

```powershell
$env:GPS_TEST_DATABASE_URL='postgres://postgres@127.0.0.1:55432/fleet_test_repository'
npm.cmd --prefix .\gps-receiver test
powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "Invoke-Pester '.\gps-receiver\test\windows-scripts.Tests.ps1' -EnableExit"
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug --no-daemon
```

Expected: all Node, Pester, Android unit, lint, and APK build tasks pass. Stop the portable database afterwards.

- [ ] **Step 3: Commit documentation and verified source**

```powershell
git add gps-receiver/docs/ANDROID.md gps-receiver/docs/OPERATIONS.md gps-receiver/README.md
git commit -m "docs: add two-day Internet pilot runbook"
git status --short
```

Expected: clean tracked worktree; runtime secrets and profile are not listed.

- [ ] **Step 4: Install cloudflared and deploy the receiver update**

Run from an elevated PowerShell:

```powershell
winget install --id Cloudflare.cloudflared --exact --accept-package-agreements --accept-source-agreements
.\gps-receiver\windows\Install-GpsReceiver.ps1 -RootPath 'D:\InternalGPS'
```

Verify the Authenticode signature, services `Running/Automatic`, PostgreSQL listening only on `127.0.0.1:5432`, receiver listening on `0.0.0.0:5055`, and `/health` returning `200`.

- [ ] **Step 5: Start the pilot and verify the public contract**

Run:

```powershell
.\gps-receiver\windows\QuickTunnelPilot.ps1 -Action Start -RootPath 'D:\InternalGPS'
.\gps-receiver\windows\QuickTunnelPilot.ps1 -Action Status -RootPath 'D:\InternalGPS'
```

Expected: stable process for the current session, HTTPS `*.trycloudflare.com` URL, missing token `401`, correct token reaching validation, local health `200`, and no test device created.

- [ ] **Step 6: Hand the APK and profile to the phone owner**

Provide these local paths:

```text
D:\app android\app\build\outputs\apk\debug\app-debug.apk
D:\InternalGPS\Pilot\tracking-pilot-profile.json
```

Install the APK, import the profile, activate it while tracking is stopped, then delete the plaintext JSON from Windows after confirmed import.

- [ ] **Step 7: Perform the one-phone acceptance test**

On the Android 14+ phone:

1. Send a real location on Wi-Fi and verify the exact automatic Device ID on the dashboard.
2. Disable Wi-Fi, use mobile data, and verify a newer server timestamp.
3. Disable all networking, wait for at least one queued point, re-enable networking, and verify the queue drains without losing the point.
4. Confirm `401` is shown as a configuration/authentication issue by temporarily selecting a profile with an invalid token, then restore the generated profile.
5. Leave the PC powered, prevent sleep, and record any interruption for two days.

- [ ] **Step 8: End the pilot after two days**

Run:

```powershell
.\gps-receiver\windows\QuickTunnelPilot.ps1 -Action Stop -RootPath 'D:\InternalGPS'
```

Verify: recorded tunnel PID is gone, the public URL no longer works, `GPS_INGEST_TOKEN_SECRET_FILE` is absent from `receiver.env`, the DPAPI token and plaintext profile are deleted, LAN `/health` is `200`, receiver remains `Running/Automatic`, and `cloudflared.log` remains for IT review.
