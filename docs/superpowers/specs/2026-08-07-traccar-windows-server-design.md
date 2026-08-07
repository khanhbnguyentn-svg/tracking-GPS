# Thiết kế Traccar Server miễn phí trên Windows PC

Ngày: 2026-08-07  
Trạng thái: Đã được người dùng duyệt

## 1. Mục tiêu

Triển khai Traccar Server nội bộ trực tiếp trên PC Windows hiện tại để nhận vị trí từ ứng dụng Android trong cùng mạng Wi-Fi/LAN. Hệ thống phục vụ tối đa 350 điện thoại, dùng hoàn toàn các thành phần không phát sinh phí bản quyền, có HTTPS, PostgreSQL, backup/restore, cảnh báo gián đoạn và quy trình tạm ngừng cảnh báo có thời hạn.

Hệ thống không mở ra Internet. PC phải luôn bật, không sleep, và có địa chỉ IPv4 LAN cố định hoặc DHCP reservation.

## 2. Phạm vi và ranh giới

Repository cung cấp cấu hình, script PowerShell, kiểm thử tự động và SOP vận hành. Script cài đặt tải và cài Traccar, PostgreSQL và Caddy trực tiếp trên Windows, đăng ký chúng làm Windows Service, cấu hình Windows Firewall và Task Scheduler.

Các việc cần thao tác bên ngoài repository gồm cấp quyền Administrator, xác nhận network profile là Private, đặt IP/DHCP reservation, nhập mật khẩu, cài CA nội bộ lên điện thoại và xác nhận thiết bị trong giao diện Traccar.

Không dùng Docker Desktop, WSL, dịch vụ cloud trả phí, SMS trả phí hoặc chứng chỉ công cộng. Không hỗ trợ truy cập từ Internet/4G trong thiết kế này.

## 3. Kiến trúc

```text
Android trên LAN
  -> HTTPS :5055 (Caddy)
  -> HTTP loopback :15055
  -> Traccar OsmAnd
  -> PostgreSQL :5432 (loopback only)

Trình duyệt quản trị trên LAN
  -> HTTPS :8082 (Caddy)
  -> HTTP loopback :18082
  -> Traccar Web/API
```

- Traccar được ghim tại `6.13.3`; nâng cấp là thao tác có chủ đích sau backup và thử nghiệm.
- PostgreSQL chỉ lắng nghe loopback và dùng tài khoản/database riêng cho Traccar.
- Caddy dùng CA nội bộ để cấp chứng chỉ cho hostname và IP LAN đã cấu hình. Root CA được export để cài trên điện thoại và máy quản trị.
- Traccar, PostgreSQL và Caddy chạy bằng tài khoản dịch vụ với quyền tối thiểu mà bộ cài/hệ điều hành hỗ trợ.
- Dữ liệu, log, backup và cấu hình runtime nằm ngoài source tree. Repository chỉ chứa template không có secret.

## 4. Cấu hình môi trường

Một file cấu hình cục bộ, bị Git ignore, chứa:

- IPv4 LAN và hostname của PC.
- Đường dẫn cài đặt, dữ liệu và backup.
- Port công khai `5055` và `8082`; port loopback `15055` và `18082`.
- Tên database/user PostgreSQL; mật khẩu được lấy từ Windows Credential Manager hoặc nhập tương tác, không ghi plaintext vào repository/log.
- Thời gian lưu backup và ngưỡng cảnh báo dung lượng.
- Group ID chờ xác nhận, group hoạt động, group tạm ngừng và notification ID sau khi người vận hành tạo chúng trong Traccar.

Script validate IPv4, port, đường dẫn, dung lượng đĩa và quyền Administrator trước khi thay đổi máy. Chạy lại script phải idempotent: không tạo trùng service, firewall rule hoặc scheduled task.

## 5. Cài đặt và dịch vụ Windows

Trình cài thực hiện theo thứ tự:

