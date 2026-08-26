# Continuous Adaptive GPS Tracking Design

## Mục tiêu

Chuyển ứng dụng từ cơ chế ReportWorker chụp một vị trí tại mỗi kỳ báo cáo sang hai luồng độc lập:

- Foreground service theo dõi vị trí liên tục, ghi sự kiện hành trình vào Room.
- ReportWorker chạy theo lịch 6h, 12h hoặc 24h, gửi một email CSV chứa các record đã hoàn tất nhưng chưa gửi.

Ứng dụng phải tiếp tục ghi nhận khi chạy nền, phục hồi sau khi tiến trình hoặc thiết bị khởi động lại, và không gửi email trực tiếp từ foreground service.

## Quyết định sản phẩm

- Quan sát vị trí mỗi 10 giây khi tracking bật.
- Khi xe đang di chuyển, lưu một record định kỳ mỗi 2 phút.
- Lưu thêm record khi bắt đầu di chuyển và khi bắt đầu dừng.
- Điểm dừng dưới 2 phút là `TEMP_STOP`; từ 2 phút trở lên được nâng cấp thành `STOP`.
- Không loại bỏ vị trí vì timestamp cũ hoặc accuracy lớn. App lưu nguyên timestamp, tọa độ và accuracy; backend quyết định cách sử dụng chất lượng dữ liệu.
- Giữ định dạng CSV. CSV thêm cột `record_type` với `START`, `PERIODIC`, `TEMP_STOP` hoặc `STOP`.
- Backend chịu trách nhiệm phân tích các lần dừng giữa lộ trình.

## Kiến trúc

### TrackingService

`TrackingService` là foreground service loại `location`. Service được khởi động khi người dùng bật tracking và dừng khi người dùng xác nhận PIN để tắt tracking.

Service đăng ký `FusedLocationProviderClient` với chu kỳ mong muốn 10 giây. Khi detector ở `IDLE`, request dùng `PRIORITY_BALANCED_POWER_ACCURACY`; khi ở `MOVING` hoặc `STOP_CANDIDATE`, request chuyển sang `PRIORITY_HIGH_ACCURACY`. Android có thể giao callback sớm hoặc muộn hơn; app xử lý timestamp thực tế của từng `Location`, không giả định callback chính xác tuyệt đối mỗi 10 giây.

Service chuyển mỗi vị trí sang model trung lập rồi đưa vào `MovementDetector`. Service chỉ chịu trách nhiệm lifecycle, notification, permission và ghi kết quả detector qua repository; nó không chứa logic SMTP hoặc tạo CSV.

Khi tracking vẫn bật nhưng service được Android tạo lại, service khôi phục detector từ trạng thái đã lưu và đăng ký location updates lại. `BOOT_COMPLETED`, thay đổi thời gian và thay đổi múi giờ đều gọi luồng reconcile để khôi phục service và lịch báo cáo.

### Activity Recognition và fallback

Ứng dụng đăng ký Activity Recognition Transition API cho trạng thái `IN_VEHICLE`. Tín hiệu này hỗ trợ xác nhận bắt đầu/kết thúc di chuyển và giảm nhầm do dừng đèn đỏ.

Android 10+ yêu cầu quyền runtime `ACTIVITY_RECOGNITION`. Nếu người dùng từ chối quyền, Google Play services không hỗ trợ hoặc đăng ký transition thất bại, tracking vẫn tiếp tục bằng tốc độ và khoảng cách GPS. Thiếu Activity Recognition không được làm nút bắt đầu tracking bị vô hiệu hóa.

### MovementDetector

`MovementDetector` là thành phần Kotlin thuần, không phụ thuộc Android, để kiểm thử tất định. Detector nhận vị trí, timestamp và tín hiệu activity; kết quả là một hoặc nhiều hành động lưu/cập nhật record.

Trạng thái:

- `IDLE`: chưa xác nhận xe đang chạy.
- `MOVING`: xe đang chạy; áp dụng nhịp lưu 2 phút.
- `STOP_CANDIDATE`: đã tạo một record dừng tạm và đang chờ đủ 2 phút hoặc xe chạy lại.

Ngưỡng mặc định:

- Bắt đầu chạy khi Activity Recognition vào `IN_VEHICLE`, hoặc hai vị trí liên tiếp có tốc độ từ 5 km/h trở lên.
- Bắt đầu ứng viên dừng khi tốc độ dưới 3 km/h và vị trí không dịch chuyển quá 30 m so với điểm neo dừng.
- Chỉ nâng cấp thành `STOP` khi điều kiện đứng yên duy trì đủ 120 giây theo timestamp của vị trí.
- Khi `MOVING`, lưu `PERIODIC` nếu đã đủ 120 giây kể từ record hành trình được lưu gần nhất.

