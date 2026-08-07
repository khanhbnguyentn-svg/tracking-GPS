# Fleet Analytics, Dashboard and Reports Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Compute reproducible daily vehicle metrics from immutable GPS history and deliver the approved monthly dashboard and internal Excel/CSV reports.

**Architecture:** A versioned metrics engine selects and classifies ordered GPS points, then an idempotent job persists one aggregate per vehicle/local date. Dashboard/report query services read aggregates rather than scanning raw history, while quality drill-down links back to immutable positions.

**Tech Stack:** Node.js 24.19.0, PostgreSQL 17/PostGIS, Express 5.2.1/EJS 6.0.1, Bootstrap 5.3.8, Chart.js 4.5.1 stored locally, ExcelJS 4.4.0, Node test runner.

## Global Constraints

- Business timezone is `Asia/Ho_Chi_Minh`.
- Default quality limits: accuracy 100 m, implied speed 200 km/h, continuity gap 15 minutes.
- Default stop limits: speed 3 km/h and displacement 50 m.
- Every threshold is Admin-configurable, validated and audited.
- Dashboard never displays stop duration; stop duration remains in detail/report output.
- Late/backfilled GPS and assignment reconciliation must recompute affected dates idempotently.
- Raw GPS rows are immutable and excluded points retain an explicit reason.
- Billing calculations remain outside scope.

## File Map

- Create module `modules/metrics` with settings, point classifier, repository, calculator, queue and worker.
- Create module `modules/dashboard` with monthly query service, routes and views.
- Create module `modules/reports` with query, CSV and Excel exporters.
- Create local Chart.js asset and dashboard JS.
- Create tests `point-classifier.test.js`, `daily-metrics.integration.test.js`, `metrics-worker.test.js`, `dashboard.test.js`, `reports.test.js`.

---

### Task 1: Versioned quality settings and point classification

**Interfaces:**
- Produces `classifySegment(previous, current, settings): SegmentDecision` where decision is `{ accepted, distanceMeters, stopSeconds, reason }`.
- Produces `metricsSettingsService.getActive()` and Admin-only `update(command, actor)`.

- [ ] **Step 1: Write failing literal tests** for valid movement, accuracy rejection, non-increasing time, >200 km/h implied speed, >15-minute gap, stop classification and no stop time across a gap.
- [ ] **Step 2: Verify RED** because classifier/settings service are absent.
- [ ] **Step 3: Implement pure classification**; distance is supplied by the repository/PostGIS fixture rather than reimplementing geodesy in JavaScript. Return one stable reason enum: `ACCURACY`, `TIME_ORDER`, `IMPLIED_SPEED`, `GAP`, `COORDINATE`, or `null`.
- [ ] **Step 4: Implement audited settings versions** with positive bounded numeric validation and one active version ID included in aggregates.
- [ ] **Step 5: Run focused/full tests** and commit `feat: classify GPS metric segments`.

### Task 2: Daily calculator and idempotent recomputation queue

**Interfaces:**
- Produces `dailyCalculator.compute(vehicleId, localDate, settingsVersion): DailyMetric`.
- Produces `metricsQueue.enqueue(vehicleId, localDate, cause)` and `metricsWorker.runBatch(limit)`.

- [ ] **Step 1: Write failing PostgreSQL integration tests** with hand-checked points spanning valid motion, excluded jump, midnight, late arrival and unassigned data. Assert literal totals and counts.
- [ ] **Step 2: Write failing queue tests** proving duplicate jobs coalesce, one vehicle/day is leased once, failures retry with backoff and completed recalculation atomically replaces the aggregate.
- [ ] **Step 3: Verify RED** against a disposable database.
- [ ] **Step 4: Implement SQL point retrieval** ordered by `device_time,id`; calculate `ST_Distance(previous.location,current.location)` on geography and feed the pure classifier.
- [ ] **Step 5: Implement transactional aggregate upsert and queue leasing** with `FOR UPDATE SKIP LOCKED`, attempt count, next-attempt time and structured last error.
- [ ] **Step 6: Connect ingestion and assignment reconciliation** to enqueue affected Vietnam dates after durable writes.
- [ ] **Step 7: Run focused/full/load tests** and commit `feat: compute daily fleet metrics`.

