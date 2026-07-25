# API Dành Cho Lập Trình Viên

SmartSpawner cung cấp Java API để đọc và chỉnh sửa spawner, lắng nghe sự kiện vòng đời và mở rộng hành vi plugin.

## Điều Hướng Nhanh

<CardGrid>

<DocCard icon="Package" title="Cài Đặt" link="/vi/docs/developer-api/installation" desc="Thêm SmartSpawner làm dependency bằng JitPack." />
<DocCard icon="Wrench" title="Khởi Tạo API" link="/vi/docs/developer-api/creation" desc="Lấy API instance và khởi tạo tích hợp." />
<DocCard icon="Server" title="Truy Cập Dữ Liệu" link="/vi/docs/developer-api/data-access" desc="Đọc và sửa stack, kho, kinh nghiệm cùng các thuộc tính khác." />
<DocCard icon="Zap" title="Sự Kiện" link="/vi/docs/developer-api/events" desc="Lắng nghe đặt, phá, bán và các sự kiện vòng đời spawner." />
<DocCard icon="Palette" title="API Bố Cục GUI" link="/vi/docs/developer-api/gui-layout" desc="Đăng ký và chèn provider bố cục GUI tùy chỉnh." />
<DocCard icon="Check" title="Kiểm Tra" link="/vi/docs/developer-api/validation" desc="Xác định vật phẩm spawner và loại entity hỗ trợ." />
<DocCard icon="FileCode2" title="Ví Dụ" link="/vi/docs/developer-api/examples" desc="Tham khảo ví dụ hoàn chỉnh cho các mẫu tích hợp phổ biến." />

</CardGrid>

## Tổng Quan

API dùng provider pattern:

```java
SmartSpawnerAPI api = SmartSpawnerProvider.getAPI();
```

API hỗ trợ:

- Đọc và sửa thuộc tính spawner như stack, delay và range
- Truy cập vật phẩm cùng kinh nghiệm đã lưu
- Tạo và xóa spawner bằng code
- Lắng nghe sự kiện đặt, phá, tạo vật phẩm và bán
- Đăng ký provider bố cục GUI tùy chỉnh
- Kiểm tra loại entity và dữ liệu spawner
