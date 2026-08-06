# Internal Traccar Tracker

Ứng dụng Android 14+ lấy vị trí trong foreground service và gửi đến Traccar/OsmAnd. Kết nối được chỉnh ngay trên điện thoại hoặc nhập bằng một file JSON dùng chung. Device ID tự sinh; PIC xác nhận trên Traccar.

## Build trên GitHub

1. Tạo repository trống trên GitHub, không thêm README/license.
2. Mở Terminal tại thư mục dự án và chạy:

```powershell
git remote add origin https://github.com/TEN_CUA_BAN/TEN_REPO.git
git push -u origin main
```

3. Mở tab **Actions** trên GitHub, chọn workflow **Android** và chờ dấu xanh.
4. Mở lần chạy mới nhất, tải artifact `traccar-tracker-debug`, giải nén và cài `app-debug.apk` lên máy test.

GitHub Actions tự chạy unit test, Android lint và build APK nội bộ. Dự án không có quy trình phát hành Google Play hoặc phân phối công khai.

## Build bằng Android Studio

- Android Studio mới, JDK 17 và Android SDK 36.
- Mở thư mục dự án, chờ Gradle sync, chọn **Build > Build APK(s)**.
- APK nằm tại `app/build/outputs/apk/debug/app-debug.apk`.

Hoặc chạy:

```powershell
./gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

## Cấu hình điện thoại

1. IT sửa `config/traccar-profile.example.json` thành host/port/TLS thực tế và gửi cùng một file cho mọi máy.
2. Trong app mở **Kết nối**, chọn **Nhập JSON**, xem trước rồi xác nhận.
3. Chọn cấu hình vừa nhập làm cấu hình hoạt động.
4. Cấp lần lượt quyền vị trí chính xác, vị trí nền và thông báo. Khi Android chặn quyền, nút trong app mở đúng trang Settings.
5. Nhấn **Bắt đầu theo dõi**, giữ notification foreground đang chạy.
6. Gửi Device ID hiển thị trong app cho PIC; PIC tìm thiết bị tự đăng ký trong group chờ, đổi tên và xác nhận.
7. Mở **Chẩn đoán**: thử server trước, sau đó thử gửi vị trí GPS thật.

Không tắt tối ưu pin nếu chính sách công ty không cho phép. Nếu hãng điện thoại dừng app, trạng thái/notification sẽ cho biết tracking không còn chạy; WorkManager chỉ gửi lại dữ liệu đã xếp hàng.

Tài liệu IT: `Huong-dan-ky-thuat-setup-Traccar-Server-noi-bo.md`  
Checklist máy thật: `docs/android-14-device-test-checklist.md`
