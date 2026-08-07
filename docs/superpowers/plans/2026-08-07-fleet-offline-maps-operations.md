# Offline Maps, Geocoding and Production Operations Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add fully offline Northern Vietnam maps and reverse geocoding, GPS history visualization, production backup/health controls and Windows auto-start without exposing the management website to the Internet.

**Architecture:** Node serves pinned MapLibre/PMTiles assets and authenticated GeoJSON APIs. Nominatim and its OSM database run in an isolated Podman network bound only to loopback; a cached adapter makes geocoding optional to ingestion. Operations scripts manage checksummed map artifacts, database backups, health and reversible service rollout.

**Tech Stack:** Node.js 24.19.0, MapLibre GL JS 6.2.0, PMTiles 4.4.1, PostgreSQL 17/PostGIS, Podman, Nominatim 5.3.2, OpenStreetMap data, PowerShell/Pester, WinSW.

## Global Constraints

- Offline coverage is Northern Vietnam: Northwest, Northeast and Red River Delta; North Central Coast is excluded.
- All browser JS/CSS/fonts/styles/tiles are local; no CDN or Google Maps request is permitted.
- Display required OpenStreetMap attribution.
- Geocoder failure never rejects a GPS point.
- Reverse-geocode latest positions first and history on demand; cache nearby results.
- Nominatim, map tooling and PostgreSQL are not exposed on LAN or Internet.
- Internet ingestion gateway configuration remains deferred and cannot expose dashboard routes.
- GPS source history remains immutable and JSONL migration backups remain preserved.

## File Map

- Create module `modules/maps` for PMTiles metadata/range serving and authenticated GeoJSON routes.
- Create module `modules/geocoding` for adapter, cache repository, queue and worker.
- Create map/history EJS views and local MapLibre/PMTiles assets.
- Create `podman/compose.yaml`, Nominatim environment template and import/update scripts without secrets.
- Create PowerShell scripts for map artifact install, database backup/restore drill and health checks.
- Extend WinSW installer and Pester tests.

---

### Task 1: Checksummed Northern Vietnam PMTiles artifact

**Interfaces:**
- Produces `mapArtifactService.metadata()` and `openRange(start,end)` for one approved PMTiles artifact.

- [ ] **Step 1: Write failing tests** proving only a configured file under the project-owned map root is served, range parsing rejects invalid/multipart/out-of-bounds requests, ETag uses artifact checksum and missing artifact returns a degraded map status.
- [ ] **Step 2: Verify RED** because map artifact service is absent.
- [ ] **Step 3: Implement artifact metadata/range service** using file descriptors and bounded streams; never read the full PMTiles file into memory.
- [ ] **Step 4: Implement Admin installation script** accepting a `.partial` artifact plus expected SHA-256, validating geographic metadata/zoom range, atomically promoting it and preserving one prior artifact for rollback.
- [ ] **Step 5: Generate/import the Northern Vietnam artifact with Podman tooling** from a dated OSM extract, record source URL/date/license/checksum and verify the defined three-region coverage without North Central Coast.
- [ ] **Step 6: Run unit/Pester/artifact smoke tests** and commit `feat: serve offline Northern Vietnam map`.

### Task 2: Authenticated MapLibre fleet and history maps

**Interfaces:**
- Produces `/api/map/latest` and `/api/map/history?vehicleId=&date=` GeoJSON FeatureCollections with bounded fields.
- Consumes dashboard latest positions and immutable GPS history.

- [ ] **Step 1: Write failing GeoJSON tests** for role enforcement, filters, coordinate order `[longitude,latitude]`, quality flags, route ordering, maximum point response and no secrets/internal notes.
- [ ] **Step 2: Write failing HTML tests** asserting local MapLibre/PMTiles URLs, OSM attribution, bilingual controls and zero external asset origins.
- [ ] **Step 3: Verify RED** because routes/views are absent.
- [ ] **Step 4: Implement query services** with date/vehicle validation, simplification for display while preserving endpoints, and downloadable raw CSV handled by reports rather than map JSON.
- [ ] **Step 5: Implement MapLibre screens** for fleet latest markers, status colors, route line, excluded-point toggle, popup vehicle/driver/time/accuracy/address and responsive fallback table.
- [ ] **Step 6: Run automated tests and an offline browser acceptance** with the Internet adapter disabled; commit `feat: visualize fleet GPS offline`.

### Task 3: Podman Nominatim and cached reverse geocoding

**Interfaces:**
- Produces `geocoder.reverse(lat, lon): AddressResult|null` and a queue worker for latest/on-demand lookup.
- `AddressResult` contains road, suburb, locality, district, province, postcode, displayName and OSM attribution metadata.

