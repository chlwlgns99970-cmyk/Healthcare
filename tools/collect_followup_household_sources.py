"""Cache public official serving sources for the household-unit follow-up.

No keys or private APIs are used. Downloads are separate from deterministic audit.
"""
from __future__ import annotations
import argparse
import hashlib
import json
from pathlib import Path
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parents[1]
CACHE = ROOT / 'app/build/food-quality-followup/household-source-cache'
SOURCES = {
    'usda-sr-legacy.zip': 'https://fdc.nal.usda.gov/fdc-datasets/FoodData_Central_sr_legacy_food_csv_2018-04.zip',
    'usda-fields.pdf': 'https://fdc.nal.usda.gov/docs/Download_Field_Descriptions_Oct2020.pdf',
    'maeil-99-9-190ml.html': 'https://productguide.maeil.com/products/99-9-190ml',
    'maeil-organic-milk.html': 'https://productguide.maeil.com/products/ep-c41ce4ab896b',
    'maeil-lentil.html': 'https://productguide.maeil.com/products/190ml-6568cb24',
    'maeil-99-9-label.jpg': 'https://freshmaeil.cafe24.com/freshmaeil/05beverage/a_maeilsoy/MaeilSoy99_detail_07.jpg',
    'maeil-99-9-info.gif': 'https://freshmaeil.cafe24.com/freshmaeil/05beverage/a_maeilsoy/MaeilSoy99_detail_08.gif',
    'maeil-99-9-declaration.jpg': 'https://freshmaeil.cafe24.com/freshmaeil/05beverage/a_maeilsoy/MaeilSoy99_detail_09.jpg',
}

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--only', choices=SOURCES, action='append')
    args = parser.parse_args()
    CACHE.mkdir(parents=True, exist_ok=True)
    manifest_path = CACHE/'manifest.json'
    manifest = json.loads(manifest_path.read_text(encoding='utf-8')) if manifest_path.exists() else []
    entries = {row['file']: row for row in manifest}
    for name in args.only or SOURCES:
        path, url = CACHE/name, SOURCES[name]
        if not path.exists():
            request = Request(url, headers={'User-Agent': 'Mozilla/5.0'})
            with urlopen(request, timeout=40) as response:
                path.write_bytes(response.read())
        entries[name] = {'file': name, 'url': url,
                         'bytes': path.stat().st_size,
                         'sha256': hashlib.sha256(path.read_bytes()).hexdigest(),
                         'checkedAt': '2026-10-04'}
    manifest = sorted(entries.values(), key=lambda entry: entry['file'])
    manifest_path.write_text(json.dumps(manifest, indent=2), encoding='utf-8')
    print(json.dumps(manifest, ensure_ascii=False))

if __name__ == '__main__':
    main()
