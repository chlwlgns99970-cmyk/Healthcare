"""Write residual-only research evidence; do not mutate validated inputs/assets."""
import collections
import copy
import json
import re
from index_recipe_amount_references import OUT, load, save, DATE
from adjudicate_recipe_inputs import ROOT, norm
from maximize_recipe_evidence import additional_nutrients


def build():
    original = load(OUT/'baseline-ingredient-decisions.json')
    residual = [d for d in original if d['status'] not in ('LINKED','EXCLUDED_NON_CALORIC_OR_PROCESS_ITEM')]
    assert len(residual)==1747
    amounts = {(d['recipeId'],d['ingredientIndex']):d for d in load(OUT/'amount-survey-reference-attempts.json')}
    portions = {(d['recipeId'],d['ingredientIndex']):d for d in load(OUT/'official-book-portion-candidates.json')}
    books = [r for r in load(OUT/'source-captures.json') if r.get('contentType','').startswith('application/pdf') and 'foodsafetykorea' in r['url']]
    references = load(OUT/'survey-average-reference-audit.json')
    # Exact g spans from NEW official cooking recipes; these are independent
    # references, not a replacement for either original kimbap recipe.
    book_url='https://www.foodsafetykorea.go.kr/upload/20170417/20170417053825_1492418305266.pdf'
    book_sha='c85d9e08c15254e19e007eb30d87628300891a7ebbc8f1e9feed70d6c024f0f0'
    nutrients={n['code']:n for n in load(ROOT/'data-source/full-adjudication/rda-10.4-nutrients.json')}
    nutrients.update(additional_nutrients())
    reviewed={
        '파인애플':('H0920000000a','CONTEXT_VERIFIED','같은 조리법에서 썰어 그대로 김밥에 넣는 파인애플'),
        '달걀':('J0030000000a','CONTEXT_VERIFIED','스크램블 에그/지단을 만드는 투입 전 생달걀'),
        '당근':('F041000B090a','CONTEXT_VERIFIED','조리법의 다져 볶기 전 생 당근 뿌리 중량'),
        '깻잎':('F049000B140a','ALIAS_VERIFIED','기존 검증된 깻잎=생 들깻잎 alias; 지단 위에 올리는 투입량'),
        '참기름':('N0200000009a','EXACT_VERIFIED','공식 영양 DB 동일 일반 식품명'),
        '매실청':('H030000000Ha','EXACT_VERIFIED','공식 영양 DB 동일 일반 식품명'),
        '마요네즈':('R0150020009a','EXACT_VERIFIED','저칼로리 제품과 구분한 일반 마요네즈'),
        '설탕':('169655','ALIAS_VERIFIED','기존 검증된 조리용 설탕=granulated sugar alias 재사용')}
    alternate_recipes=[]
    for page,name,ingredients in [
        (20,'새싹참치김밥',[('파프리카',15),('파인애플',15),('달걀',30),('김',2),('밥',150),
            ('새싹채소',10),('참치',12),('저염간장',5),('마요네즈',2.5),('설탕',.5)]),
        (22,'오징어불고기김밥',[('오징어 몸통',130),('파프리카',30),('당근',20),('피망',30),('밥',200),
            ('채 썬 쇠고기(우둔살)',60),('달걀',50),('김',2),('깻잎',20),('새송이버섯',30),
            ('참기름',15),('고추장',3),('매실청',15)])]:
        inputs=[]
        for ingredient,grams in ingredients:
            rule=reviewed.get(ingredient)
            nutrient=nutrients[rule[0]] if rule else None
            inputs.append(dict(ingredient=ingredient,amountGrams=grams,
                amountEvidenceStatus='OFFICIAL_REFERENCE_RECIPE_AMOUNT',nutritionLinked=bool(rule),
                identityStatus=rule[1] if rule else 'AMBIGUOUS',nutrient=nutrient,
                estimatedKcal=grams*nutrient['energyKcal']/100 if nutrient else None,
                reason=rule[2] if rule else '종·제품·곡물 또는 상태의 영양 항목을 확정하지 않음'))
        alternate_recipes.append(dict(recipeId=f'MFDS-BOOK3-P{page}',name=name,pdfPage=page,
            sourceUrl=book_url,sourceSha256=book_sha,checkedAt=DATE,
            separateReference=True,originalAmountOverridden=False,published=False,
            basis='공식 레시피 참고 구성 · 원본 김밥 배합과 별도',
            inputs=inputs,complete=all(i['nutritionLinked'] for i in inputs)))
    save('alternate-official-recipe-amounts.json',alternate_recipes)
    attempts=[]
    for d in residual:
        key=d['recipeId'],d['ingredientIndex']
        same_dish=[r for r in references if norm(r['name'])==norm(d['recipeName'])]
        part=portions.get(key)
        attempts.append(dict(recipeId=d['recipeId'],ingredientIndex=d['ingredientIndex'],
            recipeName=d['recipeName'],ingredient=d['ingredient'],originalSpan=d['originalSpan'],
            beforeStatus=d['status'],afterStatus=d['status'],linked=False,
            previousFailure=d['identityReason']+' / '+d['unitReason'],
            checkedAt=DATE, query=dict(dish=d['recipeName'],ingredient=d['ingredient'],unit=d['unit']),
            checkedSources=[dict(url=r['url'],sha256=r['sha256'],rawFile=r['rawFile'],
                role='공식 별도 요리책; 동일 원본 여부 확정 전 양 대체 금지') for r in books],
            separateSurveyReferenceIds=[r['referenceId'] for r in same_dish],
            amountEvidenceStatus='NO_SAFE_AMOUNT' if key in amounts else None,
            amountSurveyResult=amounts[key]['result'] if key in amounts else None,
            portionResult=part['result'] if part else None,
            gramsPerUnitCandidates=part['gramsPerUnitCandidates'] if part else [],
            result='원본과 동일한 중량·형태·영양 연결을 확정하지 못함. 별도 레시피/조사 평균 또는 충돌 계량값을 원본에 적용하지 않음',
            nextPossibleSource='동일 원본의 기관 발간 식단 원본·레시피 첨부파일 또는 정정 자료'))
    save('residual-attempts.json',attempts)
    kimbap=[]
    for attempt in attempts:
        if attempt['recipeId'] not in ('RDA-DIET-89289-0','RDA-91342'):continue
        portion=portions.get((attempt['recipeId'],attempt['ingredientIndex']))
        kimbap.append(attempt | dict(newOfficialRecipeEvidence=dict(
            sourceUrl='https://www.foodsafetykorea.go.kr/upload/20170417/20170417053825_1492418305266.pdf',
            sourceSha256='c85d9e08c15254e19e007eb30d87628300891a7ebbc8f1e9feed70d6c024f0f0',
            pdfPages=[20,22], recipeNames=['새싹참치김밥','오징어불고기김밥'],
            result='다른 공식 김밥 레시피의 g 표기 확인; 기존 두 원본 구성과 다름'),
            grams=None,nutrientSource=None,success=False,
            portionConflict=portion['gramsPerUnitCandidates'] if portion else []))
    assert len(kimbap)==18
    save('kimbap-new-attempts.json',kimbap)
    audit=copy.deepcopy(load(OUT/'baseline-recipe-final-audit.json'))
    by_recipe=collections.defaultdict(list)
    for a in attempts:by_recipe[a['recipeId']].append(a)
    remaining=[]
    for recipe in audit['audit']:
        if recipe['complete']:continue
        remaining.append(dict(recipeId=recipe['recipeId'],name=recipe['name'],status=recipe['status'],
            unresolvedIngredients=by_recipe[recipe['recipeId']], sourceUrl=recipe['sourceUrl']))
    assert len(remaining)==475
    save('remaining-recipes.json',remaining)
    save('ingredient-decisions.json',original)
    save('recipe-final-audit.json',audit)
    save('newly-linked-ingredients.json',[])
    save('newly-complete-recipes.json',[])
    save('known-source-gaps.json',dict(
        webfootOctopusInk=dict(recipeId='MFDS-295',ingredient='주꾸미먹물',
            checkedSources=['기존 RDA 10.4/MFDS/공공 원재료 영양 인덱스','KDCA 음식별 재료량 613개'],
            result='동일 종 먹물 자체의 기준량별 공식 열량 없음; 몸통·다른 종 먹물 값 사용 금지',resolved=False),
        originalJam=dict(recipeId='MFDS-223',checkedSources=[r['url'] for r in books],
            result='동일 4인분 원본의 딸기·당류별 실제 투입량 확인 못함; 완제품 열량 역산 금지',
            originalAmountOverridden=False,resolved=False)))
    summary=dict(status='PARTIAL',recipeStates=audit['recipeStates'],ingredientCounts=audit['ingredientCounts'],
        originalAmountResolved=0,amountRemaining=563,identityResolved=0,identityRemaining=850,
        unitResolved=0,unitRemaining=332,nutritionRemaining=1,recipeSourceRemaining=1,
        separateSurveyReference=load(OUT/'survey-reference-progress.json'),
        separateOfficialRecipeAmountRows=sum(len(r['inputs']) for r in alternate_recipes),
        separateOfficialRecipeNutritionRows=sum(sum(i['nutritionLinked'] for i in r['inputs']) for r in alternate_recipes),
        portionCandidates=dict(collections.Counter(r['result'] for r in portions.values())),
        kimbap=dict(linked=18,excluded=1,unlinked=18,complete=False),
        newCompleteRecipes=[],appAssetChanged=False,productionChanged=False,checkedAt=DATE)
    save('progress.json',summary)
    print(json.dumps({k:v for k,v in summary.items() if k!='separateSurveyReference'},ensure_ascii=False))


if __name__=='__main__':build()
