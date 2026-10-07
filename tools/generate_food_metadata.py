"""Deterministic exact-ID evidence sidecar and linked food-quality audit.

Raw source classification is distinct from verified dish composition. Reference
recipes never certify a restaurant's full ingredients, allergens or nutrition.
Nutrition/portion assets are read-only here; conflicts are preserved, not averaged.
"""
from pathlib import Path
import argparse, csv, hashlib, json, re, unicodedata

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT/'app/src/main/assets/fooddata'
SOURCES = ROOT/'data-source/food-quality'
VERSION = 'linked-food-metadata-v2'
DATE = '2026-10-04'
MFDS = 'https://various.foodsafetykorea.go.kr/nutrient/general/down/list.do'
HEADERS = ('foodItemId','sourceType','sourceFoodCode','name','normalizedName','brand','rawClassification',
 'manufacturer','productReportNumber','packageSize','intakeReference','ingredients','ingredientText',
 'ingredientStatus','allergens','allergenStatus','mayContainAllergens','foodGroups','foodGroupEvidenceScope',
 'sourceReference','sourceName','checkedAt','sourceDate','parserVersion','identityEvidence','staleCandidate',
 'allergenText','crossContactText','completeIngredientText','householdUnit','basisAmountPerUnit','basisUnit',
 'servingSourceReference','servingEvidenceKind','servingSourceSize',
 'recommendationReferenceAmount','recommendationReferenceUnit','recommendationSourceReference','menuCategory',
 'sourceStatus','availabilityStatus','sourceVersion','sourceHash')

def read(path):
    if not path.exists(): return []
    with path.open(encoding='utf-8-sig',newline='') as f: return list(csv.DictReader(f))
def clean(value): return ' '.join(str(value or '').split())
def meaningful(value): return clean(value) not in ('','-','해당없음','해당 없음','없음','UNKNOWN')
def tokens(value): return {clean(t) for t in re.split(r'[|]', value or '') if meaningful(t)}
def token_field(values): return '|'.join(sorted(values))
def normalize(value): return re.sub(r'[^a-z0-9가-힣]','',unicodedata.normalize('NFKC',value).lower())

def allergen_tokens(text):
    """Canonical positive evidence only; never infer absence or ingredients from a name."""
    words = set(re.findall(r'[가-힣A-Za-z]+', text or ''))
    groups = {'달걀':{'난류','알류','계란','달걀','전란','난백','난황'}, '우유':{'우유','유제품'},
      '밀':{'밀','밀가루'}, '대두':{'대두'}, '땅콩':{'땅콩'},
      '견과류':{'견과류','견과','호두','잣','아몬드','캐슈넛','피스타치오','마카다미아'},
      '새우':{'새우'},'게':{'게'},'생선':{'생선','고등어','참치','연어','명태','대구'},
      '조개류':{'조개류','조개','홍합','굴','전복'}}
    return {key for key, values in groups.items() if words & values}

def ingredient_tokens(text):
    # Preserve original amounts/details; no composite-ingredient expansion is inferred.
    return {clean(part) for part in re.split(r'[|,;\n]', text or '') if meaningful(part)}

def normalize_source_date(value):
    value = clean(value)
    if re.fullmatch(r'\d{5}(?:\.0)?',value):
        from datetime import datetime,timedelta
        return (datetime(1899,12,30)+timedelta(days=float(value))).date().isoformat()
    return value

