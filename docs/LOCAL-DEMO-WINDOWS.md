# Chạy thử trên Windows + PowerShell

Bộ demo dùng PostgreSQL riêng trên cổng **5434**, backend **8080**, frontend **5173**, AI service **8000**. Không cần cài PostgreSQL trực tiếp trên Windows hoặc tải bộ M5 nhiều GB. Model đã có trong Git; dữ liệu mô phỏng đủ để kiểm tra luồng dự báo.

## 1. Chuẩn bị

Cài và mở **Docker Desktop** ở chế độ Linux containers (Docker Compose v2.20 trở lên); cài **Git**, **JDK 21** (không chỉ JRE), **Node.js 22.16+ hoặc 24 LTS**. **Python 3.12** cần nếu muốn thử dự báo AI; có thể bổ sung sau khi thử backend/frontend.

Mở PowerShell mới sau khi cài và kiểm tra:

```powershell
git --version
java -version
javac -version
node --version
npm.cmd --version
docker compose version
docker info
py -3.12 --version
```

Không cần cài Maven riêng vì dự án có Maven Wrapper. Lần đầu cần Internet để tải image và dependencies.

## 2. Lấy main mới nhất

Nếu chưa clone:

```powershell
git clone https://github.com/HuyDEV10/enterprise.git
Set-Location .\enterprise
```

Nếu đã có repo, mở PowerShell tại thư mục đó rồi chạy:

```powershell
git status --short
# Nếu có thay đổi chưa lưu, hãy commit hoặc stash trước khi chuyển nhánh.
git fetch origin
git switch main
git pull --ff-only origin main
```

Nếu Git báo nhánh đã phân kỳ hoặc có file thay đổi, xử lý các thay đổi đó trước; không cần dùng reset --hard.

## 3. Terminal 1: database demo + backend

Từ thư mục gốc `enterprise`:

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
.\scripts\start-demo.ps1
```

Lần đầu script hỏi mật khẩu và xác nhận. Đặt mật khẩu **ít nhất 12 ký tự, tối đa 72 byte UTF-8**. Đây là mật khẩu chung cho các tài khoản demo; không có mật khẩu mặc định trong source.

Script tạo `.env.demo` (được Git bỏ qua), chứa key JWT, mật khẩu database ngẫu nhiên và mật khẩu demo mã hóa Base64 để tránh lỗi ký tự `$`, `#`, dấu nháy. Base64 không phải mã hóa bảo mật: hãy giữ file này riêng, không commit/chia sẻ. Script không in mật khẩu. File được giữ để lần sau dùng lại cùng tài khoản và dữ liệu.

Chờ log:

```text
DEMO DATASET READY
```

Giữ Terminal 1 chạy. Các lần sau chỉ chạy lại `start-demo.ps1`: dữ liệu không bị nhân đôi và các thay đổi bạn thử được giữ nguyên. Không dùng `run-dev.ps1 -ResetDatabase` cho hướng dẫn demo này; đó là môi trường dev khác.

## 4. Terminal 2: frontend

Mở PowerShell khác tại thư mục gốc repo:

```powershell
Set-Location .\frontend
npm.cmd ci
npm.cmd run dev
```

Mở **http://localhost:5173**. Lần sau có thể bỏ `npm.cmd ci` nếu dependencies không thay đổi.

Đăng nhập bằng mật khẩu đã đặt ở bước 3:

| Tài khoản | Vai trò | Thử nghiệm |
| --- | --- | --- |
| `demo_admin` | ADMIN | Xem tất cả, quản lý tài khoản và phân quyền |
| `demo_manager` | MANAGER | Quản lý vận hành, rủi ro, cảnh báo |
| `demo_analyst` | ANALYST | Xem vận hành, xử lý rủi ro, chạy phân tích; không sửa đơn/kho/sản phẩm |
| `demo_staff` | STAFF | Thao tác vận hành; không vào danh sách rủi ro/cảnh báo/người dùng |

Có thêm 20 tài khoản `demo_member_01`…`demo_member_20` để thử trang thứ hai; `demo_member_20` đã bị khóa để thử trường hợp tài khoản không hoạt động.

## 5. Terminal 3: AI service để thử dự báo

