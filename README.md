# GPS Email Pilot

Ung dung noi bo cho Android 10+, lay mot diem GPS theo lich 6h/12h/24h, luu lich su/CSV tren dien thoai va gui mot email tong hop qua Gmail SMTP.

## Build tren Windows

Yeu cau: JDK 17, Android SDK Platform 36 va Build Tools 36.0.0.

1. Tao `gmail-secrets.properties` tai thu muc goc tu mau `docs/gmail-build-secrets.example.properties`.
2. Dien Gmail gui rieng va App Password 16 ky tu. Khong commit file nay.
3. Chay:

```powershell
./gradlew.bat clean testDebugUnitTest lintDebug assembleDebug --no-daemon
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.

## Su dung

1. Mo app bang PIN test mac dinh `18758691`.
2. Trong `Cau hinh`, nhap so thiet bi `001`-`100`, email nhan, chu ky va Gmail gui.
3. App Password de trong neu giu gia tri da luu; nhap ma moi de thay doi.
4. Bam `Luu va kiem tra`, cap quyen vi tri nen, sau do `Bat dau theo doi`.
5. Xem va chia se CSV tai `Lich su`.

Gio gui la khoang du kien do Android Doze co the tri hoan WorkManager. Chi tiet van hanh: `docs/periodic-gmail-pilot-handover.md`.