Accuracy và tuổi dữ liệu không phải điều kiện loại bỏ. Detector vẫn sử dụng timestamp, speed và khoảng cách của dữ liệu nhận được, còn record lưu đầy đủ accuracy để backend đánh giá.

### TEMP_STOP lifecycle

Khi detector chuyển từ `MOVING` sang `STOP_CANDIDATE`:

1. Tạo ngay record `TEMP_STOP` với timestamp và tọa độ lúc bắt đầu đứng yên.
2. Đánh dấu record là chưa hoàn tất (`isFinalized = false`). Record tồn tại trong Room để không mất khi tiến trình chết, nhưng không được ReportWorker chọn gửi.
3. Nếu xe chạy lại trước 120 giây, giữ loại `TEMP_STOP`, chuyển `isFinalized = true`, rồi quay lại `MOVING`.
4. Nếu xe tiếp tục đứng yên đủ 120 giây, cập nhật cùng record thành `STOP`, chuyển `isFinalized = true`, rồi vào `IDLE`.

Cơ chế này không tạo hai record trùng cho cùng một điểm dừng và không gửi một trạng thái tạm thời rồi sửa sau khi email đã phát đi.

Nếu người dùng chủ động dừng tracking trong lúc có ứng viên dừng, record được hoàn tất dưới dạng `TEMP_STOP`; app không tự suy diễn thành `STOP` khi chưa đủ 120 giây. Nếu tiến trình chết ngoài ý muốn, record vẫn chưa hoàn tất để service phục hồi và đánh giá tiếp khi được tạo lại.

## Mô hình dữ liệu và repository

`LocationRecord` bổ sung:

- `recordType: RecordType`, enum gồm `START`, `PERIODIC`, `TEMP_STOP`, `STOP`.
- `isFinalized: Boolean`, mặc định `true`; chỉ ứng viên dừng đang chờ có giá trị `false`.

Room database tăng version và có migration gán record cũ là `PERIODIC`, `isFinalized = true` để bảo toàn dữ liệu hiện hữu.

DAO/repository bổ sung:

- `deleteOlderThan(beforeMillis: Long)` trong DAO.
- `deleteOlderThan(date: LocalDate)` trong repository, quy đổi đầu ngày bằng múi giờ thiết bị.
- `deleteBetween(from: Long, until: Long)` và `deleteAll()` cho History.
- `observeOldestCapturedAt()` để xây danh sách năm.
- Phương thức tạo ứng viên dừng và hoàn tất ứng viên theo id.

Truy vấn record chưa gửi chỉ trả record `isFinalized = true` có delivery state khác `SENT`, theo thứ tự `capturedAt, id`. Record đang chờ kết luận không được đưa vào email.

## ReportWorker và email

ReportWorker không lấy vị trí. Mỗi lần chạy:

1. Xóa record có `capturedAt` trước đầu ngày `LocalDate.now().minusYears(1)` theo múi giờ thiết bị.
2. Lấy các record đã hoàn tất và chưa gửi, theo thứ tự thời gian.
3. Nếu danh sách rỗng, không gửi email.
4. Tạo một CSV tổng hợp và gửi một email.
5. Chỉ sau khi SMTP trả về thành công mới cập nhật các record trong email thành `SENT` và đặt `sentAt`.
6. Nếu gửi thất bại, cập nhật record thành `RETRYING`; chúng được thử lại ở kỳ sau.
7. Luôn reconcile lịch tiếp theo trong `finally`.

Nếu cleanup thất bại, worker vẫn thử gửi báo cáo và công khai lỗi cleanup nếu không có lỗi gửi nghiêm trọng hơn. Giới hạn attachment hiện có tiếp tục được áp dụng; một lần chạy chỉ gửi một email, phần backlog vượt giới hạn giữ lại cho kỳ sau.

CSV giữ các cột hiện tại và thêm `record_type`. `TEMP_STOP` chỉ xuất hiện khi đã hoàn tất do xe chạy lại trước 2 phút.

## UI và bảo vệ PIN

### Điều hướng

- Cold start mở trực tiếp màn Status, không hiển thị màn hình hoặc dialog PIN.
- Status và History xem được mà không yêu cầu PIN.
- Khi chọn Settings lần đầu trong phiên app, hiển thị dialog PIN. Sau khi đúng PIN, Settings được mở khóa cho phần còn lại của phiên.
- Trạng thái mở khóa Settings chỉ thuộc phiên chạy, không lưu vĩnh viễn.

### Thao tác nhạy cảm

- Bấm “Dừng theo dõi” luôn mở dialog PIN riêng. Chỉ khi PIN đúng mới dừng service, tắt tracking và hủy lịch báo cáo.
- Xóa dữ liệu luôn theo hai bước: dialog mô tả chính xác phạm vi và cảnh báo không thể hoàn tác, sau đó dialog PIN.
- Việc Settings đã mở khóa không bỏ qua PIN của thao tác dừng hoặc xóa.

