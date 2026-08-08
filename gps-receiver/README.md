# Internal GPS Receiver

Web Node.js miễn phí nhận vị trí từ ứng dụng Android trên cổng `5055` và hiển thị dashboard thời gian thực.

## Endpoint

- `GET /?id=...&lat=...&lon=...&timestamp=...&speed=...&accuracy=...`: contract app hiện tại.
- `POST /api/locations`: nhận cùng các field dưới dạng JSON.
- `GET /dashboard`: giao diện thiết bị mới nhất.
- `GET /api/devices`, `/api/stats`, `/health`: API đọc và health check.

## Chạy từ source

```powershell
$env:GPS_DATABASE_URL = 'postgres://fleet_app:MAT_KHAU@127.0.0.1:5432/fleet_tracking'
$env:GPS_SESSION_SECRET = 'CHUOI_NGAU_NHIEN_TOI_THIEU_32_KY_TU'
npm.cmd --prefix .\gps-receiver start
```

Server tự chạy migration trước khi mở cổng. Mở `http://localhost:5055/login`. Chạy test:

```powershell
& '.\.tools\node\node-v24.19.0-win-x64\node.exe' --test 'gps-receiver\test\*.test.js'
powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "Invoke-Pester gps-receiver\test\windows-scripts.Tests.ps1"
```

## Windows Service

Mở PowerShell Administrator rồi chạy:

```powershell
Set-ExecutionPolicy -Scope Process Bypass -Force
.\gps-receiver\windows\Install-FleetDatabase.ps1 -RootPath 'D:\InternalGPS'
.\gps-receiver\windows\Install-GpsReceiver.ps1 -RootPath 'D:\InternalGPS'
```

Hai lệnh trên mặc định dùng `-RootPath 'D:\InternalGPS'`; nên chạy `-WhatIf` trước khi cài thật. Service `InternalGpsReceiver` tự khởi động cùng Windows. Chương trình nằm tại `D:\InternalGPS\Receiver`; database, dữ liệu, log và backup nằm trong các thư mục con cùng root.

Chỉ dùng trong LAN nội bộ. Firewall giới hạn cổng `5055` cho `LocalSubnet`; không cấu hình port-forward cho cổng này.

Hướng dẫn đầy đủ cho IT: `docs\OPERATIONS.md`. Database chỉ lắng nghe loopback và secret không nằm trong XML/repository.

## Pilot Internet hai ngày

Pilot một điện thoại dùng Cloudflare Quick Tunnel và token tạm thời; đây không phải production. Không mở port router hoặc PostgreSQL. IT dùng `windows\QuickTunnelPilot.ps1` với `-Action Start`, `Status`, rồi `Stop`; lệnh Stop thu hồi token dùng chung. Xem `docs\OPERATIONS.md` để triển khai và `docs\ANDROID.md` để import `D:\InternalGPS\Pilot\tracking-pilot-profile.json` cùng checklist Wi-Fi/mobile-data/offline queue.
