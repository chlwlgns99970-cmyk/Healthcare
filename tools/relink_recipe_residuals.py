"""Retry only frozen unresolved inputs. Never rebuild the 2,072 linked decisions.

Official definitions resolve identity separately from quantities. Full official
datasets are indexed once locally; no sample API requests occur in this module.
"""
import collections,copy,hashlib,json,re
from pathlib import Path
from lxml import html
from adjudicate_recipe_inputs import ROOT,read,norm
from maximize_recipe_evidence import additional_nutrients,context_for,portion_conversion,archive_tables,archive_digest,KOREAN_MEASURE_URL,US_MEASURE_URL
from publish_adjudicated_recipe_references import publish

OUT=ROOT/'data-source/recipe-linkage-residual'
PRIOR=ROOT/'data-source/recipe-linkage-maximization'
EXCLUDED='EXCLUDED_NON_CALORIC_OR_PROCESS_ITEM'
DATE='2026-10-05'

def load(p):return json.loads(p.read_text(encoding='utf-8'))
def save(name,value):
    (OUT/name).write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
def key(d):return d['recipeId'],d['ingredientIndex']

def verified_definition(filename,required):
    p=OUT/'raw'/filename
    doc=html.fromstring(p.read_text(encoding='utf-8'))
    for element in doc.xpath('//script|//style'):element.drop_tree()
    text=re.sub(r'\s+',' ',doc.text_content())
    assert required in text,(filename,required)
    capture=next(c for c in load(OUT/'source-captures.json') if c.get('rawFile')==p.relative_to(ROOT).as_posix())
    assert capture['sha256']==hashlib.sha256(p.read_bytes()).hexdigest()
    return capture

def identity_rule(ingredient,context,definitions):
    k=norm(ingredient)
    if k=='통깨' and not re.search(r'(?:생|안\s*볶은|볶지\s*않은)\s*통깨',context):
        return '170151','국립국어원 통깨 정의로 볶은 상태, RDA 참깨 설명으로 종을 확인. 색상·품종을 선택하지 않는 USDA 일반 볶은 통참깨.',definitions
    if k=='생땅콩':
        return '172430','원문 생땅콩의 생 상태를 보존한 USDA 모든 종류 일반 생땅콩. 볶은 땅콩·가공 캔디·특정 품종으로 대체하지 않음',[]
    if k=='팥' and re.search(r'팥(?:은|을|도)?[^.]{0,100}(?:씻|불려|삶|붓)',context):
        return '173727','같은 공식 조리법에서 팥을 씻거나 불리거나 삶는 후속 단계 확인. 최초 재료량의 USDA 일반 성숙 생팥 (삶은 결과 중량 아님)',[]
    if k=='목이버섯' and re.search(r'목이버섯[^.]{0,70}불려',context):
        return 'G0080000001a','같은 조리 원문에서 목이버섯을 불리는 후속 단계 확인: 최초 마른 재료량의 RDA 일반 말린 목이버섯',[]
    if k=='미역' and re.search(r'미역[^.]{0,70}불린다',context):
        return 'L0130000001a','같은 원문에서 물에 불리는 후속 단계가 명시된 최초 마른 미역. 불린 중량으로 대체하지 않음',[]
    if k=='다시마' and re.search(r'다시마[^.]{0,50}불려',context):
        return 'L0050000001a','같은 원문에서 불리는 후속 단계가 명시된 최초 마른 다시마',[]
    if k=='청포묵' and '녹두묵은' in context:
        return 'D0050000009a','동일 공식 recipe 조리법에서 원재료 청포묵을 녹두묵으로 명시. 다른 전분 묵으로 확대하지 않음',[]
    if k=='녹두' and re.search(r'녹두를[^.]{0,35}씻어[^.]{0,25}삶',context):
        return '174256','동일 조리법에서 원재료 녹두 낟알을 씻어 삶는 후속 단계 명시. USDA 일반 성숙 생녹두; 녹두묵·새싹·삶은 완성 중량 아님',[]
    return None,None,[]

