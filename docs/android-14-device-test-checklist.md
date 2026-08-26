# Checklist thiết bị thật Android 14-16

Checklist này chỉ áp dụng cho Android 2.1 Periodic Email Pilot. Ghi lại model, hãng, phiên bản Android, phiên bản app, số thiết bị, Device ID, người kiểm thử và thời gian kiểm thử.

## 1. Cài đặt và nâng cấp

- [ ] Cài đè signed APK `tracking-gps-2.1.0.apk` bằng Package Installer hoặc `adb install -r`; không uninstall và không dùng `-d`.
- [ ] Android hiển thị luồng cập nhật, không báo xung đột chữ ký; app sau cài đặt là `2.1.0 (6)`.
- [ ] Room History, số thiết bị, Gmail gửi, email nhận, chu kỳ, PIN và tracking state còn nguyên.
- [ ] Nếu gặp signature mismatch, dừng kiểm thử và bảo toàn package data; không gỡ app để thử lại.
- [ ] Nếu tracking đang bật trước cập nhật, `MY_PACKAGE_REPLACED` hoặc lần mở app kế tiếp phục hồi foreground service mà không reset thời điểm bắt đầu.

## 2. Cấu hình và quyền

- [ ] Mở Settings bằng PIN quản trị và chạy `Lưu và kiểm tra`; Gmail SMTP đăng nhập thành công.
- [ ] Cấp vị trí chính xác, vị trí nền và notification theo luồng Android.
- [ ] Activity Recognition là tùy chọn; từ chối quyền này không chặn GPS tracking.
- [ ] Nút quyền thiết bị mở đúng App Settings khi quyền bị từ chối vĩnh viễn.
- [ ] Khi GPS tắt, app mở đúng Location Settings và hiển thị lỗi có thể xử lý.
- [ ] Tắt tối ưu pin theo chính sách của model Samsung/Xiaomi/Oppo/Vivo được triển khai.

## 3. Foreground tracking và hành trình

- [ ] Bật tracking; foreground notification xuất hiện và duy trì ổn định.
- [ ] Khóa màn hình 30 phút; callback GPS vẫn tiếp tục theo cadence đã thiết kế.
- [ ] Vuốt app khỏi recent apps; foreground tracking tiếp tục hoặc app báo rõ nếu firmware dừng service.
- [ ] Reboot khi tracking đang bật; sau khi mở khóa user profile, foreground service và GPS tự phục hồi mà không cần bấm Start.
- [ ] `dumpsys location` hiển thị request `HIGH_ACCURACY` khoảng 10 giây cho `com.internal.tracker` khi tracking hoạt động.
- [ ] Khi xe chạy, app lưu `PERIODIC` khoảng mỗi 2 phút.
- [ ] Dừng dưới 2 phút tạo `TEMP_STOP`; dừng từ 2 phút trở lên nâng cùng record thành `STOP`.
- [ ] Di chuyển lại tạo `START` mới mà không cần mở lại app.
- [ ] Stop trong app hoặc notification dừng tracking; app không tự bật lại khi trạng thái đã được lưu là tắt.

## 4. Email định kỳ và retry

- [ ] Mỗi kỳ 6h/12h/24h tạo tối đa một email logic với đúng Message ID và CSV attachment.
- [ ] CSV chứa đúng số thiết bị, Device ID, thời gian, tọa độ, accuracy, pin, tracked duration, delivery state và record type.
- [ ] `TEMP_STOP` chưa hoàn tất không xuất hiện trong email.
- [ ] Tắt mạng qua ít nhất ba kỳ; dữ liệu vẫn nằm trong Room và delivery chuyển sang retry thay vì bị mất.
- [ ] Bật mạng; kỳ kế tiếp gửi backlog đúng thứ tự và không đánh dấu `SENT` trước khi SMTP thành công.
- [ ] Gmail authentication failure hiển thị lỗi công khai an toàn; không lộ App Password, PIN hoặc stack trace.
- [ ] Báo cáo đã gửi không bị gửi lại ngoài trường hợp retry/resend có Message ID được kiểm soát.

## 5. Tracking integrity diagnostics

- [ ] Tắt Location ít nhất 40 giây khi tracking đang bật; service vẫn foreground và chỉ tạo một incident GPS gap.
- [ ] Khi có mạng, chỉ một email `GPS_GAP_OPENED` được gửi; không lặp theo health check 10 giây.
- [ ] Bật lại Location; email `GPS_GAP_RECOVERED` dùng cùng incident ID và chứa duration cùng evidence trước/sau.
- [ ] Khi mất mạng, lỗi SMTP không dừng tracking; diagnostic chưa gửi xuất hiện trong attachment của report định kỳ kế tiếp.
- [ ] Reboot/update chỉ tạo tối đa một gap suy luận cho cùng sự kiện và không reset `startedAt`.
- [ ] Diagnostics không xóa, sửa, trì hoãn hoặc tạo thêm route record giả.
- [ ] Status, History và Settings không hiển thị màn diagnostics nội bộ mới.

## 6. History, export và xóa dữ liệu

- [ ] Lọc theo năm/tháng trả đúng phạm vi và xuất đúng CSV.
- [ ] Xóa theo bộ lọc chỉ xóa phạm vi đang chọn sau bước xác nhận và PIN.
- [ ] Xóa tất cả chỉ thực hiện sau xác nhận rõ phạm vi và PIN.
- [ ] Dữ liệu cũ hơn thời hạn lưu giữ được cleanup mà không ảnh hưởng record mới hoặc delivery đang retry.

## 7. Kết quả chấp nhận

- [ ] Chạy toàn bộ checklist trên ít nhất một máy Android 14 và từng nhóm model thực tế sẽ triển khai.
- [ ] Ghi rõ mọi hạn chế firmware/battery policy theo model.
- [ ] Admin xác nhận email định kỳ, OPENED/RECOVERED và diagnostics attachments bằng dữ liệu đã che thông tin nhạy cảm.
- [ ] Không cài debug APK lên thiết bị đang chứa dữ liệu vận hành.