def base_metadata(food, raw=None):
    raw=raw or {}
    source_type=food['sourceType']
    source = MFDS if source_type.startswith('K-FIND') else food.get('servingDescription','')
    if source_type == 'OFFICIAL-BRAND-NUTRITION':
        source = source_type+':'+food['sourceFoodCode']
    if source_type == 'USDA-SR-LEGACY':
        source='https://fdc.nal.usda.gov/food-details/'+food['sourceFoodCode'].replace('FDC-','')+'/nutrients'
    return dict.fromkeys(HEADERS,'') | {
      'foodItemId':food['id'],'sourceType':source_type,'sourceFoodCode':food['sourceFoodCode'],
      'name':food['name'],'normalizedName':food['normalizedName'],'brand':food.get('brand',''),
      'rawClassification':' | '.join(dict.fromkeys(clean(raw.get(k)) for k in
          ('식품기원명','식품대분류명','대표식품명','식품중분류명','식품소분류명','식품세분류명') if meaningful(raw.get(k)))),
      'manufacturer':clean(raw.get('제조사명') or raw.get('업체명')) if meaningful(raw.get('제조사명') or raw.get('업체명')) else '',
      'productReportNumber':clean(raw.get('품목제조보고번호')),
      'packageSize':clean(raw.get('식품중량')),'intakeReference':clean(raw.get('1回 섭취참고량') or raw.get('1회 섭취참고량') or raw.get('1인(회)분량 참고량')),
      'ingredientStatus':'UNKNOWN','allergenStatus':'UNKNOWN','sourceReference':source or source_type+':'+food['sourceFoodCode'],
      'sourceName':clean(raw.get('출처명')) or ('USDA FoodData Central' if source_type.startswith('USDA')
          else '브랜드 공식 메뉴 정보' if source_type=='BRAND_OFFICIAL_MENU' else '브랜드 공식 영양정보'),
      'checkedAt':DATE,'sourceDate':normalize_source_date(raw.get('데이터기준일자') or
          (food.get('dataVersion') if source_type!='OFFICIAL-BRAND-NUTRITION' else '')),
      'parserVersion':VERSION,'identityEvidence':source_type+':'+food['sourceFoodCode'],'staleCandidate':'false'}

def apply_evidence(target, evidence):
    if not meaningful(target.get('rawClassification')) and meaningful(evidence.get('sourceCategory')):
        target['rawClassification']=clean(evidence['sourceCategory'])
    for key in ('rawClassification','manufacturer','productReportNumber','packageSize','intakeReference',
                'sourceStatus','availabilityStatus','sourceVersion','sourceHash'):
        if meaningful(evidence.get(key)):
            target[key]=clean(evidence[key])
    if meaningful(evidence.get('menuCategory')):
        target['menuCategory']=clean(evidence['menuCategory'])
    status=evidence.get('ingredientStatus','')
    complete=target.get('ingredientStatus')=='COMPLETE_DECLARATION'
    for key in ('ingredients','allergens','mayContainAllergens','foodGroups'):
        if key=='ingredients' and status=='COMPLETE_DECLARATION':
            target[key]=token_field(tokens(evidence.get(key)))
        elif key=='ingredients' and complete:
            continue
        else:
            target[key]=token_field(tokens(target.get(key)) | tokens(evidence.get(key)))
    if meaningful(evidence.get('ingredientText')) and (not complete or status=='COMPLETE_DECLARATION'):
        target['ingredientText']=clean(evidence['ingredientText'])
    if status=='COMPLETE_DECLARATION':
        target['completeIngredientText']=clean(evidence.get('completeIngredientText') or evidence.get('ingredientText'))
    if status and status!='UNKNOWN' and (not complete or status=='COMPLETE_DECLARATION'):
        target['ingredientStatus']=status if status == 'COMPLETE_DECLARATION' else 'PARTIAL_DESCRIPTION'
    for key in ('allergenText','crossContactText'):
        incoming=clean(evidence.get(key))
        if incoming and incoming not in target.get(key,''):
            target[key]=' | '.join(filter(None,(target.get(key,''),incoming)))
    if evidence.get('householdUnit'):
        amount=float(evidence['basisAmountPerUnit'])
        import math
        assert math.isfinite(amount) and amount>0 and evidence.get('basisUnit') in ('g','ml')
        assert evidence.get('servingSourceReference','').startswith(('https://','http://'))
        assert evidence.get('servingEvidenceKind') in ('OFFICIAL_SERVING','VERIFIED_CONVERSION')
        assert meaningful(evidence.get('servingSourceSize'))
        for key in ('householdUnit','basisAmountPerUnit','basisUnit','servingSourceReference','servingEvidenceKind','servingSourceSize'):
            target[key]=clean(evidence.get(key))
    if meaningful(evidence.get('recommendationReferenceAmount')):
        import math
        amount=float(evidence['recommendationReferenceAmount'])
        assert math.isfinite(amount) and amount>0
        assert evidence.get('recommendationReferenceUnit') in ('g','ml')
        assert evidence.get('recommendationSourceReference','').startswith('https://')
        for key in ('recommendationReferenceAmount','recommendationReferenceUnit','recommendationSourceReference'):
            target[key]=clean(evidence.get(key))
    allergen_status=evidence.get('allergenStatus','')
    if allergen_status and allergen_status!='UNKNOWN':
        target['allergenStatus']=allergen_status
    refs=tokens(target.get('sourceReference')) | tokens(evidence.get('sourceUrl') or evidence.get('sourceReference'))
    target['sourceReference']=token_field(refs)
    for key in ('sourceName','sourceDate','checkedAt','parserVersion','foodGroupEvidenceScope','staleCandidate'):
        if evidence.get(key): target[key]=clean(evidence[key])
    target['identityEvidence']=token_field(tokens(target['identityEvidence']) | {clean(evidence.get('evidenceKind') or 'EXACT_ID_EVIDENCE')})

