"""Exact-identity serving evidence and full food household-expectation audit.

Names classify audit targets only; they never provide a conversion. All new
servings come from a reviewed exact FDC portion or exact manufacturer report
number. Raw nutrition, original IDs, display names, categories and basis are
read-only. Runtime results are produced by targeted Kotlin policy tests.
"""
from __future__ import annotations
import argparse
from collections import Counter
import csv
import hashlib
import io
import json
import math
from pathlib import Path
import re
import zipfile

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT/'data-source/food-quality'
CACHE = ROOT/'app/build/food-quality-followup/household-source-cache'
DATE = '2026-10-04'
VERSION = 'household-exact-evidence-v1'
FDC_URL = 'https://fdc.nal.usda.gov/fdc-datasets/FoodData_Central_sr_legacy_food_csv_2018-04.zip'
HEADERS = ('foodItemId','sourceType','sourceFoodCode','name','brand','householdUnit',
           'basisAmountPerUnit','basisUnit','servingEvidenceKind','servingSourceReference',
           'servingSourceSize','sourceUrl','sourceFoodName','fullUnitPhrase','checkedAt',
           'sourceDate','parserVersion','identityEvidence','sourceHash')
MANUFACTURER_HEADERS = ('foodItemId','sourceFoodCode','brand','name','ingredients','ingredientText',
    'completeIngredientText','ingredientStatus','allergens','allergenText','allergenStatus',
    'mayContainAllergens','crossContactText','sourceUrl','checkedAt','sourceDate','parserVersion',
    'evidenceKind','staleCandidate','sourceHash','productReportNumber','householdUnit',
    'basisAmountPerUnit','basisUnit','servingEvidenceKind','servingSourceReference','servingSourceSize',
    'foodGroups','foodGroupEvidenceScope')

def read(path):
    if not path.exists():
        return []
    with path.open(encoding='utf-8-sig', newline='') as handle:
        return list(csv.DictReader(handle))

def write(path, fields, rows):
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open('w', encoding='utf-8', newline='') as handle:
        output = csv.DictWriter(handle, fieldnames=fields, lineterminator='\n')
        output.writeheader()
        output.writerows(rows)

def compact(value):
    return f'{value:.10f}'.rstrip('0').rstrip('.')

def verified_cache():
    entries = json.loads((CACHE/'manifest.json').read_text(encoding='utf-8'))
    for row in entries:
        path = CACHE/row['file']
        if path.stat().st_size != row['bytes'] or hashlib.sha256(path.read_bytes()).hexdigest() != row['sha256']:
            raise ValueError('Cached source hash/size mismatch: '+row['file'])
        if not row['url'].startswith('https://'):
            raise ValueError('Unverified source scheme')
    return {row['file']: row for row in entries}

def archive_rows(archive, name):
    path = next(p for p in archive.namelist() if p.endswith('/'+name))
    return list(csv.DictReader(io.TextIOWrapper(archive.open(path), encoding='utf-8-sig')))

