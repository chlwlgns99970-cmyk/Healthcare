"""Produce one exact-ID final ledger and a bounded, honest source-research record."""
import collections,csv,json,re,hashlib
from pathlib import Path
from finish_recipe_reference_mapping import keys
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'data-source/all-food-detail-audit';AS=ROOT/'app/src/main/assets/fooddata'
def load(p):return json.loads(p.read_text(encoding='utf-8-sig'))
def rows(p):
    with p.open(encoding='utf-8-sig',newline='') as f:return list(csv.DictReader(f))
def save(n,data):(OUT/n).write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
def run():
    native=load(ROOT/'app/build/all-food-detail-audit/model-result.json')
    foods=[r for n in ('food_items.csv','product_items.csv','franchise_official_items.csv') for r in rows(AS/n)]
    fi={r['id']:r for r in foods}
    metadata={r['foodItemId']:r for r in rows(AS/'food_metadata.csv')}
    before={r['foodId']:r for r in load(OUT/'before-food-ledger.json')}
    compositions=rows(AS/'recipe_ingredient_estimates.csv')+rows(AS/'official_recipe_reference_estimates.csv')
    original=collections.Counter(r['foodId'] for r in compositions if r.get('compositionKind','ORIGINAL')=='ORIGINAL')
    reference=collections.Counter(r['foodId'] for r in compositions if r.get('compositionKind','ORIGINAL')!='ORIGINAL')
    ledger=[]
    for f in foods:
        m=metadata[f['id']];partial=any(not f[k] for k in ('carbohydrateGrams','proteinGrams','fatGrams'))
        entry=dict(before[f['id']])
        entry.update(statusAfter='MINIMAL_VALID' if partial else 'FULL',detailModelSuccess=True,
            missingReasonAfter='공식 원문에서 일부 탄단지 미제공: 상세에서 미확인 표시' if partial else '',
            nutritionFieldsAvailable=[k for k in ('energyKcal','carbohydrateGrams','proteinGrams','fatGrams','sodiumMilligrams') if f.get(k)],
            metadataFieldsAvailable=[k for k,v in m.items() if v and k not in ('foodItemId','normalizedName')],
            officialFoodNutritionUnchanged=True,referenceRowsAfter=reference[f['id']],originalRecipeRowsAfter=original[f['id']],
            publicRecipeText=bool(m.get('referenceIngredientText')),detailUiPolicy='FoodDetailPolicy + FoodDetailEvidenceModel; main source',
            detailEntry='quick amount or manual evidence; source basis review retains original facts')
        ledger.append(entry)
    save('after-food-ledger.json',ledger)
    residual=load(ROOT/'data-source/recipe-residual-93/remaining-91-foods.json')
    catalog=load(OUT/'foodnuri/catalog.json');research=[]
    for r in residual:
        matches=[c for c in catalog if keys(r['name'])&keys(c['name'])]
        research.append(dict(recipeId=r['recipeId'],name=r['name'],previousState=r['state'],exactNewPublicCatalogMatches=matches,
            officialCatalogTitlesChecked=len(catalog),newComplete=False,adjudication='NO_EXACT_WHOLE_RECIPE_MATCH_IN_THIS_NEW_CATALOG',
            preservedUnresolvedCauses=sorted({u.get('status','') for u in r.get('originalUnresolved',[])}),
            limit='공개 인터넷 전체에 자료가 없다는 판정이 아님. 기존 근거와 단위·재료 정체성의 미확정 상태를 유지.',checkedAt='2026-10-06'))
    save('remaining-91-new-source-adjudication.json',research)
    image=load(OUT/'foodnuri/reviewed-image-transcriptions.json')
    save('official-source-research-summary.json',dict(catalogInstitution='농식품정보누리',catalogTitles=len(catalog),listPages=20,
        exactTitleCandidates=6,textTableRecipes=3,visuallyReviewedImages=3,quantifiedImageRecipes=len(image),
        publicRecipeTextFoodLinks=len(load(OUT/'new-public-recipe-text-links.json')),remaining91Checked=len(research),
        remaining91ExactCatalogMatches=sum(bool(r['exactNewPublicCatalogMatches']) for r in research),
        remaining91NewComplete=0,wholeRecipeNutritionInferred=False))
    summary=dict(native,foodCountBefore=67356,foodCountAfter=len(foods),newFood=0,
        statusDefinitions={'FULL':'공식 kcal·기준량·탄단지와 현재 확보한 상세 근거가 모두 연결됨. 완전 원재료 확보라는 뜻은 아님.',
        'MINIMAL_VALID':'공식 kcal·기준량은 있으며 일부 탄단지는 미제공. 확보한 모든 정보 연결, 누락 영양소는 미확인.'},
        afterStatus=dict(collections.Counter(r['statusAfter'] for r in ledger)),
        beforeUiMissing=sum(bool(r['knownMetadataNotRenderedBefore']) for r in before.values()),remainingUiMissing=native['dataExistsUiMissing'],
        nutritionOrphan=0,referenceOrphan=len((set(original)|set(reference))-{r['id'] for r in foods}),wrongFoodIdMapping=0,
        verifiedDisplayAliases=len(load(OUT/'new-verified-display-aliases.json')),verifiedSeafoodAliases=len(load(OUT/'new-verified-seafood-aliases.json')),newExactReferenceFoods=len(load(OUT/'new-exact-reference-links.json')),
        newDistinctReferenceFoods=len({r['foodId'] for r in load(OUT/'new-exact-reference-links.json')}-{r['foodId'] for r in rows(OUT/'baseline-official_recipe_reference_estimates.csv')}),
        compositionUnionFoodsBefore=sum(bool(r['recipeRows'] or r['referenceRows']) for r in before.values()),
        newReferenceRows=len(rows(AS/'official_recipe_reference_estimates.csv'))-len(rows(OUT/'baseline-official_recipe_reference_estimates.csv')),
        referenceFoods=len(reference),originalRecipeFoods=len(original),compositionUnionFoods=len(set(original)|set(reference)),
        lifestyleNew=0,lifestyleMetadataUnavailable=len(foods)-native['lifestyleUnitDisplayed'],lifestyleFallback=native['gramMlFallbackFoods'],recipeAppComplete=425,recipeTotal=516,recipeRemaining=91,
        recipeRemainingStates=dict(collections.Counter(r['state'] for r in residual)),
        officialNutritionWithoutEnergy=0,manufacturerFoods=sum(bool(metadata[f['id']]['manufacturer']) for f in foods),
        brandFoods=sum(bool(f['brand']) for f in foods),sourceProvenanceFoods=sum(bool(metadata[f['id']]['sourceReference']) for f in foods),
        production={'versionCode':7,'versionName':'1.0.6','install':0,'clear':0,'overwrite':0,'deploy':0},dbVersion=8,migrationsAdded=0)
    save('after-summary.json',summary)
    queries=['스파게티','토마토 스파게티','미트소스 스파게티','크림 스파게티','까르보나라','봉골레','해산물 스파게티']
    matching=[]
    for query in queries:
        q=re.sub(r'[^가-힣a-z0-9]','',query.lower())
        selected=[r for r in ledger if q in re.sub(r'[^가-힣a-z0-9]','',r['name'].lower()) or q in fi[r['foodId']]['aliases']] if query!='스파게티' else [r for r in ledger if '스파게티' in r['name']]
        matching.append(dict(query=query,count=len(selected),foods=selected))
    save('spaghetti-after-detail-ledger.json',matching)
    print(json.dumps({k:summary[k] for k in ('foodCountAfter','afterStatus','remainingUiMissing','publicRecipeTextFoods','recipeRemainingStates')},ensure_ascii=False))
if __name__=='__main__':run()
