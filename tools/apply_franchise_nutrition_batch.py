"""Apply an exact-baseline nutrition patch to a local development catalog only."""
import argparse
import hashlib
import json
from datetime import datetime, timezone
from pathlib import Path
from franchise_daily_sync import CONFIG, validate, atomic_write
from franchise_nutrition import retain_verified_on_conflict, validate_nutrition, status

ROOT = Path(__file__).resolve().parents[1]

def apply_batch(raw, patch):
    assert patch['schemaVersion'] == 1 and patch['environment'] == 'DEVELOPMENT_ONLY_NOT_PUBLISHED'
    assert hashlib.sha256(raw).hexdigest() == patch['baseCatalogSha256'], 'Baseline catalog changed; revalidate first'
    catalog = json.loads(raw)
    menus = {row['id']: row for row in catalog['menus']}
    assert len(menus) == len(catalog['menus'])
    brands = {row['brandId']: row for row in json.loads(CONFIG.read_text(encoding='utf-8'))}
    seen = set()
    for item in patch['menus']:
        assert item['id'] not in seen, 'Duplicate patch menu'
        seen.add(item['id'])
        original = menus[item['id']]
        assert all(original[key] == item[key] for key in ('brand', 'name', 'externalId', 'sourceUrl'))
        fact = item['officialNutrition']
        assert fact['officialProductId'] == original['externalId']
        validate_nutrition(fact, brands[original['brandId']]['allowedHosts'])
        merged, conflicts = retain_verified_on_conflict(original, fact)
        assert not conflicts, 'Known official nutrition conflict; patch withheld'
        candidate = dict(original, officialNutrition=merged, nutritionStatus=status(merged))
        validate(candidate, brands[original['brandId']])
        menus[item['id']] = candidate
    return dict(catalog, menus=[menus[row['id']] for row in catalog['menus']])

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--catalog', required=True)
    parser.add_argument('--patch', required=True)
    parser.add_argument('--output', required=True)
    args = parser.parse_args()
    output = Path(args.output).resolve()
    assert output.is_relative_to((ROOT/'app/build').resolve()), 'Development output must stay in app/build'
    raw = Path(args.catalog).read_bytes()
    patch = json.loads(Path(args.patch).read_text(encoding='utf-8'))
    catalog = apply_batch(raw, patch)
    encoded = json.dumps(catalog, ensure_ascii=False, separators=(',', ':')).encode()
    digest = hashlib.sha256(encoded).hexdigest()
    manifest = dict(schemaVersion=1, file='catalog-'+digest+'.json', sha256=digest,
                    version=digest, menuCount=len(catalog['menus']),
                    publishedAt=datetime.now(timezone.utc).isoformat())
    output.mkdir(parents=True, exist_ok=True)
    atomic_write(output/manifest['file'], encoded)
    atomic_write(output/'latest.json', json.dumps(manifest).encode())
    print(json.dumps(dict(environment='DEVELOPMENT_ONLY', linkedMenus=len(patch['menus']), sha256=digest)))

if __name__ == '__main__':
    main()
