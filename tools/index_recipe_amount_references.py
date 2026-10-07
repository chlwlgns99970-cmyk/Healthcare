"""Index official measured compositions without overwriting original recipe inputs.

KDCA's survey averages are dietary survey references, expressly not cooking
instructions. This index is separate from both the 516-recipe audit and app asset.
An unchanged food code alone is insufficient when its qualified name changed.
"""
import collections
import hashlib
import json
from pathlib import Path

from openpyxl import load_workbook
from adjudicate_recipe_inputs import ROOT, norm

OUT = ROOT / 'data-source/recipe-amount-priority'
DATE = '2026-10-05'


def load(path):
    return json.loads(path.read_text(encoding='utf-8'))


def save(name, value):
    (OUT / name).write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def build():
    captures = load(OUT / 'source-captures.json')
    source = next(r for r in captures if r.get('recipeSourceRecord') == 281 and r.get('fileSequence') == 2)
    raw = ROOT / source['rawFile']
    assert hashlib.sha256(raw.read_bytes()).hexdigest() == source['sha256']
    with raw.open('rb') as stream:
        workbook = load_workbook(stream, data_only=True, read_only=True)
        intro = '\n'.join(str(c) for row in workbook['이용자를 위하여'].values for c in row if c)
        assert '음식 조리를 위한 정보로 활용하기에는 적절하지 않습니다' in intro
        grouped = collections.defaultdict(list)
        for excel_row, row in enumerate(workbook['음식별 식품재료량 데이터베이스'].values, 1):
            if excel_row == 1 or not row[2]:
                continue
            group, food_group, dish, volume, ingredient, code, grams, marker, note = row
            assert isinstance(grams, (int, float)) and grams > 0
            grouped[group, dish].append(dict(excelRow=excel_row, group=group, foodGroup=food_group,
                dish=dish, volumeMl=volume, ingredient=ingredient, officialCode=code,
                grams=grams, marker=marker, note=note))
    assert len(grouped) == 613
    nutrients = load(ROOT / 'data-source/full-adjudication/rda-10.4-nutrients.json')
    by_code = {r['code']: r for r in nutrients}
    by_name = collections.defaultdict(list)
    for nutrient in nutrients:
        by_name[norm(nutrient['name'])].append(nutrient)
    amounts = load(OUT / 'amount-563-baseline.json')
    amount_names = {norm(d['recipeName']) for d in amounts}
    references = []
    for (institution, dish), rows in grouped.items():
        if norm(dish) not in amount_names:
            continue
        inputs = []
        for row in rows:
            candidate = by_code.get(row['officialCode'])
            exact_code = candidate is not None and norm(candidate['name']) == norm(row['ingredient'])
            exact_names = by_name[norm(row['ingredient'])]
            if not exact_code:
                candidate = exact_names[0] if len(exact_names) == 1 else None
            # Preserve all measured rows, including +/* rare-ingredient flags.
            nutrient = dict(candidate) if candidate else None
            inputs.append(row | dict(status='EXACT_VERIFIED' if nutrient else 'AMBIGUOUS',
                nutrient=nutrient,
                matchBasis='SAME_CODE_AND_QUALIFIED_NAME' if exact_code else
                    'UNIQUE_EXACT_QUALIFIED_NAME' if nutrient else 'NO_EXACT_QUALIFIED_NAME',
                estimatedKcal=row['grams'] * nutrient['energyKcal'] / 100 if nutrient else None))
        references.append(dict(referenceId='KDCA-281-' + str(rows[0]['excelRow']),
            name=dish, institution=institution, volumeMl=rows[0]['volumeMl'],
            basis='국민건강영양조사 음식별 조사 평균 참고 구성 · 조리용 레시피 아님',
            originalRecipeAmountOverridden=False, sourceUrl=source['sourcePage'],
            sourceRecord=281, fileSequence=2, sourceSha256=source['sha256'],
            checkedAt=DATE, complete=all(r['nutrient'] is not None for r in inputs), inputs=inputs))
    save('kdca-reference-compositions.json', list(grouped.values()))
    save('survey-average-reference-audit.json', references)
    # Exact same dish name provides a separate composition, never an original mass.
    amount_audit = []
    for original in amounts:
        alternatives = [r for r in references if norm(r['name']) == norm(original['recipeName'])]
        amount_audit.append(dict(recipeId=original['recipeId'], ingredientIndex=original['ingredientIndex'],
            recipeName=original['recipeName'], ingredient=original['ingredient'],
            originalSpan=original['originalSpan'], originalAmountEvidenceStatus='NO_SAFE_AMOUNT',
            originalAmountOverridden=False, alternateSurveyReferenceIds=[r['referenceId'] for r in alternatives],
            alternateIsOriginalRecipe=False, checkedAt=DATE,
            checkedSource=dict(url=source['sourcePage'], record=281, fileSequence=2, sha256=source['sha256']),
            result='SAME_DISH_SURVEY_AVERAGE_ONLY' if alternatives else 'NO_EXACT_DISH_IN_SURVEY_DATASET',
            nextSource='동일 원본의 공식 책자·식단표·다운로드 원문에서 단위 또는 중량 확인'))
    save('amount-survey-reference-attempts.json', amount_audit)
    summary = dict(officialCompositionCount=len(grouped), officialIngredientRows=sum(map(len, grouped.values())),
        matchedReferenceCount=len(references), matchedDishCount=len({r['name'] for r in references}),
        completeReferenceCount=sum(r['complete'] for r in references),
        amountRowsWithSeparateSurveyReference=sum(bool(r['alternateSurveyReferenceIds']) for r in amount_audit),
        originalAmountResolved=0, originalRecipeCompletionAdded=0, appAssetChanged=False,
        warning='조사 평균 자료는 원본 중량·조리 레시피를 확정하지 않음', sourceSha256=source['sha256'])
    save('survey-reference-progress.json', summary)
    print(json.dumps(summary, ensure_ascii=False))
    return references


if __name__ == '__main__':
    build()
