# DevNet ADB

DevNet ADB là ứng dụng Android hỗ trợ kiểm tra kết nối ADB qua Wi-Fi, theo dõi chất lượng mạng LAN và ghi lại thông tin khi tái hiện lỗi.

## Các module

| Module | Mục đích |
| --- | --- |
| `:devnet` | Ứng dụng DevNet ADB với package `com.devnet.adbcompanion`. |
| `:app` | Ứng dụng demo chụp và cắt ảnh giấy tờ với package `com.example.myapplication`. |

## Build và chạy

Cài JDK 21 và Android SDK Platform API 37. Project dùng Gradle Wrapper 9.4.1 và hỗ trợ cài đặt từ Android 7.0 (API 24). Có thể mở project trong Android Studio và chọn cấu hình chạy `devnet`.

```bash
./gradlew :devnet:assembleDebug
./gradlew :devnet:testDebugUnitTest
```

Khi thiết bị đã kết nối ADB, chạy lệnh sau để cài bản debug.

```bash
./gradlew :devnet:installDebug
```

APK debug nằm ở `devnet/build/outputs/apk/debug/devnet-debug.apk`. Trên Windows, dùng `gradlew.bat` để chạy các task tương ứng.

## Tính năng DevNet

- **ADB & Kết nối** hiển thị trạng thái Wireless Debugging và ADB TCP/IP. Khi phát hiện cổng kết nối, app cho phép sao chép địa chỉ. Wireless Debugging có từ Android 11. Nếu thiết bị chưa cung cấp địa chỉ, mở màn **Gỡ lỗi không dây** của hệ thống để xem IP và cổng.
- **LAN & scrcpy** đo ping, jitter và tỷ lệ mất gói, đồng thời gợi ý cấu hình scrcpy. Trong mục **Ghi phiên debug**, có thể bắt đầu ghi, đánh dấu lúc gặp lỗi, xem lại và sao chép báo cáo. Báo cáo nằm trong bộ nhớ và ẩn SSID, BSSID cùng địa chỉ mạng.
- **Private DNS** cấu hình hostname DNS ở cấp hệ thống, mặc định là `dns.adguard-dns.com`. Hiệu quả lọc phụ thuộc vào DNS đã chọn. Private DNS có từ Android 9.
- **Chẩn đoán và theo dõi** hiển thị thông tin Wi-Fi, VPN, proxy và pin. Có thể bật HUD nổi, thông báo trạng thái hoặc thêm ô ADB Wi-Fi và AdGuard DNS vào Cài đặt nhanh.

## Quyền điều khiển ADB và Private DNS

Các thao tác đổi cài đặt trực tiếp trong DevNet cần `WRITE_SECURE_SETTINGS`. Trên thiết bị phát triển của bạn đã kết nối ADB, có thể cấp quyền cho ứng dụng bằng lệnh sau.

```bash
adb shell pm grant com.devnet.adbcompanion android.permission.WRITE_SECURE_SETTINGS
```

Nếu thiết bị giới hạn quyền này, đổi thiết lập trong Cài đặt Android. HUD và thông báo cần quyền tương ứng của hệ thống.
