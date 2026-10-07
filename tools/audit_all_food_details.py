"""Audit exact identities and preserved source rows; no guessed name joins."""
import collections,csv,hashlib,json,math
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
AS=ROOT/'app/src/main/assets/fooddata'
OUT=ROOT/'data-source/all-food-detail-audit'
def rows(path):
    with path.open(encoding='utf-8-sig',newline='') as f:return list(csv.DictReader(f))
def save(name,value):
    (OUT/name).write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
def run():
    OUT.mkdir(exist_ok=True)
    files=['food_items.csv','product_items.csv','franchise_official_items.csv','food_metadata.csv','recipe_ingredient_estimates.csv','official_recipe_reference_estimates.csv','food_data_manifest.properties']
    for name in files:
        target=OUT/('baseline-'+name)
        if not target.exists():target.write_bytes((AS/name).read_bytes())
    foods=[r for n in files[:3] for r in rows(AS/n)]
    metadata=rows(AS/'food_metadata.csv');mi={r['foodItemId']:r for r in metadata}
    recipes=rows(AS/files[4]);refs=rows(AS/files[5]);ri=collections.defaultdict(list)
    for r in recipes+refs:ri[r['foodId']].append(r)
    ids={r['id'] for r in foods}
    ledger=[]
    for f in foods:
        m=mi.get(f['id'],{});nutrition=[f.get(k,'') for k in ['carbohydrateGrams','proteinGrams','fatGrams']]
        missing_fields=[k for k in ['packageSize','intakeReference','sourceDate','servingSourceReference','completeIngredientText','ingredientText','rawClassification'] if m.get(k)]
        entry=dict(foodId=f['id'],name=f['name'],canonicalName=f['normalizedName'],category=f['category'],brand=f['brand'],manufacturer=m.get('manufacturer',''),kcal=f['energyKcal'],basisAmount=f['referenceAmount'],basisUnit=f['unit'],carbs=nutrition[0],protein=nutrition[1],fat=nutrition[2],serving=f['servingDescription'],lifestyleUnit=m.get('householdUnit',''),source=f['sourceType'],provenance=m.get('sourceReference',''),metadata=bool(m),recipeRows=sum(r.get('compositionKind','ORIGINAL')=='ORIGINAL' for r in ri[f['id']]),referenceRows=sum(r.get('compositionKind','ORIGINAL')!='ORIGINAL' for r in ri[f['id']]),knownMetadataNotRenderedBefore=missing_fields)
        entry['statusBefore']='DATA_EXISTS_UI_MISSING' if missing_fields else ('FULL' if m else 'NUTRITION_ONLY')
        entry['missingReason']='known metadata fields not rendered' if missing_fields else ('additional metadata not acquired' if not m else '')
        ledger.append(entry)
    save('before-food-ledger.json',ledger)
    save('spaghetti-before.json',[r for r in ledger if any(q in r['name'] for q in ['스파게티','까르보나라','봉골레','파스타'])])
    orphan=[m for m in metadata if m['foodItemId'] not in ids]
    save('metadata-outside-room-foods.json',orphan)
    invalid=[f for f in foods if not math.isfinite(float(f['energyKcal'])) or float(f['energyKcal'])<0 or not math.isfinite(float(f['referenceAmount'])) or float(f['referenceAmount'])<=0]
    save('invalid-nutrition-basis.json',invalid)
    summary=dict(foodCount=len(foods),uniqueFoodIds=len(ids),metadataRows=len(metadata),metadataForRoomFoods=sum(r['metadata'] for r in ledger),metadataOutsideRoomFoods=len(orphan),recipeOrphans=sorted({r['foodId'] for r in recipes+refs}-ids),invalidNutritionBasis=len(invalid),beforeStatus=dict(collections.Counter(r['statusBefore'] for r in ledger)),nutritionMacros=dict(collections.Counter(sum(bool(v) for v in [r['carbs'],r['protein'],r['fat']]) for r in ledger)),referenceFoods=sum(bool(r['referenceRows']) for r in ledger),originalRecipeFoods=sum(bool(r['recipeRows']) for r in ledger),lifestyleMetadata=sum(bool(r['lifestyleUnit']) for r in ledger),spaghettiLiteral=sum('스파게티' in r['name'] for r in ledger),fieldOmissions=dict(collections.Counter(k for r in ledger for k in r['knownMetadataNotRenderedBefore'])))
    save('before-summary.json',summary)
    print(json.dumps(summary,ensure_ascii=False))
if __name__=='__main__':run()
