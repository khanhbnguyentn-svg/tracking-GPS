# Android SET 3.0 Phase 1 Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Produce a buildable Android SET 3.0 foundation with pinned release identity, stable platform abstractions, deterministic Device ID, Keystore-wrapped database passphrase, and verified SQLCipher/Room integration without yet replacing the 2.1 business flows.

**Architecture:** Add new `core` packages beside the existing email-pilot code so each commit continues to compile. Phase 1 does not wire the new database into the old `AppContainer`; it proves the new encryption and platform interfaces through isolated unit/instrumented tests. Later phases consume these exact interfaces and create the production schema/composition root.

**Tech Stack:** Kotlin 2.2.10, AGP 8.13.0, Java 17, Room 2.8.4, SQLCipher for Android 4.17.0, AndroidX SQLite 2.7.0, Android Keystore, JUnit 4, AndroidX Test.

**Spec:** `docs/superpowers/specs/2026-08-25-android-set-3.0-design.md`

## Global Constraints

- Release identity is `com.internal.tracker`, `versionName = "3.0.0"`, `versionCode = 7`.
- Android baseline is min SDK 26, compile SDK 36, target SDK 36, Java 17.
- This release permits clean install and does not migrate the 2.1 database.
- Future 3.x production schema changes must be non-destructive.
- Business timezone constant is exactly `Asia/Ho_Chi_Minh`.
- Never commit Gmail credentials, HMAC/AES secrets, signing stores, database passphrases, PINs, or private coordinates.
- Never invoke destructive database fallback or delete a database because Keystore unwrap/open failed.
- Phase 1 must not rewrite the old tracking/UI/report code; it adds isolated foundation code that later plans replace deliberately.
- Dependency pins are based on the official [Room release page](https://developer.android.com/jetpack/androidx/releases/room), [AndroidX SQLite release page](https://developer.android.com/jetpack/androidx/releases/sqlite), and [Zetetic SQLCipher Community integration](https://www.zetetic.net/sqlcipher/sqlcipher-for-android-community/).
- SQLCipher is pinned to `4.17.0`: its published AAR metadata is compatible with compile SDK 36; `4.18.0` requires compile SDK 37 and is outside this release's approved toolchain.

---

## File map

### Modify

- `gradle/libs.versions.toml` — pin Room, SQLite, SQLCipher, and test dependencies.
- `app/build.gradle.kts` — set Android 3.0 identity and add encryption/test dependencies.
- `app/src/main/AndroidManifest.xml` — disable cleartext traffic.
- `app/src/test/java/com/internal/tracker/ProjectConfigTest.kt` — enforce release/platform/security build policy.

### Create

- `app/src/main/java/com/internal/tracker/core/time/BusinessClock.kt` — injectable wall/monotonic clock contract.
- `app/src/main/java/com/internal/tracker/core/time/SystemBusinessClock.kt` — Android/system clock implementation.
- `app/src/main/java/com/internal/tracker/core/time/BusinessTime.kt` — canonical timezone and deadline helpers.
- `app/src/test/java/com/internal/tracker/core/time/BusinessTimeTest.kt` — deterministic timezone/deadline tests.
- `app/src/main/java/com/internal/tracker/core/id/UuidSource.kt` — injectable UUID contract.
- `app/src/main/java/com/internal/tracker/core/id/RandomUuidSource.kt` — production UUID implementation.
- `app/src/main/java/com/internal/tracker/core/device/DeviceIdHasher.kt` — pure deterministic Device ID derivation.
- `app/src/main/java/com/internal/tracker/core/device/StableDeviceIdProvider.kt` — Android ID input and encrypted cache orchestration.
- `app/src/main/java/com/internal/tracker/core/device/DeviceIdCache.kt` — persistence boundary for cached identity.
- `app/src/main/java/com/internal/tracker/core/device/EncryptedDeviceIdCache.kt` — Android encrypted-preference adapter.
- `app/src/test/java/com/internal/tracker/core/device/DeviceIdHasherTest.kt` — Device ID determinism/domain tests.
- `app/src/test/java/com/internal/tracker/core/device/StableDeviceIdProviderTest.kt` — source/cache consistency tests.
- `app/src/main/java/com/internal/tracker/core/security/PassphraseEnvelope.kt` — serialized wrapped-passphrase model.
- `app/src/main/java/com/internal/tracker/core/security/PassphraseEnvelopeCodec.kt` — strict binary/base64 envelope codec.
- `app/src/main/java/com/internal/tracker/core/security/DatabasePassphraseStore.kt` — get-or-create/unwrap state contract.
- `app/src/main/java/com/internal/tracker/core/security/DatabasePassphraseManager.kt` — pure get-or-create state machine and collaborator interfaces.
- `app/src/main/java/com/internal/tracker/core/security/AndroidDatabasePassphraseStore.kt` — Keystore AES-GCM implementation.
- `app/src/main/java/com/internal/tracker/core/security/DatabaseKeyResult.kt` — explicit Ready/RecoveryRequired result.
- `app/src/test/java/com/internal/tracker/core/security/PassphraseEnvelopeCodecTest.kt` — codec corruption/version tests.
- `app/src/test/java/com/internal/tracker/core/security/DatabasePassphraseStoreTest.kt` — creation/reopen/recovery state tests.
- `app/src/main/java/com/internal/tracker/core/database/SqlCipherFactoryProvider.kt` — converts a ready passphrase into SQLCipher `SupportOpenHelperFactory`.
- `app/src/androidTest/java/com/internal/tracker/core/database/EncryptionProbeEntity.kt` — test-only entity.
- `app/src/androidTest/java/com/internal/tracker/core/database/EncryptionProbeDao.kt` — test-only DAO.
- `app/src/androidTest/java/com/internal/tracker/core/database/EncryptionProbeDatabase.kt` — test-only Room database.
- `app/src/androidTest/java/com/internal/tracker/core/database/SqlCipherRoomIntegrationTest.kt` — encrypted create/reopen/wrong-key test.
- `app/src/main/java/com/internal/tracker/core/platform/SetPlatformModule.kt` — immutable holder for Phase 1 platform services.
- `app/src/test/java/com/internal/tracker/core/platform/SetPlatformModuleTest.kt` — construction contract test.

---

### Task 1: Pin the Android 3.0 release and encryption dependencies

**Files:**

- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/test/java/com/internal/tracker/ProjectConfigTest.kt`

**Interfaces:**

- Consumes: existing Gradle version catalog and release signing logic.
- Produces: aliases `androidx-sqlite`, `sqlcipher-android`; Android config 3.0.0/code 7/min 26; Room 2.8.4.

- [ ] **Step 1: Replace the old project policy assertions with failing Android 3.0 assertions**

Replace `ProjectConfigTest.kt` with the following root-aware policy test:

```kotlin
class ProjectConfigTest {
    private fun projectFile(relativePath: String): String {
        val start = File(System.getProperty("user.dir"))
        val root = generateSequence(start) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile }
            ?: error("Repository root not found from ${start.absolutePath}")
        return File(root, relativePath).readText()
    }

    @Test
    fun `package name stays stable`() {
        assertEquals("com.internal.tracker", BuildConfig.APPLICATION_ID)
    }

    @Test
    fun `project pins Android SET 3 release identity and encrypted storage`() {
        val appBuild = projectFile("app/build.gradle.kts")
        val catalog = projectFile("gradle/libs.versions.toml")
        val manifest = projectFile("app/src/main/AndroidManifest.xml")

        assertTrue(appBuild.contains("minSdk = 26"))
        assertTrue(appBuild.contains("targetSdk = 36"))
        assertTrue(appBuild.contains("versionCode = 7"))
        assertTrue(appBuild.contains("versionName = \"3.0.0\""))
        assertTrue(catalog.contains("room = \"2.8.4\""))
        assertTrue(catalog.contains("sqlcipher = \"4.17.0\""))
        assertTrue(catalog.contains("sqlite = \"2.7.0\""))
        assertTrue(appBuild.contains("implementation(libs.sqlcipher.android)"))
        assertTrue(appBuild.contains("implementation(libs.androidx.sqlite)"))
        assertTrue(manifest.contains("android:allowBackup=\"false\""))
        assertTrue(manifest.contains("android:usesCleartextTraffic=\"false\""))
    }
}
```

Add imports for `java.io.File`, `org.junit.Assert.assertEquals`, `org.junit.Assert.assertTrue`, and `org.junit.Test`.

- [ ] **Step 2: Run the policy test and verify red**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.internal.tracker.ProjectConfigTest --no-daemon
```

