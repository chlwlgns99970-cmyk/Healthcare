"""Residual-only linkage: public bulk index, explicit state and separate evidence axes.

Never substitutes quantities from another recipe or changes frozen decisions.
"""
import collections,copy,hashlib,json,re
from pathlib import Path
from lxml import html
from adjudicate_recipe_inputs import ROOT,read,norm
from maximize_recipe_evidence import additional_nutrients,context_for
from publish_adjudicated_recipe_references import publish

OUT=ROOT/'data-source/recipe-linkage-strategy'
EXCLUDED='EXCLUDED_NON_CALORIC_OR_PROCESS_ITEM'
DATE='2026-10-05'
def load(p):return json.loads(p.read_text(encoding='utf-8'))
def save(name,x):(OUT/name).write_text(json.dumps(x,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
def key(d):return d['recipeId'],d['ingredientIndex']

def bulk_index():
    capture=next(c for c in load(OUT/'source-captures.json') if c['url'].endswith('searchKeyword1=R&limit=10000'))
    assert hashlib.sha256((ROOT/capture['rawFile']).read_bytes()).hexdigest()==capture['sha256']
    rows=load(OUT/'public-raw-nutrients.json');assert len(rows)==3672
    index={}
    for r in rows:
        assert r['데이터구분코드']=='R'
        if r['영양성분함량기준량']!='100g' or not r['에너지(kcal)']:continue
        index[r['식품코드']]=dict(code=r['식품코드'],name=r['식품명'],energyKcal=float(r['에너지(kcal)']),
            sourceUrl=capture['url'],sourceSha256=capture['sha256'],sourceName=r['출처명'],
            referenceAmount=100,referenceUnit='g',checkedAt=DATE,originalRecord=r)
    return index

def legacy_index():
    capture=next(c for c in load(OUT/'source-captures.json') if 'downloadFoodNutrientDB.do|POST|' in c['url'])
    assert hashlib.sha256((ROOT/capture['rawFile']).read_bytes()).hexdigest()==capture['sha256']
    rows=load(OUT/'mfds-legacy-nutrients-rows.json');assert len(rows)==4907
    grouped=collections.defaultdict(list)
    for r in rows[1:]:grouped[norm(r[2])].append(r)
    result={}
    for name,group in grouped.items():
        year=max(int(r[-1]) for r in group if str(r[-1]).isdigit())
        latest=[r for r in group if r[-1]==str(year)]
        # Do not select between conflicting same-year rows or infer density.
        values={(r[3],r[4]) for r in latest}
        if len(values)!=1:continue
        r=latest[0]
        if not isinstance(r[3],(int,float)) or r[3]<=0 or not isinstance(r[4],(int,float)):continue
        code='MFDS-LEGACY-'+str(int(r[0]))
        result[code]=dict(code=code,name=r[2].strip(),energyKcal=r[4]/r[3]*100,
            sourceUrl=capture['url'].split('|POST|')[0],sourceRequest=capture['url'],sourceSha256=capture['sha256'],
            sourceName='식약처 공개 기존 식품영양 DB',dataYear=year,referenceAmount=100,referenceUnit='g',
            originalServingGrams=r[3],originalServingKcal=r[4],originalRecord=r,checkedAt=DATE)
    return result

def legacy_rule(ingredient,context,index):
    k=norm(ingredient)
    cooking=context.split('식단소개',1)[-1]
    explicit={'된장':'된장','식초':'식초,식초','깨소금':'깨소금가루,볶은것',
        '돼지갈비':'돼지고기,갈비,생것','돼지고기(갈비)':'돼지고기,갈비,생것',
        '돼지고기(삼겹살)':'돼지고기,삼겹살,생것'}
    target=explicit.get(k);confidence='B' if k=='깨소금' else 'A'
    if k in ('파','다진파','잘게썬파') and (k!='파' or re.search(r'(?<![가-힣])파(?=는|를|의|와|도|,|\s)[^.]{0,35}(?:썰|썬|다듬|씻|송송)',cooking)):
        target='파,생것';confidence='C'
    if k=='감자' and re.search(r'(?<![가-힣])감자[^.]{0,40}(?:껍질|씻|깎|깍둑|나박|썰|썬|다듬)',cooking):
        target='감자,생것';confidence='C'
    if k=='미나리' and re.search(r'(?<![가-힣])미나리[^.]{0,40}(?:다듬|씻|잎을|데쳐|데친다|썰|썬)',cooking):
        target='미나리,생것';confidence='C'
    if not target:return None,'D',''
    matches=[code for code,n in index.items() if norm(n['name'])==target]
    if len(matches)!=1:return None,'D',''
    return matches[0],confidence,'식약처 별도 공식 DB의 품종·제품을 지정하지 않은 정확한 일반 항목. 최신 동일명 연도 사용; 세분 항목의 평균/대표 품종을 임의 생성하지 않음'

def state_rule(ingredient,context):
    k=norm(ingredient)
    # State is explicit in the ingredient. Official representative values, not
    # an invented mean of sample locations, sizes or species.
    if k=='멸치(건)':return 'R211-105033902-0000','A','원문 건멸치; 공공 자료의 전체 말린 멸치 대표값'
    if k in ('대구포','대구(포)') and re.search(r'대구포[^.]{0,35}불린',context):
        return 'R211-065034037-0000','C','같은 조리법에서 대구포를 물에 불리는 후속 단계; 최초 말린 포 중량'
    if k=='고춧잎' and re.search(r'건고춧잎[^.]{0,30}불려',context):
        return 'F019000B141a','C','같은 원문이 건고춧잎으로 명시하고 불리는 후속 단계 확인'
    if k=='싸리버섯' and re.search(r'싸리버섯[^.]{0,40}뿌리[^.]{0,20}흙',context):
        return 'G0150000000a','C','같은 조리법에서 뿌리 흙 제거가 명시된 신선 싸리버섯'
    if k=='다진파' and re.search(r'대파는 반은[^.]{0,25}반은 다진다',context):
        return 'F1910010000a','C','같은 조리법이 대파의 절반을 다진 파로 명시'
    if k=='차수수' and re.search(r'차수수[^.]{0,40}(?:씻|불려)',context):
        return 'A027001A010a','B','국립국어원 차수수=찰수수 정의 및 같은 원문의 씻고 불리는 최초 곡물 상태'
    return None,'D','품종·부위·상태 또는 제품 유형을 특정할 공식 문맥 부족'

def explicit_rda_portion(d,recipes):
    expected={('감자전분','큰술'):('RDA-90723','감자 전분 8g(1큰술)',8.0),
              ('다진생강','큰술'):('RDA-90773','다진 생강 4g(1/2큰술)',8.0)}
    row=expected.get((norm(d['ingredient']),d['unit']))
    if not row or not d['recipeId'].startswith('RDA') or d['identityStatus']!='LINKED' or d['amountGrams'] is not None or d['quantity'] is None or d['quantityRange']:return None
    rid,span,mass=row;r=recipes[rid]
    assert norm(span) in norm(r['mainIngredientText']+r['additionalIngredientText'])
    raw=ROOT/'app/build/food-quality-qa/recipe-source'/(rid.lower()+'.html')
    assert hashlib.sha256(raw.read_bytes()).hexdigest()==r['sourceSha256']
    return dict(grams=d['quantity']*mass,gramsPerUnit=mass,source='RDA_EXPLICIT_SAME_FORM_REFERENCE_PORTION',
        sourceUrl=r['sourceUrl'],sourceSha256=r['sourceSha256'],sourceRecipeId=rid,originalSpan=span,checkedAt=DATE,
        reason='농진청 동일 재료·동일 다짐/전분 형태·동일 큰술의 명시적 g 병기 참고 환산. 단일 공식 레시피 근거이며 실제 계량값을 확정하지 않음. 컵·개수·작은술로 확대하지 않음')

def build():
    before=load(OUT/'baseline-ingredient-decisions.json')
    assert len(before)==4258 and sum(d['status']=='LINKED' for d in before)==2143
    public=bulk_index();legacy=legacy_index()
    definitions={}
    for filename,word,required in [('5a59a62eb81fe805a96b','깨소금','볶은 참깨를 빻은 것'),('f8db862d6b8fdd64ac8f','차수수','찰수수')]:
        capture=next(c for c in load(OUT/'source-captures.json') if c.get('rawFile','').endswith(filename))
        raw=ROOT/capture['rawFile'];assert hashlib.sha256(raw.read_bytes()).hexdigest()==capture['sha256']
        doc=html.fromstring(raw.read_text(encoding='utf-8'));assert required in ' '.join(doc.xpath('//font[@class="dataLine"]//text()'))
        definitions[word]=capture
    nutrients={n['code']:n for n in load(ROOT/'data-source/full-adjudication/rda-10.4-nutrients.json')}
    nutrients.update(additional_nutrients());nutrients.update(public);nutrients.update(legacy)
    recipes={r['recipeId']:r for p in (ROOT/'data-source/recommendation').glob('official*public-recipe-facts.csv') for r in read(p)}
    contexts={};after=[];attempts=[];amount_groups=collections.defaultdict(list)
    for original in before:
        d=copy.deepcopy(original)
        if d['status'] in ('LINKED',EXCLUDED):after.append(d);continue
        rid=d['recipeId'];r=recipes[rid]
        if rid not in contexts:
            raw=ROOT/'app/build/food-quality-qa/recipe-source'/(rid.lower().replace('rda-diet-','nongsaro-diet-')+'.html')
            assert hashlib.sha256(raw.read_bytes()).hexdigest()==r['sourceSha256']
            contexts[rid]=context_for(r,raw)
        context=contexts[rid]
        identity,confidence,reason=state_rule(d['ingredient'],context)
        if not identity:identity,confidence,reason=legacy_rule(d['ingredient'],context,legacy)
        if identity:
            assert identity in nutrients,identity
            d.update(identityStatus='LINKED',nutrientId=identity,nutritionProvenance=nutrients[identity],
                identityReason=reason,matchingConfidence=confidence,matchingMethod='PUBLIC_BULK_AND_EXPLICIT_ORIGINAL_STATE')
            if norm(d['ingredient']) in definitions:d['identityProvenance']=[definitions[norm(d['ingredient'])]]
        elif d['identityStatus']=='LINKED':confidence='A' if d.get('matchingMethod')=='EXACT' else 'B'
        portion=explicit_rda_portion(d,recipes)
        if portion:d.update(amountGrams=portion['grams'],conversionProvenance=portion,unitStatus='OFFICIAL_FOOD_PORTION',unitReason=portion['reason'],matchingConfidence=confidence)
        amount='ORIGINAL_EXACT_AMOUNT' if d['amountGrams'] is not None and not d.get('conversionProvenance') else 'OFFICIAL_PORTION_DERIVED' if d['amountGrams'] is not None else 'NO_SAFE_AMOUNT'
        d.update(amountEvidenceStatus=amount,checkedAt=DATE)
        if original['status']!='RECIPE_SOURCE_INCOMPLETE':
            d['status']='LINKED' if d['identityStatus']=='LINKED' and d['amountGrams'] is not None else 'UNIT_CONVERSION_UNVERIFIED' if d['amountGrams'] is None else d['identityStatus']
        if d['status']=='LINKED':
            d['estimatedKcal']=d['amountGrams']*nutrients[d['nutrientId']]['energyKcal']/100
            d['amountProvenance']=dict(sourceUrl=d['sourceUrl'],sourceSha256=d['sourceSha256'],originalSpan=d['originalSpan'],basis='ORIGINAL_RECIPE_INPUT',checkedAt=DATE)
        # Keep independent failures visible, even when reporting mutually
        # exclusive primary causes. Fixing identity alone is never LINKED.
        axes=[]
        if d['identityStatus']!='LINKED':axes.append('identity')
        if d['amountGrams'] is None:axes.append('amount' if d['quantity'] is None or d['unit'] is None or d['quantityRange'] else 'unit')
        if rid=='MFDS-223':axes.append('recipeSource')
        if norm(d['ingredient'])=='주꾸미먹물':axes.append('nutrition')
        candidates=[n['code'] for n in public.values() if norm(d['ingredient']) in norm(n['name'])]
        attempt=dict(recipeId=rid,ingredientIndex=d['ingredientIndex'],ingredient=d['ingredient'],previousReason=original['identityReason'],
            originalSpan=d['originalSpan'],beforeStatus=original['status'],afterStatus=d['status'],
            confidence=confidence,identityChanged=d['nutrientId']!=original['nutrientId'],amountChanged=d['amountGrams']!=original['amountGrams'],
            amountEvidenceStatus=amount,failureAxes=axes,publicBulkCandidates=candidates,
            checkedSources=['PUBLIC_RAW_3672_LOCAL_INDEX','MFDS_LEGACY_4906_LOCAL_INDEX','ORIGINAL_RECIPE_CONTEXT_HASH_VERIFIED','REUSED_RDA_10.4_USDA_AND_OFFICIAL_UNIT_AUDITS'],
            query=d['ingredient'],result=reason,checkedAt=DATE,contextSha256=hashlib.sha256(context.encode()).hexdigest(),
            sameRecipeSourceVerified=True)
        attempts.append(attempt);after.append(d)
        if re.search(r'약간|적당량|적량|소량',d['originalSpan']):amount_groups[r['name']].append(attempt)
    assert len(attempts)==1961
    fixed={key(d):d for d in before if d['status'] in ('LINKED',EXCLUDED)}
    assert len(fixed)==2297 and all(d==fixed[key(d)] for d in after if key(d) in fixed)
    save('ingredient-decisions.json',after);save('residual-attempts.json',attempts)
    save('vague-amount-groups.json',amount_groups)
    save('compound-failure-groups.json',dict(collections.Counter('+'.join(a['failureAxes']) or 'resolved' for a in attempts)))
    save('bulk-index-summary.json',dict(publicRows=len(public),legacyRows=4906,legacyLatestUniqueNames=len(legacy),sources=dict(collections.Counter(n['sourceName'] for n in public.values())),queriedResiduals=1961))
    save('canonical-dictionary.json',[dict(ingredient=d['ingredient'],nutrientId=d['nutrientId'],confidence=d['matchingConfidence'],reason=d['identityReason']) for d,o in zip(after,before) if d['nutrientId']!=o['nutrientId']])
    previous={r['recipeId']:r for r in load(OUT/'baseline-recipe-final-audit.json')['audit']}
    grouped=collections.defaultdict(list)
    for d in after:grouped[d['recipeId']].append(d)
    selected={norm(r['name']):r['recipeId'] for r in load(ROOT/'data-source/recipe-linkage-residual/selected-complete-references.json')}
    for rid,rows in grouped.items():
        if not previous[rid]['complete'] and all(d['status'] in ('LINKED',EXCLUDED) for d in rows):selected.setdefault(norm(recipes[rid]['name']),rid)
    save('selected-complete-references.json',[dict(recipeId=rid,name=recipes[rid]['name']) for rid in selected.values()])
    summary=publish(after,nutrients,OUT,selected.values())
    save('newly-linked-ingredients.json',[d for d,o in zip(after,before) if d['status']=='LINKED' and o['status']!='LINKED'])
    save('newly-complete-recipes.json',[r for r in summary['audit'] if r['complete'] and not previous[r['recipeId']]['complete']])
    kimbap_ids={'RDA-DIET-89289-0','RDA-91342'}
    save('kimbap-new-attempts.json',[a|dict(newSource=next(d for d in after if key(d)==key(a)).get('nutritionProvenance',{}),
        linked=a['afterStatus']=='LINKED',grams=next(d for d in after if key(d)==key(a))['amountGrams'],
        estimatedKcal=next(d for d in after if key(d)==key(a)).get('estimatedKcal')) for a in attempts if a['recipeId'] in kimbap_ids])
    save('remaining-recipes.json',[r for r in summary['audit'] if not r['complete']])
    print(json.dumps(dict(newLinked=len(load(OUT/'newly-linked-ingredients.json')),newComplete=len(load(OUT/'newly-complete-recipes.json')),identityResolved=sum(a['identityChanged'] for a in attempts),attempted=len(attempts)),ensure_ascii=False))
    return summary
if __name__=='__main__':build()
