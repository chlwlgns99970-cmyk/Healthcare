"""State-aware generic food references and food-specific official portions.

Never select a cultivar, meat grade, sauce method, or unqualified piece weight.
US cups are deliberately unavailable to Korean recipes. Vague amounts remain None.
"""
import csv, hashlib, io, re, zipfile,json
from functools import lru_cache
from pathlib import Path
from lxml import html

ROOT=Path(__file__).resolve().parents[1]
ARCHIVE=ROOT/'app/build/food-quality-followup/household-source-cache/usda-sr-legacy.zip'
ARCHIVE_URL='https://fdc.nal.usda.gov/fdc-datasets/FoodData_Central_sr_legacy_food_csv_2018-04.zip'
KOREAN_MEASURE_URL='https://www.nongsaro.go.kr/portal/ps/psz/psza/contentSub.ps?cntntsNo=209500&menuId=PS65142'
US_MEASURE_URL='https://fsistraining.fsis.usda.gov/pluginfile.php/18442/mod_resource/content/1/CFR-2021-title9-vol2-chapIII.pdf'

@lru_cache(maxsize=1)
def archive_digest():
    return hashlib.sha256(ARCHIVE.read_bytes()).hexdigest()

@lru_cache(maxsize=1)
def archive_tables():
    with zipfile.ZipFile(ARCHIVE) as z:
        def table(name):
            path=next(p for p in z.namelist() if p.endswith('/'+name+'.csv'))
            return list(csv.DictReader(io.StringIO(z.read(path).decode('utf-8-sig'))))
        return table('food'),table('food_nutrient'),table('food_portion')

@lru_cache(maxsize=1)
def additional_nutrients():
    foods,values,_=archive_tables()
    energy={r['fdc_id']:float(r['amount']) for r in values if r['nutrient_id']=='1008' and r['amount']!=''}
    digest=archive_digest()
    result={r['fdc_id']:dict(code=r['fdc_id'],name=r['description'],energyKcal=energy[r['fdc_id']],
        sourceUrl=f"https://fdc.nal.usda.gov/food-details/{r['fdc_id']}/nutrients",sha256=digest,
        archiveUrl=ARCHIVE_URL,checkedAt='2026-10-05',referenceAmount=100,unit='g')
        for r in foods if r['fdc_id'] in energy}
    fallback=json.loads((ROOT/'data-source/recipe-linkage-maximization/kfind-generic-fallbacks.json').read_text(encoding='utf-8'))
    for key,r in fallback['accepted'].items():
        code=r['식품코드']
        result[code]=dict(code=code,name=r['식품명'],energyKcal=float(r['에너지(kcal)']),
            sourceUrl=fallback['sourceUrl'],sha256=fallback['sourceSha256'],checkedAt=fallback['checkedAt'],
            sourceIdentifier=r['품목제조보고번호'],worksheetRow=r['worksheetRow'],referenceAmount=100,unit='g',
            foodSubgroup=r['식품소분류명'],attribution=r['출처명'])
    return result

def context_for(recipe,raw):
    text=raw.read_text(encoding='utf-8')
    doc=html.fromstring(re.sub(r'<\?xml[^>]*\?>','',text))
    if recipe['recipeId'].startswith('MFDS-'):
        nodes=doc.xpath('//div[@class="direction"]//pre')
    elif recipe['recipeId'].startswith('RDA-DIET-'):
        nodes=doc.xpath('//strong[contains(.,"식단소개")]/parent::*')
        # Only the recipe's introduction/instructions; not unrelated diet tabs.
        nodes=nodes[1:2]
    else:
        nodes=doc.xpath('//tr[th[contains(.,"조리방법")]]/td')
    return re.sub(r'\s+',' ',' '.join(n.text_content() for n in nodes)).strip()

# Generic rows already accommodate unqualified varieties; never choose a named
# cultivar when a generic row exists. These names explicitly identify the input.
GENERIC_RAW={
    '현미':'A008000A060a','차조':'A037002A010a','수수':'A027000A010a','기장':'A003000A010a',
    '고구마':'B0060000000a','배추':'F0870000000a','배춧잎':'F0870000000a',
    '돼지고기(등심)':'I014000D110a','닭가슴살':'I008000D020a',
    '사과':'H0500000000a','배':'H0380000000a','노각':'F1480030000a',
    '노각(늙은오이)':'F1480030000a','메밀가루':'A004000A015a',
}
DRIED={'건표고버섯':'168436','건표고':'168436'}

