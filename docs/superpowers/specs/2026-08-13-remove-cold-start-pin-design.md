# Remove Cold-Start PIN Design

## Trạng thái

Thiết kế đã được người dùng duyệt ngày 2026-08-13. Tài liệu này thay thế phần yêu cầu PIN khi cold start trong `2026-08-13-continuous-adaptive-gps-tracking-design.md`.

Chưa triển khai code trong phiên tạo tài liệu này. Phiên tiếp theo bắt đầu bằng việc viết kế hoạch triển khai TDD.

## Mục tiêu

Bỏ lớp PIN đầu tiên khi mở ứng dụng để Status và History có thể được xem ngay, đồng thời giữ nguyên PIN bảo vệ các khu vực và thao tác nhạy cảm.

## Luồng điều hướng đã duyệt

- Mở ứng dụng đi thẳng vào màn Status; không hiển thị `PinScreen`.
- Status và History được xem tự do, không yêu cầu PIN.
- Setup/Cấu hình yêu cầu PIN ở lần truy cập đầu tiên trong mỗi phiên chạy của ứng dụng.
- Sau khi xác thực thành công, Setup/Cấu hình được mở khóa cho phần còn lại của phiên hiện tại.
- Trạng thái mở khóa Setup không được lưu vĩnh viễn.

## Thao tác vẫn yêu cầu PIN riêng

- Dừng tracking luôn yêu cầu nhập PIN, kể cả khi Setup đã được mở khóa.
- Xóa theo bộ lọc luôn yêu cầu bước xác nhận phạm vi, sau đó nhập PIN.
- Xóa tất cả luôn yêu cầu bước xác nhận, sau đó nhập PIN.
- Chức năng đổi PIN trong Setup được giữ nguyên và vẫn yêu cầu PIN hiện tại theo luồng đang có.

## Phạm vi thay đổi dự kiến

- Xóa `PinScreen` và trạng thái `unlocked` khỏi `TrackerApp`.
- Khởi tạo điều hướng trực tiếp tại `Destination.STATUS`.
- Loại bỏ `Destination.PIN` và policy chỉ phục vụ khóa toàn ứng dụng nếu không còn nơi sử dụng.
- Giữ nguyên `ProtectedAction.OPEN_SETTINGS`, `STOP_TRACKING`, `DELETE_FILTERED` và `DELETE_ALL`.
- Cập nhật unit test để xác nhận app luôn cho phép các destination chỉ đọc, trong khi Setup và thao tác nhạy cảm vẫn áp dụng đúng policy PIN.
- Cập nhật README, tài liệu vận hành và design/plan liên quan để không còn mô tả PIN đăng nhập khi mở app.

## Tiêu chí hoàn thành

- Cold start mở thẳng Status mà không có dialog hoặc màn hình PIN.
- History truy cập được mà không nhập PIN.
- Setup vẫn yêu cầu PIN lần đầu trong phiên và không hỏi lại trong cùng phiên sau khi xác thực đúng.
- Dừng tracking và mọi thao tác xóa vẫn yêu cầu PIN riêng mỗi lần.
- Đổi PIN vẫn hoạt động trong Setup.
- Unit test, lint và debug build thành công.

## Ngoài phạm vi

- Không thay đổi cách lưu hoặc kiểm tra PIN quản trị.
- Không thay đổi logic GPS, báo cáo email, database hoặc bộ lọc History.
- Không thêm cơ chế đăng nhập, tài khoản người dùng hoặc sinh trắc học.
