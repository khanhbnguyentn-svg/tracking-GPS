# Thiet ke thu nghiem GPS qua Cloudflare Quick Tunnel

Ngay: 2026-08-08  
Trang thai: Da duoc nguoi dung duyet ve huong giai phap

## 1. Muc tieu

Cho mot dien thoai Android 14+ gui GPS qua Internet den Internal GPS Receiver trong 2 ngay. Thu nghiem dung Cloudflare Quick Tunnel mien phi, khong mo port modem va khong yeu cau ten mien. Ket qua can xac nhan duoc viec gui lai khi mat mang, hien thi tren dashboard va van hanh lien tuc khi dien thoai chuyen giua Wi-Fi va du lieu di dong.

Quick Tunnel chi la cong cu thu nghiem. URL `trycloudflare.com` thay doi khi tien trinh tunnel khoi dong lai va Cloudflare khong cam ket uptime. May Windows va ket noi Internet phai duoc giu hoat dong trong suot thu nghiem.

## 2. Pham vi

Trong pham vi:

- Xac thuc request GPS bang mot Bearer token tam thoi cho pilot.
- Luu token trong EncryptedSharedPreferences tren Android.
- Ho tro token trong file cau hinh JSON dung chung.
- Tao va chay Quick Tunnel toi receiver tren may Windows.
- Tao file cau hinh pilot sau khi lay duoc URL tunnel.
- Cung cap lenh dung tunnel va vo hieu hoa token sau thu nghiem.
- Kiem thu tu dong va mot lan gui GPS that tu dien thoai.

Ngoai pham vi:

- Khong dung Quick Tunnel cho 100-350 dien thoai hoac production.
- Khong mo port router, khong cong khai PostgreSQL va khong thay doi port `5432`.
- Khong xay quy trinh phe duyet thiet bi, token rieng tung may hoac ten mien on dinh trong pilot.
- Khong cam ket URL ton tai sau khi may chu hoac `cloudflared` khoi dong lai.

## 3. Lua chon bao mat

Pilot dung header:

```http
Authorization: Bearer <pilot-token>
```

Khong dua token vao query string vi URL co the xuat hien trong log. Khong cong khai endpoint GPS khi chua bat xac thuc.

Token la 32 byte ngau nhien, ma hoa base64url. Ban plaintext chi ton tai trong file cau hinh pilot trong thoi gian chuyen sang dien thoai va trong bo nho tien trinh. Ban phia server duoc luu bang Windows DPAPI ngoai repository. Receiver so sanh token theo thoi gian hang dinh bang SHA-256 va `timingSafeEqual`.

Hai endpoint nhan vi tri `GET /` va `POST /api/locations` deu yeu cau token khi bien cau hinh pilot duoc bat. Thieu hoac sai token tra `401` voi ma `UNAUTHORIZED_DEVICE`. `/health` van khong can token. Dashboard va API quan tri tiep tuc dung tai khoan `admin`, session cookie va CSRF hien co.

## 4. Thay doi Android

File cau hinh JSON tang len phien ban 2 va them `ingestToken`. Decoder van doc phien ban 1 de khong lam mat profile LAN cu; phien ban 1 khong co token.

`ingestToken` duoc luu cung `host`, certificate pin va custom CA trong kho profile ma hoa. Token khong nam trong Room, log, crash message hoac giao dien sau khi import.

`OsmAndRequestFactory` them Bearer header khi profile co token. Ket noi HTTPS Quick Tunnel dung System CA va port `443`. HTTP `401` duoc phan loai thanh loi xac thuc de man hinh chan doan huong dan import lai file cau hinh, khong bao sai thanh loi mang.

File pilot co dang:

```json
{
  "version": 2,
  "name": "Internet pilot 2 ngay",
  "host": "random.trycloudflare.com",
  "port": 443,
  "scheme": "https",
  "intervalSeconds": 60,
  "tlsMode": "system",
  "ingestToken": "<generated-secret>"
}
```

Device ID van do dien thoai tu tao. PIC xac nhan Device ID tren dashboard; khong tao file rieng theo Device ID.

## 5. Thay doi receiver

