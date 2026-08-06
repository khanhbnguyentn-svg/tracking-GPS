# Plan.md — Android Location Tracking App (Traccar-compatible client)

> Tài liệu này là spec kỹ thuật đầy đủ để AI coding agent (Codex) triển khai app.
> Người yêu cầu (product owner) không phải dev — mọi quyết định kỹ thuật mặc định
> nên tuân theo spec này; nếu cần đổi hướng, agent nên hỏi lại thay vì tự quyết định âm thầm.

---

## 1. Mục tiêu sản phẩm

Xây dựng app Android **native (Kotlin)** chạy nền, tự động lấy vị trí GPS của thiết bị theo
chu kỳ và gửi lên một server nội bộ tương thích **Traccar** (https://github.com/traccar/traccar),
qua **OsmAnd HTTP protocol**. Server do bộ phận IT quản lý — **không thuộc phạm vi dự án này**.

### Ngoài phạm vi (Out of scope)
- Không cài đặt/deploy server Traccar.
- Không xây dựng backend riêng.
- Không cần bản đồ/UI xem lịch sử vị trí trong app (Traccar Web UI đã có sẵn phía server).

---

## 2. Tech stack bắt buộc

| Thành phần | Lựa chọn |
|---|---|
| Ngôn ngữ | Kotlin |
| Min SDK | 26 (Android 8.0) — bắt buộc để Foreground Service hoạt động ổn định |
| Target SDK | Bản mới nhất hiện hành (Android 14/15 API level tương ứng) |
| Location | `FusedLocationProviderClient` (Google Play Services location) |
| Background execution | `ForegroundService` (loại `location`) + `WorkManager` làm lớp dự phòng khởi động lại |
| Local storage | `Room` (SQLite) cho hàng đợi vị trí chưa gửi được |
| Config storage | `EncryptedSharedPreferences` (Jetpack Security) |
| Networking | `OkHttp` + `Retrofit` (hoặc thuần OkHttp nếu Retrofit không cần thiết cho endpoint đơn giản) |
| Dependency Injection | Hilt (khuyến nghị, không bắt buộc — agent có thể chọn manual DI nếu đơn giản hơn cho scope này) |
| Build | Gradle (Kotlin DSL), tương thích GitHub Actions CI |

---

## 3. Kiến trúc tổng thể

```
app/
 ├─ location/
 │   ├─ LocationForegroundService.kt   // service chạy nền, lấy vị trí định kỳ
 │   ├─ LocationWorker.kt               // WorkManager backup, restart service nếu bị kill
 │   └─ LocationRepository.kt
 ├─ data/
 │   ├─ db/ (Room: PendingLocationEntity, PendingLocationDao)
 │   └─ config/ (ConnectionProfile model, EncryptedPrefsDataStore)
 ├─ network/
 │   ├─ OsmAndProtocolClient.kt         // build & gửi request theo format OsmAnd
 │   ├─ ConnectionTester.kt             // logic test connection 2 bước
 │   └─ CertPinningConfig.kt            // xử lý chế độ chứng chỉ
 ├─ ui/
 │   ├─ config/ (ServerConfigScreen, ProfileListScreen)
 │   ├─ main/ (trạng thái tracking, bật/tắt)
 │   └─ permissions/ (luồng xin quyền theo từng bước)
 └─ MainApplication.kt
```

---

## 4. Đặc tả chức năng chi tiết

### 4.1 Permissions flow
Xin quyền theo đúng thứ tự (Android yêu cầu bắt buộc theo trình tự này):
1. `ACCESS_FINE_LOCATION`
2. Sau khi có (1) → xin riêng `ACCESS_BACKGROUND_LOCATION` (dialog hệ thống, kèm màn hình giải thích trước khi mở dialog — Android yêu cầu rationale rõ ràng cho quyền nhạy cảm này)
3. `POST_NOTIFICATIONS` (Android 13+)
4. `FOREGROUND_SERVICE_LOCATION` (khai báo trong Manifest, không cần runtime prompt)
5. Sau khi đủ quyền: gợi ý người dùng loại app khỏi Battery Optimization
   (`Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)`), có màn hình giải thích lý do trước khi mở.

Nếu người dùng từ chối bất kỳ quyền bắt buộc nào → hiển thị màn hình giải thích + nút mở Settings, **không crash, không im lặng bỏ qua**.

### 4.2 Location Foreground Service
- Chạy `ForegroundService` với notification cố định, nội dung ví dụ: "Đang chia sẻ vị trí".
- Lấy vị trí bằng `FusedLocationProviderClient`, `PRIORITY_BALANCED_POWER_ACCURACY`.
- Interval gửi vị trí: **cấu hình được** (mặc định 60s), lưu trong config, không hardcode.
- Mỗi điểm vị trí lấy được → lưu vào Room queue trước, sau đó thử gửi ngay; nếu gửi thành công mới xóa khỏi queue.
- `LocationWorker` (WorkManager, periodic ~15 phút — giới hạn tối thiểu của WorkManager) kiểm tra: nếu service không chạy trong khi tracking đang bật → khởi động lại.

### 4.3 Local Queue & Retry
- Bảng `PendingLocationEntity`: id, lat, lon, timestamp, speed, accuracy, sent(boolean), retryCount.
- Retry theo exponential backoff khi gửi thất bại (network lỗi, timeout).
- Giới hạn tối đa số bản ghi lưu trữ (ví dụ 10,000 điểm) — khi vượt, xóa bản ghi cũ nhất để tránh phình DB nếu mất mạng kéo dài.
- Gửi theo đúng thứ tự thời gian (FIFO) khi có mạng trở lại.

### 4.4 Network layer — OsmAnd protocol
Format request (tham khảo, cần verify lại với bản Traccar cụ thể IT triển khai):
```
{scheme}://{host}:{port}/?id={device_id}&lat={lat}&lon={lon}&timestamp={unix_time}&speed={speed}&accuracy={accuracy}
```
- `scheme` lấy từ config (`http` hoặc `https`), **không hardcode https**.
- Timeout riêng cho request gửi định kỳ (ví dụ connect 10s, read 15s) — khác với timeout của Test Connection.

### 4.5 Server Configuration screen (⭐ trọng tâm — linh hoạt kết nối)

Các trường bắt buộc:

| Trường | Loại input | Ghi chú |
|---|---|---|
| Server host/IP | Text | Validate không rỗng, không chứa scheme (`http://` tự nhập nhầm) |
| Port | Number | Default gợi ý 5055 |
| Scheme | Dropdown `http` / `https` | Quyết định URL cuối cùng |
| Device ID | Text hoặc quét QR | QR để tránh gõ sai — có thể để scope sau nếu không kịp |
| Certificate mode (chỉ hiện khi scheme = https) | Radio: `System CA` / `Custom CA (import .crt file)` / `Certificate Pinning (nhập SHA-256 fingerprint)` | Xem mục 4.7 |
| Update interval (giây) | Number | Default 60 |

- **Cho phép nhiều profile** (ví dụ "Test server", "Production server"), lưu list trong Room hoặc DataStore, có thể switch nhanh giữa các profile — chỉ 1 profile active tại một thời điểm.
- Toàn bộ config lưu qua `EncryptedSharedPreferences`, không lưu plaintext.

### 4.6 Test Connection feature (2 bước, hiển thị trạng thái từng bước)

```
[Bấm "Kiểm tra kết nối"]
  → Bước 1: Network check
     - HTTP HEAD/GET nhẹ tới {scheme}://{host}:{port}/
     - Timeout ngắn (~5s)
     - Kết quả: "✅ Server phản hồi" hoặc lỗi cụ thể
       (DNS not found / Connection refused / Timeout / SSL handshake failed)
  → Nếu bước 1 OK → Bước 2: Data path check
     - Gửi 1 điểm GPS test (dùng vị trí cache gần nhất, hoặc toạ độ 0,0 nếu chưa có fix,
       kèm cảnh báo cho user nếu dùng toạ độ giả)
     - Kết quả: "✅ Server đã nhận dữ liệu" (HTTP 200) hoặc lỗi
       (ví dụ 400 = device ID chưa đăng ký trên server — hiển thị gợi ý cụ thể này cho user)
```

Thông báo lỗi phải **cụ thể, không chung chung** — vì người dùng cuối (kể cả người cấu hình) có thể không phải dev.

### 4.7 Xử lý HTTPS / Certificate (bảo mật — không thỏa hiệp)

- **Không bao giờ** implement kiểu "trust-all certificates" / bỏ qua SSL validation toàn bộ — đây là lỗ hổng nghiêm trọng, agent không được tự ý làm tắt để "test cho nhanh" rồi quên bỏ.
- `System CA`: dùng validate chuẩn của hệ thống Android (mặc định).
- `Custom CA`: cho phép user import file `.crt` (ví dụ CA nội bộ công ty), thêm vào `NetworkSecurityConfig` scoped theo domain cụ thể, không áp dụng toàn cục.
- `Certificate Pinning`: user nhập SHA-256 fingerprint của cert server, dùng OkHttp `CertificatePinner`, chỉ pin theo domain đã cấu hình.
- Nếu scheme = `http` (không TLS): bắt buộc dùng `NetworkSecurityConfig` để **whitelist cleartext traffic chỉ cho đúng domain/IP đã cấu hình trong profile hiện tại**, tuyệt đối không set `cleartextTrafficPermitted="true"` toàn cục trong Manifest.

### 4.8 UI/UX tối thiểu cần có
- Màn hình chính: trạng thái tracking (bật/tắt), profile server đang active, số điểm đang chờ gửi trong queue.
- Màn hình config server (mục 4.5).
- Màn hình test connection (mục 4.6), có thể là dialog/bottom sheet trong màn config.
- Màn hình xin quyền có giải thích rõ ràng (không chỉ dialog hệ thống trơ).

---

## 5. Definition of Done / Acceptance Criteria

- [ ] App xin đủ quyền theo đúng thứ tự ở mục 4.1, xử lý được trường hợp từ chối.
- [ ] Service chạy nền gửi vị trí định kỳ, sống sót qua việc màn hình tắt, app bị swipe khỏi recent apps.
- [ ] Khi mất mạng: vị trí được lưu vào queue, tự động gửi lại khi có mạng, đúng thứ tự.
- [ ] Đổi được giữa `http` và `https` chỉ qua UI, không cần build lại app.
- [ ] Certificate pinning hoạt động đúng: từ chối kết nối nếu cert server không khớp fingerprint đã cấu hình.
- [ ] Test Connection phân biệt rõ lỗi mạng vs lỗi server từ chối dữ liệu (2 bước riêng biệt).
- [ ] Không có bất kỳ đoạn code nào tắt SSL validation toàn cục.
- [ ] Config được lưu mã hóa, không có secret/URL nội bộ nào log ra plaintext trong Logcat ở build release.

---

## 6. Lộ trình triển khai đề xuất (để agent chia nhỏ commit/PR)

1. **Milestone 1**: Project scaffold, permissions flow, lấy vị trí 1 lần hiển thị lên UI (chưa gửi server).
2. **Milestone 2**: Server Config screen + lưu profile mã hóa + Test Connection (bước 1: network check).
3. **Milestone 3**: OsmAnd protocol client + Test Connection bước 2 (gửi điểm test thật).
4. **Milestone 4**: Foreground Service gửi định kỳ + WorkManager backup.
5. **Milestone 5**: Room queue + retry logic khi mất mạng.
6. **Milestone 6**: HTTPS đầy đủ — Custom CA import + Certificate Pinning.
7. **Milestone 7**: Đa profile (test/production), polish UI, test trên nhiều dòng máy.

---

## 7. Ghi chú cho agent

- Đây là app nội bộ doanh nghiệp (dữ liệu vị trí nhân viên) — ưu tiên đúng đắn về bảo mật/permission hơn là tốc độ code.
- Nếu gặp quyết định không rõ trong spec này (ví dụ format field nào đó của OsmAnd protocol chưa khớp với bản Traccar thực tế), **dừng lại và hỏi**, không tự đoán rồi implement âm thầm.
- Viết unit test tối thiểu cho: logic queue/retry, logic build URL từ config, logic parse kết quả Test Connection.