Expected: FAIL because the project still declares 2.1.0/code 6/min 29 and lacks SQLCipher aliases.

- [ ] **Step 3: Update the version catalog**

Set/add:

```toml
room = "2.8.4"
sqlite = "2.7.0"
sqlcipher = "4.17.0"

androidx-sqlite = { module = "androidx.sqlite:sqlite", version.ref = "sqlite" }
sqlcipher-android = { module = "net.zetetic:sqlcipher-android", version.ref = "sqlcipher" }
```

Keep Room 2.x APIs for SQLCipher `SupportOpenHelperFactory`; do not adopt Room 3 alpha or a SQLiteDriver in this phase.

- [ ] **Step 4: Update the app Gradle configuration**

Change only:

```kotlin
minSdk = 26
versionCode = 7
versionName = "3.0.0"
```

Add:

```kotlin
implementation(libs.androidx.sqlite)
implementation(libs.sqlcipher.android)
```

Change the application manifest to `android:usesCleartextTraffic="false"`. Do not remove the existing signing block or SMTP dependencies yet; later phases replace the old business implementation while keeping the app compiling.

- [ ] **Step 5: Run policy and full unit tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --no-daemon
```

Expected: all current tests PASS. If an old test intentionally asserts version 2.1, update only that obsolete expectation to 3.0.

- [ ] **Step 6: Commit**

```powershell
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/AndroidManifest.xml app/src/test/java/com/internal/tracker/ProjectConfigTest.kt
git commit -m "build: start Android SET 3.0 foundation"
```

---

### Task 2: Add deterministic time and UUID boundaries

**Files:**

- Create: `app/src/main/java/com/internal/tracker/core/time/BusinessClock.kt`
- Create: `app/src/main/java/com/internal/tracker/core/time/SystemBusinessClock.kt`
- Create: `app/src/main/java/com/internal/tracker/core/time/BusinessTime.kt`
- Create: `app/src/main/java/com/internal/tracker/core/id/UuidSource.kt`
- Create: `app/src/main/java/com/internal/tracker/core/id/RandomUuidSource.kt`
- Test: `app/src/test/java/com/internal/tracker/core/time/BusinessTimeTest.kt`

**Interfaces:**

- Consumes: Java `Instant`, `ZoneId`, `LocalDate` and Android `SystemClock` only in production implementation.
- Produces:
  - `BusinessClock.now(): Instant`
  - `BusinessClock.elapsedRealtimeNanos(): Long`
  - `BusinessTime.ZONE: ZoneId`
  - `BusinessTime.expenseLockAt(endActionAt: Instant): Instant`
  - `UuidSource.newUuid(): UUID`

- [ ] **Step 1: Write failing business-time tests**

```kotlin
class BusinessTimeTest {
    @Test
    fun `business zone is Ho Chi Minh`() {
        assertEquals("Asia/Ho_Chi_Minh", BusinessTime.ZONE.id)
    }

