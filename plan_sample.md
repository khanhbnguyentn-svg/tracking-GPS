# Checkpoint triển khai hệ thống quản lý xe

**Thời điểm cập nhật:** 2026-08-08  
**Workspace chính:** `D:\app android`  
**Worktree đang phát triển:** `D:\app android\.worktrees\fleet-foundation`  
**Nhánh:** `feature/fleet-foundation`

## 1. Phạm vi đã thống nhất

- Website quản trị và điều phối xe chạy bằng Node.js trên cổng `5055`.
- Tiếp tục tương thích API GPS hiện tại:
  - `GET /?id=...`
  - `POST /api/locations`
- Cơ sở dữ liệu PostgreSQL/PostGIS; lưu toàn bộ lịch sử GPS.
- Quản lý vendor, xe, biển số, người lái, thiết bị GPS, đăng kiểm và lịch sử phân công theo thời gian hiệu lực.
- Tự động tính quãng đường theo ngày từ dữ liệu GPS; dashboard và báo cáo theo tháng.
- Dashboard **không hiển thị thời gian dừng đỗ**. Dữ liệu dừng đỗ vẫn được giữ cho màn hình chi tiết/báo cáo.
- Bản đồ không dùng Google Maps: MapLibre + PMTiles offline cho miền Bắc Việt Nam.
- Reverse geocoding offline bằng Nominatim chạy qua Podman.
- Giao diện Bootstrap song ngữ Việt/Anh.
- Chỉ có người dùng nội bộ: Admin và Dispatcher. Không tạo tài khoản hoặc chia sẻ dữ liệu cho vendor.
- Phần thanh toán/vendor sẽ được xem xét sau.
- Website quản trị chỉ dùng trong LAN. Kết nối nhận GPS qua Internet sẽ cấu hình ở giai đoạn sau.

## 2. Tài liệu thiết kế và kế hoạch đã duyệt

- Thiết kế chính: `docs/superpowers/specs/2026-08-07-fleet-management-website-design.md`
- Roadmap: `docs/superpowers/plans/2026-08-07-fleet-management-roadmap.md`
- Giai đoạn 1: `docs/superpowers/plans/2026-08-07-fleet-platform-foundation.md`
- Giai đoạn 2: `docs/superpowers/plans/2026-08-07-fleet-operations-ui.md`
- Giai đoạn 3: `docs/superpowers/plans/2026-08-07-fleet-analytics-dashboard.md`
- Giai đoạn 4: `docs/superpowers/plans/2026-08-07-fleet-offline-maps-operations.md`

## 3. Tiến độ hiện tại

### Đã hoàn thành

0. Kiểm tra lại máy sau thay đổi:
   - Node.js `24.19.0`, npm `11.17.0`, Git `2.45.1` đã có.
   - Windows PowerShell `5.1` đã có; PowerShell 7 và Podman chưa cài vì chưa cần cho giai đoạn hiện tại.
   - Dependency được khôi phục bằng `npm.cmd ci`: audit 0 lỗ hổng.

1. Hoàn thành thiết kế tổng thể và chia thành bốn kế hoạch triển khai tuần tự.
2. Hoàn thành Task 1 của giai đoạn nền tảng:
   - Tạo `gps-receiver/src/core/config.js`.
   - Cập nhật `gps-receiver/src/config.js` để giữ tương thích cấu hình cũ.
   - Tạo `gps-receiver/test/core-config.test.js`.
   - Tạo `gps-receiver/.env.example`.
   - Cập nhật `gps-receiver/package.json` và `package-lock.json`.
   - Cài dependency Node cố định phiên bản; kết quả audit tại thời điểm kiểm tra là 0 lỗ hổng.
   - Bộ test Node sau Task 1: 39/39 đạt.
3. Viết xong mã và unit test cho Task 2:
   - `gps-receiver/src/db/pool.js`
   - `gps-receiver/src/db/migrator.js`
   - `gps-receiver/scripts/migrate.js`
   - `gps-receiver/test/migrator.test.js`
   - Migration `001_extensions.sql` đến `004_tracking.sql`.
   - Unit test migrator: 4/4 đạt.
4. Schema hiện có bao gồm:
   - PostGIS và `btree_gist`.
   - Users, sessions, audit logs, system settings.
   - Vendors, vehicles, inspections, drivers, tracking devices.
   - Lịch sử phân công theo khoảng hiệu lực với exclusion constraint chống chồng lấn.
   - `gps_positions` partition theo tháng, kiểu `geography(Point,4326)`.
   - Chống trùng dữ liệu GPS và bảng ghi nhận dữ liệu bị từ chối.
5. Chuẩn bị runtime portable đã xác minh checksum:
   - Node.js `24.19.0` tại `server/runtime/node-v24.19.0`.
   - PostgreSQL `17.10` + PostGIS `3.6.2` trong cụm thử nghiệm `server/runtime/pg-postgis-test`.
   - Gói PostGIS có MD5 chính thức khớp: `FACA768CC580C4AB2EEF621BE05B408E`.
6. Hoàn thành Task 2 trên PostgreSQL/PostGIS thật:
   - PostgreSQL `17.10` + PostGIS `3.6.2` portable chạy loopback `127.0.0.1:55432`.
   - Migration chạy hai lần idempotent; có 4 dòng `schema_migrations` và partition `2026_08`, `2026_09`.
7. Hoàn thành Task 3:
   - Repository transaction lưu vị trí PostGIS, tự tạo Device ID, phân giải assignment, chống trùng và rollback.
