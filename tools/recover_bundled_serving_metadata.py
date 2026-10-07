#!/usr/bin/env python3
"""Recover explicit one-roll package metadata without changing any food identity/nutrient.

The importer now emits this metadata for future generation. This repair keeps the
existing curated product set and all reference nutrition byte-for-byte as fields.
"""
from __future__ import annotations
import csv
import hashlib
import json
from pathlib import Path
import openpyxl
from import_kfind_processed_foods import package_unit, parse_amount

ROOT = Path(__file__).resolve().parents[1]

def recover(source_path: Path, asset_path: Path) -> dict:
    expected_sha = 'B074D98E75D2D087DC1B193F0056AFFBF9AFD14524C9CCB556CB2B20978504F7'
    if hashlib.sha256(source_path.read_bytes()).hexdigest().upper() != expected_sha:
        raise ValueError('Unexpected original workbook SHA')
    with asset_path.open(encoding='utf-8', newline='') as source:
        reader = csv.DictReader(source)
        fields, rows = reader.fieldnames, list(reader)
    workbook = openpyxl.load_workbook(source_path, read_only=True, data_only=True)
    source_iterator = workbook.active.iter_rows(values_only=True)
    headers = list(next(source_iterator))
    positions = {key: index for index, key in enumerate(headers)}
    source_rows = {}
    bundled_codes = {row['sourceFoodCode'] for row in rows}
    for values in source_iterator:
        code = values[positions['식품코드']]
        if code not in bundled_codes:
            continue
        original = dict(zip(headers, values))
        if package_unit(original['식품명'], original['대표식품명'], original['식품소분류명'],
                        str(original.get('1회 섭취참고량') or ''), parse_amount(original['식품중량'])) == '줄':
            source_rows[code] = original
    workbook.close()
    changes = []
    for row in rows:
        original = source_rows.get(row['sourceFoodCode'])
        if original is None:
            continue
        total = parse_amount(original['식품중량'])
        unit = package_unit(original['식품명'], original['대표식품명'], '', str(original.get('1회 섭취참고량') or ''), total)
        if unit != '줄':
            continue
        for field, original_field in (
            ('name','식품명'), ('energyKcal','에너지(kcal)'), ('carbohydrateGrams','탄수화물(g)'),
            ('proteinGrams','단백질(g)'), ('fatGrams','지방(g)')
        ):
            a, b = row[field], original[original_field]
            if field == 'name' and a != b or field != 'name' and float(a) != float(b):
                raise ValueError(f'Source mismatch: {row["sourceFoodCode"]} {field}')
        if parse_amount(original['영양성분함량기준량']) != (float(row['referenceAmount']), row['unit']):
            raise ValueError('Reference basis mismatch')
        if f'공식 총내용량 {total[0]:g}{total[1]}' not in row['servingDescription']:
            raise ValueError('Original total was not preserved')
        if '포장단위 줄' not in row['servingDescription']:
            if '포장단위' in row['servingDescription']:
                raise ValueError('Conflicting existing package unit')
            row['servingDescription'] += ' · 포장단위 줄'
            changes.append({'sourceFoodCode':row['sourceFoodCode'],'name':row['name'],'oneRollGrams':total[0]})
    with asset_path.open('w', encoding='utf-8', newline='') as output:
        writer = csv.DictWriter(output, fieldnames=fields, lineterminator='\n')
        writer.writeheader()
        writer.writerows(rows)
    return {'foodCount':len(rows),'changedMetadata':changes,'nutritionChanges':0,'identityChanges':0,
            'outputSha256':hashlib.sha256(asset_path.read_bytes()).hexdigest().upper()}

if __name__ == '__main__':
    source_path = ROOT / 'data-source/kfind/kfind-processed-food-db-2026-08-28.xlsx'
    result = recover(source_path, ROOT / 'app/src/main/assets/fooddata/product_items.csv')
    if result['changedMetadata']:
        manifest_path = ROOT / 'app/src/main/assets/fooddata/food_data_manifest.properties'
        manifest = manifest_path.read_text(encoding='utf-8').splitlines()
        replacement = {
            'bundleId': 'FOOD-AMOUNT-2026-10-02-' + result['outputSha256'][:12],
            'productAssetSha256': result['outputSha256'],
        }
        manifest_path.write_text('\n'.join(
            f'{line.split("=", 1)[0]}={replacement[line.split("=", 1)[0]]}'
            if line.split('=', 1)[0] in replacement else line for line in manifest
        ) + '\n', encoding='utf-8')
    output = ROOT / 'app/build/amount-qa/recovered-serving-metadata.json'
    output.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps(result, ensure_ascii=False))
