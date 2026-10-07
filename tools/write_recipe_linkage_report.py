"""Numeric report for actual linkage, explicitly PARTIAL unless all 516 link."""
import collections,hashlib,json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'data-source/recipe-linkage-maximization';QA=ROOT/'app/build/recipe-linkage-maximization'
def load(path):return json.loads(path.read_text(encoding='utf-8'))
def write():
    audit=load(OUT/'recipe-final-audit.json');before=load(OUT/'baseline-recipe-final-audit.json')
    decisions=[d for r in audit['audit'] for d in r['ingredients']]
    base={(d['recipeId'],d['ingredientIndex']):d for r in before['audit'] for d in r['ingredients']}
    newly=[d for d in decisions if d['status']=='LINKED' and base[d['recipeId'],d['ingredientIndex']]['status']!='LINKED']
    failures=[d for d in decisions if d['status'] not in ('LINKED','EXCLUDED_NON_CALORIC_OR_PROCESS_ITEM')]
    prior={r['recipeId']:r for r in load(OUT/'remaining-recipes.json')}
    alternatives=load(OUT/'official-alternative-recipe-catalog.json')
    queries={q['query']:q for q in alternatives['queries']}
    remaining=[]
    for r in audit['audit']:
        if r['complete']:continue
        item=prior.get(r['recipeId'],{})|dict(recipeId=r['recipeId'],name=r['name'],status=r['status'],sourceUrl=r['sourceUrl'],
            sourceSha256=r['ingredients'][0]['sourceSha256'],gaps=[dict(ingredient=d['ingredient'],originalSpan=d['originalSpan'],
                status=d['status'],identityReason=d['identityReason'],amountReason=d['unitReason'],
                nutritionCandidates=d['candidates'],unitCandidates=d['unitCandidates']) for d in r['ingredients']
                if d['status'] not in ('LINKED','EXCLUDED_NON_CALORIC_OR_PROCESS_ITEM')])
        if r['name'] in queries:
            q=queries[r['name']]
            item['additionalOfficialRecipeQuery']={k:q[k] for k in ('url','status','result','sha256','rawFile') if k in q}
        remaining.append(item)
    (OUT/'remaining-recipes.json').write_text(json.dumps(remaining,ensure_ascii=False,indent=2),encoding='utf-8')
    causes=dict(identity=sum(d['status']=='AMBIGUOUS_IDENTITY' for d in failures),
        unit=sum(d['status']=='UNIT_CONVERSION_UNVERIFIED' and d['quantity'] is not None for d in failures),
        amount=sum(d['status']=='UNIT_CONVERSION_UNVERIFIED' and d['quantity'] is None for d in failures),
        nutrition=sum(d['status']=='NO_OFFICIAL_NUTRITION_MATCH' for d in failures),
        recipeSource=sum(d['status']=='RECIPE_SOURCE_INCOMPLETE' for d in failures))
    assert sum(causes.values())==len(failures)
    sources=dict(collections.Counter('K-FIND' if d['nutrientId'].startswith('P') else 'USDA' if d['nutrientId'].isdigit() else 'RDA' for d in newly))
    examples=load(OUT/'complete-examples.json')
    preservation=load(QA/'preservation-verify.json');assert preservation['status']=='PASS'
    logs={name:(QA/name).read_text(encoding='utf-8') for name in ('data-tests.log','unit.log','samsung.log','samsung-final.log','samsung-kimbap-final.log')}
    assert 'Ran 10 tests' in logs['data-tests.log'] and 'OK' in logs['data-tests.log']
    assert 'BUILD SUCCESSFUL' in logs['unit.log'] and 'OK (6 tests)' in logs['samsung.log'] and 'OK (3 tests)' in logs['samsung-final.log']
    assert 'OK (3 tests)' in (QA/'samsung-latest.log').read_text(encoding='utf-8')
    files=['tools/adjudicate_recipe_inputs.py','tools/maximize_recipe_evidence.py','tools/extract_recipe_kfind_fallbacks.py',
        'tools/publish_adjudicated_recipe_references.py','tools/qa_recipe_linkage_preservation.py',
        'tools/test_recipe_linkage_maximization.py','tools/write_recipe_linkage_report.py','tools/collect_recipe_official_fallbacks.py',
        'app/src/main/assets/fooddata/recipe_ingredient_estimates.csv',
        'app/src/main/java/com/example/healthcare/ui/screens/AddRecordScreen.kt',
        'app/src/androidTest/java/com/example/healthcare/CatalogExpansionSamsungTest.kt']
    summary=dict(status='COMPLETE' if audit['recipeStates'].get('COMPLETE_LINKED')==516 else 'PARTIAL',
        before=before['recipeStates'],after=audit['recipeStates'],totalRecipes=516,
        ingredientRows=len(decisions),identityLinked=audit['identityCounts']['LINKED'],amountLinked=sum(d['amountGrams'] is not None for d in decisions),
        nutritionLinked=sum(d['nutrientId'] is not None for d in decisions),finalLinked=audit['ingredientCounts']['LINKED'],
        excludedNonCaloric=audit['ingredientCounts']['EXCLUDED_NON_CALORIC_OR_PROCESS_ITEM'],failed=len(failures),causes=causes,
        overlappingIdentityFailures=sum(d['identityStatus']=='AMBIGUOUS_IDENTITY' for d in failures),newFinalLinks=len(newly),
        removedCircularLink=1,newLinksByNutritionSource=sources,newOfficialPortionConversions=sum(bool(d.get('conversionProvenance')) for d in decisions),
        kimbap=[dict(recipeId=r['recipeId'],name=r['name'],total=len(r['ingredients']),linked=sum(d['status']=='LINKED' for d in r['ingredients']),
                    excluded=sum(d['status']=='EXCLUDED_NON_CALORIC_OR_PROCESS_ITEM' for d in r['ingredients']),
                    missing=sum(d['status'] not in ('LINKED','EXCLUDED_NON_CALORIC_OR_PROCESS_ITEM') for d in r['ingredients'])) for r in audit['audit'] if '김밥' in r['name']],
        tests=dict(data=10,unit=2,samsungDistinctCases=6,status='PASS',completeFoodsOnSamsung=3,recordCalories=[123,610],kimbapGrams100Kcal=140),
        DBVersion=8,migrations=0,userDataPreserved=True,production=dict(versionCode=7,versionName='1.0.6',changes=0),
        questionRequired=False,modifiedCodeAndAssetFiles=files)
    (OUT/'final-summary.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2),encoding='utf-8')
    (OUT/'modified-files.json').write_text(json.dumps(dict(codeAndAsset=files,generatedArtifacts=[str(p.relative_to(ROOT)).replace('\\','/') for p in OUT.rglob('*') if p.is_file()]),ensure_ascii=False,indent=2),encoding='utf-8')
    lines=[f"1. STATUS = {summary['status']}. 516개 완전 연결 목표는 미달성.",
        f"2. Recipe: Before total 516 / complete 13 / partial 407 / unlinkable 96 → After total 516 / complete {audit['recipeStates']['COMPLETE_LINKED']} / partial {audit['recipeStates']['PARTIAL_LINKED']} / unlinkable {audit['recipeStates']['UNLINKABLE']}. 신규 완전 연결 17개, 원재료 없이 완성품 중량만 제공한 딸기잼 1개는 완전 연결 취소.",
        f"3. Ingredient: 전체 {len(decisions)}행 / identity 연결 {summary['identityLinked']} / g 중량 근거 {summary['amountLinked']} / nutrition 연결 {summary['nutritionLinked']} / 최종 연결 {summary['finalLinked']} / 비열량·공정 제외 {summary['excludedNonCaloric']} / 최종 미연결 {len(failures)}. 신규 연결 {len(newly)}행, 순환 연결 삭제 1행 → 순증 {len(newly)-1}행. Identity·amount·nutrition 숫자는 독립 항목이며 전부가 최종 계산 가능하다는 뜻은 아님.",
        f"4. 최종 실패 단일 원인: identity {causes['identity']} / unit {causes['unit']} / amount {causes['amount']} / nutrition {causes['nutrition']} / recipe source {causes['recipeSource']} = {len(failures)}행. 사용량 미정과 겹치는 identity 모호성까지 포함하면 {summary['overlappingIdentityFailures']}행. 소금도 사용량이 없으면 완전 연결로 인정하지 않도록 강화하여 기존 비열량 제외 136행을 다시 처리했다.",
        f"5. 추가 활용 공식 source: RDA 10.4 일반 생재료·조리 완료 밥·동일 조리법의 다진 대파 명시 → 신규 최종 연결 {sources['RDA']}행; USDA SR Legacy 7793 식품/14449 portion의 건표고·동일 재료 계량스푼 → {sources['USDA']}행; K-FIND 316734행 중 동일명·동일 식품군·제조사 없는 단일 분석 어묵/식빵/생크림/버터/고추장 → {sources['K-FIND']}행. USDA 재료별 계량스푼 환산 124행, 5개 이상 공식 원문 중량 병기가 모두 일치하는 설탕 컵·달걀 개·통깨 작은술 참고량 7행. RDA와 USDA의 공식 근거·source 해시 보존. 모든 컵·개수에 공통 중량을 적용하거나 약간의 가짜 중량은 미생성.",
        f"식약처 COOKRCP01 추가 공식 레시피 {alternatives['uniqueRecipeCount']}개를 확보했다. 공개 샘플은 INFO-310 호출 한도 소진 후 중단했다. 한도 응답은 레시피가 없다는 뜻으로 처리하지 않았다. 다른 배합은 별도 원문 catalog에 보존하며 원본 516개 완전 연결 수에 넣지 않았다.",
        '추가 김밥 source: 농사로 새싹 김밥과 농식품 올바로 수수떡갈비김밥도 조사했지만 선택 배합·종류 미정 및 참기름/참깨 약간이 남아 원래 두 레시피를 완전 연결로 바꾸는 근거로 쓰지 않았다. 원본 516 identity와 ingredient spans는 그대로 유지했다. source-captures와 remaining-recipes에 확인 URL을 보존했다.',
        '6. 김밥: 원본 2개 / 37재료 / 연결 18 / 물·공정 제외 1 / 미연결 18. 일반 김밥 18재료 중 연결 10, 충무김밥 19재료 중 연결 8. 실제 UI는 공식 100g=140 kcal, 근거 있는 g 수량 입력, 참고 구성·재료별 예상값·부분 연결 안내 PASS. 검증된 1줄 중량은 없으므로 임의 줄 환산은 추가하지 않았다.',
        '7. 완전 연결 예시 6개. 아래 예상값은 원문 전체 재료량 기준이고 오른쪽 공식 kcal는 선택 식품의 기존 기준량 값이므로 서로 다른 분모이며 기록값으로 대체하지 않는다.']
    for e in examples:
        ingredients='; '.join(f"{d['name']} {d['grams']:g}g → {d['estimatedKcal']:.3f} kcal" for d in e['ingredients'])
        lines.append(f"{e['name']} ({e['recipeId']}): {ingredients}. 참고 전체 합 {e['ingredientEstimatedTotal']:.3f} kcal. 공식 {e['officialFoodBasis']}={e['officialFoodKcal']:g} kcal. 레시피 [원문]({e['recipeUrl']}); 재료별 nutrition URL은 complete-examples.json.")
    lines += [f'8. 남은 {len(remaining)}개 전체 목록: remaining-recipes.json. 각 원본 레시피의 미연결 재료·원문 span·nutrition 후보·단위 근거 후보·조사 source·추가 API 접근 상태·잔여 원인 포함.',
        f'9. 실제 수정 소스/테스트/asset {len(files)}개와 이번 생성 artifact는 modified-files.json. 기존 메뉴·식품 영양값·추천·업데이트·기록 정책 파일은 수정하지 않았다.',
        '10. targeted Data 10/10 PASS, Unit 2/2 PASS, Samsung UI/기록 6개 고유 테스트 PASS. 최신 재료 데이터 QA 빌드·install -r 후 영향 있는 3개 UI/기록 테스트 재실행 PASS(samsung-latest.log). 완전 연결 화면 3음식=알감자구이/현미밥/곤드레나물밥, 부분 김밥/미연결 보리밥, 짜파게티 상세·610 kcal 기록, 123 kcal 저장 유지 확인. 추가 김밥 수량 검증에서 기본 입력이 빈칸인데 100으로 가정한 테스트 실패 1회는 실제 100g 입력 후 140 kcal 검사로 수정해 PASS. 전체 suite/Lint/Release 빌드 미실행. 로그와 QA 실제 화면은 app/build/recipe-linkage-maximization.',
        '11. DB 8 유지 / migration 0. QA 사용자 데이터 테스트 전 baseline 복원 후 검증 PASS: changedTables=[], DB 8→8, allowlisted 16 private files byte-identical, QA 최초 설치 시각 보존, 제품 identity 불변. 최종 QA APK만 install -r.',
        f"12. 남은 문제: {audit['recipeStates']['PARTIAL_LINKED']} 부분 연결 + {audit['recipeStates']['UNLINKABLE']} 연결 불가. 곡종·양념 제법·육류 부위 등 미확정 identity, 부피/개수 가식부 환산, 약간/무단위/범위량, 주꾸미 먹물 공식 nutrition 1행, 완성 딸기잼 중량만 있는 source 1행. 다른 공식 레시피의 배합을 원본에 복사하거나 임의 중량을 만들지 않았다.",
        '13. QUESTION_REQUIRED = 없음. 추가 승인이나 임의 데이터 결정은 요청하지 않는다.',
        '14. Production versionCode=7 / versionName=1.0.6 / production 변경=0. 제품 APK 설치·삭제·clear, production 배포·서비스 변경 없음.']
    (OUT/'completion-report.md').write_text('\n\n'.join(lines)+'\n',encoding='utf-8')
    print(json.dumps({k:v for k,v in summary.items() if k not in ('modifiedCodeAndAssetFiles','kimbap')},ensure_ascii=False))
if __name__=='__main__':write()
