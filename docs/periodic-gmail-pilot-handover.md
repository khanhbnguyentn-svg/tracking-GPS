# Huong dan trien khai GPS Email Pilot

## 1. Phạm vi

- Android 10 tro len, phan phoi APK noi bo.
- 100 thiet bi, danh so `001` den `100`; Device ID tu dong khong thay doi.
- Lich `6h`, `12h`, `24h`, neo gan 00:00 va dan deu trong 59 phut.
- Khong can Traccar, webserver, Cloudflare Tunnel hoac may tinh chay lien tuc.

Ứng dụng tách thành hai luồng độc lập:

- `TrackingService` chạy foreground khi tracking bật, quan sát GPS khoảng mỗi 10 giây và lưu sự kiện hành trình vào Room.
- `ReportWorker` chạy theo lịch 6h/12h/24h, gom các record đã hoàn tất nhưng chưa gửi vào một CSV và gửi một email duy nhất.

Khi xe đang chạy, app lưu `PERIODIC` mỗi 2 phút, đồng thời lưu `START`, `TEMP_STOP` và `STOP`. Điểm bắt đầu dừng được tạo ngay dưới dạng `TEMP_STOP` chưa hoàn tất; nếu xe chạy lại trước 2 phút, record được hoàn tất dưới dạng `TEMP_STOP`; nếu đứng yên đủ 2 phút, cùng record đó được nâng cấp thành `STOP`.

## 2. Chuan bi Gmail

1. Tao Gmail rieng chi de gui bao cao.
2. Bat xac minh hai buoc tren tai khoan Gmail.
3. Tao App Password 16 ky tu.
4. Gmail mien phi co gioi han 500 thu/24 gio. Lich 6h voi 100 may tao toi da 400 thu theo lich, chua tinh retry.

Khong dung mat khau dang nhap Gmail thuong. Dien thoai khong phai xac minh hai buoc khi gui SMTP bang App Password.

## 3. Chuan bi may Windows moi

1. Cai Git, JDK 17 va Android Studio hoac Android command-line tools mien phi.
2. Cai Android SDK Platform 36 va Build Tools 36.0.0.
3. Clone repository va mo PowerShell tai thu muc du an.
4. Tao `gmail-secrets.properties`:

```properties
SMTP_USER=sender@gmail.com
SMTP_APP_PASSWORD=abcdefghijklmnop
```

File that bi `.gitignore`; khong dua credential len GitHub.

5. Build:

```powershell
./gradlew.bat clean testDebugUnitTest lintDebug assembleDebug --no-daemon
```

6. Lay APK tai `app/build/outputs/apk/debug/app-debug.apk`.

GitHub Actions cung build artifact `gps-email-pilot-debug`, nhung may tinh khong can bat sau khi `git push` thanh cong.

## 4. Cap phat dien thoai

1. Cài APK và mở app; màn `Trạng thái` xuất hiện ngay, không yêu cầu PIN.
2. Mở `Cấu hình`, nhập PIN quản trị, sau đó nhập số thiết bị duy nhất, email nhận và chu kỳ.
3. Bam `Luu va kiem tra`. Neu App Password build san con dung, co the de trong o ma moi.
4. Cấp vị trí chính xác, `Luôn cho phép` vị trí nền và thông báo khi được hỏi.
5. Có thể cấp thêm quyền Nhận diện hoạt động để hỗ trợ nhận biết `IN_VEHICLE`. Nếu từ chối hoặc thiết bị không hỗ trợ, app vẫn theo dõi bằng tốc độ và khoảng cách GPS.
6. Nếu nút `Cấp quyền thiết bị` mở Settings, chọn quyền còn thiếu rồi quay lại app.
7. Bấm `Bắt đầu theo dõi` và xác nhận thông báo foreground `Đang theo dõi vị trí` xuất hiện.

Khong tat pin/toi uu nen cho app neu nha san xuat dien thoai co tuy chon rieng. WorkManager phuc hoi sau reboot, nhung Android/firmware co the tri hoan hoac dung tac vu; app hien thoi diem GPS/email cuoi va loi ky thuat gan nhat.

## 5. Dữ liệu và mất sóng

Foreground service ghi Room liên tục, không gửi email ngay. Khi đến kỳ, worker xóa record cũ hơn một năm, lấy các record đã hoàn tất chưa gửi, tạo một CSV và gửi một email. Khi mất mạng, record giữ `RETRYING`; kỳ sau tiếp tục gom backlog. Bản ghi `SENT` vẫn được giữ trong lịch sử cho tới khi bị xóa thủ công hoặc quá thời hạn lưu giữ.

CSV gồm Device ID/số thiết bị, thời gian, tọa độ, sai số GPS, pin, thời gian theo dõi, trạng thái gửi và `record_type`. Record `TEMP_STOP` chưa hoàn tất không được đưa vào email.

Tại màn `Lịch sử`:

- Chọn `Năm` và `Tháng` để xem hoặc xuất đúng phạm vi.
- Chọn tháng khi năm đang là `Tất cả` sẽ tự chọn năm hiện tại.
- `Xóa theo bộ lọc` chỉ bật khi đã chọn năm; nó xóa đúng năm hoặc tháng đang chọn.
- `Xóa tất cả` xóa toàn bộ database.
- Mỗi thao tác xóa đều yêu cầu xác nhận phạm vi, sau đó nhập PIN.

Màn Status không hiển thị Device ID. Device ID vẫn có trong Cấu hình, Room và CSV.

## 6. Bảo vệ bằng PIN

- App mở trực tiếp tại Status, không yêu cầu PIN khi khởi động.
- Status và Lịch sử chỉ đọc, có thể truy cập mà không cần PIN.
- Cấu hình yêu cầu PIN ở lần truy cập đầu tiên trong mỗi phiên.
- Dừng tracking luôn yêu cầu PIN riêng.
- Xóa theo bộ lọc và xóa tất cả luôn yêu cầu xác nhận rồi nhập PIN riêng.

## 7. Đổi credential

1. Tao App Password moi trong Gmail.
2. Mở app, vào `Cấu hình` và nhập PIN quản trị.
3. Dan ma 16 ky tu vao o App Password va bam `Luu va kiem tra`.
4. App chi thay credential cu sau khi Gmail chap nhan dang nhap.

## 8. Kiểm thử còn phải làm trên thiết bị

- Android 10, 12, 14+.
- Reboot va doi mui gio.
- Xác nhận callback GPS khoảng 10 giây và thay đổi BALANCED/HIGH accuracy theo trạng thái.
- Chạy xe để kiểm tra `START`, `PERIODIC` sau 2 phút, `TEMP_STOP` dưới 2 phút và `STOP` từ 2 phút.
- Từ chối quyền Nhận diện hoạt động và xác nhận fallback GPS vẫn ghi dữ liệu.
- Dừng tracking, mở Cấu hình và xóa dữ liệu để xác nhận đúng các dialog PIN.
- Lọc/xóa qua biên tháng 12 sang tháng 1 theo múi giờ thiết bị.
- Giữ dữ liệu cũ hơn một năm trong bản test rồi chạy báo cáo để xác nhận retention.
- GPS tat/quyen bi thu hoi.
- Doze va toi uu pin cua nha san xuat.
- Mat mang, tao backlog, sau do co mang lai.
- App Password sai/bi thu hoi va cap nhat ma moi.
- Lich 6h/12h/24h; thiet bi `001` va `100`.
- Mo/chia se CSV va doi chieu email nhan.

JVM unit test va lint khong thay the cac buoc thiet bi nay.