def resolve_identity(key,context,recipe,nutrients):
    if recipe.get('recipeId')=='RDA-DIET-89329-4' and key=='다진파' and '다진 대파' in context:
        return 'F1910040000a','동일 공식 애호박볶음 조리법에서 원재료 다진 파를 다진 대파로 명시. 해당 recipe에만 적용하며 다른 파·쪽파에는 확대하지 않음'
    generic=[n for n in nutrients.values() if n.get('sourceIdentifier') and n['name']==key]
    if len(generic)==1:
        return generic[0]['code'],'K-FIND 정확한 일반 식품명·동일 식품군·100g 분석 행. 제조사·수입사 없는 단일 공식 분석만 사용; 소매 제품·복수 분석·동명 캔디류 제외'
    code=DRIED.get(key)
    if code:
        return code,'원문 건표고: 건조 상태 명시. RDA 재배 방식 선택을 피하고 USDA 일반 건조 shiitake 행으로 공식 fallback'
    code=GENERIC_RAW.get(key)
    if code and context:
        assert code in nutrients
        # The ingredient is an input subsequently prepared by the instructions.
        if any(word in context for word in ('삶은 '+key,'익힌 '+key,'찐 '+key)):
            return None,None
        return code,'원문 재료의 곡종·부위와 후속 조리 지시를 대조한 RDA 일반 생재료 행. 품종·등급별 행을 임의 선택하지 않음'
    if key=='쌀밥':
        return 'A013000A039a','원문 쌀밥은 조리 완료 백미 밥: RDA 일반 백미 밥 행 (생쌀로 대체하지 않음)'
    if key=='표고버섯' and re.search(r'표고버섯[^.]{0,65}(?:물에\s*불|불려|불린다)',context):
        return '168436','조리 원문에서 표고버섯을 물에 불리는 후속 단계 확인: 최초 재료량의 건조 shiitake 공식 행 (불린 완성 중량으로 대체하지 않음)'
    return None,None

# Same generic identity, not similar foods. No perilla/soybean substitution.
PORTION_IDS={'참기름':'171016','설탕':'169655','소금':'173468','꿀':'169640',
             '콩기름':'171411','대두유':'171411','올리브유':'171413',
             '올리브오일':'171413','다진마늘':'169230','다진생강':'169231'}

def portion_conversion(key,ingredient,identity):
    quantity=ingredient['quantity'];unit=ingredient['unit']
    if quantity is None or quantity<=0 or ingredient.get('quantityRange'):
        return None
    # Official food portion data define each ingredient's gram weight separately.
    # Both Korean and USDA nutrition household spoons define 5/15 mL; no cups,
    # vague amounts, unqualified fruit/pieces or liquid-density extrapolation.
    fdc=PORTION_IDS.get(key)
    if not identity or not fdc or unit not in ('작은술','큰술'):
        return None
    _,_,portions=archive_tables()
    matches=[p for p in portions if p['fdc_id']==fdc and p['modifier'] in
             (('tsp','teaspoon') if unit=='작은술' else ('tbsp','tablespoon'))]
    if not matches:return None
    weights={float(p['gram_weight'])/float(p['amount']) for p in matches if float(p['amount'])>0}
    if len(weights)!=1:return None
    mass=weights.pop()
    return dict(grams=quantity*mass,gramsPerUnit=mass,fdcId=fdc,
        sourceUrl=f'https://fdc.nal.usda.gov/food-details/{fdc}/nutrients',
        archiveSha256=archive_digest(),
        portionRows=matches,measureDefinitionSources=[KOREAN_MEASURE_URL,US_MEASURE_URL],
        checkedAt='2026-10-05',reason='USDA 동일 일반 재료의 공식 portion 중량: 작은술 5mL/큰술 15mL 계량스푼 참고 환산. 컵·약간·개수에는 적용하지 않음')
