"""292 identity audit and conservative groups from observed ingredient fields.

Dictionary lookup applies to source ingredients only, never menu names. Recipe amounts are
used to suppress seasoning tags; they are never transferred to a food's kcal or serving.
"""
from collect_public_recipe_evidence import ROOT, OUT, CACHE, CHECKED, VERSION, read_csv, write_csv, canonical, recipe_name, plain
from collections import defaultdict
import csv, hashlib, html, json, re

VERSION = 'recommendation-composition-catalog-v3'

GROUPS = {
 'GRAIN_UNSPECIFIED':'쌀 멥쌀 찹쌀 쌀가루 찹쌀가루 멥쌀가루 떡 흰떡 가래떡 떡볶이떡 국수 면 밀가루 소면 칼국수 칼국수면 식빵 쌀밥 메밀가루 메밀',
 'REFINED_GRAIN':'백미 흰쌀 흰쌀가루 정백미',
 'WHOLE_GRAIN':'현미 통밀 통밀가루 통밀빵',
 'MIXED_GRAIN':'보리 보리쌀 기장 수수 차조 조 귀리 흑미 찰보리 차수수 찰수수 율무 잡곡',
 'LEGUME_SOY':'콩 흰콩 백태 대두 검은콩 검정콩 서리태 흑태 팥 붉은팥 적두 녹두 거피녹두 완두콩 두부 순두부 연두부 콩가루 녹두가루 청국장 강낭콩 밤콩 유부',
 'VEGETABLE':'채소 야채 배추 양배추 배춧잎 우거지 시래기 배추시래기 시금치 콩나물 숙주 숙주나물 애호박 늙은호박 호박 단호박 당근 양파 오이 무 무우 미나리 쑥갓 가지 도라지 더덕 취 취나물 고사리 토란대 곤드레 곤달비 부추 깻잎 상추 청경채 브로콜리 연근 우엉 토마토 파프리카 피망 풋고추 고추 꽈리고추 마늘쫑 고구마줄기 냉이 쑥 돌나물 참나물 버섯 표고버섯 느타리버섯 송이버섯 팽이버섯 목이버섯 생표고버섯 다시마 미역 김 미역줄기 매생이 배추김치 김치 열무김치',
 'ROOT_STARCH':'감자 고구마 토란 밤 도토리묵 메밀묵',
 'RED_MEAT':'쇠고기 소고기 돼지고기 쇠갈비 소갈비 갈비 사태 양지 양지머리 등심 돼지갈비 우둔살 쇠고기등심 소뼈 사골 도가니 꼬리 내장 돼지뼈 돼지등뼈 쇠머리 우족 우설 유통',
 'POULTRY':'닭 닭고기 닭가슴살 닭다리살 닭봉 오리 오리고기 꿩 꿩고기',
 'FISH':'생선 고등어 갈치 삼치 조기 가자미 동태 동태살 명태 생태 대구 대구포 북어 북어포 황태 미꾸라지 뱅어포 멸치 잔멸치 생멸치 임연수어 연어 참치 장어 붕장어 아귀 생아귀 복어 꽁치 민물고기 가오리 병어 명란 날치알 물메기',
 'SEAFOOD':'새우 건새우 마른새우 보리새우 새우살 오징어 낙지 주꾸미 쭈꾸미 갑오징어 문어 꽃게 게 전복 전복살 굴 바지락 홍합 조개 조갯살 대합 모시조개 백합 꼬막 해물 민물새우 우렁이 골뱅이',
 'EGG':'달걀 계란 달걀흰자 달걀노른자',
 'DAIRY':'우유 치즈 모차렐라치즈 리코타치즈 요구르트 요거트',
 'NUT_SEED':'잣 잣가루 호두 아몬드 땅콩 참깨 검은깨 흑임자 깨 들깨 들깨가루 깨가루 통깨',
 'FRUIT':'사과 배 귤 오렌지 바나나 딸기 포도 복숭아 망고 블루베리 자몽 크랜베리 아보카도 수박 참다래 키위',
 'PROCESSED_MEAT':'햄 베이컨 소시지 런천미트 런천미트통조림',
 'PROCESSED_FISH':'어묵',
 'MEAT_UNSPECIFIED':'고기',
}
DICTIONARY={canonical(word):group for group,words in GROUPS.items() for word in words.split()}
for token in '청피망 붉은고추 홍고추 청고추 붉은양배추 생미역 건미역 마른미역 얼갈이배추 양상추 양송이버섯 양송이 싸리버섯 건표고버섯 염장미역줄기 신배추김치 묵은김치 무청시래기 아욱 달래 두릅 열무 봄동 고춧잎 머위대 도라지채 총각무 유채나물 노각 마늘종'.split():
    DICTIONARY[token]='VEGETABLE'
