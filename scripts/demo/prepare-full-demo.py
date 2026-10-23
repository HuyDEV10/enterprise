"""Prepare the local full-system demonstration without deleting existing data.
Python 3.12+, standard library only. Run from any directory; services must be running.
Business fixtures are synthetic. M5 observations and GDELT records retain provenance.
"""
import argparse
from collections import Counter
from datetime import datetime, timedelta, timezone
import json
import os
from pathlib import Path
import subprocess
import sys
from urllib.error import HTTPError
from urllib.parse import urlparse
from urllib.request import OpenerDirector, HTTPHandler, HTTPDefaultErrorHandler, HTTPErrorProcessor, Request

SCRIPT_DIR = Path(__file__).resolve().parent
REPO = SCRIPT_DIR.parents[1]
REPORTS = REPO / 'demo-data' / 'full-system-v1'
MARKER = 'FULL-SYSTEM-DEMO-V1'
NOTE = '[DEMO] ' + MARKER + ' | Du lieu mo phong nghiep vu; khong phai giao dich/tin tuc that.'
COLLECTIONS = ['companies', 'departments', 'employees', 'suppliers', 'product-categories', 'products', 'warehouses', 'inventory', 'purchase-orders', 'shipments', 'risk-events', 'alerts', 'users']


def save(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    temp = path.with_suffix(path.suffix + '.tmp')
    temp.write_text(json.dumps(value, ensure_ascii=False, indent=2), encoding='utf-8')
    temp.replace(path)


class ApiError(RuntimeError):
    def __init__(self, status, data, path):
        self.status, self.data, self.path = status, data, path
        super().__init__(f'{path}: HTTP {status}: {data}')


class Client:
    def __init__(self, base, username, password):
        parsed = urlparse(base)
        if parsed.scheme != 'http' or parsed.hostname not in {'localhost', '127.0.0.1', '::1'} or parsed.path.rstrip('/') != '/api':
            raise ValueError('Only local HTTP /api development servers are supported.')
        self.base = base.rstrip('/')
        self.token = None
        self.http = OpenerDirector()
        for handler in (HTTPHandler(), HTTPDefaultErrorHandler(), HTTPErrorProcessor()):
            self.http.add_handler(handler)
        login = self.call('POST', 'auth/login', {'username': username, 'password': password})
        if 'ADMIN' not in login['user']['roles']:
            raise ValueError('Use a local ADMIN account.')
        self.token = login['accessToken']
        self.created = Counter()
        self.cache = {}

    def call(self, method, path, body=None, timeout=90):
        headers = {'Content-Type': 'application/json; charset=utf-8'}
        if self.token:
            headers['Authorization'] = 'Bearer ' + self.token
        payload = None if body is None else json.dumps(body, ensure_ascii=False).encode('utf-8')
        request = Request(self.base + '/' + path, data=payload, headers=headers, method=method)
        try:
            with self.http.open(request, timeout=timeout) as reply:
                raw = reply.read()
                return json.loads(raw) if raw else None
        except HTTPError as error:
            raw = error.read().decode('utf-8', errors='replace')
            try:
                data = json.loads(raw)
            except ValueError:
                data = raw
            raise ApiError(error.code, data, path) from None

    def rows(self, path):
        if path not in self.cache:
            self.cache[path] = self.call('GET', path)
        return self.cache[path]

    def ensure(self, path, key, payload):
        keys = [key] if isinstance(key, str) else key
        matches = [row for row in self.rows(path) if all(row.get(field) == payload.get(field) for field in keys)]
        if len(matches) > 1:
            raise ValueError(f'Ambiguous fixture identity: {path} {keys}')
        if matches:
            return matches[0], False
        row = self.call('POST', path, payload)
        self.rows(path).append(row)
        self.created[path] += 1
        return row, True

    def status(self, path, row, target):
        if row['status'] != target:
            row.update(self.call('PATCH', f"{path}/{row['id']}/status", {'status': target}))
        return row


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base-url', default='http://127.0.0.1:5173/api')
    parser.add_argument('--username', default='admin')
    parser.add_argument('--skip-core', action='store_true', help='Keep existing QA fixtures without running the core seeder.')
    args = parser.parse_args()
    password = os.environ.get('ENTERPRISE_TEST_PASSWORD', '123456')
    REPORTS.mkdir(parents=True, exist_ok=True)
    state_file = REPORTS / 'state.json'
    state = json.loads(state_file.read_text(encoding='utf-8')) if state_file.exists() else {}
    state.setdefault('seedDate', datetime.now(timezone(timedelta(hours=7))).date().isoformat())
    state.setdefault('completedOrders', [])
    state.setdefault('completedForecastSetup', [])
    state.setdefault('pendingPasswordUsers', [])
    state.setdefault('completedSentiments', [])
    today = datetime.fromisoformat(state['seedDate']).date()
    day = lambda offset: (today + timedelta(days=offset)).isoformat()
    save(state_file, state)

    client = Client(args.base_url, args.username, password)
    if not (REPORTS / 'before-snapshot.json').exists():
        save(REPORTS / 'before-snapshot.json', {name: client.call('GET', name) for name in COLLECTIONS})
    if not args.skip_core:
        child_env = dict(os.environ, ENTERPRISE_TEST_PASSWORD=password, PYTHONUTF8='1')
        subprocess.run([sys.executable, str(SCRIPT_DIR / 'seed-local-test-data.py'), '--base-url', args.base_url, '--username', args.username], env=child_env, check=True)
        client.cache.clear()
    company = next(row for row in client.rows('companies') if row.get('taxCode') == 'QA-COMPANY-001')
    department, _ = client.ensure('departments', 'code', dict(companyId=company['id'], code='DEMO-PB-01', name='[DEMO] Trung tam van hanh va phan tich', description=NOTE, status='ACTIVE'))
    for index in range(1, 21):
        username = f'demo_member_{index:02}'
        role = ['STAFF', 'MANAGER', 'ANALYST'][(index-1) % 3]
        user, created = client.ensure('users', 'username', dict(username=username, email=f'{username}@demo.example', password='Demo123456!', fullName=f'[DEMO] Nhan vien {index:02}', roles=[role]))
        if created:
            state['pendingPasswordUsers'].append(username)
            save(state_file, state)
            if index == 20:
                client.status('users', user, 'INACTIVE')
        client.ensure('employees', 'employeeCode', dict(companyId=company['id'], departmentId=department['id'], userId=user['id'], employeeCode=f'DEMO-NV-{index:02}', fullName=f'[DEMO] Nhan vien {index:02}', email=f'{username}@demo.example', jobTitle=['Nhan vien kho', 'Quan ly cung ung', 'Chuyen vien phan tich'][(index-1) % 3], status='INACTIVE' if index == 20 else 'ACTIVE'))

    suppliers = []
    for index, (name, country, region, risk) in enumerate([
        ('Atlas Components','USA','Texas','HIGH'), ('Pacific Supply','USA','California','LOW'),
        ('Minh Phat','Vietnam','Dong Nam A','MEDIUM'), ('Unrelated Alpine Goods','Switzerland','Europe','LOW')], 1):
        row, _ = client.ensure('suppliers', 'supplierCode', dict(supplierCode=f'DEMO-NCC-{index:02}', name='[DEMO] '+name, country=country, region=region, address='[DEMO] '+region, riskLevel=risk, status='ACTIVE', email=f'demo-supplier-{index}@demo.example', notes=NOTE))
        suppliers.append(row)
    category, _ = client.ensure('product-categories', 'code', dict(code='DEMO-NHOM-AI', name='[DEMO] San pham du bao va chuoi cung ung', description=NOTE, status='ACTIVE'))
    warehouse, _ = client.ensure('warehouses', 'warehouseCode', dict(warehouseCode='DEMO-KHO-01', name='[DEMO] Kho AI va chuoi cung ung', address='Khu logistics mo phong, TP Ho Chi Minh', status='ACTIVE'))
    titles = ['Ton du - LOW', 'Thieu sau 7 ngay - HIGH', 'Can nguong - MEDIUM', 'Thieu som - CRITICAL', 'Hang ve kip - LOW', 'Hang ve muon - CRITICAL', 'Chua co lich su - loi co kiem soat']
    products, inventory = [], []
    quantities, thresholds = [10000,30,50,10,8,2,100], [20,10,15,5,5,5,10]
    for index, title in enumerate(titles, 1):
        product, _ = client.ensure('products', 'productCode', dict(productCode=f'DEMO-AI-{index:02}', name='[DEMO] '+title, categoryId=category['id'], unit='don vi', referencePrice=25000+index*5000, description=NOTE+' M5 la du lieu ban le cong khai 2011-2016; anh xa san pham la mo phong.', status='ACTIVE'))
        products.append(product)
        item, _ = client.ensure('inventory', ['warehouseId','productId'], dict(warehouseId=warehouse['id'], productId=product['id'], quantity=quantities[index-1], lowStockThreshold=thresholds[index-1]))
        inventory.append(item)

    qa_products = {row['productCode']:row for row in client.rows('products')}
    orders, shipments = [], []
    def order_fixture(code, supplier, product, quantity, arrival, shipping=False, multi=False, draft=False):
        order, _ = client.ensure('purchase-orders', 'orderCode', dict(orderCode=code, supplierId=supplier['id'], orderDate=day(-15), expectedDeliveryDate=day(arrival), status='DRAFT', notes=NOTE, items=[dict(productId=product['id'], quantity=quantity, unitPrice=product['referencePrice'])]))
        orders.append(order)
        if code in state['completedOrders']:
            shipments.extend(row for row in client.rows('shipments') if row['purchaseOrderId'] == order['id'])
            return
        if not draft:
            if order['status']=='DRAFT': client.status('purchase-orders', order, 'ORDERED')
            suffixes = ['A','B'] if multi else ['']
            for suffix in suffixes:
                shcode = code.replace('DEMO-PO-', 'DEMO-SH-')+suffix
                shipment, _ = client.ensure('shipments', 'shipmentCode', dict(shipmentCode=shcode, purchaseOrderId=order['id'], carrierName='[DEMO] Logistics Viet', departureDate=day(-12) if shipping else None, expectedArrivalDate=day(arrival), status='PENDING', currentLocation='[DEMO] Trung tam trung chuyen', notes=NOTE))
                if shipping and shipment['status']=='PENDING': client.status('shipments', shipment, 'SHIPPING')
                shipments.append(shipment)
        state['completedOrders'].append(code)
        save(state_file, state)
    for number in range(1,4):
        order_fixture(f'DEMO-PO-{number:03}', suppliers[0], qa_products['QA-SP-001'], 100, -8, shipping=True)
    order_fixture('DEMO-PO-005', suppliers[1], products[4], 50, 2)
    order_fixture('DEMO-PO-006', suppliers[1], products[5], 100, 10)
    order_fixture('DEMO-PO-007', suppliers[2], qa_products['QA-SP-002'], 25, 7, draft=True)
    order_fixture('DEMO-PO-008', suppliers[2], qa_products['QA-SP-003'], 60, 7, multi=True)

    # Source-file Java uses the driver's local Maven-cache jar, not database passwords on command line.
    user_home = Path(os.environ.get('USERPROFILE', str(Path.home())))
    jars = sorted((user_home / '.m2/repository/org/postgresql/postgresql').glob('*/postgresql-*.jar'))
    if not jars:
        raise ValueError('PostgreSQL JDBC jar missing: run/build backend with Maven first.')
    java = subprocess.run(['java','-Duser.timezone=Asia/Ho_Chi_Minh','--class-path',str(jars[-1]),str(SCRIPT_DIR/'SeedDemoExtras.java'),str(REPO),state['seedDate'],','.join(state['pendingPasswordUsers'])], capture_output=True, text=True, encoding='utf-8', check=False)
    if java.returncode:
        raise RuntimeError('Demo DB fixture helper failed: '+java.stderr)
    extras = json.loads(java.stdout.strip())
    state['pendingPasswordUsers'] = []
    save(state_file, state)
    save(REPORTS/'database-fixtures.json', extras)
    print('Business fixtures, accounts and explicitly labelled demo events ready.', flush=True)

    forecast_cases = []
    item_ids = ['FOODS_3_003','FOODS_3_007','FOODS_3_002','FOODS_3_008','FOODS_3_009','FOODS_3_010']
    expected = ['LOW','HIGH','MEDIUM','CRITICAL','LOW','CRITICAL']
    for index, product in enumerate(products[:6]):
        code, identity = product['productCode'], item_ids[index]
        if code not in state['completedForecastSetup']:
            imported = client.call('POST', f"ai/inventory/products/{product['id']}/demand/import-m5?itemId={identity}&storeId=CA_1")
            assert imported['historyRows']==1941 and imported['sourceDataset']=='M5'
            save(REPORTS/f'{code}-import.json', imported)
            state['completedForecastSetup'].append(code)
            save(state_file, state)
        forecast = client.call('GET',f"ai/inventory/products/{product['id']}/forecast")
        save(REPORTS/f'{code}-forecast.json', forecast)
        forecast_cases.append(dict(productId=product['id'],productCode=code,sourceItemId=identity,sourceLocationId='CA_1',expectedRisk=expected[index],observedRisk=forecast['stockoutRisk'],currentInventory=forecast['currentInventory'],lowStockThreshold=forecast['lowStockThreshold'],forecastDemand=forecast['forecastDemand'],incomingSupply=forecast['incomingSupply'],projectedInventory=forecast['projectedInventory'],projectedStockoutDate=forecast['projectedStockoutDate'],historyDays=forecast['historyDays'],historySource=forecast.get('historySource')))
        client.call('POST',f"ai/inventory/products/{product['id']}/risk/refresh")
    print('Six real-M5 forecast scenarios imported, predicted and risk-refreshed.', flush=True)

    all_events = client.call('GET', f'ai/external-events?from={day(-60)}&to={day(1)}')
    events = {row['externalEventId']:row for row in all_events if row['externalSource']=='DEMO_FIXTURE'}
    texts = {
      'mixedEn': 'Atlas Components reported heavy losses after closing its factory, while Pacific Supply reported record profits.',
      'positiveEn':'Pacific Supply reported record profits and won a major new contract after expanding production.',
      'negativeVi':'Minh Phat thua lỗ nặng và buộc phải đóng cửa nhà máy. Hoạt động sản xuất của Minh Phat bị gián đoạn.'}
    definitions=[('DEMO-EVT-01','Atlas Components','mixedEn',0,'en','NEGATIVE',False),('DEMO-EVT-01','Pacific Supply','mixedEn',1,'en','POSITIVE',False),('DEMO-EVT-02','Pacific Supply','positiveEn',1,'en','POSITIVE',False),('DEMO-EVT-03','Minh Phat','negativeVi',2,'vi','NEGATIVE',True)]
    sentiment_cases=[]
    for event_code,entity,text_key,supplier_index,language,label,review in definitions:
        event=events[event_code]
        scenario_key=event_code+':'+entity
        if scenario_key not in state['completedSentiments']:
            client.call('POST',f"ai/external-events/{event['id']}/sentiment",dict(entity=entity,text=texts[text_key],supplierId=suppliers[supplier_index]['id'],language=language))
            state['completedSentiments'].append(scenario_key)
            save(state_file,state)
        sentiment_cases.append(dict(eventId=event['id'],eventCode=event_code,entity=entity,language=language,supplierCode=suppliers[supplier_index]['supplierCode'],supplierId=suppliers[supplier_index]['id'],text=texts[text_key],expectedSentiment=label,expectedNeedsReview=review,syntheticDemoText=True))
    for case in sentiment_cases:
        if case['eventCode']=='DEMO-EVT-01' and case['entity']=='Pacific Supply':
            case['knownModelLimitation']=True
            case['limitationReason']='Mixed-company English news: model returned NEGATIVE although this target reports record profits; expected POSITIVE is retained.'
    for event_code,event in events.items():
        if event_code=='DEMO-EVT-05':
            continue
        impacts=client.call('POST',f"ai/external-events/{event['id']}/impacts/calculate")
        save(REPORTS/f'{event_code}-impacts.json',impacts)
        save(REPORTS/f'{event_code}-sentiments.json',client.call('GET',f"ai/external-events/{event['id']}/sentiments"))
    save(REPORTS/'sentiment-inputs.json', {'notice':'FICTIONAL DEMO SCENARIOS; not source articles, training data or evaluation metrics.', 'texts':texts,'cases':sentiment_cases})
    monitoring1=client.call('POST','risk-monitoring/run',{})
    monitoring2=client.call('POST','risk-monitoring/run',{})
    assert monitoring2['riskEventsCreated']==0 and monitoring2['alertsCreated']==0
    after={name:client.call('GET',name) for name in COLLECTIONS}
    all_events=client.call('GET',f'ai/external-events?from={day(-60)}&to={day(1)}')
    selected={name:[row for row in rows if any(str(row.get(field,'')).startswith('DEMO-') for field in ['supplierCode','productCode','warehouseCode','orderCode','shipmentCode','employeeCode','code']) or (name=='users' and row.get('username','').startswith('demo_member_'))] for name,rows in after.items()}
    selected['companies']=[company]
    selected['inventory']=[row for row in after['inventory'] if row['productId'] in {p['id'] for p in products}]
    selected['events']=[row for row in all_events if row['externalSource']=='DEMO_FIXTURE']
    catalog=dict(dataset=MARKER,seedDate=state['seedDate'],fixtureData=selected,forecastCases=forecast_cases,sentimentCases=sentiment_cases,events=selected['events'],accounts=after['users'],noHistoryProductId=products[6]['id'],noHistoryProductCode=products[6]['productCode'])
    save(REPORTS/'full-demo-catalog.json',catalog)
    summary=dict(dataset=MARKER,seedDate=state['seedDate'],preparedAt=datetime.now(timezone(timedelta(hours=7))).isoformat(),baseUrl=args.base_url,addedThisRun=dict(client.created),totalCounts={name:len(rows) for name,rows in after.items()},supplementCounts={name:len(rows) for name,rows in selected.items()},dashboard=client.call('GET','dashboard/summary'),monitoringFirst=monitoring1,monitoringSecond=monitoring2,statusDistributions={name:dict(Counter(row['status'] for row in after[name])) for name in ['purchase-orders','shipments','risk-events','alerts','users']},eventSources=dict(Counter(row['externalSource'] for row in all_events)),forecastCases=forecast_cases)
    save(REPORTS/'summary.json',summary)
    state['completed']=True
    save(state_file,state)
    print(json.dumps({'status':'READY','reportDirectory':str(REPORTS),'addedThisRun':dict(client.created),'totals':summary['totalCounts'],'eventSources':summary['eventSources'],'forecastRisks':{row['productCode']:row['observedRisk'] for row in forecast_cases}},ensure_ascii=False),flush=True)

if __name__=='__main__':
    main()