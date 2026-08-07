# Fleet Management Website Delivery Roadmap

**Approved spec:** `docs/superpowers/specs/2026-08-07-fleet-management-website-design.md`

The approved scope is split into four sequential implementation plans. Each phase must leave the current GPS contract usable and produce independently testable software.

## Phase 1: Platform foundation and PostgreSQL ingestion

Plan: `docs/superpowers/plans/2026-08-07-fleet-platform-foundation.md`

Deliver a PostgreSQL/PostGIS-backed receiver that preserves the existing GET/POST contract on port 5055, imports valid JSONL history without the three smoke-test devices, and provides secure login/session/RBAC foundations. Keep the existing minimal dashboard available until Phase 3 replaces it.

## Phase 2: Fleet operations and bilingual Bootstrap UI

Plan: `docs/superpowers/plans/2026-08-07-fleet-operations-ui.md`

Deliver vendor, vehicle, inspection, driver, tracking-device and effective-dated assignment management. Add the Vietnamese/English Bootstrap application shell, role-specific navigation, forms, audit records and assignment reconciliation.

## Phase 3: Daily metrics, monthly dashboard and reports

Plan: `docs/superpowers/plans/2026-08-07-fleet-analytics-dashboard.md`

Deliver configurable GPS quality rules, idempotent daily recomputation, distance and stop-time reports, monthly dashboard cards/charts, vendor aggregation and Excel/CSV exports. Stop time remains absent from the dashboard.

## Phase 4: Offline maps, reverse geocoding and production operations

Plan: `docs/superpowers/plans/2026-08-07-fleet-offline-maps-operations.md`

Deliver local MapLibre/PMTiles assets for Northern Vietnam, Podman-hosted Nominatim, cached reverse geocoding, GPS history routes, backup/restore drill, health monitoring, Windows auto-start and cutover documentation. Internet ingestion remains a separately configured gateway after the LAN release.

## Cross-phase gates

- Run the complete Node and PowerShell test suites at every phase boundary.
- Preserve `GET /?id=...` and `POST /api/locations` behavior throughout.
- Never acknowledge a GPS point before durable database persistence.
- Never delete the JSONL source during migration or cutover.
- Do not import `AND-0123456789ABCDEF`, `AND-FEDCBA9876543210` or `AND-A1B2C3D4E5F60718`.
- Keep the currently running receiver available until a tested cutover step explicitly replaces it.
- Do not expose the management website to the Internet.
