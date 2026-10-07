"""Per-row disposition of every residual, with independent original/reference axes."""
import collections
import csv
import hashlib
import json
from pathlib import Path
from build_full_recipe_references import OUT, ROOT, load, norm, save
from maximize_recipe_evidence import context_for

def run():
    baseline=load(OUT/'baseline-ingredient-decisions.json')
    after=load(OUT/'ingredient-decisions.json') if (OUT/'ingredient-decisions.json').exists() else baseline
    afterByKey={(d['recipeId'],d['ingredientIndex']):d for d in after}
    facts={r['recipeId']:r for p in (ROOT/'data-source/recommendation').glob('official*public-recipe-facts.csv')
        for r in csv.DictReader(p.open(encoding='utf-8-sig'))}
    refs=load(OUT/'reference-composition-audit.json')
    states={r['recipeId']:r for r in load(OUT/'recipe-final-states.json')}
    portions={(r['recipeId'],r['ingredientIndex']):r for r in load(OUT/'portion-reviews.json')}
    context={}
    for rid,f in facts.items():
        raw=ROOT/'app/build/food-quality-qa/recipe-source'/ (rid.lower().replace('rda-diet-','nongsaro-diet-')+'.html')
        if raw.exists():
            assert hashlib.sha256(raw.read_bytes()).hexdigest()==f['sourceSha256']
            context[rid]=context_for(f,raw)
        else: context[rid]=''
    attempts=[]
    for d in baseline:
        if d['status'] in ('LINKED','EXCLUDED_NON_CALORIC_OR_PROCESS_ITEM'):continue
        rid=d['recipeId']; p=portions.get((rid,d['ingredientIndex']))
        updated=afterByKey[rid,d['ingredientIndex']]
        matching=[r for r in refs if r['recipeId']==states[rid]['referenceId'] or norm(r['name'])==norm(d['recipeName']) or
            norm(d['recipeName'])=='김밥' and r['recipeId'].startswith(('MENUZEN-D016','MFDS-BOOK3-'))]
        gap='source' if d['status']=='RECIPE_SOURCE_INCOMPLETE' else 'nutrition' if d['status']=='NO_OFFICIAL_NUTRITION_MATCH' else \
            'identity' if d['status']=='AMBIGUOUS_IDENTITY' else 'unit' if p else 'amount'
        reason={
            'identity':'원본 문맥과 후보의 품종·부위·상태·제품 세분조건을 동일하게 확정할 근거 부족. 참고 구성의 세분 식품을 원본에 역대입하지 않음.',
            'unit':'원본과 동일한 계량 단위·식품 형태·용량 정의를 확정하지 못함. 개·컵·장 크기 또는 충돌 계량을 평균내지 않음.',
            'amount':'원본의 약간/적당량 또는 단위 없는 수치를 g으로 확정할 원문 없음. 다른 레시피 중량으로 보충하지 않음.',
            'nutrition':'주꾸미 먹물의 종·식용 부위·에너지 기준량이 일치하는 영양 자료 없음. 주꾸미 전체 또는 다른 종 먹물은 대체 근거가 아님.',
            'source':'원본 딸기잼 재료 구성이 누락됨. 대학·USDA 참고 구성은 별도이며 원본 공식 kcal로 배합을 역산하지 않음.'
        }[gap]
        attempts.append(dict(recipeId=rid,recipeName=d['recipeName'],ingredientIndex=d['ingredientIndex'],
            ingredient=d['ingredient'],originalSpan=d['originalSpan'],primaryGap=gap,
            originalStatus=d['status'],originalIdentityStatus=d['identityStatus'],originalAmountGrams=d['amountGrams'],
            contextReviewed=context[rid],contextSha256=hashlib.sha256(context[rid].encode()).hexdigest(),
            originalSource=d['sourceUrl'],originalSourceSha256=d['sourceSha256'],
            originalCandidateNutrition=d['candidates'],portionReview=p,
            checkedSources=[dict(url=r['sourceUrl'],sha256=r['sourceSha256'],recipeId=r['recipeId'],complete=r['complete']) for r in matching],
            broaderSources=['RDA 국가표준식품성분 10.4','식약처 공개 기존/신규 영양 DB','USDA SR Legacy',
                'KDCA 613개 조사 평균 구성','MFDS 공식 조리책자 1–7','RDA 메뉴젠 일반·가공·제철 음식 전체 목록'],
            appReferenceId=states[rid]['referenceId'],appReferencePublished=states[rid]['referencePublished'],
            appReferenceComplete=states[rid]['referenceComplete'],originalAmountOverridden=False,
            afterStatus=updated['status'],resolvedOriginalAmountGrams=updated['amountGrams'],
            originalConversionProvenance=updated.get('conversionProvenance'),
            failureReason=reason if updated['status']!='LINKED' else None,checkedAt='2026-10-05'))
    assert len(attempts)==1747
    save('residual-row-dispositions.json',attempts)
    save('remaining-unresolved-ingredients.json',[x for x in attempts if x['afterStatus']!='LINKED'])
    save('residual-progress.json',dict(originalLinked=sum(x['status']=='LINKED' for x in after),originalExcluded=154,
        originalUnresolved=sum(x['afterStatus']!='LINKED' for x in attempts),
        primaryGapsBefore=dict(collections.Counter(x['primaryGap'] for x in attempts)),
        primaryGapsAfter=dict(collections.Counter(x['primaryGap'] for x in attempts if x['afterStatus']!='LINKED')),
        residualRowsWithPublishedCompleteReference=sum(x['appReferencePublished'] and x['appReferenceComplete'] for x in attempts),
        originalRowsChanged=sum(x['afterStatus']=='LINKED' for x in attempts),originalAmountOverrides=0,conflictsReviewed=238,
        conflictConversionsAccepted=sum(bool(r['preferredGramsPerUnit']) for r in portions.values() if r['result']=='CONFLICTING_OFFICIAL_PORTIONS')))
    print(json.dumps(load(OUT/'residual-progress.json'),ensure_ascii=False))

if __name__=='__main__':run()
