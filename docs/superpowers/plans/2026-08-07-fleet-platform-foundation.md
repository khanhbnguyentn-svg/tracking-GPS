# Fleet Platform Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace JSONL as the primary GPS store with PostgreSQL/PostGIS while preserving the current port-5055 contract, importing real history safely, and adding secure authentication/RBAC foundations for later fleet screens.

**Architecture:** Extend the existing `gps-receiver` as a modular Node.js application. Route handlers depend on repository interfaces; production repositories use one local PostgreSQL pool while unit tests use controlled fakes. SQL migrations own schema evolution, and the old JSONL receiver remains the rollback path until import and live smoke tests pass.

**Tech Stack:** Node.js 24.19.0, Express 5.2.1, `pg` 8.22.0, Zod 4.4.3, EJS 6.0.1, `cookie` 2.0.1, `@node-rs/argon2` 2.0.2, PostgreSQL 17, PostGIS, Bootstrap 5.3.8 assets reserved for Phase 2, Node test runner, PowerShell/Pester on Windows.

## Global Constraints

- Keep both `GET /?id=...&lat=...&lon=...&timestamp=...&speed=...&accuracy=...` and `POST /api/locations` compatible with the Android app.
- Keep TCP `5055`; only the Node application may listen on LAN interfaces.
- Normalize valid `AND-` plus 16-hex Device IDs to uppercase.
- Use `Asia/Ho_Chi_Minh` as the business timezone; store timestamps as `timestamptz` in UTC.
- Return `503` when durable PostgreSQL persistence fails; never report false acceptance.
- Keep all valid GPS history. Do not implement automatic position retention.
- Do not import the three loopback smoke IDs `AND-0123456789ABCDEF`, `AND-FEDCBA9876543210`, or `AND-A1B2C3D4E5F60718`.
- Vendor accounts, billing logic, public Internet ingress, maps, geocoding, reports and the final dashboard are outside this phase.
- Keep source files focused by module; route files do not contain SQL and repositories do not render HTTP responses.
- Every behavior change follows red-green-refactor with Node's real test runner.
- Git commits are listed as checkpoints. The current PC lacks Git CLI; execution must report skipped commits rather than silently claiming them.

## File Map

### Application core

- Create `gps-receiver/src/core/config.js`: parse and validate process configuration.
- Create `gps-receiver/src/core/errors.js`: typed application errors and safe HTTP mapping.
- Create `gps-receiver/src/core/request-context.js`: correlation IDs and structured request context.
- Create `gps-receiver/src/db/pool.js`: create/close the PostgreSQL pool.
- Create `gps-receiver/src/db/migrator.js`: apply ordered SQL migrations under an advisory lock.
- Create `gps-receiver/src/db/migrations/001_extensions.sql`: enable PostGIS.
- Create `gps-receiver/src/db/migrations/002_identity.sql`: users, sessions and audit schema.
- Create `gps-receiver/src/db/migrations/003_fleet_core.sql`: vendors, vehicles, inspections, drivers, devices and assignments.
- Create `gps-receiver/src/db/migrations/004_tracking.sql`: partitioned GPS positions, rejection records and indexes.

### Tracking module

- Modify `gps-receiver/src/validation.js`: preserve current normalization contract and expose a parsed ingestion command.
- Create `gps-receiver/src/modules/tracking/position-repository.js`: PostgreSQL persistence and idempotency.
- Create `gps-receiver/src/modules/tracking/ingestion-service.js`: validate, resolve source and persist before acceptance.
- Create `gps-receiver/src/modules/tracking/routes.js`: GET/POST ingestion HTTP adapter.
- Create `gps-receiver/src/modules/tracking/jsonl-importer.js`: streaming, idempotent legacy import.
- Create `gps-receiver/scripts/import-jsonl.js`: explicit import entry point.

### Authentication foundation