### Task 3: Approved monthly dashboard

**Interfaces:**
- Produces `dashboardService.month({ month, vendorId, vehicleId, driverId, now }): MonthlyDashboard`.
- Output contains total distance, status counts, daily distance series, top vehicles, vendor totals, inspection alerts and latest positions; it contains no stop-duration field.

- [ ] **Step 1: Write failing service tests** for month boundaries in Vietnam time, filters, vehicles with zero distance, status thresholds (<5, 5–15, >15 minutes), unassigned device count and inspection 30/60-day groups.
- [ ] **Step 2: Write a contract test that recursively rejects keys matching `/stop|idle|park/i`** from dashboard JSON and dashboard chart configuration.
- [ ] **Step 3: Verify RED** because dashboard module is absent.
- [ ] **Step 4: Implement aggregate SQL queries** with bound parameters and consistent filters; do not scan `gps_positions` for month totals.
- [ ] **Step 5: Implement Bootstrap dashboard and local Chart.js** with KPI cards, daily line/bar chart, top vehicle table, vendor table, inspection alerts and a latest-position placeholder consumed by Phase 4 map.
- [ ] **Step 6: Add SSE invalidation** so clients refetch summary after new accepted positions/metric completion without streaming full GPS payloads.
- [ ] **Step 7: Run accessibility/HTML, service and full tests** and commit `feat: add monthly fleet dashboard`.

### Task 4: Internal CSV and Excel reports

**Interfaces:**
- Produces `reportService.dailyVehicle`, `monthlyVehicle`, `monthlyVendor`, `stopDetail` and exporters `toCsv(rows, columns, locale)`, `toXlsx(report, locale)`.

- [ ] **Step 1: Write failing report tests** for vendor/vehicle/date filters, stable column order, Vietnamese/English headings, formula-injection escaping in CSV, numeric Excel cells and no billing columns.
- [ ] **Step 2: Verify RED** because report/export modules are absent.
- [ ] **Step 3: Implement report queries from daily aggregates** and a separate stop-detail query; include coverage/quality counts so users understand conservative distance totals.
- [ ] **Step 4: Implement streaming CSV and bounded Excel export** with filename-safe month/vendor identifiers and maximum row limits returning a validation error rather than exhausting memory.
- [ ] **Step 5: Add authenticated Dispatcher/Admin routes and audit export events** without logging report contents.
- [ ] **Step 6: Run focused/full tests** and commit `feat: export fleet analytics reports`.

### Task 5: Phase 3 recomputation and acceptance

- [ ] **Step 1: Enqueue all imported vehicle/date pairs** in bounded batches and run workers until the queue is empty.
- [ ] **Step 2: Independently hand-check at least two vehicles and three days** by exporting ordered GPS segments and verifying accepted/rejected distance calculations.
- [ ] **Step 3: Run average-load plus backfill-burst tests** while workers and dashboard queries run; record p95 ingest/dashboard latency and database size.
- [ ] **Step 4: Run complete Node/integration/Pester suites** with zero failures.
- [ ] **Step 5: Verify dashboard month/vendor filters, no stop-time widgets, and bilingual CSV/XLSX downloads manually.**
- [ ] **Step 6: Record evidence in `gps-receiver/docs/PHASE3-ACCEPTANCE.md`** and commit `docs: verify fleet analytics`.

## Phase 3 Definition of Done

- Daily/monthly distance is reproducible from immutable GPS points and quality settings.
- Late data and corrected assignments recompute only affected dates.
- Dashboard matches approved fields and excludes stop duration.
- Internal reports include distance, stop detail and quality coverage but no billing.
- Full verification and capacity evidence are recorded.
