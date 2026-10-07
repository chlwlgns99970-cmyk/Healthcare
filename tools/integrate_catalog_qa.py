"""Idempotent additive consumer catalog integration. Existing nutrition and IDs are immutable."""
import csv, hashlib, json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'app/src/main/assets/fooddata'
SOURCE = ROOT / 'data-source/catalog-retail'

def read(path):
    with path.open(encoding='utf-8-sig', newline='') as f:
        reader = csv.DictReader(f)
        return list(reader.fieldnames), list(reader)

def main():
    baseline = ROOT / 'app/build/catalog-qa/baseline/app/src/main/assets/fooddata/product_items.csv'
    headers, original = read(baseline)
    _, additions = read(SOURCE / 'product-items.csv')
    _, patches = read(SOURCE / 'alias-patches.csv')
    result = {r['id']: dict(r) for r in original}
    for row in additions:
        assert row['id'] not in result, 'Addition conflicts with preserved identity: ' + row['id']
        assert row['sourceType'] in ('K-FIND-PRODUCT', 'OFFICIAL-RETAIL-PRODUCT')
        result[row['id']] = row
    for patch in patches:
        row = result[patch['foodItemId']]
        assert row['sourceFoodCode'] == patch['sourceFoodCode']
        aliases = set(row.get('aliases', '').split('|')) | set(patch['aliases'].split('|'))
        row['aliases'] = '|' + '|'.join(sorted(aliases - {''})) + '|'
    with (ASSETS / 'product_items.csv').open('w', encoding='utf-8', newline='') as f:
        writer = csv.DictWriter(f, fieldnames=headers, extrasaction='ignore', lineterminator='\n')
        writer.writeheader()
        writer.writerows(result.values())
    immutable = ('id', 'sourceType', 'sourceFoodCode', 'name', 'brand', 'referenceAmount', 'unit',
                 'energyKcal', 'carbohydrateGrams', 'proteinGrams', 'fatGrams', 'sodiumMilligrams')
    for old in original:
        assert all(old[k] == result[old['id']][k] for k in immutable)
    report = {'baseline':len(original), 'additional':len(additions), 'total':len(result),
              'aliasPatches':len(patches), 'preservedIdentitiesAndNutrition':True,
              'assetSha256':hashlib.sha256((ASSETS/'product_items.csv').read_bytes()).hexdigest().upper()}
    (ROOT/'data-source/catalog-qa/retail-integration.json').write_text(
        json.dumps(report, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
    print(json.dumps(report, ensure_ascii=False))

if __name__ == '__main__': main()