- Create `gps-receiver/src/modules/auth/passwords.js`: Argon2id hash/verify policy.
- Create `gps-receiver/src/modules/auth/auth-repository.js`: users and hashed sessions.
- Create `gps-receiver/src/modules/auth/auth-service.js`: login, logout, expiry and role checks.
- Create `gps-receiver/src/modules/auth/middleware.js`: session cookie, CSRF and `requireRole`.
- Create `gps-receiver/src/modules/auth/routes.js`: login/logout routes.
- Create `gps-receiver/scripts/create-user.js`: password-stdin bootstrap/update command for Admin and Dispatcher users.
- Create `gps-receiver/src/web/views/login.ejs`: bilingual-ready login form.
- Create `gps-receiver/src/web/i18n.js`: `vi` and `en` key lookup with Vietnamese default.

### Composition and operations

- Modify `gps-receiver/src/app.js`: compose Express adapters without changing public contract.
- Modify `gps-receiver/src/index.js`: migrate, start, and shut down database resources safely.
- Modify `gps-receiver/src/config.js`: delegate to the new core config while keeping old exports temporarily compatible.
- Modify `gps-receiver/package.json`: dependencies, migration/import scripts and test scripts.
- Create `gps-receiver/.env.example`: non-secret configuration names only.
- Create `gps-receiver/windows/Install-FleetDatabase.ps1`: install/configure local PostgreSQL/PostGIS database resources idempotently.
- Modify `gps-receiver/windows/InternalGpsReceiver.xml.template`: inject database environment from a protected environment file.
- Modify `gps-receiver/windows/Install-GpsReceiver.ps1`: preserve existing data and configure the database-backed service.
- Modify `gps-receiver/docs/OPERATIONS.md`: migration, rollback and credential handling.

### Tests

- Create `gps-receiver/test/core-config.test.js`.
- Create `gps-receiver/test/migrator.test.js`.
- Create `gps-receiver/test/position-repository.integration.test.js`.
- Create `gps-receiver/test/ingestion-service.test.js`.
- Create `gps-receiver/test/jsonl-importer.integration.test.js`.
- Create `gps-receiver/test/passwords.test.js`.
- Create `gps-receiver/test/auth-service.test.js`.
- Create `gps-receiver/test/create-user.test.js`.
- Modify `gps-receiver/test/app.test.js`.
- Modify `gps-receiver/test/windows-scripts.Tests.ps1`.

---

### Task 1: Configuration and dependency boundary

**Files:**
- Create: `gps-receiver/src/core/config.js`
- Create: `gps-receiver/test/core-config.test.js`
- Modify: `gps-receiver/src/config.js`
- Modify: `gps-receiver/package.json`
- Create: `gps-receiver/.env.example`

**Interfaces:**
- Produces: `loadConfig(env, rootDir): AppConfig` where `AppConfig` contains `host`, `port`, `databaseUrl`, `sessionSecret`, `dataDir`, `rateLimit`, `inactiveMinutes`, `businessTimezone`, `trustProxy`, and `nodeEnv`.
- Consumes: no new application interfaces.

- [ ] **Step 1: Write failing config tests**

Add literal expectations proving safe defaults, required secrets outside test mode, a loopback PostgreSQL URL, `Asia/Ho_Chi_Minh`, valid port/rate values, and rejection of malformed URLs:

```js
test('loads the test configuration with explicit database settings', () => {
  const config = loadConfig({
    NODE_ENV: 'test', GPS_DATABASE_URL: 'postgres://fleet:test@127.0.0.1:5432/fleet_test',
    GPS_SESSION_SECRET: '0123456789abcdef0123456789abcdef', GPS_PORT: '5055',
  }, 'D:\\fleet');
  assert.equal(config.port, 5055);
  assert.equal(config.businessTimezone, 'Asia/Ho_Chi_Minh');
  assert.equal(config.trustProxy, false);
});

test('rejects a non-loopback production database host', () => {
  assert.throws(() => loadConfig({
    NODE_ENV: 'production', GPS_DATABASE_URL: 'postgres://fleet:x@10.0.0.9/fleet',
    GPS_SESSION_SECRET: '0123456789abcdef0123456789abcdef',
  }, 'D:\\fleet'), /GPS_DATABASE_URL must use a loopback host/);
});
```