def additional_portion(d):
    if d['quantity'] is None or d['quantityRange'] or d['identityStatus']!='LINKED':return None
    # Modifier is part of the match: a chopped onion spoon is not a sliced
    # onion, a whole clove, or an unspecified cup.
    options={('계피가루','작은술'):('171320','tsp'),
             ('계피가루','큰술'):('171320','tbsp'),
             ('다진양파','큰술'):('170000','tbsp chopped')}
    option=options.get((norm(d['ingredient']),d['unit']))
    if not option:return None
    code,modifier=option
    rows=[p for p in archive_tables()[2] if p['fdc_id']==code and p['modifier']==modifier]
    weights={float(p['gram_weight'])/float(p['amount']) for p in rows if float(p['amount'])>0}
    if len(weights)!=1:return None
    mass=weights.pop()
    return dict(grams=d['quantity']*mass,gramsPerUnit=mass,fdcId=code,portionRows=rows,
        sourceUrl=f'https://fdc.nal.usda.gov/food-details/{code}/nutrients',archiveSha256=archive_digest(),
        measureDefinitionSources=[KOREAN_MEASURE_URL,US_MEASURE_URL],checkedAt=DATE,
        reason='USDA 동일 재료·동일 다짐 상태의 명시적 5/15mL 계량스푼 portion만 사용. 컵·개수·약간에는 적용하지 않음')

