# Internal GPS Receiver

Web Node.js miễn phí nhận vị trí từ ứng dụng Android trên cổng `5055` và hiển thị dashboard thời gian thực.

## Endpoint

- `GET /?id=...&lat=...&lon=...&timestamp=...&speed=...&accuracy=...`: contract app hiện tại.
- `POST /api/locations`: nhận cùng các field dưới dạng JSON.
- `GET /dashboard`: giao diện thiết bị mới nhất.
- `GET /api/devices`, `/api/stats`, `/health`: API đọc và health check.

## Chạy từ source

```powershell
$env:GPS_DATA_DIR = "$PWD\gps-receiver\runtime-data"
& '.\.tools\node\node-v24.19.0-win-x64\node.exe' '.\gps-receiver\src\index.js'
```

Mở `http://localhost:5055/dashboard`. Chạy test:

```powershell
& '.\.tools\node\node-v24.19.0-win-x64\node.exe' --test 'gps-receiver\test\*.test.js'
powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "Invoke-Pester gps-receiver\test\windows-scripts.Tests.ps1"
```

## Windows Service

Mở PowerShell Administrator rồi chạy:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\gps-receiver\windows\Install-GpsReceiver.ps1
```

Service `InternalGpsReceiver` tự khởi động cùng Windows. Source được cài tại `C:\Program Files\InternalGpsReceiver`; dữ liệu và log tại `C:\ProgramData\InternalGpsReceiver`.

Chỉ dùng trong LAN Private. Không cấu hình port-forward cho cổng `5055`.
