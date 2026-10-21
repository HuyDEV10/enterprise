# Enterprise frontend

React 19 / Vite 8. Giao diện tiếng Việt, responsive, phông chữ đóng gói cùng ứng dụng; không cần gọi Google Fonts khi chạy.

## Chạy cùng backend

Dùng Node 22.16+ hoặc Node 24 LTS và backend đã cấu hình theo [backend/README.md](../backend/README.md).

```sh
cd frontend
npm ci
npm run dev
```

Vite chuyển `/api` tới `http://localhost:8080`. Đặt `API_PROXY_TARGET` trong biến môi trường hoặc `frontend/.env.local` nếu backend dùng địa chỉ khác. `VITE_API_BASE_URL` chỉ cần khi API khác origin; backend phải cho phép CORS tương ứng. Khi triển khai production, reverse proxy `/api` tới backend và trả `index.html` cho các URL frontend để hỗ trợ tải lại trang chi tiết. `npm run preview` chỉ dùng kiểm tra bản build tại máy phát triển.

Tài khoản được quản trị viên cấp hoặc bootstrap theo hướng dẫn backend. Màn hình đăng nhập không chứa thông tin tài khoản mặc định.

## Quyền và dữ liệu

Ma trận ở `src/auth/permissions.js` phản ánh `SecurityConfig`; backend kiểm tra quyền cho mọi request.

| Vai trò | Vận hành (NCC, sản phẩm, kho, tồn, đơn, lô hàng) | Rủi ro  | Cảnh báo         | Thao tác AI/giám sát | Người dùng |
| ------- | ------------------------------------------------ | ------- | ---------------- | -------------------- | ---------- |
| ADMIN   | Đọc/ghi                                          | Đọc/ghi | Đọc/tạo/cập nhật | Có                   | Quản trị   |
| MANAGER | Đọc/ghi                                          | Đọc/ghi | Đọc/tạo/cập nhật | Có                   | Không      |
| ANALYST | Chỉ đọc                                          | Đọc/ghi | Đọc/cập nhật     | Có                   | Không      |
| STAFF   | Đọc/ghi                                          | Không   | Không            | Chỉ xem              | Không      |

Dashboard chỉ gọi API phù hợp quyền và hiển thị lỗi độc lập theo widget. Tổng số rủi ro/cảnh báo tổng hợp vẫn được backend cho mọi vai trò xem. Bộ lọc và trang của nhà cung cấp, đơn mua, người dùng dùng URL; dữ liệu phân trang được lấy từ backend, 20 mục/trang. Các biểu đồ dùng dữ liệu API thực, không tự tạo lịch sử hay xu hướng.

Đơn mua mới là DRAFT, chỉ sửa nội dung khi còn nháp. Lô mới là PENDING và chỉ gắn với đơn đã đặt/đang vận chuyển/chậm. Các bước chuyển trạng thái có nút xác nhận; ngày đến thực tế được backend ghi nhận khi ARRIVED. Backend kiểm tra thêm ràng buộc giữa đơn và các lô hàng đang hoạt động.

Lỗi mạng/5xx khi khôi phục phiên không xóa token; 401 chuyển về đăng nhập và giữ URL cần đến. Token hiện vẫn nằm trong localStorage theo hợp đồng hiện tại; chưa có refresh token/cookie session.

## Kiểm tra

```sh
npm test
npm run build
npx playwright install chromium
npm run test:e2e
```

Có thể đặt `PLAYWRIGHT_CHROMIUM_EXECUTABLE=/usr/bin/chromium` để dùng Chromium đã cài. Các E2E trong repo mô phỏng API để kiểm tra vai trò, phiên, phân trang, AI unavailable, responsive và accessibility ổn định; chúng không thay thế kiểm thử tích hợp backend. Bộ Vitest kiểm tra phản hồi tới muộn, lỗi độc lập từng panel, phân quyền, phiên và thao tác trạng thái. Kiểm tra axe tự động trên dashboard, đăng nhập và nhà cung cấp không thay thế đánh giá accessibility thủ công.

Ở lần triển khai này, đã kiểm tra thêm bằng Chromium với backend JAR/PostgreSQL thật: đăng nhập bốn vai trò; duyệt module; tạo đơn nháp → xác nhận đặt → tạo lô chờ → vận chuyển → đã đến. Dữ liệu kiểm thử và thông tin đăng nhập được tạo riêng ngoài source.

## Phần để giai đoạn AI service

Đã sửa lỗi kết quả forecast/exposure của lựa chọn cũ ghi đè lựa chọn mới, bổ sung biểu đồ và giải thích ảnh hưởng cho từng nhà cung cấp. Luồng phân tích sentiment theo entity sẽ hoàn thiện cùng thay đổi mô hình dữ liệu backend/AI; chưa đưa nút phân tích vào UI khi kết quả hiện đang gắn chung với event. Nếu dữ liệu M5 hoặc model chưa cài, giao diện báo trạng thái chưa sẵn sàng. Không thay đổi AI service trong đợt frontend này.