- [ ] **Step 2: Run the config test and verify RED**

Run:

```powershell
& '.\.tools\node\node-v24.19.0-win-x64\node.exe' --test gps-receiver\test\core-config.test.js
```

Expected: FAIL because `src/core/config.js` does not exist.

- [ ] **Step 3: Implement `loadConfig` and compatibility export**

Use pure parsing helpers, reject unknown enum values, require a 32-character session secret in production, and freeze the returned object. Keep `src/config.js` exporting the prior `loadConfig` name so existing tests can migrate incrementally.

- [ ] **Step 4: Add pinned runtime dependencies and scripts**

Add exact dependencies `express@5.2.1`, `ejs@6.0.1`, `pg@8.22.0`, `zod@4.4.3`, `cookie@2.0.1`, and `@node-rs/argon2@2.0.2`, commit `package-lock.json`, and define:

```json
{
  "scripts": {
    "start": "node src/index.js",
    "test": "node --test",
    "db:migrate": "node scripts/migrate.js",
    "import:jsonl": "node scripts/import-jsonl.js"
  }
}
```

Verify these pinned versions against the official npm registry before installation; do not use `latest` or caret ranges.

- [ ] **Step 5: Run existing and new config tests**

Run `node --test test/config.test.js test/core-config.test.js` from `gps-receiver` with the portable Node directory prepended to `PATH`.

Expected: all config tests PASS with no warnings.

- [ ] **Step 6: Commit checkpoint**

```bash
git add gps-receiver/package.json gps-receiver/package-lock.json gps-receiver/.env.example gps-receiver/src/config.js gps-receiver/src/core/config.js gps-receiver/test/core-config.test.js
git commit -m "build: add fleet platform configuration"
```

If Git remains unavailable, mark only this commit step skipped and continue without claiming a commit exists.

### Task 2: Migration runner and complete foundation schema

**Files:**
- Create: `gps-receiver/src/db/pool.js`
- Create: `gps-receiver/src/db/migrator.js`
- Create: `gps-receiver/src/db/migrations/001_extensions.sql`
- Create: `gps-receiver/src/db/migrations/002_identity.sql`
- Create: `gps-receiver/src/db/migrations/003_fleet_core.sql`
- Create: `gps-receiver/src/db/migrations/004_tracking.sql`
- Create: `gps-receiver/scripts/migrate.js`
- Create: `gps-receiver/test/migrator.test.js`

**Interfaces:**
- Produces: `createPool(config): pg.Pool`, `closePool(pool): Promise<void>`, `migrate(pool, migrationsDir): Promise<string[]>`.
- Produces schema tables used by Tasks 3–7.
- Consumes: `AppConfig.databaseUrl` from Task 1.

- [ ] **Step 1: Write failing migrator unit tests**

Use a recording fake client to prove migrations sort numerically, execute once, use `pg_advisory_xact_lock`, reject a changed checksum, and release the client in `finally`:

```js
test('applies pending SQL files in order under one advisory lock', async () => {
  const applied = await migrate(fakePool, fixturesDir);
  assert.deepEqual(applied, ['001_first.sql', '002_second.sql']);
  assert.match(fakePool.queries[0].text, /pg_advisory_xact_lock/);
});
```

- [ ] **Step 2: Run the migrator test and verify RED**

Run `node --test test/migrator.test.js`.

Expected: FAIL because `migrate` is missing.

- [ ] **Step 3: Implement the migration runner**

