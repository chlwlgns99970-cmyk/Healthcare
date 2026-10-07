"""Evidence-bearing independent identity and amount decisions for official recipes."""
from pathlib import Path
import csv,hashlib,json,re,collections,os
from recipe_ingredient_parser import parse
from reviewed_recipe_identities import RDA_ALIASES,AMBIGUITY,NO_MATCH,RECIPE_REVIEWED_ALIASES,CANDIDATE_TERMS
import build_recipe_calorie_references as old
ROOT=Path(__file__).resolve().parents[1];SOURCE_OUT=ROOT/'data-source/full-adjudication'
OUT=ROOT/os.environ.get('RECIPE_AUDIT_OUT','data-source/recipe-linkage-maximization')
OUT.mkdir(parents=True,exist_ok=True)
def norm(t):return re.sub(r'\s+','',t)
def read(p):return list(csv.DictReader(p.open(encoding='utf-8-sig',newline='')))
def build():
    recipes={r['recipeId']:r for p in (ROOT/'data-source/recommendation').glob('official*public-recipe-facts.csv') for r in read(p)}
    nutrients={r['code']:r for r in json.loads((SOURCE_OUT/'rda-10.4-nutrients.json').read_text(encoding='utf-8'))}
    legacy=old.archive_nutrients()
    for k,v in legacy.items():nutrients[k]=dict(name=v['description'],energyKcal=v['energyKcal'],sourceUrl=f'https://fdc.nal.usda.gov/food-details/{k}/nutrients',sha256=v['archiveSha256'])
    assert all(k in nutrients for k in RDA_ALIASES.values())
    aliases=RDA_ALIASES|old.ALIASES
    maximize=os.environ.get('RECIPE_MAXIMIZE','1')=='1'
    if maximize:
        from maximize_recipe_evidence import context_for,resolve_identity,portion_conversion,additional_nutrients
        nutrients.update(additional_nutrients())
    # Audit every explicit equivalence before considering a household conversion.
    allinputs={k:parse(', '.join(filter(None,[r['mainIngredientText'],r['additionalIngredientText']]))) for k,r in recipes.items()}
    unit_evidence=collections.defaultdict(list)
    for rid,inputs in allinputs.items():
        for i in inputs:
            if i['unit'] not in ('g','kg') or i['quantity'] is None:continue
            for eq in i['equivalents']:
                if eq['value']>0:unit_evidence[(norm(i['ingredient']),eq['unit'])].append(dict(recipeId=rid,sourceUrl=recipes[rid]['sourceUrl'],originalSpan=i['originalSpan'],gramsPerUnit=i['quantity']*(1000 if i['unit']=='kg' else 1)/eq['value']))
    unit_audit=[dict(ingredient=k[0],unit=k[1],distinctGramsPerUnit=sorted({round(e['gramsPerUnit'],8) for e in es}),evidence=es) for k,es in unit_evidence.items()]
    (OUT/'official-unit-equivalences.json').write_text(json.dumps(unit_audit,ensure_ascii=False,indent=2),encoding='utf-8')
    decisions=[];review=[];candidate_cache={}
    normalized_nutrients=[(k,n['name'],norm(n['name'])) for k,n in nutrients.items()]
    for r in recipes.values():
        raw=ROOT/'app/build/food-quality-qa/recipe-source'/(r['recipeId'].lower().replace('rda-diet-','nongsaro-diet-')+'.html')
        assert hashlib.sha256(raw.read_bytes()).hexdigest()==r['sourceSha256']
        inputs=allinputs[r['recipeId']]
        context=context_for(r,raw) if maximize else ''
        for index,i in enumerate(inputs):
            key=norm(i['ingredient']);identity=(aliases|old.RECIPE_ALIASES.get(r['recipeId'],{})|RECIPE_REVIEWED_ALIASES.get(r['recipeId'],{})).get(key)
            # Recipe-specific nori identity: both recipes explicitly wrap rice in dried sheets.
            if '김밥' in r['name'] and key=='김':identity='L0030010001a'
            candidatekey=CANDIDATE_TERMS.get(key,re.sub(r'^(?:다진|채썬|통)','',key))
            if candidatekey not in candidate_cache:
                candidate_cache[candidatekey]=[dict(code=k,name=name) for k,name,normalized in normalized_nutrients if candidatekey and candidatekey in normalized]
            candidates=candidate_cache[candidatekey]
            reason=AMBIGUITY.get(key)
            match_evidence=None
            if maximize and not identity:
                identity,match_evidence=resolve_identity(key,context,r,nutrients)
            excluded=key in ('물','찬물','더운물','밥짓는물','물적당') or key.startswith('소금(데침용)') or key=='소금(해감용)'
            if excluded:identity_state='EXCLUDED_NON_CALORIC_OR_PROCESS_ITEM';reason='원문 물 또는 세척·데침용 소금: 섭취 열량 합산 대상 아님'
            elif identity:identity_state='LINKED';reason=match_evidence or '원문 재료명과 조리 상태를 보존한 검토 alias / 공식 영양 DB 개별 행'
            elif reason:identity_state='AMBIGUOUS_IDENTITY'
            elif key in NO_MATCH:identity_state='NO_OFFICIAL_NUTRITION_MATCH';reason=NO_MATCH[key]
            else:identity_state='UNREVIEWED';reason='추가 identity 검토 필요'
            grams=None;unit_state='UNIT_CONVERSION_UNVERIFIED';unit_reason='원문 수치에 단위 표기가 없거나 약간·적량: 계량표가 있어도 실제 사용량 확정 불가'
            unit_candidates=unit_evidence.get((key,i['unit']),[])
            local=[e for e in unit_candidates if e['recipeId']==r['recipeId']]
            if i['unit'] in ('g','kg') and i['quantity'] is not None:
                grams=i['quantity']*(1000 if i['unit']=='kg' else 1);unit_state='DIRECT_MASS';unit_reason='원문 직접 중량'
            elif excluded:unit_state='EXCLUDED_NON_CALORIC_OR_PROCESS_ITEM';unit_reason=reason
            elif local and i['quantity'] is not None and len({round(e['gramsPerUnit'],8) for e in local})==1:
                grams=i['quantity']*local[0]['gramsPerUnit'];unit_state='OFFICIAL_RECIPE_CONVERSION';unit_reason='같은 recipe·동일 재료·동일 단위의 원문 중량 병기'
            elif (key,i['unit']) in {('고춧가루','작은술'),('물엿','큰술')} and i['quantity'] is not None and len({e['recipeId'] for e in unit_candidates})>=2 and len({round(e['gramsPerUnit'],8) for e in unit_candidates})==1:
                grams=i['quantity']*unit_candidates[0]['gramsPerUnit'];unit_state='OFFICIAL_SOURCE_EQUIVALENCE';unit_reason='동일 농촌진흥청 공식 source의 복수 recipe에서 동일 재료·계량스푼의 g 병기가 모두 일치: 참고 환산. 개수·컵·충돌 중량에는 적용하지 않음'
            elif maximize and (key,i['unit']) in {('설탕','컵'),('달걀','개'),('통깨','작은술')} and i['quantity'] is not None and len({e['recipeId'] for e in unit_candidates})>=5 and len({round(e['gramsPerUnit'],8) for e in unit_candidates})==1:
                grams=i['quantity']*unit_candidates[0]['gramsPerUnit'];unit_state='OFFICIAL_REVIEWED_STANDARD_PORTION'
                unit_reason='동일 일반 재료의 공식 recipe 중량 병기 5개 이상이 모두 일치하는 명시적 표준 참고량. 설탕 컵·달걀 개·통깨 계량스푼에만 적용; 생선 한 마리·모든 컵·충돌 병기에는 미적용'
            elif i['unit']:
                unit_reason='공식 kcal 기준은 100g; 원문 부피/개수에서 가식부 g 환산 근거 없음'
                if len({round(e['gramsPerUnit'],8) for e in unit_candidates})>1:unit_reason='동일 공식 source의 재료 단위별 중량 병기가 충돌: 다른 recipe 값을 일괄 적용 불가'
            conversion=None
            if maximize and unit_state=='OFFICIAL_REVIEWED_STANDARD_PORTION':
                conversion=dict(grams=grams,gramsPerUnit=unit_candidates[0]['gramsPerUnit'],source='RDA_OFFICIAL_RECIPE_STANDARD_PORTION',
                    sourceUrl=unit_candidates[0]['sourceUrl'],recipeEquivalences=unit_candidates,checkedAt='2026-10-05',reason=unit_reason)
            if maximize and grams is None and not excluded:
                conversion=portion_conversion(key,i,identity)
                if conversion:
                    grams=conversion['grams'];unit_state='OFFICIAL_FOOD_PORTION';unit_reason=conversion['reason']
            if not maximize and key=='소금' and nutrients[identity]['energyKcal']==0 and grams is None:
                excluded=True;identity_state='EXCLUDED_NON_CALORIC_OR_PROCESS_ITEM';reason='검증된 공식 소금 열량은 0 kcal: 미정 사용량을 만들지 않고 비열량 기여 재료로 보존'
            if identity_state=='LINKED' and grams is not None:state='LINKED'
            elif identity_state=='UNREVIEWED' or unit_state=='UNREVIEWED':state='UNREVIEWED'
            elif excluded:state=identity_state
            elif not grams:state='UNIT_CONVERSION_UNVERIFIED'
            else:state=identity_state
            if maximize and len(inputs)==1 and key.startswith(norm(r['name'])) and not context:
                state='RECIPE_SOURCE_INCOMPLETE'
                reason='공식 페이지는 완성 음식의 중량만 제공하고 조리법·원재료 배합이 없음: 완성 음식의 kcal를 재료 kcal로 순환 연결하지 않음'
            decisions.append(dict(recipeId=r['recipeId'],recipeName=r['name'],ingredientIndex=index,**i,identityStatus=identity_state,identityReason=reason,unitStatus=unit_state,unitReason=unit_reason,unitCandidates=unit_candidates,amountGrams=grams,nutrientId=identity,candidates=candidates,status=state,sourceUrl=r['sourceUrl'],sourceSha256=r['sourceSha256'],checkedAt='2026-10-05'))
            if maximize:
                decisions[-1].update(nutritionProvenance=nutrients.get(identity),conversionProvenance=conversion,contextSha256=hashlib.sha256(context.encode()).hexdigest())
    summary=dict(recipeCount=len(recipes),ingredientRows=len(decisions),identityCounts=dict(collections.Counter(x['identityStatus'] for x in decisions)),unitCounts=dict(collections.Counter(x['unitStatus'] for x in decisions)),ingredientCounts=dict(collections.Counter(x['status'] for x in decisions)),publicationUpdated=False)
    (OUT/'ingredient-decisions.json').write_text(json.dumps(decisions,ensure_ascii=False,indent=2),encoding='utf-8')
    (OUT/'recipe-progress.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps(summary,ensure_ascii=False))
    return decisions,nutrients
if __name__=='__main__':build()
