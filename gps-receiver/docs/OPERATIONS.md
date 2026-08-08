# Vận hành Internal GPS Receiver

Tất cả lệnh cài đặt trong tài liệu này phải chạy bằng **Windows PowerShell - Run as administrator** tại thư mục gốc của repository.

## Cài đặt lần đầu

Yêu cầu: ổ `D:` là ổ cố định định dạng NTFS và còn ít nhất 20 GB. Installer sử dụng các thư mục sau:

```text
D:\InternalGPS\PostgreSQL
D:\InternalGPS\PostgreSQLData
D:\InternalGPS\Receiver
D:\InternalGPS\ReceiverData
D:\InternalGPS\Backup
```

Kiểm tra trước, không thay đổi hệ thống:

```powershell
Set-ExecutionPolicy -Scope Process Bypass -Force
.\gps-receiver\windows\Install-FleetDatabase.ps1 -RootPath 'D:\InternalGPS' -WhatIf
.\gps-receiver\windows\Install-GpsReceiver.ps1 -RootPath 'D:\InternalGPS' -WhatIf
```

Cài PostgreSQL/PostGIS, database `fleet_tracking`, rồi cài service:

```powershell
.\gps-receiver\windows\Install-FleetDatabase.ps1 -RootPath 'D:\InternalGPS'
.\gps-receiver\windows\Install-GpsReceiver.ps1 -RootPath 'D:\InternalGPS'
```

Database chỉ lắng nghe tại `127.0.0.1:5432`. Installer không tạo firewall rule cho cổng 5432. Receiver chỉ cho phép TCP `5055` từ `LocalSubnet`, kể cả khi Windows phân loại mạng là Public. File cấu hình và secret nằm ngoài repository tại `D:\InternalGPS\ReceiverData`, được bảo vệ bằng ACL và DPAPI.

Tạo tài khoản quản trị sau khi cài đặt. Nhập mật khẩu tối thiểu 12 ký tự qua stdin, không đặt mật khẩu trong câu lệnh:

```powershell
$secure = Read-Host 'Nhap mat khau admin (toi thieu 12 ky tu)' -AsSecureString
$pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
try {
    $plain = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    $plain | & 'D:\InternalGPS\Receiver\Start-GpsReceiver.ps1' `
        -EnvironmentFile 'D:\InternalGPS\ReceiverData\config\receiver.env' `
        -CreateUser -Username admin -Role admin
} finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
    $plain = $null
    $secure.Dispose()
}
```

Không lưu mật khẩu vào `.ps1`, `.env`, lịch sử terminal hoặc tài liệu bàn giao. Khi chủ động thay mật khẩu/vai trò của user đã tồn tại, chạy lại cùng lệnh với switch `-Update`.

## Kiểm tra hàng ngày

```powershell
Get-Service InternalGpsReceiver, InternalTraccar-PostgreSQL
Get-NetTCPConnection -LocalPort 5055,5432 -State Listen | Select-Object LocalAddress,LocalPort,OwningProcess
Get-CimInstance Win32_Process -Filter "Name='node.exe'" | Select-Object ProcessId,CommandLine
.\gps-receiver\windows\Test-GpsReceiver.ps1
```

Mở `http://localhost:5055/dashboard`, đăng nhập và kiểm tra thiết bị mới nhất. Trạng thái `Gián đoạn` chỉ có nghĩa server không nhận dữ liệu trong thời gian cấu hình; đây không phải xác nhận tai nạn.

Trên điện thoại cùng mạng LAN, URL receiver có dạng `http://<IP-WINDOWS>:5055`. Thay `<IP-WINDOWS>` bằng địa chỉ IPv4 của máy server, ví dụ `http://192.168.80.146:5055`; không dùng `localhost` trên điện thoại.

## Quick Tunnel thử nghiệm Internet hai ngày

Quick Tunnel chỉ dành cho pilot một điện thoại trong hai ngày, **không phải production**. Không mở port router, không nới firewall và không thay đổi PostgreSQL đang chỉ bind `127.0.0.1:5432`. Giữ PC bật, ngăn Windows sleep và duy trì kết nối Internet trong suốt pilot.

Mở Windows PowerShell **Run as administrator** tại thư mục gốc repository, cài `cloudflared`, rồi chạy pilot:

```powershell
winget install --id Cloudflare.cloudflared --exact
.\gps-receiver\windows\QuickTunnelPilot.ps1 -Action Start -RootPath 'D:\InternalGPS'
.\gps-receiver\windows\QuickTunnelPilot.ps1 -Action Status -RootPath 'D:\InternalGPS'
```

