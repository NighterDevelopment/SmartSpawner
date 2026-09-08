# SmartSpawner Storage Performance & Architecture Benchmark Report

Báo cáo so sánh chi tiết hiệu năng và kiến trúc lưu trữ giữa nhánh **`main`** (cũ) và nhánh **`feature/native-take-storage`** (mới).

---

## 1. Tổng quan kiến trúc (Architecture Overview)

### Nhánh `main` (Cũ): Full Repaint & Immediate Compacting
- **Click Model**: Huỷ toàn bộ tương tác (`event.setCancelled(true)`), tự tính toán số lượng lấy bằng code, chuyển item vào túi đồ người chơi bằng tay (`transferToPlayerInventory`).
- **Cập nhật hiển thị (Display)**: Sau mỗi lần click lấy đồ, server xoá sạch 45 slot (`setItem(null)` x 45), truy vấn lại `VirtualInventory.getDisplayPage(page, 45)`, khởi tạo lại các đối tượng `ItemStack` mới và ghi đè vào 45 slot.
- **Dồn item (Compacting)**: Tự động dồn item lên các slot đầu ngay lập tức trên mỗi lần nhấp chuột, làm item nhảy vị trí liên tục trước mắt người chơi và tạo ra lượng packet cập nhật slot khổng lồ gửi về client.

### Nhánh `feature/native-take-storage` (Mới): Native Take & Deferred Compacting
- **Click Model**: Cho phép tương tác native của Minecraft (`event.setCancelled(false)` cho slot 0..44), client tự xử lý nhấc item, tách stack, kéo thả mượt mà không bị khựng. Các nút điều khiển (slot 45..53) được khoá (`setCancelled(true)`), chặn shift-click item lạ từ túi vào kho.
- **Đối soát thay đổi (Diff Reconciliation)**: Hàm `reconcileStoragePage` sử dụng `ItemStack.isSimilar()` để so sánh 45 slot thực tế với mảng cache trong `StorageSession`. Chỉ trừ/cộng đúng phần chênh lệch vào `VirtualInventory` và cập nhật nút Bán (slot 49). Không xoá slot, không vẽ lại 45 slot.
- **Hoãn dồn item (Deferred Compacting)**: Khi người chơi lấy đồ, các ô trống được giữ nguyên tại chỗ nhờ `StorageSession`. Chỉ khi **tất cả người chơi đóng GUI** (`viewers == 0`), session mới kết thúc. Lần mở GUI kế tiếp sẽ đọc tuần tự từ `VirtualInventory.getDisplayPage()`, tự động dồn item gọn gàng mà không bao giờ làm mất đồ.

---

## 2. Kết quả đo lường hiệu năng (Benchmark Results)

Toàn bộ benchmark được thực thi thông qua lệnh `/ss benchmark` trên nền tảng **Paper 1.21.11+ (Build 26.2) / Java 25** dưới cùng điều kiện phần cứng và môi trường máy chủ.

### Benchmark 1: Tốc độ đọc và dựng trang kho (Display Page Retrieval)
*Đo thời gian đọc và chuyển đổi dữ liệu từ kho ảo ra trang hiển thị ở các quy mô kho khác nhau.*

| Quy mô kho | Nhánh `main` (Uncached) | Nhánh `feature/native-take-storage` (Session) | Cải thiện độ trễ | Cải thiện thông lượng |
| :--- | :--- | :--- | :--- | :--- |
| **Small (5 items)** | 1.81 μs (553,073 op/s) | **0.16 μs (6,438,321 op/s)** | **11.3x nhanh hơn** | **+1,064%** |
| **Medium (45 items - 1 trang full)** | 1.60 μs (626,480 op/s) | **0.12 μs (8,179,959 op/s)** | **13.3x nhanh hơn** | **+1,206%** |
| **Large (200 items - ~5 trang)** | 1.79 μs (559,068 op/s) | **0.12 μs (8,283,632 op/s)** | **14.9x nhanh hơn** | **+1,381%** |
| **Massive (1,000 items - ~23 trang)** | 1.04 μs (962,773 op/s) | **0.12 μs (8,134,490 op/s)** | **8.7x nhanh hơn** | **+744%** |

