# Stable Manual APK Updates Design

## Trạng thái và mục tiêu

Thiết kế được người dùng duyệt ngày 2026-08-14. Mục tiêu là phát hành APK cập nhật thủ công có chữ ký ổn định, cập nhật đè ứng dụng đang cài mà giữ nguyên Room database, cấu hình, PIN và lịch sử GPS.

Bản đầu tiên theo cơ chế này là:

- `versionName = "2.0.0"`
- `versionCode = 2`
- package/application ID `com.internal.tracker`

Phạm vi chỉ gồm APK được chuyển thủ công tới điện thoại và cài bằng Android Package Installer. Không thêm kiểm tra phiên bản, tải APK hoặc tự cài đặt từ Internet bên trong ứng dụng.

## Hiện trạng đã xác minh

Ứng dụng hiện dùng `versionName = "1.0"`, `versionCode = 1` và chỉ cấu hình debug signing mặc định. APK cục bộ đang được phân phối có chứng thư:

`8F:19:12:A3:4E:D2:CB:9D:DF:88:40:DB:49:A7:69:13:42:51:B3:29:74:84:33:36:78:E2:C6:79:CA:E4:F5:85`

Chứng thư trong `app/build/outputs/apk/debug/app-debug.apk` đã được đối chiếu và khớp chính xác với khóa tại `.tools/android-home/debug.keystore`. APK đang cài trên điện thoại được build từ chính máy này, vì vậy giữ nguyên signing identity trên sẽ cho phép bản `2.0.0 (2)` cập nhật đè mà không gỡ ứng dụng.

GitHub Actions hiện tạo debug APK trên runner tạm. Artifact đó không được xem là APK cập nhật chính thức vì debug keystore của runner không ổn định giữa các môi trường.

## Kiến trúc signing

Khóa hiện tại được nhập một lần sang kho phát hành riêng:

- Keystore: `.signing/tracker-release.p12`
- Cấu hình bí mật: `.signing/signing.properties`
- Alias: `tracker-release`
- Certificate fingerprint: giá trị SHA-256 cố định đã nêu trên

Thư mục `.signing/` phải được thêm vào `.gitignore`. Không commit keystore, mật khẩu, signing properties hoặc APK đầu ra.

Keystore PKCS#12 dùng mật khẩu mạnh được tạo cục bộ. Script setup không in mật khẩu ra stdout hoặc ghi mật khẩu vào log. `signing.properties` giữ đường dẫn tương đối tới keystore, alias, store password và key password; file chỉ phục vụ Gradle trên máy phát hành và phải được sao lưu cùng keystore vào nơi bảo mật.

Việc nhập sang PKCS#12 chỉ đổi cách lưu và mật khẩu bảo vệ trên máy build; signing certificate/private key vẫn là identity cũ để Android chấp nhận update. Bản sao debug keystore cũ có mật khẩu mặc định vẫn là một rủi ro kế thừa và phải được bảo vệ như khóa phát hành. Không xoay sang khóa mới trong phạm vi này vì ưu tiên tương thích cập nhật trên toàn bộ Android 10+; tài liệu Android không khuyến nghị dựa vào key rotation cho Android 12 trở xuống.

## Cấu hình Gradle và version

`app/build.gradle.kts` đặt `versionName = "2.0.0"` và `versionCode = 2`.

Release signing chỉ được cấu hình từ `.signing/signing.properties`. Khi người vận hành yêu cầu release build mà file, keystore, alias hoặc mật khẩu bị thiếu/sai, build phải dừng với lỗi rõ ràng. Release build không được fallback sang debug signing hoặc tự tạo khóa mới.

Build phải có một verification gate kiểm tra chứng thư của APK đầu ra khớp fingerprint cố định. Fingerprint là dữ liệu công khai và có thể nằm trong source/script; private key và password thì không.

Các bản phát hành sau áp dụng:

- Tăng `versionCode` ít nhất 1 cho mỗi APK đã phân phối.
- Dùng `versionName` dạng `MAJOR.MINOR.PATCH`.
- Không phát hành hai nội dung APK khác nhau với cùng `versionCode`.
- Không hỗ trợ downgrade vì migration database ngược không được thiết kế.

## Công cụ phát hành

### Chuẩn bị khóa một lần

Thêm `scripts/prepare-release-signing.ps1` với trách nhiệm:

