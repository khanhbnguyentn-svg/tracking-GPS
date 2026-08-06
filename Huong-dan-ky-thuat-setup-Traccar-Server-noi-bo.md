# Tài liệu kỹ thuật: Setup Traccar Server nội bộ
**Mục đích:** Gửi phòng IT làm căn cứ triển khai server tiếp nhận dữ liệu vị trí từ app Android nội bộ.
**Nguồn tham khảo:** https://github.com/traccar/traccar (Apache-2.0, Java)

---

## 1. Tổng quan yêu cầu

Cần triển khai **Traccar Server** trên hạ tầng nội bộ để tiếp nhận vị trí GPS định kỳ từ app Android do bộ phận [tên bộ phận] phát triển. App sẽ gửi dữ liệu qua giao thức **OsmAnd HTTP protocol** — một trong các protocol listener có sẵn của Traccar, dùng HTTP GET/POST đơn giản.

## 2. Yêu cầu hạ tầng

| Hạng mục | Yêu cầu tối thiểu | Ghi chú |
|---|---|---|
| OS | Linux (khuyến nghị) hoặc Windows Server | Có thể chạy bằng Docker image chính thức |
| RAM | 2 GB (dev/test) — 4 GB+ (production, nhiều thiết bị) | |
| CPU | 2 core | |
| Ổ đĩa | 20 GB+, tăng theo số lượng thiết bị và tần suất gửi vị trí | Dữ liệu vị trí tích lũy theo thời gian |
| Database | PostgreSQL (khuyến nghị cho production) | Traccar mặc định dùng H2 (file-based) — **không khuyến nghị cho production** vì không phù hợp nhiều kết nối đồng thời |
| Java Runtime | JRE 17+ (nếu không chạy Docker) | Traccar là ứng dụng Java |

## 3. Phương án triển khai (khuyến nghị: Docker)

```bash
docker run -d \
  --name traccar \
  -p 8082:8082 \
  -p 5055:5055 \
  -v /opt/traccar/data:/opt/traccar/data \
  -v /opt/traccar/logs:/opt/traccar/logs \
  traccar/traccar:latest
```

- **Port 8082**: Web UI + REST API quản trị.
- **Port 5055**: OsmAnd protocol listener — **đây là port app Android sẽ kết nối tới**.

> IT cần xác nhận port 5055 (hoặc port tùy chỉnh nếu đổi) được mở trong firewall nội bộ, chỉ cho phép truy cập từ dải mạng nội bộ/VPN công ty, **không public ra Internet**.

## 4. Yêu cầu bảo mật — QUAN TRỌNG

Vì dữ liệu là vị trí thời gian thực của nhân viên (dữ liệu nhạy cảm), đề xuất theo thứ tự ưu tiên:

### Ưu tiên 1 (khuyến nghị chính): HTTPS qua reverse proxy
- Đặt **Nginx** (hoặc reverse proxy tương đương) phía trước Traccar.
- Cấp **chứng chỉ TLS nội bộ** (từ CA nội bộ công ty nếu có, hoặc self-signed + cài certificate vào app).
- Forward: `https://traccar.internal.company.com:XXXX` → `http://traccar-server:5055`.
- App sẽ luôn gọi qua HTTPS, không gọi trực tiếp HTTP vào port 5055.

### Ưu tiên 2 (fallback tạm thời nếu chưa kịp cấp TLS)
- Giới hạn truy cập port 5055 **chỉ trong mạng nội bộ / bắt buộc qua VPN công ty**, không mở ra ngoài dưới bất kỳ hình thức nào.
- Đây chỉ nên là giải pháp tạm thời, cần có lộ trình chuyển sang HTTPS.

### Khuyến nghị bổ sung
- Theo dõi log truy cập bất thường vào port 5055/8082.
- Đổi mật khẩu admin mặc định ngay sau khi cài đặt Web UI.
- Backup định kỳ database Traccar.

## 5. Cấu hình Device (do bộ phận nghiệp vụ quản lý, IT chỉ cần biết luồng)

Mỗi thiết bị/nhân viên cần được tạo trong Traccar với một `uniqueId` (Device ID) — đây là giá trị app Android sẽ dùng khi gửi vị trí. Việc tạo device thực hiện qua Web UI (`http://<server>:8082`) hoặc REST API, **không thuộc phạm vi setup hạ tầng của IT**, nhưng IT cần đảm bảo Web UI truy cập được cho người quản trị (ví dụ HR/AI Crew) để tạo/quản lý device.

## 6. Endpoint app sẽ gọi (để IT xác nhận đã sẵn sàng)

Sau khi setup xong, endpoint app Android sẽ gọi có dạng:

```
https://<domain-noi-bo>:<port>/?id=<device_id>&lat=<lat>&lon=<lon>&timestamp=<unix_time>&speed=<speed>
```

IT cần xác nhận:
- [ ] Domain/IP nội bộ đã cấu hình DNS (nếu dùng domain) và có thể resolve từ mạng nhân viên (Wi-Fi văn phòng / VPN).
- [ ] Port đã mở đúng, test được bằng `curl` hoặc Postman từ máy trong mạng nội bộ.
- [ ] HTTPS hoạt động, chứng chỉ hợp lệ (không bị cảnh báo self-signed nếu dùng CA nội bộ đã cài vào máy/app).
- [ ] Đã tạo được ít nhất 1 device test trên Web UI để phía dev test kết nối từ app.

## 7. Câu hỏi cần IT xác nhận trước khi dev bắt đầu tích hợp

1. Server sẽ có domain nội bộ hay chỉ dùng IP tĩnh?
2. Có sẵn CA nội bộ để cấp chứng chỉ TLS không, hay cần dùng self-signed?
3. Nhân viên truy cập server này qua Wi-Fi văn phòng, VPN, hay cả hai? (ảnh hưởng tới việc app hoạt động khi nhân viên ở ngoài văn phòng)
4. Database dự kiến dùng là gì (PostgreSQL có sẵn hay cần IT cấp mới)?
5. Ai sẽ là người vận hành/patch Traccar server định kỳ về sau?

---
*Tài liệu này mô tả yêu cầu kỹ thuật để IT triển khai server; phần phát triển app Android và cấu hình device/nghiệp vụ được thực hiện bởi đội dự án.*
