"""Populate linked local business fixtures through the actual Spring Boot API.

Python 3.12+, standard library only. Existing data and completed fixtures are kept.
Usage: python seed-local-test-data.py [--base-url http://127.0.0.1:8080/api]
"""
import argparse
import json
import os
from collections import Counter
from datetime import date, datetime, timedelta, timezone
from pathlib import Path
from urllib.error import HTTPError
from urllib.parse import urlparse
from urllib.request import HTTPDefaultErrorHandler, HTTPErrorProcessor, HTTPHandler, OpenerDirector, Request

ROOT = Path(__file__).resolve().parent
STATE_PATH = ROOT / "test-data-state.json"
MARKER = "LOCAL-QA-V1"
NOTE = f"{MARKER} | Dữ liệu mô phỏng để kiểm thử nghiệp vụ."
COLLECTIONS = ["companies", "departments", "employees", "suppliers", "product-categories",
               "products", "warehouses", "inventory", "purchase-orders", "shipments", "risk-events", "alerts"]


def save(path, data):
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
    temporary.replace(path)


class Seed:
    def __init__(self, args):
        parsed = urlparse(args.base_url)
        if parsed.scheme != "http" or parsed.hostname not in {"localhost", "127.0.0.1", "::1"}:
            raise ValueError("This fixture script only targets local HTTP development servers.")
        self.base = args.base_url.rstrip("/")
        self.http = OpenerDirector()
        for handler in (HTTPHandler(), HTTPDefaultErrorHandler(), HTTPErrorProcessor()):
            self.http.add_handler(handler)
        self.token = None
        login = self.api("POST", "auth/login", {"username": args.username, "password": args.password})
        self.token = login["accessToken"]
        if "ADMIN" not in login["user"]["roles"]:
            raise ValueError("Use a local ADMIN account to seed all modules.")
        self.state = json.loads(STATE_PATH.read_text(encoding="utf-8")) if STATE_PATH.exists() else {}
        self.today = date.fromisoformat(self.state.get("seedDate", datetime.now(timezone(timedelta(hours=7))).date().isoformat()))
        self.state.setdefault("seedDate", self.today.isoformat())
        self.state.setdefault("completedOrders", [])
        self.state.setdefault("completedAlerts", [])
        self.cache = {path: self.api("GET", path) for path in COLLECTIONS}
        self.users = {user["username"]: user for user in self.api("GET", "users")}
        for username in ("admin", "manager_test", "analyst_test", "staff_test"):
            if username not in self.users:
                raise ValueError(f"Required existing local account is missing: {username}")
        self.before = {path: len(rows) for path, rows in self.cache.items()}
        self.added = Counter()
        self.reused = Counter()
        self.ids = {path: [] for path in COLLECTIONS}
        self.new_ids = set()

    def api(self, method, path, payload=None, token=None):
        body = None if payload is None else json.dumps(payload, ensure_ascii=False).encode("utf-8")
        headers = {"Content-Type": "application/json; charset=utf-8"}
        bearer = self.token if token is None else token
        if bearer:
            headers["Authorization"] = "Bearer " + bearer
        request = Request(self.base + "/" + path, body, headers, method=method)
        try:
            with self.http.open(request, timeout=30) as response:
                raw = response.read()
                return json.loads(raw) if raw else None
        except HTTPError as error:
            detail = error.read().decode("utf-8", errors="replace")
            raise RuntimeError(f"{method} {path}: HTTP {error.code}: {detail}") from None

    def ensure(self, path, fields, payload):
        fields = [fields] if isinstance(fields, str) else fields
        found = [row for row in self.cache[path] if all(row.get(key) == payload.get(key) for key in fields)]
        if len(found) > 1:
            raise RuntimeError(f"Ambiguous fixture key in {path}: {fields}")
        if found:
            row = found[0]
            self.reused[path] += 1
        else:
            row = self.api("POST", path, payload)
            self.cache[path].append(row)
            self.new_ids.add(row["id"])
            self.added[path] += 1
        if row["id"] not in self.ids[path]:
            self.ids[path].append(row["id"])
        return row

    def status(self, path, row, target):
        if row["status"] == target:
            return row
        result = self.api("PATCH", f"{path}/{row['id']}/status", {"status": target})
        row.update(result)
        return row

    def day(self, offset):
        return (self.today + timedelta(days=offset)).isoformat()

    def seed(self):
        company = self.ensure("companies", "taxCode", dict(name="[TEST] Công ty Cung ứng An Việt", taxCode="QA-COMPANY-001",
            email="office@anviet.example", address="Khu công nghiệp mô phỏng, Hà Nội", status="ACTIVE"))
        departments = [self.ensure("departments", "code", dict(companyId=company["id"], code=f"QA-PB-{i+1:03}", name=name,
            description=NOTE, status="ACTIVE")) for i, name in enumerate(["[TEST] Điều hành", "[TEST] Mua hàng", "[TEST] Kho vận", "[TEST] Phân tích rủi ro"])]
        people = [("Nguyễn Minh An", "admin", 0, "Giám đốc vận hành"), ("Trần Thu Hà", "manager_test", 1, "Trưởng phòng mua hàng"),
                  ("Lê Hoàng Nam", "staff_test", 2, "Nhân viên kho"), ("Phạm Ngọc Linh", "analyst_test", 3, "Chuyên viên rủi ro"),
                  ("Võ Thanh Tâm", None, 1, "Chuyên viên cung ứng"), ("Đỗ Thùy Dương", None, 2, "Điều phối vận tải"),
                  ("Bùi Gia Huy", None, 2, "Nhân viên kiểm kê"), ("Hoàng Mai Anh", None, 3, "Chuyên viên đối soát")]
        linked_user_ids = {row.get("userId") for row in self.cache["employees"]}
        for i, (name, username, department, title) in enumerate(people):
            user_id = self.users[username]["id"] if username else None
            # Keep any pre-existing employee/account relationship.
            existing = next((row for row in self.cache["employees"] if row.get("employeeCode") == f"QA-NV-{i+1:03}"), None)
            if user_id in linked_user_ids and not existing:
                user_id = None
            self.ensure("employees", "employeeCode", dict(companyId=company["id"], departmentId=departments[department]["id"],
                userId=user_id, employeeCode=f"QA-NV-{i+1:03}", fullName="[TEST] " + name, email=f"nhanvien{i+1}@anviet.example",
                jobTitle=title, status="INACTIVE" if i == 7 else "ACTIVE"))
        supplier_names = ["Nông sản An Việt", "Thực phẩm Mekong", "Bao bì Đại Phát", "Cung ứng Sakura", "Nguyên liệu Greenfield", "Phân phối Thành Công",
                          "Đồ uống Thanh Sơn", "Gia dụng Minh Anh", "Thương mại Nam Hải", "Chế biến Hưng Thịnh", "Cung ứng Bình Minh", "Sản xuất Đại Lộc"]
        countries = [("Việt Nam", "Đông Nam Á"), ("Thái Lan", "Đông Nam Á"), ("Trung Quốc", "Đông Á"),
                     ("Nhật Bản", "Đông Á"), ("Hàn Quốc", "Đông Á"), ("Ấn Độ", "Nam Á")]
        suppliers = []
        for i in range(24):
            country, region = countries[i % len(countries)]
            suppliers.append(self.ensure("suppliers", "supplierCode", dict(supplierCode=f"QA-NCC-{i+1:03}",
                name=f"[TEST] {supplier_names[i % 12]} {i//12+1}", email=f"supplier{i+1:02}@supply.example",
                address="Địa chỉ mô phỏng tại " + country, country=country, region=region, status="ACTIVE" if i < 22 else "INACTIVE",
                riskLevel=["LOW", "MEDIUM", "HIGH"][i % 3], notes=NOTE)))
        categories = [self.ensure("product-categories", "code", dict(code=f"QA-NHOM-{i+1:03}", name="[TEST] " + name,
            description=NOTE, status="ACTIVE")) for i, name in enumerate(["Lương thực", "Đồ uống", "Gia vị", "Bao bì", "Gia dụng", "Nguyên liệu"])]
        product_specs = [("Gạo thơm Jasmine",0,"kg",25000),("Bột mì đa dụng",0,"kg",18000),("Đường tinh luyện",0,"kg",22000),
                         ("Cà phê Robusta",1,"kg",160000),("Trà xanh",1,"hộp",65000),("Nước khoáng",1,"thùng",85000),
                         ("Dầu đậu nành",2,"lít",43000),("Nước tương",2,"chai",22000),("Muối biển",2,"kg",12000),
                         ("Thùng carton",3,"cái",9500),("Túi giấy",3,"cái",2500),("Màng bọc thực phẩm",3,"cuộn",35000),
                         ("Khăn giấy",4,"hộp",18000),("Nước rửa chén",4,"chai",34000),("Túi rác",4,"cuộn",20000),
                         ("Bột yến mạch",5,"kg",45000),("Bột cacao",5,"kg",95000),("Hạt điều",5,"kg",180000)]
        products = [self.ensure("products", "productCode", dict(productCode=f"QA-SP-{i+1:03}", name="[TEST] " + name,
            categoryId=categories[category]["id"], unit=unit, referencePrice=price, description=NOTE,
            status="ACTIVE" if i < 16 else "INACTIVE")) for i, (name, category, unit, price) in enumerate(product_specs)]
        warehouses = [self.ensure("warehouses", "warehouseCode", dict(warehouseCode=f"QA-KHO-{i+1:03}", name="[TEST] Kho " + city,
            address="Khu logistics mô phỏng, " + city, status="ACTIVE")) for i, city in enumerate(["Hà Nội", "Đà Nẵng", "TP. Hồ Chí Minh"])]
        for p in range(16):
            for w in range(3):
                quantity = [0, 8, 25, 40][p] if p < 4 else 150 + p * 12 + w * 25
                self.ensure("inventory", ["warehouseId", "productId"], dict(warehouseId=warehouses[w]["id"],
                    productId=products[p]["id"], quantity=quantity, lowStockThreshold=40))
        print("Master data, people, and inventory ready.", flush=True)
        orders = []
        primary_shipments = {}
        targets = ["DRAFT", "ORDERED", "IN_TRANSIT", "DELAYED", "DELIVERED", "CANCELLED"]
        for i in range(24):
            group = i // 4
            code = f"QA-PO-{i+1:03}"
            target = targets[group]
            expected_offset = -[1,4,8,12][i%4] if group == 3 else -(2+i%4) if group == 4 else 3+i%9
            items = [dict(productId=products[(i*2+line)%16]["id"], quantity=20+((i+line)%5)*5,
                          unitPrice=product_specs[(i*2+line)%16][3]) for line in range(3)]
            order = self.ensure("purchase-orders", "orderCode", dict(orderCode=code, supplierId=suppliers[i%22]["id"],
                orderDate=self.day(-30-i%6), expectedDeliveryDate=self.day(expected_offset), status="DRAFT",
                notes=f"{NOTE} Kịch bản ban đầu: {target}." + (" Đơn có hai chuyến giao." if i in (6,10) else ""), items=items))
            orders.append(order)
            completed = code in self.state["completedOrders"] and order["id"] not in self.new_ids
            if group == 0:
                pass
            elif completed:
                primary = next((row for row in self.cache["shipments"] if row["shipmentCode"] == f"QA-SH-{i+1:03}"), None)
                if primary:
                    primary_shipments[i] = primary
                    self.ids["shipments"].append(primary["id"])
                for extra in self.cache["shipments"]:
                    if extra["shipmentCode"] == f"QA-SH-{i+1:03}-B":
                        self.ids["shipments"].append(extra["id"])
                continue
            else:
                if order["status"] == "DRAFT":
                    self.status("purchase-orders", order, "ORDERED")
                shipment = self.ensure("shipments", "shipmentCode", dict(shipmentCode=f"QA-SH-{i+1:03}", purchaseOrderId=order["id"],
                    carrierName=["[TEST] An Việt Express", "[TEST] Bắc Nam Logistics", "[TEST] Mekong Transport"][i%3],
                    departureDate=self.day(-27-i%6) if group in (2,3,4) else None, expectedArrivalDate=self.day(expected_offset),
                    status="PENDING", currentLocation="Trung tâm trung chuyển mô phỏng", notes=NOTE))
                primary_shipments[i] = shipment
                if group in (2,3,4):
                    if shipment["status"] == "PENDING":
                        self.status("shipments", shipment, "SHIPPING")
                    if group == 3:
                        self.status("shipments", shipment, "DELAYED")
                    elif group == 4:
                        self.status("shipments", shipment, "ARRIVED")
                elif group == 5:
                    self.status("shipments", shipment, "CANCELLED")
                if i in (6,10):
                    self.ensure("shipments", "shipmentCode", dict(shipmentCode=f"QA-SH-{i+1:03}-B", purchaseOrderId=order["id"],
                        carrierName="[TEST] Đơn vị giao bổ sung", expectedArrivalDate=self.day(expected_offset+1), status="PENDING",
                        currentLocation="Chờ lấy lô hàng thứ hai", notes=NOTE + " Chuyến thứ hai để test giao nhiều đợt."))
                # SHIPPING changes ORDERED to IN_TRANSIT inside the real service.
                order.update(self.api("GET", f"purchase-orders/{order['id']}"))
                self.status("purchase-orders", order, target)
            if code not in self.state["completedOrders"]:
                self.state["completedOrders"].append(code)
            save(STATE_PATH, self.state)
        print("24 purchase-order scenarios and their shipments ready.", flush=True)
        risk_specs = [
            ("Hết gạo tại kho Hà Nội", "INVENTORY", "CRITICAL", "OPEN", 0, None, None),
            ("Bột mì dưới ngưỡng tối thiểu", "INVENTORY", "HIGH", "OPEN", 1, None, None),
            ("Nhà cung cấp chậm xác nhận năng lực", "SUPPLIER", "MEDIUM", "OPEN", None, 2, None),
            ("Chuyến giao trễ 1 ngày", "SHIPPING", "MEDIUM", "INVESTIGATING", None, None, 12),
            ("Chuyến giao trễ 4 ngày", "SHIPPING", "HIGH", "INVESTIGATING", None, None, 13),
            ("Chuyến giao trễ 12 ngày", "SHIPPING", "CRITICAL", "INVESTIGATING", None, None, 15),
            ("Chênh lệch hóa đơn đã xử lý", "FINANCIAL", "LOW", "RESOLVED", None, None, 16),
            ("Kiểm kê carton đã đối soát", "OPERATIONAL", "MEDIUM", "RESOLVED", 9, None, None),
            ("Chất lượng lô hàng đã khắc phục", "SUPPLIER", "HIGH", "RESOLVED", None, 5, None),
            ("Biến động giá đã đóng hồ sơ", "MARKET", "MEDIUM", "CLOSED", None, 8, None),
            ("Lỗi nhãn bao bì đã đóng hồ sơ", "OTHER", "LOW", "CLOSED", 14, None, None),
            ("Rà soát hồ sơ cung ứng hoàn tất", "SUPPLIER", "LOW", "CLOSED", None, 11, None)]
        for i, (title, kind, impact, status, product, supplier, order_index) in enumerate(risk_specs):
            order = orders[order_index] if order_index is not None else None
            risk = self.ensure("risk-events", "title", dict(title=f"[TEST][QA-RISK-{i+1:03}] {title}", riskType=kind,
                impactLevel=impact, status=status, description=NOTE + " " + title + ". Kho và đối tác đều là tình huống thử nghiệm.",
                detectedAt=self.day(-1-i%5)+"T09:00:00+07:00", productId=products[product]["id"] if product is not None else None,
                supplierId=suppliers[supplier]["id"] if supplier is not None else order["supplierId"] if order else None,
                purchaseOrderId=order["id"] if order else None,
                shipmentId=primary_shipments[order_index]["id"] if order_index in primary_shipments else None))
            alert = self.ensure("alerts", "title", dict(title=f"[TEST][QA-ALERT-{i+1:03}] {title}",
                message=NOTE + " Mở rủi ro liên quan, kiểm tra tham chiếu và cập nhật tiến độ xử lý.", alertType="MANUAL_TEST",
                priority=impact, recipientUserId=self.users[["admin", "manager_test", "analyst_test"][i%3]]["id"], riskEventId=risk["id"]))
            if alert["id"] in self.new_ids or alert["id"] not in self.state["completedAlerts"]:
                self.status("alerts", alert, "NEW" if status == "OPEN" else "READ" if status == "INVESTIGATING" else "RESOLVED")
                self.state["completedAlerts"].append(alert["id"])
                save(STATE_PATH, self.state)
        self.state["completed"] = True
        save(STATE_PATH, self.state)

    def report(self):
        # Run real business monitoring after all linked fixtures are committed.
        monitoring_first = self.api("POST", "risk-monitoring/run", {})
        monitoring_second = self.api("POST", "risk-monitoring/run", {})
        assert monitoring_second["riskEventsCreated"] == 0 and monitoring_second["alertsCreated"] == 0
        after = {path: self.api("GET", path) for path in COLLECTIONS}
        selected = {path: [row for row in rows if row["id"] in self.ids[path]] for path, rows in after.items()}
        assert len(selected["suppliers"]) == 24 and len(selected["products"]) == 18
        assert len(selected["inventory"]) == 48 and len(selected["purchase-orders"]) == 24
        assert len(selected["shipments"]) == 22 and len(selected["risk-events"]) == 12 and len(selected["alerts"]) == 12
        for order in selected["purchase-orders"]:
            assert sum(item["quantity"]*item["unitPrice"] for item in order["items"]) == order["totalAmount"]
        inventories = {(row["warehouseId"], row["productId"]) for row in selected["inventory"]}
        assert len(inventories) == 48
        for collection in ("suppliers", "purchase-orders"):
            page = self.api("GET", collection + "/page?page=1&size=20")
            assert page["totalPages"] >= 2 and len(page["content"]) > 0
        for path, rows in selected.items():
            if path in ("purchase-orders", "shipments", "risk-events", "alerts"):
                print(path + " statuses: " + str(dict(Counter(row["status"] for row in rows))), flush=True)
        counts = {path: {"fixture": len(selected[path]), "addedThisRun": self.added[path], "totalInDatabase": len(after[path])} for path in COLLECTIONS}
        summary = {"dataset": MARKER, "seedDate": self.today.isoformat(), "baseUrl": self.base,
            "counts": counts, "beforeThisRun": self.before, "dashboard": self.api("GET", "dashboard/summary"),
            "monitoringFirst": monitoring_first, "monitoringSecond": monitoring_second,
            "monitoringSummary": self.api("GET", "risk-monitoring/summary"),
            "statusDistributions": {path: dict(Counter(row["status"] for row in selected[path])) for path in ("purchase-orders", "shipments", "risk-events", "alerts")}}
        catalog = {"dataset": MARKER, "seedDate": self.today.isoformat(), "fixtureData": selected,
            "accounts": [{key: row[key] for key in ("id", "username", "roles", "status")} for row in self.users.values()]}
        save(ROOT / "test-data-summary.json", summary)
        save(ROOT / "test-data-catalog.json", catalog)
        print(json.dumps({"addedThisRun": sum(self.added.values()), "fixtureRecords": sum(len(rows) for rows in selected.values()), "counts": counts}, ensure_ascii=False), flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", default="http://127.0.0.1:8080/api")
    parser.add_argument("--username", default="admin")
    parser.add_argument("--password", default=os.environ.get("ENTERPRISE_TEST_PASSWORD", "123456"))
    args = parser.parse_args()
    seed = Seed(args)
    seed.seed()
    seed.report()


if __name__ == "__main__":
    main()
