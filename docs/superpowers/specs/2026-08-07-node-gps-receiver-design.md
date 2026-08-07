# Thiết kế web Node.js nhận GPS trên cổng 5055

Ngày: 2026-08-07  
Trạng thái: Đã được người dùng duyệt về kiến trúc

## 1. Mục tiêu

Xây dựng một dịch vụ web Node.js chạy liên tục trên PC Windows, nhận dữ liệu vị trí từ ứng dụng Android trong cùng mạng LAN qua cổng TCP `5055`, lưu lịch sử cục bộ và cung cấp dashboard thời gian thực. Dịch vụ độc lập với Traccar/PostgreSQL và dùng hoàn toàn phần mềm miễn phí.

## 2. Giao thức nhận dữ liệu

Dịch vụ hỗ trợ đồng thời contract hiện tại của ứng dụng và JSON API:

```text
GET /?id=AND-0123456789ABCDEF&lat=10.1&lon=106.1&timestamp=1786123456&speed=3.2&accuracy=8
```

```http
POST /api/locations
Content-Type: application/json

{
  "id": "AND-0123456789ABCDEF",
  "lat": 10.1,
  "lon": 106.1,
  "timestamp": 1786123456,
  "speed": 3.2,
  "accuracy": 8
}
```

`id` phải khớp `^AND-[0-9A-F]{16}$`. Latitude nằm trong `-90..90`, longitude trong `-180..180`, timestamp là Unix seconds hoặc milliseconds hợp lệ, speed và accuracy là số không âm. Trường lạ trong JSON bị bỏ qua; trường bắt buộc thiếu hoặc sai kiểu trả `400` với mã lỗi ổn định. Body JSON bị giới hạn 16 KiB.

Response thành công là `200 application/json` với `accepted`, `deviceId` và `receivedAt`. Response không trả lại tọa độ để tránh lan truyền dữ liệu nhạy cảm không cần thiết.

## 3. Kiến trúc ứng dụng

Ứng dụng dùng Node.js LTS và các module chuẩn `http`, `fs`, `path`, `url`, `events`; không cần dependency runtime bên ngoài.

- `src/validation.js`: chuẩn hóa và validate payload, không phụ thuộc HTTP.
- `src/store.js`: append JSON Lines theo ngày, quản lý snapshot thiết bị mới nhất và thống kê.
- `src/rate-limit.js`: giới hạn theo địa chỉ IP bằng cửa sổ thời gian trong bộ nhớ.
- `src/app.js`: router HTTP và lifecycle của server.
- `src/dashboard.js`: trả HTML/CSS/JavaScript dashboard được nhúng cục bộ.
- `src/index.js`: đọc cấu hình môi trường, khởi động và shutdown có kiểm soát.

Server bind `0.0.0.0` để điện thoại trong LAN truy cập. Host, port, data directory, retention và inactivity threshold lấy từ biến môi trường với mặc định an toàn; port mặc định bắt buộc là `5055`.

## 4. Lưu trữ

Mỗi vị trí hợp lệ được ghi append-only vào `locations-YYYY-MM-DD.jsonl`. Bản ghi gồm payload đã chuẩn hóa, `receivedAt` và địa chỉ nguồn đã rút gọn/ẩn danh; không lưu header hoặc token không cần thiết.

State thiết bị mới nhất được giữ trong memory để dashboard phản hồi nhanh và ghi atomically vào `latest-devices.json` mỗi 5 giây khi có thay đổi. Khi khởi động, dịch vụ nạp snapshot; nếu snapshot hỏng, đổi tên file sang `.corrupt-<timestamp>`, khởi động với state rỗng và ghi lỗi rõ ràng.

Job retention chạy khi khởi động và mỗi 24 giờ, chỉ xóa file đúng mẫu `locations-YYYY-MM-DD.jsonl` cũ hơn số ngày cấu hình. Mặc định giữ 30 ngày. Không tự xóa file khác trong data directory.

## 5. Dashboard và API đọc

- `GET /dashboard`: bảng thiết bị, trạng thái, tọa độ mới nhất, speed, accuracy, timestamp thiết bị và thời điểm server nhận.
- `GET /api/devices`: JSON danh sách state mới nhất, sắp xếp theo lần nhận giảm dần.
- `GET /api/stats`: tổng request, accepted, rejected, số thiết bị và tốc độ nhận gần nhất.
- `GET /events`: Server-Sent Events gửi bản cập nhật đã chuẩn hóa cho dashboard.
- `GET /health`: trả `200` khi event loop và data directory ghi được; trả `503` khi persistence không sẵn sàng.

Thiết bị được coi là inactive khi không có dữ liệu mới trong 5 phút. Dashboard không gọi inactive là tai nạn. Giao diện dùng tiếng Việt, trạng thái không chỉ dựa vào màu và hỗ trợ màn hình nhỏ.

## 6. An toàn và khả năng chịu lỗi

Rate limit mặc định 120 request/phút/IP, đủ cho nhiều thiết bị thử nghiệm nhưng chặn flood rõ ràng; health/dashboard có quota riêng. Request body quá lớn trả `413`. Method không hỗ trợ trả `405`; path không tồn tại trả `404`.

Ghi JSONL được serialize qua một hàng đợi Promise để tránh xen kẽ record. Nếu ghi thất bại, request trả `503` và không cập nhật state như thể đã lưu thành công. Lỗi của một request không làm process dừng. Shutdown chờ hàng đợi ghi và snapshot hoàn tất.

Không có HTTPS trong bản mô phỏng LAN đầu tiên. Windows Firewall chỉ mở TCP `5055` cho network profile Private và subnet LAN được xác định lúc cài. Dịch vụ không được đặt sau router port-forward và tài liệu cảnh báo không dùng trực tiếp trên Internet.

## 7. Chạy liên tục trên Windows

Node.js LTS được cài từ package chính thức bằng winget. Dịch vụ dùng WinSW mã nguồn mở, có cấu hình tự khởi động, log rotation và restart khi lỗi. Data/log tách khỏi source tại `C:\ProgramData\InternalGpsReceiver`.

Script PowerShell cài đặt xác minh quyền Administrator, Node version, port conflict và network profile trước khi tạo service/firewall. Chạy lại không tạo resource trùng. Uninstall mặc định gỡ service và firewall rule nhưng giữ data; xóa data cần cờ riêng và xác nhận.

## 8. Kiểm thử

Test dùng `node:test` và temporary directory, bao phủ GET/POST hợp lệ, validation biên, body sai/quá lớn, persistence, snapshot, retention, stats, rate limit, SSE, graceful shutdown và các response `404/405/413/429/503`.

Smoke test sau cài đặt gửi một GET từ localhost, một POST JSON, đọc dashboard/API và kiểm tra service tự chạy lại sau restart process. Nghiệm thu LAN dùng app Android thật trỏ tới IPv4 của PC và xác nhận record xuất hiện trên dashboard.

## 9. Tiêu chí hoàn thành

Repository chứa source, test, Postman collection, script cài/gỡ Windows Service, hướng dẫn vận hành và cấu hình Android mẫu. Hoàn thành khi tất cả test Node vượt qua, service Windows ở trạng thái Running, firewall chỉ mở cho LAN Private, `/health` trả `200`, GET từ app và POST JSON đều được lưu/hiển thị, và service tự khởi động lại sau reboot hoặc process lỗi.
