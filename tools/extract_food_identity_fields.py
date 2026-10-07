"""Restore exact-code K-FIND identity/classification/provenance fields into a sidecar.

All columns (including hidden ones) are inspected. Nutrition assets are untouched.
Only stable food codes join rows; a name, intake reference or maker never creates
an ingredient, allergen or household portion. Re-running uses verified SHA caches.
"""
from pathlib import Path
import csv, hashlib, json, re, zipfile
from lxml import etree

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'app/src/main/assets/fooddata'
OUT = ROOT / 'data-source/food-quality'
VERSION = 'identity-fields-v1'
NS = '{http://schemas.openxmlformats.org/spreadsheetml/2006/main}'
SOURCE_URL = 'https://various.foodsafetykorea.go.kr/nutrient/general/down/list.do'
FIELDS = ('식품코드', '식품명', '식품기원명', '식품대분류명', '대표식품명', '식품중분류명',
          '식품소분류명', '식품세분류명', '영양성분함량기준량', '출처코드', '출처명',
          '1인(회)분량 참고량', '1회 섭취참고량', '식품중량', '업체명', '품목제조보고번호',
          '제조사명', '수입업체명', '유통업체명', '데이터생성방법명', '데이터생성일자', '데이터기준일자')

def rows(path):
    with path.open(encoding='utf-8-sig', newline='') as f:
        return list(csv.DictReader(f))

def cell_value(cell, shared):
    v = cell.find(NS + 'v')
    if v is None:
        return ''.join(cell.itertext()).strip() if cell.attrib.get('t') == 'inlineStr' else ''
    value = v.text or ''
    return shared[int(value)] if cell.attrib.get('t') == 's' else value

def extract(path, wanted):
    output, hidden, headers, scanned = [], [], {}, 0
    with zipfile.ZipFile(path) as archive:
        shared = []
        with archive.open('xl/sharedStrings.xml') as stream:
            for _, node in etree.iterparse(stream, events=('end',), tag=NS+'si'):
                shared.append(''.join(node.itertext()))
                node.clear()
                while node.getprevious() is not None: del node.getparent()[0]
        with archive.open('xl/worksheets/sheet1.xml') as stream:
            for _, node in etree.iterparse(stream, events=('end',), tag=(NS+'row', NS+'col')):
                if node.tag == NS+'col':
                    if node.attrib.get('hidden') in ('1', 'true'): hidden.append(dict(node.attrib))
                    continue
                cells = list(node)
                if node.attrib['r'] == '1':
                    headers = {re.sub(r'\d', '', c.attrib['r']): cell_value(c, shared) for c in cells}
                else:
                    scanned += 1
                    code = next((cell_value(c, shared) for c in cells if headers.get(re.sub(r'\d', '', c.attrib['r'])) == '식품코드'), '')
                    if code in wanted:
                        data = {headers[re.sub(r'\d', '', c.attrib['r'])]: cell_value(c, shared) for c in cells
                                if headers.get(re.sub(r'\d', '', c.attrib['r'])) in FIELDS}
                        output.append({field: data.get(field, '') for field in FIELDS})
                node.clear()
                while node.getprevious() is not None: del node.getparent()[0]
    return output, {'file': path.name, 'sha256': hashlib.sha256(path.read_bytes()).hexdigest().upper(),
                    'rowsScanned': scanned, 'columnsInspected': len(headers), 'allHeaders': list(headers.values()),
                    'hiddenColumns': hidden, 'ingredientFields': [h for h in headers.values() if '원재료' in h],
                    'allergenFields': [h for h in headers.values() if '알레르' in h],
                    'restoredFields': [h for h in FIELDS if h in headers.values()],
                    'sourceUrl': SOURCE_URL, 'checkedAt': '2026-10-04', 'parserVersion': VERSION}

def main():
    OUT.mkdir(exist_ok=True)
    existing = OUT / 'raw-identity-fields.csv'
    audit_path = OUT / 'original-field-audit.json'
    foods = rows(ASSETS/'food_items.csv') + rows(ASSETS/'product_items.csv')
    by_source = {}
    for food in foods:
        by_source.setdefault(food['sourceType'], {})[food['sourceFoodCode']] = food['id']
    checks = []
    for kind, filename in (('K-FIND','kfind-food-db-2026-08-28.xlsx'), ('K-FIND-PRODUCT','kfind-processed-food-db-2026-08-28.xlsx')):
        path = ROOT/'data-source/kfind'/filename
        checks.append((kind, path, hashlib.sha256(path.read_bytes()).hexdigest().upper()))
    if existing.exists() and audit_path.exists():
        audit = json.loads(audit_path.read_text(encoding='utf-8'))
        expected_ids = set(food['id'] for food in foods if food['sourceType'] in by_source and food['sourceType'].startswith('K-FIND'))
        if [x[2] for x in checks] == [x['sha256'] for x in audit] and {r['foodItemId'] for r in rows(existing)} == expected_ids:
            print('Verified cached exact-code source fields; originals and selected IDs unchanged.')
            return
    result, audit = [], []
    for kind, path, digest in checks:
        source_rows, report = extract(path, by_source[kind])
        for data in source_rows:
            result.append({'foodItemId': by_source[kind][data['식품코드']], 'sourceType': kind, **data})
        audit.append(report)
        print(f'{kind}: scanned {report["rowsScanned"]}, restored {len(source_rows)} exact IDs', flush=True)
    result.sort(key=lambda row: row['foodItemId'])
    assert len(result) == len({r['foodItemId'] for r in result})
    assert len(result) == sum(len(by_source[k]) for k in ('K-FIND','K-FIND-PRODUCT'))
    with existing.open('w', encoding='utf-8', newline='') as f:
        writer = csv.DictWriter(f, fieldnames=('foodItemId','sourceType',*FIELDS), lineterminator='\n')
        writer.writeheader(); writer.writerows(result)
    audit_path.write_text(json.dumps(audit, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')

if __name__ == '__main__': main()