Create `schema_migrations(name text primary key, sha256 text not null, applied_at timestamptz not null default now())`. For each ordered `.sql` file, SHA-256 the UTF-8 bytes, reject checksum drift, run pending SQL and insert its checksum inside one transaction guarded by a fixed project advisory lock.

- [ ] **Step 4: Write the SQL migrations**

`001_extensions.sql` must run:

```sql
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS btree_gist;
```

`002_identity.sql` creates role-constrained users, hashed sessions, audit logs and system settings. `003_fleet_core.sql` creates vendor/vehicle/inspection/driver/device/assignment tables with normalized unique keys and exclusion constraints preventing overlapping effective ranges for one vehicle or device. `004_tracking.sql` creates a range-partitioned `gps_positions` parent, the current and next monthly partitions, `ingestion_rejections`, dedupe indexes and a function that creates a named monthly partition safely.

Use `tstzrange(effective_from, effective_to, '[)')` and GiST exclusion constraints. Use `geography(Point, 4326)` for GPS positions.

- [ ] **Step 5: Add the migration CLI**

`scripts/migrate.js` loads config, opens the pool, calls `migrate`, prints only migration names, and closes the pool in `finally`. It must exit nonzero without printing the database URL on error.

- [ ] **Step 6: Verify against a disposable PostgreSQL database**

Create a database whose name starts with `fleet_test_`, run migrations twice, and query:

```sql
SELECT PostGIS_Version();
SELECT count(*) FROM schema_migrations;
SELECT to_regclass('public.gps_positions');
```

Expected: PostGIS version is returned, four migration rows exist, `gps_positions` exists, and the second migration run applies zero files.

- [ ] **Step 7: Commit checkpoint**

```bash
git add gps-receiver/src/db gps-receiver/scripts/migrate.js gps-receiver/test/migrator.test.js
git commit -m "feat: add fleet database schema"
```

### Task 3: Idempotent PostgreSQL position repository

**Files:**
- Create: `gps-receiver/src/modules/tracking/position-repository.js`
- Create: `gps-receiver/test/position-repository.integration.test.js`

**Interfaces:**
- Produces: `createPositionRepository(pool)` with `insert(command): Promise<{ record: PositionRecord, duplicate: boolean }>` and `health(): Promise<{ writable: boolean, latencyMs: number }>`.
- `command` fields: `deviceId`, `latitude`, `longitude`, `deviceTime`, `speedKnots`, `accuracyMeters`, `receivedAt`, `source`, `dedupeKey`.
- Consumes: migrated schema from Task 2.

- [ ] **Step 1: Write failing repository integration tests**

Test one durable insert, same-command idempotency, uppercase Device ID, PostGIS coordinates, unassigned storage and rollback on SQL error:

```js
test('returns the existing row for a duplicate GPS command', async () => {
  const first = await repository.insert(command);
  const second = await repository.insert(command);
  assert.equal(first.duplicate, false);
  assert.equal(second.duplicate, true);
  assert.equal(second.record.id, first.record.id);
  assert.equal(await rowCount(pool, 'gps_positions'), 1);
});
```

- [ ] **Step 2: Run the repository test and verify RED**

Run with `GPS_TEST_DATABASE_URL` pointing only to a disposable `fleet_test_` database.

Expected: FAIL because the repository module does not exist.

- [ ] **Step 3: Implement transactional insert and dedupe**

Compute no SQL in the route. Insert the tracking device if unknown, resolve the effective assignment with `device_time <@ tstzrange(effective_from, effective_to, '[)')`, insert with `ST_SetSRID(ST_MakePoint($lon,$lat),4326)::geography`, and use a unique `dedupe_key`. On conflict, return the existing row and `duplicate: true`.

- [ ] **Step 4: Run repository tests twice**

Expected: both runs PASS and leave the disposable database clean through test teardown.

- [ ] **Step 5: Commit checkpoint**

