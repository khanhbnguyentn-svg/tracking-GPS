# Thiết kế server GPS Windows trên ổ D

## Mục tiêu

Cài Internal GPS Receiver, PostgreSQL 17 và PostGIS trên máy Windows bằng công cụ miễn phí/mã nguồn mở. Toàn bộ chương trình, dữ liệu, cấu hình và backup do dự án quản lý nằm dưới một thư mục gốc `D:\InternalGPS`. Điện thoại GPS hiện có tiếp tục gửi dữ liệu bằng contract OsmAnd trên cổng `5055`.

## Bố cục thư mục

```text
D:\InternalGPS\
|-- PostgreSQL\
|-- PostgreSQLData\
|-- Receiver\
|-- ReceiverData\
`-- Backup\
```

- `PostgreSQL`: binary PostgreSQL/PostGIS.
- `PostgreSQLData`: database cluster và secret quản trị database.
- `Receiver`: Node.js, source đã đóng gói, dependency và WinSW.
- `ReceiverData`: file môi trường không chứa mật khẩu rõ, secret DPAPI, log và dữ liệu JSONL cũ nếu có.
- `Backup`: vị trí mặc định cho backup do IT chủ động tạo; installer không tự xóa nội dung thư mục này.

## Thành phần và kết nối

- Windows Service `InternalTraccar-PostgreSQL` chạy PostgreSQL tại `127.0.0.1:5432`; không tạo firewall rule cho cổng này.
- Windows Service `InternalGpsReceiver` lắng nghe `0.0.0.0:5055` và firewall chỉ cho profile `Private`, phạm vi `LocalSubnet`.
- Receiver chạy migration trước khi mở cổng HTTP và kết nối database bằng role giới hạn `fleet_app`.
- Node.js, PostgreSQL, PostGIS và WinSW dùng đúng phiên bản/hash đã khóa trong repository; installer không yêu cầu phần mềm trả phí.

## Cài đặt và cập nhật

Installer nhận `-RootPath`, mặc định `D:\InternalGPS`, rồi suy ra toàn bộ thư mục con. `-WhatIf` phải liệt kê thay đổi mà không tạo file, service hay firewall rule. Trước khi cài thật, script kiểm tra:

- ổ `D:` tồn tại và dùng NTFS;
- thư mục đích nằm trực tiếp dưới root đã chuẩn hóa;
- còn ít nhất 20 GB trống;
- PowerShell đang chạy bằng Administrator;
- artifact tồn tại và đúng SHA-256;
- cổng `5055` không bị tiến trình khác chiếm.

Chạy lại installer phải idempotent: giữ database, secret, lịch sử GPS và backup; chỉ cập nhật binary/application sau khi migration thành công.

## Bảo mật và quyền truy cập

- `ReceiverData` chỉ cấp quyền cần thiết cho `LOCAL SERVICE`, `Administrators` và `SYSTEM`.
- `PostgreSQLData` chỉ cấp quyền cần thiết cho tài khoản service PostgreSQL, `Administrators` và `SYSTEM`.
- Mật khẩu database và session được mã hóa DPAPI `LocalMachine`; không nằm trong XML, command line, Git hoặc log.
- Dashboard quản trị cần đăng nhập; ingestion GPS vẫn không yêu cầu tài khoản để giữ tương thích điện thoại.
- Không cấu hình port forwarding Internet cho `5055`.

## Gỡ cài đặt và chống xóa nhầm

Mặc định uninstall chỉ gỡ service, firewall rule và binary receiver; giữ `PostgreSQLData`, `ReceiverData` và `Backup`. `-PurgeData` chỉ được thực hiện khi đồng thời có `-RootPath D:\InternalGPS`, đường dẫn sau chuẩn hóa khớp chính xác root, và người vận hành xác nhận `ShouldProcess`. Script từ chối root ổ đĩa, thư mục cha, junction/reparse point hoặc đường dẫn ngoài `D:\InternalGPS`.

Uninstall receiver không tự gỡ PostgreSQL và không xóa database `fleet_tracking`. Việc xóa database là quy trình IT riêng sau khi đã backup.

## Kiểm thử và tiêu chí đạt

- Pester chứng minh đường dẫn mặc định trên D, kiểm tra NTFS/dung lượng, ACL, `-WhatIf`, idempotency và chốt purge.
- PowerShell parser báo không có lỗi cho toàn bộ script Windows.
- Node test và migration integration tiếp tục đạt.
- Diễn tập test-owned path dưới `D:\InternalGPS-Test-*` không thay đổi dữ liệu thật.
- Cài production chỉ được ghi nhận hoàn tất sau khi IT kiểm tra service, `/health`, login, một GET GPS, một POST GPS và backup/rollback.

## Ngoài phạm vi

- Không cài Traccar đầy đủ, Docker, phần mềm thương mại hoặc dịch vụ cloud.
- Không thay đổi ứng dụng GPS Android đã cài.
- Không thực hiện live cutover hay xóa dữ liệu production tự động.
