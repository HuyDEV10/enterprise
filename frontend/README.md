# Enterprise Risk Frontend - Giai đoạn 3

React frontend cho hệ thống giám sát rủi ro doanh nghiệp và chuỗi cung ứng.

## Stack

- Node.js 22 LTS
- React 19.2.x
- Vite 8.x
- React Router
- Axios
- Lucide React
- CSS thuần

## Chạy local

```powershell
cd frontend
Copy-Item .env.example .env
npm install
npm run dev
```

Frontend mặc định: `http://localhost:5173`

Backend mặc định: `http://localhost:8080`

`.env`:

```env
VITE_API_BASE_URL=http://localhost:8080/api
```

## Build

```powershell
npm run build
```

## Các module đã tích hợp

- Dashboard
- Suppliers
- Products / Product Categories
- Warehouses / Inventory / Low Stock
- Purchase Orders / Items / Status
- Shipments / Status
- Risk Events / Status
- Alerts / Status
- Company / Departments / Employees

Authentication, JWT, AI và Neo4j chưa thuộc Giai đoạn 3.