```bash
git add gps-receiver/src/modules/tracking/position-repository.js gps-receiver/test/position-repository.integration.test.js
git commit -m "feat: persist GPS positions in PostgreSQL"
```

### Task 4: Tracking service and preserved HTTP contract

**Files:**
- Create: `gps-receiver/src/core/errors.js`
- Create: `gps-receiver/src/core/request-context.js`
- Create: `gps-receiver/src/modules/tracking/ingestion-service.js`
- Create: `gps-receiver/src/modules/tracking/routes.js`
- Modify: `gps-receiver/src/validation.js`
- Modify: `gps-receiver/src/app.js`
- Modify: `gps-receiver/test/ingestion-service.test.js`
- Modify: `gps-receiver/test/app.test.js`

**Interfaces:**
- Produces: `createIngestionService({ repository, clock }).ingest(raw, context)`.
- Produces: `createTrackingRouter({ ingestionService, rateLimiter })`.
- Consumes: `positionRepository.insert` from Task 3 and existing validation semantics.

- [ ] **Step 1: Write failing service tests**

Prove lowercase IDs normalize, dedupe keys are stable, repository errors become `PERSISTENCE_UNAVAILABLE`, and success is returned only after the repository promise resolves. Derive the dedupe key from normalized Device ID, device timestamp in milliseconds, latitude and longitude using SHA-256.

- [ ] **Step 2: Write failing HTTP compatibility tests**

Run the real Express app with a recording repository and assert exact status/body for valid GET, valid JSON POST, duplicate request, invalid ID, invalid JSON, wrong content type, oversized body, rate limit and repository failure. Keep `/health` reachable when ingestion is rate limited.

- [ ] **Step 3: Run the focused tests and verify RED**

Run:

```powershell
node --test test\ingestion-service.test.js test\app.test.js
```

Expected: FAIL because the service/router adapters are missing.

- [ ] **Step 4: Implement service and HTTP adapters**

Move ingestion-specific branching out of `app.js`. Keep stable response JSON. Map only known application errors to public responses; log unexpected errors with correlation ID and return a generic `500`. Use a 16 KiB JSON limit and preserve the current per-source rate limit.

- [ ] **Step 5: Keep the legacy dashboard temporarily**

Continue serving `/dashboard`, `/api/devices` and `/api/stats` through a compatibility read model backed by PostgreSQL queries. Do not add final dashboard features in this phase.

- [ ] **Step 6: Run all Node tests**

Run `node --test` from `gps-receiver`.

Expected: all old and new tests PASS; no test listens on port 5055 or writes production data.

- [ ] **Step 7: Commit checkpoint**

```bash
git add gps-receiver/src/core gps-receiver/src/modules/tracking gps-receiver/src/validation.js gps-receiver/src/app.js gps-receiver/test
git commit -m "feat: preserve GPS API on PostgreSQL"
```

### Task 5: Streaming JSONL migration with explicit exclusions

**Files:**
- Create: `gps-receiver/src/modules/tracking/jsonl-importer.js`
- Create: `gps-receiver/scripts/import-jsonl.js`
- Create: `gps-receiver/test/jsonl-importer.integration.test.js`

**Interfaces:**
- Produces: `importJsonl({ paths, repository, excludedDeviceIds, dryRun, onProgress }): Promise<ImportSummary>`.
- `ImportSummary`: `files`, `lines`, `imported`, `duplicates`, `excluded`, `invalid`, `failed`.
- Consumes: Task 3 repository and Task 1 config.

- [ ] **Step 1: Write failing importer integration tests**

Create a temporary JSONL file containing one valid LAN record, the three exact smoke IDs, a duplicate line, malformed JSON and a valid second LAN record. Assert `imported: 2`, `excluded: 3`, `duplicates: 1`, `invalid: 1`, `failed: 0` and two database rows.

- [ ] **Step 2: Run importer tests and verify RED**

Expected: FAIL because `importJsonl` is missing.