    @Test
    fun `expense ending late day ten locks at start of day twelve`() {
        val end = ZonedDateTime.of(2026, 8, 10, 23, 30, 0, 0, BusinessTime.ZONE).toInstant()
        val expected = ZonedDateTime.of(2026, 8, 12, 0, 0, 0, 0, BusinessTime.ZONE).toInstant()
        assertEquals(expected, BusinessTime.expenseLockAt(end))
    }

    @Test
    fun `expense ending after midnight locks two dates later`() {
        val end = ZonedDateTime.of(2026, 8, 11, 0, 30, 0, 0, BusinessTime.ZONE).toInstant()
        val expected = ZonedDateTime.of(2026, 8, 13, 0, 0, 0, 0, BusinessTime.ZONE).toInstant()
        assertEquals(expected, BusinessTime.expenseLockAt(end))
    }
}
```

- [ ] **Step 2: Run tests and verify red**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.internal.tracker.core.time.BusinessTimeTest --no-daemon
```

Expected: FAIL because the core time types do not exist.

- [ ] **Step 3: Implement the contracts**

```kotlin
interface BusinessClock {
    fun now(): Instant
    fun elapsedRealtimeNanos(): Long
}

object BusinessTime {
    val ZONE: ZoneId = ZoneId.of("Asia/Ho_Chi_Minh")

    fun expenseLockAt(endActionAt: Instant): Instant =
        endActionAt.atZone(ZONE).toLocalDate().plusDays(2).atStartOfDay(ZONE).toInstant()
}

fun interface UuidSource {
    fun newUuid(): UUID
}
```

