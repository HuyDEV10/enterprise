#!/usr/bin/env python3
"""Verify full demo with GET/login and invalid-payload authorization checks.

No reset, seed, delete, valid update, AI refresh, ingestion or integration test
is performed. HTTP-only OpenerDirector + HTTPHandler avoids SSL initialization
and inherited SSLKEYLOGFILE problems. Passwords/tokens never enter the report.
"""
from __future__ import annotations
import argparse
from collections import Counter
from datetime import datetime, timezone
from decimal import Decimal
import json
import math
from pathlib import Path
import sys
from urllib.error import HTTPError
from urllib.parse import quote, urlsplit
from urllib.request import HTTPHandler, OpenerDirector, Request

CORE = {'admin': 'ADMIN', 'manager_test': 'MANAGER', 'analyst_test': 'ANALYST', 'staff_test': 'STAFF'}
SENSITIVE = {'password', 'accesstoken', 'authorization', 'token', 'jwtsecret'}


def clean(value):
    if isinstance(value, dict):
        return {str(k): clean(v) for k, v in value.items() if str(k).replace('_', '').lower() not in SENSITIVE}
    if isinstance(value, (list, tuple)):
        return [clean(v) for v in value]
    if isinstance(value, Decimal):
        return str(value)
    return value[:1600] if isinstance(value, str) else value


def rows(value):
    if isinstance(value, list):
        return [v for v in value if isinstance(v, dict)]
    if isinstance(value, dict):
        for key in ('content', 'items', 'results', 'data'):
            if isinstance(value.get(key), list):
                return rows(value[key])
        return [value] if any(k in value for k in ('id', 'username', 'productCode')) else []
    return []


def decimal(value):
    result = Decimal(str(value))
    if not result.is_finite():
        raise ValueError('Non-finite numeric field')
    return result


def error_code(value):
    if not isinstance(value, dict):
        return None
    detail = value.get('detail')
    return value.get('code') or (detail.get('code') if isinstance(detail, dict) else None)


class Client:
    def __init__(self, base, timeout):
        parsed = urlsplit(base)
        if parsed.scheme != 'http' or parsed.hostname not in ('localhost', '127.0.0.1', '::1'):
            raise ValueError('--base-url must be local HTTP')
        if parsed.username or parsed.password or parsed.query or parsed.fragment:
            raise ValueError('--base-url must not include credentials, query or fragment')
        self.base, self.timeout = base.rstrip('/'), timeout
        self.opener = OpenerDirector()
        self.opener.add_handler(HTTPHandler())

    def request(self, method, path, token=None, body=None):
        headers = {'Accept': 'application/json', 'Connection': 'close'}
        if token:
            headers['Authorization'] = 'Bearer ' + token
        data = None
        if body is not None:
            data = json.dumps(body, ensure_ascii=False).encode('utf-8')
            headers['Content-Type'] = 'application/json; charset=utf-8'
        req = Request(self.base + '/' + path.lstrip('/'), headers=headers, data=data, method=method)
        try:
            response = self.opener.open(req, timeout=self.timeout)
        except HTTPError as exc:
            response = exc
        with response:
            status, raw = response.status, response.read()
        try:
            payload = json.loads(raw.decode('utf-8-sig')) if raw else None
        except (UnicodeDecodeError, json.JSONDecodeError):
            payload = {'nonJsonResponse': True, 'responseBytes': len(raw)}
        return status, payload


