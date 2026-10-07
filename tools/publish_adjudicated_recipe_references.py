"""Publish verified ingredient references; official food kcal/amounts are immutable."""
import collections,csv,hashlib,json,re
from adjudicate_recipe_inputs import ROOT,OUT,read,build,norm
import build_recipe_calorie_references as legacy
def publish(decisions=None,nutrients=None,output_directory=None,selected_reference_ids=()):
    if decisions is None:
        decisions,nutrients=build()
    assert nutrients is not None
    output_directory=output_directory or OUT
    output_directory.mkdir(parents=True,exist_ok=True)
    assert all(d['identityStatus']!='UNREVIEWED' and d['unitStatus']!='UNREVIEWED' for d in decisions)
    recipes={r['recipeId']:r for p in (ROOT/'data-source/recommendation').glob('official*public-recipe-facts.csv') for r in read(p)}
    selected_reference_ids=set(selected_reference_ids)
    selected_names=[norm(recipes[rid]['name']) for rid in selected_reference_ids]
    assert len(selected_names)==len(set(selected_names)), 'Only one selected reference per generic dish name'
    foods=read(ROOT/'app/src/main/assets/fooddata/food_items.csv')
    byname=collections.defaultdict(list);recipe_names=collections.Counter(norm(r['name']) for r in recipes.values())
    for f in foods:
        if not f['brand'] and f['sourceType']=='K-FIND':byname[norm(f['name'])].append(f)
    grouped=collections.defaultdict(list)
    for d in decisions:grouped[d['recipeId']].append(d)
    audit=[];output=[]
    for rid,inputs in grouped.items():
        r=recipes[rid];linked=[d for d in inputs if d['status']=='LINKED']
        complete=bool(inputs) and all(d['status'] in ('LINKED','EXCLUDED_NON_CALORIC_OR_PROCESS_ITEM') for d in inputs)
        state='COMPLETE_LINKED' if complete else 'PARTIAL_LINKED' if linked else 'UNLINKABLE'
        targets=byname[norm(legacy.REFERENCE_NAMES.get(rid,r['name']))]
        unique=recipe_names[norm(r['name'])]==1
        selected_reference=rid in selected_reference_ids and complete
        publish=bool(targets) and (unique or selected_reference) and bool(linked)
        ingredient_text=', '.join(filter(None,[r['mainIngredientText'],r['additionalIngredientText']]))
        audit.append(dict(recipeId=rid,name=r['name'],status=state,complete=complete,published=publish,
            sourceHashVerified=True,ingredients=inputs,ingredientText=ingredient_text,sourceUrl=r['sourceUrl'],
            targetFoodIds=[f['id'] for f in targets],foodIdentityUnique=unique,
            identityReason='NO_EXACT_GENERIC_FOOD' if not targets else 'SELECTED_COMPLETE_OFFICIAL_REFERENCE' if selected_reference and not unique else 'MULTIPLE_RECIPE_IDENTITIES' if not unique else 'EXACT_GENERIC_REFERENCE'))
        if not publish:continue
        portions=re.search(r'재료량\(?\s*\(?(\d+)인분',r['mainIngredientText'])
        basis=f'공식 레시피 {portions[1]}인분 전체 재료량' if portions else '공식 원문에 제시된 전체 재료량 (인분 수 미확인)'
        if any(d.get('conversionProvenance') for d in inputs):
            basis+=' · 계량 중량은 동일 재료의 공식 계량 근거를 사용한 참고 환산'
        combined={}
        for d in linked:
            identity=d['nutrientId'];entry=combined.setdefault(identity,[d['ingredient'],0.0]);entry[1]+=d['amountGrams']
        for f in targets:
            for identity,(name,grams) in combined.items():
                n=nutrients[identity];assert isinstance(n['energyKcal'],(int,float))
                output.append(dict(foodId=f['id'],recipeId=rid,recipeName=r['name'],recipeBasis=basis,
                  ingredientText=ingredient_text,ingredientName=name,amountGrams=f'{grams:g}',
                  nutrientFoodId=('fdc-reference-' if identity.isdigit() else 'kfind-reference-' if identity.startswith('P') else 'mfds-reference-' if identity.startswith('MFDS-LEGACY-') else 'public-reference-' if identity.startswith(('R1','R2')) and '-' in identity else 'rda-reference-')+identity,
                  nutrientName=n['name'],kcalPer100g=f"{n['energyKcal']:g}",recipeUrl=r['sourceUrl'],
                  nutrientUrl=n['sourceUrl'],recipeSha256=r['sourceSha256'],checkedAt='2026-10-05',recipeComplete=str(complete).lower(),
                  foodReferenceKcal=f['energyKcal'],foodReferenceAmount=f['referenceAmount'],foodReferenceUnit=f['unit']))
    asset=ROOT/'app/src/main/assets/fooddata/recipe_ingredient_estimates.csv'
    with asset.open('w',encoding='utf-8',newline='') as stream:
        writer=csv.DictWriter(stream,fieldnames=legacy.FIELDS);writer.writeheader();writer.writerows(output)
    summary=dict(recipeCount=len(audit),ingredientRows=len(decisions),identityCounts=dict(collections.Counter(d['identityStatus'] for d in decisions)),
       unitCounts=dict(collections.Counter(d['unitStatus'] for d in decisions)),ingredientCounts=dict(collections.Counter(d['status'] for d in decisions)),
       recipeStates=dict(collections.Counter(r['status'] for r in audit)),publishedRecipeCount=sum(r['published'] for r in audit),
       publishedCompleteCount=sum(r['published'] and r['complete'] for r in audit),linkedFoodCount=len({d['foodId'] for d in output}),
       publishedIngredientRows=len(output),assetSha256=hashlib.sha256(asset.read_bytes()).hexdigest(),audit=audit)
    (output_directory/'recipe-final-audit.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2),encoding='utf-8')
    (output_directory/'recipe-progress.json').write_text(json.dumps({k:v for k,v in summary.items() if k!='audit'}|{'publicationUpdated':True},ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps({k:v for k,v in summary.items() if k!='audit'},ensure_ascii=False))
    return summary
if __name__=='__main__':publish()
