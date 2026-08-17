# Checklist máy thật Android 14–16

Ghi model máy, hãng, phiên bản Android, phiên bản app, Device ID, profile và người test.

## Cài đặt và quyền

- [ ] Trên máy đang có `2.0.2 (4)`, cài đè `tracking-gps-2.0.3.apk` bằng Package Installer hoặc `adb install -r`; không uninstall và không dùng `-d`.
- [ ] Android hiển thị luồng **Cập nhật**, không báo xung đột chữ ký; app sau cài đặt là `2.0.3 (5)`.
- [ ] Room History, số thiết bị, Gmail/email nhận, chu kỳ và PIN còn nguyên sau cập nhật.
- [ ] Mở Settings bằng PIN tầng 2, bấm `Lưu và kiểm tra`; Gmail đăng nhập thành công trên bản 2.0.3.
- [ ] Trạng thái tracking còn nguyên; sau khi mở app, foreground notification và kỳ báo cáo tiếp theo được reconcile.
- [ ] Nếu tracking đang bật trước cập nhật, `MY_PACKAGE_REPLACED` tự phục hồi foreground service mà không cần reboot; nếu broadcast bị trì hoãn, mở app phục hồi service nhưng không thay lịch báo cáo.
- [ ] Nếu gặp signature mismatch, dừng kiểm thử và bảo toàn package data; không gỡ app để thử lại.
- [ ] Import file JSON dùng chung; xem trước đúng host, port, chu kỳ, scheme và TLS.
- [ ] Lưu/chuyển profile trên máy; không có Device ID trong file JSON.
- [ ] Quyền được hỏi theo thứ tự: vị trí chính xác, vị trí nền, thông báo.
- [ ] Khi từ chối vĩnh viễn, nút mở đúng trang App Settings; khi GPS tắt, nút mở Location Settings.
- [ ] Nút tối ưu pin mở đúng trang hệ thống; ghi lại yêu cầu riêng của Samsung/Xiaomi/Oppo/Vivo nếu có.

## Theo dõi

- [ ] Bắt đầu tracking khi app đang mở; notification foreground xuất hiện và giữ ổn định.
- [ ] Traccar nhận đúng Device ID và vị trí thật, không có điểm `0,0`.
- [ ] Khóa màn hình 30 phút: vị trí vẫn cập nhật theo chu kỳ cấu hình.
- [ ] Vuốt app khỏi recent apps: foreground tracking vẫn chạy hoặc app báo rõ nếu hãng dừng service.
- [ ] Nút Stop trong app và notification dừng tracking; app không tự khởi động lại bằng WorkManager.
- [ ] Khởi động lại điện thoại: tracking đang bật tự phục hồi foreground service và GPS sau khi user profile mở khóa; không cần bấm Start lại.
- [ ] Khi tracking vẫn bật và xe đã dừng trên 2 phút, `dumpsys location` vẫn hiển thị request `HIGH_ACCURACY` mỗi 10 giây cho `com.internal.tracker`.
- [ ] Di chuyển lại sau khi dừng trên 2 phút: app tạo `START` mới mà không cần mở lại app.

## Mạng, retry và TLS

- [ ] Tắt Wi-Fi/mobile data ít nhất ba chu kỳ: số điểm chờ tăng, app không mất dữ liệu.
- [ ] Bật mạng: hàng đợi giảm về 0, thứ tự/thời gian điểm trên Traccar hợp lý.
- [ ] DNS sai, port đóng, timeout và HTTP lỗi cho thông báo chẩn đoán khác nhau.
- [ ] HTTPS CA hệ thống hoạt động; chứng chỉ sai hostname/hết hạn bị từ chối.
- [ ] Custom CA đúng hoạt động; file CA sai bị từ chối.
- [ ] Pin đúng hoạt động; pin sai bị từ chối.
- [ ] HTTP chỉ được thử trong mạng/VPN đã duyệt và app hiển thị cảnh báo.

## Server và cảnh báo

- [ ] Thiết bị mới vào group chờ, PIC đối chiếu ID rồi đổi tên/chuyển group.
- [ ] Ngắt mạng: PIC nhận cảnh báo sau 5 phút và escalation/lặp ở phút 10.
- [ ] Khôi phục mạng: điểm chờ được gửi và PIC thấy dữ liệu mới.
- [ ] Tạm tắt cảnh báo cho máy test, đặt hạn ngắn; hết hạn notification được gắn lại tự động.

Kết quả chỉ đạt khi thử ít nhất một máy Android 14 và các model hãng thực tế sẽ triển khai. Các lỗi do chính sách pin của hãng phải được ghi thành hướng dẫn riêng theo model.

## Tracking integrity diagnostics - signed build 2.1.0

Chỉ thực hiện các bước sau với APK release đã ký đúng certificate. Không cài debug APK lên máy đang chứa dữ liệu vận hành.

### GPS gap khi có mạng

- [ ] Bật tracking, xác nhận foreground service đang chạy và ghi lại app version, incident test time và service state.
- [ ] Tắt Location ít nhất 40 giây; service phải tiếp tục foreground.
- [ ] Xác nhận chỉ có một email `GPS_GAP_OPENED`, không phát sinh email lặp lại theo mỗi health check 10 giây.
- [ ] Bật lại Location; email `GPS_GAP_RECOVERED` phải dùng cùng incident ID và có duration cùng evidence trước/sau.
- [ ] Không lưu credential hoặc tọa độ đầy đủ trong log test; chỉ lưu incident ID, timestamp, count, service state và subject email đã che thông tin.

### Mất mạng và fallback định kỳ

- [ ] Tắt mạng trước khi tạo một GPS gap khác; lỗi SMTP không được dừng tracking hoặc foreground service.
- [ ] Bật lại mạng rồi chờ/trigger kỳ report tiếp theo.
- [ ] Email định kỳ phải chứa `diagnostic-summary` và `diagnostic-samples`; incident chưa gửi tức thời phải nằm trong attachment.
- [ ] Email không chứa Gmail App Password, PIN hoặc stack trace.

### Reboot, update và dữ liệu hành trình

- [ ] Khi tracking đang bật, reboot máy, mở khóa user profile một lần và không bấm Start lại.
- [ ] Foreground service và request `HIGH_ACCURACY` 10 giây phải tự phục hồi; gap suy luận sau reboot chỉ được tạo một lần.
- [ ] Cài đè signed APK bằng `adb install -r`; `MY_PACKAGE_REPLACED` phải phục hồi tracking mà không reset `startedAt`.
- [ ] Khi xe chạy, route vẫn lưu `PERIODIC` mỗi 2 phút; diagnostics không được xóa, sửa, trì hoãn hoặc thêm route record giả.
- [ ] Một `TEMP_STOP` dưới 2 phút và một `STOP` từ 2 phút trở lên theo đúng thứ tự không được tạo anomaly giả.
- [ ] Status, History và Settings không hiển thị màn hình diagnostics mới.
