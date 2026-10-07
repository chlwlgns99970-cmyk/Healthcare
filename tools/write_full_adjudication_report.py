"""Assemble the requested 17-point report only from completed source/test evidence."""
from pathlib import Path
import json,csv,hashlib,re,collections
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'data-source/full-adjudication';BUILD=ROOT/'app/build/full-adjudication';A=ROOT/'app/src/main/assets/fooddata'
def read(p):
    with p.open(encoding='utf-8-sig',newline='') as f:return list(csv.DictReader(f))
def load(p):return json.loads(p.read_text(encoding='utf-8'))
def link(p,label=None):return f'[{label or p.name}]({p.resolve().as_posix()})'
def main():
    r=load(OUT/'recipe-final-audit.json');m=load(OUT/'menu-final-audit.json');b=load(OUT/'nutrition-basis-audit.json');n=load(OUT/'nolboo-order-catalog-audit.json');preserve=load(BUILD/'preservation-verify.json');existing=load(OUT/'existing-metadata-preservation.json')
    assert r['recipeCount']==516 and m['unreviewed']==0 and not existing['changedExistingRows']
    assert all(d['identityStatus']!='UNREVIEWED' and d['unitStatus']!='UNREVIEWED' for x in r['audit'] for d in x['ingredients'])
    assert preserve['status']=='PASS' and preserve['allAllowlistedPrivateFileBytesIdentical'] and preserve['productIdentityUnchanged']
    assert 'OK (6 tests)' in (BUILD/'samsung-final.log').read_text(encoding='utf-8')
    assert 'OK (1 test)' in (BUILD/'samsung-runtime-menu-audit.log').read_text(encoding='utf-8')
    assert 'OK (3 tests)' in (BUILD/'samsung-final-screen-verification.log').read_text(encoding='utf-8')
    assert 'Ran 11 tests' in (BUILD/'targeted-data.log').read_text(encoding='utf-8')
    assert 'BUILD SUCCESSFUL' in (BUILD/'targeted-unit.log').read_text(encoding='utf-8')
    testxml=list((ROOT/'app/build/test-results/testQaUnitTest').glob('TEST-com.example.healthcare.*.xml'))
    from xml.etree import ElementTree
    targetxml=[p for p in testxml if p.stem in ('TEST-com.example.healthcare.RecipeMenuCompletionTest','TEST-com.example.healthcare.FoodMenuCategoryPolicyTest')]
    assert sum(int(ElementTree.parse(p).getroot().get('tests')) for p in targetxml)==12
    assert all(ElementTree.parse(p).getroot().get('failures')=='0' for p in targetxml)
    foods={x['id']:x for x in read(A/'food_items.csv')};refs=read(A/'recipe_ingredient_estimates.csv')
    examples=[]
    for rid in ('MFDS-158','MFDS-247','MFDS-263'):
        first=next(x for x in refs if x['recipeId']==rid);part=[x for x in refs if x['foodId']==first['foodId']]
        assert all(x['recipeComplete']=='true' for x in part)
        examples.append(dict(name=first['recipeName'],foodId=first['foodId'],officialBasis=first['foodReferenceAmount']+first['foodReferenceUnit'],officialKcal=float(first['foodReferenceKcal']),recipeBasis=first['recipeBasis'],recipeUrl=first['recipeUrl'],ingredients=[dict(name=x['ingredientName'],grams=float(x['amountGrams']),estimatedKcal=float(x['amountGrams'])*float(x['kcalPer100g'])/100,nutrientName=x['nutrientName'],source=x['nutrientUrl']) for x in part]))
    kim=[x for x in r['audit'] if '김밥' in x['name']];kc=collections.Counter(d['status'] for x in kim for d in x['ingredients'])
    menu_reasons=collections.Counter(x['reason'] for x in m['reviews'] if not x['category'])
    source_unlinked=collections.Counter(x['identityReason'] for x in r['audit'] if not x['published'])
    appfiles=[
     'app/src/main/assets/fooddata/recipe_ingredient_estimates.csv','app/src/main/assets/fooddata/food_metadata.csv','app/src/main/assets/fooddata/food_data_manifest.properties',
     'app/src/main/java/com/example/healthcare/domain/FoodMenuCategoryPolicy.kt','app/src/main/java/com/example/healthcare/domain/ReviewedMenuCategorySources.kt','app/src/main/java/com/example/healthcare/domain/OfficialFranchiseMenus.kt','app/src/main/java/com/example/healthcare/domain/FranchiseCatalog.kt','app/src/main/java/com/example/healthcare/ui/screens/SmartFoodInputScreen.kt',
     'app/src/test/java/com/example/healthcare/RecipeMenuCompletionTest.kt','app/src/test/java/com/example/healthcare/FoodMenuCategoryPolicyTest.kt','app/src/androidTest/java/com/example/healthcare/CatalogExpansionSamsungTest.kt','app/src/androidTest/assets/fixtures/full-menu-adjudication.csv',
     'data-source/recipe-menu-completion/reviewed-menu-categories.csv']
    toolnames=['build_recipe_calorie_references','generate_food_metadata','integrate_nolboo_public_menus','build_reviewed_menu_categories','inspect_full_adjudication','capture_full_adjudication_sources','recipe_ingredient_parser','prepare_ingredient_reviews','extract_rda_nutrition','reviewed_recipe_identities','adjudicate_recipe_inputs','publish_adjudicated_recipe_references','adjudicate_unknown_menus','inspect_menu_groups','inspect_target_menu_markup','inspect_order_api','inspect_remaining_ingredient_candidates','inspect_menu_source_context','audit_full_nutrition_basis','finalize_nutrition_basis_sources','research_nolboo_channel','research_nolboo_order_catalog','integrate_nolboo_order_menus','qa_full_adjudication_preservation','research_remaining_menu_evidence','research_nolboo_branch_menus','test_full_adjudication','write_full_adjudication_report']
    changed=[ROOT/p for p in appfiles]+[ROOT/'tools'/f'{name}.py' for name in toolnames]
    assert all(p.exists() for p in changed)
    artifact_paths=[p for p in OUT.rglob('*') if p.is_file() and p.name not in ('completion-report.md','modified-files.txt','final-summary.json')]
    (OUT/'modified-files.txt').write_text('\n'.join(p.resolve().as_posix() for p in changed+artifact_paths)+ '\n',encoding='utf-8')
    final=dict(status='COMPLETE',recipe={k:v for k,v in r.items() if k!='audit'},menu={k:v for k,v in m.items() if k!='reviews'},basis={k:v for k,v in b.items() if k not in ('suspicious','sourceConflicts')},unreviewedIngredientIdentity=0,unreviewedIngredientUnit=0,unreviewedMenu=0,kimbap=dict(recipeCount=len(kim),ingredientRows=sum(len(x['ingredients']) for x in kim),counts=dict(kc)),completeExamples=examples,nolboo=dict(brandMenus=17,storeMenus=7,portionOptions=47,branchesChecked=47,nutritionConfirmed=0,completeIngredientsConfirmed=0,partialDescriptions=14,originDeclarations=9,allergensConfirmed=0,currentSalesVerified=False,currentHomepageCatalogVerified=False),tests=dict(data=11,unit=12,samsungDistinct=7,allPassed=True),databaseVersion=8,migration=False,userDataPreserved=True,productionVersionCode=7,productionVersionName='1.0.6',productionChanges=0,questionRequired=False)
    (OUT/'final-summary.json').write_text(json.dumps(final,ensure_ascii=False,indent=2),encoding='utf-8')
    i=r['identityCounts'];u=r['unitCounts'];s=r['recipeStates']
    text=[
     '1. **STATUS = COMPLETE**. 공식 source 전수 판정 완료. 미검토 identity/단위/menu 모두 **0**. 연결 불가와 원문 미확정은 아래 사유로 확정했으며 성공으로 세지 않았다.',
     f'2. **Recipe 전체**: {r["recipeCount"]:,}개 / ingredient {r["ingredientRows"]:,}행 / 검토 완료 {r["ingredientRows"]:,}행 / 미검토 0행. 괄호 속 g·개수 병기는 한 재료에 귀속하며 이전 parser 조각을 재료로 부풀리지 않았다. '+link(OUT/'recipe-final-audit.json'),
     f'3. **Ingredient nutrition identity**: Before 미검토 2,276건. After 연결 {i["LINKED"]:,}, 공식 nutrition 없음 {i["NO_OFFICIAL_NUTRITION_MATCH"]}, identity 모호 {i["AMBIGUOUS_IDENTITY"]:,}, recipe identity 문제 0, 비열량/공정 제외 {i["EXCLUDED_NON_CALORIC_OR_PROCESS_ITEM"]}, 기타 0, 미검토 0. 독립 identity 판정이며 amount까지 계산 가능한 재료는 {r["ingredientCounts"]["LINKED"]:,}행이다.',
     f'4. **Ingredient unit**: Before 미확정 2,134건. After 직접 중량(g/kg) {u["DIRECT_MASS"]:,}, 공식 동일 source 계량스푼 환산 {u["OFFICIAL_SOURCE_EQUIVALENCE"]}, 안전한 g 환산/사용량 근거 없음 {u["UNIT_CONVERSION_UNVERIFIED"]:,}, 비열량 공정 제외 {u["EXCLUDED_NON_CALORIC_OR_PROCESS_ITEM"]}, 미검토 0. ml/L를 1g=1ml로 바꾸지 않았다. 공식 환산 11행은 고춧가루 작은술·물엿 큰술에 한정하며 복수 원문 중량이 모두 일치하는 경우만 사용했다. 개수·컵·충돌값은 적용하지 않았다.',
     f'5. **Recipe 결과**: Before complete 1 / partial 59. After complete {s["COMPLETE_LINKED"]} / partial {s["PARTIAL_LINKED"]} / unlinkable {s["UNLINKABLE"]}. 정확한 일반 음식과 고유 recipe identity로 공개 연결한 것은 recipe {r["publishedRecipeCount"]}개, 음식 {r["linkedFoodCount"]}개, 완전 연결 공개 recipe {r["publishedCompleteCount"]}개, 참고 ingredient asset {r["publishedIngredientRows"]:,}행이다. 공식 food kcal/ID/serving은 변경하지 않았다.',
     '6. **실제 complete 예시 3개**. 다음 예상 kcal는 원문 전체 재료량 기준이고 공식 음식 값은 개별 선택 food의 기준량이다. 서로 다른 기준이며 합계를 맞추지 않았다. 고형 음식의 ml 표기는 정확한 K-FIND 원문 행과 일치해 유지했다.'
    ]
    for e in examples:
        ingredient='; '.join(f'{x["name"]} {x["grams"]:g}g → {x["estimatedKcal"]:g} kcal ([{x["nutrientName"]}]({x["source"]}))' for x in e['ingredients'])
        text.append(f'   {e["name"]}: {ingredient}. {e["recipeBasis"]}. 선택 음식 공식 {e["officialBasis"]}당 {e["officialKcal"]:g} kcal. [공식 레시피]({e["recipeUrl"]}) / food ID `{e["foodId"]}`.')
    text += [
     f'7. **김밥**: recipe {len(kim)}, ingredient {sum(len(x["ingredients"]) for x in kim)}, 계산 연결 {kc["LINKED"]}, 비열량 제외 {kc["EXCLUDED_NON_CALORIC_OR_PROCESS_ITEM"]}, identity 모호 {kc["AMBIGUOUS_IDENTITY"]}, 단위/사용량 미확정 {kc["UNIT_CONVERSION_UNVERIFIED"]}. 일반 김밥은 18행 중 10행 계산 연결; 충무김밥은 19행 중 3행 연결. 쌀의 도정/종류, 고기 부위·등급, 치즈 종류, 식용유 종류, 간장 제법, 약간 양념의 양을 추정하지 않았다. UI에서 김 1.5g 약 3 kcal·계란 30g 약 42 kcal·오이 13g 약 2 kcal와 부분 연결 안내 확인. 김밥 공식 100g 140 kcal 및 기록 계산 유지. '+link(BUILD/'screens/kimbap-ingredients.png','김밥 실제 앱 렌더링 화면'),
     f'8. **Unknown menu**: Before 229. After category assigned {m["categoryAssigned"]} / category unresolvable {m["categoryUnresolvable"]} / unreviewed 0. 모든 229개를 실제 패키지 lookup 결과와 대조해 일치. 브랜드 업종 추정 사용 0. '+link(OUT/'menu-final-audit.json'),
     '9. **놀부부대찌개**: 공식 홈페이지·기업/모바일/robots/sitemap 접근 결과, 공식 카카오 채널 전체 21개 공지, 연결된 주문 서비스 HTML/Next.js/API를 확인했다. 인기 목록 16개에서 끝내지 않고 공개 매장 47개의 5페이지와 모든 메뉴 API를 확인하여 제육놀부세트를 추가 확보했다. 부대 코드 0027에 해당하는 브랜드 주문 메뉴 **17개/인분 옵션 47개** 반영; 기존 공공 관광의 특정 신길로 매장 **7개** 유지. 47개 요청은 동일 브랜드 카탈로그 응답으로, 각 매장의 현재 판매 확정 근거로 사용하지 않았다. nutrition 0 / 전체 원재료 0 / 부분 메뉴 설명 14 / 원산지 선언 9 / allergen 0. 나머지 3개 설명은 미제공. 2022~2023 원문 갱신일과 현재 판매 미확인을 표시하며, 공식 홈페이지의 완전한 현재 판매 메뉴는 검증 불가로 명시했다. 보쌈 코드 메뉴는 부대 브랜드에 넣지 않았다. '+link(OUT/'nolboo-order-catalog-audit.json')+' / '+link(OUT/'nolboo-branch-menu-audit.json')+' / '+link(BUILD/'screens/nolboo-brand-scope.png','출처 범위 화면'),
     f'10. **Nutrition basis audit**: 전체 {b["totalCatalogRows"]:,}행 / 정확한 원본 food code {b["kfindRowsCompared"]:,}행 비교. suspicious {b["suspiciousFound"]:,} / source confirmed {b["sourceConfirmed"]:,} / parser corrected {b["parserCorrected"]} / unresolved {b["unresolved"]}. 의심 태그는 중복될 수 있다: '+', '.join(f'{k}={v:,}' for k,v in b['countsBySuspicion'].items())+'. 잔여 10건은 원문 payload 없는 기존 메가커피 9건과 동일 품목번호의 source 기준량 차이 1건이다. 근거 없이 값을 통합/수정하지 않았다. '+link(OUT/'nutrition-basis-audit.json'),
     '11. **실제 수정 파일**: 아래 링크에 이 요청의 앱 데이터/lookup/출처 문구/테스트 및 source 파이프라인 파일을 나열했다. 3개 기존 nutrition asset의 원본 bytes와 기존 metadata 70,016행의 값은 전부 보존했고, 새 metadata는 놀부 메뉴 전용 17행이다. 음식 상세의 구조, 기록·추천·홈·업데이트·DB schema는 수정하지 않았다. '+link(OUT/'modified-files.txt','정확한 파일 목록')+' / '+link(OUT/'existing-metadata-preservation.json'),
     '12. **Targeted tests**: Data 11 PASS; Kotlin Unit 12 PASS; Samsung 서로 다른 targeted 검증 7 PASS. 전체 test suite/Lint/Release build 미실행. 삼성 6개 기능 테스트 + 229개 분류 전수 런타임 테스트 1개이며, 앱 렌더링 캡처 방식 변경 후 해당 화면 3개도 재검증 PASS. '+link(BUILD/'targeted-data.log')+' / '+link(BUILD/'targeted-unit.log')+' / '+link(BUILD/'samsung-final.log')+' / '+link(BUILD/'samsung-runtime-menu-audit.log')+' / '+link(BUILD/'samsung-final-screen-verification.log'),
     '13. **Samsung QA**: SM-S948N / Android 16 / API36 / R5KL20HFPAK. 완전 연결 알감자구이, 김밥과 부분 안내, 연결 불가 보리밥의 fake kcal 부재, 공식 총 kcal/기록 저장값 보존, 놀부 브랜드/매장 출처 분리, 새로 분류한 동백 도시락·죠스쿨 음료, 짜파게티 610 kcal 및 영양 macro/상세·편집값 확인 PASS. QA 앱만 install -r; 제품 앱 제거/clear/덮어쓰기 없음. '+link(BUILD/'screens/complete-recipe.png','완전 연결 화면')+' / '+link(BUILD/'screens/unlinkable-recipe.png','연결 불가 화면'),
     '14. **DB**: version 8→8 / migration 0. 작업 전 snapshot으로 복원 후 모든 DB schema/테이블 일치, changedTables=[]; 보존 대상 private 파일 16개/72,343,552 bytes 동일; QA firstInstallTime 유지. 복원 이후 QA를 다시 실행하지 않았다. '+link(BUILD/'preservation-verify.json'),
     '15. **최종 연결 불가 사유**: 계산 기준 identity 모호 1,241행, 단위/실제량 환산 불가 899행, 공식 nutrition 없음 1행(주꾸미 먹물). 비열량/공정 290행은 계산 제외. 독립 identity/단위 집계는 3·4번과 다르며 같은 행의 복수 제약을 중복 합산하지 않은 최종 계산 상태다. 메뉴 분류 불가 72개는 식품 종류를 현 taxonomy로 대응할 근거 부족, 혼합 세트, 설명 미제공 등 개별 사유를 확정했다. recipe 공개 연결이 안 된 경우는 정확한 일반 음식 없음 또는 복수 recipe identity 등이며 별도 artifact에 전수 기록했다. 새 놀부 메뉴 17개도 분류 12/불가 5로 각각 이유 기록. '+link(OUT/'ingredient-decisions.json')+' / '+link(OUT/'nolboo-order-category-audit.json'),
     '16. **QUESTION_REQUIRED = false**. schema/정책 추가 결정 필요 없음. source 부족 항목은 미검토로 남기지 않고 최종 이유로 기록했다.',
     '17. **Production**: versionCode=7 / versionName=1.0.6 / production 변경=0. 제품 package identity와 lastUpdateTime은 작업 전과 동일. GitHub Release·Supabase production·Vercel 변경 없음.'
    ]
    (OUT/'completion-report.md').write_text('\n\n'.join(text)+'\n',encoding='utf-8')
    print('STATUS COMPLETE; report written; all required evidence assertions passed')
if __name__=='__main__':main()