DICTIONARY['가죽']='VEGETABLE'
DICTIONARY['거피팥']='LEGUME_SOY'
PROTEINS={'LEGUME_SOY','RED_MEAT','POULTRY','FISH','SEAFOOD','EGG','DAIRY','NUT_SEED','PROCESSED_MEAT'}
PANTRY={'물','쌀뜨물','멸치장국국물','육수','식용유','참기름','콩기름','들기름','소금','간장','국간장','진간장','설탕','물엿','꿀','고추장','된장','다진마늘','마늘','파','대파','쪽파','다진파','고춧가루','고추가루','후추','후춧가루','생강','다진생강','청주','맛술','식초','깨소금'}

def aliases(name):
    name=recipe_name(name)
    return {canonical(n) for n in [name]+re.split('[(),]',name) if n.strip()}

def chunks(text):
    text=re.sub(r'^재료량?\s*\([^)]*\)\s*-?\s*','',text)
    text=re.sub(r'<[가-힣\s]+>', '', text)
    return re.split(r',(?=(?:[^()]*\([^()]*\))*[^()]*$)',text)

def parse_ingredients(text):
    result=[]
    for chunk in chunks(text):
        chunk=chunk.strip()
        if not chunk:continue
        # Both "94.7g" and the explicitly printed "94.7(g)" declare grams.
        # A bare number still has no unit and remains unverified.
        mass=re.search(r'(\d+(?:\.\d+)?)\s*\(?\s*(kg|g)\b\s*\)?',chunk,re.I)
        # Ingredient name appears before first quantity; quantities in parentheses can precede g.
        name=re.split(r'\d',chunk,maxsplit=1)[0].strip()
        # Keep an explicit milling qualifier long enough to distinguish white from brown rice.
        rice_type='REFINED_GRAIN' if re.search(r'쌀\s*\(\s*백미',chunk) else 'WHOLE_GRAIN' if re.search(r'쌀\s*\(\s*현미',chunk) else None
        name=re.sub(r'\([^)]*\)','',name).strip()
        name=re.sub(r'^(불린|삶은|데친|다진|말린|손질한)\s*','',name)
        name=name.strip(' -<>:')
        amount=float(mass.group(1))*(1000 if mass.group(2).lower()=='kg' else 1) if mass else None
        norm=canonical(name)
        # Explicit cut/preparation qualifiers, not menu-name ingredient inference.
        if norm.startswith('쌀백미'): norm='백미'
        elif norm.startswith('쌀현미'): norm='현미'
        group=rice_type or DICTIONARY.get(norm)
        result.append(dict(name=name,group=group,grams=amount,pantry=norm in PANTRY,
                           quantityUnitUnverified=bool(re.search(r'\d',chunk)) and amount is None and not bool(re.search(r'\d\s*(?:개|모|컵|뿌리|쪽|장|마리|ml|mL|L|작은술|큰술)',chunk)),
                           uncertain=('약간' in chunk or '적량' in chunk) and amount is None,
                           smallSpoon=bool(re.search(r'작은술|큰술',chunk)) and amount is None))
    return result

