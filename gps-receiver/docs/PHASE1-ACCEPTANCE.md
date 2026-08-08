# Phase 1 Acceptance Record

Thời điểm kiểm tra: `2026-08-08 06:58:15 +07:00`  
Môi trường: Windows development machine, Node.js `24.19.0`, PostgreSQL `17.10`, PostGIS `3.6.2` trên `127.0.0.1:55432`.

## Đã đạt

- Node test: `59/59` đạt ở checkpoint cuối.
- Pester: `8/8` đạt trên Windows PowerShell 5.1/Pester 3.4.0.
- PowerShell parser: tất cả script trong `gps-receiver/windows` hợp lệ.
- `Install-FleetDatabase.ps1 -WhatIf` và `Install-GpsReceiver.ps1 -WhatIf` không tạo tài nguyên.
- Startup lifecycle: migration chạy trước listen; migration lỗi không mở cổng; SIGINT/SIGTERM chỉ shutdown một lần; HTTP dừng trước khi pool đóng; timeout 15 giây.
- Load smoke 10 phút, 7 request/giây, 100 Device ID kiểm soát:
  - gửi: `4200`
  - lưu mới: `4200`
  - mất sau HTTP 200: `0`
  - p95: `22.97 ms`
  - RSS đầu/cuối: `51,257,344 / 74,706,944 bytes`
  - không có lỗi HTTP hoặc cạn connection pool.

## Chưa thực hiện trên máy dev

- Chưa import JSONL production: workspace không có file GPS thực hoặc hai Device ID LAN thật. Integration test đã chứng minh dry-run/apply, idempotency và loại trừ đúng ba ID smoke-test, nhưng không thay thế rehearsal với bản sao dữ liệu thật.
- Chưa thực hiện live cutover: cần IT chạy bằng Administrator trên server Windows đích, ghi PID cũ/mới, backup JSONL, import, smoke GET/POST và diễn tập rollback.
- Chưa ghi vị trí backup production, PID service và kết quả rollback vì các giá trị này chỉ tồn tại khi cutover thật.

## Điều kiện để IT ký Phase 1

Thực hiện tuần tự các mục `Cài đặt lần đầu`, `Import dữ liệu JSONL cũ`, và `Cutover và rollback` trong `OPERATIONS.md`. Không ký hoàn tất nếu số dòng import không khớp, ba ID smoke-test xuất hiện, `/health` không trả healthy, hoặc receiver JSONL cũ không khởi động lại được.