1. Preflight: phiên bản Windows, quyền Administrator, RAM/đĩa, network Private, port conflict và trạng thái sleep.
2. Tải artifact từ nguồn chính thức, kiểm tra SHA-256 lấy từ manifest được ghim trong repository.
3. Cài PostgreSQL, tạo database/user và kiểm tra kết nối loopback.
4. Cài Traccar `6.13.3`, thay cấu hình database và binding loopback.
5. Cài Caddy, tạo chứng chỉ CA nội bộ, reverse proxy và Windows Service.
6. Tạo firewall rules chỉ cho profile Private và subnet LAN đã cấu hình.
7. Tạo scheduled tasks cho backup hằng ngày, health check và xử lý hết hạn tạm ngừng mỗi 5 phút.
8. Chạy smoke test và in hướng dẫn cài root CA/cấu hình Android.

Script không tự động mở cổng trên router, không tạo port forwarding và không tắt firewall hay TLS validation.

## 6. Traccar và onboarding thiết bị

`traccar.xml` bật PostgreSQL và tự đăng ký thiết bị với:

```xml
<entry key='database.registerUnknown'>true</entry>
<entry key='database.registerUnknown.regex'>^AND-[0-9A-F]{16}$</entry>
<entry key='database.registerUnknown.defaultGroupId'>GROUP_ID_CHO_PIC</entry>
<entry key='database.registerUnknown.defaultCategory'>mobile</entry>
```

Vì Group ID chỉ tồn tại sau lần khởi động và cấu hình ban đầu, script có hai pha. Pha bootstrap cài dịch vụ; người vận hành đổi mật khẩu admin mặc định và tạo các group/notification. Pha finalize nhận ID, cập nhật cấu hình, restart Traccar và kiểm thử regex.

Nhóm `Chờ PIC xác nhận` không được gắn notification vận hành. PIC đối chiếu Device ID, đổi tên và chuyển thiết bị hợp lệ sang nhóm hoạt động. Thiết bị không xác định bị disable/xóa và phải được điều tra từ log.

File `config/traccar-profile.example.json` được cập nhật theo hostname/IP LAN, port `5055`, scheme `https`, interval mặc định 60 giây và `tlsMode` phù hợp với cách app hỗ trợ CA nội bộ.

## 7. HTTPS và mạng

Caddy terminate TLS trên `5055` và `8082`; upstream chỉ dùng loopback. Root CA nội bộ phải được cài như CA tin cậy trên mọi điện thoại trước khi app kết nối. Không có trust-all certificate hoặc fallback tự động sang HTTP.

Firewall cho phép TCP `5055` từ subnet điện thoại đã cấu hình. TCP `8082` chỉ cho subnet/máy quản trị; nếu ban đầu cùng một LAN, danh sách địa chỉ quản trị phải được khai báo rõ. PostgreSQL và các port upstream không được mở trên LAN.

Tên DNS nội bộ là lựa chọn ưu tiên. Nếu LAN chưa có DNS, dùng IP tĩnh trong chứng chỉ và profile Android. Thay IP yêu cầu cấp lại chứng chỉ và phát lại cấu hình.

## 8. Cảnh báo gián đoạn

Nhóm hoạt động có các attribute:

```text
deviceInactivityStart = 300000
deviceInactivityPeriod = 300000
```

Notification `Device Inactive` gửi trên Web và ít nhất một kênh miễn phí đã có sẵn, mặc định email SMTP nội bộ. Nếu không có SMTP/webhook miễn phí, Web notification vẫn được cấu hình nhưng nghiệm thu production bị đánh dấu chưa đạt cho đến khi có kênh ngoài trình duyệt.

Cảnh báo đầu tiên ở phút 5, lần thứ hai ở phút 10. Nội dung chứa Device ID, tên, thời điểm vị trí cuối, tuổi dữ liệu và link Traccar. Cảnh báo chỉ biểu thị mất dữ liệu vị trí, không xác nhận tai nạn.

## 9. Tạm ngừng cảnh báo có thời hạn

