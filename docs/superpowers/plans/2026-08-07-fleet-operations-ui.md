# Fleet Operations and Bilingual UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add secure Vietnamese/English Bootstrap screens for internal vendor, vehicle, inspection, driver, device and effective-dated assignment management.

**Architecture:** Build feature modules on the authenticated platform from Phase 1. Each module owns its repository, service, routes, validators, views and tests; shared web helpers provide pagination, form errors, flash messages, locale and CSRF without containing business rules.

**Tech Stack:** Node.js 24.19.0, Express 5.2.1, PostgreSQL 17/PostGIS, EJS 6.0.1, Bootstrap 5.3.8 stored locally, Zod 4.4.3, Node test runner.

## Global Constraints

- Only Admin and Dispatcher accounts use the website; vendor accounts and vendor sharing routes do not exist.
- Vietnamese is the default locale and English is selectable/persisted per account.
- User-entered names and notes are never automatically translated.
- Admin manages users/settings/history corrections; Dispatcher manages normal operational records but cannot delete history or change system rules.
- All state-changing routes require authentication, role authorization, CSRF and audit logging.
- Effective-dated assignment history is append/close based; do not overwrite prior intervals.
- Keep GPS ingestion unauthenticated and backward-compatible throughout this phase.
- Git checkpoint commands must be reported as skipped if Git CLI remains unavailable.

## File Map

- Create `gps-receiver/src/web/app-shell.js`: common render locals, locale, navigation and flash messages.
- Create `gps-receiver/src/web/public/vendor/bootstrap.min.css` and `bootstrap.bundle.min.js`: pinned local Bootstrap distribution.
- Create `gps-receiver/src/web/views/layout.ejs`, `partials/nav.ejs`, `partials/errors.ejs`, `partials/pagination.ejs`.
- Expand `gps-receiver/src/web/i18n.js`: complete `vi`/`en` dictionaries.
- Create module directories `modules/vendors`, `modules/vehicles`, `modules/drivers`, `modules/devices`, `modules/assignments`, `modules/audit` with focused repository/service/routes files.
- Create EJS views under `src/web/views/{vendors,vehicles,drivers,devices,assignments,audit}`.
- Create tests `fleet-repositories.integration.test.js`, `fleet-services.test.js`, `fleet-routes.test.js`, `assignments.integration.test.js`, `i18n.test.js`, `rbac.test.js`.

---

### Task 1: Local Bootstrap shell and complete i18n contract

**Files:**
- Create: `gps-receiver/src/web/app-shell.js`
- Create: `gps-receiver/src/web/views/layout.ejs`
- Create: `gps-receiver/src/web/views/partials/nav.ejs`
- Create: `gps-receiver/src/web/views/partials/errors.ejs`
- Create: `gps-receiver/src/web/views/partials/pagination.ejs`
- Modify: `gps-receiver/src/web/i18n.js`
- Create: `gps-receiver/src/web/public/vendor/bootstrap.min.css`
- Create: `gps-receiver/src/web/public/vendor/bootstrap.bundle.min.js`
- Create: `gps-receiver/test/i18n.test.js`

**Interfaces:**
- Produces `translate(locale, key, params?)`, `supportedLocales`, `createViewLocals(request)` and `renderPage(response, view, model)`.
- Consumes authenticated `request.user`, session locale and CSRF token from Phase 1.

- [ ] **Step 1: Write failing i18n and shell tests** proving every Vietnamese key exists in English, unknown locale falls back to `vi`, parameter interpolation escapes values, active navigation respects role, and rendered pages reference only `/assets/...` resources rather than CDNs.
- [ ] **Step 2: Run `node --test test/i18n.test.js`** and verify it fails because the shell and full dictionaries are missing.
- [ ] **Step 3: Implement local asset serving and layout helpers** with page title, language switch, username, logout form with CSRF, responsive sidebar/topbar and accessible labels.
- [ ] **Step 4: Pin Bootstrap files** by copying the exact package distribution selected in `package-lock.json`; record its version and SHA-256 in `gps-receiver/README.md`.
- [ ] **Step 5: Run the focused test and HTTP smoke tests**; expect no external `http://` or `https://` asset URL in generated HTML.
- [ ] **Step 6: Commit checkpoint:** `git commit -m "feat: add bilingual Bootstrap application shell"` with only Task 1 files.

