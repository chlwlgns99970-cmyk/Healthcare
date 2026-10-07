"""Rejudge the ten foods exceeding a source reference in 52 prior plan rows.

The official 음식 식품중량 field means a one-meal reference, not a health
maximum. Only eight exact g-only foods get a fixed reference choice; the two
rice foods retain their reviewed discrete bowl choices and 210g conversion.
"""
import csv
import hashlib
import json
import re
from extract_food_identity_fields import ROOT, OUT as QUALITY, SOURCE_URL, extract, rows

OUT = ROOT / 'data-source/catalog-recommendation'
SOURCE = ROOT / 'data-source/kfind/kfind-food-db-2026-08-28.xlsx'
VERSION = 'catalog-recommendation-portion-review-v1'
DECISIONS = {
    'D305-233000000-0001': ('소탕', 150, 'FIXED_MEASURED_REFERENCE', '국·탕 category가 600g까지 자동 확대해 원본 제사음식 1회 참고량의 4배를 선택함; 정확 식품코드 원본 150g를 고정 g 후보로 사용'),
    'D303-177000000-0002': ('팟타이', 100, 'FIXED_MEASURED_REFERENCE', '면 category가 350g까지 자동 확대해 원본 참고량의 3.5배를 선택함; 정확 식품코드 원본 100g를 고정 g 후보로 사용'),
    'D202-115000000-0001': ('토스트_갈릭스틱토스트', 41, 'FIXED_MEASURED_REFERENCE', '파리바게뜨의 정확 분석 제품 코드·이름·브랜드 확인; 빵 category 100g 확대는 원본 41g 참고량의 2.44배; 제품 개수는 추정하지 않고 41g 고정'),
    'D106-289000000-0001': ('소고기전골', 300, 'FIXED_MEASURED_REFERENCE', '전골 category 550g 확대는 원본 300g 참고량의 1.83배; kcal 부족을 채우는 확대 대신 정확 원본 300g 고정'),
    'D306-267000000-0001': ('꽁치찌개', 300, 'FIXED_MEASURED_REFERENCE', '찌개 category 550g 확대는 원본 300g 참고량의 1.83배; 정확 원본 300g 고정'),
    'D306-276000000-0001': ('두부전골', 500, 'FIXED_MEASURED_REFERENCE', '550g은 원본의 1.1배로 비현실적인 양이라고 단정하지 않음; g-only 음식에 정확 원본 500g 참고 후보가 있어 일반 category 확대 550g보다 이를 우선'),
    'D308-374000000-0001': ('돼지껍데기구이', 100, 'FIXED_MEASURED_REFERENCE', '150g은 개인 건강상한 위반이라고 단정하지 않음; 정확 원본 참고량 100g를 g-only 고정 후보로 제공하여 kcal 목표로 인한 확대 방지'),
    'D101-049000000-0001': ('하이라이스', 360, 'FIXED_MEASURED_REFERENCE', '400g은 원본의 1.11배로 비현실적인 양이라고 단정하지 않음; 정확 원본 참고량 360g를 g-only 고정 후보로 제공'),
    'D301-027000000-0001': ('오곡밥', 250, 'RETAIN_DISCRETE_HOUSEHOLD_CHOICES', '원본 250g은 1회 참고량이며 건강상한이 아님; 검증된 210g/공기와 0.5·1·1.5공기 후보 유지; 315g만으로 과도하다고 단정하지 않으며 1.19공기 같은 새 소수 환산을 만들지 않음'),
    'D101-006000000-0001': ('기장밥', 200, 'RETAIN_DISCRETE_HOUSEHOLD_CHOICES', '원본 200g은 1회 참고량이며 건강상한이 아님; 검증된 210g/공기와 0.5·1·1.5공기 후보 유지; 315g만으로 과도하다고 단정하지 않으며 0.95공기 같은 새 소수 환산을 만들지 않음'),
}

