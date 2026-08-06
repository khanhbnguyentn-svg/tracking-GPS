# Thiết kế ứng dụng Android gửi vị trí đến Traccar

Ngày: 2026-08-06  
Trạng thái: Đã được người dùng duyệt

## 1. Mục tiêu

Xây dựng một ứng dụng Android nội bộ bằng Kotlin để 100–350 điện thoại gửi vị trí định kỳ đến Traccar Server qua giao thức OsmAnd HTTP. Người dùng không cần kiến thức kỹ thuật để cấu hình, chẩn đoán kết nối, cấp quyền và theo dõi trạng thái hoạt động.

Ứng dụng chỉ hỗ trợ Android 14 trở lên (`minSdk = 34`). Traccar Server, cảnh báo mất kết nối và quy trình vận hành được bàn giao cho IT.

## 2. Phạm vi

### Ứng dụng Android

- Cấu hình nhiều profile server ngay trên điện thoại.
- Cấu hình host/IP, port, HTTP/HTTPS, chu kỳ gửi và chế độ chứng chỉ.
- Xuất file JSON mẫu và nhập file JSON dùng chung để cấu hình nhanh nhiều điện thoại.
- Tự tạo Device ID ổn định trên từng điện thoại; người dùng không phải nhập thủ công.
- Hướng dẫn cấp quyền vị trí, vị trí nền, thông báo và loại trừ tối ưu pin.
- Lấy vị trí bằng Fused Location Provider trong foreground service loại `location`.
- Lưu từng vị trí vào Room trước khi gửi.
- Gửi OsmAnd, xóa khỏi queue chỉ sau khi server xác nhận thành công.
- Retry bằng WorkManager với network constraint và exponential backoff.
- Test Connection tách kiểm tra mạng và gửi dữ liệu thử.
- Hiển thị trạng thái tracking, lần GPS cuối, lần gửi cuối và số điểm đang chờ.
- Hỗ trợ System CA, CA nội bộ được import và certificate pinning; không có trust-all TLS.
- Giao diện tiếng Việt mặc định.

### Server và IT

- Triển khai Traccar, PostgreSQL, reverse proxy HTTPS, firewall, backup và giám sát.
- Quản lý Device ID duy nhất, nhóm thiết bị và PIC phụ trách.
- Cảnh báo khi không nhận dữ liệu 5 phút, nhắc/escalate sau 10 phút và báo phục hồi.
- Cho phép tạm ngừng cảnh báo theo thiết bị với thời điểm hết hạn và lý do.
- Lưu lịch sử người thao tác và tự bật lại cảnh báo khi hết hạn.

Không xây dashboard hoặc dịch vụ cảnh báo riêng trong giai đoạn đầu. IT dùng khả năng sẵn có của Traccar và quy trình vận hành; chỉ phát triển bổ sung khi triển khai thực tế chứng minh có khoảng trống.

## 3. Kiến trúc Android

Ứng dụng gồm một module `app` và các package theo trách nhiệm, không dùng kiến trúc nhiều module.

- `ui`: màn hình trạng thái, profile, chẩn đoán và hướng dẫn quyền.
- `location`: foreground service và bộ chuyển Location thành bản ghi queue.
- `data`: Room cho profile và hàng đợi; kho cấu hình mã hóa cho dữ liệu nhạy cảm.
- `network`: tạo request OsmAnd, cấu hình TLS và phân loại lỗi kết nối.
- `worker`: gửi lại queue khi có mạng.

Jetpack Compose cung cấp UI. ViewModel giữ trạng thái màn hình. Repository là ranh giới dùng chung cho Room, cấu hình và networking. Dùng manual dependency injection vì ứng dụng chỉ có một module và số dependency nhỏ.

## 4. Luồng quyền và khôi phục

Khi người dùng bấm **Bắt đầu theo dõi**, ứng dụng kiểm tra theo thứ tự:

1. Dịch vụ vị trí của điện thoại đã bật.
2. `ACCESS_FINE_LOCATION` đã được cấp.
3. `ACCESS_BACKGROUND_LOCATION` đã được cấp.
4. `POST_NOTIFICATIONS` đã được cấp.
5. Ứng dụng đã được loại khỏi Battery Optimization khi người dùng đồng ý.

Nếu có thể xin quyền trực tiếp, app hiển thị giải thích ngắn trước system prompt. Nếu quyền bị từ chối vĩnh viễn, app mở trang App Settings. Nếu GPS tắt, app mở Location Settings. Khi người dùng quay lại, app tự kiểm tra lại toàn bộ trạng thái.

Foreground service chỉ được khởi động từ màn hình đang hiển thị sau thao tác người dùng. WorkManager không cố khởi động lại location foreground service từ background. Nếu service bị hệ điều hành hoặc hãng máy dừng, app hiển thị trạng thái đã dừng, mở App/Battery Settings khi cần và cho phép người dùng khởi động lại.