### Task 2: Vendor and vehicle management

**Files:**
- Create: `gps-receiver/src/modules/vendors/repository.js`
- Create: `gps-receiver/src/modules/vendors/service.js`
- Create: `gps-receiver/src/modules/vendors/routes.js`
- Create: `gps-receiver/src/modules/vehicles/repository.js`
- Create: `gps-receiver/src/modules/vehicles/service.js`
- Create: `gps-receiver/src/modules/vehicles/routes.js`
- Create: `gps-receiver/src/web/views/vendors/index.ejs`
- Create: `gps-receiver/src/web/views/vendors/form.ejs`
- Create: `gps-receiver/src/web/views/vehicles/index.ejs`
- Create: `gps-receiver/src/web/views/vehicles/form.ejs`
- Create: `gps-receiver/src/web/views/vehicles/detail.ejs`
- Create: `gps-receiver/test/fleet-repositories.integration.test.js`
- Create: `gps-receiver/test/fleet-routes.test.js`

**Interfaces:**
- Produces `vendorService.list/create/update/deactivate` and `vehicleService.list/get/create/update/deactivate`.
- List inputs use `{ query, status, vendorId, page, pageSize }`; list outputs use `{ rows, total, page, pageSize }`.

- [ ] **Step 1: Write failing repository tests** for normalized unique vendor code, normalized unique plate, parameterized filtering, deterministic pagination and deactivate-not-delete behavior.
- [ ] **Step 2: Write failing HTTP tests** for Vietnamese/English labels, validation errors retaining safe input, CSRF, Admin/Dispatcher access and audit creation.
- [ ] **Step 3: Run focused tests** and confirm missing modules cause the expected failures.
- [ ] **Step 4: Implement repositories/services** using explicit selected columns, transactions for write plus audit, Zod schemas, uppercase trimmed vendor codes and uppercase whitespace-normalized plates.
- [ ] **Step 5: Implement responsive list/form/detail views** with search, status filter, pagination and no hard deletion action.
- [ ] **Step 6: Run repository and route tests**, then all Node tests; expect all PASS.
- [ ] **Step 7: Commit checkpoint:** `git commit -m "feat: manage vendors and vehicles"`.

### Task 3: Inspections, drivers and tracking devices

**Files:**
- Create: `gps-receiver/src/modules/vehicles/inspection-repository.js`
- Create: `gps-receiver/src/modules/vehicles/inspection-service.js`
- Create: `gps-receiver/src/modules/drivers/repository.js`
- Create: `gps-receiver/src/modules/drivers/service.js`
- Create: `gps-receiver/src/modules/drivers/routes.js`
- Create: `gps-receiver/src/modules/devices/repository.js`
- Create: `gps-receiver/src/modules/devices/service.js`
- Create: `gps-receiver/src/modules/devices/routes.js`
- Create/Modify: corresponding EJS views under `vehicles`, `drivers`, `devices`
- Create: `gps-receiver/test/operations-services.test.js`

**Interfaces:**
- Produces inspection history ordered by `inspected_on desc`, driver CRUD/deactivate and device list/rename/deactivate.
- Device IDs are immutable after creation; only display name/status/notes change.

- [ ] **Step 1: Write failing tests** for inspection expiry validation, unique active license number, phone normalization without inventing country codes, uppercase immutable Device ID and latest-received timestamp display.
- [ ] **Step 2: Verify RED** with `node --test test/operations-services.test.js`.
- [ ] **Step 3: Implement transaction-safe services and audit records**; an inspection is appended, never overwritten; deactivation is rejected while an active assignment depends on the record.
- [ ] **Step 4: Implement views/routes** with expiry badges at 30/60 days, complete bilingual labels and accessible form feedback.
- [ ] **Step 5: Run focused and full tests**; expect PASS.
- [ ] **Step 6: Commit checkpoint:** `git commit -m "feat: manage inspections drivers and devices"`.

### Task 4: Effective-dated assignments and reconciliation queue