- [ ] **Step 3: Implement line-streaming import**

Use `readline.createInterface` over file streams; never load a full file into memory. Convert legacy fields to the repository command, preserve both device and receive timestamps, mark source `legacy-jsonl`, and send batches sequentially with bounded size. `dryRun` validates and counts without writing.

- [ ] **Step 4: Implement safe CLI behavior**

Require explicit `--source <absolute-or-project-relative-directory>`. Default to `--dry-run`; writing requires `--apply`. Print only counts and filenames. Exit nonzero if `failed > 0`; malformed legacy lines count as invalid but do not abort other files.

- [ ] **Step 5: Verify dry-run then applied import against copied fixtures**

Run dry-run, record counts, run `--apply`, rerun `--apply`, and prove the second applied run reports all valid records as duplicates.

- [ ] **Step 6: Commit checkpoint**

```bash
git add gps-receiver/src/modules/tracking/jsonl-importer.js gps-receiver/scripts/import-jsonl.js gps-receiver/test/jsonl-importer.integration.test.js
git commit -m "feat: import legacy GPS history safely"
```

### Task 6: Argon2id users, sessions, CSRF and RBAC

**Files:**
- Create: `gps-receiver/src/modules/auth/passwords.js`
- Create: `gps-receiver/src/modules/auth/auth-repository.js`
- Create: `gps-receiver/src/modules/auth/auth-service.js`
- Create: `gps-receiver/src/modules/auth/middleware.js`
- Create: `gps-receiver/src/modules/auth/routes.js`
- Create: `gps-receiver/scripts/create-user.js`
- Create: `gps-receiver/src/web/i18n.js`
- Create: `gps-receiver/src/web/views/login.ejs`
- Create: `gps-receiver/test/passwords.test.js`
- Create: `gps-receiver/test/auth-service.test.js`
- Create: `gps-receiver/test/create-user.test.js`
- Modify: `gps-receiver/test/app.test.js`

**Interfaces:**
- Produces: `hashPassword(password)`, `verifyPassword(hash, password)`.
- Produces: `authService.login(username, password, meta)`, `logout(sessionToken)`, `authenticate(sessionToken)`, `requireRole(user, roles)`.
- Produces middleware `loadSession`, `requireAuth`, `requireRole(...roles)`, `requireCsrf`.
- Consumes identity tables from Task 2.

- [ ] **Step 1: Write failing password tests**

Assert an Argon2id encoded hash, correct/wrong password behavior, rejection below 12 characters for new passwords, and `needsRehash` when the stored policy is weaker.

- [ ] **Step 2: Write failing auth service tests**

Assert case-normalized username lookup, generic invalid-credential errors, opaque 32-byte session token, only SHA-256 token hash persisted, 8-hour idle expiry, 24-hour absolute expiry, revoked session rejection and role enforcement.

- [ ] **Step 3: Run focused tests and verify RED**

Expected: FAIL because auth modules do not exist.

- [ ] **Step 4: Implement password and session services**

Use Argon2id through `@node-rs/argon2`; keep cost parameters in one frozen policy object. Use `crypto.randomBytes(32).toString('base64url')` for the client token and store only `sha256(token)`. Use constant public errors for unknown user and wrong password.

- [ ] **Step 5: Implement login/logout, cookie and CSRF middleware**

Set cookie name `fleet_session`, `HttpOnly`, `SameSite=Strict`, `Path=/`, no persistent max-age beyond server expiry, and `Secure` only when the verified request is HTTPS. Rotate the session after successful login. Generate a session-bound CSRF token and require it on state-changing form/API routes outside GPS ingestion.

- [ ] **Step 6: Add bilingual-ready login page**

Implement `translate(locale, key)` with complete `vi` and `en` dictionaries for login/logout/error labels. Vietnamese is the fallback. Do not auto-translate user data.

- [ ] **Step 7: Add HTTP tests**

