# Quy trình phát hành và cập nhật APK ổn định

## Danh tính bản phát hành

- Package: `com.internal.tracker`
- Bản hiện tại: `2.0.0 (2)`
- APK phân phối: `dist/tracking-gps-2.0.0.apk`
- Certificate SHA-256: `8F:19:12:A3:4E:D2:CB:9D:DF:88:40:DB:49:A7:69:13:42:51:B3:29:74:84:33:36:78:E2:C6:79:CA:E4:F5:85`

Chỉ APK có đúng package, version tăng dần và certificate trên mới được dùng để cập nhật các điện thoại hiện có. Debug APK hoặc artifact từ GitHub Actions chỉ phục vụ kiểm thử, không phải bản phân phối.

## Chuẩn bị signing một lần

Tại PowerShell ở thư mục dự án:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\prepare-release-signing.ps1
```

Lệnh tạo hai file riêng tư:

- `.signing/tracker-release.p12`
- `.signing/signing.properties`

Script dừng nếu file đã tồn tại và không tự ghi đè. Cả hai file bị Git bỏ qua. `signing.properties` chứa mật khẩu dạng rõ trên máy phát hành; giới hạn quyền truy cập thư mục và không gửi file qua email/chat.

Khóa này kế thừa private key của debug APK cũ để Android chấp nhận cập nhật đè. Việc đổi sang PKCS#12 và mật khẩu mạnh không loại bỏ rủi ro của bản debug keystore nguồn có mật khẩu mặc định; bảo vệ cả `.signing` và `.tools/android-home/debug.keystore`.

## Backup và thử khôi phục

Trước khi phân phối bản đầu tiên:

1. Sao chép nguyên cặp `.signing/tracker-release.p12` và `.signing/signing.properties` vào kho backup được mã hóa, nằm ngoài máy build.
2. Trên một thư mục checkout thử nghiệm, khôi phục cặp file về `.signing/`.
3. Chạy `scripts/build-release-apk.ps1` và xác nhận fingerprint đúng.
4. Xóa bản khôi phục thử sau khi kiểm tra theo chính sách lưu trữ nội bộ.

Mất private key đồng nghĩa không thể cập nhật các điện thoại đã cài. Không tạo khóa thay thế âm thầm khi backup lỗi.

## Build bản phát hành

Điền `gmail-secrets.properties` như hướng dẫn hiện có, sau đó chạy:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\build-release-apk.ps1
```

Script chạy unit tests, lint, release build, xác minh package/version/chữ ký rồi mới tạo:

```text
dist/tracking-gps-2.0.0.apk
```

Nếu file cùng version đã tồn tại, script dừng để tránh phát hành lại một `versionCode` với nội dung khác. Sau khi bàn giao, lưu thêm SHA-256 của chính file APK:

```powershell
Get-FileHash .\dist\tracking-gps-2.0.0.apk -Algorithm SHA256
```

Mỗi APK đã phân phối phải tăng `versionCode` ít nhất 1. `versionName` dùng `MAJOR.MINOR.PATCH`. Không dùng downgrade vì database không có migration ngược.

## Cập nhật điện thoại

### Android Package Installer

1. Chuyển APK release tới điện thoại.
2. Mở file và chọn **Cập nhật**; không gỡ ứng dụng cũ.
3. Mở app một lần sau cập nhật để reconcile foreground tracking và lịch báo cáo.
4. Kiểm tra History, số thiết bị, Gmail/email nhận, chu kỳ, PIN và trạng thái tracking còn nguyên.
5. Nếu tracking đang bật, xác nhận notification foreground xuất hiện và kỳ báo cáo tiếp theo hợp lý.

### ADB

```powershell
adb install -r .\dist\tracking-gps-2.0.0.apk
```

Không dùng `-d`, không chạy `adb uninstall` và không xóa package data.

## Xử lý lỗi

- `UPDATE_INCOMPATIBLE` hoặc xung đột chữ ký: dừng cài đặt, đối chiếu certificate fingerprint của APK cũ/mới. Không gỡ app trước khi đánh giá và sao lưu dữ liệu.
- Version downgrade: build versionCode mới cao hơn; không ép downgrade.
- Thiếu/sai signing properties: khôi phục đúng cặp file backup; không tạo keystore mới.
- Build identity mismatch: không phân phối APK; kiểm tra package, version và source keystore.
- App chưa tự khôi phục tracking sau update: mở app, kiểm tra quyền/notification và trạng thái; không xóa database.

## Acceptance trước khi phân phối rộng

Trên ít nhất một điện thoại đang có `1.0 (1)` ký bằng fingerprint đã duyệt, cập nhật lên `2.0.0 (2)` mà không uninstall. Kết quả chỉ đạt khi Room History, cấu hình, PIN và tracking state còn nguyên, đồng thời service và lịch báo cáo được reconcile sau khi mở app.