def classify(text):
    ingredients=parse_ingredients(text)
    bygroup=defaultdict(float)
    for item in ingredients:
        if item['group'] and not item['pantry'] and item['grams'] is not None:
            bygroup[item['group']]+=item['grams']
    total=sum(bygroup.values())
    groups=set()
    for item in ingredients:
        group=item['group']
        if not group or item['pantry'] or item['uncertain']:continue
        if group in {'RED_MEAT','PROCESSED_MEAT','MEAT_UNSPECIFIED'}:
            groups.add(group);continue
        if group=='NUT_SEED' and item['smallSpoon']:continue
        amount=bygroup[group]
        # Technical seasoning filter: >=20g AND >=10% of classified solids in this recipe.
        # Not a nutrition recommendation or an assertion of a serving of vegetables.
        if item['grams'] is None or (amount>=20 and (total==0 or amount/total>=.10)):
            groups.add(group)
    return ingredients,groups

def composition_with_explicit_filling(row):
    """A source's labeled filling is food composition, not garnish or seasoning.

    Keep the original main/additional fields intact. Transfer only explicit 소/
    만두소 sections, ending before the next labeled section. This restores the
    420g peeled-red-bean filling in wind rice cake without adding a garnish group.
    """
    main=row['mainIngredientText'] or row['additionalIngredientText']
    if not row['mainIngredientText']:return main
    fillings=re.findall(r'<(?:만두)?소>\s*(.*?)(?=<[^>]+>|$)',row['additionalIngredientText'],re.S)
    return ', '.join([main]+[s.strip()for s in fillings if s.strip()])

def recipe_rank(row,menu_name):
    # All candidates already passed exact title / explicitly reviewed identity
    # matching. A unitless new sample must not displace an equally identified
    # source whose main ingredient quantities are actually declared.
    items=parse_ingredients(composition_with_explicit_filling(row))
    incomplete=any(i['quantityUnitUnverified']and i['group']and not i['pantry']for i in items)
    return (incomplete,row['recipeId'].startswith('RDA-DIET'),
            canonical(recipe_name(row['name']))!=canonical(menu_name),
            not row['recipeId'].startswith('RDA'),row['recipeId'])

def cook(row):
    if row.get('observedCookingStyle'):return row['observedCookingStyle']
    method=row.get('methodText','')
    field=row.get('cookingText','')
    if '튀기' in field or re.search(r'튀김기름|기름에.{0,30}튀|튀겨|튀긴다|튀긴 후',method):return 'DEEP_FRIED'
    if '수증기로 찌' in field:return 'STEAMED'
    if '굽는 음식' in field:return 'GRILLED'
    if '끓이는 음식' in field:return 'SIMMERED'
    if '기름을 이용' in field:return 'PAN_COOKED'
    if '가열하지 않는' in field:return 'UNHEATED'
    return 'UNKNOWN'

def positive_score(groups):
    weights={'WHOLE_GRAIN':4,'MIXED_GRAIN':3,'LEGUME_SOY':4,'VEGETABLE':3,'NUT_SEED':3,'FRUIT':3,'FISH':3,'SEAFOOD':2}
    return sum(weights.get(g,0) for g in groups)

def verdict(groups,cooking,negative):
    if 'REFERENCE_MAJOR_QUANTITY_UNIT_UNVERIFIED' in negative:
        return False,'SOURCE_MAJOR_QUANTITY_UNIT_UNVERIFIED'
    if cooking=='DEEP_FRIED':return False,'VERIFIED_DEEP_FRIED'
    if 'PROCESSED_MEAT' in groups or 'PROCESSED_MEAT' in negative:return False,'VERIFIED_PROCESSED_MEAT'
    if 'SUGAR_HEAVY_DESSERT' in negative or 'SWEETENED_BEVERAGE' in negative or 'FRUIT_JUICE' in negative:
        return False,'VERIFIED_SUGAR_OR_JUICE_MODERATION'
    score=positive_score(groups)-(2 if 'RED_MEAT' in groups or 'RED_MEAT_PRESENT' in negative else 0)
    return (score>=2,'ELIGIBLE' if score>=2 else 'VERIFIED_NO_PREFERRED_GROUP')

