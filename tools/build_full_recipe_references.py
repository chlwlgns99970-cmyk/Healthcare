"""Publish whole official compositions separately from frozen original recipes."""
import collections
import csv
import hashlib
import json
import re
from pathlib import Path
from build_recipe_calorie_references import FIELDS, read

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'data-source/recipe-full-reference'
ASSETS = ROOT / 'app/src/main/assets/fooddata'

def load(path): return json.loads(path.read_text(encoding='utf-8'))
def save(name, value): (OUT/name).write_text(json.dumps(value, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
def norm(name): return re.sub(r'\s+', '', re.sub(r'\(\d+인분\)', '', name))

def build():
    originals = load(OUT/'recipe-final-audit.json' if (OUT/'recipe-final-audit.json').exists() else OUT/'baseline-recipe-final-audit.json')['audit']
    nutrients = {n['code']: n for n in load(ROOT/'data-source/full-adjudication/rda-10.4-nutrients.json')}
    captures = load(OUT/'source-captures.json')
    references = []
    for c in captures:
        if 'selectFoodDetail.json?fdCode=' not in c['url'] or c['status'] != 'CAPTURED': continue
        raw = ROOT/c['rawFile']
        assert hashlib.sha256(raw.read_bytes()).hexdigest() == c['sha256']
        data = load(raw)
        inputs = []
        for row in data.get('foodDetailList', []):
            n = nutrients.get(row['nationStdFoodCode'])
            if not n or norm(n['name']) != norm(row['foodNm']): n = None
            valid = isinstance(row['foodWgh'], (int, float)) and row['foodWgh'] > 0
            inputs.append(dict(ingredient=row['foodNm'], amountGrams=row['foodWgh'],
                amountEvidenceStatus='REFERENCE_RECIPE_EXACT' if valid else 'NO_SAFE_AMOUNT',
                identityStatus='REFERENCE_RECIPE_VERIFIED' if n else 'AMBIGUOUS', nutrient=n,
                originalRecord=row, estimatedKcal=row['foodWgh']*n['energyKcal']/100 if n and valid else None))
        if not inputs: continue
        header = data['foodDetailHeader']
        references.append(dict(recipeId='MENUZEN-'+inputs[0]['originalRecord']['fdCode'], name=header['fdNm'],
            compositionKind='REFERENCE_RECIPE', sourceInstitution='농촌진흥청 국립식량과학원 메뉴젠',
            sourceUrl=c['url'], sourceSha256=c['sha256'], checkedAt=c['checkedAt'],
            basis=f"메뉴젠에 제시된 전체 재료량 {header['totalFoodWgh']}g · 별도 공식 참고 구성",
            inputs=inputs, complete=all(x['nutrient'] and x['amountGrams']>0 for x in inputs),
            originalAmountOverridden=False, header=header))
    # Survey averages remain explicitly labelled, and never become cooking instructions.
    # Review all 613 survey groups, rather than just the earlier amount-only subset.
    originalNames={norm(r['name']) for r in originals}
    surveySource=next(c for c in load(ROOT/'data-source/recipe-amount-priority/source-captures.json')
        if c.get('recipeSourceRecord')==281 and c.get('fileSequence')==2)
    aliases={
        ('마늘, 구근, 생것','마늘, 생것'), ('간장, 양조','간장, 개량, 양조'),
        ('후추, 검은후추, 가루','후추, 검은색, 가루'), ('고춧가루, 가루','고춧가루'),
        ('참깨, 흰깨, 볶은것','참깨, 흰색, 볶은것'), ('참깨, 검정깨, 볶은것','참깨, 검은색, 볶은것'),
        ('청주, 알코올16%','발효주, 청주, 알코올16%'),
        ('얼갈이배추, 생것','배추, 얼갈이배추, 생것'), ('멸치젓, 액젓, 염절임','멸치, 액젓'),
        ('새우젓, 추젓, 염절임','새우, 젓갈, 추젓'),
        ('무시래기, 잎, 말린것, 삶은것','무청, 시래기, 말린것, 삶은것'),
        ('돼지고기, 목심(목심살), 생것','돼지고기, 목심, 생것'),
        ('돼지고기, 안심(안심살), 생것','돼지고기, 안심, 생것'),
        ('팥, 붉은팥, 말린것','팥, 적색, 말린것'), ('토마토 소스, 케첩','토마토 케첩'),
        ('사과, 부사(후지), 생것','사과, 부사, 생것'),
        ('포도, 건포도, 말린것','포도, 건포도'), ('국수, 칼국수, 생것','칼국수면, 생것'),
    }
    aliases={(norm(a),norm(b)) for a,b in aliases}
    kdca=[]
    from relink_recipe_strategy import bulk_index,legacy_index
    public=bulk_index();legacy=legacy_index()
    salt=public['R318-020000000-0000']
    assert salt['name']=='소금' and salt['referenceAmount']==100 and salt['referenceUnit']=='g'
    # Exact generic/qualified names from independently captured public tables.
    # Never turn legacy generic cucumber into a guessed modern cultivar.
    fallbacks={('R0200000009a','소금'):salt,
        ('F1480010000a','오이, 개량종, 생것'):public['R106-148010001-0000'],
        ('H0900000000a','키위, 생것'):legacy['MFDS-LEGACY-2096'],
        ('R0310000009a','조미료'):legacy['MFDS-LEGACY-4574']}
    identity=lambda s: re.sub(r'[\s_,]','',s)
    for (_,name),value in fallbacks.items():
        assert identity(name)==identity(value['name']) and value['referenceAmount']==100 and value['referenceUnit']=='g'
    for group in load(ROOT/'data-source/recipe-amount-priority/kdca-reference-compositions.json'):
        # Keep every public survey group available for independently reviewed aliases.
        inputs=[]
        for row in group:
            n=nutrients.get(row['officialCode'])
            exact=bool(n and norm(n['name'])==norm(row['ingredient']))
            alias=bool(n and (norm(row['ingredient']),norm(n['name'])) in aliases)
            if not exact and not alias: n=None
            fallback=fallbacks.get((row['officialCode'],row['ingredient']))
            if fallback: n=fallback
            inputs.append(row|dict(nutrient=n,status='EXACT_VERIFIED' if exact or fallback else 'ALIAS_VERIFIED' if alias else 'AMBIGUOUS',
                matchBasis='EXACT_QUALIFIED_NAME_IN_PUBLIC_100G_TABLE' if fallback else 'SAME_OFFICIAL_CODE_AND_QUALIFIED_NAME' if exact else 'REVIEWED_SAME_CODE_SEMANTIC_NAME_CHANGE' if alias else 'QUALIFIER_NOT_VERIFIED',
                estimatedKcal=row['grams']*n['energyKcal']/100 if n else None))
        kdca.append(dict(referenceId='KDCA-281-'+str(group[0]['excelRow']), name=group[0]['dish'],
            sourceUrl=surveySource['sourcePage'],sourceSha256=surveySource['sha256'],checkedAt='2026-10-05',
            basis='국민건강영양조사 음식별 조사 평균 참고 구성 · 조리용 레시피 아님',
            complete=all(x['nutrient'] for x in inputs),inputs=inputs))
    for r in kdca:
        references.append(dict(recipeId=r['referenceId'], name=r['name'], compositionKind='SURVEY_AVERAGE',
            sourceInstitution='질병관리청 국민건강영양조사', sourceUrl=r['sourceUrl'], sourceSha256=r['sourceSha256'],
            checkedAt=r['checkedAt'], basis=r['basis'], complete=r['complete'], originalAmountOverridden=False,
            inputs=[dict(ingredient=x['ingredient'], amountGrams=x['grams'], nutrient=x['nutrient'],
                identityStatus=x['status'], amountEvidenceStatus='REFERENCE_RECIPE_EXACT', originalRecord=x,
                estimatedKcal=x['estimatedKcal']) for x in r['inputs']]))
    # The two PDF compositions retain their independently reviewed nine nutrition links.
    for r in load(ROOT/'data-source/recipe-amount-priority/alternate-official-recipe-amounts.json'):
        references.append(r | dict(compositionKind='REFERENCE_RECIPE', sourceInstitution='식품의약품안전처',
            basis='공식 책자 1인분 전체 구성 · 원본 김밥 배합과 별도'))
    # Peer-reviewed Sugar control formulation. The table's 100.08% rounded sum
    # is preserved; these are reference mass parts, never 100g of finished jam.
    from maximize_recipe_evidence import additional_nutrients
    usda = additional_nutrients()
    jamSource = next(c for c in captures if c['url']=='https://www.foodengprog.org/download/download_pdf?pid=fep-26-1-44')
    jamInputs=[]
    for name,grams,code in [('냉동 딸기(해동)',54,None),('하얀 설탕',27,'169655'),
        ('펙틴(Yellow Ribbon 1500M)',1.08,None),('물',18,None)]:
        n=usda.get(code)
        jamInputs.append(dict(ingredient=name,amountGrams=grams,nutrient=n,
            identityStatus='CONTEXT_VERIFIED' if n else 'AMBIGUOUS',
            amountEvidenceStatus='REFERENCE_OFFICIAL_CONVERSION',
            conversionProvenance=dict(sourceUrl=jamSource['url'],sourceSha256=jamSource['sha256'],
                table='Table 1 Sugar',massParts=grams,normalization='100 mass parts; rounded sum 100.08 retained'),
            estimatedKcal=grams*n['energyKcal']/100 if n else None))
    references.append(dict(recipeId='ACADEMIC-JAM-2022-SUGAR',name='딸기잼',
        compositionKind='REFERENCE_RECIPE',sourceInstitution='원광대학교·서울대학교 식품공학 학술논문',
        sourceUrl=jamSource['url'],sourceSha256=jamSource['sha256'],checkedAt=jamSource['checkedAt'],
        basis='논문 Sugar 구성의 중량비를 100 기준으로 환산 · 표의 반올림 합계 100.08g 유지 · 완성 잼 100g 아님',
        inputs=jamInputs,complete=False,originalAmountOverridden=False))
    byname = collections.defaultdict(list)
    for r in references: byname[norm(r['name'])].append(r)
    selected = {}
    for name, choices in byname.items():
        # Prefer complete cooking compositions, then quantified coverage. Never borrow missing rows.
        choices.sort(key=lambda r: (not r['complete'], r['compositionKind']=='SURVEY_AVERAGE',
            -sum(bool(x['nutrient']) for x in r['inputs']), r['recipeId']))
        selected[name] = choices[0]
    variantSelections=[]
    for name in sorted(originalNames):
        if name in selected or '(' in name or '<' in name or '/' in name: continue
        choices=[r for r in references if r['recipeId'].startswith('MENUZEN-') and
            norm(r['name'].split('(',1)[0])==name and '(' in r['name']]
        if not choices: continue
        originalIngredients={norm(i['ingredient']) for o in originals if norm(o['name'])==name for i in o['ingredients']}
        def overlap(r):
            full=' '.join(norm(x['ingredient']) for x in r['inputs'])
            return sum(bool(i) and i in full for i in originalIngredients)
        choices.sort(key=lambda r:(not r['complete'],-overlap(r),-sum(bool(x['nutrient']) for x in r['inputs']),r['recipeId']))
        selected[name]=choices[0]
        variantSelections.append(dict(originalGenericName=name,selectedReferenceId=choices[0]['recipeId'],
            selectedReferenceName=choices[0]['name'],ingredientOverlap=overlap(choices[0]),
            candidateIds=[r['recipeId'] for r in choices],
            reason='동일 일반 음식명·조리형태의 공식 명명 변형. 완전 구성과 원본 재료 문맥의 겹침을 우선; 원본 배합으로 확정하지 않고 변형명을 그대로 표시'))
    # A named kimbap variant is an example of generic kimbap, not its certified formulation.
    selected['김밥'] = next(r for r in references if r['recipeId']=='MENUZEN-D016004')
    referenceAliases={
        '느타리버섯볶음':('MENUZEN-D103032','공식 제목 버섯볶음(느타리버섯)은 같은 주재료와 볶음 조리형태를 명시. 98g 전체 구성을 독립 참고 레시피로 표시',None),
        '마늘쫑볶음':('MENUZEN-D103025','국립국어원: 마늘종의 비표준 표기 마늘쫑',
            'https://www.korean.go.kr/front/onlineQna/onlineQnaView.do?mn_id=73&pageIndex=1&qna_seq=328566'),
        '임연수구이':('MENUZEN-D081031','제조사 원문: 임연수구이의 생선은 임연수어',
            'https://www.cjthemarket.com/the/product/product-main?areaNum=10&plnId=300004&prdCd=40190781'),
        '무조림(무시왁저기)':('MENUZEN-D113009','원본 제목이 무조림의 향토 동의명을 괄호로 명시',None),
        '밀가루수제비(수제비,밀가루자베기)':('MENUZEN-D031010','원본 제목의 수제비 동의명; 밀가루 수제비의 명명 변형 전체 참고 구성',None),
    }
    for name,(rid,reason,url) in referenceAliases.items():
        selected[norm(name)]=next(r for r in references if r['recipeId']==rid)
        variantSelections.append(dict(originalGenericName=name,selectedReferenceId=rid,
            selectedReferenceName=selected[norm(name)]['name'],reason=reason,identitySource=url))
    foods = [f for f in read(ASSETS/'food_items.csv') if not f['brand'] and f['sourceType']=='K-FIND']
    foodnames = collections.defaultdict(list)
    for f in foods: foodnames[norm(f['name'])].append(f)
    output = []
    publications = []
    def publish(r, targets):
        linked = [x for x in r['inputs'] if x.get('nutrient') and x['amountGrams']>0]
        if not linked: return
        text = ', '.join(f"{x['ingredient']} {x['amountGrams']:g}g" for x in r['inputs'])
        combined = {}
        for x in linked:
            n = x['nutrient']; identity = n['code']
            combined.setdefault(identity, [x['ingredient'], 0, n])[1] += x['amountGrams']
        for f in targets:
            for code,(name,grams,n) in combined.items():
                output.append(dict(foodId=f['id'],recipeId=r['recipeId'],recipeName=r['name'],recipeBasis=r['basis'],
                    ingredientText=text,ingredientName=name,amountGrams=f'{grams:.15g}',nutrientFoodId='official-reference-'+code,
                    nutrientName=n['name'],kcalPer100g=f"{n['energyKcal']:g}",recipeUrl=r['sourceUrl'],
                    nutrientUrl=n['sourceUrl'],recipeSha256=r['sourceSha256'],checkedAt=r['checkedAt'],
                    recipeComplete=str(r['complete']).lower(),foodReferenceKcal=f['energyKcal'],
                    foodReferenceAmount=f['referenceAmount'],foodReferenceUnit=f['unit'],
                    compositionKind=r['compositionKind'],sourceInstitution=r['sourceInstitution']))
        publications.append(dict(recipeId=r['recipeId'],name=r['name'],complete=r['complete'],
            foodIds=[f['id'] for f in targets], ingredientCount=len(r['inputs']),linkedCount=len(linked)))
    for name,r in selected.items(): publish(r, foodnames[name])
    # Keep both known PDF references visibly separate; their 23 amounts and nine links are shipped.
    for r in references:
        if r['recipeId'].startswith('MFDS-BOOK3-'): publish(r, foodnames['김밥'])
    assert len({(r['foodId'],r['recipeId'],r['ingredientName']) for r in output}) == len(output)
    with (ASSETS/'official_recipe_reference_estimates.csv').open('w',encoding='utf-8',newline='') as stream:
        writer=csv.DictWriter(stream,fieldnames=FIELDS+['compositionKind','sourceInstitution'])
        writer.writeheader();writer.writerows(output)
    states=[]
    for original in originals:
        r=selected.get(norm(original['name']))
        published=bool(r and foodnames[norm(original['name'])])
        state='ORIGINAL_COMPLETE' if original['complete'] else 'REFERENCE_COMPLETE' if r and r['complete'] and published else \
            'PARTIAL' if original['status']=='PARTIAL_LINKED' or r and any(x.get('nutrient') for x in r['inputs']) else 'UNRESOLVED'
        states.append(dict(recipeId=original['recipeId'],name=original['name'],state=state,
            originalComplete=original['complete'],referenceId=r['recipeId'] if r else None,
            referencePublished=published,referenceComplete=bool(r and r['complete']),targetFoodIds=original['targetFoodIds']))
        states[-1]['appCompleteAvailable']=bool(original['complete'] and original['published'] or r and r['complete'] and published)
    save('reference-composition-audit.json',references)
    save('reference-publication-audit.json',publications)
    save('reference-variant-selection-audit.json',variantSelections)
    save('recipe-final-states.json',states)
    summary=dict(recipeCount=len(states),states=dict(collections.Counter(x['state'] for x in states)),
        acquiredReferences=len(references),publishedReferences=sum(bool(x['foodIds']) for x in publications),
        publishedFoods=len({x['foodId'] for x in output}),publishedRows=len(output),
        appCompleteCoverage=sum(x['appCompleteAvailable'] for x in states),
        assetSha256=hashlib.sha256((ASSETS/'official_recipe_reference_estimates.csv').read_bytes()).hexdigest())
    save('reference-progress.json',summary)
    print(json.dumps(summary,ensure_ascii=False))

if __name__=='__main__': build()