def build(foods, raw_rows, franchise_evidence, groups, verified_ingredients, links, menus):
    raw={r['foodItemId']:r for r in raw_rows}
    output={f['id']:base_metadata(f,raw.get(f['id'])) for f in foods}
    conflicts=[]
    for menu in menus:
        if menu['id'] not in output:
            output[menu['id']]=base_metadata({'id':menu['id'],'sourceType':'BRAND_OFFICIAL_MENU','sourceFoodCode':menu.get('sourceFoodCode') or menu['id'],
                 'name':menu.get('name',''),'normalizedName':normalize(menu.get('name','')),'brand':menu.get('brand',''),
                 'servingDescription':menu.get('sourceUrl',''),'dataVersion':menu.get('sourceDate','')})
    declarations={}
    conflicted_declarations=set()
    for row in franchise_evidence:
        identity=row['foodItemId']
        if identity not in output:
            conflicts.append({'id':identity,'reason':'NO_EXACT_BUNDLED_MENU_ID'}); continue
        target=output[identity]
        if row.get('sourceFoodCode') and row['sourceFoodCode']!=target['sourceFoodCode']:
            conflicts.append({'id':identity,'reason':'SOURCE_FOOD_CODE_CONFLICT'}); continue
        if row.get('brand') and normalize(row['brand'])!=normalize(target['brand']):
            conflicts.append({'id':identity,'reason':'BRAND_IDENTITY_CONFLICT'}); continue
        if row.get('name') and normalize(row['name'])!=normalize(target['name']):
            conflicts.append({'id':identity,'reason':'MENU_NAME_IDENTITY_CONFLICT'}); continue
        if row.get('allergenStatus')=='CONFIRMED_LABEL':
            declaration=(tokens(row.get('allergens')),tokens(row.get('mayContainAllergens')))
            if identity in declarations and declarations[identity]!=declaration:
                conflicts.append({'id':identity,'reason':'OFFICIAL_DECLARATION_CONFLICT','sourceUrl':row.get('sourceUrl','')})
                # Preserve positive evidence but never certify no intersection from conflicting labels.
                conflicted_declarations.add(identity)
            if identity in conflicted_declarations:
                row=row|{'allergenStatus':'PARTIAL_CONFLICT'}
            declarations[identity]=declaration
        apply_evidence(target,row)
    link_map={r['mealTemplateId']:r['foodItemId'] for r in links}
    for row in groups:
        food_id=link_map.get(row['stableTemplateId'])
        if food_id not in output: continue
        apply_evidence(output[food_id],{'ingredients':row['ingredients'],'foodGroups':row['foodGroups'],
           'ingredientStatus':'PARTIAL_DESCRIPTION','sourceUrl':row['sourceUrl'],'sourceName':row['sourceName'],
           'checkedAt':row.get('verifiedAt',DATE),'evidenceKind':row.get('evidenceScope','PUBLIC_REFERENCE_RECIPE'),
           'foodGroupEvidenceScope':'REFERENCE_RECIPE_COMPOSITION'})
    for row in verified_ingredients:
        food_id=link_map.get(row['stableTemplateId'])
        if food_id not in output: continue
        allergen_status='CONFIRMED_LABEL' if row['allergenCompleteness']=='COMPLETE' else 'PARTIAL_INGREDIENT_EVIDENCE'
        if allergen_status=='CONFIRMED_LABEL':
            declaration=(tokens(row['allergenTags']),set())
            if food_id in declarations and declarations[food_id]!=declaration:
                conflicts.append({'id':food_id,'reason':'OFFICIAL_DECLARATION_CONFLICT',
                    'sourceUrl':row['sourceUrlOrIdentifier']})
                conflicted_declarations.add(food_id)
            declarations[food_id]=declaration
            if food_id in conflicted_declarations: allergen_status='PARTIAL_CONFLICT'
        apply_evidence(output[food_id],{'ingredients':row['ingredients'],'ingredientStatus':'PARTIAL_DESCRIPTION',
           'allergens':row['allergenTags'],'allergenStatus':allergen_status,
           'sourceUrl':row['sourceUrlOrIdentifier'],'sourceName':row['sourceName'],'checkedAt':row['verifiedAt'],
           'evidenceKind':'EXACT_PRODUCT_OFFICIAL_DESCRIPTION'})
    # Single-food USDA source identities have observed components, not generic name joins.
    natural={'171688':('사과','FRUIT'),'173944':('바나나','FRUIT'),'167762':('딸기','FRUIT'),
       '174683':('포도','FRUIT'),'169097':('오렌지','FRUIT'),'167765':('수박','FRUIT'),
       '169928':('복숭아','FRUIT'),'168177':('배','FRUIT'),'169975':('양배추','VEGETABLE'),
       '169249':('상추','VEGETABLE'),'170457':('토마토','VEGETABLE'),'168409':('오이','VEGETABLE'),
       '172475':('두부','LEGUME_SOY'),'173424':('달걀','EGG')}
    for f in foods:
        code=f['sourceFoodCode'].replace('FDC-','')
        if f['sourceType']=='USDA-SR-LEGACY' and code in natural:
            name,group=natural[code]
            apply_evidence(output[f['id']],{'ingredients':name,'ingredientStatus':'PARTIAL_DESCRIPTION','foodGroups':group,
              'foodGroupEvidenceScope':'EXACT_USDA_SINGLE_FOOD','evidenceKind':'USDA_EXACT_FDC_ID',
              'allergens':'달걀' if group=='EGG' else '대두' if group=='LEGUME_SOY' else '',
              'allergenStatus':'PARTIAL_INGREDIENT_EVIDENCE' if group in ('EGG','LEGUME_SOY') else 'UNKNOWN'})
    # Evidence from another pass cannot silently resolve contradictory official labels.
    for identity in conflicted_declarations:
        output[identity]['allergenStatus']='PARTIAL_CONFLICT'
    return sorted(output.values(),key=lambda r:r['foodItemId']),conflicts

