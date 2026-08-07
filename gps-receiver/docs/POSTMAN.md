# Kiểm thử bằng Postman

1. Import `postman/gps-receiver.postman_collection.json`.
2. Import `postman/gps-receiver.local.postman_environment.json` và chọn environment đó.
3. Chạy từng request hoặc mở Collection Runner.
4. Để mô phỏng nhiều thiết bị, chọn request `OsmAnd GET - app hiện tại`, đặt số iteration từ 1 đến 350. Script tự tạo Device ID hợp lệ theo iteration; không cần data file trả phí.
5. Giữ tốc độ dưới 120 request/phút cho mỗi IP hoặc tăng `GPS_RATE_LIMIT` có chủ đích trong môi trường test.

Dashboard: `http://localhost:5055/dashboard`.
