# Thiết kế website quản lý đội xe và lịch sử GPS

**Ngày:** 2026-08-07  
**Trạng thái:** Đã duyệt trong hội thoại, chờ người dùng rà soát tài liệu  
**Phạm vi:** Website nội bộ Node.js, PostgreSQL/PostGIS, Bootstrap, bản đồ và tra cứu địa chỉ offline

## 1. Mục tiêu

Nâng cấp dịch vụ nhận GPS hiện tại thành một website quản lý đội xe dùng nội bộ. Hệ thống tiếp tục nhận dữ liệu từ ứng dụng Android bằng contract hiện có trên TCP `5055`, lưu toàn bộ lịch sử GPS trong PostgreSQL, quản lý vendor, xe, tài xế, thiết bị và lịch sử phân công, đồng thời cung cấp bản đồ offline và dashboard theo tháng.

Hệ thống phục vụ hai vai trò nội bộ là **Quản trị** và **Điều phối**. Vendor không có tài khoản và không truy cập hệ thống; vendor chỉ là chiều dữ liệu dùng để phân nhóm, tổng hợp sản lượng và chuẩn bị cho đối soát nội bộ.

## 2. Phạm vi phiên bản đầu

### Có trong phạm vi

- Giữ nguyên API OsmAnd GET và JSON POST hiện tại.
- Quản lý vendor, xe, đăng kiểm, tài xế và thiết bị GPS.
- Cho phép thay đổi tài xế và Device ID theo thời gian, có đầy đủ lịch sử phân công.
- Lưu toàn bộ lịch sử GPS, không tự xóa theo retention mặc định.
- Tính quãng đường tự động theo xe và ngày; tổng hợp theo tháng.
- Tính thời gian dừng/đỗ để xem trong lịch sử và báo cáo chi tiết.
- Bản đồ miền Bắc Việt Nam hoạt động khi mất Internet. Trong đặc tả này, miền Bắc là toàn bộ Bắc Bộ gồm Tây Bắc Bộ, Đông Bắc Bộ và Đồng bằng sông Hồng; không bao gồm Bắc Trung Bộ.
- Tra tọa độ thành địa chỉ offline.
- Dashboard tháng, báo cáo nội bộ và xuất Excel/CSV.
- Giao diện responsive bằng Bootstrap, hỗ trợ Tiếng Việt và English.
- Audit log, giám sát lỗi nhận GPS, backup và quy trình khôi phục.

### Ngoài phạm vi phiên bản đầu

- Công thức, đơn giá và quy trình thanh toán vendor. Schema chỉ chừa ranh giới để bổ sung sau.
- Tài khoản, portal hoặc link chia sẻ cho vendor.
- Cấu hình endpoint Internet. Kiến trúc chuẩn bị một public ingestion gateway riêng, nhưng việc chọn tunnel/tên miền thực hiện sau.
- Xóa/sửa dữ liệu GPS gốc từ giao diện.
- Điều hành chuyến đi, bắt đầu/kết thúc chuyến hoặc trạng thái khóa điện.

## 3. Kiến trúc

```text
Điện thoại Android
  | GET /?id=... hoặc POST /api/locations
  v
Node.js :5055
  |-- API nhận GPS tương thích hiện tại
  |-- Website Bootstrap + API quản trị
  |-- Xác thực, RBAC và audit log
  |-- Quản lý đội xe và lịch sử phân công
  |-- Tính số liệu ngày/tháng
  |-- Phục vụ MapLibre, PMTiles và tài nguyên web cục bộ
  |
  |-- PostgreSQL + PostGIS trên Windows
  |     `-- Dữ liệu nghiệp vụ, GPS và số liệu tổng hợp
  |
  `-- Nominatim trong Podman, chỉ bind loopback
        `-- Reverse geocoding từ dữ liệu OSM miền Bắc