def fdc_evidence(foods, review, manifest):
    if manifest['usda-sr-legacy.zip']['sha256'] != review['fdcArchiveSha256']:
        raise ValueError('Unexpected official FDC source')
    with zipfile.ZipFile(CACHE/'usda-sr-legacy.zip') as archive:
        original = {row['fdc_id']: row for row in archive_rows(archive, 'food.csv')}
        portions = {row['id']: row for row in archive_rows(archive, 'food_portion.csv')}
    by_code = {food['sourceFoodCode']: food for food in foods if food['sourceType']=='USDA-SR-LEGACY'}
    results = []
    facts = []
    for selected in review['fdcSelections']:
        food = by_code['FDC-'+selected['fdcId']]
        portion = portions[selected['portionId']]
        if portion['fdc_id'] != selected['fdcId'] or portion['modifier'] != selected['expectedModifier']:
            raise ValueError('FDC portion identity/modifier mismatch')
        if food['unit'] != 'g' or float(food['referenceAmount']) != 100:
            raise ValueError('FDC nutrition basis changed')
        count, grams = float(portion['amount']), float(portion['gram_weight'])
        if not math.isfinite(count) or not math.isfinite(grams) or count<=0 or grams<=0:
            raise ValueError('Invalid FDC portion amount')
        phrase = f"{compact(count)} {portion['modifier']} = {compact(grams)}g"
        results.append(dict.fromkeys(HEADERS, '') | {
            'foodItemId': food['id'], 'sourceType': food['sourceType'], 'sourceFoodCode': food['sourceFoodCode'],
            'name': food['name'], 'brand': food['brand'], 'householdUnit': selected['householdUnit'],
            'basisAmountPerUnit': compact(grams/count), 'basisUnit': 'g',
            'servingEvidenceKind': 'VERIFIED_CONVERSION', 'servingSourceReference': FDC_URL,
            'servingSourceSize': selected['sourceSize']+' · '+phrase,
            'sourceUrl': FDC_URL, 'sourceFoodName': original[selected['fdcId']]['description'],
            'fullUnitPhrase': phrase, 'checkedAt': DATE,
            'sourceDate': original[selected['fdcId']]['publication_date'], 'parserVersion': VERSION,
            'identityEvidence': f"USDA-SR-LEGACY:{food['sourceFoodCode']}; food_portion.id={portion['id']}",
            'sourceHash': manifest['usda-sr-legacy.zip']['sha256']})
        facts.append({'originalFood': original[selected['fdcId']], 'originalPortion': portion})
    return results, facts

def maeil_evidence(foods, raw, review, manifest):
    item = review['manufacturer']
    if manifest['maeil-99-9-declaration.jpg']['sha256'] != item['declarationImageSha256']:
        raise ValueError('Unexpected reviewed ingredient label')
    html = (CACHE/'maeil-99-9-190ml.html').read_text(encoding='utf-8')
    if any(number not in html for number in item['reportNumbers']) or item['packageText'] not in html:
        raise ValueError('Official public report number or pack phrase missing')
    sources = {r['foodItemId']: r for r in raw}
    results, manufacturer = [], []
    for number in item['reportNumbers']:
        matches = [food for food in foods if (original:=sources.get(food['id']))
                   and original.get('품목제조보고번호')==number]
        if len(matches)!=1:
            raise ValueError('Manufacturer report number is not unique in the bundled asset')
        food, original = matches[0], sources[matches[0]['id']]
        if food['name']!=item['productName'] or food['sourceType']!='K-FIND-PRODUCT' or food['unit']!='ml' or original['식품중량']!=item['total']:
            raise ValueError('Manufacturer exact product name/type/basis/total mismatch')
        expected_factory = item['manufacturerF2'] if number=='19810227007211' else item['manufacturerF4']
        if re.sub(r'\s+', '', original['제조사명']) != re.sub(r'\s+', '', expected_factory):
            raise ValueError('Exact reported manufacturer factory mismatch')
        row = dict.fromkeys(HEADERS, '') | {
            'foodItemId':food['id'], 'sourceType':food['sourceType'], 'sourceFoodCode':food['sourceFoodCode'],
            'name':food['name'], 'brand':food['brand'], 'householdUnit':'팩',
            'basisAmountPerUnit':'190', 'basisUnit':'ml', 'servingEvidenceKind':'OFFICIAL_SERVING',
            'servingSourceReference':item['sourceUrl'], 'servingSourceSize':'공식 개별 멸균팩 190ml',
            'sourceUrl':item['sourceUrl'], 'sourceFoodName':'매일두유 99.9 190mL', 'fullUnitPhrase':item['packageText'],
            'checkedAt':DATE, 'sourceDate':item['guidePublicationDate'], 'parserVersion':VERSION,
            'identityEvidence':'EXACT_REPORT_NUMBER:'+number+'; exact name/manufacturer factory/190ml',
            'sourceHash':manifest['maeil-99-9-190ml.html']['sha256']}
        results.append(row)
        manufacturer.append(dict.fromkeys(MANUFACTURER_HEADERS,'') | {
            'foodItemId':food['id'], 'sourceFoodCode':food['sourceFoodCode'], 'brand':food['brand'], 'name':food['name'],
            'ingredients':'대두|식염|원액두유', 'ingredientText':item['fullIngredientText'],
            'completeIngredientText':item['fullIngredientText'], 'ingredientStatus':'COMPLETE_DECLARATION',
            'allergens':'대두', 'allergenText':item['allergenText'], 'allergenStatus':'CONFIRMED_LABEL',
            'mayContainAllergens':'견과류|달걀|땅콩|밀|우유', 'crossContactText':item['crossContactText'],
            'sourceUrl':item['sourceDetailUrl'], 'checkedAt':DATE, 'sourceDate':'', 'parserVersion':VERSION,
            'evidenceKind':row['identityEvidence']+'; VISUALLY_REVIEWED_FULL_PRODUCT_LABEL',
            'staleCandidate':'false', 'sourceHash':item['declarationImageSha256'], 'productReportNumber':number,
            'householdUnit':'팩', 'basisAmountPerUnit':'190','basisUnit':'ml',
            'servingEvidenceKind':'OFFICIAL_SERVING','servingSourceReference':item['sourceUrl'],
            'servingSourceSize':'공식 개별 멸균팩 190ml',
            'foodGroups':'LEGUME_SOY' if '대두' in item['fullIngredientText'] and item['allergenText']=='대두 함유' else '',
            'foodGroupEvidenceScope':'EXACT_FULL_PRODUCT_LABEL' if '대두' in item['fullIngredientText'] and item['allergenText']=='대두 함유' else ''})
    return results, manufacturer