1. Xác nhận source keystore tồn tại tại đường dẫn do tham số cung cấp, mặc định `.tools/android-home/debug.keystore`.
2. Đọc certificate fingerprint trước khi nhập và từ chối nếu không khớp fingerprint đã duyệt.
3. Tạo `.signing/` và mật khẩu mạnh bằng bộ sinh số ngẫu nhiên mật mã.
4. Nhập cùng private key/certificate sang `.signing/tracker-release.p12` với alias `tracker-release`.
5. Ghi `.signing/signing.properties` mà không in bí mật ra terminal.
6. Đọc lại keystore đích, xác minh alias và fingerprint.
7. Nếu file đích đã tồn tại, dừng và yêu cầu người vận hành dùng bản hiện có; không ghi đè khóa phát hành.

Script không xóa source debug keystore. Xóa hoặc di chuyển khóa là thao tác vận hành riêng vì có thể làm mất khả năng build/update.

### Build bản cập nhật

Thêm `scripts/build-release-apk.ps1` với trách nhiệm:

1. Kiểm tra `.signing/signing.properties` và keystore tồn tại.
2. Chạy unit tests, Android lint và `assembleRelease`.
3. Dùng Android build tools để xác minh chữ ký APK.
4. Kiểm tra package, `versionCode`, `versionName` và certificate fingerprint.
5. Sao chép APK đạt chuẩn sang `dist/tracking-gps-2.0.0.apk`.
6. In đường dẫn APK, version và fingerprint; không in secret.

`dist/` bị Git bỏ qua. Nếu bất kỳ bước nào thất bại, script trả exit code khác 0 và không công bố APK như một bản phát hành hợp lệ.

## CI và phân phối

GitHub Actions tiếp tục chạy unit tests và lint trên pull request. Workflow không được quảng bá debug artifact là APK cập nhật chính thức. Trong phạm vi này, release APK chỉ được build trên máy đang giữ `.signing/`.

Không tải keystore hoặc signing password lên GitHub Secrets trong lần triển khai này. Có thể bổ sung signed-release workflow sau bằng một thiết kế riêng nếu cần phát hành từ CI.

Quy trình phân phối:

1. Sao lưu `.signing/tracker-release.p12` và `.signing/signing.properties` vào nơi bảo mật trước lần phát hành đầu tiên.
2. Chạy `scripts/build-release-apk.ps1`.
3. Chuyển `dist/tracking-gps-2.0.0.apk` tới điện thoại.
4. Mở APK và chọn **Cập nhật**.
5. Mở app một lần sau update để reconcile foreground tracking và lịch báo cáo.
6. Xác nhận cấu hình, PIN, History/Room data và tracking state còn nguyên.

Nếu Android báo xung đột chữ ký, dừng cài đặt và đối chiếu fingerprint; không gỡ app trước khi sao lưu/đánh giá dữ liệu.

## Kiểm thử và tiêu chí hoàn thành

Kiểm thử tự động/script phải chứng minh:

- Release build thất bại khi thiếu signing config.
- Setup từ chối source keystore có fingerprint sai và không ghi đè keystore đích.
- APK release có package `com.internal.tracker`.
- APK release có version `2.0.0 (2)`.
- APK release có chữ ký hợp lệ và SHA-256 fingerprint đúng.
- Unit tests, lint và release build thành công.
- Git không theo dõi `.signing/`, `dist/`, keystore, signing properties hoặc APK.

Kiểm thử thiết bị là acceptance gate bắt buộc trước khi phân phối rộng:

- Cài APK release bằng `adb install -r` hoặc Android Package Installer lên một điện thoại đang có bản `1.0 (1)` được ký bằng fingerprint hiện tại.
- Không gỡ ứng dụng trong quá trình kiểm thử.
- Xác nhận update thành công và package data không bị xóa.
- Xác nhận Room history, cấu hình Gmail/thiết bị/chu kỳ, PIN và trạng thái tracking còn nguyên.
- Mở app để xác nhận service/lịch báo cáo được reconcile.

Nếu không có thiết bị kết nối trong phiên build, các kiểm tra APK tĩnh vẫn được chạy nhưng acceptance update phải được báo là chưa xác minh, không được ghi là đã hoàn thành.

## Khôi phục và giới hạn

Mất private key đồng nghĩa không thể cập nhật các thiết bị đã cài bằng identity này. Backup keystore và properties phải được thử khôi phục bằng cách build một APK kiểm tra, không chỉ xác nhận file backup tồn tại.

Không thể nâng mức bảo mật của private key cũ chỉ bằng cách đổi mật khẩu PKCS#12, vì bản debug keystore nguồn vẫn chứa cùng private key. Giới hạn này được chấp nhận để tránh gỡ app/mất dữ liệu. Việc chuyển sang signing identity hoàn toàn mới cần kế hoạch migration hoặc key-rotation riêng và không thuộc phạm vi thiết kế này.