Có thể bỏ qua bước này nếu chỉ thử chức năng quản lý. Tại thư mục gốc repo:

```powershell
Set-Location .\ai-service
py -3.12 -m venv .venv
.\.venv\Scripts\python.exe -m pip install --upgrade pip
.\.venv\Scripts\python.exe -m pip install -r requirements-demo.txt
.\.venv\Scripts\python.exe -m uvicorn app.main:app --host 127.0.0.1 --port 8000
```

Không cần activate virtualenv, không cần train lại model. Giữ Terminal 3 chạy. Kiểm tra **http://localhost:8000/health**; `forecastModelLoaded` phải là `true`.

Vào **Dự báo nhu cầu**, chọn `DEMO-SP-001`…`DEMO-SP-012`, bấm **Chạy dự báo**. Mỗi sản phẩm có 90 ngày nhu cầu liên tục và trả 28 ngày dự báo. Sản phẩm ngoài nhóm 12 này cố ý chưa có mapping để thử thông báo thiếu lịch sử. Không cần bấm **Nhập lịch sử M5**: thao tác đó cần dataset M5 thật, không nằm trong bộ demo nhỏ.

Dữ liệu nhu cầu mang nguồn `DEMO_SYNTHETIC`, giao diện ghi rõ dữ liệu mô phỏng. Dùng để thử kết nối, biểu đồ và quy tắc thiếu hàng; không dùng để đánh giá độ chính xác/huấn luyện mô hình hay ra quyết định kinh doanh. Sự kiện thị trường và điểm ảnh hưởng đã nạp cũng là fixture được ghi rõ, không phải tin tức/scoring AI thật.

## 6. Bộ dữ liệu và cách thử

| Nội dung | Số lượng ban đầu |
| --- | ---: |
| Công ty / phòng ban / nhân viên | 1 / 5 / 24 |
| Tài khoản | 24 |
| Nhà cung cấp | 30 |
| Nhóm sản phẩm / sản phẩm | 6 / 40 |
| Kho / bản ghi tồn kho | 4 / 160 |
| Đơn mua / dòng sản phẩm | 60 / 180 |
| Lô vận chuyển | 50 |
| Rủi ro / cảnh báo thủ công | 18 / 18 |
| Sản phẩm có lịch sử nhu cầu | 12 |
| Dòng nhu cầu (12 × 90 ngày) | 1.080 |
| Sự kiện thị trường / ảnh hưởng NCC | 12 / 36 |

Hệ thống chạy một lần quét quy tắc sau khi nạp xong, bổ sung rủi ro/cảnh báo thật tính từ tồn kho thấp, đơn trễ, lô trễ và NCC rủi ro cao. Tổng số rủi ro/cảnh báo vì vậy lớn hơn 18. Bộ demo tắt lịch quét tự động để bạn dễ so sánh; nút quét thủ công và cập nhật AI risk vẫn dùng được.

Gợi ý thứ tự thử:

1. `demo_admin`: xem dashboard, phân trang nhà cung cấp/đơn mua/người dùng, xem công ty và nhân viên.
2. Tồn kho: lọc sắp hết, sửa lượng một mặt hàng, vào Giám sát rủi ro → Quét để xem cảnh báo thay đổi.
3. Đơn mua: chọn một trong 10 đơn nháp, sửa nội dung và xác nhận đặt hàng. Tạo lô chờ cho đơn đó, chuyển Đang vận chuyển → Đã đến; sau đó kết thúc đơn. Thử chuyển thẳng đơn nháp sang đã giao: UI không cho chọn.
4. Đơn/lô bị trễ: xem rủi ro liên quan và tiến độ. Đơn đã giao/hủy và lô đã đến/hủy không được mở lại.
5. Đăng xuất, thử từng vai trò trong bảng để kiểm tra nút bị ẩn và trang bị giới hạn.
6. Bật AI service, thử ba sản phẩm tồn thấp `DEMO-SP-001`…`003` và các sản phẩm tồn dồi dào `009`…`012`; kết quả phụ thuộc model và hàng đang về, không cố định sẵn nhãn dự báo.
7. Tắt AI service bằng Ctrl+C, chạy dự báo để xem lỗi dịch vụ; phiên đăng nhập vẫn còn.
8. Tín hiệu thị trường: 12 sự kiện ở ba ngày gần ngày nạp. Chọn từng sự kiện, xem giải thích fixture hoặc bấm tính lại để chạy quy tắc hiện có. Không cần đồng bộ tin GDELT qua mạng để thử giao diện.

