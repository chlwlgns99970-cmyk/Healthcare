"""Official reference input calories, independent of FoodItem/MealRecord values."""
from pathlib import Path
import csv, hashlib, json, re, io, zipfile, html
from collections import Counter
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'data-source/recipe-calories';OUT.mkdir(exist_ok=True)
ASSETS=ROOT/'app/src/main/assets/fooddata'
CACHE=ROOT/'app/build/food-quality-followup/household-source-cache'
def read(path):
    with path.open(encoding='utf-8-sig',newline='') as f:return list(csv.DictReader(f))
def norm(value):return re.sub(r'\s+','',value)
# Only presentation aliases. No cooked/raw, cultivar, peeled or part stripping.
ALIASES={'오이':'168409','양배추':'169975','당근':'170393','양파':'170000','마늘':'169230',
 '다진마늘':'169230','다진다진마늘':'169230','통마늘':'169230','다진양파':'170000',
 '생강':'169231','설탕':'169655','소금':'173468','올리브유':'171413','콩기름':'171411',
 '대두유':'171411','토마토':'170457','딸기':'167762','바나나':'173944',
 '무':'168451','무채':'168451','오이(中)':'168409'}
# Whole baby potatoes oven-roasted without any peeling instruction in original.
RECIPE_ALIASES={'MFDS-247':{'감자':'170026'}}
# Explicit reviewed reference relation, not a certified formulation or serving:
# whole baby-potato oven recipe is a named example of the generic roasted dish.
REFERENCE_NAMES={'MFDS-247':'감자구이'}
FIELDS=['foodId','recipeId','recipeName','recipeBasis','ingredientText','ingredientName','amountGrams',
 'nutrientFoodId','nutrientName','kcalPer100g','recipeUrl','nutrientUrl','recipeSha256','checkedAt','recipeComplete',
 'foodReferenceKcal','foodReferenceAmount','foodReferenceUnit']
AMOUNT=re.compile(r'(?P<name>[^,;:\d]+?)\s*(?P<amount>\d+(?:\.\d+)?)\s*\(?g\)?(?![A-Za-z])')
def parse_ingredients(text):
    text=html.unescape(text)
    text=re.sub(r'^.*?재료량\s*\(?\s*\d+인분\)?\s*[-:]?','',text)
    text=re.sub(r'\([^)]*(?:양념|소스)[^)]*\)|(?:양념장|양념|간장소스)\s*:',',',text)
    entries=[]
    for part in re.split('[,;]',text):
        part=part.strip()
        if not part:continue
        end=0
        for m in AMOUNT.finditer(part):
            if part[end:m.start()].strip():entries.append((part[end:m.start()].strip(),None))
            entries.append((m['name'].strip(' -'),float(m['amount'])));end=m.end()
        if part[end:].strip():entries.append((part[end:].strip(),None))
    return entries