`SystemBusinessClock` returns `Instant.now()` and `SystemClock.elapsedRealtimeNanos()`. `RandomUuidSource` returns `UUID.randomUUID()`.

- [ ] **Step 4: Run focused and full tests**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.internal.tracker.core.time.BusinessTimeTest --no-daemon
.\gradlew.bat :app:testDebugUnitTest --no-daemon
```

Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/internal/tracker/core/time app/src/main/java/com/internal/tracker/core/id app/src/test/java/com/internal/tracker/core/time
git commit -m "feat: add deterministic platform time boundaries"
```

---

### Task 3: Add pure Device ID derivation

**Files:**

- Create: `app/src/main/java/com/internal/tracker/core/device/DeviceIdHasher.kt`
- Create: `app/src/main/java/com/internal/tracker/core/device/DeviceIdCache.kt`
- Create: `app/src/main/java/com/internal/tracker/core/device/EncryptedDeviceIdCache.kt`
- Create: `app/src/main/java/com/internal/tracker/core/device/StableDeviceIdProvider.kt`
- Test: `app/src/test/java/com/internal/tracker/core/device/DeviceIdHasherTest.kt`
- Test: `app/src/test/java/com/internal/tracker/core/device/StableDeviceIdProviderTest.kt`

**Interfaces:**

- Consumes: raw Android ID supplied as a string; application ID; `DeviceIdCache`.
- Produces:
  - `DeviceIdHasher.derive(androidId: String, applicationId: String): String`
  - `DeviceIdCache.read(): String?`
  - `DeviceIdCache.write(deviceId: String)`
  - `StableDeviceIdProvider.get(): DeviceIdResult`

- [ ] **Step 1: Write failing derivation tests**

```kotlin
class DeviceIdHasherTest {
    private val hasher = DeviceIdHasher()

    @Test
    fun `same Android and application IDs produce same uppercase hex ID`() {
        val first = hasher.derive("android-123", "com.internal.tracker")
        val second = hasher.derive("android-123", "com.internal.tracker")
        assertEquals(first, second)
        assertTrue(first.matches(Regex("[0-9A-F]{64}")))
    }

    @Test
    fun `application identity is domain separated`() {
        assertNotEquals(
            hasher.derive("android-123", "com.internal.tracker"),
            hasher.derive("android-123", "another.package"),
        )
    }
}
```

- [ ] **Step 2: Write failing cache-consistency tests**

Use an in-memory fake `DeviceIdCache`:

```kotlin
private class FakeDeviceIdCache(var value: String? = null) : DeviceIdCache {
    override fun read(): String? = value
    override fun write(deviceId: String) { value = deviceId }
}

@Test
fun `provider persists first derived identity`() {
    val cache = FakeDeviceIdCache()
    val provider = StableDeviceIdProvider(
        androidId = { "android-123" },
        applicationId = "com.internal.tracker",
        hasher = DeviceIdHasher(),
        cache = cache,
    )

    val result = provider.get()

    assertTrue(result is DeviceIdResult.Ready)
    assertEquals((result as DeviceIdResult.Ready).value, cache.value)
}

@Test
fun `provider reports recovery instead of replacing a mismatched cached identity`() {
    val cache = FakeDeviceIdCache("A".repeat(64))
    val provider = StableDeviceIdProvider(
        androidId = { "different-android-id" },
        applicationId = "com.internal.tracker",
        hasher = DeviceIdHasher(),
        cache = cache,
    )
    assertTrue(provider.get() is DeviceIdResult.IdentityMismatch)
    assertEquals("A".repeat(64), cache.value)
}
```

