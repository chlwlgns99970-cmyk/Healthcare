"""Reviewed exact identities and additional official composition evidence.

This source review adds literal major ingredients, including measured alternate
recipes for the same dish. It never claims an alternate recipe is universal,
transfers nutrition/servings, or relaxes the existing eligibility weights.
"""
import csv
import hashlib
import json
import re
from collect_public_recipe_evidence import ROOT, OUT as RECOMMENDATION, CHECKED, read_csv, write_csv, plain
from build_recommendation_ingredient_evidence import classify, cook, composition_with_explicit_filling, PROTEINS
from collect_catalog_recommendation_sources import OUT, RAW, SOURCES

VERSION = 'catalog-recommendation-group-review-v1'
FIELDS = list(read_csv(RECOMMENDATION / 'verified-food-groups.csv')[0]) + [
    'foodItemId', 'sourceFoodCode', 'originalFoodName', 'brand', 'identityMatchReason', 'ingredientText']

def build():
    OUT.mkdir(parents=True, exist_ok=True)
    templates = {r['id']: r for r in read_csv(ROOT / 'app/src/main/assets/fooddata/meal_templates.csv')}
    foods = {r['id']: r for r in read_csv(ROOT / 'app/src/main/assets/fooddata/food_items.csv')}
    links = {r['mealTemplateId']: r['foodItemId'] for r in read_csv(ROOT / 'app/src/main/assets/fooddata/meal_template_ingredients.csv')}
    existing = read_csv(RECOMMENDATION / 'verified-food-groups.csv')
    baseline_path = OUT / 'baseline.json'
    if not baseline_path.exists():
        eligible = {r['stableTemplateId'] for r in existing if r['slowStyleEligible'] == 'true'}
        baseline = dict(checkedAt=CHECKED, rows=292, sourcedGroups=len(existing), styleEligible=len(eligible),
                        sourcedIds=[r['stableTemplateId'] for r in existing], eligibleIds=sorted(eligible),
                        perMeal={m:sum(t['id'] in eligible and m in t['supportedMealTypes'] for t in templates.values())
                                 for m in ['BREAKFAST','LUNCH','DINNER','SNACK']},
                        protectedSha256={str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in [
                            ROOT / 'app/src/main/assets/fooddata/meal_templates.csv',
                            ROOT / 'app/src/main/assets/fooddata/meal_template_ingredients.csv',
                            ROOT / 'app/src/main/assets/fooddata/food_items.csv']})
        baseline_path.write_text(json.dumps(baseline, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
    result=[]
    def add(tid, ingredients, groups, cooking, grain, url, digest, source_name, rid, scope, identity, note, ingredient_text=''):
        t=templates[tid]; food=foods[links[tid]]
        result.append(dict(stableTemplateId=tid, menuName=t['name'], ingredients='|'.join(ingredients),
                           foodGroups='|'.join(sorted(groups)), grainType=grain,
                           proteinSources='|'.join(sorted(groups & PROTEINS)) or 'UNKNOWN', cookingStyle=cooking,
                           sourceName=source_name, sourceUrl=url, evidenceScope=scope, verifiedAt=CHECKED,
                           slowStyleEligible='false', notes=note+'; 전체 원재료·알레르기·실제 영양값·분량은 확정하지 않음',
                           evidenceRecipeId=rid, ingredientStatus='PARTIAL_DESCRIPTION' if scope.startswith('BRAND') else 'REFERENCE_PARTIAL',
                           allergenStatus='UNKNOWN', negativeSignals='', parserVersion=VERSION, sourceSha256=digest,
                           foodItemId=food['id'], sourceFoodCode=food['sourceFoodCode'], originalFoodName=food['name'],
                           brand=food['brand'], identityMatchReason=identity, ingredientText=ingredient_text))
    # Exact Korean synonymous / orthographic dish names. Only these reviewed
    # pairs are accepted; no global adjective removal or fuzzy matching.
    kto = [
        ('181470', 'kfind-catalog-d104-194000000-0001', '야채죽', ['쌀','당근','호박','양파'], {'GRAIN_UNSPECIFIED','VEGETABLE'}, 'SIMMERED', 'RICE_OR_FLOUR',
         '채소죽/야채죽은 같은 채소 조리명 표현; 본문 쌀·당근·호박·양파를 끓이는 구성 확인', ['rice','carrots','pumpkin','onions','simmer']),
        ('179699', 'kfind-catalog-d103-168000000-0001', '짜장면', ['돼지고기','양파','호박','짜장소스','밀가루 국수'], {'RED_MEAT','VEGETABLE','GRAIN_UNSPECIFIED'}, 'PAN_COOKED', 'RICE_OR_FLOUR',
         '자장면/짜장면 표준 허용 표기만 연결; 채소와 돼지고기를 볶는 조리 구성 확인', ['Pork','onions','pumpkins','stir-fried','flour-based noodles']),
        ('231600', 'kfind-catalog-d103-141000000-0001', '간짜장', ['돼지고기','양파','짜장소스','국수'], {'RED_MEAT','VEGETABLE','GRAIN_UNSPECIFIED'}, 'PAN_COOKED', 'RICE_OR_FLOUR',
         '간자장/간짜장 표기만 연결; 해당 간짜장 본문의 돼지고기·양파·국수 구성 확인', ['noodles','Pork','onions','stir-fried']),
    ]
    for sid, tid, source_title, ingredients, groups, cooking, grain, note, observed in kto:
        raw=(RAW / f'kto-{sid}.html').read_bytes(); text=raw.decode('utf-8')
        titles=[plain(t) for t in re.findall(r'<h2 class="title">(.*?)</h2>', text, re.S)]
        assert any(f'({source_title} /' in title for title in titles), (sid, titles)
        body=plain(re.search(r'<div class="condetail">(.*?)</div>', text, re.S)[1])
        assert all(term in body for term in observed), (sid, body)
        assert not foods[links[tid]]['brand']
        add(tid,ingredients,groups,cooking,grain,SOURCES[f'kto-{sid}.html'],hashlib.sha256(raw).hexdigest(),
            '한국관광공사 공식 음식 소개', 'KTO-'+sid, 'PUBLIC_DISH_DEFINITION',
            'REVIEWED_EXACT_DISH_SYNONYM:'+source_title, note)
    # A source's parenthetical "나물" identifies the same gajuk namul dish.
    raw=(RAW / 'rda-91474.html').read_bytes(); text=raw.decode('utf-8')
    fields={plain(a):plain(b) for a,b in re.findall(r'<th[^>]*>(.*?)</th>\s*<td[^>]*>(.*?)</td>',text,re.S)}
    assert fields['음식명']=='가죽무침(나물) English' and '가죽 300g' in fields['식재료']
    _,gs=classify(fields['식재료'])
    add('kfind-catalog-d114-601000000-0001', ['가죽','깨소금'], gs, 'UNHEATED', 'NONE', SOURCES['rda-91474.html'],
        hashlib.sha256(raw).hexdigest(), '농촌진흥청 농식품 올바로 전통향토음식', 'RDA-91474', 'PUBLIC_REFERENCE_RECIPE',
        'SOURCE_EXPLICIT_PARENTHETICAL_NAMUL_IDENTITY', '가죽무침(나물)의 명시된 나물 표현과 조리명 연결; 가죽 주재료 300g 확인')
    # Additional quantity-declared same-title recipes. Record the alternate
    # composition explicitly; do not merge unlike recipes or copy any quantity.
    recipes={r['recipeId']:r for r in read_csv(RECOMMENDATION/'official-public-recipe-facts.csv')}
    for tid,rid,title in [('kfind-catalog-d101-039000000-0001','RDA-92064','찰밥'),
                          ('kfind-catalog-d105-200000000-0001','RDA-91015','감자국')]:
        r=recipes[rid]; assert r['name']==title and templates[tid]['name']==title
        parsed,gs=classify(composition_with_explicit_filling(r))
        assert all(not i['quantityUnitUnverified'] for i in parsed if i['group'])
        grain='RICE_OR_FLOUR' if 'GRAIN_UNSPECIFIED' in gs else 'NONE'
        add(tid,[i['name'] for i in parsed if i['name']],gs,cook(r),grain,r['sourceUrl'],r['sourceSha256'],r['sourceName'],rid,
            'PUBLIC_REFERENCE_RECIPE','SOURCE_EXACT_SAME_TITLE_ADDITIONAL_QUANTITY_DECLARED_RECIPE',
            '기존 단일 조리자료 이외의 같은 음식명 공공 참고 조리자료에서 주요 팥/양파 구성 확인; 조리 변형이므로 모든 실제 준비 방식에 포함된다고 주장하지 않음')
    # Corporate official release gives the exact old menu that the current
    # catalogue no longer exposes. Adjacent falafel/bean/frying prose belongs
    # to another named product and is deliberately excluded.
    raw=(RAW/'cj-vegi-wrap.html').read_bytes(); text=raw.decode('utf-8')
    observed='‘베지텐더 밸런스랩’은 식물성 텐더와 아삭한 채소가 어우러진 콜드 샌드위치다.'
    assert observed in plain(text) and '뚜레쥬르' in plain(text)
    tid='kfind-catalog-d202-083000000-0021'; food=foods[links[tid]]
    assert food['brand']=='뚜레쥬르' and food['name']=='또띠아_베지텐더밸런스랩'
    add(tid,['식물성 텐더','채소'],{'VEGETABLE'},'UNHEATED','NONE',SOURCES['cj-vegi-wrap.html'],hashlib.sha256(raw).hexdigest(),
        'CJ 공식 뉴스룸 뚜레쥬르 제품 보도자료(2022-06-03)', 'CJ-TLJ-VEGI-TENDER-20220603', 'BRAND_OFFICIAL_MAJOR_INGREDIENTS',
        'EXACT_BRAND_OFFICIAL_MENU_NAME_WHITESPACE_ONLY',
        '동일 브랜드·정확 메뉴명으로 과거 공식 제품 소개 연결; 원본 K-FIND 영양 분석시점과 recipe version 일치 여부는 미확인; 식물성 텐더의 콩/곡물 구성·조리방법은 미확인; 인접한 담백아삭 베지랩의 팔라펠·병아리콩·튀김 설명을 복사하지 않음', observed)
    write_csv(OUT/'reviewed-group-evidence.csv',result,FIELDS)
    # Food metadata connection is exact ID + code + brand + original source
    # name. Keep the known literal description and preserve allergy UNKNOWN.
    metadata=[]
    for r in result:
        metadata.append(dict(foodItemId=r['foodItemId'],sourceFoodCode=r['sourceFoodCode'],brand=r['brand'],name=r['originalFoodName'],
                             ingredientText=r['ingredientText'],ingredients=r['ingredients'],foodGroups=r['foodGroups'],
                             ingredientStatus=r['ingredientStatus'],allergenStatus='UNKNOWN',allergens='',
                             sourceName=r['sourceName'],sourceUrl=r['sourceUrl'],checkedAt=CHECKED,
                             sourceDate='2022-06-03' if r['brand'] else '',parserVersion=VERSION,
                             evidenceKind=r['evidenceScope']+'|'+r['identityMatchReason'],sourceHash=r['sourceSha256'],
                             foodGroupEvidenceScope=r['evidenceScope'],
                             staleCandidate='true' if r['brand'] else 'false',
                             notes=r['notes']))
    write_csv(OUT/'metadata-evidence.csv',metadata,list(metadata[0]))
    print(f'{len(result)} reviewed official compositions; no nutrition/serving/allergy import')

def audit():
    before=json.loads((OUT/'baseline.json').read_text(encoding='utf-8'))
    rows=read_csv(RECOMMENDATION/'verified-food-groups.csv')
    templates=read_csv(ROOT/'app/src/main/assets/fooddata/meal_templates.csv')
    research=read_csv(RECOMMENDATION/'recommendation-ingredient-research.csv')
    eligible={r['stableTemplateId'] for r in rows if r['slowStyleEligible']=='true'}
    sourced={r['stableTemplateId'] for r in rows}
    assert len(templates)==292
    for relative,digest in before['protectedSha256'].items():
        if relative.endswith(('meal_templates.csv','meal_template_ingredients.csv')):
            assert hashlib.sha256((ROOT/relative).read_bytes()).hexdigest()==digest
    summary=dict(checkedAt=CHECKED,parserVersion=VERSION,stableTemplates=292,
                 before={k:before[k] for k in ['sourcedGroups','styleEligible','perMeal']},
                 after=dict(sourcedGroups=len(sourced),sourcedIngredients=len(sourced),styleEligible=len(eligible),
                            unresolved=292-len(sourced),perMeal={m:sum(t['id'] in eligible and m in t['supportedMealTypes']
                            for t in templates) for m in ['BREAKFAST','LUNCH','DINNER','SNACK']}),
                 newSourcedTemplateIds=sorted(sourced-set(before['sourcedIds'])),
                 newStyleTemplateIds=sorted(eligible-set(before['eligibleIds'])),
                 removedStyleTemplateIds=sorted(set(before['eligibleIds'])-eligible),
                 nutritionTransferred=0,servingTransferredFromRecipes=0,policyWeightsChanged=0,
                 officialAllergenDeclarationsAdded=0,allReferenceRecipeAllergensRemainUnknown=True,
                 rejectedJoins=[dict(food='기피편',source='조선예가 공식 기피편 제품 소개',
                                     reason='SPECIFIC_BRANDED_PRODUCT_IS_NOT_A_GENERIC_DISH_IDENTITY'),
                                dict(food='베지텐더 밸런스랩',source='CJ 공식 인접 담백아삭 베지랩 소개',
                                     reason='ADJACENT_PRODUCT_FALAFEL_CHICKPEA_FRYING_IS_NOT_THIS_PRODUCT')],
                 remainingUnresolvedReasons={reason:sum(r['reason']==reason for r in research if not r['ingredients'])
                    for reason in sorted({r['reason'] for r in research if not r['ingredients']})})
    (OUT/'summary.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(summary,ensure_ascii=False))

if __name__=='__main__':
    import argparse
    parser=argparse.ArgumentParser();parser.add_argument('--audit',action='store_true')
    audit() if parser.parse_args().audit else build()