Receiver nhan cau hinh token tu mot secret file DPAPI. Launcher giai ma secret khi khoi dong va chi dua gia tri vao environment cua tien trinh Node. Production config khong chua plaintext token.

Middleware xac thuc chi bao quanh hai route ingestion. Kiem tra token dien ra truoc rate limit, validate va ghi database, nen request sai khong the tao thiet bi hoac vi tri. Log va response khong lap lai token.

Installer va tai lieu van hanh duoc cap nhat de co the bat, thay va tat token. Khi token duoc bat, profile LAN cu khong co token se nhan `401`; file pilot phien ban 2 cung co the dung trong LAN neu doi lai host.

## 6. Quick Tunnel tren Windows

`cloudflared` duoc cai tu nguon chinh thuc mien phi va kiem tra checksum hoac chu ky so truoc khi chay. Script pilot:

1. Xac nhan receiver healthy tai `127.0.0.1:5055`.
2. Tao token neu chua co va bao ve ban server bang DPAPI.
3. Khoi dong `cloudflared tunnel --url http://127.0.0.1:5055` an cua so, ghi PID va log trong `D:\InternalGPS\Pilot`.
4. Doc URL HTTPS tu log voi timeout ro rang.
5. Tao file JSON pilot tai `D:\InternalGPS\Pilot\tracking-pilot-profile.json`.
6. Hien thi URL, duong dan file va trang thai tunnel ma khong in token.

Script chi dung dung PID da ghi, khong kill cac tien trinh `cloudflared` khac. Sau khi import vao dien thoai, file JSON plaintext phai duoc xoa khoi may Windows. Khi ket thuc pilot, dung tunnel, vo hieu hoa token va khoi dong lai receiver.

## 7. Xu ly loi

- `401`: token thieu/sai; app giu queue va bao cau hinh ket noi khong hop le.
- `404`, DNS hoac TLS: URL Quick Tunnel da doi/khong con hoat dong; app giu queue va yeu cau tao/import profile moi.
- `429`: tam dung gui va de WorkManager retry theo backoff hien co.
- Mat Internet may chu: tunnel mat ket noi; queue tren dien thoai khong bi xoa.
- Receiver/database loi: receiver tra loi khong thanh cong; app khong danh dau da gui.
- May Windows khoi dong lai: pilot khong tu dong tiep tuc voi URL cu; chay lai script va import file moi.

## 8. Kiem thu va tieu chi chap nhan

Kiem thu Android:

- Config v2 doc token hop le; tu choi token trong/rong/sai kieu.
- Token duoc luu trong kho ma hoa, khong nam trong `ProfileEntity`.
- Request HTTPS co Bearer header; profile v1 khong tu them header.
- `401` duoc phan loai la loi xac thuc va queue duoc giu lai.

Kiem thu receiver:

- Thieu/sai token tra `401`, khong tao device/position.
- Token dung chap nhan GET va POST.
- So sanh token khong dua secret vao log/response.
- `/health` va login van hoat dong.

Kiem thu Windows:

- Script khong in token, khong ghi token vao repository va chi dung PID cua pilot.
- Parser URL co timeout va tu choi hostname khong thuoc `trycloudflare.com`.
- Full Node, Pester va Android unit tests dat; APK debug build thanh cong.

Chap nhan thu nghiem that:

1. Import file pilot vao mot dien thoai Android 14+.
2. Gui diem GPS qua Wi-Fi va du lieu di dong, server tra chap nhan.
3. Dashboard hien dung Device ID va thoi gian nhan.
4. Tat mang dien thoai, tao diem cho, bat lai mang va xac nhan queue gui bu.
5. Theo doi trong 2 ngay; ghi lai moi lan tunnel/server/app bi gian do.
6. Ket thuc bang cach dung tunnel, vo hieu hoa token va xoa file cau hinh plaintext.

## 9. Huong production sau pilot

Neu pilot dat, thay Quick Tunnel bang named Cloudflare Tunnel co hostname on dinh. Token chung duoc thay bang luong dang ky: file chung chua enrollment code, dien thoai tu sinh secret, server tao thiet bi cho PIC phe duyet va moi thiet bi dung token rieng. Day la mot dac ta va ke hoach rieng.