- [ ] **Step 3: Run tests and verify red**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests 'com.internal.tracker.core.device.*' --no-daemon
```

Expected: FAIL because Device ID types do not exist.

- [ ] **Step 4: Implement deterministic SHA-256 derivation**

Hash UTF-8 bytes of the unambiguous versioned input:

```kotlin
private const val DOMAIN = "android-set-device-id:v1"
val canonical = "$DOMAIN\u0000$applicationId\u0000$androidId"
```

Return 64-character uppercase hex. Define:

```kotlin
sealed interface DeviceIdResult {
    data class Ready(val value: String) : DeviceIdResult
    data class IdentityMismatch(val cached: String, val derived: String) : DeviceIdResult
    data object StorageRecoveryRequired : DeviceIdResult
    data object SourceUnavailable : DeviceIdResult
}
```

Never silently overwrite a mismatch.

Implement `EncryptedDeviceIdCache` using the existing AndroidX Security encrypted-preferences dependency, private file `set-device-identity`, and key `device-id-v1`. Validate `[0-9A-F]{64}` on both read and write. Define `DeviceIdentityStorageException` and have `StableDeviceIdProvider` return an explicit storage-recovery result when the cache cannot be read; it must not derive and persist a replacement after a storage error.

- [ ] **Step 5: Run focused/full tests**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests 'com.internal.tracker.core.device.*' --no-daemon
.\gradlew.bat :app:testDebugUnitTest --no-daemon
```

Expected: PASS.

- [ ] **Step 6: Commit**

```powershell
git add app/src/main/java/com/internal/tracker/core/device app/src/test/java/com/internal/tracker/core/device
git commit -m "feat: derive stable SET device identity"
```

---

### Task 4: Define and test the wrapped-passphrase envelope

**Files:**

- Create: `app/src/main/java/com/internal/tracker/core/security/PassphraseEnvelope.kt`
- Create: `app/src/main/java/com/internal/tracker/core/security/PassphraseEnvelopeCodec.kt`
- Create: `app/src/test/java/com/internal/tracker/core/security/PassphraseEnvelopeCodecTest.kt`

**Interfaces:**

- Consumes: AES-GCM IV and ciphertext bytes.
- Produces:
  - `PassphraseEnvelope(version: Int, iv: ByteArray, ciphertext: ByteArray)`
  - `PassphraseEnvelopeCodec.encode(envelope): String`
  - `PassphraseEnvelopeCodec.decode(encoded): PassphraseEnvelope`

- [ ] **Step 1: Write failing round-trip and corruption tests**