def build():
    baseline=load(OUT/'baseline-ingredient-decisions.json')
    assert len(baseline)==4258 and sum(d['status']=='LINKED' for d in baseline)==2072
    definitions=[verified_definition('official-whole-sesame-search.html','볶아서 빻지 아니한 통째로의 깨'),
                 verified_definition('rda-sesame-definition.html','참깨를 의미')]
    nutrients={r['code']:r for r in load(ROOT/'data-source/full-adjudication/rda-10.4-nutrients.json')}
    nutrients.update(additional_nutrients())
    recipes={r['recipeId']:r for p in (ROOT/'data-source/recommendation').glob('official*public-recipe-facts.csv') for r in read(p)}
    units=load(PRIOR/'official-unit-equivalences.json')
    unit_index={(norm(u['ingredient']),u['unit']):u for u in units}
    contexts={};decisions=[];attempts=[]
    # One local lexical index per source, queried only for residual ingredients.
    name_index=collections.defaultdict(list)
    for code,n in nutrients.items():name_index[norm(n['name'])].append(code)
    cookbook=load(OUT/'mfds-cookbook-pages.json')
    cookbook_index=[(p['page'],norm(p['text'])) for p in cookbook]
    for original in baseline:
        d=copy.deepcopy(original)
        if d['status'] in ('LINKED',EXCLUDED):
            decisions.append(d);continue
        rid=d['recipeId'];recipe=recipes[rid]
        if rid not in contexts:
            raw=ROOT/'app/build/food-quality-qa/recipe-source'/(rid.lower().replace('rda-diet-','nongsaro-diet-')+'.html')
            assert hashlib.sha256(raw.read_bytes()).hexdigest()==recipe['sourceSha256']
            contexts[rid]=context_for(recipe,raw)
        context=contexts[rid]
        identity,reason,evidence=identity_rule(d['ingredient'],context,definitions)
        if identity:
            assert identity in nutrients
            d.update(identityStatus='LINKED',identityReason=reason,nutrientId=identity,
                     nutritionProvenance=nutrients[identity],identityProvenance=evidence,
                     matchingMethod='EXACT_INGREDIENT_OFFICIAL_DEFINITION_AND_SAME_RECIPE_STATE')
        # Synonyms may share an existing reviewed unit, but never an arbitrary
        # size or an equivalence whose official evidence disagrees.
        k=norm(d['ingredient'])
        canonical={'계란':'달걀'}.get(k,k)
        u=unit_index.get((canonical,d['unit']))
        if d['amountGrams'] is None and d['quantity'] is not None and not d['quantityRange']:
            if canonical=='달걀' and d['unit']=='개' and u and len({e['recipeId'] for e in u['evidence']})>=5 and len(u['distinctGramsPerUnit'])==1:
                grams=d['quantity']*u['distinctGramsPerUnit'][0]
                d.update(amountGrams=grams,unitStatus='OFFICIAL_REVIEWED_STANDARD_PORTION',
                         unitReason='계란/달걀 동일 alias에 한하여 기존 공식 개당 표준 참고량을 공유',
                         conversionProvenance=dict(grams=grams,gramsPerUnit=u['distinctGramsPerUnit'][0],source='RDA_OFFICIAL_RECIPE_STANDARD_PORTION',sourceUrl=u['evidence'][0]['sourceUrl'],recipeEquivalences=u['evidence'],checkedAt=DATE))
            elif d['identityStatus']=='LINKED':
                conversion=additional_portion(d) or portion_conversion(k,d,d['nutrientId'])
                if conversion:
                    d.update(amountGrams=conversion['grams'],unitStatus='OFFICIAL_FOOD_PORTION',unitReason=conversion['reason'],conversionProvenance=conversion)
        if original['status']!='RECIPE_SOURCE_INCOMPLETE':
            if d['amountGrams'] is None:d['status']='UNIT_CONVERSION_UNVERIFIED'
            elif d['identityStatus']=='LINKED':d['status']='LINKED'
            else:d['status']=d['identityStatus']
        d['checkedAt']=DATE
        if d['status']=='LINKED':
            d['estimatedKcal']=d['amountGrams']*nutrients[d['nutrientId']]['energyKcal']/100
            d['amountProvenance']=dict(sourceUrl=d['sourceUrl'],sourceSha256=d['sourceSha256'],originalSpan=d['originalSpan'],basis='ORIGINAL_RECIPE_INPUT',checkedAt=DATE)
        exact=name_index.get(k,[])
        alternatives=[page for page,text in cookbook_index if norm(recipe['name']) in text]
        attempts.append(dict(recipeId=rid,ingredientIndex=d['ingredientIndex'],ingredient=d['ingredient'],originalSpan=d['originalSpan'],
            beforeStatus=original['status'],afterStatus=d['status'],identityChanged=d['nutrientId']!=original['nutrientId'],amountChanged=d['amountGrams']!=original['amountGrams'],
            sameRecipeSourceVerified=True,contextSha256=hashlib.sha256(context.encode()).hexdigest(),
            exactNutrientNameCandidates=exact,officialNutritionSourcesChecked=['RDA_10.4_LOCAL_INDEX','K_FIND_CACHED_EXACT_NAME_CANDIDATES','USDA_SR_LEGACY_LOCAL_INDEX'],
            cachedCookbookCandidatePages=alternatives,amountLookupResult='NO_SAME_RECIPE_ID_QUANTIFIED_OVERRIDE' if d['amountGrams'] is None else 'ORIGINAL_MASS_OR_REVIEWED_UNIT',
            remainingIdentityReason=d['identityReason'],remainingAmountReason=d['unitReason']))
        decisions.append(d)
    assert len(attempts)==2032
    fixed={key(d):d for d in baseline if d['status'] in ('LINKED',EXCLUDED)}
    assert all(d==fixed[key(d)] for d in decisions if key(d) in fixed)
    save('ingredient-decisions.json',decisions);save('residual-attempts.json',attempts)
    before={r['recipeId']:r for r in load(OUT/'baseline-recipe-final-audit.json')['audit']}
    grouped=collections.defaultdict(list)
    for d in decisions:grouped[d['recipeId']].append(d)
    newly_complete=[rid for rid,rows in grouped.items() if not before[rid]['complete'] and all(d['status'] in ('LINKED',EXCLUDED) for d in rows)]
    # A complete original may be explicitly presented as one named official
    # reference even when the institution also publishes other formulations.
    # Retain all originals in the audit, never combine their inputs, and select
    # at most one new complete reference per exact generic dish name.
    selected={}
    for rid in sorted(newly_complete,key=lambda rid:(not rid.startswith('RDA'),rid)):
        selected.setdefault(norm(recipes[rid]['name']),rid)
    save('selected-complete-references.json',[dict(recipeId=rid,name=recipes[rid]['name'],sourceUrl=recipes[rid]['sourceUrl'],
        displayScope='OFFICIAL_RECIPE_REFERENCE_NOT_SELECTED_FOOD_FORMULATION',selectionMethod='NEWLY_COMPLETE_ORIGINAL_EXACT_GENERIC_NAME_ONE_REFERENCE_NO_INPUT_MERGE') for rid in selected.values()])
    summary=publish(decisions,nutrients,OUT,selected.values())
    newly=[d for d,o in zip(decisions,baseline) if d['status']=='LINKED' and o['status']!='LINKED']
    save('newly-linked-ingredients.json',newly)
    save('newly-complete-recipes.json',[r for r in summary['audit'] if r['complete'] and not before[r['recipeId']]['complete']])
    save('remaining-recipes.json',[dict(recipeId=r['recipeId'],name=r['name'],status=r['status'],sourceUrl=r['sourceUrl'],
        gaps=[dict(ingredient=d['ingredient'],originalSpan=d['originalSpan'],identityReason=d['identityReason'],amountReason=d['unitReason'],status=d['status'],
                  checkedSources=['RDA 10.4','K-FIND cached official exact-name candidates','USDA SR Legacy',r['sourceUrl']],nutritionCandidates=d['candidates'],unitCandidates=d['unitCandidates'])
              for d in r['ingredients'] if d['status'] not in ('LINKED',EXCLUDED)]) for r in summary['audit'] if not r['complete']])
    print(json.dumps(dict(newLinked=len(newly),newIdentity=sum(a['identityChanged'] for a in attempts),newAmount=sum(a['amountChanged'] for a in attempts),preservedLinked=2072,residualAttempted=len(attempts)),ensure_ascii=False))
    return summary

if __name__=='__main__':build()
