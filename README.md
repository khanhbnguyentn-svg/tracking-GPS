# GPS Email Pilot

Ứng dụng nội bộ cho Android 10+, theo dõi GPS bằng foreground service và gửi báo cáo tổng hợp qua Gmail SMTP.

- Quan sát vị trí khoảng mỗi 10 giây khi tracking bật.
- Lưu `START`, `PERIODIC` mỗi 2 phút khi xe chạy, `TEMP_STOP` khi bắt đầu dừng và nâng cấp thành `STOP` nếu đứng yên ít nhất 2 phút.
- Gửi một CSV tổng hợp các record đã hoàn tất nhưng chưa gửi theo chu kỳ 6h/12h/24h.
- Tự động xóa record cũ hơn một năm khi chạy báo cáo.
- Activity Recognition là tín hiệu hỗ trợ tùy chọn; nếu không cấp quyền, app tiếp tục dùng GPS.

## Build tren Windows

Yeu cau: JDK 17, Android SDK Platform 36 va Build Tools 36.0.0.

1. Tao `gmail-secrets.properties` tai thu muc goc tu mau `docs/gmail-build-secrets.example.properties`.
2. Dien Gmail gui rieng va App Password 16 ky tu. Khong commit file nay.
3. Chay:

```powershell
./gradlew.bat clean testDebugUnitTest lintDebug assembleDebug --no-daemon
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.

## Su dung

1. Mở app bằng PIN test mặc định `18758691`.
2. Nhập PIN riêng khi mở `Cấu hình`; việc mở khóa có hiệu lực trong phiên app hiện tại.
3. Nhập số thiết bị `001`-`100`, email nhận, chu kỳ và Gmail gửi.
4. App Password để trống nếu giữ giá trị đã lưu; nhập mã mới để thay đổi.
5. Bấm `Lưu và kiểm tra`, cấp quyền vị trí nền và thông báo, sau đó `Bắt đầu theo dõi`.
6. Quyền nhận diện hoạt động là tùy chọn và không chặn việc bắt đầu tracking.
7. Khi tracking chạy, Android hiển thị thông báo foreground liên tục.
8. Xem, lọc theo Năm/Tháng và chia sẻ CSV tại `Lịch sử`.

PIN luôn được yêu cầu lại khi dừng tracking hoặc xóa dữ liệu. Xóa theo bộ lọc chỉ xóa đúng năm/tháng đang chọn; `Xóa tất cả` xóa toàn bộ lịch sử sau bước xác nhận.

Gio gui la khoang du kien do Android Doze co the tri hoan WorkManager. Chi tiet van hanh: `docs/periodic-gmail-pilot-handover.md`.
