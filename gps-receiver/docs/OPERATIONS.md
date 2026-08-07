# Vận hành Internal GPS Receiver

Tất cả lệnh cài đặt trong tài liệu này phải chạy bằng **Windows PowerShell - Run as administrator** tại thư mục gốc của repository.

## Cài đặt lần đầu

Kiểm tra trước, không thay đổi hệ thống:

```powershell
Set-ExecutionPolicy -Scope Process Bypass -Force
.\gps-receiver\windows\Install-FleetDatabase.ps1 -WhatIf
.\gps-receiver\windows\Install-GpsReceiver.ps1 -WhatIf
```

Cài PostgreSQL/PostGIS, database `fleet_tracking`, rồi cài service:

```powershell
.\gps-receiver\windows\Install-FleetDatabase.ps1
.\gps-receiver\windows\Install-GpsReceiver.ps1
```

Database chỉ lắng nghe tại `127.0.0.1:5432`. Installer không tạo firewall rule cho cổng 5432. File cấu hình và secret nằm ngoài repository tại `C:\ProgramData\InternalGpsReceiver`, được bảo vệ bằng ACL và DPAPI.

Tạo tài khoản quản trị sau khi cài đặt. Nhập mật khẩu tối thiểu 12 ký tự qua stdin, không đặt mật khẩu trong câu lệnh:

```powershell
$env:GPS_ENV_FILE='C:\ProgramData\InternalGpsReceiver\config\receiver.env'
$secure = Read-Host 'Nhap mat khau admin (toi thieu 12 ky tu)' -AsSecureString
$pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
try {
    $plain = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    $plain | & 'C:\Program Files\InternalGpsReceiver\Start-GpsReceiver.ps1' -CreateUser -Username admin -Role admin
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

## Import dữ liệu JSONL cũ

Không chạy trực tiếp trên thư mục dữ liệu duy nhất. Sao chép các file `locations-*.jsonl` sang một thư mục thử nghiệm trước.

```powershell
Stop-Service InternalGpsReceiver
Copy-Item 'C:\ProgramData\InternalGpsReceiver\data\locations-*.jsonl' 'D:\GPS-Import-Copy' -Force
Set-Location 'C:\Program Files\InternalGpsReceiver\app'
& '..\node\node.exe' '.\scripts\import-jsonl.js' --source 'D:\GPS-Import-Copy'
& '..\node\node.exe' '.\scripts\import-jsonl.js' --source 'D:\GPS-Import-Copy' --apply
Start-Service InternalGpsReceiver
```

Lệnh đầu là dry-run. Chỉ lệnh có `--apply` mới ghi database. Import có thể chạy lại và không xóa file nguồn.

## Cutover và rollback

Trước cutover, ghi lại PID và sao lưu JSONL:

```powershell
Get-CimInstance Win32_Process -Filter "Name='node.exe'" | Select-Object ProcessId,CommandLine
Stop-Service InternalGpsReceiver
Copy-Item 'C:\ProgramData\InternalGpsReceiver\data' 'D:\GPS-Backup-Before-Cutover' -Recurse
```

Chạy migration, dry-run/import, sau đó khởi động service mới và kiểm tra `/health`, dashboard và một điểm GPS thử. Nếu bất kỳ bước nào thất bại:

```powershell
Stop-Service InternalGpsReceiver -ErrorAction SilentlyContinue
```

Không xóa `C:\ProgramData\InternalGpsReceiver\data` hoặc bản sao lưu. Khởi động lại phiên bản JSONL cũ từ thư mục cài đặt đã lưu trước cutover bằng đúng lệnh Node/PID đã ghi nhận. PostgreSQL và dữ liệu import được giữ nguyên để IT phân tích; không cần xóa để rollback luồng nhận GPS.

## Gỡ cài đặt

```powershell
.\gps-receiver\windows\Uninstall-GpsReceiver.ps1
```

Mặc định lệnh trên giữ database, bản đồ, lịch sử GPS, log và secret. Chỉ dùng `-PurgeData` sau khi đã sao lưu và xác nhận đúng đường dẫn; switch này không gỡ PostgreSQL hay xóa `fleet_tracking`.