## 5. Thu thập, lưu và gửi vị trí

Foreground service dùng `PRIORITY_BALANCED_POWER_ACCURACY`; chu kỳ mặc định 60 giây và có thể chỉnh trên điện thoại. Notification cố định cho biết tracking đang hoạt động và có action dừng.

Mỗi location được ghi vào Room trước khi gửi. Queue có giới hạn 10.000 bản ghi; khi vượt giới hạn, bản ghi cũ nhất bị xóa và sự kiện này được ghi nhận trong trạng thái chẩn đoán. Queue được gửi theo thứ tự thời gian. WorkManager chỉ chịu trách nhiệm upload lại dữ liệu, chạy khi có mạng và dùng exponential backoff.

Request OsmAnd có dạng:

```text
{scheme}://{host}:{port}/?id={deviceId}&lat={lat}&lon={lon}&timestamp={unixTime}&speed={speed}&accuracy={accuracy}
```

Các tham số được mã hóa bằng URL builder của OkHttp. Không log URL hoàn chỉnh, Device ID hoặc dữ liệu vị trí trong release build.

## 6. Profile, file cấu hình và bảo mật kết nối

Mỗi profile có tên, host/IP, port, scheme, chu kỳ gửi và TLS mode. Chỉ một profile active tại một thời điểm. Không cho đổi profile active trong lúc tracking; người dùng phải dừng tracking trước.

App có nút **Tải file cấu hình mẫu** và **Nhập file cấu hình** dùng Android system file picker. File dùng JSON có trường `version` để kiểm soát tương thích, không dùng text tự do. Một file có thể dùng chung cho toàn bộ điện thoại và không chứa Device ID. Khi import, app validate toàn bộ dữ liệu, hiển thị bản xem trước rồi mới cho lưu; không tự ghi đè profile hiện tại. Custom CA `.crt` được import riêng, không nhúng vào JSON.

```json
{
  "version": 1,
  "name": "Production",
  "host": "traccar.internal.company.com",
  "port": 443,
  "scheme": "https",
  "intervalSeconds": 60,
  "tlsMode": "system"
}
```

`tlsMode` nhận `system`, `customCa` hoặc `pinning`. Với `pinning`, file có thêm `certificatePin`; với `customCa`, người dùng phải chọn file `.crt` sau khi import. Các trường lạ hoặc version chưa hỗ trợ bị từ chối để tránh áp dụng nhầm cấu hình.

Device ID được app tự lấy từ `Settings.Secure.ANDROID_ID` và chuẩn hóa thành `AND-<16 ký tự hex>`. Cơ chế này không cần quyền đọc số điện thoại, ổn định trên cùng thiết bị/người dùng/signing key và có thể thay đổi sau factory reset hoặc thay signing key. App hiển thị Device ID với nút sao chép để PIC đối chiếu. Nếu hệ thống không trả về Android ID hợp lệ, app tạo UUID một lần và lưu trong vùng cấu hình mã hóa.

- HTTP chỉ dùng cho mạng nội bộ/VPN và cần cảnh báo rõ trên UI. Vì Android Network Security Config không hỗ trợ whitelist động hostname do người dùng nhập, manifest phải cho phép cleartext ở cấp ứng dụng để hỗ trợ profile HTTP tùy chỉnh. Ứng dụng không có endpoint HTTP nào khác và networking chỉ gửi đến profile active; HTTPS vẫn là chế độ bắt buộc khuyến nghị cho production.
- System CA dùng trust store mặc định của Android.
- Custom CA import file chứng chỉ bằng system file picker, lưu trong vùng riêng của app và chỉ áp dụng cho client/profile tương ứng.
- Certificate pinning nhận pin SHA-256 dạng chuẩn `sha256/...` và chỉ áp dụng cho hostname của profile.

Không triển khai hostname verifier hoặc trust manager bỏ qua xác minh. App phải validate host, port, interval, certificate input và Device ID tự sinh trước khi lưu profile.

## 7. Chẩn đoán kết nối

Màn hình Chẩn đoán hoạt động độc lập với tracking:

1. Kiểm tra cấu hình và DNS/TCP/TLS bằng HTTP request ngắn đến endpoint đã cấu hình.
2. Nếu bước một thành công, cho phép gửi một điểm GPS thật gần nhất. Không tự gửi tọa độ `0,0` vì có thể tạo dữ liệu giả trên hệ thống vận hành.

Kết quả phân biệt DNS, connection refused, timeout, TLS/certificate, HTTP error và thành công. Màn hình hiển thị thời gian kiểm tra nhưng không lộ thông tin nhạy cảm.

