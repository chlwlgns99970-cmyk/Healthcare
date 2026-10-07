"""Reassess each original row independently; never borrow reference quantities."""
import collections,copy,json,re
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
OLD=ROOT/'data-source/recipe-full-reference'
OUT=ROOT/'data-source/recipe-final-residual'
def load(p):return json.loads(p.read_text(encoding='utf-8'))
def save(n,x):(OUT/n).write_text(json.dumps(x,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
def run():
    decisions=load(OLD/'ingredient-decisions.json')
    direct=[]
    for d in decisions:
        if d['status']!='UNIT_CONVERSION_UNVERIFIED':continue
        equivalent=[e['value'] for e in d['equivalents'] if e['unit']=='g' and e['value']>0]
        if len(equivalent)!=1 or not re.search(r'\(\s*[0-9.]+\s*g\s*\)',d['originalSpan']):continue
        if d['identityStatus']!='LINKED' or not d.get('nutritionProvenance'):continue
        d.update(amountGrams=equivalent[0],status='LINKED',unitStatus='SAME_RECIPE_EXPLICIT_GRAMS',
            unitReason='동일 원본 재료 행의 괄호 안 직접 g. 다른 레시피의 생활단위 크기는 적용하지 않음',
            conversionProvenance=dict(sourceUrl=d['sourceUrl'],sourceSha256=d['sourceSha256'],
                originalSpan=d['originalSpan'],amountGrams=equivalent[0],unit='g',scope='THIS_RECIPE_ROW_ONLY'))
        direct.append(copy.deepcopy(d))
    save('direct-original-mass-decisions.json',direct)
    save('ingredient-decisions.json',decisions)
    bykey={(d['recipeId'],d['ingredientIndex']):d for d in decisions}
    states={s['recipeId']:s for s in load(OUT/'recipe-final-states.json')}
    refs={s['recipeId']:s for s in load(OUT/'validated-reference-compositions.json')}
    remaining=load(OLD/'remaining-unresolved-ingredients.json')
    for row in remaining:
        d=bykey[row['recipeId'],row['ingredientIndex']];s=states[row['recipeId']]
        row.update(afterStatus=d['status'],resolvedOriginalAmountGrams=d['amountGrams'],
            originalConversionProvenance=d.get('conversionProvenance'),appCompleteAvailable=s['appCompleteAvailable'],
            finalReferenceId=s.get('referenceId'),finalTargetFoodIds=s['targetFoodIds'],
            identityReassessment=dict(status='EXACT_VERIFIED' if d['identityStatus']=='LINKED' else 'AMBIGUOUS',
                originalReason=d['identityReason'],candidateCount=len(d.get('candidates',[])),
                contextSha256=row['contextSha256'],referenceIdentityNotAppliedToOriginal=True),
            amountReassessment=dict(originalSpan=d['originalSpan'],originalGrams=d['amountGrams'],
                unitStatus=d['unitStatus'],unitReason=d['unitReason'],quantityRange=d['quantityRange'],
                noUnspecifiedAmountConversion=True),nutritionReassessment=dict(nutrientId=d['nutrientId'],
                provenance=d.get('nutritionProvenance'),noSimilarFoodSubstitution=True),
            originalAmountOverridden=False)
        rid=s.get('referenceId')
        if rid in refs:
            ref=refs[rid];row['finalWholeReference']=dict(recipeId=rid,name=ref['name'],complete=ref['complete'],
                sourceUrl=ref['sourceUrl'],sourceSha256=ref['sourceSha256'],compositionKind=ref['compositionKind'])
        if d['status']=='LINKED':row['failureReason']=None
    assert len(remaining)==1722
    save('residual-row-reassessments.json',remaining)
    conflict=[]
    for r in load(OLD/'portion-reviews.json'):
        if r['result']!='CONFLICTING_OFFICIAL_PORTIONS' or r['ingredient']=='설탕':continue
        d=bykey[r['recipeId'],r['ingredientIndex']]
        r.update(finalStatus='RESOLVED_THIS_RECIPE_DIRECT_GRAMS' if d['unitStatus']=='SAME_RECIPE_EXPLICIT_GRAMS' else 'UNRESOLVED',
            selectedAmountGrams=d['amountGrams'] if d['unitStatus']=='SAME_RECIPE_EXPLICIT_GRAMS' else None,
            selectionProvenance=d.get('conversionProvenance'),context=row_context(remaining,r),
            comparisons=[dict(ref,formReview='명시된 원문 재료 형태 보존',scopeReview='동일 recipe 행' if str(ref['page'])==r['recipeId'] else '다른 recipe/책자 행',
                servingDefinitionReview='해당 행의 g/생활단위 병기; 타 recipe의 크기·다짐·밀도 동일성 증명 없음') for ref in r['references']],
            finalReason=d['unitReason'] if d['unitStatus']=='SAME_RECIPE_EXPLICIT_GRAMS' else
                '원본 동일 행의 직접 g가 없음. 같은 재료·단위라도 원문과 같은 크기/다짐/측정법/serving 정의를 지정한 값이 없어 선택 보류. 평균·다수결 금지')
        conflict.append(r)
    assert len(conflict)==213
    save('portion-conflict-reassessments.json',conflict)
    save('original-ingredient-progress.json',dict(total=len(decisions),
        states=dict(collections.Counter(d['status'] for d in decisions)),
        unresolvedByCause=dict(collections.Counter(r['primaryGap'] for r in remaining if r['afterStatus']!='LINKED')),
        newOriginalLinks=len(direct),conflicts=dict(collections.Counter(r['finalStatus'] for r in conflict))))
def row_context(rows,r):
    return next((x['contextReviewed'] for x in rows if x['recipeId']==r['recipeId'] and x['ingredientIndex']==r['ingredientIndex']),None)
if __name__=='__main__':run()