def metrics(foods,metadata):
    evidence={r['foodItemId']:r for r in metadata}
    def has_number(value):
        try:
            import math
            return math.isfinite(float(value)) and float(value)>=0
        except (ValueError,TypeError): return False
    return {'totalFoods':len(foods),'kcal':sum(has_number(f['energyKcal']) for f in foods),
      'macroComplete':sum(all(has_number(f[k]) for k in ('carbohydrateGrams','proteinGrams','fatGrams')) for f in foods),
      'officialServing':sum('공식 총내용량' in f['servingDescription'] or bool(re.search(r'공식\s*(?:(?:HOT|ICE)\s+)?(?:제공량\s*)?[0-9.]+\s*(개|줄|봉|병|캔|팩|컵|잔|조각|인분)\s*[0-9.]+\s*(g|ml)',f['servingDescription'],re.I)) for f in foods),
      'householdUnitEvidence':sum('포장단위 ' in f['servingDescription'] or bool(re.search(r'(공식|확인된|검증된)\s*[0-9.]+\s*(개|줄|공기|장|조각|봉|병|캔|팩|잔|인분)',f['servingDescription'])) or f['sourceFoodCode'] in ('FDC-173424','FDC-173944') for f in foods),
      'ingredientsKnownPartialOrComplete':sum(bool(evidence.get(f['id'],{}).get('ingredients')) for f in foods),
      'ingredientsCompleteDeclaration':sum(evidence.get(f['id'],{}).get('ingredientStatus')=='COMPLETE_DECLARATION' for f in foods),
      'allergenEvidenceKnown':sum(evidence.get(f['id'],{}).get('allergenStatus','UNKNOWN')!='UNKNOWN' for f in foods),
      'allergenCompleteDeclaration':sum(evidence.get(f['id'],{}).get('allergenStatus')=='CONFIRMED_LABEL' for f in foods),
      'foodGroupsVerified':sum(bool(evidence.get(f['id'],{}).get('foodGroups')) for f in foods),
      'provenanceLinked':sum(bool(evidence.get(f['id'],{}).get('sourceReference') and evidence.get(f['id'],{}).get('checkedAt')) for f in foods)}