Prove unauthenticated management access redirects to `/login`, valid login sets safe cookie, invalid login does not reveal whether the user exists, CSRF blocks a POST without the token, Dispatcher receives 403 on an Admin-only fixture route, and ingestion remains unauthenticated.

- [ ] **Step 8: Write the user-bootstrap CLI test and implementation**

Spawn `scripts/create-user.js --username admin --role admin --password-stdin` with a password on stdin. Assert the password never appears in argv/stdout, the created row verifies through `authService`, invalid roles fail, and rerunning requires `--update` rather than silently replacing credentials. Implement only `admin` and `dispatcher` roles.

- [ ] **Step 9: Run all Node tests**

Expected: all tests PASS, with no secrets in captured logs.

- [ ] **Step 10: Commit checkpoint**

```bash
git add gps-receiver/src/modules/auth gps-receiver/src/web gps-receiver/scripts/create-user.js gps-receiver/test
git commit -m "feat: add secure fleet authentication"
```

### Task 7: Native database installation and protected service configuration

**Files:**
- Create: `gps-receiver/windows/Install-FleetDatabase.ps1`
- Modify: `gps-receiver/windows/InternalGpsReceiver.xml.template`
- Modify: `gps-receiver/windows/Install-GpsReceiver.ps1`
- Modify: `gps-receiver/windows/Uninstall-GpsReceiver.ps1`
- Modify: `gps-receiver/test/windows-scripts.Tests.ps1`
- Modify: `gps-receiver/docs/OPERATIONS.md`

**Interfaces:**
- Produces Windows service `InternalGpsReceiver` and local database `fleet_tracking` with least-privilege role `fleet_app`.
- Consumes migration CLI from Task 2 and Node application composition from Tasks 4 and 6.

- [ ] **Step 1: Write failing Pester behavior tests**

Tests must run scripts with `-WhatIf` or test-owned paths and prove: PostgreSQL/PostGIS prerequisites are checked; generated secrets are not printed; database listens on loopback; ProgramData ACL grants only service identity/Admins/SYSTEM; uninstall preserves database and map/history data unless an explicit purge switch is provided; no firewall rule opens 5432.

- [ ] **Step 2: Run Pester and verify RED**

Run:

```powershell
Set-ExecutionPolicy -Scope Process Bypass -Force
Invoke-Pester .\gps-receiver\test\windows-scripts.Tests.ps1
```

Expected: new assertions FAIL because database install behavior is absent.

- [ ] **Step 3: Implement idempotent database preparation**

Install/reuse the project-pinned PostgreSQL 17.10 binaries through the existing `server/scripts/Install-NativePostgres.ps1` path, then install the official stable Windows PostGIS 3.6.2 bundle for PostgreSQL 17 after verifying its recorded SHA-256. Create `fleet_app` and `fleet_tracking` only when absent, set a SCRAM password, grant schema/table/sequence privileges needed by migrations, and store the connection secret encrypted with DPAPI under the existing ProgramData ACL model. Never put the password in repository files, command output or WinSW XML. Do not use the PostGIS 3.7 alpha line.

- [ ] **Step 4: Pass database configuration to the Windows service safely**

Generate a protected environment file outside the repository and reference it from the service wrapper. Keep `GPS_HOST=0.0.0.0`, `GPS_PORT=5055`, and database host loopback. Preserve the existing automatic restart policy.

- [ ] **Step 5: Document install, migrate, rollback and recovery**

Document exact Administrator commands, how to identify the old/new PID, how to run JSONL dry-run/import, how to stop before cutover, and how to restart the JSONL receiver with its preserved directory if PostgreSQL cutover fails.

- [ ] **Step 6: Run Pester and WhatIf verification**

Expected: all Pester tests PASS; `-WhatIf` lists only project-owned resources and performs no system changes.

- [ ] **Step 7: Commit checkpoint**