8. Hoàn thành Task 4:
   - GET OsmAnd và POST JSON chỉ trả accepted sau khi PostgreSQL commit.
   - Dashboard compatibility đọc PostgreSQL; smoke-test HTTP cổng `15055` đã lưu đúng tọa độ.
9. Hoàn thành Task 5:
   - Import JSONL streaming, không xóa nguồn, loại đúng ba smoke ID, idempotent.
   - CLI bắt buộc `--source`, mặc định dry-run; chỉ ghi khi có `--apply`.
10. Task 6 đã hoàn thành:
    - Argon2id policy, token 32 byte, chỉ lưu SHA-256, idle 8 giờ, absolute 24 giờ và RBAC.
    - Login/logout HTTP, cookie an toàn, CSRF, trang đăng nhập Việt/Anh và CLI tạo/cập nhật user.
    - Bộ test hiện tại: 56/56 đạt khi có database integration test.
11. Task 7 đã hoàn thành phần mã nguồn và kiểm thử an toàn:
    - Installer PostgreSQL/PostGIS, DPAPI, ACL, file môi trường ngoài repository và migration trước khi chạy service.
    - Tài liệu cài đặt, import, cutover và rollback dành cho IT.
    - Pester: 8/8 đạt; chưa chạy cài đặt production vì cần phiên Administrator trên máy server đích.
12. Task 8 đã hoàn thành phần composition và load smoke local:
    - Migration-before-listen, shutdown một lần, timeout 15 giây và đóng pool theo thứ tự.
    - Load 10 phút ở 7 request/giây: 4.200 gửi, 4.200 lưu, p95 22,97 ms.
    - Live JSONL import/cutover còn chờ dữ liệu thật và IT chạy trên server đích.
13. Deployment Windows ổ D đã hoàn thành phần mã nguồn:
    - Một `-RootPath` mặc định `D:\InternalGPS` cho PostgreSQL, receiver, data và backup.
    - Kiểm tra ổ cố định NTFS, tối thiểu 20 GB, từ chối drive root/reparse point và purge sai đường dẫn.
    - Cài production vẫn cần người dùng chạy PowerShell Administrator trên máy server đích.
    - Máy hiện tại: ổ D cố định, NTFS, còn khoảng 84,3 GB; Node 59/59 và Pester 15/15 đạt.
    - Cache đã có Node.js 24.18.1 và WinSW 2.12.0 từ nguồn chính thức, hash khớp installer.
6. Đã bổ sung `.gitignore` để loại trừ `.npm-cache`, `node_modules`, runtime GPS, `.env`, log và tệp tạm.

### Đang làm dở

- Task 8: composition, diễn tập cutover và kiểm thử chấp nhận giai đoạn 1.
- PostgreSQL test sẽ được dừng khi kết thúc phiên; runtime/cache đều bị Git ignore và có thể khởi động lại.

### Chưa thực hiện

- Task 8: composition, diễn tập cutover và kiểm thử giai đoạn 1.
- Toàn bộ giai đoạn 2, 3 và 4.

## 4. Trạng thái đồng bộ GitHub

- Workspace hiện chưa có thư mục `.git`, nên chưa phải Git repository.
- Máy hiện chưa có Git CLI hoặc GitHub CLI trong PATH.
- Không tìm thấy remote thật trong dự án; README chỉ có URL mẫu `https://github.com/TEN_CUA_BAN/TEN_REPO.git`.
- Đang chờ người dùng cung cấp URL repository GitHub đích.
- Trước khi commit phải tiếp tục rà soát file sinh ra và dữ liệu nhạy cảm. Không commit:
  - `server/runtime/`
  - `server/cache/`
  - `gps-receiver/runtime/`
  - `gps-receiver/node_modules/`
  - `.npm-cache/`
  - `.env`, mật khẩu, token, database hoặc dữ liệu GPS thực tế.

## 5. Trình tự tiếp tục sau khi có URL GitHub

1. Cài hoặc dùng Git portable đã xác minh nguồn tải.
2. Rà soát danh sách file chuẩn bị commit và quét dữ liệu nhạy cảm.
3. Khởi tạo Git repository, đặt nhánh mặc định `main`, thêm remote do người dùng cung cấp.
4. Chạy lại test Node và PowerShell trước commit.
5. Tạo commit checkpoint rõ ràng và push lên GitHub bằng cơ chế đăng nhập của người dùng.
6. Xác minh remote branch và commit đã đồng bộ thành công.
7. Quay lại Task 2:
   - Khởi động PostgreSQL thử nghiệm ở `127.0.0.1:55432`.
   - Chạy migration lần một và lần hai để kiểm tra idempotency.
   - Kiểm tra PostGIS, bảng, partition, index và exclusion constraints.
   - Nếu đạt, đánh dấu Task 2 hoàn thành.
8. Tiếp tục Task 3 đến Task 8 của giai đoạn nền tảng theo kế hoạch.
9. Chỉ chuyển website cổng `5055` sang phiên bản PostgreSQL sau khi test và diễn tập cutover đạt.

## 6. Các nguyên tắc không được bỏ qua

- Không xác nhận nhận điểm GPS trước khi database lưu bền vững thành công.
- Không xóa nguồn JSONL khi import hoặc cutover.
- Không import ba Device ID smoke-test:
  - `AND-0123456789ABCDEF`
  - `AND-FEDCBA9876543210`
  - `AND-A1B2C3D4E5F60718`
- Không mở website quản trị trực tiếp ra Internet.
- Giữ múi giờ nghiệp vụ `Asia/Ho_Chi_Minh`.
- Luôn chạy toàn bộ test tại ranh giới mỗi giai đoạn.

