# Huong dan trien khai GPS Email Pilot

## 1. Pham vi

- Android 10 tro len, phan phoi APK noi bo.
- 100 thiet bi, danh so `001` den `100`; Device ID tu dong khong thay doi.
- Lich `6h`, `12h`, `24h`, neo gan 00:00 va dan deu trong 59 phut.
- Khong can Traccar, webserver, Cloudflare Tunnel hoac may tinh chay lien tuc.

## 2. Chuan bi Gmail

1. Tao Gmail rieng chi de gui bao cao.
2. Bat xac minh hai buoc tren tai khoan Gmail.
3. Tao App Password 16 ky tu.
4. Gmail mien phi co gioi han 500 thu/24 gio. Lich 6h voi 100 may tao toi da 400 thu theo lich, chua tinh retry.

Khong dung mat khau dang nhap Gmail thuong. Dien thoai khong phai xac minh hai buoc khi gui SMTP bang App Password.

## 3. Chuan bi may Windows moi

1. Cai Git, JDK 17 va Android Studio hoac Android command-line tools mien phi.
2. Cai Android SDK Platform 36 va Build Tools 36.0.0.
3. Clone repository va mo PowerShell tai thu muc du an.
4. Tao `gmail-secrets.properties`:

```properties
SMTP_USER=sender@gmail.com
SMTP_APP_PASSWORD=abcdefghijklmnop
```

File that bi `.gitignore`; khong dua credential len GitHub.

5. Build:

```powershell
./gradlew.bat clean testDebugUnitTest lintDebug assembleDebug --no-daemon
```

6. Lay APK tai `app/build/outputs/apk/debug/app-debug.apk`.

GitHub Actions cung build artifact `gps-email-pilot-debug`, nhung may tinh khong can bat sau khi `git push` thanh cong.

## 4. Cap phat dien thoai

1. Cai APK va mo bang PIN `18758691`.
2. Mo `Cau hinh`, nhap so thiet bi duy nhat, email nhan va chu ky.
3. Bam `Luu va kiem tra`. Neu App Password build san con dung, co the de trong o ma moi.
4. Cap vi tri chinh xac, `Luon cho phep` vi tri nen va thong bao khi duoc hoi.
5. Neu nut `Cap quyen thiet bi` mo Settings, chon quyen con thieu roi quay lai app.
6. Bam `Bat dau theo doi`.

Khong tat pin/toi uu nen cho app neu nha san xuat dien thoai co tuy chon rieng. WorkManager phuc hoi sau reboot, nhung Android/firmware co the tri hoan hoac dung tac vu; app hien thoi diem GPS/email cuoi va loi ky thuat gan nhat.

## 5. Du lieu va mat song

Moi lan chay, app ghi Room va CSV truoc khi thu gui. Khi mat mang, record giu `RETRYING`; ky sau gom backlog vao mot file CSV va gui mot email. Ban ghi `SENT` van duoc giu trong lich su.

Tai man `Lich su`, bam `Xuat toan bo CSV` de chia se ket qua test ma khong can webserver. File chi gom Device ID/so thiet bi, thoi gian, toa do, sai so GPS, pin, thoi gian theo doi va trang thai gui.

## 6. Doi credential

1. Tao App Password moi trong Gmail.
2. Mo app, nhap PIN, vao `Cau hinh`.
3. Dan ma 16 ky tu vao o App Password va bam `Luu va kiem tra`.
4. App chi thay credential cu sau khi Gmail chap nhan dang nhap.

## 7. Kiem thu con phai lam tren thiet bi

- Android 10, 12, 14+.
- Reboot va doi mui gio.
- GPS tat/quyen bi thu hoi.
- Doze va toi uu pin cua nha san xuat.
- Mat mang, tao backlog, sau do co mang lai.
- App Password sai/bi thu hoi va cap nhat ma moi.
- Lich 6h/12h/24h; thiet bi `001` va `100`.
- Mo/chia se CSV va doi chieu email nhan.

JVM unit test va lint khong thay the cac buoc thiet bi nay.
