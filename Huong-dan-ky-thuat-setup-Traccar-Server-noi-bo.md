# Bàn giao IT: Traccar Server nội bộ

Phạm vi: tiếp nhận vị trí từ 100–350 điện thoại Android qua giao thức OsmAnd. App tự tạo Device ID dạng `AND-<16 ký tự hex>`; không tạo file cấu hình riêng cho từng máy.

## 1. Kiến trúc khuyến nghị

- Traccar Server `traccar/traccar:6.13.3` (ghim phiên bản, không dùng `latest` trong production).
- PostgreSQL được backup hằng ngày; không dùng H2 cho production.
- Reverse proxy HTTPS ở trước cổng OsmAnd `5055` và Web/API `8082`.
- Chỉ cho phép Web/API quản trị từ VLAN/VPN quản trị. Endpoint nhận GPS chỉ mở cho mạng/VPN điện thoại cần dùng.
- Tối thiểu 2 vCPU, 4 GB RAM, 20 GB SSD; theo dõi CPU, RAM, dung lượng DB và thời gian ghi. 350 máy gửi mỗi 30 giây tương đương khoảng 12 bản tin/giây, chưa tính retry.

Nâng phiên bản Traccar phải thử ở staging và backup DB trước. Tham chiếu bản phát hành: https://github.com/traccar/traccar/releases

## 2. Hợp đồng kết nối app

App gửi đến:

```text
https://tracker.company.internal:5055/?id=AND-0123456789ABCDEF&lat=10.1&lon=106.1&timestamp=...&speed=...
```

- `id`: tự sinh trên điện thoại, PIC xác nhận trên server.
- `speed`: knot theo OsmAnd/Traccar.
- App xếp hàng cục bộ khi mất mạng và gửi lại sau; WorkManager chỉ retry hàng đợi, không tự khởi động foreground service.
- File dùng chung: `config/traccar-profile.example.json`. IT thay host/port/TLS rồi phát cùng một file cho mọi điện thoại.
- Ưu tiên `https` + chứng chỉ CA hệ thống. Nếu dùng CA nội bộ, cung cấp file chứng chỉ cho người cài app. Chỉ dùng `http` trong mạng/VPN tin cậy và coi là tạm thời.

## 3. Tự đăng ký và vùng cách ly

Tạo trước group `Chờ PIC xác nhận`, lấy ID của group, rồi thêm vào `traccar.xml`:

```xml
<entry key='database.registerUnknown'>true</entry>
<entry key='database.registerUnknown.regex'>^AND-[0-9A-F]{16}$</entry>
<entry key='database.registerUnknown.defaultGroupId'>ID_GROUP_CHO_XAC_NHAN</entry>
<entry key='database.registerUnknown.defaultCategory'>mobile</entry>
```

Quy trình PIC:

1. Đối chiếu Device ID hiển thị trên điện thoại với thiết bị mới trong group chờ.
2. Đổi tên thành tên nghiệp vụ, gán group hoạt động và PIC phụ trách.
3. Thiết bị không xác định phải bị disable/xóa và kiểm tra log nguồn gửi.

## 4. Cảnh báo gián đoạn

Dùng notification loại **Device Inactive**, không dựa riêng vào trạng thái offline của kết nối HTTP. Gán các attribute ở group hoạt động:

```text
deviceInactivityStart = 300000
deviceInactivityPeriod = 300000
```

Đơn vị là mili giây: cảnh báo đầu sau 5 phút không có vị trí, lặp lại mỗi 5 phút. Như vậy mốc 10 phút là lần cảnh báo thứ hai để PIC escalation. Tạo thêm notification phục hồi/online nếu phiên bản và kênh thông báo đang dùng hỗ trợ; nếu không, PIC đóng sự cố khi `last update` mới xuất hiện.

Mỗi cảnh báo phải có Device ID, tên thiết bị, thời điểm vị trí cuối, tuổi dữ liệu và link vào Traccar. Gửi ít nhất qua web + email/Teams/Slack webhook do IT quản lý. Thử notification sau mỗi thay đổi cấu hình.

Lưu ý: đây là cảnh báo mất dữ liệu, không tự kết luận tai nạn. PIC phải gọi xác minh theo quy trình an toàn nội bộ.

## 5. Tạm tắt cảnh báo có thời hạn

Traccar không có nút snooze có thời hạn theo từng thiết bị. Không disable thiết bị vì sẽ bỏ cả dữ liệu GPS. IT triển khai quy trình tối thiểu sau:

1. PIC ghi Device ID, lý do, người duyệt và thời điểm tự bật lại trong ticket/lịch trực.
2. IT tạm unlink notification `deviceInactive` khỏi thiết bị (API permission) hoặc chuyển thiết bị sang group `Tạm ngừng cảnh báo` không gắn notification.
3. Job định kỳ 5 phút đọc danh sách hết hạn và tự link lại notification/chuyển về group cũ; mọi thao tác có audit log.

Nếu chưa có automation, ticket bắt buộc có lịch nhắc và hai người kiểm tra khi bật lại. Đây là yêu cầu vận hành, không được coi là tính năng native của Traccar.

## 6. Kiểm tra nghiệm thu

- [ ] DNS và HTTPS truy cập được từ Wi-Fi/VPN của điện thoại; chứng chỉ không báo lỗi.
- [ ] Cổng `5055` không public ngoài phạm vi đã duyệt; Web/API `8082` chỉ dành cho quản trị.
- [ ] Gửi một Device ID hợp lệ sẽ tự tạo đúng group chờ; ID sai regex không được tạo.
- [ ] PIC xác nhận, đổi tên và chuyển thiết bị sang group hoạt động.
- [ ] Tắt mạng máy test: cảnh báo ở phút 5 và lặp/escalate ở phút 10.
- [ ] Bật mạng: dữ liệu hàng đợi được nhận, PIC thấy cập nhật mới và đóng cảnh báo.
- [ ] Tạm ngừng cảnh báo hết hạn sẽ tự bật lại.
- [ ] Backup PostgreSQL đã được restore thử; log/DB có cảnh báo dung lượng.

Tham chiếu chính thức: https://www.traccar.org/configuration-file, https://www.traccar.org/events/, https://www.traccar.org/api-reference