### Status và Settings

- Xóa `StatusRow("Device ID", container.deviceId.get())` khỏi Status.
- Device ID vẫn tồn tại trong Settings, Room và CSV.
- Settings hiển thị chu kỳ quan sát cố định 10 giây và chu kỳ lưu khi chạy 2 phút dưới dạng thông tin, không mở cấu hình khác với thiết kế đã duyệt.
- Chu kỳ email vẫn chọn 6h, 12h hoặc 24h.

### History

History có hai dropdown:

- Năm: `Tất cả` hoặc từ năm cũ nhất có dữ liệu đến năm hiện tại.
- Tháng: `Tất cả` hoặc 1–12.

Chọn tháng khi Năm đang là `Tất cả` tự chọn năm hiện tại. Bộ lọc dùng khoảng nửa mở `[đầu kỳ, đầu kỳ kế tiếp)` theo múi giờ thiết bị:

- Năm + Tháng: đúng tháng đã chọn.
- Năm + `Tất cả`: toàn bộ năm đã chọn.
- `Tất cả` + `Tất cả`: toàn bộ lịch sử.

Hai nút xóa:

- `Xóa theo bộ lọc`: xóa tháng khi đã chọn tháng, hoặc xóa cả năm khi Tháng là `Tất cả`. Nút bị vô hiệu hóa ở trạng thái `Tất cả` + `Tất cả`.
- `Xóa tất cả`: xóa toàn bộ database sau xác nhận và PIN.

Dialog phải nêu rõ phạm vi, ví dụ “Xóa dữ liệu tháng 08/2026?” hoặc “Xóa toàn bộ dữ liệu năm 2026?”.

## Quyền và manifest

Manifest bổ sung foreground service location và Activity Recognition:

- `FOREGROUND_SERVICE`
- `FOREGROUND_SERVICE_LOCATION`
- `ACTIVITY_RECOGNITION`
- Khai báo `TrackingService` với `android:foregroundServiceType="location"` và `android:exported="false"`.

Luồng permission hiện tại tiếp tục yêu cầu location và notification. Activity Recognition được giải thích và yêu cầu riêng; từ chối quyền chỉ kích hoạt fallback GPS.

## Xử lý lỗi

- Quyền vị trí bị thu hồi: service không crash, dừng đăng ký updates và ghi lỗi cho Status; khi quyền được cấp lại, reconcile khởi động lại updates.
- Lỗi ghi Room: lưu lỗi công khai và tiếp tục nhận callback kế tiếp.
- Lỗi Activity Recognition: chuyển sang fallback GPS.
- Lỗi SMTP: không đánh dấu `SENT`.
- Không có record đã hoàn tất chưa gửi: không tạo email rỗng.
- Record ứng viên dừng được lưu trong Room và có thể phục hồi sau process death.

## Kiểm thử

Unit test cho `MovementDetector` phải bao phủ:

- Chỉ bắt đầu bằng GPS sau hai xác nhận tốc độ từ 5 km/h.
- Activity Recognition `IN_VEHICLE` tạo `START`.
- Lưu `PERIODIC` khi đủ 120 giây và không lưu sớm hơn.
- Tạo `TEMP_STOP` khi bắt đầu đứng yên.
- Xe chạy lại trước 120 giây hoàn tất record dưới dạng `TEMP_STOP`.
- Đứng yên đủ 120 giây cập nhật cùng record thành `STOP`.
- Không tạo record STOP trùng lặp.
- Dữ liệu có accuracy lớn hoặc timestamp cũ không bị loại bỏ.
- Khôi phục đúng ứng viên dừng sau khi service được tạo lại.

Repository/DAO test phải bao phủ:

- Migration bảo toàn record cũ và gán mặc định mới.
- `deleteOlderThan` dùng đầu ngày đúng múi giờ.
- `observeBetween` và `deleteBetween` đúng ở biên tháng/năm.
- Record chưa hoàn tất không xuất hiện trong tập gửi.

Report/UI policy test phải bao phủ:

- Cleanup chạy trước delivery và lỗi cleanup không ngăn delivery.
- SMTP thành công/thất bại cập nhật delivery state đúng.
- PIN được yêu cầu cho Settings, dừng tracking và xóa dữ liệu.
- Status không có Device ID.
- Phạm vi lọc và xóa khớp lựa chọn Năm/Tháng.

Xác minh cuối cùng chạy unit tests, Android lint và build APK debug.

## Ngoài phạm vi

- Backend phân loại ý nghĩa nghiệp vụ của các điểm dừng giữa hành trình.
- App không tính tổng quãng đường chính thức.
- App không loại bỏ record dựa trên accuracy hoặc tuổi của từng location callback.
- Không đổi email attachment sang JSON.
