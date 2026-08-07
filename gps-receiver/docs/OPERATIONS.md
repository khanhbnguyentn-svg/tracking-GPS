# Vận hành GPS Receiver

## Kiểm tra hằng ngày

1. Mở `http://localhost:5055/dashboard`.
2. Xác nhận service: `Get-Service InternalGpsReceiver`.
3. Chạy `gps-receiver\windows\Test-GpsReceiver.ps1`.
4. Kiểm tra dung lượng `C:\ProgramData\InternalGpsReceiver\data`.

Dữ liệu được lưu trong `locations-YYYY-MM-DD.jsonl`; mặc định giữ 30 ngày. Sao lưu bằng cách copy thư mục `data` khi service đang dừng hoặc dùng phần mềm backup có snapshot nhất quán.

## Khắc phục

- Không mở được dashboard: kiểm tra service và log trong `C:\ProgramData\InternalGpsReceiver\logs`.
- Điện thoại không gửi được: PC và điện thoại phải cùng Wi-Fi, network Windows phải là Private, IP PC phải còn là `192.168.80.146`.
- IP thay đổi: cập nhật DHCP reservation hoặc sửa file profile Android rồi nhập lại.
- Trạng thái `Gián đoạn` chỉ có nghĩa là server không nhận dữ liệu trong 5 phút, không xác nhận tai nạn.

Gỡ service bằng `Uninstall-GpsReceiver.ps1`. Dữ liệu được giữ mặc định; chỉ `-PurgeData` mới xóa vĩnh viễn.