```bash
git add gps-receiver/windows gps-receiver/test/windows-scripts.Tests.ps1 gps-receiver/docs/OPERATIONS.md
git commit -m "ops: install fleet database safely"
```

### Task 8: Composition, cutover rehearsal and phase verification

**Files:**
- Modify: `gps-receiver/src/index.js`
- Modify: `gps-receiver/src/app.js`
- Modify: `gps-receiver/README.md`
- Modify: `README.md`
- Create: `gps-receiver/docs/PHASE1-ACCEPTANCE.md`

**Interfaces:**
- Consumes all prior Task interfaces.
- Produces a database-backed receiver ready for Phase 2.

- [ ] **Step 1: Write failing lifecycle tests**

Test that startup migrates before listening, migration failure never binds a port, shutdown stops accepting HTTP then drains writes and closes the pool, and signals invoke shutdown once.

- [ ] **Step 2: Run lifecycle tests and verify RED**

Expected: FAIL until `index.js` composition is dependency-injected and lifecycle-safe.

- [ ] **Step 3: Implement final composition**

Construct config, logger, pool, repositories, services and routers in `index.js`. Run migrations before `app.start()`. On `SIGINT`/`SIGTERM`, stop HTTP, wait for in-flight requests with a bounded timeout, close PostgreSQL and exit according to success/failure.

- [ ] **Step 4: Run complete automated verification**

Run:

```powershell
$env:PATH='D:\Server\tracking-GPS-main\.tools\node\node-v24.19.0-win-x64;' + $env:PATH
npm.cmd test
Set-ExecutionPolicy -Scope Process Bypass -Force
Invoke-Pester .\test\windows-scripts.Tests.ps1
```

Expected: zero Node failures and zero Pester failures.

- [ ] **Step 5: Rehearse import without touching production JSONL**

Copy JSONL files to a test-owned directory, run dry-run and applied import into a disposable migrated database, verify the three excluded IDs are absent and the two real LAN IDs are present, then destroy only the exact disposable database after validating its generated `fleet_test_` prefix.

- [ ] **Step 6: Run a local load smoke test**

Send at least 7 requests/second for 10 minutes with valid unique timestamps and a controlled device set. Assert zero lost accepted rows, stable process memory, no connection-pool exhaustion and p95 response time recorded in `PHASE1-ACCEPTANCE.md`. This covers average 350-device load; later phases add burst/backfill benchmarks.

- [ ] **Step 7: Perform reversible live cutover**

Back up JSONL, stop only the verified current Node PID, migrate the production database, run import dry-run and apply, compare counts, start the new service, send one lowercase real-format GET and one POST, verify both return `200`, database rows exist, `/health` is healthy and the compatibility dashboard loads. If any gate fails, stop the new service and restart the preserved JSONL receiver.

- [ ] **Step 8: Record phase acceptance**

Write commands, timestamps, test counts, import counts, excluded IDs, database backup location, service PID and rollback result to `gps-receiver/docs/PHASE1-ACCEPTANCE.md`. Do not include secrets.

- [ ] **Step 9: Final commit checkpoint**

```bash
git add gps-receiver/src gps-receiver/README.md gps-receiver/docs/PHASE1-ACCEPTANCE.md README.md
git commit -m "feat: complete PostgreSQL GPS foundation"
```

## Phase 1 Definition of Done

- Existing Android GET requests and JSON POST requests return `200` only after PostgreSQL persistence.
- Duplicate retries do not create duplicate positions.
- Valid JSONL records are imported idempotently; the exact three smoke IDs are excluded.
- PostgreSQL/PostGIS is loopback-only and no database secret exists in the repository or logs.
- Login, sessions, CSRF and Admin/Dispatcher enforcement pass automated tests.
- The current compatibility dashboard remains available for operations.
- Node, integration and Pester suites pass with fresh evidence.
- Cutover is reversible and the old JSONL data remains intact.
