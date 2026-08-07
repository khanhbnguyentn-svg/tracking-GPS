# Checkpoint triển khai hệ thống quản lý xe

**Thời điểm lưu:** 2026-08-07  
**Workspace:** `D:\Server\tracking-GPS-main`  
**Trạng thái:** Tạm dừng triển khai để chờ URL GitHub và đồng bộ mã nguồn.

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
6. Đã bổ sung `.gitignore` để loại trừ `.npm-cache`, `node_modules`, runtime GPS, `.env`, log và tệp tạm.

### Đang làm dở

- Task 2: kiểm thử migration trên PostgreSQL/PostGIS thực tế.
- Cụm thử nghiệm đã từng khởi động thành công trên `127.0.0.1:55432` và PostgreSQL báo sẵn sàng nhận kết nối.
- Database thử nghiệm `fleet_migration_test` đã được tạo.
- Lệnh chạy migration thực tế bị ngắt khi người dùng yêu cầu chuyển sang đồng bộ GitHub; chưa được phép kết luận migration SQL đạt.
- Tại thời điểm ghi checkpoint:
  - PostgreSQL thử nghiệm trên cổng `55432`: không chạy.
  - Website/API trên cổng `5055`: không chạy.

### Chưa thực hiện

- Task 3: repository ghi vị trí PostgreSQL idempotent.
- Task 4: service tracking và bảo toàn hợp đồng HTTP hiện tại.
- Task 5: import JSONL có loại trừ ba thiết bị smoke-test.
- Task 6: Argon2id, session, CSRF và RBAC.
- Task 7: cài database/service Windows production.
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