def generate():
    templates=read_csv(ROOT/'app/src/main/assets/fooddata/meal_templates.csv')
    foods={r['id']:r for r in read_csv(ROOT/'app/src/main/assets/fooddata/food_items.csv')}
    linked=defaultdict(list)
    for row in read_csv(ROOT/'app/src/main/assets/fooddata/meal_template_ingredients.csv'):linked[row['mealTemplateId']].append(row['foodItemId'])
    baseline=read_csv(OUT/'verified-food-groups-baseline.csv')
    baseline={r['stableTemplateId']:r for r in baseline}
    recipes=read_csv(OUT/'official-public-recipe-facts.csv')
    followup_facts=OUT/'official-followup-public-recipe-facts.csv'
    if followup_facts.exists():recipes+=read_csv(followup_facts)
    brand_groups=OUT/'reviewed-followup-brand-groups.csv'
    brand_groups={r['stableTemplateId']:r for r in read_csv(brand_groups)} if brand_groups.exists() else {}
    reviewed_aliases=read_csv(OUT/'reviewed-recipe-identity-aliases.csv')
    aliases_by_id={r['stableTemplateId']:r for r in reviewed_aliases}
    reviewed_path=OUT/'reviewed-public-definition-ingredients.csv'
    reviewed=read_csv(reviewed_path) if reviewed_path.exists() else []
    bydefinition={r['stableTemplateId']:r for r in reviewed}
    catalog_path=ROOT/'data-source/catalog-recommendation/reviewed-group-evidence.csv'
    catalog={r['stableTemplateId']:r for r in read_csv(catalog_path)} if catalog_path.exists() else {}
    groups=[];audit=[]
    for t in templates:
        ids=linked[t['id']]; assert len(ids)==1
        food=foods[ids[0]]
        matches=[] if food['brand'] else [r for r in recipes if aliases(t['name']) & aliases(r['name'])]
        if not food['brand'] and t['id'] in aliases_by_id:
            alias=aliases_by_id[t['id']]
            selected_alias=next(r for r in recipes if r['recipeId']==alias['sourceRecipeId'])
            assert selected_alias['name']==alias['sourceRecipeName']
            if selected_alias not in matches:matches.append(selected_alias)
        # Verified source fields, exact title, stated alias, source identifier.
        matches.sort(key=lambda r:recipe_rank(r,t['name']))
        old=baseline.get(t['id']);definition=bydefinition.get(t['id'])
        selected=matches[0] if matches else None
        major=old.copy() if old else None
        if t['id'] in catalog:
            extra=catalog[t['id']]
            # Independent source identity is checked again at the final join.
            assert extra['foodItemId']==food['id'] and extra['sourceFoodCode']==food['sourceFoodCode']
            assert extra['originalFoodName']==food['name'] and extra['brand']==food['brand']
            assert extra['menuName']==t['name']
            major={key:value for key,value in extra.items() if key not in
                {'foodItemId','sourceFoodCode','originalFoodName','brand','identityMatchReason','ingredientText'}}
            # The reviewed alternate is the primary record. Keep its original
            # ingredient fields/hash together instead of using the older rank
            # winner's fields under the alternate source identifier.
            catalog_recipe=next((r for r in recipes if r['recipeId']==extra['evidenceRecipeId'] and
                                 r['sourceUrl']==extra['sourceUrl']),None)
            if catalog_recipe is not None:selected=catalog_recipe
        if not major and definition:major=definition.copy()
        if not major and t['id'] in brand_groups:major=brand_groups[t['id']].copy()
        if not major and selected:
            composition=composition_with_explicit_filling(selected)
            ingredients,gs=classify(composition)
            if ingredients and gs:
                protein=gs&PROTEINS
                grain='MIXED_GRAINS' if 'MIXED_GRAIN'in gs else 'WHOLE_GRAIN' if 'WHOLE_GRAIN'in gs else 'RICE_OR_FLOUR' if gs&{'REFINED_GRAIN','GRAIN_UNSPECIFIED'} else 'NONE'
                cooking=cook(selected)
                # Significant sugar in a dessert needs explicit review; never assume from menu name.
                major=dict(stableTemplateId=t['id'],menuName=t['name'],ingredients='|'.join(dict.fromkeys(i['name'] for i in ingredients if i['name'])),
                    foodGroups='|'.join(sorted(gs)),grainType=grain,proteinSources='|'.join(sorted(protein)) or 'UNKNOWN',
                    cookingStyle=cooking,sourceName=selected['sourceName'],sourceUrl=selected['sourceUrl'],
                    evidenceScope='PUBLIC_REFERENCE_RECIPE',verifiedAt=CHECKED,slowStyleEligible='false',
                    notes='공공 자료의 명시된 동일 음식명/다른 이름으로 연결한 참고 조리 구성; 다른 조리법·전체 원재료·알레르기를 확정하지 않음; 소량 양념 식품군은 집계 제외',
                    negativeSignals='REFERENCE_MAJOR_QUANTITY_UNIT_UNVERIFIED' if any(i['quantityUnitUnverified']and i['group']and not i['pantry']for i in ingredients) else '')
        if major:
            negative=set(major.get('negativeSignals','').split('|'))-{''}
            if t['id']=='kfind-dinner-convenience-1':
                negative.add('RED_MEAT_PRESENT')
                major['notes']='공식 주요 통밀빵·치킨·오이 구성 확인; 별도 알레르기 표기에 쇠고기도 확인돼 구성 점수 감점; 통밀 함량·전체 원재료·조리유는 미확인'
            if 'PROCESSED_MEAT' in major['foodGroups'].split('|'):negative.add('PROCESSED_MEAT')
            eligible,reason=verdict(set(major['foodGroups'].split('|')),major['cookingStyle'],negative)
            if not major['sourceUrl'].startswith('https://'):
                eligible,reason=False,'EXISTING_STYLE_HTTPS_SOURCE_MINIMUM_UNMET'
            major['slowStyleEligible']=str(eligible).lower()
            primary_recipe=selected if selected and selected['sourceUrl']==major['sourceUrl'] else None
            major.update(evidenceRecipeId=major.get('evidenceRecipeId','') or (primary_recipe['recipeId'] if primary_recipe else ''),
                ingredientStatus='REFERENCE_PARTIAL' if major['evidenceScope'].startswith('PUBLIC') else 'PARTIAL_DESCRIPTION',
                allergenStatus='UNKNOWN',negativeSignals='|'.join(sorted(negative)),parserVersion=VERSION,
                sourceSha256=major.get('sourceSha256','') or (primary_recipe['sourceSha256'] if primary_recipe else ''))
            groups.append(major)
        else:reason='OFFICIAL_PRODUCT_COMPOSITION_UNRESOLVED' if food['brand'] else 'NO_EXACT_PUBLIC_OR_OFFICIAL_INGREDIENT_EVIDENCE'
        primary_recipe=selected if major and selected and selected['sourceUrl']==major['sourceUrl'] else None
        identity_reason=catalog[t['id']]['identityMatchReason'] if t['id'] in catalog else aliases_by_id[t['id']]['matchReason'] if t['id'] in aliases_by_id and selected and selected['recipeId']==aliases_by_id[t['id']]['sourceRecipeId'] else 'SOURCE_TITLE_EXACT_OR_EXPLICIT_SYNONYM' if primary_recipe else 'REVIEWED_SOURCE_DEFINITION_OR_EXISTING_EVIDENCE' if major else 'NO_ACCEPTED_IDENTITY_JOIN'
        audit.append(dict(stableTemplateId=t['id'],menuName=t['name'],foodItemId=food['id'],sourceFoodCode=food['sourceFoodCode'],
            brand=food['brand'],researchStatus='SOURCED_PARTIAL_COMPOSITION' if major else 'UNRESOLVED_AFTER_CATALOGUE_SCAN',
            reason=reason,ingredients=major['ingredients'] if major else '',foodGroups=major['foodGroups'] if major else '',
            ingredientStatus=major['ingredientStatus'] if major else 'UNKNOWN',allergenStatus='UNKNOWN_REFERENCE_ONLY',
            mainIngredientText=primary_recipe['mainIngredientText'] if primary_recipe else '',additionalIngredientText=primary_recipe['additionalIngredientText'] if primary_recipe else '',
            evidenceRecipeId=major['evidenceRecipeId'] if major else '',alternativeReferenceIds='|'.join(r['recipeId'] for r in matches if not primary_recipe or r['recipeId']!=primary_recipe['recipeId']),identityMatchReason=identity_reason,
            # RDA's optional secondary citation can be blank in the original page. Keep that
            # field unchanged in recipe facts; identify the actual primary record here.
            sourceUrl=major['sourceUrl'] if major else '',sourceReference=(primary_recipe['sourceReference'] or
                f"{primary_recipe['sourceName']} ({primary_recipe['recipeId']})") if primary_recipe else major['sourceName'] if major else '',
            sourceSha256=major['sourceSha256'] if major else '',checkedAt=CHECKED,parserVersion=VERSION,
            scannedSources='RDA_3248_FULL_CATALOGUE|MFDS_PUBLIC_RECIPES_1_350|KTO_970_FULL_CATALOGUE|EXISTING_KFIND_FIELDS|RDA_PUBLIC_DIET_268_FULL_CATALOGUE|EXACT_OFFICIAL_BRAND_EVIDENCE|CATALOG_RECOMMENDATION_REVIEWED_OFFICIAL_SOURCES'))
    assert len(audit)==len({r['stableTemplateId'] for r in audit})==292
    fields=list(next(iter(baseline.values())))+['evidenceRecipeId','ingredientStatus','allergenStatus','negativeSignals','parserVersion','sourceSha256']
    write_csv(OUT/'verified-food-groups.csv',groups,fields)
    write_csv(OUT/'recommendation-ingredient-research.csv',audit,list(audit[0]))
    strict_comparator=[r for r in groups if r['slowStyleEligible']=='true' and 'RED_MEAT' not in r['foodGroups'].split('|') and 'RED_MEAT_PRESENT' not in r['negativeSignals'].split('|')]
    original_evidence_with_current_policy=sum(r['slowStyleEligible']=='true' for r in groups if r['stableTemplateId'] in baseline)
    summary=dict(rows=292,sourcedGroups=len(groups),styleEligible=sum(r['slowStyleEligible']=='true' for r in groups),
        unresolved=sum(r['researchStatus'].startswith('UNRESOLVED') for r in audit),
        unresolvedReasons={reason:sum(r['reason']==reason for r in audit) for reason in sorted({r['reason'] for r in audit if r['researchStatus'].startswith('UNRESOLVED')})},
        priorReviewedSnapshot=dict(sourcedGroups=19,majorIngredientInformationKnown=51,styleEligible=16,snackEligible=2),
        original19EvidenceWithCurrentPolicy=original_evidence_with_current_policy,
        additionalCandidatesFromExpandedEvidenceUnderCurrentPolicy=sum(r['slowStyleEligible']=='true' for r in groups)-original_evidence_with_current_policy,
        sameExpandedEvidenceWithBlanketRedMeatExclusion=len(strict_comparator),
        extraCandidatesFromRedMeatPenaltyInsteadOfBlanketExclusion=sum(r['slowStyleEligible']=='true' for r in groups)-len(strict_comparator),
        originalIngredientCompleteTagsUnchanged=True,referenceAllergenStatus='UNKNOWN',recipeFacts=len(recipes),
        primaryEvidence=dict(recipe=sum(r['evidenceScope']=='PUBLIC_REFERENCE_RECIPE' for r in groups),
            definition=sum(r['evidenceScope']=='PUBLIC_DISH_DEFINITION' for r in groups),brand=sum(r['evidenceScope']=='BRAND_OFFICIAL_MAJOR_INGREDIENTS' for r in groups)),
        parserVersion=VERSION,checkedAt=CHECKED)
    (OUT/'ingredient-evidence-summary.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(summary,ensure_ascii=False))

if __name__=='__main__':generate()