```kotlin
class PassphraseEnvelopeCodecTest {
    private val codec = PassphraseEnvelopeCodec()

    @Test
    fun `round trips versioned iv and ciphertext`() {
        val source = PassphraseEnvelope(1, ByteArray(12) { it.toByte() }, ByteArray(48) { (it + 20).toByte() })
        val decoded = codec.decode(codec.encode(source))
        assertEquals(1, decoded.version)
        assertArrayEquals(source.iv, decoded.iv)
        assertArrayEquals(source.ciphertext, decoded.ciphertext)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects unknown envelope version`() {
        codec.encode(PassphraseEnvelope(2, ByteArray(12), ByteArray(48)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects truncated envelope`() {
        codec.decode("AQ==")
    }
}
```

- [ ] **Step 2: Run test and verify red**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.internal.tracker.core.security.PassphraseEnvelopeCodecTest --no-daemon
```

Expected: FAIL because codec types do not exist.

- [ ] **Step 3: Implement a bounded binary codec**

Use a fixed binary layout before Base64:

```text
1 byte version
1 byte IV length
4 byte ciphertext length (big endian)
IV bytes
ciphertext bytes
```

Accept version 1 only, IV length 12 only, ciphertext length 17..1024 bytes, and no trailing bytes. Copy arrays at model boundaries so callers cannot mutate stored values.

- [ ] **Step 4: Run focused/full tests**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.internal.tracker.core.security.PassphraseEnvelopeCodecTest --no-daemon
.\gradlew.bat :app:testDebugUnitTest --no-daemon
```

Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/internal/tracker/core/security/PassphraseEnvelope.kt app/src/main/java/com/internal/tracker/core/security/PassphraseEnvelopeCodec.kt app/src/test/java/com/internal/tracker/core/security/PassphraseEnvelopeCodecTest.kt
git commit -m "feat: encode wrapped database passphrases"
```

---

### Task 5: Implement Keystore-wrapped database passphrase state

**Files:**

- Create: `app/src/main/java/com/internal/tracker/core/security/DatabaseKeyResult.kt`
- Create: `app/src/main/java/com/internal/tracker/core/security/DatabasePassphraseStore.kt`
- Create: `app/src/main/java/com/internal/tracker/core/security/DatabasePassphraseManager.kt`
- Create: `app/src/main/java/com/internal/tracker/core/security/AndroidDatabasePassphraseStore.kt`
- Test: `app/src/test/java/com/internal/tracker/core/security/DatabasePassphraseStoreTest.kt`

**Interfaces:**

- Consumes: `PassphraseEnvelopeCodec`, a private preference/file slot, AES-GCM wrap/unwrap functions, secure random.
- Produces:

```kotlin
sealed interface DatabaseKeyResult {
    data class Ready(val passphrase: ByteArray) : DatabaseKeyResult
    data class RecoveryRequired(val reason: Reason) : DatabaseKeyResult

    enum class Reason { KEY_MISSING, ENVELOPE_INVALID, AUTHENTICATION_FAILED, STORAGE_ERROR }
}

fun interface DatabasePassphraseStore {
    fun getOrCreate(): DatabaseKeyResult
}

internal interface PassphraseEnvelopeSlot {
    fun read(): String?
    fun write(encoded: String)
}

internal interface PassphraseWrapper {
    fun wrap(plaintext: ByteArray): PassphraseEnvelope
    fun unwrap(envelope: PassphraseEnvelope): ByteArray
}

internal fun interface SecureByteSource {
    fun next(size: Int): ByteArray
}
```

- [ ] **Step 1: Write failing state-machine tests around pure collaborators**

Implement the tests with in-memory `PassphraseEnvelopeSlot`, deterministic `SecureByteSource`, and fake `PassphraseWrapper`. Test:

```kotlin
@Test
fun `first access creates 32 bytes wraps stores and returns a copy`() { /* assert calls and 32-byte result */ }

@Test
fun `existing envelope unwraps without generating a new passphrase`() { /* assert no random generation */ }

@Test
fun `unwrap authentication failure requests recovery and never overwrites envelope`() { /* assert unchanged slot */ }

@Test
fun `invalid envelope requests recovery and never creates replacement`() { /* assert no write */ }
```

- [ ] **Step 2: Run tests and verify red**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.internal.tracker.core.security.DatabasePassphraseStoreTest --no-daemon
```

Expected: FAIL because passphrase store types do not exist.

- [ ] **Step 3: Implement pure orchestration and Android adapter**

Implement `DatabasePassphraseManager(slot, wrapper, byteSource, codec)` as the pure state machine and let `AndroidDatabasePassphraseStore` adapt private SharedPreferences, SecureRandom, and Android Keystore. Use a non-exportable Android Keystore AES key:

```text
alias: android-set-db-wrap-v1
algorithm: AES/GCM/NoPadding
key size: 256
randomized encryption required
user authentication not required (service must restart unattended)
```

Generate a random 32-byte SQLCipher passphrase. Wrap with a fresh 12-byte IV. Store versioned envelope in private preferences. On any existing-envelope decode/unwrap failure, return `RecoveryRequired`; never generate or persist a replacement.

Ensure temporary byte arrays are copied at output and zeroed when ownership ends where practical.

- [ ] **Step 4: Run focused/full unit tests**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.internal.tracker.core.security.DatabasePassphraseStoreTest --no-daemon
.\gradlew.bat :app:testDebugUnitTest --no-daemon
```

Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/internal/tracker/core/security app/src/test/java/com/internal/tracker/core/security
git commit -m "feat: protect database passphrase with Keystore"
```

---

### Task 6: Prove SQLCipher and Room integration on device

**Files:**

- Create: `app/src/main/java/com/internal/tracker/core/database/SqlCipherFactoryProvider.kt`
- Create: `app/src/androidTest/java/com/internal/tracker/core/database/EncryptionProbeEntity.kt`
- Create: `app/src/androidTest/java/com/internal/tracker/core/database/EncryptionProbeDao.kt`
- Create: `app/src/androidTest/java/com/internal/tracker/core/database/EncryptionProbeDatabase.kt`
- Create: `app/src/androidTest/java/com/internal/tracker/core/database/SqlCipherRoomIntegrationTest.kt`

**Interfaces:**

- Consumes: `DatabaseKeyResult.Ready.passphrase`.
- Produces: `SqlCipherFactoryProvider.create(passphrase: ByteArray): SupportSQLiteOpenHelper.Factory`.

- [ ] **Step 1: Write the failing instrumented integration test**

Create a test-only Room database with one entity/value. Test:

```kotlin
@RunWith(AndroidJUnit4::class)
class SqlCipherRoomIntegrationTest {
    @Test
    fun encryptedDatabaseReopensWithSameKeyAndRejectsWrongKey() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "sqlcipher-probe-${UUID.randomUUID()}.db"
        val key = ByteArray(32) { it.toByte() }

        open(context, name, key).use { db ->
            db.probeDao().insert(EncryptionProbeEntity(1, "secret"))
        }
        open(context, name, key).use { db ->
            assertEquals("secret", db.probeDao().value(1))
        }
        assertFailsWith<Exception> {
            open(context, name, ByteArray(32) { 7 }).use { it.openHelper.writableDatabase }
        }
        context.deleteDatabase(name)
    }
}
```

The `open` helper uses Room 2.8.4 and `.openHelperFactory(SqlCipherFactoryProvider().create(key))`.

- [ ] **Step 2: Run instrumented test and verify red**

Run on an API 26+ emulator/device:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.internal.tracker.core.database.SqlCipherRoomIntegrationTest --no-daemon
```

Expected: FAIL because the provider/probe database does not exist.

- [ ] **Step 3: Implement SQLCipher factory provider**

Use Zetetic's current API:

```kotlin
class SqlCipherFactoryProvider {
    fun create(passphrase: ByteArray): SupportSQLiteOpenHelper.Factory =
        SupportOpenHelperFactory(passphrase.copyOf())
}
```

Import `net.zetetic.database.sqlcipher.SupportOpenHelperFactory`. Do not use legacy `android-database-sqlcipher` or `SupportFactory`.

- [ ] **Step 4: Implement test-only Room probe types**

```kotlin
@Entity(tableName = "encryption_probe")
data class EncryptionProbeEntity(@PrimaryKey val id: Int, val value: String)

@Dao
interface EncryptionProbeDao {
    @Insert suspend fun insert(entity: EncryptionProbeEntity)
    @Query("SELECT value FROM encryption_probe WHERE id = :id") suspend fun value(id: Int): String?
}

@Database(entities = [EncryptionProbeEntity::class], version = 1, exportSchema = false)
abstract class EncryptionProbeDatabase : RoomDatabase() {
    abstract fun probeDao(): EncryptionProbeDao
}
```

- [ ] **Step 5: Run the integration test**

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.internal.tracker.core.database.SqlCipherRoomIntegrationTest --no-daemon
```

Expected: PASS: same key reopens/read succeeds; wrong key cannot open/read.

- [ ] **Step 6: Run unit tests and lint**

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug --no-daemon
```

Expected: PASS.

- [ ] **Step 7: Commit**

```powershell
git add app/src/main/java/com/internal/tracker/core/database app/src/androidTest/java/com/internal/tracker/core/database
git commit -m "test: verify encrypted Room database integration"
```

---

### Task 7: Assemble the Phase 1 platform module

**Files:**

- Create: `app/src/main/java/com/internal/tracker/core/platform/SetPlatformModule.kt`
- Create: `app/src/test/java/com/internal/tracker/core/platform/SetPlatformModuleTest.kt`

**Interfaces:**

- Consumes: `BusinessClock`, `UuidSource`, `StableDeviceIdProvider`, `DatabasePassphraseStore`, `SqlCipherFactoryProvider`.
- Produces:

```kotlin
data class SetPlatformModule(
    val clock: BusinessClock,
    val uuidSource: UuidSource,
    val deviceIdProvider: StableDeviceIdProvider,
    val databasePassphraseStore: DatabasePassphraseStore,
    val sqlCipherFactoryProvider: SqlCipherFactoryProvider,
)
```

- [ ] **Step 1: Write a failing construction test**

```kotlin
class SetPlatformModuleTest {
    @Test
    fun `module exposes the exact foundation services`() {
        val module = SetPlatformModule(
            clock = FakeClock(),
            uuidSource = UuidSource { UUID(0, 1) },
            deviceIdProvider = fakeDeviceIdProvider(),
            databasePassphraseStore = DatabasePassphraseStore { DatabaseKeyResult.Ready(ByteArray(32)) },
            sqlCipherFactoryProvider = SqlCipherFactoryProvider(),
        )
        assertEquals(Instant.EPOCH, module.clock.now())
        assertEquals(UUID(0, 1), module.uuidSource.newUuid())
    }
}
```

- [ ] **Step 2: Run test and verify red**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.internal.tracker.core.platform.SetPlatformModuleTest --no-daemon
```

Expected: FAIL because `SetPlatformModule` does not exist.

- [ ] **Step 3: Implement the immutable module**

Create only the data holder above. Do not modify old `AppContainer` or `TrackerApplication` yet. The tracking/data phase will consume it when the new production schema is available.

- [ ] **Step 4: Run focused/full tests**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.internal.tracker.core.platform.SetPlatformModuleTest --no-daemon
.\gradlew.bat :app:testDebugUnitTest --no-daemon
```

Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/internal/tracker/core/platform app/src/test/java/com/internal/tracker/core/platform
git commit -m "feat: compose Android SET platform foundation"
```

---

### Task 8: Phase 1 full verification and handoff

**Files:**

- Modify only if verification reveals a documented issue in a Phase 1 file.
- Review: `ANDROID_TECHNICAL_BUILD_SPEC`
- Review: `docs/superpowers/specs/2026-08-25-android-set-3.0-design.md`

**Interfaces:**

- Consumes: every interface produced by Tasks 1-7.
- Produces: a clean build/test baseline for the black-box tracking phase.

- [ ] **Step 1: Run full JVM tests and lint**

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug --no-daemon
```

Expected: PASS with zero test/lint failures.

- [ ] **Step 2: Assemble debug APK**

```powershell
.\gradlew.bat :app:assembleDebug --no-daemon
```

Expected: `app/build/outputs/apk/debug/app-debug.apk` exists and build exits 0.

- [ ] **Step 3: Run encrypted Room instrumented test**

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.internal.tracker.core.database.SqlCipherRoomIntegrationTest --no-daemon
```

Expected: PASS on API 26+ target.

- [ ] **Step 4: Check repository hygiene**

```powershell
git status --short
git diff --check
git log --oneline -8
```

Expected: no secret/key/database files; no whitespace errors; only intentional Phase 1 files are changed.

- [ ] **Step 5: Review Phase 1 traceability**

Confirm with file/test evidence:

```text
release 3.0.0/code 7/min 26          -> Task 1 policy test
Asia/Ho_Chi_Minh and deadlines       -> Task 2 tests
stable source Device ID              -> Task 3 tests
no silent key replacement            -> Tasks 4-5 tests
Room + SQLCipher same/wrong key       -> Task 6 instrumented test
bounded foundation composition        -> Task 7 test
```

If any row lacks fresh evidence, do not call the phase complete.

- [ ] **Step 6: Commit any verification-only corrections**

If no correction was needed, do not create an empty commit. Otherwise stage explicit paths and use:

```powershell
git commit -m "fix: close Android SET foundation verification gaps"
```

## Phase 1 completion contract

Phase 1 is complete only when:

- Android 3.0 identity/dependency policy is test-enforced;
- time/UUID/Device ID contracts compile and pass deterministic tests;
- existing wrapped passphrase failures never trigger silent replacement;
- SQLCipher Room opens with the correct key and rejects a wrong key on device;
- old 2.1 app code still compiles but is not treated as the new production composition;
- working tree is clean and every task has a focused commit.

The next plan creates the production black-box schema and always-on tracking service using `SetPlatformModule`; it must not duplicate or rename the Phase 1 interfaces without first updating this plan/design.
