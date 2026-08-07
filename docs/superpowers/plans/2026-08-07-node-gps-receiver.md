# Node GPS Receiver Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Xây dựng và cài một web Node.js luôn chạy trên Windows, nhận OsmAnd GET và JSON POST trên cổng 5055, lưu lịch sử và hiển thị dashboard LAN.

**Architecture:** Node.js built-in HTTP server tách validation, persistence, rate limiting và presentation thành module độc lập. JSONL là nguồn lịch sử append-only; snapshot atomic phục hồi state mới nhất; WinSW quản lý process dưới dạng Windows Service.

**Tech Stack:** Node.js LTS, CommonJS, `node:test`, PowerShell 5.1, WinSW, Windows Firewall.

## Global Constraints

- Runtime không có npm dependency; chỉ dùng Node.js built-in modules.
- Port mặc định `5055`, bind `0.0.0.0`, chỉ mở Windows Firewall cho network profile Private/LAN.
- Device ID phải khớp `^AND-[0-9A-F]{16}$`; speed dùng knot.
- JSON body tối đa 16 KiB; rate limit mặc định 120 request/phút/IP.
- Persistence phải thành công trước khi response accepted và cập nhật state.
- Không mở router port-forward, không dùng dịch vụ này trực tiếp trên Internet.

---

### Task 1: Node project and payload validation

**Files:**
- Create: `gps-receiver/package.json`
- Create: `gps-receiver/src/validation.js`
- Create: `gps-receiver/test/validation.test.js`

**Interfaces:**
- Produces: `normalizeLocation(input, nowMs) -> { ok, value } | { ok, code, field }`.

- [ ] Viết test cho payload hợp lệ, Unix seconds/milliseconds, ID sai, tọa độ biên, field thiếu, speed/accuracy âm hoặc không phải số.
- [ ] Chạy `node --test gps-receiver/test/validation.test.js`; xác nhận fail vì module chưa tồn tại.
- [ ] Implement normalization tối thiểu và error code ổn định.
- [ ] Chạy lại test; xác nhận pass.

### Task 2: JSONL store, snapshot and statistics

**Files:**
- Create: `gps-receiver/src/store.js`
- Create: `gps-receiver/test/store.test.js`

**Interfaces:**
- Produces: `createStore({dataDir, now, retentionDays, inactivityMs})` với `init`, `append`, `devices`, `stats`, `health`, `flush`, `close`, `applyRetention`.

- [ ] Viết test chứng minh append tạo đúng daily JSONL, state chỉ đổi sau write thành công, snapshot reload, snapshot hỏng được quarantine, retention chỉ xóa file đúng mẫu và stats/inactive đúng.
- [ ] Chạy test; xác nhận fail vì module thiếu.
- [ ] Implement hàng đợi Promise, atomic snapshot và retention boundary.
- [ ] Chạy Task 1–2 tests; xác nhận pass.

### Task 3: HTTP API, rate limiter and dashboard

**Files:**
- Create: `gps-receiver/src/rate-limit.js`
- Create: `gps-receiver/src/dashboard.js`
- Create: `gps-receiver/src/app.js`
- Create: `gps-receiver/test/app.test.js`

**Interfaces:**
- Produces: `createApp(options) -> {server, start(), stop()}`; endpoints `/`, `/api/locations`, `/api/devices`, `/api/stats`, `/health`, `/events`, `/dashboard`.

- [ ] Viết integration test trên ephemeral port cho valid GET/POST, malformed/oversized JSON, content type, 404/405/429/503, stats và dashboard.
- [ ] Chạy test; xác nhận fail vì app chưa tồn tại.
- [ ] Implement router, 16 KiB reader, JSON responses, SSE heartbeat, dashboard responsive và graceful stop.
- [ ] Chạy toàn bộ Node tests; xác nhận pass.

### Task 4: Runtime entry point and Postman collection

**Files:**
- Create: `gps-receiver/src/index.js`
- Create: `gps-receiver/postman/gps-receiver.postman_collection.json`
- Create: `gps-receiver/postman/gps-receiver.local.postman_environment.json`
- Create: `gps-receiver/test/config.test.js`

**Interfaces:**
- Environment: `GPS_HOST`, `GPS_PORT`, `GPS_DATA_DIR`, `GPS_RETENTION_DAYS`, `GPS_INACTIVITY_MS`, `GPS_RATE_LIMIT`.

- [ ] Viết test config mặc định và reject port/range/path không hợp lệ.
- [ ] Chạy test; xác nhận fail.
- [ ] Implement entry point, signal handling và Postman request GET/POST/health/stats với scripts tự sinh Device ID.
- [ ] Chạy tests và smoke server trên port tạm; xác nhận pass.

### Task 5: Windows Service installer

**Files:**
- Create: `gps-receiver/windows/InternalGpsReceiver.xml.template`
- Create: `gps-receiver/windows/Install-GpsReceiver.ps1`
- Create: `gps-receiver/windows/Uninstall-GpsReceiver.ps1`
- Create: `gps-receiver/windows/Test-GpsReceiver.ps1`
- Create: `gps-receiver/test/windows-scripts.Tests.ps1`

**Interfaces:**
- Installer copies app to `C:\Program Files\InternalGpsReceiver`, data to `C:\ProgramData\InternalGpsReceiver`, registers service `InternalGpsReceiver` and firewall rule `InternalGpsReceiver-5055`.

- [ ] Viết Pester tests cho template resolution, idempotent names, Private firewall scope, default uninstall preserves data, and purge path safety.
- [ ] Chạy Pester; xác nhận fail vì scripts/template thiếu.
- [ ] Implement artifact hash verification, WinSW config, service recovery, firewall và safe uninstall.
- [ ] Chạy Node + Pester tests và `Install-GpsReceiver.ps1 -WhatIf`; xác nhận không mutation.

### Task 6: Documentation, installation and acceptance

**Files:**
- Create: `gps-receiver/README.md`
- Create: `gps-receiver/docs/OPERATIONS.md`
- Create: `gps-receiver/docs/POSTMAN.md`
- Create: `gps-receiver/docs/ANDROID.md`
- Modify: `README.md`
- Modify: `config/traccar-profile.example.json`

- [ ] Document build/run, URLs, Postman import, Android profile, data retention, backup and LAN-only security.
- [ ] Install Node.js LTS from official winget package and verify version.
- [ ] Download/verify WinSW, run elevated installer, create Private firewall rule and start service.
- [ ] Verify `/health`, valid GET, valid POST, invalid ID, persistence, dashboard and service status.
- [ ] Detect LAN IPv4, update example profile with a clearly marked replaceable host, and report the exact phone URL without committing secrets.