Start kiểm tra chữ ký Authenticode của `cloudflared.exe`, tạo token mới được bảo vệ bằng DPAPI, khởi động tunnel HTTPS `*.trycloudflare.com` và ghi profile import tại `D:\InternalGPS\Pilot\tracking-pilot-profile.json`. Không ghi token hoặc nội dung profile vào log hay tài liệu bàn giao.

Khi tracking đang dừng, bàn giao file profile cho chủ điện thoại để import và chọn profile `Internet pilot 2 ngay`. Sau khi xác nhận import thành công, xóa file plaintext khỏi Windows. Chủ điện thoại thực hiện checklist trong `docs\ANDROID.md`; IT theo dõi bằng lệnh Status và ghi lại mọi gián đoạn.

Kết thúc pilot sau hai ngày:

```powershell
.\gps-receiver\windows\QuickTunnelPilot.ps1 -Action Stop -RootPath 'D:\InternalGPS'
```

Stop thu hồi token dùng chung bằng cách xóa cấu hình secret khỏi receiver, xóa token DPAPI, profile plaintext và state, rồi khởi động lại receiver. Xác nhận PID tunnel đã dừng, URL public không còn hoạt động, `GPS_INGEST_TOKEN_SECRET_FILE` không còn trong `receiver.env`, `/health` LAN trả `200`, service receiver vẫn `Running/Automatic`; giữ `D:\InternalGPS\Pilot\cloudflared.log` để IT rà soát.

## Import dữ liệu JSONL cũ

Không chạy trực tiếp trên thư mục dữ liệu duy nhất. Sao chép các file `locations-*.jsonl` sang một thư mục thử nghiệm trước.

```powershell
Stop-Service InternalGpsReceiver
New-Item -ItemType Directory -Force 'D:\InternalGPS\Backup\GPS-Import-Copy' | Out-Null
Copy-Item 'D:\InternalGPS\ReceiverData\data\locations-*.jsonl' 'D:\InternalGPS\Backup\GPS-Import-Copy' -Force
Set-Location 'D:\InternalGPS\Receiver\app'
& '..\node\node.exe' '.\scripts\import-jsonl.js' --source 'D:\InternalGPS\Backup\GPS-Import-Copy'
& '..\node\node.exe' '.\scripts\import-jsonl.js' --source 'D:\InternalGPS\Backup\GPS-Import-Copy' --apply
Start-Service InternalGpsReceiver
```

Lệnh đầu là dry-run. Chỉ lệnh có `--apply` mới ghi database. Import có thể chạy lại và không xóa file nguồn.

## Cutover và rollback

Trước cutover, ghi lại PID và sao lưu JSONL:

```powershell
Get-CimInstance Win32_Process -Filter "Name='node.exe'" | Select-Object ProcessId,CommandLine
Stop-Service InternalGpsReceiver
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
Copy-Item 'D:\InternalGPS\ReceiverData\data' "D:\InternalGPS\Backup\Before-Cutover-$stamp" -Recurse
```

Chạy migration, dry-run/import, sau đó khởi động service mới và kiểm tra `/health`, dashboard và một điểm GPS thử. Nếu bất kỳ bước nào thất bại:

```powershell
Stop-Service InternalGpsReceiver -ErrorAction SilentlyContinue
```

Không xóa `D:\InternalGPS\ReceiverData\data` hoặc bản sao lưu. Khởi động lại phiên bản JSONL cũ từ thư mục cài đặt đã lưu trước cutover bằng đúng lệnh Node/PID đã ghi nhận. PostgreSQL và dữ liệu import được giữ nguyên để IT phân tích; không cần xóa để rollback luồng nhận GPS.

## Gỡ cài đặt

```powershell
.\gps-receiver\windows\Uninstall-GpsReceiver.ps1 -RootPath 'D:\InternalGPS'
```

Mặc định lệnh trên giữ database, bản đồ, lịch sử GPS, log, secret và `Backup`. Chỉ dùng lệnh sau khi đã sao lưu và thực sự muốn xóa dữ liệu receiver:

```powershell
.\gps-receiver\windows\Uninstall-GpsReceiver.ps1 -RootPath 'D:\InternalGPS' -PurgeData
```

`-PurgeData` vẫn không gỡ PostgreSQL, không xóa `fleet_tracking` và không xóa `D:\InternalGPS\Backup`.
