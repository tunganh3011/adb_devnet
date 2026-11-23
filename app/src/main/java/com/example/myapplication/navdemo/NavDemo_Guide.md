# Hướng dẫn sử dụng Navigation Graph với Fragment

Tài liệu này hướng dẫn cách xây dựng luồng Login đơn giản sử dụng **Navigation Component** (Fragment-based) thay vì Jetpack Compose.

## 1. Cấu trúc thư mục
Tất cả code liên quan nằm trong gói: `com.example.myapplication.navdemo`
- `NavDemoActivity.kt`: Activity chính chứa NavHost.
- `LoginFragment.kt`: Màn hình nhập tài khoản.
- `SuccessFragment.kt`: Màn hình thông báo thành công.

## 2. Các thành phần chính

### a. Navigation Graph (`res/navigation/nav_graph_demo.xml`)
Đây là file XML định nghĩa "bản đồ" điều hướng của ứng dụng:
- **Destinations**: Khai báo các Fragment tham gia vào luồng.
- **Actions**: Định nghĩa đường nối giữa các màn hình. Ví dụ: `action_loginFragment_to_successFragment`.
- **Start Destination**: Điểm bắt đầu (màn hình Login).

### b. NavHost (`activity_nav_demo.xml`)
Trong layout của Activity, chúng ta sử dụng `FragmentContainerView` với các thuộc tính:
- `android:name="androidx.navigation.fragment.NavHostFragment"`: Biến container này thành vật chứa cho Navigation.
- `app:navGraph="@navigation/nav_graph_demo"`: Liên kết với file bản đồ đã tạo.

### c. Điều hướng trong Code
Để chuyển màn hình, chúng ta sử dụng `findNavController()`:
```kotlin
// Từ Login chuyển sang Success
findNavController().navigate(R.id.action_loginFragment_to_successFragment)

// Quay lại màn hình trước
findNavController().popBackStack()
```

## 3. Cách chạy Demo
Để chạy thử phần này, bạn cần sửa `AndroidManifest.xml` để `NavDemoActivity` làm Activity khởi đầu, hoặc gọi nó từ màn hình hiện tại:

```kotlin
val intent = Intent(this, NavDemoActivity::class.java)
startActivity(intent)
```

**Tài khoản dùng thử:**
- Username: `admin`
- Password: `123`

## 4. Kỹ thuật nhảy qua màn hình (Skip Screens)
Trong demo này, luồng đi là: **Login (1) -> Success (2) -> Info (3)**.

Để từ **Info (3)** quay thẳng về **Login (1)** mà không đi qua **Success (2)**:
Sử dụng phương thức `popBackStack` với ID của màn hình đích:
```kotlin
// Trong InfoFragment.kt
findNavController().popBackStack(R.id.loginFragment, false)
```
- `R.id.loginFragment`: ID của màn hình bạn muốn quay về.
- `false`: Có xóa luôn màn hình đích đó hay không (thường là `false` để giữ lại).

## 5. Phân biệt popBackStack vs popUpTo

| Tiêu chí | `popBackStack` | `popUpTo` (trong Action) |
| :--- | :--- | :--- |
| **Mục đích** | Quay lại màn hình cũ. | Đi tới màn hình mới + dọn dẹp stack. |
| **Sử dụng** | Khi nhấn nút "Hủy" hoặc "Back". | Khi xong một luồng (VD: Login xong). |
| **Kết quả** | Giữ nguyên trạng thái màn hình cũ. | Thường tạo mới màn hình đích. |

**Quy tắc vàng:** 
- Muốn **Quay lại**: Dùng `popBackStack`.
- Muốn **Đi tiếp** mà không cho quay lại: Dùng `popUpTo` trong Action.

## 6. Lợi ích của NavGraph
- Quản lý Backstack tự động và linh hoạt.
- Hình ảnh hóa luồng đi của app (Visual Editor).
- Dễ dàng thêm hiệu ứng chuyển cảnh (Animations).
- Hỗ trợ truyền dữ liệu (Safe Args) an toàn.