def expectation(food):
    """Audit hint only. None of these names can provide a quantity or conversion."""
    name = food['name']
    if '김밥' in name and not any(x in name for x in ('김밥용','김밥김','김밥재료')):
        return '김밥/줄 조사'
    if re.fullmatch(r'(달걀|계란)(?:[_ ,].*)?', name) or food['sourceType']=='USDA-SR-LEGACY' and food['sourceFoodCode']=='FDC-173424':
        return '달걀/개 조사'
    if '라면' in name or '유탕면' in (food.get('category') or ''):
        return '라면/봉·컵 조사'
    if '밥' in name and not any(x in name for x in ('볶음밥','비빔밥','주먹밥','김밥','덮밥','컵밥','국밥')):
        return '밥/공기·제품 조사'
    if '피자' in name:
        return '피자/조각 조사'
    if '빵' in name or '토스트' in name:
        return '빵/개·조각 조사'
    if re.search(r'(우유|두유|콜라|사이다|주스|음료|팩|캔|병)',name):
        return '음료/병·캔·팩 조사'
    if food['sourceType']=='K-FIND-PRODUCT':
        return '포장제품/제품 전체 조사'
    return ''

def audit(foods, raw, evidence, runtime):
    originals = {r['foodItemId']:r for r in raw}
    serving = {r['foodItemId']:r for r in runtime}
    new = {r['foodItemId']:r for r in evidence}
    rows = []
    for food in foods:
        original = originals.get(food['id'], {})
        current = serving.get(food['id'], {})
        hint = expectation(food)
        is_fallback = current.get('quality') in ('WEIGHT_ONLY','VOLUME_ONLY','UNRESOLVED')
        status = ('EXACT_SERVING_EVIDENCE_READY' if food['id'] in new else
                  'RUNTIME_AUDIT_PENDING_FOR_NEW_IDENTITY' if not current else
                  'ORIGINAL_OR_RUNTIME_HOUSEHOLD_AVAILABLE' if current.get('quality') in ('OFFICIAL_SERVING','VERIFIED_CONVERSION') else
                  'HOUSEHOLD_EXPECTATION_NEEDS_EXACT_SOURCE' if hint and is_fallback else 'NO_AUDIT_HINT')
        rows.append({'foodItemId':food['id'],'sourceType':food['sourceType'],'sourceFoodCode':food['sourceFoodCode'],
          'name':food['name'],'brand':food['brand'],'category':food['category'],
          'nutritionBasis':food['referenceAmount']+food['unit'],'sourceServingDescription':food['servingDescription'],
          'runtimeUnit':current.get('servingUnit',''),'runtimeQuality':current.get('quality',''),
          'expectationHint':hint,'defaultGMlOrUnresolvedProblem':str(bool(hint and is_fallback)).lower(),
          'originalPackageSize':original.get('식품중량',''),
          'originalIntakeReference':original.get('1회 섭취참고량') or original.get('1인(회)분량 참고량',''),
          'productReportNumber':original.get('품목제조보고번호',''),'status':status,
          'investigation':('EXACT_OFFICIAL_PORTION_OR_REPORT_NUMBER' if food['id'] in new else
             'ALL_ORIGINAL_160_OR_166_COLUMNS_PREVIOUSLY_INSPECTED; exact household declaration absent in preserved fields' if original else
             'OFFICIAL_BUNDLE_SOURCE; no new name-derived conversion'),
          'servingEvidenceSource':new.get(food['id'],{}).get('servingSourceReference','')})
    return rows

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--runtime', type=Path, default=ROOT/'app/build/food-quality-followup/baseline-serving-audit.csv')
    args = parser.parse_args()
    foods = sum([read(ROOT/'app/src/main/assets/fooddata'/name) for name in
                 ('food_items.csv','product_items.csv','franchise_official_items.csv')],[])
    if len({f['id'] for f in foods}) != len(foods):
        raise ValueError('Duplicate food identity')
    raw = read(DATA/'raw-identity-fields.csv')
    review = json.loads((DATA/'followup-household-reviewed-sources.json').read_text(encoding='utf-8'))
    manifest = verified_cache()
    fdc, facts = fdc_evidence(foods,review,manifest)
    maeil, manufacturer = maeil_evidence(foods,raw,review,manifest)
    evidence = fdc+maeil
    if len({r['foodItemId'] for r in evidence}) != len(evidence):
        raise ValueError('Duplicate evidence identity')
    write(DATA/'followup-household-evidence.csv',HEADERS,evidence)
    write(DATA/'followup-manufacturer-evidence.csv',MANUFACTURER_HEADERS,manufacturer)
    rows = audit(foods,raw,evidence,read(args.runtime))
    write(DATA/'followup-household-audit.csv',rows[0].keys(),rows)
    summary = {'checkedAt':DATE,'parserVersion':VERSION,'auditedFoodIdentities':len(foods),
      'originalSidecarRows':len(raw),'runtimeAudit':str(args.runtime.relative_to(ROOT)),
      'exactServingEvidence':len(evidence),'fdcEvidence':len(fdc),'fdcPreviouslyExistingEggBanana':2,
      'newFdcHouseholdIdentities':len(fdc)-2,'manufacturerExactPackIdentities':len(maeil),
      'completeIngredientDeclarations':len(manufacturer),'confirmedAllergenDeclarations':len(manufacturer),
      'officialCrossContactDeclarations':len(manufacturer),
      'runtimeIdentitiesAudited':len(read(args.runtime)),
      'newIdentitiesAwaitingRuntimeAudit':sum(r['status']=='RUNTIME_AUDIT_PENDING_FOR_NEW_IDENTITY' for r in rows),
      'householdExpectedDefaultGMlOrUnresolved':sum(r['defaultGMlOrUnresolvedProblem']=='true' for r in rows),
      'householdExpectationBreakdown':dict(sorted(Counter(r['expectationHint'] for r in rows if r['defaultGMlOrUnresolvedProblem']=='true').items())),
      'foodNutritionChanges':0,'foodIdentityChanges':0,'densityOrAverageConversionsAdded':0,
      'fdcRawPortions':facts,'excluded':review['excluded'],
      'officialCachedSources':list(manifest.values()),
      'coverageMeaning':'Expectation hints are audit-only. An original source inspected without a household declaration does not certify a count unit; unresolved exact sources remain explicit and no name-only conversion is added.'}
    (DATA/'followup-household-summary.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({key: value for key,value in summary.items() if key not in ('fdcRawPortions','officialCachedSources','excluded')},ensure_ascii=False))

if __name__=='__main__':
    main()