def generated_manifest(metadata_content, identity_count):
    path=ASSETS/'food_data_manifest.properties'
    values=dict(line.split('=',1) for line in path.read_text(encoding='utf-8-sig').splitlines() if '=' in line)
    names=('food_items.csv','product_items.csv','franchise_official_items.csv','meal_templates.csv','meal_template_ingredients.csv')
    bundle_hash=hashlib.sha256(b''.join((ASSETS/name).read_bytes() for name in names)+metadata_content).hexdigest().upper()
    counts={name:len(read(ASSETS/name)) for name in names}
    values.update(bundleId='FOOD-QUALITY-'+DATE+'-'+bundle_hash[:12],
        foodCount=str(counts['food_items.csv']), productFoodCount=str(counts['product_items.csv']),
        officialFranchiseFoodCount=str(counts['franchise_official_items.csv']),
        totalFoodCount=str(sum(counts[name] for name in names[:3])),
        templateCount=str(counts['meal_templates.csv']), ingredientCount=str(counts['meal_template_ingredients.csv']),
        productAssetSha256=hashlib.sha256((ASSETS/'product_items.csv').read_bytes()).hexdigest().upper(),
        officialFranchiseAssetSha256=hashlib.sha256((ASSETS/'franchise_official_items.csv').read_bytes()).hexdigest().upper(),
        metadataAssetSha256=hashlib.sha256(metadata_content).hexdigest().upper(),
        metadataCount=str(identity_count), metadataParserVersion=VERSION)
    return ('\n'.join(key+'='+value for key,value in values.items())+'\n').encode('utf-8')