**Files:**
- Create: `gps-receiver/src/modules/assignments/repository.js`
- Create: `gps-receiver/src/modules/assignments/service.js`
- Create: `gps-receiver/src/modules/assignments/routes.js`
- Create: `gps-receiver/src/modules/assignments/reconciliation-repository.js`
- Create: `gps-receiver/src/web/views/assignments/index.ejs`
- Create: `gps-receiver/src/web/views/assignments/form.ejs`
- Create: `gps-receiver/src/web/views/assignments/detail.ejs`
- Create: `gps-receiver/test/assignments.integration.test.js`

**Interfaces:**
- Produces `assignmentService.start(command, actor)`, `close(id, effectiveTo, actor)`, `correctHistory(command, adminActor)`.
- Produces reconciliation jobs keyed by assignment and affected local dates; Phase 3 consumes these jobs.

- [ ] **Step 1: Write failing integration tests** proving half-open intervals, rejection of vehicle/device overlap, optional driver, required vendor/vehicle/device, close-before-new assignment transaction, Dispatcher current changes and Admin-only historical corrections.
- [ ] **Step 2: Verify RED** against a migrated disposable database.
- [ ] **Step 3: Implement assignment repository/service** using serializable transactions or row/advisory locks per vehicle/device and translate exclusion violations into field-level conflicts.
- [ ] **Step 4: Queue reconciliation** whenever an assignment begins in the past or historical boundaries change; never synchronously rewrite a large GPS range in the HTTP request.
- [ ] **Step 5: Implement history timeline and forms** with explicit timezone labels and a confirmation screen for Admin historical corrections.
- [ ] **Step 6: Run integration/full suites**; expect PASS with no overlapping intervals.
- [ ] **Step 7: Commit checkpoint:** `git commit -m "feat: track effective fleet assignments"`.

### Task 5: Audit viewer and role-complete navigation

**Files:**
- Create: `gps-receiver/src/modules/audit/repository.js`
- Create: `gps-receiver/src/modules/audit/routes.js`
- Create: `gps-receiver/src/web/views/audit/index.ejs`
- Create: `gps-receiver/test/rbac.test.js`
- Modify: all Phase 2 route composition and navigation files

**Interfaces:**
- Produces Admin-only paginated audit lookup by actor/action/entity/date.
- Consumes audit rows written by Tasks 2–4.

- [ ] **Step 1: Write a failing role matrix test** enumerating every management route and expected anonymous/Admin/Dispatcher result; assert GPS ingestion remains public.
- [ ] **Step 2: Write failing audit redaction tests** proving password hashes, session tokens, CSRF tokens and database URLs never appear in before/after JSON.
- [ ] **Step 3: Implement route guards, navigation visibility and audit viewer** using server-side authorization independent of hidden menu items.
- [ ] **Step 4: Run role, audit and all Node tests**; expect PASS.
- [ ] **Step 5: Commit checkpoint:** `git commit -m "feat: enforce fleet roles and audit changes"`.

### Task 6: Phase 2 acceptance

**Files:**
- Modify: `gps-receiver/README.md`
- Modify: `gps-receiver/docs/OPERATIONS.md`
- Create: `gps-receiver/docs/PHASE2-ACCEPTANCE.md`

- [ ] **Step 1: Run all Node, PostgreSQL integration and Pester suites** with fresh output and zero failures.
- [ ] **Step 2: Create one Admin and one Dispatcher through a protected bootstrap command**, then force password change on first login; do not record passwords.
- [ ] **Step 3: Manually exercise Vietnamese and English flows** for vendor, vehicle, inspection, driver, device and assignment; record outcomes.
- [ ] **Step 4: Verify role boundaries** by attempting Admin-only routes as Dispatcher and direct URLs not shown in navigation.
- [ ] **Step 5: Verify GPS continuity** with real-format GET/POST while creating and closing an assignment.
- [ ] **Step 6: Record evidence** in `PHASE2-ACCEPTANCE.md` without secrets.
- [ ] **Step 7: Commit checkpoint:** `git commit -m "docs: verify fleet operations UI"`.

## Phase 2 Definition of Done

- Internal users manage vendor, vehicle, inspection, driver, device and effective assignment records in Vietnamese or English.
- Admin/Dispatcher permissions are enforced server-side and audited.
- No vendor login/share path exists.
- Existing GPS ingestion remains compatible and uninterrupted.
- Automated and manual acceptance evidence is recorded.