Không disable thiết bị. Automation lưu yêu cầu trong một file JSON cục bộ có ACL giới hạn cho Administrators và tài khoản scheduled task. Mỗi bản ghi có Device ID, group gốc, lý do, người duyệt, người thao tác, thời gian bắt đầu/kết thúc và trạng thái.

Lệnh `Suspend` chuyển thiết bị sang group `Tạm ngừng cảnh báo`; lệnh `Resume` đưa về group gốc. Scheduled task mỗi 5 phút tự resume bản ghi hết hạn. Mọi thao tác ghi audit log append-only trong Windows Event Log hoặc file có ACL. Thao tác API thất bại được retry ở lần chạy sau và tạo cảnh báo health check; không đánh dấu hoàn thành giả.

## 10. Backup, restore và giám sát

- `pg_dump` chạy hằng ngày, tạo file tạm rồi rename atomically khi thành công.
- Backup được nén, checksum SHA-256 và lưu theo retention cấu hình.
- Không xóa backup cũ nếu lần backup hiện tại thất bại.
- Restore drill dùng database tạm riêng, chạy kiểm tra schema/số bảng rồi xóa database tạm sau khi thành công.
- Health check kiểm tra Windows Services, HTTPS endpoints, kết nối PostgreSQL, tuổi backup mới nhất, dung lượng đĩa và log lỗi gần đây.
- Kết quả ghi Windows Event Log và trả exit code khác 0 để Task Scheduler ghi nhận lỗi.

## 11. Xử lý lỗi và rollback

Mỗi bước cài đặt ghi log không chứa secret. Nếu một bước thất bại, script dừng, giữ nguyên dữ liệu đã tồn tại và in lệnh tiếp tục/rollback cụ thể. Uninstall mặc định chỉ gỡ service, scheduled task và firewall rule do dự án tạo; không xóa database, backup, certificate hoặc dữ liệu. Xóa dữ liệu cần cờ riêng và xác nhận tương tác.

Nếu Caddy hoặc Traccar ngừng, health check báo lỗi; không tự mở port trực tiếp để né proxy. Nếu PostgreSQL lỗi, Traccar được giữ nguyên để phục vụ chẩn đoán và không tự khởi tạo database mới.

## 12. Kiểm thử và nghiệm thu

Kiểm thử tự động bằng PowerShell/Pester bao phủ validation cấu hình, render template, idempotency logic, firewall scope, backup retention, audit record và tính toán hạn resume. Các test không cần quyền Administrator không thay đổi máy.

Smoke test sau cài đặt kiểm tra service, loopback port, HTTPS, certificate chain và database. Nghiệm thu thủ công bao gồm:

- Android tin cậy CA và gửi được vị trí qua Wi-Fi/LAN.
- ID hợp lệ tự tạo trong group chờ; ID sai regex không tạo thiết bị.
- Web/API chỉ truy cập được từ địa chỉ quản trị.
- Cảnh báo xuất hiện ở phút 5 và lặp ở phút 10; dữ liệu trở lại được PIC xác nhận/đóng theo SOP.
- Tạm ngừng hết hạn tự bật lại mà dữ liệu GPS vẫn được nhận.
- Backup được restore thử thành công.
- Khởi động lại Windows làm cả ba service và scheduled tasks hoạt động lại.
- Khi PC sleep/tắt hoặc đổi IP, hệ thống thể hiện lỗi rõ ràng và tài liệu chỉ cách khắc phục.

## 13. Tiêu chí hoàn thành

Repository có script bootstrap/finalize, template cấu hình, test Pester, SOP cài đặt/vận hành/backup/restore/certificate và checklist nghiệm thu. Hệ thống trên PC chỉ được coi là sẵn sàng khi mọi mục nghiệm thu đạt, admin mặc định đã đổi mật khẩu, CA đã cài trên điện thoại test, backup restore thành công và firewall được xác nhận từ cả địa chỉ được phép lẫn không được phép.
