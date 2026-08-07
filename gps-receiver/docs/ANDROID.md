# Kết nối ứng dụng Android

PC hiện có IPv4 Wi-Fi `192.168.80.146/24`. Điện thoại phải cùng Wi-Fi và không dùng client isolation.

1. Import `config/traccar-profile.example.json` vào app.
2. Xác nhận host `192.168.80.146`, port `5055`, scheme `http`.
3. Chạy Chẩn đoán rồi bắt đầu tracking.
4. Mở `http://192.168.80.146:5055/dashboard` từ trình duyệt điện thoại để kiểm tra kết nối LAN.

HTTP chỉ dành cho mô phỏng trong LAN Private. Không dùng cấu hình này qua Internet hoặc mạng Wi-Fi không tin cậy. Nên tạo DHCP reservation cho PC để địa chỉ không thay đổi.
