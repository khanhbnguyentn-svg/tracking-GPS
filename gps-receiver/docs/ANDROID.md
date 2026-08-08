# Kết nối ứng dụng Android

PC hiện có IPv4 Wi-Fi `192.168.80.146/24`. Điện thoại phải cùng Wi-Fi và không dùng client isolation.

1. Import `config/traccar-profile.example.json` vào app.
2. Xác nhận host `192.168.80.146`, port `5055`, scheme `http`.
3. Chạy Chẩn đoán rồi bắt đầu tracking.
4. Mở `http://192.168.80.146:5055/dashboard` từ trình duyệt điện thoại để kiểm tra kết nối LAN.

HTTP chỉ dành cho mô phỏng trong LAN Private. Không dùng cấu hình này qua Internet hoặc mạng Wi-Fi không tin cậy. Nên tạo DHCP reservation cho PC để địa chỉ không thay đổi.

## Thử nghiệm Internet hai ngày

Cloudflare Quick Tunnel chỉ dùng cho thử nghiệm một điện thoại trong hai ngày, **không phải cấu hình production**. IT phải giữ PC bật, ngăn Windows sleep, duy trì kết nối Internet và để hai service `InternalGpsReceiver`, `InternalTraccar-PostgreSQL` chạy trong suốt thử nghiệm.

Khi tracking đang dừng trên điện thoại:

1. Import `D:\InternalGPS\Pilot\tracking-pilot-profile.json`.
2. Chọn profile `Internet pilot 2 ngay` rồi mới bật tracking.
3. Sau khi xác nhận import thành công, xóa file JSON plaintext khỏi Windows.

Checklist nghiệm thu trên điện thoại Android 14+:

1. Gửi một vị trí thật qua Wi-Fi; trên dashboard xác nhận đúng Device ID tự động của điện thoại.
2. Tắt Wi-Fi, dùng mobile data; xác nhận dashboard có server timestamp mới hơn.
3. Tắt toàn bộ kết nối mạng, chờ app báo ít nhất một điểm trong hàng đợi; bật lại mạng và xác nhận hàng đợi về 0, điểm đó không bị mất.
4. Tạm chọn một profile có token sai; xác nhận app hiển thị `401` là lỗi cấu hình/xác thực, rồi chọn lại profile đã sinh.
5. Ghi lại mọi lần PC, service, tunnel hoặc mạng bị gián đoạn trong hai ngày.

URL Quick Tunnel thay đổi nếu tunnel khởi động lại, khi đó IT phải chạy lại Start và điện thoại phải import profile mới. Lệnh Stop kết thúc thử nghiệm, thu hồi token dùng chung và xóa profile plaintext còn lại.