def archive_nutrients():
    archive=CACHE/'usda-sr-legacy.zip'
    manifest={r['file']:r for r in json.loads((CACHE/'manifest.json').read_text(encoding='utf-8'))}
    digest=hashlib.sha256(archive.read_bytes()).hexdigest();assert digest==manifest[archive.name]['sha256']
    wanted=set(ALIASES.values())|{'170026'}
    with zipfile.ZipFile(archive) as z:
        def rows(name):
            p=next(p for p in z.namelist() if p.endswith('/'+name))
            return csv.DictReader(io.TextIOWrapper(z.open(p),encoding='utf-8-sig'))
        names={r['fdc_id']:r for r in rows('food.csv') if r['fdc_id'] in wanted}
        defs={r['id']:r for r in rows('nutrient.csv')}
        energy_id=[key for key,r in defs.items() if r['name']=='Energy' and r['unit_name']=='KCAL'];assert len(energy_id)==1
        energy={r['fdc_id']:float(r['amount']) for r in rows('food_nutrient.csv') if r['fdc_id'] in wanted and r['nutrient_id']==energy_id[0]}
    assert wanted==names.keys()==energy.keys()
    result={key:dict(names[key],energyKcal=energy[key],referenceAmount=100,unit='g',archiveSha256=digest,
        sourceUrl=manifest[archive.name]['url']) for key in sorted(wanted)}
    (OUT/'reviewed-nutrient-facts.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    return result
def build():
    foods=read(ASSETS/'food_items.csv')
    recipes={r['recipeId']:r for path in sorted((ROOT/'data-source/recommendation').glob('official*public-recipe-facts.csv')) for r in read(path)}
    nutrients=archive_nutrients();by_name={};food_names={}
    for r in recipes.values():by_name.setdefault(norm(r['name']),[]).append(r)
    for f in foods:
        if not f['brand'] and f['sourceType']=='K-FIND':food_names.setdefault(norm(f['name']),[]).append(f)
    audit=[];output=[]
    for r in recipes.values():
        key=r['recipeId'].lower().replace('rda-diet-','nongsaro-diet-')
        raw=ROOT/'app/build/food-quality-qa/recipe-source'/(key+'.html')
        valid=raw.is_file() and hashlib.sha256(raw.read_bytes()).hexdigest()==r['sourceSha256']
        name=norm(r['name']);targets=food_names.get(norm(REFERENCE_NAMES.get(r['recipeId'],r['name'])),[]);unique=len(by_name[name])==1
        items=[];linked=[]
        ingredient_text=', '.join(filter(None,[r['mainIngredientText'],r['additionalIngredientText']]))
        for ingredient,grams in parse_ingredients(ingredient_text):
            identity=(RECIPE_ALIASES.get(r['recipeId'],{})|ALIASES).get(norm(ingredient))
            reason='LINKED' if grams is not None and identity else 'UNIT_UNCONFIRMED' if grams is None else 'NUTRITION_IDENTITY_UNREVIEWED'
            items.append(dict(ingredient=ingredient,grams=grams,nutrientId=identity,reason=reason))
            if reason=='LINKED':linked.append((ingredient,grams,identity))
        complete=bool(items) and all(i['reason']=='LINKED' for i in items)
        publish=bool(targets) and unique and valid and bool(linked)
        audit.append(dict(recipeId=r['recipeId'],name=r['name'],sourceUrl=r['sourceUrl'],sourceHashVerified=valid,
            ingredientText=ingredient_text,ingredients=items,targetFoodIds=[f['id'] for f in targets],
            foodIdentityUnique=unique,complete=complete,published=publish,
            reasonCounts=dict(Counter(i['reason'] for i in items if i['reason']!='LINKED')),
            identityReason='NO_EXACT_GENERIC_FOOD' if not targets else 'MULTIPLE_RECIPE_IDENTITIES' if not unique else 'REVIEWED_NAMED_ROASTED_POTATO_REFERENCE' if r['recipeId'] in REFERENCE_NAMES else 'EXACT_GENERIC_REFERENCE'))
        if not publish:continue
        portions=re.search(r'재료량\(?\s*\(?(\d+)인분',r['mainIngredientText'])
        basis=f'공식 레시피 {portions[1]}인분 전체 재료량' if portions else '공식 원문에 제시된 전체 재료량 (인분 수 미확인)'
        combined={}
        for ingredient,grams,identity in linked:
            entry=combined.setdefault(identity,[ingredient,0.0]);entry[1]+=grams
        for food in targets:
            for identity,(ingredient,grams) in combined.items():
                n=nutrients[identity]
                output.append(dict(foodId=food['id'],recipeId=r['recipeId'],recipeName=r['name'],recipeBasis=basis,
                    ingredientText=ingredient_text,ingredientName=ingredient,amountGrams=f'{grams:g}',
                    nutrientFoodId='fdc-reference-'+identity,nutrientName=n['description'],kcalPer100g=f"{n['energyKcal']:g}",
                    recipeUrl=r['sourceUrl'],nutrientUrl=f'https://fdc.nal.usda.gov/food-details/{identity}/nutrients',
                    recipeSha256=r['sourceSha256'],checkedAt=r['checkedAt'],recipeComplete=str(complete).lower(),
                    foodReferenceKcal=food['energyKcal'],foodReferenceAmount=food['referenceAmount'],foodReferenceUnit=food['unit']))
    with (ASSETS/'recipe_ingredient_estimates.csv').open('w',encoding='utf-8',newline='') as f:
        w=csv.DictWriter(f,fieldnames=FIELDS);w.writeheader();w.writerows(output)
    published=[r for r in audit if r['published']]
    summary=dict(officialRecipeCount=len(recipes),reauditedRecipeCount=len(audit),linkedRecipeCount=len(published),
        linkedFoodCount=len({r['foodId'] for r in output}),ingredientLinkCount=len(output),
        fullRecipeNutritionCount=sum(r['complete'] for r in published),partialRecipeCount=sum(not r['complete'] for r in published),
        ingredientFailures=dict(sum((Counter(r['reasonCounts']) for r in audit),Counter())),
        recipeIdentityReasons=dict(Counter(r['identityReason'] for r in audit)),sourceHashFailures=sum(not r['sourceHashVerified'] for r in audit),
        householdConversions=0,sourceRecipeIdentityNotFoodFormulation=True,reviewedAliases=ALIASES,recipeScopedAliases=RECIPE_ALIASES,
        assetSha256=hashlib.sha256((ASSETS/'recipe_ingredient_estimates.csv').read_bytes()).hexdigest(),audit=audit)
    (OUT/'audit.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({k:v for k,v in summary.items() if k!='audit'},ensure_ascii=False));return summary
if __name__=='__main__':
    # The full adjudication publisher is the current generator. Keep legacy
    # nutrient/source helpers importable without overwriting the reviewed asset.
    from publish_adjudicated_recipe_references import publish
    publish()