> **Phân tích**: Nhánh `main` phải duyệt qua Map, tính toán chia stack và khởi tạo đối tượng `ItemStack` mới ở mỗi tick hiển thị. Nhánh mới tận dụng `StorageSession` đọc thẳng từ mảng `ItemStack[]` đã cache, đạt trên **8 triệu lượt truy xuất/giây**.

---

### Benchmark 2: Chu trình lấy đồ liên tục (1,000 Consecutive Takes)
*Mô phỏng chuỗi 1,000 thao tác nhấp chuột lấy item liên tục từ kho chứa 45 slot.*

| Chỉ số đo lường | Nhánh `main` (Full Repaint / Auto-compact) | Nhánh `feature/native-take-storage` (Native Take) | Mức độ cải thiện |
| :--- | :--- | :--- | :--- |
| **Tổng thời gian (1,000 lượt)** | 26.20 ms | **7.28 ms** | **3.60x nhanh hơn** |
| **Độ trễ trung bình (Average Latency)** | 26.20 μs | **7.28 μs** | **3.60x nhanh hơn** |
| **Độ trễ trung vị (P50 Median)** | 19.00 μs | **0.10 μs** | **190.0x nhanh hơn** |
| **Độ trễ phân vị 95 (P95)** | 41.40 μs | **0.20 μs** | **207.0x nhanh hơn** |
| **Độ trễ phân vị 99 (P99)** | 123.20 μs | **128.00 μs** | Tương đương (JIT/GC boundary) |
| **Thông lượng (Throughput)** | 38,163 takes/giây | **137,334 takes/giây** | **+260% (+3.60x)** |
| **Số lần ghi slot (Inventory Writes)** | **91,000 lần ghi** | **90 lần ghi** | **Giảm 99.90% lần ghi!** |

> **Ý nghĩa thực tế về Mạng & Trải nghiệm**:
> - Nhánh `main` ghi tới **91,000 lần vào inventory** (45 lần xoá + 45 lần đặt lại + 1 nút cho mỗi click). Lượng packet đồng bộ slot liên tục làm chớp inventory và gây hiện tượng rubberband (giật trả item).
> - Nhánh `feature/native-take-storage` giảm xuống chỉ còn **90 lần ghi** (chỉ cập nhật nút bán và slot vừa lấy). Giảm **99.90%** tải mạng, triệt tiêu hoàn toàn hiện tượng chớp/giật GUI.

---

### Benchmark 3: Hoạt động đồng thời (500 Loot Adds + 500 Hopper Takes)
*Mô phỏng spawner vừa sinh loot vừa có hopper hút đồ trong lúc người chơi đang mở xem GUI.*

| Chỉ số | Nhánh `main` (Full Repaint) | Nhánh `feature/native-take-storage` (Session Sync) | Mức độ cải thiện |
| :--- | :--- | :--- | :--- |
| **Tổng thời gian (1,000 ops)** | 15.80 ms | **4.43 ms** | **3.57x nhanh hơn** |
| **Độ trễ trung bình mỗi op** | 15.80 μs | **4.43 μs** | **3.57x nhanh hơn** |
| **Thông lượng (Throughput)** | 63,284 ops/giây | **225,774 ops/giây** | **3.57x cao hơn** |

---

## 3. Giải thích về Thông lượng (Throughput) và Độ trễ (Latency)

- **Thông lượng (Throughput - ops/s, takes/s)**: Là số lượng tác vụ server xử lý được trong **1 giây**. **CÀNG CAO CÀNG TỐT**.
  - Nhánh mới đạt **137,334 takes/s** so với 38,163 takes/s của nhánh cũ, cho phép server chịu tải được nhiều người chơi spam click cùng lúc mà không bị tụt tick (TPS).
- **Độ trễ (Latency - μs, ms)**: Là thời gian hoàn thành 1 thao tác. **CÀNG THẤP CÀNG TỐT**.
  - P50 (50% thao tác thông thường) giảm từ **19.00 μs xuống 0.10 μs** (nhanh gấp 190 lần), phản hồi tức thì cho người chơi.

---

## 4. Dữ liệu thô (Raw Benchmark Logs)

Dữ liệu thô xuất ra từ server console được lưu trữ tại:
- [`benchmark_results_main.txt`](./benchmark_results_main.txt): Kết quả đo trên nhánh `main`.
- [`benchmark_results_feature_branch.txt`](./benchmark_results_feature_branch.txt): Kết quả đo trên nhánh `feature/native-take-storage`.