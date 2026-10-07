"""Bounded, per-food decisions and fresh, row-specific portion comparisons."""
import collections
import hashlib
from audit_recipe_residual_97 import ROOT, OUT, FINAL, ASSETS, load, save, rows
from finish_recipe_reference_mapping import REVIEWED_REFERENCES


def run():
    baseline = {r['recipeId']: r for r in load(OUT / 'baseline-states.json')}
    states = {r['recipeId']: r for r in load(FINAL / 'recipe-final-states.json')}
    initial = {r['recipeId']: r for r in load(OUT / 'initial-classification.json')}
    mappings = {r['originalRecipeId']: r for r in load(FINAL / 'food-mapping-decisions.json')}
    references = {r['recipeId']: r for r in load(FINAL / 'validated-reference-compositions.json')}
    new_reviews = load(OUT / 'new-primary-candidate-reviews.json')
    focused = load(OUT / 'focused-unresolved-and-source-review.json')
    focused_foods = {r['recipeId']: r for r in focused['unresolvedFoods']}
    search = collections.defaultdict(list)
    for path in sorted(OUT.glob('identity-search-*.json')):
        receipt = load(path)
        for food in receipt['foods']:
            search[food['recipeId']].append(path.name)
    ledger = []
    for prior in load(OUT / 'scope-97.json'):
        rid = prior['recipeId']
        current = states[rid]
        audit = initial[rid]
        assert search[rid], rid
        complete_ids = audit['completeWholeReferenceIds']
        selected = current.get('referenceId')
        if current['appCompleteAvailable']:
            disposition = current['state']
            reason = mappings[rid]['reason']
            next_step = 'QA asset/실제 카드 전체 재료 표시 및 기록 kcal 보존 검증'
        elif current['originalComplete'] or complete_ids:
            disposition = 'QUESTION_REQUIRED_NEW_FOOD'
            reason = ('전체 정량 구성은 있으나 전체 67,354개 기존 Food에서 같은 주재료·조리형태의 안전한 ID를 확인하지 못했다. '
                      '유사 음식이나 브랜드 완제품으로 연결하지 않으며 신규 Food 생성은 승인 대기한다.')
            next_step = '정확한 새 Food의 공식 nutrition·기준량·ID 정책을 결정하고 생성 승인 후 연결'
        else:
            disposition = ('UNRESOLVED_WITH_EXHAUSTED_EVIDENCE' if current['state'] == 'UNRESOLVED'
                           else 'PARTIAL_WITH_EXHAUSTED_EVIDENCE')
            reason = prior['remainingReason']
            next_step = ('originalUnresolved의 종/상태·정량·생활단위에 직접 대응하는 한 개의 신뢰 원문 배합 확보; '
                         '그 뒤 기존 Food 전체 후보와 identity 재비교. 원본 누락량을 타 recipe에서 보충하지 않음.')
        candidate_reviews = []
        for candidate in prior['rankedReferenceAdjudications']:
            ref = references[candidate['recipeId']]
            reviewed = REVIEWED_REFERENCES.get(prior['name'])
            same = bool(reviewed and reviewed[0] == ref['recipeId']) or ref['recipeId'] in complete_ids
            candidate_reviews.append(candidate | dict(
                validationComplete=ref['complete'],
                decision=('VERIFIED_WHOLE_COMPOSITION_NO_FOOD_ID' if same and not current['targetFoodIds']
                          else 'SELECTED' if selected == ref['recipeId'] and current['appCompleteAvailable'] else 'NOT_PUBLISHED'),
                currentReason=(reviewed[1] if same and reviewed else
                               ref.get('compositionValidationProblem') or
                               '주재료·조리형태·명시 qualifier 또는 정량/nutrition 전체가 원본 음식 identity와 안전하게 맞는지 확인되지 않음. 유사 이름 점수만으로 채택하지 않음.')))
        record = prior | dict(
            baselineClassification=audit['classification'], initialAudit=audit,
            state=current['state'], appComplete=current['appCompleteAvailable'],
            finalDisposition=disposition, verifiedFoodIds=current['targetFoodIds'],
            selectedMapping=mappings.get(rid), verifiedCompleteReferenceIds=complete_ids,
            rankedReferenceAdjudications=candidate_reviews,
            newExecutedSearchReceipts=search[rid],
            newPrimaryCandidateReviews=[r for r in new_reviews if rid in r['recipeIds']],
            focusedReview=focused_foods.get(rid),
            separateNutritionReview=focused['nutrition1'] if rid == focused['nutrition1']['recipeId'] else None,
            separateOriginalSourceReview=focused['source1'] if rid == focused['source1']['recipeId'] else None,
            needsNewFood=disposition == 'QUESTION_REQUIRED_NEW_FOOD',
            remainingReason=None if current['appCompleteAvailable'] else reason,
            nextAction=next_step, checkedAt='2026-10-05', wholeInternetExhausted=False,
            exhaustedMeaning='현재 확보한 원문 cache·전체 메뉴젠 목록·KDCA 그룹·19개 기존 문서 색인과 새로운 학교/공공급식 검색 범위 내 채택 근거 부족. 전 인터넷에 자료가 없다는 단정이 아님. 원문 근거를 추가 확보하면 재검토.')
        ledger.append(record)
    assert len(ledger) == 97 and len({r['recipeId'] for r in ledger}) == 97
    save('all-97-final-evidence-ledger.json', ledger)
    save('new-app-complete-foods.json', [r for r in ledger if r['appComplete'] and not baseline[r['recipeId']]['appCompleteAvailable']])
    save('remaining-foods.json', [r for r in ledger if not r['appComplete']])
    save('question-required-new-food.json', [r for r in ledger if r['needsNewFood']])

    # New image pairs are direct only within their own recipe. Do not choose a
    # cross-recipe size or density by voting, averaging, or global spoon mass.
    pairs = [
        dict(ingredient='다진 마늘', state='생것, 다짐', unit='작은술', grams=5, quantity=1,
             source='recipe_summer9.jpg', institution='대한당뇨병학회', recipeId='KDA-SUMMER9'),
        dict(ingredient='다진 마늘', state='생것, 다짐', unit='작은술', grams=2.5, quantity=.5,
             source='recipe_summer11.jpg', institution='대한당뇨병학회', recipeId='KDA-SUMMER11'),
        dict(ingredient='다진 파', state='생것, 다짐', unit='작은술', grams=2.5, quantity=.5,
             source='recipe_summer11.jpg', institution='대한당뇨병학회', recipeId='KDA-SUMMER11'),
        dict(ingredient='통깨', state='통깨', unit='큰술', grams=2, quantity=.25,
             source='recipe_summer11.jpg', institution='대한당뇨병학회', recipeId='KDA-SUMMER11'),
        dict(ingredient='참기름', state='기름', unit='작은술', grams=2.5, quantity=.5,
             source='recipe_summer11.jpg', institution='대한당뇨병학회', recipeId='KDA-SUMMER11'),
        dict(ingredient='소금', state='종류 미지정', unit='작은술', grams=3, quantity=1,
             source='recipe_summer11.jpg', institution='대한당뇨병학회', recipeId='KDA-SUMMER11')]
    comparisons = []
    for old in load(ROOT / 'data-source/recipe-reference-finalization/current-212-conflict-review.json'):
        applicable = [p | dict(sameRecipe=p['recipeId'] == old['recipeId'],
                       gramsPerUnit=p['grams'] / p['quantity'],
                       applicability='DIFFERENT_RECIPE_WITHOUT_SAME_SIZE_STATE_MEASUREMENT_PROOF')
                      for p in pairs if p['ingredient'].replace(' ', '') == old['ingredient'].replace(' ', '') and p['unit'] == old['unit']]
        comparisons.append(old | dict(newSourceComparisons=applicable,
            freshReviewDate='2026-10-05', newSourcesReviewed=['recipe_summer9.jpg','recipe_summer11.jpg'],
            sameInstitutionDoesNotProveSameMeasure=True, averageUsed=False,
            selectedNewConversion=None, finalStatus='UNRESOLVED',
            newReviewReason='동일 원본 행의 직접 g 환산 또는 같은 생/익힘·다짐·크기·밀도·계량법 지정이 없음. 다른 학회 recipe의 g/생활단위 병기를 전역 규칙으로 쓰지 않음.'))
    assert len(comparisons) == 212
    save('current-212-conflict-review.json', comparisons)
    summary = dict(total=516, scope=97, beforeAppComplete=419,
        afterAppComplete=sum(r['appCompleteAvailable'] for r in states.values()),
        initialClassification=load(OUT / 'initial-summary.json')['categories'],
        finalScopeDispositions=dict(collections.Counter(r['finalDisposition'] for r in ledger)),
        states=dict(collections.Counter(r['state'] for r in states.values())),
        completeWithoutFoodId=sum(r['needsNewFood'] for r in ledger),
        remaining=sum(not r['appComplete'] for r in ledger), conflictsResolved=0, conflictsUnresolved=212,
        originalIngredientProgress=load(FINAL / 'original-ingredient-progress.json'),
        assetSha256=hashlib.sha256((ASSETS / 'official_recipe_reference_estimates.csv').read_bytes()).hexdigest(),
        status='PARTIAL', productionDeployments=0)
    save('summary.json', summary)
    print(summary)


if __name__ == '__main__':
    run()