class Verify:
    def __init__(self, client, catalog, username, password):
        self.client, self.catalog = client, catalog
        self.username, self.password = username, password
        self.checks, self.tokens, self.resources = [], {}, {}
        self.sentiments, self.impacts = {}, {}

    def record(self, name, passed, detail=None, status=None):
        status = status or ('PASS' if passed else 'FAIL')
        passed = None if status == 'KNOWN_LIMITATION' else bool(passed)
        self.checks.append({'name': name, 'status': status, 'passed': passed, 'detail': clean(detail)})
        print(status + ' ' + name, flush=True)

    def attempt(self, name, action):
        try:
            return action()
        except Exception as exc:
            self.record(name, False, {'errorType': type(exc).__name__, 'message': str(exc)})

    @property
    def admin(self):
        token = self.tokens.get(self.username)
        if not token:
            raise ValueError('Admin login failed; dependent request cannot run')
        return token

    def login(self, username, role):
        status, data = self.client.request('POST', '/auth/login', body={'username': username, 'password': self.password})
        token = data.get('accessToken') if isinstance(data, dict) else None
        passed = status == 200 and isinstance(token, str) and bool(token)
        self.record('auth.' + username + '.login', passed, {'status': status, 'code': error_code(data)})
        if not passed:
            return
        self.tokens[username] = token
        status, me = self.client.request('GET', '/auth/me', token=token)
        me = me if isinstance(me, dict) else {}
        roles = [str(v).removeprefix('ROLE_') for v in me.get('roles', [])]
        self.record('auth.' + username + '.me', status == 200 and me.get('username') == username and role in roles,
                    {'status': status, 'username': me.get('username'), 'roles': roles})

    def rbac(self):
        for username, role in CORE.items():
            username = self.username if role == 'ADMIN' else username
            token, expected = self.tokens.get(username), 200 if role == 'ADMIN' else 403
            if not token:
                self.record('rbac.' + username + '.users', False, 'Login did not succeed')
                continue
            status, data = self.client.request('GET', '/users', token=token)
            self.record('rbac.' + username + '.users', status == expected, {'status': status, 'expectedStatus': expected})
        for path in ('risk-events', 'alerts', 'companies'):
            token = self.tokens.get('staff_test')
            if not token:
                self.record('rbac.staff_test.' + path, False, 'STAFF login failed')
                continue
            status, _ = self.client.request('GET', '/' + path, token=token)
            self.record('rbac.staff_test.' + path, status == 403, {'status': status, 'expectedStatus': 403})

    def load(self, endpoint):
        status, data = self.client.request('GET', '/' + endpoint, token=self.admin)
        actual = rows(data)
        if status == 200:
            self.resources[endpoint] = actual
        fixtures = rows((self.catalog.get('fixtureData') or {}).get(endpoint))
        missing = {r['id'] for r in fixtures if r.get('id')} - {r['id'] for r in actual if r.get('id')}
        self.record('resources.' + endpoint, status == 200 and bool(actual) and not missing,
                    {'status': status, 'count': len(actual), 'fixtureCount': len(fixtures), 'missingFixtureIds': sorted(missing)})

    def page(self, endpoint):
        status, data = self.client.request('GET', '/' + endpoint + '/page?page=1&size=5', token=self.admin)
        data = data if isinstance(data, dict) else {}
        content = rows(data)
        passed = status == 200 and data.get('page') == 1 and bool(content) and int(data.get('totalElements', 0)) > 5
        self.record('pagination.' + endpoint + '.page1', passed,
                    {'status': status, 'page': data.get('page'), 'rows': len(content),
                     'totalElements': data.get('totalElements'), 'totalPages': data.get('totalPages')})

    def consistency(self):
        for endpoint, key in (('products', 'productCode'), ('warehouses', 'warehouseCode')):
            values = [str(r.get(key, '')).strip().casefold() for r in self.resources.get(endpoint, [])]
            duplicates = sum(n - 1 for n in Counter(values).values() if n > 1)
            self.record('consistency.' + endpoint + '.uniqueCodes', bool(values) and all(values) and duplicates == 0,
                        {'count': len(values), 'duplicates': duplicates})
        inventory = self.resources.get('inventory', [])
        pairs = [(r.get('warehouseId'), r.get('productId')) for r in inventory]
        duplicates = [p for p, n in Counter(pairs).items() if n > 1]
        self.record('consistency.inventory.uniqueWarehouseProduct', bool(pairs) and not duplicates and all(all(p) for p in pairs),
                    {'rows': len(pairs), 'duplicatePairs': duplicates})
        errors, orders = [], self.resources.get('purchase-orders', [])
        for order in orders:
            try:
                items = rows(order.get('items'))
                if not items:
                    raise ValueError('No order items')
                total = Decimal('0')
                for item in items:
                    qty, price, line = map(decimal, (item.get('quantity'), item.get('unitPrice'), item.get('lineTotal')))
                    if qty <= 0 or price < 0 or abs(qty * price - line) > Decimal('0.01'):
                        raise ValueError('Invalid line total')
                    total += line
                if abs(total - decimal(order.get('totalAmount'))) > Decimal('0.01'):
                    raise ValueError('Order total mismatch')
            except Exception as exc:
                errors.append({'orderCode': order.get('orderCode'), 'reason': str(exc)})
        self.record('consistency.purchaseOrders.money', bool(orders) and not errors, {'orders': len(orders), 'errors': errors})

    def product_id(self, case):
        if case.get('productId') or case.get('id'):
            return str(case.get('productId') or case['id'])
        return next((str(r['id']) for r in self.resources.get('products', []) if r.get('productCode') == case.get('productCode')), None)

    def forecast(self, case):
        product_id = self.product_id(case)
        if not product_id:
            raise ValueError('Cannot resolve forecast product')
        status, data = self.client.request('GET', '/ai/inventory/products/' + quote(product_id) + '/forecast', token=self.admin)
        data = data if isinstance(data, dict) else {}
        daily = rows(data.get('dailyProjection'))
        dates = [r.get('date') for r in daily]
        numbers_ok = all(math.isfinite(float(r.get(k, math.nan))) for r in daily for k in ('forecastDemand', 'incomingSupply', 'projectedInventory'))
        expected = case.get('expectedRisk') or case.get('stockoutRisk')
        if status != 200 or len(daily) != 28:
            self.record('forecast.' + str(case.get('productCode', product_id)), False,
                        {'status': status, 'dailyRows': len(daily), 'expectedRisk': expected, 'code': error_code(data)})
            return
        parsed_dates = [datetime.fromisoformat(v) for v in dates]
        contiguous = all((b-a).days == 1 for a, b in zip(parsed_dates, parsed_dates[1:]))
        projected = decimal(data.get('currentInventory'))
        arithmetic_ok = True
        for point in daily:
            demand, incoming = decimal(point.get('forecastDemand')), decimal(point.get('incomingSupply'))
            projected += incoming - demand
            arithmetic_ok = (arithmetic_ok and demand >= 0 and incoming >= 0
                             and abs(projected-decimal(point.get('projectedInventory'))) <= Decimal('0.001'))
        horizons_ok = True
        for horizon in (7, 14, 28):
            for summary, field in (('forecastDemand', 'forecastDemand'), ('incomingSupply', 'incomingSupply')):
                total = sum((decimal(r.get(field)) for r in daily[:horizon]), Decimal('0'))
                reported = decimal((data.get(summary) or {}).get(f'next{horizon}Days'))
                horizons_ok = horizons_ok and abs(total-reported) <= Decimal('0.001')
            reported_projected = decimal((data.get('projectedInventory') or {}).get(f'next{horizon}Days'))
            horizons_ok = horizons_ok and abs(reported_projected-decimal(daily[horizon-1].get('projectedInventory'))) <= Decimal('0.001')
        passed = (status == 200 and len(daily) == 28 and len(set(dates)) == 28 and data.get('historyDays') == 365
                  and data.get('historySource') == 'M5' and data.get('stockoutRisk') == expected and numbers_ok
                  and contiguous and arithmetic_ok and horizons_ok)
        detail = {k: data.get(k) for k in ('productCode', 'historyDays', 'historySource', 'stockoutRisk', 'projectedStockoutDate',
                                          'forecastDemand', 'incomingSupply', 'projectedInventory', 'modelVersion')}
        detail.update({'status': status, 'dailyRows': len(daily), 'expectedRisk': expected, 'code': error_code(data),
                       'contiguousDates': contiguous, 'dailyArithmeticValid': arithmetic_ok, 'horizonTotalsValid': horizons_ok})
        self.record('forecast.' + str(case.get('productCode', product_id)), passed, detail)

    def no_history(self):
        case = {'productId': self.catalog.get('noHistoryProductId'), 'productCode': self.catalog.get('noHistoryProductCode', 'DEMO-SP-007')}
        product_id = self.product_id(case)
        if not product_id:
            raise ValueError('Cannot find no-history product; set noHistoryProductId in catalog')
        status, data = self.client.request('GET', '/ai/inventory/products/' + quote(product_id) + '/forecast', token=self.admin)
        self.record('forecast.noHistoryRejected', status == 400, {'status': status, 'expectedStatus': 400, 'code': error_code(data)})

    def event_list(self):
        fixture = self.catalog.get('fixtureData') or {}
        events = rows(self.catalog.get('events')) or rows(fixture.get('events')) or rows(fixture.get('external-events'))
        return list({str(r.get('id') or r.get('eventId')): r for r in events if r.get('id') or r.get('eventId')}.values())

    def event_read(self, event, suffix):
        event_id = str(event.get('id') or event['eventId'])
        code = str(event.get('externalEventId') or event.get('sourceEventId') or event_id)
        status, data = self.client.request('GET', '/ai/external-events/' + quote(event_id) + '/' + suffix, token=self.admin)
        loaded = rows(data)
        if status == 200:
            (self.sentiments if suffix == 'sentiments' else self.impacts)[event_id] = loaded
        self.record('events.' + code + '.' + suffix + '.read', status == 200, {'status': status, 'rows': len(loaded)})

    def event_consistency(self):
        en, vi, bad = [], [], []
        for event_id, analyses in self.sentiments.items():
            seen = set()
            for row in analyses:
                entity = ' '.join(str(row.get('entity', '')).casefold().split())
                if not entity or entity in seen:
                    bad.append({'eventId': event_id, 'entity': row.get('entity'), 'reason': 'Duplicate/empty target'})
                seen.add(entity)
                try:
                    confidence, probabilities = decimal(row.get('confidence')), row.get('probabilities') or {}
                    probs = [decimal(v) for v in probabilities.values()]
                    if (confidence < 0 or confidence > 1 or len(probs) != 3
                            or set(probabilities) != {'NEGATIVE', 'NEUTRAL', 'POSITIVE'}
                            or any(v < 0 or v > 1 for v in probs) or abs(sum(probs) - 1) > Decimal('0.00005')
                            or abs(decimal(probabilities.get(row.get('sentiment'))) - confidence) > Decimal('0.00005')):
                        raise ValueError('Invalid probabilities')
                except Exception:
                    bad.append({'eventId': event_id, 'entity': row.get('entity'), 'reason': 'Invalid confidence/probabilities'})
                if row.get('language') == 'en':
                    en.append(row)
                if row.get('language') == 'vi':
                    vi.append(row)
        targets = {str(r.get('entity')).strip().casefold() for r in en}
        suppliers = {r.get('supplierId') for r in en if r.get('supplierId')}
        self.record('sentiment.englishTargetsIsolated', len(targets) >= 2 and len(suppliers) >= 2 and not bad,
                    {'englishRows': len(en), 'distinctTargets': len(targets), 'distinctLinkedSuppliers': len(suppliers), 'malformed': bad})
        vi_bad = [{'entity': r.get('entity'), 'needsReview': r.get('needsReview'), 'reviewReason': r.get('reviewReason')}
                  for r in vi if r.get('needsReview') is not True or r.get('reviewReason') != 'DOMAIN_NOT_VALIDATED']
        self.record('sentiment.vietnameseNewsNeedsReview', bool(vi) and not vi_bad, {'vietnameseRows': len(vi), 'invalidRows': vi_bad})
        for i, case in enumerate(self.catalog.get('sentimentCases') or []):
            event_id = str(case.get('eventId') or case.get('externalEventId') or '')
            selected = [r for r in self.sentiments.get(event_id, []) if str(r.get('entity', '')).strip().casefold() == str(case.get('entity', '')).strip().casefold()]
            contract_matches = len(selected) == 1
            if contract_matches:
                for expected, actual in (('language', 'language'), ('supplierCode', 'supplierCode'),
                                         ('expectedNeedsReview', 'needsReview')):
                    if expected in case:
                        contract_matches = contract_matches and case[expected] == selected[0].get(actual)
            actual_label = selected[0].get('sentiment') if len(selected) == 1 else None
            semantic_matches = ('expectedSentiment' not in case or case['expectedSentiment'] == actual_label)
            matches = contract_matches and semantic_matches
            known = contract_matches and not semantic_matches and case.get('knownModelLimitation') is True
            detail = {'eventId': event_id, 'entity': case.get('entity'), 'matchingRows': len(selected),
                      'expectedSentiment': case.get('expectedSentiment'), 'actualSentiment': actual_label,
                      'contractValid': contract_matches, 'semanticLabelMatches': semantic_matches,
                      'knownModelLimitation': known}
            if len(selected) == 1:
                detail.update({k: selected[0].get(k) for k in
                               ('language', 'supplierCode', 'confidence', 'needsReview', 'reviewReason', 'modelVersion')})
            if known:
                detail['note'] = 'Expected label is preserved. The current model predicts a different label; this is not a passed semantic check.'
            self.record('sentiment.catalogCase.' + str(i + 1), matches, detail,
                        status='KNOWN_LIMITATION' if known else None)
        all_impacts, bad_impacts = [], []
        for event_id, impacts in self.impacts.items():
            for row in impacts:
                all_impacts.append(row)
                try:
                    score = decimal(row.get('impactScore'))
                    if score < 0 or score > 100:
                        raise ValueError('Impact out of range')
                except Exception:
                    bad_impacts.append({'eventId': event_id, 'supplierCode': row.get('supplierCode')})
        self.record('impacts.scoresFinite0To100', bool(all_impacts) and not bad_impacts, {'rows': len(all_impacts), 'invalidRows': bad_impacts})
        unrelated = [r for r in self.event_list() if str(r.get('externalEventId') or r.get('sourceEventId') or '').endswith('04')]
        if self.catalog.get('unrelatedEventId'):
            unrelated = [{'id': self.catalog['unrelatedEventId']}]
        forbidden = {f'DEMO-NCC-{i:02d}' for i in range(1, 5)} | {f'DEMO-NCC-{i:03d}' for i in range(1, 5)}
        forbidden.update(self.catalog.get('unrelatedExcludedSupplierCodes') or [])
        unwanted = [r.get('supplierCode') for event in unrelated
                    for r in self.impacts.get(str(event.get('id') or event.get('eventId')), []) if r.get('supplierCode') in forbidden]
        self.record('impacts.unrelatedEventDoesNotAffectDemoSuppliers', bool(unrelated) and not unwanted,
                    {'unrelatedEvents': len(unrelated), 'unexpectedSuppliers': unwanted})

    def inactive(self):
        username = self.catalog.get('inactiveUsername', 'demo_member_20')
        # One login attempt only: no retry or rate-limit loop.
        status, data = self.client.request('POST', '/auth/login', body={'username': username, 'password': self.password})
        self.record('auth.inactiveUserRejected', status == 401, {'username': username, 'status': status, 'expectedStatus': 401, 'code': error_code(data)})

    def invalid_inventory(self):
        fixture = self.catalog.get('fixtureData') or {}
        fixture_ids = {r.get('id') for r in rows(fixture.get('inventory')) if r.get('id')}
        demo_products = {r.get('id') for r in self.resources.get('products', []) if str(r.get('productCode', '')).startswith('DEMO-')}
        candidates = [r for r in self.resources.get('inventory', []) if r.get('id') in fixture_ids or r.get('productId') in demo_products]
        if not candidates:
            raise ValueError('No demo inventory row; no invalid PUT sent')
        row_id = str(candidates[0]['id'])
        status, before = self.client.request('GET', '/inventory/' + quote(row_id), token=self.admin)
        if status != 200 or not isinstance(before, dict):
            raise ValueError('Cannot read inventory row; no invalid PUT sent')
        payload = {k: before[k] for k in ('warehouseId', 'productId', 'lowStockThreshold')}
        payload['quantity'] = -1
        status, data = self.client.request('PUT', '/inventory/' + quote(row_id), token=self.admin, body=payload)
        after_status, after = self.client.request('GET', '/inventory/' + quote(row_id), token=self.admin)
        unchanged = after_status == 200 and after == before
        self.record('validation.negativeInventoryRejectedUnchanged', status == 400 and unchanged,
                    {'inventoryId': row_id, 'status': status, 'expectedStatus': 400, 'afterStatus': after_status,
                     'unchanged': unchanged, 'code': error_code(data)})

    def run(self, invalid_inventory):
        started = datetime.now(timezone.utc).isoformat()
        for username, role in CORE.items():
            username = self.username if role == 'ADMIN' else username
            self.attempt('auth.' + username, lambda u=username, r=role: self.login(u, r))
        self.attempt('rbac', self.rbac)
        for endpoint in ('companies', 'departments', 'employees', 'suppliers', 'product-categories', 'products',
                         'warehouses', 'inventory', 'purchase-orders', 'shipments', 'risk-events', 'alerts', 'users'):
            self.attempt('resources.' + endpoint, lambda e=endpoint: self.load(e))
        for user in self.resources.get('users', []):
            name = user.get('username', '')
            if name.startswith('demo_member_') and user.get('status') == 'ACTIVE':
                role = user['roles'][0]
                self.attempt('auth.' + name, lambda u=name, r=role: self.login(u, r))
        for username, role in CORE.items():
            token = self.tokens.get(self.username if role == 'ADMIN' else username)
            for path, allowed in [('products', {'ADMIN','MANAGER','STAFF'}), ('alerts', {'ADMIN','MANAGER'}), ('companies', {'ADMIN'})]:
                expected_status = 400 if role in allowed else 403
                status, data = self.client.request('POST', '/' + path, token=token, body={})
                self.record('rbac.invalidPayload.' + username + '.' + path, status == expected_status,
                            {'status':status,'expectedStatus':expected_status,'note':'Empty required fields prevent creation even when authorized.'})
        for endpoint in ('users', 'suppliers'):
            self.attempt('pagination.' + endpoint, lambda e=endpoint: self.page(e))
        self.attempt('consistency', self.consistency)
        cases = self.catalog.get('forecastCases') or []
        self.record('forecast.caseCount', len(cases) == 6, {'count': len(cases), 'expected': 6})
        for case in cases:
            self.attempt('forecast.' + str(case.get('productCode', 'unknown')), lambda c=case: self.forecast(c))
        self.attempt('forecast.noHistoryRejected', self.no_history)
        events = self.event_list()
        self.record('events.catalogAvailable', bool(events), {'count': len(events)})
        for event in events:
            for suffix in ('sentiments', 'impacts'):
                self.attempt('events.read.' + suffix, lambda e=event, s=suffix: self.event_read(e, s))
        self.attempt('events.consistency', self.event_consistency)
        self.attempt('auth.inactiveUserRejected', self.inactive)
        if invalid_inventory:
            self.attempt('validation.negativeInventoryRejectedUnchanged', self.invalid_inventory)
        passed = sum(r['passed'] is True for r in self.checks)
        failed = sum(r['passed'] is False for r in self.checks)
        known = [r for r in self.checks if r.get('status') == 'KNOWN_LIMITATION']
        technical_passed = bool(self.checks) and failed == 0
        return {'dataset': self.catalog.get('dataset'), 'seedDate': self.catalog.get('seedDate'),
                'baseUrl': self.client.base, 'startedAt': started, 'completedAt': datetime.now(timezone.utc).isoformat(),
                'passed': technical_passed, 'technicalChecksPassed': technical_passed,
                'status': ('FAIL' if not technical_passed else 'PASS_WITH_KNOWN_LIMITATIONS' if known else 'PASS'),
                'completeWithoutLimitations': technical_passed and not known,
                'checkCount': len(self.checks), 'passedChecks': passed, 'failedChecks': failed,
                'knownLimitationCount': len(known), 'knownLimitations': known,
                'activity': {'validDataWrites': False, 'authenticationRequests': True,
                             'invalidInventoryPutEnabled': invalid_inventory, 'invalidPayloadAuthorizationProbes': True,
                             'note': 'No reseed, delete, valid update, AI refresh, ingestion or integration test.'},
                'resourceCounts': {k: len(v) for k, v in self.resources.items()}, 'checks': self.checks}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--catalog', type=Path, default=Path(r'D:\Java\enterprise-risk-backend\demo-data\full-system-v1\full-demo-catalog.json'))
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--base-url', default='http://127.0.0.1:5173/api')
    parser.add_argument('--username', default='admin')
    parser.add_argument('--password', default='123456', help='Local demo password; never written to report')
    parser.add_argument('--timeout', type=float, default=90)
    parser.add_argument('--check-invalid-inventory', action='store_true', help='One quantity=-1 PUT; require 400 and unchanged row')
    args = parser.parse_args()
    try:
        catalog = json.loads(args.catalog.read_text(encoding='utf-8-sig'))
        if not isinstance(catalog, dict):
            raise ValueError('Catalog must be a JSON object')
        report = Verify(Client(args.base_url, args.timeout), catalog, args.username, args.password).run(args.check_invalid_inventory)
    except Exception as exc:
        report = {'passed': False, 'errorType': type(exc).__name__, 'message': str(exc),
                  'completedAt': datetime.now(timezone.utc).isoformat()}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(clean(report), ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print('Verification report: ' + str(args.output.resolve()), flush=True)
    print('Result: ' + report.get('status', 'FAIL'), flush=True)
    return 0 if report.get('passed') else 1


if __name__ == '__main__':
    sys.exit(main())