def build():
    OUT.mkdir(parents=True, exist_ok=True)
    previous = rows(QUALITY / 'followup-household-actual-plan-amount-audit.csv')
    exceeded = [r for r in previous if r['selectedExceedsOriginalWeight'] == 'true']
    assert len(previous) == 52 and len(exceeded) == 16
    assert {r['sourceFoodCode'] for r in exceeded} == set(DECISIONS)
    official, source_audit = extract(SOURCE, DECISIONS)
    official = {r['식품코드']: r for r in official}
    foods = {r['sourceFoodCode']: r for r in rows(ROOT / 'app/src/main/assets/fooddata/food_items.csv') if r['sourceFoodCode'] in DECISIONS}
    definitions = json.loads((QUALITY / 'followup-household-food-weight-definition-source.json').read_text(encoding='utf-8'))
    assert next(r for r in definitions['definitions'] if r['tableScope'] == '음식')['rawDefinition'] == '음식의 1회 분량'
    evidence, review = [], []
    for code, (name, amount, decision, reason) in DECISIONS.items():
        source = official[code]; food = foods[code]
        assert source['식품명'] == name and source['식품중량'] == f'{amount}g', (code, source)
        assert source['영양성분함량기준량'] == '100g'
        assert food['referenceAmount'] == '100' and food['unit'] == 'g'
        assert source['업체명'] == food['brand'] or (source['업체명'] == '해당없음' and not food['brand'])
        selected = sorted({float(r['selectedBasisAmount']) for r in exceeded if r['sourceFoodCode'] == code})
        review.append(dict(foodItemId=food['id'], sourceFoodCode=code, name=name, brand=food['brand'],
                           priorSelectedGrams='|'.join(format(v, 'g') for v in selected), originalReferenceGrams=amount,
                           decision=decision, selectedCandidateGrams=amount if decision.startswith('FIXED') else '105|210|315',
                           householdUnitPreserved='g' if decision.startswith('FIXED') else '공기',
                           reason=reason, originalSourceUrl=SOURCE_URL, fieldDefinitionUrl=definitions['officialPublicationUrl'],
                           sourceDate=source['데이터생성일자'], checkedAt='2026-10-04', parserVersion=VERSION,
                           healthMaximum=False, blanketCapApplied=False))
        if not decision.startswith('FIXED'): continue
        evidence.append(dict(foodItemId=food['id'], sourceType=food['sourceType'], sourceFoodCode=code,
                             name=food['name'], brand=food['brand'], recommendationReferenceAmount=amount,
                             recommendationReferenceUnit='g', recommendationSourceReference=SOURCE_URL,
                             nutritionReferenceAmount='100', nutritionReferenceUnit='g',
                             rawNutritionBasis=source['영양성분함량기준량'], rawFoodWeight=source['식품중량'],
                             rawPersonServingReference=source['1인(회)분량 참고량'], rawIntakeReference=source['1회 섭취참고량'],
                             sourceName='식품의약품안전처 K-FIND 원본 1회 음식 참고 분량', sourceUrl=SOURCE_URL,
                             sourceDate=source['데이터생성일자'], sourceVersion=SOURCE.name, checkedAt='2026-10-04', parserVersion=VERSION,
                             identityEvidence=f'EXACT_KFIND_SOURCE_FOOD_CODE:{code}|ORIGINAL_NAME_BRAND_MATCH|COLUMN:식품중량',
                             evidenceKind='EXACT_OFFICIAL_ONE_MEAL_REFERENCE_FIXED_MEASURED_CHOICE_NOT_HEALTH_MAXIMUM',
                             sourceSha256=source_audit['sha256'], notes=reason+'; 100g 영양 기준과 별도; 생활단위 개수·그릇·인분 환산과 개인 건강상한을 주장하지 않음'))
    for filename, records in [('portion-evidence.csv', evidence), ('portion-rejudgement.csv', review)]:
        with (OUT / filename).open('w', encoding='utf-8', newline='') as handle:
            writer=csv.DictWriter(handle, fieldnames=list(records[0]), lineterminator='\n'); writer.writeheader(); writer.writerows(records)
    report=dict(checkedAt='2026-10-04', parserVersion=VERSION, previousActualPlanRows=52,
                rowsExceedingOriginalOneMealReference=16, distinctFoodsReviewed=10,
                fixedGOnlyReferenceChoices=8, discreteHouseholdFoodsRetained=2,
                referenceMeaning='음식의 1회 분량', healthMaximumIntroduced=False, blanketCapApplied=False,
                retainedEarlierExactChoices=dict(어탕='150g', 멸치볶음='50g'),
                originalNutritionBasisChanged=0, templateAssetsChanged=0, sourceSha256=source_audit['sha256'])
    (OUT / 'portion-rejudgement-summary.json').write_text(json.dumps(report, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
    print(json.dumps(report, ensure_ascii=False))

if __name__ == '__main__': build()