## 8. Cảnh báo server cho 100–350 thiết bị

Mỗi điện thoại có Device ID duy nhất và được liên kết với nhóm/PIC trong Traccar. IT bật tạo status event và cấu hình notification theo nhóm.

Để không phải tạo thủ công 100–350 Device ID, IT bật `database.registerUnknown` với `database.registerUnknown.regex` chỉ nhận mẫu `^AND-[0-9a-f]{16}$` và `database.registerUnknown.defaultGroupId` trỏ đến nhóm **Chờ xác nhận**. PIC đối chiếu Device ID hiển thị trên điện thoại, đổi tên thiết bị, gán nhóm vận hành và PIC trước khi bật cảnh báo. Auto-register chỉ là hỗ trợ onboarding, không xác thực danh tính; endpoint vẫn phải giới hạn trong mạng nội bộ/VPN và nhóm chờ xác nhận không được tham gia cảnh báo vận hành.

- Không có dữ liệu mới trong 5 phút: cảnh báo **Mất kết nối**.
- Sau 10 phút: nhắc lại hoặc escalate đến danh sách liên hệ tiếp theo.
- Khi dữ liệu trở lại: thông báo **Đã kết nối lại**.
- Email là kênh mặc định; IT có thể cấu hình SMS, Telegram hoặc WhatsApp.
- Dashboard hiển thị trạng thái, thời điểm/vị trí cuối và PIC.

Mất kết nối chỉ là tín hiệu yêu cầu PIC kiểm tra, không phải xác nhận tai nạn.

Tạm ngừng cảnh báo yêu cầu thiết bị, thời điểm bắt đầu/kết thúc, lý do và người thao tác. Preset gồm 30 phút, 1 giờ, 4 giờ và 1 ngày. Hết hạn phải tự bật lại; không cho tạm ngừng vô thời hạn theo mặc định. Việc tạm ngừng chỉ chặn notification, không ngăn lưu vị trí nếu thiết bị vẫn gửi.

## 9. UI

Ứng dụng có ba destination chính:

- **Trạng thái:** công tắc bắt đầu/dừng, profile active, GPS cuối, gửi cuối và queue count.
- **Cấu hình:** danh sách profile, form chỉnh sửa, tải file mẫu và nhập file JSON.
- **Chẩn đoán:** hai bước kiểm tra, kết quả cụ thể và thao tác mở Settings khi cần.

Các nút dùng icon quen thuộc kèm nhãn rõ ràng. Trạng thái lỗi không chỉ dựa vào màu. Nội dung dài phải co giãn trên màn hình nhỏ và hỗ trợ font scaling.

## 10. Xử lý lỗi

- Thiếu quyền hoặc GPS tắt: không start service, hiển thị đúng hành động khắc phục.
- Network/TLS/HTTP thất bại: giữ queue và lên lịch retry.
- Profile invalid: chặn lưu và chỉ rõ trường lỗi.
- Database đầy giới hạn: xóa cũ nhất, báo số điểm bị bỏ.
- Service bị dừng: phản ánh trạng thái thực, không tuyên bố tracking vẫn chạy.
- Certificate import/pin lỗi: từ chối cấu hình, không fallback sang kết nối không an toàn.

## 11. Kiểm thử và CI

Unit test tập trung vào:

- Tạo URL OsmAnd và encode tham số.
- FIFO, giới hạn 10.000 điểm và chỉ xóa sau thành công.
- Phân loại kết quả Test Connection.
- Validate profile và certificate pin.
- Parse, validate và round-trip file cấu hình JSON.
- Tạo Device ID đúng định dạng và dùng fallback khi Android ID không hợp lệ.
- Retry/backoff ở mức worker/repository.

GitHub Actions chạy Gradle unit tests, lint và build debug APK trên mỗi pull request/push. APK debug được upload làm workflow artifact.

Kiểm thử thiết bị thật chỉ yêu cầu Android 14, 15 và 16. Checklist bao gồm cấp/từ chối quyền, khóa màn hình, swipe app, mất/khôi phục mạng, HTTP/HTTPS, certificate sai, queue replay và battery optimization. CI không thể thay thế kiểm thử foreground location trên điện thoại thật.

## 12. Bàn giao

Repository chứa README dành cho người không chuyên, lệnh build cục bộ, hướng dẫn lấy APK từ GitHub Actions và checklist cài trên điện thoại. Tài liệu IT hiện có được cập nhật để khớp với endpoint, TLS, PostgreSQL, quy mô 350 thiết bị, cảnh báo 5/10 phút và tạm ngừng cảnh báo.

Việc triển khai chia thành commit nhỏ nhưng bàn giao cuối cùng là ứng dụng đầy đủ, không dừng ở bản thử kết nối.