def main():
    parser=argparse.ArgumentParser(); parser.add_argument('--check',action='store_true'); args=parser.parse_args()
    foods=sum((read(ASSETS/name) for name in ('food_items.csv','product_items.csv','franchise_official_items.csv')),[])
    menus=[]
    for name in ('official-menu-snapshot.csv','additional-menu-snapshot.csv','quality-menu-snapshot.csv','legacy-menu-snapshot.csv','delivery-menu-snapshot.csv'):
        for row in read(ROOT/'data-source/franchise'/name):
            # Imported snapshots use menuId or id; exact IDs must agree with generated catalog.
            identity=row.get('id') or row.get('menuId')
            if identity: menus.append(row|{'id':identity})
    # Producer evidence carries catalog identity even if its source snapshot uses a different header.
    franchise=read(ROOT/'data-source/franchise/food-metadata-evidence.csv')+read(ROOT/'data-source/franchise/food-metadata-coffee-bakery.csv')
    franchise+=read(ROOT/'data-source/franchise/food-metadata-followup.csv')
    franchise+=read(ROOT/'data-source/franchise/delivery-metadata-evidence.csv')
    franchise+=read(ROOT/'data-source/catalog-retail/evidence.csv')
    franchise+=read(ROOT/'data-source/catalog-recommendation/portion-evidence.csv')
    franchise+=read(ROOT/'data-source/catalog-recommendation/metadata-evidence.csv')
    franchise+=read(SOURCES/'cafe-targeted-evidence.csv')+read(SOURCES/'product-targeted-evidence.csv')
    for name in ('followup-household-evidence.csv','followup-manufacturer-evidence.csv','followup-franchise-evidence.csv','followup-recommendation-portion-evidence.csv'):
        franchise+=read(SOURCES/name)
    food_by_id={food['id']:food for food in foods}
    nutrition_provenance=[]
    for name in ('salady-nutrition-2026-09.csv','sinjeon-nutrition-2018-11.csv','quality-nutrition.csv','delivery-nutrition.csv'):
        for row in read(ROOT/'data-source/franchise'/name):
            if row['id'] not in food_by_id: continue
            nutrition_provenance.append({'foodItemId':row['id'],'sourceFoodCode':food_by_id[row['id']]['sourceFoodCode'],
                'brand':row['brand'],'name':row['name'],'sourceUrl':row['sourceUrl'],'sourceDate':row['sourceDate'],
                'checkedAt':row['verifiedAt'],'parserVersion':'official-nutrition-provenance-v1',
                'evidenceKind':'EXACT_OFFICIAL_NUTRITION_ID','staleCandidate':
                'true' if row['saleState']=='LEGACY_OFFICIAL_NUTRITION' else 'false'})
    franchise=nutrition_provenance+franchise
    by_menu={m['id']:m for m in menus}
    for e in franchise:
        if e['foodItemId'] not in by_menu: menus.append(e|{'id':e['foodItemId']})
    result,conflicts=build(foods,read(SOURCES/'raw-identity-fields.csv')+read(ROOT/'data-source/catalog-retail/raw-identity-fields.csv'),franchise,
        read(ROOT/'data-source/recommendation/verified-food-groups.csv'),
        read(ROOT/'data-source/recommendation/official-ingredient-verification.csv'),read(ASSETS/'meal_template_ingredients.csv'),menus)
    # Hash-bound menu-only source rows carry order-service scope and sales limits.
    # They have no FoodItem nutrition and must survive sidecar regeneration.
    order_metadata=read(ROOT/'data-source/full-adjudication/nolboo-order-metadata.csv')
    if order_metadata:
        assert all(r['foodItemId'].startswith('official-menu-nolboo-order-') for r in order_metadata)
        order_ids={r['foodItemId'] for r in order_metadata}
        result=[r for r in result if r['foodItemId'] not in order_ids]+order_metadata
        result.sort(key=lambda r:r['foodItemId'])
    import io
    stream=io.StringIO(newline=''); writer=csv.DictWriter(stream,fieldnames=HEADERS,lineterminator='\n',extrasaction='ignore')
    writer.writeheader(); writer.writerows({k:clean(v) for k,v in row.items()} for row in result)
    content=stream.getvalue().encode('utf-8'); output=ASSETS/'food_metadata.csv'
    manifest_content=generated_manifest(content,len(result))
    if args.check:
        assert output.read_bytes()==content,'Generated evidence differs'
        assert (ASSETS/'food_data_manifest.properties').read_bytes()==manifest_content,'Generated manifest differs'
        print('Deterministic metadata reproduction PASS'); return
    output.write_bytes(content)
    (ASSETS/'food_data_manifest.properties').write_bytes(manifest_content)
    # Complete per-identity audit connects nutrition/serving assets with the evidence sidecar.
    audit_dir=ROOT/'app/build/data-quality-qa'; audit_dir.mkdir(parents=True,exist_ok=True)
    by_id={f['id']:f for f in foods}
    audit_headers=(*HEADERS,'category','kcal','carbs','protein','fat','nutritionBasis','referenceAmount','referenceUnit','servingDescription','nutritionAvailable')
    with (audit_dir/'food-identity-audit.csv').open('w',encoding='utf-8',newline='') as f:
        writer=csv.DictWriter(f,fieldnames=audit_headers,lineterminator='\n')
        writer.writeheader()
        for row in result:
            food=by_id.get(row['foodItemId'],{})
            writer.writerow(row|{'category':food.get('category',''),'kcal':food.get('energyKcal',''),'carbs':food.get('carbohydrateGrams',''),
              'protein':food.get('proteinGrams',''),'fat':food.get('fatGrams',''),
              'nutritionBasis':food.get('referenceAmount','')+food.get('unit',''),
              'referenceAmount':food.get('referenceAmount',''),'referenceUnit':food.get('unit',''),
              'servingDescription':food.get('servingDescription',''),'nutritionAvailable':str(bool(food)).lower()})
    SOURCES.mkdir(exist_ok=True)
    baseline_dir=ROOT/'app/build/data-quality-qa'
    frozen_baseline=SOURCES/'quality-baseline.json'
    if frozen_baseline.exists():
        before_metrics=json.loads(frozen_baseline.read_text(encoding='utf-8'))['foodMetrics']
    else:
        baseline_foods=sum((read(baseline_dir/('before-'+name)) for name in ('food_items.csv','product_items.csv','franchise_official_items.csv')),[])
        assert len(baseline_foods)==31680,'Captured task baseline is required on first generation'
        baseline_meta,_=build(baseline_foods,read(SOURCES/'raw-identity-fields.csv'),[],
            read(baseline_dir/'before-verified-food-groups.csv'),read(ROOT/'data-source/recommendation/official-ingredient-verification.csv'),
            read(baseline_dir/'before-meal_template_ingredients.csv'),[])
        before_metrics=metrics(baseline_foods,baseline_meta)
    report={'parserVersion':VERSION,'checkedAt':DATE,'before':before_metrics,'after':metrics(foods,result),
        'metadataIdentities':len(result),'joinConflicts':conflicts,
        'allFoodAndMenuEvidence':{
          'ingredientKnown':sum(bool(r['ingredients']) for r in result),
          'allergenKnown':sum(r['allergenStatus']!='UNKNOWN' for r in result),
          'allergenCompleteLabel':sum(r['allergenStatus']=='CONFIRMED_LABEL' for r in result),
          'foodGroupKnown':sum(bool(r['foodGroups']) for r in result)},
        'sidecarSha256':hashlib.sha256(content).hexdigest().upper(),
        'coveragePolicy':'Partial reviewed composition counts separately from full ingredient and allergen declarations; source classification does not certify recipe composition.',
        'unresolvedProductIngredients':{'exactReportNumberRestored':sum(bool(r['productReportNumber']) for r in result),
          'officialApi':'https://www.foodsafetykorea.go.kr/api/openApiInfo.do?svc_no=C002',
          'publicSearchProbe':'Official domestic-food search AJAX returns HTML instead of requested JSON, both normal and session requests; authenticated C002 API needs a service key. No value inferred.'}}
    (SOURCES/'food-quality-audit.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(report,ensure_ascii=False,indent=2))

if __name__=='__main__': main()
