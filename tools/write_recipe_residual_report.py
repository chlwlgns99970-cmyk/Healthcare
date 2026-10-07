"""Report actual links, separate identity improvements, and unmet acceptance items."""
import collections,csv,hashlib,json,re
from pathlib import Path
from relink_recipe_residuals import ROOT,OUT,EXCLUDED,load,save
QA=ROOT/'app/build/recipe-linkage-residual'

def write():
    audit=load(OUT/'recipe-final-audit.json');before=load(OUT/'baseline-recipe-final-audit.json')
    decisions=load(OUT/'ingredient-decisions.json');attempts=load(OUT/'residual-attempts.json')
    failures=[d for d in decisions if d['status'] not in ('LINKED',EXCLUDED)]
    causes=dict(identity=sum(d['status']=='AMBIGUOUS_IDENTITY' for d in failures),
        unit=sum(d['status']=='UNIT_CONVERSION_UNVERIFIED' and d['quantity'] is not None for d in failures),
        amount=sum(d['status']=='UNIT_CONVERSION_UNVERIFIED' and d['quantity'] is None for d in failures),
        nutrition=sum(d['status']=='NO_OFFICIAL_NUTRITION_MATCH' for d in failures),
        recipeSource=sum(d['status']=='RECIPE_SOURCE_INCOMPLETE' for d in failures))
    assert sum(causes.values())==len(failures)
    new=load(OUT/'newly-linked-ingredients.json');complete=load(OUT/'newly-complete-recipes.json')
    sources=collections.Counter('USDA' if d['nutrientId'].isdigit() else 'RDA' for d in new)
    kimbap=[r for r in audit['audit'] if '김밥' in r['name']]
    save('kimbap-priority-audit.json',[dict(recipeId=r['recipeId'],name=r['name'],sourceUrl=r['sourceUrl'],complete=r['complete'],
        gaps=[dict(ingredient=d['ingredient'],originalSpan=d['originalSpan'],identityReason=d['identityReason'],amountReason=d['unitReason'],
            checkedSources=['same original full HTML and cooking instructions','RDA 10.4 cached index','K-FIND cached exact-name evidence','USDA SR Legacy','prior official alternative kimbap references'],
            nutritionCandidates=d['candidates'],unitCandidates=d['unitCandidates']) for d in r['ingredients'] if d['status'] not in ('LINKED',EXCLUDED)]) for r in kimbap])
    save('priority-source-investigation.json',dict(
        ink=dict(recipeId='MFDS-295',ingredient='주꾸미먹물',amountGrams=10,
            result='UNRESOLVED_NO_SAME_SPECIES_INK_ENERGY_REFERENCE',
            searches=[dict(source='RDA 10.4',result='No ink energy row; cephalopod flesh is a different ingredient'),
                dict(source='K-FIND',result='Cached processed candidates and official foods workbook: finished ink-flavoured foods do not identify raw webfoot-octopus ink'),
                dict(source='USDA SR Legacy',result='No separate webfoot-octopus ink row among 7793 foods'),
                dict(source='NIFS 2023',result='Scanned PDF; contents visually checked on PDF pages 4 and 5. No separate ink listing; empty text extraction is not evidence of absence',url='https://www.nifs.go.kr/portal/pw/rschB/actionRsltPblcViewPopup.do?rsltpubSeq=21613&histSeq=7'),
                dict(source='Kalymnos Sea Food manufacturer',result='Octopus vulgaris product is a different species; page discusses whole-octopus nutrition and does not establish webfoot-octopus raw ink kcal',url='https://www.ksf.com.gr/our-products/octopus-ink-paste')]),
        jam=dict(recipeId='MFDS-223',name='딸기잼',result='UNRESOLVED_ORIGINAL_RECIPE_SOURCE',
            original='Refreshed same public recipe ID again contains only finished jam 81g and empty instructions; no embedded ingredient JSON or linked download',
            cookbook='Official 2013 cookbook 129 PDF pages indexed locally; no strawberry-jam recipe or original ID 223 linkage',
            publicIndex='Previously guessed public list URL returned 404; no alternate endpoint established',
            API='Sample quota INFO-310 retained from prior request. No new sample calls or quota bypass',
            replacements='Other jam formulations are not merged into this original')))
    preservation=load(QA/'preservation-verify.json');assert preservation['status']=='PASS'
    assert 'OK (5 tests)' in (QA/'samsung.log').read_text(encoding='utf-8')
    assert 'OK (1 test)' in (QA/'samsung-final.log').read_text(encoding='utf-8')
    assert 'BUILD SUCCESSFUL' in (QA/'build-unit-final.log').read_text(encoding='utf-8')
    assert 'Ran 10 tests' in (QA/'data-tests.log').read_text(encoding='utf-8')
    code=['tools/relink_recipe_residuals.py','tools/publish_adjudicated_recipe_references.py','tools/test_recipe_residuals.py',
          'tools/qa_recipe_residual_preservation.py','tools/write_recipe_residual_report.py',
          'app/src/main/assets/fooddata/recipe_ingredient_estimates.csv',
          'app/src/androidTest/java/com/example/healthcare/CatalogExpansionSamsungTest.kt']
    summary=dict(status='COMPLETE' if audit['recipeStates'].get('COMPLETE_LINKED')==516 else 'PARTIAL',
        beforeRecipes=before['recipeStates'],afterRecipes=audit['recipeStates'],
        beforeIngredients=before['ingredientCounts'],afterIngredients=audit['ingredientCounts'],
        unresolvedCauses=causes,newFinalLinks=len(new),newIdentityMatches=sum(a['identityChanged'] for a in attempts),
        newUnitConversions=sum(a['amountChanged'] for a in attempts),newOriginalAmountOverrides=0,newMissingNutritionResolved=0,
        newlyCompleteRecipes=len(complete),remainingRecipes=516-audit['recipeStates'].get('COMPLETE_LINKED',0),
        oldLinkedDecisionsPreserved=2072,oldExcludedDecisionsPreserved=154,residualAttemptCount=len(attempts),newLinksByNutritionSource=dict(sources),
        kimbap=dict(ingredients=37,linked=sum(d['status']=='LINKED' for r in kimbap for d in r['ingredients']),excluded=1,
                    unlinked=sum(d['status'] not in ('LINKED',EXCLUDED) for r in kimbap for d in r['ingredients']),complete=False),
        publishedRecipes=audit['publishedRecipeCount'],publishedCompleteRecipes=audit['publishedCompleteCount'],
        tests=dict(data=10,unit=6,samsungUniqueMethods=6,status='PASS',newlyCompleteFoodsTested=1,
            newCompleteThreeFoodsRequirement='NOT_MET_ONLY_ONE_NEW_COMPLETE_RECIPE',newPartialFoodScreens=3,
            existingCompleteFoodScreens=3,kimbap=True,chapagetti=True,officialStoredCalories=[123,610]),
        dbVersion=8,migrations=0,privateFilesPreserved=16,changedTables=preservation['changedTables'],userDataPreserved=True,
        production=dict(versionCode=7,versionName='1.0.6',changes=0),questionRequired=False,
        assetSha256=audit['assetSha256'],modifiedFiles=code)
    save('final-summary.json',summary)
    selected=['RDA-89514','MFDS-148','RDA-DIET-89332-3','MFDS-247','MFDS-173']
    examples=[]
    for rid in selected:
        r=next(r for r in audit['audit'] if r['recipeId']==rid);assert r['complete']
        entries=[dict(name=d['ingredient'],grams=d['amountGrams'],kcalPer100g=d['nutritionProvenance']['energyKcal'],
            estimatedKcal=d['amountGrams']*d['nutritionProvenance']['energyKcal']/100,nutritionSourceUrl=d['nutritionProvenance']['sourceUrl'])
            for d in r['ingredients'] if d['status']=='LINKED']
        examples.append(dict(recipeId=rid,name=r['name'],recipeUrl=r['sourceUrl'],basis='Original whole recipe input quantities; not one serving or selected product formulation',ingredients=entries))
    save('complete-examples.json',examples)
    lines=[
        '1. STATUS = PARTIAL. 516개 모두 완전 연결 목표는 미달성.',
        '2. Recipe: Before total 516 / complete 29 / partial 419 / unlinkable 68 → After complete 30 / partial 419 / unlinkable 67.',
        '3. Ingredient: Before total 4,258 / linked 2,072 / excluded 154 / unlinked 2,032 → After linked 2,143 / excluded 154 / unlinked 1,961. 기존 연결 2,072행과 제외 154행은 전체 decision dict가 동일하다.',
        '4. 미연결 단일 원인: identity 1,126→1,058 / unit 341→338 / amount 563→563 / nutrition 1→1 / recipe source 1→1.',
        '5. 신규 identity 확정 94행(사용량 없는 행 포함), 단위 환산 3행, 원문 amount 보강 0행, 미확인 nutrition 해결 0행, 완전 연결 recipe +1. 최종 신규 연결 71행. 원인 분류상 identity 실패 감소는 68행이며 별도 identity 개선 94행과 분모가 다르다.',
        '6. 김밥: Before/After 모두 2개 원본 recipe / 37행 / linked 18 / excluded 1 / unlinked 18 / 완전 연결 아님. kimbap-priority-audit.json에 18행의 원문·후보·실패 이유가 있다. 쌀 곡종·쇠고기 부위·치즈 종류·식용유 원료·간장 제법, 큰술 중량, 파 종류, 젓갈 종류 및 약간의 양이 미확정이다.',
        '7. 새로 적용한 공식 근거: 국립국어원 통깨 정의 + RDA 참깨 설명 → 통깨 48행; USDA 일반 볶은 참깨/생땅콩/생팥/녹두 및 계피·다진 양파 공식 portion; RDA 동일 원문의 말린 목이버섯/다시마·녹두묵. 최종 연결의 nutrition 출처는 USDA 67행/RDA 4행. 통깨 정의의 48행은 이 71행에 포함된다. 액상 요구르트 100ml 행은 recipe g와 같다고 취급하지 않고 거부했다. 샘플 API 호출 0건. 새 공식 조리책과 NIFS 성분표도 다운로드 캐시했지만 직접 연결 증가 근거로는 사용하지 않았다.',
        '8. 완전 연결 예시 5개: 호박죽(이번 신규), 현미밥, 곤드레나물밥, 알감자구이, 어묵국. 상세 g·예상 kcal·원문·nutrition URL은 complete-examples.json. 호박죽은 같은 이름의 다른 공식 배합을 합치지 않고 완전 연결된 원문 RDA-89514 하나만 공식 참고 구성으로 연결했다. 원문 전체 재료량이며 선택 제품의 확정 배합이나 1인분으로 표시하지 않는다. 앱에 발행한 완전 참고 recipe는 6→7개, 전체 발행 recipe는 77→78개.',
        '9. 남은 486개 실제 recipe 전체 목록은 remaining-recipes.json. 음식명, 미연결 ingredient 원문, 확인한 source, nutrition/unit 후보와 실패 이유 포함. residual-attempts.json에는 처음 남았던 2,032행의 개별 처리 결과가 있다. 확보한 공식 자료의 범위에서 처리했으며 모든 존재 가능한 외부 자료를 다 찾았다고 주장하지 않는다.',
        '10. 수정 파일: '+', '.join(code)+'. 새 조사/감사 artifact는 data-source/recipe-linkage-residual; 실제 검증 로그·화면·비공개 QA 보존 자료는 app/build/recipe-linkage-residual. 관련 없는 앱 UI/추천/식품 nutrition/DB 파일은 이번 요청에서 변경하지 않았다.',
        '11. targeted Data 10/10 PASS; 관련 Unit 두 클래스 6/6 PASS; QA build PASS. 기존 해결 기능 전체 suite/Lint/Release 빌드 미실행. 초기 실행의 잘못된 QaDebug task 이름은 실제 qa build type으로 바로잡고 통과했다.',
        '12. Samsung SM-S948N/API36: 6개 고유 targeted method PASS. 새 완전 연결 호박죽 1개, 새 재료가 추가된 부분 음식 콩나물무침/풋고추찜/팥밥 3개, 기존 완전 알감자구이/현미밥/곤드레나물밥 3개, 김밥 상세, 짜파게티 610 kcal와 기존 123 kcal 기록 보존 확인. 신규 완전 연결 3개 조건은 이번 증가가 1개라 미충족. 기존 QA에 install -r만 사용했고 최종 호박죽 변경 후 영향을 받는 1개 화면만 추가 확인했다.',
        '13. DB 8 유지 / migration 0 / 사용자 데이터 보존 PASS. 초기 QA snapshot을 복원 후 16 private file bytes 동일, changedTables=[], 제품 identity 불변, QA firstInstallTime 보존. 최신 QA APK는 유지했다.',
        '14. 남은 문제: 419부분/67연결불가, 근거 없는 재료 종류·양·중량 환산. 주꾸미 먹물은 같은 종/원재료 상태의 공식 kcal를 못 확보했고 다른 두족류 살이나 가공 먹물 값으로 대체하지 않았다. 딸기잼 MFDS-223은 여전히 완성품 81g과 빈 조리법뿐이며 다른 recipe 배합을 원본에 붙이지 않았다. priority-source-investigation.json에 별도 조사 결과가 있다.',
        '15. QUESTION_REQUIRED = 없음. 근거 없는 값이나 임의 정책 결정에 대한 승인을 요청하지 않는다.',
        '16. Production versionCode=7 / versionName=1.0.6 / production 변경=0. 제품 설치·삭제·clear·overwrite·배포, GitHub Release, Supabase/Vercel 변경 없음.'
    ]
    (OUT/'completion-report.md').write_text('\n\n'.join(lines)+'\n',encoding='utf-8')
    save('modified-files.json',dict(codeAndAsset=code,artifacts=[p.relative_to(ROOT).as_posix() for p in OUT.rglob('*') if p.is_file()]))
    print(json.dumps({k:v for k,v in summary.items() if k not in ('modifiedFiles','beforeIngredients','afterIngredients')},ensure_ascii=False))

if __name__=='__main__':write()