```

Node.js là modular monolith: một tiến trình triển khai, nhưng mã được tách theo module có interface rõ ràng. Các module chính gồm `auth`, `vendors`, `vehicles`, `drivers`, `devices`, `assignments`, `tracking`, `metrics`, `maps`, `reports`, `audit` và `system`.

Chỉ Node.js mở `5055` trong LAN. PostgreSQL, Nominatim và công cụ bản đồ chỉ lắng nghe trên loopback hoặc mạng Podman. Khi cấu hình Internet sau này, một gateway HTTPS riêng chỉ được chuyển tiếp tới route nhận GPS; dashboard và API quản trị không được công khai qua gateway đó.

## 4. Công nghệ

- Node.js 24 với dependency được khóa bằng lockfile.
- PostgreSQL 17 và PostGIS cho dữ liệu không gian và khoảng cách địa lý.
- Bootstrap 5.3 cài cục bộ, không dùng CDN.
- MapLibre GL JS và PMTiles cài/phục vụ cục bộ.
- Nominatim trong Podman để reverse geocoding offline.
- Dữ liệu nền từ OpenStreetMap cho miền Bắc Việt Nam, luôn hiển thị attribution theo giấy phép dữ liệu.
- SSE cho cập nhật vị trí mới nhất trên dashboard; không cần WebSocket trong phiên bản đầu.

Mục tiêu tải là tối đa 350 thiết bị với chu kỳ danh định 60 giây, tương đương khoảng 504.000 điểm/ngày và trung bình gần 6 request/giây, cộng khả năng gửi bù theo đợt sau khi mất mạng. Kế hoạch triển khai phải benchmark tốc độ ingest, dung lượng mỗi partition tháng và dung lượng backup trên PC thật trước khi chuyển đổi. Cảnh báo dung lượng dựa trên tốc độ tăng đo được, không dựa trên một kích thước hàng ước đoán.

## 5. Route và contract

### Nhận GPS

- `GET /?id=...&lat=...&lon=...&timestamp=...&speed=...&accuracy=...`
- `POST /api/locations` với JSON có cùng tên trường.
- Device ID hợp lệ có dạng `AND-` và 16 ký tự hex, không phân biệt hoa/thường; server chuẩn hóa thành chữ hoa.
- Thành công trả `200` với `accepted`, `deviceId` và `receivedAt`.
- Payload sai trả `400` với mã lỗi và tên trường ổn định.
- Sai media type trả `415`; quá giới hạn trả `413`; vượt rate limit trả `429`.
- PostgreSQL không ghi được trả `503`. Server không xác nhận thành công giả để app có thể gửi lại.
- Yêu cầu gửi lại trùng được coi là thành công idempotent nhưng không tạo điểm thứ hai.

### Website nội bộ

- `/login`, `/logout`
- `/dashboard`
- `/map`
- `/vendors`, `/vehicles`, `/drivers`, `/devices`, `/assignments`
- `/gps-history`, `/reports`
- `/admin/users`, `/admin/settings`, `/admin/audit`, `/admin/ingestion`
- `/health` cung cấp trạng thái tối thiểu; chi tiết nhạy cảm chỉ cho Quản trị đã đăng nhập.

## 6. Mô hình dữ liệu

### Bảng nghiệp vụ

- `vendors`: mã duy nhất, tên, liên hệ, trạng thái, ghi chú, timestamps.
- `vehicles`: ID nghiệp vụ, biển số duy nhất, loại xe, hãng, model, năm sản xuất, trạng thái, ghi chú.
- `vehicle_inspections`: xe, số/đơn vị đăng kiểm, ngày kiểm định, ngày hết hạn, ghi chú, timestamps.
- `drivers`: mã tài xế, họ tên, điện thoại, GPLX, ngày hết hạn GPLX, trạng thái, ghi chú.
- `tracking_devices`: Device ID duy nhất đã chuẩn hóa, tên mô tả, trạng thái, lần nhận cuối.
- `assignments`: vendor, xe, tài xế, thiết bị, `effective_from`, `effective_to`, người tạo và ghi chú.

Database ngăn các khoảng phân công đang hiệu lực chồng nhau đối với cùng xe hoặc cùng thiết bị. Mọi thay đổi phân công được thực hiện bằng đóng khoảng cũ và tạo khoảng mới, không ghi đè lịch sử.

### GPS và tổng hợp

- `gps_positions`: ID, Device ID, assignment/vehicle/driver/vendor đã phân giải, `device_time`, `received_at`, điểm PostGIS WGS84, tốc độ, độ chính xác, nguồn, trạng thái chất lượng và khóa chống trùng.
- `gps_position_addresses`: vị trí/cache key, tên đường, xã/phường, quận/huyện, tỉnh/thành, raw geocoder response đã giới hạn, thời gian cập nhật.
- `daily_vehicle_metrics`: xe, ngày Việt Nam, tổng mét, thời gian dừng, số điểm tổng/hợp lệ/loại, thời gian đầu/cuối và phiên bản thuật toán.
- `ingestion_rejections`: thời gian, nguồn rút gọn, mã lỗi, trường lỗi và request correlation ID; không lưu nguyên payload nhạy cảm.

`gps_positions` được partition theo tháng dựa trên `device_time`. Dữ liệu gốc là bất biến và không có retention tự động. Bảng số liệu ngày là dữ liệu dẫn xuất có thể tái tạo.

### Xác thực và kiểm soát

- `users`: username duy nhất, password hash, role, locale, trạng thái.
- `sessions`: phiên có thời hạn và khả năng thu hồi.
- `audit_logs`: người dùng, hành động, loại/ID đối tượng, before/after đã lọc dữ liệu nhạy cảm, timestamp và correlation ID.
- `system_settings`: ngưỡng nghiệp vụ có validation, phiên bản và audit.

## 7. Phân giải lịch sử phân công

- Điểm GPS được lưu ngay cả khi Device ID chưa được gán.
- Assignment được chọn theo `device_time`, không theo `received_at`, để dữ liệu gửi bù thuộc đúng xe/tài xế/vendor tại thời điểm phát sinh.
- Điểm chưa gán có `assignment_id` rỗng và xuất hiện trong hàng chờ xử lý.
- Khi thêm assignment có hiệu lực trong quá khứ, job reconciliation gán lại các điểm chưa gán trong khoảng đó và tính lại các ngày bị ảnh hưởng.
- Không tự động chuyển điểm đã gán sang assignment khác nếu có xung đột; thao tác sửa lịch sử cần xác nhận của Quản trị và audit đầy đủ.

## 8. Quãng đường và thời gian dừng

Múi giờ nghiệp vụ là `Asia/Ho_Chi_Minh`. Điểm được sắp theo `device_time` trong từng xe và ngày.

Khoảng cách giữa hai điểm hợp lệ liên tiếp được tính bằng PostGIS geography. Mặc định không cộng đoạn nếu:

- độ chính xác của điểm vượt 100 m;
- timestamp không tăng;
- vận tốc suy ra vượt 200 km/h;
- khoảng cách thời gian giữa hai điểm vượt 15 phút;
- điểm nằm ngoài miền giá trị GPS hợp lệ.

Mọi ngưỡng đều do Quản trị cấu hình và có audit. Điểm bị loại vẫn nằm trong lịch sử cùng lý do chất lượng. Dữ liệu gửi bù hoặc thay đổi assignment đưa ngày liên quan vào hàng đợi tính lại.

Xe được xem là dừng khi tốc độ không quá 3 km/h và dịch chuyển không quá 50 m. Chỉ cộng khoảng dừng giữa các điểm liên tục; khoảng mất GPS không được tính là dừng. Số liệu dừng có trong lịch sử/báo cáo chi tiết nhưng không hiển thị trên dashboard.

## 9. Bản đồ và địa chỉ offline

- PMTiles chứa vector tiles cho miền Bắc Việt Nam và được Node.js phục vụ với HTTP range requests.
- MapLibre render bản đồ, vị trí mới nhất, tuyến đường và điểm chất lượng thấp hoàn toàn trong LAN.
- Nominatim chạy trong Podman với database OSM riêng, không dùng dịch vụ geocoding Internet.
- Không reverse-geocode mọi điểm lúc ingest. Vị trí mới nhất được ưu tiên; lịch sử được tra khi người dùng mở và kết quả được cache theo vùng gần nhau.
- Nominatim lỗi không làm request GPS thất bại. UI hiển thị tọa độ và trạng thái “địa chỉ đang chờ”.
- Quy trình cập nhật bản đồ/geocoder là tác vụ quản trị riêng, dùng artifact có checksum và giữ bản cũ để rollback.

## 10. Phân quyền

### Quản trị

- Toàn quyền quản lý người dùng, vendor, xe, tài xế, thiết bị, đăng kiểm và assignment.
- Sửa cấu hình thuật toán, chạy reconciliation/recompute và xem trạng thái hệ thống.
- Xem audit log và lỗi ingestion.
- Không xóa GPS gốc qua UI.

### Điều phối

- Xem dashboard, bản đồ, lịch sử và báo cáo.
- Thêm/sửa dữ liệu vận hành, đăng kiểm và assignment hiện tại.
- Xuất Excel/CSV.
- Không quản lý tài khoản, cấu hình hệ thống, chạy sửa lịch sử hàng loạt hoặc xóa dữ liệu.

Vendor không có tài khoản và không có route truy cập riêng.

## 11. Giao diện song ngữ

Tiếng Việt là mặc định và English là ngôn ngữ thứ hai. Người dùng đổi ngôn ngữ trên thanh điều hướng; lựa chọn được lưu theo tài khoản. Chuỗi giao diện dùng key i18n, không hard-code trong template. Dữ liệu do người dùng nhập như tên vendor, xe và tài xế không tự dịch.

Các màn hình chính:

- đăng nhập;
- dashboard tháng;
- bản đồ đội xe;
- vendor;
- xe và chi tiết xe;
- đăng kiểm;
- tài xế;
- thiết bị;
- lịch sử phân công;
- lịch sử GPS theo ngày;
- báo cáo và xuất dữ liệu;
- người dùng, cấu hình, audit và lỗi ingestion.

## 12. Dashboard tháng

Dashboard có bộ lọc tháng, vendor, xe và tài xế, bao gồm:

- tổng quãng đường toàn đội xe;
- số xe đang hoạt động, chậm tín hiệu, mất tín hiệu và chưa gán thiết bị;
- biểu đồ quãng đường theo ngày;
- xếp hạng xe chạy nhiều nhất;
- tổng quãng đường theo vendor;
- xe sắp hết hạn đăng kiểm trong 30/60 ngày;
- bản đồ vị trí mới nhất của đội xe.

Trạng thái mặc định: xanh nếu nhận GPS dưới 5 phút, vàng từ 5 đến 15 phút, đỏ trên 15 phút. Dashboard không hiển thị tổng hoặc biểu đồ thời gian dừng/đỗ.

## 13. Bảo mật

- Website quản trị chỉ dùng trong LAN ở phiên bản đầu.
- Mật khẩu được hash bằng Argon2id với tham số được khóa cấu hình và có khả năng nâng cấp hash khi đăng nhập; không log mật khẩu/token.
- Session cookie `HttpOnly`, `SameSite=Strict`, có timeout, rotation sau đăng nhập và thu hồi khi khóa tài khoản.
- Form thay đổi trạng thái có CSRF protection; template escape output; API có validation schema.
- Rate limit tách riêng cho login, ingestion và route quản trị.
- PostgreSQL dùng tài khoản ứng dụng quyền tối thiểu và chỉ bind loopback.
- Public Internet gateway tương lai chỉ cho phép route ingestion, HTTPS và giới hạn request; tuyệt đối không forward dashboard.

## 14. Xử lý lỗi và quan sát

- Mỗi request có correlation ID.
- Lỗi DB khi ingest trả `503`; lỗi geocoder chỉ đánh dấu pending.
- Job tổng hợp/reconciliation retry có backoff và không chạy đồng thời trên cùng xe/ngày.
- Health check phân biệt app, database, disk, geocoder, map artifact và tuổi backup.
- Log dạng cấu trúc, xoay vòng, không chứa password, session, raw authorization hoặc toàn bộ payload GPS.
- UI quản trị hiển thị điểm chưa gán, job lỗi, partition sắp thiếu và backup quá hạn.

## 15. Backup, nhập dữ liệu và chuyển đổi

- Backup PostgreSQL hằng ngày bằng `pg_dump`, ghi file tạm rồi promote nguyên tử, kèm SHA-256.
- Backup giữ theo chính sách cấu hình; việc xóa backup cũ chỉ chạy sau khi backup mới thành công.
- Có restore drill vào database tạm và kiểm tra schema/số bản ghi.
- JSONL hiện tại được giữ read-only trong quá trình chuyển đổi và nhập idempotent vào PostgreSQL.
- Ba Device ID kiểm thử `AND-0123456789ABCDEF`, `AND-FEDCBA9876543210`, `AND-A1B2C3D4E5F60718` không được nhập.
- Hai Device ID LAN thực tế và mọi dữ liệu hợp lệ khác được giữ.
- Chỉ chuyển traffic sang phiên bản PostgreSQL sau khi đối chiếu số bản ghi và smoke test thành công; có thể quay lại receiver cũ nếu chuyển đổi lỗi.

## 16. Kiểm thử và nghiệm thu

### Kiểm thử tự động

- Unit test cho validation, chuẩn hóa Device ID, chống trùng, quyền, khoảng assignment, khoảng cách và trạng thái xe.
- Integration test với PostgreSQL/PostGIS thật cho migration, constraint, partition, ingest idempotent, reconciliation và daily metrics.
- HTTP test cho contract GET/POST cũ, đăng nhập, CSRF, RBAC, locale và error contract.
- Test geocoder/map bằng adapter cục bộ; không phụ thuộc Internet trong test.
- Test import JSONL chứng minh loại đúng ba ID kiểm thử và không nhập trùng.
- Test backup/restore script trên database tạm có tên/prefix an toàn.

### Nghiệm thu thủ công

- App Android hiện tại gửi thành công mà không đổi contract.
- Hai điện thoại thật xuất hiện đúng xe/tài xế/vendor theo assignment.
- Chuyển assignment có hiệu lực và dữ liệu gửi bù được gán đúng theo `device_time`.
- Xem tuyến đường và địa chỉ khi Internet bị ngắt.
- Dashboard đổi tháng/vendor và tổng km khớp dữ liệu ngày.
- Chuyển Việt/Anh không mất bộ lọc hoặc phiên đăng nhập.
- Điều phối không truy cập được chức năng chỉ dành cho Quản trị.
- PostgreSQL hoặc Nominatim dừng tạo đúng degraded behavior đã thiết kế.

## 17. Tiêu chí hoàn thành

- Website chạy liên tục trên PC Windows và tự khởi động sau reboot.
- API GPS cổng 5055 tương thích app hiện tại và không còn lưu JSONL làm nguồn dữ liệu chính.
- PostgreSQL lưu đúng lịch sử không mất điểm và có backup/restore đã thử.
- Quản lý đầy đủ vendor, xe, tài xế, đăng kiểm, thiết bị và assignment theo thời gian.
- Quãng đường ngày/tháng được tính lại ổn định, có lý do cho điểm bị loại.
- Bản đồ và reverse geocoding miền Bắc hoạt động offline.
- Dashboard tháng đúng phạm vi đã duyệt và không có thời gian dừng/đỗ.
- Giao diện Việt/Anh và RBAC đạt kiểm thử.
- Cấu hình Internet được để lại thành một bước triển khai riêng, không làm suy yếu bảo mật LAN hiện tại.
