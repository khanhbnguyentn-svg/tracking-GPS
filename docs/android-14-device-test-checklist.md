# Checklist máy thật Android 14–16

Ghi model máy, hãng, phiên bản Android, phiên bản app, Device ID, profile và người test.

## Cài đặt và quyền

- [ ] Cài APK mới; Device ID có dạng `AND-` + 16 ký tự hex và giữ nguyên sau khi cập nhật app.
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
- [ ] Khởi động lại điện thoại: xác nhận trạng thái rõ ràng; người dùng chủ động bắt đầu lại nếu service không chạy.

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