Ngày của bộ demo được tính tại lần nạp đầu tiên theo múi giờ Việt Nam. Khởi động lại giữ snapshot đó, không dịch ngày hay ghi đè thao tác. Nếu sau nhiều ngày danh sách sự kiện trống, mở rộng khoảng ngày hoặc tạo lại bộ demo.

## 7. Dừng, khởi động lại, tạo lại dữ liệu

Dừng frontend/backend/AI bằng **Ctrl+C** ở từng terminal. Database có thể để chạy hoặc dừng tại thư mục gốc:

```powershell
docker compose --project-name enterprise-demo --env-file .env.demo --file docker-compose.demo.yml stop
```

Lần sau chạy lại bước 3, frontend và AI. Dữ liệu vẫn còn.

Muốn xóa các thao tác thử và tạo lại bộ dữ liệu từ đầu, dừng backend rồi chạy:

```powershell
.\scripts\start-demo.ps1 -ResetDemo
```

Lệnh này xóa **volume của riêng project `enterprise-demo`** rồi tạo dữ liệu mới theo ngày hiện tại; không xóa database/volume dev ở `docker-compose.yml`. Mật khẩu giữ nguyên từ `.env.demo`. Nếu mất `.env.demo` nhưng còn volume cũ, cần đặt mật khẩu mới và dùng `-ResetDemo` vì thông tin kết nối cũ không còn khôi phục được từ file.

## 8. Lỗi thường gặp

- **Cổng 5434 bận:** `start-demo.ps1 -DbPort 5435`. Cổng mới được lưu cho lần sau.
- **Cổng 8080 bận:** `start-demo.ps1 -ServerPort 8081`; trong terminal frontend đặt `$env:API_PROXY_TARGET='http://localhost:8081'` trước `npm.cmd run dev`. Nếu `frontend/.env.local` có `VITE_API_BASE_URL` cũ, cập nhật nó theo cổng mới hoặc để trống để dùng proxy.
- **Docker chưa chạy:** mở Docker Desktop, chờ engine sẵn sàng, thử `docker info`.
- **Maven báo sai Java:** `java -version` và `javac -version` cần là 21; sửa `JAVA_HOME` trỏ tới JDK 21 rồi mở lại PowerShell.
- **npm.ps1 bị chặn:** dùng các lệnh `npm.cmd` như hướng dẫn; policy Bypass phía trên chỉ áp dụng cho terminal hiện tại.
- **Đăng nhập lỗi:** dùng `demo_admin`, không dùng tài khoản admin mặc định cũ. Mật khẩu là mật khẩu đã đặt lần nạp đầu. Đổi nội dung `.env.demo` không đổi mật khẩu tài khoản đã có trong DB; tạo lại demo nếu muốn dùng mật khẩu mới.
- **Forecast báo dịch vụ chưa sẵn sàng:** kiểm tra Terminal 3, `/health`, Python 3.12 và đã cài `requirements-demo.txt`. Không cần tải raw M5 để forecast trên lịch sử demo đã nạp.
- **Quên mật khẩu demo:** dừng backend, đổi tên `.env.demo` thành `.env.demo.bak`, rồi chạy `.\scripts\start-demo.ps1 -ResetDemo`. Script hỏi mật khẩu mới và tạo lại riêng DB demo; các thao tác thử cũ sẽ mất.

## Xác minh trước khi phát hành

Bộ dữ liệu đã được thử với Docker Compose, PostgreSQL 17, backend JAR và FastAPI thật: đủ các lượng bản ghi, cả bốn vai trò đăng nhập, 12 dự báo × 28 ngày, phân trang và khởi động lại. Backend có 62 test; frontend có 28 test component và 15 E2E. Script cấu hình được kiểm tra bằng PowerShell 7 trên Linux; chưa chạy toàn bộ launcher trên Windows trong môi trường này. CI có job Windows riêng cho kiểm thử cấu hình script.