- [ ] **Step 1: Write failing adapter/cache tests** for coordinate validation, loopback-only base URL, timeout, Nominatim no-result, transient failure, locale-independent structured fields and cache hit by bounded spatial cell.
- [ ] **Step 2: Verify RED** because geocoder modules are absent.
- [ ] **Step 3: Implement Podman definition** with pinned image digests, isolated network, loopback published API, persistent named volumes, health check and memory/CPU limits appropriate to measured PC capacity. Secrets use Podman secrets/environment outside the repository.
- [ ] **Step 4: Import the same dated Northern OSM coverage** into a reverse-only Nominatim database, run `nominatim admin --check-database`, test known coordinates in Hanoi and northern provinces, and record import duration/size.
- [ ] **Step 5: Implement adapter, cache and queue** with short timeout, retry/backoff, latest-position priority, on-demand history jobs and no synchronous geocoder call in ingestion.
- [ ] **Step 6: Integrate addresses into map popups/detail tables** with “address pending”/coordinate fallback in both languages.
- [ ] **Step 7: Stop Nominatim and prove GPS remains accepted** while geocoding health degrades; restart and prove queued lookups recover. Commit `feat: resolve GPS addresses offline`.

### Task 4: PostgreSQL backup, restore drill and operational health

**Files:**
- Create: `gps-receiver/windows/Backup-FleetDatabase.ps1`
- Create: `gps-receiver/windows/Test-FleetRestore.ps1`
- Create: `gps-receiver/windows/Test-FleetHealth.ps1`
- Modify: `gps-receiver/test/windows-scripts.Tests.ps1`
- Create: `gps-receiver/src/modules/system/health-service.js`

- [ ] **Step 1: Write failing Pester tests** for `.partial` backup, SHA-256 sidecar, retention only after successful new backup, exact generated restore database prefix validation, guaranteed cleanup, secret redaction and map/Nominatim/disk/service checks.
- [ ] **Step 2: Verify RED** because scripts are absent.
- [ ] **Step 3: Implement `pg_dump --format=custom` backup** with atomic promotion/checksum and configured retention that never deletes the last successful backup.
- [ ] **Step 4: Implement restore drill** into a generated `fleet_restore_test_<timestamp>_<random>` database, validate migrations/table counts/PostGIS, and drop only after exact prefix/ownership checks in `finally`.
- [ ] **Step 5: Implement health aggregation** for app, writable DB latency, pending/failed jobs, partition horizon, backup age, disk forecast, PMTiles checksum and Nominatim; public `/health` reveals only `ok/degraded`, Admin view shows components.
- [ ] **Step 6: Run Pester/integration/failure-injection tests** and commit `ops: back up and monitor fleet platform`.

### Task 5: Windows auto-start, offline acceptance and handover

**Files:**
- Modify: `gps-receiver/windows/Install-GpsReceiver.ps1`
- Modify: `gps-receiver/windows/InternalGpsReceiver.xml.template`
- Create: `gps-receiver/windows/Install-FleetScheduledTasks.ps1`
- Modify: `gps-receiver/docs/OPERATIONS.md`
- Create: `gps-receiver/docs/OFFLINE-MAPS.md`
- Create: `gps-receiver/docs/PHASE4-ACCEPTANCE.md`
- Modify: `config/traccar-profile.example.json`

- [ ] **Step 1: Write failing Pester tests** for service dependencies/restart, scheduled backup/health tasks, Private-LAN firewall scope, no DB/Nominatim port rule, protected data ACLs and uninstall preserving all data/artifacts unless explicit purge switches are supplied.
- [ ] **Step 2: Verify RED**, implement idempotent WinSW/Task Scheduler configuration and rerun until all Pester tests pass.
- [ ] **Step 3: Update the Android profile example** only with the currently approved LAN host/port/scheme; document that Internet endpoint values remain unconfigured rather than inventing a tunnel URL.
- [ ] **Step 4: Run complete automated verification** across Node, PostgreSQL integration and Pester suites with zero failures.
- [ ] **Step 5: Run full offline acceptance** by disconnecting Internet while retaining LAN: login, switch languages, view dashboard, fleet map, one-day route, address cache/fresh local lookup, CSV/Excel export and GPS ingestion.
- [ ] **Step 6: Reboot acceptance** proving PostgreSQL, Node and Podman/Nominatim start automatically, health converges and no manual terminal remains required.
- [ ] **Step 7: Run backup/restore evidence and rollback drill**, record artifact versions/checksums, database counts, service status and known capacity in `PHASE4-ACCEPTANCE.md` without secrets.
- [ ] **Step 8: Commit checkpoint:** `git commit -m "feat: complete offline fleet platform"`.

## Phase 4 Definition of Done

- Northern Vietnam maps and reverse geocoding work with Internet disconnected.
- Management screens never request Google/CDN/Internet resources.
- Geocoder/map failures degrade safely without losing GPS.
- Daily backup and restore drill are verified.
- Node/PostgreSQL/Podman services recover after reboot.
- The system remains LAN-only except for the explicitly deferred ingestion gateway.
